package android.telephony;

import android.os.PersistableBundle;
import android.annotation.NonNull;
import android.annotation.SystemApi;

public class CarrierConfigManagerEx {
    private final static String TAG = "CarrierConfigManagerEx";

    /**
     * @hide
     */
    public CarrierConfigManagerEx() {
    }

    /**
     * Extra value with broadcast "android.telephony.action.CARRIER_CONFIG_CHANGED"
     * It's means that config changed from feature , network or subinfo.
     *  By filtering the changed type value to obtain the source of the changes in the broadcast
     *  int configFeature = 0;
     *  int configNetwork = 1;
     *  int configSubInfo = 2;
     *  int configAll = 3;
     */
    public static final String CARRIER_CONFIG_CHANGED_TYPE = "carrier_changed_type";

    /* SPRD Feature Porting: Add switch for automatic call record feature. @{ */
    /**
     * Flag specifying whether automatic call record feature is supported.
     * {@hide}
     */
    public static final  String KEY_FEATURE_AUTOMATIC_CALL_RECORD_ENABLED_BOOL =
            "automatic_call_record_enabled_bool";
     /* @} */

    // SPRD: FEATURE_APN_EDITOR_PLUGIN
    public static final String KEY_OPERATOR_APN_NOT_EDITABLE = "operator_apn_not_editable";

    //SPRD: Bug Bug 757343 add for operator which APN could not show XCAP
    public static final String KEY_OPERATOR_APN_NOT_SHOW_XCAP = "operator_apn_not_show_xcap";

    public static final String KEY_APN_SHOW_XCAP_TYPE_WITH_VERSION =
            "apn_show_xcap_type_with_version";

    /* SPRD Feature Porting: Vibrate when call connected or disconnected feature. @{ */
    /**
     * Flag specifying whether Vibrate when call connected or disconnected feature is supported.
     */
    public static final String KEY_FEATURE_VIBRATE_FOR_CALL_CONNECTION_BOOL =
            "vibrate_for_call_connection_bool";
    /* @} */

    //SPRD: add for bug872634
    public static final String KEY_SUPPORT_UP_DOWN_GRADE_VT_CONFERENCE = "support_up_down_vt_conference";

    /* SPRD: bug#743963, FEATURE_MANUAL_QUERY_NETWORK @{ */
    public static final String KEY_FEATURE_SHOW_NETWORK_SELECTION_WARNING_BOOL =
            "show_network_selection_warning_bool";
    /* @{ */

    //SPRD:add for bug661370
    public static final String KEY_CARRIER_SUPPORTS_VOWIFI_EMERGENCY_CALL =
            "support_vowifi_emergency_call";

    //SPRD: add for bug678753, bug969782
    public static final String KEY_CARRIER_RETRY_ECC_VOWIFI = "retry_ecc_vowifi";
    public static final String KEY_CARRIER_DEREG_VOWIFI_BEFORE_ECC = "deregister_vowifi_before_dial_ecc";
    public static final String KEY_CARRIER_ECC_VIA_IMS = "ecc_via_ims";
    //UNISOC: add for bug1009987
    public static final String KEY_CARRIER_DIAL_ECC_VOWIFI_WHEN_AIRPLANE = "dial_ecc_vowifi_when_airplane_mode";
    public static final String KEY_CARRIER_ECC_FALLBACK_TO_CS = "ecc_fallback_to_cs_retry_ecc";
    public static final String KEY_CARRIER_ECC_ON_VOWIFI_FIRST = "ecc_on_vowifi_first";
    //UNISOC:add for bug 1087183,dial emergency on cs
    public static final String KEY_CARRIER_ALWAYS_DIAL_ECC_ON_CS = "always_dial_ecc_on_cs";
    public static final String KEY_CARRIER_DEREG_VOWIFI_WHEN_CELLULAR_PREFERRED = "deregister_vowifi_when_cellular_preffered";

    /**
     *Add for setting homepage via MCC\MNC
     *@{
     */
    public static final String KEY_GLO_CONF_HOMEPAGE_URL = "homepage_url";
    /*@}*/

