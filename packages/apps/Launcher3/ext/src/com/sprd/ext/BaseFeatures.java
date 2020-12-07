package com.sprd.ext;

/**
 *
 * Defines a set of features used to control launcher feature.
 *
 * All the feature should be defined here with appropriate default values. To override a value,
 * redefine it in {@link FeatureOption}.
 *
 * This class is kept package-private to prevent direct access.
 */

abstract class BaseFeatures {
    private static final String TAG = "BaseFeatures";

    // When enable user can select user aosp mode or singlelayer mode
    public static final boolean SPRD_MULTI_MODE_SUPPORT = getProp("ro.launcher.multimode");

    // When enabled allows customization of the columns and rows on the desktop.
    public static final boolean SPRD_DESKTOP_GRID_SUPPORT = getProp("ro.launcher.desktopgrid");

    // When enabled show the notification badge count
    public static final boolean SPRD_NOTIFICATION_BADGE_COUNT = getProp("ro.launcher.notifbadge.count");

    // When enable app icon will show icon badge
    public static final boolean SPRD_BADGE_SUPPORT = getProp("ro.launcher.badge");
    public static final boolean SPRD_BADGE_PHONE_SUPPORT = getProp("ro.launcher.badge.phone");
    public static final boolean SPRD_BADGE_MESSAGE_SUPPORT = getProp("ro.launcher.badge.message");
    public static final boolean SPRD_BADGE_EMAIL_SUPPORT = getProp("ro.launcher.badge.email");
    public static final boolean SPRD_BADGE_CALENDAR_SUPPORT = getProp("ro.launcher.badge.calendar");

    // When enable the clock & calendar icon will dynamic update
    public static final boolean SPRD_DYNAMIC_ICON_SUPPORT = getProp("ro.launcher.dynamic");
    public static final boolean SPRD_DYNAMIC_ICON_CLOCK_SUPPORT = getProp("ro.launcher.dynamic.clock");
    public static final boolean SPRD_DYNAMIC_ICON_CALENDAR_SUPPORT = getProp("ro.launcher.dynamic.calendar");

    // When enable, will hide rotation menu item on setting activity
    public static final boolean SPRD_DISABLE_ROTATION = getProp("ro.launcher.disable.rotation");


    // Gestures features
    public static final boolean SPRD_GESTURE_SUPPORT = getProp("ro.launcher.gesture");
    public static final boolean SPRD_GESTURE_ONE_FINGER_PULLDOWN = getProp("ro.launcher.gesture.onedown");

    // When enabled workspace will cycle scroll
    public static final boolean SPRD_CYCLE_SCROLL_SUPPORT = getProp("ro.launcher.cyclescroll");

    // When enabled folder icon support grid mode & aosp mode
    public static final boolean SPRD_FOLDER_ICON_MODE_SUPPORT = getProp("ro.launcher.foldericonmode");

    // When enabled the hotseat icon will adaptive layout
    public static final boolean SPRD_HOTSEAT_ICON_ADAPTIVE_LAYOUT = getProp("ro.launcher.hs.adaptive");


    // When enable can customize the allapp views app position
    public static final boolean SPRD_ALLAPP_CUSTOMIZE_SUPPORT = getProp("ro.launcher.allapp.customize");


    // QuickStep Recent features:enable lock task
    public static final boolean SPRD_TASK_LOCK_SUPPORT = getProp("ro.launcher.tasklock");

    // QuickStep Recent features:enable show memory info
    public static final boolean SPRD_SHOW_MEMINFO_SUPPORT = getProp("ro.launcher.showmeminfo");

    // QuickStep Recent features:enable show toast when clear mem
    public static final boolean SPRD_SHOW_CLEAR_MEM_SUPPORT = getProp("ro.launcher.showclearmem");


    // Performance features: customization of animation for app exit
    public static final boolean SPRD_ANIM_APP_EXIT = getProp("ro.launcher.anim.app.exit");

    // Performance features: customization of animation for launch a task
    public static final boolean SPRD_ANIM_LAUNCHTASK = getProp("ro.launcher.anim.launchtask");

    // Performance features:fast update label when language changing
    public static final boolean SPRD_FAST_UPDATE_LABEL = getProp("ro.launcher.label.fastupdate");


    private static boolean getProp(String prop) {
        boolean ret = false;

        try {
            ret = SystemPropertiesUtils.getBoolean(prop, false);
        } catch (Exception e) {
            LogUtils.e(TAG, "getProp:" + prop + " error." + e);
        }

        return ret;
    }
}
