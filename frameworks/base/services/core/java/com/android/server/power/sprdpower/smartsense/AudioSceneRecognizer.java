package com.android.server.power.sprdpower;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.sprdpower.Scene;

import android.util.ArrayMap;
import android.util.Log;
import android.util.Slog;

import android.os.BundleData;
import android.os.sprdpower.Util;

/**
 * Created by SPREADTRUM\jonas.zhang on 18-1-30.
 * This is a scene recognizer for Audio scene.
 */

public class AudioSceneRecognizer extends SceneRecognizer  {
    private static final String TAG = "AudioSceneRecognizer";

    private static final boolean DEBUG_LOG = Util.getDebugLog();
    private static final boolean DEBUG = Util.isDebug() || DEBUG_LOG;
    private static final boolean DEBUG_MORE = false;

    // Record previous/old scene status
    private int mExtraState = BundleData.STATE_STOP;
    private int mUid;
    private String mPackageName;

    private final ArrayMap<String, Scene> mSceneList = new ArrayMap<>();


    AudioSceneRecognizer(Context context, SceneRecognizeCollector sceneCollector, Handler handler) {
        super(context, Scene.SCENE_TYPE_AUDIO, sceneCollector, handler);
    }

    @Override
    protected void reportData(BundleData data) {
        updateScene(data);
    }

    @Override
    protected void reportEvent(int event) {
    }

    /**
     * Recognize and update audio scene.
     *
     * @param data System status data used to recognize scene.
     */
    // TODO: Would return value be a flag to indicate if the status has changed compared to the original
    @Override
    protected void updateScene(BundleData data) {
        int type = data.getType();
        switch (type) {
            case BundleData.DATA_TYPE_BATTERY_EVENT:
                handleBatteryEvent(data);
                break;
            default:
                break;
        }
    }

    @Override
    protected void checkScene(AppBehaviorInfo appInfo) {
    }

    // TODO: Would return value be a flag to indicate if the status has changed compared to the original
    private void handleBatteryEvent(BundleData data) {
        int extraState;             // DATA_EXTRA_SUBTYPE in new event
        int uid;                    // DATA_EXTRA_UID in new event
        boolean bChanged = false;   // whether the mScene status is changed
        String packageName;         // DATA_EXTRA_PACKAGENAME in new event
        Bundle rawBundle;           // DATA_EXTRA_RAW_DATA in new event
        Bundle extraData;           // used as mExtras in Scene

        if (DEBUG_MORE) {
            Slog.d(TAG, "handleBatteryEvent: enter");
        }
        // Format of DATA_TYPE_BATTERY_EVENT in Audio scene:
        // BundleData.DATA_EXTRA_SUBTYPE:   int, e.g. BundleData.DATA_SUBTYPE_AUDIO
        // BundleData.DATA_EXTRA_UID:   uid
        // BundleData.DATA_EXTRA_PACKAGENAME:   package name associated with uid
        // BundleData.DATA_EXTRA_STATE: BundleData.STATE_START/STATE_STOP
        if (data.getIntExtra(BundleData.DATA_EXTRA_SUBTYPE, BundleData.DATA_SUBTYPE_DEFAULT)
                == BundleData.DATA_SUBTYPE_AUDIO) {
            extraState = data.getIntExtra(BundleData.DATA_EXTRA_STATE, BundleData.STATE_STOP);

            if (DEBUG_MORE) {
                Slog.d(TAG, "DATA_EXTRA_STATE:" + extraState + " previous state:" + mExtraState);
            }

            int newSceneId = Scene.SCENE_ID_NONE;
            if (extraState == BundleData.STATE_START) {
                newSceneId = Scene.SCENE_ID_AUDIO_START;
            } else {
                newSceneId = Scene.SCENE_ID_AUDIO_END;
            }

            // Check uid & packages name
            uid = data.getIntExtra(BundleData.DATA_EXTRA_UID, -1);
            packageName = data.getStringExtra(BundleData.DATA_EXTRA_PACKAGENAME);

            Scene scene = getOrCreateAppScene(packageName, uid);

            if (scene == null) {
                Slog.w(TAG, "null scene for " + packageName);
                return;
            }

            // Check audio extra state: start/stop
            if (scene.getSceneId() != newSceneId) {
                scene.setSceneId(newSceneId);
                bChanged = true;
            }

            if ((rawBundle = data.getBundleExtra(BundleData.DATA_EXTRA_RAW_DATA)) != null) {
                if (DEBUG_MORE) {
                    Slog.d(TAG, "BundleData with passthrough raw data");
                }

                // if DATA_EXTRA_RAW_DATA exists, always set bChanged as true
                bChanged = true;
            }


            if (DEBUG) {
                Slog.d(TAG, "AudioScene for uid:" + uid + " pkg name:" + packageName +
                " sceneId:" + newSceneId + " changed:" + bChanged);
            }

            // Update scene data
            if (bChanged) {
                extraData = scene.getSceneExtras();
                if (extraData == null) {
                    extraData = new Bundle();
                    extraData.putInt(Scene.EXTRA_UID, uid);
                    extraData.putString(Scene.EXTRA_PACKAGENAME, packageName);
                    scene.setSceneExtras(extraData);
                }

                // Check if DATA_EXTRA_RAW_DATA exists in BundleData
                if (rawBundle != null) {
                    extraData.putBundle(Scene.EXTRA_RAW, rawBundle);
                }

                // Notify external modules the updated scene status
                notifySceneStatusChanged(new Scene(scene));
            }
        }

        if (DEBUG_MORE) {
            Slog.d(TAG, "handleBatteryEvent: leave");
        }
    }

    private Scene getOrCreateAppScene(String packageName, int uid) {
        if (packageName == null) return null;
        Scene scene = mSceneList.get(packageName);
        if (scene == null) {
            scene = new Scene(Scene.SCENE_TYPE_AUDIO);
            mSceneList.put(packageName, scene);
            if (DEBUG) Slog.d(TAG, "new Scene for " + packageName);
        }
        return scene;
    }


}
