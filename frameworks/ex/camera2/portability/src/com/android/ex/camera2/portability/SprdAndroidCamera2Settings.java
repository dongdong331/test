
package com.android.ex.camera2.portability;

import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest.Key;
import static android.hardware.camera2.CaptureRequest.*;
import static android.hardware.camera2.CameraMetadata.*;
import static android.hardware.camera2.CameraCharacteristics.CONTROL_AVAILABLE_AI_SCENE;

import com.android.ex.camera2.portability.AndroidCamera2Settings;
import com.android.ex.camera2.portability.CameraSettings;
import com.android.ex.camera2.portability.Size;
import com.android.ex.camera2.portability.CameraCapabilities.*;

import com.android.ex.camera2.portability.debug.Log;
import com.android.ex.camera2.utils.Camera2RequestSettingsSet;

public class SprdAndroidCamera2Settings extends AndroidCamera2Settings {

    private static final Log.Tag TAG = new Log.Tag("SprdAndCam2Set");

    public static final Key<Integer> CONTROL_SPRD_SLOW_MOTION = new Key<Integer>(
            "com.addParameters.slowMotion", int.class);

    public SprdAndroidCamera2Settings(CameraDevice camera, int template, Rect activeArray,
            Size preview, Size photo) throws CameraAccessException {
        super(camera, template, activeArray, preview, photo);

        initAndroidCamera2Settings(camera, template);
    }

    public SprdAndroidCamera2Settings(SprdAndroidCamera2Settings other) {
        super(other);
    }

    @Override
    public CameraSettings copy() {
        return new SprdAndroidCamera2Settings(this);
    }

    private void initAndroidCamera2Settings(CameraDevice camera, int template)
            throws CameraAccessException {
        if (camera == null) {
            throw new NullPointerException("camera must not be null");
        }

        final Builder templateSettings;
        templateSettings = camera.createCaptureRequest(template);
        Integer antibanding = templateSettings.get(CONTROL_AE_ANTIBANDING_MODE);
        if (antibanding != null) {
            mAntibanding = SprdAndroidCamera2Capabilities.antibandingFromInt(antibanding);
        }

        /* SPRD:Add for color effect Bug 474727 @{ */
        Integer colorEffect = templateSettings.get(CONTROL_EFFECT_MODE);
        if (colorEffect != null) {
            mCurrentColorEffect = SprdAndroidCamera2Capabilities.colorEffectFromInt(colorEffect);
        }
        /* @} */

        /* SPRD: fix bug 473462 add burst capture @{ */
        Integer burstcount = templateSettings.get(SPRD_CAPTURE_MODE);
        if (burstcount != null) {
            mBurstNumber = burstcount.intValue();
        }
        /* @} */

        // SPRD Bug:474721 Feature:Contrast.
        Integer contrast = templateSettings.get(CONTROL_CONTRAST_MODE);
        if (contrast != null) {
            mCurrentContrast = SprdAndroidCamera2Capabilities.contrastFromInt(contrast);
        }

        // SPRD Bug:474715 Feature:Brightness.
        Integer brightness = templateSettings.get(CONTROL_BRIGHTNESS_MODE);
        if (brightness != null) {
            mBrightNess = SprdAndroidCamera2Capabilities.brightnessFromInt(brightness);
        }

        // SPRD Bug:474724 Feature:ISO.
        Integer iso = templateSettings.get(CONTROL_ISO_MODE);
        if (iso != null) {
            mISO = SprdAndroidCamera2Capabilities.isoFromInt(iso);
        }

        // SPRD Bug:474718 Feature:Metering.
        Integer metering = templateSettings.get(CONTROL_METERING_MODE);
        if (metering != null) {
            mMetering = SprdAndroidCamera2Capabilities.meteringFromInt(metering);
        }

        // SPRD Bug:474722 Feature:Saturation.
        Integer saturation = templateSettings.get(CONTROL_SATURATION_MODE);
        if (saturation != null) {
            mCurrentSaturation = SprdAndroidCamera2Capabilities.saturationFromInt(saturation);
        }
    }

