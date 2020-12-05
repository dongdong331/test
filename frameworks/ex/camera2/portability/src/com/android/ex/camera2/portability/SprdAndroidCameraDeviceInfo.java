
package com.android.ex.camera2.portability;

import android.hardware.Camera;

import com.android.ex.camera2.portability.CameraDeviceInfo;
import static com.android.ex.camera2.portability.CameraAgent.*;

public abstract class SprdAndroidCameraDeviceInfo implements CameraDeviceInfo {
    // SPRD:add for smile capture Bug548832
    private boolean isSmileEnable = false;
    private boolean isAntibandAuto = false;

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

    public int getTruthCameraId(int cameraId) {
        /* SPRD: Fix bug 585183 Adds new features 3D recording @{ */
        if (SPRD_3D_VIDEO_ID == cameraId) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else if (SPRD_RANGE_FINDER_ID == cameraId) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        } else if (SPRD_3D_CAPTURE_ID == cameraId) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else if (SPRD_3D_PREVIEW_ID == cameraId) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else if (SPRD_SOFY_OPTICAL_ZOOM_ID == cameraId) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        } else if (SPRD_BLUR_ID == cameraId) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        } else if (SPRD_SELF_SHOT_ID == cameraId) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else if (SPRD_BLUR_FRONT_ID == cameraId) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        }  else if (3 == cameraId) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        } else if (SPRD_BACK_ULTRA_WIDE_ANGLE_ID == cameraId) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        }
        /* @} */
        return cameraId;
    }
}
