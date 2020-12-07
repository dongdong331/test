/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.settings.faceid;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.settings.password.ChooseLockSettingsHelper;

/**
 * Sidecar fragment to handle the state around faceid enrollment.
 */
public class FaceIdEnrollSidecar extends Fragment {

    private int mEnrollmentSteps = -1;
    private int mEnrollmentProgress = 0;
    private Listener mListener;
    private boolean mEnrolling;
    private CancellationSignal mEnrollmentCancel;
    private Handler mHandler = new Handler();
    private HandlerThread mPreviewThread;
    private Handler mPreviewHandler;
    private byte[] mToken;
    private boolean mDone;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        if (mPreviewHandler == null || mPreviewThread == null) {
            mPreviewThread = new HandlerThread("Preview Thread");
            mPreviewThread.start();
            mPreviewHandler = new PreviewHandler(mPreviewThread.getLooper());
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mToken = activity.getIntent().getByteArrayExtra(
                ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mEnrolling) {
            startEnrollment();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!getActivity().isChangingConfigurations()) {
            cancelEnrollment();
        }
    }

    private void startEnrollment() {
        mHandler.removeCallbacks(mTimeoutRunnable);
        mEnrollmentSteps = -1;
        mEnrollmentCancel = new CancellationSignal();
        //IrisManager irisManger = (IrisManager) getActivity().getSystemService(Context.IRIS_SERVICE);
        Log.d("telefk "," IrisEnrollSidecar    startEnrollment    mToken = " + mToken);
        //irisManger.enroll(mToken, mEnrollmentCancel,0 /* flags */, mEnrollmentCallback, mPreviewHandler);
        mEnrolling = true;
    }

    private void cancelEnrollment() {
        mHandler.removeCallbacks(mTimeoutRunnable);
        if (mEnrolling) {
            mEnrollmentCancel.cancel();
            mEnrolling = false;
            mEnrollmentSteps = -1;
        }
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public int getEnrollmentSteps() {
        return mEnrollmentSteps;
    }

    public int getEnrollmentProgress() {
        return mEnrollmentProgress;
    }

    public boolean isDone() {
        return mDone;
    }

    /*private IrisManager.EnrollmentCallback mEnrollmentCallback
            = new IrisManager.EnrollmentCallback() {

        @Override
        public void onEnrollmentProgress(int progress) {
            if (mEnrollmentSteps == -1) {
                mEnrollmentSteps = progress;
            }
            mEnrollmentProgress = progress;
            mDone = progress == 0;
            if (mListener != null) {
                mListener.onEnrollmentProgressChange(mEnrollmentSteps, progress);
            }
        }

        @Override
        public void onEnrollmentHelp(int helpMsgId, CharSequence helpString) {
            if (mListener != null) {
                mListener.onEnrollmentHelp(helpString);
            }
        }

        @Override
        public void onEnrollmentError(int errMsgId, CharSequence errString) {
            if (mListener != null) {
                mListener.onEnrollmentError(errString);
            }
        }

        @Override
        public void onPreviewFrame(byte[] data) {
            if (mListener != null) {
                mListener.onEnrollmentPreviewFrame(data);
            }
        }
    };*/

    private final Runnable mTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            cancelEnrollment();
        }
    };

    private class PreviewHandler extends Handler {

        public PreviewHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
        }
    }

    public interface Listener {
        void onEnrollmentHelp(int helpCode, CharSequence helpString);
        void onEnrollmentError(int errorCode, CharSequence errString);
        void onEnrollmentProgressChange(int steps, int progress);
        void onEnrollmentPreviewFrame(byte[] data);
    }
}
