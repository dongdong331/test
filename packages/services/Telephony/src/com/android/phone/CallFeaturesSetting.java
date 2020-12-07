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

package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.os.UserManager;
import android.os.Handler;
import android.database.ContentObserver;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManagerEx;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.feature.ImsFeature;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.ims.ImsConfig;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.services.telephony.sip.SipUtil;
import com.android.phone.settings.PhoneAccountSettingsFragment;
import com.android.phone.settings.VoicemailSettingsActivity;
import com.android.phone.settings.fdn.FdnSetting;
import com.unisoc.phone.callsettings.ActivityContainer;
import java.util.List;
import java.util.Collections;


/**
 * Top level "Call settings" UI; see res/xml/call_feature_setting.xml
 *
 * This preference screen is the root of the "Call settings" hierarchy available from the Phone
 * app; the settings here let you control various features related to phone calls (including
 * voicemail settings, the "Respond via SMS" feature, and others.)  It's used only on
 * voice-capable phone devices.
 *
 * Note that this activity is part of the package com.android.phone, even
 * though you reach it from the "Phone" app (i.e. DialtactsActivity) which
 * is from the package com.android.contacts.
 *
 * For the "Mobile network settings" screen under the main Settings app,
 * See {@link MobileNetworkSettings}.
 *
 * @see com.android.phone.MobileNetworkSettings
 */
public class CallFeaturesSetting extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {
    private static final String LOG_TAG = "CallFeaturesSetting";
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    // String keys for preference lookup
    // TODO: Naming these "BUTTON_*" is confusing since they're not actually buttons(!)
    // TODO: Consider moving these strings to strings.xml, so that they are not duplicated here and
    // in the layout files. These strings need to be treated carefully; if the setting is
    // persistent, they are used as the key to store shared preferences and the name should not be
    // changed unless the settings are also migrated.
    private static final String VOICEMAIL_SETTING_SCREEN_PREF_KEY = "button_voicemail_category_key";
    private static final String BUTTON_FDN_KEY   = "button_fdn_key";
    private static final String BUTTON_RETRY_KEY       = "button_auto_retry_key";
    private static final String BUTTON_GSM_UMTS_OPTIONS = "button_gsm_more_expand_key";
    private static final String BUTTON_CDMA_OPTIONS = "button_cdma_more_expand_key";

    private static final String PHONE_ACCOUNT_SETTINGS_KEY =
            "phone_account_settings_preference_screen";

    private static final String ENABLE_VIDEO_CALLING_KEY = "button_enable_video_calling";
    /* UNISOC: FEATURE_POPUP_DATA_DIALOG_ON_IMS @{ */
    private static final String CALL_FORWARDING_KEY = "call_forwarding_key";
    private static final String CALL_BARRING_KEY = "call_barring_key";
    private static final String ADDITIONAL_GSM_SETTINGS_KEY = "additional_gsm_call_settings_key";
    private AlertDialog mOpenDataDialog = null;
    private TelephonyManager mTelephonyManager;
    /* @} */
    private Phone mPhone;
    private ImsManager mImsMgr;
    private SubscriptionInfoHelper mSubscriptionInfoHelper;
    private TelecomManager mTelecomManager;

    private SwitchPreference mButtonAutoRetry;
    private PreferenceScreen mVoicemailSettingsScreen;
    private SwitchPreference mEnableVideoCalling;
    private Preference mButtonWifiCalling;
    // SPRD: add for bug735946
    private static final String FACTORY_MODE_VOLTE_STATE = "persist.vendor.sys.volte.enable";
    // UNISOC: modify for bug911559
    private AlertDialog mDialog;
    // UNISOC: add for bug916869
    private ActivityContainer mActivityContainer;
    /*
     * Click Listeners, handle click based on objects attached to UI.
     */

