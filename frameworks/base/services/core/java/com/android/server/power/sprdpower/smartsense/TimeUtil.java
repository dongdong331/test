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
import android.os.sprdpower.Util;


public class TimeUtil {

    static final String TAG = "SSense.TimeUtil";

    private static final boolean DEBUG = true;

    private static final long ONE_HOUR = (60 * 60 * 1000);
    private static final long ONE_MINUTE = (60 * 1000);
    private static final int HOURS_PER_DAY = (24);
    private static final int COUNT_PER_HOUR = (6);


    private static final long TIME_SLOT_DURATION = (15*ONE_MINUTE);
    private static final int TIME_SLOT_COUNT = (int)((HOURS_PER_DAY*ONE_HOUR)/TIME_SLOT_DURATION);
    private static final int TIMESLOT_COUNT_PER_HOUR = (int) (ONE_HOUR/TIME_SLOT_DURATION);


    // return the HH in milliseconds for currentTimeMillis
    static long getHourMillis(long currentTimeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTimeMillis);
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        if (DEBUG) Slog.d(TAG, "getHourMillis() Current hour:" + hour + ", minute:" + minute);
        return (hour * ONE_HOUR);
    }

    // return the HH-MM in milliseconds for currentTimeMillis
    static long getMinuteMillis(long currentTimeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTimeMillis);
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        if (DEBUG) Slog.d(TAG, "getMinuteMillis() Current hour:" + hour + ", minute:" + minute);
        return (hour * ONE_HOUR + minute * ONE_MINUTE);
    }

    // return the HH-MM-SS milliseconds for currentTimeMillis
    static long getMillis(long currentTimeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTimeMillis);
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int seconds = calendar.get(Calendar.SECOND);

        if (DEBUG) Slog.d(TAG, "getMillis() Current hour:" + hour + ", minute:" + minute + ", seconds:" + seconds);
        return (hour * ONE_HOUR + minute * ONE_MINUTE + seconds*1000);
    }

    // return the timeSlot for currentTimeMillis
    static int getTimeSlot(long currentTimeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTimeMillis);
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int seconds = calendar.get(Calendar.SECOND);

        if (DEBUG) Slog.d(TAG, "getMillis() Current hour:" + hour + ", minute:" + minute + ", seconds:" + seconds);
        return (int)((hour * ONE_HOUR + minute * ONE_MINUTE + seconds*1000)/TIME_SLOT_DURATION);
    }

    static long getTimeSlotDuration() {
        return TIME_SLOT_DURATION;
    }

    static int getTimeSlotCount() {
        return TIME_SLOT_COUNT;
    }

    static int getTimeSlotCountPerHour() {
        return TIMESLOT_COUNT_PER_HOUR;
    }

    // realTime: time using wall time
    static boolean isWeekend(long realTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(realTime);
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int weekDay = calendar.get(Calendar.DAY_OF_WEEK);

        if (DEBUG) Slog.d(TAG, "isWeekend() Current weekDay:" + weekDay + ", hour:" + hour);

        return (weekDay == 1 || weekDay == 7);
    }

    static long getNextDailyDeadline() {
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
        long nextDailyDeadline = calDeadline.getTimeInMillis();
        return nextDailyDeadline;
    }

}
