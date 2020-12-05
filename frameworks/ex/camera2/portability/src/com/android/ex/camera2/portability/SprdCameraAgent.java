
package com.android.ex.camera2.portability;

import com.android.ex.camera2.portability.CameraAgent.CameraPictureCallback;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.media.Image;
import android.view.Surface;

public abstract class SprdCameraAgent {
    /**
     * @see com.android.ex.camera2.portability.CameraAgent#CAMERA_OPERATION_TIMEOUT_MS
     */
    public static final long CAMERA_OPERATION_TIMEOUT_MS = 3500;
    // SPRD: Fix bug 681215 waitForStates() may block forever
    public static final long WATI_STATE_TIMEOUT = 500;
    public static final long WATI_THUMB_STATE_TIMEOUT = 4000;

    public static interface CameraSurfaceViewPreviewCallback {
        void onSurfaceUpdate();
    }

    public static interface CameraSensorSelfShotCallback {
        public void onSensorSelfShot(boolean bool, int value);
    }

    public static interface CameraStartVideoCallback {
        void onVideoStart();
    }

    public static interface CancelBurstCaptureCallback {
        public void onCanceled(int hasCaptureCount);
    }

    public static interface CameraHdrDetectionCallback {
        public void onHdrDetection(boolean isHdrScene);
    }

    public static interface CameraIsAuto3DnrSceneDetectionCallback {
        public void onIsAuto3DnrSceneDetection(boolean isAuto3DnrScene);
    }

    public static interface CameraAiSceneCallback {
        public void onAiScene(int aiScene);
    }

    public static interface CameraBlurCaptureCallback {
        public void onBlurCapture();
    }

    public CameraCloseCallback mCameraCloseCallback;

    public static interface CameraCloseCallback {
        public void onCameraClosed();
    }

    public void setCameraCloseCallback(CameraCloseCallback cb) {
        mCameraCloseCallback = cb;
    }

    public void removeCameraCloseCallback() {
        mCameraCloseCallback = null;
    }

    public void closeCameraAsyncWithState() {}

    /* SPRD: Fix bug 591216 that add new feature 3d range finding, only support API2 currently @{ */
    public static interface RangeFindDistanceCallback {
        void onRangeFindDistanceReceived(int resultCode, double distance);
    }
    /* @} */

    public abstract static class SprdCameraProxy {
        /**
         * @return The thread used on which client callbacks are served.
         */
        public abstract DispatchThread getDispatchThread();

        /**
         * @return The handler to which camera tasks should be posted.
         */
        public abstract Handler getCameraHandler();

        /**
         * @return The camera agent which creates this proxy.
         */
        public abstract CameraAgent getAgent();

        /**
         * @return The state machine tracking the camera API's current mode.
         */
        public abstract CameraStateHolder getCameraState();

        /**
         * Sprd add for surfaceview restartPreview Sets the {@link android.view.SurfaceHolder} for
         * preview.
         * 
         * @param surfaceHolder The {@link SurfaceHolder} for preview.
         */
        public void setPreviewDisplayWithoutOptimize(final SurfaceHolder surfaceHolder) {
            try {
                getDispatchThread().runJob(new Runnable() {
                    @Override
                    public void run() {
                        getCameraHandler()
                                .obtainMessage(
                                        CameraActions.SET_PREVIEW_DISPLAY_ASYNC_WITHOUT_OPTIMIZE,
                                        surfaceHolder)
                                .sendToTarget();
                    }
                });
            } catch (final RuntimeException ex) {
                getAgent().getCameraExceptionHandler().onDispatchThreadException(ex);
            }
        }

        public void setPreviewTextureWithoutOptimize(final SurfaceTexture surfaceTexture) {
            try {
                getDispatchThread().runJob(new Runnable() {
                    @Override
                    public void run() {
                        getCameraHandler()
                                .obtainMessage(
                                        CameraActions.SET_PREVIEW_TEXTURE_ASYNC_WITHOUT_OPTIMIZE,
                                        surfaceTexture)
                                .sendToTarget();
                    }
                });
            } catch (final RuntimeException ex) {
                getAgent().getCameraExceptionHandler().onDispatchThreadException(ex);
            }
        }

