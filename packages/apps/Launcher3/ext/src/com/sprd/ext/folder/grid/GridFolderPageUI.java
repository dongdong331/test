package com.sprd.ext.folder.grid;

import android.content.Context;
import android.support.v4.graphics.ColorUtils;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Hotseat;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ScrimView;
import com.sprd.ext.LogUtils;
import com.sprd.ext.folder.FolderIconUtils;

public class GridFolderPageUI {
    private static int mNumFolderRows;
    private static int mNumFolderColumns;

    public static int mGridNumFolderRows;
    public static int mGridNumFolderColumns;

    private static int mDragHandleAlpha;

    private static final int INACTIVE_ALPHA = (int) (0.50f * 255);

    public static void initFolderRowsAndColumns(Context context, InvariantDeviceProfile inv) {
        mNumFolderRows = inv.numFolderRows;
        mNumFolderColumns = inv.numFolderColumns;

        mGridNumFolderRows = context.getResources().getInteger(R.integer.grid_folder_icon_rows);
        mGridNumFolderColumns = context.getResources().getInteger(R.integer.grid_folder_icon_columns);

        updateFolderRowAndColumns(inv);
    }

    public static void updateFolderRowAndColumns(InvariantDeviceProfile inv) {
        if (FolderIconUtils.getInstance().isGridFolderIcon()) {
            inv.numFolderRows = mGridNumFolderRows;
            inv.numFolderColumns = mGridNumFolderColumns;
        } else {
            inv.numFolderRows = mNumFolderRows;
            inv.numFolderColumns = mNumFolderColumns;
        }
    }

    public static int getFolderPageLayoutResId(){
        return FolderIconUtils.getInstance().isGridFolderIcon() ?
                R.layout.grid_folder_page : R.layout.user_folder_icon_normalized;
    }

    public static void showWorkspaceAndHotseat(Launcher launcher, boolean isOpen) {
        if (FolderIconUtils.getInstance().isGridFolderIcon()) {
            if (LogUtils.DEBUG_ALL) {
                LogUtils.d("GridFolderPageUI", "showWorkspaceAndHotseat is " + isOpen);
            }

            float finalAlpha = isOpen ? 0f : 1f;
            Workspace workspace = launcher.getWorkspace();
            Hotseat hotseat = launcher.getHotseat();
            if (workspace != null) {
                workspace.setAlpha(finalAlpha);
                if (workspace.getPageIndicator() != null) {
                    workspace.getPageIndicator().setAlpha(finalAlpha);
                    if(finalAlpha == 1f && launcher.isInState(LauncherState.SPRING_LOADED)) {
                        workspace.showPageIndicatorAtCurrentScroll();
                    }
                }
            }

            if (hotseat != null) {
                hotseat.setAlpha(finalAlpha);
            }

            ScrimView scrimView = launcher.findViewById(R.id.scrim_view);
            if (isOpen) {
                mDragHandleAlpha = scrimView.mDragHandleAlpha;
            }
            if (mDragHandleAlpha > 0) {
                scrimView.setDragHandleAlpha(isOpen ? 0 : mDragHandleAlpha);
            }
        }
    }

    public static void centerAboutIcon(Launcher launcher, Folder folder) {
        DeviceProfile grid = launcher.getDeviceProfile();
        DragLayer.LayoutParams lp = (DragLayer.LayoutParams) folder.getLayoutParams();

        int width = folder.getFolderWidth();
        int height = folder.getFolderHeight();

        int offsetY = grid.isLandscape ? grid.getTotalWorkspacePadding().y * 2 : 0;

        lp.width = width;
        lp.height = height;
        lp.x = (grid.availableWidthPx - width) / 2;
        lp.y = (grid.availableHeightPx - height) / 2 + offsetY;
    }

    public static void updateFolderPageIndicatorColor(Folder folder) {
        int activeColor;
        int inActiveColor;
        if (FolderIconUtils.getInstance().isGridFolderIcon()) {
            activeColor = Themes.getAttrColor(folder.getContext(), R.attr.workspaceTextColor);
            inActiveColor =  ColorUtils.setAlphaComponent(activeColor, INACTIVE_ALPHA);
        } else {
            activeColor = Themes.getColorAccent(folder.getContext());
            inActiveColor = Themes.getAttrColor(folder.getContext(), android.R.attr.colorControlHighlight);
        }

        folder.mPageIndicator.updatePageIndicatorColor(activeColor, inActiveColor);
    }
}
