package com.android.mms.service.vowifi;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.MatchAllNetworkSpecifier;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;

import com.android.ims.internal.IVoWifiSecurity;
import com.android.ims.internal.IVoWifiSecurityCallback;
import com.android.mms.service.vowifi.Constants.APNType;
import com.android.mms.service.vowifi.Constants.JSONUtils;
import com.android.mms.service.vowifi.Constants.Result;

import org.json.JSONException;
import org.json.JSONObject;

public class ConnectivityServiceEx {
    private static final String TAG = "ConnectivityServiceEx";

    private static final String SERVICE_PACKAGE = "com.spreadtrum.vowifi";
    private static final String SERVICE_CLASS = SERVICE_PACKAGE + ".service.SecurityService";
    private final static String SERVICE_ACTION = IVoWifiSecurity.class.getCanonicalName();

    private Context mContext;

    private int mNextNetworkRequestId = 1;
    private HandlerThread mHandlerThread;
    private InternalHandler mHandler;

    private HashMap<NetworkRequest, NetworkRequestInfo> mNetworkRequests =
            new HashMap<NetworkRequest, NetworkRequestInfo>();
    private HashMap<NetworkRequest, Integer> mRequestSessions =
            new HashMap<NetworkRequest, Integer>();
    private HashMap<Integer, NetworkRequestInfo> mSessionNris =
            new HashMap<Integer, NetworkRequestInfo>();
    private HashMap<Integer, VowifiNetwork> mSessionNetinfos =
            new HashMap<Integer, VowifiNetwork>();

    private Intent mIntent;
    private IBinder mServiceBinder;
    private IVoWifiSecurity mISecurity = null;
    private SecurityCallback mSecurityCallback = new SecurityCallback();

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (Constants.DEBUG) log("The service " + name + " disconnected.");
            mServiceBinder = null;
            onServiceChanged();

