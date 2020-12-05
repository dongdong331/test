package com.android.server;

import android.util.Slog;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.app.sprdpower.PowerGuruAlarmInfo;
import android.app.sprdpower.AbsPowerGuru;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.WorkSource;
import java.util.List;

public class AlarmAlignHelper {
    private static final String TAG = "AlarmManagerHelper";
    private boolean mAlignEnable;
    private int mAlignLength;
    private AbsPowerGuru mPowerGuruService;
    private List<PowerGuruAlarmInfo> mBeatlist;
    protected AlarmManagerService mAlarmManager;
    protected boolean DEBUG = false || ("1".equals(SystemProperties.get("persist.sys.alarm.debug")));
    public AlarmAlignHelper(Context context) {}

    protected void updateBeatlist(){}

    protected boolean getAlignEnable(){
      return false;
    }

    protected void setAlignEnable(boolean enable){
      mAlignEnable = false;
    }

    public void setAlarmManager(AlarmManagerService alarmManager){
      mAlarmManager = alarmManager;
    }

    protected boolean isEnabled(){
      return false;
    }

    protected boolean checkAlignEnable(int type, final PowerGuruAlarmInfo palarm){
      return false;
    }

    protected boolean isUnavailableGMS(final PowerGuruAlarmInfo guruAlarm){
      Slog.w(TAG, "isUnavailableGMS return false immediately for no-powerguru feature!!!");
      return false;
    }

    private int getAlignLength(){
      return mAlignLength;
    }

    protected long adjustTriggerTime(long whenElapsed, long maxWhenElapsed, int type){
      return -1;
    }

    protected long adjustTriggerTime(long triggerTime, int type){
      return 0;
    }

    private boolean isAlignPoint(long rtcTime, int length){
      return false;
    }

    private long adjustTriggerTimeInternal(long rtcTime, int alignLength){
      return 0;
    }

    protected PowerGuruAlarmInfo matchBeatListPackage(final PendingIntent pi){
      return null;
    }

    protected boolean notifyPowerGuru(int type, long when, long whenElapsed, long windowLength,long maxWhen, long interval, PendingIntent operation){
      Slog.w(TAG, "notifyPowerGuru() -> PowerGuruService is not running results from no heartbeat feature ???");
      return false;
    }
    protected boolean notifyPowerGuru(int type, long when, long whenElapsed, long windowLength,
              long maxWhen, long interval, PendingIntent operation,
              int flags, WorkSource workSource, AlarmManager.AlarmClockInfo alarmClock,int uid, String pkgName){
      Slog.w(TAG, "notifyPowerGuru() -> PowerGuruService is not running results from no heartbeat feature ???");
      return false;
    }

    protected boolean noteWakeupAlarm(int uid, String pkgName){
      Slog.w(TAG, "notifyPowerGuru() -> PowerGuruService is not running results from no heartbeat feature ???");
      return false;
    }

    public boolean setHeartBeatAdjustEnable(boolean enable, boolean updated) {
      try{
          Slog.d(TAG, "setHeartBeatAdjustEnable() enable = "+enable+ ", updated = "+updated);
          if(updated)
              updateBeatlist();
          setAlignEnable(enable);
          if(mAlarmManager != null)
              mAlarmManager.rebatchAllAlarms();
      }catch(Exception ex){
          Slog.d(TAG, "setHeartBeatAdjustEnable occurs Exception!!", ex);
          return false;
      }
      return true;
    }

    public boolean setAlignLength(int length){
      Slog.d(TAG, "mAlighHelper setAlighLenth to " + length);
      if(mAlarmManager != null)
          mAlarmManager.rebatchAllAlarms();
      return true;
    }

