package com.sprd.ext.circularslide;

import android.content.Context;
import android.preference.Preference;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.sprd.ext.LauncherAppMonitor;
import com.sprd.ext.LauncherAppMonitorCallback;
import com.sprd.ext.LauncherSettingsExtension;
import com.sprd.ext.LogUtils;

/**
 * Created by SPRD on 2018/11/5.
 */
public class CycleScrollController implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "CycleScrollController";

    private static final String PREF_ENABLE_MINUS_ONE = "pref_enable_minus_one";

    private final Context mContext;
    private final LauncherAppMonitor mMonitor;
    private Preference mPref;
    private final LauncherAppMonitorCallback mAppMonitorCallback = new LauncherAppMonitorCallback() {
        @Override
        public void onLauncherCreated() {
            boolean enable = Utilities.getPrefs(mContext)
                    .getBoolean(LauncherSettingsExtension.PREF_CIRCULAR_SLIDE_KEY,
                            mContext.getResources().getBoolean(R.bool.default_circle_slide));
            updateCycleScrollState(enable);
        }

        @Override
        public void onAppSharedPreferenceChanged(String key) {
            if (PREF_ENABLE_MINUS_ONE.equals(key)) {
                updateCycleScrollPrefEnable();
            }
        }
    };

    public CycleScrollController(Context context, LauncherAppMonitor monitor) {
        mContext = context;
        mMonitor = monitor;
        mMonitor.registerCallback(mAppMonitorCallback);
    }

    public void setPref(Preference preference) {
        mPref = preference;
    }

    public void updateCycleScrollPrefEnable() {
        if (mPref != null) {
            boolean minusOne = Utilities.getPrefs(mContext).getBoolean(PREF_ENABLE_MINUS_ONE, false);
            mPref.setEnabled(!minusOne);
        }
    }

    private void updateCycleScrollState(boolean enable) {
        if (mMonitor.getLauncher() != null) {
            Workspace ws = mMonitor.getLauncher().getWorkspace();
            if (ws != null) {
                ws.setCircularSlideEnabled(enable);
                if (LogUtils.DEBUG_ALL) {
                    LogUtils.d(TAG, "updateCycleScrollState:" + enable);
                }
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        updateCycleScrollState((Boolean) newValue);
        return true;
    }
}
