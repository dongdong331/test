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

import static android.provider.Telephony.Carriers.ENFORCE_MANAGED_URI;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManagerEx;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManagerEx;
import android.telephony.euicc.EuiccManager;
import android.telephony.ims.feature.ImsFeature;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

import com.android.ims.ImsConfig;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.ims.internal.ImsManagerEx;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TeleUtils;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.util.ArrayUtils;
import com.android.phone.settings.PhoneAccountSettingsFragment;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.WirelessUtils;
import com.android.sprd.telephony.RadioInteractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import android.os.RemoteException;

import com.android.ims.internal.ImsManagerEx;
import com.android.ims.internal.IImsServiceEx;
import com.android.ims.internal.IImsRegisterListener;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LifecycleRegistry;
import static android.arch.lifecycle.Lifecycle.Event.ON_CREATE;
import static android.arch.lifecycle.Lifecycle.Event.ON_DESTROY;

/**
 * "Mobile network settings" screen.  This screen lets you
 * enable/disable mobile data, and control data roaming and other
 * network-specific mobile data features.  It's used on non-voice-capable
 * tablets as well as regular phone devices.
 *
 * Note that this Activity is part of the phone app, even though
 * you reach it from the "Wireless & Networks" section of the main
 * Settings app.  It's not part of the "Call settings" hierarchy that's
 * available from the Phone app (see CallFeaturesSetting for that.)
 */

public class MobileNetworkSettings extends Activity  {

    // CID of the device.
    private static final String KEY_CID = "ro.boot.cid";
    // CIDs of devices which should not show anything related to eSIM.
    private static final String KEY_ESIM_CID_IGNORE = "ro.setupwizard.esim_cid_ignore";
    // System Property which is used to decide whether the default eSIM UI will be shown,
    // the default value is false.
    private static final String KEY_ENABLE_ESIM_UI_BY_DEFAULT =
            "esim.enable_esim_system_ui_by_default";

    // Unisoc: add for Bug985539
    private static final int IVALAID_VT_CALL_RESOLUTION_VALUE = -1;

    private enum TabState {
        NO_TABS, UPDATE, DO_NOTHING
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        MobileNetworkFragment fragment = (MobileNetworkFragment) getFragmentManager()
                .findFragmentById(R.id.network_setting_content);
        if (fragment != null) {
            fragment.onIntentUpdate(intent);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.network_setting);

