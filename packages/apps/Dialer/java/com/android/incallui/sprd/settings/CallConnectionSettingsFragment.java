package com.android.incallui.sprd.settings;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.android.dialer.app.R;

public class CallConnectionSettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.call_connection_prompt_settings_ex);
    }
}
