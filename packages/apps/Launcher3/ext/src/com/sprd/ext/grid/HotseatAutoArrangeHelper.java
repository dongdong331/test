package com.sprd.ext.grid;

import android.view.View;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Hotseat;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.sprd.ext.FeatureOption;
import com.sprd.ext.LogUtils;

import java.util.ArrayList;

public class HotseatAutoArrangeHelper {

    private static final String TAG = "HotseatAutoArrangeHelper";

    public static final boolean IS_SUPPORT = FeatureOption.SPRD_HOTSEAT_ICON_ADAPTIVE_LAYOUT
            || FeatureOption.SPRD_DESKTOP_GRID_SUPPORT;

    public static boolean isFull(Launcher launcher) {
        if (launcher == null || !IS_SUPPORT) {
            return false;
        }

        Hotseat hs = launcher.getHotseat();
        int gridCount = hs.getGridCount();
        for (int i = 0; i < gridCount; i++) {
            int cx = hs.getCellXFromOrder(i);
            int cy = hs.getCellYFromOrder(i);
            if (!hs.getLayout().isOccupied(cx, cy)) {
                return false;
            }
        }
        return true;
    }

    public static boolean clearEmptyGrid(Launcher launcher) {
        if (launcher == null || !IS_SUPPORT) {
            return false;
        }
        if (isFull(launcher)) {
            return false;
        }

        ArrayList<View> views = backupHotseatChildes(launcher);
        Hotseat hs = launcher.getHotseat();
        boolean isLandscape = hs.mHasVerticalHotseat;
        int count = views.size();
        int countX = isLandscape ? 1 : Math.max(count, 1);
        int countY = isLandscape ? Math.max(count, 1) : 1;
        hs.getLayout().removeAllViews();
        hs.getLayout().setGridSize(countX, countY);
        for (int i = 0; i < count; i++) {
            if (!addViewToHotseat(launcher, views.get(i), i)) {
                LogUtils.e(TAG, "addViewToHotseat failed, rank:" + i);
            }
        }
        return true;
    }

    public static boolean insertEmptyGrid(Launcher launcher, int index) {
        if (launcher == null || !IS_SUPPORT) {
            return false;
        }

        if (!canInsert(launcher) || !isFull(launcher)) {
            return false;
        }

        ArrayList<View> views = backupHotseatChildes(launcher);
        Hotseat hs = launcher.getHotseat();
        final boolean isLandscape = hs.mHasVerticalHotseat;
        int newCount = views.size() + 1;
        int countX = isLandscape ? 1 :  newCount;
        int countY = isLandscape ? newCount : 1;
        hs.getLayout().removeAllViews();
        hs.getLayout().setGridSize(countX, countY);
        for (int i = 0; i < views.size(); i++) {
            if (i < index) {
                addViewToHotseat(launcher, views.get(i), i);
            } else {
                addViewToHotseat(launcher, views.get(i), i + 1);
            }
        }
        return true;
    }

    private static ArrayList<View> backupHotseatChildes(Launcher launcher) {
        Hotseat hs = launcher.getHotseat();
        int gridCount = hs.getGridCount();
        ArrayList<View> views = new ArrayList<>();
        for (int i = 0; i < gridCount; i++) {
            int cx = hs.getCellXFromOrder(i);
            int cy = hs.getCellYFromOrder(i);
            View v = hs.getLayout().getShortcutsAndWidgets().getChildAt(cx, cy);
            if (hs.getLayout().isOccupied(cx, cy)) {
                if (v != null) {
                    if (LogUtils.DEBUG_ALL) {
                        LogUtils.d(TAG, "backup child:" + i);
                    }
                    views.add(v);
                }
            }
        }
        return views;
    }

    public static boolean addViewToHotseat(Launcher launcher, View v, int rank) {
        Hotseat hs = launcher.getHotseat();
        if (v != null && rank < hs.getGridCount()) {
            int cellX = hs.getCellXFromOrder(rank);
            int cellY = hs.getCellYFromOrder(rank);
            if (v.getTag() instanceof ItemInfo) {
                CellLayout.LayoutParams lp = new CellLayout.LayoutParams(cellX, cellY, 1, 1);
                if (hs.getLayout().addViewToCellLayout(v, -1, v.getId(), lp, true)) {
                    ItemInfo item = (ItemInfo) v.getTag();
                    launcher.getModelWriter().modifyItemInDatabase(item, item.container,
                            item.screenId, cellX, cellY, 1, 1);
                    if (LogUtils.DEBUG_ALL) {
                        LogUtils.d(TAG, "update " + ((ItemInfo)v.getTag()).title + " to:" + rank);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canInsert(Launcher launcher) {
        int gridCount = launcher.getHotseat().getGridCount();
        return gridCount < launcher.getDeviceProfile().inv.numHotseatIcons;
    }
}