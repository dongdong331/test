/*
 * Copyright (C) 2018 Spreadtrum.com
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

package com.sprd.server.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.android.ims.ImsConfig;
import com.android.ims.internal.IImsServiceEx;
import com.android.ims.internal.ImsManagerEx;
import com.android.server.wifi.ClientModeManager;
import com.android.server.wifi.SoftApManager;

import java.util.concurrent.atomic.AtomicBoolean;

// SPRD:Bug #645935 This class is for closing WiFi delayed for VoWiFi
public class VoWifiAssistor {

    private static String TAG = "VoWifiAssistor";
    private boolean mVerboseLoggingEnabled = false;

    private static final int BASE = 0x00086000;
    private static final int CMD_TURN_OFF_WIFI = BASE + 1;
    private static final int CMD_START_SOFTAP  = BASE + 2;

    private final Context mContext;
    private final AssistorHandler mAssistorHandler;

    private final AtomicBoolean mIsWifiWaitingForClose = new AtomicBoolean(false);
    private final AtomicBoolean mIsSoftApWaitingForStart = new AtomicBoolean(false);
    private final AtomicBoolean mIsSoftApStarted = new AtomicBoolean(false);

    private IImsServiceEx mIImsServiceEx;
    private WifiNetworkMonitor mWifiNetworkMonitor;

    private ClientModeManager mClientModeManager;
    private SoftApManager mSoftApManager;

    public VoWifiAssistor(Context context, Looper looper, WifiNetworkMonitor wifiNetworkMonitor) {
        mContext = context;
        mAssistorHandler = new AssistorHandler(looper);

        mWifiNetworkMonitor = wifiNetworkMonitor;
        mIImsServiceEx = ImsManagerEx.getIImsServiceEx();

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (action.equals(WifiManager.WIFI_AP_STATE_CHANGED_ACTION)) {
                            int state = intent.getIntExtra(
                                    WifiManager.EXTRA_WIFI_AP_STATE,
                                    WifiManager.WIFI_AP_STATE_FAILED);
                            if (state == WifiManager. WIFI_AP_STATE_ENABLED) {
                                if (mVerboseLoggingEnabled)
                                    Log.d(TAG, "SoftAP started");
                                mIsSoftApStarted.set(true);
                            } else if (state == WifiManager.WIFI_AP_STATE_DISABLED) {
                                if (mVerboseLoggingEnabled)
                                    Log.d(TAG, "SoftAP disabled.");
                                mIsSoftApStarted.set(false);
                            }
                        }
                    }
                },
                filter);
    }

    public void enableVerboseLogging(int verbose) {
        if (verbose > 0) {
            mVerboseLoggingEnabled = true;
        } else {
            mVerboseLoggingEnabled = false;
        }
    }

    public void notifyAndCloseWifi() {
        if (mVerboseLoggingEnabled)
            Log.d(TAG, "notifyAndCloseWifi");
        turnOffWifiImmediately();
    }

    public void delayTurnOffWifi(ClientModeManager clientModeManager) {
        if (mVerboseLoggingEnabled)
            Log.d(TAG, "delayTurnOffWifi ");
        mWifiNetworkMonitor.notifyWifiStateChange(false);
        mClientModeManager = clientModeManager;
        mIsWifiWaitingForClose.set(true);
        //mAssistorHandler.sendMessageDelayed(mAssistorHandler.obtainMessage(CMD_TURN_OFF_WIFI), 500);
    }

    public boolean isVoWifiRegistered() {
        if (mIImsServiceEx == null) {
            mIImsServiceEx = ImsManagerEx.getIImsServiceEx();
        }
        if (mIImsServiceEx == null || mWifiNetworkMonitor == null) {
            return false;
        }
        try {
            return ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI
                    == mIImsServiceEx.getCurrentImsFeature();
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException: getCurrentImsFeature() failed !");
        }
        return false;
    }

    public boolean isSoftApStarted() {
        return mIsSoftApStarted.get();
    }
    public void delayStartSoftAP(SoftApManager softApManager) {
        if (mVerboseLoggingEnabled)
            Log.d(TAG, "delayStartSoftAP");
        mWifiNetworkMonitor.notifyWifiStateChange(false);
        mIsSoftApWaitingForStart.set(true);
        mSoftApManager = softApManager;
        mAssistorHandler.sendMessageDelayed(mAssistorHandler.obtainMessage(CMD_START_SOFTAP), 500);
    }

    private void turnOffWifiImmediately() {
        if (mVerboseLoggingEnabled)
            Log.d(TAG, "turnOffWifiImmediately: is waitting =" + mIsWifiWaitingForClose.get());

        if (mAssistorHandler.hasMessages(CMD_TURN_OFF_WIFI)) {
            if (mVerboseLoggingEnabled) Log.d(TAG, "remove turn off message.");
            mAssistorHandler.removeMessages(CMD_TURN_OFF_WIFI);
        }

        if (mIsWifiWaitingForClose.get()) {
            mIsWifiWaitingForClose.set(false);
            if (mClientModeManager != null) {
                if (mVerboseLoggingEnabled)
                    Log.d(TAG, "turnOffWifiImmediately: " + "quit client mode now.");
                mClientModeManager.stopImmediately();
                mClientModeManager = null;
            }
        }
    }

    private void startSoftApImmediately() {
        if (mVerboseLoggingEnabled)
            Log.d(TAG, "startSoftApImmediately: SoftApWaiting =" + mIsSoftApWaitingForStart.get());

        if (mAssistorHandler.hasMessages(CMD_START_SOFTAP)) {
            if (mVerboseLoggingEnabled) Log.d(TAG, "remove start softAP message.");
            mAssistorHandler.removeMessages(CMD_START_SOFTAP);
        }

        if (mIsSoftApWaitingForStart.get()) {
            mIsSoftApWaitingForStart.set(false);
            if (mSoftApManager != null) {
                if (mVerboseLoggingEnabled)
                    Log.d(TAG, "turnOffWifiImmediately: " + "start softAP now.");
                mSoftApManager.startImmediately();
                mSoftApManager = null;
            }
        }
    }

    private class AssistorHandler extends Handler {

        AssistorHandler(Looper loop) {
            super(loop);
        }

        @Override
        public final void handleMessage(Message msg) {
            switch(msg.what) {
                case CMD_TURN_OFF_WIFI:
                    if (mVerboseLoggingEnabled)
                        Log.d(TAG, "handleMessage: CMD_TURN_OFF_WIFI");
                    turnOffWifiImmediately();
                    break;
                case CMD_START_SOFTAP:
                    if (mVerboseLoggingEnabled)
                        Log.d(TAG, "handleMessage: CMD_START_SOFTAP");
                    startSoftApImmediately();
                    break;
                default:
                    break;
            }
        }
    };
}
