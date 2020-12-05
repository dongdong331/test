/*
 * Copyright (C) 2017 Spreadtrum Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server.policy;


import android.content.Context;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.SprdSensor;

import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.Slog;

import android.view.KeyEvent;
import android.view.View;
import android.view.MotionEvent;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.IWindowManager;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.LayoutInflater;

import java.io.PrintWriter;
import com.android.server.UiThread;

public class DisableTouchModeFunc {
    private static final String TAG = "DisableTouchModeFunc";
    static final boolean DEBUG_INPUT = true;

    Context mContext;
    PolicyHandlerEx mFuncHandler;
    boolean mPocketModeEnabledSetting;
    DisableTouchModePanel mDisableTouchModePanel;
    MyPocketEventListener mPocketEventListener;

    public DisableTouchModeFunc(Context context) {
        mContext = context;
        mFuncHandler = new PolicyHandlerEx();
    }

    protected boolean isPocketModeEnabled() {
        return mPocketModeEnabledSetting;
    }

    void setPocketModeEnabled(boolean enable) {
        mPocketModeEnabledSetting = enable;
    }

    protected void updatePocketEventListenerLp() {
        if (shouldEnablePocketEventGestureLp()) {
            if (DEBUG_INPUT) Slog.d(TAG, "register pocketEvent sensor");
            mFuncHandler.sendEmptyMessage(MSG_INIT_TOUCH_PANEL);
            enableListener();
        } else {
            if (DEBUG_INPUT) Slog.d(TAG, "unregister pocketEvent sensor");
            mFuncHandler.sendEmptyMessage(MSG_DISABLE_TOUCH_PANEL);
            disableListener();
        }
    }

    protected boolean shouldEnablePocketEventGestureLp() {
        if (DEBUG_INPUT) Slog.d(TAG, "mPocketModeEnabledSetting = " + mPocketModeEnabledSetting + ",isSupported = " + isSensorSupported());
        return mPocketModeEnabledSetting && isSensorSupported();
    }

    private static final int MSG_INIT_TOUCH_PANEL = 1;
    private static final int MSG_DISABLE_TOUCH_PANEL = 2;

    protected class PolicyHandlerEx extends Handler {
        public PolicyHandlerEx() {
            super(UiThread.getHandler().getLooper());
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_INIT_TOUCH_PANEL:
                    initTouchModePanel();
                    break;
                case MSG_DISABLE_TOUCH_PANEL:
                    disableTouchModePanel();
                    break;
            }
        }
    }

    void registerPocketEventListener(Context context) {
        mPocketEventListener = new MyPocketEventListener(context);
    }

    void enableListener() {
        mPocketEventListener.enable();
    }

    void disableListener() {
        mPocketEventListener.disable();
    }

    boolean isSensorSupported() {
        return mPocketEventListener != null && mPocketEventListener.isSupported();
    }

    void initTouchModePanel() {
        if (mDisableTouchModePanel == null) {
            mDisableTouchModePanel = new DisableTouchModePanel(mContext);
            mDisableTouchModePanel.init();
        }
    }

    void disableTouchModePanel () {
        if (mDisableTouchModePanel != null) {
            mDisableTouchModePanel.setPocketMode(false);
        }
    }

    void hideTouchModePanel() {
        if (mDisableTouchModePanel != null && mDisableTouchModePanel.isShowing()) {
            mDisableTouchModePanel.show(false);
        }
    }

    void showTouchModePanel() {
        if (mDisableTouchModePanel != null && mDisableTouchModePanel.getPocketMode()) {
            mDisableTouchModePanel.show(true);
        }
    }

    long showBackground() {
        if (mDisableTouchModePanel != null && mDisableTouchModePanel.isShowing()) {
            mDisableTouchModePanel.showBackground();
            return -1;
        }
        return 0;
    }

    class MyPocketEventListener extends PocketEventListener {
        Handler mHandler;
        private final Runnable mStartPocketRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (mLock) {
                    if (DEBUG_INPUT) Slog.d(TAG, "onStartPocketMode");
                    if (mDisableTouchModePanel != null) {
                        mDisableTouchModePanel.setPocketMode(true);
                    }
                }
            }
        };

        private final Runnable mStopPocketRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (mLock) {
                    if (DEBUG_INPUT) Slog.d(TAG, "onStopPocketMode");
                    if (mDisableTouchModePanel != null) {
                        mDisableTouchModePanel.setPocketMode(false);
                    }
                }
            }
        };

        MyPocketEventListener(Context context) {
            super(context);
            mHandler = UiThread.getHandler();
        }

        @Override
        public void onStartPocketMode() {
            mHandler.post(mStartPocketRunnable);
        }

        @Override
        public void onStopPocketMode() {
            mHandler.post(mStopPocketRunnable);
        }
    }

    abstract class PocketEventListener {
        private static final String TAG = "PocketEventListener";

        private SensorManager mSensorManager;
        private boolean mEnabled = false;
        private Sensor mSensor;
        private SensorEventListener mSensorEventListener;
        private final Handler mHandler;

        protected final Object mLock = new Object();

        public PocketEventListener(Context context) {
            mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
            mHandler = UiThread.getHandler();

            mSensor = mSensorManager.getDefaultSensor(SprdSensor.TYPE_SPRDHUB_POCKET_MODE, true);
            if (mSensor != null) {
                // Create listener only if sensors do exist
                mSensorEventListener = new SensorEventListenerImpl();
            }
        }

        public boolean isSupported() {
            synchronized (mLock) {
                return mSensor != null;
            }
        }
        /**
         * Enables the PocketEventListener so it will monitor the sensor and call
         * {@link #onPocketChanged} when the device pocket mode changes.
         */
        public void enable() {
            synchronized (mLock) {
                if (mSensor == null) {
                    Log.w(TAG, "Cannot detect sensors. Not enabled");
                    return;
                }
                if (mEnabled == false) {
                    mSensorManager.registerListener(mSensorEventListener, mSensor,
                            SensorManager.SENSOR_DELAY_NORMAL, mHandler);
                    mEnabled = true;
                }
            }
        }

        /**
         * Disables the PocketEventListener.
         */
        public void disable() {
            synchronized (mLock) {
                if (mSensor == null) {
                    Log.w(TAG, "Cannot detect sensors. Invalid disable");
                    return;
                }
                if (mEnabled == true) {
                    mSensorManager.unregisterListener(mSensorEventListener);
                    mEnabled = false;
                }
            }
        }

        public void dump(PrintWriter pw, String prefix) {
            synchronized (mLock) {
                pw.println(prefix + TAG);
                prefix += "  ";
                pw.println(prefix + "mEnabled=" + mEnabled);
                pw.println(prefix + "mSensor=" + mSensor);
            }
        }

        class SensorEventListenerImpl implements SensorEventListener {
            public void onSensorChanged(SensorEvent event) {
                if (DEBUG_INPUT) {
                    Log.d(TAG," PocketEventListener onPocketChanged event = " + event.values[0]);
                }
                int mode = (int)event.values[0];
                if (mode == 1){
                    onStartPocketMode();
                } else if(mode == 0){
                    onStopPocketMode();
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // TODO Auto-generated method stub
            }
        }

        public abstract void onStartPocketMode();
        public abstract void onStopPocketMode();
    }

    class DisableTouchModePanel {
        private static final String TAG = "DisableTouchModePanel";
        private static final int MSG_RESET_PANEL = 1;
        private static final int MSG_SHOW_PANEL = 2;
        private static final int MSG_HIDE_PANEL = 3;
        private static final int MSG_SHOW_BACKGROUND = 4;
        private final WindowManager mWindowManager;
        private final Object mLock = new Object();
        Handler mHandler = new DH();
        private final View.OnTouchListener mOnTouchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (DEBUG_INPUT) {
                    Slog.i(TAG, "onTouch, set backgournd to visible, mShowBG = " + mShowBG);
                }
                //set the background view opaque
                showBackground();
                return true;
            }
        };
        private Context mContext;
        private View mDisableTouchView;
        private boolean mShowBG = false;

        private int STATUS_HIDDEN = 0;
        private int STATUS_SHOWING = 1;
        private int STATUS_SHOWN = 2;
        private int mShow = STATUS_HIDDEN;

        private boolean mInit = false;
        private boolean mPocketModeOn = false;
        private BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DEBUG_INPUT) {
                    Slog.i(TAG, "mReceiver  onReceive  intent.getAction(): " + intent.getAction());
                }
                if (intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED) && mDisableTouchView != null) {
                    if (DEBUG_INPUT) {
                        Slog.i(TAG, "language changed, reinit Disable-Touch view");
                    }
                    mHandler.sendEmptyMessage(MSG_RESET_PANEL);
                }
            }
        };

        DisableTouchModePanel(Context context) {
            mContext = context;
            mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        }

        void setPocketMode(boolean isOn) {
            synchronized (mLock) {
                mPocketModeOn = isOn;
                if (!mPocketModeOn && mShow == STATUS_SHOWN) {
                    show(false);
                } else if (!mPocketModeOn && mShow == STATUS_SHOWING) {
                    mShow = STATUS_HIDDEN;
                }
            }
        }

        boolean getPocketMode() {
            synchronized (mLock) {
                return mPocketModeOn;
            }
        }

        void showBackground() {
            if (!mShowBG) {
                mHandler.sendEmptyMessage(MSG_SHOW_BACKGROUND);
            }
        }

        void init() {
            synchronized (mLock) {
                if (mDisableTouchView == null) {
                    try {
                        //Inflate the disable-touch-mode-panel view
                        mDisableTouchView = LayoutInflater.from(mContext).inflate(com.android.internal.R.layout.disable_touch_mode_panel, null);

                        //set this view to invisible and transparent
                        mDisableTouchView.setVisibility(View.INVISIBLE);
                        mDisableTouchView.setAlpha(0.0f);

                        //set onTouchListener for this view
                        mDisableTouchView.setOnTouchListener(mOnTouchListener);

                        //add this view in WMS
                        WindowManager.LayoutParams lp = getLayoutParams();
                        mWindowManager.addView(mDisableTouchView, lp);

                        IntentFilter filter = new IntentFilter();
                        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
                        mContext.registerReceiver(mReceiver, filter);

                        mInit = true;
                        if (DEBUG_INPUT) {
                            Slog.i(TAG, "init");
                        }
                    } catch (Exception e) {
                        Slog.e(TAG, "init failed, exception: " + e);
                        Throwable tr = new Throwable();
                        tr.fillInStackTrace();
                        Slog.e(TAG, "Throw", tr);
                    }
                }
            }
        }

        void reset() {
            mHandler.sendEmptyMessage(MSG_RESET_PANEL);
        }

        public void show(boolean show) {
            synchronized (mLock) {
                if (show) {
                    mShow = STATUS_SHOWING;
                    mHandler.sendEmptyMessage(MSG_SHOW_PANEL);
                } else {
                    mHandler.sendEmptyMessage(MSG_HIDE_PANEL);
                }
            }
        }

        void resetHandle() {
            synchronized (mLock) {
                try {
                    if (mInit && mDisableTouchView != null) {
                        mDisableTouchView.setOnTouchListener(null);
                        mWindowManager.removeView(mDisableTouchView);
                        mDisableTouchView = null;

                        //Inflate the disable-touch-mode-panel view
                        mDisableTouchView = LayoutInflater.from(mContext).inflate(com.android.internal.R.layout.disable_touch_mode_panel, null);

                        //set this view to invisible and transparent
                        mDisableTouchView.setVisibility(View.INVISIBLE);
                        mDisableTouchView.setAlpha(0.0f);

                        //set onTouchListener for this view
                        mDisableTouchView.setOnTouchListener(mOnTouchListener);

                        //add this view in WMS
                        WindowManager.LayoutParams lp = getLayoutParams();
                        mWindowManager.addView(mDisableTouchView, lp);

                        if (DEBUG_INPUT) {
                            Slog.i(TAG, "reset");
                        }
                    }
                }catch (Exception e) {
                    Slog.e(TAG, "reset failed, exception: " + e);
                    Throwable tr = new Throwable();
                    tr.fillInStackTrace();
                    Slog.e(TAG, "Throw", tr);
                }
            }
        }

        private void showBackgroundHandle() {
            synchronized (mLock) {
                if (!mShowBG) {
                    mShowBG = true;
                    mDisableTouchView.setAlpha(1.0f);
                }
            }
        }

        private void showHandle() {
            synchronized (mLock) {
                if (mDisableTouchView != null && mPocketModeOn) {
                    if (DEBUG_INPUT) {
                        Slog.i(TAG, "DisableTouchView, show = true, mShow = " + mShow);
                    }
                    if (mShow == STATUS_SHOWING) {
                        if (DEBUG_INPUT) {
                            Slog.i(TAG, "set VISIBLE");
                        }
                        mDisableTouchView.setVisibility(View.VISIBLE);
                        mDisableTouchView.setAlpha(0.0f);
                        mShow = STATUS_SHOWN;
                        mShowBG = false;
                    }
                }
            }
        }

        private void hideHandle() {
            synchronized (mLock) {
                if (mDisableTouchView != null) {
                    if (DEBUG_INPUT) {
                        Slog.i(TAG, "DisableTouchView, show = false, mShow = " + mShow);
                    }
                    if (mShow == STATUS_SHOWN) {
                        if (DEBUG_INPUT) {
                            Slog.i(TAG, "STATUS_SHOWN, set GONE");
                        }
                        mDisableTouchView.setVisibility(View.GONE);
                        mDisableTouchView.setAlpha(0.0f);
                        mShow = STATUS_HIDDEN;
                        mShowBG = false;
                    } else if (mShow == STATUS_SHOWING) {
                        if (DEBUG_INPUT) {
                            Slog.i(TAG, "STATUS_SHOWING, set GONE");
                        }
                        mShow = STATUS_HIDDEN;
                    }
                }
            }
        }

        public boolean isShown() {
            synchronized (mLock) {
                return mShow == STATUS_SHOWN;
            }
        }

        public boolean isShowing() {
            synchronized (mLock) {
                return mShow == STATUS_SHOWING || mShow == STATUS_SHOWN;
            }
        }

        public boolean isBackgroundShown() {
            synchronized (mLock) {
                return mShowBG;
            }
        }

        private WindowManager.LayoutParams getLayoutParams() {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM | Gravity.START;
            lp.setTitle("DisableTouchModePanel");
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
            // UNISOC: fits notch display
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            return lp;
        }

        private class DH extends Handler {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_RESET_PANEL:
                        resetHandle();
                        break;
                    case MSG_SHOW_PANEL:
                        showHandle();
                        break;
                    case MSG_HIDE_PANEL:
                        hideHandle();
                        break;
                    case MSG_SHOW_BACKGROUND:
                        showBackgroundHandle();
                    default:
                        break;
                }
            }
        }
    }
}
