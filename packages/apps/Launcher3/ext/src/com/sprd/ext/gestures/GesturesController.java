package com.sprd.ext.gestures;

import android.content.Context;
import android.preference.Preference;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.sprd.ext.FeatureOption;
import com.sprd.ext.LauncherAppMonitor;
import com.sprd.ext.LauncherAppMonitorCallback;
import com.sprd.ext.LauncherSettingsExtension;
import com.sprd.ext.LogUtils;
import com.sprd.ext.UtilitiesExt;
import com.sprd.ext.gestures.LauncherRootViewGestures.Gesture;

/**
 * Created by SPRD on 2018/11/5.
 */
public class GesturesController implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "GesturesController";

    private static final String NONE = "none";
    private static final String NOTIFICATION = "notification";
    private static final String SEARCH = "search";

    private final Context mContext;
    private final LauncherAppMonitor mMonitor;
    private LauncherRootViewGestures mGesture;

    private String mOneFingerDownAction;
    private LauncherRootViewGestures.OnGestureListener mOneFingerDownListener;

    private final LauncherAppMonitorCallback mAppMonitorCallback = new LauncherAppMonitorCallback() {
        @Override
        public void onLauncherCreated() {
            mGesture = new LauncherRootViewGestures(mMonitor.getLauncher());
            if (FeatureOption.SPRD_GESTURE_ONE_FINGER_PULLDOWN) {
                mOneFingerDownAction = Utilities.getPrefs(mContext)
                        .getString(LauncherSettingsExtension.PREF_ONEFINGER_PULLDOWN,
                                mContext.getResources().getString(R.string.default_pull_down_value));
                mOneFingerDownListener = gesture -> {
                    if (gesture == Gesture.ONE_FINGER_SLIDE_DOWN) {
                        return doAction(mOneFingerDownAction);
                    }
                    return false;
                };
            }
        }

        @Override
        public void onLauncherStart() {
            if (mGesture != null) {
                mGesture.registerOnGestureListener(mOneFingerDownListener);
            }
        }

        @Override
        public void onLauncherStop() {
            if (mGesture != null) {
                mGesture.unregisterOnGestureListener(mOneFingerDownListener);
            }
        }

        @Override
        public void onLauncherDestroy() {
            mOneFingerDownListener = null;
            mGesture = null;
        }
    };

    public GesturesController(Context context, LauncherAppMonitor monitor) {
        mContext = context;
        mMonitor = monitor;
        mMonitor.registerCallback(mAppMonitorCallback);
    }

    public LauncherRootViewGestures getGesture() {
        return mGesture;
    }

    private boolean doAction(String action) {
        switch (action) {
            case SEARCH:
                if (mMonitor.getLauncher() != null) {
                    mMonitor.getLauncher().onSearchRequested();
                }
                return true;
            case NOTIFICATION:
                UtilitiesExt.openNotifications(mContext);
                return true;
            case NONE:
                //do nothing
                return true;
            default:
                LogUtils.w(TAG, "error action : " + action);
                break;
        }
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        switch (key) {
            case LauncherSettingsExtension.PREF_ONEFINGER_PULLDOWN: {
                mOneFingerDownAction = (String) newValue;
                return true;
            }
            default:
                break;
        }
        return false;
    }
}
