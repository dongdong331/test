
package com.android.keyguard;

import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardSecurityCallback;
import com.android.keyguard.KeyguardSecurityView;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;
/**
 * Displays the init UI
 */
public class KeyguardSubsidyLockInitView extends LinearLayout implements KeyguardSecurityView {
    private static final boolean DEBUG = true;
    public static final String LOG_TAG = "KeyguardSubsidyInitView";
    protected KeyguardSecurityCallback mCallback;

    KeyguardUpdateMonitorCallback mUpdateMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onSubsidyDeviceLock(int mode) {
            Log.d(LOG_TAG, "onSubsidyDeviceLock");
            if (mode != KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_INIT){
                dismissView();
                return;
            }
        };
    };

    public KeyguardSubsidyLockInitView(Context context) {
        this(context, null);
    }

    public KeyguardSubsidyLockInitView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void dismissView() {
        Log.d(LOG_TAG, "dismissView");
        mCallback.dismiss(true, KeyguardUpdateMonitor.getCurrentUser());
    }

    @Override
    protected void onAttachedToWindow() {
        Log.d(LOG_TAG, "onAttachedToWindow");
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mUpdateMonitorCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        Log.d(LOG_TAG, "onDetachedFromWindow");
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mUpdateMonitorCallback);
    }

    @Override
    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        mCallback = callback;
    }

    @Override
    public KeyguardSecurityCallback getCallback() {
        return mCallback;
    }

    @Override
    public void showUsabilityHint() {
    }

    @Override
    public void onPause() {
    }

    @Override
    public boolean needsInput() {
        return false;
    }

    @Override
    public void showMessage(CharSequence message, int color) {
    }

    @Override
    public void setLockPatternUtils(LockPatternUtils utils) {
    }

    @Override
    public void startAppearAnimation() {
        // noop.
    }

    @Override
    public boolean startDisappearAnimation(Runnable finishRunnable) {
        return false;
    }

    @Override
    public void onResume(int reason) {
    }

    @Override
    public void showPromptReason(int reason) {
    }

    @Override
    public void reset() {
    }

    @Override
    public CharSequence getTitle() {
        return getContext().getString(
                com.android.internal.R.string.keyguard_accessibility_sim_pin_unlock);
   }
}

