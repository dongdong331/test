package com.sprd.ext;

import android.app.ActivityManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;

import com.android.launcher3.R;
import com.android.launcher3.SessionCommitReceiver;
import com.sprd.ext.circularslide.CycleScrollController;
import com.sprd.ext.dynamicicon.DynamicIconManager;
import com.sprd.ext.dynamicicon.DynamicIconUtils;
import com.sprd.ext.folder.FolderIconController;
import com.sprd.ext.gestures.GesturesController;
import com.sprd.ext.grid.DesktopGridController;
import com.sprd.ext.multimode.MultiModeController;
import com.sprd.ext.unreadnotifier.UnreadInfoController;

/**
 * Created by SPRD on 6/15/17.
 */

public class LauncherSettingsExtension {

    private static final String PREF_KEY_DYNAMICICON = "pref_dynamicIcon";
    private static final String PREF_KEY_UNREAD = "pref_unread";
    public static final String PREF_ONEFINGER_PULLDOWN = "pref_pulldown_action";
    public static final String PREF_CIRCULAR_SLIDE_KEY = "pref_circular_slide_switch";
    public static final String PREF_HOME_SCREEN_STYLE_KEY = "pref_home_screen_style";
    public static final String PREF_FOLDER_ICON_MODE_KEY = "pref_folder_icon_model";
    public static final String PREF_DESKTOP_GRID_KEY = "pref_desktop_grid";

    private PreferenceFragment mFragment;
    private DesktopGridController mGridController;
    private final LauncherAppMonitor mMonitor;

    public static boolean sIsUserAMonkey = false;

    public LauncherSettingsExtension(PreferenceFragment fragment) {
        mFragment = fragment;
        mMonitor = LauncherAppMonitor.getInstance(mFragment.getActivity());
    }

    public void initPreferences(Bundle savedInstanceState) {
        //Remove add icon to home preference in case Single layer mode
        if (MultiModeController.isSingleLayerMode( )) {
            mFragment.getPreferenceScreen( ).removePreference(
                    mFragment.findPreference(SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY));
        }

        mFragment.addPreferencesFromResource(R.xml.launcher_preferences_extension);

        /* add for dynamic icon */
        Preference dynamicPref = mFragment.findPreference(PREF_KEY_DYNAMICICON);
        DynamicIconManager dim = DynamicIconManager.getInstance(mFragment.getActivity());
        if (DynamicIconUtils.anyDynamicIconSupport() && dim != null && dim.hasDynamicIcon()) {
            dynamicPref.setOnPreferenceClickListener(dim);
        } else {
            mFragment.getPreferenceScreen().removePreference(dynamicPref);
        }

        /* add for unread info */
        Preference unreadPreference = mFragment.findPreference(PREF_KEY_UNREAD);
        UnreadInfoController uic = mMonitor.getUnreadInfoController();
        if(uic != null) {
            unreadPreference.setOnPreferenceClickListener(uic);
        } else {
            mFragment.getPreferenceScreen().removePreference(unreadPreference);
        }

        /* add for single layer launcher model */
        Preference slPreference = mFragment.findPreference(PREF_HOME_SCREEN_STYLE_KEY);
        MultiModeController mmc = mMonitor.getMultiModeController();
        if(MultiModeController.isSupportDynamicChange() && mmc != null) {
            slPreference.setOnPreferenceChangeListener(mmc);
        } else {
            mFragment.getPreferenceScreen().removePreference(slPreference);
        }

        /* add for one finger pull down action */
        ListPreference onefingerpdPref = (ListPreference) mFragment.findPreference(PREF_ONEFINGER_PULLDOWN);
        if (onefingerpdPref != null) {
            GesturesController gc = mMonitor.getGesturesController();
            if (FeatureOption.SPRD_GESTURE_ONE_FINGER_PULLDOWN && gc != null) {
                onefingerpdPref.setOnPreferenceChangeListener(gc);
            } else {
                mFragment.getPreferenceScreen().removePreference(onefingerpdPref);
            }
        }

        /* add for circle slide */
        SwitchPreference circularSlidePref = (SwitchPreference) mFragment.findPreference(PREF_CIRCULAR_SLIDE_KEY);
        CycleScrollController csc = mMonitor.getCycleScrollController();
        if(csc != null) {
            circularSlidePref.setOnPreferenceChangeListener(csc);
            csc.setPref(circularSlidePref);
            csc.updateCycleScrollPrefEnable();
        } else {
            mFragment.getPreferenceScreen().removePreference(circularSlidePref);
        }

        /* add for folder icon mode */
        ListPreference folderIconPref = (ListPreference) mFragment.findPreference(PREF_FOLDER_ICON_MODE_KEY);
        if (folderIconPref != null) {
            FolderIconController fic = mMonitor.getFolderIconController();
            boolean showMenu = FolderIconController.showFolderIconModelSettings(mFragment.getActivity());
            if (showMenu && fic != null) {
                fic.initPreference(folderIconPref);
            } else {
                mFragment.getPreferenceScreen().removePreference(folderIconPref);
            }
        }

        /* Add for the desktop grid */
        Preference desktopGridPref = mFragment.findPreference(PREF_DESKTOP_GRID_KEY);
        if (FeatureOption.SPRD_DESKTOP_GRID_SUPPORT) {
            boolean showPicker = savedInstanceState != null
                    && savedInstanceState.getBoolean(DesktopGridController.GRID_DIALOG_SHOWING_KEY);
            mGridController = DesktopGridController.getInstance();
            mGridController.handlePreferenceUi(desktopGridPref, showPicker);
        } else {
            mFragment.getPreferenceScreen().removePreference(desktopGridPref);
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        if (mGridController != null) {
            outState.putBoolean(DesktopGridController.GRID_DIALOG_SHOWING_KEY,
                    mGridController.isPickerShowing());
            mGridController.onSaveInstanceState();
        }
    }

    public void onStart() {
        sIsUserAMonkey = ActivityManager.isUserAMonkey();
    }

    public void onDestroy() {
        if (mGridController != null) {
            mGridController.removeDialogIfNeeded();
        }
    }
}
