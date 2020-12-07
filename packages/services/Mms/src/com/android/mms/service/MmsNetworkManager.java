/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.mms.service;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import android.os.Message;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
// add for Mms over wifi
import com.android.mms.service.vowifi.ConnectivityManagerEx;
import com.android.mms.service.vowifi.VowifiNetwork;
import android.content.Context;
import android.provider.Settings;
import android.telephony.SmsManagerEx;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.util.Log;
/* Add for bug 542996 {@ */
/* @} */
import com.android.mms.service.exception.MmsNetworkException;

/**
 * Manages the MMS network connectivity
 */
public class MmsNetworkManager {
    // Timeout used to call ConnectivityManager.requestNetwork
    // Given that the telephony layer will retry on failures, this timeout should be high enough.
    private static final int NETWORK_REQUEST_TIMEOUT_MILLIS = 3 * 60 * 1000;// modify for bug 745864
    // Wait timeout for this class, a little bit longer than the above timeout
    // to make sure we don't bail prematurely
    private static final int NETWORK_ACQUIRE_TIMEOUT_MILLIS =
            NETWORK_REQUEST_TIMEOUT_MILLIS + (5 * 1000);
    // Waiting time used before releasing a network prematurely. This allows the MMS download
    // acknowledgement messages to be sent using the same network that was used to download the data
    private static final int NETWORK_RELEASE_TIMEOUT_MILLIS = 5 * 1000;

    private final Context mContext;
    // add for Mms over wifi Begin
    private volatile ConnectivityManagerEx mConnectivityManagerEx;
    private VowifiNetwork mVowifiNetwork;
    private MmsHttpClient mMmsHttpClientEx;
    private final NetworkRequest mNetworkRequestEx; 
    private int mMmsRequestCountEx;
    private final Runnable mNetworkReleaseTask;
    private ConnectivityManagerEx.VowifiNetworkCallback mwifiNetworkCallback;
    // add for Mms over wifi End
    // The requested MMS {@link android.net.Network} we are holding
    // We need this when we unbind from it. This is also used to indicate if the
    // MMS network is available.
    private Network mNetwork;
    // The current count of MMS requests that require the MMS network
    // If mMmsRequestCount is 0, we should release the MMS network.
    private int mMmsRequestCount;
    // This is really just for using the capability
    private final NetworkRequest mNetworkRequest;
    // The callback to register when we request MMS network
    private ConnectivityManager.NetworkCallback mNetworkCallback;

    private volatile ConnectivityManager mConnectivityManager;

    // The MMS HTTP client for this network
    private MmsHttpClient mMmsHttpClient;

    // The handler used for delayed release of the network
    private final Handler mReleaseHandler;

    // The task that does the delayed releasing of the network.
    private final Runnable mWifiNetworkReleaseTask;

    // The SIM ID which we use to connect
    private final int mSubId;

    /**
     * Network callback for our wifi network request
     */
    private class VowifiNetworkRequestCallback extends ConnectivityManagerEx.VowifiNetworkCallback {
        @Override
        public void onAvailable(VowifiNetwork network) {
            super.onAvailable(network);
            LogUtil.d("VowifiNetworkCallbackListener.onAvailable: network=" + network +
                                  ", mSubId=" + mSubId);
            synchronized (MmsNetworkManager.this) {
                if (mwifiNetworkCallback !=null) {
                    mVowifiNetwork = network;
                }
                MmsNetworkManager.this.notifyAll();
            }
        }

        @Override
        public void onLost(VowifiNetwork network) {
            super.onLost(network);
            LogUtil.d("VowifiNetworkCallbackListener.onLost: network=" + network +
                                  ", mSubId=" + mSubId);
            synchronized (MmsNetworkManager.this) {
                MmsNetworkManager.this.notifyAll();
                releaseRequestLockedEx(this);
            }
        }

        @Override
        public void onUnavailable() {
            super.onUnavailable();
            LogUtil.d("VowifiNetworkCallbackListener.onUnavailable" + ", mSubId=" + mSubId);
            synchronized (MmsNetworkManager.this) {
                MmsNetworkManager.this.notifyAll();
                releaseRequestLockedEx(this);
                }
            }
        }

