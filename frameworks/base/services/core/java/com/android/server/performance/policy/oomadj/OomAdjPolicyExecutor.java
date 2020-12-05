/*
 * Copyright © 2017 Spreadtrum Communications Inc.
 */

package com.android.server.performance.policy.oomadj;

import android.app.ActivityManagerNative;
import android.app.AppRelevance;
import android.app.AppRelevance.RelevanceDate;
import android.app.ActivityManager;
import android.app.UserHabit;
import android.app.ProcessInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.Bundle;

import android.os.UserHandle;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Slog;

import com.android.internal.app.procstats.ProcessStats;
import com.android.server.am.ActivityManagerServiceEx;
import com.android.server.LocalServices;
import com.android.server.notification.NotificationManagerInternal;
import com.android.server.performance.PerformanceManagerService;
import com.android.server.performance.collector.UserHabitCollector;
import com.android.server.performance.PerformanceManagerService.ActivityStateData;
import com.android.server.performance.PolicyConfig;
import com.android.server.performance.PolicyExecutor;
import com.android.server.performance.PolicyItem;
import com.android.server.performance.status.CpuStatus;
import com.android.server.performance.status.SystemStatus;
import com.android.server.performance.policy.oomadj.OomAdjConstants;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;


import static android.app.ProcessInfo.*;
import static com.android.server.performance.PerformanceManagerDebugConfig.*;

/*
 *  1. increase HOT application's OomAdj while it's in background(Adj 900).
 *  2. decrease None-HOT applcation's OomAdj while it's in forground(Adj 200/600).
 */
public class OomAdjPolicyExecutor extends PolicyExecutor {

    private static String THREAD_NAME = "OomAdjPolicyExecutor";

    private Context mContext;
    private OomAdjConstants mConstants;
    private ActivityManagerServiceEx mAm;
    private NotificationManagerInternal mNotificationMan;
    HashMap<String, UsageData> mUsageData = new HashMap<String, UsageData>();
    private PerformanceManagerService mPerformanceManager;
    private UserHabitCollector mUserHabitCollector;
    private boolean mInStrumentation = false;
    private List<String> mSpecialPackages = new ArrayList<>();
    private String mCurrentFocusPkg = "";
    private boolean mLowRam = ActivityManager.isLowRamDeviceStatic();


    class UsageData {
        public UserHabit stats;
        public float launchCountPos;
        public int launchCountIndex;

        public UsageData(UserHabit stats, float launchCountPos,
                 int launchCountIndex) {
            this.stats = stats;
            this.launchCountPos = launchCountPos;
            this.launchCountIndex = launchCountIndex;
        }
    }

    public OomAdjPolicyExecutor(PolicyItem item,
            PerformanceManagerService service) {
        super();
        mPerformanceManager = service;
        mContext = mPerformanceManager.getContext();
        mAm = (ActivityManagerServiceEx) ActivityManagerNative.getDefault();;
        mNotificationMan = LocalServices
                .getService(NotificationManagerInternal.class);
        mUserHabitCollector = mPerformanceManager.getUserHabitCollector();
        mSpecialPackages.add("cts");
        mSpecialPackages.add("gts");
        mSpecialPackages.add("inputmethod");
        mConstants = createOomAdjConstants(item);
    }

    private OomAdjConstants createOomAdjConstants(PolicyItem item) {
        int ramConfig = mPerformanceManager.getRamConfig();
        String type = OomAdjConstants.CONFIG_TYPE_NORMAL;
        if (ramConfig >= 2048) {
            type= OomAdjConstants.CONFIG_TYPE_NORMAL;
        } else if (ramConfig >= 1024) {
            type = OomAdjConstants.CONFIG_TYPE_1G;
        } else {
            type = OomAdjConstants.CONFIG_TYPE_512M;
        }
        PolicyConfig config = item.getConfig(type);
        if (config != null) {
            return OomAdjConstants.loadFromConfig(config);
        }
        return null;
    }

    @Override
    public String getThreadName() {
        return THREAD_NAME;
    }

