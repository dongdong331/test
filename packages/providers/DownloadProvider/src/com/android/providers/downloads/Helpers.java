/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static android.os.Environment.buildExternalStorageAppCacheDirs;
import static android.os.Environment.buildExternalStorageAppFilesDirs;
import static android.os.Environment.buildExternalStorageAppMediaDirs;
import static android.os.Environment.buildExternalStorageAppObbDirs;
import static android.provider.Downloads.Impl.FLAG_REQUIRES_CHARGING;
import static android.provider.Downloads.Impl.FLAG_REQUIRES_DEVICE_IDLE;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Downloads;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import android.content.ContentValues;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import com.android.providers.downloads.DownloadConfirmActivity;
import android.provider.DocumentsContract;

/*
 * for downloadprovider_DRM
 *@{
 */
import com.android.providers.downloadsplugin.DownloadsDRMUtils;
/*@}*/

/**
 * Some helper functions for the download manager
 */
public class Helpers {
    private static final String TAG = "DownloadManager:Helpers";
    private static final boolean DEBUG = true;
    public static Random sRandom = new Random(SystemClock.uptimeMillis());
    /*
     * for downloadprovider_DRM
     *@{
     */
    public static final int DRM_PLUGIN = 0;
    public static final int HELPER_FULLNAME_RETURN = 1;
    public static final int HELPER_SUQENCE_CONTINUE = 2;
    /*@}*/

    /** Regex used to parse content-disposition headers */
    private static final Pattern CONTENT_DISPOSITION_PATTERN =
            Pattern.compile("attachment;\\s*filename\\s*=\\s*\"([^\"]*)\"");

    private static final Object sUniqueLock = new Object();

    private static HandlerThread sAsyncHandlerThread;
    private static Handler sAsyncHandler;

    private static SystemFacade sSystemFacade;
    private static DownloadNotifier sNotifier;

    private Helpers() {
    }

    public synchronized static Handler getAsyncHandler() {
        if (sAsyncHandlerThread == null) {
            sAsyncHandlerThread = new HandlerThread("sAsyncHandlerThread",
                    Process.THREAD_PRIORITY_BACKGROUND);
            sAsyncHandlerThread.start();
            sAsyncHandler = new Handler(sAsyncHandlerThread.getLooper());
        }
        return sAsyncHandler;
    }

    @VisibleForTesting
    public synchronized static void setSystemFacade(SystemFacade systemFacade) {
        sSystemFacade = systemFacade;
    }

    public synchronized static SystemFacade getSystemFacade(Context context) {
        if (sSystemFacade == null) {
            sSystemFacade = new RealSystemFacade(context);
        }
        return sSystemFacade;
    }

    public synchronized static DownloadNotifier getDownloadNotifier(Context context) {
        if (sNotifier == null) {
            sNotifier = new DownloadNotifier(context);
        }
        return sNotifier;
    }

    public static String getString(Cursor cursor, String col) {
        return cursor.getString(cursor.getColumnIndexOrThrow(col));
    }

