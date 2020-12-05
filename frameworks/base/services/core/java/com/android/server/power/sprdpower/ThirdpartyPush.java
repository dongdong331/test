/*
 ** Copyright 2016 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;

import android.os.Build;
import android.os.Environment;

import android.util.Slog;

import java.util.ArrayList;
import java.util.List;

public class ThirdpartyPush {

    static final String TAG = "PowerController.3PartyPush";

    static ThirdpartyPush sInstance;

    private final boolean DEBUG = isDebug();



    private final Context mContext;


    // a component name that will be used for associated-starting in third party push service
    // such as getui push service
    private final String[] mPresetAssociatedComponentNameList = new String[] {
        "com.igexin.sdk.GActivity",
    };


    // a component name that will be used for associated-starting in third party push service
    // such as getui push service
    private List<ComponentName> mAssociatedComponentList = new ArrayList<>();


    public static ThirdpartyPush getInstance(Context context) {
        synchronized (ThirdpartyPush.class) {
            if (sInstance == null) {
                sInstance = new ThirdpartyPush(context);
            }
            return sInstance;
        }
    }

    public ThirdpartyPush(Context context) {
        mContext = context;
    }

    public boolean isAssociatedComponent(ComponentName comp) {
        if(comp == null) return false;
        for(String s : mPresetAssociatedComponentNameList) {
            if(s.equals(comp.getClassName())) {
                return true;
            }
        }

        return mAssociatedComponentList.contains(comp);
    }


    public void loadAssociatedComponents() {

        List<PackageInfo> packages = mContext.getPackageManager().getInstalledPackagesAsUser(PackageManager.GET_ACTIVITIES, 0);
        for(PackageInfo pkg : packages){

            if(pkg !=null && pkg.applicationInfo !=null
                && (pkg.applicationInfo.flags &
                   (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP |ApplicationInfo.FLAG_SYSTEM)) == 0) {
                if (pkg !=null && pkg.activities !=null) {
                    for (int i=0; i<pkg.activities.length; i++) {
                        if ("com.igexin.sdk.PushActivityTask".equals(pkg.activities[i].taskAffinity)
                            && (pkg.activities[i].flags & ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS) != 0) {
                            if (DEBUG) Slog.d(TAG, "PKG:IGE " + pkg.packageName + " /" + pkg.activities[i].name
                                + " processName:" + pkg.activities[i].processName);
                            mAssociatedComponentList.add(new ComponentName(
                                    pkg.packageName, pkg.activities[i].name));
                        }
                    }
                }
            }
        }
    }

    private boolean isDebug(){
        return Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug");
    }
}