    // SPRD Add for Bug 570658: Heartbeat interval operator network adaptation. --> BEG
    public static final String KEY_NETWORK_NAT_OVERTIME_2G = "network_nat_overtime_2g";
    public static final String KEY_NETWORK_NAT_OVERTIME_3G = "network_nat_overtime_3g";
    public static final String KEY_NETWORK_NAT_OVERTIME_4G = "network_nat_overtime_4g";
    // SPRD Add for Bug 570658: Heartbeat interval operator network adaptation. --> END

    /**
     * SPRD: screen off 5s after call connection. See bug693120
     * {@hide}
     */
    public static final String KEY_SCREEN_OFF_IN_ACTIVE_CALL_STATE_BOOL =
            "screen_off_in_active_call_state_bool";

    /**
     * If false,do not automatically set primary card according to IccPolicy after hot swap if
     * current primary card is active.
     */
    public static final String KEY_FORCE_AUTO_SET_PRIMARY_CARD_AFTER_HOT_SWAP = "key_force_auto_set_primary_card_after_hot_swap";
    public static final String KEY_GLO_CONF_ECC_LIST_NO_CARD = "ecclist_nocard";
    public static final String KEY_GLO_CONF_ECC_LIST_WITH_CARD = "ecclist_withcard";
    public static final String KEY_GLO_CONF_FAKE_ECC_LIST_WITH_CARD = "fake_ecclist_withcard";
    /* Add for feature: Query Available Networks @{ */
    public static final String KEY_FEATURE_QUERY_NETWORK_RESULT_SHOW_TYPE_SUFFIX = "query_network_result_show_type_suffix";
    public static final String KEY_FEATURE_QUERY_NETWORK_RESULT_SHOW_STATE_SUFFIX = "query_network_result_show_state_suffix";
    /* @} */

    /**
     * SPRD Feature Porting: Flip to silence from incoming calls.
     * {@hide}
     */
    public static final String KEY_FEATURE_FLIP_SILENT_INCOMING_CALL_ENABLED_BOOL =
            "flip_to_silent_incoming_call_enabled_bool";

    // SPRD Feature Porting: Fade in ringer volume when incoming calls.
    /**
     * SPRD: Flag specifying fade-in feature is supported.
     * {@hide}
     */
    public static final String KEY_FEATURE_FADE_IN_ENABLED_BOOL = "fade_in_enabled_bool";

    /* SPRD Feature Porting: Add for double press headset media button feature. @{ */
    /**
     * Flag specifying whether Double press on the headset key feature is supported.
     * {@hide}
     */
    public static final String KEY_FEATURE_DOUBLE_PRESS_ON_HEADSET_KEY_BOOL =
            "double_press_on_headset_key_bool";
    /* @} */

    /**
     * If false,do not automatically set voice card to primary card.
     * @hide
     */
    public static final String KEY_CARRIER_SET_VOICE_CARD_TO_PRIMARY_CARD_BOOL = "set_voice_card_to_primary_card_bool";


    /* SPRD porting:bug693518 CMCC feature. @{ */
    public static final String KEY_CARRIER_SUPPORTS_VIDEO_CALL_ONLY =
            "support_video_call_only";
    public static final String KEY_CARRIER_SHOW_HOLD_BUTTON =
            "show_hold_button";
    /*@}*/
    //SPRD:add fot bug 693137
    public static final String KEY_CARRIER_SUPPORTS_VIDEO_RING_TONE = "support_video_ring_tone";

    /*SPRD: add for esm flag feature @{ */
    public static final  String KEY_FEATURE_ATTACH_APN_ENABLE_BOOL =
            "attach_apn_enable";
    public static final  String KEY_FEATURE_PLMNS_ESM_FLAG_STRING =
            "plmns_esm_flag";
    /* @} */

    /*SPRD: add for single pdn feature @{ */
    public static final String KEY_PLMNS_SINGLE_PDN = "plmns_single_pdn";

