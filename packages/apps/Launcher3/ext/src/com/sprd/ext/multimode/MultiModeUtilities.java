package com.sprd.ext.multimode;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.content.res.Resources;
import android.os.UserHandle;
import android.text.TextUtils;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.graphics.IconShapeOverride;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.sprd.ext.FeatureOption;
import com.sprd.ext.LauncherSettingsExtension;
import com.sprd.ext.LogUtils;
import com.sprd.ext.UtilitiesExt;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by SPRD on 09/14/18.
 */

class MultiModeUtilities {
    private static final String TAG = "MultiModeUtilities";

    private static final String SINGLE = "single";
    private static final String DUAL = "dual";

    private static final String PREF_BACKUP_SHORTCUTS_KEY = "pinned_shortcut";
    private static final String LINE_SPACER = "@@@";

    static boolean isSupportDynamicChangeHomeScreenStyle(Context context) {
        return FeatureOption.SPRD_MULTI_MODE_SUPPORT
                && context.getResources().getBoolean(R.bool.show_home_screen_style_settings);
    }

    static boolean isSingleLayerMode(Context context) {
         return FeatureOption.SPRD_MULTI_MODE_SUPPORT
                && SINGLE.equals(getHomeScreenStylePrefValue(context));
    }

    static boolean isDefaultMode(Context context) {
        if (!FeatureOption.SPRD_MULTI_MODE_SUPPORT || context == null) {
            return true;
        }

        if (!isSupportDynamicChangeHomeScreenStyle(context)) {
            return true;
        }

        String defaultMode = context.getResources().getString(R.string.default_home_screen_style);
        return defaultMode.equals(getHomeScreenStylePrefValue(context));
    }

    static String getHomeScreenStylePrefValue(Context context) {
        if (context == null) {
            return DUAL;
        }
        Resources res = context.getResources();
        return Utilities.getPrefs(context)
                .getString(LauncherSettingsExtension.PREF_HOME_SCREEN_STYLE_KEY,
                        res.getString(R.string.default_home_screen_style));
    }

    private static String getIconShapeOverridePrefKey(Context context) {
        return IconShapeOverride.KEY_PREFERENCE + getHomeScreenStylePrefValue(context);
    }

    static void clearIconCacheForIconShapeChanged(Context context) {
        LauncherModel.runOnWorkerThread(() -> {
            String appliedValue = IconShapeOverride.getAppliedValue(context);
            if (!getSavedIconShapeValue(context).equals(appliedValue)) {
                LogUtils.d(TAG, "will clear icon cache for icon shape changed.");
                LauncherAppState.getInstance(context).getIconCache().clear();
                syncSaveIconShapeOverrideValue(context, appliedValue);
            }
        });
    }

    private static String getSavedIconShapeValue(Context context) {
        String key = getIconShapeOverridePrefKey(context);
        return Utilities.getDevicePrefs(context).getString(key,
                context.getString(R.string.default_icon_shape_override_paths));
    }

    @SuppressLint("ApplySharedPref")
    static void syncSaveIconShapeOverrideValue(Context context, String newValue) {
        String key = getIconShapeOverridePrefKey(context);
        Utilities.getDevicePrefs(context).edit().putString(key, newValue).commit();
    }

    @SuppressLint("ApplySharedPref")
    static void syncSaveNewModel(Context context, String newModel) {
        String key = LauncherSettingsExtension.PREF_HOME_SCREEN_STYLE_KEY;
        Utilities.getPrefs(context).edit().putString(key, newModel).commit();
    }

    private static String getBackupShortCutsPrefKey(Context context) {
        return getBackupShortCutsPrefKey(getHomeScreenStylePrefValue(context));
    }

    private static String getBackupShortCutsPrefKey(String style) {
        return PREF_BACKUP_SHORTCUTS_KEY + "_" + style;
    }

