package com.android.settings.wifi.tether;

import android.content.Context;
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiFeaturesUtils;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import com.android.settings.R;

public class WifiTetherSoftApMaxNumPreferenceController extends WifiTetherBasePreferenceController {

    private static final String PREF_KEY = "limit_user";
    public static final int DEFAULT_LIMIT = WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_MAX_NUMBER;
    private final String[] mApMaxConnectEntries;
    private int mApMaxConnectValue;

    public WifiTetherSoftApMaxNumPreferenceController(Context context,
            OnTetherConfigUpdateListener listener) {
        super(context, listener);

        if (DEFAULT_LIMIT == 5) {
            mApMaxConnectEntries = mContext.getResources().getStringArray(R.array.wifi_ap_max_connect_5);
        } else if (DEFAULT_LIMIT == 8) {
            mApMaxConnectEntries = mContext.getResources().getStringArray(R.array.wifi_ap_max_connect_8);
        } else {
            mApMaxConnectEntries = mContext.getResources().getStringArray(R.array.wifi_ap_max_connect_default);
        }
    }

    @Override
    public String getPreferenceKey() {
        return PREF_KEY;
    }

    @Override
    public void updateDisplay() {
        final WifiConfiguration config = mWifiManager.getWifiApConfiguration();

        if (config != null) {
            if (config.softApMaxNumSta > DEFAULT_LIMIT) {
                mApMaxConnectValue = DEFAULT_LIMIT;
            } else if (config.softApMaxNumSta <= 0 && mApMaxConnectEntries != null) {
                mApMaxConnectValue = mApMaxConnectEntries.length;
            } else {
                mApMaxConnectValue = config.softApMaxNumSta;
            }
        } else {
            mApMaxConnectValue = DEFAULT_LIMIT;
        }

        final ListPreference preference = (ListPreference) mPreference;
        preference.setSummary(getSummaryForApMaxConnectType(mApMaxConnectValue));
        preference.setValue(String.valueOf(mApMaxConnectValue));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        mApMaxConnectValue = Integer.parseInt((String) newValue);
        preference.setSummary(getSummaryForApMaxConnectType(mApMaxConnectValue));
        mListener.onTetherConfigUpdated();
        return true;
    }

    public int getApMaxConnectType() {
        return mApMaxConnectValue;
    }

    private String getSummaryForApMaxConnectType(int securityType) {
        //return null;
        if (securityType == DEFAULT_LIMIT) {
            return mApMaxConnectEntries[DEFAULT_LIMIT-1];
        } else {
            return mApMaxConnectEntries[mApMaxConnectValue-1];
        }
    }
}

