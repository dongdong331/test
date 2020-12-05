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
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
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
import com.android.server.power.sprdpower.AbsDeviceIdleController;
import com.android.server.power.sprdpower.PMSFactory;

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

//SPRD:Bug 814570 About apps auto run BEG
import com.android.internal.R;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.WindowManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.app.Dialog;
import com.android.server.policy.PhoneWindowManager;
//SPRD:Bug 814570 About apps auto run END


import static android.provider.Telephony.Sms.Intents.SMS_DELIVER_ACTION;
import static android.accounts.AccountManager.ACTION_AUTHENTICATOR_INTENT;

import android.os.sprdpower.Util;

public class BackgroundCleanHelper extends PowerSaveHelper {

    private static final String TAG = "PowerController.BgClean";

    private static final int FOREGROUND_THRESHOLD_STATE =
            ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;

    private final boolean DEBUG_LOG = Util.getDebugLog();
    private final boolean DEBUG = isDebug() || DEBUG_LOG;
    private final boolean DEBUG_MORE = false;
    private final boolean TEST = PowerController.TEST;


    // app start reason, should sync with the ActivityManagerService
    // that calling judgeStartAllowLocked
    private static final String REASON_START_SERVICE = "start-service";
    private static final String REASON_BIND_SERVICE = "bind-service";
    private static final String REASON_CONTENT_PROVIDER = "contentprovider";
    private static final String REASON_BROADCAST = "send-broadcast";
    private static final String REASON_START_ACTIVITY = "start-activity";
    private static final String REASON_KILL_APP = "lock screen and clean up apps";

    private static final int LAUNCH_STATE_AUTO = 0;
    private static final int LAUNCH_STATE_ALLOW = 1;
    private static final int LAUNCH_STATE_DENY = 2;

    // using disable component instead of disable the whole app
    // when enter/exit ultra-saving mode. See bug#819868
    private static final boolean USE_COMPONENT = true;

    // the idle time (ms) for app that can be stopped
    private long FORCE_STOP_IDLE_THRESHOLD1 = (TEST ? 10 * 60 * 1000L : 60 * 60 * 1000L);
    private long FORCE_STOP_IDLE_THRESHOLD2 = (TEST ? 20 * 60 * 1000L : 120 * 60 * 1000L);
    private long FORCE_STOP_IDLE_THRESHOLD3 = (TEST ? 30 * 60 * 1000L : 240 * 60 * 1000L);

    // if a non-top app has been idle for DENY_START_APP_THRESHOLD
    // then don't allow this app to start other 3-party app
    private final long DENY_START_APP_THRESHOLD = (TEST ? 5 * 60 * 1000L : 30 * 60 * 1000L);

    private final int DEFAULT_MAX_LAUNCHED_APP_KEEP = 3; //including the launcher app

    // when doing bg app clean, max launched app that can be kept
    private int MAX_LAUNCHED_APP_KEEP = DEFAULT_MAX_LAUNCHED_APP_KEEP; //including the launcher app

    // if in lowpower mode, it will do clean when stanby time beyond the threshod
    private final long LOWPOWER_DOCLEAN_THRESHOLD = (TEST ? 30 * 1000L : 15 * 60 * 1000L);

    // timeout before do clean
    private long DOCLEAN_TIMEOUT = 60 * 1000L;

    // use property to control this feature
    private final boolean mEnabled = (1 == SystemProperties.getInt(PowerController.POWER_CONTROLLER_BGCLEAN_ENABLE, 1));

    private final boolean mEnabledOnlySaveMode =  (1 == SystemProperties.getInt(PowerController.PWCTL_ENABLE_ONLY_SAVEMODE, 0));

    //SPRD:Bug 814570 About apps auto run BEG
    private boolean mFirstStartedNotifityEnabled = SystemProperties.getBoolean("persist.sys.pwctl.bgc.dialog", false);
    //SPRD:Bug 814570 About apps auto run END

    private boolean mCleanByUsageEnabled = SystemProperties.getBoolean("persist.sys.pwctl.bgc.usage", false);
    private boolean mAutoStartRecognizeEnabled = SystemProperties.getBoolean("persist.sys.pwctl.bgc.recg", false);

    private AlarmManager mAlarmManager;
    private UserManager mUserManager;
    private int mCurrentUserId = 0;

    private final IActivityManager mActivityManager;
    private Handler mHandler;
    private boolean mAlarmSet = false;

    private boolean mBootCompleted = false;

    //Reference to services
    // private final UsageStatsManagerInternal mUsageStatsInternal;
    // private final UsageStatsManager mUsageStatsManager;
    // private final IDeviceIdleController mDeviceIdleController;

    // private final IPowerGuru mPowerGuruService;
    // private final PowerManagerInternal mLocalPowerManager;

    // private final AudioManager mAudioManager;

    private SystemPreferredConfig mSystemPreferredConfig;
    private ThirdpartyPush mThirdpartyPush;

    private AutoStartRecognize mAutoStartRecognize;

    // Update when device state changed
    // private Map<String, UsageStats> mLastAppStats = new ArrayMap<>();
    // private Map<String, UsageStats> mAppStats = new ArrayMap<>();

    // Apps that are visible
    private ArrayList<String> mVisibleAppList = new ArrayList<>();
    private ArrayList<String> mRemovedVisibleAppList = new ArrayList<>();

    //private Map<String, PackageInfo> mInstalledAppList = new ArrayMap<>();
    private SparseArray<ArrayMap<String, PackageInfo>> mInstalledAppListForUsers = new SparseArray<>();

    // the install third-party service that is app with out launcher entry
    private ArrayList<String> mInstalledServiceList = new ArrayList<>();

    // the install third-party admin app list, such com.baidu.appsearch
    private ArrayList<String> mInstalledAdminAppList = new ArrayList<>();

    // Apps that can be stopped
    //private ArrayList<String> mForceStopAppList = new ArrayList<>();
    private SparseArray<ArrayMap<String,Integer>> mForceStopAppListForUsers = new SparseArray<>(); //<pkgName, userId>

    // Apps that have be stopped during this standby period
    // it will clear when exit standby state
    //private ArrayList<String> mStoppedAppList = new ArrayList<>();
    private SparseArray<ArrayMap<String,Integer>> mStoppedAppListForUsers = new SparseArray<>(); //<pkgName, userId>

    // list save the first caller info, the key is the target app
    private final ArrayMap<String, CallerAppInfo> mFirstCallerAppList = new ArrayMap<>();

    // list save the notification intent info, the key is the target app
    private final ArrayMap<String, ArrayList<Intent>> mNotificationIntentList = new ArrayMap<>();

    // to recode the system elapsed time when starting standby
    private long mStandbyStartTime = 0;

    // wakefulness, true for wake, false for sleep (screen off)
    private boolean mWakefulnessOn = true;
    private boolean mWakefulnessChangingOn = true;
    private boolean mScreenOn = true;

    private ArrayList<String> mLauncherAppList = new ArrayList<>();
    private String mCurrentHomeLauncher;


    // using disable component instead of disable the whole app
    // when enter/exit ultra-saving mode. See bug#819868
    // to save the component that will be disable/enable during switch
    private ArrayList<String> mLauncherAppComponentList = new ArrayList<>();

    // save the run app list before disable it when enter ultra-saving mode
    private List<RunningAppProcessInfo> mRunAppList;

    private Intent mHomeIntent;

    // Input Method App list
    private ArrayList<String> mEnabledInputMethodAppList = new ArrayList<>();
    private boolean mNeedReloadEnabledInputMethodAppList = true;

    // the pkgName of the default Wallpaper service
    private String mDefaultWallpaperService;

    // This white list is used for some app like CTS
    private final String[] mInternalWhiteAppList = new String[] {
        "com.google.android.gms",
        "com.google.android.gsf",
        "android.app.cts",
        "com.android.cts",
        "android.icu.dev.test.util"
    };

    // app list that will be consider as not be constraint for Associate Launching
    private final String[] mExceptionAppListForAssociateLaunch = new String[] {
        "com.tencent.mm",
        "com.facebook.katana",
        "com.facebook.orca"
    };

    // specific system apps that should be stop in ultrasaving mode
    private final String[] mBlackAppListForUltraSaving = new String[] {
        "com.android.musi",
        "com.android.soundrecorder",
        "com.android.browser",
        "com.android.email",
        "com.android.camera2",
        "com.sprd.sprdnote",
        "com.android.fmradio",
        "com.sprd.fileexplorer",
        "com.android.calculator2",
        "com.android.gallery3d",
        "com.google.android.apps.maps",
        "com.google.android.gm.lite",
        "com.android.chrome",
        "com.google.android.music"
    };

    // default launcher app list
    private String[] mDefaultLauncherAppList = new String[] {
        "com.android.launcher3",
        "com.android.settings"
    };

    // default launcher app component list
    private String[] mDefaultLauncherAppComponentList = new String[] {
        "com.android.launcher3/.Launcher",
        "com.android.launcher3/com.android.searchlauncher.SearchLauncher",
    };

    // apps that have HOME category, but is not a launcher app
    private String[] mExceptionLauncherAppList = new String[] {
        "android",
        "com.android.settings",
        "com.android.managedprovisioning",
        "com.android.provision",
        "com.google.android.gms",
        "com.google.android.setupwizard",
        "com.google.android.googlequicksearchbox",
        "com.android.provision2",
        "com.google.android.apps.restore"
    };

    private boolean mEnableDefaultLauncherFailed = false;

    private List<String> mAutoLaunch_WhiteList;
    private List<String> mAutoLaunch_BlackList;
    private List<String> m2ndLaunch_WhiteList;
    private List<String> m2ndLaunch_BlackList;
    private List<String> mLockScreen_WhiteList;
    private List<String> mLockScreen_BlackList;

    private static final int DEFAULT_POWER_MODE = PowerManagerEx.MODE_SMART;
    private int mPowerSaveMode = DEFAULT_POWER_MODE;
    private int mNextPowerSaveMode = PowerManagerEx.MODE_SMART;

    private boolean mUerSwitchedInUltraSavingMode = false;


    private SettingsObserver mSettingsObserver;

    // for bug#787547 don't kill preset message app
    private final AbsDeviceIdleController mAbsDeviceIdleController;

    //SPRD:Bug 814570 About apps auto run BEG
    private AlertDialog appAutoRunDialog;
    //SPRD:Bug 814570 About apps auto run END


    // permissions that admin app must have
    private String[] mAdminAppPermissionList = new String[] {
        "android.permission.GET_PACKAGE_SIZE",
        "android.permission.KILL_BACKGROUND_PROCESSES",
        "android.permission.PACKAGE_USAGE_STATS",
        "android.permission.CLEAR_APP_CACHE"
    };

    // add for bug#982648 to handle the case of user installed PowerSaveLauncher
    private final String POWERSAVE_LAUNCHER_PACKNAME =  "com.sprd.powersavemodelauncher";

    private static class CallerAppInfo {
            // Intent intent;
            public String mPackageName;
            public int mUid;
            public String mReason;

            public CallerAppInfo(String packageName, int uid, String reason) {
                mPackageName = packageName;
                mUid = uid;
                mReason = reason;
            }
    }

    private static final int ALLOW_LEVEL_EXEMPTION = 0;
    private static final int ALLOW_LEVEL_STARTED = 1;
    private static final int ALLOW_LEVEL_NORMAL = 2;

    private static class AllowLevel {
            public int mLevel;
    }

    private AllowLevel allowLevel = new AllowLevel();

    private static class NotificationIntentInfo {
            public ArrayList<Intent> mIntentList = new ArrayList<>();
    }

    public BackgroundCleanHelper(Context context, IActivityManager activityManager, Handler handler) {
        super(context, AppPowerSaveConfig.MASK_AUTOLAUNCH |
            AppPowerSaveConfig.MASK_SECONDARYLAUNCH |
            AppPowerSaveConfig.MASK_LOCKSCRRENCLEANUP);

        mAutoLaunch_WhiteList = mPowerControllerInternal.getWhiteList(AppPowerSaveConfig.ConfigType.TYPE_AUTOLAUNCH.value);
        mAutoLaunch_BlackList = mPowerControllerInternal.getBlackList(AppPowerSaveConfig.ConfigType.TYPE_AUTOLAUNCH.value);
        m2ndLaunch_WhiteList = mPowerControllerInternal.getWhiteList(AppPowerSaveConfig.ConfigType.TYPE_SECONDARYLAUNCH.value);
        m2ndLaunch_BlackList = mPowerControllerInternal.getBlackList(AppPowerSaveConfig.ConfigType.TYPE_SECONDARYLAUNCH.value);
        mLockScreen_WhiteList = mPowerControllerInternal.getWhiteList(AppPowerSaveConfig.ConfigType.TYPE_LOCKSCREENCLEANUP.value);
        mLockScreen_BlackList = mPowerControllerInternal.getBlackList(AppPowerSaveConfig.ConfigType.TYPE_LOCKSCREENCLEANUP.value);

        mActivityManager = activityManager;
        mHandler = handler;

        // mUsageStatsInternal = LocalServices.getService(UsageStatsManagerInternal.class);
        // mUsageStatsManager = (UsageStatsManager) mContext.getSystemService(Context.USAGE_STATS_SERVICE);

        // mDeviceIdleController = IDeviceIdleController.Stub.asInterface(
        //        ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER));
        // mPowerGuruService = IPowerGuru.Stub.asInterface(ServiceManager.getService(Context.POWERGURU_SERVICE));
        mAlarmManager = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        // mAudioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        // mLocalPowerManager = LocalServices.getService(PowerManagerInternal.class);
        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);


        registerForBroadcasts();

        mSettingsObserver = new SettingsObserver(mHandler, mContext.getContentResolver());

        int docleanTimeout = SystemProperties.getInt(PowerController.POWER_CONTROLLER_BGCLEAN_TIMEOUT,
            (int)DOCLEAN_TIMEOUT);
        DOCLEAN_TIMEOUT = docleanTimeout;

        mSystemPreferredConfig = SystemPreferredConfig.getInstance(mContext, mHandler);
        if (mSystemPreferredConfig != null)
            mSystemPreferredConfig.setLoadUsageStats(mFirstStartedNotifityEnabled);

        mThirdpartyPush = ThirdpartyPush.getInstance(mContext);

        mAutoStartRecognize = new AutoStartRecognize(mContext, activityManager, mHandler);

        // set constants
        FORCE_STOP_IDLE_THRESHOLD1 = mConstants.BG_APPIDLE_THRESHOLD1;
        FORCE_STOP_IDLE_THRESHOLD2 = mConstants.BG_APPIDLE_THRESHOLD2;
        FORCE_STOP_IDLE_THRESHOLD3 = mConstants.BG_APPIDLE_THRESHOLD3;
        MAX_LAUNCHED_APP_KEEP = mConstants.BG_MAX_LAUNCHED_APP_KEEP;
        if (DEBUG) Slog.d(TAG, "FORCE_STOP_IDLE_THRESHOLD1:" + FORCE_STOP_IDLE_THRESHOLD1
            + " FORCE_STOP_IDLE_THRESHOLD2:" + FORCE_STOP_IDLE_THRESHOLD2
            + " FORCE_STOP_IDLE_THRESHOLD3:" + FORCE_STOP_IDLE_THRESHOLD3
            + " MAX_LAUNCHED_APP_KEEP:" + MAX_LAUNCHED_APP_KEEP
            + " DOCLEAN_TIMEOUT: " + DOCLEAN_TIMEOUT);

        // for bug#787547 don't kill preset message app
        mAbsDeviceIdleController = (AbsDeviceIdleController)PMSFactory.getInstance().createExtraDeviceIdleController(context);
        mAbsDeviceIdleController.readPresetConfigListFromFile();

        mHomeIntent =  new Intent(Intent.ACTION_MAIN, null);
        mHomeIntent.addCategory(Intent.CATEGORY_HOME);
        mHomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    private final AlarmManager.OnAlarmListener mAlarmListener
            = new AlarmManager.OnAlarmListener() {
        @Override
        public void onAlarm() {
            if (DEBUG) Slog.d(TAG, "onAlarm()");
            mPowerControllerInternal.reCheckAllAppInfoDelayed(0);
        }
    };

    @Override
    void onPowerSaveModeChanging(int newMode) {
        mNextPowerSaveMode = newMode;

        if (PowerManagerEx.MODE_ULTRASAVING == newMode) {
            try {
                mRunAppList = mActivityManager.getRunningAppProcesses();
            } catch (Exception e) {}
        }

        updateLauncherAppEnabledSettings(newMode);
    }

