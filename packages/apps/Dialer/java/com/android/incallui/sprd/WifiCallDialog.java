
package com.android.incallui.sprd;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import com.android.dialer.app.R;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

public class WifiCallDialog extends AlertActivity {

    private final int NOTIFICATION_DIALOG_CLOSE_TIMEOUT = 10000;
    private final int EVENT_NOTIFICATION_DIALOG_CLOSE = 0;
    private final String DIALOG_TYPE_KEY = "dialog_type_key";

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_NOTIFICATION_DIALOG_CLOSE:
                    finish();
                    break;
            }
        }
    };

    private void launchPickWifiNetworkActivity() {
        Intent intent = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        startActivity(intent);
    }

    private void createCallHaveDroppedDialog() {
        //mAlertParams.mIcon = mResources.getDrawable(R.drawable.ims_ready_icon);
        mAlertParams.mTitle = getString(R.string.wfc_have_dropped_dialog_title);
        mAlertParams.mMessage = getString(R.string.wfc_have_dropped_dialog_msg);
        mAlertParams.mPositiveButtonText = getString(R.string.wfc_have_dropped_dialog_left_btn);
        mAlertParams.mNegativeButtonText = getString(R.string.wfc_have_dropped_dialog_right_btn);

        mAlertParams.mPositiveButtonListener = new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog, int which) {
                launchPickWifiNetworkActivity();
            }
        };
    }

    private void createCallMayDropDialog() {
        //mAlertParams.mIcon = mResources.getDrawable(android.R.drawable.ic_dialog_alert);
        mAlertParams.mTitle = getString(R.string.wfc_may_drop_dialog_title);
        mAlertParams.mMessage = getString(R.string.wfc_may_drop_dialog_msg);
        mAlertParams.mPositiveButtonText = getString(R.string.wfc_may_drop_dialog_left_btn);
        mHandler.sendEmptyMessageDelayed(EVENT_NOTIFICATION_DIALOG_CLOSE, NOTIFICATION_DIALOG_CLOSE_TIMEOUT);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        int type = intent.getIntExtra(DIALOG_TYPE_KEY, -1);
        if(type == 0){
            createCallMayDropDialog();
        }else if(type == 1){
            createCallHaveDroppedDialog();
        }
        setupAlert();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mHandler.removeMessages(EVENT_NOTIFICATION_DIALOG_CLOSE);
    }
}
