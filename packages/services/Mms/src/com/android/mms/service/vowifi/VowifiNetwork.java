package com.android.mms.service.vowifi;

import android.os.Parcelable;
import android.os.Parcel;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.net.URL;
import java.net.URLConnection;
import javax.net.SocketFactory;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.List;

import com.android.mms.service.vowifi.Constants.APNType;
import com.android.okhttp.ConnectionPool;
import com.android.okhttp.Dns;
import com.android.okhttp.HttpHandler;
import com.android.okhttp.HttpsHandler;
import com.android.okhttp.OkHttpClient;
import com.android.okhttp.OkUrlFactory;

/**
 * Describes the status of a network interface.
 * <p>Use {@link ConnectivityManager#getVowifiNetwork()} to get an instance that represents
 * the current network connection.
 */
public class VowifiNetwork implements Parcelable {
        private static final String TAG = "VowifiNetwork";
        private int mSessionId;
        private int mSubId;
        private int mApnType;
        private State mState;
        private String mLocalIP4Addr;
        private String mLocalIP6Addr;
        private String mPcscfIP4Addr;
        private String mPcscfIP6Addr;
        private String mDnsIP4Addr;
        private String mDnsIP6Addr;
        private boolean mPrefIPv4;
        private boolean mIsSos;
        private int mWifiNetworkId;

    private volatile ConnectionPool mConnectionPool = null;
    // Default connection pool values. These are evaluated at startup, just
    // like the OkHttp code. Also like the OkHttp code, we will throw parse
    // exceptions at class loading time if the properties are set but are not
    // valid integers.
    private static final boolean httpKeepAlive =
            Boolean.parseBoolean(System.getProperty("http.keepAlive", "true"));
    private static final int httpMaxConnections = httpKeepAlive
            ? Integer.parseInt(System.getProperty("http.maxConnections", "5")) : 0;
    private static final long httpKeepAliveDurationMs = Long
            .parseLong(System.getProperty("http.keepAliveDuration", "300000")); // 5 minutes.
    // private volatile com.android.okhttp.internal.Network mNetwork = null;
    private volatile VowifiSocketFactory mVowifiSocketFactory = null;
    private volatile Dns mDns = null;
    private final Object mLock = new Object();

    public enum State {
            CONNECTING, CONNECTED, DISCONNECTED, UNKNOWN
        }

        public VowifiNetwork(int sessionId, int subId, State state) {
            mSessionId = sessionId;
            mSubId = subId;
            mApnType = APNType.APN_TYPE_MMS;
            mState = state;
        }

        public VowifiNetwork(int sessionId, int subId, int apnType) {
            mSessionId = sessionId;
            mSubId = subId;
            mApnType = apnType;
            mState = State.UNKNOWN;
        }

    public VowifiNetwork(int sessionId, String localIpv4, String localIpv6, String pcscfIpv4,
            String pcscfIpv6, String dnsIPv4, String dnsIpv6, boolean ipv4Pref, boolean isSos,
            int wifiNetId) {
        mSessionId = sessionId;
        mLocalIP4Addr = localIpv4;
        mLocalIP6Addr = localIpv6;
        mPcscfIP4Addr = pcscfIpv4;
        mPcscfIP6Addr = pcscfIpv6;
        mDnsIP4Addr = dnsIPv4;
        mDnsIP6Addr = dnsIpv6;
        mPrefIPv4 = ipv4Pref;
        mIsSos = isSos;
        mWifiNetworkId = wifiNetId;
        mState = State.UNKNOWN;
    }

        public VowifiNetwork(VowifiNetwork source) {
            if (source != null) {
                synchronized (source) {
                    mSessionId = source.mSessionId;
                    mSubId = source.mSubId;
                    mApnType = source.mApnType;
                    mState = source.mState;
                    mLocalIP4Addr = source.mLocalIP4Addr;
                    mLocalIP6Addr = source.mLocalIP6Addr;
                    mPcscfIP4Addr = source.mPcscfIP4Addr;
                    mPcscfIP6Addr = source.mPcscfIP6Addr;
                    mDnsIP4Addr = source.mDnsIP4Addr;
                    mDnsIP6Addr = source.mDnsIP6Addr;
                    mPrefIPv4 = source.mPrefIPv4;
                    mIsSos = source.mIsSos;
                    mWifiNetworkId = source.mWifiNetworkId;
                }
            }
        }

