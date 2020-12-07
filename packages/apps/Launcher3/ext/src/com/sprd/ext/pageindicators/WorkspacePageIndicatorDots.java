package com.sprd.ext.pageindicators;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.WallpaperColorInfo;

/**
 * A PageIndicator that briefly shows a fraction of a dots when moving between pages
 * <p>
 * The fraction is 1 / number of pages and the position is based on the progress of the page scroll.
 */
public class WorkspacePageIndicatorDots extends WorkspaceBasePageIndicator {
    private static final String TAG = "WorkspacePageIndicatorDots";

    private final Paint mCirclePaint;
    private float mDotRadius;
    private final float MAX_DOTRADIUS;
    private final int mActiveColor;
    private int mActiveAlpha = (int) (1f * 255);
    private int mInActiveAlpha = (int) (0.50f * 255);
    public static final int IN_WHITE_ALPHA = (int) (0.40f * 255);
    public static final int IN_BLACK_ALPHA = (int) (0.35f * 255);
    private final boolean mIsRtl;

    private int mNumPages;
    private int mActivePage;

    public WorkspacePageIndicatorDots(Context context) {
        this(context, null);
    }

    public WorkspacePageIndicatorDots(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkspacePageIndicatorDots(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCirclePaint.setStyle(Paint.Style.FILL);
        MAX_DOTRADIUS = getResources().getDimension(R.dimen.page_indicator_dot_size) / 2;

        boolean darkText = WallpaperColorInfo.getInstance(context).supportsDarkText();
        mActiveColor = darkText ? Color.BLACK : Color.WHITE;
        mInActiveAlpha = darkText ? IN_BLACK_ALPHA : IN_WHITE_ALPHA;
        mIsRtl = Utilities.isRtl(getResources());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int availableWidth = getWidth();
        //Recalculate dot radius size
        mDotRadius = Math.min(MAX_DOTRADIUS, availableWidth/(3*mNumPages - 1));

        float circleGap = 3 * mDotRadius;
        // Draw all page indicators;
        //(availableWidth - mNumPages * (2*mDotRadius) - (mNumPages - 1)*mDotRadius)
        float startX = (availableWidth - mNumPages * circleGap + mDotRadius) / 2;
        float x = startX + mDotRadius;
        float y = canvas.getHeight() / 2;

        if (mIsRtl) {
            x = availableWidth - x;
            circleGap = -circleGap;
        }
        mCirclePaint.setColor(mActiveColor);
        for (int i = 0; i < mNumPages; i++) {
            mCirclePaint.setAlpha((i == mActivePage ? mActiveAlpha : mInActiveAlpha));
            canvas.drawCircle(x, y, mDotRadius, mCirclePaint);
            x += circleGap;
        }
    }

    @Override
    public void setScroll(int currentScroll, int totalScroll) {
        if (getAlpha() == 0) {
            return;
        }
        invalidate();
    }

    @Override
    public void setActiveMarker(int activePage) {
        if (mActivePage != activePage) {
            mActivePage = activePage;
        }
    }

    @Override
    public void setMarkersCount(int numMarkers) {
        mNumPages = numMarkers;
        requestLayout();
    }

}
