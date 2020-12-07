/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.incallui.sprd;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import com.android.dialer.app.R;
import com.android.dialer.notification.NotificationChannelId;
import com.android.dialer.notification.NotificationChannelManager;

/**
 * This class adds Notifications to the status bar for the in-call experience.
 */
public class PhoneRecordNotifier {

    private static final int NOTIFICATION_ID = 1000;
    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private static PhoneRecordNotifier mPhoneRecordNotifier;

    private PhoneRecordNotifier(Context context) {
        mContext = context;
        mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public static PhoneRecordNotifier getInstance(Context context) {
        if (mPhoneRecordNotifier == null) {
            mPhoneRecordNotifier = new PhoneRecordNotifier(context);
        }
        return mPhoneRecordNotifier;
    }
    //method to show notification
    public void showNotification(String title, String path) {
        Notification.Builder builder =
                new Notification.Builder(mContext)
                        .setSmallIcon(R.drawable.quantum_ic_record_white_36_ex)
                        .setColor(mContext.getColor(R.color.dialer_theme_color))
                        .setContentTitle(mContext.getString(R.string.call_recording_saved))
                        .setContentText(mContext.getString(R.string.click_to_view_the_recorded_file));

        /*
         *  UNISOC : Modify for bug 895598 @{
         *  Open call recording list on the click of call recording notification.
         */
        Intent resultIntent = new Intent("com.android.soundrecorder.EXTRA_FILE_LIST");
        resultIntent.putExtra("Callrecordlist", true);
        /* @} */
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(mContext, 0, resultIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        builder.setContentIntent(resultPendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        builder.setChannelId(NotificationChannelId.DEFAULT);
        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(NOTIFICATION_ID, notification);
    }
    //method to cancel notification
    public void cancelNotification(Context context) {
        mNotificationManager.cancel(NOTIFICATION_ID);
    }
}
