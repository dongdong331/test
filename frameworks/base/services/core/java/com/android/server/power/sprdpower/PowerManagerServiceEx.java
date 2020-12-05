package com.android.server.power.sprdpower;

import android.app.PerformanceManagerInternal;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.provider.Settings;
import android.os.sprdpower.AppPowerSaveConfig;
import android.os.sprdpower.IPowerManagerEx;
import android.os.ServiceManager;
import android.os.PowerManager;
import android.os.WorkSource;
import android.util.Log;

//import com.android.server.display.DisplayManagerServiceEx;
import com.android.server.performance.PerformanceManagerService;
import com.android.server.power.ShutdownThread;
import com.android.server.LocalServices;

import java.util.List;
import java.util.ArrayList;


public class PowerManagerServiceEx extends IPowerManagerEx.Stub{

    private static final String TAG = "PowerManagerServiceEx";

    private Context mContext;
    private static Context mUIContext;

    private static PowerManagerServiceEx sInstance;

    //private DisplayManagerServiceEx mDisplayManagerServiceEx;

    private boolean mBootCompleted;

    // NOTE: Bug 707103 whether open the funtion that sending user activity broadcast after press physical button
    private boolean mUserActivityEventNeeded = false;

    private PerformanceManagerService mPerformance;

    // varibles for powercontroller
    final PowerController.LocalService mPowerControllerInternal;

    public static PowerManagerServiceEx getInstance(Context context){

        synchronized(PowerManagerServiceEx.class){
            if (sInstance == null ){
                sInstance = new PowerManagerServiceEx(context);
            }
        }

        return sInstance;
    }

    public static PowerManagerServiceEx init(Context context, Context uiContext){

        synchronized(PowerManagerServiceEx.class){
            if (sInstance == null ){
                mUIContext = uiContext;
                sInstance = new PowerManagerServiceEx(context);
            }else {
                Log.wtf(TAG,"PowerManagerServiceEx has been init more than once");
            }
        }

        return sInstance;
    }

    private PowerManagerServiceEx(Context context){

        mContext = context;
        //mDisplayManagerServiceEx = DisplayManagerServiceEx.getInstance(context);
        mPowerControllerInternal = LocalServices.getService(PowerController.LocalService.class);

        ServiceManager.addService("power_ex",this);

    }

    public void setBootCompleted(boolean bootCompleted){
        mBootCompleted = bootCompleted;
    }

    public void shutdownForAlarm(boolean confirm, boolean isPowerOffAlarm){

        ShutdownThread.shutdownForAlarm(mUIContext, confirm, isPowerOffAlarm);

    }

    public void rebootAnimation(){

        ShutdownThread.rebootAnimation(mContext);

    }

    public void scheduleButtonLightTimeout(long now){
        Log.v(TAG, "scheduleButtonTimeout");
       /* if(mDisplayManagerServiceEx != null){
        int buttonOffTimeoutSetting = PowerManagerServiceUtils.getInstance(mContext).getButtonOffTimeoutSetting();
        Log.d(TAG, "ButtonOffTimeoutSetting = " + buttonOffTimeoutSetting);
        mDisplayManagerServiceEx.updateButtonTimeout(buttonOffTimeoutSetting);
        if (mBootCompleted) {
           mDisplayManagerServiceEx.scheduleButtonTimeout(now);
          }
        }*/
    }

// NOTE: Bug 707103 sending user activity broadcast after press physical button BEG-->
    /**
     * Bug 707103
     * This method must only be called by the CatServiceSprd
     * flag:control whether open the funtion that sending user activity broadcast after press physical button
     */
    public void setEventUserActivityNeeded(boolean bEventNeeded){
        mUserActivityEventNeeded = bEventNeeded;
        Log.d(TAG, "mUserActivityEventNeeded = " + mUserActivityEventNeeded);
    }

    /**
     * Bug 707103
     * This method must only be called by the CatServiceSprd
     * flag:sending user activity broadcast after press physical button
     */
    public void notifyStkUserActivity() {
        if (mBootCompleted && mUserActivityEventNeeded) {
            Log.d(TAG, "notify Stk user activity from PowerManagerService");
            Intent intent = new Intent("com.sprd.action.stk.user_activity");
            intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            mContext.sendBroadcast(intent, "android.permission.RECEIVE_STK_COMMANDS");
        }
    }
// <-- NOTE: Bug 707103 sending user activity broadcast after press physical button END

// NOTE: Bug #627645 low power Feature BEG-->
    /**
     * get the current power save mode
     * @return Returns the current power save mode.
     */
    public int getPowerSaveMode() {
        return mPowerControllerInternal.getPowerSaveMode();
    }

