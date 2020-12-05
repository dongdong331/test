package com.android.server.performance.status;

import android.os.Process;
import static android.os.Process.*;
import android.os.UserHandle;
import android.util.Slog;
import android.content.pm.ApplicationInfo;
import com.android.internal.os.ProcessCpuTracker;
import com.android.internal.app.procstats.ProcessStats;
import com.android.server.performance.collector.CpuStatusCollector;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class CpuStatus {
    private final float[] mLoadAverageData = new float[3];
    private float mLoad1, mLoad5, mLoad15 = 0;
    private int mRelUserTime;
    private int mRelSystemTime;
    private int mRelIoWaitTime;
    private int mRelIrqTime;
    private int mRelSoftIrqTime;
    private int mRelIdleTime;
    private static final int[] LOAD_AVERAGE_FORMAT = new int[] {
            PROC_SPACE_TERM | PROC_OUT_FLOAT, // 0: 1 min
            PROC_SPACE_TERM | PROC_OUT_FLOAT, // 1: 5 mins
            PROC_SPACE_TERM | PROC_OUT_FLOAT // 2: 15 mins
    };
    private float mCurrentLoadPercent;

    private void updateCpuLoad() {
        final float[] loadAverages = mLoadAverageData;
        try {
            if (Process.readProcFile("/proc/loadavg", LOAD_AVERAGE_FORMAT,
                    null, null, loadAverages)) {
                float load1 = loadAverages[0];
                float load5 = loadAverages[1];
                float load15 = loadAverages[2];
                if (load1 != mLoad1 || load5 != mLoad5 || load15 != mLoad15) {
                    mLoad1 = load1;
                    mLoad5 = load5;
                    mLoad15 = load15;
                }
            }
        } catch (Exception e) {
        }
    }

    public CpuStatus() {
    }

    public void updateCpuInfo(CpuStatusCollector collector) {
        updateCpuLoad();
        if (collector != null && collector.hasGoodLastStats()) {
            mCurrentLoadPercent = collector.getTotalCpuPercent();
            mRelUserTime = collector.getLastUserTime();
            mRelSystemTime = collector.getLastSystemTime();
            mRelIoWaitTime = collector.getLastIoWaitTime();
            mRelIrqTime = collector.getLastIrqTime();
            mRelSoftIrqTime = collector.getLastSoftIrqTime();
            mRelIdleTime = collector.getLastIdleTime();
        }
    }

    public float getCpuLoadAvg1Min() {
        return mLoad1;
    }

    public float getCpuLoadAvg5Min() {
        return mLoad5;
    }

    public float getCpuLoadAvg15Min() {
        return mLoad15;
    }

    public float getTotalCpuPercent() {
        return mCurrentLoadPercent;
    }

    final public int getLastUserTime() {
        return mRelUserTime;
    }

    final public int getLastSystemTime() {
        return mRelSystemTime;
    }

    final public int getLastIoWaitTime() {
        return mRelIoWaitTime;
    }

    final public int getLastIrqTime() {
        return mRelIrqTime;
    }

    final public int getLastSoftIrqTime() {
        return mRelSoftIrqTime;
    }

    final public int getLastIdleTime() {
        return mRelIdleTime;
    }
}