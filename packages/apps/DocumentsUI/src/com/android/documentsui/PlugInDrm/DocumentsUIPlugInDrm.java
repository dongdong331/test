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

package com.android.documentsui.PlugInDrm;

import android.content.Context;
import android.os.Handler;
import android.net.Uri;
import android.app.Activity;
import com.android.documentsui.base.DocumentInfo;
import android.app.AddonManager;
import com.android.documentsui.R;
import android.util.Log;
import android.graphics.Bitmap;

public class DocumentsUIPlugInDrm {

    public final static int ERROR = 0;
    public final static int RESULT_DRM_OK = 1;
    public static final String MIMETYPE_DRM_MESSAGE = "application/vnd.oma.drm.message";

    static DocumentsUIPlugInDrm sInstance;

    public DocumentsUIPlugInDrm() {
    }

    public static DocumentsUIPlugInDrm getInstance() {
        if (sInstance != null)
            return sInstance;
        sInstance = (DocumentsUIPlugInDrm) AddonManager.getDefault().getAddon(R.string.feature_documentsuiplugdrm, DocumentsUIPlugInDrm.class);
        return sInstance;
    }

    public void getDocumentsActivityContext (Context context) {
        return;
    }

    public int checkDrmError(Activity activity , Uri[] uris, String[] acceptMimes) {
        return RESULT_DRM_OK;
    }

    public boolean alertDrmError(int check_ret) {
        return false;
    }

    public boolean isDrmEnabled() {
        return false;
    }

    public void getDrmEnabled() {
    }

    public String getDocMimeType(Context context, String path, String mimeType) {
        return mimeType;
    }

    public boolean setDocMimeType(DocumentInfo doc) {
        return true;
    }

    public String getDrmFilenameFromPath(String path) {
        return path;
    }

    public String getDrmPath(Context context , Uri uri) {
        return null;
    }

    public boolean getIsDrm(Context context , String drmPath){
        return false;
    }

    public Bitmap getIconBitmap(Context context , String docMimeType , String docDisplayName) {
        return null;
    }

    public boolean sendDRMFileIntent(Context context, Uri mUri, Handler mMainThreadHandler) {
        return false;
    }
}