    public Camera2RequestSettingsSet getRequestSettings() {
        Camera2RequestSettingsSet requestSettings = super.getRequestSettings();

        updateRequestAntiBandingMode(requestSettings);
        updateRequestColorEffect(requestSettings);
        updateRequestBurstNumberMode(requestSettings);// SPRD: fix bug 473462 add burst capture
        updateRequestContrast(requestSettings);// SPRD Bug:474721 Feature:Contrast.
        // SPRD Bug:474715 Feature:Brightness.
        updateRequestBrightNess(requestSettings);
        // SPRD Bug:474724 Feature:ISO.
        updateRequestISO(requestSettings);
        // SPRD Bug:474718 Feature:Metering.
        updateRequestMetering(requestSettings);
        // SPRD Bug:474722 Feature:Saturation.
        updateRequestSaturation(requestSettings);

        /*SPRD: fix bug 627247 add for ucam beauty @{ */
        requestSettings.set(CONTROL_SKIN_WHITEN_MODE, mBeatuyLevel);
        /* @} */
        // Log.i(TAG, " set ANDROID_SPRD_ZSL_ENABLED: mCurrentEnableZslMode = " +
        // mCurrentEnableZslMode);
        requestSettings.set(ANDROID_SPRD_ZSL_ENABLED, mCurrentEnableZslMode);
        /* SPRD: fix bug 500099 add for mirror @{ */
        // Log.i(TAG, " set CONTROL_FRONT_CAMERA_MIRROR: mFrontCameraMirror = " +
        // mFrontCameraMirror);
        requestSettings.set(CONTROL_FRONT_CAMERA_MIRROR, mFrontCameraMirror);
        /* @} */
        /* SPRD: feature: EOIS @{ */
        // Log.i(TAG, " set CONTROL_SPRD_EOIS_ENABLE: mEOISEnable = " + mEOISEnable);
        requestSettings.set(CONTROL_SPRD_EOIS_ENABLE, mEOISEnable);
        /* @} */
        requestSettings.set(ANDROID_SPRD_HIGHISO_ENABLED, mCurrentEnableHighISO);
        if (mCurrentEnableNormalHdrMode != -1) {
            Log.i(TAG, " set ANDROID_SPRD_NORMAL_HDR_ENABLED: mCurrentEnableNormalHdrMode = "
                    + mCurrentEnableNormalHdrMode);
            requestSettings.set(ANDROID_SPRD_NORMAL_HDR_ENABLED, mCurrentEnableNormalHdrMode);
        }

        // add tag for 3dnr and auto3dnr
        if(mCurrentEnableAuto3Dnr != -1){
            Log.i(TAG, " set ANDROID_SPRD_AUTO_3DNR_ENABLED: mCurrentEnableAuto3Dnr = "
                    + mCurrentEnableAuto3Dnr);
            requestSettings.set(ANDROID_SPRD_AUTO_3DNR_ENABLED, mCurrentEnableAuto3Dnr);
        }

        if(mCurrentEnableAuto3dnrCapture != -1){
            Log.i(TAG, " set ANDROID_SPRD_3DNR_CAPTURE_ENABLED: mCurrentEnableAuto3dnrCapture = "
                    + mCurrentEnableAuto3dnrCapture);
            requestSettings.set(ANDROID_SPRD_3DNR_CAPTURE_ENABLED, mCurrentEnableAuto3dnrCapture);
        }
        if (mCurrentEnableBurstMode != -1) {
            // Log.i(TAG, " set ANDROID_SPRD_BURSTMODE_ENABLED: mCurrentEnableBurstMode = " +
            // mCurrentEnableBurstMode);
            requestSettings.set(ANDROID_SPRD_BURSTMODE_ENABLED, mCurrentEnableBurstMode);
        }
        if (mSprdFNumber != -1) {
            // Log.i(TAG, " set CONTROL_SPRD_BLUR_F_NUMBER: mSprdFNumber = " + mSprdFNumber);
            requestSettings.set(CONTROL_SPRD_BLUR_F_NUMBER, mSprdFNumber);
        }
        if (mBlurCircleSize != -1) {
            // Log.i(TAG, " set CONTROL_SPRD_BLUR_CIRCLESIZE: mBlurCircleSize = " +
            // mBlurCircleSize);
            requestSettings.set(CONTROL_SPRD_BLUR_CIRCLESIZE, mBlurCircleSize);
        }
        if (mCurrentEnableSensorSelfShot != -1) {
            // Log.i(TAG,
            // " set CONTROL_AVAILABLE_SPRD_SENSOR_SELF_SHOT: mCurrentEnableSensorSelfShot = " +
            // mCurrentEnableSensorSelfShot);
            requestSettings.set(CONTROL_AVAILABLE_SPRD_SENSOR_SELF_SHOT,
                    mCurrentEnableSensorSelfShot);
        }

        if (mSprdDeviceOrientation != -1) {
            requestSettings.set(ANDROID_SPRD_DEVICE_ORIENTATION,
                    mSprdDeviceOrientation);
        }

        if (mCurrentEnable3dnr != -1) {
            Log.i(TAG, " set CONTROL_SPRD_3DNR_ENABLED: mCurrentEnable3dnr = " + mCurrentEnable3dnr);
            requestSettings.set(CONTROL_SPRD_3DNR_ENABLED, mCurrentEnable3dnr);
        }

        if (mCurrentEnableAiScene != -1) {
            Log.i(TAG, " set ANDROID_SPRD_AI_SCENE_ENABLED: mCurrentEnableAiScene = " + mCurrentEnableAiScene);
            requestSettings.set(ANDROID_SPRD_AI_SCENE_ENABLED, mCurrentEnableAiScene);
        }

        if (mFastThumbEnable != -1) {
            Log.i(TAG, " set CONTROL_SPRD_FAST_THUMB: mFastThumbEnable = " + mFastThumbEnable);
            requestSettings.set(CONTROL_SPRD_FAST_THUMB, mFastThumbEnable);
        }
        // SPRD: Fix bug 597206 that switch 3D preview between main camera and slave camera.
        updateRequestSettingOrForceToDefault(ANDROID_SPRD_3D_PREVIEW_MODE, mSprd3DPreviewMode);

        /*
         * SPRD: Fix bug 591216 that add new feature 3d range finding, only support API2 currently
         * @{
         */
        try {
            requestSettings.set(ANDROID_SPRD_3D_RANGE_FIND_POINTS, mSprd3DRangeFindPoints);
        } catch (Exception e) {
            Log.w(TAG, "" + e.getMessage());
        }
        /* @} */
        if (mCurrentFilterType != -1) {
            Log.i(TAG, " set CONTROL_SPRD_FILTER_TYPE: mCurrentFilterType = " + mCurrentFilterType);
            requestSettings.set(CONTROL_SPRD_FILTER_TYPE, mCurrentFilterType);
        }
        if (mCurrentSlowMotion != null) {
            Log.i(TAG, " set CONTROL_SPRD_SLOW_MOTION: mCurrentSlowMotion = " + mCurrentSlowMotion);
            requestSettings.set(CONTROL_SPRD_SLOW_MOTION, Integer.parseInt(mCurrentSlowMotion));
        }

        if (mFlashLevel != -1){
            Log.i(TAG,"set CONTROL_SPRD_ADJUST_FLASH_LEVEL mFlashLevel ="+mFlashLevel);
            requestSettings.set(CONTROL_SPRD_ADJUST_FLASH_LEVEL, mFlashLevel);
        }

        if(isAutoHdr != -1){
            Log.i(TAG,"set ANDROID_SPRD_AUTO_HDR_ENABLED isAutoHDR = " + isAutoHdr);
            requestSettings.set(ANDROID_SPRD_AUTO_HDR_ENABLED,isAutoHdr);
        }

        Log.i(TAG,"set CONTROL_SPRD_APP_MODE_ID mCurrentAppModeId ="+mCurrentAppModeId);
        requestSettings.set(CONTROL_SPRD_APP_MODE_ID, mCurrentAppModeId);

        return requestSettings;
    }

