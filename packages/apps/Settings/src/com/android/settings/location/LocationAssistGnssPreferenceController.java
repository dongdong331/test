
package com.android.settings.location;

import java.util.List;

import android.content.Context;

import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settings.R;
import com.android.settings.Utils;

import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.util.Log;

/**
 * Preference controller for assist gnss.
 *
 * Display this preference if device is cmcc version.
 */

public class LocationAssistGnssPreferenceController extends LocationBasePreferenceController {


    private static final String TAG = LocationAssistGnssPreferenceController.class.getSimpleName();
    static final String KEY_LOCATION_ASSIST_GNSS = "location_assist_gnss";
    private Preference mAssistGnssPre;

    public LocationAssistGnssPreferenceController(Context context, Lifecycle lifecycle) {
        super(context, lifecycle);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mAssistGnssPre = screen.findPreference(KEY_LOCATION_ASSIST_GNSS);
    }

    @Override
    public void onLocationModeChanged(int mode, boolean restricted) {
        // Update preference state. If location is enabled this preference is enabled.
        Log.d(TAG, "onLocationModeChanged mode " + mode);
        if (mAssistGnssPre != null) {
            mAssistGnssPre.setEnabled(mLocationEnabler.isEnabled(mode));
        }
    }

    @Override
    public String getPreferenceKey() {
        return KEY_LOCATION_ASSIST_GNSS;
    }
}
