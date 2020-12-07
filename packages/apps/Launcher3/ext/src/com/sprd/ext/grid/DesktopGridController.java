package com.sprd.ext.grid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.sprd.ext.LauncherSettingsExtension;
import com.sprd.ext.UtilitiesExt;

/**
 * Created on 9/24/18.
 */
public class DesktopGridController {

    private static final String KEY_ROW_AND_COLUMN_PREF = "pref_row_and_column_value";
    public static final String GRID_DIALOG_SHOWING_KEY = "grid_dialog_showing";

    public static DesktopGridController INSTANCE;

    private RowAndColumnPickerDialog mDialog;
    private Bundle mDialogState;

    public static DesktopGridController getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DesktopGridController();
        }
        return INSTANCE;
    }

    public static void saveGridSize(Context context, int row, int column) {
        SharedPreferences.Editor editor = Utilities.getDevicePrefs(context).edit();
        editor.putString(KEY_ROW_AND_COLUMN_PREF, UtilitiesExt.getPointString(row, column));
        editor.commit();
    }

    public static String getGridSize(Context context) {
        SharedPreferences sharedPreferences = Utilities.getDevicePrefs(context);
        return sharedPreferences.getString(KEY_ROW_AND_COLUMN_PREF, "");
    }

    public void handlePreferenceUi(Preference preference, boolean showing) {
        Context context = preference.getContext();
        InvariantDeviceProfile idp = LauncherAppState.getIDP(context);
        StringBuilder builder = new StringBuilder();
        builder.append(idp.numRows).append("x").append(idp.numColumns);

        preference.setSummary(builder.toString());
        preference.setOnPreferenceClickListener(new GridPreferenceListener());

        if (showing) {
            createAndShowGridDialog(context, true);
        }
    }

    private void createAndShowGridDialog(Context context, boolean restore) {
        removeDialogIfNeeded();

        InvariantDeviceProfile idp = LauncherAppState.getIDP(context);
        mDialog = new RowAndColumnPickerDialog(context, idp.numRows, idp.numColumns);
        mDialog.setTitle(R.string.desktop_grid);
        if (restore && mDialogState != null) {
            mDialog.onRestoreInstanceState(mDialogState);
            mDialogState = null;
        }
        mDialog.show();
    }

    public void removeDialogIfNeeded() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }

    public void onSaveInstanceState() {
        if (mDialog != null) {
            mDialogState = mDialog.onSaveInstanceState();
        }
    }

    public boolean isPickerShowing() {
        if (mDialog != null) {
            return mDialog.isShowing();
        }
        return false;
    }

    private class GridPreferenceListener implements Preference.OnPreferenceClickListener {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (LauncherSettingsExtension.sIsUserAMonkey) {
                return false;
            }

            if (LauncherSettingsExtension.PREF_DESKTOP_GRID_KEY.equals(preference.getKey())) {
                createAndShowGridDialog(preference.getContext(), false);
                return true;
            }
            return false;
        }
    }
}
