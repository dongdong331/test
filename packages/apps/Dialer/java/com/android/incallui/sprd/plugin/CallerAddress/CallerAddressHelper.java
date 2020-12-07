package com.android.incallui.sprd.plugin.CallerAddress;

import com.android.incallui.Log;
import com.android.dialer.R;

import android.content.Context;

/**
 * Display caller address for phone number feature.
 */
public class CallerAddressHelper {
    private static final String TAG = "CallerAddressHelper";
    static CallerAddressHelper sInstance;

    public static CallerAddressHelper getsInstance(Context context) {
        if (sInstance == null) {
            if (context.getResources().getBoolean(R.bool.config_is_support_display_caller_address)) {
                sInstance = new CallerAddressPlugin(context);
            } else {
                sInstance = new CallerAddressHelper();
            }
        }

        Log.i(TAG, "getInstance [" + sInstance + "]");
        return sInstance;
    }

    public CallerAddressHelper() {
    }

    public boolean isSupportCallerAddress() {
        return false;
    }

    public String getCallerAddress(Context context, String number) {
        return "";
    }
}