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
import android.os.WorkSource;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.SparseArray;
import android.widget.RemoteViews;

import com.android.internal.app.IBatteryStats;
import com.android.internal.os.BatteryStatsHelper;
import com.android.internal.R;
import com.android.server.LocalServices;
import com.android.server.am.BatteryStatsService;

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

// Class WakeLockConstrainHelper to make decision of What apps should be put
// into a wake lock constrained list
public class WakelockConstraintHelper extends PowerSaveHelper {

    private static final String TAG = "PowerController.Wakelock"; //"WakelockConstraintHelper";

    private static final int FOREGROUND_THRESHOLD_STATE =
             ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;

    private final boolean DEBUG_LOG = Util.getDebugLog();
    private final boolean DEBUG = isDebug() || DEBUG_LOG;
    private final boolean DEBUG_MORE = false;
    private final boolean TEST = PowerController.TEST;


    private final long DEFAULT_WAKELOCK_CONSTRAIN_IDLE_THRESHOLD =  (TEST ? 5 * 60 * 1000L : 20 * 60 * 1000L);
    private final long DEFAULT_WAKE_LOCK_DISABLE_THRESHOLD =  (30 * 1000L); // 30s

    //Max time in app idle state before to be constrained for wakelock
    private long WAKELOCK_CONSTRAIN_IDLE_THRESHOLD =  DEFAULT_WAKELOCK_CONSTRAIN_IDLE_THRESHOLD;

    // threshold time to disable wakelock for constraint app
    private long WAKE_LOCK_DISABLE_THRESHOLD =  DEFAULT_WAKE_LOCK_DISABLE_THRESHOLD; // 30s

    // duration threshold time for a wakelock.
    // Used to get long duration partial wakelock
    private long WAKELOCK_DURATION_THRESHOLD =  (TEST ? 5 * 60 * 1000L : 30 * 60 * 1000L); // 30min

    // These tag name & pkgname will diff in different Android Version
    private static final String AUDIO_IN_WAKELOCK_TAG = "AudioIn";
    private static final String AUDIO_OUT_WAKELOCK_TAG = "AudioMix";
    private static final String AUDIO_OUT_WAKELOCK_OFFLOAD_TAG = "AudioOffload";
    private static final String AUDIO_PACKAGE_NAME = "audioserver";

    // use property to control this feature
    private final boolean mEnabled = (1 == SystemProperties.getInt(PowerController.POWER_CONTROLLER_WAKELOCK_ENABLE, 1));

    private final boolean mEnabledOnlySaveMode =  (1 == SystemProperties.getInt(PowerController.PWCTL_ENABLE_ONLY_SAVEMODE, 0));

    private final IActivityManager mActivityManager;
    private Handler mHandler;


    //Reference to services
    private final PowerManagerInternal mLocalPowerManager;

    private RecogAlgorithm mRecogAlgorithm;

    // Applist to constrain wakelock
    //private ArrayMap<String, Integer> mNewWakeLockAppList = new ArrayMap<String, Integer>();
    // Applist waiting to add to wakelock constraint for all users and all packages  <pkgName, uid>
    private SparseArray<ArrayMap<String, Integer>> mNewWakeLockAppListForUsers = new SparseArray<>();

    // App list that has audio out <uid, pkgName>
    private ArrayMap<Integer, String> mAudioOutAppList = new ArrayMap<>();

    // App list that has audio in <uid, pkgName>
    private ArrayMap<Integer, String> mAudioInAppList = new ArrayMap<>();

    // Table of all wake locks acquired by audio.
    private final ArrayList<WakeLockInfo> mAudioOutWakeLocks = new ArrayList<WakeLockInfo>();

    private WakeLockObserver mWakeLockObserver;

    // to recode the system elapsed time when starting standby
    private long mStandbyStartTime = 0;

    // Delay time to check the if need to disable wakelock for constraint app
    private long mNextWakelockDisabledCheckDelayTime = WAKE_LOCK_DISABLE_THRESHOLD;
    private long mLastWakelockDelayTimeUpdateTimeStamp = 0;
    private final Object mWakelockDelayLock = new Object();

    private static final int DEFAULT_POWER_MODE = PowerManagerEx.MODE_SMART;
    private int mPowerSaveMode = DEFAULT_POWER_MODE;

    private List<String> mWhiteList;
    private List<String> mBlackList;


    private WakelockHistory mWakelockHistory;
    private WakeLockCallback mWakelockCallback;

    private Notification.Builder mNotificationBuilder = null;
    private String mNotificationTitle = null;
    private String mNotificationSummary = null;
    private RemoteViews mRemoteViews = null;
    private PendingIntent mNotificationIntent = null;

    // This white list is used for some app like CTS
    private final String[] mInternalWhiteAppList = new String[] {
        "android.app.cts",
        "com.android.cts",
        "android.icu.dev.test.util"
    };

    public WakelockConstraintHelper(Context context, IActivityManager activityManager, Handler handler) {
        super(context, AppPowerSaveConfig.MASK_WAKELOCK);

        mActivityManager = activityManager;
        mHandler = new InternalHandler(handler.getLooper());

        mLocalPowerManager = LocalServices.getService(PowerManagerInternal.class);

        // white & black list
        mWhiteList = mPowerControllerInternal.getWhiteList(AppPowerSaveConfig.ConfigType.TYPE_WAKELOCK.value);
        mBlackList = mPowerControllerInternal.getBlackList(AppPowerSaveConfig.ConfigType.TYPE_WAKELOCK.value);

        mWakelockHistory = new WakelockHistory();
        mWakelockCallback = new WakeLockCallback();

        // set constants
        WAKELOCK_CONSTRAIN_IDLE_THRESHOLD = mConstants.WAKELOCK_INACTIVE_TIMEOUT;
        WAKE_LOCK_DISABLE_THRESHOLD = mConstants.WAKELOCK_CONSTRAINT_DURATION;
        if (DEBUG) Slog.d(TAG, "WAKELOCK_CONSTRAIN_IDLE_THRESHOLD:" + WAKELOCK_CONSTRAIN_IDLE_THRESHOLD
            + " WAKE_LOCK_DISABLE_THRESHOLD:" + WAKE_LOCK_DISABLE_THRESHOLD);


        // register Audio wakelock Observer
        mWakeLockObserver = new WakeLockObserver();
        mLocalPowerManager.registerPowerControllerInternalCallback(mWakeLockObserver);
        // set the wake lock disabled threshold
        mLocalPowerManager.updateWakeLockDisabledThreshold(WAKE_LOCK_DISABLE_THRESHOLD);

        mRecogAlgorithm = RecogAlgorithm.getInstance(mContext);
    }


