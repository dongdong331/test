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
import java.util.Calendar;


import android.util.AtomicFile;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import com.android.internal.util.FastPrintWriter;

import android.os.sprdpower.Util;

public class AppUsageStats {

    static final String TAG = "SSense.AUStats";

    private final boolean DEBUG_LOG = Util.getDebugLog();
    private final boolean DEBUG = Util.isDebug() || DEBUG_LOG;
    private final boolean DEBUG_MORE = false;

    private static final long ONE_MIN = (60 * 1000);
    private static final long ONE_HOUR = (60 * 60 * 1000);
    private static final long ONE_DAY = (24 * 60 * 60 * 1000);

    private static final int DAYS_PER_WEEK = 7;

    private static final int SUNDAY = 0;
    private static final int MONDAY = 1;
    private static final int TUESDAY = 2;
    private static final int WEDNESDAY = 3;
    private static final int THURSDAY = 4;
    private static final int FRIDAY = 5;
    private static final int SATURDAY = 6;

    private final boolean mLowMemoryVersion = true; //ActivityManager.isLowRamDeviceStatic();

    /**
     * {@hide}
     */
    public String mPackageName;

    /**
     * {@hide}
     */
    public long mBeginTimeStamp;

    /**
     * {@hide}
     */
    public long mEndTimeStamp;

    /**
     * Last time used by the user with an explicit action (notification, activity launch).
     * {@hide}
     */
    public long mLastTimeUsed;

    /**
     * {@hide}
     */
    public long mTotalTimeInForeground;

    /**
     * {@hide}
     */
    public int mLaunchCount;

    /**
     * {@hide}
     */
    public int mLastEvent;


    private long mLastTimeStampUsed;

    /**
     * Last time used by the user with an explicit action (activity launch).
     * {@hide}
     */
    public long mLastTimeUserUsed;

    private AppUsageStatsHistory[] mHistorys;
    private AppUsageStatsHistory mWorkDays;
    private AppUsageStatsHistory mWeekEnd;
    private AppUsageStatsHistory mCurrent;

    // for test
    private final boolean mUseAppUsageStatsSaver = false;
    private AppUsageStatsSaver mAppUsageStatsSaver;

    public AppUsageStats() {
        if (mLowMemoryVersion) {
            mWorkDays = new AppUsageStatsHistory();
            mWeekEnd = new AppUsageStatsHistory();
        } else {
            mHistorys = new AppUsageStatsHistory[DAYS_PER_WEEK];
            for (int i = 0; i < DAYS_PER_WEEK; i++) {
                mHistorys[i] = new AppUsageStatsHistory();
            }
        }
        mCurrent = new AppUsageStatsHistory();

        // for test
        if (mUseAppUsageStatsSaver) {
            mAppUsageStatsSaver = new AppUsageStatsSaver();
        }
    }

    // realTime: time using wall time
    void reportUsage(String packageName, long realTime, int eventType, boolean screenOn) {
        if (packageName == null || eventType == mLastEvent) return;

        boolean timeSet = timeEverSet(mLastTimeUsed, realTime);

        if (timeSet) {
            if (DEBUG) Slog.w(TAG, "reportUsage:"+ " time is changed!! for " + packageName
                + " mLastTimeUsed:" + mLastTimeUsed
                + " realTime:" + realTime
                + " mLastTimeStampUsed:" + mLastTimeStampUsed
                + " nowElapsed:" + SystemClock.elapsedRealtime());

            if (isStatefulEvent(eventType)) {
                if (eventType != AppUsageStatsCollection.EVENT_TYPE_FORE_UPDATE)
                    mLastEvent = eventType;

                mLastTimeUsed = realTime;
                mLastTimeStampUsed = SystemClock.elapsedRealtime();
            }

            mEndTimeStamp = realTime;

            if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                mLaunchCount += 1;
            }

            if (isUserEvent(eventType)) {
                mLastTimeUserUsed = realTime;
            }
            return;
        }

        if (!screenOn && mLastEvent == UsageEvents.Event.MOVE_TO_FOREGROUND
            && eventType != UsageEvents.Event.MOVE_TO_BACKGROUND
            && (realTime - mLastTimeUsed) > 30*ONE_MIN) {
            if (DEBUG) Slog.w(TAG, "reportUsage:"+ " NO changed to background for screen OFF " + packageName);
            eventType = UsageEvents.Event.MOVE_TO_BACKGROUND;
        }

        if (mLowMemoryVersion) {
            if (isWeekend(realTime)) {
                mWeekEnd.reportUsage(packageName, mLastTimeUsed, realTime, mLastEvent, eventType);
            } else {
                mWorkDays.reportUsage(packageName, mLastTimeUsed, realTime, mLastEvent, eventType);
            }
        } else {
            getAppUsageStatsHistory(realTime).reportUsage(packageName, mLastTimeUsed, realTime, mLastEvent, eventType);
        }

