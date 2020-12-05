package com.android.server.performance.policy.powerhint;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.PerformanceManagerNative;
import android.app.UserHabit;
import android.app.ProcessInfo;
import android.app.ProcState;
import android.content.ComponentName;
import android.util.Slog;
import android.util.Slog;
import android.os.Debug;
import android.os.Debug.MemoryInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Bundle;
import android.content.Intent;
import android.os.SystemClock;
import android.os.Process;
import android.os.PowerManager;
import android.os.PowerHALManager;
import android.os.PowerManagerInternal;
import android.os.PowerHintVendorSprd;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.UserHandle;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Slog;
import android.net.LocalSocketAddress;
import android.net.LocalSocket;
import android.hardware.power.V1_0.PowerHint;

import com.android.server.performance.PerformanceManagerService;

import com.android.server.performance.PolicyExecutor;
import com.android.server.performance.PolicyItem;
import com.android.server.performance.status.SystemStatus;
import com.android.server.am.ActivityManagerService;
import com.android.server.am.ActivityManagerServiceEx;
import com.android.server.LocalServices;

import static android.app.ProcessInfo.*;
import static com.android.server.performance.PerformanceManagerDebugConfig.*;
import static com.android.server.performance.PerformanceManagerService.*;
import static com.android.server.performance.policy.powerhint.PowerHintConstants.*;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.MemoryHandler;

/**
 * Created by SPREADTRUM\joe.yu on 7/2/18.
 */

public class PowerHintPolicyExecutor extends PolicyExecutor {
    private static String TAG = "performance";
    private static String THREAD_NAME = "PowerHintPolicyExecutor";
    private PowerHintRunnable mPowerHintRunnable = new PowerHintRunnable();
    private long LAUNCH_POWER_HINT_DURATION = SystemProperties.getLong(
            "persist.sys.powerhint.launchdur", 1000);// ms

    private ActivityManagerServiceEx mAm;
    private PerformanceManagerService mService;
    private PowerManagerInternal mLocalPowerManager;
    private PowerHintConstants mPowerHintConstants;
    private InStrumentationData mCurrentInStrumentationData;
    private int mCurrentBenchMarkHint = 0;
    private int mCurrentCpuBoostHint = 0;
    private PowerHALManager mPowerHALManager;
    private boolean mCurrentPowerHintDone = true;
    private String mCurrentFocusPackage = "";

    @Override
    public void executorPolicy(SystemStatus status) {
    }

