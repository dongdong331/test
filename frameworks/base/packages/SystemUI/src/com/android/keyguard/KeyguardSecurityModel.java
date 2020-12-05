/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.keyguard;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.util.SimLockUtil;

import android.util.Log;

public class KeyguardSecurityModel {

    /**
     * The different types of security available.
     * @see KeyguardSecurityContainer#showSecurityScreen
     */
    public enum SecurityMode {
        Invalid, // NULL state
        None, // No security enabled
        Pattern, // Unlock by drawing a pattern.
        Password, // Unlock by entering an alphanumeric password
        PIN, // Strictly numeric password
        SimPin, // Unlock by entering a sim pin.
        SimPuk, // Unlock by entering a sim puk
        /* Unisoc: Support SimLock @{ */
        SimLock,
        /* @} */
        /* For SubsidyLock feature @{ */
        SubsidyLock_Lock, // Lock UI for SubsidyLock
        SubsidyLock_EnterCode, // Local unlock UI for SubsidyLock
        SubsidyLock_Init //Init UI for SubsidyLock
        /* @} */
    }

    private final static String TAG = "KeyguardSecurityModel";
    private final Context mContext;
    private final boolean mIsPukScreenAvailable;

    private LockPatternUtils mLockPatternUtils;

    KeyguardSecurityModel(Context context) {
        mContext = context;
        mLockPatternUtils = new LockPatternUtils(context);
        mIsPukScreenAvailable = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enable_puk_unlock_screen);
    }

    void setLockPatternUtils(LockPatternUtils utils) {
        mLockPatternUtils = utils;
    }

    SecurityMode getSecurityMode(int userId) {
        KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(mContext);
        //For SubsidyLock feature
        KeyguardSubsidyLockController subsidyController = KeyguardSubsidyLockController.getInstance(mContext);
        if (mIsPukScreenAvailable && SubscriptionManager.isValidSubscriptionId(
                monitor.getNextSubIdForState(IccCardConstants.State.PUK_REQUIRED))) {
            return SecurityMode.SimPuk;
        }

        if (SubscriptionManager.isValidSubscriptionId(
                monitor.getNextSubIdForState(IccCardConstants.State.PIN_REQUIRED))) {
            return SecurityMode.SimPin;
        }

        /* Unisoc: Support SimLock @{ */
        if ((SimLockUtil.isAutoShow() && (SubscriptionManager.isValidSubscriptionId(
                monitor.getNextSubIdForState(IccCardConstants.State.NETWORK_LOCKED))
                || SubscriptionManager.isValidSubscriptionId(
                        monitor.getNextSubIdForState(IccCardConstants.State.NETWORK_SUBSET_LOCKED))
                || SubscriptionManager.isValidSubscriptionId(
                        monitor.getNextSubIdForState(IccCardConstants.State.SERVICE_PROVIDER_LOCKED))
                || SubscriptionManager.isValidSubscriptionId(
                        monitor.getNextSubIdForState(IccCardConstants.State.CORPORATE_LOCKED))
                || SubscriptionManager.isValidSubscriptionId(
                        monitor.getNextSubIdForState(IccCardConstants.State.SIM_LOCKED))
                ||  SubscriptionManager.isValidSubscriptionId(
                        monitor.getNextSubIdForState(IccCardConstants.State.NETWORK_LOCKED_PUK))
                || SubscriptionManager.isValidSubscriptionId(
                        monitor.getNextSubIdForState(IccCardConstants.State.NETWORK_SUBSET_LOCKED_PUK))
                || SubscriptionManager.isValidSubscriptionId(
                        monitor.getNextSubIdForState(IccCardConstants.State.SERVICE_PROVIDER_LOCKED_PUK))
                || SubscriptionManager.isValidSubscriptionId(
                        monitor.getNextSubIdForState(IccCardConstants.State.CORPORATE_LOCKED_PUK))
                || SubscriptionManager.isValidSubscriptionId(
                        monitor.getNextSubIdForState(IccCardConstants.State.SIM_LOCKED_PUK))
                )) || KeyguardSimLockMonitor.isSimLockStateByUser()) {
            if (KeyguardSimLockMonitor.getSimLockCanceled()){
                Log.d(TAG,"getSecurityMode: SimLock already canceled by user!");
                return SecurityMode.None;
            }
            Log.d(TAG,"getSecurityMode: return SimLock");
            return SecurityMode.SimLock;
        }
        /* @} */

        /* For SubsidyLock feature @{ */
        if (subsidyController.isSubsidyLock()) {
            if(subsidyController.isSubsidyEnterCode()){
                return SecurityMode.SubsidyLock_EnterCode;
            } else if(subsidyController.getSubsidyLockMode() ==  KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_LOCK ||
                    subsidyController.getSubsidyLockMode() ==  KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_SWITCH_SIM){
                return SecurityMode.SubsidyLock_Lock;
            }
        }
        /* @} */

        final int security = mLockPatternUtils.getActivePasswordQuality(userId);
        switch (security) {
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
                return SecurityMode.PIN;

            case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
            case DevicePolicyManager.PASSWORD_QUALITY_MANAGED:
                return SecurityMode.Password;

            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                return SecurityMode.Pattern;
            case DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED:
                /* For SubsidyLock feature @{ */
                if (subsidyController.isSubsidyLock()) {
                    if (subsidyController.getSubsidyLockMode() == KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_INIT) {
                        return SecurityMode.SubsidyLock_Init;
                    }
                }
                /* @} */
                return SecurityMode.None;

            default:
                throw new IllegalStateException("Unknown security quality:" + security);
        }
    }
}
