/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server;

import android.app.IActivityController;
import android.os.Binder;
import android.os.Build;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.system.StructRlimit;
import com.android.internal.os.ZygoteConnectionConstants;
import com.android.server.am.ActivityManagerService;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hidl.manager.V1_0.IServiceManager;
import android.os.Debug;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.Looper;
import android.os.Process;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
//bug683009 adds a detecting function here for FD-leak into  Watchdog
import java.io.FileDescriptor;
import android.system.Os;
import android.system.ErrnoException;
import android.system.OsConstants;
import com.android.internal.util.SprdRuntimeInfo;
import android.os.Build;
import android.app.ActivityManager;
import com.android.internal.util.MemInfoReader;

/** This class calls its monitor every minute. Killing this process if they don't return **/
public class Watchdog extends Thread {
    static final String TAG = "Watchdog";

    // Set this to true to use debug default values.
    static final boolean DB = false;

    // Note 1: Do not lower this value below thirty seconds without tightening the invoke-with
    //         timeout in com.android.internal.os.ZygoteConnection, or wrapped applications
    //         can trigger the watchdog.
    // Note 2: The debug value is already below the wait time in ZygoteConnection. Wrapped
    //         applications may not work with a debug build. CTS will fail.
    static final long DEFAULT_TIMEOUT = DB ? 10*1000 : 60*1000;
    static final long CHECK_INTERVAL = DEFAULT_TIMEOUT / 2;

    // These are temporally ordered: larger values as lateness increases
    static final int COMPLETED = 0;
    static final int WAITING = 1;
    static final int WAITED_HALF = 2;
    static final int OVERDUE = 3;

    // Which native processes to dump into dropbox's stack traces
    public static final String[] NATIVE_STACKS_OF_INTEREST = new String[] {
        "/system/bin/audioserver",
        "/system/bin/cameraserver",
        "/system/bin/drmserver",
        "/system/bin/mediadrmserver",
        "/system/bin/mediaserver",
        "/system/bin/sdcard",
        "/system/bin/surfaceflinger",
        "media.extractor", // system/bin/mediaextractor
        "media.metrics", // system/bin/mediametrics
        "media.codec", // vendor/bin/hw/android.hardware.media.omx@1.0-service
        "com.android.bluetooth",  // Bluetooth service
        "statsd",  // Stats daemon
        "com.android.commands.monkey" // bug762460,add monkey stack
    };
    //bug931278  Which java processes to dump stack trace
    public static final String[] JAVA_STACKS_OF_INTEREST = new String[]{
        "com.android.commands.monkey",
        "com.google.android.gms.persistent"
    };
    public static final String[] JAVA_PROCESS_TOBE_KILL = new String[]{
        "com.google.android.gms.persistent",
        //bug1021561, backgound process's priority is too higher to be killed
        //so kill them when watchdog_30seconds happens
        "com.google.android.apps.messaging",
        "com.google.android.inputmethod.latin",
        "com.google.android.apps.assistant",
        //these process is 3rd apps,which may be installed or not
        "com.meitu.meiyancamera",
        "com.jiayuan",
        "com.sohu.newsclient",
        "com.qqgame.hlddz",
        "com.wepie.snake.tencent",
        "com.tencent.ttpic",
        "com.mobike.mobikeapp",
        "com.gotokeep.keep"
    };
    //bug931278 end

    public static final List<String> HAL_INTERFACES_OF_INTEREST = Arrays.asList(
        "android.hardware.audio@2.0::IDevicesFactory",
        "android.hardware.audio@4.0::IDevicesFactory",
        "android.hardware.bluetooth@1.0::IBluetoothHci",
        "android.hardware.camera.provider@2.4::ICameraProvider",
        "android.hardware.graphics.composer@2.1::IComposer",
        "android.hardware.media.omx@1.0::IOmx",
        "android.hardware.media.omx@1.0::IOmxStore",
        "android.hardware.sensors@1.0::ISensors",
        "android.hardware.vr@1.0::IVr"
    );

    static Watchdog sWatchdog;

    /* This handler will be used to post message back onto the main thread */
    final ArrayList<HandlerChecker> mHandlerCheckers = new ArrayList<>();
    final HandlerChecker mMonitorChecker;
    ContentResolver mResolver;
    ActivityManagerService mActivity;

    int mPhonePid;
    IActivityController mController;
    boolean mAllowRestart = true;
    final OpenFdMonitor mOpenFdMonitor;