    //Message define
    static final int MSG_WAKELOCK_STATE_CHANGED = 0;
    static final int MSG_LONG_PARTIAL_WAKELOCK = 1;
    static final int MSG_AUDIOIN_WAKELOCK_UPDATED = 2;
    static final int MSG_AUDIOIN_WAKELOCK_RELEASED = 3;
    static final int MSG_AUDIOOUT_WAKELOCK_UPDATED = 4;
    static final int MSG_AUDIOOUT_WAKELOCK_RELEASED = 5;

    class InternalHandler extends Handler {
        InternalHandler(Looper looper) {
            super(looper);
        }

        String Msg2Str(int msg) {
            final String msgStr[] = {"MSG_WAKELOCK_STATE_CHANGED",
                "MSG_LONG_PARTIAL_WAKELOCK",
                "MSG_AUDIOIN_WAKELOCK_UPDATED",
                "MSG_AUDIOIN_WAKELOCK_RELEASED",
                "MSG_AUDIOOUT_WAKELOCK_UPDATED",
                "MSG_AUDIOOUT_WAKELOCK_RELEASED"
            };

            if ((0 <= msg) && (msg < msgStr.length))
                return msgStr[msg];
            else
                return "Unknown message: " + msg;
        }

        @Override public void handleMessage(Message msg) {
            if (DEBUG) Slog.d(TAG, "handleMessage(" + Msg2Str(msg.what) + ")");

            switch (msg.what) {
            case MSG_WAKELOCK_STATE_CHANGED:
                updateWakeLockState();
                break;
            case MSG_LONG_PARTIAL_WAKELOCK:
                handleLongPartialWakelock((WakelockHistory.WakelockInfo)msg.obj);
                break;
            case MSG_AUDIOIN_WAKELOCK_UPDATED:
                handleAudioInWakeLockUpdated((ArrayList<Integer>)msg.obj);
                break;
            case MSG_AUDIOIN_WAKELOCK_RELEASED:
                handleAudioInWakeLockReleased();
                break;
            case MSG_AUDIOOUT_WAKELOCK_UPDATED:
                handleAudioOutWakeLockUpdated((WakeLockInfo)msg.obj);
                break;
            case MSG_AUDIOOUT_WAKELOCK_RELEASED:
                handleAudioOutWakeLockReleased((WakeLockInfo)msg.obj);
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
                WAKELOCK_CONSTRAIN_IDLE_THRESHOLD = mConstants.WAKELOCK_INACTIVE_TIMEOUT_LOWPOWER; // default 5min
                WAKE_LOCK_DISABLE_THRESHOLD = mConstants.WAKELOCK_CONSTRAINT_DURATION_LOWPOWER; // default 5s
                break;

            case PowerManagerEx.MODE_POWERSAVING:
                WAKELOCK_CONSTRAIN_IDLE_THRESHOLD = mConstants.WAKELOCK_INACTIVE_TIMEOUT_LOWPOWER; // default 5min
                WAKE_LOCK_DISABLE_THRESHOLD = mConstants.WAKELOCK_CONSTRAINT_DURATION; //30S
                break;

            case PowerManagerEx.MODE_PERFORMANCE:
            case PowerManagerEx.MODE_SMART:
                WAKELOCK_CONSTRAIN_IDLE_THRESHOLD = mConstants.WAKELOCK_INACTIVE_TIMEOUT; // 20min
                WAKE_LOCK_DISABLE_THRESHOLD = mConstants.WAKELOCK_CONSTRAINT_DURATION; //30S
                break;


        }
    }

    /*
     * App cannot be constrained for App wakelock:
     * 1. system app (app.uid <= Process.FIRST_APPLICATION_UID, this is enough ???)
     * 2. app doing Download
     * 3. music app and is playing music
     * 4. unknown app type, and its procState <= FOREGROUND_THRESHOLD_STATE and is playing music
     * 5. app in Doze white list
     *
     * NOTE:for sports app: like com.codoon.gps/com.gotokeep.keep/cn.ledongli.ldl
     * its start/stop of tracking state can be distinguished only when the mProcState or mState or Notification have
     * different status in started / stopped state.
     */
    private boolean canBeConstrained(AppState appState) {

        // if this function is disabled, just return false
        if (!mEnabled) return false;

        if (mPowerSaveMode == PowerManagerEx.MODE_NONE) {
            return false;
        }

        // if enable only in save mode
        if (mEnabledOnlySaveMode && !isSaveMode(mPowerSaveMode)) {
            //if (DEBUG) Slog.d(TAG, "canBeConstrained return false for only enable in save mode!");
            return false;
        }

        int procState = appState.mProcState;
        if (DEBUG_MORE) Slog.d(TAG, "appWakeLockCanBeConstrained: uid:" + appState.mUid
            + " :" + appState.mPackageName + " procState:" + Util.ProcState2Str(procState)
            + " Group State:" + Util.AppState2Str(appState.mState));

        // system app can not be constrained
        if (appState.mUid <= Process.FIRST_APPLICATION_UID)
            return false;

        if (inWhiteAppList(appState.mPackageName)) {
            if (DEBUG) Slog.d(TAG, "appWakeLockCanBeConstrained: " + appState.mPackageName + " in my whitelist");
            return false;
        } else if (mBlackList.contains(appState.mPackageName)) {
            if (DEBUG) Slog.d(TAG, "appWakeLockCanBeConstrained: " + appState.mPackageName + " in my blacklist");
            return true;
        }

        // playing music App can not be constrained
        int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);
        if (PowerDataBaseControl.MUSIC == appType) {
            if (mPowerControllerInternal.isPlayingMusic(appState)) {
                if (DEBUG) Slog.d(TAG, "appWakeLockCanBeConstrained: " + appState.mPackageName + " Music is playing");
                appState.forceUpdateLastUsedTime();
                return false;
            }
        }

