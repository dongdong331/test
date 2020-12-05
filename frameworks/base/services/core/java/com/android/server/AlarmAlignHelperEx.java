package com.android.server;

import android.os.SystemProperties;
import static android.app.AlarmManager.RTC_WAKEUP;
import static android.app.AlarmManager.RTC;
import static android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP;
import static android.app.AlarmManager.ELAPSED_REALTIME;

import android.os.Binder;
import android.os.SystemClock;
import android.util.Slog;
import android.app.AlarmManager;
import android.app.sprdpower.PowerGuruAlarmInfo;
import android.app.sprdpower.AbsPowerGuru;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.WorkSource;
import java.util.Calendar;
import java.util.List;

final class AlarmAlignHelperEx extends AlarmAlignHelper{

    private static final String ALIGN_LENGTH = "persist.sys.align_length";
    private static final String ALIGN_ENABLE = "persist.sys.heartbeat.enable";
    private static final int DEFAULT_LENGTH = 5;
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 60;

    // The align length is best to be the multiple times of DEFAULT_ALIGN_LENGTH
    private static final int DEFAULT_ALIGN_LENGTH = 5;

    private static final long DEFAULT_MIN_FUTURITY = 5 * 1000;

    private static final String TAG = "AlarmManagerHelperEx";
    private boolean mAlignEnable;
    private int mAlignLength;
    private AbsPowerGuru mPowerGuruService;
    private AlarmManagerService mAlarmManager;
    private List<PowerGuruAlarmInfo> mBeatlist;

    public AlarmAlignHelperEx(Context context) {
        super(context);
        mPowerGuruService = (AbsPowerGuru)context.getSystemService("powerguru");
        updateBeatlist();
        mAlignLength = DEFAULT_LENGTH;
    }

    protected void updateBeatlist(){
        if(mPowerGuruService != null){
            mBeatlist = mPowerGuruService.getBeatList();
        }else{
            Slog.w(TAG, "updateBeatlist() -> PowerGuruService is not running ???");
        }
    }

    protected boolean getAlignEnable(){
        return mAlignEnable;
    }

    protected void setAlignEnable(boolean enable){
        mAlignEnable = enable;
    }

    protected boolean isEnabled(){
        int temp = SystemProperties.getInt(ALIGN_ENABLE, 1);
        return temp == 1;
    }

    protected boolean checkAlignEnable(int type, final PowerGuruAlarmInfo palarm){
        boolean isWakeup = (type == RTC_WAKEUP || type == ELAPSED_REALTIME_WAKEUP);
        if(isWakeup && (palarm != null) && mAlignEnable){
            //Slog.d(TAG, "checkAlignEnable() return true. palarm = "+ palarm);
            return true;
        }
        return false;
    }
    protected boolean isUnavailableGMS(final PowerGuruAlarmInfo guruAlarm){
        if(guruAlarm != null && guruAlarm.isFromGMS && !guruAlarm.isAvailable){
            Slog.w(TAG, "setImplLocked() return, this alarm is from GMS and is not available!!!");
            return true;
        }
        return false;
    }

    private int getAlignLength(){
        /*
        int temp = SystemProperties.getInt(ALIGN_LENGTH, DEFAULT_LENTH);
        if (temp < MIN_LENGTH || temp > MAX_LENGTH){
            return DEFAULT_LENTH;
        }
        return temp;
        */
        return mAlignLength;
    }

    public boolean setAlignLength(int length){
        if(length < MIN_LENGTH || length > MAX_LENGTH || mAlignLength == length){
            return false;
        }
        Slog.d(TAG, "setAlignLength()  oldLength="+mAlignLength+", newLength=" + length);
        mAlignLength = length;
        super.setAlignLength(length);
        return true;
   }

    /**
     * Adjust the trigger Time according to whenElapsed and maxWhenElapsed
     * The Time Ajusted must between whenElapsed and maxWhenElapsed ,
     * that is in [whenElapsed, maxWhenElapsed]
     * Only for wake up alarm
     * return -1 for fail
     * return > 0 for success
     */
    protected long adjustTriggerTime(long whenElapsed, long maxWhenElapsed, int type){
        final int alignLength = DEFAULT_ALIGN_LENGTH;
        if ((type != RTC_WAKEUP && type != ELAPSED_REALTIME_WAKEUP)
            || (maxWhenElapsed <= whenElapsed) )
            return -1;

        long triggerTime = whenElapsed + (System.currentTimeMillis() - SystemClock.elapsedRealtime());

        // align to seconds
        long triggerTimeAlign = triggerTime - (triggerTime % 1000);

        long adjustedRtcTime = adjustTriggerTimeInternal(triggerTimeAlign, alignLength);
        long nowRTC = System.currentTimeMillis();
        if(adjustedRtcTime < nowRTC + DEFAULT_MIN_FUTURITY){
            Slog.w(TAG, "the triggerTime is adjuested to past, so it need adjusted to future time.");
            if(isAlignPoint(nowRTC, alignLength)){
                nowRTC += DEFAULT_MIN_FUTURITY; //make sure nowRTC can be aligned to future time.
            }
            // align to seconds
            nowRTC -= (nowRTC % 1000);
            adjustedRtcTime = adjustTriggerTimeInternal(nowRTC, alignLength);
        }

        final long adjuestedMillis = AlarmManagerService.convertToElapsed(adjustedRtcTime, 0);
        if (adjuestedMillis > maxWhenElapsed) return -1;

        return adjuestedMillis;
    }