    public static int getInt(Cursor cursor, String col) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(col));
    }

    /*
     * pop up dialog to confirm whether to start a download in GPRS.
     * use it only if download alert is enabled.
     */
    public static void showDownloadConfirmDialog(Context context) {
        if (DEBUG) Log.d(TAG, "showDownloadConfirmDialog");
        if (DownloadConfirmActivity.isConfirmAlertDialogExist()) {
            return;
        }
        final Intent intent = new Intent(Constants.ACTION_DOWNLOAD_TO_CONFIRME);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            if (DEBUG) Log.d(TAG, "showDownloadConfirmDialog ActivityNotFoundException");
        }
    }

    /*
     * close alert dialog.
     * use it only if download alert is enabled.
     */
    public static void closeAlertDialog(Context context) {
        if (DEBUG) Log.d(TAG, "closeAlertDialog");
        DownloadConfirmActivity.closeAlertDialogWithConfirm(-1);
    }

    /*
     * close alert dialog with a confirm result.
     * use it only if download alert is enabled.
     */
    public static void closeAlertDialog(Context context, boolean ConfirmToDownload) {
        if (DEBUG) Log.d(TAG, "closeAlertDialog, ConfirmToDownload="+ConfirmToDownload);
        DownloadConfirmActivity.closeAlertDialogWithConfirm(ConfirmToDownload ? 1 : 0);
    }

    public static void setControl(Context context, int control, long... ids) {
        if((null != ids) && (0 < ids.length)){
            final ContentValues values = new ContentValues();
            values.put(Downloads.Impl.COLUMN_CONTROL, control);
            context.getContentResolver().update(Downloads.Impl.ALL_DOWNLOADS_CONTENT_URI, values, DownloadManager.getWhereClauseForIds(ids),
                    DownloadManager.getWhereArgsForIds(ids));
        }
    }

    /*
     * correct the wrong state maybe caused by accidental shutdown.
     * use it only if download alert is enabled.
     */
    public static void resetUnfinishedDownload(Context context) {
        long ids[] = getIdsWithControlFilter(context, Downloads.Impl.CONTROL_RUN);

        if (DEBUG) Log.d(TAG, "resetUnfinishedDownload, num="+(null==ids?0:ids.length));
        if((null != ids) && (0 < ids.length)){
            Helpers.pauseDownloadForNetConfirm(context, ids);
        }
    }

    /*
     * cancel the downloads according to the dialg confirmation.
     * use it only if download alert is enabled.
     */
    public static void cancelConfirmingDownloads(Context context) {
        int nums = 0;
        int numsUpdate = 0;
        int numsDelete = 0;
        long[] idsUpdate;
        long[] idsDelete;

        long ids[] = getIdsWithStatusFilter(context, DownloadManager.STATUS_WAITING_FOR_CONFIRM_NETWORK);
        if((null == ids) || (0 >= ids.length)){
            if (DEBUG) Log.d(TAG, "cancelConfirmingDownloads, no waiting download!!!");
            return;
        }
        if (DEBUG) Log.d(TAG, "cancelConfirmingDownloads, num="+ids.length);

        nums = ids.length;
        idsUpdate = new long[nums];
        idsDelete = new long[nums];

        for(int i=0;i<nums;i++){
            long downloadId = ids[i];
            DownloadInfo downloadInfo = DownloadInfo.queryDownloadInfo(context, downloadId);
            if(null != downloadInfo){
                if(downloadInfo.mCurrentBytes >0){
                    idsUpdate[numsUpdate++] = downloadId;
                    if (DEBUG) Log.d(TAG, "cancelConfirmingDownloads, update id="+downloadId);
                }else{
                    idsDelete[numsDelete++] = downloadId;
                    if (DEBUG) Log.d(TAG, "cancelConfirmingDownloads, delete id="+downloadId);
                }
            }
        }

        if(numsUpdate > 0){
            long[] idsUpdateNew = new long[numsUpdate];
            System.arraycopy(idsUpdate, 0, idsUpdateNew, 0, numsUpdate);
            final ContentValues values = new ContentValues();
            values.put(Downloads.Impl.COLUMN_STATUS, Downloads.Impl.STATUS_PAUSED_BY_APP);
            values.put(Downloads.Impl.COLUMN_CONTROL, Constants.TAG_PAUSED_BY_OWNER);
            context.getContentResolver().update(Downloads.Impl.ALL_DOWNLOADS_CONTENT_URI, values, DownloadManager.getWhereClauseForIds(idsUpdateNew),
                    DownloadManager.getWhereArgsForIds(idsUpdateNew));
        }

        if(numsDelete > 0){
            long[] idsDeleteNew = new long[numsDelete];
            System.arraycopy(idsDelete, 0, idsDeleteNew, 0, numsDelete);
            context.getContentResolver().delete(Downloads.Impl.ALL_DOWNLOADS_CONTENT_URI, DownloadManager.getWhereClauseForIds(idsDeleteNew),
                    DownloadManager.getWhereArgsForIds(idsDeleteNew));
        }
    }

    /*
     * pause and set state as waiting, before schedule a download job in GPRS or when network losts.
     * use it only if download alert is enabled.
     */
    public static void pauseDownloadForNetConfirm(Context context, long... ids) {
        if (DEBUG) Log.d(TAG, "pauseDownloadForNetConfirm, num="+(null==ids?0:ids.length));
        if((null != ids) && (0 < ids.length)){
            for(int i = 0; i < ids.length; i++){
                if (DEBUG) Log.d(TAG, "pauseDownloadForNetConfirm, ids["+i+"]="+ids[i]);
            }
            final ContentValues values = new ContentValues();
            values.put(Downloads.Impl.COLUMN_STATUS, Downloads.Impl.STATUS_WAITING_FOR_CONFIRM_NETWORK);
            values.put(Downloads.Impl.COLUMN_CONTROL, Downloads.Impl.CONTROL_PAUSED);
            int count = context.getContentResolver().update(Downloads.Impl.ALL_DOWNLOADS_CONTENT_URI, values, DownloadManager.getWhereClauseForIds(ids),
                    DownloadManager.getWhereArgsForIds(ids));
        }
    }

    /*
     * start the downloads according to the dialg confirmation.
     * use it only if download alert is enabled.
     */
    public static void confirmToDownload(Context context) {
        long ids[] = getIdsWithStatusFilter(context, DownloadManager.STATUS_WAITING_FOR_CONFIRM_NETWORK);
        if((null == ids) || (0 >= ids.length)){
            if (DEBUG) Log.d(TAG, "confirmToDownload, no waiting download!!!");
            return;
        }
        if (DEBUG) Log.d(TAG, "confirmToDownload, num="+ids.length);
        for(int i = 0; i < ids.length; i++){
            if (DEBUG) Log.d(TAG, "confirmToDownload, ids["+i+"]="+ids[i]);
        }
        ContentValues values = new ContentValues();
        values.put(Downloads.Impl.COLUMN_STATUS, Downloads.Impl.STATUS_CONFIRMED);
        values.put(Downloads.Impl.COLUMN_CONTROL, Downloads.Impl.CONTROL_RUN);
        context.getContentResolver().update(Downloads.Impl.ALL_DOWNLOADS_CONTENT_URI, values,
                DownloadManager.getWhereClauseForIds(ids), DownloadManager.getWhereArgsForIds(ids));
    }

    public static long[] getIdsWithStatusFilter(Context context, int status) {
        DownloadManager dm = (DownloadManager) context.getSystemService(
                Context.DOWNLOAD_SERVICE);
        long ids[] = null;
        int i = 0;
        Cursor cursor = null;
        try {
            cursor = dm.query(new DownloadManager.Query().setFilterByStatus(status));
            final int count = cursor.getCount();
            if(count > 0){
                ids = new long[count];
                while (cursor.moveToNext()) {
                    ids[i] = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
                    i++;
                }
            }
        } finally {
            if(cursor != null) cursor.close();
        }
        return ids;
    }

    public static long[] getIdsWithControlFilter(Context context, int control) {
        DownloadManager dm = (DownloadManager) context.getSystemService(
                Context.DOWNLOAD_SERVICE);
        long ids[] = null;
        int i = 0;
        Cursor cursor = null;
        try {
            cursor = dm.query(new DownloadManager.Query().setFilterByControl(control));
            final int count = cursor.getCount();
            if(count > 0){
                ids = new long[count];
                while (cursor.moveToNext()) {
                    ids[i] = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
                    i++;
                }
            }
        } finally {
            if(cursor != null) cursor.close();
        }
        return ids;
    }

    public static long[] getIdsWithStatusAndControlFilter(Context context, int status, int control) {
        DownloadManager dm = (DownloadManager) context.getSystemService(
                Context.DOWNLOAD_SERVICE);
        long ids[] = null;
        int i = 0;
        Cursor cursor = null;
        try {
            cursor = dm.query(new DownloadManager.Query().setFilterByStatus(status)
                    .setFilterByControl(control));
            final int count = cursor.getCount();
            if(count > 0){
                ids = new long[count];
                while (cursor.moveToNext()) {
                    ids[i] = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
                    i++;
                }
            }
        } finally {
            if(cursor != null) cursor.close();
        }
        return ids;
    }

    public static void scheduleJob(Context context, long downloadId) {
        final boolean scheduled = scheduleJob(context,
                DownloadInfo.queryDownloadInfo(context, downloadId));
        if (!scheduled) {
            // If we didn't schedule a future job, kick off a notification
            // update pass immediately
            getDownloadNotifier(context).update();
        }
    }

    /**
     * Schedule (or reschedule) a job for the given {@link DownloadInfo} using
     * its current state to define job constraints.
     */
    public static boolean scheduleJob(Context context, DownloadInfo info) {
        if (DEBUG) Log.d(TAG, "scheduleJob");
        if (info == null) return false;

        final JobScheduler scheduler = context.getSystemService(JobScheduler.class);

        // Tear down any existing job for this download
        final int jobId = (int) info.mId;
        scheduler.cancel(jobId);

        // Skip scheduling if download is paused or finished
        if (!info.isReadyToSchedule()) return false;

        final JobInfo.Builder builder = new JobInfo.Builder(jobId,
                new ComponentName(context, DownloadJobService.class));

        // When this download will show a notification, run with a higher
        // priority, since it's effectively a foreground service
        if (info.isVisible()) {
            builder.setPriority(JobInfo.PRIORITY_FOREGROUND_APP);
            builder.setFlags(JobInfo.FLAG_WILL_BE_FOREGROUND);
        }

        // We might have a backoff constraint due to errors
        final long latency = info.getMinimumLatency();
        if (latency > 0) {
            builder.setMinimumLatency(latency);
        }

        // We always require a network, but the type of network might be further
        // restricted based on download request or user override
        builder.setRequiredNetworkType(info.getRequiredNetworkType(info.mTotalBytes));

        if ((info.mFlags & FLAG_REQUIRES_CHARGING) != 0) {
            builder.setRequiresCharging(true);
        }
        if ((info.mFlags & FLAG_REQUIRES_DEVICE_IDLE) != 0) {
            builder.setRequiresDeviceIdle(true);
        }
        if (DownloadManager.isDownloadAlertEnabled()) {
            builder.setRescheduleEnable(true);
        }

        // Provide estimated network size, when possible
        if (info.mTotalBytes > 0) {
            if (info.mCurrentBytes > 0 && !TextUtils.isEmpty(info.mETag)) {
                // If we're resuming an in-progress download, we only need to
                // download the remaining bytes.
                builder.setEstimatedNetworkBytes(info.mTotalBytes - info.mCurrentBytes);
            } else {
                builder.setEstimatedNetworkBytes(info.mTotalBytes);
            }
        }

        // If package name was filtered during insert (probably due to being
        // invalid), blame based on the requesting UID instead
        String packageName = info.mPackage;
        if (packageName == null) {
            packageName = context.getPackageManager().getPackagesForUid(info.mUid)[0];
        }

        scheduler.scheduleAsPackage(builder.build(), packageName, UserHandle.myUserId(), TAG);
        return true;
    }

    /*
     * Parse the Content-Disposition HTTP Header. The format of the header
     * is defined here: http://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html
     * This header provides a filename for content that is going to be
     * downloaded to the file system. We only support the attachment type.
     */
    private static String parseContentDisposition(String contentDisposition) {
        try {
            Matcher m = CONTENT_DISPOSITION_PATTERN.matcher(contentDisposition);
            if (m.find()) {
                return m.group(1);
            }
        } catch (IllegalStateException ex) {
             // This function is defined as returning null when it can't parse the header
        }
        return null;
    }

    public static boolean isWritePermissionNeeded(final String filePath) {
        boolean result = false;
        if (filePath != null) {
            String[] parts = filePath.split("/");
            if (parts.length >= 3) {
                if (parts[1].equals("storage") && !parts[2].equals("emulated")) {
                    result = true;
                }
            }
        }
        return result;
    }

    private static String getDocId(final String filePath) {
        String docId;
        int index = filePath.indexOf("/storage/");
        if (index == 0) {
            docId = filePath.substring(9);
        } else {
            Log.w(TAG, "getDocId, filePath is wrong");
            return null;
        }
        index = docId.indexOf("/");
        if (index == -1) {
            docId = docId + ":";
        } else {
            docId = docId.replaceFirst("/", ":");
        }
        return docId;
    }

    public static String getUriPermission(final String filePath) {
        String volumePath = null;
        final String docId = getDocId(filePath);
        if (docId != null) {
            volumePath = docId.substring(0, docId.indexOf(":")+1);
        }
        final Uri grantUri = DocumentsContract.buildTreeDocumentUri(
                Constants.AUTHORITY_EXTERNAL_STORAGE, volumePath);
        return grantUri.toString();
    }

    public static Uri generateGrantUri(final String filePath) {
        Uri grantUri = Uri.parse(getUriPermission(filePath));
        final String docId = getDocId(filePath);
        grantUri = DocumentsContract.buildDocumentUriUsingTree(grantUri, docId);
        if (DEBUG) Log.d(TAG, "generateGrantUri, grantUri="+grantUri);
        return grantUri;
    }

    /**
     * Creates a filename (where the file should be saved) from info about a download.
     * This file will be touched to reserve it.
     */
    static String generateSaveFile(Context context, String url, String hint,
            String contentDisposition, String contentLocation, String mimeType, int destination)
            throws IOException {

        final File parent;
        final File[] parentTest;
        String name = null;

        if (destination == Downloads.Impl.DESTINATION_FILE_URI) {
            final File file = new File(Uri.parse(hint).getPath());
            parent = file.getParentFile().getAbsoluteFile();
            parentTest = new File[] { parent };
            name = file.getName();
        } else {
            parent = getRunningDestinationDirectory(context, destination);
            parentTest = new File[] {
                    parent,
                    getSuccessDestinationDirectory(context, destination)
            };
            name = chooseFilename(url, hint, contentDisposition, contentLocation);
        }
        if (DEBUG) Log.d(TAG, "generateSaveFile, parent="+parent+"  name="+name);

        // Ensure target directories are ready
        if (!Helpers.isWritePermissionNeeded(parent.toString())) {
            for (File test : parentTest) {
                if (!(test.isDirectory() || test.mkdirs())) {
                    throw new IOException("Failed to create parent for " + test);
                }
            }
        }

        /*
         * for downloadprovider_DRM
         * original code
         if (DownloadDrmHelper.isDrmConvertNeeded(mimeType)) {
         *@{
         */
        if (DownloadDrmHelper.isDrmConvertNeeded(mimeType) && (!DownloadsDRMUtils.getInstance(context).isSupportDRM())) {
        /*@}*/
            name = DownloadDrmHelper.modifyDrmFwLockFileExtension(name);
        }

        final String prefix;
        final String suffix;
        final int dotIndex = name.lastIndexOf('.');
        final boolean missingExtension = dotIndex < 0;
        if (destination == Downloads.Impl.DESTINATION_FILE_URI) {
            // Destination is explicitly set - do not change the extension
            if (missingExtension) {
                prefix = name;
                suffix = "";
            } else {
                prefix = name.substring(0, dotIndex);
                suffix = name.substring(dotIndex);
            }
        } else {
            // Split filename between base and extension
            // Add an extension if filename does not have one
            if (missingExtension) {
                prefix = name;
                suffix = chooseExtensionFromMimeType(mimeType, true);
            } else {
                prefix = name.substring(0, dotIndex);
                suffix = chooseExtensionFromFilename(mimeType, destination, name, dotIndex);
            }
        }

        synchronized (sUniqueLock) {
            /*
             * for downloadprovider_DRM
             * original code
             name = generateAvailableFilenameLocked(parentTest, prefix, suffix);
             *@{
             */
            name = generateAvailableFilenameLocked(context, parentTest, prefix, suffix);
            /*@}*/

            // Claim this filename inside lock to prevent other threads from
            // clobbering us. We're not paranoid enough to use O_EXCL.
            final File file = new File(parent, name);
            if (Helpers.isWritePermissionNeeded(parent.toString())) {
                final Uri grantUri = generateGrantUri(parent.toString());
                final Uri pathUri = DocumentsContract.createDocument(
                        context.getContentResolver(), grantUri, mimeType, name);
            } else {
                file.createNewFile();
            }
            return file.getAbsolutePath();
        }
    }

    private static String chooseFilename(String url, String hint, String contentDisposition,
            String contentLocation) {
        String filename = null;

        // First, try to use the hint from the application, if there's one
        if (filename == null && hint != null && !hint.endsWith("/")) {
            if (Constants.LOGVV) {
                Log.v(Constants.TAG, "getting filename from hint");
            }
            int index = hint.lastIndexOf('/') + 1;
            if (index > 0) {
                filename = hint.substring(index);
            } else {
                filename = hint;
            }
        }

        // If we couldn't do anything with the hint, move toward the content disposition
        if (filename == null && contentDisposition != null) {
            filename = parseContentDisposition(contentDisposition);
            if (filename != null) {
                if (Constants.LOGVV) {
                    Log.v(Constants.TAG, "getting filename from content-disposition");
                }
                int index = filename.lastIndexOf('/') + 1;
                if (index > 0) {
                    filename = filename.substring(index);
                }
            }
        }

        // If we still have nothing at this point, try the content location
        if (filename == null && contentLocation != null) {
            String decodedContentLocation = Uri.decode(contentLocation);
            if (decodedContentLocation != null
                    && !decodedContentLocation.endsWith("/")
                    && decodedContentLocation.indexOf('?') < 0) {
                if (Constants.LOGVV) {
                    Log.v(Constants.TAG, "getting filename from content-location");
                }
                int index = decodedContentLocation.lastIndexOf('/') + 1;
                if (index > 0) {
                    filename = decodedContentLocation.substring(index);
                } else {
                    filename = decodedContentLocation;
                }
            }
        }

        // If all the other http-related approaches failed, use the plain uri
        if (filename == null) {
            String decodedUrl = Uri.decode(url);
            if (decodedUrl != null
                    && !decodedUrl.endsWith("/") && decodedUrl.indexOf('?') < 0) {
                int index = decodedUrl.lastIndexOf('/') + 1;
                if (index > 0) {
                    if (Constants.LOGVV) {
                        Log.v(Constants.TAG, "getting filename from uri");
                    }
                    filename = decodedUrl.substring(index);
                }
            }
        }

        // Finally, if couldn't get filename from URI, get a generic filename
        if (filename == null) {
            if (Constants.LOGVV) {
                Log.v(Constants.TAG, "using default filename");
            }
            filename = Constants.DEFAULT_DL_FILENAME;
        }

        // The VFAT file system is assumed as target for downloads.
        // Replace invalid characters according to the specifications of VFAT.
        filename = FileUtils.buildValidFatFilename(filename);

        return filename;
    }

    private static String chooseExtensionFromMimeType(String mimeType, boolean useDefaults) {
        String extension = null;
        if (mimeType != null) {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (extension != null) {
                if (Constants.LOGVV) {
                    Log.v(Constants.TAG, "adding extension from type");
                }
                extension = "." + extension;
            } else {
                if (Constants.LOGVV) {
                    Log.v(Constants.TAG, "couldn't find extension for " + mimeType);
                }
            }
        }
        if (extension == null) {
            if (mimeType != null && mimeType.toLowerCase().startsWith("text/")) {
                if (mimeType.equalsIgnoreCase("text/html")) {
                    if (Constants.LOGVV) {
                        Log.v(Constants.TAG, "adding default html extension");
                    }
                    extension = Constants.DEFAULT_DL_HTML_EXTENSION;
                } else if (useDefaults) {
                    if (Constants.LOGVV) {
                        Log.v(Constants.TAG, "adding default text extension");
                    }
                    extension = Constants.DEFAULT_DL_TEXT_EXTENSION;
                }
            } else if (useDefaults) {
                if (Constants.LOGVV) {
                    Log.v(Constants.TAG, "adding default binary extension");
                }
                extension = Constants.DEFAULT_DL_BINARY_EXTENSION;
            }
        }
        return extension;
    }

    private static String chooseExtensionFromFilename(String mimeType, int destination,
            String filename, int lastDotIndex) {
        String extension = null;
        if (mimeType != null) {
            // Compare the last segment of the extension against the mime type.
            // If there's a mismatch, discard the entire extension.
            String typeFromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    filename.substring(lastDotIndex + 1));
            if (typeFromExt == null || !typeFromExt.equalsIgnoreCase(mimeType)) {
                extension = chooseExtensionFromMimeType(mimeType, false);
                if (extension != null) {
                    if (Constants.LOGVV) {
                        Log.v(Constants.TAG, "substituting extension from type");
                    }
                } else {
                    if (Constants.LOGVV) {
                        Log.v(Constants.TAG, "couldn't find extension for " + mimeType);
                    }
                }
            }
        }
        if (extension == null) {
            if (Constants.LOGVV) {
                Log.v(Constants.TAG, "keeping extension");
            }
            extension = filename.substring(lastDotIndex);
        }
        return extension;
    }

    private static boolean isFilenameAvailableLocked(File[] parents, String name) {
        if (Constants.RECOVERY_DIRECTORY.equalsIgnoreCase(name)) return false;

        for (File parent : parents) {
            if (new File(parent, name).exists()) {
                return false;
            }
        }

        return true;
    }

    /*
     * for downloadprovider_DRM
     * original code
     private static String generateAvailableFilenameLocked(
     *@{
     */
    private static String generateAvailableFilenameLocked(Context context,
    /*@}*/
            File[] parents, String prefix, String suffix) throws IOException {
        String name = prefix + suffix;

        /*
         * for downloadprovider_DRM
         *@{
         */
        name = DownloadsDRMUtils.getInstance(context).getDRMFileName(prefix, suffix);
        /*@}*/

        if (isFilenameAvailableLocked(parents, name)) {
            return name;
        }

        /*
        * This number is used to generate partially randomized filenames to avoid
        * collisions.
        * It starts at 1.
        * The next 9 iterations increment it by 1 at a time (up to 10).
        * The next 9 iterations increment it by 1 to 10 (random) at a time.
        * The next 9 iterations increment it by 1 to 100 (random) at a time.
        * ... Up to the point where it increases by 100000000 at a time.
        * (the maximum value that can be reached is 1000000000)
        * As soon as a number is reached that generates a filename that doesn't exist,
        *     that filename is used.
        * If the filename coming in is [base].[ext], the generated filenames are
        *     [base]-[sequence].[ext].
        */
        int sequence = 1;
        for (int magnitude = 1; magnitude < 1000000000; magnitude *= 10) {
            for (int iteration = 0; iteration < 9; ++iteration) {
                name = prefix + Constants.FILENAME_SEQUENCE_SEPARATOR + sequence + suffix;
                /*
                 * for downloadprovider_DRM
                 *@{
                 */
                name = DownloadsDRMUtils.getInstance(context).getDRMFileName(prefix + Constants.FILENAME_SEQUENCE_SEPARATOR + sequence, suffix);
                /*@}*/
                if (isFilenameAvailableLocked(parents, name)) {
                    return name;
                }
                sequence += sRandom.nextInt(magnitude) + 1;
            }
        }

        throw new IOException("Failed to generate an available filename");
    }

    static boolean isFilenameValid(Context context, File file) {
        return isFilenameValid(context, file, true);
    }

    static boolean isFilenameValidInExternal(Context context, File file) {
        return isFilenameValid(context, file, false);
    }

    /**
     * Test if given file exists in one of the package-specific external storage
     * directories that are always writable to apps, regardless of storage
     * permission.
     */
    static boolean isFilenameValidInExternalPackage(Context context, File file,
            String packageName) {
        try {
            if (containsCanonical(buildExternalStorageAppFilesDirs(packageName), file) ||
                    containsCanonical(buildExternalStorageAppObbDirs(packageName), file) ||
                    containsCanonical(buildExternalStorageAppCacheDirs(packageName), file) ||
                    containsCanonical(buildExternalStorageAppMediaDirs(packageName), file)) {
                return true;
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to resolve canonical path: " + e);
            return false;
        }

        Log.w(TAG, "Path appears to be invalid: " + file);
        return false;
    }

    /**
     * Checks whether the filename looks legitimate for security purposes. This
     * prevents us from opening files that aren't actually downloads.
     */
    static boolean isFilenameValid(Context context, File file, boolean allowInternal) {
        try {
            if (allowInternal) {
                if (containsCanonical(context.getFilesDir(), file)
                        || containsCanonical(context.getCacheDir(), file)
                        || containsCanonical(Environment.getDownloadCacheDirectory(), file)) {
                    return true;
                }
            }

            final StorageVolume[] volumes = StorageManager.getVolumeList(UserHandle.myUserId(),
                    StorageManager.FLAG_FOR_WRITE);
            for (StorageVolume volume : volumes) {
                if (containsCanonical(volume.getPathFile(), file)) {
                    return true;
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to resolve canonical path: " + e);
            return false;
        }

        Log.w(TAG, "Path appears to be invalid: " + file);
        return false;
    }

    private static boolean containsCanonical(File dir, File file) throws IOException {
        return FileUtils.contains(dir.getCanonicalFile(), file);
    }

    private static boolean containsCanonical(File[] dirs, File file) throws IOException {
        for (File dir : dirs) {
            if (containsCanonical(dir, file)) {
                return true;
            }
        }
        return false;
    }

    public static File getRunningDestinationDirectory(Context context, int destination)
            throws IOException {
        return getDestinationDirectory(context, destination, true);
    }

    public static File getSuccessDestinationDirectory(Context context, int destination)
            throws IOException {
        return getDestinationDirectory(context, destination, false);
    }

    private static File getDestinationDirectory(Context context, int destination, boolean running)
            throws IOException {
        switch (destination) {
            case Downloads.Impl.DESTINATION_CACHE_PARTITION:
            case Downloads.Impl.DESTINATION_CACHE_PARTITION_PURGEABLE:
            case Downloads.Impl.DESTINATION_CACHE_PARTITION_NOROAMING:
                if (running) {
                    return context.getFilesDir();
                } else {
                    return context.getCacheDir();
                }

            case Downloads.Impl.DESTINATION_EXTERNAL:
                final File target = new File(
                        Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS);
                if (!target.isDirectory() && target.mkdirs()) {
                    throw new IOException("unable to create external downloads directory");
                }
                return target;

            default:
                throw new IllegalStateException("unexpected destination: " + destination);
        }
    }
}
