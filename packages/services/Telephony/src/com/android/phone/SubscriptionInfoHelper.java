/**
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.phone;

import java.util.List;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.PersistableBundle;
import android.telecom.PhoneAccountHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

/**
 * Helper for manipulating intents or components with subscription-related information.
 *
 * In settings, subscription ids and labels are passed along to indicate that settings
 * are being changed for particular subscriptions. This helper provides functions for
 * helping extract this info and perform common operations using this info.
 */
public class SubscriptionInfoHelper {

    // Extra on intent containing the id of a subscription.
    public static final String SUB_ID_EXTRA =
            "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionId";
    // Extra on intent containing the label of a subscription.
    private static final String SUB_LABEL_EXTRA =
            "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionLabel";

    private static final String TAG = "SubscriptionInfoHelper";
    private Context mContext;

    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private String mSubLabel;

    /**
     * Instantiates the helper, by extracting the subscription id and label from the intent.
     */
    public SubscriptionInfoHelper(Context context, Intent intent) {
        mContext = context;
        PhoneAccountHandle phoneAccountHandle =
                intent.getParcelableExtra(TelephonyManager.EXTRA_PHONE_ACCOUNT_HANDLE);
        if (phoneAccountHandle != null) {
            mSubId = PhoneUtils.getSubIdForPhoneAccountHandle(phoneAccountHandle);
        }
        if (mSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            mSubId = intent.getIntExtra(SUB_ID_EXTRA, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        }
        mSubLabel = intent.getStringExtra(SUB_LABEL_EXTRA);
    }

    /**
     * @param newActivityClass The class of the activity for the intent to start.
     * @return Intent containing extras for the subscription id and label if they exist.
     */
    public Intent getIntent(Class newActivityClass) {
        Intent intent = new Intent(mContext, newActivityClass);

        if (hasSubId()) {
            intent.putExtra(SUB_ID_EXTRA, mSubId);
        }

        if (!TextUtils.isEmpty(mSubLabel)) {
            intent.putExtra(SUB_LABEL_EXTRA, mSubLabel);
        }

        return intent;
    }

    public static void addExtrasToIntent(Intent intent, SubscriptionInfo subscription) {
        if (subscription == null) {
            return;
        }

        intent.putExtra(SubscriptionInfoHelper.SUB_ID_EXTRA, subscription.getSubscriptionId());
        intent.putExtra(
                SubscriptionInfoHelper.SUB_LABEL_EXTRA, subscription.getDisplayName().toString());
    }

    /**
     * @return Phone object. If a subscription id exists, it returns the phone for the id.
     */
    // UNISOC: modify for bug896038
    public Phone getPhone() {
        return hasSubId()
                ? PhoneFactory.getPhone(SubscriptionManager.getPhoneId(mSubId))
                : getDefaultPhone();
    }

    /**
     * Sets the action bar title to the string specified by the given resource id, formatting
     * it with the subscription label. This assumes the resource string is formattable with a
     * string-type specifier.
     *
     * If the subscription label does not exists, leave the existing title.
     */
    public void setActionBarTitle(ActionBar actionBar, Resources res, int resId) {
        if (actionBar == null || TextUtils.isEmpty(mSubLabel)) {
            return;
        }

        if (!TelephonyManager.from(mContext).isMultiSimEnabled()) {
            return;
        }

        String title = String.format(res.getString(resId), mSubLabel);
        actionBar.setTitle(title);
    }

    public boolean hasSubId() {
        return mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    public int getSubId() {
        return mSubId;
    }

    /* UNISOC: add for FEATURE_VIDEO_CALL_FOR @{ */
    public boolean getCarrierValueByKey(String key) {
        Log.d(TAG, "getCarrierValueBykey key = " + key);
        boolean value = false;
        CarrierConfigManager carrierConfigManager = (CarrierConfigManager) mContext.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        if (carrierConfigManager != null) {
            PersistableBundle globalConfig = carrierConfigManager.getConfigForSubId(
                    getPhone().getSubId());
            if (globalConfig != null) {
                value = globalConfig.getBoolean(key);
                Log.d(TAG, "Value = " + value + " phone slot = " + getPhone().getPhoneId());
            }
        }
        return value;
    }
    /* @} */

    /* UNISOC: add for feature 888845 @{ */
    public int getCarrierIntValueByKey(String key) {
        Log.d(TAG, "getCarrierIntValueByKey key = " + key);
        int value = -1;
        CarrierConfigManager carrierConfigManager = (CarrierConfigManager) mContext.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        if (carrierConfigManager != null) {
            PersistableBundle globalConfig = carrierConfigManager.getConfigForSubId(
                    getPhone().getSubId());
            if (globalConfig != null) {
                value = globalConfig.getInt(key);
                Log.d(TAG, "Value = " + value + " phone slot = " + getPhone().getPhoneId());
            }
        }
        return value;
    }
    /* @} */

    /* UNISOC: FEATURE_VOLTE_CALLFORWARD_OPTIONS @{ */
    public Intent getCallSettingsIntent(Class newActivityClass, Intent intent) {
        if (hasSubId()) {
            intent.putExtra(SUB_ID_EXTRA, mSubId);
        }
        if (!TextUtils.isEmpty(mSubLabel)) {
            intent.putExtra(SUB_LABEL_EXTRA, mSubLabel);
        }
        if (mContext.getPackageManager().resolveActivity(intent, 0) == null) {
            Log.i("SubscriptionInfoHelper", intent + " is not exist");
            if (newActivityClass == null) {
                return null;
            }
            intent.setClass(mContext, newActivityClass);
        }
        return intent;
    }
    /* @} */

    /* UNISOC: add for bug896038 @{ */
    private Phone getDefaultPhone() {
        Phone phone = PhoneGlobals.getPhone();
        List<SubscriptionInfo> subList = SubscriptionManager.from(mContext)
                .getActiveSubscriptionInfoList();
        if (subList != null && subList.size() == 1) {
            phone = PhoneFactory.getPhone(subList.get(0).getSimSlotIndex());
        }
        Log.d(TAG, "phone slot : " + phone.getPhoneId() + " sub id : " + phone.getSubId());
        return phone;
    }
    /* @} */
}
