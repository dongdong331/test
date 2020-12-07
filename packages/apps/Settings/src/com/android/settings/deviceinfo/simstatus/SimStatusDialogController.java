/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.deviceinfo.simstatus;

import static android.content.Context.CARRIER_CONFIG_SERVICE;
import static android.content.Context.EUICC_SERVICE;
import static android.content.Context.TELEPHONY_SERVICE;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.telephony.CarrierConfigManager;
import android.telephony.CellBroadcastMessage;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.euicc.EuiccManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.ims.ImsManager;
import com.android.ims.internal.ImsManagerEx;
import android.os.RemoteException;
import com.android.ims.internal.IImsServiceEx;
import com.android.ims.internal.IImsRegisterListener;
import com.android.ims.ImsConfig;
import com.android.settings.R;
import com.android.settingslib.DeviceInfoUtils;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnPause;
import com.android.settingslib.core.lifecycle.events.OnResume;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.util.List;

public class SimStatusDialogController implements LifecycleObserver, OnResume, OnPause, OnStart, OnStop {

    private final static String TAG = "SimStatusDialogCtrl";

    @VisibleForTesting
    final static int NETWORK_PROVIDER_VALUE_ID = R.id.operator_name_value;
    @VisibleForTesting
    final static int PHONE_NUMBER_VALUE_ID = R.id.number_value;
    @VisibleForTesting
    final static int CELLULAR_NETWORK_STATE = R.id.data_state_value;
    @VisibleForTesting
    final static int OPERATOR_INFO_LABEL_ID = R.id.latest_area_info_label;
    @VisibleForTesting
    final static int OPERATOR_INFO_VALUE_ID = R.id.latest_area_info_value;
    @VisibleForTesting
    final static int SERVICE_STATE_VALUE_ID = R.id.service_state_value;
    @VisibleForTesting
    final static int SIGNAL_STRENGTH_LABEL_ID = R.id.signal_strength_label;
    @VisibleForTesting
    final static int SIGNAL_STRENGTH_VALUE_ID = R.id.signal_strength_value;
    @VisibleForTesting
    final static int CELL_VOICE_NETWORK_TYPE_VALUE_ID = R.id.voice_network_type_value;
    @VisibleForTesting
    final static int CELL_DATA_NETWORK_TYPE_VALUE_ID = R.id.data_network_type_value;
    @VisibleForTesting
    final static int ROAMING_INFO_VALUE_ID = R.id.roaming_state_value;
    @VisibleForTesting
    final static int ICCID_INFO_LABEL_ID = R.id.icc_id_label;
    @VisibleForTesting
    final static int ICCID_INFO_VALUE_ID = R.id.icc_id_value;
    @VisibleForTesting
    final static int EID_INFO_VALUE_ID = R.id.esim_id_value;
    @VisibleForTesting
    final static int IMS_REGISTRATION_STATE_LABEL_ID = R.id.ims_reg_state_label;
    @VisibleForTesting
    final static int IMS_REGISTRATION_STATE_VALUE_ID = R.id.ims_reg_state_value;

    private final static String CB_AREA_INFO_RECEIVED_ACTION =
            "com.android.cellbroadcastreceiver.CB_AREA_INFO_RECEIVED";
    private final static String GET_LATEST_CB_AREA_INFO_ACTION =
            "com.android.cellbroadcastreceiver.GET_LATEST_CB_AREA_INFO";
    private final static String CELL_BROADCAST_RECEIVER_APP = "com.android.cellbroadcastreceiver";

    private final SimStatusDialogFragment mDialog;
    private final SubscriptionInfo mSubscriptionInfo;
    private final TelephonyManager mTelephonyManager;
    // UNISOC:add for bug 894005
    private boolean mIsAreaInfoReceiverRegister = false;
    private final CarrierConfigManager mCarrierConfigManager;
    private final EuiccManager mEuiccManager;
    private final Resources mRes;
    private final Context mContext;
    private int mSlotId;
    private boolean mIsReceiverRegister = false;

    private boolean mShowLatestAreaInfo;
    private boolean mIsImsListenerRegistered;
    private IImsServiceEx mIImsServiceEx;
    private ImsManager mImsManager;
    boolean mIsWifiCallingEnabled = false;
    int mImsType = -2;

