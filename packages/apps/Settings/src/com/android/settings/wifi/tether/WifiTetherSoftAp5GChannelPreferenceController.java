package com.android.settings.wifi.tether;

import android.content.Context;
import android.provider.Settings;
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiFeaturesUtils;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.util.Log;
import com.android.settings.R;
import java.util.ArrayList;

public class WifiTetherSoftAp5GChannelPreferenceController extends WifiTetherBasePreferenceController {

    private static final String TAG = "WifiTetherAp5GChannelPref";
    private static final String PREF_KEY = "ap_5g_channel";
    private int mApChannelValue = 0;
    private CharSequence[] mApChannelEntries = null;  // {"Auto","36","40","48","149","153","157","161","165"}
    private CharSequence[] mApChannelEntryValues = null;
    private String mSoftApSupportChannels = null;
    private String m5GChannels[] = null;
    private final Context mContext;

    public WifiTetherSoftAp5GChannelPreferenceController(Context context,
            OnTetherConfigUpdateListener listener) {
        super(context, listener);
        mContext = context;
        getSoftapSupportChannels();
    }

    @Override
    public String getPreferenceKey() {
        return PREF_KEY;
    }

    @Override
    public void updateDisplay() {
        final WifiConfiguration config = mWifiManager.getWifiApConfiguration();

        if (config != null && config.apChannel > 11) {
            Log.d(TAG, "config.apChannel:" + config.apChannel);
            mApChannelValue = config.apChannel;
        } else {
            mApChannelValue = 0;
        }

        final ListPreference preference = (ListPreference) mPreference;
        preference.setEntries(mApChannelEntries);
        preference.setEntryValues(mApChannelEntryValues);
        preference.setSummary(getSummaryForApChannelType(mApChannelValue));
        preference.setValue(String.valueOf(mApChannelValue));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        mApChannelValue = Integer.parseInt((String) newValue);
        Log.d(TAG, "Ap 5g channel preference changed, updating AP channel to " + mApChannelValue);
        preference.setSummary(getSummaryForApChannelType(mApChannelValue));
        mListener.onTetherConfigUpdated();
        return true;
    }

    @Override
    public boolean isAvailable() {
        return !WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_HIDE_5G_CHANNEL;
    }


    public void updateVisibility(boolean visible) {
        mPreference.setVisible(visible);
    }

    public int getApChannelType() {
        if (mApChannelValue == 0) {
            return mApChannelValue;
        } else {
            return Integer.parseInt(mApChannelEntries[mApChannelValue].toString());
        }
    }

    private String getSummaryForApChannelType(int ApChannelType) {
        Log.d(TAG, "ApChannelType:" + ApChannelType + ":ApChannelEntry:" +mApChannelEntries[ApChannelType].toString());
        return mApChannelEntries[ApChannelType].toString();
    }

    private void getSoftapSupportChannels() {
        int m5GChannelsCount = 0;
        ArrayList<CharSequence> entries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> entryValues = new ArrayList<CharSequence>();

        mSoftApSupportChannels = Settings.Global.getString(
                mContext.getContentResolver(), Settings.Global.SOFTAP_SUPPORT_CHANNELS);
        Log.d(TAG, "mSoftApSupportChannels:" + mSoftApSupportChannels);
        if (mSoftApSupportChannels != null && mSoftApSupportChannels.contains(",")) {
            m5GChannels = mSoftApSupportChannels.split(",");
        } else {
            m5GChannels = null;
        }

        entries.add("Auto");
        entryValues.add("0");
        m5GChannelsCount++;
        if (mSoftApSupportChannels != null && m5GChannels != null) {
            for (int i = 0 ; i < m5GChannels.length; i++) {
                entries.add(m5GChannels[i]);
                entryValues.add(String.valueOf(m5GChannelsCount));
                m5GChannelsCount++;
            }
        }

        Log.d(TAG,  "m5GChannelsCount:" +m5GChannelsCount);
        mApChannelEntries = entries.toArray(new CharSequence[entries.size()]);
        mApChannelEntryValues = entryValues.toArray(new CharSequence[entryValues.size()]);
    }
}