    //bug683009 adds for FD-leak into  Watchdog
    int mLastCheckedFdNumber = 0;
    long mLastCheckedTime = 0;
    static long FDCHECK_INTERVAL = ActivityManager.isLowRamDeviceStatic() ? 6*60*1000 : 5*60*1000;
    static final int   FDLEAK_THRESHOLD = 30000;
    static final int PROCESSLIMITAPPS_A53ONLY_DEFAULT = 4;
    static final int PROCESSLIMITAPPS_DEFAULT = -1;
    int  mAMSProcessLimitChanged = PROCESSLIMITAPPS_DEFAULT;
    int  mAMSProcessLimitToBeChanged = PROCESSLIMITAPPS_DEFAULT;

    static int[] mProcessLimitThreshold= new int[] {3000,1000,500,200,0}; //ms
    static int[] mProcessLimitApps= new int[] {0,4,6,8,PROCESSLIMITAPPS_DEFAULT};
    static int[] mCpuLoadAvg=new int[] {25,20,16,12,0};
    static int  mProcessLimitSum = 5;

    public static final int PROC_SPACE_TERM_WD = (int)' ';
    public static final int PROC_OUT_FLOAT_WD = 0x4000;
    private static final int[] WD_LOAD_AVERAGE_FORMAT = new int[] {
        PROC_SPACE_TERM_WD|PROC_OUT_FLOAT_WD,                 // 0: 1 min
        PROC_SPACE_TERM_WD|PROC_OUT_FLOAT_WD,                 // 1: 5 mins
        PROC_SPACE_TERM_WD|PROC_OUT_FLOAT_WD                  // 2: 15 mins
    };

     //add for kill some app with high priority for more memory
    static long  KILLPROCESS_THRESHOLD_PERCENT =  20;  //20%
    private static final String[] PROCESS_KILL_FORMOREMEMORY = new String[]{
        "com.google.android.inputmethod.latin",
        "com.android.vending",
        "com.android.chrome",
        "com.google.android.googlequicksearchbox:search",
        "com.google.android.googlequicksearchbox:interactor",
        "com.google.android.setupwizard",
        "com.google.android.calendar",
    };
    private long mLastKillForMoreMemoryTime = 0;
    static long KILLPROCESSFORMOREMEMORY_PERIOD = 1800*1000; //30minutes
    /**
     * Used for checking status of handle threads and scheduling monitor callbacks.
     */
    public final class HandlerChecker implements Runnable {
        private final Handler mHandler;
        private final String mName;
        private final long mWaitMax;
        private final ArrayList<Monitor> mMonitors = new ArrayList<Monitor>();
        private boolean mCompleted;
        private Monitor mCurrentMonitor;
        private long mStartTime;

        HandlerChecker(Handler handler, String name, long waitMaxMillis) {
            mHandler = handler;
            mName = name;
            mWaitMax = waitMaxMillis;
            mCompleted = true;
        }

        public void addMonitor(Monitor monitor) {
            mMonitors.add(monitor);
        }

        public void scheduleCheckLocked() {
            if (mMonitors.size() == 0 && mHandler.getLooper().getQueue().isPolling()) {
                // If the target looper has recently been polling, then
                // there is no reason to enqueue our checker on it since that
                // is as good as it not being deadlocked.  This avoid having
                // to do a context switch to check the thread.  Note that we
                // only do this if mCheckReboot is false and we have no
                // monitors, since those would need to be executed at this point.
                mCompleted = true;
                return;
            }

            if (!mCompleted) {
                // we already have a check in flight, so no need
                return;
            }

            mCompleted = false;
            mCurrentMonitor = null;
            mStartTime = SystemClock.uptimeMillis();
            mHandler.postAtFrontOfQueue(this);
        }

        public boolean isOverdueLocked() {
            return (!mCompleted) && (SystemClock.uptimeMillis() > mStartTime + mWaitMax);
        }

        public int getCompletionStateLocked() {
            if (mCompleted) {
                return COMPLETED;
            } else {
                long latency = SystemClock.uptimeMillis() - mStartTime;
                if (latency < mWaitMax/2) {
                    return WAITING;
                } else if (latency < mWaitMax) {
                    return WAITED_HALF;
                }
            }
            return OVERDUE;
        }

        public Thread getThread() {
            return mHandler.getLooper().getThread();
        }

