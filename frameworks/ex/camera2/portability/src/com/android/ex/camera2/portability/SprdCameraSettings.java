
package com.android.ex.camera2.portability;

import android.hardware.Camera;

import com.android.ex.camera2.portability.debug.Log;

import java.util.List;

public abstract class SprdCameraSettings {
    private static final Log.Tag TAG = new Log.Tag("SprdCamSettings");

    protected CameraCapabilities.Antibanding mAntibanding;
    protected CameraCapabilities.ColorEffect mCurrentColorEffect;// SPRD:Add for color effect Bug
                                                                 // 474727
    protected int mBurstNumber;
    protected int mCurrentEnableZslMode = 0;// SPRD:fix bug 473462 add for burst capture or zsl
    protected CameraCapabilities.Contrast mCurrentContrast;
    protected int[] mBeatuyLevel = new int[]{0,0,0,0,0,0,0,0,0};
    protected String mBeatuyLevelForApi1;
    protected int mCurrentEnableHighISO = 0;//SPRD Add for highiso

    protected int mCurrentEnableBurstMode = -1;
    protected int mCurrentEnableSensorSelfShot = -1;
    protected int mCurrentAppModeId = -1; // SPRD: Fix bug675012/819184, duration of timelapse recording video is incorrect
//    <integer name="camera_mode_auto_photo">0</integer>
//    <integer name="camera_mode_manual">1</integer>
//    <integer name="camera_mode_continue">2</integer>
//    <integer name="camera_mode_interval">3</integer>
//    <integer name="camera_mode_panorama">4</integer>
//    <integer name="camera_mode_refocus">5</integer>
//    <integer name="camera_mode_scene">6</integer>
//    <integer name="camera_mode_pip">7</integer>
//    <integer name="camera_mode_gcam">8</integer>
//    <integer name="camera_mode_auto_video">9</integer>
//    <integer name="camera_mode_viv">10</integer>
//    <integer name="camera_mode_timelapse">11</integer>
//    <integer name="camera_mode_slowmotion">12</integer>
//    <integer name="camera_mode_audio_picture">13</integer>
//    <integer name="camera_mode_filter">14</integer>
//    <integer name="camera_mode_qrcode">15</integer>
//    <integer name="camera_mode_video_td">16</integer>
//    <integer name="camera_mode_photo_td">17</integer>
//    <integer name="camera_mode_front_blurrefocus">18</integer>
//    <integer name="camera_mode_td_range_find">19</integer>
//    <integer name="camera_mode_3dnr_photo">20</integer>
//    <integer name="camera_mode_3dnr_video">21</integer>

    // SPRD: Fix bug 600110 that the rotation of 3d video content is not correct
    protected int mSprdDeviceRotationFor3DRecord = 0;
    protected int mSprdDeviceOrientation = -1;
    // SPRD: Fix bug 597206 that switch 3D preview between main camera and slave camera.
    protected int mSprd3DPreviewMode = Integer.MAX_VALUE;
    /* SPRD: Fix bug 591216 that add new feature 3d range find, only support API2 currently @{ */
    protected int[] mSprd3DRangeFindPoints;
    /* @} */
    protected int mSprdFNumber = -1;
    protected int mBlurCircleSize = -1;
    protected int mCurrentEnableNormalHdrMode = -1;
    protected int mCurrentEnable3dnr = -1;
    protected int mCurrentFilterType = -1;
    protected int mCurrentEnableAiScene= -1;

    protected static final int[] MAKE_UP_DEFAULT_VALUE = new int[]{0,0,0,0,0,0,0,0,0};
    protected boolean mCurrentNeedThumb = false;
    protected int mFastThumbEnable = -1;
    protected boolean mEnterVideoMode = false;

    protected SprdCameraSettings() {}

