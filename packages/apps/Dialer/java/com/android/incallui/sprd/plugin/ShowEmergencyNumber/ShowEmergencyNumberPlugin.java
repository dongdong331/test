package com.android.incallui.sprd.plugin.ShowEmergencyNumber;

import android.content.Context;
import com.android.incallui.Log;
import com.android.incallui.sprd.plugin.ShowEmergencyNumber.ShowEmergencyNumberHelper;

/**
 * Show emergency number when dial emergency call feature
 */
public class ShowEmergencyNumberPlugin extends ShowEmergencyNumberHelper{
    private Context mContext;
    private static final String TAG = "ShowEmergencyNumberPlugin";

    private String mEmergencyNumber = ""; // Show emergency number when dial emergency call

    public ShowEmergencyNumberPlugin() {
    }

    /* Show emergency number when dial emergency call feature. @{ */
    public void setEmergencyNumber(String number) {
        mEmergencyNumber = number;
    }

    public String getEmergencyNumber() {
        Log.i(TAG, "Display emergency number = " + mEmergencyNumber);
        return mEmergencyNumber;
    }
    /* @} */
}
