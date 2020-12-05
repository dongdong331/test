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
import android.util.MathUtils;
import android.util.Slog;

import java.util.Arrays;
import java.util.Calendar;


import android.util.AtomicFile;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import com.android.internal.util.FastPrintWriter;

import android.text.format.DateFormat;
import android.text.format.Time;

import android.os.sprdpower.Util;

public class AppUsageStatsSaver {

    static final String TAG = "SSense.AUStatsSaver";

    private final boolean DEBUG = Util.isDebug();


    /**
     * {@hide}
     */
    public String mPackageName;

    /**
     * {@hide}
     */
    public int mCount;


    private long[] mStarts;
    private long[] mEnds;

    public AppUsageStatsSaver() {
        mCount = 0;
        mStarts = new long[24];
        mEnds = new long[24];
    }

    // realTime: time using wall time
    void reportUsage(String packageName, long curTime, int curEvent, int lastEvent) {
        if (packageName == null) return;

        mPackageName = packageName;

        // create more buckets when needed
        if (mCount >= mStarts.length) {
            final int newLength = Math.max(mStarts.length, 10) * 3 / 2;
            mStarts = Arrays.copyOf(mStarts, newLength);
            if (mEnds != null) mEnds = Arrays.copyOf(mEnds, newLength);

            if (DEBUG) Slog.w(TAG, "extend mStarts length to:" + newLength
                + " mStarts.length:" + mStarts.length
                + " mCount:" + mCount);
        }

        if (curEvent == UsageEvents.Event.MOVE_TO_FOREGROUND
            && lastEvent != UsageEvents.Event.MOVE_TO_FOREGROUND) {

            if (DEBUG) Slog.w(TAG, "start using app:" + packageName
                + " at " + curTime);
           mStarts[mCount] = curTime;
        }

        if (curEvent == UsageEvents.Event.MOVE_TO_BACKGROUND
            && lastEvent == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            if (DEBUG) Slog.w(TAG, "end using app:" + packageName
                + " at " + curTime);
            mEnds[mCount++] = curTime;
        }

        //if (DEBUG) Slog.d(TAG, "reportUsage:"+ " mWorkDays:" + mWorkDays);
    }

    void dumpInfo(PrintWriter pw) {

        pw.println("AppUsageStatsSaver {");
        pw.print("mPackageName:");
        pw.print(mPackageName);
        pw.println();
        pw.print("mCount:");
        pw.print(mCount);
        pw.println();

        pw.print("mStarts[");
        for (int i = 0; i < mCount; i++) {
            pw.print(mStarts[i]);
            pw.print(",");
        }
        pw.println("]");

        pw.print("mEnds[");
        for (int i = 0; i < mCount; i++) {
            pw.print(mEnds[i]);
            pw.print(",");
        }
        pw.println("]");

        pw.print("mStarts[");
        for (int i = 0; i < mCount; i++) {
            pw.print(DateFormat.format("yyyy-MM-dd HH:mm:ss", mStarts[i]));
            pw.print(",");
        }
        pw.println("]");

        pw.print("mEnds[");
        for (int i = 0; i < mCount; i++) {
            pw.print(DateFormat.format("yyyy-MM-dd HH:mm:ss", mEnds[i]));
            pw.print(",");
        }
        pw.println("]");


        pw.println("}");
    }


    public void writeToParcel(Parcel out) {

        out.writeString(mPackageName);

        final int N = mStarts.length;
        out.writeInt(N);
        if (mCount > N)
            mCount = N;

        out.writeInt(mCount);
        for (int i=0; i<mCount; i++) {
            out.writeLong(mStarts[i]);
            out.writeLong(mEnds[i]);
        }
    }

    public void readFromParcel(Parcel in) throws ParcelFormatException {
        mPackageName = in.readString();

        final int N = in.readInt();
        if (N > mStarts.length) {
            mStarts = Arrays.copyOf(mStarts, N );
            if (mEnds != null) mEnds = Arrays.copyOf(mEnds, N );
        }

        mCount = in.readInt();


        for (int iu = 0; iu < mCount; iu++) {
            mStarts[iu] = in.readLong();
            mEnds[iu] = in.readLong();
        }
    }


}
