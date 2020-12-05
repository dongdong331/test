/**
 */
package com.android.server.power.sprdpower;

import android.app.usage.UsageEvents;
import android.app.usage.UsageEvents.Event;

import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.ParcelFormatException;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.os.BatteryStatsHelper;
import com.android.internal.util.JournaledFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.util.AtomicFile;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import com.android.internal.util.FastPrintWriter;

import android.os.BundleData;
import android.os.sprdpower.Util;

class AppUsageStatsCollection {

    static final String TAG = "SSense.AUStatsCollection";

    private final boolean DEBUG_LOG = Util.getDebugLog();
    private final boolean DEBUG = Util.isDebug() || DEBUG_LOG;
    private final boolean DEBUG_MORE = false;

    private final boolean SAVE_DUMP = false;

    private static final int TIME_INTERVAL = (10*60*1000); // 10mins

    // should not conflict with UsageEvents.Event
    // used to force update the current using time for current visible app
    static final int EVENT_TYPE_FORE_UPDATE = 0x100;

    private long mBeginTime;
    private long mEndTime;
    private long mLastTimeSaved;
    private final ArrayMap<String, AppUsageStats> mPackageStats = new ArrayMap<>();
    private final List<AppUsageStats> mUsageStatsList = new ArrayList<>();

    private final Object mLock = new Object();

    private final AppUsageStatsComparator mAppUsageStatsComparator = new AppUsageStatsComparator();

    static AppUsageStatsCollection sInstance;

    private final JournaledFile mFile;

    private final Handler mHandler;

    private long mLastTimeUsedAppStatsUpdated;
    private UsedAppStats mUsedAppStats;

    public long mNextDailyDeadline;

    private final ArrayList<AppUsageObserver> mAppUsageObservers
            = new ArrayList<AppUsageObserver>();

    // Apps that are visible
    private final ArrayList<String> mVisibleAppList = new ArrayList<>();

    private boolean mScreenOn = true;

    public static AppUsageStatsCollection getInstance() {
        synchronized (AppUsageStatsCollection.class) {
            if (sInstance == null) {
                sInstance = new AppUsageStatsCollection();
            }
            return sInstance;
        }
    }

    public AppUsageStatsCollection() {
        mHandler = new H(SmartSenseService.BackgroundThread.get().getLooper());

        File systemDir = new File(Environment.getDataDirectory(), "system");
        if (systemDir != null) {
            mFile = new JournaledFile(new File(systemDir, "appUsage.bin"),
                    new File(systemDir, "appUsage.bin.tmp"));
        } else {
            mFile = null;
        }

        mUsedAppStats = new UsedAppStats();

        readAppUsageStats();

        mLastTimeSaved = SystemClock.elapsedRealtime();
        mLastTimeUsedAppStatsUpdated = mLastTimeSaved;
        mNextDailyDeadline = 0;
    }


    //Message define
    static final int MSG_SAVE_TO_DISK = 0;
    static final int MSG_INFOR_LISTENER = 1;
    static final int MSG_UPDATE_USEDAPP = 2;
    static final int MSG_UPDATE_DAILY_INFO = 3;

    private class H extends Handler {
        public H(Looper looper) {
            super(looper);
        }

        String msg2Str(int msg) {
            final String msgStr[] = {"MSG_SAVE_TO_DISK",
                "MSG_INFOR_LISTENER",
                "MSG_UPDATE_USEDAPP",
                "MSG_UPDATE_DAILY_INFO",
            };

            if ((0 <= msg) && (msg < msgStr.length))
                return msgStr[msg];
            else
                return "Unknown message: " + msg;
        }

        @Override
        public void handleMessage(Message msg) {
            if (DEBUG_MORE) Slog.d(TAG, "handleMessage(" + msg2Str(msg.what) + ")");
            switch (msg.what) {
                case MSG_INFOR_LISTENER:
                    String packageName = (String)msg.obj;
                    int eventType = msg.arg1;
                    informAppUsageObservers(packageName, eventType);
                    break;
                case MSG_SAVE_TO_DISK:
                    writeAppUsageStats();
                    break;
                case MSG_UPDATE_USEDAPP:
                    updateUsedAppStats();
                    break;
                case MSG_UPDATE_DAILY_INFO:
                    onDailyUpdated();
                    break;
                default:
                    break;
            }
        }
    }

