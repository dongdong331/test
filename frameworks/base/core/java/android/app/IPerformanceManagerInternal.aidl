/*
 * Copyright (C) 2017 Spreadtrum communications Inc.
 *
 */

 package android.app;
 
 import android.content.Intent;
 import android.app.TaskThumbnail;

 /**
 * Add for PerformanceManager
 *
 * {@hide}
 */

 interface IPerformanceManagerInternal {
    void windowReallyDrawnDone(in String pkgName);
    TaskThumbnail getTaskThumbnail(in Intent intent);
    void removePendingUpdateThumbTask();
    boolean pkgSupportRecentThumbnail(in String pkgName);
    void removeApplcationSnapShot(in String pkgName);
    void scheduleBoostRRForApp(in boolean boost);
    void scheduleBoostRRForAppName(in boolean boost, String pkgName, int type);
    void scheduleBoostWhenTouch();
 }