    /* SPRD: add for bug693288 @{ */
    public static final String KEY_GLO_CONF_VOICEMAIL_TAG = "vmtag";
    /* @} */
    public static final String KEY_UT_PRIORITY =
            "ut_priority";
    public static final String KEY_UT_FALLBACK_VOLTE =
            "ut_fallback_volte";
    // UNISOC: add for feature 888845
    public static final String KEY_CONFIG_IMS_CALLFORWARD_SERVICECLASS = "config_ims_callforward_serviceclass";

    /**
     * Give a chance to control whether always show network type.
     */
    public static final String KEY_ALWAYS_SHOW_NETWORK_OPTION = "always_show_network_option";

    /**
     * Specially for devices whose slots both supported multi-mode like L+W. But
     * only primary card is allowed to show network option.
     */
    public static final String KEY_NETWORK_OPTION_NOT_ALLOWED_FOR_NON_PRIMARY_CARD = "network_option_not_allowed_for_non_primary_card";

    /**
     * Operator special requirements: Don't show network type option for non-USIM card.
     */
    public static final String KEY_NETWORK_OPTION_NOT_ALLOWED_FOR_NON_USIM_CARD = "network_option_not_allowed_for_non_USIM_card";

    /** {@hide}
     * SPRD:Add the interface for supplementary business to set the Ut interface or CS domain
     */
    public static final String KEY_NETWORK_RAT_PREFER_INT = "network_RAT_prefer";
    public static final String KEY_NETWORK_RAT_ON_SWITCH_IMS = "network_rat_set_on_switch_ims";//UNISOC: add for bug982110

    /**
     *SPRD:Modify for bug 850548
     */
    public static final String KEY_QUERY_ALL_CF = "key_query_all_cf";

    //UNISSOC: add for bug1237955
    public static final String KEY_SUPPORT_SET_SS_FLAG = "key_support_set_ss_flag";

    /* SPRD: FEATURE_OF_APN_AND_DATA_POP_UP @{ */
    public static final String KEY_FEATURE_DATA_AND_APN_POP_BOOL =
            "feature_data_and_apn_pop_bool";
    public static final String KEY_FEATURE_DATA_AND_APN_POP_OPERATOR_STRING =
            "feature_data_and_apn_pop_operator_string";
    /* @} */

    /* SPRD: MTU configured by operator @{ */
    public static final String KEY_MTU_CONFIG_FOR_IPV6 = "mtu_config_for_IPV6";
    public static final String KEY_MTU_CONFIG = "mtu_config";
    /* @} */

    // SPRD: add for bug719941
    public static final String KEY_HIDE_VIDEO_CALL_FORWARD = "hide_video_call_forward";

    //UNISOC add for Bug#1147915
    public static final String KEY_CARRIER_REQUEST_XCAP_PDN_FOR_CW = "request_xcap_pdn_for_cw";
    public static final String KEY_CARRIER_REQUEST_XCAP_PDN_FOR_CLIR = "request_xcap_pdn_for_clir";
    public static final String KEY_CARRIER_SUPPORT_DISABLE_UT_BY_NETWORK = "support_disable_ut_by_network";
    /**
     * If true, disable/enable SIM card only power off/on radio.
     * {@hide}
     */
    public static final String KEY_FEATURE_SET_FAKE_SIM_ENABLE_BOOL = "set_fake_sim_enable_boolean";
    //SPRD:add for bug718250
    public static final String KEY_FEATURE_SET_VOICE_DOMAIN_BOOL = "set_voice_domain_bool";

    // SPRD:add for Bug754508
    public static final String KEY_SUPPORT_SHOW_NETWORK_DIALOG = "support_show_open_network_dialog";
    // SPRD: add for bug757327
    public static final String KEY_SUPPORT_SEND_USSD_OVER_VOWIFI = "support_send_ussd_over_vowifi";

    // SPRD: add for bug709181
    public static final String KEY_HIDE_CALLBARRING_DEACTIVATE_ALL
            = "hide_callbarring_deactivate_all";

    // SPRD:add for Bug711538
    public static final String KEY_CALL_FORWARD_SHOW_DIALOG = "call_forward_show_dialog";
    //SPRD:add for Bug718809
    public static final String KEY_FEATURE_EF_SPN_DISPLAY_FIRST = "EF_SPN_display_first";

