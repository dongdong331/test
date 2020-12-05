
package com.android.ex.camera2.portability;

import android.annotation.TargetApi;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.android.ex.camera2.portability.debug.Log;

public abstract class SprdAndroidCameraAgentImpl extends CameraAgent {
    private static final Log.Tag TAG = new Log.Tag("SprdAndroidCameraAgentImpl");
    private static int mSensorSelfShotPreValue = -1;

    public abstract class SprdAndroidCameraProxyImpl extends CameraAgent.CameraProxy {
        @Override
        public boolean cancelBurstCapture(CancelBurstCaptureCallback cb) {
            return false;
        }

        public boolean isNeedAFBeforeCapture() {
            Parameters parameters = getParameters();
            String withflash = parameters.getNeedAFBeforeCapture();
            Log.i(TAG, "withflash = " + withflash);
            if ("0".equals(withflash)) {
                return false;
            } else {
                return true;
            }
        };
    }

    protected static class SensorSelfShotCallbackForward implements
            Camera.SensorSelfShotListenerApi1 {
        private final Handler mHandler;
        private final CameraAgent.CameraSensorSelfShotCallback mCallback;

        public static SensorSelfShotCallbackForward getNewInstance(Handler handler,
                CameraAgent.CameraSensorSelfShotCallback cb) {
            if (handler == null || cb == null)
                return null;
            return new SensorSelfShotCallbackForward(handler, cb);
        }

        private SensorSelfShotCallbackForward(Handler h, CameraAgent.CameraSensorSelfShotCallback cb) {
            mHandler = h;
            mCallback = cb;
        }

        @Override
        public void onSensorSelfShotApi1(final Camera.Face[] faces, Camera camera) {
            if (faces.length <= 10) {
                return;
            }

            int coveredValue = faces[10].score;
            if (coveredValue == 0) {
                return;
            }

            if (coveredValue != mSensorSelfShotPreValue && mCallback != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onSensorSelfShot(faces[10].score == 2 || faces[10].score == 6,
                                faces[10].score);
                    }
                });
                mSensorSelfShotPreValue = coveredValue;
            }
        }
    }

    public class SprdCameraHandler extends HistoryHandler {
        protected Camera mCamera;

        protected boolean mPreviewStarted = false;

        SprdCameraHandler(Looper looper) {
            super(looper);
        }

        protected void setSensorSelfShotListenerApi1(Camera.SensorSelfShotListenerApi1 listener) {
            mCamera.setSensorSelfShotListenerApi1(listener);
        }

        // SPRD:fix bug527657 Crash: com.android.camera2,(java.lang.RuntimeException)
        public android.hardware.Camera getCamera() {
            return mCamera;
        }

        protected void applySuperSettingsToParameters(final CameraSettings settings,
                final Parameters parameters, AndroidCameraCapabilities capabilities) {
            /* SPRD:Add for white balance */
            if (capabilities.supports(settings.getWhiteBalance())) {
                parameters.setWhiteBalance(getWhiteBalanceString(settings
                        .getWhiteBalance()));
            }

            /* SPRD:Add for color effect */
            if (capabilities.supports(settings.getCurrentColorEffect())) {
                parameters.setColorEffect(getColorEffectModeString(settings
                        .getCurrentColorEffect()));
            }

            /* SPRD:Add for antibanding */
            if (capabilities.supports(settings.getAntibanding())) {
                parameters.setAntibanding(getAntibandingString(settings
                        .getAntibanding()));
            }

            /* SPRD: fix bug 473462 add burst capture @ */
            if (settings.getBurstPicNum() > 0) {
                parameters.setContinuousCount(String.valueOf(settings.getBurstPicNum()));
            }
            /* @} */

            // SPRD Bug:474721 Feature:Contrast.
            if (capabilities.supports(settings.getCurrentContrast())) {
                parameters.setContrast(getContrastString(settings.getCurrentContrast()));
            }

            // SPRD Bug:474715 Feature:Brightness.
            if (capabilities.supports(settings.getBrightNess())) {
                parameters.setBrightness(getBrightnessString(settings.getBrightNess()));
            }

            // SPRD Bug:474724 Feature:ISO.
            if (capabilities.supports(settings.getISO())) {
                parameters.setISO(getISOString(settings.getISO()));
            }

            // SPRD Bug:474718 Feature:Metering.
            if (capabilities.supports(settings.getMetering())) {
                parameters.setMeteringMode(getMeteringString(settings.getMetering()));
            }

            // SPRD Bug:474722 Feature:Saturation.
            if (capabilities.supports(settings.getCurrentSaturation())) {
                parameters.setSaturation(getSaturationString(settings.getCurrentSaturation()));
            }

            parameters.set("perfect-skin-level", settings.getSkinWhitenLevel());

            // SPRD Bug:474696 Feature:Slow-Motion.
            if (capabilities.supports(settings.getCurrentVideoSlowMotion(),
                    capabilities.getSupportedSlowMotion())) {
                parameters.setSlowmotion(settings.getCurrentVideoSlowMotion());
            }

            // SPRD Feature: EOIS
            parameters.setEOIS(settings.getEOISEnable());

            // SPRD Bug:500099 Feature:Mirror.
            parameters.set("mirror", settings.getfrontCameraMirror() ? "true" : "false");
            /* SPRD: Fix bug 597206 that switch 3D preview between main camera and slave camera @{ */
            int current3DPreviewMode = settings.get3DPreviewMode();
            if (current3DPreviewMode == 0 || current3DPreviewMode == 1) {
                parameters.set("sprdMultiCam3PreviewId", settings.get3DPreviewMode());
            }
            /* @} */

            // SPRD: Fix bug 600110 that the rotation of 3d video content is not correct
            parameters.set("sensor-rot", settings.getDeviceRotationFor3DRecord());

            /*
             * SPRD: Fix bug 591216 that add new feature 3d range finding, only support API2
             * currently @{ int[] points = settings.get3DRangeFindPoints(); if (points != null &&
             * points.length > 0) { String pointsStr = "" + points[0]; for (int i = 1; i <
             * points.length; i++) { pointsStr = "," + points[i]; }
             * parameters.set("3drangefindpoints", pointsStr); } @}
             */
            parameters.set("sprd-zsl-enabled", settings.getZslModeEnable());
            if (settings.getBurstModeEnable() != -1) {
                parameters.set("sprd-burstmode-enabled", settings.getBurstModeEnable());
            }
            if (settings.getSensorSelfShotEnable() != -1) {
                parameters.set("sprd3AvailableSensorSelfShot", settings.getSensorSelfShotEnable());
            }
            parameters.set("sprd-appmode-id", settings.getAppModeId());
            if (settings.get3DNREnable() != -1) {
                parameters.set("sprd-3dnr-enabled", settings.get3DNREnable());
            }
            if (settings.getFilterType() != -1) {
                parameters.set("sprd-filter-type", settings.getFilterType());
            }
            if(settings.getFlashLevel() != -1){
                Log.i(TAG,"set flash level:"+settings.getFlashLevel());
                parameters.set("adjust-flash-level", settings.getFlashLevel());
            }
            Log.e(TAG, " Camera1 API setParameters = " + parameters.flatten());
        }

        /*
         * SPRD:Add for antibanding
         */
        public String getAntibandingString(
                CameraCapabilities.Antibanding antibanding) {
            String antibandingParameter = null;
            switch (antibanding) {
                case AUTO: {
                    antibandingParameter = Camera.Parameters.ANTIBANDING_AUTO;
                    break;
                }
                case ANTIBANDING_50HZ: {
                    antibandingParameter = Camera.Parameters.ANTIBANDING_50HZ;
                    break;
                }
                case ANTIBANDING_60HZ: {
                    antibandingParameter = Camera.Parameters.ANTIBANDING_60HZ;
                    break;
                }
                case OFF: {
                    antibandingParameter = Camera.Parameters.ANTIBANDING_OFF;
                    break;
                }
                default: {
                    antibandingParameter = Camera.Parameters.ANTIBANDING_AUTO;
                    break;
                }
            }
            return antibandingParameter;
        }

        /*
         * SPRD:Add for coloreffect
         */
        private String getColorEffectModeString(
                CameraCapabilities.ColorEffect colorEffect) {
            String colorParametersString = null;
            switch (colorEffect) {
                case NONE: {
                    colorParametersString = Camera.Parameters.EFFECT_NONE;
                    break;
                }
                case MONO: {
                    colorParametersString = Camera.Parameters.EFFECT_MONO;
                    break;
                }
                case NEGATIVE: {
                    colorParametersString = Camera.Parameters.EFFECT_NEGATIVE;
                    break;
                }
                case SEPIA: {
                    colorParametersString = Camera.Parameters.EFFECT_SEPIA;
                    break;
                }
                case COLD: {
                    colorParametersString = Camera.Parameters.EFFECT_AQUA;
                    break;
                }
                case ANTIQUE: {
                    colorParametersString = Camera.Parameters.EFFECT_SOLARIZE;
                    break;
                }
                default: {
                    colorParametersString = Camera.Parameters.EFFECT_NONE;
                    break;
                }
            }
            return colorParametersString;
        }

        /*
         * SPRD:Add for whitebalance
         */
        private String getWhiteBalanceString(
                CameraCapabilities.WhiteBalance whiteBalance) {
            String whiteBalanceParametersString = null;
            switch (whiteBalance) {
                case AUTO: {
                    whiteBalanceParametersString = Camera.Parameters.WHITE_BALANCE_AUTO;
                    break;
                }
                case CLOUDY_DAYLIGHT: {
                    whiteBalanceParametersString = Camera.Parameters.WHITE_BALANCE_CLOUDY_DAYLIGHT;
                    break;
                }
                case DAYLIGHT: {
                    whiteBalanceParametersString = Camera.Parameters.WHITE_BALANCE_DAYLIGHT;
                    break;
                }
                case FLUORESCENT: {
                    whiteBalanceParametersString = Camera.Parameters.WHITE_BALANCE_FLUORESCENT;
                    break;
                }
                case INCANDESCENT: {
                    whiteBalanceParametersString = Camera.Parameters.WHITE_BALANCE_INCANDESCENT;
                    break;
                }
                case SHADE: {
                    whiteBalanceParametersString = Camera.Parameters.WHITE_BALANCE_SHADE;
                    break;
                }
                case TWILIGHT: {
                    whiteBalanceParametersString = Camera.Parameters.WHITE_BALANCE_TWILIGHT;
                    break;
                }
                case WARM_FLUORESCENT: {
                    whiteBalanceParametersString = Camera.Parameters.WHITE_BALANCE_WARM_FLUORESCENT;
                    break;
                }
                default: {
                    whiteBalanceParametersString = Camera.Parameters.WHITE_BALANCE_AUTO;
                    break;
                }
            }
            return whiteBalanceParametersString;
        }

        // SPRD Bug:474721 Feature:Contrast.
        public String getContrastString(CameraCapabilities.Contrast contrast) {
            String contrastParameter = null;
            switch (contrast) {
                case CONTRAST_ZERO: {
                    contrastParameter = AndroidCameraCapabilities.VALUE_ZERO;
                    break;
                }
                case CONTRAST_ONE: {
                    contrastParameter = AndroidCameraCapabilities.VALUE_ONE;
                    break;
                }
                case CONTRAST_TWO: {
                    contrastParameter = AndroidCameraCapabilities.VALUE_TWO;
                    break;
                }
                case CONTRAST_THREE: {
                    contrastParameter = AndroidCameraCapabilities.VALUE_THREE;
                    break;
                }
                case CONTRAST_FOUR: {
                    contrastParameter = AndroidCameraCapabilities.VALUE_FOUR;
                    break;
                }
                case CONTRAST_FIVE: {
                    contrastParameter = AndroidCameraCapabilities.VALUE_FIVE;
                    break;
                }
                case CONTRAST_SIX: {
                    contrastParameter = AndroidCameraCapabilities.VALUE_SIX;
                    break;
                }
                default: {
                    contrastParameter = AndroidCameraCapabilities.VALUE_THREE;
                }
            }
            return contrastParameter;
        }

        // SPRD Bug:474715 Feature:Brightness.
        public String getBrightnessString(CameraCapabilities.BrightNess brightness) {
            String brightnessParameter = null;
            switch (brightness) {
                case BRIGHTNESS_ZERO: {
                    brightnessParameter = AndroidCameraCapabilities.VALUE_ZERO;
                    break;
                }
                case BRIGHTNESS_ONE: {
                    brightnessParameter = AndroidCameraCapabilities.VALUE_ONE;
                    break;
                }
                case BRIGHTNESS_TWO: {
                    brightnessParameter = AndroidCameraCapabilities.VALUE_TWO;
                    break;
                }
                case BRIGHTNESS_THREE: {
                    brightnessParameter = AndroidCameraCapabilities.VALUE_THREE;
                    break;
                }
                case BRIGHTNESS_FOUR: {
                    brightnessParameter = AndroidCameraCapabilities.VALUE_FOUR;
                    break;
                }
                case BRIGHTNESS_FIVE: {
                    brightnessParameter = AndroidCameraCapabilities.VALUE_FIVE;
                    break;
                }
                case BRIGHTNESS_SIX: {
                    brightnessParameter = AndroidCameraCapabilities.VALUE_SIX;
                    break;
                }
                default: {
                    brightnessParameter = AndroidCameraCapabilities.VALUE_THREE;
                }
            }
            return brightnessParameter;
        }

        // SPRD Bug:474724 Feature:ISO.
        public String getISOString(CameraCapabilities.ISO iso) {
            String isoParameter = null;
            switch (iso) {
                case AUTO: {
                    isoParameter = Camera.Parameters.ISO_AUTO;
                    break;
                }
                case ISO_1600: {
                    isoParameter = Camera.Parameters.ISO_1600;
                    break;
                }
                case ISO_800: {
                    isoParameter = Camera.Parameters.ISO_800;
                    break;
                }
                case ISO_400: {
                    isoParameter = Camera.Parameters.ISO_400;
                    break;
                }
                case ISO_200: {
                    isoParameter = Camera.Parameters.ISO_200;
                    break;
                }
                case ISO_100: {
                    isoParameter = Camera.Parameters.ISO_100;
                    break;
                }
                default: {
                    isoParameter = Camera.Parameters.ISO_AUTO;
                    break;
                }
            }
            return isoParameter;
        }

        // SPRD Bug:474718 Feature:Metering.
        public String getMeteringString(CameraCapabilities.Metering metering) {
            String meteringParameter = null;
            switch (metering) {
                case FRAMEAVERAGE: {
                    meteringParameter = Parameters.AUTO_EXPOSURE_FRAME_AVG;
                    break;
                }
                case CENTERWEIGHTED: {
                    meteringParameter = Parameters.AUTO_EXPOSURE_CENTER_WEIGHTED;
                    break;
                }
                case SPOTMETERING: {
                    meteringParameter = Parameters.AUTO_EXPOSURE_SPOT_METERING;
                    break;
                }
                default: {
                    meteringParameter = Parameters.AUTO_EXPOSURE_FRAME_AVG;
                    break;
                }
            }
            return meteringParameter;
        }

        // SPRD Bug:474722 Feature:Saturation.
        public String getSaturationString(CameraCapabilities.Saturation saturation) {
            String saturationParameter = null;
            switch (saturation) {
                case SATURATION_ZERO: {
                    saturationParameter = AndroidCameraCapabilities.VALUE_ZERO;
                    break;
                }
                case SATURATION_ONE: {
                    saturationParameter = AndroidCameraCapabilities.VALUE_ONE;
                    break;
                }
                case SATURATION_TWO: {
                    saturationParameter = AndroidCameraCapabilities.VALUE_TWO;
                    break;
                }
                case SATURATION_THREE: {
                    saturationParameter = AndroidCameraCapabilities.VALUE_THREE;
                    break;
                }
                case SATURATION_FOUR: {
                    saturationParameter = AndroidCameraCapabilities.VALUE_FOUR;
                    break;
                }
                case SATURATION_FIVE: {
                    saturationParameter = AndroidCameraCapabilities.VALUE_FIVE;
                    break;
                }
                case SATURATION_SIX: {
                    saturationParameter = AndroidCameraCapabilities.VALUE_SIX;
                    break;
                }
                default: {
                    saturationParameter = AndroidCameraCapabilities.VALUE_THREE;
                }
            }
            return saturationParameter;
        }
    }
}
