
package com.android.ex.camera2.portability;

import static android.hardware.camera2.CameraCharacteristics.*;
import static android.hardware.camera2.CameraMetadata.*;

import android.hardware.camera2.CameraCharacteristics;
import com.android.ex.camera2.portability.debug.Log;
import com.android.ex.camera2.portability.SprdCameraCapabilities.*;
import com.android.ex.camera2.portability.CameraCapabilities.Stringifier;
import android.hardware.camera2.CameraCharacteristics.Key;

public class SprdAndroidCamera2Capabilities extends CameraCapabilities {

    private static Log.Tag TAG = new Log.Tag("SprdAndCam2Capabs");

    public static final Key<int[]> CONTROL_AVAILABLE_SLOWMOTION =
            new Key<int[]>("com.addParameters.availableSlowMotion", int[].class);
    public static final Key<int[]> ANDROID_SPRD_MAX_PREVIEW_SIZE =
            new Key<int[]>("com.addParameters.maxPreviewSize", int[].class);
    public static final Key<Integer> ANDROID_SPRD_VIDEO_SNAP_SUPPORT =
            new Key<Integer>("com.addParameters.videoSnapshotSupport", int.class);

    SprdAndroidCamera2Capabilities(Stringifier stringifier, CameraCharacteristics p) {
        super(stringifier);
        buildAntibandingModes(p);// SPRD:Add for antibanding
        buildColorEffects(p);// SPRD:Add for color effect Bug 474727
        buildContrast(p);// SPRD Bug:474721 Feature:Contrast.
        // SPRD Bug:474715 Feature:Brightness.
        buildBrightNess(p);
        // SPRD Bug:474724 Feature:ISO.
        buildISO(p);
        // SPRD Bug:474718 Feature:Metering.
        buildMetering(p);
        // SPRD Bug:474722 Feature:Saturation.
        buildSaturation(p);

        buildSmileEnable(p);
        buildAntibandAutoEnable(p);

        buildFlashLevels(p);
        buildAELock(p);
        buildAutoHdr(p);

        buildAuto3Dnr(p);

        buildSlowMotion(p);

        buildAiSceneEnable(p);

        try {
            int[] maxsize = p.get(ANDROID_SPRD_MAX_PREVIEW_SIZE);
            if (maxsize != null && maxsize.length > 1) {
                mPreferredPreviewSizeForVideo = new Size(maxsize[0], maxsize[1]);
            }
            Log.i(TAG, " mPreferredPreviewSizeForVideo " + mPreferredPreviewSizeForVideo);
        } catch (Exception e) {
            Log.e(TAG, " get ANDROID_SPRD_MAX_PREVIEW_SIZE error !");
        }
        int videoSnap = p.get(ANDROID_SPRD_VIDEO_SNAP_SUPPORT);
        if (videoSnap == 1) {
            mSupportedFeatures.add(Feature.VIDEO_SNAPSHOT);
        }
        if (p.get(CONTROL_AWB_LOCK_AVAILABLE)) {
            mSupportedFeatures.add(Feature.AUTO_WHITE_BALANCE_LOCK);
        }
    }

    private void buildAELock(CameraCharacteristics p) {
        if (p.get(CONTROL_AE_LOCK_AVAILABLE)) {
            mSupportedFeatures.add(Feature.AUTO_EXPOSURE_LOCK);
        }
    }

    private void buildFlashLevels(CameraCharacteristics p) {
        mSupportFlashLevel = p.get(CONTROL_AVAILABLE_FLASH_LEVELS);

        Log.i(TAG, "supportedFlashLevels = " + mSupportFlashLevel);
    }

    private void buildAutoHdr(CameraCharacteristics p) {
        mSupportAutoHdr = p.get(ANDROID_SPRD_AVAILABLE_AUTO_HDR);
        Log.i(TAG, "supportedAutoHdr = " + mSupportAutoHdr);
    }

    private void buildAuto3Dnr(CameraCharacteristics p) {
        mSupportAuto3Dnr = p.get(ANDROID_SPRD_AVAILABLE_AUTO_3DNR_SCENE);
        Log.i(TAG, "SupportAuto3Dnr = " + mSupportAuto3Dnr);
    }

    private void buildAntibandingModes(CameraCharacteristics p) {
        int[] antibanding = p.get(CONTROL_AE_AVAILABLE_ANTIBANDING_MODES);
        if (antibanding != null) {
            for (int antiband : antibanding) {
                Antibanding equiv = antibandingFromInt(antiband);
                if (equiv != null) {
                    mSupportedAntibanding.add(equiv);
                }
            }
        }
    }

