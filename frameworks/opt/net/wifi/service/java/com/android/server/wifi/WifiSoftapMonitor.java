/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.os.Handler;
import android.os.Message;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;


import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.android.internal.util.Protocol;

/**
 * Listens for events from the softap server, and passes them on
 * to the {@link StateMachine} for handling.
 *
 * @hide
 */
public class WifiSoftapMonitor {
    private static final String TAG = "WifiSoftapMonitor";
    /* Supplicant events reported to a state machine */
    private static final int BASE = Protocol.BASE_WIFI_MONITOR;
    /* Connection to softap established */
    public static final int SOFTAP_STA_CONNECTED_EVENT = BASE + 71;
    /* Connection to softap lost */
    public static final int SOFTAP_STA_DISCONNECTED_EVENT = BASE + 72;
    public static final int SOFTAP_DATA_EVENT = BASE + 73;
    //NOTE: Add For SoftAp advance Feature BEG-->
    public static final int SOFTAP_HOSTAPD_CONNECTION_EVENT = BASE + 74;
    public static final int SOFTAP_HOSTAPD_DISCONNECTION_EVENT = BASE + 75;

    //<-- Add For SoftAp Advance Feature END
    private final WifiInjector mWifiInjector;
    private boolean mVerboseLoggingEnabled = true;
    private boolean mConnected = false;

    public WifiSoftapMonitor(WifiInjector wifiInjector) {
        mWifiInjector = wifiInjector;
    }

    void enableVerboseLogging(int verbose) {
        if (verbose > 0) {
            mVerboseLoggingEnabled = true;
        } else {
            mVerboseLoggingEnabled = false;
        }
    }

    // TODO(b/27569474) remove support for multiple handlers for the same event
    private final Map<String, SparseArray<Set<Handler>>> mHandlerMap = new HashMap<>();
    public synchronized void registerHandler(String iface, int what, Handler handler) {
        SparseArray<Set<Handler>> ifaceHandlers = mHandlerMap.get(iface);
        if (ifaceHandlers == null) {
            ifaceHandlers = new SparseArray<>();
            mHandlerMap.put(iface, ifaceHandlers);
        }
        Set<Handler> ifaceWhatHandlers = ifaceHandlers.get(what);
        if (ifaceWhatHandlers == null) {
            ifaceWhatHandlers = new ArraySet<>();
            ifaceHandlers.put(what, ifaceWhatHandlers);
        }
        ifaceWhatHandlers.add(handler);
    }

    private final Map<String, Boolean> mMonitoringMap = new HashMap<>();
    private boolean isMonitoring(String iface) {
        Log.d(TAG, "isMonitoring inface :" + iface);
        Boolean val = mMonitoringMap.get(iface);
        if (val == null) {
            return false;
        } else {
            return val.booleanValue();
        }
    }

    /**
     * Enable/Disable monitoring for the provided iface.
     *
     * @param iface Name of the iface.
     * @param enabled true to enable, false to disable.
     */
    public void setMonitoring(String iface, boolean enabled) {
        Log.d(TAG, "setMonitoring inface :" + iface);
        mMonitoringMap.put(iface, enabled);
    }

    private void setMonitoringNone() {
        for (String iface : mMonitoringMap.keySet()) {
            setMonitoring(iface, false);
        }
    }

