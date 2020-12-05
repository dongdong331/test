/*
 * Copyright (C) 2013 Sprdtrum.com
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

package com.android.server;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Slog;

import com.android.sprd.telephony.RadioInteractor;
import com.android.sprd.telephony.RadioInteractorCallbackListener;

import java.util.HashMap;

//TODO:

public class WcnManagerService {
    private static final String TAG = "WcnManagerService";

    private static final boolean DBG = true;

    private static final int EVENT_WIFI_AP_STARTED = 1000;

    private static final int EVENT_WIFI_AP_STOPPED = 1001;

    private static final int EVENT_LTE_BAND_CHANGED = 1002;

    private static final int EVENT_LISTEN_BAND_INFO = 1003;

    private static final int EVENT_LISTEN_NONE = 1004;

    private static final int DISABLE_LTE_BAND_REPORT = 0;
    private static final int ENABLE_LTE_BAND_REPORT = 1;

    //if LTE bond change too fast. dnsmasq will die. so hanle event every 5s
    private static final int EVENT_CHANGE_DELAY = 5000;

    private static final String RESULT_ERROR = "ERROR";
    /**
     * Binder context for this service
     */
    private Context mContext;

    private WifiManager mWifiManager = null;
    private int mWifiApIdeaChannel = -1;
    private int mWifiApState = WifiManager.WIFI_AP_STATE_DISABLED;

    private RadioInteractor mRi;

    private boolean mLteBandReportEnabled = false;

    private final WakeLock mWakeLock;

    private InternalHandler mHandler = null;
    private boolean mRestartWifiApAfterChannelChange = false;
    private RadioInteractorCallbackListener[] mRadioInteractorCallbackListener;
    private int mPhoneCount;
    private TelephonyManager mTelephonyManager;
    private static final HashMap<String, Integer> mWifiApBandMap = new HashMap<String, Integer>();

    //the band info from Tel/RIL
    static {
        mWifiApBandMap.put("4007", 6); //band40/band7
        mWifiApBandMap.put("40", 11); //band 40
        //mWifiApBandMap.put("41", 1); //band 41
        //mWifiApBandMap.put("38", 1); //band 38
        mWifiApBandMap.put("7", 1); // band 7
    }


    /**
     * Constructs a new WcnManagerService instance
     *
     * @param context  Binder context for this service
     */
    private WcnManagerService(Context context) {
        mContext = context;

        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mHandler = new InternalHandler(handlerThread.getLooper());

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WcnManagerService");

        mContext.registerReceiver(mWifiApStateReceiver,
                new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION));

        mContext.bindService(new Intent(
                "com.android.sprd.telephony.server.RADIOINTERACTOR_SERVICE")
                .setPackage("com.android.sprd.telephony.server"), new ServiceConnection(){
            public void onServiceConnected(ComponentName name, IBinder service){
                log("on radioInteractor service connected");
                mRi = new RadioInteractor(mContext);

                mTelephonyManager = TelephonyManager.from(mContext);
                if (mTelephonyManager != null) mPhoneCount = mTelephonyManager.getPhoneCount();
                mRadioInteractorCallbackListener = new RadioInteractorCallbackListener[mPhoneCount];
                for (int i = 0; i < mPhoneCount; i++) {
                    mRadioInteractorCallbackListener[i] = getRadioInteractorCallbackListener(i);
                }
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_LISTEN_BAND_INFO));
            }
            public void onServiceDisconnected(ComponentName name){
                log("on radioInteractor service disconnect");
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_LISTEN_NONE));
            }
        },0);
    }

    private static WcnManagerService mService;
    public static WcnManagerService create(Context context) throws InterruptedException {
        if (mService == null) mService = new WcnManagerService(context);
        Slog.d(TAG, "Creating WcnManagerService mService=" + mService);
        return mService;
    }

    private RadioInteractorCallbackListener getRadioInteractorCallbackListener(final int phoneId) {
        return new RadioInteractorCallbackListener(phoneId) {
            public void onbandInfoEvent(Object  o) {
                String info = (String) ((AsyncResult) o).result;
               log("onbandInfoEvent:" + info);
               //mLTEBand = parseLteBandInfo(info);
               if (mHandler.hasMessages(EVENT_LTE_BAND_CHANGED)) {
                   mHandler.removeMessages(EVENT_LTE_BAND_CHANGED);
               }
               mHandler.sendEmptyMessageDelayed(EVENT_LTE_BAND_CHANGED, EVENT_CHANGE_DELAY);
            }
        };
    }


    private final BroadcastReceiver mWifiApStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleWifiApStateBroadcast(context, intent);
        }
    };

    private void handleWifiApStateBroadcast(Context context, Intent intent) {
        String action = intent.getAction();

        //log("handleWifiApStateBroadcast " + action);
        if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
            mWifiApState = intent.getIntExtra(WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED);
            log("state = " + mWifiApState);
            // Check if wifi ap need restart.
            if (mWifiApState == WifiManager.WIFI_AP_STATE_DISABLED
                    || mWifiApState == WifiManager.WIFI_AP_STATE_FAILED) {
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_WIFI_AP_STOPPED));
            } else if (mWifiApState == WifiManager.WIFI_AP_STATE_ENABLED) {
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_WIFI_AP_STARTED));
            }
        }
    }

    // Start/Stop band report
    private void setLteBandReportEnabled(boolean enabled) {
        synchronized (this) {
            log("setLteBandReportEnabled enabled=" + enabled + ", mLteBandReportEnabled=" + mLteBandReportEnabled);
            if (mLteBandReportEnabled == enabled) return;

            mLteBandReportEnabled = enabled;
            for (int i = 0; i < mPhoneCount; i++) {
                //param1:enable/disable LTE band report,param2:sim card
                mRi.setBandInfoMode(enabled ? ENABLE_LTE_BAND_REPORT:DISABLE_LTE_BAND_REPORT,i);
            }
        }
    }

    // On wifi ap started
    private void onWifiApStarted() {
        //start band report
        setLteBandReportEnabled(true);
        mRestartWifiApAfterChannelChange = false;
    }

    //On wifi ap stopped
    private void onWifiApStopped() {
        // No need reset lte band report if restarting wifi ap which spend a very little time.
        if (mRestartWifiApAfterChannelChange) {
            mRestartWifiApAfterChannelChange = false;
            log("onWifiApStopped start softap.");
            mWifiManager.startSoftAp(null);
        } else {
            //close band report
            setLteBandReportEnabled(false);
        }
    }

    public int calculateWifiApIdeaChannel() {
        int ideaChannel = -1;
        try {
            Integer channel = null;
            String lteBand = getCurrentLteBand();
            if (lteBand != null) {
                channel = mWifiApBandMap.get(lteBand);
                if (channel != null) ideaChannel = channel;
            }
            log("calculateWifiApIdeaChannel lteBand = " + lteBand
                + ", ideaChannel=" + ideaChannel);
        } catch (Exception e) {
            log("Exception: " + e);
        }
        return ideaChannel;
    }

    /**
     * +SPCLB:<band>,<frequency>,<mode>,<state>,<paging_period>,<ts_type>,<tx_timing_advance>,<rssp>
     * or <band>,<frequency>,<mode>,<state>,<paging_period>,<ts_type>,<tx_timing_advance>,<rssp>
     * <band>: 1 ~ 41
     * <mode>: 0, master
     *                  1, slave
     * <state>: 0, cell searching
     *               1, idle
     *               2, connect
     * <ts_type>:
     * <rssp>:  dbm value
     * ex:40,38950,0,1,128,2,0,-77
    */
    private String parseLteBandInfo(String bandInfoStr) {
        String band = null;
        log("parse bandinfo:" + bandInfoStr);

        if (bandInfoStr != null) {
            String[] tokens = bandInfoStr.split(":");
            String bandValueInfo = null;

            if (tokens.length < 2) {
                bandValueInfo = tokens[0].split(",")[0];
            } else {
                bandValueInfo = tokens[1].split(",")[0];
            }

            if (bandValueInfo != null) {
                String[] bandVlaues = bandValueInfo.split(" ");
                if(bandVlaues.length < 2) {
                    band = bandVlaues[0];
                } else {
                    band = bandVlaues[1];
                }
            }
        }
        return band;
    }

    private void onLteBandChanged() {
        int oldIdeaChannel = mWifiApIdeaChannel;
        mWifiApIdeaChannel = calculateWifiApIdeaChannel();
        if (mWifiApIdeaChannel != -1 && oldIdeaChannel != mWifiApIdeaChannel) {
            if (mWifiApState == WifiManager.WIFI_AP_STATE_ENABLED
                    || mWifiApState == WifiManager.WIFI_AP_STATE_ENABLING) {
                // Get wifi ap config and check whether wifi ap should be restarted
                WifiConfiguration wifiConfig = mWifiManager.getWifiApConfiguration();
                if (wifiConfig != null
                    && (wifiConfig.apChannel >= 1 && wifiConfig.apChannel <= 14) //2.4G channel (1~14)
                    && wifiConfig.apChannel != mWifiApIdeaChannel) {
                    mRestartWifiApAfterChannelChange = true;
                    log("onLteBandChanged stop softap");
                    mWifiManager.stopSoftAp();
                }
            }
        }
    }

    // The formated LTE bands should match the keys of mWifiApBandMap
    private String formatDoubleLteBands(String lteBands) {
        if (lteBands.contains("40") && lteBands.contains("7")) {
            return "4007"; //For LTE band40 & band7
        } else if (lteBands.contains("40")) {
            return "40"; //For LTE band40
        } else if (lteBands.contains("7")) {
            return "7"; //For LTE band7
        } else {
            return null;
        }
    }

    private String getCurrentLteBand() {
        String currentLteBand = null;
        String[] bandInfoArray = new String[mPhoneCount];
        String[] lteBand = new String[mPhoneCount];

        setLteBandReportEnabled(true); //Start band report before getBandInfo.
        for (int i = 0; i < mPhoneCount; i++) {
            //Get current LTE Working band
            bandInfoArray[i] = mRi.getBandInfo(i);
            log("getCurrentLteBand Get Current sim " + i +" LTE BAND info:" + bandInfoArray[i]);
            try {
                //parse and get the band value and other infor
                lteBand[i] = parseLteBandInfo(bandInfoArray[i]);
            } catch (Exception e) {
            }
        }

        if (mPhoneCount == 1) {
            if (!RESULT_ERROR.equals(lteBand[0])) {
                currentLteBand = lteBand[0];
            }
        } else if (mPhoneCount > 1) {
            if (RESULT_ERROR.equals(lteBand[0]) && RESULT_ERROR.equals(lteBand[1])) {
                log("getCurrentLteBand there is no LTE!");
            } else if (!RESULT_ERROR.equals(lteBand[0]) && !RESULT_ERROR.equals(lteBand[1])) {
                log("getCurrentLteBand there is LTE + LTE!!");
                currentLteBand = lteBand[0] + lteBand[1];
                currentLteBand = formatDoubleLteBands(currentLteBand);
            } else if (!RESULT_ERROR.equals(lteBand[0])) {
                currentLteBand = lteBand[0];
            } else if (!RESULT_ERROR.equals(lteBand[1])) {
                currentLteBand = lteBand[1];
            }
        }
        return currentLteBand;
    }

    private class InternalHandler extends Handler {
        public InternalHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (mRi == null) {
                log("mRi is NULL, return!");
                return;
            }
            switch (msg.what) {
                case EVENT_WIFI_AP_STARTED:
                    log(" EVENT_WIFI_AP_STARTED");
                    onWifiApStarted();
                    break;
                case EVENT_WIFI_AP_STOPPED:
                    log(" EVENT_WIFI_AP_STOPPED");
                    onWifiApStopped();
                    break;
                case EVENT_LTE_BAND_CHANGED: {
                    onLteBandChanged();
                    break;
                }
                case EVENT_LISTEN_BAND_INFO: {
                    for (int i = 0; i < mPhoneCount; i++) {
                        mRi.listen(mRadioInteractorCallbackListener[i],
                                RadioInteractorCallbackListener.LISTEN_BAND_INFO_EVENT, false);
                    }
                    break;
                }
                case EVENT_LISTEN_NONE: {
                    for (int i = 0; i < mPhoneCount; i++) {
                        mRi.listen(mRadioInteractorCallbackListener[i],
                                RadioInteractorCallbackListener.LISTEN_NONE, false);
                    }
                    break;
                }
                default:
                    log("Unkown msg, msg.what=" + msg.what);
                    break;
            }

        }
    }

    private void log(String s) {
        Slog.d(TAG, s);
    }

}
