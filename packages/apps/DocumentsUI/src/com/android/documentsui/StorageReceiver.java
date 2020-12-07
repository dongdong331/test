package com.android.documentsui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.ActivityManager;
import android.net.Uri;
import java.util.List;
import android.app.ActivityManager.AppTask;
import android.util.Log;
import android.provider.DocumentsContract;

public class StorageReceiver extends BroadcastReceiver {

    private static final String TAG = "DocUIStorageReceiver";
    /*
     * <intent-filter> 
     * <action android:name="android.intent.action.MEDIA_EJECT" /> <action android:name="android.intent.action.MEDIA_MOUNTED" /> 
     * <action android:name="android.intent.action.MEDIA_BAD_REMOVAL" /> <action android:name="android.intent.action.MEDIA_UNMOUNTED" />
     */

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive intent " + intent);
        ActivityManager activities =(ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<AppTask> tasks = activities.getAppTasks();
        for (AppTask task : tasks) {
            Intent intentTask = task.getTaskInfo().baseIntent;
            Uri uri = intentTask.getData();
            if( (uri != null) && "com.android.externalstorage.documents".equals(uri.getAuthority())
                && !DocumentsContract.getRootId(uri).equals("primary")) {
                task.finishAndRemoveTask();
                Log.d(TAG, "onReceive remove task " + uri.toString());
            }
        }
    }
}


