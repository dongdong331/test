package com.sprd.ext.dynamicicon.calendar;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;

import com.android.launcher3.R;
import com.sprd.ext.dynamicicon.BaseDynamicIcon;
import com.sprd.ext.dynamicicon.DynamicIconSettings;
import com.sprd.ext.dynamicicon.DynamicIconUtils;

import java.util.Calendar;

/**
 * Created on 6/7/18.
 */
public class GoogleCalendarIcon extends BaseDynamicIcon {
    private static final String TAG = "GoogleDynamicCalendar";

    private int mLastDate = 0;

    public GoogleCalendarIcon(Context context, String pkg) {
        super(context, pkg);

        mPreDynamic = DynamicIconUtils.getAppliedValue(context, mPkg,
                context.getResources().getBoolean(R.bool.dynamic_calendar_default_state));
        mCurDynamic = DynamicIconUtils.getAppliedValue(context,
                DynamicIconSettings.PREF_KEY_GOOGLE_CALENDAR,
                context.getResources().getBoolean(R.bool.dynamic_calendar_default_state));

        mFilter.addAction(Intent.ACTION_DATE_CHANGED);
        mFilter.addAction(Intent.ACTION_TIME_CHANGED);
        mFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver();
    }

    @Override
    protected String getDrawableContentShowing() {
        return String.valueOf(mLastDate);
    }

    @Override
    public String getDrawableContentNeedShown() {
        return String.valueOf(dayOfMonth());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public Drawable getIcon(final LauncherActivityInfo launcherActivityInfo, int iconDpi, boolean flattenDrawable) {
        if (!mCurDynamic) {
            return null;
        }

        try {
            PackageManager pm = mContext.getPackageManager();
            ActivityInfo activityInfo = pm.getActivityInfo(
                    launcherActivityInfo.getComponentName(),
                    PackageManager.GET_META_DATA |
                            PackageManager.MATCH_UNINSTALLED_PACKAGES);
            Bundle metaData = activityInfo.metaData;
            Resources resourcesForApplication = pm.getResourcesForApplication(mPkg);
            int shape = getCorrectShape(metaData, resourcesForApplication);
            if (shape != 0) {
                return resourcesForApplication.getDrawableForDensity(shape, iconDpi);
            }
        } catch (PackageManager.NameNotFoundException ex) {}

        return null;
    }

    private int dayOfMonth() {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1;
    }

    private int getCorrectShape(Bundle bundle, Resources resources) {
        if (bundle != null) {
            int roundIcons = bundle.getInt("com.google.android.calendar.dynamic_icons_nexus_round", 0);
            if (roundIcons != 0) {
                try {
                    TypedArray obtainTypedArray = resources.obtainTypedArray(roundIcons);
                    mLastDate = dayOfMonth();
                    int resourceId = obtainTypedArray.getResourceId(mLastDate, 0);
                    obtainTypedArray.recycle();
                    return resourceId;
                }
                catch (Resources.NotFoundException ex) {
                }
            }
        }
        return 0;
    }
}
