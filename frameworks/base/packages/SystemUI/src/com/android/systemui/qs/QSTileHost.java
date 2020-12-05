/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.RemoteException;
import android.os.sprdpower.IPowerManagerEx;
import android.os.sprdpower.PowerManagerEx;
import android.os.ServiceManager;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.service.quicksettings.Tile;
import android.text.TextUtils;
import android.util.Log;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.plugins.PluginManager;
import com.android.systemui.plugins.qs.QSFactory;
import com.android.systemui.plugins.qs.QSTileView;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.qs.external.CustomTile;
import com.android.systemui.qs.external.TileLifecycleManager;
import com.android.systemui.qs.external.TileServices;
import com.android.systemui.qs.tileimpl.QSFactoryImpl;
import com.android.systemui.statusbar.phone.AutoTileManager;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;
import com.sprd.systemui.qs.tiles.LongScreenshotTile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import android.os.SystemProperties;

/** Platform implementation of the quick settings tile host **/
public class QSTileHost implements QSHost, Tunable, PluginListener<QSFactory> {
    private static final String TAG = "QSTileHost";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final String TILES_SETTING = Secure.QS_TILES;

    private final Context mContext;
    private final StatusBar mStatusBar;
    private final LinkedHashMap<String, QSTile> mTiles = new LinkedHashMap<>();
    private final BatteryController mBattery;
    protected final ArrayList<String> mTileSpecs = new ArrayList<>();
    private final TileServices mServices;

    private final List<Callback> mCallbacks = new ArrayList<>();
    private final AutoTileManager mAutoTiles;
    private final StatusBarIconController mIconController;
    private final ArrayList<QSFactory> mQsFactories = new ArrayList<>();
    private int mCurrentUser;
    private boolean isEnableWifiDisplay = false;
    private String deleted_tile_in_ultramode = null;
    private String added_tile_in_ultramode = null;
    //NOTE: Support the cutting function of WCN(WLAN,BT,GPS) BEG -->
    private static final boolean WCN_DISABLED = SystemProperties.get("ro.wcn").equals("disabled");
    //<-- Support the cutting function of WCN(WLAN,BT,GPS) END

    private static final boolean LOCATION_DISABLED = SystemProperties.get("ro.location").equals("disabled");
    private static final String TITLE_SAVE = "saver";

    public QSTileHost(Context context, StatusBar statusBar,
            StatusBarIconController iconController) {
        mIconController = iconController;
        mContext = context;
        mStatusBar = statusBar;
        mBattery = Dependency.get(BatteryController.class);
        isEnableWifiDisplay = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enableWifiDisplay);

        mServices = new TileServices(this, Dependency.get(Dependency.BG_LOOPER));

        mQsFactories.add(new QSFactoryImpl(this));
        Dependency.get(PluginManager.class).addPluginListener(this, QSFactory.class, true);

