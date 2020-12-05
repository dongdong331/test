package com.android.server.policy;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.PatternMatcher;
import android.os.PowerManager.WakeLock;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.sprdpower.IPowerManagerEx;
import android.os.sprdpower.PowerManagerEx;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Slog;
import android.view.KeyEvent;
import android.view.HapticFeedbackConstants;
import android.view.IWindowManager;
import android.view.WindowManager;

import com.android.server.statusbar.StatusBarManagerInternal;
import java.io.PrintWriter;
import java.util.List;

import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;

public class SprdPhoneWindowManager extends PhoneWindowManager {
    private static final String SYSTEM_DIALOG_REASON_CAMERA_KEY = "camerakey";
    private Intent mCameraIntent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
    private static final int LONG_PRESS_BACK_CANCEL_LOCKTASK = 2;
    public static final String TOUCH_DISABLE = "touch_disable";
    private static final String ACTION_QUICKSTEP = "android.intent.action.QUICKSTEP_SERVICE";
    private IPowerManagerEx mPowerManagerEx = null;
    /* SPRD: add for dynamic navigationbar @{ */
    //new feature:disable Volumeup and Volume down
    private static final String SUBSIDY_LOCK_STATE = "gsm.subsidy.lock.state";

    void initNavStatus() {
        mDynamicNavigationBar = (Settings.System.getInt(mContext.getContentResolver(), NAVIGATIONBAR_CONFIG, 0) & 0x10) != 0;
        if (SystemProperties.getBoolean("ro.notsupport.hwpin", false)) {
            mLongPressOnBackBehavior = LONG_PRESS_BACK_CANCEL_LOCKTASK;
        }
    }

    void showNavigationBar(boolean show) {
        showNavigationBar(show, false);
    }

    void showNavigationBar(boolean show, boolean updateSettings){
        if (mNaviHided == !show) {
            return;
        }
        if (!isKeyguardShowingAndNotOccluded()) {
            mNaviHided = !show;
            mWindowManagerInternal.setLayoutNeeded();
            if (updateSettings) {
                updateNavBarSettings(show);
            }
        }
    }

    void updateNavBarSettings(boolean show) {
        Settings.System.putInt(mContext.getContentResolver(), SHOW_NAVIGATIONBAR, (show ? 1 : 0));
    }

    @Override
    public boolean isNavigationBarShowing() {
        return mHasNavigationBar && !mNaviHided;
    }

