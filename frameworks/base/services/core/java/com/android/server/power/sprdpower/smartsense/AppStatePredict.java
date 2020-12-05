/*
 ** Copyright 2016 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlarmManager;
import android.app.AppGlobals;
import android.app.INotificationManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.sprdpower.IPowerGuru;
import android.app.IActivityManager;
import android.app.IUidObserver;
import android.app.usage.IUsageStatsManager;
import android.app.usage.UsageEvents.Event;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.app.usage.UsageStatsManagerInternal;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.sprdpower.AppPowerSaveConfig;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IDeviceIdleController;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.sprdpower.PowerManagerEx;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.service.wallpaper.WallpaperService;
import android.speech.RecognizerIntent;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.SparseArray;
import android.view.inputmethod.InputMethodInfo;

import com.android.internal.view.IInputMethodManager;
import com.android.internal.util.ArrayUtils;
import com.android.server.LocalServices;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.Calendar;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.os.sprdpower.Util;

public class AppStatePredict {

    private static final String TAG = "SSense.AppStatePredict";

    private final boolean DEBUG_LOG = Util.getDebugLog();
    private final boolean DEBUG = Util.isDebug() || DEBUG_LOG;
    private final boolean DEBUG_MORE = false;
    private final boolean TEST = PowerController.TEST;

    private static final boolean ENABLE_RARE = true;

    // use for debug, force to call bucket api
    private final boolean FORCE_PREDICT = SystemProperties.getBoolean("persist.sys.ss.predict", false);

    private static final long DELAYED_INIT_TIME = (10*1000);

    private static final int THRESHOLD_ACTIVE = (2); // 2hours
    private static final int THRESHOLD_WORKING_SET = (4); // 6hours
    private static final int THRESHOLD_FREQUENT = (6); // hours


    private static final boolean USE_SIMILARITY = false;

    private static final long LASTUSED_THRESHOLD1 = (24*60*60*1000); //ms
    private static final long LASTUSED_THRESHOLD2 = (60*60*1000); //ms
    private static final long LASTUSED_THRESHOLD3 = (30*60*1000); //ms
    private static final int NEXTUSED_THRESHOLD1 = (1); //hour
    private static final int NEXTUSED_THRESHOLD2 = (2); //hour

    private static final int PREDICT_SET_INTERVAL = (1*60*60*1000); //ms, 1hour

    public static final int APP_PREDICT_STATE_ACTIVE = AppStateTracker.APP_PREDICT_STATE_ACTIVE;
    public static final int APP_PREDICT_STATE_WORKING_SET = AppStateTracker.APP_PREDICT_STATE_WORKING_SET;
    public static final int APP_PREDICT_STATE_FREQUENT = AppStateTracker.APP_PREDICT_STATE_FREQUENT;
    public static final int APP_PREDICT_STATE_RARE = AppStateTracker.APP_PREDICT_STATE_RARE;

    static final String ALARM_TAG = "AppStatePredict";

    static AppStatePredict sInstance;


    private final Context mContext;

    private Handler mHandler;

    private  UsageStatsManagerInternal mUsageStatsInternal;

    private AppUsageStatsCollection mAppUsageStatsCollection;

    private ArrayList<AppUsageStatsHistory> mHistoryAppUsageStatsList;

    private ArrayList<AppPredictState> mAppPredictList = new ArrayList<>();

    private AppStateTracker mAppStateTracker;
    private SmartSenseService mSmartSenseService;

    // for -1, is invalid value, for 0 is non-gms version, for 1 is gms version
    private int mIsGmsVersion = -1;

    private long mLastPredictTime = 0;

    // if call the mUsageStatsInternal.setAppStandbyBucket
    private boolean mEnableApplyStandbyBucket = false;

    // if call mUsageStatsInternal.forceAppIdleEnabled(true);
    private boolean mForceAppIdleEnabled = false;

    private static class AppPredictState {
        public String mPackageName;
        public int mUid;
        public int mState;
        public boolean mStateChanged;

        public AppPredictState(String packageName, int uid, int state) {
            mPackageName = packageName;
            mUid = uid;
            mState = state;
            mStateChanged = true;
        }
    }


    public static AppStatePredict getInstance(Context context) {
        synchronized (AppStatePredict.class) {
            if (sInstance == null) {
                sInstance = new AppStatePredict(context);
            }
            return sInstance;
        }
    }

    public AppStatePredict(Context context) {
        mContext = context;
        mHandler = new H(SmartSenseService.BackgroundThread.get().getLooper());
    }


    void systemReady() {

        mAppStateTracker = LocalServices.getService(AppStateTracker.class);
        mUsageStatsInternal = LocalServices.getService(UsageStatsManagerInternal.class);

        mAppUsageStatsCollection = AppUsageStatsCollection.getInstance();
        mHistoryAppUsageStatsList = mAppUsageStatsCollection.getHistoryAppUsageList();
        if (mHistoryAppUsageStatsList == null) {
            if (DEBUG) Slog.d(TAG, "NULL mHistoryAppUsageStatsList");
        } else {
            if (DEBUG_MORE) {
                for (int i = 0; i < mHistoryAppUsageStatsList.size(); i++) {
                    Slog.d(TAG, "HistoryAppUsageStats: " + i + ":" + mHistoryAppUsageStatsList.get(i));
                }
            }
        }

        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_INIT), DELAYED_INIT_TIME);
    }

    void setSenseService(SmartSenseService ss) {
        mSmartSenseService = ss;
    }

    void startPeriodCheck() {
        mAppUsageStatsCollection.registerAppUsageObserver(new AppUsageObserver(30*1000));
    }

    void enableApplyStandbyBucket(boolean enabled) {
        if (DEBUG) Slog.d(TAG, "enableApplyStandbyBucket:" + enabled);
        mEnableApplyStandbyBucket = enabled;
    }

    void forceAppIdleEnabled(boolean enabled) {
        if (DEBUG) Slog.d(TAG, "forceAppIdleEnabled:" + enabled);
        mForceAppIdleEnabled = enabled;
    }

    //Message define
    static final int MSG_INIT = 0;
    static final int MSG_CHECK_APPSTATE = 1;

    private class H extends Handler {
        public H(Looper looper) {
            super(looper);
        }

        String msg2Str(int msg) {
            final String msgStr[] = {"MSG_INIT",
                "MSG_CHECK_APPSTATE",
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
                case MSG_INIT:
                    handleInit();
                    break;
                case MSG_CHECK_APPSTATE:
                    handleCheckAppState();
                    break;
                default:
                    break;
            }
        }
    }


    private final AlarmManager.OnAlarmListener mNextCheckListener = new AlarmManager.OnAlarmListener() {
        @Override
        public void onAlarm() {
            if (DEBUG) Slog.d(TAG, "on alarm check!");
            handleCheckAppState();
        }
    };

    private AlarmManager mAlarmManager;

    private void getAlarmManager(){
        if (mAlarmManager == null) {
            mAlarmManager = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        }
    }
    private void scheduleAlarm(long delay) {
        getAlarmManager();
        if (mAlarmManager != null) {
            mAlarmManager.set(AlarmManager.ELAPSED_REALTIME,
                (SystemClock.elapsedRealtime()) + delay, ALARM_TAG, mNextCheckListener, mHandler);
        }
    }

    private void cancelAlarm() {
        getAlarmManager();
        if (mAlarmManager != null) {
            mAlarmManager.cancel(mNextCheckListener);
        }
    }

    private void handleCheckAppState() {
        mHandler.removeMessages(MSG_CHECK_APPSTATE);
        scheduleAlarm(30*60*1000);
        checkAppState();
    }

    private class AppUsageObserver extends AppUsageStatsCollection.AppUsageObserver {
        public AppUsageObserver(long duration) {
            timeDuration = duration;
            notifyFlag = (FLAG_NOTIFY_ONLY_TIMEOUT|FLAG_NOTIFY_ONLY_DAILY_UPDATED);
        }

        @Override
        public void onTimeOut() {
            cancelAlarm();
            mHandler.removeMessages(MSG_CHECK_APPSTATE);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_CHECK_APPSTATE));
        }

        @Override
        public void onDailyUpdated() {
            mHistoryAppUsageStatsList = mAppUsageStatsCollection.getHistoryAppUsageList();
            cancelAlarm();
            mHandler.removeMessages(MSG_CHECK_APPSTATE);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_CHECK_APPSTATE));
        }
    }

    public void checkAppState() {
        try {
            ArrayList<AppUsageStatsHistory> currentAppUsageStatsList = mAppUsageStatsCollection.getCurrentAppUsageList();

            if (currentAppUsageStatsList != null) {
                for (int i = 0; i < currentAppUsageStatsList.size(); i++) {
                    if (DEBUG_MORE) Slog.d(TAG, "currentAppUsageStats: " + i + ":" + currentAppUsageStatsList.get(i));
                }
            }

            if (currentAppUsageStatsList != null) {
                int currentTimeSlot = getCurrentTimeSlot();
                long now = System.currentTimeMillis(); //wall time
                long nowElapsed = SystemClock.elapsedRealtime();
                for (int i = 0; i < currentAppUsageStatsList.size(); i++) {
                    AppUsageStatsHistory currentUsage = currentAppUsageStatsList.get(i);
                    if (currentUsage.mPackageName == null
                        || getPackageUid(currentUsage.mPackageName) <= Process.FIRST_APPLICATION_UID
                        /*|| !mSmartSenseService.isInstalledApp(currentUsage.mPackageName, 0)*/) continue;


                    if (USE_SIMILARITY && mHistoryAppUsageStatsList != null) {
                        predictUsingSimilarity(currentUsage, currentTimeSlot, nowElapsed);
                    } else if (!USE_SIMILARITY) {
                        predictUsingTimeStamp(currentUsage, now, nowElapsed);
                    }
                }

                if (mLastPredictTime == 0 || (nowElapsed - mLastPredictTime) >= PREDICT_SET_INTERVAL) {
                    mLastPredictTime = SystemClock.elapsedRealtime();
                }

                if (DEBUG) Slog.d(TAG, "mLastPredictTime: " + mLastPredictTime);

            }
        } catch (Exception e) {
             Slog.w(TAG, "checkAppState Exception: " + e);
        }

        // notify
        //for (int i=0;i<mAppPredictList.size();i++) {
        //    AppPredictState predict =  mAppPredictList.get(i);
        //    if (predict.mStateChanged) {
        //        Slog.d(TAG, "Notify new state for " + predict.mPackageName + " is " + getStateString(predict.mState));
        //        predict.mStateChanged = false;
        //    }
        //}
    }

    private void predictUsingSimilarity(AppUsageStatsHistory currentUsage, int currentTimeSlot, long nowElapsed) {

        float similarity = 0.0f;
        for (int j = 0; j < mHistoryAppUsageStatsList.size(); j++) {
            AppUsageStatsHistory historyUsage = mHistoryAppUsageStatsList.get(j);

            if (currentUsage.mPackageName.equals(historyUsage.mPackageName)) {
                similarity = getSimilarity(getClampVector(currentUsage, currentTimeSlot), getClampVector(historyUsage, currentTimeSlot));
                if (DEBUG) Slog.d(TAG, "similarity for: " + currentUsage.mPackageName + ":" + similarity);
                break;
            }
        }

        if (similarity <= 0.5) {
            if (DEBUG) Slog.d(TAG, "no similarity for " + currentUsage.mPackageName);
        } else {
            int nextInterval = mAppUsageStatsCollection.getNextFavoriteTimeInterval(currentUsage.mPackageName);
            if (DEBUG) Slog.d(TAG, "nextInterval for " + currentUsage.mPackageName + " is " + nextInterval);
            int newState = APP_PREDICT_STATE_ACTIVE;
            if (enabledRareBucket() && nextInterval > THRESHOLD_FREQUENT) {
                newState = APP_PREDICT_STATE_RARE;
            } else if (nextInterval > THRESHOLD_WORKING_SET) {
                newState = APP_PREDICT_STATE_FREQUENT;
            } else if (nextInterval > THRESHOLD_ACTIVE) {
                newState = APP_PREDICT_STATE_WORKING_SET;
            }

            boolean found = false;
            for (int index=0;index<mAppPredictList.size();index++) {
                AppPredictState predict =  mAppPredictList.get(index);
                if (currentUsage.mPackageName.equals(predict.mPackageName)) {
                    found = true;
                    if (predict.mState != newState) {
                        if (DEBUG) Slog.d(TAG, "State for " + currentUsage.mPackageName + " change from " + getStateString(predict.mState)
                           +  " to " + getStateString(newState));

                        mAppStateTracker.setAppPredictState(currentUsage.mPackageName, 0, newState);

                        if (mUsageStatsInternal != null && (FORCE_PREDICT || needApplyStandbyBucket()))
                            mUsageStatsInternal.setAppStandbyBucket(currentUsage.mPackageName, getBucket(newState), 0, "ssense");

                        predict.mState = newState;
                        predict.mStateChanged = true;
                    } else {
                        if (DEBUG) Slog.d(TAG, "mLastPredictTime: " + mLastPredictTime + " nowElapsed " + nowElapsed +
                            " (nowElapsed - mLastPredictTime) > PREDICT_SET_INTERVAL:" + ((nowElapsed - mLastPredictTime) > PREDICT_SET_INTERVAL));

                        if ((nowElapsed - mLastPredictTime) >= PREDICT_SET_INTERVAL && mLastPredictTime > 0) {
                            if (DEBUG) Slog.d(TAG, "State for " + currentUsage.mPackageName + " is " + getStateString(newState));
                            if (mUsageStatsInternal != null && (FORCE_PREDICT || needApplyStandbyBucket()))
                                mUsageStatsInternal.setAppStandbyBucket(currentUsage.mPackageName, getBucket(newState), 0, "ssense");
                        }
                    }
                }
            }

            if (!found && newState != APP_PREDICT_STATE_ACTIVE) {
                if (DEBUG) Slog.d(TAG, "New State for " + currentUsage.mPackageName + " is " + getStateString(newState));
                mAppStateTracker.setAppPredictState(currentUsage.mPackageName, 0, newState);
                if (mUsageStatsInternal != null && (FORCE_PREDICT || needApplyStandbyBucket()))
                    mUsageStatsInternal.setAppStandbyBucket(currentUsage.mPackageName, getBucket(newState), 0, "ssense");

                mAppPredictList.add(new AppPredictState(currentUsage.mPackageName, 0, newState));
            }
        }
    }

    private void predictUsingTimeStamp(AppUsageStatsHistory currentUsage, long now, long nowElapsed) {
        long lastTimeUsed = mAppUsageStatsCollection.getLastUserUsedTimeForApp(currentUsage.mPackageName);
        int intervalForNextUsed = mAppUsageStatsCollection.getNextUsedTimeInterval(currentUsage.mPackageName);
        final long timeElapsedSinceLastUsed = now - lastTimeUsed;

        if (DEBUG) Slog.d(TAG, "nextInterval: "  + intervalForNextUsed
            + ", lastTimeUsed:" + lastTimeUsed
            + " , now:" + now + ", timeElapsedSinceLastUsed:" + timeElapsedSinceLastUsed
            + " for " + currentUsage.mPackageName);

        int newState = APP_PREDICT_STATE_ACTIVE;
        if (timeElapsedSinceLastUsed < LASTUSED_THRESHOLD3) {
            if (intervalForNextUsed < 0) {
                newState = APP_PREDICT_STATE_WORKING_SET;
            } else if (intervalForNextUsed <= NEXTUSED_THRESHOLD1) {
                newState = APP_PREDICT_STATE_ACTIVE;
            } else if (intervalForNextUsed <= NEXTUSED_THRESHOLD2) {
                newState = APP_PREDICT_STATE_WORKING_SET;
            } else {
                // use APP_PREDICT_STATE_FREQUENT instead of APP_PREDICT_STATE_RARE in native version
                newState = enabledRareBucket() ? APP_PREDICT_STATE_RARE: APP_PREDICT_STATE_FREQUENT;
            }
        } else if (timeElapsedSinceLastUsed < LASTUSED_THRESHOLD2) {
            if (intervalForNextUsed < 0) {
                newState = APP_PREDICT_STATE_FREQUENT;
            } else if (intervalForNextUsed <= NEXTUSED_THRESHOLD1) {
                newState = APP_PREDICT_STATE_ACTIVE;
            } else if (intervalForNextUsed <= NEXTUSED_THRESHOLD2) {
                newState = APP_PREDICT_STATE_WORKING_SET;
            } else {
                // use APP_PREDICT_STATE_FREQUENT instead of APP_PREDICT_STATE_RARE in native version
                newState = enabledRareBucket() ? APP_PREDICT_STATE_RARE:APP_PREDICT_STATE_FREQUENT;
            }
        } else if (timeElapsedSinceLastUsed < LASTUSED_THRESHOLD1) {
            if (intervalForNextUsed < 0) {
                newState = APP_PREDICT_STATE_RARE;
            } else if (intervalForNextUsed <= NEXTUSED_THRESHOLD1) {
                newState = APP_PREDICT_STATE_WORKING_SET;
            } else if (intervalForNextUsed <= NEXTUSED_THRESHOLD2) {
                newState = APP_PREDICT_STATE_FREQUENT;
            } else {
                // use APP_PREDICT_STATE_FREQUENT instead of APP_PREDICT_STATE_RARE in native version
                newState = enabledRareBucket() ? APP_PREDICT_STATE_RARE:APP_PREDICT_STATE_FREQUENT;
            }
        } else {
            // use APP_PREDICT_STATE_FREQUENT instead of APP_PREDICT_STATE_RARE in native version
            newState = enabledRareBucket() ? APP_PREDICT_STATE_RARE:APP_PREDICT_STATE_FREQUENT;
        }

        boolean found = false;
        for (int index=0;index<mAppPredictList.size();index++) {
            AppPredictState predict =  mAppPredictList.get(index);
            if (currentUsage.mPackageName.equals(predict.mPackageName)) {
                found = true;
                if (predict.mState != newState) {
                    if (DEBUG) Slog.d(TAG, "State for " + currentUsage.mPackageName + " change from " + getStateString(predict.mState)
                       +  " to " + getStateString(newState));

                    mAppStateTracker.setAppPredictState(currentUsage.mPackageName, 0, newState);

                    if (mUsageStatsInternal != null && (FORCE_PREDICT || needApplyStandbyBucket()))
                        mUsageStatsInternal.setAppStandbyBucket(currentUsage.mPackageName, getBucket(newState), 0, "ssense");

                    predict.mState = newState;
                    predict.mStateChanged = true;
                } else {
                    if (DEBUG) Slog.d(TAG, "mLastPredictTime: " + mLastPredictTime + " nowElapsed " + nowElapsed +
                        " (nowElapsed - mLastPredictTime) > PREDICT_SET_INTERVAL:" + ((nowElapsed - mLastPredictTime) > PREDICT_SET_INTERVAL));
                    if ((nowElapsed - mLastPredictTime) >= PREDICT_SET_INTERVAL && mLastPredictTime > 0) {
                        if (DEBUG) Slog.d(TAG, "State for " + currentUsage.mPackageName + " is " + getStateString(newState));
                        if (mUsageStatsInternal != null && (FORCE_PREDICT || needApplyStandbyBucket())) {
                            mUsageStatsInternal.setAppStandbyBucket(currentUsage.mPackageName, getBucket(newState), 0, "ssense");
                            if (DEBUG) Slog.d(TAG, "call UsageStatsInternal.setAppStandbyBucket() !! ");
                        }
                    }
                }
            }
        }

        if (!found && newState != APP_PREDICT_STATE_ACTIVE) {
            if (DEBUG) Slog.d(TAG, "New State for " + currentUsage.mPackageName + " is " + getStateString(newState));
            mAppStateTracker.setAppPredictState(currentUsage.mPackageName, 0, newState);
            if (mUsageStatsInternal != null && (FORCE_PREDICT || needApplyStandbyBucket())) {
                mUsageStatsInternal.setAppStandbyBucket(currentUsage.mPackageName, getBucket(newState), 0, "ssense");
                if (DEBUG) Slog.d(TAG, "call UsageStatsInternal.setAppStandbyBucket() !! ");
            }

            mAppPredictList.add(new AppPredictState(currentUsage.mPackageName, 0, newState));
        }
    }

    private int getPackageUid(String packageName) {
        if (packageName == null) return -1;
        int uid = -1;
        try {
            uid = AppGlobals.getPackageManager().getPackageUid(packageName, 0, 0);
        } catch (Exception e) {
            Slog.w(TAG, "getPackageUid for " + packageName + " Exception:" + e);
        }
        return uid;
    }

    // using Cosine similarity
    private float getSimilarity(final int[] v1, final int[] v2) {

        if (v1 == null || v1.length == 0 || v2 == null || v2.length == 0) {
            return 0;
        }

        try {
            final int n = v1.length > v2.length ? v2.length : v1.length;
            double v1v2 = 0.0;
            double v1v1 = 0.0;
            double v2v2 = 0.0;

            for (int i = 0; i < n; i++) {
                if (DEBUG_MORE) Slog.d(TAG, "v1[" + i + "]" + v1[i] + ": v2[" + i + "]" + v2[i]);

                v1v2 += (long)v1[i]*v2[i];
                v1v1 += (long)v1[i]*v1[i];
                v2v2 += (long)v2[i]*v2[i];
                //Slog.d(TAG, "v2v2:" + v2v2 + " v2[i]*v2[i]:" + (long)v2[i]*v2[i]);
            }

           if (DEBUG_MORE) Slog.d(TAG, "v1v2:" + v1v2 + " v1v1:" + v1v1 + " v2v2:" + v2v2 + " Math.sqrt(v1v1):" + Math.sqrt(v1v1)
            + " Math.sqrt(v2v2):" + Math.sqrt(v2v2));

            float similar = (float) (v1v2 / (Math.sqrt(v1v1) * Math.sqrt(v2v2)));

            Slog.d(TAG, "similar: " + similar);

            return similar;
        } catch (Exception ex) {
            Slog.e(TAG, "Could not get similarity.", ex);
            return 0.0f;
        }

    }

    private static int clamp(long value, int div) {
        if (div == 0) div=1;
        int ret = (int)(value/(div*1000*60));

        return (ret > 0 ? ret : 1);
    }

    private static int[] getClampVector(AppUsageStatsHistory usage, int size) {
        if (usage == null) return null;

        final int n = ((size > 0 && size <= usage.mBucketCount) ? size : usage.mBucketCount);
        int[] v = new int[n];
        for (int i = 0; i < n; i++) {
            v[i] = clamp(usage.mTimeInForeground[i], usage.mDayCount);
        }
        return v;
    }

    private int getCurrentTimeSlot() {
        long currentTimeMillis = System.currentTimeMillis(); //wall time

        return TimeUtil.getTimeSlot(currentTimeMillis);
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

    private int getBucket(int state) {
        switch (state) {
            case APP_PREDICT_STATE_ACTIVE:
                return UsageStatsManager.STANDBY_BUCKET_ACTIVE;
            case APP_PREDICT_STATE_WORKING_SET:
                return UsageStatsManager.STANDBY_BUCKET_WORKING_SET;
            case APP_PREDICT_STATE_FREQUENT:
                return UsageStatsManager.STANDBY_BUCKET_FREQUENT;
            case APP_PREDICT_STATE_RARE:
                return UsageStatsManager.STANDBY_BUCKET_RARE;
            default:
                return UsageStatsManager.STANDBY_BUCKET_ACTIVE;
        }
    }

    private boolean needApplyStandbyBucket() {
        return mEnableApplyStandbyBucket;
    }

    private boolean enabledRareBucket() {
        return (ENABLE_RARE && isGmsVersion());
    }

    private boolean isGmsVersion() {
        if (mIsGmsVersion != -1)
            return (mIsGmsVersion == 1);

        int gms = SystemProperties.getInt("persist.sys.gms", -1);
        String buildExtraInfo = SystemProperties.get("ro.sprd.extrainfo");

        if (gms == 1 || (buildExtraInfo != null && buildExtraInfo.contains("gms"))) {
            mIsGmsVersion = 1;
        } else if (gms != -1) {
            mIsGmsVersion = 0;
        }
        return (mIsGmsVersion == 1);
    }

    private void handleInit() {
        if (mUsageStatsInternal != null && (FORCE_PREDICT || mForceAppIdleEnabled))
            mUsageStatsInternal.forceAppIdleEnabled(true);
    }

}
