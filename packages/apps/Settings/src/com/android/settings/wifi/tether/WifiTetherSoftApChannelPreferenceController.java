package com.android.settings.wifi.tether;

import android.content.Context;
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiFeaturesUtils;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.util.Log;
import com.android.settings.R;

public class WifiTetherSoftApChannelPreferenceController extends WifiTetherBasePreferenceController {

    private static final String PREF_KEY = "ap_channel";
    public static final int DEFAULT_CHANNEL = 11;
    private final String[] mApChannelEntries;
    private int mApChannelValue = DEFAULT_CHANNEL;
    private boolean mHideChannel =true;

    public WifiTetherSoftApChannelPreferenceController(Context context,
            OnTetherConfigUpdateListener listener) {
        super(context, listener);
        mApChannelEntries = mContext.getResources().getStringArray(R.array.wifi_ap_channel);
    }

    @Override
    public String getPreferenceKey() {
        return PREF_KEY;
    }

    @Override
    public void updateDisplay() {
        final WifiConfiguration config = mWifiManager.getWifiApConfiguration();

        if ((config != null) && (config.apChannel != 0)) {
            mApChannelValue = config.apChannel;
        } else {
            mApChannelValue = DEFAULT_CHANNEL;
        }

        final ListPreference preference = (ListPreference) mPreference;
        preference.setSummary(getSummaryForApChannelType(mApChannelValue));
        preference.setValue(String.valueOf(mApChannelValue));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        mApChannelValue = Integer.parseInt((String) newValue);
        preference.setSummary(getSummaryForApChannelType(mApChannelValue));
        mListener.onTetherConfigUpdated();
        return true;
    }

    @Override
    public boolean isAvailable() {
        return !WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_COEXIST_LTE;
    }

    public void updateVisibility(boolean visible) {
        mPreference.setVisible(visible);
    }

    public int getApChannelType() {
        return mApChannelValue;
    }

    private String getSummaryForApChannelType(int securityType) {
        //return null;
        if (securityType == DEFAULT_CHANNEL) {
            return mApChannelEntries[DEFAULT_CHANNEL-1];
        } else {
            return mApChannelEntries[mApChannelValue-1];
        }
    }
}


