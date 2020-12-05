/*
 * The Unisoc Inc. 2018
 */
package com.android.internal.app;

import android.content.Intent;

/**
 * Only debug interface for BroadcastReceivers registered in SystemServer.
 * When broadcast receive timeout, use this interface to print debug info
 *
 * @hide
 */
public interface BroadcastTimeoutListener {
    public void onBroadcastTimeout(Intent intent);
}
