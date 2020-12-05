package com.android.server.wm;

import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;
import android.util.Slog;

public class WMSLog {

    public void dumpWindowStatusLocked(AppWindowToken wtoken) {
        wtoken.forAllWindows((w) -> {
            final WindowStateAnimator winAnimator = w.mWinAnimator;
            Slog.i(TAG_WM, "Dump window# " + w + "  Status#" + "isFocused: " + w.isFocused()
                    + " hasDrawnLw: " + w.hasDrawnLw() + " canReceiveKeys: " + w.canReceiveKeys()
                    + " isDrawnLw: " + w.isDrawnLw() + " isDrawFinishedLw: " + w.isDrawFinishedLw()
                    + " isAnimatingLw: " + w.isAnimatingLw()
                    + " isDisplayedLw: " + w.isDisplayedLw()
                    + " isReadyForDisplay: " + w.isReadyForDisplay()
                    + " isOnScreen: " + w.isOnScreen()
                    + " isVisibleOrAdding: " + w.isVisibleOrAdding()
                    + " isVisibleNow: " + w.isVisibleNow()
                    + " isWinVisibleLw: " + w.isWinVisibleLw()
                    + " isVisibleLw: " + w.isVisibleLw()
                    + " isLetterboxedAppWindow: " + w.isLetterboxedAppWindow()
                    + " needsZBoost: " + w.needsZBoost()
                    + " shouldMagnify: " + w.shouldMagnify());
            Slog.i(TAG_WM, "Dump WindowStateAnimator# "  + "  Status# " +
                    " hasDrawnLw: " + winAnimator.isAnimationSet());
        }, true /* traverseTopToBottom */);
    }

    public void dumpAllWindowStatusLocked(WindowManagerService service) {
        final int numDisp = service.mRoot.mChildren.size();
        for (int displayNdx = 0; displayNdx < numDisp; ++displayNdx) {
            final DisplayContent displayContent = service.mRoot.mChildren.get(displayNdx);
            Slog.i(TAG_WM, "Dump Display#" + displayNdx + " windows:");
            final int[] index = new int[1];
            displayContent.forAllWindows((win) -> {
                Slog.i( TAG_WM, "Dump Win No." + index[0]
                        + " win= " + win
                        + ", flags=0x" + Integer.toHexString(win.mAttrs.flags)
                        + ", canReceive=" + win.canReceiveKeys()
                        + ", ViewVisibility=" + win.mViewVisibility
                        + ", isVisibleOrAdding=" + win.isVisibleOrAdding()
                        + ", DrawState=" + win.mWinAnimator.mDrawState);
                if(win.mAppToken != null){
                    Slog.i( TAG_WM, " win.mAppToken=" +  win.mAppToken
                            + ", mAppToken.removed=" + win.mAppToken.removed
                            + ", mAppToken.sendingToBottom=" + win.mAppToken.sendingToBottom
                            + ", isSelfAnimating()=" + win.mAppToken.isSelfAnimating()
                            + ", isWaitingForTransitionStart()=" + win.mAppToken.isWaitingForTransitionStart());
                }

                if (!win.isVisibleOrAdding()) {
                    String s;
                    if (win.mAppToken != null) {
                        s = " hiddenRequested=" + win.mAppToken.hiddenRequested;
                    } else {
                        s = " appToken=null";
                    }
                    Slog.i(TAG_WM, "    mSurfaceController=" + win.mWinAnimator.mSurfaceController
                            + " relayoutCalled=" + win.mRelayoutCalled
                            + " viewVis=" + win.mViewVisibility
                            + " policyVis=" + win.mPolicyVisibility
                            + " policyVisAfterAnim=" + win.mPolicyVisibilityAfterAnim
                            + " attachHid=" + win.isParentWindowHidden()
                            + " exiting=" + win.mAnimatingExit
                            + " destroying=" + win.mDestroying + s);
                }
                index[0] = index[0] + 1;
            }, true /* traverseTopToBottom */);
        }
    }
}
