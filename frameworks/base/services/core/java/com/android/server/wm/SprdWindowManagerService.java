/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.server.wm;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.Context;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.SurfaceControl;
import com.android.server.policy.WindowManagerPolicy;
import com.android.server.input.InputManagerService;
import com.android.server.wm.WindowManagerService;

import java.io.FileDescriptor;
import java.io.PrintWriter;

import static android.view.Display.DEFAULT_DISPLAY;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;


/** {@hide} */
public class SprdWindowManagerService extends WindowManagerService {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "SprdWindowManagerService" : TAG_WM;
    WMSLog mWMSLog = new WMSLog();
    private static final int MAX_WINDOW_COUNT = 100;
    private static int mDumpWinDelayTime = 1000 * 60 * 10;
    private static boolean mDumpWinDelay = false;
    private static boolean mPostDumpWinMessage = false;
    /**
     * ADD for SR
     * Google design min_scale for display size
     * Google design min dp for display size
     */
    private static final float MIN_SCALE = 0.85f;
    private static final int MIN_DIMENSION_DP = 320;
    SprdWindowManagerService(Context context, InputManagerService inputManager,
            boolean haveInputMethods, boolean showBootMsgs, boolean onlyCore,
            WindowManagerPolicy policy) {
        super(context, inputManager, haveInputMethods, showBootMsgs, onlyCore, policy);
        Slog.i(TAG, "init SprdWindowManagerService");
    }

