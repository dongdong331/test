package android.hardware.face;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.util.Log;
import android.view.Surface;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.media.ImageReader;
import android.content.Context;
import android.graphics.ImageFormat;
import java.util.Arrays;
import java.util.List;
import android.media.Image;
import java.lang.Runnable;
import android.os.SystemProperties;
import android.app.ActivityThread;

/**
 * <p>The FaceCamera class is a representation of a  camera APIs supply to Face APP</p>
 *
 */
class FaceCameraImpl {
    private static final String TAG = "FaceCameraImpl";
    private static final String ENROLL_CAMERA_ID = "32";
    private static final String AUTHENTICATE_CAMERA_ID = "33";
    private static final String ENROLL_CAMERA_ID_SINGLE = "30";
    private static final String AUTHENTICATE_CAMERA_ID_SINGLE = "31";
    private static final String FACEID_VER_PROP = "persist.vendor.cam.faceid.version";
    private static final String FACEID_LIVEMODE_PROP = "persist.vendor.faceid.livenessmode";
    private int faceid_ver = 0;
    private boolean isLiveMode = false;
    private String openId;
    private int mWidth;
    private int mHeight;
    private int mFormat;
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private ImageReader mCaptureReader;
    private CaptureRequest.Builder mRequestBuilder;
    private CameraCaptureSession mSession;
    private CameraCallback mCameraCallback;
    private Surface mSurface;
    private Surface mUnlockSurface;
    private SurfaceTexture mUnlockST;
    private Handler mHandler;
    //private HandlerThread mHandlerThread;
    private Context mContext;
    private boolean mToDoClose = false;
    private boolean mOpenIdReady = true;
    private boolean mOtherIdsReady = true;
    private boolean mDelayCheckDone = false;
    private boolean mRealOpenCalled = false;

    public static abstract class CameraCallback {
        /**
         * The method called when preview result has been available.
         *
         * <p>At this point, Face APP can get buffer address. Do not handle the image data in this thread!</p>
         *
         * @param addrs the buffer addresses for capture result.
         * @param ae the AE status.
         */
        public abstract void onCameraResultAvailable(long[] addrs, int[] info, byte[] byteInfo); // Must implement
    }

    public void initialize(int width, int height, int format,
                    int length, CameraCallback callback, Surface surface, Context context) {
        mWidth = width;
        mHeight = height;
        mFormat= format;
        mContext = context;
        mCameraCallback = callback;
        mSurface = surface;
        faceid_ver = SystemProperties.getInt(FACEID_VER_PROP, 0);
        isLiveMode = SystemProperties.getBoolean(FACEID_LIVEMODE_PROP + UserHandle.myUserId(), false);
        //initHandler();
        openCameraAndStartPreview();
    }

