package com.sprd.ext.folder.grid;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.support.v4.graphics.ColorUtils;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.util.Themes;
import com.sprd.ext.LauncherAppMonitor;

public class GridPreviewBackground extends PreviewBackground {
    private int mFolderIconRadius;
    private int mPreviewPadding;

    private final PorterDuffXfermode mClipPorterDuffXfermode
            = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);

    private LinearGradient mOffsetXShadowShader = null;
    private LinearGradient mShadowShader = null;

    public GridPreviewBackground() {
        Launcher launcher = LauncherAppMonitor.getInstanceNoCreate().getLauncher();
        if (launcher != null) {
            Resources resources = launcher.getResources();
            mFolderIconRadius = resources.getDimensionPixelSize(R.dimen.grid_folder_icon_radius);
            mPreviewPadding = resources.getDimensionPixelSize(R.dimen.grid_folder_icon_preview_padding);
        }
    }

    @Override
    public void setup(Launcher launcher, View invalidateDelegate, int availableSpaceX, int topPadding) {
        mInvalidateDelegate = invalidateDelegate;
        mBgColor = Themes.getAttrColor(launcher, android.R.attr.colorPrimary);

        DeviceProfile grid = launcher.getDeviceProfile();
        previewSize = grid.folderIconSizePx - 2 * mPreviewPadding;

        basePreviewOffsetX = (availableSpaceX - previewSize) / 2;
        basePreviewOffsetY = topPadding + grid.folderIconOffsetYPx + mPreviewPadding;

        // Stroke width is 1dp
        mStrokeWidth = launcher.getResources().getDisplayMetrics().density;

        mOffsetXShadowShader = new LinearGradient(0, 0, getOffsetX(),0,
                new int[] {Color.TRANSPARENT, Color.TRANSPARENT, Color.BLACK },
                new float[] {0, 0.999f, 1},
                Shader.TileMode.CLAMP);

        mShadowShader = new LinearGradient(getOffsetX(), 0, getOffsetX() + previewSize,0,
                new int[] {Color.BLACK, Color.BLACK, Color.TRANSPARENT },
                new float[] {0, 0.999f, 1},
                Shader.TileMode.CLAMP);

        invalidate();
    }

    @Override
    public void drawBackground(Canvas canvas) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(getBgColor());
        drawRoundRect(canvas, 0/* edge */);
    }

    @Override
    public void drawBackgroundStroke(Canvas canvas) {
        mPaint.setColor(ColorUtils.setAlphaComponent(mBgColor, mStrokeAlpha));
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mStrokeWidth);
        drawRoundRect(canvas, 0/* edge */);
    }

    @Override
    public void drawLeaveBehind(Canvas canvas) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.argb(160, 245, 245, 245));
        drawRoundRect(canvas, 0/* edge */);
    }

    @Override
    public void drawShadow(Canvas canvas) {

    }

    @Override
    public int getOffsetX() {
        return basePreviewOffsetX;
    }

    @Override
    public int getOffsetY() {
        return basePreviewOffsetY;
    }

    @Override
    public Path getClipPath() {
        mPath.reset();
        mPath.addRoundRect(getOffsetX(), getOffsetY(),
                previewSize + getOffsetX(), previewSize + getOffsetY(),
                mFolderIconRadius, mFolderIconRadius, Path.Direction.CW);
        return mPath;
    }

    private void drawRoundRect(Canvas canvas, int edge) {
        canvas.drawRoundRect(getOffsetX() + edge, getOffsetY() + edge,
                previewSize + getOffsetX() - edge, previewSize + getOffsetY() - edge,
                mFolderIconRadius, mFolderIconRadius, mPaint);
    }

    @Override
    public void clipCanvasHardware(Canvas canvas) {
        mPaint.setColor(Color.BLACK);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setXfermode(mClipPorterDuffXfermode);

        mPaint.setShader(mOffsetXShadowShader);
        canvas.drawPaint(mPaint);

        mPaint.setShader(mShadowShader);
        canvas.drawPaint(mPaint);

        mPaint.setXfermode(null);
        mPaint.setShader(null);
    }
}