        FragmentManager fragmentManager = getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.network_setting_content);
        if (fragment == null) {
            fragmentManager.beginTransaction()
                    .add(R.id.network_setting_content, new MobileNetworkFragment())
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        switch (itemId) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Whether to show the entry point to eUICC settings.
     *
     * <p>We show the entry point on any device which supports eUICC as long as either the eUICC
     * was ever provisioned (that is, at least one profile was ever downloaded onto it), or if
     * the user has enabled development mode.
     */
    public static boolean showEuiccSettings(Context context) {
        EuiccManager euiccManager =
                (EuiccManager) context.getSystemService(Context.EUICC_SERVICE);
        if (!euiccManager.isEnabled()) {
            return false;
        }

        ContentResolver cr = context.getContentResolver();

        TelephonyManager tm =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String currentCountry = tm.getNetworkCountryIso().toLowerCase();
        String supportedCountries =
                Settings.Global.getString(cr, Settings.Global.EUICC_SUPPORTED_COUNTRIES);
        boolean inEsimSupportedCountries = false;
        if (TextUtils.isEmpty(currentCountry)) {
            inEsimSupportedCountries = true;
        } else if (!TextUtils.isEmpty(supportedCountries)) {
            List<String> supportedCountryList =
                    Arrays.asList(TextUtils.split(supportedCountries.toLowerCase(), ","));
            if (supportedCountryList.contains(currentCountry)) {
                inEsimSupportedCountries = true;
            }
        }
        final boolean esimIgnoredDevice =
                Arrays.asList(TextUtils.split(SystemProperties.get(KEY_ESIM_CID_IGNORE, ""), ","))
                        .contains(SystemProperties.get(KEY_CID, null));
        final boolean enabledEsimUiByDefault =
                SystemProperties.getBoolean(KEY_ENABLE_ESIM_UI_BY_DEFAULT, true);
        final boolean euiccProvisioned =
                Settings.Global.getInt(cr, Settings.Global.EUICC_PROVISIONED, 0) != 0;
        final boolean inDeveloperMode =
                Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;

        return (inDeveloperMode || euiccProvisioned
                || (!esimIgnoredDevice && enabledEsimUiByDefault && inEsimSupportedCountries));
    }

    /**
     * Whether to show the Enhanced 4G LTE settings in search result.
     *
     * <p>We show this settings if the VoLTE can be enabled by this device and the carrier app
     * doesn't set {@link CarrierConfigManager#KEY_HIDE_ENHANCED_4G_LTE_BOOL} to false.
     */
    public static boolean hideEnhanced4gLteSettings(Context context) {
        List<SubscriptionInfo> sil =
                SubscriptionManager.from(context).getActiveSubscriptionInfoList();
        // Check all active subscriptions. We only hide the button if it's disabled for all
        // active subscriptions.
        if (sil != null) {
            for (SubscriptionInfo subInfo : sil) {
                ImsManager imsManager = ImsManager.getInstance(context, subInfo.getSimSlotIndex());
                PersistableBundle carrierConfig = PhoneGlobals.getInstance()
                        .getCarrierConfigForSubId(subInfo.getSubscriptionId());
                if ((imsManager.isVolteEnabledByPlatform()
                        && imsManager.isVolteProvisionedOnDevice())
                        || carrierConfig.getBoolean(
                        CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL)
                        || !context.getResources()
                        .getBoolean(R.bool.config_show_enhanced_4g_lte_button)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean showApnSettings(Context context) {
        List<SubscriptionInfo> sil =
                SubscriptionManager.from(context).getActiveSubscriptionInfoList();
        if (sil != null) {
            for (SubscriptionInfo subInfo : sil) {
                PersistableBundle carrierConfig = PhoneGlobals.getInstance()
                        .getCarrierConfigForSubId(subInfo.getSubscriptionId());
                if(carrierConfig.getBoolean(CarrierConfigManager.KEY_APN_EXPAND_BOOL)){
                    return true;
                }
            }
        }
        return false;
    }

    // UNISOC: add for bug 1174972
    public static boolean showVtResolutionSettings(Context context) {
        List<SubscriptionInfo> sil =
                SubscriptionManager.from(context).getActiveSubscriptionInfoList();
        if (sil != null) {
            for (SubscriptionInfo subInfo : sil) {
                ImsManager imsManager = ImsManager.getInstance(context, subInfo.getSimSlotIndex());
                if(imsManager.isVolteEnabledByPlatform() || imsManager.isWfcEnabledByPlatform()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns if DPC APNs are enforced.
     */
    public static boolean isDpcApnEnforced(Context context) {
        try (Cursor enforceCursor = context.getContentResolver().query(ENFORCE_MANAGED_URI,
                null, null, null, null)) {
            if (enforceCursor == null || enforceCursor.getCount() != 1) {
                return false;
            }
            enforceCursor.moveToFirst();
            return enforceCursor.getInt(0) > 0;
        }
    }

    /* UNISOC: fix for bug 1002424 @{ */
    private static boolean isImsServiceStateReady(ImsManager imsMgr) {
        boolean isImsServiceStateReady = false;

        try {
            if (imsMgr != null && imsMgr.getImsServiceState() == ImsFeature.STATE_READY) {
                isImsServiceStateReady = true;
            }
        } catch (ImsException ex) {
            Log.e(MobileNetworkFragment.LOG_TAG, "Exception when trying to get ImsServiceStatus: " + ex);
        }

        Log.d( MobileNetworkFragment.LOG_TAG,"isImsServiceStateReady=" + isImsServiceStateReady);
        return isImsServiceStateReady;
    }
    /* @} */

    public static class MobileNetworkFragment extends PreferenceFragment implements
            Preference.OnPreferenceChangeListener, RoamingDialogFragment.RoamingDialogListener,LifecycleOwner{

        // debug data
        private static final String LOG_TAG = "NetworkSettings";
        private static final boolean DBG = true;
        public static final int REQUEST_CODE_EXIT_ECM = 17;

        // Number of active Subscriptions to show tabs
        private static final int TAB_THRESHOLD = 2;

        // Number of last phone number digits shown in Euicc Setting tab
        private static final int NUM_LAST_PHONE_DIGITS = 4;

        // fragment tag for roaming data dialog
        private static final String ROAMING_TAG = "RoamingDialogFragment";

        //String keys for preference lookup
        /* UNISOC: FEATURE_DATA_ALWAYS_ONLINE @{ */
        private static final String BUTTON_ALWAYS_ONLINE_KEY = "data_always_online";
        private static final int DIALOG_DISABLE_MOBILE_DATA_AOL = 100;
        /* @} */
        private static final String BUTTON_PREFERED_NETWORK_MODE = "preferred_network_mode_key";
        private static final String BUTTON_ROAMING_KEY = "button_roaming_key";
        private static final String BUTTON_CDMA_LTE_DATA_SERVICE_KEY = "cdma_lte_data_service_key";
        private static final String BUTTON_ENABLED_NETWORKS_KEY = "enabled_networks_key";
        private static final String BUTTON_4G_LTE_KEY = "enhanced_4g_lte";
        private static final String BUTTON_CELL_BROADCAST_SETTINGS = "cell_broadcast_settings";
        private static final String BUTTON_CARRIER_SETTINGS_KEY = "carrier_settings_key";
        private static final String BUTTON_CDMA_SYSTEM_SELECT_KEY = "cdma_system_select_key";
        private static final String BUTTON_CDMA_SUBSCRIPTION_KEY = "cdma_subscription_key";
        private static final String BUTTON_CARRIER_SETTINGS_EUICC_KEY =
                "carrier_settings_euicc_key";
        private static final String BUTTON_WIFI_CALLING_KEY = "wifi_calling_key";
        private static final String BUTTON_VIDEO_CALLING_KEY = "video_calling_key";
        private static final String BUTTON_MOBILE_DATA_ENABLE_KEY = "mobile_data_enable";
        private static final String BUTTON_DATA_USAGE_KEY = "data_usage_summary";
        private static final String BUTTON_ADVANCED_OPTIONS_KEY = "advanced_options";
        private static final String CATEGORY_CALLING_KEY = "calling";
        private static final String CATEGORY_GSM_APN_EXPAND_KEY = "category_gsm_apn_key";
        private static final String CATEGORY_CDMA_APN_EXPAND_KEY = "category_cdma_apn_key";
        private static final String BUTTON_GSM_APN_EXPAND_KEY = "button_gsm_apn_key";
        private static final String BUTTON_CDMA_APN_EXPAND_KEY = "button_cdma_apn_key";
        //Feature for Uplmn
        private static final String BUTTON_UPLMN_KEY = "uplmn_setting_key";
        private final BroadcastReceiver mPhoneChangeReceiver = new PhoneChangeReceiver();
        private final ContentObserver mDpcEnforcedContentObserver = new DpcApnEnforcedObserver();

        static final int preferredNetworkMode = Phone.PREFERRED_NT_MODE;

        //Information about logical "up" Activity
        private static final String UP_ACTIVITY_PACKAGE = "com.android.settings";
        private static final String UP_ACTIVITY_CLASS =
                "com.android.settings.Settings$WirelessSettingsActivity";
        // SPRD: FEATURE_NATIONAL_DATA_ROAMING
        private static final String BUTTON_PREFERRED_DATA_ROAMING = "preferred_data_roaming_key";

        //Information that needs to save into Bundle.
        private static final String EXPAND_ADVANCED_FIELDS = "expand_advanced_fields";
        //Intent extra to indicate expand all fields.
        private static final String EXPAND_EXTRA = "expandable";

        /* SPRD: FEATURE_RESOLUTION_SETTING @{ */
        private ListPreference mPreferredSetResolution;
        ImsConfig  mImsConfig;
        private static IImsServiceEx mIImsServiceEx;
        private boolean mIsImsListenerRegistered;
        private int mCurrentTab = 0;
        private static final String CURRENT_TAB = "currentTab";
        public static final String VT_RESOLUTION = "vt_resolution";
        private static final String BUTTON_IC_RESOLUTION = "vt_resolution_set_key";
        // SPRD: bug 802740, 802785
        private static final String PROP_CSVT_SUPPORT = "persist.sys.csvt";
        public static final int RESOLUTION_720P = 0;
        public static final int RESOLUTION_VGA_15 = 1;
        public static final int RESOLUTION_VGA_30 = 2;
        public static final int RESOLUTION_QVGA_15 = 3;
        public static final int RESOLUTION_QVGA_30 = 4;
        public static final int RESOLUTION_CIF = 5;
        public static final int RESOLUTION_QCIF = 6;
        public static class VideoQualityConstants {
            public static final int FEATURE_VT_RESOLUTION = 50;
            public static final int NETWORK_VT_RESOLUTION = 51;
        }
        /* @} */

        private SubscriptionManager mSubscriptionManager;
        private TelephonyManager mTelephonyManager;
        private TelephonyManagerEx mTelephonyManagerEx;

        //UI objects
        /* UNISOC: FEATURE_DATA_ALWAYS_ONLINE @{ */
        private SwitchPreference mButtonDataAol;
        private SharedPreferences mSharedPrefs;
        /* @} */
        private AdvancedOptionsPreference mAdvancedOptions;
        private ListPreference mButtonPreferredNetworkMode;
        private ListPreference mButtonEnabledNetworks;
        private RestrictedSwitchPreference mButtonDataRoam;
        private RoamingDialogFragment mRoamingDialogFragment;
        private SwitchPreference mButton4glte;
        private Preference mLteDataServicePref;
        private Preference mEuiccSettingsPref;
        private PreferenceCategory mCallingCategory;
        private Preference mWiFiCallingPref;
        private SwitchPreference mVideoCallingPref;
        private NetworkSelectListPreference mButtonNetworkSelect;
        private MobileDataPreference mMobileDataPref;
        private DataUsagePreference mDataUsagePref;
        //Feature for Uplmn
        private Preference mUplmnPref;
        // SPRD: Add for extended network settings.
        private com.android.phone.ExtendedNetworkSettings mExtendedNetSettings;
        /* UNISOC: Bug 782722 switch default data when in call，call disconnected automatically @{ */
        private PhoneStateListener[] mPhoneStateListeners;
        private ArrayMap<Integer, PhoneStateListener> mPhoneStateListenerLists =
                new ArrayMap<Integer, PhoneStateListener>();
        private int mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
        private int[] mCallState = new int[mPhoneCount];
        /* @} */
        /* UNISOC: bug 830615 @{ */
        private String[] mIccIds;
        /* @} */

        private static final String iface = "rmnet0"; //TODO: this will go away
        private List<SubscriptionInfo> mActiveSubInfos;

        private UserManager mUm;
        private Phone mPhone;
        private ImsManager mImsMgr;
        private MyHandler mHandler;
        private boolean mOkClicked;
        // UNISOC: modify by BUG 788133
        private HashMap<Integer, Boolean> mExpandAdvancedFields = new HashMap<Integer, Boolean>();

        // We assume the the value returned by mTabHost.getCurrentTab() == slotId
        private TabHost mTabHost;

        //GsmUmts options and Cdma options
        GsmUmtsOptions mGsmUmtsOptions;
        CdmaOptions mCdmaOptions;

        private Preference mClickedPreference;
        // UNISOC: modify by Bug 757157
        private AlertDialog mDialog;
        private boolean mShow4GForLTE;
        private boolean mIsGlobalCdma;
        private boolean mUnavailable;
        /* SPRD: FEATURE_NATIONAL_DATA_ROAMING @{ */
        private ListPreference mButtonPreferredDataRoam;
        private boolean mShowNationalDataRoam;
        /* @} */

        private RadioInteractor mRadioInteractor;
        private boolean mSupportSubsidyLock = false;

        /* UNISOC: Bug 882828 @{*/
        private NetworkLifecycleObserver mNetworkLifecycleObserver;
        private LifecycleRegistry mLifecycle = new LifecycleRegistry(this);

        public LifecycleRegistry getLifecycle(){
            return mLifecycle;
        }
        /* @} */

        private class PhoneCallStateListener extends PhoneStateListener {
            /*
             * Enable/disable the 'Enhanced 4G LTE Mode' when in/out of a call
             * and depending on TTY mode and TTY support over VoLTE.
             * @see android.telephony.PhoneStateListener#onCallStateChanged(int,
             * java.lang.String)
             */
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (DBG) log("PhoneStateListener.onCallStateChanged: state=" + state);

                updateEnhanced4gLteState();
                updateWiFiCallState();
                updateVideoCallState();
            }

            /*
             * Listen to different subId if mPhone is updated.
             */
            protected void updatePhone() {
                int newSubId = (mPhone != null
                        && SubscriptionManager.isValidSubscriptionId(mPhone.getSubId()))
                        ? mPhone.getSubId()
                        : SubscriptionManager.INVALID_SUBSCRIPTION_ID;

                // Now, listen to new subId if it's valid.
                mTelephonyManager.listen(this, PhoneStateListener.LISTEN_NONE);

                mSubId = newSubId;
                if (SubscriptionManager.isValidSubscriptionId(mSubId)) {
                    mTelephonyManager.listen(this, PhoneStateListener.LISTEN_CALL_STATE);
                }
            }
        }

        private final PhoneCallStateListener mPhoneStateListener = new PhoneCallStateListener();

        /**
         * Service connection code for the NetworkQueryService.
         * Handles the work of binding to a local object so that we can make
         * the appropriate service calls.
         */

        /** Local service interface */
        private INetworkQueryService mNetworkQueryService = null;

        private void setNetworkQueryService() {
            mButtonNetworkSelect = (NetworkSelectListPreference) getPreferenceScreen()
                    .findPreference(NetworkOperators.BUTTON_NETWORK_SELECT_KEY);
            if (mButtonNetworkSelect != null) {
                mButtonNetworkSelect.setNetworkQueryService(mNetworkQueryService);
            }

        }
        /** Service connection */
        private final ServiceConnection mNetworkQueryServiceConnection = new ServiceConnection() {

            /** Handle the task of binding the local object to the service */
            public void onServiceConnected(ComponentName className, IBinder service) {
                if (DBG) log("connection created, binding local service.");
                mNetworkQueryService = ((NetworkQueryService.LocalBinder) service).getService();
                setNetworkQueryService();
            }

            /** Handle the task of cleaning up the local binding */
            public void onServiceDisconnected(ComponentName className) {
                if (DBG) log("connection disconnected, cleaning local binding.");
                mNetworkQueryService = null;
                setNetworkQueryService();
            }
        };

        private void bindNetworkQueryService() {
            getContext().startService(new Intent(getContext(), NetworkQueryService.class));
            getContext().bindService(new Intent(getContext(), NetworkQueryService.class).setAction(
                        NetworkQueryService.ACTION_LOCAL_BINDER),
                        mNetworkQueryServiceConnection, Context.BIND_AUTO_CREATE);
        }

        private void unbindNetworkQueryService() {
            // unbind the service.
            getContext().unbindService(mNetworkQueryServiceConnection);
        }

        @Override
        public void onPositiveButtonClick(DialogFragment dialog) {
            mPhone.setDataRoamingEnabled(true);
            mButtonDataRoam.setChecked(true);
            MetricsLogger.action(getContext(),
                    getMetricsEventCategory(getPreferenceScreen(), mButtonDataRoam),
                    true);
        }

        /* UNISOC: bug 906818 @{ */
        public void onDialogDismiss(DialogFragment dialog) {
            if (DBG) log("onDialogDismiss");
            boolean hasActiveSubscriptions = hasActiveSubscriptions();
            if (hasActiveSubscriptions) {
                mButtonDataRoam.setEnabled(true);
            }
        }
        /* @} */

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            if (getListView() != null) {
                getListView().setDivider(null);
            }
        }

        public void onIntentUpdate(Intent intent) {
            if (!mUnavailable) {
                updateCurrentTab(intent);
            }
        }

        /**
         * Invoked on each preference click in this hierarchy, overrides
         * PreferenceActivity's implementation.  Used to make sure we track the
         * preference click events.
         */
        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                                             Preference preference) {
            sendMetricsEventPreferenceClicked(preferenceScreen, preference);

            /** TODO: Refactor and get rid of the if's using subclasses */
            final int phoneSubId = mPhone.getSubId();
            if (preference.getKey().equals(BUTTON_4G_LTE_KEY)) {
                return true;
            } else if (mGsmUmtsOptions != null &&
                    mGsmUmtsOptions.preferenceTreeClick(preference) == true) {
                return true;
            } else if (mCdmaOptions != null &&
                    mCdmaOptions.preferenceTreeClick(preference) == true) {
                if (mPhone.isInEcm()) {

                    mClickedPreference = preference;

                    // In ECM mode launch ECM app dialog
                    startActivityForResult(
                            new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null),
                            REQUEST_CODE_EXIT_ECM);
                }
                return true;
            } else if (preference == mButtonPreferredNetworkMode) {
                //displays the value taken from the Settings.System
                int settingsNetworkMode = android.provider.Settings.Global.getInt(
                        mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                        preferredNetworkMode);
                mButtonPreferredNetworkMode.setValue(Integer.toString(settingsNetworkMode));
                return true;
            } else if (preference == mLteDataServicePref) {
                String tmpl = android.provider.Settings.Global.getString(
                        getActivity().getContentResolver(),
                        android.provider.Settings.Global.SETUP_PREPAID_DATA_SERVICE_URL);
                if (!TextUtils.isEmpty(tmpl)) {
                    String imsi = mTelephonyManager.getSubscriberId();
                    if (imsi == null) {
                        imsi = "";
                    }
                    final String url = TextUtils.isEmpty(tmpl) ? null
                            : TextUtils.expandTemplate(tmpl, imsi).toString();
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } else {
                    android.util.Log.e(LOG_TAG, "Missing SETUP_PREPAID_DATA_SERVICE_URL");
                }
                return true;
            }  else if (preference == mButtonEnabledNetworks) {
                // UNISOC: Network type settings for global market
                if("true".equals(SystemProperties.get("persist.vendor.radio.engtest.enable","false"))) {
                    Toast.makeText(this.getActivity(), R.string.network_mode_setting_prompt, Toast.LENGTH_SHORT).show();
                    if(mButtonEnabledNetworks.getDialog() != null){
                        mButtonEnabledNetworks.getDialog().dismiss();
                    }
                } else {
                    int settingsNetworkMode = android.provider.Settings.Global.getInt(
                            mPhone.getContext().getContentResolver(),
                            android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                            preferredNetworkMode);
                    mButtonEnabledNetworks.setValue(Integer.toString(settingsNetworkMode));
                }
                return true;
            } else if (preference == mButtonDataRoam) {
                // Do not disable the preference screen if the user clicks Data roaming.
                return true;
                /* SPRD: FEATURE_NATIONAL_DATA_ROAMING @{ */
            } else if (preference == mButtonPreferredDataRoam) {
                return false;
                /* @} */
            } else if (preference == mEuiccSettingsPref) {
                Intent intent = new Intent(EuiccManager.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS);
                startActivity(intent);
                return true;
            } else if (preference == mWiFiCallingPref || preference == mVideoCallingPref
                    || preference == mMobileDataPref || preference == mDataUsagePref) {
                return false;
                /* UNISOC: FEATURE_DATA_ALWAYS_ONLINE @{ */
            } else if(preference == mButtonDataAol) {
                return false;
                /* @} */
            } else if (preference == mAdvancedOptions) {
                // SPRD: modify by BUG 788133
                mExpandAdvancedFields.put(mCurrentTab, true);
                updateBody();
                return true;
            } /* Feature for Uplmn @{*/ else if (preference == mUplmnPref) {
                if (DBG) log("onPreferenceTreeClick: preference = mUplmnPref");
                Intent intent = new Intent("android.uplmnsettings.action.startuplmnsettings");
                intent.putExtra("sub_id", phoneSubId);
                startActivity(intent);
                return false;
             } /* @} */
            // SPRD: Add for extended network settings.
            else if (mExtendedNetSettings.onPreferenceTreeClick(preference)) {
                return true;
            }
            /* SPRD: FEATURE_RESOLUTION_SETTING @{*/
            else if(preference == mPreferredSetResolution){
            if (preference != null) {
                int phoneId = SubscriptionManager.getPhoneId(phoneSubId);
                if (phoneId == SubscriptionManager.INVALID_PHONE_INDEX) {
                    Log.i(LOG_TAG, "phoneId is invalid");
                    return false;
                }
                // Unisoc: add for Bug985539
                if(setVideoQualityForSpecialOpearter() ) {
                    Log.d(LOG_TAG, "onPreferenceTreeClick return");
                    return true;
                }
                updateImsConfig(phoneId);
            }
            return false;
        /* @} */
        } else{
                // if the button is anything but the simple toggle preference,
                // we'll need to disable all preferences to reject all click
                // events until the sub-activity's UI comes up.
                preferenceScreen.setEnabled(false);
                // Let the intents be launched by the Preference manager
                return false;
            }
        }

        private final SubscriptionManager.OnSubscriptionsChangedListener
                mOnSubscriptionsChangeListener
                = new SubscriptionManager.OnSubscriptionsChangedListener() {
            @Override
            public void onSubscriptionsChanged() {
                if (DBG) log("onSubscriptionsChanged:");
                /* UNISOC: modify by Bug 757157 @{ */
                if (mDialog != null && mDialog.isShowing()) {
                    if (DBG) {
                        log("onSubscriptionsChanged: mDialog dismiss");
                    }
                    mDialog.dismiss();
                }
                if (mMobileDataPref != null
                        && mMobileDataPref.getDialog() != null) {
                    if (mMobileDataPref.getDialog().isShowing()) {
                        mMobileDataPref.getDialog().dismiss();
                    }
                }
                // UNISOC: Bug782722 switch default data when in call call disconnected automatically
                updatePhoneStateListeners();
                initializeSubscriptions();
            }
        };

        private int getSlotIdFromIntent(Intent intent) {
            Bundle data = intent.getExtras();
            int subId = -1;
            if (data != null) {
                subId = data.getInt(Settings.EXTRA_SUB_ID, -1);
            }
            return SubscriptionManager.getSlotIndex(subId);
        }

        private void initializeSubscriptions() {
            final Activity activity = getActivity();
            if (activity == null || activity.isDestroyed()) {
                // Process preferences in activity only if its not destroyed
                return;
            }
            int currentTab = 0;
            // UNISOC: Bug 1010150 Current tab always present the previous inserted sim.
            int currentPhone = 0;
            if (DBG) log("initializeSubscriptions:+");

            // Before updating the the active subscription list check
            // if tab updating is needed as the list is changing.
            List<SubscriptionInfo> sil = mSubscriptionManager.getActiveSubscriptionInfoList();
            MobileNetworkSettings.TabState state = isUpdateTabsNeeded(sil);

            // Update to the active subscription list
            mActiveSubInfos.clear();
            if (sil != null) {
                mActiveSubInfos.addAll(sil);
                // If there is only 1 sim then currenTab should represent slot no. of the sim.
                if (sil.size() == 1) {
                    currentTab = sil.get(0).getSimSlotIndex();
                }
            }

            switch (state) {
                case UPDATE: {
                    if (DBG) log("initializeSubscriptions: UPDATE");
//                    currentTab = mTabHost != null ? mTabHost.getCurrentTab() : 0;
                    // UNISOC: Bug 1010150 Current tab always present the previous inserted sim.
                    currentPhone = mPhone != null ?  mPhone.getPhoneId() : 0;
                    currentTab = mTabHost != null ? mTabHost.getCurrentTab() : currentPhone;

                    mTabHost = (TabHost) getActivity().findViewById(android.R.id.tabhost);
                    /* UNISOC: add for bug708517 @{ */
                    if (mTabHost == null) {
                        // UNISOC: [bug712156] Clear mActiveSubInfos and refresh TabHost during the next UPDATE.
                        mActiveSubInfos.clear();
                        log("initializeSubscriptions: mTabHost is null");
                        break;
                    }
                    /* @} */
                    mTabHost.setup();

                    // Update the tabName. Since the mActiveSubInfos are in slot order
                    // we can iterate though the tabs and subscription info in one loop. But
                    // we need to handle the case where a slot may be empty.

                    Iterator<SubscriptionInfo> siIterator = mActiveSubInfos.listIterator();
                    SubscriptionInfo si = siIterator.hasNext() ? siIterator.next() : null;
                    for (int simSlotIndex = 0; simSlotIndex  < mActiveSubInfos.size();
                         simSlotIndex++) {
                        String tabName;
                        /* UNISOC: add for bug894787 @{ */
                        SubscriptionInfo subInfo = SubscriptionManager.from(getActivity())
                                .getActiveSubscriptionInfoForSimSlotIndex(simSlotIndex);
                        if (subInfo != null) {
                            // Slot is not empty and we match
                            tabName = String.valueOf(subInfo.getDisplayName());
                        } else {
                            // Slot is empty, set name to unknown
                            tabName = getResources().getString(R.string.unknown);
                        }
                        /* @} */
                        if (DBG) {
                            log("initializeSubscriptions:tab=" + simSlotIndex + " name=" + tabName);
                        }
                        /* UNISOC: Bug 968890 Update tab name @{ */
                        if (mTabHost != null && mTabHost.getTabWidget() != null
                                && mTabHost.getTabWidget().getChildTabViewAt(simSlotIndex) != null) {
                            log("update tab" + simSlotIndex +" name and new name is " + tabName);
                            updateTabName(simSlotIndex,tabName);
                        } else {
                            log("add tab" + simSlotIndex +" name and new name is " + tabName);
                            mTabHost.addTab(buildTabSpec(String.valueOf(simSlotIndex), tabName));
                            updateTabName(simSlotIndex,tabName);
                        }
                        /* @} */
                    }

                    mTabHost.setOnTabChangedListener(mTabListener);
                    mTabHost.setCurrentTab(currentTab);
                    break;
                }
                case NO_TABS: {
                    if (DBG) log("initializeSubscriptions: NO_TABS");

                    if (mTabHost != null) {
                        mTabHost.clearAllTabs();
                        mTabHost = null;
                    }
                    break;
                }
                case DO_NOTHING: {
                    if (DBG) log("initializeSubscriptions: DO_NOTHING");
                    if (mTabHost != null) {
                        currentTab = mTabHost.getCurrentTab();
                    }
                    break;
                }
            }
            updatePhone(currentTab);
            updateBody();
            if (DBG) log("initializeSubscriptions:-");
        }
        /* UNISOC: Bug 968890 @{ */
        private void updateTabName(int slotIndex, String tabName){
            TextView tabTitle = (TextView) mTabHost.getTabWidget()
                    .getChildTabViewAt(slotIndex)
                    .findViewById(android.R.id.title);
            tabTitle.setText(tabName);
            tabTitle.setAllCaps(false);
        }
        /* @} */

        private MobileNetworkSettings.TabState isUpdateTabsNeeded(List<SubscriptionInfo> newSil) {
            TabState state = MobileNetworkSettings.TabState.DO_NOTHING;
            if (newSil == null) {
                if (mActiveSubInfos.size() >= TAB_THRESHOLD) {
                    if (DBG) log("isUpdateTabsNeeded: NO_TABS, size unknown and was tabbed");
                    state = MobileNetworkSettings.TabState.NO_TABS;
                }
            } else if (newSil.size() < TAB_THRESHOLD && mActiveSubInfos.size() >= TAB_THRESHOLD) {
                if (DBG) log("isUpdateTabsNeeded: NO_TABS, size went to small");
                state = MobileNetworkSettings.TabState.NO_TABS;
            } else if (newSil.size() >= TAB_THRESHOLD && mActiveSubInfos.size() < TAB_THRESHOLD) {
                if (DBG) log("isUpdateTabsNeeded: UPDATE, size changed");
                state = MobileNetworkSettings.TabState.UPDATE;
            } else if (newSil.size() >= TAB_THRESHOLD) {
                Iterator<SubscriptionInfo> siIterator = mActiveSubInfos.iterator();
                for(SubscriptionInfo newSi : newSil) {
                    SubscriptionInfo curSi = siIterator.next();
                    if (!newSi.getDisplayName().equals(curSi.getDisplayName())) {
                        if (DBG) log("isUpdateTabsNeeded: UPDATE, new name="
                                + newSi.getDisplayName());
                        state = MobileNetworkSettings.TabState.UPDATE;
                        break;
                    }
                }
            }
            if (DBG) {
                Log.i(LOG_TAG, "isUpdateTabsNeeded:- " + state
                        + " newSil.size()=" + ((newSil != null) ? newSil.size() : 0)
                        + " mActiveSubInfos.size()=" + mActiveSubInfos.size());
            }
            return state;
        }

        private ContentObserver mWfcEnableObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                /* SPRD: add for bug807208 @{ */
                log("[mWfcEnableObserver] getActivity: " + getActivity());
                if (getActivity() == null) {
                    return;
                }
                /* @} */
                boolean enabled = ImsManager.isWfcEnabledByUser(getActivity())
                        && ImsManager.isNonTtyOrTtyOnVolteEnabled(getActivity());
                log("[mWfcEnableObserver][wfcEnabled]: " + enabled);
                updateWiFiCallState();
            }
        };

        /* UNISOC: Bug 807273 @{ */
        private ContentObserver mVolteContentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                log("volteContentObserver onChange()");
                /* UNISOC: Bug 900469 @{ */
                mHandler.removeMessages(MyHandler.MESSAGE_VOLTE_CONTENT_CHANGE);
                mHandler.sendEmptyMessageDelayed(MyHandler.MESSAGE_VOLTE_CONTENT_CHANGE, 200);
                /* @} */
            }
        };
        /* @} */

        private TabHost.OnTabChangeListener mTabListener = new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                if (DBG) log("onTabChanged:");
                /* SPRD: modify by BUG 718912 @{ */
                try {
                    mCurrentTab = Integer.parseInt(tabId);
                } catch (NumberFormatException ex) {
                    ex.printStackTrace();
                }
                /* @} */
                // The User has changed tab; update the body.
                updatePhone(Integer.parseInt(tabId));
                updateBody();
                /* SPRD: modify by BUG 960683 @{ */
                if (getListView() != null) {
                    getListView().setSelection(0);
                }
                /* @} */
            }
        };

