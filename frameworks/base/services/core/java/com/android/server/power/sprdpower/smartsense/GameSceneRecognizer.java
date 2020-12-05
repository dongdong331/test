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

////////////////////////////


/**
 * Created on 18-6-21.
 */

public class GameSceneRecognizer extends SceneRecognizer {
    private static final String TAG = "SSense.GameRecognizer";

    private static final boolean DEBUG_LOG = Util.getDebugLog();
    private static final boolean DEBUG = Util.isDebug() || DEBUG_LOG;
    private static final boolean DEBUG_MORE = false;

    private static final int DELAY_CHECK_INTERVAL = (2*1000); // 2s

    private static final int TOUCH_CHECK_DURATION = (60*1000); // 60s


    private Handler mHandler;

    private final IWindowManager mIWindowManager;
    private final WindowManagerInternal mWindowManagerInternal;
    private ActivityManagerInternal mActivityManagerInternal;


    private final ArrayMap<String, Scene> mSceneList = new ArrayMap<>();

    private boolean mHasGamePlaying = false;
    private String mCurrentGameAppName;
    private int mCurrentPlayingAppUid;

    GameSceneRecognizer(Context context, SceneRecognizeCollector sceneCollector, Handler handler) {
        super(context, Scene.SCENE_TYPE_GAME, sceneCollector, handler);
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
        handleCheckGameState(appInfo);
    }


    private static final int MSG_INIT = 0;

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

    private void handleCheckGameState(AppBehaviorInfo appInfo) {

        if (appInfo == null) return;

        String packageName = appInfo.mPackageName;
        int uid = appInfo.mUid;

        int audioState = Scene.SCENE_ID_NONE;
        if (appInfo.mRequestAudioType == AppBehaviorInfo.REQUEST_AUDIO_TYPE_START) {
            audioState = Scene.SCENE_ID_AUDIO_START;
        } else {
            audioState = Scene.SCENE_ID_AUDIO_END;
        }

        int sensorType = appInfo.mRequestSensorType;

        int screenState = appInfo.mRequestScreenType;

        int fpsType = appInfo.mRequestFpsType;
        int touchType = appInfo.mRequestTouchRateType;

        int newSceneId = Scene.SCENE_ID_NONE;

        if ((isFpsSatisfied(appInfo) && isFullScreen(screenState) && isTouchRateSatisfied(appInfo) && isAudioSatisfied(appInfo))
            || isGameState(appInfo)
            || isInternalGameApp(appInfo)) {
            newSceneId = Scene.SCENE_ID_GAME_START;
        } else if (avaliableScreenState(appInfo)
            && mHasGamePlaying
            && packageName.equals(mCurrentGameAppName)) {
            newSceneId = Scene.SCENE_ID_GAME_START;
        }

        boolean bChanged = false;   // whether the mScene status is changed

        Scene scene = getOrCreateAppScene(packageName, uid);

        if (scene == null) {
            Slog.w(TAG, "null scene for " + packageName);
            return;
        }

        // Check game extra state: start/stop
        if (scene.getSceneId() != newSceneId) {
            if (newSceneId == Scene.SCENE_ID_NONE)
                newSceneId = Scene.SCENE_ID_GAME_EXIT;

            if (scene.getSceneId() != newSceneId)
                bChanged = true;

            scene.setSceneId(newSceneId);

            if (newSceneId == Scene.SCENE_ID_GAME_START) {
                mHasGamePlaying = true;
                mCurrentGameAppName = packageName;
                mCurrentPlayingAppUid = uid;
            } else {
                mHasGamePlaying = false;
                mCurrentGameAppName = null;
                mCurrentPlayingAppUid = -1;
            }

        }

        if (DEBUG) {
            Slog.d(TAG, "GameScene for uid:" + uid + " pkg name:" + packageName +
            " sceneId:" + newSceneId + " changed:" + bChanged + " mHasGamePlaying:" + mHasGamePlaying
            + " mCurrentGameAppName:" + mCurrentGameAppName);
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

            //showToast((newSceneId==Scene.SCENE_ID_GAME_START), newSceneId);
        }

    }

