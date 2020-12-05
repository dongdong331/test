/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi;

import com.sprd.server.wifi.VoWifiAssistor;

import static com.android.server.wifi.util.ApConfigUtil.ERROR_GENERIC;
import static com.android.server.wifi.util.ApConfigUtil.ERROR_NO_CHANNEL;
import static com.android.server.wifi.util.ApConfigUtil.SUCCESS;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.internal.util.WakeupMessage;
import com.android.server.wifi.WifiNative.InterfaceCallback;
import com.android.server.wifi.WifiNative.SoftApListener;
import com.android.server.wifi.util.ApConfigUtil;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.app.IBatteryStats;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.MessageUtils;
import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.wifi.nano.WifiMetricsProto;
import com.android.server.wifi.nano.WifiMetricsProto.StaEvent;
import com.android.server.wifi.util.NativeUtil;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiFeaturesUtils;
import android.net.wifi.WpsInfo;
import android.net.wifi.WpsResult;
import android.net.wifi.WpsResult.Status;
import android.os.Message;
import android.os.SystemProperties;
import com.android.sprd.telephony.RadioInteractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.android.server.wifi.WifiApClientStats;

/**
 * Manage WiFi in AP mode.
 * The internal state machine runs under "WifiStateMachine" thread context.
 */
public class SoftApManager implements ActiveModeManager {
    private static final String TAG = "SoftApManager";

    // Minimum limit to use for timeout delay if the value from overlay setting is too small.
    private static final int MIN_SOFT_AP_TIMEOUT_DELAY_MS = 600_000;  // 10 minutes

    @VisibleForTesting
    public static final String SOFT_AP_SEND_MESSAGE_TIMEOUT_TAG = TAG
            + " Soft AP Send Message Timeout";

    private final Context mContext;
    private final FrameworkFacade mFrameworkFacade;
    private final WifiNative mWifiNative;

    private String mCountryCode;
    // Channel for sending replies.
    private AsyncChannel mReplyChannel = new AsyncChannel();

    private static final int SUCCESSES = 1;
    private static final int FAILURE = -1;

    private final SoftApStateMachine mStateMachine;
    private RadioInteractor mRadioInteractor;
    private final WifiManager.SoftApCallback mCallback;
    //NOTE: Add for SoftAp Advance Feature BEG-->
    private static boolean DBG = true;
    private WifiSoftapMonitor mWifiSoftapMonitor = null;
    private String mConnectedStations = "";
    private boolean mHostapdMonitorStarted = false;
    private boolean mConnectedToHostapd = false;
    private WifiApClientStats mWifiApClientStats = null;
    private int mMacAddrAcl = 0;
    private final WifiSoftapNative mWifiSoftapNative;
    private BroadcastReceiver mSoftApBroadcastReceiver;
    private WifiInjector mWifiInjector = null;
    String mInterfaceName = null;
    private static final int HOSTAPD_UPDATE_5G_CHANNEL_DELAY = 2000;
    private static final int HOSTAPD_SUPPORT_5G_CHANNEL_MIN = 36;
    private static final int HOSTAPD_SUPPORT_5G_CHANNEL_MAX = 165;
    private static final int HOSTAPD_SUPPORT_CHANNEL_INIT = 0;

    protected void log(String s) {
    Log.d(TAG, s);
    }
    protected void loge(String s) {
        Log.e(TAG, s);
    }

    private static class TetherStateChange {
        public ArrayList<String> available;
        public ArrayList<String> active;

        TetherStateChange(ArrayList<String> av, ArrayList<String> ac) {
            available = av;
            active = ac;
        }
    }

    //<-- Add for SoftAp advance Feature END

    private String mApInterfaceName;
    private boolean mIfaceIsUp;

    private final WifiApConfigStore mWifiApConfigStore;

    private final WifiMetrics mWifiMetrics;

    private final int mMode;
    private WifiConfiguration mApConfig;

    private int mReportedFrequency = -1;
    private int mReportedBandwidth = -1;

    private int mNumAssociatedStations = 0;
    private boolean mTimeoutEnabled = false;

    private VoWifiAssistor mVoWifiAssistor;
    /**
     * Listener for soft AP events.
     */
    private final SoftApListener mSoftApListener = new SoftApListener() {
        @Override
        public void onNumAssociatedStationsChanged(int numStations) {
            mStateMachine.sendMessage(
                    SoftApStateMachine.CMD_NUM_ASSOCIATED_STATIONS_CHANGED, numStations);
        }

        @Override
        public void onSoftApChannelSwitched(int frequency, int bandwidth) {
            mStateMachine.sendMessage(
                    SoftApStateMachine.CMD_SOFT_AP_CHANNEL_SWITCHED, frequency, bandwidth);
        }
    };

