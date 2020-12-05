package com.android.server.power.sprdpower;

import android.os.Bundle;
import android.os.sprdpower.Scene;


import android.util.ArrayMap;
import android.util.Log;
import android.util.Slog;

//////////////////////////
import android.app.ActivityManager;
import android.app.ActivityManagerInternal;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.server.LocalServices;

import android.graphics.Rect;
import android.graphics.Point;
import android.view.View;
import java.util.List;
import android.view.Display;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.MathUtils;
import android.os.SystemClock;
import android.os.SystemProperties;

import android.os.BundleData;
import android.os.sprdpower.Util;

////////////////////////////


/**
 * Created by SPREADTRUM\jonas.zhang on 18-2-6.
 */

public class TouchRateMonitor {
    private static final String TAG = "SSense.Touch";

    private static final boolean DEBUG_LOG = Util.getDebugLog();
    private static final boolean DEBUG = Util.isDebug() || DEBUG_LOG;
    private static final boolean DEBUG_MORE = false;

    private static final boolean mEnabled = true;

    // to notify the touch rate directly without calculating the average value
    private static final boolean mNotifyDirectly = true;

    private Handler mHandler;


    private final Callbacks mCallbacks;

    private int mPollingInterval = NORMAL_POLLING_INTERVAL;

    private boolean mScreenOn = true;

    private static final int NORMAL_POLLING_INTERVAL = (10*1000);


    // for touch event frequency
    private long mLastTouchTime = 0;
    private int mLastTouchCount = 0;
    private int mTouchCount = 0;
    private float mAvgTouchCount = 0; // touch count per 5s
    private float mLastAvgTouchCount = 0;
    private int mSavedAvgTouchCount = 0;
    private float[] mLastAvgTouch = new float[6];

    private static final long TOUCH_CHECK_DURATION = (120*1000); // 120s

    private float mCurrentTouchRate = 0;
    private long mLastChangeTime = 0;

    TouchRateMonitor(Handler handler, Callbacks callback) {
        mCallbacks = callback;
        mHandler = new H(handler.getLooper());
    }

