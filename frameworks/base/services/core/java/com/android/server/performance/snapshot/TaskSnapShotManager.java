/*
 * Copyright © 2017 Spreadtrum Communications Inc.
 */

package com.android.server.performance.snapshot;


import android.app.ActivityManagerNative;
import android.app.ActivityManager;
import android.app.IPerformanceManagerInternal;
import android.app.TaskThumbnail;
import android.app.ProcessInfo;
import android.content.Intent;
import android.content.Context;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.Message;
import android.os.Process;
import android.os.SystemProperties;
import android.util.Slog;
import android.view.SurfaceControl;
import android.os.UserHandle;
import android.view.WindowManager;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;
import android.view.Display;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import static android.app.ProcessInfo.*;
import static android.view.Surface.ROTATION_0;
import com.android.server.am.ActivityManagerServiceEx;
import com.android.server.performance.PerformanceManagerService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import static com.android.server.performance.PerformanceManagerDebugConfig.*;

public class TaskSnapShotManager {
    private static final String IMAGES_DIRNAME = "sprd_recent_images";
    private static final String STARTING_WINDOW_ENABLED = "persist.sys.startingwindow";
    private PerformanceManagerService mService;
    private Context mContext;
    private ActivityManagerServiceEx mAm;
    private List<RecentTaskThumbnail> mRecentTaskThumbnailList = new ArrayList<RecentTaskThumbnail>();
    private int mExcludeFlags = Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
    private HashMap <String, TaskSnapShot> mRecentThumbnailPkgs = new HashMap<String, TaskSnapShot>();
    int mRecentThumbnailWidth;
    int mRecentThumbnailHeight;
    private boolean mRecentReady;
    private final int MSG_SCHEDULE_UPDATE_THUMBNAIL = 0;
    private final int MSG_SCHEDULE_REMOVE_THUMBNAIL = 1;
    private HandlerThread mThumbThread;
    private Handler mThumbnailHandler;
    private int mCurrentUserId = UserHandle.myUserId();
    private boolean mUpdateCanceled = false;


    class TaskSnapShot {
        String pkgName = "";
        long removingDelay = 0;
        long snapDelay = 0;//RecentTaskThumbnail.RECENT_THUMBNAIL_DELAY;
        public TaskSnapShot(String pkgName, long removingDelay, long snapDelay) {
            this.pkgName = pkgName;
            this.removingDelay = removingDelay;
            this.snapDelay = snapDelay;
        }
        public TaskSnapShot(String pkgName) {
            this(pkgName, 0, RecentTaskThumbnail.RECENT_THUMBNAIL_DELAY);
        }
    }

    public TaskSnapShotManager(PerformanceManagerService service, ActivityManagerServiceEx am, Context context) {
        mService = service;
        mAm = am;
        mContext = context;
        restoreTaskThumbnailFromFile();
    }

    private void initRecentTaskThumbnail() {
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);

