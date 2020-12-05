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
import android.content.pm.ServiceInfo;

import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.sprdpower.AppPowerSaveConfig;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
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

import com.android.internal.app.HeavyWeightSwitcherActivity;
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

import android.os.sprdpower.Util;

public class AppInfoUtil {

    private static final String TAG = "SSense.AppInfoUtil";


    private final boolean DEBUG = isDebug();
    private final boolean DEBUG_MORE = false;
    private final boolean TEST = PowerController.TEST;

    // using disable component instead of disable the whole app
    // when enter/exit ultra-saving mode. See bug#819868
    private static final boolean USE_COMPONENT = true;

    static AppInfoUtil sInstance;

    private UserManager mUserManager;
    private int mCurrentUserId = 0;

    private final Context mContext;

    private Handler mHandler;


    private boolean mBootCompleted = false;

    // Apps that are visible
    private ArrayList<String> mVisibleAppList = new ArrayList<>();
    private ArrayList<String> mRemovedVisibleAppList = new ArrayList<>();

    //private Map<String, PackageInfo> mInstalledAppList = new ArrayMap<>();
    private SparseArray<ArrayMap<String, PackageInfo>> mInstalledAppListForUsers = new SparseArray<>();

    // the install third-party service that is app with out launcher entry
    private ArrayList<String> mInstalledServiceList = new ArrayList<>();

    // the install third-party admin app list, such com.baidu.appsearch
    private ArrayList<String> mInstalledAdminAppList = new ArrayList<>();

    // list save the notification intent info, the key is the target app
    private final ArrayMap<String, ArrayList<Intent>> mNotificationIntentList = new ArrayMap<>();


    // permissions that admin app must have
    private String[] mAdminAppPermissionList = new String[] {
        "android.permission.GET_PACKAGE_SIZE",
        "android.permission.KILL_BACKGROUND_PROCESSES",
        "android.permission.PACKAGE_USAGE_STATS",
        "android.permission.CLEAR_APP_CACHE"
    };



    public static AppInfoUtil getInstance(Context context) {
        synchronized (AppInfoUtil.class) {
            if (sInstance == null) {
                sInstance = new AppInfoUtil(context);
            }
            return sInstance;
        }
    }


    public AppInfoUtil(Context context) {
        mContext = context;
        mHandler = new Handler(SmartSenseService.BackgroundThread.get().getLooper());


        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);