    protected SprdCameraSettings(SprdCameraSettings src) {
        mAntibanding = src.mAntibanding;
        mCurrentColorEffect = src.mCurrentColorEffect;// SPRD:Add for color effect Bug 474727
        /* SPRD:fix 473462 bug add burst capture @{ */
        mBurstNumber = src.mBurstNumber;
        mCurrentEnableZslMode = src.mCurrentEnableZslMode;
        mCurrentEnableNormalHdrMode = src.mCurrentEnableNormalHdrMode;
        mSprdFNumber = src.mSprdFNumber;
        mBlurCircleSize = src.mBlurCircleSize;
        mCurrentEnableSensorSelfShot = src.mCurrentEnableSensorSelfShot;
        mSprdDeviceOrientation = src.mSprdDeviceOrientation;
        /* @} */
        // SPRD Bug:474721 Feature:Contrast.
        mCurrentContrast = src.mCurrentContrast;
        mBrightNess = src.mBrightNess;// SPRD Bug:474715 Feature:Brightness.
        // SPRD Bug:474724 Feature:ISO.
        mISO = src.mISO;
        // SPRD Bug:474718 Feature:Metering.
        mMetering = src.mMetering;
        // SPRD Bug:474722 Feature:Saturation.
        mCurrentSaturation = src.mCurrentSaturation;
        mBeatuyLevel = src.mBeatuyLevel;
        // SPRD Bug:474696 Feature:Slow-Motion.
        mCurrentSlowMotion = src.mCurrentSlowMotion;
        // SPRD Bug:500099 Feature:mirror.
        mFrontCameraMirror = src.mFrontCameraMirror;
        // SPRD Feature: EOIS.
        mEOISEnable = src.mEOISEnable;

        mCurrentEnableHighISO = src.mCurrentEnableHighISO;
        mCurrentEnableBurstMode = src.mCurrentEnableBurstMode;

        // SPRD: Fix bug675012, duration of timelapse recording video is incorrect
        mCurrentAppModeId = src.mCurrentAppModeId;

        // SPRD: Fix bug 600110 that the rotation of 3d video content is not correct
        mSprdDeviceRotationFor3DRecord = src.mSprdDeviceRotationFor3DRecord;

        // SPRD: Fix bug 597206 that switch 3D preview between main camera and slave camera.
        mSprd3DPreviewMode = src.mSprd3DPreviewMode;
        /* SPRD: Fix bug 591216 that add new feature 3d range find, only support API2 currently @{ */
        mSprd3DRangeFindPoints = src.mSprd3DRangeFindPoints;
        /* @} */
        mCurrentEnable3dnr = src.mCurrentEnable3dnr;
        mCurrentFilterType = src.mCurrentFilterType;
        mCurrentNeedThumb = src.mCurrentNeedThumb;
        mFastThumbEnable = src.mFastThumbEnable;
        mFlashLevel = src.mFlashLevel;
        isAutoHdr = src.isAutoHdr;
        mEnterVideoMode = src.mEnterVideoMode;
        mAiScene = src.mAiScene;
        mCurrentEnableAiScene = src.mCurrentEnableAiScene;
        mCurrentEnableAuto3Dnr = src.mCurrentEnableAuto3Dnr;
        mCurrentEnableAuto3dnrCapture = src.mCurrentEnableAuto3dnrCapture;
    }

    public void setAntibanding(CameraCapabilities.Antibanding antibanding) {
        mAntibanding = antibanding;
    }

    public CameraCapabilities.Antibanding getAntibanding() {
        return mAntibanding;
    }

    /* SPRD:Add for color effect Bug 474727 @{ */
    public void setColorEffect(CameraCapabilities.ColorEffect colorEffect) {
        mCurrentColorEffect = colorEffect;
    }

    public CameraCapabilities.ColorEffect getCurrentColorEffect() {
        return mCurrentColorEffect;
    }
    /* @} */

    /*
     * SPRD:fix bug 473462 add burst capture
     */
    public void setBurstPicNum(int count) {
        mBurstNumber = count;
    }

    public int getBurstPicNum() {
        return mBurstNumber;
    }

    /*
     * SPRD:fix bug 473462 add zsl mode for api2
     */
    public void setZslModeEnable(int enable) {
        mCurrentEnableZslMode = enable;
    }