        /* SPRD:modify for Bug 950530 modify getting correct height in guest mode. @{ */
        //mRecentThumbnailWidth = wm.getDefaultDisplay().getWidth();
        //mRecentThumbnailHeight = wm.getDefaultDisplay().getHeight();
        DisplayMetrics metric = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metric);
        mRecentThumbnailWidth = metric.widthPixels;
        mRecentThumbnailHeight = metric.heightPixels;
        /* @} */

        String[] tmp = mContext.getResources().getStringArray(com.android.internal.R.array.config_startingwindow_packages);
        for (String str : tmp) {
            String [] strs = str.split(",");
            if (strs.length == 1) {
                //use default values
                mRecentThumbnailPkgs.put(strs[0], new TaskSnapShot(strs[0]));
            } else if(strs.length == 3) {
                mRecentThumbnailPkgs.put(strs[0], new TaskSnapShot(strs[0],
                            Long.valueOf(strs[1]), Long.valueOf(strs[2])));
            }
        }
        File recentDir = getUserImagesDir(mCurrentUserId);
        try {
            if (!recentDir.exists()) {
                recentDir.mkdirs();
            }
        } catch (Exception e) {}
        mThumbThread = new HandlerThread("splash-update",Process.THREAD_PRIORITY_BACKGROUND);
        mThumbThread.start();
        mThumbnailHandler = new Handler(mThumbThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case MSG_SCHEDULE_UPDATE_THUMBNAIL:
                        handleUpdateRecentTaskThumbnail((Intent)msg.obj);
                        break;
                    case MSG_SCHEDULE_REMOVE_THUMBNAIL:
                        try {
                            handleRecentTaskThumbnailListRemoveApp((String)msg.obj);
                        } catch (Exception e) {}
                        break;
                }
            }
        };
    }

    private void handleUpdateRecentTaskThumbnail(Intent intent) {
         float scale = 1.0f;
         Slog.d(TAG,"handleUpdateRecentTaskThumbnail...:"+mUpdateCanceled);
         if (intent != null && intent.getComponent() != null && !mUpdateCanceled) {
             String pkgName = intent.getComponent().getPackageName();
             if (pkgName != null && mContext.getResources().getConfiguration().orientation ==
                 Configuration.ORIENTATION_PORTRAIT) {
                 boolean isNavShow = false;
                 try {
                     IWindowManager mWindowManager = WindowManagerGlobal.getWindowManagerService();
                     isNavShow = mWindowManager.isNavigationBarShowing();
                 } catch (Exception e) {}
                 /*
                 Bitmap thumb = mAm.screenShotResumedApp(pkgName, getRecentThumbnailWidth(),
                                                        getRecentThumbnailHeight(isNavShow), scale);
                 */
                 int dw = getRecentThumbnailWidth();
                 int dh =  getRecentThumbnailHeight(isNavShow);

                 int statusHeight = mContext.getResources()
                        .getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);
                 int navHeight = mContext.getResources()
                        .getDimensionPixelSize(com.android.internal.R.dimen.navigation_bar_height);

                 final Rect frame = new Rect(0, statusHeight, dw,
                         (isNavShow ? (mRecentThumbnailHeight - navHeight) : mRecentThumbnailHeight));
                 Bitmap bit = SurfaceControl.screenshot(frame, dw, dh, 0, 1, false, ROTATION_0);
                 if (bit != null) {
                     Bitmap thumb = bit.createAshmemBitmap(Bitmap.Config.ARGB_8888);
                     bit.recycle();
                     if (thumb != null) {
                         updateRecentTaskThumbnail(intent, thumb, isNavShow);
                     }
                 }
             }
         }
    }

    private void handleRecentTaskThumbnailListRemoveApp(String pkgName) {
        synchronized (mRecentTaskThumbnailList) {
            for (int i = 0; i < mRecentTaskThumbnailList.size(); i++) {
                if (DEBUG_TASKSNAPSHOT) Slog.d(TAG, "recentTaskThumbnailListRemoveApp " + mRecentTaskThumbnailList.get(i).toString());
                if (mRecentTaskThumbnailList.get(i).toString().contains(pkgName)) {
                    mRecentTaskThumbnailList.remove(i);
                    i--;
                }
            }
            File dir = getUserImagesDir(mCurrentUserId);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().startsWith(RecentTaskThumbnail.THUMBNAIL_SUFFIX) && file.getName().contains(pkgName)) {
                        file.delete();
                    }
                }
            }
        }
    }

    private void restoreTaskThumbnailFromFile() {
        initRecentTaskThumbnail();
        try {
            File dir = getUserImagesDir(mCurrentUserId);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(RecentTaskThumbnail.THUMBNAIL_XML_ENDWITH)
                        && file.getName().startsWith(RecentTaskThumbnail.THUMBNAIL_SUFFIX)
                        && file.canRead()) {
                        boolean isNavShow = false;
                        try {
                             IWindowManager mWindowManager = WindowManagerGlobal.getWindowManagerService();
                             isNavShow = mWindowManager.isNavigationBarShowing();
                         } catch (Exception e) {}
                        RecentTaskThumbnail task = RecentTaskThumbnail.restoreFromXML(file, this, mCurrentUserId, isNavShow);
                        if (task != null) {
                            synchronized (mRecentTaskThumbnailList) {
                                if (DEBUG_TASKSNAPSHOT) Slog.d(TAG, "restoreTaskThumbnailFromFile--->" + task);
                                mRecentTaskThumbnailList.add(task);
                            }
                        }
                    }
                }
            }
        } catch(Exception e) {
            Slog.e(TAG, "restoreTaskThumbnailFromFile " + e);
        }
        mRecentReady = true;
    }

    private void scheduleUpdateCurrentTaskThumbnail(Intent intent) {
        if (!mUpdateCanceled) {
            mThumbnailHandler.removeMessages(MSG_SCHEDULE_UPDATE_THUMBNAIL);
            Message msg = mThumbnailHandler.obtainMessage(MSG_SCHEDULE_UPDATE_THUMBNAIL, intent);
            long delay = SystemProperties.getLong("debug.snap",
                         getPackageSnapShotDelay(intent.getComponent().getPackageName()));
            mThumbnailHandler.sendMessageDelayed(msg, delay);
        }
    }

    private int getRecentThumbnailWidth() {
        return mRecentThumbnailWidth;
    }

    private int getRecentThumbnailHeight(boolean isNavShow) {

        return mRecentThumbnailHeight - getTopHeight(isNavShow);
    }

    private int getTopHeight(boolean isNavShow) {
        int height = 0;
        int statusHeight = mContext.getResources().getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);
        int navHeight = mContext.getResources().getDimensionPixelSize(com.android.internal.R.dimen.navigation_bar_height);
        if ( isNavShow ) {
            return  statusHeight + navHeight;
        } else {
            return statusHeight;
        }
    }

    public void handleActivityLaunch (Intent intent, Bundle bundle) {
        boolean fullscreen = bundle.getBoolean(KEY_FULLSCREEN);
        ComponentName app = intent.getComponent();
        if (fullscreen && pkgSupportRecentThumbnail(app.getPackageName()) &&
                    intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
                    mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            scheduleUpdateCurrentTaskThumbnail(intent);
        }
    }

    public boolean pkgSupportRecentThumbnail(String pkgName) {
        boolean enabled = SystemProperties.getBoolean(STARTING_WINDOW_ENABLED, false);
        if (!enabled) {
            return false;
        }
        if (mRecentThumbnailPkgs != null) {
            if(mRecentThumbnailPkgs.get(pkgName) != null) {
                return true;
            }
        }
        return false;
    }

    private long getPackageSnapShotDelay(String pkgName) {
        return mRecentThumbnailPkgs.get(pkgName).snapDelay;
    }

    private long getPackageStartingWindowRemoveDelay(String pkgName) {
        return SystemProperties.getLong("debug.remove",
                mRecentThumbnailPkgs.get(pkgName).removingDelay);
    }

    public long getStartingWindowRemoveDelay(Intent intent) {
        if (intent != null) {
            ComponentName app = intent.getComponent();
            String pkgName = app.getPackageName();
            if (pkgSupportRecentThumbnail(pkgName) &&
                intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
                mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                return getPackageStartingWindowRemoveDelay(pkgName);
            }
        }
        return 0;
    }

    public void handleActivityResume(Intent intent) {
        ComponentName app = intent.getComponent();
        mUpdateCanceled = false;
    }
    public void removePendingUpdateThumbTask() {
        Slog.w(TAG,"update thumbnail cancelled");
        mUpdateCanceled = true;
        mThumbnailHandler.removeMessages(MSG_SCHEDULE_UPDATE_THUMBNAIL);
    }

    public TaskThumbnail getTaskThumbnail(Intent intent) {
        if (mContext.getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT) {
            return null;
        }
        boolean isNavShow = false;
        try {
             IWindowManager mWindowManager = WindowManagerGlobal.getWindowManagerService();
             isNavShow = mWindowManager.isNavigationBarShowing();
         } catch (Exception e) {}
        synchronized (mRecentTaskThumbnailList) {
            Slog.d(TAG, "getRecentTaskThumbnail----->" + intent);
            for (RecentTaskThumbnail recent : mRecentTaskThumbnailList) {
                if (recent.matchRecord(intent, isNavShow)) {
                    return recent.getTaskThumbnailLocked();
                }
            }
        }
        return null;
    }

    public  File getUserImagesDir(int userId) {
        return new File(Environment.getDataSystemDeDirectory(userId), IMAGES_DIRNAME);
    }

    private void clearRecentThumbNail() {
        synchronized (mRecentTaskThumbnailList) {
            try {
                mRecentTaskThumbnailList.clear();
                //File dir = new File(RecentTaskThumbnail.THUMBNAIL_DIR);
                File dir = getUserImagesDir(mCurrentUserId);
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().startsWith(RecentTaskThumbnail.THUMBNAIL_SUFFIX)) {
                            file.delete();
                        }
                    }
                }
            } catch (Exception e) {}
        }
    }
    public void handleLocaleChange() {
        //remove all thumbnail when local change
        if(!mRecentReady) {
            return;
        }
        clearRecentThumbNail();
    }

    public void handleUserChange(int userId) {
        clearRecentThumbNail();
        File recentDir = getUserImagesDir(userId);
        try {
            if (!recentDir.exists()) {
                recentDir.mkdirs();
            }
        } catch (Exception e) {}
        mCurrentUserId = userId;
    }
    private void updateRecentTaskThumbnail(Intent who, Bitmap thumbnail, boolean isNavShow) {
        if (mUpdateCanceled) {
            return;
        }
        Slog.d(TAG, "updateRecentTaskThumbnail----->" + who + ", current config--->" +  mContext.getResources().getConfiguration().orientation);
        if (who != null && (who.getFlags() & mExcludeFlags) == 0) {
            synchronized (mRecentTaskThumbnailList) {
                for (RecentTaskThumbnail recent : mRecentTaskThumbnailList) {
                    if (recent.matchRecord(who, isNavShow)) {
                        recent.setLastThumbnail(thumbnail);
                        return;
                    }
                }
                RecentTaskThumbnail recent = new RecentTaskThumbnail(this, who.getComponent().getPackageName(), who, mCurrentUserId, isNavShow);
                recent.setLastThumbnail(thumbnail);
                mRecentTaskThumbnailList.add(recent);
                recent.tryToSave();
            }
        }
    }

    public void recentTaskThumbnailListRemoveApp(String pkgName){
        Message msg = new Message();
        msg.what = MSG_SCHEDULE_REMOVE_THUMBNAIL;
        msg.obj = pkgName;
        mThumbnailHandler.sendMessage(msg);
    }

}