        registerForBroadcasts();
    }

    void loadInstalledPackages(){

        try {
            boolean needUpdateGmsProperty = false;
            boolean hasGms = SystemProperties.getBoolean("persist.sys.gms", false);
            final List<UserInfo> users = mUserManager.getUsers();
            for (int ui = users.size() - 1; ui >= 0; ui--) {
                UserInfo user = users.get(ui);
                if (DEBUG) Slog.d(TAG, "- loadInstalledPackages() for user: " + user.id);
                ArrayMap<String, PackageInfo> mInstalledAppList = getInstalledAppList(user.id);

                List<PackageInfo> packages = mContext.getPackageManager().getInstalledPackagesAsUser(0, user.id);
                for(PackageInfo pkg : packages){
                    if (DEBUG) Slog.d(TAG, "PKG:" + pkg.packageName + " Flag:" + Integer.toHexString(pkg.applicationInfo.flags));

                    if(pkg !=null && pkg.applicationInfo !=null
                        && (pkg.applicationInfo.flags &
                           (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP |ApplicationInfo.FLAG_SYSTEM)) == 0) {
                       if (!mInstalledAppList.containsKey(pkg.packageName)) {
                           mInstalledAppList.put(pkg.packageName, pkg);
                           checkPermissionList(pkg.packageName, user.id);
                       }
                    }

                    // a gms version
                    if (pkg !=null && pkg.applicationInfo != null
                        && "com.google.android.gms".equals(pkg.packageName)
                        && (pkg.applicationInfo.flags & (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP | ApplicationInfo.FLAG_SYSTEM)) != 0) {
                        if (!hasGms) {
                            needUpdateGmsProperty = true;
                            hasGms = true;
                        }
                    }

                }

                if (DEBUG) {
                    Slog.d(TAG, "mInstalledAppList: " + mInstalledAppList.size());
                    for (String key : mInstalledAppList.keySet()) {
                        Slog.d(TAG, "App:" + key);
                    }

                    Slog.d(TAG, "mInstalledAdminAppList: " + mInstalledAdminAppList.size());
                    for (int i=0;i<mInstalledAdminAppList.size();i++) {
                        Slog.d(TAG, "App:" + mInstalledAdminAppList.get(i));
                    }

                }
            }

            if (needUpdateGmsProperty) {
                SystemProperties.set("persist.sys.gms", (hasGms?"1":"0"));
            }
        } catch (Exception e) {
        }
    }

    void onBootCompleted() {
        loadInstalledServiceList();

        mBootCompleted = true;
    }

    static boolean isSystemApp(AppState appState) {
        if (appState != null) {
            return ((appState.mFlags & (ApplicationInfo.FLAG_SYSTEM |ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                || appState.mFlags == 0);
        } else {
            return true; // default
        }
    }

    boolean isInstalledApp(String pkgName, int userId) {
        ArrayMap<String, PackageInfo> mInstalledAppList = getInstalledAppList(userId);

        PackageInfo targetPkg = mInstalledAppList.get(pkgName);
        if (targetPkg != null) {
            return true;
        }
        return false;
    }


    ArrayList<String> getInstallAppList(int userId) {
        ArrayMap<String, PackageInfo> mInstalledAppList = getInstalledAppList(userId);

        ArrayList<String> appList = new ArrayList<String>();
        for (String key : mInstalledAppList.keySet()) {
            appList.add(key);
        }

        return appList;
    }





    private void loadInstalledServiceList() {
        try {
            final List<UserInfo> users = mUserManager.getUsers();
            for (int ui = users.size() - 1; ui >= 0; ui--) {
                UserInfo user = users.get(ui);
                if (DEBUG) Slog.d(TAG, "- loadInstalledServiceList() for user: " + user.id);
                ArrayMap<String, PackageInfo> mInstalledAppList = getInstalledAppList(user.id);
                if (DEBUG) Slog.d(TAG, "get launcher intent for installed app : size: " + mInstalledAppList.size());
                for (String key : mInstalledAppList.keySet()) {
                    if (mContext.getPackageManager().getLaunchIntentForPackage(key) == null
                        && !mInstalledServiceList.contains(key)
                        && getAppLauncherEnabledSetting(key, mCurrentUserId) == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ) {
                        mInstalledServiceList.add(key);
                    }
                }
            }
        } catch (Exception e) {
        }

        if (DEBUG) {
            Slog.d(TAG, "mInstalledServiceList: " + mInstalledServiceList.size());
            for (int i=0;i<mInstalledServiceList.size();i++) {
                Slog.d(TAG, "App:" + mInstalledServiceList.get(i));
            }
        }
    }

    private void registerForBroadcasts() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        intentFilter.addDataScheme("package");

        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (Intent.ACTION_PACKAGE_ADDED.equals(action)
                        ||Intent.ACTION_PACKAGE_REPLACED.equals(action) ) {
                        Uri data = intent.getData();
                        String pkgName = data.getEncodedSchemeSpecificPart();
                        scheduleUpdateInstalledPackages(pkgName);
                    } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                        if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                            Uri data = intent.getData();
                            String ssp;
                            if (data != null && (ssp = data.getSchemeSpecificPart()) != null) {
                                scheduleUpdateRemovedPackages(ssp);
                            }
                        }
                    }
                }
            }, intentFilter);


        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                        mCurrentUserId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0);
                        Slog.d(TAG, "ACTION_USER_SWITCHED : mCurrentUserId:" + mCurrentUserId);
                        mHandler.post(new Runnable() {
                            public void run() {
                                try {
                                    onUserSwitched();
                                } catch (Exception e) {}
                            }
                        });
                    }
                }
            }, filter);

        IntentFilter sdcardfilter = new IntentFilter();
        sdcardfilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        sdcardfilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    Slog.d(TAG, "listener SD Card mounted,action: " + action);
                    if (Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(action)) {
                        Slog.d(TAG, "SD Card mounted, update installed packages list ");
                        loadInstalledPackages();
                    }
                }
          }, sdcardfilter);

    }

    private void scheduleUpdateInstalledPackages(String newPkgName) {
        mHandler.post(new Runnable() {
            public void run() {
                try {
                    PackageInfo pkg = mContext.getPackageManager().getPackageInfoAsUser(
                        newPkgName, 0, mCurrentUserId);

                    ArrayMap<String, PackageInfo> mInstalledAppList = getInstalledAppList(mCurrentUserId);

                    if(pkg !=null && pkg.applicationInfo !=null
                        && (pkg.applicationInfo.flags &
                           (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP |ApplicationInfo.FLAG_SYSTEM)) == 0) {
                        Slog.d(TAG, "new APK:" + pkg.packageName + " is installed! for userId:" + mCurrentUserId);
                        mInstalledAppList.put(pkg.packageName, pkg);
                    }

                    if (pkg !=null && pkg.packageName != null
                        && mContext.getPackageManager().getLaunchIntentForPackage(pkg.packageName) == null
                        && !mInstalledServiceList.contains(pkg.packageName)) {
                        mInstalledServiceList.add(pkg.packageName);
                    }

                    checkPermissionList(newPkgName, mCurrentUserId);
                } catch (Exception e) {}
            }
        });
    }

    private void scheduleUpdateRemovedPackages(String pkgName) {
        mHandler.post(new Runnable() {
            public void run() {
                try {
                        Slog.d(TAG, "remove APK:" + pkgName);
                } catch (Exception e) {}
            }
        });
    }

    private void onUserSwitched() {
    }

    private int getAppLauncherEnabledSetting(String packageName, int userId) {

        if (USE_COMPONENT) {
            ComponentName launcherComp = null;
            int launcherState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setPackage(packageName);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);

            ResolveInfo info = mContext.getPackageManager().resolveActivityAsUser(intent,
                PackageManager.MATCH_DISABLED_COMPONENTS, mCurrentUserId);

            if (DEBUG) Slog.d(TAG, "getApplicationLauncherEnabledSetting: "
                + " appPackageName:" + packageName
                + " launcher info:" + info);
            if (info != null && info.activityInfo != null) {
                launcherComp = new ComponentName(packageName, info.activityInfo.name);
                try {
                    launcherState = AppGlobals.getPackageManager().getComponentEnabledSetting(launcherComp, userId);
                } catch (Exception e) {
                    Slog.d(TAG, "getApplicationLauncherEnabledSetting Exception:" + e);
                }
            }
            return launcherState;
        } else {
            int enabled = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;

            try {
                enabled = AppGlobals.getPackageManager().getApplicationEnabledSetting(packageName, userId);
            } catch (Exception e) {
                Slog.d(TAG, "getApplicationLauncherEnabledSetting Exception:" + e);
            }
            return enabled;
        }
    }

    private void checkPermissionList(String packageName, int userId) {
        PackageManager pm = mContext.getPackageManager();
        PackageInfo pi;
        try {
            // use PackageManager.GET_PERMISSIONS
            pi = pm.getPackageInfoAsUser(packageName, PackageManager.GET_PERMISSIONS, userId);
            String[] permissions = pi.requestedPermissions;
            if(permissions != null){
                if (DEBUG_MORE) {
                    Slog.d(TAG, "Permission for app:" + packageName);
                    for(String str : permissions) {
                        Slog.d(TAG, str);
                    }
                }
                if (checkAdminAppPermissionList(permissions)) {
                    Slog.d(TAG, "ADMIN APP:" + packageName);
                    if (!mInstalledAdminAppList.contains(packageName))
                        mInstalledAdminAppList.add(packageName);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private boolean checkAdminAppPermissionList(String[] permissions) {
        for (String p: mAdminAppPermissionList) {
            boolean found = false;
            for (String str: permissions) {
                if (str.equals(p)) found = true;
            }
            if (!found) return false;
        }
        return true;
    }

    // if a app is a type of admin app, such as com.baidu.appsearch
    boolean isAdminApp(String packageName) {
        if (packageName == null) return false;

        return (mInstalledAdminAppList.contains(packageName));
    }


    private boolean isDebug(){
        return Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug");
    }


    private void dumpInstalledAppList() {
        for (int index=mInstalledAppListForUsers.size()-1; index>=0; index--) {
            ArrayMap<String, PackageInfo> mInstalledAppList = mInstalledAppListForUsers.valueAt(index);

            Slog.d(TAG, "mInstalledAppList for userId: " + mInstalledAppListForUsers.keyAt(index));
            for (String key : mInstalledAppList.keySet()) {
                Slog.d(TAG, "App:" + key);
            }
        }

    }

    private ArrayMap<String, PackageInfo> getInstalledAppList(int userId) {
        ArrayMap<String, PackageInfo> mInstalledAppList = mInstalledAppListForUsers.get(userId);
        if (mInstalledAppList == null) {
            mInstalledAppList = new ArrayMap<>();
            mInstalledAppListForUsers.put(userId, mInstalledAppList);
        }
        return mInstalledAppList;
    }
}
