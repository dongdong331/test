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

package com.android.server.face;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AppOpsManager;
import android.app.SynchronousUserSwitchObserver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.graphics.ImageFormat;
import android.os.Binder;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IHwBinder;
import android.os.IRemoteCallback;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.PowerManager;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SELinux;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.security.KeyStore;
import android.util.Slog;

import com.android.server.SystemService;

import vendor.sprd.hardware.face.V1_0.IFace;
import vendor.sprd.hardware.face.V1_0.IFaceClientCallback;
import android.hardware.face.Face;
import android.hardware.face.FaceManager;
import android.hardware.face.IFaceService;
import android.hardware.face.IFaceServiceLockoutResetCallback;
import android.hardware.face.IFaceServiceReceiver;

import static android.Manifest.permission.MANAGE_FACE;
import static android.Manifest.permission.USE_FACE;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.lang.Exception;

/**
 * A service to manage multiple clients that want to access the face HAL API.
 * The service is responsible for maintaining a list of clients and dispatching all
 * face -related events.
 *
 * @hide
 */
public class FaceService extends SystemService implements IHwBinder.DeathRecipient {
    private static final String TAG = "FaceService";
    private static final boolean DEBUG = true;
    private static final String FACE_DATA_DIR = "facedata";
    private static final int MSG_USER_SWITCHING = 10;
    private static final int ENROLLMENT_TIMEOUT_MS = 30 * 1000;
    private static final int MaxTemplatesPerUser = 1;

    private int mWidth;
    private int mHeight;

    private ClientMonitor mAuthClient = null;
    private ClientMonitor mEnrollClient = null;
    private ClientMonitor mRemoveClient = null;
    //private final AppOpsManager mAppOps;

    private static final long MS_PER_SEC = 1000;
    private static final long FAIL_LOCKOUT_TIMEOUT_MS = 60 * 1000;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private HandlerThread mFaceDispatchThread = null;
    private Handler mFaceDispatchHandler = null;
    private HandlerThread mFaceInformThread = null;
    private Handler mFaceInformHandler = null;

    private final FaceUtils mFaceUtils = FaceUtils.getInstance();
    private Context mContext;
    private long mHalDeviceId;
    private int mFailedAttempts;
    private int mLockoutLeftTime;
    private IFace mDaemon;
    private PowerManager mPowerManager;
    private final ArrayList<FaceServiceLockoutResetMonitor> mLockoutMonitors = new ArrayList<>();

    private static final long USER_ACTIVITY_TIME_INTERVAL_MS = 10 * 1000;

    private final Runnable mUserActivity = new Runnable() {
        @Override
        public void run() {
            userActivity();
            mHandler.postDelayed(mUserActivity, USER_ACTIVITY_TIME_INTERVAL_MS);
        }
    };

    private final Runnable mLockoutReset = new Runnable() {
        @Override
        public void run() {
            resetFailedAttempts();
        }
    };

    private final Runnable mLockoutTimer = new Runnable() {
        @Override
        public void run() {
            mLockoutLeftTime--;
            if(mLockoutLeftTime > 1)
                mHandler.postDelayed(mLockoutTimer, MS_PER_SEC);
        }
    };

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case MSG_USER_SWITCHING:
                    handleUserSwitching(msg.arg1);
                    break;

