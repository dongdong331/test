package com.android.server.performance.collector;

import com.android.internal.os.ProcessCpuTracker;
import com.android.server.performance.PerformanceManagerService;
import com.android.server.performance.status.CpuStatus;

import android.os.FileUtils;
import android.os.Process;
import android.os.HandlerThread;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.os.SystemClock;
import android.system.OsConstants;
import android.util.Slog;

import java.util.concurrent.atomic.AtomicLong;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.IOException;

import static com.android.server.performance.PerformanceManagerDebugConfig.*;

public class CpuStatusCollector {
    private final int MSG_UPDATE_CPU_USAGE = 0;
    private final int MSG_INIT = 1;
    private final long MONITOR_CPU_AVG_TIME = 30 * 1000;
    public static final long MONITOR_CPU_MIN_TIME = 5 * 1000;
    private final float CPU_LOAD_MAY_HIGH = 50;
    private boolean MONITOR_ENABLED = true;
    final AtomicLong mLastCpuTime = new AtomicLong(0);
    private HandlerThread mThread;
    private Handler mHandler;
    private ProcessCpuTracker mProcessCpuTracker;
    private PerformanceManagerService mService;
    private boolean mLastStatusGood;
    private float mCurrentCpuLoading = 0;
    private int mRelUserTime;
    private int mRelSystemTime;
    private int mRelIoWaitTime;
    private int mRelIrqTime;
    private int mRelSoftIrqTime;
    private int mRelIdleTime;

    public CpuStatusCollector(PerformanceManagerService service) {
        mService = service;
        mProcessCpuTracker = new ProcessCpuTracker(false);
        mThread = new HandlerThread("performance-cpu",
                Process.THREAD_PRIORITY_BACKGROUND);
        mThread.start();
        mHandler = new Handler(mThread.getLooper()) {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case MSG_UPDATE_CPU_USAGE: {
                    handleUpdateCpuUsage();
                    break;
                }
                case MSG_INIT: {
                    mProcessCpuTracker.init();
                    scheduleUpdateCpuUsage(MONITOR_CPU_AVG_TIME);
                    break;
                }
                }
            }
        };
        mHandler.sendEmptyMessage(MSG_INIT);
    }

    private void handleUpdateCpuUsage() {
        final long now = SystemClock.uptimeMillis();
        if (MONITOR_ENABLED
                && ((now - mLastCpuTime.get()) >= MONITOR_CPU_MIN_TIME)) {
            synchronized (mProcessCpuTracker) {
                mLastCpuTime.set(now);
                mProcessCpuTracker.update();
                if (mProcessCpuTracker.hasGoodLastStats()) {
                    mLastStatusGood = true;
                    mCurrentCpuLoading = mProcessCpuTracker
                            .getTotalCpuPercent();
                    mRelUserTime = mProcessCpuTracker.getLastUserTime();
                    mRelSystemTime = mProcessCpuTracker.getLastSystemTime();
                    mRelIoWaitTime = mProcessCpuTracker.getLastIoWaitTime();
                    mRelIrqTime = mProcessCpuTracker.getLastIrqTime();
                    mRelSoftIrqTime = mProcessCpuTracker.getLastSoftIrqTime();
                    mRelIdleTime = mProcessCpuTracker.getLastIdleTime();
                    if (DEBUG_CPUSTAT) {
                        Slog.d(TAG, "current Loading:" + mCurrentCpuLoading
                                + " cost :"
                                + (SystemClock.uptimeMillis() - now) + "ms");
                    }
                    if (mCurrentCpuLoading >= CPU_LOAD_MAY_HIGH) {
                        scheduleUpdateCpuUsage(MONITOR_CPU_MIN_TIME);
                    } else {
                        scheduleUpdateCpuUsage(MONITOR_CPU_AVG_TIME);
                    }
                    CpuStatus status = new CpuStatus();
                    status.updateCpuInfo(this);
                    mService.notifyPolicyExecutorsCpuStatusChanged(status);
                }
            }
        }
    }

    public void updateCpuUsageNow() {
        scheduleUpdateCpuUsage(0);
    }

    public void scheduleUpdateCpuUsage(long delay) {
        if (!MONITOR_ENABLED) {
            return;
        }
        mHandler.removeMessages(MSG_UPDATE_CPU_USAGE);
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_CPU_USAGE, delay);
    }

    public float getTotalCpuPercent() {
        return mCurrentCpuLoading;
    }

    public boolean hasGoodLastStats() {
        return mLastStatusGood;
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

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CPUSTATUS:");
        pw.println("TOTAL :" + getTotalCpuPercent() + "% USED");
        pw.println("----------------------------");
    }
}