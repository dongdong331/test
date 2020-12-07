package com.android.incallui.sprd.plugin.ExplicitCallTransfer;

import android.content.Context;
import android.util.Log;
import android.widget.TextView;

import com.android.dialer.R;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.CallList;

/**
 * This class is used to manager InCallUI CMCC Plugin Helper.
 */
public class ExplicitCallTransferHelper {
    private static final String TAG = "ExplicitCallTransferHelper";
    static ExplicitCallTransferHelper sInstance;

    public static ExplicitCallTransferHelper getInstance(Context context) {
        log("getInstance()...");
        if (sInstance == null) {
            if (context.getResources().getBoolean(R.bool.config_is_support_ect_feature)) {
                sInstance = new ExplicitCallTransferPlugin(context);
            } else {
                sInstance = new ExplicitCallTransferHelper();
            }
            log("getInstance [" + sInstance + "]");
        }
        return sInstance;
    }

    public boolean isSupportEctFeature(Context context) {
      return context.getResources().getBoolean(R.bool.config_is_support_ect_feature);
    }

    public ExplicitCallTransferHelper() {
    }

    public void explicitCallTransfer(Context context){
        // do nothing
    }

    public boolean shouldEnableTransferButton() {
        return false;
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
