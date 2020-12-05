/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.hardware.face;

import android.view.Surface;
import android.os.Bundle;
import android.hardware.face.IFaceServiceLockoutResetCallback;
import android.hardware.face.IFaceServiceReceiver;
import android.hardware.face.Face;
import java.util.List;

/**
 * Communication channel from client to the face service.
 * @hide
 */
interface IFaceService {
    // Authenticate the given sessionId with a face
    void authenticate(IBinder token, long sessionId, int userId,
            IFaceServiceReceiver receiver, int width, int height, int flags, String opPackageName);

    // Cancel authentication for the given sessionId
    void cancelAuthentication(IBinder token, String opPackageName);

    // Start face enrollment
    void enroll(IBinder token, in byte [] cryptoToken, int groupId, IFaceServiceReceiver receiver,
            int flags, int width, int height);

    // Cancel enrollment in progress
    void cancelEnrollment(IBinder token);

    // Any errors resulting from this call will be returned to the listener
    void remove(IBinder token, int faceId, int groupId, IFaceServiceReceiver receiver);

    // Rename the face specified by faceId and groupId to the given name
    void rename(int faceId, int groupId, String name);

    // Get a list of enrolled Faces in the given group.
    List<Face> getEnrolledFaces(int groupId, String opPackageName);

    // Determine if HAL is loaded and ready
    boolean isHardwareDetected(long deviceId, String opPackageName);

    // Get a pre-enrollment authentication token
    long preEnroll(IBinder token);

    // Finish an enrollment sequence and invalidate the authentication token
    int postEnroll(IBinder token);

    // Determine if a user has at least one enrolled face
    boolean hasEnrolledFaces(int groupId, String opPackageName);

    // Gets the number of hardware devices
    // int getHardwareDeviceCount();

    // Gets the unique device id for hardware enumerated at i
    // long getHardwareDevice(int i);

    // Gets the authenticator ID for face
    long getAuthenticatorId(String opPackageName);

    // Inform enroll face buffer
    void informEnrollFaceBuffer(long addr, in int[] info, in byte[] byteInfo);

    // Inform authenticate face buffer
    void informAuthFaceBuffer(long main, long sub, long otp, in int[] info, in byte[] byteInfo);

    // get lockout left time(s)
    int getLockoutLeftTime();

    // reset lockout counter
    void resetLockout(in byte [] token);

    // Add a callback which gets notified when the face lockout period expired.
    void addLockoutResetCallback(IFaceServiceLockoutResetCallback callback);
}
