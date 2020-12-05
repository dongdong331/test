/*
 * Copyright © 2016 Spreadtrum Communications, Inc.
 */

package com.android.server.am;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.IApplicationThread;
import android.app.LowmemoryUtils;
import android.app.ProcessProtection;
import android.app.ProcessInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.SystemProperties;
import android.os.SystemClock;
import android.os.IBinder;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.TimingsTraceLog;
import android.graphics.Bitmap;
import android.view.Display;

import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_BROADCAST_BACKGROUND;
import com.android.server.performance.PerformanceManagerService;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ActivityManagerServiceEx extends ActivityManagerService {
    private static final String TAG = "ActivityManagerEx";
    private static final boolean IS_USER_BUILD = android.os.Build.IS_USER;
    static final boolean DEBUG_AMSEX = false && !IS_USER_BUILD;
    static final boolean DEBUG_PROCESS_PROTECT = true && !IS_USER_BUILD;

    // Add for ProtectArea feature
    boolean mIsInHome;

    LmKillerTracker mLmkTracker;

    /**SPRD: Process name list which prevented to be killed in CACHE_EMPTY state*/
    private List<String> mCacheProtectList;

    // Kill stop front app function related fields, when home key pressed and incall-screen come,
    // flag to control whether kill-front-app or not.
    public static final boolean KILL_FRONT_APP = SystemProperties.getBoolean("sys.kill.frontapp",
            false) || ActivityManager.isLowRamDeviceStatic();
    static final int KILL_STOP_TIMEOUT_DELAY = 7 * 1000;// 7s
    static final int KILL_STOP_TIMEOUT_DELAY_SHORT = 4 * 1000;// 4s, use it when home process died
    static final int KILL_STOP_TIMEOUT = 80;
    final ExHandler mExHandler;
    private int mStopingPid = -1;
    private boolean mIsKillStop = false;
    final String[] mKillTopSystemAppBlacklist;

    // Third party Persistent Service Processes and components list
    final String[] m3rdPartyPersistentSvcProcs;
    final String[] m3rdPartyPersistentSvcCompnts;

    // Add for special broadcast queue
    private static final boolean sSupportCompatibityBroadcast = true;
    private final String[] mSpecialBroadcasts;
    private final String[] mForceFgBroadcasts;
    private final String[] mSpecialBroadcastQueueBlacklist;

    private PerformanceManagerService mPerformanceManagerService;
    // Add for cmcc hidden apps
    private List<String> mHidePackages;

    public ActivityManagerServiceEx(Context systemContext) {
        super(systemContext);

        mIsInHome = true;
        mExHandler = new ExHandler((Looper) mHandler.getLooper());
        mKillTopSystemAppBlacklist = super.mContext.getResources().getStringArray(
                com.android.internal.R.array.kill_top_system_app_blacklist);
        m3rdPartyPersistentSvcProcs = super.mContext.getResources().getStringArray(
                com.android.internal.R.array.third_party_persistent_service_processes);
        m3rdPartyPersistentSvcCompnts = super.mContext.getResources().getStringArray(
                com.android.internal.R.array.third_party_persistent_service_components);

        if (sSupportCompatibityBroadcast) {
            BroadcastQueue[] broadcastQueues = new BroadcastQueue[4];
            broadcastQueues[0] = mBroadcastQueues[0];
            broadcastQueues[1] = mBroadcastQueues[1];
            broadcastQueues[2] = mCtsFgBroadcastQueue = new BroadcastQueue(this, this.mHandler,
                    "foreground-comp", ActivityManagerService.BROADCAST_FG_TIMEOUT, false);
            broadcastQueues[3] = mCtsBgBroadcastQueue = new BroadcastQueue(this, this.mHandler,
                    "background-comp", ActivityManagerService.BROADCAST_BG_TIMEOUT, false);
            mBroadcastQueues = broadcastQueues;
            mForceFgBroadcasts = mContext.getResources().getStringArray(
                    com.android.internal.R.array.force_foreground_broadcasts);
            mSpecialBroadcastQueueBlacklist = mContext.getResources().getStringArray(
                    com.android.internal.R.array.special_broadcast_queue_blacklist);
            mSpecialBroadcasts = mContext.getResources().getStringArray(
                    com.android.internal.R.array.special_broadcasts);
        }

        String[] hidePackagesArray = mContext.getResources().getStringArray(
                com.android.internal.R.array.hide_packages);
        mHidePackages = Arrays.asList(hidePackagesArray);
    }

    @Override
    public void systemReady(final Runnable goingCallback, TimingsTraceLog traceLog) {
        if (mLmkTracker == null) {
            mLmkTracker = new LmKillerTracker();
            mLmkTracker.start();
        }
        //SPRD: Initiate the mCacheProtectList! @{
        String[] protectProcesses = super.mContext.getResources()
                                    .getStringArray(com.android.internal.R.array.cache_protect_processes);
        if (protectProcesses != null && protectProcesses.length > 0) {
            mCacheProtectList = Arrays.asList(protectProcesses);
        }
        //SPRD: @}
        super.systemReady(goingCallback, traceLog);
    }

    @Override
    public void onShellCommand(FileDescriptor in, FileDescriptor out,
            FileDescriptor err, String[] args, ShellCallback callback,
            ResultReceiver resultReceiver) {
        (new ActivityManagerShellCommandEx(this, false)).exec(
                this, in, out, err, args, callback, resultReceiver);
    }

    static HashMap<String, ProtectArea> sPreProtectAreaList;
    static HashSet<String> sHasAlarmList;
    static HashSet<String> sNeedCleanPackages;
    static SparseIntArray sProcProtectConfig;

    static {
        sPreProtectAreaList = new HashMap<String, ProtectArea>();

        sHasAlarmList = new HashSet<String>();
        sNeedCleanPackages = new HashSet<String>();

        sProcProtectConfig = new SparseIntArray();
        sProcProtectConfig.put(ProcessProtection.PROCESS_STATUS_RUNNING,
                ProcessList.FOREGROUND_APP_ADJ);
        sProcProtectConfig.put(ProcessProtection.PROCESS_STATUS_MAINTAIN,
                ProcessList.PERCEPTIBLE_APP_ADJ);
        sProcProtectConfig.put(ProcessProtection.PROCESS_STATUS_PERSISTENT,
                ProcessList.PERSISTENT_PROC_ADJ);
        sProcProtectConfig.put(ProcessProtection.PROCESS_PROTECT_CRITICAL,
                ProcessList.FOREGROUND_APP_ADJ);
        sProcProtectConfig.put(ProcessProtection.PROCESS_PROTECT_IMPORTANCE,
                ProcessList.PERCEPTIBLE_APP_ADJ);
        sProcProtectConfig.put(ProcessProtection.PROCESS_PROTECT_NORMAL,
                ProcessList.HEAVY_WEIGHT_APP_ADJ);
        sPreProtectAreaList.put("android.process.media", new ProtectArea(ProcessList.HOME_APP_ADJ, ProcessList.UNKNOWN_ADJ,
                ProcessProtection.PROCESS_PROTECT_NORMAL));
    }

    @Override
    final boolean startProcessLocked(String hostingType, String hostingNameStr, String entryPoint,
                ProcessRecord app, int uid, int[] gids, int runtimeFlags, int mountExternal,
                String seInfo, String requiredAbi, String instructionSet, String invokeWith,
                long startTime) {
        boolean startSuccess = super.startProcessLocked(hostingType, hostingNameStr,
                entryPoint, app, uid, gids, runtimeFlags, mountExternal, seInfo,
                requiredAbi, instructionSet, invokeWith, startTime);
        if (app.processName != null) {
            ProtectArea pa = sPreProtectAreaList.get(app.processName);
            if (pa != null) {
                app.protectStatus = pa.mLevel;
                app.protectMinAdj = pa.mMinAdj;
                app.protectMaxAdj = pa.mMaxAdj;
            }
            if (DEBUG_AMSEX){
                Slog.d(TAG, "startProcessLocked app.protectLevel :" + app.protectStatus
                        + " app.protectMinAdj: " + app.protectMinAdj
                        + " app.protectMaxAdj: " + app.protectMaxAdj);
            }
        }
        return startSuccess;
    }

    boolean isTopHostApp(ProcessRecord app,  ProcessRecord top){
        if (top != null && top != mHomeProcess && app != mHomeProcess) {
            for (int i = 0; i < top.activities.size(); i++) {
                final ActivityRecord r = top.activities.get(i);
                if (r != null  && r.callerPid == app.pid) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    final boolean computeOomAdjLocked(ProcessRecord app, int cachedAdj, ProcessRecord TOP_APP,
                                            boolean doingAll, long now) {
        mIsInHome = TOP_APP == mHomeProcess;
        boolean computeResult = super.computeOomAdjLocked(app, cachedAdj, TOP_APP, doingAll, now);
        int curadj = app.curAdj;
        if (DEBUG_AMSEX) Slog.d(TAG, "computeOomAdjLocked enter, app:" + app + " curadj:" + curadj);
        /* check State protection */
        switch (app.protectStatus) {
            case ProcessProtection.PROCESS_STATUS_RUNNING:
            case ProcessProtection.PROCESS_STATUS_MAINTAIN:
            case ProcessProtection.PROCESS_STATUS_PERSISTENT: {
                int value = sProcProtectConfig.get(app.protectStatus);
                if(curadj > value) curadj = value;
                break;
            }
            case ProcessProtection.PROCESS_PROTECT_CRITICAL:
            case ProcessProtection.PROCESS_PROTECT_IMPORTANCE:
            case ProcessProtection.PROCESS_PROTECT_NORMAL: {
                if (curadj >= app.protectMinAdj && curadj <= app.protectMaxAdj) {
                    int value = sProcProtectConfig.get(app.protectStatus);
                    if (curadj > value) curadj = value;
                }
                break;
            }
        }
        if (curadj > ProcessList.SERVICE_ADJ){
            if (app !=  TOP_APP && isTopHostApp(app, TOP_APP)){
                curadj = ProcessList.SERVICE_ADJ;
                app.cached = false;
                app.adjType = "host";
            }
        }
        if (DEBUG_AMSEX){
            Slog.d(TAG, "computeOomAdjLocked app.protectStatus:" + app.protectStatus
                    + " app.protectMinAdj:" + app.protectMinAdj + " protectMaxAdj:"
                    + app.protectMaxAdj + " curadj:" + curadj);
        }
        if (curadj != app.curRawAdj) {
            // adj changed, adjust its other parameters:
            app.empty = false;
            app.cached = false;
            app.adjType = "protected";
            app.curProcState = ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND;
            app.curSchedGroup = Process.THREAD_GROUP_DEFAULT;
            app.curAdj = curadj;
            if (DEBUG_AMSEX) {
                Slog.d(TAG, "computeOomAdjLocked :" + app + " app.curAdj:" + app.curAdj);
            }
        }
        return computeResult;
    }

    private boolean checkProcessProtectPermisson(){
        int perm = PackageManager.PERMISSION_DENIED;
        perm = checkPermission(android.Manifest.permission.PROTECT_PROCESS,
                Binder.getCallingPid(), Binder.getCallingUid());
        if (perm != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    public void setProcessProtectStatusByPid(int pid, int status) {
        if (!checkProcessProtectPermisson()) {
            if (DEBUG_PROCESS_PROTECT) Slog.w(TAG, "Permission Denial: pid "
                    + Binder.getCallingPid() + " who want to setProcessProtectStatusByPid requires "
                    + android.Manifest.permission.PROTECT_PROCESS);
            return;
        }
        ProcessRecord app = null;
        synchronized (mPidsSelfLocked) {
            app = mPidsSelfLocked.get(pid);
        }
        if (app != null) {
            if (DEBUG_PROCESS_PROTECT) Slog.d(TAG, "setProcessProtectStatusByPid, app: "
                    + app + " status: " + status + " preStatus: " + app.protectStatus);
            synchronized (this) {
                app.protectStatus = status;
            }
        }
    }

    public void setProcessProtectStatusByProcName(String appName, int status) {
        if (!checkProcessProtectPermisson()) {
            if (DEBUG_PROCESS_PROTECT) Slog.w(TAG, "Permission Denial: pid "
                    + Binder.getCallingPid()
                    + " who want to setProcessProtectStatusByAppName requires "
                    + android.Manifest.permission.PROTECT_PROCESS);
            return;
        }
        if (appName == null)  return;
        if (DEBUG_PROCESS_PROTECT) {
            Slog.d(TAG, "setProcessProtectStatusByAppName :"
                    + appName + " status:" + status);
        }
        for (int i = mLruProcesses.size() - 1; i >= 0; i--) {
            ProcessRecord rec = mLruProcesses.get(i);
            if (rec != null && appName.equals(rec.processName)) {
                rec.protectStatus = status;
                if (DEBUG_PROCESS_PROTECT) {
                    Slog.d(TAG, "setProcessProtectStatusByAppName find app:" + rec);
                }
                break;
            }
        }

    }

    public void setProcessProtectArea(String appName, int minAdj, int maxAdj, int protectLevel) {
        if(!checkProcessProtectPermisson()) {
            if (DEBUG_PROCESS_PROTECT) {
                Slog.w(TAG, "Permission Denial: pid " + Binder.getCallingPid()
                        + " who want to setProcessProtectArea requires "
                        + android.Manifest.permission.PROTECT_PROCESS);
            }
            return;
        }
        if (DEBUG_PROCESS_PROTECT){
            Slog.d(TAG, "setProcessProtectStatus :" + appName + " minAdj:" + minAdj + " maxAdj:"
                    + maxAdj + " protectLevel:" + protectLevel);
        }
        if (appName == null) return;
        synchronized (mPidsSelfLocked) {
            for (int i = mLruProcesses.size() - 1; i >= 0; i--) {
                ProcessRecord rec = mPidsSelfLocked.valueAt(i);
                if (rec != null && appName.equals(rec.processName)) {
                    rec.protectStatus = protectLevel;
                    rec.protectMinAdj = minAdj;
                    rec.protectMaxAdj = maxAdj;
                    if (DEBUG_PROCESS_PROTECT) Slog.d(TAG, "setProcessProtectArea find app:" + rec);
                    break;
                }
            }
        }
    }

    @Override
    void updateLruProcessLocked(ProcessRecord app, boolean activityChange, ProcessRecord client) {
        super.updateLruProcessLocked(app, activityChange, client);
        if (mCacheProtectList == null || mCacheProtectList.size() <= 0
                || app.activities.size() > 0 || app.hasClientActivities
                || app.treatLikeActivity || app.services.size() > 0
                || app.persistent) {
            //Don't change lru list if this app is not empty.
            return;
        } else  {
            //Bump up the cache-protecting processes to the top of all CACHE_EMPTY processes
            int index = mLruProcesses.lastIndexOf(app);
            for (int i = index - 1; i >= 0; i--) {
                ProcessRecord cacheProc = mLruProcesses.get(i);
                if (mCacheProtectList.contains(cacheProc.processName)) {
                    mLruProcesses.remove(i);
                    mLruProcesses.add(index, cacheProc);
                    index--;
                }
            }
        }
    }

    public static class ProtectArea
    {
        int mMinAdj;
        int mMaxAdj;
        int mLevel;

        public ProtectArea(int minAdj, int maxAdj, int protectLevel) {
            mMinAdj = minAdj;
            mMaxAdj = maxAdj;
            mLevel = protectLevel;
        }

        @Override
        public String toString() {
            return "ProtectArea [mMinAdj=" + mMinAdj + ", mMaxAdj="
                    + mMaxAdj + ", mLevel=" + mLevel + "]";
        }
    }

    public void addProtectArea(final String processName, final ProtectArea area) {
        if (processName == null || area == null) {
            return;
        }
        if (DEBUG_AMSEX) Slog.d(TAG, "addProtectArea, processName: " + processName
                + " ProtectArea: " + area);
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                synchronized (ActivityManagerServiceEx.this) {
                    sPreProtectAreaList.put(processName, area);
                    updateOomAdjLocked();
                }
            }
        });
    }

    public void removeProtectArea(final String processName) {
        if (processName == null) {
            return;
        }
        sPreProtectAreaList.remove(processName);
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                synchronized (ActivityManagerServiceEx.this) {
                    ProcessRecord app = null;
                    for (int i = mLruProcesses.size() -1; i >= 0; i--) {
                        if (processName.equals(mLruProcesses.get(i).processName)) {
                            app = mLruProcesses.get(i);
                            break;
                        }
                    }
                    if (DEBUG_AMSEX) Slog.d(TAG, "removeProtectArea, processName: "
                            + processName + " app: " + app);
                    if (app != null) {
                        updateOomAdjLocked(app, false);
                    }
                }
            }
        });
    }

    @Override
    void preBringUpPhoneLocked() {
        try {
            Slog.i(TAG, "pre start phone process");
            ApplicationInfo phoneInfo = mContext.getPackageManager()
                    .getApplicationInfo("com.android.phone", 0);
            addAppLocked(phoneInfo, null, false, null /* ABI override */);
        }catch (Exception e) {
            Slog.e(TAG, "pre bring up phone process failed !", e);
        }
    }

    @Override
    public void startHomePre() {
        if (!KILL_FRONT_APP) {
            return;
        }
        synchronized(this) {
            final ActivityRecord topActivity = resumedAppLocked();
            if (topActivity == null || topActivity.app == null
                    || (topActivity.app.info.flags & (ApplicationInfo.FLAG_SYSTEM)) != 0
                    || topActivity.isActivityTypeHome()) {
                return;
            }

            if (mHomeProcess == null) {
                Slog.w(TAG, "kill front app pid=" + topActivity.app.pid);
                final long origId = Binder.clearCallingIdentity();
                Process.killProcessQuiet(topActivity.app.pid);
                handleAppDiedLocked(topActivity.app, false, false);
                Binder.restoreCallingIdentity(origId);
            }
        }
    }

    final class ExHandler extends Handler {
        public ExHandler (Looper looper) {
            super(looper, null, true);
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case KILL_STOP_TIMEOUT: {
                    Slog.w(TAG, "The kill front app delay time is expired,"
                            + " it's time to kill the front app Pid:" + mStopingPid);
                    if (mStopingPid > 0) {
                        Process.sendSignal(mStopingPid, Process.SIGNAL_KILL);
                        mStopingPid = -1;
                        mIsKillStop = false;
                    }
                    break;
                }
            }
        }
    }

    private boolean isInKillTopSystemAppBlacklist(ApplicationInfo info) {
        for (int i=0; i < mKillTopSystemAppBlacklist.length; i++) {
            if (mKillTopSystemAppBlacklist[i] != null
                    && mKillTopSystemAppBlacklist[i].equals(info.packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add the implement of API {@link ActivityManagerProxyEx.killStopFrontApp}
     * that for kill-stop front app when the phone call is coming in.
     * @params func 0 continue the stopped app;
     *              1 stop the front app ;
     *              2 Incall screen is displayed;
     *                remove the KILL_STOP_TIMEOUT msg
     */
    @Override
    public void killStopFrontApp(int func) {
        if (!KILL_FRONT_APP) {
            Slog.w(TAG, "kill stop front app feature is only enabled in low ram device.");
            return;
        }

        if (mIsKillStop && func == 1) {
            Slog.w(TAG, "killStopFrontApp is already called by someone "
                    + "else, wait for the kill action to be done.");
            return;
        }
        synchronized (this) {
            final ActivityRecord topActivity = resumedAppLocked();
            int callingPid = Binder.getCallingPid();
            if (func == LowmemoryUtils.KILL_STOP_FRONT_APP
                    && topActivity != null
                    && topActivity.app != null
                    && topActivity.app.info != null
                    && (((topActivity.app.info.flags & (ApplicationInfo.FLAG_SYSTEM)) == 0)
                        || isInKillTopSystemAppBlacklist(topActivity.app.info))
                    && !topActivity.isActivityTypeHome()) {
                mIsKillStop = true;
                int pid = topActivity.app.pid;
                Slog.w(TAG, "Caller[" + callingPid + "] is request to kill front app,"
                        + " topActivity: " + topActivity + ", pkg: " + topActivity.packageName
                        + ", Pid: " + pid);

                if (pid > 0) {
                    if (KILL_FRONT_APP && mHomeProcess == null) {
                        mStopingPid = pid;
                        if (!mExHandler.hasMessages(KILL_STOP_TIMEOUT)) {
                            Slog.w(TAG, "Home process is not active, kill the front app after "
                                    + KILL_STOP_TIMEOUT_DELAY_SHORT
                                    + "ms, front app pid: " + mStopingPid);
                            Message msg = mExHandler.obtainMessage(KILL_STOP_TIMEOUT);
                            mExHandler.sendMessageDelayed(msg, KILL_STOP_TIMEOUT_DELAY_SHORT);
                        }
                        return;
                    }
                    mStopingPid = pid;
                    if (!mExHandler.hasMessages(KILL_STOP_TIMEOUT)) {
                        Slog.w(TAG, "kill the front app after " + KILL_STOP_TIMEOUT_DELAY
                                + "ms, front app pid: " + mStopingPid);
                        Message msg = mExHandler.obtainMessage(KILL_STOP_TIMEOUT);
                        mExHandler.sendMessageDelayed(msg,
                                KILL_STOP_TIMEOUT_DELAY);
                    }
                }
            } else if (func == LowmemoryUtils.KILL_CONT_STOPPED_APP) {
                mIsKillStop = false;
                if (mStopingPid > 0) {
                    Slog.w(TAG, "Caller[" + callingPid + "] is request to "
                            + "cancel to kill the front app, pid: " + mStopingPid);
                    mExHandler.removeMessages(KILL_STOP_TIMEOUT);
                    mStopingPid = -1;
                }
            } else if (func == LowmemoryUtils.CANCEL_KILL_STOP_TIMEOUT) {
                // InCallUI is already displayed,remove timeout message
                Slog.w(TAG, "Caller[" + callingPid + "] is request to "
                        + "cancel to kil front app, pid: " + mStopingPid);
                mExHandler.removeMessages(KILL_STOP_TIMEOUT);
            } else {
                Slog.w(TAG, "The top activity is home Activity or is from light weight"
                        + " system app, will not kill it.");
            }
        }
    }

    @Override
    public List<RunningAppProcessInfo> getRunningAppProcesses() {
        List<RunningAppProcessInfo> runningAppList = super.getRunningAppProcesses();
        if (runningAppList == null || runningAppList.size() <= 0) return runningAppList;
        if (mHidePackages == null || mHidePackages.size() <= 0) return runningAppList;
        List<ActivityManager.RunningAppProcessInfo> loopList = new ArrayList<ActivityManager.RunningAppProcessInfo>(runningAppList);
        for (ActivityManager.RunningAppProcessInfo appInfo : loopList) {
            if (appInfo.pkgList == null || appInfo.pkgList.length <= 0) continue;
            for (String pkg : appInfo.pkgList) {
                if (mHidePackages.contains(pkg)) {
                    Slog.d(TAG, "mHidePackages contains " + pkg + ", remove it");
                    runningAppList.remove(appInfo);
                }
            }
        }
        remove3rdPartyPersistSvcProcs(runningAppList);
        return runningAppList;
    }

    private void remove3rdPartyPersistSvcProcs(List<RunningAppProcessInfo> runningAppList){
        List<RunningAppProcessInfo> runlistCopy =
                new ArrayList<RunningAppProcessInfo>(runningAppList);
        int callingUid = Binder.getCallingUid();

        for (RunningAppProcessInfo rpi: runlistCopy) {
            if (rpi != null && rpi.processName != null
                    && callingUid > Process.FIRST_APPLICATION_UID) {
                for (int i = 0; i < m3rdPartyPersistentSvcProcs.length; i++) {
                    if (rpi.processName.equals(m3rdPartyPersistentSvcProcs[i])) {
                        Slog.i(TAG, "getRunningAppProcesses: Removing rpi:" + rpi.processName
                                + " from result" + " for caller[uid]:" + callingUid);
                        runningAppList.remove(rpi);
                    }
                }
            }
        }
    }

    @Override
    public final void attachApplication(IApplicationThread thread, long startSeq) {
        synchronized (this) {
            super.attachApplication(thread, startSeq);

            int callingPid = Binder.getCallingPid();
            ProcessRecord app;
            if (callingPid != MY_PID && callingPid >= 0) {
                synchronized (mPidsSelfLocked) {
                    app = mPidsSelfLocked.get(callingPid);
                }
            } else {
                app = null;
            }

            if (app != null) {
                for (int i = 0; i < m3rdPartyPersistentSvcProcs.length; i++){
                    if (app.processName != null
                            && app.processName.equals(m3rdPartyPersistentSvcProcs[i])) {
                        app.persistent = true;
                        app.info.flags |= ApplicationInfo.FLAG_PERSISTENT;
                        if (!uidOnBackgroundWhitelist(app.uid)) {
                            backgroundWhitelistUid(app.uid);
                        }
                        Slog.i(TAG, "attachApplicationLocked: set persistent:" + app.persistent
                                + " for app:" + app);
                    }
                }
            }
        }
    }

    @Override
    BroadcastQueue broadcastQueueForIntent(String callerPackage, Intent intent) {
        if (!sSupportCompatibityBroadcast) {
            return broadcastQueueForIntent(intent);
        }

        final boolean isSpecialBroadcast = isInSpecialBroadcastGroup(mSpecialBroadcasts,
                callerPackage, intent) && !isInSpecialBroadcastGroup(
                mSpecialBroadcastQueueBlacklist, callerPackage, intent);

        final boolean isFg = isInSpecialBroadcastGroup(mForceFgBroadcasts, callerPackage, intent)
                || ((intent.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND) != 0);

        if (DEBUG_BROADCAST_BACKGROUND && isSpecialBroadcast) {
            Slog.i(TAG, "Check broadcast intent " + intent + " on "
                    + (isFg ? (isSpecialBroadcast ? "foreground-comp" : "foreground")
                    : (isSpecialBroadcast ? "background-comp" : "background")) + " queue");
        }

        if (isFg && !isSpecialBroadcast) {// common foreground queue
            return mBroadcastQueues[0];
        } else if (!isFg && !isSpecialBroadcast) {// common background queue
            return mBroadcastQueues[1];
        } else if (isFg && isSpecialBroadcast) {// special foreground queue
            return mBroadcastQueues[2];
        } else {// special background queue
            return mBroadcastQueues[3];
        }
    }

    // We use the regular expression matcher to check whether the caller Package or
    // Intent action is in the specialGroup.
    private boolean isInSpecialBroadcastGroup(String[] specialGroup,
                                              String callerPackage, Intent intent) {
        // check whether is special sender
        boolean isSpecialCaller = false;
        // check whether is special broadcast action
        boolean isSpecialBroadcastAction = false;

        String action = null;
        if (intent != null && intent.getAction() != null) {
            action = intent.getAction();
        }
        if (action == null && callerPackage == null) {
            return false;
        }

        if (specialGroup != null && specialGroup.length > 0) {
            for (int i = 0; i < specialGroup.length; i++) {
                final Pattern pattern;
                try {
                    pattern = Pattern.compile(specialGroup[i]);
                } catch (PatternSyntaxException e) {
                    Slog.w(TAG, "isInSpecialBroadcastGroup check for sender:"+ callerPackage
                            + " of " + intent + " encounter invalid pattern string:"
                            + specialGroup[i], e);
                    continue;
                }

                if (callerPackage != null && pattern != null
                        && pattern.matcher(callerPackage).find()) {
                    isSpecialCaller = true;
                    break;
                }
                if (action != null && pattern != null
                        && pattern.matcher(action).find()) {
                    isSpecialBroadcastAction = true;
                    break;
                }
            }
        }

        return isSpecialCaller || isSpecialBroadcastAction;
    }

    @Override
    boolean isPendingBroadcastProcessLocked(int pid) {
        if(!sSupportCompatibityBroadcast){
            return super.isPendingBroadcastProcessLocked(pid);
        }
        return super.isPendingBroadcastProcessLocked(pid)
                || mCtsFgBroadcastQueue.isPendingBroadcastProcessLocked(pid)
                || mCtsBgBroadcastQueue.isPendingBroadcastProcessLocked(pid);
    }

    @Override
    BroadcastQueue[] getFgOrBgQueues(int flags) {
        if(!sSupportCompatibityBroadcast){
            return super.getFgOrBgQueues(flags);
        }
        boolean foreground = (flags & Intent.FLAG_RECEIVER_FOREGROUND) != 0;
        return foreground ? new BroadcastQueue[] { mFgBroadcastQueue, mCtsFgBroadcastQueue }
                : new BroadcastQueue[] { mBgBroadcastQueue, mCtsBgBroadcastQueue };
    }

    //modify for performance  begin
    @Override
    /* package */ void notifyActivityStateChange(Intent intent, int state, Bundle bundle) {
        try {
            if (mPerformanceManagerService != null) {
                if (bundle  == null) {
                    bundle = new Bundle();
                }
                bundle.putInt(ProcessInfo.KEY_ACTIVITY_STATE, state);
                mPerformanceManagerService.notifyActivityStateChange(intent, bundle);
            }
        } catch (Exception e) {}
    }

    @Override
    /* package */ int processLmkAdjTunningIfneeded(ProcessInfo app, int memLvl, long now, long nowElapsed) {
        int ret = ProcessInfo.PROCESS_LMK_ADJ_TUNNING_NONEED;
        try {
            if (mPerformanceManagerService != null) {
                ret = mPerformanceManagerService.processLmkAdjTunningIfneeded(app, memLvl, now, nowElapsed);
            }
        } catch (Exception e) {}
        return ret;
    }

    public void setPerformanceManager(PerformanceManagerService performanceManager) {
        mPerformanceManagerService = performanceManager;
    }

    public void applyOomAdjByProcessInfo(ProcessInfo info, long now, long nowElapsed) {
        synchronized(this) {
            applyOomAdjByProcessInfoLocked(info, now, nowElapsed);
        }
    }

    public void applyOomAdjByProcessInfoLocked(ProcessInfo info, long now, long nowElapsed) {
        ProcessRecord app = null;
        for (int i = mLruProcesses.size() -1; i >= 0; i--) {
            if (info.processName.equals(mLruProcesses.get(i).processName) &&
                info.pid == mLruProcesses.get(i).pid) {
                app = mLruProcesses.get(i);
                break;
            }
        }
        if (app != null) {
            app.curAdj = info.tunnedAdj;
            app.adjType = info.tunnedAdjType;
            app.curProcState = info.tunnedProcState;
            app.curSchedGroup = info.tunnedSchedGroup;
            applyOomAdjLocked(app, true, now, nowElapsed);
        }
    }

    public ArrayList<ProcessInfo> getRunningProcessesInfo() {
        ArrayList<ProcessInfo> list = new ArrayList<>();
        synchronized (this) {
            for(int i = mLruProcesses.size() -1; i >= 0; i--) {
                ProcessRecord app = mLruProcesses.get(i);
                if (app != null && app.info != null ) {
                    ProcessInfo info = createProcessInfo(app);
                    list.add(info);
                }
            }
        }
        return list;
    }

    public ProcessInfo getHomeProcess() {
        synchronized (this) {
            if (mHomeProcess != null && mHomeProcess.info != null) {
                return createProcessInfo(mHomeProcess);
            }
        }
        return null;
    }

    public Intent getLaunchedActivityIntent(IBinder activityToken) {
            ActivityRecord srec;
            synchronized (this) {
                srec = ActivityRecord.forTokenLocked(activityToken);
            }
            if ( srec != null && srec.supportThumbnail && srec.task != null && srec.task.mFullscreen) {
                return srec.intent;
            }
            return null;
    }


    /* package */ boolean keepImpAppAlive(String pkgName) {
        return mPerformanceManagerService != null &&
                    mPerformanceManagerService.keepImpAppAlive(pkgName, mLastMemoryLevel);
    }
    /* package */ boolean keepImpAppAliveForce(String pkgName) {
        return mPerformanceManagerService != null &&
                    mPerformanceManagerService.keepImpAppAliveForce(pkgName);
    }

    /* package */ boolean enhancedPowerHintForLaunchEnabled() {
        return mPerformanceManagerService != null &&
                    mPerformanceManagerService.enhancedPowerHintForLaunchEnabled();
    }

    /* package */ void sendPowerHintForLaunch(int enable) {
         if (mPerformanceManagerService != null &&
             mPerformanceManagerService.enhancedPowerHintForLaunchEnabled()) {
                    mPerformanceManagerService.sendPowerHintForLaunch(enable);
         }
    }

    boolean isProcessHeldWakeLock(ProcessRecord p) {
        if (mPerformanceManagerService != null) {
            return mPerformanceManagerService.isProcessHeldWakeLock(p.uid);
        }
        return false;
    }

    void notifyInstrumentationStatus(boolean start, ProcessRecord app, InstrumentationInfo ii) {
        if (mPerformanceManagerService != null) {
            mPerformanceManagerService.notifyInstrumentationStatus(start,
                                          createProcessInfo(app), ii);
        }
    }

    public String getTopActivityCallingPkg() {
        synchronized(this) {
            final ActivityRecord topActivity = resumedAppLocked();
            if (topActivity == null || topActivity.app == null) {
                return null;
            }
            return topActivity.launchedFromPackage;
        }
    }
    //modify for performance  end
}
