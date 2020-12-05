/**
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.ActivityManagerNative;
import android.content.Context;
import android.os.Binder;
import android.os.CancellationSignal;
import android.os.CancellationSignal.OnCancelListener;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.security.keystore.AndroidKeyStoreProvider;
import android.util.Log;
import android.util.Slog;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.graphics.ImageFormat;

import java.security.Signature;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.Mac;

import static android.Manifest.permission.USE_FACE;
import static android.Manifest.permission.MANAGE_FACE;

/**
 * A class that coordinates access to the face hardware.
 * <p>
 * Use {@link android.content.Context#getSystemService(java.lang.String)}
 * with argument {@link android.content.Context#FACE_SERVICE} to get
 * an instance of this class.
 */

public class FaceManager {
    private static final String TAG = "FaceManager";
    private static final boolean DEBUG = true;
    private static final int MSG_ENROLL_RESULT = 100;
    private static final int MSG_HELP = 101;
    private static final int MSG_AUTHENTICATION_SUCCEEDED = 102;
    private static final int MSG_AUTHENTICATION_FAILED = 103;
    private static final int MSG_ERROR = 104;
    private static final int MSG_REMOVED = 105;

    //
    // Error messages from face hardware during initilization, enrollment, authentication or
    // removal. Must agree with the list in face.h
    //

    /**
     * The hardware is unavailable. Try again later.
     */
    public static final int FACE_ERROR_HW_UNAVAILABLE = 1;

    /**
     * Error state returned when the sensor was unable to process the current image.
     */
    public static final int FACE_ERROR_UNABLE_TO_PROCESS = 2;

    /**
     * Error state returned when the current request has been running too long. This is intended to
     * prevent programs from waiting for the face sensor indefinitely. The timeout is
     * platform and sensor-specific, but is generally on the order of 30 seconds.
     */
    public static final int FACE_ERROR_TIMEOUT = 3;

    /**
     * Error state returned for operations like enrollment; the operation cannot be completed
     * because there's not enough storage remaining to complete the operation.
     */
    public static final int FACE_ERROR_NO_SPACE = 4;

    /**
     * The operation was canceled because the face sensor is unavailable. For example,
     * this may happen when the user is switched, the device is locked or another pending operation
     * prevents or disables it.
     */
    public static final int FACE_ERROR_CANCELED = 5;

    /**
     * The {@link FaceManager#remove(Face, RemovalCallback)} call failed. Typically
     * this will happen when the provided face id was incorrect.
     *
     * @hide
     */
    public static final int FACE_ERROR_UNABLE_TO_REMOVE = 6;

   /**
     * The operation was canceled because the API is locked out due to too many attempts.
     */
    public static final int FACE_ERROR_LOCKOUT = 7;

    /**
     * Hardware vendors may extend this list if there are conditions that do not fall under one of
     * the above categories. Vendors are responsible for providing error strings for these errors.
     * @hide
     */
    public static final int FACE_ERROR_VENDOR_BASE = 1000;

    /**
     * Liveness fail, faceid fail.
     * @hide
     */
    public static final int FACE_ERROR_AUTH_LIVENESSFAIL = 1001;

    /**
     * No face detected, faceid fail.
     * @hide
     */
    public static final int FACE_ERROR_NOFACE = 1002;

    /**
     * Other errors, faceid fail.
     * @hide
     */
    public static final int FACE_ERROR_AUTH_FAIL = 1003;

    /**
     * Camera unavailable, cannot use faceid.
     * @hide
     */
    public static final int FACE_ERROR_CAMERA_UNAVAILABLE = 1035;

    // Help code from face algo during face enrolling and authenticating.
    // Must agree with the list in face.h
    //

    /**
     * no eyes detected, it is unkown how to move, please look at the camera
     */
    public static final int FACE_HELP_MOVE_UNKNOWN = 0;

    /**
     * it is too right, move left
     */
    public static final int FACE_HELP_MOVE_LEFT = 1;

    /**
     * it is too left, move right
     */
    public static final int FACE_HELP_MOVE_RIGHT = 2;

    /**
     * it is too down, move up
     */
    public static final int FACE_HELP_MOVE_UP = 3;

    /**
     * it is too up, move down
     */
    public static final int FACE_HELP_MOVE_DOWN = 4;

    /**
     * it is too far away, move closer
     */
    public static final int FACE_HELP_MOVE_CLOSER = 5;

