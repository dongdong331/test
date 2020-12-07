package com.android.documentsui;

import android.content.Context;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.widget.Toast;

public class LengthFilterToast implements  InputFilter{
    private final int mMax;
    private final Context mContext;
    private Toast mToast = null;

    public LengthFilterToast(Context context,  int max) {
        mContext = context;
        mMax = max;
    }

    private void showMsg() {
        if(mToast != null){
            mToast.cancel();
            mToast = null;
        }
        mToast = Toast.makeText(mContext,R.string.length_limited , Toast.LENGTH_SHORT);
        mToast.show();
    }

    public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
            int dstart, int dend) {
        int keep = mMax - (dest.length() - (dend - dstart));
        if (keep <= 0) {
            showMsg();
            return "";
        } else if (keep >= end - start) {
            return null; // keep original
        } else {
            keep += start;
            if (Character.isHighSurrogate(source.charAt(keep - 1))) {
                --keep;
                if (keep == start) {
                    return "";
                }
            }
            return source.subSequence(start, keep);
        }
    }

    /**
    * @return the maximum length enforced by this input filter
    */
    public int getMax() {
        return mMax;
    }
}