    private void updateRequestAntiBandingMode(Camera2RequestSettingsSet requestSettings) {
        Integer mode = null;
        if (mAntibanding != null) {
            switch (mAntibanding) {
                case AUTO: {
                    mode = CONTROL_AE_ANTIBANDING_MODE_AUTO;
                    break;
                }
                case ANTIBANDING_50HZ: {
                    mode = CONTROL_AE_ANTIBANDING_MODE_50HZ;
                    break;
                }
                case ANTIBANDING_60HZ: {
                    mode = CONTROL_AE_ANTIBANDING_MODE_60HZ;
                    break;
                }
                case OFF: {
                    mode = CONTROL_AE_ANTIBANDING_MODE_OFF;
                    break;
                }
                default: {
                    Log.w(TAG, "convertAntiBandingMode - Unknown antibanding mode " + mode);
                    break;
                }
            }
            requestSettings.set(CONTROL_AE_ANTIBANDING_MODE, mode);
        }
    }

    /* SPRD:Add for color effect Bug 474727 @{ */
    private void updateRequestColorEffect(Camera2RequestSettingsSet requestSettings) {
        Integer mode = null;
        if (mCurrentColorEffect != null) {
            switch (mCurrentColorEffect) {
                case NONE: {
                    mode = CONTROL_EFFECT_MODE_OFF;
                    break;
                }
                case MONO: {
                    mode = CONTROL_EFFECT_MODE_MONO;
                    break;
                }
                case NEGATIVE: {
                    mode = CONTROL_EFFECT_MODE_NEGATIVE;
                    break;
                }
                case SEPIA: {
                    mode = CONTROL_EFFECT_MODE_SEPIA;
                    break;
                }
                case COLD: {
                    mode = CONTROL_EFFECT_MODE_AQUA;
                    break;
                }
                case ANTIQUE: {
                    mode = CONTROL_EFFECT_MODE_SOLARIZE;
                    break;
                }
                default: {
                    Log.w(TAG, "Unable to convert to API 2 color effect: " + mCurrentColorEffect);
                    break;
                }
            }
            requestSettings.set(CONTROL_EFFECT_MODE, mode);
        }
    }
    /* @} */

