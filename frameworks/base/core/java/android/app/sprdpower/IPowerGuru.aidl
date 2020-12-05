/* //device/java/android/android/app/IPowerGuru.aidl
**
** Copyright 2015, The Spreadtrum.com
*/
package android.app.sprdpower;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.sprdpower.PowerGuruAlarmInfo;
import android.os.WorkSource;

/**
 * System private API for talking with the power guru service.
 *
 * @hide
 */
interface IPowerGuru {

    void testHello();

    //for alarmmanager
    boolean notifyPowerguruAlarm(int type, long when,long whenElapsed, long windowLength,
            long maxWhen,long interval, in PendingIntent operation);
    boolean notifyAlarm(int type, long when,long whenElapsed, long windowLength,
            long maxWhen,long interval, in PendingIntent operation, int flags,
            in WorkSource workSource, in AlarmManager.AlarmClockInfo alarmClock,
            int uid, String pkgName);
    List<PowerGuruAlarmInfo> getBeatList();

    //for app
    List<String> getWhiteList();
    boolean delWhiteAppfromList(String appname);
    List<String> getWhiteCandicateList();
    boolean addWhiteAppfromList(String appname);

    //for Internal used
    boolean addConstrainedListAppBySystem(String name);
    boolean removeConstrainedListAppBySystem(String name);
    int getOrignalAlignInterval();
    boolean setAlignInterval(int interval);
    void notifyDozeStateChanged();

    void noteWakeupAlarm(String sourcePkg, int sourceUid);
    int getWakeupAlarmCount(String pkgName);
}