    protected long adjustTriggerTime(long triggerTime, int type){
        final int alignLength = getAlignLength();
        if(type == ELAPSED_REALTIME_WAKEUP || type == ELAPSED_REALTIME){
            triggerTime += System.currentTimeMillis() - SystemClock.elapsedRealtime();
        }
        // align to seconds
        long triggerTimeAlign = triggerTime - (triggerTime % 1000);

        long adjustedRtcTime = adjustTriggerTimeInternal(triggerTimeAlign, alignLength);
        long nowRTC = System.currentTimeMillis();
        if(adjustedRtcTime < nowRTC + DEFAULT_MIN_FUTURITY){
            Slog.w(TAG, "the triggerTime is adjuested to past, so it need adjusted to future time.");
            if(isAlignPoint(nowRTC, alignLength)){
                nowRTC += DEFAULT_MIN_FUTURITY; //make sure nowRTC can be aligned to future time.
            }

            // align to seconds
            nowRTC -= (nowRTC % 1000);

            adjustedRtcTime = adjustTriggerTimeInternal(nowRTC, alignLength);
        }

        final long adjuestedMillis = AlarmManagerService.convertToElapsed(adjustedRtcTime, 0);
        return adjuestedMillis;
    }

    private boolean isAlignPoint(long rtcTime, int length){
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(rtcTime);
        int minutes = calendar.get(Calendar.MINUTE);
        int seconds = calendar.get(Calendar.SECOND);
        if(seconds == 0 && (minutes % length == 0)){
            return true;
        }
        return false;
    }

    private long adjustTriggerTimeInternal(long rtcTime, int alignLength){
        if(alignLength <=0){
            throw new IllegalStateException("The align length can not be less than 0 !");
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(rtcTime);
        int minute = calendar.get(Calendar.MINUTE);
        int seconds = calendar.get(Calendar.SECOND);
        int residue = minute / alignLength;
        if (residue == 0){// minute is less than alignLength
            if(minute != 0 || seconds != 0){
                calendar.set(Calendar.MINUTE, alignLength);
                calendar.set(Calendar.SECOND, 0);
            }
        }else{
            if(0 != (minute % alignLength) || 0 != seconds){
                int alignMinute = (residue+1) * alignLength;
                if (alignMinute >= 60){//This case means it should align to next hour.
                    calendar.add(Calendar.HOUR_OF_DAY, 1);
                    calendar.set(Calendar.MINUTE, 0);
                }else{
                    calendar.set(Calendar.MINUTE, alignMinute);
                }
                calendar.set(Calendar.SECOND, 0);
            }
        }
        return calendar.getTimeInMillis();
    }

    protected PowerGuruAlarmInfo matchBeatListPackage(final PendingIntent pi){

        // getIntent need android.permission.GET_INTENT_SENDER_INTENT
        // so clearCallingIdentity first
        long ident = Binder.clearCallingIdentity();
        try {
            if(pi != null && mBeatlist != null && mBeatlist.size() > 0){
                String pn = pi.getCreatorPackage();
                Intent in = pi.getIntent();
                String action = null;
                String component = null;
                if(in != null){
                    action = in.getAction();
                    if(in.getComponent() != null){
                        component = in.getComponent().getClassName();
                    }
                }
                for(PowerGuruAlarmInfo palarm : mBeatlist){
                    if(pn.equals(palarm.packageName) &&
                    ((action != null && action.equals(palarm.actionName)) || (action == null && palarm.actionName == null)) &&
                    ((component != null && component.equals(palarm.componentName)) || (component == null && palarm.componentName == null))
                    )
                    return palarm;
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        return null;
    }

    protected boolean notifyPowerGuru(int type, long when, long whenElapsed, long windowLength,
            long maxWhen, long interval, PendingIntent operation){
        if(mPowerGuruService != null){
            mPowerGuruService.notifyPowerguruAlarm(type, when, whenElapsed, windowLength, maxWhen, interval, operation);
        }else{
            return false;
        }
        return true;
    }

    protected boolean notifyPowerGuru(int type, long when, long whenElapsed, long windowLength,
            long maxWhen, long interval, PendingIntent operation,
            int flags, WorkSource workSource, AlarmManager.AlarmClockInfo alarmClock,int uid, String pkgName){
        if(mPowerGuruService != null){
            mPowerGuruService.notifyAlarm(type, when, whenElapsed, windowLength, maxWhen,
                interval, operation, flags, workSource, alarmClock, uid, pkgName);
        }else{
            return false;
        }
        return true;
    }

    protected boolean noteWakeupAlarm(int uid, String pkgName){
        if(mPowerGuruService != null){
            mPowerGuruService.noteWakeupAlarm(pkgName, uid);
        }else{
            return false;
        }
        return true;
    }

}
