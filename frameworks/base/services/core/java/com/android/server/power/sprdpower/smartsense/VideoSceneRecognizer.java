package com.android.server.power.sprdpower;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.content.Context;
import android.content.ComponentName;

import android.graphics.Rect;
import android.graphics.Point;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.sprdpower.Scene;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;

import android.view.Display;
import android.view.IWindowManager;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import android.util.ArrayMap;
import android.util.Log;
import android.util.Slog;
import android.util.MathUtils;
import android.widget.Toast;

import com.android.server.LocalServices;
import com.android.server.wm.WindowManagerInternal;

import java.util.ArrayList;
import java.util.List;

import android.os.BundleData;
import android.os.sprdpower.Util;

public class VideoSceneRecognizer extends SceneRecognizer {
    private static final String TAG = "SSense.VideoRecognizer";

    private static final boolean DEBUG_LOG = Util.getDebugLog();
    private static final boolean DEBUG = Util.isDebug() || DEBUG_LOG;
    private static final boolean DEBUG_MORE = false;

    private static final int DELAY_CHECK_INTERVAL = (2*1000); // 2s

    private static final int TOUCH_CHECK_DURATION = (30*1000); // 30s

    private Handler mHandler;

    private final IWindowManager mIWindowManager;
    private final WindowManagerInternal mWindowManagerInternal;
    private ActivityManagerInternal mActivityManagerInternal;


    private final ArrayMap<String, Scene> mSceneList = new ArrayMap<>();

    private boolean mHasVideoPlaying = false;
    private String mCurrentPlayingAppName;
    private int mCurrentPlayingAppUid;


    private final String[] mVideoTag = new String[] {
        "video",
        "MVplayer",
        "vod",
        "vodplayer",
        "VideoDetail",
        "videolive",
        "Video",
    };

    private final String[] mTag1 = new String[] {
        "tv",
        "live",
        "mv",
        "Mv",
        "living",
        "com.smile.gifmaker",
        "show"
    };

    private final String[] mTag2 = new String[] {
        "RoomActivity",
        "DetailActivity",
        "PlayActivity",
        "LiveActivty",
        "PlayerActivity",
        "VideoPlayer",
        "live_room",
        "LivingActivity",
    };

    private final String[] mInternalVideoAppList = new String[] {
        "com.ss.android.ugc.aweme",
        "dopool.player",
        "com.youku.phone",
    };


    VideoSceneRecognizer(Context context, SceneRecognizeCollector sceneCollector, Handler handler) {
        super(context, Scene.SCENE_TYPE_VIDEO, sceneCollector, handler);
        mHandler = new H(handler.getLooper());
        mIWindowManager = IWindowManager.Stub.asInterface(
                ServiceManager.getService(Context.WINDOW_SERVICE));
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
        mActivityManagerInternal = LocalServices.getService(ActivityManagerInternal.class);
    }

    @Override
    protected void reportData(BundleData data) {
    }

    @Override
    protected void reportEvent(int event) {
    }


    @Override
    protected void updateScene(BundleData data) {
    }

    @Override
    protected void checkScene(AppBehaviorInfo appInfo) {
        handleCheckVideoState(appInfo);
    }


    private static final int MSG_INIT = 0;
    private static final int MSG_CHECK_WINDOW_DELAY = 1;
    private static final int MSG_REPORT_EVENT = 2;
    private static final int MSG_CHECK_VIDEO_FOR_FPS = 3;
    private static final int MSG_UPDATE_VISIBLE_APP = 4;
    private static final int MSG_REPORT_DATA = 5;
    private static final int MSG_CHECK_APP_STARTWINDOW = 6;

    private class H extends Handler {
        public H(Looper looper) {
            super(looper);
        }

        String msg2Str(int msg) {
            final String msgStr[] = {"MSG_INIT",
            };

            if ((0 <= msg) && (msg < msgStr.length))
                return msgStr[msg];
            else
                return "Unknown message: " + msg;
        }

