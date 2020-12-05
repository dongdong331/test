/*
 ** Copyright 2018 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.util.Log;
import android.content.Context;
import android.os.RemoteException;
import android.os.Handler;
import android.os.Bundle;
import android.content.Context;

import vendor.sprd.hardware.power.V3_0.IPower;
import android.os.sprdpower.ISceneStatsNotifier;
import android.os.sprdpower.Scene;

public class PowerHintCallback extends ISceneStatsNotifier.Stub {
    private static String TAG = "PowerHintCallback";
    private static boolean DEBUG = false;

    private Context mContext;
    private IPower mService;
    private float mCurrentFPS = 0;
    private int mGameSceneId = Scene.SCENE_ID_NONE;
    private int mGameFpsHintId = 0;

    private HandlerForMemoryGts mHandlerForMemoryGts;

    public PowerHintCallback(Context context) {
        mContext = context;
        mHandlerForMemoryGts = new HandlerForMemoryGts(mContext);

        try {
            mService = IPower.getService();
        } catch (Exception e) {
            Log.e(TAG, "Get Power HIDL HAL service failed!!!");
            e.printStackTrace();
        }
    }

    private int getPowerHintSceneID(String sceneID, String subID) {
        String sceneName = sceneID + "_" + subID;

        if (DEBUG) {
            Log.d(TAG, "getPowerHintSceneID(" + sceneName + ")");
        }
        return getPowerHintSceneID(sceneName);
    }

    private int getPowerHintSceneID(String sceneName) {
        int sceneID =  -1;

        if (sceneName == null) {
            Log.d(TAG, "sceneName is null");
            return -1;
        }

        try {
            sceneID = mService.getSceneIdByName(sceneName);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sceneID;
    }

    public void onNotifySceneStats(Scene scene) {
        int sceneType = Scene.SCENE_TYPE_NONE;
        int sceneId = Scene.SCENE_ID_NONE;
        Bundle data = scene.getSceneExtras();
        int powerHintSceneId = 0;
        int state = 0;
        int duration = 0;

        if (mService == null || data == null) {
            Log.e(TAG, ((mService == null)?"mService":"data") + "is null!!!");
            return;
        }

        sceneType = scene.getSceneType();
        sceneId = scene.getSceneId();
        //duration = data.getInt(Scene.EXTRA_DURATION, 0);

        if (DEBUG) {
            Log.d(TAG, "Enter onNotifySceneStats(): sceneType: " + sceneType + ", sceneId: " + sceneId);
            Log.d(TAG, "Bundle: " + data);
        }

        switch (sceneType) {
        case Scene.SCENE_TYPE_EVENT:
            switch (sceneId) {
            case Scene.SCENE_ID_EVENT_FPS:
                float fps = data.getFloat(Scene.EXTRA_FPS, 0);
                if (fps != mCurrentFPS) {
                    if ((mGameSceneId == Scene.SCENE_ID_GAME_START) && (fps <= 30) && mGameFpsHintId == 0) {
                        powerHintSceneId = getPowerHintSceneID("game", "30fps");
                        mGameFpsHintId = powerHintSceneId;
                        duration = 1;
                    } else if (mGameSceneId != 0 && fps > 30) {
                        powerHintSceneId = mGameFpsHintId;
                        mGameFpsHintId = 0;
                        duration = 0;
                    }
                    mCurrentFPS = fps;
                }
                break;
            default:
                break;
            }
            break;
        case Scene.SCENE_TYPE_GAME:
            if (mGameSceneId != sceneId) {
                mGameSceneId = sceneId;
                if ((mGameSceneId == Scene.SCENE_ID_GAME_EXIT) && (mGameSceneId != 0)) {
                    powerHint(mGameFpsHintId, 0);
                    mGameFpsHintId = 0;
                }
            }
            break;
        /*
        case Scene.SCENE_TYPE_RADIO:
            powerHintSceneId = getPowerHintSceneID(Scene.SCENE_RADIO, sceneSubID);
            break;
        */
        case Scene.SCENE_TYPE_APP:
            String pkg = data.getString(Scene.EXTRA_PACKAGENAME, null);
            if (sceneId == Scene.SCENE_ID_APP_START) {
                mHandlerForMemoryGts.noteAppLaunched(pkg);
            } else if (sceneId == Scene.SCENE_ID_APP_FINISH) {
                mHandlerForMemoryGts.noteAppStopped(pkg);

            }
            //powerHintSceneId = getPowerHintSceneID(pkg);
            break;
        default:
            return;
        }

        powerHint(powerHintSceneId, duration);
    }

    private void powerHint(int powerHintSceneId, int duration) {
        if (powerHintSceneId <= 0) {
            Log.e(TAG, "sceneId: " + powerHintSceneId + " is invalid");
            return;
        }

        try {
            mService.powerHint(powerHintSceneId, duration);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
