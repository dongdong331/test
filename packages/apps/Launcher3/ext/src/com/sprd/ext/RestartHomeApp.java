package com.sprd.ext;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

/**
 * Created on 9/22/18.
 */
public class RestartHomeApp implements Runnable {
    private static final String TAG = "RestartHomeApp";

    // Time to wait before killing the process this ensures that the progress bar is visible for
    // sufficient time so that there is no flicker.
    private static final long PROCESS_KILL_DELAY_MS = 1000;

    private static final int RESTART_REQUEST_CODE = 42; // the answer to everything

    private final Context mContext;

    public RestartHomeApp(Context context) {
        mContext = context;
    }

    protected void saveNewValue(){}

    protected void clear(){}

    @Override
    public void run() {
        // Synchronously write the preference.
        saveNewValue();
        // Clear something
        clear();

        // Wait for it
        try {
            Thread.sleep(PROCESS_KILL_DELAY_MS);
        } catch (Exception e) {
            Log.e(TAG, "Error waiting", e);
        }

        // Schedule an alarm before we kill ourself.
        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(mContext.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(mContext, RESTART_REQUEST_CODE,
                homeIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
        mContext.getSystemService(AlarmManager.class).setExact(
                AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 50, pi);

        // Kill process
        android.os.Process.killProcess(android.os.Process.myPid());
    }

}
