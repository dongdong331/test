/*
 ** Copyright 2018 The Spreadtrum.com
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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.sprdpower.AppPowerSaveConfig;
import android.os.BatteryManager;
import android.os.BatteryManagerInternal;
import android.os.BatteryStatsInternal;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IDeviceIdleController;
import android.os.sprdpower.ISSense;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.sprdpower.PowerManagerEx;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.WorkSource;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.Xml;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodInfo;
import android.view.GestureDetector;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;

import com.android.internal.view.IInputMethodManager;
import com.android.internal.util.XmlUtils;
import com.android.internal.R;
import com.android.server.am.BatteryStatsService;
import com.android.server.DeviceIdleController;
import com.android.server.LocalServices;
import com.android.server.net.NetworkPolicyManagerInternal;
import com.android.server.policy.PhoneWindowManager;
import com.android.server.policy.WindowManagerPolicy.WindowManagerFuncs;
import android.view.WindowManagerPolicyConstants.PointerEventListener;
import com.android.server.wm.WindowManagerInternal;
import com.android.server.wm.WindowManagerInternal.AppTransitionListener;
import com.android.server.wm.WindowManagerService;

import java.lang.reflect.Array;
import java.util.ArrayList;
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

// Sprd: self define calss
import android.os.BundleData;
import android.os.sprdpower.Util;
import android.os.sprdpower.Scene;

public class SmartSenseService
        extends UsageStatsManagerInternal.AppStateEventChangeListener
        implements FrameRateMonitor.Callbacks, TouchRateMonitor.Callbacks {

    private static final String TAG = "SSense";

    private static final boolean DEBUG = false;

    private final boolean mEnabled = SystemProperties.getBoolean(SMART_SENSE_ENABLE, true);

    static final String SMART_SENSE_ENABLE = "persist.sys.ss.enable";

    private final boolean mEnableScrollInput = SystemProperties.getBoolean("persist.sys.ss.scroll", false);

    private final boolean mEnableAppStateTracker = SystemProperties.getBoolean("persist.sys.ss.track", true);
    private final boolean mEnableSceneReconize = SystemProperties.getBoolean("persist.sys.ss.scene", true);
    private final boolean mEnableUserHabit = SystemProperties.getBoolean("persist.sys.ss.habit", true);

    // should sync with ssense.h
    // DATA TYPE for binder call reportData
    public static final int DATA_TYPE_APP_AUDIO = 0;
    public static final int DATA_TYPE_APP_VIDEO = 1;


    // DATA SUBTYPE for VIDEO for binder call reportData
    public static final int DATA_SUBTYPE_APP_VIDEO_STOP = 0;
    public static final int DATA_SUBTYPE_APP_VIDEO_START = 1;

    private final Context mContext;
    private final IActivityManager mActivityManager;

    private  UsageStatsManagerInternal mUsageStatsInternal;

    // for touch event listener
    private WindowManagerFuncs mWindowManagerFuncs;
    private WindowManagerInternal mWindowManagerInternal;
    private ActivityManagerInternal mActivityManagerInternal;
    private WindowManagerService mWindowManagerService;

    private BatteryStatsInternal mBatteryStatsInternal;

    private SensorManager mSensorManager;

    private Handler msgHandler;

    // Foreground at UID granularity.
    private final SparseIntArray mUidState = new SparseIntArray();
    private final Object mUidStateLock = new Object();

    // UsageHabitCollection
    private UsageHabitCollection mUsageHabitCollection;

    // SceneRecognizeService
    private SceneRecognizeService mSceneRecognizeService;

    private AppStateTracker mAppStateTracker = null;

    private final AppInfoUtil mAppInfoUtil;

    private FrameRateMonitor mFrameRateMonitor;
    private TouchRateMonitor mTouchRateMonitor;

    public SmartSenseService(Context context, IActivityManager activityManager) {
        mContext = context;
        mActivityManager = activityManager;
        mAppInfoUtil = AppInfoUtil.getInstance(context);

        ServiceManager.addService("sprdssense", new BinderService());

        if (!mEnabled) return;

        if (mEnableAppStateTracker) {
            mAppStateTracker = AppStateTracker.getInstance(mContext);
            LocalServices.addService(AppStateTracker.class, mAppStateTracker);
        }
    }


    // called when system is ready ( that is all the service is started)
    public void systemReady() {
        mUsageStatsInternal = LocalServices.getService(UsageStatsManagerInternal.class);
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
        mActivityManagerInternal = LocalServices.getService(ActivityManagerInternal.class);
        mBatteryStatsInternal = LocalServices.getService(BatteryStatsInternal.class);

        // if not enable don't create & start handler thread
        if (!mEnabled) return;

        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);

        // UsageHabitCollection
        mUsageHabitCollection = new UsageHabitCollection(mContext);

        // SceneRecognizeService
        mSceneRecognizeService = SceneRecognizeService.getInstance(mContext);

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        msgHandler = new MyHandler(handlerThread.getLooper());


        mFrameRateMonitor = new FrameRateMonitor(msgHandler, this);
        mTouchRateMonitor = new TouchRateMonitor(msgHandler, this);

        // to init Data first
        msgHandler.sendMessage(msgHandler.obtainMessage(MSG_INIT));

        mAppInfoUtil.loadInstalledPackages();

        if (mEnableAppStateTracker && mAppStateTracker != null) {
            mAppStateTracker.systemReady();
            mAppStateTracker.setSenseService(this);
        }

        // register
        mUsageStatsInternal.addAppStateEventChangeListener(this);

        registerBroadcastReceiver();
        registerUidObserver();
        registerTouchEventListener();
        registerAppTransitionListener();
        registerBatteryStatsListener();
        registerWindowChangeListener();


        /* < Register PowerHint callback Bgn */
        try {
            if (mSceneRecognizeService != null) {
                mSceneRecognizeService.registerSceneStatsNotifier(new PowerHintCallback(mContext)
                        , Scene.SCENE_TYPE_VIDEO | Scene.SCENE_TYPE_GAME
                        | Scene.SCENE_TYPE_EVENT | Scene.SCENE_TYPE_APP);
            } else {
                Slog.d(TAG, "mSceneRecognizeService is null, register PowerHintCallback failed");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        /* Register PowerHint callback End > */
    }

    // shuold be called before SystemReadby
    public void setWindowManager(WindowManagerService wm) {
        mWindowManagerFuncs = wm;
        mWindowManagerService = wm;
    }

    public boolean isInstalledApp(String pkgName, int userId) {
        return mAppInfoUtil.isInstalledApp(pkgName, userId);
    }

    public ArrayList<String> getInstallAppList(int userId) {
        return mAppInfoUtil.getInstallAppList(userId);
    }


    @Override
    public void onAppStateEventChanged(String packageName, int userId, int state) {
        if (state == UsageEvents.Event.STANDBY_BUCKET_CHANGED) {
            if (DEBUG) Slog.d(TAG, " ignore event: STANDBY_BUCKET_CHANGED for " + packageName);
            return;
        }
        msgHandler.sendMessage(msgHandler.obtainMessage(MSG_APP_STATE_CHANGED, userId, state, packageName));
    }


    //Message define
    static final int MSG_INIT = 0;
    static final int MSG_APP_STATE_CHANGED = 1;
    static final int MSG_DEVICE_STATE_CHANGED = 2;
    static final int MSG_UID_STATE_CHANGED = 3;
    static final int MSG_BOOT_COMPLETED = 4;
    static final int MSG_INPUT = 5;
    static final int MSG_APP_TRANSITION = 6;
    static final int MSG_BATTERY_EVENT = 7;
    static final int MSG_REPORT_DATA = 8;
    static final int MSG_WINDOW_CHANGED = 9;

    final class MyHandler extends Handler {
        MyHandler(Looper looper) {
            super(looper);
        }

        String Msg2Str(int msg) {
            final String msgStr[] = {
                "MSG_INIT",
                "MSG_APP_STATE_CHANGED",
                "MSG_DEVICE_STATE_CHANGED",
                "MSG_UID_STATE_CHANGED",
                "MSG_BOOT_COMPLETED",
                "MSG_INPUT",
                "MSG_APP_TRANSITION",
                "MSG_BATTERY_EVENT",
                "MSG_REPORT_DATA",
                "MSG_WINDOW_CHANGED",
            };

            if ((0 <= msg) && (msg < msgStr.length))
                return msgStr[msg];
            else
                return "Unknown message: " + msg;
        }

        @Override
        public void handleMessage(Message msg) {
            if (DEBUG) Slog.d(TAG, "handleMessage(" + Msg2Str(msg.what) + ")");

            try {
                switch (msg.what) {
                case MSG_APP_STATE_CHANGED:
                    handleAppStateEventChanged((String)msg.obj, msg.arg1, msg.arg2);
                    break;
                case MSG_DEVICE_STATE_CHANGED:
                    handleDeviceStateChanged((Intent)msg.obj);
                    break;
                case MSG_UID_STATE_CHANGED:
                    handleProcStateChanged((String)msg.obj, msg.arg1, msg.arg2);
                    break;
                case MSG_INIT:
                    initData();
                    break;
                case MSG_INPUT:
                    handleTouchInput(msg.arg1);
                    break;
                case MSG_APP_TRANSITION:
                    handleAppTransition();
                    break;
                case MSG_BATTERY_EVENT:
                    handleBatteryStatsEvent((BundleData)msg.obj);
                    break;
                case MSG_REPORT_DATA:
                    handleDataInfo((DataInfo)msg.obj);
                    break;
                case MSG_WINDOW_CHANGED:
                    handleWindowChanged();
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private void registerBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        intentFilter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        intentFilter.addAction(Intent.ACTION_BOOT_COMPLETED);

        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if(Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                        msgHandler.sendMessage(msgHandler.obtainMessage(MSG_BOOT_COMPLETED));
                    } else {
                        msgHandler.sendMessage(msgHandler.obtainMessage(MSG_DEVICE_STATE_CHANGED, intent));
                    }
                }
            }, intentFilter);
    }

    // register touch event listener
    private void registerTouchEventListener() {
        if (mWindowManagerFuncs != null)
            mWindowManagerFuncs.registerPointerEventListener(new TouchEventListener());
    }


    private GestureDetector mGestureDetector;
    private GestureListener mGestureListener = new GestureListener();

    private class TouchEventListener implements PointerEventListener {

        public TouchEventListener() {
        }

        @Override
        public void onPointerEvent(MotionEvent event) {
            if (!event.isTouchEvent()) {
                return;
            }

            if (mGestureDetector == null) {
                mGestureDetector = new GestureDetector(mContext, mGestureListener);
                mGestureDetector.setOnDoubleTapListener(mGestureListener);
                mGestureDetector.setContextClickListener(mGestureListener);
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    msgHandler.sendMessage(msgHandler.obtainMessage(MSG_INPUT, BundleData.TOUCH_EVENT_DOWN, 0));
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    break;
                case MotionEvent.ACTION_MOVE:
                    break;
                case MotionEvent.ACTION_HOVER_MOVE:
                    break;
                case MotionEvent.ACTION_UP:
                    msgHandler.sendMessage(msgHandler.obtainMessage(MSG_INPUT, BundleData.TOUCH_EVENT_UP, 0));
                    break;
                case MotionEvent.ACTION_CANCEL:
                    break;
                default:
                    if (DEBUG) Slog.d(TAG, "Ignoring " + event);
            }

            mGestureDetector.onTouchEvent(event);

        }

    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            //if (DEBUG) Slog.d(TAG, "onSingleTapUp");
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            //if (DEBUG) Slog.d(TAG, "onLongPress");
            msgHandler.sendMessage(msgHandler.obtainMessage(MSG_INPUT, BundleData.TOUCH_EVENT_LONG_PRESS, 0));
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                float distanceX, float distanceY) {
            //if (DEBUG) Slog.d(TAG, "onScroll");
            if (mEnableScrollInput) {
                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_INPUT, BundleData.TOUCH_EVENT_SCROLL, 0));
            }
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                float velocityY) {
            //if (DEBUG) Slog.d(TAG, "onFling");
            msgHandler.sendMessage(msgHandler.obtainMessage(MSG_INPUT, BundleData.TOUCH_EVENT_FLING, 0));
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {
            //if (DEBUG) Slog.d(TAG, "onShowPress");
        }

        @Override
        public boolean onDown(MotionEvent e) {
            //if (DEBUG) Slog.d(TAG, "onDown");
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            //if (DEBUG) Slog.d(TAG, "onDoubleTap");
            msgHandler.sendMessage(msgHandler.obtainMessage(MSG_INPUT, BundleData.TOUCH_EVENT_DOUBLE_TAP, 0));
            return true;
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            //if (DEBUG) Slog.d(TAG, "onDoubleTapEvent");
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            //if (DEBUG) Slog.d(TAG, "onSingleTapConfirmed");
            msgHandler.sendMessage(msgHandler.obtainMessage(MSG_INPUT, BundleData.TOUCH_EVENT_SINGLE_TAP, 0));
            return true;
        }

        @Override
        public boolean onContextClick(MotionEvent e) {
            if (DEBUG) Slog.d(TAG, "onContextClick");
            return true;
        }
    }

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
            if (DEBUG) Slog.d(TAG, "removeUidStateLocked: packageName:"
                + appName + ", uid:" + uid + " state : "  + Util.ProcState2Str(oldUidState));

            if ((null != appName) ) {
                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_UID_STATE_CHANGED,
                    uid, ActivityManager.PROCESS_STATE_CACHED_EMPTY, appName));
            }
        }
    }

    final private IUidObserver mUidObserver = new IUidObserver.Stub() {
        @Override
        public void onUidStateChanged(int uid, int procState, long procStateSeq) throws RemoteException {
            synchronized (mUidStateLock) {
                updateUidStateLocked(uid, procState);
            }
        }

        @Override
        public void onUidGone(int uid, boolean disabled) throws RemoteException {
            synchronized (mUidStateLock) {
                removeUidStateLocked(uid);
            }
        }

        @Override
        public void onUidActive(int uid) throws RemoteException {
        }


        @Override
        public void onUidIdle(int uid, boolean disabled) throws RemoteException {
        }

        @Override
        public void onUidCachedChanged(int uid, boolean cached) throws RemoteException {
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

    private void registerAppTransitionListener() {
        if (mWindowManagerInternal == null) return;
        mWindowManagerInternal.registerAppTransitionListener(new AppTransitionListener() {
            @Override
            public void onAppTransitionPendingLocked() {
            }

            @Override
            public int onAppTransitionStartingLocked(int transit, IBinder openToken,
                    IBinder closeToken, long duration, long statusBarAnimationStartTime,
                    long statusBarAnimationDuration) {
                return 0;
            }

            @Override
            public void onAppTransitionCancelledLocked(int transit) {
            }

            @Override
            public void onAppTransitionFinishedLocked(IBinder token) {
                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_APP_TRANSITION));
            }
        });
    }


    private void registerBatteryStatsListener() {
        if (mBatteryStatsInternal == null) return;
        mBatteryStatsInternal.registerBatteryStatsListener(new BatteryStatsInternal.BatteryStatsListener() {
            @Override
            public void noteStartSensor(int uid, int sensor) {
                BundleData data = new BundleData(BundleData.DATA_TYPE_BATTERY_EVENT);
                data.putExtra(BundleData.DATA_EXTRA_SUBTYPE, BundleData.DATA_SUBTYPE_SENSOR);
                data.putExtra(BundleData.DATA_EXTRA_UID, uid);
                data.putExtra(BundleData.DATA_EXTRA_SENSOR, sensor);
                data.putExtra(BundleData.DATA_EXTRA_STATE, BundleData.STATE_START);

                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_BATTERY_EVENT, data));
            }
            @Override
            public void noteStopSensor(int uid, int sensor) {
                BundleData data = new BundleData(BundleData.DATA_TYPE_BATTERY_EVENT);
                data.putExtra(BundleData.DATA_EXTRA_SUBTYPE, BundleData.DATA_SUBTYPE_SENSOR);
                data.putExtra(BundleData.DATA_EXTRA_UID, uid);
                data.putExtra(BundleData.DATA_EXTRA_SENSOR, sensor);
                data.putExtra(BundleData.DATA_EXTRA_STATE, BundleData.STATE_STOP);

                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_BATTERY_EVENT, data));
            }
            @Override
            public void noteVibratorOn(int uid, long durationMillis) {
            }
            @Override
            public void noteVibratorOff(int uid) {
            }
            @Override
            public void noteGpsChanged(WorkSource oldWs, WorkSource newWs) {
            }
            @Override
            public void notePhoneOn() {
            }
            @Override
            public void notePhoneOff() {
            }
            @Override
            public void noteStartAudio(int uid) {
                BundleData data = new BundleData(BundleData.DATA_TYPE_BATTERY_EVENT);
                data.putExtra(BundleData.DATA_EXTRA_SUBTYPE, BundleData.DATA_SUBTYPE_AUDIO);
                data.putExtra(BundleData.DATA_EXTRA_UID, uid);
                data.putExtra(BundleData.DATA_EXTRA_STATE, BundleData.STATE_START);

                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_BATTERY_EVENT, data));
            }
            @Override
            public void noteStopAudio(int uid) {
                BundleData data = new BundleData(BundleData.DATA_TYPE_BATTERY_EVENT);
                data.putExtra(BundleData.DATA_EXTRA_SUBTYPE, BundleData.DATA_SUBTYPE_AUDIO);
                data.putExtra(BundleData.DATA_EXTRA_UID, uid);
                data.putExtra(BundleData.DATA_EXTRA_STATE, BundleData.STATE_STOP);

                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_BATTERY_EVENT, data));
            }
            @Override
            public void noteStartVideo(int uid) {
                BundleData data = new BundleData(BundleData.DATA_TYPE_BATTERY_EVENT);
                data.putExtra(BundleData.DATA_EXTRA_SUBTYPE, BundleData.DATA_SUBTYPE_VIDEO);
                data.putExtra(BundleData.DATA_EXTRA_UID, uid);
                data.putExtra(BundleData.DATA_EXTRA_STATE, BundleData.STATE_START);

                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_BATTERY_EVENT, data));
            }
            @Override
            public void noteStopVideo(int uid) {
                BundleData data = new BundleData(BundleData.DATA_TYPE_BATTERY_EVENT);
                data.putExtra(BundleData.DATA_EXTRA_SUBTYPE, BundleData.DATA_SUBTYPE_VIDEO);
                data.putExtra(BundleData.DATA_EXTRA_UID, uid);
                data.putExtra(BundleData.DATA_EXTRA_STATE, BundleData.STATE_STOP);

                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_BATTERY_EVENT, data));
            }
            @Override
            public void noteResetAudio() {
            }
            @Override
            public void noteResetVideo() {
            }
            @Override
            public void noteFlashlightOn(int uid) {
            }
            @Override
            public void noteFlashlightOff(int uid) {
            }
            @Override
            public void noteStartCamera(int uid) {
            }
            @Override
            public void noteStopCamera(int uid) {
            }
            @Override
            public void noteResetCamera() {
            }
            @Override
            public void noteResetFlashlight() {
            }

            @Override
            public void noteProcessStart(String name, int uid) {
                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_UID_STATE_CHANGED
                            , uid, BundleData.PROCESS_STATE_VENDOR_START, name));
            }

            @Override
            public void noteProcessFinish(String name, int uid) {
                msgHandler.sendMessage(msgHandler.obtainMessage(MSG_UID_STATE_CHANGED
                            , uid, BundleData.PROCESS_STATE_VENDOR_FINISH, name));
            }

        });
    }

    private void registerWindowChangeListener () {
        Slog.d(TAG, "registerWindowChangeListener");

        mWindowManagerService.addWindowChangeListener(new WindowManagerService.WindowChangeListener() {
                @Override
                public void windowsChanged() {
                    Slog.d(TAG, "windowsChanged");
                    msgHandler.removeMessages(MSG_WINDOW_CHANGED);
                    msgHandler.sendMessage(msgHandler.obtainMessage(MSG_WINDOW_CHANGED));
                }

                @Override
                public void focusChanged() {
                    //Slog.d(TAG, "focusChanged");
                }
            });
    }

    private void handleAppStateEventChanged(String packageName, int userId, int stateEvent) {
         if (DEBUG) Slog.d(TAG, "handleAppStateEventChanged: - packageName:" + packageName
            + " stateEvent:" + Util.AppState2Str(stateEvent)+ " user:" + userId);

        try {
            int uid = mContext.getPackageManager().getPackageUidAsUser(packageName, userId);
            BundleData data = new BundleData(BundleData.DATA_TYPE_APP_STATE_EVENT);
            data.putExtra(BundleData.DATA_EXTRA_PACKAGENAME, packageName);
            data.putExtra(BundleData.DATA_EXTRA_UID, uid);
            data.putExtra(BundleData.DATA_EXTRA_APP_STATE_EVENT, stateEvent);
            reportData(data);
        } catch (Exception e) {
        }
    }

    private void handleProcStateChanged(String appName, int uid, int procState) {
        if (DEBUG) Slog.d(TAG, "- handleProcstateChanged() - packageName:" + appName
            + " uid:" + uid + " procState:" + Util.ProcState2Str(procState));

        BundleData data = new BundleData(BundleData.DATA_TYPE_PROCESS_STATE);
        data.putExtra(BundleData.DATA_EXTRA_PACKAGENAME, appName);
        data.putExtra(BundleData.DATA_EXTRA_UID, uid);
        data.putExtra(BundleData.DATA_EXTRA_PROCESS_STATE, procState);
        reportData(data);
    }

    private void handleDeviceStateChanged(Intent intent) {
        String action = intent.getAction();
        BundleData data = new BundleData(BundleData.DATA_TYPE_DEV_STATUS);

        if (DEBUG) Slog.d(TAG, "- handleDeviceStateChanged() - action: " + action);

        if (action.equals(Intent.ACTION_SCREEN_ON)) {
            data.putExtra(BundleData.DATA_EXTRA_SUBTYPE, BundleData.DATA_SUBTYPE_SCREEN);
            data.putExtra(BundleData.DATA_EXTRA_SCREEN_ON, true);

            mFrameRateMonitor.onScreenOn(true);
            mTouchRateMonitor.onScreenOn(true);
        } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
            data.putExtra(BundleData.DATA_EXTRA_SUBTYPE, BundleData.DATA_SUBTYPE_SCREEN);
            data.putExtra(BundleData.DATA_EXTRA_SCREEN_ON, false);

            mFrameRateMonitor.onScreenOn(false);
            mTouchRateMonitor.onScreenOn(false);
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            data.putExtra(BundleData.DATA_EXTRA_SUBTYPE, BundleData.DATA_SUBTYPE_NETWORK);
        } else if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            int pluggedType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            boolean charging = (0 != intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));

            if (DEBUG) Slog.d(TAG, "pluggedType:" + pluggedType + " charging:" + charging);

            data.putExtra(BundleData.DATA_EXTRA_SUBTYPE, BundleData.DATA_SUBTYPE_BATTERY);
            data.putExtra(BundleData.DATA_EXTRA_BATTERY_PLUGGED, charging);

        } else if (action.equals(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)) {

        }

        reportData(data);
    }

    private void handleTouchInput(int touchEvent) {
        if (DEBUG) Slog.d(TAG, "- handleTouchInput() - touchEvent:" + touchEvent);
        int event = touchEvent;

        mTouchRateMonitor.onTouchEvent(event);

        BundleData data = new BundleData(BundleData.DATA_TYPE_INPUT);
        data.putExtra(BundleData.DATA_EXTRA_TOUCH_EVENT, event);
        reportData(data);
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

        } catch (Exception e) {
            e.printStackTrace();
        }

        mFrameRateMonitor.onAppTransitionFinished();
        mTouchRateMonitor.onAppTransitionFinished();

        BundleData data = new BundleData(BundleData.DATA_TYPE_APP_TRANSITION);
        data.putStringArrayListExtra(BundleData.DATA_EXTRA_VISIBLE_APPS, visibleAppList);
        reportData(data);
    }


    private void handleBatteryStatsEvent(BundleData data) {
        if (DEBUG) Slog.d(TAG, "- handleBatteryStatsEvent()");

        int uid = -1;

        try {
            uid = data.getIntExtra(BundleData.DATA_EXTRA_UID, -1);
            if (uid != -1) {
                String appName = null;
                try {
                    appName = AppGlobals.getPackageManager().getNameForUid(uid);
                } catch (RemoteException e) {
                    // can't happen; package manager is process-local
                }
                data.putExtra(BundleData.DATA_EXTRA_PACKAGENAME, appName);
            }

            // find the sensor type
            int subType = data.getIntExtra(BundleData.DATA_EXTRA_SUBTYPE, -1);
            if (subType == BundleData.DATA_SUBTYPE_SENSOR) {
                int handle = data.getIntExtra(BundleData.DATA_EXTRA_SENSOR, -1);
                List<Sensor> list;
                final List<Sensor> fullList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
                for (Sensor se : fullList) {
                    if (se.getHandle() == handle) {
                        data.putExtra(BundleData.DATA_EXTRA_SENSOR, se.getType());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        reportData(data);
    }

    private void handleDataInfo(DataInfo dataInfo) {
        if (DEBUG) Slog.d(TAG, "- handleDataInfo()");

        if (dataInfo == null) return;

        try {
            if ( DATA_TYPE_APP_VIDEO == dataInfo.mType) {
                int uid = dataInfo.mData1;
                if (Process.MEDIA_UID == uid) {
                    if (DEBUG) Slog.d(TAG, "- MEDIA play video, just return");
                    return;
                }

                BundleData data = new BundleData(BundleData.DATA_TYPE_APP_VIDEO);
                data.putExtra(BundleData.DATA_EXTRA_UID, uid);
                if (DATA_SUBTYPE_APP_VIDEO_START == dataInfo.mData2) {
                    data.putExtra(BundleData.DATA_EXTRA_STATE, BundleData.STATE_START);
                } else {
                    data.putExtra(BundleData.DATA_EXTRA_STATE, BundleData.STATE_STOP);
                }
                String appName = null;
                try {
                    appName = AppGlobals.getPackageManager().getNameForUid(uid);
                } catch (RemoteException e) {
                    // can't happen; package manager is process-local
                }
                data.putExtra(BundleData.DATA_EXTRA_PACKAGENAME, appName);

                data.putExtra(BundleData.DATA_EXTRA_VIDEO_WIDTH, dataInfo.mData3);
                data.putExtra(BundleData.DATA_EXTRA_VIDEO_HEIGHT, dataInfo.mData4);

                reportData(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void handleWindowChanged() {
        if (DEBUG) Slog.d(TAG, "- handleWindowChanged()");

        reportEvent(BundleData.SYSTEM_EVENT_WINDOW_CHANGED);
    }


    private void reportData(BundleData data) {

        if (DEBUG) Slog.d(TAG, "reportData:"+ " BundleData:" + data);

        try {
            if (mEnableUserHabit && mUsageHabitCollection != null) {
                mUsageHabitCollection.reportData(data);
            }

            if (mEnableSceneReconize && mSceneRecognizeService != null) {
                mSceneRecognizeService.reportData(data);
            }

            if (mEnableAppStateTracker && mAppStateTracker != null) {
                mAppStateTracker.reportData(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void reportEvent(int event) {

        if (DEBUG) Slog.d(TAG, "reportEvent:"+ event);

        try {
            if (mEnableUserHabit && mUsageHabitCollection != null) {
                mUsageHabitCollection.reportEvent(event);
            }

            if (mEnableSceneReconize && mSceneRecognizeService != null) {
                mSceneRecognizeService.reportEvent(event);
            }

            if (mEnableAppStateTracker && mAppStateTracker != null) {
                mAppStateTracker.reportEvent(event);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initData() {
        ConfigReader.getInstance();
    }

    // if SDK is pre 'P'
    private boolean isPreP() {
        return (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1);
    }

    /**
     * Shared singleton background thread for smartSenseService.
     */
    public final static class BackgroundThread extends HandlerThread {
        private static BackgroundThread sInstance;
        private static Handler sHandler;

        private BackgroundThread() {
            super("SmartSS.bg", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        }

        private static void ensureThreadLocked() {
            if (sInstance == null) {
                sInstance = new BackgroundThread();
                sInstance.start();
                sHandler = new Handler(sInstance.getLooper());
            }
        }

        public static BackgroundThread get() {
            synchronized (BackgroundThread.class) {
                ensureThreadLocked();
                return sInstance;
            }
        }

        public static Handler getHandler() {
            synchronized (BackgroundThread.class) {
                ensureThreadLocked();
                return sHandler;
            }
        }
    }


    private static class DataInfo {

        public int mType;
        public int mData1;
        public int mData2;
        public int mData3;
        public int mData4;

        public DataInfo(int type, int data1, int data2, int data3, int data4) {
            mType = type;
            mData1 = data1;
            mData2 = data2;
            mData3 = data3;
            mData4 = data4;
        }
    }

    private final class BinderService extends ISSense.Stub {
        @Override
        public void reportData(int type, int data1, int data2, int data3, int data4) {
            if (!mEnabled) return;

            /*if (DEBUG)*/ Slog.d(TAG, "reportData:"+ " type:" + type + " data1:" + data1 + " data2:" + data2
                + " data3:" + data3 + " data4:" + data4);
            // add for bug#938844 --> start
            try {
                if (msgHandler != null) {
                    DataInfo dataInfo = new DataInfo(type, data1, data2, data3, data4);
                    msgHandler.sendMessage(msgHandler.obtainMessage(MSG_REPORT_DATA, dataInfo));
                }
            } catch (Exception e) {}
            // add for bug#938844 <-- end
        }

    }

    @Override
    public void onTouchRateChanged(float rate) {

        try {

            /*if (DEBUG)*/ Slog.d(TAG, "onTouchRateChanged:"+ " rate:" + rate);

            BundleData data = new BundleData(BundleData.DATA_TYPE_SYSTEM_EVENT);

            data.putExtra(BundleData.DATA_EXTRA_SUBTYPE, BundleData.SYSTEM_EVENT_TOUCHRATE_CHANGED);

            data.putExtra(BundleData.DATA_EXTRA_RATE, rate);

            reportData(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onFpsChanged(float fps) {
        try {

            /*if (DEBUG)*/ Slog.d(TAG, "onFpsChanged:"+ " fps:" + fps);

            BundleData data = new BundleData(BundleData.DATA_TYPE_SYSTEM_EVENT);

            data.putExtra(BundleData.DATA_EXTRA_SUBTYPE, BundleData.SYSTEM_EVENT_FPS_CHANGED);

            data.putExtra(BundleData.DATA_EXTRA_RATE, fps);

            reportData(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
