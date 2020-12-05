/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.pm;

import static android.content.pm.PackageParser.isApkFile;
import static android.os.Trace.TRACE_TAG_PACKAGE_MANAGER;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.io.FileFilter;
import java.util.regex.Pattern;
import com.android.internal.util.ArrayUtils;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.ResolveInfo;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Trace;
import android.util.Log;
import android.util.Slog;

/* SPRD : Add for bug742368, when WeiChatClone exists, if we login in an app using
 * WeiChat account ,both  WeiChat and WeiChatClone should appear.
import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.Process;
import android.os.UserHandle;
import android.content.pm.UserInfo;
import android.content.pm.AppCloneUserInfo; */
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;

/**
 * Keep track of all those .apks everywhere.
 *
 * This is very central to the platform's security; please run the unit
 * tests whenever making modifications here:
 *
mmm frameworks/base/tests/AndroidTests
adb install -r -f out/target/product/passion/data/app/AndroidTests.apk
adb shell am instrument -w -e class com.android.unit_tests.PackageManagerTests com.android.unit_tests/android.test.InstrumentationTestRunner
 *
 * {@hide}
 */
public class PackageManagerServiceExUtils {
    static final String TAG = "PackageManagerServiceExUtils";
    public boolean debug = false;
    public boolean isOpen = false;
    public final File mPreloadInstallDir;
    public final File mDeleteRecord;
    public static final int THREAD_NUMS = deriveCoreNum();
    public final PackageManagerService mService;
    private static final boolean DEBUG_PACKAGE_SCANNING = false;
    private boolean isAppCloneMode = false;

    public PackageManagerServiceExUtils(PackageManagerService service) {
       mService = service;
       mPreloadInstallDir = new File(Environment.getRootDirectory(), "preloadapp");
       mDeleteRecord = new File(new File(Environment.getDataDirectory(),"app"), ".delrecord");
       deriveCoreNum();
    }

    /* Remove this API.
    public void filterSTK(List<ResolveInfo> list , boolean isOpen){
            if(isOpen)return;
            Iterator<ResolveInfo> infos = list.iterator();
            ResolveInfo info = null;
            while(infos.hasNext()){
                info = infos.next();
                if(info != null && info.activityInfo.packageName.startsWith("com.android.stk")){
                    Log.w(TAG, "remove "+info.activityInfo.packageName);
                    String packageName = info.activityInfo.packageName;
                    String className = info.activityInfo.name;
                    synchronized (mService.mPackages) {
                        PackageSetting pkgSetting = mService.mSettings.mPackages.get(packageName);
                        boolean res = pkgSetting.disableComponentLPw(className, 0);
                        Log.d(TAG, "Disabled className=" + className + ", res=" + res);
                    }
                    infos.remove();
                }
            }
    } */

    /* SPRD: Add for boot performance with multi-thread and preload scan @{*/
    public boolean isPreloadOrVitalApp(String path){
        if(path.startsWith("/system/preloadapp") || path.startsWith("/system/vital-app"))
              return true;
        return false;
   }

    public boolean isPreloadOrVitalApp(ApplicationInfo info) {
        if(info.sourceDir != null) {
            return info.sourceDir.startsWith("/system/preloadapp/")
                    || info.sourceDir.startsWith("/system/vital-app");
        }
        return false;
    }
    public boolean isDeleteApp(String packageName){
        BufferedReader br = null;
        try{
          br = new BufferedReader(new FileReader(mDeleteRecord));
          String lineContent = null;
          while( (lineContent = br.readLine()) != null){
              if(packageName.equals(lineContent)){
                   return true;
              }
          }
        }catch(IOException e){
           Log.e(TAG, " isDeleteApp IOException");
        }finally{
           try{
              if(br != null)
                br.close();
           }catch(IOException e){
               Log.e(TAG, " isDeleteApp Close ... IOException");
           }
        }
        return false;
    }

    public boolean delAppRecord(String packageName,int parseFlags){
      OutputStreamWriter writer = null;
      try{
         FileOutputStream fos = new FileOutputStream(mDeleteRecord, true);
         writer = new OutputStreamWriter(fos);
         writer.write(packageName +"\n");
         writer.flush();
         FileUtils.sync(fos);//Need to syncex plicitly
      }catch(IOException e){
           Log.e(TAG, "preloadapp unInstall record:  IOException");
      }finally{
           try{
            if(writer != null)
                writer.close();
           }catch(IOException e){}
      }
       return true;
    }

