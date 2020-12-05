package com.android.server.power.sprdpower;

import android.content.Context;
import android.os.Handler;
import android.os.sprdpower.ISceneStatsNotifier;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.sprdpower.Scene;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;

import android.os.BundleData;
import android.os.sprdpower.Util;

/**
 * SceneRecognizer is a basic class for each specific scene recognizer.
 */

abstract public class SceneRecognizer {
    private static final String TAG = "SceneRecognizer";
    private static final boolean DEBUG = true;

    protected BundleData mBundleData;

    protected final RemoteCallbackList<ISceneStatsNotifier> mCallbacks = new RemoteCallbackList<>();
    protected final Scene mScene = new Scene();
    protected Context mContext;
    protected int mSceneType = Scene.SCENE_TYPE_NONE;

    protected SceneRecognizeCollector mSceneCollector;

    SceneRecognizer(Context context, int sceneType, SceneRecognizeCollector sceneCollector,
        Handler handler) {
        mContext = context;
        mSceneType = sceneType;
        mSceneCollector = sceneCollector;
    }

    protected abstract void reportData(BundleData data);

    protected void reportEvent(int event) {
    }

    /**
     * abstract function that each sub scene recognizer should implement.
     *
     * @param data System status data used to recognize scene
     */
    protected abstract void updateScene(BundleData data);

    protected void checkScene(AppBehaviorInfo appInfo) {
    }


    boolean match(int sceneType) {
        return (sceneType == Scene.SCENE_TYPE_ALL
            || (sceneType & mSceneType) == mSceneType);
    }

    void registerSceneStatusNotifier(ISceneStatsNotifier callback) {
        mCallbacks.register(callback);
    }

    void unregisterSceneStatusNotifier(ISceneStatsNotifier callback) {
        mCallbacks.unregister(callback);
    }

    /**
     * This function is used to get current sub scene status.
     *
     * @return Current scene state
     */
    @NonNull
    Scene getScene() {
        return mScene;
    }

    /**
     * Call the callback function of each registered module to notify the updated scene status.
     *
     * @param scene Current scene status
     */
    protected void notifySceneStatusChanged(Scene scene) {

        int n = mCallbacks.beginBroadcast();
        if (DEBUG) {
            Log.d(TAG, "notifySceneStatusChanged: notify " + n + " receivers");
        }

        for (int i = 0; i < n; i++) {
            try {
                mCallbacks.getBroadcastItem(i).onNotifySceneStats(scene);
            } catch (RemoteException e) {
                if (DEBUG) {
                    Log.d(TAG, "notifySceneStatusChanged: callback #" + n + " failed(" + e + ")");
                }
            }
        }

        mCallbacks.finishBroadcast();
    }

    void cleanUp() {
        mCallbacks.kill();
    }
}
