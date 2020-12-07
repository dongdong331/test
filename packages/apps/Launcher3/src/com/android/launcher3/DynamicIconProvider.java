package com.android.launcher3;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import com.sprd.ext.dynamicicon.DynamicIconManager;
import com.sprd.ext.dynamicicon.DynamicIconUtils;

/**
 * Created on 5/30/18.
 */
public class DynamicIconProvider extends IconProvider {

    private DynamicIconManager mDIManager;

    public DynamicIconProvider(Context context) {
        if (DynamicIconUtils.anyDynamicIconSupport()) {
            mDIManager = DynamicIconManager.getInstance(context);
        }
    }

    @Override
    public Drawable getIcon(LauncherActivityInfo info, int iconDpi, boolean flattenDrawable) {
        Drawable icon = mDIManager == null ? null : mDIManager.getIcon(info, iconDpi, flattenDrawable);

        if (icon == null) {
            icon = super.getIcon(info, iconDpi, flattenDrawable);
        }
        return icon;
    }

    @Override
    public String getIconSystemState(String packageName) {
        String extraState = mDIManager == null ? null : mDIManager.getIconContent(packageName);
        return TextUtils.isEmpty(extraState) ? super.getIconSystemState(packageName)
                : super.getIconSystemState(packageName) + "," + extraState;
    }
}
