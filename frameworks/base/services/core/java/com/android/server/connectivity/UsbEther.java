/*
 * create by spreadst
 */


package com.android.server.connectivity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.INetworkStatsService;
import android.net.InterfaceConfiguration;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkUtils;
import android.net.RouteInfo;
import android.os.Binder;
import android.os.INetworkManagementService;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import com.android.server.net.BaseNetworkObserver;

import java.net.InetAddress;
import java.net.Inet4Address;
import android.os.SystemProperties;

public class UsbEther {
    private static final String NETWORKTYPE = "USBETHER";
    private static final String USB_CONFIG_PROPERTY = "sys.usb.config";
    private static final String TAG = "UsbEther";
    private static final boolean LOGD = true;

    private final static int LOCAL_NETWORK_NET_ID = 99; //get from netd

    private final static int USB_ETHER_NETWORK_SCORE = 51; // just over mobile data


    private static final int EVENT_USB_NETWORK_UP  = 0;
    private static final int EVENT_USB_NETWORK_DOWN  = 1;
    private static final int EVENT_USB_NETWORK_REMOVED  = 2;
    private static final int EVENT_USB_NETWORK_ADDED  = 3;

    private Context mContext;
    private NetworkInfo mNetworkInfo;
    private String mInterface;
    private final INetworkManagementService mNetd;
    private NetworkAgent mNetworkAgent;
    private final Looper mLooper;
    private final NetworkCapabilities mNetworkCapabilities;
    private RouteInfo mRouteInfo = null;

    private String mLocalAddr = null;
    private String mRemoteAddr = null;

    private BroadcastReceiver mStateReceiver;
    private UsbNetworkObserver mUsbNetworkObserver;

    private boolean mRndisEnabled = false;
    private boolean mUsbEtherRequested = false;
    String[] mTetherableUsbRegexs;


    // used to synchronize public access to members
    private Object mPublicSync;

    final private InternalHandler mHandler;

    /* SPRD:Add for bug692657 androido porting pc net share @{ */
    private static final String DEFAULT_USB_NEAR_IFACE_ADDR      = "192.168.137.129";
    private String mUSBNearIfaceAddr = DEFAULT_USB_NEAR_IFACE_ADDR;
    private static final int USB_PREFIX_LENGTH = 24;
    boolean mUsbNetworkAdded = false;
    boolean mUsbNetworkUp = false;
    /* Bug692657 end @}*/



    public UsbEther(Looper looper, Context context, INetworkManagementService netService) {
        mContext = context;
        mNetd = netService;
        mLooper = looper;

        // For PC USB ether
        mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_ETHERNET, 0, NETWORKTYPE, "");

        mNetworkCapabilities = new NetworkCapabilities();
        mNetworkCapabilities.addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET);
        mNetworkCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        mNetworkCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);


        mPublicSync = new Object();

        mHandler = new InternalHandler(mLooper);

        //mStateReceiver = new UsbStateReceiver();
        //IntentFilter filter = new IntentFilter();
        //filter.addAction(UsbManager.ACTION_USB_STATE);
        //mContext.registerReceiver(mStateReceiver, filter);



        mUsbNetworkObserver = new UsbNetworkObserver();
        try {
            mNetd.registerObserver(mUsbNetworkObserver);
        } catch (RemoteException e) {
            Log.e(TAG, "Error registering observer :" + e);
        }
        mTetherableUsbRegexs = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_tether_usb_regexs);
    }


