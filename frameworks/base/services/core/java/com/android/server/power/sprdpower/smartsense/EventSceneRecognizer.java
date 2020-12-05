package com.android.server.power.sprdpower;

import android.util.Log;
import android.app.usage.UsageEvents;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Bundle;

import android.os.sprdpower.Scene;
import android.os.BundleData;

/**
 * Pass event by Scene
 */
public class EventSceneRecognizer extends SceneRecognizer {
    private static final String TAG = "EventSceneRecognizer";
    private static final boolean DEBUG = false;

    EventSceneRecognizer(Context context, SceneRecognizeCollector sceneCollector, Handler handler) {
        super(context, Scene.SCENE_TYPE_EVENT, sceneCollector, handler);
    }

    @Override
    protected void reportData(BundleData data) {
        updateScene(data);
    }

    @Override
    protected void updateScene(BundleData data) {
        int type = data.getType();
        switch (type) {
            case BundleData.DATA_TYPE_SYSTEM_EVENT:
                handleEvent(data);
            default:
                break;
        }
    }

    private void handleEvent(BundleData data) {
        int type = data.getType();
        int sceneType = 0;
        int sceneID = Scene.SCENE_ID_NONE;
        Scene scene = new Scene();
        float fps = 0;
        Bundle bundle = null;

        Bundle tmp = data.getBundleExtra(BundleData.DATA_EXTRA_RAW_DATA);
        if (tmp != null) {
            bundle = new Bundle(tmp);
        } else {
            bundle = new Bundle();
        }
        if (bundle == null) {
            Log.d(TAG, "bundle is null!!!");
            return;
        }

        if (DEBUG)
            Log.d(TAG, "###handle System Event: Enter");

        switch (type) {
            case BundleData.DATA_TYPE_SYSTEM_EVENT: {
                int subEvent = data.getIntExtra(BundleData.DATA_EXTRA_SUBTYPE, -1);
                switch(subEvent) {
                case BundleData.SYSTEM_EVENT_FPS_CHANGED:
                    sceneID = Scene.SCENE_ID_EVENT_FPS;
                    fps= data.getFloatExtra(BundleData.DATA_EXTRA_RATE, 0);
                    if (fps != 0) {
                        bundle.putFloat(Scene.EXTRA_FPS, fps);
                    }
                    break;
                default:
                    return;
                }
                sceneType = Scene.SCENE_TYPE_EVENT;
                break;
            }
            default:
                return;
        }

        scene.setSceneType(sceneType);
        scene.setSceneId(sceneID);
        scene.setSceneExtras(bundle);

        // Notify external modules the updated scene status
        notifySceneStatusChanged(scene);
        if (DEBUG)
            Log.d(TAG, "Bundle mExtras:" + bundle);
            Log.d(TAG, "###handle Event: Exit");
    }
}