            // Re-bind tConnectivityServiceExhe service if the service disconnected.
            if (Constants.DEBUG) log("As service disconnected, will rebind the service after 30s.");
            mHandler.sendEmptyMessageDelayed(EVENT_REBIND_SERVICE, 30 * 1000);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (Constants.DEBUG) log("The service " + name + " connected.");
            mServiceBinder = service;
            onServiceChanged();
        }
    };

    private static final int EVENT_REGISTER_IMS_NETWORK = 1;

    /**
     * indicates a timeout period is over - check if we had a network yet or not
     * and if not, call the timeout calback (but leave the request live until they
     * cancel it.
     * includes a NetworkRequestInfo
     */
    private static final int EVENT_TIMEOUT_IMS_NETWORK = 2;

    /**
     * used to remove a network request, either a listener or a real request
     * arg1 = UID of caller
     * obj  = NetworkRequest
     */
    private static final int EVENT_RELEASE_IMS_NETWORK = 3;

    private static final int EVENT_REBIND_SERVICE = 4;

    public ConnectivityServiceEx(Context context) {
        if (Constants.DEBUG) log("ConnectivityServiceEx starting up");

        mContext = context;
        mHandlerThread = new HandlerThread("ConnectivityServiceExThread");;
        mHandlerThread.start();
        mHandler = new InternalHandler(mHandlerThread.getLooper());
    }

    private IVoWifiSecurity getVowifiSecurity() {
        if (null == mISecurity) {
            int n = 0;
            int retryTimes = 5;

            while (n < retryTimes) {
                if (null == mISecurity) {
                    if (Constants.DEBUG)
                        log("getVowifiSecurity: bind SECURITY_SERVICE!");
                    bindService();
                }
                if (null != mISecurity) {
                    break;
                } else if (null == mISecurity && (n == retryTimes - 1)) {
                    if (Constants.DEBUG)
                        log("getVowifiSecurity: can't get mISecurity!");
                }

                n++;
                SystemClock.sleep(1000);
            }
        }

        return mISecurity;
    }

    private void bindService() {
        if (mServiceBinder != null) {
            log("bindService: init mISecurity");
            onServiceChanged();
            return;
        }

        mIntent = new Intent(SERVICE_ACTION);
        mIntent.setComponent(new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS));
        mContext.bindService(mIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private void rebindService() {
        if (mIntent != null) {
            mContext.bindService(mIntent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void unbindService() {
        mContext.unbindService(mConnection);
    }

    private void onServiceChanged() {
        try {
            if (mServiceBinder != null) {
                mISecurity = IVoWifiSecurity.Stub.asInterface(mServiceBinder);
                mISecurity.registerCallback(mSecurityCallback);
            } else {
                loge("onServiceChanged: mServiceBinder is null");
            }
        } catch (RemoteException e) {
            log("Can not register callback as catch the RemoteException. e: " + e);
        }
    }


    private class SecurityCallback extends IVoWifiSecurityCallback.Stub {

        @Override
        public void onS2bStateChanged(String json) throws RemoteException {
            if (Constants.DEBUG) log("Get the security callback: " + json);

            if (TextUtils.isEmpty(json)) {
                loge("Can not handle the security callback as the json is null.");
                return;
            }

            try {
                JSONObject jObject = new JSONObject(json);
                int eventCode = jObject.optInt(JSONUtils.KEY_EVENT_CODE);
                switch (eventCode) {
                    case JSONUtils.EVENT_CODE_ATTACH_SUCCESSED: {
                        if (Constants.DEBUG) log("onS2bStateChanged: S2b attach success.");
                        int sessionId = jObject.optInt(JSONUtils.KEY_SESSION_ID, -1);
                        String localIP4 = jObject.optString(JSONUtils.KEY_LOCAL_IP4, null);
                        String localIP6 = jObject.optString(JSONUtils.KEY_LOCAL_IP6, null);
                        String pcscfIP4 = jObject.optString(JSONUtils.KEY_PCSCF_IP4, null);
                        String pcscfIP6 = jObject.optString(JSONUtils.KEY_PCSCF_IP6, null);
                        String dnsIP4 = jObject.optString(JSONUtils.KEY_DNS_IP4, null);
                        String dnsIP6 = jObject.optString(JSONUtils.KEY_DNS_IP6, null);
                        boolean prefIPv4 = jObject.optBoolean(JSONUtils.KEY_PREF_IP4, false);
                        boolean isSos = jObject.optBoolean(JSONUtils.KEY_SOS, false);

                        if (Constants.DEBUG) log("onS2bStateChanged: success, sessionId = " + sessionId);
                        if (mSessionNris.get(sessionId) != null) {
                            if (Constants.DEBUG) {
                                log("onS2bStateChanged: success, NRI is "
                                        + mSessionNris.get(sessionId));
                            }
                            VowifiNetwork netInfo = mSessionNetinfos.get(sessionId);
                            if (netInfo != null && mISecurity != null) {
                                netInfo.setState(VowifiNetwork.State.CONNECTED);
                                if (Constants.DEBUG) {
                                    log("onS2bStateChanged: success,current state is "
                                            + netInfo.getState());
                                }
                                netInfo.setIpv4PcscfAddr(pcscfIP4);
                                netInfo.setIpv6PcscfAddr(pcscfIP6);
                                netInfo.setIPv4DnsAddr(dnsIP4);
                                netInfo.setIPv6DnsAddr(dnsIP6);
                                netInfo.setPrefIPv4(prefIPv4);
                                netInfo.setIsSos(isSos);
                                if (Constants.DEBUG)
                                    log("onS2bStateChanged: success, prefIPv4 = " + prefIPv4);
                                if (prefIPv4) {
                                    netInfo.setIpv4Addr(localIP4);
                                    mISecurity.switchLoginIpVersion(sessionId, 0);
                                } else {
                                    netInfo.setIpv6Addr(localIP6);
                                    mISecurity.switchLoginIpVersion(sessionId, 1);
                                }
                            }
                            onNetConnected(sessionId, netInfo);
                        }
                        break;
                    }
                    case JSONUtils.EVENT_CODE_ATTACH_FAILED: {
                        int sessionId = jObject.optInt(JSONUtils.KEY_SESSION_ID, -1);
                        int errorCode = jObject.optInt(JSONUtils.KEY_STATE_CODE);
                        if (Constants.DEBUG) {
                            log("S2b attach failed, errorCode: " + errorCode + ", sessionId = "
                                    + sessionId);
                        }

                        if (mSessionNris.get(sessionId) != null) {
                            if (Constants.DEBUG) {
                                log("onS2bStateChanged: fail, NRI is "
                                        + mSessionNris.get(sessionId));
                            }
                            onNetFailed(sessionId, mSessionNris.get(sessionId).request);
                        }
                        break;
                    }
                    case JSONUtils.EVENT_CODE_ATTACH_PROGRESSING: {
                        int sessionId = jObject.optInt(JSONUtils.KEY_SESSION_ID, -1);
                        if (Constants.DEBUG) log("attach progressing: progressing");
                        if (mSessionNris.get(sessionId) != null) {
                            if (Constants.DEBUG) {
                                log("onS2bStateChanged: progressing,  NRI is "
                                        + mSessionNris.get(sessionId));
                            }
                            VowifiNetwork netInfo = mSessionNetinfos.get(sessionId);
                            if (netInfo != null) {
                                netInfo.setState(VowifiNetwork.State.CONNECTING);
                            }
                        }
                        break;
                    }
                    case JSONUtils.EVENT_CODE_ATTACH_STOPPED: {
                        int sessionId = jObject.optInt(JSONUtils.KEY_SESSION_ID, -1);
                        int errorCode = jObject.optInt(JSONUtils.KEY_STATE_CODE);
                        if (Constants.DEBUG) {
                            log("attach stopped, errorCode: " + errorCode + ", sessionId = "
                                    + sessionId);
                        }
                        if (mSessionNris.get(sessionId) != null) {
                            if (Constants.DEBUG)
                                log("onS2bStateChanged: stopped,  NRI is "
                                        + mSessionNris.get(sessionId));
                            onNetDisconnected(sessionId, mSessionNris.get(sessionId).request);
                        }
                        break;
                    }
                }
            } catch (JSONException e) {
                loge("Failed to parse the security callback as catch the JSONException, e: " + e);
            }
        }
    }

    protected void onNetConnected(int sessionId, VowifiNetwork info) {
        if (Constants.DEBUG) log("attachOk: info = " + info);
        callCallbackForRequest(
                mSessionNris.get(sessionId), info, ConnectivityManager.CALLBACK_AVAILABLE);
    }

    protected void onNetFailed(int sessionId, NetworkRequest netReq) {
        if (Constants.DEBUG) log("attachFailed: E");
        callCallbackForRequest(
                mSessionNris.get(sessionId), null, ConnectivityManager.CALLBACK_UNAVAIL);

        mRequestSessions.remove(netReq);
        mNetworkRequests.remove(netReq);
        mSessionNris.remove(sessionId);
        mSessionNetinfos.remove(sessionId);
    }

    protected void onNetDisconnected(int sessionId, NetworkRequest netReq) {
        if (Constants.DEBUG) {
            log("onNetDisconnect: vowifiNetwork = " + mSessionNetinfos.get(sessionId));
        }
        callCallbackForRequest(mSessionNris.get(sessionId), mSessionNetinfos.get(sessionId),
                ConnectivityManager.CALLBACK_LOST);

        mRequestSessions.remove(netReq);
        mNetworkRequests.remove(netReq);
        mSessionNris.remove(sessionId);
        mSessionNetinfos.remove(sessionId);
    }

    public NetworkRequest requestImsNetwork(NetworkCapabilities networkCapabilities,
            Messenger messenger, IBinder binder, int subId) {
        int apnType = -1;

        if (mISecurity == null) {
            bindService();
        }

        NetworkRequest.Type type = networkCapabilities == null ? NetworkRequest.Type.TRACK_DEFAULT
                : NetworkRequest.Type.REQUEST;

        // If the requested networkCapabilities is null, take them instead from
        // the default network request. This allows callers to keep track of
        // the system default network.
        if (type == NetworkRequest.Type.TRACK_DEFAULT) {
            // TO DO
            return null;
        } else {
            networkCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        }
        ensureRequestableCapabilities(networkCapabilities);

        if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS)) {
            apnType = APNType.APN_TYPE_MMS;
        }

        if (apnType == -1) {
            log("requestImsNetwork: invalid apnType");
            return null;
        }

        MatchAllNetworkSpecifier
                .checkNotMatchAllNetworkSpecifier(networkCapabilities.getNetworkSpecifier());

        NetworkRequest networkRequest = new NetworkRequest(networkCapabilities,
                ConnectivityManager.TYPE_NONE, nextNetworkRequestId(), type);

        NetworkRequestInfo nri =
                new NetworkRequestInfo(messenger, networkRequest, binder, type, subId);
        if (Constants.DEBUG) log("requestImsNetwork for " + nri);

        mHandler.sendMessage(
                mHandler.obtainMessage(EVENT_REGISTER_IMS_NETWORK, subId, apnType, nri));

        return networkRequest;
    }

    public void releaseImsNetworkRequest(NetworkRequest networkRequest, int subId) {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_RELEASE_IMS_NETWORK, subId,
                 -1, networkRequest));
    }

    private void ensureRequestableCapabilities(NetworkCapabilities networkCapabilities) {
        final String badCapability = networkCapabilities.describeFirstNonRequestableCapability();
        if (badCapability != null) {
            throw new IllegalArgumentException("requestImsNetwork Cannot request network with "
                    + badCapability);
        }
    }

    private synchronized int nextNetworkRequestId() {
        return mNextNetworkRequestId++;
    }

    public VowifiNetwork getVowifiNetwork(int subId) {
        if (!mSessionNetinfos.isEmpty()) {
            for (VowifiNetwork info : mSessionNetinfos.values()) {
                if (APNType.APN_TYPE_MMS == info.getApnType() && subId == info.getSubId()) {
                    if (Constants.DEBUG) log("getVowifiNetwork: State = " + info.getState());
                    return info;
                }
            }
        }

        return new VowifiNetwork(Result.INVALID_ID, subId, VowifiNetwork.State.DISCONNECTED);
    }

    private class InternalHandler extends Handler {
        public InternalHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case EVENT_REGISTER_IMS_NETWORK: {
                    handleRegisterNetworkRequest(msg.arg1, msg.arg2, (NetworkRequestInfo) msg.obj);
                    break;
                }
                case EVENT_RELEASE_IMS_NETWORK: {
                    handleReleaseNetworkRequest((NetworkRequest) msg.obj, msg.arg1, msg.arg2);
                    break;
                }
                case EVENT_REBIND_SERVICE: {
                    rebindService();
                    break;
                }
            }
        }
    }


    private void handleRegisterNetworkRequest(int subId, int apnType, NetworkRequestInfo nri) {
        // SIMAccountInfo simInfo = null;
        int sessionId = Result.INVALID_ID;

        mNetworkRequests.put(nri.request, nri);
        log("handleRegisterNetworkRequest: " + nri);

        if (mISecurity == null) {
            getVowifiSecurity();
        }

        if (mISecurity != null) {
            try {
                if (Constants.DEBUG) {
                    log("handleRegisterNetworkRequest: call function of mISecurity");
                }
                sessionId = mISecurity.start(apnType, subId);
                log("handleRegisterNetworkRequest: returned sessionId by start = " + sessionId);
            } catch (RemoteException e) {
                loge("Catch the remote exception when start the s2b attach. e: " + e);
            }
        }

        if (sessionId != Result.INVALID_ID) {
            VowifiNetwork netInfo = new VowifiNetwork(sessionId, subId,
                    VowifiNetwork.State.CONNECTING);
            netInfo.setWifiNetId(getActiveWifiNetId());
            if (Constants.DEBUG) {
                log("handleRegisterNetworkRequest: current state is " + netInfo.getState());
            }
            mRequestSessions.put(nri.request, sessionId);
            mSessionNris.put(sessionId, nri);
            mSessionNetinfos.put(sessionId, netInfo);
        } else {
            callCallbackForRequest(nri, null, ConnectivityManager.CALLBACK_UNAVAIL);
            mNetworkRequests.remove(nri.request);
        }
    }

    private int getActiveWifiNetId() {
        ConnectivityManager cm =
                    (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm != null) {
            for(Network network : cm.getAllNetworks()) {
                NetworkInfo netInfo = cm.getNetworkInfo(network);
                if (netInfo != null && ConnectivityManager.TYPE_WIFI == netInfo.getType()) {
                    int wifiNetId = network.netId;
                    if (Constants.DEBUG) log("getActiveWifiNetId: wifiNetId = " + wifiNetId);
                    return wifiNetId;
                }
            }
        }

        return ConnectivityManager.NETID_UNSET;
    }

    private void handleReleaseNetworkRequest(NetworkRequest request, int sesssionId,
            int callingUid) {
        int sessionId = Result.INVALID_ID;

        NetworkRequestInfo nri = mNetworkRequests.get(request);
        if (nri != null) {
            if (Constants.DEBUG) {
                log("releasing NetworkRequest " + request + ", nri.isRequest() = "
                        + nri.isRequest());
            }
            nri.unlinkDeathRecipient();

            if (nri.isRequest()) {
                if (mISecurity == null) {
                    bindService();
                }

                if (mISecurity != null) {
                    try {
                        if (mRequestSessions.get(request) != null) {
                            if (Constants.DEBUG) {
                                log("handleReleaseNetworkRequest: release session id "
                                        + mRequestSessions.get(request));
                            }
                            mISecurity.stop(mRequestSessions.get(request), false);
                        }
                    } catch (RemoteException e) {
                        loge("Catch the remote exception when start the s2b attach. e: " + e);
                    }
                }
            } else {
                // listens don't have a singular affectedNetwork. Check all networks to see
                // if this listen request applies and remove it.
                // how to handle listens but not requests ??
                // nothing for now
            }
        }

        if (mRequestSessions.get(request) != null) {
            sessionId = mRequestSessions.get(request);
        }
        onNetDisconnected(sessionId, request);
    }

    private void callCallbackForRequest(NetworkRequestInfo nri,
            VowifiNetwork netInfo, int notificationType) {
        if (nri == null || nri.messenger == null) {
            if (Constants.DEBUG) log("callCallbackForRequest: nri or messenger is null");
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putParcelable(NetworkRequest.class.getSimpleName(),
                new NetworkRequest(nri.request));
        if (netInfo != null) {
            bundle.putParcelable(VowifiNetwork.class.getSimpleName(), new VowifiNetwork(netInfo));
        }
        Message msg = Message.obtain();
        msg.what = notificationType;
        msg.setData(bundle);
        try {
            if (Constants.DEBUG) {
                log("sending notification " + notifyTypeToName(notificationType) +
                        " for " + nri.request);
            }
            nri.messenger.send(msg);
        } catch (RemoteException e) {
            // may occur naturally in the race of binder death.
            loge("RemoteException caught trying to send a callback msg for " + nri.request);
        }
    }

    private String notifyTypeToName(int notifyType) {
        switch (notifyType) {
            case ConnectivityManager.CALLBACK_PRECHECK:
                return "PRECHECK";
            case ConnectivityManager.CALLBACK_AVAILABLE:
                return "AVAILABLE";
            case ConnectivityManager.CALLBACK_LOSING:
                return "LOSING";
            case ConnectivityManager.CALLBACK_LOST:
                return "LOST";
            case ConnectivityManager.CALLBACK_UNAVAIL:
                return "UNAVAILABLE";
            case ConnectivityManager.CALLBACK_CAP_CHANGED:
                return "CAP_CHANGED";
            case ConnectivityManager.CALLBACK_IP_CHANGED:
                return "IP_CHANGED";
        }
        return "UNKNOWN";
    }

    /**
     * Tracks info about the requester.
     * Also used to notice when the calling process dies so we can self-expire
     */
    private class NetworkRequestInfo implements IBinder.DeathRecipient {
        final NetworkRequest request;
        final PendingIntent mPendingIntent;
        private final IBinder mBinder;
        int mSubId;
        final Messenger messenger;
        private final NetworkRequest.Type mType;

        NetworkRequestInfo(Messenger m, NetworkRequest r, IBinder binder, NetworkRequest.Type type,
                int subId) {
            super();
            messenger = m;
            request = r;
            mBinder = binder;
            mType = type;
            mPendingIntent = null;
            mSubId = subId;

            try {
                mBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                binderDied();
            }
        }

        private String typeString() {
            switch (mType) {
                case LISTEN: return "Listen";
                case REQUEST: return "Request";
                case TRACK_DEFAULT: return "Track default";
                default:
                    return "unknown type";
            }
        }

        void unlinkDeathRecipient() {
            if (mBinder != null) {
                mBinder.unlinkToDeath(this, 0);
            }
        }

        public void binderDied() {
            log("ConnectivityServiceEx NetworkRequestInfo binderDied(" +
                    request + ", " + mBinder + ")");
            releaseImsNetworkRequest(request, mSubId);
        }

        /**
         * Returns true iff. the contained NetworkRequest is one that:
         *
         *     - should be associated with at most one satisfying network
         *       at a time;
         *
         *     - should cause a network to be kept up if it is the only network
         *       which can satisfy the NetworkReqeust.
         *
         * For full detail of how isRequest() is used for pairing Networks with
         * NetworkRequests read rematchNetworkAndRequests().
         *
         * TODO: Rename to something more properly descriptive.
         */
        public boolean isRequest() {
            return (mType == NetworkRequest.Type.TRACK_DEFAULT) ||
                   (mType == NetworkRequest.Type.REQUEST);
        }

        public String toString() {
            return typeString() + ", on sub " + mSubId + " for " + request +
                    (mPendingIntent == null ? "" : " to trigger " + mPendingIntent);
        }
    }

    private static void log(String s) {
        Log.d(TAG, s);
    }

    private static void loge(String s) {
        Log.e(TAG, s);
    }

}
