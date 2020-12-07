package com.android.incallui.sprd.plugin.displayfdn;

import android.content.Context;

import com.android.dialer.app.R;
import com.android.incallui.Log;
import com.android.incallui.incall.impl.InCallFragment;
import com.android.incallui.incall.protocol.PrimaryInfo;

public class DisplayFdnHelper {
    private static String TAG = "DisplayFdnHelper";
    static DisplayFdnHelper sInstance;

    public DisplayFdnHelper() {

    }

    public static DisplayFdnHelper getInstance(Context context) {
        if (sInstance == null) {
            if (context.getResources().getBoolean(R.bool.config_is_support_fdnname_feature)) {
                sInstance = new DisplayFdnPlugin(context);
            } else {
                sInstance = new DisplayFdnHelper();
            }
        }
        Log.d(TAG, "getInstance [" + sInstance + "]");
        return sInstance;
    }

    /* SPRD Feature Porting: Show fdn list name in incallui feature. @{ */
    public boolean isSupportFdnListName(int subId) {
        return false;
    }

    public void setFDNListName(PrimaryInfo primaryInfo, InCallFragment incallFragment) {

    }
    /* @} */

    /* UNISOC: add for bug965735 @{ */
    public String getFDNListName(String number, int subId) {
        return "";

    }
    /* @} */
}
