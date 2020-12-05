package android.provider;

/**
 * @hide
 *
 */
public final class SettingsEx {

    public static final class SystemEx {

        /** SPRD: add default ringtone uriString to system dataBase @{ */
        public static final String DEFAULT_RINGTONE = "default_ringtone";
        public static final String DEFAULT_NOTIFICATION = "default_notification";
        public static final String DEFAULT_ALARM = "default_alarm";
        public static final String DEFAULT_MESSAGE = "default_message";
        public static final String MESSAGE_TONE = "messagetone";
        /** @} */

    }

    public static final class GlobalEx {
        /**
         * Current emergency number list
         */
        public static final String ECC_LIST_REAL = "ecc_list_real";
        /**
         * Indicate the preference state for the dual volte.
         */
        public static final String MANUAL_DUAL_VOLTE_MODE_ENABLE = "manual_dual_volte_mode_enable";
        /**
         * Indicate state of radio busy to limit some operations related to radios.
         */
        public static final String RADIO_BUSY = "radio_busy";
        //Indicate whether vowifi enable toast show checked
        public static final String ENHANCED_VOWIFI_TOAST_SHOW_ENABLED = "enhanced_vowifi_toast_show_enabled";
        /**
         * Indicate state of data switch during volte call.
         */
        public static final String DATA_ENABLED_DURING_VOLTE_CALLS = "data_enable_during_volte_calls";
        /**
         * Indicate state of network type need to restrict by some operator.
         */
        public static final String RESTRICT_NETWORK_TYPE = "restrict_network_type";
    }
}
