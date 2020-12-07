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

package com.android.settings;

import static android.net.ConnectivityManager.TETHERING_BLUETOOTH;
import static android.net.ConnectivityManager.TETHERING_USB;
import static android.net.ConnectivityManager.TETHERING_WIFI;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.UserManager;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.datausage.DataSaverBackend;
import com.android.settings.wifi.tether.WifiTetherPreferenceController;
import com.android.settingslib.TetherUtil;
import com.android.settings.widget.FixedLineSummaryPreference;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import android.os.SystemProperties;
import android.widget.Toast;
import com.sprd.settings.SprdChooseOS;
import android.util.Log;
import android.net.wifi.WifiManager;

/*
 * Displays preferences for Tethering.
 */
public class TetherSettings extends RestrictedSettingsFragment
        implements DataSaverBackend.Listener {

    private static final String USB_TETHER_SETTINGS = "usb_tether_settings";
    private static final String ENABLE_BLUETOOTH_TETHERING = "enable_bluetooth_tethering";
    private static final String DATA_SAVER_FOOTER = "disabled_on_data_saver";

    /* SPRD:Add for 692657 androido porting :pc net share @{ */
    private static final String USB_PC_SHARE_SETTINGS = "usb_pc_share_settings";
    private static final boolean SUPPORT_USB_REVERSE_TETHER = SystemProperties.getBoolean("persist.sys.usb-pc.tethering",true);
    private static final int PROVISION_REQUEST_USB_PC_TETHER = 1;
    public static final int TETHERING_INVALID   = -1;
    private static final String WIFI_TETHER_SETTINGS = "wifi_tether";
    private SwitchPreference mUsbPcShare;
    private FixedLineSummaryPreference mWifiTether;
    private int mTetherChoice = TETHERING_INVALID;
    private WifiManager mWifiManager;
    /* Bug692657 end @} */

    private static final String TAG = "TetheringSettings";

    private SwitchPreference mUsbTether;

    private SwitchPreference mBluetoothTether;

    private BroadcastReceiver mTetherChangeReceiver;

    private String[] mUsbRegexs;
    private String[] mBluetoothRegexs;
    private AtomicReference<BluetoothPan> mBluetoothPan = new AtomicReference<>();

    private Handler mHandler = new Handler();
    private OnStartTetheringCallback mStartTetheringCallback;
    private ConnectivityManager mCm;

    private WifiTetherPreferenceController mWifiTetherPreferenceController;

    private boolean mUsbConnected;

    private boolean mBluetoothEnableForTether;
    private boolean mUnavailable;

    private DataSaverBackend mDataSaverBackend;
    private boolean mDataSaverEnabled;
    private Preference mDataSaverFooter;

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.TETHER;
    }

    public TetherSettings() {
        super(UserManager.DISALLOW_CONFIG_TETHERING);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (!Utils.WCN_DISABLED) {
            mWifiTetherPreferenceController =
                    new WifiTetherPreferenceController(context, getLifecycle());
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.tether_prefs);
        if (Utils.WCN_DISABLED) {
            mFooterPreferenceMixin.createFooterPreference()
                    .setTitle(R.string.tethering_footer_info_no_wcn);
        } else {
            mFooterPreferenceMixin.createFooterPreference()
                    .setTitle(R.string.tethering_footer_info);
        }

        mDataSaverBackend = new DataSaverBackend(getContext());
        mDataSaverEnabled = mDataSaverBackend.isDataSaverEnabled();
        mDataSaverFooter = findPreference(DATA_SAVER_FOOTER);

        setIfOnlyAvailableForAdmins(true);
        if (isUiRestricted()) {
            mUnavailable = true;
            getPreferenceScreen().removeAll();
            return;
        }

        final Activity activity = getActivity();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
         // SPRD: Modify for Bug#875145. Get PAN proxy just when Bluetooth is ON.
        if (adapter != null &&adapter.getState() == BluetoothAdapter.STATE_ON) {
            adapter.getProfileProxy(activity.getApplicationContext(), mProfileServiceListener,
                    BluetoothProfile.PAN);
        }

        mWifiTether = (FixedLineSummaryPreference) findPreference(WIFI_TETHER_SETTINGS);
        mUsbTether = (SwitchPreference) findPreference(USB_TETHER_SETTINGS);
        // SPRD:Add for bug692657 androido porting pc net share
        mUsbPcShare = (SwitchPreference) findPreference(USB_PC_SHARE_SETTINGS);
        mWifiManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        mBluetoothTether = (SwitchPreference) findPreference(ENABLE_BLUETOOTH_TETHERING);

        mCm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        mUsbRegexs = mCm.getTetherableUsbRegexs();
        mBluetoothRegexs = mCm.getTetherableBluetoothRegexs();
        mDataSaverBackend.addListener(this);

        final boolean usbAvailable = mUsbRegexs.length != 0;
        final boolean bluetoothAvailable = mBluetoothRegexs.length != 0;

        if (!usbAvailable || Utils.isMonkeyRunning() && !Utils.WCN_DISABLED) {
            getPreferenceScreen().removePreference(mUsbTether);
        /* SPRD:Add for bug692657 androido porting pc net share */
            getPreferenceScreen().removePreference(mUsbPcShare);
        }
        if (!SUPPORT_USB_REVERSE_TETHER && mUsbPcShare != null){
            getPreferenceScreen().removePreference(mUsbPcShare);
        }
        /* Bug692657 end @ } */
        if (!Utils.WCN_DISABLED) {
            mWifiTetherPreferenceController.displayPreference(getPreferenceScreen());
        } else {
            getPreferenceScreen().removePreference(mWifiTether);
        }

        if (!bluetoothAvailable || Utils.WCN_DISABLED) {
            getPreferenceScreen().removePreference(mBluetoothTether);
        } else {
            BluetoothPan pan = mBluetoothPan.get();
            if (pan != null && pan.isTetheringOn()) {
                mBluetoothTether.setChecked(true);
            } else {
                mBluetoothTether.setChecked(false);
            }
        }
        // Set initial state based on Data Saver mode.
        onDataSaverChanged(mDataSaverBackend.isDataSaverEnabled());
    }

    @Override
    public void onDestroy() {
        mDataSaverBackend.remListener(this);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothProfile profile = mBluetoothPan.getAndSet(null);
        if (profile != null && adapter != null) {
            adapter.closeProfileProxy(BluetoothProfile.PAN, profile);
        }

        super.onDestroy();
    }

    @Override
    public void onDataSaverChanged(boolean isDataSaving) {
        mDataSaverEnabled = isDataSaving;
        /* SPRD:Add for bug692657 androido porting pc net share @{ */
        mUsbTether.setEnabled(!mDataSaverEnabled && mUsbConnected && !mUsbPcShare.isChecked());
        if (mDataSaverEnabled) {
            mTetherChoice = TETHERING_INVALID;
            updateState();
        }
        /* Bug692657 end @} */
        mBluetoothTether.setEnabled(!mDataSaverEnabled);
        mDataSaverFooter.setVisible(mDataSaverEnabled);
    }

    @Override
    public void onWhitelistStatusChanged(int uid, boolean isWhitelisted) {
    }

    @Override
    public void onBlacklistStatusChanged(int uid, boolean isBlacklisted)  {
    }

    private class TetherChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.ACTION_TETHER_STATE_CHANGED)) {
                // TODO - this should understand the interface types
                ArrayList<String> available = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ERRORED_TETHER);
                updateState(available.toArray(new String[available.size()]),
                        active.toArray(new String[active.size()]),
                        errored.toArray(new String[errored.size()]));
            } else if (action.equals(UsbManager.ACTION_USB_STATE)) {
                mUsbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
                /* SPRD:Add for bug692657 androido porting pc net share @{ */
                boolean rndisEnabled = intent.getBooleanExtra(UsbManager.USB_FUNCTION_RNDIS, false);
                if (mUsbConnected == false  || (mUsbConnected && !rndisEnabled) && mTetherChoice == TETHERING_USB) {
                    mTetherChoice = TETHERING_INVALID;
                }
                /* Bug692657 end @} */
                updateState();
            } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                  // SPRD: Modify for Bug#875145. Get PAN proxy just when Bluetooth is ON. START->
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON && mBluetoothPan.get() == null) {
                    Log.d(TAG, "recevie ACTION_STATE_CHANGED, getProfieProxy PAN");
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    if (adapter != null)  {
                        adapter.getProfileProxy(content.getApplicationContext(), mProfileServiceListener,
                                  BluetoothProfile.PAN);
                    }
                }
                // <- END
                if (mBluetoothEnableForTether) {
                    switch (state) {
                        case BluetoothAdapter.STATE_ON:
                            startTethering(TETHERING_BLUETOOTH);
                            mBluetoothEnableForTether = false;
                            break;

                        case BluetoothAdapter.STATE_OFF:
                        case BluetoothAdapter.ERROR:
                            mBluetoothEnableForTether = false;
                            break;

                        default:
                            // ignore transition states
                    }
                }
                /* SPRD:Add for bug692657 androido porting pc net share @{ */
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_OFF &&
                        mTetherChoice == TETHERING_BLUETOOTH) {
                    mTetherChoice = TETHERING_INVALID;
                }
                /* Bug692657 end @} */
                updateState();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mUnavailable) {
            if (!isUiRestrictedByOnlyAdmin()) {
                getEmptyTextView().setText(R.string.tethering_settings_not_available);
            }
            getPreferenceScreen().removeAll();
            return;
        }

        final Activity activity = getActivity();

        mStartTetheringCallback = new OnStartTetheringCallback(this);

        mTetherChangeReceiver = new TetherChangeReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        Intent intent = activity.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_STATE);
        activity.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        activity.registerReceiver(mTetherChangeReceiver, filter);

        if (intent != null) mTetherChangeReceiver.onReceive(activity, intent);

        updateState();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mUnavailable) {
            return;
        }
        getActivity().unregisterReceiver(mTetherChangeReceiver);
        mTetherChangeReceiver = null;
        mStartTetheringCallback = null;
    }

    /* SPRD:Add for bug692657 androido porting pc net share @} @{ */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult requestCode = " + requestCode + ", resultCode = " + resultCode);
        if (requestCode == PROVISION_REQUEST_USB_PC_TETHER) {
            if (resultCode == Activity.RESULT_OK) {
                setInternetShare(true, data.getStringExtra("usb_rndis_ip_address"));
            }
        }
    }
    /* Bug692657 end @} */

    private void updateState() {
        String[] available = mCm.getTetherableIfaces();
        String[] tethered = mCm.getTetheredIfaces();
        String[] errored = mCm.getTetheringErroredIfaces();
        updateState(available, tethered, errored);
    }

    private void updateState(String[] available, String[] tethered,
            String[] errored) {
        updateUsbState(available, tethered, errored);
        updateBluetoothState();
    }

    private void updateUsbState(String[] available, String[] tethered,
            String[] errored) {
        boolean usbAvailable = mUsbConnected;
        int usbError = ConnectivityManager.TETHER_ERROR_NO_ERROR;
        for (String s : available) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) {
                    if (usbError == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                        usbError = mCm.getLastTetherError(s);
                    }
                }
            }
        }
        boolean usbTethered = false;
        for (String s : tethered) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) usbTethered = true;
            }
        }
        boolean usbErrored = false;
        for (String s: errored) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) usbErrored = true;
            }
        }
        Log.d(TAG, "updateUsbState: " + "usbTethered:" + usbTethered + " usbAvailable: " + usbAvailable + " mCm.getPCNetTether(): " + mCm.getPCNetTether());
        /* SPRD:Add for bug692657 androido porting pc net share @} */
        if (mCm.getPCNetTether()) {
            mUsbTether.setEnabled(false);
            mUsbPcShare.setEnabled(true);
            mUsbPcShare.setChecked(true);
        } else if (usbTethered) {
            mUsbTether.setEnabled(!mDataSaverEnabled);
            mUsbTether.setChecked(true);
            mUsbPcShare.setEnabled(false);
            mUsbPcShare.setChecked(false);
        } else if (usbAvailable) {
            mUsbTether.setEnabled(!mDataSaverEnabled);
            mUsbTether.setChecked(false);
            mUsbPcShare.setEnabled(mTetherChoice!=TETHERING_USB);
            mUsbPcShare.setChecked(false);
        } else {
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
            mUsbPcShare.setEnabled(false);
            mUsbPcShare.setChecked(false);
        }
        /* Bug692657 end @} */
    }

    private void updateBluetoothState() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return;
        }
        int btState = adapter.getState();
        if (btState == BluetoothAdapter.STATE_TURNING_OFF) {
            mBluetoothTether.setEnabled(false);
        } else if (btState == BluetoothAdapter.STATE_TURNING_ON) {
            mBluetoothTether.setEnabled(false);
        } else {
            BluetoothPan bluetoothPan = mBluetoothPan.get();
            mBluetoothTether.setSummary(R.string.bluetooth_tethering_subtext);
            if (btState == BluetoothAdapter.STATE_ON && bluetoothPan != null
                    && bluetoothPan.isTetheringOn()) {
                mBluetoothTether.setChecked(true);
                mBluetoothTether.setEnabled(!mDataSaverEnabled);
            } else {
                mBluetoothTether.setEnabled(!mDataSaverEnabled);
                mBluetoothTether.setChecked(false);
            }
        }
    }

    public static boolean isProvisioningNeededButUnavailable(Context context) {
        return (TetherUtil.isProvisioningNeeded(context)
                && !isIntentAvailable(context));
    }

    private static boolean isIntentAvailable(Context context) {
        String[] provisionApp = context.getResources().getStringArray(
                com.android.internal.R.array.config_mobile_hotspot_provision_app);
        if (provisionApp.length < 2) {
            return false;
        }
        final PackageManager packageManager = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(provisionApp[0], provisionApp[1]);

        return (packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY).size() > 0);
    }

    private void startTethering(int choice) {
        /* SPRD:Add for bug692657 androido porting pc net share @} */
        if (choice == TETHERING_WIFI || choice == TETHERING_BLUETOOTH) {
            mTetherChoice =choice;
        }
        /* Bug692657 end @} */
        if (choice == TETHERING_BLUETOOTH) {
            // Turn on Bluetooth first.
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter.getState() == BluetoothAdapter.STATE_OFF) {
                mBluetoothEnableForTether = true;
                adapter.enable();
                mBluetoothTether.setEnabled(false);
                return;
            }
        }

        mCm.startTethering(choice, true, mStartTetheringCallback, mHandler);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mUsbTether) {
            if (mUsbTether.isChecked()) {
                mUsbPcShare.setEnabled(false);
                mTetherChoice = TETHERING_USB;
                startTethering(TETHERING_USB);
            } else {
                mTetherChoice = TETHERING_INVALID;
                mCm.stopTethering(TETHERING_USB);
            }
        } else if (preference == mBluetoothTether) {
            if (mBluetoothTether.isChecked()) {
                startTethering(TETHERING_BLUETOOTH);
            } else {
                mCm.stopTethering(TETHERING_BLUETOOTH);
                // No ACTION_TETHER_STATE_CHANGED is fired or bluetooth unless a device is
                // connected. Need to update state manually.
                updateState();
            }
        /* SPRD:Add for bug692657 androido porting pc net share @} */
        } else if (preference == mUsbPcShare) {
            if (!mUsbPcShare.isChecked()) {
                setInternetShare(false, null);
            } else {
                mUsbPcShare.setEnabled(false);
                Intent chooseOSIntent = new Intent(getActivity(),SprdChooseOS.class);
                startActivityForResult(chooseOSIntent, PROVISION_REQUEST_USB_PC_TETHER);
            }
         }   /* Bug692657 end@} */

        return super.onPreferenceTreeClick(preference);
    }

    /* SPRD:Add for bug692657 androido porting pc net share @{ */
    private void setInternetShare(boolean isShared, String ip) {
        int result = ConnectivityManager.TETHER_ERROR_NO_ERROR;
        Log.d(TAG, "setInternetShare(" + isShared +"," + " " + ip + ")");
        if(isShared) {
            mWifiManager.setWifiEnabled(false);
            result = mCm.enableTetherPCInternet(ip);
        } else {
            result = mCm.disableTetherPCInternet();
        }
    }
    /* Bug692657 end @} */

    @Override
    public int getHelpResource() {
        return R.string.help_url_tether;
    }

    private BluetoothProfile.ServiceListener mProfileServiceListener =
            new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mBluetoothPan.set((BluetoothPan) proxy);
        }
        public void onServiceDisconnected(int profile) {
            mBluetoothPan.set(null);
        }
    };

    private static final class OnStartTetheringCallback extends
            ConnectivityManager.OnStartTetheringCallback {
        final WeakReference<TetherSettings> mTetherSettings;

        OnStartTetheringCallback(TetherSettings settings) {
            mTetherSettings = new WeakReference<>(settings);
        }

        @Override
        public void onTetheringStarted() {
            update();
        }

        @Override
        public void onTetheringFailed() {
            /* SPRD:Add for bug692657 androido porting pc net share @} */
            TetherSettings settings = mTetherSettings.get();
            if(TETHERING_USB == settings.mTetherChoice){
                settings.mTetherChoice = TETHERING_INVALID;
            }
            /* Bug692657 end @} */
            update();
        }

        private void update() {
            TetherSettings settings = mTetherSettings.get();
            if (settings != null) {
                settings.updateState();
            }
        }
    }
}
