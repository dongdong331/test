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
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.inputmethod.InputMethodInfo;

import com.android.internal.view.IInputMethodManager;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

import android.os.sprdpower.Util;

public class AppStateInfoCollector {

    static final String TAG = "PowerController.AppState";

    private final boolean DEBUG = Util.isDebug() || Util.getDebugLog();

    static AppStateInfoCollector sInstance;

    //private ArrayMap<String, AppState> mAppStateInfoList = new ArrayMap<>();
    // for all users and all app
    private SparseArray<ArrayMap<String,AppState>> mUserAppStateInfoList = new SparseArray<>();

    // uid state
    private final SparseIntArray mUidState = new SparseIntArray();
    private final Object mUidStateLock = new Object();

    private final Context mContext;

    private SparseArray<String> mDefaultIMEAppNameForUsers = new SparseArray<>();

    public static AppStateInfoCollector getInstance(Context context) {
        synchronized (AppStateInfoCollector.class) {
            if (sInstance == null) {
                sInstance = new AppStateInfoCollector(context);
            }
            return sInstance;
        }
    }

    public AppStateInfoCollector(Context context) {
        mContext = context;
    }

    // return true: for new app state
    //     false: for others
    public boolean reportAppStateEventInfo(String packageName, int userId, int stateEvent) {
        ArrayMap<String, AppState> mAppStateInfoList = getAppStateInfoList(userId);

        //update mAppStateInfoList
        int index = mAppStateInfoList.indexOfKey(packageName);
        AppState appState = null;
        boolean ret = true;

        if (DEBUG) Slog.d(TAG, "- reportAppStateEventInfo() E -");

        if (index >= 0) {
            appState = mAppStateInfoList.valueAt(index);
            appState.updateAppState(stateEvent);
            ret = false;
        } else {
            appState = buildAppState(packageName, userId, stateEvent);
            mAppStateInfoList.put(packageName, appState);
        }

        return ret;
    }

    // return true: for new app state
    //     false: for others
    public boolean reportAppProcStateInfo(String packageName, int uid, int procState) {
        int userId = UserHandle.getUserId(uid);
        ArrayMap<String, AppState> mAppStateInfoList = getAppStateInfoList(userId);

        //update mAppStateInfoList
        int index = mAppStateInfoList.indexOfKey(packageName);
        AppState appState = null;
        boolean ret = true;

        if (DEBUG) Slog.d(TAG, "- reportAppProcStateInfo() E -");

        if (index >= 0) {
            appState = mAppStateInfoList.valueAt(index);
            // update procState
            appState.mProcState = procState;
            if (uid != appState.mUid) appState.mUid = uid;
            ret = false;
        } else {
            if (DEBUG) Slog.d(TAG, "reportAppProcStateInfo: appName:" + packageName + " uid:" + uid + " is not exist, create it");
            appState = buildAppState(packageName, userId, Event.NONE);
            mAppStateInfoList.put(packageName, appState);
        }

        return ret;
    }

    public AppState getAppState(String pkgName, int userId) {
        ArrayMap<String, AppState> mAppStateInfoList = getAppStateInfoList(userId);
        int index = mAppStateInfoList.indexOfKey(pkgName);
        if (index >= 0) {
            AppState appState = mAppStateInfoList.valueAt(index);
            return appState;
        } else {
            return null;
        }
    }

    public AppState getAppState(int uid) {
        int userId = UserHandle.getUserId(uid);
        ArrayMap<String, AppState> mAppStateInfoList = getAppStateInfoList(userId);
        for (int i=0;i<mAppStateInfoList.size();i++) {
            AppState appState = mAppStateInfoList.valueAt(i);

            if (appState.mUid == uid)
                return appState;
        }
        return null;
    }

    // to sync with mAppStateInfoList if this api is called by other thread ??
    public int getCountOfActiveLaunchedApps(int userId) {
        ArrayMap<String, AppState> mAppStateInfoList = getAppStateInfoList(userId);
        int count = 0;
        for (int i=0;i<mAppStateInfoList.size();i++) {
            AppState appState = mAppStateInfoList.valueAt(i);
            if (appState.mLaunchCount > 0
                && appState.mProcState != ActivityManager.PROCESS_STATE_CACHED_EMPTY
                && appState.mProcState != ActivityManager.PROCESS_STATE_NONEXISTENT) {
                count++;
            }
        }
        return count;
    }

    // update the Input Method identify state of this app
    public void updateAppInputMethodState(String pkgName, boolean isInputMethod, int userId) {
        ArrayMap<String, AppState> mAppStateInfoList = getAppStateInfoList(userId);
        int index = mAppStateInfoList.indexOfKey(pkgName);
        if (index >= 0) {
            AppState appState = mAppStateInfoList.valueAt(index);
            appState.mIsEnabledInputMethod = isInputMethod;
        }
    }

    // update the Input Method identify state of this app
    public void setDefaultInputMethodApp(String pkgName, int userId) {
        ArrayMap<String, AppState> mAppStateInfoList = getAppStateInfoList(userId);
        String mDefaultIMEAppName =  mDefaultIMEAppNameForUsers.get(userId);

        if (mDefaultIMEAppName != null
            && mDefaultIMEAppName.equals(pkgName)) {
            return;
        }

        // clear orignal
        if (mDefaultIMEAppName != null) {
            int index = mAppStateInfoList.indexOfKey(mDefaultIMEAppName);
            if (index >= 0) {
                AppState appState = mAppStateInfoList.valueAt(index);
                appState.mIsDefaultInputMethod = false;
            }
        }

        // set new
        int index = mAppStateInfoList.indexOfKey(pkgName);
        if (index >= 0) {
            AppState appState = mAppStateInfoList.valueAt(index);
            appState.mIsDefaultInputMethod = true;
        }

        mDefaultIMEAppName = pkgName;
        mDefaultIMEAppNameForUsers.put(userId, mDefaultIMEAppName);
    }

