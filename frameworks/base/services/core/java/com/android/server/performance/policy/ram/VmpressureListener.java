package com.android.server.performance.policy.ram;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Slog;
import com.android.server.performance.PerformanceManagerService;

import static com.android.server.performance.PerformanceManagerService.VmpressureData;
import static com.android.server.performance.PerformanceManagerDebugConfig.DEBUG_RAMPOLICY;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class VmpressureListener extends Thread {
    private final static String TAG = "performance";
    private static final String LMK_VMPRESSURE_SOCKET = "vmpressure";
    private PerformanceManagerService mService;
    private RamPolicyExecutor mExecutor;
    private boolean mConnected;

    public VmpressureListener(PerformanceManagerService service) {
        mService = service;
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
        int retryCount = 0;
        LocalSocket lmkillerSocket = null;
        mConnected = false;
        Slog.d(TAG, "VmpressureListeners start!");
        try {
            while(true) {
                LocalSocket s = null;
                LocalSocketAddress lSAddr;
                try {
                    s = new LocalSocket();
                    lSAddr = new LocalSocketAddress(LMK_VMPRESSURE_SOCKET,
                            LocalSocketAddress.Namespace.RESERVED);
                    s.connect(lSAddr);
                } catch (IOException e) {
                    mConnected = false;
                    try {
                        if (s != null) {
                            s.close();
                        }
                    } catch (Exception e2) {
                    }

                    if (retryCount == 8) {
                        Slog.e(TAG, "can't find vmpressure socket after "
                                + retryCount + " retry, abort VmpressureListener");
                        return;
                    } else if ( retryCount >= 0 && retryCount < 8) {
                        Slog.d(TAG, "retrying " + retryCount);
                    }

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException er) {
                    }
                    retryCount++;
                    continue;
                }
                retryCount = 0;
                lmkillerSocket = s;
                Slog.d(TAG, "connected to vmpressure");
                mConnected = true;
                try {
                    InputStream is = lmkillerSocket.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    while (true) {
                        String line = reader.readLine();
                        try {
                            handleVmpressure(line);
                        } catch (Exception e) {
                            Slog.e(TAG, "handleVmpressure encounter exception, read line "
                                    + line, e);
                        }
                    }
                } catch (Exception e) {
                    Slog.e(TAG, "caugth exception, closing lmk tracker", e);
                }

                try{
                    if (lmkillerSocket != null) lmkillerSocket.close();
                } catch (IOException e) {
                }
            }
        } catch (Exception e) {
        }
    }

    void handleVmpressure(String info) {
        if (info != null) {
            String[] temp = info.split(" ");
            if (temp != null && temp.length == 2) {
                VmpressureData data = new VmpressureData();
                data.swapUsage = Integer.valueOf(temp[0]);
                data.pressure = Integer.valueOf(temp[1]);
                if (DEBUG_RAMPOLICY) Slog.d(TAG, "handleVmpressure->swap:" + data.swapUsage + "pressure:" + data.pressure);
                mService.notifyExecutorsVmpressureChanged(data);
            }
        }
    }

}