    static void backupPinnedShortcuts(Context context) {
        DeepShortcutManager dsMgr = DeepShortcutManager.getInstance(context);
        UserManagerCompat userManager = UserManagerCompat.getInstance(context);

        List<ShortcutInfoCompat> allPinnedShortcuts = new ArrayList<>();
        for (UserHandle user : userManager.getUserProfiles()) {
            if (!userManager.isUserUnlocked(user)) {
                continue;
            }
            List<ShortcutInfoCompat> pinnedShortcuts =
                    dsMgr.queryForPinnedShortcuts(null, user);
            for (ShortcutInfoCompat shortcut : pinnedShortcuts) {
                ShortcutKey sk = ShortcutKey.fromInfo(shortcut);
                ShortcutInfo info = shortcut.getShortcutInfo();
                if (LogUtils.DEBUG_ALL) {
                    LogUtils.d( TAG, "backupPinnedShortcuts, shortcut:" + sk);
                }
                if (info.isDynamic() || info.isDeclaredInManifest()) {
                    //only unpin the dynamic or mainfest shortcuts
                    dsMgr.unpinShortcut(sk);
                    if (LogUtils.DEBUG_ALL) {
                        LogUtils.d(TAG, "unpin shortcut:" + sk + " ret:" + dsMgr.wasLastCallSuccess());
                    }
                }
                allPinnedShortcuts.add(shortcut);
            }
        }

        if (!allPinnedShortcuts.isEmpty()) {
            String prefKey = getBackupShortCutsPrefKey(context);
            saveShortcutsToPref(context, prefKey, allPinnedShortcuts);
            if (LogUtils.DEBUG_ALL) {
                LogUtils.d(TAG, "backupPinnedShortcuts to [" + prefKey + "] success.");
            }
        }

    }

    static void restorePinnedShortcuts(Context context) {
        DeepShortcutManager dsMgr = DeepShortcutManager.getInstance(context);
        String prefKey = getBackupShortCutsPrefKey(context);
        List<ShortcutInfoCompat> shortcuts = readShortcutsFromPref(context, prefKey);
        List<ShortcutInfoCompat> unRequestPinShortcuts = new ArrayList<>();
        for (ShortcutInfoCompat shortcut : shortcuts) {
            ShortcutKey sk = ShortcutKey.fromInfo(shortcut);
            ShortcutInfo info = shortcut.getShortcutInfo();
            if (LogUtils.DEBUG_ALL) {
                LogUtils.d( TAG, "restorePinnedShortcuts, shortcut:" + sk);
            }
            if (info.isDynamic() || info.isDeclaredInManifest()) {
                dsMgr.pinShortcut(sk);
                if (LogUtils.DEBUG_ALL) {
                    LogUtils.d( TAG, "pin shortcut:" + sk + " ret:" + dsMgr.wasLastCallSuccess());
                }
            } else {
                unRequestPinShortcuts.add(shortcut);
            }
        }

        if (!shortcuts.isEmpty()) {
            if (!unRequestPinShortcuts.isEmpty()) {
                saveShortcutsToPref(context, prefKey, unRequestPinShortcuts);
            }
            if (LogUtils.DEBUG_ALL) {
                LogUtils.d(TAG, "restorePinnedShortcuts from [" + prefKey + "] success.");
            }
        }
    }

    static void clearBackupShortCutsPref(Context context) {
        Utilities.getPrefs(context).edit().remove(getBackupShortCutsPrefKey(context)).apply();
    }

    static List<ShortcutInfoCompat> readPreModelSavedShortcuts(Context context, boolean isSingleModel) {
        return readShortcutsFromPref(context, getBackupShortCutsPrefKey(isSingleModel ? DUAL : SINGLE));
    }

    private static List<ShortcutInfoCompat> readShortcutsFromPref(Context context, String prefKey) {
        List<ShortcutInfoCompat> shortcuts = new ArrayList<>();
        if (context == null || (TextUtils.isEmpty(prefKey))) {
            LogUtils.e(TAG, "readShortcutsFromPref input error, prefKey:" + prefKey);
            return shortcuts;
        }
        String[] items = Utilities.getPrefs(context).getString(prefKey, "").split(LINE_SPACER);
        for (String item : items) {
            if (!TextUtils.isEmpty(item)) {
                shortcuts.add(new ShortcutInfoCompat(UtilitiesExt.unMarshall(item, ShortcutInfo.CREATOR)));
            }
        }

        return shortcuts;
    }

    @SuppressLint("ApplySharedPref")
    private static void saveShortcutsToPref(Context context, String prefKey, List<ShortcutInfoCompat> list) {
        if (context == null || TextUtils.isEmpty(prefKey) || list == null) {
            LogUtils.e(TAG, "readShortcutsFromPref input error, key:" + prefKey + " list:" + list);
            return;
        }

        StringBuilder str = new StringBuilder();
        for (ShortcutInfoCompat shortcut : list) {
            ShortcutInfo info = shortcut.getShortcutInfo();
            if (!TextUtils.isEmpty(str)) {
                str.append(LINE_SPACER);
            }
            str.append(UtilitiesExt.marshall(info));
        }

        String string = String.valueOf(str);
        if (string.length() > 0) {
            Utilities.getPrefs(context).edit().putString(prefKey, string).commit();
            if (LogUtils.DEBUG_ALL) {
                LogUtils.d(TAG, "saveShortcutsToPref to " + prefKey);
            }
        }
    }
}