    /*
     * SPRD: fix bug 473462 add burst capture
     */
    private void updateRequestBurstNumberMode(Camera2RequestSettingsSet requestSettings) {
        requestSettings.set(SPRD_CAPTURE_MODE, mBurstNumber);
    }

    /*
     * SPRD Bug:474721 Feature:Contrast. @{
     */
    private void updateRequestContrast(Camera2RequestSettingsSet requestSettings) {
        Integer mode = null;
        if (mCurrentContrast != null) {
            switch (mCurrentContrast) {
                case CONTRAST_ZERO: {
                    mode = 0;
                    break;
                }
                case CONTRAST_ONE: {
                    mode = 1;
                    break;
                }
                case CONTRAST_TWO: {
                    mode = 2;
                    break;
                }
                case CONTRAST_THREE: {
                    mode = 3;
                    break;
                }
                case CONTRAST_FOUR: {
                    mode = 4;
                    break;
                }
                case CONTRAST_FIVE: {
                    mode = 5;
                    break;
                }
                case CONTRAST_SIX: {
                    mode = 6;
                    break;
                }
                default: {
                    Log.w(TAG, " convertContrastMode - Unknown Contrast mode " + mode);
                    break;
                }
            }
            requestSettings.set(CONTROL_CONTRAST_MODE, mode);
        }
    }
    /* @} */