        public void startVideoRecording(final Surface recoderSurface, CameraStartVideoCallback cb, final boolean useSurfaceView) {
            try {
                setStartVideoCallback(cb);
//                getDispatchThread().runJob(new Runnable() {
//                    @Override
//                    public void run() {
//                        getCameraHandler()
//                                .obtainMessage(CameraActions.START_VIDEO_RECORDER, useSurfaceView ? 1 : 0, 0, recoderSurface)
//                                .sendToTarget();
//                    }});
                getCameraHandler().sendMessageAtFrontOfQueue(
                        getCameraHandler().obtainMessage(CameraActions.START_VIDEO_RECORDER, useSurfaceView ? 1 : 0, 0, recoderSurface));
            } catch (final RuntimeException ex) {
                getAgent().getCameraExceptionHandler().onDispatchThreadException(ex);
            }
        }

        /* SPRD: fix bug677344 intent capture should not stoppreview with flush @{ */
        public void stopPreviewWithOutFlush() {
            // Don't bother to wait since camera is in bad state.
            if (getCameraState().isInvalid()) {
                return;
            }
            final CameraAgent.WaitDoneBundle bundle = new CameraAgent.WaitDoneBundle();
            try {
                if (getCameraState().getState() == SprdCameraStateHolder.CAMERA_WITH_THUMB) {
                    stopPreviewWithAsync();
                } else {
                    getDispatchThread().runJobSync(new Runnable() {
                        @Override
                        public void run() {
                            /**
                             * SPRD:fix bug622519 should unlock when stoppreivew is return due to wrong state
                            getCameraHandler().obtainMessage(CameraActions.STOP_PREVIEW, bundle)
                             */
                            getCameraHandler().obtainMessage(CameraActions.STOP_PREVIEW_WITHOUT_FLUSH)
                                    .sendToTarget();
                            getCameraHandler().post(bundle.mUnlockRunnable);
                        }}, bundle.mWaitLock, CAMERA_OPERATION_TIMEOUT_MS, "stop preview");
                }
            } catch (final RuntimeException ex) {
                getAgent().getCameraExceptionHandler().onDispatchThreadException(ex);
            }
        }
        /* @} */

        public void stopPreviewWithAsync() {}
        /**
         * SPRD:fix bug 473462 Cancels the burst capture process.
         * <p>
         * This action has the highest priority and will get processed before anything else that is
         * pending.
         * </p>
         */
        public abstract boolean cancelBurstCapture(CancelBurstCaptureCallback cd);

        public void setSensorSelfShotCallback(Handler handler,
                CameraSensorSelfShotCallback callback) {
        }

        /**
         * SPRD: Fix bug 591216 that add new feature 3d range finding, only support API2 currently @{
         * suites of native parameters can be get from captureCallback
         * 
         * @param rangeFindDistanceCallback receive message from native
         */
        public void set3DRangeFindDistanceCallback(
                CameraAgent.RangeFindDistanceCallback rangeFindDistanceCallback) {

        }

        /**
         * get capture callback set by camera app, then call its function
         * 
         * @return the CaptureCallback set by camera app, used to receive callback from native
         */
        public CameraAgent.RangeFindDistanceCallback getRangeFindDistanceCallback() {
            return null;
        }
        /* @} */

        public void setSurfaceViewPreviewCallback(CameraSurfaceViewPreviewCallback callback) {

        }

        public CameraSurfaceViewPreviewCallback getSurfaceViewPreviewCallback() {
            return null;
        }

        public boolean isNeedAFBeforeCapture() {
            return true;
        };

        public void onImageAvailableWithThumb(Image image, Handler handler, CameraPictureCallback jpeg) {}

        public void setHdrDetectionCallback(Handler handler, CameraAgent.CameraHdrDetectionCallback mCallback) {}
        public void setHdrDetectionWork(boolean work){};

        public void setAuto3DnrSceneDetectionCallback(Handler handler, CameraAgent.CameraIsAuto3DnrSceneDetectionCallback mCallback) {}
        public void setAuto3DnrSceneDetectionWork(boolean work){};

        public void setStartVideoCallback(CameraStartVideoCallback callback) {}

        public void setAiSceneCallback(Handler handler, CameraAgent.CameraAiSceneCallback mCallback) {}
        public void setAiSceneWork(boolean work){};

        public void setBlurCaptureCallback(Handler handler, CameraAgent.CameraBlurCaptureCallback mCallback) {}
        public CameraAgent.CameraBlurCaptureCallback getBlurCaptureCallback() {
            return null;
        }
    }

    public CameraStartVideoCallback getCameraStartVideoCallback() {
        return null;
    }
    protected void startRecoderRequest() {}
}
