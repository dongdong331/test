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
import android.os.Build;
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

import com.android.server.LocalServices;
import com.android.server.LocationManagerService;

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

public class GpsConstraintHelper extends PowerSaveHelper {

    private static final String TAG = "PowerController.Gps";

    private static final int FOREGROUND_THRESHOLD_STATE =
            ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;

    private final boolean DEBUG_LOG = Util.getDebugLog();
    private final boolean DEBUG = isDebug() || DEBUG_LOG;
    private final boolean DEBUG_MORE = false;
    private final boolean TEST = PowerController.TEST;

    // IF app request gps duration more than this time,
    // then see this app as a frequently gps request app
    private final long FREQUENTLY_GPS_REQUEST_THRESHOLD =  (10*60*1000);

    private final long DEFAULT_GPS_CONSTRAINT_IDLE_THRESHOLD = (TEST ? 5 * 60 * 1000L : 30 * 60 * 1000L);

    // the idle time (ms) for app that can be stopped
    private long GPS_CONSTRAINT_IDLE_THRESHOLD = DEFAULT_GPS_CONSTRAINT_IDLE_THRESHOLD;

    private final long MINI_IDLE_DURATION_THRESHOLD =  (1*60*1000);

    // use property to control this feature
    private final boolean mEnabled = (1 == SystemProperties.getInt(PowerController.POWER_CONTROLLER_GPS_ENABLE, 1));

    private final boolean mEnabledOnlySaveMode =  ((1 == SystemProperties.getInt(PowerController.PWCTL_ENABLE_ONLY_SAVEMODE, 0))
        || (1 == SystemProperties.getInt(PowerController.PWCTL_ENABLE_GPS_ONLY_SAVEMODE, 0)));

    private final IActivityManager mActivityManager;
    private Handler mHandler;

    //Reference to services
    private final PowerControllerHelper mPowerControllerHelper;

    private RecogAlgorithm mRecogAlgorithm;

    // Apps that can be constraint GPS access
    //private ArrayList<String> mConstraintAppList = new ArrayList<>();
    // Applist waiting to add to gps constraint for all users and all packages  <pkgName, uid>
    private SparseArray<ArrayMap<String, Integer>> mConstraintAppListForUsers = new SparseArray<>();

    // to recode the system elapsed time when starting standby
    private long mStandbyStartTime = 0;

    private static final int DEFAULT_POWER_MODE = PowerManagerEx.MODE_SMART;
    private int mPowerSaveMode = DEFAULT_POWER_MODE;


    // This white list is used for some app like CTS
    private final String[] mInternalWhiteAppList = new String[] {
        "android.app.cts",
        "com.android.cts",
        "android.icu.dev.test.util"
    };

    public GpsConstraintHelper(Context context, IActivityManager activityManager, Handler handler) {
        super(context, AppPowerSaveConfig.MASK_NULL);

        mActivityManager = activityManager;
        mHandler = new InternalHandler(handler.getLooper());

        mPowerControllerHelper = PowerControllerHelper.getInstance(mContext);

        mRecogAlgorithm = RecogAlgorithm.getInstance(mContext);

        mPowerControllerHelper.registerLocationRequestListener(new LocationRequestObserver());

        // set constants
        GPS_CONSTRAINT_IDLE_THRESHOLD = mConstants.GPS_INACTIVE_TIMEOUT;
        if (DEBUG) Slog.d(TAG, "GPS_CONSTRAINT_IDLE_THRESHOLD:" + GPS_CONSTRAINT_IDLE_THRESHOLD);
    }


    //Message define
    static final int MSG_LOCATION_REQUEST_UPDATE = 0;


    class InternalHandler extends Handler {
        InternalHandler(Looper looper) {
            super(looper);
        }

        String Msg2Str(int msg) {
            final String msgStr[] = {"MSG_LOCATION_REQUEST_UPDATE"
            };

            if ((0 <= msg) && (msg < msgStr.length))
            return msgStr[msg];
            else
            return "Unknown message: " + msg;
        }

