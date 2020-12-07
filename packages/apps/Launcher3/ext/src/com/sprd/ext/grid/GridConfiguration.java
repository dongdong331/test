package com.sprd.ext.grid;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;

import com.android.launcher3.R;
import com.sprd.ext.LogUtils;
import com.sprd.ext.UtilitiesExt;

/**
 * Created on 9/25/18.
 */
public class GridConfiguration {
    private static final String TAG = "GridConfiguration";

    private Point mRowsRange;
    private Point mColumnsRange;

    private int mRows;
    private int mColumns;

    private int[] mTmpGrid = new int[2];

    public GridConfiguration(Context context, int numRows, int numColumns, int numHotseatIcons) {
        Resources res = context.getResources();
        int minRows = res.getInteger(R.integer.min_rows_in_desktop_grid);
        int maxRows = res.getInteger(R.integer.max_rows_in_desktop_grid);
        mRowsRange = new Point(minRows, maxRows);

        int minColumns = res.getInteger(R.integer.min_columns_in_desktop_grid);
        int maxColumns = res.getInteger(R.integer.max_columns_in_desktop_grid);
        maxColumns = maxColumns > 0 ? maxColumns : numHotseatIcons;
        mColumnsRange = new Point(minColumns, maxColumns);

        int defRows = res.getInteger(R.integer.default_rows_in_desktop_grid);
        int defColumns = res.getInteger(R.integer.default_columns_in_desktop_grid);

        int rows = defRows > 0 ? defRows : numRows;
        int columns = defColumns > 0 ? defColumns : numColumns;
        initGridSize(context, rows, columns);

        verifyGridSize(mRows, mRowsRange, mColumns, mColumnsRange);
        mRows = mTmpGrid[0];
        mColumns = mTmpGrid[1];

        LogUtils.d(TAG, "GridConfiguration:" + this);
    }

    private int[] verifyGridSize(int rows, Point rowRange, int columns, Point columnRange) {
        mTmpGrid[0] = UtilitiesExt.clamp(rows, rowRange.x, rowRange.y);
        mTmpGrid[1] = UtilitiesExt.clamp(columns, columnRange.x, columnRange.y);
        return mTmpGrid;
    }

    private void initGridSize(Context context, int rows, int columns) {
        mRows = rows;
        mColumns = columns;
        String savedGrid = DesktopGridController.getGridSize(context);
        if (isGridSizeValid(savedGrid)) {
            // The saved grid size is valid, so make the desktop grid is same with it.
            if (!savedGrid.equals(UtilitiesExt.getPointString(mRows, mColumns))) {
                Point grid = UtilitiesExt.parsePoint(savedGrid);
                if (mRows != grid.x) {
                    mRows = grid.x;
                }
                if (mColumns != grid.y) {
                    mColumns = grid.y;
                }
            }
        } else {
            DesktopGridController.saveGridSize(context, mRows, mColumns);
        }
    }

    private boolean isGridSizeValid(String gridSize) {
        if (gridSize.isEmpty()) {
            return false;
        }

        Point grid = UtilitiesExt.parsePoint(gridSize);
        return grid.x >= mRowsRange.x && grid.x <= mRowsRange.y
                && grid.y >= mColumnsRange.x && grid.y <= mColumnsRange.y;
    }

    public int[] getGridSize() {
        mTmpGrid[0] = mRows;
        mTmpGrid[1] = mColumns;
        return mTmpGrid;
    }

    void saveGridSize(Context context, int row, int column) {
        if (mRows != row || mColumns != column) {
            mRows = row;
            mColumns = column;
            DesktopGridController.saveGridSize(context, row, column);
        }
    }

    Point getRowsRange() {
        return mRowsRange;
    }

    Point getColumnsRange() {
        return mColumnsRange;
    }

    @Override
    public String toString() {
        return "mRows=" + mRows
                + " mColumns=" + mColumns
                + " mRowsRange=" + mRowsRange
                + " mColumnsRange=" + mColumnsRange;
    }
}
