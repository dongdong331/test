/*
 *Created by spreadst
 */

package android.net.wifi;

import android.content.Context;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;


public class WifiFeaturesUtils {

    private static final String TAG = "WifiFeaturesUtils";

//// CMCC Features Start ////
    /**
     * SPRD: add for cmcc wifi feature, 0 is auto switch, 1 is manual switch, 2 is always ask.
     * @hide
     */
    public static final String WIFI_MOBILE_TO_WLAN_POLICY = "wifi_mobile_to_wlan_policy";

    /**
     * SPRD: Broadcast intent action indicating the ap is removed from scan_results.
     * @hide
     */
    public static final String WIFI_SCAN_RESULT_BSS_REMOVED_ACTION
        = "sprd.net.wifi.BSS_REMOVED_ACTION";

    /**
     * SPRD: Broadcast intent action indicating the connected ap is absent.
     * @hide
     */
    public static final String WIFI_CONNECTED_AP_ABSENT_ACTION
        = "sprd.net.wifi.WIFI_CONNECTED_AP_ABSENT";

    /**
     * SPRD: Broadcast intent action indicating wifi is disabling with connected state.
     * @hide
     */
    public static final String WIFI_DISABLED_WHEN_CONNECTED_ACTION
        = "sprd.net.wifi.WIFI_DISABLED_WHEN_CONNECTED";

    /**
     * SPRD: Broadcast intent action indicating the ap is removed from scan_results.
     * @hide
     */
    public static final String WIFI_SCAN_RESULTS_AVAILABLE_ACTION
        = "sprd.net.wifi.SCAN_RESULTS_AVAILABLE";

    /**
     * SPRD: intent action indicating setting alarm to connect wifi
     * @hide
     */
    public static final String ALARM_FOR_CONNECT_WIFI_ACTION = "sprd.wifi.alarm.CONNECT_WIFI";

    /**
     * SPRD: intent action indicating setting alarm to disconnect wifi
     * @hide
     */
    public static final String ALARM_FOR_DISCONNECT_WIFI_ACTION = "sprd.wifi.alarm.DISCONNECT_WIFI";

    // wifi connect alarm flag, and its hour and minute flags.
    /** @hide */
    public static final String WIFI_CONNECT_ALARM_FLAG = "wifi_connect_alarm_flag";
    /** @hide */
    public static final String WIFI_CONNECT_ALARM_HOUR = "wifi_connect_alarm_hour";
    /** @hide */
    public static final String WIFI_CONNECT_ALARM_MINUTE = "wifi_connect_alarm_minute";

    // wifi disconnect alarm flag, and its hour and minute flags.
    /** @hide */
    public static final String WIFI_DISCONNECT_ALARM_FLAG = "wifi_disconnect_alarm_flag";
    /** @hide */
    public static final String WIFI_DISCONNECT_ALARM_HOUR = "wifi_disconnect_alarm_hour";
    /** @hide */
    public static final String WIFI_DISCONNECT_ALARM_MINUTE = "wifi_disconnect_alarm_minute";
    /** @hide */
    public static final int INTERVAL_MILLIS = 1000 * 60 * 60 * 24; //24h

    /** @hide */
    public static final String WIFI_AUTO_CONNECT_FLAG = "wifi_auto_connect_flag";

    /** @hide */
    public void setAutoConnect(boolean autoConnect) {
        if (isAutoConnect() != autoConnect) {
            Settings.Global.putInt(mContext.getContentResolver(), WIFI_AUTO_CONNECT_FLAG, (autoConnect ? 1 : 0));
        }
    }

    /** @hide */
    public boolean isAutoConnect() {
        return Settings.Global.getInt(mContext.getContentResolver(), WIFI_AUTO_CONNECT_FLAG, 1) == 1;
    }

    /** @hide */
    public boolean isSupportCMCC() {
        return "cmcc".equals(mWifiOperator);
    }
//// CMCC Feature end ////

    /** @hide */
    public static final String AUTO_JOIN_VODAFONE_WIFI_SSID = "VodafoneWiFi";

    /** @hide */
    public static final String SUPPORT_OPERATOR_VODAFONE = "vodafone";

    /** @hide */
    public static final String SUPPORT_OPERATOR_VODAFONE_UK = "vodafone-uk";

