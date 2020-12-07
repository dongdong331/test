package com.android.wallpaperpicker;

import com.android.wallpaperpicker.common.SystemPropertiesUtils;
import com.android.wallpaperpicker.common.Utilities;

/**
 * Created by SPREADTRUM on 7/28/17.
 */

public class FeatureOption {
    private static final String TAG = "FeatureOption";

    // When enabled, the system wallpaper will not scroll with the scrolling of pages in idle screen.
    public static final boolean SPRD_STABLE_WALLPAPER_SUPPORT =
            SystemPropertiesUtils.getBoolean("ro.wallpaper.stable", Utilities.IS_LOW_RAM);

    //SPRD add for SPRD_ADAPTIVE_WALLPAPER_SUPPORT
    public static final boolean SPRD_ADAPTIVE_WALLPAPER_SUPPORT = !SPRD_STABLE_WALLPAPER_SUPPORT;

    public static final boolean SPRD_ENABLE_LOCK_WALLPAPER =
            SystemPropertiesUtils.getBoolean("ro.lockwallpaper.enable", true);
}
