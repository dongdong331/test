/*
 * Copyright (C) 2017 The Android Open Source Project
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


import android.annotation.NonNull;
import android.content.Context;
import android.hardware.wifi.hostapd.V1_0.HostapdStatus;
import android.hardware.wifi.hostapd.V1_0.HostapdStatusCode;
//import android.hardware.wifi.hostapd.V1_0.IHostapd;
import vendor.sprd.hardware.wifi.hostapd.V1_0.IHostapd;
import vendor.sprd.hardware.wifi.hostapd.V1_0.IHostapdCallback;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.net.wifi.WifiConfiguration;
import android.os.HwRemoteBinder;
import android.os.RemoteException;
import android.util.Log;

import android.net.wifi.WifiManager;
import android.os.HwRemoteBinder;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseArray;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.WifiNative.HostapdDeathEventHandler;
import com.android.server.wifi.util.NativeUtil;

import javax.annotation.concurrent.ThreadSafe;

/**
 * To maintain thread-safety, the locking protocol is that every non-static method (regardless of
 * access level) acquires mLock.
 */
@ThreadSafe
public class HostapdHal {
    private static final String TAG = "HostapdHal";

    private final Object mLock = new Object();
    private boolean mVerboseLoggingEnabled = false;
    private final boolean mEnableAcs;
    private final boolean mEnableIeee80211AC;
    private WifiSoftapMonitor mWifiMonitor;

    // Hostapd HAL interface objects
    private IServiceManager mIServiceManager = null;
    private IHostapd mIHostapd;
    private IHostapdCallback mIHostapdCallback;
    private final Context mContext;
    private HostapdDeathEventHandler mDeathEventHandler;
    private final String AP_STA_CONNECTED_STR = "AP-STA-CONNECTED";
    private final String AP_STA_DISCONNECTED_STR = "AP-STA-DISCONNECTED";
    public String mInterfaceName = SystemProperties.get("wifi.interface", "wlan0");

    private final IServiceNotification mServiceNotificationCallback =
            new IServiceNotification.Stub() {
        public void onRegistration(String fqName, String name, boolean preexisting) {
            synchronized (mLock) {
                if (mVerboseLoggingEnabled) {
                    Log.i(TAG, "IServiceNotification.onRegistration for: " + fqName
                            + ", " + name + " preexisting=" + preexisting);
                }
                if (!initHostapdService()) {
                    Log.e(TAG, "initalizing IHostapd failed.");
                    hostapdServiceDiedHandler();
                } else {
                    Log.i(TAG, "Completed initialization of IHostapd.");
                }
            }
        }
    };
    private final HwRemoteBinder.DeathRecipient mServiceManagerDeathRecipient =
            cookie -> {
                synchronized (mLock) {
                    Log.w(TAG, "IServiceManager died: cookie=" + cookie);
                    hostapdServiceDiedHandler();
                    mIServiceManager = null; // Will need to register a new ServiceNotification
                }
            };
    private final HwRemoteBinder.DeathRecipient mHostapdDeathRecipient =
            cookie -> {
                synchronized (mLock) {
                    Log.w(TAG, "IHostapd/IHostapd died: cookie=" + cookie);
                    hostapdServiceDiedHandler();
                }
            };

    public HostapdHal(Context context, WifiSoftapMonitor monitor) {
        mEnableAcs = context.getResources().getBoolean(R.bool.config_wifi_softap_acs_supported);
        mEnableIeee80211AC =
                context.getResources().getBoolean(R.bool.config_wifi_softap_ieee80211ac_supported);
        mContext = context;
        mWifiMonitor = monitor;
        mIHostapdCallback = new SoftapIfaceHalCallback();
    }

    /**
     * Enable/Disable verbose logging.
     *
     * @param enable true to enable, false to disable.
     */
    void enableVerboseLogging(boolean enable) {
        synchronized (mLock) {
            mVerboseLoggingEnabled = enable;
        }
    }

    /**
     * Link to death for IServiceManager object.
     * @return true on success, false otherwise.
     */
    private boolean linkToServiceManagerDeath() {
        synchronized (mLock) {
            if (mIServiceManager == null) return false;
            try {
                if (!mIServiceManager.linkToDeath(mServiceManagerDeathRecipient, 0)) {
                    Log.wtf(TAG, "Error on linkToDeath on IServiceManager");
                    hostapdServiceDiedHandler();
                    mIServiceManager = null; // Will need to register a new ServiceNotification
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "IServiceManager.linkToDeath exception", e);
                mIServiceManager = null; // Will need to register a new ServiceNotification
                return false;
            }
            return true;
        }
    }

