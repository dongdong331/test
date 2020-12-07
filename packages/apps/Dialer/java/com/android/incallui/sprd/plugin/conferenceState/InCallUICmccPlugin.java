package com.android.incallui.sprd.plugin.conferenceState;

import android.content.Context;
import com.android.incallui.Log;
import com.android.incallui.sprd.plugin.conferenceState.InCallUICmccHelper;

/**
 * This class is used to manager InCallUI CMCC Plugin
 */
public class InCallUICmccPlugin extends InCallUICmccHelper{
    private Context mContext;
    private static final String TAG = "InCallUICmccPlugin";

    public InCallUICmccPlugin() {
    }

    /* SPRD: add for bug692155 */
    public boolean shouldShowParticipantState () {
        return true;
    }

    public boolean isOnlyDislpayActiveOrHoldCall () {
        return true;
    }
    /* @} */
}
