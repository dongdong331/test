/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IPowerManager;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.WindowManager;
import android.os.Handler;

public class ShutdownCountdownActivity extends Activity {

    private static final String TAG = "ShutdownCountdownActivity";
    static boolean singleInstance = false;
    private int mSeconds = 15;
    private PowerManager.WakeLock mWakeLock;
    private AlertDialog mDialog;
    private IPowerManager mPm;
    //SPRD: add for bug843381 BEG
    private int mSecondsForOverheat = 3;
    private static final int SHUTDOWN_FOR_OVERHEAT_FLAG = 1;
    //SPRD: add for bug843381 BEG

    private Handler mHandler = new Handler();
    private Runnable mShutdownAction = new Runnable() {
        @Override
        public void run() {
            mSeconds --;
            if(mDialog != null){
                if (mSeconds > 0) {
                    mDialog.setMessage(getResources().getQuantityString(
                            com.android.internal.R.plurals.shutdown_after_seconds_plurals, mSeconds,
                            mSeconds));
                } else {
                    mDialog.setMessage(getString(com.android.internal.R.string.shutdown_confirm));
                }
            }
            if(mSeconds > 0){
                mHandler.postDelayed(mShutdownAction, 1000);
            }else{
                mHandler.post(new Runnable() {
                    public void run() {
                        Slog.i(TAG,"ShutdownThread->shutdown");
                        try{
                            if(mDialog != null){
                                mDialog.dismiss();
                            }
                            //SPRD: Bug#612163 add shutdown reason for PhoneInfo feature  BEG-->
                            mPm.shutdown(false, "timer", false);
                            //<-- add shutdown reason for PhoneInfo feature  END
                        }catch(RemoteException e){}
                    }
                });
            }
        }
    };
    //SPRD: add for bug843381 BEG
    private Runnable mShutdownForOverheat = new Runnable() {
        @Override
        public void run() {
            mSecondsForOverheat --;
            if(mDialog != null){
                if (mSecondsForOverheat > 0) {
                    mDialog.setMessage(getResources().getQuantityString(
                            com.android.internal.R.plurals.overheat_shutdown_after_seconds_plurals, mSecondsForOverheat,
                            mSecondsForOverheat));
                } else {
                    mDialog.setMessage(getString(com.android.internal.R.string.shutdown_confirm));
                }
            }
            if(mSecondsForOverheat > 0){
                mHandler.postDelayed(mShutdownForOverheat, 1000);
            }else{
                mHandler.post(new Runnable() {
                    public void run() {
                        Slog.i(TAG,"ShutdownThread->shutdown");
                        try{
                            if(mDialog != null){
                                mDialog.dismiss();
                            }
                            mPm.shutdown(false, "timer", false);
                        }catch(RemoteException e){}
                    }
                });
            }
        }
    };
    //SPRD: add for bug843381 END

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(singleInstance && mHandler != null
            && !mHandler.hasCallbacks(mShutdownAction) && !mHandler.hasCallbacks(mShutdownForOverheat)) {
            Slog.i(TAG, "already has ShutdownCountdownActivity, exit onCreate!");
            return;
        }
        singleInstance = true;
        Intent intent = getIntent();
        String mAction = intent.getAction();
        Slog.i(TAG, "onCreate(): mAction=" + mAction);

        mPm = IPowerManager.Stub.asInterface(ServiceManager.getService(Context.POWER_SERVICE));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        // SPRD: Fix bug654213
        getWindow().getDecorView().setAlpha(0.0f);
        // SPRD: Fix bug614904 for TimerShutdown . @{
        PowerManager pm = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShutdownActivity");
        Slog.i(TAG, "Countdown dialog need power wake lock to show countdown normally ,acquire mWakeLock" );
        mWakeLock.acquire();
        // SPRD: bug614904 end.
        mDialog = new AlertDialog.Builder(this,android.R.style.Theme_Holo_Light_Dialog_MinWidth).create();
        mDialog.getWindow().setBackgroundDrawableResource(com.android.internal.R.color.transparent);
        mDialog.setTitle(com.android.internal.R.string.power_off);
        //SPRD: add for bug843381 BEG
        if(intent.getIntExtra("ShutdownDueToOverheat",0) == SHUTDOWN_FOR_OVERHEAT_FLAG) {
            if (mSecondsForOverheat > 0) {
                mDialog.setMessage(getResources().getQuantityString(
                        com.android.internal.R.plurals.overheat_shutdown_after_seconds_plurals, mSecondsForOverheat, mSecondsForOverheat));
            } else {
                mDialog.setMessage(getString(com.android.internal.R.string.shutdown_confirm));
            }
            mDialog.setCancelable(false);
            mDialog.getWindow().getAttributes().setTitle("ShutdownTiming");
            mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            mDialog.show();
            mHandler.postDelayed(mShutdownForOverheat, 1000);
            //SPRD: add for bug843381 END

        } else {
            if (mSeconds > 0) {
                mDialog.setMessage(getResources().getQuantityString(
                        com.android.internal.R.plurals.shutdown_after_seconds_plurals, mSeconds, mSeconds));
            } else {
                mDialog.setMessage(getString(com.android.internal.R.string.shutdown_confirm));
            }
            mDialog.setButton(DialogInterface.BUTTON_NEUTRAL, getText(com.android.internal.R.string.cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mHandler.removeCallbacks(mShutdownAction);
                    dialog.cancel();
                    finish();
                }});
            mDialog.setCancelable(false);
            mDialog.getWindow().getAttributes().setTitle("ShutdownTiming");
            mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            mDialog.show();
            mHandler.postDelayed(mShutdownAction, 1000);
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(singleInstance && mHandler != null
            && (mHandler.hasCallbacks(mShutdownAction) || mHandler.hasCallbacks(mShutdownForOverheat))) {
            Slog.i(TAG, "already has ShutdownCountdownActivity, exit onDestroy!");
            return;
        }
        singleInstance = false;
        if(mDialog != null){
            mDialog.dismiss();
            mDialog = null;
        }
        // SPRD: Fix bug614904 for TimerShutdown . @{
        if (mWakeLock != null) {
          Slog.i(TAG,"Countdown dialog dismiss - release mWakeLock.");
          mWakeLock.release();
        }
        //SPRD:bug614904 end.
    }
}
