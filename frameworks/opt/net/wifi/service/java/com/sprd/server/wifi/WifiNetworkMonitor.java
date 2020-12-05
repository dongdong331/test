/*
 * Copyright (C) 2016 Spreadtrum.com
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


import static android.net.ConnectivityManager.TYPE_WIFI;

import android.content.Context;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkAgent;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.ProxyInfo;
import android.net.wifi.IWifiNetworkEventObserver;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.Uri;


import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;

import android.text.TextUtils;
import android.util.LocalLog;
import android.util.Log;

import com.android.internal.util.StateMachine;
import com.android.server.connectivity.NetworkAgentInfo;


import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.util.List;


public class WifiNetworkMonitor
 {

    private static String TAG = "WifiNetworkMonitor";
    private boolean DBG = true;
    private final Context mContext;
    private final StateMachine mStateMachine;
    private final HandlerThread mHandlerThread;
    private final InternalHandler mHandler;
    private ConnectivityManager mCm;

    /**
     * The link properties of the wifi network.
     * Do not modify this directly; use updateLinkProperties instead.
     */
    private LinkProperties mLinkProperties;

    private final RemoteCallbackList<IWifiNetworkEventObserver> mObservers =
            new RemoteCallbackList<IWifiNetworkEventObserver>();

    private NetworkInfo.DetailedState mCurrentNetworkState;
    private WifiInfo mWifiInfo;

    private int mReevaluateDelayMs;
    private int mReevaluateToken = 0;
    private boolean mConnectedNotified = false;
    private boolean mHasInternetAccess = false;
    private boolean mNeedCheckInternetAccess = true;
    private static enum InternalNetworkState{DISCONNECTED, CONNECTING, CONNECTED};
    private InternalNetworkState mInternalNetworkState = InternalNetworkState.DISCONNECTED;

    private static final int INITIAL_REEVALUATE_DELAY_MS = 1000;
    private static final int MAX_REEVALUATE_DELAY_MS = 10*60*1000;

    private static final String DEFAULT_SERVER = "connectivitycheck.gstatic.com";
    private static final int SOCKET_TIMEOUT_MS = 2000;

    private static final int EVENT_WIFI_NETWORK_CONNECTED  = 0;
    private static final int EVENT_WIFI_NETWORK_CONNECTING = 1;
    private static final int EVENT_WIFI_NETWORK_DISCONNECTED  = 2;
    private static final int EVENT_WIFI_NETWORK_LINKPROPERTY_CHANGED  = 3;
    private static final int EVENT_WIFI_RSSI_CHANGED  = 4;
    private static final int EVENT_WIFI_NETWORK_REEVALUATE  = 5;
    private static final int EVENT_WIFI_PRE_CLOSE = 6;
    private static final int EVENT_WIFI_NETWORK_VALIDATION  = 7;

    public WifiNetworkMonitor(Context context, StateMachine stateMachine) {
        mContext = context;
        mStateMachine = stateMachine;

        mCurrentNetworkState = DetailedState.DISCONNECTED;

        mHandlerThread = new HandlerThread("WifiNetworkMonitor");

        mHandlerThread.start();
        mHandler = new InternalHandler(mHandlerThread.getLooper());

    }

    public void updateNetworkState(WifiInfo wifiInfo,
                                   NetworkInfo.DetailedState state,
                                   int wifiState) {
        if (mHandler == null || mCurrentNetworkState == state) return;

        mCurrentNetworkState = state;

        if (DetailedState.DISCONNECTED == state) {
            mHandler.sendMessage(mHandler.obtainMessage
                            (EVENT_WIFI_NETWORK_DISCONNECTED,
                                    wifiState, 0, new WifiInfo(wifiInfo)));

        } else if (DetailedState.CONNECTED == state) {
            mHandler.sendMessage(mHandler.obtainMessage
                            (EVENT_WIFI_NETWORK_CONNECTED, new WifiInfo(wifiInfo)));

        } else if (DetailedState.CONNECTING == state) {
            mHandler.sendMessage(mHandler.obtainMessage
                            (EVENT_WIFI_NETWORK_CONNECTING, new WifiInfo(wifiInfo)));
        }
    }

    public void updateNetworkStatus(WifiInfo wifiInfo, int status) {
        if (mHandler == null) return;
        mHandler.sendMessage(mHandler.obtainMessage
                        (EVENT_WIFI_NETWORK_VALIDATION, new WifiInfo(wifiInfo)));
    }

    public void updateRssi(int rssi) {
        mHandler.sendMessage(mHandler.obtainMessage
                        (EVENT_WIFI_RSSI_CHANGED, rssi, 0));
    }

    public void updateLinkProperties(LinkProperties lp) {
        mLinkProperties = new LinkProperties(lp);
        /*
        mHandler.sendMessage(mHandler.obtainMessage
                        (EVENT_WIFI_NETWORK_LINKPROPERTY_CHANGED, mLinkProperties));
        */
    }

    public void notifyWifiStateChange(boolean enable) {
        if (!enable) {
            mHandler.sendEmptyMessage(EVENT_WIFI_PRE_CLOSE);
        }
    }

    public boolean registerObserver(IWifiNetworkEventObserver observer) {
        Log.d(TAG, "Register Obervers!!!");

        mObservers.register(observer);
        return true;
    }

    public boolean unregisterObserver(IWifiNetworkEventObserver observer) {
        mObservers.unregister(observer);
        return true;
    }


    public boolean isNetworkValidForVoWifi() {
        if (mNeedCheckInternetAccess)
            return mHasInternetAccess;
        else
            return (DetailedState.CONNECTED == mCurrentNetworkState);
    }

    public boolean setCheckingInternetForVoWifi(boolean enabled) {
        Log.d(TAG, "setCheckingInternetForVoWifi: " + enabled);
        mNeedCheckInternetAccess = enabled;
        return true;
    }

    private void notifyWifiPreClose() {
        final int length = mObservers.beginBroadcast();

        if (length == 0) Log.e(TAG, "Not any Obervers!!!");

        try {
            for (int i = 0; i < length; i++) {
                try {
                    mObservers.getBroadcastItem(i).onWifiPreClose();
                } catch (RemoteException e) {
                } catch (RuntimeException e) {
                }
            }
        } finally {
            mObservers.finishBroadcast();
        }
    }

    /**
     * Notify our observers of wifi connected
     */
    private void notifyWifiNetworkConnected(WifiInfo wifiInfo, boolean trustNetwork) {
        final int length = mObservers.beginBroadcast();

        if (length == 0) Log.e(TAG, "Not any Obervers!!!");

        try {
            for (int i = 0; i < length; i++) {
                try {
                    mObservers.getBroadcastItem(i).onWifiNetworkConnected(wifiInfo, trustNetwork);
                } catch (RemoteException e) {
                } catch (RuntimeException e) {
                }
            }
        } finally {
            mObservers.finishBroadcast();
        }
    }


    private void notifyWifiNetworkDisconnected(WifiInfo wifiInfo, boolean trustNetwork, boolean disable) {
        final int length = mObservers.beginBroadcast();
        Log.e(TAG, "notifyWifiNetworkDisconnected disable = " + disable);

        if (length == 0) Log.e(TAG, "Not any Obervers!!!");

        try {
            for (int i = 0; i < length; i++) {
                try {
                    mObservers.getBroadcastItem(i).onWifiNetworkDisconnected(wifiInfo, trustNetwork, disable);
                } catch (RemoteException e) {
                } catch (RuntimeException e) {
                }
            }
        } finally {
            mObservers.finishBroadcast();
        }
    }
    private void notifyWifiNetworkConnecting(WifiInfo wifiInfo, boolean trustNetwork) {
        final int length = mObservers.beginBroadcast();

        if (length == 0) Log.e(TAG, "Not any Obervers!!!");

        try {
            for (int i = 0; i < length; i++) {
                try {
                    mObservers.getBroadcastItem(i).onWifiNetworkConnecting(wifiInfo, trustNetwork);
                } catch (RemoteException e) {
                } catch (RuntimeException e) {
                }
            }
        } finally {
            mObservers.finishBroadcast();
        }
    }

    private void notifyRssiChanged(int rssi) {
        final int length = mObservers.beginBroadcast();
        if (length == 0) Log.e(TAG, "Not any Obervers!!!");

        try {
            for (int i = 0; i < length; i++) {
                try {
                    mObservers.getBroadcastItem(i).onRssiChanged(rssi);
                } catch (RemoteException e) {
                } catch (RuntimeException e) {
                }
            }
        } finally {
            mObservers.finishBroadcast();
        }
    }

    private void notifyLinkPropertiesChanged(LinkProperties linkProperties) {
        final int length = mObservers.beginBroadcast();
        if (length == 0) Log.e(TAG, "Not any Obervers!!!");

        try {
            for (int i = 0; i < length; i++) {
                try {
                    mObservers.getBroadcastItem(i).notifyLinkPropertiesChanged(linkProperties);
                } catch (RemoteException e) {
                } catch (RuntimeException e) {
                }
            }
        } finally {
            mObservers.finishBroadcast();
        }
    }

    private void checkAndSetConnectivityInstance() {
        if (mCm == null) {
            mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        }
    }

    /**
     * Do a URL fetch on a known server to see if we get the data we expect.
     * Returns true for this network has a internet connection.
     */
    private boolean isValidated() {
        HttpURLConnection urlConnection = null;
        int httpResponseCode = 204;
        boolean hostNameResolved = false;
        boolean validated = false;

        checkAndSetConnectivityInstance();

        Network wifNetwork = mCm.getNetworkForType(TYPE_WIFI);
        if (wifNetwork == null) {
            Log.e(TAG, "null Network for TYPE_WIFI!!!");
            return false;
        }

        try {
            URL url = new URL("http", DEFAULT_SERVER, "/generate_204");
            // On networks with a PAC instead of fetching a URL that should result in a 204
            // reponse, we instead simply fetch the PAC script.  This is done for a few reasons:
            // 1. At present our PAC code does not yet handle multiple PACs on multiple networks
            //    until something like https://android-review.googlesource.com/#/c/115180/ lands.
            //    Network.openConnection() will ignore network-specific PACs and instead fetch
            //    using NO_PROXY.  If a PAC is in place, the only fetch we know will succeed with
            //    NO_PROXY is the fetch of the PAC itself.
            // 2. To proxy the generate_204 fetch through a PAC would require a number of things
            //    happen before the fetch can commence, namely:
            //        a) the PAC script be fetched
            //        b) a PAC script resolver service be fired up and resolve mServer
            //    Network validation could be delayed until these prerequisities are satisifed or
            //    could simply be left to race them.  Neither is an optimal solution.
            // 3. PAC scripts are sometimes used to block or restrict Internet access and may in
            //    fact block fetching of the generate_204 URL which would lead to false negative
            //    results for network validation.
            boolean fetchPac = false;
            final ProxyInfo proxyInfo = mLinkProperties.getHttpProxy();
            if (proxyInfo != null && !Uri.EMPTY.equals(proxyInfo.getPacFileUrl())) {
                url = new URL(proxyInfo.getPacFileUrl().toString());
                fetchPac = true;
            }
            final StringBuffer connectInfo = new StringBuffer();
            String hostToResolve = null;
            // Only resolve a host if HttpURLConnection is about to, to avoid any potentially
            // unnecessary resolution.
            if (proxyInfo == null || fetchPac) {
                hostToResolve = url.getHost();
            } else if (proxyInfo != null) {
                hostToResolve = proxyInfo.getHost();
            }
            if (!TextUtils.isEmpty(hostToResolve)) {
                connectInfo.append(", " + hostToResolve + "=");
                final InetAddress[] addresses =
                        wifNetwork.getAllByName(hostToResolve);
                for (InetAddress address : addresses) {
                    connectInfo.append(address.getHostAddress());
                    if (address != addresses[addresses.length-1]) connectInfo.append(",");
                    hostNameResolved = true;
                }
            }
            Log.d(TAG, "Checking " + url.toString() + " on " +
                    wifNetwork + connectInfo);
            urlConnection = (HttpURLConnection) wifNetwork.openConnection(url);
            urlConnection.setInstanceFollowRedirects(fetchPac);
            urlConnection.setConnectTimeout(SOCKET_TIMEOUT_MS);
            urlConnection.setReadTimeout(SOCKET_TIMEOUT_MS);
            urlConnection.setUseCaches(false);

            // Time how long it takes to get a response to our request
            long requestTimestamp = SystemClock.elapsedRealtime();

            urlConnection.getInputStream();

            // Time how long it takes to get a response to our request
            long responseTimestamp = SystemClock.elapsedRealtime();

            httpResponseCode = urlConnection.getResponseCode();
            Log.d(TAG, "isValidated: ret=" + httpResponseCode +
                    " headers=" + urlConnection.getHeaderFields());
            // NOTE: We may want to consider an "HTTP/1.0 204" response to be a captive
            // portal.  The only example of this seen so far was a captive portal.  For
            // the time being go with prior behavior of assuming it's not a captive
            // portal.  If it is considered a captive portal, a different sign-in URL
            // is needed (i.e. can't browse a 204).  This could be the result of an HTTP
            // proxy server.

            // Consider 200 response with "Content-length=0" to not be a captive portal.
            // There's no point in considering this a captive portal as the user cannot
            // sign-in to an empty page.  Probably the result of a broken transparent proxy.
            // See http://b/9972012.
            if (httpResponseCode == 200 && urlConnection.getContentLength() == 0) {
                Log.d(TAG, "Empty 200 response interpreted as 204 response.");
                httpResponseCode = 204;
            }

            if (httpResponseCode == 200 && fetchPac) {
                Log.d(TAG, "PAC fetch 200 response interpreted as 204 response.");
                httpResponseCode = 204;
            }

        } catch (UnknownHostException e1) {
            Log.d(TAG, "Probably not a portal: exception " + e1);
            httpResponseCode = 599; //indicate has not internet connection
        } catch (SocketTimeoutException e2) {
            Log.d(TAG, "Probably not a portal: exception " + e2);
            if (hostNameResolved) {
                httpResponseCode = 204;
            }
        } catch (IOException e) {
            Log.d(TAG, "Probably not a portal: exception " + e);
            //host Name is resolved, that means this network is actived.
            if (hostNameResolved) {
                httpResponseCode = 204;
            }

            if (httpResponseCode == 599) {
                // TODO: Ping gateway and DNS server and log results.
            }
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        if (httpResponseCode == 204) {
            validated = true;
        } else if (httpResponseCode >= 200 && httpResponseCode <= 399) {
            Log.d(TAG, "Probably a portal, Need to login");
            validated = false;
        } else {
            Log.d(TAG, "Probably has not Internet Connection!!!");
            validated = false;
        }

        return validated;
    }




   private class InternalHandler extends Handler {
        public InternalHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_WIFI_NETWORK_CONNECTED:
                    if (DBG) Log.d(TAG, "EVENT_WIFI_NETWORK_CONNECTED");
                    mWifiInfo = (WifiInfo)msg.obj;

                    if (!mNeedCheckInternetAccess) {
                        if (DBG) Log.d(TAG, "Don't need check Internet access!!!");
                        if (mInternalNetworkState != InternalNetworkState.CONNECTED) {
                            notifyWifiNetworkConnected(mWifiInfo, false);
                            //mConnectedNotified = true;
                            mInternalNetworkState = InternalNetworkState.CONNECTED;
                        }
                        break;
                    }

                    /*mReevaluateDelayMs = INITIAL_REEVALUATE_DELAY_MS;
                    if (isValidated()) {
                        if (DBG) Log.d(TAG, "Validated!!!");
                        mHasInternetAccess = true;

                        if (mInternalNetworkState != InternalNetworkState.CONNECTED) {
                            notifyWifiNetworkConnected(mWifiInfo, false);
                            //mConnectedNotified = true;
                            mInternalNetworkState = InternalNetworkState.CONNECTED;
                        }
                    } else {
                        final Message newMsg = mHandler.obtainMessage(EVENT_WIFI_NETWORK_REEVALUATE, ++mReevaluateToken, 0);
                        sendMessageDelayed(newMsg, mReevaluateDelayMs);
                        mReevaluateDelayMs *= 2;
                        if (mReevaluateDelayMs > MAX_REEVALUATE_DELAY_MS) {
                            mReevaluateDelayMs = MAX_REEVALUATE_DELAY_MS;
                        }
                    }*/

                    break;
                case EVENT_WIFI_NETWORK_VALIDATION:
                     if (DBG) Log.d(TAG, "EVENT_WIFI_NETWORK_VALIDATION");
                     mWifiInfo = (WifiInfo)msg.obj;
                     mHasInternetAccess = true;

                     if (mInternalNetworkState != InternalNetworkState.CONNECTED) {
                         notifyWifiNetworkConnected(mWifiInfo, false);
                         mInternalNetworkState = InternalNetworkState.CONNECTED;
                     }

                    break;
                case EVENT_WIFI_NETWORK_DISCONNECTED:
                    if (DBG) Log.d(TAG, "EVENT_WIFI_NETWORK_DISCONNECTED");
                    WifiInfo wifiInfo = (WifiInfo)msg.obj;
                    boolean disable = msg.arg1 != WifiManager.WIFI_STATE_ENABLED;

                    if (mInternalNetworkState != InternalNetworkState.DISCONNECTED) {
                        notifyWifiNetworkDisconnected(wifiInfo, false, disable);
                        //mConnectedNotified = false;
                        mInternalNetworkState = InternalNetworkState.DISCONNECTED;
                    }

                    mHasInternetAccess = false;
                    mReevaluateToken = 0; //clear when disconnect

                    //need to clear ??
                    if (mWifiInfo != null) mWifiInfo.reset();

                    break;

                case EVENT_WIFI_NETWORK_CONNECTING:
                    if (DBG) Log.d(TAG, "EVENT_WIFI_NETWORK_CONNECTING");
                    //WifiInfo mWifiInfo = (WifiInfo)msg.obj;
                    if (mInternalNetworkState != InternalNetworkState.CONNECTING) {
                        notifyWifiNetworkConnecting(null, false);
                        mInternalNetworkState = InternalNetworkState.CONNECTING;
                    }
                    break;

                case EVENT_WIFI_NETWORK_LINKPROPERTY_CHANGED:
                    if (DBG) Log.d(TAG, "EVENT_WIFI_NETWORK_LINKPROPERTY_CHANGED");

                    LinkProperties linkProperties = (LinkProperties)msg.obj;
                    //Need notify only when connected ??
                    if(mInternalNetworkState == InternalNetworkState.CONNECTED) {
                        notifyLinkPropertiesChanged(linkProperties);
                    }
                    break;

                case EVENT_WIFI_RSSI_CHANGED:
                    if (DBG) Log.d(TAG, "EVENT_WIFI_RSSI_CHANGED");
                    int rssi = msg.arg1;
                    //Only when connected state, notify RSSI.
                    if (mInternalNetworkState == InternalNetworkState.CONNECTED)
                        notifyRssiChanged(rssi);
                    break;

                case EVENT_WIFI_NETWORK_REEVALUATE:
                    if (DBG) Log.d(TAG, "EVENT_WIFI_NETWORK_REEVALUATE");

                    if (msg.arg1 != mReevaluateToken) {
                        if (DBG) Log.d(TAG, "Stall REEVALUATE CMD, IGNORE!!!");
                        break;
                    }

                    if (!mNeedCheckInternetAccess) {
                        if (DBG) Log.d(TAG, "Don't need check Internet access!!!");
                        if (mInternalNetworkState != InternalNetworkState.CONNECTED && (DetailedState.CONNECTED == mCurrentNetworkState)) {
                            notifyWifiNetworkConnected(mWifiInfo, false);
                            //mConnectedNotified = true;
                            mInternalNetworkState = InternalNetworkState.CONNECTED;
                        }
                        break;
                    }

                    if (isValidated()) {
                        if (DBG) Log.d(TAG, "Validated!!!");
                        mHasInternetAccess = true;

                        if (mInternalNetworkState != InternalNetworkState.CONNECTED) {
                            notifyWifiNetworkConnected(mWifiInfo, false);
                            //mConnectedNotified = true;
                            mInternalNetworkState = InternalNetworkState.CONNECTED;
                        }
                    } else {
                        final Message newMsg = mHandler.obtainMessage(EVENT_WIFI_NETWORK_REEVALUATE, ++mReevaluateToken, 0);
                        sendMessageDelayed(newMsg, mReevaluateDelayMs);
                        mReevaluateDelayMs *= 2;
                        if (mReevaluateDelayMs > MAX_REEVALUATE_DELAY_MS) {
                            mReevaluateDelayMs = MAX_REEVALUATE_DELAY_MS;
                        }
                    }
                    break;
                case EVENT_WIFI_PRE_CLOSE:
                    if (DBG) Log.d(TAG, "EVENT_WIFI_PRE_CLOSE");
                    if (mInternalNetworkState == InternalNetworkState.CONNECTED)
                        notifyWifiPreClose();
                    break;
                default:
                    break;
            }
        }
    }

}
