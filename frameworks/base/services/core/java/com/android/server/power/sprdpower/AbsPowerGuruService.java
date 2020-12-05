package com.android.server.power.sprdpower;

import android.app.AlarmManager;
import android.app.sprdpower.IPowerGuru;
import android.app.PendingIntent;
import android.app.sprdpower.PowerGuruAlarmInfo;

import android.content.Context;
import android.os.WorkSource;

import java.util.List;

public abstract class AbsPowerGuruService extends IPowerGuru.Stub{

    public AbsPowerGuruService(Context context) {

    }

    public static boolean isEnabled() {
        return false;
    }

    public void testHello(){

    }

    public boolean notifyPowerguruAlarm(int type, long when,long whenElapsed, long windowLength,
           long maxWhen, long interval, PendingIntent operation) {
    return true;
    }

    /**
     * Heart beat app list, the app can be adjust when it in this list
     * @return heart beat app list
     */
    public List<PowerGuruAlarmInfo> getBeatList() {
    return null;
    }

    /**
     * white app list, the app can not adjust when it in this list
     * used by app
     * @return white app list
     */
    public List<String> getWhiteList() {
    return null;
    }

    /**
     * Delete app packageName from PowerGuru White app list
     * @param appname need to delete from white app list
     * @return true success, false failure
     */
    public boolean delWhiteAppfromList(String appname) {
    return false;
    }

    /**
     * Candicate white app list, return all thirdParty Apps in device
     * @return Candicate white app list
     */
    public List<String> getWhiteCandicateList() {
    return null;
    }

    /**
     * Add app packageName to PowerGuru white app list
     * @param appname need to add to white app list
     * @return true success, false failure
     */
    public boolean addWhiteAppfromList(String appname) {
    return false;
    }

    /**
     * Notify of an alarm to be set at the stated time by the stated owner
     *
     * <p>
     * This method is called from AlarmManagerService when an alarm is set by an app.
     *
     * @param type One of {@link #ELAPSED_REALTIME}, {@link #ELAPSED_REALTIME_WAKEUP},
     *        {@link #RTC}, or {@link #RTC_WAKEUP}.
     * @param when time in milliseconds that the alarm should go
     *        off, using the appropriate clock (depending on the alarm type).
     * @param whenElapsed time in milliseconds that the alarm should go
     *        off, using the elapsed time base, that is 'when' in the elapsed time base
     * @param windowLength The length of the requested delivery window,
     *        in milliseconds.  The alarm will be delivered no later than this many
     *        milliseconds after {@code when}.  Note that this parameter
     *        is a <i>duration,</i> not the timestamp of the end of the window.
     * @param maxWhen time in milliseconds that the alarm will be delivered no later than this many
     *        milliseconds, using the elapsed time base.
     * @param interval in milliseconds between subsequent repeats
     *        of the alarm.
     * @param operation Action to perform when the alarm goes off;
     *        typically comes from {@link PendingIntent#getBroadcast
     *        IntentSender.getBroadcast()}.
     * @param flags for the alarm
     * @param workSource for the alarm
     * @param alarmClock for the alarm, indicate this for clock alarm
     * @param uid of the owner app of the alarm
     * @param pkgName of the owner app of the alarm
     */
    public boolean notifyAlarm(int type, long when,long whenElapsed, long windowLength,
            long maxWhen,long interval, PendingIntent operation, int flags,
            WorkSource workSource, AlarmManager.AlarmClockInfo alarmClock,
            int uid, String pkgName) {
    return false;
    }

    /**
     * Add a app (name) to the constrained list
     * Note: This constrained list should separate from the orignal Black list.
     *     This constrained list is used by system. And it may dymatic changed as running
     * @param name The package name of the app
     * @return Returns true for sucess.Return false for fail
     */
    public boolean addConstrainedListAppBySystem(String name) {
    return false;
    }

    /**
     * Remove a app (name) from constrained list
     * Note: This constrained list should separate from the orignal Black list.
     *     This constrained list is used by system. And it may dymatic changed as running
     * @param name The package name of the app
     * @return Returns true for sucess.Return false for fail
     */
    public boolean removeConstrainedListAppBySystem(String name) {
    return false;
    }

    /**
     * Get the Orignal Alarm Align interval
     * @return Returns the original alarm align interval (in minutes)
     */
    public int getOrignalAlignInterval() {
    return 0;
    }

    /**
     * Set the Alarm Align interval
     * @param interval The time interval in minutes.
     * @return Returns true for sucess.Return false for fail
     */
    public boolean setAlignInterval(int interval) {
    return false;
    }

    /**
     * Mark the state of Doze is changed
     */
    public void notifyDozeStateChanged() {
    return;
    }

    /**
     * Notify of a wakeup alarm of the stated app is sent
     *
     * <p>
     * This method is called from AlarmManagerService when a wakeup alarm of the stated app is sent.
     *
     * @param sourcePkg package name of the owner app of the wakeup alarm
     * @param sourceUid uid of the owner app of the wakeup alarm
     */
    public void noteWakeupAlarm(String sourcePkg, int sourceUid) {
    return;
    }

    /*
     * get the total count of wake up alarm of the app since system boot up
     * @param name The package name of the app
     * @return Returns the count of wakeup alarm set for this app
     */
    public int getWakeupAlarmCount(String pkgName) {
    return 0;
    }
}
