/** Created by Spreadst */

package com.android.settings.location;

import android.content.ContentResolver;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceClickListener;

import android.provider.Settings;
import android.provider.Settings.Secure;
import android.util.Log;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.widget.RadioButtonPreference;

/**
 * A page with 3 radio buttons to choose the agps mode
 *
 * There are 3 agps modes when location access is enabled:
 *
 * Registered network: set position mode is MS_BASED by registered network.
 *
 * All networks: set position mode is MS_BASED by All networks.
 *
 * Do not use AGPS: set position mode is standalone.
 *
 * If user choose registered network or all networks will set assisted_gps_enable_option is 0 or 1.
 * If user choose do not use agps will set assisted_gps_enable_option is 2.
 * If agps is enable set set position mode is MS_BASED (assisted_gps_enable_option is 0 or 1), and disable set position mode is standalone (assisted_gps_enable_option is 2).
 *
 * Default settings and agps help information
 *
 * Default settings:Restore the default setting use registered network.
 */

public class LocationAgpsEnableConfig extends SettingsPreferenceFragment
        implements RadioButtonPreference.OnClickListener {

    private static final String TAG = LocationAgpsEnableConfig.class.getSimpleName();
    private static final String KEY_ENABLE_AGPS_REGISTERED = "enable_agps_registered";
    private static final String KEY_ENABLE_AGPS_ALL = "enable_agps_all";
    private static final String KEY_ENABLE_AGPS_NONE = "enable_agps_none";
    private static final String KEY_DEFAULT_SET = "default_set_button";

    private static final String SETTINGS_SECURE_KEY_ASSISTED_GPS_ENABLE_OPTION = "assisted_gps_enable_option";

    // SPRD: MODIFY change the CheckBox into RadioButton
    private RadioButtonPreference mEnableAgpsRegistered;
    private RadioButtonPreference mEnableAgpsAll;
    private RadioButtonPreference mEnableAgpsNone;
    private ContentResolver resolver;
    private Preference mDefaultSet;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        resolver = getActivity().getContentResolver();
        addPreferencesFromResource(R.xml.agps_enable_config);
        // SPRD: MODIFY change the CheckBox into RadioButton
        mEnableAgpsRegistered = (RadioButtonPreference) findPreference(KEY_ENABLE_AGPS_REGISTERED);
        mEnableAgpsAll = (RadioButtonPreference) findPreference(KEY_ENABLE_AGPS_ALL);
        mEnableAgpsNone = (RadioButtonPreference) findPreference(KEY_ENABLE_AGPS_NONE);
        mEnableAgpsRegistered.setOnClickListener(this);
        mEnableAgpsAll.setOnClickListener(this);
        mEnableAgpsNone.setOnClickListener(this);
        mDefaultSet = findPreference(KEY_DEFAULT_SET);
        mDefaultSet.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (!mEnableAgpsRegistered.isChecked()) {
                    mEnableAgpsRegistered.setChecked(true);
                    mEnableAgpsAll.setChecked(false);
                    mEnableAgpsNone.setChecked(false);
                    Settings.Secure.putInt(resolver,
                            SETTINGS_SECURE_KEY_ASSISTED_GPS_ENABLE_OPTION, 0);
                }
                return false;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        int tmp = Settings.Secure.getInt(resolver, SETTINGS_SECURE_KEY_ASSISTED_GPS_ENABLE_OPTION,
                0);
        if (tmp == 0) {
            mEnableAgpsRegistered.setChecked(true);
            /* SPRD: Add for bug457629. @{ */
            if (mEnableAgpsAll != null && mEnableAgpsNone != null) {
                mEnableAgpsAll.setChecked(false);
                mEnableAgpsNone.setChecked(false);
            }
            /* @} */
        } else if (tmp == 1) {
            mEnableAgpsAll.setChecked(true);
            /* SPRD: Add for bug457629. @{ */
            if (mEnableAgpsRegistered != null && mEnableAgpsNone != null) {
                mEnableAgpsRegistered.setChecked(false);
                mEnableAgpsNone.setChecked(false);
            }
            /* @} */
        } else if (tmp == 2) {
            mEnableAgpsNone.setChecked(true);
            /* SPRD: Add for bug457629. @{ */
            if (mEnableAgpsAll != null && mEnableAgpsRegistered != null) {
                mEnableAgpsAll.setChecked(false);
                mEnableAgpsRegistered.setChecked(false);
            }
            /* @} */
        }

        //update preference state
        boolean locationModeOff = (Secure.getInt(getContentResolver(), Secure.LOCATION_MODE,
                Secure.LOCATION_MODE_OFF) == Secure.LOCATION_MODE_OFF);
        ((Preference) mEnableAgpsNone).setEnabled(!locationModeOff);
        ((Preference) mEnableAgpsAll).setEnabled(!locationModeOff);
        ((Preference) mEnableAgpsRegistered).setEnabled(!locationModeOff);
        mDefaultSet.setEnabled(!locationModeOff);
    }

    public void onRadioButtonClicked(RadioButtonPreference preference) {
        if (preference == mEnableAgpsRegistered) {
            mEnableAgpsRegistered.setChecked(true);
            mEnableAgpsAll.setChecked(false);
            mEnableAgpsNone.setChecked(false);
            Settings.Secure.putInt(resolver, SETTINGS_SECURE_KEY_ASSISTED_GPS_ENABLE_OPTION, 0);
        } else if (preference == mEnableAgpsAll) {
            mEnableAgpsAll.setChecked(true);
            mEnableAgpsRegistered.setChecked(false);
            mEnableAgpsNone.setChecked(false);
            Settings.Secure.putInt(resolver, SETTINGS_SECURE_KEY_ASSISTED_GPS_ENABLE_OPTION, 1);
        } else if (preference == mEnableAgpsNone) {
            mEnableAgpsNone.setChecked(true);
            mEnableAgpsAll.setChecked(false);
            mEnableAgpsRegistered.setChecked(false);
            Settings.Secure.putInt(resolver, SETTINGS_SECURE_KEY_ASSISTED_GPS_ENABLE_OPTION, 2);
        } else {
            Log.e(TAG, "No handle!");
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LOCATION;
    }
}
