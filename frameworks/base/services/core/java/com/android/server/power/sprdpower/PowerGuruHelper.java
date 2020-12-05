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
import android.os.PowerManagerInternal;
import android.os.sprdpower.PowerManagerEx;
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

// Class PowerGuruHelper to make decision of What apps should be put
// into the PowerGuru Constrained App List
public class PowerGuruHelper extends PowerSaveHelper{

    private static final String TAG = "PowerController.Guru";

    private final boolean DEBUG_LOG = Util.getDebugLog();
    private final boolean DEBUG = isDebug() || DEBUG_LOG;
    private final boolean DEBUG_MORE = false;
    private final boolean TEST = PowerController.TEST;

    private final boolean mPowerControllerGuruEnabled = (1 == SystemProperties.getInt(PowerController.POWER_CONTROLLER_POWERGURU_ENABLE, 1));

    private final boolean mEnabledOnlySaveMode =  (1 == SystemProperties.getInt(PowerController.PWCTL_ENABLE_ONLY_SAVEMODE, 0));

    // IF app has wakeup-alarms per hour more than this value,
    // then see this app as a frequently wake up app
    private final long FREQUENTLY_WAKEUP_ALARM_THRESHOLD =  6;

    //Config Value
    //Max time in app bg state before to be added to powerguru blacklist
    private long POWERGURU_TIMEOUT =  (TEST ? 5 * 60 * 1000L : 20 * 60 * 1000L);

    private final IActivityManager mActivityManager;
    private Handler mHandler;

    //Reference to services
    private  IPowerGuru mPowerGuruService;
    private final PowerGuruService.LocalService mPowerGuruInternal;

    private List<String> mWhiteList;
    private List<String> mBlackList;
    //private List<String> mNewBlackList = new ArrayList<String>();
    // Applist waiting to add to powerGuru for all users and all packages  <pkgName, userId>
    private SparseArray<ArrayMap<String,Integer>> mNewBlackListForUsers = new SparseArray<>();

    private boolean mStandby = false;

    private static final int DEFAULT_POWER_MODE = PowerManagerEx.MODE_SMART;
    private int mPowerSaveMode = DEFAULT_POWER_MODE;

    // This white list is used for some app like CTS
    private final String[] mInternalWhiteAppList = new String[] {
        "android.app.cts",
        "com.android.cts",
        "android.icu.dev.test.util"
    };

    public PowerGuruHelper(Context context, IActivityManager activityManager, Handler handler) {
        super(context, AppPowerSaveConfig.MASK_ALARM);

        mActivityManager = activityManager;
        mHandler = new InternalHandler(handler.getLooper());

        mPowerGuruService = IPowerGuru.Stub.asInterface(ServiceManager.getService(Context.POWERGURU_SERVICE));
        mPowerGuruInternal = LocalServices.getService(PowerGuruService.LocalService.class);

        // white & black list
        mWhiteList = mPowerControllerInternal.getWhiteList(AppPowerSaveConfig.ConfigType.TYPE_ALARM.value);
        mBlackList = mPowerControllerInternal.getBlackList(AppPowerSaveConfig.ConfigType.TYPE_ALARM.value);

        // set constants values
        POWERGURU_TIMEOUT = mConstants.POWERGURU_INACTIVE_TIMEOUT;
        if (DEBUG) Slog.d(TAG, "POWERGURU_TIMEOUT:" + POWERGURU_TIMEOUT);

        // for bug#777546
        if (mPowerGuruInternal != null) {
            mPowerGuruInternal.registerWakeupAlarmObserver(
                    new PowerGuruService.WakeupAlarmListener() {
                @Override
                public void onWakeupAlarmFired(String pkgName, int userId, boolean screenOn) {
                    // Slog.d(TAG, "onWakeupAlarmFired(" + pkgName + ")");
                    onWakeupAlarm(pkgName, userId, screenOn);
                }
            });
        } else {
            Slog.w(TAG, "Warning: PowerGuruService is not enabled!");
        }
    }

    //Message define
    static final int MSG_WAKEUP_ALARM = 0;


    class InternalHandler extends Handler {
        InternalHandler(Looper looper) {
            super(looper);
        }

        String Msg2Str(int msg) {
            final String msgStr[] = {"MSG_WAKEUP_ALARM"
            };

            if ((0 <= msg) && (msg < msgStr.length))
            return msgStr[msg];
            else
            return "Unknown message: " + msg;
        }