    public int getZslModeEnable() {
        return mCurrentEnableZslMode;
    }

    public void setNormalHdrModeEnable(int enable) {
        mCurrentEnableNormalHdrMode = enable;
    }

    public int getNormalHdrModeEnable() {
        return mCurrentEnableNormalHdrMode;
    }

    /*
     * SPRD Bug:474721 Feature:Contrast. @{
     */
    public void setContrast(CameraCapabilities.Contrast contrast) {
        mCurrentContrast = contrast;
    }

    public CameraCapabilities.Contrast getCurrentContrast() {
        return mCurrentContrast;
    }
    /* @} */

    /*
     * SPRD Bug:474715 Feature:Brightness. @{
     */
    protected CameraCapabilities.BrightNess mBrightNess;

    public void setBrightNess(CameraCapabilities.BrightNess brightness) {
        mBrightNess = brightness;
    }

    public CameraCapabilities.BrightNess getBrightNess() {
        return mBrightNess;
    }
    /* @} */

    /*
     * SPRD Bug:474724 Feature:ISO. @{
     */
    protected CameraCapabilities.ISO mISO;

    public void setISO(CameraCapabilities.ISO iso) {
        mISO = iso;
    }

    public CameraCapabilities.ISO getISO() {
        return mISO;
    }
    /* @} */

    /*
     * SPRD Bug:474718 Feature:Metering. @{
     */
    protected CameraCapabilities.Metering mMetering;

    public void setMetering(CameraCapabilities.Metering metering) {
        mMetering = metering;
    }

    public CameraCapabilities.Metering getMetering() {
        return mMetering;
    }
    /* @} */

    /*
     * SPRD Bug:474722 Feature:Saturation. @{
     */
    protected CameraCapabilities.Saturation mCurrentSaturation;

    public void setSaturation(CameraCapabilities.Saturation saturation) {
        mCurrentSaturation = saturation;
    }

    public CameraCapabilities.Saturation getCurrentSaturation() {
        return mCurrentSaturation;
    }
    /* @} */

    public void setSkinWhitenLevel(int level) {
        if (level == 0) {
            setSkinWhitenLevel(MAKE_UP_DEFAULT_VALUE);
        }
    }

    public void setSkinWhitenLevel(String level) {
        if (level == null) {
            return;
        } else if (level.equals("0")) {
            setSkinWhitenLevel(MAKE_UP_DEFAULT_VALUE);
        } else {
            String[] beatuyLevel = level.split(",");
            for (int i = 0; i < beatuyLevel.length; i++) {
                mBeatuyLevel[i] = Integer.parseInt(beatuyLevel[i]);
            }
        }
    }

    public void setSkinWhitenLevel(int[] level) {
        if (level == null) {
            return;
        }
        mBeatuyLevel = level;
    }

    public String getSkinWhitenLevel() {
        if (mBeatuyLevel != null) {
            mBeatuyLevelForApi1 = String.valueOf(mBeatuyLevel[0]);
            for (int i = 1; i < mBeatuyLevel.length; i++) {
                mBeatuyLevelForApi1 = mBeatuyLevelForApi1 + ","+mBeatuyLevel[i] ;
            }
        } else {
            mBeatuyLevelForApi1 = "0";
            for (int i = 1; i < MAKE_UP_DEFAULT_VALUE.length; i++) {
                mBeatuyLevelForApi1 = mBeatuyLevelForApi1 +",0";
            }
        }
        return mBeatuyLevelForApi1;
    }

    /*
     * SPRD Bug:474696 Feature:Slow-Motion. @{
     */
    protected String mCurrentSlowMotion;

    public void setVideoSlowMotion(String slowmotion) {
        mCurrentSlowMotion = slowmotion;
    }

    public String getCurrentVideoSlowMotion() {
        return mCurrentSlowMotion;
    }
    /* @} */

    /*
     * SPRD Bug:500099 Feature:mirror. @{
     */
    protected boolean mFrontCameraMirror;

    public void setFrontCameraMirror(boolean frontCameraMirror) {
        mFrontCameraMirror = frontCameraMirror;
    }

