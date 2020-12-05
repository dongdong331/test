package android.os.sprdpower;

import android.os.sprdpower.Scene;

/**
 * SPRD added for Scene recognize
 * @hide
 */
oneway interface ISceneStatsNotifier {
    /**
     * Callback function that is used to notify client the
     * current scene status.
     */
     void onNotifySceneStats(in Scene scene);
}