        @Override public void handleMessage(Message msg) {
            if (DEBUG) Slog.d(TAG, "handleMessage(" + Msg2Str(msg.what) + ")");

            switch (msg.what) {
            case MSG_WAKEUP_ALARM:
                handleWakeupAlarm((String)msg.obj, msg.arg1, (msg.arg2==1?true:false));
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
                POWERGURU_TIMEOUT = mConstants.POWERGURU_INACTIVE_TIMEOUT_LOWPOWER; //default (5 * 60 * 1000L);
                break;
            case PowerManagerEx.MODE_PERFORMANCE:
            case PowerManagerEx.MODE_SMART:
                POWERGURU_TIMEOUT = mConstants.POWERGURU_INACTIVE_TIMEOUT; //default (20 * 60 * 1000L);
                break;
        }
    }

    @Override
    void onPowerSaveConfigChanged(int configType, String appName, int oldValue, int newValue, boolean init) {
        if (AppPowerSaveConfig.ConfigType.TYPE_ALARM.value != configType)
            return;

        Slog.d(TAG, "onPowerSaveConfigChanged(), init: " + init
            + ", configType: " + AppPowerSaveConfig.ConfigType2Str(configType)
            + ", appName: " + appName
            + ", oldValue: " + AppPowerSaveConfig.ConfigValue2Str(oldValue) + "(" + oldValue
            + "), newValue: " + AppPowerSaveConfig.ConfigValue2Str(newValue) + "(" + newValue +")");

        if (mPowerGuruService == null) return;
        try {
            if (init) {
                if (AppPowerSaveConfig.VALUE_NO_OPTIMIZE == newValue) {
                    mPowerGuruService.addWhiteAppfromList(appName);
                }
            } else {

                if (AppPowerSaveConfig.VALUE_NO_OPTIMIZE == newValue) {
                    mPowerGuruService.addWhiteAppfromList(appName);
                } else {
                    mPowerGuruService.delWhiteAppfromList(appName);
                }
            }
        } catch (Exception e) {}
    }

    @Override
    boolean updateAppEvaluatedTimeStamp(AppState appState) {
        boolean bConstrained = canBeConstrained(appState);
        return updateAppEvaluatedTimeStamp(appState, !bConstrained);
    }

    @Override
    boolean updateAppEvaluatedTimeStamp(AppState appState, boolean clear) {
        if (clear) {
            appState.mBeginPowerGuruEvaluatedTimeStamp = 0;
            if (DEBUG_MORE) Slog.d(TAG, "upatePowerGuruEvaluatedTimeStamp:" + appState.mPackageName
                + " clear beginPowerGuruEvaluatedTimeStamp");
        } else if (appState.mBeginPowerGuruEvaluatedTimeStamp == 0) {
            appState.mBeginPowerGuruEvaluatedTimeStamp = SystemClock.elapsedRealtime();
            if (DEBUG_MORE) Slog.d(TAG, "upatePowerGuruEvaluatedTimeStamp:" + appState.mPackageName
                + " beginPowerGuruEvaluatedTimeStamp:" + appState.mBeginPowerGuruEvaluatedTimeStamp);
        }
        return true;
    }

    /*
     * App cannot be constrained for powerGuru:
     * 1. system app (app.uid <= Process.FIRST_APPLICATION_UID, this is enough ???)
     * 2. app in doze whitelist
     * 3. carrier app
     */
    private boolean canBeConstrained(AppState appState) {
        int procState = appState.mProcState;

        if (DEBUG_MORE) Slog.d(TAG, "powerGuruCanBeConstrained: uid:" + appState.mUid
            + " :" + appState.mPackageName + " procState:" + Util.ProcState2Str(procState)
            + " Group State:" + Util.AppState2Str(appState.mState));

        if ((appState.mAudioFlag & AppState.AUDIO_TYPE_IN) != 0) {
            if (DEBUG) Slog.d(TAG, "powerGuruCanBeConstrained: " + appState.mPackageName + " soundRecoder!!");
            return false;
        }

        if (inWhiteAppList(appState.mPackageName)) {
            if (DEBUG) Slog.d(TAG, "powerGuruCanBeConstrained: " + appState.mPackageName + " in my whitelist");
            return false;
        } else if (mBlackList.contains(appState.mPackageName)) {
            if (DEBUG) Slog.d(TAG, "powerGuruCanBeConstrained: " + appState.mPackageName + " in my blacklist");
            return true;
        }

        // Note: Bug 696448 alarm fail -->BEG
        // system app can not be constrained
        if (appState.mUid <= Process.FIRST_APPLICATION_UID
            || isSystemApp(appState))
            return false;
        // Note: Bug 696448 alarm fail <--END

        // Carrier App can not be constrained
        if (isCarrierApp(appState.mPackageName)) {
            if (DEBUG) Slog.d(TAG, "powerGuruCanBeConstrained: " + appState.mPackageName + " a Carrier App");
            return false;
        }

        return true;
    }

