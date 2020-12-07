package com.android.incallui.sprd.plugin.SendSms;

import android.content.Context;

import com.android.dialer.R;
import com.android.incallui.Log;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.sprd.plugin.SendSms.SendSmsPlugin;

/**
 * Support send sms in incallui feature..
 */
public class SendSmsHelper {

    private static final String TAG = "SendSmsHelper";
    static SendSmsHelper sInstance;

    public static SendSmsHelper getInstance(Context context) {
        if (sInstance == null) {
            if(context.getResources().getBoolean(R.bool.config_is_send_sms_enabled_feature)){
                 sInstance = new SendSmsPlugin();
            } else {
                 sInstance = new SendSmsHelper();
            }
            Log.i(TAG, "getInstance [" + sInstance + "]");
        }
        return sInstance;
    }

    public SendSmsHelper() {
    }

    /* Enable send sms in incallui. @{ */
    public boolean isSupportSendSms() {
        return false;
    }

    public void sendSms(Context context, DialerCall call, CallList callList) {
        // Do nothing
    }
    /* @} */
}
