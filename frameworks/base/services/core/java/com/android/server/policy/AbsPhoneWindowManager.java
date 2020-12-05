package com.android.server.policy;

import android.database.ContentObserver;
import android.content.ContentResolver;
import android.view.KeyEvent;

public abstract class AbsPhoneWindowManager {
    /* SPRD: add for dynamic navigationbar @{ */
    boolean mDynamicNavigationBar = false;
    public static final String NAVIGATIONBAR_CONFIG = "navigationbar_config";
    public static final String SHOW_NAVIGATIONBAR = "show_navigationbar";
    public static boolean mNaviBooted = false;
    boolean mNaviHided = false;

    protected void sprdObserve(ContentResolver resolver, ContentObserver observer) {
    }

    void initNavStatus() {
    }

    void showNavigationBar(boolean show){
    }

    void showNavigationBar(boolean show, boolean updateSettings){
    }

    public boolean isNavigationBarShowing() {
        return true;
    }

    void registerNavIfNeeded() {
    }
    /* @} */

    public void setThreadPriorities(int dispatchDuration, int deliveryDuration) {
    }
    //SPRD:add for Quick Capture
    public void handleQuickCamera(KeyEvent event, boolean down) {
    }

    void updateQuickStepStatus() {
    }

    void registerSprdIntentFilter() {
    }

    boolean hasInPowerUtrlSavingMode() {
        return false;
    }
}