    @Override
    void onPowerSaveModeChanged(int mode) {
        if (DEBUG) Slog.d(TAG, "Current PowerSaveMode:" + mode);

        int preMode = mPowerSaveMode;
        mPowerSaveMode = mode;

        // enable default launcher fail when exit from ultra-saving mode
        if (mEnableDefaultLauncherFailed && mBootCompleted
            && PowerManagerEx.MODE_ULTRASAVING != mode
            && PowerManagerEx.MODE_ULTRASAVING == preMode) {
            checkLauncherEnabledSetting();
            mEnableDefaultLauncherFailed = false;
        }

        //if (PowerManagerEx.MODE_ULTRASAVING == preMode
        //    && mode != preMode) {

        //    if (DEBUG) Slog.d(TAG, "Exit ultra saving mode kill com.sprd.powersavemodelauncher!");
        //    try {
        //        mActivityManager.forceStopPackage("com.sprd.powersavemodelauncher", UserHandle.myUserId());
        //    } catch (Exception e) {}
        //}

        switch (mode) {
            case PowerManagerEx.MODE_ULTRASAVING:
                List<String> ultramodeAppList = mPowerControllerInternal.getAppList_UltraMode();
                try {
                    List<RunningAppProcessInfo> runAppList = mRunAppList;
                    mRunAppList = null;
                    if (runAppList == null) {
                        Slog.d(TAG, "mRunAppList is null ");
                        runAppList = mActivityManager.getRunningAppProcesses();
                    }
                    for (int i=0;i<runAppList.size();i++) {
                        RunningAppProcessInfo info = runAppList.get(i);
                        if (ultramodeAppList.contains(info.pkgList[0])) {
                            if (DEBUG) Slog.d(TAG, "pkgList[0]: " + info.pkgList[0] + " in ultramodeAppList, skip it");
                            continue;
                        }

                        // not app in whitelist
                        if (inCommonWhiteAppList(info.pkgList[0])) {
                            if (DEBUG) Slog.d(TAG, "pkgList[0]: " + info.pkgList[0] + " in CommonWhiteAppList, skip it");
                            continue;
                        }

                        AppState appState = mAppStateInfoCollector.getAppState(info.pkgList[0], UserHandle.myUserId());
                        if (appState == null) {
                            if (DEBUG) Slog.d(TAG, "pkgList[0]: " + info.pkgList[0] + ", appState is null, skip it");
                            continue;
                        }
                        // avoid kill input method
                        if (appState.mIsEnabledInputMethod) {
                            if (DEBUG) Slog.d(TAG, "pkgList[0]: " + info.pkgList[0] + " mIsEnabledInputMethod, skip it");
                            continue;
                        }

                        if (isDefaultWallpaperService(info.pkgList[0])) {
                          if (DEBUG) Slog.d(TAG, "pkgList[0]: " + info.pkgList[0] + " DefaultWallpaperService, skip it");
                          continue;
                        }

                        boolean bFind = false;
                        if (isSystemApp(appState)) {
                            for(String s : mBlackAppListForUltraSaving) {
                                if(info.pkgList[0].contains(s)) {
                                    bFind = true;
                                    if (DEBUG) Slog.d(TAG, "pkgList[0]: " + info.pkgList[0] + " is system app in black list");
                                    break;
                                }
                            }
                            if (!bFind) {
                                if (DEBUG) Slog.d(TAG, "pkgList[0]: " + info.pkgList[0] + " is system app, skip it");
                                continue;
                            }
                        }

                        // not user installed PowerSaveLauncher app
                        if (POWERSAVE_LAUNCHER_PACKNAME.equals(info.pkgList[0])) {
                            if (DEBUG) Slog.d(TAG, "pkgList[0]: " + info.pkgList[0] + " user installed PowerSaveLauncher, skip it");
                            continue;
                        }

                        mActivityManager.forceStopPackage(info.pkgList[0], UserHandle.myUserId());
                        // do some cleaning for appState
                        if (appState != null) appState.clearLaunchInfo();
                        if (DEBUG) Slog.d(TAG, "enter ultrasaving mode, force stop:" + info.pkgList[0]);
                    }
                } catch (RemoteException e) {
                }
                //no break
            case PowerManagerEx.MODE_LOWPOWER:
            case PowerManagerEx.MODE_POWERSAVING:
                MAX_LAUNCHED_APP_KEEP = mConstants.BG_MAX_LAUNCHED_APP_KEEP_LOWPOWER; //including the launcher app
                break;

            case PowerManagerEx.MODE_PERFORMANCE:
            case PowerManagerEx.MODE_SMART:
                MAX_LAUNCHED_APP_KEEP = mConstants.BG_MAX_LAUNCHED_APP_KEEP; //including the launcher app
                break;
        }

        // add for bug#901762 913854 --> start
        if (PowerManagerEx.MODE_INVALID != preMode) {
            if ((PowerManagerEx.MODE_ULTRASAVING != mode && PowerManagerEx.MODE_ULTRASAVING == preMode)
                || (PowerManagerEx.MODE_ULTRASAVING == mode && PowerManagerEx.MODE_ULTRASAVING != preMode)) {
                try {
                    if (DEBUG) Slog.d(TAG, "exit/enter Ultra-saving mode!");
                    mContext.startActivityAsUser(mHomeIntent, UserHandle.CURRENT);
                } catch (Exception e) {
                    Slog.d(TAG, "startActivityAsUser fail!");
                }
            }
        }
        // add for bug#901762 913854 <-- end

        // mPowerSaveMode = mode;
    }

