
package com.android.ex.camera2.portability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import android.os.Environment;


import com.android.ex.camera2.portability.CameraAgent.CameraPictureCallback;
import com.android.ex.camera2.portability.debug.Log;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraCaptureSession;
import static android.hardware.camera2.CaptureRequest.SPRD_CAPTURE_MODE;

import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.media.ImageReader;
import android.media.Image;
import android.media.Image.Plane;
import android.os.Message;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

public class SprdAndroidCamera2AgentImpl extends AndroidCamera2AgentImpl {

    private static final Log.Tag TAG = new Log.Tag("SprdAndCam2AgntImp");
    /**
     * enum{
       BLUR_CAP_NOAI=0;
       BLUR_CAP_AI;
       }BLUR_CAPTURE_VERSION
     */
    public static final CaptureResult.Key<Integer> CONTROL_SPRD_BLUR_CAPTURE = new CaptureResult.Key<Integer>(
            "com.addParameters.sprd3BlurCapVersion", int.class);
    protected int mCanceledCaptureCount;//SPRD:fix bug 497854 when cancel 10 burst capture,the count of pics saveing is wrong
    private int mSensorSelfShotPreValue = -1;
    private int mLastFrameNumberOfFaces = 0;

    private boolean mNeedAfBeforeCapture = true;
    private boolean mHdrNormalChangeState = false;
    SprdAndroidCamera2AgentImpl(Context context) {
        super(context);
        mSprdAgentImpl = this;
        mCameraHandler = new SprdCamera2Handler(mCameraHandlerThread.getLooper());
        mExceptionHandler = new CameraExceptionHandler(mCameraHandler);
        mDispatchThread.setHandler(mCameraHandler);
    }

    /*
     * SPRD: fix bug 402668/400619 @{
     */
    public void recycle() {
        closeCamera(null, true);
        mDispatchThread.end();
    }

    /*
     * @}
     */

    public void closeCameraAsyncWithState() {
        try {
            getDispatchThread().runJob(new Runnable() {
                @Override
                public void run() {
                    if (getCameraState().getState() == SprdCameraStateHolder.CAMERA_WITH_THUMB) {
                        if (mCameraHandler instanceof SprdCamera2Handler) {
                            mCameraHandler.obtainMessage(CameraActions.RELEASE_FOR_THUMB).sendToTarget();
                        }
                        getCameraState().waitForStatesWithTimeout(AndroidCamera2StateHolder.CAMERA_PREVIEW_READY,
                                CameraAgent.WATI_THUMB_STATE_TIMEOUT);
                    }
                    getCameraHandler()
                            .obtainMessage(CameraActions.RELEASE)
                            .sendToTarget();
                }});
        } catch (final RuntimeException ex) {
            getCameraExceptionHandler().onDispatchThreadException(ex);
        }
    }

    @Override
    public void setCameraExceptionHandler(CameraExceptionHandler exceptionHandler) {
        mExceptionHandler = exceptionHandler != null ? exceptionHandler : sDefaultExceptionHandler;
    }

    /* SPRD: fix bug542668 NullPointerException @{ */
    protected static final CameraExceptionHandler sDefaultExceptionHandler =
            new CameraExceptionHandler(null) {
                @Override
                public void onCameraError(int errorCode) {
                    Log.w(TAG, "onCameraError called with no handler set: " + errorCode);
                }

                @Override
                public void onCameraException(RuntimeException ex, String commandHistory,
                        int action,
                        int state) {
                    Log.w(TAG, "onCameraException called with no handler set", ex);
                }

                @Override
                public void onDispatchThreadException(RuntimeException ex) {
                    Log.w(TAG, "onDispatchThreadException called with no handler set", ex);
                }
            };
    /* @} */

    public static class FaceDetectionCallbackForward implements CameraFaceDetectionCallback {
        private final Handler mHandler;
        private final CameraFaceDetectionCallback mCallback;
        private final CameraAgent.CameraProxy mCamera;

        public static FaceDetectionCallbackForward getNewInstance(Handler handler,
                CameraProxy camera, CameraFaceDetectionCallback cb) {
            if (handler == null || camera == null || cb == null)
                return null;
            return new FaceDetectionCallbackForward(handler, camera, cb);
        }

        private FaceDetectionCallbackForward(Handler h, CameraAgent.CameraProxy camera,
                CameraFaceDetectionCallback cb) {
            mHandler = h;
            mCamera = camera;
            mCallback = cb;
        }

