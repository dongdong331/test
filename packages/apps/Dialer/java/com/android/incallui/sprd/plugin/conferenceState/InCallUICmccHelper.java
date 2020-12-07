package com.android.incallui.sprd.plugin.conferenceState;

import com.android.incallui.Log;
import com.android.dialer.R;

import android.content.Context;

/**
 * This class is used to manager InCallUI CMCC Plugin Helper.
 */
public class InCallUICmccHelper {


    private static final String TAG = "InCallUICmccHelper";
    static InCallUICmccHelper sInstance;

    public static InCallUICmccHelper getInstance(Context context) {
        if (sInstance == null) {
            if(context.getResources().getBoolean(R.bool.config_is_show_conference_state_feature)){
                sInstance = new InCallUICmccPlugin();
            } else {
                sInstance = new InCallUICmccHelper();
            }            
            Log.i(TAG, "getInstance [" + sInstance + "]");
        }
        return sInstance;
    }

    public InCallUICmccHelper() {
    }

    /* SPRD: add for bug692155 */
    public boolean shouldShowParticipantState () {
        return false;
    }

    public boolean isOnlyDislpayActiveOrHoldCall () {
        return false;
    }
    /* @} */
}