    /**
     * get the previous power save mode
     * @return Returns the previous power save mode.
     */
    public int getPrePowerSaveMode() {
        return mPowerControllerInternal.getPrePowerSaveMode();
    }

    /**
     * set the current power save mode
     * @param mode link {@PowerManagerEx#MODE_LOWPOWER, #MODE_SMART, #MODE_ULTRASAVING}
     * @return Returns true for success, false for fail.
     */
    public boolean setPowerSaveMode(int mode) {
        return mPowerControllerInternal.setPowerSaveMode(mode);
    }

    /**
     * fore the current power save mode to be in low-power mode
     * @param mode link {@PowerManagerEx#MODE_LOWPOWER, #MODE_SMART, #MODE_ULTRASAVING}
     * @return Returns true for success, false for fail.
     */
    public boolean forcePowerSaveMode(boolean mode) {
        return mPowerControllerInternal.forcePowerSaveMode(mode);
    }

    /**
     * Get the current power save config of the app
     * @param appName The package name of the app
     * @return Returns the AppPowerSaveConfig of the app.
     */
    public AppPowerSaveConfig getAppPowerSaveConfig(String appName) {
        return mPowerControllerInternal.getAppPowerSaveConfig(appName);
    }

    /**
     * Get the current power save config value of the app in the specified type
     * @param appName The package name of the app
     * @param type The power save type link {@AppPowerSaveConfig#ConfigType}
     * @return Returns the config value link
     *    {@AppPowerSaveConfig#VALUE_INVALID, #VALUE_AUTO, #VALUE_OPTIMIZE, #VALUE_NO_OPTIMIZE}
     */
    public int getAppPowerSaveConfigWithType(String appName, int type) {
        return mPowerControllerInternal.getAppPowerSaveConfigWithType(appName, type);
    }

    /**
     * Set the current power save config of the app
     * @param appName The package name of the app
     * @param config The power save config
     * @return Returns true for success, false for fail.
     */
    public boolean setAppPowerSaveConfig(String appName, AppPowerSaveConfig config) {
        return mPowerControllerInternal.setAppPowerSaveConfig(appName, config);
    }

    /**
     * Set the current power save config value of the app in the specified type
     * @param appName The package name of the app
     * @param type The power save type link {@AppPowerSaveConfig#ConfigType}
     * @param value The config value link
     *    {@AppPowerSaveConfig#VALUE_INVALID, #VALUE_AUTO, #VALUE_OPTIMIZE, #VALUE_NO_OPTIMIZE}
     * @return Returns true for success, false for fail.
     */
    public boolean setAppPowerSaveConfigWithType(String appName, int type, int value) {
        return mPowerControllerInternal.setAppPowerSaveConfigWithType(appName, type, value);
    }

    /**
     * Set the current power save config value of the apps in 'appList' in the specified type
     * @param appList The package name list of the apps
     * @param type The power save type link {@AppPowerSaveConfig#ConfigType}
     * @param value The config value link
     *    {@AppPowerSaveConfig#VALUE_INVALID, #VALUE_AUTO, #VALUE_OPTIMIZE, #VALUE_NO_OPTIMIZE}
     * @return Returns true for success, false for fail.
     */
    public boolean setAppPowerSaveConfigListWithType(List<String> appList, int type, int value) {
        return mPowerControllerInternal.setAppPowerSaveConfigListWithType(appList, type, value);
    }

    /**
     * Get the number of apps that have the specified power save config value in the specified type
     * @param type The power save type link {@AppPowerSaveConfig#ConfigType}
     * @param value The config value link
     *    {@AppPowerSaveConfig#VALUE_INVALID, #VALUE_AUTO, #VALUE_OPTIMIZE, #VALUE_NO_OPTIMIZE}
     * @return Returns the number of apps.
     */
    public int getAppNumWithSpecificConfig(int type, int value) {
        return mPowerControllerInternal.getAppNumWithSpecificConfig(type, value);
    }


