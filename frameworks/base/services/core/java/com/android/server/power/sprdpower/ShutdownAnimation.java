package com.android.server.power.sprdpower;

import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.view.IWindowManager;
import android.util.Slog;
import java.io.File;

public class ShutdownAnimation {
    private static final String TAG = "ShutdownAnimation";
    private static final int BOOT_ANIMATION_CHECK_SPAN = 200;
    private static final int MAX_BOOTANIMATION_WAIT_TIME = 15*1000;
    private final Object mAnimationDoneSync = new Object();
    private boolean mPlayAnim = false;

    boolean hasShutdownAnimation() {
        File fileDefault = new File("/system/media/shutdownanimation.zip");
        File file = new File("/oem/media/shutdownanimation.zip");
        return file.exists() || fileDefault.exists();
    }

    boolean playShutdownAnimation() {
        IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
        try {
            wm.updateRotation(false, false);
        } catch (RemoteException e) {
            Slog.e(TAG, "stop orientation failed!", e);
        }

        //String[] bootcmd = {"bootanimation", "shutdown"} ;
        try {
            mPlayAnim = true;
            Slog.i(TAG, "exec the bootanimation ");
            SystemProperties.set("service.bootanim.exit", "0");
            SystemProperties.set("service.bootanim.end", "0");
            SystemProperties.set("service.bootanim.shutdown", "1");
            SystemProperties.set("ctl.start", "bootanim");
            //Runtime.getRuntime().exec(bootcmd);
        } catch (Exception e) {
            mPlayAnim = false;
            Slog.e(TAG,"bootanimation command exe err!");
        }
        return true;
    }

    void waitForBootanim() {
        // SPRD: SPCSS00331921 Add for extended shutdown time off BEG-->
        if (mPlayAnim && SystemProperties.get("service.wait_for_bootanim").equals("1")) {
            mPlayAnim = false;
            synchronized (mAnimationDoneSync) {
                final long endBootAnimationTime = SystemClock.elapsedRealtime() + MAX_BOOTANIMATION_WAIT_TIME;
                while (SystemProperties.get("service.bootanim.end").equals("0")) {
                    long delay = endBootAnimationTime - SystemClock.elapsedRealtime();
                    if (delay <= 0) {
                        Slog.w(TAG, "BootAnimation wait timed out");
                        break;
                    }
                    try {
                        Slog.d(TAG,"-----waiting boot animation completed-----");
                        mAnimationDoneSync.wait(BOOT_ANIMATION_CHECK_SPAN);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
