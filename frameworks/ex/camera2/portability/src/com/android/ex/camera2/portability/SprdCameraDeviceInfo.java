
package com.android.ex.camera2.portability;

public interface SprdCameraDeviceInfo {
    /**
     * SPRD:add for smile capture Bug548832
     * 
     * @return whether the device support smile-shutter
     */
    boolean getSmileEnable();

    /**
     * SPRD:add for antiband auto Bug549740
     * 
     * @return whether the device support antiband auto
     */
    boolean getAntibandAutoEnable();
}
