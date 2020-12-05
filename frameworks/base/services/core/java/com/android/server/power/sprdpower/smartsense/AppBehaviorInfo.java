/*
 ** Copyright 2018 The Spreadtrum.com
 */
package com.android.server.power.sprdpower;

import android.util.Slog;
import android.os.sprdpower.Util;


////////////////////////////

public class AppBehaviorInfo {
    private static final String TAG = "SSense.AppBehaviorInfo";
    private static final boolean DEBUG = Util.isDebug();
    private static final boolean DEBUG_MORE = false;


    public static final int REQUEST_GPS_TYPE_NONE = 0;
    public static final int REQUEST_GPS_TYPE_GPS = 1;

    public static final int REQUEST_AUDIO_TYPE_NONE = 0;
    public static final int REQUEST_AUDIO_TYPE_START = 1;

    public static final int REQUEST_VIDEO_TYPE_NONE = 0;
    public static final int REQUEST_VIDEO_TYPE_START_NORMAL = 1<<0;
    public static final int REQUEST_VIDEO_TYPE_START_CODEC = 1<<1;

    public static final int REQUEST_FPS_TYPE_NONE= 0;
    public static final int REQUEST_FPS_TYPE_START= 1;

    public static final int VIDEO_START_TYPE_NONE = 0;
    public static final int VIDEO_START_TYPE_NORMAL = 1;
    public static final int VIDEO_START_TYPE_FPS = 2;

    public static final int REQUEST_SCREEN_TYPE_NONE = 0;
    public static final int REQUEST_SCREEN_TYPE_VFULL = 1; // V full screen
    public static final int REQUEST_SCREEN_TYPE_HFULL = 2; // H full screen may with status bar
    public static final int REQUEST_SCREEN_TYPE_HFULL_EXACT = 3; // H full screen without status bar
    public static final int REQUEST_SCREEN_TYPE_NORMAL = 4;
    public static final int REQUEST_SCREEN_TYPE_SMALL = 5;

    public static final int REQUEST_SENSOR_TYPE_NONE = 0;

    public static final int REQUEST_TOUCH_TYPE_NONE= 0;
    public static final int REQUEST_TOUCH_TYPE_START= 1;

    public String mPackageName;
    public int mUid;
    public String mVisibleComponent;
    public String mStartUpComponent;
    public int mRequestSensorType;
    public int mRequestAudioType;
    public int mRequestVideoType;
    public int mRequestScreenType;
    public int mRequestGPSType;
    public int mRequestFpsType;
    public int mRequestTouchRateType;
    public int mScreenTypeWhenStarted;
    public long mAppFirstStartStamp;

    public long mLastAudioStartStamp;
    public long mLastVideoStartStamp;
    public long mLastScreenTypeChangedStamp;

    public int mAudioStartCountSinceScreenChanged;

    public long mTouchRateDuration;

    public int mVideoWidth;
    public int mVideoHeight;

    public int mVideoStartType;

    public boolean mVideoPlaying;
    public boolean mVisible;

    public int mVisibleWindowCount;
    public int mRequestScreenTypeForMaxWindow;

    public boolean mGetHFullScreenWhenRotationEnabled;

    public AppBehaviorInfo(String packageName, int uid) {
        mPackageName = packageName;
        mUid = uid;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(TAG);

        result.append("{packageName: " + mPackageName
            + ", uid: "  + mUid
            + ", mVisibleComponent:" + mVisibleComponent
            + ", mRequestSensorType: " + mRequestSensorType
            + ", mRequestAudioType: " + mRequestAudioType
            + ", mRequestVideoType: " + mRequestVideoType
            + ", mRequestScreenType: " + mRequestScreenType
            + ", mRequestFpsType: " + mRequestFpsType
            + ", mRequestTouchRateType: " + mRequestTouchRateType
            + ", mRequestGPSType: " + mRequestGPSType
            + ", mVideoWidth: " + mVideoWidth
            + ", mVideoHeight: " + mVideoHeight
            + ", mScreenTypeWhenStarted: " + mScreenTypeWhenStarted
            + ", mStartUpComponent:" + mStartUpComponent
            + ", mAppFirstStartStamp: " + mAppFirstStartStamp
            + ", mLastAudioStartStamp: " + mLastAudioStartStamp
            + ", mLastVideoStartStamp: " + mLastVideoStartStamp
            + ", mTouchRateDuration: " + mTouchRateDuration
            + ", mVideoStartType: " + mVideoStartType
            + ", mVideoPlaying: " + mVideoPlaying
            + ", mVisible:" + mVisible
            + ", mGetHFullScreenWhenRotationEnabled:" + mGetHFullScreenWhenRotationEnabled
            + ", mAudioStartCountSinceScreenChanged:" + mAudioStartCountSinceScreenChanged
            + ", mVisibleWindowCount:" + mVisibleWindowCount
            + ", mRequestScreenTypeForMaxWindow:" + mRequestScreenTypeForMaxWindow
            + " }");
        return result.toString();
    }


}
