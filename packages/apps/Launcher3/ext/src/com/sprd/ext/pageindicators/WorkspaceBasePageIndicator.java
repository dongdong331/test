package com.sprd.ext.pageindicators;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.pageindicators.PageIndicator;

/**
 * Workspace Base PageIndicator
 */
public abstract class WorkspaceBasePageIndicator extends View implements Insettable, PageIndicator {

    protected final Launcher mLauncher;

    protected boolean mShouldAutoHide = true;

    public WorkspaceBasePageIndicator(Context context) {
        this(context, null);
    }

    public WorkspaceBasePageIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkspaceBasePageIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(context);
    }

    @Override
    protected abstract void onDraw(Canvas canvas);

    @Override
    public abstract void setScroll(int currentScroll, int totalScroll);

    @Override
    public abstract void setActiveMarker(int activePage);

    @Override
    public abstract void setMarkersCount(int numMarkers);

    public void pauseAnimations() {
    }

    public void skipAnimationsToEnd() {
    }

    public void setShouldAutoHide(boolean shouldAutoHide) {
        mShouldAutoHide = shouldAutoHide;
    }

    @Override
    public void setInsets(Rect insets) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();

        if (grid.isVerticalBarLayout()) {
            Rect padding = grid.workspacePadding;
            lp.leftMargin = padding.left + grid.workspaceCellPaddingXPx;
            lp.rightMargin = padding.right + grid.workspaceCellPaddingXPx;
            lp.bottomMargin = padding.bottom;
        } else {
            lp.leftMargin = lp.rightMargin = 0;
            lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            lp.bottomMargin = grid.hotseatBarSizePx + insets.bottom;
        }
        setLayoutParams(lp);
    }
}