    /**
     * it is too closer, move far away
     */
    public static final int FACE_HELP_MOVE_FAR_AWAY = 6;

    private IFaceService mService;
    private Context mContext;
    private IBinder mToken = new Binder();
    private AuthenticationCallback mAuthenticationCallback;
    private EnrollmentCallback mEnrollmentCallback;
    private RemovalCallback mRemovalCallback;
    private CryptoObject mCryptoObject;
    private Face mRemovalFace;
    private Handler mHandler;
    private Handler mFaceCamHandler;
    private FaceCameraImpl mFaceCam;
    private int mWidth;
    private int mHeight;
    private Surface mSurface;
    private CancellationSignal mCancel;
    private FaceOrientationEventListener mOrientationEventListener;

    private class OnEnrollCancelListener implements OnCancelListener {
        @Override
        public void onCancel() {
            cancelEnrollment();
            stopFaceCamera();
        }
    }

    private class OnAuthenticationCancelListener implements OnCancelListener {
        private CryptoObject mCrypto;

        public OnAuthenticationCancelListener(CryptoObject crypto) {
            mCrypto = crypto;
        }

        @Override
        public void onCancel() {
            cancelAuthentication(mCrypto);
            stopFaceCamera();
        }
    }

    /**
     * A wrapper class for the crypto objects supported by FaceManager. Currently the
     * framework supports {@link Signature}, {@link Cipher} and {@link Mac} objects.
     */
    public static final class CryptoObject {

        public CryptoObject(@NonNull Signature signature) {
            mCrypto = signature;
        }

        public CryptoObject(@NonNull Cipher cipher) {
            mCrypto = cipher;
        }

        public CryptoObject(@NonNull Mac mac) {
            mCrypto = mac;
        }

        /**
         * Get {@link Signature} object.
         * @return {@link Signature} object or null if this doesn't contain one.
         */
        public Signature getSignature() {
            return mCrypto instanceof Signature ? (Signature) mCrypto : null;
        }

        /**
         * Get {@link Cipher} object.
         * @return {@link Cipher} object or null if this doesn't contain one.
         */
        public Cipher getCipher() {
            return mCrypto instanceof Cipher ? (Cipher) mCrypto : null;
        }

        /**
         * Get {@link Mac} object.
         * @return {@link Mac} object or null if this doesn't contain one.
         */
        public Mac getMac() {
            return mCrypto instanceof Mac ? (Mac) mCrypto : null;
        }

        /**
         * @hide
         * @return the opId associated with this object or 0 if none
         */
        public long getOpId() {
            return mCrypto != null ?
                    AndroidKeyStoreProvider.getKeyStoreOperationHandle(mCrypto) : 0;
        }

        private final Object mCrypto;
    };

    /**
     * Container for callback data from {@link FaceManager#authenticate(CryptoObject,
     *     CancellationSignal, int, AuthenticationCallback, int, int, Handler)}.
     *
     * @hide
     */
    public static class AuthenticationResult {
        private Face mFace;
        private CryptoObject mCryptoObject;

        /**
         * Authentication result
         *
         * @param crypto the crypto object
         * @param face the recognized face data, if allowed.
         * @hide
         */
        public AuthenticationResult(CryptoObject crypto, Face face) {
            mCryptoObject = crypto;
            mFace = face;
        }

        /**
         * Obtain the crypto object associated with this transaction
         * @return crypto object provided to {@link FaceManager#authenticate(CryptoObject,
         *     CancellationSignal, int, AuthenticationCallback, int, int, Handler)}.
         */
        public CryptoObject getCryptoObject() { return mCryptoObject; }

        /**
         * Obtain the Face associated with this operation. Applications are strongly
         * discouraged from associating specific faces with specific applications or operations.
         *
         * @hide
         */
        public Face getFace() { return mFace; }
    };

    /**
     * Callback structure provided to {@link FaceManager#authenticate(CryptoObject,
     * CancellationSignal, int, AuthenticationCallback, int, int, Handler)}. Users of {@link
     * FaceManager#authenticate(CryptoObject, CancellationSignal,
     * int, AuthenticationCallback, int, int, Handler) } must provide an implementation of this for listening to
     * face events.
     *
     * @hide
     */
    public static abstract class AuthenticationCallback {
        /**
         * Called when an unrecoverable error has been encountered and the operation is complete.
         * No further callbacks will be made on this object.
         * @param errorCode An integer identifying the error message
         * @param errString A human-readable error string that can be shown in UI
         */
        public void onAuthenticationError(int errorCode, CharSequence errString) { }

