/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.documentsui;

import static com.android.documentsui.base.SharedMinimal.VERBOSE;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.OperationCanceledException;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.storage.VolumeInfo;
import android.os.UserHandle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.base.Shared;
import com.android.documentsui.PlugInDrm.DocumentsUIPlugInDrm;
import com.android.documentsui.ProviderExecutor.Preemptable;

import java.io.File;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 *  Loads a Thumbnails asynchronously then animates from the mime icon to the thumbnail
 */
public final class ThumbnailLoader extends AsyncTask<Uri, Void, Bitmap> implements Preemptable {

    private static final String TAG = ThumbnailLoader.class.getCanonicalName();
    private static String mDrmPath;
    private static boolean mIsDrm;

    private static boolean mIsVideoThumbnailEnabled = true;

    /**
     * Two animations applied to image views. The first is used to switch mime icon and thumbnail.
     * The second is used when we need to update thumbnail.
     */
    public static final BiConsumer<View, View> ANIM_FADE_IN = (mime, thumb) -> {
        float alpha = mime.getAlpha();
        mime.animate().alpha(0f).start();
        thumb.setAlpha(0f);
        thumb.animate().alpha(alpha).start();
    };
    public static final BiConsumer<View, View> ANIM_NO_OP = (mime, thumb) -> {};

    private final ImageView mIconThumb;
    private final Point mThumbSize;
    private final Uri mUri;
    private final long mLastModified;
    private final Consumer<Bitmap> mCallback;
    private final boolean mAddToCache;
    private final CancellationSignal mSignal;

    /**
     * @param uri - to a thumbnail.
     * @param iconThumb - ImageView to display the thumbnail.
     * @param thumbSize - size of the thumbnail.
     * @param lastModified - used for updating thumbnail caches.
     * @param addToCache - flag that determines if the loader saves the thumbnail to the cache.
     */
    public ThumbnailLoader(Uri uri, ImageView iconThumb, Point thumbSize, long lastModified,
        Consumer<Bitmap> callback, boolean addToCache) {

        mUri = uri;
        mIconThumb = iconThumb;
        mThumbSize = thumbSize;
        mLastModified = lastModified;
        mCallback = callback;
        mAddToCache = addToCache;
        mSignal = new CancellationSignal();
        mIconThumb.setTag(this);

        if (VERBOSE) Log.v(TAG, "Starting icon loader task for " + mUri);
    }

    public void setVideoThumbnailEnable(boolean bEnable) {
        mIsVideoThumbnailEnabled = bEnable;
    }

    @Override
    public void preempt() {
        if (VERBOSE) Log.v(TAG, "Icon loader task for " + mUri + " was cancelled.");
        cancel(false);
        mSignal.cancel();
    }

