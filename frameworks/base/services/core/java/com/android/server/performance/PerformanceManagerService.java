/*
 * Copyright 2017 Spreadtrum Communications Inc.
 */

package com.android.server.performance;

import android.app.ActivityManagerNative;
import android.app.ActivityManager;
import android.app.IPerformanceManagerInternal;
import android.app.TaskThumbnail;
import android.app.PerformanceManagerInternal;
import android.app.PerformanceManagerNative;
import android.app.UserHabit;
import android.app.ProcState;
import android.app.ProcessInfo;
import android.app.UserSwitchObserver;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Bundle;
import android.os.Binder;
import android.os.Environment;
import android.os.Message;
import android.os.UserHandle;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.SystemProperties;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.util.Slog;
import android.hardware.power.V1_0.PowerHint;

import com.android.server.am.ActivityManagerServiceEx;
import com.android.server.LocalServices;
import com.android.server.performance.collector.CpuStatusCollector;
import com.android.server.performance.collector.ProcessStateCollector;
import com.android.server.performance.collector.UserHabitCollector;
import com.android.server.performance.policy.ram.*;
import com.android.server.performance.policy.sched.ProcessSchedExecutor;
import com.android.server.performance.policy.io.*;
import com.android.server.performance.policy.powerhint.PowerHintPolicyExecutor;
import com.android.server.performance.policy.oomadj.OomAdjPolicyExecutor;
import com.android.server.performance.snapshot.TaskSnapShotManager;
import com.android.server.performance.status.CpuStatus;
import com.android.server.performance.status.SystemStatus;
import com.android.server.power.sprdpower.PowerController;
import android.os.sprdpower.PowerControllerInternal;
import com.android.internal.util.DumpUtils;
import com.android.internal.app.procstats.ProcessStats;
import com.android.internal.R;

