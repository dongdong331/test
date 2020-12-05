package com.android.server.display;

import android.content.Context;
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;

import java.util.NoSuchElementException;

import vendor.sprd.hardware.enhance.V1_0.IEnhance;
import vendor.sprd.hardware.enhance.V1_0.Status;
import vendor.sprd.hardware.enhance.V1_0.Type;
/**
 *@hide
 */
class SprdColorModeController {
    private static final String TAG = "SprdColorModeController";
    private static final boolean DEBUG = true;

    private int mColorMode;
    private int mAutoModeValue;

    private int mCurrentId = UserHandle.USER_NULL;
    private Context mContext;
    private ColorModeSettingObserver mColorModeObserver;
    private AutoValueObserver mAutoValueObserver;
    private ContentResolver mContentResolver;

    private static final int COLOR_MODE_OFF = 0;
    private static final int COLOR_MODE_AUTO = 1;
    private static final int COLOR_MODE_ENHANCE = 2;
    private static final int COLOR_MODE_NORMAL = 3;

    private static final int AUTO_VALUE_NATURE = 0xFF000000;
    private static final int AUTO_VALUE_WARM = 0xFF000001;
    private static final int AUTO_VALUE_COLD = 0xFF000002;
    private static final String COLOR_MODE = "sprd_display_color_temperature_mode";
    private static final String COLOR_MODE_AUTO_VALUE = "sprd_display_color_temperature_auto_mode_value";

    private IEnhance mEnhance;

    SprdColorModeController(Context context) {
        mContext = context;
        mContentResolver = mContext.getContentResolver();
    }

    int getColorMode() {
        return Settings.System.getIntForUser(mContentResolver, COLOR_MODE,
                COLOR_MODE_AUTO, mCurrentId);
    }

    int getAutoValue() {
        return Settings.System.getIntForUser(mContentResolver, COLOR_MODE_AUTO_VALUE,
                AUTO_VALUE_NATURE, mCurrentId);
    }

    void systemReady() {
        try {
            mEnhance = IEnhance.getService();
        } catch (RemoteException e) {
            Slog.e(TAG, "getService IEnhance RemoteException " + e);
            mEnhance = null;
        } catch (NoSuchElementException e) {
            Slog.e(TAG, "getService IEnhance NoSuchElementException " + e);
            mEnhance = null;
        }
        mColorModeObserver = new ColorModeSettingObserver();
        mAutoValueObserver = new AutoValueObserver();
    }

    void setColorMode(int mode) {
        if (mEnhance == null) {
            Slog.e(TAG, "setColorMode  failed mEnhance = null mode=" + mode);
            return;
        }
        try {
            Slog.e(TAG, "setMode mode=" + mode);
            mEnhance.setMode(Type.CMS, mode);
        } catch (RemoteException e) {
            Slog.e(TAG, "setColorMode   failed " + e);
        }
    }

    void setAutoValue(int value) {
        if (mEnhance == null) {
            Slog.e(TAG, "setAutoValue  failed mEnhance = null  value=" + value);
            return;
        }
        try {
            Slog.e(TAG, "setValue value=" + value);
            mEnhance.setValue(Type.CMS, value);
        } catch (RemoteException e) {
            Slog.e(TAG, "setAutoValue   failed " + e);
        }
    }

    void startUserConfig(int userId){
        //first start Color Mode
        if(mCurrentId == UserHandle.USER_NULL) {
            switchUserConfig(userId);
        }
    }

    void switchUserConfig(int userId){
        //unregisterContentObserver for old user
        if(mCurrentId != UserHandle.USER_NULL) {
            mContentResolver.unregisterContentObserver(mColorModeObserver);
            mContentResolver.unregisterContentObserver(mAutoValueObserver);
        }

        mCurrentId = userId;
        if(mCurrentId != UserHandle.USER_NULL){
            //registerContentObserver for current user
            mContentResolver.registerContentObserver(
                    Settings.System.getUriFor(COLOR_MODE),
                    false, mColorModeObserver,mCurrentId);
            mContentResolver.registerContentObserver(
                    Settings.System.getUriFor(COLOR_MODE_AUTO_VALUE),
                    false, mAutoValueObserver,mCurrentId);

            //init Colormode and set auto mode value
            mColorMode = getColorMode();
            mAutoModeValue = getAutoValue();
            if(mColorMode != COLOR_MODE_OFF) {
                setColorMode(mColorMode);
            }
            if (mColorMode == COLOR_MODE_AUTO ) {
                setAutoValue(mAutoModeValue);
            }
        }
    }

    class ColorModeSettingObserver extends ContentObserver {
        public ColorModeSettingObserver() {
            super(new Handler());
        }
        public void onChange(boolean selfChange) {
            int mode = getColorMode();
            if (mColorMode != mode) {
                mColorMode = mode;
                setColorMode(mode);
                if (mColorMode == COLOR_MODE_AUTO) {
                    setAutoValue(mAutoModeValue);
                }
            }
        }
    }

    class AutoValueObserver extends ContentObserver {
        public AutoValueObserver() {
            super(new Handler());
        }
        public void onChange(boolean selfChange) {
            int value = getAutoValue();
            if (mAutoModeValue != value) {
                mAutoModeValue = value;
                if (mColorMode == COLOR_MODE_AUTO) {
                    setAutoValue(mAutoModeValue);
                }
            }
        }
    }
}
