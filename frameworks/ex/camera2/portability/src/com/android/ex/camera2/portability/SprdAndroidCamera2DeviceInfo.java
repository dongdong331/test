
package com.android.ex.camera2.portability;

import android.hardware.Camera;
import android.hardware.camera2.CameraManager;
import static android.hardware.camera2.CameraCharacteristics.CONTROL_AVAILABLE_SMILEENABLE;
import static android.hardware.camera2.CameraCharacteristics.CONTROL_AVAILABLE_ANTIBAND_AUTO;
import static com.android.ex.camera2.portability.CameraAgent.*;

public abstract class SprdAndroidCamera2DeviceInfo implements CameraDeviceInfo {
    // SPRD:add for smile capture Bug548832
    private boolean isSmileEnable = false;
    private boolean isAntibandAuto = false;

    public void updateFeatureEnable(CameraManager cameraManager, String cameraId) {
        /* SPRD:add for smile capture Bug548832 @{ */
        try {
            isSmileEnable = (cameraManager.getCameraCharacteristics(cameraId)
                    .get(CONTROL_AVAILABLE_SMILEENABLE) == 1);
            isAntibandAuto = (cameraManager.getCameraCharacteristics(cameraId)
                    .get(CONTROL_AVAILABLE_ANTIBAND_AUTO) == 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        /* @} */
    }

    /* SPRD:add for smile capture Bug548832 @{ */
    @Override
    public boolean getSmileEnable() {
        return isSmileEnable;
    }
    /* @} */

    /* SPRD:add for antiband auto Bug549740 @{ */
    @Override
    public boolean getAntibandAutoEnable() {
        return isAntibandAuto;
    }
    /* @} */

    public int getActualCameraId(int cameraId) {
        /* SPRD: Fix bug 585183 Adds new features 3D recording @{ */
        switch (cameraId) {
            case SPRD_3D_VIDEO_ID:
            case SPRD_3D_CAPTURE_ID:
            case SPRD_3D_PREVIEW_ID:
            case SPRD_SELF_SHOT_ID:
            case SPRD_BLUR_FRONT_ID:
                return Camera.CameraInfo.CAMERA_FACING_FRONT;
            case SPRD_RANGE_FINDER_ID:
            case SPRD_BLUR_ID:
            //case 3:
            case SPRD_SOFY_OPTICAL_ZOOM_ID:
            case SPRD_BACK_ULTRA_WIDE_ANGLE_ID:
                return Camera.CameraInfo.CAMERA_FACING_BACK;
            default:
                return cameraId;
        }
        /* @} */
    }
}
