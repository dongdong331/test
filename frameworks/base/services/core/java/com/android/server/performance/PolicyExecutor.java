package com.android.server.performance;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Bundle;
import android.content.ComponentName;
import android.util.Log;

import static android.app.ProcessInfo.*;
import static com.android.server.performance.PerformanceManagerService.*;

import org.xmlpull.v1.XmlPullParser;

import com.android.server.performance.status.CpuStatus;
import com.android.server.performance.status.SystemStatus;

import java.io.PrintWriter;

/**
 * Created by SPREADTRUM\joe.yu on 7/31/17.
 */

public class PolicyExecutor {
    private static final String LOG_TAG = "PolicyExecutor";
    private final String KEY_PKGNAME = "pkgname";
    private final String KEY_CLSNAME = "clsname";
    protected HandlerThread mThread;
    protected Handler mHandler;
    private static final int MSG_SYSTEMSTATUS_CHANGED = 0;
    private static final int MSG_ACTIVITY_STATE_CHANGED = 1;
    private static final int MSG_CPUSTATUS_CHANGED = 2;
    private static final int MSG_INSTRUMENTATION_CHANGE = 3;
    private static final int MSG_VMPRESSURE_EVENT = 4;
    boolean mInProgress = false;

    public PolicyExecutor() {
        mThread = new HandlerThread(getThreadName());
        mThread.start();
        mHandler = new Handler(mThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                case MSG_SYSTEMSTATUS_CHANGED:
                    SystemStatus status = (SystemStatus) msg.obj;
                    mInProgress = true;
                    executorPolicy(status);
                    mInProgress = false;
                    break;
                case MSG_ACTIVITY_STATE_CHANGED:
                    handleActivityStateChange((ActivityStateData) msg.obj);
                    break;
                case MSG_CPUSTATUS_CHANGED:
                    CpuStatus cpu = (CpuStatus) msg.obj;
                    handleCpuStatusChange(cpu);
                    break;
                case MSG_INSTRUMENTATION_CHANGE:
                    handleInStrumentationChange(msg);
                    break;
                case MSG_VMPRESSURE_EVENT:
                    handleVmpressureEvent((VmpressureData)msg.obj);
                    break;
                default:
                    break;
                }

            }
        };
    }

    public void executorPolicy(final SystemStatus status) {

    }

    public void onSystemStatusChanged(SystemStatus status) {
        if (!mInProgress) {
            Message msg = new Message();
            msg.what = MSG_SYSTEMSTATUS_CHANGED;
            msg.obj = status;
            mHandler.sendMessage(msg);
        }
    }

    public void onActivityStateChanged(ActivityStateData aData) {
        Message msg = new Message();
        msg.what = MSG_ACTIVITY_STATE_CHANGED;
        msg.obj = aData;
        mHandler.sendMessage(msg);
    }

    protected void handleActivityStateChange(ActivityStateData aData) {
    }

    public void onCpusStatusChanged(CpuStatus status) {
        Message msg = new Message();
        msg.what = MSG_CPUSTATUS_CHANGED;
        msg.obj = status;
        mHandler.sendMessage(msg);
    }

    protected void handleCpuStatusChange(CpuStatus status) {
    }

    public void onInStrumentationChange(InStrumentationData data) {
        Message m = new Message();
        m.what = MSG_INSTRUMENTATION_CHANGE;
        m.obj = data;
        mHandler.sendMessage(m);
    }

    protected void handleInStrumentationChange(Message msg) {
    }

    public void onVmpressureChanged(VmpressureData data) {
        Message m = new Message();
        m.what = MSG_VMPRESSURE_EVENT;
        m.obj = data;
        mHandler.sendMessage(m);
    }

    protected void handleVmpressureEvent(VmpressureData data) {
    }

    public String getThreadName() {
        return LOG_TAG;
    }

    public void dump(PrintWriter pw, String[] args) {
    }

    public static PolicyExecutor createFromConfig(PolicyItem in,
            PerformanceManagerService service) {
        return null;
    }
}
