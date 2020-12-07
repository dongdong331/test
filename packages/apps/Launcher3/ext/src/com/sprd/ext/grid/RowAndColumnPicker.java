package com.sprd.ext.grid;

import android.content.Context;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;

/**
 * Created on 9/4/18.
 */
public class RowAndColumnPicker extends FrameLayout {

    private int mRows;
    private int mColumns;
    private NumberPicker mRowPicker;
    private NumberPicker mColumnPicker;

    public RowAndColumnPicker(@NonNull Context context) {
        this(context, null);
    }

    public RowAndColumnPicker(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RowAndColumnPicker(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.row_and_column_picker, this, true);

        GridConfiguration gridConfig = LauncherAppState.getIDP(context).gridConfig;
        Point rowsRange = gridConfig.getRowsRange();
        mRowPicker = findViewById(R.id.row);
        mRowPicker.setMinValue(rowsRange.x);
        mRowPicker.setMaxValue(rowsRange.y);
        mRowPicker.setOnValueChangedListener((picker, oldVal, newVal) -> mRows = newVal);

        Point columnsRange = gridConfig.getColumnsRange();
        mColumnPicker = findViewById(R.id.column);
        mColumnPicker.setMinValue(columnsRange.x);
        mColumnPicker.setMaxValue(columnsRange.y);
        mColumnPicker.setOnValueChangedListener((picker, oldVal, newVal) -> mColumns = newVal);
    }

    void init(int row, int column) {
        mRows = row;
        mColumns = column;
        updateValues();
    }

    private void updateValues() {
        mRowPicker.setValue(mRows);
        mColumnPicker.setValue(mColumns);
    }

    int getRow() {
        return mRows;
    }

    int getColumn() {
        return mColumns;
    }
}
