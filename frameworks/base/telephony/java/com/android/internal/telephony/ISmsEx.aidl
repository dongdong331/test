package com.android.internal.telephony;
interface ISmsEx {
    //sprd:add for cb
    boolean commonInterfaceForMessaging(int commonType, long szSubId, String szString, inout int[] data);

    int copyMessageToIccEfForSubscriber(in int subId, String callingPkg, int status, in byte[] pdu, in byte[] smsc);

    // added by tony for mms upgrade
    String getSimCapacityForSubscriber(int subId);
    void setCMMSForSubscriber(int subId, int value);
    void setPropertyForSubscriber(int subId, String key,String value);
    String getSmscForSubscriber(in int subId);
    boolean setSmscForSubscriber(in int subId, String smscAddr);
}