    /*
     * handle the case:
     * app move from BG2FG, and it can not be constrained again.
     * at this time should remove from the PowerGuru Constrained App list
     */
    @Override
    void updateAppStateChanged(AppState appState, int stateChange) {
        if (stateChange != PowerController.STATE_BG2FG) return;
        if (!canBeConstrained(appState) && appState.mInPowerGuruBlackList) {
            if (DEBUG) Slog.d(TAG, "packageName:" + appState.mPackageName + " remove from PowerGuru BlackList");

            try {
                updatePowerGuruConstrainedList(appState.mPackageName, false);
                appState.updateAppPowerGuruState(false);
            } catch (Exception e) {
            // fall through
            }

            ArrayMap<String, Integer> mNewBlackList = getNewBlackList(appState.mUserId);
            mNewBlackList.remove(appState.mUid);
        }
    }

    /*
     * check this app should be constrained or not
     * return true if this app should be constrained, and is add to mNewPowerGuruBlackList
     * others return false
     */
    @Override
    boolean checkAppStateInfo(AppState appState, final long nowELAPSED) {
        ArrayMap<String, Integer> mNewBlackList = getNewBlackList(appState.mUserId);

        boolean bChanged = false;
        if (canBeConstrained(appState)) {
            long diffTime = nowELAPSED - appState.mBeginPowerGuruEvaluatedTimeStamp;
            if ((POWERGURU_TIMEOUT < diffTime && !appState.mInPowerGuruBlackList)
                || (!appState.mInPowerGuruBlackList && inBlackAppList(appState.mPackageName))
                || (!appState.mInPowerGuruBlackList && constraintSatisfied(appState, nowELAPSED))){
                bChanged = true;
                mNewBlackList.put(appState.mPackageName, appState.mUserId);
            }
        } else {
        // check if procState changed, and became can not be constrainted
        // if it already in PowerGuru black list, then update it.
            if (appState.mInPowerGuruBlackList) {
                if (DEBUG) Slog.d(TAG, "packageName:" + appState.mPackageName + " remove from PowerGuru BlackList");

                try {
                    updatePowerGuruConstrainedList(appState.mPackageName, false);
                    appState.updateAppPowerGuruState(false);
                } catch (Exception e) {
                // fall through
                }
            }
        }

        // just for test!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        //int wakeupAlarmCount = mPowerControllerInternal.getWakeupAlarmCount(appState.mPackageName);
        //if (DEBUG) Slog.d(TAG, "packageName:" + appState.mPackageName + " wake up alarm count:" + wakeupAlarmCount);
        // just for test end!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

        return bChanged;
    }

    // add the apps to the PowerGuru Constrained App List
    @Override
    void applyConstrain() {
        if (!mStandby) {
            if (DEBUG) Slog.d(TAG, " system becomes non-standby before applying constrain!");
            clearConstrainAppList();
            return;
        }

        for (int index=mNewBlackListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, Integer> mNewBlackList = mNewBlackListForUsers.valueAt(index);

            for (int i=0;i<mNewBlackList.size();i++) {
                try {
                    if (DEBUG) Slog.d(TAG, "packageName:" + mNewBlackList.keyAt(i)
                        +" userId:" + mNewBlackList.valueAt(i) + " add to PowerGuru BlackList");
                    updatePowerGuruConstrainedList(mNewBlackList.keyAt(i), true);
                    updateAppPowerGuruState(mNewBlackList.keyAt(i), true, mNewBlackList.valueAt(i));
                } catch (Exception e) {
                    // fall through
                }
            }
            mNewBlackList.clear();

        }
    }

    // clear the app from the PowerGuru Constrained App List
    @Override
    void clearConstrain(AppState appState) {
        if (appState.mInPowerGuruBlackList) {
            updatePowerGuruConstrainedList(appState.mPackageName, false);
            appState.updateAppPowerGuruState(false);
        }

        // clear Time Stamp
        updateAppEvaluatedTimeStamp(appState, true);
    }

    @Override
    void onAppUsageStateChanged(String packName, int uid, int state) {
        if (DEBUG) Slog.d(TAG, "onAppUsageStateChanged:" + packName + " new state:" + state);
    }