        /**
         * Called when a recoverable error has been encountered during authentication. The help
         * string is provided to give the user guidance for what went wrong.
         * @param helpCode An integer identifying the error message
         * @param helpString A human-readable string that can be shown in UI
         */
        public void onAuthenticationHelp(int helpCode, CharSequence helpString) { }

        /**
         * Called when a face is recognized.
         * @param result An object containing authentication-related data
         */
        public void onAuthenticationSucceeded(AuthenticationResult result) { }

        /**
         * Called when a face is valid but not recognized.
         */
        public void onAuthenticationFailed() { }
    };

    /**
     * Callback structure provided to {@link FaceManager#enroll(long, EnrollmentCallback,
     * CancellationSignal, int, Surface, int, int). Users of {@link #FaceManager()}
     * must provide an implementation of this to {@link FaceManager#enroll(long,
     * CancellationSignal, int, EnrollmentCallback, Surface, int, int) for listening to face events.
     *
     * @hide
     */
    public static abstract class EnrollmentCallback {
        /**
         * Called when an unrecoverable error has been encountered and the operation is complete.
         * No further callbacks will be made on this object.
         * @param errMsgId An integer identifying the error message
         * @param errString A human-readable error string that can be shown in UI
         */
        public void onEnrollmentError(int errMsgId, CharSequence errString) { }

        /**
         * Called when a recoverable error has been encountered during enrollment. The help
         * string is provided to give the user guidance for what went wrong or what they need to do next.
         * @param helpMsgId An integer identifying the error message
         * @param helpString A human-readable string that can be shown in UI
         */
        public void onEnrollmentHelp(int helpMsgId, CharSequence helpString) { }

        /**
         * Called as each enrollment step progresses. Enrollment is considered complete when
         * progress reaches 100. This function will not be called if enrollment fails. See
         * {@link EnrollmentCallback#onEnrollmentError(int, CharSequence)}
         * @param progress The progress of enrollment
         */
        public void onEnrollmentProgress(int progress) { }
    };

    /**
     * Callback structure provided to {@link FaceManager#remove(int). Users of
     * {@link #FaceManager()} may optionally provide an implementation of this to
     * {@link FaceManager#remove(int, int, RemovalCallback)} for listening to
     * face template removal events.
     *
     * @hide
     */
    public static abstract class RemovalCallback {
        /**
         * Called when the given face can't be removed.
         * @param face The face that the call attempted to remove
         * @param errMsgId An associated error message id
         * @param errString An error message indicating why the face id can't be removed
         */
        public void onRemovalError(Face face, int errMsgId, CharSequence errString) { }

        /**
         * Called when a given face is successfully removed.
         * @param face the face template that was removed.
         */
        public void onRemovalSucceeded(Face face) { }
    };

    /**
     * @hide
     */
    public static abstract class LockoutResetCallback {

        /**
         * Called when lockout period expired and clients are allowed to listen for face
         * again.
         */
        public void onLockoutReset() { }
    };

    /**
     * Request authentication of a crypto object. This call warms up the face hardware
     * and starts scanning for a face. It terminates when
     * {@link AuthenticationCallback#onAuthenticationError(int, CharSequence)} or
     * {@link AuthenticationCallback#onAuthenticationSucceeded(AuthenticationResult)} is called, at
     * which point the object is no longer valid. The operation can be canceled by using the
     * provided cancel object.
     *
     * @param crypto object associated with the call or null if none required.
     * @param cancel an object that can be used to cancel authentication
     * @param flags optional flags; should be 0
     * @param callback an object to receive authentication events
     * @param handler an optional handler to handle callback events
     *
     * @throws IllegalArgumentException if the crypto operation is not supported or is not backed
     *         by <a href="{@docRoot}training/articles/keystore.html">Android Keystore
     *         facility</a>.
     * @throws IllegalStateException if the crypto primitive is not initialized.
     * @hide
     */
    @RequiresPermission(USE_FACE)
    public void authenticate(@Nullable CryptoObject crypto, @Nullable CancellationSignal cancel,
            int flags, @NonNull AuthenticationCallback callback, @NonNull int width, @NonNull int height, @Nullable Handler handler) {
        authenticate(crypto, cancel, flags, callback, width, height, handler, UserHandle.myUserId());
    }

