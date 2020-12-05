/*
 ** Copyright 2016 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerThread;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.SystemProperties;
import android.util.Slog;
import java.util.HashSet;
import android.util.AndroidException;
import java.util.Arrays;

import android.os.PowerHALManager;
import android.os.PowerHintVendorSprd;

public class HandlerForMemoryGts {
    private static final String TAG = "HandlerForMemoryGts";
    private static final boolean mDebug = false;

    private Context mContext;
    private PackageManager mPackageManager;
    private HandlerThread mHandlerThread;
    private Handler mHandler;

    private PowerHALManager mPowerHALManager;

    private HashSet mAppList;
    private PowerHALManager.PowerHintScene mMemoryGtsScene;
    private boolean mMemoryGtsSceneBoosted = false;

    private HashSet mAppListForDropCache;
    private PowerHALManager.PowerHintScene mMemoryGtsSceneForDropCache;

    private PowerHALManager.PowerHintScene mMemoryGtsSceneForVmFaultAround;
    private boolean mVmFaultAroundBoosted = false;

    private static final String PKG_MEMORY_GTS = "com.google.android.memory.gts";
    private int mAppStopedDelayMillis = SystemProperties.getInt("persist.sys.pwctl.appstop.delay", 60000);

    public HandlerForMemoryGts(Context context) {
        mContext = context;
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = mHandlerThread.getThreadHandler();
        mPackageManager = mContext.getPackageManager();

        mPowerHALManager = new PowerHALManager(mContext, mHandler);
        mMemoryGtsScene = mPowerHALManager.createPowerHintScene(
                "gts_memory", PowerHintVendorSprd.POWER_HINT_VENDOR_GTS_MEMORY, null);
        mMemoryGtsSceneForDropCache = mPowerHALManager.createPowerHintScene(
                "gts_memory", PowerHintVendorSprd.POWER_HINT_VENDOR_GTS_MEMORY_PSS, null);
        mMemoryGtsSceneForVmFaultAround= mPowerHALManager.createPowerHintScene(
                "gts_memory", PowerHintVendorSprd.POWER_HINT_VENDOR_GTS_VM_FAULT_AROUND, null);

        mAppList = new HashSet();
        mAppList.add("com.google.android.apps.mapslite");
        mAppList.add("com.google.android.apps.youtube.mango");

        Resources res = mContext.getResources();
        String[] appArray = res.getStringArray(com.android.internal.R.array.config_power_gts_memory_app_list);
        mAppListForDropCache = new HashSet(Arrays.asList(appArray));

        if (mDebug) {
            Slog.d(TAG, "###mAppListForDropCache >>>");
            Slog.d(TAG, "" + mAppListForDropCache);
            Slog.d(TAG, "###mAppListForDropCache <<<");
        }
    }

    public void noteAppStopped(String packageName) {
        if (mDebug) {
            Slog.d(TAG, "####noteAppStopped: " + "packageName: " + packageName);
        }
        if (packageName == null) return;

        mHandler.post(new Runnable() {
            public void run() {
                if (mMemoryGtsSceneBoosted && (mMemoryGtsScene != null) && (packageName != null)
                    && mAppList.contains(packageName)) {
                    Slog.d(TAG, "####gts_memory deboost");
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            try {
                                mMemoryGtsScene.release();
                                mMemoryGtsSceneBoosted = false;
                            } catch (Exception e) {}
                        }
                    }, mAppStopedDelayMillis);

                }
            }
        });
    }

    private boolean isPkgInstalled(String packageName) {
        PackageInfo pi = null;

        try {
            pi = mPackageManager.getPackageInfo(packageName, 0);
        } catch (AndroidException e) {
            if (mDebug) {
                Slog.d(TAG, "Don't found pkd:" + packageName);
            }
            return false;
        }

        if (pi == null) {
            return false;
        } else {
            return true;
        }
    }

    public void noteAppLaunched(String targetApp) {
        if (mDebug) {
            Slog.d(TAG, "#### targetApp: " + targetApp);
        }
        if (targetApp == null) return;

        mHandler.post(new Runnable() {
            public void run() {
                if (mVmFaultAroundBoosted && !isPkgInstalled(PKG_MEMORY_GTS) && mMemoryGtsSceneForVmFaultAround != null) {
                    mMemoryGtsSceneForVmFaultAround.release();
                    mVmFaultAroundBoosted = false;
                    return;
                }

                if (mAppList.contains(targetApp) && !mMemoryGtsSceneBoosted) {
                    if (isPkgInstalled(PKG_MEMORY_GTS) && mMemoryGtsScene != null) {
                        Slog.d(TAG, "####gts_memory boost");
                        mMemoryGtsScene.acquire();
                        mMemoryGtsSceneBoosted = true;
                    }
                }

                if (mAppListForDropCache != null && mAppListForDropCache.contains(targetApp)
                    && isPkgInstalled(PKG_MEMORY_GTS) && mMemoryGtsSceneForDropCache != null) {
                    if (!mVmFaultAroundBoosted && mMemoryGtsSceneForVmFaultAround != null) {
                        mMemoryGtsSceneForVmFaultAround.acquire();
                        mVmFaultAroundBoosted = true;
                    }

                    if (mDebug) {
                        Slog.d(TAG, "Trigger mMemoryGtsSceneForDropCache");
                    }
                    mMemoryGtsSceneForDropCache.acquire(500);
                }
            }
        });
    }

}