    public void updateUidState(int uid, int uidState) {
        synchronized (mUidStateLock) {
            final int oldUidState = mUidState.get(uid, ActivityManager.PROCESS_STATE_CACHED_EMPTY);
            if (oldUidState != uidState) {
                // state changed, push updated rules
                mUidState.put(uid, uidState);
            }
        }
    }

    public void removeUidState(int uid) {
        synchronized (mUidStateLock) {
            final int index = mUidState.indexOfKey(uid);
            if (index >= 0) {
                final int oldUidState = mUidState.valueAt(index);
                mUidState.removeAt(index);
            }
        }
    }

    public void updateNotification(String packageName, int uid) {
        int userId = UserHandle.getUserId(uid);
        ArrayMap<String, AppState> mAppStateInfoList = getAppStateInfoList(userId);

        //update mAppStateInfoList
        int index = mAppStateInfoList.indexOfKey(packageName);
        AppState appState = null;

        if (DEBUG) Slog.d(TAG, "- updateNotification() E -");

        if (index >= 0) {
            appState = mAppStateInfoList.valueAt(index);

            if (appState != null) {
                appState.updateActiveNotificationState(mContext);
            }
        }
    }

    void handleVisibleAppChanged(ArrayList<String> visibleAppList, int userId) {
        if (visibleAppList == null) return;
        ArrayMap<String, AppState> mAppStateInfoList = getAppStateInfoList(userId);
        if (mAppStateInfoList == null) {
            Slog.w(TAG, "handleVisibleAppChanged: is null for user:" + userId);
            return;
        }

        for (int i=0;i<mAppStateInfoList.size();i++) {
            AppState appState = mAppStateInfoList.valueAt(i);
            if (visibleAppList.contains(appState.mPackageName)) {
                appState.updateVisibleState(true);
            } else {
                appState.updateVisibleState(false);
            }
        }
    }

    public void dump() {
        for (int index=mUserAppStateInfoList.size()-1; index>=0; index--) {
            ArrayMap<String, AppState> mAppStateInfoList = mUserAppStateInfoList.valueAt(index);
            int userId = mUserAppStateInfoList.keyAt(index);

            // Strange:: why mAppStateInfoList is null bug#756774
            if (mAppStateInfoList == null) {
                Slog.w(TAG, "mAppStateInfoList: is null for index:" + index + " for user:" + userId);
                break;
            }
            Slog.d(TAG, "mAppStateInfoList: size:" + mAppStateInfoList.size() + " for user:" + userId);
            for (int i=0;i<mAppStateInfoList.size();i++) {
                AppState appState = mAppStateInfoList.valueAt(i);
                Slog.d(TAG, "AppState: appName:" + appState.mPackageName
                    + " uid:" + appState.mUid
                    + " procState:" + Util.ProcState2Str(appState.mProcState)
                    + " state:" + Util.AppState2Str(appState.mState));
            }
        }
    }

    private AppState buildAppState(String packageName, int userId, int stateEvent) {

        ApplicationInfo app = null;
        int uid = 0;
        int procState = ActivityManager.PROCESS_STATE_CACHED_EMPTY;
        int flags = 0;
        try {
            app = AppGlobals.getPackageManager().
                getApplicationInfo(packageName, 0, userId);
        } catch (RemoteException e) {
            // can't happen; package manager is process-local
        }

        if (app != null) {
            uid = app.uid;
            flags = app.flags;
            synchronized (mUidStateLock) {
                procState = mUidState.get(uid, ActivityManager.PROCESS_STATE_CACHED_EMPTY);
            }
        }

        AppState retVal = new AppState(packageName, userId, uid, stateEvent, procState, flags);

        // check if is input method
        retVal.mIsEnabledInputMethod = isEnabledIMEApp(packageName);

        //if (DEBUG) Slog.d(TAG, "- buildAppState() :" + packageName);

        return retVal;
    }


    // if this app is a input Method
    private boolean isEnabledIMEApp(String pkgName){
        if (pkgName == null) return false;
        IInputMethodManager service = IInputMethodManager.Stub.asInterface(
            ServiceManager.getService(Context.INPUT_METHOD_SERVICE));
        List<InputMethodInfo> inputMethods;
        try {
            inputMethods = service.getEnabledInputMethodList();
        } catch (RemoteException e) {
            return false;
        }
        if (inputMethods == null || inputMethods.size() == 0) return false;
        for (InputMethodInfo info : inputMethods){
            if (info == null || info.getPackageName() == null) continue;
            if (info.getPackageName().equals(pkgName)) return true;
        }
        return false;
    }

    public ArrayMap<String, AppState> getAppStateInfoList(int userId) {
        ArrayMap<String, AppState> mAppStateInfoList = mUserAppStateInfoList.get(userId);
        if (mAppStateInfoList == null) {
            mAppStateInfoList = new ArrayMap<>();
            mUserAppStateInfoList.put(userId, mAppStateInfoList);
        }
        return mAppStateInfoList;
    }
}
