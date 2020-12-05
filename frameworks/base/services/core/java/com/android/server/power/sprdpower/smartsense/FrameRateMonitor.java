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
import android.os.sprdpower.Util;

////////////////////////////


/**
 * Created by SPREADTRUM\jonas.zhang on 18-2-6.
 */

public class FrameRateMonitor {
    private static final String TAG = "SSense.fps";

    private static final boolean DEBUG_LOG = Util.getDebugLog();
    private static final boolean DEBUG = Util.isDebug() || DEBUG_LOG;
    private static final boolean DEBUG_MORE = false;

    private static final boolean mEnabled = true;

    private Handler mHandler;


    private final Callbacks mCallbacks;

    // for fps
    private float mLastAvgFPS = 1.0f;
    private float mCurrentFPS = 1.0f;
    private long mLastFPSPollingTime = 0;
    private int mLastFCount = 0;
    private int mFPSCount = 0;
    private float[] mLastFPS = new float[3];
    private int mPollingInterval = NORMAL_FPS_POLLING_INTERVAL;

    private boolean mScreenOn = true;

    private static final int NORMAL_FPS_POLLING_INTERVAL = (5*1000); // 5s
    private static final int FAST_FPS_POLLING_INTERVAL = (1*1000);


    FrameRateMonitor(Handler handler, Callbacks callback) {
        mCallbacks = callback;
        mHandler = new H(handler.getLooper());
    }

    void onAppTransitionFinished() {
        if (!mEnabled) return;
        clear();
        mPollingInterval = FAST_FPS_POLLING_INTERVAL;
        mHandler.removeMessages(MSG_POLLING_FPS);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_POLLING_FPS),
            mPollingInterval);
    }

    void onScreenOn(boolean on) {
        if (!mEnabled) return;

        if (DEBUG) Slog.d(TAG, "onScreenOn:" + on);
        mScreenOn = on;
        // stop polling if screen off
        mHandler.removeMessages(MSG_POLLING_FPS);
        if (mScreenOn) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_POLLING_FPS),
                mPollingInterval);
        }
    }


    private static final int MSG_POLLING_FPS = 0;

    private class H extends Handler {
        public H(Looper looper) {
            super(looper);
        }

        String msg2Str(int msg) {
            final String msgStr[] = {"MSG_POLLING_FPS",
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
                case MSG_POLLING_FPS:
                    pollingFps();
                    break;
                default:
                    break;
            }
        }
    }

    private void pollingFps() {

        boolean needFast = false;
        // get fps
        long now = SystemClock.elapsedRealtime();
        int flipCount = SystemProperties.getInt("sys.flipbuffer", 1);
        if (mLastFPSPollingTime > 0) {
            int delt = flipCount - mLastFCount;
            float fps = (float)delt/((now-mLastFPSPollingTime)/1000);

            int index = mFPSCount % mLastFPS.length;
            mLastFPS[index] = fps;
            mFPSCount++;

            if (DEBUG_MORE) {
                Slog.d(TAG, "mCurrentFPS:" + mCurrentFPS + " mLastAvgFPS:" + mLastAvgFPS
                    + " fps:" + fps);
                for(int i=0; i<mLastFPS.length; i++)
                    Slog.d(TAG, "mLastFPS[" + i + "]:" + mLastFPS[i]);
            }

            // fps is changed largely
            if (MathUtils.abs(fps - mCurrentFPS) > (0.5f*mCurrentFPS)) {
                needFast = true;
            }
            if (mFPSCount >= mLastFPS.length) {
                float total = 0.0f;
                for(int i=0; i<mLastFPS.length; i++)
                    total += mLastFPS[i];
                mCurrentFPS = total/mLastFPS.length;
            }
        }

        mLastFCount = flipCount;
        mLastFPSPollingTime = now;


        if (DEBUG_MORE) {
            Slog.d(TAG, "mCurrentFPS:" + mCurrentFPS + " mFPSCount:" + mFPSCount
                + " mLastFCount:" + mLastFCount
                + " mLastFPSPollingTime:" + mLastFPSPollingTime
                + " mScreenOn:" + mScreenOn);
            for(int i=0; i<mLastFPS.length; i++)
                Slog.d(TAG, "mLastFPS[" + i + "]:" + mLastFPS[i]);
        }

        mPollingInterval = needFast? FAST_FPS_POLLING_INTERVAL:NORMAL_FPS_POLLING_INTERVAL;

        if (isBalanced()) {

            if (MathUtils.abs(mLastAvgFPS - mCurrentFPS) > 5.0f ) {
                if (DEBUG) Slog.d(TAG, "Avg FPS CHANGED!!");
                if (mCallbacks != null)
                    mCallbacks.onFpsChanged(mCurrentFPS);
            }

            mLastAvgFPS = mCurrentFPS;
        }

        if (mScreenOn) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_POLLING_FPS),
                mPollingInterval);
        }
    }

    private boolean isBalanced() {
        if (mFPSCount < mLastFPS.length) return false;

        // find the max and min
        float min = mLastFPS[0];
        float max = mLastFPS[0];

        for(int i=0; i<mLastFPS.length; i++) {
            if (mLastFPS[i] > max)
                max = mLastFPS[i];
            if (mLastFPS[i] < min)
                min = mLastFPS[i];
        }

        if ((max - min) > mCurrentFPS*2/3
            && mCurrentFPS > 10.0f)
            return false;

        return true;
    }


    private void clear() {
        mLastAvgFPS = 1.0f;
        mCurrentFPS = 1.0f;
        mLastFPSPollingTime = 0;
        mLastFCount = 0;
        mFPSCount = 0;
        for(int i=0; i<mLastFPS.length; i++)
            mLastFPS[i] = 0;
    }


    /** Callbacks to notify fps changed. */
    interface Callbacks {
        void onFpsChanged(float fps);
    }

}
