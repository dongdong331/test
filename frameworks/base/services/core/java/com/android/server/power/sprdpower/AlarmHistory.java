/*
 ** Copyright 2017 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.os.Environment;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseArray;
import android.util.TimeUtils;

import com.android.internal.util.IndentingPrintWriter;

import libcore.io.IoUtils;

import java.nio.charset.StandardCharsets;

/**
 * Keeps track of wake up alarm count in apps.
 */
public class AlarmHistory {

    private static final String TAG = "AlarmHistory";

    // History for all users and all packages
    private SparseArray<ArrayMap<String,PackageHistory>> mHistory = new SparseArray<>();

    private final Object mLock = new Object();



    private static class PackageHistory {
        long beginUsedElapsedTime;
        int countDuringScreenOn;
        int countDuringScreenOff;
    }

    AlarmHistory() {}


    // report a wake up alarm of the app with "packageName" is sending
    public void reportWakeupAlarm(String packageName, int userId, boolean screenOn) {
        synchronized (mLock) {

            ArrayMap<String, PackageHistory> userHistory = getUserHistoryLocked(userId);
            PackageHistory packageHistory = getPackageHistoryLocked(userHistory, packageName);

            if (screenOn)
                packageHistory.countDuringScreenOn++;
            else
                packageHistory.countDuringScreenOff++;

            //Slog.d(TAG, "reportWakeupAlarm(), appName: " + packageName + ", screenON: " + screenOn + ", userId: " + userId);

        }
    }

    public int getWakeupAlarmCount(String packageName, int userId, boolean screenOn) {
        synchronized (mLock) {
            ArrayMap<String, PackageHistory> userHistory = getUserHistoryLocked(userId);
            PackageHistory packageHistory = getPackageHistoryLocked(userHistory, packageName);

            if (screenOn)
                return packageHistory.countDuringScreenOn;
            else
                return packageHistory.countDuringScreenOff;
        }
    }

    public int getWakeupAlarmCount(String packageName, int userId) {
        synchronized (mLock) {
            ArrayMap<String, PackageHistory> userHistory = getUserHistoryLocked(userId);
            PackageHistory packageHistory = getPackageHistoryLocked(userHistory, packageName);

            //Slog.d(TAG, "getWakeupAlarmCount(), appName: " + packageName + ", userId: " + userId + ", count:"
            //    + (packageHistory.countDuringScreenOn + packageHistory.countDuringScreenOff));

            return packageHistory.countDuringScreenOn + packageHistory.countDuringScreenOff;
        }
    }

    private PackageHistory getPackageHistoryLocked(ArrayMap<String, PackageHistory> userHistory,
            String packageName) {
        PackageHistory packageHistory = userHistory.get(packageName);
        if (packageHistory == null) {
            packageHistory = new PackageHistory();
            packageHistory.beginUsedElapsedTime = SystemClock.elapsedRealtime();
            packageHistory.countDuringScreenOn = 0;
            packageHistory.countDuringScreenOff = 0;
            userHistory.put(packageName, packageHistory);
        }
        return packageHistory;
    }


    public void dump(IndentingPrintWriter idpw, int userId) {
        idpw.println("Package wake up alarm stats:");
        idpw.increaseIndent();
        ArrayMap<String, PackageHistory> userHistory = mHistory.get(userId);

        if (userHistory == null) return;
        final int P = userHistory.size();
        for (int p = 0; p < P; p++) {
            final String packageName = userHistory.keyAt(p);
            final PackageHistory packageHistory = userHistory.valueAt(p);
            idpw.print("package=" + packageName);
            idpw.print(" countDuringScreenOn=" + packageHistory.countDuringScreenOn);
            idpw.print(" countDuringScreenOff=" + packageHistory.countDuringScreenOff);
            idpw.println();
        }
        idpw.println();
        idpw.decreaseIndent();
    }


    private ArrayMap<String, PackageHistory> getUserHistoryLocked(int userId) {
        ArrayMap<String, PackageHistory> userHistory = mHistory.get(userId);
        if (userHistory == null) {
            userHistory = new ArrayMap<>();
            mHistory.put(userId, userHistory);
        }
        return userHistory;
    }

}
