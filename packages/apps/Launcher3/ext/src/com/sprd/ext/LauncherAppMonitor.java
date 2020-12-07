package com.sprd.ext;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.UserHandle;

import com.android.launcher3.AppInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.graphics.IconShapeOverride;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.sprd.ext.circularslide.CycleScrollController;
import com.sprd.ext.folder.FolderIconController;
import com.sprd.ext.gestures.GesturesController;
import com.sprd.ext.multimode.MultiModeController;
import com.sprd.ext.navigationbar.NavigationBarController;
import com.sprd.ext.unreadnotifier.UnreadInfoController;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Created by SPRD on 2017/6/21.
 */

public class LauncherAppMonitor implements SharedPreferences.OnSharedPreferenceChangeListener,
        LauncherAppsCompat.OnAppsChangedCallbackCompat {
    private static final String TAG = "LauncherAppMonitor";

    private final ArrayList<WeakReference<LauncherAppMonitorCallback>> mCallbacks = new ArrayList<>();

    private Launcher mLauncher;
    private static LauncherAppMonitor INSTANCE;
    private UnreadInfoController mUnreadInfoController;
    private FolderIconController mFolderIconController;
    private MultiModeController mMultiModeController;
    private GesturesController mGesturesController;
    private CycleScrollController mCycleScrollController;
    private NavigationBarController mNavigationBarController;

    public static LauncherAppMonitor getInstance(final Context context) {
        if (INSTANCE == null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                INSTANCE = new LauncherAppMonitor(context.getApplicationContext());
            } else {
                try {
                    return new MainThreadExecutor().submit( new Callable<LauncherAppMonitor>() {
                        @Override
                        public LauncherAppMonitor call() throws Exception {
                            return LauncherAppMonitor.getInstance(context);
                        }
                    }).get();
                } catch (InterruptedException|ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return INSTANCE;
    }

    public static LauncherAppMonitor getInstanceNoCreate() {
        return INSTANCE;
    }

    //return null while launcher activity isn't running
    public Launcher getLauncher() {
        return mLauncher;
    }

    /**
     * Remove the given observer's callback.
     *
     * @param callback The callback to remove
     */
    public void unregisterCallback(LauncherAppMonitorCallback callback) {
        if (LogUtils.DEBUG_LOADER) {
            UtilitiesExt.DEBUG_PRINT_FUNCTIONNAME("unregisterCallback:" + callback);
        }
        for (int i = mCallbacks.size() - 1; i >= 0; i--) {
            if (mCallbacks.get(i).get() == callback) {
                mCallbacks.remove(i);
            }
        }
    }

    /**
     * Register to receive notifications about general Launcher app information
     * @param callback The callback to register
     */
    public void registerCallback(LauncherAppMonitorCallback callback) {
        if (LogUtils.DEBUG_LOADER) {
            UtilitiesExt.DEBUG_PRINT_FUNCTIONNAME("registerCallback:" + callback);
        }
        // Prevent adding duplicate callbacks
        for (int i = 0; i < mCallbacks.size(); i++) {
            if (mCallbacks.get(i).get() == callback) {
                return;
            }
        }
        mCallbacks.add(new WeakReference<>(callback));
//        unregisterCallback(null); // remove unused references
    }

    private LauncherAppMonitor(Context context) {
        if (LogUtils.DEBUG_LOADER) {
            UtilitiesExt.DEBUG_PRINT_FUNCTIONNAME("LauncherAppMonitor init");
        }
        LauncherAppsCompat.getInstance(context).addOnAppsChangedCallback(this);
        Utilities.getPrefs(context).registerOnSharedPreferenceChangeListener(this);

        if (FeatureOption.SPRD_MULTI_MODE_SUPPORT) {
            mMultiModeController = new MultiModeController(context, this);
        }

        if(FeatureOption.SPRD_BADGE_SUPPORT) {
            mUnreadInfoController = new UnreadInfoController(context, this);
        }

        if(FeatureOption.SPRD_FOLDER_ICON_MODE_SUPPORT) {
            mFolderIconController = new FolderIconController(context, this);
        }

        if (FeatureOption.SPRD_GESTURE_SUPPORT) {
            mGesturesController = new GesturesController(context, this);
        }

        if (FeatureOption.SPRD_CYCLE_SCROLL_SUPPORT) {
            mCycleScrollController = new CycleScrollController(context, this);
        }

        if (NavigationBarController.hasNavigationBar()) {
            mNavigationBarController = new NavigationBarController(context, this);
        }
    }

    private static void DEBUG_PRINT_FUNCTIONNAME() {
        UtilitiesExt.DEBUG_PRINT_FUNCTIONNAME(UtilitiesExt.BASE_STACK_DEPTH, null);
    }

    private static void DEBUG_PRINT_FUNCTIONNAME(String msg) {
        UtilitiesExt.DEBUG_PRINT_FUNCTIONNAME(UtilitiesExt.BASE_STACK_DEPTH, msg);
    }

    public void onLauncherPreCreate(Launcher launcher) {
        if (LogUtils.DEBUG_LOADER) {
            DEBUG_PRINT_FUNCTIONNAME( );
        }
        mLauncher = launcher;

        PendingIntent pi = IconShapeOverride.getHomePendingIntent(launcher);
        launcher.getSystemService(AlarmManager.class).cancel(pi);

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onLauncherPreCreate(launcher);
            }
        }
    }

    public void onLauncherCreated() {
        if (LogUtils.DEBUG_LOADER) {
            DEBUG_PRINT_FUNCTIONNAME( );
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onLauncherCreated();
            }
        }
    }

    public void onLauncherPreResume() {
        if (LogUtils.DEBUG_LOADER) {
            DEBUG_PRINT_FUNCTIONNAME( );
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onLauncherPreResume();
            }
        }
    }

    public void onLauncherResumed() {
        if (LogUtils.DEBUG_LOADER) {
            DEBUG_PRINT_FUNCTIONNAME( );
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onLauncherResumed();
            }
        }
    }

    public void onLauncherPrePause() {
        if (LogUtils.DEBUG_LOADER) {
            DEBUG_PRINT_FUNCTIONNAME( );
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onLauncherPrePaused();
            }
        }
    }

    public void onLauncherPaused() {
        if (LogUtils.DEBUG_LOADER) {
            DEBUG_PRINT_FUNCTIONNAME( );
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onLauncherPaused();
            }
        }
    }

    public void onLauncherStart() {
        if (LogUtils.DEBUG_LOADER) {
            DEBUG_PRINT_FUNCTIONNAME( );
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onLauncherStart();
            }
        }
    }

    public void onLauncherStop() {
        if (LogUtils.DEBUG_LOADER) {
            DEBUG_PRINT_FUNCTIONNAME( );
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onLauncherStop();
            }
        }
    }

    public void onLauncherDestroy() {
        if (LogUtils.DEBUG_LOADER) {
            DEBUG_PRINT_FUNCTIONNAME( );
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onLauncherDestroy();
            }
        }
        mLauncher = null;
    }

    public void onLauncherRequestPermissionsResult(int requestCode, String[] permissions,
                                                   int[] grantResults) {
        DEBUG_PRINT_FUNCTIONNAME("rC:" + requestCode + " ret:" + Arrays.toString(grantResults));

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onLauncherRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }

    public void onLauncherFocusChanged(boolean hasFocus) {
        if (LogUtils.DEBUG) {
            DEBUG_PRINT_FUNCTIONNAME("hasFocus:" + hasFocus);
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onLauncherFocusChanged(hasFocus);
            }
        }
    }

    public void onReceiveHomeIntent() {
        if (LogUtils.DEBUG_RECEIVER) {
            DEBUG_PRINT_FUNCTIONNAME( );
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onHomeIntent();
            }
        }
    }

    public void onLauncherWorkspaceBindingFinish () {
        if (LogUtils.DEBUG_LOADER) {
            DEBUG_PRINT_FUNCTIONNAME( );
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onBindingWorkspaceFinish();
            }
        }
    }

    public void onLauncherAllAppBindingFinish (ArrayList<AppInfo> apps) {
        if (LogUtils.DEBUG_LOADER) {
            DEBUG_PRINT_FUNCTIONNAME("AllApps size:"+apps.size());
        }
        if (LogUtils.DEBUG_ALL) {
            for (AppInfo app : apps) {
                LogUtils.d("Load app ", app.toComponentKey().toString() + "\n");
            }
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onBindingAllAppFinish(apps);
            }
        }
    }

    @Override
    public void onPackageRemoved(String packageName, UserHandle user) {
        if (LogUtils.DEBUG_RECEIVER) {
            DEBUG_PRINT_FUNCTIONNAME("pkgName:" + packageName +
                    " user:" + user.toString( ));
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onPackageRemoved(packageName, user);
            }
        }
    }

    @Override
    public void onPackageAdded(String packageName, UserHandle user) {
        if (LogUtils.DEBUG_RECEIVER) {
            DEBUG_PRINT_FUNCTIONNAME("pkgName:" + packageName
                    + " user:" + user.toString( ));
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onPackageAdded(packageName, user);
            }
        }
    }

    @Override
    public void onPackageChanged(String packageName, UserHandle user) {
        if (LogUtils.DEBUG_RECEIVER) {
            DEBUG_PRINT_FUNCTIONNAME("pkgName:" + packageName
                    + " user:" + user.toString( ));
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onPackageChanged(packageName, user);
            }
        }
    }

    @Override
    public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
        if (LogUtils.DEBUG_RECEIVER) {
            DEBUG_PRINT_FUNCTIONNAME(
                    "packageNames:" + Arrays.toString(packageNames)
                            + " user:" + user.toString( ) + " replacing:" + replacing);
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onPackagesAvailable(packageNames, user, replacing);
            }
        }
    }

    @Override
    public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
        if (LogUtils.DEBUG_RECEIVER) {
            DEBUG_PRINT_FUNCTIONNAME(
                    "packageNames:" + Arrays.toString(packageNames)
                            + " user:" + user.toString( ) + " replacing:" + replacing);
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onPackagesUnavailable(packageNames, user, replacing);
            }
        }
    }

    @Override
    public void onPackagesSuspended(String[] packageNames, UserHandle user) {
        if (LogUtils.DEBUG_RECEIVER) {
            DEBUG_PRINT_FUNCTIONNAME(
                    "packageNames:" + Arrays.toString(packageNames) + " user:" + user.toString( ));
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onPackagesSuspended(packageNames, user);
            }
        }
    }

    @Override
    public void onPackagesUnsuspended(String[] packageNames, UserHandle user) {
        if (LogUtils.DEBUG_RECEIVER) {
            DEBUG_PRINT_FUNCTIONNAME(
                    "packageNames:" + Arrays.toString(packageNames) + " user:" + user.toString( ));
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onPackagesUnsuspended(packageNames, user);
            }
        }
    }

    @Override
    public void onShortcutsChanged(String packageName, List<ShortcutInfoCompat> shortcuts, UserHandle user) {
        if (LogUtils.DEBUG_RECEIVER) {
            DEBUG_PRINT_FUNCTIONNAME(
                    "packageNames" + packageName + " shortcuts:" + shortcuts.toString( )
                            + " user:" + user.toString( ));
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onShortcutsChanged(packageName, shortcuts, user);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (LogUtils.DEBUG_RECEIVER) {
            DEBUG_PRINT_FUNCTIONNAME("key:" + key);
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onAppSharedPreferenceChanged(key);
            }
        }
    }

    public void onModelReceive(Intent intent) {
        if (LogUtils.DEBUG_RECEIVER) {
            DEBUG_PRINT_FUNCTIONNAME("action:" + intent.getAction( ));
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onReceive(intent);
            }
        }
    }

    public void onAppCreated(Context context) {
        if (LogUtils.DEBUG_LOADER) {
            DEBUG_PRINT_FUNCTIONNAME( );
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onAppCreated(context);
            }
        }
    }

    public void onAppConfigChanged() {
        if (LogUtils.DEBUG_RECEIVER) {
            DEBUG_PRINT_FUNCTIONNAME( );
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onConfigChanged();
            }
        }
    }

    public void onAppIconShapeChanged(String newValue) {
        if (LogUtils.DEBUG_RECEIVER) {
            DEBUG_PRINT_FUNCTIONNAME("newValue:" + newValue);
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherAppMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onIconShapeChanged(newValue);
            }
        }
    }

    public FolderIconController getFolderIconController() {
        return mFolderIconController;
    }

    public MultiModeController getMultiModeController() {
        return mMultiModeController;
    }

    public GesturesController getGesturesController() {
        return mGesturesController;
    }

    public CycleScrollController getCycleScrollController() {
        return mCycleScrollController;
    }

    public UnreadInfoController getUnreadInfoController() {
        return mUnreadInfoController;
    }
}
