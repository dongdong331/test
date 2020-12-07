package com.android.incallui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.telecom.VideoProfile;

import com.android.dialer.common.LogUtil;
import com.android.incallui.call.DialerCall;

public class LowBatteryAnswerDialogFragment extends DialogFragment
{
    private static final String TAG = "LowBatteryAnswerDialogFragment";
    private Context mContext;
    private DialerCall mCall;
    private AlertDialog mLowBatteryAlertDialog;
    private boolean mIsVideoUpgradeRequest;

    public LowBatteryAnswerDialogFragment(){
    }

    public static LowBatteryAnswerDialogFragment getInstance(Context context, DialerCall call,boolean isVideoUpgradeRequest){
        LowBatteryAnswerDialogFragment lowBatteryDialogFragment = new LowBatteryAnswerDialogFragment();
        lowBatteryDialogFragment.mContext = context;
        lowBatteryDialogFragment.mCall = call;
        lowBatteryDialogFragment.mIsVideoUpgradeRequest = isVideoUpgradeRequest;
        return lowBatteryDialogFragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        mLowBatteryAlertDialog = buildDialog(mContext,mCall);
        mLowBatteryAlertDialog.show();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        if (mLowBatteryAlertDialog == null) {
            mLowBatteryAlertDialog = buildDialog(mContext,mCall);
        }
        return mLowBatteryAlertDialog;
    }

    public AlertDialog buildDialog(final Context context , final DialerCall call) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.low_battery_warning_title));
        builder.setMessage(context.getString(mIsVideoUpgradeRequest ? R.string.low_battery_warning_media_alert_message : R.string.low_battery_warning_message));
        builder.setPositiveButton(context.getString(mIsVideoUpgradeRequest ? R.string.remote_request_change_accept : R.string.low_battery_continue_video_call),
                new android.content.DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        LogUtil.i(TAG, "onClick->Video call: " + call+ " mIsVideoUpgradeRequest = "+mIsVideoUpgradeRequest);
                        if (call != null) {
                            if (mIsVideoUpgradeRequest) {
                                call.getVideoTech().acceptVideoRequest(context);
                            } else {
                                call.answer();
                            }
                            if (dialog != null) {
                                dialog.dismiss();
                            }
                        }
                    }
                });
        builder.setNegativeButton(context.getString(mIsVideoUpgradeRequest ? R.string.remote_request_change_reject : R.string.low_battery_convert_to_voice_call),
                new android.content.DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        LogUtil.i(TAG, "onClick->Voice call:" + call + " mIsVideoUpgradeRequest = "+mIsVideoUpgradeRequest);
                        if (call != null) {
                            if (mIsVideoUpgradeRequest) {
                                call.getVideoTech().acceptVideoRequestAsAudio();
                            } else {
                                call.answer(VideoProfile.STATE_AUDIO_ONLY);
                            }
                        }
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                    }
                });
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        return dialog;
    }
}