    /**
     * Use the provided handler thread for events.
     * @param handler
     */
    private void useHandler(Handler handler) {
        if (handler != null) {
            mHandler = new MyHandler(handler.getLooper());
        } else if (mHandler.getLooper() != mContext.getMainLooper()){
            mHandler = new MyHandler(mContext.getMainLooper());
        }
    }

    /**
     * Per-user version
     * @hide
     */
    @RequiresPermission(USE_FACE)
    public void authenticate(@Nullable CryptoObject crypto, @Nullable CancellationSignal cancel,
            int flags, @NonNull AuthenticationCallback callback, @NonNull int width, @NonNull int height, Handler handler, int userId) {
        if (callback == null) {
            throw new IllegalArgumentException("Must supply an authentication callback");
        }

        if (cancel != null) {
            if (cancel.isCanceled()) {
                Log.w(TAG, "authentication already canceled");
                return;
            } else {
                cancel.setOnCancelListener(new OnAuthenticationCancelListener(crypto));
                mCancel = cancel;
            }
        }

        mWidth = width;
        mHeight = height;
        mSurface = null;
        onFaceidEntered();

        if (mService != null) try {
            useHandler(handler);
            mAuthenticationCallback = callback;
            mCryptoObject = crypto;
            long sessionId = crypto != null ? crypto.getOpId() : 0;
            mService.authenticate(mToken, sessionId, userId, mServiceReceiver, width, height, flags,
                    mContext.getOpPackageName());
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception while authenticating: ", e);
            onFaceidExited();
            if (callback != null) {
                // Though this may not be a hardware issue, it will cause apps to give up or try
                // again later.
                callback.onAuthenticationError(FACE_ERROR_HW_UNAVAILABLE,
                        getErrorString(FACE_ERROR_HW_UNAVAILABLE));
            }
        }
    }

    /**
     * Request face enrollment. This call warms up the face hardware
     * and starts scanning for faces. Progress will be indicated by callbacks to the
     * {@link EnrollmentCallback} object. It terminates when
     * {@link EnrollmentCallback#onEnrollmentError(int, CharSequence)} or
     * {@link EnrollmentCallback#onEnrollmentProgress(int) is called with progress == 100, at
     * which point the object is no longer valid. The operation can be canceled by using the
     * provided cancel object.
     * @param token a unique token provided by a recent creation or verification of device
     * credentials (e.g. pin, pattern or password).
     * @param cancel an object that can be used to cancel enrollment
     * @param flags optional flags
     * @param callback an object to receive enrollment events
     * @param handler an optional handler to handle callback events
     * @hide
     */
    @RequiresPermission(MANAGE_FACE)
    public void enroll(byte [] token, CancellationSignal cancel, int flags,
            EnrollmentCallback callback, Surface surface, int width, int height, Handler handler) {
        if (callback == null) {
            throw new IllegalArgumentException("Must supply an enrollment callback");
        }

        if (cancel != null) {
            if (cancel.isCanceled()) {
                Log.w(TAG, "enrollment already canceled");
                return;
            } else {
                cancel.setOnCancelListener(new OnEnrollCancelListener());
                mCancel = cancel;
            }
        }

        mWidth = width;
        mHeight = height;
        mSurface = surface;
        onFaceidEntered();

        if (mService != null) try {
            useHandler(handler);
            mEnrollmentCallback = callback;
            mService.enroll(mToken, token, getCurrentUserId(), mServiceReceiver, flags, width, height);
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception in enroll: ", e);
            onFaceidExited();
            if (callback != null) {
                // Though this may not be a hardware issue, it will cause apps to give up or try
                // again later.
                callback.onEnrollmentError(FACE_ERROR_HW_UNAVAILABLE,
                        getErrorString(FACE_ERROR_HW_UNAVAILABLE));
            }
        }
    }

    /**
     * Requests a pre-enrollment auth token to tie enrollment to the confirmation of
     * existing device credentials (e.g. pin/pattern/password).
     * @hide
     */
    @RequiresPermission(MANAGE_FACE)
    public long preEnroll() {
        long result = 0;
        if (mService != null) try {
            result = mService.preEnroll(mToken);
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception in preEnroll: ", e);
        }
        return result;
    }

    /**
     * Finishes enrollment and cancels the current auth token.
     * @hide
     */
    @RequiresPermission(MANAGE_FACE)
    public int postEnroll() {
        int result = 0;
        if (mService != null) try {
            result = mService.postEnroll(mToken);
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception in post enroll: ", e);
        }
        return result;
    }