    private final BroadcastReceiver mAreaInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TextUtils.equals(action, CB_AREA_INFO_RECEIVED_ACTION)) {
                final Bundle extras = intent.getExtras();
                if (extras == null) {
                    return;
                }
                final CellBroadcastMessage cbMessage = (CellBroadcastMessage) extras.get("message");
                if (cbMessage != null
                        && mSubscriptionInfo.getSubscriptionId() == cbMessage.getSubId()) {
                    final String latestAreaInfo = cbMessage.getMessageBody();
                    mDialog.setText(OPERATOR_INFO_VALUE_ID, latestAreaInfo);
                }
            }
        }
    };

    private PhoneStateListener mPhoneStateListener;

    public SimStatusDialogController(@NonNull SimStatusDialogFragment dialog, Lifecycle lifecycle,
            int slotId) {
        mDialog = dialog;
        mContext = dialog.getContext();
        mSubscriptionInfo = getPhoneSubscriptionInfo(slotId);
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(
                TELEPHONY_SERVICE);
        mCarrierConfigManager = (CarrierConfigManager) mContext.getSystemService(
                CARRIER_CONFIG_SERVICE);
        mEuiccManager = (EuiccManager) mContext.getSystemService(EUICC_SERVICE);
        mImsManager = ImsManager.getInstance(mContext, slotId);
        mRes = mContext.getResources();

        if (lifecycle != null) {
            lifecycle.addObserver(this);
        }

        mSlotId = slotId;
    }

    public void initialize() {
        updateEid();

        if (mSubscriptionInfo == null) {
            return;
        }

        mPhoneStateListener = getPhoneStateListener();

        final ServiceState serviceState = getCurrentServiceState();
        updateNetworkProvider(serviceState);
        updatePhoneNumber();
        updateLatestAreaInfo();
        updateServiceState(serviceState);
        updateSignalStrength(getSignalStrength());
        updateNetworkType();
        updateRoamingStatus(serviceState);
        updateIccidNumber();
        updateImsRegistrationState();
    }

    /* UNISOC: add for bug 977213 */
    @Override
    public void onStart() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        intentFilter.addAction(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED);
        mContext.registerReceiver(mReceiver,intentFilter);
        mIsReceiverRegister = true;
    }

    @Override
    public void onResume() {
        if (mSubscriptionInfo == null) {
            return;
        }

        mTelephonyManager.listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                        | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        | PhoneStateListener.LISTEN_SERVICE_STATE);

        if (mShowLatestAreaInfo) {
            /* UNISOC: add for bug 894005 @{ */
            if (!mIsAreaInfoReceiverRegister) {
                mContext.registerReceiver(mAreaInfoReceiver,
                        new IntentFilter(CB_AREA_INFO_RECEIVED_ACTION),
                        Manifest.permission.RECEIVE_EMERGENCY_BROADCAST, null /* scheduler */);
                mIsAreaInfoReceiverRegister = true;
            }
            /* @} */

            // Ask CellBroadcastReceiver to broadcast the latest area info received
            final Intent getLatestIntent = new Intent(GET_LATEST_CB_AREA_INFO_ACTION);
            getLatestIntent.setPackage(CELL_BROADCAST_RECEIVER_APP);
            mContext.sendBroadcastAsUser(getLatestIntent, UserHandle.ALL,
                    Manifest.permission.RECEIVE_EMERGENCY_BROADCAST);
        }
        tryRegisterImsListener();
    }

    @Override
    public void onPause() {
        if (mSubscriptionInfo == null) {
            return;
        }

        mTelephonyManager.listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_NONE);

        if (mShowLatestAreaInfo) {
            /* UNISOC: add for bug 894005 @{ */
            if (mIsAreaInfoReceiverRegister) {
                try {
                    mIsAreaInfoReceiverRegister = false;
                    mContext.unregisterReceiver(mAreaInfoReceiver);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Receiver not registered");
                }
            }
            /* @} */
        }
        unTryRegisterImsListener();
    }

    /* UNISOC: add for bug 977213 */
    @Override
    public void onStop() {
        if (mIsReceiverRegister) {
            mContext.unregisterReceiver(mReceiver);
            mIsReceiverRegister = false;
        }
    }

    private String getCurrentNetworkOperatorName() {
        return mTelephonyManager.getNetworkOperatorName(mSubscriptionInfo.getSubscriptionId());
    }

    private void updateNetworkProvider(ServiceState serviceState) {
        mDialog.setText(NETWORK_PROVIDER_VALUE_ID, isInService(serviceState) ? getCurrentNetworkOperatorName() :
                null);
    }

    private void updatePhoneNumber() {
        // If formattedNumber is null or empty, it'll display as "Unknown".
        mDialog.setText(PHONE_NUMBER_VALUE_ID, BidiFormatter.getInstance().unicodeWrap(
                getPhoneNumber(), TextDirectionHeuristics.LTR));
    }

    private void updateDataState(int state) {
        String networkStateValue;

        switch (state) {
            case TelephonyManager.DATA_CONNECTED:
                networkStateValue = mRes.getString(R.string.radioInfo_data_connected);
                break;
            case TelephonyManager.DATA_SUSPENDED:
                networkStateValue = mRes.getString(R.string.radioInfo_data_suspended);
                break;
            case TelephonyManager.DATA_CONNECTING:
                networkStateValue = mRes.getString(R.string.radioInfo_data_connecting);
                break;
            case TelephonyManager.DATA_DISCONNECTED:
                networkStateValue = mRes.getString(R.string.radioInfo_data_disconnected);
                break;
            default:
                networkStateValue = mRes.getString(R.string.radioInfo_unknown);
                break;
        }

        mDialog.setText(CELLULAR_NETWORK_STATE, networkStateValue);
    }


    private void updateLatestAreaInfo() {
        mShowLatestAreaInfo = Resources.getSystem().getBoolean(
                com.android.internal.R.bool.config_showAreaUpdateInfoSettings)
                && mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_CDMA;

        if (!mShowLatestAreaInfo) {
            mDialog.removeSettingFromScreen(OPERATOR_INFO_LABEL_ID);
            mDialog.removeSettingFromScreen(OPERATOR_INFO_VALUE_ID);
        }
    }

    private void updateServiceState(ServiceState serviceState) {
        int state = ServiceState.STATE_OUT_OF_SERVICE;
        if (serviceState != null) {
            state = serviceState.getVoiceRegState();
        }
        if (state != ServiceState.STATE_IN_SERVICE) {
            state = serviceState.getDataRegState();
        }
        if (state != ServiceState.STATE_IN_SERVICE) {
            resetSignalStrength();
        }
        String serviceStateValue;

        switch (state) {
            case ServiceState.STATE_IN_SERVICE:
                serviceStateValue = mRes.getString(R.string.radioInfo_service_in);
                break;
            case ServiceState.STATE_OUT_OF_SERVICE:
            case ServiceState.STATE_EMERGENCY_ONLY:
                // Set summary string of service state to radioInfo_service_out when
                // service state is both STATE_OUT_OF_SERVICE & STATE_EMERGENCY_ONLY
                serviceStateValue = mRes.getString(R.string.radioInfo_service_out);
                break;
            case ServiceState.STATE_POWER_OFF:
                serviceStateValue = mRes.getString(R.string.radioInfo_service_off);
                break;
            default:
                serviceStateValue = mRes.getString(R.string.radioInfo_unknown);
                break;
        }

        mDialog.setText(SERVICE_STATE_VALUE_ID, serviceStateValue);
    }

    private void updateSignalStrength(SignalStrength signalStrength) {
        final int subscriptionId = mSubscriptionInfo.getSubscriptionId();
        final PersistableBundle carrierConfig =
                mCarrierConfigManager.getConfigForSubId(subscriptionId);
        // by default we show the signal strength
        boolean showSignalStrength = true;
        if (carrierConfig != null) {
            showSignalStrength = carrierConfig.getBoolean(
                    CarrierConfigManager.KEY_SHOW_SIGNAL_STRENGTH_IN_SIM_STATUS_BOOL);
        }
        if (!showSignalStrength) {
            mDialog.removeSettingFromScreen(SIGNAL_STRENGTH_LABEL_ID);
            mDialog.removeSettingFromScreen(SIGNAL_STRENGTH_VALUE_ID);
            return;
        }

        if (!isInService(getCurrentServiceState())) {
            resetSignalStrength();
            return;
        }

        int signalDbm = getDbm(signalStrength);
        int signalAsu = getAsuLevel(signalStrength);

        if (signalDbm == -1) {
            signalDbm = 0;
        }

        if (signalAsu == -1) {
            signalAsu = 0;
        }

        mDialog.setText(SIGNAL_STRENGTH_VALUE_ID, mRes.getString(R.string.sim_signal_strength,
                signalDbm, signalAsu));
    }

    private void resetSignalStrength() {
        mDialog.setText(SIGNAL_STRENGTH_VALUE_ID, "0");
    }

    private void updateNetworkType() {
        String dataNetworkTypeName = null;
        String voiceNetworkTypeName = null;
        Log.d(TAG, "updateNetworkType mIsWifiCallingEnabled : " + mIsWifiCallingEnabled);
        if (!isInService(getCurrentServiceState()) && !mIsWifiCallingEnabled) {
            mDialog.setText(CELL_VOICE_NETWORK_TYPE_VALUE_ID, voiceNetworkTypeName);
            mDialog.setText(CELL_DATA_NETWORK_TYPE_VALUE_ID, dataNetworkTypeName);
            return;
        }
        // Whether EDGE, UMTS, etc...
        final int subId = mSubscriptionInfo.getSubscriptionId();
        final int actualDataNetworkType = mTelephonyManager.getDataNetworkType(subId);
        final int actualVoiceNetworkType = mTelephonyManager.getVoiceNetworkType(subId);
        if (TelephonyManager.NETWORK_TYPE_UNKNOWN != actualDataNetworkType) {
            dataNetworkTypeName = mTelephonyManager.getNetworkTypeName(actualDataNetworkType);
        }
        if (TelephonyManager.NETWORK_TYPE_UNKNOWN != actualVoiceNetworkType) {
            voiceNetworkTypeName = mTelephonyManager.getNetworkTypeName(actualVoiceNetworkType);
        }

        boolean show4GForLTE = false;
        try {
            final Context con = mContext.createPackageContext(
                    "com.android.systemui", 0 /* flags */);
            final int id = con.getResources().getIdentifier("config_show4GForLTE",
                    "bool" /* default type */, "com.android.systemui" /* default package */);
            show4GForLTE = con.getResources().getBoolean(id);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "NameNotFoundException for show4GForLTE");
        }
        if (mIsWifiCallingEnabled) {
            dataNetworkTypeName = "IWLAN";
            voiceNetworkTypeName = "IWLAN";
        } else if (show4GForLTE) {
            if ("LTE".equals(dataNetworkTypeName)) {
                dataNetworkTypeName = "4G";
            }
            if ("LTE".equals(voiceNetworkTypeName)) {
                voiceNetworkTypeName = "4G";
            }
        }

        mDialog.setText(CELL_VOICE_NETWORK_TYPE_VALUE_ID, voiceNetworkTypeName);
        mDialog.setText(CELL_DATA_NETWORK_TYPE_VALUE_ID, dataNetworkTypeName);
    }

    private void updateRoamingStatus(ServiceState serviceState) {
        if (serviceState.getRoaming()) {
            mDialog.setText(ROAMING_INFO_VALUE_ID, mRes.getString(R.string.radioInfo_roaming_in));
        } else {
            mDialog.setText(ROAMING_INFO_VALUE_ID, mRes.getString(R.string.radioInfo_roaming_not));
        }
    }

    private void updateIccidNumber() {
        final int subscriptionId = mSubscriptionInfo.getSubscriptionId();
        final PersistableBundle carrierConfig =
                mCarrierConfigManager.getConfigForSubId(subscriptionId);
        // do not show iccid by default
        boolean showIccId = false;
        if (carrierConfig != null) {
            showIccId = carrierConfig.getBoolean(
                    CarrierConfigManager.KEY_SHOW_ICCID_IN_SIM_STATUS_BOOL);
        }
        if (!showIccId) {
            mDialog.removeSettingFromScreen(ICCID_INFO_LABEL_ID);
            mDialog.removeSettingFromScreen(ICCID_INFO_VALUE_ID);
        } else {
            mDialog.setText(ICCID_INFO_VALUE_ID, getSimSerialNumber(subscriptionId));
        }
    }

    private void updateEid() {
        mDialog.setText(EID_INFO_VALUE_ID, mEuiccManager.getEid());
    }

    private void updateImsRegistrationState() {
        final int subscriptionId = mSubscriptionInfo.getSubscriptionId();
        final PersistableBundle carrierConfig =
            mCarrierConfigManager.getConfigForSubId(subscriptionId);
        // UNISOC: add for bug 993444
        final boolean showImsRegState = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_show_ims_registration_status);
        if (showImsRegState) {
            final boolean isImsRegistered = mTelephonyManager.isImsRegistered(subscriptionId);
            mDialog.setText(IMS_REGISTRATION_STATE_VALUE_ID, mRes.getString(isImsRegistered ?
                R.string.ims_reg_status_registered : R.string.ims_reg_status_not_registered));
        } else {
            mDialog.removeSettingFromScreen(IMS_REGISTRATION_STATE_LABEL_ID);
            mDialog.removeSettingFromScreen(IMS_REGISTRATION_STATE_VALUE_ID);
        }
    }

    private SubscriptionInfo getPhoneSubscriptionInfo(int slotId) {
        final List<SubscriptionInfo> subscriptionInfoList = SubscriptionManager.from(
                mContext).getActiveSubscriptionInfoList();
        /* UNISOC: modify for bug902550 @{ */
        if (subscriptionInfoList != null) {
            for (SubscriptionInfo subInfo : subscriptionInfoList) {
                if (subInfo.getSimSlotIndex() == slotId) {
                    return subInfo;
                }
            }
        }
        return null;
        /* @} */
    }

    @VisibleForTesting
    ServiceState getCurrentServiceState() {
        return mTelephonyManager.getServiceStateForSubscriber(
                mSubscriptionInfo.getSubscriptionId());
    }

    @VisibleForTesting
    int getDbm(SignalStrength signalStrength) {
        return signalStrength.getDbm();
    }

    @VisibleForTesting
    int getAsuLevel(SignalStrength signalStrength) {
        return signalStrength.getAsuLevel();
    }

    @VisibleForTesting
    PhoneStateListener getPhoneStateListener() {
        return new PhoneStateListener(
                mSubscriptionInfo.getSubscriptionId()) {
            @Override
            public void onDataConnectionStateChanged(int state) {
                updateDataState(state);
                updateNetworkType();
            }

            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                updateSignalStrength(signalStrength);
            }

            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                updateNetworkProvider(serviceState);
                updateServiceState(serviceState);
                updateRoamingStatus(serviceState);
                updateNetworkType();
            }
        };
    }

    @VisibleForTesting
    String getPhoneNumber() {
        return DeviceInfoUtils.getFormattedPhoneNumber(mContext, mSubscriptionInfo);
    }

    @VisibleForTesting
    SignalStrength getSignalStrength() {
        return mTelephonyManager.getSignalStrength();
    }

    @VisibleForTesting
    String getSimSerialNumber(int subscriptionId) {
        return mTelephonyManager.getSimSerialNumber(subscriptionId);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "mReceive  action : " + action);
            if (action.equals(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED) ||
                    action.equals(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED)) {
                int slotId = intent.getIntExtra(PhoneConstants.PHONE_KEY, -1);
                int state = intent.getIntExtra(TelephonyManager.EXTRA_SIM_STATE,
                        TelephonyManager.SIM_STATE_UNKNOWN);
                Log.d(TAG, "mReceive  state : " + state);
                if (TelephonyManager.SIM_STATE_ABSENT == state ||
                        TelephonyManager.SIM_STATE_LOADED == state) {
                    if (mDialog != null && slotId == mSlotId) {
                        mDialog.dismiss();
                    }
                }
            }
        }
    };

    private boolean isInService(ServiceState serviceState) {
        if (serviceState != null && (serviceState.getVoiceRegState() == ServiceState.STATE_IN_SERVICE
               || serviceState.getDataRegState() == ServiceState.STATE_IN_SERVICE)) {
            return true;
        }
        return false;
    }

    private synchronized void tryRegisterImsListener() {
        if (mImsManager != null && mImsManager.isWfcEnabledByPlatform()) {
            mIImsServiceEx = ImsManagerEx.getIImsServiceEx();
            if(mIImsServiceEx != null){
                try{
                    if(!mIsImsListenerRegistered){
                        mIsImsListenerRegistered = true;
                        mIImsServiceEx.registerforImsRegisterStateChanged(mImsUtListenerExBinder);
                    }
                    if (mSubscriptionInfo != null) {
                        mImsType = mIImsServiceEx.getCurrentImsFeatureForPhone(
                                mSubscriptionInfo.getSimSlotIndex());
                    }
                    Log.d(TAG, " mImsType: " + mImsType);
                    mIsWifiCallingEnabled = (mImsType ==
                            ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI);
                } catch (RemoteException e){
                    Log.e(TAG, "regiseterforImsException: "+ e);
                }
            }
        }
    }

    private synchronized void unTryRegisterImsListener() {
        if (mImsManager != null && mImsManager.isWfcEnabledByPlatform()) {
            try{
                if(mIsImsListenerRegistered){
                    mIsImsListenerRegistered = false;
                    mIImsServiceEx.unregisterforImsRegisterStateChanged(mImsUtListenerExBinder);
                }
                mImsType = -2;
                mIsWifiCallingEnabled = false;
            }catch(RemoteException e){
                Log.e(TAG, "finalize: " + e);
            }
        }
    }

    private final IImsRegisterListener.Stub mImsUtListenerExBinder = new IImsRegisterListener.Stub(){
        @Override
        public void imsRegisterStateChange(boolean isRegistered){
            Log.d(TAG, "imsRegisterStateChange. isRegistered: " + isRegistered);
            try {
                if (mIImsServiceEx != null && mSubscriptionInfo != null) {
                    mImsType = mIImsServiceEx.getCurrentImsFeatureForPhone(
                            mSubscriptionInfo.getSimSlotIndex());
                    Log.d(TAG, "imsRegisterStateChange. mImsType: " + mImsType);
                    mIsWifiCallingEnabled = (mImsType ==
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI);
                    updateNetworkType();
                } else {
                    return;
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };
}
