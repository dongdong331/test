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

public class RecogAlgorithmPlugin {

    RecogAlgorithm mRecogAlgorithm;

    Context mContext;

    int mAlgorithmTypeFlag;

    public RecogAlgorithmPlugin(Context context, int type) {
        mContext = context;
        mRecogAlgorithm = RecogAlgorithm.getInstance(mContext);
        mAlgorithmTypeFlag = type;
    }

    public int reportEvent(RecogInfo appRecogInfo, int eventType, int data) {
        return 0;
    }

    public int reportDeviceState(int stateType, boolean stateOn) {
        return 0;
    }

    public boolean canConstraint(RecogInfo recogInfo) {
        return false;
    }

    public void startRecognize(RecogInfo appRecord) {
    }

    public boolean match(int type) {
        return ((mAlgorithmTypeFlag & type) != 0);
    }
}