    private void finishKillApplication(final String packageName, int uid) {
        Intent intent = new Intent(PowerManager.ACTION_POWER_CONTROLLER_KILL_APP,
             Uri.fromParts("package", packageName, null));

        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        intent.putExtra(Intent.EXTRA_UID, uid);
        intent.putExtra(Intent.EXTRA_USER_HANDLE, UserHandle.getUserId(uid));

        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    /*
     * App can be constrained for force stop:
     * 1. third party app &&
     * 2. (app not in doze whitelist  or not a cts/monkey app) &&
     * 3. not a Message app &&
     * 4. not doing download &&
     * 5. not playing music &&
     * 6. not a visible app &&
     * 7. ((app not lauched by user  && app has not notification) ||
     *      (app not launched by user && app has notification && idle time > 1 h after standby) ||
     *      (app launched by user && app has not notification && idle time > 2h) ||
     *      (app launched by user && app has notification && && idle time > 4h after standby))
     */
    boolean canBeConstrained(AppState appState) {

        // if this function is disabled, just return false
        if (!mEnabled) return false;

        if (appState == null || (mPowerSaveMode == PowerManagerEx.MODE_NONE
            && !mLockScreen_BlackList.contains(appState.mPackageName))) {
            return false;
        }

        // if enable only in save mode
        if (mEnabledOnlySaveMode && !isSaveMode(mPowerSaveMode)) {
            //if (DEBUG) Slog.d(TAG, "canBeConstrained return false for only enable in save mode!");
            return false;
        }

        // if in ultra-saving laucher, then clear the app that not in the allowed app list
        // after standby for 1s
        if (contraintInUltraSavingMode(appState)) {
            if (DEBUG) Slog.d(TAG, "canBeConstrained: " + appState.mPackageName + " constraint by ultra-saving mode");
            return true;
        }

        // set bgclean timer
        long delayMs = SystemClock.elapsedRealtime() - mStandbyStartTime - DOCLEAN_TIMEOUT;
        if (delayMs < 0) {
            if (!mAlarmSet) {
                if (DEBUG) Slog.d(TAG, "canBeConstrained(), set alarm");
                // not use wake up alarm, otherwise a cts case of sensor will fail.
                mAlarmManager.set(AlarmManager.ELAPSED_REALTIME,
                    mStandbyStartTime + DOCLEAN_TIMEOUT, TAG, mAlarmListener, mHandler);
                mAlarmSet = true;
            }
            return false;
        }

        // not a system app
        if (isSystemApp(appState)) { // system app
            return false;
        }

        // app not exist
        if ((appState.mProcState == ActivityManager.PROCESS_STATE_CACHED_EMPTY && Event.NONE == appState.mState)
            || appState.mProcState == ActivityManager.PROCESS_STATE_NONEXISTENT) {
            return false;
        }

        // avoid kill input method
        if (appState.mIsEnabledInputMethod)
            return false;

        if (isImportantPrefferedApp(appState.mPackageName))
            return false;

        boolean isVisible = false;
        int index = mVisibleAppList.indexOf(appState.mPackageName);
        if (index >= 0 || appState.mVisible) {
            isVisible = true;
        }
        // not a visible app
        if (isVisible) {
            return false;
        }

        // playing music App can not be constrained
        if (isPlayingMusic(appState)) {
            if (DEBUG) Slog.d(TAG, "canBeConstrained: " + appState.mPackageName + " Music is playing");
            return false;
        }

        // doing download App can not be constrained
        if (isDoingDownload(appState)) {
            if (DEBUG) Slog.d(TAG, "canBeConstrained: " + appState.mPackageName
                + " Doing Download");
            return false;
        }

        /** @hide Process is in the background, but it can't restore its state so we want
         * to try to avoid killing it. */
        if (appState.mProcState == ActivityManager.PROCESS_STATE_HEAVY_WEIGHT) {
            return false;
        }

        // not app in whitelist
        if (inCommonWhiteAppList(appState.mPackageName)) {
            return false;
        }

        if (mLockScreen_WhiteList.contains(appState.mPackageName)) {
            if (DEBUG) Slog.d(TAG, "bgclean CanBeConstrained: " + appState.mPackageName + " in my lockscreen whitelist");
            return false;
        } else if (mLockScreen_BlackList.contains(appState.mPackageName) && mStandbyStartTime > 0) {
            if (DEBUG) Slog.d(TAG, "bgclean CanBeConstrained: " + appState.mPackageName + " in my lockscreen blacklist");
            return true;
        }

        int lockScreenCleanUp = mPowerControllerInternal.getAppPowerSaveConfgWithTypeInternal(appState.mPackageName, AppPowerSaveConfig.ConfigType.TYPE_LOCKSCREENCLEANUP.value);
        if ((lockScreenCleanUp == AppPowerSaveConfig.VALUE_OPTIMIZE)) {
            if (DEBUG_MORE) Slog.d(TAG, "in appPowerConfig lockScreenCleanUp black list: " + appState.mPackageName);
            return true;
        }

        // don't do auto clean for app in doze white list
        if (inDozeWhiteList(appState.mPackageName)) {
            if (DEBUG) Slog.d(TAG, "bgclean CanBeConstrained: " + appState.mPackageName + " in doze white list");
            return false;
        }

        // this app is avoid killing
        if (appState.mAvoidKilling) return false;

        // message App can not be constrained
        // for bug#787547 don't kill preset message app
        if (isMessageApp(appState)
            ||mAbsDeviceIdleController.isInPresetWhiteAppList(appState.mPackageName)
            ) {
            if (DEBUG) Slog.d(TAG, "canBeConstrained: " + appState.mPackageName + " Message App");
            return false;
        }

        // in low power mode, we will clean more bg app
        if (PowerManagerEx.MODE_LOWPOWER == mPowerSaveMode) {
            if (DEBUG) Slog.d(TAG, "canBeConstrained, low power mode clean, appname: " + appState.mPackageName + ", time diff: " + (SystemClock.elapsedRealtime() - mStandbyStartTime)
                + ", procState: " + appState.mProcState);
            if (LOWPOWER_DOCLEAN_THRESHOLD <= SystemClock.elapsedRealtime() - mStandbyStartTime) {
                if (appState.mProcState > ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND) {
                    if (DEBUG) Slog.d(TAG, "canBeConstrained: low power mode clean, " + appState.mPackageName);
                    return true;
                }
            }
        }

        // doing download App can not be constrained
        if (isDoingDownload(appState)) {
            if (DEBUG) Slog.d(TAG, "canBeConstrained: " + appState.mPackageName
                + " Doing Download (mUsedTimeSliceCount:" + appState.mUsedTimeSliceCount + ")");
            // download is checked using a time slice (default 30s)
            // if the count of time slice using to complete detecting download
            // is >= 1, then we can be sure that user use this app doing download before standby
            // in this case, we will avoid kill this app
            if (appState.mUsedTimeSliceCount >=1 ) {
                appState.mAvoidKilling = true;
            }
            return false;
        }

        if (appState.mFCMorGCMForBgClean) {
            if (DEBUG) Slog.d(TAG, "canBeConstrained: " + appState.mPackageName + " not Constrained, that uses GCM service");
            return false;
        }

        long nowELAPSED = SystemClock.elapsedRealtime();
        long now = System.currentTimeMillis(); // wall time
        long fromBootup = now - nowELAPSED; // wall time
        long idleDuration = 0;
        long idleDurationBeforeStandby = 0;
        boolean hasLaunched = false;
        boolean hasNotification = false;
        boolean mayPlayingMusic = false;
        boolean launcherApp = isLauncherApp(appState.mPackageName);

        // FixMe: if we has more exact method to jude app playing music, then we don't need to do this
        mayPlayingMusic = mayBePlayingMusic(appState);

        if (DEBUG) Slog.d(TAG, "pkg: " + appState.mPackageName
            + " flags:" + Integer.toHexString(appState.mFlags)
            + " ProcState:" + Util.ProcState2Str(appState.mProcState));

        if (appState.mLastLaunchTime > 0
            && appState.mLastVisibleTime > appState.mLastStopTime) hasLaunched = true;
        idleDuration = (appState.mLastTimeUsed > 0 ? (nowELAPSED -appState.mLastTimeUsed) : -1);
        idleDurationBeforeStandby = (mStandbyStartTime > appState.mLastTimeUsed ? (mStandbyStartTime -appState.mLastTimeUsed) : 0);

        hasNotification = hasActiveNotification(appState);

        if (DEBUG) Slog.d(TAG, "STATE: pkg:" + appState.mPackageName
            + " idle for:" + idleDuration
            + " idleDurationBeforeStandby:" + idleDurationBeforeStandby
            + " hasLaunched:" + hasLaunched
            + " isVisable:" + isVisible
            + " hasNotification:" + hasNotification
            + " mayPlayingMusic:" + mayPlayingMusic
            + " launcherApp:" + launcherApp);


        if (!launcherApp && constraintByUsageHabit(appState)) {
            if (DEBUG) Slog.d(TAG, "canBeConstrained: constraintByUsageHabit true: " + appState.mPackageName);
            return true;
        }

        // if app is launched by user
        // and may be playing music, then don't kill this app
        if (hasLaunched && mayPlayingMusic) {
            return false;
        }

        /*
         * 7. ((app not lauched by user  && app has not notification) ||
         *      (app not launched by user && app has notification && idle time > 1 h after standby) )
         */
        if ((!hasLaunched && !hasNotification)
            || (!hasLaunched && hasNotification && idleDuration > (idleDurationBeforeStandby + FORCE_STOP_IDLE_THRESHOLD1) )
            ) {

            return true;
        }

        /*
         * 8. ((app launched by user && app has not notification && idle time > 2h) ||
         *      (app launched by user && app has notification && && idle time > 4h after standby))
         */
        if (hasLaunched) {
            int currentActiveLaunchedCount = mAppStateInfoCollector.getCountOfActiveLaunchedApps(mCurrentUserId);
            if (DEBUG) Slog.d(TAG, "currentActiveLaunchedCount:" + currentActiveLaunchedCount);

            if (!launcherApp && currentActiveLaunchedCount > MAX_LAUNCHED_APP_KEEP) {
                if ((!hasNotification && idleDuration > ( FORCE_STOP_IDLE_THRESHOLD2))
                ||  (hasNotification && !appState.mHasNoClearNotification
                      && idleDuration > ( idleDurationBeforeStandby + FORCE_STOP_IDLE_THRESHOLD3))
                ) {
                    return true;
                }
            }
        }

        return false;
    }

    /*
     * handle the case:
     * app move from BG2FG, and it can not be constrained again.
     */
    void updateAppStateChanged(AppState appState, int stateChange) {

    }

    /*
     * check this app should be constrained or not
     * return true if this app should be constrained
     * others return false
     */
    boolean checkAppStateInfo(AppState appState, final long nowELAPSED) {
        ArrayMap<String, Integer> mForceStopAppList = getForceStopAppList(appState.mUserId);

        boolean bChanged = false;

        if (canBeConstrained(appState)) {
            bChanged = true;
            if (!mForceStopAppList.containsKey(appState.mPackageName)) {
                mForceStopAppList.put(appState.mPackageName, appState.mUid);
                if (DEBUG) Slog.d(TAG, "checkAppStateInfo(), add " + appState.mPackageName + " to forcestop list");
            }
        } else {
            // if already put it in mForceStopAppList, just remove
            mForceStopAppList.remove(appState.mPackageName);
        }

        return bChanged;
    }

    void applyConstrain() {

        if (mStandbyStartTime <= 0) {
            if (DEBUG) Slog.d(TAG, " system becomes non-standby before applying constrain!");
            // clear for standby state changed
            clearAllStoppedAppList();
            return;
        }

        for (int index=mForceStopAppListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, Integer> mForceStopAppList = mForceStopAppListForUsers.valueAt(index);

            try {
                for (int i=0;i<mForceStopAppList.size();i++) {
                    String pkgName =  mForceStopAppList.keyAt(i);
                    int uid = mForceStopAppList.valueAt(i);
                    int userId = UserHandle.getUserId(uid);
                    try {
                        //mActivityManager.forceStopPackage(pkgName, userId);
                        if (DEBUG) Slog.d(TAG, "applyConstrain & killApplication--->pkgName: " + pkgName + " uid:" + uid);
                        mActivityManager.killApplication(pkgName, UserHandle.getAppId(uid), userId, REASON_KILL_APP);
                        if (DEBUG) Slog.d(TAG, "applyConstrain & killApplication:send PowerManager.ACTION_POWER_CONTROLLER_KILL_APP broacast!!");
                        finishKillApplication(pkgName, uid);
                        AppState appState = mAppStateInfoCollector.getAppState(pkgName, userId);
                        if (appState != null) {
                            // do some cleaning for appState
                            appState.clearLaunchInfo();
                        } else {
                            Slog.w(TAG, "null appState for " + pkgName + " userId:" + userId);
                        }
                        Slog.d(TAG, "force stop:" + pkgName + " userId:" + userId);

                        ArrayMap<String, Integer> mStoppedAppList = getStoppedAppList(userId);
                        // add to stopped app list
                        mStoppedAppList.put(pkgName, userId);

                    } catch (RemoteException e) {
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            mForceStopAppList.clear();

        }
    }

    // clear the app from the Constrained App List
    void clearConstrain(AppState appState) {
        appState.mAvoidKilling = false;
        ArrayMap<String, Integer> mForceStopAppList = getForceStopAppList(appState.mUserId);
        mForceStopAppList.remove(appState.mPackageName);
    }

    // wrap API for 


    /*
     * Note device is enter standby state ( screen off / unpluged)
     */
    void noteDeviceStateChanged(boolean bStandby, final long nowELAPSED) {
        if (DEBUG) Slog.d(TAG, "noteDeviceStateChanged bStandby:" + bStandby);

        if (bStandby) mStandbyStartTime = nowELAPSED; //SystemClock.elapsedRealtime();
        else mStandbyStartTime = 0;

        if (!bStandby) {
            //if (DEBUG) Slog.d(TAG, "not standby, so remove doclean msg and clear mForceStopAppList");
            mAlarmManager.cancel(mAlarmListener);
            clearAllForceStopAppList();
            mAlarmSet = false;

            // clear for standby state changed
            clearAllStoppedAppList();

            //mVisibleAppList.clear();
            return;
        }

        //updateVisibleActivities(); //move to onWakefulnessChangeFinished
    }
    //SPRD:Bug 814570 About apps auto run BEG
    void noteAppFirstStarted(String packageName, int userId) {
        if (!mFirstStartedNotifityEnabled) return;

        // not notify for other user and ultra-saving mode
        if (userId != 0 || PowerManagerEx.MODE_ULTRASAVING == mPowerSaveMode) return;

        ArrayMap<String, PackageInfo> mInstalledAppList = getInstalledAppList(userId);

        PackageInfo targetPkg = mInstalledAppList.get(packageName);
        // if system app, just return
        if (targetPkg == null) return;

        if (DEBUG) Slog.d(TAG, "noteAppFirstStarted: " + packageName);

        mHandler.postDelayed(new Runnable() {
            public void run() {
                try {
                    handleAppFirstStarted(packageName, userId);
                } catch (Exception e) {}
            }
        }, 1000);
    }

    private void handleAppFirstStarted(String packageName, int userId) {
        if (userId != 0) return;

        if (DEBUG) Slog.d(TAG, "handleAppFirstStarted!!");

        AppState appState = mAppStateInfoCollector.getAppState(packageName, userId);

        if (DEBUG) Slog.d(TAG, "handleAppFirstStarted: appState:" + appState);

        if (appState == null || (ActivityManager.PROCESS_STATE_TOP != appState.mProcState)) {
            return;
        }

        if (isSystemApp(appState)) return;

        CallerAppInfo caller = mFirstCallerAppList.get(packageName);

        if (caller == null || !caller.mReason.equals(REASON_START_ACTIVITY)
            || Util.isCts(packageName) || Util.isCts(caller.mPackageName)
            || isAutoTest(caller.mUid, caller.mPackageName, packageName)
            || "android".equals(caller.mPackageName)
            || isLauncherApp(packageName)
            || !mSystemPreferredConfig.isFirstLaunched(packageName)
            ) {
            return;
        }

        if (DEBUG) Slog.d(TAG, "handleAppFirstStarted: packageName:" + packageName + " caller:" + caller.mPackageName);

        int autoLaunch = mPowerControllerInternal.getAppPowerSaveConfgWithTypeInternal(packageName,
            AppPowerSaveConfig.ConfigType.TYPE_AUTOLAUNCH.value);
        int secondLaunch = mPowerControllerInternal.getAppPowerSaveConfgWithTypeInternal(packageName,
            AppPowerSaveConfig.ConfigType.TYPE_SECONDARYLAUNCH.value);

        //check whether app is in black list
        boolean bInBlackList = false;
        if ((autoLaunch == AppPowerSaveConfig.VALUE_OPTIMIZE)
            || (secondLaunch == AppPowerSaveConfig.VALUE_OPTIMIZE)) {
            bInBlackList = true;
        }

        if (!bInBlackList) return;

        if (DEBUG) Slog.d(TAG, "-show dialog!!");

        // TODO, show dialog here
        if(appAutoRunDialog != null){

            appAutoRunDialog.dismiss();
            appAutoRunDialog = null;
        }
        CloseDialogReceiver closer = new CloseDialogReceiver(mContext);
        PackageManager mPackageManager = mContext.getPackageManager();
        CharSequence label = null;
        String message = mContext.getString(R.string.prohibit_auto_run_summary);
        try {
            ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(packageName, 0);
            label = mPackageManager.getApplicationLabel(applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        appAutoRunDialog = new AlertDialog.Builder(mContext)
                   .setTitle(label)
                   .setMessage(message)
                   .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(DialogInterface dialog, int which) {
                            appAutoRunDialog.dismiss();
                       }
                   })
                   .create();
        appAutoRunDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        closer.dialog = appAutoRunDialog;
        closer.registerBroadcastReceiver();
        appAutoRunDialog.setOnDismissListener(closer);
        appAutoRunDialog.show();
    }

    private static class CloseDialogReceiver extends BroadcastReceiver
            implements DialogInterface.OnDismissListener {
        private Context mContext;
        public Dialog dialog;
        private boolean registeredBroadcast;

        CloseDialogReceiver(Context context) {
            mContext = context;
            registeredBroadcast = false;
        }

        public void registerBroadcastReceiver() {
            if (!registeredBroadcast) {
                IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                mContext.registerReceiver(this, filter);
                registeredBroadcast = true;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String reason = intent.getStringExtra(PhoneWindowManager.SYSTEM_DIALOG_REASON_KEY);
            if(!PhoneWindowManager.SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS.equals(reason)
                && dialog != null) {
                dialog.cancel();
            }
        }

        public void onDismiss(DialogInterface unused) {
            if (registeredBroadcast) {
                mContext.unregisterReceiver(this);
                registeredBroadcast = false;
            }
        }
    }

    //SPRD:Bug 814570 About apps auto run END

    void noteAppStopped(int uid, String packageName) {
    }

    void loadInstalledPackages(){

        try {
            final List<UserInfo> users = mUserManager.getUsers();
            for (int ui = users.size() - 1; ui >= 0; ui--) {
                UserInfo user = users.get(ui);
                if (DEBUG) Slog.d(TAG, "- loadInstalledPackages() for user: " + user.id);
                ArrayMap<String, PackageInfo> mInstalledAppList = getInstalledAppList(user.id);

                List<PackageInfo> packages = mContext.getPackageManager().getInstalledPackagesAsUser(0, user.id);
                for(PackageInfo pkg : packages){
                    if (DEBUG) Slog.d(TAG, "PKG:" + pkg.packageName + " Flag:" + Integer.toHexString(pkg.applicationInfo.flags));

                    if(pkg !=null && pkg.applicationInfo !=null
                        && (pkg.applicationInfo.flags &
                           (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP |ApplicationInfo.FLAG_SYSTEM)) == 0) {
                        if (!mInstalledAppList.containsKey(pkg.packageName)) {
                            mInstalledAppList.put(pkg.packageName, pkg);
                            checkPermissionList(pkg.packageName, user.id);
                        }
                    }
                }

                if (DEBUG) {
                    Slog.d(TAG, "mInstalledAppList: " + mInstalledAppList.size());
                    for (String key : mInstalledAppList.keySet()) {
                        Slog.d(TAG, "App:" + key);
                    }

                    Slog.d(TAG, "mInstalledAdminAppList: " + mInstalledAdminAppList.size());
                    for (int i=0;i<mInstalledAdminAppList.size();i++) {
                        Slog.d(TAG, "App:" + mInstalledAdminAppList.get(i));
                    }

                }
            }
        } catch (Exception e) {
        }


    }

    void initDataForBootCompleted() {
        loadInstalledServiceList();
        loadLauncherApps();
        loadEnabledInputMethodApp();

        //checkLauncherEnabledSetting();

        mSystemPreferredConfig.initDataForBootCompleted();
        mThirdpartyPush.loadAssociatedComponents();

        mBootCompleted = true;

        // for bug896325
        if (mEnableDefaultLauncherFailed)
            checkLauncherEnabledSetting();
    }

    private void loadLauncherApps() {
        try {

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);

            final List<UserInfo> users = mUserManager.getUsers();
            for (int ui = users.size() - 1; ui >= 0; ui--) {
                UserInfo user = users.get(ui);
                if (DEBUG) Slog.d(TAG, "- loadLauncherApps() for user: " + user.id);
                List<ResolveInfo> resolves =
                    mContext.getPackageManager().queryIntentActivitiesAsUser(intent, PackageManager.MATCH_DISABLED_COMPONENTS, user.id);

                // Look for the original activity in the list...
                final int N = resolves != null ? resolves.size() : 0;
                for (int i=0; i<N; i++) {
                    final ResolveInfo candidate = resolves.get(i);
                    final ActivityInfo info = candidate.activityInfo;
                    if (info != null && info.packageName != null
                        && !mLauncherAppList.contains(info.packageName)) {
                        mLauncherAppList.add(info.packageName);
                    }
                    Slog.d(TAG, "homeApp:" + info.packageName);

                    // using disable component instead of disable the whole app
                    // when enter/exit ultra-saving mode. See bug#819868
                    if (USE_COMPONENT) {
                        if (info != null && info.packageName != null
                             && POWERSAVE_LAUNCHER_PACKNAME.equals(info.packageName)) {
                            if (!mLauncherAppComponentList.contains(info.packageName)) {
                                mLauncherAppComponentList.add(info.packageName);
                            }
                            Slog.d(TAG, "homeComponent:" + info.packageName);
                            continue;
                        }

                        if (info != null && info.packageName != null) {
                            if (ArrayUtils.contains(mExceptionLauncherAppList, info.packageName)) {
                                if (DEBUG) Slog.d(TAG, "loadLauncherApps:" + info.packageName
                                    + " NOT a launcher app!");
                                continue;
                            }

                            ComponentName cn = new ComponentName(info.packageName, info.name);
                            String componetName = cn.flattenToShortString();
                            if (!mLauncherAppComponentList.contains(componetName)) {
                                mLauncherAppComponentList.add(componetName);
                            }
                            Slog.d(TAG, "homeComponent:" + componetName);
                        }
                    }
                }

                // using disable component instead of disable the whole app
                // when enter/exit ultra-saving mode. See bug#819868
                if (USE_COMPONENT) {
                    loadLauncherComponent(user.id);
                }
            }

            /*check if contains the default launcher*/
            for(String s : mDefaultLauncherAppList) {
                if(!mLauncherAppList.contains(s)) {
                    mLauncherAppList.add(s);
                }
            }

            // using disable component instead of disable the whole app
            // when enter/exit ultra-saving mode. See bug#819868
            if (USE_COMPONENT) {
                /*check if contains the default launcher*/
                for(String s : mDefaultLauncherAppComponentList) {
                    if(!mLauncherAppComponentList.contains(s)) {
                        mLauncherAppComponentList.add(s);
                    }
                }
            }

            ResolveInfo resolvedHome = mContext.getPackageManager().resolveActivity(intent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            if (resolvedHome != null) {
                mCurrentHomeLauncher = resolvedHome.activityInfo.packageName;
            }
        } catch (Exception e) {
            Slog.d(TAG, "e:" + e);
        }

        //disbleLauncherAppIfNeed();

        if (DEBUG) {
            Slog.d(TAG, "mCurrentHomeLauncher:" + mCurrentHomeLauncher);
            Slog.d(TAG, "mLauncherAppList: " + mLauncherAppList.size());
            for (int i=0;i<mLauncherAppList.size();i++) {
                Slog.d(TAG, "App:" + mLauncherAppList.get(i));
            }

            Slog.d(TAG, "mLauncherAppComponentList: " + mLauncherAppComponentList.size());
            for (int i=0;i<mLauncherAppComponentList.size();i++) {
                Slog.d(TAG, "Component:" + mLauncherAppComponentList.get(i));
            }
        }
    }

    private void loadInstalledServiceList() {
        try {
            final List<UserInfo> users = mUserManager.getUsers();
            for (int ui = users.size() - 1; ui >= 0; ui--) {
                UserInfo user = users.get(ui);
                if (DEBUG) Slog.d(TAG, "- loadInstalledServiceList() for user: " + user.id);
                ArrayMap<String, PackageInfo> mInstalledAppList = getInstalledAppList(user.id);
                if (DEBUG) Slog.d(TAG, "get launcher intent for installed app : size: " + mInstalledAppList.size());
                for (String key : mInstalledAppList.keySet()) {
                    if (mContext.getPackageManager().getLaunchIntentForPackage(key) == null
                        && !mInstalledServiceList.contains(key)
                        && getApplicationLauncherEnabledSetting(key, mCurrentUserId) == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ) {
                        mInstalledServiceList.add(key);
                    }
                }
            }
        } catch (Exception e) {
        }

        if (DEBUG) {
            Slog.d(TAG, "mInstalledServiceList: " + mInstalledServiceList.size());
            for (int i=0;i<mInstalledServiceList.size();i++) {
                Slog.d(TAG, "App:" + mInstalledServiceList.get(i));
            }
        }

    }

    private void updateLauncherApps(String appName, boolean added) {
        if (appName == null) return;
        try {

            if (!added) {
                int index = mLauncherAppList.indexOf(appName);
                if (index >= 0) {
                    mLauncherAppList.remove(index);
                }

                // using disable component instead of disable the whole app
                // when enter/exit ultra-saving mode. See bug#819868
                if (USE_COMPONENT) {

                    Iterator<String> it = mLauncherAppComponentList.iterator();
                    while (it.hasNext()){
                        String toDel = it.next();
                        ComponentName compn = ComponentName.unflattenFromString(toDel);
                        if (compn != null && appName.equals(compn.getPackageName())) {
                            it.remove();
                        }
                    }
                    Slog.d(TAG, "App:" + appName + " is uninstall!!");
                }

                // for bug841035 clear info
                AppState appState = mAppStateInfoCollector.getAppState(appName, mCurrentUserId);
                if (appState != null) {
                    // do some cleaning for appState
                    appState.clearLaunchInfo();
                    appState.mTotalLaunchCount = 0;
                }
                mSystemPreferredConfig.onAppRemoved(appName);
            } else {

                boolean isLauncher = false;
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setPackage(appName);
                intent.addCategory(Intent.CATEGORY_HOME);
                List<ResolveInfo> resolves =
                    mContext.getPackageManager().queryIntentActivitiesAsUser(intent, PackageManager.MATCH_DISABLED_COMPONENTS, mCurrentUserId);

                // Look for the original activity in the list...
                final int N = resolves != null ? resolves.size() : 0;
                for (int i=0; i<N; i++) {
                    final ResolveInfo candidate = resolves.get(i);
                    final ActivityInfo info = candidate.activityInfo;
                    if (info != null && info.packageName != null) {
                        int index = mLauncherAppList.indexOf(info.packageName);
                        if (index < 0 || (info.packageName.equals(appName) && added) ) {
                            if (index < 0) mLauncherAppList.add(info.packageName);
                            isLauncher = true; // is a launcher app
                            if (!USE_COMPONENT) {
                                // new install launcher
                                if (!ArrayUtils.contains(mExceptionLauncherAppList, info.packageName)
                                    && PowerManagerEx.MODE_ULTRASAVING == mPowerSaveMode
                                    && !POWERSAVE_LAUNCHER_PACKNAME.equals(info.packageName)) {
                                    Slog.d(TAG, "updateLauncherApps disable new installed launcher:" + info.packageName);
                                    try {
                                        AppGlobals.getPackageManager().setApplicationEnabledSetting(info.packageName,
                                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0, mCurrentUserId, mContext.getOpPackageName());

                                        if (mPowerControllerInternal != null) {
                                            mPowerControllerInternal.addDisabledLauncherApp(info.packageName);
                                        }
                                    } catch (Exception e) {
                                        Slog.d(TAG, "updateLauncherApps new installed launcher Exception:" + e);
                                    }
                                } else if (POWERSAVE_LAUNCHER_PACKNAME.equals(info.packageName)
                                    && PowerManagerEx.MODE_ULTRASAVING != mPowerSaveMode) {

                                    try {
                                        if (DEBUG) Slog.d(TAG, "disable power save launcher for non-ultra-saving mode!");
                                        AppGlobals.getPackageManager().setApplicationEnabledSetting(POWERSAVE_LAUNCHER_PACKNAME,
                                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0, mCurrentUserId, mContext.getOpPackageName());
                                    } catch (Exception e) {
                                        Slog.d(TAG, "updateLauncherApps: disable PowerSaveLauncher Exception:" + e);
                                    }
                                }
                             }
                        }

                        // using disable component instead of disable the whole app
                        // when enter/exit ultra-saving mode. See bug#819868
                        if (USE_COMPONENT) {
                            ComponentName cn = new ComponentName(info.packageName, info.name);
                            String componetName = cn.flattenToShortString();

                            if (POWERSAVE_LAUNCHER_PACKNAME.equals(info.packageName)) {
                                if (DEBUG) Slog.d(TAG, "install power save launcher:" + componetName);

                                if (!mLauncherAppComponentList.contains(info.packageName)) {
                                    mLauncherAppComponentList.add(info.packageName);

                                    if ( PowerManagerEx.MODE_ULTRASAVING != mPowerSaveMode) {
                                        try {
                                            if (DEBUG) Slog.d(TAG, "disable power save launcher for non-ultra-saving mode!");
                                            AppGlobals.getPackageManager().setApplicationEnabledSetting(POWERSAVE_LAUNCHER_PACKNAME,
                                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0, mCurrentUserId, mContext.getOpPackageName());
                                        } catch (Exception e) {
                                            Slog.d(TAG, "updateLauncherApps: disable PowerSaveLauncher Exception:" + e);
                                        }
                                    }
                                }
                                continue;
                            }

                            index = mLauncherAppComponentList.indexOf(componetName);
                            if (index < 0
                                || (info.packageName.equals(appName) && added) ) {
                                if (index < 0) mLauncherAppComponentList.add(componetName);

                                // new install launcher
                                if (!ArrayUtils.contains(mExceptionLauncherAppList, info.packageName)
                                    && PowerManagerEx.MODE_ULTRASAVING == mPowerSaveMode
                                    && !POWERSAVE_LAUNCHER_PACKNAME.equals(info.packageName)) {
                                    Slog.d(TAG, "updateLauncherApps disable new installed launcher component:" + componetName);
                                    try {
                                        AppGlobals.getPackageManager().setComponentEnabledSetting(cn,
                                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0, mCurrentUserId);

                                        if (mPowerControllerInternal != null) {
                                            mPowerControllerInternal.addDisabledLauncherApp(componetName);
                                        }
                                    } catch (Exception e) {
                                        Slog.d(TAG, "updateLauncherApps new installed launcher Exception:" + e);
                                    }
                                 }

                            }
                        }

                    }
                    //Slog.d(TAG, "homeActivities:" + info.packageName);
                }

                // using disable component instead of disable the whole app
                // when enter/exit ultra-saving mode. See bug#819868
                if (USE_COMPONENT && isLauncher) {
                    intent.removeCategory(Intent.CATEGORY_HOME);
                    intent.addCategory(Intent.CATEGORY_LAUNCHER);
                    resolves =
                        mContext.getPackageManager().queryIntentActivitiesAsUser(intent, PackageManager.MATCH_DISABLED_COMPONENTS, mCurrentUserId);
                    // Look for the original activity in the list...
                    final int SIZE = resolves != null ? resolves.size() : 0;
                    for (int i=0; i<SIZE; i++) {
                        final ResolveInfo candidate = resolves.get(i);
                        final ActivityInfo info = candidate.activityInfo;
                        if (info != null && info.packageName != null) {
                            ComponentName cn = new ComponentName(info.packageName, info.name);
                            String componetName = cn.flattenToShortString();
                            int index = mLauncherAppComponentList.indexOf(componetName);
                            if (index < 0
                                || (info.packageName.equals(appName) && added) ) {
                                if (index < 0) mLauncherAppComponentList.add(componetName);

                                // new install launcher
                                if (!ArrayUtils.contains(mExceptionLauncherAppList, info.packageName)
                                    && PowerManagerEx.MODE_ULTRASAVING == mPowerSaveMode ) {
                                    Slog.d(TAG, "updateLauncherApps disable new installed launcher:" + componetName);
                                    try {
                                        AppGlobals.getPackageManager().setComponentEnabledSetting(cn,
                                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0, mCurrentUserId);

                                        if (mPowerControllerInternal != null) {
                                            mPowerControllerInternal.addDisabledLauncherApp(componetName);
                                        }
                                    } catch (Exception e) {
                                        Slog.d(TAG, "updateLauncherApps new installed launcher Exception:" + e);
                                    }
                                 }
                            }
                        }
                    }
                }

            }
        } catch (Exception e) {
            Slog.d(TAG, "e:" + e);
        }

        if (DEBUG) {
            Slog.d(TAG, "mLauncherAppList: " + mLauncherAppList.size());
            for (int i=0;i<mLauncherAppList.size();i++) {
                Slog.d(TAG, "App:" + mLauncherAppList.get(i));
            }

            Slog.d(TAG, "mLauncherAppComponentList: " + mLauncherAppComponentList.size());
            for (int i=0;i<mLauncherAppComponentList.size();i++) {
                Slog.d(TAG, "Component:" + mLauncherAppComponentList.get(i));
            }
        }

    }

     boolean judgeAppLaunchAllowed(Intent intent, String targetApp, int targetUid,
            int callerUid, String callerApp, String reason) {

        //long timestamp = SystemClock.elapsedRealtime();

        // AllowLevel allowLevel = new AllowLevel();
        boolean ret = judgeAppLaunchAllowedInternal(intent, targetApp, targetUid, callerUid, callerApp, reason, allowLevel);

        if (isAutoStartRecognizeEnabled() && ret) {
            int targetUserId = UserHandle.getUserId(targetUid);
            AppState targetAppState = mAppStateInfoCollector.getAppState(targetApp, targetUserId);
            if (isInstalledApp(targetApp, targetUserId)) {
                if (needAutoStartRecognize(intent, targetApp, targetUid, callerApp, callerUid, reason)) {

                    mAutoStartRecognize.addAutoStartCallerInfo(intent, targetApp, targetUid, callerApp, callerUid, reason);

                } else if (needClearAutoStartRecognize(intent, targetApp, targetUid, callerApp, callerUid, reason, allowLevel)){

                    if (DEBUG_MORE) Slog.d(TAG,"needClearAutoStartRecognize return true: "+targetApp+"(uid:" + targetUid
                        + "), callingPackage = "+callerApp+"(uid:" + callerUid + "), reason = "+reason+ " allowLevel:" + allowLevel);
                    if (DEBUG_MORE && intent != null) Slog.d(TAG,"needClearAutoStartRecognize return true: intent action:" + intent);

                    mAutoStartRecognize.clearAutoStartCallerInfo(targetApp);
                }
            }
        }

        if (mCurrentUserId == 0 && ret && targetApp != null) {
            if (!targetApp.equals(callerApp) && !mFirstCallerAppList.containsKey(targetApp)
                && isInstalledApp(targetApp, 0)) {
                mFirstCallerAppList.put(targetApp, new CallerAppInfo(callerApp, callerUid, reason));
            }
        }

        //if (DEBUG) Slog.d(TAG, "judgeAppLaunchAllowed time spend:" + (SystemClock.elapsedRealtime() - timestamp));
        return ret;

    }
    /*
     * Third app can not self started:
     * 1. not allow system broadcast from "systemserver" or "com.android.systemui"
     * 2. not allow start service from ""com.android.shell"
     * 3. not allow background app to launch other third party app
     * 4. not allow self started third party app to start other third party app
     * 5. not allow third party app to start other third party app during standby
     *
     * Note: This api is call from AMS in other threads and may be in variable calling context
     *  SO BE CAREFULL : avoid call other system API that may hold lock. Otherwise, a deadlock may HAPPEN
     */
     boolean judgeAppLaunchAllowedInternal(Intent intent, String targetApp, int targetUid,
            int callerUid, String callerApp, String reason, AllowLevel outAllowLevel) {

        outAllowLevel.mLevel = ALLOW_LEVEL_EXEMPTION;
        // if this function is disabled, just return true
        if (!mEnabled) return true;

        if (DEBUG_MORE) Slog.d(TAG,"judgeAppLaunchAllowed : "+targetApp+"(uid:" + targetUid
            + "), callingPackage = "+callerApp+"(uid:" + callerUid + "), reason = "+reason);
        if (DEBUG_MORE && intent != null) Slog.d(TAG,"judgeAppLaunchAllowed : intent action:" + intent);

        if (targetApp == null) return true;

        // bug#768670
        if (handleStartServiceStartAction(intent, targetApp, targetUid, callerApp, callerUid, reason)) {
            return true;
        }

        if (handleBindServiceStartAction(intent, targetApp, targetUid, callerApp, callerUid, reason)) {
            return true;
        }

        if (handleContentProviderStartAction(intent, targetApp, targetUid, callerApp, callerUid, reason)) {
            return true;
        }

        //handle inputmethod
        if (isLaunchingIMEApp(intent, targetApp, targetUid, callerApp, reason)) {
            return true;
        }

        if (handleBroadcastAction(intent, targetApp, targetUid, callerApp, callerUid, reason)) {
            return true;
        }

        //handle speech recognition
        if (isRecognizerIntent(intent, targetApp, targetUid, callerApp, callerUid, reason)) {
            return true;
        }

        // allow cts app to start any other app
        // allow autotest app
        if (Util.isCts(callerApp) || isAutoTest(callerUid, callerApp, targetApp)) {
            return true;
        }

        // check deny for ultra saving mode
        //if (denyBecauseOfUltraSavingMode(intent, targetApp, targetUid, callerApp, callerUid, reason)) {
        //    return false;
        //}

        int targetUserId = UserHandle.getUserId(targetUid);
        int callUserId = UserHandle.getUserId(callerUid);
        AppState callerAppState = mAppStateInfoCollector.getAppState(callerApp, callUserId);
        AppState targetAppState = mAppStateInfoCollector.getAppState(targetApp, targetUserId);

        // if target app already exist
        /*if (targetAppState != null
            && targetAppState.mProcState < ActivityManager.PROCESS_STATE_CACHED_EMPTY
            && targetAppState.mProcState != ActivityManager.PROCESS_STATE_NONEXISTENT) {*/
        if (isAlreadyStarted(targetAppState)) {
            if (DEBUG_MORE) Slog.d(TAG,"Start Proc : "+targetApp+", callingPackage = "+callerApp+", reason = "+reason + ": already started!!"
                + " (ProcState:" +  Util.ProcState2Str(targetAppState.mProcState)
                + " mState:" + Util.AppState2Str(targetAppState.mState)
                + ")");
            outAllowLevel.mLevel = ALLOW_LEVEL_STARTED;
            return true;
        }

        outAllowLevel.mLevel = ALLOW_LEVEL_NORMAL;

        // check system app
        // allow to launch system app
        int launchState = checkLaunchStateByPreset(intent, targetApp, targetUid, callerApp, callerUid, reason);
        if (launchState == LAUNCH_STATE_ALLOW)
            return true;
        else if (launchState == LAUNCH_STATE_DENY)
            return false;

        // check user setting for third-party app
        launchState = checkLaunchStateByUserSetting(intent, targetApp, targetUid, callerApp, callerUid, reason);
        if (DEBUG_MORE) Slog.d(TAG, "launchState: " + launchState);
        if (launchState == LAUNCH_STATE_ALLOW)
            return true;
        else if (launchState == LAUNCH_STATE_DENY)
            return false;

        // not allow third party app that has been force stopped during standby
        // to be started again
        if (mStandbyStartTime > 0 && isForceStoppedAppDuringStandby(targetApp, targetUid)) {
            if (DEBUG) Slog.d(TAG,"Start Proc : "+targetApp+", callingPackage = "+callerApp+", reason = "+reason + ": denyed (has been force stopped)!!");
            return false;
        }

        // not allow system broadcast to launch third party app
        if ((callerAppState == null ||(callerApp != null && callerApp.equals("android")))
            && REASON_BROADCAST.equals(reason)) {
            if (DEBUG) Slog.d(TAG,"Start Proc : "+targetApp+", callingPackage = "+callerApp+", reason = "+reason + ": denyed!!");
            return false;
        }

        // not allow "com.android.systemui" broadcast to launch third party app
        if (callerApp != null && callerApp.equals("com.android.systemui")
            && REASON_BROADCAST.equals(reason)) {
            if (DEBUG) Slog.d(TAG,"Start Proc : "+targetApp+", callingPackage = "+callerApp+", reason = "+reason + ": denyed!!");
            return false;
        }

        // allow app to launch itself
        if (targetApp.equals(callerApp)) {
            return true;
        }

        // not allow non-top app to launch other app, except launched by UserActivity
        if (!launchedByUserActivity(intent, targetApp, targetUid, callerApp, callerUid, reason, true)) {
            if (DEBUG) {
                Slog.d(TAG,"Start Proc : "+targetApp
                    +", callingPackage = "+callerApp
                    + " (ProcState:" + (callerAppState != null ? Util.ProcState2Str(callerAppState.mProcState):"none")
                    +"), reason = "+reason
                    + ": non-UserActivity denyed!!");
            }
            return false;
        }

        // not allow background app to launch other third party app
        if (callerAppState != null && callerAppState.mProcState > FOREGROUND_THRESHOLD_STATE
            && !REASON_START_ACTIVITY.equals(reason)) {
            if (DEBUG) {
                Slog.d(TAG,"Start Proc : "+targetApp
                    +", callingPackage = "+callerApp
                    + " (ProcState:" + (callerAppState != null ? Util.ProcState2Str(callerAppState.mProcState):"none")
                    +"), reason = "+reason
                    + ": denyed!!");
            }
            return false;
        }

        // not allow self started third party app to start other third party app
        if (callerAppState != null && callerAppState.mLaunchCount == 0 && !isSystemApp(callerAppState)
            && !REASON_START_ACTIVITY.equals(reason)) {
            if (DEBUG) Slog.d(TAG,"Start Proc : "+targetApp+", callingPackage = "+callerApp+", reason = "+reason + ": denyed!!");
            return false;
        }

        // not allow long idle third party app to start other third party app
        if (callerAppState != null && !isSystemApp(callerAppState)
            && callerAppState.mState != Event.MOVE_TO_FOREGROUND
            && callerAppState.mProcState != ActivityManager.PROCESS_STATE_TOP
            && !REASON_START_ACTIVITY.equals(reason)) {

            long nowELAPSED = SystemClock.elapsedRealtime();
            long idleDuration = 0;
            idleDuration = (callerAppState.mLastTimeUsed > 0 ? (nowELAPSED -callerAppState.mLastTimeUsed) : -1);
            if (idleDuration > DENY_START_APP_THRESHOLD) {
                if (DEBUG) Slog.d(TAG,"Start Proc : "+targetApp+", callingPackage = "+callerApp+", reason = "+reason + ": denyed!! long idle");
                return false;
            }
        }


        // not allow third party app to start other third party app during standby
        if (mStandbyStartTime > 0
            && !REASON_START_ACTIVITY.equals(reason)) {
            if (DEBUG) {
                Slog.d(TAG,"Start Proc : "+targetApp
                    +", callingPackage = "+callerApp
                    + " (ProcState:" + (callerAppState != null ? Util.ProcState2Str(callerAppState.mProcState):"none")
                    +"), reason = "+reason
                    + ": denyed during standby!!");
            }
            return false;
        }


        return true;

    }

    private boolean isSystemApp(AppState appState) {
        if (appState != null) {
            return ((appState.mFlags & (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP | ApplicationInfo.FLAG_SYSTEM)) != 0
                || appState.mFlags == 0);
        } else {
            return true; // default
        }
    }


    private boolean isMessageApp(AppState appState) {
        if (appState != null) {
            int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);
            if (PowerDataBaseControl.MESSAGE == appType) {
                return true;
            } else {
                return false;
            }
        } else {
            return true; // default
        }
    }


    // download judging is using the data received by the app, so it will has some latency
    private boolean isDoingDownload(AppState appState) {
        if (appState != null) {
            if (appState.mProcState <= ActivityManager.PROCESS_STATE_SERVICE) {
                if (mPowerControllerInternal.isAppDoingDownload(appState)) {
                    return true;
                }
            }
            return false;
        } else {
            return true; // default
        }
    }

    private boolean isPlayingMusic(AppState appState) {
        if (appState != null) {
            int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);

                if (mPowerControllerInternal.isPlayingMusic(appState)) {
                    return true;
                }
            //}
            return false;
        } else {
            return true; // default
        }
    }

    // app type is unknown
    // and procState <= FOREGROUND_THRESHOLD_STATE
    // and system is playing music
    // then we see this app maybe playing music
    // NOTE: if we has more exact method to judge app playing music, then we don't need to do this
    private boolean mayBePlayingMusic(AppState appState) {
        if (appState != null) {
            int appType = mPowerControllerInternal.getAppCategoryType(appState.mPackageName);
            if (PowerDataBaseControl.UNKNOWN == appType
                && appState.mProcState <= FOREGROUND_THRESHOLD_STATE
                && mPowerControllerInternal.isPlayingMusic(appState)) {
                if (DEBUG) Slog.d(TAG, "mayBePlayingMusic: " + appState.mPackageName + " UNKNOWN App type");
                return true;
            }

            return false;
        } else {
            return false; // default
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
                //StatusBarNotification[] active = list.toArray(new StatusBarNotification[list.size()]);

                if (list != null && list.size() > 0) {
                    hasNotification = true;
                }
            }
        } catch (Exception e) {}

        return hasNotification;
   }

    /**
     * Common white list, that is  for both lockScreen manager and auto self starting manager
     */
    private boolean inCommonWhiteAppList(String pkgName) {
        if (pkgName == null) return true;

/** auto start and lock screen clean do not need to care about doze white list
 *        try {
 *         if (mPowerControllerInternal.isWhitelistApp(pkgName)) {
 *               return true;
 *           }
 *       } catch (Exception e) {
 *       }
*/

        /*check if in internal white app list, like CTS app*/
        for(String s : mInternalWhiteAppList) {
            if(pkgName.contains(s)) {
                if (DEBUG) Slog.d(TAG, "Internal white applist: " + pkgName);
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


    private boolean inSelfStartWhiteAppList(String pkgName) {
        if (inCommonWhiteAppList(pkgName))
            return true;

        // if setting in the LaunchWhiteList in pwctl_config.xml
        if (mPowerConfig != null && mPowerConfig.inLaunchWhiteList(pkgName)) {
            if (DEBUG) Slog.d(TAG, "in PowerConfig launch white list: " + pkgName);
            return true;
        }

        return false;
    }

    private boolean isLauncherApp(String pkgName) {
        int index = mLauncherAppList.indexOf(pkgName);
        if (index >= 0) {
            return true;
        }
        return false;
    }

    private boolean isInstalledApp(String pkgName, int userId) {
        ArrayMap<String, PackageInfo> mInstalledAppList = getInstalledAppList(userId);

        PackageInfo targetPkg = mInstalledAppList.get(pkgName);
        if (targetPkg != null) {
            return true;
        }
        return false;
    }

    private boolean isForceStoppedAppDuringStandby(String pkgName, int uid) {
        ArrayMap<String, Integer> mStoppedAppList = getStoppedAppList(UserHandle.getUserId(uid));

        return mStoppedAppList.containsKey(pkgName);
    }

    // input method app
    private boolean isInputMethodApp(String pkgName) {
        int index = mEnabledInputMethodAppList.indexOf(pkgName);
        if (index >= 0) {
            return true;
        }
        return false;
    }

    private boolean isLaunchingIMEApp(Intent intent, String targetApp, int targetUid,
        String callerApp, String reason) {

        //handle inputmethod
        if ("android".equals(callerApp)
            && REASON_BIND_SERVICE.equals(reason)
            && intent != null && "android.view.InputMethod".equals(intent.getAction())) {

            if (targetApp != null) { // a input method
                int index = mEnabledInputMethodAppList.indexOf(targetApp);
                if (index < 0) {
                    mEnabledInputMethodAppList.add(targetApp);
                    int userId = UserHandle.getUserId(targetUid);
                    mAppStateInfoCollector.updateAppInputMethodState(targetApp, true, userId);
                    mAppStateInfoCollector.setDefaultInputMethodApp(targetApp, userId);
                }
            }
            return true;
        }

        // allow to start input Method
        if (REASON_BIND_SERVICE.equals(reason) && "android".equals(callerApp) && isInputMethodApp(targetApp)) {
            if (DEBUG) Slog.d(TAG, "isLaunchingIMEApp: "+targetApp
                + ", callingPackage = "+callerApp+", reason = "+reason +" allow for input method");
            return true;
        }

        return false;
    }

    private boolean isRecognizerIntent(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason) {
        AppState callerAppState = mAppStateInfoCollector.getAppState(callerApp, UserHandle.getUserId(callerUid));
        if (isSystemApp(callerAppState)
            && REASON_START_ACTIVITY.equals(reason)
            && intent != null && RecognizerIntent.ACTION_WEB_SEARCH.equals(intent.getAction())) {
            return true;
        }

        return false;
    }

    // check if this a launched operation by user
    // INPUT: ignoreTouchTime if true, then don't see TouchElasedTime <= (1*1000) as a userActivity
    //            else see TouchElasedTime <= (1*1000) as a userActivity for Top App
    // NOTE: this cannot decide exactly
    private boolean launchedByUserActivity(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason, boolean ignoreTouchTime) {
        boolean userLaunched = false;

        long now = SystemClock.elapsedRealtime();
        long lastTouchTime = 0;
        AppState callerAppState = mAppStateInfoCollector.getAppState(callerApp, UserHandle.getUserId(callerUid));
        if (mPowerControllerInternal != null) {
            lastTouchTime = mPowerControllerInternal.getLastTouchEventTimeStamp();
        }
        //Add for Bug 897839 BEG
        // if system app start other app using 'start-activity'
        if (!isInstalledApp(callerApp, UserHandle.getUserId(callerUid))
            && REASON_START_ACTIVITY.equals(reason)
            && (now - lastTouchTime < 1000)) {
            return true;
        }
        //Add for Bug 897839 END

        boolean isCallerTopApp  =  false;
        if (callerAppState != null
            &&((callerAppState.mProcState == ActivityManager.PROCESS_STATE_TOP)
                || (callerAppState.mProcState == ActivityManager.PROCESS_STATE_HOME)
                || (callerAppState.mState == Event.MOVE_TO_FOREGROUND)
                || (now - callerAppState.mLastTimeUsed) < 1000
                || isLauncherApp(callerApp)) // for Bug#712736
        ) {

            isCallerTopApp = true;
        }

        // check the recognize 
        if (isCallerTopApp &&
            REASON_START_ACTIVITY.equals(reason)
            && !isLauncherAction(intent)
            && isAutoStartRecognizeEnabled()
            && !mAutoStartRecognize.isAutoStartAllowed(intent, targetApp,
                targetUid, callerApp, callerUid, reason)) {
            isCallerTopApp = false;
        }

        // check the associated-starting because of third party push service
        if (isCallerTopApp &&
            REASON_START_ACTIVITY.equals(reason)
            && !isLauncherAction(intent)
            && intent != null
            && mThirdpartyPush.isAssociatedComponent(intent.getComponent())) {
            if (DEBUG) Slog.d(TAG,"launchedByUserActivity : Start Proc : "+targetApp
                +", callingPackage = "+callerApp
                + " (ProcState:" + (callerAppState != null ? Util.ProcState2Str(callerAppState.mProcState):"none")
                +"), reason = "+reason
                + " Associated-Starting ThirdParty Push Service");
            isCallerTopApp = false;
        }

        // see the caller of a launcher action as top app
        // add for bug#776461
        if (!isCallerTopApp && intent != null && isLauncherAction(intent)) {
            isCallerTopApp = true;
        }

        //Add for Bug 897839 BEG
        // for bug#897839 any method to judge starting from notification menu??
        if (!isCallerTopApp && intent != null && isViewAction(intent)) {
            isCallerTopApp = true;
        }
        //Add for Bug 897839 END

        if (DEBUG_MORE) {
            Slog.d(TAG,"launchedByUserActivity : Start Proc : "+targetApp
                +", callingPackage = "+callerApp
                + " (ProcState:" + (callerAppState != null ? Util.ProcState2Str(callerAppState.mProcState):"none")
                +"), reason = "+reason);
        }

        long lastTouchElasedTime = now - lastTouchTime;
        if (DEBUG) Slog.d(TAG, "lastTouchElasedTime: "+lastTouchElasedTime);

        if (isCallerTopApp
            && (REASON_START_ACTIVITY.equals(reason)
                    || (!ignoreTouchTime && lastTouchElasedTime <= (1*1000)))
            ) {
            userLaunched = true;
        }

        // check if this call from "android"
        if (!isCallerTopApp && REASON_START_ACTIVITY.equals(reason) && lastTouchElasedTime <= (1*1000)) {
            AppState androidState = mAppStateInfoCollector.getAppState("android", UserHandle.USER_SYSTEM);
            if (androidState != null
                && (androidState.mState == Event.MOVE_TO_FOREGROUND
                    || (now - androidState.mLastTimeUsed) < 1000)) {
                userLaunched = true;
            }
        }

        // Bug#707888 setting monkey fail --> BEG
        // for callerApp is system app, and use "start-activity", then allow
        if ((callerAppState == null || callerAppState.mUid < Process.FIRST_APPLICATION_UID)
            && REASON_START_ACTIVITY.equals(reason)) {
            userLaunched = true;
        }
        // Bug#707888 setting monkey fail <-- END

        return userLaunched;
    }

    private boolean inAssociateLaunchExceptionAppList(String pkgName) {
        if (pkgName == null) return true;

        /*check if in internal white app list, like CTS app*/
        for(String s : mExceptionAppListForAssociateLaunch) {
            if(pkgName.contains(s)) {
                return true;
            }
        }

        return false;
    }

    // return true, if app is start by system broadcast
    private boolean autoLaunchByOtherApp(String targetApp, int targetUid,
                String callerApp, int callerUid, String reason) {
        if (callerApp == null
            ||callerApp.equals("android")
            || callerApp.equals("com.android.systemui"))
            return false;

        AppState callerAppState = mAppStateInfoCollector.getAppState(callerApp, UserHandle.getUserId(callerUid));
        if (callerAppState != null && callerAppState.mUid < Process.FIRST_APPLICATION_UID)
            return false;

        return true;
    }

    // return launch state according to user setting
    private int checkLaunchStateByUserSetting(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason) {

        if (targetApp == null || targetApp.equals(callerApp))
            return LAUNCH_STATE_AUTO;

        boolean launchByOther = autoLaunchByOtherApp(targetApp, targetUid, callerApp, callerUid, reason);

        // allow app in whitelist
        if (inSelfStartWhiteAppList(targetApp)) {
            return LAUNCH_STATE_ALLOW;
        }

        // if in ultrasaving mode, don't allow autostart except apps in ultrasve applist
        /* changd for bug#960889, allow to start, but will clean when standby
        if (!launchByOther && (PowerManagerEx.MODE_ULTRASAVING == mPowerSaveMode)
            && (PowerManagerEx.MODE_ULTRASAVING == mNextPowerSaveMode)) {
            List<String> appList = mPowerControllerInternal.getAppList_UltraMode();
            if (!appList.contains(targetApp)) {
                if (DEBUG) Slog.d(TAG, "app: " + targetApp + " not in applist of ultramode, refuse to autostart, denyed");
                return LAUNCH_STATE_DENY;
            }
        }
        */

        int autoLaunch = mPowerControllerInternal.getAppPowerSaveConfgWithTypeInternal(targetApp,AppPowerSaveConfig.ConfigType.TYPE_AUTOLAUNCH.value);
        int secondLaunch = mPowerControllerInternal.getAppPowerSaveConfgWithTypeInternal(targetApp,AppPowerSaveConfig.ConfigType.TYPE_SECONDARYLAUNCH.value);
        if (((autoLaunch == AppPowerSaveConfig.VALUE_NO_OPTIMIZE) && !launchByOther)
            || ((secondLaunch == AppPowerSaveConfig.VALUE_NO_OPTIMIZE) && launchByOther)) {
            if (DEBUG) Slog.d(TAG, "bgclean judgeAppLaunchAllowed: "+targetApp
                + ", callingPackage = "+callerApp+", reason = "+reason +" in my whitelist");
            return LAUNCH_STATE_ALLOW;
        }

        //check whether app is in black list
        boolean bInBlackList = false;
        if ((autoLaunch == AppPowerSaveConfig.VALUE_OPTIMIZE) && !launchByOther) {
            if (DEBUG_MORE) Slog.d(TAG, "in apppowerconfig autolaunch black list: " + targetApp);
            bInBlackList = true;
        }
        if ((secondLaunch == AppPowerSaveConfig.VALUE_OPTIMIZE) && launchByOther) {
            if (DEBUG_MORE) Slog.d(TAG, "in apppowerconfig 2ndlaunch black list: " + targetApp);
            bInBlackList = true;
        }

        // check whether blacklist app is in exception
        if (bInBlackList) {
            // not allow auto start app that in auto start black list
            // 1. in mAutoLaunch_BlackList AND is not launched by other app (that is launched by system broadcast etc)
            // 2. NOTE: Exception:
            //     1) Start reason is REASON_START_ACTIVITY  (this is alway happened in case of
            //         app use another app to do something, including launcher to launch a app)
            //     2) Self start self ( that is this app is alreadby started, and is start some activity internal)
            if (!launchByOther && !REASON_START_ACTIVITY.equals(reason)) {
                if (DEBUG) Slog.d(TAG, "in autolaunch black list: "+targetApp
                    + ", callingPackage = "+callerApp+", reason = "+reason +" denyed!");
                return LAUNCH_STATE_DENY;
            }
            // not allow auto start by other app that in secondary start black list
            // 1. in m2ndLaunch_BlackList AND is launched by other app
            // 2. NOTE: Exception:
            //     1) when callerApp is top AND Start reason is REASON_START_ACTIVITY  (this is alway happened in case of
            //         app use another app to do something, including launcher to launch a app)
            //     2) Self start self ( that is this app is alreadby started, and is start some activity internal)
            //     3) when callerApp is top AND targetApp is in AssociateLaunchExceptionAppList
            if (launchByOther && !launchedByUserActivity(intent, targetApp, targetUid, callerApp, callerUid, reason, true)) {
                if (DEBUG) Slog.d(TAG, "in 2ndlaunch black list: "+targetApp
                    + ", callingPackage = "+callerApp+", reason = "+ reason + " intent:" + intent + " denyed!");
                return LAUNCH_STATE_DENY;
            }

            // Although it is black list, but it start by user, so allowed to start
            return LAUNCH_STATE_ALLOW;
        }
        return LAUNCH_STATE_AUTO;
    }

    /**
     * Check the preset, including:
     * (1) system app should be allow to be self started
     * (2) system app that is the launcher black app list should not be self started
     */
    private int  checkLaunchStateByPreset(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason) {
        ArrayMap<String, PackageInfo> mInstalledAppList = getInstalledAppList(UserHandle.getUserId(targetUid));

        PackageInfo targetPkg = mInstalledAppList.get(targetApp);
        // if not a system app, just return STATE_AUTO
        if (targetPkg != null) {
            return LAUNCH_STATE_AUTO;
        }

        if (mPowerConfig != null && mPowerConfig.inLaunchBlackList(targetApp)
            && !launchedByUserActivity(intent, targetApp, targetUid, callerApp, callerUid, reason, true)) {
            if (DEBUG) Slog.d(TAG, "in PowerConfig launch black list: " + targetApp + " denyed!!");
            return LAUNCH_STATE_DENY;
        }

        return LAUNCH_STATE_ALLOW;
    }

    private void updateVisibleActivities() {
        try {
            // Clear first
            //mVisibleAppList.clear();

            List<IBinder> activityTokens = null;

            // Let's get top activities from all visible stacks
            activityTokens = LocalServices.getService(ActivityManagerInternal.class).getTopVisibleActivities();
            final int count = activityTokens.size();

            for (int i = 0; i < count; i++) {
                IBinder topActivity =  activityTokens.get(i);
                try {
                    String  packageName = mActivityManager.getPackageForToken(topActivity);
                    if (packageName != null) {
                        if (mVisibleAppList.indexOf(packageName) < 0) {
                            mVisibleAppList.add(packageName);
                            Slog.d(TAG, "new VisibleActivities:" + packageName);
                        }
                    }
                } catch (RemoteException e) {
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (DEBUG) {
            Slog.d(TAG, "mVisibleAppList: " + mVisibleAppList.size());
            for (int i=0;i<mVisibleAppList.size();i++) {
                Slog.d(TAG, "App:" + mVisibleAppList.get(i));
            }
        }

        Slog.d(TAG, "updateVisibleActivities done");
    }


    private void registerForBroadcasts() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        intentFilter.addDataScheme("package");

        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (Intent.ACTION_PACKAGE_ADDED.equals(action)
                        ||Intent.ACTION_PACKAGE_REPLACED.equals(action) ) {
                        Uri data = intent.getData();
                        String pkgName = data.getEncodedSchemeSpecificPart();
                        scheduleUpdateInstalledPackages(pkgName);
                    } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                        if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                            Uri data = intent.getData();
                            String ssp;
                            if (data != null && (ssp = data.getSchemeSpecificPart()) != null) {
                                scheduleUpdateRemovedPackages(ssp);
                            }
                        }
                    }
                }
            }, intentFilter);


        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                        mCurrentUserId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0);
                        Slog.d(TAG, "ACTION_USER_SWITCHED : mCurrentUserId:" + mCurrentUserId);
                        mHandler.post(new Runnable() {
                            public void run() {
                                try {
                                    onUserSwitched();
                                } catch (Exception e) {}
                            }
                        });
                    }
                }
            }, filter);

        IntentFilter sdcardfilter = new IntentFilter();
        sdcardfilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        sdcardfilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();

                    Slog.d(TAG, "listener SD Card mounted,action: " + action);
                    if (Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(action)) {
                        Slog.d(TAG, "SD Card mounted, update installed packages list ");
                        loadInstalledPackages();
                    }
                }
            }, sdcardfilter);

    }

    private void scheduleUpdateInstalledPackages(String newPkgName) {
        mHandler.postDelayed(new Runnable() {
            public void run() {
                try {
                    PackageInfo pkg = mContext.getPackageManager().getPackageInfoAsUser(
                        newPkgName, 0, mCurrentUserId);

                    ArrayMap<String, PackageInfo> mInstalledAppList = getInstalledAppList(mCurrentUserId);

                    if(pkg !=null && pkg.applicationInfo !=null
                        && (pkg.applicationInfo.flags &
                           (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP |ApplicationInfo.FLAG_SYSTEM)) == 0) {
                        Slog.d(TAG, "new APK:" + pkg.packageName + " is installed! for userId:" + mCurrentUserId);
                        mInstalledAppList.put(pkg.packageName, pkg);
                    }

                    if (pkg !=null && pkg.packageName != null
                        && mContext.getPackageManager().getLaunchIntentForPackage(pkg.packageName) == null
                        && !mInstalledServiceList.contains(pkg.packageName)) {
                        mInstalledServiceList.add(pkg.packageName);
                    }

                    updateLauncherApps(newPkgName, true);
                    mSystemPreferredConfig.onAppInstalled();
                    checkPermissionList(newPkgName, mCurrentUserId);
                } catch (Exception e) {}
            }
        },2000);
    }

    private void scheduleUpdateRemovedPackages(String pkgName) {
        mHandler.post(new Runnable() {
            public void run() {
                try {
                    updateLauncherApps(pkgName, false);
                } catch (Exception e) {}
            }
        });
    }
    // if this app is a input Method
    private void loadEnabledInputMethodApp(){

        // clear first
        for (int i=0;i<mEnabledInputMethodAppList.size();i++) {
            Slog.d(TAG, "Clear InputMethodApp:" + mEnabledInputMethodAppList.get(i));
            mAppStateInfoCollector.updateAppInputMethodState(mEnabledInputMethodAppList.get(i), false, UserHandle.myUserId());
        }
        mEnabledInputMethodAppList.clear();

        IInputMethodManager service = IInputMethodManager.Stub.asInterface(ServiceManager.getService(Context.INPUT_METHOD_SERVICE));
        List<InputMethodInfo> inputMethods;
        try {
            inputMethods = service.getEnabledInputMethodList();
        } catch (RemoteException e) {
            return;
        }
        if (inputMethods == null || inputMethods.size() == 0) return;
        for (InputMethodInfo info : inputMethods){
            if (info == null || info.getPackageName() == null) continue;
            String appName = info.getPackageName();
            int index = mEnabledInputMethodAppList.indexOf(appName);
            if (index < 0) {
                mEnabledInputMethodAppList.add(appName);
                mAppStateInfoCollector.updateAppInputMethodState(appName, true, UserHandle.myUserId());
            }
        }

        if (mEnabledInputMethodAppList.size() > 0)
            mNeedReloadEnabledInputMethodAppList = false;
        else
            scheduleReloadEnabledInputMethod(1000);

        if (DEBUG) {
            Slog.d(TAG, "mEnabledInputMethodAppList: " + mEnabledInputMethodAppList.size());
            for (int i=0;i<mEnabledInputMethodAppList.size();i++) {
                Slog.d(TAG, "App:" + mEnabledInputMethodAppList.get(i));
            }
        }
    }

    private void scheduleReloadEnabledInputMethod(long delayMillis) {
        mHandler.postAtTime(new Runnable() {
            public void run() {
                try {
                    loadEnabledInputMethodApp();
                } catch (Exception e) {}
            }
        }, delayMillis);
    }

    class SettingsObserver extends ContentObserver {
        private final ContentResolver mResolver;

        SettingsObserver(Handler handler, ContentResolver resolver) {
            super(handler);
            mResolver = resolver;
            mResolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.ENABLED_INPUT_METHODS), false, this);
        }

        @Override public void onChange(boolean selfChange, Uri uri) {
            mNeedReloadEnabledInputMethodAppList = true;
            scheduleReloadEnabledInputMethod(500);
        }
    }


    private boolean isDefaultWallpaperService(String pkgName) {

        //if (DEBUG_MORE) Slog.d(TAG, "target: " + pkgName + " defaultWallpaper:" + mDefaultWallpaperService);

        return (pkgName != null
            && mDefaultWallpaperService != null
            && pkgName.equals(mDefaultWallpaperService));
    }

    private boolean isImportantPrefferedApp(String pkgName) {
        if (pkgName == null) return false;

        return (isDefaultWallpaperService(pkgName)
            || mSystemPreferredConfig.isDefaultPhoneApp(pkgName)
            || mSystemPreferredConfig.isDefaultSmsApp(pkgName)
            || mInstalledServiceList.contains(pkgName));
    }

    private boolean isAlreadyStarted(AppState appState) {
        // if target app already exist
        if ((appState != null
                && appState.mProcState < ActivityManager.PROCESS_STATE_CACHED_EMPTY
                && appState.mProcState != ActivityManager.PROCESS_STATE_NONEXISTENT)
            || (appState != null
                && appState.mProcState == ActivityManager.PROCESS_STATE_CACHED_EMPTY
                && Event.NONE != appState.mState)) {
            return true;
        }
        return false;
    }

    private boolean isUserTouchActive() {

        long now = SystemClock.elapsedRealtime();

        long lastTouchTime = 0;
        if (mPowerControllerInternal != null)
            lastTouchTime = mPowerControllerInternal.getLastTouchEventTimeStamp();

        return ((now - lastTouchTime) <= 1000);
    }

    private boolean isImportantCoreApp(String pkgName) {
        if (pkgName == null) return false;

        return (Util.isGmsCoreApp(pkgName)
            || mInstalledServiceList.contains(pkgName));
    }

    private boolean isLauncherAction(Intent intent) {
        if (intent == null) return false;

        if (Intent.ACTION_MAIN.equals(intent.getAction())
            && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
            && (intent.getComponent() != null)) {
            return true;
        }
        return false;
    }

    //Add for Bug 897839 BEG
    private boolean isViewAction(Intent intent) {
        if (intent == null || (intent.getComponent() == null)) return false;

        // for bug#897839 any method to judge starting from notification menu??
        if (Intent.ACTION_VIEW.equals(intent.getAction())
            || Intent.ACTION_QUICK_VIEW.equals(intent.getAction())) {
            return true;
        }

        return false;
    }
    //Add for Bug 897839 END

    // return true: for should be allow to be started
    //   false: for others
    private boolean handleBindServiceStartAction(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason) {

        if (!REASON_BIND_SERVICE.equals(reason)) return false;

        // check start wallpaper service
        if (intent != null && WallpaperService.SERVICE_INTERFACE.equals(intent.getAction())) {

            // save the defalut wallpaper service
            if ("android".equals(callerApp))  mDefaultWallpaperService = targetApp;

            if (DEBUG) Slog.d(TAG, "start wallpaper service, allowed");
            return true;
        }

        if ("android".equals(callerApp)
            && intent != null && mSystemPreferredConfig.isEnabledAccessibilityService(intent.getComponent())
            ) {
            if (DEBUG) Slog.d(TAG, "start enabled accessibility service" + intent.getComponent());
            return true;
        }

        // check start accessibility service
        if ("android".equals(callerApp)
            && intent != null && mSystemPreferredConfig.isInstalledAccessibilityService(intent.getComponent())
            && isUserTouchActive()) {

            if (DEBUG) Slog.d(TAG, "start installed accessibility service" + intent.getComponent());
            return true;
        }

        // check start voice synthesize service
        if (intent != null && mSystemPreferredConfig.isTTSAction(intent.getAction())
            && isUserTouchActive()) {

            if (DEBUG) Slog.d(TAG, "start voice synthesize service" + intent.getAction());
            return true;
        }

        // check start print service
        if ("android".equals(callerApp)
            && intent != null && mSystemPreferredConfig.isInstalledPrintService(intent.getComponent())
            && isUserTouchActive()) {

            if (DEBUG) Slog.d(TAG, "start installed print service" + intent.getComponent());
            return true;
        }

        // for sina
        if ( intent != null  && "com.sina.weibo.remotessoservice".equals(intent.getAction())
            && "com.sina.weibo".equals(targetApp)) {
            if (DEBUG) Slog.d(TAG, "allow Start:using sina to login:" +callerApp);
            return true;
        }

        // check start default phone service
        if (intent != null && "android.telecom.InCallService".equals(intent.getAction())) {

            if (DEBUG) Slog.d(TAG, "start default phone app:" + targetApp);
            return true;
        }

        if (mInstalledServiceList.contains(targetApp)
            && autoLaunchByOtherApp(targetApp, targetUid, callerApp, callerUid, reason)) {
            if (DEBUG) Slog.d(TAG, "bind installed service :" + targetApp + " from " + callerApp);
            return true;
        }

        // for bug#778255
        if ("android".equals(callerApp)
            && intent != null  && ACTION_AUTHENTICATOR_INTENT.equals(intent.getAction())
            && isUserTouchActive()) {
            if (DEBUG) Slog.d(TAG, "bind Account service :" + targetApp + " from " + callerApp);
            return true;
        }

        if (intent != null && "com.google.firebase.MESSAGING_EVENT".equals(intent.getAction())) {
            if (DEBUG) Slog.d(TAG, "start app:" + targetApp + " for receiving FCM message");
            AppState targetAppState = mAppStateInfoCollector.getAppState(targetApp, UserHandle.getUserId(targetUid));
            if (targetAppState != null) {
                targetAppState.mFCMorGCMForAppIdle = true;
                targetAppState.mFCMorGCMForBgClean = true;
                mPowerControllerInternal.reCheckAllAppInfoDelayed(0);
            }
            return true;
        }

        // for bug#966540
        if ("com.android.systemui".equals(callerApp)
            && intent != null && mSystemPreferredConfig.isInstalledTileService(intent.getComponent())
            /*&& isUserTouchActive()*/) {

            if (DEBUG) Slog.d(TAG, "start installed tile service" + intent.getComponent());
            return true;
        }

        //fix bug : 1130383
        if ("android".equals(callerApp)
                && intent != null && intent.getComponent() != null && intent.getAction() == null) {
            AppState targetAppState = mAppStateInfoCollector.getAppState(targetApp, UserHandle.getUserId(targetUid));
            if (targetAppState != null && targetAppState.mTotalLaunchCount > 0) {
                if (DEBUG) Slog.d(TAG, "bind service：" + intent.getComponent());
                return true;
            }
        }

        return false;
    }


    // return true: for should be allow to be started
    //   false: for others
    private boolean handleStartServiceStartAction(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason) {

        if (!REASON_START_SERVICE.equals(reason)) return false;

        if (mInstalledServiceList.contains(targetApp)
            && autoLaunchByOtherApp(targetApp, targetUid, callerApp, callerUid, reason)) {
            if (DEBUG) Slog.d(TAG, "start installed service :" + targetApp + " from " + callerApp);
            return true;
        }

        return false;
    }

    // return true: for should be allow to be started
    //   false: for others
    private boolean handleContentProviderStartAction(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason) {
        if (!REASON_CONTENT_PROVIDER.equals(reason)) return false;

        int targetAppType = mPowerControllerInternal.getAppCategoryType(targetApp);;
        AppState callerAppState = mAppStateInfoCollector.getAppState(callerApp, UserHandle.getUserId(callerUid));

        boolean isCallerTopApp  =  false;
        if (callerAppState != null
            &&((callerAppState.mProcState == ActivityManager.PROCESS_STATE_TOP)
                || (callerAppState.mState == Event.MOVE_TO_FOREGROUND))
        ) {

            isCallerTopApp = true;
        }

        if (isCallerTopApp && isUserTouchActive()
            && (inAssociateLaunchExceptionAppList(targetApp) || PowerDataBaseControl.UNKNOWN == targetAppType)) {

            if (PowerDataBaseControl.UNKNOWN == targetAppType
                && isAutoStartRecognizeEnabled()
                && !mAutoStartRecognize.isAutoStartAllowed(intent, targetApp,
                    targetUid, callerApp, callerUid, reason)) {
                if (DEBUG) Slog.d(TAG," not allow Start: "+targetApp+", callingPackage = "+callerApp+ " reason = "+reason);
                return false;
            }

            if (DEBUG) Slog.d(TAG,"allow Start: "+targetApp+", callingPackage = "+callerApp+ " reason = "+reason);

            return true;
        }

        return false;
    }

    private boolean handleAppWidgetStartAction(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason) {
        if (REASON_BROADCAST.equals(reason) && intent != null
            && "android".equals(callerApp)) {
            String action = intent.getAction();
            if (action != null && action.startsWith("android.appwidget.action.")) //AppWidget action
                return true;
        }
        return false;
    }


    private boolean handleBroadcastAction(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason) {
        if (!REASON_BROADCAST.equals(reason) || intent == null) return false;

        if (intent != null
            && "android".equals(callerApp)) {
            String action = intent.getAction();
            if (action != null && action.startsWith("android.appwidget.action.")) //AppWidget action
                return true;
        }

        // check out call for phone app
        if ("android".equals(callerApp)
            && intent != null && Intent.ACTION_NEW_OUTGOING_CALL.equals(intent.getAction())
            && mSystemPreferredConfig.isDefaultPhoneApp(targetApp)) {

            if (DEBUG) Slog.d(TAG, "start phone app for out call:" + targetApp);
            return true;
        }

        if (SMS_DELIVER_ACTION.equals(intent.getAction())) {
            if (DEBUG) Slog.d(TAG, "start default sms app:" + targetApp);
            return true;
        }

        // receive GMS C2DM message for bug#787547
        if ("com.google.android.gms".equals(callerApp)
            && "com.google.android.c2dm.intent.RECEIVE".equals(intent.getAction())) {

            if (DEBUG) Slog.d(TAG, "start app:" + targetApp + " for receiving message from GCM");
            AppState targetAppState = mAppStateInfoCollector.getAppState(targetApp, UserHandle.getUserId(targetUid));
            if (targetAppState != null) {
                targetAppState.mFCMorGCMForAppIdle = true;
                targetAppState.mFCMorGCMForBgClean = true;
                mPowerControllerInternal.reCheckAllAppInfoDelayed(0);
            }
            return true;
        }

        return false;
    }


    // return true: for should not be allow to be started
    //   false: for others
    private boolean denyBecauseOfUltraSavingMode(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason) {

        // Bug#766329
        if ((PowerManagerEx.MODE_ULTRASAVING == mPowerSaveMode
            && "com.android.launcher3".equals(targetApp))) {
            if (DEBUG) Slog.d(TAG, "app: " + targetApp + " in ultramode, refuse to start, denyed");
            return true;
        }

        if (PowerManagerEx.MODE_ULTRASAVING != mNextPowerSaveMode
            && POWERSAVE_LAUNCHER_PACKNAME.equals(targetApp)) {
            if (DEBUG) Slog.d(TAG, "app: " + targetApp + " not in ultramode, refuse to start, denyed");
            return true;
        }

        return false;
    }


    // disable/enable appropriate launcher app for saving mode 
    private void updateLauncherAppEnabledSettings(int newMode) {

        // if mLauncherAppList is empty and mBootCompleted is false, then try to load launcher app first
        if (!mBootCompleted && mLauncherAppList.size() == 0
            && PowerManagerEx.MODE_ULTRASAVING == newMode) {
            Slog.d(TAG, "updateLauncherAppEnabledSettings: try to load launcher apps first!");
            loadLauncherApps();
        }

        int state = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        boolean changed = false;

        int stateOfSaveLauncher = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;

        try {
            stateOfSaveLauncher = AppGlobals.getPackageManager()
                .getApplicationEnabledSetting(POWERSAVE_LAUNCHER_PACKNAME, mCurrentUserId);
        } catch (Exception e) {
            Slog.d(TAG, "updateLauncherAppEnabledSettings Exception:" + e);
        }
        // new mode is not ultra saving mode, then disable com.sprd.powersavemodelauncher
        if (PowerManagerEx.MODE_ULTRASAVING != newMode
            && stateOfSaveLauncher != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {

            Slog.d(TAG, "updateLauncherAppEnabledSettings disable powersavemodelauncher");
            try {
                AppGlobals.getPackageManager().setApplicationEnabledSetting(POWERSAVE_LAUNCHER_PACKNAME,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0, mCurrentUserId, mContext.getOpPackageName());
            } catch (Exception e) {
                Slog.d(TAG, "updateLauncherAppEnabledSettings Exception:" + e);
            }
        } else if (PowerManagerEx.MODE_ULTRASAVING == newMode
            && stateOfSaveLauncher != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            Slog.d(TAG, "updateLauncherAppEnabledSettings enable powersavemodelauncher");
            // enable com.sprd.powersavemodelauncher first
            try {
                AppGlobals.getPackageManager().setApplicationEnabledSetting(POWERSAVE_LAUNCHER_PACKNAME,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0, mCurrentUserId, mContext.getOpPackageName());
            } catch (Exception e) {
                Slog.d(TAG, "updateLauncherAppEnabledSettings Exception:" + e);
            }
        }

        if ((PowerManagerEx.MODE_ULTRASAVING  == mPowerSaveMode
                && PowerManagerEx.MODE_ULTRASAVING != newMode)
            || (DEFAULT_POWER_MODE == mPowerSaveMode
                && newMode == mPowerSaveMode)) {
            // exit ultra saving mode
            state = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            changed =true;
        } else if (PowerManagerEx.MODE_ULTRASAVING  != mPowerSaveMode
            && PowerManagerEx.MODE_ULTRASAVING == newMode) {
            // enter ultra saving mode
            state = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            changed = true;
        }

        if (changed) {
            int enabledCount = 0;
            ArrayList<String> launcherAppList = getLauncherList();
            for (int i=0;i<launcherAppList.size();i++) {
                String name = launcherAppList.get(i);
                if ( name != null && name.contains(POWERSAVE_LAUNCHER_PACKNAME)) continue;
                if (PackageManager.COMPONENT_ENABLED_STATE_DEFAULT == state
                    && !mPowerControllerInternal.isDisabledLauncherApp(name)) {
                    if (DEBUG) Slog.d(TAG, "updateLauncherAppEnabledSettings:" + name
                        + " NOT disable by PowerContoller, DON'T enable it here!");
                    continue;
                }

               if (PackageManager.COMPONENT_ENABLED_STATE_DISABLED == state) {
                    int enabled = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
                    try {
                        enabled = getApplicationEnabledSetting(name, mCurrentUserId);
                    } catch (Exception e) {
                        Slog.d(TAG, "updateLauncherAppEnabledSettings Exception:" + e);
                    }
                    boolean packageDisabled = (enabled != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            && enabled != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
                    if (packageDisabled) {
                        if (DEBUG) Slog.d(TAG, "updateLauncherAppEnabledSettings:" + name
                            + " is disabled by other app, DON'T disable it here!");
                        continue;
                    }

                    String packageName = name;
                    if (USE_COMPONENT)
                        packageName =  ComponentName.unflattenFromString(name).getPackageName();

                    if (ArrayUtils.contains(mExceptionLauncherAppList, packageName)) {
                        if (DEBUG) Slog.d(TAG, "updateLauncherAppEnabledSettings:" + packageName
                            + " NOT a launcher app, DON'T disable it here!");
                        continue;
                    }
                }

                //Slog.d(TAG, "updateLauncherAppEnabledSettings:" + name + " state:" + state
                //    + " newMode:" + newMode);
                try {
                    //SPRD: modify for Bug 886084 BEG
                    boolean ret = setApplicationEnabledSetting(name, state, 0/*PackageManager.DONT_KILL_APP*/,
                        mCurrentUserId, mContext.getOpPackageName());
                    //SPRD: modify for Bug 886084 END
                    if (PackageManager.COMPONENT_ENABLED_STATE_DEFAULT == state && ret) enabledCount++;
                    else if (PackageManager.COMPONENT_ENABLED_STATE_DISABLED == state) {
                        mPowerControllerInternal.addDisabledLauncherAppWithoutSave(name);
                    }
                } catch (Exception e) {
                    Slog.d(TAG, "updateLauncherAppEnabledSettings Exception:" + e);
                }
            }

            if (enabledCount == 0
                && PackageManager.COMPONENT_ENABLED_STATE_DEFAULT == state) {
                Slog.d(TAG, "updateLauncherAppEnabledSettings: NOT launcher app is enabled.ENABLE default!");

                // using disable component instead of disable the whole app
                // when enter/exit ultra-saving mode. See bug#819868
                if (USE_COMPONENT) {
                    for(String s : mDefaultLauncherAppComponentList) {
                        try {
                            ComponentName component = ComponentName.unflattenFromString(s);
                            AppGlobals.getPackageManager().setComponentEnabledSetting(component, state, 0,
                                mCurrentUserId);
                            if (DEBUG) Slog.d(TAG, "updateLauncherAppEnabledSettings: enable " + component + " state:" + state
                                    + " newMode:" + newMode);
                        } catch (Exception e) {
                            Slog.d(TAG, "updateLauncherAppEnabledSettings Exception:" + e);
                            mEnableDefaultLauncherFailed = true;
                        }
                    }
                } else {
                    for(String s : mDefaultLauncherAppList) {
                        try {
                            AppGlobals.getPackageManager().setApplicationEnabledSetting(s, state, 0,
                                mCurrentUserId, mContext.getOpPackageName());
                            if (DEBUG) Slog.d(TAG, "updateLauncherAppEnabledSettings: enable " + s + " state:" + state
                                + " newMode:" + newMode);
                        } catch (Exception e) {
                            Slog.d(TAG, "updateLauncherAppEnabledSettings Exception:" + e);
                            mEnableDefaultLauncherFailed = true;
                        }
                    }
                }
            }
            // clear the disabled launcher app list
            if (PackageManager.COMPONENT_ENABLED_STATE_DEFAULT == state) {
                mPowerControllerInternal.updateDisabledLauncherAppList(null);
            }
        }

    }

    // to disable the launcher app specified in 'disabledAppList'
    // input: 'disabledAppList' is a string contains the package name of app sperated by ';'
    public void disbleLauncherAppIfNeed(String disabledAppList) {
        if (disabledAppList == null) return;

        if (PowerManagerEx.MODE_ULTRASAVING  == mPowerSaveMode) {
            String[] apps = disabledAppList.split(";");
            if (apps == null) return;
            for (String app : apps) {
                try {
                    setApplicationEnabledSetting(app,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0,
                        mCurrentUserId, mContext.getOpPackageName());
                    if (DEBUG) Slog.d(TAG, "disbleLauncherAppIfNeed: disable " + app);

                    // using disable component instead of disable the whole app
                    // when enter/exit ultra-saving mode. See bug#819868
                    if (USE_COMPONENT) {
                        if (app != null && !mLauncherAppComponentList.contains(app)) {
                            mLauncherAppComponentList.add(app);
                        }
                    } else {
                        if (app != null && !mLauncherAppList.contains(app)) {
                            mLauncherAppList.add(app);
                        }
                    }
                } catch (Exception e) {
                    Slog.d(TAG, "disbleLauncherAppIfNeed Exception:" + e);
                }
            }
        }
    }


    // check to disable the launcher app specified in mLauncherAppList
    private void disbleLauncherAppIfNeed() {
        if (PowerManagerEx.MODE_ULTRASAVING  == mPowerSaveMode) {
            ArrayList<String> launcherAppList = getLauncherList();
            for (int i=0;i<launcherAppList.size();i++) {
                String name = launcherAppList.get(i);
                if (name != null && name.contains(POWERSAVE_LAUNCHER_PACKNAME)) continue;

                int enabled = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
                try {
                    enabled = getApplicationEnabledSetting(name, mCurrentUserId);
                } catch (Exception e) {
                    Slog.d(TAG, "disbleLauncherAppIfNeed Exception:" + e);
                }
                boolean packageDisabled = (enabled != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        && enabled != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
                if (packageDisabled) {
                    if (DEBUG) Slog.d(TAG, "disbleLauncherAppIfNeed:" + name
                        + " is disabled by other app, DON'T disable it here!");
                    continue;
                }

                String packageName = name;
                if (USE_COMPONENT)
                    packageName =  ComponentName.unflattenFromString(name).getPackageName();

                if (ArrayUtils.contains(mExceptionLauncherAppList, packageName)) {
                    if (DEBUG) Slog.d(TAG, "disbleLauncherAppIfNeed:" + packageName
                        + " NOT a launcher app, DON'T disable it here!");
                    continue;
                }

                Slog.d(TAG, "disbleLauncherAppIfNeed: disable " + name);
                try {
                    setApplicationEnabledSetting(name,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0,
                        mCurrentUserId, mContext.getOpPackageName());
                    mPowerControllerInternal.addDisabledLauncherApp(name);
                } catch (Exception e) {
                    Slog.d(TAG, "disbleLauncherAppIfNeed Exception:" + e);
                }
            }
        } else {
            int stateOfSaveLauncher = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            try {
                stateOfSaveLauncher = AppGlobals.getPackageManager()
                    .getApplicationEnabledSetting(POWERSAVE_LAUNCHER_PACKNAME, mCurrentUserId);
            } catch (Exception e) {
                Slog.d(TAG, "disbleLauncherAppIfNeed Exception:" + e);
            }
            // new mode is not ultra saving mode, then disable com.sprd.powersavemodelauncher
            if (stateOfSaveLauncher != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {

                Slog.d(TAG, "disbleLauncherAppIfNeed disable powersavemodelauncher");
                try {
                    AppGlobals.getPackageManager().setApplicationEnabledSetting(POWERSAVE_LAUNCHER_PACKNAME,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0,
                        mCurrentUserId, mContext.getOpPackageName());
                } catch (Exception e) {
                    Slog.d(TAG, "disbleLauncherAppIfNeed Exception:" + e);
                }
            }
        }
    }

    void onUserSwitched() {
        disbleLauncherAppIfNeed();
        /*******
        if (mUerSwitchedInUltraSavingMode) {
            boolean needStartHomeActivity = ensureLauncherAppEnabledStatus();
            if (needStartHomeActivity) {
                try {
                    if (DEBUG) Slog.d(TAG, "onUserSwitched(): need start home activity!");
                    mContext.startActivityAsUser(mHomeIntent, UserHandle.CURRENT);
                } catch (Exception e) {
                    Slog.d(TAG, "startActivityAsUser fail!");
                }
            }
        } else if (PowerManagerEx.MODE_ULTRASAVING  == mPowerSaveMode
           && (mCurrentUserId !=0)) {
            mUerSwitchedInUltraSavingMode = true;

            ensureLauncherAppEnabledStatus();
        }
        **********/
        ensureLauncherAppEnabledStatus();
    }

    // using disable component instead of disable the whole app
    // when enter/exit ultra-saving mode. See bug#819868
    // --> START
    private boolean setApplicationEnabledSetting(String appPackageName,
            int newState, int flags, int userId, String callingPackage) {

        try {
            if (USE_COMPONENT) {
                ComponentName component = ComponentName.unflattenFromString(appPackageName);

                AppGlobals.getPackageManager().setComponentEnabledSetting(component, newState, flags, userId);


                if (DEBUG) Slog.d(TAG, "setApplicationEnabledSetting: newState:" + newState
                    + " component:" + component);

            } else {
                AppGlobals.getPackageManager().setApplicationEnabledSetting(appPackageName,
                    newState, flags, userId, callingPackage);
            }

        } catch (Exception e) {
            Slog.d(TAG, "setApplicationEnabledSetting Exception:" + e);
            return false;
        }

        return true;
    }

    private int getApplicationEnabledSetting(String name, int userId) {

        if (USE_COMPONENT) {
            int state = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            ComponentName component = ComponentName.unflattenFromString(name);

            try {
                state = AppGlobals.getPackageManager().getComponentEnabledSetting(component, userId);
            } catch (Exception e) {
                Slog.d(TAG, "getApplicationEnabledSetting Exception:" + e);
            }

            if (DEBUG) Slog.d(TAG, "getApplicationEnabledSetting: "
                + " state:" + state);

            return state;
        } else {
            int enabled = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;

            try {
                enabled = AppGlobals.getPackageManager().getApplicationEnabledSetting(name, userId);
            } catch (Exception e) {
                Slog.d(TAG, "getApplicationEnabledSetting Exception:" + e);
            }
            return enabled;
        }
    }

    private int getApplicationLauncherEnabledSetting(String packageName, int userId) {

        if (USE_COMPONENT) {
            ComponentName launcherComp = null;
            int launcherState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setPackage(packageName);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);

            ResolveInfo info = mContext.getPackageManager().resolveActivityAsUser(intent, PackageManager.MATCH_DISABLED_COMPONENTS, mCurrentUserId);

            if (DEBUG) Slog.d(TAG, "getApplicationLauncherEnabledSetting: "
                + " appPackageName:" + packageName
                + " launcher info:" + info);
            if (info != null && info.activityInfo != null) {
                launcherComp = new ComponentName(packageName, info.activityInfo.name);
                try {
                    launcherState = AppGlobals.getPackageManager().getComponentEnabledSetting(launcherComp, userId);
                } catch (Exception e) {
                    Slog.d(TAG, "getApplicationLauncherEnabledSetting Exception:" + e);
                }
            }
            return launcherState;
        } else {
            int enabled = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;

            try {
                enabled = AppGlobals.getPackageManager().getApplicationEnabledSetting(packageName, userId);
            } catch (Exception e) {
                Slog.d(TAG, "getApplicationLauncherEnabledSetting Exception:" + e);
            }
            return enabled;
        }
    }

    // check if normal launcher app is enabled when exit ultra-saving mode
    // return ture, if actually doing enable operation for launcher apps
    private boolean checkLauncherEnabledSetting() {
        if (PowerManagerEx.MODE_ULTRASAVING  == mPowerSaveMode) return false;

        boolean ret = false;

        ArrayList<String> launcherAppList = getLauncherList();
        for (int i=0;i<launcherAppList.size();i++) {
            String name = launcherAppList.get(i);
            if (name != null && name.contains(POWERSAVE_LAUNCHER_PACKNAME)) continue;

            int enabled = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            try {
                enabled = getApplicationEnabledSetting(name, mCurrentUserId);
            } catch (Exception e) {
                Slog.d(TAG, "checkLauncherEnabledSetting Exception:" + e);
            }
            boolean packageDisabled = (enabled != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    && enabled != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
            if (!packageDisabled) {
                if (DEBUG) Slog.d(TAG, "checkLauncherEnabledSetting:" + name
                    + " is enabled!");
                continue;
            }

            String packageName = name;
            if (USE_COMPONENT)
                packageName =  ComponentName.unflattenFromString(name).getPackageName();

            if (packageName == null) continue;
            if (ArrayUtils.contains(mExceptionLauncherAppList, packageName)) {
                if (DEBUG) Slog.d(TAG, "checkLauncherEnabledSetting:" + packageName
                    + " NOT a launcher app!");
                continue;
            }

            if (!isInstalledApp(packageName, mCurrentUserId)
                && packageName.contains("provision")
                && packageName.startsWith("com.android.")
                && !isDefaultLauncherApp(packageName)) {
                if (DEBUG) Slog.d(TAG, "checkLauncherEnabledSetting:" + packageName
                    + " NOT need to enable!");
                continue;
            }

            if (DEBUG) Slog.d(TAG, "checkLauncherEnabledSetting: enable " + name);
            try {
                setApplicationEnabledSetting(name,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0,
                    mCurrentUserId, mContext.getOpPackageName());

                ret = true;
            } catch (Exception e) {
                Slog.d(TAG, "checkLauncherEnabledSetting Exception:" + e);
            }
        }

        return ret;
    }


    // ensure all the launcher apps are enable properly
    // return ture, if an launcher app is setting enabled
    private boolean ensureLauncherAppEnabledStatus() {
        if (DEBUG) Slog.d(TAG, "ensureLauncherAppEnabledStatus()");
        boolean ret = false;
        if (PowerManagerEx.MODE_ULTRASAVING  == mPowerSaveMode) {
            int stateOfSaveLauncher = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            try {
                stateOfSaveLauncher = AppGlobals.getPackageManager()
                    .getApplicationEnabledSetting(POWERSAVE_LAUNCHER_PACKNAME, mCurrentUserId);
            } catch (Exception e) {
                Slog.d(TAG, "ensureLauncherAppEnabledStatus Exception:" + e);
            }
            boolean packageDisabled = (stateOfSaveLauncher != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    && stateOfSaveLauncher != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);

            // new mode is ultra saving mode, then enable com.sprd.powersavemodelauncher
            if (packageDisabled) {
                if (DEBUG) Slog.d(TAG, "ensureLauncherAppEnabledStatus enable powersavemodelauncher");
                try {
                    AppGlobals.getPackageManager().setApplicationEnabledSetting(POWERSAVE_LAUNCHER_PACKNAME,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0,
                        mCurrentUserId, mContext.getOpPackageName());
                    ret = true;
                } catch (Exception e) {
                    Slog.d(TAG, "ensureLauncherAppEnabledStatus Exception:" + e);
                }
            }
        } else {
            ret = checkLauncherEnabledSetting();
        }

        return ret;
    }

    private void loadLauncherComponent(int userId) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        for (int i=0;i<mLauncherAppList.size();i++) {
            String packageName = mLauncherAppList.get(i);
            if (ArrayUtils.contains(mExceptionLauncherAppList, packageName)) {
                if (DEBUG) Slog.d(TAG, "loadLauncherComponent:" + packageName
                    + " NOT a launcher app!");
                continue;
            }

            intent.setPackage(packageName);

            List<ResolveInfo> resolves =
                mContext.getPackageManager().queryIntentActivitiesAsUser(intent, PackageManager.MATCH_DISABLED_COMPONENTS, userId);

            // Look for the original activity in the list...
            final int SIZE = resolves != null ? resolves.size() : 0;
            for (int n=0; n<SIZE; n++) {
                final ResolveInfo candidate = resolves.get(n);
                final ActivityInfo info = candidate.activityInfo;
                if (info != null && info.packageName != null
                     && POWERSAVE_LAUNCHER_PACKNAME.equals(info.packageName)) {
                    if (!mLauncherAppComponentList.contains(info.packageName)) {
                        mLauncherAppComponentList.add(info.packageName);
                    }
                    Slog.d(TAG, "homeLauncherComponent:" + info.packageName);
                    continue;
                }

                if (info != null && info.packageName != null) {
                    ComponentName cn = new ComponentName(info.packageName, info.name);
                    String componetName = cn.flattenToShortString();
                    if (!mLauncherAppComponentList.contains(componetName)) {
                        mLauncherAppComponentList.add(componetName);
                    }
                    Slog.d(TAG, "homeLauncherComponent:" + componetName);
                }
            }
        }

    }

    private ArrayList<String> getLauncherList() {
        if (USE_COMPONENT)
            return mLauncherAppComponentList;

        return mLauncherAppList;
    }

    private boolean isDefaultLauncherApp(String appName) {
        if (appName == null) return false;
        for(String s : mDefaultLauncherAppList) {
            if(appName.equals(s)) {
                return true;
            }
        }
        return false;
    }

    // using disable component instead of disable the whole app
    // when enter/exit ultra-saving mode. See bug#819868
    // <-- END


    private void checkPermissionList(String packageName, int userId) {
        PackageManager pm = mContext.getPackageManager();
        PackageInfo pi;
        try {
            // use PackageManager.GET_PERMISSIONS
            pi = pm.getPackageInfoAsUser(packageName, PackageManager.GET_PERMISSIONS, userId);
            String[] permissions = pi.requestedPermissions;
            if(permissions != null){
                if (DEBUG_MORE) {
                    Slog.d(TAG, "Permission for app:" + packageName);
                    for(String str : permissions) {
                        Slog.d(TAG, str);
                    }
                }
                if (checkAdminAppPermissionList(permissions)) {
                    Slog.d(TAG, "ADMIN APP:" + packageName);
                    if (!mInstalledAdminAppList.contains(packageName))
                        mInstalledAdminAppList.add(packageName);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private boolean checkAdminAppPermissionList(String[] permissions) {
        for (String p: mAdminAppPermissionList) {
            boolean found = false;
            for (String str: permissions) {
                if (str.equals(p)) found = true;
            }
            if (!found) return false;
        }
        return true;
    }

    // if a app is a type of admin app, such as com.baidu.appsearch
    public boolean isAdminApp(String packageName) {
        if (packageName == null) return false;

        return (mInstalledAdminAppList.contains(packageName));
    }

    private boolean isSaveMode(int mode) {
        return (mode == PowerManagerEx.MODE_POWERSAVING
            || mode == PowerManagerEx.MODE_LOWPOWER
            || mode == PowerManagerEx.MODE_ULTRASAVING
            || mode == PowerManagerEx.MODE_INVALID);//see invalid as save mode
    }

    void onWakefulnessChangeFinished(boolean screenOn) {
        if (DEBUG) Slog.d(TAG, "onWakefulnessChangeFinished screenOn:" + screenOn);

        if (mWakefulnessOn == screenOn) return;

        mWakefulnessOn = screenOn;
        if (screenOn) {
            //mVisibleAppList.clear();
            return;
        } else {
            updateVisibleActivities();
        }
    }

    public void onWakefulnessChangeStarted(final int wakefulness, int reason) {
        if (DEBUG) Slog.d(TAG, "- onWakefulnessChangeStarted() wakefulness: " + wakefulness + " reason:" + reason);
        if (PowerManagerInternal.isInteractive(wakefulness))
            mWakefulnessChangingOn = true;
        else
            mWakefulnessChangingOn = false;
    }

    void onScreenChanged(boolean screenOn) {
        if (DEBUG) Slog.d(TAG, "onScreenChanged mWakefulnessOn:" + mWakefulnessOn + " screenOn:" + screenOn);

        mScreenOn = screenOn;
    }

    boolean checkBackgroundApp(AppState appState, final long nowELAPSED) {

        boolean bChanged = false;

        if (canBeConstrained(appState)) {
            bChanged = true;

            try {
                //mActivityManager.forceStopPackage(appState.mPackageName, appState.mUserId);
                if (DEBUG) Slog.d(TAG, "checkBackgroundApp(2) & killApplication--->PackageName: " + appState.mPackageName + " uid:" + appState.mUid);
                mActivityManager.killApplication(appState.mPackageName, UserHandle.getAppId(appState.mUid), appState.mUserId, REASON_KILL_APP);
                if (DEBUG) Slog.d(TAG, "checkBackgroundApp(2) & killApplication:send PowerManager.ACTION_POWER_CONTROLLER_KILL_APP broacast!!");
                finishKillApplication(appState.mPackageName, appState.mUid);
                if (isAutoStartRecognizeEnabled() && mAutoStartRecognize.hasPendingAutoStartCallerInfo(appState.mPackageName))
                    mAutoStartRecognize.clearAutoStartCallerInfo(appState.mPackageName);

                // do some cleaning for appState
                appState.clearLaunchInfo();

                if (DEBUG) Slog.d(TAG, "checkBackgroundApp: force stop:" + appState.mPackageName + " userId:" + appState.mUserId);

            } catch (Exception e) {
                Slog.w(TAG, "checkBackgroundApp: exception: " + e);
            }

        }
        return bChanged;
    }


    boolean checkBackgroundApp(AppState appState, String openApp, String closeApp) {
        if (appState == null) return false;

        boolean bChanged = false;

        if (!appState.mPackageName.equals(openApp)
            && !appState.mPackageName.equals(closeApp)
            && canBeConstrained(appState)) {
            bChanged = true;

            try {
                //mActivityManager.forceStopPackage(appState.mPackageName, appState.mUserId);
                if (DEBUG) Slog.d(TAG, "checkBackgroundApp(3) & killApplication--->PackageName: " + appState.mPackageName + " uid:" + appState.mUid);
                mActivityManager.killApplication(appState.mPackageName, UserHandle.getAppId(appState.mUid), appState.mUserId, REASON_KILL_APP);
                if (DEBUG) Slog.d(TAG, "checkBackgroundApp(3) & killApplication:send PowerManager.ACTION_POWER_CONTROLLER_KILL_APP broacast!!");
                finishKillApplication(appState.mPackageName, appState.mUid);
                if (isAutoStartRecognizeEnabled() && mAutoStartRecognize.hasPendingAutoStartCallerInfo(appState.mPackageName))
                    mAutoStartRecognize.clearAutoStartCallerInfo(appState.mPackageName);

                // do some cleaning for appState
                appState.clearLaunchInfo();

                if (DEBUG) Slog.d(TAG, "checkBackgroundApp: force stop:" + appState.mPackageName + " userId:" + appState.mUserId
                    + " openApp:" + openApp + " closeApp:" + closeApp);

            } catch (Exception e) {
                Slog.w(TAG, "checkBackgroundApp: exception: " + e);
            }

        }
        return bChanged;
    }

    void handleAppTransitionStarting(String openApp, String closeApp) {
        if (openApp != null || closeApp != null) return;

        boolean add = true;
        if (!mWakefulnessOn && !mWakefulnessChangingOn)
            add = true;
        else if (mWakefulnessChangingOn)
            add = false;

        if (DEBUG) Slog.d(TAG, "handleAppTransitionStarting: mWakefulnessChangingOn:" + mWakefulnessChangingOn
            + " mWakefulnessOn:" +mWakefulnessOn
            + " mVisibleAppList.size:" + mVisibleAppList.size()
            + " add:" + add);

        if (!add) return;

        int changeCount = 0;
        try {
            List<IBinder> activityTokens = null;

            // Let's get top activities from all visible stacks
            activityTokens = LocalServices.getService(ActivityManagerInternal.class).getTopVisibleActivities();
            final int count = activityTokens.size();

            for (int i = 0; i < count; i++) {
                IBinder topActivity =  activityTokens.get(i);
                String  packageName = mActivityManager.getPackageForToken(topActivity);
                if (packageName != null) {
                    if (DEBUG) Slog.d(TAG, "current VisibleActivities:" + packageName);
                    int index = mVisibleAppList.indexOf(packageName);
                    if (add && index <0) {
                        mVisibleAppList.add(packageName);
                        changeCount++;
                        if (DEBUG) Slog.d(TAG, "new VisibleActivities:" + packageName);
                    } else if (!add && index >= 0) {
                        mVisibleAppList.remove(index);
                        changeCount++;
                        if (DEBUG) Slog.d(TAG, "remove VisibleActivities:" + packageName);
                    }
                }
            }

            if (changeCount == 0 && !add && count > 0) {
                mVisibleAppList.clear();
                if (DEBUG) Slog.d(TAG, "Clear VisibleActivities for home");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (DEBUG) {
            Slog.d(TAG, "mVisibleAppList: " + mVisibleAppList.size());
            for (int i=0;i<mVisibleAppList.size();i++) {
                Slog.d(TAG, "App:" + mVisibleAppList.get(i));
            }
        }
    }

    void handleVisibleAppChanged(ArrayList<String> visibleAppList) {
        if (visibleAppList == null) return;

        if (isAutoStartRecognizeEnabled()) {
            mAutoStartRecognize.updateVisibleAppList(visibleAppList);
        }

        if (!mScreenOn || mVisibleAppList.size() == 0) {
            mRemovedVisibleAppList.clear();
            return;
        }

        int changeCount = 0;
        boolean found = false;
        try {
            List<IBinder> activityTokens = null;

            // Let's get top activities from all visible stacks
            activityTokens = LocalServices.getService(ActivityManagerInternal.class).getTopVisibleActivities();
            final int count = activityTokens.size();

            for (int i = 0; i < count; i++) {
                IBinder topActivity =  activityTokens.get(i);
                String  packageName = mActivityManager.getPackageForToken(topActivity);
                if (packageName != null) {
                    if (DEBUG) Slog.d(TAG, "current VisibleActivities:" + packageName);
                    int index = mVisibleAppList.indexOf(packageName);
                     if (index >= 0) {
                        mVisibleAppList.remove(index);
                        changeCount++;
                        if (DEBUG) Slog.d(TAG, "remove VisibleActivities:" + packageName);
                        mRemovedVisibleAppList.add(packageName);
                    } else if (mRemovedVisibleAppList.indexOf(packageName) >= 0) {
                        found = true;
                    }
                }
            }

            if (changeCount == 0 && count > 0 && !found) {
                mVisibleAppList.clear();
                mRemovedVisibleAppList.clear();
                if (DEBUG) Slog.d(TAG, "Clear VisibleActivities");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (DEBUG) {
            Slog.d(TAG, "mVisibleAppList: " + mVisibleAppList.size());
            for (int i=0;i<mVisibleAppList.size();i++) {
                Slog.d(TAG, "App:" + mVisibleAppList.get(i));
            }
        }

    }

    void handleNotificationUpdate(String appName, int uid) {
        if (appName != null && appName.equals("android")) return;

        if (!isAutoStartRecognizeEnabled()) return;

        if (!isInstalledApp(appName, UserHandle.getUserId(uid))) return;

        // has other method to judge ??
        try {
            INotificationManager inm = NotificationManager.getService();
            final ParceledListSlice<StatusBarNotification> parceledList
                    = inm.getAppActiveNotifications(appName, UserHandle.getUserId(uid));
            final List<StatusBarNotification> list = parceledList.getList();

            int N = list.size();
            ArrayList<Intent> intentList = mNotificationIntentList.get(appName);
            if (intentList == null) {
                intentList = new ArrayList<>();
                mNotificationIntentList.put(appName, intentList);
            }

            if (N > 0) {
                intentList.clear();
            }

            for (int i = 0; i < N; i++) {
                StatusBarNotification sbn = list.get(i);
                Notification notification = sbn.getNotification();
                if (notification != null && notification.contentIntent != null) {
                    Intent intent = notification.contentIntent.getIntent();
                    if (intent != null) {
                        Intent saveIntent = (Intent)intent.clone();
                        Bundle extra = saveIntent.getExtras();

                        if (DEBUG_MORE) Slog.d(TAG, "handleNotificationUpdate: intent for " + appName+ " intent:" + intent + " extra:" + extra);
                        intentList.add(saveIntent);
                    }
                }
            }

        } catch (Exception e) {}

    }

    private boolean needAutoStartRecognize(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason) {

        if (targetApp == null || targetApp.equals(callerApp))
            return false;

        int targetUserId = UserHandle.getUserId(targetUid);
        AppState targetAppState = mAppStateInfoCollector.getAppState(targetApp, targetUserId);
        if (isAlreadyStarted(targetAppState))
            return false;

        AppState callerAppState = mAppStateInfoCollector.getAppState(callerApp, UserHandle.getUserId(callerUid));
        long now = SystemClock.elapsedRealtime();

        boolean isCallerTopApp  =  false;
        if (callerAppState != null
            &&((callerAppState.mProcState == ActivityManager.PROCESS_STATE_TOP)
                || (callerAppState.mProcState == ActivityManager.PROCESS_STATE_HOME)
                || (callerAppState.mState == Event.MOVE_TO_FOREGROUND)
                || (now - callerAppState.mLastTimeUsed) < 1000
                || isLauncherApp(callerApp)) // for Bug#712736
        ) {

            isCallerTopApp = true;
        }

        if (isCallerTopApp &&
            REASON_START_ACTIVITY.equals(reason)
            && !isLauncherAction(intent)) {
            return true;
        }

        isCallerTopApp = false;
        if (callerAppState != null
            &&((callerAppState.mProcState == ActivityManager.PROCESS_STATE_TOP)
                || (callerAppState.mState == Event.MOVE_TO_FOREGROUND))
        ) {

            isCallerTopApp = true;
        }

        int targetAppType = mPowerControllerInternal.getAppCategoryType(targetApp);;
        if (isCallerTopApp
            && REASON_CONTENT_PROVIDER.equals(reason)
            && PowerDataBaseControl.UNKNOWN == targetAppType
            && isUserTouchActive()) {
                return true;
        }

        return false;
    }

    private boolean callFromNotification(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason) {

        if (callerApp == null) return false;
        ArrayList<Intent> intentList = mNotificationIntentList.get(callerApp);
        if (intentList == null) return false;

        for (int i=0;i<intentList.size();i++) {
            Intent sIntent =  intentList.get(i);
            if (sIntent != null && sIntent.getExtras() != null) {
                if (intent == null
                    || intent.getExtras() == null
                    || intent.getExtras().getSize() != sIntent.getExtras().getSize())
                    continue;
            }

            if (intent != null && intent.filterEquals(sIntent)) {
                if (DEBUG_MORE) Slog.d(TAG, "callFromNotification: intent for " + targetApp+ " intent:" + intent + " from caller:" + callerApp + " reason:" + reason
                    + " extra: " + intent.getExtras() + " sIntent:" + sIntent + " extra:" + sIntent.getExtras());
                return true;
            } else if (intent != null
                && intent.getAction() != null && intent.getData() != null
                && Objects.equals(intent.getAction(), sIntent.getAction())
                && Objects.equals(intent.getData(), sIntent.getData())
                && Objects.equals(intent.getType(), sIntent.getType())
                && Objects.equals(intent.getPackage(), sIntent.getPackage())
                && Objects.equals(intent.getCategories(), sIntent.getCategories())) {
                if (DEBUG_MORE) Slog.d(TAG, "callFromNotification: intent for " + targetApp+ " intent:" + intent + " from caller:" + callerApp + " reason:" + reason
                    + " extra: " + intent.getExtras() + " sIntent:" + sIntent + " extra:" + sIntent.getExtras());
                return true;
            }
        }
        return false;
    }

    private boolean needClearAutoStartRecognize(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason, AllowLevel allowLevel) {

        if (targetApp == null || (targetApp.equals(callerApp) && allowLevel.mLevel != ALLOW_LEVEL_STARTED))
            return false;

        if (isAutoStartRecognizeEnabled() && !mAutoStartRecognize.hasPendingAutoStartCallerInfo(targetApp))
            return false;

        if (allowLevel.mLevel == ALLOW_LEVEL_STARTED
            && targetApp.equals(callerApp)
            && callFromNotification(intent, targetApp, targetUid,
            callerApp, callerUid, reason)) {
            return true;
        }

        if (allowLevel.mLevel == ALLOW_LEVEL_STARTED && !REASON_START_ACTIVITY.equals(reason)) {
            return false;
        }

        AppState callerAppState = mAppStateInfoCollector.getAppState(callerApp, UserHandle.getUserId(callerUid));
        long now = SystemClock.elapsedRealtime();

        boolean isCallerTopApp  =  false;
        if (callerAppState != null
            &&((callerAppState.mProcState == ActivityManager.PROCESS_STATE_TOP)
                || (callerAppState.mProcState == ActivityManager.PROCESS_STATE_HOME)
                || (callerAppState.mState == Event.MOVE_TO_FOREGROUND)
                || (now - callerAppState.mLastTimeUsed) < 1000
                || isLauncherApp(callerApp)) // for Bug#712736
        ) {

            isCallerTopApp = true;
        }

        if (isCallerTopApp &&
            REASON_START_ACTIVITY.equals(reason)
            && !isLauncherAction(intent)) {
            return false;
        } else if (allowLevel.mLevel == ALLOW_LEVEL_STARTED && !isCallerTopApp) {
            return false;
        }

        isCallerTopApp = false;
        if (callerAppState != null
            &&((callerAppState.mProcState == ActivityManager.PROCESS_STATE_TOP)
                || (callerAppState.mState == Event.MOVE_TO_FOREGROUND))
        ) {

            isCallerTopApp = true;
        }

        int targetAppType = mPowerControllerInternal.getAppCategoryType(targetApp);;
        if (isCallerTopApp
            && REASON_CONTENT_PROVIDER.equals(reason)
            && PowerDataBaseControl.UNKNOWN == targetAppType
            && isUserTouchActive()) {
            return false;
        }


        return true;
    }

    private boolean isAutoStartRecognizeEnabled() {
        return (mAutoStartRecognizeEnabled && (mAutoStartRecognize != null));
    }

    private AppUsageStatsCollection mAppUsageStatsCollection;

    private boolean constraintByUsageHabit(AppState appState) {

        if (!mCleanByUsageEnabled) return false;

        if (mAppUsageStatsCollection == null)
            mAppUsageStatsCollection = AppUsageStatsCollection.getInstance();

        long now = System.currentTimeMillis(); //wall time

        boolean notificationConstraint = ((!appState.mHasNotification && mStandbyStartTime == 0)
            || (!appState.mHasNoClearNotification && mStandbyStartTime > 0));
            
        int timeInterval = mAppUsageStatsCollection.getNextFavoriteTimeInterval(appState.mPackageName, now);

        long nowELAPSED = SystemClock.elapsedRealtime();
        long idleDuration = 0;
        boolean hasLaunched = false;
        long invisibleDuration = nowELAPSED - appState.mLastInvisibleTime;

        if (appState.mLastLaunchTime > 0
            && appState.mLastVisibleTime > appState.mLastStopTime) hasLaunched = true;
        idleDuration = (appState.mLastTimeUsed > 0 ? (nowELAPSED -appState.mLastTimeUsed) : -1);

        if (DEBUG) Slog.d(TAG, "constraintByUsageHabit: STATE: pkg:" + appState.mPackageName
            + " idle for:" + idleDuration
            + " hasLaunched:" + hasLaunched
            + " mProcState:" + Util.ProcState2Str(appState.mProcState)
            + " mState:" + Util.AppState2Str(appState.mState)
            + " mHasNotification:" + appState.mHasNotification
            + " mHasNoClearNotification:" + appState.mHasNoClearNotification
            + " mVisible:" + appState.mVisible
            + " timeInterval:" + timeInterval
            + " notificationConstraint:" + notificationConstraint
            + " invisibleDuration:" + invisibleDuration);

        if (timeInterval > 0 
            && idleDuration > (10*60*1000)
            && invisibleDuration > (30*60*1000)
            && appState.mProcState != ActivityManager.PROCESS_STATE_TOP
            && !appState.mVisible
            && appState.mState != Event.MOVE_TO_FOREGROUND
            && notificationConstraint ) {

            if (DEBUG) Slog.d(TAG, "constraintByUsageHabit CONSTRAINT: STATE: pkg:" + appState.mPackageName
                + " idle for:" + idleDuration
                + " hasLaunched:" + hasLaunched
                + " mProcState:" + Util.ProcState2Str(appState.mProcState)
                + " mState:" + Util.AppState2Str(appState.mState)
                + " mHasNotification:" + appState.mHasNotification
                + " mHasNoClearNotification:" + appState.mHasNoClearNotification
                + " mVisible:" + appState.mVisible
                + " timeInterval:" + timeInterval);
            return true;
        }
        return false;
    }

    private boolean contraintInUltraSavingMode(AppState appState) {
        if (appState == null) return false;
        if ((PowerManagerEx.MODE_ULTRASAVING != mPowerSaveMode)
            || (PowerManagerEx.MODE_ULTRASAVING != mNextPowerSaveMode)) return false;

        long nowElapsed = SystemClock.elapsedRealtime();
        long delayMs = nowElapsed- mStandbyStartTime;
        long idleDuration;

        idleDuration = (appState.mLastTimeUsed > 0 ? (nowElapsed -appState.mLastTimeUsed) : -1);

        // delay 1s
        if (mStandbyStartTime <= 0 || (delayMs < 1000/*0 && idleDuration < 10000*/)) return false;

        boolean ret = false;
        List<String> ultramodeAppList = mPowerControllerInternal.getAppList_UltraMode();
        try {
            if (ultramodeAppList.contains(appState.mPackageName)) {
                //if (DEBUG) Slog.d(TAG, "contraintInUltraSavingMode: " + appState.mPackageName + " in ultramodeAppList");
                return false;
            }

            // app not exist
            if ((appState.mProcState == ActivityManager.PROCESS_STATE_CACHED_EMPTY && Event.NONE == appState.mState)
                || appState.mProcState == ActivityManager.PROCESS_STATE_NONEXISTENT) {
                return false;
            }

            // not app in whitelist
            if (inCommonWhiteAppList(appState.mPackageName) || inDozeWhiteList(appState.mPackageName)) {
                return false;
            }

            // avoid kill input method
            if (appState.mIsEnabledInputMethod) {
                return false;
            }

            if (isDefaultWallpaperService(appState.mPackageName))
                return false;

            // not a visible app
            int index = mVisibleAppList.indexOf(appState.mPackageName);
            if (index >= 0 || appState.mVisible) {
                return false;
            }

            ret = true;
            boolean bFind = false;
            if (isSystemApp(appState)) {
                for(String s : mBlackAppListForUltraSaving) {
                    if(appState.mPackageName.contains(s)) {
                        bFind = true;
                        ret = true;
                        break;
                    }
                }
                if (!bFind) {
                    ret = false;
                }
            }

            if (ret) {
                if (DEBUG) Slog.d(TAG, "contraintInUltraSavingMode: " + appState.mPackageName + " is constraint to run");
            }
        } catch (Exception e) {}

        return ret;
    }


    private boolean inDozeWhiteList(String pkgName) {

        try {
            if (mPowerControllerInternal.isWhitelistApp(pkgName)) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    private boolean isDebug(){
        return Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug");
    }


    private boolean isAutoTest(int callerUid, String callerApp, String targetApp) {
        if (callerUid == Process.SHELL_UID) {
            if (DEBUG) Slog.d(TAG, "calling from shell, see as doing auto test: " + targetApp);
            return true;
        }
        return false;
    }

    String dump() {
        String out = TAG + "\n";
        out += "[mAutoLaunch_WhiteList]: " + mAutoLaunch_WhiteList + "\n";
        out += "[mAutoLaunch_BlackList]: " + mAutoLaunch_BlackList + "\n";
        out += "[m2ndLaunch_WhiteList]: " + m2ndLaunch_WhiteList + "\n";
        out += "[m2ndLaunch_BlackList]: " + m2ndLaunch_BlackList + "\n";
        out += "[mLockScreen_WhiteList]: " + mLockScreen_WhiteList + "\n";
        out += "[mLockScreen_BlackList]: " + mLockScreen_BlackList + "\n";
        return out;
    }

    private void dumpInstalledAppList() {
        for (int index=mInstalledAppListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, PackageInfo> mInstalledAppList = mInstalledAppListForUsers.valueAt(index);

            Slog.d(TAG, "mInstalledAppList for userId: " + mInstalledAppListForUsers.keyAt(index));
            for (String key : mInstalledAppList.keySet()) {
                Slog.d(TAG, "App:" + key);
            }
        }

    }

    private void clearAllForceStopAppList() {
        for (int index=mForceStopAppListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, Integer> mForceStopAppList = mForceStopAppListForUsers.valueAt(index);
            mForceStopAppList.clear();
        }
    }

    private void clearAllStoppedAppList() {
        for (int index=mStoppedAppListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, Integer> mStoppedAppList = mStoppedAppListForUsers.valueAt(index);
            mStoppedAppList.clear();
        }
    }

    private ArrayMap<String, PackageInfo> getInstalledAppList(int userId) {
        ArrayMap<String, PackageInfo> mInstalledAppList = mInstalledAppListForUsers.get(userId);
        if (mInstalledAppList == null) {
            mInstalledAppList = new ArrayMap<>();
            mInstalledAppListForUsers.put(userId, mInstalledAppList);
        }
        return mInstalledAppList;
    }

    private ArrayMap<String, Integer> getForceStopAppList(int userId) {
        ArrayMap<String, Integer> mForceStopAppList = mForceStopAppListForUsers.get(userId);
        if (mForceStopAppList == null) {
            mForceStopAppList = new ArrayMap<>();
            mForceStopAppListForUsers.put(userId, mForceStopAppList);
        }
        return mForceStopAppList;
    }

    private ArrayMap<String, Integer> getStoppedAppList(int userId) {
        ArrayMap<String, Integer> mStoppedAppList = mStoppedAppListForUsers.get(userId);
        if (mStoppedAppList == null) {
            mStoppedAppList = new ArrayMap<>();
            mStoppedAppListForUsers.put(userId, mStoppedAppList);
        }
        return mStoppedAppList;
    }
}
