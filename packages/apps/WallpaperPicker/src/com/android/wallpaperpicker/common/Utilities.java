package com.android.wallpaperpicker.common;

import android.app.WallpaperManager;
import android.os.Build;

public class Utilities {
    public static final boolean DEBUG =
            SystemPropertiesUtils.getBoolean("persist.sys.wallpaper.debug", false);

    public static final boolean ATLEAST_NOUGAT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;

    public static final boolean IS_LOW_RAM = SystemPropertiesUtils.getBoolean("ro.config.low_ram", false);

    public static boolean isAtLeastN() {
        // TODO: replace this with a more final implementation.
        try {
            WallpaperManager.class.getMethod("getWallpaperFile", int.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
