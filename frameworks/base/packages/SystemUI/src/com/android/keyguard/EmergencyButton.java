/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.keyguard;

import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.util.EmergencyAffordanceManager;
import com.android.settingslib.WirelessUtils;
import com.android.systemui.SysUiServiceProvider;
import com.android.systemui.statusbar.phone.StatusBar;

/**
 * This class implements a smart emergency button that updates itself based
 * on telephony state.  When the phone is idle, it is an emergency call button.
 * When there's a call in progress, it presents an appropriate message and
 * allows the user to return to the call.
 */
public class EmergencyButton extends Button {
    private static final Intent INTENT_EMERGENCY_DIAL = new Intent()
            .setAction("com.android.phone.EmergencyDialer.DIAL")
            .setPackage("com.android.phone")
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);

    private static final boolean VDBG = true;
    private static final String LOG_TAG = "EmergencyButton";
    private final EmergencyAffordanceManager mEmergencyAffordanceManager;

    private int mDownX;
    private int mDownY;
    /* UNISOC: Bug886808 add the feature of calling 112 SOS after press long click @{ */
    private final boolean mEnableSimpleSOS;
    private String[] mSimpleSosAllowMccs;
    /* @} */
    KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onSimStateChanged(int subId, int slotId, State simState) {
            updateEmergencyCallButton();
        }

        @Override
        public void onPhoneStateChanged(int phoneState) {
            updateEmergencyCallButton();
        }

        /* For SubsidyLock feature @{ */
        @Override
        public void onSubsidyDeviceLock(int mode) {
            updateEmergencyCallButton();
        }

        @Override
        public void onSubsidyEnterCode() {
            updateEmergencyCallButton();
        }
        /* @} */
    };
    private boolean mLongPressWasDragged;

    public interface EmergencyButtonCallback {
        public void onEmergencyButtonClickedWhenInCall();
    }

    private LockPatternUtils mLockPatternUtils;
    private PowerManager mPowerManager;
    private EmergencyButtonCallback mEmergencyButtonCallback;

    private final boolean mIsVoiceCapable;
    private final boolean mEnableEmergencyCallWhileSimLocked;

    /* UNISOC: bug 692478 FEATURE_SHOW_EMERGENCY_BTN_IN_KEYGUARD @{ */
    private final boolean mShowBtnInLockScreenForCarrier;
    private final boolean mShowBtnInLockScreenForSpecial;
    private IntentFilter mIntentFilter;
    private ServiceStateChangeReceiver mServiceStateChangeReceiver;
    // UNISOC: bug692478 during power on the phone, we cannot read ss from KeyguardUpdateMonitor,
    // we can use mStickySS to save servicestate broadcast the last time.
    private ServiceState mStickySS = null;
    /* @} */
    /* UNISOC: 668195 modify double click Emergency splash @{ */
    private final int MIN_CLICK_DELAY_TIME = 1000;
    private long lastClickTime = 0;
    /* @} */

    public EmergencyButton(Context context) {
        this(context, null);
    }

    public EmergencyButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIsVoiceCapable = context.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);
        mEnableEmergencyCallWhileSimLocked = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enable_emergency_call_while_sim_locked);
        /* UNISOC: bug 692478 FEATURE_SHOW_EMERGENCY_BTN_IN_KEYGUARD @{ */
        mShowBtnInLockScreenForCarrier = mContext.getResources().getBoolean(
                R.bool.config_show_emergency_button_on_lockscreen);
        mShowBtnInLockScreenForSpecial = mContext.getResources().getBoolean(
                R.bool.config_show_emergency_button_for_special);
        /* @} */

        mEmergencyAffordanceManager = new EmergencyAffordanceManager(context);
        /* UNISOC: Bug886808 add the feature of calling 112 SOS after press long click @{ */
        mEnableSimpleSOS = mContext.getResources().getBoolean(
                R.bool.config_simple_sos_enable);
        mSimpleSosAllowMccs = mContext.getResources().getStringArray(
                R.array.config_simple_sos_mcc_list);
        Log.d(LOG_TAG, "mEnableSimpleSOS : " + mEnableSimpleSOS);
        /* @} */
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
        mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        if (mServiceStateChangeReceiver == null) {
            mServiceStateChangeReceiver = new ServiceStateChangeReceiver();
        }
        mContext.registerReceiver(mServiceStateChangeReceiver, mIntentFilter);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
        if(mServiceStateChangeReceiver != null){
            mContext.unregisterReceiver(mServiceStateChangeReceiver);
            mServiceStateChangeReceiver = null;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLockPatternUtils = new LockPatternUtils(mContext);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                    /* UNISOC: BUG 654179 @{ */
                    /* UNISOC: BUG 725440 @{ */
                    if (!EmergencyButton.this.isVisibleToUser()) {
                        return;
                    }
                    /* @} */
                takeEmergencyCallAction();
            }
        });
        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                    /* UNISOC: BUG 725440 @{ */
                    if (!EmergencyButton.this.isVisibleToUser()) {
                        return false;
                    }
                    /* @} */

                /* UNISOC: Bug886808 add the feature of calling 112 SOS after press long click @{ */
                Log.d(LOG_TAG, "call onLongClick mcc : " + getMcc());
                if (mEnableSimpleSOS && getMcc() != null
                        && (getMcc().equals(mSimpleSosAllowMccs[0])
                        || getMcc().equals(mSimpleSosAllowMccs[1]))) {
                    Intent callIntent = new Intent(
                            Intent.ACTION_CALL_EMERGENCY);
                    callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    callIntent.setData(Uri.fromParts(PhoneAccount.SCHEME_TEL,
                            mContext.getResources()
                                    .getString(R.string.config_simple_sos_emergency_number), null));
                    mContext.startActivity(callIntent);
                }
                /* @} */
                if (!mLongPressWasDragged
                        && mEmergencyAffordanceManager.needsEmergencyAffordance()) {
                    mEmergencyAffordanceManager.performEmergencyCall();
                    return true;
                }
                return false;
            }
        });
        updateEmergencyCallButton();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mDownX = x;
            mDownY = y;
            mLongPressWasDragged = false;
        } else {
            final int xDiff = Math.abs(x - mDownX);
            final int yDiff = Math.abs(y - mDownY);
            int touchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
            if (Math.abs(yDiff) > touchSlop || Math.abs(xDiff) > touchSlop) {
                mLongPressWasDragged = true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performLongClick() {
        return super.performLongClick();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isInsideKeyguardBottomArea()) {
            if (VDBG) {
                Log.d(LOG_TAG, "onConfigurationChanged text_size = " + getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.text_size_small_material) );
            }
            /* UNISOC: add for bug 701492 @{ */
            this.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(
                            com.android.internal.R.dimen.text_size_small_material));
            /* @} */
        }
        updateEmergencyCallButton();
    }

    /* UNISOC: BUG 725440 @{ */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // TODO Auto-generated method stub
        if (!isVisibleToUser()) {
            return false;
        }
        return super.dispatchTouchEvent(event);
    }
    /* @} */

    /**
     * Shows the emergency dialer or returns the user to the existing call.
     */
    public void takeEmergencyCallAction() {
        MetricsLogger.action(mContext, MetricsEvent.ACTION_EMERGENCY_CALL);
        // TODO: implement a shorter timeout once new PowerManager API is ready.
        // should be the equivalent to the old userActivity(EMERGENCY_CALL_TIMEOUT)
        mPowerManager.userActivity(SystemClock.uptimeMillis(), true);
        try {
            ActivityManager.getService().stopSystemLockTaskMode();
        } catch (RemoteException e) {
            Slog.w(LOG_TAG, "Failed to stop app pinning");
        }
        if (isInCall()) {
            resumeCall();
            if (mEmergencyButtonCallback != null) {
                mEmergencyButtonCallback.onEmergencyButtonClickedWhenInCall();
            }
        } else {
            KeyguardUpdateMonitor.getInstance(mContext).reportEmergencyCallAction(
                    true /* bypassHandler */);
            //UNISOC: add for bug 960589
            if(isCurrentEmergencyCallActivity()) {
                SysUiServiceProvider.getComponent(mContext, StatusBar.class).onBackPressed();
            } else {
                getContext().startActivityAsUser(INTENT_EMERGENCY_DIAL,
                        ActivityOptions.makeCustomAnimation(getContext(), 0, 0).toBundle(),
                        new UserHandle(KeyguardUpdateMonitor.getCurrentUser()));
            }
        }
    }

    //UNISOC: add for bug 960589
    private boolean isCurrentEmergencyCallActivity() {
        String topActivityClass = null;
        ActivityManager activityManager = (ActivityManager) (getContext()
                .getSystemService(android.content.Context.ACTIVITY_SERVICE));
        try {
            List<RunningTaskInfo> runningTaskInfos = activityManager
                    .getRunningTasks(1);
            if (runningTaskInfos != null) {
                ComponentName f = runningTaskInfos.get(0).topActivity;
                topActivityClass = f.getClassName();
            }
        } catch (Exception e) {
        }
        return topActivityClass != null && topActivityClass.contains("EmergencyDialer");
    }

    private void updateEmergencyCallButton() {
        boolean visible = false;
        if (mIsVoiceCapable) {
            Log.d(LOG_TAG, "updateEmergencyCallButton");
            if (isInsideKeyguardBottomArea()) {
                // UNISOC: bug 692478 FEATURE_SHOW_EMERGENCY_BTN_IN_KEYGUARD
                Log.d(LOG_TAG, "updateEmergencyCallButton isInsideKeyguardBottomArea");
                visible = mShowBtnInLockScreenForCarrier
                        && !makeEmergencyInvisible();
            } else {
                if (isInCall()) {
                    visible = true; // always show "return to call" if phone is off-hook
                } else {
                    final boolean simLocked = KeyguardUpdateMonitor.getInstance(mContext)
                            .isSimPinVoiceSecure();
                    // Unisoc: Support SimLock
                    final boolean isSimLockStateByUser = KeyguardSimLockMonitor.isSimLockStateByUser();
                    // For SubsidyLock feature
                    final boolean subSidyLocked = KeyguardSubsidyLockController.getInstance(mContext)
                            .isSubsidyLock();
                    if (simLocked) {
                        // Some countries can't handle emergency calls while SIM is locked.
                        visible = mEnableEmergencyCallWhileSimLocked;
                    }
                    //Unisoc: Need to handle emergency calls while state is SIM LOCK.
                    else if(isSimLockStateByUser) {
                        visible = true;
                    } else if (subSidyLocked) { // When in SubsidyLock state, need show emergency button
                        visible = true;
                    } else {
                        // Only show if there is a secure screen (pin/pattern/SIM pin/SIM puk);
                        visible = mLockPatternUtils.isSecure(KeyguardUpdateMonitor.getCurrentUser());
                    }
                }
            }
        }
        Log.d(LOG_TAG, "updateEmergencyCallButton visible = " +visible);
        if (visible) {
            setVisibility(View.VISIBLE);

            int textId;
            if (isInCall()) {
                textId = com.android.internal.R.string.lockscreen_return_to_call;
            } else {
                // UNISOC: modify for bug910795
                if (!isEmergencyCallAllowed()) {
                    setClickable(false);
                    // UNISOC: add for bug915779
                    setLongClickable(false);
                    textId = R.string.lockscreen_no_available_network;
                } else {
                    /* UNISOC: BUG 654179 @{ */
                    textId = com.android.internal.R.string.lockscreen_emergency_call;
                    setText(textId);
                    setClickable(true);
                    // UNISOC: add for bug915779
                    setLongClickable(true);
                    /* @} */
                }
            }
            setText(textId);
            invalidate();
        } else {
            setVisibility(View.GONE);
        }
    }

    public void setCallback(EmergencyButtonCallback callback) {
        mEmergencyButtonCallback = callback;
    }

    /**
     * Resumes a call in progress.
     */
    private void resumeCall() {
        getTelecommManager().showInCallScreen(false);
    }

    /**
     * @return {@code true} if there is a call currently in progress.
     */
    private boolean isInCall() {
        return getTelecommManager().isInCall();
    }

    private TelecomManager getTelecommManager() {
        return (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }

    public boolean makeEmergencyInvisible() {
        if (!mShowBtnInLockScreenForSpecial) {
            return false;
        }
        boolean hasIccCard = false;
        TelephonyManager telephonyManager = TelephonyManager.from(mContext);
        int numPhones = telephonyManager.getPhoneCount();
        for (int i = 0; i < numPhones; i++) {
            hasIccCard |= telephonyManager.hasIccCard(i);
        }
        boolean isAirPlaneMode = (Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1);
        if (isAirPlaneMode || !hasIccCard) {
            return true;
        }
        return false;
    }

    /* UNISOC: FEATURE_SHOW_EMERGENCY_BTN_IN_KEYGUARD @{ */
    private boolean isInsideKeyguardBottomArea() {
        return "emergency_call_btn_in_keyguard".equals(getTag());
    }

    private boolean isEmergencyCallAllowed() {
        List<SubscriptionInfo> subs = KeyguardUpdateMonitor.getInstance(mContext)
                .getSubscriptionInfo(false);
        final int N = subs.size();
        ServiceState ss = null;
        if (VDBG) Log.d(LOG_TAG, "isEmergencyCallAllowed, N: " + N);
        if (N == 0) {
            // no active subinfo, it may indicate that there is no sim.
            return true;
        }

        for (int i = 0; i < N; i++) {
            int subId = subs.get(i).getSubscriptionId();
            ss = KeyguardUpdateMonitor.getInstance(mContext)
                    .mServiceStates.get(subId);
            if (VDBG) Log.d(LOG_TAG, "subId: " + subId + "SS: " + ss);
            if (ss != null) {
                if (ss.isEmergencyOnly() || hasService(ss)) {
                    return true;
                }
            }
        }
        if(mStickySS != null) {
            if (VDBG) Log.d(LOG_TAG, "mStickySS: " + mStickySS);
            if (mStickySS.isEmergencyOnly() || hasService(mStickySS)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasService(ServiceState ss) {
        if (ss != null) {
            switch (ss.getState()) {
                case ServiceState.STATE_OUT_OF_SERVICE:
                    return false;
                case ServiceState.STATE_POWER_OFF:
                    if (WirelessUtils.isAirplaneModeOn(mContext)) {
                        return true;
                    }
                default:
                    return true;
            }
        }
        return false;
    }

    private class ServiceStateChangeReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyIntents.ACTION_SERVICE_STATE_CHANGED.equals(intent.getAction())) {
                mStickySS = ServiceState.newFromBundle(intent.getExtras());
                if (VDBG) Log.d(LOG_TAG, "ServiceStateChangedIntent: " + intent.getAction() +
                        " mStickySS= " + mStickySS);
                updateEmergencyCallButton();
            }
        }
    }

    /* UNISOC: Bug886808 add the feature of calling 112 SOS after press long click @{ */
    private String getMcc () {
        String mccMnc = TelephonyManager.getDefault().getSimOperator();
        String mcc = "";
        if (mccMnc != null && mccMnc.length() > 2) {
            mcc = mccMnc.substring(0, 3);
        }
        return mcc;
    }
    /* @} */
}
