package android.os.sprdpower;

import android.os.sprdpower.ISceneStatsNotifier;
import android.os.sprdpower.Scene;

/**
 * SPRD added for Scene recognize
 * @hide
 */
interface ISceneRecognizeManagerEx {

    /** Return all of current scene status to client */
    List<Scene> getCurrentScene();

    /**
     * Be used to register client's callback function.
     * If the status of scene which client is interested
     * is changed, server would notify client with the
     * registered callback function.
     */
    void registerSceneStatsNotifier(ISceneStatsNotifier callback, int type);
}