    /**
     * Wait for softap's control interface to be ready.
     *
     * TODO: Add unit tests for these once we remove the legacy code.
     */
    private boolean ensureConnectedLocked() {
        if (mConnected) {
            return true;
        }
        if (mVerboseLoggingEnabled) Log.d(TAG, "connecting to hostapd");
        int connectTries = 0;
        while (true) {
            mConnected = mWifiInjector.getWifiSoftapNative().connectToHostapd();
            if (mConnected) {
                return true;
            }
            if (connectTries++ < 50) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignore) {
                }
            } else {
                return false;
            }
        }
    }

    /**
     * Start Monitoring for softap events.
     *
     * @param iface Name of iface.
     * TODO: Add unit tests for these once we remove the legacy code.
     */
    public synchronized void startMonitoring(String iface, boolean isStaIface) {
        if (ensureConnectedLocked()) {
            setMonitoring(iface, true);
            //broadcastSupplicantConnectionEvent(iface);
        } else {
            boolean originalMonitoring = isMonitoring(iface);
            setMonitoring(iface, true);
           // broadcastSupplicantDisconnectionEvent(iface);
            setMonitoring(iface, originalMonitoring);
            Log.e(TAG, "startMonitoring(" + iface + ") failed!");
        }
    }

    /**
     * Stop Monitoring for softap events.
     *
     * @param iface Name of iface.
     * TODO: Add unit tests for these once we remove the legacy code.
     */
    public synchronized void stopMonitoring(String iface) {
        if (mVerboseLoggingEnabled) Log.d(TAG, "stopMonitoring(" + iface + ")");
        setMonitoring(iface, true);
        //broadcastSupplicantDisconnectionEvent(iface);
        setMonitoring(iface, false);
    }

    /**
     * Stop Monitoring for softap events.
     *
     * TODO: Add unit tests for these once we remove the legacy code.
     */
    public synchronized void stopAllMonitoring() {
        mConnected = false;
        setMonitoringNone();
    }


    /**
     * Similar functions to Handler#sendMessage that send the message to the registered handler
     * for the given interface and message what.
     * All of these should be called with the WifiMonitor class lock
     */
    private void sendMessage(String iface, int what) {
        sendMessage(iface, Message.obtain(null, what));
    }

    private void sendMessage(String iface, int what, Object obj) {
        sendMessage(iface, Message.obtain(null, what, obj));
    }

    private void sendMessage(String iface, int what, int arg1) {
        sendMessage(iface, Message.obtain(null, what, arg1, 0));
    }

    private void sendMessage(String iface, int what, int arg1, int arg2) {
        sendMessage(iface, Message.obtain(null, what, arg1, arg2));
    }

    private void sendMessage(String iface, int what, int arg1, int arg2, Object obj) {
        sendMessage(iface, Message.obtain(null, what, arg1, arg2, obj));
    }

    private void sendMessage(String iface, Message message) {
        SparseArray<Set<Handler>> ifaceHandlers = mHandlerMap.get(iface);
        if (iface != null && ifaceHandlers != null) {
            if (isMonitoring(iface)) {
                Set<Handler> ifaceWhatHandlers = ifaceHandlers.get(message.what);
                if (ifaceWhatHandlers != null) {
                    for (Handler handler : ifaceWhatHandlers) {
                        if (handler != null) {
                            sendMessage(handler, Message.obtain(message));
                        }
                    }
                }
            } else {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Dropping event because (" + iface + ") is stopped");
                }
            }
        } else {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "Sending to all monitors because there's no matching iface");
            }
            for (Map.Entry<String, SparseArray<Set<Handler>>> entry : mHandlerMap.entrySet()) {
                if (isMonitoring(entry.getKey())) {
                    Set<Handler> ifaceWhatHandlers = entry.getValue().get(message.what);
                    for (Handler handler : ifaceWhatHandlers) {
                        if (handler != null) {
                            sendMessage(handler, Message.obtain(message));
                        }
                    }
                }
            }
        }

        message.recycle();
    }

    private void sendMessage(Handler handler, Message message) {
        message.setTarget(handler);
        message.sendToTarget();
    }

    /**
     * Broadcast the supplicant state change event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param networkId ID of the network in softap.
     * @param bssid BSSID of the access point.
     * @param newSupplicantState New supplicant state.
     */
    public void broadcastHostapdConnectionEvent(String iface) {
        sendMessage(iface, SOFTAP_HOSTAPD_CONNECTION_EVENT);
    }
    public void broadcastHostapdDisconnectionEvent(String iface) {
        sendMessage(iface, SOFTAP_HOSTAPD_DISCONNECTION_EVENT);
    }

    /**
     * Broadcast the connection to softap event to all the handlers registered for
     * this event.
     *
     * @param iface Name of iface on which this occurred.
     */
    public void broadcastSoftapConnectionEvent(String iface,String mac) {
        sendMessage(iface, SOFTAP_STA_CONNECTED_EVENT,mac);
    }

    /**
     * Broadcast the loss of connection to softap event to all the handlers registered for
     * this event.
     *
     * @param iface Name of iface on which this occurred.
     */
    public void broadcastSoftapDisconnectionEvent(String iface,String mac) {
        sendMessage(iface, SOFTAP_STA_DISCONNECTED_EVENT,mac);
    }

    public synchronized void unregisterHandler(String iface, int what, Handler handler) {
        SparseArray<Set<Handler>> ifaceHandlers = mHandlerMap.get(iface);
        if (ifaceHandlers == null) {
            return;
        }
        Set<Handler> ifaceWhatHandlers = ifaceHandlers.get(what);
        if (ifaceWhatHandlers == null) {
            return;
        }
        ifaceWhatHandlers.remove(handler);
    }

}
