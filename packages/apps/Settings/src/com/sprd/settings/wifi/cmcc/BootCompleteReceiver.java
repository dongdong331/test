package com.sprd.settings.wifi.cmcc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiFeaturesUtils;
import android.util.Log;

public class BootCompleteReceiver extends BroadcastReceiver {
    private static final String TAG = "WifiSettingsAddon_BootCompleteReceiver";
    private WifiFeaturesUtils mWifiFeaturesUtils;
    @Override
    public void onReceive(Context context, Intent intent) {
        if (mWifiFeaturesUtils == null) mWifiFeaturesUtils = WifiFeaturesUtils.getInstance(context);
        if (!mWifiFeaturesUtils.isSupportCMCC()) return;

        String action = intent.getAction();
        Log.d(TAG, "Receive action : " + action);
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Intent i = new Intent(context.getApplicationContext(), WifiSettingsService.class);
            context.getApplicationContext().startService(i);
        }
    }
}