    public void setState(State state) {
        synchronized (this) {
            mState = state;
        }
    }

    public State getState() {
        synchronized (this) {
            return mState;
        }
    }

    public int getApnType() {
        synchronized (this) {
            return mApnType;
        }
    }

    public int getSubId() {
        synchronized (this) {
            return mSubId;
        }
    }

    public void setIpv4Addr(String ipv4Addr) {
        synchronized (this) {
            mLocalIP4Addr = ipv4Addr;
        }
    }

    public String getIpv4Addr() {
        synchronized (this) {
            return mLocalIP4Addr;
        }
    }

    public void setIpv6Addr(String ipv6Addr) {
        synchronized (this) {
            mLocalIP6Addr = ipv6Addr;
        }
    }

    public String getIpv6Addr() {
        synchronized (this) {
            return mLocalIP6Addr;
        }
    }


    public void setIpv4PcscfAddr(String ipv4Addr) {
        synchronized (this) {
            mPcscfIP4Addr = ipv4Addr;
        }
    }

    public String getIpv4PcscfAddr() {
        synchronized (this) {
            return mPcscfIP4Addr;
        }
    }

    public void setIpv6PcscfAddr(String ipv6Addr) {
        synchronized (this) {
            mPcscfIP6Addr = ipv6Addr;
        }
    }

    public String getIpv6PcscfAddr() {
        synchronized (this) {
            return mPcscfIP6Addr;
        }
    }

    public void setIPv4DnsAddr(String ipv4Addr) {
        synchronized (this) {
            mDnsIP4Addr = ipv4Addr;
        }
    }

    public String getIPv4DnsAddr() {
        synchronized (this) {
            return mDnsIP4Addr;
        }
    }

    public void setIPv6DnsAddr(String ipv6Addr) {
        synchronized (this) {
            mDnsIP6Addr = ipv6Addr;
        }
    }

    public String getIPv6DnsAddr() {
        synchronized (this) {
            return mDnsIP6Addr;
        }
    }

    public void setPrefIPv4(boolean prefIpv4) {
        synchronized (this) {
            mPrefIPv4 = prefIpv4;
        }
    }

    public boolean getPrefIPv4() {
        synchronized (this) {
            return mPrefIPv4;
        }
    }

    public void setIsSos(boolean isSos) {
        synchronized (this) {
            mIsSos = isSos;
        }
    }

    public boolean getIsSos() {
        synchronized (this) {
            return mIsSos;
        }
    }

    public void setWifiNetId(int wifiNetId) {
        synchronized (this) {
            mWifiNetworkId = wifiNetId;
        }
    }

