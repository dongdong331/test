package com.android.server.power.sprdpower;

import android.content.Context;

import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.SparseArray;
import android.util.Slog;

import com.android.server.LocationManagerService;

import java.util.ArrayList;

public class PowerControllerHelper {

    private static PowerControllerHelper sInstance;

    private boolean mIgnoreProcStateForAppIdle = false;


    private SparseArray<ArrayMap<String, Integer>> mGpsConstraintAppListForUsers = new SparseArray<>();

    private final ArrayList<LocationRequestListener> mLocationRequestListeners
            = new ArrayList<LocationRequestListener>();

    private LocationManagerService mLocationService = null;

    // used internally for synchronization
    private final Object mLock = new Object();

    public PowerControllerHelper(Context context) {
    }

    public static PowerControllerHelper getInstance(Context context) {
        synchronized (PowerControllerHelper.class) {
            if (sInstance == null) {
                sInstance = new PowerControllerHelper(context);
            }
            return sInstance;
        }
    }

    public void setIgnoreProcStateForAppIdle(boolean ignore) {
        mIgnoreProcStateForAppIdle = ignore;
    }

    public boolean ignoreProcStateForAppIdle() {
        return mIgnoreProcStateForAppIdle;
    }


    public void setLocationManagerService(LocationManagerService locationManager) {
        mLocationService = locationManager;
    }

    /**
     * LocationRequestListener: tell the listener the location request state of the app
     * state: 0: for stop
     *          1: for start
     */
    public interface LocationRequestListener {
        public void onLocationRequest(String packageName, int uid, int state);
    }


    /**
     * appName: name of the app
     * enable: ture: the app can access GPS. false: the app cannot access GPS
     */
    public void noteGpsConstraintStateChanged(String appName, int uid, boolean enable) {
            updateGpsConstraintAppList(appName, uid, enable);
            if (mLocationService != null && mLocationService.needApplyAllProviderRequirements(appName, uid)) {
                mLocationService.applyAllProviderRequirements();
            }
    }

    public void registerLocationRequestListener(LocationRequestListener listener) {
        synchronized (mLock) {
            mLocationRequestListeners.add(listener);
        }
    }

    public boolean inGpsConstraintAppList(String appName, int uid) {
        synchronized (mLock) {
            ArrayMap<String, Integer> mGpsConstraintAppList = getGpsConstraintAppList(UserHandle.getUserId(uid));

            int index = mGpsConstraintAppList.indexOfKey(appName);
            if (index >= 0)
                return true;
            else
                return false;
        }
    }

    public void notifyLocationRequestListeners(String pkgName, int uid, int state) {
        for (int i=0; i<mLocationRequestListeners.size(); i++) {
            mLocationRequestListeners.get(i).onLocationRequest(pkgName, uid, state);
        }
    }


    private void updateGpsConstraintAppList(String appName, int uid, boolean enable) {
        ArrayMap<String, Integer> mGpsConstraintAppList = getGpsConstraintAppList(UserHandle.getUserId(uid));

        synchronized (mLock) {
            if (!enable) {
                mGpsConstraintAppList.put(appName, uid);
            } else {
                mGpsConstraintAppList.remove(appName);
            }
        }
    }


    private ArrayMap<String, Integer> getGpsConstraintAppList(int userId) {
        synchronized (mLock) {
            ArrayMap<String, Integer> mGpsConstraintAppList = mGpsConstraintAppListForUsers.get(userId);
            if (mGpsConstraintAppList == null) {
                mGpsConstraintAppList = new ArrayMap<>();
                mGpsConstraintAppListForUsers.put(userId, mGpsConstraintAppList);
            }
            return mGpsConstraintAppList;
        }
    }


}