    /**
     * Registers a service notification for the IHostapd service, which triggers intialization of
     * the IHostapd
     * @return true if the service notification was successfully registered
     */
    public boolean initialize() {
        synchronized (mLock) {
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "Registering IHostapd service ready callback.");
            }
            mIHostapd = null;
            if (mIServiceManager != null) {
                // Already have an IServiceManager and serviceNotification registered, don't
                // don't register another.
                return true;
            }
            try {
                mIServiceManager = getServiceManagerMockable();
                if (mIServiceManager == null) {
                    Log.e(TAG, "Failed to get HIDL Service Manager");
                    return false;
                }
                if (!linkToServiceManagerDeath()) {
                    return false;
                }
                /* TODO(b/33639391) : Use the new IHostapd.registerForNotifications() once it
                   exists */
                if (!mIServiceManager.registerForNotifications(
                        IHostapd.kInterfaceName, "", mServiceNotificationCallback)) {
                    Log.e(TAG, "Failed to register for notifications to "
                            + IHostapd.kInterfaceName);
                    mIServiceManager = null; // Will need to register a new ServiceNotification
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while trying to register a listener for IHostapd service: "
                        + e);
                hostapdServiceDiedHandler();
                mIServiceManager = null; // Will need to register a new ServiceNotification
                return false;
            }
            return true;
        }
    }

    /**
     * Link to death for IHostapd object.
     * @return true on success, false otherwise.
     */
    private boolean linkToHostapdDeath() {
        synchronized (mLock) {
            if (mIHostapd == null) return false;
            try {
                if (!mIHostapd.linkToDeath(mHostapdDeathRecipient, 0)) {
                    Log.wtf(TAG, "Error on linkToDeath on IHostapd");
                    hostapdServiceDiedHandler();
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "IHostapd.linkToDeath exception", e);
                return false;
            }
            return true;
        }
    }

    /**
     * Initialize the IHostapd object.
     * @return true on success, false otherwise.
     */
    private boolean initHostapdService() {
        synchronized (mLock) {
            try {
                mIHostapd = getHostapdMockable();
            } catch (RemoteException e) {
                Log.e(TAG, "IHostapd.getService exception: " + e);
                return false;
            }
            if (mIHostapd == null) {
                Log.e(TAG, "Got null IHostapd service. Stopping hostapd HIDL startup");
                return false;
            }
            if (!linkToHostapdDeath()) {
                return false;
            }
        }
        mWifiMonitor.broadcastHostapdConnectionEvent(mInterfaceName);
        return true;
    }

     /** See ISupplicantStaNetwork.hal for documentation */
    private boolean registerCallback(String ifaceName, IHostapdCallback callback) {
        synchronized (mLock) {
            try {
                IHostapd.IfaceParams ifaceParams = new IHostapd.IfaceParams();
                ifaceParams.ifaceName = ifaceName;
                return mIHostapd.registerCallback(ifaceParams, callback);
            } catch (RemoteException e) {
                handleRemoteException(e, "registerCallback");
                return false;
            }
        }
    }
    private void handleRemoteException(RemoteException e, String methodStr) {
        hostapdServiceDiedHandler();
        Log.e(TAG, "mIHostapd." + methodStr + " failed with exception", e);
    }

    /**
     * Add and start a new access point.
     *
     * @param ifaceName Name of the interface.
     * @param config Configuration to use for the AP.
     * @return true on success, false otherwise.
     */
    public boolean addAccessPoint(@NonNull String ifaceName, @NonNull WifiConfiguration config) {
        synchronized (mLock) {
            final String methodStr = "addAccessPoint";
            IHostapd.IfaceParams ifaceParams = new IHostapd.IfaceParams();
            ifaceParams.ifaceName = ifaceName;
            ifaceParams.hwModeParams.enable80211N = true;
            ifaceParams.hwModeParams.enable80211AC = mEnableIeee80211AC;
            try {
                ifaceParams.channelParams.band = getBand(config);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Unrecognized apBand " + config.apBand);
                return false;
            }
            if (mEnableAcs) {
                ifaceParams.channelParams.enableAcs = true;
                ifaceParams.channelParams.acsShouldExcludeDfs = true;
            } else {
                // Downgrade IHostapd.Band.BAND_ANY to IHostapd.Band.BAND_2_4_GHZ if ACS
                // is not supported.
                // We should remove this workaround once channel selection is moved from
                // ApConfigUtil to here.
                if (ifaceParams.channelParams.band == IHostapd.Band.BAND_ANY) {
                    Log.d(TAG, "ACS is not supported on this device, using 2.4 GHz band.");
                    ifaceParams.channelParams.band = IHostapd.Band.BAND_2_4_GHZ;
                }
                ifaceParams.channelParams.enableAcs = false;
                ifaceParams.channelParams.channel = config.apChannel;
            }

            IHostapd.NetworkParamsEx nwParams = new IHostapd.NetworkParamsEx();
            // TODO(b/67745880) Note that config.SSID is intended to be either a
            // hex string or "double quoted".
            // However, it seems that whatever is handing us these configurations does not obey
            // this convention.
            nwParams.networkParams.ssid.addAll(NativeUtil.stringToByteArrayList(config.SSID));
            nwParams.networkParams.isHidden = config.hiddenSSID;
            nwParams.networkParams.encryptionType = getEncryptionType(config);
            nwParams.networkParams.pskPassphrase = (config.preSharedKey != null) ? config.preSharedKey : "";
            nwParams.softApMaxNumSta = config.softApMaxNumSta;
            nwParams.macAddrAcl = config.macAddrAcl;
            nwParams.groupReKeyInterval = config.groupReKeyInterval;
            if (!checkHostapdAndLogFailure(methodStr)) return false;
            try {
                HostapdStatus status = mIHostapd.addAccessPointEx(ifaceParams, nwParams);
                if (!registerCallback(mInterfaceName, mIHostapdCallback)) {
                    Log.e(TAG, "registerCallback fail");
                    return false;
                }
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Remove a previously started access point.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    public boolean removeAccessPoint(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "removeAccessPoint";
            if (!checkHostapdAndLogFailure(methodStr)) return false;
            try {
                HostapdStatus status = mIHostapd.removeAccessPoint(ifaceName);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Registers a death notification for hostapd.
     * @return Returns true on success.
     */
    public boolean registerDeathHandler(@NonNull HostapdDeathEventHandler handler) {
        if (mDeathEventHandler != null) {
            Log.e(TAG, "Death handler already present");
        }
        mDeathEventHandler = handler;
        return true;
    }

    /**
     * Deregisters a death notification for hostapd.
     * @return Returns true on success.
     */
    public boolean deregisterDeathHandler() {
        if (mDeathEventHandler == null) {
            Log.e(TAG, "No Death handler present");
        }
        mDeathEventHandler = null;
        return true;
    }

    /**
     * Clear internal state.
     */
    private void clearState() {
        synchronized (mLock) {
            mIHostapd = null;
        }
    }

    /**
     * Handle hostapd death.
     */
    private void hostapdServiceDiedHandler() {
        synchronized (mLock) {
            clearState();
            if (mDeathEventHandler != null) {
                mDeathEventHandler.onDeath();
                mWifiMonitor.broadcastHostapdDisconnectionEvent(mInterfaceName);
            }
        }
    }

    /**
     * Signals whether Initialization completed successfully.
     */
    public boolean isInitializationStarted() {
        synchronized (mLock) {
            return mIServiceManager != null;
        }
    }

    /**
     * Signals whether Initialization completed successfully.
     */
    public boolean isInitializationComplete() {
        synchronized (mLock) {
            return mIHostapd != null;
        }
    }

    /**
     * Terminate the hostapd daemon.
     */
    public void terminate() {
        synchronized (mLock) {
            final String methodStr = "terminate";
            if (!checkHostapdAndLogFailure(methodStr)) return;
            try {
                mIHostapd.terminate();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            }
        }
    }

    /**
     * Wrapper functions to access static HAL methods, created to be mockable in unit tests
     */
    @VisibleForTesting
    protected IServiceManager getServiceManagerMockable() throws RemoteException {
        synchronized (mLock) {
            return IServiceManager.getService();
        }
    }

    @VisibleForTesting
    protected IHostapd getHostapdMockable() throws RemoteException {
        synchronized (mLock) {
            return IHostapd.getService();
        }
    }

    private static int getEncryptionType(WifiConfiguration localConfig) {
        int encryptionType;
        switch (localConfig.getAuthType()) {
            case WifiConfiguration.KeyMgmt.NONE:
                encryptionType = IHostapd.EncryptionType.NONE;
                break;
            case WifiConfiguration.KeyMgmt.WPA_PSK:
                encryptionType = IHostapd.EncryptionType.WPA;
                break;
            case WifiConfiguration.KeyMgmt.WPA2_PSK:
                encryptionType = IHostapd.EncryptionType.WPA2;
                break;
            default:
                // We really shouldn't default to None, but this was how NetworkManagementService
                // used to do this.
                encryptionType = IHostapd.EncryptionType.NONE;
                break;
        }
        return encryptionType;
    }

    private static int getBand(WifiConfiguration localConfig) {
        int bandType;
        switch (localConfig.apBand) {
            case WifiConfiguration.AP_BAND_2GHZ:
                bandType = IHostapd.Band.BAND_2_4_GHZ;
                break;
            case WifiConfiguration.AP_BAND_5GHZ:
                bandType = IHostapd.Band.BAND_5_GHZ;
                break;
            case WifiConfiguration.AP_BAND_ANY:
                bandType = IHostapd.Band.BAND_ANY;
                break;
            default:
                throw new IllegalArgumentException();
        }
        return bandType;
    }

    /**
     * Returns false if Hostapd is null, and logs failure to call methodStr
     */
    private boolean checkHostapdAndLogFailure(String methodStr) {
        synchronized (mLock) {
            if (mIHostapd == null) {
                Log.e(TAG, "Can't call " + methodStr + ", IHostapd is null");
                return false;
            }
            return true;
        }
    }

    /**
     * Returns true if provided status code is SUCCESS, logs debug message and returns false
     * otherwise
     */
    private boolean checkStatusAndLogFailure(HostapdStatus status,
            String methodStr) {
        synchronized (mLock) {
            if (status.code != HostapdStatusCode.SUCCESS) {
                Log.e(TAG, "IHostapd." + methodStr + " failed: " + status.code
                        + ", " + status.debugMessage);
                return false;
            } else {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "IHostapd." + methodStr + " succeeded");
                }
                return true;
            }
        }
    }

    /** See IHostapd.hal for documentation */
    public boolean doHostapdBooleanCommand(String ifaceName, String cmd) {
        synchronized (mLock) {
            if (mIHostapd == null) return false;
            IHostapd.IfaceParams ifaceParams = new IHostapd.IfaceParams();
            ifaceParams.ifaceName = ifaceName;
            final String methodStr = "doHostapdBooleanCommand";
            Log.e(TAG, "doHostapdBooleanCommand");
            //if (!checkSupplicantAndLogFailure(methodStr)) return false;
            try {
                return mIHostapd.doHostapdBooleanCommand(ifaceParams, cmd);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }
    /** See IHostapd.hal for documentation */
    public int doHostapdIntCommand(String ifaceName, String cmd) {
        synchronized (mLock) {
            if (mIHostapd == null) return 0;
            IHostapd.IfaceParams ifaceParams = new IHostapd.IfaceParams();
            ifaceParams.ifaceName = ifaceName;
            final String methodStr = "doHostapdIntCommand";
            Log.e(TAG, "doHostapdIntCommand");
            //if (!checkSupplicantAndLogFailure(methodStr)) return false;
            try {
                return mIHostapd.doHostapdIntCommand(ifaceParams, cmd);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return 0;
            }
        }
    }
    /** See IHostapd.hal for documentation */
    public String doHostapdStringCommand(String ifaceName, String cmd) {
        synchronized (mLock) {
            if (mIHostapd == null) return "fail";
            IHostapd.IfaceParams ifaceParams = new IHostapd.IfaceParams();
            ifaceParams.ifaceName = ifaceName;
            final String methodStr = "doHostapdStringCommand";
            Log.e(TAG, "doHostapdStringCommand");
            //if (!checkSupplicantAndLogFailure(methodStr)) return false;
            try {
                String result = mIHostapd.doHostapdStringCommand(ifaceParams, cmd);
                Log.e(TAG, "doHostapdStringCommand return " + result);
                return result;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return "fail";
            }
        }
    }
private class SoftapIfaceHalCallback extends IHostapdCallback.Stub {

    @Override
    public void HostApEvents(String event) {
        // String mInterfaceName = SystemProperties.get("wifi.interface", "wlan0");
         Log.e(TAG, "call back :" + event);
         String[] tokens = event.split(" ");
        /* AP-STA-CONNECTED 42:fc:89:a8:96:09 */
        if (tokens[0].equals(AP_STA_CONNECTED_STR)) {
            mWifiMonitor.broadcastSoftapConnectionEvent(
                    mInterfaceName,tokens[1]);
            /* AP-STA-DISCONNECTED 42:fc:89:a8:96:09 */
        } else if (tokens[0].equals(AP_STA_DISCONNECTED_STR)) {
            mWifiMonitor.broadcastSoftapDisconnectionEvent(
                                mInterfaceName,tokens[1]);
        }
    }

}
    private static void logd(String s) {
        Log.d(TAG, s);
    }

    private static void logi(String s) {
        Log.i(TAG, s);
    }

    private static void loge(String s) {
        Log.e(TAG, s);
    }
}

