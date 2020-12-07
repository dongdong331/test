package com.android.incallui.sprd.plugin.InComingThirdCall;

import com.android.incallui.Log;
import com.android.dialer.R;

import android.content.Context;

/**
 *  SPRD Feature Porting: Toast information when the number of conference call is over limit for cmcc case
 */
public class InComingThirdCallHelper {

    private static final String TAG = "InComingThirdCallHelper";
    static InComingThirdCallHelper sInstance;

    public static InComingThirdCallHelper getInstance(Context context) {
        if (sInstance == null) {
            if(context.getResources().getBoolean(R.bool.config_is_support_incoming_third_call_feature)){
                Log.i(TAG, "new InComingThirdCallPlugin()");
                sInstance = new InComingThirdCallPlugin();
            } else {
                Log.i(TAG, "new InComingThirdCallHelper()");
                sInstance = new InComingThirdCallHelper();
            }
            Log.i(TAG, "getInstance [" + sInstance + "]");
        }
        return sInstance;
    }

    public InComingThirdCallHelper() {
    }

    public void dismissHangupCallDialog() {
        // Do nothing
    }

    public void handleIncomingThirdCall(Context context) {
        // Do nothing
    }

    public boolean isSupportIncomingThirdCall() {
        return false;
    }
}