/* Spreadtrum Communication Inc. 2016 */

package android.app;


/**
 * @hide
 */
public class AlarmManagerWrapper {

    /**
     * Alarm time in {@link System#currentTimeMillis System.currentTimeMillis()}
     * (wall clock time in UTC), which will wake up the device when it goes off.
     */
    public static final int POWER_OFF_WAKEUP = 4;

    /**
     * Alarm time in {@link System#currentTimeMillis System.currentTimeMillis()}
     * (wall clock time in UTC), which will boot the device when time is on.
     */
    public static final int POWER_ON_WAKEUP = 5;

    /**
     * Alarm time in {@link System#currentTimeMillis System.currentTimeMillis()}
     * (wall clock time in UTC), which will wake up the device and boot up with ro.bootmode=alarm
     */
    public static final int POWER_OFF_ALARM = 6;

    public static void cancelAlarm(AlarmManager am, PendingIntent op, IAlarmListener listener){
        am.removeExtraAlarm(op, listener);
    }
}
