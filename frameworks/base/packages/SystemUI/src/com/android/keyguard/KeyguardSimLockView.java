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

import com.android.internal.telephony.ITelephonyEx;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.PhoneConstants;
import com.android.keyguard.util.SimLockUtil;
import com.android.sprd.telephony.uicc.IccCardStatusEx;
import com.android.sprd.telephony.RadioInteractor;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.lang.Exception;
/**
 * Displays a PIN pad for unlocking.
 */
public class KeyguardSimLockView extends KeyguardPinBasedInputView {
    private static final String TAG = "KeyguardSimLockView";
    private static final boolean DEBUG = true;

    private ProgressDialog mSimUnlockProgressDialog = null;
    private CheckSimLockThread mCheckSimLockThread;

    // Below flag is set to true during power-up or when a new SIM card inserted on device.
    // When this is true and when SIM card is SIMLOCK locked state, on SIMLOCK lock screen, message would
    // be displayed to inform user about the number of remaining SIMLOCK attempts left.
    private int mRemainingAttempts = -1;
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private TextView mSimlockTitleView;
    private TextView mDismissButton;

    private static final int PASSWORD_LENGTH_MIN = 8;
    private static final int PASSWORD_LENGTH_MAX = 16;

    private static int mUnlockType = IccCardStatusEx.UNLOCK_NETWORK;
    private static State mState = IccCardConstants.State.NETWORK_LOCKED;
    private RadioInteractor mRadioInteractor = null;