    // SPRD Feature: Support invite by carrier config.
    /**
     * Flag specifying whether invite function is supported.
     * {@hide}
     */
    public static final String KEY_FEATURE_INVITE_ENABLED_BOOL = "invite_enabled_bool";

    // SPRD: Show manage conference button even srvcc for cmcc.
    /**
     * Flag specifying whether show conference button when srvcc.
     * {@hide}
     */
    public static final String KEY_MANAGE_CONFERENCE_EVEN_SRVCC =
            "manage_conference_even_srvcc_bool";

    // SPRD: add for bug715443
    public static final String KEY_OPERATOR_SHOW_IMS_APN = "operator_show_ims_apn";

    //UNISOC: add for bug928599
    public static final String KEY_OPERATOR_SHOW_XCAP_APN = "operator_show_xcap_apn";

    public static final String KEY_ANTITHEFT_PREFER_GPS="antitheft_prefer_gps";
    //SPRD:add for bug690302
    public static final String KEY_CARRIER_SUPPORTS_MULTI_CALL =
            "support_multi_call";
    public static final String KEY_FEATURE_DUAL_VOLTE_WHITE_LIST = "operator_dual_volte_white_list";
    public static final String KEY_CALL_TELCEL_SHOW_TOAST = "support_show_toast"; //SPRD:add for Bug809098
    public static final String KEY_WIFI_CALL_NOTIFICATION_ENABLE = "wifi_call_notification_enable";//SPRD Feature: Support wifiCall notification.
    public static final String KEY_CARRIER_DISABLE_MERGE_CALL  =
            "disable_merge_call";
    public static final String KEY_SUPPORT_SHOW_WIFI_CALLING_PREFERENCE = "support_show_wifi_calling_preference";//SPRD:add for Bug843265

    /* SPRD Feature Porting: Airtel RingBackTone Feature. @{ */
    /**
     * Flag specifying whether airtel ringbacktone feature is supported.
     */
    public static final  String KEY_FEATURE_AIRTEL_RINGBACKTONE_ENABLED_BOOL =
            "airtel_ringbacktone_enabled_bool";
     /* @} */

    /**
     * SPRD: [Bug475782] Flag for fixed primary slot.
     */
    public static final String KEY_FIXED_PRIMARY_SLOT_INT = "fixed_primary_slot_int";
    /* UNISOC: add for bug 843666 controll WFC showing via OMA request @{ */
    public static final String KEY_DEFAULT_SHOW_WIFI_CALL = "default_show_wifi_call";
    public static final String KEY_OPERATOR_STRING_SHOW_WIFI_CALL = "operator_string_show_wifi_call";
    /* @} */
    // SPRD: add new feature for data switch on/off
    public static final String KEY_FEATURE_MANUAL_DEFAULT_DATA_ENABLE = "manual_default_data_enable";

    public static final String KEY_SUPPORT_NON_DOMESTIC_ROAMING = "support_non_domestic_roaming";
    //UNISOC: add for bug 893992
    public static final String KEY_STK_DIFFERENT_LAUNCH_BROWSER_TR = "stk_different_launch_browser_tr";
    // SPRD: add for bug 905754
    public static final String KEY_SUPPORT_TXRX_VT_CALL_BOOL = "support_txrx_vt_call_bool";

    public static final String KEY_MT_REQUEST_MEDIA_CHANGE_TIMEOUT_LONG = "mt_request_media_change_timeout_long";
    // SPRD: add for bug 968647
    public static final String KEY_WIFI_CALLING_TITLE = "wifi_calling_title";
    // UNISOC: add for BUG 925034
    public static final String KEY_FDN_NUMBER_LENGTH = "key_fdn_number_length";
    //SPRD:add for bug 977043
    public static final String KEY_SEPARATE_SETTING_FOR_WFC_VOLTE = "separate_setting_for_wfc_volte";
    // UNISOC: add for BUG 985539
    public static final String KEY_SUPPORT_FIXED_VT_CALL_RESOLUTION= "support_fixed_vt_call_resolution";

