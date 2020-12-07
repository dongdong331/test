package com.android.dialer.sprd.calllog;

import android.provider.CallLog.Calls;

//Add by Sprd
public interface CallLogClearColumn {

    /** The projection to use when querying the call log table */
    public static final String[] CALL_LOG_PROJECTION = new String[] {
            Calls._ID,
            Calls.NUMBER,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.CACHED_NAME,
            Calls.CACHED_NUMBER_TYPE,
            Calls.CACHED_NUMBER_LABEL, /* TODO,
            Calls.SUB_ID*/
            /* SPRD: add for search call log in google search box feature @{ */
            Calls.PHONE_ACCOUNT_ID,
            Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            Calls.PHONE_ACCOUNT_ID,
            Calls.DATA_USAGE,
            Calls.FEATURES,
            Calls.CACHED_LOOKUP_URI,
            Calls.CACHED_PHOTO_ID,
            Calls.CACHED_FORMATTED_NUMBER
            /* @} */
    };

    public static final int ID_COLUMN_INDEX = 0;
    public static final int NUMBER_COLUMN_INDEX = 1;
    public static final int DATE_COLUMN_INDEX = 2;
    public static final int DURATION_COLUMN_INDEX = 3;
    public static final int CALL_TYPE_COLUMN_INDEX = 4;
    public static final int CALLER_NAME_COLUMN_INDEX = 5;
    public static final int CALLER_NUMBERTYPE_COLUMN_INDEX = 6;
    public static final int CALLER_NUMBERLABEL_COLUMN_INDEX = 7;
    public static final int SIM_COLUMN_INDEX = 8;
    /* SPRD: add for search call log in google search box feature @{ */
    public static final int ACCOUNT_COMPONENT_NAME = 9;
    public static final int ACCOUNT_ID = 10;
    public static final int DATA_USAGE_INDEX = 11;
    public static final int CALL_FEATURES_INDEX = 12;
    public static final int CACHED_LOOKUP_URI_INDEX = 13;
    public static final int CACHED_PHOTO_ID_INDEX = 14;
    public static final int CACHED_FORMATTED_NUMBER_INDEX = 15;
    /* @} */
}
