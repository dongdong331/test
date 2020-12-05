/*
 ** Copyright 2016 The Spreadtrum.com
 */

package android.os.sprdpower;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * @hide
 */
public class Scene implements Parcelable {

    static final String TAG = "SceneRecognize.Scene";

    private boolean DEBUG = true;

    // scene type that identify a group of scene ids that describe the same scene
    public static final int SCENE_TYPE_NONE = -1;
    public static final int SCENE_TYPE_ALL = 0;
    public static final int SCENE_TYPE_AUDIO = 1<<1;
    public static final int SCENE_TYPE_VIDEO = 1<<2;
    public static final int SCENE_TYPE_GAME = 1<<3;
    public static final int SCENE_TYPE_APP = 1<<4;
    public static final int SCENE_TYPE_EVENT = 1<<5;

    // scene id that idenfiy a specified scene
    public static final int SCENE_ID_NONE = 0;
    public static final int SCENE_ID_AUDIO_START = 1;
    public static final int SCENE_ID_AUDIO_END = 2;
    public static final int SCENE_ID_AUDIO_IN_START = 3;
    public static final int SCENE_ID_AUDIO_IN_END = 4;
    public static final int SCENE_ID_AUDIO_OUT_START = 5;
    public static final int SCENE_ID_AUDIO_OUT_END = 6;
    public static final int SCENE_ID_VIDEO_START = 7;
    public static final int SCENE_ID_VIDEO_END = 8;
    public static final int SCENE_ID_VIDEO_START_VFULL = 9;
    public static final int SCENE_ID_VIDEO_START_HFULL = 10;
    public static final int SCENE_ID_VIDEO_START_HFULL_MATCH = 11; // H FULL with video ratio match the display ratio

    // for game
    public static final int SCENE_ID_GAME_START = 20;
    public static final int SCENE_ID_GAME_EXIT = 21;

    // For SCENE_TYPE_APP scene id
    public static final int SCENE_ID_APP_START = 0x10;
    public static final int SCENE_ID_APP_FINISH = 0x11;
    public static final int SCENE_ID_APP_MOVE_TO_FORGROUND = 0x20;
    public static final int SCENE_ID_APP_MOVE_TO_BACKGROUND = 0x21;

    // Scend id for SCENE_TYPE_EVENT
    public static final int SCENE_ID_EVENT_FPS = 30;

    public static final int SCENE_HIERARCHIAL_TYPE_FOREGROUND = 1;
    public static final int SCENE_HIERARCHIAL_TYPE_BACKGROUND = 2;
    public static final int SCENE_HIERARCHIAL_TYPE_WHOLE = 3;

    public static final String EXTRA_PACKAGENAME = "scene.extra.PACKAGENAME";
    public static final String EXTRA_UID = "scene.extra.UID";
    public static final String EXTRA_RAW = "scene.extra.RAW_DATA";
    public static final String EXTRA_FPS = "scene.fps";

    private int mSceneType;

    private int mSceneId;
    // the type of foreground / background or whole system
    private int mHierarchialType;

    // extra info for the scene
    private Bundle mExtras;

    public Scene() {
        mSceneType = SCENE_TYPE_NONE;
        mSceneId = SCENE_ID_NONE;
    }

    public Scene(int type) {
        mSceneType = type;
        mSceneId = SCENE_ID_NONE;
    }

    public Scene(Scene scene) {
        mSceneType = scene.getSceneType();
        mSceneId = scene.getSceneId();
        mHierarchialType = scene.getHierarchialType();
        if (scene.getSceneExtras() != null)
            mExtras = new Bundle(scene.getSceneExtras());
    }

    public int getSceneType() {
        return mSceneType;
    }

    public void setSceneType(int type) {
        mSceneType = type;
    }

    public void setHierarchialType(int type) {
        mHierarchialType = type;
    }

    public int getHierarchialType() {
        return mHierarchialType;
    }

    public void setSceneId(int id) {
        mSceneId = id;
    }

    public int getSceneId() {
        return mSceneId;
    }

    public void setSceneExtras(Bundle data) {
        mExtras = data;
    }

    public Bundle getSceneExtras() {
        return mExtras;
    }

    Scene(Parcel in) {
        DEBUG = in.readByte() != 0;
        mSceneId = in.readInt();
        mHierarchialType = in.readInt();
        mExtras = in.readBundle();
    }

    public static final Parcelable.Creator<Scene> CREATOR = new Parcelable.Creator<Scene>() {
        @Override
        public Scene createFromParcel(Parcel in) {
            return new Scene(in);
        }

        @Override
        public Scene[] newArray(int size) {
            return new Scene[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (DEBUG ? 1 : 0));
        dest.writeInt(mSceneId);
        dest.writeInt(mHierarchialType);
        dest.writeBundle(mExtras);
    }
}