    public static Antibanding antibandingFromInt(int antiband) {
        switch (antiband) {
            case STATISTICS_SCENE_FLICKER_NONE:
                return Antibanding.OFF;
            case STATISTICS_SCENE_FLICKER_50HZ:
                return Antibanding.ANTIBANDING_50HZ;
            case STATISTICS_SCENE_FLICKER_60HZ:
                return Antibanding.ANTIBANDING_60HZ;
        }
        Log.w(TAG, "Unable to convert from API 2 antibanding: " + antiband);
        return null;
    }

    /* SPRD:Add for color effect Bug 474727 @{ */
    private void buildColorEffects(CameraCharacteristics p) {
        int[] effects = p.get(CONTROL_AVAILABLE_EFFECTS);
        if (effects != null) {
            for (int effect : effects) {
                ColorEffect equiv = colorEffectFromInt(effect);
                if (equiv != null) {
                    mSupportedColorEffects.add(equiv);
                }
            }
        }
    }
    /* @} */

    /* SPRD:Add for color effect Bug 474727 @{ */
    public static ColorEffect colorEffectFromInt(int ce) {
        switch (ce) {
            case CONTROL_EFFECT_MODE_OFF:
                return ColorEffect.NONE;
            case CONTROL_EFFECT_MODE_MONO:
                return ColorEffect.MONO;
            case CONTROL_EFFECT_MODE_NEGATIVE:
                return ColorEffect.NEGATIVE;
            case CONTROL_EFFECT_MODE_SEPIA:
                return ColorEffect.SEPIA;
            case CONTROL_EFFECT_MODE_AQUA:
                return ColorEffect.COLD;
            case CONTROL_EFFECT_MODE_SOLARIZE:
                return ColorEffect.ANTIQUE;
        }
        Log.w(TAG, "Unable to convert from API 2 color effect: " + ce);
        return null;
    }
    /* @} */

    /*
     * SPRD Bug:474721 Feature:Contrast. @{
     */
    private void buildContrast(CameraCharacteristics p) {
        int[] contrast = p.get(CONTROL_AVAILABLE_CONTRAST);
        if (contrast != null) {
            for (int con : contrast) {
                Contrast equiv = contrastFromInt(con);
                if (equiv != null) {
                    mSupportedContrast.add(equiv);
                }
            }
        }
    }

    public static Contrast contrastFromInt(int contrast) {
        switch (contrast) {
            case 0:
                return Contrast.CONTRAST_ZERO;
            case 1:
                return Contrast.CONTRAST_ONE;
            case 2:
                return Contrast.CONTRAST_TWO;
            case 3:
                return Contrast.CONTRAST_THREE;
            case 4:
                return Contrast.CONTRAST_FOUR;
            case 5:
                return Contrast.CONTRAST_FIVE;
            case 6:
                return Contrast.CONTRAST_SIX;
        }
        Log.w(TAG, "Unable to convert from API 2 Contrast: " + contrast);
        return null;
    }
    /* @} */

    /*
     * SPRD Bug:474715 Feature:Brightness. @{
     */
    private void buildBrightNess(CameraCharacteristics p) {
        int[] brightness = p.get(SPRD_OEM_AVAILABLE_BRIGHTNESS);
        if (brightness != null) {
            for (int bright : brightness) {
                BrightNess equiv = brightnessFromInt(bright);
                if (equiv != null) {
                    mSupportedBrightNess.add(equiv);
                }
            }
        }
    }

    public static BrightNess brightnessFromInt(int brightness) {
        switch (brightness) {
            case CONTROL_BRIGHTNESS_ZERO:
                return BrightNess.BRIGHTNESS_ZERO;
            case CONTROL_BRIGHTNESS_ONE:
                return BrightNess.BRIGHTNESS_ONE;
            case CONTROL_BRIGHTNESS_TWO:
                return BrightNess.BRIGHTNESS_TWO;
            case CONTROL_BRIGHTNESS_THREE:
                return BrightNess.BRIGHTNESS_THREE;
            case CONTROL_BRIGHTNESS_FOUR:
                return BrightNess.BRIGHTNESS_FOUR;
            case CONTROL_BRIGHTNESS_FIVE:
                return BrightNess.BRIGHTNESS_FIVE;
            case CONTROL_BRIGHTNESS_SIX:
                return BrightNess.BRIGHTNESS_SIX;
        }
        Log.w(TAG, "Unable to convert from API 2 BrightNess: " + brightness);
        return null;
    }
    /* @} */