        if (mCurrent.needUpdateDailyCount(realTime)) {
            mCurrent.reset();
        }

        mCurrent.reportUsage(packageName, mLastTimeUsed, realTime, mLastEvent, eventType);

        // TODO(adamlesinski): Ensure that we recover from incorrect event sequences
        // like double MOVE_TO_BACKGROUND, etc.
        if (eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                eventType == UsageEvents.Event.END_OF_DAY ||
                eventType == AppUsageStatsCollection.EVENT_TYPE_FORE_UPDATE) {
            if (mLastEvent == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    mLastEvent == UsageEvents.Event.CONTINUE_PREVIOUS_DAY) {
                mTotalTimeInForeground += realTime - mLastTimeUsed;
                if (mLastTimeUsed == 0) {
                    Slog.w(TAG, "reportUsage:"+ " add foreground time but mLastTimeUsed is 0!!");
                }
            }
        }

        // for test
        if (mUseAppUsageStatsSaver && mAppUsageStatsSaver != null) {
            mAppUsageStatsSaver.reportUsage(packageName, realTime, eventType, mLastEvent);
        }

        if (isStatefulEvent(eventType)) {
            if (eventType != AppUsageStatsCollection.EVENT_TYPE_FORE_UPDATE)
                mLastEvent = eventType;

            mLastTimeUsed = realTime;
            mLastTimeStampUsed = SystemClock.elapsedRealtime();
        }

        if (isUserEvent(eventType)) {
            mLastTimeUserUsed = realTime;
        }

        //if (eventType != UsageEvents.Event.SYSTEM_INTERACTION) {
        //    mLastTimeUsed = realTime;
        //}
        mEndTimeStamp = realTime;