    public SoftApManager(@NonNull WifiInjector wifiInjector,
                         @NonNull Context context,
                         @NonNull Looper looper,
                         @NonNull FrameworkFacade framework,
                         @NonNull WifiNative wifiNative,
                         String countryCode,
                         @NonNull WifiManager.SoftApCallback callback,
                         @NonNull WifiApConfigStore wifiApConfigStore,
                         @NonNull SoftApModeConfiguration apConfig,
                         @NonNull WifiMetrics wifiMetrics,
                         @NonNull VoWifiAssistor voWifiAssistor) {
        mContext = context;
        mFrameworkFacade = framework;
        mWifiNative = wifiNative;
        mCountryCode = countryCode;
        mCallback = callback;
        mWifiInjector = wifiInjector;
        mWifiSoftapNative = wifiInjector.getWifiSoftapNative();
        mInterfaceName = mWifiSoftapNative.getInterfaceName();
        mWifiSoftapMonitor = mWifiInjector.getWifiSoftapMonitor();
        mWifiApClientStats = mWifiInjector.getWifiApClientStats();
        mWifiApConfigStore = wifiApConfigStore;
        mMode = apConfig.getTargetMode();
        mRadioInteractor = new RadioInteractor(mContext);
        WifiConfiguration config = apConfig.getWifiConfiguration();
        if (config == null) {
            mApConfig = mWifiApConfigStore.getApConfiguration();
        } else {
            mApConfig = config;
        }
        mWifiMetrics = wifiMetrics;
        mStateMachine = new SoftApStateMachine(looper);

        mVoWifiAssistor = voWifiAssistor;
        mContext.registerReceiver(
                mSoftApBroadcastReceiver = new BroadcastReceiver() {
                    @Override
                     public void onReceive(Context context, Intent intent) {
                         ArrayList<String> available = intent.getStringArrayListExtra(
                                 ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                         ArrayList<String> active = intent.getStringArrayListExtra(
                                 ConnectivityManager.EXTRA_ACTIVE_TETHER);
                         mStateMachine.sendMessage(
                                 SoftApStateMachine.CMD_TETHER_STATE_CHANGE,
                                 new TetherStateChange(available, active));
                     }
                 }, new IntentFilter(ConnectivityManager.ACTION_TETHER_STATE_CHANGED));

        Log.d(TAG, "Using SPRD softap features : " + WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_FEATURES
                + " maxstanum = " + WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_MAX_NUMBER);

    }

    /**
     * Start soft AP with the supplied config.
     */
    public void start() {
        if (mVoWifiAssistor.isVoWifiRegistered()) {
            mVoWifiAssistor.delayStartSoftAP(this);
        } else {
            startImmediately();
        }
    }

    public void startImmediately() {
        mStateMachine.sendMessage(SoftApStateMachine.CMD_START, mApConfig);
    }

    /**
     * Stop soft AP.
     */
    public void stop() {
        Log.d(TAG, " currentstate: " + getCurrentStateName());
        if (mApInterfaceName != null) {
            if (mIfaceIsUp) {
                updateApState(WifiManager.WIFI_AP_STATE_DISABLING,
                        WifiManager.WIFI_AP_STATE_ENABLED, 0);
            } else {
                updateApState(WifiManager.WIFI_AP_STATE_DISABLING,
                        WifiManager.WIFI_AP_STATE_ENABLING, 0);
            }
        }
        mStateMachine.quitNow();
    }

    /**
     * Dump info about this softap manager.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("--Dump of SoftApManager--");

        pw.println("current StateMachine mode: " + getCurrentStateName());
        pw.println("mApInterfaceName: " + mApInterfaceName);
        pw.println("mIfaceIsUp: " + mIfaceIsUp);
        pw.println("mMode: " + mMode);
        pw.println("mCountryCode: " + mCountryCode);
        if (mApConfig != null) {
            pw.println("mApConfig.SSID: " + mApConfig.SSID);
            pw.println("mApConfig.apBand: " + mApConfig.apBand);
            pw.println("mApConfig.hiddenSSID: " + mApConfig.hiddenSSID);
        } else {
            pw.println("mApConfig: null");
        }
        pw.println("mNumAssociatedStations: " + mNumAssociatedStations);
        pw.println("mTimeoutEnabled: " + mTimeoutEnabled);
        pw.println("mReportedFrequency: " + mReportedFrequency);
        pw.println("mReportedBandwidth: " + mReportedBandwidth);
    }

    private String getCurrentStateName() {
        IState currentState = mStateMachine.getCurrentState();

        if (currentState != null) {
            return currentState.getName();
        }

        return "StateMachine not active";
    }

    /**
     * Update AP state.
     * @param newState new AP state
     * @param currentState current AP state
     * @param reason Failure reason if the new AP state is in failure state
     */
    private void updateApState(int newState, int currentState, int reason) {
        mCallback.onStateChanged(newState, reason);

        //send the AP state change broadcast
        final Intent intent = new Intent(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiManager.EXTRA_WIFI_AP_STATE, newState);
        intent.putExtra(WifiManager.EXTRA_PREVIOUS_WIFI_AP_STATE, currentState);
        if (newState == WifiManager.WIFI_AP_STATE_FAILED) {
            //only set reason number when softAP start failed
            intent.putExtra(WifiManager.EXTRA_WIFI_AP_FAILURE_REASON, reason);
        }

        intent.putExtra(WifiManager.EXTRA_WIFI_AP_INTERFACE_NAME, mApInterfaceName);
        intent.putExtra(WifiManager.EXTRA_WIFI_AP_MODE, mMode);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    /**
     * Start a soft AP instance with the given configuration.
     * @param config AP configuration
     * @return integer result code
     */
    private int startSoftAp(WifiConfiguration config) {
        if (config == null || config.SSID == null) {
            Log.e(TAG, "Unable to start soft AP without valid configuration");
            return ERROR_GENERIC;
        }

        if (mWifiApClientStats != null && mWifiApClientStats.isWhiteListEnabled()) {
             config.macAddrAcl = 1;
        } else {
             config.macAddrAcl = 0;
        }

        // Make a copy of configuration for updating AP band and channel.
        WifiConfiguration localConfig = new WifiConfiguration(config);
        if (WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_5G_FEATURE) {
            if (mCountryCode == null) {
                Log.d(TAG, "mCountryCode is null!!,set default to CN");
                mCountryCode = "CN";
            }
        }
        if (0 == config.apBand) {
            int result = ApConfigUtil.updateApChannelConfig(
                    mWifiNative, mCountryCode,
                    mWifiApConfigStore.getAllowed2GChannel(), localConfig);
            if (result != SUCCESS) {
                Log.e(TAG, "Failed to update AP band and channel");
                return result;
            }
        }

        if (WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_5G_FEATURE) {
            if (1 == config.apBand) {
                if (config.apChannel < HOSTAPD_SUPPORT_5G_CHANNEL_MIN || config.apChannel > HOSTAPD_SUPPORT_5G_CHANNEL_MAX)
                    config.apChannel = HOSTAPD_SUPPORT_CHANNEL_INIT;
            }
        }

        // Setup country code if it is provided.
        if (mCountryCode != null) {
            // Country code is mandatory for 5GHz band, return an error if failed to set
            // country code when AP is configured for 5GHz band.
            if (!mWifiNative.setCountryCodeHal(
                    mApInterfaceName, mCountryCode.toUpperCase(Locale.ROOT))
                    && config.apBand == WifiConfiguration.AP_BAND_5GHZ) {
                Log.e(TAG, "Failed to set country code, required for setting up "
                        + "soft ap in 5GHz");
                return ERROR_GENERIC;
            }
        }
        if (localConfig.hiddenSSID) {
            Log.d(TAG, "SoftAP is a hidden network");
        }
        if (!mWifiNative.startSoftAp(mApInterfaceName, localConfig, mSoftApListener)) {
            Log.e(TAG, "Soft AP start failed");
            return ERROR_GENERIC;
        }
        Log.d(TAG, "Soft AP is started");

        return SUCCESS;
    }

    /**
     * Teardown soft AP and teardown the interface.
     */
    private void stopSoftAp() {
        //SPRD: Add for unregister SoftApBroadcastReceiver When softap is disabling BEG-->
        if (mContext != null && mSoftApBroadcastReceiver != null) {
            mContext.unregisterReceiver(mSoftApBroadcastReceiver);
        }
        //<-- Add for unregister SoftApBroadcastReceiver When softap is disabling END

        mWifiNative.teardownInterface(mApInterfaceName);
        mWifiSoftapMonitor.stopAllMonitoring();
        mRadioInteractor.enableRadioPowerFallback(false,0);
        Log.d(TAG, "Soft AP is stopped");
    }

    private class SoftApStateMachine extends StateMachine {
        // Commands for the state machine.
        public static final int CMD_START = 0;
        public static final int CMD_INTERFACE_STATUS_CHANGED = 3;
        public static final int CMD_NUM_ASSOCIATED_STATIONS_CHANGED = 4;
        public static final int CMD_NO_ASSOCIATED_STATIONS_TIMEOUT = 5;
        public static final int CMD_TIMEOUT_TOGGLE_CHANGED = 6;
        public static final int CMD_INTERFACE_DESTROYED = 7;
        public static final int CMD_INTERFACE_DOWN = 8;
        public static final int CMD_SOFT_AP_CHANNEL_SWITCHED = 9;
        public static final int CMD_TETHER_STATE_CHANGE = 10;
        public static final int CMD_UPDATE_HOSTAPD_CHANNEL = 11;

        private final State mIdleState = new IdleState();
        private final State mStartedState = new StartedState();

        private final InterfaceCallback mWifiNativeInterfaceCallback = new InterfaceCallback() {
            @Override
            public void onDestroyed(String ifaceName) {
                if (mApInterfaceName != null && mApInterfaceName.equals(ifaceName)) {
                    sendMessage(CMD_INTERFACE_DESTROYED);
                }
            }

            @Override
            public void onUp(String ifaceName) {
                if (mApInterfaceName != null && mApInterfaceName.equals(ifaceName)) {
                    sendMessage(CMD_INTERFACE_STATUS_CHANGED, 1);
                }
            }

            @Override
            public void onDown(String ifaceName) {
                if (mApInterfaceName != null && mApInterfaceName.equals(ifaceName)) {
                    sendMessage(CMD_INTERFACE_STATUS_CHANGED, 0);
                }
            }
        };

        SoftApStateMachine(Looper looper) {
            super(TAG, looper);

            addState(mIdleState);
            addState(mStartedState);

            setInitialState(mIdleState);
            //NOTE: Add For SoftAp advance Feature BEG-->
            registerSoftapEventHandler();
            //<-- Add For SoftAp advance Feature END
            start();
        }

        private class IdleState extends State {
            @Override
            public void enter() {
                mApInterfaceName = null;
                mIfaceIsUp = false;
            }

            @Override
            public boolean processMessage(Message message) {
                switch (message.what) {
                    case CMD_START:
                        mApInterfaceName = mWifiNative.setupInterfaceForSoftApMode(
                                mWifiNativeInterfaceCallback);
                        if (TextUtils.isEmpty(mApInterfaceName)) {
                            Log.e(TAG, "setup failure when creating ap interface.");
                            updateApState(WifiManager.WIFI_AP_STATE_FAILED,
                                    WifiManager.WIFI_AP_STATE_DISABLED,
                                    WifiManager.SAP_START_FAILURE_GENERAL);
                            mWifiMetrics.incrementSoftApStartResult(
                                    false, WifiManager.SAP_START_FAILURE_GENERAL);
                            break;
                        }
                        updateApState(WifiManager.WIFI_AP_STATE_ENABLING,
                                WifiManager.WIFI_AP_STATE_DISABLED, 0);
                        int result = startSoftAp((WifiConfiguration) message.obj);
                        if (result != SUCCESS) {
                            int failureReason = WifiManager.SAP_START_FAILURE_GENERAL;
                            if (result == ERROR_NO_CHANNEL) {
                                failureReason = WifiManager.SAP_START_FAILURE_NO_CHANNEL;
                            }
                            updateApState(WifiManager.WIFI_AP_STATE_FAILED,
                                          WifiManager.WIFI_AP_STATE_ENABLING,
                                          failureReason);
                            stopSoftAp();
                            mWifiMetrics.incrementSoftApStartResult(false, failureReason);
                            break;
                        } else {
                            mRadioInteractor.enableRadioPowerFallback(true,0);
                        }
                        transitionTo(mStartedState);
                        break;
                    default:
                        // Ignore all other commands.
                        break;
                }

                return HANDLED;
            }
        }

        private class StartedState extends State {
            private int mTimeoutDelay;
            private WakeupMessage mSoftApTimeoutMessage;
            private SoftApTimeoutEnabledSettingObserver mSettingObserver;

            /**
            * Observer for timeout settings changes.
            */
            private class SoftApTimeoutEnabledSettingObserver extends ContentObserver {
                SoftApTimeoutEnabledSettingObserver(Handler handler) {
                    super(handler);
                }

                public void register() {
                    mFrameworkFacade.registerContentObserver(mContext,
                            Settings.Global.getUriFor(Settings.Global.SOFT_AP_TIMEOUT_ENABLED),
                            true, this);
                    mTimeoutEnabled = getValue();
                }

                public void unregister() {
                    mFrameworkFacade.unregisterContentObserver(mContext, this);
                }

                @Override
                public void onChange(boolean selfChange) {
                    super.onChange(selfChange);
                    mStateMachine.sendMessage(SoftApStateMachine.CMD_TIMEOUT_TOGGLE_CHANGED,
                            getValue() ? 1 : 0);
                }

                private boolean getValue() {
                    boolean enabled = mFrameworkFacade.getIntegerSetting(mContext,
                            Settings.Global.SOFT_AP_TIMEOUT_ENABLED, 1) == 1;
                    return enabled;
                }
            }

            private int getConfigSoftApTimeoutDelay() {
                int delay = mContext.getResources().getInteger(
                        R.integer.config_wifi_framework_soft_ap_timeout_delay);
                if (delay < MIN_SOFT_AP_TIMEOUT_DELAY_MS) {
                    delay = MIN_SOFT_AP_TIMEOUT_DELAY_MS;
                    Log.w(TAG, "Overriding timeout delay with minimum limit value");
                }
                Log.d(TAG, "Timeout delay: " + delay);
                return delay;
            }

            private void scheduleTimeoutMessage() {
                if (!mTimeoutEnabled) {
                    return;
                }
                mSoftApTimeoutMessage.schedule(SystemClock.elapsedRealtime() + mTimeoutDelay);
                Log.d(TAG, "Timeout message scheduled");
            }

            private void cancelTimeoutMessage() {
                mSoftApTimeoutMessage.cancel();
                Log.d(TAG, "Timeout message canceled");
            }

            /**
             * Set number of stations associated with this soft AP
             * @param numStations Number of connected stations
             */
            private void setNumAssociatedStations(int numStations) {
                if (mNumAssociatedStations == numStations) {
                    return;
                }
                mNumAssociatedStations = numStations;
                Log.d(TAG, "Number of associated stations changed: " + mNumAssociatedStations);

                if (mCallback != null) {
                    mCallback.onNumClientsChanged(mNumAssociatedStations);
                } else {
                    Log.e(TAG, "SoftApCallback is null. Dropping NumClientsChanged event.");
                }
                mWifiMetrics.addSoftApNumAssociatedStationsChangedEvent(mNumAssociatedStations,
                        mMode);

                if (mNumAssociatedStations == 0) {
                    scheduleTimeoutMessage();
                } else {
                    cancelTimeoutMessage();
                }
            }

            private void onUpChanged(boolean isUp) {
                if (isUp == mIfaceIsUp) {
                    return;  // no change
                }
                mIfaceIsUp = isUp;
                if (isUp) {
                    Log.d(TAG, "SoftAp is ready for use");
                    updateApState(WifiManager.WIFI_AP_STATE_ENABLED,
                            WifiManager.WIFI_AP_STATE_ENABLING, 0);
                    mWifiMetrics.incrementSoftApStartResult(true, 0);
                    if (mCallback != null) {
                        mCallback.onNumClientsChanged(mNumAssociatedStations);
                    }
                    if (mWifiSoftapMonitor != null) {
                        mWifiSoftapMonitor.startMonitoring(mInterfaceName, false);
                        Log.d(TAG, "startMonitoring");
                        mConnectedToHostapd = mWifiSoftapNative.isHostapdConncted();
                        if (mConnectedToHostapd) {
                            hostapdSupportChannels();
                            syncSoftApWhiteList();
                            if (mWifiApClientStats != null) {
                                boolean whiteMode = mWifiApClientStats.isWhiteListEnabled();
                                syncSoftApSetClientWhiteListEnabled(whiteMode);
                            }
                        }
                        Log.d(TAG, "mConnectedToHostapd " +mConnectedToHostapd);
                    } else {
                        Log.e(TAG, "mWifiSoftapMonitor is null");
                    }
                    if (mWifiApClientStats != null) {
                        mWifiApClientStats.start();
                    }
                } else {
                    // the interface was up, but goes down
                    sendMessage(CMD_INTERFACE_DOWN);
                }
                mWifiMetrics.addSoftApUpChangedEvent(isUp, mMode);
            }

            @Override
            public void enter() {
                mIfaceIsUp = false;
                onUpChanged(mWifiNative.isInterfaceUp(mApInterfaceName));
                //SPRD: set sys.ril.wifi.activeprop when softap connected
                SystemProperties.set("vendor.sys.ril.wifi.active", "1");
                mTimeoutDelay = getConfigSoftApTimeoutDelay();
                Handler handler = mStateMachine.getHandler();
                mSoftApTimeoutMessage = new WakeupMessage(mContext, handler,
                        SOFT_AP_SEND_MESSAGE_TIMEOUT_TAG,
                        SoftApStateMachine.CMD_NO_ASSOCIATED_STATIONS_TIMEOUT);
                mSettingObserver = new SoftApTimeoutEnabledSettingObserver(handler);

                if (mSettingObserver != null) {
                    mSettingObserver.register();
                }
                Log.d(TAG, "Resetting num stations on start");
                mNumAssociatedStations = 0;
                scheduleTimeoutMessage();
            }

            @Override
            public void exit() {
                if (mWifiApClientStats != null) {
                    mWifiApClientStats.stop();
                }
                if (mApInterfaceName != null) {
                    stopSoftAp();
                }
                if (mSettingObserver != null) {
                    mSettingObserver.unregister();
                }
                Log.d(TAG, "Resetting num stations on stop");
                mConnectedStations = "";
                mNumAssociatedStations = 0;
                cancelTimeoutMessage();
                // Need this here since we are exiting |Started| state and won't handle any
                // future CMD_INTERFACE_STATUS_CHANGED events after this point
                mWifiMetrics.addSoftApUpChangedEvent(false, mMode);
                updateApState(WifiManager.WIFI_AP_STATE_DISABLED,
                        WifiManager.WIFI_AP_STATE_DISABLING, 0);
                mApInterfaceName = null;
                mIfaceIsUp = false;
                // SPRD: set sys.ril.wifi.activeprop when softap disconnected
                SystemProperties.set("vendor.sys.ril.wifi.active", "0");
                mStateMachine.quitNow();
            }

            @Override
            public boolean processMessage(Message message) {
                boolean ok;
                SoftApClient client;
                switch (message.what) {
                    case CMD_NUM_ASSOCIATED_STATIONS_CHANGED:
                        if (message.arg1 < 0) {
                            Log.e(TAG, "Invalid number of associated stations: " + message.arg1);
                            break;
                        }
                        Log.d(TAG, "Setting num stations on CMD_NUM_ASSOCIATED_STATIONS_CHANGED");
                        setNumAssociatedStations(message.arg1);
                        break;
                    case CMD_SOFT_AP_CHANNEL_SWITCHED:
                        mReportedFrequency = message.arg1;
                        mReportedBandwidth = message.arg2;
                        Log.d(TAG, "Channel switched. Frequency: " + mReportedFrequency
                                + " Bandwidth: " + mReportedBandwidth);
                        mWifiMetrics.addSoftApChannelSwitchedEvent(mReportedFrequency,
                                mReportedBandwidth, mMode);
                        int[] allowedChannels = new int[0];
                        if (mApConfig.apBand == WifiConfiguration.AP_BAND_2GHZ) {
                            allowedChannels =
                                    mWifiNative.getChannelsForBand(WifiScanner.WIFI_BAND_24_GHZ);
                        } else if (mApConfig.apBand == WifiConfiguration.AP_BAND_5GHZ) {
                            allowedChannels =
                                    mWifiNative.getChannelsForBand(WifiScanner.WIFI_BAND_5_GHZ);
                        } else if (mApConfig.apBand == WifiConfiguration.AP_BAND_ANY) {
                            int[] allowed2GChannels =
                                    mWifiNative.getChannelsForBand(WifiScanner.WIFI_BAND_24_GHZ);
                            int[] allowed5GChannels =
                                    mWifiNative.getChannelsForBand(WifiScanner.WIFI_BAND_5_GHZ);
                            allowedChannels = Stream.concat(
                                    Arrays.stream(allowed2GChannels).boxed(),
                                    Arrays.stream(allowed5GChannels).boxed())
                                    .mapToInt(Integer::valueOf)
                                    .toArray();
                        }
                        if (!ArrayUtils.contains(allowedChannels, mReportedFrequency)) {
                            Log.e(TAG, "Channel does not satisfy user band preference: "
                                    + mReportedFrequency);
                            mWifiMetrics.incrementNumSoftApUserBandPreferenceUnsatisfied();
                        }
                        break;
                    case CMD_TIMEOUT_TOGGLE_CHANGED:
                        boolean isEnabled = (message.arg1 == 1);
                        if (mTimeoutEnabled == isEnabled) {
                            break;
                        }
                        mTimeoutEnabled = isEnabled;
                        if (!mTimeoutEnabled) {
                            cancelTimeoutMessage();
                        }
                        if (mTimeoutEnabled && mNumAssociatedStations == 0) {
                            scheduleTimeoutMessage();
                        }
                        break;
                    case CMD_INTERFACE_STATUS_CHANGED:
                        boolean isUp = message.arg1 == 1;
                        onUpChanged(isUp);
                        break;
                    case CMD_START:
                        // Already started, ignore this command.
                        break;
                    case CMD_NO_ASSOCIATED_STATIONS_TIMEOUT:
                        if (!mTimeoutEnabled) {
                            Log.wtf(TAG, "Timeout message received while timeout is disabled."
                                    + " Dropping.");
                            break;
                        }
                        if (mNumAssociatedStations != 0) {
                            Log.wtf(TAG, "Timeout message received but has clients. Dropping.");
                            break;
                        }
                        Log.i(TAG, "Timeout message received. Stopping soft AP.");
                        updateApState(WifiManager.WIFI_AP_STATE_DISABLING,
                                WifiManager.WIFI_AP_STATE_ENABLED, 0);
                        transitionTo(mIdleState);
                        break;
                    case CMD_INTERFACE_DESTROYED:
                        Log.d(TAG, "Interface was cleanly destroyed.");
                        updateApState(WifiManager.WIFI_AP_STATE_DISABLING,
                                WifiManager.WIFI_AP_STATE_ENABLED, 0);
                        mApInterfaceName = null;
                        transitionTo(mIdleState);
                        break;
                    case CMD_INTERFACE_DOWN:
                        Log.w(TAG, "interface error, stop and report failure");
                        updateApState(WifiManager.WIFI_AP_STATE_FAILED,
                                WifiManager.WIFI_AP_STATE_ENABLED,
                                WifiManager.SAP_START_FAILURE_GENERAL);
                        updateApState(WifiManager.WIFI_AP_STATE_DISABLING,
                                WifiManager.WIFI_AP_STATE_FAILED, 0);
                        transitionTo(mIdleState);
                        break;
                    case CMD_UPDATE_HOSTAPD_CHANNEL:
                        String channels = mWifiNative.softApGetSupportChannel(mApInterfaceName);
                        Settings.Global.putString(
                            mContext.getContentResolver(), Settings.Global.SOFTAP_SUPPORT_CHANNELS, channels);
                        Log.d(TAG, "Update softap supportChannels " + channels);
                        break;
                    default:
                        return processSoftApMessageAtStartedState(message);
                }
                return HANDLED;
            }
        }

        private boolean hostapdSupportChannels() {
            if (WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_5G_FEATURE) {
                String mHostapdSupportChannels = Settings.Global.getString(
                    mContext.getContentResolver(), Settings.Global.SOFTAP_SUPPORT_CHANNELS);
                Log.d(TAG,"hostapd connected, mHostapdSupportChannels = " + mHostapdSupportChannels);
                if (mHostapdSupportChannels != null && mHostapdSupportChannels.contains(",")){
                    Log.d(TAG, "No need to update soft supportChannels , current channels " + mHostapdSupportChannels);
                    return false;
                }
                String hostapdCountryCode = mCountryCode;
                if (hostapdCountryCode == null) {
                    hostapdCountryCode = "CN";
                }
                mWifiNative.softApSetCountry(mApInterfaceName, hostapdCountryCode);
                sendMessageDelayed(obtainMessage(CMD_UPDATE_HOSTAPD_CHANNEL), HOSTAPD_UPDATE_5G_CHANNEL_DELAY);
            }
            return true;
        }

        private void syncSoftApWhiteList() {
            if (mWifiApClientStats != null) {
                List<String> macList = mWifiApClientStats.getClientMacWhiteList();
                if (mConnectedToHostapd) {
                    for (String mac : macList) {
                        mWifiNative.softApSetStationToWhiteList(mApInterfaceName, mac, true);
                    }
                }
            }
        }

        //NOTE: Add For SoftAp advance Feature BEG-->
        private boolean processSoftApMessageAtStartedState(Message message) {
            String macStr = "";
            switch (message.what) {
                case WifiSoftapMonitor.SOFTAP_STA_CONNECTED_EVENT:
                    macStr = (String)message.obj;
                    //showToast("Station: " + macStr + " now is connected!");
                   if (DBG) log("Soft AP Connected Station: " + macStr);
                    if(mConnectedStations.equals("") ) {
                        mConnectedStations = macStr;
                    } else {
                        if (mConnectedStations.contains(macStr)) {
                            loge("Soft AP Connected Station: " + macStr + " already saved!");
                        } else {
                            mConnectedStations += (" " + macStr);
                        }
                    }
                    sendSoftApConnectionChangedBroadcast(true, macStr);
                    break;
                case WifiSoftapMonitor.SOFTAP_STA_DISCONNECTED_EVENT:
                    macStr = (String)message.obj;
                    //showToast("Station: " + macStr + " now is disconnected!");
                    if (DBG) log("Soft AP Disconnected Station: "+ macStr);
                    if (mConnectedStations.contains(macStr) ) {
                        String[] dataTokens = mConnectedStations.split(" ");
                        mConnectedStations = "";
                        for (String token : dataTokens) {
                            if (token.equals(macStr)) {
                                continue;
                            }
                            if(mConnectedStations.equals("") ) {
                                mConnectedStations = token;
                            }
                            else
                                mConnectedStations +=  (" " + token);
                        }
                    }
                    sendSoftApConnectionChangedBroadcast(false, macStr);
                    break;
                case WifiSoftapMonitor.SOFTAP_HOSTAPD_CONNECTION_EVENT:
                    Log.d(TAG, "Soft AP hostapd connected");
                    mConnectedToHostapd = true;
                    mWifiNative.softApGetStations(mApInterfaceName);
                    sendSoftApBlockListAvailableBroadcast();
                    break;
                case WifiSoftapMonitor.SOFTAP_HOSTAPD_DISCONNECTION_EVENT:
                    Log.d(TAG, "Soft AP hostapd disconnected");
                    mConnectedToHostapd = false;
                    break;
                default:
                    return NOT_HANDLED;
            }
                return HANDLED;
        }

        private void registerSoftapEventHandler(){
            if (mWifiSoftapMonitor == null) {
                mWifiSoftapMonitor = mWifiInjector.getWifiSoftapMonitor();
            }
            // TO monitor the connected station to softap
            if (mWifiSoftapMonitor != null) {
                mWifiSoftapMonitor.registerHandler(mInterfaceName,WifiSoftapMonitor.SOFTAP_STA_CONNECTED_EVENT , getHandler());
                mWifiSoftapMonitor.registerHandler(mInterfaceName,WifiSoftapMonitor.SOFTAP_STA_DISCONNECTED_EVENT , getHandler());
                mWifiSoftapMonitor.registerHandler(mInterfaceName,WifiSoftapMonitor.SOFTAP_HOSTAPD_CONNECTION_EVENT , getHandler());
                mWifiSoftapMonitor.registerHandler(mInterfaceName,WifiSoftapMonitor.SOFTAP_HOSTAPD_DISCONNECTION_EVENT , getHandler());
            }
        }

        //SPRD: Repeatedly open/close softap, it will cause phone restart BEG-->
        private void unregisterSoftapEventHandler() {
            if (mWifiSoftapMonitor == null) {
                mWifiSoftapMonitor = mWifiInjector.getWifiSoftapMonitor();
            }
            mWifiSoftapMonitor.unregisterHandler(mInterfaceName,WifiSoftapMonitor.SOFTAP_STA_CONNECTED_EVENT , getHandler());
            mWifiSoftapMonitor.unregisterHandler(mInterfaceName,WifiSoftapMonitor.SOFTAP_STA_DISCONNECTED_EVENT , getHandler());
            mWifiSoftapMonitor.unregisterHandler(mInterfaceName,WifiSoftapMonitor.SOFTAP_HOSTAPD_CONNECTION_EVENT , getHandler());
            mWifiSoftapMonitor.unregisterHandler(mInterfaceName,WifiSoftapMonitor.SOFTAP_HOSTAPD_DISCONNECTION_EVENT , getHandler());
            //SPRD: Add for unregister SoftApBroadcastReceiver When softap is disabling BEG-->
            if (mContext != null && mSoftApBroadcastReceiver != null) {
                mContext.unregisterReceiver(mSoftApBroadcastReceiver);
            }
            //<-- Add for unregister SoftApBroadcastReceiver When softap is disabling END
        }
    }
    private void removeConnectedStation(String macStr) {
         if (mConnectedStations.contains(macStr) ) {
             String[] dataTokens = mConnectedStations.split(" ");
             mConnectedStations = "";
             for (String token : dataTokens) {
                 if (token.equals(macStr)) {
                     continue;
                 }
                 if(mConnectedStations.equals("") ) {
                     mConnectedStations = token;
                 }
                 else
                     mConnectedStations +=  (" " + token);
             }
         }
     }
    
     //To block a station with a mac string. Then this station will can not connected to our softap
     public boolean syncSoftApBlockStation(String mac) {
         //remove the station if it is a connected station, because for blocking a connected station,
         //a disconnected event will not be notified
         if (mConnectedToHostapd) {
             mWifiNative.softApSetBlockStation(mApInterfaceName, mac, true);
             if (mWifiApClientStats != null)
                 mWifiApClientStats.blockClient(mac);
             return true;
         } else {
             if (DBG) log("Hostapd is not connected yet ");
             return false;
         }
    }
    
     //To ublock the statition
     public boolean syncSoftApUnblockStation(String mac) {
         if(mConnectedToHostapd) {
            log("syncSoftApUnblockStation ");
             mWifiNative.softApSetBlockStation(mApInterfaceName, mac, false);
             if (mWifiApClientStats != null)
                 mWifiApClientStats.unBlockClient(mac);
                 log("unBlockClient ");
             return true;
         } else {
             if (DBG) log("Hostapd is not connected yet ");
             return false;
         }
     }
    
     //get the current connected station, return a string with format:
     //XX:XX:XX:XX:XX:XX XX:XX:XX:XX:XX:XX ...
     //each mac string separate with a blank
     public String syncSoftApGetConnectedStations() {
         if (DBG) log("Soft AP Current connected Station: "+ mConnectedStations);
    
         return mConnectedStations;
     }
    
     //get the current blocked station, return a string with format:
     //XX:XX:XX:XX:XX:XX XX:XX:XX:XX:XX:XX ...
     //each mac string separate with a blank
     public String syncSoftApGetBlockedStations() {
         String ret ="";
         if(mConnectedToHostapd) {
             if (DBG) log("Get Soft AP Blocked Station");
             ret = mWifiNative.softApGetBlockStationList(mApInterfaceName);
         } else {
             if (DBG) log("Hostapd is not connected yet ");
             ret = "";
         }
             return ret;
     }
     /**
      * Get the detail info of the connected client
      * return:
      *      return the detail info list.
      *      Format of each string info:
      *      MAC IP DEV_NAME
      * such as:
      *      00:08:22:0e:2d:fc 192.168.43.37 android-9dfb76a944bd077a
      */
     public List<String> syncSoftApGetConnectedStationsDetail() {
         if (mWifiApClientStats != null)
             return mWifiApClientStats.getClientInfoList(mConnectedStations);
         else
             return new ArrayList<String>();
     }
    
    
     /**
      * Get the detail info of the blocked client
      * return:
      *      return the detail info list.
      *      Format of each string info:
      *      MAC IP DEV_NAME
      * such as:
      *      00:08:22:0e:2d:fc 192.168.43.37 android-9dfb76a944bd077a
      */
     public List<String> syncSoftApGetBlockedStationsDetail() {
         if(mConnectedToHostapd) {
             if (DBG) log("Get Soft AP Blocked Station");
             String result = mWifiNative.softApGetBlockStationList(mApInterfaceName);
             return mWifiApClientStats.getBlockedClientInfoList(result);
         } else {
             if (DBG) log("Hostapd is not connected yet ");
             return new ArrayList<String>();
         }
     }
    
     /**
      * add the client to white list
      * in: mac
      *      contain the mac that want to add to white list. Format: xx:xx:xx:xx:xx:xx
      * in: name
      *      the name of the client, may be null
      * in softapStarted
      *      tell if the softap has started or not
      * return:
      *      return true for success.
      */
     public boolean syncSoftApAddClientToWhiteList(String mac, String name) {
         if(mConnectedToHostapd) {
             mWifiNative.softApSetStationToWhiteList(mApInterfaceName, mac, true);
         }
         return  true;
     }
    
    
     /**
      * remove the client from white list
      * in: mac
      *      contain the mac that want to remove from white list. Format: xx:xx:xx:xx:xx:xx
      * in: name
      *      the name of the client, may be null
      * in softapStarted
      *      tell if the softap has started or not
      * return:
      *      return true for success.
      */
     public boolean syncSoftApDelClientFromWhiteList(String mac, String name) {
         if(mConnectedToHostapd) {
             mWifiNative.softApSetStationToWhiteList(mApInterfaceName, mac, false);
         }
         return  true;
     }
    
     /**
      * To enable the white list or not
      * in enabled
      *      true: enable white list
      *      false: disable white list
      */
     public boolean syncSoftApSetClientWhiteListEnabled(boolean enabled) {
         if(mConnectedToHostapd) {
             mWifiNative.softApSetWhiteListEnabled(mApInterfaceName, enabled);
         }
         return  true;
     }

     private void sendSoftApConnectionChangedBroadcast(boolean isConnected, String macString){
         final Intent intent = new Intent(WifiManager.WIFI_AP_CONNECTION_CHANGED_ACTION);
         intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
         intent.putExtra(WifiManager.EXTRA_WIFI_AP_CONNECTED_STATION, isConnected);
         intent.putExtra(WifiManager.EXTRA_WIFI_AP_CONNCTION_STA_MAC, macString);
         mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
     }
     private void sendSoftApBlockListAvailableBroadcast() {
         Intent intent = new Intent(WifiManager.SOFTAP_BLOCKLIST_AVAILABLE_ACTION);
         intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
         mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
     }
     //<-- Add for SoftAp Advance Feature END
     //---------------------------------------------------------------------------------------
      //NOTE: Add For SoftAp advance Feature BEG-->

 //To block a station with a mac string. Then this station will can not connected to our softap
 public boolean syncSoftApBlockStation(AsyncChannel channel, String mac) {
     Message resultMsg = channel.sendMessageSynchronously(CMD_AP_BLOCK_STATION, mac);
     boolean result = (resultMsg.arg1 != FAILURE);
     resultMsg.recycle();
     return result;

}

 //To ublock the statition
 public boolean syncSoftApUnblockStation(AsyncChannel channel, String mac) {
     Message resultMsg = channel.sendMessageSynchronously(CMD_AP_UNBLOCK_STATION, mac);
     boolean result = (resultMsg.arg1 != FAILURE);
     resultMsg.recycle();
     return result;
 }

 //get the current connected station, return a string with format:
 //XX:XX:XX:XX:XX:XX XX:XX:XX:XX:XX:XX ...
 //each mac string separate with a blank
 public String syncSoftApGetConnectedStations(AsyncChannel channel) {
     Message resultMsg = channel.sendMessageSynchronously(CMD_AP_GET_CONNECTED_STATION);
     String result = (String) resultMsg.obj;
     resultMsg.recycle();
     return result;
 }

 //get the current blocked station, return a string with format:
 //XX:XX:XX:XX:XX:XX XX:XX:XX:XX:XX:XX ...
 //each mac string separate with a blank
 public String syncSoftApGetBlockedStations(AsyncChannel channel) {
     Message resultMsg = channel.sendMessageSynchronously(CMD_AP_GET_BLOCK_STATION);
     String result = (String) resultMsg.obj;
     resultMsg.recycle();
     return result;
 }


 /**
  * Get the detail info of the connected client
  * return:
  *      return the detail info list.
  *      Format of each string info:
  *      MAC IP DEV_NAME
  * such as:
  *      00:08:22:0e:2d:fc 192.168.43.37 android-9dfb76a944bd077a
  */
 public List<String> syncSoftApGetConnectedStationsDetail(AsyncChannel channel) {
     Message resultMsg = channel.sendMessageSynchronously(CMD_AP_GET_CONNECTED_STATION_DETAIL);
     List<String> result = (List<String>)resultMsg.obj;
     resultMsg.recycle();
     return result;
 }


 /**
  * Get the detail info of the blocked client
  * return:
  *      return the detail info list.
  *      Format of each string info:
  *      MAC IP DEV_NAME
  * such as:
  *      00:08:22:0e:2d:fc 192.168.43.37 android-9dfb76a944bd077a
  */
 public List<String> syncSoftApGetBlockedStationsDetail(AsyncChannel channel) {
     Message resultMsg = channel.sendMessageSynchronously(CMD_AP_GET_BLOCK_STATION_DETAIL);
     List<String> result =(List<String>) resultMsg.obj;
     resultMsg.recycle();
     return result;
 }

 private class SoftApClient {
     public String mac;
     public String name;

     public SoftApClient(String _mac, String _name) {
         mac = _mac;
         name = _name;
     }
 }
 /**
  * add the client to white list
  * in: mac
  *      contain the mac that want to add to white list. Format: xx:xx:xx:xx:xx:xx
  * in: name
  *      the name of the client, may be null
  * in softapStarted
  *      tell if the softap has started or not
  * return:
  *      return true for success.
  */
 public boolean syncSoftApAddClientToWhiteList(AsyncChannel channel, String mac, String name) {
     if (mac == null) return false;
     SoftApClient client = new SoftApClient(mac, name);
     if (client == null) return false;
     Message resultMsg = channel.sendMessageSynchronously(CMD_AP_ADD_WHITE_STATION, client);
     boolean result = (resultMsg.arg1 != FAILURE);
     resultMsg.recycle();
     return result;
 }

 /**
  * remove the client from white list
  * in: mac
  *      contain the mac that want to remove from white list. Format: xx:xx:xx:xx:xx:xx
  * in: name
  *      the name of the client, may be null
  * in softapStarted
  *      tell if the softap has started or not
  * return:
  *      return true for success.
  */
 public boolean syncSoftApDelClientFromWhiteList(AsyncChannel channel, String mac, String name) {
     if (mac == null) return false;
     SoftApClient client = new SoftApClient(mac, name);
     if (client == null) return false;
     Message resultMsg = channel.sendMessageSynchronously(CMD_AP_DEL_WHITE_STATION, client);
     boolean result = (resultMsg.arg1 != FAILURE);
     resultMsg.recycle();
     return result;
 }

 /**
  * To enable the white list or not
  * in enabled
  *      true: enable white list
  *      false: disable white list
  */
 public boolean syncSoftApSetClientWhiteListEnabled(AsyncChannel channel, boolean enabled) {
     Message resultMsg = channel.sendMessageSynchronously(CMD_AP_SET_STATION_BLOCKMODE, (enabled?1:0));
     boolean result = (resultMsg.arg1 != FAILURE);
     resultMsg.recycle();
     return result;
 }

 /**
  * Get the detail info of the white client list
  * return:
  *      return the detail info list.
  *      Format of each string info:
  *      MAC DEV_NAME
  * such as:
  *      00:08:22:0e:2d:fc android-9dfb76a944bd077a
  */
 public List<String> syncSoftApGetClientWhiteList() {
     if (mWifiApClientStats != null)
         return mWifiApClientStats.getClientWhiteList();
     else
         return new ArrayList<String>();
 }

 public boolean syncSoftApIsWhiteListEnabled() {
     if (mWifiApClientStats != null)
         return mWifiApClientStats.isWhiteListEnabled();
     else
         return false;
 }
 //----------------------------------------------------------------------------
    //NOTE: Add for softap support wps connect mode and hidden ssid Feature BEG-->
    public boolean softApStartWpsPbc() {
        return mWifiNative.softApStartWpsPbc(mApInterfaceName);
    }

    public boolean softApCancelWps() {
      return mWifiNative.softApCancelWps(mApInterfaceName);
    }

    public boolean softApCheckWpsPin(String pin) {
       if (TextUtils.isEmpty(pin)) return false;
	   return mWifiNative.softApCheckWpsPin(mApInterfaceName, pin);
    }
    //<-- Add for softap support wps connect mode and hidden ssid Feature END
 //NOTE: Add for softap support wps connect mode and hidden ssid Feature BEG-->
 public boolean syncSoftApWpsCheckPin(AsyncChannel channel, String wpsPin) {
     Message resultMsg = channel.sendMessageSynchronously(WifiManager.SOFTAP_WPS_CHECK_PIN, wpsPin);
     boolean result = (resultMsg.arg1 != FAILURE);
     resultMsg.recycle();
     return result;
 }

 /**
  * SoftAp Start WPS push button configuration
  * @param config WPS configuration
  * @return WpsResult indicating status and pin
  */
 WpsResult softApStartWpsPbc(WpsInfo config) {
     WpsResult result = new WpsResult();
     if (mWifiNative.softApStartWpsPbc(mApInterfaceName)) {
         result.status = WpsResult.Status.SUCCESS;
     } else {
         loge("Failed to start WPS push button configuration");
         result.status = WpsResult.Status.FAILURE;
     }
     return result;
 }
 /**
  * Start WPS pin method configuration with pin obtained
  * from the access point
  * @param config WPS configuration
  * @return Wps result containing status and pin
  */
 WpsResult softApStartWpsPinKeypad(WpsInfo config) {
     WpsResult result = new WpsResult();

     if (mWifiNative.softApStartWpsPinKeypad(mApInterfaceName, config.pin)) {
         result.status = WpsResult.Status.SUCCESS;
     } else {
         loge("Failed to start WPS pin method configuration");
         result.status = WpsResult.Status.FAILURE;
     }
     return result;
 }
 //<-- Add for softap support wps connect mode and hidden ssid Feature END

    
 /**
  * State machine initiated requests can have replyTo set to null indicating
  * there are no recepients, we ignore those reply actions.
  */
    private void replyToMessage(Message msg, int what) {
        if (msg.replyTo == null) return;
        Message dstMsg = obtainMessageWithWhatAndArg2(msg, what);
        mReplyChannel.replyToMessage(msg, dstMsg);
    }
 
    private void replyToMessage(Message msg, int what, int arg1) {
        if (msg.replyTo == null) return;
        Message dstMsg = obtainMessageWithWhatAndArg2(msg, what);
        dstMsg.arg1 = arg1;
        mReplyChannel.replyToMessage(msg, dstMsg);
    }
 
    private void replyToMessage(Message msg, int what, Object obj) {
        if (msg.replyTo == null) return;
        Message dstMsg = obtainMessageWithWhatAndArg2(msg, what);
        dstMsg.obj = obj;
        mReplyChannel.replyToMessage(msg, dstMsg);
    }
        /**
     * arg2 on the source message has a unique id that needs to be retained in replies
     * to match the request
     * <p>see WifiManager for details
     */
    private Message obtainMessageWithWhatAndArg2(Message srcMsg, int what) {
        Message msg = Message.obtain();
        msg.what = what;
        msg.arg2 = srcMsg.arg2;
        return msg;
    }
 static final int CMD_AP_BLOCK_STATION         = 11;
 static final int CMD_AP_UNBLOCK_STATION     = 12;
 static final int CMD_AP_GET_CONNECTED_STATION     = 13;
 static final int CMD_AP_GET_BLOCK_STATION     = 14;
 static final int CMD_AP_ADD_WHITE_STATION         = 15;
 static final int CMD_AP_DEL_WHITE_STATION     = 16;
 static final int CMD_AP_SET_STATION_BLOCKMODE     = 17;
 static final int CMD_AP_GET_CONNECTED_STATION_DETAIL     = 18;
 static final int CMD_AP_GET_BLOCK_STATION_DETAIL     = 19;
 //<-- Add For SoftAp advance Feature END
}
