package com.sprd.settings.wifi.cmcc;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiFeaturesUtils;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

public class WifiSettingsService extends Service {
    private static boolean registered = false;

    private final IWifiSettings.Stub mBinder = new IWifiSettings.Stub(){
        public void resetTimer() {
            WifiConnectionPolicy.resetTimer();
        }
        public void setManulConnectFlags(boolean enabled) {
            WifiConnectionPolicy.setManulConnectFlags(enabled);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("WifiSettingsService", "WifiSettingsService onStartCommand");
        if (!registered) {
            IntentFilter mFilter = new IntentFilter();
            mFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            mFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            mFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
            mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            mFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
            mFilter.addAction(WifiFeaturesUtils.WIFI_SCAN_RESULTS_AVAILABLE_ACTION);
            mFilter.addAction(WifiFeaturesUtils.WIFI_SCAN_RESULT_BSS_REMOVED_ACTION);
            mFilter.addAction(WifiFeaturesUtils.WIFI_CONNECTED_AP_ABSENT_ACTION);
            mFilter.addAction(WifiFeaturesUtils.WIFI_DISABLED_WHEN_CONNECTED_ACTION);
            mFilter.addAction(WifiFeaturesUtils.ALARM_FOR_CONNECT_WIFI_ACTION);
            mFilter.addAction(WifiFeaturesUtils.ALARM_FOR_DISCONNECT_WIFI_ACTION);
            getApplicationContext().registerReceiver(new WifiConnectionPolicy(), mFilter);
            registered = true;
        }
        startForeground(0, new Notification());
        flags = START_STICKY;
        return super.onStartCommand(intent, flags, startId);
    }

}
