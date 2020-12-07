package com.sprd.ext.multimode;

import static android.app.ProgressDialog.show;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.os.UserHandle;
import android.preference.Preference;
import android.util.Pair;

import com.android.launcher3.AppInfo;
import com.android.launcher3.InstallShortcutReceiver;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ItemInfoMatcher;
import com.sprd.ext.FeatureOption;
import com.sprd.ext.LauncherAppMonitor;
import com.sprd.ext.LauncherAppMonitorCallback;
import com.sprd.ext.LauncherSettingsExtension;
import com.sprd.ext.LogUtils;
import com.sprd.ext.RestartHomeApp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class MultiModeController implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "MultiModeController";

    private final Context mContext;

    private static boolean sIsSupportDynamicChange;

    private static boolean sIsSingleLayerMode;

    private static boolean sIsDefaultMode;

    private static final Map<ShortcutKey, ShortcutInfoCompat> sPreModeSavedShortcuts = new HashMap<>();

    public MultiModeController(Context context, LauncherAppMonitor monitor) {
        mContext = context;
        sIsSupportDynamicChange = MultiModeUtilities.isSupportDynamicChangeHomeScreenStyle(context);
        sIsSingleLayerMode = MultiModeUtilities.isSingleLayerMode(context);
        sIsDefaultMode = MultiModeUtilities.isDefaultMode(context);

        if (sIsSupportDynamicChange) {
            List<ShortcutInfoCompat> shortcuts = MultiModeUtilities.
                    readPreModelSavedShortcuts(context, sIsSingleLayerMode );
            for (ShortcutInfoCompat shortcut : shortcuts) {
                sPreModeSavedShortcuts.put(ShortcutKey.fromInfo(shortcut), shortcut);
            }
        }

        monitor.registerCallback(mAppMonitorCallback);
        LogUtils.i(TAG,"sIsSupportDynamicChange: " + sIsSupportDynamicChange
                +" sIsSingleLayerMode: " + sIsSingleLayerMode
                +" sIsDefaultMode: " + sIsDefaultMode);
    }

    public static boolean isSupportDynamicChange() {
        throwIfControllerNotInited();
        return sIsSupportDynamicChange;
    }

    public static boolean isSingleLayerMode() {
        throwIfControllerNotInited();
        return sIsSingleLayerMode;
    }

    public static boolean isDefaultMode() {
        throwIfControllerNotInited();
        return sIsDefaultMode;
    }

    public static Map<ShortcutKey, ShortcutInfoCompat> getsPreModeSavedShortcuts() {
        throwIfControllerNotInited();
        return sPreModeSavedShortcuts;
    }

    private static void throwIfControllerNotInited() {
        if (FeatureOption.SPRD_MULTI_MODE_SUPPORT) {
            LauncherAppMonitor Lam = LauncherAppMonitor.getInstanceNoCreate();
            if (Lam == null || Lam.getMultiModeController() == null) {
                throw new RuntimeException("MultiModeController is not init.");
            }
        }
    }

    private LauncherAppMonitorCallback mAppMonitorCallback = new LauncherAppMonitorCallback() {
        @Override
        public void onBindingAllAppFinish(ArrayList<AppInfo> apps) {
            if (sIsSingleLayerMode) {
                LauncherModel.runOnWorkerThread(
                        new VerifyTask(apps, null, null, false));
            }
        }

        @Override
        public void onPackageAdded(String packageName, UserHandle user) {
            if (sIsSingleLayerMode) {
                LauncherModel.runOnWorkerThread(
                        new VerifyTask(null, packageName, user, true));
            }
        }

        @Override
        public void onPackageChanged(String packageName, UserHandle user) {
            if (sIsSingleLayerMode) {
                LauncherModel.runOnWorkerThread(
                        new VerifyTask(null, packageName, user, false));
            }
        }

        @Override
        public void onAppCreated(Context context) {
            if (sIsSupportDynamicChange) {
                MultiModeUtilities.clearIconCacheForIconShapeChanged(mContext);
            }
        }

        @Override
        public void onIconShapeChanged(String newValue) {
            if (sIsSupportDynamicChange) {
                MultiModeUtilities.syncSaveIconShapeOverrideValue(mContext, newValue);
            }
        }

        @Override
        public void onBindingWorkspaceFinish() {
            if (sIsSupportDynamicChange) {
                MultiModeUtilities.clearBackupShortCutsPref(mContext);
            }
        }
    };

    private static void verifyAllApps(Context context, Map<ComponentKey, Object> map) {
        List<Pair<ItemInfo, Object>> newItems = new ArrayList<>();
        synchronized (LauncherModel.getBgDataModel()) {
            for (ComponentKey key : map.keySet()) {
                HashSet<ComponentName> components = new HashSet<>(1);
                components.add(key.componentName);
                ItemInfoMatcher matcher = ItemInfoMatcher.ofComponents(components, key.user);
                if (matcher.filterItemInfos(LauncherModel.getBgDataModel().workspaceItems).isEmpty()) {
                    Object obj = map.get(key);
                    if (obj instanceof AppInfo) {
                        verifyShortcutHighRes(context,(AppInfo)obj);
                        newItems.add(Pair.create((AppInfo)obj, null));
                    } else if (obj instanceof LauncherActivityInfo) {
                        LauncherActivityInfo info = (LauncherActivityInfo) obj;
                        newItems.add(Pair.create(InstallShortcutReceiver.fromActivityInfo(info, context), null));
                    }
                    if (LogUtils.DEBUG_ALL) {
                        LogUtils.d(TAG, "will bind " + key.componentName +" to workspace.");
                    }
                }
            }
        }

        LauncherAppState appState = LauncherAppState.getInstanceNoCreate();
        if (appState != null && newItems.size() > 0) {
            appState.getModel().addAndBindAddedWorkspaceItems(newItems);
        }
    }

    private static void verifyShortcutHighRes(Context context, AppInfo appInfo) {
        if (appInfo != null) {
            if (appInfo.usingLowResIcon) {
                LauncherAppState.getInstance(context).getIconCache().getTitleAndIcon(appInfo,false);
            }
        }
    }

    public static String getHomeScreenStylePrefValue(Context context){
        return MultiModeUtilities.getHomeScreenStylePrefValue(context);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (LauncherSettingsExtension.sIsUserAMonkey) {
            return false;
        }

        final String newModel = (String) newValue;
        if (!getHomeScreenStylePrefValue(mContext).equals(newModel)) {
            // Value has changed
            show(preference.getContext(), null, mContext.getString(R.string.home_screen_style_notification),
                    true, false);

            LauncherModel.runOnWorkerThread(new RestartHomeApp(mContext) {
                @Override
                protected void saveNewValue() {
                    //backup cur model pinned shortcuts
                    MultiModeUtilities.backupPinnedShortcuts(mContext);

                    // Synchronously write the preference.
                    MultiModeUtilities.syncSaveNewModel(mContext, newModel);

                    //restore new model pinned shortcuts
                    MultiModeUtilities.restorePinnedShortcuts(mContext);
                }
            });
        }
        return false;
    }

    private class VerifyTask implements Runnable {
        final Collection<AppInfo> mApps;
        final String mPackageNames;
        final UserHandle mUser;
        final boolean mIsAddPackage;

        VerifyTask(Collection<AppInfo> apps, String packageNames, UserHandle user, boolean isAdd) {
            mApps = apps;
            mPackageNames = packageNames;
            mUser = user;
            mIsAddPackage = isAdd;
        }

        @Override
        public void run() {
            final Map<ComponentKey, Object> map = new HashMap<>();
            if (mApps != null && mApps.size() > 0) {
                for (AppInfo app : mApps) {
                    map.put(app.toComponentKey(), app);
                }
            } else if (mPackageNames != null && mUser != null) {
                final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(mContext);
                if (mIsAddPackage || launcherApps.isPackageEnabledForProfile(mPackageNames, mUser)) {
                    final List<LauncherActivityInfo> infos = launcherApps.getActivityList(mPackageNames, mUser);
                    for (LauncherActivityInfo info : infos) {
                        map.put(new ComponentKey(info.getComponentName(), info.getUser()), info);
                    }
                }
            }

            verifyAllApps(mContext, map);
        }
    }
}
