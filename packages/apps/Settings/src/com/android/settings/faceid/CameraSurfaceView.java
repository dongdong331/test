package com.android.settings.faceid;

import android.content.Context;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

public class CameraSurfaceView extends TextureView {
    private static final String TAG = "CameraSurfaceView";
    private Context mContext;

    public CameraSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }
}
