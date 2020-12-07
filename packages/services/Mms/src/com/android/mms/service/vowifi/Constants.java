package com.android.mms.service.vowifi;

public class Constants {
    public static final boolean DEBUG = true;

    public static class APNType {
        public static final int APN_TYPE_NORMAL = 1;
        public static final int APN_TYPE_SOS    = 2;
        public static final int APN_TYPE_XCAP   = 4;
        public static final int APN_TYPE_MMS    = 8;
    }

    public static class Result {
        public static final int INVALID_ID = -1;

        public static final int FAIL       = 0;
        public static final int SUCCESS    = 1;
    }

    public class JSONUtils {

        // Callback constants
        public static final String KEY_EVENT_CODE = "event_code";
        public static final String KEY_EVENT_NAME = "event_name";
        public static final String KEY_STATE_CODE = "state_code";

        // Security
        public static final String KEY_SESSION_ID = "session_id";

        public static final int STATE_CODE_SECURITY_INVALID_ID = 1;
        public static final int STATE_CODE_SECURITY_AUTH_FAILED = 2;

        public final static int SECURITY_EVENT_CODE_BASE = 0;
        public final static int EVENT_CODE_ATTACH_SUCCESSED = SECURITY_EVENT_CODE_BASE + 1;
        public final static int EVENT_CODE_ATTACH_FAILED = SECURITY_EVENT_CODE_BASE + 2;
        public final static int EVENT_CODE_ATTACH_PROGRESSING = SECURITY_EVENT_CODE_BASE + 3;
        public final static int EVENT_CODE_ATTACH_STOPPED = SECURITY_EVENT_CODE_BASE + 4;

        public final static String EVENT_ATTACH_SUCCESSED = "attach_successed";
        public final static String EVENT_ATTACH_FAILED = "attach_failed";
        public final static String EVENT_ATTACH_PROGRESSING = "attach_progressing";
        public final static String EVENT_ATTACH_STOPPED = "attach_stopped";

        // Keys for security callback
        public final static String KEY_ERROR_CODE = "error_code";
        public final static String KEY_PROGRESS_STATE = "progress_state";
        public final static String KEY_LOCAL_IP4 = "local_ip4";
        public final static String KEY_LOCAL_IP6 = "local_ip6";
        public final static String KEY_PCSCF_IP4 = "pcscf_ip4";
        public final static String KEY_PCSCF_IP6 = "pcscf_ip6";
        public final static String KEY_DNS_IP4 = "dns_ip4";
        public final static String KEY_DNS_IP6 = "dns_ip6";
        public final static String KEY_PREF_IP4 = "pref_ip4";
        public final static String KEY_HANDOVER = "is_handover";
        public final static String KEY_SOS = "is_sos";

        public final static int USE_IP4 = 0;
        public final static int USE_IP6 = 1;

    }

}
