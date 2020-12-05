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

import android.hardware.face.Face;
import android.os.Bundle;
import android.os.UserHandle;

/**
 * Communication channel from the FaceService back to FaceManager.
 * @hide
 */
oneway interface IFaceServiceReceiver {
    void onEnrollResult(long deviceId, int faceId, int groupId, int progress);
    void onHelp(long deviceId, int help);
    void onAuthenticationSucceeded(long deviceId, in Face face);
    void onAuthenticationFailed(long deviceId);
    void onError(long deviceId, int error);
    void onRemoved(long deviceId, int faceId, int groupId);
    void onEnrollFreeBuffer(long deviceId, long addr);
    void onAuthFreeBuffer(long deviceId, long main, long sub);
    void onAlgoStartedResult(long deviceId, int result);
}
