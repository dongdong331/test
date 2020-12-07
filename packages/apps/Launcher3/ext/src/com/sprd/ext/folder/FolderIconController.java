package com.sprd.ext.folder;

import android.content.Context;
import android.content.res.Resources;
import android.preference.ListPreference;
import android.preference.Preference;
import android.text.TextUtils;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.sprd.ext.FeatureOption;
import com.sprd.ext.LauncherAppMonitor;
import com.sprd.ext.LauncherAppMonitorCallback;
import com.sprd.ext.LauncherSettingsExtension;
import com.sprd.ext.LogUtils;
import com.sprd.ext.UtilitiesExt;
import com.sprd.ext.folder.grid.GridFolderIconModel;
import com.sprd.ext.multimode.MultiModeController;

import java.util.ArrayList;
import java.util.List;

public class FolderIconController implements Preference.OnPreferenceChangeListener, DeviceProfile.OnDeviceProfileChangeListener {
    private static final String TAG = "FolderIconController";

    // the "Annular" and "Grid" from the array folder_icon_model_values
    private static final String AUTO = "auto";
    private static final String ANNULAR = "annular";
    private static final String GRID = "grid";

    private static final String PREF_KEY = LauncherSettingsExtension.PREF_FOLDER_ICON_MODE_KEY;

    private Context mContext;
    private Launcher mLauncher;

    private String mOldModel;
    private static String sModel = ANNULAR;

    final private ArrayList<BaseFolderIconModel> mAllFolderIconModel = new ArrayList<>();
    final private ArrayList<FolderIconModelListener> mListeners = new ArrayList<>();

    public FolderIconController(Context context, LauncherAppMonitor monitor) {
        mContext = context;
        monitor.registerCallback(mAppMonitorCallback);
    }

    private LauncherAppMonitorCallback mAppMonitorCallback = new LauncherAppMonitorCallback() {
        @Override
        public void onLauncherPreCreate(Launcher launcher) {
            mOldModel = sModel = getCurrentFolderIconModel();
            mLauncher = launcher;
        }

        @Override
        public void onLauncherCreated() {
            loadAllFolderIconModels();
            mLauncher.addOnDeviceProfileChangeListener(FolderIconController.this);
        }

        @Override
        public void onLauncherResumed() {
            if (!mOldModel.equals(sModel)) {
                mOldModel = sModel;
                folderIconModelChanged();
            }
        }

        @Override
        public void onLauncherDestroy() {
            mAllFolderIconModel.clear();
            mListeners.clear();
            mLauncher.removeOnDeviceProfileChangeListener(FolderIconController.this);
        }
    };

    void addListener(FolderIconModelListener listener) {
        mListeners.add(listener);
    }

    void removeListener(FolderIconModelListener listener) {
        mListeners.remove(listener);
    }

    private void folderIconModelChanged() {
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onFolderIconModelChanged();
        }
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        if(mLauncher != null) {
            FolderIconUtils.getInstance( ).updateFolderPageIfConfigChanged(mLauncher);
        }
    }

    public interface FolderIconModelListener {
        void onFolderIconModelChanged();
    }

    private void setFolderIconModel(String folderIconMode) {
        sModel = verifyFolderIconModel(folderIconMode);
    }

    boolean isNativeFolderIcon() {
        return ANNULAR.equals(sModel);
    }

    boolean isGridFolderIcon() {
        return GRID.equals(sModel);
    }

    private void loadAllFolderIconModels(){
        GridFolderIconModel gfModel = new GridFolderIconModel(mContext, GRID);
        mAllFolderIconModel.add(gfModel);

        NativeFolderIconModel nfModel = new NativeFolderIconModel(mContext, ANNULAR);
        mAllFolderIconModel.add(nfModel);
    }

    BaseFolderIconModel getCurrentIconModel() {
        for(BaseFolderIconModel model: mAllFolderIconModel){
            if(sModel.equals(model.getIconModel())){
                return model;
            }
        }
        return new NativeFolderIconModel(mContext, ANNULAR);
    }

    private  String getCurrentFolderIconModel() {
        String value = Utilities.getPrefs(mContext).getString(PREF_KEY, getDefaultIconModel());
        return verifyFolderIconModel(value);
    }

    private String getDefaultIconModel() {
        String defaultModel = mContext.getResources().getString(R.string.default_folder_icon_model);
        return verifyFolderIconModel(defaultModel);
    }

    private String verifyFolderIconModel(String model) {
        String result;
        switch (model) {
            case ANNULAR:
            case GRID:
                result = model;
                break;
            case AUTO:
                result = MultiModeController.isSingleLayerMode() ? GRID : ANNULAR;
                break;
            default:
                LogUtils.w(TAG, model + "is a wrong mode, will use annular");
                result = ANNULAR;
                break;
        }
        return result;
    }

    public static boolean showFolderIconModelSettings(Context context) {
        return FeatureOption.SPRD_FOLDER_ICON_MODE_SUPPORT
                && UtilitiesExt.isDevSettingEnable(context)
                && context.getResources().getBoolean(R.bool.show_folder_icon_model_settings);
    }

    public void initPreference(ListPreference preference) {
        if (preference == null) {
            return;
        }

        final Resources res = preference.getContext().getResources();
        final CharSequence[] entries = res.getTextArray(R.array.folder_icon_model_entries);
        final CharSequence[] values = res.getTextArray(R.array.folder_icon_model_values);
        final CharSequence defaultValue = res.getText(R.string.default_folder_icon_model);

        final boolean isSupportDynamicChange = MultiModeController.isSupportDynamicChange();
        final int entryCount = entries.length + (isSupportDynamicChange ? 1 : 0);
        final List<CharSequence> modelEntries = new ArrayList<>(entryCount);
        final List<CharSequence> modelValues = new ArrayList<>(entryCount);

        if (isSupportDynamicChange) {
            //add auto items
            modelEntries.add(res.getText(R.string.auto_model));
            modelValues.add(AUTO);
        }

        for (int i = 0; i < entries.length; i++) {
            modelEntries.add(entries[i]);
            modelValues.add(values[i]);
        }

        preference.setEntries(modelEntries.toArray(new CharSequence[0]));
        preference.setEntryValues(modelValues.toArray(new CharSequence[0]));

        //init default value
        if (Utilities.getPrefs(mContext).getString(PREF_KEY, "").equals("")) {
            ArrayList<CharSequence> array = new ArrayList<>();
            CharSequence[] curValues = preference.getEntryValues();
            for (CharSequence curValue : curValues) {
                if (!TextUtils.isEmpty(curValue)) {
                    array.add(curValue);
                }
            }

            if (array.contains(defaultValue)) {
                preference.setValue(defaultValue.toString());
            } else {
                preference.setValue(verifyFolderIconModel(defaultValue.toString()));
            }

        }

        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (LauncherSettingsExtension.sIsUserAMonkey) {
            return false;
        }

        setFolderIconModel((String)newValue);
        return true;
    }
}
