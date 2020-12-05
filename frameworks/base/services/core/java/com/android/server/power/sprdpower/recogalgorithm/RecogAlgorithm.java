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
import android.util.SparseArray;

import com.android.internal.R;
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

public class RecogAlgorithm {

    static final String TAG = "PowerController.RecogA";

    private final boolean DEBUG = Util.isDebug();


    static final int EVENT_TYPE_FG_STATE = 0;
    static final int EVENT_TYPE_PROC_STATE = 1;
    static final int EVENT_TYPE_AUDIO_STATE = 2;
    static final int EVENT_TYPE_WAKELOCK_STATE = 3;
    static final int EVENT_TYPE_GPS_STATE = 4;

    static final int DEVICE_STATE_TYPE_SCREEN = 0;
    static final int DEVICE_STATE_TYPE_NETWORK = 1;
    static final int DEVICE_STATE_TYPE_CHARGING = 2;
    static final int DEVICE_STATE_TYPE_DOZE = 3;

    static final int TYPE_GPS = 0;


    static final int RECOGNIZE_TYPE_ALL = 0xffff;
    static final int RECOGNIZE_TYPE_GPS = 1 << 0;


    // Audio type: 'IN' for recoder; 'OUT' for play music
    public static int AUDIO_TYPE_NONE = 0;
    public static int AUDIO_TYPE_IN = 1<<1;
    public static int AUDIO_TYPE_OUT = 1<<2;


    // Notification state:
    public static int NOTIFICATION_STATE_NONE = 0;
    public static int NOTIFICATION_STATE_NO_CLEAR = 1<<1;
    public static int NOTIFICATION_STATE_ALERT = 1<<2;
    public static int NOTIFICATION_STATE_AUDIO_REPEAT = 1<<3;
    public static int NOTIFICATION_STATE_OTHER = 1<<4;


    static RecogAlgorithm sInstance;

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Context mContext;

    private List<RecogAlgorithmPlugin> mRecogAlgorithmPlugins = new ArrayList<>();
    private GpsRecogAlgorithm mGpsRecogAlgorithm;

    // add for bug#965940
    private final Object mLock = new Object();

    //private ArrayMap<String, RecogInfo> mAppRecogInfoList = new ArrayMap<>();
    private SparseArray<ArrayMap<String, RecogInfo>> mAppRecogInfoListForUsers = new SparseArray<>();

    private boolean mScreenOn = true;
    private boolean mCharging = false;

    private long mLastTimeScreenStateChanged = 0;

    private boolean mStateChanged = false;


    public static RecogAlgorithm getInstance(Context context) {
        synchronized (RecogAlgorithm.class) {
            if (sInstance == null) {
                sInstance = new RecogAlgorithm(context);
            }
            return sInstance;
        }
    }

    public RecogAlgorithm(Context context) {
        mContext = context;
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new InternalHandler(mHandlerThread.getLooper());
    }

    public void createAlgorithms() {
        mGpsRecogAlgorithm = new GpsRecogAlgorithm(mContext, RECOGNIZE_TYPE_GPS, mHandler);
        mRecogAlgorithmPlugins.add(mGpsRecogAlgorithm);
    }

