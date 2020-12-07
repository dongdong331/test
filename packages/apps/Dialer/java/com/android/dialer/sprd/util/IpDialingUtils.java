
package com.android.dialer.sprd.util;

import java.util.List;
import java.util.ArrayList;

//import com.google.android.collect.Lists;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

/***********************************************************
 *              SPRD: bug877517 FEATURE_CALL_DETAIL_ACTIONS
 * *********************************************************/
public class IpDialingUtils {
    private static final String TAG = "IpDialingUtils";
    private static final boolean DBG = true; //Debug.isDebug();
    // Extra on intent containing the id of a subscription.
    public static final String SUB_ID_EXTRA =
            "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionId";
    // Extra on intent containing the label of a subscription.
    public static final String SUB_LABEL_EXTRA =
            "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionLabel";
    private static final String IP_DIALING_SHARED_PREFERENCES_NAME = "ipdial_preferences";
    private static final String KEY_IP_NUMBER = "ip_number";
    private static final String KEY_SELECTED_PREFERENCES_NUMBER = "selected_number_pos";

    public static final String EXTRA_IS_IP_DIAL = "is_ip_dial";
    public static final String EXTRA_IP_PRFIX_NUM = "ip_prefix_num";

    private static final int NUMBER_COUNT = 5;
    private SharedPreferences mPreference;
    private Editor mEditor;

    @SuppressWarnings("static-access")
    public IpDialingUtils(Context context) {
        mPreference = context.getSharedPreferences(IP_DIALING_SHARED_PREFERENCES_NAME,
                    Context.MODE_PRIVATE);
        mEditor = mPreference.edit();
    }

    public static final String EXCLUDE_PREFIX[] = new String[]{
            "+86", "0086"
    };

    public void setIpNumber(String ipNumber, int editTextNumber, int subId) {
        if (DBG) {
            Log.d(TAG, "ipNumber = " + ipNumber + " editTextNumber = "
                    + editTextNumber + " subId= " + subId);
        }
        mEditor.putString(subId + KEY_IP_NUMBER + editTextNumber, ipNumber);
        mEditor.apply();
    }

    public String getIpNumber(int editTextNumber, int subId) {
        return mPreference.getString(subId + KEY_IP_NUMBER + editTextNumber, "");
    }

    public void setIpPreferenceNumber(int num, int subId) {
        mEditor.putInt(subId + KEY_SELECTED_PREFERENCES_NUMBER, num);
        mEditor.apply();
    }

    public int getIpPreferenceNumber(int subId) {
        return mPreference.getInt(subId + KEY_SELECTED_PREFERENCES_NUMBER, 0);
    }

    public String getIpDialNumber(int subId) {
        int ipPreferenceNumber = mPreference.getInt(subId + KEY_SELECTED_PREFERENCES_NUMBER, -1);
        if (DBG) {
            Log.d(TAG, "ipPreferenceNumber = " + ipPreferenceNumber);
        }
        return mPreference.getString(subId + KEY_IP_NUMBER + ipPreferenceNumber, "");
    }

    public String[] getAllIpNumberArray(int subId) {
        List<String> list = new ArrayList<String>();//Lists.newArrayList();
        for (int i = 0; i < NUMBER_COUNT; i++) {
            String num = getIpNumber(i, subId);
            if (!TextUtils.isEmpty(num) && TextUtils.isDigitsOnly(num)) {
                list.add(num);
            }
        }
        return list.toArray(new String[0]);
    }

    public int getSubId(Context context, Intent intent) {
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (intent != null) {
            PhoneAccountHandle phoneAccountHandle =
                    intent.getParcelableExtra(TelephonyManager.EXTRA_PHONE_ACCOUNT_HANDLE);
            if (phoneAccountHandle != null) {
                subId = getSubIdForPhoneAccountHandle(context, phoneAccountHandle);
            }
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                subId = intent.getIntExtra(SUB_ID_EXTRA, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            }
        }
        return subId;
    }

    private static int getSubIdForPhoneAccountHandle(Context context, PhoneAccountHandle handle) {
        List<SubscriptionInfo> result = SubscriptionManager.from(
                context).getActiveSubscriptionInfoList();

        if (result != null) {
            String iccId = handle.getId();
            for (SubscriptionInfo subInfo : result) {
                if (iccId != null && subInfo != null && iccId.equals(subInfo.getIccId())) {
                    return subInfo.getSubscriptionId();
                }
            }
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }
}
