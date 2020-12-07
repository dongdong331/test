package com.android.mms.service.vowifi;

import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.NetworkRequest;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectivityManagerEx {
    private static final String TAG = "ConnectivityManagerEx";

    private Context mContext;
    private ConnectivityServiceEx mService;

    private static final int REQUEST_ID_UNSET = 0;

    private static final HashMap<NetworkRequest, VowifiNetworkCallback> sNetworkCallback =
            new HashMap<NetworkRequest, VowifiNetworkCallback>();
    private static final AtomicInteger sCallbackRefCount = new AtomicInteger(0);

    private static CallbackHandler sCallbackHandler = null;
    private static ConnectivityManagerEx sInstance = null;

    private ConnectivityManagerEx(Context context) {
        mContext = context;
        mService = new ConnectivityServiceEx(context);
    }

    public static ConnectivityManagerEx from(Context context) {
        if (sInstance == null) {
            sInstance = new ConnectivityManagerEx(context);
        }
        return sInstance;
    }

    /**
     * Returns connection state and other information about a particular network type.
     *
     * @subId on which sub the VowifiNetwork is asked for
     * @return a {@link VowifiNetwork} object for the requested network type or {@code null} if the
     *         type is not supported by the device.
     */
    public VowifiNetwork getVowifiNetwork(int subId) {
        return mService.getVowifiNetwork(subId);
    }

    /**
     * Request a network to satisfy a set of {@link android.net.NetworkCapabilities}.
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param networkCallback The callbacks to be utilized for this request. Note the callbacks must
     *            not be shared - they uniquely specify this request. @ param subId The subId on
     *            which the request is initiated.
     */
    public void requestImsNetwork(NetworkRequest request, VowifiNetworkCallback networkCallback,
            int subId) {
        if (request == null || networkCallback == null) {
            throw new IllegalArgumentException("requestImsNetwork: request or callback is null.");
        }

        incCallbackHandlerRefCount();
        synchronized (sNetworkCallback) {
            networkCallback.networkRequest = mService.requestImsNetwork(request.networkCapabilities,
                    new Messenger(sCallbackHandler), new Binder(), subId);

            if (networkCallback.networkRequest != null) {
                sNetworkCallback.put(networkCallback.networkRequest, networkCallback);
            }
        }

        if (networkCallback.networkRequest == null) decCallbackHandlerRefCount();
    }

    /**
     * Unregisters callbacks about and possibly releases networks originating from
     * {@link #requestImsNetwork(NetworkRequest, NetworkCallback)} call. If the given
     * {@code NetworkCallback} had previously been used with {@code #requestImsNetwork}, any
     * networks that had been connected to only to satisfy that request will be disconnected.
     *
     * @param networkCallback The {@link NetworkCallback} used when making the request.
     */
    public void unregisterImsNetworkCallback(VowifiNetworkCallback networkCallback, int subId) {
        if (networkCallback == null || networkCallback.networkRequest == null
                || networkCallback.networkRequest.requestId == REQUEST_ID_UNSET) {
            throw new IllegalArgumentException("Invalid NetworkCallback");
        }
        mService.releaseImsNetworkRequest(networkCallback.networkRequest, subId);

        synchronized (sNetworkCallback) {
            sNetworkCallback.remove(networkCallback.networkRequest);
        }
    }

    /**
     * Base class for NetworkRequest callbacks. Used for notifications about network changes. Should
     * be extended by applications wanting notifications.
     */
    public static class VowifiNetworkCallback {

        /**
         * Called when the framework connects and has declared a new network ready for use. This
         * callback may be called more than once if the {@link Network} that is satisfying the
         * request changes.
         *
         * @param network The {@link Network} of the satisfying network.
         */
        public void onAvailable(VowifiNetwork networkInfo) {
        }

        /**
         * Called when the framework has a hard loss of the network or when the graceful failure
         * ends.
         *
         * @param network The {@link Network} lost.
         */
        public void onLost(VowifiNetwork networkInfo) {
        }

        /**
         * Called if no network is found in the given timeout time. If no timeout is given, this
         * will not be called.
         *
         * @hide
         */
        public void onUnavailable() {
        }

        private NetworkRequest networkRequest;
    }

    private class CallbackHandler extends Handler {
        private final HashMap<NetworkRequest, VowifiNetworkCallback> mCallbackMap;
        private final AtomicInteger mRefCount;
        private static final String TAG = "ConnectivityManagerEx.CallbackHandler";
        private final ConnectivityManagerEx mCmEx;

        CallbackHandler(Looper looper, HashMap<NetworkRequest, VowifiNetworkCallback> callbackMap,
                AtomicInteger refCount, ConnectivityManagerEx cmEx) {
            super(looper);
            mCallbackMap = callbackMap;
            mRefCount = refCount;
            mCmEx = cmEx;
        }

        @Override
        public void handleMessage(Message message) {
            NetworkRequest request = (NetworkRequest) getObject(message, NetworkRequest.class);
            VowifiNetwork netInfo = (VowifiNetwork) getObject(message, VowifiNetwork.class);
            if (Constants.DEBUG) {
                Log.d(TAG, "handle message " + message.what + " for " + netInfo);
            }
            switch (message.what) {
                // attach sucess
                case ConnectivityManager.CALLBACK_AVAILABLE: {
                    VowifiNetworkCallback callback = getCallback(request, "AVAILABLE");
                    if (callback != null) {
                        if (Constants.DEBUG)
                            Log.d(TAG, "CALLBACK_AVAILABLE, notify");
                        callback.onAvailable(netInfo);
                    }
                    break;
                }
                // attach fail
                case ConnectivityManager.CALLBACK_UNAVAIL: {
                    VowifiNetworkCallback callback = getCallback(request, "UNAVAIL");
                    if (callback != null) {
                        if (Constants.DEBUG)
                            Log.d(TAG, "CALLBACK_UNAVAIL, notify");
                        callback.onUnavailable();
                    }
                    break;
                }
                // vowifi link disconnects
                case ConnectivityManager.CALLBACK_LOST: {
                    VowifiNetworkCallback callback = getCallback(request, "LOST");
                    if (callback != null) {
                        if (Constants.DEBUG)
                            Log.d(TAG, "CALLBACK_UNAVAIL, notify");
                        callback.onLost(netInfo);
                    }
                    break;
                }
            }
        }

        private Object getObject(Message msg, Class c) {
            return msg.getData().getParcelable(c.getSimpleName());
        }

        private VowifiNetworkCallback getCallback(NetworkRequest req, String name) {
            VowifiNetworkCallback callback;
            synchronized (mCallbackMap) {
                callback = mCallbackMap.get(req);
            }
            if (callback == null) {
                Log.e(TAG, "callback not found for " + name + " message");
            }
            return callback;
        }
    }

    private void incCallbackHandlerRefCount() {
        synchronized (sCallbackRefCount) {
            if (sCallbackRefCount.incrementAndGet() == 1) {
                // TODO: switch this to ConnectivityThread
                HandlerThread callbackThread = new HandlerThread("ConnectivityManagerEx");
                callbackThread.start();
                sCallbackHandler = new CallbackHandler(callbackThread.getLooper(), sNetworkCallback,
                        sCallbackRefCount, this);
            }
        }
    }

    private void decCallbackHandlerRefCount() {
        synchronized (sCallbackRefCount) {
            if (sCallbackRefCount.decrementAndGet() == 0) {
                // TODO, remove for build error
                // sCallbackHandler.obtainMessage(ConnectivityManager.CALLBACK_EXIT).sendToTarget();
                sCallbackHandler = null;
            }
        }
    }

}
