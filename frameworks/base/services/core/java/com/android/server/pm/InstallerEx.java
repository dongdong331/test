/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.Context;
import android.util.Slog;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.IInstalld;
import android.os.RemoteException;

public class InstallerEx extends Installer {
    private static final String TAG = "InstallerEx";

    public InstallerEx(Context context) {
        super(context);
    }

    public int backupApp(String pkgName, String destPath, int callUid,
            int callGid) {
        if (!checkBeforeRemote()) return -1;
        if (pkgName == null || destPath == null) {
            Slog.d(TAG, "in backupApp, pkgName: " + pkgName + " destPath: "
                    + destPath + " have null value");
            return -1;
        }

        StringBuilder builder = new StringBuilder("backupapp ");
        builder.append(pkgName);
        builder.append(' ');
        builder.append(destPath);
        builder.append(' ');
        builder.append(callUid);
        builder.append(' ');
        builder.append(callGid);
        Slog.d(TAG, "backupApp: " + builder);
        int ret = -1;
        try{
            ret = mInstalld.backupApp(pkgName, destPath, callUid ,callGid);
        }catch(Exception e){
        	Slog.d(TAG, "backupApp failed", e);
        	ret = -1;
        }
        Slog.d(TAG, "backupApp: ret = " + ret);
        return ret;
    }

    public int restoreApp(String sourcePath, String pkgName, int uid, int gid) {
        if (!checkBeforeRemote()) return -1;
        if (sourcePath == null || pkgName == null) {
            Slog.d(TAG, "in restoreApp, sourcePath: " + sourcePath
                    + " pkgName: " + pkgName + " have null value");
            return -1;
        }
        StringBuilder builder = new StringBuilder("restoreapp ");
        builder.append(sourcePath);
        builder.append(' ');
        builder.append(pkgName);
        builder.append(' ');
        builder.append(uid);
        builder.append(' ');
        builder.append(gid);
        Slog.d(TAG, "restoreApp: " + builder);
        final String[] res;
        int ret = -1;
        try{
            ret = mInstalld.restoreApp(sourcePath, pkgName, uid, gid);
        }catch(Exception e){
        	Slog.d(TAG, "backupApp failed", e);
        	ret = -1;
        }
        Slog.d(TAG, "restoreApp: ret = " + ret);
        return ret;
    }
}
