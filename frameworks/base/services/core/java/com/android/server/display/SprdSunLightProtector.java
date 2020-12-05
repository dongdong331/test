/* *
 * Copyright (C) 2018 The spreadtrum.com
 */

package com.android.server.display;

import com.android.server.EventLogTags;
import com.android.server.LocalServices;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Handler;
import android.os.sprdpower.ISceneRecognizeManagerEx;
import android.os.sprdpower.ISceneStatsNotifier;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.sprdpower.Scene;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.text.format.DateUtils;
import android.util.EventLog;
import android.util.MathUtils;
import android.util.Slog;
import android.util.Spline;
import android.util.TimeUtils;

import java.io.PrintWriter;

class SprdSunLightProtector {
    private static final String TAG = "SprdSunLightProtector";

    private static final boolean DEBUG = false || getDebugDisplayLog();


    private static final String PROPERTY_SLP_ENABLE = "persist.sys.abc.slp";


    private static final int MSG_INIT = 0;
    private static final int MSG_UPDATE_CONFIG = 1;
    private static final int MSG_UPDATE_SUN_LIGHT_PROTECT_STATE = 2;
    private static final int MSG_UPDATE_AMBIENT_LUX = 3;
    private static final int MSG_CHECK_SSENSE_SERVICE = 4;
    private static final int MSG_SCENE_CHANGED = 5;


    private boolean SYNC_SLP = SystemProperties.getBoolean("persist.sys.pq.synslp", false);

    private final Context mContext;

    // The handler
    private AHandler mHandler;

    // enabled
    private boolean mEnabled = false;

    // sun light protect is on or not
    private boolean mSunLightProtectOn = false;

    // the sun light protect function shoud enabled or not
    private boolean mShouldEnable = SystemProperties.getBoolean(PROPERTY_SLP_ENABLE, true);


    private int mLastTableId = SunLightManager.SUNLIGHT_TABLE_ID_NONE;

    // current lux
    private float mAmbientLux;

    private boolean mBatteryLevelLow = false;


    // The sensor manager.
    private final SensorManager mSensorManager;

    // The light sensor, or null if not available or needed.
    private final Sensor mLightSensor;

    // Set to true if the light sensor is enabled.
    private boolean mLightSensorEnabled;

    // Initial light sensor event rate in milliseconds.
    private final int mInitialLightSensorRate;

    // Steady-state light sensor event rate in milliseconds.
    private final int mNormalLightSensorRate;

    // The current light sensor event rate in milliseconds.
    private int mCurrentLightSensorRate;


    private SunLightManager mSunLightManager;

    private SettingsObserver mSettingsObserver;
    private int mUserId = 0;

    private boolean mScreenOn = true;


    private ISceneRecognizeManagerEx mSceneRecognizeManager;
    private int mVideoSceneId;
    private int mGameSceneId;
    private int mTryCount = 0;

    public SprdSunLightProtector(Context context, Looper looper, SensorManager sensorManager,
        int lightSensorRate, int initialLightSensorRate) {

        mContext = context;
        mSensorManager = sensorManager;
        mNormalLightSensorRate = lightSensorRate;
        mInitialLightSensorRate = initialLightSensorRate;
        mCurrentLightSensorRate = -1;

        mHandler = new AHandler(looper);


        SystemProperties.addChangeCallback(new Runnable() {
            @Override public void run() {
                    Slog.d(TAG, "SYSPROP changed");

                    boolean shouldEnable = SystemProperties.getBoolean(PROPERTY_SLP_ENABLE, true);
                    if (mShouldEnable != shouldEnable) {
                        Slog.d(TAG, "property:" + PROPERTY_SLP_ENABLE + " changed:" + shouldEnable);
                        mShouldEnable = shouldEnable;
                        mHandler.sendEmptyMessage(MSG_UPDATE_CONFIG);
                    }
            }
        });

        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        mHandler.sendEmptyMessage(MSG_INIT);
    }

