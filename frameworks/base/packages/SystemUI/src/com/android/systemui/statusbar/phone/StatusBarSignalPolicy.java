/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import static com.android.systemui.statusbar.policy.NetworkControllerImpl.KEEP_AOSP;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManagerEx;
import android.util.ArraySet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;

import com.android.settingslib.graph.SignalDrawable;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.policy.DarkIconDispatcher;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.NetworkControllerImpl;
import com.android.systemui.statusbar.policy.SecurityController;
import com.android.systemui.tuner.TunerService.Tunable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class StatusBarSignalPolicy implements NetworkControllerImpl.SignalCallback,
        SecurityController.SecurityControllerCallback, Tunable {
    private static final String TAG = "StatusBarSignalPolicy";

    private final String mSlotAirplane;
    private final String mSlotMobile;
    private final String mSlotWifi;
    private final String mSlotEthernet;
    private final String mSlotVpn;
    private final String mSlotVowifi;
    private final String mSlotHdVoice;

    private final Context mContext;
    private final StatusBarIconController mIconController;
    private final NetworkController mNetworkController;
    private final SecurityController mSecurityController;
    private final Handler mHandler = Handler.getMain();

    private boolean mBlockAirplane;
    private boolean mBlockMobile;
    private boolean mBlockWifi;
    private boolean mBlockEthernet;
    private boolean mActivityEnabled;
    private boolean mForceBlockWifi;
    /* UNISOC: add for FEATURE bug886899 @{ */
    private boolean mShowAllSims;
    private boolean mShowSignalTowerIcon;
    /* @} */
    /* UNISOC: add for FEATURE bug887088 @{ */
    private boolean mShowDisabledSims;
    private HashMap<Integer, Integer> mDisabledPhoneMaps = new HashMap<Integer, Integer>();
    /* @} */
    private boolean mAlwaysShowRAT;

    // Track as little state as possible, and only for padding purposes
    private boolean mIsAirplaneMode = false;
    private boolean mWifiVisible = false;
    private boolean mVowifiConnected = false;
    private boolean mHdVoiceVisible = false;

    private ArrayList<MobileIconState> mMobileStates = new ArrayList<MobileIconState>();
    private WifiIconState mWifiIconState = new WifiIconState();

    public StatusBarSignalPolicy(Context context, StatusBarIconController iconController) {
        mContext = context;

        mSlotAirplane = mContext.getString(com.android.internal.R.string.status_bar_airplane);
        mSlotMobile   = mContext.getString(com.android.internal.R.string.status_bar_mobile);
        mSlotWifi     = mContext.getString(com.android.internal.R.string.status_bar_wifi);
        mSlotEthernet = mContext.getString(com.android.internal.R.string.status_bar_ethernet);
        mSlotVpn      = mContext.getString(com.android.internal.R.string.status_bar_vpn);
        mActivityEnabled = mContext.getResources().getBoolean(R.bool.config_showActivity);
        mSlotVowifi = mContext.getString(com.android.internal.R.string.status_bar_vowifi);
        mShowAllSims = mContext.getResources().getBoolean(
                R.bool.config_always_show_all_sims_signalbar);
        mShowSignalTowerIcon = mContext.getResources().getBoolean(
                R.bool.config_show_stat_sys_tower_icon_signalbar);
        mShowDisabledSims = mContext.getResources().getBoolean(
                R.bool.config_always_show_all_disabled_sim_signalbar);
        mSlotHdVoice = mContext.getString(com.android.internal.R.string.status_bar_hdvoice);
        mAlwaysShowRAT = mContext.getResources().getBoolean(R.bool.config_alwaysShowRAT);

        mIconController = iconController;
        mNetworkController = Dependency.get(NetworkController.class);
        mSecurityController = Dependency.get(SecurityController.class);

        mNetworkController.addCallback(this);
        mSecurityController.addCallback(this);
    }

    public void destroy() {
        mNetworkController.removeCallback(this);
        mSecurityController.removeCallback(this);
    }

    private void updateVpn() {
        boolean vpnVisible = mSecurityController.isVpnEnabled();
        int vpnIconId = currentVpnIconId(mSecurityController.isVpnBranded());

        mIconController.setIcon(mSlotVpn, vpnIconId, null);
        mIconController.setIconVisibility(mSlotVpn, vpnVisible);
    }

    private int currentVpnIconId(boolean isBranded) {
        return isBranded ? R.drawable.stat_sys_branded_vpn : R.drawable.stat_sys_vpn_ic;
    }

    /**
     * From SecurityController
     */
    @Override
    public void onStateChanged() {
        mHandler.post(this::updateVpn);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (!StatusBarIconController.ICON_BLACKLIST.equals(key)) {
            return;
        }
        ArraySet<String> blockList = StatusBarIconController.getIconBlacklist(newValue);
        boolean blockAirplane = blockList.contains(mSlotAirplane);
        boolean blockMobile = blockList.contains(mSlotMobile);
        boolean blockWifi = blockList.contains(mSlotWifi);
        boolean blockEthernet = blockList.contains(mSlotEthernet);

        if (blockAirplane != mBlockAirplane || blockMobile != mBlockMobile
                || blockEthernet != mBlockEthernet || blockWifi != mBlockWifi) {
            mBlockAirplane = blockAirplane;
            mBlockMobile = blockMobile;
            mBlockEthernet = blockEthernet;
            mBlockWifi = blockWifi || mForceBlockWifi;
            // Re-register to get new callbacks.
            mNetworkController.removeCallback(this);
        }
    }

    @Override
    public void setWifiIndicators(boolean enabled, IconState statusIcon, IconState qsIcon,
            boolean activityIn, boolean activityOut, String description, boolean isTransient,
            String statusLabel) {

        boolean visible = statusIcon.visible && !mBlockWifi;
        boolean in = activityIn && mActivityEnabled && visible;
        boolean out = activityOut && mActivityEnabled && visible;

        WifiIconState newState = mWifiIconState.copy();
        mWifiVisible = visible;
        newState.visible = visible;
        newState.resId = statusIcon.icon;
        newState.activityIn = in;
        newState.activityOut = out;
        newState.slot = mSlotWifi;
        newState.airplaneSpacerVisible = mIsAirplaneMode;
        newState.contentDescription = statusIcon.contentDescription;

        MobileIconState first = getFirstMobileState();
        newState.signalSpacerVisible = first != null && first.typeId != 0;

        updateWifiIconWithState(newState);
        mWifiIconState = newState;
    }

    private void updateShowWifiSignalSpacer(WifiIconState state) {
        MobileIconState first = getFirstMobileState();
        state.signalSpacerVisible = first != null && first.typeId != 0;
    }

    private void updateWifiIconWithState(WifiIconState state) {
        if (state.visible && state.resId > 0) {
            mIconController.setSignalIcon(mSlotWifi, state);
            mIconController.setIconVisibility(mSlotWifi, true);
        } else {
            mIconController.setIconVisibility(mSlotWifi, false);
        }
    }

    @Override
    public void setMobileDataIndicators(IconState statusIcon, IconState qsIcon, int statusType,
            int qsType, boolean activityIn, boolean activityOut, String typeContentDescription,
            String description, boolean isWide, int subId, boolean roaming) {
        MobileIconState state = getState(subId);
        if (state == null) {
            return;
        }

        // Visibility of the data type indicator changed
        boolean typeChanged = statusType != state.typeId && (statusType == 0 || state.typeId == 0);

        state.visible = statusIcon.visible && !mBlockMobile;
        state.strengthId = statusIcon.icon;
        if (mWifiVisible && !mAlwaysShowRAT) {
            state.typeId = 0;
        } else {
            state.typeId = statusType;
        }
        state.contentDescription = statusIcon.contentDescription;
        state.typeContentDescription = typeContentDescription;
        state.roaming = roaming;
        state.activityIn = activityIn && mActivityEnabled;
        state.activityOut = activityOut && mActivityEnabled;

        // Always send a copy to maintain value type semantics
        mIconController.setMobileIcons(mSlotMobile, MobileIconState.copyStates(mMobileStates));
        Log.d(TAG, "state.visible = " + state.visible + "state.typeId = " + state.typeId + " subId = " + subId);

        if (typeChanged) {
            WifiIconState wifiCopy = mWifiIconState.copy();
            updateShowWifiSignalSpacer(wifiCopy);
            if (!Objects.equals(wifiCopy, mWifiIconState)) {
                updateWifiIconWithState(wifiCopy);
                mWifiIconState = wifiCopy;
            }
        }
    }

    /* UNISOC: add feature statusbar signal cluster view. @{ */
    @Override
    public void setMobileDataConnectedIndicators(boolean show, int subId) {
        MobileIconState state = getState(subId);
        if (state == null) {
            return;
        }
        if (mWifiVisible && !mAlwaysShowRAT) {
            state.dataConnected = false;
        } else {
            state.dataConnected = show;
        }
        mIconController.setMobileIcons(mSlotMobile, MobileIconState.copyStates(mMobileStates));
    }
    /* @} */

    private MobileIconState getState(int subId) {
        for (MobileIconState state : mMobileStates) {
            if (state.subId == subId) {
                return state;
            }
        }
        Log.e(TAG, "Unexpected subscription " + subId);
        return null;
    }

    private MobileIconState getFirstMobileState() {
        if (mMobileStates.size() > 0) {
            return mMobileStates.get(0);
        }

        return null;
    }


    /**
     * It is expected that a call to setSubs will be immediately followed by setMobileDataIndicators
     * so we don't have to update the icon manager at this point, just remove the old ones
     * @param subs list of mobile subscriptions, displayed as mobile data indicators (max 8)
     */
    @Override
    public void setSubs(List<SubscriptionInfo> subs) {
        if (hasCorrectSubs(subs) && !mShowAllSims && !mShowDisabledSims) {
            return;
        }
        mIconController.removeAllIconsForSlot(mSlotMobile);
        mMobileStates.clear();
        mDisabledPhoneMaps.clear();
        /* UNISOC: add for FEATURE bug886899 @{ */
        if (KEEP_AOSP) {
            final int n = subs.size();
            for (int i = 0; i < n; i++) {
                mMobileStates.add(new MobileIconState(subs.get(i).getSubscriptionId()));
            }
        } else {
            final int phoneCount = TelephonyManager.from(mContext).getPhoneCount();
            for (int i = 0; i < phoneCount; i++) {
                SubscriptionInfo subInfo = findRecordByPhoneId(subs, i);
                int subId = subInfo != null ? subInfo.getSubscriptionId()
                        : SubscriptionManager.INVALID_SUBSCRIPTION_ID - i;
                MobileIconState state = new MobileIconState(subId);
                if (SubscriptionManager.isValidSubscriptionId(subId)) {
                    mMobileStates.add(state);
                } else {
                    /* UNISOC: add for FEATURE bug887088 @{ */
                    if (mShowAllSims || mShowDisabledSims) {
                        mMobileStates.add(state);
                        if (mShowDisabledSims && !TelephonyManagerEx.from(mContext).isSimEnabled(i)) {
                            Log.i(TAG, "phoneId:" + i + " is disabled subId:" + subId);
                            mDisabledPhoneMaps.put(subId, i);
                        }
                    }
                /* @} */
                }
                if (mShowSignalTowerIcon) {
                    if (SubscriptionManager.isValidSubscriptionId(subId)) {
                        state.mobileCardId = SIM_CARD_ID[i];
                    } else if (subId == -1) {
                        state.mobileCardId = SIM_CARD_ID[0];
                    } else if (subId == -2) {
                        state.mobileCardId = SIM_CARD_ID[1];
                    }
                }
            }
            // UNISOC: add for bug894727
            Collections.reverse(mMobileStates);
            Log.d(TAG, "Collections.reverse" );
        }
        /* @} */
    }

    /* UNISOC: add for FEATURE bug886899 @{ */
    public final int[] SIM_CARD_ID = {
            R.drawable.stat_sys_card1_cucc_ex,
            R.drawable.stat_sys_card2_cucc_ex,
            R.drawable.stat_sys_card3_cucc_ex
    };

    private SubscriptionInfo findRecordByPhoneId(List<SubscriptionInfo> subs,
                                                 int phoneId) {
        if (subs != null) {
            final int length = subs.size();
            for (int i = 0; i <length ; i++) {
                final SubscriptionInfo sir = subs.get(i);
                if (sir.getSimSlotIndex() == phoneId) {
                    return sir;
                }
            }
        }
        return null;
    }
    /* @} */

    private boolean hasCorrectSubs(List<SubscriptionInfo> subs) {
        final int N = subs.size();
        if (N != mMobileStates.size()) {
            return false;
        }
        for (int i = 0; i < N; i++) {
            if (mMobileStates.get(i).subId != subs.get(i).getSubscriptionId()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setNoSims(boolean show, boolean simDetected) {
        /* UNISOC: add for FEATURE bug886899 & 887088 @{ */
        if (!KEEP_AOSP) {
            if ((mDisabledPhoneMaps.size() == 0) && mShowAllSims) {
                for (int i = 0; i < mMobileStates.size(); i ++) {
                    if (!(SubscriptionManager.isValidSubscriptionId(mMobileStates.get(i).subId))) {
                        Log.d(TAG, "setNoSims subid : " + mMobileStates.get(i).subId);
                        MobileIconState state = mMobileStates.get(i);
                        state.strengthId = R.drawable.stat_sys_no_sims;
                        state.visible = true;
                    }
                }
            }
            if (mShowDisabledSims) {
                for (int i = 0; i < mMobileStates.size(); i ++) {
                    MobileIconState state = mMobileStates.get(i);
                    int subId = state.subId;
                    if (mDisabledPhoneMaps.get(subId) != null
                            && !TelephonyManagerEx.from(mContext).isSimEnabled(mDisabledPhoneMaps.get(subId))) {
                        Log.i(TAG, "phoneId:" + mDisabledPhoneMaps.get(subId) + " is disabled subId:" + subId);
                        state.visible = true;
                        state.strengthId = R.drawable.stat_sys_signal_standby_ex;
                    }
                }
            }
            mIconController.setMobileIcons(mSlotMobile, MobileIconState.copyStates(mMobileStates));
        }
        /* @} */
    }


    @Override
    public void setEthernetIndicators(IconState state) {
        boolean visible = state.visible && !mBlockEthernet;
        int resId = state.icon;
        String description = state.contentDescription;

        if (resId > 0) {
            mIconController.setIcon(mSlotEthernet, resId, description);
            mIconController.setIconVisibility(mSlotEthernet, true);
        } else {
            mIconController.setIconVisibility(mSlotEthernet, false);
        }
    }

    @Override
    public void setIsAirplaneMode(IconState icon) {
        mIsAirplaneMode = icon.visible && !mBlockAirplane;
        int resId = icon.icon;
        String description = icon.contentDescription;

        if (mIsAirplaneMode && resId > 0) {
            mIconController.setIcon(mSlotAirplane, resId, description);
            mIconController.setIconVisibility(mSlotAirplane, true);
        } else {
            mIconController.setIconVisibility(mSlotAirplane, false);
        }
    }

    @Override
    public void setMobileDataEnabled(boolean enabled) {
        // Don't care.
    }

    /* UNISOC: Bug 697836 impl the new interface for volte icon. @{ */
    @Override
    public void setMobileVolteIndicators(boolean show, int subId, int resId) {
        // TODO Auto-generated method stub
        MobileIconState state = getState(subId);
            if (state == null) {
                return;
            } else if (show) {
                state.mMobileVolteId = resId;
            } else {
                state.mMobileVolteId = 0;
            }
            Log.d(TAG, "state.show = " + show + "state.mMobileVolteId = " + state.mMobileVolteId + " subId = " + subId);
            mIconController.setMobileIcons(mSlotMobile, MobileIconState.copyStates(mMobileStates));
    }
    /* @} */

    public void setMobileVoWifiIndicators(IconState icon) {
        mVowifiConnected = icon.visible;
        int resId = icon.icon;
        String description = icon.contentDescription;
        Log.d(TAG, "state.mVowifiConnected = " + mVowifiConnected + "state.mVoWifiId = " + resId );

        if (mVowifiConnected && resId > 0) {
            mIconController.setIcon(mSlotVowifi, resId, description);
            mIconController.setIconVisibility(mSlotVowifi, true);
        } else {
            mIconController.setIconVisibility(mSlotVowifi, false);
        }

    }

    public void setMobileHdVoiceIndicators(IconState icon) {
        mHdVoiceVisible = icon.visible;
        int resId = icon.icon;
        String description = icon.contentDescription;
        Log.d(TAG, "state.mHdVoiceVisible = " + mHdVoiceVisible + "state.mSlotHdVoice = " + resId );

        if (mHdVoiceVisible && resId > 0) {
            mIconController.setIcon(mSlotHdVoice, resId, description);
            mIconController.setIconVisibility(mSlotHdVoice, true);
        } else {
            mIconController.setIconVisibility(mSlotHdVoice, false);
        }

    }

    private static abstract class SignalIconState {
        public boolean visible;
        public boolean activityOut;
        public boolean activityIn;
        public String slot;
        public String contentDescription;

        @Override
        public boolean equals(Object o) {
            // Skipping reference equality bc this should be more of a value type
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SignalIconState that = (SignalIconState) o;
            return visible == that.visible &&
                    activityOut == that.activityOut &&
                    activityIn == that.activityIn &&
                    Objects.equals(contentDescription, that.contentDescription) &&
                    Objects.equals(slot, that.slot);
        }

        @Override
        public int hashCode() {
            return Objects.hash(visible, activityOut, slot);
        }

        protected void copyTo(SignalIconState other) {
            other.visible = visible;
            other.activityIn = activityIn;
            other.activityOut = activityOut;
            other.slot = slot;
            other.contentDescription = contentDescription;
        }
    }

    public static class WifiIconState extends SignalIconState{
        public int resId;
        public boolean airplaneSpacerVisible;
        public boolean signalSpacerVisible;

        @Override
        public boolean equals(Object o) {
            // Skipping reference equality bc this should be more of a value type
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            WifiIconState that = (WifiIconState) o;
            return resId == that.resId &&
                    airplaneSpacerVisible == that.airplaneSpacerVisible &&
                    signalSpacerVisible == that.signalSpacerVisible;
        }

        public void copyTo(WifiIconState other) {
            super.copyTo(other);
            other.resId = resId;
            other.airplaneSpacerVisible = airplaneSpacerVisible;
            other.signalSpacerVisible = signalSpacerVisible;
        }

        public WifiIconState copy() {
            WifiIconState newState = new WifiIconState();
            copyTo(newState);
            return newState;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(),
                    resId, airplaneSpacerVisible, signalSpacerVisible);
        }

        @Override public String toString() {
            return "WifiIconState(resId=" + resId + ", visible=" + visible + ")";
        }
    }

    /**
     * A little different. This one delegates to SignalDrawable instead of a specific resId
     */
    public static class MobileIconState extends SignalIconState {
        public int subId;
        public int strengthId;
        public  int mobileCardId;
        public int typeId;
        public boolean roaming;
        public boolean needsLeadingPadding;
        public String typeContentDescription;
        public int mMobileVolteId;
        public boolean dataConnected;

        private MobileIconState(int subId) {
            super();
            this.subId = subId;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            MobileIconState that = (MobileIconState) o;
            return subId == that.subId &&
                    strengthId == that.strengthId &&
                    typeId == that.typeId &&
                    roaming == that.roaming &&
                    needsLeadingPadding == that.needsLeadingPadding &&
                    mMobileVolteId == that.mMobileVolteId &&
                    dataConnected == that.dataConnected &&
                    mobileCardId == that.mobileCardId &&
                    Objects.equals(typeContentDescription, that.typeContentDescription);
        }

        @Override
        public int hashCode() {

            return Objects
                    .hash(super.hashCode(), subId, strengthId, typeId, roaming, needsLeadingPadding,
                            typeContentDescription);
        }

        public MobileIconState copy() {
            MobileIconState copy = new MobileIconState(this.subId);
            copyTo(copy);
            return copy;
        }

        public void copyTo(MobileIconState other) {
            super.copyTo(other);
            other.subId = subId;
            other.strengthId = strengthId;
            other.typeId = typeId;
            other.roaming = roaming;
            other.needsLeadingPadding = needsLeadingPadding;
            other.dataConnected = dataConnected;
            other.typeContentDescription = typeContentDescription;
            other.mMobileVolteId = mMobileVolteId;
            other.mobileCardId = mobileCardId;
        }

        private static List<MobileIconState> copyStates(List<MobileIconState> inStates) {
            ArrayList<MobileIconState> outStates = new ArrayList<>();
            for (MobileIconState state : inStates) {
                MobileIconState copy = new MobileIconState(state.subId);
                state.copyTo(copy);
                outStates.add(copy);
            }

            return outStates;
        }

        @Override public String toString() {
            return "MobileIconState(subId=" + subId + ", strengthId=" + strengthId + ", roaming="
                    + roaming + ", typeId=" + typeId + ", visible=" + visible + ", dataConnected="
                    + dataConnected + " , mMobileVolteId = " + mMobileVolteId + ", mobileCardId = "
                    + mobileCardId +")";
        }
    }
}
