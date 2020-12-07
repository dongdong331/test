package com.sprd.ext.dynamicicon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Process;

import com.android.launcher3.IconCache;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;

import com.sprd.ext.LogUtils;


/**
 * Created on 5/30/18.
 */
public class BaseDynamicIcon implements DynamicIconCallback {
    private static final String TAG = "BaseDynamicIcon";

    protected String mPkg;
    protected boolean mCurDynamic;
    protected boolean mPreDynamic;
    protected boolean mRegistered;
    protected DynamicDrawable mIcon;
    protected IntentFilter mFilter;

    protected Context mContext;
    protected Handler mHandler;
    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateUI();
        }
    };

    public BaseDynamicIcon(Context context, String pkg) {
        mContext = context;
        mPkg = pkg;
        mFilter = new IntentFilter();
        mHandler = new Handler(LauncherModel.getWorkerLooper());
    }

    @Override
    public String getPkgName() {
        return mPkg;
    }

    @Override
    public void onStateChanged(boolean dynamic) {
        mCurDynamic = dynamic;

        if (LogUtils.DEBUG_DYNAMIC_ICON) {
            LogUtils.d(TAG, "onStateChanged: mCurDynamic = " + mCurDynamic + ", mPkg = " + mPkg);
        }
    }

    protected void updateUI() {
        DynamicIconManager.getInstance(mContext).updateIcons(mPkg);
    }

    @Override
    public Drawable getIcon(LauncherActivityInfo info, int iconDpi, boolean flattenDrawable) {
        if (LogUtils.DEBUG_DYNAMIC_ICON) {
            LogUtils.d(TAG, "getIcon: mCurDynamic = " + mCurDynamic + ", pkg = " + getPkgName());
        }
        if (mCurDynamic) {
            return mIcon.create(info.getIcon(iconDpi));
        } else {
            return null;
        }
    }

    @Override
    public void registerReceiver() {
        if (LogUtils.DEBUG_DYNAMIC_ICON) {
            LogUtils.d(TAG, "registerReceiver: mCurDynamic = " + mCurDynamic
                    + ", mRegistered = " + mRegistered);
        }

        if (mCurDynamic && !mRegistered) {
            mRegistered = true;
            mContext.registerReceiver(mReceiver, mFilter, null, mHandler);
            onReceiverRegistered();
        }
    }

    @Override
    public void unRegisterReceiver() {
        if (LogUtils.DEBUG_DYNAMIC_ICON) {
            LogUtils.d(TAG, "unRegisterReceiver: mRegistered = " + mRegistered);
        }

        if (mRegistered) {
            mRegistered = false;
            mContext.unregisterReceiver(mReceiver);
            onReceiverUnregistered();
        }
    }

    protected void onReceiverRegistered() {
        if (LogUtils.DEBUG_DYNAMIC_ICON) {
            LogUtils.d(TAG, "Registered for " + mPkg);
        }
    }

    protected void onReceiverUnregistered() {
        if (LogUtils.DEBUG_DYNAMIC_ICON) {
            LogUtils.d(TAG, "Unregistered for " + mPkg);
        }
    }

    @Override
    public void onStart() {
        if (LogUtils.DEBUG_DYNAMIC_ICON) {
            LogUtils.d(TAG, "onStart: mPreDynamic = " + mPreDynamic
                    + ", mCurDynamic = " + mCurDynamic + ", pkg  = " + mPkg);
        }
        if (mPreDynamic != mCurDynamic) {
            mPreDynamic = mCurDynamic;
            DynamicIconUtils.setAppliedValue(mContext, mPkg, mPreDynamic);

            LauncherAppState appState = LauncherAppState.getInstanceNoCreate();
            if (appState != null) {
                boolean isModelLoaded = appState.getModel().isModelLoaded();
                if (isModelLoaded) {
                    updateUI();
                } else {
                    IconCache iconCache = appState.getIconCache();
                    iconCache.removeIconsForPkg(mPkg, Process.myUserHandle());

                    if (LogUtils.DEBUG_DYNAMIC_ICON) {
                        LogUtils.d(TAG, "onStart: remove icons for " + mPkg + " in the cache and database.");
                    }
                }
            }
        } else if (mCurDynamic) {
            String curContent = getDrawableContentNeedShown();
            if (LogUtils.DEBUG_DYNAMIC_ICON) {
                LogUtils.d(TAG, "onStart: curContent = " + curContent
                        + ", iconContent = " + getDrawableContentShowing());
            }

            if (!curContent.equals(getDrawableContentShowing())) {
                updateUI();
            }
        }
    }

    protected String getDrawableContentShowing() {
        return mIcon.mContent;
    }

    @Override
    public String getDrawableContentNeedShown() {
        return "";
    }

    public abstract class DynamicDrawable {
        protected Resources mRes;
        protected Drawable mBackground;
        protected String mContent;

        abstract public Drawable create(Drawable icon);

        public DynamicDrawable(Context context) {
            mRes = context.getResources();
        }

        public String getContent() {
            return mContent;
        }

        protected Drawable getBackgroundDrawable(final String pkgName, Context context) {
            try {
                PackageManager pm = context.getPackageManager();
                final Resources resourcesForApplication = pm.getResourcesForApplication(pkgName);
                final int resId = resourcesForApplication.getIdentifier(
                        "ic_launcher_background", "color", pkgName);
                if (resId != 0) {
                    return resourcesForApplication.getDrawable(resId, null);
                }
            } catch (PackageManager.NameNotFoundException ex) {
                LogUtils.d(TAG, "get background error for " + pkgName);
            }

            return context.getResources().getDrawable(R.color.legacy_icon_background, null);
        }
    }
}
