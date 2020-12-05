
package com.android.internal.telephony;

interface IMmsNetwork {

    boolean acquireNetwork(IBinder client, IBinder request, int subId, String callingPkg);
    boolean releaseNetwork(IBinder client, IBinder request, int subId, String callingPkg);

}