    public void configure(boolean enable, boolean batteryLevelLow) {

        if (DEBUG) Slog.d(TAG, "configure: enable:" + enable + " batteryLevelLow:" + batteryLevelLow);

        boolean changed = false;
        int tableId = SunLightManager.SUNLIGHT_TABLE_ID_NONE;

        if (mSunLightProtectOn != enable) {
            mSunLightProtectOn = enable;
        }

        boolean needEnabled = false;
        if (mSunLightProtectOn && mShouldEnable) {
            needEnabled = true;
        }

        // if not sync auto brightness with slp, then will not enabled when screen off
        if (!SYNC_SLP  && !mScreenOn)
            needEnabled = false;

        if (mEnabled != needEnabled) {
            mEnabled = needEnabled;
            changed = true;
        }

        // if not sync auto brightness with slp, then enable light sensor
        if (!SYNC_SLP)
            setLightSensorEnabled(mEnabled);


        if (mBatteryLevelLow != batteryLevelLow && mEnabled) {
            mBatteryLevelLow = batteryLevelLow;
            changed = true;
        }

        tableId = getAppropriateTableIndex();
        if (mLastTableId != tableId) {
            changed = true;
        }

        if (changed) {
            //if (!mEnabled) {
                // shutdown
            //    tableId = SunLightManager.SUNLIGHT_TABLE_ID_NONE;
            //} else {
            //    tableId = mBatteryLevelLow?SunLightManager.SUNLIGHT_TABLE_ID_LOWPOWER:SunLightManager.SUNLIGHT_TABLE_ID_NORMAL;
            //}

            if (DEBUG) Slog.d(TAG, "configure: mEnabled:" + mEnabled + " mBatteryLevelLow:" + mBatteryLevelLow
                + " tableId:" + tableId);

            getSunLightManager();
            if(mSunLightManager != null)
                mSunLightManager.setTable(tableId);
            mLastTableId = tableId;
        }

    }

    public void updateLux(float lux) {
        if (!mEnabled) return;

        mAmbientLux = lux;

        if (DEBUG) Slog.d(TAG, "updateLux:" + mAmbientLux);
        getSunLightManager();
        if(mSunLightManager != null)
            mSunLightManager.setLight((int)mAmbientLux);
    }