        // doing download App can not be constrained
        if (procState <= ActivityManager.PROCESS_STATE_SERVICE) {
            if (mPowerControllerInternal.isAppDoingDownload(appState)) {
                if (DEBUG) Slog.d(TAG, "appWakeLockCanBeConstrained: " + appState.mPackageName + " Doing Download");
                appState.forceUpdateLastUsedTime();
                return false;
            }
        }

        boolean hasLaunched = false;
        if (appState.mLastLaunchTime > 0) hasLaunched = true;

        // app procState <= FOREGROUND_THRESHOLD_STATE
        // and is playing music
        // can not be constrained
        if (/*PowerDataBaseControl.UNKNOWN == appType
            &&*/ procState <= FOREGROUND_THRESHOLD_STATE) {
            if (hasLaunched && mPowerControllerInternal.isPlayingMusic(appState)) {
                if (DEBUG) Slog.d(TAG, "appWakeLockCanBeConstrained: " + appState.mPackageName + " UNKNOWN App type Music is playing");
                appState.forceUpdateLastUsedTime();
                return false;
            }
        }

        // app type is unknown
        // and procState <= FOREGROUND_THRESHOLD_STATE
        // and is doing audio recoder
        // can not be constrained
        if (PowerDataBaseControl.UNKNOWN == appType
            && procState <= FOREGROUND_THRESHOLD_STATE) {
            if (hasLaunched && (appState.mAudioFlag & AppState.AUDIO_TYPE_IN) != 0) {
                if (DEBUG) Slog.d(TAG, "appWakeLockCanBeConstrained: " + appState.mPackageName + " UNKNOWN App type soundRecoder");
                appState.forceUpdateLastUsedTime();
                return false;
            }
        }

        // app type is unknown
        // and procState <= FOREGROUND_THRESHOLD_STATE
        // some sport APP, like keep (com.gotokeep.keep), start/stop sport tracking, its procState and state stay the same.
        // so in this case, cannot use appState.mLastTimeUsed to distinguish the state of sport tracking
        // so here return false for this case
        if ((PowerDataBaseControl.UNKNOWN == appType || PowerDataBaseControl.SPORT == appType)
            && procState <= FOREGROUND_THRESHOLD_STATE) {
            if (hasLaunched && appState.mHasNoClearNotification
                && !appState.stateChangedToFGDuringStandby(mStandbyStartTime)
                && !mPowerControllerInternal.isAdminApp(appState.mPackageName)) {
                if (DEBUG) Slog.d(TAG, "appWakeLockCanBeConstrained: " + appState.mPackageName + " UNKNOWN App type");
                appState.forceUpdateLastUsedTime();
                return false;
            }
        }

        return true;
    }

    /*
     * check this app should be constrained or not
     * return true if this app should be constrained, and is add to mNewWakeLockAppList
     * others return false
     */
    @Override
    boolean checkAppStateInfo(AppState appState, final long nowELAPSED) {
        ArrayMap<String, Integer> mNewWakeLockAppList = getNewWakeLockAppList(appState.mUserId);

        boolean bChanged = false;
        if (canBeConstrained(appState)) {
            if (!appState.mWakeLockContrained
                    && (idleTimeConstraintSatisfied(appState, nowELAPSED)
                    || inBlackAppList(appState.mPackageName))) {
                bChanged = true;
                mNewWakeLockAppList.put(appState.mPackageName, appState.mUid);
                if (DEBUG) Slog.d(TAG, "packageName:" +appState.mPackageName
                    + " (procState:" + Util.ProcState2Str(appState.mProcState)
                    + ") will be constrained Wake Lock");
            }
        } else {
            if (appState.mWakeLockContrained) {
                // Notify PowerManager to recovery the wake lock
                notifyAppWakeLockConstrainedStateChanged(appState.mUid, false);
                appState.updateAppWakeLockConstrainedState(false);
            }

            // if already put it in mNewWakeLockAppList, just remove
            mNewWakeLockAppList.remove(appState.mPackageName);
        }

        return bChanged;
    }

    // set apps that should constrain its wakelock
    @Override
    void applyConstrain() {
        if (mStandbyStartTime <= 0) {
            if (DEBUG) Slog.d(TAG, " system becomes non-standby before applying constrain!");
            clearConstrainAppList();
            return;
        }

        for (int index=mNewWakeLockAppListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, Integer> mNewWakeLockAppList = mNewWakeLockAppListForUsers.valueAt(index);

            for (int i=0;i<mNewWakeLockAppList.size();i++) {
                try {
                    if (DEBUG) Slog.d(TAG, "packageName:" + mNewWakeLockAppList.keyAt(i)
                        + " uid:" + mNewWakeLockAppList.valueAt(i) + " constrained Wake Lock");
                    // Notify PowerManager to disable the wake lock of this app
                    notifyAppWakeLockConstrainedStateChanged(mNewWakeLockAppList.valueAt(i), true);
                    updateAppWakeLockConstrainedState(mNewWakeLockAppList.keyAt(i), true, mNewWakeLockAppList.valueAt(i));
                } catch (Exception e) {
                    // fall through
                }
            }
            mNewWakeLockAppList.clear();

        }
    }

    // clear the app from the wakelock constrained app list
    @Override
    void clearConstrain(AppState appState) {
        if (appState.mWakeLockContrained) {
            notifyAppWakeLockConstrainedStateChanged(appState.mUid, false);
            appState.updateAppWakeLockConstrainedState(false);
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
            // reset the time
            synchronized (mWakelockDelayLock) {
                mNextWakelockDisabledCheckDelayTime = WAKE_LOCK_DISABLE_THRESHOLD;
                mLastWakelockDelayTimeUpdateTimeStamp = 0;
            }

            clearConstrainAppList();
        }
    }

