/*
 ** Copyright 2016 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.content.Context;
import android.util.Slog;
import android.os.sprdpower.AppPowerSaveConfig;
import android.os.SystemClock;

import com.android.server.LocalServices;
import com.android.server.power.sprdpower.PowerController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//
//  ----------------- PowerSaveHelper ----------------------
//

abstract class PowerSaveHelper {
    static final String TAG = "PowerSaveHelper";

    int mMask = AppPowerSaveConfig.MASK_NULL;
    final PowerController.LocalService mPowerControllerInternal;
    final PowerConfig mPowerConfig;
    final PowerConfig.Constants mConstants;
    final AppStateInfoCollector mAppStateInfoCollector;
    final Context mContext;

    PowerSaveHelper(Context context, int mask) {
        mContext = context;
        mMask = mask;
        mPowerConfig = PowerConfig.getInstance();
        mConstants = mPowerConfig.getConstants();
        mPowerControllerInternal = LocalServices.getService(PowerController.LocalService.class);
        mAppStateInfoCollector = AppStateInfoCollector.getInstance(mContext);
    }

    void onPowerSaveConfigChanged(int configType, String appName, int oldValue, int newValue, boolean init) {
        if (AppPowerSaveConfig.MASK_NULL== mMask)
            return;
    }

    void onPowerSaveModeChanged(int mode) {
    }

    void onPowerSaveModeChanging(int newMode) {
    }

    // if needed, should override this function to call "updateAppEvaluatedTimeStamp(AppState appState, boolean clear)"
    boolean updateAppEvaluatedTimeStamp(AppState appState) {
        return true;
    }

    boolean updateAppEvaluatedTimeStamp(AppState appState, boolean clear) {
        return true;
    }

    /*
     * handle the case:
     * the state of the app is changed
     */
    void updateAppStateChanged(AppState appState, int stateChange) {
    }

    /*
     * check this app should be constrained or not
     * return true if this app should be constraint
     * others return false
     */
    boolean checkAppStateInfo(AppState appState, final long nowELAPSED) {
        return true;
    }

    // add all apps in list to the PowerGuru Constrained App List
    void applyConstrain() {
    }

    // clear the app from the PowerGuru Constrained App List
    void clearConstrain(AppState appState) {
    }

    /*
     * Note device is enter standby state ( screen off / unpluged)
     */
    void noteDeviceStateChanged(boolean bStandby, final long nowELAPSED) {
    }

    void onAppUsageStateChanged(String packName, int uid, int state) {
    }

    // to check the AppState
    void checkAppRequirement(AppState appState) {
    }

    public String toString() {
        return TAG;
    }
}


