package com.android.incallui.sprd.plugin.shakePhoneToStartRecording;

import android.content.Context;

import com.android.dialer.R;
import com.android.incallui.Log;

/**
 * Add for shaking phone to start recording.
 */
public class ShakePhoneToStartRecordingHelper {

    private static final String TAG = "ShakePhoneToStartRecordingHelper";
    static ShakePhoneToStartRecordingHelper sInstance;

    public ShakePhoneToStartRecordingHelper() {
    }

    public static ShakePhoneToStartRecordingHelper getInstance(Context context) {
        if (sInstance == null) {
            if(context.getResources().getBoolean(R.bool.config_is_support_shaking_phone_startrecord_feature)){
                Log.i(TAG, "new ShakePhoneToStartRecordingPlugin()");
                sInstance = new ShakePhoneToStartRecordingPlugin();
            } else {
                Log.i(TAG, "new ShakePhoneToStartRecordingHelper()");
                sInstance = new ShakePhoneToStartRecordingHelper();
            }
            Log.i(TAG, "getInstance [" + sInstance + "]");
        }
        return sInstance;
    }

    public void init(Context context ) {
    }

    public void unRegisterTriggerRecorderListener() {
    }
}
