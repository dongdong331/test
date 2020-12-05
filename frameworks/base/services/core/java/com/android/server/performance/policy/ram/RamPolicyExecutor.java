package com.android.server.performance.policy.ram;

import static com.android.server.performance.PerformanceManagerDebugConfig.DEBUG_RAMPOLICY;
import static com.android.server.performance.policy.ram.MemoryScene.POLICY_KILL;
import static com.android.server.performance.policy.ram.MemoryScene.POLICY_QUICK_KILL;
import static com.android.server.performance.policy.ram.MemoryScene.POLICY_RECLAIM;
import static com.android.server.performance.policy.ram.MemoryScene.POLILCY_RECLAIM_PERSIST;
import static com.android.server.performance.policy.ram.MemoryScene.SCENE_EMERGENCY;
import static com.android.server.performance.policy.ram.MemoryScene.SCENE_IDLE;
import static com.android.server.performance.PerformanceManagerService.VmpressureData;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;

import com.android.server.am.ActivityManagerServiceEx;
import com.android.server.performance.PerformanceManagerService.*;
import com.android.server.performance.PerformanceManagerService;
import com.android.server.performance.collector.UserHabitCollector;
import com.android.server.performance.PolicyConfig;
import com.android.server.performance.PolicyExecutor;
import com.android.server.performance.PolicyItem;
import com.android.server.notification.NotificationManagerInternal;
import com.android.server.LocalServices;
import com.android.server.performance.status.CpuStatus;
import com.android.server.performance.status.SystemStatus;


import android.app.ActivityManagerNative;
import android.app.PerformanceManagerNative;
import android.app.ProcState;
import android.app.ProcessInfo;
import android.app.UserHabit;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.ComponentName;
import android.content.Context;
import android.view.inputmethod.InputMethodInfo;
import com.android.internal.view.IInputMethodManager;
import android.os.Message;
import android.os.Process;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.content.ComponentName;
import android.os.SystemClock;
import android.os.Trace;
import android.os.UserHandle;
import android.util.Slog;

/**
 * Created by SPREADTRUM\joe.yu on 7/31/17.
 */

public class RamPolicyExecutor extends PolicyExecutor {
    private static String TAG = "performance";
    private static String THREAD_NAME = "RamPolicyExecutor";
    private static String CONF_TAG_ACTION = "action";
    private static String ATTR_NAME = "name";
    private static String ATTR_RAM_SIZE = "ramsize";
    private static String ATTR_SCENE = "scene";

    private int CPU_LOADING_SAMPLE_LOW_COUNT = 3;
    private int CPU_LOADING_SAMPLE_HIGH_COUNT = 2;
    private int RECLAIM_ADJ_THROL_RECLAIM = 800;
    private int RECLAIM_ADJ_BOOST_KILL = 900;
    private int FS_RECLAIM_ADJ = 200;
    private int MAX_RECLAIM_COUNT = 2;
    private int MAX_RECLAIM_PERSIST_COUNT = 10;
    private static final int SIGSTOP = 19;
    private static final int SIGCONT = 18;
    private static final int MSG_SCREEN_ON = 1;
    private static final int MSG_UNFREEZE_PROCESS = 2;
    private static final int SCEN_NORMAL = 0;
    private static final int SCEN_EM = 1;
    private static final int SCEN_BOOST = 2;
    private static final int MEMRECLAIM_CMD_PROCESS_RECLAIM = 0;
    private static final String RECLAIM_TYPE_ANON = "anon";
    private static final String RECLAIM_TYPE_FILE = "file";
    private static final String RECLAIM_TYPE_ALL = "all";
    private static final String RECLAIM_TYPE_HIBER = "hiber";
    private static final int PAGE_SIZE = 4 * 1024;
    private static final long MB = 1024 * 1024;
    private static final long NOTIFICATIONLOWTIME = 2 * 60 * 1000;
    private static final long NOTIFICATIONNORMALTIME = 5 * 60 * 1000;
    private static final long WAKELOCK_PROTECTED_TIME = 15 * 1000;
    private static final int CRITIAL_SWAP_USAGE = 90;
    private static final long VMPRESSURE_INTERVAL = 1000;

