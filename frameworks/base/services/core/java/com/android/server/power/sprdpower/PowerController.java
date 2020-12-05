/*
 ** Copyright 2016 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.AppGlobals;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.sprdpower.IPowerGuru;
import android.app.IActivityManager;
import android.app.IUidObserver;
import android.app.INotificationManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.usage.IUsageStatsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageEvents.Event;
import android.app.usage.UsageStatsManagerInternal;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.sprdpower.AppPowerSaveConfig;
import android.os.BatteryManager;
import android.os.BatteryManagerInternal;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IDeviceIdleController;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.sprdpower.PowerControllerInternal;
import android.os.sprdpower.PowerManagerEx;
import android.os.PowerHintVendorSprd;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.Ranking;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.Xml;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodInfo;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.Toast;

import com.android.internal.view.IInputMethodManager;
import com.android.internal.util.XmlUtils;
import com.android.internal.R;
import com.android.server.DeviceIdleController;
import com.android.server.LocalServices;
import com.android.server.net.NetworkPolicyManagerInternal;
import com.android.server.policy.PhoneWindowManager;
import com.android.server.wm.WindowManagerInternal;
import com.android.server.wm.WindowManagerInternal.AppTransitionListener;
import com.android.server.policy.WindowManagerPolicy.WindowManagerFuncs;
import com.android.server.wm.WindowManagerService;

import java.lang.reflect.Array;
import java.lang.InterruptedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;
import com.android.internal.util.FastXmlSerializer;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.android.ims.internal.ImsManagerEx;
import com.android.ims.internal.IImsServiceEx;
import android.telecom.VideoProfile;

import android.os.sprdpower.Util;

public class PowerController //extends IPowerController.Stub
        extends UsageStatsManagerInternal.AppStateEventChangeListener{

    static final String TAG = "PowerController";

    static final String POWER_CONTROLLER_ENABLE = "persist.sys.pwctl.enable";
    static final String POWER_CONTROLLER_APPSTANDBY_ENABLE = "persist.sys.pwctl.appidle";
    static final String POWER_CONTROLLER_POWERGURU_ENABLE = "persist.sys.pwctl.guru";
    static final String POWER_CONTROLLER_BGCLEAN_ENABLE = "persist.sys.pwctl.bgclean";
    static final String POWER_CONTROLLER_GPS_ENABLE = "persist.sys.pwctl.gps";
    static final String POWER_CONTROLLER_WAKELOCK_ENABLE = "persist.sys.pwctl.wl";

    static final String POWER_CONTROLLER_APPSTANDBY_TIMEOUT = "persist.sys.pwctl.appidle.to";
    static final String POWER_CONTROLLER_APPSTANDBY_PAROLE_TIMEOUT = "persist.sys.pwctl.parole.to";
    static final String POWER_CONTROLLER_APPSTANDBY_FORCE = "persist.sys.pwctl.appidle.force"; // ignore procState for appidle in network
    static final String POWER_CONTROLLER_BGCLEAN_TIMEOUT = "persist.sys.pwctl.bgclean.to";

    static final String POWER_CONTROLLER_ULTRASAVING_ENABLE = "ro.sys.pwctl.ultrasaving";

    static final String PWCTL_ENABLE_ONLY_SAVEMODE = "persist.sys.pwctl.onlysave";
    static final String PWCTL_ENABLE_GPS_ONLY_SAVEMODE = "persist.sys.pwctl.gps.onlysave";


    static final String PWCTL_ENABLE_APPSTATS = "persist.sys.pwctl.appstats";

    private final boolean mAppStatsCollectEnabled = (1 == SystemProperties.getInt(PWCTL_ENABLE_APPSTATS, 0));

    private final boolean mAppIdleForceEnabled = (1 == SystemProperties.getInt(POWER_CONTROLLER_APPSTANDBY_FORCE, 1));


    static final String NOTIFICATION_CHANNEL = "POWER";


    private static final String ACTION_CHECK_APPSTATE =
            "com.android.server.powercontroller.CHECK_APPSTATE";

    /* Intent for the schedule mode alarm */
    private static final String INTENT_SCHEDULEMODE_ALARM =
            "com.android.server.power.powercontroller.schedulemode_alarm";

    private static final int FOREGROUND_THRESHOLD_STATE =
             ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;

    private PendingIntent mScheduleModeAlarmIntent = null;

    private final boolean DEBUG = isDebug() || getDeBugPowerControllerLog();
    private final boolean DEBUG_LOG = getDeBugPowerControllerLog();
    private final boolean DEBUG_MORE = false;
    static final boolean TEST = false;

    private final Object mLock = new Object();

    private final boolean mPowerControllerEnabled = (1 == SystemProperties.getInt(POWER_CONTROLLER_ENABLE, 1));
    private final boolean mPowerControllerGuruEnabled = (1 == SystemProperties.getInt(POWER_CONTROLLER_POWERGURU_ENABLE, 1));
    private final boolean mUltraSavingEnabled = (1 == SystemProperties.getInt(POWER_CONTROLLER_ULTRASAVING_ENABLE, 1));

    // check background
    private final boolean mBackgroundCheckEnabled = SystemProperties.getBoolean("persist.sys.pwctl.bgcheck", false);
    // check the app backgound behavior ( playing / downloading / location) all the time
    private final boolean mAllTimeCheckEnabled = SystemProperties.getBoolean("persist.sys.pwctl.alltimecheck", true);


    // if disconnect the network during time span (00:00 ~ 06:00), default is false
    private static final boolean mEnableNetworkDisconnectInSmartMode = false;
    // if disable powerguru, when doze is enter.
    // Default set to be true, then Power Guru and Doze will work at the same time.
    private static final boolean mEnablePowerGuruDuringDozeOn = true;


    // if disable network restrict for message app
    // if set true, then when app is detected as a message app it will always has network access even in doze
    private final boolean mDisableNetworkRestrictForMessageApp = SystemProperties.getBoolean("persist.sys.pwctl.disbnetrestrict", false);;

    //
    private AppStateInfoCollector mAppStateInfoCollector;

    //App category list
    private ArrayMap<String, Integer> mAppCategoryList = new ArrayMap<>();


    // Foreground at UID granularity.
    private final SparseIntArray mUidState = new SparseIntArray();
    private final Object mUidStateLock = new Object();

    // Applist that have been white-listed in power save mode,
    // except device idle (doze) still applies.
    private ArrayList<String> mPowerSaveWhitelistExceptIdleAppList = new ArrayList<>();
    private final Object mWhiteListLock = new Object();

     // Set of app ids that we will always respect the power save for.
    int[] mAppIdPowerSaveWhitelistExceptIdleAppList = new int[0];


    private PendingIntent mAlarmIntent;
    private AlarmManager mAlarmManager;

    private final Context mContext;
    private final IActivityManager mActivityManager;
    private ActivityManagerInternal mActivityManagerInternal;
    private Handler msgHandler;

    private boolean mCharging = true;
    private boolean mScreenOn = true;
    private boolean mMobileConnected = false;
    private boolean mWifiConnected = false;

    private boolean mPowerGuruAligned = false;
    private boolean mPowerGuruAligningStart = false; // PowerGuru Align is starting
    private int mNextAlignedTime = 0;
    private int mOrignalAlignedTime = 0;
    private long mNextAligningTimeStamp = 0;


    // to recode the system elapsed time when starting standby
    private long mStandbyStartTime = 0;

    //Reference to services
    private  UsageStatsManagerInternal mUsageStatsInternal;
    private  IUsageStatsManager mUsageStatsBinder;
    private  IDeviceIdleController mDeviceIdleController;
    private DeviceIdleController.LocalService mLocalDeviceIdleController;
    private BatteryManagerInternal mBatteryManagerInternal;

    private  IPowerGuru mPowerGuruService;
    private  PowerManagerInternal mLocalPowerManager;
    private PowerManager mPowerManager;
    private  AudioManager mAudioManager;
    private TelephonyManager mTelephonyManager;
    private WifiManager mWifiManager;
    private BluetoothAdapter mBluetoothAdapter;
    private UserManager mUserManager;

    private int mCurrentUserId = 0;

    private LocalService mLocalService;
    // helpers
    private List<PowerSaveHelper> mHelpers;
    private PowerGuruHelper mPowerGuruHelper;
    private AppIdleHelper mAppIdleHelper;
    private WakelockConstraintHelper mWakelockConstraintHelper;
    private BackgroundCleanHelper mBackgroundCleanHelper;
    private GpsConstraintHelper mGpsConstraintHelper;

    private static final int DEFAULT_USER_MODE = PowerManagerEx.MODE_SMART;
    private static final int DEFAULT_AUTOLOWPOWER_MODE = PowerManagerEx.MODE_LOWPOWER;
    private static final int DEFAULT_AUTOLOWPOWER_BATTVALUE = 30;
    private static final boolean DEFAULT_AUTOLOWPOWER_EXITWITHPOWER = false; // change to false for bug#870094
    private static final int DEFAULT_SCHEDULE_MODE = PowerManagerEx.MODE_LOWPOWER;
    private static final int DEFAULT_SCHEDULE_START_HOUR = 23;
    private static final int DEFAULT_SCHEDULE_START_MINUTE = 0;
    private static final int DEFAULT_SCHEDULE_END_HOUR = 7;
    private static final int DEFAULT_SCHEDULE_END_MINUTE = 0;

    // value in 0-100
    private int mLowBatteryWarningLevel = DEFAULT_AUTOLOWPOWER_BATTVALUE;
    private static final int LOWBATTERY_CLOSEWARNING_BUMP = 5;
    private boolean mBatteryLevelLow;

    private boolean mExitLowPower_WithPower = DEFAULT_AUTOLOWPOWER_EXITWITHPOWER;

    private boolean mSetSmartSaveMode = false;
    private static boolean mPowerSaveMode_Switch = false;

    // pending timestamp of last switching to/out ultra-saving mode
    private long mLastTimeOfUltraSavingSwitch;
    private static final long DELAY_FOR_CONTINUE_ULTRASAVING_SWITCH = (1*1000);

    // current mode
    // settings use the premode in first broadcast to detect the boot in low power-mode see bug#776332
    private int mPowerSaveMode = PowerManagerEx.MODE_INVALID;
    private int mPrePowerSaveMode = PowerManagerEx.MODE_SMART;

    private boolean mInitFinished = false;
    private boolean mBootCompleted = false;

    // sorted by priority, from high to low
    final private int MODECONFIG_AUTOLOWPOWER = 0;
    final private int MODECONFIG_SCHEDULE = 1;
    final private int MODECONFIG_USER = 2;

    //pre power save mode index value
    private int mPrePowerSaveModeIndex = MODECONFIG_USER;
    private boolean mPrePowerSaveModeStatus = false;

    //For Bug#859178 mobile connect is not disable when enter ultra saving from status bar
    private boolean mIsMobileEnabled = false;
    private boolean mIsWifiEnabled = false;
    private boolean mIsBTEnabled = false;
    private int mScreenOffTime = 15 * 1000;
    private int mLocationState = 0;
    private boolean mWaittingRestoreStates = false;