    public void reportEvent(String pkgName, int uid, int eventType, int data) {
        SimpleRecogInfo info = new SimpleRecogInfo(pkgName, uid);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_REPORT_EVENT, eventType, data, info));
    }

    public void reportDeviceState(int stateType, boolean stateOn) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_REPORT_DEVICE_STATE, stateType, (stateOn?1:0)));
    }

    public boolean canConstraint(String pkgName, int uid, int type) {
        RecogInfo recogInfo = getAppRecogInfo(pkgName, uid);

        switch (type) {
        case TYPE_GPS:
            return mGpsRecogAlgorithm.canConstraint(recogInfo);
        default:
            return false;
        }
    }

    public boolean hasNoClearNotification(String pkgName, int uid) {
        RecogInfo recogInfo = getAppRecogInfo(pkgName, uid);
        return (recogInfo != null && (recogInfo.mNotificationState & NOTIFICATION_STATE_NO_CLEAR) != 0);
    }

    public boolean hasRequestingGps(String pkgName, int uid) {
        RecogInfo recogInfo = getAppRecogInfo(pkgName, uid);
        return (recogInfo != null && recogInfo.mGpsRecogInfo != null &&  recogInfo.mGpsRecogInfo.mRequestGps);
    }

    public void updateNotificationDelayed(String pkgName, int uid, long delayedMillsec) {
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_UPDATE_NOTIFICATION, uid, 0, pkgName), delayedMillsec);
    }

    public void reRecognizeAllDelayed(long delayedMillsec) {
        mHandler.removeMessages(MSG_RERECOGNIZE_ALL);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_RERECOGNIZE_ALL), delayedMillsec);
    }

    public RecogInfo getAppRecogInfo(String pkgName, int uid) {
        // add for bug#965940
        synchronized(mLock) {
            ArrayMap<String, RecogInfo> mAppRecogInfoList = getAppRecogInfoList(UserHandle.getUserId(uid));

            RecogInfo appRecogInfo = null;
            int index = mAppRecogInfoList.indexOfKey(pkgName);
            if (index >= 0) {
                appRecogInfo = mAppRecogInfoList.valueAt(index);
            } else {
                appRecogInfo = new RecogInfo(pkgName, uid);
                mAppRecogInfoList.put(pkgName, appRecogInfo);
            }
            return appRecogInfo;
        }
    }


    //Message defines
    static final int MSG_RERECOGNIZE_ALL = 0;
    static final int MSG_UPDATE_NOTIFICATION = 1;
    static final int MSG_REPORT_EVENT= 2;
    static final int MSG_REPORT_DEVICE_STATE= 3;


    class InternalHandler extends Handler {
        InternalHandler(Looper looper) {
            super(looper);
        }

        String Msg2Str(int msg) {
            final String msgStr[] = {"MSG_RERECOGNIZE_ALL",
                "MSG_UPDATE_NOTIFICATION",
                "MSG_REPORT_EVENT",
                "MSG_REPORT_DEVICE_STATE"
            };

            if ((0 <= msg) && (msg < msgStr.length))
                return msgStr[msg];
            else
                return "Unknown message: " + msg;
        }

        @Override public void handleMessage(Message msg) {
            if (DEBUG) Slog.d(TAG, "handleMessage(" + Msg2Str(msg.what) + ")");

            switch (msg.what) {
            case MSG_RERECOGNIZE_ALL:
                reRecognizeAll(RECOGNIZE_TYPE_ALL);
                break;

            case MSG_UPDATE_NOTIFICATION:
                updateNotification((String)msg.obj, msg.arg1);
                break;

            case MSG_REPORT_EVENT:
                reportEventInternal((SimpleRecogInfo)msg.obj, msg.arg1, msg.arg2);
                break;

            case MSG_REPORT_DEVICE_STATE:
                boolean stateOn = (msg.arg2 == 1 ? true : false);
                reportDeviceStateInternal(msg.arg1, stateOn);
                break;
            }
        }
    }

    private void reportEventInternal(SimpleRecogInfo sInfo, int eventType, int data) {
        int changed = 0;

        RecogInfo appRecogInfo = getAppRecogInfo(sInfo.mPackageName, sInfo.mUid);
        switch (eventType) {
        case EVENT_TYPE_FG_STATE:
            int fgEvent = data;
            if (appRecogInfo.mFgEvent != fgEvent) {
                appRecogInfo.mFgEvent = fgEvent;

                long now = SystemClock.elapsedRealtime();
                appRecogInfo.mLastTimeFgEventChanged = now;


                if (Event.MOVE_TO_FOREGROUND == appRecogInfo.mFgEvent) {
                    appRecogInfo.mLastLaunchTime = SystemClock.elapsedRealtime();
                } else if (Event.MOVE_TO_BACKGROUND == appRecogInfo.mFgEvent) {

                    if (appRecogInfo.mLastLaunchTime > 0
                        && appRecogInfo.mLastLaunchTime > appRecogInfo.mLastBgTime) {
                        appRecogInfo.mFgDuration += (now - appRecogInfo.mLastLaunchTime);
                    }

                    appRecogInfo.mLastBgTime = now;
                }


                changed = RECOGNIZE_TYPE_ALL;
            }
        break;

        case EVENT_TYPE_PROC_STATE:
            int procState = data;
            if (appRecogInfo.mProcState != procState) {
                appRecogInfo.mProcState = procState;
                appRecogInfo.mLastTimeProcStateChanged = SystemClock.elapsedRealtime();
                changed = RECOGNIZE_TYPE_ALL;
            }
        break;

        case EVENT_TYPE_AUDIO_STATE:
            int audioState = data;
            if (appRecogInfo.mAudioState != audioState) {

                if ((appRecogInfo.mAudioState & AUDIO_TYPE_OUT) != AUDIO_TYPE_NONE
                    && (audioState & AUDIO_TYPE_OUT) == AUDIO_TYPE_NONE
                    && appRecogInfo.mLastTimeAudioStateChanged > 0) {
                    long now = SystemClock.elapsedRealtime();
                    appRecogInfo.mAudioDuration += (now - appRecogInfo.mLastTimeAudioStateChanged);

                    if (appRecogInfo.mLastLaunchTime > 0 && appRecogInfo.mLastBgTime > appRecogInfo.mLastLaunchTime) {
                        if (appRecogInfo.mLastTimeAudioStateChanged >= appRecogInfo.mLastBgTime) {
                            appRecogInfo.mBgAudioDuration += (now - appRecogInfo.mLastTimeAudioStateChanged);
                        } else {
                            appRecogInfo.mBgAudioDuration += (now - appRecogInfo.mLastBgTime);
                        }
                    }
                }
                appRecogInfo.mAudioState = audioState;
                appRecogInfo.mLastTimeAudioStateChanged = SystemClock.elapsedRealtime();
                changed = RECOGNIZE_TYPE_ALL;
            }
        break;

        }

        changed |= reportEventToAlgorithms(appRecogInfo, eventType, data);

        if (changed != 0) startRecognize(appRecogInfo, changed);
    }



    private void reportDeviceStateInternal(int stateType, boolean stateOn) {

        int changed = 0;

        switch (stateType) {
        case DEVICE_STATE_TYPE_SCREEN:
            if (mScreenOn != stateOn) {
                mScreenOn = stateOn;
                mLastTimeScreenStateChanged = SystemClock.elapsedRealtime();
                changed = RECOGNIZE_TYPE_ALL;
            }
        break;

        case DEVICE_STATE_TYPE_CHARGING:
            if (mCharging != stateOn) {
                mCharging = stateOn;
            }
        break;

        }

        changed |= reportDeviceStateToAlgorithms(stateType, stateOn);

        if (changed != 0) {
            reRecognizeAll(changed);
        }
    }

    private int getNotificationState(String pkgName) {
        int notificationState = 0;

        if (pkgName != null && pkgName.equals("android")) return 0;

        // has other method to judge ??
        try {
            INotificationManager inm = NotificationManager.getService();
            final ParceledListSlice<StatusBarNotification> parceledList
                    = inm.getAppActiveNotifications(pkgName, UserHandle.myUserId());
            final List<StatusBarNotification> list = parceledList.getList();

            if (list != null && list.size() > 0) {
                notificationState =  (notificationState&~NOTIFICATION_STATE_OTHER) | NOTIFICATION_STATE_OTHER;
            }

            int N = list.size();
            for (int i = 0; i < N; i++) {
                StatusBarNotification sbn = list.get(i);
                Notification notification = sbn.getNotification();
                if (DEBUG) Slog.d(TAG,  "packageName:" + pkgName
                    + " has Notification:(" + notification
                    + " title=" + notification.extras.getCharSequence(Notification.EXTRA_TITLE)
                    + " text=" + notification.extras.getCharSequence(Notification.EXTRA_TEXT) +")");

                if (notification.extras.getCharSequence(Notification.EXTRA_TEXT) != null) {

                    String sysText = mContext.getString(com.android.internal.R.string
                                                                            .app_running_notification_text);
                    String text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString();
                    int color = mContext.getColor(com.android.internal
                                                .R.color.system_notification_accent_color);
                    if (sysText.equals(text) && color== notification.color) {
                        continue;
                    }
                }

                if (notification.priority >= Notification.PRIORITY_DEFAULT
                    && ((notification.flags & Notification.FLAG_NO_CLEAR) != 0)
                    && ((notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0)) {
                    notificationState =  (notificationState&~NOTIFICATION_STATE_NO_CLEAR) | NOTIFICATION_STATE_NO_CLEAR;
                }

                if ((notification.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0) {
                    notificationState =  (notificationState&~NOTIFICATION_STATE_ALERT) | NOTIFICATION_STATE_ALERT;
                }

                if ((notification.flags & Notification.FLAG_INSISTENT) != 0) {
                    notificationState =  (notificationState&~NOTIFICATION_STATE_AUDIO_REPEAT) | NOTIFICATION_STATE_AUDIO_REPEAT;
                }
            }

        } catch (Exception e) {}

        return notificationState;

    }

    private void updateNotification(String pkgName, int uid) {
        boolean changed = false;

        RecogInfo appRecogInfo = getAppRecogInfo(pkgName, uid);
        int notificationState = getNotificationState(appRecogInfo.mPackageName);
        if (appRecogInfo.mNotificationState != notificationState) {
            appRecogInfo.mNotificationState = notificationState;
            appRecogInfo.mLastTimeNotificationStateChanged = SystemClock.elapsedRealtime();
            changed = true;
        }
        if (changed) startRecognize(appRecogInfo, RECOGNIZE_TYPE_ALL);
    }


    private int reportEventToAlgorithms(RecogInfo appRecogInfo, int eventType, int data) {
        int changed = 0;
        for (int i = 0; i < mRecogAlgorithmPlugins.size(); i++) {
            RecogAlgorithmPlugin helper = mRecogAlgorithmPlugins.get(i);
            changed |= helper.reportEvent(appRecogInfo, eventType, data);
        }
        return changed;
    }

    private int reportDeviceStateToAlgorithms(int stateType, boolean stateOn) {
        int changed = 0;
        for (int i = 0; i < mRecogAlgorithmPlugins.size(); i++) {
            RecogAlgorithmPlugin helper = mRecogAlgorithmPlugins.get(i);
            changed |= helper.reportDeviceState(stateType, stateOn);
        }
        return changed;
    }

    private void startRecognize(RecogInfo appRecogInfo, int changed) {
        for (int i = 0; i < mRecogAlgorithmPlugins.size(); i++) {
            RecogAlgorithmPlugin helper = mRecogAlgorithmPlugins.get(i);
            if (helper.match(changed))
                helper.startRecognize(appRecogInfo);
        }
    }

    private void reRecognizeAll(int changed) {
        for (int index=mAppRecogInfoListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, RecogInfo> mAppRecogInfoList = mAppRecogInfoListForUsers.valueAt(index);
            for (int i=0;i<mAppRecogInfoList.size();i++) {
                RecogInfo appRecogInfo = mAppRecogInfoList.valueAt(i);
                startRecognize(appRecogInfo, changed);
            }
        }
    }

    private static class SimpleRecogInfo {

        String mPackageName;
        int mUid;

        public SimpleRecogInfo(String pkgName, int uid) {
            mPackageName = pkgName;
            mUid = uid;
        }
    }

    private ArrayMap<String, RecogInfo> getAppRecogInfoList(int userId) {
        ArrayMap<String, RecogInfo> mAppRecogInfoList = mAppRecogInfoListForUsers.get(userId);
        if (mAppRecogInfoList == null) {
            mAppRecogInfoList = new ArrayMap<>();
            mAppRecogInfoListForUsers.put(userId, mAppRecogInfoList);
        }
        return mAppRecogInfoList;
    }

}
