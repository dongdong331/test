package com.sprd.ext.dynamicicon;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.launcher3.Utilities;
import com.sprd.ext.FeatureOption;

import java.util.Calendar;
import java.util.Locale;

/**
 * Created on 6/1/18.
 */
public class DynamicIconUtils {

    public static boolean anyDynamicIconSupport() {
        if (FeatureOption.SPRD_DYNAMIC_ICON_SUPPORT) {
            return FeatureOption.SPRD_DYNAMIC_ICON_CALENDAR_SUPPORT
                    || FeatureOption.SPRD_DYNAMIC_ICON_CLOCK_SUPPORT;
        }
        return false;
    }

    public static void setAppliedValue(Context context, String key, boolean value) {
        SharedPreferences.Editor editor = Utilities.getPrefs(context).edit();
        editor.putBoolean(key, value).apply();
    }

    public static boolean getAppliedValue(Context context, String key, boolean def) {
        SharedPreferences sharedPref = Utilities.getPrefs(context);
        return sharedPref.getBoolean(key, def);
    }

    static void removePrefKeyFromSharedPref(Context context, String... keys) {
        SharedPreferences.Editor editor = Utilities.getPrefs(context).edit();
        for (String key : keys) {
            editor.remove(key);
        }
        editor.apply();
    }

    public static int dayOfMonth() {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
    }

    public static String dayOfWeek() {
        return Calendar.getInstance().getDisplayName(
                Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()).toUpperCase();
    }

    public static int timeOfField(int field) {
        return Calendar.getInstance().get(field);
    }
}
