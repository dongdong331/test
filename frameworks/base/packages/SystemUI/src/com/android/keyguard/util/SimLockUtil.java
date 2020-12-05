package com.android.keyguard.util;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.keyguard.R;
import com.android.sprd.telephony.uicc.IccCardStatusEx;
import com.android.sprd.telephony.RadioInteractor;
import android.content.res.Resources;
import android.content.Context;
import android.content.Intent;
import android.telephony.SubscriptionManager;
import android.os.SystemProperties;

import java.util.HashMap;

public class SimLockUtil {

    public static SimLockUtil mInstance;
    public static String[] mSimlockReason = {IccCardConstants.INTENT_VALUE_LOCKED_NETWORK,
            IccCardConstants.INTENT_VALUE_LOCKED_NS,
            IccCardConstants.INTENT_VALUE_LOCKED_SP,
            IccCardConstants.INTENT_VALUE_LOCKED_CP,
            IccCardConstants.INTENT_VALUE_LOCKED_SIM,
            IccCardConstants.INTENT_VALUE_LOCKED_NETWORK_PUK,
            IccCardConstants.INTENT_VALUE_LOCKED_NS_PUK,
            IccCardConstants.INTENT_VALUE_LOCKED_SP_PUK,
            IccCardConstants.INTENT_VALUE_LOCKED_CP_PUK,
            IccCardConstants.INTENT_VALUE_LOCKED_SIM_PUK,
            IccCardConstants.INTENT_VALUE_LOCKED_PERMANENTLY};

    public static SimLockUtil getInstance(Context context){
        if(mInstance == null){
            return new SimLockUtil(context);
        }
        return mInstance;
    }

    public SimLockUtil(){
    }

    public SimLockUtil(Context context){
    }

    public static State getStateByReason(String lockedReason){
        State state = IccCardConstants.State.UNKNOWN;

        //Network Lock
        if(IccCardConstants.INTENT_VALUE_LOCKED_NETWORK.equals(lockedReason)){
            state = IccCardConstants.State.NETWORK_LOCKED;
        }
        //Network Subset Lock
        else if (IccCardConstants.INTENT_VALUE_LOCKED_NS.equals(lockedReason)){
            state = IccCardConstants.State.NETWORK_SUBSET_LOCKED;
        }
        //Service Provider Lock
        else if (IccCardConstants.INTENT_VALUE_LOCKED_SP.equals(lockedReason)){
            state = IccCardConstants.State.SERVICE_PROVIDER_LOCKED;
        }
        //Corporate Lock
        else if (IccCardConstants.INTENT_VALUE_LOCKED_CP.equals(lockedReason)){
            state = IccCardConstants.State.CORPORATE_LOCKED;
        }
        //Sim Lock
        else if (IccCardConstants.INTENT_VALUE_LOCKED_SIM.equals(lockedReason)){
            state = IccCardConstants.State.SIM_LOCKED;
        }
        //Network Lock PUK
        else if (IccCardConstants.INTENT_VALUE_LOCKED_NETWORK_PUK.equals(lockedReason)){
            state = IccCardConstants.State.NETWORK_LOCKED_PUK;
        }
        //Network Subset Lock PUK
        else if (IccCardConstants.INTENT_VALUE_LOCKED_NS_PUK.equals(lockedReason)){
            state = IccCardConstants.State.NETWORK_SUBSET_LOCKED_PUK;
        }
        //Service Provider Lock PUK
        else if (IccCardConstants.INTENT_VALUE_LOCKED_SP_PUK.equals(lockedReason)){
            state = IccCardConstants.State.SERVICE_PROVIDER_LOCKED_PUK;
        }
        //Corporate Lock PUK
        else if (IccCardConstants.INTENT_VALUE_LOCKED_CP_PUK.equals(lockedReason)){
            state = IccCardConstants.State.CORPORATE_LOCKED_PUK;
        }
        //Sim Lock PUK
        else if (IccCardConstants.INTENT_VALUE_LOCKED_SIM_PUK.equals(lockedReason)){
            state = IccCardConstants.State.SIM_LOCKED_PUK;
        }
        //Lock permanently
        else if (IccCardConstants.INTENT_VALUE_LOCKED_PERMANENTLY.equals(lockedReason)){
            state = IccCardConstants.State.SIM_LOCKED_PERMANENTLY;
        }
        else {
            state = IccCardConstants.State.UNKNOWN;
        }
        return state;
    }

    public State getStateByLockStatus(int isNetworkLock, int isNetworkSubsetLock, int isServiceProviderLock, int isCorporateLock, int isSimLock){
        State state = IccCardConstants.State.UNKNOWN;
        if(isNetworkLock > 0){
            state = IccCardConstants.State.NETWORK_LOCKED;;
        }
        if(isNetworkSubsetLock > 0){
            state = IccCardConstants.State.NETWORK_SUBSET_LOCKED;;
        }
        if(isServiceProviderLock > 0){
            state = IccCardConstants.State.SERVICE_PROVIDER_LOCKED;;
        }
        if(isCorporateLock > 0){
            state = IccCardConstants.State.CORPORATE_LOCKED;;
        }
        if(isSimLock > 0){
            state = IccCardConstants.State.SIM_LOCKED;;
        }
        return state;
    }