/***********************************************************************************
    private class UsbStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            if (action == null) { return; }
            if (action.equals(UsbManager.ACTION_USB_STATE)) {
                synchronized (UsbEther.this.mPublicSync) {
                    boolean usbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
                    mRndisEnabled = intent.getBooleanExtra(UsbManager.USB_FUNCTION_RNDIS, false);
                    mUsbEtherRequested = false;

                    if (LOGD) Log.d(TAG, "mRndisEnabled: " + mRndisEnabled + " usbConnected: " + usbConnected);

                }
            }
        }
    }
*************************************************************************************/

    private boolean isUsb(String iface) {
        for (String regex : mTetherableUsbRegexs) {
            if (iface.matches(regex)) return true;
        }
        Log.d(TAG, iface + " is not usb interface");
        return false;
    }

    private class UsbNetworkObserver extends BaseNetworkObserver {

        @Override
        public void interfaceLinkStateChanged(String iface, boolean up) {
            interfaceStatusChanged(iface, up);
        }

        public void interfaceStatusChanged(String iface, boolean up) {
            if (LOGD) Log.d(TAG, "interfaceStatusChanged " + iface + ", " + up);

            boolean found = false;
            boolean usb = false;
            synchronized (mPublicSync) {
                if (isUsb(iface)) {
                    found = true;
                    usb = true;
                }

                if (found == false) return;
                mUsbNetworkUp = up;
                if (up && mRndisEnabled) {
                    mHandler.sendMessage(mHandler.obtainMessage
                                    (EVENT_USB_NETWORK_UP));
                } else {
                    //SPRD: Bug #607858 UsbEther refresh statusbar icon BEG-->
                    if (LOGD) Log.d(TAG, " interface down for " + iface);
                    //mHandler.sendMessage(mHandler.obtainMessage
                    //        (EVENT_USB_NETWORK_DOWN));
                    //<-- UsbEther refresh statusbar icon END
                }
            }
        }

        public void interfaceRemoved(String iface) {
            if (LOGD) Log.d(TAG, "interfaceRemoved " + iface);

            synchronized (mPublicSync) {
                if (isUsb(iface)) {
                    mUsbNetworkAdded = false;
                    mHandler.sendMessage(mHandler.obtainMessage
                                    (EVENT_USB_NETWORK_REMOVED));
                }
            }
        }

        public void interfaceAdded(String iface) {
            if (LOGD) Log.d(TAG, "interfaceAdded " + iface);

            synchronized (mPublicSync) {
                if (isUsb(iface)) {
                    mUsbNetworkAdded = true;
                    mInterface = iface;
                    mHandler.sendMessage(mHandler.obtainMessage
                                    (EVENT_USB_NETWORK_ADDED));
                }
            }
        }
    }

    private class UsbNetworkAgent extends NetworkAgent {

        public UsbNetworkAgent(Looper l, Context c, String TAG, NetworkInfo ni,
                NetworkCapabilities nc, LinkProperties lp, int score) {
            super(l, c, TAG, ni, nc, lp, score);
        }

        protected void unwanted() {
            // Ignore if we're not the current networkAgent.
            if (this != mNetworkAgent) return;
            if (LOGD) log("UsbNetworkAgent -> UsbEther unwanted");
            setUsbEther(false, null, null, null);
        }


    }

    public void configUsbEther(String ifaceName, String localAddr, String remoteAddr ) {
        synchronized (mPublicSync) {
            //mInterface = ifaceName;
            mLocalAddr = localAddr;
            mRemoteAddr = remoteAddr;
        }
    }

    public void setUsbEther(boolean enable, String ifaceName, String localAddr, String remoteAddr) {
        if (LOGD) Log.d(TAG, "setUsbEther(" + enable + ")");

        UsbManager usbManager = (UsbManager)mContext.getSystemService(Context.USB_SERVICE);

        synchronized (mPublicSync) {
            if (enable) {
                if (!mRndisEnabled) {
                    mUsbEtherRequested = true;
                    usbManager.setCurrentFunctions(UsbManager.FUNCTION_RNDIS);
                    mRndisEnabled = true;
                }

                //mInterface = ifaceName;
                mLocalAddr = localAddr;
                mRemoteAddr = remoteAddr;

                if (LOGD) Log.d(TAG, "setUsbEther mUsbNetworkAdded = " + mUsbNetworkAdded + ", mUsbNetworkUp = " + mUsbNetworkUp);
                if (mUsbNetworkAdded && !mUsbNetworkUp) configureUsbIface(true);

            } else {
                if (mRndisEnabled) {
                    if(SystemProperties.get(USB_CONFIG_PROPERTY, usbManager.USB_FUNCTION_NONE)
                            .contains(usbManager.USB_FUNCTION_RNDIS)) {
                        usbManager.setCurrentFunctions(UsbManager.FUNCTION_NONE);
                    }
                    mRndisEnabled = false;
                }
                mUsbEtherRequested = false;
                tearDownConnection();
            }
            Intent broadcast = new Intent(ConnectivityManager.ACTION_PC_SHARE_CHANGED);
            broadcast.putExtra(ConnectivityManager.ACTION_PC_SHARE_CHANGED, enable);
            mContext.sendStickyBroadcastAsUser(broadcast, UserHandle.ALL);
        }
    }

    private void agentConnect() {

        synchronized (mPublicSync) {

            if (mInterface == null || mLocalAddr == null || mRemoteAddr == null)
                return;

            if (mNetworkAgent != null) {
                if (LOGD) Log.d(TAG, "agentConnect: already connected!");
                return;
            }

            LinkProperties lp = new LinkProperties();

            lp.setInterfaceName(mInterface);

            InetAddress usbIfAddr = NetworkUtils.numericToInetAddress(mLocalAddr);

            lp.addLinkAddress(new LinkAddress(usbIfAddr, 24));

            InetAddress addr = NetworkUtils.numericToInetAddress(mRemoteAddr);
            mRouteInfo = new RouteInfo((IpPrefix) null, addr, mInterface);

            lp.addRoute(mRouteInfo);
            lp.addDnsServer(addr);

            mNetworkInfo.setIsAvailable(true);
            mNetworkInfo.setDetailedState(DetailedState.CONNECTED, null, null);

            long token = Binder.clearCallingIdentity();
            try {
                mNetworkAgent = new UsbNetworkAgent(mLooper, mContext, NETWORKTYPE,
                        mNetworkInfo, mNetworkCapabilities, lp, USB_ETHER_NETWORK_SCORE);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    private void agentDisconnect() {

        synchronized (mPublicSync) {

            if (mNetworkAgent == null ) return;

            if (LOGD) Log.d(TAG, "tearDownConnection");

            mNetworkInfo.setIsAvailable(false);
            mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, null);
            if (mNetworkAgent != null) {
                mNetworkAgent.sendNetworkInfo(mNetworkInfo);
            }

            mNetworkAgent = null;
            //mInterface = null;
            mLocalAddr = null;
            mRemoteAddr = null;
        }
    }

    public void establishConnection() {

        if (LOGD) Log.d(TAG, "establishConnection from " + mLocalAddr + " to " + mRemoteAddr);

        agentConnect();
    }

    public void tearDownConnection() {
        agentDisconnect();
    }

    public void setUSBNearIfaceAddr(String usbNearIfaceAddr){
        mUSBNearIfaceAddr = usbNearIfaceAddr;
    }

    // configured when the USB interface is added
    private boolean configureUsbIface(boolean enabled) {
        if (!mRndisEnabled) return false;
        if (LOGD) Log.d(TAG, "configureUsbIface(" + enabled + ")");

        // toggle the USB interfaces
        String[] ifaces = new String[0];
        try {
            ifaces = mNetd.listInterfaces();
        } catch (Exception e) {
            Log.e(TAG, "Error listing Interfaces", e);
            return false;
        }
        for (String iface : ifaces) {
            if (isUsb(iface)) {
                InterfaceConfiguration ifcg = null;

                try {
                    ifcg = mNetd.getInterfaceConfig(iface);
                    if (ifcg != null) {
                        InetAddress addr = NetworkUtils.numericToInetAddress(mUSBNearIfaceAddr);
                        ifcg.setLinkAddress(new LinkAddress(addr, USB_PREFIX_LENGTH));
                        if (enabled) {
                            ifcg.setInterfaceUp();
                        } else {
                            ifcg.setInterfaceDown();
                        }
                        ifcg.clearFlag("running");
                        mNetd.setInterfaceConfig(iface, ifcg);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error configuring interface " + iface, e);
                    return false;
                }
            }
         }

        return true;
    }

    private class InternalHandler extends Handler {
        public InternalHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_USB_NETWORK_DOWN:
                    tearDownConnection();
                    break;

                case EVENT_USB_NETWORK_ADDED:
                    configureUsbIface(true);
                    establishConnection();
                    break;

                case EVENT_USB_NETWORK_REMOVED:
                    tearDownConnection();
                    break;

                case EVENT_USB_NETWORK_UP:
                    establishConnection();
                    break;
            }
        }
    }
}
