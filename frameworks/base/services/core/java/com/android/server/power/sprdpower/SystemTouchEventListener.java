/*
 ** Copyright 2016 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Slog;
import android.view.GestureDetector;
import android.view.InputDevice;
import android.view.MotionEvent;

import android.view.WindowManagerPolicyConstants.PointerEventListener;

public class SystemTouchEventListener implements PointerEventListener {

    private static final String TAG = "PowerController.SysTouchEvt";


    private final boolean DEBUG = isDebug();
    private final boolean DEBUG_MORE = false;
    private final boolean TEST = PowerController.TEST;

    private final Context mContext;
    private final Callbacks mCallbacks;


    public SystemTouchEventListener(Context context, Callbacks callbacks) {
        mContext = context;
        mCallbacks = callbacks;
    }

    @Override
    public void onPointerEvent(MotionEvent event) {
        if (!event.isTouchEvent()) {
            return;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                //if (DEBUG) Slog.d(TAG, "Event " + event);
                if (mCallbacks != null)
                    mCallbacks.onDown();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_HOVER_MOVE:
                break;
            case MotionEvent.ACTION_UP:
                //if (DEBUG) Slog.d(TAG, "Event " + event);
                if (mCallbacks != null)
                    mCallbacks.onUp();
                break;
            case MotionEvent.ACTION_CANCEL:
                break;
            default:
                if (DEBUG) Slog.d(TAG, "Ignoring " + event);
        }
    }


    interface Callbacks {
        void onDown();
        void onUp();
    }

    private boolean isDebug(){
        return Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug");
    }
}
