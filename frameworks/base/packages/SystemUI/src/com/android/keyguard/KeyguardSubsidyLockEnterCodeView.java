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
import com.android.keyguard.KeyguardPinBasedInputView;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.keyguard.R;
import com.android.keyguard.KeyguardSecurityContainer;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.net.wifi.WifiManager;
import android.widget.Toast;
import com.android.sprd.telephony.uicc.IccCardStatusEx;
/**
 * Displays a enter code view for subsidy
 */
public class KeyguardSubsidyLockEnterCodeView extends KeyguardPinBasedInputView {
    private static final String LOG_TAG = "KeyguardSubsidyLockEnterCodeView";
    private static final boolean DEBUG = true;
    private TextView mEnableData;
    private TextView mSetupWifi;
    private TextView mRecommend;
    private TextView mNodataTitle;
    private IntentFilter mFilter = new IntentFilter();
    private TelephonyManager mTelephonyManager = null;
    private ProgressDialog mSimUnlockProgressDialog = null;
    private CheckSimPin mCheckSimPinThread;
    private int mUnLockTime = 0;
    private static int MAX_UNLOCK_TIMES = 5;
    private static int ONE_MINUTE = 1000 * 60;
    private static int UNLOCK_TIMEOUT = ONE_MINUTE * 60;
    private int mTimeCounter = MAX_UNLOCK_TIMES;
    private int PIN_MIN_LENGTH = 8;
    private int PIN_MAX_LENGTH = 16;
    WifiManager mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    private SharedPreferences mSp;
    private static final String UNLOCK_DEADLINE = "subsidy_deadline";
    private static final String MAX_UNLOCK_TIMES_USER = "subsidy_unlocktimes";
    private static final String UNLOCK_ATTEMPT_TIMES = "vendor.sim.attempttimes.PN";
    private long mDeadline;

    KeyguardUpdateMonitorCallback mUpdateMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onSubsidyEnterCode() {
            Log.d(LOG_TAG, "onSubsidyEnterCode");
            resetState();
        };

