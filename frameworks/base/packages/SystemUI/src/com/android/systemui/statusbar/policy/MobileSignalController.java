/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.systemui.statusbar.policy;

import static com.android.systemui.statusbar.policy.NetworkControllerImpl.KEEP_AOSP;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.provider.Settings.Global;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.ims.internal.IImsServiceEx;
import com.android.ims.internal.ImsManagerEx;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.EriInfo;
import com.android.settingslib.graph.SignalDrawable;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;
import com.android.systemui.statusbar.policy.NetworkControllerImpl.Config;
import com.android.systemui.statusbar.policy.NetworkControllerImpl.SubscriptionDefaults;

import java.io.PrintWriter;
import java.util.BitSet;
import java.util.Objects;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.keyguard.KeyguardUpdateMonitor;

public class MobileSignalController extends SignalController<
        MobileSignalController.MobileState, MobileSignalController.MobileIconGroup> {
    private final TelephonyManager mPhone;
    private final SubscriptionDefaults mDefaults;
    private final String mNetworkNameDefault;
    private final String mNetworkNameSeparator;
    private final ContentObserver mObserver;
    @VisibleForTesting
    final PhoneStateListener mPhoneStateListener;
    // Save entire info for logging, we only use the id.
    final SubscriptionInfo mSubscriptionInfo;

    // @VisibleForDemoMode
    final SparseArray<MobileIconGroup> mNetworkToIconLookup;

    // Since some pieces of the phone state are interdependent we store it locally,
    // this could potentially become part of MobileState for simplification/complication
    // of code.
    private int mDataNetType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    private int mDataState = TelephonyManager.DATA_DISCONNECTED;
    private ServiceState mServiceState;
    private SignalStrength mSignalStrength;
    private MobileIconGroup mDefaultIcons;
    private Config mConfig;
    /* UNISOC: Bug 697836 add for volte. @{ */
    private IImsServiceEx mIImsServiceEx;
    public static final int IMS_FEATURE_TYPE_DEFAULT = -2;
    private int mImsType;
    /* UNISOC: bug 880865 @{ */
    private boolean mIsVolteRegisted = false;
    private boolean mIsImsRegisted = false;
    /* @} */
    private boolean mIsVolteIconShow = false;

    private int mVolteIconForSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private int mVolteIconResId = 0;
    /* @} */

    /* UNISOC: add feature statusbar signal cluster view. @{ */
    private boolean mIgnoreAOSPInet;
    public static final int DEFAULT_INET_CONDITION = 1;
    /* @} */
    // UNISOC: bug 688768 Distinguish 3G type icons.
    private boolean mShowCarrier3GIcon;
    /* UNISOC: modify for feature bug693456 @{ */
    private boolean mIsModemResetActive = false;
    private boolean mEnableRefreshSignalStrengths = true;
    private boolean mEnableRefreshServiceState = true;
    private boolean mEnableRefreshMobileDataIndicator = true;
    private boolean mEnableRefreshDataConnectionState = true;
    private boolean mEnableRefreshVoLteServiceState = true;

    private CarrierConfigManager mConfigManager;
    private PersistableBundle mCarrierConfig;

    private KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private KeyguardUpdateMonitorCallback mCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onModemAssert(boolean isModemResetActive) {
            Log.d(mTag, "onModemAssert isModemResetActive = " + isModemResetActive);
            mIsModemResetActive = isModemResetActive;
            if (mIsModemResetActive) {
                mEnableRefreshSignalStrengths = false;
                mEnableRefreshServiceState = false;
                mEnableRefreshMobileDataIndicator = false;
                mEnableRefreshDataConnectionState = false;
                mEnableRefreshVoLteServiceState = false;
            } else {
                mEnableRefreshSignalStrengths = true;
                mEnableRefreshServiceState = true;
                mEnableRefreshMobileDataIndicator = true;
                mEnableRefreshDataConnectionState = true;
                mEnableRefreshVoLteServiceState = true;
                updateTelephony();
            }
        }

    };
    /* @} */
    // TODO: Reduce number of vars passed in, if we have the NetworkController, probably don't
    // need listener lists anymore.
    public MobileSignalController(Context context, Config config, boolean hasMobileData,
            TelephonyManager phone, CallbackHandler callbackHandler,
            NetworkControllerImpl networkController, SubscriptionInfo info,
            SubscriptionDefaults defaults, Looper receiverLooper) {
        super("MobileSignalController(" + info.getSubscriptionId() + ")", context,
                NetworkCapabilities.TRANSPORT_CELLULAR, callbackHandler,
                networkController);
        mNetworkToIconLookup = new SparseArray<>();
        mConfig = config;
        mPhone = phone;
        mDefaults = defaults;
        mSubscriptionInfo = info;
        mPhoneStateListener = new MobilePhoneStateListener(info.getSubscriptionId(),
                receiverLooper);
        mNetworkNameSeparator = getStringIfExists(R.string.status_bar_network_name_separator);
        mNetworkNameDefault = getStringIfExists(
                com.android.internal.R.string.lockscreen_carrier_default);
        mIgnoreAOSPInet = mContext.getResources().getBoolean(
                R.bool.config_ignore_google_inet_validation);
        // UNISOC: bug 688768 Distinguish 3G type icons.
        mShowCarrier3GIcon = mContext.getResources().getBoolean(
                R.bool.config_show_3g_max_rat_icon);
        mConfigManager = (CarrierConfigManager) mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        mapIconSets();

        String networkName = info.getCarrierName() != null ? info.getCarrierName().toString()
                : mNetworkNameDefault;
        mLastState.networkName = mCurrentState.networkName = networkName;
        mLastState.networkNameData = mCurrentState.networkNameData = networkName;
        mLastState.enabled = mCurrentState.enabled = hasMobileData;
        mLastState.iconGroup = mCurrentState.iconGroup = mDefaultIcons;
        // Get initial data sim state.
        updateDataSim();
        mObserver = new ContentObserver(new Handler(receiverLooper)) {
            @Override
            public void onChange(boolean selfChange) {
                updateTelephony();
            }
        };
        // UNISOC: modify for feature bug693456
        mKeyguardUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        /* UNISOC: Modify for Bug 739621 & 905151. @{ */
        ServiceState state = TelephonyManager.from(context)
                .getServiceStateForSubscriber(mSubscriptionInfo.getSubscriptionId());
        if (state != null) {
            mPhoneStateListener.onServiceStateChanged(state);
            Log.d(mTag, "init ss = " + state);
        }
        /* @} */
    }

    public void setConfiguration(Config config) {
        mConfig = config;
        mapIconSets();
        updateTelephony();
    }

    public int getDataContentDescription() {
        return getIcons().mDataContentDescription;
    }

    public void setAirplaneMode(boolean airplaneMode) {
        mCurrentState.airplaneMode = airplaneMode;
        notifyListenersIfNecessary();
    }

    public void setUserSetupComplete(boolean userSetup) {
        mCurrentState.userSetup = userSetup;
        notifyListenersIfNecessary();
    }

    /* UNISOC: Bug 697836 add for volte. @{ */
    private void refreshVolteIndicators(boolean show, int subId, int resId,
                                        SignalCallback callback) {
        if (DEBUG) {
            Log.d(mTag, "refreshVolteIndicators show = " + show
                    + " subId = " + subId + " resId = " + resId + " alwaysShowVolteIcon = "
                    + mConfig.alwaysShowVolteIcon);
        }
        mIsVolteIconShow = show;
        mVolteIconForSubId = subId;
        mVolteIconResId = resId;
        if (mIsModemResetActive
                && !mIsVolteIconShow) {
            Log.i(mTag, "modem reset and volte is disable");
            mEnableRefreshVoLteServiceState = false;
            return;
        } else {
            mEnableRefreshVoLteServiceState = true;
        }
        // UNISOC: bug 886918 always hide volte icon feature.
        if (mEnableRefreshVoLteServiceState && mConfig.alwaysShowVolteIcon) {
            callback.setMobileVolteIndicators(mIsVolteIconShow,
                    mVolteIconForSubId,
                    mVolteIconResId);
        }

    }

    private boolean showVolteIcon(int subId) {
        boolean showVolteIcon = true;
        mCarrierConfig = mConfigManager.getConfigForSubId(subId);
        if (mCarrierConfig != null) {
            showVolteIcon = mCarrierConfig.getBoolean(CarrierConfigManager.KEY_VOLTE_ICON_SHOW);
            Log.d(mTag, "mCarrierConfig != null showVolteIcon " + showVolteIcon);
        }
        return showVolteIcon;
    }

    public void refreshImsState (boolean isRegistered ,SignalCallback callback) {
        Log.d(mTag, "imsRegisterStateChange. isRegistered: " + isRegistered);
        int phoneId = mSubscriptionInfo.getSimSlotIndex();
        mImsType = IMS_FEATURE_TYPE_DEFAULT;
        try {
            mIImsServiceEx = ImsManagerEx.getIImsServiceEx();
            if (mIImsServiceEx != null) {
                mImsType = mIImsServiceEx.getCurrentImsFeatureForPhone(phoneId);;
            } else {
                return;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        /* mImsType: sim card IMS feature type.
         *           Ims is not registered if mImsType equals -1.
         * isVoLTEType: true if mImsType equals 0, which means register on VoLTE.
         * isVoWiFiType: true if mImsType equals 2  which means register on VoWiFi.
         */
        boolean isVoWiFiType =
                mImsType == ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI;
        boolean isVoLTEType =
                    mImsType == ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE;
        Log.d(mTag, "imsRegistered type: " + mImsType);
        /* UNISOC: bug 880865 @{ */
        if (SubscriptionManager.isValidPhoneId(phoneId)) {
            mIsImsRegisted = ImsManagerEx.isImsRegisteredForPhone(phoneId);
            mIsVolteRegisted = ImsManagerEx.isVoLTERegisteredForPhone(phoneId);
            Log.d(mTag, "refreshImsState mIsVolteRegisted: " + mIsVolteRegisted +
                    " phoneId = " + phoneId +
                    " subId = " + mSubscriptionInfo.getSubscriptionId() +
                    " mIsImsRegisted = " + mIsImsRegisted);
            refreshVolteIndicators(mIsImsRegisted && isVoLTEType && showVolteIcon(mSubscriptionInfo.getSubscriptionId()),
                    mSubscriptionInfo.getSubscriptionId(),
                    TelephonyIcons.ICON_VOLTE, callback);
        }
        notifyListeners();
    }

    public void refreshImsIcons(SignalCallback callback) {
        /* UNISOC: bug 880865 @{ */
        int phoneId = mSubscriptionInfo.getSimSlotIndex();
        boolean isVoLTEType =
                mImsType == ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE;
        Log.d(mTag, "refreshImsIcons mIsVolteRegisted: " + mIsVolteRegisted +
                " phoneId = " + phoneId +
                " subId = " + mSubscriptionInfo.getSubscriptionId() +
                " mIsImsRegisted = " + mIsImsRegisted);
        if (SubscriptionManager.isValidPhoneId(phoneId)) {
            refreshVolteIndicators(mIsImsRegisted && isVoLTEType && showVolteIcon(mSubscriptionInfo.getSubscriptionId()),
                    mSubscriptionInfo.getSubscriptionId(),
                    TelephonyIcons.ICON_VOLTE, callback);
        }
    }
    /* @} */

    @Override
    public void updateConnectivity(BitSet connectedTransports, BitSet validatedTransports) {
        boolean isValidated = validatedTransports.get(mTransportType);
        mCurrentState.isDefault = connectedTransports.get(mTransportType);
        // Only show this as not having connectivity if we are default.
        mCurrentState.inetCondition = (isValidated || !mCurrentState.isDefault) ? 1 : 0;
        if (mIgnoreAOSPInet) {
            mCurrentState.inetCondition = DEFAULT_INET_CONDITION;
        }
        notifyListenersIfNecessary();
    }

    public void setCarrierNetworkChangeMode(boolean carrierNetworkChangeMode) {
        mCurrentState.carrierNetworkChangeMode = carrierNetworkChangeMode;
        updateTelephony();
    }

    /**
     * Start listening for phone state changes.
     */
    public void registerListener() {
        mPhone.listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_SERVICE_STATE
                        | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        | PhoneStateListener.LISTEN_CALL_STATE
                        | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                        | PhoneStateListener.LISTEN_DATA_ACTIVITY
                        | PhoneStateListener.LISTEN_CARRIER_NETWORK_CHANGE);
        mContext.getContentResolver().registerContentObserver(Global.getUriFor(Global.MOBILE_DATA),
                true, mObserver);
        mContext.getContentResolver().registerContentObserver(Global.getUriFor(
                Global.MOBILE_DATA + mSubscriptionInfo.getSubscriptionId()),
                true, mObserver);
        mContext.getContentResolver().registerContentObserver(Global.getUriFor(
                Global.PREFERRED_NETWORK_MODE + mSubscriptionInfo.getSubscriptionId()),
                true, mObserver);
        mKeyguardUpdateMonitor.registerCallback(mCallback);
    }

    /**
     * Stop listening for phone state changes.
     */
    public void unregisterListener() {
        mPhone.listen(mPhoneStateListener, 0);
        mContext.getContentResolver().unregisterContentObserver(mObserver);
        if (mKeyguardUpdateMonitor != null) {
            mKeyguardUpdateMonitor.removeCallback(mCallback);
        }
    }

    /**
     * Produce a mapping of data network types to icon groups for simple and quick use in
     * updateTelephony.
     */
    private void mapIconSets() {
        mNetworkToIconLookup.clear();

        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyIcons.THREE_G);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyIcons.THREE_G);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyIcons.THREE_G);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyIcons.THREE_G);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_UMTS, TelephonyIcons.THREE_G);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_TD_SCDMA, TelephonyIcons.THREE_G);

        if (!mConfig.showAtLeast3G) {
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                    TelephonyIcons.UNKNOWN);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EDGE, TelephonyIcons.E);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_CDMA, TelephonyIcons.ONE_X);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyIcons.ONE_X);

            mDefaultIcons = TelephonyIcons.G;
        } else {
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                    TelephonyIcons.THREE_G);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyIcons.THREE_G);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_CDMA,
                    TelephonyIcons.THREE_G);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_1xRTT,
                    TelephonyIcons.THREE_G);
            mDefaultIcons = TelephonyIcons.THREE_G;
        }

        MobileIconGroup hGroup = TelephonyIcons.THREE_G;
        MobileIconGroup hPlusGroup = TelephonyIcons.THREE_G;
        if (mConfig.hspaDataDistinguishable) {
            hGroup = TelephonyIcons.H;
            hPlusGroup = TelephonyIcons.H_PLUS;
        }
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSDPA, hGroup);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSUPA, hGroup);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSPA, hGroup);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSPAP, hPlusGroup);

        if (mConfig.show4gForLte) {
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE, TelephonyIcons.FOUR_G);
            if (mConfig.hideLtePlus) {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE_CA,
                        TelephonyIcons.FOUR_G);
            } else {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE_CA,
                        TelephonyIcons.FOUR_G_PLUS);
            }
        } else {
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE, TelephonyIcons.LTE);
            if (mConfig.hideLtePlus) {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE_CA,
                        TelephonyIcons.LTE);
            } else {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE_CA,
                        TelephonyIcons.LTE_PLUS);
            }
        }
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_IWLAN, TelephonyIcons.WFC);
    }

    private int getNumLevels() {
        if (mConfig.inflateSignalStrengths) {
            return SignalStrength.NUM_SIGNAL_STRENGTH_BINS + 1;
        }
        return SignalStrength.NUM_SIGNAL_STRENGTH_BINS;
    }

    @Override
    public int getCurrentIconId() {
        /* UNISOC: add feature statusbar signal cluster view. @{ */
        if (KEEP_AOSP) {
            if (mCurrentState.iconGroup == TelephonyIcons.CARRIER_NETWORK_CHANGE) {
                return SignalDrawable.getCarrierChangeState(getNumLevels());
            } else if (mCurrentState.connected) {
                int level = mCurrentState.level;
                if (mConfig.inflateSignalStrengths) {
                    level++;
                }
                boolean dataDisabled = mCurrentState.userSetup
                        && mCurrentState.iconGroup == TelephonyIcons.DATA_DISABLED;
                boolean noInternet = mCurrentState.inetCondition == 0;
                boolean cutOut = dataDisabled || noInternet;
                return SignalDrawable.getState(level, getNumLevels(), cutOut);
            } else if (mCurrentState.enabled) {
                return SignalDrawable.getEmptyState(getNumLevels());
            } else {
                return 0;
            }
        } else {
            if (mCurrentState.iconGroup == TelephonyIcons.CARRIER_NETWORK_CHANGE) {
                return SignalDrawable.getCarrierChangeState(getNumLevels());
            } else if (mCurrentState.connected) {
                if (mCurrentState.level <= (getNumLevels() - 1)
                        && mCurrentState.inetCondition == 1) {
                    return TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH[mCurrentState.level];
                } else {
                    return TelephonyIcons.ICON_SIGNAL_ZERO;
                }
            } else if (mCurrentState.enabled) {
                return TelephonyIcons.ICON_NO_NETWORK;
            } else {
                return 0;
            }
        }
        /* @} */
    }

    @Override
    public int getQsCurrentIconId() {
        if (mCurrentState.airplaneMode) {
            return SignalDrawable.getAirplaneModeState(getNumLevels());
        }

        return getCurrentIconId();
    }

    @Override
    public void notifyListeners(SignalCallback callback) {
        MobileIconGroup icons = getIcons();

        String contentDescription = getStringIfExists(getContentDescription());
        String dataContentDescription = getStringIfExists(icons.mDataContentDescription);
        if (mCurrentState.inetCondition == 0) {
            dataContentDescription = mContext.getString(R.string.data_connection_no_internet);
        }
        final boolean dataDisabled = mCurrentState.iconGroup == TelephonyIcons.DATA_DISABLED
                && mCurrentState.userSetup;

        // Show icon in QS when we are connected or data is disabled.
        boolean showDataIcon = mCurrentState.dataConnected || dataDisabled;
        IconState statusIcon = new IconState(mCurrentState.enabled && !mCurrentState.airplaneMode,
                getCurrentIconId(), contentDescription);

        int qsTypeIcon = 0;
        IconState qsIcon = null;
        String description = null;
        // Only send data sim callbacks to QS.
        if (mCurrentState.dataSim) {
            qsTypeIcon = (showDataIcon || mConfig.alwaysShowDataRatIcon) ? icons.mQsDataType : 0;
            qsIcon = new IconState(mCurrentState.enabled
                    && !mCurrentState.isEmergency, getQsCurrentIconId(), contentDescription);
            description = mCurrentState.isEmergency ? null : mCurrentState.networkName;
        }
        boolean activityIn = mCurrentState.dataConnected
                && !mCurrentState.carrierNetworkChangeMode
                && mCurrentState.activityIn;
        boolean activityOut = mCurrentState.dataConnected
                && !mCurrentState.carrierNetworkChangeMode
                && mCurrentState.activityOut;
        showDataIcon &= mCurrentState.isDefault || dataDisabled;
        // UNISOC : Modify for bug 904251
        int typeIcon = (showDataIcon || mConfig.alwaysShowDataRatIcon && hasService()) ? icons.mDataType : 0;
        /* UNISOC: bug 688768 Distinguish 3G type icons . @{ */
        if (mShowCarrier3GIcon && getSpecial3GIcon(mDataNetType) != 0
                && typeIcon != 0) {
            if (mCurrentState.dataConnected) {
                typeIcon = getSpecial3GIcon(mDataNetType);
            } else {
                typeIcon = TelephonyIcons.ICON_3G;
            }
        }
        /* @} */
        if (typeIcon != 0 && hspapShow4G(mSubscriptionInfo.getSubscriptionId())) {
            int getTypeIcon = getHspap4GIcon(mDataNetType);
            if (getTypeIcon != 0) {
                typeIcon = getTypeIcon;
            }
        }
        if (mIsModemResetActive) {
            Log.i(mTag, "modem reset so not refresh status bar");
        } else {
            callback.setMobileDataIndicators(statusIcon, qsIcon, typeIcon, qsTypeIcon,
                    activityIn, activityOut, dataContentDescription, description, icons.mIsWide,
                    mSubscriptionInfo.getSubscriptionId(), mCurrentState.roaming);
            /* UNISOC: add feature statusbar signal cluster view. @{ */
            callback.setMobileDataConnectedIndicators(mCurrentState.dataConnected,
                    mSubscriptionInfo.getSubscriptionId());
            /* @} */
            refreshImsIcons(callback);
        }
    }

    @Override
    protected MobileState cleanState() {
        return new MobileState();
    }

    private boolean hasService() {
        if (mServiceState != null) {
            // Consider the device to be in service if either voice or data
            // service is available. Some SIM cards are marketed as data-only
            // and do not support voice service, and on these SIM cards, we
            // want to show signal bars for data service as well as the "no
            // service" or "emergency calls only" text that indicates that voice
            // is not available.
            switch (mServiceState.getVoiceRegState()) {
                case ServiceState.STATE_POWER_OFF:
                    return false;
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    return mServiceState.getDataRegState() == ServiceState.STATE_IN_SERVICE;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

    private boolean isCdma() {
        return (mSignalStrength != null) && !mSignalStrength.isGsm();
    }

    public boolean isEmergencyOnly() {
        return (mServiceState != null && mServiceState.isEmergencyOnly());
    }

    private boolean isRoaming() {
        // During a carrier change, roaming indications need to be supressed.
        if (isCarrierNetworkChangeActive()) {
            return false;
        }
        if (isCdma() && mServiceState != null) {
            final int iconMode = mServiceState.getCdmaEriIconMode();
            return mServiceState.getCdmaEriIconIndex() != EriInfo.ROAMING_INDICATOR_OFF
                    && (iconMode == EriInfo.ROAMING_ICON_MODE_NORMAL
                    || iconMode == EriInfo.ROAMING_ICON_MODE_FLASH);
        } else {
            return mServiceState != null && mServiceState.getRoaming();
        }
    }

    private boolean isCarrierNetworkChangeActive() {
        return mCurrentState.carrierNetworkChangeMode;
    }

    public void handleBroadcast(Intent intent) {
        String action = intent.getAction();
        if (action.equals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION)) {
            updateNetworkName(intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false),
                    intent.getStringExtra(TelephonyIntents.EXTRA_SPN),
                    intent.getStringExtra(TelephonyIntents.EXTRA_DATA_SPN),
                    intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false),
                    intent.getStringExtra(TelephonyIntents.EXTRA_PLMN));
            notifyListenersIfNecessary();
        } else if (action.equals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)) {
            updateDataSim();
            notifyListenersIfNecessary();
        }
    }

    private void updateDataSim() {
        int defaultDataSub = mDefaults.getDefaultDataSubId();
        if (SubscriptionManager.isValidSubscriptionId(defaultDataSub)) {
            mCurrentState.dataSim = defaultDataSub == mSubscriptionInfo.getSubscriptionId();
        } else {
            // There doesn't seem to be a data sim selected, however if
            // there isn't a MobileSignalController with dataSim set, then
            // QS won't get any callbacks and will be blank.  Instead
            // lets just assume we are the data sim (which will basically
            // show one at random) in QS until one is selected.  The user
            // should pick one soon after, so we shouldn't be in this state
            // for long.
            mCurrentState.dataSim = true;
        }
    }

    /**
     * Updates the network's name based on incoming spn and plmn.
     */
    void updateNetworkName(boolean showSpn, String spn, String dataSpn,
            boolean showPlmn, String plmn) {
        if (CHATTY) {
            Log.d("CarrierLabel", "updateNetworkName showSpn=" + showSpn
                    + " spn=" + spn + " dataSpn=" + dataSpn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn);
        }
        StringBuilder str = new StringBuilder();
        StringBuilder strData = new StringBuilder();
        if (showPlmn && plmn != null) {
            str.append(plmn);
            strData.append(plmn);
        }
        if (showSpn && spn != null) {
            if (str.length() != 0) {
                str.append(mNetworkNameSeparator);
            }
            str.append(spn);
        }
        if (str.length() != 0) {
            mCurrentState.networkName = str.toString();
        } else {
            mCurrentState.networkName = mNetworkNameDefault;
        }
        if (showSpn && dataSpn != null) {
            if (strData.length() != 0) {
                strData.append(mNetworkNameSeparator);
            }
            strData.append(dataSpn);
        }
        if (strData.length() != 0) {
            mCurrentState.networkNameData = strData.toString();
        } else {
            mCurrentState.networkNameData = mNetworkNameDefault;
        }
    }

    /**
     * Updates the current state based on mServiceState, mSignalStrength, mDataNetType,
     * mDataState, and mSimState.  It should be called any time one of these is updated.
     * This will call listeners if necessary.
     */
    private final void updateTelephony() {
        if (DEBUG) {
            Log.d(mTag, "updateTelephonySignalStrength: hasService=" + hasService()
                    + " ss=" + mSignalStrength);
        }
        if (mEnableRefreshServiceState && mEnableRefreshSignalStrengths) {
            mCurrentState.connected = hasService() && mSignalStrength != null;
            if (mCurrentState.connected) {
                if (!mSignalStrength.isGsm() && mConfig.alwaysShowCdmaRssi) {
                    mCurrentState.level = mSignalStrength.getCdmaLevel();
                } else {
                    mCurrentState.level = mSignalStrength.getLevel();
                }
            }
        }
        if (mEnableRefreshDataConnectionState) {
            if (mNetworkToIconLookup.indexOfKey(mDataNetType) >= 0) {
                mCurrentState.iconGroup = mNetworkToIconLookup.get(mDataNetType);
            } else {
                mCurrentState.iconGroup = mDefaultIcons;
            }
        }
        mCurrentState.dataConnected = mCurrentState.connected
                && mDataState == TelephonyManager.DATA_CONNECTED;

        mCurrentState.roaming = isRoaming();
        if (isCarrierNetworkChangeActive()) {
            mCurrentState.iconGroup = TelephonyIcons.CARRIER_NETWORK_CHANGE;
        } else if (isDataDisabled() && !mConfig.alwaysShowDataRatIcon) {
            mCurrentState.iconGroup = TelephonyIcons.DATA_DISABLED;
        }
        if (isEmergencyOnly() != mCurrentState.isEmergency) {
            mCurrentState.isEmergency = isEmergencyOnly();
            mNetworkController.recalculateEmergency();
        }
        // Fill in the network name if we think we have it.
        if (mEnableRefreshServiceState) {
            if (mCurrentState.networkName == mNetworkNameDefault && mServiceState != null
                    && !TextUtils.isEmpty(mServiceState.getOperatorAlphaShort())) {
                mCurrentState.networkName = mServiceState.getOperatorAlphaShort();
            }
        }

        notifyListenersIfNecessary();
    }

    /* UNISOC: Modify for bug 890995. @{ */
    private int getRegNetworkType(ServiceState state) {
        int voiceNetworkType = state.getVoiceNetworkType();
        int dataNetworkType = state.getDataNetworkType();

        int retNetworkType =
                (dataNetworkType == TelephonyManager.NETWORK_TYPE_UNKNOWN)
                        ? voiceNetworkType : dataNetworkType;
        return retNetworkType;
    }
    /* @} */

    private boolean isDataDisabled() {
        return !mPhone.getDataEnabled(mSubscriptionInfo.getSubscriptionId());
    }

    /* UNISOC: bug 688768 Distinguish 3G type icons. @{ */
    private int getSpecial3GIcon(int dataType) {
        if (dataType == TelephonyManager.NETWORK_TYPE_EVDO_0
                || dataType == TelephonyManager.NETWORK_TYPE_EVDO_A
                || dataType == TelephonyManager.NETWORK_TYPE_EVDO_B
                || dataType == TelephonyManager.NETWORK_TYPE_EHRPD
                || dataType == TelephonyManager.NETWORK_TYPE_UMTS) {
            return TelephonyIcons.ICON_3G;
        } else if (dataType == TelephonyManager.NETWORK_TYPE_HSDPA
                || dataType == TelephonyManager.NETWORK_TYPE_HSUPA
                || dataType == TelephonyManager.NETWORK_TYPE_HSPA) {
            return TelephonyIcons.ICON_3G_PLUS;
        } else if (dataType == TelephonyManager.NETWORK_TYPE_HSPAP) {
            return TelephonyIcons.ICON_H_PLUS;
        } else {
            return 0;
        }
    }
    /* @} */

    private int getHspap4GIcon (int dataType) {
        switch (dataType) {
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
                return TelephonyIcons.ICON_3G;
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                int netWorkType = android.provider.Settings.Global.getInt(mContext.getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE + mSubscriptionInfo.getSubscriptionId(),
                        RILConstants.PREFERRED_NETWORK_MODE);
                if (netWorkType == RILConstants.NETWORK_MODE_LTE_GSM_WCDMA) {
                    return TelephonyIcons.ICON_4G;
                } else {
                    return TelephonyIcons.ICON_H_PLUS;
                }
            case TelephonyManager.NETWORK_TYPE_LTE:
                return TelephonyIcons.ICON_4G_LTE;
            case TelephonyManager.NETWORK_TYPE_LTE_CA:
                return TelephonyIcons.ICON_4G_LTE_PLUS;
            default :
                return 0;
        }
    }

    private boolean hspapShow4G (int subId) {
        boolean hspapShow4G = false;
        if (mConfigManager != null) {
            mCarrierConfig = mConfigManager.getConfigForSubId(subId);
            if (mCarrierConfig != null) {
                hspapShow4G = mCarrierConfig.getBoolean(CarrierConfigManager.KEY_HSPAP_SHOW_4G);
                Log.d(mTag, "mCarrierConfig != null hspapShow4G " + hspapShow4G);
            }
        }
        return hspapShow4G;
    }

    @VisibleForTesting
    void setActivity(int activity) {
        mCurrentState.activityIn = activity == TelephonyManager.DATA_ACTIVITY_INOUT
                || activity == TelephonyManager.DATA_ACTIVITY_IN;
        mCurrentState.activityOut = activity == TelephonyManager.DATA_ACTIVITY_INOUT
                || activity == TelephonyManager.DATA_ACTIVITY_OUT;
        notifyListenersIfNecessary();
    }

    @Override
    public void dump(PrintWriter pw) {
        super.dump(pw);
        pw.println("  mSubscription=" + mSubscriptionInfo + ",");
        pw.println("  mServiceState=" + mServiceState + ",");
        pw.println("  mSignalStrength=" + mSignalStrength + ",");
        pw.println("  mDataState=" + mDataState + ",");
        pw.println("  mDataNetType=" + mDataNetType + ",");
    }

    class MobilePhoneStateListener extends PhoneStateListener {
        public MobilePhoneStateListener(int subId, Looper looper) {
            super(subId, looper);
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if (DEBUG) {
                Log.d(mTag, "onSignalStrengthsChanged signalStrength=" + signalStrength +
                        ((signalStrength == null) ? "" : (" level=" + signalStrength.getLevel())));
            }
            mSignalStrength = signalStrength;
            if (mIsModemResetActive
                    && (signalStrength.getLevel() ==
                    SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN)) {
                Log.i(mTag, "modem reset and signal strength is none or unknown");
                mEnableRefreshSignalStrengths = false;
                return;
            } else {
                mEnableRefreshSignalStrengths = true;
            }
            updateTelephony();
        }

        @Override
        public void onServiceStateChanged(ServiceState state) {
            mServiceState = state;
            if (state != null) {
                if (DEBUG) {
                    Log.d(mTag, "onServiceStateChanged voiceState=" + state.getVoiceRegState()
                            + " dataState=" + state.getDataRegState());
                }
                mDataNetType = state.getDataNetworkType();
                /* UNISOC: Modify for bug 890995. @{ */
                if (mConfig.alwaysShowDataRatIcon
                        && mDataNetType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                    mDataNetType = getRegNetworkType(state);
                }
                /* @} */
                if (mDataNetType == TelephonyManager.NETWORK_TYPE_LTE && mServiceState != null &&
                        mServiceState.isUsingCarrierAggregation()) {
                    mDataNetType = TelephonyManager.NETWORK_TYPE_LTE_CA;
                }
            }
            if (mIsModemResetActive
                    && !hasService()) {
                Log.i(mTag, "modem reset and service not ready");
                mEnableRefreshServiceState = false;
                return;
            } else {
                mEnableRefreshServiceState = true;
            }
            updateTelephony();
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            if (DEBUG) {
                Log.d(mTag, "onDataConnectionStateChanged: state=" + state
                        + " type=" + networkType);
            }
            mDataState = state;
            mDataNetType = networkType;
            /* UNISOC: Modify for bug 890995. @{ */
            if (mConfig.alwaysShowDataRatIcon
                    && networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN
                    && mServiceState != null) {
                mDataNetType = getRegNetworkType(mServiceState);
            }
            /* @} */
            if (mDataNetType == TelephonyManager.NETWORK_TYPE_LTE && mServiceState != null &&
                    mServiceState.isUsingCarrierAggregation()) {
                mDataNetType = TelephonyManager.NETWORK_TYPE_LTE_CA;
            }
            if (mIsModemResetActive
                    && state != TelephonyManager.DATA_CONNECTED) {
                Log.i(mTag, "modem reset and data connected");
                mEnableRefreshDataConnectionState = false;
                return;
            } else {
                mEnableRefreshDataConnectionState = true;
            }
            updateTelephony();
        }

        @Override
        public void onDataActivity(int direction) {
            if (DEBUG) {
                Log.d(mTag, "onDataActivity: direction=" + direction);
            }
            setActivity(direction);
        }

        @Override
        public void onCarrierNetworkChange(boolean active) {
            if (DEBUG) {
                Log.d(mTag, "onCarrierNetworkChange: active=" + active);
            }
            mCurrentState.carrierNetworkChangeMode = active;

            updateTelephony();
        }
    };

    static class MobileIconGroup extends SignalController.IconGroup {
        final int mDataContentDescription; // mContentDescriptionDataType
        final int mDataType;
        final boolean mIsWide;
        final int mQsDataType;

        public MobileIconGroup(String name, int[][] sbIcons, int[][] qsIcons, int[] contentDesc,
                int sbNullState, int qsNullState, int sbDiscState, int qsDiscState,
                int discContentDesc, int dataContentDesc, int dataType, boolean isWide) {
            super(name, sbIcons, qsIcons, contentDesc, sbNullState, qsNullState, sbDiscState,
                    qsDiscState, discContentDesc);
            mDataContentDescription = dataContentDesc;
            mDataType = dataType;
            mIsWide = isWide;
            mQsDataType = dataType; // TODO: remove this field
        }
    }

    static class MobileState extends SignalController.State {
        String networkName;
        String networkNameData;
        boolean dataSim;
        boolean dataConnected;
        boolean isEmergency;
        boolean airplaneMode;
        boolean carrierNetworkChangeMode;
        boolean isDefault;
        boolean userSetup;
        boolean roaming;

        @Override
        public void copyFrom(State s) {
            super.copyFrom(s);
            MobileState state = (MobileState) s;
            dataSim = state.dataSim;
            networkName = state.networkName;
            networkNameData = state.networkNameData;
            dataConnected = state.dataConnected;
            isDefault = state.isDefault;
            isEmergency = state.isEmergency;
            airplaneMode = state.airplaneMode;
            carrierNetworkChangeMode = state.carrierNetworkChangeMode;
            userSetup = state.userSetup;
            roaming = state.roaming;
        }

        @Override
        protected void toString(StringBuilder builder) {
            super.toString(builder);
            builder.append(',');
            builder.append("dataSim=").append(dataSim).append(',');
            builder.append("networkName=").append(networkName).append(',');
            builder.append("networkNameData=").append(networkNameData).append(',');
            builder.append("dataConnected=").append(dataConnected).append(',');
            builder.append("roaming=").append(roaming).append(',');
            builder.append("isDefault=").append(isDefault).append(',');
            builder.append("isEmergency=").append(isEmergency).append(',');
            builder.append("airplaneMode=").append(airplaneMode).append(',');
            builder.append("carrierNetworkChangeMode=").append(carrierNetworkChangeMode)
                    .append(',');
            builder.append("userSetup=").append(userSetup);
        }

        @Override
        public boolean equals(Object o) {
            return super.equals(o)
                    && Objects.equals(((MobileState) o).networkName, networkName)
                    && Objects.equals(((MobileState) o).networkNameData, networkNameData)
                    && ((MobileState) o).dataSim == dataSim
                    && ((MobileState) o).dataConnected == dataConnected
                    && ((MobileState) o).isEmergency == isEmergency
                    && ((MobileState) o).airplaneMode == airplaneMode
                    && ((MobileState) o).carrierNetworkChangeMode == carrierNetworkChangeMode
                    && ((MobileState) o).userSetup == userSetup
                    && ((MobileState) o).isDefault == isDefault
                    && ((MobileState) o).roaming == roaming;
        }
    }
}
