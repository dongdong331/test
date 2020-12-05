/*
 ** Copyright 2018 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.AlarmManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageEvents.Event;

import android.annotation.NonNull;

import android.content.Context;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.sprdpower.ISceneRecognizeManagerEx;
import android.os.sprdpower.ISceneStatsNotifier;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.ParcelFormatException;
import android.os.Parcelable;
import android.os.ServiceManager;
import android.os.sprdpower.Scene;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;

import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.MathUtils;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.SparseArray;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Calendar;

import android.os.BundleData;
import android.os.sprdpower.Util;

public class AppStateTracker {

    static final String TAG = "SSense.StateTracker";

    private final boolean DEBUG_LOG = Util.getDebugLog();
    private final boolean DEBUG = Util.isDebug() || DEBUG_LOG;

    static AppStateTracker sInstance;

    public static final int APP_PREDICT_STATE_ACTIVE = (0);
    public static final int APP_PREDICT_STATE_WORKING_SET = (10);
    public static final int APP_PREDICT_STATE_FREQUENT = (20);
    public static final int APP_PREDICT_STATE_RARE = (30);

    private static final long DEFAULT_ACTIVE_TIMEOUT = (30*60*1000); // 30mins

    static final String ALARM_TAG = "AppStateTracker";


    private final boolean USE_HMM = SystemProperties.getBoolean("persist.sys.ss.hmm", true);


    private final Context mContext;

    private Handler mHandler;

    private long mLastTimeChecked;

    private final SparseArray<ArrayMap<String,AppUsageState>> mAppUsageListForUsers = new SparseArray<>(); //<pkgName, userId>

    private final Object mLock = new Object();

    final ArraySet<Listener> mListeners = new ArraySet<>();

    private SmartSenseService mSmartSenseService;

    private AppStatePredict mAppStatePredict;
    private AppStateHMMPredict mAppStateHMMPredict;

    private AlarmManager mAlarmManager;
    private ISceneRecognizeManagerEx mSceneRecognizeManager;

    // this is controller by "Battery Manager" in the settings -> Battery
    private boolean mAutoRestrictionEnabled;

    private boolean mGoogleSmartBatteryEnabled;

    private static class AppUsageState {
        public String mPackageName;
        public int mUid;

        // elapsed time
        public long mLastTimeUsed;

        public long mTotalTimeInForeground;

        public int mLaunchCount;

        public int mLastEvent;

        public int mCurrentState;
        public int mPredictState;
        // public boolean mCurrentStateChanged;
        public long mActiveStateTimeout;

        public boolean mHasAudio;

        public AppUsageState(String packageName, int uid) {
            mPackageName = packageName;
            mUid = uid;
            mLastTimeUsed = 0;
            mTotalTimeInForeground = 0;
            mLaunchCount = 0;
            mLastEvent = 0;
            mCurrentState = APP_PREDICT_STATE_ACTIVE;
            mPredictState = APP_PREDICT_STATE_ACTIVE;
            mActiveStateTimeout = 0;
            mHasAudio = false;
        }
    }

    public static AppStateTracker getInstance(Context context) {
        synchronized (AppStateTracker.class) {
            if (sInstance == null) {
                sInstance = new AppStateTracker(context);
            }
            return sInstance;
        }
    }


    public AppStateTracker(Context context) {
        mContext = context;
        mHandler = new H(SmartSenseService.BackgroundThread.get().getLooper());

        mAppStatePredict = AppStatePredict.getInstance(mContext);

        if (USE_HMM) {
            mAppStateHMMPredict = AppStateHMMPredict.getInstance(mContext);
        }
    }

    public void systemReady() {
        mAlarmManager = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);

        mAutoRestrictionEnabled = isAutoRestrictionEnabled();
        mGoogleSmartBatteryEnabled = isGoogleSmartBatterySupported();

        if (USE_HMM) {
            mAppStateHMMPredict.forceAppIdleEnabled(!mGoogleSmartBatteryEnabled);
            mAppStateHMMPredict.enableApplyStandbyBucket(isApplyStandbyBucketEnabled());
            mAppStateHMMPredict.systemReady();
        } else {
            mAppStatePredict.forceAppIdleEnabled(!mGoogleSmartBatteryEnabled);
            mAppStatePredict.enableApplyStandbyBucket(isApplyStandbyBucketEnabled());
            mAppStatePredict.systemReady();
        }

        mLastTimeChecked = SystemClock.elapsedRealtime();
        //mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_CHECK_APPSTATE), (30*60*1000));

        mSceneRecognizeManager = ISceneRecognizeManagerEx.Stub.asInterface(ServiceManager.getService("SceneRecognize"));
        if (mSceneRecognizeManager != null) {
            if (DEBUG) Slog.d(TAG, "register Audio scene callback!");
            try {
                mSceneRecognizeManager.registerSceneStatsNotifier(mSceneObserver, Scene.SCENE_TYPE_AUDIO);
            } catch (Exception e) {
                Slog.d(TAG, "registerSceneStatsNotifier fail: " + e);
            }
        } else {
            Slog.d(TAG, "get ISceneRecognizeManagerEx fail!");
        }

        registerSettingsObserver();
    }

    public void setSenseService(SmartSenseService ss) {
        mSmartSenseService = ss;

        if (USE_HMM) {
            mAppStateHMMPredict.setSenseService(ss);
            mAppStateHMMPredict.startHMM();
            mAppStateHMMPredict.startPeriodCheck();
        } else {
            mAppStatePredict.setSenseService(ss);
        }
    }

    public void reportData(BundleData data) {

        switch (data.getType()) {
            case BundleData.DATA_TYPE_APP_STATE_EVENT:
                int stateEvent = data.getIntExtra(BundleData.DATA_EXTRA_APP_STATE_EVENT, 0);
                int uid = data.getIntExtra(BundleData.DATA_EXTRA_UID, -1);
                String packName = data.getStringExtra(BundleData.DATA_EXTRA_PACKAGENAME);

                mHandler.sendMessage(mHandler.obtainMessage(MSG_APP_STATE_CHANGED, uid, stateEvent, packName));

                //reportUsage(packName, uid, stateEvent);
                break;
        }
    }

    public void reportEvent(int event) {

    }

    void reportUsage(String packageName, int uid, int eventType) {

       //if(mSmartSenseService != null && !mSmartSenseService.isInstalledApp(packageName, 0)) return;

        AppUsageState appUsageState = getOrCreateAppUsageState(packageName, uid);
        if (appUsageState == null) return;

        long now = SystemClock.elapsedRealtime();

        if (eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                eventType == UsageEvents.Event.END_OF_DAY) {
            if (appUsageState.mLastEvent == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    appUsageState.mLastEvent == UsageEvents.Event.CONTINUE_PREVIOUS_DAY) {
                appUsageState.mTotalTimeInForeground += now - appUsageState.mLastTimeUsed;
                if (appUsageState.mLastTimeUsed == 0) {
                    Slog.w(TAG, "reportUsage:"+ " add foreground time but mLastTimeUsed is 0!!");
                }
            }
        }

        boolean stateChanged = false;
        if (isStatefulEvent(eventType)) {
            appUsageState.mLastEvent = eventType;
            stateChanged = true;
        }

        if (eventType != UsageEvents.Event.SYSTEM_INTERACTION) {
            appUsageState.mLastTimeUsed = now;
        }

        if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            appUsageState.mLaunchCount += 1;
        }

        // update current state
        if (stateChanged && (appUsageState.mLastEvent == UsageEvents.Event.MOVE_TO_FOREGROUND
                || appUsageState.mLastEvent == UsageEvents.Event.MOVE_TO_BACKGROUND)) {

            if (appUsageState.mCurrentState != APP_PREDICT_STATE_ACTIVE) {
                appUsageState.mCurrentState = APP_PREDICT_STATE_ACTIVE;

                Slog.d(TAG, "Notify new state for " + appUsageState.mPackageName
                    + " is " + getStateString(appUsageState.mCurrentState));

                mHandler.sendMessage(mHandler.obtainMessage(MSG_INFORM_LISTENERS,
                    appUsageState.mUid, appUsageState.mCurrentState, appUsageState.mPackageName));
            }

            appUsageState.mActiveStateTimeout = now + DEFAULT_ACTIVE_TIMEOUT;
        }

        if (now - mLastTimeChecked >= 30 *1000) {
            mLastTimeChecked = now;
            cancelAlarm();
            mHandler.removeMessages(MSG_CHECK_APPSTATE);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_CHECK_APPSTATE));
        }

    }


    public void setAppPredictState(String packageName, int uid, int predictState) {

        AppUsageState appUsageState = getOrCreateAppUsageState(packageName, uid);
        if (appUsageState == null) return;

        appUsageState.mPredictState = predictState;
    }

    public int getAppState(String packageName, int uid) {
        AppUsageState appUsageState = getOrCreateAppUsageState(packageName, uid);
        if (appUsageState == null) return APP_PREDICT_STATE_ACTIVE;
        return appUsageState.mCurrentState;
    }

    public boolean isIdle(String packageName, int uid) {
        AppUsageState appUsageState = getOrCreateAppUsageState(packageName, uid);
        if (appUsageState == null) return false;

        if (appUsageState.mCurrentState >= APP_PREDICT_STATE_WORKING_SET ) {
            return true;
        }
        return false;
    }

    /**
     * Register a new listener.
     */
    public void addListener(@NonNull Listener listener) {
        synchronized (mLock) {
            mListeners.add(listener);
        }
    }

    public static abstract class Listener {
        public void onAppUsageStateChanged(String packageName, int uid, int state) {
        }
        public void blockAppNetwork(String packageName, int uid) {
        }
        public void unblockAppNetwork(String packageName, int uid) {
        }
    }


    public final class LocalService  {
        public void registerListener(Listener listener) {
            addListener(listener);
        }

    }


    //Message define
    static final int MSG_INIT = 0;
    static final int MSG_CHECK_APPSTATE = 1;
    static final int MSG_INFORM_LISTENERS = 2;
    static final int MSG_AUDIO_SCENE_UPDATED = 3;
    static final int MSG_APP_STATE_CHANGED = 4;
    static final int MSG_AUTO_RESTRICTION_CHANGED = 5;

    private class H extends Handler {
        public H(Looper looper) {
            super(looper);
        }

        String msg2Str(int msg) {
            final String msgStr[] = {"MSG_INIT",
                "MSG_CHECK_APPSTATE",
                "MSG_INFORM_LISTENERS",
                "MSG_AUDIO_SCENE_UPDATED",
                "MSG_APP_STATE_CHANGED",
                "MSG_AUTO_RESTRICTION_CHANGED",
            };

            if ((0 <= msg) && (msg < msgStr.length))
                return msgStr[msg];
            else
                return "Unknown message: " + msg;
        }

        @Override
        public void handleMessage(Message msg) {
            if (DEBUG_LOG) Slog.d(TAG, "handleMessage(" + msg2Str(msg.what) + ")");
            switch (msg.what) {
                case MSG_CHECK_APPSTATE:
                    checkAppState();
                    break;
                case MSG_INFORM_LISTENERS:
                    inforListeners((String)msg.obj, msg.arg1, msg.arg2);
                    break;

                case MSG_AUDIO_SCENE_UPDATED:
                    handleAudioSceneChanged((Scene)msg.obj);
                    break;

                case MSG_APP_STATE_CHANGED:
                    reportUsage((String)msg.obj, msg.arg1, msg.arg2);
                    break;

                case MSG_AUTO_RESTRICTION_CHANGED:
                    handleAutoRestrictionChanged();
                    break;
                default:
                    break;
            }
        }
    }


    private boolean isStatefulEvent(int eventType) {
        switch (eventType) {
            case UsageEvents.Event.MOVE_TO_FOREGROUND:
            case UsageEvents.Event.MOVE_TO_BACKGROUND:
            case UsageEvents.Event.END_OF_DAY:
            case UsageEvents.Event.CONTINUE_PREVIOUS_DAY:
                return true;
        }
        return false;
    }

    //private boolean testtt = false;
    private void checkAppState() {

        mHandler.removeMessages(MSG_CHECK_APPSTATE);
        scheduleAlarm((30*60*1000));

        if (!USE_HMM) {
            mAppStatePredict.checkAppState();
        }

        ArrayList<AppUsageState> changedAppPredictList = new ArrayList<>();

        for (int index=mAppUsageListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, AppUsageState> appUsageList = mAppUsageListForUsers.valueAt(index);
            for (int i=0;i<appUsageList.size();i++) {
                AppUsageState appUsageState =  appUsageList.valueAt(i);
                if (appUsageState == null) continue;
                if (checkAndUpdateState(appUsageState)) {
                    changedAppPredictList.add(appUsageState);
                }
            }
        }

        //Slog.d(TAG, "Notify property changed!");
        //testtt = !testtt;
        //SystemProperties.set("persist.sys.pwctl.bgc.usage", (testtt?"1":"0"));
        //SystemProperties.reportSyspropChanged();

        // notify
        for (int i=0;i<changedAppPredictList.size();i++) {
            AppUsageState predict =  changedAppPredictList.get(i);
            Slog.d(TAG, "Notify new state for " + predict.mPackageName + " is " + getStateString(predict.mCurrentState));
            inforListeners(predict.mPackageName, predict.mUid, predict.mCurrentState);
        }
    }

    // return true for mCurrentState changed
    private boolean checkAndUpdateState(AppUsageState appUsageState) {
        if (appUsageState == null) return false;

        long now = SystemClock.elapsedRealtime();
        int newState = appUsageState.mPredictState;
        boolean updated = false;

        if (newState > APP_PREDICT_STATE_ACTIVE
                && appUsageState.mActiveStateTimeout > now) {
            newState = APP_PREDICT_STATE_ACTIVE;

            if (DEBUG) {
                Slog.d(TAG, "    Keeping at ACTIVE due to min timeout for " + appUsageState.mPackageName);
            }
        }

        if (newState > APP_PREDICT_STATE_ACTIVE
            && appUsageState.mLastEvent == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            newState = APP_PREDICT_STATE_ACTIVE;
            if (DEBUG) {
                Slog.d(TAG, "    Keeping at ACTIVE due to MOVE_TO_FOREGROUND for " + appUsageState.mPackageName);
            }
        }

        if (newState > APP_PREDICT_STATE_ACTIVE
            && appUsageState.mHasAudio) {
            if (DEBUG) {
                Slog.d(TAG, "    Keeping at ACTIVE due to Playing Audio for " + appUsageState.mPackageName);
            }
            appUsageState.mActiveStateTimeout = now + DEFAULT_ACTIVE_TIMEOUT;
        }

        if (newState != appUsageState.mCurrentState) {
            updated = true;

            Slog.d(TAG, "State for " + appUsageState.mPackageName
                + " change from " + getStateString(appUsageState.mCurrentState)
               +  " to " + getStateString(newState));
            appUsageState.mCurrentState = newState;
        }

        return updated;
    }

    private final AlarmManager.OnAlarmListener mNextCheckListener = new AlarmManager.OnAlarmListener() {
        @Override
        public void onAlarm() {
            if (DEBUG) Slog.d(TAG, "on alarm check!");
            checkAppState();
        }
    };

    private void scheduleAlarm(long delay) {
        if (mAlarmManager == null) return;
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME,
            (SystemClock.elapsedRealtime()) + delay, ALARM_TAG, mNextCheckListener, mHandler);
    }

    private void cancelAlarm() {
        if (mAlarmManager == null) return;
        mAlarmManager.cancel(mNextCheckListener);
    }

    private Listener[] cloneListeners() {
        synchronized (mLock) {
            return mListeners.toArray(new Listener[mListeners.size()]);
        }
    }
    private void inforListeners(String packageName, int uid, int state) {
        synchronized (mLock) {
            for (Listener l : mListeners) {
                l.onAppUsageStateChanged(packageName, uid, state);
            }
        }
    }

    final private ISceneStatsNotifier mSceneObserver = new ISceneStatsNotifier.Stub() {
        @Override
        public void onNotifySceneStats(Scene scene) {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_AUDIO_SCENE_UPDATED, scene));
        }
    };


    private void handleAudioSceneChanged(Scene scene) {
        if (DEBUG_LOG) Slog.d(TAG, "handleAudioSceneChanged: scene " + scene);

        if (scene == null
            || scene.getSceneType() != Scene.SCENE_TYPE_AUDIO
            || scene.getSceneExtras() == null) return;

        boolean hasAudio = false;
        // if audio start
        int sceneId = scene.getSceneId();
        if (sceneId == Scene.SCENE_ID_AUDIO_START
            || sceneId == Scene.SCENE_ID_AUDIO_IN_START
            || sceneId == Scene.SCENE_ID_AUDIO_OUT_START) {
            hasAudio = true;
       }

        int uid = scene.getSceneExtras().getInt(Scene.EXTRA_UID);
        String packName = scene.getSceneExtras().getString(Scene.EXTRA_PACKAGENAME);

        if (DEBUG_LOG) Slog.d(TAG, "handleAudioSceneChanged: app " + packName + " has audio:" + hasAudio);

        AppUsageState appUsageState = getOrCreateAppUsageState(packName, uid);

        if (appUsageState == null) return;

        // update current state
        if (!appUsageState.mHasAudio && hasAudio) {
            long now = SystemClock.elapsedRealtime();

            if (appUsageState.mCurrentState != APP_PREDICT_STATE_ACTIVE) {
                appUsageState.mCurrentState = APP_PREDICT_STATE_ACTIVE;

                Slog.d(TAG, "Notify new state for " + appUsageState.mPackageName
                    + " is " + getStateString(appUsageState.mCurrentState));

                mHandler.sendMessage(mHandler.obtainMessage(MSG_INFORM_LISTENERS,
                    appUsageState.mUid, appUsageState.mCurrentState, appUsageState.mPackageName));
            }

            appUsageState.mActiveStateTimeout = now + DEFAULT_ACTIVE_TIMEOUT;
        }

        appUsageState.mHasAudio = hasAudio;
    }

    private String getStateString(int state) {
        switch (state) {
            case APP_PREDICT_STATE_ACTIVE:
                return "ACTIVE";
            case APP_PREDICT_STATE_WORKING_SET:
                return "WORKING_SET";
            case APP_PREDICT_STATE_FREQUENT:
                return "FREQUENT";
            case APP_PREDICT_STATE_RARE:
                return "RARE";
            default:
                return "UN-KNOWN";
        }
    }

    private boolean isGoogleSmartBatterySupported() {
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_smart_battery_available);
    }

    private boolean isAutoRestrictionEnabled() {
        final boolean runtimeFlag = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.APP_AUTO_RESTRICTION_ENABLED, 1) == 1;
        return runtimeFlag;
    }

    private boolean isApplyStandbyBucketEnabled() {
        return !mGoogleSmartBatteryEnabled && mAutoRestrictionEnabled;
    }

    private void handleAutoRestrictionChanged() {
        if (mGoogleSmartBatteryEnabled) return;

        boolean autoRestrictionEnabled = isAutoRestrictionEnabled();
        if (mAutoRestrictionEnabled != autoRestrictionEnabled) {
            mAutoRestrictionEnabled = autoRestrictionEnabled;
            if (DEBUG) Slog.d(TAG, "Auto restriction is changed:" + mAutoRestrictionEnabled);

            if (USE_HMM) {
                mAppStateHMMPredict.enableApplyStandbyBucket(isApplyStandbyBucketEnabled());
            } else {
                mAppStatePredict.enableApplyStandbyBucket(isApplyStandbyBucketEnabled());
            }
        }
    }

    private final class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (mHandler == null) return;

            mHandler.removeMessages(MSG_AUTO_RESTRICTION_CHANGED);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_AUTO_RESTRICTION_CHANGED));
        }
    }

    private SettingsObserver mSettingsObserver;
    private void registerSettingsObserver() {
        if (mGoogleSmartBatteryEnabled) {
            if (DEBUG) Slog.d(TAG, "Google smart battery is supported!");
            return;
        }
        mSettingsObserver = new SettingsObserver(mHandler);
        mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor(
            Settings.Global.APP_AUTO_RESTRICTION_ENABLED), false, mSettingsObserver);
    }


    /**
     * Gets the AppUsageState object for the given package, or creates one and adds it internally.
     */
    private AppUsageState getOrCreateAppUsageState(String packageName, int uid) {
        if (packageName == null) return null;
        ArrayMap<String, AppUsageState> appUsageList = getAppUsageList(UserHandle.getUserId(uid));
        AppUsageState appUsageState = appUsageList.get(packageName);
        if (appUsageState == null) {
            appUsageState = new AppUsageState(packageName, uid);
            appUsageList.put(appUsageState.mPackageName, appUsageState);
            if (DEBUG) Slog.d(TAG, "new AppUsageState for " + appUsageState.mPackageName);
        }
        return appUsageState;
    }


    private ArrayMap<String,AppUsageState> getAppUsageList(int userId) {
        ArrayMap<String, AppUsageState> appUsageList = mAppUsageListForUsers.get(userId);
        if (appUsageList == null) {
            appUsageList = new ArrayMap<>();
            mAppUsageListForUsers.put(userId, appUsageList);
        }
        return appUsageList;
    }
}