    @Override
    protected void handleActivityStateChange(ActivityStateData aData) {
        if (aData == null) {
            return;
        }
        int state = aData.bundle.getInt(KEY_ACTIVITY_STATE);
        Bundle bundle = aData.bundle;
        Intent intent = aData.intent;
        ComponentName app = intent.getComponent();

        if (app == null) {
            return;
        }
        switch (state) {
            case ACTIVITY_STATE_START: {
                break;
            }
            case ACTIVITY_STATE_STOP: {
                break;
            }
            case ACTIVITY_STATE_PAUSE: {
                break;
            }
            case ACTIVITY_STATE_RESUME: {
                mCurrentFocusPkg = app.getPackageName();
                break;
            }
            case ACTIVITY_STATE_FINISH: {
                break;
            }
            case ACTIVITY_STATE_LAUNCHDONE: {
                updateUsageStat();
                break;
            }
            case ACTIVITY_STATE_PROC_START: {
                break;
            }
            default:
                break;
        }
    }

    @Override
    protected void handleInStrumentationChange(Message msg) {
        mInStrumentation = true;
    }

    // get a list of 3rd-apps-usagestats sort by launch couts.
    private void updateUsageStat() {
        try {
            ArrayList<UserHabit> usageStatsByCounts = new ArrayList<>();
            List<UserHabit> us = new ArrayList<UserHabit>();
            if (mUserHabitCollector != null) {
                us= (ArrayList<UserHabit>) mUserHabitCollector.getUserHabits(UserHandle.myUserId()).clone();
            }

            for (UserHabit stat : us) {
                try {
                    ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(stat.getPackageName(), 0);
                    if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        usageStatsByCounts.add(stat);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    //e.printStackTrace();
                }
            }
            Collections.sort(usageStatsByCounts, new Comparator<UserHabit>() {
                @Override
                public int compare(UserHabit lhs, UserHabit rhs) {
                    if (lhs.launchCount == rhs.launchCount) {
                        return 0;
                    }
                    return lhs.launchCount >= rhs.launchCount ? -1 : 1;
                }
            });
            synchronized (mUsageData) {
                for (UserHabit stats : usageStatsByCounts) {
                    int index = usageStatsByCounts.indexOf(stats);
                    float pos = 1.0f * (index + 1) / (usageStatsByCounts.size() + 1);
                    UsageData data = mUsageData.get(stats.getPackageName());
                    if (data == null) {
                        data = new UsageData(stats, 1,  usageStatsByCounts.size()-1);
                    }
                    data.launchCountPos = pos;
                    data.launchCountIndex = index;
                    mUsageData.put(stats.getPackageName(), data);
                }

                if (false) {
                    for (String key : mUsageData.keySet()) {
                        UsageData data = mUsageData.get(key);
                        if (data != null) {
                            Slog.e(TAG, "usage :"+data.stats.getPackageName() +" launchCount = "
                                +data.stats.launchCount + "index = "+data.launchCountIndex + "pos :"+data.launchCountPos);
                        }
                    }
                }
            }
        } catch (Exception e) {
            //e.printStackTrace();
        }
    }