    // UNISOC: add for BUG 982460
    public static final String KEY_CARRIER_SHOW_VOLTE_SETTING = "carrier_show_volte_setting";

    // UNISOC: add for Bug 1000675
    public static final String KEY_CARRIER_ENABLE_CB_BA_ALL_AND_PW_SETTINGS = "carrier_enable_cb_ba_all_and_pw_settings";

    /**
     * UNISOC :add for Bug949130,the operator name is displayed as a customer request
     * and no longer cares about the 3GPP protocol display rules.
     * {@hide}
     */
    public static final String KEY_NETWORK_NAME_IGNORE_RULES = "network_operator_ignore_rules";

    /** The default value for every variable. */
    private final static PersistableBundle sDefaultsEx;
    //UNISOC: add for bug999104
    public static final String KEY_SUPPORT_SS_OVER_VOWIFI_WITH_AIRPLANE = "support_send_ss_over_vowifi_with_airplane";

    //UNISOC: add for BUG1124237
    public static final String KEY_CALL_WAITING_SERVICE_CLASS_INT = "call_waiting_service_class_int";


    //UNISOC: add for Bug1127968
    public static final String KEY_DISCONNECT_OTHER_CALL_FOR_EMERGENCY_CALL = "config_disconnect_other_call_for_emergency_call";

    //UNISOC: add for BUG 1043239
    public static final String KEY_HD_VOICE_ICON_SHOULD_BE_REMOVED= "HD_voice_icon_should_be_removed";

   //UNISOC: add for Bug 1229682
    public static final String KEY_CALL_BARRING_SERVICE_CLASS_INT= "call_barring_service_class_int";

    //UNISOC: add for Bug1258195
    public static final String KEY_SHOW_CB_FACILITY_BAICR = "show_cb_facility_baicr";

