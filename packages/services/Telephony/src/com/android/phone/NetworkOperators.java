/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.TwoStatePreference;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import com.android.ims.internal.ImsManagerEx;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TeleUtils;

/**
 * "Networks" settings UI for the Phone app.
 */
public class NetworkOperators extends PreferenceCategory
        implements Preference.OnPreferenceChangeListener {

    private static final String LOG_TAG = "NetworkOperators";
    private static final boolean DBG = true;

    private static final int EVENT_AUTO_SELECT_DONE = 100;
    private static final int EVENT_GET_NETWORK_SELECTION_MODE_DONE = 200;

    //String keys for preference lookup
    public static final String BUTTON_NETWORK_SELECT_KEY = "button_network_select_key";
    public static final String BUTTON_AUTO_SELECT_KEY = "button_auto_select_key";
    public static final String BUTTON_CHOOSE_NETWORK_KEY = "button_choose_network_key";
    public static final String CATEGORY_NETWORK_OPERATORS_KEY = "network_operators_category_key";

    int mPhoneId = SubscriptionManager.INVALID_PHONE_INDEX;
    private static final int ALREADY_IN_AUTO_SELECTION = 1;

    //preference objects
    private NetworkSelectListPreference mNetworkSelect;
    protected TwoStatePreference mAutoSelect;
    private Preference mChooseNetwork;

    private int mSubId;
    private ProgressDialog mProgressDialog;
    // UNISOC: Add for Bug 1010794
    private AlertDialog mSelectionFailedDialog;

    // There's two sets of Auto-Select UI in this class.
    // If {@code com.android.internal.R.bool.config_enableNewAutoSelectNetworkUI} set as true
    // {@link mChooseNetwork} will be used, otherwise {@link mNetworkSelect} will be used.
    boolean mEnableNewManualSelectNetworkUI;

    public NetworkOperators(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NetworkOperators(Context context) {
        super(context);
    }

    /**
     * Initialize NetworkOperators instance.
     */
    public void initialize() {
        mEnableNewManualSelectNetworkUI = getContext().getResources().getBoolean(
                com.android.internal.R.bool.config_enableNewAutoSelectNetworkUI);
        mAutoSelect = (TwoStatePreference) findPreference(BUTTON_AUTO_SELECT_KEY);
        mChooseNetwork = findPreference(BUTTON_CHOOSE_NETWORK_KEY);
        mNetworkSelect = (NetworkSelectListPreference) findPreference(BUTTON_NETWORK_SELECT_KEY);
        if (mEnableNewManualSelectNetworkUI) {
            removePreference(mNetworkSelect);
        } else {
            removePreference(mChooseNetwork);
        }
        mProgressDialog = new ProgressDialog(getContext());
    }

    /**
     * Update NetworkOperators instance if like subId or queryService are updated.
     *
     * @param subId Corresponding subscription ID of this network.
     * @param queryService The service to do network queries.
     */
    protected void update(final int subId, INetworkQueryService queryService) {
        mSubId = subId;
        mPhoneId = SubscriptionManager.getPhoneId(mSubId);

        if (mAutoSelect != null) {
            mAutoSelect.setOnPreferenceChangeListener(this);
        }

        if (mEnableNewManualSelectNetworkUI) {
            if (mChooseNetwork != null) {
                TelephonyManager telephonyManager = (TelephonyManager)
                        getContext().getSystemService(Context.TELEPHONY_SERVICE);
                if (DBG) logd("data connection status " + telephonyManager.getDataState());
                if (telephonyManager.getDataState() == telephonyManager.DATA_CONNECTED) {
                    // UNISOC: Bug 781063 Update operator name
                    String networkOperatorName = telephonyManager.getNetworkOperatorName(mSubId);
                    mChooseNetwork.setSummary(TeleUtils.updateOperator(networkOperatorName, "operator"));
                } else {
                    mChooseNetwork.setSummary(R.string.network_disconnected);
                }
            }
        } else {
            if (mNetworkSelect != null) {
                mNetworkSelect.initialize(mSubId, queryService, this, mProgressDialog);
            }
        }
        getNetworkSelectionMode();
    }

    /**
     * Implemented to support onPreferenceChangeListener to look for preference
     * changes specifically on auto select button.
     *
     * @param preference is the preference to be changed, should be auto select button.
     * @param newValue should be the value of whether autoSelect is checked.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mAutoSelect) {
            /* UNISOC: Bug723661 In DSDA mode, user should not perform manual network selection @{ */
            if (ImsManagerEx.isDualVoLTEActive() && (Boolean) newValue == false) {
                Toast.makeText(getContext(), R.string.dualvolte_on_cannot_select_operator,
                        Toast.LENGTH_SHORT).show();
                return true;
            }
            /* @}*/
            boolean autoSelect = (Boolean) newValue;
            if (DBG) logd("onPreferenceChange autoSelect: " + String.valueOf(autoSelect));
            selectNetworkAutomatic(autoSelect);
            MetricsLogger.action(getContext(),
                    MetricsEvent.ACTION_MOBILE_NETWORK_AUTO_SELECT_NETWORK_TOGGLE, autoSelect);
            // UNISOC: Bug 791054 If it's scaning network now, no need to refresh state of mAutoSelect button, unless actually press confirm button of the warning dialog .
            return autoSelect;
//            return true;
        }
        return false;
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                case EVENT_AUTO_SELECT_DONE:
                    mAutoSelect.setEnabled(true);
                    // UNISOC: Bug 698162 Register network automatically after failing to register manually.
                    mAutoSelect.setChecked(true);
                    dismissProgressBar();

                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) logd("automatic network selection: failed!");
                        /* UNISOC: Bug 698162
                         * @orig
                        displayNetworkSelectionFailed(ar.exception);
                        /* @{ */
                        displayNetworkSelectionFailed(ar.exception, false);
                        getNetworkSelectionMode();
                        /* @} */
                    } else {
                        if (DBG) logd("automatic network selection: succeeded!");
                        displayNetworkSelectionSucceeded(msg.arg1);
                    }

                    break;
                case EVENT_GET_NETWORK_SELECTION_MODE_DONE:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) logd("get network selection mode: failed!");
                    } else if (ar.result != null) {
                        try {
                            int[] modes = (int[]) ar.result;
                            boolean autoSelect = (modes[0] == 0);
                            if (DBG) {
                                logd("get network selection mode: "
                                        + (autoSelect ? "auto" : "manual") + " selection");
                            }
                            /* UNISOC: Bug 791054
                             * @orig
                             *
                            if (mAutoSelect != null) {
                                mAutoSelect.setChecked(autoSelect);
                            }
                            if (mEnableNewManualSelectNetworkUI) {
                                if (mChooseNetwork != null) {
                                    mChooseNetwork.setEnabled(!autoSelect);
                                }
                            } else {
                                if (mNetworkSelect != null) {
                                    mNetworkSelect.setEnabled(!autoSelect);
                                }
                            }
                            /* @{*/
                            // Prepared to select a network.
                            if (mProgressDialog != null && mProgressDialog.isShowing()
                                    || mNetworkSelect != null && mNetworkSelect.getDialog()!= null){
                                Log.d(LOG_TAG,"network scan phoneId: " + mNetworkSelect.getScanPhoneId());
                                // Force to refresh mAutoSelect button state if user prepare to select network manually.
                                if (mPhoneId == mNetworkSelect.getScanPhoneId()){
                                    refreshButton(false);
                                } else {
                                    logd("Not refresh mAutoSelect button");
                                    return;
                                }
                            } else {
                                refreshButton(autoSelect);
                            }
                            /* @} */
                        } catch (Exception e) {
                            if (DBG) loge("get network selection mode: unable to parse result.");
                        }
                    }
            }
            return;
        }
    };

    /* UNISOC: Bug 791054 If mPhoneId not equals to mTempPhoneId which user used querying available networks and saving, just use mTempPhoneId to select network @{*/
    private void refreshButton (boolean autoSelect){
        if (mAutoSelect != null) {
            mAutoSelect.setChecked(autoSelect);
        }
        if (mEnableNewManualSelectNetworkUI) {
            if (mChooseNetwork != null) {
                mChooseNetwork.setEnabled(!autoSelect);
            }
        } else {
            if (mNetworkSelect != null) {
                mNetworkSelect.setEnabled(!autoSelect);
            }
        }
    }
    /* @}*/

    // Used by both mAutoSelect and mNetworkSelect buttons.
    protected void displayNetworkSelectionFailed(Throwable ex, boolean manual) {
        String status;
        if ((ex != null && ex instanceof CommandException)
                && ((CommandException) ex).getCommandError()
                == CommandException.Error.ILLEGAL_SIM_OR_ME) {
            status = getContext().getResources().getString(R.string.not_allowed);
        } else {
            status = getContext().getResources().getString(R.string.connect_later);
        }

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);

        TelephonyManager tm = (TelephonyManager) app.getSystemService(Context.TELEPHONY_SERVICE);
        Phone phone = PhoneFactory.getPhone(mPhoneId);
        if (phone != null) {
            ServiceState ss = tm.getServiceStateForSubscriber(phone.getSubId());
            if (ss != null) {
                app.notificationMgr.updateNetworkSelection(ss.getState(), phone.getSubId());
            }
        }
        /* UNISOC: Bug 698162 Register network automatically after failing to register manually. @{ */
        if (manual) {
            loge("not auto select, try auto select");
            showNetworkSelectionFailed();
        }
        /* @} */
    }

    /* UNISOC: Bug 1010794 Dissmiss network selection failed dialog if sim is removed.*/
    protected void dismissSelectionFailedDialog(){
        if (mSelectionFailedDialog != null && mSelectionFailedDialog.isShowing()) {
            mSelectionFailedDialog.dismiss();
            mSelectionFailedDialog = null;
        }
    }

    /* UNISOC: Bug 698162 Register network automatically after failing to register manually. @{ */
    private void showNetworkSelectionFailed(){
        dismissSelectionFailedDialog();
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.network_registration_fail_title)
                    .setMessage(R.string.network_registration_fail)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            selectNetworkAutomatic(true);
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    })
                    .setCancelable(false);
           mSelectionFailedDialog = builder.create();
           mSelectionFailedDialog.show();
    }
    /* @} */

    // Used by both mAutoSelect and mNetworkSelect buttons.
    protected void displayNetworkSelectionSucceeded(int msgArg1) {
        String status = null;
        if (msgArg1 == ALREADY_IN_AUTO_SELECTION) {
            status = getContext().getResources().getString(R.string.already_auto);
        } else {
            status = getContext().getResources().getString(R.string.registration_done);
        }

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);
    }

    private void selectNetworkAutomatic(boolean autoSelect) {
        if (DBG) logd("selectNetworkAutomatic: " + String.valueOf(autoSelect));

        if (autoSelect) {
            if (mEnableNewManualSelectNetworkUI) {
                if (mChooseNetwork != null) {
                    mChooseNetwork.setEnabled(!autoSelect);
                }
            } else {
                if (mNetworkSelect != null) {
                    mNetworkSelect.setEnabled(!autoSelect);
                }
            }
            if (DBG) logd("select network automatically...");
            showAutoSelectProgressBar();
            mAutoSelect.setEnabled(false);
            Message msg = mHandler.obtainMessage(EVENT_AUTO_SELECT_DONE);
            Phone phone = PhoneFactory.getPhone(mPhoneId);
            if (phone != null) {
                phone.setNetworkSelectionModeAutomatic(msg);
            }
        } else {
            if (mEnableNewManualSelectNetworkUI) {
                if (mChooseNetwork != null) {
                    // Open the choose Network page automatically when user turn off the auto-select
                    openChooseNetworkPage();
                }
            } else {
                if (mNetworkSelect != null) {
                    mNetworkSelect.onClick();
                }
            }
        }
    }

    protected void getNetworkSelectionMode() {
        if (DBG) logd("getting network selection mode...");
        Message msg = mHandler.obtainMessage(EVENT_GET_NETWORK_SELECTION_MODE_DONE);
        Phone phone = PhoneFactory.getPhone(mPhoneId);
        if (phone != null) {
            phone.getNetworkSelectionMode(msg);
        }
    }

    private void dismissProgressBar() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    private void showAutoSelectProgressBar() {
        mProgressDialog.setMessage(
                getContext().getResources().getString(R.string.register_automatically));
        mProgressDialog.setCanceledOnTouchOutside(false);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.show();
    }

    /**
     * Open the Choose netwotk page via {@alink NetworkSelectSettingActivity}
     */
    public void openChooseNetworkPage() {
        Intent intent = NetworkSelectSettingActivity.getIntent(getContext(), mPhoneId);
        getContext().startActivity(intent);
    }

    protected boolean preferenceTreeClick(Preference preference) {
        /* UNISOC: Bug723661 In DSDA mode, user should not perform manual network selection @{ */
        if (preference == mAutoSelect && ImsManagerEx.isDualVoLTEActive()
                && !mAutoSelect.isChecked()) {
                logd("Click mAutoSelect preference...");
                mAutoSelect.setChecked(true);
        }
        /* @}*/
        if (mEnableNewManualSelectNetworkUI) {
            if (DBG) logd("enable New AutoSelectNetwork UI");
            if (preference == mChooseNetwork) {
                openChooseNetworkPage();
            }
            return (preference == mAutoSelect || preference == mChooseNetwork);
        } else {
            return (preference == mAutoSelect || preference == mNetworkSelect);
        }
    }

    /* UNISOC: Bug 882828 @{*/
    protected NetworkSelectListPreference getNetworkPreference(){
        return mNetworkSelect;
    }
    /* @} */

    private void logd(String msg) {
        Log.d(LOG_TAG, "[NetworksList] " + msg);
    }

    private void loge(String msg) {
        Log.e(LOG_TAG, "[NetworksList] " + msg);
    }
}