    boolean isPackageMayBusyWithSth(String packageName, int memFactor) {
        if (mNotificationMan == null || mConstants == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long lastPost = mNotificationMan
                .getAppLastActiveNotificationTime(packageName);
        long idle = now - lastPost;
        if (DEBUG_PRIOADJ) {
            String lastDate = new java.text.SimpleDateFormat(
                    "yyyy MM-dd HH:mm:ss").format(new java.util.Date(lastPost));
            String nowDate = new java.text.SimpleDateFormat(
                    "yyyy MM-dd HH:mm:ss").format(new java.util.Date(System
                    .currentTimeMillis()));
            Slog.d(TAG, "package --" + packageName + " last post = "
                    + lastDate + "now = " + nowDate + "idle " + idle);
        }
        return idle <= mConstants.getNotifcationProtectedTimeLimit(memFactor);
    }

    boolean isPackageBeenUnusedForLongTime(UserHabit data, int memFactor) {
        if (data != null) {
            long usedTime = System.currentTimeMillis() - data.getLastTimeUsed();
            long limit = mConstants.getAppIdleTimeLimit(memFactor);
            if (DEBUG_PRIOADJ) {
                String lastUseDate = new java.text.SimpleDateFormat(
                        "yyyy MM-dd HH:mm:ss").format(new java.util.Date(data
                        .getLastTimeUsed()));
                String nowDate = new java.text.SimpleDateFormat(
                        "yyyy MM-dd HH:mm:ss").format(new java.util.Date(System
                        .currentTimeMillis()));
                Slog.e(TAG, "package " + data.getPackageName() + "lastuse = "
                        + lastUseDate + "now :" + nowDate + " mLaunchCount = "
                        + data.launchCount + "mem lvl" + memFactor
                        + "idletime = " + usedTime + "limit = "
                        + limit);
            }
            if (usedTime >= limit) {
                // has been idle for over limit?, let it down..
                return true;
            }
        }
        return false;
    }

    boolean isSpecialPackage(String packageName) {
        for (String name : mSpecialPackages) {
            if (packageName.contains(name)) {
                return true;
            }
        }
        return false;
    }

    boolean isPackageRecentUsedForAWhile(String packageName, int memFactor) {
        if (mUserHabitCollector != null && mConstants != null) {
            UserHabit data = mUserHabitCollector.getPackageUserHabits(
                    UserHandle.myUserId(), packageName);
            if (data == null) {
                // if applcation not in list, may not be start?
                return false;
            }
            long usedTime = System.currentTimeMillis() - data.getLastTimeUsed();
            long limit = mConstants.getAppIdleTimeLimit(memFactor);
            if (DEBUG_PRIOADJ) {
                String lastUseDate = new java.text.SimpleDateFormat(
                        "yyyy MM-dd HH:mm:ss").format(new java.util.Date(data
                        .getLastTimeUsed()));
                String nowDate = new java.text.SimpleDateFormat(
                        "yyyy MM-dd HH:mm:ss").format(new java.util.Date(System
                        .currentTimeMillis()));
                Slog.e(TAG, "package " + packageName + "lastuse = "
                        + lastUseDate + "now :" + nowDate + " mLaunchCount = "
                        + data.launchCount + "mem lvl" + memFactor +
                        "recent idle time = " + usedTime
                        + "limit = " + limit);
            }
            if (usedTime <= limit) {
                // recently used, keep it stay in memory for a while
                return true;
            }
        }
        return false;
    }

    boolean isPackageRecentUsedForAWhile(UserHabit data, int memFactor) {
        if (data != null) {
            long usedTime = System.currentTimeMillis() - data.getLastTimeUsed();
            long limit = mConstants.getAppIdleTimeLimit(memFactor);
            if (DEBUG_PRIOADJ) {
                String lastUseDate = new java.text.SimpleDateFormat(
                        "yyyy MM-dd HH:mm:ss").format(new java.util.Date(data
                        .getLastTimeUsed()));
                String nowDate = new java.text.SimpleDateFormat(
                        "yyyy MM-dd HH:mm:ss").format(new java.util.Date(System
                        .currentTimeMillis()));
                Slog.e(TAG, "package " + data.getPackageName() + "lastuse = "
                        + lastUseDate + "now :" + nowDate + " mLaunchCount = "
                        + data.launchCount + "mem lvl" + memFactor +
                        "recent idle time = " + usedTime
                        + "limit = " + limit);
            }
            if (usedTime <= limit) {
                // recently used, keep it stay in memory for a while
                return true;
            }
        }
        return false;
    }

    boolean isPackageRelevance(ProcessInfo app) {
        RelevanceDate rd = getPackageRelevanceDate(app);
        if (rd != null && mConstants != null) {
            return rd.launchCount > mConstants.getRelevanceAppMinLaunchCount() &&
            rd.launchCountIndex < mConstants.getRelevanceAppCount();
        }
        return false;
    }

    RelevanceDate getPackageRelevanceDate(ProcessInfo app) {
        AppRelevance ar = null;
        UserHabit data = null;
        if (mUserHabitCollector != null) {
            if (mCurrentFocusPkg != null) {
                data = mUserHabitCollector.getPackageUserHabits(
                        UserHandle.myUserId(), mCurrentFocusPkg);
                if (data != null) {
                    ar = data.getAppRelevance();
                    if (ar != null) {
                        RelevanceDate rd = ar.getRelevanceDate(app.packageName,
                                AppRelevance.getType());
                        if (DEBUG_PRIOADJ) {
                            Slog.d(TAG, "RelevanceDate  for" + app.packageName
                                    + " top:" + mCurrentFocusPkg + "is " + rd);
                        }
                        return rd;
                    }
                }
            }
        }
        return null;
    }

    ProcessInfo getCurrentFocusPackage() {
        ArrayList<ProcessInfo> runningList = mAm.getRunningProcessesInfo();
        ProcessInfo top = null;
        for (int i = 0; i < runningList.size(); i++) {
            ProcessInfo p = runningList.get(i);
            if (p != null && p.curAdj == 0
                    && "top-activity".equals(p.curAdjType)) {
                top = p;
                break;
            }
        }
        return top;
    }

    long getPackageLaunchCount(String packageName) {
        synchronized (mUsageData) {
            UsageData data = mUsageData.get(packageName);
            if (data != null) {
                return data.stats.launchCount;
            }
            return 0;
        }
    }

    long getPackageLastUseTime(String packageName) {
        synchronized (mUsageData) {
            UsageData data = mUsageData.get(packageName);
            if (data != null) {
                return data.stats.getLastTimeUsed();
            }
            return 0;
        }
    }

    private void dropAdj(ProcessInfo app, int memFactor, long now,
            long nowElapsed) {
        int adj = mConstants.getAdjTunningValue(app.curAdj, memFactor);
        app.adjTunned = true;
        app.tunnedAdj = adj;
        app.tunnedSchedGroup = ProcessInfo.SCHED_GROUP_BACKGROUND;
        mPerformanceManager.applyOomAdjByProcessInfo(app, now, nowElapsed);
        if (DEBUG_PRIOADJ) {
            Slog.d(TAG, "doAdjDropIfNeeded: " + app + "Tunned ADJ" + adj);
        }
    }

    // increase hot apps while adj > HOT_APP_ADJ, let it stay in memory
    // increase recent apps while adj > CACHED, let it stay in memory for a
    // while.
    public boolean increaseAdjIfNeeded(ProcessInfo app, int memFactor,
            long now, long nowElapsed) {
        boolean shouldIncrease = false;
        String reason = "none";
        if (mConstants == null) {
            return false;
        }
        if (app != null && (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            return false;
        }
        if (mInStrumentation) {
            return false;
        }
        if (app != null && app.packageName != null
                && isSpecialPackage(app.packageName)) {
            return false;
        }
        if (app.curAdj > mConstants.getHotAppAdj() && isProcessHotApp(app)) {
            app.tunnedAdj = mConstants.getHotAppAdj();
            shouldIncrease = true;
            reason = "hot app";
        } else if (app.curAdj >= ProcessInfo.CACHED_APP_MIN_ADJ
                && isPackageRecentUsedForAWhile(app.packageName, memFactor)) {
            app.tunnedAdj = mConstants.getRecentAppAdj();
            shouldIncrease = true;
            reason = "recent app";
        } else if (app.curAdj >= ProcessInfo.CACHED_APP_MIN_ADJ
                && isPackageRelevance(app)
                && app.processName.equals(app.packageName)) {
            app.tunnedAdj = mConstants.getRelevanceAppAdj();;
            shouldIncrease = true;
            reason = "relevance app";
        }
        if (shouldIncrease == true) {
            if (DEBUG_PRIOADJ) {
                Slog.d(TAG, "increaseAdjLocked for:"
                        + reason + ","
                        + app.processName + "(" + app.pid + ")" + " adj:"
                        + app.curAdj + "current memPresure = " + memFactor
                        + "launch cout = "
                        + getPackageLaunchCount(app.packageName)
                        + "app reelevance:" + getPackageRelevanceDate(app));
            }
            app.adjTunned = true;
            app.tunnedProcState = ActivityManager.PROCESS_STATE_SERVICE;
            app.tunnedAdjType = "force-fg";
            app.tunnedSchedGroup = ProcessInfo.SCHED_GROUP_BACKGROUND;
            mPerformanceManager.applyOomAdjByProcessInfo(app, now, nowElapsed);
            return true;
        }
        return false;
    }

    private boolean isProcessHotApp(ProcessInfo app) {
        if (mConstants == null) {
            return false;
        }
        if (app != null && !app.packageName.equals(app.processName)) {
            return false;
        }
        synchronized (mUsageData) {
            UsageData data = mUsageData.get(app.packageName);
            if (data != null && data.launchCountIndex < mConstants.getHotAppCount()
                    && data.stats.launchCount >= mConstants.getHotAppMinLaunchCount()) {
                return true;
            }
        }
        return false;
    }

    public boolean isPackageUsrFavorite(String pkgName) {
        synchronized (mUsageData) {
            UsageData data = mUsageData.get(pkgName);
            if (data != null && data.launchCountIndex < mConstants.getHotAppCount()
                    && data.stats.launchCount >= mConstants.getHotAppMinLaunchCount()) {
                return true;
            }
        }
        return false;
    }

    private boolean isProcessAdjCare(int adj) {
        if (adj == ProcessInfo.PERCEPTIBLE_APP_ADJ
                || adj == ProcessInfo.SERVICE_ADJ
                || adj == ProcessInfo.SERVICE_B_ADJ
                || adj == ProcessInfo.VISIBLE_APP_ADJ) {
            // avoid cold app to be "persist"
            return true;
        }
        return false;
    }

    private boolean isGmsApk (String pkgName) {
        if (pkgName != null && pkgName.contains("google")) {
            return true;
        }
        return false;
    }
    private boolean dropGmsProc() {
        return mLowRam;
    }

    public boolean doAdjDropIfNeeded(ProcessInfo app, int memFactor, long now,
            long nowElapsed) {
        if (mConstants == null) {
            return false;
        }
        if (mInStrumentation) {
            return false;
        }
        if (memFactor < ProcessStats.ADJ_MEM_FACTOR_MODERATE) {
            return false;
        }
        // system apps always go first
        if (isGmsApk(app.packageName) && dropGmsProc()) {
            //drop gms...
        } else if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            return false;
        }
        // pass special pkgs
        if (app != null && app.packageName != null
                && isSpecialPackage(app.packageName)) {
            return false;
        }
        if (!isProcessAdjCare(app.curAdj)) {
            return false;
        }

        if(isPackagePerceptibleToUser(app, memFactor)) {
            return false;
        }
        dropAdj(app, memFactor, now, nowElapsed);
        return true;
    }

    private boolean isPackagePerceptibleToUser(ProcessInfo app, int memFactor) {
        // if app holds a wakelock, let it(or them ) go, may bg playing music or
        // bg download sth.
        if (mPerformanceManager.isProcessHeldWakeLock(app.uid) && mPerformanceManager.isBusy(app.packageName, app.uid)) {
            if (DEBUG_PRIOADJ) Slog.d(TAG, "isPackagePerceptibleToUser " + app.packageName + " is doing something");
            return true;
        }
        if (isProcessHotApp(app)) {
            return true;
        }
        if (isPackageMayBusyWithSth(app.packageName, memFactor)) {
            return true;
        }
        UserHabit data = null;
        if (mUserHabitCollector != null) {
            data = mUserHabitCollector.getPackageUserHabits(
                    UserHandle.myUserId(), app.packageName);
        }
        if (data == null) {
            return true;
        }
        if (!isPackageBeenUnusedForLongTime(data, memFactor)) {
            return true;
        }
        if (isPackageRecentUsedForAWhile(data, memFactor)) {
            return true;
        }
        return false;
    }

    public void dump(PrintWriter pw, String[] args) {
        pw.println("PRIO ADJ:");
        synchronized (mUsageData) {
            for (String key : mUsageData.keySet()) {
                UsageData data = mUsageData.get(key);
                if (data != null) {
                    pw.println("usage :" + data.stats.getPackageName()
                            + " launchCount = " + data.stats.launchCount
                            + "index = " + data.launchCountIndex + "pos :"
                            + data.launchCountPos);
                }
            }
        }
        if (mConstants != null ) {
            mConstants.dump(pw);
        }
    }

}