        @Override
        public void onFaceDetection(final Camera.Face[] faces, final boolean faceChange) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onFaceDetection(faces, faceChange);
                }
            });
        }
    }

    public static class AiSceneCallbackForward implements SprdCameraAgent.CameraAiSceneCallback {
        private final Handler mHandler;
        private final SprdCameraAgent.CameraAiSceneCallback mCallback;

        public static AiSceneCallbackForward getNewInstance(Handler handler , SprdCameraAgent.CameraAiSceneCallback cb) {
            if (handler == null || cb == null)
                return null;
            return new AiSceneCallbackForward(handler , cb);
        }

        private AiSceneCallbackForward(Handler h , SprdCameraAgent.CameraAiSceneCallback cb) {
            mHandler = h;
            mCallback = cb;
        }

        @Override
        public void onAiScene(int sceneType) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onAiScene(sceneType);
                }
            });
        }
    }

    public void monitorControlStatesAiScene(CaptureResult result,
                                            AndroidCamera2ProxyImpl camera2Proxy) {
        Integer aiScene = result.get(CaptureResult.CONTROL_SPRD_AI_SCENE);

        if (camera2Proxy instanceof SprdAndroidCamera2ProxyImpl) {
            if (((SprdAndroidCamera2ProxyImpl) camera2Proxy).getAiSceneStatus() &&
                    mCameraHandler instanceof SprdCamera2Handler) {
                SprdCamera2Handler handler = (SprdCamera2Handler)mCameraHandler;
                if (handler.getAiSceneCallback() != null) {
                    handler.getAiSceneCallback().onAiScene(aiScene);
                }
            }
        }
    }

    public static class Auto3DnrSceneDetectionCallbackForward implements CameraIsAuto3DnrSceneDetectionCallback {
        private final Handler mHandler;
        private final CameraIsAuto3DnrSceneDetectionCallback mCallback;

        public static Auto3DnrSceneDetectionCallbackForward getNewInstance(Handler handler , CameraIsAuto3DnrSceneDetectionCallback cb) {
            if (handler == null || cb == null)
                return null;
            return new Auto3DnrSceneDetectionCallbackForward(handler, cb);
        }

        private Auto3DnrSceneDetectionCallbackForward(Handler h,CameraIsAuto3DnrSceneDetectionCallback cb) {
            mHandler = h;
            mCallback = cb;
        }
        @Override
        public void onIsAuto3DnrSceneDetection(boolean isAuto3DnrScene) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onIsAuto3DnrSceneDetection(isAuto3DnrScene);
                }
            });
        }

    }

    public void monitorControlStatesAuto3DnrSceneDetect(CaptureResult result,
            AndroidCamera2ProxyImpl camera2Proxy){
        Integer isAuto3Dnr = result.get(CaptureResult.ANDROID_SPRD_IS_3DNR_SCENE);
        if (camera2Proxy instanceof  SprdAndroidCamera2ProxyImpl) {
            if (((SprdAndroidCamera2ProxyImpl) camera2Proxy).getAuto3DnrDetectionStatus() &&
                    mCameraHandler instanceof SprdCamera2Handler) {
                SprdCamera2Handler handler = (SprdCamera2Handler) mCameraHandler;
                if (handler.getAuto3DnrDetectionCallback() != null) {
                     handler.getAuto3DnrDetectionCallback().onIsAuto3DnrSceneDetection(isAuto3Dnr != 0);
                }
           }
        }
   }

    public static class HdrDetectionCallbackForward implements CameraHdrDetectionCallback {
        private final Handler mHandler;
        private final CameraHdrDetectionCallback mCallback;

        public static HdrDetectionCallbackForward getNewInstance(Handler handler , CameraHdrDetectionCallback cb) {
            if (handler == null || cb == null)
                return null;
            return new HdrDetectionCallbackForward(handler, cb);
        }

        private HdrDetectionCallbackForward(Handler h,CameraHdrDetectionCallback cb) {
            mHandler = h;
            mCallback = cb;
        }
        @Override
        public void onHdrDetection(boolean isHdrScene) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onHdrDetection(isHdrScene);
                }
            });
        }

    }

    /* SPRD:fix bug947586 face blur cost much time @{ */
    public static class BlurCaptureCallbackForward implements SprdCameraAgent.CameraBlurCaptureCallback {
        private final Handler mHandler;
        private final SprdCameraAgent.CameraBlurCaptureCallback mCallback;

        public static BlurCaptureCallbackForward getNewInstance(Handler handler , SprdCameraAgent.CameraBlurCaptureCallback cb) {
            if (handler == null || cb == null)
                return null;
            return new BlurCaptureCallbackForward(handler , cb);
        }

        private BlurCaptureCallbackForward(Handler h , SprdCameraAgent.CameraBlurCaptureCallback cb) {
            mHandler = h;
            mCallback = cb;
        }

        @Override
        public void onBlurCapture() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onBlurCapture();
                }
            });
        }
    }

    private int mLastBlurCapture = -1;
    public void monitorControlStatesBlurCapture(CaptureResult result,
                                            AndroidCamera2ProxyImpl camera2Proxy) {
        Integer blurCapture = result.get(CONTROL_SPRD_BLUR_CAPTURE);

        if (blurCapture != null) {
            int currentblurCapture = blurCapture;
            boolean blurCaptureChange = currentblurCapture != mLastBlurCapture;
            mLastBlurCapture = currentblurCapture;

            if (blurCaptureChange && currentblurCapture == 1 && camera2Proxy != null && camera2Proxy.getBlurCaptureCallback() != null) {
                camera2Proxy.getBlurCaptureCallback().onBlurCapture();
            }
        }
    }
    /* @} */

    public void monitorControlStatesHdrDetect(CaptureResult result,
                                              AndroidCamera2ProxyImpl camera2Proxy){
        Integer isHdr = result.get(CaptureResult.ANDROID_SPRD_IS_HDR_SCENE);
        if (camera2Proxy instanceof  SprdAndroidCamera2ProxyImpl) {
            if (((SprdAndroidCamera2ProxyImpl) camera2Proxy).getHdrDetectionStatus() &&
                    mCameraHandler instanceof SprdCamera2Handler) {
                SprdCamera2Handler handler = (SprdCamera2Handler) mCameraHandler;
                if (handler.getHdrDetectionCallback() != null) {
                    handler.getHdrDetectionCallback().onHdrDetection(isHdr != 0);
                }
            }
        }

    }

    public void monitorControlStatesAIDetect(CaptureResult result,
            AndroidCamera2ProxyImpl cameraProxy,
            Rect activeArray) {
        Integer faceState = result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE);
        if (faceState != null) {
            int faceStateMaybe = faceState;
            switch (faceStateMaybe) {
                case CaptureResult.STATISTICS_FACE_DETECT_MODE_SIMPLE:
                    android.hardware.camera2.params.Face[] faces = result
                            .get(CaptureResult.STATISTICS_FACES);
                    /* SPRD:fix bug689227 should not callback when has not face info @{ */
                    if (faces.length == 0 && mLastFrameNumberOfFaces == 0) {
                        break;
                    }
                    Camera.Face[] cFaces = new Camera.Face[faces.length];
                    boolean faceChange = mLastFrameNumberOfFaces != faces.length;//SPRD:fix bug967653
                    mLastFrameNumberOfFaces = faces.length;
                    /* @} */
                    for (int i = 0; i < faces.length; i++) {
                        Camera.Face face = new Camera.Face();
                        face.score = faces[i].getScore();
                        face.rect = faceForConvertCoordinate(activeArray, faces[i].getBounds());
                        cFaces[i] = face;
                    }
                    if (mCameraHandler instanceof SprdCamera2Handler) {
                        SprdCamera2Handler handler = (SprdCamera2Handler) mCameraHandler;
                        if (handler.getFaceDetectionListener() != null) {
                            handler.getFaceDetectionListener().onFaceDetection(cFaces, faceChange);
                        }
                    }

                    break;
            }
        }
        Integer blurCoveredValue = result.get(CaptureResult.CONTROL_SPRD_BLUR_COVERED);
        if (mCameraHandler instanceof SprdCamera2Handler) {
            SprdCamera2Handler handler = (SprdCamera2Handler) mCameraHandler;
            if (blurCoveredValue != null && handler.getSensorSelfListener() != null) {
                int value = blurCoveredValue;
                if (value != mSensorSelfShotPreValue) {
                    handler.getSensorSelfListener().onSensorSelfShot(
                            value == 2 || value == 6 || value == 50 || value == 51 || value == 52
                                    || value == 53 || value == 54 || value == 55, value);
                    mSensorSelfShotPreValue = value;
                }
            }
        }

        Integer needAfValue = result.get(CaptureResult.CONTROL_SPRD_NEED_AF_BEFORE_CAPTURE);
        if (needAfValue != null && needAfValue == 0) {
            mNeedAfBeforeCapture = false;
        } else {
            mNeedAfBeforeCapture = true;
        }
    }

    /* SPRD: Fix bug 591216 that add new feature 3d range finding, only support API2 currently @{ */
    public void monitorControlStatesRangeFind(CaptureResult result,
            AndroidCamera2ProxyImpl cameraProxy) {
        try {
            if (cameraProxy != null && cameraProxy.getRangeFindDistanceCallback() != null) {
                Double[] distanceResult = result.get(CaptureResult.CONTROL_SPRD_3D_RANGE_FIND_DISTANCE);
                if (distanceResult != null) {
                    cameraProxy.getRangeFindDistanceCallback()
                            .onRangeFindDistanceReceived(distanceResult[1].intValue(), distanceResult[0]);
                }
            }
        } catch (IllegalArgumentException e) {
        }
    }
    /* @} */

    public void monitorSurfaceViewPreviewUpdate(AndroidCamera2ProxyImpl cameraProxy) {
        if (cameraProxy != null && cameraProxy.getSurfaceViewPreviewCallback() != null) {
            cameraProxy.getSurfaceViewPreviewCallback().onSurfaceUpdate();
        }
    }

    public Rect faceForConvertCoordinate(Rect activeArray, Rect rect) {
        if (activeArray == null) {
            return null;
        }
        int sensorWidth = activeArray.width();
        int sendorHeight = activeArray.height();
        int left = rect.left * 2000 / sensorWidth - 1000;
        int top = rect.top * 2000 / sendorHeight - 1000;
        int right = rect.right * 2000 / sensorWidth - 1000;
        int bottom = rect.bottom * 2000 / sendorHeight - 1000;
        return new Rect(left, top, right, bottom);
    }

    public class SprdCamera2Handler extends AndroidCamera2AgentImpl.Camera2Handler {
        private FaceDetectionCallbackForward mFaceDetectionCallback;
        private SensorSelfShotCallbackForward mSensorSelfShotCallback;
        private HdrDetectionCallbackForward mHdrDetectionCallback;
        private AiSceneCallbackForward mAiSceneCallback;
        private Auto3DnrSceneDetectionCallbackForward mAuto3DnrDetectionCallback;

        SprdCamera2Handler(Looper looper) {
            super(looper);
        }

        public void setFaceDetectionListener(FaceDetectionCallbackForward listener) {
            mFaceDetectionCallback = listener;
        }

        private void setSensorSelfShotListener(SensorSelfShotCallbackForward listener) {
            mSensorSelfShotCallback = listener;
        }

        public FaceDetectionCallbackForward getFaceDetectionListener() {
            return mFaceDetectionCallback;
        }

        public SensorSelfShotCallbackForward getSensorSelfListener() {
            return mSensorSelfShotCallback;
        }

        public void setAiSceneListener(AiSceneCallbackForward listener) {
            mAiSceneCallback = listener;
        }

        public AiSceneCallbackForward getAiSceneCallback() {
            return mAiSceneCallback;
        }

        public void setHdrDetectionListener(HdrDetectionCallbackForward listener){
            mHdrDetectionCallback = listener;
        }

        public HdrDetectionCallbackForward getHdrDetectionCallback(){
            return mHdrDetectionCallback;
        }

        public void setAuto3DnrDetectionListener(Auto3DnrSceneDetectionCallbackForward listener){
            mAuto3DnrDetectionCallback = listener;
        }

        public Auto3DnrSceneDetectionCallbackForward getAuto3DnrDetectionCallback(){
            return mAuto3DnrDetectionCallback;
        }

        @Override
        public void handleMessage(final Message msg) {
            super.handleMessage(msg);
            /*
             * SPRD: Fix bug 666647 that optimize log
             */
            if (superHandled()) {
                return;
            }
            /* @} */
            int cameraAction = msg.what;
            try {
                switch (cameraAction) {
                    case CameraActions.RELEASE_FOR_THUMB: {
                        releaseForThumb();
                        break;
                    }
                    case CameraActions.SET_PREVIEW_DISPLAY_ASYNC: {
                        if(!reconnect){
                            setPreviewDisplay((SurfaceHolder) msg.obj);
                            break;
                        }
                     // fall through
                        Log.i(TAG, "reconnecting, forward to SET_PREVIEW_DISPLAY_ASYNC_WITHOUT_OPTIMIZE");
                        reconnect = false;
                    }
                    case CameraActions.SET_PREVIEW_DISPLAY_ASYNC_WITHOUT_OPTIMIZE: {
                        setPreviewDisplayWithoutOptimize((SurfaceHolder) msg.obj);
                        break;
                    }
                    case CameraActions.SET_FACE_DETECTION_LISTENER: {
                        setFaceDetectionListener((FaceDetectionCallbackForward) msg.obj);
                        break;
                    }

                    case CameraActions.START_FACE_DETECTION: {
                        boolean isFace = mPersistentSettings.set(
                                CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE);
                        mLastFrameNumberOfFaces = 0;
                        break;
                    }

                    case CameraActions.STOP_FACE_DETECTION: {
                        boolean isFace = mPersistentSettings.set(
                                CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF);
                        mLastFrameNumberOfFaces = 0;
                        break;
                    }

                    case CameraActions.SET_SENSOR_SELF_SHOT_LISTENER: {
                        setSensorSelfShotListener((SensorSelfShotCallbackForward) msg.obj);
                        break;
                    }

                    /* SPRD: fix bug 473462 add burst capture @{ */
                    case CameraActions.CAPTURE_BURST_PHOTO: {
                        int num = mPersistentSettings.get(SPRD_CAPTURE_MODE);
                        Log.i(TAG, "CameraActions.CAPTURE_BURST_PHOTO num=" + num);
                        List<CaptureRequest> requests = new ArrayList<CaptureRequest>();
                        final CaptureAvailableListener listener = (CaptureAvailableListener) msg.obj;
                        mCaptureReader.setOnImageAvailableListener(listener, /* handler */this);

                        for (int i = 0; i < num; i++) {
                            Log.i(TAG, "CameraActions.CAPTURE_BURST_PHOTO i=" + i);
                            CaptureRequest request = mPersistentSettings.createRequest(
                                    mCamera, CameraDevice.TEMPLATE_STILL_CAPTURE,
                                    mPreviewSurface, mCaptureReader.getSurface());
                            requests.add(request);
                        }

                        Log.i(TAG, "mSession.captureBurst size " + requests.size());
                        mSession.captureBurst(requests, listener, /* mHandler */this);

                        break;
                    }

                    // SPRD : Add for bug 657472 Save normal hdr picture
                    case CameraActions.CAPTURE_HDR_PHOTO: {
                        // int num = mPersistentSettings.get(CaptureRequest.SPRD_CAPTURE_MODE);
                        // Log.i(TAG,"CameraActions.CAPTURE_BURST_PHOTO num="+num);

                        final CaptureAvailableListener listener =
                                (CaptureAvailableListener) msg.obj;
                        mCaptureReader.setOnImageAvailableListener(listener, /*handler*/this);
                        if (mNeedThumb) {
                            mThumbnailReader.setOnImageAvailableListener(listener, /*handler*/this);
                            List<CaptureRequest> requests = new ArrayList<CaptureRequest>();
                            CaptureRequest request = mPersistentSettings.createRequest(mCamera,
                                    CameraDevice.TEMPLATE_STILL_CAPTURE,
                                    mCaptureReader.getSurface(), mThumbnailReader.getSurface());
                            requests.add(request);
                            request = mPersistentSettings.createRequest(mCamera,
                                    CameraDevice.TEMPLATE_STILL_CAPTURE,
                                    mCaptureReader.getSurface());
                            requests.add(request);
                            try {
                                mSession.captureBurst(requests, listener, /* handler */this);
                            } catch (CameraAccessException ex) {
                                Log.e(TAG, "Unable to initiate immediate capture", ex);
                            }
                            mHdrNormalChangeState = false;
                        } else {
                            for (int i = 0; i < 2; i++) {
                                CaptureRequest request = mPersistentSettings.createRequest(mCamera,
                                        CameraDevice.TEMPLATE_STILL_CAPTURE, mPreviewSurface,
                                        mCaptureReader.getSurface());
                                try {
                                    mSession.capture(request, listener, /* handler */this);
                                } catch (CameraAccessException ex) {
                                    Log.e(TAG, "Unable to initiate immediate capture", ex);
                                }
                            }
                        }
                        break;
                    }

                    case CameraActions.CAPTURE_PHOTO_WITH_THUMB:{
                        if (mCameraState.getState() < AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE) {
                            Log.e(TAG, "Photos may only be taken when a preview is active");
                            break;
                        }
                        if (mCameraState.getState() != AndroidCamera2StateHolder.CAMERA_FOCUS_LOCKED) {
                            Log.w(TAG, "Taking a (likely blurry) photo without the lens locked");
                        }

                        final CaptureAvailableListener listener = (CaptureAvailableListener) msg.obj;

                        mCaptureReader.setOnImageAvailableListener(listener, /*handler*/this);
                        mThumbnailReader.setOnImageAvailableListener(listener, /*handler*/this);
                        try {
                            mSession.capture(
                                    mPersistentSettings.createRequest(mCamera,
                                            CameraDevice.TEMPLATE_STILL_CAPTURE,
                                            mCaptureReader.getSurface(), mThumbnailReader.getSurface()),
                                    listener, /*handler*/this);
                        } catch (CameraAccessException ex) {
                            Log.e(TAG, "Unable to initiate immediate capture", ex);
                        }
                        break;
                    }

                    case CameraActions.CANCEL_CAPTURE_BURST_PHOTO: {
                        Log.i(TAG,"CameraActions.CANCEL_CAPTURE_BURST_PHOTO");
                        //mSession.abortCaptures();
                        /*
                         * SPRD:fix bug 497854 when cancel 10 burst capture,the count of pics
                         * saveing is wrong @{
                         */
                        CancelBurstCaptureCallback cb = (CancelBurstCaptureCallback) msg.obj;
                        /*
                         * SPRD:fix bug 644311 cancel picture should not called when cancel burst if
                         * (mSession instanceof CameraCaptureSessionEx) { mCanceledCaptureCount =
                         * ((CameraCaptureSessionEx)mSession).cancelPicture(); }
                         */
                        cb.onCanceled(mBurstHasCaptureCount);
                        /* @} */

                        /*
                         * SPRD: Fix bug 672886 that not enough photo is saved though we have taken
                         * 99 pictures
                         */
                        mBurstCaptureCanceled = true;
                        mBurstMaxCaptureCount = 0;
                        mBurstHasCaptureCount = 0;
                        /* @} */
                        break;
                    }
                    case CameraActions.SET_PREVIEW_TEXTURE_ASYNC_WITHOUT_OPTIMIZE: {
                        setPreviewTextureWithoutOptimize((SurfaceTexture) msg.obj);
                        break;
                    }

                    case CameraActions.STOP_PREVIEW_WITHOUT_FLUSH: {
                        if (mCameraState.getState() < AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE) {
                            Log.w(TAG, "Refusing to stop preview at inappropriate time");
                            break;
                        }
                        try {
                            mSession.stopRepeating();
                            mCameraProxy.getSettings().setSizesLocked(false);
                            changeState(AndroidCamera2StateHolder.CAMERA_PREVIEW_READY);
                        } catch (CameraAccessException ex) {
                            Log.w(TAG, "Unable to stop preview", ex);
                            throw new RuntimeException("Unimplemented CameraProxy message="
                                    + msg.what);
                        }
                        break;
                    }

                    case SprdCameraActions.SET_AI_SCENE_LISTENER: {
                        setAiSceneListener((AiSceneCallbackForward)msg.obj);
                        break;
                    }

                    case SprdCameraActions.SET_HDR_SCENE_LISTENER: {
                        setHdrDetectionListener((HdrDetectionCallbackForward) msg.obj);
                        break;
                    }

                    case SprdCameraActions.SET_AUTO3DNR_SCENE_LISTENER: {
                        setAuto3DnrDetectionListener((Auto3DnrSceneDetectionCallbackForward) msg.obj);
                        break;
                    }

                    case CameraActions.START_VIDEO_RECORDER: {
                        if (msg.arg1 > 0) {
                            setVideoRecoderSurface((Surface) msg.obj);
                        } else {
                            setVideoRecoderSurfaceTexture((Surface) msg.obj);
                        }
                        break;
                    }

                    case CameraActions.CAPTURE_PHOTO_WITH_SNAP:{
                        if (mCameraState.getState() < AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE) {
                            Log.e(TAG, "Photos may only be taken when a preview is active");
                            break;
                        }
                        if (mCameraState.getState() != AndroidCamera2StateHolder.CAMERA_FOCUS_LOCKED) {
                            Log.w(TAG, "Taking a (likely blurry) photo without the lens locked");
                        }

                        final CaptureAvailableListener listener = (CaptureAvailableListener) msg.obj;

                        mCaptureReader.setOnImageAvailableListener(listener, /*handler*/this);
                        try {
                            mSession.capture(
                                    mPersistentSettings.createRequest(mCamera,
                                            CameraDevice.TEMPLATE_VIDEO_SNAPSHOT,
                                            mCaptureReader.getSurface()),
                                    listener, /*handler*/this);
                        } catch (CameraAccessException ex) {
                            Log.e(TAG, "Unable to initiate immediate capture", ex);
                        }
                        break;
                    }

                    default: {
                        // TODO: Rephrase once everything has been implemented
                        //throw new RuntimeException("Unimplemented CameraProxy message=" + msg.what);
                    }
                }
            } catch (Exception ex) {
                Log.i(TAG, "Unable to run " + cameraAction, ex);
            }
        }

        public CameraSettings buildSettings(AndroidCamera2Capabilities caps) {
            try {
                int template = CameraDevice.TEMPLATE_PREVIEW;
                if (mIsVideMode) {
                    template = CameraDevice.TEMPLATE_RECORD;
                }
                return new SprdAndroidCamera2Settings(mCamera, template,
                        mActiveArray, mPreviewSize, mPhotoSize);
            } catch (CameraAccessException ex) {
                Log.e(TAG, "Unable to query camera device to build settings representation");
                return null;
            }
        }

        protected void setPreviewTextureWithoutOptimize(SurfaceTexture surfaceTexture) {
            // TODO: Must be called after providing a .*Settings populated with sizes
            // TODO: We don't technically offer a selection of sizes tailored to SurfaceTextures!

            // TODO: Handle this error condition with a callback or exception
            if (mCameraState.getState() < AndroidCamera2StateHolder.CAMERA_CONFIGURED) {
                Log.w(TAG, "Ignoring texture setting at inappropriate time");
                return;
            }

            // Avoid initializing another capture session unless we absolutely have to
            /*
             * if (surfaceTexture == mPreviewTexture) { Log.i(TAG,
             * "Optimizing out redundant preview texture setting"); return; }
             */

            if (mSession != null) {
                closePreviewSession();
            }

            mPreviewTexture = surfaceTexture;
            surfaceTexture.setDefaultBufferSize(mPreviewSize.width(), mPreviewSize.height());

            if (mPreviewSurface != null) {
                mPreviewSurface.release();
            }
            mPreviewSurface = new Surface(surfaceTexture);

            if (mCaptureReader != null) {
                mCaptureReader.close();
            }
            mCaptureReader = ImageReader.newInstance(
                    mPhotoSize.width(), mPhotoSize.height(), ImageFormat.JPEG, 1);

            if (mThumbnailReader != null) {
                mThumbnailReader.close();
            }

            if (mPreviewReader != null) {
                mPreviewReader.close();
            }

            try {
                if (mNeedThumb) {
                    mThumbnailReader = ImageReader.newInstance(
                            mThumbnailSize.width(), mThumbnailSize.height(), ImageFormat.YUV_420_888, 1);
                    mCamera.createCaptureSession(
                            Arrays.asList(mPreviewSurface, mCaptureReader.getSurface(), mThumbnailReader.getSurface()),
                            mCameraPreviewStateCallback, this);
                } else if (mPreviewCallback != null) {
                    mPreviewReader = ImageReader.newInstance(
                            mPreviewSize.width(), mPreviewSize.height(), ImageFormat.YUV_420_888, 1);
                    mCamera.createCaptureSession(
                            Arrays.asList(mPreviewSurface, mPreviewReader.getSurface(), mCaptureReader.getSurface()),
                            mCameraPreviewStateCallback, this);
                } else {
                    mCamera.createCaptureSession(
                            Arrays.asList(mPreviewSurface, mCaptureReader.getSurface()),
                            mCameraPreviewStateCallback, this);
                }
            } catch (CameraAccessException ex) {
                Log.e(TAG, "Failed to create camera capture session", ex);
            }
        }

        protected void setVideoRecoderSurface(Surface mediaSurface) {
            if (mCameraState.getState() < AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE) {
                Log.e(TAG, "start video recoding when a preview is inactive");
                return;
            }
            closeVideoPreviewSession();
            mMediaRecoderSurface = mediaSurface;
            List<OutputConfiguration> outConfigurations = new ArrayList<>();
            OutputConfiguration surfaceViewOutputConfiguration= new OutputConfiguration(new Size(mPreviewSize.width(), mPreviewSize.height()), SurfaceHolder.class);
            surfaceViewOutputConfiguration.addSurface(mPreviewSurface);
            surfaceViewOutputConfiguration.enableSprd();
            outConfigurations.add(surfaceViewOutputConfiguration);
            OutputConfiguration mediaOutputConfiguration = new OutputConfiguration(mMediaRecoderSurface);
            if (mIsEISenable) {
                mediaOutputConfiguration.enableEIS(true);
            }
            outConfigurations.add(mediaOutputConfiguration);
            outConfigurations.add(new OutputConfiguration(mCaptureReader.getSurface()));
            try {
                mCamera.createCaptureSessionByOutputConfigurations(
                        outConfigurations,
                        mCameraPreviewStateCallback, this);
            } catch (CameraAccessException ex) {
                Log.e(TAG, "Failed to create camera capture session", ex);
            }
        }

        protected void setVideoRecoderSurfaceTexture(Surface mediaSurface) {
            if (mCameraState.getState() < AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE) {
                Log.e(TAG, "start video recoding when a preview is inactive");
                return;
            }
            closeVideoPreviewSession();
            mMediaRecoderSurface = mediaSurface;
            mPreviewTexture.setDefaultBufferSize(mPreviewSize.width(), mPreviewSize.height());

            if (mPreviewSurface != null) {
                mPreviewSurface.release();
            }
            mPreviewSurface = new Surface(mPreviewTexture);
            try {
                mCamera.createCaptureSession(
                        Arrays.asList(mPreviewSurface, mMediaRecoderSurface, mCaptureReader.getSurface()),
                        mCameraPreviewStateCallback, this);
            } catch (CameraAccessException ex) {
                Log.e(TAG, "Failed to create camera capture session", ex);
            }
        }

        protected CameraCaptureSession.StateCallback mCameraStartVideoCallback =
                new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                mSession = session;
                setRecoderRepeatingRequest();
                if (getCameraStartVideoCallback() != null) {
                    getCameraStartVideoCallback().onVideoStart();
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                // TODO: Invoke a callback
                Log.e(TAG, "Failed to configure the camera for capture");
            }

        };

        protected void setRecoderRepeatingRequest() {
            if (mCamera == null) {
                Log.e(TAG, "mCamera is null, can not start video request");
                return;
            }
            try {
                mSession.setRepeatingRequest(
                        mPersistentSettings.createRequest(mCamera,
                                CameraDevice.TEMPLATE_RECORD, mPreviewSurface, mMediaRecoderSurface),
                        null, /*handler*/this);
                changeState(SprdCameraStateHolder.CAMERA_RECODERING);
            } catch(CameraAccessException ex) {
                Log.w(TAG, "Unable to start Recoder", ex);
            }
        }

        protected void closeVideoPreviewSession() {
            if (mSession != null) {
                mSession.close();
                mSession = null;
                changeState(AndroidCamera2StateHolder.CAMERA_CONFIGURED);
            }
        }
        protected void releaseForThumb() {
            if (mPreviewSurface != null) {
                mPreviewSurface.release();
                mPreviewSurface = null;
            }
            if (mCameraState.getState() <
                    AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE) {
                Log.w(TAG, "Refusing to stop preview at inappropriate time");
                return;
            }
            try {
                mSession.stopRepeating();
                mCameraProxy.getSettings().setSizesLocked(false);
            }catch (CameraAccessException ex) {
                Log.w(TAG,"Unable to stop preview", ex );
                throw new RuntimeException("Unimplemented CameraProxy message=");
            }
        }

        protected void setPreviewDisplay(SurfaceHolder surfaceHolder) {
            // TODO: Must be called after providing a .*Settings populated with sizes
            // TODO: We don't technically offer a selection of sizes tailored to SurfaceTextures!

            // TODO: Handle this error condition with a callback or exception
            if (mCameraState.getState() < AndroidCamera2StateHolder.CAMERA_CONFIGURED) {
                Log.w(TAG, "Ignoring texture setting at inappropriate time");
                return;
            }

            // Avoid initializing another capture session unless we absolutely have to
            if (surfaceHolder == mSurfaceHolder) {
                Log.i(TAG, "Optimizing out redundant preview texture setting");
                return;
            }

            if (mSession != null) {
                closePreviewSession();
            }

            mSurfaceHolder = surfaceHolder;

            if (mPreviewSurface != null) {
                mPreviewSurface.release();
            }
            mPreviewSurface = mSurfaceHolder.getSurface();
            if (mCaptureReader != null) {
                mCaptureReader.close();
            }
            mCaptureReader = ImageReader.newInstance(
                    mPhotoSize.width(), mPhotoSize.height(), ImageFormat.JPEG, 1);

            if (mThumbnailReader != null) {
                mThumbnailReader.close();
            }

            if (mPreviewReader != null) {
                mPreviewReader.close();
            }
            List<OutputConfiguration> outConfigurations = new ArrayList<>();
//            outConfigurations.add(new OutputConfiguration(mPreviewSurface, mPreviewSize.width(), mPreviewSize.height()));
            OutputConfiguration surfaceViewOutputConfiguration= new OutputConfiguration(new Size(mPreviewSize.width(), mPreviewSize.height()), SurfaceHolder.class);
            surfaceViewOutputConfiguration.addSurface(mPreviewSurface);
            surfaceViewOutputConfiguration.enableSprd();
            outConfigurations.add(surfaceViewOutputConfiguration);
            outConfigurations.add(new OutputConfiguration(mCaptureReader.getSurface()));
            if (mNeedThumb) {
                mThumbnailReader = ImageReader.newInstance(
                        mThumbnailSize.width(), mThumbnailSize.height(), ImageFormat.YUV_420_888, 1);
                outConfigurations.add(new OutputConfiguration(mThumbnailReader.getSurface()));
            }
            if (mPreviewCallback != null) {
                mPreviewReader = ImageReader.newInstance(
                        mPreviewSize.width(), mPreviewSize.height(), ImageFormat.YUV_420_888, 1);
                outConfigurations.add(new OutputConfiguration(mPreviewReader.getSurface()));
            }
            try {
                mCamera.createCaptureSessionByOutputConfigurations(
                        outConfigurations,
                        mCameraPreviewStateCallback, this);
            } catch (CameraAccessException ex) {
                Log.e(TAG, "Failed to create camera capture session", ex);
            }
        }

        protected void setPreviewDisplayWithoutOptimize(SurfaceHolder surfaceHolder) {
            // TODO: Must be called after providing a .*Settings populated with sizes
            // TODO: We don't technically offer a selection of sizes tailored to SurfaceTextures!

            // TODO: Handle this error condition with a callback or exception
            if (mCameraState.getState() < AndroidCamera2StateHolder.CAMERA_CONFIGURED) {
                Log.w(TAG, "Ignoring texture setting at inappropriate time");
                return;
            }

            if (mSession != null) {
                closePreviewSession();
            }

            mSurfaceHolder = surfaceHolder;
            mPreviewSurface = mSurfaceHolder.getSurface();

            if (mCaptureReader != null) {
                mCaptureReader.close();
            }
            mCaptureReader = ImageReader.newInstance(
                    mPhotoSize.width(), mPhotoSize.height(), ImageFormat.JPEG, 1);

            if (mThumbnailReader != null) {
                mThumbnailReader.close();
            }

            if (mPreviewReader != null) {
                mPreviewReader.close();
            }
            List<OutputConfiguration> outConfigurations = new ArrayList<>();
//            outConfigurations.add(new OutputConfiguration(mPreviewSurface, mPreviewSize.width(), mPreviewSize.height()));
            OutputConfiguration surfaceViewOutputConfiguration= new OutputConfiguration(new Size(mPreviewSize.width(), mPreviewSize.height()), SurfaceHolder.class);
            surfaceViewOutputConfiguration.addSurface(mPreviewSurface);
            surfaceViewOutputConfiguration.enableSprd();
            outConfigurations.add(surfaceViewOutputConfiguration);
            outConfigurations.add(new OutputConfiguration(mCaptureReader.getSurface()));
            if (mNeedThumb) {
                mThumbnailReader = ImageReader.newInstance(
                        mThumbnailSize.width(), mThumbnailSize.height(), ImageFormat.YUV_420_888, 1);
                outConfigurations.add(new OutputConfiguration(mThumbnailReader.getSurface()));
            }
            if (mPreviewCallback != null) {
                mPreviewReader = ImageReader.newInstance(
                        mPreviewSize.width(), mPreviewSize.height(), ImageFormat.YUV_420_888, 1);
                outConfigurations.add(new OutputConfiguration(mPreviewReader.getSurface()));
            }
            try {
                mCamera.createCaptureSessionByOutputConfigurations(
                        outConfigurations,
                        mCameraPreviewStateCallback, this);
            } catch (CameraAccessException ex) {
                Log.e(TAG, "Failed to create camera capture session", ex);
            }
        }

        /**
         * This method process the callback of camera status result in super class
         * 
         * @param result capture result
         */
        @Override
        protected void onMonitorControlStates(CaptureResult result) {
            monitorControlStatesAIDetect(result, mCameraProxy, mActiveArray);
            monitorControlStatesRangeFind(result, mCameraProxy);
            monitorSurfaceViewPreviewUpdate(mCameraProxy);
            monitorControlStatesHdrDetect(result,mCameraProxy);
            monitorControlStatesAiScene(result, mCameraProxy);
            monitorControlStatesBlurCapture(result, mCameraProxy);
            monitorControlStatesAuto3DnrSceneDetect(result, mCameraProxy);
        }
    }

    protected class SprdAndroidCamera2ProxyImpl extends
            AndroidCamera2AgentImpl.AndroidCamera2ProxyImpl {

        // SPRD: Fix bug 591216 that add new feature 3d range finding, only support API2 currently
        private RangeFindDistanceCallback mRangeFindDistanceCallback;
        private boolean isHdrDetectionWork = false;
        private int mLastCameraState = 16;//CAMERA_PREVIEW_ACTIVE
        private boolean isAiSceneWork = false;
        private boolean isAuto3DnrDetectionWork = false;

        public SprdAndroidCamera2ProxyImpl(
                AndroidCamera2AgentImpl agent,
                int cameraIndex,
                CameraDevice camera,
                CameraDeviceInfo.Characteristics characteristics,
                CameraCharacteristics properties) {
            super(agent, cameraIndex, camera, characteristics, properties);
        }

        @Override
        public void setFaceDetectionCallback(Handler handler, CameraFaceDetectionCallback callback) {
            mCameraHandler.obtainMessage(
                    CameraActions.SET_FACE_DETECTION_LISTENER,
                    FaceDetectionCallbackForward.getNewInstance(handler,
                            SprdAndroidCamera2ProxyImpl.this, callback)).sendToTarget();
        }

        @Override
        public void setSensorSelfShotCallback(Handler handler, CameraSensorSelfShotCallback callback) {
            mCameraHandler.obtainMessage(
                    CameraActions.SET_SENSOR_SELF_SHOT_LISTENER,
                    SensorSelfShotCallbackForward.getNewInstance(handler,
                            callback)).sendToTarget();
        }

        @Override
        public void startFaceDetection() {
            mCameraHandler.sendEmptyMessage(CameraActions.START_FACE_DETECTION);
        }

        @Override
        public void stopFaceDetection() {
            mCameraHandler.sendEmptyMessage(CameraActions.STOP_FACE_DETECTION);
        }

        /* SPRD:fix bug 497854 when cancel 10 burst capture,the count of pics saveing is wrong {@ */
        @Override
        public boolean cancelBurstCapture(CancelBurstCaptureCallback cb) {
            Log.i(TAG, "cancelBurstCapture");
            boolean hasRemoved = super.cancelBurstCapture(cb);
            getCameraHandler().sendMessageAtFrontOfQueue(
                    getCameraHandler().obtainMessage(CameraActions.CANCEL_CAPTURE_BURST_PHOTO, cb));
            return hasRemoved;
        }
        /* @} */

        /*
         * SPRD: Fix bug 591216 that add new feature 3d range finding, only support API2 currently
         * @{
         */
        @Override
        public void set3DRangeFindDistanceCallback(
                RangeFindDistanceCallback rangeFindDistanceCallback) {
            mRangeFindDistanceCallback = rangeFindDistanceCallback;
        }

        @Override
        public RangeFindDistanceCallback getRangeFindDistanceCallback() {
            return mRangeFindDistanceCallback;
        }
        /* @} */

        private CameraSurfaceViewPreviewCallback mSurfaceViewPreviewCallback;

        @Override
        public void setSurfaceViewPreviewCallback(CameraSurfaceViewPreviewCallback callback) {
            mSurfaceViewPreviewCallback = callback;
        }

        @Override
        public CameraSurfaceViewPreviewCallback getSurfaceViewPreviewCallback() {
            return mSurfaceViewPreviewCallback;
        }

        private BlurCaptureCallbackForward mBlurCaptureCallback;

        public void setBlurCaptureCallback(Handler handler, CameraBlurCaptureCallback cb) {
            mBlurCaptureCallback = BlurCaptureCallbackForward.getNewInstance(handler, cb);
        }

        public BlurCaptureCallbackForward getBlurCaptureCallback() {
            return mBlurCaptureCallback;
        }

        public void setPreviewDisplay(SurfaceHolder surfaceHolder) {
            // Once the Surface has been selected, we configure the session and
            // are no longer able to change the sizes.
            getSettings().setSizesLocked(true);
            super.setPreviewDisplay(surfaceHolder);
        }

        public void setPreviewDisplayWithoutOptimize(SurfaceHolder surfaceHolder) {
            // Once the Surface has been selected, we configure the session and
            // are no longer able to change the sizes.
            getSettings().setSizesLocked(true);
            super.setPreviewDisplayWithoutOptimize(surfaceHolder);
        }

        public void setPreviewTextureWithoutOptimize(SurfaceTexture surfaceTexture) {
            // Once the Surface has been selected, we configure the session and
            // are no longer able to change the sizes.
            getSettings().setSizesLocked(true);
            super.setPreviewTextureWithoutOptimize(surfaceTexture);
        }

        @Override
        public boolean isNeedAFBeforeCapture() {
            return mNeedAfBeforeCapture;
        }

        @Override
        public void setStartVideoCallback(CameraStartVideoCallback callback) {
            mCameraStartVideoCallback = callback;
        }


        @Override
        public void setPreviewDataCallback(Handler handler, CameraPreviewDataCallback cb) {
            mPreviewCallback = CameraPreViewCallbackForward.getNewInstance(handler,
                    SprdAndroidCamera2ProxyImpl.this, cb);
        }

        public void onImageAvailableWithThumb(Image image, Handler handler, CameraPictureCallback jpeg) {
            if (jpeg != null) {
                Plane[] planeList = image.getPlanes();
                byte[] pixels = null;
                Log.i(TAG, " planeList.length = " + planeList.length);
                if (planeList.length == 1) {
                    if (mLastSettings.getNormalHdrModeEnable() == 1) {
                        if (mHdrNormalChangeState) {
                            changeState(mLastCameraState);
                            mHdrNormalChangeState = false;
                        } else {
                            mHdrNormalChangeState = true;
                        }
                    } else {
                        changeState(mLastCameraState);
                    }
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    pixels = new byte[buffer.remaining()];
                    buffer.get(pixels);
                } else if (planeList.length == 3) {
                    long t1 = System.currentTimeMillis();
                    mLastCameraState = getCameraState().getState();
                    if (mLastCameraState >= AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE) {
                        changeState(SprdCameraStateHolder.CAMERA_WITH_THUMB);
                    }
                    if (!mPersistentSettings.contains(CaptureRequest.JPEG_ORIENTATION)) return;
                    int jpegRotation = mPersistentSettings.get(CaptureRequest.JPEG_ORIENTATION);
                    final byte[] thumbYuvByte = getNV21FromImage(image);
                    Log.i(TAG,"thumbYuvByte.size = " + image.getWidth() + "x" + image.getHeight() + " yuv to nv21 cost :" + (System.currentTimeMillis() - t1) );
                    int imageWidth = image.getWidth();
                    int imageHeight = image.getHeight();
                    long t2 = System.currentTimeMillis();
                    if (jpegRotation % 180 != 0) {
                        int temp = imageWidth;
                        imageWidth = imageHeight;
                        imageHeight = temp;
                    }
                    YuvImage yuvimage = new YuvImage(thumbYuvByte, ImageFormat.NV21,
                            imageWidth,
                            imageHeight, null);
                      ByteArrayOutputStream out = new ByteArrayOutputStream();
                      yuvimage.compressToJpeg(new Rect(0, 0, imageWidth,imageHeight),90, out);
                      pixels = out.toByteArray();
                      Log.i(TAG, "jpegByte.length = " + pixels.length + " yuv compress cost :" + (System.currentTimeMillis() - t2));
                      try {
                          out.close();
                      } catch (Exception e) {
                          Log.e(TAG, "Exception in takePicture for thumbnail", e);
                      }
                }
                //change for app receiver second callback before first callback
                final byte[] jpegByte = pixels;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        jpeg.onPictureTaken(jpegByte, SprdAndroidCamera2ProxyImpl.this);
                    }});
            }
        }

        public void stopPreviewWithAsync() {
            Log.i(TAG, "stopPreviewWithAsync");
            if (getCameraState().isInvalid()) {
                return;
            }
            try {
                getDispatchThread().runJob(new Runnable() {
                    @Override
                    public void run() {
                        if (mCameraHandler instanceof SprdCamera2Handler) {
                            mCameraHandler.obtainMessage(CameraActions.RELEASE_FOR_THUMB).sendToTarget();
                        }
                        mCameraState.waitForStatesWithTimeout(~SprdCameraStateHolder.CAMERA_WITH_THUMB,
                                CameraAgent.WATI_THUMB_STATE_TIMEOUT);
                        mHdrNormalChangeState = false;
                        getCameraHandler()
                                .obtainMessage(CameraActions.STOP_PREVIEW)
                                .sendToTarget();
                    }});
            } catch (final RuntimeException ex) {
                getAgent().getCameraExceptionHandler().onDispatchThreadException(ex);
            }
        }

        protected void changeState(int newState) {
            Log.i(TAG, "changeState newState = " + newState + " getState = " + mCameraState.getState());
            if (mCameraState.getState() != newState) {
                mCameraState.setState(newState);
            }
        }

        @Override
        public void setHdrDetectionCallback(Handler handler, CameraHdrDetectionCallback mCallback){
            mCameraHandler.obtainMessage(
                    SprdCameraActions.SET_HDR_SCENE_LISTENER,
                    HdrDetectionCallbackForward.getNewInstance(handler, mCallback)).sendToTarget();
        }

        @Override
        public void setHdrDetectionWork(boolean work){
            Log.e(TAG,"setHdr detection work: " + work);
            isHdrDetectionWork = work;
        }

        public boolean getHdrDetectionStatus(){
            return isHdrDetectionWork;
        }

        @Override
        public void setAiSceneCallback(Handler handler , SprdCameraAgent.CameraAiSceneCallback mCallback) {
            mCameraHandler.obtainMessage(
                    SprdCameraActions.SET_AI_SCENE_LISTENER,
                    AiSceneCallbackForward.getNewInstance(handler, mCallback)).sendToTarget();
        }

        @Override
        public void setAiSceneWork(boolean work){
            isAiSceneWork = work;
        }

        public boolean getAiSceneStatus() {
            return isAiSceneWork;
        }

        @Override
        public void setAuto3DnrSceneDetectionCallback(Handler handler, CameraIsAuto3DnrSceneDetectionCallback mCallback){
            mCameraHandler.obtainMessage(
                    SprdCameraActions.SET_AUTO3DNR_SCENE_LISTENER,
                    Auto3DnrSceneDetectionCallbackForward.getNewInstance(handler, mCallback)).sendToTarget();
        }

        @Override
        public void setAuto3DnrSceneDetectionWork(boolean work){
            Log.e(TAG,"setAuto3Dnrr detection work: " + work);
            isAuto3DnrDetectionWork = work;
        }

        public boolean getAuto3DnrDetectionStatus(){
            return isAuto3DnrDetectionWork;
        }
    }


    private static class SensorSelfShotCallbackForward implements CameraSensorSelfShotCallback {
        private final Handler mHandler;
        private final CameraSensorSelfShotCallback mCallback;

        public static SensorSelfShotCallbackForward getNewInstance(Handler handler,
                CameraSensorSelfShotCallback cb) {
            if (handler == null || cb == null)
                return null;
            return new SensorSelfShotCallbackForward(handler, cb);
        }

        private SensorSelfShotCallbackForward(Handler h, CameraSensorSelfShotCallback cb) {
            mHandler = h;
            mCallback = cb;
        }

        @Override
        public void onSensorSelfShot(boolean bool, int value) {
            final boolean mbool = bool;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onSensorSelfShot(mbool, value);
                }
            });
        }
    }

    private static class CameraPreViewCallbackForward extends CameraPreViewCallbackAbstract {
        private final Handler mHandler;
        private final CameraPreviewDataCallback mCallback;
        private final CameraAgent.CameraProxy mCamera;

        public static CameraPreViewCallbackForward getNewInstance(Handler handler,
                CameraProxy camera, CameraPreviewDataCallback cb) {
            if (handler == null || camera == null || cb == null)
                return null;
            return new CameraPreViewCallbackForward(handler, camera, cb);
        }

        private CameraPreViewCallbackForward(Handler h, CameraProxy camera, CameraPreviewDataCallback cb) {
            mHandler = h;
            mCallback = cb;
            mCamera = camera;
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            try (Image image = reader.acquireNextImage()) {
                onPreviewFrame(getNV21FromImage(image), mCamera);
            }
        }

        @Override
        public void onPreviewFrame(byte[] data, CameraAgent.CameraProxy camera) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onPreviewFrame(data, mCamera);
                }
            });
        }
    }

    public static byte[] getYUVFromImage(Image image) {

        int width = image.getWidth();
        int height = image.getHeight();
        int rowStride, pixelStride;

        // Read image data
        Plane[] planes = image.getPlanes();

        ByteBuffer buffer = null;

        int offset = 0;
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(image.getFormat()) / 8];
        int maxRowSize = planes[0].getRowStride();
        for (int i = 0; i < planes.length; i++) {
            if (maxRowSize < planes[i].getRowStride()) {
                maxRowSize = planes[i].getRowStride();
            }
        }
        byte[] rowData = new byte[maxRowSize];

        for (int plane = 0; plane < planes.length; plane++) {
            buffer = planes[plane].getBuffer();

            rowStride = planes[plane].getRowStride();
            pixelStride = planes[plane].getPixelStride();

            int subsampleFactor = (plane == 0) ? 1 : 2;
            int colorW = width / subsampleFactor;
            int colorH = height / subsampleFactor;
            for (int row = 0; row < colorH; row++) {
                int length;
                if (pixelStride == 1) {
                    // Special case: optimized read of the entire row
                    length = colorW;
                    buffer.get(data, offset, length);
                    offset += length;
                } else {
                    // Generic case: should work for any pixelStride but slower.
                    // Use intermediate buffer to avoid read byte-by-byte from
                    // DirectByteBuffer, which is very bad for performance
                    length = (colorW - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < colorW; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
                // Advance buffer the remainder of the row stride
                if (row < colorH - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }

            buffer.rewind();
        }
//        saveBytes(data, "yuv");
        return data;
    }

    private static byte[] getNV21FromImage(Image img) {
        long t1 = System.currentTimeMillis();
        final int NUM_PLANES = 3;
        final Plane[] planeList = img.getPlanes();
        ByteBuffer[] planeBuf = new ByteBuffer[NUM_PLANES];
        int[] pixelStride = new int[NUM_PLANES];
        int[] rowStride = new int[NUM_PLANES];

        for (int i = 0; i < NUM_PLANES; i++) {
            Plane plane = planeList[i];
            planeBuf[i] = plane.getBuffer();
            pixelStride[i] = plane.getPixelStride();
            rowStride[i] = plane.getRowStride();
        }

        ByteBuffer buf = planeBuf[0];
        int yLength = buf.remaining();
        byte[] imageBytes = new byte[yLength * 3 / 2];
        buf.get(imageBytes, 0, yLength);
        buf.clear();

        byte[] bytes_u;
        buf = planeBuf[1];
        int uLength = buf.remaining();
        bytes_u = new byte[uLength];
        buf.get(bytes_u);
        buf.clear();

        byte[] bytes_v;
        buf = planeBuf[2];
        bytes_v = new byte[1];
        buf.get(bytes_v);
        buf.clear();
        // Log.i(TAG, "bytes_y.length =" + bytes_y.length + ", bytes_u.length =" + bytes_u.length
        // + ", bytes_v.length =" + bytes_v.length + ", pixelStride0 =" + pixelStride[0]
        // + ", pixelStride1 =" + pixelStride[1] + ", pixelStride2 =" + pixelStride[2]
        // + ", rowStride0 =" + rowStride[0] + ", rowStride1 =" + rowStride[1]
        // + ", rowStride2 ="
        // + rowStride[2]);
        // saveBytes(bytes_y, "y_plane");
        // saveBytes(bytes_u, "u_plane");
        // saveBytes(bytes_v, "v_plane");
        imageBytes[yLength] = bytes_v[0];
        System.arraycopy(bytes_u, 0, imageBytes, yLength + 1, uLength);
        bytes_u = null;
        bytes_v = null;
        Log.i(TAG, "getNV21FromImage cost " + (System.currentTimeMillis() - t1));
        return imageBytes;
    }

    private CameraStartVideoCallback mCameraStartVideoCallback;
    @Override
    public CameraStartVideoCallback getCameraStartVideoCallback() {
        return mCameraStartVideoCallback;
    }

    @Override
    protected void startRecoderRequest() {
        if (mCameraHandler instanceof SprdCamera2Handler) {
            SprdCamera2Handler handler = (SprdCamera2Handler)mCameraHandler;
            handler.setRecoderRepeatingRequest();
        }
    }

    private static void saveBytes(byte[] bytes, String name) {
        OutputStream output = null;
        try {
            File file = new File(Environment.getExternalStorageDirectory() + "/DCIM", name
                    + "_bytes");
            output = new FileOutputStream(file);
            output.write(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != output) {
                try {
                    output.close();
                } catch (Exception e2) {
                    e2.printStackTrace();
                }
            }
        }
    }
}
