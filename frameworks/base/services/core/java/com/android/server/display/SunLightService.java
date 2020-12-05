/* *
 * Copyright (C) 2018 The spreadtrum.com
 */

package com.android.server.display;

import com.android.server.SystemService;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.Trace;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Slog;

import vendor.sprd.hardware.enhance.V1_0.IEnhance;
import vendor.sprd.hardware.enhance.V1_0.Status;
import vendor.sprd.hardware.enhance.V1_0.Type;

public class SunLightService extends SystemService {
    static final String TAG = "SunLightService";
    static final boolean DEBUG = false || getDebugDisplayLog();

    private IEnhance mEnhance;
    private boolean mSystemReady = false;

    public SunLightService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        publishLocalService(SunLightManager.class, mService);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_SYSTEM_SERVICES_READY) {
            mSystemReady = true;
        }
    }

    private final SunLightManager mService = new SunLightManager() {
        @Override
        public void setLight(int light) {
            setLux(light);
            if (DEBUG) Slog.d(TAG, "setLight " + light);
        }

        @Override
        public void setTable(int index) {
            setSlpMode(index);
            if (DEBUG) Slog.d(TAG, "setTable " + index);
        }
    };

    private void setLux(int lux) {
        getEnhanceService();
        try {
            if (mEnhance != null) {

                long nowElapsed = SystemClock.elapsedRealtime();

                mEnhance.setValue(Type.SLP, lux);

                long latencyMs = SystemClock.elapsedRealtime() - nowElapsed;

                if (DEBUG) Slog.e(TAG, "setLux lux=" + lux);

                if (latencyMs >= 50) {
                    Slog.w(TAG, "Excessive delay setLux: " + latencyMs+ " ms");
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "setLux failed: " + e);
        }
    }

    private void setSlpMode(int modeIndex) {
        getEnhanceService();
        try {
            if (mEnhance != null) {

                long nowElapsed = SystemClock.elapsedRealtime();

                mEnhance.setMode(Type.SLP, modeIndex);

                long latencyMs = SystemClock.elapsedRealtime() - nowElapsed;

                if (DEBUG) Slog.e(TAG, "setSlpMode mode=" + modeIndex);

                if (latencyMs >= 50) {
                    Slog.w(TAG, "Excessive delay setSlpMode: " + latencyMs+ " ms");
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "setSlpMode failed: " + e);
        }
    }

    private void getEnhanceService() {
        if (mEnhance != null && mSystemReady) return;
        try {
            mEnhance = IEnhance.getService();
        } catch (Exception e) {
            Slog.e(TAG, "getEnhanceService IEnhance Exception " + e);
            mEnhance = null;
        }
    }

    private static boolean getDebugDisplayLog() {
        String value = SystemProperties.get("persist.sys.power.fw.debug");
        StringBuilder stringBuilder = new StringBuilder(value);
        String stringBuilderNew = stringBuilder.toString();
        if (stringBuilderNew.contains("display")) {
            return true;
        }
        return false;
    }
}
