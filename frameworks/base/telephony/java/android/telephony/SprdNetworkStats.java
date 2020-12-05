/*
 * Copyright: Spreadtrum Communications, Inc. (2015-2115)
 * Description: statistical mobile and wifi Total Bytes
 * Date: 2017/6/20
 */

package com.dmyk.commlog.data;


import android.net.INetworkStatsService;
import android.net.INetworkStatsSession;
import android.net.NetworkStatsHistory;
import android.net.NetworkTemplate;
import android.net.TrafficStats;
import android.os.Build;
import android.os.ServiceManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import java.util.Date;
import java.util.Calendar;
import java.text.ParseException;
import java.io.IOException;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.content.Context;
import android.net.ConnectivityManager;
import static android.net.NetworkStatsHistory.FIELD_RX_BYTES;
import static android.net.NetworkStatsHistory.FIELD_TX_BYTES;

public class SprdNetworkStats extends Handler {
    private final String TAG = "SprdNetworkStats";
    Context mContext;
    private static Looper sLooper = null;
    private static SprdNetworkStats sInstance = null;
    private static final int FIELDS = FIELD_RX_BYTES | FIELD_TX_BYTES;


    public SprdNetworkStats(Looper looper) {
        super(looper);
    }

    public synchronized  static SprdNetworkStats getInstance() {
        if (sInstance == null) {
            if (sLooper == null) {
                HandlerThread thread = new HandlerThread("SprdNetworkStats");
                thread.start();
                sLooper = thread.getLooper();
        }
            sInstance = new SprdNetworkStats(sLooper);
        }
        return sInstance;
    }

    public void init(Context context) {
        mContext = context;
    }

    public long getWiFiTotalBytes (long startTime, long endTime) {
        long wifiTotal = 0;
        long end = System.currentTimeMillis();
        try {
            Log.d(TAG, " getWiFiTotalBytes start do!! ");
            NetworkTemplate template = NetworkTemplate.buildTemplateWifiWildcard();
            INetworkStatsService mStatsService = INetworkStatsService.Stub.asInterface(ServiceManager.getService(Context.NETWORK_STATS_SERVICE));
            INetworkStatsSession statsSession = mStatsService.openSession();
            if (statsSession != null) {
                final NetworkStatsHistory history = statsSession.getHistoryForNetwork(template, FIELDS);
                final long now = System.currentTimeMillis();
                Log.d(TAG, " get wifi bytes bucketDuration =  " + history.getBucketDuration());
                final NetworkStatsHistory.Entry entry = history.getValues(startTime, endTime, now, null);
                Log.d(TAG, " get wifi bytes current time: now =  " + now);
                if (entry == null) {
                    Log.d(TAG, " no wifi entry data  ");
                    return 0;
                }
                wifiTotal = entry.rxBytes + entry.txBytes;
                TrafficStats.closeQuietly(statsSession);
            }

        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        Log.d(TAG, " get wifi Bytes = " + wifiTotal + " , startTime =  " + startTime + ",  endTime = " + endTime);

        return wifiTotal;
    }


    public long getMobileTotalBytes(int phoneId, long startTime, long endTime) {
        long mobileBytes = 0;
        String subscriberId = getSubscriberId(phoneId);
        try {
            if (null == subscriberId) {
                Log.e(TAG, " not insert sim card !!!");
                return 0;
            } else {
                Log.d(TAG, " test  mobile start do!! ");
                NetworkTemplate template = NetworkTemplate.buildTemplateMobileAll(subscriberId);
                INetworkStatsService mStatsService = INetworkStatsService.Stub.asInterface(ServiceManager.getService(Context.NETWORK_STATS_SERVICE));

                INetworkStatsSession statsSession = mStatsService.openSession();
                if (statsSession != null) {
                    final NetworkStatsHistory history = statsSession.getHistoryForNetwork(template, FIELDS);
                    final long now = System.currentTimeMillis();
                    Log.d(TAG, " get mobile bytes bucketDuration =  " + history.getBucketDuration());
                    final NetworkStatsHistory.Entry entry = history.getValues(startTime, endTime, now, null);
                    Log.d(TAG, " get mobile bytes current time: now =  " + now);
                    if (entry == null) {
                        Log.d(TAG, " no mobile entry data  ");
                        return 0;
                    }
                    mobileBytes = entry.rxBytes + entry.txBytes;
                    TrafficStats.closeQuietly(statsSession);
                } else {
                    mobileBytes = 0;
                }
            }

        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        Log.d(TAG, " phoneId =  " + phoneId + ", get mobileBytes = " + mobileBytes + " , startTime =  " + startTime + ",  endTime = " + endTime);

        return mobileBytes;
    }


    private String getSubscriberId(int phoneId) {
        int subId = 0;
        int slotId = phoneId;
        String subscriberId = null;

        TelephonyManager mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if(null == mTelephonyManager) {
            Log.d(TAG, "get mTelephonyManager is null !");
            return null;
        }
        if (SubscriptionManager.from(mContext).getActiveSubscriptionInfoForSimSlotIndex(slotId) !=null ) {
            subId = SubscriptionManager.from(mContext)
                                  .getActiveSubscriptionInfoForSimSlotIndex(slotId).getSubscriptionId();
        } else {
            Log.e(TAG, "get subscriberId is null !");
            return null;
        }
        subscriberId = mTelephonyManager.getSubscriberId(subId);

        return subscriberId;
    }

}