                default:
                    Slog.w(TAG, "Unknown message:" + msg.what);
            }
        }
    };

    public FaceService(Context context) {
        super(context);
        mContext = context;
        //mAppOps = context.getSystemService(AppOpsManager.class);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
    }

    @Override
    public void serviceDied(long cookie) {
        Slog.v(TAG, "faced died");
        mDaemon = null;
        dispatchError(mHalDeviceId, FaceManager.FACE_ERROR_HW_UNAVAILABLE);
    }

    public IFace getFaceDaemon() {
        if (mDaemon == null) {
            Slog.v(TAG, "mDeamon was null, reconnect to faceId");
            try {
                mDaemon = IFace.getService();
            } catch (java.util.NoSuchElementException e) {
                // Service doesn't exist or cannot be opened. Logged below.
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to get face interface", e);
            }
            if (mDaemon == null) {
                Slog.w(TAG, "face HIDL not available");
                return null;
            }

            mDaemon.asBinder().linkToDeath(this, 0);

            try {
                mHalDeviceId = mDaemon.setNotify(mDaemonCallback);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to open face HAL", e);
                mDaemon = null; // try again later!
            }

            if (DEBUG) Slog.v(TAG, "faceId HAL id: " + mHalDeviceId);
            if (mHalDeviceId != 0) {
                updateActiveGroup(ActivityManager.getCurrentUser());
            } else {
                Slog.w(TAG, "Failed to open Face HAL!");
                mDaemon = null;
            }
        }
        return mDaemon;
    }

    private void userActivity() {
        long now = SystemClock.uptimeMillis();
        mPowerManager.userActivity(now, PowerManager.USER_ACTIVITY_EVENT_TOUCH, 0);
    }

    void handleUserSwitching(int userId) {
        updateActiveGroup(userId);
    }

    private void updateActiveGroup(int userId) {
        IFace daemon = getFaceDaemon();
        if (daemon != null) {
            try {
                userId = getEffectiveUserId(userId);
                daemon.setActiveGroup(userId, "dummy path");
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to setActiveGroup():", e);
            }
        }
    }

    private void listenForUserSwitches() {
        try {
            ActivityManager.getService().registerUserSwitchObserver(
                new SynchronousUserSwitchObserver() {
                    @Override
                    public void onUserSwitching(int newUserId) throws RemoteException {
                        mHandler.obtainMessage(MSG_USER_SWITCHING, newUserId, 0 /* unused */)
                                .sendToTarget();
                    }
                }, TAG);
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to listen for user switching event" ,e);
        }
    }

    /*protected void dispatchEnumerate(long deviceId, int[] faceIds, int[] groupIds) {
        if (faceIds.length != groupIds.length) {
            Slog.w(TAG, "faceIds and groupIds differ in length: face[]="
                    + faceIds + ", g[]=" + groupIds);
            return;
        }
        if (DEBUG) Slog.w(TAG, "Enumerate: face[]=" + faceIds + ", g[]=" + groupIds);
        // TODO: update face/name pairs
    }*/

    protected void dispatchRemoved(long deviceId, int faceId, int groupId) {
        final ClientMonitor client = mRemoveClient;
        if (faceId != 0) {
            removeTemplateForUser(mRemoveClient, faceId);
        }
        if (client != null) {
            if(!client.sendRemoved(faceId, groupId)) {
                Slog.w(TAG, "sendRemoved failed");
            }
            removeClient(mRemoveClient);
        }
    }

    protected void dispatchError(long deviceId, int error) {
        serviceEnded();
        if (mEnrollClient != null) {
            if (!mEnrollClient.sendError(error)) {
                Slog.w(TAG, "mEnrollClient sendError failed");
            }
            removeClient(mEnrollClient);
        } else if (mAuthClient != null) {
            if (!mAuthClient.sendError(error)) {
                Slog.w(TAG, "mAuthClient sendError failed");
            }
            if(error != FaceManager.FACE_ERROR_NOFACE) {
                handleFailedAttempt(mAuthClient);
            }
            removeClient(mAuthClient);
        } else if (mRemoveClient != null) {
            if (!mRemoveClient.sendError(error)) {
                Slog.w(TAG, "mRemoveClient sendError failed");
            }
            removeClient(mRemoveClient);
        }
    }

    protected void dispatchEnrollProcessed(long deviceId, long addr, int progress) {
        if (mEnrollClient != null) {
            if (mEnrollClient.sendEnrollFreeBuffer(addr)) {
                //
            } else {
                Slog.w(TAG, "mEnrollClient sendEnrollFreeBuffer failed");
            }
            if (mEnrollClient.sendEnrollProgress(progress)) {
                //
            } else {
                Slog.w(TAG, "mEnrollClient sendEnrollProgress failed");
            }
        }
    }

    protected void dispatchAuthProcessed(long deviceId, long main, long sub) {
        if (mAuthClient != null) {
            if (mAuthClient.sendAuthFreeBuffer(main, sub)) {
                //
            } else {
                Slog.w(TAG, "mAuthClient sendAuthFreeBuffer failed");
            }
        }
    }

    protected void dispatchAuthenticated(long deviceId, int faceId, int groupId, ArrayList<Byte> token) {
        serviceEnded();
        if (faceId != 0) {
            // Ugh...
            final byte[] byteToken = new byte[token.size()];
            for (int i = 0; i < token.size(); i++) {
                byteToken[i] = token.get(i);
            }
            // Send to Keystore
            KeyStore.getInstance().addAuthToken(byteToken);
        }
        if (mAuthClient != null) {
            if (!mAuthClient.sendAuthenticated(faceId, groupId)) {
                Slog.w(TAG, "mAuthClient sendAuthenticated failed");
            }
            removeClient(mAuthClient);
        }
    }

    protected void dispatchHelp(long deviceId, int help) {
        if (mEnrollClient != null) {
            if (!mEnrollClient.sendHelp(help)) {
                Slog.w(TAG, "mEnrollClient sendHelp failed");
            }
        } else if (mAuthClient != null) {
            if (!mAuthClient.sendHelp(help)) {
                Slog.w(TAG, "mAuthClient sendHelp failed");
            }
        }
    }

    protected void dispatchEnrollResult(long deviceId, int faceId, int groupId) {
        serviceEnded();
        if (mEnrollClient != null) {
            if (mEnrollClient.sendEnrollResult(faceId, groupId)) {
                if (faceId > 0 && !hasEnrolledFaces(groupId)) {
                    addTemplateForUser(mEnrollClient, faceId);
                }
            } else {
                Slog.w(TAG, "mEnrollClient sendEnrollResult failed");
            }
            removeClient(mEnrollClient);
        }
    }

    private void removeClient(ClientMonitor client) {
        if (client == null) return;
        client.destroy();
        if (client == mAuthClient) {
            mAuthClient = null;
        } else if (client == mEnrollClient) {
            mEnrollClient = null;
        } else if (client == mRemoveClient) {
            mRemoveClient = null;
        }
    }

    private boolean inLockoutMode() {
        return mFailedAttempts >= MAX_FAILED_ATTEMPTS;
    }

    private void resetFailedAttempts() {
        if (inLockoutMode()) {
            Slog.v(TAG, "Reset face lockout");
            notifyLockoutResetMonitors();
        }
        mFailedAttempts = 0;
    }

    private boolean handleFailedAttempt(ClientMonitor clientMonitor) {
        mFailedAttempts++;
        if (mFailedAttempts >= MAX_FAILED_ATTEMPTS) {
            // Failing multiple times will continue to push out the lockout time.
            if (clientMonitor != null
                    && !clientMonitor.sendError(FaceManager.FACE_ERROR_LOCKOUT)) {
                Slog.w(TAG, "Cannot send lockout message to client");
            }
            //mLockoutLeftTime = (int)(FAIL_LOCKOUT_TIMEOUT_MS / MS_PER_SEC);
            //mHandler.postDelayed(mLockoutTimer, MS_PER_SEC);
            //mHandler.removeCallbacks(mLockoutReset);
            //mHandler.postDelayed(mLockoutReset, FAIL_LOCKOUT_TIMEOUT_MS);
            return true;
        }
        return false;
    }

    private void removeTemplateForUser(ClientMonitor clientMonitor, int faceId) {
        mFaceUtils.removeFaceIdForUser(mContext, faceId, clientMonitor.userId);
    }

    private void addTemplateForUser(ClientMonitor clientMonitor, int faceId) {
        mFaceUtils.addFaceForUser(mContext, faceId, clientMonitor.userId);
    }

    private void initFaceThreads() {
        mFaceDispatchThread = new HandlerThread("FaceDispatch");
        mFaceDispatchThread.start();
        mFaceDispatchHandler = new Handler(mFaceDispatchThread.getLooper()) ;
        mFaceInformThread = new HandlerThread("FaceInformer");
        mFaceInformThread.start();
        mFaceInformHandler = new Handler(mFaceInformThread.getLooper());
    }

    private void serviceStarted() { // faceid algo started
        mHandler.post(mUserActivity);
    }

    private void serviceEnded() { // faceid algo ended
        mFaceInformHandler.removeCallbacksAndMessages(null);
        mHandler.removeCallbacks(mUserActivity);
    }

    private void addLockoutResetMonitor(FaceServiceLockoutResetMonitor monitor) {
        if (!mLockoutMonitors.contains(monitor)) {
            mLockoutMonitors.add(monitor);
        }
    }

    private void removeLockoutResetCallback(
            FaceServiceLockoutResetMonitor monitor) {
        mLockoutMonitors.remove(monitor);
    }

    private void notifyLockoutResetMonitors() {
        for (int i = 0; i < mLockoutMonitors.size(); i++) {
            mLockoutMonitors.get(i).sendLockoutReset();
        }
    }

    void startEnrollment(IBinder token, byte[] cryptoToken, int groupId,
            IFaceServiceReceiver receiver, int flags, boolean restricted) {
        IFace daemon = getFaceDaemon();
        if (daemon == null) {
            Slog.w(TAG, "enroll: no faced!");
            return;
        }
        stopPendingOperations(true);
        mEnrollClient = new ClientMonitor(token, receiver, groupId, restricted);
        final int timeout = (int) (ENROLLMENT_TIMEOUT_MS / MS_PER_SEC);
        try {
            final int result = daemon.enroll(cryptoToken, groupId, timeout, mWidth, mHeight);
            mEnrollClient.sendAlgoStartedResult(result);
            if (result != 0) {
                Slog.w(TAG, "startEnroll failed, result=" + result);
                mEnrollClient = null;
            } else {
                serviceStarted();
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "startEnroll failed", e);
        }
    }

    public long startPreEnroll(IBinder token) {
        IFace daemon = getFaceDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startPreEnroll: no faced!");
            return 0;
        }
        try {
            return daemon.preEnroll();
        } catch (RemoteException e) {
            Slog.e(TAG, "startPreEnroll failed", e);
        }
        return 0;
    }

    public int startPostEnroll(IBinder token) {
        IFace daemon = getFaceDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startPostEnroll: no faced!");
            return 0;
        }
        try {
            return daemon.postEnroll();
        } catch (RemoteException e) {
            Slog.e(TAG, "startPostEnroll failed", e);
        }
        return 0;
    }

    private void stopPendingOperations(boolean initiatedByClient) {
        if (mEnrollClient != null) {
            stopEnrollment(mEnrollClient.token, initiatedByClient);
        }
        if (mAuthClient != null) {
            stopAuthentication(mAuthClient.token, initiatedByClient);
        }
        // mRemoveClient is allowed to continue
    }

    /**
     * Stop enrollment in progress and inform client if they initiated it.
     *
     * @param token token for client
     * @param initiatedByClient if this call is the result of client action (e.g. calling cancel)
     */
    void stopEnrollment(IBinder token, boolean initiatedByClient) {
        final ClientMonitor client = mEnrollClient;
        if (client == null || client.token != token) return;
        removeClient(mEnrollClient);
        serviceEnded();
        IFace daemon = getFaceDaemon();
        if (daemon == null) {
            Slog.w(TAG, "stopEnrollment: no faced!");
            return;
        }
        if (initiatedByClient) {
            try {
                int result = daemon.cancel();
                if (result != 0) {
                    Slog.w(TAG, "startEnrollCancel failed, result = " + result);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "stopEnrollment failed", e);
            }
        }
    }

    void startAuthentication(IBinder token, long opId, int groupId,
            IFaceServiceReceiver receiver, int width, int height, int flags, boolean restricted) {
        IFace daemon = getFaceDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startAuthentication: no faced!");
            return;
        }
        stopPendingOperations(true);
        mAuthClient = new ClientMonitor(token, receiver, groupId, restricted);
        if (inLockoutMode()) {
            Slog.v(TAG, "In lockout mode; disallowing authentication");
            if (!mAuthClient.sendError(FaceManager.FACE_ERROR_LOCKOUT)) {
                Slog.w(TAG, "Cannot send timeout message to client");
            }
            mAuthClient = null;
            return;
        }
        try {
            final int result = daemon.authenticate(opId, groupId, width, height);
            mAuthClient.sendAlgoStartedResult(result);
            if (result != 0) {
                Slog.w(TAG, "startAuthentication failed, result=" + result);
                mAuthClient = null;
            } else {
                serviceStarted();
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "startAuthentication failed", e);
        }
    }

    /**
     * Stop authentication in progress and inform client if they initiated it.
     *
     * @param token token for client
     * @param initiatedByClient if this call is the result of client action (e.g. calling cancel)
     */
    void stopAuthentication(IBinder token, boolean initiatedByClient) {
        final ClientMonitor client = mAuthClient;
        if (client == null || client.token != token) return;
        removeClient(mAuthClient);
        serviceEnded();
        IFace daemon = getFaceDaemon();
        if (daemon == null) {
            Slog.w(TAG, "stopAuthentication: no faced!");
            return;
        }
        if (initiatedByClient) {
            try {
                int result = daemon.cancel();
                if (result != 0) {
                    Slog.w(TAG, "stopAuthentication failed, result=" + result);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "stopAuthentication failed", e);
            }
        }
    }

    void startRemove(IBinder token, int faceId, int userId,
            IFaceServiceReceiver receiver, boolean restricted) {
        IFace daemon = getFaceDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startRemove: no faced!");
            return;
        }

        mRemoveClient = new ClientMonitor(token, receiver, userId, restricted);
        // The face template ids will be removed when we get confirmation from the HAL
        try {
            final int result = daemon.remove(userId, faceId);
            if (result != 0) {
                Slog.w(TAG, "startRemove with id = " + faceId + " failed, result=" + result);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "startRemove failed", e);
        }
    }

    void startInformEnrollFaceBuffer(long addr, int[] info, byte[] byteInfo) {
        if(mEnrollClient == null) return;
        IFace daemon = getFaceDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startInformEnrollFaceBuffer: no faced!");
            return;
        }

        try {
            ArrayList<Integer> cameraInfo = new ArrayList<Integer>();
            for(final int b : info) {
                cameraInfo.add(b);
            }
            ArrayList<Byte> cameraByteInfo = new ArrayList<Byte>();
            for(final byte b : byteInfo) {
                cameraByteInfo.add(b);
            }
            int result = daemon.doEnrollProcess(addr, cameraInfo, cameraByteInfo);
            if (result != 0) {
                Slog.w(TAG, "startInformEnrollFaceBuffer with addr = " + addr + " failed, result=" + result);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "startInformEnrollFaceBuffer failed", e);
        }
    }

    void startInformAuthFaceBuffer(long main, long sub, long otp, int[] info, byte[] byteInfo) {
        if(mAuthClient == null) return;
        IFace daemon = getFaceDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startInformAuthFaceBuffer: no faced!");
            return;
        }

        try {
            ArrayList<Integer> cameraInfo = new ArrayList<Integer>();
            for(final int b : info) {
                cameraInfo.add(b);
            }
            ArrayList<Byte> cameraByteInfo = new ArrayList<Byte>();
            for(final byte b : byteInfo) {
                cameraByteInfo.add(b);
            }
            int result = daemon.doAuthenticateProcess(main, sub, otp, cameraInfo, cameraByteInfo);
            if (result != 0) {
                Slog.w(TAG, "startInformAuthFaceBuffer failed, result=" + result);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "startInformAuthFaceBuffer failed", e);
        }
    }

    public List<Face> getEnrolledFaces(int userId) {
        return mFaceUtils.getFacesForUser(mContext, userId);
    }

    public boolean hasEnrolledFaces(int userId) {
        return mFaceUtils.getFacesForUser(mContext, userId).size() > 0;
    }

    public long getAuthenticatorId() {
        IFace daemon = getFaceDaemon();
        if (daemon != null) {
            try {
                return daemon.getAuthenticatorId();
            } catch (RemoteException e) {
                Slog.e(TAG, "getAuthenticatorId failed", e);
            }
        }
        return 0;
    }

    boolean hasPermission(String permission) {
        return getContext().checkCallingOrSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    void checkPermission(String permission) {
        getContext().enforceCallingOrSelfPermission(permission,
                "Must have " + permission + " permission.");
    }

    int getEffectiveUserId(int userId) {
        UserManager um = UserManager.get(mContext);
        if (um != null) {
            final long callingIdentity = Binder.clearCallingIdentity();
            userId = um.getCredentialOwnerProfile(userId);
            Binder.restoreCallingIdentity(callingIdentity);
        } else {
            Slog.e(TAG, "Unable to acquire UserManager");
        }
        return userId;
    }

    boolean isCurrentUserOrProfile(int userId) {
        UserManager um = UserManager.get(mContext);

        // Allow current user or profiles of the current user...
        List<UserInfo> profiles = um.getEnabledProfiles(userId);
        final int n = profiles.size();
        for (int i = 0; i < n; i++) {
            if (profiles.get(i).id == userId) {
                return true;
            }
        }
        return false;
    }

    private boolean canUseFace(String opPackageName) {
        checkPermission(USE_FACE);
        /*return mAppOps.noteOp(AppOpsManager.OP_USE_FACE, Binder.getCallingUid(),
                opPackageName) == AppOpsManager.MODE_ALLOWED;*/
        return true;
    }

    private class ClientMonitor implements IBinder.DeathRecipient {
        IBinder token;
        IFaceServiceReceiver receiver;
        int userId;
        boolean restricted; // True if client does not have MANAGE_FACE permission

        public ClientMonitor(IBinder token, IFaceServiceReceiver receiver, int userId,
                boolean restricted) {
            this.token = token;
            this.receiver = receiver;
            this.userId = userId;
            this.restricted = restricted;
            try {
                token.linkToDeath(this, 0);
            } catch (RemoteException e) {
                Slog.w(TAG, "caught remote exception in linkToDeath: ", e);
            }
        }

        public void destroy() {
            if (token != null) {
                try {
                    token.unlinkToDeath(this, 0);
                } catch (NoSuchElementException e) {
                    // TODO: remove when duplicate call bug is found
                    Slog.e(TAG, "destroy(): " + this + ":", new Exception("here"));
                }
                token = null;
            }
            receiver = null;
        }

        @Override
        public void binderDied() {
            //token = null;
            //removeClient(this);
            //receiver = null;
            stopPendingOperations(true);
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                if (token != null) {
                    if (DEBUG) Slog.w(TAG, "removing leaked reference: " + token);
                    removeClient(this);
                }
            } finally {
                super.finalize();
            }
        }

        /*
         * @return true if we're done.
         */
        private boolean sendRemoved(int faceId, int groupId) {
            if (receiver == null) return true; // client not listening
            try {
                receiver.onRemoved(mHalDeviceId, faceId, groupId);
                Slog.d(TAG, "removed face: faceId " + faceId + " groupId "+ groupId);
                return true; // faceId == 0;
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to notify Removed:", e);
            }
            return false;
        }

        /*
         * @return true if we're done.
         */
        private boolean sendEnrollResult(int faceId, int groupId) {
            if (receiver == null) return true; // client not listening
            //FaceUtils.vibrateFaceSuccess(getContext());
            try {
                if(faceId > 0) {
                    receiver.onEnrollResult(mHalDeviceId, faceId, groupId, 100);
                } else {
                    receiver.onError(mHalDeviceId, FaceManager.FACE_ERROR_VENDOR_BASE);
                }
                return true;
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to notify EnrollResult:", e);
                return false;
            }
        }

        /*
         * @return true if we're done.
         */
        private boolean sendEnrollProgress(int progress) {
            if (receiver == null) return true; // client not listening
            //FaceUtils.vibrateFaceSuccess(getContext());
            try {
                receiver.onEnrollResult(mHalDeviceId, 0, 0, progress);
                return true;
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to notify EnrollResult:", e);
                return false;
            }
        }

        /*
         * @return true if we're done.
         */
        private boolean sendAuthenticated(int faceId, int groupId) {
            boolean result = true;
            boolean authenticated = faceId != 0;
            if (receiver != null) {
                try {
                    if (!authenticated) {
                        receiver.onAuthenticationFailed(mHalDeviceId);
                    } else {
                        Face face = !restricted ?
                                new Face("" /* TODO */, groupId, faceId, mHalDeviceId) : null;
                        receiver.onAuthenticationSucceeded(mHalDeviceId, face);
                    }
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to notify Authenticated:", e);
                    result = false; // client failed
                }
            } else {
                result = true; // client not listening
	        }
	        if (faceId == 0) {
                //FaceUtils.vibrateFaceError(getContext());
                handleFailedAttempt(this);
            } else {
                //FaceUtils.vibrateFaceSuccess(getContext());
                mHandler.post(mLockoutReset);
            }
            return result;
        }

        /*
         * @return true if we're done.
         */
        private boolean sendHelp(int help) {
            if (receiver == null) return true; // client not listening
            try {
                receiver.onHelp(mHalDeviceId, help);
                return true;
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to invoke sendHelp:", e);
                return false; // client failed
            }
        }

        /*
         * @return true if we're done.
         */
        private boolean sendError(int error) {
            if (receiver != null) {
                try {
                    receiver.onError(mHalDeviceId, error);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to invoke sendError:", e);
                    return false;
                }
            }
            return true;
        }

        /*
         * @return true if we're done.
         */
        private boolean sendEnrollFreeBuffer(long addr) {
            if (receiver != null) {
                try {
                    receiver.onEnrollFreeBuffer(mHalDeviceId, addr);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to invoke sendEnrollFreeBuffer:", e);
                    return false;
                }
            }
            return true;
        }

        /*
         * @return true if we're done.
         */
        private boolean sendAuthFreeBuffer(long main, long sub) {
            if (receiver != null) {
                try {
                    receiver.onAuthFreeBuffer(mHalDeviceId, main, sub);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to invoke sendAuthFreeBuffer:", e);
                    return false;
                }
            }
            return true;
        }

        /*
         * @return true if we're done.
         */
        private boolean sendAlgoStartedResult(int result) {
            if (receiver != null) {
                try {
                    receiver.onAlgoStartedResult(mHalDeviceId, result);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to invoke sendAlgoStartedResult:", e);
                    return false;
                }
            }
            return true;
        }
    }

    private class FaceServiceLockoutResetMonitor implements IBinder.DeathRecipient {

        private static final long WAKELOCK_TIMEOUT_MS = 2000;
        private final IFaceServiceLockoutResetCallback mCallback;
        private final PowerManager.WakeLock mWakeLock;

        public FaceServiceLockoutResetMonitor(
                IFaceServiceLockoutResetCallback callback) {
            mCallback = callback;
            mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "lockout reset callback");
            try {
                mCallback.asBinder().linkToDeath(FaceServiceLockoutResetMonitor.this, 0);
            } catch (RemoteException e) {
                Slog.w(TAG, "caught remote exception in linkToDeath", e);
            }
        }

        public void sendLockoutReset() {
            if (mCallback != null) {
                try {
                    mWakeLock.acquire(WAKELOCK_TIMEOUT_MS);
                    mCallback.onLockoutReset(mHalDeviceId, new IRemoteCallback.Stub() {

                        @Override
                        public void sendResult(Bundle data) throws RemoteException {
                            releaseWakelock();
                        }
                    });
                } catch (DeadObjectException e) {
                    Slog.w(TAG, "Death object while invoking onLockoutReset: ", e);
                    mHandler.post(mRemoveCallbackRunnable);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to invoke onLockoutReset: ", e);
                    releaseWakelock();
                }
            }
        }

        private final Runnable mRemoveCallbackRunnable = new Runnable() {
            @Override
            public void run() {
                releaseWakelock();
                removeLockoutResetCallback(FaceServiceLockoutResetMonitor.this);
            }
        };

        @Override
        public void binderDied() {
            Slog.e(TAG, "Lockout reset callback binder died");
            mHandler.post(mRemoveCallbackRunnable);
        }

        private void releaseWakelock() {
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
    }

    private IFaceClientCallback mDaemonCallback = new IFaceClientCallback.Stub() {

        @Override
        public void onEnrollResult(final long deviceId, final int faceId, final int groupId) {
            mFaceDispatchHandler.post(new Runnable() {
                @Override
                public void run() {
                    dispatchEnrollResult(deviceId, faceId, groupId);
                }
            });
        }

        @Override
        public void onHelp(final long deviceId, final int help) {
            mFaceDispatchHandler.post(new Runnable() {
                @Override
                public void run() {
                    dispatchHelp(deviceId, help);
                }
            });
        }

        @Override
        public void onAuthenticated(final long deviceId, final int faceId, final int groupId, ArrayList<Byte> token) {
            mFaceDispatchHandler.post(new Runnable() {
                @Override
                public void run() {
                    dispatchAuthenticated(deviceId, faceId, groupId, token);
                }
            });
        }

        @Override
        public void onError(final long deviceId, final int error) {
            mFaceDispatchHandler.post(new Runnable() {
                @Override
                public void run() {
                    dispatchError(deviceId, error);
                }
            });
        }

        @Override
        public void onRemoved(final long deviceId, final int faceId, final int groupId) {
            mFaceDispatchHandler.post(new Runnable() {
                @Override
                public void run() {
                    dispatchRemoved(deviceId, faceId, groupId);
                }
            });
        }

        /*@Override
        public void onEnumerate(final long deviceId, final int[] faceIds, final int[] groupIds) {
            mFaceDispatchHandler.post(new Runnable() {
                @Override
                public void run() {
                    dispatchEnumerate(deviceId, faceIds, groupIds);
                }
            });
        }*/

        @Override
        public void onEnrollProcessed(final long deviceId, final long addr, final int progress) {
            mFaceDispatchHandler.post(new Runnable() {
                @Override
                public void run() {
                    dispatchEnrollProcessed(deviceId, addr, progress);
                }
            });
        }

        @Override
        public void onAuthProcessed(final long deviceId, final long main, final long sub) {
            mFaceDispatchHandler.post(new Runnable() {
                @Override
                public void run() {
                    dispatchAuthProcessed(deviceId, main, sub);
                }
            });
        }
    };

    private final class FaceServiceWrapper extends IFaceService.Stub {

        @Override // Binder call
        public long preEnroll(IBinder token) {
            checkPermission(MANAGE_FACE);
            return startPreEnroll(token);
        }

        @Override // Binder call
        public int postEnroll(IBinder token) {
            checkPermission(MANAGE_FACE);
            return startPostEnroll(token);
        }

        @Override // Binder call
        public void enroll(final IBinder token, final byte[] cryptoToken, final int groupId,
                final IFaceServiceReceiver receiver, final int flags, final int width, final int height) {
            checkPermission(MANAGE_FACE);
            final int limit =  MaxTemplatesPerUser;
            final int callingUid = Binder.getCallingUid();
            final int userId = UserHandle.getUserId(callingUid);
            final int enrolled = FaceService.this.getEnrolledFaces(userId).size();
            /*if (enrolled >= limit) {
                Slog.w(TAG, "Too many faces registered");
                return;
            }*/
            final byte [] cryptoClone = Arrays.copyOf(cryptoToken, cryptoToken.length);

            // Group ID is arbitrarily set to parent profile user ID. It just represents
            // the default faces for the user.
            final int effectiveGroupId = getEffectiveUserId(groupId);

            mWidth = width;
            mHeight = height;

            final boolean restricted = isRestricted();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    startEnrollment(token, cryptoClone, effectiveGroupId, receiver, flags, restricted);
                }
            });
        }

        private boolean isRestricted() {
            // Only give privileged apps (like Settings) access to face info
            final boolean restricted = !hasPermission(MANAGE_FACE);
            return restricted;
        }

        @Override // Binder call
        public void cancelEnrollment(final IBinder token) {
            checkPermission(MANAGE_FACE);
            /*mHandler.post(new Runnable() {
                @Override
                public void run() {
                    stopEnrollment(token, true);
                }
            });*/
            stopEnrollment(token, true);
        }

        @Override // Binder call
        public void authenticate(final IBinder token, final long opId, final int groupId,
                final IFaceServiceReceiver receiver, final int width, final int height, final int flags,
                final String opPackageName) {
            if (!isCurrentUserOrProfile(UserHandle.getCallingUserId())) {
                Slog.w(TAG, "Can't authenticate non-current user");
                return;
            }
            if (!canUseFace(opPackageName)) {
                Slog.w(TAG, "Calling not granted permission to use face");
                return;
            }

            // Group ID is arbitrarily set to parent profile user ID. It just represents
            // the default faces for the user.
            final int effectiveGroupId = getEffectiveUserId(groupId);

            final boolean restricted = isRestricted();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    startAuthentication(token, opId, effectiveGroupId, receiver, width, height, flags, restricted);
                }
            });
        }

        @Override // Binder call
        public void cancelAuthentication(final IBinder token, String opPackageName) {
            if (!canUseFace(opPackageName)) {
                return;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    stopAuthentication(token, true);
                }
            });
        }

        @Override // Binder call
        public void remove(final IBinder token, final int faceId, final int groupId,
                final IFaceServiceReceiver receiver) {
            checkPermission(MANAGE_FACE); // TODO: Maybe have another permission
            final boolean restricted = isRestricted();

            // Group ID is arbitrarily set to parent profile user ID. It just represents
            // the default faces for the user.
            final int effectiveGroupId = getEffectiveUserId(groupId);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    startRemove(token, faceId, effectiveGroupId, receiver, restricted);
                }
            });

        }

        @Override // Binder call
        public boolean isHardwareDetected(long deviceId, String opPackageName) {
            if (!canUseFace(opPackageName)) {
                return false;
            }
            return mHalDeviceId != 0;
        }

        @Override // Binder call
        public void rename(final int faceId, final int groupId, final String name) {
            checkPermission(MANAGE_FACE);

            // Group ID is arbitrarily set to parent profile user ID. It just represents
            // the default faces for the user.
            final int effectiveGroupId = getEffectiveUserId(groupId);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mFaceUtils.renameFaceForUser(mContext, faceId,
                            effectiveGroupId, name);
                }
            });
        }

        @Override // Binder call
        public List<Face> getEnrolledFaces(int userId, String opPackageName) {
            if (!canUseFace(opPackageName)) {
                return Collections.emptyList();
            }
            int effectiveUserId = getEffectiveUserId(userId);

            return FaceService.this.getEnrolledFaces(effectiveUserId);
        }

        @Override // Binder call
        public boolean hasEnrolledFaces(int userId, String opPackageName) {
            if (!canUseFace(opPackageName)) {
                return false;
            }

            int effectiveUserId  = getEffectiveUserId(userId);
            return FaceService.this.hasEnrolledFaces(effectiveUserId);
        }

        @Override // Binder call
        public long getAuthenticatorId(String opPackageName) {
            // In this method, we're not checking whether the caller is permitted to use face
            // API because current authenticator ID is leaked (in a more contrived way) via Android
            // Keystore (android.security.keystore package): the user of that API can create a key
            // which requires face authentication for its use, and then query the key's
            // characteristics (hidden API) which returns, among other things, face
            // authenticator ID which was active at key creation time.
            //
            // Reason: The part of Android Keystore which runs inside an app's process invokes this
            // method in certain cases. Those cases are not always where the developer demonstrates
            // explicit intent to use face functionality. Thus, to avoiding throwing an
            // unexpected SecurityException this method does not check whether its caller is
            // permitted to use face API.
            //
            // The permission check should be restored once Android Keystore no longer invokes this
            // method from inside app processes.

            return FaceService.this.getAuthenticatorId();
        }

        @Override // Binder call
        public void informEnrollFaceBuffer(final long addr, final int[] info, final byte[] byteInfo) {
            mFaceInformHandler.post(new Runnable() {
                @Override
                public void run() {
                    startInformEnrollFaceBuffer(addr, info, byteInfo);
                }
            });
        }

        @Override // Binder call
        public void informAuthFaceBuffer(final long main, final long sub, final long otp, final int[] info, final byte[] byteInfo) {
            mFaceInformHandler.post(new Runnable() {
                @Override
                public void run() {
                    startInformAuthFaceBuffer(main, sub, otp, info, byteInfo);
                }
            });
        }

        @Override // Binder call
        public int getLockoutLeftTime() {
            return mLockoutLeftTime;
        }

        @Override // Binder call
        public void resetLockout(byte [] token) {
            mHandler.post(mLockoutReset);
        }

        @Override
        public void addLockoutResetCallback(final IFaceServiceLockoutResetCallback callback)
                throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    addLockoutResetMonitor(
                            new FaceServiceLockoutResetMonitor(callback));
                }
            });
        }
    }

    @Override
    public void onStart() {
        publishBinderService(Context.FACE_SERVICE, new FaceServiceWrapper());
        IFace daemon = getFaceDaemon();
        if (DEBUG) Slog.v(TAG, "Face HAL id: " + mHalDeviceId);
        listenForUserSwitches();
        initFaceThreads();
    }
}
