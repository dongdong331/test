/*
 ** Copyright 2018 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManagerInternal;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.sprdpower.IAppStatsService;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.IntArray;
import android.util.Slog;

import android.util.SparseBooleanArray;
import com.android.internal.annotations.GuardedBy;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.pm.UserManagerService;

/**
 * @hide
 */
public class AppStatsServiceLoader {
    private static final String TAG = "AppStatsServiceLoader";

    private IAppStatsService mAppStatsService;

    private class AppStatsServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Normally, we would listen for death here
            try {
                service.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        connectToAppStatsService();
                    }
                }, 0);

                mAppStatsService = IAppStatsService.Stub.asInterface(service);

                Slog.w(TAG, "onServiceConnected:" + mAppStatsService);

                //ServiceManager.addService("appstats", service);
            } catch (Exception e) {
                Slog.w(TAG, "Failed linking to death.");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connectToAppStatsService();
        }
    }

    private static final ComponentName SERVICE_COMPONENT = new ComponentName(
            "com.android.power.appstats",
            "com.android.power.appstats.AppStatsService");

    private static final String SERVICE_ACTION = "com.android.power.appstats";

    private final Object mLock = new Object();


    private final Context mContext;
    private int mConnectCount = 0;

    public AppStatsServiceLoader(Context context) {
        mContext = context;
    }

    void loadService() {
        connectToAppStatsService();
    }

    void reportAppStateChanged(String packageName, int userId, int state) {
        try {
            if (mAppStatsService != null)
                mAppStatsService.reportAppStateChanged(packageName, userId, state);
        } catch (Exception e) {
            Slog.w(TAG, "reportAppStateChanged exception:" + e);
        }
    }

    void reportAppProcStateChanged(String packageName, int uid, int procState) {
        try {
            if (mAppStatsService != null)
                mAppStatsService.reportAppProcStateChanged(packageName, uid, procState);
        } catch (Exception e) {
            Slog.w(TAG, "reportAppProcStateChanged exception:" + e);
        }
    }

    //@GuardedBy("mLock")
    private AppStatsServiceConnection mServiceConnection;

    private void connectToAppStatsService() {
        try {
            synchronized (mLock) {
                mConnectCount++;
                if (mConnectCount > 5) return;
                if (mServiceConnection != null) {
                    // TODO: Is unbinding worth doing or wait for system to rebind?
                    mContext.unbindService(mServiceConnection);
                    mServiceConnection = null;
                }

                AppStatsServiceConnection serviceConnection = new AppStatsServiceConnection();
                Intent intent = new Intent(SERVICE_ACTION);
                intent.setComponent(SERVICE_COMPONENT);
                int flags = Context.BIND_IMPORTANT | Context.BIND_FOREGROUND_SERVICE
                        | Context.BIND_AUTO_CREATE;

                // Bind to the service
                if (mContext.bindServiceAsUser(intent, serviceConnection, flags, UserHandle.SYSTEM)) {
                    mServiceConnection = serviceConnection;
                    Slog.w(TAG, "bindServiceAsUser Success.");
                } else {
                    Slog.w(TAG, "bindServiceAsUser Fail.");
                }
            }
        } catch (Exception e) {}
    }


}