    KeyguardUpdateMonitorCallback mUpdateMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onSimStateChanged(int subId, int slotId, State simState) {
            if (DEBUG) Log.d(TAG, "onSimStateChanged(subId=" + subId + ",state=" + simState + ")");
            if(SimLockUtil.isByNv()){
                if (DEBUG) Log.d(TAG, "onSimStateChanged(Simlock unlock by nv turned on!)");
                return;
            }
            switch(simState) {
            // If the SIM is removed, then we must remove the keyguard. It will be put up
            // again when the PUK locked SIM is re-entered.
            case ABSENT:
            case SIM_LOCKED_PERMANENTLY:
            {
                KeyguardUpdateMonitor.getInstance(getContext()).reportSimUnlocked(mSubId);
                // onSimStateChanged callback can fire when the SIMLOCK is not currently
                // active and mCallback is null.
                if (mCallback != null) {
                    mCallback.dismiss(true, KeyguardUpdateMonitor.getCurrentUser());
                }
                break;
            }
            default:
                if (simState.isSimlockLocked()){
                    if(mState != simState){
                        mRemainingAttempts = -1;
                    }
                    mState = simState;
                    mUnlockType = SimLockUtil.getUnlockTypeByState(mState);
                    if (DEBUG) Log.d(TAG, "Now state is simlock: mState= " + mState + ", mUnlockType= " + mUnlockType);
                }
                resetState();
            }
        }
    };

    public KeyguardSimLockView(Context context) {
        this(context, null);
    }

    public KeyguardSimLockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRadioInteractor = new RadioInteractor(context);
    }

    @Override
    public void resetState() {
        super.resetState();
        if (DEBUG) Log.d(TAG, "Resetting state");
        handleSubInfoChangeIfNeeded();

        showSecurityMessage();

        boolean isEsimLocked = KeyguardEsimArea.isEsimLocked(mContext, mSubId);

        KeyguardEsimArea esimButton = findViewById(R.id.keyguard_esim_area);
        esimButton.setVisibility(isEsimLocked ? View.VISIBLE : View.GONE);
    }

    private void showSecurityMessage() {
        updateTitle(mState);
        if (mRemainingAttempts >= 0) {
            mSecurityMessageDisplay.setMessage(getSimlockPwErrorMessage(
                    mRemainingAttempts, true));
            return;
        }
        mRemainingAttempts = mRadioInteractor.getSimLockRemainTimes(mUnlockType, 0);
        mSecurityMessageDisplay.setMessage(getSimlockPwErrorMessage(
                mRemainingAttempts, true));
    }

    private void updateTitle(State state){
        Log.d(TAG, "updateTitle: state = " + state);
        mSimlockTitleView.setText(SimLockUtil.getTitleByState(state));
    }

    private void handleSubInfoChangeIfNeeded() {
        KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(mContext);
        int subId = monitor.getNextSubIdForState(mState);
        Log.d(TAG, "handleSubInfoChangeIfNeeded: mState = " + mState + ", subId = " + subId);
        if (subId != mSubId && SubscriptionManager.isValidSubscriptionId(subId)) {
            mSubId = subId;
            mRemainingAttempts = -1;
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resetState();
    }

    @Override
    protected int getPromptReasonStringRes(int reason) {
        return 0;
    }

    private String getSimlockPwErrorMessage(int attemptsRemaining, boolean isDefault) {
        String displayMessage;
        int msgId;
        if (attemptsRemaining == 0) {
            displayMessage = getContext().getString(R.string.kg_password_wrong_simlock_code_pukked);
        } else if (attemptsRemaining > 0) {
            msgId = isDefault ? R.plurals.kg_password_default_simlock_message :
                     R.plurals.kg_password_wrong_simlock_code;
            displayMessage = getContext().getResources()
                    .getQuantityString(msgId, attemptsRemaining, attemptsRemaining);
        } else {
            msgId = isDefault ? R.string.kg_simlock_instructions : R.string.kg_password_simlock_failed;
            displayMessage = getContext().getString(msgId);
        }
        if (KeyguardEsimArea.isEsimLocked(mContext, mSubId)) {
            displayMessage = getResources()
                    .getString(R.string.kg_sim_lock_esim_instructions, displayMessage);
        }
        if (DEBUG) Log.d(TAG, "getSimlockPwErrorMessage:"
                + " attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    @Override
    protected boolean shouldLockout(long deadline) {
        return false;
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.simLockEntry;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (mEcaView instanceof EmergencyCarrierArea) {
            Log.v(TAG, "onFinishInflate: is an EmergencyCarrierArea");
            ((EmergencyCarrierArea) mEcaView).setCarrierTextVisible(true);
        }
        mSimlockTitleView = findViewById(R.id.keyguard_simlock_title);
        mDismissButton = findViewById(R.id.kg_simlock_dismiss_button);
        mDismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "dismiss SimLock view");
                doHapticKeyClick();
                KeyguardSimLockMonitor.getInstance(mContext).setSimLockCanceled(true);
                KeyguardSimLockMonitor.getInstance(getContext()).reportSimLockCanceled(mSubId);
                mCallback.dismiss(true, KeyguardUpdateMonitor.getCurrentUser());
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mUpdateMonitorCallback);
        if(SimLockUtil.isByNv()){
            mState = KeyguardSimLockMonitor.getInstance(mContext).getNvState();
            mUnlockType = SimLockUtil.getUnlockTypeByState(mState);
            resetState();
        }
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
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimLockThread extends Thread {
        private final String mPin;
        private int mSubId;

        protected CheckSimLockThread(String pin, int subId) {
            mPin = pin;
            mSubId = subId;
        }

        abstract void onSimCheckResponse(final int result, final int attemptsRemaining);

        @Override
        public void run() {
            try {
                if (DEBUG) {
                    Log.d(TAG, "call supplySimlockReportResultForPhone: mUnlockType=" + mUnlockType);
                }
                final int[] result = ITelephonyEx.Stub.asInterface(ServiceManager
                        .checkService("phone_ex")).supplySimlockReportResultForPhone(0, false, mPin, mUnlockType);
                if (DEBUG) {
                    Log.d(TAG, "supplySimlockReportResultForPhone returned: " + result[0] + " " + result[1]);
                }
                post(new Runnable() {
                    @Override
                    public void run() {
                        onSimCheckResponse(result[0], result[1]);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException for supplySimlockReportResultForPhone:", e);
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

    @Override
    protected void verifyPasswordAndUnlock() {
        String entry = mPasswordEntry.getText();
        Log.d(TAG, "verifyPasswordAndUnlock: " + entry);
        if (entry.length() < PASSWORD_LENGTH_MIN || entry.length() > PASSWORD_LENGTH_MAX) {
            // otherwise, display a message to the user, and don't submit.
            mSecurityMessageDisplay.setMessage(R.string.kg_invalid_simlock_pin_hint);
            resetPasswordText(true /* animate */, true /* announce */);
            mCallback.userActivity();
            return;
        }

        getSimUnlockProgressDialog().show();

        if (mCheckSimLockThread == null) {
            mCheckSimLockThread = new CheckSimLockThread(mPasswordEntry.getText(), mSubId) {
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
                                if (SimLockUtil.isByNv()) {
                                    KeyguardSimLockMonitor.getInstance(getContext()).reportUnlockedByNV();
                                } else {
                                    KeyguardUpdateMonitor.getInstance(getContext()).reportSimUnlocked(mSubId);
                                    KeyguardSimLockMonitor.getInstance(getContext()).reportUnlockedBySIM();
                                }
                                mRemainingAttempts = -1;
                                if (mCallback != null) {
                                    mCallback.dismiss(true, KeyguardUpdateMonitor.getCurrentUser());
                                }
                            } else {
                                mSecurityMessageDisplay.setMessage(
                                        getSimlockPwErrorMessage(attemptsRemaining, false));

                                if (DEBUG) Log.d(TAG, "verifyPasswordAndUnlock "
                                        + " mCheckSimLockThread.onSimCheckResponse: " + result
                                        + " attemptsRemaining=" + attemptsRemaining);
                            }
                            mCallback.userActivity();
                            mCheckSimLockThread = null;
                        }
                    });
                }
            };
            mCheckSimLockThread.start();
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
}

