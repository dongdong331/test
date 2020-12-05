package com.android.server.display;

import android.os.RemoteException;
import android.util.Slog;

import java.util.NoSuchElementException;

import vendor.sprd.hardware.enhance.V1_0.IEnhance;
import vendor.sprd.hardware.enhance.V1_0.Status;
import vendor.sprd.hardware.enhance.V1_0.Type;

/**
 *@hide
 */
class SprdNightDisplayController {
    private static final String TAG = "SprdNightDisplayController";
    private static final boolean DEBUG = true;

    IEnhance mIEnhance;

    private boolean mActivated;
    private int mNightValue = 0;
    private static final int NIGHT_DISPLAY_OFF = 0;
    private static final int NIGHT_DISPLAY_DEFAULT = 1;
    private static final int NIGHT_DISPLAY_MIDDLE = 2;
    private static final int NIGHT_DISPLAY_HIGH = 3;
    SprdNightDisplayController() {
        try {
            mIEnhance = IEnhance.getService();
        } catch (RemoteException e) {
            Slog.e(TAG, "getService IEnhance error RemoteException " + e);
            mIEnhance = null;
        } catch (NoSuchElementException e) {
            Slog.e(TAG, "getService IEnhance error NoSuchElementException " + e);
            mIEnhance = null;
        }
    }

    void setNightDispalyStatusAndValue(boolean activated, int nightValue) {
        if (DEBUG) {
            Slog.d(TAG, " setActivated = " + activated + " setNightDisplayValue=" + nightValue);
        }
        if (!activated) {
            if (mActivated != activated){
                Slog.i(TAG, " mIEnhance set NightDispalyStatus: from  " + mActivated
                        + " to " + activated);
                mActivated = activated;
                mNightValue = NIGHT_DISPLAY_OFF;
                setModeValue(mNightValue);
            }
        } else {
            if (mNightValue != nightValue) {
                Slog.i(TAG, " mIEnhance set NightDisplayValue from  " + mNightValue
                        + " to " + nightValue);
                mNightValue = nightValue;
                setModeValue(mNightValue);
            }
            mActivated = activated;
        }
    }

    void setModeValue(int value){
        try {
            if (mIEnhance != null) {
                mIEnhance.setMode(Type.BLP, value);
            } else {
                Slog.e(TAG, " mIEnhance  == null");
            }
        } catch (RemoteException e) {
            Slog.e(TAG, " setNightDispalyStatusAndValue error " + e);
        }
    }

}
