/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.faceid;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.face.Face;
import android.hardware.face.FaceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.v7.preference.Preference;
import android.util.Log;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.overlay.FeatureFactory;

import java.util.List;

public class FaceLockPreferenceController extends BasePreferenceController {
    private static final String TAG = "FaceLockPreferenceController";

    private static final String KEY_FACELOCK_SETTINGS = "facelock_settings";

    protected final FaceManager mFaceManager;
    protected final UserManager mUm;
    protected final LockPatternUtils mLockPatternUtils;
    protected DevicePolicyManager mDevicePolicyManager;

    protected final int mUserId = UserHandle.myUserId();
    protected final int mProfileChallengeUserId;

    public FaceLockPreferenceController(Context context) {
        this(context, KEY_FACELOCK_SETTINGS);
    }

    public FaceLockPreferenceController(Context context, String key) {
        super(context, key);
        mFaceManager = (FaceManager) context.getSystemService(Context.FACE_SERVICE);
        mUm = (UserManager) context.getSystemService(Context.USER_SERVICE);
        mLockPatternUtils = FeatureFactory.getFactory(context)
                .getSecurityFeatureProvider()
                .getLockPatternUtils(context);
        mProfileChallengeUserId = Utils.getManagedProfileId(mUm, mUserId);
        mDevicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    @Override
    public int getAvailabilityStatus() {
        if (mFaceManager == null || !mFaceManager.isHardwareDetected()) {
            Log.d(TAG, "No face lock hardware detected!!");
            return UNSUPPORTED_ON_DEVICE;
        }
        if (mDevicePolicyManager != null
                && (mDevicePolicyManager.getKeyguardDisabledFeatures(null, mUserId)
                        & DevicePolicyManager.KEYGUARD_DISABLE_FACE) != 0) {
            Log.d(TAG, "Face lock dpm keyguard disabled!");
            return UNSUPPORTED_ON_DEVICE;
        }
        if (isUserSupported()) {
            return AVAILABLE;
        } else {
            return DISABLED_FOR_USER;
        }
    }

    @Override
    public void updateState(Preference preference) {
        if (!isAvailable()) {
            if (preference != null) {
                preference.setVisible(false);
            }
            return;
        } else {
            preference.setVisible(true);
        }
        final int userId = getUserId();
        final List<Face> items = mFaceManager == null ? null : mFaceManager.getEnrolledFaces();
        final int faceidCount = items != null ? items.size() : 0;
        final String clazz;
        if (faceidCount > 0) {
            preference.setSummary(R.string.security_settings_faceid_preference_summary);
            clazz = FaceIdSettings.class.getName();
        } else {
            preference.setSummary(
                    R.string.security_settings_fingerprint_preference_summary_none);
            clazz = FaceIdEnrollIntroduction.class.getName();
        }
        preference.setOnPreferenceClickListener(target -> {
            final Context context = target.getContext();
            final UserManager userManager = UserManager.get(context);
            if (Utils.startQuietModeDialogIfNecessary(context, userManager,
                    userId)) {
                return false;
            }
            Intent intent = new Intent();
            intent.setClassName("com.android.settings", clazz);
            intent.putExtra(Intent.EXTRA_USER_ID, userId);
            context.startActivity(intent);
            return true;
        });
    }

    protected int getUserId() {
        return mUserId;
    }

    protected boolean isUserSupported() {
        return true;
    }
}