    // Click listener for all toggle events
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mButtonAutoRetry) {
            android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Global.CALL_AUTO_RETRY,
                    mButtonAutoRetry.isChecked() ? 1 : 0);
            return true;
        /* UNISOC: FEATURE_POPUP_DATA_DIALOG_ON_IMS @{ */
        } else if (preference != null && (CALL_FORWARDING_KEY.equals(preference.getKey())
                || ADDITIONAL_GSM_SETTINGS_KEY.equals(preference.getKey())
                || CALL_BARRING_KEY.equals(preference.getKey()))) {
            /* UNISOC: modify for bug900342 and add for bug999104@{ */
            if (isAirplaneModeOn(getBaseContext()) && (!isSupportSSOnVowifiEnable())) {
                log("the phone airplane mode is opened");
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(getResources().getString(
                        R.string.airplane_on))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return true;
            }
            /* @} */
            if ((ImsManager.isVolteEnabledByPlatform(this) || ImsManager.isWfcEnabledByPlatform(this)) && (mPhone.getImsPhone() != null)
                    && ((mPhone.getImsPhone().getServiceState().getState()
                    == ServiceState.STATE_IN_SERVICE) || mPhone.getImsPhone().isUtEnabled())) {
                log("volte is supported and volte is enabled and is primary card");
                int defaultDataId = SubscriptionManager.getDefaultDataSubscriptionId();
                PersistableBundle carrierConfig = PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
                if (mTelephonyManager.getDataEnabled() && mPhone.getSubId() == defaultDataId
                        // UNISOC: add for Bug 993516
                        || !getResources().getBoolean(R.bool.config_support_show_open_data_dialog) ||
                        //UNIOSC:add for Bug 1015367
                        !carrierConfig.getBoolean(
                                CarrierConfigManagerEx.KEY_SUPPORT_SHOW_NETWORK_DIALOG)) {
                    return false;
                } else{
                    /* UNISOC: modify for bug918870 @{ */
                    if (!mPhone.getImsPhone().isUtEnabled()) {
                        return false;
                    } else {
                        showOpenDataDialog(this, preference);
                        return true;
                    }
                    /* @} */
                }
            } else {
                log("volte is disable or not support or not primary card");
                return false;
            }
        }
        /* @} */
        return false;
    }

    /**
     * Implemented to support onPreferenceChangeListener to look for preference
     * changes.
     *
     * @param preference is the preference to be changed
     * @param objValue should be the value of the selection, NOT its localized
     * display value.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (DBG) log("onPreferenceChange: \"" + preference + "\" changed to \"" + objValue + "\"");

        if (preference == mEnableVideoCalling) {
            if (mImsMgr.isEnhanced4gLteModeSettingEnabledByUser()) {
                /*UNISOC: modify for bug895253 @{ */
                PhoneInterfaceManager phoneMgr = PhoneGlobals.getInstance().phoneMgr;
                phoneMgr.setPhone(mPhone);
                phoneMgr.enableVideoCalling((boolean) objValue);
                /* @} */
            } else {
                /* UNISOC: modify for bug911559 @{ */
                if (mDialog != null && mDialog.isShowing()) {
                    return false;
                }
                DialogInterface.OnClickListener networkSettingsClickListener =
                        new Dialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(mPhone.getContext(),
                                        com.android.phone.MobileNetworkSettings.class));
                            }
                        };
               mDialog = new AlertDialog.Builder(this)
                        .setMessage(getResources().getString(
                                R.string.enable_video_calling_dialog_msg))
                        .setNeutralButton(getResources().getString(
                                R.string.enable_video_calling_dialog_settings),
                                networkSettingsClickListener)
                        .setPositiveButton(android.R.string.ok, null)
                        .create();
               mDialog.show();
               return false;
               /* @} */
            }
        }

        // Always let the preference setting proceed.
        return true;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (DBG) log("onCreate: Intent is " + getIntent());

        // Make sure we are running as an admin user.
        if (!UserManager.get(this).isAdminUser()) {
            Toast.makeText(this, R.string.call_settings_admin_user_only,
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mSubscriptionInfoHelper.setActionBarTitle(
                getActionBar(), getResources(), R.string.call_settings_with_label);
        mPhone = mSubscriptionInfoHelper.getPhone();
        /* UNISOC: add for bug923038 @{ */
        if (mPhone == null) {
            finish();
        } else {
            mTelecomManager = TelecomManager.from(this);
            // UNISOC: FEATURE_POPUP_DATA_DIALOG_ON_IMS
            mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            /* UNISOC: modify by BUG 916869 @{ */
            mActivityContainer = ActivityContainer.getInstance();
            mActivityContainer.setApplication(getApplication());
            mActivityContainer.addActivity(this, mSubscriptionInfoHelper.getPhone().getPhoneId());
            /* @} */
        }
        /* @} */
    }

    private void updateImsManager(Phone phone) {
        /* UNISOC: modify by BUG 923022 @{ */
        if (phone != null && phone.getContext() != null){
            log("updateImsManager :: phone.getContext()=" + phone.getContext()
                    + " phone.getPhoneId()=" + phone.getPhoneId());
            mImsMgr = ImsManager.getInstance(phone.getContext(), phone.getPhoneId());
            if (mImsMgr == null) {
                log("updateImsManager :: Could not get ImsManager instance!");
            } else {
                log("updateImsManager :: mImsMgr=" + mImsMgr);
            }
        }
        /* @} */
    }

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (DBG) log("PhoneStateListener onCallStateChanged: state is " + state);
            if (mEnableVideoCalling != null) {
                // Use TelephonyManager#getCallStete instead of 'state' parameter because it needs
                // to check the current state of all phone calls.
                TelephonyManager telephonyManager =
                        (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                // UNISOC: modify for bug904982
                mEnableVideoCalling.setEnabled(
                        telephonyManager.getCallState(
                                mPhone.getSubId()) == TelephonyManager.CALL_STATE_IDLE);
                // UNISOC: modify for bug913342
                mButtonWifiCalling.setEnabled(
                        telephonyManager.getCallState(
                                mPhone.getSubId()) == TelephonyManager.CALL_STATE_IDLE);
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        if (getBaseContext() != null) {
            getBaseContext().getContentResolver().unregisterContentObserver(mWfcEnableObserver);
            // UNISOC: Add for Bug 843666 controll WFC showing via OMA request
            getBaseContext().getContentResolver().unregisterContentObserver(mWFCShownObserver);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        /* UNISOC: modify by BUG 916869 @{ */
        if (mPhone != null) {
            updateImsManager(mPhone);
        }
        /* @} */
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            preferenceScreen.removeAll();
        }

        addPreferencesFromResource(R.xml.call_feature_setting);

        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        PreferenceScreen prefSet = getPreferenceScreen();
        /* UNISOC: add for bug903033 and bug1009528 @{ */
        Preference phoneAccountSettingsPreference = findPreference(PHONE_ACCOUNT_SETTINGS_KEY);
        if (telephonyManager.isMultiSimEnabled()) {
            prefSet.removePreference(phoneAccountSettingsPreference);
        }
        /* @} */
        mVoicemailSettingsScreen =
                (PreferenceScreen) findPreference(VOICEMAIL_SETTING_SCREEN_PREF_KEY);
        mVoicemailSettingsScreen.setIntent(mSubscriptionInfoHelper.getIntent(
                VoicemailSettingsActivity.class));

        maybeHideVoicemailSettings();

        mButtonAutoRetry = (SwitchPreference) findPreference(BUTTON_RETRY_KEY);

        mEnableVideoCalling = (SwitchPreference) findPreference(ENABLE_VIDEO_CALLING_KEY);
        mButtonWifiCalling = findPreference(getResources().getString(
                R.string.wifi_calling_settings_key));

        PersistableBundle carrierConfig =
                PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());

        if (carrierConfig.getBoolean(CarrierConfigManager.KEY_AUTO_RETRY_ENABLED_BOOL)) {
            mButtonAutoRetry.setOnPreferenceChangeListener(this);
            int autoretry = Settings.Global.getInt(
                    getContentResolver(), Settings.Global.CALL_AUTO_RETRY, 0);
            mButtonAutoRetry.setChecked(autoretry != 0);
        } else {
            prefSet.removePreference(mButtonAutoRetry);
            mButtonAutoRetry = null;
        }

        Preference cdmaOptions = prefSet.findPreference(BUTTON_CDMA_OPTIONS);
        Preference gsmOptions = prefSet.findPreference(BUTTON_GSM_UMTS_OPTIONS);
        Preference fdnButton = prefSet.findPreference(BUTTON_FDN_KEY);
        fdnButton.setIntent(mSubscriptionInfoHelper.getIntent(FdnSetting.class));
        if (carrierConfig.getBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL)) {
            cdmaOptions.setIntent(mSubscriptionInfoHelper.getIntent(CdmaCallOptions.class));
            gsmOptions.setIntent(mSubscriptionInfoHelper.getIntent(GsmUmtsCallOptions.class));
        } else {
            prefSet.removePreference(cdmaOptions);
            prefSet.removePreference(gsmOptions);

            int phoneType = mPhone.getPhoneType();
            if (carrierConfig.getBoolean(CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL)) {
                prefSet.removePreference(fdnButton);
            } else {
                if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                    prefSet.removePreference(fdnButton);

                    if (!carrierConfig.getBoolean(
                            CarrierConfigManager.KEY_VOICE_PRIVACY_DISABLE_UI_BOOL)) {
                        addPreferencesFromResource(R.xml.cdma_call_privacy);
                    }
                } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {

                    if (carrierConfig.getBoolean(
                            CarrierConfigManager.KEY_ADDITIONAL_CALL_SETTING_BOOL)) {
                        /* SPRD: add for bug983978 @{ */
                        boolean isAllowShowIPFeature = getResources().getBoolean(com.android.internal.R.bool.ip_dial_enabled_bool);
                        addPreferencesFromResource(R.xml.gsm_umts_call_options);
                        GsmUmtsCallOptions.init(prefSet, mSubscriptionInfoHelper, isAllowShowIPFeature);
                        /* @} */
                    }
                } else {
                    throw new IllegalStateException("Unexpected phone type: " + phoneType);
                }
            }
        }

        // SPRD: add for bug735946
        if (mImsMgr.isVtEnabledByPlatform() && mImsMgr.isVtProvisionedOnDevice()
                && SystemProperties.getBoolean(FACTORY_MODE_VOLTE_STATE, false)
                && (carrierConfig.getBoolean(
                        CarrierConfigManager.KEY_IGNORE_DATA_ENABLED_CHANGED_FOR_VIDEO_CALLS)
                || mPhone.mDcTracker.isDataEnabled())) {
            /*UNISOC: modify for bug895253 @{ */
            PhoneInterfaceManager phoneMgr = PhoneGlobals.getInstance().phoneMgr;
            phoneMgr.setPhone(mPhone);
            boolean currentValue = phoneMgr.isVideoCallingEnabled(getOpPackageName());
            /* @} */
            mEnableVideoCalling.setChecked(currentValue);
            mEnableVideoCalling.setOnPreferenceChangeListener(this);
        } else {
            prefSet.removePreference(mEnableVideoCalling);
        }

        if ((mImsMgr.isVolteEnabledByPlatform() || mImsMgr.isWfcEnabledByPlatform())
                && !carrierConfig.getBoolean(
                        CarrierConfigManager.KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL)) {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            /* tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE); */
        }

        /* SPRD: add for bug727245 @{ */
        if (getBaseContext() != null) {
            getBaseContext().getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.WFC_IMS_ENABLED), true,
                    mWfcEnableObserver);
            /* UNISOC: Add for Bug 843666 controll WFC showing via OMA request @{ */
            getBaseContext().getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(OmaHelper.OMA_WFC_ENABLE + mPhone.getSubId()), true,
                    mWFCShownObserver);
            updateWfcShowing();
            /* @} */
        }
        /* @} */
    }

    /**
     * Hides the top level voicemail settings entry point if the default dialer contains a
     * particular manifest metadata key. This is required when the default dialer wants to display
     * its own version of voicemail settings.
     */
    private void maybeHideVoicemailSettings() {
        String defaultDialer = getSystemService(TelecomManager.class).getDefaultDialerPackage();
        if (defaultDialer == null) {
            return;
        }
        try {
            Bundle metadata = getPackageManager()
                    .getApplicationInfo(defaultDialer, PackageManager.GET_META_DATA).metaData;
            if (metadata == null) {
                return;
            }
            if (!metadata
                    .getBoolean(TelephonyManager.METADATA_HIDE_VOICEMAIL_SETTINGS_MENU, false)) {
                if (DBG) {
                    log("maybeHideVoicemailSettings(): not disabled by default dialer");
                }
                return;
            }
            getPreferenceScreen().removePreference(mVoicemailSettingsScreen);
            if (DBG) {
                log("maybeHideVoicemailSettings(): disabled by default dialer");
            }
        } catch (NameNotFoundException e) {
            // do nothing
            if (DBG) {
                log("maybeHideVoicemailSettings(): not controlled by default dialer");
            }
        }
    }

    @Override
    protected void onNewIntent(Intent newIntent) {
        setIntent(newIntent);

        mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mSubscriptionInfoHelper.setActionBarTitle(
                getActionBar(), getResources(), R.string.call_settings_with_label);
        mPhone = mSubscriptionInfoHelper.getPhone();
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Finish current Activity and go up to the top level Settings ({@link CallFeaturesSetting}).
     * This is useful for implementing "HomeAsUp" capability for second-level Settings.
     */
    public static void goUpToTopLevelSetting(
            Activity activity, SubscriptionInfoHelper subscriptionInfoHelper) {
        Intent intent = subscriptionInfoHelper.getIntent(CallFeaturesSetting.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        activity.finish();
    }

    private ContentObserver mWfcEnableObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (getBaseContext() == null) {
                return;
            }
            boolean enabled = ImsManager.isWfcEnabledByUser(getBaseContext())
                    && ImsManager.isNonTtyOrTtyOnVolteEnabled(getBaseContext());
            Log.d(LOG_TAG,"[mWfcEnableObserver][wfcEnabled]: " + enabled);
            // update WFC setting
            PreferenceScreen ps = (PreferenceScreen) findPreference(
                    getResources().getString(R.string.wifi_calling_settings_key));
            Log.d(LOG_TAG,"[mWfcEnableObserver][ps]: " + ps);
            if (ps != null) {
                int resId = com.android.internal.R.string.wifi_calling_off_summary;
                if (ImsManager.isWfcEnabledByUser(getBaseContext())) {
                    /* SPRD: add for bug854291 @{ */
                    boolean mShowWifiCallingSummaryOn = false;
                    CarrierConfigManager configManager =
                            (CarrierConfigManager) getBaseContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
                    int primarySubId = SubscriptionManager.getDefaultDataSubscriptionId();
                    if (configManager.getConfigForDefaultPhone() != null) {
                        mShowWifiCallingSummaryOn = configManager.getConfigForSubId(primarySubId).getBoolean(
                        CarrierConfigManagerEx.KEY_SUPPORT_SHOW_WIFI_CALLING_PREFERENCE);
                    }
                    log("mShowWifiCallingSummaryOn " +  mShowWifiCallingSummaryOn);
                    if (mShowWifiCallingSummaryOn) {
                        int wfcMode = ImsManager.getWfcMode(getBaseContext());
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
                ps.setSummary(resId);
            }
         }
    };

    /* UNISOC: modify for bug900342 @{ */
    private boolean isAirplaneModeOn(Context context) {
        if (context == null) {
            return true;
        }
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }
    /* @} */

    /* UNISOC: FEATURE_POPUP_DATA_DIALOG_ON_IMS @{ */
    private void showOpenDataDialog(final PreferenceActivity preferenceActivity,
            final Preference preference) {
        final DialogInterface.OnClickListener butListener
            = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        preferenceActivity.startActivity(preference.getIntent());
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        dialog.dismiss();
                        break;
                    default:
                        break;
                }
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setPositiveButton(R.string.alert_dialog_yes, butListener);
        builder.setNegativeButton(R.string.alert_dialog_no, butListener);
        builder.setTitle(getText(R.string.error_updating_title).toString());
        builder.setMessage(getText(R.string.network_not_on).toString());
        mOpenDataDialog = builder.create();
        mOpenDataDialog.setCanceledOnTouchOutside(false);
        mOpenDataDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        mOpenDataDialog.show();
    }
    /* @} */

    /* UNISOC: Add for Bug 843666 controll WFC showing via OMA request @{ */
    private void updateWfcShowing() {
        if (mPhone == null || mButtonWifiCalling == null) {
            return;
        }

        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        PreferenceScreen prefSet = getPreferenceScreen();
        boolean shouldShowWfcByDefault = shouldShowWfcByDefault(mPhone.getSubId());
        boolean isOmaWfcEnable = ((Settings.Global.getInt(getBaseContext().getContentResolver(),
                OmaHelper.OMA_WFC_ENABLE + mPhone.getSubId(), 0)) == 1 ? true : false);
        log("updateWfcShowing shouldShowWfcByDefault = " + shouldShowWfcByDefault
                + " isOmaWfcEnable = " + isOmaWfcEnable);
        if (shouldShowWfcByDefault || (!shouldShowWfcByDefault && isOmaWfcEnable)) {
            /* UNISOC: add for bug743528 @{ */
            if (mSubscriptionInfoHelper.getSubId() == SubscriptionManager
                        .getDefaultDataSubscriptionId()) {
                /* UNISOC: Add for wifi-call string demand in bug 695296. @{ */
                String wifiCallingTitle = getResources().getString(
                        R.string.wifi_calling_settings_title_english);
                if (!TextUtils.isEmpty(wifiCallingTitle)) {
                    if (mButtonWifiCalling != null) {
                        mButtonWifiCalling.setTitle(wifiCallingTitle);
                    }
                }
                /* @} */
                final PhoneAccountHandle simCallManager = mTelecomManager.getSimCallManager();
                if (simCallManager != null) {
                    Intent intent = PhoneAccountSettingsFragment.buildPhoneAccountConfigureIntent(
                            this, simCallManager);
                    if (intent != null) {
                        PackageManager pm = mPhone.getContext().getPackageManager();
                        List<ResolveInfo> resolutions = pm.queryIntentActivities(intent, 0);
                        if (!resolutions.isEmpty()) {
                            /* UNISOC: Add for wifi-call string demand in bug 695296. @{ */
                            if (TextUtils.isEmpty(wifiCallingTitle)) {
                                mButtonWifiCalling.setTitle(resolutions.get(0).loadLabel(pm));
                            } else {
                                mButtonWifiCalling.setTitle(wifiCallingTitle);
                            }
                            /* @} */
                            mButtonWifiCalling.setSummary(null);
                            mButtonWifiCalling.setIntent(intent);
                        } else {
                            prefSet.removePreference(mButtonWifiCalling);
                        }
                    } else {
                        prefSet.removePreference(mButtonWifiCalling);
                    }
                } else if (!mImsMgr.isWfcEnabledByPlatform() || !mImsMgr.isWfcProvisionedOnDevice()) {
                    prefSet.removePreference(mButtonWifiCalling);
                } else {
                    int resId = com.android.internal.R.string.wifi_calling_off_summary;
                    if (mImsMgr.isWfcEnabledByUser()) {
                    	 /* SPRD: add for bug854291 @{ */
                        boolean mShowWifiCallingSummaryOn = false;
                        CarrierConfigManager configManager =
                                (CarrierConfigManager) getBaseContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
                        int primarySubId = SubscriptionManager.getDefaultDataSubscriptionId();
                        if (configManager.getConfigForDefaultPhone() != null) {
                            mShowWifiCallingSummaryOn = configManager.getConfigForSubId(primarySubId).getBoolean(
                            CarrierConfigManagerEx.KEY_SUPPORT_SHOW_WIFI_CALLING_PREFERENCE);
                        }
                        log("mShowWifiCallingSummaryOn " +  mShowWifiCallingSummaryOn);
                        if (mShowWifiCallingSummaryOn) {
                            boolean isRoaming = telephonyManager.isNetworkRoaming();
                            int wfcMode = mImsMgr.getWfcMode(isRoaming);
                            switch (wfcMode) {
                                case ImsConfig.WfcModeFeatureValueConstants.WIFI_ONLY:
                                    resId = com.android.internal.R.string.wfc_mode_wifi_only_summary;
                                    break;
                                case ImsConfig.WfcModeFeatureValueConstants.CELLULAR_PREFERRED:
                                    resId = com.android.internal.R.string.wfc_mode_cellular_preferred_summary;
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
                    mButtonWifiCalling.setSummary(resId);
                }
            } else {
                prefSet.removePreference(mButtonWifiCalling);
            }
            /*  @} */

            try {
                if (mImsMgr.getImsServiceState() != ImsFeature.STATE_READY) {
                    log("Feature state not ready so remove vt and wfc settings for "
                            + " phone =" + mPhone.getPhoneId());
                    prefSet.removePreference(mButtonWifiCalling);
                    prefSet.removePreference(mEnableVideoCalling);
                }
            } catch (ImsException ex) {
                log("Exception when trying to get ImsServiceStatus: " + ex);
                prefSet.removePreference(mButtonWifiCalling);
                prefSet.removePreference(mEnableVideoCalling);
            }
        } else {
            prefSet.removePreference(mButtonWifiCalling);
        }
    }

    private boolean shouldShowWfcByDefault(int subId) {
        CarrierConfigManager configManager = (CarrierConfigManager)
                getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if ((configManager != null)
                && (configManager.getConfigForSubId(subId) != null)) {
            return configManager.getConfigForSubId(subId).getBoolean(
                    CarrierConfigManagerEx.KEY_DEFAULT_SHOW_WIFI_CALL);
        }
        return true;
    }

    private final ContentObserver mWFCShownObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (getBaseContext() == null) {
                log("Base context is null.");
                return;
            }
            updateWfcShowing();
        }
    };
    /* @} */
    /* UNISOC: modify by BUG 916869 @{ */
    protected void onDestroy() {
        super.onDestroy();
        if (mActivityContainer != null) {
            mActivityContainer.removeActivity(this);
        }
    }
    /* @} */
    /* UNISOC: add for bug999104 @{ */
    private boolean isSupportSSOnVowifiEnable() {
        boolean isWifiEnbale = mPhone.isWifiCallingEnabled();
        CarrierConfigManager carrierConfigManager = (CarrierConfigManager) getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if ((carrierConfigManager != null)
                && (carrierConfigManager.getConfigForSubId(mPhone.getSubId()) != null)) {
            return carrierConfigManager.getConfigForSubId(mPhone.getSubId()).getBoolean(
                    CarrierConfigManagerEx.KEY_SUPPORT_SS_OVER_VOWIFI_WITH_AIRPLANE) && isWifiEnbale;
        }
        return false;
    }
    /* @} */
}
