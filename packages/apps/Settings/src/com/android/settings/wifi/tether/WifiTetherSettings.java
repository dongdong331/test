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

package com.android.settings.wifi.tether;

import static android.net.ConnectivityManager.ACTION_TETHER_STATE_CHANGED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_CHANGED_ACTION;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiFeaturesUtils;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.UserManager;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.dashboard.RestrictedDashboardFragment;
import com.android.settings.widget.SwitchBar;
import com.android.settings.widget.SwitchBarController;
import com.android.settingslib.core.AbstractPreferenceController;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.provider.Settings;

import android.net.wifi.WpsInfo;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.ConnectivityManager;
import android.database.ContentObserver;
import android.os.Handler;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


import static android.net.ConnectivityManager.TETHERING_WIFI;

import java.util.ArrayList;
import java.util.List;

public class WifiTetherSettings extends RestrictedDashboardFragment
        implements WifiTetherBasePreferenceController.OnTetherConfigUpdateListener,DialogInterface.OnClickListener,
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "WifiTetherSettings";
    private static final IntentFilter TETHER_STATE_CHANGE_FILTER;
    private static final String KEY_WIFI_TETHER_AUTO_OFF = "wifi_tether_auto_turn_off";
    private static final String HOTSPOT_CONNECTED_STATIONS = "hotspot_connected_stations";
    private static final String HOTSPOT_NO_CONNECTED_STATION = "hotspot_no_connected_station";
    private static final String HOTSPOT_BLOCKED_STATIONS = "hotspot_blocked_stations";
    private static final String HOTSPOT_NO_BLOCKED_STATION = "hotspot_no_blocked_station";
    private static final String HOTSPOT_WHITELIST_STATIONS = "hotspot_whitelist_stations";
    private static final String HOTSPOT_NO_WHITELIST_STATION = "hotspot_no_whitelist_station";
    private static final String HOTSPOT_MODE = "hotspot_mode";
    private static final String LIMIT_USER = "limit_user";
    private int mConnectedUser = DEFAULT_LIMIT;
    private static final String HOTSPOT_KEEP_WIFI_HOTSPOT_ON = "soft_ap_sleep_policy";
    private static final int HOTSPOT_NARMAL_MODE = 0;
    private static final int DIALOG_AP_SETTINGS = 1;
    private static final int DIALOG_ADD_WHITELIST = 2;
    public static final int WIFI_SOFT_AP_SLEEP_POLICY_NEVER = 0;
    public static final String STATIONS_STATE_CHANGED_ACTION = "com.sprd.settings.STATIONS_STATE_CHANGED";
    public static final String WIFI_SOFT_AP_SLEEP_POLICY = "wifi_soft_ap_sleep_policy_key";
    private static final String AP_CHANNEL = "ap_channel";
    private static final String AP_5G_CHANNEL = "ap_5g_channel";
    private static final String HOTSPOT_MAX_CONNECTIONS = "limit_user";

    private String[] mSecurityType;
    private String mUserConnectTitle;
    private String mUserNoConnectTitle;
    private String mUserBlockTitle;
    private String mUserNoBlockTitle;
    private Preference mCreateNetwork;
    private PreferenceCategory mConnectedStationsCategory;
    private Preference mHotspotNoConnectedStation;
    private PreferenceCategory mBlockedStationsCategory;
    private Preference mHotspotNoBlockedStations;
    private PreferenceCategory mWhitelistStationsCategory;
    private Preference mHotspotNoWhitelistStations;
    private ListPreference mHotspotMode;
    private ListPreference mHotspotKeepOn;
    private ListPreference mHotspotMaxConnections;
    private EditText mNameText;
    private EditText mMacText;

    private AlertDialog mAddDialog;
    private WifiManager mWifiManager;
    private WifiConfiguration mWifiConfig;
    private StateReceiver mStateReceiver;
    private ConnectivityManager mCm;
    private Handler mHandler = new Handler();

    private static final String HOTSPOT_WPS_MODE = "hotspot_wps_connect";
    private static final String HOTSPOT_HIDDEN_SSID = "hotspot_hidden_ssid";
    private static final int DIALOG_WPS_MODE = 3;
    private EditText mPinEdit;
    private TextView mPinText;
    private Spinner mModeSpinner;
    private Preference mHotspotWpsMode;
    private boolean supportHiddenSsid = false;
    private SwitchPreference mHotspotHiddenSsid;
    private AlertDialog mWpsDialog;
    private boolean needPin = false;
    private Button mAddWhitelistButton;
    public static final int DEFAULT_LIMIT = WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_MAX_NUMBER;
    private ListPreference mSoftApChanPref;
    private ListPreference mSoftAp5GChanPref;
    private WifiTetherSwitchBarController mSwitchBarController;
    private WifiTetherSSIDPreferenceController mSSIDPreferenceController;
    private WifiTetherPasswordPreferenceController mPasswordPreferenceController;
    private WifiTetherApBandPreferenceController mApBandPreferenceController;
    private WifiTetherSecurityPreferenceController mSecurityPreferenceController;
    private WifiTetherSoftApMaxNumPreferenceController mSoftApMaxNumPreferenceController;
    private WifiTetherSoftApChannelPreferenceController mWifiTetherSoftApChannelPreferenceController;
    private WifiTetherSoftAp5GChannelPreferenceController mWifiTetherSoftAp5GChannelPreferenceController;
    private boolean mRestartWifiApAfterConfigChange;
    private boolean mRestartWifiAfterChangeSoftapConfig;
    private static final int WIFI_ENABLED = 1;

    @VisibleForTesting
    TetherChangeReceiver mTetherChangeReceiver;

    static {
        TETHER_STATE_CHANGE_FILTER = new IntentFilter(ACTION_TETHER_STATE_CHANGED);
        TETHER_STATE_CHANGE_FILTER.addAction(WIFI_AP_STATE_CHANGED_ACTION);
    }

    public WifiTetherSettings() {
        super(UserManager.DISALLOW_CONFIG_TETHERING);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.WIFI_TETHER_SETTINGS;
    }

    @Override
    protected String getLogTag() {
        return "WifiTetherSettings";
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mTetherChangeReceiver = new TetherChangeReceiver();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Assume we are in a SettingsActivity. This is only safe because we currently use
        // SettingsActivity as base for all preference fragments.
        final SettingsActivity activity = (SettingsActivity) getActivity();
        final SwitchBar switchBar = activity.getSwitchBar();
        mSwitchBarController = new WifiTetherSwitchBarController(activity,
                new SwitchBarController(switchBar));
        getLifecycle().addObserver(mSwitchBarController);
        switchBar.show();

        //setRetainInstance(true);
        mConnectedStationsCategory = (PreferenceCategory) findPreference(HOTSPOT_CONNECTED_STATIONS);
        mBlockedStationsCategory = (PreferenceCategory) findPreference(HOTSPOT_BLOCKED_STATIONS);
        mWhitelistStationsCategory = (PreferenceCategory) findPreference(HOTSPOT_WHITELIST_STATIONS);
        mHotspotNoConnectedStation = (Preference) findPreference(HOTSPOT_NO_CONNECTED_STATION);
        mHotspotNoBlockedStations = (Preference) findPreference(HOTSPOT_NO_BLOCKED_STATION);
        mHotspotNoWhitelistStations = (Preference) findPreference(HOTSPOT_NO_WHITELIST_STATION);
        mHotspotMode = (ListPreference) findPreference(HOTSPOT_MODE);
        mHotspotKeepOn = (ListPreference) findPreference(HOTSPOT_KEEP_WIFI_HOTSPOT_ON);
        //NOTE: Bug #505201 Add for softap support wps connect mode and hidden ssid Feature BEG-->
        mHotspotWpsMode = (Preference) findPreference(HOTSPOT_WPS_MODE);
        mHotspotHiddenSsid = (SwitchPreference) findPreference(HOTSPOT_HIDDEN_SSID);
        mSoftApChanPref = (ListPreference) findPreference(AP_CHANNEL);
        mSoftAp5GChanPref = (ListPreference) findPreference(AP_5G_CHANNEL);
        mHotspotMaxConnections = (ListPreference) findPreference(HOTSPOT_MAX_CONNECTIONS);

        if (mHotspotWpsMode != null && getResources().getBoolean(
                com.android.internal.R.bool.config_enableSoftApWPS) == false) {
            getPreferenceScreen().removePreference(mHotspotWpsMode);
        } else {
            mHotspotHiddenSsid.setSummary(R.string.hotspot_hidden_ssid_and_disable_wps_summary);
        }

        if (DEFAULT_LIMIT == 10) {
            mHotspotMaxConnections.setEntries(R.array.wifi_ap_max_connect_default);
            mHotspotMaxConnections.setEntryValues(R.array.wifi_ap_max_connect_default);
        } else if (DEFAULT_LIMIT == 8) {
            mHotspotMaxConnections.setEntries(R.array.wifi_ap_max_connect_8);
            mHotspotMaxConnections.setEntryValues(R.array.wifi_ap_max_connect_8);
        } else if (DEFAULT_LIMIT == 5) {
            mHotspotMaxConnections.setEntries(R.array.wifi_ap_max_connect_5);
            mHotspotMaxConnections.setEntryValues(R.array.wifi_ap_max_connect_5);
        }

        if (!WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_WHITE_LIST) {
            getPreferenceScreen().removePreference(mWhitelistStationsCategory);
            getPreferenceScreen().removePreference(mHotspotNoWhitelistStations);
            getPreferenceScreen().removePreference(mHotspotMode);
        }
        initWifiTethering();
        if (mHotspotMode != null) {
            mHotspotMode.setOnPreferenceChangeListener(this);
            int value = Settings.Global.getInt(getContentResolver(),
                    HOTSPOT_MODE,HOTSPOT_NARMAL_MODE);
            String stringValue = String.valueOf(value);
            mHotspotMode.setValue(stringValue);
            updateControlModeSummary(mHotspotMode, stringValue);
        }

        if (mHotspotKeepOn != null) {
            //add for keep wifi hotspot on
            mHotspotKeepOn.setOnPreferenceChangeListener(this);
            int value = Settings.System.getInt(getActivity().getContentResolver(),WIFI_SOFT_AP_SLEEP_POLICY,
                    WIFI_SOFT_AP_SLEEP_POLICY_NEVER);
            String stringValue = String.valueOf(value);
            mHotspotKeepOn.setValue(stringValue);
            updateHotspotKeepOnSummary(mHotspotKeepOn, stringValue);
        }

        //if support sprd softap & LTE coexist, softap channel should not be set by user
        if (WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_COEXIST_LTE && mSoftApChanPref != null) {
            getPreferenceScreen().removePreference(mSoftApChanPref);
        }
        if (WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_HIDE_5G_CHANNEL&& mSoftAp5GChanPref != null) {
            getPreferenceScreen().removePreference(mSoftAp5GChanPref);
        }

        if (mApBandPreferenceController.getBandIndex() == 1) {
            if (mWifiTetherSoftAp5GChannelPreferenceController != null && mSoftAp5GChanPref != null){
                mWifiTetherSoftAp5GChannelPreferenceController.updateVisibility(true);
            }
            if (mWifiTetherSoftApChannelPreferenceController != null && mSoftApChanPref != null){
                mWifiTetherSoftApChannelPreferenceController.updateVisibility(false);
            }
        } else if (mApBandPreferenceController.getBandIndex() == 0) {
            if (mWifiTetherSoftAp5GChannelPreferenceController != null && mSoftAp5GChanPref != null){
                mWifiTetherSoftAp5GChannelPreferenceController.updateVisibility(false);
            }
            if (mWifiTetherSoftApChannelPreferenceController != null && mSoftApChanPref != null){
                mWifiTetherSoftApChannelPreferenceController.updateVisibility(true);
            }
        } else {
            if (mWifiTetherSoftAp5GChannelPreferenceController != null && mSoftAp5GChanPref != null){
                mWifiTetherSoftAp5GChannelPreferenceController.updateVisibility(false);
            }
            if (mWifiTetherSoftApChannelPreferenceController != null && mSoftApChanPref != null){
                mWifiTetherSoftApChannelPreferenceController.updateVisibility(false);
            }
        }

        //NOTE: Bug #505201 Add for softap support wps connect mode and hidden ssid Feature BEG-->
        if (WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_HIDE_SSID) {
            supportHiddenSsid = true;
            refreshHiddenSsidState();
        } else {
            supportHiddenSsid = false;
            if (mHotspotHiddenSsid != null) getPreferenceScreen().removePreference(mHotspotHiddenSsid);
        }
        //<-- Add for softap support wps connect mode and hidden ssid Feature END

    }

    @Override
    public void onStart() {
        super.onStart();
        final Context context = getContext();
        if (context != null) {
            context.registerReceiver(mTetherChangeReceiver, TETHER_STATE_CHANGE_FILTER);
            updateStations();
            mStateReceiver = new StateReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
            filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            filter.addAction(WifiManager.WIFI_AP_CONNECTION_CHANGED_ACTION);
            filter.addAction(WifiManager.SOFTAP_BLOCKLIST_AVAILABLE_ACTION);
            filter.addAction(WifiManager.WIFI_AP_CLIENT_DETAILINFO_AVAILABLE_ACTION);
            context.registerReceiver(mStateReceiver, filter);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        final Context context = getContext();
        if (context != null) {
            context.unregisterReceiver(mTetherChangeReceiver);
            context.unregisterReceiver(mStateReceiver);
        }
    }


    @Override
    protected int getPreferenceScreenResId() {
        if (WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_FEATURES) {
            return R.xml.hotspot_tether_settings;
        } else {
            return R.xml.wifi_tether_settings;
        }
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        mSSIDPreferenceController = new WifiTetherSSIDPreferenceController(context, this);
        mSecurityPreferenceController = new WifiTetherSecurityPreferenceController(context, this);
        mPasswordPreferenceController = new WifiTetherPasswordPreferenceController(context, this);
        mApBandPreferenceController = new WifiTetherApBandPreferenceController(context, this);
        mSoftApMaxNumPreferenceController = new WifiTetherSoftApMaxNumPreferenceController(context, this);
        controllers.add(mSSIDPreferenceController);
        controllers.add(mSecurityPreferenceController);
        controllers.add(mPasswordPreferenceController);
        controllers.add(mApBandPreferenceController);
        controllers.add(
                new WifiTetherAutoOffPreferenceController(context, KEY_WIFI_TETHER_AUTO_OFF));
        controllers.add(mSoftApMaxNumPreferenceController);

        mWifiTetherSoftAp5GChannelPreferenceController = new WifiTetherSoftAp5GChannelPreferenceController(context, this);
        controllers.add(mWifiTetherSoftAp5GChannelPreferenceController);
        mWifiTetherSoftApChannelPreferenceController = new WifiTetherSoftApChannelPreferenceController(context, this);
        controllers.add(mWifiTetherSoftApChannelPreferenceController);
        return controllers;
    }

    @Override
    public boolean onPreferenceTreeClick(
            Preference preference) {
        if (preference == mHotspotNoWhitelistStations) {
            showDialog(DIALOG_ADD_WHITELIST);
        //NOTE: Bug #505201 Add for softap support wps connect mode and hidden ssid Feature BEG-->
        } else if (preference == mHotspotWpsMode) {
            showDialog(DIALOG_WPS_MODE);
        } else if (preference == mHotspotHiddenSsid) {
            mWifiConfig = mWifiManager.getWifiApConfiguration();
            if (mWifiConfig != null) {
                if (mHotspotHiddenSsid.isChecked()) {
                    if (mHotspotWpsMode != null) {
                        mHotspotWpsMode.setEnabled(false);
                    }
                    mWifiConfig.hiddenSSID = true;
                } else {
                    mWifiConfig.hiddenSSID = false;
                    if (mHotspotWpsMode != null) {
                        mHotspotWpsMode.setEnabled(isWpsCanUse());
                    }
                }
                mWifiManager.setWifiApConfiguration(mWifiConfig);
                Log.i(TAG, "setWifiApConfiguration mWifiConfig.hiddenSSID = " + mWifiConfig.hiddenSSID);
            } else {
                Log.e(TAG, "mWifiConfig is null ");
            }
        //<-- Add for softap support wps connect mode and hidden ssidFeature END
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public Dialog onCreateDialog(int id) {
        if (id == DIALOG_ADD_WHITELIST) {
            LayoutInflater inflater = getLayoutInflater();
            View layout = inflater.inflate(R.layout.hotspot_add_whitelist,null);
            mNameText = (EditText)layout.findViewById(R.id.nameText);
            mMacText = (EditText)layout.findViewById(R.id.macText);
            mAddDialog = new AlertDialog.Builder(getActivity()).setTitle(R.string.hotspot_whitelist).setView(layout)
                    .setPositiveButton(R.string.hotspot_whitelist_add, addClickListener)
                    .setNegativeButton(R.string.hotspot_whitelist_cancel, null).show();
            mAddWhitelistButton = mAddDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            mAddWhitelistButton.setEnabled(false);
            mNameText.addTextChangedListener(addTextChangedListener);
            mMacText.addTextChangedListener(addTextChangedListener);
            return mAddDialog;
        //NOTE: Bug #505201 Add for softap support wps connect mode and hidden ssid Feature BEG-->
        } else if (id == DIALOG_WPS_MODE) {
            LayoutInflater inflater = getLayoutInflater();
            View layout = inflater.inflate(R.layout.hotspot_wps_mode,null);
            mPinEdit = (EditText)layout.findViewById(R.id.pin_number);
            mPinText = (TextView)layout.findViewById(R.id.hotspot_wps_pin);
            mModeSpinner = (Spinner)layout.findViewById(R.id.hotspot_wps_mode);
            mModeSpinner.setOnItemSelectedListener(wpsSelectedListener);
            //NOTE: Bug #505201 Add for softap support wps connect mode and hidden ssid Feature BEG-->
            mWpsDialog = new AlertDialog.Builder(getActivity()).setTitle(R.string.hotspot_wps_connect).setView(layout)
                    .setPositiveButton(R.string.hotspot_connect, null)
                    .setNegativeButton(R.string.hotspot_whitelist_cancel, null).show();
            mWpsDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(connectClickListener);
            return mWpsDialog;
            //<-- Add for softap support wps connect mode and hidden ssidFeature END
        }

        return null;
    }

    public void onClick(DialogInterface dialogInterface, int button) {
        if (button == DialogInterface.BUTTON_POSITIVE) {
            if (mWifiConfig != null) {
                /**
                 * if soft AP is stopped, bring up else restart with new config
                 * TODO: update config on a running access point when framework
                 * support is added
                 */
                 //NOTE: Bug #505201 Add for softap support wps connect mode and hidden ssid Feature BEG-->
                if (mHotspotWpsMode != null) {
                    mHotspotWpsMode.setEnabled(isWpsCanUse());
                }
            } else {
                if (mHotspotWpsMode != null) {
                    mHotspotWpsMode.setEnabled(false);
                }
                //<-- Add for softap support wps connect mode and hidden ssidFeature END
            }
        }
    }


    OnClickListener addClickListener = new OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            // TODO Auto-generated method stub
            if (which == DialogInterface.BUTTON_POSITIVE) {
                List<String> mWhitelistStations = mWifiManager.softApGetClientWhiteList();
                if (mWhitelistStations != null && mWhitelistStations.size() >= DEFAULT_LIMIT) {
                    Activity activity = getActivity();
                    String error = "null";
                    if (activity != null) {
                        error = String.format(activity.getString(R.string.wifi_add_whitelist_limit_error), DEFAULT_LIMIT);
                    }
                    Toast.makeText(getActivity(), error, Toast.LENGTH_SHORT).show();
                    return;
                }
                mWifiManager.softApAddClientToWhiteList(mMacText.getText().toString().trim(), mNameText.getText().toString().trim());
                addWhitelistStations();
            }
        }
    };

    private void initWifiTethering() {
        final Activity activity = getActivity();
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mCm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiConfig = mWifiManager.getWifiApConfiguration();
        mSecurityType = getResources().getStringArray(R.array.wifi_ap_security);
        mRestartWifiApAfterConfigChange = false;
        mRestartWifiAfterChangeSoftapConfig = false;
        mUserConnectTitle = activity.getString(R.string.wifi_tether_connect_title);
        mUserBlockTitle = activity.getString(R.string.wifi_tether_block_title);
        mUserNoConnectTitle = activity.getString(R.string.hotspot_connected_stations);
        mUserNoBlockTitle = activity.getString(R.string.hotspot_blocked_stations);
        //NOTE: Bug #505201 Add for softap support wps connect mode and hidden ssid Feature BEG-->
        if (mHotspotWpsMode != null) {
            if (mWifiManager.getWifiApState() != WifiManager.WIFI_AP_STATE_ENABLED
                    || mWifiConfig == null) {
                mHotspotWpsMode.setEnabled(false);
            } else if (mWifiConfig.getAuthType() == KeyMgmt.NONE) {
                mHotspotWpsMode.setEnabled(false);
            }
        }
        //<-- Add for softap support wps connect mode and hidden ssidFeature END
    }

    private void updateStations() {
        addConnectedStations();
        addBlockedStations();
        addWhitelistStations();
    }

    private void addConnectedStations() {
        List<String> mConnectedStationsDetail = mWifiManager.softApGetConnectedStationsDetail();
        mConnectedStationsCategory.removeAll();
        if (mConnectedStationsDetail == null || mConnectedStationsDetail.isEmpty()) {
            mConnectedStationsCategory.addPreference(mHotspotNoConnectedStation);
            mConnectedStationsCategory.setTitle(mUserNoConnectTitle);
            return;
        }
        mConnectedStationsCategory.setTitle(mConnectedStationsDetail.size() + mUserConnectTitle);
        for (String mConnectedStationsStr:mConnectedStationsDetail) {
            String[] mConnectedStations = mConnectedStationsStr.split(" ");
            if (mConnectedStations.length == 3) {
                mConnectedStationsCategory.addPreference(new Station(getActivity(), mConnectedStations[2], mConnectedStations[0], mConnectedStations[1], true, false));
            } else {
                mConnectedStationsCategory.addPreference(new Station(getActivity(), null, mConnectedStations[0], null, true, false));
            }
        }
    }

    private void addBlockedStations() {
        List<String> mBlockedStationsDetail = mWifiManager.softApGetBlockedStationsDetail();
        mBlockedStationsCategory.removeAll();
        if (mBlockedStationsDetail == null || mBlockedStationsDetail.isEmpty()) {
            mBlockedStationsCategory.addPreference(mHotspotNoBlockedStations);
            mBlockedStationsCategory.setTitle(mUserNoBlockTitle);
            return;
        }
        mBlockedStationsCategory.setTitle(mBlockedStationsDetail.size() + mUserBlockTitle);
        for (String mBlockedStationsStr:mBlockedStationsDetail) {
            String[] mBlockedStations = mBlockedStationsStr.split(" ");

            if (mBlockedStations.length == 3) {
                mBlockedStationsCategory.addPreference(new Station(getActivity(), mBlockedStations[2], mBlockedStations[0], null, false, false));
            } else {
                mBlockedStationsCategory.addPreference(new Station(getActivity(), null, mBlockedStations[0], null, false, false));
            }
        }
    }

    private void addWhitelistStations() {
         List<String> mWhitelistStationsDetail = mWifiManager.softApGetClientWhiteList();

         mWhitelistStationsCategory.removeAll();
         if (mWhitelistStationsDetail == null || mWhitelistStationsDetail.isEmpty()) {
             return;
         }
         for (String mWhitelistStationsStr:mWhitelistStationsDetail) {
             String[] mWhitelistStations = mWhitelistStationsStr.split(" ");
             int len = mWhitelistStations[0].length();
             if (mWhitelistStations.length >= 2) {
                 mWhitelistStationsCategory.addPreference(new Station(getActivity(), mWhitelistStationsStr.substring(len+1), mWhitelistStations[0], null, false, true));
             } else {
                 mWhitelistStationsCategory.addPreference(new Station(getActivity(), null, mWhitelistStations[0], null, false, true));
             }
         }
     }

    private class StateReceiver extends BroadcastReceiver {
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            if (action.equals(WifiManager.WIFI_AP_CONNECTION_CHANGED_ACTION)
                    || action.equals(WifiManager.SOFTAP_BLOCKLIST_AVAILABLE_ACTION)
                    || action.equals(WifiManager.WIFI_AP_CLIENT_DETAILINFO_AVAILABLE_ACTION)) {
                updateStations();
            } else if (action.equals(WifiManager.WIFI_AP_STATE_CHANGED_ACTION)) {
                int hotspotState = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_AP_STATE,
                        WifiManager.WIFI_AP_STATE_FAILED);
                if (hotspotState != WifiManager.WIFI_AP_STATE_ENABLED) {
                    mConnectedStationsCategory.removeAll();
                    mConnectedStationsCategory.addPreference(mHotspotNoConnectedStation);
                    mConnectedStationsCategory.setTitle(mUserNoConnectTitle);
                    mBlockedStationsCategory.removeAll();
                    mBlockedStationsCategory.addPreference(mHotspotNoBlockedStations);
                    mBlockedStationsCategory.setTitle(mUserNoBlockTitle);
                    //NOTE: Bug #505201 Add for softap support wps connect mode and hidden ssid Feature BEG-->
                    if(mWpsDialog !=null && mWpsDialog.isShowing()){
                        mWpsDialog.dismiss();
                    }
                    if (mHotspotWpsMode != null) {
                        mHotspotWpsMode.setEnabled(false);
                    }
                    /*
                    //<-- Add for softap support wps connect mode and hidden ssidFeature END
                    if (hotspotState == WifiManager.WIFI_AP_STATE_DISABLED
                            && mRestartWifiApAfterConfigChange && !mNeedStartWifiAfterConfigChange) {
                        mRestartWifiApAfterConfigChange = false;
                        mNeedStartWifiAfterConfigChange = false;
                        Log.d(TAG, "Restarting WifiAp due to prior config change.");
                        mWifiManager.startSoftAp(mWifiConfig);
                        //mCm.startTethering(TETHERING_WIFI, true, null, mHandler);
                    }*/
                } else {
                    //NOTE: Bug #505201 Add for softap support wps connect mode and hidden ssid Feature BEG-->
                    if (mHotspotWpsMode != null) {
                        mHotspotWpsMode.setEnabled(isWpsCanUse());
                    }
                    //updateStations();
                //}
                    updateStations();
                }
                if (supportHiddenSsid) {
                    refreshHiddenSsidState();
                }
                //<-- Add for softap support wps connect mode and hidden ssidFeature END
            } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if ((wifiState == WifiManager.WIFI_STATE_ENABLED || wifiState == WifiManager.WIFI_STATE_UNKNOWN)
                    && mRestartWifiApAfterConfigChange
                    && mRestartWifiAfterChangeSoftapConfig) {
                    startTether();
                }
            }/* else if (action.equals(ConnectivityManager.ACTION_TETHER_STATE_CHANGED)) {
                if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_DISABLED
                        && mRestartWifiApAfterConfigChange) {
                    mRestartWifiApAfterConfigChange = false;
                    Log.d(TAG, "Restarting WifiAp due to prior config change.");
                    mCm.startTethering(TETHERING_WIFI, true, null, mHandler);
                }
            }*/
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // TODO Auto-generated method stub
        final Context context = getActivity();
        String key = preference.getKey();
        if (HOTSPOT_MODE.equals(key)) {
            try {
                String stringValue = (String) newValue;
                Settings.Global.putInt(getContentResolver(), HOTSPOT_MODE,
                        Integer.parseInt(stringValue));
                updateControlModeSummary(preference, stringValue);
            } catch (NumberFormatException e) {
                Toast.makeText(context, R.string.wifi_setting_sleep_policy_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        } else if (HOTSPOT_KEEP_WIFI_HOTSPOT_ON.equals(key)) {
            try {
                int value = Integer.parseInt((String) newValue);
                Settings.System.putInt(getActivity().getContentResolver(),WIFI_SOFT_AP_SLEEP_POLICY,value);
                updateHotspotKeepOnSummary(preference, (String) newValue);
            } catch (IllegalArgumentException e) {
                return false;
            }
        } 
        return true;
    }

    private void updateControlModeSummary(Preference modePref, String value) {
        if (value != null) {
            String[] values = getResources().getStringArray(R.array.hotspot_mode_values);
            String[] summaries = getResources().getStringArray(R.array.hotspot_mode);
            for (int i = 0; i < values.length; i++) {
                if (value.equals(values[i])) {
                    if (i < summaries.length) {
                        modePref.setSummary(summaries[i]);
                        mWifiManager.softApSetClientWhiteListEnabled((i==1));
                        updateModePref((i==1));
                        return;
                    }
                }
            }
        }

        modePref.setSummary("");
        Log.e(TAG, "Invalid controlMode value: " + value);
    }

    private boolean checkMac(String str) {
        String patternStr = "^[A-Fa-f0-9]{2}(:[A-Fa-f0-9]{2}){5}$";
        return Pattern.matches(patternStr, str);
    }

    private void updateModePref(boolean mode) {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (mode) {
            if (mBlockedStationsCategory != null) preferenceScreen.removePreference(mBlockedStationsCategory);
        } else {
            preferenceScreen.addPreference(mBlockedStationsCategory);
        }
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        if (dialogId == DIALOG_ADD_WHITELIST) {
            return MetricsEvent.DIALOG_ADD_WHITELIST;
        } else if (dialogId == DIALOG_WPS_MODE) {
            return MetricsEvent.DIALOG_WPS_MODE;
        }
        return 0;
    }

    private void updateHotspotKeepOnSummary(Preference modePref, String value) {
        if (value !=null) {
            String[] values = getResources().getStringArray(R.array.soft_ap_sleep_policy_entryvalues);
            String[] summaries = getResources().getStringArray(R.array.soft_ap_sleep_policy_entries);
            for (int i = 0; i < values.length; i++) {
                if (value.equals(values[i])) {
                    if (i < summaries.length) {
                        modePref.setSummary(summaries[i]);
                        return;
                    }
                }
            }
        }

        modePref.setSummary("");
        Log.e(TAG, "Invalid  value: " + value);
    }

    //=============================================================================
    // add by sprd start
    //=============================================================================
    //NOTE: Bug #505201 Add for softap support wps connect mode and hidden ssid Feature BEG-->
    private void refreshHiddenSsidState() {
        if (mWifiManager == null) {
            mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        }
        if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_DISABLED) {
            mHotspotHiddenSsid.setEnabled(true);
        } else {
            mHotspotHiddenSsid.setEnabled(false);
        }
        if (mWifiConfig != null) {
            Log.i(TAG, "mWifiConfig.hiddenSSID = " + mWifiConfig.hiddenSSID);
            if (mWifiConfig.hiddenSSID) {
                mHotspotHiddenSsid.setChecked(true);
                if (mHotspotWpsMode != null) mHotspotWpsMode.setEnabled(false);
            } else {
                mHotspotHiddenSsid.setChecked(false);
                if (mHotspotWpsMode != null) {
                    mHotspotWpsMode.setEnabled(isWpsCanUse());
                }
            }
        } else {
            if (mHotspotWpsMode != null) {
                mHotspotWpsMode.setEnabled(false);
            }
        }
    }
    private boolean isWpsCanUse() {
        if (mWifiManager == null) {
            mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        }
        WifiConfiguration wifiConfig = mWifiManager.getWifiApConfiguration();
        if (supportHiddenSsid) {
            if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED
                    && wifiConfig != null
                    && wifiConfig.getAuthType() != KeyMgmt.NONE
                    && !wifiConfig.hiddenSSID) {
                return true;
            } else {
                return false;
            }
        } else {
            if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED
                    && wifiConfig != null
                    && wifiConfig.getAuthType() != KeyMgmt.NONE) {
                return true;
            } else {
                return false;
            }
        }
    }

    OnItemSelectedListener wpsSelectedListener = new OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view,
                int position, long id) {
            if (position == 0) {
                mPinEdit.setVisibility(View.GONE);
                mPinText.setVisibility(View.GONE);
                needPin = false;
            } else {
                mPinEdit.setVisibility(View.VISIBLE);
                mPinText.setVisibility(View.VISIBLE);
                needPin = true;
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    };

    View.OnClickListener connectClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (needPin) {
                if (mWifiManager.softApWpsCheckPin(mPinEdit.getText().toString().trim())) {
                    WpsInfo config = new WpsInfo();
                    config.pin = mPinEdit.getText().toString().trim();
                    config.setup = WpsInfo.KEYPAD;
                    Log.d(TAG,"hotspot wps config: "+config.toString());
                    mWpsDialog.dismiss();
                    mWifiManager.softApStartWps(config,null);
                } else {
                    Toast.makeText(getActivity(), R.string.hotspot_pin_error,
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                WpsInfo config = new WpsInfo();
                config.setup = WpsInfo.PBC;
                Log.d(TAG,"hotspot wps config: "+config.toString());
                mWpsDialog.dismiss();
                mWifiManager.softApStartWps(config,null);
            }
        }
    };
    TextWatcher addTextChangedListener =new TextWatcher(){
        @Override
        public void beforeTextChanged(CharSequence s, int start,
            int count, int after) {
                // TODO Auto-generated method stub
        }
        @Override
        public void onTextChanged(CharSequence s, int start,
                int before, int count) {
            // TODO Auto-generated method stub
        }
        @Override
        public void afterTextChanged(Editable s) {
                mAddWhitelistButton.setEnabled(isAddWhitelistButtonEnabled());
        }
    };
    private boolean isAddWhitelistButtonEnabled() {
        if (!checkMac(mMacText.getText().toString().trim())) {
            return false;
        }
        if (mNameText.getText().toString().trim() == null
                || mNameText.getText().toString().trim().equals("")) {
            return false;
        }
        return true;
    }

    @Override
    public void onTetherConfigUpdated() {
        final WifiConfiguration config = buildNewConfig();
        mPasswordPreferenceController.updateVisibility(config.getAuthType());
        if (mApBandPreferenceController.getBandIndex() == 1) {
            if (mWifiTetherSoftAp5GChannelPreferenceController != null && mSoftAp5GChanPref != null){
                mWifiTetherSoftAp5GChannelPreferenceController.updateVisibility(true);
            }
            if (mWifiTetherSoftApChannelPreferenceController != null && mSoftApChanPref != null){
                mWifiTetherSoftApChannelPreferenceController.updateVisibility(false);
            }
        } else if (mApBandPreferenceController.getBandIndex() == 0) {
            if (mWifiTetherSoftAp5GChannelPreferenceController != null && mSoftAp5GChanPref != null){
                mWifiTetherSoftAp5GChannelPreferenceController.updateVisibility(false);
            }
            if (mWifiTetherSoftApChannelPreferenceController != null && mSoftApChanPref != null){
                mWifiTetherSoftApChannelPreferenceController.updateVisibility(true);
            }
        } else {
            if (mWifiTetherSoftAp5GChannelPreferenceController != null && mSoftAp5GChanPref != null){
                mWifiTetherSoftAp5GChannelPreferenceController.updateVisibility(false);
            }
            if (mWifiTetherSoftApChannelPreferenceController != null && mSoftApChanPref != null){
                mWifiTetherSoftApChannelPreferenceController.updateVisibility(false);
            }
        }

        /**
         * if soft AP is stopped, bring up
         * else restart with new config
         * TODO: update config on a running access point when framework support is added
         */
        if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED) {
            Log.d("TetheringSettings",
                    "Wifi AP config changed while enabled, stop and restart");
            mRestartWifiApAfterConfigChange = true;
            try {
                int wifiState = Settings.Global.getInt(getActivity().getContentResolver(),
                    Settings.Global.WIFI_SAVED_STATE);
                if (wifiState == WIFI_ENABLED) {
                    mRestartWifiAfterChangeSoftapConfig = true;
                }
            }catch (Settings.SettingNotFoundException e) {
            }
            mSwitchBarController.stopTether();
        }
        mWifiManager.setWifiApConfiguration(config);
    }

    private WifiConfiguration buildNewConfig() {
        final WifiConfiguration config = new WifiConfiguration();
        final int securityType = mSecurityPreferenceController.getSecurityType();

        config.SSID = mSSIDPreferenceController.getSSID();
        config.allowedKeyManagement.set(securityType);
        config.preSharedKey = mPasswordPreferenceController.getPasswordValidated(securityType);
        config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        config.apBand = mApBandPreferenceController.getBandIndex();
        config.softApMaxNumSta = mSoftApMaxNumPreferenceController.getApMaxConnectType();
        if (config.apBand == 1) {
            config.apChannel = mWifiTetherSoftAp5GChannelPreferenceController.getApChannelType();
        } else {
            config.apChannel = mWifiTetherSoftApChannelPreferenceController.getApChannelType();
        }
        config.hiddenSSID = mWifiConfig.hiddenSSID;
        Log.d(TAG, "config.apBand: " + config.apBand + ":config.apChannel:" + config.apChannel);

        return config;
    }

    private void startTether() {
        mRestartWifiApAfterConfigChange = false;
        mRestartWifiAfterChangeSoftapConfig = false;
        mSwitchBarController.startTether();
    }

    private void updateDisplayWithNewConfig() {
        use(WifiTetherSSIDPreferenceController.class)
                .updateDisplay();
        use(WifiTetherSecurityPreferenceController.class)
                .updateDisplay();
        use(WifiTetherPasswordPreferenceController.class)
                .updateDisplay();
        use(WifiTetherApBandPreferenceController.class)
                .updateDisplay();
    }

    @VisibleForTesting
    class TetherChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "receiving broadcast action " + action);
            //updateDisplayWithNewConfig();
            if (action.equals(ACTION_TETHER_STATE_CHANGED)) {
                /*if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_DISABLED
                        && mRestartWifiApAfterConfigChange) {
                    startTether();
                }*/
            } else if (action.equals(WIFI_AP_STATE_CHANGED_ACTION)) {
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_AP_STATE, 0);
                if (state == WifiManager.WIFI_AP_STATE_DISABLED
                        && mRestartWifiApAfterConfigChange
                        && !mRestartWifiAfterChangeSoftapConfig) {
                    startTether();
                }
            }
        }
    }
}