    public void setSunLightProtectOn(boolean on, boolean batteryLevelLow) {
        mHandler.removeMessages(MSG_UPDATE_SUN_LIGHT_PROTECT_STATE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_SUN_LIGHT_PROTECT_STATE, (on?1:0),
            (batteryLevelLow?1:0)));
    }

    public void setSunLightProtectTemporayDisabled(boolean disabled) {
        boolean shouldEnable = !disabled;

        if (mShouldEnable != shouldEnable) {
            if (DEBUG) Slog.d(TAG, "setSunLightProtectTemporayDisabled: "  + disabled + " shouldEnable:" + shouldEnable);
            mShouldEnable = shouldEnable;
            mHandler.sendEmptyMessage(MSG_UPDATE_CONFIG);
        }
    }

    public void setBatteryLevelLow(boolean batteryLevelLow) {
        mHandler.removeMessages(MSG_UPDATE_SUN_LIGHT_PROTECT_STATE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_SUN_LIGHT_PROTECT_STATE, (mSunLightProtectOn?1:0),
            (batteryLevelLow?1:0)));
    }

    public void screenOff(boolean off) {
        if (mScreenOn == !off) return;

        mScreenOn = !off;
        mHandler.sendEmptyMessage(MSG_UPDATE_CONFIG);
        if (DEBUG) Slog.d(TAG, "screenOff : mScreenOn:" + mScreenOn);
    }

    public void dump(PrintWriter pw) {
        pw.println();
        pw.println("Sun Light Protector Configuration:");
        pw.println("  mEnabled=" + mEnabled);
        pw.println("  mBatteryLevelLow=" + mBatteryLevelLow);
        pw.println("  mAmbientLux=" + mAmbientLux);
    }


    private final class AHandler extends Handler {
        public AHandler(Looper looper) {
            super(looper, null, true /*async*/);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_CONFIG:
                    updateConfig();
                    break;

                case MSG_UPDATE_SUN_LIGHT_PROTECT_STATE:
                    boolean on = (msg.arg1 == 1 ? true:false);
                    boolean lowBattery = (msg.arg2 == 1 ? true:false);
                    if (DEBUG) Slog.d(TAG, "MSG_UPDATE_SUN_LIGHT_PROTECT_STATE, on:" + on + " lowBattery:" + lowBattery);
                    configure(on, lowBattery);
                    break;

                case MSG_UPDATE_AMBIENT_LUX:
                    if (DEBUG) Slog.d(TAG, "MSG_UPDATE_AMBIENT_LUX");
                    break;

                case MSG_INIT:
                    initSettings();
                    break;

                case MSG_CHECK_SSENSE_SERVICE:
                    registerSceneListener();
                    break;

                case MSG_SCENE_CHANGED:
                    handleSceneChanged();
                    break;
            }
        }
    }

    private void updateConfig() {
        if (DEBUG) Slog.d(TAG, "updateConfig: enabled:" + mEnabled + " mShouldEnable:" + mShouldEnable
            + " mSunLightProtectOn:" +mSunLightProtectOn + " mScreenOn:" + mScreenOn);

        configure(mSunLightProtectOn, mBatteryLevelLow);
    }


    private final SensorEventListener mLightSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mLightSensorEnabled) {
                final long time = SystemClock.uptimeMillis();
                final float lux = event.values[0];
                if (DEBUG){
                    Slog.i(TAG, "onSensorChanged, time = " + time + ",lux = " + lux);
                }
                handleLightSensorEvent(time, lux);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Not used.
        }
    };

    private void handleLightSensorEvent(long time, float lux) {
        updateLux(lux);
    }

    private boolean setLightSensorEnabled(boolean enable) {
        if (mSensorManager == null || mLightSensor == null) {
             Slog.i(TAG, "setLightSensorEnabled, NULL sensor!!");
             return false;
        }

        if (enable) {
            if (!mLightSensorEnabled) {
                mLightSensorEnabled = true;
                mCurrentLightSensorRate = mNormalLightSensorRate;
                mSensorManager.registerListener(mLightSensorListener, mLightSensor,
                        mCurrentLightSensorRate * 1000, mHandler);
                return true;
            }
        } else {
            if (mLightSensorEnabled) {
                mLightSensorEnabled = false;
                mCurrentLightSensorRate = -1;
                mHandler.removeMessages(MSG_UPDATE_AMBIENT_LUX);
                mSensorManager.unregisterListener(mLightSensorListener);
            }
        }
        return false;
    }


    private final class SettingsObserver extends ContentObserver {
        private final Uri NIGHT_DISPLAY_ACTIVATED
                = Secure.getUriFor (Secure.NIGHT_DISPLAY_ACTIVATED);
        private final Uri DISPLAY_COLOR_TEMPERATURE_MODE
                = System.getUriFor ("sprd_display_color_temperature_mode");

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri, @UserIdInt int userId) {
            if (userId != mUserId) return;

            try {
                final boolean bNightDisplayActived = Secure.getIntForUser(mContext.getContentResolver(),
                        Secure.NIGHT_DISPLAY_ACTIVATED, 0, mUserId) == 1;

                final boolean bColorTemperatureActived = System.getIntForUser(mContext.getContentResolver(),
                        "sprd_display_color_temperature_mode", 1, mUserId) == 1;

                if (DEBUG) Slog.d(TAG, " onChange() uri:" + uri + " selfChange: " + selfChange + " userId:" + userId);

                if (DEBUG) Slog.d(TAG, "bNightDisplayActived:" + bNightDisplayActived
                    + " bColorTemperatureActived:" + bColorTemperatureActived + " for user:" + mUserId);

                boolean enabled = (bNightDisplayActived || bColorTemperatureActived);
                configure(enabled, mBatteryLevelLow);

            } catch (Exception e) {}
        }

        public void setListening(boolean listening) {
            final ContentResolver cr = mContext.getContentResolver();
            if (listening) {
                if (NIGHT_DISPLAY_ACTIVATED != null)
                    cr.registerContentObserver(NIGHT_DISPLAY_ACTIVATED, false, this, UserHandle.USER_ALL);
                if (DISPLAY_COLOR_TEMPERATURE_MODE != null)
                    cr.registerContentObserver(DISPLAY_COLOR_TEMPERATURE_MODE, false, this, UserHandle.USER_ALL);
            } else {
                cr.unregisterContentObserver(this);
            }
        }
    }

    private void initSettings() {
        // if sync with auto brightness, then slp is controlled by auto brightness
        // so it will not need to listen system state and setting states
        if (SYNC_SLP) return;

        mSettingsObserver = new SettingsObserver(mHandler);

        // register broadcast
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                        mUserId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0);
                        if (DEBUG) Slog.d(TAG, "ACTION_USER_SWITCHED : mUserId:" + mUserId);
                        // add for bug#964841
                        checkNightDisplayConfig();
                    } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                        mScreenOn = true;
                        mHandler.sendEmptyMessage(MSG_UPDATE_CONFIG);
                        if (DEBUG) Slog.d(TAG, "ACTION_SCREEN_ON : mScreenOn:" + mScreenOn);
                    } else if(Intent.ACTION_SCREEN_OFF.equals(action)) {
                        mScreenOn = false;
                        mHandler.sendEmptyMessage(MSG_UPDATE_CONFIG);
                        if (DEBUG) Slog.d(TAG, "ACTION_SCREEN_OFF : mScreenOn:" + mScreenOn);
                    }
                }
            }, filter);

        boolean bNightDisplayActived =  Secure.getIntForUser(mContext.getContentResolver(),
                Secure.NIGHT_DISPLAY_ACTIVATED, 0, mUserId) == 1;
        boolean bColorTemperatureActived = System.getIntForUser(mContext.getContentResolver(),
                    "sprd_display_color_temperature_mode", 1, mUserId) == 1;

        if (DEBUG) Slog.d(TAG, "initSettings: bNightDisplayActived:" + bNightDisplayActived
            + " bColorTemperatureActived:" + bColorTemperatureActived);

        boolean enabled = (bNightDisplayActived || bColorTemperatureActived);
        configure(enabled, mBatteryLevelLow);

        mSettingsObserver.setListening(true);

        //registerSceneListener();
        mHandler.sendEmptyMessageDelayed(MSG_CHECK_SSENSE_SERVICE, 10000);

    }

    // add for bug#964841
    private void checkNightDisplayConfig() {

        final boolean bNightDisplayActived =  Secure.getIntForUser(mContext.getContentResolver(),
                Secure.NIGHT_DISPLAY_ACTIVATED, 0, mUserId) == 1;

        final boolean bColorTemperatureActived = System.getIntForUser(mContext.getContentResolver(),
                    "sprd_display_color_temperature_mode", 1, mUserId) == 1;

        if (DEBUG) Slog.d(TAG, "checkNightDisplayConfig: bNightDisplayActived:" + bNightDisplayActived
            + " bColorTemperatureActived:" + bColorTemperatureActived + " for user:" + mUserId);

        boolean enabled = (bNightDisplayActived || bColorTemperatureActived);
        configure(enabled, mBatteryLevelLow);
    }

    private void getSunLightManager() {
        if (mSunLightManager == null)
            mSunLightManager = LocalServices.getService(SunLightManager.class);
    }

    private void registerSceneListener() {
        if (mSceneRecognizeManager != null) return;
        mSceneRecognizeManager = ISceneRecognizeManagerEx.Stub.asInterface(ServiceManager.getService("SceneRecognize"));
        if (mSceneRecognizeManager != null) {
            Slog.d(TAG, "register Video/Game scene callback!");
            try {
                mSceneRecognizeManager.registerSceneStatsNotifier(mSceneObserver,
                    (Scene.SCENE_TYPE_VIDEO | Scene.SCENE_TYPE_GAME));
            } catch (Exception e) {
                Slog.d(TAG, "registerSceneStatsNotifier fail: " + e);
            }
        } else {
            Slog.d(TAG, "get ISceneRecognizeManagerEx fail!");
            mTryCount++;
            if (mTryCount < 5) {
                mHandler.sendEmptyMessageDelayed(MSG_CHECK_SSENSE_SERVICE, 10000);
            }
        }
    }

    final private ISceneStatsNotifier mSceneObserver = new ISceneStatsNotifier.Stub() {
        @Override
        public void onNotifySceneStats(Scene scene) {
            int sceneType = scene.getSceneType();
            boolean changed = false;
            if (sceneType == Scene.SCENE_TYPE_VIDEO) {
                if (mVideoSceneId != scene.getSceneId()) {
                    mVideoSceneId = scene.getSceneId();
                    changed = true;
                }
           } else if (sceneType == Scene.SCENE_TYPE_GAME) {
                if (mGameSceneId != scene.getSceneId()) {
                    mGameSceneId = scene.getSceneId();
                    changed = true;
                }
           }

            Slog.d(TAG, "Scene notify: mVideoSceneId:" + mVideoSceneId
                + " mGameSceneId:" + mGameSceneId + " changed:" + changed);

            if (changed) {
                mHandler.sendEmptyMessage(MSG_SCENE_CHANGED);
            }
       }
    };

    private int getAppropriateTableIndex() {
        int tableId = SunLightManager.SUNLIGHT_TABLE_ID_NONE;
       if (!mEnabled) {
            // shutdown
            tableId = SunLightManager.SUNLIGHT_TABLE_ID_NONE;
       } else {
            tableId = mBatteryLevelLow?SunLightManager.SUNLIGHT_TABLE_ID_LOWPOWER:SunLightManager.SUNLIGHT_TABLE_ID_NORMAL;
            //if (tableId == SunLightManager.SUNLIGHT_TABLE_ID_NORMAL) {
                if (mGameSceneId == Scene.SCENE_ID_GAME_START) {
                    tableId = (tableId | SunLightManager.SUNLIGHT_TABLE_ID_GAME);
                } else if (mVideoSceneId == Scene.SCENE_ID_VIDEO_START
                    || mVideoSceneId == Scene.SCENE_ID_VIDEO_START_HFULL
                    || mVideoSceneId == Scene.SCENE_ID_VIDEO_START_VFULL
                    || mVideoSceneId == Scene.SCENE_ID_VIDEO_START_HFULL_MATCH) {
                    tableId = (tableId | SunLightManager.SUNLIGHT_TABLE_ID_VIDEO);
                    if (mVideoSceneId == Scene.SCENE_ID_VIDEO_START_HFULL
                        || mVideoSceneId == Scene.SCENE_ID_VIDEO_START_HFULL_MATCH)
                        tableId = (tableId | SunLightManager.SUNLIGHT_TABLE_ID_VIDEO_FULL);
                } else {
                    tableId = (tableId | SunLightManager.SUNLIGHT_TABLE_ID_UI);
                }
            //}
        }
        return tableId;
    }

    private void handleSceneChanged() {
        if (DEBUG) Slog.d(TAG, "handleSceneChanged: enabled:" + mEnabled + " mShouldEnable:" + mShouldEnable
            + " mSunLightProtectOn:" +mSunLightProtectOn + " mScreenOn:" + mScreenOn);

        configure(mSunLightProtectOn, mBatteryLevelLow);
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
