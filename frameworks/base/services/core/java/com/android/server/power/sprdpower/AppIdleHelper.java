/*
 ** Copyright 2016 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.sprdpower.AppPowerSaveConfig;
import android.os.BatteryManager;
import android.os.BatteryStats;
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
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.SparseArray;

import com.android.internal.app.IBatteryStats;
import com.android.internal.os.BatteryStatsHelper;
import com.android.server.LocalServices;
import com.android.server.am.BatteryStatsService;
import com.android.server.NetworkManagementInternal;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.Calendar;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.os.sprdpower.Util;

// Class AppIdleHelper to make decision of What apps should be put
// into AppIdle state
public class AppIdleHelper extends PowerSaveHelper{

    private static final String TAG = "PowerController.AppIdle";

    private final boolean DEBUG_LOG = Util.getDebugLog();
    private final boolean DEBUG = isDebug() || DEBUG_LOG;
    private final boolean DEBUG_MORE = false;
    private final boolean TEST = PowerController.TEST;


    private final boolean mPowerControllerAppIdleEnabled =  (1 == SystemProperties.getInt(PowerController.POWER_CONTROLLER_APPSTANDBY_ENABLE, 1));

    private final boolean mEnabledOnlySaveMode =  (1 == SystemProperties.getInt(PowerController.PWCTL_ENABLE_ONLY_SAVEMODE, 0));


    private final long DEFAULT_APPSTANDBY_TIMEOUT =  (TEST ? 5 * 60 * 1000L : 20 * 60 * 1000L);

    //Max time in app bg state before to be added to appstandby list
    private long APPSTANDBY_TIMEOUT =  DEFAULT_APPSTANDBY_TIMEOUT;

    //Max time in app bg state before to be added to appstandby list when standby duration > APPSTANDBY_TIMEOUT
    private long APPSTANDBY_TIMEOUT2 =  (4 * 60 * 1000L); // 4min < CHECK period

    // If stay in appstandby beyond this timeout, we will let it in parole
    private long APPSTANDBY_PAROLE_TIMEOUT = (TEST ? 60 * 60 * 1000L : 60 * 60 * 1000L);


    // Max time in app bg state before to be added to appstandby list again
    // When this app exit app standby by UsageStatsService during standby period
    // Should not greater than APPSTANDBY_TIMEOUT
    private long APPSTANDBY_SECOND_TIMEOUT =  ( 5 * 60 * 1000L);

    private final IActivityManager mActivityManager;
    private Handler mHandler;


    //Reference to services
    private final UsageStatsManagerInternal mUsageStatsInternal;
    private NetworkManagementInternal mNmi;

    private boolean mEnableDnsRestrict = false;
    private int mCurrentRestrictAppCount = 0;

    // to recode the system elapsed time when starting standby
    private long mStandbyStartTime = 0;

    long mNextParoleOnTime = 0;
    boolean mNeedUpdateNextParoleOnTime = false;

    private List<String> mWhiteList;
    private List<String> mBlackList;

    // Applist waiting to add to AppStandby
    //private ArrayMap<String, Integer> mNewStandbyList = new ArrayMap<>();
    // Applist waiting to add to AppStandby for all users and all packages  <pkgName, userId>
    private SparseArray<ArrayMap<String,Integer>> mNewStandbyListForUsers = new SparseArray<>();

    private static final int DEFAULT_POWER_MODE = PowerManagerEx.MODE_SMART;
    private int mPowerSaveMode = DEFAULT_POWER_MODE;

    // This white list is used for some app like CTS
    private final String[] mInternalWhiteAppList = new String[] {
        "android.app.cts",
        "com.android.cts",
        "android.icu.dev.test.util"
    };

    private final Object mLock = new Object();
    private final ArrayList<Listener> mListeners = new ArrayList<>();

    public AppIdleHelper(Context context, IActivityManager activityManager, Handler handler) {

        super(context, AppPowerSaveConfig.MASK_NETWORK);

        mActivityManager = activityManager;
        mHandler = new InternalHandler(handler.getLooper());

        mUsageStatsInternal = LocalServices.getService(UsageStatsManagerInternal.class);

        // white & black list
        mWhiteList = mPowerControllerInternal.getWhiteList(AppPowerSaveConfig.ConfigType.TYPE_NETWORK.value);
        mBlackList = mPowerControllerInternal.getBlackList(AppPowerSaveConfig.ConfigType.TYPE_NETWORK.value);

        mNmi = LocalServices.getService(NetworkManagementInternal.class);

        // set constants
        APPSTANDBY_TIMEOUT = mConstants.APPIDLE_INACTIVE_TIMEOUT;
        APPSTANDBY_TIMEOUT2 = mConstants.APPIDLE_PAROLE_TIMEOUT;
        APPSTANDBY_PAROLE_TIMEOUT = mConstants.APPIDLE_IDLE_TIMEOUT;
        if (DEBUG) Slog.d(TAG, "APPSTANDBY_TIMEOUT:" + APPSTANDBY_TIMEOUT
            + " APPSTANDBY_TIMEOUT2:" + APPSTANDBY_TIMEOUT2
            + " APPSTANDBY_PAROLE_TIMEOUT:" + APPSTANDBY_PAROLE_TIMEOUT);
    }


    //Message define
    static final int MSG_APP_IDLE_STATE_CHANGED = 0;


    class InternalHandler extends Handler {
        InternalHandler(Looper looper) {
            super(looper);
        }

        String Msg2Str(int msg) {
            final String msgStr[] = {"MSG_APP_IDLE_STATE_CHANGED"
            };

            if ((0 <= msg) && (msg < msgStr.length))
                return msgStr[msg];
            else
                return "Unknown message: " + msg;
        }

        @Override public void handleMessage(Message msg) {
            if (DEBUG) Slog.d(TAG, "handleMessage(" + Msg2Str(msg.what) + ")");

            switch (msg.what) {
            case MSG_APP_IDLE_STATE_CHANGED:
                handleAppIdleStateChanged((String)msg.obj, msg.arg1, msg.arg2);
                break;
            }
        }
    }

    @Override
    void onPowerSaveModeChanged(int mode) {
        if (DEBUG) Slog.d(TAG, "Current PowerSaveMode:" + mode);

        mPowerSaveMode = mode;

        switch (mode) {
            case PowerManagerEx.MODE_LOWPOWER:
            case PowerManagerEx.MODE_POWERSAVING:
            case PowerManagerEx.MODE_ULTRASAVING:
                APPSTANDBY_TIMEOUT = mConstants.APPIDLE_INACTIVE_TIMEOUT_LOWPOWER; // 5min
                break;
            case PowerManagerEx.MODE_PERFORMANCE:
            case PowerManagerEx.MODE_SMART:
                APPSTANDBY_TIMEOUT = mConstants.APPIDLE_INACTIVE_TIMEOUT; // 20min
                break;
        }
    }


    @Override
    boolean updateAppEvaluatedTimeStamp(AppState appState) {
        boolean bConstrained = canBeConstrained(appState);
        return updateAppEvaluatedTimeStamp(appState, !bConstrained);
    }

    @Override
    boolean updateAppEvaluatedTimeStamp(AppState appState, boolean clear) {
        if (clear) {
            appState.mBeginEvaluatedTimeStamp = 0;
            if (DEBUG_MORE) Slog.d(TAG, "upateAppStandbyEvaluatedTimeStamp:" + appState.mPackageName + " clear beginEvaluatedTimeStamp");
        } else if (appState.mBeginEvaluatedTimeStamp == 0) {
            appState.mBeginEvaluatedTimeStamp = SystemClock.elapsedRealtime();
            if (DEBUG_MORE) Slog.d(TAG, "upateAppStandbyEvaluatedTimeStamp:" + appState.mPackageName + " beginEvaluatedTimeStamp:"
                + appState.mBeginEvaluatedTimeStamp);
        }

        return true;
    }

    /*
     * App cannot be constrained for App Idle:
     * 1. system app (app.uid <= Process.FIRST_APPLICATION_UID, this is enough ???)
     * 2. message app
     * 3. non-message app, but doing Download
     * 4. music app and is playing music
     * 5. unknown app type, and its procState <= PROCESS_STATE_BOUND_FOREGROUND_SERVICE
     * 6. app in Doze white list
     * 7. Carrier App
     */
    private boolean canBeConstrained(AppState appState) {
        int procState = appState.mProcState;

        if (DEBUG_MORE) Slog.d(TAG, "appStandbyCanBeConstrained: uid:" + appState.mUid
            + " :" + appState.mPackageName + " procState:" + Util.ProcState2Str(procState)
            + " Group State:" + Util.AppState2Str(appState.mState) + " mFCMorGCMForAppIdle:"
            + appState.mFCMorGCMForAppIdle);

        // system app can not be constrained
        if (appState.mUid <= Process.FIRST_APPLICATION_UID)
            return false;

        if(appState.mFCMorGCMForAppIdle) {
            if (DEBUG) Slog.d(TAG, "appStandbyCanBeConstrained: " + appState.mPackageName + " a GCM App");
            if (!mPowerControllerInternal.disableNetworkRestrictForMessageApp())
                appState.mFCMorGCMForAppIdle = false;
            return false;
        }

        if (inWhiteAppList(appState.mPackageName, appState.mUid)) {
            if (DEBUG) Slog.d(TAG, "appStandbyCanBeConstrained:" +appState.mPackageName +" in my whitelist");
            return false;
        } else if (mBlackList.contains(appState.mPackageName)) {
            if (DEBUG) Slog.d(TAG, "appStandbyCanBeConstrained:" +appState.mPackageName +" in my blacklist");
            return true;
        }

        // message App can not be constrained
        int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);
        if (PowerDataBaseControl.MESSAGE == appType) {
            if (DEBUG) Slog.d(TAG, "appStandbyCanBeConstrained: " + appState.mPackageName + " Message App");

            return false;
        }

        // playing music App can not be constrained
        if (PowerDataBaseControl.MUSIC == appType
            /*&& procState <= ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE*/) {
            if (mPowerControllerInternal.isPlayingMusic(appState)) {
                if (DEBUG) Slog.d(TAG, "appStandbyCanBeConstrained: " + appState.mPackageName + " Music is playing");

                return false;
            }
        }

        if (PowerDataBaseControl.UNKNOWN != appType
            && procState <= ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE
            && appState.mLaunchCount > 0) {
            if (mPowerControllerInternal.isPlayingMusic(appState)) {
                if (DEBUG) Slog.d(TAG, "appStandbyCanBeConstrained: " + appState.mPackageName + " Music is playing");

                return false;
            }
        }

        // doing download App can not be constrained
        if (procState <= ActivityManager.PROCESS_STATE_SERVICE) {
            if (mPowerControllerInternal.isAppDoingDownload(appState)) {
                if (DEBUG) Slog.d(TAG, "appStandbyCanBeConstrained: " + appState.mPackageName + " Doing Download");

                return false;
            }
        }

        // app type is unknown
        // and procState <= ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE
        // can not be constrained
        if (PowerDataBaseControl.UNKNOWN == appType
            && procState <= ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE
            && appState.mLaunchCount > 0
            && !mPowerControllerInternal.isAdminApp(appState.mPackageName)) {
            if (DEBUG) Slog.d(TAG, "appStandbyCanBeConstrained: " + appState.mPackageName + " UNKNOWN App type");

            return false;
        }


        // Carrier App can not be constrained
        if (isCarrierApp(appState.mPackageName)) {
            if (DEBUG) Slog.d(TAG, "appStandbyCanBeConstrained: " + appState.mPackageName + " a Carrier App");
            return false;
        }

        return true;
    }


    /*
     * To check if the app has been in App Idle state for a long time (APPSTANDBY_PAROLE_TIMEOUT)
     * if so, exit from App Standby state
     */
    void checkAppParole (AppState appState, final long nowELAPSED) {
        boolean paroleOn = false;

        if (DEBUG_MORE) Slog.d(TAG, "nowELAPSED:" + nowELAPSED + " mNextParoleOnTime:" + mNextParoleOnTime);

        // use the unified ParoleOn schedule time to control all the standby app
        // period is standby for APPSTANDBY_PAROLE_TIMEOUT and parole for APPSTANDBY_TIMEOUT
        if (nowELAPSED > mNextParoleOnTime) {
            paroleOn = true;
            mNeedUpdateNextParoleOnTime = true;
        }

        long standbyTime = nowELAPSED - appState.mBeginStanbyTimeStamp;
        if (appState.mInAppStandby && paroleOn && (appState.mMinForcedStanbyDuration < standbyTime)) {
            if (DEBUG) Slog.d(TAG, "packageName:" + appState.mPackageName + " Standby long enough, exit standby");

            try {
                setAppInactive(appState.mPackageName, false, appState.mUserId);
                appState.updateAppStandbyState(false);

                // re-update app standby Evaluated time
                updateAppEvaluatedTimeStamp(appState, true);
                updateAppEvaluatedTimeStamp(appState, !canBeConstrained(appState));
            } catch (Exception e) {
                // fall through
            }
        }

    }

    /*
     * check this app should be constrained or not
     * return true if this app should be constrained, and is add to mNewStandbyList
     * others return false
     */
    @Override
    boolean checkAppStateInfo(AppState appState, final long nowELAPSED) {
        ArrayMap<String, Integer> mNewStandbyList = getNewStandbyList(appState.mUserId);

        boolean bChanged = false;
        if (canBeConstrained(appState)) {
            if (!appState.mInAppStandby
                && (idleTimeConstraintSatisfied(appState, nowELAPSED)
                       || inBlackAppList(appState.mPackageName))
            ) {
                bChanged = true;
                mNewStandbyList.put(appState.mPackageName, appState.mUserId);
            }
        } else {
        // check if procState changed, and became can not be constrainted
        // if it already in app standby state, then update it.
            // reset the time stamp
            updateAppEvaluatedTimeStamp(appState, true);

            if (appState.mInAppStandby) {
                if (DEBUG) Slog.d(TAG, "packageName:" + appState.mPackageName + " canBeConstrained is not satisfied. Exit from standby State");

                try {
                    setAppInactive(appState.mPackageName, false, appState.mUserId);
                    appState.updateAppStandbyState(false);
                } catch (Exception e) {
                // fall through
                }
            }

            // if already put it in mNewStandbyList, just remove
            mNewStandbyList.remove(appState.mPackageName);
        }

        return bChanged;
    }

    // set the apps to be in App Idle state
    @Override
    void applyConstrain() {
        if (mStandbyStartTime <= 0) {
            if (DEBUG) Slog.d(TAG, " system becomes non-standby before applying constrain!");
            clearConstrainAppList();
            return;
        }

        for (int index=mNewStandbyListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, Integer> mNewStandbyList = mNewStandbyListForUsers.valueAt(index);

            for (int i=0;i<mNewStandbyList.size();i++) {
                try {
                    if (DEBUG) Slog.d(TAG, "packageName:" + mNewStandbyList.keyAt(i)
                        + " userId:" + mNewStandbyList.valueAt(i) + " enter standby");
                    int userId = mNewStandbyList.valueAt(i);
                    setAppInactive(mNewStandbyList.keyAt(i), true, userId);
                    updateAppStandbyState(mNewStandbyList.keyAt(i), true, userId);

                    mCurrentRestrictAppCount++;
                } catch (Exception e) {
                    // fall through
                }
            }
            mNewStandbyList.clear();

        }

        // add for bug#899925
        long now = SystemClock.elapsedRealtime();
        if (mCurrentRestrictAppCount > 0
            && (now - mStandbyStartTime) > DEFAULT_APPSTANDBY_TIMEOUT
            && mStandbyStartTime > 0) {
            if (!mEnableDnsRestrict && mNmi != null) {
                try {
                    mNmi.setFirewallDnsDenyEnabled(true);
                } catch (Exception e) {}
                mEnableDnsRestrict = true;
            }
        }
    }

    // set the app to exit from App Idle state
    @Override
    void clearConstrain(AppState appState) {
        if (appState.mInAppStandby) {
            setAppInactive(appState.mPackageName, false, appState.mUserId);
            appState.updateAppStandbyState(false);
        }
        // Clear Time Stamp
        updateAppEvaluatedTimeStamp(appState, true);
    }

    @Override
    void onAppUsageStateChanged(String packName, int uid, int state) {
        if (DEBUG) Slog.d(TAG, "onAppUsageStateChanged:" + packName + " new state:" + state);
        AppState appState = mAppStateInfoCollector.getAppState(packName, UserHandle.getUserId(uid));

        // add for bug#945204
        if (appState == null) return;

        if (state >= AppStateTracker.APP_PREDICT_STATE_WORKING_SET) {
            appState.mMinForcedStanbyDuration = 2*APPSTANDBY_PAROLE_TIMEOUT;
        } else {
            appState.mMinForcedStanbyDuration = 0;
        }

        if (mStandbyStartTime > 0) return;

        // check when system is not standby
        int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);
        if (PowerDataBaseControl.UNKNOWN != appType
            && state >= AppStateTracker.APP_PREDICT_STATE_WORKING_SET
            && !appState.mInAppStandby
            /*&& appState.mProcState >= ActivityManager.PROCESS_STATE_SERVICE*/
            && canBeConstrained(appState)) {
            if (DEBUG) Slog.d(TAG, "onAppUsageStateChanged: force " + packName + " to be idle");

            setAppInactive(appState.mPackageName, true, appState.mUserId);
            appState.updateAppStandbyState(true);

        } else if (appState.mInAppStandby) {
            if (DEBUG) Slog.d(TAG, "onAppUsageStateChanged: " + packName + " exit from force idle");

            setAppInactive(appState.mPackageName, false, appState.mUserId);
            appState.updateAppStandbyState(false);
        }

    }


    // wrap API for UsageStatsService
    private void setAppInactive(String packageName, boolean idle, int userId) {
        if (!mPowerControllerAppIdleEnabled) return;

        if (mPowerSaveMode == PowerManagerEx.MODE_NONE) {
            return;
        }

        if (mEnabledOnlySaveMode && !isSaveMode(mPowerSaveMode)) {
            //if (DEBUG) Slog.d(TAG, "setAppInactive return for only enable in save mode!");
            return;
        }

        AppState appState = mAppStateInfoCollector.getAppState(packageName, userId);

        if (appState == null) return;

        informListeners(packageName, appState.mUid, idle);
    }

    private boolean isSaveMode(int mode) {
        return (mode == PowerManagerEx.MODE_POWERSAVING
            || mode == PowerManagerEx.MODE_LOWPOWER
            || mode == PowerManagerEx.MODE_ULTRASAVING
            || mode == PowerManagerEx.MODE_INVALID);//see invalid as save mode
    }
    /*
     * Note device is enter standby state ( screen off / unpluged)
     */
    @Override
    void noteDeviceStateChanged(boolean bStandby, final long nowELAPSED) {
        if (bStandby) {
            if (nowELAPSED > 0) mStandbyStartTime = nowELAPSED;
            else mStandbyStartTime = SystemClock.elapsedRealtime();
            mNextParoleOnTime = mStandbyStartTime + APPSTANDBY_PAROLE_TIMEOUT + DEFAULT_APPSTANDBY_TIMEOUT;
        } else {
            mStandbyStartTime = 0;
            mNextParoleOnTime = 0;
            mNeedUpdateNextParoleOnTime = false;

            clearConstrainAppList();

            // add for bug#899925
            if (mEnableDnsRestrict && mNmi != null) {
                try {
                    mNmi.setFirewallDnsDenyEnabled(false);
                } catch (Exception e) {}
                mEnableDnsRestrict = false;
            }
            mCurrentRestrictAppCount = 0;
        }
    }

    private void clearConstrainAppList() {
        for (int index=mNewStandbyListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, Integer> mNewStandbyList = mNewStandbyListForUsers.valueAt(index);
            mNewStandbyList.clear();
        }
    }


    /*
     * Note all the AppStates Info all check done
     */
    void noteCheckAllAppStateInfoDone() {
        if (mNeedUpdateNextParoleOnTime) {
            mNextParoleOnTime = SystemClock.elapsedRealtime() + APPSTANDBY_PAROLE_TIMEOUT + DEFAULT_APPSTANDBY_TIMEOUT;
            mNeedUpdateNextParoleOnTime = false;
        }
    }

    void initData() {
        int defaultParoleTimeout = (int)(APPSTANDBY_PAROLE_TIMEOUT/(60*1000));
        int paroleTimeout = SystemProperties.getInt(PowerController.POWER_CONTROLLER_APPSTANDBY_PAROLE_TIMEOUT,
            defaultParoleTimeout);
        if (paroleTimeout != defaultParoleTimeout) {
            APPSTANDBY_PAROLE_TIMEOUT = paroleTimeout * 60 * 1000;
        }

        int defaultAppIdleTimeout2 = (int)(APPSTANDBY_TIMEOUT2/(60*1000));
        int appIdleTimeout2 = SystemProperties.getInt(PowerController.POWER_CONTROLLER_APPSTANDBY_TIMEOUT,
            defaultAppIdleTimeout2);
        if (appIdleTimeout2 != defaultAppIdleTimeout2) {
            APPSTANDBY_TIMEOUT2 = appIdleTimeout2 * 60 * 1000;
        }
        Slog.d(TAG, "APPSTANDBY_PAROLE_TIMEOUT:" + APPSTANDBY_PAROLE_TIMEOUT
            + " APPSTANDBY_TIMEOUT2:" + APPSTANDBY_TIMEOUT2);

    }

    private void handleAppIdleStateChanged(String packageName, int userId, int idle) {
        AppState appState = mAppStateInfoCollector.getAppState(packageName, userId);

        if (appState == null) {
            if (DEBUG) Slog.d(TAG, "Warning: packageName:" + packageName + " not in mAppStateInfoList");
            return;
        }

        boolean currentIdle = (idle == 1);
        // if idle state exit by UsageStatsService, re-evaluate
        if (appState.mInAppStandby && !currentIdle) {
            if (DEBUG) Slog.d(TAG, "App: " + packageName + " Exit from standby State:  by UsageStatsService ");

            appState.updateAppStandbyState(false);
            // re-update app standby Evaluated time, clear first
            updateAppEvaluatedTimeStamp(appState, true);
            // then update again
            updateAppEvaluatedTimeStamp(appState);

            // set the next enter standby interval to be APPSTANDBY_SECOND_TIMEOUT
            if (appState.mBeginEvaluatedTimeStamp > 0) {
                appState.mBeginEvaluatedTimeStamp -= (APPSTANDBY_TIMEOUT - APPSTANDBY_SECOND_TIMEOUT);
            }
        }
        return;
    }


    private boolean updateAppStandbyState(String packageName, boolean standby, int userId) {
        AppState appState = mAppStateInfoCollector.getAppState(packageName, userId);
        if (appState != null) {
            appState.updateAppStandbyState(standby);
        } else {
            if (DEBUG) Slog.d(TAG, "Warning: packageName:" + packageName + " not in mAppStateInfoList");
        }
        return true;
    }


    private boolean isCarrierApp(String packageName) {
        TelephonyManager telephonyManager =  (TelephonyManager)mContext.getSystemService(mContext.TELEPHONY_SERVICE);
        return telephonyManager.checkCarrierPrivilegesForPackageAnyPhone(packageName)
                    == TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS;
    }

    // when standby duration > APPSTANDBY_TIMEOUT
    // the timeout for app to enter app standby change to APPSTANDBY_TIMEOUT2
    private boolean idleTimeConstraintSatisfied(AppState appState, final long nowELAPSED) {
        long diffTime = nowELAPSED - appState.mBeginEvaluatedTimeStamp;
        long standbyDuration = nowELAPSED - mStandbyStartTime;
        long threshold = APPSTANDBY_TIMEOUT;

        // app may doing navigation during standby, see bug#978516
        long thresholdForLastPlayingMusic = (appState.mNeedUsingGps ? (2*APPSTANDBY_TIMEOUT):APPSTANDBY_TIMEOUT);
        if ( (nowELAPSED - appState.mLastTimePlayingMusicSeen) <= thresholdForLastPlayingMusic) {
            return false;
        }

        // for known type app, can be restrict immediately after standby
        int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);
        if (PowerDataBaseControl.UNKNOWN != appType
            && standbyDuration > 0 && standbyDuration < APPSTANDBY_TIMEOUT
            && mStandbyStartTime > 0
            && diffTime > 0
            && appState.mBeginEvaluatedTimeStamp > 0
            && (nowELAPSED - appState.mLastTimePlayingMusicSeen) > APPSTANDBY_TIMEOUT ) {
            if (DEBUG) Slog.d(TAG, "restrict known type app:" + appState.mPackageName);

            return true;
        }

        if (standbyDuration > APPSTANDBY_TIMEOUT && mStandbyStartTime > 0) {
            threshold = APPSTANDBY_TIMEOUT2;
        }

        if (threshold < diffTime
            && appState.mBeginEvaluatedTimeStamp > 0) {
            return true;
        }

        return false;
    }

    public String toString() {
        String out = TAG + "\n";
        out += "[mWhiteList]: " + mWhiteList + "\n";
        out += "[mBlackList]: " + mBlackList + "\n";
        return out;
    }


    // changed for bug#1027415
    private boolean inWhiteAppList(String pkgName, int uid) {
        if (pkgName == null) return true;

        try {
            if (mPowerControllerInternal.isWhitelistApp(pkgName, uid)) {
                return true;
            }
        } catch (Exception e) {
        }

        if (mWhiteList.contains(pkgName)) {
            return true;
        }

        /*check if in internal white app list, like CTS app*/
        for(String s : mInternalWhiteAppList) {
            if(pkgName.contains(s)) {
                return true;
            }
        }

        // is cts/gts app
        if (Util.isCts(pkgName)) {
            if (DEBUG_LOG) Slog.d(TAG, "CTS/GTS app: " + pkgName + ", see as in white list!!");
            return true;
        }

        return false;
    }

    private boolean inBlackAppList(String pkgName) {
        if (pkgName == null) return false;

        if (mBlackList.contains(pkgName)) {
            return true;
        }

        return false;
    }


    private void informListeners(String packageName, int uid, boolean isIdle) {
        synchronized (mLock) {
            for (Listener l: mListeners) {
                l.onAppIdleStateChanged(packageName, uid, isIdle);
            }
        }
    }

    void addListener(Listener listener) {
        synchronized(mLock) {
            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
            }
        }
    }

    public static abstract class Listener {
        public void onAppIdleStateChanged(String packageName, int uid, boolean idle) {
        }
    }

    private boolean isDebug(){
        return Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug");
    }

    private ArrayMap<String, Integer> getNewStandbyList(int userId) {
        ArrayMap<String, Integer> mNewStandbyList = mNewStandbyListForUsers.get(userId);
        if (mNewStandbyList == null) {
            mNewStandbyList = new ArrayMap<>();
            mNewStandbyListForUsers.put(userId, mNewStandbyList);
        }
        return mNewStandbyList;
    }

}