    /**
     * get the target power save mode set in auto low-power setting
     * @return Returns the power save mode  link {@PowerManagerEx#MODE_LOWPOWER, #MODE_ULTRASAVING}
     */
    public int getAutoLowPower_Mode() {
        return mPowerControllerInternal.getAutoLowPower_Mode();
    }

    /**
     * set the target power save mode of auto low-power setting
     * @param mode The power save mode  link {@PowerManagerEx#MODE_LOWPOWER, #MODE_ULTRASAVING}
     */
    public boolean setAutoLowPower_Mode(int mode) {
        return mPowerControllerInternal.setAutoLowPower_Mode(mode);
    }

    /**
     * if auto low-power setting is enabled
     * @return Returns ture for yes, false for no
     */
    public boolean getAutoLowPower_Enable() {
        return mPowerControllerInternal.getAutoLowPower_Enable();
    }

    /**
     * set auto low-power setting is enabled or not
     * @param enable ture for enabled, false for disabled
     */
    public boolean setAutoLowPower_Enable(boolean enable) {
        return mPowerControllerInternal.setAutoLowPower_Enable(enable);
    }

    /**
     * get the battery level value, that when battery
     * level is below this value, the auto low-power mode will be set
     * @return Return battery level value
     */
    public int getAutoLowPower_BattValue() {
        return mPowerControllerInternal.getAutoLowPower_BattValue();
    }

    /**
     * Set the battery level value, that when battery
     * level is below this value, the auto low-power mode will be set
     * @param battValue The battery level value
     */
    public boolean setAutoLowPower_BattValue(int battValue) {
        return mPowerControllerInternal.setAutoLowPower_BattValue(battValue);
    }

    /**
     * get the boolean value of exit auto Low-power mode when plug in
     * @return Return true, exit auto low-power mode when plug in
     */
    public boolean getAutoLowPower_ExitWithPower() {
        return mPowerControllerInternal.getAutoLowPower_ExitWithPower();
    }


    /**
     * set the boolean value of exit auto Low-power mode when plug in
     * @param bExit if true, exit auto low-power mode when plug in
     */
    public boolean setAutoLowPower_ExitWithPower(boolean bExit) {
        return mPowerControllerInternal.setAutoLowPower_ExitWithPower(bExit);
    }

    /**
     * get the boolean value of exit power save mode when plug in
     * @return Return true, switch smart power save mode from other
     * power save mode when plug in
     */
    public boolean getSmartSavingModeWhenCharging() {
        Log.d(TAG, "get Power Save Mode When Charging");
        return mPowerControllerInternal.getSmartSavingModeWhenCharging();
    }

    /**
     * set the boolean value of exit power save mode when plug in
     * @param bExit if true, switch smart power save mode from other
     * power save mode when plug in
     */
    public boolean setSmartSavingModeWhenCharging(boolean bExit) {
        Log.d(TAG, "setting Power Save Mode to be Smart Save Mode When Charging");
        return mPowerControllerInternal.setSmartSavingModeWhenCharging(bExit);
    }

    /**
     * get the boolean value of schedule power mode setting
     * @return Return true, schedule power mode setting is enabled
     */
    public boolean getSchedule_Enable() {
        return mPowerControllerInternal.getSchedule_Enable();
    }

    /**
     * set the boolean value of schedule power mode setting
     * @param enable If true, schedule power mode setting is enabled
     */
    public boolean setSchedule_Enable(boolean enable) {
        return mPowerControllerInternal.setSchedule_Enable(enable);
    }

    /**
     * get the target power save mode of schedule power mode setting
     * @return Return power save mode
     */
    public int getSchedule_Mode() {
        return mPowerControllerInternal.getSchedule_Mode();
    }

    /**
     * set the target power save mode of schedule power mode setting
     * @param mode The target power save mode
     * @return Returns true for success, false for fail.
     */
    public boolean setSchedule_Mode(int mode) {
        return mPowerControllerInternal.setSchedule_Mode(mode);
    }

    /**
     * get the start time of schedule power mode setting
     * @return
     */
    public int getSchedulePowerMode_StartTime() {
        return mPowerControllerInternal.getSchedulePowerMode_StartTime();
    }

