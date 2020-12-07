/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.sprd.quickstepext.memory;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.widget.Toast;

import com.android.launcher3.R;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.sprd.ext.FeatureOption;
import com.sprd.ext.LogUtils;

public class TaskMeminfo{
    public static final String TAG = "TaskMeminfo";
    private static final long PROCESS_REMOVETASKS_DELAY_MS = 200;
    private static final long KUNIT = 1024;
    private static final long SI_KUNIT = 1000;//formatShortFileSize use FLAG_SI_UNITS, KB = B*1000
    private static final long GB_SIZES = KUNIT * KUNIT * KUNIT;

    public static void showReleaseMemorySize(final Context context, final boolean isRemoveView, final long avail) {
        BackgroundExecutor.get().submit(() -> {
            try {
                Thread.sleep(PROCESS_REMOVETASKS_DELAY_MS);
            } catch (Exception e) {
                LogUtils.e(TAG, "showReleaseMemorySize sleep failed, e:" + e);
            }
            long availMem = getMemoryInfo(context).availMem;
            long releaseSize = isRemoveView ? availMem - avail:0;
            if (LogUtils.DEBUG) {
                LogUtils.d(TAG, "isRemoveView:" + isRemoveView
                        + "--avail:" + avail + "--availMem:" + availMem);
            }
            Handler uiHandler = new Handler(Looper.getMainLooper());
            uiHandler.post(() -> makeText(context,releaseSize, availMem));
        });
    }

    public static void makeText(Context context, long releaseSize,long availSize) {
        if (releaseSize <= 0) {
            Toast.makeText(context,
                    context.getString(R.string.recents_nothing_to_clear), Toast.LENGTH_SHORT)
                    .show();
        } else {
            String release = Formatter.formatShortFileSize(context, releaseSize);
            String avail = Formatter.formatShortFileSize(context, availSize);
            Toast.makeText(context,
                    context.getString(R.string.recents_clean_finished_toast,
                            release.toUpperCase(), avail.toUpperCase()), Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public static ActivityManager.MemoryInfo getMemoryInfo(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
        }
        return memoryInfo;
    }

    public interface GetMeminfoCallback {
        void onGetMeminfoSuccess(long availMem, long totalMem);
    }

    public static void updateMeminfo(Context context, GetMeminfoCallback cb) {
        if (!FeatureOption.SPRD_SHOW_MEMINFO_SUPPORT) {
            return;
        }
        BackgroundExecutor.get().submit(() -> {
            ActivityManager.MemoryInfo memoryInfo = TaskMeminfo.getMemoryInfo(context);
            memoryInfo.totalMem = roundTotalRamSize(memoryInfo.totalMem);
            Handler uiHandler = new Handler(Looper.getMainLooper());
            if (cb != null) {
                uiHandler.post(() -> cb.onGetMeminfoSuccess(memoryInfo.availMem, memoryInfo.totalMem));
            }
        });
    }

    private static long roundTotalRamSize(long size) {
        long gbDivisionNum = size >= GB_SIZES ? 1 : 4;//Minimum match value: 1GB or 256MB
        long mbBlockSize = GB_SIZES / gbDivisionNum;

        //roundmbBlockSize * count
        long roundSize = (size + mbBlockSize - 1) / mbBlockSize *mbBlockSize;

        //FLAG_IEC_UNITS to FLAG_SI_UNITS, 1024 to 1000
        if (roundSize >= GB_SIZES) {
            roundSize = roundSize/KUNIT*SI_KUNIT/KUNIT*SI_KUNIT/KUNIT*SI_KUNIT;//xGB
        } else {
            roundSize = roundSize/KUNIT*SI_KUNIT/KUNIT*SI_KUNIT;//256MB 512MB or 768MB
        }
        if (LogUtils.DEBUG) {
            LogUtils.d(TAG, "roundStorageSize size: " + size + "--roundSize:" + roundSize);
        }
        return roundSize;
    }
}
