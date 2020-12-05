package com.android.internal.telephony;

import android.os.Bundle;
import android.net.Uri;

interface ITelephonyEx {
    /**
     * Attention!!! These two interfaces must be put at the top.
     * So please do not add interface in front of these two, otherwise
     * it will cause the failure of PLMN/NetworkList functions.
     */
    /**
     * Update PLMN network name,try to get operator name from SIM if ONS exists or regplmn matched
     * OPL/PNN PLMN showed priorities: OPL/PNN > ONS(CPHS) > NITZ > numeric_operator.xml > mcc+mnc
     * See 3GPP TS 22.101 for details.
     *
     * @param phoneId of whose high priority PLMN network name is returned
     * @param mccmnc mobile country code and mobile network code
     */
    String getHighPriorityPlmn(int phoneId, String mccmnc);
    /**
     * Returns high priority operator name if exists according to the giving operator info.
     *
     * @param phoneId of whose high priority PLMN network name is returned
     * @param operatorInfo operator info from network including MCC/MNC and network operator name.
     */
    String updateNetworkList(int phoneId, in String[] operatorInfo);
    /**
     * Returns the current location of the device according to the giving slotId.
     *<p>
     * If there is only one radio in the device and that radio has an LTE connection,
     * this method will return null. The implementation must not to try add LTE
     * identifiers into the existing cdma/gsm classes.
     *<p>
     * In the future this call will be deprecated.
     *<p>
     * @return Current location of the device or null if not available.
     **/
    Bundle getCellLocationForPhone(int phoneId,String callingPkg);
    /**
     * UNISOC: Add for bug693265, AndroidP porting for USIM/SIM phonebook
     * @return true if a IccFdn enabled
     */
    boolean getIccFdnEnabledForSubscriber(int subId);
    /**
     * UNISOC:Add for bug602746, get PNN name with subId
     * return PNN name
     */
    String getPnnHomeName(int subId);
    // UNISOC: add for bug 849436, returns the network type for a phone
    int getNetworkTypeForPhone(int phoneId, String callingPackage);
    /**
     * Set the line 1 phone number string and its alphatag for the current ICCID
     * for display purpose only, for example, displayed in Phone Status. It won't
     * change the actual MSISDN/MDN. To unset alphatag or number, pass in a null
     * value.
     *
     * <p>Requires that the calling app has carrier privileges.
     * @see #hasCarrierPrivileges
     *
     * @param subId the subscriber that the alphatag and dialing number belongs to.
     * @param alphaTag alpha-tagging of the dailing nubmer
     * @param number The dialing number
     * @return true if the operation was executed correctly.
     */
    boolean setLine1NumberForDisplayForSubscriberEx(int subId, String alphaTag, String number);

    /**
     * UNISOC: Support SimLock
     */
    int[] supplySimlockReportResultForPhone(int subId, boolean isLock, String password, int type);
    /**
     * Enable or disable SIM card just like hot-plugging. If SIM is disabled, all informations
     * related to this SIM card will be removed. SIM card will be reloaded if enable it again.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#MODIFY_PHONE_STATE MODIFY_PHONE_STATE}
     *
     * @param phoneId
     * @param turnOn whether disable or enable SIM
     */
     void setSimEnabled(int phoneId, boolean turnOn);
     //UNISOC:Add cache of original SPN value.
     String getOriginalServiceProviderName(int phoneId);

    /** DM Telephony interface support @{ */
    /**
     * Returns a constant indicating the current data connection state
     * (cellular).
     *
     * @param phoneId of which data state is returned
     */
    int getDataState(int phoneId);
    /**
     * Returns a platform configuration for VoLTE which may override the user setting.
     */
    boolean isVolteEnabledByPlatform();
    /**
     * Returns the user configuration of Enhanced 4G LTE Mode setting.
     */
    boolean isEnhanced4gLteModeSettingEnabledByUser(int phoneId);
    /**
     * Change persistent Enhanced 4G LTE Mode setting.
     */
    void setEnhanced4gLteModeSetting(boolean enabled, int phoneId);
    /**
     * Returns true if the device is considered international roaming on the current network for a
     * subscription.
     * <p>
     * Availability: Only when user registered to a network.
     *
     * @param subId
     */
    boolean isInternationalNetworkRoaming(int phoneId);
    int queryLteCtccSimType (int phoneId);
    /** DM Telephony interface support @} */
    boolean isImsRegistered();

    boolean isPrimaryCardSwitchAllowed();
    boolean isDisableSimAllowed(int phoneId);
}