    public void faceCapture(long[] addrs, int orientation) {
        long[] results = addrs;
        if(mSurface == null && faceid_ver == 1) { // single camera & auth
            results = new long[1];
            results[0] = addrs[0];
        }
        mRequestBuilder.set(CaptureRequest.ANDROID_SPRD_DEVICE_ORIENTATION, orientation);
        mRequestBuilder.set(CaptureRequest.ANDROID_SPRD_FROM_FACEIDSERVICE_PHYADDR, results);
        mRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE);
        try {
            if(mSession != null) {
                mSession.setRepeatingRequest(mRequestBuilder.build(), captureListener, mHandler);
            }
        } catch (Exception e) {
            //e.printStackTrace();
        }
    }

    public void deInitialize() {
        closeCamera();
    }

    public FaceCameraImpl(Handler h) {
        mHandler = h;
    }

    private void realOpenCamera() {
        mRealOpenCalled = true;
        mCameraManager.unregisterAvailabilityCallback(availableCallback);
        if(mToDoClose) return;
        try {
            Log.d(TAG, "open camera id = " + openId);
            mCameraManager.openCamera(openId, mCameraDeviceStateCallback, mHandler);
        } catch (Exception e) {
            Log.e(TAG, "Cannot open Camera:" + e);
        }
    }

    private void openCameraAndStartPreview() {
        Log.d(TAG, "openCameraAndStartPreview in");
        Log.d(TAG, "faceid version: " + faceid_ver);
        if(mSurface != null) {
            openId = faceid_ver == 1 ? ENROLL_CAMERA_ID_SINGLE : ENROLL_CAMERA_ID;
        } else {
            //openId = faceid_ver == 1 ? AUTHENTICATE_CAMERA_ID_SINGLE : AUTHENTICATE_CAMERA_ID;
            if(faceid_ver == 1 || !isLiveMode) {
                openId = AUTHENTICATE_CAMERA_ID_SINGLE;
            } else {
                openId = AUTHENTICATE_CAMERA_ID;
            }
        }
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        mCameraManager.registerAvailabilityCallback(availableCallback, mHandler);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mDelayCheckDone = true;
                if(mOpenIdReady && mOtherIdsReady) {
                    realOpenCamera();
                } else { //give another chance to wait cameras to be ready
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if(!mRealOpenCalled) {
                                mCameraManager.unregisterAvailabilityCallback(availableCallback);
                                if(!mToDoClose) mCameraCallback.onCameraResultAvailable(null, null, null);
                            }
                        }
                    }, 3000);
                }
            }
        }, 10);
        Log.d(TAG, "openCameraAndStartPreview out");
    }

    private CameraManager.AvailabilityCallback availableCallback = new CameraManager.AvailabilityCallback() {
        @Override
        public void onCameraAvailable(String cameraId) {
            Log.d(TAG, "Available camera: " + cameraId);
            if(!mDelayCheckDone) return;
            // another chance check
            if(cameraId.equals(openId)) {
                mOpenIdReady = true;
                if(mOtherIdsReady) {
                    realOpenCamera(); //another chance for openId ready
                }
            } else { //check other cameras
                mOtherIdsReady = true;
                if(mOpenIdReady) {
                    realOpenCamera(); //another chance for other cameras ready
                }
            }
        }

        @Override
        public void onCameraUnavailable(String cameraId) {
            Log.d(TAG, "Unavailable camera: " + cameraId);
            if(cameraId.equals(openId)) {
                mOpenIdReady = false;
            } else {// check other cameras
                mOtherIdsReady = false;
            }
        }
    };

    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {
            Log.d(TAG, "CameraDevice.StateCallback onOpened in");
            try {
                if(mToDoClose) {
                    camera.close();
                    mToDoClose = false;
                } else {
                    mCameraDevice = camera;
                    startPreview(camera);
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "CameraDevice.StateCallback onOpened out");
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.e(TAG, "CameraDevice Disconnected");
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.e(TAG, "CameraDevice open failed");
        }
    };

    private void startPreview(CameraDevice camera) throws CameraAccessException {
        Log.d(TAG, "startPreview in");
        try {
            mRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<Surface> surfaces;
        ImageDropperListener imageDropperListener = new ImageDropperListener();
        mCaptureReader = ImageReader.newInstance(mWidth, mHeight, mFormat, 3);
        mCaptureReader.setOnImageAvailableListener(imageDropperListener, mHandler);
        if(mSurface != null) {
            mRequestBuilder.addTarget(mSurface);
            surfaces = Arrays.asList(mSurface,mCaptureReader.getSurface());
        } else {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            mUnlockST = new SurfaceTexture(textures[0]);
            mUnlockST.setDefaultBufferSize(mWidth, mHeight);
            mUnlockSurface = new Surface(mUnlockST);
            mRequestBuilder.addTarget(mUnlockSurface);
            surfaces = Arrays.asList(mUnlockSurface, mCaptureReader.getSurface());
        }
        camera.createCaptureSession(surfaces, mSessionStateCallback, mHandler);
        Log.d(TAG, "startPreview out");
    }

    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            Log.d(TAG,"CameraCaptureSession.StateCallback onConfigured in");
            mSession = session;
            if(mToDoClose) {
                realCloseCamera();
                mToDoClose = false;
                return;
            }
            mRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE);
            try {
                mSession.setRepeatingRequest(mRequestBuilder.build(), captureListener, mHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "CameraCaptureSession.StateCallback onConfigured out");
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            throw new IllegalStateException("onConfigureFailed");
        }
    };

    /**
     * It can be used for the case where we don't care the image data at all.
     */
    private class ImageDropperListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d("FaceCameraImpl","onImageAvailable in");
            Image image = null;
            try {
                image = reader.acquireNextImage();
            } finally {
                if (image != null) {
                    image.close();
                }
            }
            Log.d("FaceCameraImpl","onImageAvailable out");
        }
    }

    final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                        CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            try {
                long[] addrs = result.get(CaptureResult.ANDROID_SPRD_TO_FACEIDSERVICE_PHYADDR);
                int[] sprdAeInfo = result.get(CaptureResult.ANDROID_SPRD_AE_INFO);
                int[] histogram = result.get(CaptureResult.ANDROID_STATISTICS_HISTOGRAM);
                int[] info = new int[1 + sprdAeInfo.length + histogram.length]; // CONTROL_AE_STATE + ANDROID_SPRD_AE_INFO + ANDROID_STATISTICS_HISTOGRAM
                info[0] = result.get(CaptureResult.CONTROL_AE_STATE);
                System.arraycopy(sprdAeInfo, 0, info, 1, sprdAeInfo.length);
                System.arraycopy(histogram, 0, info, 1 + sprdAeInfo.length, histogram.length);
                Log.i(TAG, "onCaptureCompleted , result faceid buffers "
                                        + " for frameNumber " + result.getFrameNumber());
                byte[] faceInfo = result.get(CaptureResult.ANDROID_SPRD_FACE_INFO);
                if(addrs[0] != 0) {
                    long[] results = addrs;
                    if(mSurface == null && (faceid_ver == 1 || !isLiveMode)) { // single camera & auth
                        results = new long[3];
                        results[0] = addrs[0];
                    }
                    mCameraCallback.onCameraResultAvailable(results, info, faceInfo);
                }
            } catch (Exception e) {
                // TODO Auto-generated catch block
                Log.d(TAG, "onCaptureCompleted, no ANDROID_SPRD_TO_FACEIDSERVICE_PHYADDR");
            }
        }

        public void onCapturePartial(CameraCaptureSession session,
                                                                CaptureRequest request, CaptureResult result) {
            // default empty implementation
            Log.i(TAG, "onCapturePartial  " );
        }
    };

    private void realCloseCamera() {
        try {
            mCaptureReader = null;
            if(mUnlockSurface != null) {
                mUnlockSurface.release();
                mUnlockSurface = null;
            }
            if(mUnlockST != null) {
                mUnlockST.release();
                mUnlockST = null;
            }
            mSession.abortCaptures();
            mSession = null;
            Log.d(TAG,"closeCamera set mSession null");
        } catch (CameraAccessException ex) {
            Log.e(TAG, "Failed to close existing camera capture session", ex);
        } catch (Exception ex) {
            Log.e(TAG, "close exception", ex);
        }
        mCameraDevice.close();
        mCameraDevice = null;
    }

    private void closeCamera() {
        Log.i(TAG, "closeCamera in");
        if (mCameraDevice == null) {
            Log.e(TAG, "mCameraDevice = null");
            mToDoClose = true;
            return;
        }
        if (mSession == null) {
            Log.e(TAG, "mSession = null");
            mToDoClose = true;
            return;
        }
        realCloseCamera();
    }
}