    /* SPRD: for Super Resolution @{ */
    @Override
    public void setForcedDisplaySizeDensity(int width, int height, int density) {
        if (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS) !=
                PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Must hold permission " +
                    android.Manifest.permission.WRITE_SECURE_SETTINGS);
        }
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized(mWindowMap) {
                // Set some sort of reasonable bounds on the size of the display that we
                // will try to emulate.
                final int MIN_WIDTH = 200;
                final int MIN_HEIGHT = 200;
                final int MAX_SCALE = 2;
                final DisplayContent displayContent = mRoot.getDisplayContent(DEFAULT_DISPLAY);
                if (displayContent != null) {
                    width = Math.min(Math.max(width, MIN_WIDTH),
                            displayContent.mInitialDisplayWidth * MAX_SCALE);
                    height = Math.min(Math.max(height, MIN_HEIGHT),
                            displayContent.mInitialDisplayHeight * MAX_SCALE);
                    setForcedDisplaySizeDensityLocked(displayContent, width, height, density);
                    Settings.Global.putString(mContext.getContentResolver(),
                            Settings.Global.DISPLAY_SIZE_FORCED, width + "," + height);
                    Settings.Secure.putString(mContext.getContentResolver(),
                            Settings.Secure.DISPLAY_DENSITY_FORCED, Integer.toString(density));
                    displayContent.mSuperResDisplayDensity = density;
                    displayContent.mSuperResDisplayDensityChanged = true;
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }
    private void setForcedDisplaySizeDensityLocked(DisplayContent displayContent, int width, int height, int density) {
        Slog.i(TAG_WM, "Using new display size & density: " + width + "x" + height + " density = " + density);

        /**
         * modify for SR BUG:963855
         * DO NOT Override the display size in Settings when changed SR
         * @para displayContent.mBaseDisplayDensity the last actual density
         * @para displayContent.mInitialDisplayDensity the physical density
         * @para displayContent.mSuperResDisplayDensity the last default density
         * @para density the new default density
         */
        int minDimensionPx = Math.min(width, height);
        if (displayContent.mBaseDisplayDensity == displayContent.mInitialDisplayDensity ||
                displayContent.mBaseDisplayDensity == displayContent.mSuperResDisplayDensity) {
            displayContent.mBaseDisplayDensity = density;
        }else {
            if (displayContent.mBaseDisplayDensity - density > 0) {
                if (Math.abs(displayContent.mBaseDisplayDensity - density) >
                        Math.abs(displayContent.mSuperResDisplayDensity - density)) {
                    displayContent.mBaseDisplayDensity = DisplayMetrics.DENSITY_MEDIUM * minDimensionPx / MIN_DIMENSION_DP;;
                } else {
                    displayContent.mBaseDisplayDensity = (int) (density * MIN_SCALE);
                }
            } else {
                if (Math.abs(displayContent.mBaseDisplayDensity - density) >
                        Math.abs(displayContent.mSuperResDisplayDensity - density)) {
                    displayContent.mBaseDisplayDensity = (int) (density * MIN_SCALE);
                } else {
                    displayContent.mBaseDisplayDensity = DisplayMetrics.DENSITY_MEDIUM * minDimensionPx / MIN_DIMENSION_DP;
                }
            }
        }
        /* modify end */
        displayContent.updateBaseDisplayMetrics(width, height, displayContent.mBaseDisplayDensity);
        reconfigureDisplayForConfigChangeLocked(displayContent);
        clearAllSnapshots();
    }
    private void reconfigureDisplayForConfigChangeLocked(@NonNull DisplayContent displayContent) {
        if (!displayContent.isReady()) {
            return;
        }
        displayContent.configureDisplayPolicy();
        displayContent.setLayoutNeeded();

        final int displayId = displayContent.getDisplayId();
        boolean configChanged = updateOrientationFromAppTokensLocked(displayId);
        final Configuration currentDisplayConfig = displayContent.getConfiguration();
        mTempConfiguration.setTo(currentDisplayConfig);
        displayContent.computeScreenConfiguration(mTempConfiguration);
        configChanged |= currentDisplayConfig.diff(mTempConfiguration) != 0;

        if (configChanged) {
            mWaitingForConfig = true;
            startFreezingDisplayLocked(0 /* exitAnim */,
                    0 /* enterAnim */, displayContent);
            mH.obtainMessage(H.SEND_NEW_CONFIGURATION, displayId).sendToTarget();
        }

        mWindowPlacerLocked.performSurfacePlacement();
    }
    private void clearAllSnapshots() {
        synchronized (mWindowMap) {
            // clear all snapshots for better UX
            // if not, there will be crop or scale the first time
            // just do this as launching the first time
            mTaskSnapshotController.clearAllSnapshots();
        }
    }
    int computeInitialDisplayDensity(DisplayContent displayContent, int defaultDensity) {
        if (SystemProperties.getBoolean("ro.sprd.superresolution", false)
                && displayContent.getDisplayId() == DEFAULT_DISPLAY) {
            try {
                IBinder displayToken = SurfaceControl.getBuiltInDisplay(DEFAULT_DISPLAY);
                int activeConfig = SurfaceControl.getActiveConfig(displayToken);

                SurfaceControl.PhysicalDisplayInfo[] configs =
                            SurfaceControl.getDisplayConfigs(displayToken);
                SurfaceControl.PhysicalDisplayInfo phys = configs[activeConfig];
                // set size & density for default display
                float density = (activeConfig == 0) ? phys.density :
                                    (phys.width > 480) ? (float)phys.width/360f : (float)phys.width/320f;
                return (int)(density * 160 + 0.5f);
            } catch (Exception e) {
                Slog.e(TAG_WM, "computeInitialDisplayDensity error !");
                e.printStackTrace();
            }
            return defaultDensity;
        }
        return defaultDensity;
    }

    int currentWindowCount(){
        final int numDisp = super.mRoot.mChildren.size();
        final int[] index = new int[1];
        for (int displayNdx = 0; displayNdx < numDisp; ++displayNdx) {
            final DisplayContent displayContent = super.mRoot.mChildren.get(displayNdx);
            displayContent.forAllWindows((win) -> {
                index[0] = index[0] + 1;
            }, true);
        }
        return index[0];
    }

    void dumpCurrentWindow(){
        int currentWinCount = currentWindowCount();
        if(currentWinCount > MAX_WINDOW_COUNT){
            if(!mDumpWinDelay) {
                sprdDumpWindow(currentWinCount);
                mDumpWinDelay = true;
            } else {
                if(!mPostDumpWinMessage) {
                    mPostDumpWinMessage = true;
                    mH.postDelayed(new Runnable(){
                        @Override
                        public void run(){
                            sprdDumpWindow(currentWinCount);
                        }
                    }, mDumpWinDelayTime);
                }
            }
        }
    }

    void sprdDumpWindow(int currentWinCount){
        Slog.d(TAG,"Dump Window, Current Window count ( " + currentWinCount + " ) is more than "
                + " the max ( " + MAX_WINDOW_COUNT + " )");
        mWMSLog.dumpAllWindowStatusLocked(this);
        if(mDumpWinDelay && mPostDumpWinMessage){
            mPostDumpWinMessage = false;
        }
    }

}