    @Override
    protected Bitmap doInBackground(Uri... params) {
        if (isCancelled()) {
            return null;
        }

        final Context context = mIconThumb.getContext();
        final ContentResolver resolver = context.getContentResolver();

        ContentProviderClient client = null;
        Bitmap result = null;
        mIsDrm = false;
        try {
            mDrmPath = DocumentsUIPlugInDrm.getInstance().getDrmPath(context,mUri);
            mIsDrm = DocumentsUIPlugInDrm.getInstance().getIsDrm(context,mDrmPath);
        } catch (Exception e) {
            Log.d(TAG, "Failed to get drm info, maybe drm is not supported in this resolver");
        }
        Log.d(TAG, "LoaderTask, mDrmPath = " + mDrmPath + ", mIsDrm = " + mIsDrm);
        if (mIsDrm) {
            result = DocumentsUIPlugInDrm.getInstance().getIconBitmap(context, MimeTypes.DRM_TYPE, mDrmPath);
            if (result != null && mAddToCache) {
                final ThumbnailCache cache = DocumentsApplication.getThumbnailCache(context);
                cache.putThumbnail(mUri, mThumbSize, result, mLastModified);
            }
        } else {
            try {
                client = DocumentsApplication.acquireUnstableProviderOrThrow(
                    resolver, mUri.getAuthority());

                long id = -1;
                String filePath = null;
                filePath = getPath(context, mUri);
                if (filePath != null) {
                    id = getMediaVideoID(resolver, filePath);
                }

                if (id != -1) {
                    if (mIsVideoThumbnailEnabled) {
                        result = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MINI_KIND);
                    }
                } else {
                    result = DocumentsContract.getDocumentThumbnail(client, mUri, mThumbSize, mSignal);
                }

                if (result != null && mAddToCache) {
                    final ThumbnailCache cache = DocumentsApplication.getThumbnailCache(context);
                    cache.putThumbnail(mUri, mThumbSize, result, mLastModified);
                }
            } catch (Exception e) {
                if (!(e instanceof OperationCanceledException)) {
                    Log.w(TAG, "Failed to load thumbnail for " + mUri + ": " + e);
                }
            } finally {
                ContentProviderClient.releaseQuietly(client);
            }
        }
        return result;
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        if (VERBOSE) Log.v(TAG, "Loader task for " + mUri + " completed");

        if (mIconThumb.getTag() == this) {
            mIconThumb.setTag(null);
            mCallback.accept(result);
        }
    }

    private String getPath(final Context context, final Uri uri) {
        if (uri == null) {
            return null;
        }

        if (Shared.isExternalStorageDocument(uri)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            final String type = split[0];

            final StorageManager storage = context.getSystemService(StorageManager.class);
            VolumeInfo info = null;
            StorageVolume primary = null;
            if ("primary".equalsIgnoreCase(type)) {
                primary = storage.getPrimaryStorageVolume();
            } else {
                info = storage.findVolumeByUuid(type);
            }
            final int userId = UserHandle.myUserId();
            String path = null;
            if (info != null) {
                File file = info.getPathForUser(userId);
                if (file != null) {
                    path = file.getPath();
                }
            }
            if (primary != null) {
                path = primary.getPath();
            }

            if (split.length > 1) {
                path = path + "/" + split[1];
            }
            return path;
        }
        // DownloadsProvider
        else if (Shared.isDownloadsDocument(uri)) {
            try {
                final String docId = DocumentsContract.getDocumentId(uri);
                if("downloads".equals(docId)) {
                     File downloadroot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                     if(downloadroot != null) {
                         return downloadroot.getAbsolutePath();
                     }
                }
                final String[] split = docId.split(":");
                final String id = split[0];
                if (split.length > 1) {
                    String path =  split[1];
                    return path;
                } else {
                    final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/all_downloads"), Long.valueOf(id));
                    Log.d(TAG,"entent getPath isDownloadsDocument contentUri = " + contentUri);
                    String path = getDataColumn(context, contentUri, null, null);
                    return path;
                }
             } catch (RuntimeException e) {
               Log.e(TAG, uri + " is not a download uri");
               return null;
             }
        }
        return null;
    }

    private String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
            column
        };

        if (uri != null) {
            try {
                cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
                if (cursor != null && cursor.moveToFirst()) {
                    final int index = cursor.getColumnIndexOrThrow(column);
                    return cursor.getString(index);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return null;
    }

    private long getMediaVideoID(ContentResolver resolver, String filePath) {
        Cursor cursor = null;
        long id = -1;
        String mimeType;

        try {
            cursor = resolver.query(MediaStore.Files.getContentUri("external"),
                         new String[] { "_id, mime_type" }, MediaStore.Files.FileColumns.DATA + "=?",
                         new String[] { filePath }, null);
            if (cursor.getCount() > 0) {
                cursor.moveToNext();
                mimeType = cursor.getString(1);
                if (mimeType != null && mimeType.startsWith("video/")) {
                     id = cursor.getLong(0);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Query database error!");
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
        }
        return id;
    }
}
