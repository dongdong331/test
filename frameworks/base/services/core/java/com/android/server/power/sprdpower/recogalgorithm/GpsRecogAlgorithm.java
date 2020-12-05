/*
 ** Copyright 2016 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AppGlobals;
import android.app.PendingIntent;
import android.app.sprdpower.IPowerGuru;
import android.app.IActivityManager;
import android.app.INotificationManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.IUidObserver;
import android.app.usage.IUsageStatsManager;
import android.app.usage.UsageEvents.Event;
import android.app.usage.UsageStatsManagerInternal;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.ParceledListSlice;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IDeviceIdleController;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.service.notification.StatusBarNotification;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseIntArray;

import com.android.server.LocalServices;
import com.android.server.LocationManagerService;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

import android.os.sprdpower.Util;

public class GpsRecogAlgorithm extends RecogAlgorithmPlugin{

    static final String TAG = "PowerController.RecogA";

    private final boolean DEBUG = true;


    public static int BEHAVIOR_TYPE_MAYBE_1 = 1<<1;
    public static int BEHAVIOR_TYPE_MAYBE_2 = 1<<2;
    public static int BEHAVIOR_TYPE_MAYBE_3 = 1<<3;


    public static int BEHAVIOR_TYPE_1 = 0x10;
    public static int BEHAVIOR_TYPE_2 = 0x20;
    public static int BEHAVIOR_TYPE_3 = 0x30;


    public static int PENDING_FLAG_CHECK_TYPE1 = 1<<1;
    public static int PENDING_FLAG_CHECK_TYPE2 = 1<<2;
    public static int PENDING_FLAG_CHECK_TYPE3 = 1<<3;


    private boolean mScreenOn = true;
    private boolean mCharging = false;

    private long mLastTimeScreenStateChanged = 0;

    private boolean mStateChanged = false;

    private final PowerController.LocalService mPowerControllerInternal;

    private Handler mHandler;

    // Apps that request GPS at standby state
    private List<String> mRequestGpsAppList = new ArrayList<String>();
    private final Object mLock = new Object();


    private static long VOICE_NAVIGATION_TIMEOUT = (40*60*1000);

    public GpsRecogAlgorithm(Context context, int type, Handler handler) {
        super(context, type);

        mHandler = new InternalHandler(handler.getLooper());

        mPowerControllerInternal = LocalServices.getService(PowerController.LocalService.class);
    }

    public int reportEvent(RecogInfo appRecogInfo, int eventType, int data) {
        boolean changed = false;

        switch (eventType) {
        case RecogAlgorithm.EVENT_TYPE_FG_STATE:

            if (appRecogInfo.mProcState <= ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE
                && appRecogInfo.mGpsRecogInfo.mRequestGps
                && (Event.MOVE_TO_BACKGROUND == appRecogInfo.mFgEvent
                    || Event.MOVE_TO_FOREGROUND == appRecogInfo.mFgEvent)) {
                mRecogAlgorithm.updateNotificationDelayed(appRecogInfo.mPackageName, appRecogInfo.mUid, 3000);
            }

        break;

        case RecogAlgorithm.EVENT_TYPE_PROC_STATE:
        case RecogAlgorithm.EVENT_TYPE_AUDIO_STATE:
        break;

        case RecogAlgorithm.EVENT_TYPE_GPS_STATE:
            // a app may have more 1 location request
            // here need to get all the request gps app list
            boolean requestGps = ((data == 1) ? true : false);
            changed = updateGpsRequestState(appRecogInfo, requestGps);
        break;

        }

        return (changed ? mAlgorithmTypeFlag : 0);
    }

    public int reportDeviceState(int stateType, boolean stateOn) {
        boolean changed = false;

        switch (stateType) {
        case RecogAlgorithm.DEVICE_STATE_TYPE_SCREEN:
            if (mScreenOn != stateOn) {
                mScreenOn = stateOn;
                mLastTimeScreenStateChanged = SystemClock.elapsedRealtime();
                changed = true;
            }
        break;

        case RecogAlgorithm.DEVICE_STATE_TYPE_CHARGING:
            if (mCharging != stateOn) {
                mCharging = stateOn;
                changed = true;
            }
        break;
        }

        return (changed ? mAlgorithmTypeFlag : 0);
    }

    public boolean canConstraint(RecogInfo recogInfo) {
        return !recogInfo.mGpsRecogInfo.mAvoidConstraintGps;
    }



    //Message define
    static final int MSG_RECOGNIZE = 0;
    static final int MSG_NAVIGATION_TIMEOUT = 1;

    private class InternalHandler extends Handler {
        InternalHandler(Looper looper) {
            super(looper);
        }

        String Msg2Str(int msg) {
            final String msgStr[] = {"MSG_RECOGNIZE",
            };

            if ((0 <= msg) && (msg < msgStr.length))
                return msgStr[msg];
            else
                return "Unknown message: " + msg;
        }

        @Override public void handleMessage(Message msg) {
            if (DEBUG) Slog.d(TAG, "handleMessage(" + Msg2Str(msg.what) + ")");

            switch (msg.what) {
            case MSG_RECOGNIZE:
            case MSG_NAVIGATION_TIMEOUT:
                startRecognize((RecogInfo)msg.obj);
                break;
            }
        }
    }


    private void recognizeDelayed(RecogInfo appRecogInfo, long delayedMillsec) {
        mHandler.removeMessages(MSG_RECOGNIZE, appRecogInfo);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_RECOGNIZE, appRecogInfo), delayedMillsec);
    }

    private boolean updateGpsRequestState(RecogInfo appRecord, boolean request) {
        boolean changed = false;
        boolean requestGps = true;
        if (request) {

            appRecord.mGpsRecogInfo.mRequestGpsCount++;
        } else {

            appRecord.mGpsRecogInfo.mRequestGpsCount--;
            if (appRecord.mGpsRecogInfo.mRequestGpsCount <= 0) {
                appRecord.mGpsRecogInfo.mRequestGpsCount = 0;
                requestGps = false;
            }
        }

        if (appRecord.mGpsRecogInfo.mRequestGps != requestGps) {
            appRecord.mGpsRecogInfo.mRequestGps = requestGps;
            changed = true;
        }

        return changed;
    }

    private boolean behaviorTypeDecided(RecogInfo appRecord) {
        if ((appRecord.mGpsRecogInfo.mBehaviorType & 0xf0) != 0)
            return true;

        return false;
    }


    /**
     * TYPE1: when start Navigation, it behave as below: com.uu.uunavi
     *   (1) when screen off, it will have voice prompt
     *   (2) other case it has  not voice prompt
     *   (3) when turn into background, it has not Alert Notification
     *   when stop Navigation, in all case, it has not voice prompt
     *
     * TYPE2: when start Navigation, it behave as below: such as:com.mapbar.android.mapbarmap
     *  (1) when turn into background, it will have voice prompt
     *  (2) other case it has  not voice prompt
     *  (3) when turn into background, it has not Alert Notification
     *  when stop Navigation, in all case, it has not voice prompt, and has not Alert Notification
     *
     * TYPE3: when start Navigation, it behave as below: such as:com.tencent.map
     *  (1) when turn into background at first time, it will have voce prompt
     *  (2) other case it has  not voice prompt
     *  (3) when turn into background, it will have Alert Notification prompt
     *  (4) when turn into foreground, it will remove the Alert Notification prompt
     *  when stop Navigation, in all case, it has not voice prompt, and has not Alert Notification
     */
    private void startRecognizePreTypeDecided(RecogInfo appRecord) {

        // MAYBE TYPE1
        if (!mScreenOn
            && ((appRecord.mAudioState & RecogAlgorithm.AUDIO_TYPE_OUT) != 0)
            && (Event.USER_INTERACTION != appRecord.mFgEvent)
            && (timeInSpan(appRecord.mLastTimeAudioStateChanged, mLastTimeScreenStateChanged, 1000)
                || (appRecord.mLastTimeAudioStateChanged > mLastTimeScreenStateChanged))
            && appRecord.mGpsRecogInfo.mRequestGps
            ) {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " maybe 1 request GPS and has AUDIO OUT, it may be doing Navigation! Then avoid GPS constraint!!");
                appRecord.mGpsRecogInfo.mAvoidConstraintGps = true;
                if ((appRecord.mGpsRecogInfo.mBehaviorType & 0xf0) == 0)
                    appRecord.mGpsRecogInfo.mBehaviorType = (appRecord.mGpsRecogInfo.mBehaviorType|BEHAVIOR_TYPE_MAYBE_1);
        }


        // MAYBE TYPE2
        if (Event.MOVE_TO_BACKGROUND == appRecord.mFgEvent
            && ((appRecord.mAudioState & RecogAlgorithm.AUDIO_TYPE_OUT) != 0)
            && (timeInSpan(appRecord.mLastTimeAudioStateChanged, appRecord.mLastTimeFgEventChanged, 1000)
                || (appRecord.mLastTimeAudioStateChanged > appRecord.mLastTimeFgEventChanged))
            && appRecord.mGpsRecogInfo.mRequestGps
            ) {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " may be 2 request GPS and has AUDIO OUT, it may be doing Navigation! Then avoid GPS constraint!!");
                appRecord.mGpsRecogInfo.mAvoidConstraintGps = true;
                if ((appRecord.mGpsRecogInfo.mBehaviorType & 0xf0) == 0)
                    appRecord.mGpsRecogInfo.mBehaviorType = (appRecord.mGpsRecogInfo.mBehaviorType|BEHAVIOR_TYPE_MAYBE_2);
        }


        // MAYBE TYPE3
        if (Event.MOVE_TO_BACKGROUND == appRecord.mFgEvent
            && (appRecord.mGpsRecogInfo.mBehaviorType & BEHAVIOR_TYPE_MAYBE_2) != 0
            && (appRecord.mNotificationState & (RecogAlgorithm.NOTIFICATION_STATE_ALERT|RecogAlgorithm.NOTIFICATION_STATE_AUDIO_REPEAT)) != 0
            && (timeInSpan(appRecord.mLastTimeNotificationStateChanged, appRecord.mLastTimeFgEventChanged, 1000)
                || (appRecord.mLastTimeNotificationStateChanged > appRecord.mLastTimeFgEventChanged))
            && appRecord.mGpsRecogInfo.mRequestGps
            ) {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " maybe 3 request GPS and has AUDIO OUT, it may be doing Navigation! Then avoid GPS constraint!!");
                appRecord.mGpsRecogInfo.mAvoidConstraintGps = true;
                if ((appRecord.mGpsRecogInfo.mBehaviorType & 0xf0) == 0)
                    appRecord.mGpsRecogInfo.mBehaviorType = (appRecord.mGpsRecogInfo.mBehaviorType|BEHAVIOR_TYPE_MAYBE_3);
        }

        long now = SystemClock.elapsedRealtime();

        // clear pending flag
        if (!mScreenOn) appRecord.mGpsRecogInfo.mPendingFlag = 0;

        // TYPE 1
        if ((appRecord.mGpsRecogInfo.mBehaviorType & 0xf0) == 0
            && Event.MOVE_TO_BACKGROUND == appRecord.mFgEvent
            && (appRecord.mGpsRecogInfo.mBehaviorType & BEHAVIOR_TYPE_MAYBE_1) != 0
            && appRecord.mGpsRecogInfo.mAvoidConstraintGps
            && (appRecord.mNotificationState) == RecogAlgorithm.NOTIFICATION_STATE_NONE
            && (appRecord.mLastTimeAudioStateChanged < appRecord.mLastLaunchTime)
            && (appRecord.mLastLaunchTime < appRecord.mLastTimeFgEventChanged)
            && mScreenOn
            && appRecord.mGpsRecogInfo.mRequestGps
            ) {

                if (now > (appRecord.mLastTimeFgEventChanged + 5000)
                    && now > (appRecord.mLastTimeAudioStateChanged + 5000)
                    && (appRecord.mGpsRecogInfo.mPendingFlag & PENDING_FLAG_CHECK_TYPE1) != 0
                    ) {
                    if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                        + " behavior type 1!!");
                    appRecord.mGpsRecogInfo.mBehaviorType = ((appRecord.mGpsRecogInfo.mBehaviorType & 0x0f)|BEHAVIOR_TYPE_1);
                } else {
                    if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                        + " maybe type 1 delay check if it is a behavior type 1 !!");
                    appRecord.mGpsRecogInfo.mPendingFlag = (appRecord.mGpsRecogInfo.mPendingFlag|PENDING_FLAG_CHECK_TYPE1);

                    recognizeDelayed(appRecord, 5000);
                }
        }

        // TYPE 2
        if ((appRecord.mGpsRecogInfo.mBehaviorType & 0xf0) == 0
            && Event.MOVE_TO_BACKGROUND == appRecord.mFgEvent
            && (appRecord.mGpsRecogInfo.mBehaviorType & BEHAVIOR_TYPE_MAYBE_2) != 0
            && appRecord.mGpsRecogInfo.mAvoidConstraintGps
            && (appRecord.mNotificationState) == RecogAlgorithm.NOTIFICATION_STATE_NONE
            && (timeInSpan(appRecord.mLastTimeAudioStateChanged, appRecord.mLastTimeFgEventChanged, 1000)
                || (appRecord.mLastTimeAudioStateChanged > appRecord.mLastTimeFgEventChanged))
            && mScreenOn
            && appRecord.mGpsRecogInfo.mRequestGps
            ) {
                if (now > (appRecord.mLastTimeFgEventChanged + 5000)
                    && (appRecord.mGpsRecogInfo.mPendingFlag & PENDING_FLAG_CHECK_TYPE2) != 0
                    ) {
                    if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                        + " behavior type 2!!");
                    appRecord.mGpsRecogInfo.mBehaviorType =  ((appRecord.mGpsRecogInfo.mBehaviorType & 0x0f)|BEHAVIOR_TYPE_2);
                    //appRecord.mPendingFlag = 0;
                } else if ((appRecord.mAudioState & RecogAlgorithm.AUDIO_TYPE_OUT) != 0) {
                    if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                        + " maybe type 2 delay check if it is a behavior type 2 !!");
                    appRecord.mGpsRecogInfo.mPendingFlag = (appRecord.mGpsRecogInfo.mPendingFlag|PENDING_FLAG_CHECK_TYPE2);

                    recognizeDelayed(appRecord, 5000);
                }
        }

        // TYPE 3
        if ((appRecord.mGpsRecogInfo.mBehaviorType & 0xf0) == 0
            && Event.MOVE_TO_FOREGROUND == appRecord.mFgEvent
            && (appRecord.mGpsRecogInfo.mBehaviorType & BEHAVIOR_TYPE_MAYBE_3) != 0
            && (appRecord.mGpsRecogInfo.mBehaviorType & BEHAVIOR_TYPE_MAYBE_2) != 0
            && (appRecord.mNotificationState & (RecogAlgorithm.NOTIFICATION_STATE_ALERT|RecogAlgorithm.NOTIFICATION_STATE_AUDIO_REPEAT)) == 0
            && appRecord.mGpsRecogInfo.mAvoidConstraintGps
            && mScreenOn
            && appRecord.mGpsRecogInfo.mRequestGps
            ) {

                if (now > (appRecord.mLastTimeFgEventChanged + 5000)
                    && now > (appRecord.mLastTimeNotificationStateChanged + 5000)
                    && (appRecord.mGpsRecogInfo.mPendingFlag & PENDING_FLAG_CHECK_TYPE3) != 0
                    ) {
                    if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                        + " behavior type 3!!");
                    appRecord.mGpsRecogInfo.mBehaviorType =  ((appRecord.mGpsRecogInfo.mBehaviorType & 0x0f)|BEHAVIOR_TYPE_3);
                } else {
                    if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                        + " maybe type 3 delay check if it is a behavior type 3 !!");
                    appRecord.mGpsRecogInfo.mPendingFlag = (appRecord.mGpsRecogInfo.mPendingFlag|PENDING_FLAG_CHECK_TYPE3);

                    recognizeDelayed(appRecord, 5000);
                }
        }

        // check TYPE 1, if stop using GPS
        if (!mScreenOn
            && appRecord.mGpsRecogInfo.mRequestGps
            && appRecord.mGpsRecogInfo.mAvoidConstraintGps
            && (((appRecord.mGpsRecogInfo.mBehaviorType & 0xf0) == BEHAVIOR_TYPE_1)
                || ((appRecord.mGpsRecogInfo.mBehaviorType & (0xf0|BEHAVIOR_TYPE_MAYBE_1|BEHAVIOR_TYPE_MAYBE_2|BEHAVIOR_TYPE_MAYBE_3)) == BEHAVIOR_TYPE_MAYBE_1))
            && (appRecord.mAudioState & RecogAlgorithm.AUDIO_TYPE_OUT) == 0
            && Event.MOVE_TO_BACKGROUND == appRecord.mFgEvent
            && (appRecord.mLastTimeAudioStateChanged < appRecord.mLastLaunchTime)
            && (appRecord.mLastLaunchTime < appRecord.mLastTimeFgEventChanged)
        ) {

            if (now > (mLastTimeScreenStateChanged + 5000)
                && now > (appRecord.mLastTimeAudioStateChanged + 5000)
                && (mLastTimeScreenStateChanged > appRecord.mLastTimeAudioStateChanged)
                ) {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " maybe type 1 gps navigation is stopped !!");
                appRecord.mGpsRecogInfo.mAvoidConstraintGps = false;

                mStateChanged = true;
            } else {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " maybe type 1 delay check if gps navigation is stopped !!");

                recognizeDelayed(appRecord, 5000);
            }

        }


        // check TYPE 2, if stop using GPS
        if (Event.MOVE_TO_BACKGROUND == appRecord.mFgEvent
            && appRecord.mGpsRecogInfo.mRequestGps
            && appRecord.mGpsRecogInfo.mAvoidConstraintGps
            && (((appRecord.mGpsRecogInfo.mBehaviorType & 0xf0) == BEHAVIOR_TYPE_2)
                || ((appRecord.mGpsRecogInfo.mBehaviorType & (0xf0|BEHAVIOR_TYPE_MAYBE_2|BEHAVIOR_TYPE_MAYBE_3)) == BEHAVIOR_TYPE_MAYBE_2))
            && (appRecord.mAudioState & RecogAlgorithm.AUDIO_TYPE_OUT) == 0
            && (appRecord.mLastTimeAudioStateChanged < appRecord.mLastLaunchTime)
            && (appRecord.mLastLaunchTime < appRecord.mLastTimeFgEventChanged)
        ) {

            if (now > (appRecord.mLastTimeFgEventChanged + 5000)
                && now > (appRecord.mLastTimeAudioStateChanged + 5000)
                && (appRecord.mLastTimeFgEventChanged > appRecord.mLastTimeAudioStateChanged)
                ) {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " maybe type 2 gps navigation is stopped !!");
                appRecord.mGpsRecogInfo.mAvoidConstraintGps = false;

                mStateChanged = true;
            } else {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " maybe type 2 delay check if gps navigation is stopped !!");

                recognizeDelayed(appRecord, 5000);
            }

        }


        // check TYPE 3, if stop using GPS
        if (Event.MOVE_TO_BACKGROUND == appRecord.mFgEvent
            && appRecord.mGpsRecogInfo.mRequestGps
            && appRecord.mGpsRecogInfo.mAvoidConstraintGps
            && (((appRecord.mGpsRecogInfo.mBehaviorType & 0xf0) == BEHAVIOR_TYPE_3)
                || ((appRecord.mGpsRecogInfo.mBehaviorType & (0xf0|BEHAVIOR_TYPE_MAYBE_3)) == BEHAVIOR_TYPE_MAYBE_3))
            && appRecord.mNotificationState == RecogAlgorithm.NOTIFICATION_STATE_NONE
            && (appRecord.mLastLaunchTime < appRecord.mLastTimeFgEventChanged)
        ) {

            if (now > (appRecord.mLastTimeFgEventChanged + 5000)
                && now > (appRecord.mLastTimeAudioStateChanged + 5000)
                && now > (appRecord.mLastTimeNotificationStateChanged + 5000)
                ) {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " maybe type 3 gps navigation is stopped !!");
                appRecord.mGpsRecogInfo.mAvoidConstraintGps = false;

                mStateChanged = true;
            } else {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " maybe type 3 delay check if gps navigation is stopped !!");

                recognizeDelayed(appRecord, 5000);
            }

        }

    }


    private void startRecognizeAfterTypeDecided(RecogInfo appRecord) {

        int behaviorType = (appRecord.mGpsRecogInfo.mBehaviorType & 0xf0);

        // TYPE1
        if (!appRecord.mGpsRecogInfo.mAvoidConstraintGps
            && behaviorType == BEHAVIOR_TYPE_1
            && !mScreenOn
            && ((appRecord.mAudioState & RecogAlgorithm.AUDIO_TYPE_OUT) != 0)
            && (timeInSpan(appRecord.mLastTimeAudioStateChanged, mLastTimeScreenStateChanged, 1000)
                || (appRecord.mLastTimeAudioStateChanged > mLastTimeScreenStateChanged))
            && appRecord.mGpsRecogInfo.mRequestGps
            ) {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " 1 request GPS and has AUDIO OUT, it may be doing Navigation! Then avoid GPS constraint!!");
                appRecord.mGpsRecogInfo.mAvoidConstraintGps = true;

                mStateChanged = true;
        }


        // TYPE2
        if (!appRecord.mGpsRecogInfo.mAvoidConstraintGps
            && behaviorType == BEHAVIOR_TYPE_2
            && Event.MOVE_TO_BACKGROUND == appRecord.mFgEvent
            && ((appRecord.mAudioState & RecogAlgorithm.AUDIO_TYPE_OUT) != 0)
            && (timeInSpan(appRecord.mLastTimeAudioStateChanged, appRecord.mLastTimeFgEventChanged, 1000)
                || (appRecord.mLastTimeAudioStateChanged > appRecord.mLastTimeFgEventChanged))
            && appRecord.mGpsRecogInfo.mRequestGps
            ) {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " 2 request GPS and has AUDIO OUT, it may be doing Navigation! Then avoid GPS constraint!!");
                appRecord.mGpsRecogInfo.mAvoidConstraintGps = true;

                mStateChanged = true;
        }


        // TYPE3
        if (!appRecord.mGpsRecogInfo.mAvoidConstraintGps
            && behaviorType == BEHAVIOR_TYPE_3
            && Event.MOVE_TO_BACKGROUND == appRecord.mFgEvent
            && (appRecord.mNotificationState & (RecogAlgorithm.NOTIFICATION_STATE_ALERT|RecogAlgorithm.NOTIFICATION_STATE_AUDIO_REPEAT)) != 0
            && (timeInSpan(appRecord.mLastTimeNotificationStateChanged, appRecord.mLastTimeFgEventChanged, 1000)
                || (appRecord.mLastTimeNotificationStateChanged > appRecord.mLastTimeFgEventChanged))
            && appRecord.mGpsRecogInfo.mRequestGps
            ) {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " 3 request GPS and has AUDIO OUT, it may be doing Navigation! Then avoid GPS constraint!!");
                appRecord.mGpsRecogInfo.mAvoidConstraintGps = true;

                mStateChanged = true;
        }

        long now = SystemClock.elapsedRealtime();

        // clear pending flag
        if (!mScreenOn) appRecord.mGpsRecogInfo.mPendingFlag = 0;

        // check TYPE 1, if stop using GPS
        if (!mScreenOn
            && appRecord.mGpsRecogInfo.mAvoidConstraintGps
            && behaviorType == BEHAVIOR_TYPE_1
            && (appRecord.mAudioState & RecogAlgorithm.AUDIO_TYPE_OUT) == 0
            && Event.MOVE_TO_BACKGROUND == appRecord.mFgEvent
            && (appRecord.mLastTimeAudioStateChanged < appRecord.mLastLaunchTime)
            && (appRecord.mLastLaunchTime < appRecord.mLastTimeFgEventChanged)
        ) {

            if (now > (mLastTimeScreenStateChanged + 5000)
                && now > (appRecord.mLastTimeAudioStateChanged + 5000)
                && (mLastTimeScreenStateChanged > appRecord.mLastTimeAudioStateChanged)
                ) {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " type 1 gps navigation is stopped !!");
                appRecord.mGpsRecogInfo.mAvoidConstraintGps = false;

                mStateChanged = true;
            } else {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " type 1 delay check if gps navigation is stopped !!");

                recognizeDelayed(appRecord, 5000);
            }

        }

        // check TYPE 2, if stop using GPS
        if (Event.MOVE_TO_BACKGROUND == appRecord.mFgEvent
            && appRecord.mGpsRecogInfo.mAvoidConstraintGps
            && behaviorType == BEHAVIOR_TYPE_2
            && (appRecord.mAudioState & RecogAlgorithm.AUDIO_TYPE_OUT) == 0
            && (appRecord.mLastTimeAudioStateChanged < appRecord.mLastLaunchTime)
            && (appRecord.mLastLaunchTime < appRecord.mLastTimeFgEventChanged)
        ) {

            if (now > (appRecord.mLastTimeFgEventChanged + 5000)
                && now > (appRecord.mLastTimeAudioStateChanged + 5000)
                && (appRecord.mLastTimeFgEventChanged > appRecord.mLastTimeAudioStateChanged)
                ) {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " type 2 gps navigation is stopped !!");
                appRecord.mGpsRecogInfo.mAvoidConstraintGps = false;

                mStateChanged = true;
            } else {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " type 2 delay check if gps navigation is stopped !!");

                recognizeDelayed(appRecord, 5000);
            }

        }

        // check TYPE 3, if stop using GPS
        if (Event.MOVE_TO_BACKGROUND == appRecord.mFgEvent
            && appRecord.mGpsRecogInfo.mAvoidConstraintGps
            && behaviorType == BEHAVIOR_TYPE_3
            && appRecord.mNotificationState == RecogAlgorithm.NOTIFICATION_STATE_NONE
            && (appRecord.mLastLaunchTime < appRecord.mLastTimeFgEventChanged)
        ) {

            if (now > (appRecord.mLastTimeFgEventChanged + 5000)
                && now > (appRecord.mLastTimeAudioStateChanged + 5000)
                && now > (appRecord.mLastTimeNotificationStateChanged + 5000)
                ) {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " type 3 gps navigation is stopped !!");
                appRecord.mGpsRecogInfo.mAvoidConstraintGps = false;

                mStateChanged = true;
            } else {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " type 3 delay check if gps navigation is stopped !!");

                recognizeDelayed(appRecord, 5000);
            }

        }

    }

    public void startRecognize(RecogInfo appRecord) {

        if ("android".equals(appRecord.mPackageName)) return;

        if (!appRecord.mGpsRecogInfo.mRequestGps) {

            if (appRecord.mGpsRecogInfo.mAvoidConstraintGps) {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " does not request GPS, clear mAvoidConstraintGps!!");
                appRecord.mGpsRecogInfo.mAvoidConstraintGps = false;
                appRecord.mGpsRecogInfo.mHasNoClearNotificationWhenNavi = false;

                mHandler.removeMessages(MSG_NAVIGATION_TIMEOUT, appRecord);

                if (!mScreenOn && !mCharging) {
                    if (DEBUG) Slog.d(TAG,  "reCheckAllAppInfo 1");
                    mPowerControllerInternal.reCheckAllAppInfoDelayed(0);
                }
            }
            return;
        }

        boolean preAvoidConstraintGps = appRecord.mGpsRecogInfo.mAvoidConstraintGps;

        if (DEBUG) {
            Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                + " mFgEvent:" +  Util.AppState2Str(appRecord.mFgEvent)
                + " mLastTimeFgEventChanged:" +  appRecord.mLastTimeFgEventChanged
                + " mLastLaunchTime:" +  appRecord.mLastLaunchTime
                + " mProcState:" +  Util.ProcState2Str(appRecord.mProcState)
                + " mLastTimeProcStateChanged:" +  appRecord.mLastTimeProcStateChanged
                + " mNotificationState:" +  appRecord.mNotificationState
                + " mLastTimeNotificationStateChanged:" +  appRecord.mLastTimeNotificationStateChanged
                + " mAudioState:" +  appRecord.mAudioState
                + " mLastTimeAudioStateChanged:" +  appRecord.mLastTimeAudioStateChanged
                + " mRequestGps:" +  appRecord.mGpsRecogInfo.mRequestGps
                + " mAvoidConstraintGps:" +  appRecord.mGpsRecogInfo.mAvoidConstraintGps
                + " mBehaviorType:" +  appRecord.mGpsRecogInfo.mBehaviorType
                + " mScreenOn:" +  mScreenOn
                + " mLastTimeScreenStateChanged:" +  mLastTimeScreenStateChanged
                + " mLastBgTime:" + appRecord.mLastBgTime
                + " mFgDuration:" + appRecord.mFgDuration
                + " mAudioDuration:" + appRecord.mAudioDuration
                + " mBgAudioDuration:" + appRecord.mBgAudioDuration
                + " mHasNoClearNotificationWhenNavi:" + appRecord.mGpsRecogInfo.mHasNoClearNotificationWhenNavi
               );
        }


        if (!needDoRecognize(appRecord)) {

            if (appRecord.mGpsRecogInfo.mAvoidConstraintGps) {
                if (DEBUG) Slog.d(TAG,  "packageName:" + appRecord.mPackageName
                    + " does not do voice navigation, clear mAvoidConstraintGps!!");
                appRecord.mGpsRecogInfo.mAvoidConstraintGps = false;
                appRecord.mGpsRecogInfo.mHasNoClearNotificationWhenNavi = false;

                mHandler.removeMessages(MSG_NAVIGATION_TIMEOUT, appRecord);

                if (!mScreenOn && !mCharging) {
                    if (DEBUG) Slog.d(TAG,  "reCheckAllAppInfo 1");
                    mPowerControllerInternal.reCheckAllAppInfoDelayed(0);
                }
            }
            return;
        }

        if (behaviorTypeDecided(appRecord)) {
            startRecognizeAfterTypeDecided(appRecord);
        } else {
            startRecognizePreTypeDecided(appRecord);
        }

        if (mStateChanged && !mScreenOn && !mCharging) {
            if (DEBUG) Slog.d(TAG,  "reCheckAllAppInfo");
            mPowerControllerInternal.reCheckAllAppInfoDelayed(0);
        }

        if (preAvoidConstraintGps != appRecord.mGpsRecogInfo.mAvoidConstraintGps) {
            if (DEBUG) Slog.d(TAG,  "mAvoidConstraintGps changed for " + appRecord.mPackageName);

            if (appRecord.mGpsRecogInfo.mAvoidConstraintGps) {
                if (DEBUG) Slog.d(TAG,  "to re-check the navigation of " + appRecord.mPackageName);
                mHandler.removeMessages(MSG_NAVIGATION_TIMEOUT, appRecord);
                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_NAVIGATION_TIMEOUT, appRecord), VOICE_NAVIGATION_TIMEOUT);
            }
        }

        if (appRecord.mGpsRecogInfo.mAvoidConstraintGps) {
            if ((appRecord.mNotificationState & RecogAlgorithm.NOTIFICATION_STATE_NO_CLEAR) != 0
                && !appRecord.mGpsRecogInfo.mHasNoClearNotificationWhenNavi)
                appRecord.mGpsRecogInfo.mHasNoClearNotificationWhenNavi = true;
        } else {
            if (appRecord.mGpsRecogInfo.mHasNoClearNotificationWhenNavi)
                appRecord.mGpsRecogInfo.mHasNoClearNotificationWhenNavi = false;
        }

        mStateChanged = false;
    }


    private boolean needDoRecognize(RecogInfo appRecord) {

        if (appRecord.mGpsRecogInfo.mAvoidConstraintGps
            && appRecord.mGpsRecogInfo.mHasNoClearNotificationWhenNavi
            && (appRecord.mNotificationState & RecogAlgorithm.NOTIFICATION_STATE_NO_CLEAR) == 0) {
            return false;
        }

        long now =  SystemClock.elapsedRealtime();

        // 40min
        if ((now - appRecord.mLastTimeAudioStateChanged) >= VOICE_NAVIGATION_TIMEOUT) {
            return false;
        }

        return true;
    }

    /*
     * the Math.abs(timeA, timeB) <= span
     */
    private boolean timeInSpan(long timeA, long timeB, long span) {
        return (Math.abs(timeA - timeB) <= span);
    }

    /*
     * timeA >= timeB && timeA <= (timeB + span)
     */
    private boolean timeBiggerThanInSpan(long timeA, long timeB, long span) {
        return (timeA >= timeB && timeA <= (timeB + span));
    }

    private long max(long A, long B) {
        return (A >= B ? A : B);
    }
}
