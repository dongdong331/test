package com.android.internal.telephony;

public class TelephonyIntentsEx {
    /**
     * SPRD: Add for bug693265, AndroidP porting for USIM/SIM phonebook, STK_REFRESH
     */
    public static final String ACTION_STK_REFRESH_SIM_CONTACTS
                               = "android.stkintent.action.ACTION_STK_REFRESH_SIM_CONTACTS";

     /**
      * SPRD: Add for bug693265, AndroidP porting for USIM/SIM phonebook, FDN_STATUS
      */
     public static final String INTENT_KEY_FDN_STATUS = "fdn_status";

    /**
     * UNISOC: add for ACTION_HIGH_DEF_AUDIO_SUPPORT
     */
    public static final String ACTION_HIGH_DEF_AUDIO_SUPPORT
                               = "android.intent.action.HIGH_DEF_AUDIO_SUPPORT";
}

