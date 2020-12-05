/*
 * Copyright 2018 Spreadtrum Communications Inc.
 */

package com.android.server.performance.policy.sched;

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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.os.UserHandle;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.SystemProperties;
import android.os.Trace;
import android.util.Log;
import android.util.Slog;
import android.hardware.power.V1_0.PowerHint;

import com.android.server.am.ActivityManagerServiceEx;
import com.android.server.LocalServices;
import com.android.server.performance.PerformanceManagerService;
import com.android.server.performance.PolicyExecutor;
import com.android.server.performance.PolicyConfig;
import com.android.server.performance.PolicyItem;
import com.android.server.performance.policy.sched.SchedAdjustment.SchedProcessInfo;
import com.android.server.performance.policy.sched.SchedAdjustment.SchedThreadInfo;
import com.android.internal.util.DumpUtils;
import com.android.internal.app.procstats.ProcessStats;
import com.android.internal.R;

import static android.app.ProcessInfo.*;
import static android.os.Process.SCHED_FIFO;
import static android.os.Process.SCHED_OTHER;
import static android.os.Process.SCHED_RESET_ON_FORK;
import static com.android.server.performance.PerformanceManagerDebugConfig.*;
import static com.android.server.performance.PerformanceManagerService.*;

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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProcessSchedExecutor extends PolicyExecutor {

    private static String THREAD_NAME = "SchedPolicyExecutor";
    private static final int TOUCH_APP_PRIO = -19;
    // set unitymain/UnityGfxDeviceW to SCHED_RR to reduce runnable
    private PerformanceManagerService mService;
    private ActivityManagerServiceEx mAm;
    // record current boost info ,reset while focus changed
    public ProcessSchedBoostInfo mGameBoosts;
    public ProcessSchedBoostInfo mTouchBoosts;
    public ProcessSchedBoostInfo mStartBoosts;
    private ArrayList<Integer> mCurrTouchBoostThreads;
    private String mCurrPackageName;

    private SchedAdjustment mSchedAdjustment;
    private SchedTunningRunnable mSchedTunningRunnable = new SchedTunningRunnable();

    private class ProcessSchedBoostInfo {
        Integer pid;
        HashMap<Integer, ThreadInfo> boostThreads; // Tnteger:tid

        private ProcessSchedBoostInfo(Integer pid, HashMap<Integer, ThreadInfo> tids) {
            this.pid = pid;
            this.boostThreads = tids;
        }

        public String toString() {
            String tids = "TID:";
            if (boostThreads != null && boostThreads.size() != 0) {
                for (Integer tid : boostThreads.keySet()) {
                    tids = tids + tid + " ";
                }
            }
            return "PID:" + pid + "|" + tids;
        }
    }

    private class ThreadInfo {
        int tid;
        int prio;
        int schedClass;

        private ThreadInfo() {

        }

        private ThreadInfo(int tid, int prio, int schedClass) {
            this.tid = tid;
            this.prio = prio;
            this.schedClass = schedClass;
        }

        public String toString() {
            return " |tid:" + tid + "|prio" + prio + "|schedClass:" + schedClass;
        }
    }

    @Override
    public String getThreadName() {
        return THREAD_NAME;
    }

    public ProcessSchedExecutor(PolicyItem config, PerformanceManagerService service) {
        super();
        mSchedAdjustment = SchedAdjustment.loadFromConfig(config.getConfig(SchedAdjustment.TAG));
        mService = service;
        mAm = (ActivityManagerServiceEx) ActivityManagerNative.getDefault();
    }

    ProcessInfo getTopApp() {
        ArrayList<ProcessInfo> runningList = mAm.getRunningProcessesInfo();
        ProcessInfo top = null;
        if (runningList == null) {
            return null;
        }
        for (int i = 0; i < runningList.size(); i++) {
            ProcessInfo p = runningList.get(i);
            if (p != null && p.curAdj == 0 && "top-activity".equals(p.curAdjType)) {
                top = p;
                break;
            }
        }
        return top;
    }

    @Override
    protected void handleActivityStateChange(ActivityStateData aData) {
        if (aData != null) {
            int state = aData.bundle.getInt(ProcessInfo.KEY_ACTIVITY_STATE);
            ComponentName app = aData.intent.getComponent();
            if (mService.isMonkeyOrInStrumentation()) {
                return;
            }
            if (state == ProcessInfo.ACTIVITY_STATE_RESUME && app != null) {
                try {
                    tunningSchedBoostIfNeeded(app.getPackageName());
                } catch (Exception e) {
                }
            }
        }
    }

    public ArrayList<Integer> getBoostThreadsOfApp(int pid) {
        ArrayList<Integer> tids = new ArrayList<Integer>();
        File procTaskDir = new File("/proc/" + pid + "/task");
        BufferedReader reader = null;
        int tidCounts = 0;
        for (File thread : procTaskDir.listFiles()) {
            if (tidCounts > 1) {
                break;
            }
            try {
                reader = new BufferedReader(new FileReader(new File(thread.getAbsolutePath() + "/comm")));
                String comm = reader.readLine();
                if ("RenderThread".equals(comm) || pid == Integer.valueOf(thread.getName())) {
                    tids.add(Integer.valueOf(thread.getName()));
                    tidCounts++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (DEBUG_SERVICE)
            Slog.d(TAG, "getBoostThreadsOfApp: tids:" + tids);
        return tids;
    }

    public void scheduleBoostApp(boolean boost, Integer id, boolean isPid) {
        synchronized (this) {
            HashMap<Integer, ThreadInfo> threadTids = new HashMap<>();
            ArrayList<Integer> tids = new ArrayList<Integer>();
            if (isPid) {
                tids = getBoostThreadsOfApp(id);
            } else {
                tids.add(id);
            }
            for (Integer t : tids) {
                ThreadInfo threadInfo = new ThreadInfo();
                threadInfo.tid = t;
                if (boost) {
                    threadInfo.prio = 1;
                    threadInfo.schedClass = Process.SCHED_RR;
                } else {
                    threadInfo.prio = -10;
                    threadInfo.schedClass = Process.SCHED_OTHER;
                }
                threadTids.put(t, threadInfo);
            }
            mStartBoosts = new ProcessSchedBoostInfo(id, threadTids);
            scheduleBoost(mStartBoosts);
        }
    }

    public void scheduleBoostsWhenTouch() {
        synchronized (this) {
            if (mCurrTouchBoostThreads == null || mCurrTouchBoostThreads.size() == 0) {
                return;
            }
            try {
                HashMap<Integer, ThreadInfo> oldTids = new HashMap<Integer, ThreadInfo>();
                HashMap<Integer, ThreadInfo> newTids = new HashMap<Integer, ThreadInfo>();
                for (Integer tid : mCurrTouchBoostThreads) {
                    if (DEBUG_SERVICE)
                        Slog.d(TAG,
                                "scheduleBoostsWhenTouch, tid:" + tid + "tid scheduler:"
                                        + Process.getThreadScheduler(tid));
                    if (Process.getThreadScheduler(tid) == Process.SCHED_OTHER) {
                        if (Process.getThreadPriority(tid) <= TOUCH_APP_PRIO) {
                            break;
                        }
                        ThreadInfo oldThread =
                                new ThreadInfo(tid, Process.getThreadPriority(tid), Process.getThreadScheduler(tid));
                        oldTids.put(tid, oldThread);
                        ThreadInfo newThread = new ThreadInfo(tid, TOUCH_APP_PRIO, Process.getThreadScheduler(tid));
                        newTids.put(tid, newThread);
                    }
                }
                if (oldTids.size() == 0) {
                    return;
                }
                mTouchBoosts = new ProcessSchedBoostInfo(mCurrTouchBoostThreads.get(0), oldTids);
                if (DEBUG_SERVICE)
                    Slog.d(TAG, "scheduleBoostsWhenTouch: touchBoost:" + mTouchBoosts);
                ProcessSchedBoostInfo currBoost = new ProcessSchedBoostInfo(mCurrTouchBoostThreads.get(0), newTids);
                Trace.asyncTraceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "scheduleBoost" + currBoost.pid, 666);
                scheduleBoost(currBoost);
            } catch (IllegalArgumentException e) {
                if (DEBUG_SERVICE) {
                    Slog.w(TAG, "Failed to scheduleBoostsWhenTouch :\n" + e);
                    e.printStackTrace();
                }
            } catch (SecurityException e) {
                Slog.w(TAG, "Failed to scheduleBoostsWhenTouch, not allowed:\n" + e);
            } catch (Exception e) {
                Slog.w(TAG, "Failed to scheduleBoostsWhenTouch, error:\n" + e);
            }
        }
    }

    public void scheduleBoostsAsRegual() {
        synchronized (this) {
            if (DEBUG_SERVICE)
                Slog.w(TAG, "scheduleBoostsAsRegual,pid:" + mTouchBoosts);
            if (mTouchBoosts != null) {
                scheduleBoost(mTouchBoosts);
                Trace.asyncTraceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER, "scheduleBoost" + mTouchBoosts.pid, 666);
                mTouchBoosts = null;
            }
        }
    }

    public void scheduleBoost(ProcessSchedBoostInfo processInfo) {
        if (DEBUG_SERVICE)
            Slog.w(TAG, "scheduleBoost,pid:" + processInfo.pid + ",boostthread:" + processInfo.boostThreads);
        for (Integer tid : processInfo.boostThreads.keySet()) {
            ThreadInfo threadInfo = processInfo.boostThreads.get(tid);
            try {
                if (threadInfo.schedClass == Process.SCHED_OTHER) {
                    Process.setThreadScheduler(threadInfo.tid, threadInfo.schedClass, 0);
                    Process.setThreadPriority(threadInfo.tid, threadInfo.prio);
                } else {
                    Process.setThreadScheduler(tid, Process.SCHED_RR | Process.SCHED_RESET_ON_FORK, 1);
                }
            } catch (IllegalArgumentException e) {
                if (DEBUG_SERVICE) {
                    Slog.w(TAG, "Failed to set scheduling policy, :\n" + e);
                    e.printStackTrace();
                }
            } catch (SecurityException e) {
                Slog.w(TAG, "Failed to set scheduling policy, not allowed:\n" + e);
            } catch (Exception e) {
                Slog.w(TAG, "Failed to set scheduling policy, error:\n" + e);
            }
        }
        if (DEBUG_SERVICE)
            Slog.w(TAG, "scheduleBoost after boost,pid:" + processInfo.pid + ",boostthread:" + processInfo.boostThreads);
        return;
    }

    private class SchedTunningRunnable implements Runnable {
        @Override
        public void run() {
            schedBoostForGame();
        }
    }

    private void tunningSchedBoostIfNeeded(String pkgName) {
        ProcessInfo top = getTopApp();
        synchronized (this) {	
            SchedProcessInfo schedProcessInfo = null;
            if (top != null) {
                schedProcessInfo = mSchedAdjustment.mMap.get(top.packageName);
                if (schedProcessInfo == null) {
                    if (mCurrPackageName != top.packageName) {
                        scheduleBoostsAsRegual();
                    }
                    mCurrTouchBoostThreads = getBoostThreadsOfApp(top.pid);
                    mCurrPackageName = top.packageName;

                } else {
                    mCurrTouchBoostThreads = null;
                }
            }
            // focus change, reset?
            if (DEBUG_SERVICE)
                Slog.w(TAG, "tunningSchedBoostIfNeeded,top:" + top + ",mCurrentBoost:" + mGameBoosts);
            if (mGameBoosts != null && mGameBoosts.boostThreads != null) {
                boolean needRest = false;
                boolean appSwitch = !(top.pid == mGameBoosts.pid);
                // for common case ,package changed
                if (appSwitch) {
                    needRest = true;
                } else {
                    if (mGameBoosts.boostThreads.size() == 1) {
                        mHandler.post(mSchedTunningRunnable);
                    }
                }
                if (needRest && mGameBoosts != null) {
                    mHandler.removeCallbacks(mSchedTunningRunnable);
                    scheduleBoost(mGameBoosts);
                    mGameBoosts = null;
                } else {
                    // nothing changes
                    return;
                }
            }
            // schedRR the target
            // double check for process(xxx:bbb /xxx)
            if (top != null) {
                if (schedProcessInfo != null) {
                    mHandler.postDelayed(mSchedTunningRunnable, 5000);
                    // } else {
                    // mHandler.post(mSchedTunningRunnable);
                }
            }
        }
    }

    private void schedBoostForGame() {
        ProcessInfo top = getTopApp();
        ThreadInfo oldThread;
        ThreadInfo newThread;
        HashMap<Integer, ThreadInfo> oldTids = new HashMap<Integer, ThreadInfo>();
        HashMap<Integer, ThreadInfo> newTids = new HashMap<Integer, ThreadInfo>();

        synchronized (this) {
            if (top != null) {
                try {
                    SchedProcessInfo schedProcessInfo = mSchedAdjustment.mMap.get(top.packageName);
                    if (schedProcessInfo == null) {
                        return;
                    }

                    File procTaskDir = new File("/proc/" + top.pid + "/task");
                    BufferedReader reader = null;
                    for (File thread : procTaskDir.listFiles()) {
                        try {
                            reader = new BufferedReader(new FileReader(new File(thread.getAbsolutePath() + "/comm")));
                            String comm = reader.readLine();
                            SchedThreadInfo schedInfo = schedProcessInfo.schedThreadInfos.get(comm);
                            if (schedInfo == null) {
                                for (String t : schedProcessInfo.schedThreadInfos.keySet()) {
                                    if (t.endsWith("*") && comm.startsWith(t.replace("*", ""))) {
                                        schedInfo = schedProcessInfo.schedThreadInfos.get(t);
                                        break;
                                    }
                                }
                            }
                            if (schedInfo == null)
                                continue;
                            int tid = Integer.valueOf(thread.getName());
                            oldThread =
                                    new ThreadInfo(tid, Process.getThreadPriority(tid), Process.getThreadScheduler(tid));
                            oldTids.put(tid, oldThread);
                            newThread = new ThreadInfo(tid, schedInfo.prio, schedInfo.schedClass);
                            newTids.put(tid, newThread);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                if (reader != null) {
                                    reader.close();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    mGameBoosts = new ProcessSchedBoostInfo(top.pid, oldTids);
                    ProcessSchedBoostInfo currBoost = new ProcessSchedBoostInfo(top.pid, newTids);
                    Slog.w(TAG, "tunningSchedBoostIfNeeded,mGameBoosts:" + mGameBoosts + ",currBoost:" + currBoost);
                    scheduleBoost(currBoost);
                } catch (IllegalArgumentException e) {
                    Slog.w(TAG, "Failed to set scheduling policy, thread does not exist:\n" + e);
                } catch (SecurityException e) {
                    Slog.w(TAG, "Failed to set scheduling policy, not allowed:\n" + e);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void dump(PrintWriter pw, String[] args) {
        pw.println("PRIO SCHED:");
        synchronized (this) {
            pw.println("CURRENT SCHED BOOST ->" + mGameBoosts);
        }
        mSchedAdjustment.dump(pw);
    }
}
