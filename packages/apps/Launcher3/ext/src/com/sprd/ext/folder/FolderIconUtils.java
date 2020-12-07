package com.sprd.ext.folder;

import android.content.res.Resources;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.folder.ClippedFolderIconLayoutRule;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.PreviewBackground;
import com.sprd.ext.LauncherAppMonitor;
import com.sprd.ext.folder.grid.GridFolderIconLayoutRule;
import com.sprd.ext.folder.grid.GridFolderPageUI;
import com.sprd.ext.folder.grid.GridPreviewBackground;

public class FolderIconUtils {

    public static FolderIconUtils INSTANCE;
    private FolderIconController mController;

    public static FolderIconUtils getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FolderIconUtils();
        }
        return INSTANCE;
    }

    private FolderIconUtils() {
        LauncherAppMonitor monitor = LauncherAppMonitor.getInstanceNoCreate();
        if (monitor != null) {
            mController = monitor.getFolderIconController();
        }
    }

    public boolean isNativeFolderIcon() {
        boolean isNative = true;
        if (mController != null) {
            isNative = mController.isNativeFolderIcon();
        }
        return isNative;
    }

    public boolean isGridFolderIcon() {
        boolean isGrid = false;
        if (mController != null) {
            isGrid = mController.isGridFolderIcon();
        }
        return isGrid;
    }

    public void addListener(FolderIconController.FolderIconModelListener listener) {
        if (mController != null) {
            mController.addListener(listener);
        }
    }

    public void removeListener(FolderIconController.FolderIconModelListener listener) {
        if (mController != null) {
            mController.removeListener(listener);
        }
    }

    public BaseFolderIconModel getCurrentIconModel() {
        if (mController != null) {
            return mController.getCurrentIconModel();
        }
        return null;
    }

    public PreviewBackground createPreviewBackground() {
        PreviewBackground bg;
        if (isGridFolderIcon()) {
            bg = new GridPreviewBackground();
        } else {
            bg = new PreviewBackground();
        }
        return bg;
    }

    private ClippedFolderIconLayoutRule createLayoutRule() {
        ClippedFolderIconLayoutRule rule;
        if (isGridFolderIcon()) {
            rule = new GridFolderIconLayoutRule();
        } else {
            rule = new ClippedFolderIconLayoutRule();
        }
        return rule;
    }

    public int getMaxNumItems() {
        int maxNumItems;
        if(isGridFolderIcon()) {
            maxNumItems = GridFolderPageUI.mGridNumFolderRows * GridFolderPageUI.mGridNumFolderColumns;
        } else {
            maxNumItems = ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;
        }
        return maxNumItems;
    }

    public void fillIconLayoutAndBackground(FolderIcon icon) {
        icon.setFolderBackground(createPreviewBackground());
        icon.setLayoutRule(createLayoutRule());
    }

    private void updateFolderIcon(Launcher launcher, FolderIcon icon) {
        DeviceProfile dp = launcher.getDeviceProfile();
        Resources resources = launcher.getResources();
        dp.updateAvailableFolderCellDimensions(resources.getDisplayMetrics(), resources);

        dp.updateFolderIconSize();
        fillIconLayoutAndBackground(icon);
        FolderIcon.bindFolder(launcher, icon);

        icon.getPreviewItemManager().computePreviewDrawingParamsIfNeeded();
        icon.invalidate();
    }

    public void updateFolderIconIfModelChanged(Launcher launcher, FolderIcon icon) {
        GridFolderPageUI.updateFolderRowAndColumns(LauncherAppState.getIDP(launcher));
        updateFolderIcon(launcher, icon);

        GridFolderPageUI.updateFolderPageIndicatorColor(icon.getFolder());
    }

    public void updateFolderPageIfConfigChanged(Launcher launcher) {
        DeviceProfile dp = launcher.getDeviceProfile();
        Resources resources = launcher.getResources();
        dp.updateAvailableFolderCellDimensions(resources.getDisplayMetrics(), resources);
    }
}
