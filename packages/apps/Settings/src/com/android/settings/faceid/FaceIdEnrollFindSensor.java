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

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.hardware.face.FaceManager;

import com.android.settings.password.ChooseLockSettingsHelper;
import com.android.settings.R;
import com.android.settings.Utils;


/**
 * Activity notifying user look at the faceid camera for faceid enrollment.
 */
public class FaceIdEnrollFindSensor extends FaceIdEnrollBase {
    private static final String TAG = "FaceIdEnrollFindSensor";

    public static final String EXTRA_KEY_LAUNCHED_CONFIRM = "launched_confirm_lock";
    private static final int CONFIRM_REQUEST = 1;
    private static final int ENROLLING = 2;
    private static final int FACEID_MAX_TEMPLATES_PER_USER = 5;
    private boolean mLaunchedConfirmLock;
    private FaceManager mFaceManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.faceid_enroll_find_sensor);
        setHeaderText(R.string.security_settings_faceid_enroll_add_faceid_title);
        if (savedInstanceState != null) {
            mLaunchedConfirmLock = savedInstanceState.getBoolean(EXTRA_KEY_LAUNCHED_CONFIRM);
            mToken = savedInstanceState.getByteArray(
                    ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
        }
        mFaceManager = (FaceManager) getSystemService(Context.FACE_SERVICE);
        if (mToken == null && !mLaunchedConfirmLock) {
            launchConfirmLock();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_KEY_LAUNCHED_CONFIRM, mLaunchedConfirmLock);
        outState.putByteArray(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, mToken);
    }

    @Override
    protected void onNextButtonClick() {
        startActivityForResult(getEnrollingIntent(), ENROLLING);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CONFIRM_REQUEST) {
            if (resultCode == RESULT_OK) {
                mToken = data.getByteArrayExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
                Log.d(TAG, "telefk mToken = " + mToken);
                overridePendingTransition(R.anim.suw_slide_next_in, R.anim.suw_slide_next_out);
            } else {
                finish();
            }
        } else if (requestCode == ENROLLING) {
            if (resultCode == RESULT_FINISHED) {
                setResult(RESULT_FINISHED);
                finish();
            } else {
                setResult(RESULT_SKIP);
                finish();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void launchConfirmLock() {
        long challenge = mFaceManager.preEnroll();
        ChooseLockSettingsHelper helper = new ChooseLockSettingsHelper(this);
        boolean launchedConfirmationActivity = false;
        if (mUserId == UserHandle.USER_NULL) {
            launchedConfirmationActivity = helper.launchConfirmationActivity(CONFIRM_REQUEST,
                getString(R.string.security_settings_fingerprint_preference_title),
                null, null, challenge);
        } else {
            launchedConfirmationActivity = helper.launchConfirmationActivity(CONFIRM_REQUEST,
                    getString(R.string.security_settings_fingerprint_preference_title),
                    null, null, challenge, mUserId);
        }
        if (!launchedConfirmationActivity) {
            // This shouldn't happen, as we should only end up at this step if a lock thingy is
            // already set.
            finish();
        } else {
            mLaunchedConfirmLock = true;
        }
    }
}