//    private int mAirplaneMode;

   private static final int DEFAULT_SCREEN_OFF_TIMEOUT = 30 * 1000;

    private IImsServiceEx mImsServiceEx;
    private boolean mNeedCheckPowerModeForCall = false;
    private boolean mHasShowToastForSwichModeWhenInCall = false;

    private static String ModeConfig2Str(int value){
        final String typeStr[] = {"MODECONFIG_AUTOLOWPOWER",
            "MODECONFIG_SCHEDULE",
            "MODECONFIG_USER"};

        if ((value >= 0) && (value < typeStr.length))
            return typeStr[value];
        else
            return "Unknown value: " + value;
    }
    final class ModeConfigContext {
        int mMode;
        boolean mEnable = false;
        boolean mDependencyMet = false;
        ModeConfigContext(int mode, boolean enable, boolean met) {
            mMode = mode;
            mEnable = enable;
            mDependencyMet = met;
        }
    }

    // sorted by priority, from high to low
    private ModeConfigContext[] mModeConfigArray = {
        new ModeConfigContext(DEFAULT_AUTOLOWPOWER_MODE, false, false), // for auto lowpower
        new ModeConfigContext(DEFAULT_SCHEDULE_MODE, false, false), // for schedule
        new ModeConfigContext(DEFAULT_USER_MODE, true, true)}; // for user

    private int mModeConfigIndex = MODECONFIG_USER;
    private static AlertDialog sConfirmDialog;
    //whether show alert to user when enter ultrasaving mode
    private boolean mDontRemind = false;

    private boolean mForcePowerSave = false;

    private int mStartHour = DEFAULT_SCHEDULE_START_HOUR;
    private int mStartMinute = DEFAULT_SCHEDULE_START_MINUTE;
    private int mEndHour = DEFAULT_SCHEDULE_END_HOUR;
    private int mEndMinute = DEFAULT_SCHEDULE_END_MINUTE;

    /** The object used to wait/notify */
    private Object mMsgLock = new Object();

    private Map<String, AppPowerSaveConfig> mAppConfigMap = new HashMap<String, AppPowerSaveConfig>();

    private List<String> mAppList_UltraSave = new ArrayList<String>();
    private List<String> mAppList_UltraSave_Internal = new ArrayList<>();

    // threshold time for app is see as playing music all the time
    // if app is still playing music when standby duration > APP_PLAYING_MUSIC_THRESHOLD
    // then we can identify this app is playing music for a loop
    private long APP_PLAYING_MUSIC_THRESHOLD =  (10 * 60 * 1000L); // 10min


    // threshold time for disconnect network connection
    private long DEFAULT_DISCONNECT_NETWORK_THRESHOLD =  (TEST ? 5 * 60 * 1000L : 30 * 60 * 1000L);; // 30min
    // threshold time for deciding system is not doing download
    private long DEFAULT_NOTDOWNLOADING_THRESHOLD =  (10 * 60 * 1000L); // 10min


    // time interval to increase PowerGuru Align interval
    private long DEFAULT_ALIGN_INTERVAL = (TEST ? 5 * 60 * 1000L : 60 * 60 * 1000L);

    private int STEP_POWERGURU_FACTOR = 5; // in mins
    private int MAX_POWERGURU_INTERVAL = 30 ; // in mins
    private int MIN_POWERGURU_INTERVAL = 5; /* 5 minutes */

    //Check period
    private long CHECK_INTERVAL = (TEST ? 5 * 60 * 1000L : 5 * 60 * 1000L);
    // check period after screen_off
    private long SCREENOFF_CHECK_INTERVAL = (5 * 1000L);


    // Min Data Rate 1k/s
    static final long MIN_DATA_RATE = (1*1000);
    static final long DOWNLOAD_CHECKING_MIN_TIME_SPAN = (30L); //30 seconds

    // Default threshold time to disable wakelock for constraint app
    private long DEFAULT_WAKELOCK_DISABLE_THRESHOLD =  (30 * 1000L); // 30s

    //store white/black list of each config type
    private List<String>[] mWhiteListArray = (List<String>[])Array.newInstance(List.class, AppPowerSaveConfig.ConfigType.TYPE_MAX.value);//new Object[6][2];
    private List<String>[] mBlackListArray = (List<String>[])Array.newInstance(List.class, AppPowerSaveConfig.ConfigType.TYPE_MAX.value);//new Object[6][2];


    // system download state
    private boolean mDownloading = false;
    private int mAppDownloadCount = 0;
    private long mLastNotDownloadTimeStamp = 0;
    private int mDownloadChangeCount = 0;

    private boolean mNeedReConnectMobile = false;
    private boolean mNeedReConnectWifi = false;

    // network constraint time span 00:00 ~ 06:00
    private static int NETWORK_CONSTRAINT_START_TIME_HOUR = 0;
    private static int NETWORK_CONSTRAINT_END_TIME_HOUR = 6;

    // if Doze is enabled
    private boolean mDozeEnabled = false;
    private boolean mForceIdle = false; // system is force in idle


    // Default threshold time to enter doze state in low-power/ultra power saving mode
    private long DEFAULT_FORCE_DOZE_THRESHOLD =  (30 * 1000L); // 30s

    // the time stamp of last system is up using elapsed realtime
    private long mLastSystemUpTimeStamp = 0;
    // next time stamp to check system up using elapsed realtime
    private long mNextCheckSystemUpTimeStamp = 0;

    // the time stamp of last check using up time
    private long mLastCheckTimeStampUptime = 0;


    private static long SYSTEM_UP_CHECK_INTERVAL =  (30 * 1000L); // 30s

    // system touch event listener
    private SystemTouchEventListener mSystemTouchEventListener;
    private WindowManagerFuncs mWindowManagerFuncs;
    private long mLastTouchEventTimeStamp = 0;

    private WindowManagerInternal mWindowManagerInternal;

    private SettingsObserver mSettingsObserver = null;

    private PowerConfig mPowerConfig;

    private RecogAlgorithm mRecogAlgorithm;

    // Note: Bug 698133 appIdle cts fail -->BEG
    // if NetworkPolicyManager ignore procState for AppIdle
    private boolean mIgnoreProcStateForAppIdle = false;
    // Note: Bug 698133 appIdle cts fail <--END

    // launcher app list that is disabled by powercontroller during entering ultra saving mode
    // seperate by ';'
    private String mDisabledLanucherAppList_UltraSave;

    private boolean mModeSwitch;

    private SmartSenseService mSmartSenseService;

    private AppStatsServiceLoader mAppStatsServiceLoader;

    public PowerController(Context context, IActivityManager activityManager) {
        mContext = context;
        mActivityManager = activityManager;

        mAppStateInfoCollector = AppStateInfoCollector.getInstance(mContext);

        // add local service
        mLocalService = new LocalService();
        LocalServices.addService(LocalService.class, mLocalService);

        mSmartSenseService = new SmartSenseService(context, activityManager);

        if (mAppStatsCollectEnabled)
            mAppStatsServiceLoader = new AppStatsServiceLoader(context);
    }

    // called when system is ready ( that is all the service is started)
    public void systemReady() {
        mUsageStatsInternal = LocalServices.getService(UsageStatsManagerInternal.class);
        mUsageStatsBinder = IUsageStatsManager.Stub.asInterface(ServiceManager.getService(Context.USAGE_STATS_SERVICE));
        mDeviceIdleController = IDeviceIdleController.Stub.asInterface(
                ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER));
        mPowerGuruService = IPowerGuru.Stub.asInterface(ServiceManager.getService(Context.POWERGURU_SERVICE));
        mAlarmManager = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        mAudioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        mLocalPowerManager = LocalServices.getService(PowerManagerInternal.class);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mLocalDeviceIdleController = LocalServices.getService(DeviceIdleController.LocalService.class);
        if (mLocalDeviceIdleController != null)
            mDozeEnabled = mLocalDeviceIdleController.isDozeEnabled(); // carefull: the init sequence of DeviceIdleController.mDeepEnabled

        mBatteryManagerInternal = LocalServices.getService(BatteryManagerInternal.class);
        mActivityManagerInternal = LocalServices.getService(ActivityManagerInternal.class);
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);

        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);

        // if not enable don't create & start handler thread
        if (!mPowerControllerEnabled) return;

        Intent intent = new Intent(ACTION_CHECK_APPSTATE)
                .setPackage("android")
                .setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mAlarmIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        msgHandler = new MyHandler(handlerThread.getLooper());

        // to init Data first
        msgHandler.sendMessage(msgHandler.obtainMessage(MSG_INIT));

        // register
        mUsageStatsInternal.addAppStateEventChangeListener(this);

        // ignore procState for network access checking
        if (mAppIdleForceEnabled) {
            try {
                //LocalServices.getService(NetworkPolicyManagerInternal.class).setIgnoreProcStateForAppIdle(true);
                PowerControllerHelper.getInstance(mContext).setIgnoreProcStateForAppIdle(true);

                Slog.d(TAG, "ignoreProcStateForAppIdle: "
                    + PowerControllerHelper.getInstance(mContext).ignoreProcStateForAppIdle());

                // Note: Bug 698133 appIdle cts fail -->BEG
                mIgnoreProcStateForAppIdle = true;
                // Note: Bug 698133 appIdle cts fail <--END
            } catch (Exception e) {
                Slog.d(TAG, "E:ignoreProcStateForAppIdle: "  + e);
            }
        }

        registerForBroadcasts();
        registerUidObserver();
        registerTouchEventListener();
        registerForSettings();
        registerAppTransitionListener();

        if (mLocalPowerManager != null) {
            mLocalPowerManager.registerWakefulnessCallback(new WakefulnessObserver());
        }

        //create Notification Channel
        createNotificationChannel();
        if (mNotificationListener != null) {
            try {
                mNotificationListener.registerAsSystemService(mContext, new ComponentName(mContext.getPackageName(),
                        this.getClass().getCanonicalName()), UserHandle.USER_ALL);
            } catch (Exception e) {
                Slog.w(TAG, "mNotificationListener.registerAsSystemService exception:" + e);
            }
        }

        // do initial check
        // start MSG_CHECK
        msgHandler.sendMessage(msgHandler.obtainMessage(MSG_CHECK));

        // call sense service
        mSmartSenseService.systemReady();
        registerAppUsageStateListener();
    }

    public void onAppStateEventChanged(String packageName, int userId, int state) {
        if (state == UsageEvents.Event.STANDBY_BUCKET_CHANGED) {
            if (DEBUG) Slog.d(TAG, " ignore event: STANDBY_BUCKET_CHANGED for " + packageName);
            return;
        }
        msgHandler.sendMessage(msgHandler.obtainMessage(MSG_APP_STATE_CHANGED, userId, state, packageName));
    }

    // call before SystemReadby
    public void setWindowManager(WindowManagerService wm) {
        mWindowManagerFuncs = wm;

        mSmartSenseService.setWindowManager(wm);
    }

    //Message define
    static final int MSG_APP_STATE_CHANGED = 0;
    static final int MSG_CHECK = 1;
    static final int MSG_NOTIFY_CHANGED = 2;
    static final int MSG_DEVICE_STATE_CHANGED = 3;
    static final int MSG_UPDATE_POWERGURU_INTERVAL = 4;
    static final int MSG_UID_STATE_CHANGED = 5;
    static final int MSG_DOWNLOAD_STATE_CHANGED = 6;
    static final int MSG_INIT = 7;
    static final int MSG_APP_IDLE_STATE_CHANGED = 8;
    static final int MSG_BOOT_COMPLETED = 9;
    static final int MSG_WHITELIST_CHANGED = 10;
    static final int MSG_WAKELOCK_STATE_CHANGED = 11;
    static final int MSG_SET_POWERSAVE_MODE = 12;
    static final int MSG_SET_APP_POWERSAVE_CONFIG = 13;
    static final int MSG_SET_APP_POWERSAVE_CONFIG_WITHTYPE = 14;
    static final int MSG_SET_AUTOLOWPOWER_MODE = 15;
    static final int MSG_SET_AUTOLOWPOWER_ENABLE = 16;
    static final int MSG_SET_AUTOLOWPOWER_BATTVALUE = 17;
    static final int MSG_SET_AUTOLOWPOWER_EXITWITHPOWER = 18;
    static final int MSG_SET_SCHEDULE_MODE = 19;
    static final int MSG_SET_SCHEDULE_ENABLE = 20;
    static final int MSG_SET_SCHEDULE_STARTTIME = 21;
    static final int MSG_SET_SCHEDULE_ENDTIME = 22;
    static final int MSG_SCHEDULEMODE_ALARM = 23;
    static final int MSG_FORCE_POWERSAVE = 24;
    static final int MSG_SET_APP_POWERSAVE_CONFIG_LIST_WITHTYPE = 25;
    static final int MSG_PACKAGE_REMOVED = 26;
    static final int MSG_ADD_APP_IN_ULTRAMODE = 27;
    static final int MSG_DEL_APP_IN_ULTRAMODE = 28;
    static final int MSG_UPDATE_APP_POWER_CONSUMER_TYPE = 29;
    static final int MSG_CHECK_SYSTEM_UP = 30;
    static final int MSG_NOTIFICATION_UPDATE = 31;
    static final int MSG_APP_TRANSITION = 32;
    static final int MSG_WAKEFULNESS_CHANGE_START = 33;
    static final int MSG_WAKEFULNESS_CHANGE_FINISH = 34;
    static final int MSG_APP_USAGE_STATE_CHANGED = 35;
    static final int MSG_SET_SMARTSAVING_CHARGING = 36;
    static final int MSG_SYSTEM_TIME_CHANGED = 37;
    static final int MSG_CHECK_POWER_SAVE_MODE = 38;

    // struct for obj of MSG_SET_APP_POWERSAVE_CONFIG
    final class MsgPowerSaveConfig {
        String appName;
        AppPowerSaveConfig powerConfig;

        MsgPowerSaveConfig(String name, AppPowerSaveConfig config) {
            appName = name;
            powerConfig = config;
        }
    }

    final class MyHandler extends Handler {
        MyHandler(Looper looper) {
            super(looper);
        }

        String Msg2Str(int msg) {
            final String msgStr[] = {"MSG_APP_STATE_CHANGED",
            "MSG_CHECK",
            "MSG_NOTIFY_CHANGED",
            "MSG_DEVICE_STATE_CHANGED",
            "MSG_UPDATE_POWERGURU_INTERVAL",
            "MSG_UID_STATE_CHANGED",
            "MSG_DOWNLOAD_STATE_CHANGED",
            "MSG_INIT",
            "MSG_APP_IDLE_STATE_CHANGED",
            "MSG_BOOT_COMPLETED",
            "MSG_WHITELIST_CHANGED",
            "MSG_WAKELOCK_STATE_CHANGED",
            "MSG_SET_POWERSAVE_MODE",
            "MSG_SET_APP_POWERSAVE_CONFIG",
            "MSG_SET_APP_POWERSAVE_CONFIG_WITHTYPE",
            "MSG_SET_AUTOLOWPOWER_MODE",
            "MSG_SET_AUTOLOWPOWER_ENABLE",
            "MSG_SET_AUTOLOWPOWER_BATTVALUE",
            "MSG_SET_AUTOLOWPOWER_EXITWITHPOWER",
            "MSG_SET_SCHEDULE_MODE",
            "MSG_SET_SCHEDULE_ENABLE",
            "MSG_SET_SCHEDULE_STARTTIME",
            "MSG_SET_SCHEDULE_ENDTIME",
            "MSG_SCHEDULEMODE_ALARM",
            "MSG_FORCE_POWERSAVE",
            "MSG_SET_APP_POWERSAVE_CONFIG_LIST_WITHTYPE",
            "MSG_PACKAGE_REMOVED",
            "MSG_ADD_APP_IN_ULTRAMODE",
            "MSG_DEL_APP_IN_ULTRAMODE",
            "MSG_UPDATE_APP_POWER_CONSUMER_TYPE",
            "MSG_CHECK_SYSTEM_UP",
            "MSG_NOTIFICATION_UPDATE",
            "MSG_APP_TRANSITION",
            "MSG_WAKEFULNESS_CHANGE_START",
            "MSG_WAKEFULNESS_CHANGE_FINISH",
            "MSG_APP_USAGE_STATE_CHANGED",
            "MSG_SET_SMARTSAVING_CHARGING" ,
            "MSG_SYSTEM_TIME_CHANGED",
            "MSG_CHECK_POWER_SAVE_MODE"};

            if ((0 <= msg) && (msg < msgStr.length))
                return msgStr[msg];
            else
                return "Unknown message: " + msg;
        }

        @Override public void handleMessage(Message msg) {
        if (DEBUG) Slog.d(TAG, "handleMessage(" + Msg2Str(msg.what) + ")");

        switch (msg.what) {
            case MSG_APP_STATE_CHANGED:
                handleAppStateChanged((String)msg.obj, msg.arg1, msg.arg2);
                break;
            case MSG_CHECK:
                checkAllAppStateInfo();
                break;
            case MSG_NOTIFY_CHANGED:
                notifyChanged();
                break;
            case MSG_DEVICE_STATE_CHANGED:
                handleDeviceStateChanged((Intent)msg.obj);
                break;
            case MSG_UPDATE_POWERGURU_INTERVAL:
                updatePowerGuruInterval();
                break;
            case MSG_UID_STATE_CHANGED:
                handleProcStateChanged((String)msg.obj, msg.arg1, msg.arg2);
                break;
            case MSG_INIT:
                initData();
                break;
            case MSG_DOWNLOAD_STATE_CHANGED:
                handleDownloadStateChanged((AppState)msg.obj, msg.arg1);
                break;
            case MSG_APP_IDLE_STATE_CHANGED:
                // handle in AppIdleHelper
                break;
            case MSG_BOOT_COMPLETED:
                initDataForBootCompleted();
                break;
            case MSG_WHITELIST_CHANGED:
                updateWhiteAppList();
                break;
            case MSG_WAKELOCK_STATE_CHANGED:
                // handle in WakelockConstraintHelper
                break;
            case MSG_SET_POWERSAVE_MODE:
                setPowerSaveModeInternal(msg.arg1);
                break;
            case MSG_SET_APP_POWERSAVE_CONFIG:
                MsgPowerSaveConfig config = (MsgPowerSaveConfig)msg.obj;
                setAppPowerSaveConfigInternal(config.appName, config.powerConfig);
                break;
            case MSG_SET_APP_POWERSAVE_CONFIG_WITHTYPE: {
                    String appName = (String)msg.obj;
                    setAppPowerSaveConfigWithTypeInternal(appName, msg.arg1, msg.arg2);
                }
                break;
            case MSG_SET_APP_POWERSAVE_CONFIG_LIST_WITHTYPE:
                List<String> appList = (List<String>)msg.obj;
                setAppPowerSaveConfigListWithTypeInternal(appList, msg.arg1, msg.arg2);
                break;
            case MSG_SET_AUTOLOWPOWER_MODE:
                // if current mode is AUTOLOWPOWER, while save mode changes, don't change the mPrePowerSaveMode
                // for bug#913238 --> start
                if (mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mEnable
                        && mModeConfigIndex == MODECONFIG_AUTOLOWPOWER) {
                    mModeSwitch = true;
                }
                // for bug#913238 <-- end
                mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mMode = msg.arg1;
                writeConfig();
                updatePowerSaveMode();
                break;
            case MSG_SET_AUTOLOWPOWER_ENABLE:
                mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mEnable = (boolean)msg.obj;
                if (mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mEnable) {
                    if (DEBUG) Slog.d(TAG, "enable auto lowpower mode!!");
                    updateBatteryLevelLow(true);
                } else {
                    if (DEBUG) Slog.d(TAG, "disable auto lowpower mode!!");
                    mBatteryLevelLow = false;
                }
                // if disable, should reset relevant configs
                /*if (!mEnable_AutoLowPowerMode) {
                    mPowerSaveMode_LowPower = DEFAULT_AUTOLOWPOWER_MODE;
                    mLowBatteryWarningLevel = DEFAULT_AUTOLOWPOWER_BATTVALUE;
                    mExitLowPower_WithPower = DEFAULT_AUTOLOWPOWER_EXITWITHPOWER;
                }*/
                writeConfig();
                updatePowerSaveMode();
                break;
            case MSG_SET_AUTOLOWPOWER_BATTVALUE:
                mLowBatteryWarningLevel = msg.arg1;
                if (DEBUG) Slog.d(TAG, "set battery level: " +mLowBatteryWarningLevel + " for auto lowpower mode!!");
                writeConfig();
                Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, mLowBatteryWarningLevel);
                updateBatteryLevelLow(true);
                updatePowerSaveMode();
                break;
            case MSG_SET_AUTOLOWPOWER_EXITWITHPOWER:
                mExitLowPower_WithPower = (boolean)msg.obj;
                writeConfig();
                updatePowerSaveMode();
                break;
            case MSG_SET_SCHEDULE_MODE:
                // if current mode is SCHEDULE, while save mode changes, don't change the mPrePowerSaveMode
                 // for bug#913238 --> start
                if (mModeConfigArray[MODECONFIG_SCHEDULE].mEnable
                        && mModeConfigIndex == MODECONFIG_SCHEDULE) {
                    mModeSwitch = true;
                }
                // for bug#913238 <-- end
                mModeConfigArray[MODECONFIG_SCHEDULE].mMode = msg.arg1;
                writeConfig();
                updateScheduleAlarm();
                break;
            case MSG_SET_SCHEDULE_ENABLE:
                mModeConfigArray[MODECONFIG_SCHEDULE].mEnable = (boolean)msg.obj;
                // if disable, should reset relevant configs
                /*if (!mEnable_ScheduleMode) {
                    mPowerSaveMode_Schedule = DEFAULT_SCHEDULE_MODE;
                    mStartHour = DEFAULT_SCHEDULE_START_HOUR;
                    mStartMinute = DEFAULT_SCHEDULE_START_MINUTE;
                    mEndHour = DEFAULT_SCHEDULE_END_HOUR;
                    mEndMinute = DEFAULT_SCHEDULE_END_MINUTE;
                }*/
                writeConfig();
                updateScheduleAlarm();
                break;
            case MSG_SET_SCHEDULE_STARTTIME: {
                    int hour = msg.arg1;
                    int minute = msg.arg2;
                    mStartHour = hour;
                    mStartMinute = minute;
                    writeConfig();
                    updateScheduleAlarm();
                    break;
                }
            case MSG_SET_SCHEDULE_ENDTIME: {
                    int hour = msg.arg1;
                    int minute = msg.arg2;
                    mEndHour = hour;
                    mEndMinute = minute;
                    writeConfig();
                    updateScheduleAlarm();
                    break;
                }
            case MSG_SCHEDULEMODE_ALARM:
                updateScheduleAlarm();
                break;
            case MSG_FORCE_POWERSAVE:
                mForcePowerSave = true;
                updatePowerSaveMode();
                break;
            case MSG_PACKAGE_REMOVED: {
                    String pkgName = (String)msg.obj;
                    if (null != mAppConfigMap.remove(pkgName)) {
                        if (DEBUG) Slog.d(TAG, "remove " + pkgName + "'s appconfig");
                        AppPowerSaveConfig.writeConfig(mAppConfigMap);
                    }
                }
                break;
            case MSG_ADD_APP_IN_ULTRAMODE: {
                    String appName = (String)msg.obj;
                    if (!mAppList_UltraSave.contains(appName)) {
                        if (DEBUG) Slog.d(TAG, "add " + appName + " to applist of ultramode");
                        mAppList_UltraSave.add(appName);
                        writeConfig();
                        updateAppList_UltraSave_internal(appName, true);
                    }
                }
                break;
            case MSG_DEL_APP_IN_ULTRAMODE: {
                    String appName = (String)msg.obj;
                    // add for bug#965339 --> start
                    if (mCurrentUserId != 0) {
                        if (appName != null) {
                            String[] sArrays = appName.split("#");
                            if (sArrays != null && sArrays.length <= 2) {
                                if (DEBUG) Slog.d(TAG, "remove the orginal applist:" + appName +
                                    " at non-owner user, ignore");
                                break;
                            }
                        }
                    }
                    // add for bug#965339 <--end
                    if (mAppList_UltraSave.remove(appName)) {
                        if (DEBUG) Slog.d(TAG, "remove " + appName + " from applist of ultramode");
                        writeConfig();
                        updateAppList_UltraSave_internal(appName, false);
                    }
                }
                break;

            case MSG_UPDATE_APP_POWER_CONSUMER_TYPE:
                String appName = (String)msg.obj;
                int type = msg.arg1;
                int mask = msg.arg2;
                updateAppPowerConsumerType(appName, type, mask);
                break;

            case MSG_CHECK_SYSTEM_UP:
                checkSystemUpTime();
                break;

            case MSG_NOTIFICATION_UPDATE:
                appName = (String)msg.obj;
                int uid = msg.arg1;
                handleNotificationUpdate(appName, uid);
                break;

            case MSG_APP_TRANSITION:
                handleAppTransition();
                break;

            case MSG_WAKEFULNESS_CHANGE_START:
                if (mBackgroundCleanHelper != null)
                    mBackgroundCleanHelper.onWakefulnessChangeStarted(msg.arg1, msg.arg2);
               break;

            case MSG_WAKEFULNESS_CHANGE_FINISH:
                boolean screenOn = (msg.arg1 == 1);
                if (mBackgroundCleanHelper != null)
                    mBackgroundCleanHelper.onWakefulnessChangeFinished(screenOn);
               break;

            case MSG_APP_USAGE_STATE_CHANGED:
                handleAppUsageStateChanged((String)msg.obj, msg.arg1, msg.arg2);
                break;
            case MSG_SET_SMARTSAVING_CHARGING:
                Slog.d(TAG, "setSmartSavingModeWhenCharging old status:" +mSetSmartSaveMode);
                mSetSmartSaveMode = (boolean)msg.obj;
                Slog.d(TAG, "setSmartSavingModeWhenCharging new status:" +mSetSmartSaveMode);
                writeConfig();
                updatePowerSaveMode();
                synchronized(mMsgLock) {
                  mMsgLock.notify();
                }
                break;

            case MSG_SYSTEM_TIME_CHANGED:
                Slog.d(TAG, "system time is changed and update power save mode!!");
            case MSG_CHECK_POWER_SAVE_MODE:
                updatePowerSaveMode();
                break;
            }
        }
    }

    private void updateAppList_UltraSave_internal(String appName, boolean bAdd) {
        String[] strings = appName.split("/");
        if (bAdd) {
            mAppList_UltraSave_Internal.add(strings[0]);
            if (DEBUG) Slog.d(TAG, "add " + strings[0] + " to internal applist of ultramode");
        } else {
            mAppList_UltraSave_Internal.remove(strings[0]);
            if (DEBUG) Slog.d(TAG, "remove " + strings[0] + " from internal applist of ultramode");
        }
    }
    private static final long DAY_IN_MILLIS = 24 * 60 * 60 * 1000L;

    private void updateScheduleAlarm() {
        if (mModeConfigArray[MODECONFIG_SCHEDULE].mEnable) {
            long delay = 0L;
            long now = System.currentTimeMillis();
            Calendar startCalendar = Calendar.getInstance();
            Calendar endCalendar = Calendar.getInstance();
            startCalendar.setTimeInMillis(now);
            endCalendar.setTimeInMillis(now);
            startCalendar.set(Calendar.HOUR_OF_DAY, mStartHour);
            startCalendar.set(Calendar.MINUTE, mStartMinute);
            startCalendar.set(Calendar.SECOND, 0);
            startCalendar.set(Calendar.MILLISECOND, 0);
            endCalendar.set(Calendar.HOUR_OF_DAY, mEndHour);
            endCalendar.set(Calendar.MINUTE, mEndMinute);
            endCalendar.set(Calendar.SECOND, 0);
            endCalendar.set(Calendar.MILLISECOND, 0);
            long start = startCalendar.getTimeInMillis();
            long end = endCalendar.getTimeInMillis();
            long startInterval = getInterval(now, start);
            long endInterval = getInterval(now, end);
            delay = (startInterval < endInterval)? startInterval : endInterval;
            if (DEBUG) Slog.d(TAG, "updateScheduleAlarm(), next alarm in schedule: " + (startInterval < endInterval) 
                + ", [" + mStartHour + ":" + mStartMinute + ", " + mEndHour + ":" + mEndMinute + "]");
            startScheduleAlarm(delay);
        } else {
            stopScheduleAlarm();
        }
        updatePowerSaveMode();
    }

    private static long getInterval(long a, long b) {
        if (a <= b)
            return (b - a);
        else
            return (b - a + DAY_IN_MILLIS);
    }

    private void startScheduleAlarm(long delayInMs) {
        if (DEBUG) Slog.d(TAG, "startScheduleAlarm(), delayInMs:" + delayInMs);
        Intent intent = new Intent(INTENT_SCHEDULEMODE_ALARM);
        mScheduleModeAlarmIntent = PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayInMs, mScheduleModeAlarmIntent);
    }

    private void stopScheduleAlarm() {
        if (DEBUG) Slog.d(TAG, "stopScheduleAlarm(), mScheduleModeAlarmIntent: " +mScheduleModeAlarmIntent);
        if (mScheduleModeAlarmIntent != null) {
            mAlarmManager.cancel(mScheduleModeAlarmIntent);
            mScheduleModeAlarmIntent = null;
        }
    }

    private void handleAppStateChanged(String packageName, int userId, int state) {
        int oldState = state;
        AppState appState = mAppStateInfoCollector.getAppState(packageName, userId);

        if (DEBUG) Slog.d(TAG, "- handleAppStateChanged() E -");

        if (mAppStatsCollectEnabled && mAppStatsServiceLoader != null) {
            mAppStatsServiceLoader.reportAppStateChanged(packageName, userId, state);
        }

        if (appState != null) {
            oldState = appState.mState;
            if (oldState == state)
                return;
        }

        if (mAppStateInfoCollector.reportAppStateEventInfo(packageName, userId, state)) {
            // Note: Bug 698133 appIdle cts fail -->BEG
            // Ugly: we have to check if doing cts/gts test
            // is cts/gts test, then
            checkCtsGtsTesting(packageName);
            // Note: Bug 698133 appIdle cts fail <--END
        }

        if (DEBUG) Slog.d(TAG, "packageName:" + packageName + " state:" + Util.AppState2Str(state)+ " user:" + userId);

        // get new app state
        appState = mAppStateInfoCollector.getAppState(packageName, userId);

        if (appState == null) {
            Slog.w(TAG, "null appState for packageName:" + packageName + " state:" + Util.AppState2Str(state)+ " user:" + userId);
            return;
        }
        //SPRD:Bug 814570 About apps auto run BEG
        if (Event.MOVE_TO_FOREGROUND == state
            && appState.mTotalLaunchCount == 1) {
            if (DEBUG) Slog.d(TAG, "- handleAppStateChanged() NEW -");

            if (mBackgroundCleanHelper != null)
                mBackgroundCleanHelper.noteAppFirstStarted(packageName, userId);
        }
        //SPRD:Bug 814570 About apps auto run END

        mRecogAlgorithm.reportEvent(packageName, appState.mUid, RecogAlgorithm.EVENT_TYPE_FG_STATE, state);

        if (mCharging || mScreenOn) {
            //if (!mBackgroundCheckEnabled) return;

            if (Event.MOVE_TO_BACKGROUND == state) {
                // recode the rxBytes if needed
                // should be called before doing Evaluated time stamp update
                appState.updateAppTrafficStats(false);
            } else if (Event.MOVE_TO_FOREGROUND == state
               && appState.mProcState == ActivityManager.PROCESS_STATE_TOP) {
                appState.updateAppTrafficStats(true);
            }
            return;
        }

        // recode the traffic stats if needed
        // should be called before doing Evaluated time stamp update
        appState.updateAppTrafficStats(false);

        // notify helpers to update time stamp
        updateAppEvaluatedTimeStamp(appState);


        int stateChange = getStateChange(oldState, state);

        // if app is already set in standby state, then its app state changed
        // UsageStatsService will make the app to exit standby state
        if (stateChange != STATE_NOCHANGE && appState.mInAppStandby) {
            if (DEBUG) Slog.d(TAG, "packageName:" + packageName + " may exit standby by UsageStatsService");
        }

        if (stateChange == STATE_BG2FG) { //if bg2fg, clear list, remove from powerguru & appstandby
            if (DEBUG) Slog.d(TAG, "packageName:" + packageName + " change from BG2FG");

            // notify helpers to handle the app state changed
            updateAppStateChanged(appState, stateChange);

        } else { // if not bg2fg, just resend MSG_CHECK
        }

        cancelAlarmLocked();
        msgHandler.removeMessages(MSG_CHECK);
        msgHandler.sendMessage(msgHandler.obtainMessage(MSG_CHECK));

    }

    private void handleProcStateChanged(String appName, int uid, int procState) {
        if (DEBUG) Slog.d(TAG, "- handleProcstateChanged() E - packageName:" + appName
            + " uid:" + uid + " procState:" + Util.ProcState2Str(procState));

        if (mAppStatsCollectEnabled && mAppStatsServiceLoader != null) {
            mAppStatsServiceLoader.reportAppProcStateChanged(appName, uid, procState);
        }

        if (mAppStateInfoCollector.reportAppProcStateInfo(appName, uid, procState)) {
            // Note: Bug 698133 appIdle cts fail -->BEG
            // Ugly: we have to check if doing cts/gts test
            // is cts/gts test, then
            checkCtsGtsTesting(appName);
            // Note: Bug 698133 appIdle cts fail <--END
        }

        int userId = UserHandle.getUserId(uid);
        AppState appState = mAppStateInfoCollector.getAppState(appName, userId);

        if (appState == null) {
            Slog.w(TAG, "null appState for packageName:" + appName
                + " uid:" + uid + " procState:" + Util.ProcState2Str(procState));
            return;
        }

        // if app is stopped, notify WakelockConstraintHelper
        if (appState.mProcState == ActivityManager.PROCESS_STATE_CACHED_EMPTY) {
            appState.updateAppState(Event.NONE);
            appState.clearLaunchInfo();
            mWakelockConstraintHelper.noteAppStopped(appState);
        }

        mRecogAlgorithm.reportEvent(appName, uid, RecogAlgorithm.EVENT_TYPE_PROC_STATE, procState);

        if (mCharging || mScreenOn) {
            //if (!mBackgroundCheckEnabled) return;

            if (Event.MOVE_TO_FOREGROUND == appState.mState
               && appState.mProcState == ActivityManager.PROCESS_STATE_TOP) {
                appState.updateAppTrafficStats(true);
            }
            return;
        }
        // recode the rxBytes if needed
        // should be called before doing Evaluated time stamp update
        appState.updateAppTrafficStats(false);

        // notify helpers to update time stamp
        updateAppEvaluatedTimeStamp(appState);

        // Note: Bug#695969 Audio Recoder fail --> BEG
        // if new procState is <= FOREGROUND_THRESHOLD_STATE
        // then update its Notification state
        if (procState <= FOREGROUND_THRESHOLD_STATE) {
            appState.updateActiveNotificationState(mContext);
        }
        // Note: Bug#695969 Audio Recoder fail <-- END

        msgHandler.removeMessages(MSG_CHECK);
        msgHandler.sendMessage(msgHandler.obtainMessage(MSG_CHECK));
        cancelAlarmLocked();
    }

    private void checkAllAppStateInfo() {
        if (DEBUG) Slog.d(TAG, "- checkAllAppStateInfo() E -");
        if (DEBUG) Slog.d(TAG, "mCharging:" + mCharging + " mScreenOn:" + mScreenOn + " mMobileConnected:" + mMobileConnected);

        //set next check
        //msgHandler.removeMessages(MSG_CHECK);
        //msgHandler.sendMessageDelayed(msgHandler.obtainMessage(MSG_CHECK), CHECK_INTERVAL);
        scheduleAlarmLocked(CHECK_INTERVAL);

        if (mCharging || mScreenOn) {
            if (mNeedCheckPowerModeForCall) {
                updatePowerSaveMode();
            }

            checkBackgroundApp();
            return;
        }

        checkSystemUpTime();

        boolean bChanged = false;
        // Note:use the same now elapsed time for all the AppStateInfo
        // otherwise, when checking app Parole, some app may have not
        // opportunity to exit app standby.
        long now = SystemClock.elapsedRealtime();

        updatePowerSaveMode();

        try {
            final List<UserInfo> users = mUserManager.getUsers();
            for (int ui = users.size() - 1; ui >= 0; ui--) {
                UserInfo user = users.get(ui);
                if (DEBUG) Slog.d(TAG, "- checkAllAppStateInfo() for user: " + user.id);

                final ArrayMap<String, AppState> appStateInfoList = mAppStateInfoCollector.getAppStateInfoList(user.id);
                for (int i=0;i<appStateInfoList.size();i++) {
                    AppState appState = appStateInfoList.valueAt(i);

                    //let app to be parole
                    mAppIdleHelper.checkAppParole(appState, now);

                    // check app state info
                    if (checkAppStateInfo(appState, now)) {
                        bChanged = true;
                    }
                }
            }
        } catch (Exception e) {
        }

        // note AppidleHelper all check done
        mAppIdleHelper.noteCheckAllAppStateInfoDone();
        // note mWakelockConstraintHelper all check done
        mWakelockConstraintHelper.noteCheckAllAppStateInfoDone();

        //send notification to powerguru & appstandby
        if (bChanged) msgHandler.sendMessage(msgHandler.obtainMessage(MSG_NOTIFY_CHANGED));

        if (needCheckNetworkConnection(now)) {
            if (DEBUG) Slog.d(TAG, "- checkNetworkConnection() in checkAllAppStateInfo -");
            checkNetworkConnection(true);
        }

        // check doze state
        checkDozeState();
    }

    private void notifyChanged() {
        if (DEBUG) Slog.d(TAG, "- notifyChanged() E -");

        for (int i = 0; i < mHelpers.size(); i++) {
            PowerSaveHelper helper = mHelpers.get(i);
            helper.applyConstrain();
        }
    }

    private void registerForBroadcasts() {
        IntentFilter devicestatFilter = new IntentFilter();
        devicestatFilter.addAction(Intent.ACTION_SCREEN_ON);
        devicestatFilter.addAction(Intent.ACTION_SCREEN_OFF);
        devicestatFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        devicestatFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        devicestatFilter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        devicestatFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    msgHandler.sendMessage(msgHandler.obtainMessage(MSG_DEVICE_STATE_CHANGED, intent));
                }
        }, devicestatFilter);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_CHECK_APPSTATE);
        intentFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        intentFilter.addAction(PowerManager.ACTION_POWER_SAVE_WHITELIST_CHANGED);
        intentFilter.addAction(INTENT_SCHEDULEMODE_ALARM);

        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (ACTION_CHECK_APPSTATE.equals(action)) {
                        msgHandler.removeMessages(MSG_CHECK);
                        msgHandler.sendMessage(msgHandler.obtainMessage(MSG_CHECK));

                        // for PowerGuru Interval upadate
                        if (mPowerGuruAligningStart) {
                            msgHandler.removeMessages(MSG_UPDATE_POWERGURU_INTERVAL);
                            msgHandler.sendMessage(msgHandler.obtainMessage(MSG_UPDATE_POWERGURU_INTERVAL));
                        }
                    } else if(Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                        msgHandler.sendMessage(msgHandler.obtainMessage(MSG_BOOT_COMPLETED));
                    } else if(PowerManager.ACTION_POWER_SAVE_WHITELIST_CHANGED.equals(action)) {
                        msgHandler.sendMessage(msgHandler.obtainMessage(MSG_WHITELIST_CHANGED));
                    } else if(INTENT_SCHEDULEMODE_ALARM.equals(action)) {
                        msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SCHEDULEMODE_ALARM));
                    }
                }
            }, intentFilter);

        intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");

        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                        Uri data = intent.getData();
                        String pkgName = data.getEncodedSchemeSpecificPart();
                        // For bug#720777,don't remove the config
                        //msgHandler.sendMessage(msgHandler.obtainMessage(MSG_PACKAGE_REMOVED, pkgName));
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
                        int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0);
                        Slog.d(TAG, "ACTION_USER_SWITCHED : userId:" + userId);
                        msgHandler.post(new Runnable() {
                            public void run() {
                                try {
                                    onUserSwitched(userId);
                                } catch (Exception e) {}
                            }
                        });
                    }
                }
            }, filter);

        IntentFilter timeFilter = new IntentFilter();
        timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
        timeFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (Intent.ACTION_TIME_CHANGED.equals(action)) {
                        Slog.d(TAG, "system time is changed!!");
                        msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SYSTEM_TIME_CHANGED));
                    }
                }
            }, timeFilter);
    }

    private void handleDeviceStateChanged(Intent intent) {
        String action = intent.getAction();
        boolean oldScreenOn = mScreenOn;
        boolean oldMobileConnected = mMobileConnected;
        boolean oldCharging = mCharging;

        if (DEBUG) Slog.d(TAG, "- handleDeviceStateChanged() E -, action: " + action);

        if (action.equals(Intent.ACTION_SCREEN_ON)) {
            mScreenOn = true;
        } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
            mScreenOn = false;
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            boolean bNoConnection = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
            if (!bNoConnection) {
                int networkType = intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, ConnectivityManager.TYPE_NONE);
                mMobileConnected = ConnectivityManager.isNetworkTypeMobile(networkType);
                mWifiConnected = ConnectivityManager.isNetworkTypeWifi(networkType);
            } else {
                mMobileConnected = false;
                mWifiConnected = false;
            }
        } else if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            int pluggedType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            mCharging = (0 != intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));
            if (DEBUG) Slog.d(TAG, "pluggedType:" + pluggedType + " mCharging:" + mCharging);
            updateBatteryLevelLow(false);

            long delay = delayCheckPowerSaveMode();
            if (delay > 0) {
                if (DEBUG) Slog.d(TAG, "delay " + delay + "ms to check MSG_CHECK_POWER_SAVE_MODE");
                msgHandler.removeMessages(MSG_CHECK_POWER_SAVE_MODE);
                msgHandler.sendMessageDelayed(msgHandler.obtainMessage(MSG_CHECK_POWER_SAVE_MODE), delay);
            } else {
                updatePowerSaveMode();
            }

            //fix Bug966108:The power saving mode confirmation box does not disappear when charging.
            if(sConfirmDialog != null && mCharging && mSetSmartSaveMode){
                sConfirmDialog.dismiss();
            }
        } else if (action.equals(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)) {
            try {
                notifyDozeStateChanged();
            } catch (Exception e) {
                // fall through
            }
            return;
        }

        if (DEBUG_MORE) Slog.d(TAG, "handleDeviceStateChanged: mCharging:" + mCharging
            + " mScreenOn:" + mScreenOn + " mMobileConnected:" + mMobileConnected);

        boolean bScreenChanged = (oldScreenOn != mScreenOn);
        boolean bConnectionChanged = (oldMobileConnected != mMobileConnected);
        boolean bChargingChanged = (oldCharging != mCharging);

        if (bScreenChanged) {
            if (mBackgroundCleanHelper != null)
                mBackgroundCleanHelper.onScreenChanged(mScreenOn);
        }

        if (DEBUG) Slog.d(TAG, "handleDeviceStateChanged: bScreenChanged:" + bScreenChanged
            + " bConnectionChanged:" + bConnectionChanged + " bChargingChanged:" + bChargingChanged);

        if (bScreenChanged || bConnectionChanged || bChargingChanged) {
            checkPowerGuruInterval();
        }
        if (bScreenChanged || bChargingChanged) {
            updateAppStateInfoForDeviceStateChanged();
        }

        if (bScreenChanged)
            mRecogAlgorithm.reportDeviceState(RecogAlgorithm.DEVICE_STATE_TYPE_SCREEN, mScreenOn);

        if (bChargingChanged) {
            mRecogAlgorithm.reportDeviceState(RecogAlgorithm.DEVICE_STATE_TYPE_CHARGING, mCharging);
        }

        // if in standby state, trigger a delay MSG_CHECK operation
        if (!mScreenOn && !mCharging) {
            msgHandler.removeMessages(MSG_CHECK);
            msgHandler.sendMessageDelayed(msgHandler.obtainMessage(MSG_CHECK), SCREENOFF_CHECK_INTERVAL);
        }
    }

    private void checkPowerGuruInterval() {
        if (DEBUG) Slog.d(TAG, "checkPowerGuruInterval: mCharging:" + mCharging
            + " mScreenOn:" + mScreenOn + " mMobileConnected:" + mMobileConnected);

        if (needUpdatePowerGuruInterval()) {
            if (mPowerGuruAligned) {
                return;
            }
            //a new start aligning circle
            try {
                mOrignalAlignedTime = getPowerGuruOrignalInterval();
                if (mOrignalAlignedTime < MIN_POWERGURU_INTERVAL)
                    mOrignalAlignedTime = MIN_POWERGURU_INTERVAL;
            } catch (Exception e) {
                // fall through
            }
            //set next Align Interval
            mNextAlignedTime = mOrignalAlignedTime + STEP_POWERGURU_FACTOR;
            if (mNextAlignedTime > MAX_POWERGURU_INTERVAL) {
                mNextAlignedTime = MAX_POWERGURU_INTERVAL;
            }
            if (DEBUG) Slog.d(TAG, "To set next Align Interval:" + mNextAlignedTime );


            // Aligning start
            mPowerGuruAligningStart = true;
            // for mobile data connection, adjusting time interval is (2*DEFAULT_ALIGN_INTERVAL)
            if (mMobileConnected) {
                mNextAligningTimeStamp = SystemClock.elapsedRealtime() + DEFAULT_ALIGN_INTERVAL*2;
            } else {
                mNextAligningTimeStamp = SystemClock.elapsedRealtime() + DEFAULT_ALIGN_INTERVAL;
            }
            msgHandler.removeMessages(MSG_UPDATE_POWERGURU_INTERVAL);
            msgHandler.sendMessageDelayed(msgHandler.obtainMessage(MSG_UPDATE_POWERGURU_INTERVAL), DEFAULT_ALIGN_INTERVAL);
        } else {
            if (!mPowerGuruAligned) {
                return;
            }
            //Cancel ongoing aligning circle
            mPowerGuruAligned = false;

            // Aligning is done
            mPowerGuruAligningStart = false;
            try {
                // Reset to the orignal Value
                setPowerGuruInterval(mOrignalAlignedTime);
            } catch (Exception e) {
                // fall through
            }
            msgHandler.removeMessages(MSG_UPDATE_POWERGURU_INTERVAL);
        }
    }

    // adjust interval for both WIFI and Mobile Data
    private boolean needUpdatePowerGuruInterval() {
        if (/*mMobileConnected ||*/ mScreenOn || mCharging) {
            return false;
        }

        return true;
    }

    private void updatePowerGuruInterval() {
        if (needUpdatePowerGuruInterval()) {
           if (DEBUG) Slog.d(TAG, " mNextAlignedTime: " + mNextAlignedTime);

            mPowerGuruAligned = true;

            long now = SystemClock.elapsedRealtime();

            if (now < mNextAligningTimeStamp) {
                long leftTime = mNextAligningTimeStamp-now;
                if (DEBUG_LOG) Slog.d(TAG, " updatePowerGuruInterval: now: " + now + " left:" + leftTime);
                msgHandler.removeMessages(MSG_UPDATE_POWERGURU_INTERVAL);
                msgHandler.sendMessageDelayed(msgHandler.obtainMessage(MSG_UPDATE_POWERGURU_INTERVAL), leftTime);
                return;
            }

            try {
                setPowerGuruInterval(mNextAlignedTime);
            } catch (Exception e) {
                // fall through
            }
            if (mNextAlignedTime < MAX_POWERGURU_INTERVAL) {
                mNextAlignedTime += STEP_POWERGURU_FACTOR;
                if (mNextAlignedTime > MAX_POWERGURU_INTERVAL)
                    mNextAlignedTime = MAX_POWERGURU_INTERVAL;

                // for mobile data connection, adjusting time interval is (2*DEFAULT_ALIGN_INTERVAL)
                if (mMobileConnected) {
                    mNextAligningTimeStamp = SystemClock.elapsedRealtime() + DEFAULT_ALIGN_INTERVAL*2;
                } else {
                    mNextAligningTimeStamp = SystemClock.elapsedRealtime() + DEFAULT_ALIGN_INTERVAL;
                }

                // Re-sent the MSG
                msgHandler.removeMessages(MSG_UPDATE_POWERGURU_INTERVAL);
                msgHandler.sendMessageDelayed(msgHandler.obtainMessage(MSG_UPDATE_POWERGURU_INTERVAL), DEFAULT_ALIGN_INTERVAL);
            } else {
                // Clear the MSG
                msgHandler.removeMessages(MSG_UPDATE_POWERGURU_INTERVAL);
                // Aligning is done
                mPowerGuruAligningStart = false;
            }
        } else {
            if (!mPowerGuruAligned)
                return;
            mPowerGuruAligned = false;

            // Aligning is done
            mPowerGuruAligningStart = false;
            msgHandler.removeMessages(MSG_UPDATE_POWERGURU_INTERVAL);
            try {
                // Reset to the orignal Value
                if (mOrignalAlignedTime > 0)
                    setPowerGuruInterval(mOrignalAlignedTime);
            } catch (Exception e) {
                // fall through
            }
        }
    }

    private void updateAppStateInfoForDeviceStateChanged() {
        if (DEBUG) Slog.d(TAG, "updateAppStateInfoForDeviceStateChanged() E, mScreenOn: " + mScreenOn + ", mCharging: " + mCharging);

        //final ArrayMap<String, AppState> appStateInfoList = mAppStateInfoCollector.getAppStateInfoList();
        checkSystemUpTime();

        if (mScreenOn ||mCharging) {

            checkNetworkConnection(false);
            mDownloading = false;
            mAppDownloadCount = 0;
            mLastNotDownloadTimeStamp = 0;
            mDownloadChangeCount = 0;

            mStandbyStartTime = 0;
            // note exit standby
            noteDeviceStateChanged(false, mStandbyStartTime);

            final List<UserInfo> users = mUserManager.getUsers();
            for (int ui = users.size() - 1; ui >= 0; ui--) {
                UserInfo user = users.get(ui);
                if (DEBUG) Slog.d(TAG, "- checkAllAppStateInfo() for user: " + user.id);

                final ArrayMap<String, AppState> appStateInfoList = mAppStateInfoCollector.getAppStateInfoList(user.id);
                for (int i=0;i<appStateInfoList.size();i++) {
                    AppState appState = appStateInfoList.valueAt(i);
                    try {
                        // recode the rxBytes if needed
                        // should be called before doing Evaluated time stamp update
                        appState.updateAppTrafficStats(true);

                        // note exit standby
                        appState.noteDeviceStateChanged(false, 0);

                        // clear constraint
                        clearConstrain(appState);

                    } catch (Exception e) {
                        // fall through
                    }
                }
            }

            checkPowerSaveMode(false);
        } else {

            // save the standby start time
            mStandbyStartTime = SystemClock.elapsedRealtime();

            mDownloading = false;
            mAppDownloadCount = 0;
            // should be equal to mStandbyStartTime, this will be used in needCheckNetworkConnection()
            mLastNotDownloadTimeStamp = mStandbyStartTime;
            mDownloadChangeCount = 0;

            final List<UserInfo> users = mUserManager.getUsers();
            for (int ui = users.size() - 1; ui >= 0; ui--) {
                UserInfo user = users.get(ui);
                if (DEBUG) Slog.d(TAG, "- checkAllAppStateInfo() for user: " + user.id);

                final ArrayMap<String, AppState> appStateInfoList = mAppStateInfoCollector.getAppStateInfoList(user.id);
                for (int i=0;i<appStateInfoList.size();i++) {
                    AppState appState = appStateInfoList.valueAt(i);

                    // recode the rxBytes if needed
                    // should be called before doing Evaluated time stamp update
                    appState.updateAppTrafficStats(false);

                    // note enter standby
                    appState.noteDeviceStateChanged(true, mStandbyStartTime);

                    // update Notification flags
                    appState.updateActiveNotificationState(mContext);

                    // notify helpers to do Evaluated time stamp update
                    updateAppEvaluatedTimeStamp(appState);

                }
            }

            // note standby
            noteDeviceStateChanged(true, mStandbyStartTime);

            // check current power save mode
            checkPowerSaveMode(true);

           //reset next check
            scheduleAlarmLocked(SCREENOFF_CHECK_INTERVAL);
        }
    }


    // To handle the app download state changed
    private void handleDownloadStateChanged(AppState appState, int newState) {
        mDownloadChangeCount--;
        if (appState == null) return;

        boolean newDownload = (newState == 1);

        if (appState.mDoingDownload == newDownload) return;

        appState.mDoingDownload = newDownload;

        // notify helpers to do Evaluated time stamp update
        updateAppEvaluatedTimeStamp(appState);

        if (appState.mDoingDownload)
            incAppDownloadCount();
        else
            decAppDownloadCount();


        if (DEBUG) Slog.d(TAG, "handleDownloadStateChanged() :"
            + " app:" + appState.mPackageName
            + " is download:" + newState
            + " mAppDownloadCount:" + mAppDownloadCount);

        if (!mScreenOn && !mCharging)
            checkNetworkConnection(true);
    }



    /////////////////Below for Api wrap Other Module////////////////////////////////////////////////////////

    private void setPowerGuruInterval(int interval) {
        if (!mPowerControllerGuruEnabled || mPowerGuruService == null) return;
        try {
            mPowerGuruService.setAlignInterval(interval);
        } catch (RemoteException e) {
            // fall through
        }
    }

    private int getPowerGuruOrignalInterval() {
        if (!mPowerControllerGuruEnabled || mPowerGuruService == null) return 0;
        try {
            return mPowerGuruService.getOrignalAlignInterval();
        } catch (RemoteException e) {
            // fall through
            return 0;
        }
    }

    private void notifyDozeStateChanged() {
        if (DEBUG) Slog.d(TAG, "notifyDozeStateChanged() E");

        if (mEnablePowerGuruDuringDozeOn) {
            Slog.d(TAG, "allow Power Guru to be work during Doze is enter!!");
            return;
        }

        if (mPowerGuruService == null) return;
        try {
            mPowerGuruService.notifyDozeStateChanged();
        } catch (RemoteException e) {
            // fall through
        }
    }

    /////////////////For Api wrap Other Module END////////////////////////////////////////////////////////



    /////////////////Below for App Traffic information////////////////////////////////////////////////////////

    private boolean isAppDoingDownloadInternal(AppState state) {
        int procState = state.mProcState;

        boolean doingDownload = false;
        if (state.mRxBytesWhenStartEvaluated > 0) {
            long now = SystemClock.elapsedRealtime();
            long currentRxBytes = TrafficStats.getUidRxBytes(state.mUid);
            long secondsSpended = (now - state.mTimeStampForRxBytes)/1000;
            if (DEBUG) Slog.d(TAG, "uid:" + state.mUid + " packageName:" + state.mPackageName
                + " currentRxBytes:" + currentRxBytes + " startRxBytes:" + state.mRxBytesWhenStartEvaluated
                + " timespended: " + secondsSpended + " avgRate:" + (currentRxBytes - state.mRxBytesWhenStartEvaluated)/(secondsSpended+1));
            if (currentRxBytes - state.mRxBytesWhenStartEvaluated
                > (MIN_DATA_RATE * secondsSpended) || (secondsSpended < DOWNLOAD_CHECKING_MIN_TIME_SPAN))
                doingDownload = true;

            if (doingDownload && secondsSpended > DOWNLOAD_CHECKING_MIN_TIME_SPAN) {
                long systemUpDuration = secondsSpended*1000;
                if (mLastSystemUpTimeStamp > 0)
                    systemUpDuration = (now - mLastSystemUpTimeStamp);
                if (DEBUG) Slog.d(TAG, "systemUpDuration:" + systemUpDuration + " secondsSpended:" + secondsSpended);

                if (systemUpDuration < (secondsSpended - 2 *SYSTEM_UP_CHECK_INTERVAL)) {
                    if (DEBUG) Slog.d(TAG, "systemUpDuration:" + systemUpDuration + "but secondsSpended:" + secondsSpended
                        + " clear download flag!!");
                    doingDownload = false;
                }
            }

            //Download state changed!!
            if (state.mDoingDownload != doingDownload) {
                mDownloadChangeCount++;
                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_DOWNLOAD_STATE_CHANGED, (doingDownload?1:0), 0, state));
            }

            if (doingDownload) {
                if (secondsSpended >= DOWNLOAD_CHECKING_MIN_TIME_SPAN) {
                    state.mRxBytesWhenStartEvaluated = currentRxBytes;
                    state.mTimeStampForRxBytes = SystemClock.elapsedRealtime();
                    state.mUsedTimeSliceCount++;
                }
                return true;
            }
        }
        return false;
    }

    /////////////////For App Traffic information END////////////////////////////////////////////////////////


    private int getAppCategoryTypeInternal(String packageName) {

        int index = mAppCategoryList.indexOfKey(packageName);
        if (index >= 0) {
            int type = mAppCategoryList.valueAt(index);
            return type;
        }

        return PowerDataBaseControl.UNKNOWN;
    }

    private boolean isPlayingMusicInternal(AppState appState) {
        if (appState.mPlayingMusic) return true;

        if(mAudioManager != null && !mAudioManager.isMusicActive()
            /*&& !mAudioManager.isFmActive()*/){
            return false;
        }

        if (DEBUG_MORE) Slog.d(TAG, "mAudioManager.isMusicActive(): " + mAudioManager.isMusicActive()
            + " getMode():" + mAudioManager.getMode());

        boolean playing = appState.isPlayingMusic();

        // if app is still playing music after system standby for APP_PLAYING_MUSIC_THRESHOLD
        // set app to be playing music
        if (playing && mStandbyStartTime > 0) {
            long standbyDuration = SystemClock.elapsedRealtime() - mStandbyStartTime;
            if (standbyDuration > APP_PLAYING_MUSIC_THRESHOLD)
                appState.setPlayingMusicState(true);
        }

        return playing;
    }

    private boolean isPlayingMusicInternal() {
        if(mAudioManager != null && !mAudioManager.isMusicActive()
            /*&& !mAudioManager.isFmActive()*/){
            return false;
        }

        if (DEBUG_MORE) Slog.d(TAG, "mAudioManager.isMusicActive(): " + mAudioManager.isMusicActive()
            + " getMode():" + mAudioManager.getMode());

        return true;
    }

    // Note: Bug 698133 appIdle cts fail -->BEG
    // check if doing cts/gts test
    private void checkCtsGtsTesting(String pkgName) {
        // Ugly: we have to check if doing cts/gts test
        // is cts/gts test, then
        if (mIgnoreProcStateForAppIdle && Util.isCts(pkgName)) {
            if (DEBUG_LOG) Slog.d(TAG, "CTS/GTS app: " + pkgName + ", see as doing cts/gts test, clear mIgnoreProcStateForAppIdle!!");
            try {
                //LocalServices.getService(NetworkPolicyManagerInternal.class).setIgnoreProcStateForAppIdle(false);
                PowerControllerHelper.getInstance(mContext).setIgnoreProcStateForAppIdle(false);

                mIgnoreProcStateForAppIdle = false;
            } catch (Exception e) {
                Slog.d(TAG, "E:ignoreProcStateForAppIdle: "  + e);
            }
        }
    }
    // Note: Bug 698133 appIdle cts fail <--END


    /////////////////Below for App uid state infor////////////////////////////////////////////////////////

    /**
     * Process state of UID changed; if needed, will trigger
     */
    private void updateUidStateLocked(int uid, int uidState) {
        final int oldUidState = mUidState.get(uid, ActivityManager.PROCESS_STATE_CACHED_EMPTY);
        if (oldUidState != uidState) {
            String appName = null;
            // state changed, push updated rules
            mUidState.put(uid, uidState);

            try {
                appName = AppGlobals.getPackageManager().getNameForUid(uid);
            } catch (RemoteException e) {
                // can't happen; package manager is process-local
            }

            if (DEBUG) Slog.d(TAG, "updateUidStateLocked: packageName:" + appName + ", uid:" + uid
                + " state change from "  + Util.ProcState2Str(oldUidState) + " to " + Util.ProcState2Str(uidState));

            if (uidState == ActivityManager.PROCESS_STATE_CACHED_EMPTY) {
                if (DEBUG)
                    Slog.d(TAG, "updateUidStateLocked: packageName:" + appName + ", uid:" + uid + " changed from non-cached to cached_empty, just return!");
                return;
            }
            if ((null != appName) ) {
                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_UID_STATE_CHANGED, uid, uidState, appName));
            }
        }
    }

    private void removeUidStateLocked(int uid) {
        final int index = mUidState.indexOfKey(uid);
        if (index >= 0) {
            final int oldUidState = mUidState.valueAt(index);
            mUidState.removeAt(index);
            String appName = null;
            try {
                appName = AppGlobals.getPackageManager().getNameForUid(uid);
            } catch (RemoteException e) {
                // can't happen; package manager is process-local
            }
            if (DEBUG_MORE) Slog.d(TAG, "removeUidStateLocked: packageName:" + appName + ", uid:" + uid + " state : "  + Util.ProcState2Str(oldUidState));

            if ((null != appName) ) {
                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_UID_STATE_CHANGED, uid, ActivityManager.PROCESS_STATE_CACHED_EMPTY, appName));
                if (mBackgroundCleanHelper != null) {
                    mBackgroundCleanHelper.noteAppStopped(uid, appName);
                }
            }
        }
    }

    final private IUidObserver mUidObserver = new IUidObserver.Stub() {
        @Override public void onUidStateChanged(int uid, int procState, long procStateSeq) throws RemoteException {
            synchronized (mUidStateLock) {
                updateUidStateLocked(uid, procState);
            }
            mAppStateInfoCollector.updateUidState(uid, procState);
        }

        @Override public void onUidGone(int uid, boolean disabled) throws RemoteException {
            synchronized (mUidStateLock) {
                removeUidStateLocked(uid);
            }
            mAppStateInfoCollector.removeUidState(uid);
        }

        @Override public void onUidActive(int uid) throws RemoteException {
            //synchronized (mUidStateLock) {
            //    updateUidStateLocked(uid, ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND);//?
            //}
        }


        @Override public void onUidIdle(int uid, boolean disabled) throws RemoteException {
            //synchronized (mUidStateLock) {
            //    removeUidStateLocked(uid);
            //}
        }

        @Override public void onUidCachedChanged(int uid, boolean cached) throws RemoteException {
        }

    };

    private void registerUidObserver() {
        try {
            if (mActivityManager != null)
                mActivityManager.registerUidObserver(mUidObserver, ActivityManager.UID_OBSERVER_PROCSTATE
                  | ActivityManager.UID_OBSERVER_GONE
                  /*| ActivityManager.UID_OBSERVER_IDLE
                  | ActivityManager.UID_OBSERVER_ACTIVE*/,
                  ActivityManager.PROCESS_STATE_UNKNOWN, null);
        } catch (RemoteException e) {
            // ignored; both services live in system_server
        }
    }

    private final class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            synchronized (mLock) {
                boolean curLowPower = (mPowerSaveMode == PowerManagerEx.MODE_LOWPOWER);
                boolean nativeLowPower = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.LOW_POWER_MODE, 0) != 0;
            }
        }
    }

    private void registerForSettings() {
        mSettingsObserver = new SettingsObserver(msgHandler);
        mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
            Settings.Global.LOW_POWER_MODE), false, mSettingsObserver, UserHandle.USER_ALL);
    }

    /////////////////For App uid state infor END////////////////////////////////////////////////////////


    static final int STATE_NOCHANGE = 0;
    static final int STATE_BG2FG = -2;
    static final int STATE_FG2BG = 2;

    private int getStateChange(int oldState, int newState) {
        int oldFlag = isBGState(oldState)?-1:1;
        int newFlag = isBGState(newState)?-1:1;

        return(oldFlag - newFlag);
    }

    private boolean isBGState(int state) {
        return (state == Event.MOVE_TO_BACKGROUND) ;
    }

    private boolean isUserActivityState(int state) {
        return (state == Event.USER_INTERACTION) ;
    }

    private boolean isDebug(){
        return Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug");
    }

    void scheduleAlarmLocked(long delay) {
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME,
            (SystemClock.elapsedRealtime()) + delay, mAlarmIntent);
    }

    void cancelAlarmLocked() {
        mAlarmManager.cancel(mAlarmIntent);
    }

    void checkDB() {
        PowerDataBaseControl powerDb = new PowerDataBaseControl(mContext);
        if (!powerDb.checkInfoDB()) {
          Slog.e(TAG, "CheckinfoDB error!");
          return;
        }

        powerDb.openDB();
        mAppCategoryList = powerDb.queryAll();
        powerDb.closeDB();

        //dump mAppCategoryList
        if (DEBUG) {
            if (mAppCategoryList != null) {
                for (int i=0;i<mAppCategoryList.size();i++) {
                    String pkgName = mAppCategoryList.keyAt(i);
                    int type = mAppCategoryList.valueAt(i);
                    Slog.d(TAG, "pkgName:" + pkgName + " type:" + type);
                }
            }
        }
    }

    private void initData() {
        // load Power config, should before create helpers
        mPowerConfig = PowerConfig.getInstance();

        mRecogAlgorithm = RecogAlgorithm.getInstance(mContext);
        mRecogAlgorithm.createAlgorithms();

        // create Helpers
        createPowerSaveHelpers();
        // init app power save config
        AppPowerSaveConfig.readConfig(mAppConfigMap);
        // init helpers according to the previous app powersave config
        initPowerSaveHelpers();

        try {
            checkDB();

            if (mBackgroundCleanHelper != null)
                mBackgroundCleanHelper.loadInstalledPackages();

            if (mAppIdleHelper != null)
                mAppIdleHelper.initData();
        } catch (Exception e) {}

        // load white app list
        updateWhiteAppList();

        // init powersave mode
        readConfig();

        // add for bug#947741
        checkBatteryWarningLevel();

        // mPrePowerSaveMode will be overwrite by mPowerSaveMode after updatePowerSaveMode(), so record it for restore
        int tempMode = mPrePowerSaveMode;

        updateBatteryLevelLow(true);
        if (DEBUG) Slog.d(TAG, "initData(), mPowerSaveMode: " + mPowerSaveMode + " mPrePowerSaveMode:" + mPrePowerSaveMode);
        // initial powersave mode
        updatePowerSaveMode();
        if (DEBUG) Slog.d(TAG, "initData() 2, mPowerSaveMode: " + mPowerSaveMode + " mPrePowerSaveMode:" + mPrePowerSaveMode);

        // for bug#771801
        if (PowerManagerEx.MODE_ULTRASAVING == mPowerSaveMode
            && PowerManagerEx.MODE_ULTRASAVING == tempMode) {
            if (DEBUG) Slog.d(TAG, "initData() 3, mPowerSaveMode: " + mPowerSaveMode
                + "  and saved mPrePowerSaveMode:" + tempMode + " reset mPrePowerSaveMode to default!!");
            mPrePowerSaveMode = PowerManagerEx.MODE_SMART;
        } else {
            mPrePowerSaveMode = tempMode;
        }

        // for bug#939073
        if (PowerManagerEx.MODE_ULTRASAVING == mPowerSaveMode
            || PowerManagerEx.MODE_LOWPOWER == mPowerSaveMode) {
            if(DEBUG) Slog.d(TAG, "boot up with power save mode:" + mPowerSaveMode + " set mWaittingRestoreStates !");
            mWaittingRestoreStates = true;
        }

        if (mBackgroundCleanHelper != null)
            mBackgroundCleanHelper.disbleLauncherAppIfNeed(mDisabledLanucherAppList_UltraSave);

        mInitFinished = true;
    }

    private void initDataForBootCompleted() {

        try {
            if (mBackgroundCleanHelper != null)
                mBackgroundCleanHelper.initDataForBootCompleted();
        } catch (Exception e) {}

        mBootCompleted = true;

        updatePowerSaveMode();

        // load AppStatsService
        if (mAppStatsCollectEnabled && mAppStatsServiceLoader != null)
            mAppStatsServiceLoader.loadService();
    }

    private void updateWhiteAppList() {
        try {
            String[] whitelistExceptIdle = mDeviceIdleController.getFullPowerWhitelistExceptIdle();
            synchronized (mWhiteListLock) {
                if (whitelistExceptIdle != null) {
                    mPowerSaveWhitelistExceptIdleAppList.clear();
                    Slog.d(TAG, "whitelistExceptIdle: " + whitelistExceptIdle.length);
                    for (String appName : whitelistExceptIdle) {
                        Slog.d(TAG, "app: " + appName);
                         if (appName != null)
                            mPowerSaveWhitelistExceptIdleAppList.add(appName);
                    }
                }
                mAppIdPowerSaveWhitelistExceptIdleAppList = mDeviceIdleController.getAppIdWhitelistExceptIdle();
            }
        } catch (Exception e) {
            Slog.d(TAG, "e:" + e);
        }


        if (DEBUG) {
            Slog.d(TAG, "mPowerSaveWhitelistExceptIdleAppList: " + mPowerSaveWhitelistExceptIdleAppList.size());
            for (int i=0;i<mPowerSaveWhitelistExceptIdleAppList.size();i++) {
                Slog.d(TAG, "App:" + mPowerSaveWhitelistExceptIdleAppList.get(i));
            }
            Slog.d(TAG, "mAppIdPowerSaveWhitelistExceptIdleAppList=" + Arrays.toString(mAppIdPowerSaveWhitelistExceptIdleAppList));
        }

        // make sure power save config list is sync with doze white list
        syncPowerSaveConfigWithDozeWhitelist();
    }

    // judge which powersave mode should be set
    // 1. if battery is low and without power, set to MODECONFIG_AUTOLOWPOWER
    // 2. if in pre-define period, set to MODECONFIG_SCHEDULE
    // 3. else set to MODECONFIG_USER
    private void updatePowerSaveMode() {
        final boolean wasPowered = mBatteryManagerInternal.isPowered(BatteryManager.BATTERY_PLUGGED_ANY);
        if (DEBUG) Slog.d(TAG, "updatePowerSaveMode(), wasPowered: " + wasPowered + ", mBatteryLevelLow: " + mBatteryLevelLow);

        // Add for bug#797412
        int preModeConfigIndex = mModeConfigIndex;

        // Add for bug#940200
        boolean changedByAutoExitWhenPlugged = false;

        if (wasPowered || !mBatteryLevelLow)
            mForcePowerSave = false;

        //save current power save mode to be restored
        if (wasPowered && mSetSmartSaveMode && !mPowerSaveMode_Switch) {
            mPrePowerSaveMode = mModeConfigArray[mModeConfigIndex].mMode;
            mPrePowerSaveModeIndex = mModeConfigIndex;
            mPrePowerSaveModeStatus = mModeConfigArray[mModeConfigIndex].mEnable;
            Slog.d(TAG, "updatePowerSaveMode(), mPrePowerSaveModeIndex: " + mModeConfigIndex
                + ", mPrePowerSaveMode: " + mPrePowerSaveMode);
        }

        if (mPowerSaveMode_Switch && (!wasPowered || !mSetSmartSaveMode)) {
            mModeConfigIndex = mPrePowerSaveModeIndex;
            mModeConfigArray[mModeConfigIndex].mMode = mPrePowerSaveMode;
            mModeConfigArray[mModeConfigIndex].mEnable = mPrePowerSaveModeStatus;
            if (mModeConfigIndex == MODECONFIG_USER)
                mModeConfigArray[mModeConfigIndex].mDependencyMet = true;

            //check low battery level when usb unplug or switch disable
            updateBatteryLevelLow(false);

            mPowerSaveMode_Switch = false;
            Slog.d(TAG, "updatePowerSaveMode(), restore old mode when usb unplug or power save mode switch disable");
            // Add for bug#940200
            changedByAutoExitWhenPlugged = true;
        }

        mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mDependencyMet = false;
        mModeConfigArray[MODECONFIG_SCHEDULE].mDependencyMet = false;

        if (mBatteryLevelLow && mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mEnable
            && mBootCompleted && mCurrentUserId == UserHandle.USER_SYSTEM) {
            if (!mExitLowPower_WithPower || !wasPowered) {
                if (DEBUG) Slog.d(TAG, "updatePowerSaveMode(), autoLowPower met");
                mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mDependencyMet = true;
            }
        }

        if (mModeConfigArray[MODECONFIG_SCHEDULE].mEnable && isMeetModeSwitchSchedule()
            && mBootCompleted && mCurrentUserId == UserHandle.USER_SYSTEM) {
            if (DEBUG) Slog.d(TAG, "updatePowerSaveMode(), schedule met");
            mModeConfigArray[MODECONFIG_SCHEDULE].mDependencyMet = true;
        }

        if (mForcePowerSave) {
            mModeConfigIndex = MODECONFIG_USER;
            if (DEBUG) Slog.d(TAG, "updatePowerSaveMode(), force low power, set to " + ModeConfig2Str(mModeConfigIndex));
            mModeConfigArray[MODECONFIG_USER].mMode = PowerManagerEx.MODE_LOWPOWER;
        } else {
            // get the available highest priority mode
            for (int i=0; i<mModeConfigArray.length; i++) {
                if (mModeConfigArray[i].mDependencyMet && mModeConfigArray[i].mEnable) {
                    mModeConfigIndex = i;
                    if (DEBUG) Slog.d(TAG, "updatePowerSaveMode(), set to " + ModeConfig2Str(mModeConfigIndex));
                    break;
                }
            }
        }

        if (wasPowered && mSetSmartSaveMode) {
            mModeConfigIndex = MODECONFIG_USER;
            mModeConfigArray[mModeConfigIndex].mMode = PowerManagerEx.MODE_SMART;
            mModeConfigArray[mModeConfigIndex].mEnable = true;
            mModeConfigArray[mModeConfigIndex].mDependencyMet = true;
            mPowerSaveMode_Switch = true;
            Slog.d(TAG, "updatePowerSaveMode(), mEnterNormalSaveMode and wasPowered are true, Now switch normal save mode");
            // Add for bug#940200
            changedByAutoExitWhenPlugged = true;
        }

        // Add for bug 868839,878708 START
        //   1.whe user set in "choose battery saver":mModeConfigIndex=MODEUSERCONFIG, mPreMode.mEnable=false
        //   2.auto switch:
        //   USER->AL(Auto Low Power):mModeConfigIndex = MODECONFIG_AUTOLOWPOWER,mPreMode.mEnable=true.
        //   USER->SP(Schedule Power):mModeConfigIndex = MODECONFIG_SCHEDULE,mPreMode.mEnable=true.
        //   SP->AL:mModeConfigIndex = MODECONFIG_AUTOLOWPOWER,mPreMode.mEnable=true.
        //   AL->SP:mModeConfigIndex = MODECONFIG_SCHEDULE,mPreMode.mEnable=false.
        //   AL->USER:mModeConfigIndex = MODECONFIG_USER,mPreMode.mEnable=false.
        //   SP->USER:mModeConfigIndex = MODECONFIG_USER,mPreMode.mEnable=false.
        if (mModeConfigIndex != MODECONFIG_USER || mModeConfigArray[preModeConfigIndex].mEnable) {
            if (isInVideoCallOrNormalCall(preModeConfigIndex)) {
                mModeConfigIndex = preModeConfigIndex;
            } else {
                // reset flag at the end of call
                mHasShowToastForSwichModeWhenInCall = false;
            }
        } else {
            mHasShowToastForSwichModeWhenInCall = false;
        }
        // Add for bug 868839,878708 END

        // Add for bug#940200
        if (!changedByAutoExitWhenPlugged) {
            // Add for bug#797412 --START
            showToastIfNeed(preModeConfigIndex);
            // Add for bug#797412 --END
        }

        updatePowerSaveModeInternal(mModeConfigArray[mModeConfigIndex].mMode);
        return;
    }

    private boolean isMeetModeSwitchSchedule() {
        long now = System.currentTimeMillis();
        Calendar nowCalendar = Calendar.getInstance();
        Calendar startCalendar = Calendar.getInstance();
        Calendar endCalendar = Calendar.getInstance();
        nowCalendar.setTimeInMillis(now);
        startCalendar.setTimeInMillis(now);
        endCalendar.setTimeInMillis(now);
        startCalendar.set(Calendar.HOUR_OF_DAY, mStartHour);
        startCalendar.set(Calendar.MINUTE, mStartMinute);
        startCalendar.set(Calendar.SECOND, 0);
        startCalendar.set(Calendar.MILLISECOND, 0);
        endCalendar.set(Calendar.HOUR_OF_DAY, mEndHour);
        endCalendar.set(Calendar.MINUTE, mEndMinute);
        endCalendar.set(Calendar.SECOND, 0);
        endCalendar.set(Calendar.MILLISECOND, 0);
        if (startCalendar.after(endCalendar)) {
            return nowCalendar.before(endCalendar) || nowCalendar.after(startCalendar);
        }
        return nowCalendar.before(endCalendar) && nowCalendar.after(startCalendar);
    }


    // create helpers
    private void createPowerSaveHelpers() {
        Slog.d(TAG, "Begin create helpers ");
        mPowerGuruHelper = new PowerGuruHelper(mContext, mActivityManager, msgHandler);
        mAppIdleHelper = new AppIdleHelper(mContext, mActivityManager, msgHandler);
        mWakelockConstraintHelper = new WakelockConstraintHelper(mContext, mActivityManager, msgHandler);
        mBackgroundCleanHelper = new BackgroundCleanHelper(mContext, mActivityManager, msgHandler);
        mGpsConstraintHelper = new GpsConstraintHelper(mContext, mActivityManager, msgHandler);

        mHelpers = new ArrayList<PowerSaveHelper>();
        mHelpers.add(mPowerGuruHelper);
        mHelpers.add(mAppIdleHelper);
        mHelpers.add(mWakelockConstraintHelper);
        mHelpers.add(mBackgroundCleanHelper);
        mHelpers.add(mGpsConstraintHelper);
    }

    // init
    // notify helpers to init according mAppConfigMap and mPowerSaveMode
    private void initPowerSaveHelpers() {
        Slog.d(TAG, "initPowerSaveHelpers() E");
        if (DEBUG) Slog.d(TAG, "dump appconfig " + dumpAppConfigMap());

        //init helpers' configuration
        for (Map.Entry<String, AppPowerSaveConfig> cur : mAppConfigMap.entrySet()) {
            final String appName = cur.getKey();
            final AppPowerSaveConfig config = cur.getValue();
            for (int i = 0; i < AppPowerSaveConfig.ConfigType.TYPE_MAX.value; i++) {
                int newValue = AppPowerSaveConfig.getConfigValue(config, i);
                onPowerSaveConfigChanged(i, appName, 0, newValue, true);

                for (int j = 0; j < mHelpers.size(); j++) {
                    PowerSaveHelper helper = mHelpers.get(j);
                    if (0 != (AppPowerSaveConfig.mMaskArray[i] & helper.mMask)) {
                        helper.onPowerSaveConfigChanged(i, appName, 0, newValue, true);
                    }
                }
            }
        }

        for (int j = 0; j < mHelpers.size(); j++) {
            PowerSaveHelper helper = mHelpers.get(j);
            if (DEBUG) Slog.d(TAG, helper.toString());
        }
    }

    // when app powersave config changed, update all the whitelist/blacklist for helpers
    private void onPowerSaveConfigChanged(int configType, String appName, int oldValue, int newValue, boolean init) {
        if (DEBUG) Slog.d(TAG, "onPowerSaveConfigChanged(), init: " + init
            + ", configType: " + AppPowerSaveConfig.ConfigType2Str(configType)
            + ", appName: " + appName
            + ", oldValue: " + AppPowerSaveConfig.ConfigValue2Str(oldValue) + "(" + oldValue
            + "), newValue: " + AppPowerSaveConfig.ConfigValue2Str(newValue) + "(" + newValue +")");

        List<String> whiteList = mLocalService.getWhiteList(configType);
        List<String> blackList = mLocalService.getBlackList(configType);
        if (init) {
            if (AppPowerSaveConfig.ConfigType.TYPE_OPTIMIZE.value == configType) {
                if (0 == newValue) {
                    mLocalDeviceIdleController.addPowerSaveWhitelistAppLocal(appName);
                }
            } else {
                if (AppPowerSaveConfig.VALUE_OPTIMIZE == newValue) {
                    blackList.add(appName);
                } else if (AppPowerSaveConfig.VALUE_NO_OPTIMIZE == newValue) {
                    whiteList.add(appName);
                }
            }
        } else {
            if (AppPowerSaveConfig.ConfigType.TYPE_OPTIMIZE.value == configType) {
                if (0 == oldValue) {
                    mLocalDeviceIdleController.removePowerSaveWhitelistAppLocal(appName);
                }
                if (0 == newValue) {
                    mLocalDeviceIdleController.addPowerSaveWhitelistAppLocal(appName);
                }
            } else {
                if (AppPowerSaveConfig.VALUE_NO_OPTIMIZE == oldValue){
                    whiteList.remove(appName);
                } else if (AppPowerSaveConfig.VALUE_OPTIMIZE == oldValue) {
                    blackList.remove(appName);
                }

                if (AppPowerSaveConfig.VALUE_NO_OPTIMIZE == newValue){
                    whiteList.add(appName);
                } else if (AppPowerSaveConfig.VALUE_OPTIMIZE == newValue) {
                    blackList.add(appName);
                }
            }
        }
    }

    private String dumpAppConfigMap() {
        String out = "[AppPowerSaveConfig]\n";
        for (Map.Entry<String, AppPowerSaveConfig> cur : mAppConfigMap.entrySet()) {
            final String appName = cur.getKey();
            final AppPowerSaveConfig config = cur.getValue();
            out += appName + " --- " + config + "\n";
        }
        return out;
    }

    // local service
    public final class LocalService  {
        public boolean judgeAppLaunchAllowed(Intent intent, String targetApp, int targetUid,
            int callerUid, String callerApp, String reason) {
            try {
                if (mBackgroundCleanHelper != null) {
                    boolean ret = mBackgroundCleanHelper.judgeAppLaunchAllowed(intent, targetApp, targetUid, callerUid, callerApp, reason);
                    return ret;
                }
            } catch (Exception e) {
                if (DEBUG) {
                    Slog.d(TAG, "judgeAppLaunchAllowed Exception:" + e);
                    e.printStackTrace();
                }
            }
            return true;
        }

        public int getAppCategoryType(String pkgName) {
            return getAppCategoryTypeInternal(pkgName);
        }

        // return type  as defined in PowerControllerInternal.java
        //  APP_CATEGORY_TYPE_UNKNOWN = 0;
        //  APP_CATEGORY_TYPE_MESSAGE = 1;
        public int getAppCategoryType(String pkgName, int uid) {
            int appType = getAppCategoryTypeInternal(pkgName);
            if (PowerDataBaseControl.MESSAGE == appType) {
                return PowerControllerInternal.APP_CATEGORY_TYPE_MESSAGE;
            } else if (PowerDataBaseControl.UNKNOWN == appType) {
                try {
                    AppState appState = mAppStateInfoCollector.getAppState(pkgName, UserHandle.getUserId(uid));
                    if (appState == null) return PowerControllerInternal.APP_CATEGORY_TYPE_UNKNOWN;

                    if (appState.mFCMorGCMForAppIdle || appState.mFCMorGCMForBgClean) {
                        return PowerControllerInternal.APP_CATEGORY_TYPE_MESSAGE;
                    }
                } catch (Exception e) {}
            }
            return  PowerControllerInternal.APP_CATEGORY_TYPE_UNKNOWN;
        }

        public boolean disableNetworkRestrictForMessageApp() {
            return  mDisableNetworkRestrictForMessageApp;
        }

        public boolean isAppDoingDownload(AppState state) {
            return isAppDoingDownloadInternal(state);
        }

        public  boolean isPlayingMusic(AppState state) {
            return isPlayingMusicInternal(state);
        }

        public  boolean isPlayingMusic() {
            return isPlayingMusicInternal();
        }


        // whether in doze white list
        public  boolean isWhitelistApp(String pkgName) {
            synchronized (mWhiteListLock) {
                int index = mPowerSaveWhitelistExceptIdleAppList.indexOf(pkgName);
                if (index >= 0) {
                    return true;
                }
                return false;
            }
         }

        public  boolean isWhitelistApp(String pkgName, int uid) {
            synchronized (mWhiteListLock) {
                int index = mPowerSaveWhitelistExceptIdleAppList.indexOf(pkgName);
                if (index >= 0) {
                    return true;
                }
                final int appid = UserHandle.getAppId(uid);
                if (Arrays.binarySearch(mAppIdPowerSaveWhitelistExceptIdleAppList, appid) >= 0) {
                    return true;
                }

                return false;
            }
         }

        // return the lastTouchEvent time stamp
        public long getLastTouchEventTimeStamp() {
            return mLastTouchEventTimeStamp;
        }

        // re-check all app info
        public void reCheckAllAppInfoDelayed(long delayedMillSec) {
            msgHandler.removeMessages(MSG_CHECK);
            msgHandler.sendMessageDelayed(msgHandler.obtainMessage(MSG_CHECK), delayedMillSec);
        }


        public boolean addAppIdleListener(AppIdleHelper.Listener listener) {
            if (mAppIdleHelper != null && listener != null) {
                mAppIdleHelper.addListener(listener);
                return true;
            }
            return false;
        }

        public int getAppBehaviorType(String pkgName, int uid) {
            return getAppBehaviorTypeInternal(pkgName, uid);
        }

        //
        //  ----------------- interface exported to powermanagerex ----------------------
        //

        //called by setting from powermanagerex
        // get current powersave mode
        public int getPowerSaveMode() {
            return mPowerSaveMode;
        }

        public int getPrePowerSaveMode() {
            return mPrePowerSaveMode;
        }

        //called by setting from powermanagerex
        boolean setPowerSaveMode(int mode) {
            if (DEBUG) Slog.d(TAG, "setPowerSaveMode(" + mode + "), user mode: " + mModeConfigArray[MODECONFIG_USER].mMode);

            if ((mode < PowerManagerEx.MODE_PERFORMANCE) || (mode > PowerManagerEx.MODE_MAX)) {
                Slog.e(TAG, "invalid mode: " + mode);
                return false;
            }

            if ((mode == PowerManagerEx.MODE_ULTRASAVING) && !mUltraSavingEnabled) {
                Slog.e(TAG, "ULTRASAVING mode is disabled");
                return false;
            }
            msgHandler.removeMessages(MSG_SET_POWERSAVE_MODE);
            return msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SET_POWERSAVE_MODE, mode, 0));
        }

        boolean forcePowerSaveMode(boolean mode) {
            if (mode) {
                return setPowerSaveMode(PowerManagerEx.MODE_LOWPOWER);
                //return msgHandler.sendMessage(msgHandler.obtainMessage(MSG_FORCE_POWERSAVE, 0, 0));
            } else if (!mUltraSavingEnabled) {
                return setPowerSaveMode(PowerManagerEx.MODE_SMART);
            }
            return false;
        }

        //called by setting from powermanagerex
        AppPowerSaveConfig getAppPowerSaveConfig(String appName) {
            AppPowerSaveConfig config = mAppConfigMap.get(appName);
            if (null == config) {
                config = new AppPowerSaveConfig();
                //mAppConfigMap.put(appName, config);
                //AppPowerSaveConfig.writeConfig(mAppConfigMap);
            }
            if (DEBUG) Slog.d(TAG, "getAppPowerSaveConfig() X, appName: " + appName + ", config: " + config);
            return config;
        }

        int getAppPowerSaveConfigWithType(String appName, int type) {
            AppPowerSaveConfig config = getAppPowerSaveConfig(appName);
            if (DEBUG) Slog.d(TAG, "getAppPowerSaveConfigWithType() X, appName: " + appName + ", type: " + AppPowerSaveConfig.ConfigType2Str(type)
                + ", value: " + AppPowerSaveConfig.ConfigValue2Str(AppPowerSaveConfig.getConfigValue(config, type)));

            return AppPowerSaveConfig.getConfigValue(config, type);
        }

        int getAppPowerSaveConfgWithTypeInternal(String appName, int type) {
            AppPowerSaveConfig config = mAppConfigMap.get(appName);
            if (null == config) {
                return AppPowerSaveConfig.getDefaultValue(type);
            }
            return AppPowerSaveConfig.getConfigValue(config, type);
        }

        //called by setting from powermanagerex
        boolean setAppPowerSaveConfig(String appName, AppPowerSaveConfig config) {
            if ((null == appName) || (!config.isValid())) {
                return false;
            }
            if (DEBUG) Slog.d(TAG, "setAppPowerSaveConfig(), appName: " + appName + ", new config: " + config);

            try {
                synchronized (mMsgLock) {
                    if (!msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SET_APP_POWERSAVE_CONFIG, new MsgPowerSaveConfig(appName, config)))) return false;
                    mMsgLock.wait();
                }
            } catch (InterruptedException e) {
                Slog.e(TAG, e.toString());
            }

            return true;
        }

        boolean setAppPowerSaveConfigWithType(String appName, int type, int value) {
            if (DEBUG) Slog.d(TAG, "setAppPowerSaveConfigWithType(), appName: " + appName + ", type: " + AppPowerSaveConfig.ConfigType2Str(type) + ", value: " + AppPowerSaveConfig.ConfigValue2Str(value));
            if ((null == appName) || ((type <= AppPowerSaveConfig.ConfigType.TYPE_NULL.value) || (type >= AppPowerSaveConfig.ConfigType.TYPE_MAX.value))
                || ((value < AppPowerSaveConfig.VALUE_AUTO) || (value > AppPowerSaveConfig.VALUE_NO_OPTIMIZE))) {
                return false;
            }

            try {
                synchronized (mMsgLock) {
                    if (!msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SET_APP_POWERSAVE_CONFIG_WITHTYPE, type, value, appName))) return false;
                    mMsgLock.wait();
                }
            } catch (InterruptedException e) {
                Slog.e(TAG, e.toString());
            }

            return true;
        }

        boolean setAppPowerSaveConfigListWithType(List<String> appList, int type, int value) {
            if (DEBUG) Slog.d(TAG, "setAppPowerSaveConfigListWithType(), appList: " + appList + ", type: " + AppPowerSaveConfig.ConfigType2Str(type) + ", value: " + AppPowerSaveConfig.ConfigValue2Str(value));
            if ((null == appList) || ((type <= AppPowerSaveConfig.ConfigType.TYPE_NULL.value) || (type >= AppPowerSaveConfig.ConfigType.TYPE_MAX.value))
                || ((value < AppPowerSaveConfig.VALUE_AUTO) || (value > AppPowerSaveConfig.VALUE_NO_OPTIMIZE))) {
                return false;
            }

            try {
                synchronized (mMsgLock) {
                    if (!msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SET_APP_POWERSAVE_CONFIG_LIST_WITHTYPE, type, value, appList))) return false;
                    mMsgLock.wait();
                }
            } catch (InterruptedException e) {
                Slog.e(TAG, e.toString());
            }

            return true;
        }

        int getAppNumWithSpecificConfig(int type, int value) {
            if (((type <= AppPowerSaveConfig.ConfigType.TYPE_NULL.value) || (type >= AppPowerSaveConfig.ConfigType.TYPE_MAX.value))
                || ((value < AppPowerSaveConfig.VALUE_AUTO) || (value > AppPowerSaveConfig.VALUE_NO_OPTIMIZE))) {
                return 0;
            }

            int num = 0;
            for (Map.Entry<String, AppPowerSaveConfig> cur : mAppConfigMap.entrySet()) {
                final AppPowerSaveConfig config = cur.getValue();
                if (value == AppPowerSaveConfig.getConfigValue(config, type))
                    num++;
            }
            if (DEBUG) Slog.d(TAG, "getAppNumWithSpecificConfig(), type: " + AppPowerSaveConfig.ConfigType2Str(type) + ", value: " + AppPowerSaveConfig.ConfigValue2Str(value) + ", num: " + num);
            return num;
        }

        int getAutoLowPower_Mode() {
            return mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mMode;
        }

        boolean setAutoLowPower_Mode(int mode) {
            if (DEBUG) Slog.d(TAG, "setAutoLowPowerMode(" + mode + "), auto lowpower mode: " + mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mMode);

            if ((mode < PowerManagerEx.MODE_PERFORMANCE) || (mode > PowerManagerEx.MODE_ULTRASAVING)) {
                Slog.e(TAG, "invalid mode: " + mode);
                return false;
            }

            if (mode == mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mMode) {
                return true;
            }
            msgHandler.removeMessages(MSG_SET_AUTOLOWPOWER_MODE);
            return msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SET_AUTOLOWPOWER_MODE, mode, 0));
        }

        boolean getAutoLowPower_Enable() {
            return mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mEnable;
        }

        boolean setAutoLowPower_Enable(boolean enable) {
            if (DEBUG) Slog.d(TAG, "setAutoLowPower_Enable(" + enable + ")");
            return msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SET_AUTOLOWPOWER_ENABLE, enable));
        }

        int getAutoLowPower_BattValue() {
            return mLowBatteryWarningLevel;
        }

        boolean setAutoLowPower_BattValue(int battValue) {
            if ((battValue < 0) || (battValue > 100)) return false;
            if (DEBUG) Slog.d(TAG, "setAutoLowPower_BattValue(" + battValue + ")");
            return msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SET_AUTOLOWPOWER_BATTVALUE, battValue, 0));
        }

        boolean getAutoLowPower_ExitWithPower() {
            return mExitLowPower_WithPower;
        }

        boolean setAutoLowPower_ExitWithPower(boolean bExit) {
            if (DEBUG) Slog.d(TAG, "setAutoLowPower_ExitWithPower(" + bExit + ")");
            return msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SET_AUTOLOWPOWER_EXITWITHPOWER, bExit));
        }

        boolean getSmartSavingModeWhenCharging() {
            Slog.d(TAG, "getSmartSavingModeWhenCharging status");
            return mSetSmartSaveMode;
        }

        boolean setSmartSavingModeWhenCharging(boolean bExit) {
            Slog.d(TAG, "setSmartSavingModeWhenCharging(" + bExit + ")");

            try {
                synchronized (mMsgLock) {
                  if(!msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SET_SMARTSAVING_CHARGING, bExit))) return false;
                  mMsgLock.wait();
                }
            } catch (InterruptedException e) {
               Slog.e(TAG, e.toString());
            }

            return true;
        }

        boolean getSchedule_Enable() {
            return mModeConfigArray[MODECONFIG_SCHEDULE].mEnable;
        }

        boolean setSchedule_Enable(boolean enable) {
            if (DEBUG) Slog.d(TAG, "setSchedule_Enable(" + enable + ")");
            return msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SET_SCHEDULE_ENABLE, enable));
        }

        int getSchedule_Mode() {
            return mModeConfigArray[MODECONFIG_SCHEDULE].mMode;
        }

        boolean setSchedule_Mode(int mode) {
            if (DEBUG) Slog.d(TAG, "setScheduleMode(" + mode + "), schedule mode: " + mModeConfigArray[MODECONFIG_SCHEDULE].mMode);

            if ((mode < PowerManagerEx.MODE_PERFORMANCE) || (mode > PowerManagerEx.MODE_ULTRASAVING)) {
                Slog.e(TAG, "invalid mode: " + mode);
                return false;
            }

            if (mode == mModeConfigArray[MODECONFIG_SCHEDULE].mMode) {
                return true;
            }
            msgHandler.removeMessages(MSG_SET_SCHEDULE_MODE);
            return msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SET_SCHEDULE_MODE, mode, 0));
        }

        int getSchedulePowerMode_StartTime() {
            return (mStartHour * 100 + mStartMinute);
        }

        boolean setSchedulePowerMode_StartTime(int hour, int minute) {
            if ((hour < 0) || (hour>=24) || (minute<0) || (minute >=60))
                return false;
            if (DEBUG) Slog.d(TAG, "setSchedulePowerMode_StartTime(" + hour + ", " + minute + ")");
            return msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SET_SCHEDULE_STARTTIME, hour, minute));
        }

        int getSchedulePowerMode_EndTime() {
            return (mEndHour* 100 + mEndMinute);
        }

        boolean setSchedulePowerMode_EndTime(int hour, int minute) {
            if ((hour < 0) || (hour>=24) || (minute<0) || (minute >=60))
                return false;
            if (DEBUG) Slog.d(TAG, "setSchedulePowerMode_EndTime(" + hour + ", " + minute + ")");
            return msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SET_SCHEDULE_ENDTIME, hour, minute));
        }

        boolean addAllowedAppInUltraSavingMode(String componentNameStr) {
            return msgHandler.sendMessage(msgHandler.obtainMessage(MSG_ADD_APP_IN_ULTRAMODE, componentNameStr));
        }

        boolean delAllowedAppInUltraSavingMode(String componentNameStr) {
            return msgHandler.sendMessage(msgHandler.obtainMessage(MSG_DEL_APP_IN_ULTRAMODE, componentNameStr));
        }

        List<String> getAllowedAppListInUltraSavingMode() {
            //if (DEBUG) Slog.d(TAG, "getAllowedAppListInUltraSavingMode");

            // add for bug#961077
            List<String> appList = new ArrayList<String>();
            try {
                if (mAppList_UltraSave.size() > 0) {
                    for (int i=0; i< mAppList_UltraSave.size(); i++) {
                        String app = mAppList_UltraSave.get(i);
                        //if (DEBUG) Slog.d(TAG, "getAllowedAppListInUltraSavingMode:" + i + " :" + app);

                        boolean added = false;
                        if (app != null) {
                            String[] sArrays = app.split("#");
                            //if (DEBUG) Slog.d(TAG, "getAllowedAppListInUltraSavingMode: sArrays.length:" + sArrays.length);
                            if (sArrays.length > 2) {
                                long userId = Integer.valueOf(sArrays[2]);
                                if (mCurrentUserId == userId) {
                                    added = true;
                                }
                            } else {
                                added = true;
                            }

                            if (added) {
                                //if (DEBUG) Slog.d(TAG, "getAllowedAppListInUltraSavingMode: add: " + app);
                                appList.add(app);
                            }
                        }
                    }
                }
            } catch (Exception e) {}

            return appList; //mAppList_UltraSave;
        }

        // get the total count of wake up alarm of the app since system boot up
        int getWakeupAlarmCount(String appName) {
            if (mPowerGuruService != null) {
                try {
                    return mPowerGuruService.getWakeupAlarmCount(appName);
                } catch (RemoteException e) {
                    // fall through
                    return 0;
                }
            }
            return 0;
        }
        //
        //  ----------------- END ----------------------
        //

        List<String> getWhiteList(int configType) {
            List<String> list = mWhiteListArray[configType];
            if (null == list) {
                list = new ArrayList<String>();
                mWhiteListArray[configType] = list;
            }
            return list;
        }

        List<String> getBlackList(int configType) {
            List<String> list = mBlackListArray[configType];
            if (null == list) {
                list = new ArrayList<String>();
                mBlackListArray[configType] = list;
            }
            return list;
        }

        void updateAppPowerConsumerType(String appName, int type, int mask) {
            msgHandler.sendMessage(msgHandler.obtainMessage(MSG_UPDATE_APP_POWER_CONSUMER_TYPE, type, mask, appName));
        }

        List<String> getAppList_UltraMode() {
            return mAppList_UltraSave_Internal;
        }

        // called by BackgroundCleanHelpler
        // TODO: to move to powercontroller ??
        boolean isDisabledLauncherApp(String appName) {
            return (mDisabledLanucherAppList_UltraSave != null
                        && mDisabledLanucherAppList_UltraSave.contains(appName));
        }

        // called by BackgroundCleanHelpler
        // TODO: to move to powercontroller ??
        void updateDisabledLauncherAppList(String newAppList) {
            if (DEBUG) Slog.d(TAG, "oldDisabledLanucherAppList_UltraSave:" + mDisabledLanucherAppList_UltraSave
                + ", new mDisabledLanucherAppList_UltraSave:" + newAppList);
            mDisabledLanucherAppList_UltraSave = newAppList;
        }

        // called by BackgroundCleanHelpler, must called in powercontroller thread!!
        // TODO: to move to powercontroller ??
        void addDisabledLauncherAppWithoutSave(String appName) {
            if (appName == null) return;
            String oldDisabledLanucherAppList_UltraSave = mDisabledLanucherAppList_UltraSave;
            if (mDisabledLanucherAppList_UltraSave == null) {
                mDisabledLanucherAppList_UltraSave = appName + ";";
            } else if (!mDisabledLanucherAppList_UltraSave.contains(appName)) {
                 mDisabledLanucherAppList_UltraSave = oldDisabledLanucherAppList_UltraSave + appName + ";";
            }

            if (DEBUG) Slog.d(TAG, "oldDisabledLanucherAppList_UltraSave:" + oldDisabledLanucherAppList_UltraSave
                + ", mDisabledLanucherAppList_UltraSave:" + mDisabledLanucherAppList_UltraSave);
        }

        // called by BackgroundCleanHelpler, must called in powercontroller thread!!
        // TODO: to move to powercontroller ??
        void addDisabledLauncherApp(String appName) {
            addDisabledLauncherAppWithoutSave(appName);

            // save
            writeConfig();
        }

        // --- power mode observer
        public void registerPowerModeObserver(PowerModeListener listener) {
            mPowerModeListeners.add(listener);
        }

        // if a app is a type of admin app, such as com.baidu.appsearch
        boolean isAdminApp(String appName) {
            if (mBackgroundCleanHelper != null) {
                boolean ret = mBackgroundCleanHelper.isAdminApp(appName);
                return ret;
            }
            return false;
        }
    }

    //called by setting from powermanagerex
    //update mModeConfigArray[MODECONFIG_USER].mMode
    private void setPowerSaveModeInternal(int mode) {
        if (DEBUG) Slog.d(TAG, "setPowerSaveModeInternal() E, new mode: " + mode + ", old mode: " + mPowerSaveMode + ", [" + ModeConfig2Str(mModeConfigIndex) + "]");
        // exit force low power
        if (mForcePowerSave) {
            if (mPowerSaveMode != mode) {
                mForcePowerSave = false;

                mModeConfigArray[MODECONFIG_USER].mMode = mode;
                updatePowerSaveMode();
                msgHandler.removeMessages(MSG_SET_POWERSAVE_MODE);
                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_SET_POWERSAVE_MODE, mode, 0));
                return;
            }
        }

        if (mModeConfigArray[mModeConfigIndex].mMode != mode) {
            if (showAlert(mode)) return;
        }

        mModeConfigArray[MODECONFIG_USER].mMode = mode;
        updatePowerSaveMode();
    }

    /**
     * Show power switch or ultra powersaving alert dialog
     *
     * @return Returns whether need to show alert
     */
    private boolean showAlert(int mode) {
        String summaryStr = null;
        String modeStr = null;
        String titleStr = null;
        int layoutId = 0;

        boolean modeSwitch = false;
        boolean enterUltrasaving = false;
        boolean exitUltrasaving = false;
        mModeSwitch = false;
        // if in auto lowpower or schedule mode, should show dialog to user
        if ((mModeConfigIndex != MODECONFIG_USER)
                && (mModeConfigArray[mModeConfigIndex].mMode != mode)) {
            mModeSwitch = modeSwitch = true;
        }
        if ((mode == PowerManagerEx.MODE_ULTRASAVING) && !mDontRemind) {
            enterUltrasaving = true;
        }
        if (mModeConfigArray[mModeConfigIndex].mMode == PowerManagerEx.MODE_ULTRASAVING) {
            exitUltrasaving = true;
        }

        if (DEBUG) Slog.d(TAG, "showAlert(), modeSwitch: " + modeSwitch + ", enterUltrasaving: " + enterUltrasaving + ", exitUltrasaving: " + exitUltrasaving + ", mDontRemind: " + mDontRemind);

        if (!modeSwitch && !enterUltrasaving && !exitUltrasaving)
            return false;

        // decide title
        if (modeSwitch) {
            titleStr = mContext.getString(R.string.pwctl_switchmode_title);
        }
        if (enterUltrasaving || exitUltrasaving) {
            titleStr = mContext.getString(R.string.pwctl_ultrasaving_title);
        }

        // decide layout
        if (enterUltrasaving) {
            layoutId = R.layout.layout_pwctl_dialog;
        }
        // decide summary
        if (modeSwitch) {
            if (mModeConfigIndex == MODECONFIG_AUTOLOWPOWER)
                modeStr = mContext.getString(R.string.pwctl_autolowpower_saving);
            else
                modeStr = mContext.getString(R.string.pwctl_schedule_power_saving);

            if (enterUltrasaving) {
                summaryStr = mContext.getString(R.string.pwctl_switchmode_enter_ultrasaving_summary, modeStr);
            } else if (exitUltrasaving) {
                summaryStr = mContext.getString(R.string.pwctl_switchmode_exit_ultrasaving_summary, modeStr);
            } else {
                summaryStr = mContext.getString(R.string.pwctl_switchmode_summary, modeStr);
            }
        } else {
            if (enterUltrasaving) {
                summaryStr = mContext.getString(R.string.pwctl_enter_ultrasaving_summary);
            } else if (exitUltrasaving) {
                summaryStr = mContext.getString(R.string.pwctl_exit_ultrasaving_summary);
            }
        }

        if (summaryStr != null) {
            // for bug#771563 to exit when receive home key
            final CloseDialogReceiver closer = new CloseDialogReceiver(mContext);
            if (sConfirmDialog != null) {
                sConfirmDialog.dismiss();
            }
            if (layoutId != 0) {
            // with "dont remimd" checkbox
            //Note: Bug 805226 Dialog interval narrow -->BEG
            /*
                sConfirmDialog = new AlertDialog.Builder(mContext)
                    .setView(layoutId)
                    .setTitle(titleStr)
                    .setMessage(summaryStr)
                    .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // update settings focus
                            // postModeChangeBroadcast(mPowerSaveMode, mPrePowerSaveMode); // modify for bug#756870
                        }
                    })
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        CheckBox ckView = (CheckBox)sConfirmDialog.findViewById(R.id.ck_dontremind);
                        if (ckView != null) {
                            mDontRemind = ckView.isChecked();
                            if (mDontRemind) writeConfig();
                        }
                        //disable current auto-mode, update current powersaving mode
                        if ((mModeConfigIndex != MODECONFIG_USER)
                                && (mModeConfigArray[mModeConfigIndex].mMode != mode)) {
                            mModeConfigArray[mModeConfigIndex].mEnable = false;
                        }
                        mModeConfigArray[MODECONFIG_USER].mMode = mode;
                        updatePowerSaveMode();
                    }})
                    .create();
                    */
                UltraSavingAlertDialog.Builder builder = new UltraSavingAlertDialog.Builder(mContext);
                sConfirmDialog = builder.setTitle(titleStr)
                        .setMessage(summaryStr)
                        .setPositiveButton(R.string.yes, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                mDontRemind = builder.isChecked();
                                if (mDontRemind) writeConfig();
                                //disable current auto-mode, update current powersaving mode
                                if ((mModeConfigIndex != MODECONFIG_USER)
                                    && (mModeConfigArray[mModeConfigIndex].mMode != mode)) {
                                    mModeConfigArray[mModeConfigIndex].mEnable = false;
                                }
                                mModeConfigArray[MODECONFIG_USER].mMode = mode;
                                updatePowerSaveMode();
                                builder.dialog.dismiss();
                            }
                        })
                        .setNegativeButton(R.string.no, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // update settings focus
                                // postModeChangeBroadcast(mPowerSaveMode, mPrePowerSaveMode); // modify for bug#756870
                                builder.dialog.dismiss();
                            }
                        }).create();
            // Note: Bug 805226 Dialog interval narrow <--END
            } else {
            // without "dont remimd" checkbox
                sConfirmDialog = new AlertDialog.Builder(mContext)
                    .setTitle(titleStr)
                    .setMessage(summaryStr)
                    .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // update settings focus
                            // postModeChangeBroadcast(mPowerSaveMode, mPrePowerSaveMode); // modify for bug#756870
                        }
                    })
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //disable current auto-mode, update current powersaving mode
                        if ((mModeConfigIndex != MODECONFIG_USER)
                                && (mModeConfigArray[mModeConfigIndex].mMode != mode)) {
                            mModeConfigArray[mModeConfigIndex].mEnable = false;
                        }
                        mModeConfigArray[MODECONFIG_USER].mMode = mode;
                        updatePowerSaveMode();
                    }})
                    .create();
            }

            closer.dialog = sConfirmDialog;
            sConfirmDialog.setOnDismissListener(closer);
            closer.registerBroadcastReceiver();

            //SPRD: fixBug 802114: dialog can not be displayed.
            sConfirmDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            sConfirmDialog.show();
        }
        return true;
    }


    private static class CloseDialogReceiver extends BroadcastReceiver
            implements DialogInterface.OnDismissListener {
        private Context mContext;
        public Dialog dialog;
        private boolean registeredBroadcast;

        CloseDialogReceiver(Context context) {
            mContext = context;
            registeredBroadcast = false;
            // IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            // context.registerReceiver(this, filter);
        }

        // add for bug#775494
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


    private boolean updatePowerSaveModeInternal(int mode) {
        if (DEBUG) Slog.d(TAG, "updatePowerSaveModeInternal(" + mode + "), pre-mode: " + mPowerSaveMode);

        if (mInitFinished && mPowerSaveMode == mode) {
            if (DEBUG) Slog.d(TAG, "updatePowerSaveModeInternal(), no change, return true");
            return true; //just return
        }

        handlePowerSaveModeChanged(mode);

        writeConfig();
        return true;
    }

    //called by setting from powermanagerex
    private boolean setAppPowerSaveConfigInternal(String appName, AppPowerSaveConfig config) {
        AppPowerSaveConfig oldConfig = mAppConfigMap.get(appName);
        if (null == oldConfig) {
            oldConfig = new AppPowerSaveConfig();
        }
        if (DEBUG) Slog.d(TAG, "setAppPowerSaveConfigInternal(), appName: " + appName + ", new config: " + config);
        if (DEBUG) Slog.d(TAG, "old config: " +  oldConfig);

        for (int i = 0; i < AppPowerSaveConfig.ConfigType.TYPE_MAX.value; i++) {
            int oldValue = AppPowerSaveConfig.getConfigValue(oldConfig, i);
            int newValue = AppPowerSaveConfig.getConfigValue(config, i);

            if (newValue != oldValue) {
                onPowerSaveConfigChanged(i, appName, oldValue, newValue, false);
                for (int j = 0; j < mHelpers.size(); j++) {
                    PowerSaveHelper helper = mHelpers.get(j);
                    if (0 != (AppPowerSaveConfig.mMaskArray[i] & helper.mMask)) {
                        helper.onPowerSaveConfigChanged(i, appName, oldValue, newValue, false);
                    }
                }
            }
        }
        if (config.isReset()) {
            mAppConfigMap.remove(appName);
        } else {
            mAppConfigMap.put(appName, config);
        }
        AppPowerSaveConfig.writeConfig(mAppConfigMap);

        synchronized(mMsgLock) {
            mMsgLock.notify();
        }
        return true;
    }

    private boolean setAppPowerSaveConfigWithTypeInternal(String appName, int type, int value) {
        AppPowerSaveConfig oldConfig = mAppConfigMap.get(appName);
        if (null == oldConfig) {
            oldConfig = new AppPowerSaveConfig();
        }
        int oldValue = AppPowerSaveConfig.getConfigValue(oldConfig, type);

        if (DEBUG) Slog.d(TAG, "setAppPowerSaveConfigWithTypeInternal(), appName: " + appName + ", config type: " + AppPowerSaveConfig.ConfigType2Str(type) 
            + ", new value: " + AppPowerSaveConfig.ConfigValue2Str(value)
            + ", old value: " + AppPowerSaveConfig.ConfigValue2Str(oldValue));

        if (value != oldValue) {
            onPowerSaveConfigChanged(type, appName, oldValue, value, false);
            for (int j = 0; j < mHelpers.size(); j++) {
                PowerSaveHelper helper = mHelpers.get(j);
                if (0 != (AppPowerSaveConfig.mMaskArray[type] & helper.mMask)) {
                    helper.onPowerSaveConfigChanged(type, appName, oldValue, value, false);
                }
            }
        }

        AppPowerSaveConfig.setConfigWithType(oldConfig, type, value);

        if (DEBUG) Slog.d(TAG, "setAppPowerSaveConfigWithTypeInternal() config: " + oldConfig);

        if (oldConfig.isReset()) {
            if (DEBUG) Slog.d(TAG, "setAppPowerSaveConfigWithTypeInternal() remove old");
            mAppConfigMap.remove(appName);
        } else {
            if (DEBUG) Slog.d(TAG, "setAppPowerSaveConfigWithTypeInternal() put new");
            mAppConfigMap.put(appName, oldConfig);
        }
        AppPowerSaveConfig.writeConfig(mAppConfigMap);

        synchronized(mMsgLock) {
            mMsgLock.notify();
        }
        return true;
    }

    private boolean setAppPowerSaveConfigListWithTypeInternal(List<String> appList, int type, int value) {
        boolean bRet = true;
        for (int i=0; i<appList.size(); i++) {
            bRet |= setAppPowerSaveConfigWithTypeInternal(appList.get(i), type, value);
        }

        synchronized(mMsgLock) {
            mMsgLock.notify();
        }
        return bRet;
    }


    /**
     * To make sure the doze white list is sync with power save config list
     * Because some app may use doze api to add it self to doze white list
     * such as: com.iflytek.cmcc
     */
    private void syncPowerSaveConfigWithDozeWhitelist() {
        boolean needSave = false;
        for (int i=0;i<mPowerSaveWhitelistExceptIdleAppList.size();i++) {
            String appName = mPowerSaveWhitelistExceptIdleAppList.get(i);
            AppPowerSaveConfig config = mAppConfigMap.get(appName);

            // ignore system app
            boolean systemApp = false;
            try {
                ApplicationInfo ai = mContext.getPackageManager().getApplicationInfo(appName,
                        PackageManager.MATCH_UNINSTALLED_PACKAGES);
                if (ai == null || (ai.flags & (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP |ApplicationInfo.FLAG_SYSTEM)) != 0
                     || ai.flags == 0) {
                    systemApp = true;
                }
            } catch (Exception e) {
                if (DEBUG) Slog.d(TAG, "syncPowerSaveConfigWithDozeWhitelist() Exception: " + e);
                systemApp = true;
            }

            if (systemApp) continue;

            if (null == config) {
                config = new AppPowerSaveConfig();
            }

            int optimize = AppPowerSaveConfig.getConfigValue(config, AppPowerSaveConfig.ConfigType.TYPE_OPTIMIZE.value);
            if (optimize != 0) {
                needSave = true;
                AppPowerSaveConfig.setConfigWithType(config, AppPowerSaveConfig.ConfigType.TYPE_OPTIMIZE.value, 0);
                mAppConfigMap.put(appName, config);
                if (DEBUG) Slog.d(TAG, "App:" + appName + " PowerSaveConfig is not sync with doze whitelist");

            }
        }

        if (needSave) {
            if (DEBUG) Slog.d(TAG, "syncPowerSaveConfigWithDozeWhitelist: save config");
            AppPowerSaveConfig.writeConfig(mAppConfigMap);
        }
    }

    private void updateAppPowerConsumerType(String appName, int type, int mask) {
        AppPowerSaveConfig config = mAppConfigMap.get(appName);
        if (null == config) {
            config = new AppPowerSaveConfig();
            mAppConfigMap.put(appName, config);
        }
        int oType = AppPowerSaveConfig.getConfigValue(config, AppPowerSaveConfig.ConfigType.TYPE_POWERCONSUMERTYPE.value);

        if ((oType & mask) == type) return;
        AppPowerSaveConfig.setConfigWithType(config, AppPowerSaveConfig.ConfigType.TYPE_POWERCONSUMERTYPE.value, (oType|(type&mask)));
        AppPowerSaveConfig.writeConfig(mAppConfigMap);
    }


    private static final String CONFIG_FILENAME = "powercontroller.xml";

    private static final String XML_CONFIG_FILE_TAG = "powercontroller";
    //private static final String XML_CONFIG_POWERSAVE_MODE_TAG	= "powersave_mode";
    private static final String XML_CONFIG_DEF_MODE_TAG = "def_mode";
    private static final String XML_CONFIG_PRE_MODE_TAG = "pre_mode";
    private static final String XML_CONFIG_NOMORE_ALERT_TAG = "nomore_alert";
    private static final String XML_CONFIG_AUTOLOWPOWER_MODE_TAG = "autolowpower_mode";
    private static final String XML_CONFIG_AUTOLOWPOWER_ENABLE_TAG = "autolowpower_enable";
    private static final String XML_CONFIG_AUTOLOWPOWER_BATTVALUE_TAG = "autolowpower_battvalue";
    private static final String XML_CONFIG_AUTOLOWPOWER_EXITWITHPOWER_TAG = "autolowpower_exitwithpower";
    private static final String XML_CONFIG_SCHEDULE_MODE_TAG = "schedule_mode";
    private static final String XML_CONFIG_SCHEDULE_ENABLE_TAG = "schedule_enable";
    private static final String XML_CONFIG_SCHEDULE_START_HOUR_TAG = "schedule_start_hour";
    private static final String XML_CONFIG_SCHEDULE_START_MINUTE_TAG = "schedule_start_minute";
    private static final String XML_CONFIG_SCHEDULE_END_HOUR_TAG = "schedule_end_hour";
    private static final String XML_CONFIG_SCHEDULE_END_MINUTE_TAG = "schedule_end_minute";
    private static final String XML_CONFIG_APPLIST_IN_ULTRASAVE_TAG = "applist_in_ultrasave";
    private static final String XML_CONFIG_APPNAME_TAG = "appname";
    private static final String XML_CONFIG_DISABLED_LAUNCHER_TAG = "disabled_launcher";

    private static final String XML_CONFIG_IS_MOBILE_CONNECT_ENABLED_TAG = "is_mobile_connect_enabled";
    private static final String XML_CONFIG_IS_WIFI_ENABLED_TAG = "is_wifi_enabled";
    private static final String XML_CONFIG_IS_BT_ENABLED_TAG = "is_bt_enabled";
    private static final String XML_CONFIG_SCREENOFF_TIME_TAG = "screenoff_time";
    private static final String XML_CONFIG_LOCATION_STATE_TAG = "location_state";
    private static final String XML_CONFIG_POWERSAVE_EXITWITHCHARGING_TAG = "powersave_exitwithcharging";

    private static void writeItem(XmlSerializer serializer, String tag, String value) throws IOException {
        serializer.startTag(null, tag);
        if (value == null ) {
            serializer.text("null");
        } else {
            serializer.text(value);
        }
        serializer.endTag(null, tag);
    }

    private boolean writeConfig() {
        AtomicFile aFile = new AtomicFile(new File(new File(Environment.getDataDirectory(), "system"), CONFIG_FILENAME));
        FileOutputStream stream;
        try {
            stream = aFile.startWrite();
        } catch (IOException e) {
            Slog.e(TAG, "Failed to write state: " + e);
            return false;
        }

        if (DEBUG) Slog.d(TAG, "writeConfig: mPrePowerSaveMode: " + mPrePowerSaveMode);

        try {
            XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(stream, "utf-8");
            serializer.startDocument(null, true);
            serializer.startTag(null, XML_CONFIG_FILE_TAG);
            writeItem(serializer, XML_CONFIG_DEF_MODE_TAG, String.valueOf(mModeConfigArray[MODECONFIG_USER].mMode));
            writeItem(serializer, XML_CONFIG_PRE_MODE_TAG, String.valueOf(mPrePowerSaveMode));
            writeItem(serializer, XML_CONFIG_NOMORE_ALERT_TAG, String.valueOf(mDontRemind));
            writeItem(serializer, XML_CONFIG_AUTOLOWPOWER_ENABLE_TAG, String.valueOf(mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mEnable));
            writeItem(serializer, XML_CONFIG_AUTOLOWPOWER_BATTVALUE_TAG, String.valueOf(mLowBatteryWarningLevel));
            writeItem(serializer, XML_CONFIG_AUTOLOWPOWER_MODE_TAG, String.valueOf(mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mMode));
            writeItem(serializer, XML_CONFIG_AUTOLOWPOWER_EXITWITHPOWER_TAG, String.valueOf(mExitLowPower_WithPower));
            writeItem(serializer, XML_CONFIG_SCHEDULE_ENABLE_TAG, String.valueOf(mModeConfigArray[MODECONFIG_SCHEDULE].mEnable));
            writeItem(serializer, XML_CONFIG_SCHEDULE_START_HOUR_TAG, String.valueOf(mStartHour));
            writeItem(serializer, XML_CONFIG_SCHEDULE_START_MINUTE_TAG, String.valueOf(mStartMinute));
            writeItem(serializer, XML_CONFIG_SCHEDULE_END_HOUR_TAG, String.valueOf(mEndHour));
            writeItem(serializer, XML_CONFIG_SCHEDULE_END_MINUTE_TAG, String.valueOf(mEndMinute));
            writeItem(serializer, XML_CONFIG_SCHEDULE_MODE_TAG, String.valueOf(mModeConfigArray[MODECONFIG_SCHEDULE].mMode));
            writeItem(serializer, XML_CONFIG_IS_MOBILE_CONNECT_ENABLED_TAG, String.valueOf(mIsMobileEnabled));
            writeItem(serializer, XML_CONFIG_IS_WIFI_ENABLED_TAG, String.valueOf(mIsWifiEnabled));
            writeItem(serializer, XML_CONFIG_IS_BT_ENABLED_TAG, String.valueOf(mIsBTEnabled));
            writeItem(serializer, XML_CONFIG_LOCATION_STATE_TAG, String.valueOf(mLocationState));
            writeItem(serializer, XML_CONFIG_SCREENOFF_TIME_TAG, String.valueOf(mScreenOffTime));
            writeItem(serializer, XML_CONFIG_POWERSAVE_EXITWITHCHARGING_TAG, String.valueOf(mSetSmartSaveMode));

            if (mAppList_UltraSave.size() > 0) {
                serializer.startTag(null, XML_CONFIG_APPLIST_IN_ULTRASAVE_TAG);
                for (int i=0; i< mAppList_UltraSave.size(); i++) {
                    writeItem(serializer, XML_CONFIG_APPNAME_TAG, mAppList_UltraSave.get(i));
                }
                serializer.endTag(null, XML_CONFIG_APPLIST_IN_ULTRASAVE_TAG);
            }

            if (mDisabledLanucherAppList_UltraSave != null) {
                if (DEBUG) Slog.d(TAG, "writeConfig: mDisabledLanucherAppList_UltraSave: " + mDisabledLanucherAppList_UltraSave);
                writeItem(serializer, XML_CONFIG_DISABLED_LAUNCHER_TAG, mDisabledLanucherAppList_UltraSave);
            }

            serializer.endTag(null, XML_CONFIG_FILE_TAG);
            serializer.endDocument();
            aFile.finishWrite(stream);
        } catch (IOException e) {
            Slog.e(TAG, "Failed to write state, restoring backup."+"exp:"+"\n"+e);
            aFile.failWrite(stream);
            return false;
        }
        return true;
    }

    private boolean readConfig(){
        AtomicFile aFile = new AtomicFile(new File(new File(Environment.getDataDirectory(), "system"), CONFIG_FILENAME));
        InputStream stream = null;

        try {
            stream = aFile.openRead();
        } catch (FileNotFoundException exp){
            Slog.e(TAG, ">>>file not found,"+exp);
        }

        if (null == stream) {
            aFile = new AtomicFile(new File(new File(Environment.getRootDirectory(), "etc"), CONFIG_FILENAME));
            try {
                stream = aFile.openRead();
            } catch (FileNotFoundException exp){
                Slog.e(TAG, ">>>default file not found,"+exp);
                return false;
            }
        }

        try {
            XmlPullParser pullParser = Xml.newPullParser();
            pullParser.setInput(stream, "UTF-8");
            int event = pullParser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                switch (event) {
                    case XmlPullParser.START_DOCUMENT:
                        break;

                    case XmlPullParser.START_TAG:
                        if (XML_CONFIG_DEF_MODE_TAG.equals(pullParser.getName())) {
                            mModeConfigArray[MODECONFIG_USER].mMode = Integer.parseInt(pullParser.nextText());
                            // if the save mode is invalid, then reset to default
                            if (PowerManagerEx.MODE_INVALID == mModeConfigArray[MODECONFIG_USER].mMode)
                                mModeConfigArray[MODECONFIG_USER].mMode = PowerManagerEx.MODE_SMART;
                        } else if (XML_CONFIG_PRE_MODE_TAG.equals(pullParser.getName())) {
                            mPrePowerSaveMode = Integer.parseInt(pullParser.nextText());
                            // if the save mode is invalid, then reset to default
                            if (PowerManagerEx.MODE_INVALID == mPrePowerSaveMode)
                                mPrePowerSaveMode = PowerManagerEx.MODE_SMART;
                        } else if (XML_CONFIG_NOMORE_ALERT_TAG.equals(pullParser.getName())) {
                            mDontRemind = Boolean.parseBoolean(pullParser.nextText());
                        } else if (XML_CONFIG_AUTOLOWPOWER_MODE_TAG.equals(pullParser.getName())) {
                            mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mMode = Integer.parseInt(pullParser.nextText());
                        } else if (XML_CONFIG_AUTOLOWPOWER_ENABLE_TAG.equals(pullParser.getName())) {
                            mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mEnable = Boolean.parseBoolean(pullParser.nextText());
                        } else if (XML_CONFIG_AUTOLOWPOWER_BATTVALUE_TAG.equals(pullParser.getName())) {
                            mLowBatteryWarningLevel = Integer.parseInt(pullParser.nextText());
                        } else if (XML_CONFIG_AUTOLOWPOWER_EXITWITHPOWER_TAG.equals(pullParser.getName())) {
                            mExitLowPower_WithPower = Boolean.parseBoolean(pullParser.nextText());
                        }else if (XML_CONFIG_SCHEDULE_MODE_TAG.equals(pullParser.getName())) {
                            mModeConfigArray[MODECONFIG_SCHEDULE].mMode = Integer.parseInt(pullParser.nextText());
                        } else if (XML_CONFIG_SCHEDULE_ENABLE_TAG.equals(pullParser.getName())) {
                            mModeConfigArray[MODECONFIG_SCHEDULE].mEnable = Boolean.parseBoolean(pullParser.nextText());
                        } else if (XML_CONFIG_SCHEDULE_START_HOUR_TAG.equals(pullParser.getName())) {
                            mStartHour= Integer.parseInt(pullParser.nextText());
                        } else if (XML_CONFIG_SCHEDULE_START_MINUTE_TAG.equals(pullParser.getName())) {
                            mStartMinute = Integer.parseInt(pullParser.nextText());
                        } else if (XML_CONFIG_SCHEDULE_END_HOUR_TAG.equals(pullParser.getName())) {
                            mEndHour = Integer.parseInt(pullParser.nextText());
                        } else if (XML_CONFIG_SCHEDULE_END_MINUTE_TAG.equals(pullParser.getName())) {
                            mEndMinute = Integer.parseInt(pullParser.nextText());
                        } else if (XML_CONFIG_APPLIST_IN_ULTRASAVE_TAG.equals(pullParser.getName())) {
                            mAppList_UltraSave.clear();
                            mAppList_UltraSave_Internal.clear();
                        } else if (XML_CONFIG_APPNAME_TAG.equals(pullParser.getName())) {
                            String appName = pullParser.nextText();
                            mAppList_UltraSave.add(appName);
                            updateAppList_UltraSave_internal(appName, true);
                        } else if (XML_CONFIG_DISABLED_LAUNCHER_TAG.equals(pullParser.getName())) {
                            mDisabledLanucherAppList_UltraSave = pullParser.nextText();
                            if (DEBUG) Slog.d(TAG, "readConfig: mDisabledLanucherAppList_UltraSave: " + mDisabledLanucherAppList_UltraSave);
                        } else if (XML_CONFIG_IS_MOBILE_CONNECT_ENABLED_TAG.equals(pullParser.getName())) {
                            mIsMobileEnabled = Boolean.parseBoolean(pullParser.nextText());
                        } else if (XML_CONFIG_IS_WIFI_ENABLED_TAG.equals(pullParser.getName())) {
                            mIsWifiEnabled = Boolean.parseBoolean(pullParser.nextText());
                        } else if (XML_CONFIG_IS_BT_ENABLED_TAG.equals(pullParser.getName())) {
                            mIsBTEnabled = Boolean.parseBoolean(pullParser.nextText());
                        } else if (XML_CONFIG_LOCATION_STATE_TAG.equals(pullParser.getName())) {
                            mLocationState = Integer.parseInt(pullParser.nextText());
                        } else if (XML_CONFIG_SCREENOFF_TIME_TAG.equals(pullParser.getName())) {
                            mScreenOffTime = Integer.parseInt(pullParser.nextText());
                        } else if (XML_CONFIG_POWERSAVE_EXITWITHCHARGING_TAG.equals(pullParser.getName())) {
                           mSetSmartSaveMode = Boolean.parseBoolean(pullParser.nextText());
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        break;
                }
                event = pullParser.next();
            }
        } catch (IllegalStateException e) {
            Slog.e(TAG, "Failed parsing " + e);
            return false;
        } catch (NullPointerException e) {
            Slog.e(TAG, "Failed parsing " + e);
            return false;
        } catch (NumberFormatException e) {
            Slog.e(TAG, "Failed parsing " + e);
            return false;
        } catch (XmlPullParserException e) {
            Slog.e(TAG, "Failed parsing " + e);
            return false;
        } catch (IOException e) {
            Slog.e(TAG, "Failed parsing " + e);
            return false;
        } catch (IndexOutOfBoundsException e) {
            Slog.e(TAG, "Failed parsing " + e);
            return false;
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                Slog.e(TAG, "Fail to close stream " + e);
                return false;
            } catch (Exception e) {
                Slog.e(TAG, "exception at last,e: " + e);
                return false;
            }
        }
        return true;
    }

    private void exitPowerSaveMode(int oldMode) {
        switch (oldMode) {
            case PowerManagerEx.MODE_LOWPOWER:
            case PowerManagerEx.MODE_ULTRASAVING:
            case PowerManagerEx.MODE_INVALID: // default mode is MODE_INVALID, so it must be the first time coming here. We should do reset lowpower mode
                mPowerManager.setPowerSaveMode(false);
                exitForceIdleIfNeed();
                break;
            case PowerManagerEx.MODE_PERFORMANCE:
                break;
            case PowerManagerEx.MODE_SMART:
                 if (!mDozeEnabled) {
                    exitForceIdleIfNeed();
                 }
                break;
            case PowerManagerEx.MODE_POWERSAVING:
                break;
        }
    }

    private void enterPowerSaveMode(int newMode) {
        Slog.d(TAG, "enterPowerSaveMode(), " + newMode);
        // for bug#938252
        int preMode = mPowerSaveMode;

        if (mModeSwitch) {
            mModeSwitch = false;
        } else {
            mPrePowerSaveMode = mPowerSaveMode;
        }
        mPowerSaveMode = newMode;

        if (mPrePowerSaveMode ==  PowerManagerEx.MODE_ULTRASAVING
            && mPowerSaveMode == PowerManagerEx.MODE_ULTRASAVING) {
            Slog.d(TAG, "mPrePowerSaveMode and mPowerSaveMode are equal to PowerManagerEx.MODE_ULTRASAVING");
            mPrePowerSaveMode =PowerManagerEx.MODE_SMART;
        }

        notifyPowerModeListeners(newMode);

        //For Bug#859178 mobile connect is not disable when enter ultra saving from status bar start-->
        if (mPrePowerSaveMode != PowerManagerEx.MODE_INVALID) {
            updateByMode(newMode);
        }
        //<--end For Bug#859178 mobile connect is not disable when enter ultra saving from status bar

        // send broadcast
        postModeChangeBroadcast(newMode, preMode/*mPrePowerSaveMode*/);

        switch (newMode) {
            case PowerManagerEx.MODE_ULTRASAVING:
                try {
                    //ActivityManager.getService().resizeStack(ActivityManager.StackId.DOCKED_STACK_ID, null, true, true, false, -1);
                    ActivityManager.getService().dismissSplitScreenMode(false /* onTop */);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to resize stack: " + e);
                }
            case PowerManagerEx.MODE_LOWPOWER:
                mPowerManager.setPowerSaveMode(true);
                break;
            case PowerManagerEx.MODE_PERFORMANCE:
                break;
            case PowerManagerEx.MODE_SMART:
                break;
            case PowerManagerEx.MODE_POWERSAVING:
                break;
        }

        // if lowpower mode, aosp code will call powerHint() interface
        if (PowerManagerEx.MODE_LOWPOWER != mPowerSaveMode) {
            mLocalPowerManager.powerHint(getPowerHintMode(mPowerSaveMode),  1);
        }

    }

    private void postModeChangeBroadcast(int newMode, int oldMode) {
        if (DEBUG) Slog.d(TAG, "postModeChangeBroadcast(), newMode:" + newMode + ", oldMode:" + oldMode);
        Intent intent = new Intent(PowerManagerEx.ACTION_POWEREX_SAVE_MODE_CHANGED)
                .putExtra(PowerManagerEx.EXTRA_POWEREX_SAVE_MODE, newMode)
                .putExtra(PowerManagerEx.EXTRA_POWEREX_SAVE_PREMODE, oldMode)
                .setFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND
                        | Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(intent);
    }

    private void updateByMode(int mode) {
        if(DEBUG) Slog.d(TAG, "updateByMode(), newMode:" + mode);
        switch (mode) {
            case PowerManagerEx.MODE_LOWPOWER:
            case PowerManagerEx.MODE_ULTRASAVING:
                updateStates();
                break;
             case PowerManagerEx.MODE_SMART:
                restoreStates();
                break;
        }
    }

    //get current state and disable wifi mobile gps bt and flashlight
    private void updateStates() {
        ContentResolver resolver = mContext.getContentResolver();

        if (!mWaittingRestoreStates) {
            mScreenOffTime = Settings.System.getInt(resolver,
                    Settings.System.SCREEN_OFF_TIMEOUT, DEFAULT_SCREEN_OFF_TIMEOUT);

            mLocationState = Settings.Secure.getInt(resolver,
                    Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);

           getBluetoothAdapter();
           mIsBTEnabled = mBluetoothAdapter != null ? mBluetoothAdapter.isEnabled() : false;

           getWifiManager();
           mIsWifiEnabled = mWifiManager != null ? mWifiManager.isWifiEnabled() : false;

           getTelephonyManager();
           mIsMobileEnabled = mTelephonyManager != null ? mTelephonyManager.getDataEnabled() : false;
           mWaittingRestoreStates = true;
        }

//        mAirplaneMode = Settings.Global.getInt(resolver,
//                Settings.Global.AIRPLANE_MODE_ON, 0);

         if(DEBUG) Slog.d(TAG, "updateStates(), mIsMobileEnabled: " + mIsMobileEnabled + ", mIsWifiEnabled: " + mIsWifiEnabled
                + ", mIsBTEnabled: " + mIsBTEnabled + ", mScreenOffTime: " + mScreenOffTime
                + ", mLocationState: " + mLocationState + ", mWaittingRestoreStates: " + mWaittingRestoreStates);

        setStandbyScreen(DEFAULT_SCREEN_OFF_TIMEOUT);
        setWifiEnabled(false);
        setMobileDataEnabled(false);
        updateGPS(Settings.Secure.LOCATION_MODE_OFF);
        setBTEnabled(false);
        setWifiHotspotState(false);
        setFlashlight(false);
    }

    //restore wifi mobile bt gps state if needed
    private void restoreStates() {
        ContentResolver resolver = mContext.getContentResolver();
        int curScreenOffTime = Settings.System.getInt(resolver,
                Settings.System.SCREEN_OFF_TIMEOUT, DEFAULT_SCREEN_OFF_TIMEOUT);

        if (curScreenOffTime == DEFAULT_SCREEN_OFF_TIMEOUT) setStandbyScreen(mScreenOffTime);

        if (mIsMobileEnabled) setMobileDataEnabled(true);

        if (mIsWifiEnabled) setWifiEnabled(true);

        if (mLocationState != Settings.Secure.LOCATION_MODE_OFF) updateGPS(mLocationState);

        if (mIsBTEnabled) setBTEnabled(true);

        if (mWaittingRestoreStates) mWaittingRestoreStates = false;

        if (DEBUG) Slog.d(TAG, "restoreStates(), mIsMobileEnabled: " + mIsMobileEnabled + ", mIsWifiEnabled: " + mIsWifiEnabled
                + ", mIsBTEnabled: " + mIsBTEnabled + ", mScreenOffTime: " + mScreenOffTime + ", mLocationState: " + mLocationState
                + ", curScreenOffTime: " + curScreenOffTime + ", mWaittingRestoreStates: " + mWaittingRestoreStates);
    }

    private void setStandbyScreen(int timeout) {
        final ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver, Settings.System.SCREEN_OFF_TIMEOUT,
                timeout, UserHandle.USER_CURRENT);
    }

    private void setWifiEnabled(boolean enabled) {
        getWifiManager();
        mWifiManager.setWifiEnabled(enabled);
    }

    private void setMobileDataEnabled(boolean enabled) {
        getTelephonyManager();
        mTelephonyManager.setDataEnabled(enabled);
    }

    private void updateGPS(int mode) {
        final ContentResolver resolver = mContext.getContentResolver();
        Settings.Secure.putIntForUser(resolver, Settings.Secure.LOCATION_MODE,
                mode, UserHandle.USER_CURRENT);
    }

    private void setBTEnabled(boolean enabled) {
        getBluetoothAdapter();
        if (enabled) {
            mBluetoothAdapter.enable();
        } else {
            mBluetoothAdapter.disable();
        }
    }

    private void setWifiHotspotState(boolean state) {
        getWifiManager();
        if (state) {
            mWifiManager.startSoftAp(null);
        } else {
            mWifiManager.stopSoftAp();
        }
        return;
    }

    private void setFlashlight(boolean enabled) {
        final CameraManager cameraManager = (CameraManager) mContext.getSystemService(
                Context.CAMERA_SERVICE);

        String cameraId = null;
        try {
            String[] ids = cameraManager.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
                if (flashAvailable != null && flashAvailable
                        && lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }
            if (cameraId == null) return;
            cameraManager.setTorchMode(cameraId, enabled);
        } catch (CameraAccessException e) {
            Slog.e(TAG, "Couldn't set torch mode", e);
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "Couldn't set torch mode", e);
        }
     }

    private int getPowerHintMode(int powerSaveMode) {
        switch (powerSaveMode) {
            case PowerManagerEx.MODE_LOWPOWER:
                return PowerHintVendorSprd.POWER_HINT_VENDOR_MODE_LOW_POWER;
            case PowerManagerEx.MODE_PERFORMANCE:
                return PowerHintVendorSprd.POWER_HINT_VENDOR_MODE_PERFORMANCE;
            case PowerManagerEx.MODE_SMART:
                return PowerHintVendorSprd.POWER_HINT_VENDOR_MODE_NORMAL;
            case PowerManagerEx.MODE_POWERSAVING:
                return PowerHintVendorSprd.POWER_HINT_VENDOR_MODE_POWER_SAVE;
            case PowerManagerEx.MODE_ULTRASAVING:
                return PowerHintVendorSprd.POWER_HINT_VENDOR_MODE_ULTRA_POWER_SAVE;
        }
        return PowerHintVendorSprd.POWER_HINT_VENDOR_MODE_NORMAL;
    }

    // handle the power save mode changed
    private void handlePowerSaveModeChanged(int newMode) {

        if (mInitFinished && mPowerSaveMode == newMode) return;

        // notify helpers power save mode changing
        for (int i = 0; i < mHelpers.size(); i++) {
            PowerSaveHelper helper = mHelpers.get(i);
            helper.onPowerSaveModeChanging(newMode);
        }

        exitPowerSaveMode(mPowerSaveMode);
        enterPowerSaveMode(newMode);
        if (mPowerSaveMode != newMode) {
            Slog.e(TAG, "Something error!! mPowerSaveMode change fail!!old mode:" + mPowerSaveMode
                + " new mode:" + newMode);
        }

        // notify helpers power save mode changed
        for (int i = 0; i < mHelpers.size(); i++) {
            PowerSaveHelper helper = mHelpers.get(i);
            helper.onPowerSaveModeChanged(newMode);
        }

        //switch between ultra-saving mode and other mode
        if ((mPrePowerSaveMode ==  PowerManagerEx.MODE_ULTRASAVING || mPowerSaveMode == PowerManagerEx.MODE_ULTRASAVING)
            && (mPrePowerSaveMode !=  mPowerSaveMode)){
            mLastTimeOfUltraSavingSwitch = SystemClock.elapsedRealtime();
        }
    }

    // check current power save mode to do something
    // when enter/exit standby
    private void checkPowerSaveMode(boolean standby) {
        switch (mPowerSaveMode) {
            case PowerManagerEx.MODE_LOWPOWER:
            case PowerManagerEx.MODE_ULTRASAVING:
                if (standby) {
                    long now = SystemClock.elapsedRealtime();
                    long standbyDuration = now - mStandbyStartTime;

                    // For bug#761647 pin lock is work in low-power/ultra saving mode
                    if (mStandbyStartTime > 0 && standbyDuration > DEFAULT_FORCE_DOZE_THRESHOLD) {
                        // force enter doze
                        forceIdleIfNeed();
                    } else {
                        msgHandler.sendMessageDelayed(msgHandler.obtainMessage(MSG_CHECK), DEFAULT_FORCE_DOZE_THRESHOLD);
                    }
                } else {
                    // exit force doze
                    exitForceIdleIfNeed();
                }
                break;
            case PowerManagerEx.MODE_PERFORMANCE:
                break;
            case PowerManagerEx.MODE_SMART:
                 if (!standby && !mDozeEnabled) {
                    exitForceIdleIfNeed();
                 }
                 break;
            case PowerManagerEx.MODE_POWERSAVING:
                break;
        }
    }

    // increase the count of app that is doing download
    // if count > 0, then mDownloading is set to be true
    private void incAppDownloadCount() {
        mAppDownloadCount++;
        if (mAppDownloadCount > 0) {
            mDownloading = true;
            mLastNotDownloadTimeStamp = 0;
        }
    }

    // decrease the count of app that is doing download
    // if count is 0, then mDownloading is set to be false
    private void decAppDownloadCount() {
        if (mAppDownloadCount > 0) mAppDownloadCount--;
        if (mAppDownloadCount <= 0) {
            mDownloading = false;
            mLastNotDownloadTimeStamp = SystemClock.elapsedRealtime();
        }
    }

    private boolean needCheckNetworkConnection(long now) {
        long standbyDuration = now - mStandbyStartTime;
        if (standbyDuration > DEFAULT_DISCONNECT_NETWORK_THRESHOLD
            && mStandbyStartTime > 0
            && mLastNotDownloadTimeStamp >= mStandbyStartTime
            && mDownloadChangeCount <=0) {
            if (DEBUG) Slog.d(TAG, "needCheckNetworkConnection: mDownloadChangeCount: " + mDownloadChangeCount);
            return true;
        }

        // for bug#776206
        if (PowerManagerEx.MODE_ULTRASAVING == mPowerSaveMode) {
            return true;
        }

        return false;
    }

    private void closeNetworkConnection() {
        if (mMobileConnected) {
            // disable mobile data
            getTelephonyManager();
            if (mTelephonyManager != null)
                mTelephonyManager.setDataEnabled(SubscriptionManager.getDefaultDataSubscriptionId(), false);
            mNeedReConnectMobile = true;
            if (DEBUG) Slog.d(TAG, "Close Data Connection in Power Mode:" + mPowerSaveMode);
        }
        if (mWifiConnected) {
            // disable wifi connection
            getWifiManager();
            if(mWifiManager != null) mWifiManager.setWifiEnabled(false);
            mNeedReConnectWifi = true;
            if (DEBUG) Slog.d(TAG, "Close Wifi Connection in Power Mode:" + mPowerSaveMode);
        }
    }

    private void reopenNetworkConnection() {
        if (mNeedReConnectMobile) {
            // reconnect Mobile connection
            getTelephonyManager();
            if (mTelephonyManager != null)
                mTelephonyManager.setDataEnabled(SubscriptionManager.getDefaultDataSubscriptionId(), true);
            if (DEBUG) Slog.d(TAG, "Re-Open Data Connection in Power Mode:" + mPowerSaveMode);
        }

        if (mNeedReConnectWifi) {
            // reconnect wifi
            // disable wifi connection
            getWifiManager();
            if(mWifiManager != null) mWifiManager.setWifiEnabled(true);
            if (DEBUG) Slog.d(TAG, "Re-Open Wifi Connection in Power Mode:" + mPowerSaveMode);
        }
        mNeedReConnectMobile = false;
        mNeedReConnectWifi = false;
    }

    // check if current time is in the constraint time span: startHour ~ endHour
    private boolean inConstraintTimeSpan(int startHour, int endHour) {
        long now = System.currentTimeMillis(); //wall time
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        if (DEBUG) Slog.d(TAG, "inConstraintTimeSpan() Current hour:" + hour + ", minute:" + minute);
        if (hour >= startHour
            && hour < endHour) {
            return true;
        }
        return false;
    }

    // check if current time is in the network constraint time span: 00:00 ~ 06:00
    private boolean inNetworkConstraintTimeSpan() {
        long now = System.currentTimeMillis(); //wall time
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        if (DEBUG) Slog.d(TAG, "inNetworkConstraintTimeSpan() Current hour:" + hour + ", minute:" + minute);
        if (hour >= NETWORK_CONSTRAINT_START_TIME_HOUR
            && hour < NETWORK_CONSTRAINT_END_TIME_HOUR) {
            return true;
        }
        return false;
    }

    // check the network connection
    // if network is connected when enter standby, remeber it.
    // 1. if in LOWPOWER/POWERSAVING
    //     --> if (!mDownloading && standbyDuration > 30min) then disconnect the network
    // 2. if in  ULTRASAVING mode,
    //     --> if (enter standby) then disconnect the network
    // 3. for SMART mode
    //     --> if in timespan 00:00 ~ 6:00 && !mDownloading then disconnect the network
    // 4. if network is disconnectd, then when exit standby, it will be auto connect again
    private void checkNetworkConnection(boolean standby) {
        if (DEBUG) Slog.d(TAG, "checkNetworkConnection: standby:" + standby
            + " mMobileConnected:" + mMobileConnected + " mWifiConnected:" + mWifiConnected
            + " mPowerSaveMode:" + mPowerSaveMode
            + " mDownloading:" + mDownloading);

        // in standby state
        if (standby) {

            if (mMobileConnected || mWifiConnected) {

                if (mPowerSaveMode == PowerManagerEx.MODE_LOWPOWER
                    || mPowerSaveMode == PowerManagerEx.MODE_POWERSAVING
                    ) {
                    long now = SystemClock.elapsedRealtime();
                    long standbyDuration = now - mStandbyStartTime;
                    long notDownloadDuration = now - mLastNotDownloadTimeStamp;
                    if (!mDownloading && standbyDuration > DEFAULT_DISCONNECT_NETWORK_THRESHOLD
                        && mStandbyStartTime > 0
                        && notDownloadDuration > DEFAULT_NOTDOWNLOADING_THRESHOLD
                        && mLastNotDownloadTimeStamp > 0) {
                        closeNetworkConnection();
                    }
                } else if (mPowerSaveMode == PowerManagerEx.MODE_ULTRASAVING) {
                    // for ULTRASAVING MODE, when enter standby, close network connection immediatly
                    closeNetworkConnection();
                } else if (mPowerSaveMode == PowerManagerEx.MODE_SMART) {
                    if (mEnableNetworkDisconnectInSmartMode && !mDownloading && inNetworkConstraintTimeSpan()) {
                        // close network during early morning 00:00 ~ 00:06
                        closeNetworkConnection();
                    }
                }
            }else {
                if (mPowerSaveMode == PowerManagerEx.MODE_SMART) {
                    if (mEnableNetworkDisconnectInSmartMode && !inNetworkConstraintTimeSpan()) {
                        // reopen network if out of network constraint time span
                        reopenNetworkConnection();
                    }
                }
            }
        } else { // exit standby
            reopenNetworkConnection();
        }
    }

    private void getTelephonyManager() {
        if (mTelephonyManager == null) {
            mTelephonyManager = TelephonyManager.from(mContext);
        }
    }

    private void getWifiManager() {
        if (mWifiManager == null) {
            mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        }
    }

    private void getBluetoothAdapter() {
        if (mBluetoothAdapter == null) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
    }


    private void forceIdleIfNeed() {
        if (!mForceIdle) {
            if (mLocalDeviceIdleController != null)
                mLocalDeviceIdleController.forceDoze(true, true);
            mForceIdle = true;
        }
    }

    private void exitForceIdleIfNeed() {
        if (mForceIdle) {
            if (mLocalDeviceIdleController != null)
                mLocalDeviceIdleController.forceDoze(false, true);
            mForceIdle = false;
        }
    }

    private void checkDozeState() {
        // For smart mode:
        // if doze is disabled, then check if in time span 00:00 ~ 06:00
        // if so, force in doze state.
        if (PowerManagerEx.MODE_SMART == mPowerSaveMode) {
            long now = SystemClock.elapsedRealtime();
            long standbyDuration = now - mStandbyStartTime;

            if (!mDozeEnabled && standbyDuration > DEFAULT_DISCONNECT_NETWORK_THRESHOLD
                && mStandbyStartTime > 0
                && inConstraintTimeSpan(0, 6)) {
                forceIdleIfNeed();
            } else if (!mDozeEnabled && !inConstraintTimeSpan(0, 6)) {
                exitForceIdleIfNeed();
            }
        } else if (PowerManagerEx.MODE_LOWPOWER == mPowerSaveMode
            || PowerManagerEx.MODE_ULTRASAVING == mPowerSaveMode) {

            if (!mForceIdle) {
                long now = SystemClock.elapsedRealtime();
                long standbyDuration = now - mStandbyStartTime;
                // For bug#761647 pin lock is work in low-power/ultra saving mode
                if (mStandbyStartTime > 0 && standbyDuration > DEFAULT_FORCE_DOZE_THRESHOLD) {
                    // force enter doze
                    forceIdleIfNeed();
                }
            }
        }
    }


    // register system touch event listener
    private void registerTouchEventListener() {
        mSystemTouchEventListener = new SystemTouchEventListener(mContext,
                new SystemTouchEventListener.Callbacks() {
                    @Override
                    public void onDown() {
                    }
                    @Override
                    public void onUp() {
                        mLastTouchEventTimeStamp = SystemClock.elapsedRealtime();
                        if (DEBUG_LOG) Slog.d(TAG, "TouchEvent Up: mLastTouchEventTimeStamp:" + mLastTouchEventTimeStamp);
                    }
                });
        if (mWindowManagerFuncs != null)
            mWindowManagerFuncs.registerPointerEventListener(mSystemTouchEventListener);
    }

    //
    //  ----------------- interface to notify helpers ----------------------
    //
    private void updateAppEvaluatedTimeStamp(AppState appState) {
        for (int i = 0; i < mHelpers.size(); i++) {
            PowerSaveHelper helper = mHelpers.get(i);
            helper.updateAppEvaluatedTimeStamp(appState);
        }
    }

    // notify helpers to handle the app state changed
    private void updateAppStateChanged(AppState appState, int stateChange) {
        for (int i = 0; i < mHelpers.size(); i++) {
            PowerSaveHelper helper = mHelpers.get(i);
            helper.updateAppStateChanged(appState, stateChange);
        }
    }

    // notify helpers to check the app info
    // if this app needs to be constraint, return true
    // else return false;
    private boolean checkAppStateInfo(AppState appState, final long nowELAPSED) {
        boolean bChanged = false;
        // check app state info
        for (int i = 0; i < mHelpers.size(); i++) {
            PowerSaveHelper helper = mHelpers.get(i);
            if (helper.checkAppStateInfo(appState, nowELAPSED)) {
                bChanged = true;
            }
        }

        return bChanged;
    }

    // notify helpers to note device state changed
    // bStandby: if device enter standby state, true for standby, false for exit standby
    // nowELAPSED: the elapsed time stamp, will used wehn standby is true,
    //                    if standby is false, don't care this nowELAPSED
    private void noteDeviceStateChanged(boolean bStandby, final long nowELAPSED) {
        for (int i = 0;i < mHelpers.size(); i++) {
            PowerSaveHelper helper = mHelpers.get(i);
            helper.noteDeviceStateChanged(bStandby, nowELAPSED);
        }
    }

    // notify helpers to clear constraint
    private void clearConstrain(AppState appState) {
        for (int i = 0; i < mHelpers.size(); i++) {
            mHelpers.get(i).clearConstrain(appState);
        }
    }

    //
    //  ----------------- interface to notify helpers  END----------------------
    //

    private boolean getDeBugPowerControllerLog() {
        String mValue = SystemProperties.get("persist.sys.power.fw.debug");
        StringBuilder mStringBuilder = new StringBuilder(mValue);
        String mStringBuilderNew = mStringBuilder.toString();
        if (mStringBuilderNew.contains("controller")) {
            return true;
        }
        return false;
    }

    // add for bug#947741
    private void checkBatteryWarningLevel() {
        final ContentResolver resolver = mContext.getContentResolver();
        int defWarnLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryWarningLevel);
        int curentLowBatteryWarningLevel = Settings.Global.getInt(resolver,
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, defWarnLevel);

        if (curentLowBatteryWarningLevel == 0) {
            curentLowBatteryWarningLevel = defWarnLevel;
        }

        if (mLowBatteryWarningLevel != curentLowBatteryWarningLevel
            && mLowBatteryWarningLevel > 0) {
            if (DEBUG) Slog.d(TAG, "mLowBatteryWarningLevel is != value from LOW_POWER_MODE_TRIGGER_LEVEL. Set it:" + mLowBatteryWarningLevel);
            Settings.Global.putInt(resolver, Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, mLowBatteryWarningLevel);
        }
    }


    private void updateBatteryLevelLow(boolean force) {
        int battLevel = mBatteryManagerInternal.getBatteryLevel();

        if (DEBUG) Slog.d(TAG, "updateBatteryLevelLow() E, force: " + force + ", battLevel: " + battLevel);
        if (DEBUG) Slog.d(TAG, "mBatteryLevelLow: " + mBatteryLevelLow);
        if (!mBatteryLevelLow) {
            // Should we now switch in to low battery mode?
            if (battLevel <= mLowBatteryWarningLevel) {
                mBatteryLevelLow = true;
            }
        } else {
            // Should we now switch out of low battery mode?
            if (battLevel >= mLowBatteryWarningLevel + LOWBATTERY_CLOSEWARNING_BUMP)  {
                if (DEBUG) Slog.d(TAG, "way 1, beyond close warning level:" + (mLowBatteryWarningLevel + LOWBATTERY_CLOSEWARNING_BUMP) + ", false");
                mBatteryLevelLow = false;
            } else if (force && battLevel >= mLowBatteryWarningLevel) {
                // If being forced, the previous state doesn't matter, we will just
                // absolutely check to see if we are now above the warning level.
                if (DEBUG) Slog.d(TAG, "way 2, force");
                mBatteryLevelLow = false;
            }
        }
        if (DEBUG) Slog.d(TAG, "updateBatteryLevelLow() X, mBatteryLevelLow: " + mBatteryLevelLow);
    }

    private long delayCheckPowerSaveMode() {

        if (mSetSmartSaveMode && mLastTimeOfUltraSavingSwitch > 0) {
            long now = SystemClock.elapsedRealtime();
            // >= 1s from last switching
            if (now - mLastTimeOfUltraSavingSwitch < DELAY_FOR_CONTINUE_ULTRASAVING_SWITCH) {

                if (DEBUG) Slog.d(TAG, "delayCheckPowerSaveMode: last switch:" + mLastTimeOfUltraSavingSwitch + " now:" + now);
                return (DELAY_FOR_CONTINUE_ULTRASAVING_SWITCH + mLastTimeOfUltraSavingSwitch - now);
            }
        }

        return 0;
    }

    void onUserSwitched(int userId) {
        mCurrentUserId = userId;
        updatePowerSaveMode();
    }

    private void checkSystemUpTime() {
        if (mCharging || mScreenOn) {
            mLastSystemUpTimeStamp = 0;
            mNextCheckSystemUpTimeStamp = 0;
            mLastCheckTimeStampUptime = 0;
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (mNextCheckSystemUpTimeStamp > now)
            return;

        long nowUptime = SystemClock.uptimeMillis();
        if (DEBUG_LOG) Slog.d(TAG, "checkSystemUpTime : mLastSystemUpTimeStamp:" + mLastSystemUpTimeStamp
            + " now:" + now + " nowUptime:" + nowUptime);

        if (mLastSystemUpTimeStamp == 0) {
            mLastSystemUpTimeStamp = now;
        } else if (mNextCheckSystemUpTimeStamp > 0
            && mLastCheckTimeStampUptime > 0) {
            long upDuration = nowUptime - mLastCheckTimeStampUptime;
            long totalDuration = now-mNextCheckSystemUpTimeStamp + SYSTEM_UP_CHECK_INTERVAL;
            if ((totalDuration-upDuration) > 2*SYSTEM_UP_CHECK_INTERVAL) {
                 if (DEBUG) Slog.d(TAG, "system has sleep for : " + (totalDuration-upDuration));
                 mLastSystemUpTimeStamp = now;
            }
        }

        mLastCheckTimeStampUptime = nowUptime;
        mNextCheckSystemUpTimeStamp = now + SYSTEM_UP_CHECK_INTERVAL;

        msgHandler.removeMessages(MSG_CHECK_SYSTEM_UP);
        msgHandler.sendMessageDelayed(msgHandler.obtainMessage(MSG_CHECK_SYSTEM_UP), SYSTEM_UP_CHECK_INTERVAL);
    }


    private void checkBackgroundApp() {
        if (!mBackgroundCheckEnabled && !mAllTimeCheckEnabled) return;

        long now = SystemClock.elapsedRealtime();

        try {
            final List<UserInfo> users = mUserManager.getUsers();
            for (int ui = users.size() - 1; ui >= 0; ui--) {
                UserInfo user = users.get(ui);
                if (DEBUG) Slog.d(TAG, "- checkBackgroundApp() for user: " + user.id);

                final ArrayMap<String, AppState> appStateInfoList = mAppStateInfoCollector.getAppStateInfoList(user.id);
                for (int i=0;i<appStateInfoList.size();i++) {
                    AppState appState = appStateInfoList.valueAt(i);
                    if (appState == null) continue;

                    if (mAllTimeCheckEnabled) {
                        if (isAppDoingDownloadInternal(appState)) {
                            if (DEBUG) Slog.d(TAG, "- checkBackgroundApp() : " + appState.mPackageName + " is doing download!");
                        }
                        if (isPlayingMusicInternal(appState)) {
                            if (DEBUG) Slog.d(TAG, "- checkBackgroundApp() : " + appState.mPackageName + " is playing music!");
                        }
                        for (int j = 0; j < mHelpers.size(); j++) {
                            PowerSaveHelper helper = mHelpers.get(j);
                            helper.checkAppRequirement(appState);
                        }
                    }

                    //check if can be stop a app
                    if (mBackgroundCheckEnabled) mBackgroundCleanHelper.checkBackgroundApp(appState, now);
                }
            }
        } catch (Exception e) {
        }

    }

    private void handleNotificationUpdate(String appName, int uid) {
        mAppStateInfoCollector.updateNotification(appName, uid);
        mBackgroundCleanHelper.handleNotificationUpdate(appName, uid);
   }

    private final NotificationListenerService mNotificationListener = new NotificationListenerService() {
        @Override
        public void onNotificationPosted(StatusBarNotification sbn, RankingMap ranking) {
            String packName = sbn.getPackageName();
            int uid = sbn.getUid();
            if (DEBUG_MORE) Slog.d(TAG, "onNotificationPosted for " +  sbn.getPackageName()
                + " Notification:" + sbn.getNotification());
            msgHandler.sendMessage(msgHandler.obtainMessage(MSG_NOTIFICATION_UPDATE, uid, 0, packName));
        }

        @Override
        public void onNotificationRemoved(StatusBarNotification notification, RankingMap ranking) {
            String packName = notification.getPackageName();
            int uid = notification.getUid();
            if (DEBUG_MORE) Slog.d(TAG, "onNotificationRemoved for " +  notification.getPackageName()
                + " Notification:" + notification.getNotification());

            msgHandler.sendMessage(msgHandler.obtainMessage(MSG_NOTIFICATION_UPDATE, uid, 0, packName));
        }
    };

    private void registerAppTransitionListener() {
        if (mWindowManagerInternal == null) return;
        mWindowManagerInternal.registerAppTransitionListener(new AppTransitionListener() {
            @Override
            public void onAppTransitionPendingLocked() {
            }

            @Override
            public void onAppTransitionCancelledLocked(int transit) {
            }

            @Override
            public int onAppTransitionStartingLocked(int transit, IBinder openToken,
                    IBinder closeToken, long duration, long statusBarAnimationStartTime,
                    long statusBarAnimationDuration) {
                msgHandler.post(new Runnable() {
                    public void run() {
                        try {
                            String  openApp = mActivityManager.getPackageForToken(openToken);
                            String  closeApp = mActivityManager.getPackageForToken(closeToken);
                            handleAppTransitionStarting(openApp, closeApp);
                        } catch (Exception e) {}
                    }
                });

                return 0;
            }

            @Override
            public void onAppTransitionFinishedLocked(IBinder token) {
                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_APP_TRANSITION));
            }
        });
    }

    private void handleAppTransition() {
        if (DEBUG) Slog.d(TAG, "- handleAppTransition()");

        ArrayList<String> visibleAppList = new ArrayList<>();

        try {

            List<IBinder> activityTokens = null;

            // Let's get top activities from all visible stacks
            activityTokens = mActivityManagerInternal.getTopVisibleActivities();
            final int count = activityTokens.size();

            for (int i = 0; i < count; i++) {
                IBinder topActivity =  activityTokens.get(i);
                try {
                    String  packageName = mActivityManager.getPackageForToken(topActivity);
                    if (packageName != null) {
                        visibleAppList.add(packageName);
                    }
                } catch (RemoteException e) {
                }
            }

            mAppStateInfoCollector.handleVisibleAppChanged(visibleAppList, mCurrentUserId);
            if (mBackgroundCleanHelper != null)
                mBackgroundCleanHelper.handleVisibleAppChanged(visibleAppList);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void handleAppTransitionStarting(String openApp, String closeApp) {
        if (DEBUG) Slog.d(TAG, "- handleAppTransitionStarting(): openApp:" + openApp + " closeApp:" + closeApp);

        try {
            mBackgroundCleanHelper.handleAppTransitionStarting(openApp, closeApp);
        } catch (Exception e) {
        }

        if (!mBackgroundCheckEnabled) return;

        try {
            final List<UserInfo> users = mUserManager.getUsers();
            for (int ui = users.size() - 1; ui >= 0; ui--) {
                UserInfo user = users.get(ui);
                if (DEBUG_MORE) Slog.d(TAG, "- handleAppTransitionStarting() for user: " + user.id);

                final ArrayMap<String, AppState> appStateInfoList = mAppStateInfoCollector.getAppStateInfoList(user.id);
                for (int i=0;i<appStateInfoList.size();i++) {
                    AppState appState = appStateInfoList.valueAt(i);

                    mBackgroundCleanHelper.checkBackgroundApp(appState, openApp, closeApp);
                }
            }
        } catch (Exception e) {
        }

    }

    private void registerAppUsageStateListener() {
        AppStateTracker appStateTracker = LocalServices.getService(AppStateTracker.class);
        if (appStateTracker == null) return;
        appStateTracker.addListener(new AppStateTracker.Listener() {
            @Override
            public void onAppUsageStateChanged(String packageName, int uid, int state) {
                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_APP_USAGE_STATE_CHANGED, uid, state, packageName));
            }
        });
    }

    private void handleAppUsageStateChanged(String packName, int uid, int state) {
        for (int i = 0; i < mHelpers.size(); i++) {
            PowerSaveHelper helper = mHelpers.get(i);
            helper.onAppUsageStateChanged(packName, uid, state);
        }
    }


    private void createNotificationChannel() {
        try {
            final NotificationManager nm = mContext.getSystemService(NotificationManager.class);

            NotificationChannel notificationChannel= new NotificationChannel(
                            NOTIFICATION_CHANNEL,
                            NOTIFICATION_CHANNEL,
                            NotificationManager.IMPORTANCE_LOW);

            nm.createNotificationChannel(notificationChannel);
        } catch (Exception e) {}
    }

    private void showToastIfNeed(int preModeConfigIndex) {
        String text = null;

        // if new mode is the same, just return
        try {
            if (mModeConfigArray[mModeConfigIndex].mMode == mModeConfigArray[preModeConfigIndex].mMode)
                return;
        } catch (Exception e) {
        }

        // auto exit from auto power mode
        if (preModeConfigIndex != MODECONFIG_USER
            && mModeConfigIndex != preModeConfigIndex) {
            if ( preModeConfigIndex == MODECONFIG_AUTOLOWPOWER
                && mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mEnable) {
                text = mContext.getString(R.string.exit_autoLowPowerMode);
            } else if (preModeConfigIndex == MODECONFIG_SCHEDULE
                && mModeConfigArray[MODECONFIG_SCHEDULE].mEnable) {
                text = mContext.getString(R.string.exit_autoSchedulePowerMode);
            }
        }

        // auto enter auto power mode
        if (mModeConfigIndex != MODECONFIG_USER
            && mModeConfigIndex != preModeConfigIndex) {
            if ( mModeConfigIndex == MODECONFIG_AUTOLOWPOWER
                && mModeConfigArray[MODECONFIG_AUTOLOWPOWER].mEnable) {
                text = mContext.getString(R.string.enter_autoLowPowerMode);
            } else if (mModeConfigIndex == MODECONFIG_SCHEDULE
                && mModeConfigArray[MODECONFIG_SCHEDULE].mEnable) {
                text = mContext.getString(R.string.enter_autoSchedulePowerMode);
            }
        }

        if (text != null) {
            Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isPowerSavedMode(int mode) {
        return (PowerManagerEx.MODE_LOWPOWER == mode || PowerManagerEx.MODE_ULTRASAVING == mode);
    }

    private class WakefulnessObserver
        implements PowerManagerInternal.WakefulnessCallback {
        @Override
        public void onWakefulnessChangeFinished(boolean screenOn) {
            if (DEBUG) Slog.d(TAG, "- onWakefulnessChangeFinished() screenOn: " + screenOn);
            msgHandler.sendMessage(msgHandler.obtainMessage(MSG_WAKEFULNESS_CHANGE_FINISH, (screenOn? 1:0), 0));
        }

        @Override
        public void onWakefulnessChangeStarted(final int wakefulness, int reason) {
            if (DEBUG) Slog.d(TAG, "- onWakefulnessChangeStarted() wakefulness: " + wakefulness);
            msgHandler.sendMessage(msgHandler.obtainMessage(MSG_WAKEFULNESS_CHANGE_START, wakefulness, reason));
        }
    }


    // app behavior type:
    // 0: none
    // 1: playing music
    // 2: doing download
    // 3: location
    private int getAppBehaviorTypeInternal(String pkgName, int uid) {

        if (mAppStateInfoCollector == null) return 0;
        int type = 0;
        try {
            AppState appState = mAppStateInfoCollector.getAppState(pkgName, UserHandle.getUserId(uid));
            if (appState == null) return PowerControllerInternal.APP_ACTION_NONE;

            if (appState.isPlayingMusic() || appState.isRecording()) {
                type = PowerControllerInternal.APP_ACTION_PLAYING_MUSIC;
            } else if (appState.mDoingDownload) {
                type = PowerControllerInternal.APP_ACTION_DOWNLOADING;
            } else if (appState.mNeedUsingGps) {
                type = PowerControllerInternal.APP_ACTION_LOCATION;
            }
        } catch (Exception e) {

        }

        return type;
    }


    // --- interface class ---

    public interface PowerModeListener {
        public void onPowerModeChanged(int mode);
    }


    private final ArrayList<PowerModeListener> mPowerModeListeners
            = new ArrayList<PowerModeListener>();


    private void notifyPowerModeListeners(int mode) {
        ArrayList<PowerModeListener> listeners;
        synchronized (mLock) {
            listeners = new ArrayList<PowerModeListener>(
                    mPowerModeListeners);
        }
        for (int i=0; i<listeners.size(); i++) {
            listeners.get(i).onPowerModeChanged(mode);
        }
    }
    // Note: Bug 805226 Dialog interval narrow -->BEG
    private static class UltraSavingAlertDialog extends AlertDialog {
        protected UltraSavingAlertDialog(Context context) {
            super(context);
        }

        private static class Builder{
            public UltraSavingAlertDialog dialog;
            private TextView title;
            private TextView summary;
            private Button mButton_positive;
            private Button mButton_negative;
            private View.OnClickListener mOnPosititveClickListener;
            private View.OnClickListener mOnCancelClickListener;
            private String titleStr;
            private String summaryStr;
            private int mButton_positiveId;
            private int mButton_negativeId;
            private CheckBox mCheckBox;


            Builder(Context context){
                dialog  = new UltraSavingAlertDialog(context);
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View view = inflater.inflate(R.layout.ultra_saving_dialog,null);
                title = view.findViewById(R.id.text_title);
                summary = view.findViewById(R.id.text_msg);
                mButton_positive = view.findViewById(R.id.button_positive);
                mButton_negative = view.findViewById(R.id.button_negative);
                mCheckBox = view.findViewById(R.id.ck_dontremind);
                dialog.setView(view);
            }
            public UltraSavingAlertDialog create(){
                if(title != null && titleStr != null){
                    title.setText(titleStr);
                }
                if(summary != null && summaryStr != null){
                    summary.setText(summaryStr);
                }
                if(mButton_positive != null && mButton_positiveId != 0){
                    mButton_positive.setText(mButton_positiveId);
                    mButton_positive.setOnClickListener(mOnPosititveClickListener);
                }
                if(mButton_negative != null && mButton_negativeId != 0){
                    mButton_negative.setText(mButton_negativeId);
                    mButton_negative.setOnClickListener(mOnCancelClickListener);
                }
                return dialog;
            }
            public Builder setTitle(String title){
                titleStr = title;
                return this;
            }
            public Builder setMessage(String message){
                summaryStr = message;
                return this;
            }
            public boolean isChecked(){
                return mCheckBox.isChecked();
            }
            public Builder setPositiveButton(int textId,View.OnClickListener listener){
                mButton_positiveId = textId;
                mOnPosititveClickListener = listener;
                return this;
            }
            public Builder setNegativeButton(int textId,View.OnClickListener listener){
                mButton_negativeId = textId;
                mOnCancelClickListener = listener;
                return this;
            }
        }
    }
    // Note: Bug 805226 Dialog interval narrow <--END

    // Add for bug 868839 START
    private IImsServiceEx getIImsServiceEx(){
        if(mImsServiceEx == null){
            mImsServiceEx = ImsManagerEx.getIImsServiceEx();
        }
        return mImsServiceEx;
    }

    public boolean isCurrentVideoCallActive() {
        try {
            if(getIImsServiceEx() !=null){
                return (mImsServiceEx.getCurrentImsVideoState()
                        == VideoProfile.STATE_BIDIRECTIONAL);
            } else {
                return false;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "RemoteException: getCurrentImsCallType() failed !!!!!");
            e.printStackTrace();
        } catch (NullPointerException e) {
            Slog.e(TAG, "NullPointerException: getCurrentImsCallType() failed !!!!!");
            e.printStackTrace();
        }
        return false;
    }
    // Add for bug 868839 END

    // Add for bug 868839,878708 START
    private boolean isInVideoCallOrNormalCall(int preModeConfigIndex) {
        Slog.d(TAG,"isInVideoCallOrNormalCall:mModeConfigIndex:"+mModeConfigIndex + ",preModeConfigIndex = "+preModeConfigIndex);
        mNeedCheckPowerModeForCall = false;
        if (preModeConfigIndex != mModeConfigIndex) {
            mNeedCheckPowerModeForCall = false;
            if ((isCurrentVideoCallActive() || isInCall())) {
                Slog.d(TAG,"isInVideoCallOrNormalCall:can't switch mode During ongoing call,don't change mModeConfigIndex");
                mNeedCheckPowerModeForCall = true;
                showToastForSwitchModeWhenInCallIfNeed(preModeConfigIndex);
                return true;
            }
            Slog.d(TAG,"isInVideoCallOrNormalCall:neither VT call nor normal call is now in progress,continue");
        }
        return false;
    }

    private void showToastForSwitchModeWhenInCallIfNeed(int preModeConfigIndex) {
        if (!mHasShowToastForSwichModeWhenInCall) {
            mHasShowToastForSwichModeWhenInCall = true;
            String modeStr = null;
            String message = null;
            //activate auto low power/schedule power
            if (preModeConfigIndex != MODECONFIG_AUTOLOWPOWER && mModeConfigIndex != MODECONFIG_USER) {
                if (mModeConfigIndex == MODECONFIG_AUTOLOWPOWER) {
                    modeStr = mContext.getString(R.string.pwctl_autolowpower_saving);
                } else {
                    modeStr = mContext.getString(R.string.pwctl_schedule_power_saving);
                }
                message = mContext.getString(R.string.delaySwitchModeWhenInCallForEnter,modeStr);
            } else if (preModeConfigIndex != MODECONFIG_USER && mModeConfigIndex != MODECONFIG_AUTOLOWPOWER) {//exit from auto low/schedule to user.
                if (preModeConfigIndex == MODECONFIG_AUTOLOWPOWER) {
                    modeStr = mContext.getString(R.string.pwctl_autolowpower_saving);
                } else {
                    modeStr = mContext.getString(R.string.pwctl_schedule_power_saving);
                }
                message = mContext.getString(R.string.delaySwitchModeWhenInCallForExit,modeStr);
            }
            if (message != null) {
                Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
            }
        }
    }


    private boolean isInCall() {
        if (getTelecommManager() != null) {
            return getTelecommManager().isInCall();
        }
        return false;
    }

    private TelecomManager getTelecommManager() {
        return (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }

    // Add for bug 868839,878708 END

}