    /*
     * Note device is enter standby state ( screen off / unpluged)
     */
    @Override
    void noteDeviceStateChanged(boolean bStandby, final long nowELAPSED) {
        if (DEBUG) Slog.d(TAG, "noteDeviceStateChanged bStandby:" + bStandby);
        mStandby = bStandby;
        if (!bStandby) {
            clearConstrainAppList();
        }
    }

    private void clearConstrainAppList() {
        for (int index=mNewBlackListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, Integer> mNewBlackList = mNewBlackListForUsers.valueAt(index);
            mNewBlackList.clear();
        }
    }

    // wrap API for PowerGuruService
    void updatePowerGuruConstrainedList(String packageName, boolean add) {
        if (!mPowerControllerGuruEnabled || mPowerGuruService == null) return;

        if (mPowerSaveMode == PowerManagerEx.MODE_NONE) {
            return;
        }

        if (mEnabledOnlySaveMode && !isSaveMode(mPowerSaveMode)) {
            //if (DEBUG) Slog.d(TAG, "updatePowerGuruConstrainedList return for only enable in save mode!");
            return;
        }

        try {
            if (add) {
                mPowerGuruService.addConstrainedListAppBySystem(packageName);
            } else {
                mPowerGuruService.removeConstrainedListAppBySystem(packageName);
            }
        } catch (RemoteException e) {
            // fall through
        }
    }

    private boolean updateAppPowerGuruState(String packageName, boolean inBlacklist, int userId) {
        AppState appState = mAppStateInfoCollector.getAppState(packageName, userId);
        if (appState != null) {
            appState.updateAppPowerGuruState(inBlacklist);
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

    private boolean isCarrierApp(String packageName) {
        TelephonyManager telephonyManager =  (TelephonyManager)mContext.getSystemService(mContext.TELEPHONY_SERVICE);
        return telephonyManager.checkCarrierPrivilegesForPackageAnyPhone(packageName)
                    == TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS;
    }

    private boolean isSystemApp(AppState appState) {
        if (appState != null) {
            return ( (appState.mFlags & (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP |ApplicationInfo.FLAG_SYSTEM)) != 0
                || appState.mFlags == 0);
        } else {
            return true; // default
        }
    }

    private boolean constraintSatisfied(AppState appState, final long nowELAPSED) {
        long diffTime = nowELAPSED - appState.mBeginPowerGuruEvaluatedTimeStamp;

        // for known type app, can be restrict immediately after standby
        int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);
        if (PowerDataBaseControl.UNKNOWN != appType
            && diffTime > 0
            && appState.mBeginPowerGuruEvaluatedTimeStamp > 0) {
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

    private void onWakeupAlarm(String pkgName, int userId, boolean screenOn) {
        // if (DEBUG) Slog.d(TAG, "onWakeupAlarm: " + pkgName + " for userId:" + userId);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_WAKEUP_ALARM, userId, (screenOn?1:0), pkgName));
    }

    private void handleWakeupAlarm(String pkgName, int userId, boolean screenOn) {
        // if (DEBUG) Slog.d(TAG, "handleWakeupAlarm: " + pkgName + " for userId:" + userId);

        AppState appState = mAppStateInfoCollector.getAppState(pkgName, userId);;
        if (appState != null) {
            if (isSystemApp(appState)) return;

            appState.incWakeupAlarm();
            int alarmCount = appState.getWakeupAlarmAvgCount();
            if (DEBUG) Slog.d(TAG, "alarmCount: " + alarmCount + " for pkgName:" + pkgName);
            if (alarmCount > FREQUENTLY_WAKEUP_ALARM_THRESHOLD) {
                if (DEBUG) Slog.d(TAG, "frequently wakeup Alarm: " + alarmCount + " pkgName:" + pkgName);
                mPowerControllerInternal.updateAppPowerConsumerType(pkgName, AppPowerSaveConfig.POWER_CONSUMER_TYPE_ALARM,
                    AppPowerSaveConfig.POWER_CONSUMER_TYPE_ALARM);
            }
        }
    }

    public String toString() {
        String out = TAG + "\n";
        out += "[mWhiteList]: " + mWhiteList + "\n";
        out += "[mBlackList]: " + mBlackList + "\n";
        return out;
    }

    private boolean isDebug(){
        return Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug");
    }

    private ArrayMap<String, Integer> getNewBlackList(int userId) {
        ArrayMap<String, Integer> mNewBlackList = mNewBlackListForUsers.get(userId);
        if (mNewBlackList == null) {
            mNewBlackList = new ArrayMap<>();
            mNewBlackListForUsers.put(userId, mNewBlackList);
        }
        return mNewBlackList;
    }
}