    void update(String packageName, int eventType) {
        if (packageName == null || "android".equals(packageName)) {
            checkTime(eventType);
            return;
        }

        if (eventType == UsageEvents.Event.SYSTEM_INTERACTION
            || eventType == UsageEvents.Event.NONE) {
            checkTime(eventType);
            return;
        }

        AppUsageStats usageStats = getOrCreateUsageStats(packageName);

        long now = System.currentTimeMillis(); //wall time
        if (usageStats != null) {
            usageStats.reportUsage(packageName, now, eventType, mScreenOn);
            if (DEBUG_MORE) Slog.d(TAG, "update:"+ usageStats);
        }

        mEndTime = now;

        long nowElapsed = SystemClock.elapsedRealtime();
        if (nowElapsed - mLastTimeUsedAppStatsUpdated > TIME_INTERVAL) {
            mLastTimeUsedAppStatsUpdated = nowElapsed;
            mHandler.removeMessages(MSG_UPDATE_USEDAPP);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_USEDAPP));
        }

        if (nowElapsed - mLastTimeSaved > TIME_INTERVAL) {
            mLastTimeSaved = nowElapsed;
            mHandler.removeMessages(MSG_SAVE_TO_DISK);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_SAVE_TO_DISK));
        }

        if ( now > mNextDailyDeadline) {
            mNextDailyDeadline = TimeUtil.getNextDailyDeadline();
            mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_DAILY_INFO));
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_INFOR_LISTENER, eventType, 0, packageName));
    }

    void reportAppTransition(BundleData data) {
        mVisibleAppList.clear();
        ArrayList<String> appList = data.getStringArrayListExtra(BundleData.DATA_EXTRA_VISIBLE_APPS);

        if (appList != null) {
            for (int i=0;i<appList.size();i++) {
                mVisibleAppList.add(appList.get(i));
            }
        }
    }

    void reportScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
    }

    void forceUpdateVisibleApp() {
        for (int i=0;i<mVisibleAppList.size();i++) {
            String packageName = mVisibleAppList.get(i);
            AppUsageStats usageStats = getOrCreateUsageStats(packageName);

            long now = System.currentTimeMillis(); //wall time
            if (usageStats != null)
                usageStats.reportUsage(packageName, now, EVENT_TYPE_FORE_UPDATE, mScreenOn);
        }
    }

    // return the the first 'size' favorite app list. if 'size' <= 0, means the whole list
    List<String> getFavoriteAppList(int size) {

        List<String> appList = new ArrayList<>();
        synchronized (mLock) {
            Collections.sort(mUsageStatsList, mAppUsageStatsComparator);

            //for (int i = 0; i < mUsageStatsList.size(); i++) {
            //    if (i<3) appList.add(mUsageStatsList.get(i).mPackageName);
            //    if (DEBUG) Slog.d(TAG, "getFavoriteAppList:"+ mUsageStatsList.get(i));
            //}
            int count = (size<=0 ? mUsageStatsList.size() : size);

            for (int i = 0; i < mUsageStatsList.size() && i<size; i++) {
                appList.add(mUsageStatsList.get(i).mPackageName);
            }
        }
        return appList;
    }

    // return the time interval between the next favorite hour
    // return > 0 in hour is valid, and -1 for invalid
    // packageName: the package name
    // currentTime: current time using wall time
    int getNextFavoriteTimeInterval(String packageName, long currentTime) {
        AppUsageStats usageStats = getOrCreateUsageStats(packageName);

        if (usageStats == null)
            return -1;

        return usageStats.getNextFavoriteTimeInterval(currentTime);
    }


    // return the time interval between the next favorite hour
    // return > 0 in hour is valid, and -1 for invalid
    // packageName: the package name
    int getNextFavoriteTimeInterval(String packageName) {
        AppUsageStats usageStats = getOrCreateUsageStats(packageName);

        if (usageStats == null)
            return -1;

        long now = System.currentTimeMillis(); //wall time
        return usageStats.getNextFavoriteTimeInterval(now);
    }

    // return the day AppUsageStatsHistory corresponding to the current time
    ArrayList<AppUsageStatsHistory> getCurrentAppUsageList() {
        ArrayList<AppUsageStatsHistory> usageList = new ArrayList<AppUsageStatsHistory>();
        synchronized (mLock) {
            for (int i = 0; i < mUsageStatsList.size(); i++) {
                usageList.add(mUsageStatsList.get(i).getCurrentAppUsageList());
            }
        }
        return usageList;
    }

    // return the hisory day AppUsageStatsHistory
    ArrayList<AppUsageStatsHistory> getHistoryAppUsageList() {

        long now = System.currentTimeMillis(); //wall time

        ArrayList<AppUsageStatsHistory> usageList = new ArrayList<AppUsageStatsHistory>();
        synchronized (mLock) {
            for (int i = 0; i < mUsageStatsList.size(); i++) {
                usageList.add(mUsageStatsList.get(i).getHistoryAppUsageList(now));
            }
        }
        return usageList;
    }

    // return the last used time (wall time) for the app
    long getLastUserUsedTimeForApp(String packageName) {
        AppUsageStats usageStats = getOrCreateUsageStats(packageName);

        if (usageStats == null)
            return -1;

        return usageStats.mLastTimeUserUsed;
    }


    // return the time interval between the next used hour
    // return > 0 in hour is valid, and -1 for invalid
    // packageName: the package name
    int getNextUsedTimeInterval(String packageName) {
        AppUsageStats usageStats = getOrCreateUsageStats(packageName);

        if (usageStats == null)
            return -1;

        long now = System.currentTimeMillis(); //wall time
        return usageStats.getNextUsedTimeInterval(now);
    }

    ArrayList<String> getUsedAppList() {
        return mUsedAppStats.getUsedAppList();
    }

    int[] getUsedAppIndexMaps() {
        return mUsedAppStats.getUsedAppIndexMaps();
    }

    int getUsedAppIndexCount() {
        return mUsedAppStats.getUsedAppIndexCount();
    }

    void registerAppUsageObserver(AppUsageObserver observer) {
        if (observer == null) return;
        long nowElapsed = SystemClock.elapsedRealtime();
        observer.timeRequested = nowElapsed;
        observer.timeLimit = nowElapsed + observer.timeDuration;
        mAppUsageObservers.add(observer);
    }

    static class AppUsageObserver {
        public static int FLAG_NOTIFY_ONLY_TIMEOUT = 1 << 0;
        public static int FLAG_NOTIFY_ONLY_USEDAPP_UPDATED = 1 << 1; 
        public static int FLAG_NOTIFY_ONLY_DAILY_UPDATED = 1 << 2; 

        public long timeDuration;
        public int notifyFlag;
        long timeLimit; // using SystemClock.elapsedRealtime()
        long timeRequested; // using SystemClock.elapsedRealtime()

        public void onTimeOut(){};
        public void onUsedAppUpdated(){};
        public void onDailyUpdated(){};
    }


    private void informAppUsageObservers(String packageName, int eventType) {
        long nowElapsed = SystemClock.elapsedRealtime();
        for (AppUsageObserver listener : mAppUsageObservers) {
            if (listener.timeDuration > 0 && nowElapsed >= listener.timeLimit) {
                listener.timeLimit = nowElapsed + listener.timeDuration;
                listener.onTimeOut();
            }
        }
    }

    private void onUsedAppUpdated() {
        for (AppUsageObserver listener : mAppUsageObservers) {
            if ((listener.notifyFlag & AppUsageObserver.FLAG_NOTIFY_ONLY_USEDAPP_UPDATED) != 0) {
                listener.onUsedAppUpdated();
            }
        }
    }

    private void onDailyUpdated() {
        for (AppUsageObserver listener : mAppUsageObservers) {
            if ((listener.notifyFlag & AppUsageObserver.FLAG_NOTIFY_ONLY_DAILY_UPDATED) != 0) {
                listener.onDailyUpdated();
            }
        }
    }
    private void writeAppUsageStats() {
        if (mFile == null) {
            Slog.w(TAG, "writeAppUsageStats: no file associated with this instance");
            return;
        }
        mLastTimeSaved = SystemClock.elapsedRealtime();

        Parcel out = Parcel.obtain();
        try {
            writeToParcel(out);

            FileOutputStream stream = new FileOutputStream(mFile.chooseForWrite());
            stream.write(out.marshall());
            stream.flush();
            FileUtils.sync(stream);
            stream.close();
            mFile.commit();
        } catch (Exception e) {
            Slog.w(TAG, "Error writing statistics", e);
            mFile.rollback();
        } finally {
            out.recycle();
        }

    }


    private void readAppUsageStats() {
        if (mFile == null) {
            Slog.w(TAG, "readAppUsageStats: no file associated with this instance");
            return;
        }

        Parcel in = Parcel.obtain();
        try {
            File file = mFile.chooseForRead();
            if (!file.exists()) {
                return;
            }
            FileInputStream stream = new FileInputStream(file);

            byte[] raw = BatteryStatsHelper.readFully(stream);
            in.unmarshall(raw, 0, raw.length);
            in.setDataPosition(0);
            stream.close();

            readFromParcel(in);
        } catch(Exception e) {
            Slog.e(TAG, "Error reading battery statistics", e);
        } finally {
            in.recycle();
        }

    }


    private void writeToParcel(Parcel out) {

        out.writeLong(mBeginTime);
        out.writeLong(mEndTime);
        out.writeLong(mLastTimeSaved);

        final int N = mPackageStats.size();
        out.writeInt(N);
        for (int i = 0; i < N; i++) {
            //String packageName = mPackageStats.keyAt(i);
            AppUsageStats history = mPackageStats.valueAt(i);
            history.writeToParcel(out);
        }

        mUsedAppStats.writeToParcel(out);

        if (DEBUG_MORE) {
            Slog.d(TAG, "writeToParcel mUsageStatsList SIZE:"+ mUsageStatsList.size());
            for (int iu = 0; iu < mUsageStatsList.size(); iu++) {
                Slog.d(TAG, " appStats:" + mUsageStatsList.get(iu));
            }
        }

        if (DEBUG_MORE) {
            Slog.d(TAG, " mUsedAppStats:" + mUsedAppStats);
        }

        if (SAVE_DUMP) dumpInfo();
    }

    private void readFromParcel(Parcel in) throws ParcelFormatException {
        mBeginTime = in.readLong();
        mEndTime = in.readLong();
        mLastTimeSaved =  in.readLong();

        final int N = in.readInt();
        for (int iu = 0; iu < N; iu++) {
            AppUsageStats usageStats = new AppUsageStats();
            usageStats.readFromParcel(in);
            if (usageStats.mPackageName != null) {
                mPackageStats.put(usageStats.mPackageName, usageStats);
                mUsageStatsList.add(usageStats);
            }
        }

        mUsedAppStats.readFromParcel(in);

        if (DEBUG_MORE) {
            Slog.d(TAG, "readFromParcel mUsageStatsList SIZE:"+ mUsageStatsList.size());
            for (int i = 0; i < mUsageStatsList.size(); i++) {
                Slog.d(TAG, " appStats:" + mUsageStatsList.get(i));
            }
        }

        if (DEBUG) {
            Slog.d(TAG, " mUsedAppStats:" + mUsedAppStats);
        }
    }

    private void dumpInfo() {

        AtomicFile aFile = new AtomicFile(new File(new File(Environment.getDataDirectory(), "system"), "appUsageInfo.txt"));
        FileOutputStream stream;
        try {
            stream = aFile.startWrite();
        } catch (IOException e) {
            Slog.e(TAG, "Failed to write state: " + e);
            return;
        }

        try {

            PrintWriter pw = new FastPrintWriter(stream);
            pw.println("AppUsageStatsCollection {");
            pw.print("mBeginTime:");
            pw.print(mBeginTime);
            pw.println();
            pw.print("mEndTime:");
            pw.print(mEndTime);
            pw.println();
            pw.print("mLastTimeSaved:");
            pw.print(mLastTimeSaved);
            pw.println();

            final int N = mPackageStats.size();
            pw.print("PackageSize:");
            pw.println(N);
            for (int i = 0; i < N; i++) {
                //String packageName = mPackageStats.keyAt(i);
                AppUsageStats history = mPackageStats.valueAt(i);
                history.dumpInfo(pw);
            }

            mUsedAppStats.dumpInfo(pw);

            pw.println("}");

            pw.flush();
        } catch (Exception e) {
            Slog.d(TAG, " Exception:" + e);
        } finally {
            aFile.finishWrite(stream);
        }
    }


    private void updateUsedAppStats() {
        try {
            mUsedAppStats.update(this);
            onUsedAppUpdated();
        } catch (Exception e) {
            Slog.d(TAG, " updateUsedAppStats Exception:" + e);
        }
    }

    private void checkTime(int eventType) {
        long nowElapsed = SystemClock.elapsedRealtime();
        if ((eventType == UsageEvents.Event.MOVE_TO_BACKGROUND || eventType == UsageEvents.Event.MOVE_TO_FOREGROUND)
            && (nowElapsed - mLastTimeUsedAppStatsUpdated > TIME_INTERVAL)) {
            mLastTimeUsedAppStatsUpdated = nowElapsed;
            mHandler.removeMessages(MSG_UPDATE_USEDAPP);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_USEDAPP));
        }

        if ((eventType == UsageEvents.Event.MOVE_TO_BACKGROUND || eventType == UsageEvents.Event.MOVE_TO_FOREGROUND)
            && (nowElapsed - mLastTimeSaved > TIME_INTERVAL)) {
            mLastTimeSaved = nowElapsed;
            mHandler.removeMessages(MSG_SAVE_TO_DISK);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_SAVE_TO_DISK));
        }

        long now = System.currentTimeMillis(); //wall time
        if ( now > mNextDailyDeadline) {
            mNextDailyDeadline = TimeUtil.getNextDailyDeadline();
            mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_DAILY_INFO));
        }
    }



    /**
     * Gets the UsageStats object for the given package, or creates one and adds it internally.
     */
    private AppUsageStats getOrCreateUsageStats(String packageName) {
        if (packageName == null||"android".equals(packageName)) return null; 
        AppUsageStats usageStats = mPackageStats.get(packageName);
        if (usageStats == null) {
            usageStats = new AppUsageStats();
            usageStats.mPackageName = packageName;
            usageStats.mBeginTimeStamp = mBeginTime;
            usageStats.mEndTimeStamp = mEndTime;
            mPackageStats.put(usageStats.mPackageName, usageStats);
            synchronized (mLock) {
                mUsageStatsList.add(usageStats);
            }
        }
        return usageStats;
    }

    private static class AppUsageStatsComparator implements Comparator<AppUsageStats> {
        @Override
        public final int compare(AppUsageStats a, AppUsageStats b) {

            if (a == null || b == null) {
                Slog.d(TAG, "compare:"+ " null");
                return -1;
            }

            if (a.mTotalTimeInForeground != b.mTotalTimeInForeground) {
                return (a.mTotalTimeInForeground > b.mTotalTimeInForeground ? -1 : 1);
            } else {
                if (a.mLaunchCount != b.mLaunchCount) {
                    return (a.mLaunchCount > b.mLaunchCount ? -1 : 1);
                } else {
                    return 0;
                }
            }
        }
    }


}
