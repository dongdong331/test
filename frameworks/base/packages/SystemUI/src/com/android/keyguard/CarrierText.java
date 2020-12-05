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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.telephony.gsm.GsmCellLocation;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManagerEx;
import android.text.TextUtils;
import android.text.method.SingleLineTransformationMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TeleUtils;
import com.android.keyguard.util.SimLockUtil;
import com.android.settingslib.WirelessUtils;

public class CarrierText extends TextView {
    /** Do not show missing sim message. */
    public static final int FLAG_HIDE_MISSING_SIM = 1 << 0;
    /** Do not show airplane mode message. */
    public static final int FLAG_HIDE_AIRPLANE_MODE = 1 << 1;

    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "CarrierText";

    private static CharSequence mSeparator;

    private final boolean mIsEmergencyCallCapable;

    private KeyguardUpdateMonitor mKeyguardUpdateMonitor;

    private WifiManager mWifiManager;

    private boolean[] mSimErrorState = new boolean[TelephonyManager.getDefault().getPhoneCount()];

    private int mFlags;

    //UNISOC: Modify by BUG 693456
    private boolean mIsModemResetActive = false;

    /* UNISOC: add for FEATURE bug698868 @{ */
    private static final String RAT_4G = "4G";
    private static final String RAT_3G = "3G";
    private static final String RAT_2G = "2G";
    private static CharSequence mSeparatorForSims;
    private ServiceState mServiceState;
    private String mRats;
    private int mSubId;
    /* @} */
    private static HashMap<String, String> mVivoMap = new HashMap<String, String>();

    private KeyguardUpdateMonitorCallback mCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onRefreshCarrierInfo() {
            updateCarrierText();
        }

        public void onFinishedGoingToSleep(int why) {
            setSelected(false);
        };

        public void onStartedWakingUp() {
            setSelected(true);
        };

        public void onSimStateChanged(int subId, int slotId, IccCardConstants.State simState) {
            if (slotId < 0) {
                Log.d(TAG, "onSimStateChanged() - slotId invalid: " + slotId);
                return;
            }

            if (DEBUG) Log.d(TAG,"onSimStateChanged: " + getStatusForIccState(simState));
            if (getStatusForIccState(simState) == StatusMode.SimIoError) {
                mSimErrorState[slotId] = true;
                updateCarrierText();
            } else if (mSimErrorState[slotId]) {
                mSimErrorState[slotId] = false;
                updateCarrierText();
            }
        };