    public int getWifiNetId() {
        synchronized (this) {
            return mWifiNetworkId;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

        dest.writeInt(mSessionId);
        dest.writeInt(mSubId);
        dest.writeInt(mApnType);
        dest.writeString(mState.name());
        dest.writeString(mLocalIP4Addr);
        dest.writeString(mLocalIP6Addr);
        dest.writeString(mPcscfIP4Addr);
        dest.writeString(mPcscfIP6Addr);
        dest.writeString(mDnsIP4Addr);
        dest.writeString(mDnsIP6Addr);
        dest.writeInt(mPrefIPv4 ? 1 : 0);
        dest.writeInt(mIsSos ? 1 : 0);
        dest.writeInt(mWifiNetworkId);
    }

    public static final Creator<VowifiNetwork> CREATOR = new Creator<VowifiNetwork>() {
        @Override
        public VowifiNetwork createFromParcel(Parcel in) {
            int mSessionId = in.readInt();
            int mSubId = in.readInt();
            int mApnType = in.readInt();
            VowifiNetwork netInfo = new VowifiNetwork(mSessionId, mSubId, mApnType);
            netInfo.mState = State.valueOf(in.readString());

            netInfo.mLocalIP4Addr = in.readString();
            netInfo.mLocalIP6Addr = in.readString();
            netInfo.mPcscfIP4Addr = in.readString();
            netInfo.mPcscfIP6Addr = in.readString();
            netInfo.mDnsIP4Addr = in.readString();
            netInfo.mDnsIP6Addr = in.readString();
            netInfo.mPrefIPv4 = in.readInt() != 0;
            netInfo.mIsSos = in.readInt() != 0;
            netInfo.mWifiNetworkId = in.readInt();
            return netInfo;
        }

        @Override
        public VowifiNetwork[] newArray(int size) {
            return new VowifiNetwork[size];

        }

    };


    /**
     * Opens the specified {@link URL} on this {@code VowifiNetwork}, such that all traffic will
     * be sent on this Network. The URL protocol must be {@code HTTP} or {@code HTTPS}.
     *
     * @return a {@code URLConnection} to the resource referred to by this URL.
     * @throws MalformedURLException if the URL protocol is not HTTP or HTTPS.
     * @throws IOException if an error occurs while opening the connection.
     * @see java.net.URL#openConnection()
     */
    public URLConnection openConnection(URL url) throws IOException {
        java.net.Proxy proxy = java.net.Proxy.NO_PROXY;

        return openConnection(url, proxy);
    }

    /**
     * Opens the specified {@link URL} on this {@code VowifiNetwork}, such that all traffic will be
     * sent on this Network. The URL protocol must be {@code HTTP} or {@code HTTPS}.
     *
     * @param proxy the proxy through which the connection will be established.
     * @return a {@code URLConnection} to the resource referred to by this URL.
     * @throws MalformedURLException if the URL protocol is not HTTP or HTTPS.
     * @throws IllegalArgumentException if the argument proxy is null.
     * @throws IOException if an error occurs while opening the connection.
     * @see java.net.URL#openConnection()
     */
    public URLConnection openConnection(URL url, java.net.Proxy proxy) throws IOException {

        if (proxy == null) throw new IllegalArgumentException("proxy is null");
        OkUrlFactory okUrlFactory;
        OkHttpClient client;
        //HttpURLConnection connection = null;

        maybeInitHttpClient();

        String protocol = url.getProtocol();

        if (protocol.equals("http")) {
            okUrlFactory = HttpHandler.createHttpOkUrlFactory(proxy);
        } else if (protocol.equals("https")) {
            okUrlFactory = HttpsHandler.createHttpsOkUrlFactory(proxy);
        } else {
            // OkHttp only supports HTTP and HTTPS and returns a null URLStreamHandler if
            // passed another protocol.
            throw new MalformedURLException("Invalid URL or unrecognized protocol " + protocol);
        }

        client = okUrlFactory.client();
        client.setSocketFactory(getSocketFactory()).setConnectionPool(mConnectionPool);

        // Let network traffic go via mDns
        client.setDns(mDns);

        return okUrlFactory.open(url);
    }

    /**
     * Operates the same as {@code InetAddress.getAllByName} except that host
     * resolution is done on this network.
     *
     * @param host the hostname or literal IP string to be resolved.
     * @return the array of addresses associated with the specified host.
     * @throws UnknownHostException if the address lookup fails.
     */
    public InetAddress[] getAllByName(String host) throws UnknownHostException {
        return InetAddress.getAllByNameOnNet(host, mWifiNetworkId);
    }

    /**
     * Operates the same as {@code InetAddress.getByName} except that host
     * resolution is done on this network.
     *
     * @param host
     *            the hostName to be resolved to an address or {@code null}.
     * @return the {@code InetAddress} instance representing the host.
     * @throws UnknownHostException
     *             if the address lookup fails.
     */
    public InetAddress getByName(String host) throws UnknownHostException {
        return InetAddress.getByNameOnNet(host, mWifiNetworkId);
    }

    /**
     * Returns a {@link SocketFactory} bound to this network.  Any {@link Socket} created by
     * this factory will have its traffic sent over this {@code Network}.  Note that if this
     * {@code Network} ever disconnects, this factory and any {@link Socket} it produced in the
     * past or future will cease to work.
     *
     * @return a {@link SocketFactory} which produces {@link Socket} instances bound to this
     *         {@code Network}.
     */
    public SocketFactory getSocketFactory() {
        if (mVowifiSocketFactory == null) {
            synchronized (mLock) {
                if (mVowifiSocketFactory == null) {
                    mVowifiSocketFactory = new VowifiSocketFactory();
                }
            }
        }
        return mVowifiSocketFactory;
    }

    private void maybeInitHttpClient() {
        synchronized (mLock) {
            if (mDns == null) {
                mDns = new Dns() {
                    @Override
                    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                        return Arrays.asList(VowifiNetwork.this.getAllByName(hostname));
                    }
                };
            }
            if (mConnectionPool == null) {
                mConnectionPool = new ConnectionPool(httpMaxConnections,
                        httpKeepAliveDurationMs, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * A {@code SocketFactory} that produces {@code Socket}'s bound to this network.
     */
    private class VowifiSocketFactory extends SocketFactory{

        //private final int mNetId;
        InetSocketAddress mLocalAddress = null;

        public VowifiSocketFactory() {
            super();

            //prefer to use IPv4 address,for this network is used by MMS/UT
            if (mLocalIP4Addr != null) {
                Log.d(TAG, "VowifiSocketFactory: mLocalIP4Addr is " + mLocalIP4Addr);
                mLocalAddress = new InetSocketAddress(mLocalIP4Addr, 0);
            } else {
                Log.d(TAG, "VowifiSocketFactory: mLocalIP6Addr is " + mLocalIP6Addr);
                mLocalAddress = new InetSocketAddress(mLocalIP6Addr, 0);
            }
        }

        private Socket connectToHost(String host, int port, SocketAddress localAddress)
                throws IOException {
            // Lookup addresses only on this Network.
            InetAddress[] hostAddresses = getAllByName(host);

            // Try all addresses.
            for (int i = 0; i < hostAddresses.length; i++) {
                try {
                    Socket socket = createSocket();
                    if (localAddress != null) socket.bind(localAddress);
                    socket.connect(new InetSocketAddress(hostAddresses[i], port));
                    return socket;
                } catch (IOException e) {
                    if (i == (hostAddresses.length - 1)) throw e;
                }
            }
            throw new UnknownHostException(host);
        }


        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException {
            return connectToHost(host, port, new InetSocketAddress(localHost, localPort));
        }


        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
                int localPort) throws IOException {
            Socket socket = createSocket();
            socket.bind(new InetSocketAddress(localAddress, localPort));
            socket.connect(new InetSocketAddress(address, port));
            return socket;

        }


        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            Socket socket = createSocket();
            socket.connect(new InetSocketAddress(host, port));
            return socket;

        }


        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return connectToHost(host, port, null);
        }


        @Override
        public Socket createSocket() throws IOException {
            Socket socket = new Socket();
            if (mLocalAddress != null) socket.bind(mLocalAddress);
            return socket;
        }
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("[");
        builder.append("sessionId: " + mSessionId)
                .append(", subId: " + mSubId)
                .append(", apnType: " + mApnType)
                .append(", LocalIP4Addr: " + mLocalIP4Addr)
                .append(", LocalIP6Addr: " + mLocalIP6Addr)
                .append(", PcscfIP4Addr: " + mPcscfIP4Addr)
                .append(", PcscfIP6Addr: " + mPcscfIP6Addr)
                .append(", DnsIP4Addr: " + mDnsIP4Addr)
                .append(", DnsIP6Addr: " + mDnsIP6Addr)
                .append(", PrefIPv4: " + mPrefIPv4)
                .append(", IsSos: " + mIsSos)
                .append(", wifiNetId: " + mWifiNetworkId)
                .append("]");

        return builder.toString();
    }

}
