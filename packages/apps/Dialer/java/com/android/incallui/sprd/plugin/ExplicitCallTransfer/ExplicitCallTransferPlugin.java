package com.android.incallui.sprd.plugin.ExplicitCallTransfer;

import android.content.Context;
import android.util.Log;

import com.android.incallui.call.CallList;
import com.android.sprd.telephony.RadioInteractor;
import com.android.incallui.sprd.InCallUiUtils;

/**
 * Various utilities for dealing with phone number strings.
 */
public class ExplicitCallTransferPlugin extends ExplicitCallTransferHelper {

    private Context addonContext;
    private static final String TAG = "[ExplicitCallTransferPlugin]";

    public ExplicitCallTransferPlugin() {
    }

    public ExplicitCallTransferPlugin(Context context) {
        addonContext = context;
    }

    public void explicitCallTransfer(Context context){
        RadioInteractor radioInteractor = new RadioInteractor(context);
        radioInteractor.explicitCallTransfer(InCallUiUtils.getCurrentPhoneId(context));
    }

    public boolean shouldEnableTransferButton() {
        // According to 3GPP TS23.091, only when background call is HOLDING and foreground call
        // is DIALING, ALERTING, or ACTIVE, transfer button will display.
        CallList calllist = CallList.getInstance();
        return calllist != null
                && calllist.getBackgroundCall() != null // HOLDING
                && calllist.getOutgoingOrActive() != null; // DIALING/ALERTING/ACTIVE
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
