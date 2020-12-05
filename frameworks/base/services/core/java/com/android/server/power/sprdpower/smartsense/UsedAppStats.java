/*
 ** Copyright 2018 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.app.ActivityManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageEvents.Event;

import android.os.Parcel;
import android.os.ParcelFormatException;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.MathUtils;
import android.util.Slog;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Calendar;

////////////////////////////////////
import android.util.AtomicFile;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import com.android.internal.util.FastPrintWriter;
import android.os.sprdpower.Util;

public class UsedAppStats {

    static final String TAG = "SSense.UsedAStats";


    static final String DEFAULT_APP = "SYSTEM";

    private final boolean DEBUG_LOG = Util.getDebugLog();
    private final boolean DEBUG = Util.isDebug() || DEBUG_LOG;
    private final boolean DEBUG_MORE = false;

    private static final int INIT_COUNT = 24;

    private static final int DAYS_PER_WEEK = 7;

    private static final int SUNDAY = 0;
    private static final int MONDAY = 1;
    private static final int TUESDAY = 2;
    private static final int WEDNESDAY = 3;
    private static final int THURSDAY = 4;
    private static final int FRIDAY = 5;
    private static final int SATURDAY = 6;

    private final boolean mLowMemoryVersion = true; //ActivityManager.isLowRamDeviceStatic();


    private static final int MAX_SAVED_COUNT = (20*TimeUtil.getTimeSlotCount());


    /**
     * {@hide}
     */
    private long mLastTimeStamp; // wall time

    private long mFirstStartUpTimeStamp; // real elapsed time;
    private long mLastUpdateTimeStamp; // real elapsed time;


    private ArrayList<String> mUsedAppList = new ArrayList<>();

    private int[] mUsedAppIndexMaps = new int[0];

    // the total saved used app
    private int mUsedAppIndexCount;
    // the index to save new used app
    private int mCurUsedAppIndex;

    private final Object mLock = new Object();

    public UsedAppStats() {
        mUsedAppIndexCount = 0;
        mLastTimeStamp = 0;
        mCurUsedAppIndex = 0;
        mFirstStartUpTimeStamp = SystemClock.elapsedRealtime();
        mLastUpdateTimeStamp = 0;
    }


    long getLastTimeStamp () {
        return mLastTimeStamp;
    }

    void putUsedApp2(String packName, int timeSlot, boolean replace) {
        if (packName == null) return;

        if (DEBUG_LOG) Slog.w(TAG, "putUsedApp2:" + packName + " at timeSlot:" + timeSlot);


        if (mUsedAppIndexMaps == null) {
            mUsedAppIndexMaps = new int[INIT_COUNT];
        }

        if (mCurUsedAppIndex >= MAX_SAVED_COUNT) {
            mUsedAppIndexCount = MAX_SAVED_COUNT;
            // to reset the current save index
            mCurUsedAppIndex = 0;
        }

        // create more buckets when needed
        if (mUsedAppIndexCount >= mUsedAppIndexMaps.length) {
            final int newLength =  Math.max((mUsedAppIndexCount -mUsedAppIndexMaps.length + (TimeUtil.getTimeSlotCount() / 4)),
                (TimeUtil.getTimeSlotCount() / 2)) + mUsedAppIndexMaps.length ;
            mUsedAppIndexMaps = Arrays.copyOf(mUsedAppIndexMaps, newLength);

            if (DEBUG) Slog.w(TAG, "extent UsedAppIndexMaps length to:" + newLength
                + " mUsedAppIndexMaps.length:" + mUsedAppIndexMaps.length
                + " mUsedAppIndexCount:" + mUsedAppIndexCount);
        }

        // check the app if exist
        if (!mUsedAppList.contains(packName)) {
            if (DEBUG) Slog.w(TAG, "new Used App:" + packName + " orig size:" + mUsedAppList.size());
            mUsedAppList.add(packName);
        }

        // find index
        int index = mUsedAppList.indexOf(packName);
        if (index < 0) {
            if (DEBUG) Slog.w(TAG, "can not find " + packName + " in mUsedAppList!!");
            return;
        }

        if (DEBUG_MORE) Slog.w(TAG, " find " + packName + " in mUsedAppList in index:" + index);
        // replace the pre-index
        if (replace) {
            int savedIndex = mCurUsedAppIndex;
            if (mCurUsedAppIndex == 0 && mUsedAppIndexCount < MAX_SAVED_COUNT) {
                savedIndex = 0;
                mCurUsedAppIndex++;
            } else if (mCurUsedAppIndex == 0 && mUsedAppIndexCount >= MAX_SAVED_COUNT) {
                savedIndex = 0;
            } else {
                savedIndex = mCurUsedAppIndex-1;
            }
            mUsedAppIndexMaps[savedIndex] = index;
            if (mUsedAppIndexCount == 0)
                mUsedAppIndexCount++;
        } else {
            mUsedAppIndexMaps[mCurUsedAppIndex++] = index;

            // max count is MAX_SAVED_COUNT
            if (mUsedAppIndexCount < MAX_SAVED_COUNT)
                mUsedAppIndexCount++;
        }
    }

    void putUsedApp(String packName, int timeSlot, boolean replace) {
        if (packName == null) return;

        if (mUsedAppIndexMaps == null) {
            mUsedAppIndexMaps = new int[INIT_COUNT];
        }

        int CountPerDay = mUsedAppIndexCount % TimeUtil.getTimeSlotCount();

        if (DEBUG_LOG) Slog.w(TAG, "putUsedApp timeSlot:" + timeSlot
            + " mUsedAppIndexMaps.length:" + mUsedAppIndexMaps.length
            + " mUsedAppIndexCount:" + mUsedAppIndexCount);


        if (CountPerDay < timeSlot) {
            if (DEBUG) Slog.e(TAG, "putUsedApp mUsedAppIndexCount % TimeUtil.getTimeSlotCount() < timeSlot "
                + " timeSlot:" + timeSlot
                + " mUsedAppIndexCount:" + mUsedAppIndexCount
                + " CountPerDay:" + CountPerDay);
            mUsedAppIndexCount +=(timeSlot - CountPerDay);
        }

        // create more buckets when needed
        if (mUsedAppIndexCount >= mUsedAppIndexMaps.length) {
            final int newLength =  Math.max((mUsedAppIndexCount -mUsedAppIndexMaps.length + (TimeUtil.getTimeSlotCount() / 4)),
                (TimeUtil.getTimeSlotCount() / 2)) + mUsedAppIndexMaps.length ;
            mUsedAppIndexMaps = Arrays.copyOf(mUsedAppIndexMaps, newLength);

            if (DEBUG) Slog.w(TAG, "extent UsedAppIndexMaps length to:" + newLength
                + " mUsedAppIndexMaps.length:" + mUsedAppIndexMaps.length
                + " mUsedAppIndexCount:" + mUsedAppIndexCount);
        }

        // check the app if exist
        if (!mUsedAppList.contains(packName)) {
            if (DEBUG) Slog.w(TAG, "new Used App:" + packName + " orig size:" + mUsedAppList.size());
            mUsedAppList.add(packName);
        }

        // find index
        int index = mUsedAppList.indexOf(packName);
        if (index < 0) {
            if (DEBUG) Slog.w(TAG, "can not find " + packName + " in mUsedAppList!!");
            return;
        }

        if (DEBUG_MORE) Slog.w(TAG, " find " + packName + " in mUsedAppList in index:" + index);

        // save to index maps
        if (timeSlot < CountPerDay/* && replace*/) {
            int savedIndex = mUsedAppIndexCount - CountPerDay + timeSlot;
            if (DEBUG) Slog.w(TAG, " relace " + savedIndex + " with timeslot:" 
                + timeSlot + " mUsedAppIndexCount:" + mUsedAppIndexCount);
            mUsedAppIndexMaps[savedIndex] = index;
        } else {
            mUsedAppIndexMaps[mUsedAppIndexCount++] = index;
        }
    }


    void update(AppUsageStatsCollection appUsageStatsCollection) {
        if (appUsageStatsCollection == null) return;

        appUsageStatsCollection.forceUpdateVisibleApp();

        long nowElapsed = SystemClock.elapsedRealtime();

        ArrayList<AppUsageStatsHistory> cUsageList = null;
        if ((nowElapsed - mFirstStartUpTimeStamp < 2 * TimeUtil.getTimeSlotDuration())
            || (mLastUpdateTimeStamp <= mFirstStartUpTimeStamp)){
            if (DEBUG) Slog.d(TAG, " use history usage stats!");
            cUsageList = appUsageStatsCollection.getHistoryAppUsageList();
        } else {
            cUsageList = appUsageStatsCollection.getCurrentAppUsageList();
        }

        if (DEBUG_MORE) {
            for (int i = 0; i < cUsageList.size(); i++) {
                Slog.d(TAG, "cUsageList: " + i + ":" + cUsageList.get(i));
            }
        }
        long now = System.currentTimeMillis(); //wall time

        int startTimeSlot = 0;
        if (mLastTimeStamp > 0) {
            startTimeSlot = (int) (TimeUtil.getMillis(mLastTimeStamp) /TimeUtil.getTimeSlotDuration());
        }

        int endTimeSlot = (int) (TimeUtil.getMillis(now) /TimeUtil.getTimeSlotDuration());

        // check if in this case: last shutdown at 10:00, and boot at 22:00 ???


        if (DEBUG) Slog.w(TAG, " update() startTimeSlot:" + startTimeSlot + " endTimeSlot:" + endTimeSlot);

        for (int i=startTimeSlot; i<=endTimeSlot; i++) {
            String app = getAppAtTimeSlotWithMaxForegroundTime(i, cUsageList);
            int index = 0;

            boolean replace = false;
            if (i == startTimeSlot) replace = true;

            // see as a system app
            if (app == null) {
                putUsedApp2(DEFAULT_APP, i, replace);
            } else {
                putUsedApp2(app, i, replace);
            }
        }

        // save the time stamp
        mLastTimeStamp = now;
        mLastUpdateTimeStamp = nowElapsed;

        if (DEBUG_LOG) {
            for (int i=0;i<mUsedAppList.size();i++) {
                Slog.d(TAG, "Used app : " + i + ":" +mUsedAppList.get(i));
            }

            Slog.d(TAG, "mUsedAppIndexCount : " + mUsedAppIndexCount);

            if (mUsedAppIndexMaps != null) {
                Slog.d(TAG, "mUsedAppIndexMaps.length: " + mUsedAppIndexMaps.length);
                for (int i=0;i<mUsedAppIndexMaps.length;i++) {
                    if (DEBUG_MORE) Slog.d(TAG, "UsedAppIndexMaps:" + i + ":" +mUsedAppIndexMaps[i]);
                }
            }
        }

    }

    ArrayList<String> getUsedAppList() {
        ArrayList<String> appList = new ArrayList<String>();
        synchronized (mLock) {
            for (int i=0;i<mUsedAppList.size();i++) {
                appList.add(mUsedAppList.get(i));
            }
        }
        return appList;
    }

    int[] getUsedAppIndexMaps() {
        synchronized (mLock) {
            int size = mUsedAppIndexCount;
            if (size > mUsedAppIndexMaps.length) {
                if (DEBUG) Slog.d(TAG, "Warning: getUsedAppIndexMaps : mUsedAppIndexCount > mUsedAppIndexMaps.length"
                    + " mUsedAppIndexCount:" + mUsedAppIndexCount + " mUsedAppIndexMaps.length:" + mUsedAppIndexMaps.length);
                size = mUsedAppIndexMaps.length;
            }

            int[] maps = new int[size];
            for (int i = 0; i < size; i++) {
                maps[i] = mUsedAppIndexMaps[i];
            }
            return maps;
        }
    }

    int getUsedAppIndexCount() {
        return mUsedAppIndexCount;
    }

    void writeToParcel(Parcel out) {

        out.writeLong(mLastTimeStamp);
        int appSize = mUsedAppList.size();
        out.writeInt(appSize);
        for (int i=0; i<appSize; i++) {
            out.writeString(mUsedAppList.get(i));
        }

        int arrayLength = mUsedAppIndexMaps.length;
        out.writeInt(arrayLength);
        if (mUsedAppIndexCount > arrayLength) {
            if (DEBUG) Slog.w(TAG, " writeToParcel() mUsedAppIndexCount > arrayLength");
            mUsedAppIndexCount = arrayLength;
        }

        if (mCurUsedAppIndex > mUsedAppIndexCount) {
            if (DEBUG) Slog.w(TAG, " writeToParcel() mCurUsedAppIndex > mUsedAppIndexCount");
            mCurUsedAppIndex = mUsedAppIndexCount;
        }
        out.writeInt(mCurUsedAppIndex);
        out.writeInt(mUsedAppIndexCount);
        for (int i=0; i<mUsedAppIndexCount; i++) {
            out.writeInt(mUsedAppIndexMaps[i]);
        }
    }

    void readFromParcel(Parcel in) throws ParcelFormatException {
        mLastTimeStamp = in.readLong();
        int appSize = in.readInt();

        for (int i=0; i<appSize; i++) {
            String app = in.readString();
            if (app != null)
                mUsedAppList.add(app);
        }

        int arrayLength = in.readInt();
        mCurUsedAppIndex = in.readInt();
        mUsedAppIndexCount = in.readInt();
        if (arrayLength < mUsedAppIndexCount)
            arrayLength = mUsedAppIndexCount + TimeUtil.getTimeSlotCount()/2;
        mUsedAppIndexMaps = new int[arrayLength];
        for (int i=0; i<mUsedAppIndexCount; i++) {
            mUsedAppIndexMaps[i] = in.readInt();
        }

        //if (DEBUG) Slog.d(TAG, " readFromParcel() " + this);
    }


    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(TAG);
        result.append("{\nmLastTimeStamp: " + mLastTimeStamp
            + "\nmUsedAppIndexCount: " + mUsedAppIndexCount
            + "\nmCurUsedAppIndex: " + mCurUsedAppIndex);

        for (int i=0;i<mUsedAppList.size();i++) {
            result.append("\n " + i + ":" +mUsedAppList.get(i));
        }

        if (mUsedAppIndexMaps != null) {
            result.append("\nmUsedAppIndexMaps.length: " + mUsedAppIndexMaps.length
                + "\nmUsedAppIndexMaps[");
            for (int i=0;i<mUsedAppIndexMaps.length;i++) {
                result.append("," +mUsedAppIndexMaps[i]);
            }
            result.append("\n]");
        }

        result.append("\n}");
        return result.toString();
    }

    private String getAppAtTimeSlotWithMaxForegroundTime(int timeSlot,
        ArrayList<AppUsageStatsHistory> cUsageList) {

        if (timeSlot < 0 || timeSlot >= TimeUtil.getTimeSlotCount() || cUsageList == null) return null;

        int maxIndex = 0;
        long maxForegroundTime = 0;
        for (int i = 0; i < cUsageList.size(); i++) {
            //if (DEBUG) Slog.d(TAG, "cUsageList: " + i + ":" + cUsageList.get(i).mPackageName + " bucketcout:" + cUsageList.get(i).mBucketCount);
            //if (DEBUG) Slog.d(TAG, "cUsageList: " + i + ":" + cUsageList.get(i));
            long foregroundTime = 0;
            if (cUsageList.get(i).mDayCount <=1)
                foregroundTime = cUsageList.get(i).mTimeInForeground[timeSlot];
            else
                foregroundTime = cUsageList.get(i).mTimeInForeground[timeSlot]/cUsageList.get(i).mDayCount;

            if (foregroundTime > maxForegroundTime) {
                maxForegroundTime = foregroundTime;
                maxIndex = i;
            }
        }

        if (maxForegroundTime == 0)
            return null;

        return cUsageList.get(maxIndex).mPackageName;
    }

    private String getAppAtTimeSlotWithMaxLaunchCount(int timeSlot,
        ArrayList<AppUsageStatsHistory> cUsageList) {

        if (timeSlot < 0 || timeSlot >= TimeUtil.getTimeSlotCount() || cUsageList == null) return null;

        int maxIndex = 0;
        long maxLaunchCount = 0;
        for (int i = 0; i < cUsageList.size(); i++) {
            //Slog.d(TAG, "cUsageList: " + i + ":" + cUsageList.get(i));
            long launchCount = 0;
            if (cUsageList.get(i).mDayCount <=1)
                launchCount = cUsageList.get(i).mLaunchCount[timeSlot];
            else
                launchCount = cUsageList.get(i).mLaunchCount[timeSlot]/cUsageList.get(i).mDayCount;

            if (launchCount > maxLaunchCount) {
                maxLaunchCount = launchCount;
                maxIndex = i;
            }
        }

        if (maxLaunchCount == 0)
            return null;

        return cUsageList.get(maxIndex).mPackageName;
    }


    void dumpInfo(PrintWriter pw) {

        pw.println("UsedAppStats {");
        pw.print("mLastTimeStamp:");
        pw.print(mLastTimeStamp);
        pw.println();
        int appSize = mUsedAppList.size();
        pw.print("mUsedAppSize:");
        pw.print(appSize);
        pw.println();

        pw.print("mUsedAppList{");
        for (int i = 0; i < appSize; i++) {
            pw.print(mUsedAppList.get(i));
            pw.print(";");
        }
        pw.println("}");

        int arrayLength = mUsedAppIndexMaps.length;
        if (mUsedAppIndexCount > arrayLength) {
            mUsedAppIndexCount = arrayLength;
        }

        pw.print("mUsedAppIndexCount:");
        pw.print(mUsedAppIndexCount);
        pw.println();
        pw.print("mUsedAppIndexMaps[");
        for (int i = 0; i < mUsedAppIndexCount; i++) {
            pw.print(mUsedAppIndexMaps[i]);
            pw.print(",");
        }
        pw.println("]");

        pw.println("}");
    }


}
