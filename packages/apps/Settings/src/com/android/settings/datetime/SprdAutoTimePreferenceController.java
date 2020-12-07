/*
 *Created by spreadst
 */

package com.android.settings.datetime;

import android.content.Context;
import android.os.SystemProperties;
import android.provider.Settings;
import android.support.v7.preference.Preference;
import android.support.v7.preference.ListPreference;
import android.util.Log;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedSwitchPreference;

import com.android.settings.location.GpsHelper;
import android.provider.Settings.SettingNotFoundException;
import android.location.LocationManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.view.KeyEvent;
import com.android.settings.R;
import com.android.settings.Utils;

public class SprdAutoTimePreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, Preference.OnPreferenceChangeListener, DialogInterface.OnClickListener,
        OnCancelListener {

    private static String TAG = SprdAutoTimePreferenceController.class.getSimpleName();
    private static final String KEY_AUTO_TIME_LIST = Utils.SUPPORT_GNSS ? "auto_time_list"
            : "auto_time_list_no_gps";
    private static final int AUTO_TIME_NETWORK_INDEX = 0;
    private static final int AUTO_TIME_GPS_INDEX = 1;
    private static final int AUTO_TIME_OFF_INDEX = Utils.SUPPORT_GNSS ? 2 : 1;

    public static final int DIALOG_AUTO_TIME_GPS_CONFIRM = 2;
    private AlertDialog mGpsDialog;
    private final SprdAutoTimePreferenceHost mSprdHost;
    private final SprdGpsUpdateTimeCallback mSprdCallback;

    public interface SprdAutoTimePreferenceHost extends SprdGpsUpdateTimeCallback {
        void showGpsConfirm();
    }

    public SprdAutoTimePreferenceController(Context context, SprdAutoTimePreferenceHost host,
            SprdGpsUpdateTimeCallback sprdcallback) {
        super(context);
        mSprdHost = host;
        mSprdCallback = sprdcallback;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        Log.d(TAG, "updateState preference");
        if (!(preference instanceof ListPreference)) {
            return;
        }
        boolean autoTimeEnabled = getAutoState(Settings.Global.AUTO_TIME);
        boolean autoTimeGpsEnabled = getAutoState(Settings.Global.AUTO_TIME_GPS);
        if (autoTimeEnabled) {
            ((ListPreference) preference).setValueIndex(AUTO_TIME_NETWORK_INDEX);
        } else if (Utils.SUPPORT_GNSS && autoTimeGpsEnabled) {
            ((ListPreference) preference).setValueIndex(AUTO_TIME_GPS_INDEX);
        } else {
            ((ListPreference) preference).setValueIndex(AUTO_TIME_OFF_INDEX);
        }
        ((ListPreference) preference).setSummary(((ListPreference) preference).getValue());
    }

    @Override
    public String getPreferenceKey() {
        return KEY_AUTO_TIME_LIST;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String value = newValue.toString().trim();
        int index = ((ListPreference) preference).findIndexOfValue(value);
        boolean autoEnabled = true;
        if (index == AUTO_TIME_NETWORK_INDEX) {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.AUTO_TIME, 1);
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.AUTO_TIME_GPS, 0);
        } else if (Utils.SUPPORT_GNSS && index == AUTO_TIME_GPS_INDEX) {
            if (mGpsDialog == null || !mGpsDialog.isShowing()) {
                mSprdHost.showGpsConfirm();
            }
        } else {
            Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME, 0);
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.AUTO_TIME_GPS, 0);
            autoEnabled = false;
        }
        mSprdCallback.updatePreference(mContext);
        return true;
    }

    private boolean getAutoState(String name) {
        try {
            return Settings.Global.getInt(mContext.getContentResolver(), name) > 0;
        } catch (SettingNotFoundException snfe) {
            return false;
        }
    }

    public boolean isEnabled() {
        boolean enabled = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AUTO_TIME, 0) > 0;
        if (Utils.SUPPORT_GNSS) {
            enabled = enabled || (Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.AUTO_TIME_GPS, 0) > 0);
        }
        return enabled;
    }

    private RestrictedLockUtils.EnforcedAdmin getEnforcedAdminProperty() {
        return RestrictedLockUtils.checkIfAutoTimeRequired(mContext);
    }

    public Dialog buildGpsConfirm(Activity activity) {
        LocationManager mLocationManager = (LocationManager) activity
                .getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        int msg;
        if (gpsEnabled) {
            msg = R.string.gps_time_sync_attention_gps_on;
        } else {
            msg = R.string.gps_time_sync_attention_gps_off;
        }
        mGpsDialog = new AlertDialog.Builder(activity)
                .setMessage(activity.getResources().getString(msg))
                .setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.proxy_error)
                .setPositiveButton(android.R.string.yes, (OnClickListener) this)
                .setNegativeButton(android.R.string.no, (OnClickListener) this).create();
        mGpsDialog.setCanceledOnTouchOutside(false);
        mGpsDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    Log.d(TAG, "setOnKeyListener KeyEvent.KEYCODE_BACK");
                    reSetAutoTimePref();
                }
                return false; // default return false
            }
        });
        return mGpsDialog;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            Log.d(TAG, "Enable GPS time sync");
            LocationManager mLocationManager = (LocationManager) mContext.getSystemService(
                    Context.LOCATION_SERVICE);
            boolean gpsEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if (!gpsEnabled) {
                Log.d(TAG, "Enable GPS time sync gpsEnabled =" + gpsEnabled);
                int currentUserId = ActivityManager.getCurrentUser();
                Settings.Secure.putIntForUser(mContext.getContentResolver(),
                        Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_SENSORS_ONLY,
                        currentUserId);
            }
            Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME, 0);
            Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME_GPS, 1);
            mSprdCallback.updatePreference(mContext);
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            Log.d(TAG, "DialogInterface.BUTTON_NEGATIVE");
            reSetAutoTimePref();
        }
    }

    private void reSetAutoTimePref() {
        Log.d(TAG, "reset AutoTimePref as cancel the selection");
        mSprdCallback.updatePreference(mContext);
    }

    @Override
    public void onCancel(DialogInterface arg0) {
        Log.d(TAG, "onCancel Dialog");
        reSetAutoTimePref();
    }
}