    /**
     * set the start time of schedule power mode setting
     * @return Returns true for success, false for fail.
     */
    public boolean setSchedulePowerMode_StartTime(int hour, int minute) {
        return mPowerControllerInternal.setSchedulePowerMode_StartTime(hour, minute);
    }

    /**
     * get the end time of schedule power mode setting
     * @return
     */
    public int getSchedulePowerMode_EndTime() {
        return mPowerControllerInternal.getSchedulePowerMode_EndTime();
    }

    /**
     * set the end time of schedule power mode setting
     * @return Returns true for success, false for fail.
     */
    public boolean setSchedulePowerMode_EndTime(int hour, int minute) {
        return mPowerControllerInternal.setSchedulePowerMode_EndTime(hour, minute);
    }

    /**
     * add a app that will be allowed to run in Ultra saving mode
     * @componentNameStr the string get from component
     * @return Returns true for success, false for fail.
     */
    public boolean addAllowedAppInUltraSavingMode(String componentNameStr) {
        return mPowerControllerInternal.addAllowedAppInUltraSavingMode(componentNameStr);
    }

    /**
     * del a app that will be allowed to run in Ultra saving mode
     * @componentNameStr the string get from component
     * @return Returns true for success, false for fail.
     */
    public boolean delAllowedAppInUltraSavingMode(String componentNameStr) {
        return mPowerControllerInternal.delAllowedAppInUltraSavingMode(componentNameStr);
    }

    /**
     * get the app list that will be allowed to run in Ultra saving mode
     * @return Returns app list (app name is the string get from component).
     */
    public List<String> getAllowedAppListInUltraSavingMode() {
        return mPowerControllerInternal.getAllowedAppListInUltraSavingMode();
    }

// <-- NOTE: Bug #627645 low power Feature END

    // NOTE: 624590 send wakelock info to AMS BEG-->

    /**
     * Bug624590
     * update wake lock status when acquire or release a wake lock
     * @param opt acquire(OPT_ADD) or release(OPT_RELEASE) a wake lock
     * @param lockUid the owner UID
     * @param flags the wake lock flags
     * @param workSource the workSource
     */
    public void updateWakeLockStatusLocked(int opt, int lockUid, int flags, WorkSource workSource) {
        try {
            if(mPerformance == null){
                mPerformance = (PerformanceManagerService) PerformanceManagerInternal.getDefault().getService();
            }
            if((flags & PowerManager.PARTIAL_WAKE_LOCK) != 0) {
                ArrayList<Integer>uids = new ArrayList<>();
                if(workSource != null && workSource.size() != 0) {
                    for(int i = 0; i < workSource.size(); i++) {
                        int uid = workSource.get(i);
                        if(uid >= 10000) {
                            uids.add(uid);
                        }
                    }
                } else if(lockUid >= 10000) {
                    uids.add(lockUid);
                }
                mPerformance.updateWakeLockStatus(opt, uids);
            }
        } catch (Exception e) {}
    }

    /**
     * Bug624590
     * update wake lock status when the workSource changing
     * @param flags the wake lock flags
     * @param from the old WorkSource
     * @param to the new WorkSource
     */
    public void updateWakeLockStatusLockedChanging(int flags, WorkSource from, WorkSource to) {
        try {
            if(mPerformance == null){
                mPerformance = (PerformanceManagerService) PerformanceManagerInternal.getDefault().getService();
            }
            if((flags & PowerManager.PARTIAL_WAKE_LOCK) != 0) {
                ArrayList<Integer>fromUids = new ArrayList<>();
                ArrayList<Integer>toUids = new ArrayList<>();
                if(from != null && from.size() != 0) {
                    for(int i = 0; i < from.size(); i++) {
                        int uid = from.get(i);
                        if(uid >= 10000) {
                            fromUids.add(uid);
                        }
                    }
                }
                if(to != null && to.size() != 0) {
                    for(int i = 0; i < to.size(); i++) {
                        int uid = to.get(i);
                        if(uid >= 10000) {
                            toUids.add(uid);
                        }
                    }
                }
                mPerformance.updateWakeLockStatusChanging(fromUids, toUids);
            }
        } catch (Exception e) {}
    }
    // <-- NOTE: Bug 624590 send wakelock info to AMS END
}
