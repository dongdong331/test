/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License.
 */
package com.android.settings.network;

import static android.os.UserHandle.myUserId;
import static android.os.UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.support.annotation.VisibleForTesting;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedPreference;
import com.android.settingslib.Utils;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.util.List;

public class MobileNetworkPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, LifecycleObserver, OnStart, OnStop {

    private static final String KEY_MOBILE_NETWORK_SETTINGS = "mobile_network_settings";

    private final boolean mIsSecondaryUser;
    private final TelephonyManager mTelephonyManager;
    private final UserManager mUserManager;
    private Preference mPreference;
    @VisibleForTesting
    PhoneStateListener mPhoneStateListener;
    private SubscriptionManager mSubscriptionManager;
    private SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangeListener;
    // UNISOC: bug 904899
    private boolean mHasActiveSubscriptions = false;

    private BroadcastReceiver mAirplanModeChangedReceiver;

    public MobileNetworkPreferenceController(Context context) {
        super(context);
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mIsSecondaryUser = !mUserManager.isAdminUser();

        mAirplanModeChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateState(mPreference);
            }
        };
        mSubscriptionManager = SubscriptionManager.from(mContext);
        if(Looper.getMainLooper() == Looper.myLooper()){
            mOnSubscriptionsChangeListener
                    = new SubscriptionManager.OnSubscriptionsChangedListener() {
                @Override
                public void onSubscriptionsChanged() {
                    updateSubscriptions();
                }
            };
        }
    }

    private void updateSubscriptions() {
        if (mUserManager != null && mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
            return;
        }
        if (mPreference == null) {
            return;
        }
        List<SubscriptionInfo> sil = SubscriptionManager.from(mContext).getActiveSubscriptionInfoList();
        if (sil != null && sil.size() > 0) {
            /* UNISOC: bug 904899 @{ */
            mHasActiveSubscriptions = true;
        } else {
            mHasActiveSubscriptions = false;
        }
        updateState(mPreference);
        /* @} */
    }

    @Override
    public boolean isAvailable() {
        return !isUserRestricted() && !Utils.isWifiOnly(mContext);
    }

    public boolean isUserRestricted() {
        return mIsSecondaryUser ||
                RestrictedLockUtils.hasBaseUserRestriction(
                        mContext,
                        DISALLOW_CONFIG_MOBILE_NETWORKS,
                        myUserId());
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public String getPreferenceKey() {
        return KEY_MOBILE_NETWORK_SETTINGS;
    }

    @Override
    public void onStart() {
        if (isAvailable()) {
            if (mPhoneStateListener == null) {
                mPhoneStateListener = new PhoneStateListener() {
                    @Override
                    public void onServiceStateChanged(ServiceState serviceState) {
                        updateState(mPreference);
                    }
                };
            }
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
        }
        if (mAirplanModeChangedReceiver != null) {
            mContext.registerReceiver(mAirplanModeChangedReceiver,
                new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED));
        }
        if (mOnSubscriptionsChangeListener != null) {
            mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        }
    }

    @Override
    public void onStop() {
        if (mPhoneStateListener != null) {
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        if (mAirplanModeChangedReceiver != null) {
            mContext.unregisterReceiver(mAirplanModeChangedReceiver);
        }
        if (mOnSubscriptionsChangeListener != null) {
            mSubscriptionManager.removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        }
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);

        if (preference instanceof RestrictedPreference &&
            ((RestrictedPreference) preference).isDisabledByAdmin()) {
                return;
        }
        // UNISOC: bug 904899
        if (!mHasActiveSubscriptions) {
            preference.setSummary(null);
        }
        preference.setEnabled(Settings.Global.getInt(
            mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 0
            && mHasActiveSubscriptions);
    }

    @Override
    public CharSequence getSummary() {
        /* UNISOC: bug911549&981071 Show network operator name as summary only when in service. @{ */
        ServiceState state = mTelephonyManager.getServiceState();
        if (state != null) {
            int regState = state.getVoiceRegState();
            int dataRegState = state.getDataRegState();
            if ((regState == ServiceState.STATE_OUT_OF_SERVICE
                    || regState == ServiceState.STATE_POWER_OFF)
                    && (dataRegState == ServiceState.STATE_IN_SERVICE)) {
                regState = dataRegState;
            }
            if ( regState == ServiceState.STATE_IN_SERVICE) {
                return mTelephonyManager.getNetworkOperatorName();
            }
        }
        return "";
        /* @} */
    }
}