    private MemoryConstant mMemoryConstant;
    private MemoryScene mMemoryScene;
    private boolean mInReclaimProcess = false;
    private SystemStatus mCurrentSystemStatus;
    private ActivityManagerServiceEx mAm = null;
    private PerformanceManagerService mService = null;
    private int mCpuLoadingLowCount = 0;
    private int mCpuLoadingHighCount = 0;
    private boolean mBoostKillEnable = true;
    private boolean mReclaimProcessEnabled = false;
    private ArrayList<String> mSpecialPackages = new ArrayList<>();
    private UserHabitCollector mUserHabitCollector;
    private boolean mInStrumentation = false;
    private String mCurrentFocusPackage = "";
    private NotificationManagerInternal mNotificationMan;
    private int mCurrentBgRunningPkgCount = 0; // 3rd pkg count running in bg exclude HOME & inputmehod & focus
    private VmpressureListener mVmpressureListener;
    private long mLastVmEventTime = -1;


    private long MEMORY_LEVEL_RECLAIM_GAP = 10; // In MB

    //per package...
    class LRUReclaimProcessRecord {
        UserHabit habit;
        long rss;
        int uid;
        ArrayList<ProcessInfo> procs;
        String pkgName;
    }

    //per process...
    class ReclaimProcessRecord {
        ProcessInfo info;
        long uss;
        long pss;
        UserHabit habit;
        long launchCount;
        String policy;

        public ReclaimProcessRecord(ProcessInfo processInfo, UserHabit habit,
                String policy) {
            this.info = processInfo;
            this.habit = habit;
            this.launchCount = habit.launchCount;
            this.policy = policy;
        }

        public void setUss(long uss) {
            this.uss = uss;
        }

        public void setPss(long pss) {
            this.pss = pss;
        }

        public long getReclaimableMemory() {
            switch (policy) {
            case POLICY_KILL:
            case POLICY_QUICK_KILL:
                return pss;
            case POLICY_RECLAIM:
            case POLILCY_RECLAIM_PERSIST:
                return uss;
            default:
                return pss;
            }
        }

        @Override
        public String toString() {
            return "pid:" + info.pid + "proc:" + info.processName + habit
                    + " uss:" + uss + "pss:" + pss + "policy:" + policy;
        }

        public long reclaim() {
            long nr_reclaimed = 0;
            switch (policy) {
            case POLICY_KILL:
            case POLICY_QUICK_KILL:
                killOneProcess(info.pid);
                nr_reclaimed = pss;
                break;
            case POLICY_RECLAIM:
                nr_reclaimed = getReclaimResult(reclaimOneProcess(info.pid,
                        RECLAIM_TYPE_HIBER));
                break;
            case POLILCY_RECLAIM_PERSIST:
                nr_reclaimed = getReclaimResult(reclaimOneProcess(info.pid,
                        RECLAIM_TYPE_HIBER));
                break;
            default:
                break;
            }

            return nr_reclaimed;
        }
    }

    @Override
    public void executorPolicy(SystemStatus status) {
        checkMemoryStatus(status);
    }

    public RamPolicyExecutor(PolicyItem config,
            PerformanceManagerService service) {
        super();
        mMemoryConstant = MemoryConstant.loadFromConfig(config
                .getConfig(MemoryConstant.TAG), service.getRamConfig());
        mMemoryScene = MemoryScene.loadFromConfig(config
                .getConfig(MemoryScene.TAG));
        mService = service;
        mAm = (ActivityManagerServiceEx) ActivityManagerNative.getDefault();
        mNotificationMan = LocalServices
                .getService(NotificationManagerInternal.class);
        mReclaimProcessEnabled = reclaimProcessEnabled();
        mUserHabitCollector = mService.getUserHabitCollector();
        mSpecialPackages.add("com.android");
        mSpecialPackages.add("com.google");
        mSpecialPackages.add("com.quicinc.vellamo");
        mSpecialPackages.add("com.tencent.mm");
        mSpecialPackages.add("com.immomo.momo");
        mSpecialPackages.add("com.tencent.mobileqq");
        mSpecialPackages.add("android.inputmethodservice.cts");
        mSpecialPackages.add("com.whatsapp");
        mSpecialPackages.add("com.facebook.orca");
        mSpecialPackages.add("com.sprdsrt.srtmemtest");
        if (mMemoryConstant.isVmpressureListenerEnabled()) {
            VmpressureListener mVmpressureListener = new VmpressureListener(mService);
            mVmpressureListener.start();
        }
    }