        @Override public void handleMessage(Message msg) {
            if (DEBUG) Slog.d(TAG, "handleMessage(" + Msg2Str(msg.what) + ")");

            switch (msg.what) {
            case MSG_LOCATION_REQUEST_UPDATE:
                handleLocationRequestUpdate((String)msg.obj, msg.arg1, msg.arg2);
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
            case PowerManagerEx.MODE_ULTRASAVING:
            case PowerManagerEx.MODE_POWERSAVING:
                GPS_CONSTRAINT_IDLE_THRESHOLD = mConstants.GPS_INACTIVE_TIMEOUT_LOWPOWER; // 5min
                break;

            case PowerManagerEx.MODE_PERFORMANCE:
            case PowerManagerEx.MODE_SMART:
                GPS_CONSTRAINT_IDLE_THRESHOLD = mConstants.GPS_INACTIVE_TIMEOUT; // 30min
                break;
        }
    }


    /*
     * App cannot be constrained for gps access:
     * 1. system app (app.uid <= Process.FIRST_APPLICATION_UID, this is enough ???)
     * 2. sport app, that is running using GPS
     * 3. unknown app type, and its procState <= FOREGROUND_THRESHOLD_STATE
     * 4. app in Doze white list
     */
    private boolean canBeConstrained(AppState appState) {

        // if this function is disabled, just return false
        if (!mEnabled) return false;

        if (mPowerSaveMode == PowerManagerEx.MODE_NONE) {
            return false;
        }

        // check idle time
        if (!idleTimeSatisfied(appState, MINI_IDLE_DURATION_THRESHOLD)) {
            return false;
        }

        // if enable only in save mode
        if (mEnabledOnlySaveMode && !isSaveMode(mPowerSaveMode)) {
            //if (DEBUG) Slog.d(TAG, "canBeConstrained return false for only enable in save mode!");
            return false;
        }

        int procState = appState.mProcState;
        if (DEBUG_MORE) Slog.d(TAG, "canBeConstrained: uid:" + appState.mUid
            + " :" + appState.mPackageName + " procState:" + Util.ProcState2Str(procState)
            + " Group State:" + Util.AppState2Str(appState.mState));

        if (inWhiteAppList(appState.mPackageName)) {
            return false;
        }

        // system app can not be constrained
        if (appState.mUid <= Process.FIRST_APPLICATION_UID)
            return false;


        boolean hasLaunched = false;
        if (appState.mLastLaunchTime > 0) hasLaunched = true;

        // running sport App can not be constrained
        int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);
        if (hasLaunched && PowerDataBaseControl.SPORTS == appType
            && procState <= FOREGROUND_THRESHOLD_STATE) {
            if (!idleTimeConstraintSatisfied(appState)) {
                if (DEBUG) Slog.d(TAG, "canBeConstrained: " + appState.mPackageName + " SPORTS App type");

                // set this app require gps
                if (mRecogAlgorithm.hasRequestingGps(appState.mPackageName, appState.mUid))
                    appState.updateGpsRequirementState(true);
                return false;
            }
        }

        // app type is unknown
        // and procState <= FOREGROUND_THRESHOLD_STATE
        // can not be constrained
        if ((PowerDataBaseControl.UNKNOWN == appType
                || PowerDataBaseControl.MAP == appType
                || PowerDataBaseControl.SPORT == appType
                || PowerDataBaseControl.TRAFFIC == appType)
            && procState <= FOREGROUND_THRESHOLD_STATE) {
            if (hasLaunched &&
                (appState.mHasNoClearNotification || mRecogAlgorithm.hasNoClearNotification(appState.mPackageName, appState.mUid))) {
                if (DEBUG) Slog.d(TAG, "canBeConstrained: " + appState.mPackageName + " UNKNOWN App type");
                // set this app require gps
                if (mRecogAlgorithm.hasRequestingGps(appState.mPackageName, appState.mUid))
                    appState.updateGpsRequirementState(true);
                return false;
            }
        }