        if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            mLaunchCount += 1;
        }

        //if (DEBUG) Slog.d(TAG, "reportUsage:"+ " mWorkDays:" + mWorkDays);
    }

    // return the time interval between the next favorite hour
    // return > 0 in hour is valid, and -1 for invalid
    // currentTime: current time using wall time
    int getNextFavoriteTimeInterval(long currentTime) {
        int nextHourInterval = -1;
        if (mLowMemoryVersion) {
            if (isWeekend(currentTime)) {
                nextHourInterval = mWeekEnd.getNextFavoriteTimeInterval(currentTime);
            } else {
                nextHourInterval = mWorkDays.getNextFavoriteTimeInterval(currentTime);
            }
        } else {
            nextHourInterval = getAppUsageStatsHistory(currentTime).getNextFavoriteTimeInterval(currentTime);
        }
        return nextHourInterval;
    }

    // return the time interval between the next used hour
    // return > 0 in hour is valid, and -1 for invalid
    // currentTime: current time using wall time
    int getNextUsedTimeInterval(long currentTime) {
        int nextHourInterval = -1;
        if (mLowMemoryVersion) {
            if (isWeekend(currentTime)) {
                nextHourInterval = mWeekEnd.getNextUsedTimeInterval(currentTime);
            } else {
                nextHourInterval = mWorkDays.getNextUsedTimeInterval(currentTime);
            }
        } else {
            nextHourInterval = getAppUsageStatsHistory(currentTime).getNextUsedTimeInterval(currentTime);
        }
        return nextHourInterval;
    }


    // return the day AppUsageStatsHistory corresponding to the current time
    AppUsageStatsHistory getCurrentAppUsageList() {
        return mCurrent.clone();
    }

    // return the hisory day AppUsageStatsHistory
    AppUsageStatsHistory getHistoryAppUsageList(long currentTime) {
        if (mLowMemoryVersion) {
            if (isWeekend(currentTime)) {
                return mWeekEnd.clone();
            } else {
                return mWorkDays.clone();
            }
        } else {
            return getAppUsageStatsHistory(currentTime).clone();
        }
    }

    void writeToParcel(Parcel out) {

        out.writeString(mPackageName);
        out.writeLong(mBeginTimeStamp);
        out.writeLong(mEndTimeStamp);
        out.writeLong(mLastTimeUsed);
        out.writeLong(mTotalTimeInForeground);
        out.writeInt(mLaunchCount);
        out.writeInt(mLastEvent);

        if (mLowMemoryVersion) {
            mWorkDays.writeToParcel(out);
            mWeekEnd.writeToParcel(out);
        } else {
            for (int i = 0; i < DAYS_PER_WEEK; i++) {
                mHistorys[i].writeToParcel(out);
            }
        }

        // for test
        if (mUseAppUsageStatsSaver && mAppUsageStatsSaver != null)
            mAppUsageStatsSaver.writeToParcel(out);

    }

    void readFromParcel(Parcel in) throws ParcelFormatException {
        mPackageName = in.readString();
        mBeginTimeStamp = in.readLong();
        mEndTimeStamp = in.readLong();
        mLastTimeUsed = in.readLong();
        mTotalTimeInForeground = in.readLong();
        mLaunchCount = in.readInt();
        mLastEvent = in.readInt();


        if (mLowMemoryVersion) {
            mWorkDays.readFromParcel(in);
            mWeekEnd.readFromParcel(in);
        } else {
            for (int i = 0; i < DAYS_PER_WEEK; i++) {
                mHistorys[i].readFromParcel(in);
            }
        }

        // for test
        if (mUseAppUsageStatsSaver && mAppUsageStatsSaver != null)
            mAppUsageStatsSaver.readFromParcel(in);

    }


    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(TAG);
        result.append("{\nmPackageName: " + mPackageName
            + "\nLastEvent: " + mLastEvent
            + "\nLastTimeUsed: " + mLastTimeUsed
            + "\nLaunchCount: "  + mLaunchCount
            + "\nTotalTimeInForeground: " + mTotalTimeInForeground);

        if (mLowMemoryVersion) {
            result.append("\nWorkDays: " + mWorkDays
                + "\nWeekEnd: " + mWeekEnd);
        } else {
            for (int i = 0; i < DAYS_PER_WEEK; i++) {
                result.append("\nmHistorys[" + i + "]" + mHistorys[i]);
            }
        }
        result.append("\n}");
        return result.toString();
    }

    private boolean isStatefulEvent(int eventType) {
        switch (eventType) {
            case UsageEvents.Event.MOVE_TO_FOREGROUND:
            case UsageEvents.Event.MOVE_TO_BACKGROUND:
            case UsageEvents.Event.END_OF_DAY:
            case UsageEvents.Event.CONTINUE_PREVIOUS_DAY:
            case AppUsageStatsCollection.EVENT_TYPE_FORE_UPDATE:
                return true;
        }
        return false;
    }

    private boolean isUserEvent(int eventType) {
        switch (eventType) {
            case UsageEvents.Event.MOVE_TO_FOREGROUND:
            case UsageEvents.Event.MOVE_TO_BACKGROUND:
                return true;
        }
        return false;
    }

    // realTime: time using wall time
    private boolean isWeekend(long realTime) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(realTime);
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int weekDay = calendar.get(Calendar.DAY_OF_WEEK);

        if (DEBUG_MORE) Slog.d(TAG, "isWeekend() Current weekDay:" + weekDay + ", hour:" + hour);

        return (weekDay == 1 || weekDay == 7);
    }

    private AppUsageStatsHistory getAppUsageStatsHistory(long realTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(realTime);
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int weekDay = calendar.get(Calendar.DAY_OF_WEEK);

        if (DEBUG_MORE) Slog.d(TAG, "getAppUsageStatsHistory() Current weekDay:" + weekDay + ", hour:" + hour);
        if (weekDay > 0)
            return mHistorys[weekDay-1];

        return mHistorys[0];
    }

    private boolean timeEverSet(long startTime, long endTime) {
        if (endTime < startTime || mLastTimeStampUsed == 0) return true;

        if (mLastTimeStampUsed > 0) {
            long elapsedDuration = SystemClock.elapsedRealtime() - mLastTimeStampUsed;
            long realDuration = endTime - startTime;

            long diff = (realDuration >= elapsedDuration) ? (realDuration - elapsedDuration) : (elapsedDuration - realDuration);
            return (diff >= (5*ONE_MIN));
        } else {
            return (endTime - startTime >= (6*ONE_HOUR));
        }
    }

    void dumpInfo(PrintWriter pw) {

        pw.println("AppUsageStats {");
        pw.print("mPackageName:");
        pw.print(mPackageName);
        pw.println();
        pw.print("mBeginTimeStamp:");
        pw.print(mBeginTimeStamp);
        pw.println();
        pw.print("mEndTimeStamp:");
        pw.print(mEndTimeStamp);
        pw.println();
        pw.print("mLastTimeUsed:");
        pw.print(mLastTimeUsed);
        pw.println();
        pw.print("mTotalTimeInForeground:");
        pw.print(mTotalTimeInForeground);
        pw.println();
        pw.print("mLaunchCount:");
        pw.print(mLaunchCount);
        pw.println();
        pw.print("mLastEvent:");
        pw.print(mLastEvent);
        pw.println();

        pw.println("mWorkDays {");
        mWorkDays.dumpInfo(pw);
        pw.println("}");

        pw.println("mWeekEnd {");
        mWeekEnd.dumpInfo(pw);
        pw.println("}");


        if (mUseAppUsageStatsSaver && mAppUsageStatsSaver != null)
            mAppUsageStatsSaver.dumpInfo(pw);

        pw.println("}");
    }

}