    /*
     * SPRD Bug:474724 Feature:ISO. @{
     */
    private void buildISO(CameraCharacteristics p) {
        int[] iso = p.get(CONTROL_AVAILABLE_ISO);
        if (iso != null) {
            for (int so : iso) {
                ISO equiv = isoFromInt(so);
                if (equiv != null) {
                    mSupportedISO.add(equiv);
                }
            }
        }
    }

    public static ISO isoFromInt(int iso) {
        switch (iso) {
            case CONTROL_ISO_MODE_AUTO:
                return ISO.AUTO;
            case CONTROL_ISO_MODE_1600:
                return ISO.ISO_1600;
            case CONTROL_ISO_MODE_800:
                return ISO.ISO_800;
            case CONTROL_ISO_MODE_400:
                return ISO.ISO_400;
            case CONTROL_ISO_MODE_200:
                return ISO.ISO_200;
            case CONTROL_ISO_MODE_100:
                return ISO.ISO_100;
        }
        Log.w(TAG, "Unable to convert from API 2 ISO: " + iso);
        return null;
    }
    /* @} */

    /*
     * SPRD Bug:474718 Feature:Metering. @{
     */
    private void buildMetering(CameraCharacteristics p) {
        int[] metering = p.get(CONTROL_AVAILABLE_METERING);
        if (metering != null) {
            for (int meter : metering) {
                Metering equiv = meteringFromInt(meter);
                if (equiv != null) {
                    mSupportedMetering.add(equiv);
                }
            }
        }
    }

    public static Metering meteringFromInt(int metering) {
        switch (metering) {
            case CONTROL_METERING_FRAMEAVERAGE:
                return Metering.FRAMEAVERAGE;
            case CONTROL_METERING_CENTERWEIGHTED:
                return Metering.CENTERWEIGHTED;
            case CONTROL_METERING_SPOTMETERING:
                return Metering.SPOTMETERING;
        }
        Log.w(TAG, "Unable to convert from API 2 Metering: " + metering);
        return null;
    }
    /* @} */

    /*
     * SPRD Bug:474722 Feature:Saturation. @{
     */
    private void buildSaturation(CameraCharacteristics p) {
        int[] saturation = p.get(CONTROL_AVAILABLE_SATURATION);
        if (saturation != null) {
            for (int sat : saturation) {
                Saturation equiv = saturationFromInt(sat);
                if (equiv != null) {
                    mSupportedSaturation.add(equiv);
                }
            }
        }
    }

    public static Saturation saturationFromInt(int saturation) {
        switch (saturation) {
            case 0:
                return Saturation.SATURATION_ZERO;
            case 1:
                return Saturation.SATURATION_ONE;
            case 2:
                return Saturation.SATURATION_TWO;
            case 3:
                return Saturation.SATURATION_THREE;
            case 4:
                return Saturation.SATURATION_FOUR;
            case 5:
                return Saturation.SATURATION_FIVE;
            case 6:
                return Saturation.SATURATION_SIX;
        }
        Log.w(TAG, "Unable to convert from API 2 Saturation: " + saturation);
        return null;
    }
    /* @} */

    private void buildSmileEnable(CameraCharacteristics p) {
        int supportedSmileEnable = p.get(CONTROL_AVAILABLE_SMILEENABLE);
        Log.d(TAG, "supportedSmileEnable = " + supportedSmileEnable);
        if (1 == supportedSmileEnable) {
            mSupportedSmileEnable = true;
        } else {
            mSupportedSmileEnable = false;
        }
    }

    private void buildAntibandAutoEnable(CameraCharacteristics p) {
        int supportedAntibandAutoEnable = p.get(CONTROL_AVAILABLE_ANTIBAND_AUTO);
        Log.d(TAG, "supportedAntibandAutoEnable = " + supportedAntibandAutoEnable);
        if (1 == supportedAntibandAutoEnable) {
            mSupportedAntibandAutoEnable = true;
        } else {
            mSupportedAntibandAutoEnable = false;
        }
    }

    private void buildSlowMotion(CameraCharacteristics p) {
        int[] slowmotion = p.get(CONTROL_AVAILABLE_SLOWMOTION);
        if (slowmotion != null) {
            for (int sm : slowmotion) {
                mSupportedSlowMotion.add("" + sm);
            }
        }
    }

    private void buildAiSceneEnable(CameraCharacteristics p) {
        int supportedAiScene = p.get(CONTROL_AVAILABLE_AI_SCENE);
        Log.i(TAG, "mSupportedAiScene = " + supportedAiScene);
        if (1 == supportedAiScene)
            mSupportedAiScene = true;
    }
}