        @Override
        public void handleMessage(Message msg) {
            if (DEBUG_MORE) Slog.d(TAG, "handleMessage(" + msg2Str(msg.what) + ")");
            switch (msg.what) {
                default:
                    break;
            }
        }
    }


    private void handleCheckVideoState(AppBehaviorInfo appInfo) {

        if (appInfo == null) return;

        String packageName = appInfo.mPackageName;
        int uid = appInfo.mUid;

        boolean useFps = false;

        int videoState = Scene.SCENE_ID_NONE;
        if ((appInfo.mRequestVideoType & (AppBehaviorInfo.REQUEST_VIDEO_TYPE_START_CODEC | AppBehaviorInfo.REQUEST_VIDEO_TYPE_START_NORMAL))
            != 0) {
            videoState = Scene.SCENE_ID_VIDEO_START;
        } else {
            videoState = Scene.SCENE_ID_VIDEO_END;
        }

        // use fps instead
        if (videoState == Scene.SCENE_ID_VIDEO_END) {
            if (DEBUG_MORE) Slog.d(TAG, "handleCheckVideoState:" + uid + " pkg name:" + packageName + " is:" + " use FPS instead!!");
            if (isFpsVideoType(appInfo)) {
                videoState = Scene.SCENE_ID_VIDEO_START;
                useFps = true;
            } else {
                videoState = Scene.SCENE_ID_VIDEO_END;
            }
        }

        int audioState = Scene.SCENE_ID_NONE;
        if (appInfo.mRequestAudioType == AppBehaviorInfo.REQUEST_AUDIO_TYPE_START) {
            audioState = Scene.SCENE_ID_AUDIO_START;
        } else {
            audioState = Scene.SCENE_ID_AUDIO_END;
        }

        int sensorType = appInfo.mRequestSensorType;

        int screenState = appInfo.mRequestScreenType;


        if (!continueCheckVideoState(appInfo)) {
            return;
        }

        // check if use fps, if yes, then judge stop only by audio
        if (mHasVideoPlaying
            && audioState == Scene.SCENE_ID_AUDIO_START
            && appInfo.mVideoStartType == AppBehaviorInfo.VIDEO_START_TYPE_FPS
            && videoState !=  Scene.SCENE_ID_VIDEO_START
            && appInfo.mVisible
            && !lowFps(appInfo)) {
            if (DEBUG_MORE) Slog.d(TAG, "check stop only by audio for fps type video!! uid:" + uid + " pkg name:" + packageName);
            videoState = Scene.SCENE_ID_VIDEO_START;
            useFps = true;
        }


        int newSceneId = Scene.SCENE_ID_NONE;

        if (appInfo.mVisible
            && videoState ==  Scene.SCENE_ID_VIDEO_START
            && audioState == Scene.SCENE_ID_AUDIO_START) {
            newSceneId = Scene.SCENE_ID_VIDEO_START;

            if (appInfo.mRequestScreenType == AppBehaviorInfo.REQUEST_SCREEN_TYPE_VFULL) {
                newSceneId = Scene.SCENE_ID_VIDEO_START_VFULL;
            } else if (isHFullScreen(appInfo.mRequestScreenType)) {
                if (isMatchFullScreen(appInfo.mVideoWidth, appInfo.mVideoHeight))
                    newSceneId = Scene.SCENE_ID_VIDEO_START_HFULL_MATCH;
                else
                    newSceneId = Scene.SCENE_ID_VIDEO_START_HFULL;
            }
        }

        // update mVideoStartType
        if (newSceneId != Scene.SCENE_ID_NONE) {
            appInfo.mVideoStartType = AppBehaviorInfo.VIDEO_START_TYPE_NORMAL;
            if (useFps) {
                appInfo.mVideoStartType = AppBehaviorInfo.VIDEO_START_TYPE_FPS;
            }
        } else {
            appInfo.mVideoStartType = AppBehaviorInfo.VIDEO_START_TYPE_NONE;
        }


        boolean bChanged = false;   // whether the mScene status is changed

        Scene scene = getOrCreateAppScene(packageName, uid);

        if (scene == null) {
            Slog.w(TAG, "null scene for " + packageName);
            return;
        }

        // Check video extra state: start/stop
        if (scene.getSceneId() != newSceneId) {
            if (newSceneId == Scene.SCENE_ID_NONE)
                newSceneId = Scene.SCENE_ID_VIDEO_END;

            if (scene.getSceneId() != newSceneId)
                bChanged = true;

            scene.setSceneId(newSceneId);

            if (newSceneId == Scene.SCENE_ID_VIDEO_START
                || newSceneId == Scene.SCENE_ID_VIDEO_START_VFULL
                || newSceneId == Scene.SCENE_ID_VIDEO_START_HFULL
                || newSceneId == Scene.SCENE_ID_VIDEO_START_HFULL_MATCH) {
                mHasVideoPlaying = true;
                mCurrentPlayingAppName = packageName;
                mCurrentPlayingAppUid = uid;

                // record the time stamp
                appInfo.mLastVideoStartStamp = SystemClock.elapsedRealtime();
                appInfo.mVideoPlaying = true;
            } else {
                mHasVideoPlaying = false;
                mCurrentPlayingAppName = null;
                mCurrentPlayingAppUid = -1;
                appInfo.mVideoPlaying = false;
            }
        }

        if (DEBUG) {
            Slog.d(TAG, "VideoScene for uid:" + uid + " pkg name:" + packageName +
            " sceneId:" + newSceneId + " changed:" + bChanged + " mHasVideoPlaying:" + mHasVideoPlaying);
        }

        // Update scene data
        if (bChanged) {
            Bundle extraData = scene.getSceneExtras();
            if (extraData == null) {
                extraData = new Bundle();
                extraData.putInt(Scene.EXTRA_UID, uid);
                extraData.putString(Scene.EXTRA_PACKAGENAME, packageName);
                scene.setSceneExtras(extraData);
            }

            // Notify external modules the updated scene status
            notifySceneStatusChanged(new Scene(scene));

            //showToast(mHasVideoPlaying, newSceneId);
        }

    }