    private boolean isFpsSatisfied(AppBehaviorInfo appInfo) {
        // check fps
        if (appInfo.mVisible && highFps(appInfo)
            && isFullScreen(appInfo.mRequestScreenType)) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isTouchRateSatisfied(AppBehaviorInfo appInfo) {
        // check touch rate
        if (appInfo.mVisible && highTouchRate(appInfo) && isFullScreen(appInfo.mRequestScreenType)) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isAudioSatisfied(AppBehaviorInfo appInfo) {
        if (!appInfo.mVisible) return false;

        // check audio
        if (appInfo.mRequestAudioType == AppBehaviorInfo.REQUEST_AUDIO_TYPE_START
            /*|| appInfo.mAudioStartCountSinceScreenChanged > 6*/) {
            return true;
        } else {
            return false;
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
            && (rect.bottom - rect.top) >= (baseSize.x - 100)) { // 100 is the height of status bar
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
            && (rect.bottom - rect.top) >= (baseSize.y - 100)) { // 100 is the height of status bar
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


    private boolean isFullScreen(int state) {

        return (state == AppBehaviorInfo.REQUEST_SCREEN_TYPE_VFULL
            /*|| state == AppBehaviorInfo.REQUEST_SCREEN_TYPE_HFULL*/
            || state == AppBehaviorInfo.REQUEST_SCREEN_TYPE_HFULL_EXACT);
    }


    private boolean highFps(AppBehaviorInfo appInfo) {
        if (DEBUG) Slog.d(TAG, "highFps mCurrentFPS:" + mSceneCollector.mCurrentFPS);
        return (mSceneCollector.mCurrentFPS > 40.0f);
    }

    private boolean highTouchRate(AppBehaviorInfo appInfo) {

        if (DEBUG) Slog.d(TAG, "highTouchRate in: mLastTouchChangedStamp:" + mSceneCollector.mLastTouchChangedStamp
            + " mLastAppTranstionStamp:" + mSceneCollector.mLastAppTranstionStamp
            + " mCurrentTouchRate:" + mSceneCollector.mCurrentTouchRate);

        if (mSceneCollector.mCurrentTouchRate < 1.0f) {
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        if (mSceneCollector.mLastTouchChangedStamp > 0) {
            if (now - mSceneCollector.mLastTouchChangedStamp > TOUCH_CHECK_DURATION
                && mSceneCollector.mLastTouchChangedStamp >= mSceneCollector.mLastAppTranstionStamp
                && now - appInfo.mLastScreenTypeChangedStamp > TOUCH_CHECK_DURATION)
                return true;
        }

        if (DEBUG) Slog.d(TAG, "highTouchRate mLastTouchChangedStamp:" + mSceneCollector.mLastTouchChangedStamp
            + " mLastAppTranstionStamp:" + mSceneCollector.mLastAppTranstionStamp
            + " now:" + now
            + " mCurrentTouchRate:" + mSceneCollector.mCurrentTouchRate);

        return false;

    }

    private boolean isGameState(AppBehaviorInfo appInfo) {
        if (!appInfo.mVisible || !isFullScreen(appInfo.mRequestScreenType))
            return false;

        if (appInfo.mScreenTypeWhenStarted == AppBehaviorInfo.REQUEST_SCREEN_TYPE_HFULL
            && mSceneCollector.mCurrentFPS > 20.0f) {
            return true;
        }

        return (appInfo.mScreenTypeWhenStarted == AppBehaviorInfo.REQUEST_SCREEN_TYPE_HFULL_EXACT);
    }

   private boolean isInternalGameApp(AppBehaviorInfo appInfo) {
        if (!appInfo.mVisible || !isFullScreen(appInfo.mRequestScreenType))
            return false;

        if (appInfo.mVisibleComponent == null) return false;

        String appName = ComponentName.unflattenFromString(appInfo.mVisibleComponent).getPackageName();

        return ConfigReader.getInstance().inInternalGameAppList(appName);
    }

    private boolean avaliableScreenState(AppBehaviorInfo appInfo) {
        if (!appInfo.mVisible)
            return false;

        if (appInfo.mScreenTypeWhenStarted == AppBehaviorInfo.REQUEST_SCREEN_TYPE_HFULL
            || appInfo.mScreenTypeWhenStarted == AppBehaviorInfo.REQUEST_SCREEN_TYPE_HFULL_EXACT) {
            return !(AppBehaviorInfo.REQUEST_SCREEN_TYPE_NONE == appInfo.mRequestScreenTypeForMaxWindow
                /*|| AppBehaviorInfo.REQUEST_SCREEN_TYPE_SMALL == appInfo.mRequestScreenTypeForMaxWindow*/);
        }

        return isFullScreen(appInfo.mRequestScreenType);
    }

    private void showToast(boolean start, int id) {
        String text = null;

        if (start)
            text = new String("Game start:" + id + " " + mCurrentGameAppName);
        else
            text = new String("Game stop!");

        Slog.d(TAG, "showToast: " + text);

        if (text != null) {
            Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
        }
    }

    private Scene getOrCreateAppScene(String packageName, int uid) {
        if (packageName == null) return null;
        Scene scene = mSceneList.get(packageName);
        if (scene == null) {
            scene = new Scene(Scene.SCENE_TYPE_GAME);
            mSceneList.put(packageName, scene);
            if (DEBUG) Slog.d(TAG, "new Scene for " + packageName);
        }
        return scene;
    }

}