    /**
     * Network callback for our network request
     */
    private class NetworkRequestCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            LogUtil.i("NetworkCallbackListener.onAvailable: network=" + network);
            synchronized (MmsNetworkManager.this) {
                mNetwork = network;
                MmsNetworkManager.this.notifyAll();
            }
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            LogUtil.w("NetworkCallbackListener.onLost: network=" + network);
            synchronized (MmsNetworkManager.this) {
                releaseRequestLocked(this);
                MmsNetworkManager.this.notifyAll();
            }
        }

        @Override
        public void onUnavailable() {
            super.onUnavailable();
            LogUtil.w("NetworkCallbackListener.onUnavailable");
            synchronized (MmsNetworkManager.this) {
                releaseRequestLocked(this);
                MmsNetworkManager.this.notifyAll();
            }
        }
    }

    public MmsNetworkManager(Context context, int subId) {
        mContext = context;
        mNetworkCallback = null;
        mNetwork = null;
        mMmsRequestCount = 0;
        mConnectivityManager = null;
        mMmsHttpClient = null;
        mVowifiNetwork=null;
        mConnectivityManagerEx=ConnectivityManagerEx.from(context);// add for Mms over wifi
        mMmsHttpClientEx=null;
        mMmsRequestCountEx = 0;
        mReleaseHandler = new Handler(Looper.getMainLooper());
        mSubId = subId;
        mNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                .setNetworkSpecifier(Integer.toString(mSubId))
                .build();
        // for MMS over ePDG demo: use internet over wifi
         mNetworkRequestEx = new NetworkRequest.Builder()
                  .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                  .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                  .setNetworkSpecifier(Integer.toString(mSubId))
                  .build();

        mNetworkReleaseTask = new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    if (mMmsRequestCount < 1) {
                        releaseRequestLocked(mNetworkCallback);
                }
            }
        }
    };
        mWifiNetworkReleaseTask = new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    if (mMmsRequestCountEx < 1) {
                        releaseRequestLockedEx(mwifiNetworkCallback);
                }
            }
        }
    };
    }

    /**
     * Acquire the MMS network of vowifi
     *
     * @param requestId request ID for logging
     * @throws com.android.mms.service.exception.MmsNetworkException if we fail to acquire it
     */
    public void acquireNetworkEx(final String requestId) throws MmsNetworkException {
        synchronized (this) {
            // Since we are acquiring the network, remove the network release task if exists.
            mReleaseHandler.removeCallbacks(mWifiNetworkReleaseTask);
            mMmsRequestCountEx += 1;
            if (mVowifiNetwork != null) {
                // Already available
                LogUtil.d(requestId, "MmsNetworkManager:  WifiNetwork already available");
                return;
            }
            // Not available, so start a new request if not done yet
            if (mwifiNetworkCallback== null) {
                LogUtil.d(requestId, "MmsNetworkManager: start new WifiNetwork request");
                startNewNetworkRequestLockedEx();
            }
            final long shouldEnd = SystemClock.elapsedRealtime() + NETWORK_ACQUIRE_TIMEOUT_MILLIS;
            long waitTime = NETWORK_ACQUIRE_TIMEOUT_MILLIS;
            while (waitTime > 0) {
                try {
                    this.wait(waitTime);
                } catch (InterruptedException e) {
                    LogUtil.w(requestId, "MmsNetworkManager: acquire WifiNetwork wait interrupted");
                }
                if (mVowifiNetwork != null) {
                    // Success
                    return;
                }
                // Calculate remaining waiting time to make sure we wait the full timeout period
                waitTime = shouldEnd - SystemClock.elapsedRealtime();
            }
            // Timed out, so release the request and fail
            LogUtil.e(requestId, "MmsNetworkManager: wifi timed out");
            releaseRequestLockedEx(mwifiNetworkCallback);
            throw new MmsNetworkException("Acquiring WifiNetwork timed out");
            }
        } 
    /**
     * Acquire the MMS network
     *
     * @param requestId request ID for logging
     * @throws com.android.mms.service.exception.MmsNetworkException if we fail to acquire it
     */
    public void acquireNetwork(final String requestId) throws MmsNetworkException {
        synchronized (this) {
            // Since we are acquiring the network, remove the network release task if exists.
            mReleaseHandler.removeCallbacks(mNetworkReleaseTask);
            mMmsRequestCount += 1;
            if (mNetwork != null) {
                // Already available
                LogUtil.d(requestId, "MmsNetworkManager: already available");
                return;
            }
            // Not available, so start a new request if not done yet
            if (mNetworkCallback == null) {
                LogUtil.d(requestId, "MmsNetworkManager: start new network request");
                startNewNetworkRequestLocked();
            }
            final long shouldEnd = SystemClock.elapsedRealtime() + NETWORK_ACQUIRE_TIMEOUT_MILLIS;
            long waitTime = NETWORK_ACQUIRE_TIMEOUT_MILLIS;
            while (waitTime > 0) {
                try {
                    this.wait(waitTime);
                } catch (InterruptedException e) {
                    LogUtil.w(requestId, "MmsNetworkManager: acquire network wait interrupted");
                }
                if (mNetwork != null) {
                    // Success
                    return;
                }
                // Calculate remaining waiting time to make sure we wait the full timeout period
                waitTime = shouldEnd - SystemClock.elapsedRealtime();
            }
            // Timed out, so release the request and fail
            LogUtil.e(requestId, "MmsNetworkManager: timed out");
            releaseRequestLocked(mNetworkCallback);
            throw new MmsNetworkException("Acquiring network timed out");
        }
    }

    /**
     * Release the MMS network when nobody is holding on to it.
     *
     * @param requestId request ID for logging
     * @param shouldDelayRelease whether the release should be delayed for 5 seconds, the regular
     *                           use case is to delay this for DownloadRequests to use the network
     *                           for sending an acknowledgement on the same network
     */
    public void releaseNetwork(final String requestId, final boolean shouldDelayRelease) {
        synchronized (this) {
            if (mMmsRequestCount > 0) {
                mMmsRequestCount -= 1;
                LogUtil.d(requestId, "MmsNetworkManager: release, count=" + mMmsRequestCount);
                if (mMmsRequestCount < 1) {
                    if (shouldDelayRelease) {
                        // remove previously posted task and post a delayed task on the release
                        // handler to release the network
                        mReleaseHandler.removeCallbacks(mNetworkReleaseTask);
                        mReleaseHandler.postDelayed(mNetworkReleaseTask,
                                NETWORK_RELEASE_TIMEOUT_MILLIS);
                    } else {
                        releaseRequestLocked(mNetworkCallback);
                    }
                }
            }
        }
    }
    /**
     * Release the MMS network over wifi when nobody is holding on to it.
     *
     * @param requestId request ID for logging
     * @param shouldDelayRelease whether the release should be delayed for 5 seconds, the regular
     *                           use case is to delay this for DownloadRequests to use the network
     *                           for sending an acknowledgement on the same network
     */
    public void releaseNetworkEx(final String requestId, final boolean shouldDelayRelease) {
        synchronized (this) {
            if (mMmsRequestCountEx > 0) {
                mMmsRequestCountEx -= 1;
                LogUtil.d(requestId, "MmsNetworkManager:  wifiNetwork release, count=" + mMmsRequestCountEx);
                if (mMmsRequestCountEx < 1) {
                    if (shouldDelayRelease) {
                        // remove previously posted task and post a delayed task on the release
                        // handler to release the network
                        mReleaseHandler.removeCallbacks(mWifiNetworkReleaseTask);
                        mReleaseHandler.postDelayed(mWifiNetworkReleaseTask,
                                NETWORK_RELEASE_TIMEOUT_MILLIS);
                    } else {
                        releaseRequestLockedEx(mwifiNetworkCallback);
                    }
                }
            }
        }
    }

    /**
     * Start a new {@link android.net.NetworkRequest} for MMS
     */
    private void startNewNetworkRequestLocked() {
        final ConnectivityManager connectivityManager = getConnectivityManager();
        mNetworkCallback = new NetworkRequestCallback();
        connectivityManager.requestNetwork(
                mNetworkRequest, mNetworkCallback, NETWORK_REQUEST_TIMEOUT_MILLIS);
    }
    /* Modify by SPRD for bug 542996 End */
    /**
     * Start a new {@link android.net.NetworkRequest} for MMS over wifi
     */
    private void startNewNetworkRequestLockedEx() {
        final ConnectivityManagerEx connectivityManagerEx = getConnectivityManagerEx();
        mwifiNetworkCallback = new VowifiNetworkRequestCallback();
        LogUtil.d("connectivityManagerEx.requestwifiNetwork");
        connectivityManagerEx.requestImsNetwork(
                mNetworkRequestEx, mwifiNetworkCallback, mSubId);
    }

    /**
     * Release the current {@link android.net.NetworkRequest} for MMS
     *
     * @param callback the {@link android.net.ConnectivityManager.NetworkCallback} to unregister
     */
    private void releaseRequestLocked(ConnectivityManager.NetworkCallback callback) {
        if (callback != null) {
            final ConnectivityManager connectivityManager = getConnectivityManager();
            try {
                connectivityManager.unregisterNetworkCallback(callback);
            } catch (IllegalArgumentException e) {
                // It is possible ConnectivityManager.requestNetwork may fail silently due
                // to RemoteException. When that happens, we may get an invalid
                // NetworkCallback, which causes an IllegalArgumentexception when we try to
                // unregisterNetworkCallback. This exception in turn causes
                // MmsNetworkManager to skip resetLocked() in the below. Thus MMS service
                // would get stuck in the bad state until the device restarts. This fix
                // catches the exception so that state clean up can be executed.
                LogUtil.w("Unregister network callback exception", e);
            }
        }
        resetLocked();
    }
    /**
     * Release the current {@link android.net.NetworkRequest} for MMS over wifi
     *
     * @param callback the {@link android.net.ConnectivityManager.NetworkCallback} to unregister
     */
    private void releaseRequestLockedEx(ConnectivityManagerEx.VowifiNetworkCallback callback) {
        if (callback != null) {
            final ConnectivityManagerEx connectivityManagerEx = getConnectivityManagerEx();
            try {
                connectivityManagerEx.unregisterImsNetworkCallback(callback,mSubId);
            } catch (IllegalArgumentException e) {
                // It is possible ConnectivityManager.requestNetwork may fail silently due
                // to RemoteException. When that happens, we may get an invalid
                // NetworkCallback, which causes an IllegalArgumentexception when we try to
                // unregisterNetworkCallback. This exception in turn causes
                // MmsNetworkManager to skip resetLocked() in the below. Thus MMS service
                // would get stuck in the bad state until the device restarts. This fix
                // catches the exception so that state clean up can be executed.
                LogUtil.w("Unregister network callback exception", e);
            }
        }
        resetLockedEx();
    }
    /**
     * Reset the state
     */
    private void resetLocked() {
        mNetworkCallback = null;
        mNetwork = null;
        mMmsRequestCount = 0;
        mMmsHttpClient = null;
    }
    /**
     * Reset the state
     */
    private void resetLockedEx() {
        mwifiNetworkCallback= null;
        mVowifiNetwork= null;
        mMmsRequestCountEx = 0;
        mMmsHttpClientEx = null;
    }

    private ConnectivityManager getConnectivityManager() {
        if (mConnectivityManager == null) {
            mConnectivityManager = (ConnectivityManager) mContext.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
        }
        return mConnectivityManager;
    }
    private ConnectivityManagerEx getConnectivityManagerEx() {
        if (mConnectivityManagerEx == null) {
            mConnectivityManagerEx=ConnectivityManagerEx.from(mContext);
        }
        return mConnectivityManagerEx;
    }

        /**
     * Get an MmsHttpClient for the current wifi network
     *
     * @return The MmsHttpClient instance
     */
    public MmsHttpClient getOrCreateHttpClientEx() {
        synchronized (this) {
            if (mMmsHttpClientEx == null) {
                if (mVowifiNetwork != null) {
                    // Create new MmsHttpClient for the current Network
                    mMmsHttpClientEx = new MmsHttpClient(mContext, mVowifiNetwork, mConnectivityManagerEx);
                }
            }
            return mMmsHttpClientEx;
        }
    }
    /**
     * Get an MmsHttpClient for the current network
     *
     * @return The MmsHttpClient instance
     */
    public MmsHttpClient getOrCreateHttpClient() {
        synchronized (this) {
            if (mMmsHttpClient == null) {
                if (mNetwork != null) {
                    // Create new MmsHttpClient for the current Network
                    mMmsHttpClient = new MmsHttpClient(mContext, mNetwork, mConnectivityManager);
                }
            }
            return mMmsHttpClient;
        }
    }

    /**
     * Get the APN name for the active network
     *
     * @return The APN name if available, otherwise null
     */
    public String getApnName() {
        Network network = null;
        synchronized (this) {
            if (mNetwork == null) {
                return null;
            }
            network = mNetwork;
        }
        String apnName = null;
        final ConnectivityManager connectivityManager = getConnectivityManager();
        final NetworkInfo mmsNetworkInfo = connectivityManager.getNetworkInfo(network);
        if (mmsNetworkInfo != null) {
            apnName = mmsNetworkInfo.getExtraInfo();
        }
        return apnName;
    }
      public  boolean isVowifiSmsEnable(int subId) {
        boolean isVowifiConnected = SmsManagerEx.getDefault().getIsVowifiConnected();
        //int primaryPhoneId = SmsManagerEx.getDefault().getPrimaryCardPhoneId();
        int primaryPhoneId= SubscriptionManager.from(mContext).getDefaultDataPhoneId();
        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        //int phoneId = tm.getPhoneId(subId);
           int phoneId = SubscriptionManager.getPhoneId(subId);
        Log.d("MmsNetworkManager", "isVowifiSmsEnable subId = " + subId + ", phoneId = " + phoneId
                + ", primaryPhoneId =" + primaryPhoneId + ", isVowifiConnected = "
                + isVowifiConnected);
        return ((phoneId == primaryPhoneId) && isVowifiConnected);
    }
}
