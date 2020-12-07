package com.android.phone;

import android.arch.lifecycle.Lifecycle.Event;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.PhoneConstants;

public class NetworkLifecycleObserver implements LifecycleObserver{
    private static final String LOG_TAG = "NetworkLifecycleObserver";
    private Context mContext;
    private BroadcastReceiver mSimStateChangedReceiver;
    private NetworkSelectListPreference mPreference;

    public NetworkLifecycleObserver(Context context, NetworkSelectListPreference preference){
        mContext = context;
        mPreference = preference;
        mSimStateChangedReceiver = new SimStateChangeReceiver();
    }

    @OnLifecycleEvent(Event.ON_CREATE)
    public void onCreate() {
        Log.d(LOG_TAG, "onCreate");

        if(mSimStateChangedReceiver != null){
            IntentFilter intentFilter =
                    new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            mContext.registerReceiver(mSimStateChangedReceiver, intentFilter);
        }
    }

    @OnLifecycleEvent(Event.ON_DESTROY)
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        if(mSimStateChangedReceiver != null){
            mContext.unregisterReceiver(mSimStateChangedReceiver);
            mPreference = null;
            mContext = null;
        }
    }

    private class SimStateChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(LOG_TAG,"receive broadcast: " + action);
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)){
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                        SubscriptionManager.INVALID_PHONE_INDEX);
                String simState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                Log.d(LOG_TAG,"phoneId: " + phoneId + ", simState: " + simState);
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simState)){
                    if (mPreference == null) {
                        Log.d(LOG_TAG, "mPreference is null");
                        return;
                    }

                    int scanPhoneId = mPreference.getScanPhoneId();
                    if (phoneId == scanPhoneId) {
                        mPreference.dismissWarningDialog();
                        mPreference.dismissProgressBar();
                        mPreference.dismissSelectionFailedDialog();
                        Dialog dialog = mPreference.getDialog();
                        if (dialog == null){
                            Log.d(LOG_TAG, "Networks list dialog not exist");
                            return;
                        }
                        Log.d(LOG_TAG,"Dismiss networks list");
                        dialog.dismiss();
                    } else {
                        Log.d(LOG_TAG,"phoneId used to scan network: " + scanPhoneId);
                    }
                }
            }
        }
    }
}
