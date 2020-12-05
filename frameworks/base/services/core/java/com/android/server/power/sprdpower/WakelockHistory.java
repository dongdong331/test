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
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps track of long duration PARTIAL_WAKE_LOCK wakelock in apps.
 */
public class WakelockHistory {

    private static final String TAG = "WakelockHistory";

    // History for all users and all packages
    private SparseArray<ArrayMap<String,PackageHistory>> mHistory = new SparseArray<>();

    private final Object mLock = new Object();


    static class WakelockInfo {
        String pkgName;
        String tag;
        int uid;
        long duration;

        public WakelockInfo(String _pkgName, String _tag, int _uid, long _duration) {
            pkgName = _pkgName;
            tag = _tag;
            uid = _uid;
            duration = _duration;
        }

        public boolean matches(String _pkgName, String _tag) {
            if (pkgName != null && pkgName.equals(_pkgName)
                && tag != null && tag.equals(_tag))
                return true;

            return false;
        }

    }


    private class PackageHistory {

        // the time notify to user that this app has a long duration wakelock
        // time always in ELAPSED
        long lastNotifyTime;

        // max duration
        long maxDuration;

        // app is stopped
        boolean stopped;

        final ArrayList<WakelockInfo> wakelocks = new ArrayList<WakelockInfo>();

        PackageHistory() {
            lastNotifyTime = 0;
            maxDuration = 0;
            stopped = false;
        }

        // return true: for this a new wakelock, and is added sucessfully.
        //          false: for others
        boolean add(WakelockInfo wakelock) {
            if (wakelock == null || wakelock.pkgName == null) {
                return false;
            }

            boolean found = false;
            for (int i = wakelocks.size()-1; i >= 0; i--) {
                WakelockInfo wl = wakelocks.get(i);
                if (wl != null && wl.matches(wakelock.pkgName, wakelock.tag)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                wakelocks.add(wakelock);
                if (wakelock.duration > maxDuration)
                    maxDuration = wakelock.duration;
            }
            return (!found);
        }

        boolean remove(final String packageName, final String tag) {
            if (packageName == null || tag == null) {
                return false;
            }
            boolean didRemove = false;
            long newMaxDuration = 0;
            long newEnd = Long.MAX_VALUE;
            int newFlags = 0;
            for (int i = wakelocks.size()-1; i >= 0; i--) {
                WakelockInfo wl = wakelocks.get(i);
                if (wl != null && wl.matches(packageName, tag)) {
                    wakelocks.remove(i);
                    didRemove = true;
                } else {
                    if (wl.duration > newMaxDuration) {
                        newMaxDuration = wl.duration;
                    }
                }
            }
            if (didRemove) {
                // commit the new maxDuration
                maxDuration = newMaxDuration;
            }
            return didRemove;
        }

    }

    WakelockHistory() {}


    // report a PARTIAL_WAKE_LOCK wakelock
    // return true: for this a new wakelock, and is added sucessfully.
    //          false: for others
    public boolean reportWakelock(WakelockInfo wakelock) {
        if (wakelock == null) return false;

        boolean added = false;
        int userId = UserHandle.getUserId(wakelock.uid);
        synchronized (mLock) {

            ArrayMap<String, PackageHistory> userHistory = getUserHistoryLocked(userId);
            PackageHistory packageHistory = getPackageHistoryLocked(userHistory, wakelock.pkgName);

            added = packageHistory.add(wakelock);
            if (packageHistory.stopped) { // this app has been stopped before
                packageHistory.stopped = false;
                added = true;
            }

            Slog.d(TAG, "reportWakelock(), appName: " + wakelock.pkgName
                + ", tag: " + wakelock.tag + ", uid: " + wakelock.uid + " ,duration:" + wakelock.duration);

        }
        return added;
    }

    // return the app packages that hold long duration PARTIAL_WAKE_LOCK wakelock
    // with holding duration >= duration
    // @param ignoreNotified: true, then get all the packages that has maxDuration >= duration
    //      false: only get the packages that has maxDuration >= duration && has not notify to user
    public List<String> getPackagesWithDuration(int userId, long duration, boolean ignoreNotified) {
        List<String> list = new ArrayList<String>();
        synchronized (mLock) {
            ArrayMap<String, PackageHistory> userHistory = getUserHistoryLocked(userId);
            for (int i=0;i<userHistory.size();i++) {
                String pkgName = userHistory.keyAt(i);
                PackageHistory packageHistory = userHistory.valueAt(i);
                if (packageHistory.maxDuration >= duration
                    && (ignoreNotified || (packageHistory.lastNotifyTime == 0)))
                    list.add(pkgName);
            }
        }

        return list;
    }

    // note the app hold a long duration PARTIAL_WAKE_LOCK wakelock
    // has been notified to user
    public void noteNotifyUser(String packageName, int userId) {
        synchronized (mLock) {
            ArrayMap<String, PackageHistory> userHistory = getUserHistoryLocked(userId);
            PackageHistory packageHistory = getPackageHistoryLocked(userHistory, packageName);

            packageHistory.lastNotifyTime = SystemClock.elapsedRealtime();
        }
    }

    public void noteAppStopped(String packageName, int userId) {
        synchronized (mLock) {
            ArrayMap<String, PackageHistory> userHistory = getUserHistoryLocked(userId);
            PackageHistory packageHistory = getPackageHistoryLocked(userHistory, packageName);

            packageHistory.lastNotifyTime = 0;
            packageHistory.stopped = true;
        }
    }

    private PackageHistory getPackageHistoryLocked(ArrayMap<String, PackageHistory> userHistory,
            String packageName) {
        PackageHistory packageHistory = userHistory.get(packageName);
        if (packageHistory == null) {
            packageHistory = new PackageHistory();
            userHistory.put(packageName, packageHistory);
        }
        return packageHistory;
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
