package com.android.incallui.sprd.plugin.ShowEmergencyNumber;

import com.android.incallui.Log;
import com.android.dialer.R;

import android.content.Context;

/**
 * Show emergency number when dial emergency call feature
 */
public class ShowEmergencyNumberHelper {


    private static final String TAG = "ShowEmergencyNumberHelper";
    static ShowEmergencyNumberHelper sInstance;

    public static ShowEmergencyNumberHelper getInstance(Context context) {
        if (sInstance == null) {
            if(context.getResources().getBoolean(R.bool.config_is_show_emergency_number_feature)){
                Log.i(TAG, "new ShowEmergencyNumberPlugin()");
                sInstance = new ShowEmergencyNumberPlugin();
            } else {
                Log.i(TAG, "new ShowEmergencyNumberHelper()");
                sInstance = new ShowEmergencyNumberHelper();
            }
            Log.i(TAG, "getInstance [" + sInstance + "]");
        }
        return sInstance;
    }

    public ShowEmergencyNumberHelper() {
    }


    /* Show emergency number when dial emergency call feature. @{ */
    public void setEmergencyNumber(String number) {
        // do nothing
    }

    public String getEmergencyNumber() {
        return null;
    }
    /* @} */
}
