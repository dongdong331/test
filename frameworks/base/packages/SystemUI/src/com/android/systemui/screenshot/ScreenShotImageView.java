
package com.android.systemui.screenshot;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.ImageView;

public class ScreenShotImageView extends ImageView {
    private boolean mEnabledHwBitmapsInSwMode;

    public ScreenShotImageView(Context context) {
        super(context);
    }

    public ScreenShotImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setHwBitmapsInSwModeEnabled(boolean enable) {
        mEnabledHwBitmapsInSwMode = enable;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.setHwBitmapsInSwModeEnabled(mEnabledHwBitmapsInSwMode);
        try {
            super.onDraw(canvas);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
