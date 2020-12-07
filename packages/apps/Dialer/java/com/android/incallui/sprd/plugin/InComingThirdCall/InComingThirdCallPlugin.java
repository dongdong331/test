package com.android.incallui.sprd.plugin.InComingThirdCall;

import android.content.Context;
import android.widget.Toast;
import com.android.dialer.R;
import android.app.AlertDialog;

import com.android.incallui.Log;
import com.android.incallui.call.CallList;
import com.android.incallui.sprd.plugin.InComingThirdCall.InComingThirdCallHelper;

/**
 *  SPRD Feature Porting: InComing Third Call Feature
 */
public class InComingThirdCallPlugin extends InComingThirdCallHelper{
    private static final String TAG = "InComingThirdCallPlugin";
    private AlertDialog mHangupcallDialog = null;

    public InComingThirdCallPlugin() {
    }

    public void handleIncomingThirdCall(Context context) {
        if ((CallList.getInstance().getActiveCall() != null)
                && (CallList.getInstance().getBackgroundCall() != null)
                && mHangupcallDialog == null) {
            showHangupCallDialog(context);
        }
    }

    private void showHangupCallDialog(Context context) {
        String note_title = context.getString(R.string.hangupcall_note_title);
        String note_message = context
                .getString(R.string.hangupcall_note_message);
        mHangupcallDialog = new AlertDialog.Builder(context)
                .setTitle(note_title).setMessage(note_message)
                .setPositiveButton(com.android.internal.R.string.ok, null)
                .setCancelable(false).create();
        mHangupcallDialog.show();
    }

    public void dismissHangupCallDialog() {
        if (mHangupcallDialog != null) {
            mHangupcallDialog.dismiss();
            mHangupcallDialog = null;
        }
    }

    public boolean isSupportIncomingThirdCall() {
        return true;
    }
}