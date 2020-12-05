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

public class RecogInfo {

    static final String TAG = "PowerController.RecogA";

    private final boolean DEBUG = true;


    String mPackageName;
    // The kernel user-ID that has been assigned to this application
    int mUid;

    int mFgEvent;
    long mLastTimeFgEventChanged; // elapsed time instead
    long mLastLaunchTime;  // elapsed time instead
    long mLastBgTime;  // elapsed time instead
    long mFgDuration;

    // Process state: PROCESS_STATE_PERSISTENT / PROCESS_STATE_PERSISTENT_UI /...
    int mProcState;
    long mLastTimeProcStateChanged;  // elapsed time instead

    // flags from ApplicationInfo
    int mFlags;

    // if set to true, then this app has active notification when enter standby
    int mNotificationState;
    long mLastTimeNotificationStateChanged;

    // current audio type
    int mAudioState;
    long mLastTimeAudioStateChanged;
    long mAudioDuration;
    long mBgAudioDuration;


    // -------- below are special for GPS ----------
    public GpsRecogInfo mGpsRecogInfo;


    public RecogInfo(String pkgName, int uid) {
        mPackageName = pkgName;

        mUid = uid;

        mFgEvent = Event.NONE;
        mLastTimeFgEventChanged = 0; // elapsed time instead
        mLastLaunchTime = 0;  // elapsed time instead
        mLastBgTime = 0;
        mFgDuration = 0;

        mProcState = ActivityManager.PROCESS_STATE_CACHED_EMPTY;
        mLastTimeProcStateChanged = 0;  // elapsed time instead

        mFlags = 0;

        mNotificationState = 0;
        mLastTimeNotificationStateChanged = 0;

        mAudioState = 0;
        mLastTimeAudioStateChanged = 0;
        mAudioDuration = 0;
        mBgAudioDuration = 0;

        mGpsRecogInfo = new GpsRecogInfo();
    }


    final class GpsRecogInfo {

        // -------- below are special for GPS ----------
        public boolean mRequestGps = false;
        public int mRequestGpsCount = 0;

        // if set to true, then this app can not be constraint for GPS access
        public boolean mAvoidConstraintGps = false;

        // has not-clearable notification when doing navigation
        public boolean mHasNoClearNotificationWhenNavi = false;

        public int mBehaviorType = 0;

        public int mPendingFlag = 0;
    }

}
