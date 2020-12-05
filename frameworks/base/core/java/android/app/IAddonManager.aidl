/*
 * Copyright (C) 2017 Spreadtrum communications Inc.
 *
 */

 package android.app;

 import android.content.pm.PackageInfo;

 import java.util.List;

 /**
 * Add for AddonManager feature
 *
 * {@hide}
 */
 interface IAddonManager {
    // AddonManagerService will collect all addon packages while system boot
    // call this interface to get addon packages for the given app package
    List<PackageInfo> getAppAddonFeatureList(in String pkgName);
 }