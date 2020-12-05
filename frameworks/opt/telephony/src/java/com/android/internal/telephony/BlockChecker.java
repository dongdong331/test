package com.android.internal.telephony;

import android.content.Context;
import android.os.Bundle;
import android.provider.BlockedNumberContract;
import android.telephony.Rlog;
import android.telephony.TelephonyManagerEx;

/**
 * {@hide} Checks for blocked phone numbers against {@link BlockedNumberContract}
 */
public class BlockChecker {
    private static final String TAG = "BlockChecker";
    private static final boolean VDBG = false; // STOPSHIP if true.
    private static int sCallBlockType = 1;
    private static int sSmsBlockType = 2;

    /**
     * Returns {@code true} if {@code phoneNumber} is blocked according to {@code extras}.
     * <p>
     * This method catches all underlying exceptions to ensure that this method never throws any
     * exception.
     * <p>
     * @deprecated use {@link #isBlocked(Context, String, Bundle)} instead.
     *
     * @param context the context of the caller.
     * @param phoneNumber the number to check.
     * @return {@code true} if the number is blocked. {@code false} otherwise.
     */
    /* UNISOC: Bringup for CallFireWall @{ */
//    @Deprecated
//    public static boolean isBlocked(Context context, String phoneNumber) {
//        return isBlocked(context, phoneNumber, null /* extras */);
//    }
    /* @} */


    /**
     * Returns {@code true} if {@code phoneNumber} is blocked according to {@code extras}.
     * <p>
     * This method catches all underlying exceptions to ensure that this method never throws any
     * exception.
     *
     * @param context the context of the caller.
     * @param phoneNumber the number to check.
     * @param extras the extra attribute of the number.
     * @return {@code true} if the number is blocked. {@code false} otherwise.
     */
    /* UNISOC: Bringup for CallFireWall @{ */
    public static boolean isBlocked(Context context, String phoneNumber, Bundle extras, int blockType) {
        boolean isBlocked = false;
        long startTimeNano = System.nanoTime();
        boolean isCallBlock;
        boolean isSmsBlock;
        TelephonyManagerEx tmEx = TelephonyManagerEx.from(context);

        if (blockType == sCallBlockType) {
            if (tmEx != null && tmEx.isCallFireWallInstalled()) {
                isCallBlock = tmEx.checkIsBlockCallNumber(context, phoneNumber);
            } else {
                isCallBlock = false;
            }
            try {
                if (BlockedNumberContract.SystemContract.shouldSystemBlockNumber(context,
                        phoneNumber,  extras) && isCallBlock) {
                    Rlog.d(TAG, phoneNumber + " is blocked call");
                    isBlocked = true;
                } else {
                    isBlocked = false;
                }
            } catch (Exception e) {
                Rlog.e(TAG, "Exception checking for blocked number: "+ e);
            }
        }

        if (blockType == sSmsBlockType) {
            if (tmEx != null && tmEx.isCallFireWallInstalled()) {
                isSmsBlock = tmEx.checkIsBlockSMSNumber(context, phoneNumber);
            } else {
                isSmsBlock = false;
            }
            try {
                if (BlockedNumberContract.SystemContract.shouldSystemBlockNumber(
                        context, phoneNumber, extras) && isSmsBlock) {
                    Rlog.d(TAG, phoneNumber + " is blocked SMS.");
                    isBlocked = true;
                } else {
                    isBlocked = false;
                }
            } catch (Exception e) {
                Rlog.e(TAG, "Exception checking for blocked number: " + e);
            }
        }
        int durationMillis = (int) ((System.nanoTime() - startTimeNano) / 1000000);
        if (durationMillis > 500 || VDBG) {
            Rlog.d(TAG, "Blocked number lookup took: " + durationMillis + " ms.");
        }
        return isBlocked;
    }
    /* @} */
}