    /**
     * Remove given face template from face hardware and/or protected storage.
     * @param face the face item to remove
     * @param callback an optional callback to verify that face templates have been
     * successfully removed. May be null of no callback is required.
     *
     * @hide
     */
    @RequiresPermission(MANAGE_FACE)
    public void remove(Face face, RemovalCallback callback) {
        if (mService != null) try {
            mRemovalCallback = callback;
            mRemovalFace = face;
            mService.remove(mToken, face.getFaceId(), getCurrentUserId(), mServiceReceiver);
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception in remove: ", e);
            if (callback != null) {
                callback.onRemovalError(face, FACE_ERROR_HW_UNAVAILABLE,
                        getErrorString(FACE_ERROR_HW_UNAVAILABLE));
            }
        }
    }

    /**
     * Renames the given face template
     * @param faceId the face id
     * @param newName the new name
     *
     * @hide
     */
    @RequiresPermission(MANAGE_FACE)
    public void rename(int faceId, String newName) {
        // Renames the given faceId
        if (mService != null) {
            try {
                mService.rename(faceId, getCurrentUserId(), newName);
            } catch (RemoteException e) {
                Log.v(TAG, "Remote exception in rename(): ", e);
            }
        } else {
            Log.w(TAG, "rename(): Service not connected!");
        }
    }

    /**
     * Obtain the list of enrolled faces templates.
     * @return list of current face items
     *
     * @hide
     */
    @RequiresPermission(USE_FACE)
    public List<Face> getEnrolledFaces(int userId) {
        if (mService != null) try {
            return mService.getEnrolledFaces(userId, mContext.getOpPackageName());
        } catch (RemoteException e) {
            Log.v(TAG, "Remote exception in getEnrolledFaces: ", e);
        }
        return null;
    }

    /**
     * Obtain the list of enrolled faces templates.
     * @return list of current face items
     *
     * @hide
     */
    @RequiresPermission(USE_FACE)
    public List<Face> getEnrolledFaces() {
        return getEnrolledFaces(UserHandle.myUserId());
    }

    /**
     * Determine if there is at least one face enrolled.
     *
     * @return true if at least one face is enrolled, false otherwise
     */
    @RequiresPermission(USE_FACE)
    public boolean hasEnrolledFaces() {
        if (mService != null) try {
            return mService.hasEnrolledFaces(UserHandle.myUserId(),
                    mContext.getOpPackageName());
        } catch (RemoteException e) {
            Log.v(TAG, "Remote exception in hasEnrolledFaces: ", e);
        }
        return false;
    }

    /**
     * Determine if face hardware is present and functional.
     *
     * @return true if hardware is present and functional, false otherwise.
     */
    @RequiresPermission(USE_FACE)
    public boolean isHardwareDetected() {
        if (mService != null) {
            try {
                long deviceId = 0;
                return mService.isHardwareDetected(deviceId, mContext.getOpPackageName());
            } catch (RemoteException e) {
                Log.v(TAG, "Remote exception in isFaceHardwareDetected(): ", e);
            }
        } else {
            Log.w(TAG, "isFaceHardwareDetected(): Service not connected!");
        }
        return false;
    }

    /**
     * Retrieves the authenticator token for binding keys to the lifecycle
     * of the current set of faces. Used only by internal clients.
     *
     * @hide
     */
    public long getAuthenticatorId() {
        if (mService != null) {
            try {
                return mService.getAuthenticatorId(mContext.getOpPackageName());
            } catch (RemoteException e) {
                Log.v(TAG, "Remote exception in getAuthenticatorId(): ", e);
            }
        } else {
            Log.w(TAG, "getAuthenticatorId(): Service not connected!");
        }
        return 0;
    }

    /**
     * Get lockout left time(s).
     */
    public int getLockoutLeftTime() {
        if (mService != null) {
            try {
                return mService.getLockoutLeftTime();
            } catch (RemoteException e) {
                Log.v(TAG, "Remote exception in getLockoutLeftTime(): ", e);
            }
        } else {
            Log.w(TAG, "getLockoutLeftTime(): Service not connected!");
        }
        return 0;
    }