import static android.os.Process.SCHED_FIFO;
import static android.os.Process.SCHED_OTHER;
import static android.os.Process.SCHED_RESET_ON_FORK;
import static android.app.ProcessInfo.*;
import static com.android.server.performance.PerformanceManagerDebugConfig.*;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PerformanceManagerService extends IPerformanceManagerInternal.Stub {

    private Context mContext;
    private HandlerThread mThread;
    private Handler mHandler;
    private MonitorRunnable mMonitorRunnable;
    private String mlocale = null;
    // private final String PERFORMANCE_CONFIG_FILE =
    // "/system/etc/sprd_performance_config.xml";

    private static final long POLL_DELAY = ActivityManager.isLowRamDeviceStatic() ? 1 * 60 * 1000 : 2 * 60 * 1000;
    private static final long POLL_DELAY_START_PROC = 15 * 1000;
    private static final long TOUCH_BOOST_REMOVE_DUR = 1000;

    // for handle events
    private static final int MSG_INIT_MODULES = 0;
    private static final int MSG_ACTIVITY_STATE_CHANGE = 1;
    private static final int MSG_SCREEN_ON = 2;
    private static final int MSG_SCREEN_OFF = 3;
    private static final int MSG_CONFIGURATION_CHANGED = 4;
    private static final int MSG_LOCALE_CHANGED = 5;
    private static final int MSG_SHUTDOWN = 6;
    private static final int MSG_USER_SWITCHED = 7;
    private static final int MSG_INSTRUMENTATION_CHANGE = 8;
    private static final int MSG_BOOST_PR = 9;
    private static final int MSG_BOOST_PR_WHEN_TOUCH = 10;
    private static final int MSG_BOOST_AS_REGUAL = 11;

    // work around for test
    private static AtomicBoolean mRunCts = new AtomicBoolean(false);
    private static String mVmpressureKnob = "/sys/module/lowmemorykiller/parameters/enable_adaptive_lmk";
    private String mOomCatcher = "com.android.cts.oomcatcher";
    private BroadcastReceiver mBroadcastReceiver = new PerformanceBroadcastReceiver();
    private SystemStatus mLastSystemStatus;
    private Object mSystemStatusLock = new Object();
    private HashMap<Integer, WakeLockData> mBusyUids = new HashMap<>();
    private UserHabitCollector mUserHabitCollector;
    private ProcessStateCollector mProcessStateCollector;
    private CpuStatusCollector mCpuStatusCollector;
    private TaskSnapShotManager mTaskSnapShotManager;
    private RamPolicyExecutor mRamPolicyExecutor;
    private PowerHintPolicyExecutor mPowerHintPolicyExecutor;
    private ProcessSchedExecutor mProcessSchedExecutor;
    private OomAdjPolicyExecutor mOomAdjPolicyExecutor;
    private ExtraFetch mExtraFetch;
    private ActivityManagerServiceEx mAm;
    PowerManagerInternal mLocalPowerManager;
    private boolean monitorEnabled = true;
    private String mCurrentFocusPackage = "";
    private Intent mCurrentFocusIntent = null;
    private int mRecentForkedPid = -1;
    private int mRamConfig = -1;
    // policy executors
    List<PolicyExecutor> mPolicyExecutors = new ArrayList<PolicyExecutor>();

    // modify for performance Bug #685274
    private List<String> mImpAppList;
    private List<String> mConfigChangeBoost;
    private boolean mEnhancedHintForLaunch = false;
    private boolean mInStrumentationChange = false;
    PowerController.LocalService mPowerControllerInternal = null;

    public static class WakeLockData {
        public int uid;
        public boolean held;
        public long lastHeldTime;
    }

    public static class InStrumentationData {
        public ProcessInfo app;
        public InstrumentationInfo iInfo;
        public boolean start;
    }

    public static class ActivityStateData {
        public Intent intent;
        public Bundle bundle;
    }

    public static class BoostAppInfo {
        public boolean boost;
        public int id;
        public boolean isPid;
    }

    public static class VmpressureData {
        public int swapUsage;
        public int pressure;
    }
    public PerformanceManagerService(Context context) {
        mContext = context;
        mAm = (ActivityManagerServiceEx) ActivityManager.getService();
        initMainHandler();
        mHandler.sendEmptyMessage(MSG_INIT_MODULES);
    }

    private void initMainHandler() {
        mThread = new HandlerThread(TAG);
        mThread.start();
        mHandler = new Handler(mThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "handleMessage:" + msg.what);
                switch (msg.what) {
                case MSG_INIT_MODULES: {
                    handleInitModules();
                    break;
                }
                case MSG_ACTIVITY_STATE_CHANGE: {
                    Bundle bundle = msg.getData();
                    Intent intent = (Intent) msg.obj;
                    handleActivityStateChange(intent, bundle);
                    break;
                }
                case MSG_SCREEN_ON: {
                    changeMonitorSystemStatus(true, POLL_DELAY);
                    break;
                }
                case MSG_SCREEN_OFF: {
                    changeMonitorSystemStatus(false, POLL_DELAY);
                    break;
                }
                case MSG_CONFIGURATION_CHANGED: {
                    break;
                }
                case MSG_LOCALE_CHANGED: {
                    handleLocaleChange();
                    break;
                }
                case MSG_SHUTDOWN: {
                    handleShutDown();
                    break;
                }
                case MSG_USER_SWITCHED: {
                    handleUserChange(msg.arg1);
                    break;
                }
                case MSG_INSTRUMENTATION_CHANGE: {
                    handleInStrumentationChange(msg);
                    break;
                }
                case MSG_BOOST_PR: {
                    handleScheduleBoostRRForApp(msg);
                    break;
                }
                case MSG_BOOST_PR_WHEN_TOUCH: {
                    if (mProcessSchedExecutor == null) {
                        break;
                    }
                    mProcessSchedExecutor.scheduleBoostsWhenTouch();
                    break;
                }
                case MSG_BOOST_AS_REGUAL: {
                    if (mProcessSchedExecutor == null) {
                        break;
                    }
                    mProcessSchedExecutor.scheduleBoostsAsRegual();
                    break;
                }
                default:
                    break;
                }
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            }
        };
    }

    private void handleInitModules() {
        mMonitorRunnable = new MonitorRunnable();
        mUserHabitCollector = new UserHabitCollector(mAm, mHandler);
        // mExtraFetch = new ExtraFetch(this);
        if (!ActivityManager.isLowRamDeviceStatic()) {
            mCpuStatusCollector = new CpuStatusCollector(this);
            mProcessStateCollector = new ProcessStateCollector(mAm, mHandler);
        }
        mTaskSnapShotManager = new TaskSnapShotManager(this, mAm, mContext);
        initPolicyExecutorsFromConfig();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        mContext.registerReceiverAsUser(mBroadcastReceiver, UserHandle.ALL, filter, null, null);
        mLocalPowerManager = LocalServices.getService(PowerManagerInternal.class);
        mEnhancedHintForLaunch =
                mContext.getResources().getBoolean(com.android.internal.R.bool.config_enhanced_powerhint);
        startMonitorSystemStatus(POLL_DELAY);
        initImpList();
        initConfigChangeBoostList();
        initUserSwitchObserver();
        Slog.d(TAG, "init done");
    }

    // modify for performance Bug #685274 begin
    private void initImpList() {
        String[] impAppList = mContext.getResources().getStringArray(com.android.internal.R.array.config_imp_app_list);
        if (impAppList != null && !ActivityManager.isLowRamDeviceStatic()) {
            mImpAppList = Arrays.asList(impAppList);
        }
    }

    // modify for performance Bug #685274 end

    private void initConfigChangeBoostList() {
        String[] configChangeBoost =
                mContext.getResources().getStringArray(com.android.internal.R.array.config_change_boost_list);
        if (configChangeBoost != null) {
            mConfigChangeBoost = Arrays.asList(configChangeBoost);
        }
    }

    private void initUserSwitchObserver() {
        try {
            mAm.registerUserSwitchObserver(new UserSwitchObserver() {
                @Override
                public void onUserSwitchComplete(int newUserId) throws android.os.RemoteException {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_SWITCHED, newUserId, 0));
                }
            }, TAG);
        } catch (Exception e) {
        }
    }

    private void startMonitorSystemStatus(long delay) {
        if (monitorEnabled) {
            mHandler.removeCallbacks(mMonitorRunnable);
            mHandler.postDelayed(mMonitorRunnable, delay);
        }
    }

    private void changeMonitorSystemStatus(boolean enable, long delay) {
        monitorEnabled = enable;
        mHandler.removeCallbacks(mMonitorRunnable);
        if (monitorEnabled) {
            startMonitorSystemStatus(delay);
        }
    }

    private class MonitorRunnable implements Runnable {
        @Override
        public void run() {
            if (monitorEnabled) {
                Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "monitorSystemStatus");
                monitorSystemStatus();
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            }
        }
    }

    private void specialActionForTest(Intent intent) {
        if (mRunCts.get() == true)
            return;

        ComponentName app = intent.getComponent();
        String pkgname = app.getPackageName();

        if (pkgname == null)
            return;

        if (pkgname.toLowerCase().contains(".cts") || pkgname.toLowerCase().contains(".gts")) {
            mRunCts.set(true);
            // disable vmpressure in low_ram
            Slog.d(TAG, "disable vmpressure on specialActionForTest.");
            try {
                PerformanceManagerNative.getDefault().writeProcFile(mVmpressureKnob, "0");
            } catch (Exception e) {
                if (DEBUG_SERVICE)
                    Slog.e(TAG, "exception happend in disable vmpressure " + e);
            }
        }
    }

    private void handleActivityStateChange(Intent intent, Bundle bundle) {
        if (bundle == null || intent == null) {
            return;
        }
        int state = bundle.getInt(KEY_ACTIVITY_STATE);
        ComponentName app = intent.getComponent();
        if (app == null) {
            return;
        }
        ActivityStateData aData = new ActivityStateData();
        aData.intent = intent;
        aData.bundle = bundle;
        boolean appSwitch = false;
        // for modules
        switch (state) {
        case ACTIVITY_STATE_START: {
            break;
        }
        case ACTIVITY_STATE_STOP: {
            break;
        }
        case ACTIVITY_STATE_PAUSE: {
            if (mUserHabitCollector != null) {
                mUserHabitCollector.updateHabitStatus(app.getPackageName(), false, false);
            }
            break;
        }
        case ACTIVITY_STATE_RESUME: {
            if (!mCurrentFocusPackage.equals(app.getPackageName())) {
                appSwitch = true;
            }
            if (mUserHabitCollector != null) {
                mUserHabitCollector.updateHabitStatus(app.getPackageName(), true, appSwitch);
            }
            mCurrentFocusPackage = app.getPackageName();
            mCurrentFocusIntent = intent;
            if (mTaskSnapShotManager != null) {
                mTaskSnapShotManager.handleActivityResume(intent);
            }
            break;
        }
        case ACTIVITY_STATE_FINISH: {
            if (mCpuStatusCollector != null) {
                mCpuStatusCollector.updateCpuUsageNow();
            }
            break;
        }
        case ACTIVITY_STATE_LAUNCHDONE: {
            long launchTime = bundle.getLong(KEY_LAUNCH_TIME);
            if (mUserHabitCollector != null && launchTime != 0) {
                mUserHabitCollector.updateHabitsLaunchTime(app.getPackageName(), launchTime);
            }
            if (mTaskSnapShotManager != null && launchTime != 0) {
                mTaskSnapShotManager.handleActivityLaunch(intent, bundle);
            }
            if (mRecentForkedPid != -1 && mExtraFetch != null && launchTime != 0) {
                mExtraFetch.handleProcessColdLaunchDone(intent, mRecentForkedPid);
                mRecentForkedPid = -1;
            }
            if (DEBUG_SERVICE && launchTime != 0) {
                Slog.d(TAG, "app:" + app + " launchtime= " + launchTime);
                dumpProcess();
            }
            break;
        }
        case ACTIVITY_STATE_PROC_START: {
            specialActionForTest(intent);
            String hostingType = bundle.getString(ProcessInfo.KEY_START_PROC_HOST_TYPE);
            int pid = Integer.valueOf(bundle.getString(ProcessInfo.KEY_START_PROC_PID));
            if (DEBUG_SERVICE)
                Slog.d(TAG, "start proc for " + app.getPackageName() + " type:" + hostingType + "pid" + pid);
            if (hostingType != null && "activity".equals(hostingType)) {
                if (mCpuStatusCollector != null) {
                    mCpuStatusCollector.scheduleUpdateCpuUsage(CpuStatusCollector.MONITOR_CPU_MIN_TIME);
                }
                changeMonitorSystemStatus(true, POLL_DELAY_START_PROC);
                if (mExtraFetch != null) {
                    mExtraFetch.handleProcessColdLaunchStart(intent, pid);
                }
                mRecentForkedPid = pid;
            }
            break;
        }
        default:
            break;
        }
        // notify policy excutors
        notifyPolicyExecutorsActivityStateChanged(aData);
    }

    // for debug
    private void dumpProcess() {
        ArrayList<ProcessInfo> runningList = mAm.getRunningProcessesInfo();
        ProcessInfo top = null;
        for (int i = 0; i < runningList.size(); i++) {
            ProcessInfo p = runningList.get(i);
            if (p != null && p.curAdj == 0) {
                top = p;
                break;
            }
        }
        if (top == null) {
            return;
        }
        String mainThread = "/proc/" + top.pid + "/task/" + top.pid + "/stat";
        Slog.d(TAG, "dumping :" + mainThread);
        String result = "";
        try {
            result =
                    PerformanceManagerNative.getDefault().readProcFile(
                            "/proc/" + top.pid + "/task/" + top.pid + "/stat");
        } catch (Exception e) {
            // Slog.e(TAG, " exception in enableBoostKill " +e);
        }
        Slog.d(TAG, "stat :" + result);
    }

    // for debug
    private void handleConfigurationChange() {
        Configuration config = mContext.getResources().getConfiguration();
    }

    private void handleLocaleChange() {
        try {
            Slog.d(TAG, "thumbnail mlocale " + mlocale + ", new locale " + Locale.getDefault().getLanguage());
            if (mlocale != null && mlocale != Locale.getDefault().getLanguage()) {
                if (mTaskSnapShotManager != null) {
                    mTaskSnapShotManager.handleLocaleChange();
                }
            }
            mlocale = Locale.getDefault().getLanguage();
        } catch (Exception e) {
        }
    }

    private void handleShutDown() {
        if (mUserHabitCollector != null) {
            mUserHabitCollector.onShutDown();
        }
        if (mProcessStateCollector != null) {
            mProcessStateCollector.onShutDown();
        }
        if (mExtraFetch != null) {
            mExtraFetch.onShutDown();
        }
    }

    private void handleUserChange(int userId) {
        try {
            Slog.d(TAG, "thumbnail User Change");
            if (mTaskSnapShotManager != null) {
                mTaskSnapShotManager.handleUserChange(userId);
            }
        } catch (Exception e) {
        }
    }

    private class PerformanceBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
                Slog.d(TAG, "configuration change");
                mHandler.sendEmptyMessage(MSG_CONFIGURATION_CHANGED);
            } else if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
                Slog.d(TAG, "Locale changed");
                mHandler.sendEmptyMessage(MSG_LOCALE_CHANGED);
            } else if (Intent.ACTION_SHUTDOWN.equals(action)) {
                onShutDown();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mHandler.sendEmptyMessage(MSG_SCREEN_ON);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mHandler.sendEmptyMessage(MSG_SCREEN_OFF);
            }
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                String pName="";
                PackageManager packageManager = context.getPackageManager();
                List<PackageInfo> pinfo = packageManager.getInstalledPackages(0);
                if (pinfo != null) {
                    for (int i = 0; i < pinfo.size(); i++) {
                        pName = pinfo.get(i).packageName;
                        if (mOomCatcher.equals(pName)) {
                            try {
                                Slog.d(TAG, "disable vmpressure on PerformanceBroadcastReceiver.");
                                PerformanceManagerNative.getDefault().writeProcFile(mVmpressureKnob, "0");
                            } catch (Exception e) {
                                if (DEBUG_SERVICE)
                                    Slog.e(TAG, "exception happend in PerformanceBroadcastReceiver " + e);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private void notifyPolicyExecutorsSystemStatusChanged(SystemStatus status) {
        synchronized (mPolicyExecutors) {
            for (PolicyExecutor executor : mPolicyExecutors) {
                executor.onSystemStatusChanged(status);
            }
        }
    }

    public CpuStatusCollector getCpuStatusCollector() {
        return mCpuStatusCollector;
    }

    private void monitorSystemStatus() {
        SystemStatus status = getCurrentSystemStatus(true);
        if (mProcessStateCollector != null) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "updateProcStates");
            mProcessStateCollector.updateProcStates();
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        }
        notifyPolicyExecutorsSystemStatusChanged(status);
        startMonitorSystemStatus(POLL_DELAY);
    }

    private void notifyPolicyExecutorsActivityStateChanged(ActivityStateData aData) {
        synchronized (mPolicyExecutors) {
            for (PolicyExecutor executor : mPolicyExecutors) {
                executor.onActivityStateChanged(aData);
            }
        }
    }

    public void notifyPolicyExecutorsCpuStatusChanged(CpuStatus cpu) {
        synchronized (mPolicyExecutors) {
            for (PolicyExecutor executor : mPolicyExecutors) {
                executor.onCpusStatusChanged(cpu);
            }
        }
    }

    public void notifyExecutorsVmpressureChanged(VmpressureData data) {
        synchronized (mPolicyExecutors) {
            for (PolicyExecutor executor : mPolicyExecutors) {
                executor.onVmpressureChanged(data);
            }
        }
    }
    private void initPolicyExecutorsFromConfig() {
        synchronized (mPolicyExecutors) {
            try {
                Map<String, Class<? extends PolicyExecutor>> prototypes =
                        new HashMap<String, Class<? extends PolicyExecutor>>();
                prototypes.put("Memory", RamPolicyExecutor.class);
                prototypes.put("Schedule", ProcessSchedExecutor.class);
                prototypes.put("PowerHint", PowerHintPolicyExecutor.class);
                prototypes.put("OomAdj", OomAdjPolicyExecutor.class);
                List<PolicyItem> configs = PolicyExecutorConfigLoader.load();
                for (PolicyItem config : configs) {
                    Class<? extends PolicyExecutor> prototype = prototypes.get(config.getName());

                    if (prototype == null) {
                        continue;
                    }
                    PolicyExecutor executor =
                            prototype.getConstructor(PolicyItem.class, PerformanceManagerService.class).newInstance(
                                    config, this);
                    mPolicyExecutors.add(executor);
                    if (executor instanceof RamPolicyExecutor) {
                        mRamPolicyExecutor = (RamPolicyExecutor) executor;
                        Slog.d(TAG, "created RamPolicyExecutor..");
                    } else if (executor instanceof PowerHintPolicyExecutor) {
                        mPowerHintPolicyExecutor = (PowerHintPolicyExecutor) executor;
                        Slog.d(TAG, "created PowerHintPolicyExecutor..");
                    } else if (executor instanceof OomAdjPolicyExecutor) {
                        mOomAdjPolicyExecutor = (OomAdjPolicyExecutor) executor;
                        Slog.d(TAG, "created OomAdjPolicyExecutor..");
                    } else if (executor instanceof ProcessSchedExecutor) {
                        mProcessSchedExecutor = (ProcessSchedExecutor) executor;
                        Slog.d(TAG, "created ProcessSchedExecutor..");
                    }
                }
            } catch (Exception e) {
                // e.printStackTrace();
            }
        }
    }

    private boolean isCurrentMemPressureNormal() {
        if (mRamPolicyExecutor != null) {
            return mRamPolicyExecutor.isMemoryPressureNormal();
        } else {
            return false;
        }
    }

    public boolean isProcessHeldWakeLock(int uid) {
        synchronized (mBusyUids) {
            WakeLockData data = mBusyUids.get(uid);
            return data != null && data.held;
        }
    }

    public boolean isProcessHeldWakeLock(int uid, long limit) {
        long now = SystemClock.uptimeMillis();
        synchronized (mBusyUids) {
            WakeLockData data = mBusyUids.get(uid);
            return data != null && (data.held || (now - data.lastHeldTime) < limit);
        }
    }

    public boolean isBusy(String pkgName, int uid) {
        if (mPowerControllerInternal == null) {
            mPowerControllerInternal = LocalServices.getService(PowerController.LocalService.class);
        }
        int type = mPowerControllerInternal.getAppBehaviorType(pkgName, uid);
        if (DEBUG_SERVICE)
            Slog.w(TAG, "isBusy with something:" + type);
        return type != PowerControllerInternal.APP_ACTION_NONE;
    }

    public boolean pkgSupportRecentThumbnail(String pkgName) {
        if (mTaskSnapShotManager != null) {
            return mTaskSnapShotManager.pkgSupportRecentThumbnail(pkgName);
        }
        return false;
    }

    public void removePendingUpdateThumbTask() {
        Slog.w(TAG, "update thumbnail cancelled");
        if (mTaskSnapShotManager != null) {
            mTaskSnapShotManager.removePendingUpdateThumbTask();
        }
    }

    /* package */public SystemStatus getCurrentSystemStatus(boolean update) {
        if (update) {
            synchronized (mSystemStatusLock) {
                mLastSystemStatus = new SystemStatus(this);
            }
        }
        return mLastSystemStatus;
    }

    /* package */public UserHabitCollector getUserHabitCollector() {
        return mUserHabitCollector;
    }

    /* package */public ProcState getProcessState(String processName) {
        if (mProcessStateCollector != null) {
            return mProcessStateCollector.getProcStates(UserHandle.myUserId(), processName);
        }
        return null;
    }

    public Context getContext() {
        return mContext;
    }

    /* IPerformanceManagerInternal for clients */
    public void windowReallyDrawnDone(String pkgName) {
    }

    public void removeApplcationSnapShot(String pkgName) {
        if (mTaskSnapShotManager != null && pkgName != null) {
            mTaskSnapShotManager.recentTaskThumbnailListRemoveApp(pkgName);
        }
    }

    public TaskThumbnail getTaskThumbnail(Intent intent) {
        if (mTaskSnapShotManager != null) {
            return mTaskSnapShotManager.getTaskThumbnail(intent);
        }
        return null;
    }

    public void onShutDown() {
        Slog.d(TAG, "shutdown");
        mHandler.sendEmptyMessage(MSG_SHUTDOWN);
    }

    public void notifyActivityStateChange(Intent intent, Bundle bundle) {
        if (bundle != null) {
            Message msg = new Message();
            msg.what = MSG_ACTIVITY_STATE_CHANGE;
            msg.setData(bundle);
            msg.obj = intent;
            mHandler.sendMessage(msg);
        }
    }

    public void notifyUnfreezeProcessLocked(int pid) {
    }

    public int processLmkAdjTunningIfneeded(ProcessInfo app, int memLvl, long now, long nowElapsed) {
        if (mOomAdjPolicyExecutor == null) {
            return ProcessInfo.PROCESS_LMK_ADJ_TUNNING_NONEED;
        }
        if (mOomAdjPolicyExecutor.increaseAdjIfNeeded(app, memLvl, now, nowElapsed)) {
            return ProcessInfo.PROCESS_LMK_ADJ_TUNNING_INCREASE;
        } else if (mOomAdjPolicyExecutor.doAdjDropIfNeeded(app, memLvl, now, nowElapsed)) {
            return ProcessInfo.PROCESS_LMK_ADJ_TUNNING_DECREASE;
        } else {
            return ProcessInfo.PROCESS_LMK_ADJ_TUNNING_NONEED;
        }
    }

    public void applyOomAdjByProcessInfo(ProcessInfo app, long now, long nowElapsed) {
        mAm.applyOomAdjByProcessInfo(app, now, nowElapsed);
    }

    // 0 add 1 remove
    public void updateWakeLockStatus(int opt, ArrayList<Integer> uids) {
        try {
            synchronized (mBusyUids) {
                if (uids == null) {
                    return;
                }
                long now = SystemClock.uptimeMillis();
                if (opt == 0) {
                    for (int uid : uids) {
                        WakeLockData lock = mBusyUids.get(uid);
                        if (lock == null) {
                            lock = new WakeLockData();
                            lock.uid = uid;
                            mBusyUids.put(uid, lock);
                        }
                        lock.held = true;
                    }
                } else {
                    for (int uid : uids) {
                        WakeLockData lock = mBusyUids.get(uid);
                        if (lock == null) {
                            lock = new WakeLockData();
                            lock.uid = uid;
                            mBusyUids.put(uid, lock);
                        }
                        lock.held = false;
                        lock.lastHeldTime = now;
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    // 0 add 1 remove
    public void updateWakeLockStatusChanging(ArrayList<Integer> from, ArrayList<Integer> to) {
        try {
            updateWakeLockStatus(1, from);
            updateWakeLockStatus(0, to);
        } catch (Exception e) {
        }
    }

    public void notifyScreenOn() {
    }

    // modify for performance Bug #685274 begin
    public boolean keepImpAppAlive(String pkgName, int lastMemoryLevel) {
        return (lastMemoryLevel <= ProcessStats.ADJ_MEM_FACTOR_MODERATE) && keepImpAppAliveForce(pkgName);
    }

    public boolean keepImpAppAliveForce(String pkgName) {
        if (mImpAppList == null || mImpAppList.size() == 0) {
            return false;
        }
        boolean keep = mImpAppList.contains(pkgName) && !mAm.isUserAMonkey();
        if (DEBUG_SERVICE)
            Slog.d(TAG, "keepImpAppAliveForce " + pkgName + ":" + keep);
        return keep;
    }

    // modify for performance Bug #685274 end

    public boolean enhancedPowerHintForLaunchEnabled() {
        return mLocalPowerManager != null && mEnhancedHintForLaunch;
    }

    public void sendPowerHintForLaunch(int enable) {
        if (enhancedPowerHintForLaunchEnabled() && mPowerHintPolicyExecutor != null) {
            mPowerHintPolicyExecutor.sendPowerHintForLaunch(enable);
        } else {
            Slog.w(TAG, "sendPowerHintForLaunch called under disabled!");
        }
    }

    public long getStartingWindowRemoveDelay() {
        if (mTaskSnapShotManager != null) {
            return mTaskSnapShotManager.getStartingWindowRemoveDelay(mCurrentFocusIntent);
        }
        return 0;
    }

    private void handleScheduleBoostRRForApp(Message msg) {
        if (mProcessSchedExecutor == null) {
            return;
        }
        BoostAppInfo info = (BoostAppInfo) msg.obj;
        Slog.d(TAG, "handleScheduleBoostRRForApp: " + info.id + " ,ispid:" + info.isPid + ",info.boost:" + info.boost);
        if (info.boost) {
            Trace.asyncTraceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "schedule Boost for :" + info.id, 0);
        } else {
            Trace.asyncTraceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER, "schedule Boost for :" + info.id, 0);
        }
        mProcessSchedExecutor.scheduleBoostApp(info.boost, info.id, info.isPid);
    }

    public void scheduleBoostRRForApp(boolean boost) {
        if (isMonkeyOrInStrumentation()) {
            return;
        }
        BoostAppInfo info = new BoostAppInfo();
        info.boost = boost;
        info.id = Binder.getCallingPid();
        info.isPid = true;
        Message msg = Message.obtain();
        msg.what = MSG_BOOST_PR;
        msg.obj = info;
        mHandler.sendMessage(msg);
    }

    private boolean isBoostApp(String pkgName, int type) {
        boolean keep = false;
        switch (type) {
        case TYPE_NONE: {
            keep = true;
            break;
        }
        case TYPE_CONFIG_CHANGE: {
            if (mConfigChangeBoost == null || mConfigChangeBoost.size() == 0) {
                break;
            }
            keep = mConfigChangeBoost.contains(pkgName);
            if (keep) {
                Slog.d(TAG, "isBoostApp,keep = true :" + pkgName);
            }
            break;
        }
        default:
            break;
        }
        return keep;
    }

    public boolean isMonkeyOrInStrumentation() {
        return mInStrumentationChange || mAm.isUserAMonkeyNoCheck();
    }

    public void scheduleBoostRRForAppName(boolean boost, String pkgName, int type) {
        if (!isBoostApp(pkgName, type)) {
            return;
        }
        if (isMonkeyOrInStrumentation()) {
            return;
        }
        Slog.d(TAG, "scheduleBoostRRForAppName :" + pkgName);
        BoostAppInfo info = new BoostAppInfo();
        info.boost = boost;
        info.id = Binder.getCallingPid();
        info.isPid = false;
        Message msg = new Message();
        msg.what = MSG_BOOST_PR;
        msg.obj = info;
        mHandler.sendMessage(msg);
    }

    public void scheduleBoostWhenTouch() {
        if (DEBUG_SERVICE)
            Slog.d(TAG, "scheduleBoostWhenTouch");
        if (isMonkeyOrInStrumentation() || mProcessSchedExecutor == null || mHandler.hasMessages(MSG_BOOST_PR_WHEN_TOUCH)) {
            return;
        }
        if(mHandler.hasMessages(MSG_BOOST_AS_REGUAL)){
            mHandler.removeMessages(MSG_BOOST_AS_REGUAL);
        }else{
            mHandler.sendEmptyMessage(MSG_BOOST_PR_WHEN_TOUCH);
        }
        mHandler.sendEmptyMessageDelayed(MSG_BOOST_AS_REGUAL, TOUCH_BOOST_REMOVE_DUR);
    }

    private void handleInStrumentationChange(Message msg) {
        mInStrumentationChange = true;
        synchronized (mPolicyExecutors) {
            for (PolicyExecutor executor : mPolicyExecutors) {
                executor.onInStrumentationChange((InStrumentationData) msg.obj);
            }
        }
    }

    protected boolean isInStrumentationChangeEnabled() {
        return mInStrumentationChange;
    }

    public void notifyInstrumentationStatus(boolean start, ProcessInfo app, InstrumentationInfo ii) {
        Message msg = new Message();
        msg.what = MSG_INSTRUMENTATION_CHANGE;
        Bundle bundle = new Bundle();
        if (ii != null) {
            bundle.putString(ProcessInfo.KEY_PKGNAME, ii.targetPackage);
        }
        InStrumentationData data = new InStrumentationData();
        data.app = app;
        data.iInfo = ii;
        data.start = start;
        msg.obj = data;
        mHandler.sendMessage(msg);
    }

    public int getRamConfig() {
        if (mRamConfig == -1) {
            SystemStatus status = getCurrentSystemStatus(true);
            mRamConfig = status.getRamStatus().getRamConfig();
        }
        return mRamConfig;
    }

    public boolean isPackageUsrFavorite(String pkgName) {
        if (mOomAdjPolicyExecutor != null) {
            return mOomAdjPolicyExecutor.isPackageUsrFavorite(pkgName);
        }
        return false;
    }

    // for dumpsys:
    private void dumpHelp(PrintWriter pw) {
        pw.println("performance dump options: debugon/debugoff ");
        pw.println("debugon/debugoff: enable or disable debug to get more logs");
        pw.println("available modules :");
        pw.println("    usrhabit");
        pw.println("    service");
        pw.println("    procstat");
        pw.println("    prioadj");
        pw.println("    rampolicy");
        pw.println("    cpustat");
        pw.println("    all");
        pw.println("Example: enable all debug");
        pw.println("dumpsys performance_fw debugon all");
        pw.println("Example: enable usrhabit debug:");
        pw.println("dumpsys performance_fw debugon usrhabit");
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, TAG, pw))
            return;
        int opti = 0;
        while (opti < args.length) {
            String opt = args[opti];
            if (opt == null || opt.length() <= 0) {
                break;
            }
            opti++;
            if ("debugon".equals(opt)) {
                PerformanceManagerDebugConfig.handleDebugSwitch(args, true);
                return;
            } else if ("debugoff".equals(opt)) {
                PerformanceManagerDebugConfig.handleDebugSwitch(args, false);
                return;
            } else if ("-h".equals(opt)) {
                dumpHelp(pw);
                return;
            } else if ("fetch".equals(opt)) {
                if (mExtraFetch != null) {
                    mExtraFetch.dump(fd, pw, args);
                }
                return;
            } else {
                pw.println("Unknown argument: " + opt + "; use -h for help");
            }
        }
        pw.println("Dump performance:");
        if (mUserHabitCollector != null) {
            mUserHabitCollector.dump(fd, pw, args);
        }
        if (mProcessStateCollector != null) {
            mProcessStateCollector.dump(fd, pw, args);
        }
        if (mCpuStatusCollector != null) {
            mCpuStatusCollector.dump(fd, pw, args);
        }
        synchronized (mPolicyExecutors) {
            for (PolicyExecutor executor : mPolicyExecutors) {
                executor.dump(pw, args);
            }
        }
    }
}