        @Override
        public void onSubsidyDeviceLock(int mode) {
            Log.d(LOG_TAG, "onSubsidyDeviceLock mode " + mode);
            if(mode == KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_UNLOCK_PERMANENTLY){
                dismissView();
                return;
            }
        };
    };

    private void dismissView() {
        Log.d(LOG_TAG, "dismissView");
        if (mCallback == null) {
            Log.d(LOG_TAG, "mCallback is null");
            return;
        }
        mCallback.dismiss(true, KeyguardUpdateMonitor.getCurrentUser());
        if (mSp != null) {
            SharedPreferences.Editor editor = mSp.edit();
            editor.putLong(UNLOCK_DEADLINE, 0);
            editor.commit();
        }
    }

    public KeyguardSubsidyLockEnterCodeView(Context context) {
        this(context, null);
    }

    public KeyguardSubsidyLockEnterCodeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSp = mContext.getSharedPreferences("SubsidyLock", Context.MODE_PRIVATE);
    }

    @Override
    public void resetState() {
        Log.d(LOG_TAG, "resetState");
        mTelephonyManager = TelephonyManager.from(getContext());
        mEnableData = (TextView) findViewById(R.id.enable_data);
        mSetupWifi = (TextView) findViewById(R.id.setup_wifi);
        mNodataTitle = (TextView) findViewById(R.id.no_data_title);
        mEnableData.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                handleEnableData();
            }
        });
        mSetupWifi.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                handleSetupWifi();
            }
        });
        setEnableDataBtn();
        Resources rez = getResources();
        final String msg = rez.getString(R.string.no_data_message);
        mSecurityMessageDisplay.setMessage(msg);
        mPasswordEntry.setEnabled(true);
    }

    public boolean isAirplaneModeOn() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void handleEnableData() {
        Log.d(LOG_TAG, "setMobileDataEnabled");
        if (isAirplaneModeOn()) {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0);
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", false);
            getContext().sendBroadcastAsUser(intent, UserHandle.ALL);
        }
        mTelephonyManager.setDataEnabled(true);
    }

    private boolean isAllSimAbsent() {
        boolean isAllSimAbsent = true;
        for (int phoneId = 0; phoneId < mTelephonyManager.getPhoneCount(); phoneId++) {
            if (mTelephonyManager.hasIccCard(phoneId)) {
                isAllSimAbsent = false;
                break;
            }
        }
        Log.d(LOG_TAG, "isAllSimAbsent = " + isAllSimAbsent);
        return isAllSimAbsent;
    }

    private void setEnableDataBtn(){
        Log.d(LOG_TAG, "setEnableDataBtn visable");
        if(isAllSimAbsent()== false && (isAirplaneModeOn() || mTelephonyManager.getDataEnabled() == false) ){
            mEnableData.setVisibility(View.VISIBLE);
        } else {
            mEnableData.setVisibility(View.GONE);
        }
    }

    private static final String IS_SUBSIDYLOCK = ":settings:subsidylock";
    private void handleSetupWifi() {
        Log.d(LOG_TAG, "handleSetupWifi");
        ComponentName componetName = new ComponentName(
              "com.android.settings",
              "com.android.settings.wifi.WifiPickerActivity");
        Intent wifiSetupIntent = new Intent();
        wifiSetupIntent.setComponent(componetName);
        wifiSetupIntent.putExtra(IS_SUBSIDYLOCK, true);
        wifiSetupIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        mWifiManager.setWifiEnabled(true);
        mContext.startActivity(wifiSetupIntent);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                Log.d(LOG_TAG, "CONNECTIVITY_ACTION");
                    Log.d(LOG_TAG, "SubsidyLock Mode is SUBSIDY_LOCK_SCREEN_MODE_NODATA, so if Data availanle unlock again");
                    ConnectivityManager mConnectivityManager = (ConnectivityManager) mContext
                            .getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo mMobileNetworkInfo = mConnectivityManager
                            .getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                    if ((mMobileNetworkInfo != null) && mMobileNetworkInfo.isConnected()) {
                        Log.d(LOG_TAG, "Data availanle, unlock the device again");
                        //To do unlock
                    }

            } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                Log.d(LOG_TAG, "ACTION_AIRPLANE_MODE_CHANGED");
                setEnableDataBtn();
            }
        }
    };

    private ContentObserver mMobileDataObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Log.d(LOG_TAG, "MobileDataObserver onChange");
            setEnableDataBtn();
        }
    };

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resetState();
    }

    @Override
    protected boolean shouldLockout(long deadline) {
        // SIM PIN doesn't have a timed lockout
        return false;
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.simLockPinEntry;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        getContext().registerReceiver(mBroadcastReceiver, mFilter);
        getContext().getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.MOBILE_DATA +SubscriptionManager.MAX_SUBSCRIPTION_ID_VALUE), true,
                mMobileDataObserver,UserHandle.USER_OWNER);
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(
                mUpdateMonitorCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getContext().unregisterReceiver(mBroadcastReceiver);
        getContext().getContentResolver().unregisterContentObserver(mMobileDataObserver);
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mUpdateMonitorCallback);
    }

    @Override
    public void showUsabilityHint() {
    }

    @Override
    public void onResume(int reason) {
        Log.d(LOG_TAG,"onResume");
        super.onResume(reason);
        mDeadline = mSp.getLong(UNLOCK_DEADLINE, 0);
        Log.d(LOG_TAG, "getDeadLine = " + mDeadline);
        Log.d(LOG_TAG, "getUnLockTime = " + mUnLockTime);
        if (System.currentTimeMillis() < mDeadline) {
            Log.d(LOG_TAG, "not reach deadline, continue forbidden input");
            mTimeCounter = (int) (mDeadline - System.currentTimeMillis()) / (ONE_MINUTE);
            if (mTimeCounter > UNLOCK_TIMEOUT / ONE_MINUTE) {
                Log.d(LOG_TAG, "mTimeCounter more than 60 minutes, set to 60 minutes");
                mDeadline = System.currentTimeMillis() + UNLOCK_TIMEOUT;
                mTimeCounter = UNLOCK_TIMEOUT/ONE_MINUTE;
                SharedPreferences.Editor editor = mSp.edit();
                editor.putLong(UNLOCK_DEADLINE, mDeadline);
                editor.commit();
            }
            mPasswordEntry.setEnabled(false);
            removeCallbacks(mRunnable);
            post(mRunnable);
        } else {
            Log.d(LOG_TAG, "time out when show, reset all");
            Resources rez = getResources();
            final String msg = rez.getString(R.string.no_data_message);
            mSecurityMessageDisplay.setMessage(msg);
            mPasswordEntry.setEnabled(true);
            mTimeCounter = UNLOCK_TIMEOUT / ONE_MINUTE;
            SharedPreferences.Editor editor = mSp.edit();
            editor.putLong(UNLOCK_DEADLINE, 0);
            editor.commit();
        }
    }

    @Override
    public void onPause() {
        dismissProgressDialog();
    }

    @Override
    protected void verifyPasswordAndUnlock() {
        Log.d(LOG_TAG, "verifyPasswordAndUnlock");
        String entry = mPasswordEntry.getText();
        Log.d(LOG_TAG, "entry = " + entry);
        if (entry.length() == 0) {
            // otherwise, display a message to the user, and don't submit.
            resetPasswordText(true /* animate */, true /* announce */);
            mCallback.userActivity();
            return;
        }
        if (entry.length() < PIN_MIN_LENGTH || entry.length() > PIN_MAX_LENGTH) {
            // the length invalided, so set to 000000000
            entry= "000000000";
        }
        getSimUnlockProgressDialog().show();
        if (mCheckSimPinThread == null) {
            mCheckSimPinThread = new CheckSimPin(entry) {
                @Override
                void onSimCheckResponse(final int result, final int unlockTime) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            resetPasswordText(true /* animate */,
                                    result != PhoneConstants.PIN_RESULT_SUCCESS /* announce */);
                            if (result == PhoneConstants.PIN_RESULT_SUCCESS) {
                                Log.d(LOG_TAG,
                                        "show unlock toast for PIN_RESULT_SUCCESS");
                                Resources rez = getResources();
                                final String msg = rez
                                        .getString(R.string.device_unlocked);
                                Toast toast = Toast.makeText(mContext, msg,
                                        Toast.LENGTH_LONG);
                                toast.getWindowParams().type = WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL;
                                toast.getWindowParams().privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
                                toast.getWindowParams().flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
                                toast.show();
                                Intent intent = new Intent(
                                        KeyguardSubsidyLockController.ACTION_USER_REQUEST);
                                intent.putExtra(
                                        KeyguardSubsidyLockController.INTENT_KEY_PIN_VERIFIED,
                                        true);
                                intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
                                Log.d(LOG_TAG,
                                        "PIN_RESULT_SUCCESS sendBroadcast INTENT_KEY_PIN_VERIFIED");
                                mContext.sendBroadcast(intent);
                                mCallback.dismiss(true,
                                        KeyguardUpdateMonitor.getCurrentUser());
                                dismissProgressDialog();
                            } else {
                                /* SPRD:add PIN verify success prompt @{ */
                                if (mSimUnlockProgressDialog != null) {
                                    mSimUnlockProgressDialog.hide();
                                }
                                /* @} */
                                mUnLockTime = getSimLockUnLockTimes();
                                Log.d(LOG_TAG,
                                        " depersonalization request failure. mUnLockTime = "
                                                + mUnLockTime);
                                mSecurityMessageDisplay
                                        .setMessage(getResources().getString(
                                                R.string.wrong_pin_entered,
                                                MAX_UNLOCK_TIMES - mUnLockTime
                                                        % MAX_UNLOCK_TIMES));
                                mPasswordEntry.requestFocus();
                                if (mUnLockTime % MAX_UNLOCK_TIMES == 0) {
                                    mDeadline = System.currentTimeMillis()
                                            + UNLOCK_TIMEOUT;
                                    SharedPreferences.Editor editor = mSp
                                            .edit();
                                    editor.putLong(UNLOCK_DEADLINE, mDeadline);
                                    editor.commit();
                                    Log.d(LOG_TAG, "set mDeadline = "
                                            + mDeadline);
                                    mTimeCounter = UNLOCK_TIMEOUT / 60000;
                                    mPasswordEntry.setEnabled(false);
                                    removeCallbacks(mRunnable);
                                    post(mRunnable);
                                }
                                if (DEBUG)
                                    Log.d(LOG_TAG,
                                            "verifyPasswordAndUnlock "
                                                    + " CheckSimPin.onSimCheckResponse: "
                                                    + result + " unLockTime ="
                                                    + mUnLockTime);
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

    private int getSimLockUnLockTimes() {
        Log.i(LOG_TAG, "getUnLockTimes for " + UNLOCK_ATTEMPT_TIMES);
        String attemptsString = TelephonyManager.getTelephonyProperty(0, UNLOCK_ATTEMPT_TIMES, "0");
        int attemptsCount = -1;
        try {
            attemptsCount = Integer.parseInt(attemptsString);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        if (attemptsCount < 0) {
            Log.i(LOG_TAG, "invalid attemptsCount, set to 0 " + attemptsCount);
            attemptsCount = 0;
        }
        return attemptsCount;
    }

    private Dialog getSimUnlockProgressDialog() {
        if (mSimUnlockProgressDialog == null) {
            mSimUnlockProgressDialog = new ProgressDialog(mContext);
            mSimUnlockProgressDialog.setMessage(mContext
                    .getString(R.string.kg_sim_unlock_progress_dialog_message));
            mSimUnlockProgressDialog.setIndeterminate(true);
            mSimUnlockProgressDialog.setCancelable(false);
            mSimUnlockProgressDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        }
        return mSimUnlockProgressDialog;
    }

    private void dismissProgressDialog(){
        postDelayed(new Runnable() {
            public void run() {
                Log.d(LOG_TAG, "dismissProgressDialog() : mSimUnlockProgressDialog ="
                        + mSimUnlockProgressDialog);
                if (mSimUnlockProgressDialog != null) {
                    mSimUnlockProgressDialog.dismiss();
                    mSimUnlockProgressDialog = null;
                    Log.d(LOG_TAG, "dismissProgressDialog(); "
                            + "prompt subscribers PIN unlock success for one second");
                }
            }
        }, 500);
    }

    Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            if(mTimeCounter > 0) {
                Log.d(LOG_TAG,"onTick set to " + mTimeCounter + "minutes");
                mSecurityMessageDisplay.setMessage(getResources().getString(
                        R.string.lockpattern_too_many_failed_confirmation_attempts, mTimeCounter));
                mTimeCounter--;
                removeCallbacks(mRunnable);
                postDelayed(this, ONE_MINUTE);
            } else {
                Log.d(LOG_TAG,"time out, reset time and count" );
                mSecurityMessageDisplay.setMessage(R.string.no_data_message);
                mPasswordEntry.setEnabled(true);
                mTimeCounter = UNLOCK_TIMEOUT/ONE_MINUTE;
                SharedPreferences.Editor editor = mSp.edit();
                editor.putLong(UNLOCK_DEADLINE, 0);
                editor.commit();
            }
        }
    };

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPin extends Thread {
        private final String mPin;
        protected CheckSimPin(String pin) {
            mPin = pin;
        }
        abstract void onSimCheckResponse(final int result,
                final int attemptsRemaining);
        @Override
        public void run() {
            try {
                Log.d(LOG_TAG, "call unlock sim lock");
                final int[] result = ITelephonyEx.Stub.asInterface(
                        ServiceManager.checkService("phone_ex"))
                        .supplySimlockReportResultForPhone(0, false, mPin, IccCardStatusEx.UNLOCK_NETWORK);
                if (DEBUG) {
                    Log.v(LOG_TAG, "supplyPinReportResult returned: " + result[0]
                            + " " + result[1]);
                }
                post(new Runnable() {
                    @Override
                    public void run() {
                        onSimCheckResponse(result[0], result[1]);
                    }
                });
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "RemoteException for supplyPinReportResult:", e);
                post(new Runnable() {
                    @Override
                    public void run() {
                        onSimCheckResponse(PhoneConstants.PIN_GENERAL_FAILURE,
                                -1);
                    }
                });
            }
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
}