    public static int getUnlockTypeByState(State state){
        switch(state){
        case NETWORK_LOCKED:
            return IccCardStatusEx.UNLOCK_NETWORK;
        case NETWORK_SUBSET_LOCKED:
            return IccCardStatusEx.UNLOCK_NETWORK_SUBSET;
        case SERVICE_PROVIDER_LOCKED:
            return IccCardStatusEx.UNLOCK_SERVICE_PORIVDER;
        case CORPORATE_LOCKED:
            return IccCardStatusEx.UNLOCK_CORPORATE;
        case SIM_LOCKED:
            return IccCardStatusEx.UNLOCK_SIM;
        case NETWORK_LOCKED_PUK:
            return IccCardStatusEx.UNLOCK_NETWORK_PUK;
        case NETWORK_SUBSET_LOCKED_PUK:
            return IccCardStatusEx.UNLOCK_NETWORK_SUBSET_PUK;
        case SERVICE_PROVIDER_LOCKED_PUK:
            return IccCardStatusEx.UNLOCK_SERVICE_PORIVDER_PUK ;
        case CORPORATE_LOCKED_PUK:
            return IccCardStatusEx.UNLOCK_CORPORATE_PUK;
        case SIM_LOCKED_PUK:
            return IccCardStatusEx.UNLOCK_SIM_PUK;
        default:
            return -1;
        }
    }

    public static int getTitleByState(State state){
        switch(state){
        case NETWORK_LOCKED:
            return R.string.keyguard_simlock_network_locked_message;
        case NETWORK_SUBSET_LOCKED:
            return R.string.keyguard_simlock_networksubset_locked_message;
        case SERVICE_PROVIDER_LOCKED:
            return R.string.keyguard_simlock_serviceprovider_locked_message;
        case CORPORATE_LOCKED:
            return R.string.keyguard_simlock_corprorate_locked_message;
        case SIM_LOCKED:
            return R.string.keyguard_simlock_sim_locked_message;
        case NETWORK_LOCKED_PUK:
            return R.string.keyguard_simlock_network_puk_locked__message;
        case NETWORK_SUBSET_LOCKED_PUK:
            return R.string.keyguard_simlock_networksubset_puk_locked_message;
        case SERVICE_PROVIDER_LOCKED_PUK:
            return R.string.keyguard_simlock_serviceprovider_puk_locked_message;
        case CORPORATE_LOCKED_PUK:
            return R.string.keyguard_simlock_corprorate_puk_locked_message;
        case SIM_LOCKED_PUK:
            return R.string.keyguard_simlock_sim_puk_locked_message;
        default:
            return R.string.keyguard_simlock_default_message;
        }
    }

    public static boolean isSimlockLocked(String lockedReason){
        for (String reason : mSimlockReason){
            if (reason.equals(lockedReason)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSimlockState(State state){
        return state.isSimlockLocked();
    }

    public static boolean isAutoShow(){
        return  !Resources.getSystem().getBoolean(com.android.internal.R.bool.config_subsidyLock) &&
                SystemProperties.getBoolean("ro.simlock.unlock.autoshow", true)
                && !SystemProperties.getBoolean("ro.simlock.onekey.lock", false)
                && !SystemProperties.getBoolean("ro.simlock.unlock.bynv", false);
    }

    public static boolean isByNv(){
        return SystemProperties.getBoolean("ro.simlock.unlock.bynv", false);
    }

    public static boolean isOnekeyLock(){
        return SystemProperties.getBoolean("ro.simlock.onekey.lock", false);
    }

    public boolean isSimlockStatusChange (Intent intent) {
        HashMap<Integer, SimDataEx> mSimDatasEx = new HashMap<Integer, SimDataEx>();
        State state;
        boolean ret = false;
        if (!TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
            throw new IllegalArgumentException("only handles intent ACTION_SIM_STATE_CHANGED");
        }
        String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        int slotId = intent.getIntExtra(PhoneConstants.SLOT_KEY, 0);
        int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
            final String lockedReason = intent
                    .getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
            state = getStateByReason(lockedReason);
        } else {
            state = State.UNKNOWN;
        }
        SimDataEx data = mSimDatasEx.get(subId);
        if (data == null) {
            data = new SimDataEx(state, slotId, subId);
            mSimDatasEx.put(subId, data);
            if (state != State.UNKNOWN) {
                ret = true;
            }
        } else {
            if (data.simState != state) {
                data.simState = state;
                ret = true;
            }
            data.subId = subId;
            data.slotId = slotId;
        }
        return ret;
    }

    private static class SimDataEx {
        public State simState;
        public int slotId;
        public int subId;

        SimDataEx(State state, int slot, int id) {
            simState = state;
            slotId = slot;
            subId = id;
        }

        public String toString() {
            return "SimDataEx{state=" + simState + ",slotId=" + slotId + ",subId=" + subId + "}";
        }
    }
}