        private void updatePhone(int slotId) {
            final SubscriptionInfo sir = mSubscriptionManager
                    .getActiveSubscriptionInfoForSimSlotIndex(slotId);
            if (sir != null) {
                int phoneId = SubscriptionManager.getPhoneId(sir.getSubscriptionId());
                if (SubscriptionManager.isValidPhoneId(phoneId)) {
                    mPhone = PhoneFactory.getPhone(phoneId);
                    updateImsConfig(phoneId);//SPRD:add for FEATURE_RESOLUTION_SETTING
                }
            }
            if (mPhone == null) {
                // Do the best we can
                mPhone = PhoneGlobals.getPhone();
            }
            Log.i(LOG_TAG, "updatePhone:- slotId=" + slotId + " sir=" + sir);

            mImsMgr = ImsManager.getInstance(mPhone.getContext(), mPhone.getPhoneId());
            mTelephonyManager = new TelephonyManager(mPhone.getContext(), mPhone.getSubId());
            if (mImsMgr == null) {
                log("updatePhone :: Could not get ImsManager instance!");
            } else if (DBG) {
                log("updatePhone :: mImsMgr=" + mImsMgr);
            }

            mPhoneStateListener.updatePhone();
        }

        private TabHost.TabContentFactory mEmptyTabContent = new TabHost.TabContentFactory() {
            @Override
            public View createTabContent(String tag) {
                return new View(mTabHost.getContext());
            }
        };

        private TabHost.TabSpec buildTabSpec(String tag, String title) {
            return mTabHost.newTabSpec(tag).setIndicator(title).setContent(
                    mEmptyTabContent);
        }

