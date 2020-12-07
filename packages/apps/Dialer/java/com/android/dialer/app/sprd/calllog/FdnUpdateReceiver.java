package com.android.dialer.app.sprd.calllog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManagerEx;

import com.android.dialer.app.sprd.telcel.DialerTelcelHelper;
import android.util.Log;
import com.android.dialer.app.R;

public class FdnUpdateReceiver extends BroadcastReceiver {
    TelephonyManager mTelephonyManager;
    private static final String ACTION_FDN_STATUS_CHANGED =
            "android.callsettings.action.FDN_STATUS_CHANGED";
    private static final String ACTION_FDN_LIST_CHANGED =
            "android.callsettings.action.FDN_LIST_CHANGED";
    private static final String ACTION_SIM_STATE_CHANGED
            = "android.intent.action.SIM_STATE_CHANGED";
    private static final String ACTION_FDN_STATUS_CHANGED0 =
             "android.fdnintent.action.FDN_STATE_CHANGED0";
    private static final String ACTION_FDN_STATUS_CHANGED1 =
            "android.fdnintent.action.FDN_STATE_CHANGED";
    private static final String INTENT_EXTRA_SUB_ID = "subid";
    private static final String INTENT_EXTRA_NUMBER = "number";
    public static final String INTENT_VALUE_ICC_ABSENT = "ABSENT";
    public static final String INTENT_VALUE_ICC_LOADED = "LOADED";
    public static final String INTENT_KEY_ICC_STATE = "ss";
    public static final String SUBSCRIPTION_KEY = "subscription";
    private static final int NO_SUB_ID = -1;
    private static final int INVALID_SUBSCRIPTION_ID = -1;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!context.getResources().getBoolean(R.bool.is_telcel_version)) {
            return;
        }
        String action = intent.getAction();
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (ACTION_FDN_STATUS_CHANGED.equals(action)) {
            int subId = intent.getIntExtra(INTENT_EXTRA_SUB_ID, NO_SUB_ID);
            String number = intent.getStringExtra(INTENT_EXTRA_NUMBER);
            if (TelephonyManagerEx.from(context).getIccFdnEnabled(subId)) {
                DialerTelcelHelper.getInstance(context).queryFdnList(subId, context);
            } else {
                DialerTelcelHelper.getInstance(context).refreshFdnListCache(subId, context);
            }
        } else if (ACTION_FDN_LIST_CHANGED.equals(action)) {
            int subId = intent.getIntExtra(INTENT_EXTRA_SUB_ID, NO_SUB_ID);
            String number = intent.getStringExtra(INTENT_EXTRA_NUMBER);
            DialerTelcelHelper.getInstance(context).queryFdnList(subId, context);
        } else if (ACTION_SIM_STATE_CHANGED.equals(action)) {
            String state = intent.getStringExtra(INTENT_KEY_ICC_STATE);
            int subId = intent.getIntExtra(SUBSCRIPTION_KEY,
                    INVALID_SUBSCRIPTION_ID);
            if (INTENT_VALUE_ICC_LOADED.equals(state)) {
                if (TelephonyManagerEx.from(context).getIccFdnEnabled(subId)) {
                    DialerTelcelHelper.getInstance(context).queryFdnList(
                            subId, context);
                } else {
                    DialerTelcelHelper.getInstance(context).refreshFdnListCache(
                            subId, context);
                }
            } else if (INTENT_VALUE_ICC_ABSENT.equals(state)) {
                DialerTelcelHelper.getInstance(context).refreshFdnListCache(
                        subId, context);
            }
        }
    }
}