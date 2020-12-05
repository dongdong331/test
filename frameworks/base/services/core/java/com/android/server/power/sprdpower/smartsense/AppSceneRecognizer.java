package com.android.server.power.sprdpower;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.app.usage.UsageEvents;
import android.app.ActivityManager;
import android.content.Context;

import android.os.sprdpower.Util;
import android.os.sprdpower.Scene;
import android.os.BundleData;

public class AppSceneRecognizer extends SceneRecognizer {
    private static final String TAG = "AppSceneRecognizer";
    private static final boolean DEBUG = false;

    AppSceneRecognizer(Context context, SceneRecognizeCollector sceneCollector,
            Handler handler) {
        super(context, Scene.SCENE_TYPE_APP, sceneCollector, handler);
    }

    @Override
    protected void reportData(BundleData data) {
        updateScene(data);
    }

    @Override
    protected void updateScene(BundleData data) {
        int type = data.getType();
        switch (type) {
            case BundleData.DATA_TYPE_PROCESS_STATE:
            case BundleData.DATA_TYPE_APP_STATE_EVENT:
                handleAppEvent(data);
            default:
                break;
        }
    }

    /*
     * BundleData format:
     * -------------
     * |type|bundle|
     * -------------
     */
    private void handleAppEvent(BundleData data) {
        int type = data.getType();
        int sceneType = 0;
        int sceneID = Scene.SCENE_ID_NONE;
        Scene scene = new Scene();

        if (DEBUG)
            Log.d(TAG, "###handle app Event: Enter");

        switch (type) {
            case BundleData.DATA_TYPE_PROCESS_STATE:
                int procState = data.getIntExtra(BundleData.DATA_EXTRA_PROCESS_STATE, ActivityManager.PROCESS_STATE_NONEXISTENT);
                switch(procState) {
                case BundleData.PROCESS_STATE_VENDOR_START:
                    sceneID = Scene.SCENE_ID_APP_START;
                    break;
                case BundleData.PROCESS_STATE_VENDOR_FINISH:
                    sceneID = Scene.SCENE_ID_APP_FINISH;
                    break;
                default:
                    if (DEBUG) {
                        Log.d(TAG, "Don't support process state " + Util.ProcState2Str(procState));
                    }
                    return;
                }

                if (DEBUG) {
                    Log.d(TAG, "procState: " + Util.ProcState2Str(procState));
                }
                sceneType = Scene.SCENE_TYPE_APP;
                break;

            case BundleData.DATA_TYPE_APP_STATE_EVENT:
                int appState = data.getIntExtra(BundleData.DATA_EXTRA_APP_STATE_EVENT, -1);
                switch(appState) {
                case UsageEvents.Event.MOVE_TO_FOREGROUND:
                    sceneID = Scene.SCENE_ID_APP_MOVE_TO_FORGROUND;
                    break;
                case UsageEvents.Event.MOVE_TO_BACKGROUND:
                    sceneID = Scene.SCENE_ID_APP_MOVE_TO_BACKGROUND;
                    break;
                default:
                    if (DEBUG) {
                        Log.d(TAG, "Don't support app state " + Util.AppState2Str(appState));
                    }
                    return;
                }
                sceneType = Scene.SCENE_TYPE_APP;
                break;

            default:
                return;
        }

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

        bundle.putInt(Scene.EXTRA_UID, data.getIntExtra(BundleData.DATA_EXTRA_UID, -1));
        bundle.putString(Scene.EXTRA_PACKAGENAME, data.getStringExtra(BundleData.DATA_EXTRA_PACKAGENAME));

        scene.setSceneType(sceneType);
        scene.setSceneId(sceneID);
        scene.setSceneExtras(bundle);

        // Notify external modules the updated scene status
        notifySceneStatusChanged(scene);
        if (DEBUG)
            Log.d(TAG, "Bundle mExtras:" + bundle);
            Log.d(TAG, "###handle app Event: Exit");
    }
}
