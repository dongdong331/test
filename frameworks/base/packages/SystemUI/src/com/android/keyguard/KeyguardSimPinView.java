/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.PhoneConstants;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.euicc.EuiccManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * Displays a PIN pad for unlocking.
 */
public class KeyguardSimPinView extends KeyguardPinBasedInputView {
    private static final String LOG_TAG = "KeyguardSimPinView";
    private static final boolean DEBUG = KeyguardConstants.DEBUG_SIM_STATES;
    public static final String TAG = "KeyguardSimPinView";
    /* UNISOC: add for bug915352 @{ */
    private static final String PROPERTY_PIN_REMAINTIMES = "vendor.sim.pin.remaintimes";
    private static final int INVALID_REMAINTIMES = -10;
    /* @} */

    private ProgressDialog mSimUnlockProgressDialog = null;
    private CheckSimPin mCheckSimPinThread;

    // Below flag is set to true during power-up or when a new SIM card inserted on device.
    // When this is true and when SIM card is PIN locked state, on PIN lock screen, message would
    // be displayed to inform user about the number of remaining PIN attempts left.
    private boolean mShowDefaultMessage = true;
    private int mRemainingAttempts = -1;
    private AlertDialog mRemainingAttemptsDialog;
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private ImageView mSimImageView;