    private boolean isFpsVideoType(AppBehaviorInfo appInfo) {
        // check fps
        if (hasVideoTag(appInfo.mVisibleComponent) || isSpecialVideoApp(appInfo.mVisibleComponent)) {
            if (highFps(appInfo) && appInfo.mRequestScreenType != AppBehaviorInfo.REQUEST_SCREEN_TYPE_NONE) {
                return true;
            } else {
                return false;
            }
        } else {
            if (highFps(appInfo) && isHFullScreen(appInfo.mRequestScreenType)
                && availableStartupScreenTypeForVideo(appInfo.mScreenTypeWhenStarted)
                && (!highTouchRate(appInfo) || isVideoState(appInfo))) {
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean isFullScreen(Rect rect, Point baseSize) {
        if (rect.isEmpty()) return false;

        if (DEBUG) {
            Slog.d(TAG, "isFullScreen rect:" + rect + " baseSize:" + baseSize);
        }

        if ((rect.right - rect.left) == baseSize.x
            && (rect.bottom - rect.top) == baseSize.y) {
            if (DEBUG) Slog.d(TAG, "V FullScreen");
            return true;
        }

        if ((rect.right - rect.left) == baseSize.y
            && (rect.bottom - rect.top) == baseSize.x) {
            if (DEBUG) Slog.d(TAG, "H FullScreen");
            return true;
        }

        return false;
    }

    private boolean isVFullScreen(Rect rect, Point baseSize) {
        if (rect.isEmpty()) return false;

        if (DEBUG) {
            Slog.d(TAG, "isVFullScreen rect:" + rect + " baseSize:" + baseSize);
        }

        if ((rect.right - rect.left) == baseSize.x
            && (rect.bottom - rect.top) == baseSize.y) {
            if (DEBUG) Slog.d(TAG, "V FullScreen");
            return true;
        }

        return false;
    }

    private boolean isHFullScreen(Rect rect, Point baseSize) {
        if (rect.isEmpty()) return false;

        if (DEBUG) {
            Slog.d(TAG, "isHFullScreen rect:" + rect + " baseSize:" + baseSize);
        }

        if ((rect.right - rect.left) == baseSize.y
            && (rect.bottom - rect.top) >= (baseSize.x - 100)) {// 100 is consider for the height of status bar 
            if (DEBUG) Slog.d(TAG, "H FullScreen");
            return true;
        }
        return false;
    }

    private boolean isNormalScreen(Rect rect, Point baseSize) {
        if (rect.isEmpty()) return false;

        if (DEBUG) {
            Slog.d(TAG, "isNormalScreen rect:" + rect + " baseSize:" + baseSize);
        }

        if ((rect.right - rect.left) == baseSize.x
            && (rect.bottom - rect.top) >= (baseSize.y - 100)) {// 100 is consider for the height of status bar 
            if (DEBUG) Slog.d(TAG, "Normal Screen");
            return true;
        }
        return false;
    }

    private boolean isMatchFullScreen(int width, int height) {

        if (width == 0 || height == 0) return false;


        // check the window state
        try {
            Point baseSize = new Point();
            try {
                mIWindowManager.getBaseDisplaySize(Display.DEFAULT_DISPLAY, baseSize);
            } catch (RemoteException e) {
            }

            if (baseSize.x == 0 || baseSize.y == 0) return false;

            if (DEBUG) {
                Slog.d(TAG, "isMatchFullScreen width:" + width + " height:" + height + " baseSize:" + baseSize);
            }

            float ratio1 = (float) baseSize.x/baseSize.y;
            float ratio2 = (float) height / width;

            if (MathUtils.abs(ratio1-ratio2) < 0.2) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private boolean isHFullScreen(int screenState) {
        return ((AppBehaviorInfo.REQUEST_SCREEN_TYPE_HFULL == screenState
            || AppBehaviorInfo.REQUEST_SCREEN_TYPE_HFULL_EXACT == screenState));
    }

    private boolean availableStartupScreenTypeForVideo(int screenState) {
        return ((AppBehaviorInfo.REQUEST_SCREEN_TYPE_VFULL == screenState
            || AppBehaviorInfo.REQUEST_SCREEN_TYPE_NORMAL == screenState));
    }

    private boolean highFps(AppBehaviorInfo appInfo) {

        if (DEBUG) Slog.d(TAG, "highFps mCurrentFPS:" + mSceneCollector.mCurrentFPS);

        return (mSceneCollector.mCurrentFPS >= 20.0f);
    }

    private boolean highTouchRate(AppBehaviorInfo appInfo) {

        if (DEBUG_MORE) Slog.d(TAG, "highTouchRate in: mLastTouchChangedStamp:" + mSceneCollector.mLastTouchChangedStamp
            + " mLastAppTranstionStamp:" + mSceneCollector.mLastAppTranstionStamp
            + " mCurrentTouchRate:" + mSceneCollector.mCurrentTouchRate);

        if (mSceneCollector.mCurrentTouchRate > 2.0f) {
            return true;
        }

        long now = SystemClock.elapsedRealtime();
        if (mSceneCollector.mLastTouchChangedStamp > 0) {
            if (now - mSceneCollector.mLastTouchChangedStamp > TOUCH_CHECK_DURATION && mSceneCollector.mLastTouchChangedStamp >= mSceneCollector.mLastAppTranstionStamp)
                return false;
        }

        if (DEBUG_MORE) Slog.d(TAG, "highTouchRate mLastTouchChangedStamp:" + mSceneCollector.mLastTouchChangedStamp
            + " mLastAppTranstionStamp:" + mSceneCollector.mLastAppTranstionStamp
            + " now:" + now
            + " mCurrentTouchRate:" + mSceneCollector.mCurrentTouchRate);

        return true;
    }

    private long durationFpsChanged(AppBehaviorInfo appInfo) {

        long now = SystemClock.elapsedRealtime();

        return (now - mSceneCollector.mLastFpsChangedStamp);
    }

    private boolean lowFps(AppBehaviorInfo appInfo) {
        return (!highFps(appInfo) && (durationFpsChanged(appInfo) > 3000));
    }

    private boolean continueCheckVideoState(AppBehaviorInfo appInfo) {

        if (DEBUG_MORE) Slog.d(TAG, "continueCheckVideoState: " + appInfo.mPackageName 
            + " mHasVideoPlaying:" + mHasVideoPlaying
            + " appInfo.mVideoPlaying:" + appInfo.mVideoPlaying
            + " appInfo.mLastVideoStartStamp:" + appInfo.mLastVideoStartStamp
            + " mLastAppWindowChangedStamp:" + mSceneCollector.mLastAppWindowChangedStamp);


        // check if use fps, if yes, then judge stop only by audio
        if (mHasVideoPlaying
            && appInfo.mVideoPlaying
            && appInfo.mLastVideoStartStamp > mSceneCollector.mLastAppWindowChangedStamp) {
            if (DEBUG) Slog.d(TAG, "video start after window is changed not need to check video again for " + appInfo.mPackageName);
            return false;
        }
        return true;
    }

    private boolean isVideoState(AppBehaviorInfo appInfo) {
        if (isHFullScreen(appInfo.mRequestScreenType) &&
            appInfo.mScreenTypeWhenStarted == AppBehaviorInfo.REQUEST_SCREEN_TYPE_NORMAL) {
            return true;
        }

        return false;
    }

    private void showToast(boolean start, int id) {
        String text = null;

        if (start)
            text = new String("Video start:" + id + " " + mCurrentPlayingAppName);
        else
            text = new String("Video stop!");

        if (DEBUG) Slog.d(TAG, "showToast: " + text);

        if (text != null) {
            Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
        }
    }

   private boolean hasTag1(String componentName) {
        if (componentName == null) return false;
        for(String s : mTag1) {
            if(componentName.contains(s)) {
                return true;
            }
        }

        return false;
   }

   private boolean hasTag2(String componentName) {
        if (componentName == null) return false;
        for(String s : mTag2) {
            if(componentName.contains(s)) {
                return true;
            }
        }

        return false;
   }

   private boolean hasVideoTag(String componentName) {
        if (componentName == null) return false;
        for(String s : mVideoTag) {
            if(componentName.contains(s)) {
                if (DEBUG_MORE) Slog.d(TAG, "hasVideoTag: " + componentName + " for " + s);
                return true;
            }
        }
        // special for 'music'
        if (componentName.contains("music")) {
            String className = ComponentName.unflattenFromString(componentName).getClassName();
            if (className != null && className.contains("music"))
                return false;
        }

        return (hasTag1(componentName) && hasTag2(componentName));
    }

   private boolean isSpecialVideoApp(String componentName) {
        if (componentName == null) return false;
        for(String s : mInternalVideoAppList) {
            if(componentName.contains(s)) {
                if (DEBUG_MORE) Slog.d(TAG, "isSpecialVideoApp: " + componentName + " for " + s);
                return true;
            }
        }

        String appName = ComponentName.unflattenFromString(componentName).getPackageName();

        return ConfigReader.getInstance().inInternalVideoAppList(appName);
    }

    private long abs(long v) {
        return v > 0 ? v : -v;
    }

    private Scene getOrCreateAppScene(String packageName, int uid) {
        if (packageName == null) return null;
        Scene scene = mSceneList.get(packageName);
        if (scene == null) {
            scene = new Scene(Scene.SCENE_TYPE_VIDEO);
            mSceneList.put(packageName, scene);
            if (DEBUG) Slog.d(TAG, "new Scene for " + packageName);
        }
        return scene;
    }

}
