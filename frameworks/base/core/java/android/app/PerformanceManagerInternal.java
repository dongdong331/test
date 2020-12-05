/**
 * Copyright (C) 2017 Spreadtrum Communications Inc.
 */
package android.app;

import android.app.IPerformanceManagerInternal;
import android.app.TaskThumbnail;
import android.content.Intent;
import android.content.Context;
import android.os.IBinder;
import android.os.ServiceManager;

/** @hide */
public class PerformanceManagerInternal {
    private static PerformanceManagerInternal sInstance;
    private static IPerformanceManagerInternal sPerformanceManagerService;
    private Context mContext;

    public static PerformanceManagerInternal getDefault() {
        if (sInstance != null) return sInstance;
        sInstance = new PerformanceManagerInternal(ActivityThread.currentApplication());
        return sInstance;
    }

    public PerformanceManagerInternal(Context context) {
        mContext = context;
        if (sPerformanceManagerService == null) {
            sPerformanceManagerService =
                    IPerformanceManagerInternal.Stub.asInterface(ServiceManager.getService("performance_fw"));
        }
    }

    public IPerformanceManagerInternal getService() {
        if (sPerformanceManagerService != null) {
            return sPerformanceManagerService;
        }
        return null;
    }

    /** @hide */
    public TaskThumbnail getTaskThumbnail(Intent intent) {
        TaskThumbnail thumb = null;
        if (sPerformanceManagerService != null) {
            try {
                thumb = sPerformanceManagerService.getTaskThumbnail(intent);
            } catch (Exception e) {}
        }
        return thumb;
    }

    public void windowReallyDrawnDone(String pkgName){
        if (sPerformanceManagerService != null) {
            try {
                sPerformanceManagerService.windowReallyDrawnDone(pkgName);
            } catch (Exception e) {}
        }
    }
    /** @hide */
    public boolean pkgSupportRecentThumbnail(String pkgName) {
       boolean support = false;
       if (sPerformanceManagerService != null) {
            try {
                support = sPerformanceManagerService.pkgSupportRecentThumbnail(pkgName);
            } catch (Exception e) {}
        }
        return support;
    }

    /** @hide */
    public void removePendingUpdateThumbTask(){
        if (sPerformanceManagerService != null) {
            try {
                sPerformanceManagerService.removePendingUpdateThumbTask();
            } catch (Exception e) {}
        }
    }

    public void removeApplcationSnapShot(String pkgName){
        if (sPerformanceManagerService != null) {
            try {
                sPerformanceManagerService.removeApplcationSnapShot(pkgName);
            } catch (Exception e) {}
        }
    }

    /** @hide */
    public void scheduleBoostRRForApp(boolean boost){
        if (sPerformanceManagerService != null) {
            try {
                sPerformanceManagerService.scheduleBoostRRForApp(boost);
            } catch (Exception e) {}
        }
    }

    /** @hide */
    public void scheduleBoostRRForAppName(boolean boost, String pkgName, int type){
        if (sPerformanceManagerService != null) {
            try {
                sPerformanceManagerService.scheduleBoostRRForAppName(boost, pkgName, type);
            } catch (Exception e) {}
        }
    }

    /** @hide */
    public void scheduleBoostWhenTouch(){
        if (sPerformanceManagerService != null) {
            try {
                sPerformanceManagerService.scheduleBoostWhenTouch();
            } catch (Exception e) {}
        }
    }
}