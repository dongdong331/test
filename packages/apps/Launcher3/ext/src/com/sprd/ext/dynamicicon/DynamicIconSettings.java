package com.sprd.ext.dynamicicon;

import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;

import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;
import com.sprd.ext.FeatureOption;
import com.sprd.ext.UtilitiesExt;

import java.util.ArrayList;

/**
 * Created on 5/30/18.
 */
public class DynamicIconSettings extends FragmentActivity {

    public static final String PREF_KEY_ORIGINAL_CALENDAR = "pref_original_calendar";
    public static final String PREF_KEY_GOOGLE_CALENDAR = "pref_google_calendar";
    public static final String PREF_KEY_ORIGINAL_CLOCK = "pref_original_clock";
    public static final String PREF_KEY_GOOGLE_CLOCK = "pref_google_clock";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dynamic_icon_fragment);
    }

    public static class DynamicIconSettingsFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener {
        private static final String PKG_NAME = "package_name";

        private Context mContext;
        private DynamicIconManager mDIManager;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(R.xml.dynamic_icon_settings);

            mContext = getActivity();
            mDIManager = DynamicIconManager.getInstance(mContext);
            ArrayList<String> installedPkgs = mDIManager.getInstalledDynamicPkgs();

            if (FeatureOption.SPRD_DYNAMIC_ICON_CALENDAR_SUPPORT) {
                verifyAndInitPref(PREF_KEY_ORIGINAL_CALENDAR,
                        getResources().getBoolean(R.bool.dynamic_calendar_default_state), installedPkgs);
                verifyAndInitPref(PREF_KEY_GOOGLE_CALENDAR,
                        getResources().getBoolean(R.bool.dynamic_calendar_default_state), installedPkgs);
            } else {
                getPreferenceScreen().removePreference(findPreference(PREF_KEY_ORIGINAL_CALENDAR));
                getPreferenceScreen().removePreference(findPreference(PREF_KEY_GOOGLE_CALENDAR));
            }

            if (FeatureOption.SPRD_DYNAMIC_ICON_CLOCK_SUPPORT) {
                verifyAndInitPref(PREF_KEY_ORIGINAL_CLOCK,
                        getResources().getBoolean(R.bool.dynamic_clock_default_state), installedPkgs);
                verifyAndInitPref(PREF_KEY_GOOGLE_CLOCK,
                        getResources().getBoolean(R.bool.dynamic_clock_default_state), installedPkgs);
            } else {
                getPreferenceScreen().removePreference(findPreference(PREF_KEY_ORIGINAL_CLOCK));
                getPreferenceScreen().removePreference(findPreference(PREF_KEY_GOOGLE_CLOCK));
            }
        }

        private void verifyAndInitPref(String key, boolean def, ArrayList<String> installedPkgs) {
            SwitchPreference pref = (SwitchPreference) findPreference(key);
            String pkg = mDIManager.getPackageNameByPrefKey(key);

            if (installedPkgs.contains(pkg)) {
                Bundle bundle = pref.getExtras();
                bundle.putString(PKG_NAME, pkg);

                pref.setIcon(UtilitiesExt.getAppIcon(mContext, pkg, Process.myUserHandle()));
                CharSequence title = UtilitiesExt.getAppLabelByPackageName(mContext, pkg);
                if (!TextUtils.isEmpty(title)) {
                    pref.setTitle(title);
                }
                boolean isChecked = DynamicIconUtils.getAppliedValue(mContext, key, def);
                pref.setChecked(isChecked);
                pref.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(pref);
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            if (preference instanceof SwitchPreference) {
                SwitchPreference pref = (SwitchPreference) preference;
                mDIManager.onSettingChanged(pref.peekExtras().getString(PKG_NAME), (boolean) o);
            }
            return true;
        }
    }
}