        public String getName() {
            return mName;
        }

        public String describeBlockedStateLocked() {
            if (mCurrentMonitor == null) {
                return "Blocked in handler on " + mName + " (" + getThread().getName() + ")";
            } else {
                return "Blocked in monitor " + mCurrentMonitor.getClass().getName()
                        + " on " + mName + " (" + getThread().getName() + ")";
            }
        }

        @Override
        public void run() {
            final int size = mMonitors.size();
            for (int i = 0 ; i < size ; i++) {
                synchronized (Watchdog.this) {
                    mCurrentMonitor = mMonitors.get(i);
                }
                mCurrentMonitor.monitor();
            }

            synchronized (Watchdog.this) {
                mCompleted = true;
                mCurrentMonitor = null;
            }
        }
    }

    final class RebootRequestReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (intent.getIntExtra("nowait", 0) != 0) {
                rebootSystem("Received ACTION_REBOOT broadcast");
                return;
            }
            Slog.w(TAG, "Unsupported ACTION_REBOOT broadcast: " + intent);
        }
    }

    /** Monitor for checking the availability of binder threads. The monitor will block until
     * there is a binder thread available to process in coming IPCs to make sure other processes
     * can still communicate with the service.
     */
    private static final class BinderThreadMonitor implements Watchdog.Monitor {
        @Override
        public void monitor() {
            Binder.blockUntilThreadAvailable();
        }
    }

    public interface Monitor {
        void monitor();
    }

    public static Watchdog getInstance() {
        if (sWatchdog == null) {
            sWatchdog = new Watchdog();
        }

        return sWatchdog;
    }

    private Watchdog() {
        super("watchdog");
        // Initialize handler checkers for each common thread we want to check.  Note
        // that we are not currently checking the background thread, since it can
        // potentially hold longer running operations with no guarantees about the timeliness
        // of operations there.

        // The shared foreground thread is the main checker.  It is where we
        // will also dispatch monitor checks and do other work.
        mMonitorChecker = new HandlerChecker(FgThread.getHandler(),
                "foreground thread", DEFAULT_TIMEOUT);
        mHandlerCheckers.add(mMonitorChecker);
        // Add checker for main thread.  We only do a quick check since there
        // can be UI running on the thread.
        mHandlerCheckers.add(new HandlerChecker(new Handler(Looper.getMainLooper()),
                "main thread", DEFAULT_TIMEOUT));
        // Add checker for shared UI thread.
        mHandlerCheckers.add(new HandlerChecker(UiThread.getHandler(),
                "ui thread", DEFAULT_TIMEOUT));
        // And also check IO thread.
        mHandlerCheckers.add(new HandlerChecker(IoThread.getHandler(),
                "i/o thread", DEFAULT_TIMEOUT));
        // And the display thread.
        mHandlerCheckers.add(new HandlerChecker(DisplayThread.getHandler(),
                "display thread", DEFAULT_TIMEOUT));

        // Initialize monitor for Binder threads.
        addMonitor(new BinderThreadMonitor());

        mOpenFdMonitor = OpenFdMonitor.create();

        // See the notes on DEFAULT_TIMEOUT.
        assert DB ||
                DEFAULT_TIMEOUT > ZygoteConnectionConstants.WRAPPED_PID_TIMEOUT_MILLIS;

        //bug 956242 for monkey_sleep.sh receive signal 13
       // if(Build.PRODUCT.contains("9832e")){
            FDCHECK_INTERVAL = ActivityManager.isLowRamDeviceStatic() ? 3*60*1000 : 2*60*1000;
       // }

    }

    public void init(Context context, ActivityManagerService activity) {
        mResolver = context.getContentResolver();
        mActivity = activity;

        context.registerReceiver(new RebootRequestReceiver(),
                new IntentFilter(Intent.ACTION_REBOOT),
                android.Manifest.permission.REBOOT, null);
    }

    public void processStarted(String name, int pid) {
        synchronized (this) {
            if ("com.android.phone".equals(name)) {
                mPhonePid = pid;
            }
        }
    }

    public void setActivityController(IActivityController controller) {
        synchronized (this) {
            mController = controller;
        }
    }

    public void setAllowRestart(boolean allowRestart) {
        synchronized (this) {
            mAllowRestart = allowRestart;
        }
    }

    public void addMonitor(Monitor monitor) {
        synchronized (this) {
            if (isAlive()) {
                throw new RuntimeException("Monitors can't be added once the Watchdog is running");
            }
            mMonitorChecker.addMonitor(monitor);
        }
    }

    public void addThread(Handler thread) {
        addThread(thread, DEFAULT_TIMEOUT);
    }

    public void addThread(Handler thread, long timeoutMillis) {
        synchronized (this) {
            if (isAlive()) {
                throw new RuntimeException("Threads can't be added once the Watchdog is running");
            }
            final String name = thread.getLooper().getThread().getName();
            mHandlerCheckers.add(new HandlerChecker(thread, name, timeoutMillis));
        }
    }

    /**
     * Perform a full reboot of the system.
     */
    void rebootSystem(String reason) {
        Slog.i(TAG, "Rebooting system because: " + reason);
        IPowerManager pms = (IPowerManager)ServiceManager.getService(Context.POWER_SERVICE);
        try {
            pms.reboot(false, reason, false);
        } catch (RemoteException ex) {
        }
    }

    private int evaluateCheckerCompletionLocked() {
        int state = COMPLETED;
        for (int i=0; i<mHandlerCheckers.size(); i++) {
            HandlerChecker hc = mHandlerCheckers.get(i);
            state = Math.max(state, hc.getCompletionStateLocked());
        }
        return state;
    }

    private ArrayList<HandlerChecker> getBlockedCheckersLocked() {
        ArrayList<HandlerChecker> checkers = new ArrayList<HandlerChecker>();
        for (int i=0; i<mHandlerCheckers.size(); i++) {
            HandlerChecker hc = mHandlerCheckers.get(i);
            if (hc.isOverdueLocked()) {
                checkers.add(hc);
            }
        }
        return checkers;
    }

    private String describeCheckersLocked(List<HandlerChecker> checkers) {
        StringBuilder builder = new StringBuilder(128);
        for (int i=0; i<checkers.size(); i++) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(checkers.get(i).describeBlockedStateLocked());
        }
        return builder.toString();
    }

    private ArrayList<Integer> getInterestingHalPids() {
        try {
            IServiceManager serviceManager = IServiceManager.getService();
            ArrayList<IServiceManager.InstanceDebugInfo> dump =
                    serviceManager.debugDump();
            HashSet<Integer> pids = new HashSet<>();
            for (IServiceManager.InstanceDebugInfo info : dump) {
                if (info.pid == IServiceManager.PidConstant.NO_PID) {
                    continue;
                }

                if (!HAL_INTERFACES_OF_INTEREST.contains(info.interfaceName)) {
                    continue;
                }

                pids.add(info.pid);
            }
            return new ArrayList<Integer>(pids);
        } catch (RemoteException e) {
            return new ArrayList<Integer>();
        }
    }

    private ArrayList<Integer> getInterestingNativePids() {
        ArrayList<Integer> pids = getInterestingHalPids();

        int[] nativePids = Process.getPidsForCommands(NATIVE_STACKS_OF_INTEREST);
        if (nativePids != null) {
            pids.ensureCapacity(pids.size() + nativePids.length);
            for (int i : nativePids) {
                pids.add(i);
            }
        }

        return pids;
    }

    @Override
    public void run() {
        boolean waitedHalf = false;
        while (true) {
            final List<HandlerChecker> blockedCheckers;
            final String subject;
            final boolean allowRestart;
            int debuggerWasConnected = 0;
            //for bug718279, setProcessLimit should be called out of synchronized(this), or else
            //dead-lock may happen
            autoAdjustProcessLimit();
	    checkForAvaliableMem();
            synchronized (this) {
                long timeout = CHECK_INTERVAL;
                // Make sure we (re)spin the checkers that have become idle within
                // this wait-and-check interval
                for (int i=0; i<mHandlerCheckers.size(); i++) {
                    HandlerChecker hc = mHandlerCheckers.get(i);
                    hc.scheduleCheckLocked();
                }

                if (debuggerWasConnected > 0) {
                    debuggerWasConnected--;
                }

                // NOTE: We use uptimeMillis() here because we do not want to increment the time we
                // wait while asleep. If the device is asleep then the thing that we are waiting
                // to timeout on is asleep as well and won't have a chance to run, causing a false
                // positive on when to kill things.
                long start = SystemClock.uptimeMillis();
                while (timeout > 0) {
                    if (Debug.isDebuggerConnected()) {
                        debuggerWasConnected = 2;
                    }
                    try {
                        wait(timeout);
                    } catch (InterruptedException e) {
                        Log.wtf(TAG, e);
                    }
                    if (Debug.isDebuggerConnected()) {
                        debuggerWasConnected = 2;
                    }
                    timeout = CHECK_INTERVAL - (SystemClock.uptimeMillis() - start);
                }

                //bug683009 adds a detecting function here for FD-leak into  Watchdog
                checkFdLeakHappen();

                boolean fdLimitTriggered = false;
                if (mOpenFdMonitor != null) {
                    fdLimitTriggered = mOpenFdMonitor.monitor();
                }

                if (!fdLimitTriggered) {
                    final int waitState = evaluateCheckerCompletionLocked();
                    if (waitState == COMPLETED) {
                        // The monitors have returned; reset
                        waitedHalf = false;
                        continue;
                    } else if (waitState == WAITING) {
                        // still waiting but within their configured intervals; back off and recheck
                        continue;
                    } else if (waitState == WAITED_HALF) {
                        if (!waitedHalf) {
                            // We've waited half the deadlock-detection interval.  Pull a stack
                            // trace and wait another half.
                            ArrayList<Integer> pids = new ArrayList<Integer>();
                            pids.add(Process.myPid());
                            //bug931278 add for gms trace
                            if ( mController != null ){
                                int[] JavaAppPid = Process.getPidsForCommands(JAVA_STACKS_OF_INTEREST);
                                if (JavaAppPid != null){
                                    Slog.w(TAG, "WAITED HALF get java app stack");
                                    for (int i : JavaAppPid) {
                                        pids.add(i);
                                    }
                                }
                            }
                            //bug931278 end
                            ActivityManagerService.dumpStackTraces(true, pids, null, null,
                                getInterestingNativePids());
                            waitedHalf = true;
                            //bug931278 fix watchdog.The gms version,system_server main thread blocked by gms.persistent.
                            int[] JavaAppPid = Process.getPidsForCommands(JAVA_PROCESS_TOBE_KILL);
                            if (JavaAppPid != null){
                                for (int i : JavaAppPid) {
                                    Process.killProcess(i);
                                    Slog.e(TAG,"WAITED HALF kill Pid:"+i);
                                }
                            }
                            //bug931278 end
                        }
                        continue;
                    }

                    // something is overdue!
                    blockedCheckers = getBlockedCheckersLocked();
                    subject = describeCheckersLocked(blockedCheckers);
                } else {
                    blockedCheckers = Collections.emptyList();
                    subject = "Open FD high water mark reached";
                }
                allowRestart = mAllowRestart;
            }

            // If we got here, that means that the system is most likely hung.
            // First collect stack traces from all threads of the system process.
            // Then kill this process so that the system will restart.
            EventLog.writeEvent(EventLogTags.WATCHDOG, subject);

            // [SDBG]Print CpuInfo
            SprdRuntimeInfo.printSprdRuntimeInfo(SprdRuntimeInfo.SPRD_CPU_INFO);

            ArrayList<Integer> pids = new ArrayList<>();
            pids.add(Process.myPid());
            if (mPhonePid > 0) pids.add(mPhonePid);
            //bug762460 begin:add for monkey trace
            if ( mController != null ){
                Slog.w(TAG, "Pass (!waitedHalf), get monkey stack");
                int[] JavaAppPid = Process.getPidsForCommands(JAVA_STACKS_OF_INTEREST);
                if (JavaAppPid != null){
                    Slog.w(TAG, "WAITED HALF get java app stack");
                    for (int i : JavaAppPid) {
                        pids.add(i);
                    }
                }
            } //bug762460 end
            // Pass !waitedHalf so that just in case we somehow wind up here without having
            // dumped the halfway stacks, we properly re-initialize the trace file.
            final File stack = ActivityManagerService.dumpStackTraces(
                    !waitedHalf, pids, null, null, getInterestingNativePids());

            // Give some extra time to make sure the stack traces get written.
            // The system's been hanging for a minute, another second or two won't hurt much.
            SystemClock.sleep(2000);

            // Trigger the kernel to dump all blocked threads, and backtraces on all CPUs to the kernel log
            doSysRq('w');
            doSysRq('l');

            // Try to add the error to the dropbox, but assuming that the ActivityManager
            // itself may be deadlocked.  (which has happened, causing this statement to
            // deadlock and the watchdog as a whole to be ineffective)
            Thread dropboxThread = new Thread("watchdogWriteToDropbox") {
                    public void run() {
                        mActivity.addErrorToDropBox(
                                "watchdog", null, "system_server", null, null,
                                subject, null, stack, null);
                    }
                };
            dropboxThread.start();
            try {
                dropboxThread.join(2000);  // wait up to 2 seconds for it to return.
            } catch (InterruptedException ignored) {}

            IActivityController controller;
            synchronized (this) {
                controller = mController;
            }
            if (controller != null) {
                Slog.i(TAG, "Reporting stuck state to activity controller");
                try {
                    Binder.setDumpDisabled("Service dumps disabled due to hung system process.");
                    // 1 = keep waiting, -1 = kill system
                    int res = controller.systemNotResponding(subject);
                    if (res >= 0) {
                        Slog.i(TAG, "Activity controller requested to coninue to wait");
                        waitedHalf = false;
                        continue;
                    }
                } catch (RemoteException e) {
                }
            }

            // Only kill the process if the debugger is not attached.
            if (Debug.isDebuggerConnected()) {
                debuggerWasConnected = 2;
            }
            if (debuggerWasConnected >= 2) {
                Slog.w(TAG, "Debugger connected: Watchdog is *not* killing the system process");
            } else if (debuggerWasConnected > 0) {
                Slog.w(TAG, "Debugger was connected: Watchdog is *not* killing the system process");
            } else if (!allowRestart) {
                Slog.w(TAG, "Restart not allowed: Watchdog is *not* killing the system process");
            } else {
                Slog.w(TAG, "*** WATCHDOG KILLING SYSTEM PROCESS: " + subject);
                WatchdogDiagnostics.diagnoseCheckers(blockedCheckers);
                Slog.w(TAG, "*** GOODBYE!");
                Process.killProcess(Process.myPid());
                System.exit(10);
            }

            waitedHalf = false;
        }
    }

    private void doSysRq(char c) {
        try {
            FileWriter sysrq_trigger = new FileWriter("/proc/sysrq-trigger");
            sysrq_trigger.write(c);
            sysrq_trigger.close();
        } catch (IOException e) {
            Slog.w(TAG, "Failed to write to /proc/sysrq-trigger", e);
        }
    }

    /**
     * bug683009 adds a detecting function here for FD-leak into  Watchdog
     * here we should detect FD-leaking before the total opened FDs up to 1024. If so,
     * maybe we have not enough time to dump necessary information.
     */
    private static Boolean IS_DEBUG_BUILD = null;
    private static boolean isDebug() {
         if (IS_DEBUG_BUILD == null) {
             IS_DEBUG_BUILD = Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug");
         }
         return IS_DEBUG_BUILD;
     }

    private void checkFdLeakHappen(){
        if(isDebug()){
            long curTime = SystemClock.uptimeMillis();
            if(mLastCheckedTime!=0  &&  curTime-mLastCheckedTime < FDCHECK_INTERVAL)
                return ;
            //for bug709381. if system is very slow, checkFdLeakHappen will also be called very slowly.
            //and we can detected it by "curTime-mLastCheckedTime". if too slow, we shoud do somethings
            //to avoid being worse later.
            float curLoad1 = getCpuLoad1();
            if(mActivity != null){
                long exceedingWaitTime = 0;
                if(mLastCheckedTime != 0)
                    exceedingWaitTime = curTime-mLastCheckedTime - FDCHECK_INTERVAL;
                Log.e(TAG,"checkFdLeakHappen exceedingWaitTime=:"+exceedingWaitTime + "  load1 = " + curLoad1);

                for(int i = 0; i < mProcessLimitSum; i++){
                    if(exceedingWaitTime >= mProcessLimitThreshold[i] ||
                        curLoad1 >= mCpuLoadAvg[i]){
                        if(	mAMSProcessLimitChanged != mProcessLimitApps[i]){
                            mAMSProcessLimitToBeChanged = mProcessLimitApps[i];
                            Log.e(TAG,"checkFdLeakHappen: set ProcessLimit to "+mAMSProcessLimitToBeChanged);
                        }
                        break;
                    }
                }
            }
            //for printSprdRuntimeInfo may occupy too much cpu time, so here we recalculate checkTime.
            mLastCheckedTime = SystemClock.uptimeMillis();
            Log.e(TAG,"checkFdLeakHappen checkFdLeakHappen mLastCheckedTime=:"+mLastCheckedTime);
            //only do this check under userdebug or eng
            //sys/kernel/debug/sprd_debug/cpu/cpu_usage must exist and can be accessed in userdebug mode
            //So we can open this file, and check FD number to check if FD leak happens
            //if FD leak exists in system_server, FD number will be increased and closed to 1024.
            FileDescriptor socket = null;
            try{
                socket = Os.socket(OsConstants.AF_UNIX, OsConstants.SOCK_STREAM, 0);
                if(socket != null){
                    int curFd = socket.getInt$();
                    Os.close(socket);
                    socket = null;
                    Log.e(TAG,"checkFdLeakHappen checkFdLeakHappen fd =:"+curFd);
                    //if the current Fd number is larger than 600, we think Fd leak may hapens.
                    //for checkFdLeakHappen will be called very frequently and bugreport will dump too many data into
                    // /data/bugreports and will conumse too much time.
                    //So we we only do this bugreport when the current fd number is increased more than 50 .
                    if((curFd > FDLEAK_THRESHOLD) && ((mLastCheckedFdNumber == 0) ||(curFd-mLastCheckedFdNumber > 500))){
                        mActivity.requestBugReport(0);
                        mLastCheckedFdNumber = curFd;
                    }
                }
            }catch (ErrnoException errnoException){
                //exception may happen here when the count of FDs is upto 1024.
                Log.e(TAG,"checkFdLeakHappen failed to open cpu_usage errno=:"+errnoException.errno);
            }catch (Exception e){
            } finally{
                if(socket != null){
                    try{
                        Os.close(socket);
                        socket = null;
                        }catch (Exception e){
                        }
                }
            }
        }
    }

    /**
     * for bug718279, if call setProcessLimit in checkFdLeakHappen. Dead-lock may happend between
     * checkFdLeakHappen and setActivityController
     * so here we define a new function to be called  out of the synchronized codes in Watchdog.run
     * this function will auto adjust process limit by the mAMSProcessLimitToBeChanged determined in
     * checkFdLeakHappen
     */
    private void autoAdjustProcessLimit(){
        //for bug724023: a53only version, limit background app to 4
        if(isDebug() && Build.PRODUCT.contains("a53only")){
             if((mAMSProcessLimitToBeChanged == PROCESSLIMITAPPS_DEFAULT)||
                          (mAMSProcessLimitToBeChanged >= PROCESSLIMITAPPS_A53ONLY_DEFAULT))
                 mAMSProcessLimitToBeChanged = PROCESSLIMITAPPS_A53ONLY_DEFAULT;
            Log.e(TAG,"checkFdLeakHappen: autoAdjustProcessLimit  A53Only mAMSProcessLimitToBeChanged:"+mAMSProcessLimitToBeChanged);
        }

        //Log.e(TAG,"checkFdLeakHappen: autoAdjustProcessLimit  called old mAMSProcessLimitChanged:"+mAMSProcessLimitChanged);
        if( mActivity != null && mAMSProcessLimitToBeChanged != mAMSProcessLimitChanged){
            mActivity.setProcessLimit(mAMSProcessLimitToBeChanged);
            mAMSProcessLimitChanged = mAMSProcessLimitToBeChanged;
            Log.e(TAG,"checkFdLeakHappen: autoAdjustProcessLimit  mAMSProcessLimitChanged:"+mAMSProcessLimitChanged);
        }
    }

     /**
     * Returns the cpu loading
     */
    private float getCpuLoad1() {
        final float[] wdLoadAverages = new float[3];
        if (Process.readProcFile("/proc/loadavg", WD_LOAD_AVERAGE_FORMAT,
                null, null, wdLoadAverages)) {
            float load1 = wdLoadAverages[0];
            float load5 = wdLoadAverages[1];
            float load15 = wdLoadAverages[2];
            return load1;
        }
        return 0;
    }


  //for gms monkey performance:
     private void checkForAvaliableMem(){
        if(isDebug() && mActivity != null && mActivity.isUserAMonkeyNoCheck()){//do it only under monkey
            try{
                long curTime = SystemClock.uptimeMillis();

                //get current meminfo, and may kill some app with high priority for more memory
                if((curTime-mLastKillForMoreMemoryTime)>KILLPROCESSFORMOREMEMORY_PERIOD){
                    boolean needKillProcess = false;
                    boolean largeMemory = false;
                    MemInfoReader minfo = new MemInfoReader();
                    minfo.readMemInfo();
                    long memCanUse = (minfo.getCachedSizeKb()+minfo.getFreeSizeKb())*100/minfo.getTotalSizeKb();
                    needKillProcess = memCanUse<KILLPROCESS_THRESHOLD_PERCENT ? true:false;
                    largeMemory = minfo.getTotalSizeKb() > 1024*1024;
                    Log.e(TAG,"checkForAvaliableMem memCanUse="+memCanUse + ",needKillProcess="+needKillProcess+",largeMemory="+largeMemory);
                    if(largeMemory && needKillProcess){//ignore it for androidGO
                        //if available memory <20%, we should kill some google apps with higher priority
                        int[]  pids = Process.getPidsForCommands(PROCESS_KILL_FORMOREMEMORY);
                        if (pids != null){
                            for (int pid : pids) {
                               Process.killProcess(pid);
                               Slog.e(TAG,"checkForAvaliableMem try to kill some  apps for more memory:"+pid);
                               SystemClock.sleep(50);
                            }
                        }
                   }
                   mLastKillForMoreMemoryTime = curTime;
                }


            }catch(Exception e){
                Log.e(TAG," exception in checkForAvaliableMem"+e);
            }
         }
      }

    private native void native_dumpKernelStacks(String tracesPath);

    public static final class OpenFdMonitor {
        /**
         * Number of FDs below the soft limit that we trigger a runtime restart at. This was
         * chosen arbitrarily, but will need to be at least 6 in order to have a sufficient number
         * of FDs in reserve to complete a dump.
         */
        private static final int FD_HIGH_WATER_MARK = 12;

        private final File mDumpDir;
        private final File mFdHighWaterMark;

        public static OpenFdMonitor create() {
            // Only run the FD monitor on debuggable builds (such as userdebug and eng builds).
            if (!Build.IS_DEBUGGABLE) {
                return null;
            }

            // Don't run the FD monitor on builds that have a global ANR trace file. We're using
            // the ANR trace directory as a quick hack in order to get these traces in bugreports
            // and we wouldn't want to overwrite something important.
            final String dumpDirStr = SystemProperties.get("dalvik.vm.stack-trace-dir", "");
            if (dumpDirStr.isEmpty()) {
                return null;
            }

            final StructRlimit rlimit;
            try {
                rlimit = android.system.Os.getrlimit(OsConstants.RLIMIT_NOFILE);
            } catch (ErrnoException errno) {
                Slog.w(TAG, "Error thrown from getrlimit(RLIMIT_NOFILE)", errno);
                return null;
            }

            // The assumption we're making here is that FD numbers are allocated (more or less)
            // sequentially, which is currently (and historically) true since open is currently
            // specified to always return the lowest-numbered non-open file descriptor for the
            // current process.
            //
            // We do this to avoid having to enumerate the contents of /proc/self/fd in order to
            // count the number of descriptors open in the process.
            final File fdThreshold = new File("/proc/self/fd/" + (rlimit.rlim_cur - FD_HIGH_WATER_MARK));
            return new OpenFdMonitor(new File(dumpDirStr), fdThreshold);
        }

        OpenFdMonitor(File dumpDir, File fdThreshold) {
            mDumpDir = dumpDir;
            mFdHighWaterMark = fdThreshold;
        }

        private void dumpOpenDescriptors() {
            try {
                File dumpFile = File.createTempFile("anr_fd_", "", mDumpDir);
                java.lang.Process proc = new ProcessBuilder()
                    .command("/system/bin/lsof", "-p", String.valueOf(Process.myPid()))
                    .redirectErrorStream(true)
                    .redirectOutput(dumpFile)
                    .start();

                int returnCode = proc.waitFor();
                if (returnCode != 0) {
                    Slog.w(TAG, "Unable to dump open descriptors, lsof return code: "
                        + returnCode);
                    dumpFile.delete();
                }
            } catch (IOException | InterruptedException ex) {
                Slog.w(TAG, "Unable to dump open descriptors: " + ex);
            }
        }

        /**
         * @return {@code true} if the high water mark was breached and a dump was written,
         *     {@code false} otherwise.
         */
        public boolean monitor() {
            if (mFdHighWaterMark.exists()) {
                dumpOpenDescriptors();
                return true;
            }

            return false;
        }
    }
}