    public boolean getfrontCameraMirror() {
        return mFrontCameraMirror;
    }
    /* @} */

    /*
     * SPRD Feature: EOIS. @{
     */
    protected boolean mEOISEnable;

    public void setEOISEnable(boolean eois) {
        mEOISEnable = eois;
    }

    public boolean getEOISEnable() {
        return mEOISEnable;
    }
    /* @} */

    /* SPRD Add for highiso 556862 @{ */
    public void setHighISOEnable(int enable) {
        mCurrentEnableHighISO = enable;
    }

    public int getHighISOEnable() {
        return mCurrentEnableHighISO;
    }
    /* @} */

    public void setBurstModeEnable(int enable) {
        mCurrentEnableBurstMode = enable;
    }

    public int getBurstModeEnable() {
        return mCurrentEnableBurstMode;
    }

    public void setSensorSelfShotEnable(int enable) {
        mCurrentEnableSensorSelfShot = enable;
    }

    public int getSensorSelfShotEnable() {
        return mCurrentEnableSensorSelfShot;
    }

    /* SPRD: Fix bug675012, duration of timelapse recording video is incorrect @{ */
    public void setAppModeId(int id) {
        mCurrentAppModeId = id;
    }

    public int getAppModeId() {
        return mCurrentAppModeId;
    }
    /* @} */

    public void setFNumberValue(int value) {
        mSprdFNumber = value;
    }

    public int getFNumberValue() {
        return mSprdFNumber;
    }

    public void setCircleSize(int value) {
        mBlurCircleSize = value;
    }

    public int getCircleSize() {
        return mBlurCircleSize;
    }

    /* SPRD: Fix bug 600110 that the rotation of 3d video content is not correct @{ */
    public void setDeviceOrientation(int deviceRotation) {
        mSprdDeviceOrientation = deviceRotation;
    }

    public int getDeviceRotationForFrontBlur() {
        return mSprdDeviceOrientation;
    }

    /* SPRD: Fix bug 600110 that the rotation of 3d video content is not correct @{ */
    public void setDeviceRotationFor3DRecord(int deviceRotation) {
        mSprdDeviceRotationFor3DRecord = deviceRotation;
    }

    public int getDeviceRotationFor3DRecord() {
        return mSprdDeviceRotationFor3DRecord;
    }
    /* @} */

    /**
     * SPRD: Fix bug 597206 that switch 3D preview between main camera and slave camera.
     * 
     * @param previewMode The preview mode to use. 0 -- Just main camera preview is enabled 1 --
     *            Just slave camera preview is enabled @{
     */
    public void set3DPreviewMode(int previewMode) {
        mSprd3DPreviewMode = previewMode;
    }

    public int get3DPreviewMode() {
        return mSprd3DPreviewMode;
    }
    /* @} */

    /* SPRD: Fix bug 591216 that add new feature 3d range finding, only support API2 currently @{ */
    public void set3DRangeFindPoints(int[] points) {
        if (points == null) {
            Log.i(TAG, "range find points error");
            return;
        }
        mSprd3DRangeFindPoints = points;
    }

    public int[] get3DRangeFindPoints() {
        return mSprd3DRangeFindPoints;
    }
    /* @} */

    public abstract void setFocusAreas(List<Camera.Area> areas);

    public abstract void setMeteringAreas(List<Camera.Area> areas);

    public abstract void setFocusMode(CameraCapabilities.FocusMode focusMode);

    public abstract void setPhotoJpegCompressionQuality(int quality);

    public abstract void setExposureCompensationIndex(int index);

    public abstract void setSceneMode(CameraCapabilities.SceneMode sceneMode);

    public abstract void setWhiteBalance(CameraCapabilities.WhiteBalance whiteBalance);

    public abstract void setFlashMode(CameraCapabilities.FlashMode flashMode);

