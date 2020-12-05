package com.android.server;

import android.app.StatusBarManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telephony.TelephonyManager;
import android.util.MutableBoolean;
import android.util.Slog;
import android.view.KeyEvent;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.server.statusbar.StatusBarManagerInternal;

public class SprdGestureLauncherService extends GestureLauncherService {
    private static final boolean DBG = true;
    static final String TAG = "SprdGestureLauncherService";
    boolean mEmergencyFeatureEnabled = SystemProperties.getBoolean(
            "gesture.enable_sos_launch", false);
    private static final long CAMERA_POWER_DOUBLE_TAP_MAX_TIME_MS = 300;
    private long mMoreAheadPowerDown;
    private long mLastPowerDown;
    private Context mContext;
    private boolean mCameraLaunchPending = false;
    Handler mHandler = new Handler();

    public SprdGestureLauncherService(Context context) {
        super(context);
        mContext = context;
    }

    public void onStart() {
        LocalServices.addService(SprdGestureLauncherService.class, this);
    }

    private boolean isOkToCall() {
        return mEmergencyFeatureEnabled && isMccSatisfactory();
    }

    private boolean isMccSatisfactory() {
        String mccMnc = TelephonyManager.getDefault().getSimOperator();
        String mcc = "";
        if (mccMnc != null && mccMnc.length() > 2) {
            mcc = mccMnc.substring(0, 3);
        }
        return mcc.equals("404") || mcc.equals("405");
    }

    public boolean interceptPowerKeyDown(KeyEvent event, boolean interactive,
            MutableBoolean outLaunched, boolean isScreenOn, boolean isPowerUtrl) {
        if (!isOkToCall() && !isPowerUtrl) {
            return super.interceptPowerKeyDown(event, interactive, outLaunched,isScreenOn);
        }
        boolean launchedCamera = false;
        boolean launchedEmergency = false;
        boolean intercept = false;
        long doubleTapInterval;
        long tripleTapInterval;
        synchronized (this) {
            doubleTapInterval = event.getEventTime() - mLastPowerDown;
            tripleTapInterval = mLastPowerDown - mMoreAheadPowerDown;
            if (isOkToCall()
                    && doubleTapInterval < CAMERA_POWER_DOUBLE_TAP_MAX_TIME_MS
                    && tripleTapInterval < CAMERA_POWER_DOUBLE_TAP_MAX_TIME_MS) {
                launchedEmergency = true;
                intercept = interactive;
            } else if (mCameraDoubleTapPowerEnabled
                    && doubleTapInterval < CAMERA_POWER_DOUBLE_TAP_MAX_TIME_MS &&!isPowerUtrl) {
                launchedCamera = true;
                intercept = interactive;
            }
            mMoreAheadPowerDown = mLastPowerDown;
            mLastPowerDown = event.getEventTime();
        }
        if (launchedEmergency) {
            if (mCameraLaunchPending) {
                mHandler.removeCallbacks(mCameraLaunchRunnable);
            }
            Slog.i(TAG, "Power button triple tap gesture detected, launching emergency. Interval="
                    + doubleTapInterval + "ms " + tripleTapInterval + "ms");
            Intent intent = new Intent(Intent.ACTION_CALL_EMERGENCY);
            intent.setData(Uri.fromParts(PhoneAccount.SCHEME_TEL, "112", null));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            //avoid "... without a qualified user"
            mContext.startActivityAsUser(intent, UserHandle.CURRENT);
        } else if (launchedCamera && !isPowerUtrl) {
            Slog.i(TAG, "Power button double tap gesture detected, launching camera. Interval="
                    + doubleTapInterval + "ms");
            boolean userSetupComplete = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0, UserHandle.USER_CURRENT) != 0;
            if (!userSetupComplete) {
                if (DBG) Slog.d(TAG, String.format(
                        "userSetupComplete = %s, ignoring camera launch gesture.",
                        userSetupComplete));
                launchedCamera = false;
            } else {
                mCameraLaunchPending = true;
                mHandler.postDelayed(mCameraLaunchRunnable, CAMERA_POWER_DOUBLE_TAP_MAX_TIME_MS);
            }
        }
        MetricsLogger.histogram(mContext, "power_double_tap_interval", (int) doubleTapInterval);
        outLaunched.value = launchedCamera || launchedEmergency;
        return intercept && (launchedCamera || launchedEmergency);
    }

    /**
     * @return true if camera was launched, false otherwise.
     */
    private final Runnable mCameraLaunchRunnable = new Runnable() {
        @Override
        public void run() {
            mCameraLaunchPending = false;
            boolean launched = handleCameraGesture(false /* useWakelock */,
                    StatusBarManager.CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP);
            if (launched) {
                MetricsLogger.action(mContext, MetricsEvent.ACTION_DOUBLE_TAP_POWER_CAMERA_GESTURE,
                        (int) CAMERA_POWER_DOUBLE_TAP_MAX_TIME_MS);//TODO
            }
        }
    };
}
