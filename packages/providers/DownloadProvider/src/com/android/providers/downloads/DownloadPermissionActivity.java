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

package com.android.providers.downloads;

import java.io.File;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.UserHandle;
import android.net.Uri;

/*
** request permisson to write external storage
*/
public class DownloadPermissionActivity extends Activity {
    private static final String TAG = "DownloadManager:DownloadPermissionActivity";
    private static final boolean DEBUG = true;
    private static final int REQUEST_CODE = 777;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        final String volumePath = intent.getStringExtra(Constants.VOLUME_PATH);
        if (DEBUG) Log.d(TAG, "onCreate, volumePath="+volumePath);

        final StorageVolume volume = StorageManager.getStorageVolume(
                new File(volumePath), UserHandle.myUserId());
        if (volume == null) {
            finish();
        } else {
            final Intent requestIntent= volume.createAccessIntent(null);
            if (requestIntent != null) {
                startActivityForResult(requestIntent, REQUEST_CODE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (REQUEST_CODE == requestCode) {
            if (Activity.RESULT_OK == resultCode) {
                Uri grantUri = data.getData();
                int flags = data.getFlags()
						& (Intent.FLAG_GRANT_READ_URI_PERMISSION
						| Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(grantUri, flags);
                if (DEBUG) Log.d(TAG, "onActivityResult, grantUri="+grantUri+"  flags="+flags);
            }
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * TODO: if finish() is not called, app will crash with an exception such as:
         * AndroidRuntime: java.lang.RuntimeException: Unable to resume activity 
         * {com.android.providers.downloads/com.android.providers.downloads.DownloadPermissionActivity}:
         */
        finish();
    }
}