    public static final String KEY_DATA_ROAMING_ENABLED_SETTINGS_BOOL = "data_roaming_enabled_settings_bool";
    static {
        sDefaultsEx = new PersistableBundle();
        // SPRD: Add switch for automatic call record feature.
        sDefaultsEx.putBoolean(KEY_FEATURE_AUTOMATIC_CALL_RECORD_ENABLED_BOOL, true);
        // SPRD Feature Porting: Vibrate when call connected or disconnected feature.
        sDefaultsEx.putBoolean(KEY_FEATURE_VIBRATE_FOR_CALL_CONNECTION_BOOL, true);
        /**
         *Add for setting homepage via MCC\MNC
         *@{
         */
        sDefaultsEx.putString(KEY_GLO_CONF_HOMEPAGE_URL, "");
        /*@}*/
        /*feature: vowifi emergency call@{*/
        sDefaultsEx.putBoolean(KEY_CARRIER_SUPPORTS_VOWIFI_EMERGENCY_CALL, true);//SPRD:add for bug661370
        sDefaultsEx.putBoolean(KEY_CARRIER_RETRY_ECC_VOWIFI, false); //SPRD:add for bug678753
        sDefaultsEx.putBoolean(KEY_CARRIER_DEREG_VOWIFI_BEFORE_ECC, false); //SPRD:add for bug776303
        sDefaultsEx.putBoolean(KEY_CARRIER_ECC_VIA_IMS,false);
        //UNISOC: add for bug1009987
        sDefaultsEx.putBoolean(KEY_CARRIER_DIAL_ECC_VOWIFI_WHEN_AIRPLANE, false);
        sDefaultsEx.putBoolean(KEY_CARRIER_ECC_FALLBACK_TO_CS, false);
        sDefaultsEx.putBoolean(KEY_CARRIER_ECC_ON_VOWIFI_FIRST, false);
        sDefaultsEx.putBoolean(KEY_CARRIER_DEREG_VOWIFI_WHEN_CELLULAR_PREFERRED, false);
        /*@}*/
        // UNISOC:add for bug 1087183,dial emergency on cs
        sDefaultsEx.putBoolean(KEY_CARRIER_ALWAYS_DIAL_ECC_ON_CS, false);
        // SPRD: FEATURE_APN_EDITOR_PLUGIN
        sDefaultsEx.putString(KEY_OPERATOR_APN_NOT_EDITABLE, "");
        //SPRD: Bug Bug 757343 add for operator which APN could not show XCAP
        sDefaultsEx.putString(KEY_OPERATOR_APN_NOT_SHOW_XCAP, "");
        sDefaultsEx.putBoolean(KEY_APN_SHOW_XCAP_TYPE_WITH_VERSION, true);
        sDefaultsEx.putBoolean(KEY_CARRIER_SET_VOICE_CARD_TO_PRIMARY_CARD_BOOL, false);
        sDefaultsEx.putBoolean(KEY_FORCE_AUTO_SET_PRIMARY_CARD_AFTER_HOT_SWAP, false);
        sDefaultsEx.putBoolean(KEY_FEATURE_QUERY_NETWORK_RESULT_SHOW_TYPE_SUFFIX, true);
        sDefaultsEx.putBoolean(KEY_FEATURE_QUERY_NETWORK_RESULT_SHOW_STATE_SUFFIX, true);
        sDefaultsEx.putString(KEY_GLO_CONF_ECC_LIST_NO_CARD, "");
        sDefaultsEx.putString(KEY_GLO_CONF_ECC_LIST_WITH_CARD, "");
        sDefaultsEx.putString(KEY_GLO_CONF_FAKE_ECC_LIST_WITH_CARD, "");
        // SPRD porting:bug693518 CMCC feature
        sDefaultsEx.putBoolean(KEY_CARRIER_SUPPORTS_VIDEO_CALL_ONLY, false);
        sDefaultsEx.putBoolean(KEY_CARRIER_SHOW_HOLD_BUTTON, true);
        sDefaultsEx.putBoolean(KEY_CARRIER_SUPPORTS_VIDEO_RING_TONE, false);
        // SPRD Feature Porting: Flip to silence from incoming calls.
        sDefaultsEx.putBoolean(KEY_FEATURE_FLIP_SILENT_INCOMING_CALL_ENABLED_BOOL, true);
        /* SPRD: add for bug693288 @{ */
        sDefaultsEx.putString(KEY_GLO_CONF_VOICEMAIL_TAG, "");
        /*@}*/
        sDefaultsEx.putBoolean(KEY_ALWAYS_SHOW_NETWORK_OPTION, false);
        sDefaultsEx.putBoolean(KEY_NETWORK_OPTION_NOT_ALLOWED_FOR_NON_PRIMARY_CARD, true);
        sDefaultsEx.putBoolean(KEY_NETWORK_OPTION_NOT_ALLOWED_FOR_NON_USIM_CARD, false);
        // UNISOC: add for feature 888845
        sDefaultsEx.putInt(KEY_CONFIG_IMS_CALLFORWARD_SERVICECLASS, -1);
        sDefaultsEx.putString(KEY_UT_PRIORITY, "");
        sDefaultsEx.putString(KEY_UT_FALLBACK_VOLTE, "");
        sDefaultsEx.putInt(KEY_NETWORK_RAT_PREFER_INT,0);
        sDefaultsEx.putBoolean(KEY_NETWORK_RAT_ON_SWITCH_IMS,false);
        // SPRD: add for bug850548
        sDefaultsEx.putBoolean(KEY_QUERY_ALL_CF, true);

        sDefaultsEx.putBoolean(KEY_SUPPORT_SET_SS_FLAG,false);
        // SPRD Feature Porting: Fade in ringer volume when incoming calls.
        sDefaultsEx.putBoolean(KEY_FEATURE_FADE_IN_ENABLED_BOOL, true);
        // SPRD Feature Porting: Add for double press headset media button feature.
        sDefaultsEx.putBoolean(KEY_FEATURE_DOUBLE_PRESS_ON_HEADSET_KEY_BOOL, false);
        /* SPRD: FEATURE_OF_APN_AND_DATA_POP_UP @{ */
        sDefaultsEx.putBoolean(KEY_FEATURE_DATA_AND_APN_POP_BOOL, false);
        sDefaultsEx.putString(KEY_FEATURE_DATA_AND_APN_POP_OPERATOR_STRING, "");
        /* @} */
        //SPRD: add for bug872634
        sDefaultsEx.putBoolean(KEY_SUPPORT_UP_DOWN_GRADE_VT_CONFERENCE, false);

        /*  SPRD: bug#743963, FEATURE_MANUAL_QUERY_NETWORK @{ */
        sDefaultsEx.putBoolean(KEY_FEATURE_SHOW_NETWORK_SELECTION_WARNING_BOOL, true);
        /* @} */

        // SPRD Add for Bug 570658: Heartbeat interval operator network adaptation. -->BEG
        sDefaultsEx.putInt(KEY_NETWORK_NAT_OVERTIME_2G, 5);
        sDefaultsEx.putInt(KEY_NETWORK_NAT_OVERTIME_3G, 15);
        sDefaultsEx.putInt(KEY_NETWORK_NAT_OVERTIME_4G, 30);
        // SPRD Add for Bug 570658: Heartbeat interval operator network adaptation. -->END
        sDefaultsEx.putBoolean(KEY_FEATURE_SET_FAKE_SIM_ENABLE_BOOL, false);
        // SPRD:add for Bug754508
        sDefaultsEx.putBoolean(KEY_SUPPORT_SHOW_NETWORK_DIALOG, true);
        // SPRD: add for bug757327
        sDefaultsEx.putBoolean(KEY_SUPPORT_SEND_USSD_OVER_VOWIFI, true);
        // SPRD: add for bug709181
        sDefaultsEx.putBoolean(KEY_HIDE_CALLBARRING_DEACTIVATE_ALL, false);
        sDefaultsEx.putBoolean(KEY_CALL_FORWARD_SHOW_DIALOG, true); //SPRD:add for Bug711538
        // SPRD: add for bug719941
        sDefaultsEx.putBoolean(KEY_HIDE_VIDEO_CALL_FORWARD, false);

        //UNISOC: add for Bug#1147915
        sDefaultsEx.putBoolean(KEY_CARRIER_REQUEST_XCAP_PDN_FOR_CW, true);
        sDefaultsEx.putBoolean(KEY_CARRIER_REQUEST_XCAP_PDN_FOR_CLIR, true);
        sDefaultsEx.putBoolean(KEY_CARRIER_SUPPORT_DISABLE_UT_BY_NETWORK, false);

        /* SPRD: MTU configured by operator @{ */
        sDefaultsEx.putInt(KEY_MTU_CONFIG_FOR_IPV6, 0);
        sDefaultsEx.putInt(KEY_MTU_CONFIG, 1500);
        /* @} */
        sDefaultsEx.putBoolean(KEY_FEATURE_EF_SPN_DISPLAY_FIRST, false); //SPRD:add for Bug718809
        // SPRD Feature: Support invite by carrier config.
        sDefaultsEx.putBoolean(KEY_FEATURE_INVITE_ENABLED_BOOL, true);
        sDefaultsEx.putBoolean(KEY_FEATURE_SET_VOICE_DOMAIN_BOOL, false); //SPRD:add for bug718250
        // SPRD: Show manage conference button even srvcc for cmcc.
        sDefaultsEx.putBoolean(KEY_MANAGE_CONFERENCE_EVEN_SRVCC, false);
        // SPRD: add for bug715443
        sDefaultsEx.putString(KEY_OPERATOR_SHOW_IMS_APN, "");

        //UNISOC: add for bug928599
        sDefaultsEx.putString(KEY_OPERATOR_SHOW_XCAP_APN, "");

        sDefaultsEx.putBoolean(KEY_ANTITHEFT_PREFER_GPS, false);
        sDefaultsEx.putBoolean(KEY_CARRIER_SUPPORTS_MULTI_CALL, true);//SPRD:add for bug690302
        sDefaultsEx.putString(KEY_FEATURE_DUAL_VOLTE_WHITE_LIST, "");
        sDefaultsEx.putBoolean(KEY_CALL_TELCEL_SHOW_TOAST, true);//SPRD:add for Bug809098
        sDefaultsEx.putBoolean(KEY_WIFI_CALL_NOTIFICATION_ENABLE, false);//SPRD Feature: Support wifiCall notification.
        sDefaultsEx.putBoolean(KEY_CARRIER_DISABLE_MERGE_CALL, false);
        // SPRD Feature Porting: Airtel RingBackTone Feature.
        sDefaultsEx.putBoolean(KEY_FEATURE_AIRTEL_RINGBACKTONE_ENABLED_BOOL, false);
        sDefaultsEx.putInt(KEY_FIXED_PRIMARY_SLOT_INT,0);
        sDefaultsEx.putBoolean(KEY_SUPPORT_SHOW_WIFI_CALLING_PREFERENCE , true);
        /* UNISOC: add for bug 843666 controll WFC showing via OMA request @{ */
        sDefaultsEx.putBoolean(KEY_DEFAULT_SHOW_WIFI_CALL, true);
        sDefaultsEx.putString(KEY_OPERATOR_STRING_SHOW_WIFI_CALL, "");
        /* @} */

        // SPRD: add new feature for data switch on/off
        sDefaultsEx.putInt(KEY_FEATURE_MANUAL_DEFAULT_DATA_ENABLE,1);

        //Unisoc: add for bug829719
        sDefaultsEx.putBoolean(KEY_SUPPORT_NON_DOMESTIC_ROAMING, false);
        //UNISOC: add for bug893992
        sDefaultsEx.putBoolean(KEY_STK_DIFFERENT_LAUNCH_BROWSER_TR, false);
        // SPRD: add for bug 905754
        sDefaultsEx.putBoolean(KEY_SUPPORT_TXRX_VT_CALL_BOOL, false);

        sDefaultsEx.putLong(KEY_MT_REQUEST_MEDIA_CHANGE_TIMEOUT_LONG ,10000);
        // SPRD: add for bug 968647
        sDefaultsEx.putString(KEY_WIFI_CALLING_TITLE,"");

        // UNISOC: add for BUG 925034
        sDefaultsEx.putInt(KEY_FDN_NUMBER_LENGTH, 20);
        //SPRD:add for bug 977043
        sDefaultsEx.putBoolean(KEY_SEPARATE_SETTING_FOR_WFC_VOLTE, true);

       // UNISOC: add for BUG 985539
        sDefaultsEx.putInt(KEY_SUPPORT_FIXED_VT_CALL_RESOLUTION,-1);

        // UNISOC: add for BUG 982460
        sDefaultsEx.putBoolean(KEY_CARRIER_SHOW_VOLTE_SETTING, true);

        sDefaultsEx.putBoolean(KEY_SUPPORT_SS_OVER_VOWIFI_WITH_AIRPLANE, false);

        // UNISOC: add for Bug 1000675
        sDefaultsEx.putBoolean(KEY_CARRIER_ENABLE_CB_BA_ALL_AND_PW_SETTINGS, false);
        sDefaultsEx.putString(KEY_NETWORK_NAME_IGNORE_RULES,"");

        //UNISOC: add for BUG1124237
        sDefaultsEx.putInt(KEY_CALL_WAITING_SERVICE_CLASS_INT, 1 /* SERVICE_CLASS_VOICE */);

        //UNISOC: add for Bug1127968
        sDefaultsEx.putBoolean(KEY_DISCONNECT_OTHER_CALL_FOR_EMERGENCY_CALL, true);

        //UNISOC: add for Bug1043239
        sDefaultsEx.putBoolean(KEY_HD_VOICE_ICON_SHOULD_BE_REMOVED, false);

        //UNISOC: add for BUG 1229682
        sDefaultsEx.putInt(KEY_CALL_BARRING_SERVICE_CLASS_INT, 0 /* SERVICE_CLASS_NONE */);

        sDefaultsEx.putBoolean(KEY_SHOW_CB_FACILITY_BAICR, true);

        sDefaultsEx.putBoolean(KEY_DATA_ROAMING_ENABLED_SETTINGS_BOOL, false);
    }

    /**
     * Returns a new bundle with the default value for every supported configuration variable.
     *
     * @hide
     */
    @NonNull
    public static PersistableBundle getDefaultConfig() {
        return new PersistableBundle(sDefaultsEx);
    }
}
