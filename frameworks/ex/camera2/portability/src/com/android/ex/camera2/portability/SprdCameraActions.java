
package com.android.ex.camera2.portability;

public class SprdCameraActions {
    /* SPRD: fix bug 473462 add burst capture @{ */
    public static final int CAPTURE_BURST_PHOTO = 602;
    public static final int CANCEL_CAPTURE_BURST_PHOTO = 603;
    /* @} */
    public static final int SET_SENSOR_SELF_SHOT_LISTENER = 711;

    public static final int SET_PREVIEW_TEXTURE_ASYNC_WITHOUT_OPTIMIZE = 109;
    public static final int SET_PREVIEW_DISPLAY_ASYNC_WITHOUT_OPTIMIZE = 110;
    public static final int STOP_PREVIEW_WITHOUT_FLUSH = 111;
    // SPRD:add for saving normal pic for HDR
    public static final int CAPTURE_HDR_PHOTO = 701;

    public static final int CAPTURE_PHOTO_WITH_THUMB = 702;

    // SPRD: add for thumb resolution, release surface and flush preview buffer.
    public static final int RELEASE_FOR_THUMB = 801;
    public static final int SET_HDR_SCENE_LISTENER = 802;
    public static final int START_VIDEO_RECORDER = 901;
    public static final int CAPTURE_PHOTO_WITH_SNAP = 902;

    public static final int SET_AI_SCENE_LISTENER = 803;
    public static final int SET_AUTO3DNR_SCENE_LISTENER = 805;

    public static String stringifySprd(int action) {
        switch (action) {
            /* SPRD:fix bug 473462 add burst capture @{*/
            case CAPTURE_BURST_PHOTO:
                return "CAPTURE_BURST_PHOTO";
            case CANCEL_CAPTURE_BURST_PHOTO:
                return "CANCEL_CAPTURE_BURST_PHOTO";
                /* @} */
            case SET_PREVIEW_DISPLAY_ASYNC_WITHOUT_OPTIMIZE:
                return "SET_PREVIEW_DISPLAY_ASYNC_WITHOUT_OPTIMIZE";
            case STOP_PREVIEW_WITHOUT_FLUSH:
                return "STOP_PREVIEW_WITHOUT_FLUSH";
            case SET_SENSOR_SELF_SHOT_LISTENER:
                return "SET_SENSOR_SELF_SHOT_LISTENER";
            // SPRD:add for saving normal pic for HDR
            case CAPTURE_HDR_PHOTO:
                return "CAPTURE_HDR_PHOTO";
            case CAPTURE_PHOTO_WITH_THUMB:
                return "CAPTURE_PHOTO_WITH_THUMB";
            case RELEASE_FOR_THUMB:
                return "RELEASE_FOR_THUMB";
            case START_VIDEO_RECORDER:
                return "START_VIDEO_RECORDER";
            case CAPTURE_PHOTO_WITH_SNAP:
                return "CAPTURE_PHOTO_WITH_SNAP";
            default:
                return "UNKNOWN(" + action + ")";
        }
    }
}
