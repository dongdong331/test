package com.android.incallui.sprd.plugin.ConferenceNumLimit;

import com.android.incallui.Log;
import com.android.dialer.R;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;

import android.content.Context;

/**
 *  SPRD Feature Porting: Toast information when the number of conference call is over limit for cmcc case
 */
public class ConferenceNumLimitHelper {

    private static final String TAG = "ConferenceNumLimitHelper";
    static ConferenceNumLimitHelper sInstance;

    public static ConferenceNumLimitHelper getInstance(Context context) {
        if (sInstance == null) {
            if(context.getResources().getBoolean(R.bool.config_is_support_conference_number_limit_feature)){
                Log.i(TAG, "new ConferenceNumLimitPlugin()");
                sInstance = new ConferenceNumLimitPlugin();
            } else {
                Log.i(TAG, "new ConferenceNumLimitHelper()");
                sInstance = new ConferenceNumLimitHelper();
            }
            Log.i(TAG, "getInstance [" + sInstance + "]");
        }
        return sInstance;
    }

    public ConferenceNumLimitHelper() {
    }

    /* SPRD Feature Porting: Toast information when the number of conference call is over limit for cmcc case @{ */
    public boolean showMergeButton(DialerCall call) {
        int conferenceCallSize = 0;
        DialerCall conferenceCall = CallList.getInstance().getAllConferenceCall();
        if (conferenceCall != null && conferenceCall.getChildCallIds() != null) {
            conferenceCallSize = conferenceCall.getChildCallIds().size();
        }
        return call.can(android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE)
                && conferenceCallSize < 5;
    }

    public void showToast(Context context) {
    }

    public boolean isSupportClickMergeButton() {
        return true;
    }
    /* @} */
}
