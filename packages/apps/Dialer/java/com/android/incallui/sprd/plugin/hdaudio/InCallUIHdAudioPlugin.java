package com.android.incallui.sprd.plugin.hdaudio;

import com.android.dialer.R;
import android.content.Context;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;

import com.android.incallui.contactgrid.BottomRow.Info;
import com.android.internal.telephony.TelephonyIntentsEx;
import android.os.AsyncTask;
import android.content.Intent;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import com.android.sprd.telephony.RadioInteractor;

/**
 * Various utilities for dealing with phone number strings.
 */
public class InCallUIHdAudioPlugin extends InCallUIHdAudioHelper {
    private Context context;
    private static final String TAG = "[InCallUIHdAudioPlugin]";
    private boolean hdVoiceState = false;
    private boolean volteHdVoiceState = false;

    public InCallUIHdAudioPlugin() {
    }

    public InCallUIHdAudioPlugin(Context context) {
        this.context = context;
    }

    public Drawable getCallStateIcon(Context context) {
        log("getCallStateIcon volteHdVoiceState = " + volteHdVoiceState + " hdVoiceState = " + hdVoiceState) ;
        // Return high definition audio icon if the capability is indicated.
        //UNISOC: add for bug 973929
        if (hdVoiceState) {
            return this.context.getDrawable(R.drawable.ic_hd_voice_audio);
        }
        return super.getCallStateIcon(context);
    }

    public void showHdAudioIndicator(ImageView view, Info info, Context context, int subId) {
        if (info == null || view == null) {
            return;
        }
        log("showHdAudioIndicator") ;
        volteHdVoiceState = (info != null && info.isHdIconVisible);
        HdVoiceAsyncTask task = new HdVoiceAsyncTask(context, view, info, subId);
        task.execute();
    }

    public void removeHdVoiceIcon (Context context) {
        hdVoiceState = false;
        log("removeHdVoiceIcon hdVoiceState = " + hdVoiceState);
        Intent intent = new Intent( "android.intent.action.HIGH_DEF_AUDIO_SUPPORT"/*TelephonyIntentsEx.ACTION_HIGH_DEF_AUDIO_SUPPORT*/);
        intent.putExtra("isHdVoiceSupport", hdVoiceState);
        context.sendBroadcast(intent);
    }

    private class HdVoiceAsyncTask extends AsyncTask<Void, Void, Boolean> {
        private Context context;
        private int subId;
        private ImageView view;
        private Info info;

        public HdVoiceAsyncTask(Context context, ImageView view, Info info, int id) {
            Log.d("HdVoiceAsyncTask", "new HdVoiceAsyncTask");
            this.context = context;
            this.subId = id;
            this.view = view;
            this.info = info;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            boolean hdVoiceSupport;
            SubscriptionManager.getDefaultDataSubscriptionId();
            int slotId = SubscriptionManager.getSlotIndex(subId);
            RadioInteractor radioInteractor = new RadioInteractor(context);
            hdVoiceSupport = radioInteractor.queryHdVoiceState(slotId);
            Log.d("HdVoiceAsyncTask", "doInBackground hdVoiceSupport = " + hdVoiceSupport);
            return hdVoiceSupport;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            hdVoiceState = result;
            Log.d("HdVoiceAsyncTask", "onPostExecute hdVoiceState = " + hdVoiceState + ","
                    + info.isHdAttemptingIconVisible + "," + info.isHdIconVisible);
            Intent intent = new Intent("android.intent.action.HIGH_DEF_AUDIO_SUPPORT"/*TelephonyIntentsEx.ACTION_HIGH_DEF_AUDIO_SUPPORT*/);
            //UNISOC: add for bug 973929
            intent.putExtra("isHdVoiceSupport"/*TelephonyIntentsEx.EXTRA_HIGH_DEF_AUDIO*/, hdVoiceState);
            context.sendBroadcast(intent);

            view.setImageDrawable(getCallStateIcon(context));
            /* add for bug900292 963878 */
            if (view.getVisibility() != View.VISIBLE) {
                if (info.isHdAttemptingIconVisible) {
                    view.setVisibility(View.VISIBLE);
                    view.setActivated(false);
                    Drawable drawableCurrent = view.getDrawable().getCurrent();
                    if (drawableCurrent instanceof Animatable
                            && !((Animatable) drawableCurrent).isRunning()) {
                        ((Animatable) drawableCurrent).start();
                    }
                } else if (info.isHdIconVisible || hdVoiceState) {
                    view.setVisibility(View.VISIBLE);
                    view.setActivated(true);
                }
            } else if (info.isHdIconVisible || hdVoiceState) {
                view.setActivated(true);
            } else if (!info.isHdAttemptingIconVisible) {
                /* add for bug900292 */
                view.setVisibility(View.INVISIBLE);
            }
            /* UNISOC: add for bug979817 @{*/
            if (view.getVisibility() == View.VISIBLE && view.getWidth() == 0) {
                ViewGroup.LayoutParams para;
                para = view.getLayoutParams();
                para.width = WindowManager.LayoutParams.WRAP_CONTENT;
                view.setLayoutParams(para);
                view.requestLayout();
            } else if (view.getVisibility() == View.INVISIBLE && view.getWidth() != 0) {
                ViewGroup.LayoutParams para;
                para = view.getLayoutParams();
                para.width = 0;
                view.setLayoutParams(para);
                view.requestLayout();
            }
            /* @} */
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
