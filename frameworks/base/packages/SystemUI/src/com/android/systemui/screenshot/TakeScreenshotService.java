/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui.screenshot;

import java.util.List;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

public class TakeScreenshotService extends Service {
    private static final String TAG = "TakeScreenshotService";
    public static final int UPDATE_LONG_SCREENSHOT = 4;
    public static final int CONTINUE_LONG_SCREENSHOT = 5;
    public static final int STOP_LONG_SCREENSHOT = 6;
    public static final int NOT_SUPPORT_LONG_SCREENSHOT = 7;
    public static final int COMPLETE_LONG_SCREENSHOT = 8;
    public static final int UPDATE_LONG_SCREENSHOT_SUPPORT_STATE = 10;
    public static final int START_LONG_SCREENSHOT = 11;
    public static final int LONG_SCREENSHOT_UNKOWN_ERROR = 12;
    public static final int CANCEL_LONG_SCREENSHOT = 13;
    private static final boolean LONG_SCREENSHOT_ENABLED = SystemProperties.getBoolean("ro.longscreenshot.enable", true);

    private static GlobalScreenshot mScreenshot;
    private Runnable finisher;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            final Messenger callback = msg.replyTo;
            if (msg.what <= 3) {
            finisher = new Runnable() {
                    @Override
                    public void run() {
                        Message reply = Message.obtain(null, 1);
                        try {
                            callback.send(reply);
                        } catch (RemoteException e) {
                        }
                    }
                };
            } else if (finisher != null && msg.what > CANCEL_LONG_SCREENSHOT){
                finisher.run();
                finisher = null;
            }
            /*SPRD bug 685985:Maybe cause ANR.*/
            if(ActivityManager.isUserAMonkey()){
                Log.w(TAG, "isUserAMonkey ignore!");
                if (finisher != null){
                    finisher.run();
                    finisher = null;
                }
                return;
            }
            /*@}*/
            if (!getSystemService(UserManager.class).isUserUnlocked()) {
                Log.w(TAG, "Skipping screenshot because storage is locked!");
                if (finisher != null){
                    finisher.run();
                    finisher = null;
                }
                return;
            }
            if (mScreenshot == null) {
                mScreenshot = new GlobalScreenshot(TakeScreenshotService.this);
            }
            mScreenshot.setScreenshotService(TakeScreenshotService.this);

            switch (msg.what) {
                case WindowManager.TAKE_SCREENSHOT_FULLSCREEN:
                    mScreenshot.takeScreenshot(finisher, msg.arg1 > 0, msg.arg2 > 0);
                    break;
                case WindowManager.TAKE_SCREENSHOT_SELECTED_REGION:
                    mScreenshot.takeScreenshotPartial(finisher, msg.arg1 > 0, msg.arg2 > 0);
                    break;
                case WindowManager.TAKE_LONG_SCREENSHOT_SELECTED_REGION:
                    if (LONG_SCREENSHOT_ENABLED) {
                        mScreenshot.takeRegionScreenshot(finisher);
                    } else {
                       Toast.makeText(TakeScreenshotService.this, "long screenshot disabled",  Toast.LENGTH_SHORT).show();
                    }
                    break;
                case UPDATE_LONG_SCREENSHOT:
                    Bitmap b = (Bitmap)msg.obj;
                    Log.d(TAG, "Bitmap height = " +  b.getHeight());
                    int overlayViewsTop = msg.arg1;
                    int secondBitmapHeight = msg.arg2;

                    mScreenshot.updateLongScreenshotView(b, overlayViewsTop, secondBitmapHeight);
                    break;
                case NOT_SUPPORT_LONG_SCREENSHOT:
                    mScreenshot.notSupportLongscreenshot();
                    break;
                case COMPLETE_LONG_SCREENSHOT:
                    mScreenshot.completeLongscreenshot();
                    break;
                case UPDATE_LONG_SCREENSHOT_SUPPORT_STATE:
                    replyMesenger = msg.replyTo;
                    boolean supportLongScreenshot = msg.arg1 == 1;
                    mScreenshot.setSupportLongScreenshot(supportLongScreenshot);
                    break;
                case LONG_SCREENSHOT_UNKOWN_ERROR:
                    mScreenshot.notifyScreenshotError();
                    break;
                default:
                    Log.d(TAG, "Invalid screenshot option: " + msg.what);
            }
        }
    };

    private Messenger replyMesenger = null;
    @Override
    public IBinder onBind(Intent intent) {
        return new Messenger(mHandler).getBinder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mScreenshot != null && !mScreenshot.isbitmapAnimating()) {
            mScreenshot.stopScreenshot();
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    public void sendLongscreenshotMessage(int what) {
        if (replyMesenger != null) {
            try {
                Message msg = Message.obtain(null, what);
                replyMesenger.send(msg);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }
}
