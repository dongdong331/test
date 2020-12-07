
package com.android.settings.location;

import android.content.Context;
import android.location.LocationManagerEx;
import android.util.Log;

/**
 * A helper class used to determine whether cmcc feature is support.
 */

public class GpsHelper {

    private static final String  TAG = GpsHelper.class.getSimpleName();
    private Context mContext;
    private LocationManagerEx mLocationManagerEx;

    static GpsHelper sInstance;

    public GpsHelper(Context context) {
        mContext = context;
        mLocationManagerEx = new LocationManagerEx(mContext);
    }

    public static GpsHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new GpsHelper(context);
        }
        return sInstance;
    }

    public boolean isSupportCmcc() {
        Log.d(TAG, " isSupportCmcc " + (mLocationManagerEx != null && mLocationManagerEx.isSupportCmcc()));
        return mLocationManagerEx != null && mLocationManagerEx.isSupportCmcc();
    }

}
