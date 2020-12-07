package com.android.incallui.sprd.settings.callrecording;

import android.content.Context;
import android.preference.ListPreference;
import com.android.dialer.R;
import android.util.AttributeSet;

/**
 * This class is used to display chooser for record type
 */

public class RecordListFrom extends ListPreference {

    private Context context;
    public static final int RECORD_ALL_NUMBERS = 0;
    public static final int RECORD_LISTED_NUMBERS = 1;
    private int selected = 0;

    public RecordListFrom(Context context) {
        super(context);
        prepare();
    }

    public RecordListFrom(Context context , AttributeSet attrs) {
        super(context, attrs);
        prepare();
    }

    private void prepare() {
        context = getContext();
        setEntries(new String[]{
                context.getString(R.string.all_numbers),
                context.getString(R.string.listed_numbers),
        });
        setEntryValues(new String[]{
                String.valueOf(RECORD_ALL_NUMBERS),
                String.valueOf(RECORD_LISTED_NUMBERS),
        });
    }

    @Override
    protected boolean shouldPersist() {
        return false;   // This preference takes care of its own storage
    }

    @Override
    public CharSequence getSummary() {
        switch (selected) {
            case RECORD_ALL_NUMBERS:
                return context.getString(R.string.all_numbers);
            case RECORD_LISTED_NUMBERS:
                return context.getString(R.string.listed_numbers);
        }
        return null;
    }

    @Override
    protected boolean persistString(String value) {
        int newValue = Integer.parseInt(value);
        if (newValue != selected) {
            notifyChanged();
        }
        return true;
    }

    public void setSelected(int selected){
        this.selected = selected;
    }
}