    /* SPRD:fix bug616836 add for api1 use reconnect @ */
    public void setDefault(boolean isLcd, CameraCapabilities cameraCapabilities) {
        setFocusAreas(null);
        setMeteringAreas(null);
        setFocusMode(CameraCapabilities.FocusMode.CONTINUOUS_PICTURE);
        setAntibanding(CameraCapabilities.Antibanding.AUTO);
        setPhotoJpegCompressionQuality(95);
        setExposureCompensationIndex(0);
        setSceneMode(CameraCapabilities.SceneMode.AUTO);
        setWhiteBalance(CameraCapabilities.WhiteBalance.AUTO);
        setColorEffect(CameraCapabilities.ColorEffect.NONE);
        setSkinWhitenLevel(MAKE_UP_DEFAULT_VALUE);
        if(cameraCapabilities != null ){
            if(!isLcd && cameraCapabilities.isSupportFlash() && cameraCapabilities.supports(CameraCapabilities.FlashMode.OFF)){
                setFlashMode(CameraCapabilities.FlashMode.OFF);
            } else if(isLcd) {
                setFlashMode(CameraCapabilities.FlashMode.OFF);
            }
        }
        setFlashLevel(FLASH_LEVEL_DEFAULT_VALUE);
        setContrast(CameraCapabilities.Contrast.CONTRAST_THREE);
        setBrightNess(CameraCapabilities.BrightNess.BRIGHTNESS_THREE);
        setSaturation(CameraCapabilities.Saturation.SATURATION_THREE);
        //setISO(CameraCapabilities.ISO.AUTO);//SPRD:fix bug706699 for diff manual and non-maual mode
        setMetering(CameraCapabilities.Metering.CENTERWEIGHTED);
        setFrontCameraMirror(false);
        setZslModeEnable(0);
        setEOISEnable(false);
        setAppModeId(-1);
        setAutoHdr(false);
        setVideoSlowMotion("1");
        setCurrentAiSenceEnable(0);
    }
    /* @} */
    public void set3DNREnable(int enable) {
        mCurrentEnable3dnr = enable;
    }

    public int get3DNREnable() {
        return mCurrentEnable3dnr;
    }

    public void setFilterType(int type) {
        mCurrentFilterType = type;
    }

    public int getFilterType() {
        return mCurrentFilterType;
    }

    public void setNeedThumbCallBack(boolean need) {
        mCurrentNeedThumb = need;
    }

    public boolean getNeedThumbCallBack() {
        return mCurrentNeedThumb;
    }

    public void setThumbCallBack(int enable) {
        mFastThumbEnable = enable;
    }

    public int getThumbCallBack() {
        return mFastThumbEnable;
    }

    public int mFlashLevel = -1;
    private static final int FLASH_LEVEL_DEFAULT_VALUE = 0;
    public void setFlashLevel(int flashLevel) {
        mFlashLevel = flashLevel;
    }

    public int getFlashLevel(){
        return mFlashLevel;
    }
    public int isAutoHdr = -1;
    public void setAutoHdr(boolean isOn){isAutoHdr = isOn ? 1 : 0;}

    public void setEnterVideoMode(boolean video) {
        mEnterVideoMode = video;
    }

    public boolean getEnterVideoMode() {
        return mEnterVideoMode;
    }

    public int mAiScene = -1;

    public void setCurrentAiSenceEnable(int enable) {
        mCurrentEnableAiScene = enable;
    }

    public int getCurrentAiSenceEnable() {
        return mCurrentEnableAiScene;
    }

    // add tag for 3dnr and auto3dnr
    public int mCurrentEnableAuto3Dnr = -1;

    public void setAuto3DnrEnable(int enable){
        mCurrentEnableAuto3Dnr = enable;
    }

    public int getCurrentAuto3DnrEnable(){
        return mCurrentEnableAuto3Dnr;
    }

    public int mCurrentEnableAuto3dnrCapture = -1;

    public void setAuto3dnrCaptureEnable(int enable){
        mCurrentEnableAuto3dnrCapture = enable;
    }

    public int getCurrentAuto3dnrCaptureEnable() {
        return mCurrentEnableAuto3dnrCapture;
    }
}