    void onAppTransitionFinished() {
        if (!mEnabled) return;

        mHandler.removeMessages(MSG_POLLING_TOUCH);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_POLLING_TOUCH),
            mPollingInterval);
        if (!mNotifyDirectly) {
            clear();
        } else {
            mCurrentTouchRate = 0;
            mLastChangeTime = SystemClock.elapsedRealtime();
        }
    }

    void onScreenOn(boolean on) {
        if (!mEnabled) return;

        mScreenOn = on;
        // stop polling if screen off
        mHandler.removeMessages(MSG_POLLING_TOUCH);
        if (mScreenOn) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_POLLING_TOUCH),
                mPollingInterval);
        }
    }

    void onTouchEvent(int event) {
        if (!mEnabled) return;

        mHandler.sendMessage(mHandler.obtainMessage(MSG_TOUCH_EVENT, event, 0));
    }

    private static final int MSG_POLLING_TOUCH = 0;
    private static final int MSG_TOUCH_EVENT = 1;

    private class H extends Handler {
        public H(Looper looper) {
            super(looper);
        }

        String msg2Str(int msg) {
            final String msgStr[] = {"MSG_POLLING_TOUCH",
                "MSG_TOUCH_EVENT",
            };

            if ((0 <= msg) && (msg < msgStr.length))
                return msgStr[msg];
            else
                return "Unknown message: " + msg;
        }

        @Override
        public void handleMessage(Message msg) {
            if (DEBUG_MORE) Slog.d(TAG, "handleMessage(" + msg2Str(msg.what) + ")");
            switch (msg.what) {
                case MSG_POLLING_TOUCH:
                    pollingTouch();
                    break;
                case MSG_TOUCH_EVENT:
                    handleTouchEvent(msg.arg1);
                    break;
                default:
                    break;
            }
        }
    }

    private boolean isBalanced() {
        if (mSavedAvgTouchCount < mLastAvgTouch.length) return false;

        // find the max and min
        float min = mLastAvgTouch[0];
        float max = mLastAvgTouch[0];

        for(int i=0; i<mLastAvgTouch.length; i++) {
            if (mLastAvgTouch[i] > max)
                max = mLastAvgTouch[i];
            if (mLastAvgTouch[i] < min)
                min = mLastAvgTouch[i];
        }

        if ((max - min) > mAvgTouchCount*3/4)
            return false;

        return true;
    }

    private void handleTouchEvent(int event) {

        if (event == BundleData.TOUCH_EVENT_DOWN
            || event == BundleData.TOUCH_EVENT_UP) {
            mTouchCount++;
        }
    }

    private void pollingTouch() {
        long now = SystemClock.elapsedRealtime();
        float currentAvg = 0.0f;
        if (mLastTouchTime > 0) {
            int delt = mTouchCount - mLastTouchCount;
            float avg = (float)delt/((now-mLastTouchTime)/10000);

            currentAvg = avg;
            int index = mSavedAvgTouchCount % mLastAvgTouch.length;
            mLastAvgTouch[index] = avg;
            mSavedAvgTouchCount++;

            if (mSavedAvgTouchCount >= mLastAvgTouch.length) {
                float total = 0.0f;
                for(int i=0; i<mLastAvgTouch.length; i++)
                    total += mLastAvgTouch[i];
                mAvgTouchCount = total/mLastAvgTouch.length;
            }
        }

        if (DEBUG_MORE) {
            Slog.d(TAG, "mAvgTouchCount:" + mAvgTouchCount + " mLastAvgTouchCount:" + mLastAvgTouchCount
                + " mSavedAvgTouchCount:" + mSavedAvgTouchCount
                + " mTouchCount:" + mTouchCount
                + " mLastTouchCount:" + mLastTouchCount
                + " mLastTouchTime:" + mLastTouchTime);
            for(int i=0; i<mLastAvgTouch.length; i++)
                Slog.d(TAG, "mLastAvgTouch[" + i + "]:" + mLastAvgTouch[i]);
        }

        mLastTouchTime = now;
        mLastTouchCount = mTouchCount;


        if (mNotifyDirectly) {
            float newRate = (float)((currentAvg + mCurrentTouchRate)*0.5);
            boolean changed = false;
            // threshold: 1.0
            if ((newRate > 1.0f && mCurrentTouchRate < 1.0f)
                || (newRate < 1.0f && mCurrentTouchRate > 1.0f)) {
                changed = true;
                mLastChangeTime = now;
            }
            mCurrentTouchRate = newRate;

            if (now - mLastChangeTime <= TOUCH_CHECK_DURATION)
                changed = true;

            if (changed) {
                if (mCallbacks != null)
                    mCallbacks.onTouchRateChanged(newRate);
            }
        } else {
            if (isBalanced()) {
                if (MathUtils.abs(mLastAvgTouchCount - mAvgTouchCount) > 1.0f ) {
                    if (DEBUG) Slog.d(TAG, "Avg TOUCH CHANGED!!");
                    if (mCallbacks != null)
                        mCallbacks.onTouchRateChanged(currentAvg);
                }
                mLastAvgTouchCount = mAvgTouchCount;
            }
        }

        if (mScreenOn) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_POLLING_TOUCH),
                mPollingInterval);
        }

    }

    private void clear() {
        mLastTouchTime = 0;
        mLastTouchCount = 0;
        mTouchCount = 0;
        mAvgTouchCount = 0; // touch count per 5s
        mLastAvgTouchCount = 0;
        mSavedAvgTouchCount = 0;
        for(int i=0; i<mLastAvgTouch.length; i++)
            mLastAvgTouch[i] = 0;
    }

    /** Callbacks to notify touch rate changed. */
    interface Callbacks {
        void onTouchRateChanged(float rate);
    }
}