        if (PowerDataBaseControl.UNKNOWN == appType
            && !mRecogAlgorithm.canConstraint(appState.mPackageName, appState.mUid, RecogAlgorithm.TYPE_GPS)) {
            if (DEBUG) Slog.d(TAG, "canBeConstrained: " + appState.mPackageName + " is avoid GPS Constraint");
            // set this app require gps
            appState.updateGpsRequirementState(true);
            return false;
        }

        // set this app require gps
        appState.updateGpsRequirementState(false);

        return true;
    }


    /*
     * check this app should be constrained or not
     * return true if this app should be constrained
     * others return false
     */
    @Override
    boolean checkAppStateInfo(AppState appState, final long nowELAPSED) {
        ArrayMap<String, Integer> mConstraintAppList = getConstraintAppList(appState.mUserId);

        boolean bChanged = false;

        if (canBeConstrained(appState)) {
            if (!appState.mGpsConstrained) {
                bChanged = true;
                mConstraintAppList.put(appState.mPackageName, appState.mUid);
            }
        } else {
            clearConstrain(appState);

            // if already put it in mConstraintAppList, just remove
            mConstraintAppList.remove(appState.mPackageName);
        }

        return bChanged;
    }

    // apply the constraint
    @Override
    void applyConstrain() {
        if (mStandbyStartTime <= 0) {
            if (DEBUG) Slog.d(TAG, " system becomes non-standby before applying constrain!");
            clearConstrainAppList();
            return;
        }

        for (int index=mConstraintAppListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, Integer> mConstraintAppList = mConstraintAppListForUsers.valueAt(index);

            try {
                for (int i=0;i<mConstraintAppList.size();i++) {
                    String pkgName =  mConstraintAppList.keyAt(i);
                    int uid = mConstraintAppList.valueAt(i);
                    AppState appState = mAppStateInfoCollector.getAppState(pkgName, UserHandle.getUserId(uid));
                    if (appState != null) {
                        appState.updateAppGpsConstrainedState(true);
                    } else {
                        Slog.w(TAG, "null appState for " + pkgName + " uid:" + uid);
                    }

                    mPowerControllerHelper.noteGpsConstraintStateChanged(pkgName, uid, false);

                    Slog.d(TAG, "applay GPS Constraint:" + pkgName + " uid:" + uid);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            mConstraintAppList.clear();

        }

    }

    // clear the constraint
    @Override
    void clearConstrain(AppState appState) {
        if (appState.mGpsConstrained) {
            appState.updateAppGpsConstrainedState(false);
            try {
                mPowerControllerHelper.noteGpsConstraintStateChanged(appState.mPackageName, appState.mUid, true);
                if (DEBUG) Slog.d(TAG, "clear GPS Constraint:" + appState.mPackageName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /*
     * Note device is enter standby state ( screen off / unpluged)
     */
    @Override
    void noteDeviceStateChanged(boolean bStandby, final long nowELAPSED) {
        if (DEBUG) Slog.d(TAG, "noteDeviceStateChanged bStandby:" + bStandby);

        if (bStandby) {
            mStandbyStartTime = nowELAPSED; //SystemClock.elapsedRealtime();
        } else {
            mStandbyStartTime = 0;
            clearConstrainAppList();
        }
    }

    @Override
    void checkAppRequirement(AppState appState) {
        if (appState == null) return;

        int procState = appState.mProcState;

        boolean requireGps = false;
        boolean hasLaunched = false;
        if (appState.mLastLaunchTime > 0) hasLaunched = true;

        // running sport App can not be constrained
        int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);
        if ((PowerDataBaseControl.UNKNOWN == appType
                || PowerDataBaseControl.MAP == appType
                || PowerDataBaseControl.SPORT == appType
                || PowerDataBaseControl.TRAFFIC == appType)
            && procState <= FOREGROUND_THRESHOLD_STATE) {
            if (hasLaunched &&
                (appState.mHasNoClearNotification || mRecogAlgorithm.hasNoClearNotification(appState.mPackageName, appState.mUid))
                && mRecogAlgorithm.hasRequestingGps(appState.mPackageName, appState.mUid)) {
                if (DEBUG) Slog.d(TAG, "checkAppRequirement: " + appState.mPackageName + " UNKNOWN App type");
                requireGps = true;
            }
        }

        if (PowerDataBaseControl.UNKNOWN == appType
            && !mRecogAlgorithm.canConstraint(appState.mPackageName, appState.mUid, RecogAlgorithm.TYPE_GPS)) {
            if (DEBUG) Slog.d(TAG, "checkAppRequirement: " + appState.mPackageName + " is avoid GPS Constraint");
            requireGps = true;
        }

        appState.updateGpsRequirementState(requireGps);
    }

    private void clearConstrainAppList() {
        for (int index=mConstraintAppListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, Integer> mConstraintAppList = mConstraintAppListForUsers.valueAt(index);
            mConstraintAppList.clear();
        }
    }

    private boolean hasActiveNotification(AppState appState) {
        boolean hasNotification = false;

        // has other method to judge ??
        try {

            // if app type is unkown, and its procState is high, then we see it as has notification
            // such as a defined Message App
            int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);
            if (PowerDataBaseControl.UNKNOWN == appType
                && appState.mProcState <= FOREGROUND_THRESHOLD_STATE) {
                hasNotification = true;
            } else {
                INotificationManager inm = NotificationManager.getService();
                final ParceledListSlice<StatusBarNotification> parceledList
                        = inm.getAppActiveNotifications(appState.mPackageName, UserHandle.myUserId());
                final List<StatusBarNotification> list = parceledList.getList();

                if (list != null && list.size() > 0) {
                    hasNotification = true;
                }
            }
        } catch (Exception e) {}

        return hasNotification;
   }


    private boolean inWhiteAppList(String pkgName) {
        if (pkgName == null) return true;

        try {
            if (mPowerControllerInternal.isWhitelistApp(pkgName)) {
                return true;
            }
        } catch (Exception e) {
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

    private boolean isSaveMode(int mode) {
        return (mode == PowerManagerEx.MODE_POWERSAVING
            || mode == PowerManagerEx.MODE_LOWPOWER
            || mode == PowerManagerEx.MODE_ULTRASAVING
            || mode == PowerManagerEx.MODE_INVALID);//see invalid as save mode
    }

    // 1. standby time > GPS_CONSTRAINT_IDLE_THRESHOLD
    // 2. if procState <= FOREGROUND_THRESHOLD_STATE then
    //      idle time after standby should > GPS_CONSTRAINT_IDLE_THRESHOLD
    // 3. when the app has been satisfied the time constraint before, then it will be satisfied directly.
    private boolean idleTimeConstraintSatisfied(AppState appState) {
        if (appState.mGpsConstrained) return true;

        long nowELAPSED = SystemClock.elapsedRealtime();
        int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);
        long standbyDuration = nowELAPSED - mStandbyStartTime;
        long idleDuration = (appState.mLastTimeUsed > 0 ? (nowELAPSED -appState.mLastTimeUsed) : -1);
        long idleDurationBeforeStandby = (mStandbyStartTime > appState.mLastTimeUsed ? (mStandbyStartTime -appState.mLastTimeUsed) : 0);


        // procState <= FOREGROUND_THRESHOLD_STATE
        // then idle time after standby should > GPS_CONSTRAINT_IDLE_THRESHOLD
        if (appState.mProcState <= FOREGROUND_THRESHOLD_STATE
            && idleDuration < (idleDurationBeforeStandby + GPS_CONSTRAINT_IDLE_THRESHOLD)) {
            if (DEBUG_MORE) Slog.d(TAG, "idleTimeConstraintSatisfied: " + appState.mPackageName + " idle for " + idleDuration);
            return false;
        }

        if (GPS_CONSTRAINT_IDLE_THRESHOLD > standbyDuration && mStandbyStartTime > 0) {
            return false;
        }

        if (DEBUG_MORE) Slog.d(TAG, "idleTimeConstraintSatisfied: " + appState.mPackageName
            + " ProcState: "  + Util.ProcState2Str(appState.mProcState)
            + " State:" + Util.AppState2Str(appState.mState)
            + " is satisfied");

        return true;
    }


    private boolean idleTimeSatisfied(AppState appState, long minIdleDuration) {

        long nowELAPSED = SystemClock.elapsedRealtime();
        int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);
        long standbyDuration = nowELAPSED - mStandbyStartTime;
        long idleDuration = (appState.mLastTimeUsed > 0 ? (nowELAPSED -appState.mLastTimeUsed) : -1);
        long idleDurationBeforeStandby = (mStandbyStartTime > appState.mLastTimeUsed ? (mStandbyStartTime -appState.mLastTimeUsed) : 0);

        if (idleDuration < (idleDurationBeforeStandby + minIdleDuration)
            && standbyDuration < 2*minIdleDuration) {
            if (DEBUG_MORE) Slog.d(TAG, "idleTimeSatisfied: " + appState.mPackageName + " idle for " + idleDuration);
            return false;
        }

        if (minIdleDuration > standbyDuration || mStandbyStartTime == 0) {
            return false;
        }

        if (DEBUG_MORE) Slog.d(TAG, "idleTimeSatisfied: " + appState.mPackageName
            + " ProcState: "  + Util.ProcState2Str(appState.mProcState)
            + " State:" + Util.AppState2Str(appState.mState)
            + " is satisfied");

        return true;
    }


    private class LocationRequestObserver
        implements PowerControllerHelper.LocationRequestListener {
        @Override
        public void onLocationRequest(String packageName, int uid, int state) {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_LOCATION_REQUEST_UPDATE, uid, state, packageName));
        }
    }

    private void handleLocationRequestUpdate(String pkgName, int uid, int state) {
        if (DEBUG) Slog.d(TAG, "request GPS: " + pkgName + (state ==1 ? " start" : " stop"));
        mRecogAlgorithm.reportEvent(pkgName, uid, RecogAlgorithm.EVENT_TYPE_GPS_STATE, state);

        AppState appState = mAppStateInfoCollector.getAppState(pkgName, UserHandle.getUserId(uid));
        if (appState != null) {
            appState.updateGpsRequestState(state);
            if (appState.getGpsRequestDuration() > FREQUENTLY_GPS_REQUEST_THRESHOLD) {
                if (DEBUG_LOG) Slog.d(TAG, "frequently GPS request duration: " + appState.getGpsRequestDuration()
                    + " pkgName:" + pkgName + " userId:" + UserHandle.getUserId(uid));
                mPowerControllerInternal.updateAppPowerConsumerType(pkgName, AppPowerSaveConfig.POWER_CONSUMER_TYPE_GPS,
                    AppPowerSaveConfig.POWER_CONSUMER_TYPE_GPS);
            }
        }
    }

    private boolean isDebug(){
        return Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug");
    }

    private ArrayMap<String, Integer> getConstraintAppList(int userId) {
        ArrayMap<String, Integer> mConstraintAppList = mConstraintAppListForUsers.get(userId);
        if (mConstraintAppList == null) {
            mConstraintAppList = new ArrayMap<>();
            mConstraintAppListForUsers.put(userId, mConstraintAppList);
        }
        return mConstraintAppList;
    }
}