    /**
     * check and adjust the trigger time according to the powerguru heart beat list and the alarm trigger time
     **/
    public long checkAndAdjustAlarm(int type, long triggerAtTime, long triggerElapsed, long maxElapsed, PendingIntent operation, int flags, boolean tiggerTimeAdjusted) {
        long alignTime =0l;
        // Here start to align alarm. It just adjust the 3rd-party alarm without any flag.
        // [2017-11-28] don't care about the alarm from app that is whitelisted for doze.
        // [2017-11-28] This whitelisted will be handled in PowerGuruService
        if ((flags&(/*AlarmManager.FLAG_ALLOW_WHILE_IDLE_UNRESTRICTED
                |*/ AlarmManager.FLAG_WAKE_FROM_IDLE
                | AlarmManager.FLAG_IDLE_UNTIL)) == 0){

            final PowerGuruAlarmInfo guruAlarm = matchBeatListPackage(operation);
            if(isUnavailableGMS(guruAlarm)){
                return alignTime;
            }
            if(isEnabled() && checkAlignEnable(type, guruAlarm)){
                alignTime = adjustTriggerTime(triggerAtTime, type);
                if(alignTime > SystemClock.elapsedRealtime()){
                    // do not use the orignal window length for adjusted alarm, set the window length to be 5ms
                    // becuase:
                    // (1) the purpose of adjusting alarm is to align the wake up in the align time
                    // (2) if not limit the window length, then the below case will hanppen
                    //       A wake up alarm A is adjust to  10:50:00 with orignal window length 0
                    //       A wake up alarm B is adjust to  10:50:00 with orignal window length to be 5 mins
                    //       A non-wake up alarm C is set to 10:52:12 with orignal window length to be 5mins
                    //       Then this will form 2 batchs: batch1 { start: 10:50:00, end:10:50:00}; batch2 { start: 10:52:12, end:10:55:00}
                    //       A this will wake up 2times: first in 10:50:00, second in 10:52:12
                    // modify for  Bug #627645
                    //alarm.maxWhenElapsed = 5; //alignTime + (alarm.maxWhenElapsed - alarm.whenElapsed);
                    //alarm.whenElapsed = alignTime;
                    if(DEBUG) {
                    Slog.d(TAG, " alarm type = "+ type
                          + ", operation = "+ operation + ", triggerAtTime =" + triggerAtTime
                          + ", triggerElapsed =" + triggerElapsed + ", maxElapsed =" + maxElapsed
                          + ", alignTime = " + alignTime
                          + ", guruAlarm" + guruAlarm
                          + ", is adjusted for PowerGuru!!!!");
                    }
                    return alignTime;
                }/*else{
                    Slog.w(TAG, "the adjusted time is in the past, ignore it -- alarm type = "+ type
                          + ", operation = "+ operation + ", triggerAtTime =" + triggerAtTime
                          + ", triggerElapsed =" + triggerElapsed + ", maxElapsed =" + maxElapsed);
                }*/
            }
        }
        // NOTE: Bug #627645 low power Feature BEG -->
        // when Alarm Align is started, then check if this alarm can be align to specified INTERVAL
        // Because when Alarm Align is started, if alarms that is not in align list can be align to
        // the specified INTERVAL (consider the whenElapsed and maxWhenElapsed). This will
        // make the rtc wake up times less.
        // for example: if Alarm Align length is 10 min, then a system alarm is set in 6min,but its maxWhenElapsed
        // allow it to go off in align of 5min, then this will make the alarm wake up times less
        if ((flags&(AlarmManager.FLAG_WAKE_FROM_IDLE
                | AlarmManager.FLAG_IDLE_UNTIL)) == 0) {
            try{
                if(isEnabled() && getAlignEnable()
                        && !tiggerTimeAdjusted){
                    alignTime = adjustTriggerTime(triggerElapsed, maxElapsed, type);
                    if(alignTime > SystemClock.elapsedRealtime()){
                        if(DEBUG) {
                        Slog.d(TAG, " alarm type = "+ type
                            + ", operation = "+ operation + ", triggerAtTime =" + triggerAtTime
                            + ", triggerElapsed =" + triggerElapsed + ", maxElapsed =" + maxElapsed
                            + ", alignTime = " + alignTime
                            + ", is adjusted!!");
                        }
                        return alignTime;
                    }/*else{
                        Slog.w(TAG, "can not adjust this alarm, ignore it -- alarm type = "+ type
                            + ", operation = "+ operation + ", triggerAtTime =" + triggerAtTime
                            + ", triggerElapsed =" + triggerElapsed + ", maxElapsed =" + maxElapsed);
                    }*/
                }
            }catch(Exception e){
                Slog.w(TAG, "LSL here is some Exception!!", e);
            }
        }
        return triggerElapsed;
    }
}
