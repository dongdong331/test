/*
 ** Copyright 2018 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import static android.text.format.DateUtils.SECOND_IN_MILLIS;

import static com.android.internal.util.ArrayUtils.total;

import android.app.usage.UsageEvents;
import android.app.usage.UsageEvents.Event;

import android.os.Parcel;
import android.os.ParcelFormatException;
import android.os.Parcelable;
import android.util.MathUtils;
import android.util.Slog;

import java.util.Arrays;
import java.util.Calendar;

///////////////////////////////////
import android.util.AtomicFile;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import com.android.internal.util.FastPrintWriter;
import android.os.sprdpower.Util;

public class AppUsageStatsHistory {

    static final String TAG = "SSense.AUStatsHistory";

    private final boolean DEBUG_LOG = Util.getDebugLog();
    private final boolean DEBUG = Util.isDebug() || DEBUG_LOG;
    private final boolean DEBUG_MORE = false;

    private static final long ONE_HOUR = (60 * 60 * 1000);
    private static final long ONE_MINUTE = (60 * 1000);
    private static final int HOURS_PER_DAY = (24);
    private static final int COUNT_PER_HOUR = (6);

    private static final int TIMESLOT_COUNT = TimeUtil.getTimeSlotCount();
    private static final long BUCKET_DURATION = TimeUtil.getTimeSlotDuration();
    private static final int TIMESLOT_COUNT_PER_HOUR = (int) (ONE_HOUR/BUCKET_DURATION);

     //5mins per hour
    private static final long FAVORITE_THRESHOLD = (5 * 60 * 1000);

    private static final long USED_THRESHOLD = (30 * 1000); // 30 seconds

    public String mPackageName;
    /**
     * Last time used by the user with an explicit action (notification, activity launch).
     * {@hide}
     */
    public long mLastTimeUpdated;
    public long[] mTimeInForegroundLastHour;
    public long mTotalTimeInForegroundLastHour;

    public long mNextDailyDeadline;
    public long mBucketDuration;
    public int mDayCount;
    public int mBucketCount;
    public long[] mBucketStart;
    public long[] mLaunchCount;
    public long[] mTimeInForeground;


    public AppUsageStatsHistory() {
        this(BUCKET_DURATION, TIMESLOT_COUNT);
    }

    public AppUsageStatsHistory(long bucketDuration, int initialSize) {
        this.mBucketDuration = bucketDuration;
        mBucketStart = new long[initialSize];
        mLaunchCount = new long[initialSize];
        mTimeInForeground = new long[initialSize];
        mBucketCount = 0;
        mDayCount = 0;
        mNextDailyDeadline = 0;
        ensureBuckets(0, initialSize*bucketDuration);
        mTimeInForegroundLastHour = new long[COUNT_PER_HOUR];
        mTotalTimeInForegroundLastHour = 0;
    }

    @Override
    public AppUsageStatsHistory clone() {
        final AppUsageStatsHistory clone = new AppUsageStatsHistory();
        clone.mLastTimeUpdated = mLastTimeUpdated;
        clone.mPackageName = mPackageName;
        clone.mTotalTimeInForegroundLastHour = mTotalTimeInForegroundLastHour;
        clone.mNextDailyDeadline = mNextDailyDeadline;
        clone.mDayCount = mDayCount;
        clone.mBucketCount = mBucketCount;
        for (int i = 0; i < mBucketCount; i++) {
            clone.mBucketStart[i] = mBucketStart[i];
            clone.mLaunchCount[i] = mLaunchCount[i];
            clone.mTimeInForeground[i] = mTimeInForeground[i];
        }

        for (int i = 0; i < COUNT_PER_HOUR; i++) {
            clone.mTimeInForegroundLastHour[i] = mTimeInForegroundLastHour[i];
        }
        return clone;
    }

    public int size() {
        return mBucketCount;
    }

    public long getBucketDuration() {
        return mBucketDuration;
    }

    public long getStart() {
        if (mBucketCount > 0) {
            return mBucketStart[0];
        } else {
            return Long.MAX_VALUE;
        }
    }

    public long getEnd() {
        if (mBucketCount > 0) {
            return mBucketStart[mBucketCount - 1] + mBucketDuration;
        } else {
            return Long.MIN_VALUE;
        }
    }

    public void reset() {
        mTotalTimeInForegroundLastHour = 0;
        mNextDailyDeadline = 0;
        mDayCount = 0;
        for (int i = 0; i < mBucketCount; i++) {
            mLaunchCount[i] = 0;
            mTimeInForeground[i] = 0;
        }

        for (int i = 0; i < COUNT_PER_HOUR; i++) {
            mTimeInForegroundLastHour[i] = 0;
        }
    }

    public boolean needUpdateDailyCount(long timeMillis) {
       return (timeMillis > mNextDailyDeadline);
    }

    /**
     * Return index of bucket that contains or is immediately before the
     * requested time.
     */
    public int getIndexBefore(long time) {
        int index = Arrays.binarySearch(mBucketStart, 0, mBucketCount, time);
        if (index < 0) {
            index = (~index) - 1;
        } else {
            index -= 1;
        }
        return MathUtils.constrain(index, 0, mBucketCount - 1);
    }

    /**
     * Return index of bucket that contains or is immediately after the
     * requested time.
     */
    public int getIndexAfter(long time) {
        int index = Arrays.binarySearch(mBucketStart, 0, mBucketCount, time);
        if (index < 0) {
            index = ~index;
        } else {
            index += 1;
        }
        return MathUtils.constrain(index, 0, mBucketCount - 1);
    }

    /**
     * startTimeMillis: wall time
     * endTimeMillis: wall time
     */
    public void reportUsage(String packageName, long startTimeMillis, long endTimeMillis,
            int lastEventType, int eventType) {

        long totalTimeInForeground = 0;
        long totalTimeInForeground2 = 0;
        long start = getMillis(startTimeMillis);
        long end = getMillis(endTimeMillis);

        if (DEBUG_MORE) Slog.d(TAG, "reportUsage:"+ " packageName:" + packageName
            + " start:" + start/BUCKET_DURATION + " end:" + end/BUCKET_DURATION
            + " eventType:" + eventType + "  lastEventType:" + lastEventType
            + " startTimeMillis:" + startTimeMillis + " endTimeMillis:" + endTimeMillis);

        mPackageName = packageName;

        updateDailyCount(endTimeMillis);

        // create any buckets needed by this range
        ensureBuckets(start, end);

        if (eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                eventType == UsageEvents.Event.END_OF_DAY ||
                eventType == AppUsageStatsCollection.EVENT_TYPE_FORE_UPDATE) {
            if (lastEventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    lastEventType == UsageEvents.Event.CONTINUE_PREVIOUS_DAY) {
                totalTimeInForeground += (endTimeMillis - startTimeMillis);
            }
        }

        totalTimeInForeground2 = totalTimeInForeground;

        // distribute data usage into buckets
        final int startIndex = getIndexBefore(start);
        final int endIndex = getIndexAfter(end);

        if (DEBUG_MORE) Slog.d(TAG, "reportUsage:"+ " startIndex:" + startIndex + " endIndex:" + endIndex
            + " totalTimeInForeground:" + totalTimeInForeground);

        if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            addLong(this.mLaunchCount, endIndex-1, 1);
        }

        if (totalTimeInForeground > 0) {
            start = getMillis(startTimeMillis);
            end = getMillis(endTimeMillis);

            if (DEBUG_MORE) Slog.d(TAG, "reportUsage:"+ " start:" + start + " end:" + end);

            if (startIndex <= endIndex) {
                for (int i = startIndex; i <= endIndex; i++) {
                    final long curStart = mBucketStart[i];
                    final long curEnd = curStart + mBucketDuration;

                    // bucket is older than record; we're finished
                    //if (curEnd < start) break;
                    // bucket is newer than record; keep looking
                    //if (curStart > end) continue;

                    final long overlap = Math.min(curEnd, end) - Math.max(curStart, start);
                    if (DEBUG_MORE) Slog.d(TAG, "reportUsage:"+ " i:" + i + " curStart:" + curStart + " curEnd:" + curEnd + " overlap:" + overlap);

                    if (overlap <= 0) continue;

                    addLong(this.mTimeInForeground, i, overlap); totalTimeInForeground -= overlap;

                    if (DEBUG_MORE) Slog.d(TAG, "reportUsage:"+ packageName + " i:" + i + " overlap:" + overlap + " remain:" + totalTimeInForeground
                        + " total:" + getLong(this.mTimeInForeground, i, 0));
                }
            } else {

                for (int i = startIndex; i < mBucketCount; i++) {
                    final long curStart = mBucketStart[i];
                    final long curEnd = curStart + mBucketDuration;

                    // bucket is older than record; we're finished
                    //if (curEnd < start) break;

                    final long overlap = curEnd - Math.max(curStart, start);
                    if (DEBUG_MORE) Slog.d(TAG, "reportUsage:"+ " i:" + i + " curStart:" + curStart + " curEnd:" + curEnd + " overlap:" + overlap);
                    if (overlap <= 0) continue;

                    addLong(this.mTimeInForeground, i, overlap); totalTimeInForeground -= overlap;

                    if (DEBUG_MORE) Slog.d(TAG, "reportUsage:"+ " i:" + i + " overlap:" + overlap + " remain:" + totalTimeInForeground);
                }


                for (int i = 0; i <= endIndex; i++) {
                    final long curStart = mBucketStart[i];
                    final long curEnd = curStart + mBucketDuration;


                    //if (curStart > end) break;

                    final long overlap = Math.min(curEnd, end) - curStart;
                    if (DEBUG_MORE) Slog.d(TAG, "reportUsage:"+ " i:" + i + " curStart:" + curStart + " curEnd:" + curEnd + " overlap:" + overlap);
                    if (overlap <= 0) continue;

                    addLong(this.mTimeInForeground, i, overlap); totalTimeInForeground -= overlap;

                    if (DEBUG_MORE) Slog.d(TAG, "reportUsage:"+ " i:" + i + " overlap:" + overlap + " remain:" + totalTimeInForeground);
                }
            }
        }


        // update mTimeInForegroundLastHour && mTotalTimeInForegroundLastHour
        if (totalTimeInForeground2 > 0) {

            int elapsedCount = (int)((endTimeMillis -mLastTimeUpdated)/(ONE_HOUR/COUNT_PER_HOUR));
            if (DEBUG_MORE) Slog.d(TAG, "reportUsage: 2"+ " endTimeMillis:" + endTimeMillis + " mLastTimeUpdated:" + mLastTimeUpdated
                + " elapsedCount:" + elapsedCount
                + " ONE_HOUR/COUNT_PER_HOUR:" + ONE_HOUR/COUNT_PER_HOUR);

            if (elapsedCount > 0) {
                for (int i = 0; i <COUNT_PER_HOUR; i++) {
                    int targetIndex = i + elapsedCount;
                    if (targetIndex < COUNT_PER_HOUR)
                        mTimeInForegroundLastHour[i] = mTimeInForegroundLastHour[targetIndex];
                    else
                        mTimeInForegroundLastHour[i] = 0;
                }
            }


            start = getMillis(startTimeMillis);
            end = getMillis(endTimeMillis);
            mTotalTimeInForegroundLastHour = 0;

            if (DEBUG_MORE) Slog.d(TAG, "reportUsage: 2"+ " start:" + start + " end:" + end);

            for (int i = 0; i <COUNT_PER_HOUR; i++) {
                final long curStart = end - ONE_HOUR + (ONE_HOUR/COUNT_PER_HOUR)*i;
                final long curEnd = curStart + ONE_HOUR/COUNT_PER_HOUR;

                final long overlap = Math.min(curEnd, end) - Math.max(curStart, start);
                if (DEBUG_MORE) Slog.d(TAG, "reportUsage 2:"+ " i:" + i + " curStart:" + curStart + " curEnd:" + curEnd + " overlap:" + overlap);

                if (overlap <= 0) {
                    mTotalTimeInForegroundLastHour += mTimeInForegroundLastHour[i];
                    continue;
                }

                addLong(this.mTimeInForegroundLastHour, i, overlap); totalTimeInForeground2 -= overlap;
                mTotalTimeInForegroundLastHour += mTimeInForegroundLastHour[i];

                if (DEBUG_MORE) Slog.d(TAG, "reportUsage 2:"+ " i:" + i + " overlap:" + overlap + " remain:" + totalTimeInForeground2);
            }

            mLastTimeUpdated = endTimeMillis;
        }

        if (DEBUG_MORE) {
            for (int i = 0; i <COUNT_PER_HOUR; i++) {
                Slog.d(TAG, "reportUsage mTimeInForegroundLastHour:"+ " i:" + i + " " + mTimeInForegroundLastHour[i]);
            }
            Slog.d(TAG, "reportUsage mTotalTimeInForegroundLastHour:"+ mTotalTimeInForegroundLastHour);
        }

    }


    // return the time interval between the next favorite hour
    // return > 0 in hour is valid, and -1 for invalid
    // currentTime: current time using wall time
    public int getNextFavoriteTimeInterval(long currentTime) {

        if (mDayCount < 1) return -1;

        // get the current millseconds ignore the day-month-year
        long current = getMillis(currentTime);
        int startIndex = getIndexAfter(current)-1;

        int currentHour = (int)((current + ONE_HOUR/4)/ONE_HOUR);

        if (DEBUG_MORE) Slog.d(TAG, "getNextFavoriteTimeInterval:"+ " packageName:" + mPackageName
            + " currentHour:" + currentHour + " current:" + current);
        // if startIndex is past
        if ((mBucketStart[startIndex] + mBucketDuration) < current)
            startIndex++;

        for (int i = startIndex; i < mBucketCount; i++) {
            final long curStart = mBucketStart[i];
            final long curEnd = curStart + mBucketDuration;

            if ((curStart > current)
                || (current > curStart && current < curEnd)){
                long avgForegroundTime = getAvgFavoriteTime(i, current);
                if (avgForegroundTime >= FAVORITE_THRESHOLD) {
                    if (DEBUG_MORE) Slog.d(TAG, "getNextFavoriteTimeInterval AF:"+ " i:" + i
                        + " curStart:" + curStart + " curEnd:" + curEnd
                        + " avgForegroundTime:" + avgForegroundTime);

                    return ((i/TIMESLOT_COUNT_PER_HOUR) - currentHour);
                }
            }
        }

        for (int i = 0; i < startIndex; i++) {
            final long curStart = mBucketStart[i];
            final long curEnd = curStart + mBucketDuration;

            if (curEnd < current) {
                long avgForegroundTime = getAvgFavoriteTime(i, current);
                if (avgForegroundTime >= FAVORITE_THRESHOLD) {
                    if (DEBUG_MORE) Slog.d(TAG, "getNextFavoriteTimeInterval BE:"+ " i:" + i
                        + " curStart:" + curStart + " curEnd:" + curEnd
                        + " avgForegroundTime:" + avgForegroundTime);

                    return (HOURS_PER_DAY + (i/TIMESLOT_COUNT_PER_HOUR) - currentHour);
                }
            }
        }

        if (mDayCount >= 2) return HOURS_PER_DAY;
        return -1;
    }


    // return the time interval between the next used hour
    // return > 0 in hour is valid, and -1 for invalid
    // currentTime: current time using wall time
    public int getNextUsedTimeInterval(long currentTime) {

        if (mDayCount < 1) return -1;

        // get the current millseconds ignore the day-month-year
        long current = getMillis(currentTime);
        int startIndex = getIndexAfter(current)-1;

        int currentHour = (int)((current)/ONE_HOUR);

        if (DEBUG_MORE) Slog.d(TAG, "getNextUsedTimeInterval:"+ " packageName:" + mPackageName
            + " currentHour:" + currentHour + " current:" + current);
        // if startIndex is past
        if ((mBucketStart[startIndex] + mBucketDuration) < current)
            startIndex++;

        for (int i = startIndex; i < mBucketCount; i++) {
            final long curStart = mBucketStart[i];
            final long curEnd = curStart + mBucketDuration;

            if ((curStart > current)
                || (current > curStart && current < curEnd)){
                long avgForegroundTime = getAvgTime(i, current);
                if (avgForegroundTime >= USED_THRESHOLD) {
                    if (DEBUG_MORE) Slog.d(TAG, "getNextUsedTimeInterval AF:"+ " i:" + i
                        + " curStart:" + curStart + " curEnd:" + curEnd
                        + " avgForegroundTime:" + avgForegroundTime + " total:" + getLong(this.mTimeInForeground, i, 0));

                    return ((i/TIMESLOT_COUNT_PER_HOUR) - currentHour);
                }
            }
        }

        for (int i = 0; i < startIndex; i++) {
            final long curStart = mBucketStart[i];
            final long curEnd = curStart + mBucketDuration;

            if (curEnd < current) {
                long avgForegroundTime = getAvgTime(i, current);
                if (avgForegroundTime >= USED_THRESHOLD) {
                    if (DEBUG_MORE) Slog.d(TAG, "getNextUsedTimeInterval BE:"+ " i:" + i
                        + " curStart:" + curStart + " curEnd:" + curEnd
                        + " avgForegroundTime:" + avgForegroundTime + " total:" + getLong(this.mTimeInForeground, i, 0));

                    return (HOURS_PER_DAY + (i/TIMESLOT_COUNT_PER_HOUR) - currentHour);
                }
            }
        }

        if (mDayCount >= 2) return HOURS_PER_DAY;
        return -1;
    }


    public void writeToParcel(Parcel out) {

        out.writeString(mPackageName);
        out.writeLong(mNextDailyDeadline);
        out.writeLong(mBucketDuration);
        out.writeInt(mDayCount);
        out.writeInt(mBucketCount);
        final int N = mBucketStart.length;
        out.writeInt(N);
        for (int i=0; i<N; i++) {
            out.writeLong(mBucketStart[i]);
            out.writeLong(mLaunchCount[i]);
            out.writeLong(mTimeInForeground[i]);
        }
    }

    public void readFromParcel(Parcel in) throws ParcelFormatException {
        mPackageName = in.readString();
        mNextDailyDeadline = in.readLong();
        mBucketDuration = in.readLong();
        mDayCount = in.readInt();
        mBucketCount = in.readInt();

        final int N = in.readInt();
        if (N > mBucketStart.length) {
            mBucketStart = Arrays.copyOf(mBucketStart, N );
            if (mLaunchCount != null) mLaunchCount = Arrays.copyOf(mLaunchCount, N );
            if (mTimeInForeground != null) mTimeInForeground = Arrays.copyOf(mTimeInForeground, N );
        }

        for (int iu = 0; iu < N; iu++) {
            setLong(mBucketStart, iu, in.readLong());
            setLong(mLaunchCount, iu, in.readLong());
            setLong(mTimeInForeground, iu, in.readLong());
        }
    }


    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(TAG);
            result.append("{\nmPackageName: " + mPackageName
                + "\nmNextDailyDeadline: " + mNextDailyDeadline
                + "\nbucketDuration: " + mBucketDuration
                + "\ndayCount: "  + mDayCount
                + "\nbucketCount: " + mBucketCount);

        for(int i=0; i<mBucketCount; i++) {
            result.append("\nbucketStart[" + i + "]:" + mBucketStart[i]
                + ", launchCount[" + i + "]:" + mLaunchCount[i]
                + ", timeInForeground[" + i + "]:" + mTimeInForeground[i]);
        }
        result.append("\n}" );
        return result.toString();
    }


    private boolean isFavoriteTime(int index, long currentTime) {
        if (mDayCount <= 0) return false;

        final long foregroundTime = getLong(this.mTimeInForeground, index, 0);
        final long curStart = getLong(this.mBucketStart, index, 0);
        long avgForegroundTime = 0;

        if (curStart > currentTime) {
            if (mDayCount <=1) avgForegroundTime = foregroundTime;
            else avgForegroundTime = foregroundTime/(mDayCount -1);
        } else {
            avgForegroundTime = foregroundTime/mDayCount;
        }

        if (avgForegroundTime >= FAVORITE_THRESHOLD)
            return true;
        else
            return false;
    }

    private long getAvgFavoriteTime(int index, long currentTime) {
        if (mDayCount <= 0) return 0;

        final long foregroundTime = getLong(this.mTimeInForeground, index, 0);
        final long curStart = getLong(this.mBucketStart, index, 0);
        long avgForegroundTime = 0;

        if (curStart > currentTime) {
            if (mDayCount <=1) avgForegroundTime = foregroundTime;
            else avgForegroundTime = foregroundTime/(mDayCount -1);
        } else {
            long avg = 0;
            if (currentTime < (mLastTimeUpdated + ONE_HOUR/4)) {
                avg = mTotalTimeInForegroundLastHour/2;
            }

            if (avg > foregroundTime/mDayCount) {
                avgForegroundTime = avg;
            } else {
                avgForegroundTime = foregroundTime/mDayCount;
            }
        }

        return avgForegroundTime;
    }

    private long getAvgTime(int index, long currentTime) {
        if (mDayCount <= 0) return 0;

        final long foregroundTime = getLong(this.mTimeInForeground, index, 0);
        final long curStart = getLong(this.mBucketStart, index, 0);
        long avgForegroundTime = 0;

        if (curStart > currentTime) {
            if (mDayCount <=1) avgForegroundTime = foregroundTime;
            else avgForegroundTime = foregroundTime/(mDayCount -1);
        } else {
            avgForegroundTime = foregroundTime/mDayCount;
        }

        return avgForegroundTime;
    }

    /**
     * Ensure that buckets exist for given time range, creating as needed.
     */
    private void ensureBuckets(long start, long end) {
        // normalize incoming range to bucket boundaries
        start -= start % mBucketDuration;
        end += (mBucketDuration - (end % mBucketDuration)) % mBucketDuration;

        for (long now = start; now < end; now += mBucketDuration) {
            // try finding existing bucket
            final int index = Arrays.binarySearch(mBucketStart, 0, mBucketCount, now);
            if (index < 0) {
                // bucket missing, create and insert
                insertBucket(~index, now);
            }
        }

        if (DEBUG_MORE) Slog.d(TAG, "ensureBuckets:"+ " bucketCount:" + mBucketCount + " start:" + start + " end:" + end);
    }

    /**
     * Insert new bucket at requested index and starting time.
     */
    private void insertBucket(int index, long start) {
        // create more buckets when needed
        if (mBucketCount >= mBucketStart.length) {
            final int newLength = Math.max(mBucketStart.length, 10) * 3 / 2;
            mBucketStart = Arrays.copyOf(mBucketStart, newLength);
            if (mLaunchCount != null) mLaunchCount = Arrays.copyOf(mLaunchCount, newLength);
            if (mTimeInForeground != null) mTimeInForeground = Arrays.copyOf(mTimeInForeground, newLength);
        }

        // create gap when inserting bucket in middle
        if (index < mBucketCount) {
            final int dstPos = index + 1;
            final int length = mBucketCount - index;

            System.arraycopy(mBucketStart, index, mBucketStart, dstPos, length);
            if (mLaunchCount != null) System.arraycopy(mLaunchCount, index, mLaunchCount, dstPos, length);
            if (mTimeInForeground != null) System.arraycopy(mTimeInForeground, index, mTimeInForeground, dstPos, length);
        }

        mBucketStart[index] = start;
        setLong(mLaunchCount, index, 0L);
        setLong(mTimeInForeground, index, 0L);
        mBucketCount++;
    }


    private static long getLong(long[] array, int i, long value) {
        return array != null ? array[i] : value;
    }

    private static void setLong(long[] array, int i, long value) {
        if (array != null) array[i] = value;
    }

    private static void addLong(long[] array, int i, long value) {
        if (array != null) array[i] += value;
    }

    // return the HH in milliseconds for currentTimeMillis
    private long getHourMillis(long currentTimeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTimeMillis);
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        if (DEBUG_MORE) Slog.d(TAG, "getHourMillis() Current hour:" + hour + ", minute:" + minute);
        return (hour * ONE_HOUR);
    }

    // return the HH-MM in milliseconds for currentTimeMillis
    private long getMinuteMillis(long currentTimeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTimeMillis);
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        if (DEBUG_MORE) Slog.d(TAG, "getMinuteMillis() Current hour:" + hour + ", minute:" + minute);
        return (hour * ONE_HOUR + minute * ONE_MINUTE);
    }

    // return the HH-MM-SS milliseconds for currentTimeMillis
    private long getMillis(long currentTimeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTimeMillis);
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int seconds = calendar.get(Calendar.SECOND);

        if (DEBUG_MORE) Slog.d(TAG, "getMillis() Current hour:" + hour + ", minute:" + minute + ", seconds:" + seconds);
        return (hour * ONE_HOUR + minute * ONE_MINUTE + seconds*1000);
    }

    private void updateDailyCount(long timeMillis) {

        if (timeMillis > mNextDailyDeadline) {
            // Get the current time.
            long currentTime = System.currentTimeMillis();
            Calendar calDeadline = Calendar.getInstance();
            calDeadline.setTimeInMillis(currentTime);

            // Move time up to the next day, ranging from 1am.
            calDeadline.set(Calendar.DAY_OF_YEAR, calDeadline.get(Calendar.DAY_OF_YEAR) + 1);
            calDeadline.set(Calendar.MILLISECOND, 0);
            calDeadline.set(Calendar.SECOND, 0);
            calDeadline.set(Calendar.MINUTE, 0);
            calDeadline.set(Calendar.HOUR_OF_DAY, 1);
            mNextDailyDeadline = calDeadline.getTimeInMillis();
            mDayCount++;

            if (DEBUG_MORE) Slog.d(TAG, "updateDailyCount: dayCount:" + mDayCount + ", mNextDailyDeadline:" + mNextDailyDeadline);
        }
    }


    void dumpInfo(PrintWriter pw) {

        pw.println("AppUsageStatsHistory {");
        pw.print("mPackageName:");
        pw.print(mPackageName);
        pw.println();
        pw.print("mNextDailyDeadline:");
        pw.print(mNextDailyDeadline);
        pw.println();
        pw.print("mBucketDuration:");
        pw.print(mBucketDuration);
        pw.println();
        pw.print("mDayCount:");
        pw.print(mDayCount);
        pw.println();
        pw.print("mBucketCount:");
        pw.print(mBucketCount);
        pw.println();

        pw.print("mBucketStart[");
        for (int i = 0; i < mBucketCount; i++) {
            pw.print(mBucketStart[i]);
            pw.print(",");
        }
        pw.println("]");

        pw.print("mLaunchCount[");
        for (int i = 0; i < mBucketCount; i++) {
            pw.print(mLaunchCount[i]);
            pw.print(",");
        }
        pw.println("]");

        pw.print("mTimeInForeground[");
        for (int i = 0; i < mBucketCount; i++) {
            pw.print(mTimeInForeground[i]);
            pw.print(",");
        }
        pw.println("]");

        pw.println("}");
    }


}