    private void clearConstrainAppList() {
        for (int index=mNewWakeLockAppListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, Integer> mNewWakeLockAppList = mNewWakeLockAppListForUsers.valueAt(index);
            mNewWakeLockAppList.clear();
        }
    }

    // wrap API for PowerManagerInternal
    private void notifyAppWakeLockConstrainedStateChanged(int uid, boolean constrained) {
        if (DEBUG) Slog.d(TAG, "notifyAppWakeLockConstrainedStateChanged() E uid:" + uid + " constrained:" + constrained);
        if (mLocalPowerManager != null) {
            mLocalPowerManager.updateUidProcWakeLockDisabledState(uid, constrained);
        }
    }


    // used by PowerManager to notify that a constrant app new acquire a wakelock
    // nowElapsed : is the now elapsed time
    // wakelockStartTime: the start time of the wakelock
    void noteConstraintAppAcquireWakeLock(long nowElapsed, long wakelockStartTime) {
        if (DEBUG) Slog.d(TAG, "noteConstraintAppAcquireWakeLock: ");
        long nextDelayTime = mNextWakelockDisabledCheckDelayTime;
        long leftTime = WAKE_LOCK_DISABLE_THRESHOLD - (nowElapsed - wakelockStartTime);
        synchronized (mWakelockDelayLock) {
            long deltTimeElapsed = 0;
            if (mLastWakelockDelayTimeUpdateTimeStamp > 0) {
                deltTimeElapsed = nowElapsed - mLastWakelockDelayTimeUpdateTimeStamp;
                if (deltTimeElapsed < 0) {
                    // time error!!
                    deltTimeElapsed = 0;
                }
            }

            mLastWakelockDelayTimeUpdateTimeStamp = nowElapsed;

            if (mNextWakelockDisabledCheckDelayTime > deltTimeElapsed)
                mNextWakelockDisabledCheckDelayTime -= deltTimeElapsed;

            if (wakelockStartTime > 0 && leftTime > 0
                && mNextWakelockDisabledCheckDelayTime > leftTime) {
                mNextWakelockDisabledCheckDelayTime = leftTime;
            }

            nextDelayTime = mNextWakelockDisabledCheckDelayTime;
        }

        if (DEBUG) Slog.d(TAG, "noteConstraintAppAcquireWakeLock: nextDelayTime:" + nextDelayTime);
        mHandler.removeMessages(MSG_WAKELOCK_STATE_CHANGED);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_WAKELOCK_STATE_CHANGED), nextDelayTime);
    }


    /*
     * Note all the AppStates Info all check done
     */
    void noteCheckAllAppStateInfoDone() {
        // check if a app has a long duration partial wakelock
        if (mLocalPowerManager != null) {
            mLocalPowerManager.checkWakeLock(mWakelockCallback, WAKELOCK_DURATION_THRESHOLD);
        }
    }

    /*
     * Note a app is stopped
     */
    void noteAppStopped(AppState appState) {
        if (mWakelockHistory != null)
            mWakelockHistory.noteAppStopped(appState.mPackageName, UserHandle.myUserId());
    }

    private boolean isSaveMode(int mode) {
        return (mode == PowerManagerEx.MODE_POWERSAVING
            || mode == PowerManagerEx.MODE_LOWPOWER
            || mode == PowerManagerEx.MODE_ULTRASAVING
            || mode == PowerManagerEx.MODE_INVALID);//see invalid as save mode
    }

    //
    //  --------------------------------- private interface ---------------------------------
    //

    private class WakeLockCallback
            extends PowerManagerInternal.WakeLockCallback {
        @Override
        public void onWakeLock(String pkgName, String tag, int uid, long holdDuration) {
            WakelockHistory.WakelockInfo wl = new WakelockHistory.WakelockInfo(pkgName, tag, uid, holdDuration);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_LONG_PARTIAL_WAKELOCK, wl));
        }
    }

    private class WakeLockObserver
        implements PowerManagerInternal.PowerControllerInternalCallback {
        @Override
        public void onWakeLockAcquired(String tag, String packageName,
            int ownerUid, int ownerPid, WorkSource workSource) {
            noteAudioWakeLockAcquired(tag, packageName, ownerUid, ownerPid, workSource);
        }
        @Override
        public void onWakeLockReleased(String tag, String packageName,
            int ownerUid, int ownerPid, WorkSource workSource) {
            noteAudioWakeLockReleased(tag, packageName, ownerUid, ownerPid, workSource);
        }
        @Override
        public void onWakeLockChanging(String tag, String packageName,
                int ownerUid, int ownerPid, WorkSource workSource,String newTag,
                String newPackageName, int newOwnerUid, int newOwnerPid, WorkSource newWorkSource) {
            noteAudioWakeLockChanging(tag, packageName,
                ownerUid, ownerPid, workSource, newTag,
                newPackageName, newOwnerUid, newOwnerPid, newWorkSource);
        }
        @Override
        public void onConstraintAppAcquireWakeLock(long nowElapsed, long wakelockStartTime) {
            noteConstraintAppAcquireWakeLock(nowElapsed, wakelockStartTime);
        }
    }

    static final class WakeLockInfo {
        String mPackageName;
        String mTag;
        int mUid;
        WorkSource mWorkSource;

        WakeLockInfo(String tag, String packageName,
                int uid, WorkSource workSource) {
            mUid = uid;
            mPackageName = packageName;
            mTag = tag;
            mWorkSource = copyWorkSource(workSource);
        }
    }

    private static WorkSource copyWorkSource(WorkSource workSource) {
        return workSource != null ? new WorkSource(workSource) : null;
    }

    // 1. standby time > WAKELOCK_CONSTRAIN_IDLE_THRESHOLD
    // 2. if procState <= FOREGROUND_THRESHOLD_STATE then
    //      idle time after standby should > WAKELOCK_CONSTRAIN_IDLE_THRESHOLD
    private boolean idleTimeConstraintSatisfied(AppState appState, final long nowELAPSED) {
        int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);
        long standbyDuration = nowELAPSED - mStandbyStartTime;
        long idleDuration = (appState.mLastTimeUsed > 0 ? (nowELAPSED -appState.mLastTimeUsed) : -1);
        long idleDurationBeforeStandby = (mStandbyStartTime > appState.mLastTimeUsed ? (mStandbyStartTime -appState.mLastTimeUsed) : 0);


        // for known type app, can be restrict immediately after standby 5mins
        long threshold = (WAKELOCK_CONSTRAIN_IDLE_THRESHOLD > (5*60*1000L)) ? (5*60*1000L) : WAKELOCK_CONSTRAIN_IDLE_THRESHOLD;
        if (PowerDataBaseControl.UNKNOWN != appType
            && standbyDuration > 0
            && mStandbyStartTime > 0
            && idleDuration > (idleDurationBeforeStandby + threshold)) {
            return true;
        }

        // procState <= FOREGROUND_THRESHOLD_STATE
        // then idle time after standby should > WAKELOCK_CONSTRAIN_IDLE_THRESHOLD
        if (idleDuration < (idleDurationBeforeStandby + WAKELOCK_CONSTRAIN_IDLE_THRESHOLD)) {
            if (DEBUG_MORE) Slog.d(TAG, "idleTimeConstraintSatisfied: " + appState.mPackageName + " idle for " + idleDuration);
            return false;
        }

        if (WAKELOCK_CONSTRAIN_IDLE_THRESHOLD > standbyDuration && mStandbyStartTime > 0) {
            return false;
        }

        if (DEBUG_MORE) Slog.d(TAG, "idleTimeConstraintSatisfied: " + appState.mPackageName
            + " ProcState: "  + Util.ProcState2Str(appState.mProcState)
            + " State:" + Util.AppState2Str(appState.mState)
            + " is satisfied");

        return true;
    }


    private void updateWakeLockState() {
        // reset the next delay time
        synchronized (mWakelockDelayLock) {
            mNextWakelockDisabledCheckDelayTime = WAKE_LOCK_DISABLE_THRESHOLD;
            mLastWakelockDelayTimeUpdateTimeStamp = 0;
        }

        // just tell PowerManager to update its all wakelock
        notifyAppWakeLockConstrainedStateChanged(0, false);
    }


    private boolean updateAppWakeLockConstrainedState(String packageName, boolean constrained, int uid) {
        int userId = UserHandle.getUserId(uid);
        AppState appState = mAppStateInfoCollector.getAppState(packageName, userId);

        if (appState != null) {
            appState.updateAppWakeLockConstrainedState(constrained);
        } else {
            if (DEBUG) Slog.d(TAG, "Warning: packageName:" + packageName + " not in mAppStateInfoList");
        }
        return true;
    }


    private boolean inWhiteAppList(String pkgName) {
        if (pkgName == null) return true;

        try {
            if (mPowerControllerInternal.isWhitelistApp(pkgName)) {
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

    private boolean isSystemApp(AppState appState) {
        if (appState != null) {
            return ( (appState.mFlags & (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP |ApplicationInfo.FLAG_SYSTEM)) != 0
                || appState.mFlags == 0);
        } else {
            return true; // default
        }
    }

    private void handleLongPartialWakelock(WakelockHistory.WakelockInfo wakelock) {
        AppState appState = null;
        if (AUDIO_PACKAGE_NAME.equals(wakelock.pkgName) && isAudioOut(wakelock.tag)) {
            appState = mAppStateInfoCollector.getAppState(wakelock.uid);
            if (appState != null && !isSystemApp(appState)) {
                // correct the packageName, for audio hold the wakelock for this app
                wakelock.pkgName = appState.mPackageName;
            }
        } else {
            int userId = UserHandle.getUserId(wakelock.uid);
            appState = mAppStateInfoCollector.getAppState(wakelock.pkgName, userId);
        }

        if (appState == null) {
            if (DEBUG) Slog.d(TAG, "Warning: cannot find AppState for:" + wakelock.pkgName + " uid:" + wakelock.uid);
            return;
        }

        int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);
        if (/*PowerDataBaseControl.UNKNOWN != appType
            &&*/ (mPowerControllerInternal.isPlayingMusic(appState) || mPowerControllerInternal.isAppDoingDownload(appState))) {
            return;
        }

        if (PowerDataBaseControl.UNKNOWN == appType
            && isSystemApp(appState) ) {
            return;
        }

        mPowerControllerInternal.updateAppPowerConsumerType(appState.mPackageName, AppPowerSaveConfig.POWER_CONSUMER_TYPE_WAKELOCK,
            AppPowerSaveConfig.POWER_CONSUMER_TYPE_WAKELOCK);

        long idleDuration = (appState.mLastTimeUsed > 0 ? (SystemClock.elapsedRealtime() -appState.mLastTimeUsed) : -1);
        if (idleDuration < WAKELOCK_DURATION_THRESHOLD) {
            return;
        }

        boolean notifyUser = false;
        if (mWakelockHistory != null)
            notifyUser = mWakelockHistory.reportWakelock(wakelock);

        if (notifyUser) {
            // show a notification for user that background app has be stopping system from sleeping
            // for a long time
            List<String> apps = mWakelockHistory.getPackagesWithDuration(UserHandle.myUserId(),
                WAKELOCK_DURATION_THRESHOLD, false);
            if (apps != null) {

                final StringBuilder textBuilder = new StringBuilder();
                int index = 0;
                for (String app : apps){
                    if (DEBUG) Slog.d(TAG, "hold long duration partial wakelock:" + app + " uid:" + wakelock.uid);
                    makeNotificationText(textBuilder, app, index++);
                    mWakelockHistory.noteNotifyUser(app, UserHandle.myUserId());
                }

                if (index > 0) {
                    final String text = textBuilder.toString();
                    try {
                        sendNotification(text);
                    } catch (Exception e) {
                        Slog.d(TAG, "exception:" + e);
                    }
                }
            }
        }

    }

    private void makeNotificationText(StringBuilder rawBuilder, String appName, int index) {

        ApplicationInfo appInfo = null;
        String text = "";
        try{
            PackageManager pm = mContext.getPackageManager();
            appInfo = pm.getApplicationInfo(appName, 0);
            text = appInfo.loadLabel(pm).toString();
        } catch(PackageManager.NameNotFoundException e){
            Slog.e(TAG,"package name not found");
        }

        if (index == 0)
            rawBuilder.append(text);
        else
            rawBuilder.append(';').append(text);
    }

    private void sendNotification(String text) {
        sendNotificationCustom(text);
    }

    private void sendNotificationCustom(String text) {

        NotificationManager notificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            Slog.w(TAG, "NotificationManager is null.");
            return;
        }

        // get title
        if (mNotificationTitle == null) {
            mNotificationTitle = mContext.getString(R.string.pwctl_notification_title);
        }

        // get summary
        if (mNotificationSummary == null) {
            mNotificationSummary = mContext.getString(R.string.pwctl_notification_summary);
        }

        // intent for go to Power Setting page.
        if (mNotificationIntent == null) {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.POWER_USAGE_SUMMARY");
            mNotificationIntent = PendingIntent.getActivity(
                    mContext, R.drawable.ic_pwctl_notification,intent, 0);
        }

        // create RemoteViews
        if (mRemoteViews == null) {
            mRemoteViews = new RemoteViews(mContext.getPackageName(),
                    R.layout.layout_pwctl_notification);
            mRemoteViews.setImageViewResource(R.id.icon,
                    R.drawable.ic_pwctl_notification);
            mRemoteViews.setTextViewText(R.id.title, mNotificationTitle);
            mRemoteViews.setTextViewText(R.id.hint, mNotificationSummary);
        }
        mRemoteViews.setTextViewText(R.id.description, text);

        // build notification.
        if (mNotificationBuilder == null) {
            // Cache the Notification builder object.
            mNotificationBuilder = new Notification.Builder(mContext, PowerController.NOTIFICATION_CHANNEL);
            //mNotificationBuilder.setWhen(0);
            mNotificationBuilder.setSmallIcon(R.drawable.ic_pwctl_notification);
            mNotificationBuilder.setAutoCancel(true);
            mNotificationBuilder.setColor(mContext.getResources().getColor(
                            com.android.internal.R.color.system_notification_accent_color));

            mNotificationBuilder.setContent(mRemoteViews);
            mNotificationBuilder.setContentIntent(mNotificationIntent);
        }

        // notify user
        notificationManager.notifyAsUser(null, R.drawable.ic_pwctl_notification,
                mNotificationBuilder.build(), UserHandle.ALL);

    }


    private void noteAudioWakeLockAcquired(String tag, String packageName,
        int ownerUid, int ownerPid, WorkSource workSource) {

        if (DEBUG) Slog.d(TAG, "noteAudioWakeLockAcquired: workSource:" + workSource);

        // only care about the workSource
        if (workSource == null) return;
        if (!AUDIO_PACKAGE_NAME.equals(packageName) || Process.AUDIOSERVER_UID != ownerUid) return;

        //try {
            ArrayList<Integer> uids = new ArrayList();

            if (workSource != null) {
                int num = workSource.size();
                int count = 0;
                for (; count<num; count++) {
                    uids.add(workSource.get(count));
                }
            }
            if (DEBUG) Slog.d(TAG, "noteAudioWakeLockAcquired: tag: " + tag + " uid size:" + uids.size());

            if (isAudioIn(tag)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_AUDIOIN_WAKELOCK_UPDATED, uids));
            } else if (isAudioOut(tag)) {
                WakeLockInfo wakeLockInfo = new WakeLockInfo(tag, packageName, ownerUid, workSource);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_AUDIOOUT_WAKELOCK_UPDATED, wakeLockInfo));
            }
        //} catch (Exception e) {}
    }

    private void noteAudioWakeLockReleased(String tag, String packageName,
        int ownerUid, int ownerPid, WorkSource workSource) {

        // only care about the workSource
        if (!AUDIO_PACKAGE_NAME.equals(packageName) || Process.AUDIOSERVER_UID != ownerUid) return;

        if (DEBUG) Slog.d(TAG, "noteAudioWakeLockReleased: tag: " + tag);

        if (isAudioIn(tag)) {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_AUDIOIN_WAKELOCK_RELEASED));
        } else if (isAudioOut(tag)) {
            WakeLockInfo wakeLockInfo = new WakeLockInfo(tag, packageName, ownerUid, workSource);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_AUDIOOUT_WAKELOCK_RELEASED, wakeLockInfo));
        }

    }

    private void noteAudioWakeLockChanging(String tag, String packageName,
            int ownerUid, int ownerPid, WorkSource workSource,String newTag,
            String newPackageName, int newOwnerUid, int newOwnerPid, WorkSource newWorkSource) {

        // only care about the workSource
        if (!AUDIO_PACKAGE_NAME.equals(newPackageName) || Process.AUDIOSERVER_UID != newOwnerUid) return;

        //try {
            ArrayList<Integer> uids = new ArrayList();

            if (newWorkSource != null) {
                int num = newWorkSource.size();
                int count = 0;
                for (; count<num; count++) {
                    uids.add(newWorkSource.get(count));
                }
            }
            if (DEBUG) Slog.d(TAG, "noteAudioWakeLockChanging: tag: " + tag + " uid size:" + uids.size());

            if (isAudioIn(tag)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_AUDIOIN_WAKELOCK_UPDATED, uids));
            } else if (isAudioOut(tag)) {
                WakeLockInfo wakeLockInfo = new WakeLockInfo(tag, newPackageName, newOwnerUid, newWorkSource);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_AUDIOOUT_WAKELOCK_UPDATED, wakeLockInfo));
            }
        //} catch (Exception e) {}

    }

    private void handleAudioInWakeLockUpdated(ArrayList<Integer> uids) {

        // clear original first
        for (int i=0;i<mAudioInAppList.size();i++) {
            String pkgName = mAudioInAppList.valueAt(i);
            int uid = mAudioInAppList.keyAt(i);
            int userId = UserHandle.getUserId(uid);
            AppState appState = mAppStateInfoCollector.getAppState(pkgName, userId);
            if (appState == null || appState.mUid != uid) {
                if (DEBUG) Slog.d(TAG, "Warning: AudioIN: cannot find AppState for:" + pkgName + " uid:" + uid);
                continue;
            }

            // clear AppState.AUDIO_TYPE_IN
            int audioFlag = (appState.mAudioFlag&~AppState.AUDIO_TYPE_IN);
            appState.updateAudioState(audioFlag);

            mRecogAlgorithm.reportEvent(pkgName, appState.mUid, RecogAlgorithm.EVENT_TYPE_AUDIO_STATE, audioFlag);
        }
        mAudioInAppList.clear();

        // set new uids
        for (int i=0; i<uids.size(); i++) {
            int uid = uids.get(i);
            AppState appState = mAppStateInfoCollector.getAppState(uid);
            if (appState == null || appState.mUid != uid) {
                if (DEBUG) {
                    Slog.d(TAG, "Warning: AudioIN: cannot find AppState for: uid:" + uid);
                    mAppStateInfoCollector.dump();
                }
                continue;
            }

            // set AppState.AUDIO_TYPE_IN
            int audioFlag = (appState.mAudioFlag&~AppState.AUDIO_TYPE_IN) | AppState.AUDIO_TYPE_IN;
            appState.updateAudioState(audioFlag);

            mRecogAlgorithm.reportEvent(appState.mPackageName, appState.mUid, RecogAlgorithm.EVENT_TYPE_AUDIO_STATE, audioFlag);

            // add to mAudioInAppList
            mAudioInAppList.put(appState.mUid, appState.mPackageName);
        }

        if (DEBUG) {
            Slog.d(TAG, " Apps that is AUDIO IN:");
            for (int i=0;i<mAudioInAppList.size();i++)
                Slog.d(TAG, "pkgName:" + mAudioInAppList.valueAt(i) + " uid:" + mAudioInAppList.keyAt(i));
        }

        // tell the PowerController to re-check all App info
        mPowerControllerInternal.reCheckAllAppInfoDelayed(0);
    }

    private ArrayList<Integer> getAudioOutUids() {
        ArrayList<Integer> uids = new ArrayList();
        final int numWakeLocks = mAudioOutWakeLocks.size();
        for (int i = 0; i < numWakeLocks; i++) {
            final WakeLockInfo savedWakeLockInfo = mAudioOutWakeLocks.get(i);
            if (savedWakeLockInfo != null && savedWakeLockInfo.mWorkSource != null) {
                int num = savedWakeLockInfo.mWorkSource.size();
                int count = 0;
                for (; count<num; count++) {
                    uids.add(savedWakeLockInfo.mWorkSource.get(count));
                }
            }
        }
        return uids;
    }

    private void handleAudioOutWakeLockUpdated(WakeLockInfo wakeLockInfo) {
        if (wakeLockInfo == null) {
            Slog.w(TAG, " null wakeLockInfo");
            return;
        }

        // update the saved audio out wakelock info
        final int numWakeLocks = mAudioOutWakeLocks.size();
        boolean found = false;
        for (int i = 0; i < numWakeLocks; i++) {
            WakeLockInfo savedWakeLockInfo = mAudioOutWakeLocks.get(i);
            if (savedWakeLockInfo != null
                && savedWakeLockInfo.mPackageName == wakeLockInfo.mPackageName
                && savedWakeLockInfo.mTag == wakeLockInfo.mTag
                && savedWakeLockInfo.mUid == wakeLockInfo.mUid) {
                found = true;
                savedWakeLockInfo.mWorkSource = copyWorkSource(wakeLockInfo.mWorkSource);
                break;
            }
        }

        if (!found) {
            mAudioOutWakeLocks.add(wakeLockInfo);
        }

        ArrayList<Integer> uids = getAudioOutUids();
        if (DEBUG) Slog.d(TAG, "handleAudioOutWakeLockUpdated: uid size:" + uids.size());

        // clear original first
        for (int i=0;i<mAudioOutAppList.size();i++) {
            String pkgName = mAudioOutAppList.valueAt(i);
            int uid = mAudioOutAppList.keyAt(i);
            int userId = UserHandle.getUserId(uid);
            AppState appState = mAppStateInfoCollector.getAppState(pkgName, userId);
            if (appState == null || appState.mUid != uid) {
                if (DEBUG) Slog.d(TAG, "Warning: AudioOUT: cannot find AppState for:" + pkgName + " uid:" + uid);
                continue;
            }

            // clear AppState.AUDIO_TYPE_OUT
            int audioFlag = (appState.mAudioFlag&~AppState.AUDIO_TYPE_OUT);
            appState.updateAudioState(audioFlag);

            // if not in new uids
            if (!uids.contains(appState.mUid))
                mRecogAlgorithm.reportEvent(pkgName, appState.mUid, RecogAlgorithm.EVENT_TYPE_AUDIO_STATE, audioFlag);

        }
        mAudioOutAppList.clear();

        boolean changed = false;
        // set new uids
        for (int i=0; i<uids.size(); i++) {
            int uid = uids.get(i);
            AppState appState = mAppStateInfoCollector.getAppState(uid);
            if (appState == null || appState.mUid != uid) {
                if (DEBUG) {
                    Slog.d(TAG, "Warning: AudioOUT cannot find AppState for: uid:" + uid);
                    mAppStateInfoCollector.dump();
                }
                continue;
            }

            // set AppState.AUDIO_TYPE_OUT
            int audioFlag = (appState.mAudioFlag&~AppState.AUDIO_TYPE_OUT) | AppState.AUDIO_TYPE_OUT;
            appState.updateAudioState(audioFlag);

            mRecogAlgorithm.reportEvent(appState.mPackageName, appState.mUid, RecogAlgorithm.EVENT_TYPE_AUDIO_STATE, audioFlag);

            // add to mAudioOutAppList
            mAudioOutAppList.put(appState.mUid, appState.mPackageName);
            changed = true;
        }

        if (DEBUG) {
            Slog.d(TAG, " Apps that is AUDIO OUT:" + mAudioOutAppList.size());
            for (int i=0;i<mAudioOutAppList.size();i++)
                Slog.d(TAG, "pkgName:" + mAudioOutAppList.valueAt(i) + " uid:" + mAudioOutAppList.keyAt(i));
        }

        // tell the PowerController to re-check all App info
        if (changed) mPowerControllerInternal.reCheckAllAppInfoDelayed(10*1000);
    }

    private void handleAudioInWakeLockReleased() {
        for (int i=0;i<mAudioInAppList.size();i++) {
            String pkgName = mAudioInAppList.valueAt(i);
            int uid = mAudioInAppList.keyAt(i);
            int userId = UserHandle.getUserId(uid);
            AppState appState = mAppStateInfoCollector.getAppState(pkgName, userId);
            if (appState == null || appState.mUid != uid) {
                if (DEBUG) Slog.d(TAG, "Warning: AudioIN: cannot find AppState for:" + pkgName + " uid:" + uid);
                continue;
            }

            // clear AppState.AUDIO_TYPE_IN
            int audioFlag = (appState.mAudioFlag&~AppState.AUDIO_TYPE_IN);
            appState.updateAudioState(audioFlag);

            mRecogAlgorithm.reportEvent(pkgName, appState.mUid, RecogAlgorithm.EVENT_TYPE_AUDIO_STATE, audioFlag);
        }
        mAudioInAppList.clear();
    }

    private void handleAudioOutWakeLockReleased(WakeLockInfo wakeLockInfo) {
        if (wakeLockInfo == null) {
            Slog.w(TAG, " null wakeLockInfo");
            return;
        }

        // update the saved audio out wakelock info
        final int numWakeLocks = mAudioOutWakeLocks.size();
        boolean found = false;
        int index = -1;
        for (int i = 0; i < numWakeLocks; i++) {
            WakeLockInfo savedWakeLockInfo = mAudioOutWakeLocks.get(i);
            if (savedWakeLockInfo != null
                && savedWakeLockInfo.mPackageName == wakeLockInfo.mPackageName
                && savedWakeLockInfo.mTag == wakeLockInfo.mTag
                && savedWakeLockInfo.mUid == wakeLockInfo.mUid) {
                found = true;
                index = i;
                break;
            }
        }

        if (found && index >=0 && index < numWakeLocks) {
            mAudioOutWakeLocks.remove(index);
        }

        for (int i=0;i<mAudioOutAppList.size();i++) {
            String pkgName = mAudioOutAppList.valueAt(i);
            int uid = mAudioOutAppList.keyAt(i);
            int userId = UserHandle.getUserId(uid);
            AppState appState = mAppStateInfoCollector.getAppState(pkgName, userId);
            if (appState == null || appState.mUid != uid) {
                if (DEBUG) Slog.d(TAG, "Warning: AudioOUT: cannot find AppState for:" + pkgName + " uid:" + uid);
                continue;
            }

            // clear AppState.AUDIO_TYPE_OUT
            int audioFlag = (appState.mAudioFlag&~AppState.AUDIO_TYPE_OUT);
            appState.updateAudioState(audioFlag);

            mRecogAlgorithm.reportEvent(pkgName, appState.mUid, RecogAlgorithm.EVENT_TYPE_AUDIO_STATE, audioFlag);

        }
        mAudioOutAppList.clear();

        ArrayList<Integer> uids = getAudioOutUids();
        if (DEBUG) Slog.d(TAG, "handleAudioOutWakeLockReleased: uid size:" + uids.size());
        // set new uids
        for (int i=0; i<uids.size(); i++) {
            int uid = uids.get(i);
            AppState appState = mAppStateInfoCollector.getAppState(uid);
            if (appState == null || appState.mUid != uid) {
                if (DEBUG) {
                    Slog.d(TAG, "Warning: AudioOUT cannot find AppState for: uid:" + uid);
                    mAppStateInfoCollector.dump();
                }
                continue;
            }

            // set AppState.AUDIO_TYPE_OUT
            int audioFlag = (appState.mAudioFlag&~AppState.AUDIO_TYPE_OUT) | AppState.AUDIO_TYPE_OUT;
            appState.updateAudioState(audioFlag);

            mRecogAlgorithm.reportEvent(appState.mPackageName, appState.mUid, RecogAlgorithm.EVENT_TYPE_AUDIO_STATE, audioFlag);

            // add to mAudioOutAppList
            mAudioOutAppList.put(appState.mUid, appState.mPackageName);
        }

        if (DEBUG) {
            Slog.d(TAG, " handleAudioOutWakeLockReleased : Apps that is AUDIO OUT:" + mAudioOutAppList.size());
            for (int i=0;i<mAudioOutAppList.size();i++)
                Slog.d(TAG, "pkgName:" + mAudioOutAppList.valueAt(i) + " uid:" + mAudioOutAppList.keyAt(i));
        }

    }

    private boolean isAudioIn(String tag) {
        if (AUDIO_IN_WAKELOCK_TAG.equals(tag)) {
            return true;
        }
        return false;
    }

    private boolean isAudioOut(String tag) {
        if (!isAudioIn(tag)) {
            return true;
        }

        if (AUDIO_OUT_WAKELOCK_TAG.equals(tag)) {
            return true;
        }

        if (tag != null
            && tag.startsWith(AUDIO_OUT_WAKELOCK_TAG)
            && tag.contains("AudioOut")) {
            return true;
        }

        if (AUDIO_OUT_WAKELOCK_OFFLOAD_TAG.equals(tag)) {
            return true;
        }

        return false;
    }

    private boolean isDebug(){
        return Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug");
    }

    private ArrayMap<String, Integer> getNewWakeLockAppList(int userId) {
        ArrayMap<String, Integer> mNewWakeLockAppList = mNewWakeLockAppListForUsers.get(userId);
        if (mNewWakeLockAppList == null) {
            mNewWakeLockAppList = new ArrayMap<>();
            mNewWakeLockAppListForUsers.put(userId, mNewWakeLockAppList);
        }
        return mNewWakeLockAppList;
    }
}