    public PowerHintPolicyExecutor(PolicyItem config,
            PerformanceManagerService service) {
        super();
        this.mPowerHintConstants = PowerHintConstants.loadFromConfig(config
                .getConfig(PowerHintConstants.TAG));
        mService = service;
        mAm = (ActivityManagerServiceEx) ActivityManagerNative.getDefault();
        mLocalPowerManager = LocalServices
                .getService(PowerManagerInternal.class);
        mPowerHALManager = new PowerHALManager(mService.getContext(), mHandler);
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
        String pkgName = app.getPackageName();

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
                if (!mCurrentFocusPackage.equals(pkgName)) {
                    //focus changed
                    // check for benchmark
                    powerHintForBechMark(app);
                    // check for CPU boost
                    powerHintForCpuBoost(app);
                }
                mCurrentFocusPackage = pkgName;
                break;
            }
            case ACTIVITY_STATE_FINISH: {
                break;
            }
            case ACTIVITY_STATE_LAUNCHDONE: {
                break;
            }
            case ACTIVITY_STATE_PROC_START: {
                String hostingType = bundle
                        .getString(ProcessInfo.KEY_START_PROC_HOST_TYPE);
                int pid = Integer.valueOf(bundle
                        .getString(ProcessInfo.KEY_START_PROC_PID));
                if (hostingType != null && "activity".equals(hostingType)) {
                    // check hint type start proc
                    powerHintForStartProc(app);
                }
                break;
            }
            default:
                break;
        }
    }

    private class PowerHintReleaser implements Runnable {
        private int hint = 0;
        private String pkgName;

        public PowerHintReleaser(int hint, String pkgName) {
            this.hint = hint;
            this.pkgName = pkgName;
        }

        public void run() {
            mLocalPowerManager.powerHint(hint, 0);
            Slog.d(TAG, "powerHintForStartProc done for " + pkgName);
        }
    }

    private void powerHintForStartProc(ComponentName app) {
        Scenes sc = mPowerHintConstants.getPowerHintScenes(
                           SCEN_TYPE_START_PROC, app.getPackageName());

        if (sc != null) {
        if (DEBUG_SERVICE)
            Slog.d(TAG, "powerHintForStartProc,scene :"
                + sc.toString() + ",packageName:" + app.getPackageName());
            int hint = sc.hintType;
            if (hint != 0) {
                mLocalPowerManager.powerHint(hint, 1);
                PowerHintReleaser releaser = new PowerHintReleaser(hint, app.getPackageName());
                Slog.d(TAG, "powerHintForStartProc start for "+app.getPackageName());
                mHandler.postDelayed(releaser, sc.duration);
            }
        }
    }

    private void powerHintForCpuBoost(ComponentName app) {
        if (mCurrentCpuBoostHint != 0) {
            mLocalPowerManager.powerHint(mCurrentCpuBoostHint, 0);
            Slog.d(TAG,
                    "powerHintForCpuBoost done for " + app.getPackageName());
            mCurrentCpuBoostHint = 0;
        }
        int hint = mPowerHALManager.getSceneIdByName(app.getPackageName());
        if (hint != 0) {
            mLocalPowerManager.powerHint(hint, 1);
            Slog.d(TAG,
                    "powerHintForCpuBoost start for " + app.getPackageName());
            mCurrentCpuBoostHint = hint;
        }
    }

    private void powerHintForBechMark(ComponentName app) {

        if (mCurrentBenchMarkHint != 0) {
            mLocalPowerManager.powerHint(mCurrentBenchMarkHint, 0);
            Slog.d(TAG,
                    "powerHintForBechMark end for " + app.getPackageName());
            mCurrentBenchMarkHint = 0;
        }
        int hint = mPowerHintConstants.getPackageScenePowerHintType(
                SCEN_TYPE_BENCHMARK, app.getPackageName());
        if (hint != 0) {
            mLocalPowerManager.powerHint(hint, 1);
            Slog.d(TAG,
                    "powerHintForBechMark start for " + app.getPackageName());
            mCurrentBenchMarkHint = hint;
        }
    }

    private int getInStrumentationHintType(String pkgName) {
        int hint = mPowerHintConstants.getPackageScenePowerHintType(
                SCEN_TYPE_INSTRMENTATIONCTS, pkgName);
        if (hint == 0) {
            hint = mPowerHintConstants.getPackageScenePowerHintType(
                    SCEN_TYPE_INSTRMENTATIONGTS, pkgName);
        }
        if (DEBUG_SERVICE)
            Slog.d(TAG, "getInStrumentationHintType,packageName:" + pkgName + ", hint: " + hint);
        return hint;
    }

    public void sendPowerHintForLaunch(int enable){
        if (enable == 1) {
            mLocalPowerManager.powerHint(PowerHint.LAUNCH, 1);
            mCurrentPowerHintDone = false;
            mHandler.removeCallbacks(mPowerHintRunnable);
            mHandler.postDelayed(mPowerHintRunnable,
                    LAUNCH_POWER_HINT_DURATION);
            if (DEBUG_SERVICE)
                Slog.d(TAG, "sendPowerHintForLaunch on");
        } else {
            if (mHandler.hasCallbacks(mPowerHintRunnable)) {
                // duration < 1000ms let runnable run;
                if (DEBUG_SERVICE)
                    Slog.d(TAG, "sendPowerHintForLaunch off, run for :"
                            + LAUNCH_POWER_HINT_DURATION);
            } else {
                // duration >= 1000ms
                mLocalPowerManager.powerHint(PowerHint.LAUNCH, 0);
                if (DEBUG_SERVICE)
                    Slog.d(TAG, "sendPowerHintForLaunch off ,launch done");
            }
            mCurrentPowerHintDone = true;
        }
    }

    @Override
    protected void handleInStrumentationChange(Message msg) {
        if (mLocalPowerManager == null) {
            return;
        }
        InStrumentationData data = (InStrumentationData) msg.obj;
        if (data.start) {
            if (data.iInfo != null && mCurrentInStrumentationData == null) {
                int hint = getInStrumentationHintType(data.iInfo.targetPackage);
                if (hint != 0) {
                    mLocalPowerManager.powerHint(hint, 1);
                    mCurrentInStrumentationData = data;
                }
            }
        } else {
            if (mCurrentInStrumentationData != null) {
                if (data.app.packageName == mCurrentInStrumentationData.app.packageName) {
                    int hint = getInStrumentationHintType(mCurrentInStrumentationData.iInfo.targetPackage);
                    if (hint != 0) {
                        mLocalPowerManager.powerHint(hint, 0);
                        mCurrentInStrumentationData = null;
                    }
                }
            }
        }
    }

    private class PowerHintRunnable implements Runnable {
        @Override
        public void run() {
            if (mCurrentPowerHintDone) {
                // means launchTime <LAUNCH_POWER_HINT_DURATION;
                mLocalPowerManager.powerHint(PowerHint.LAUNCH, 0);
                mCurrentPowerHintDone = true;
                if (DEBUG_SERVICE)
                    Slog.d(TAG, "send powerhint off runnable");
            } else {
                // launchtime > 1000;
                // doNothing;
            }
        }
    }

    @Override
    public void dump(PrintWriter pw, String[] args) {
        pw.println("PowerHintPolicyExecutor:");
        pw.println("----------------------------");
        if (mPowerHintConstants != null) {
            mPowerHintConstants.dump(pw);
        }
        pw.println("----------------------------");
    }
}
