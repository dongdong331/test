package com.android.incallui.sprd.plugin.voiceclearcode;

import android.content.Context;

import com.android.dialer.app.R;
import com.android.incallui.Log;

/**
 * Support voice clear code in incallui.
 */
public class VoiceClearCodeHelper {

    private static String TAG = "VoiceClearCodeHelper";
    static VoiceClearCodeHelper sInstance;

    public VoiceClearCodeHelper() {

    }

    public static VoiceClearCodeHelper getInstance(Context context) {
        if (sInstance == null) {
            if (context.getResources().getBoolean(R.bool.config_is_support_voiceclearcode_feature)) {
                sInstance = new VoiceClearCodePlugin(context);
            } else {
                sInstance = new VoiceClearCodeHelper();
            }
        }
        Log.d(TAG, "getInstance [" + sInstance + "]");
        return sInstance;
    }

    public void showToastMessage(Context context, String reason) {
        // Do nothing
    }

    public boolean isVoiceClearCodeLabel(String callStateLabel) {
        return false;
    }

    public boolean isSpecialVoiceClearCode(String number) {
        return false;
    }
}
