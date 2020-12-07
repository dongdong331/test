package com.android.incallui.sprd.plugin.ConferenceNumLimit;

import android.content.Context;
import android.widget.Toast;
import com.android.dialer.R;
import com.android.incallui.Log;
import com.android.incallui.InCallPresenter;
import com.android.incallui.call.DialerCall;
import com.android.incallui.sprd.plugin.ConferenceNumLimit.ConferenceNumLimitHelper;

/**
 *  SPRD Feature Porting: Toast information when the number of conference call is over limit for cmcc case
 */
public class ConferenceNumLimitPlugin extends ConferenceNumLimitHelper{
    private static final String TAG = "ConferenceNumLimitPlugin";
    private static final int MAX_CONFERENCE_NUMS = 5;

    private int mConferenceSize;
    private DialerCall mBackgroundCall;

    public ConferenceNumLimitPlugin() {
    }

    /* SPRD: Toast information when the number of conference call is over limit for cmcc case @{ */
    public boolean showMergeButton(DialerCall call) {
        if (call.getChildCallIds() == null || InCallPresenter.getInstance().getCallList() == null) {
            log("showMergeButton null error");
            return false;
        }
        mBackgroundCall = InCallPresenter.getInstance().getCallList().getBackgroundCall();

        if (call.isConferenceCall()) {
            mConferenceSize = call.getChildCallIds().size();
            log("foreground conference call size == " + mConferenceSize);
        } else if (mBackgroundCall != null) {
            mConferenceSize = mBackgroundCall.getChildCallIds().size();
            log("background conference call size == " + mConferenceSize);
        } else {
            mConferenceSize = 0;
        }

        return mConferenceSize < MAX_CONFERENCE_NUMS ? call.can(
                android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE) :
                mBackgroundCall != null;
    }

    public void showToast(Context context) {
        if (mConferenceSize == MAX_CONFERENCE_NUMS) {
            Toast.makeText(context, context.getString(R.string.exceed_limit),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public boolean isSupportClickMergeButton() {
        return !(mConferenceSize == MAX_CONFERENCE_NUMS);
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
    /* @} */
}