    /** @hide */
    public static final String SUPPORT_OPERATOR_AIS = "ais-th";

    private Context mContext;
    private String mWifiOperator;
    private static WifiFeaturesUtils mInstance;

    public WifiFeaturesUtils(Context  context) {
        mContext = context;
        try{
            mWifiOperator = context.getResources().getString(com.android.internal.R.string.config_wifi_operator);
        } catch (Resources.NotFoundException e) {
        }
        Log.d(TAG, "WifiFeaturesUtils config_wifi_operator = " + mWifiOperator);
    }

    /** @hide */
    public static WifiFeaturesUtils getInstance(Context context) {
       if (mInstance == null) {
           mInstance = new WifiFeaturesUtils(context);
       }
       return mInstance;
    }

    /** @hide */
    public String getOperatorSupport() {
        return mWifiOperator;
    }

    /** @hide */
    public boolean isSupportCUCC() {
        return "cucc".equals(mWifiOperator);
    }


    /** @hide */
    public static class FeatureProperty {
        private FeatureProperty() { }

        /** @hide */
        public static final boolean SUPPORT_SPRD_WCN = SystemProperties.get("ro.wcn").equals("enabled");
        /** @hide */
        public static final boolean SUPPORT_SPRD_WIFI_FEATURES
                = SystemProperties.getBoolean("ro.wifi.sup_sprd", true);
        /** @hide */
        public static final boolean SUPPORT_SPRD_WIFI_WAPI
                = SystemProperties.getBoolean("ro.wifi.sup_sprd.wapi", true);
        /** @hide */
        public static final boolean SUPPORT_SPRD_WIFI_80211W
                = SystemProperties.getBoolean("ro.wifi.sup_sprd.80211w", true);
        /** @hide */
        public static final boolean SUPPORT_SPRD_WIFI_CHECK_NETWORKS
                = SystemProperties.getBoolean("ro.wifi.sup_sprd.check_network", true);
        /** @hide */
        public static final boolean SUPPORT_SPRD_WIFI_POCKET_MODE
                = SystemProperties.getBoolean("ro.wifi.sup_sprd.pocketmode", true);
        /** @hide */
        public static final boolean SUPPORT_SPRD_WIFI_AUTO_ROAM
                = SystemProperties.getBoolean("ro.wifi.sup_sprd.auto_roam", true);
        /** @hide */
        public static final boolean SUPPORT_SPRD_WIFI_COEXIST_P2P
                = SystemProperties.getBoolean("ro.wifi.sup_sprd.coexist_p2p", true);

        /** @hide */
        public static final boolean SUPPORT_SPRD_SOFTAP_FEATURES
                = SystemProperties.getBoolean("ro.softap.sup_sprd", true);
        /** @hide */
        public static final boolean SUPPORT_SPRD_SOFTAP_COEXIST_LTE
                = SystemProperties.getBoolean("ro.softap.sup_sprd.coexist_lte", true);
        /** @hide */
        public static final boolean SUPPORT_SPRD_SOFTAP_WHITE_LIST
                = SystemProperties.getBoolean("ro.softap.sup_sprd.whitelist", true);
        /** @hide */
        public static final boolean SUPPORT_SPRD_SOFTAP_COEXIST_BT
                = SystemProperties.getBoolean("ro.softap.sup_sprd.coexist_bt", true);
        /** @hide */
        public static final boolean SUPPORT_SPRD_SOFTAP_HIDE_SSID
                = SystemProperties.getBoolean("ro.softap.sup_sprd.hidessid", true);
        /** @hide */
        public static final boolean SUPPORT_SPRD_SOFTAP_HIDE_5G_CHANNEL
                = SystemProperties.getBoolean("ro.softap.sup_sprd.hide5gchannel", true);
        /** @hide */
        public static final boolean SUPPORT_SPRD_SOFTAP_5G_FEATURE
                = SystemProperties.getBoolean("ro.softap.sup_sprd.support5G", false);
        /** @hide */
        public static final int SUPPORT_SPRD_SOFTAP_MAX_NUMBER
                = SystemProperties.getInt("ro.softap.sup_sprd.reqstanum",
                        SystemProperties.getInt("ro.softap.sup_sprd.maxstanum", 8));
    }


}
