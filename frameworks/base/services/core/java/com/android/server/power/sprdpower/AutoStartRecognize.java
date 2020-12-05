/*
 ** Copyright 2016 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlarmManager;
import android.app.AppGlobals;
import android.app.INotificationManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.sprdpower.IPowerGuru;
import android.app.IActivityManager;
import android.app.IUidObserver;
import android.app.usage.IUsageStatsManager;
import android.app.usage.UsageEvents.Event;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.app.usage.UsageStatsManagerInternal;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.sprdpower.AppPowerSaveConfig;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IDeviceIdleController;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.sprdpower.PowerManagerEx;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.service.wallpaper.WallpaperService;
import android.speech.RecognizerIntent;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.SparseArray;
import android.view.inputmethod.InputMethodInfo;

import com.android.internal.view.IInputMethodManager;
import com.android.internal.util.ArrayUtils;
import com.android.server.LocalServices;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.Calendar;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

//SPRD:Bug 814570 About apps auto run BEG
import com.android.internal.R;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.WindowManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.app.Dialog;
import com.android.server.policy.PhoneWindowManager;
//SPRD:Bug 814570 About apps auto run END

import static android.provider.Telephony.Sms.Intents.SMS_DELIVER_ACTION;
import static android.accounts.AccountManager.ACTION_AUTHENTICATOR_INTENT;

public class AutoStartRecognize {

    private static final String TAG = "PowerController.BgCleanAuto";


    private final boolean DEBUG = isDebug();
    private final boolean DEBUG_MORE = false;
    private final boolean TEST = PowerController.TEST;


    // app start reason, should sync with the ActivityManagerService
    // that calling judgeStartAllowLocked
    private static final String REASON_START_SERVICE = "start-service";
    private static final String REASON_BIND_SERVICE = "bind-service";
    private static final String REASON_CONTENT_PROVIDER = "contentprovider";
    private static final String REASON_BROADCAST = "send-broadcast";
    private static final String REASON_START_ACTIVITY = "start-activity";

    private static final int LAUNCH_STATE_NONE = 0;
    private static final int LAUNCH_STATE_ALLOW = 1;
    private static final int LAUNCH_STATE_DENY = 2;

    private static final int CALLERINFO_STATE_INVALID = 0;
    private static final int CALLERINFO_STATE_VALID = 1;

    // use property to control this feature
    private final boolean mEnabled = (1 == SystemProperties.getInt(PowerController.POWER_CONTROLLER_BGCLEAN_ENABLE, 1));


    private final IActivityManager mActivityManager;
    private Handler mHandler;

    // Apps that are visible
    private ArrayList<String> mVisibleAppList = new ArrayList<>();

    private final ArrayMap<CallerAppInfo, Integer> mAutoStartMapList = new ArrayMap<>();
    //private ArrayList<CallerAppInfo> mAutoStartMapList = new ArrayList<>();

    private static class CallerAppInfo {
        public Intent mIntent;
        public String mTargetPackageName;
        public String mPackageName;
        public int mUid;
        public String mReason;
        public int mState;
        public int mHashCode;
        public int mAllowCount;
        public int mDenyCount;

        public CallerAppInfo(String targetApp, String packageName, int uid, String reason, Intent intent) {
            mTargetPackageName = targetApp;
            mPackageName = packageName;
            mUid = uid;
            mReason = reason;
            if (intent != null)
                mIntent = (Intent)intent.clone();
            else
                mIntent = null;

            mState = CALLERINFO_STATE_VALID;
            mHashCode = 0;
            mAllowCount = 0;
            mDenyCount = 0;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object == null) {
                return false;
            }
            if (getClass() != object.getClass()) {
                return false;
            }
            CallerAppInfo other = (CallerAppInfo) object;
            if (mTargetPackageName == null
                || !mTargetPackageName.equals(other.mTargetPackageName)) {
                return false;
            }
            if (mPackageName == null
                || !mPackageName.equals(other.mPackageName)) {
                return false;
            }
            if (mReason == null
                || !mReason.equals(other.mReason)) {
                return false;
            }
            if ((mIntent == null && other.mIntent != null)
                || (mIntent != null && other.mIntent == null)
                || (mIntent != null && other.mIntent != null && !mIntent.filterEquals(other.mIntent))) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            if (mHashCode != 0) return mHashCode;

            mHashCode = 0;
            if (mTargetPackageName != null) {
                mHashCode += mTargetPackageName.hashCode();
            }
            if (mPackageName != null) {
                mHashCode += mPackageName.hashCode();
            }
            if (mReason != null) {
                mHashCode += mReason.hashCode();
            }
            if (mIntent != null) {
                mHashCode += mIntent.filterHashCode();
            }

            return mHashCode;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append("{" + mTargetPackageName
                + " from " + mPackageName
                + " reason:" + mReason + " intent:" + mIntent
                + " state:" + mState + "}");

            return result.toString();
        }
    }

    public AutoStartRecognize(Context context, IActivityManager activityManager, Handler handler) {

        mActivityManager = activityManager;
        mHandler = new InternalHandler(handler.getLooper());
    }


    private static final int MSG_CHECK_CALLER_INFO = 0;
    private static final int MSG_CHECK_VISIBLE_APP = 1;
    private static final int MSG_ADD_CALLER_INFO = 2;
    private static final int MSG_CLEAR_CALLER_INFO = 3;


    private class InternalHandler extends Handler {
        InternalHandler(Looper looper) {
            super(looper);
        }

        private String Msg2Str(int msg) {
            final String msgStr[] = {
                "MSG_CHECK_CALLER_INFO",
                "MSG_CHECK_VISIBLE_APP",
                "MSG_ADD_CALLER_INFO",
                "MSG_CLEAR_CALLER_INFO",
            };

            if ((0 <= msg) && (msg < msgStr.length))
                return msgStr[msg];
            else
                return "Unknown message: " + msg;
        }

        @Override public void handleMessage(Message msg) {
            if (DEBUG) Slog.d(TAG, "handleMessage(" + Msg2Str(msg.what) + ")");

            switch (msg.what) {
            case MSG_CHECK_CALLER_INFO:
                CallerAppInfo callerInfo = (CallerAppInfo)msg.obj;
                checkAutoStartCallerInfo(callerInfo);
                break;
            case MSG_CHECK_VISIBLE_APP:
                ArrayList<String> visibleAppList = (ArrayList<String>)msg.obj;
                handleUpdateVisibleAppList(visibleAppList);
                break;
            case MSG_ADD_CALLER_INFO:
                callerInfo = (CallerAppInfo)msg.obj;
                handleAddAutoStartCallerInfo(callerInfo);
                break;
            case MSG_CLEAR_CALLER_INFO:
                String appName = (String)msg.obj;
                handleClearAutoStartCallerInfo(appName);
                break;
            }
        }
    }

    void addAutoStartCallerInfo(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason) {

        CallerAppInfo callerInfo = new CallerAppInfo(targetApp, callerApp, callerUid, reason, intent);

        mHandler.sendMessage(mHandler.obtainMessage(MSG_ADD_CALLER_INFO, callerInfo));
    }

    void clearAutoStartCallerInfo(String targetApp) {
        if (targetApp == null) return;
        if (mAutoStartMapList.size() == 0) return;
        mHandler.sendMessage(mHandler.obtainMessage(MSG_CLEAR_CALLER_INFO, targetApp));
    }

    boolean hasPendingAutoStartCallerInfo(String targetApp) {
        if (targetApp == null) return false;
        for (int i=0;i<mAutoStartMapList.size();i++) {
            CallerAppInfo callerInfo =  mAutoStartMapList.keyAt(i);
            int state = mAutoStartMapList.valueAt(i);
            if (callerInfo != null && targetApp.equals(callerInfo.mTargetPackageName)
                && state == LAUNCH_STATE_NONE
                && callerInfo.mState == CALLERINFO_STATE_VALID) {
                return true;
            }
        }
        return false;
    }

    void updateVisibleAppList(ArrayList<String> visibleAppList) {
        if (visibleAppList == null) return;
        mHandler.sendMessage(mHandler.obtainMessage(MSG_CHECK_VISIBLE_APP, visibleAppList));
    }

    boolean isAutoStartAllowed(Intent intent, String targetApp, int targetUid,
            String callerApp, int callerUid, String reason) {
        CallerAppInfo callerInfo = new CallerAppInfo(targetApp, callerApp, callerUid, reason, intent);
        int index = mAutoStartMapList.indexOfKey(callerInfo);
        if (index >= 0) {
            CallerAppInfo savedCallerInfo = mAutoStartMapList.keyAt(index);
            int state = mAutoStartMapList.valueAt(index);
            if (savedCallerInfo.mState == CALLERINFO_STATE_VALID
                && state == LAUNCH_STATE_DENY) {
                if (DEBUG) Slog.d(TAG, "isAutoStartAllowed DENY Auto start :" + targetApp + " from " + callerApp
                    + " reason:" + reason + " intent:" + intent);
                return false;
            }
        }

        return true;
    }

    private void handleAddAutoStartCallerInfo(CallerAppInfo callerInfo) {
        if (callerInfo == null) return;

        int index = mAutoStartMapList.indexOfKey(callerInfo);
        if (index < 0) {
            if (DEBUG) Slog.d(TAG, "handleAddAutoStartCallerInfo new Auto start :" + callerInfo);
            mAutoStartMapList.put(callerInfo, LAUNCH_STATE_NONE);
        } else {
            CallerAppInfo oldCallerInfo = mAutoStartMapList.keyAt(index);
            if (oldCallerInfo.mState == CALLERINFO_STATE_INVALID) {
                oldCallerInfo.mState = CALLERINFO_STATE_VALID;
                if (DEBUG) Slog.d(TAG, "handleAddAutoStartCallerInfo update Auto start :" + oldCallerInfo);
                mAutoStartMapList.put(oldCallerInfo, LAUNCH_STATE_NONE);
            }
        }

        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_CHECK_CALLER_INFO, callerInfo), 30*1000);
    }

    private void handleClearAutoStartCallerInfo(String targetApp) {
        if (targetApp == null) return;
        if (DEBUG) Slog.d(TAG, "handleClearAutoStartCallerInfo :" + targetApp);
        for (int i=0;i<mAutoStartMapList.size();i++) {
            CallerAppInfo callerInfo =  mAutoStartMapList.keyAt(i);
            int state = mAutoStartMapList.valueAt(i);
            if (callerInfo != null && targetApp.equals(callerInfo.mTargetPackageName)
                && state == LAUNCH_STATE_NONE) {
                callerInfo.mState = CALLERINFO_STATE_INVALID;
                mAutoStartMapList.put(callerInfo, state);
            }
        }
    }

    private void handleUpdateVisibleAppList(ArrayList<String> visibleAppList) {
        if (visibleAppList == null) return;
        mVisibleAppList.clear();
        for (int i=0;i<visibleAppList.size();i++) {
            mVisibleAppList.add(visibleAppList.get(i));

            for (int inx=0;inx<mAutoStartMapList.size();inx++) {
                CallerAppInfo callerInfo =  mAutoStartMapList.keyAt(inx);
                int state = mAutoStartMapList.valueAt(inx);
                if (callerInfo != null && callerInfo.mState == CALLERINFO_STATE_VALID
                    && visibleAppList.get(i).equals(callerInfo.mTargetPackageName)
                    && state == LAUNCH_STATE_NONE) {
                    if (DEBUG) Slog.d(TAG, "handleUpdateVisibleAppList ALLOW Auto start :" + callerInfo);
                    mAutoStartMapList.put(callerInfo, LAUNCH_STATE_ALLOW);
                }
            }
        }
    }

    private void checkAutoStartCallerInfo(CallerAppInfo callerInfo) {
        if (callerInfo == null) return;

        int state = mAutoStartMapList.get(callerInfo);
        int index = mAutoStartMapList.indexOfKey(callerInfo);
        if (index < 0) {
            if (DEBUG) Slog.d(TAG, "checkAutoStartCallerInfo NNOT SAVE Auto start :" + callerInfo.mTargetPackageName
                + " from " + callerInfo.mPackageName
                + " reason:" + callerInfo.mReason + " intent:" + callerInfo.mIntent);
        } else {
            CallerAppInfo savedCallerInfo = mAutoStartMapList.keyAt(index);
            if (savedCallerInfo == null
                || savedCallerInfo.mState != CALLERINFO_STATE_VALID) return;
            if (!isVisible(callerInfo.mTargetPackageName)
                && state == LAUNCH_STATE_NONE) {
                if (DEBUG) Slog.d(TAG, "checkAutoStartCallerInfo DENY Auto start :" + savedCallerInfo);
                mAutoStartMapList.put(callerInfo, LAUNCH_STATE_DENY);
            } else {
                if (DEBUG) Slog.d(TAG, "checkAutoStartCallerInfo ALLOW Auto start :" + savedCallerInfo);
                mAutoStartMapList.put(callerInfo, LAUNCH_STATE_ALLOW);
            }
        }
    }

    private boolean isVisible(String packageName) {
        boolean visible = false;
        int index = mVisibleAppList.indexOf(packageName);
        if (index >= 0) {
            visible = true;
        }
        return visible;
    }

    private boolean isDebug(){
        return Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug");
    }
}
