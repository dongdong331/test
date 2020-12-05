/*
 *Created by spreadst
 */

package com.android.server.location;

import java.util.Calendar;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.location.Location;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SprdLocationUtils {

    private static final String TAG = SprdLocationUtils.class.getSimpleName();
    private static final boolean DEBUG = isDebug();

    // these need to match GnssPositionMode enum in IGnss.hal
    private static final int GPS_POSITION_MODE_STANDALONE = 0;
    private static final int GPS_POSITION_MODE_MS_BASED = 1;
    private static final int GPS_POSITION_MODE_MS_ASSISTED = 2;

    private static final String HEADER_CASE1 = "03020312af90";
    private static final String HEADER_CASE2 = "6170706c69636174696f6e2f766e642e6f6d616c6f632d7375706c2d696e697400af782d6f6d612d6170706c69636174696f6e3a756c702e756100";

    private Context mContext;
    private String mOperatorName = null;

    private boolean mGpsTimeSyncFlag = true;
    private long mLastLocationUpdatesTime = 0;

    public SprdLocationUtils(Context context) {
        mContext = context;
        mOperatorName = getSupportOperator();
        Log.d(TAG, "mOperatorName : " + mOperatorName);
    }

    private static Boolean IS_DEBUG_BUILD = null;

    private static boolean isDebug() {
        if (IS_DEBUG_BUILD == null) {
            IS_DEBUG_BUILD = !Build.TYPE.equals("user");
        }
        return IS_DEBUG_BUILD;
    }

    //SPRD: Bug#692740 support AGPS settings BEG -->
    /**
     * get postion mode from agps settings
     */
    int getPositionMode() {
        int enableOption = Settings.Secure.getInt(mContext.getContentResolver(),
                "assisted_gps_enable_option", 0);
        if (DEBUG)
            Log.d(TAG, "get the agps enable option is " + enableOption);

        // if agps is enable set mode is 1 (assisted_gps_enable_option is 0
        // or 1), and disable set 0 (assisted_gps_enable_option is 2).
        if (enableOption == 2) {
            return GPS_POSITION_MODE_STANDALONE;
        } else {
            return GPS_POSITION_MODE_MS_BASED;
        }
    }
    //<-- support AGPS settings END

    /**
     * support cmcc feature
     */
    boolean isSupportCmcc() {
        return "cmcc".equals(mOperatorName);
    }

    /**
     * get cmcc configuration
     */
    private String getSupportOperator() {
        String operator = null;
        try {
            operator = mContext.getResources().getString(
                    com.android.internal.R.string.config_location_support_operator);
        } catch (NotFoundException e) {
            if (DEBUG)
                Log.d(TAG, "config_location_support_operator cannot be found.");
        }
        if (DEBUG)
            Log.d(TAG, "config_location_support_operator value is " + operator);
        return operator;
    }

    /**
    * SPRD start bug 511190.
    * add extra filter to parse NI wap push message
    * and only message matches SUPL spec can be delivered to HAL
    * more details please check <OMA-ETS-SUPL-V2_0_1> SUPL-2.0-con-001
    */
    private String bytesToHexString(byte[] bytes) {
        if (bytes == null)
            return null;
        StringBuilder ret = new StringBuilder(2 * bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            int b;
            b = 0x0f & (bytes[i] >> 4);
            ret.append("0123456789abcdef".charAt(b));
            b = 0x0f & bytes[i];
            ret.append("0123456789abcdef".charAt(b));
        }
        return ret.toString();
    }

    boolean vaildNiMessage(Intent intent) {
        if (DEBUG)
            Log.d(TAG, "checkWapSuplInit");
        if (intent == null) {
            if (DEBUG)
                Log.d(TAG, "checkWapSuplInit intent is null!");
            return false;
        }
        boolean case1 = false;
        boolean case2 = false;
        int i = 0;
        byte[] header = (byte[]) intent.getExtra("header");
        String realHeader = bytesToHexString(header);
        Log.d(TAG, "checkWapSuplInit header is " + realHeader);
        if (HEADER_CASE1.compareTo(realHeader) == 0) {
            case1 = true;
            if (DEBUG)
                Log.d(TAG, "it's case 1");
        } else if (HEADER_CASE2.compareTo(realHeader) == 0) {
            case2 = true;
            if (DEBUG)
                Log.d(TAG, "it's case 2");
        } else {
            if (DEBUG)
                Log.d(TAG, "neither case 1 nor case 2");
        }
        if (case1 || case2) {
            return true;
        } else {
            return false;
        }
    }

    //SPRD: Bug#692741 AGPS support 4G network BEG -->
    /**
     * get cell identity from Lte by telephony
     */
    CellIdentityLte getLteCellInfo(TelephonyManager phone) {
        List<CellInfo> list = phone.getAllCellInfo();
        if (list != null && list.size() > 0) {
            for (CellInfo cell : list) {
                if (cell instanceof CellInfoLte) {
                    if (cell.isRegistered()) {
                        Log.d(TAG, "get registered CellIdentityLte !");
                        return ((CellInfoLte) cell).getCellIdentity();
                    }
                }
            }
        }
        return null;
    }
    //<-- AGPS support 4G network END

    // SPRD: Bug#692739 support GPS automatic update time BEG -->
    private boolean getAutoGpsState() {
        try {
            return Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.AUTO_TIME_GPS) > 0;
        } catch (SettingNotFoundException snfe) {
            return false;
        }
    }

    public void initLocationUpdatesTime(int mFixInterval, Location location) {
        long currentTime = System.currentTimeMillis() + 200;

        // some one may change the system time after the last fix, if this happen, we reset the
        // mLastLocationUpdatesTime
        if (mLastLocationUpdatesTime >= currentTime) {
            mLastLocationUpdatesTime = 0;
            Log.d(TAG, "last > current");
        }

        Log.d(TAG, "GPS last updates: " + mLastLocationUpdatesTime + "currentTime: "
                + currentTime);
        if (mFixInterval <= 1 || currentTime - mLastLocationUpdatesTime > mFixInterval) {
            mLastLocationUpdatesTime = currentTime;
        }
        if (DEBUG)
            Log.v(TAG,
                    "reportLocation lat: " + location.getLatitude() + " long: "
                            + location.getLongitude() + " timestamp: " + location.getTime());
    }

    public void setLocationUpdatesTime(boolean hasLatLong, Location location) {
        Log.d(TAG, "GPS time sync is enabled, mGpsTimeSyncFlag=" + mGpsTimeSyncFlag
                + "mGpsTimeSyncFlag = " + mGpsTimeSyncFlag);
        if (mGpsTimeSyncFlag && hasLatLong) {
            // Add for "time auto-sync with Gps"
            if (getAutoGpsState()) {
                mGpsTimeSyncFlag = false;
                Log.d(TAG, "GPS time sync is enabled");
                Log.d(TAG, "GPS time sync  Auto-sync time with GPS: timestamp = " + location.getTime());
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(location.getTime());
                long when = c.getTimeInMillis();
                if (when / 1000 < Integer.MAX_VALUE) {
                    SystemClock.setCurrentTimeMillis(when);
                }
                mLastLocationUpdatesTime = System.currentTimeMillis();
            } else {
                Log.d(TAG, "Auto-sync time with GPS is disabled by user settings!");
                Log.d(TAG, "GPS time sync is disabled");
            }
        }
    }

    public boolean getGpsTimeSyncFlag() {
        return mGpsTimeSyncFlag;
    }

    public void setGpsTimeSyncFlag(boolean flag) {
        mGpsTimeSyncFlag = flag;
    }
    //<-- support GPS automatic update time END

}
