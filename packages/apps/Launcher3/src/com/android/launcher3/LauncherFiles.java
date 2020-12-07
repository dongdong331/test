package com.android.launcher3;

import com.sprd.ext.multimode.MultiModeController;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Central list of files the Launcher writes to the application data directory.
 *
 * To add a new Launcher file, create a String constant referring to the filename, and add it to
 * ALL_FILES, as shown below.
 */
public class LauncherFiles {

    private static final String XML = ".xml";


    public static final String SHARED_PREFERENCES_KEY = "com.android.launcher3.prefs";
    private static final String MANAGED_USER_PREFERENCES_KEY = "com.android.launcher3.managedusers.prefs";
    // This preference file is not backed up to cloud.
    static final String DEVICE_PREFERENCES_KEY = "com.android.launcher3.device.prefs";

    private static final String LAUNCHER_DB = "launcher.db";
    private static final String SL_LAUNCHER_DB = "sl_launcher.db";
    private static final String WIDGET_PREVIEWS_DB = "widgetpreviews.db";
    private static final String SL_WIDGET_PREVIEWS_DB = "sl_widgetpreviews.db";
    private static final String APP_ICONS_DB = "app_icons.db";
    private static final String SL_APP_ICONS_DB = "sl_app_icons.db";

    public static final List<String> ALL_FILES = Collections.unmodifiableList(Arrays.asList(
            LAUNCHER_DB,
            SL_LAUNCHER_DB,
            SHARED_PREFERENCES_KEY + XML,
            WIDGET_PREVIEWS_DB,
            SL_WIDGET_PREVIEWS_DB,
            MANAGED_USER_PREFERENCES_KEY + XML,
            DEVICE_PREFERENCES_KEY + XML,
            APP_ICONS_DB,
            SL_APP_ICONS_DB));

    static String getLauncherDb() {
        if (MultiModeController.isSupportDynamicChange()) {
            return MultiModeController.isSingleLayerMode() ? SL_LAUNCHER_DB : LAUNCHER_DB;
        }
        return LAUNCHER_DB;
    }

    static String getWidgetPreviewsDB() {
        if (MultiModeController.isSupportDynamicChange()) {
            return MultiModeController.isSingleLayerMode() ? SL_WIDGET_PREVIEWS_DB : WIDGET_PREVIEWS_DB;
        }
        return WIDGET_PREVIEWS_DB;
    }

    static String getAppIconsDb() {
        if (MultiModeController.isSupportDynamicChange()) {
            return MultiModeController.isSingleLayerMode() ? SL_APP_ICONS_DB : APP_ICONS_DB;
        }
        return APP_ICONS_DB;
    }
}
