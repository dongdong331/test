package com.sprd.ext.dynamicicon;

import android.content.pm.LauncherActivityInfo;
import android.graphics.drawable.Drawable;

/**
 * Created on 5/31/18.
 */
public interface DynamicIconCallback {

    String getPkgName();

    void onStateChanged(boolean dynamic);

    Drawable getIcon(LauncherActivityInfo info, int iconDpi, boolean flattenDrawable);
    String getDrawableContentNeedShown();

    void registerReceiver();
    void unRegisterReceiver();

    void onStart();
}