    void registerNavIfNeeded() {
        if (mHasNavigationBar) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(NAVIGATIONBAR_CONFIG),
                    true,
                    mHideNavObserver,
                    UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(SHOW_NAVIGATIONBAR),
                    true,
                    mShowHideNavObserver,
                    UserHandle.USER_ALL);
        }
    }

    private ContentObserver mHideNavObserver = new ContentObserver(mHandler) {
        public void onChange(boolean selfChange) {
            Slog.d(TAG, "nav settings changed, " + Settings.System.getInt(mContext.getContentResolver(), NAVIGATIONBAR_CONFIG, 0));
            mDynamicNavigationBar = (Settings.System.getInt(mContext.getContentResolver(), NAVIGATIONBAR_CONFIG, 0) & 0x10) != 0;
            if (!mDynamicNavigationBar) {
                showNavigationBar(true, true);
            }
        }
    };

    private ContentObserver mShowHideNavObserver = new ContentObserver(mHandler) {
        public void onChange(boolean selfChange) {
            Slog.d(TAG, "nav changed, " + Settings.System.getInt(mContext.getContentResolver(), SHOW_NAVIGATIONBAR, 0));
            boolean show = (Settings.System.getInt(mContext.getContentResolver(), SHOW_NAVIGATIONBAR, 0) & 0x1) != 0;
            if (mDynamicNavigationBar) {
                showNavigationBar(show);
            }
        }
    };

    protected void sprdObserve(ContentResolver resolver, ContentObserver observer) {
        resolver.registerContentObserver(Settings.Global.getUriFor(
                TOUCH_DISABLE), false, observer,
                UserHandle.USER_ALL);
        final Resources res = mContext.getResources();
        mHasNavigationBar = res.getBoolean(com.android.internal.R.bool.config_showNavigationBar);
        // Allow a system property to override this. Used by the emulator.
        // See also hasNavigationBar().
        String navBarOverride = SystemProperties.get("qemu.hw.mainkeys");
        if ("1".equals(navBarOverride)) {
            mHasNavigationBar = false;
        } else if ("0".equals(navBarOverride)) {
            mHasNavigationBar = true;
        }
        registerNavIfNeeded();
    }
    /* @} */
    /* SPRD: long press recent enter split-screen */
    boolean mAppSwitchKeyHandled = false;
    void appswitchLongPress() {
        mAppSwitchKeyHandled = true;
        if (!mQuickStepEnabled && ActivityManager.supportsSplitScreenMultiWindow(mContext)) {
            StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
            if (statusbar != null) {
                statusbar.toggleSplitScreen();
            }
        }
    }

    public void setThreadPriorities(int dispatchDuration, int deliveryDuration) {
         mHandler.getLooper().setSlowLogThresholdMs(dispatchDuration, deliveryDuration);
    }

    boolean hasInPowerUtrlSavingMode() {
        if (mPowerManagerEx == null) {
            mPowerManagerEx = IPowerManagerEx.Stub.asInterface(
                    ServiceManager.getService("power_ex"));
        }
        try {
            if (PowerManagerEx.MODE_ULTRASAVING == mPowerManagerEx.getPowerSaveMode()) {
                return true;
            }
        } catch (RemoteException e){
            //not much we can do here
        }
        return false;
    }

    @Override
    public long interceptKeyBeforeDispatching(WindowState win, KeyEvent event, int policyFlags) {
        final int keyCode = event.getKeyCode();
        final boolean keyguardOn = keyguardOn();
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        final int repeatCount = event.getRepeatCount();
        if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            WindowManager.LayoutParams attr = win != null ? win.getAttrs() : null;
            if (attr != null && (null != attr.packageName)
                    && ((attr.packageName.startsWith("com.sprd.validationtools")) || (attr.packageName
                            .startsWith("com.sprd.factorymode")) || (attr.packageName
                            .startsWith("com.sprd.autoslt")))) {
                if (DEBUG_INPUT) Slog.i(TAG, "skip KeyEvent " + keyCode + " for " +attr.packageName);
                return 0;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_CAMERA) {
            if ((event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0 && !keyguardOn) {
                if (handleLaunchCamera(mCameraIntent)) {
                    return -1;
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!keyguardOn && down && repeatCount == 1) {
                if (handleLongPressBackRecents(event)) {
                    return -1;
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            // Warning: this skips logic in PWM !!
            if (hasInPowerUtrlSavingMode()) {
                Slog.d(TAG,"In power ULTRA saving mode: Do not allow to show Recent Apps");
                return -1;
            }
            if (!keyguardOn) {
                if (down && repeatCount == 0) {
                    mAppSwitchKeyHandled = false;
                } else if (down && repeatCount == 1) {
                    if (handleLongPressBackRecents(event)) {
                        return -1;
                    }
                    appswitchLongPress();
                } else if (!down && !mAppSwitchKeyHandled) {
                    preloadRecentApps();
                    toggleRecentApps();
                    mAppSwitchKeyHandled = true;
                }
            }
            return -1;
        } else if (handleTouchModeKey(event) == -1) {
            return -1;
        }

        return super.interceptKeyBeforeDispatching(win, event, policyFlags);
    }

    private boolean handleLaunchCamera(Intent cameraIntent) {
        ResolveInfo resolveInfo = resolveCameraIntent(cameraIntent);
        String packageToLaunch = (resolveInfo == null || resolveInfo.activityInfo == null)
                ? null : resolveInfo.activityInfo.packageName;
        if (isForegroundApp(packageToLaunch)) {
            return false;
        } else {
            if (DEBUG_INPUT) Slog.i(TAG, "Launch Camera Because CAMERA_KEY");
            cameraIntent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
            sendCloseSystemWindows(SYSTEM_DIALOG_REASON_CAMERA_KEY);
            int result = ActivityManager.START_CANCELED;
            try {
                result = ActivityManager.getService().startActivityAsUser(
                            null, mContext.getBasePackageName(),
                            cameraIntent,
                            cameraIntent.resolveTypeIfNeeded(mContext.getContentResolver()),
                            null, null, 0, Intent.FLAG_ACTIVITY_NEW_TASK, null,
                            null, UserHandle.CURRENT.getIdentifier());
            } catch (RemoteException e) {
            }
            return true;
        }
    }

    private boolean isForegroundApp(String pkgName) {
        ActivityManager am = mContext.getSystemService(ActivityManager.class);
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        return !tasks.isEmpty() && pkgName.equals(tasks.get(0).topActivity.getPackageName());
    }

    private ResolveInfo resolveCameraIntent(Intent cameraIntent) {
        return mContext.getPackageManager().resolveActivityAsUser(cameraIntent,
                PackageManager.MATCH_DEFAULT_ONLY,
                ActivityManager.getCurrentUser());
    }

    /* SPRD: double-tap volume-down/camera key to quick capture @{ */
    private long mLastVolumeKeyDownTime = 0L;
    private long mLastCameraKeyDownTime = 0L;
     /**
     * Time in milliseconds in which the volume-down/camera button must be pressed twice so it will be considered
     * as a camera launch.
     */
    private static final long CAMERA_DOUBLE_TAP_MAX_TIME_MS = 500L;
    private static final long CAMERA_DOUBLE_TAP_MIN_TIME_MS = 50L;

    final Object mQuickCameraLock = new Object();
    ServiceConnection mQuickCameraConnection = null;
    WakeLock mWakeLock = null;

    final Runnable mQuickCameraTimeout = new Runnable() {
        @Override
        public void run() {
            synchronized (mQuickCameraLock) {
                if (mQuickCameraConnection != null) {
                    if (DEBUG_INPUT) Slog.v("QuickCamera", "unbind");
                    if (mWakeLock != null && mWakeLock.isHeld()) {
                        mWakeLock.release();
                        mWakeLock = null;
                    }
                    mContext.unbindService(mQuickCameraConnection);
                    mQuickCameraConnection = null;
                }
            }
        };
    };

    private void launchQuickCamera() {
        synchronized (mQuickCameraLock) {
            if (mQuickCameraConnection != null) {
                return;
            }
        }
        ComponentName cn = new ComponentName("com.sprd.quickcamera",
                "com.sprd.quickcamera.QuickCameraService");
        Intent intent = new Intent();
        intent.setComponent(cn);
        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                synchronized (mQuickCameraLock) {
                    if (DEBUG_INPUT) Slog.d(TAG, "launchQuickCamera" + "onServiceConnected" );
                    Messenger messenger = new Messenger(service);
                    Message msg = Message.obtain(null, 1);
                    final ServiceConnection myConn = this;
                    Handler h = new Handler(mHandler.getLooper()) {
                        @Override
                        public void handleMessage(Message msg) {
                            synchronized (mQuickCameraLock) {
                                if (DEBUG_INPUT) Slog.d(TAG, "launchQuickCamera" + "receive reply");
                                if (mQuickCameraConnection == myConn) {
                                    if (DEBUG_INPUT) Slog.d(TAG, "launchQuickCamera" + "receive reply unbind");
                                    if (mWakeLock != null && mWakeLock.isHeld()) {
                                        mWakeLock.release();
                                        mWakeLock = null;
                                    }
                                    mContext.unbindService(mQuickCameraConnection);
                                    mQuickCameraConnection = null;
                                    mHandler.removeCallbacks(mQuickCameraTimeout);
                                }
                            }
                        }
                    };
                    msg.replyTo = new Messenger(h);
                    try {
                        messenger.send(msg);
                    } catch (RemoteException e) {}
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };
        if (DEBUG_INPUT) Slog.d(TAG, "launchQuickCamera bind start");
        if (mContext.bindServiceAsUser(
                intent, conn, Context.BIND_AUTO_CREATE, UserHandle.CURRENT)) {
            if (DEBUG_INPUT) Slog.d(TAG, "launchQuickCamera bind successful");
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"QuickCamera");
            mWakeLock.acquire(6000); // more than 5000
            mQuickCameraConnection = conn;
            mHandler.postDelayed(mQuickCameraTimeout, 5000);
        }
        if (DEBUG_INPUT) Slog.d(TAG, "launchQuickCamera bind end");
    };

    public void handleQuickCamera(KeyEvent event, boolean down) {
        if (!SystemProperties.getBoolean("persist.sys.cam.quick", true)) {
            return;
        }

        boolean launched = false;
        long doubleTapInterval = 0L;
        if (!SystemProperties.getBoolean("persist.sys.cam.hascamkey", false)) {
            AudioManager audioManager = (AudioManager) mContext
                    .getSystemService(Context.AUDIO_SERVICE);
            if (!audioManager.isMusicActive()
                    && !isScreenOn()
                    && event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN
                    && down) {
                doubleTapInterval = event.getEventTime() - mLastVolumeKeyDownTime;
                if (doubleTapInterval < CAMERA_DOUBLE_TAP_MAX_TIME_MS
                        && doubleTapInterval > CAMERA_DOUBLE_TAP_MIN_TIME_MS) {
                    launched = true;
                }
                mLastVolumeKeyDownTime = event.getEventTime();
            }
        } else {
            if (!isScreenOn()
                    && event.getKeyCode() == KeyEvent.KEYCODE_CAMERA
                    && down) {
                doubleTapInterval = event.getEventTime() - mLastCameraKeyDownTime;
                if (doubleTapInterval < CAMERA_DOUBLE_TAP_MAX_TIME_MS
                        && doubleTapInterval > CAMERA_DOUBLE_TAP_MIN_TIME_MS) {
                    launched = true;
                }
                mLastCameraKeyDownTime = event.getEventTime();
            }
        }

        if (launched) {
            if (DEBUG_INPUT) Slog.d(TAG, "launchQuickCamera, " + "launched = " + launched
                    + ", doubleTapInterval = " + doubleTapInterval);
            launchQuickCamera();
        }
    }
    /* @} */

    /* SPRD: long press back, exit screen pin mode 
       In screen pinning, double tap recent and back to unpin @ { 
    */
    @Override
    void backLongPress() {
        if (mLongPressOnBackBehavior == LONG_PRESS_BACK_CANCEL_LOCKTASK) {
            try {
                IActivityManager activityManager = ActivityManager.getService();
                if (activityManager.isInLockTaskMode()) {
                    activityManager.stopSystemLockTaskMode();
                    mBackKeyHandled = true;
                }
            } catch (RemoteException e) {
                Slog.w(TAG, "Unable to reach activity manager", e);
            }
        } else {
            super.backLongPress();
        }
    }

    void handleLongPressOnHome(int deviceId) {
        if (hasInPowerUtrlSavingMode()) {
            Slog.d(TAG,"In power ULTRA saving mode:"
                    + " Do not allow to response Long press on Home key");
            return;
        }
        super.handleLongPressOnHome(deviceId);
    }

    @Override
    public int interceptKeyBeforeQueueing(KeyEvent event, int policyFlags) {
        if (!mSystemBooted) {
            // If we have not yet booted, don't let key events do anything.
            return 0;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK:
                isKeyBackDown = event.getAction() == KeyEvent.ACTION_DOWN;
                break;
            case KeyEvent.KEYCODE_APP_SWITCH:
                isKeyRecentDown = event.getAction() == KeyEvent.ACTION_DOWN;
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
                if(SystemProperties.getBoolean(SUBSIDY_LOCK_STATE, false)) {
                    return 0;
                }
                break;
        }
        return super.interceptKeyBeforeQueueing(event, policyFlags);
    }

    private boolean isKeyBackDown;
    private boolean isKeyRecentDown;

    private boolean handleLongPressBackRecents(KeyEvent event) {
        if (!SystemProperties.getBoolean("ro.notsupport.hwpin", false)) {
            if (isKeyRecentDown && isKeyBackDown) {
                mAppSwitchKeyHandled = true;
                try {
                    if (DEBUG_INPUT) Slog.d(TAG, "handleLongPressBackRecents called");
                    IActivityManager activityManager = ActivityManager.getService();
                    if (activityManager.isInLockTaskMode()) {
                        activityManager.stopSystemLockTaskMode();
                        return true;
                    }
                } catch (Exception e) {
                    Slog.d(TAG, "LongPressBackRecents Fail !", e);
                    return false;
                }
            }
        }
        return false;
    }
    /* @} */

    /* SPRD: add pocket mode acquirement @ { */
    private long handleTouchModeKey(KeyEvent event) {
        final int keyCode = event.getKeyCode();
        final int flags = event.getFlags();
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (func.isPocketModeEnabled() && (flags & KeyEvent.FLAG_FALLBACK) == 0) {
            if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP
                    || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
                if (down){
                    return func.showBackground();
                }
            }
        }
        return 0;
    }

    @Override
    public void init(Context context, IWindowManager windowManager,
            WindowManagerFuncs windowManagerFuncs) {
        super.init(context, windowManager, windowManagerFuncs);
        func = new DisableTouchModeFunc(context);
        registerSprdSensors();
    }

    DisableTouchModeFunc func;

    private void hideDisableTouchModePanel() {
        func.hideTouchModePanel();
    }

    private void showDisableTouchModePanel() {
        func.showTouchModePanel();
    }

    private void registerSprdSensors() {
        func.registerPocketEventListener(mContext);
    }

    private void updateSprdSettings(ContentResolver resolver) {
        if (mSystemReady) {
            boolean pocketModeEnabledSetting = Settings.Global.getInt(resolver,
                     TOUCH_DISABLE, 0) != 0;
            if (func.isPocketModeEnabled() != pocketModeEnabledSetting) {
                func.setPocketModeEnabled(pocketModeEnabledSetting);
                func.updatePocketEventListenerLp();
            }
        }
    }

    public void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        updateSprdSettings(resolver);
        super.updateSettings();
    }

    @Override
    public void screenTurnedOff() {
        hideDisableTouchModePanel();
        super.screenTurnedOff();
    }

    @Override
    public void screenTurningOn(final ScreenOnListener screenOnListener) {
        showDisableTouchModePanel();
        super.screenTurningOn(screenOnListener);
    }

    private boolean mQuickStepEnabled;
    private ComponentName mRecentsComponentName;


    void updateQuickStepStatus() {
        final Intent quickStepIntent = new Intent(ACTION_QUICKSTEP)
                .setPackage(mRecentsComponentName.getPackageName());
        mQuickStepEnabled = mContext.getPackageManager().resolveServiceAsUser(quickStepIntent,
                MATCH_DIRECT_BOOT_UNAWARE, mCurrentUserId) != null;
        Slog.i(TAG, "updateQuickStepStatus  mQuickStepEnabled=" + mQuickStepEnabled);
    }

    private final BroadcastReceiver mLauncherStateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateQuickStepStatus();
        }
    };

    void registerSprdIntentFilter() {
        mRecentsComponentName = ComponentName.unflattenFromString(
                mContext.getString(com.android.internal.R.string.config_recentsComponentName));
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addDataScheme("package");
        filter.addDataSchemeSpecificPart(mRecentsComponentName.getPackageName(),
                PatternMatcher.PATTERN_LITERAL);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        Slog.i(TAG, "registerSprdIntentFilter");
        mContext.registerReceiver(mLauncherStateChangedReceiver, filter);
    }
    /* @} */

}
