/*
 *Created by spreadst
 */

package com.android.server.location;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.widget.Toast;

public class SprdGpsUpdateTimeService {

    private static final String TAG = SprdGpsUpdateTimeService.class.getSimpleName();
    private static final boolean DEBUG = true;

    private static final int EVENT_GPS_TIME_SYNC_CHANGED = 4;
    private static final int GPS_TIME_OUT_DURATION = 5 * 60 * 1000;
    private static final long GPS_TIME_MIN_TIME = 1000;
    private static final float GPS_TIME_MIN_DISTANCE = 0;

    private Context mContext;
    private Handler mGpsHandler;
    private HandlerThread mGpsThread;
    private Thread mGpsTimerThread; // for interrupt
    private LocationManager mLocationManager;
    private boolean mIsGpsTimeSyncRunning = false;
    private GpsTimeSyncObserver mGpsTimeSyncObserver;

    public SprdGpsUpdateTimeService(Context context) {
        mContext = context;
    }

    private boolean getGpsTimeSyncState() {
        try {
            return Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.AUTO_TIME_GPS) > 0;
        } catch (SettingNotFoundException snfe) {
            return false;
        }
    }

    private static class GpsTimeSyncObserver extends ContentObserver {

        private int mMsg;
        private Handler mHandler;

        GpsTimeSyncObserver(Handler handler, int msg) {
            super(handler);
            mHandler = handler;
            mMsg = msg;
        }

        void observe(Context context) {
            ContentResolver resolver = context.getContentResolver();
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.AUTO_TIME_GPS),
                    false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            mHandler.obtainMessage(mMsg).sendToTarget();
        }
    }

    public void onGpsTimeChanged() {
        boolean enable = getGpsTimeSyncState();
        Log.d(TAG, "GPS Time sync is changed to " + enable);
        if (enable) {
            startUsingGpsWithTimeout(GPS_TIME_OUT_DURATION,
                    mContext.getString(com.android.internal.R.string.gps_time_sync_fail_str));
        } else {
            if (mGpsTimerThread != null) {
                mGpsTimerThread.interrupt();
            }
        }
    }

    public void startUsingGpsWithTimeout(final int milliseconds, final String timeoutMsg) {

        if (mIsGpsTimeSyncRunning == true) {
            Log.d(TAG, "WARNING: Gps Time Sync is already run");
            return;
        } else {
            mIsGpsTimeSyncRunning = true;
        }

        Log.d(TAG, "start using GPS for GPS time sync timeout=" + milliseconds + " timeoutMsg="
                + timeoutMsg);
        mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_TIME_MIN_TIME,
                GPS_TIME_MIN_DISTANCE,
                mLocationListener);
        mGpsTimerThread = new Thread() {
            public void run() {
                boolean isTimeout = false;
                try {
                    Thread.sleep(milliseconds);
                    isTimeout = true;
                } catch (InterruptedException e) {
                }
                Log.d(TAG, "GPS time sync isTimeout=" + isTimeout);
                if (isTimeout == true) {
                    Message m = new Message();
                    m.obj = timeoutMsg;
                    mGpsToastHandler.sendMessage(m);
                }
                mLocationManager.removeUpdates(mLocationListener);
                mIsGpsTimeSyncRunning = false;
            }
        };
        mGpsTimerThread.start();
    }

    private Handler mGpsToastHandler = new Handler() {
        public void handleMessage(Message msg) {
            String timeoutMsg = (String) msg.obj;
            Log.d(TAG, "GPS time sync mGpsToastHandler timeoutMsg=" + timeoutMsg);
            Toast.makeText(mContext, timeoutMsg, Toast.LENGTH_LONG).show();
        }
    };

    private LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG, "GPS time sync mLocationListener location=" + location);
            mGpsTimerThread.interrupt();
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    private class MyHandler extends Handler {

        public MyHandler(Looper l) {
            super(l);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_GPS_TIME_SYNC_CHANGED:
                    onGpsTimeChanged();
                    break;
            }
        }
    }

    public void init() {
        Log.d(TAG, "add GPS time sync handler and looper");
        mGpsThread = new HandlerThread(TAG);
        mGpsThread.start();
        mGpsHandler = new MyHandler(mGpsThread.getLooper());

        mGpsTimeSyncObserver = new GpsTimeSyncObserver(mGpsHandler, EVENT_GPS_TIME_SYNC_CHANGED);
        mGpsTimeSyncObserver.observe(mContext);
    }
}
