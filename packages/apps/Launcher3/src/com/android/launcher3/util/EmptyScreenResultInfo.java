package com.android.launcher3.util;

/**
 * Created on 8/2/18.
 */
public class EmptyScreenResultInfo {
    public final boolean animate;
    public final Runnable onComplete;
    public final int delay;
    public final boolean stripEmptyScreens;

    public EmptyScreenResultInfo(final boolean animate, final Runnable onComplete,
                                 final int delay, final boolean stripEmptyScreens) {
        this.animate = animate;
        this.onComplete = onComplete;
        this.delay = delay;
        this.stripEmptyScreens = stripEmptyScreens;
    }
}
