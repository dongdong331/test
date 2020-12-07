package com.sprd.ext.unreadnotifier;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;

import com.android.launcher3.AppInfo;
import com.sprd.ext.LauncherAppMonitor;
import com.sprd.ext.LauncherAppMonitorCallback;

import java.util.ArrayList;

/**
 * Created by SPRD on 7/7/17.
 */

public class UnreadInfoController implements UnreadLoaderUtils.UnreadChangedListener
        , Preference.OnPreferenceClickListener {
    private UnreadLoaderUtils mUnreadLoaderUtils = null;
    private Context mContext;

    public UnreadInfoController(Context context, LauncherAppMonitor monitor) {
        mContext = context;
        monitor.registerCallback(mUnreadMonitorCallback);
    }

    private LauncherAppMonitorCallback mUnreadMonitorCallback = new LauncherAppMonitorCallback() {
        @Override
        public void onLauncherCreated() {
            mUnreadLoaderUtils = UnreadLoaderUtils.getInstance(mContext);
            // Register unread change broadcast.
            mUnreadLoaderUtils.registerUnreadReceiver();

            mUnreadLoaderUtils.initializeUnreadInfo(UnreadInfoController.this);
        }

        @Override
        public void onLauncherDestroy() {
            if (mUnreadLoaderUtils != null) {
                mUnreadLoaderUtils.unRegisterUnreadReceiver();
                mUnreadLoaderUtils.unregisterItemContentObservers();
            }
        }

        @Override
        public void onLauncherRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
            UnreadInfoManager.getInstance(mContext).handleRequestPermissionResult(requestCode,
                    permissions, grantResults);
        }

        @Override
        public void onBindingWorkspaceFinish() {
            mUnreadLoaderUtils.refreshWorkspaceUnreadInfoIfNeeded();
        }

        @Override
        public void onBindingAllAppFinish(ArrayList<AppInfo> apps) {
            mUnreadLoaderUtils.refreshAppsUnreadInfoIfNeeded(apps);
        }
    };

    //Implementation of the method from UnreadLoaderUtils.UnreadChangedListener.
    @Override
    public void onUnreadInfoChanged(UnreadKeyData info) {
        mUnreadLoaderUtils.onUnreadInfoChanged(info);
    }

    @Override
    public void onWorkspaceUnreadInfoRefresh() {
        mUnreadLoaderUtils.onWorkspaceUnreadInfoRefresh();
    }

    @Override
    public void onAppsUnreadInfoRefresh(ArrayList<AppInfo> apps) {
        mUnreadLoaderUtils.onAppsUnreadInfoRefresh(apps);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (mContext != null) {
            Intent it = new Intent(mContext.getApplicationContext(), UnreadActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(it);
            return true;
        }
        return false;
    }
}