    KeyguardUpdateMonitorCallback mUpdateMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onSimStateChanged(int subId, int slotId, State simState) {
            if (DEBUG) Log.v(TAG, "onSimStateChanged(subId=" + subId + ",state=" + simState + ")");
                switch(simState) {
                    // If the SIM is removed, then we must remove the keyguard. It will be put up
                    // again when the PUK locked SIM is re-entered.
                    case ABSENT: {
                        // UNISOC: add for bug 802572 & 900000 subId must be same ,or will dismiss the normal one.
                        if (mSubId == subId) {
                            Log.d(TAG, "subId equal,mSubId = " + mSubId);
                            // UNISOC: add for bug 802572
                            KeyguardUpdateMonitor.getInstance(
                                    getContext()).reportSimUnlocked(mSubId, simState);
                            // onSimStateChanged callback can fire when the SIM PIN lock is not currently
                            // active and mCallback is null.
                            if (mCallback != null) {
                                mCallback.dismiss(true, KeyguardUpdateMonitor.getCurrentUser());
                            }
                        }
                        break;
                    }
                    case READY: {
                        mRemainingAttempts = -1;
                        resetState();
                        /* UNISOC: add for bug908989 @{ */
                        if (mSubId == subId) {
                            if (mCallback != null) {
                                mCallback.dismiss(true, KeyguardUpdateMonitor.getCurrentUser());
                            }
                        }
                        /* @} */
                        break;
                    }
                    default:
                        resetState();
                }
        }
    };

    public KeyguardSimPinView(Context context) {
        this(context, null);
    }

    public KeyguardSimPinView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void resetState() {
        super.resetState();
        if (DEBUG) Log.v(TAG, "Resetting state");
        handleSubInfoChangeIfNeeded();
        if (mShowDefaultMessage) {
            showDefaultMessage();
        }
        boolean isEsimLocked = KeyguardEsimArea.isEsimLocked(mContext, mSubId);

        KeyguardEsimArea esimButton = findViewById(R.id.keyguard_esim_area);
        esimButton.setVisibility(isEsimLocked ? View.VISIBLE : View.GONE);
    }

    private void showDefaultMessage() {
        if (mRemainingAttempts >= 0) {
            mSecurityMessageDisplay.setMessage(getPinPasswordErrorMessage(
                    mRemainingAttempts, true));
            return;
        }

        boolean isEsimLocked = KeyguardEsimArea.isEsimLocked(mContext, mSubId);
        int count = TelephonyManager.getDefault().getSimCount();
        Resources rez = getResources();
        String msg;
        int color = Color.WHITE;
        /**
         * UNISOC: Before get the remaining times,don't show message without remaining times.
         *
        if (count < 2) {
            msg = rez.getString(R.string.kg_sim_pin_instructions);
        } else {
            SubscriptionInfo info = KeyguardUpdateMonitor.getInstance(mContext).
                    getSubscriptionInfoForSubId(mSubId);
            CharSequence displayName = info != null ? info.getDisplayName() : ""; // don't crash
            msg = rez.getString(R.string.kg_sim_pin_instructions_multi, displayName);
            if (info != null) {
                color = info.getIconTint();
            }
        }

        if (isEsimLocked) {
            msg = rez.getString(R.string.kg_sim_lock_esim_instructions, msg);
        }

        mSecurityMessageDisplay.setMessage(msg);
         */
        /* UNISOC: add featrue pin remaining times @{ */
        SubscriptionInfo info = KeyguardUpdateMonitor.getInstance(mContext).
                getSubscriptionInfoForSubId(mSubId);
        if (info != null) {
            color = info.getIconTint();
        }
        /* @} */
        mSimImageView.setImageTintList(ColorStateList.valueOf(color));

        /* UNISOC: add for bug915352 @{ */
        int slotId = SubscriptionManager.getSlotIndex(mSubId);
        if (!SubscriptionManager.isValidPhoneId(slotId)) {
            Log.d(LOG_TAG, "Invalid slotId " + slotId);
            return;
        }
        int remainTimesFromProp = getPinRemainTimes();
        if (remainTimesFromProp == INVALID_REMAINTIMES) {
            // Sending empty PIN here to query the number of remaining PIN attempts
            new CheckSimPin("", mSubId) {
                void onSimCheckResponse(final int result, final int attemptsRemaining) {
                    Log.d(LOG_TAG, "onSimCheckResponse " + " dummy One result" + result +
                            " attemptsRemaining=" + attemptsRemaining);
                    if (attemptsRemaining >= 0) {
                        mRemainingAttempts = attemptsRemaining;
                        mSecurityMessageDisplay.setMessage(
                                getPinPasswordErrorMessage(attemptsRemaining, true));
                    }
                }
            }.start();
        } else if (remainTimesFromProp >= 0) {
            Log.d(LOG_TAG, "exist valid value from prop, use this value");
            mRemainingAttempts = remainTimesFromProp;
            mSecurityMessageDisplay.setMessage(
                    getPinPasswordErrorMessage(remainTimesFromProp, true));
        }
        /* @} */
    }

    /* UNISOC: add for bug915565 @{ */
    @Override
    public void onResume(int reason) {
        super.onResume(reason);
        mRemainingAttempts = getPinRemainTimes();
        resetState();
    }
    /* @} */

    private void handleSubInfoChangeIfNeeded() {
        KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(mContext);
        /* UNISOC: modify for bug905767 @{ */
        if (monitor.getSimState(mSubId) != IccCardConstants.State.PIN_REQUIRED) {
            int subId = monitor.getNextSubIdForState(IccCardConstants.State.PIN_REQUIRED);
            if (subId != mSubId && SubscriptionManager.isValidSubscriptionId(subId)) {
                /* UNISOC: add for bug903726 @{ */
                Log.d(LOG_TAG, "handleSubInfoChangeIfNeeded " + mSubId + " > " + subId);
                resetPasswordText(true, true);
                /* @} */
                mSubId = subId;
                mShowDefaultMessage = true;
                mRemainingAttempts = -1;
            }
        }
        /* @} */
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resetState();
    }

    @Override
    protected int getPromptReasonStringRes(int reason) {
        // No message on SIM Pin
        return 0;
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining, boolean isDefault) {
        String displayMessage;
        int msgId;
        if (attemptsRemaining == 0) {
            displayMessage = getContext().getString(R.string.kg_password_wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            /* UNISOC: add featrue pin remaining times @{ */
            int count = TelephonyManager.getDefault().getSimCount();
            if (count < 2) {
                msgId = isDefault ? R.plurals.kg_password_default_pin_message :
                        R.plurals.kg_password_wrong_pin_code;
                displayMessage = getContext().getResources()
                        .getQuantityString(msgId, attemptsRemaining, attemptsRemaining);
            } else {
                msgId = isDefault ? R.plurals.kg_password_default_pin_message_multi :
                        R.plurals.kg_password_wrong_pin_code_multi;
                int phoneId = SubscriptionManager.getPhoneId(mSubId);
                SubscriptionInfo info = KeyguardUpdateMonitor.getInstance(mContext)
                        .getSubscriptionInfoForSubId(mSubId);
                /* UNISOC: modify by BUG 922632 @{ */
                Log.d(LOG_TAG, "phoneId : " + phoneId + "; mSubId : " + mSubId + "; info : "
                        + info);
                if (info!= null && !SubscriptionManager.isValidPhoneId(phoneId)) {
                    phoneId = info.getSimSlotIndex();
                    Log.d(LOG_TAG, "SubscriptionInfo:"+info.toString() + "; phoneId : "
                            + phoneId);
                }
                /* @} */
                displayMessage = getContext().getResources().getQuantityString(msgId,
                        attemptsRemaining, phoneId + 1, attemptsRemaining);
            }
        /* @} */
        } else {
            msgId = isDefault ? R.string.kg_sim_pin_instructions : R.string.kg_password_pin_failed;
            displayMessage = getContext().getString(msgId);
        }
        if (KeyguardEsimArea.isEsimLocked(mContext, mSubId)) {
            displayMessage = getResources()
                    .getString(R.string.kg_sim_lock_esim_instructions, displayMessage);
        }
        if (DEBUG) Log.d(LOG_TAG, "getPinPasswordErrorMessage:"
                + " attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    @Override
    protected boolean shouldLockout(long deadline) {
        // SIM PIN doesn't have a timed lockout
        return false;
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.simPinEntry;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (mEcaView instanceof EmergencyCarrierArea) {
            ((EmergencyCarrierArea) mEcaView).setCarrierTextVisible(true);
        }
        mSimImageView = findViewById(R.id.keyguard_sim);
        // UNISOC: add for bug915575
        mPasswordEntry.setLimit(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mUpdateMonitorCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mUpdateMonitorCallback);
    }

    @Override
    public void showUsabilityHint() {
    }

    @Override
    public void onPause() {
        // dismiss the dialog.
        if (mSimUnlockProgressDialog != null) {
            mSimUnlockProgressDialog.dismiss();
            mSimUnlockProgressDialog = null;
        }
        resetPasswordText(true, true);
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPin extends Thread {
        private final String mPin;
        private int mSubId;

        protected CheckSimPin(String pin, int subId) {
            mPin = pin;
            mSubId = subId;
        }

        abstract void onSimCheckResponse(final int result, final int attemptsRemaining);

        @Override
        public void run() {
            try {
                if (DEBUG) {
                    Log.v(TAG, "call supplyPinReportResultForSubscriber(subid=" + mSubId + ")");
                }
                final int[] result = ITelephony.Stub.asInterface(ServiceManager
                        .checkService("phone")).supplyPinReportResultForSubscriber(mSubId, mPin);
                if (DEBUG) {
                    Log.v(TAG, "supplyPinReportResult returned: " + result[0] + " " + result[1]);
                }
                post(new Runnable() {
                    @Override
                    public void run() {
                        onSimCheckResponse(result[0], result[1]);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException for supplyPinReportResult:", e);
                post(new Runnable() {
                    @Override
                    public void run() {
                        onSimCheckResponse(PhoneConstants.PIN_GENERAL_FAILURE, -1);
                    }
                });
            }
        }
    }

    private Dialog getSimUnlockProgressDialog() {
        if (mSimUnlockProgressDialog == null) {
            mSimUnlockProgressDialog = new ProgressDialog(mContext);
            mSimUnlockProgressDialog.setMessage(
                    mContext.getString(R.string.kg_sim_unlock_progress_dialog_message));
            mSimUnlockProgressDialog.setIndeterminate(true);
            mSimUnlockProgressDialog.setCancelable(false);
            mSimUnlockProgressDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        }
        return mSimUnlockProgressDialog;
    }

    private Dialog getSimRemainingAttemptsDialog(int remaining) {
        String msg = getPinPasswordErrorMessage(remaining, false);
        if (mRemainingAttemptsDialog == null) {
            Builder builder = new AlertDialog.Builder(mContext);
            builder.setMessage(msg);
            builder.setCancelable(false);
            builder.setNeutralButton(R.string.ok, null);
            mRemainingAttemptsDialog = builder.create();
            mRemainingAttemptsDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        } else {
            mRemainingAttemptsDialog.setMessage(msg);
        }
        return mRemainingAttemptsDialog;
    }

    /* UNISOC: add for bug906144 @{ */
    @Override
    protected void onUserInput() {
        if (mCallback != null) {
            mCallback.userActivity();
        }
    }
    /* @} */

    @Override
    protected void verifyPasswordAndUnlock() {
        String entry = mPasswordEntry.getText();
        // change for bug 895453
        if (entry.length() < 4 || entry.length() > 8) {
            // otherwise, display a message to the user, and don't submit.
            mSecurityMessageDisplay.setMessage(R.string.kg_invalid_sim_pin_hint);
            resetPasswordText(true /* animate */, true /* announce */);
            mCallback.userActivity();
            return;
        }

        /* UNISOC: add for bug933599 @{ */
        KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(mContext);
        if (monitor.getSimState(mSubId) != IccCardConstants.State.PIN_REQUIRED) {
            Log.d(LOG_TAG, "keyguard sim state : " + monitor.getSimState(mSubId) + ";" +
                    "mSubId : " + mSubId);
            return;
        }
        /* @} */

        getSimUnlockProgressDialog().show();

        if (mCheckSimPinThread == null) {
            mCheckSimPinThread = new CheckSimPin(mPasswordEntry.getText(), mSubId) {
                @Override
                void onSimCheckResponse(final int result, final int attemptsRemaining) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            mRemainingAttempts = attemptsRemaining;
                            if (mSimUnlockProgressDialog != null) {
                                mSimUnlockProgressDialog.hide();
                            }
                            resetPasswordText(true /* animate */,
                                    result != PhoneConstants.PIN_RESULT_SUCCESS /* announce */);
                            if (result == PhoneConstants.PIN_RESULT_SUCCESS) {
                                KeyguardUpdateMonitor.getInstance(getContext())
                                        .reportSimUnlocked(mSubId);
                                mRemainingAttempts = -1;
                                mShowDefaultMessage = true;
                                if (mCallback != null) {
                                    mCallback.dismiss(true, KeyguardUpdateMonitor.getCurrentUser());
                                }
                            } else {
                                mShowDefaultMessage = false;
                                if (result == PhoneConstants.PIN_PASSWORD_INCORRECT) {
                                    // UNISOC: add featrue pin remaining times
                                    mSecurityMessageDisplay.setMessage(getPinPasswordErrorMessage(
                                            attemptsRemaining, false));
                                    if (attemptsRemaining <= 2) {
                                        // this is getting critical - show dialog
                                        getSimRemainingAttemptsDialog(attemptsRemaining).show();
                                    } else {
                                        // show message
                                        mSecurityMessageDisplay.setMessage(
                                                getPinPasswordErrorMessage(attemptsRemaining, false));
                                    }
                                } else {
                                    // "PIN operation failed!" - no idea what this was and no way to
                                    // find out. :/
                                    /* SPRD : Modify for bug 921859 @{ */
                                    int PIN_UICC_DISPOSS_FAILURE = 3;
                                    if (result != PIN_UICC_DISPOSS_FAILURE) {
                                        mSecurityMessageDisplay.setMessage(getContext().getString(
                                                R.string.kg_password_pin_failed));
                                    }
                                    /* @} */
                                }
                                if (DEBUG) Log.d(LOG_TAG, "verifyPasswordAndUnlock "
                                        + " CheckSimPin.onSimCheckResponse: " + result
                                        + " attemptsRemaining=" + attemptsRemaining);
                            }
                            mCallback.userActivity();
                            mCheckSimPinThread = null;
                        }
                    });
                }
            };
            mCheckSimPinThread.start();
        }
    }

    @Override
    public void startAppearAnimation() {
        // noop.
    }

    @Override
    public boolean startDisappearAnimation(Runnable finishRunnable) {
        return false;
    }

    @Override
    public CharSequence getTitle() {
        return getContext().getString(
                com.android.internal.R.string.keyguard_accessibility_sim_pin_unlock);
    }

    /* UNISOC: add for bug915352 @{ */
    private int getPinRemainTimes() {
        int remainTimes = INVALID_REMAINTIMES;
        if (mContext != null) {
            SubscriptionInfo info = KeyguardUpdateMonitor.getInstance(mContext).
                    getSubscriptionInfoForSubId(mSubId);
            if (info != null) {
                String propertyValue = TelephonyManager.from(mContext)
                        .getTelephonyProperty(info.getSimSlotIndex(), PROPERTY_PIN_REMAINTIMES, "");
                if (!TextUtils.isEmpty(propertyValue)) {
                    try {
                        remainTimes = Integer.valueOf(propertyValue);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        Log.d(LOG_TAG, "remain times " + remainTimes);
        return remainTimes;
    }
    /* @} */
}

