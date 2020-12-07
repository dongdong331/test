/* Created by spreadst */

package com.sprd.settings.wifi.cmcc;

import java.util.Calendar;

import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiFeaturesUtils;
import android.os.Bundle;
import android.os.UserManager;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceClickListener;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v14.preference.SwitchPreference;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.TimePicker;
import android.widget.Toast;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.RestrictedSettingsFragment;
import com.android.settings.Utils;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

public class CmccAdvancedWifiSettings extends RestrictedSettingsFragment
        implements Preference.OnPreferenceChangeListener, TimePickerDialog.OnTimeSetListener {
    private static final String TAG = "CmccAdvancedWifiSettings";


    private WifiManager mWifiManager;
    private SprdWifiSettingsAddon mAddonStub;
    private boolean supportCMCC = false;

    private static final String KEY_MAC_ADDRESS = "mac_address";
    private static final String KEY_CURRENT_IP_ADDRESS = "current_ip_address";
    private static final String KEY_WIFI_NETMASK = "wifi_netmask";
    private static final String KEY_WIFI_GATEWAY = "wifi_gateway";

    private static final String KEY_MOBILE_TO_WLAN_PREFERENCE_CATEGORY = "mobile_to_wlan_preference_category";
    private static final String KEY_MOBILE_TO_WLAN_POLICY = "mobile_to_wlan_policy";
    private String[] mMoblieToWlanPolicys;
    private static final String KEY_DIALOG_CONNECT_TO_CMCC = "show_dialog_connect_to_cmcc";
    private static final int DEFAULT_CHECKED_VALUE = 1;
    private static final String KEY_RESET_WIFI_POLICY_DIALOG_FLAG = "reset_wifi_policy_dialog_flag";

    private static final String KEY_WIFI_ALARM_CATEGORY = "wifi_alarm_category";
    private static final String KEY_WIFI_CONNECT_ALARM_SWITCH = "wifi_connect_alarm_switch";
    private static final String KEY_WIFI_CONNECT_ALARM_TIME = "wifi_connect_alarm_time";
    private static final String KEY_WIFI_DISCONNECT_ALARM_SWITCH = "wifi_disconnect_alarm_switch";
    private static final String KEY_WIFI_DISCONNECT_ALARM_TIME = "wifi_disconnect_alarm_time";
    private static final int DIALOG_WIFI_CONNECT_TIMEPICKER = 0;
    private static final int DIALOG_WIFI_DISCONNECT_TIMEPICKER = 1;

    private SwitchPreference mConnectSwitch;
    private Preference mConnectTimePref;
    private SwitchPreference mDisconnectSwitch;
    private Preference mDisconnectTimePref;

    AlarmManager mAlarmManager;
    private int whichTimepicker = -1;

    private boolean mUnavailable;

    public CmccAdvancedWifiSettings() {
        super(UserManager.DISALLOW_CONFIG_WIFI);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.WIFI_ADVANCED;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAddonStub = SprdWifiSettingsAddon.getInstance(getActivity());
        supportCMCC = mAddonStub.isSupportCmcc();
        if (isUiRestricted()) {
            mUnavailable = true;
            setPreferenceScreen(new PreferenceScreen(getPrefContext(), null));
        } else {
            addPreferencesFromResource(R.xml.cmcc_wifi_advanced_settings);
        }
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        if (dialogId == DIALOG_WIFI_CONNECT_TIMEPICKER) {
            return MetricsEvent.DIALOG_WIFI_CONNECT_TIMEPICKER;
        } else if (dialogId == DIALOG_WIFI_DISCONNECT_TIMEPICKER) {
            return MetricsEvent.DIALOG_WIFI_DISCONNECT_TIMEPICKER;
        }
        return 0;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getEmptyTextView().setText(R.string.wifi_advanced_not_available);
        if (mUnavailable) {
            getPreferenceScreen().removeAll();
        }
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mAlarmManager = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);
        mAddonStub.initWifiConnectionPolicy();
        mMoblieToWlanPolicys = getResources().getStringArray(R.array.mobile_to_wlan);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mUnavailable) {
            initCellularWLANPreference();
            refreshWifiInfo();
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();
        if (KEY_DIALOG_CONNECT_TO_CMCC.equals(key)) {
            mAddonStub.setConnectCmccDialogFlag(((SwitchPreference) preference).isChecked());
        } else if (KEY_RESET_WIFI_POLICY_DIALOG_FLAG.equals(key)) {
            mAddonStub.resetWifiPolicyDialogFlag();
            Toast.makeText(getActivity(), R.string.reset_wifi_policy_dialog_flag_toast_message, Toast.LENGTH_SHORT).show();
        } else if (KEY_WIFI_CONNECT_ALARM_SWITCH.equals(key)) {
            boolean isChecked = ((SwitchPreference) preference).isChecked();
            Global.putInt(getContentResolver(), WifiFeaturesUtils.WIFI_CONNECT_ALARM_FLAG, isChecked ? 1 : 0);
            if (isChecked) {
                setConnectWifiAlarm();
            } else {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(
                        WifiFeaturesUtils.ALARM_FOR_CONNECT_WIFI_ACTION), 0);
                mAlarmManager.cancel(pendingIntent);
            }
        } else if (KEY_WIFI_CONNECT_ALARM_TIME.equals(key)) {
            removeDialog(DIALOG_WIFI_CONNECT_TIMEPICKER);
            showDialog(DIALOG_WIFI_CONNECT_TIMEPICKER);
        } else if (KEY_WIFI_DISCONNECT_ALARM_SWITCH.equals(key)) {
            boolean isChecked = ((SwitchPreference) preference).isChecked();
            Global.putInt(getContentResolver(), WifiFeaturesUtils.WIFI_DISCONNECT_ALARM_FLAG, isChecked ? 1 : 0);
            if (isChecked) {
                setDisonnectWifiAlarm();
            } else {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(
                        WifiFeaturesUtils.ALARM_FOR_DISCONNECT_WIFI_ACTION), 0);
                mAlarmManager.cancel(pendingIntent);
            }
        } else if (KEY_WIFI_DISCONNECT_ALARM_TIME.equals(key)) {
            removeDialog(DIALOG_WIFI_DISCONNECT_TIMEPICKER);
            showDialog(DIALOG_WIFI_DISCONNECT_TIMEPICKER);
        } else {
            return super.onPreferenceTreeClick(preference);
        }
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Context context = getActivity();
        String key = preference.getKey();
        if (KEY_MOBILE_TO_WLAN_POLICY.equals(key)) {
            mAddonStub.setMobileToWlanPolicy((String) newValue);

            int value = mAddonStub.getMobileToWlanPolicy();
            preference.setSummary(mMoblieToWlanPolicys[value]);
        }
        return true;
    }

    private void refreshWifiInfo() {
        final Context context = getActivity();
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

        Preference wifiMacAddressPref = findPreference(KEY_MAC_ADDRESS);
        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        wifiMacAddressPref.setSummary(!TextUtils.isEmpty(macAddress) ? macAddress
                : context.getString(R.string.status_unavailable));
        wifiMacAddressPref.setSelectable(false);

        Preference wifiIpAddressPref = findPreference(KEY_CURRENT_IP_ADDRESS);
        String ipAddress = Utils.getWifiIpAddresses(context);
        wifiIpAddressPref.setSummary(ipAddress == null ?
                context.getString(R.string.status_unavailable) : ipAddress);
        wifiIpAddressPref.setSelectable(false);

        refreshNetmaskAndGateway();
    }

    private void initCellularWLANPreference() {
        boolean wifiEnabled = mWifiManager.isWifiEnabled();
         ListPreference mMoblieToWlanPolicy = (ListPreference) findPreference(KEY_MOBILE_TO_WLAN_POLICY);
        mMoblieToWlanPolicy.setEnabled(wifiEnabled);
        mMoblieToWlanPolicy.setOnPreferenceChangeListener(this);
        int value = mAddonStub.getMobileToWlanPolicy();
        mMoblieToWlanPolicy.setValue(String.valueOf(value));
        mMoblieToWlanPolicy.setSummary(mMoblieToWlanPolicys[value]);

        SwitchPreference connectToCmccSwitch = (SwitchPreference) findPreference(KEY_DIALOG_CONNECT_TO_CMCC);
        setShowDialogSwitchStatus(connectToCmccSwitch, wifiEnabled);

        mConnectSwitch = (SwitchPreference) findPreference(KEY_WIFI_CONNECT_ALARM_SWITCH);
        mConnectTimePref = (Preference) findPreference(KEY_WIFI_CONNECT_ALARM_TIME);
        boolean isConnectSwitchChecked = Settings.Global.getInt(getContentResolver(), WifiFeaturesUtils.WIFI_CONNECT_ALARM_FLAG, 0) == 1;
        mConnectSwitch.setChecked(isConnectSwitchChecked);

        mDisconnectSwitch = (SwitchPreference) findPreference(KEY_WIFI_DISCONNECT_ALARM_SWITCH);
        mDisconnectTimePref = (Preference) findPreference(KEY_WIFI_DISCONNECT_ALARM_TIME);
        boolean isDisconnectSwitchChecked = Settings.Global.getInt(getContentResolver(), WifiFeaturesUtils.WIFI_DISCONNECT_ALARM_FLAG, 0) == 1;
        mDisconnectSwitch.setChecked(isDisconnectSwitchChecked);

        int hourOfDay = Settings.Global.getInt(getContentResolver(), WifiFeaturesUtils.WIFI_CONNECT_ALARM_HOUR, 0);
        int minute = Settings.Global.getInt(getContentResolver(), WifiFeaturesUtils.WIFI_CONNECT_ALARM_MINUTE, 0);
        Calendar calendar = getCalendar(hourOfDay, minute);
        updateTimeDisplay(mConnectTimePref, calendar);
        hourOfDay = Settings.Global.getInt(getContentResolver(), WifiFeaturesUtils.WIFI_DISCONNECT_ALARM_HOUR, 0);
        minute = Settings.Global.getInt(getContentResolver(), WifiFeaturesUtils.WIFI_DISCONNECT_ALARM_MINUTE, 0);
        calendar = getCalendar(hourOfDay, minute);
        updateTimeDisplay(mDisconnectTimePref, calendar);
    }

    private void setShowDialogSwitchStatus(SwitchPreference item, boolean wifiEnabled) {
        item.setChecked(mAddonStub.showDialogWhenConnectCMCC());
        item.setEnabled(wifiEnabled);
    }

    private void refreshNetmaskAndGateway() {
        Preference wifiNetmaskPref = findPreference(KEY_WIFI_NETMASK);
        Preference wifiGatewayPref = findPreference(KEY_WIFI_GATEWAY);

        wifiNetmaskPref.setSummary(R.string.status_unavailable);
        wifiGatewayPref.setSummary(R.string.status_unavailable);
        wifiNetmaskPref.setSelectable(false);
        wifiGatewayPref.setSelectable(false);

        DhcpInfo dhcpInfo = mWifiManager.getDhcpInfo();
        if(dhcpInfo != null){
            int maskAddress = dhcpInfo.netmask;
            if (maskAddress != 0) {
                wifiNetmaskPref.setSummary(Formatter.formatIpAddress(maskAddress));
            }

            int gatewayAddress = dhcpInfo.gateway;
            if (gatewayAddress != 0) {
                wifiGatewayPref.setSummary(Formatter.formatIpAddress(gatewayAddress));
            }
        }
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        final Calendar calendar = Calendar.getInstance();
        switch (dialogId) {
            case DIALOG_WIFI_CONNECT_TIMEPICKER:
                whichTimepicker = DIALOG_WIFI_CONNECT_TIMEPICKER;
                return new TimePickerDialog(
                        getActivity(),
                        this,
                        //calendar.get(Calendar.HOUR_OF_DAY),
                        //calendar.get(Calendar.MINUTE),
                        Settings.Global.getInt(getContentResolver(),
                                WifiFeaturesUtils.WIFI_CONNECT_ALARM_HOUR, 0),
                        Settings.Global.getInt(getContentResolver(),
                                WifiFeaturesUtils.WIFI_CONNECT_ALARM_MINUTE, 0),
                        DateFormat.is24HourFormat(getActivity()));
            case DIALOG_WIFI_DISCONNECT_TIMEPICKER:
                whichTimepicker = DIALOG_WIFI_DISCONNECT_TIMEPICKER;
                return new TimePickerDialog(
                        getActivity(),
                        this,
                        //calendar.get(Calendar.HOUR_OF_DAY),
                        //calendar.get(Calendar.MINUTE),
                        Settings.Global.getInt(getContentResolver(),
                                WifiFeaturesUtils.WIFI_DISCONNECT_ALARM_HOUR, 0),
                        Settings.Global.getInt(getContentResolver(),
                                WifiFeaturesUtils.WIFI_DISCONNECT_ALARM_MINUTE, 0),
                        DateFormat.is24HourFormat(getActivity()));
            default:
                break;
        }
        return super.onCreateDialog(dialogId);
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        Log.d(TAG, "onTimeSet");

        Calendar calendar = getCalendar(hourOfDay, minute);
        if (whichTimepicker != -1) {
            switch (whichTimepicker) {
                case DIALOG_WIFI_CONNECT_TIMEPICKER:
                    Settings.Global.putInt(getContentResolver(), WifiFeaturesUtils.WIFI_CONNECT_ALARM_HOUR, hourOfDay);
                    Settings.Global.putInt(getContentResolver(), WifiFeaturesUtils.WIFI_CONNECT_ALARM_MINUTE, minute);
                    if (mConnectSwitch.isChecked()) {
                        setConnectWifiAlarm();
                    }
                    updateTimeDisplay(mConnectTimePref, calendar);
                    break;

                case DIALOG_WIFI_DISCONNECT_TIMEPICKER:
                    Settings.Global.putInt(getContentResolver(), WifiFeaturesUtils.WIFI_DISCONNECT_ALARM_HOUR, hourOfDay);
                    Settings.Global.putInt(getContentResolver(), WifiFeaturesUtils.WIFI_DISCONNECT_ALARM_MINUTE, minute);
                    if (mDisconnectSwitch.isChecked()) {
                        setDisonnectWifiAlarm();
                    }
                    updateTimeDisplay(mDisconnectTimePref, calendar);
                    break;
                default:
                    break;
            }
        }
    }

    private Calendar getCalendar(int hourOfDay, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 1);
        return calendar;
    }

    private void setConnectWifiAlarm() {
        Log.d(TAG, "setConnectWifiAlarm");
        int hourOfDay = Settings.Global.getInt(getContentResolver(), WifiFeaturesUtils.WIFI_CONNECT_ALARM_HOUR, 0);
        int minute = Settings.Global.getInt(getContentResolver(), WifiFeaturesUtils.WIFI_CONNECT_ALARM_MINUTE, 0);
        Calendar calendar = getCalendar(hourOfDay, minute);
        long inMillis = calendar.getTimeInMillis();
        if (isDismissCalendar(hourOfDay, minute)) {
            inMillis += WifiFeaturesUtils.INTERVAL_MILLIS;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(
                WifiFeaturesUtils.ALARM_FOR_CONNECT_WIFI_ACTION), 0);
        mAlarmManager.cancel(pendingIntent);
        mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, inMillis, pendingIntent);
    }

    private void setDisonnectWifiAlarm() {
        Log.d(TAG, "setDisonnectWifiAlarm");
        int hourOfDay = Settings.Global.getInt(getContentResolver(), WifiFeaturesUtils.WIFI_DISCONNECT_ALARM_HOUR, 0);
        int minute = Settings.Global.getInt(getContentResolver(), WifiFeaturesUtils.WIFI_DISCONNECT_ALARM_MINUTE, 0);
        Calendar calendar = getCalendar(hourOfDay, minute);
        long inMillis = calendar.getTimeInMillis();
        if (isDismissCalendar(hourOfDay, minute)) {
            inMillis += WifiFeaturesUtils.INTERVAL_MILLIS;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(
                 WifiFeaturesUtils.ALARM_FOR_DISCONNECT_WIFI_ACTION), 0);
        mAlarmManager.cancel(pendingIntent);
        mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, inMillis, pendingIntent);
    }

    private boolean isDismissCalendar(int hourOfDay, int minute) {
        Calendar calendar = Calendar.getInstance();
        if (calendar.get(Calendar.HOUR_OF_DAY) > hourOfDay) {
            return true;
        }else if (calendar.get(Calendar.HOUR_OF_DAY) == hourOfDay) {
            if (calendar.get(Calendar.MINUTE) >= minute) {
                return true;
            }
        }
        return false;
    }

    private void updateTimeDisplay(Preference preference, Calendar calendar) {
        preference.setSummary(DateFormat.getTimeFormat(getActivity()).format(
                calendar.getTime()));
    }
}
