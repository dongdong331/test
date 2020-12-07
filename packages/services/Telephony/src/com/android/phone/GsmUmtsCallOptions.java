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

import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telephony.CarrierConfigManager;
import android.view.MenuItem;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.unisoc.phone.callsettings.callforward.GsmUmtsAllCallForwardOptions;

public class GsmUmtsCallOptions extends PreferenceActivity {
    private static final String LOG_TAG = "GsmUmtsCallOptions";
    private final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private static final String CALL_FORWARDING_KEY = "call_forwarding_key";
    private static final String CALL_BARRING_KEY = "call_barring_key";
    private static final String ADDITIONAL_GSM_SETTINGS_KEY = "additional_gsm_call_settings_key";
    // UNISOC: add for IP porting
    private static final String IP_DIAL_KEY = "ip_dial_key";

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.gsm_umts_call_options);

        SubscriptionInfoHelper subInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        subInfoHelper.setActionBarTitle(
                getActionBar(), getResources(), R.string.labelGsmMore_with_label);
        // SPRD: add for bug 983978
        boolean isAllowShowIPFeature = getResources().getBoolean(com.android.internal.R.bool.ip_dial_enabled_bool);
        init(getPreferenceScreen(), subInfoHelper,isAllowShowIPFeature);

        if (subInfoHelper.getPhone().getPhoneType() != PhoneConstants.PHONE_TYPE_GSM) {
            //disable the entire screen
            getPreferenceScreen().setEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /* SPRD: add for bug983978 @{ */
    public static void init(PreferenceScreen prefScreen, SubscriptionInfoHelper subInfoHelper,
                            boolean isAllowShowIPFeature) {
        Preference callForwardingPref = prefScreen.findPreference(CALL_FORWARDING_KEY);
        // UNISOC: add for FEATURE_VIDEO_CALL_FOR
        callForwardingPref.setIntent(subInfoHelper.getIntent(GsmUmtsAllCallForwardOptions.class));

        Preference additionalGsmSettingsPref =
                prefScreen.findPreference(ADDITIONAL_GSM_SETTINGS_KEY);
        additionalGsmSettingsPref.setIntent(
                subInfoHelper.getIntent(GsmUmtsAdditionalCallOptions.class));

        Preference callBarringPref = prefScreen.findPreference(CALL_BARRING_KEY);
        PersistableBundle b = null;
        if (subInfoHelper.hasSubId()) {
            b = PhoneGlobals.getInstance().getCarrierConfigForSubId(subInfoHelper.getSubId());
        } else {
            b = PhoneGlobals.getInstance().getCarrierConfig();
        }
        if (b != null && b.getBoolean(CarrierConfigManager.KEY_CALL_BARRING_VISIBILITY_BOOL)) {
            callBarringPref.setIntent(subInfoHelper.getIntent(GsmUmtsCallBarringOptions.class));
        } else {
            prefScreen.removePreference(callBarringPref);
        }
        /* UNISOC: function ip dialing support @{ */
        Preference ipDialPref = prefScreen.findPreference(IP_DIAL_KEY);
        Intent ipDialIntent = subInfoHelper.getCallSettingsIntent(null, ipDialPref.getIntent());
        if (ipDialIntent != null && isAllowShowIPFeature) {
            ipDialPref.setIntent(ipDialIntent);
        } else {
            prefScreen.removePreference(ipDialPref);
        }
        /* @} */
    }
    /* @} */
}