    @Override
    public String getThreadName() {
        return THREAD_NAME;
    }

    @Override
    protected void handleVmpressureEvent(VmpressureData data) {
        int swapUsage = data.swapUsage;
        int vmpressure = data.pressure;
        long idleTime = -1;
        long now = SystemClock.uptimeMillis();
        if (mLastVmEventTime != -1 && (now - mLastVmEventTime) < VMPRESSURE_INTERVAL) {
            //ignore
            if (DEBUG_RAMPOLICY) Slog.d(TAG, "ignore vmpressure events...");
            return;
        }
        mLastVmEventTime = now;
        try {
            if (!mInStrumentation && (idleTime = mMemoryConstant.getCurrentForceStopIdleTime(swapUsage)) != -1) {
                if (DEBUG_RAMPOLICY) Slog.d(TAG, "system under memPressure:" + vmpressure +
                       " current swapusage ->" + swapUsage + "idle time = " + idleTime);
                boolean force = swapUsage >= CRITIAL_SWAP_USAGE;
                String reason = "swapusage :" + swapUsage;
                doLRUReclaim(idleTime, force, reason);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void handleActivityStateChange(ActivityStateData aData) {
        if (aData == null) {
            return;
        }
        int state = aData.bundle.getInt(ProcessInfo.KEY_ACTIVITY_STATE);
        ComponentName app = aData.intent.getComponent();
        /*
        if (state == ProcessInfo.ACTIVITY_STATE_PROC_START && app != null) {
            ProcState p = mService.getProcessState(app.getPackageName());
            if (p != null && p.avgTopPss != 0) {
                // if available mem is low than proc needed , kill low-prio
                // processes
                SystemStatus status = mService.getCurrentSystemStatus(true);
                long currentMem = status.getRamStatus().getAvailableMemKb();
                if (DEBUG_RAMPOLICY)
                    Slog.d(TAG, "handleActivityStateChange :" + app + "avg :"
                            + p.avgTopPss + " current " + currentMem);
                if (DEBUG_RAMPOLICY)
                    mMemoryScene.dump();
                if (DEBUG_RAMPOLICY)
                    mMemoryConstant.dump();
                if (p.avgTopPss >= mMemoryConstant.getBigMemCriticalMemory()
                        && currentMem < p.avgTopPss
                                + mMemoryConstant.getDefaultCriticalMemory()) {
                    // try to keep available high than lmk cache min+
                    // app-require
                    // TODO. disable pre-kill
                    // doMemoryReclaim(p.avgTopPss +
                    // mMemoryConstant.getDefaultCriticalMemory(),
                    // mMemoryScene.getPolicyForScene(SCENE_BIG_MEM));
                }
            }
        } else */
        if (state == ProcessInfo.ACTIVITY_STATE_LAUNCHDONE && app != null) {
            checkMemoryStatus(mService.getCurrentSystemStatus(true));
        } else if (state == ProcessInfo.ACTIVITY_STATE_RESUME && app != null) {
            // TODO: Fix ugly way to get Launcher... adjtype sometimes not work..
            String home = getHomePackageName();
            if (home != null && home.equals(app.getPackageName())) {
                checkMemoryStatus(mService.getCurrentSystemStatus(true));
            }
            mCurrentFocusPackage =  app.getPackageName();
        }
    }

    private String getHomePackageName() {
        ProcessInfo p = mAm.getHomeProcess();
        if (p != null) {
            return p.packageName;
        }
        return null;
    }

    @Override
    protected void handleCpuStatusChange(CpuStatus status) {
    }

    private void enableBoostKill(boolean enable) {
        mBoostKillEnable = enable;
        if (DEBUG_RAMPOLICY) {
            Slog.d(TAG, "enableBootKill :" + enable);
        }
        try {
            PerformanceManagerNative.getDefault().enableBoostKill(
                    enable ? 1 : 0);
        } catch (Exception e) {
            // Slog.d(TAG, " exception in enableBoostKill " +e);
        }
    }

    public boolean isMemoryPressureNormal() {
        SystemStatus status = mService.getCurrentSystemStatus(true);
        long currentMem = status.getRamStatus().getAvailableMemKb() / 1024;
        if (DEBUG_RAMPOLICY) {
            Slog.d(TAG, "curent ->" + currentMem);
            Slog.d(TAG, "idle ->" + mMemoryConstant.getNormalMemory());
        }
        return currentMem >= mMemoryConstant.getNormalMemory();
    }

    private boolean isMemoryPressureReclaim(long current) {
        if (DEBUG_RAMPOLICY) {
            Slog.d(TAG, "curent ->" + current);
            Slog.d(TAG, "idle ->" + mMemoryConstant.getNormalMemory());
            Slog.d(TAG, "def ->" + mMemoryConstant.getDefaultCriticalMemory());
        }
        return current > mMemoryConstant.getDefaultCriticalMemory()
                + MEMORY_LEVEL_RECLAIM_GAP
                && current < mMemoryConstant.getNormalMemory();
    }

    // means system under really critial mempressure (cache size ~=
    // adj0_cachemin)
    // we should try to reclaim persist-processes
    private boolean isMemoryPressureEmergency(long current) {
        if (DEBUG_RAMPOLICY) {
            Slog.d(TAG, "curent ->" + current);
            Slog.d(TAG, "idle ->" + mMemoryConstant.getNormalMemory());
            Slog.d(TAG, "emergency ->" + mMemoryConstant.getEmergencyMemory());
        }
        return current <= mMemoryConstant.getEmergencyMemory();
    }

    private void checkMemoryStatus(SystemStatus status) {
        if (DEBUG_RAMPOLICY) {
            Slog.d(TAG, "checkMemoryStatus:"
                    + (status.getRamStatus().getAvailableMemKb() / 1024)
                    + "mReclaimProcessEnabled = " + mReclaimProcessEnabled);
        }
        if (mInReclaimProcess) {
            return;
        }
        mInReclaimProcess = true;
        try {
            long currentMem = status.getRamStatus().getAvailableMemKb() / 1024;
            long idleTime = -1;
            if (mReclaimProcessEnabled && isMemoryPressureReclaim(currentMem)) {
                long request = mMemoryConstant.getNormalMemory();
                doMemoryReclaim(request * 1024,
                        mMemoryScene.getPolicyForScene(SCENE_IDLE));
            } else if (mReclaimProcessEnabled
                    && isMemoryPressureEmergency(currentMem)) {
                // try to reclaim persist-procs
                long request = mMemoryConstant.getEmergencyMemory();
                doMemoryReclaim(request * 1024,
                        mMemoryScene.getPolicyForScene(SCENE_EMERGENCY));
            }
            if (!mInStrumentation && (idleTime = mMemoryConstant.getCurrentForceStopIdleTime(currentMem)) != -1) {
                if (DEBUG_RAMPOLICY) Slog.d(TAG, "system under memPressure current available ->" + currentMem);
                String reason = "available memory:" + currentMem;
                doLRUReclaim(idleTime, false, reason);
            }
        } catch (Exception e) {
           e.printStackTrace();
        } finally {
            mInReclaimProcess = false;
        }
    }

    boolean isPackageMayBusyWithSth(String packageName) {
        SystemStatus status = mService.getCurrentSystemStatus(true);
        long currentMem = status.getRamStatus().getAvailableMemKb();
        if (mNotificationMan == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long lastPost = mNotificationMan
                .getAppLastActiveNotificationTime(packageName);
        long idle = now - lastPost;
        if (DEBUG_RAMPOLICY) {
            String lastDate = new java.text.SimpleDateFormat(
                    "yyyy MM-dd HH:mm:ss").format(new java.util.Date(lastPost));
            String nowDate = new java.text.SimpleDateFormat(
                    "yyyy MM-dd HH:mm:ss").format(new java.util.Date(System
                    .currentTimeMillis()));
            Slog.d(TAG, "package --" + packageName + " last post = "
                    + lastDate + "now = " + nowDate + "idle " + idle);
        }
        if (isMemoryPressureEmergency(currentMem)) {
            return idle <= NOTIFICATIONLOWTIME;
        } else {
            return idle <= NOTIFICATIONNORMALTIME;
        }
    }

    private void killOneProcess(int pid) {
        Process.killProcess(pid);
    }

    private String reclaimOneProcess(int pid, String type) {
        String result = "";
        try {
            result = PerformanceManagerNative.getDefault().reclaimProcess(pid,
                    type);
        } catch (Exception e) {
            // Slog.d(TAG, " exception in reclaimOneProcess " +e);
        }
        return result;
    }

    private boolean reclaimProcessEnabled() {
        boolean enabled = false;
        try {
            enabled = PerformanceManagerNative.getDefault()
                    .reclaimProcessEnabled();
        } catch (Exception e) {
            // Slog.d(TAG, " exception in reclaimOneProcess " +e);
        }
        Slog.d(TAG, "process-reclaim current enabled:" + enabled);
        return enabled;
    }

    private long getReclaimResult(String result) {
        long reclaimed = 0;
        if (DEBUG_RAMPOLICY) {
            Slog.d(TAG, "getReclaimResult " + result);
        }
        if ("".equals(result)) {
            return 0;
        }
        try {
            String[] temp = result.trim().split(",");
            String tmp = temp[0].replace("nr_reclaimed=", "");
            reclaimed = Integer.parseInt(tmp);
        } catch (Exception e) {
            // Slog.d(TAG, "encounter exception, read line "+ result, e);
        }
        return reclaimed;
    }

    private void doMemoryReclaim(long request, String policy) {
        if (policy == null) {
            return;
        }
        mHandler.post(new Runnable() {
            public void run() {
                Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                        "doMemoryReclaimInner:" + policy);
                try {
                    doMemoryReclaimInner(request, policy);
                } catch (Exception e) {
                }
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            }
        });
    }

    private void doMemoryReclaimInner(long request, String policy) {

        SystemStatus status = mService.getCurrentSystemStatus(true);
        long currentMem = status.getRamStatus().getAvailableMemKb();
        long requestMem = request - currentMem;
        long now = SystemClock.uptimeMillis();
        if (requestMem <= 0 || policy == null) {
            return;
        }
        if (DEBUG_RAMPOLICY) {
            Slog.d(TAG, "doProcessReclaim, request = " + requestMem);
            Slog.d(TAG, "befor reclaim ,dump mem");
            dumpMemInfo();
        }
        ArrayList<ReclaimProcessRecord> processes = getReclaimTargetProcesses(policy);
        if (processes == null || processes.size() == 0) {
            return;
        }
        int walk = -1;
        long reclaimed = 0;
        long nr_reclaimed = 0;
        long[] tmpLong = new long[2];
        while (requestMem >= reclaimed && ++walk < processes.size()) {
            ReclaimProcessRecord proc = processes.get(walk);
            boolean canFreeze = false;
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "reclaim:"
                    + proc.info.pid);
            nr_reclaimed = proc.reclaim();
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            reclaimed += nr_reclaimed * 4; // kB
            if (DEBUG_RAMPOLICY)
                Slog.d(TAG, "reclaimed -->" + proc + " for " + nr_reclaimed
                        + "pages");
        }
        if (DEBUG_RAMPOLICY) {
            Slog.d(TAG, "after reclaim " + reclaimed + "pages cost "
                    + (SystemClock.uptimeMillis() - now) + "ms,dump mem ");
            dumpMemInfo();
        }
    }

    private void dumpMemInfo() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(
                    new File("/proc/meminfo")));
            String info;
            while ((info = reader.readLine()) != null) {
                Slog.d(TAG, info);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void sortReclaimListByScore(ArrayList<ReclaimProcessRecord> list,
            String policy) {
        final int size = list.size();
        int maxReclaimCount = getMaxReclaimCount(policy);
        // sort lru by reclaim_score;
        if (DEBUG_RAMPOLICY)
            Slog.d(TAG, "sortReclaimListByScore, size:" + list.size());
        Collections.sort(list, new Comparator<ReclaimProcessRecord>() {
            @Override
            public int compare(ReclaimProcessRecord lhs,
                    ReclaimProcessRecord rhs) {
                if (lhs.launchCount == rhs.launchCount) {
                    if (lhs.info.curAdj != rhs.info.curAdj) {
                        return lhs.info.curAdj >= rhs.info.curAdj ? -1 : 1;
                    }
                    return 0;
                }
                return lhs.launchCount <= rhs.launchCount ? -1 : 1;
            }
        });
        if (DEBUG_RAMPOLICY)
            Slog.d(TAG, "after sort");
        if (DEBUG_RAMPOLICY) {
            for (ReclaimProcessRecord r : list) {
                Slog.d(TAG, "sortReclaimListByScore -->" + r);
            }
        }
        if (size > maxReclaimCount) {
            list = new ArrayList(list.subList(0, maxReclaimCount));
        }
        if (DEBUG_RAMPOLICY)
            Slog.d(TAG, "sortReclaimListByScore,after clip size:" + list.size());
        for (ReclaimProcessRecord r : list) {
            switch (r.policy) {
            case POLICY_KILL:
            case POLICY_QUICK_KILL:
                long pss = Process.getPss(r.info.pid) / 1024;
                r.setPss(pss);
                break;
            case POLICY_RECLAIM:
            case POLILCY_RECLAIM_PERSIST:
                /*
                 * Debug.MemoryInfo mi = new Debug.MemoryInfo();
                 * Debug.getMemoryInfo(r.info.pid, mi);
                 * r.setUss((long)mi.getSummaryNativeHeap());
                 */
                break;
            default:
                break;
            }
        }
        if (DEBUG_RAMPOLICY)
            Slog.d(TAG, "sortReclaimListByScore, calc score done.");
        if (DEBUG_RAMPOLICY) {
            for (ReclaimProcessRecord r : list) {
                Slog.d(TAG, "sortReclaimListByScore -->" + r);
            }
        }
    }

    private int getMaxReclaimCount(String policy) {
        int maxCount = MAX_RECLAIM_COUNT;
        switch (policy) {
        case POLICY_KILL:
        case POLICY_QUICK_KILL:
        case POLICY_RECLAIM:
            break;
        case POLILCY_RECLAIM_PERSIST:
            maxCount = MAX_RECLAIM_PERSIST_COUNT;
            break;
        default:
            break;
        }
        return maxCount;
    }

    private boolean processImportanceReclaimable(int adj, String policy) {
        boolean reclaimable = false;
        switch (policy) {
        case POLICY_KILL:
        case POLICY_QUICK_KILL:
            reclaimable = adj >= RECLAIM_ADJ_BOOST_KILL;
            break;
        case POLICY_RECLAIM:
            reclaimable = adj >= RECLAIM_ADJ_THROL_RECLAIM;
            break;
        case POLILCY_RECLAIM_PERSIST:
            reclaimable = (adj == ProcessInfo.PERSISTENT_PROC_ADJ
                    || adj == ProcessInfo.PERSISTENT_SERVICE_ADJ
                    || adj == ProcessInfo.PERCEPTIBLE_APP_ADJ || adj == ProcessInfo.VISIBLE_APP_ADJ);
            break;
        default:
            break;
        }
        return reclaimable;
    }

    @Override
    protected void handleInStrumentationChange(Message msg) {
        mInStrumentation = true;
    }

    private void doLRUReclaim(long idleTime, boolean force, String reason) {
        List<LRUReclaimProcessRecord> procs = getLeastRecentUsedPkg(idleTime, force);
        if (procs != null && procs.size() > 0) {
            for (LRUReclaimProcessRecord proc : procs) {
                if (isSpecialPackage(proc.pkgName)) {
                    if (DEBUG_RAMPOLICY) {
                        Slog.d(TAG, "Killing uid:" + proc.uid + proc.pkgName + "due to "
                                    + reason + "to free " + proc.rss + "KB");
                    }
                    mAm.killUid(UserHandle.getAppId(proc.uid), UserHandle.getUserId(proc.uid), "rampolicy");
                } else {
                    Slog.d(TAG, "force-stop " + proc.pkgName + "due to " + reason
                                + "to free " + proc.rss + "KB");
                    mAm.forceStopPackage(proc.pkgName, UserHandle.USER_CURRENT);
                }
            }
        }
    }

    private boolean isPackageLRUReclaimable(LRUReclaimProcessRecord result, String homePkg, ArrayList<String> enabledInputMethods,
                              ArrayList<ProcessInfo> runningList, UserHabit stat, long idleTime, boolean force) {
        String pkgName = stat.getPackageName();

        if (homePkg != null  && homePkg.equals(pkgName)) {
            //if (DEBUG_RAMPOLICY) Slog.d(TAG, "Stop-LRU skip " + pkgName+ " is HOME!");
            return false;
        }
        if (mCurrentFocusPackage != null && mCurrentFocusPackage.equals(pkgName)) {
            if (DEBUG_RAMPOLICY) Slog.d(TAG, "Stop-LRU skip " + pkgName + " is focus");
            return false;
        }
        try {
            ApplicationInfo info = mService.getContext().getPackageManager().getApplicationInfo(pkgName, 0);
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                //Slog.d(TAG, "Stop-LRU skip " + pkgName + " is system");
                return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
                //e.printStackTrace();
            return false;
        }
        if (enabledInputMethods != null && enabledInputMethods.contains(pkgName)) {
            return false;
        }
        ArrayList<ProcessInfo> list = getProcessInfoListByPkgName(runningList, pkgName);
        if (list == null || list.size() == 0) {
            //Slog.d(TAG, "Stop-LRU skip " + pkgName + " empty process??");
            return false;
        }
        mCurrentBgRunningPkgCount++;

        if (mService.isProcessHeldWakeLock(list.get(0).uid, WAKELOCK_PROTECTED_TIME)) {
            if (DEBUG_RAMPOLICY) Slog.d(TAG, "Stop-LRU skip " + pkgName + " is doing something");
            return false;
        }
        //pass percepitable
        for (ProcessInfo pi : list) {
            if (pi != null && pi.curAdj <= FS_RECLAIM_ADJ) {
                if (DEBUG_RAMPOLICY) Slog.d(TAG, "Stop-LRU skip " + pkgName + "process:" + pi + " curAdj ->"+pi.curAdj);
                return false;
            }
        }
        //pass favorite.. if not critial
        if (!force) {
            if (mService.isPackageUsrFavorite(pkgName)) {
                if (DEBUG_RAMPOLICY) Slog.d(TAG, "Stop-LRU skip " + pkgName + " is Favorite!:");
                return false;
            }
        }
        //exclude packages those used in 5mins?
        long usedTime = System.currentTimeMillis() - stat.getLastTimeUsed();
        if (usedTime < idleTime) {
            if (DEBUG_RAMPOLICY) Slog.d(TAG, "Stop-LRU skip " + pkgName + " is recent used!:"+usedTime);
            return false;
        }
        if (isPackageMayBusyWithSth(pkgName)) {
            return false;
        }
        //will lock ams, be carefull.
        if (hasRelativeToptask(pkgName)) {
            if (DEBUG_RAMPOLICY) Slog.d(TAG, "Stop-LRU skip " + pkgName + " hasRelativeToptask!");
            return false;
        }

        long totalRss = 0;
        for (ProcessInfo pi : list) {
            if (pi != null) {
                totalRss += getProcRss(pi.pid);
            }
        }
        if (DEBUG_RAMPOLICY) {
            Slog.d(TAG, "Stop-LRU add " + pkgName + " RSS: " + totalRss + "KB");
        }
        result.uid = list.get(0).uid;
        result.rss = totalRss;
        result.procs = list;
        return true;
    }

    private long getProcRss(int pid) {
        String result = "";
        try {
            result =
                    PerformanceManagerNative.getDefault().readProcFile(
                            "/proc/" + pid + "/statm");
        } catch (Exception e) {
        }
        if (result != null && ("".equals(result) == false)) {
            String[]  temp = result.split(" ");
            if (temp != null && temp.length > 2) {
                return Long.valueOf(temp[1]) * 4;
            }
        }
        return 0;
    }
    private boolean hasRelativeToptask(String pkgName) {
        String pkg = mAm.getTopActivityCallingPkg();
        return pkg != null  && pkg.equals(pkgName);
    }

    private ArrayList<String> getEnabledInputMethods(){
        IBinder b = ServiceManager.getService(Context.INPUT_METHOD_SERVICE);
        IInputMethodManager service = IInputMethodManager.Stub.asInterface(b);
        List<InputMethodInfo> inputMethods;
        try {
            inputMethods = service.getEnabledInputMethodList();
        } catch (RemoteException e) {
            return null;
        }
        ArrayList<String> list = new ArrayList<>();
        if (inputMethods != null && inputMethods.size() != 0) {
            for (InputMethodInfo info : inputMethods) {
                if (info == null || info.getPackageName() == null) continue;
                list.add(info.getPackageName());
            }
        }
        return list;
    }

    private  List<LRUReclaimProcessRecord> getLeastRecentUsedPkg(long idleTime, boolean force) {
        ArrayList<UserHabit> us = new ArrayList<>();
        ArrayList<LRUReclaimProcessRecord> uList = new ArrayList<>();
        String homePkg = getHomePackageName();
        int bgLimit = mMemoryConstant.getBackgroundPkgRunningLimit();
        if (mUserHabitCollector != null) {
            us= (ArrayList<UserHabit>) mUserHabitCollector.getUserHabits(UserHandle.myUserId()).clone();
        }
        if (us.size() == 0) {
            return null;
        }
        mCurrentBgRunningPkgCount = 0;
        ArrayList<String> enabledInputMethods = getEnabledInputMethods();
        ArrayList<ProcessInfo> runningList = mAm.getRunningProcessesInfo();
        for (UserHabit stat : us) {
            LRUReclaimProcessRecord r = new LRUReclaimProcessRecord();
            if (isPackageLRUReclaimable(r, homePkg, enabledInputMethods, runningList, stat, idleTime, force)) {
                //Slog.d(TAG, "LRU adding "+stat.getPackageName());
                r.habit = stat;
                r.pkgName = stat.getPackageName();
                uList.add(r);
            }
        }
        if (DEBUG_RAMPOLICY) Slog.d(TAG, "current running pkg count:" + mCurrentBgRunningPkgCount +
                                     "force-stop target count :" + uList.size() + "limit is " + bgLimit);
        if (uList.size() == 0) {
            return null;
        }
        if (!force && mCurrentBgRunningPkgCount <= bgLimit) {
            return null;
        }
        // reclaim ---> [0, bgCount - bgLimit)
        int reclaimRange = mCurrentBgRunningPkgCount - bgLimit;
        if (reclaimRange <= 0) {
            reclaimRange = 1;
        }
        if (DEBUG_RAMPOLICY) Slog.d(TAG, "reclaimRange:" + reclaimRange);
        // if needed is greater than present, get them all.
        if (reclaimRange >= uList.size()) {
            return uList;
        }
        // else sort it,get [0, reclaimRange)
        Collections.sort(uList, new Comparator<LRUReclaimProcessRecord>() {
            @Override
            public int compare(LRUReclaimProcessRecord lhs, LRUReclaimProcessRecord rhs) {
                if (lhs.habit.lastUseTime == rhs.habit.lastUseTime) {
                    return 0;
                }
                return lhs.habit.lastUseTime <= rhs.habit.lastUseTime ? -1 : 1;
            }
        });
        List<LRUReclaimProcessRecord> result = new ArrayList<>();
        for (int f = 0; f < uList.size(); f ++) {
            if (f < reclaimRange) {
                result.add(uList.get(f));
            }
        }
        return result;
    }

    private ArrayList<ProcessInfo> getProcessInfoListByPkgName(ArrayList<ProcessInfo> runningList, String pkgName) {
        if (pkgName == null) {
            return null;
        }
        ArrayList<ProcessInfo> out = new ArrayList<>();
        for (ProcessInfo app : runningList) {
            if (pkgName.equals(app.packageName)) {
                //Slog.d(TAG, "getProcessInfoListByPkgName " + pkgName + "--->" + app);
                out.add(app);
            }
        }
        return out;
    }

    boolean isSpecialPackage(String packageName) {
        for (String name : mSpecialPackages) {
            if (packageName.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private ArrayList<ReclaimProcessRecord> getReclaimTargetProcesses(String policy) {
        ArrayList<ReclaimProcessRecord> list = new ArrayList<>();
        ArrayList<ProcessInfo> runningList = mAm.getRunningProcessesInfo();
        for (int i = 0; i < runningList.size(); i++) {
            ProcessInfo p = runningList.get(i);
            if (p != null && processImportanceReclaimable(p.curAdj, policy)) {
                UserHabit habit = mService.getUserHabitCollector().getPackageUserHabits(UserHandle.myUserId(),
                        p.packageName);
                ReclaimProcessRecord record = new ReclaimProcessRecord(p, habit, policy);
                list.add(record);
                if (DEBUG_RAMPOLICY) {
                    Slog.d(TAG, "add process" + record + "policy" + policy);
                }
            }
        }
        sortReclaimListByScore(list, policy);
        return list;
    }

    @Override
    public void dump(PrintWriter pw, String[] args) {
        pw.println("RamPolicy:");
        pw.println("----------------------------");
        pw.println("Process-reclaim current enabled" + mReclaimProcessEnabled);
        if (mMemoryConstant != null && mMemoryScene != null) {
            mMemoryConstant.dump(pw);
            mMemoryScene.dump(pw);
        }
        pw.println("----------------------------");
    }
}