        /* UNISOC: modify for feature bug693456 @{ */
        @Override
        public void onModemAssert(boolean isModemResetActive) {
            Log.d(TAG, "onModemAssert() - isModemResetActive: " + isModemResetActive);
            mIsModemResetActive = isModemResetActive;
            if (!mIsModemResetActive) {
                updateCarrierText();
            }
        }
        /* @} */
    };

    public void setDisplayFlags(int flags) {
        mFlags = flags;
    }

    /**
     * The status of this lock screen. Primarily used for widgets on LockScreen.
     */
    private static enum StatusMode {
        Normal, // Normal case (sim card present, it's not locked)
        NetworkLocked, // SIM card is 'network locked'.
        SimMissing, // SIM card is missing.
        SimMissingLocked, // SIM card is missing, and device isn't provisioned; don't allow access
        SimPukLocked, // SIM card is PUK locked because SIM entered wrong too many times
        SimLocked, // SIM card is currently locked
        SimPermDisabled, // SIM card is permanently disabled due to PUK unlock failure
        SimNotReady, // SIM is not ready yet. May never be on devices w/o a SIM.
        SimIoError, // SIM card is faulty
        /* Unisoc: Support SimLock @{ */
        NetworkSubsetLocked,
        ServiceProviderLocked,
        CorporateLocked,
        SIMLocked,//SIMLocked is one type of SIMLOCK, different with 'SimLocked'
        NetworkLockedPuk,
        NetworkSubsetLockedPuk,
        ServiceProviderLockedPuk,
        CorporateLockedPuk,
        SIMLockedPuk,
        SIMLockedPermanently;
        /* @} */
    }

    public CarrierText(Context context) {
        this(context, null);
    }

    public CarrierText(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIsEmergencyCallCapable = context.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);
        boolean useAllCaps;
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.CarrierText, 0, 0);
        try {
            useAllCaps = a.getBoolean(R.styleable.CarrierText_allCaps, false);
        } finally {
            a.recycle();
        }
        setTransformationMethod(new CarrierTextTransformationMethod(mContext, useAllCaps));

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        String itemList[] = context.getResources().getStringArray(R.array.vivo_area_code_and_region_name);
        for (String item : itemList) {
            String parts[] = item.split(",");
            mVivoMap.put(parts[0], parts[1]);
        }
    }

    /**
     * Checks if there are faulty cards. Adds the text depending on the slot of the card
     * @param text: current carrier text based on the sim state
     * @param noSims: whether a valid sim card is inserted
     * @return text
    */
    private CharSequence updateCarrierTextWithSimIoError(CharSequence text, boolean noSims) {
        final CharSequence carrier = "";
        CharSequence carrierTextForSimIOError = getCarrierTextForSimState(
            IccCardConstants.State.CARD_IO_ERROR, carrier);
        for (int index = 0; index < mSimErrorState.length; index++) {
            if (mSimErrorState[index]) {
                // In the case when no sim cards are detected but a faulty card is inserted
                // overwrite the text and only show "Invalid card"
                if (noSims) {
                    return concatenate(carrierTextForSimIOError,
                        getContext().getText(com.android.internal.R.string.emergency_calls_only));
                } else if (index == 0) {
                    // prepend "Invalid card" when faulty card is inserted in slot 0
                    text = concatenate(carrierTextForSimIOError, text);
                } else {
                    // concatenate "Invalid card" when faulty card is inserted in slot 1
                    text = concatenate(text, carrierTextForSimIOError);
                }
            }
        }
        return text;
    }

    protected void updateCarrierText() {
        /* UNISOC: Modify by BUG 693456. @{ */
        if (mIsModemResetActive) {
            Log.i(TAG, "modem reset don't refresh carrier text");
            return;
        }
        /* @} */
        boolean allSimsMissing = true;
        boolean anySimReadyAndInService = false;
        CharSequence displayText = null;

        List<SubscriptionInfo> subs = mKeyguardUpdateMonitor.getSubscriptionInfo(false);
        /* UNISOC: Modify by BUG 900208. @{ */
        Collections.sort(subs, new Comparator<SubscriptionInfo>() {
            @Override
            public int compare(SubscriptionInfo lhs, SubscriptionInfo rhs) {
                return lhs.getSimSlotIndex() == rhs.getSimSlotIndex()
                        ? lhs.getSubscriptionId() - rhs.getSubscriptionId()
                        : lhs.getSimSlotIndex() - rhs.getSimSlotIndex();
            }
        });
        /* @} */
        final int N = subs.size();
        if (DEBUG) Log.d(TAG, "updateCarrierText(): " + N);
        for (int i = 0; i < N; i++) {
            int subId = subs.get(i).getSubscriptionId();
            mSubId = subId;
            State simState = mKeyguardUpdateMonitor.getSimState(subId);
            CharSequence carrierName = subs.get(i).getCarrierName();
            // UNISOC: add for FEATURE bug698868
            mServiceState = mKeyguardUpdateMonitor.mServiceStates.get(subId);
            CharSequence carrierTextForSimState = getCarrierTextForSimState(simState, carrierName);
            if (DEBUG) {
                Log.d(TAG, "Handling (subId=" + subId + "): " + simState + " " + carrierName
                        + "; carrierTextForSimState:" + carrierTextForSimState);
            }
            /* UNISOC: Bug 915154 Update carrier text when simlock bynv configured.@{*/
            if (SimLockUtil.isSimlockState(simState) && SimLockUtil.isByNv()){
                carrierTextForSimState = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_simlock_default_message), carrierName);
                Log.d(TAG, "For simlock state, update carrier text:" + carrierTextForSimState);
            }
            /* @} */
            if (carrierTextForSimState != null) {
                allSimsMissing = false;
                displayText = concatenate(displayText, carrierTextForSimState);
            }
            if (simState == IccCardConstants.State.READY) {
                ServiceState ss = mKeyguardUpdateMonitor.mServiceStates.get(subId);
                if (ss != null && ss.getDataRegState() == ServiceState.STATE_IN_SERVICE) {
                    // hack for WFC (IWLAN) not turning off immediately once
                    // Wi-Fi is disassociated or disabled
                    if (ss.getRilDataRadioTechnology() != ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN
                            || (mWifiManager != null && mWifiManager.isWifiEnabled()
                                    && mWifiManager.getConnectionInfo() != null
                                    && mWifiManager.getConnectionInfo().getBSSID() != null)) {
                        if (DEBUG) {
                            Log.d(TAG, "SIM ready and in service: subId=" + subId + ", ss=" + ss);
                        }
                        anySimReadyAndInService = true;
                    }
                }
            }
        }
        if (allSimsMissing) {
            if (N != 0) {
                // Shows "No SIM card | Emergency calls only" on devices that are voice-capable.
                // This depends on mPlmn containing the text "Emergency calls only" when the radio
                // has some connectivity. Otherwise, it should be null or empty and just show
                // "No SIM card"
                // Grab the first subscripton, because they all should contain the emergency text,
                // described above.
                displayText =  makeCarrierStringOnEmergencyCapable(
                        getMissingSimMessage(), subs.get(0).getCarrierName());
            } else {
                // We don't have a SubscriptionInfo to get the emergency calls only from.
                // Grab it from the old sticky broadcast if possible instead. We can use it
                // here because no subscriptions are active, so we don't have
                // to worry about MSIM clashing.
                CharSequence text =
                        getContext().getText(com.android.internal.R.string.emergency_calls_only);
                Intent i = getContext().registerReceiver(null,
                        new IntentFilter(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION));
                if (i != null) {
                    String spn = "";
                    String plmn = "";
                    if (i.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false)) {
                        spn = i.getStringExtra(TelephonyIntents.EXTRA_SPN);
                    }
                    if (i.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false)) {
                        plmn = i.getStringExtra(TelephonyIntents.EXTRA_PLMN);
                    }
                    if (DEBUG) Log.d(TAG, "Getting plmn/spn sticky brdcst " + plmn + "/" + spn);
                    if (Objects.equals(plmn, spn)) {
                        text = plmn;
                    } else {
                        text = concatenate(plmn, spn);
                    }
                }
                displayText =  makeCarrierStringOnEmergencyCapable(getMissingSimMessage(), text);
            }
        }

        displayText = updateCarrierTextWithSimIoError(displayText, allSimsMissing);
        // APM (airplane mode) != no carrier state. There are carrier services
        // (e.g. WFC = Wi-Fi calling) which may operate in APM.
        if (!anySimReadyAndInService && WirelessUtils.isAirplaneModeOn(mContext)) {
            displayText = getAirplaneModeMessage();
        }
        setText(displayText);
    }

    private String getMissingSimMessage() {
        return (mFlags & FLAG_HIDE_MISSING_SIM) == 0
                ? getContext().getString(R.string.keyguard_missing_sim_message_short) : "";
    }

    private String getAirplaneModeMessage() {
        return (mFlags & FLAG_HIDE_AIRPLANE_MODE) == 0
                ? getContext().getString(R.string.airplane_mode) : "";
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSeparator = getResources().getString(
                com.android.internal.R.string.kg_text_message_separator);
        boolean shouldMarquee = KeyguardUpdateMonitor.getInstance(mContext).isDeviceInteractive();
        mRats = getResources().getString(R.string.network_types_default);
        mSeparatorForSims = getContext().getString(R.string.kg_carrier_text_separator);
        setSelected(shouldMarquee); // Allow marquee to work.
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (ConnectivityManager.from(mContext).isNetworkSupported(
                ConnectivityManager.TYPE_MOBILE)) {
            mKeyguardUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
            mKeyguardUpdateMonitor.registerCallback(mCallback);
        } else {
            // Don't listen and clear out the text when the device isn't a phone.
            mKeyguardUpdateMonitor = null;
            setText("");
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mKeyguardUpdateMonitor != null) {
            mKeyguardUpdateMonitor.removeCallback(mCallback);
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);

        // Only show marquee when visible
        if (visibility == VISIBLE) {
            setEllipsize(TextUtils.TruncateAt.MARQUEE);
        } else {
            setEllipsize(TextUtils.TruncateAt.END);
        }
    }

    /**
     * Top-level function for creating carrier text. Makes text based on simState, PLMN
     * and SPN as well as device capabilities, such as being emergency call capable.
     *
     * @param simState
     * @param text
     * @param spn
     * @return Carrier text if not in missing state, null otherwise.
     */
    private CharSequence getCarrierTextForSimState(IccCardConstants.State simState,
            CharSequence text) {
        CharSequence carrierText = null;
        StatusMode status = getStatusForIccState(simState);
        switch (status) {
            case Normal:
                // UNISOC: Modify for FEATURE bug698868
                carrierText = appendRatToNetworkName(text);
                break;

            case SimNotReady:
                // Null is reserved for denoting missing, in this case we have nothing to display.
                carrierText = ""; // nothing to display yet.
                break;

            case NetworkLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_simlock_network_locked_message), text);
                break;
                /* Unisoc: Support SimLock @{ */
            case NetworkSubsetLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_simlock_networksubset_locked_message), text);
                break;
            case ServiceProviderLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_simlock_serviceprovider_locked_message), text);
                break;
            case CorporateLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_simlock_corprorate_locked_message), text);
                break;
            case SIMLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_simlock_sim_locked_message), text);
                break;
            case NetworkLockedPuk:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_simlock_network_puk_locked__message), text);
                break;
            case NetworkSubsetLockedPuk:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_simlock_networksubset_puk_locked_message), text);
                break;
            case ServiceProviderLockedPuk:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_simlock_serviceprovider_puk_locked_message), text);
                break;
            case CorporateLockedPuk:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_simlock_corprorate_puk_locked_message), text);
                break;
            case SIMLockedPuk:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_simlock_sim_puk_locked_message), text);
                break;
            case SIMLockedPermanently:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_simlock_sim_locked_permanently_message), text);
                break;
                /* @} */
            case SimMissing:
                carrierText = null;
                break;

            case SimPermDisabled:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(
                                R.string.keyguard_permanent_disabled_sim_message_short),
                        text);
                break;

            case SimMissingLocked:
                carrierText = null;
                break;

            case SimLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_locked_message),
                        text);
                break;

            case SimPukLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_puk_locked_message),
                        text);
                break;
            case SimIoError:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_error_message_short),
                        text);
                break;
        }

        return carrierText;
    }

    /*
     * Add emergencyCallMessage to carrier string only if phone supports emergency calls.
     */
    private CharSequence makeCarrierStringOnEmergencyCapable(
            CharSequence simMessage, CharSequence emergencyCallMessage) {
        if (mIsEmergencyCallCapable) {
            return concatenate(simMessage, emergencyCallMessage, mSeparator);
        }
        return simMessage;
    }

    /**
     * Determine the current status of the lock screen given the SIM state and other stuff.
     */
    private StatusMode getStatusForIccState(IccCardConstants.State simState) {
        // Since reading the SIM may take a while, we assume it is present until told otherwise.
        if (simState == null) {
            return StatusMode.Normal;
        }

        final boolean missingAndNotProvisioned =
                !KeyguardUpdateMonitor.getInstance(mContext).isDeviceProvisioned()
                && (simState == IccCardConstants.State.ABSENT ||
                        simState == IccCardConstants.State.PERM_DISABLED);

        // Assume we're NETWORK_LOCKED if not provisioned
        simState = missingAndNotProvisioned ? IccCardConstants.State.NETWORK_LOCKED : simState;
        switch (simState) {
            case ABSENT:
                return StatusMode.SimMissing;
                /* Unisoc: Support SimLock @{ */
            case NETWORK_LOCKED:
                return StatusMode.NetworkLocked;
            case NETWORK_SUBSET_LOCKED:
                return StatusMode.NetworkSubsetLocked;
            case SERVICE_PROVIDER_LOCKED:
                return StatusMode.ServiceProviderLocked;
            case CORPORATE_LOCKED:
                return StatusMode.CorporateLocked;
            case SIM_LOCKED:
                return StatusMode.SIMLocked;
            case NETWORK_LOCKED_PUK:
                return StatusMode.NetworkLockedPuk;
            case NETWORK_SUBSET_LOCKED_PUK:
                return StatusMode.NetworkSubsetLockedPuk;
            case SERVICE_PROVIDER_LOCKED_PUK:
                return StatusMode.ServiceProviderLockedPuk;
            case CORPORATE_LOCKED_PUK:
                return StatusMode.CorporateLockedPuk;
            case SIM_LOCKED_PUK:
                return StatusMode.SIMLockedPuk;
            case SIM_LOCKED_PERMANENTLY:
                return StatusMode.SIMLockedPermanently;
                /*@}*/
            case NOT_READY:
                return StatusMode.SimNotReady;
            case PIN_REQUIRED:
                return StatusMode.SimLocked;
            case PUK_REQUIRED:
                return StatusMode.SimPukLocked;
            case READY:
                return StatusMode.Normal;
            case PERM_DISABLED:
                return StatusMode.SimPermDisabled;
            case UNKNOWN:
                return StatusMode.SimMissing;
            case CARD_IO_ERROR:
                return StatusMode.SimIoError;
        }
        return StatusMode.SimMissing;
    }

    private static CharSequence concatenate(CharSequence plmn, CharSequence spn) {
        final boolean plmnValid = !TextUtils.isEmpty(plmn);
        final boolean spnValid = !TextUtils.isEmpty(spn);
        if (plmnValid && spnValid) {
            // UNISOC: Modify for FEATURE bug698868
            return new StringBuilder().append(plmn).append(mSeparatorForSims).append(spn).toString();
        } else if (plmnValid) {
            return plmn;
        } else if (spnValid) {
            return spn;
        } else {
            return "";
        }
    }

    /* Unisoc: Modify for bug894664*/
    private static CharSequence concatenate(CharSequence plmn, CharSequence spn, CharSequence separator) {
        final boolean plmnValid = !TextUtils.isEmpty(plmn);
        final boolean spnValid = !TextUtils.isEmpty(spn);
        if (plmnValid && spnValid) {
            return new StringBuilder().append(plmn).append(separator).append(spn).toString();
        } else if (plmnValid) {
            return plmn;
        } else if (spnValid) {
            return spn;
        } else {
            return "";
        }
    }
    /*@}*/

    /* UNISOC: add for FEATURE bug698868 @{ */
    public CharSequence appendRatToNetworkName(CharSequence operator) {
        CharSequence operatorName = operator;
        ServiceState state = mServiceState;
        /* UNISOC: modify by BUG 601753 @{ */
        String emergencyCall = mContext.getText(com.android.internal.R.string.emergency_calls_only).toString();

        String noService = mContext
                .getText(com.android.internal.R.string.lockscreen_carrier_default).toString();

        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        TelephonyManagerEx tmEx = TelephonyManagerEx.from(mContext);

        if (mContext == null || state == null
                || operatorName.equals(emergencyCall)
                || operatorName.equals(noService) ) return operatorName;
        /* @} */

        String plmn = tm.getNetworkOperator(mSubId);
        if (plmn != null && isVivoNetwork(plmn)) {
            int phoneId = SubscriptionManager.getSlotIndex(mSubId);
            if (SubscriptionManager.isValidPhoneId(phoneId)) {
                Log.d(TAG, "isValid phoneId " + phoneId);
                try {
                    GsmCellLocation gsmCellLocation = (GsmCellLocation)tmEx.getCellLocationForPhone(phoneId);
                    int lac = gsmCellLocation.getLac();
                    int lastTwoDigitsLac;
                    if (lac >= 0) {
                        lastTwoDigitsLac = lac%100;
                        String vivoLastLac = Integer.toString(lastTwoDigitsLac);
                        String vivoLocalNetwork = mVivoMap.get(vivoLastLac);
                         Log.d(TAG, "vivoLastLac " + vivoLastLac + " vivoLocalNetwork " + vivoLocalNetwork);
                        if (vivoLocalNetwork != null) {
                            operatorName = new StringBuilder().append(operatorName).append(" ")
                                .append(vivoLocalNetwork).append(" ").append(vivoLastLac);
                        }
                    }
                } catch (NullPointerException e) {
                } catch (ClassCastException e) {
                }
            }
        }

        boolean boolAppendRat = mContext.getResources().getBoolean(
                R.bool.config_show_rat_append_operator);

        if (!boolAppendRat) {
            return operatorName;
        }

        /* UNISOC: add for BUG 536878 @{ */
        if (operatorName != null && mRats.contains(operatorName.toString())) {
            return operatorName;
        }
        /* @} */

        // UNISOC: VoWifi feature
        if (operatorName != null && operatorName.toString().endsWith("WiFiCall")) {
            return operatorName;
        }

        if (state.getDataRegState() == ServiceState.STATE_IN_SERVICE
                || state.getVoiceRegState() == ServiceState.STATE_IN_SERVICE) {
            int voiceNetType = state.getVoiceNetworkType();
            int dataNetType = state.getDataNetworkType();
            int chosenNetType = ((dataNetType == TelephonyManager.NETWORK_TYPE_UNKNOWN)
                    ? voiceNetType : dataNetType);
            int ratInt = tm.getNetworkClass(chosenNetType);
            String networktypeString = getNetworkTypeToString(ratInt);
            operatorName = new StringBuilder().append(operatorName).append(" ")
                    .append(networktypeString);
            return operatorName;
        }
        return operatorName;
    }

    private boolean isVivoNetwork(String plmn) {
        if (plmn.equals("72406") || plmn.equals("72410")
                || plmn.equals("72411") || plmn.equals("72423")) {
            return true;
        } else {
            return false;
        }
    }

    protected String getNetworkTypeToString(int ratInt) {
        String ratClassName = "";
        switch (ratInt) {
            case TelephonyManager.NETWORK_CLASS_2_G:
                boolean showRat2G = getContext().getResources().getBoolean(
                        R.bool.config_show_2g);
                Log.d(TAG, "showRat2G : " + showRat2G);
                ratClassName = showRat2G ? RAT_2G : "";
                break;
            case TelephonyManager.NETWORK_CLASS_3_G:
                boolean showRat3g = getContext().getResources().getBoolean(
                        R.bool.config_show_3g);
                Log.d(TAG, "showRat3G : " + showRat3g);
                ratClassName = showRat3g ? RAT_3G : "";
                break;
            case TelephonyManager.NETWORK_CLASS_4_G:
                boolean showRat4g = getContext().getResources().getBoolean(
                        R.bool.config_show_4g);
                Log.d(TAG, "showRat4g : " + showRat4g);
                ratClassName = showRat4g ? RAT_4G : "";
                break;
        }
        return ratClassName;
    }
    /* @} */
    private CharSequence getCarrierHelpTextForSimState(IccCardConstants.State simState,
            String plmn, String spn) {
        int carrierHelpTextId = 0;
        StatusMode status = getStatusForIccState(simState);
        switch (status) {
            case NetworkLocked:
                carrierHelpTextId = R.string.keyguard_instructions_when_pattern_disabled;
                break;

            case SimMissing:
                carrierHelpTextId = R.string.keyguard_missing_sim_instructions_long;
                break;

            case SimPermDisabled:
                carrierHelpTextId = R.string.keyguard_permanent_disabled_sim_instructions;
                break;

            case SimMissingLocked:
                carrierHelpTextId = R.string.keyguard_missing_sim_instructions;
                break;

            case Normal:
            case SimLocked:
            case SimPukLocked:
                break;
        }

        return mContext.getText(carrierHelpTextId);
    }

    private class CarrierTextTransformationMethod extends SingleLineTransformationMethod {
        private final Locale mLocale;
        private final boolean mAllCaps;

        public CarrierTextTransformationMethod(Context context, boolean allCaps) {
            mLocale = context.getResources().getConfiguration().locale;
            mAllCaps = allCaps;
        }

        @Override
        public CharSequence getTransformation(CharSequence source, View view) {
            source = super.getTransformation(source, view);

            if (mAllCaps && source != null) {
                source = source.toString().toUpperCase(mLocale);
            }

            return source;
        }
    }
}
