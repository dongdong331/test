/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.wifi;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.net.wifi.WifiFeaturesUtils;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.provider.Settings;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.text.TextUtils;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnPause;
import com.android.settingslib.core.lifecycle.events.OnResume;

/**
 * {@link PreferenceController} that controls whether we should notify user when auto roam is
 * available.
 */
public class BgscanRoamingPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, LifecycleObserver, OnResume, OnPause {

    private static final String KEY_WIFI_AUTO_ROAM = "wifi_auto_roam";
    private SettingObserver mSettingObserver;

    public BgscanRoamingPreferenceController(Context context, Lifecycle lifecycle) {
        super(context);
        lifecycle.addObserver(this);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mSettingObserver = new SettingObserver(screen.findPreference(KEY_WIFI_AUTO_ROAM));
    }

    @Override
    public void onResume() {
        if (mSettingObserver != null) {
            mSettingObserver.register(mContext.getContentResolver(), true /* register */);
        }
    }

    @Override
    public void onPause() {
        if (mSettingObserver != null) {
            mSettingObserver.register(mContext.getContentResolver(), false /* register */);
        }
    }

    @Override
    public boolean isAvailable() {
        return WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_WIFI_AUTO_ROAM;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), KEY_WIFI_AUTO_ROAM)) {
            return false;
        }
        if (!(preference instanceof SwitchPreference)) {
            return false;
        }
        Settings.Global.putInt(mContext.getContentResolver(),
                WifiManager.WIFI_AUTO_ROAM_SWITCH,
                ((SwitchPreference) preference).isChecked() ? 1 : 0);
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_WIFI_AUTO_ROAM;
    }

    @Override
    public void updateState(Preference preference) {
        if (!(preference instanceof SwitchPreference)) {
            return;
        }
        final SwitchPreference autoRoam = (SwitchPreference) preference;
        autoRoam.setChecked(Settings.Global.getInt(mContext.getContentResolver(),
                WifiManager.WIFI_AUTO_ROAM_SWITCH, 0) == 1);
    }

    class SettingObserver extends ContentObserver {
        private final Uri AUTO_ROAM_URI = Settings.Global.getUriFor(
                WifiManager.WIFI_AUTO_ROAM_SWITCH);

        private final Preference mPreference;

        public SettingObserver(Preference preference) {
            super(new Handler());
            mPreference = preference;
        }

        public void register(ContentResolver cr, boolean register) {
            if (register) {
                cr.registerContentObserver(AUTO_ROAM_URI, false, this);
            } else {
                cr.unregisterContentObserver(this);
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (AUTO_ROAM_URI.equals(uri)) {
                updateState(mPreference);
            }
        }
    }
}
