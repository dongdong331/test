package com.sprd.ext.grid;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.util.LooperExecutor;
import com.sprd.ext.RestartHomeApp;

/**
 * Created on 9/4/18.
 */
public class RowAndColumnPickerDialog extends AlertDialog implements DialogInterface.OnClickListener {

    private static final String ROWS = "rows";
    private static final String COLUMNS = "columns";

    private RowAndColumnPicker mPicker;

    RowAndColumnPickerDialog(Context context, int row, int column) {
        this(context, 0, row, column);
    }

    private RowAndColumnPickerDialog(Context context, int themeResId, int row, int column) {
        super(context, themeResId);
        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view =  inflater.inflate(R.layout.column_and_row_picker_dialog, null);
        setView(view);
        mPicker = view.findViewById(R.id.row_and_column_picker);
        mPicker.init(row, column);

        setButton(BUTTON_POSITIVE, context.getString(android.R.string.ok), this);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (BUTTON_POSITIVE == which) {
            final int row = mPicker.getRow();
            final int column = mPicker.getColumn();
            Context context = getContext();
            final GridConfiguration girdConfig = LauncherAppState.getIDP(context).gridConfig;
            int[] gridSize = girdConfig.getGridSize();
            if (gridSize[0] != row || gridSize[1] != column) {
                // Value has changed
                ProgressDialog.show(context,
                        null /* title */,
                        context.getString(R.string.desktop_grid_override_progress),
                        true /* indeterminate */,
                        false /* cancelable */);
                new LooperExecutor(LauncherModel.getWorkerLooper()).execute(
                        new RestartHomeApp(context) {
                            @Override
                            protected void saveNewValue() {
                                girdConfig.saveGridSize(context, row, column);
                            }
                        });
            }
        }
    }

    @NonNull
    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putInt(ROWS, mPicker.getRow());
        state.putInt(COLUMNS, mPicker.getColumn());
        return state;
    }

    @Override
    public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int row = savedInstanceState.getInt(ROWS);
        int column = savedInstanceState.getInt(COLUMNS);
        mPicker.init(row, column);
    }
}
