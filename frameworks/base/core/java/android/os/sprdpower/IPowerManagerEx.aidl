package android.os.sprdpower;

import android.os.sprdpower.AppPowerSaveConfig;

/**
 * SPRD added for PowerOff Alarm
 * @hide
 */
interface IPowerManagerEx{

    void shutdownForAlarm(boolean confirm, boolean isPowerOffAlarm);
    void rebootAnimation();
    void scheduleButtonLightTimeout(long now);
    //Bug 707103 control whether open the funtion that sending user activity broadcast after press physical button
    void setEventUserActivityNeeded(boolean bEventNeeded);


// NOTE: Bug #627645 low power Feature BEG-->
    int getPowerSaveMode();
    int getPrePowerSaveMode();
    boolean setPowerSaveMode(int mode);
    boolean forcePowerSaveMode(boolean mode);

    int getAutoLowPower_Mode();
    boolean setAutoLowPower_Mode(int mode);
    boolean getAutoLowPower_Enable();
    boolean setAutoLowPower_Enable(boolean enable);
    int getAutoLowPower_BattValue();
    boolean setAutoLowPower_BattValue(int battValue);
    boolean getAutoLowPower_ExitWithPower();
    boolean setAutoLowPower_ExitWithPower(boolean bExit);
    boolean getSmartSavingModeWhenCharging();
    boolean setSmartSavingModeWhenCharging(boolean bExit); 

    boolean getSchedule_Enable();
    boolean setSchedule_Enable(boolean enable);
    int getSchedule_Mode();
    boolean setSchedule_Mode(int mode);
    int getSchedulePowerMode_StartTime();
    boolean setSchedulePowerMode_StartTime(int hour, int minute);
    int getSchedulePowerMode_EndTime();
    boolean setSchedulePowerMode_EndTime(int hour, int minute);

    AppPowerSaveConfig getAppPowerSaveConfig(String appName);
    int getAppPowerSaveConfigWithType(String appName, int type);
    boolean setAppPowerSaveConfig(String appName, in AppPowerSaveConfig config);
    boolean setAppPowerSaveConfigWithType(String appName, int type, int value);
    boolean setAppPowerSaveConfigListWithType(in List<String> appList, int type, int value);
    int getAppNumWithSpecificConfig(int type, int value);

    boolean addAllowedAppInUltraSavingMode(String componentNameStr);
    boolean delAllowedAppInUltraSavingMode(String componentNameStr);
    List<String> getAllowedAppListInUltraSavingMode();
// <-- NOTE: Bug #627645 low power Feature END
}