        Dependency.get(TunerService.class).addTunable(this, TILES_SETTING);
        // AutoTileManager can modify mTiles so make sure mTiles has already been initialized.
        mAutoTiles = new AutoTileManager(context, this);
    }

    public StatusBarIconController getIconController() {
        return mIconController;
    }

    public void destroy() {
        mTiles.values().forEach(tile -> tile.destroy());
        mAutoTiles.destroy();
        Dependency.get(TunerService.class).removeTunable(this);
        mServices.destroy();
        Dependency.get(PluginManager.class).removePluginListener(this);
    }

    @Override
    public void onPluginConnected(QSFactory plugin, Context pluginContext) {
        // Give plugins priority over creation so they can override if they wish.
        mQsFactories.add(0, plugin);
        String value = Dependency.get(TunerService.class).getValue(TILES_SETTING);
        // Force remove and recreate of all tiles.
        onTuningChanged(TILES_SETTING, "");
        onTuningChanged(TILES_SETTING, value);
    }

    @Override
    public void onPluginDisconnected(QSFactory plugin) {
        mQsFactories.remove(plugin);
        // Force remove and recreate of all tiles.
        String value = Dependency.get(TunerService.class).getValue(TILES_SETTING);
        onTuningChanged(TILES_SETTING, "");
        onTuningChanged(TILES_SETTING, value);
    }

    @Override
    public void addCallback(Callback callback) {
        mCallbacks.add(callback);
    }

    @Override
    public void removeCallback(Callback callback) {
        mCallbacks.remove(callback);
    }

    @Override
    public Collection<QSTile> getTiles() {
        return mTiles.values();
    }

    @Override
    public void warn(String message, Throwable t) {
        // already logged
    }

    @Override
    public void collapsePanels() {
        mStatusBar.postAnimateCollapsePanels();
    }

    @Override
    public void forceCollapsePanels() {
        mStatusBar.postAnimateForceCollapsePanels();
    }

    @Override
    public void openPanels() {
        mStatusBar.postAnimateOpenPanels();
    }

    @Override
    public void collapsePanelsForPowerSave() {
        mStatusBar.postAnimateCollapsePanelsForPowerSave();
    }

    /* SPRD bug 889452 click battery icon twice is not effect in statusbar @{ */
    @Override
    public boolean isQsExpanded() {
        return mStatusBar.getPanel().isQsExpanded();
    }
    /* @} */

    @Override
    public Context getContext() {
        return mContext;
    }


    public TileServices getTileServices() {
        return mServices;
    }

    public int indexOf(String spec) {
        return mTileSpecs.indexOf(spec);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (!TILES_SETTING.equals(key)) {
            return;
        }
        if (DEBUG) Log.d(TAG, "Recreating tiles");
        if (newValue == null && UserManager.isDeviceInDemoMode(mContext)) {
            newValue = mContext.getResources().getString(R.string.quick_settings_tiles_retail_mode);
        }
        final List<String> tileSpecs = loadTileSpecs(mContext, newValue,false);
        int currentUser = ActivityManager.getCurrentUser();
        if (tileSpecs.equals(mTileSpecs) && currentUser == mCurrentUser) return;
        mTiles.entrySet().stream().filter(tile -> !tileSpecs.contains(tile.getKey())).forEach(
                tile -> {
                    if (DEBUG) Log.d(TAG, "Destroying tile: " + tile.getKey());
                    tile.getValue().destroy();
                });
        final LinkedHashMap<String, QSTile> newTiles = new LinkedHashMap<>();
        for (String tileSpec : tileSpecs) {
            QSTile tile = mTiles.get(tileSpec);
            if (tile != null && (!(tile instanceof CustomTile)
                    || ((CustomTile) tile).getUser() == currentUser)) {
                if (tile.isAvailable()) {
                    if (DEBUG) Log.d(TAG, "Adding " + tile);
                    tile.removeCallbacks();
                    if (!(tile instanceof CustomTile) && mCurrentUser != currentUser) {
                        tile.userSwitch(currentUser);
                    }
                    newTiles.put(tileSpec, tile);
                } else {
                    tile.destroy();
                }
            } else {
                if (DEBUG) Log.d(TAG, "Creating tile: " + tileSpec);
                try {
                    tile = createTile(tileSpec);
                    if (tile != null) {
                        if (tile.isAvailable()) {
                            tile.setTileSpec(tileSpec);
                            newTiles.put(tileSpec, tile);
                        } else {
                            tile.destroy();
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Error creating tile for spec: " + tileSpec, t);
                }
            }
        }
        mCurrentUser = currentUser;
        mTileSpecs.clear();
        mTileSpecs.addAll(tileSpecs);
        mTiles.clear();
        mTiles.putAll(newTiles);
        for (int i = 0; i < mCallbacks.size(); i++) {
            mCallbacks.get(i).onTilesChanged();
        }
    }

    @Override
    public void removeTile(String tileSpec) {
        boolean is_special_request = false;
        if (DEBUG) Log.d(TAG,"removeTile tileSpec =" + tileSpec);
        if ((StatusBar.SUPPORT_SUPER_POWER_SAVE) && (mStatusBar.getPowerSaveModeInternal() == PowerManagerEx.MODE_ULTRASAVING)){
            String setting = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                TILES_SETTING, ActivityManager.getCurrentUser());
            is_special_request = true;
            if (DEBUG) Log.d(TAG,"removeTile in ultra mode,setting =" + setting);
            final List<String> specs = loadTileSpecs(mContext, setting, is_special_request);
            specs.remove(tileSpec);
            if (DEBUG) Log.d(TAG,"removeTile in ultra mode ,specs =" + specs);
            Settings.Secure.putStringForUser(mContext.getContentResolver(), TILES_SETTING,
                TextUtils.join(",", specs), ActivityManager.getCurrentUser());
        } else {
            ArrayList<String> specs = new ArrayList<>(mTileSpecs);
            specs.remove(tileSpec);
            if (DEBUG) Log.d(TAG,"removeTile,specs =" + specs);
            Settings.Secure.putStringForUser(mContext.getContentResolver(), TILES_SETTING,
                TextUtils.join(",", specs), ActivityManager.getCurrentUser());
        }
    }

    public void addTile(String spec) {
        boolean is_special_request = false;
        String setting = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                TILES_SETTING, ActivityManager.getCurrentUser());
        if (DEBUG) Log.d(TAG,"addTile spec =" + spec);
        if ((StatusBar.SUPPORT_SUPER_POWER_SAVE) && (mStatusBar.getPowerSaveModeInternal() == PowerManagerEx.MODE_ULTRASAVING)){
            is_special_request = true;
        }
        if (DEBUG) Log.d(TAG,"addTile, setting =" + setting);
        final List<String> tileSpecs = loadTileSpecs(mContext, setting, is_special_request);
        if (tileSpecs.contains(spec)) {
            return;
        }
        tileSpecs.add(spec);
        if (DEBUG) Log.d(TAG,"addTile tileSpecs =" + tileSpecs);
        Settings.Secure.putStringForUser(mContext.getContentResolver(), TILES_SETTING,
                TextUtils.join(",", tileSpecs), ActivityManager.getCurrentUser());
    }

    public void addTile(ComponentName tile) {
        List<String> newSpecs = new ArrayList<>(mTileSpecs);
        if (DEBUG) Log.d(TAG,"addTile tile =" + tile);
        newSpecs.add(0, CustomTile.toSpec(tile));
        added_tile_in_ultramode = CustomTile.toSpec(tile);
        changeTiles(mTileSpecs, newSpecs);
        added_tile_in_ultramode = null;
    }

    public void removeTile(ComponentName tile) {
        List<String> newSpecs = new ArrayList<>(mTileSpecs);
        if (DEBUG) Log.d(TAG,"removeTile tile =" + tile);
        newSpecs.remove(CustomTile.toSpec(tile));
        deleted_tile_in_ultramode = CustomTile.toSpec(tile);
        changeTiles(mTileSpecs, newSpecs);
        deleted_tile_in_ultramode = null;
    }

    public void changeTiles(List<String> previousTiles, List<String> newTiles) {
        final int NP = previousTiles.size();
        final int NA = newTiles.size();

        for (int i = 0; i < NP; i++) {
            String tileSpec = previousTiles.get(i);
            if (!tileSpec.startsWith(CustomTile.PREFIX)) continue;
            if (!newTiles.contains(tileSpec)) {
                ComponentName component = CustomTile.getComponentFromSpec(tileSpec);
                Intent intent = new Intent().setComponent(component);
                TileLifecycleManager lifecycleManager = new TileLifecycleManager(new Handler(),
                        mContext, mServices, new Tile(), intent,
                        new UserHandle(ActivityManager.getCurrentUser()));
                lifecycleManager.onStopListening();
                lifecycleManager.onTileRemoved();
                TileLifecycleManager.setTileAdded(mContext, component, false);
                lifecycleManager.flushMessagesAndUnbind();
            }
        }

        if (DEBUG) Log.d(TAG, "saveCurrentTiles " + newTiles + " previousTiles " + previousTiles);

        /* UNISOC: Bug 960709 modify for tile editting in powersave mode*/
        if ((StatusBar.SUPPORT_SUPER_POWER_SAVE) && (mStatusBar.getPowerSaveModeInternal() == PowerManagerEx.MODE_ULTRASAVING)){
            final String setting = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                    TILES_SETTING, ActivityManager.getCurrentUser());
            if (DEBUG) Log.d(TAG,"changeTiles setting =" + setting);
            final List<String> oldTiles = loadTileSpecs(mContext,setting,true);
            if (DEBUG) Log.d(TAG,"changeTiles oldTiles =" + oldTiles);
            if (DEBUG) Log.d(TAG,"added_tile_in_ultramode =" + added_tile_in_ultramode +" deleted_tile_in_ultramode=" + deleted_tile_in_ultramode);
            final ArrayList<String> tiles = new ArrayList<String>();
            if (!TextUtils.isEmpty(added_tile_in_ultramode)) {
                tiles.add(added_tile_in_ultramode);
            }
            for (int i = 0; i < oldTiles.size(); i++) {
                String tileSpec = oldTiles.get(i);
                if (DEBUG) Log.d(TAG,"changeTiles add oldTiles:" +tileSpec);
                if (!TextUtils.isEmpty(tileSpec)) {
                    tiles.add(tileSpec);
                }
            }
            if (!TextUtils.isEmpty(deleted_tile_in_ultramode)) {
                tiles.remove(deleted_tile_in_ultramode);
            }

            if (DEBUG) Log.d(TAG, "saveCurrentTiles in powersave,tiles =" + tiles);
            Secure.putStringForUser(getContext().getContentResolver(), QSTileHost.TILES_SETTING,
                TextUtils.join(",", tiles), ActivityManager.getCurrentUser());
        } else {
            Secure.putStringForUser(getContext().getContentResolver(), QSTileHost.TILES_SETTING,
                TextUtils.join(",", newTiles), ActivityManager.getCurrentUser());
        }
    }

    public QSTile createTile(String tileSpec) {
        if (tileSpec == null) {
            Log.e(TAG, "tileSpec == null !!!");
            return null;
        }
        /* SPRD: Bug 932549,remove saver label under guest mode */
        if (TITLE_SAVE.equalsIgnoreCase(tileSpec) && (ActivityManager.getCurrentUser() != UserHandle.USER_OWNER)) {
             Log.i(TAG, "no saver in guset mode");
             return null;
        }
        if (WCN_DISABLED) {
            if (tileSpec.equals("wifi") || tileSpec.equals("bt") || tileSpec.equals("cast") || tileSpec.equals("hotspot")) {
                return null;
            }
        }
        if (LOCATION_DISABLED && tileSpec.equals("location")) return null;
        if (!isEnableWifiDisplay && tileSpec.equals("cast")) return null;
        for (int i = 0; i < mQsFactories.size(); i++) {
            QSTile t = mQsFactories.get(i).createTile(tileSpec);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    public QSTileView createTileView(QSTile tile, boolean collapsedView) {
        for (int i = 0; i < mQsFactories.size(); i++) {
            QSTileView view = mQsFactories.get(i).createTileView(tile, collapsedView);
            if (view != null) {
                return view;
            }
        }
        throw new RuntimeException("Default factory didn't create view for " + tile.getTileSpec());
    }

    protected List<String> loadTileSpecs(Context context, String tileList ,boolean is_special_request) {
        final Resources res = context.getResources();
        String defaultTileList = res.getString(R.string.quick_settings_tiles_default);
        if (tileList == null) {
            tileList = res.getString(R.string.quick_settings_tiles);
            if (DEBUG) Log.d(TAG, "Loaded tile specs from config: " + tileList);
        } else {
            if (DEBUG) Log.d(TAG, "Loaded tile specs from setting: " + tileList);
        }

        /*SPRD bug 885650:Super power feature*/
        if (!is_special_request && (mStatusBar.SUPPORT_SUPER_POWER_SAVE && mBattery != null)) {
            int mode = mBattery.getPowerSaveModeInternal();
            Log.d(TAG, "loadTileSpecs mode="+mode);
            if(mode == PowerManagerEx.MODE_ULTRASAVING){
                tileList = "wifi,cell,battery";
            }
        }
        /*@}*/

        if (!isEnableWifiDisplay) {
            tileList = tileList.replaceAll( ",cast|cast,|cast","");
            defaultTileList = defaultTileList.replaceAll( ",cast|cast,|cast","");
        }
        /* SPRD: Bug 895419, 780848,remove battery quick setting label under guest mode */
        if (ActivityManager.getCurrentUser() != UserHandle.USER_OWNER) {
            tileList = tileList.replaceAll("battery","");
            defaultTileList = defaultTileList.replaceAll("battery","");
        }
        /* @} */

        final ArrayList<String> tiles = new ArrayList<String>();
        boolean addedDefault = false;
        for (String tile : tileList.split(",")) {
            tile = tile.trim();
            if (tile.isEmpty()) continue;
            if (tile.equals("default")) {
                if (!addedDefault) {
                    tiles.addAll(Arrays.asList(defaultTileList.split(",")));
                    addedDefault = true;
                }
            } else {
                tiles.add(tile);
            }
        }
        if (tiles.contains("longscreenshot") && !SystemProperties.getBoolean("ro.longscreenshot.enable", true)) {
            boolean removed = tiles.remove("longscreenshot");
            Log.d(TAG, "remove longscreenshot as disabled:removed=" + removed);
        }
        return tiles;
    }
}
