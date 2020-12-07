package com.sprd.ext.navigationbar;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

import com.android.launcher3.Launcher;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.util.SettingsObserver;
import com.sprd.ext.LauncherAppMonitor;
import com.sprd.ext.LauncherAppMonitorCallback;
import com.sprd.ext.LogUtils;
import com.sprd.ext.SystemPropertiesUtils;
import com.sprd.ext.UtilitiesExt;

/**
 * Created by SPRD on 2018/11/7.
 */
public class NavigationBarController {
    private static final String TAG = "NavigationBarController";

    private static final String NAVIGATIONBAR_CONFIG = "navigationbar_config";
    private static final String SHOW_NAVIGATIONBAR = "show_navigationbar";

    private final Context mContext;
    private final LauncherAppMonitor mMonitor;
    private final SettingsObserver mNavigationBarShowObserver;
    private boolean mIsDynamicNavigationBar;
    private boolean mIsNavigationBarShowing;

    private final LauncherAppMonitorCallback mAppMonitorCallback = new LauncherAppMonitorCallback() {
        @Override
        public void onLauncherStart() {
            if (mNavigationBarShowObserver != null && mIsDynamicNavigationBar) {
                mNavigationBarShowObserver.register(SHOW_NAVIGATIONBAR);
            }
        }

        @Override
        public void onLauncherStop() {
            if (mNavigationBarShowObserver != null) {
                mNavigationBarShowObserver.unregister();
            }
        }
    };

    public NavigationBarController(Context context, LauncherAppMonitor monitor) {
        mContext = context;
        mMonitor = monitor;
        mMonitor.registerCallback(mAppMonitorCallback);
        ContentResolver resolver = mContext.getContentResolver();
        mIsDynamicNavigationBar = dynamicNavigationBarEnable();
        mIsNavigationBarShowing = isNavigationBarShowing();
        SettingsObserver navigationBarCfgObserver = new SettingsObserver.System(resolver) {
            @Override
            public void onSettingChanged(boolean keySettingEnabled) {
                mIsDynamicNavigationBar = dynamicNavigationBarEnable();
                LogUtils.d( TAG, "onSettingChanged, dynamic:" + mIsDynamicNavigationBar );
            }
        };
        navigationBarCfgObserver.register(NAVIGATIONBAR_CONFIG);

        mNavigationBarShowObserver = new SettingsObserver.System(resolver) {
            @Override
            public void onSettingChanged(boolean keySettingEnabled) {
                boolean show = isNavigationBarShowing();
                if (mIsNavigationBarShowing != show) {
                    mIsNavigationBarShowing = show;
                    Launcher launcher = mMonitor.getLauncher();
                    if (launcher != null) {
                        LogUtils.d(TAG, "onSettingChanged, show:" + mIsNavigationBarShowing);
                        PopupContainerWithArrow popup = PopupContainerWithArrow.getOpen(launcher);
                        if (popup != null) {
                            popup.close(false);
                        }
                    }
                }
            }
        };
    }

    private boolean isNavigationBarShowing() {
        return (Settings.System.getInt(mContext.getContentResolver(),
                SHOW_NAVIGATIONBAR, 0) & 0x1) != 0;
    }

    private boolean dynamicNavigationBarEnable() {
        return (Settings.System.getInt(mContext.getContentResolver(),
                NAVIGATIONBAR_CONFIG, 0) & 0x10) != 0;
    }

    public static boolean hasNavigationBar() {
        String hardwareMainkeys = SystemPropertiesUtils.get("qemu.hw.mainkeys", "");
        if ("".equals(hardwareMainkeys)) {
            return UtilitiesExt.getSystemBooleanRes("config_showNavigationBar");
        }
        return "0".equals(hardwareMainkeys);
    }
}
