package com.android.incallui.sprd.plugin.hdaudio;

import com.android.dialer.R;
import com.android.incallui.call.DialerCall;
import com.android.incallui.contactgrid.BottomRow;
import com.android.incallui.contactgrid.BottomRow.Info;

import android.telecom.Call.Details;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;

public class InCallUIHdAudioHelper {

    private static final String TAG = "[InCallUIHdAudioHelper]";
    static InCallUIHdAudioHelper sInstance;

    public static InCallUIHdAudioHelper getInstance(Context context) {
        log("getInstance()");
        if (sInstance == null) {
            if (context.getResources().getBoolean(R.bool.config_is_support_hd_audio_feature)) {
                sInstance = new InCallUIHdAudioPlugin(context);
            } else {
                sInstance = new InCallUIHdAudioHelper();
            }
            log("getInstance ["+sInstance+"]");
        }
        return sInstance;
    }

    public InCallUIHdAudioHelper() {

    }

    public Drawable getCallStateIcon(Context context) {
        log("getCallStateIcon");
        return context.getDrawable(R.drawable.asd_hd_icon);
    }

    public void removeHdVoiceIcon (Context context) {
        //do nothing
    }

    public void showHdAudioIndicator(ImageView view, BottomRow.Info info, Context context, int subId) {
        if (view.getVisibility() != View.VISIBLE) {
            if (info.isHdAttemptingIconVisible) {
                view.setImageResource(R.drawable.asd_hd_icon);
                view.setVisibility(View.VISIBLE);
                view.setActivated(false);
                Drawable drawableCurrent = view.getDrawable().getCurrent();
                if (drawableCurrent instanceof Animatable
                        && !((Animatable) drawableCurrent).isRunning()) {
                    ((Animatable) drawableCurrent).start();
                }
            } else if (info.isHdIconVisible) {
                view.setImageResource(R.drawable.asd_hd_icon);
                view.setVisibility(View.VISIBLE);
                view.setActivated(true);
            }
        } else if (info.isHdIconVisible) {
            view.setActivated(true);
        } else if (!info.isHdAttemptingIconVisible) {
            //Sprd: Add for bug751734
            view.setVisibility(View.INVISIBLE);
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
