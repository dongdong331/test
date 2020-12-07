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

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.View;
import android.util.Log;
import android.hardware.face.FaceManager;

import com.android.settings.password.ChooseLockGeneric;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.android.settings.R;
import com.android.settings.Utils;

/**
 * Onboarding activity for faceid enrollment.
 */
public class FaceIdEnrollIntroduction extends FaceIdEnrollBase {
    private static final String TAG = "FaceIdEnrollIntroduction";

    protected static final int CHOOSE_LOCK_GENERIC_REQUEST = 1;
    protected static final int FACEID_FIND_SENSOR_REQUEST = 2;
    private boolean mHasPassword;
    private UserManager mUserManager;
    //add by telefk
    private boolean mIsfaceidIdSettings = false;
    private FaceManager mFaceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.faceid_enroll_introduction);
        setHeaderText(R.string.security_settings_faceid_enroll_introduction_title);
        findViewById(R.id.cancel_button).setOnClickListener(this);
        //findViewById(R.id.learn_more_button).setOnClickListener(this);
        final int passwordQuality = new ChooseLockSettingsHelper(this).utils()
                .getActivePasswordQuality(UserHandle.myUserId());
        mHasPassword = passwordQuality != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
        mUserManager = UserManager.get(this);
        mFaceManager = (FaceManager) getSystemService(Context.FACE_SERVICE);
        updatePasswordQuality();
    }

    @Override
    protected void onNextButtonClick() {
        Intent intent;
        Log.d(TAG, "mHasPassword = " + mHasPassword);
        if (!mHasPassword) {
            // No faceid registered, launch into enrollment wizard.
            launchChooseLock();
        } else {
            // Lock thingy is already set up, launch directly into find sensor step from wizard.
            launchFindSensor(null);
        }
    }

    private void launchChooseLock() {
        Intent intent = getChooseLockIntent();
        long challenge = mFaceManager.preEnroll();
        intent.putExtra(ChooseLockGeneric.ChooseLockGenericFragment.MINIMUM_QUALITY_KEY,
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        intent.putExtra(ChooseLockGeneric.ChooseLockGenericFragment.HIDE_DISABLED_PREFS, true);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, true);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, challenge);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_FOR_FACE, true);
        if (mUserId != UserHandle.USER_NULL) {
            intent.putExtra(Intent.EXTRA_USER_ID, mUserId);
        }
        startActivityForResult(intent, CHOOSE_LOCK_GENERIC_REQUEST);
    }

    private void launchFindSensor(byte[] token) {
        Intent intent = getFindSensorIntent();
        if (token != null) {
            intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, token);
        }
        if (mUserId != UserHandle.USER_NULL) {
            intent.putExtra(Intent.EXTRA_USER_ID, mUserId);
        }
        startActivityForResult(intent, FACEID_FIND_SENSOR_REQUEST);
    }

    protected Intent getChooseLockIntent() {
        return new Intent(this, ChooseLockGeneric.class);
    }

    protected Intent getFindSensorIntent() {
        return new Intent(this, FaceIdEnrollFindSensor.class);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        final boolean isResultFinished = resultCode == RESULT_FINISHED;
        if (requestCode == FACEID_FIND_SENSOR_REQUEST) {
            if (isResultFinished || resultCode == RESULT_SKIP) {
                final int result = isResultFinished ? RESULT_OK : RESULT_SKIP;
                setResult(result, data);
                finish();
                return;
            }
        } else if (requestCode == CHOOSE_LOCK_GENERIC_REQUEST) {
            if (isResultFinished) {
                updatePasswordQuality();
                byte[] token = data.getByteArrayExtra(
                        ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
                launchFindSensor(token);
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void updatePasswordQuality() {
        final int passwordQuality = new ChooseLockSettingsHelper(this).utils()
                .getActivePasswordQuality(mUserManager.getCredentialOwnerProfile(mUserId));
        mHasPassword = passwordQuality != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.cancel_button) {
            finish();
        }
        super.onClick(v);
    }
}