    /**
     * Reset the lockout timer when asked to do so by keyguard.
     *
     * @param token an opaque token returned by password confirmation.
     *
     * @hide
     */
    public void resetLockout(byte[] token) {
        if (mService != null) {
            try {
                mService.resetLockout(token);
            } catch (RemoteException e) {
                Log.v(TAG, "Remote exception in resetLockout(): ", e);
            }
        } else {
            Log.w(TAG, "resetLockout(): Service not connected!");
        }
    }

    /**
     * @hide
     */
    public void addLockoutResetCallback(final LockoutResetCallback callback) {
        if (mService != null) {
            try {
                final PowerManager powerManager = mContext.getSystemService(PowerManager.class);
                mService.addLockoutResetCallback(
                        new IFaceServiceLockoutResetCallback.Stub() {

                    @Override
                    public void onLockoutReset(long deviceId, IRemoteCallback serverCallback)
                            throws RemoteException {
                        try {
                            final PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                                    PowerManager.PARTIAL_WAKE_LOCK, "lockoutResetCallback");
                            wakeLock.acquire();
                            mHandler.post(() -> {
                                try {
                                    callback.onLockoutReset();
                                } finally {
                                    wakeLock.release();
                                }
                            });
                        } finally {
                            serverCallback.sendResult(null /* data */);
                        }
                    }
                });
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        } else {
            Slog.w(TAG, "addLockoutResetCallback(): Service not connected!");
        }
    }

    private class MyHandler extends Handler {
        private MyHandler(Context context) {
            super(context.getMainLooper());
        }

        private MyHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(android.os.Message msg) {
            switch(msg.what) {
                case MSG_ENROLL_RESULT:
                    sendEnrollResult((Face) msg.obj, msg.arg1 /* progress */);
                    break;
                case MSG_HELP:
                    sendHelpResult((Long) msg.obj /* deviceId */, msg.arg1 /* help */);
                    break;
                case MSG_AUTHENTICATION_SUCCEEDED:
                    sendAuthenticatedSucceeded((Face) msg.obj);
                    break;
                case MSG_AUTHENTICATION_FAILED:
                    sendAuthenticatedFailed();
                    break;
                case MSG_ERROR:
                    sendErrorResult((Long) msg.obj /* deviceId */, msg.arg1 /* errMsgId */);
                    break;
                case MSG_REMOVED:
                    sendRemovedResult((Long) msg.obj /* deviceId */, msg.arg1 /* faceId */,
                            msg.arg2 /* groupId */);
                    break;
            }
        }

        private void sendRemovedResult(long deviceId, int faceId, int groupId) {
            if (mRemovalCallback != null) {
                int reqFaceId = mRemovalFace.getFaceId();
                int reqGroupId = mRemovalFace.getGroupId();
                if (faceId != reqFaceId) {
                    Log.w(TAG, "Face id didn't match: " + faceId + " != " + reqFaceId);
                }
                if (groupId != groupId) {
                    Log.w(TAG, "Group id didn't match: " + groupId + " != " + reqGroupId);
                }
                mRemovalCallback.onRemovalSucceeded(mRemovalFace);
            }
        }

        private void sendErrorResult(long deviceId, int errMsgId) {
            onFaceidExited();
            if (mEnrollmentCallback != null) {
                mEnrollmentCallback.onEnrollmentError(errMsgId, getErrorString(errMsgId));
            } else if (mAuthenticationCallback != null) {
                mAuthenticationCallback.onAuthenticationError(errMsgId, getErrorString(errMsgId));
            } else if (mRemovalCallback != null) {
                mRemovalCallback.onRemovalError(mRemovalFace, errMsgId,
                        getErrorString(errMsgId));
            }
        }

        private void sendEnrollResult(Face face, int progress) {
            if(progress >= 100) {
                onFaceidExited();
            }
            if (mEnrollmentCallback != null) {
                mEnrollmentCallback.onEnrollmentProgress(progress);
            }
        }

        private void sendAuthenticatedSucceeded(Face face) {
            onFaceidExited();
            if (mAuthenticationCallback != null) {
                final AuthenticationResult result = new AuthenticationResult(mCryptoObject, face);
                mAuthenticationCallback.onAuthenticationSucceeded(result);
            }
        }

        private void sendAuthenticatedFailed() {
            onFaceidExited();
            if (mAuthenticationCallback != null) {
               mAuthenticationCallback.onAuthenticationFailed();
            }
        }