    // SPRD Bug:474715 Feature:Brightness.
    private void updateRequestBrightNess(Camera2RequestSettingsSet requestSettings) {
        Integer mode = null;
        if (mBrightNess != null) {
            switch (mBrightNess) {
                case BRIGHTNESS_ZERO: {
                    mode = CONTROL_BRIGHTNESS_ZERO;
                    break;
                }
                case BRIGHTNESS_ONE: {
                    mode = CONTROL_BRIGHTNESS_ONE;
                    break;
                }
                case BRIGHTNESS_TWO: {
                    mode = CONTROL_BRIGHTNESS_TWO;
                    break;
                }
                case BRIGHTNESS_THREE: {
                    mode = CONTROL_BRIGHTNESS_THREE;
                    break;
                }
                case BRIGHTNESS_FOUR: {
                    mode = CONTROL_BRIGHTNESS_FOUR;
                    break;
                }
                case BRIGHTNESS_FIVE: {
                    mode = CONTROL_BRIGHTNESS_FIVE;
                    break;
                }
                case BRIGHTNESS_SIX: {
                    mode = CONTROL_BRIGHTNESS_SIX;
                    break;
                }
                default: {
                    break;
                }
            }
            requestSettings.set(CONTROL_BRIGHTNESS_MODE, mode);
        }
    }

    /*
     * SPRD Bug:474724 Feature:ISO. @{
     */
    private void updateRequestISO(Camera2RequestSettingsSet requestSettings) {
        Integer mode = null;
        Log.i(TAG, "updateRequestISO mISO = " + mISO);
        if (mISO != null) {
            switch (mISO) {
                case AUTO: {
                    mode = CONTROL_ISO_MODE_AUTO;
                    break;
                }
                case ISO_1600: {
                    mode = CONTROL_ISO_MODE_1600;
                    break;
                }
                case ISO_800: {
                    mode = CONTROL_ISO_MODE_800;
                    break;
                }
                case ISO_400: {
                    mode = CONTROL_ISO_MODE_400;
                    break;
                }
                case ISO_200: {
                    mode = CONTROL_ISO_MODE_200;
                    break;
                }
                case ISO_100: {
                    mode = CONTROL_ISO_MODE_100;
                    break;
                }
                default: {
                    break;
                }
            }
            requestSettings.set(CONTROL_ISO_MODE, mode);
        }
    }
    /* @} */

    /*
     * SPRD Bug:474718 Feature:Metering. @{
     */
    private void updateRequestMetering(Camera2RequestSettingsSet requestSettings) {
        Integer mode = null;
        if (mMetering != null) {
            switch (mMetering) {
                case FRAMEAVERAGE: {
                    mode = CONTROL_METERING_FRAMEAVERAGE;
                    break;
                }
                case CENTERWEIGHTED: {
                    mode = CONTROL_METERING_CENTERWEIGHTED;
                    break;
                }
                case SPOTMETERING: {
                    mode = CONTROL_METERING_SPOTMETERING;
                    break;
                }
                default: {
                    Log.w(TAG, " convertMetering - Unknown Metering mode " + mode);
                    break;
                }
            }
            requestSettings.set(CONTROL_METERING_MODE, mode);
        }
    }
    /* @} */

    /*
     * SPRD Bug:474722 Feature:Saturation. @{
     */
    private void updateRequestSaturation(Camera2RequestSettingsSet requestSettings) {
        Integer mode = null;
        if (mCurrentSaturation != null) {
            switch (mCurrentSaturation) {
                case SATURATION_ZERO: {
                    mode = 0;
                    break;
                }
                case SATURATION_ONE: {
                    mode = 1;
                    break;
                }
                case SATURATION_TWO: {
                    mode = 2;
                    break;
                }
                case SATURATION_THREE: {
                    mode = 3;
                    break;
                }
                case SATURATION_FOUR: {
                    mode = 4;
                    break;
                }
                case SATURATION_FIVE: {
                    mode = 5;
                    break;
                }
                case SATURATION_SIX: {
                    mode = 6;
                    break;
                }
                default: {
                    Log.w(TAG, " convertSaturationMode - Unknown saturation mode " + mode);
                    break;
                }
            }
            requestSettings.set(CONTROL_SATURATION_MODE, mode);
        }
    }
    /* @} */

    /* SPRD: Fix bug 597206 that switch 3D preview between main camera and slave camera @{ */
    @Override
    protected boolean matchesTemplateDefault(Key<?> setting) {
        if (setting == ANDROID_SPRD_3D_PREVIEW_MODE) {
            return (mSprd3DPreviewMode != 0 && mSprd3DPreviewMode != 1);
        } else {
            return super.matchesTemplateDefault(setting);
        }
        /* @} */
    }
}
