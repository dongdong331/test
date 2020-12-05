/**
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
 * limitations under the License.
 */

package com.android.server.face;

import android.content.Context;
import android.hardware.face.Face;
import android.os.Vibrator;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import java.util.List;

/**
 * Utility class for dealing with faces and face settings.
 */
public class FaceUtils {

    private static final long[] FACE_ERROR_VIBRATE_PATTERN = new long[] {0, 30, 100, 30};
    private static final long[] FACE_SUCCESS_VIBRATE_PATTERN = new long[] {0, 30};

    private static final Object sInstanceLock = new Object();
    private static FaceUtils sInstance;

    @GuardedBy("this")
    private final SparseArray<FacesUserState> mUsers = new SparseArray<>();

    public static FaceUtils getInstance() {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                sInstance = new FaceUtils();
            }
        }
        return sInstance;
    }

    private FaceUtils() {
    }

    public List<Face> getFacesForUser(Context ctx, int userId) {
        return getStateForUser(ctx, userId).getFaces();
    }

    public void addFaceForUser(Context ctx, int faceId, int userId) {
        getStateForUser(ctx, userId).addFace(faceId, userId);
    }

    public void removeFaceIdForUser(Context ctx, int faceId, int userId) {
        getStateForUser(ctx, userId).removeFace(faceId);
    }

    public void renameFaceForUser(Context ctx, int faceId, int userId, CharSequence name) {
        getStateForUser(ctx, userId).renameFace(faceId, name);
    }

    public static void vibrateFaceError(Context context) {
        Vibrator vibrator = context.getSystemService(Vibrator.class);
        if (vibrator != null) {
            vibrator.vibrate(FACE_ERROR_VIBRATE_PATTERN, -1);
        }
    }

    public static void vibrateFaceSuccess(Context context) {
        Vibrator vibrator = context.getSystemService(Vibrator.class);
        if (vibrator != null) {
            vibrator.vibrate(FACE_SUCCESS_VIBRATE_PATTERN, -1);
        }
    }

    private FacesUserState getStateForUser(Context ctx, int userId) {
        synchronized (this) {
            FacesUserState state = mUsers.get(userId);
            if (state == null) {
                state = new FacesUserState(ctx, userId);
                mUsers.put(userId, state);
            }
            return state;
        }
    }
}

