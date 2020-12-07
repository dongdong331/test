/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.provider.Downloads.Impl.ALL_DOWNLOADS_CONTENT_URI;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.database.ContentObserver;
import android.util.Log;
import android.util.SparseArray;
import android.app.DownloadManager;

/**
 * Service that hosts download jobs. Each active download job is handled as a
 * unique {@link DownloadThread} instance.
 * <p>
 * The majority of downloads should have ETag values to enable resuming, so if a
 * given download isn't able to finish in the normal job timeout (10 minutes),
 * we just reschedule the job and resume again in the future.
 */
public class DownloadJobService extends JobService {
    private static final String TAG = "DownloadManager:DownloadJobService";
    private static final boolean DEBUG = true;

    // @GuardedBy("mActiveThreads")
    private SparseArray<DownloadThread> mActiveThreads = new SparseArray<>();

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate");
        super.onCreate();

        // While someone is bound to us, watch for database changes that should
        // trigger notification updates.
        getContentResolver().registerContentObserver(ALL_DOWNLOADS_CONTENT_URI, true, mObserver);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy");
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mObserver);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        final int id = params.getJobId();

        // Spin up thread to handle this download
        final DownloadInfo info = DownloadInfo.queryDownloadInfo(this, id);
        if (info == null) {
            Log.w(TAG, "Odd, no details found for download " + id);
            return false;
        }

        if (DEBUG) Log.d(TAG, "onStartJob, id="+id);

        final DownloadThread thread;
        synchronized (mActiveThreads) {
            if (mActiveThreads.indexOfKey(id) >= 0) {
                if (DEBUG) Log.d(TAG, "Odd, already running download " + id);
                return false;
            }
            thread = new DownloadThread(this, params, info);
            mActiveThreads.put(id, thread);
        }
        thread.start();

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        final int id = params.getJobId();
        if (DEBUG) Log.d(TAG, "onStopJob, id="+id);

        final DownloadThread thread;
        synchronized (mActiveThreads) {
            thread = mActiveThreads.removeReturnOld(id);
        }
        if (thread != null) {
            // If the thread is still running, ask it to gracefully shutdown,
            // and reschedule ourselves to resume in the future.
            thread.requestShutdown();

            if (DEBUG) Log.d(TAG, "onStopJob, id="+id+"  mStatus="+thread.mInfoDelta.mStatus);
            if (DEBUG) Log.d(TAG, "onStopJob, id="+id+"  mToNetConfirm="+thread.mToNetConfirm);
            boolean reschedule = params.getReschedule();
            if (DEBUG) Log.d(TAG, "onStopJob, id="+id+"  params.reschedule="+reschedule);
            if(reschedule && !thread.mToNetConfirm){
                if (DEBUG) Log.d(TAG, "onStopJob, to scheduleJob");
                Helpers.scheduleJob(this, DownloadInfo.queryDownloadInfo(this, id));
            }
        }
        return false;
    }

    public void jobFinishedInternal(JobParameters params, boolean needsReschedule) {
        final int id = params.getJobId();
        if (DEBUG) Log.d(TAG, "jobFinishedInternal, id="+id);

        synchronized (mActiveThreads) {
            mActiveThreads.remove(params.getJobId());
        }
        if (needsReschedule) {
            Helpers.scheduleJob(this, DownloadInfo.queryDownloadInfo(this, id));
        }

        // Update notifications one last time while job is protecting us
        mObserver.onChange(false);

        // We do our own rescheduling above
        jobFinished(params, false);
    }

    private ContentObserver mObserver = new ContentObserver(Helpers.getAsyncHandler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (DEBUG) Log.d(TAG, "onChange, to update DownloadNotifier");
            Helpers.getDownloadNotifier(DownloadJobService.this).update();
        }
    };
}