    public void parallelTakeAndScanPackageTracedLI(File dir, ParallelPackageParser parallelPackageParser, int fileCount,
                                                   int parseFlags, int scanFlags, long currentTime){
        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "parallelTakeAndScanPackage");
        try {
            parallelTakeAndScanPackageLI(dir, parallelPackageParser, fileCount, parseFlags, scanFlags, currentTime);
        } finally {
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }
    }

    private void parallelTakeAndScanPackageLI(File dir, ParallelPackageParser parallelPackageParser, int fileCount, int parseFlags,
                                                    int scanFlags, long currentTime){
        Log.d(TAG, "parallelTakeAndScanPackageLI: "+ dir.getAbsolutePath() + " , fileCount = " + fileCount);

        CountDownLatch connectedSignal = new CountDownLatch(THREAD_NUMS);

        int partLength = 0;
        if(fileCount % THREAD_NUMS == 0){
            partLength = fileCount / THREAD_NUMS;
        }else {
            partLength = fileCount / THREAD_NUMS + 1;
        }

        for(int i = 0; i < THREAD_NUMS; i++){
            takeAndScanPackageInThread(connectedSignal, i, partLength, parallelPackageParser, fileCount, parseFlags, scanFlags, currentTime);
        }
        waitForLatch(connectedSignal);
    }

    private void takeAndScanPackageInThread(final CountDownLatch connectedSignal, final int threadId, final int countsByThread , final ParallelPackageParser parallelPackageParser,
                                            final int fileCount, final int parseFlags, final int scanFlags, final long currentTime){
        new Thread("Take-And-Scan-Thread-" + threadId){
            @Override
            public void run(){
                if(debug) Log.d(TAG, Thread.currentThread().getName() + " start!");
                for (int i = threadId * countsByThread; i < (threadId + 1) * countsByThread; i++) {
                    if (i >= fileCount)
                        break;
                    mService.takeAndScanPackageLI(parallelPackageParser, parseFlags, scanFlags, currentTime);
                }
                connectedSignal.countDown();
                if(debug) Log.d(TAG, Thread.currentThread().getName() + " end!");
            }
        }.start();
    }

    /*@deprecated
    public void multiThreadScanDirTracedLI(File dir, final int parseFlags, int scanFlags, long currentTime) {
        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "scanDir");
        try {
            multiThreadScanDir(dir, parseFlags, scanFlags, currentTime);
        } finally {
         Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }
    }

    private void multiThreadScanDir(File dir,int flags,int scanMode,long currentTime){
        Log.d(TAG, "multi-thread scan directory  : " + dir);
        String[] files = dir.list();

        if (ArrayUtils.isEmpty(files)) {
            Log.d(TAG, "No files in app dir " + dir);
            return;
        }
        CountDownLatch mConnectedSignal = new CountDownLatch(THREAD_NUMS);

        if (DEBUG_PACKAGE_SCANNING) {
            Log.d(TAG, "Scanning app dir " + dir + " scanMode="
                    + scanMode + " flags=0x" + Integer.toHexString(flags));
        }
        int partLength = 0;
        if(files.length%THREAD_NUMS==0){
            partLength=files.length/THREAD_NUMS;
        }else {
            partLength=files.length/THREAD_NUMS+1;
        }

        for(int i=0;i<THREAD_NUMS;i++)
            multiThreadScanPartDir(i,mConnectedSignal,dir,partLength,flags,scanMode,currentTime);

        waitForLatch(mConnectedSignal);
    }

    private void multiThreadScanPartDir(final int part,final CountDownLatch connectedSignal,final File dir,final int partLength,
            final int flags,final int scanMode,final long currentTime){
        final String[] files = dir.list();

        new Thread("thread"+part){
            @Override
            public void run() {
                for (int i = part * partLength; i < (part + 1) * partLength; i++) {
                    if (i >= files.length)
                        break;
                    File file = new File(dir, files[i]);
                    final boolean isPackage = (isApkFile(file) || file.isDirectory())
                            && !PackageInstallerService.isStageName(file.getName());
                    if (!isPackage) {
                        // Ignore entries which are not packages
                        continue;
                    }

                    try {
                        mService.scanPackageTracedLI(file, flags|PackageParser.PARSE_MUST_BE_APK, scanMode,
                                currentTime, null);
                    } catch (PackageManagerException e) {
                        Slog.w(TAG,"Failed to parse " + file + ": "+ e.getMessage());

                        // Delete invalid userdata apps
                        if ((flags & PackageParser.PARSE_IS_SYSTEM) == 0
                                && e.error == PackageManager.INSTALL_FAILED_INVALID_APK) {
                            mService.logCriticalInfo(Log.WARN,"Deleting invalid package at " + file);
                            mService.removeCodePathLI(file);
                        }
                    }
                }
                connectedSignal.countDown();
            }
        }.start();

    }*/

    private  void waitForLatch(CountDownLatch latch) {
        for (;;) {
            try {
                if (latch.await(5000, TimeUnit.MILLISECONDS)) {
                    Slog.e(TAG, "waitForLatch done!" );
                    return;
                } else {
                    Slog.e(TAG, "Thread " + Thread.currentThread().getName()
                            + " still waiting for ready...");
                }
            } catch (InterruptedException e) {
                Slog.e(TAG, "Interrupt while waiting for scanning package to be ready.");
            }
        }
    }

    public static int deriveCoreNum(){
        int coreNum = 4;
        try {
            File dir = new File("/sys/devices/system/cpu/");
            File[] files = dir.listFiles(new CpuFilter());
            coreNum=files.length;
        } catch(Exception e) {
            e.printStackTrace();
        }
        if(coreNum >= 20 || coreNum <=0) {
            coreNum = 4;
        }
        Log.d(TAG, "CPU core number: "+coreNum);
        return coreNum;
    }

    /* SPRD : Add for bug786629, when WeiChatClone exists, if we login in an app using
     * WeiChat account ,both  WeiChat and WeiChatClone should appear. @{
    public void resolveIntentForAppClone(Intent intent, String resolvedType, int flags,
            int filterCallingUid, int userId, boolean resolveForStart, boolean allowDynamicSplits,
             final List<ResolveInfo> query) {
        if(query.size() == 1) {
            ResolveInfo result = query.get(0);
            String resultPkgName = result.activityInfo.packageName;
            int resultUid = result.activityInfo.applicationInfo.uid;
            String intentPkgName = intent.getPackage();

            if(isAppClonePackage(resultPkgName) && !isLaunchIntent(intent) && isResolveAcrossUid(filterCallingUid,resultUid)
                    && !isMakedForAppClone(intent) && userId == UserHandle.USER_SYSTEM && !Objects.equals(resultPkgName, intentPkgName)) {
                long id = Binder.clearCallingIdentity();
                List<UserInfo> allUserInfos = null;
                try {
                    allUserInfos = mService.sUserManager.getUsers(false);
                } catch (Exception e) {
                    Slog.d(TAG,"getUsers exception, ",e);
                } finally{
                    Binder.restoreCallingIdentity(id);
                }
                if (allUserInfos != null) {
                    for (UserInfo userInfo : allUserInfos) {
                        if(AppCloneUserInfo.isAppCloneUserId(userInfo.id)) {
                            PackageSetting ps = mService.mSettings.mPackages.get(resultPkgName);
                            if(ps != null && ps.getInstalled(userInfo.id)) {
                                Slog.d(TAG, "queryIntentActivitiesInternal for user: "+userInfo.id);
                                final List<ResolveInfo> queryClone = mService.queryIntentActivitiesInternal(intent, resolvedType,
                                        flags, filterCallingUid, userInfo.id,resolveForStart,allowDynamicSplits);
                                query.addAll(queryClone);
                            }
                        }
                    }
                }

                isAppCloneMode = false;
                if(query.size() >1) {
                    isAppCloneMode = true;
                }
            }
        }
    }

    public void enwrapIntentForAppClone(Intent intent, final List<ResolveInfo> query) {
        if(getAppCloneMode()) {
            Slog.d(TAG, "putParcelableArrayListExtra for intent: "+intent+" , queryList: "+query);
            intent.putParcelableArrayListExtra("queryList",(ArrayList<ResolveInfo>)query );
            isAppCloneMode = false;
        }
    }

    private boolean isLaunchIntent(Intent intent) {
        if(intent !=null && intent.getCategories() != null && intent.getCategories().contains(Intent.CATEGORY_LAUNCHER)) {
            return true;
        }
        return false;
    }

    private boolean isAppClonePackage (String pkgName) {
        return "com.tencent.mm".equals(pkgName) || "com.whatsapp".equals(pkgName)
                || "com.facebook.katana".equals(pkgName);
    }

    private boolean isResolveAcrossUid(int callingUid, int resultUid) {
        return !UserHandle.isSameApp(callingUid,resultUid);
    }

    private boolean isMakedForAppClone(Intent intent) {
        return (intent.getFlags() & Intent.FLAG_FOR_APPCLONE) != 0;
    }
    /* @}

    public ResolveInfo getAppcloneResolveInfo() {
        ResolveInfo ri = null;
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("android","com.android.internal.app.AppCloneResolverActivity"));
        final List<ResolveInfo> matches = mService.queryIntentActivitiesInternal(intent, null,
                MATCH_SYSTEM_ONLY | MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE
                        | MATCH_DISABLED_COMPONENTS, Binder.getCallingUid(),
                UserHandle.myUserId(), false, true);
        ri = matches.get(0);
        return ri;
    }

    public boolean getAppCloneMode() {
        return isAppCloneMode;
    }*/
}

 class CpuFilter  implements FileFilter{
    public boolean accept(File pathname) {
        if(Pattern.matches("cpu[0-9]", pathname.getName())) {
            return true;
        }
        return false;
    }
}