        private void sendHelpResult(long deviceId, int help) {
            final String msg = getHelpString(help);
            /*if (msg == null) {
                return;
            }*/
            if (mEnrollmentCallback != null) {
                mEnrollmentCallback.onEnrollmentHelp(help, msg);
            } else if (mAuthenticationCallback != null) {
                mAuthenticationCallback.onAuthenticationHelp(help, msg);
            }
        }
    };

    void onFaceidEntered() {
        startFaceCamera();
        mOrientationEventListener.enable();
    }

    void onFaceidExited() {
        mCancel.setOnCancelListener(null);
        stopFaceCamera();
        mOrientationEventListener.disable();
    }

    void handleCameraError() {
        Log.d(TAG, "handleCameraError");
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mEnrollmentCallback != null) {
                    mEnrollmentCallback.onEnrollmentError(FACE_ERROR_CAMERA_UNAVAILABLE, null);
                    cancelEnrollment();
                } else if (mAuthenticationCallback != null) {
                    mAuthenticationCallback.onAuthenticationError(FACE_ERROR_CAMERA_UNAVAILABLE, null);
                    cancelAuthentication(mCryptoObject);
                }
            }
        });
        mFaceCam = null;
    }

    private final Runnable mOpenCam = new Runnable() {
        @Override
        public void run() {
            synchronized (FaceManager.class) {
                mOpenCamStarted = true;
            }
            mFaceCam = new FaceCameraImpl(mFaceCamHandler);
            mFaceCam.initialize(mWidth, mHeight, ImageFormat.YUV_420_888,
                                  mWidth * mHeight * 3 / 2, mCameraListener, mSurface, mContext);
        }
    };

    private boolean mOpenCamStarted = false;

    void startFaceCamera() {
        mFaceCamHandler.post(mOpenCam);
    }

    private final Runnable mCloseCam = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "close runnable executed.");
            if(mFaceCam != null) {
                mFaceCam.deInitialize();
                mFaceCam = null;
            }
        }
    };

    void stopFaceCamera() {
        boolean close = false;
        synchronized (FaceManager.class) {
            close = mOpenCamStarted;
            mOpenCamStarted = false;
        }
        if(close) {
            Log.d(TAG, "open camera runned, post close runnable");
            mFaceCamHandler.post(mCloseCam);
        } else {
            Log.d(TAG, "open camera not runned, remove open runnable");
            mFaceCamHandler.removeCallbacks(mOpenCam);
        }
    }

    private final FaceCameraImpl.CameraCallback mCameraListener = new FaceCameraImpl.CameraCallback() {
        @Override
        public void onCameraResultAvailable(final long[] addrs, final int[] info, final byte[] byteInfo) {
            if(addrs == null) {
                handleCameraError();
                return;
            }
            Log.d(TAG, String.format("onCameraResultAvailable: main buffer = %x", addrs[0]));
            int[] cameraInfo = new int[info.length + 1];
            System.arraycopy(info, 0, cameraInfo, 0, info.length);
            cameraInfo[info.length] = mOrientationEventListener.getCurrentOrientation();
            if(mSurface == null) {
                if (mService != null) try {
                    //long otp = (addrs.length == 2) ? 0 : addrs[2];
                    mService.informAuthFaceBuffer(addrs[0], 0, 0, cameraInfo, byteInfo);
                } catch (RemoteException e) {
                    Log.w(TAG, "Remote exception in informAuthFaceBuffer: ", e);
                }
            } else {
                if (mService != null) try {
                    mService.informEnrollFaceBuffer(addrs[0], cameraInfo, byteInfo);
                } catch (RemoteException e) {
                    Log.w(TAG, "Remote exception in informEnrollFaceBuffer: ", e);
                }
            }
        }
    };

    /*
     * inherited from vendor/sprd/platform/packages/apps/DreamCamera2/src/com/android/camera/app/OrientationManagerImpl.java
     */
    private class FaceOrientationEventListener extends OrientationEventListener {
        private int mCurrentOrientation = 0;
        private static final int ORIENTATION_HYSTERESIS = 5;
        public FaceOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if(orientation == ORIENTATION_UNKNOWN) return;
            int dist = Math.abs(orientation - mCurrentOrientation);
            dist = Math.min(dist, 360 - dist);
            boolean isOrientationChanged = (dist >= 45 + ORIENTATION_HYSTERESIS);
            if(isOrientationChanged) {
                int newRoundedOrientation = ((orientation + 45) / 90 * 90) % 360;
                switch (newRoundedOrientation) {
                    case 0:
                    case 90:
                    case 180:
                    case 270:
                        mCurrentOrientation =  newRoundedOrientation;
                        break;
                    default:
                        break;
                }
            }
        }

        public int getCurrentOrientation() {
            return mCurrentOrientation;
        }
    };

    /**
     * @hide
     */
    public FaceManager(Context context, IFaceService service) {
        mContext = context;
        mService = service;
        if (mService == null) {
            Slog.v(TAG, "FaceManagerService was null");
        }
        mHandler = new MyHandler(context);
        HandlerThread ht = new HandlerThread("CameraThread");
        ht.start();
        mFaceCamHandler = new Handler(ht.getLooper());
        mOrientationEventListener = new FaceOrientationEventListener(context);
    }

    private int getCurrentUserId() {
        try {
            return ActivityManagerNative.getDefault().getCurrentUser().id;
        } catch (RemoteException e) {
            return UserHandle.USER_NULL;
        }
    }

    private void cancelEnrollment() {
        if (mService != null) try {
            mService.cancelEnrollment(mToken);
        } catch (RemoteException e) {
            if (DEBUG) Log.w(TAG, "Remote exception while canceling enrollment");
        }
    }

    private void cancelAuthentication(CryptoObject cryptoObject) {
        if (mService != null) try {
            mService.cancelAuthentication(mToken, mContext.getOpPackageName());
        } catch (RemoteException e) {
            if (DEBUG) Log.w(TAG, "Remote exception while canceling enrollment");
        }
    }

    private String getErrorString(int errMsg) {
        return null;
    }

    private String getHelpString(int help) {
        return null;
    }

    private IFaceServiceReceiver mServiceReceiver = new IFaceServiceReceiver.Stub() {

        @Override // binder call
        public void onEnrollResult(long deviceId, int faceId, int groupId, int progress) {
            mHandler.obtainMessage(MSG_ENROLL_RESULT, progress, 0,
                    new Face(null, groupId, faceId, deviceId)).sendToTarget();
        }

        @Override // binder call
        public void onHelp(long deviceId, int help) {
            mHandler.obtainMessage(MSG_HELP, help, 0, deviceId).sendToTarget();
        }

        @Override // binder call
        public void onAuthenticationSucceeded(long deviceId, Face face) {
            mHandler.obtainMessage(MSG_AUTHENTICATION_SUCCEEDED, face).sendToTarget();
        }

        @Override // binder call
        public void onAuthenticationFailed(long deviceId) {
            mHandler.obtainMessage(MSG_AUTHENTICATION_FAILED).sendToTarget();;
        }

        @Override // binder call
        public void onError(long deviceId, int error) {
            mHandler.obtainMessage(MSG_ERROR, error, 0, deviceId).sendToTarget();
        }

        @Override // binder call
        public void onRemoved(long deviceId, int faceId, int groupId) {
            mHandler.obtainMessage(MSG_REMOVED, faceId, groupId, deviceId).sendToTarget();
        }

        @Override // binder call
        public void onEnrollFreeBuffer(long deviceId, long addr) {
            mFaceCamHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(mFaceCam != null) {
                        mFaceCam.faceCapture(new long[]{addr}, mOrientationEventListener.getCurrentOrientation());
                    } else {
                        Log.e(TAG,"enroll faceCapture fail: mFaceCam is null");
                    }
                }
            });
        }

        @Override // binder call
        public void onAuthFreeBuffer(long deviceId, long main, long sub) {
            mFaceCamHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(mFaceCam != null) {
                        mFaceCam.faceCapture(new long[]{main, sub}, mOrientationEventListener.getCurrentOrientation());
                    } else {
                        Log.e(TAG,"auth faceCapture fail: mFaceCam is null");
                    }
                }
            });
        }

        @Override // binder call
        public void onAlgoStartedResult(long deviceId, int result) {
            if(result != 0) {
                Log.e(TAG, "face algo start fail");
                onFaceidExited();
                if (mEnrollmentCallback != null) {
                    mEnrollmentCallback.onEnrollmentError(FACE_ERROR_HW_UNAVAILABLE, getErrorString(FACE_ERROR_HW_UNAVAILABLE));
                } else if (mAuthenticationCallback != null) {
                    mAuthenticationCallback.onAuthenticationError(FACE_ERROR_HW_UNAVAILABLE, getErrorString(FACE_ERROR_HW_UNAVAILABLE));
                }
                return;
            }
        }
    };

}

