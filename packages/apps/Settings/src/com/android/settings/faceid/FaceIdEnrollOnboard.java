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
import android.hardware.face.FaceManager;
import android.os.Bundle;
import android.util.Log;

import com.android.settings.password.ChooseLockGeneric;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.android.settings.R;

/**
 * Onboarding activity for faceid enrollment.
 */
public class FaceIdEnrollOnboard extends FaceIdEnrollBase {

    private static final int CHOOSE_LOCK_GENERIC_REQUEST = 1;
    private static final String TAG = "FaceIdEnrollOnboard";
    private FaceManager mFaceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.faceid_enroll_onboard);
        setHeaderText(R.string.security_settings_faceid_enroll_onboard_title);
        mFaceManager = (FaceManager) getSystemService(Context.FACE_SERVICE);
    }

    @Override
    protected void onNextButtonClick() {
        Log.d(TAG, "telefk onNextButtonClick " );
        launchChooseLock();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CHOOSE_LOCK_GENERIC_REQUEST && resultCode == RESULT_FINISHED) {
            byte[] token = data.getByteArrayExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
             Log.d(TAG, "telefk token = " + token);
            setResult(RESULT_FINISHED);
            launchFindSensor(token);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
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
        startActivityForResult(intent, CHOOSE_LOCK_GENERIC_REQUEST);
    }

    protected Intent getChooseLockIntent() {
        return new Intent(this, ChooseLockGeneric.class);
    }

    private void launchFindSensor(byte[] token) {
        Intent intent = getFindSensorIntent();
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, token);
        startActivity(intent);
        finish();
    }

    protected Intent getFindSensorIntent() {
        return new Intent(this, FaceIdEnrollFindSensor.class);
    }
}
