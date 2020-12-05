/*
 * Copyright 2015 The Spreadtrum.com
 */

package android.app.sprdpower;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemProperties;
import android.os.RemoteException;
import android.os.WorkSource;
import android.util.Slog;
import java.util.ArrayList;
import java.util.List;

/**
 * @hide
 */
public class PowerGuru extends AbsPowerGuru{

    private static final String TAG = "PowerGuru";

    /*******members******/
    private final IPowerGuru mService;


    /**
     * package private on purpose
     */
    PowerGuru(IPowerGuru service, Context ctx) {
        super(service, ctx);
        mService = service;
    }

    /**
     * for test PowerGuru decoupling code
     */
    public void testHello() {
        try {
            mService.testHello();
        } catch (RemoteException ex) {
            loge("remote err:" + ex);
        }
    }

    /**
     * Entrance to judge the new alarm whether to adjust or not
     * for alarm manager
     * @param type One of ELAPSED_REALTIME, ELAPSED_REALTIME_WAKEUP, RTC, RTC_WAKEUP in AlarmManager
     * @param when time in milliseconds that the alarm should go on
     * @param whenElapsed 'when' in the elapsed time base
     * @param windowLength one of WINDOW_EXACT or WINDOW_HEURISTIC in AlarmManager
     * @param maxWhen also in the elapsed time base
     * @param interval interval in milliseconds between subsequent repeats of the alarm.
     * @param operation Action to perform when the alarm goes off;
     * @return true success, false failure
     */
    public boolean notifyPowerguruAlarm(int type, long when, long whenElapsed,
            long windowLength, long maxWhen, long interval, PendingIntent operation) {

        try {
            return mService.notifyPowerguruAlarm(type, when, whenElapsed,
                    windowLength, maxWhen, interval, operation);
        } catch (RemoteException ex) {
            loge("remote err:" + ex);
            return false; //return false while exception occurred
        }
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
    public boolean notifyAlarm(int type, long when, long whenElapsed,
            long windowLength, long maxWhen, long interval, PendingIntent operation,
            int flags, WorkSource workSource, AlarmManager.AlarmClockInfo alarmClock, int uid,
            String pkgName) {
        try {
            return mService.notifyAlarm(type, when, whenElapsed,
                    windowLength, maxWhen, interval, operation,
                    flags, workSource, alarmClock, uid, pkgName);
        } catch (RemoteException ex) {
            loge("remote err:" + ex);
            return false; //return false while exception occurred
        }
    }

    /**
     * Heart beat app list, the app can be adjust when it in this list
     * @return heart beat app list
     */
    public List<PowerGuruAlarmInfo> getBeatList() {
        try {
            return mService.getBeatList();
        } catch (RemoteException ex) {
            loge("remote err:" + ex);
            return null;
        }
    }

    /**
     * white app list, the app can not adjust when it in this list
     * used by app
     * @return white app list
     */
    public List<String> getWhiteList() {
        try {
            return mService.getWhiteList();
        } catch (RemoteException ex) {
            loge("remote err:" + ex);
            return null;
        }
    }

    /**
     * Delete app packageName from PowerGuru White app list
     * @param appname need to delete from white app list
     * @return true success, false failure
     */
    public boolean delWhiteAppfromList(String appname) {
        try {
            return mService.delWhiteAppfromList(appname);
        } catch (RemoteException ex) {
            loge("remote err:" + ex);
            return false;
        }
    }

    /**
     * Candicate white app list, return all thirdParty Apps in device
     * @return Candicate white app list
     */
    public List<String> getWhiteCandicateList() {
        try {
            return mService.getWhiteCandicateList();
        } catch (RemoteException ex) {
            loge("remote err:" + ex);
            return null;
        }
    }

    /**
     * Add app packageName to PowerGuru white app list
     * @param appname need to add to white app list
     * @return true success, false failure
     */
    public boolean addWhiteAppfromList(String appname) {
        try {
            return mService.addWhiteAppfromList(appname);
        } catch (RemoteException ex) {
            loge("remote err:" + ex);
            return false;
        }
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
        try {
            mService.noteWakeupAlarm(sourcePkg, sourceUid);
        } catch (RemoteException ex) {
            loge("remote err:" + ex);
        }
    }

    /**
     * Slog error log
     * @param info log message
     */
    private void loge(String info) {
        Slog.e(TAG, info);
    }

    /**
     * Slog debug log
     * @param info log message
     */
    private void logd(String info) {
        Slog.d(TAG, info);
    }
}