        private void updateCurrentTab(Intent intent) {
            int slotId = getSlotIdFromIntent(intent);
            if (slotId >= 0 && mTabHost != null && mTabHost.getCurrentTab() != slotId) {
                mTabHost.setCurrentTab(slotId);
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            /* UNISOC: modify by BUG 718912 @{ */
            if (mTabHost != null) {
                log("CurrentTab : " + mTabHost.getCurrentTab());
                outState.putInt(CURRENT_TAB, mTabHost.getCurrentTab());
            }
            /* @} */
            // If advanced fields are already expanded, we save it and expand it
            // when it's re-created.
            // UNISOC : modify by BUG 788133
            outState.putSerializable(EXPAND_ADVANCED_FIELDS, mExpandAdvancedFields);
        }

        @Override
        public void onCreate(Bundle icicle) {
            Log.i(LOG_TAG, "onCreate:+");
            super.onCreate(icicle);

            final Activity activity = getActivity();
            if (activity == null || activity.isDestroyed()) {
                Log.e(LOG_TAG, "onCreate:- with no valid activity.");
                return;
            }
            /* UNISOC: Bug 978574 Mobile network does not support screen-split.@{*/
            if (getActivity().isInMultiWindowMode()){
                Toast.makeText(this.getActivity(), R.string.screen_split_not_support,Toast.LENGTH_SHORT).show();
                getActivity().finish();
            }
            /* @} */

            /* UNISOC: modify by BUG 718912 @{ */
            if (icicle != null) {
                mCurrentTab = icicle.getInt(CURRENT_TAB);
                log("onCreate mCurrentTab : " + mCurrentTab);
            }
            /* @} */
            mIccIds = activity.getResources().getStringArray(R.array.iccid_prefix);
            mHandler = new MyHandler();
            mUm = (UserManager) activity.getSystemService(Context.USER_SERVICE);
            mSubscriptionManager = SubscriptionManager.from(activity);
            mTelephonyManager = (TelephonyManager) activity.getSystemService(
                            Context.TELEPHONY_SERVICE);

            mRadioInteractor = new RadioInteractor(activity);
            mSupportSubsidyLock = Resources.getSystem().getBoolean(
                    com.android.internal.R.bool.config_subsidyLock);
            mTelephonyManagerEx = TelephonyManagerEx.from(activity);
            if (icicle != null) {
                // UNISOC: modify by BUG 788133
                mExpandAdvancedFields =
                        (HashMap<Integer, Boolean>) icicle.getSerializable(EXPAND_ADVANCED_FIELDS);
            }
            bindNetworkQueryService();

            addPreferencesFromResource(R.xml.network_setting_fragment);

            mButton4glte = (SwitchPreference)findPreference(BUTTON_4G_LTE_KEY);
            mButton4glte.setOnPreferenceChangeListener(this);

            mCallingCategory = (PreferenceCategory) findPreference(CATEGORY_CALLING_KEY);
            mWiFiCallingPref = findPreference(BUTTON_WIFI_CALLING_KEY);
            /* SPRD: add for bug968647  @{ */
            String wifiTitle = null;
            int primarySubId = SubscriptionManager.getDefaultDataSubscriptionId();
            CarrierConfigManager configManager =
                    (CarrierConfigManager) activity.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            if (configManager != null) {
                PersistableBundle b = configManager.getConfigForSubId(primarySubId);
                if (b != null) {
                    wifiTitle = b.getString(
                        CarrierConfigManagerEx.KEY_WIFI_CALLING_TITLE);
                    log("wifiTitle = " + wifiTitle);
                }
            }
            if (!TextUtils.isEmpty(wifiTitle)) {
                mWiFiCallingPref.setTitle(wifiTitle);
            }
            /* @} */
            mVideoCallingPref = (SwitchPreference) findPreference(BUTTON_VIDEO_CALLING_KEY);
            mMobileDataPref = (MobileDataPreference) findPreference(BUTTON_MOBILE_DATA_ENABLE_KEY);
            mDataUsagePref = (DataUsagePreference) findPreference(BUTTON_DATA_USAGE_KEY);

            try {
                Context con = activity.createPackageContext("com.android.systemui", 0);
                int id = con.getResources().getIdentifier("config_show4GForLTE",
                        "bool", "com.android.systemui");
                mShow4GForLTE = con.getResources().getBoolean(id);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(LOG_TAG, "NameNotFoundException for show4GFotLTE");
                mShow4GForLTE = false;
            }

            //get UI object references
            PreferenceScreen prefSet = getPreferenceScreen();

            /* UNISOC: FEATURE_DATA_ALWAYS_ONLINE @{ */
            mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            mButtonDataAol = (SwitchPreference) prefSet.findPreference(BUTTON_ALWAYS_ONLINE_KEY);
            if (mButtonDataAol != null) {
                mButtonDataAol.setOnPreferenceChangeListener(this);
            }
            /* @} */
            mButtonDataRoam = (RestrictedSwitchPreference) prefSet.findPreference(
                    BUTTON_ROAMING_KEY);
            /* UNISOC: FEATURE_NATIONAL_DATA_ROAMING @{ */
            mButtonPreferredDataRoam = (ListPreference) prefSet.findPreference(
                    BUTTON_PREFERRED_DATA_ROAMING);
            if (mButtonPreferredDataRoam != null) {
                mButtonPreferredDataRoam.setOnPreferenceChangeListener(this);
            }
            /* @} */
            mButtonPreferredNetworkMode = (ListPreference) prefSet.findPreference(
                    BUTTON_PREFERED_NETWORK_MODE);
            mButtonEnabledNetworks = (ListPreference) prefSet.findPreference(
                    BUTTON_ENABLED_NETWORKS_KEY);
            mAdvancedOptions = (AdvancedOptionsPreference) prefSet.findPreference(
                    BUTTON_ADVANCED_OPTIONS_KEY);
            mButtonDataRoam.setOnPreferenceChangeListener(this);

            mLteDataServicePref = prefSet.findPreference(BUTTON_CDMA_LTE_DATA_SERVICE_KEY);

            mEuiccSettingsPref = prefSet.findPreference(BUTTON_CARRIER_SETTINGS_EUICC_KEY);
            mEuiccSettingsPref.setOnPreferenceChangeListener(this);
            // UNISOC: FEATURE_RESOLUTION_SETTING
            mPreferredSetResolution = (ListPreference) prefSet.findPreference(BUTTON_IC_RESOLUTION);
            mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            //Feature for Uplmn
            mUplmnPref = (Preference)prefSet.findPreference(BUTTON_UPLMN_KEY);

            // UNISOC: Add for extended network settings.
            mExtendedNetSettings = new ExtendedNetworkSettings(getActivity(), prefSet);
            // Initialize mActiveSubInfo
            int max = mSubscriptionManager.getActiveSubscriptionInfoCountMax();
            mActiveSubInfos = new ArrayList<SubscriptionInfo>(max);
            // UNISOC: Bug782722 switch default data when in call call disconnected automatically
            mPhoneStateListeners = new PhoneStateListener[max];

            IntentFilter intentFilter = new IntentFilter(
                    TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);
            // UNISOC: Bug826153,the dual volte display error when change the data status.
            intentFilter.addAction(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
            // UNISOC: modify for bug895932
            intentFilter.addAction(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
            activity.registerReceiver(mPhoneChangeReceiver, intentFilter);

            activity.getContentResolver().registerContentObserver(ENFORCE_MANAGED_URI, false,
                    mDpcEnforcedContentObserver);
            tryRegisterImsListener();
            // UNISOC: bug 904704
            prefSet.removeAll();

            // UNISOC: Bug 882828
            mLifecycle.handleLifecycleEvent(ON_CREATE);
            // UNISOC: Bug 923035
            mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
            Log.i(LOG_TAG, "onCreate:-");
        }

        /* UNISOC: Bug 782722 switch default data when in call，
         * call disconnected automatically @{ */
        private void updatePhoneStateListeners() {
            List<SubscriptionInfo> subscriptions =
                    mSubscriptionManager.getActiveSubscriptionInfoList();

            Iterator<Integer> itr = mPhoneStateListenerLists.keySet().iterator();
            while (itr.hasNext()) {
                int subId = itr.next();
                if (subscriptions == null || !containsSubId(subscriptions, subId)) {
                    mTelephonyManager.listen(mPhoneStateListenerLists.get(subId),
                            PhoneStateListener.LISTEN_NONE);
                    itr.remove();
                }
            }
            if (subscriptions == null) {
                subscriptions = Collections.emptyList();
            }

            for (int i = 0; i < subscriptions.size(); i++) {
                final int phoneId = subscriptions.get(i).getSimSlotIndex();
                final int subId = subscriptions.get(i).getSubscriptionId();
                log("updatePhoneStateListeners subid = " + subId + " phoneId = " + phoneId);
                if (!mPhoneStateListenerLists.containsKey(subId)) {
                    mPhoneStateListeners[i] = new PhoneStateListener(subId) {
                        @Override
                        public void onCallStateChanged(int state, String incomingNumber) {
                            log("onCallStateChanged state = " + state + " phoneId = " + phoneId);
                            mCallState[phoneId] = state;
                            updateMobileDataPref(subId);
                        }
                    };
                    mTelephonyManager.listen(mPhoneStateListeners[i],
                            PhoneStateListener.LISTEN_CALL_STATE);
                    mPhoneStateListenerLists.put(subId, mPhoneStateListeners[i]);
                }
            }
        }

        private boolean containsSubId(List<SubscriptionInfo> subInfos, int subId) {
            if (subInfos == null) {
                return false;
            }
            for (int i = 0; i < subInfos.size(); i++) {
                if (subInfos.get(i).getSubscriptionId() == subId) {
                    return true;
                }
            }
            return false;
        }
        /* @} */

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater.inflate(com.android.internal.R.layout.common_tab_settings,
                    container, false);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            if (mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)
                    || !mUm.isSystemUser()) {
                mUnavailable = true;
                getActivity().setContentView(R.layout.telephony_disallowed_preference_screen);
            } else {
                initializeSubscriptions();
                updateCurrentTab(getActivity().getIntent());
            }
        }

        private class PhoneChangeReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(LOG_TAG, "onReceive:");
                /* UNISOC: add for bug895932 @{ */
                String action = intent.getAction();
                if (action != null &&
                        TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED.equals(action)) {
                    int stateExtra = intent.getIntExtra(
                            TelephonyManager.EXTRA_SIM_STATE, -1);
                    if (TelephonyManager.SIM_STATE_ABSENT == stateExtra) {
                        if (mPreferredSetResolution != null
                                && mPreferredSetResolution.getDialog() != null) {
                            if (mPreferredSetResolution.getDialog().isShowing()) {
                                mPreferredSetResolution.getDialog().dismiss();
                            }
                        }

                        if (mRoamingDialogFragment != null
                                && mRoamingDialogFragment.getDialog() != null) {
                            if (mRoamingDialogFragment.getDialog().isShowing()) {
                                mRoamingDialogFragment.getDialog().dismiss();
                            }
                        }
                    }
                    /* UNISOC: 915446 @{ */
                 // When the radio changes (ex: CDMA->GSM), refresh all options.
                } else if (TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED.equals(action)) {
                    updateBody();
                    /* UNISOC: 921099 @{ */
                } else if (TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED
                        .equals(action)) {
                    int defautlDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
                    int currentSubId = mPhone != null ? mPhone.getSubId()
                            : SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                    Log.i(LOG_TAG, "default data change : defautlDataSubId = " + defautlDataSubId
                            + " currentSubId = " + currentSubId);
                    if (mMobileDataPref != null
                            && currentSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                            && currentSubId != defautlDataSubId) {
                        mMobileDataPref.initialize(currentSubId);
                    }
                    updateVideoCallState();
                }
                /* @} */
                /* @} */
                /* @} */
            }
        }

        private class DpcApnEnforcedObserver extends ContentObserver {
            DpcApnEnforcedObserver() {
                super(null);
            }

            @Override
            public void onChange(boolean selfChange) {
                Log.i(LOG_TAG, "DPC enforced onChange:");
                //updateBody();
                mHandler.sendMessage(
                        mHandler.obtainMessage(MyHandler.MESSAGE_EVENT_DPC_APN_CHANGED));
            }
        }

        @Override
        public void onDestroy() {
            unbindNetworkQueryService();
            super.onDestroy();
            if (getActivity() != null) {
                getActivity().unregisterReceiver(mPhoneChangeReceiver);
                getActivity().getContentResolver().unregisterContentObserver(
                        mDpcEnforcedContentObserver);
                /* UNISOC:Bug 782722 switch default data when in call,
                 * call disconnected automatically @{ */
                Iterator<Integer> itr = mPhoneStateListenerLists.keySet().iterator();
                while (itr.hasNext()) {
                    int subId = itr.next();
                    mTelephonyManager.listen(mPhoneStateListenerLists.get(subId),
                                PhoneStateListener.LISTEN_NONE);
                }
                /* @} */
            }
            // UNISOC: Add for extended network settings.
            mExtendedNetSettings.dispose();
            unTryRegisterImsListener();

            /* UNISOC: Bug 882828 @{ */
            mLifecycle.handleLifecycleEvent(ON_DESTROY);
            if (mNetworkLifecycleObserver != null){
                mLifecycle.removeObserver(mNetworkLifecycleObserver);
            }
            /* @} */
            // UNISOC: Bug 923035
             mSubscriptionManager
                  .removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        }

        @Override
        public void onResume() {
            super.onResume();
            Log.i(LOG_TAG, "onResume:+");
            if (mUnavailable) {
                Log.i(LOG_TAG, "onResume:- ignore mUnavailable == false");
                return;
            }

            // upon resumption from the sub-activity, make sure we re-enable the
            // preferences.
            /* UNISOC: Modify for bug993906 @{ */
            boolean isAirplaneModeOn = WirelessUtils.isAirplaneModeOn(mPhone.getContext());
            boolean isRadioBusy = TeleUtils.isRadioBusy(mPhone.getContext());
            getPreferenceScreen().setEnabled(!isAirplaneModeOn && !isRadioBusy);
            /* @} */

            // Set UI state in onResume because a user could go home, launch some
            // app to change this setting's backend, and re-launch this settings app
            // and the UI state would be inconsistent with actual state
            mButtonDataRoam.setChecked(mPhone.getDataRoamingEnabled());
            /* UNISOC: FEATURE_DATA_ALWAYS_ONLINE @{ */
            if (mButtonDataAol != null) {
                mButtonDataAol.setChecked(isMobileDataAlwaysOnline(mPhone.getSubId()));
            }
            /* @} */

            if (getPreferenceScreen().findPreference(BUTTON_PREFERED_NETWORK_MODE) != null
                    || getPreferenceScreen().findPreference(BUTTON_ENABLED_NETWORKS_KEY) != null)  {
                updatePreferredNetworkUIFromDb();
            }
            /* UNISOC: FEATURE_RESOLUTION_SETTING @{ */
            if(mPreferredSetResolution != null){
                getVideoQualityFromPreference();
                // Unisoc: add for Bug985539
                setVideoQualityForSpecialOpearter();
            }
            /* @} */

            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

            // NOTE: Buttons will be enabled/disabled in mPhoneStateListener
            updateEnhanced4gLteState();

            // Video calling and WiFi calling state might have changed.
            updateCallingCategory();
            /* UNISOC: Bug 923035 @{
            mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
            /* @}*/
            mPhone.getContext().getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.WFC_IMS_ENABLED), true,
                    mWfcEnableObserver);
            // UNISOC: Bug 997661
            mPhone.getContext().getContentResolver().registerContentObserver(Uri.withAppendedPath(SubscriptionManager.
                            CONTENT_URI, SubscriptionManager.ENHANCED_4G_MODE_ENABLED),
                    true, mVolteContentObserver);
            // UNISOC: Add for Bug 843666 controll WFC showing via OMA request
            mPhone.getContext().getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(OmaHelper.OMA_WFC_ENABLE + mPhone.getSubId()), true,
                    mWfcShownObserver);
            Log.i(LOG_TAG, "onResume:-");

        }

        /* UNISOC: bug 904704 @{ */
        private boolean isAdvancedOptionShow() {
            return mExpandAdvancedFields.get(mCurrentTab) == null
                    || !mExpandAdvancedFields.get(mCurrentTab);
        }
        /* @} */

        private boolean hasActiveSubscriptions() {
            return mActiveSubInfos.size() > 0;
        }

        /* UNISOC: Bug 840805 disable non-Jio sim's data roam option @{ */
        private void updateRoamEnableState(boolean hasActiveSubscription) {
            boolean enabled = false;
            PersistableBundle carrierConfig =
            PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            enabled = carrierConfig.getBoolean(
                        CarrierConfigManagerEx.KEY_DATA_ROAMING_ENABLED_SETTINGS_BOOL);
            log("updateRoamEnableState() data roaming setting enabled is " + enabled);
            mButtonDataRoam.setEnabled(hasActiveSubscription && enabled);
        }
        /* @} */

        /* UNISOC: Bug 782722 switch default data when in call，call disconnected automatically @{ */
        private boolean isCallStateIdle() {
            for (int i = 0; i < mCallState.length; i++) {
                if (TelephonyManager.CALL_STATE_IDLE != mCallState[i]) {
                    return false;
                }
            }
            return true;
        }
        /* @} */

        private void updateBodyBasicFields(Activity activity, PreferenceScreen prefSet,
                int phoneSubId, boolean hasActiveSubscriptions) {
            Context context = activity.getApplicationContext();

            ActionBar actionBar = activity.getActionBar();
            if (actionBar != null) {
                // android.R.id.home will be triggered in onOptionsItemSelected()
                actionBar.setDisplayHomeAsUpEnabled(true);
            }

            /* UNISOC: modified by bug 807153  @{ */
            mMobileDataPref.initialize(phoneSubId);
            mDataUsagePref.initialize(phoneSubId);
            /* @} */
            prefSet.addPreference(mMobileDataPref);
            prefSet.addPreference(mButtonDataRoam);
            prefSet.addPreference(mDataUsagePref);
            /* SPRD: FEATURE_NATIONAL_DATA_ROAMING @{ */
            PersistableBundle carrierConfig =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            mShowNationalDataRoam = carrierConfig
                    .getBoolean("national_data_roaming_bool");
            if (mShowNationalDataRoam) {
                prefSet.removePreference(mButtonDataRoam);
                prefSet.addPreference(mButtonPreferredDataRoam);
            }
            /* @} */

            // Customized preferences needs to be initialized with subId.
            /* UNISOC: modified by bug 807153  @{
             * mMobileDataPref.initialize(phoneSubId);
             * mDataUsagePref.initialize(phoneSubId);
             */
            mMobileDataPref.setEnabled(hasActiveSubscriptions && (mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY));
            mButtonDataRoam.setEnabled(hasActiveSubscriptions);
            mDataUsagePref.setEnabled(hasActiveSubscriptions);
            /* UNISOC:Bug 782722 switch default data when in call,call disconnected automatically @{*/
            updateMobileDataPref(phoneSubId);
            /* @} */

            // Initialize states of mButtonDataRoam.
            /* SPRD: FEATURE_NATIONAL_DATA_ROAMING @{ */
            if (mShowNationalDataRoam) {
                int roamType = android.provider.Settings.Global.getInt(mPhone.getContext().
                        getContentResolver(),
                        android.provider.Settings.Global.DATA_ROAMING + phoneSubId, 0);
                updatePreferredDataRoamValueAndSummary(roamType);
            } else {
                mButtonDataRoam.setChecked(mPhone.getDataRoamingEnabled());
                mButtonDataRoam.setDisabledByAdmin(false);
                /* UNISOC: Bug 840805 disable non-Jio sim's data roam option @{ */
                boolean isSubsidyLock = (mRadioInteractor.getSubsidyLockStatus(
                        mPhone.getPhoneId()) == 1);
                log("isSubsidyLock = " + isSubsidyLock);
                if (mSupportSubsidyLock && isSubsidyLock) {
                    updateRoamEnableState(hasActiveSubscriptions);
                }
                /* @} */
                if (mButtonDataRoam.isEnabled()) {
                    if (RestrictedLockUtils.hasBaseUserRestriction(context,
                        UserManager.DISALLOW_DATA_ROAMING, UserHandle.myUserId())) {
                       mButtonDataRoam.setEnabled(false);
                    } else {
                       mButtonDataRoam.checkRestrictionAndSetDisabled(
                            UserManager.DISALLOW_DATA_ROAMING);
                    }
                }
            }
        }

        /* UNISOC:Bug 782722 switch default data when in call,call disconnected automatically @{*/
        private void updateMobileDataPref(int subId) {
            int defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
            /* UNISOC: bug 813021 @{ */
            int currentTabSubId = mPhone.getSubId();
            boolean hasActiveSub = hasActiveSubscriptions();
            /* UNISOC: bug 830615 @{ */
            boolean shouldEnableData = canSetData(currentTabSubId);
            List<SubscriptionInfo> sil = mSubscriptionManager.getActiveSubscriptionInfoList();
            log("phoneSubId = " + subId + " defaultDataSubId = " + defaultDataSubId +
                    " hasActiveSub = " + hasActiveSub + " currentTabSubId = " + currentTabSubId
                    + " shouldEnableData = " + shouldEnableData
                    + " isSimStateReady = " + (mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY));
            if (currentTabSubId != defaultDataSubId && mMobileDataPref != null) {
                /* @}*/
                if (mSupportSubsidyLock && sil != null && sil.size() > 1) {
                    mMobileDataPref.setEnabled(isCallStateIdle() && hasActiveSub
                            && shouldEnableData && (mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY)
                            && mTelephonyManagerEx.isPrimaryCardSwitchAllowed());
                } else {
                    mMobileDataPref.setEnabled(isCallStateIdle() && hasActiveSub
                            && shouldEnableData && (mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY));
                }
            }
        }
        /* @} */

        private boolean canSetData(int subId) {
            SubscriptionInfo sir = mSubscriptionManager
                    .getActiveSubscriptionInfo(subId);
            boolean isOperatorPrefer = "true".equals(SystemProperties
                    .get("persist.radio.network.unable","false"));
            log("canSetData() isOperatorPrefer = " + isOperatorPrefer);
            if (sir != null && isOperatorPrefer && !ArrayUtils.isEmpty(mIccIds)) {
                String iccid = sir.getIccId();
                log("canSetData() iccid = " + iccid);
                for (String iccidString : mIccIds) {
                    if (iccidString.startsWith(iccid)) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        }
        /* @}*/

        private void updateBody() {
            final Activity activity = getActivity();
            final PreferenceScreen prefSet = getPreferenceScreen();
            final int phoneSubId = mPhone.getSubId();
            final boolean hasActiveSubscriptions = hasActiveSubscriptions();
            /* UNISOC Bug 904899 @{ */
            Context context = activity.getApplicationContext();
            boolean isAirplaneModeOn = WirelessUtils.isAirplaneModeOn(context);
            boolean isRadioBusy = TeleUtils.isRadioBusy(context);
            prefSet.setEnabled(!isAirplaneModeOn && !isRadioBusy);
            /* @}*/

            if (activity == null || activity.isDestroyed()) {
                Log.e(LOG_TAG, "updateBody with no valid activity.");
                return;
            }

            if (prefSet == null) {
                Log.e(LOG_TAG, "updateBody with no null prefSet.");
                return;
            }

            prefSet.removeAll();

            updateBodyBasicFields(activity, prefSet, phoneSubId, hasActiveSubscriptions);
            // UNISOC: modify by BUG 788133
            if ((mExpandAdvancedFields.get(mCurrentTab) != null)
                    && mExpandAdvancedFields.get(mCurrentTab)) {
                updateBodyAdvancedFields(activity, prefSet, phoneSubId, hasActiveSubscriptions);
            } else {
                prefSet.addPreference(mAdvancedOptions);
            }
        }

        private void updateBodyAdvancedFields(Activity activity, PreferenceScreen prefSet,
                int phoneSubId, boolean hasActiveSubscriptions) {
            boolean isLteOnCdma = mPhone.getLteOnCdmaMode() == PhoneConstants.LTE_ON_CDMA_TRUE;
            int defaultDataSubId = mSubscriptionManager.getDefaultDataSubscriptionId();
            // UNISOC: modify for BUG 806598
            int settingsNetworkMode = android.provider.Settings.Global.getInt(
                    mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                    preferredNetworkMode);
            PersistableBundle carrierConfig =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            //add for unisoc 982460
            boolean isShowVolte = (ImsManagerEx.isDualLteModem()
                    || phoneSubId == defaultDataSubId) && (carrierConfig.getBoolean(
                            CarrierConfigManagerEx.KEY_CARRIER_SHOW_VOLTE_SETTING)
                     || settingsNetworkMode == Phone.NT_MODE_LTE_GSM_WCDMA);
            if (DBG) {
                log("updateBody: isLteOnCdma=" + isLteOnCdma + " phoneSubId=" + phoneSubId
                        + " defaultDataSubId = " + defaultDataSubId);
            }
            prefSet.addPreference(mButtonPreferredNetworkMode);
            //add for unisoc 970988
            prefSet.addPreference(mButtonEnabledNetworks);
            if (isShowVolte && getContext() != null && (getContext().getResources()
                    .getBoolean(R.bool.config_show_4GLTE_button))) {
                prefSet.addPreference(mButton4glte);
            }
            /* UNISOC: FEATURE_DATA_ALWAYS_ONLINE @{ */
            if (enableDataAlwaysOnline() && mButtonDataAol != null) {
                prefSet.addPreference(mButtonDataAol);
            }
            if (mButtonDataAol != null) {
                mButtonDataAol.setChecked(isMobileDataAlwaysOnline(phoneSubId));
                mButtonDataAol.setEnabled(hasActiveSubscriptions);
            }
            /* @} */

            if (showEuiccSettings(getActivity())) {
                prefSet.addPreference(mEuiccSettingsPref);
                String spn = mTelephonyManager.getSimOperatorName();
                if (TextUtils.isEmpty(spn)) {
                    mEuiccSettingsPref.setSummary(null);
                } else {
                    mEuiccSettingsPref.setSummary(spn);
                }
            }
            if(SystemProperties.getBoolean("persist.sys.uplmn", false)){
                if (mUplmnPref != null) {
                    prefSet.addPreference(mUplmnPref);
                    mUplmnPref
                            .setEnabled(mTelephonyManager
                                    .getSimState(SubscriptionManager
                                            .getPhoneId(phoneSubId)) == TelephonyManager.SIM_STATE_READY);
                }
            }

            mIsGlobalCdma = isLteOnCdma
                    && carrierConfig.getBoolean(CarrierConfigManager.KEY_SHOW_CDMA_CHOICES_BOOL);
            if (carrierConfig.getBoolean(
                    CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL)) {
                prefSet.removePreference(mButtonPreferredNetworkMode);
                prefSet.removePreference(mButtonEnabledNetworks);
                prefSet.removePreference(mLteDataServicePref);
            } else if (carrierConfig.getBoolean(CarrierConfigManager
                    .KEY_HIDE_PREFERRED_NETWORK_TYPE_BOOL)
                    && !mPhone.getServiceState().getRoaming()
                    && mPhone.getServiceState().getDataRegState()
                    == ServiceState.STATE_IN_SERVICE) {
                prefSet.removePreference(mButtonPreferredNetworkMode);
                prefSet.removePreference(mButtonEnabledNetworks);

                final int phoneType = mPhone.getPhoneType();
                if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                    updateCdmaOptions(this, prefSet, mPhone);
                } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                    updateGsmUmtsOptions(this, prefSet, phoneSubId, mNetworkQueryService);
                } else {
                    throw new IllegalStateException("Unexpected phone type: " + phoneType);
                }
                // Since pref is being hidden from user, set network mode to default
                // in case it is currently something else. That is possible if user
                // changed the setting while roaming and is now back to home network.
                settingsNetworkMode = preferredNetworkMode;
            } else if (carrierConfig.getBoolean(
                    CarrierConfigManager.KEY_WORLD_PHONE_BOOL) == true) {
                prefSet.removePreference(mButtonEnabledNetworks);
                // set the listener for the mButtonPreferredNetworkMode list preference so we can issue
                // change Preferred Network Mode.
                mButtonPreferredNetworkMode.setOnPreferenceChangeListener(this);

                updateCdmaOptions(this, prefSet, mPhone);
                updateGsmUmtsOptions(this, prefSet, phoneSubId, mNetworkQueryService);
            } else {
                prefSet.removePreference(mButtonPreferredNetworkMode);
                final int phoneType = mPhone.getPhoneType();
                if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                    int lteForced = android.provider.Settings.Global.getInt(
                            mPhone.getContext().getContentResolver(),
                            android.provider.Settings.Global.LTE_SERVICE_FORCED + mPhone.getSubId(),
                            0);

                    if (isLteOnCdma) {
                        if (lteForced == 0) {
                            mButtonEnabledNetworks.setEntries(
                                    R.array.enabled_networks_cdma_choices);
                            mButtonEnabledNetworks.setEntryValues(
                                    R.array.enabled_networks_cdma_values);
                        } else {
                            switch (settingsNetworkMode) {
                                case Phone.NT_MODE_CDMA:
                                case Phone.NT_MODE_CDMA_NO_EVDO:
                                case Phone.NT_MODE_EVDO_NO_CDMA:
                                    mButtonEnabledNetworks.setEntries(
                                            R.array.enabled_networks_cdma_no_lte_choices);
                                    mButtonEnabledNetworks.setEntryValues(
                                            R.array.enabled_networks_cdma_no_lte_values);
                                    break;
                                case Phone.NT_MODE_GLOBAL:
                                case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                                case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                                case Phone.NT_MODE_LTE_ONLY:
                                    mButtonEnabledNetworks.setEntries(
                                            R.array.enabled_networks_cdma_only_lte_choices);
                                    mButtonEnabledNetworks.setEntryValues(
                                            R.array.enabled_networks_cdma_only_lte_values);
                                    break;
                                default:
                                    mButtonEnabledNetworks.setEntries(
                                            R.array.enabled_networks_cdma_choices);
                                    mButtonEnabledNetworks.setEntryValues(
                                            R.array.enabled_networks_cdma_values);
                                    break;
                            }
                        }
                    }
                    updateCdmaOptions(this, prefSet, mPhone);

                } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                    if (isSupportTdscdma()) {
                        mButtonEnabledNetworks.setEntries(
                                R.array.enabled_networks_tdscdma_choices);
                        mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_tdscdma_values);
                    } else if (!carrierConfig.getBoolean(CarrierConfigManager.KEY_PREFER_2G_BOOL)
                            && !getResources().getBoolean(R.bool.config_enabled_lte)) {
                        mButtonEnabledNetworks.setEntries(
                                R.array.enabled_networks_except_gsm_lte_choices);
                        mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_except_gsm_lte_values);
                    } else if (!carrierConfig.getBoolean(CarrierConfigManager.KEY_PREFER_2G_BOOL)) {
                        int select = (mShow4GForLTE == true) ?
                                R.array.enabled_networks_except_gsm_4g_choices
                                : R.array.enabled_networks_except_gsm_choices;
                        mButtonEnabledNetworks.setEntries(select);
                        mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_except_gsm_values);
                    } else if (!getResources().getBoolean(R.bool.config_enabled_lte)) {
                        mButtonEnabledNetworks.setEntries(
                                R.array.enabled_networks_except_lte_choices);
                        mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_except_lte_values);
                    } else if (mIsGlobalCdma) {
                        mButtonEnabledNetworks.setEntries(
                                R.array.enabled_networks_cdma_choices);
                        mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_cdma_values);
                    } else {
                        int select = (mShow4GForLTE == true) ? R.array.enabled_networks_4g_choices
                                : R.array.enabled_networks_choices;
                        mButtonEnabledNetworks.setEntries(select);
                        mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_values);
                    }
                    updateGsmUmtsOptions(this, prefSet, phoneSubId, mNetworkQueryService);
                } else {
                    throw new IllegalStateException("Unexpected phone type: " + phoneType);
                }
                if (isWorldMode()) {
                    mButtonEnabledNetworks.setEntries(
                            R.array.preferred_network_mode_choices_world_mode);
                    mButtonEnabledNetworks.setEntryValues(
                            R.array.preferred_network_mode_values_world_mode);
                }
                mButtonEnabledNetworks.setOnPreferenceChangeListener(this);
                if (DBG) log("settingsNetworkMode: " + settingsNetworkMode);
            }

            final boolean missingDataServiceUrl = TextUtils.isEmpty(
                    android.provider.Settings.Global.getString(activity.getContentResolver(),
                            android.provider.Settings.Global.SETUP_PREPAID_DATA_SERVICE_URL));
            if (!isLteOnCdma || missingDataServiceUrl) {
                prefSet.removePreference(mLteDataServicePref);
            } else {
                android.util.Log.d(LOG_TAG, "keep ltePref");
            }

            updateEnhanced4gLteState();
            updateCallingCategory();
            // SPRD: Add for extended network settings.
            mExtendedNetSettings.updateBody(phoneSubId);

            // Enable link to CMAS app settings depending on the value in config.xml.
            final boolean isCellBroadcastAppLinkEnabled = activity.getResources().getBoolean(
                    com.android.internal.R.bool.config_cellBroadcastAppLinks);
            if (!mUm.isAdminUser() || !isCellBroadcastAppLinkEnabled
                    || mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_CELL_BROADCASTS)) {
                PreferenceScreen root = getPreferenceScreen();
                Preference ps = findPreference(BUTTON_CELL_BROADCAST_SETTINGS);
                if (ps != null) {
                    root.removePreference(ps);
                }
            }

            /**
             * Listen to extra preference changes that need as Metrics events logging.
             */
            if (prefSet.findPreference(BUTTON_CDMA_SYSTEM_SELECT_KEY) != null) {
                prefSet.findPreference(BUTTON_CDMA_SYSTEM_SELECT_KEY)
                        .setOnPreferenceChangeListener(this);
            }

            if (prefSet.findPreference(BUTTON_CDMA_SUBSCRIPTION_KEY) != null) {
                prefSet.findPreference(BUTTON_CDMA_SUBSCRIPTION_KEY)
                        .setOnPreferenceChangeListener(this);
            }

            // Get the networkMode from Settings.System and displays it
            mButtonEnabledNetworks.setValue(Integer.toString(settingsNetworkMode));
            mButtonPreferredNetworkMode.setValue(Integer.toString(settingsNetworkMode));
            UpdatePreferredNetworkModeSummary(settingsNetworkMode);
            UpdateEnabledNetworksValueAndSummary(settingsNetworkMode);
            // Display preferred network type based on what modem returns b/18676277
            mPhone.setPreferredNetworkType(settingsNetworkMode, mHandler
                    .obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));
            /**
             * Enable/disable depending upon if there are any active subscriptions.
             *
             * I've decided to put this enable/disable code at the bottom as the
             * code above works even when there are no active subscriptions, thus
             * putting it afterwards is a smaller change. This can be refined later,
             * but you do need to remember that this all needs to work when subscriptions
             * change dynamically such as when hot swapping sims.
             */
            boolean useVariant4glteTitle = carrierConfig.getBoolean(
                    CarrierConfigManager.KEY_ENHANCED_4G_LTE_TITLE_VARIANT_BOOL);
            int enhanced4glteModeTitleId = useVariant4glteTitle ?
                    R.string.enhanced_4g_lte_mode_title_variant :
                    R.string.enhanced_4g_lte_mode_title;

            mButtonPreferredNetworkMode.setEnabled(hasActiveSubscriptions);
            mButtonEnabledNetworks.setEnabled(hasActiveSubscriptions);
            mButton4glte.setTitle(enhanced4glteModeTitleId);
            mLteDataServicePref.setEnabled(hasActiveSubscriptions);
            Preference ps;
            ps = findPreference(BUTTON_CELL_BROADCAST_SETTINGS);
            if (ps != null) {
                ps.setEnabled(hasActiveSubscriptions);
            }
            ps = findPreference(CATEGORY_GSM_APN_EXPAND_KEY);
            if (ps != null) {
                ps.setEnabled(hasActiveSubscriptions);
            }
            ps = findPreference(CATEGORY_CDMA_APN_EXPAND_KEY);
            if (ps != null) {
                ps.setEnabled(hasActiveSubscriptions);
            }
            ps = findPreference(NetworkOperators.CATEGORY_NETWORK_OPERATORS_KEY);
            if (ps != null) {
                ps.setEnabled(hasActiveSubscriptions);
            }
            ps = findPreference(BUTTON_CARRIER_SETTINGS_KEY);
            if (ps != null) {
                ps.setEnabled(hasActiveSubscriptions);
            }
            ps = findPreference(BUTTON_CDMA_SYSTEM_SELECT_KEY);
            if (ps != null) {
                ps.setEnabled(hasActiveSubscriptions);
            }
            ps = findPreference(CATEGORY_CALLING_KEY);
            if (ps != null) {
                ps.setEnabled(hasActiveSubscriptions);
            }
            mExtendedNetSettings.updatePrefSetState();
        }

        @Override
        public void onPause() {
            super.onPause();
            if (DBG) log("onPause:+");

            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            /* UNISOC: Bug 923035 @{
            mSubscriptionManager
                    .removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
            /* @} */
            mPhone.getContext().getContentResolver().unregisterContentObserver(mWfcEnableObserver);
            // UNISOC: Bug 807273
            getContext().getContentResolver().unregisterContentObserver(mVolteContentObserver);
            // UNISOC: Add for Bug 843666 controll WFC showing via OMA request
            getContext().getContentResolver().unregisterContentObserver(mWfcShownObserver);
            if (DBG) log("onPause:-");
        }

        /**
         * Implemented to support onPreferenceChangeListener to look for preference
         * changes specifically on CLIR.
         *
         * @param preference is the preference to be changed, should be mButtonCLIR.
         * @param objValue should be the value of the selection, NOT its localized
         * display value.
         */
        public boolean onPreferenceChange(Preference preference, Object objValue) {
            sendMetricsEventPreferenceChanged(getPreferenceScreen(), preference, objValue);

            final int phoneSubId = mPhone.getSubId();
            if (preference == mButtonPreferredNetworkMode) {
                //NOTE onPreferenceChange seems to be called even if there is no change
                //Check if the button value is changed from the System.Setting
                mButtonPreferredNetworkMode.setValue((String) objValue);
                int buttonNetworkMode;
                buttonNetworkMode = Integer.parseInt((String) objValue);
                int settingsNetworkMode = android.provider.Settings.Global.getInt(
                        mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                        preferredNetworkMode);
                if (buttonNetworkMode != settingsNetworkMode) {
                    int modemNetworkMode;
                    // if new mode is invalid ignore it
                    switch (buttonNetworkMode) {
                        case Phone.NT_MODE_WCDMA_PREF:
                        case Phone.NT_MODE_GSM_ONLY:
                        case Phone.NT_MODE_WCDMA_ONLY:
                        case Phone.NT_MODE_GSM_UMTS:
                        case Phone.NT_MODE_CDMA:
                        case Phone.NT_MODE_CDMA_NO_EVDO:
                        case Phone.NT_MODE_EVDO_NO_CDMA:
                        case Phone.NT_MODE_GLOBAL:
                        case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                        case Phone.NT_MODE_LTE_GSM_WCDMA:
                        case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                        case Phone.NT_MODE_LTE_ONLY:
                        case Phone.NT_MODE_LTE_WCDMA:
                        case Phone.NT_MODE_TDSCDMA_ONLY:
                        case Phone.NT_MODE_TDSCDMA_WCDMA:
                        case Phone.NT_MODE_LTE_TDSCDMA:
                        case Phone.NT_MODE_TDSCDMA_GSM:
                        case Phone.NT_MODE_LTE_TDSCDMA_GSM:
                        case Phone.NT_MODE_TDSCDMA_GSM_WCDMA:
                        case Phone.NT_MODE_LTE_TDSCDMA_WCDMA:
                        case Phone.NT_MODE_LTE_TDSCDMA_GSM_WCDMA:
                        case Phone.NT_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                        case Phone.NT_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                            // This is one of the modes we recognize
                            modemNetworkMode = buttonNetworkMode;
                            break;
                        default:
                            loge("Invalid Network Mode (" +buttonNetworkMode+ ") chosen. Ignore.");
                            return true;
                    }

                    android.provider.Settings.Global.putInt(
                            mPhone.getContext().getContentResolver(),
                            android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                            buttonNetworkMode );
                    //Set the modem network mode
                    mPhone.setPreferredNetworkType(modemNetworkMode, mHandler
                            .obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));
                }
            } else if (preference == mButtonEnabledNetworks) {
                // UNISOC: Network type settings for global market
                if("true".equals(SystemProperties.get("persist.vendor.radio.engtest.enable","false"))) {
                    Toast.makeText(this.getActivity(), R.string.network_mode_setting_prompt, Toast.LENGTH_SHORT).show();
                } else {
                    mButtonEnabledNetworks.setValue((String) objValue);
                    int buttonNetworkMode;
                    buttonNetworkMode = Integer.parseInt((String) objValue);
                    if (DBG) log("buttonNetworkMode: " + buttonNetworkMode);
                    int settingsNetworkMode = android.provider.Settings.Global.getInt(
                            mPhone.getContext().getContentResolver(),
                            android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                            preferredNetworkMode);
                    if (buttonNetworkMode != settingsNetworkMode) {
                        int modemNetworkMode;
                        // if new mode is invalid ignore it
                        switch (buttonNetworkMode) {
                            case Phone.NT_MODE_WCDMA_PREF:
                            case Phone.NT_MODE_GSM_ONLY:
                            case Phone.NT_MODE_LTE_GSM_WCDMA:
                            case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                            case Phone.NT_MODE_CDMA:
                            case Phone.NT_MODE_CDMA_NO_EVDO:
                            case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                            case Phone.NT_MODE_TDSCDMA_ONLY:
                            case Phone.NT_MODE_TDSCDMA_WCDMA:
                            case Phone.NT_MODE_LTE_TDSCDMA:
                            case Phone.NT_MODE_TDSCDMA_GSM:
                            case Phone.NT_MODE_LTE_TDSCDMA_GSM:
                            case Phone.NT_MODE_TDSCDMA_GSM_WCDMA:
                            case Phone.NT_MODE_LTE_TDSCDMA_WCDMA:
                            case Phone.NT_MODE_LTE_TDSCDMA_GSM_WCDMA:
                            case Phone.NT_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                            case Phone.NT_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                            // Bug 926851 : make more modes as valid.
                            case Phone.NT_MODE_WCDMA_ONLY:
                                // This is one of the modes we recognize
                                modemNetworkMode = buttonNetworkMode;
                                break;
                            default:
                                loge("Invalid Network Mode (" +buttonNetworkMode+ ") chosen. Ignore.");
                                return true;
                        }

                        UpdateEnabledNetworksValueAndSummary(buttonNetworkMode);

                        android.provider.Settings.Global.putInt(
                                mPhone.getContext().getContentResolver(),
                                android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                                buttonNetworkMode );
                        //Set the modem network mode
                        mPhone.setPreferredNetworkType(modemNetworkMode, mHandler
                                .obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));
                    }
                }
                /* UNISOC: FEATURE_DATA_ALWAYS_ONLINE @{ */
            } else if (preference == mButtonDataAol) {
                if (mButtonDataAol.isChecked()) {
                    showDialog(DIALOG_DISABLE_MOBILE_DATA_AOL);
                } else {
                    setMobileDataAlwaysOnline(phoneSubId, true);
                }
                /* @} */
            } else if (preference == mButton4glte) {
                /* UNISOC: bug 817781 @{ */
                int defaultDataSubId = mSubscriptionManager.getDefaultDataSubscriptionId();
                if (phoneSubId == defaultDataSubId &&
                        !isImsTurnOffAllowed(getContext()) && mButton4glte.isChecked()) {
                    Toast.makeText(getContext(), getString(R.string.turn_off_ims_error),
                            Toast.LENGTH_LONG).show();
                    return false;
                }
                /* @} */
                boolean enhanced4gMode = !mButton4glte.isChecked();
                mButton4glte.setChecked(enhanced4gMode);
                mImsMgr.setEnhanced4gLteModeSetting(mButton4glte.isChecked());
            } else if (preference == mButtonDataRoam) {
                if (DBG) log("onPreferenceTreeClick: preference == mButtonDataRoam.");

                //normally called on the toggle click
                if (!mButtonDataRoam.isChecked()) {
                    PersistableBundle carrierConfig =
                            PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
                    if (carrierConfig != null && carrierConfig.getBoolean(
                            CarrierConfigManager.KEY_DISABLE_CHARGE_INDICATION_BOOL)) {
                        mPhone.setDataRoamingEnabled(true);
                        MetricsLogger.action(getContext(),
                                getMetricsEventCategory(getPreferenceScreen(), mButtonDataRoam),
                                true);
                    } else {
                        // MetricsEvent with no value update.
                        MetricsLogger.action(getContext(),
                                getMetricsEventCategory(getPreferenceScreen(), mButtonDataRoam));
                        // First confirm with a warning dialog about charges
                        mOkClicked = false;
                        // UNISOC: bug 906818
                        mButtonDataRoam.setEnabled(false);
                        mRoamingDialogFragment = new RoamingDialogFragment();
                        mRoamingDialogFragment.setPhone(mPhone);
                        mRoamingDialogFragment.show(getFragmentManager(), ROAMING_TAG);
                        // Don't update the toggle unless the confirm button is actually pressed.
                        return false;
                    }
                } else {
                    mPhone.setDataRoamingEnabled(false);
                    MetricsLogger.action(getContext(),
                            getMetricsEventCategory(getPreferenceScreen(), mButtonDataRoam),
                            false);
                    return true;
                }
            } else if (preference == mVideoCallingPref) {
                // If mButton4glte is not checked, mVideoCallingPref should be disabled.
                // So it only makes sense to call phoneMgr.enableVideoCalling if it's checked.
                if (mButton4glte.isChecked()
                        || (mImsMgr.isWfcEnabledByPlatform()
                            && mImsMgr.isWfcEnabledByUser()
                            && phoneSubId == SubscriptionManager.getDefaultDataSubscriptionId())) {
                    mImsMgr.setVtSetting((boolean) objValue);
                    return true;
                } else {
                    loge("mVideoCallingPref should be disabled if mButton4glte is not checked.");
                    mVideoCallingPref.setEnabled(false);
                    // UNISOC: bug 900718
                    mPreferredSetResolution.setEnabled(false);
                    return false;
                }
                /* SPRD: FEATURE_NATIONAL_DATA_ROAMING @{ */
            } else if (preference == mButtonPreferredDataRoam) {
                if(DBG) {
                    log("onPreferenceChange: preference == mButtonPreferredDataRoam.");
                }
                int buttonDataRoam;
                buttonDataRoam = Integer.valueOf((String) objValue).intValue();
                if (DBG) {
                    log("buttonDataRoam: " + buttonDataRoam);
                }
                updatePreferredDataRoamValueAndSummary(buttonDataRoam);
                android.provider.Settings.Global.putInt(
                        mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.DATA_ROAMING + phoneSubId,
                        buttonDataRoam);
                /* @} */
            }
            /* SPRD: FEATURE_RESOLUTION_SETTING @{ */
            else if (preference == mPreferredSetResolution) {
                try {
                    mImsConfig.setConfig(ImsConfig.ConfigConstants.VIDEO_QUALITY,
                            Integer.parseInt((String) objValue) + 1);
                } catch (Exception ie) {

                }
                mPreferredSetResolution.setValueIndex(Integer.parseInt((String) objValue));
                mPreferredSetResolution.setSummary(mPreferredSetResolution.getEntry());
                setVideoQualitytoPreference(Integer.parseInt((String) objValue));
                // Unisoc: add for Bug985539
                setVideoQualityForSpecialOpearter();
            }
            /* @} */
            else if (preference == getPreferenceScreen()
                    .findPreference(BUTTON_CDMA_SYSTEM_SELECT_KEY)
                    || preference == getPreferenceScreen()
                    .findPreference(BUTTON_CDMA_SUBSCRIPTION_KEY)) {
                return true;
            }

            updateBody();
            // always let the preference setting proceed.
            return true;
        }

        private boolean is4gLtePrefEnabled(PersistableBundle carrierConfig) {
            return (mTelephonyManager.getCallState(mPhone.getSubId())
                    == TelephonyManager.CALL_STATE_IDLE)
                    && mImsMgr != null
                    && mImsMgr.isNonTtyOrTtyOnVolteEnabled()
                    && carrierConfig.getBoolean(
                            CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL);
        }

        private class MyHandler extends Handler {

            static final int MESSAGE_SET_PREFERRED_NETWORK_TYPE = 0;
            static final int MESSAGE_IMS_CHANGE = 1;//SPRD:add for IMS
            static final int MESSAGE_VOLTE_CONTENT_CHANGE = 2;// UNISOC: Bug 900469
            static final int MESSAGE_EVENT_DPC_APN_CHANGED = 3;// UNISOC: Bug 968079


            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_SET_PREFERRED_NETWORK_TYPE:
                        handleSetPreferredNetworkTypeResponse(msg);
                        break;
                    /* SPRD: FEATURE_RESOLUTION_SETTINLG @{ */
                    case MESSAGE_IMS_CHANGE:
                        handleImsAboutVideo();
                        break;
                    /* @} */
                    /* UNISOC: Bug 900469 @{ */
                    case MESSAGE_VOLTE_CONTENT_CHANGE:
                        updateEnhanced4gLteState();
                        break;
                    /* @} */
                    /* UNISOC: Bug 968079 @{ */
                    case MESSAGE_EVENT_DPC_APN_CHANGED:
                        Log.d(LOG_TAG,"DPC APN changed call updateBody");
                        updateBody();
                        break;
                    /* @} */
                }
            }

            private void handleSetPreferredNetworkTypeResponse(Message msg) {
                final Activity activity = getActivity();
                if (activity == null || activity.isDestroyed()) {
                    // Access preferences of activity only if it is not destroyed
                    // or if fragment is not attached to an activity.
                    return;
                }

                AsyncResult ar = (AsyncResult) msg.obj;
                final int phoneSubId = mPhone.getSubId();

                if (ar.exception == null) {
                    int networkMode;
                    if (getPreferenceScreen().findPreference(
                            BUTTON_PREFERED_NETWORK_MODE) != null)  {
                        networkMode =  Integer.parseInt(mButtonPreferredNetworkMode.getValue());
                        android.provider.Settings.Global.putInt(
                                mPhone.getContext().getContentResolver(),
                                android.provider.Settings.Global.PREFERRED_NETWORK_MODE
                                        + phoneSubId,
                                networkMode );
                    }
                    if (getPreferenceScreen().findPreference(BUTTON_ENABLED_NETWORKS_KEY) != null) {
                        networkMode = Integer.parseInt(mButtonEnabledNetworks.getValue());
                        android.provider.Settings.Global.putInt(
                                mPhone.getContext().getContentResolver(),
                                android.provider.Settings.Global.PREFERRED_NETWORK_MODE
                                        + phoneSubId,
                                networkMode );
                    }
                } else {
                    Log.i(LOG_TAG, "handleSetPreferredNetworkTypeResponse:" +
                            "exception in setting network mode.");
                    updatePreferredNetworkUIFromDb();
                }
            }
        }

        private void updatePreferredNetworkUIFromDb() {
            final int phoneSubId = mPhone.getSubId();

            int settingsNetworkMode = android.provider.Settings.Global.getInt(
                    mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                    preferredNetworkMode);

            if (DBG) {
                log("updatePreferredNetworkUIFromDb: settingsNetworkMode = " +
                        settingsNetworkMode);
            }

            UpdatePreferredNetworkModeSummary(settingsNetworkMode);
            UpdateEnabledNetworksValueAndSummary(settingsNetworkMode);
            // changes the mButtonPreferredNetworkMode accordingly to settingsNetworkMode
            mButtonPreferredNetworkMode.setValue(Integer.toString(settingsNetworkMode));
        }

        private void UpdatePreferredNetworkModeSummary(int NetworkMode) {
            switch(NetworkMode) {
                case Phone.NT_MODE_TDSCDMA_GSM_WCDMA:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_tdscdma_gsm_wcdma_summary);
                    break;
                case Phone.NT_MODE_TDSCDMA_GSM:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_tdscdma_gsm_summary);
                    break;
                case Phone.NT_MODE_WCDMA_PREF:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_wcdma_perf_summary);
                    break;
                case Phone.NT_MODE_GSM_ONLY:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_gsm_only_summary);
                    break;
                case Phone.NT_MODE_TDSCDMA_WCDMA:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_tdscdma_wcdma_summary);
                    break;
                case Phone.NT_MODE_WCDMA_ONLY:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_wcdma_only_summary);
                    break;
                case Phone.NT_MODE_GSM_UMTS:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_gsm_wcdma_summary);
                    break;
                case Phone.NT_MODE_CDMA:
                    switch (mPhone.getLteOnCdmaMode()) {
                        case PhoneConstants.LTE_ON_CDMA_TRUE:
                            mButtonPreferredNetworkMode.setSummary(
                                    R.string.preferred_network_mode_cdma_summary);
                            break;
                        case PhoneConstants.LTE_ON_CDMA_FALSE:
                        default:
                            mButtonPreferredNetworkMode.setSummary(
                                    R.string.preferred_network_mode_cdma_evdo_summary);
                            break;
                    }
                    break;
                case Phone.NT_MODE_CDMA_NO_EVDO:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_cdma_only_summary);
                    break;
                case Phone.NT_MODE_EVDO_NO_CDMA:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_evdo_only_summary);
                    break;
                case Phone.NT_MODE_LTE_TDSCDMA:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_lte_tdscdma_summary);
                    break;
                case Phone.NT_MODE_LTE_ONLY:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_lte_summary);
                    break;
                case Phone.NT_MODE_LTE_TDSCDMA_GSM:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_lte_tdscdma_gsm_summary);
                    break;
                case Phone.NT_MODE_LTE_TDSCDMA_GSM_WCDMA:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_lte_tdscdma_gsm_wcdma_summary);
                    break;
                case Phone.NT_MODE_LTE_GSM_WCDMA:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_lte_gsm_wcdma_summary);
                    break;
                case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_lte_cdma_evdo_summary);
                    break;
                case Phone.NT_MODE_TDSCDMA_ONLY:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_tdscdma_summary);
                    break;
                case Phone.NT_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_lte_tdscdma_cdma_evdo_gsm_wcdma_summary);
                    break;
                case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                    if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA ||
                            mIsGlobalCdma ||
                            isWorldMode()) {
                        mButtonPreferredNetworkMode.setSummary(
                                R.string.preferred_network_mode_global_summary);
                    } else {
                        mButtonPreferredNetworkMode.setSummary(
                                R.string.preferred_network_mode_lte_summary);
                    }
                    break;
                case Phone.NT_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_tdscdma_cdma_evdo_gsm_wcdma_summary);
                    break;
                case Phone.NT_MODE_GLOBAL:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_cdma_evdo_gsm_wcdma_summary);
                    break;
                case Phone.NT_MODE_LTE_TDSCDMA_WCDMA:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_lte_tdscdma_wcdma_summary);
                    break;
                case Phone.NT_MODE_LTE_WCDMA:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_lte_wcdma_summary);
                    break;
                default:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_global_summary);
            }
        }

        private void UpdateEnabledNetworksValueAndSummary(int NetworkMode) {
            switch (NetworkMode) {
                case Phone.NT_MODE_TDSCDMA_WCDMA:
                case Phone.NT_MODE_TDSCDMA_GSM_WCDMA:
                case Phone.NT_MODE_TDSCDMA_GSM:
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_TDSCDMA_GSM_WCDMA));
                    mButtonEnabledNetworks.setSummary(R.string.network_3G);
                    break;
                case Phone.NT_MODE_WCDMA_ONLY:
                case Phone.NT_MODE_GSM_UMTS:
                case Phone.NT_MODE_WCDMA_PREF:
                    if (!mIsGlobalCdma) {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_WCDMA_PREF));
                        mButtonEnabledNetworks.setSummary(R.string.network_3G);
                    } else {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                        mButtonEnabledNetworks.setSummary(R.string.network_global);
                    }
                    break;
                case Phone.NT_MODE_GSM_ONLY:
                    if (!mIsGlobalCdma) {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_GSM_ONLY));
                        mButtonEnabledNetworks.setSummary(R.string.network_2G);
                    } else {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                        mButtonEnabledNetworks.setSummary(R.string.network_global);
                    }
                    break;
                case Phone.NT_MODE_LTE_GSM_WCDMA:
                    if (isWorldMode()) {
                        mButtonEnabledNetworks.setSummary(
                                R.string.preferred_network_mode_lte_gsm_umts_summary);
                        controlCdmaOptions(false);
                        controlGsmOptions(true);
                        break;
                    }
                case Phone.NT_MODE_LTE_ONLY:
                case Phone.NT_MODE_LTE_WCDMA:
                    if (!mIsGlobalCdma) {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_LTE_GSM_WCDMA));
                        mButtonEnabledNetworks.setSummary((mShow4GForLTE == true)
                                ? R.string.network_4G : R.string.network_lte);
                    } else {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                        mButtonEnabledNetworks.setSummary(R.string.network_global);
                    }
                    break;
                case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                    if (isWorldMode()) {
                        mButtonEnabledNetworks.setSummary(
                                R.string.preferred_network_mode_lte_cdma_summary);
                        controlCdmaOptions(true);
                        controlGsmOptions(false);
                    } else {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_LTE_CDMA_AND_EVDO));
                        mButtonEnabledNetworks.setSummary(R.string.network_lte);
                    }
                    break;
                case Phone.NT_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA));
                    mButtonEnabledNetworks.setSummary(R.string.network_3G);
                    break;
                case Phone.NT_MODE_CDMA:
                case Phone.NT_MODE_EVDO_NO_CDMA:
                case Phone.NT_MODE_GLOBAL:
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_CDMA));
                    mButtonEnabledNetworks.setSummary(R.string.network_3G);
                    break;
                case Phone.NT_MODE_CDMA_NO_EVDO:
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_CDMA_NO_EVDO));
                    mButtonEnabledNetworks.setSummary(R.string.network_1x);
                    break;
                case Phone.NT_MODE_TDSCDMA_ONLY:
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_TDSCDMA_ONLY));
                    mButtonEnabledNetworks.setSummary(R.string.network_3G);
                    break;
                case Phone.NT_MODE_LTE_TDSCDMA_GSM:
                case Phone.NT_MODE_LTE_TDSCDMA_GSM_WCDMA:
                case Phone.NT_MODE_LTE_TDSCDMA:
                case Phone.NT_MODE_LTE_TDSCDMA_WCDMA:
                case Phone.NT_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                    if (isSupportTdscdma()) {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA));
                        mButtonEnabledNetworks.setSummary(R.string.network_lte);
                    } else {
                        if (isWorldMode()) {
                            controlCdmaOptions(true);
                            controlGsmOptions(false);
                        }
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                        if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA ||
                                mIsGlobalCdma ||
                                isWorldMode()) {
                            mButtonEnabledNetworks.setSummary(R.string.network_global);
                        } else {
                            mButtonEnabledNetworks.setSummary((mShow4GForLTE == true)
                                    ? R.string.network_4G : R.string.network_lte);
                        }
                    }
                    break;
                default:
                    String errMsg = "Invalid Network Mode (" + NetworkMode + "). Ignore.";
                    loge(errMsg);
                    mButtonEnabledNetworks.setSummary(errMsg);
            }
            // Bug 926851: Add for extended network settings.
            mExtendedNetSettings.updateEnabledNetworksValueAndSummary(NetworkMode);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            switch(requestCode) {
                case REQUEST_CODE_EXIT_ECM:
                    Boolean isChoiceYes = data.getBooleanExtra(
                            EmergencyCallbackModeExitDialog.EXTRA_EXIT_ECM_RESULT, false);
                    if (isChoiceYes) {
                        // If the phone exits from ECM mode, show the CDMA Options
                        mCdmaOptions.showDialog(mClickedPreference);
                    } else {
                        // do nothing
                    }
                    break;

                default:
                    break;
            }
        }

        private void updateWiFiCallState() {
            if (mWiFiCallingPref == null || mCallingCategory == null) {
                return;
            }

            boolean removePref = false;

            /* UNISOC: bug 900199 @{ */
            if (mPhone.getSubId() == SubscriptionManager.getDefaultDataSubscriptionId()) {
                /* UNISOC: bug 895149 @{ */
                final PhoneAccountHandle simCallManager =
                        TelecomManager.from(mPhone.getContext()).getSimCallManager();

                if (simCallManager != null) {
                    Intent intent = PhoneAccountSettingsFragment.buildPhoneAccountConfigureIntent(
                            mPhone.getContext(), simCallManager);
                    /* @} */
                    if (intent != null) {
                        PackageManager pm = mPhone.getContext().getPackageManager();
                        List<ResolveInfo> resolutions = pm.queryIntentActivities(intent, 0);
                        if (!resolutions.isEmpty()) {
                            mWiFiCallingPref.setTitle(resolutions.get(0).loadLabel(pm));
                            mWiFiCallingPref.setSummary(null);
                            mWiFiCallingPref.setIntent(intent);
                        } else {
                            removePref = true;
                        }
                    } else {
                        removePref = true;
                    }
                } else if (mImsMgr == null
                        || !mImsMgr.isWfcEnabledByPlatform()
                        || !mImsMgr.isWfcProvisionedOnDevice()) {
                    removePref = true;
                } else {
                    int resId = com.android.internal.R.string.wifi_calling_off_summary;
                    if (mImsMgr.isWfcEnabledByUser()) {
                        /* SPRD: add for bug854291 @{ */
                        boolean mShowWifiCallingSummaryOn = false;
                        // Bug 940817 : getContext() NullPointerException
                        CarrierConfigManager configManager =
                                (CarrierConfigManager) mPhone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
                        int primarySubId = SubscriptionManager.getDefaultDataSubscriptionId();
                        if (configManager.getConfigForDefaultPhone() != null) {
                            mShowWifiCallingSummaryOn = configManager.getConfigForSubId(primarySubId).getBoolean(
                                CarrierConfigManagerEx.KEY_SUPPORT_SHOW_WIFI_CALLING_PREFERENCE);
                        }
                        log("mShowWifiCallingSummaryOn " +  mShowWifiCallingSummaryOn);
                        if (mShowWifiCallingSummaryOn) {
                            boolean isRoaming = mTelephonyManager.isNetworkRoaming();
                            int wfcMode = mImsMgr.getWfcMode(isRoaming);

                            switch (wfcMode) {
                                case ImsConfig.WfcModeFeatureValueConstants.WIFI_ONLY:
                                    resId = com.android.internal.R.string.wfc_mode_wifi_only_summary;
                                    break;
                                case ImsConfig.WfcModeFeatureValueConstants.CELLULAR_PREFERRED:
                                    resId = com.android.internal.R.string
                                            .wfc_mode_cellular_preferred_summary;
                                    break;
                                case ImsConfig.WfcModeFeatureValueConstants.WIFI_PREFERRED:
                                    resId = com.android.internal.R.string.wfc_mode_wifi_preferred_summary;
                                    break;
                                default:
                                     if (DBG) log("Unexpected WFC mode value: " + wfcMode);
                            }
                        } else {
                            resId = com.android.internal.R.string.wifi_calling_on_summary;
                        }
                        /* @} */
                    }
                    mWiFiCallingPref.setSummary(resId);
                }
            } else {
                removePref = true;
            }
            /* @} */

            /* UNISOC: Add for Bug 843666 controll WFC showing via OMA request @{ */
            boolean shouldShowWfcByDefault = shouldShowWfcByDefault(mPhone.getSubId());
            // Bug 932493: add mPhone.getContext() avoid crash
            boolean isOmaWfcEnable = ((Settings.Global.getInt(mPhone.getContext().getContentResolver(),
                    OmaHelper.OMA_WFC_ENABLE + mPhone.getSubId(), 0)) == 1 ? true : false);
            log("shouldShowWfcByDefault = " + shouldShowWfcByDefault
                    + "isOmaWfcEnable =" + isOmaWfcEnable );
            boolean carrierAllowShow = shouldShowWfcByDefault
                    || (!shouldShowWfcByDefault && isOmaWfcEnable);
            removePref = removePref && carrierAllowShow;
            /* @} */
            if (removePref) {
                mCallingCategory.removePreference(mWiFiCallingPref);
            } else {
                mCallingCategory.addPreference(mWiFiCallingPref);
                mWiFiCallingPref.setEnabled(mTelephonyManager.getCallState(mPhone.getSubId())
                        == TelephonyManager.CALL_STATE_IDLE && hasActiveSubscriptions());
            }
        }

        /* UNISOC: Add for Bug 843666 controll WFC showing via OMA request @{ */
        private boolean shouldShowWfcByDefault(int subId) {
            if (getContext() == null) {
                return true;
            }
            CarrierConfigManager configManager = (CarrierConfigManager)
                    getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
            if ((configManager != null)
                    && (configManager.getConfigForSubId(subId) != null)) {
                return configManager.getConfigForSubId(subId).getBoolean(
                        CarrierConfigManagerEx.KEY_DEFAULT_SHOW_WIFI_CALL);
            }
            return true;
        }

        private final ContentObserver mWfcShownObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                log("wificalling show observer.");
                updateCallingCategory();
            }
        };
        /* @} */

        private void updateEnhanced4gLteState() {
            if (mButton4glte == null) {
                return;
            }

            PersistableBundle carrierConfig = PhoneGlobals.getInstance()
                    .getCarrierConfigForSubId(mPhone.getSubId());
            /* UNISOC: modify by BUG 921747 @{ */
            boolean enh4glteMode = mImsMgr.isEnhanced4gLteModeSettingEnabledByUser()
                    && mImsMgr.isNonTtyOrTtyOnVolteEnabled();
            log("updateEnhanced4gLteState enh4glteMode = " + enh4glteMode);
            /* @} */
            if ((mImsMgr == null
                    || !isImsServiceStateReady(mImsMgr)
                    || !mImsMgr.isVolteEnabledByPlatform()
                    || !mImsMgr.isVolteProvisionedOnDevice()
                    || carrierConfig.getBoolean(
                            CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL)
                    || getContext() == null
                    || !getContext().getResources()
                            .getBoolean(R.bool.config_show_enhanced_4g_lte_button))) {
                // UNISOC: add for bug916350 & 921747
                mButton4glte.setChecked(enh4glteMode);
                getPreferenceScreen().removePreference(mButton4glte);
            } else {
                mButton4glte.setEnabled(is4gLtePrefEnabled(carrierConfig)
                        && hasActiveSubscriptions());
                mButton4glte.setChecked(enh4glteMode);
            }
        }

        private void updateVideoCallState() {
            /* UNISOC: bug 900718 @{ */
            if (mVideoCallingPref == null || mPreferredSetResolution == null
                    || mCallingCategory == null) {
                return;
            }

            PersistableBundle carrierConfig = PhoneGlobals.getInstance()
                    .getCarrierConfigForSubId(mPhone.getSubId());

            if (mImsMgr != null
                    && isImsServiceStateReady(mImsMgr)
                    && mImsMgr.isVtEnabledByPlatform()
                    && mImsMgr.isVtProvisionedOnDevice()
                    && (carrierConfig.getBoolean(
                        CarrierConfigManager.KEY_IGNORE_DATA_ENABLED_CHANGED_FOR_VIDEO_CALLS)
                        || mPhone.mDcTracker.isDataEnabled())) {
                mCallingCategory.addPreference(mVideoCallingPref);
                mCallingCategory.addPreference(mPreferredSetResolution);
                //UNISOC: fix bug 1174972
                if (!mButton4glte.isChecked()
                        && (!mImsMgr.isWfcEnabledByPlatform()
                            || !mImsMgr.isWfcEnabledByUser()
                            || mPhone.getSubId() != SubscriptionManager.getDefaultDataSubscriptionId())) {
                    mVideoCallingPref.setEnabled(false);
                    mVideoCallingPref.setChecked(false);
                    mPreferredSetResolution.setEnabled(false);
                } else {
                    mVideoCallingPref.setEnabled(mTelephonyManager.getCallState(mPhone.getSubId())
                            == TelephonyManager.CALL_STATE_IDLE && hasActiveSubscriptions());
                    mPreferredSetResolution.setEnabled(mTelephonyManager.getCallState(
                            mPhone.getSubId()) == TelephonyManager.CALL_STATE_IDLE
                            && hasActiveSubscriptions());
                    mVideoCallingPref.setChecked(mImsMgr.isVtEnabledByUser());
                    mVideoCallingPref.setOnPreferenceChangeListener(this);
                    mPreferredSetResolution.setOnPreferenceChangeListener(this);
                }
                // Unisoc: add for Bug985539
                setVideoQualityForSpecialOpearter();
            }else {
                mCallingCategory.removePreference(mVideoCallingPref);
                mCallingCategory.removePreference(mPreferredSetResolution);
                /*  @} */
            }
        }

        private void updateCallingCategory() {
            /* UNISOC: bug 904704 @{ */
            boolean isAdvancedOptionShow = isAdvancedOptionShow();
            log("updateCallingCategory isAdvancedOptionShow = " + isAdvancedOptionShow);
            if (mCallingCategory == null || isAdvancedOptionShow) {
                return;
            }
            /*  @} */

            updateWiFiCallState();
            updateVideoCallState();

            // If all items in calling category is removed, we remove it from
            // the screen. Otherwise we'll see title of the category but nothing
            // is in there.
            if (mCallingCategory.getPreferenceCount() == 0) {
                getPreferenceScreen().removePreference(mCallingCategory);
            } else {
                getPreferenceScreen().addPreference(mCallingCategory);
            }
        }

        private static void log(String msg) {
            Log.d(LOG_TAG, msg);
        }

        private static void loge(String msg) {
            Log.e(LOG_TAG, msg);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            final int itemId = item.getItemId();
            if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
                // Commenting out "logical up" capability. This is a workaround for issue 5278083.
                //
                // Settings app may not launch this activity via UP_ACTIVITY_CLASS but the other
                // Activity that looks exactly same as UP_ACTIVITY_CLASS ("SubSettings" Activity).
                // At that moment, this Activity launches UP_ACTIVITY_CLASS on top of the Activity.
                // which confuses users.
                // TODO: introduce better mechanism for "up" capability here.
            /*Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(UP_ACTIVITY_PACKAGE, UP_ACTIVITY_CLASS);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);*/
                getActivity().finish();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        private boolean isWorldMode() {
            boolean worldModeOn = false;
            final String configString = getResources().getString(R.string.config_world_mode);

            if (!TextUtils.isEmpty(configString)) {
                String[] configArray = configString.split(";");
                // Check if we have World mode configuration set to True only or config is set to True
                // and SIM GID value is also set and matches to the current SIM GID.
                if (configArray != null &&
                        ((configArray.length == 1 && configArray[0].equalsIgnoreCase("true"))
                                || (configArray.length == 2 && !TextUtils.isEmpty(configArray[1])
                                && mTelephonyManager != null
                                && configArray[1].equalsIgnoreCase(
                                        mTelephonyManager.getGroupIdLevel1())))) {
                    worldModeOn = true;
                }
            }

            Log.d(LOG_TAG, "isWorldMode=" + worldModeOn);

            return worldModeOn;
        }

        private void controlGsmOptions(boolean enable) {
            PreferenceScreen prefSet = getPreferenceScreen();
            if (prefSet == null) {
                return;
            }

            updateGsmUmtsOptions(this, prefSet, mPhone.getSubId(), mNetworkQueryService);

            PreferenceCategory networkOperatorCategory =
                    (PreferenceCategory) prefSet.findPreference(
                            NetworkOperators.CATEGORY_NETWORK_OPERATORS_KEY);
            Preference carrierSettings = prefSet.findPreference(BUTTON_CARRIER_SETTINGS_KEY);
            if (networkOperatorCategory != null) {
                if (enable) {
                    networkOperatorCategory.setEnabled(true);
                } else {
                    prefSet.removePreference(networkOperatorCategory);
                }
            }
            if (carrierSettings != null) {
                prefSet.removePreference(carrierSettings);
            }
        }

        private void controlCdmaOptions(boolean enable) {
            PreferenceScreen prefSet = getPreferenceScreen();
            if (prefSet == null) {
                return;
            }
            updateCdmaOptions(this, prefSet, mPhone);
            CdmaSystemSelectListPreference systemSelect =
                    (CdmaSystemSelectListPreference)prefSet.findPreference
                            (BUTTON_CDMA_SYSTEM_SELECT_KEY);
            if (systemSelect != null) {
                systemSelect.setEnabled(enable);
            }
        }

        private boolean isSupportTdscdma() {
            if (getResources().getBoolean(R.bool.config_support_tdscdma)) {
                return true;
            }

            String operatorNumeric = mPhone.getServiceState().getOperatorNumeric();
            String[] numericArray = getResources().getStringArray(
                    R.array.config_support_tdscdma_roaming_on_networks);
            if (numericArray.length == 0 || operatorNumeric == null) {
                return false;
            }
            for (String numeric : numericArray) {
                if (operatorNumeric.equals(numeric)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Metrics events related methods. it takes care of all preferences possible in this
         * fragment(except a few that log on their own). It doesn't only include preferences in
         * network_setting_fragment.xml, but also those defined in GsmUmtsOptions and CdmaOptions.
         */
        private void sendMetricsEventPreferenceClicked(
                PreferenceScreen preferenceScreen, Preference preference) {
            final int category = getMetricsEventCategory(preferenceScreen, preference);
            if (category == MetricsEvent.VIEW_UNKNOWN) {
                return;
            }

            // Send MetricsEvent on click. It includes preferences other than SwitchPreferences,
            // which send MetricsEvent in onPreferenceChange.
            // For ListPreferences, we log it here without a value, only indicating it's clicked to
            // open the list dialog. When a value is chosen, another MetricsEvent is logged with
            // new value in onPreferenceChange.
            if (preference == mLteDataServicePref || preference == mDataUsagePref
                    || preference == mEuiccSettingsPref || preference == mAdvancedOptions
                    || preference == mWiFiCallingPref || preference == mButtonPreferredNetworkMode
                    || preference == mButtonEnabledNetworks
                    || preference == preferenceScreen.findPreference(BUTTON_CDMA_SYSTEM_SELECT_KEY)
                    || preference == preferenceScreen.findPreference(BUTTON_CDMA_SUBSCRIPTION_KEY)
                    || preference == preferenceScreen.findPreference(BUTTON_GSM_APN_EXPAND_KEY)
                    || preference == preferenceScreen.findPreference(BUTTON_CDMA_APN_EXPAND_KEY)
                    || preference == preferenceScreen.findPreference(BUTTON_CARRIER_SETTINGS_KEY)) {
                MetricsLogger.action(getContext(), category);
            }
        }

        private void sendMetricsEventPreferenceChanged(
                PreferenceScreen preferenceScreen, Preference preference, Object newValue) {
            final int category = getMetricsEventCategory(preferenceScreen, preference);
            if (category == MetricsEvent.VIEW_UNKNOWN) {
                return;
            }

            // MetricsEvent logging with new value, for SwitchPreferences and ListPreferences.
            if (preference == mButton4glte || preference == mVideoCallingPref) {
                MetricsLogger.action(getContext(), category, (Boolean) newValue);
            } else if (preference == mButtonPreferredNetworkMode
                    || preference == mButtonEnabledNetworks
                    || preference == preferenceScreen
                            .findPreference(BUTTON_CDMA_SYSTEM_SELECT_KEY)
                    || preference == preferenceScreen
                            .findPreference(BUTTON_CDMA_SUBSCRIPTION_KEY)) {
                // Network select preference sends metrics event in its own listener.
                MetricsLogger.action(getContext(), category, Integer.valueOf((String) newValue));
            }
        }

        private int getMetricsEventCategory(
                PreferenceScreen preferenceScreen, Preference preference) {

            if (preference == null) {
                return MetricsEvent.VIEW_UNKNOWN;
            } else if (preference == mMobileDataPref) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_MOBILE_DATA_TOGGLE;
            } else if (preference == mButtonDataRoam) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_DATA_ROAMING_TOGGLE;
            } else if (preference == mDataUsagePref) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_DATA_USAGE;
            } else if (preference == mLteDataServicePref) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_SET_UP_DATA_SERVICE;
            } else if (preference == mAdvancedOptions) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_EXPAND_ADVANCED_FIELDS;
            } else if (preference == mButton4glte) {
                return MetricsEvent.ACTION_MOBILE_ENHANCED_4G_LTE_MODE_TOGGLE;
            } else if (preference == mButtonPreferredNetworkMode) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_SELECT_PREFERRED_NETWORK;
            } else if (preference == mButtonEnabledNetworks) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_SELECT_ENABLED_NETWORK;
            } else if (preference == mEuiccSettingsPref) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_EUICC_SETTING;
            } else if (preference == mWiFiCallingPref) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_WIFI_CALLING;
            } else if (preference == mVideoCallingPref) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_VIDEO_CALLING_TOGGLE;
            } else if (preference == preferenceScreen
                            .findPreference(NetworkOperators.BUTTON_AUTO_SELECT_KEY)) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_AUTO_SELECT_NETWORK_TOGGLE;
            } else if (preference == preferenceScreen
                            .findPreference(NetworkOperators.BUTTON_NETWORK_SELECT_KEY)) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_MANUAL_SELECT_NETWORK;
            } else if (preference == preferenceScreen
                            .findPreference(BUTTON_CDMA_SYSTEM_SELECT_KEY)) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_CDMA_SYSTEM_SELECT;
            } else if (preference == preferenceScreen
                            .findPreference(BUTTON_CDMA_SUBSCRIPTION_KEY)) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_CDMA_SUBSCRIPTION_SELECT;
            } else if (preference == preferenceScreen.findPreference(BUTTON_GSM_APN_EXPAND_KEY)
                    || preference == preferenceScreen.findPreference(BUTTON_CDMA_APN_EXPAND_KEY)) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_APN_SETTINGS;
            } else if (preference == preferenceScreen.findPreference(BUTTON_CARRIER_SETTINGS_KEY)) {
                return MetricsEvent.ACTION_MOBILE_NETWORK_CARRIER_SETTINGS;
            } else {
                return MetricsEvent.VIEW_UNKNOWN;
            }
        }

        /* UNISOC: FEATURE_DATA_ALWAYS_ONLINE @{ */
        private void showDialog(int id) {
            switch (id) {
                case DIALOG_DISABLE_MOBILE_DATA_AOL:
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    // UNISOC: BUG 895389
                    builder.setIconAttribute(android.R.attr.alertDialogIcon)
                            .setMessage(R.string.data_always_online_dialog)
                            .setTitle(R.string.dialog_alert_title)
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            int phoneSubId = mPhone.getSubId();
                                            // UNISOC: Bug 612136 Make sure the option is unchecked
                                            mButtonDataAol.setChecked(false);
                                            setMobileDataAlwaysOnline(phoneSubId, false);
                                        }
                                    })
                            .setNegativeButton(android.R.string.no,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            mButtonDataAol.setChecked(true);
                                            dialog.cancel();
                                        }
                                    })
                            /* UNISOC: Bug 609271 restore check state when dialog is cancelled @{ */
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    mButtonDataAol.setChecked(true);
                                }
                            })
                            /* @} */
                            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                    if (getActivity() != null) {
                                        getActivity().removeDialog(DIALOG_DISABLE_MOBILE_DATA_AOL);
                                    }
                                }
                            });
                    /* UNISOC: modify by Bug 757157 @{ */
                    mDialog = builder.create();
                    mDialog.show();
                    /* @}*/
                default:
                    break;
            }
        }

        private boolean enableDataAlwaysOnline() {
            PersistableBundle carrierConfig = PhoneGlobals.getInstance().getCarrierConfig();
            if (carrierConfig != null) {
                return carrierConfig.getBoolean("enable_data_always_online_bool", true);
            }
            return true;
        }

        private boolean isMobileDataAlwaysOnline(int subId) {
            return mSharedPrefs.getBoolean(BUTTON_ALWAYS_ONLINE_KEY + subId, true);
        }

        private void setMobileDataAlwaysOnline(int subId, boolean onOff) {
            SharedPreferences.Editor editor = mSharedPrefs.edit();
            editor.putBoolean(BUTTON_ALWAYS_ONLINE_KEY + subId, onOff);
            editor.commit();
        }
        /* @} */

        private void updateGsmUmtsOptions(PreferenceFragment prefFragment,
                PreferenceScreen prefScreen, final int subId, INetworkQueryService queryService) {
            // We don't want to re-create GsmUmtsOptions if already exists. Otherwise, the
            // preferences inside it will also be re-created which causes unexpected behavior.
            // For example, the open dialog gets dismissed or detached after pause / resume.
            if (mGsmUmtsOptions == null) {
                mGsmUmtsOptions = new GsmUmtsOptions(prefFragment, prefScreen, subId, queryService);
                /* UNISOC: Bug 882828 @{*/
                NetworkOperators networkOperators = mGsmUmtsOptions.getNetworkOperator();
                if(networkOperators != null){
                    NetworkSelectListPreference preference = networkOperators.getNetworkPreference();
                    mNetworkLifecycleObserver = new NetworkLifecycleObserver(getContext(), preference);
                    mLifecycle.addObserver(mNetworkLifecycleObserver);
                }
                /* @} */
            } else {
                mGsmUmtsOptions.update(subId, queryService);
            }
        }

        private void updateCdmaOptions(PreferenceFragment prefFragment, PreferenceScreen prefScreen,
                Phone phone) {
            // We don't want to re-create CdmaOptions if already exists. Otherwise, the preferences
            // inside it will also be re-created which causes unexpected behavior. For example,
            // the open dialog gets dismissed or detached after pause / resume.
            if (mCdmaOptions == null) {
                mCdmaOptions = new CdmaOptions(prefFragment, prefScreen, phone);
            } else {
                mCdmaOptions.update(phone);
            }
        }

        /* SPRD: FEATURE_NATIONAL_DATA_ROAMING @ */
        private void updatePreferredDataRoamValueAndSummary(int roamType) {
            mButtonPreferredDataRoam.setValue(String.valueOf(roamType));
            mButtonPreferredDataRoam.setSummary(mButtonPreferredDataRoam.getEntry());
        }

        /* SPRD: FEATURE_RESOLUTION_SETTING @{ */
        private void updateImsConfig(int phoneId){
            try {
                mImsConfig = ImsManager.getInstance(getActivity(),phoneId).getConfigInterface();
            } catch (Exception ie) {
                Log.d(LOG_TAG, "Get ImsConfig occour exception =" + ie);
            }
            getVideoQualityFromPreference();
        }
        private void setVideoQualitytoPreference(int quality){
            SharedPreferences.Editor editor = mSharedPrefs.edit();
            editor.putInt(VT_RESOLUTION , quality);
            editor.commit();
        }

        private void getVideoQualityFromPreference(){
            try {
                if (mImsConfig == null) {
                    Log.d(LOG_TAG, "getVideoQualityFromPreference mImsConfig is null");
                    return;
                }
                int quality = mImsConfig.getConfigInt(ImsConfig.ConfigConstants.VIDEO_QUALITY);
                Log.d(LOG_TAG, "onGetVideoQuality quality = " + quality);
                mPreferredSetResolution.setValueIndex(quality - 1);
                mPreferredSetResolution.setSummary(mPreferredSetResolution.getEntry());
            } catch (Exception e) {
                Log.d(LOG_TAG, "getVideoQualityFromPreference exception : " + e.getMessage());
                e.printStackTrace();
            }
        }

        private synchronized void tryRegisterImsListener(){
            if(ImsManager.isVolteEnabledByPlatform(getActivity())){
                mIImsServiceEx = ImsManagerEx.getIImsServiceEx();
                if(mIImsServiceEx != null){
                    try{
                        if(!mIsImsListenerRegistered){
                            mIsImsListenerRegistered = true;
                            mIImsServiceEx.registerforImsRegisterStateChanged(mImsUtListenerExBinder);
                        }
                    }catch(RemoteException e){
                        Log.e(LOG_TAG, "regiseterforImsException: "+ e);
                    }
                }
            }
        }

        private final IImsRegisterListener.Stub mImsUtListenerExBinder = new IImsRegisterListener.Stub(){
            @Override
            public void imsRegisterStateChange(boolean isRegistered){
                Log.d(LOG_TAG, "imsRegisterStateChange. isRegistered: " + isRegistered+"  mPhone:"+mPhone);
                mHandler.removeMessages(MyHandler.MESSAGE_IMS_CHANGE);
                mHandler.sendEmptyMessageDelayed(MyHandler.MESSAGE_IMS_CHANGE, 200);
            }
        };
        public void handleImsAboutVideo(){
            updateVideoCallState();
            if(mCallingCategory == null)return;
            if (mCallingCategory.getPreferenceCount() == 0) {
                getPreferenceScreen().removePreference(mCallingCategory);

            } else {
                 /* SPRD: modify by BUG 812069 @{ */
                if (mExpandAdvancedFields.get(mCurrentTab) != null
                        && mExpandAdvancedFields.get(mCurrentTab)) {
                    getPreferenceScreen().addPreference(mCallingCategory);
                }
                /* @} */
            }
        }
        private synchronized void unTryRegisterImsListener(){
            if(ImsManager.isVolteEnabledByPlatform(getActivity())){
                try{
                    if(mIsImsListenerRegistered){
                        mIsImsListenerRegistered = false;
                        mIImsServiceEx.unregisterforImsRegisterStateChanged(mImsUtListenerExBinder);
                    }
                }catch(RemoteException e){
                    Log.e(LOG_TAG, "finalize: " + e);
                }
            }
        }
        /* @} */

        /* UNISOC: fix bug 720475 && 977043@{ */
        private boolean isImsTurnOffAllowed(Context context) {
            return !ImsManagerEx.separateSettingForWFCandVoLTE(context) || (!ImsManager.isWfcEnabledByPlatform(context)
                    || !ImsManager.isWfcEnabledByUser(context));
        }
        /* @} */
        // Unisoc: add for Bug985539
        private boolean setVideoQualityForSpecialOpearter(){
            if(mPhone != null) {
                PersistableBundle carrierConfig = PhoneGlobals.getInstance()
                        .getCarrierConfigForSubId(mPhone.getSubId());

                if(carrierConfig != null) {
                    int quality = carrierConfig.getInt(CarrierConfigManagerEx.KEY_SUPPORT_FIXED_VT_CALL_RESOLUTION);
                    if(IVALAID_VT_CALL_RESOLUTION_VALUE != quality) {
                        Log.d(LOG_TAG, "setVideoQualityForSpecialOpearter quality = " + quality);
                        if (mPreferredSetResolution != null) {
                            mPreferredSetResolution.setValueIndex(quality);
                            mPreferredSetResolution.setSummary(mPreferredSetResolution.getEntry());
                            mPreferredSetResolution.setEnabled(false);

                            return true;
                        }
                    }
                }
            }

            return false;
        }
    }
}

