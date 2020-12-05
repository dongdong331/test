package com.android.server.power.sprdpower;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.WorkSource;
import android.provider.Settings;
import android.util.Log;

public class PowerManagerServiceUtils extends AbsPowerManagerServiceUtils{

    private static final String TAG = "PowerManagerServiceUtils";
    private static PowerManagerServiceUtils sInstance;

    private Context mContext;
    private boolean mBootCompleted;

    // SPRD: Default mButtonOffTimeoutSetting 0.
    private int mButtonOffTimeoutSetting = 0;
    // SPRD: Default and minimum button light off timeout in milliseconds.
    private static final int DEFAULT_BUTTON_OFF_TIMEOUT = 1500;

    public static PowerManagerServiceUtils getInstance(Context context){

        synchronized(PowerManagerServiceUtils.class){
            if (sInstance == null ){
                sInstance = new PowerManagerServiceUtils(context);
            }
        }

        return sInstance;
    }

    private PowerManagerServiceUtils(Context context){
        mContext = context;
    }

    @Override
    public void registerButtonLightOffTimeOut(ContentResolver resolver, ContentObserver settingsObserver){
        // SPRD: Register for settings changes update BUTTON_LIGHT_OFF_TIMEOUT.
        resolver.registerContentObserver(Settings.System.getUriFor(
                        Settings.System.BUTTON_LIGHT_OFF_TIMEOUT),
                false, settingsObserver, UserHandle.USER_ALL);
    }

    @Override
    public void updateButtonLightOffTimeOut(ContentResolver resolver){
        final int buttonOffTimeoutSetting = Settings.System.getInt(resolver,
                Settings.System.BUTTON_LIGHT_OFF_TIMEOUT, DEFAULT_BUTTON_OFF_TIMEOUT);
        Log.d(TAG, "buttonOffTimeoutSetting = " + buttonOffTimeoutSetting +
                " mButtonOffTimeoutSetting = " + mButtonOffTimeoutSetting);
        if (buttonOffTimeoutSetting != mButtonOffTimeoutSetting) {
            mButtonOffTimeoutSetting = buttonOffTimeoutSetting;
            PowerManagerServiceEx.getInstance(mContext);
        }
    }

    @Override
    public int getButtonOffTimeoutSetting() {
        return mButtonOffTimeoutSetting;
    }

    @Override
    public void setBootCompleted(boolean bootCompleted) {
        PowerManagerServiceEx.getInstance(mContext).setBootCompleted(bootCompleted);
    }

    // NOTE: Bug 707103 sending user activity broadcast after press physical button BEG
    @Override
    public void notifyStkUserActivity() {
        PowerManagerServiceEx.getInstance(mContext).notifyStkUserActivity();
    }

    //Bug 624590 send wakelock info to AMS
    @Override
    public void updateWakeLockStatusLocked(int opt, int lockUid, int flags, WorkSource workSource) {
        PowerManagerServiceEx.getInstance(mContext).updateWakeLockStatusLocked(opt, lockUid, flags, workSource);
    }

    //Bug 624590 send wakelock info to AMS
    @Override
    public void updateWakeLockStatusLockedChanging(int flags, WorkSource from, WorkSource to) {
        PowerManagerServiceEx.getInstance(mContext).updateWakeLockStatusLockedChanging(flags, from, to);
    }
}

