/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use mHost file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs.tileimpl;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;
import android.os.sprdpower.IPowerManagerEx;
import android.os.sprdpower.PowerManagerEx;
import android.os.ServiceManager;
import android.view.ContextThemeWrapper;

import com.android.ims.ImsManager;
import com.android.ims.internal.ImsManagerEx;
import com.android.systemui.R;
import com.android.systemui.plugins.qs.*;
import com.android.systemui.plugins.qs.QSTileView;
import com.android.systemui.qs.external.CustomTile;
import com.android.systemui.qs.tiles.AirplaneModeTile;
import com.android.systemui.qs.tiles.BatterySaverTile;
import com.android.systemui.qs.tiles.BluetoothTile;
import com.android.systemui.qs.tiles.CastTile;
import com.android.systemui.qs.tiles.CellularTile;
import com.android.systemui.qs.tiles.ColorInversionTile;
import com.android.systemui.qs.tiles.DataSaverTile;
import com.android.systemui.qs.tiles.DndTile;
import com.android.systemui.qs.tiles.FlashlightTile;
import com.android.systemui.qs.tiles.HotspotTile;
import com.android.systemui.qs.tiles.IntentTile;
import com.android.systemui.qs.tiles.LocationTile;
import com.android.systemui.qs.tiles.LteServiceTile;
import com.android.systemui.qs.tiles.NfcTile;
import com.android.systemui.qs.tiles.NightDisplayTile;
import com.android.systemui.qs.tiles.RotationLockTile;
import com.android.systemui.qs.tiles.SuperBatteryTile;
import com.android.systemui.qs.tiles.UserTile;
import com.android.systemui.qs.tiles.VoWifiTile;
import com.android.systemui.qs.tiles.VolteTile;
import com.android.systemui.qs.tiles.WifiTile;
import com.android.systemui.qs.tiles.WorkModeTile;
import com.android.systemui.qs.QSTileHost;
import com.android.systemui.util.leak.GarbageMonitor;
import com.android.systemui.statusbar.phone.StatusBar;
import com.sprd.systemui.qs.tiles.LongScreenshotTile;

import com.sprd.systemui.SystemUIAudioProfileUtils;

public class QSFactoryImpl implements QSFactory {

    private static final String TAG = "QSFactory";
    private final QSTileHost mHost;
    private static final String RADIO_MODEM_CAPABILITY = "persist.vendor.radio.modem.config";
    private static final String LTE_INDICATOR = "L";

    public QSFactoryImpl(QSTileHost host) {
        mHost = host;
    }

    public QSTile createTile(String tileSpec) {
        QSTileImpl tile = createTileInternal(tileSpec);
        if (tile != null) {
            tile.handleStale(); // Tile was just created, must be stale.
        }
        return tile;
    }

    private QSTileImpl createTileInternal(String tileSpec) {
        Log.w(TAG, "createTileInternal tileSpec: " + tileSpec);
        // Stock tiles.
        switch (tileSpec) {
            case "wifi":
                return new WifiTile(mHost);
            case "bt":
                return new BluetoothTile(mHost);
            case "cell":
                return new CellularTile(mHost);
            case "vowifi":
            case "lte":
            case "lte2":
                return createExtraTile(mHost, tileSpec);
            case "dnd":
                return new DndTile(mHost);
            case "inversion":
                return new ColorInversionTile(mHost);
            case "airplane":
                return new AirplaneModeTile(mHost);
            case "work":
                return new WorkModeTile(mHost);
            case "rotation":
                return new RotationLockTile(mHost);
            case "flashlight":
                return new FlashlightTile(mHost);
            case "longscreenshot":
                //UNISOC: fix for bug 977961
                if (ActivityManager.getCurrentUser() == 0) {
                    return new LongScreenshotTile(mHost);
                /* UNISOC: Modify for bug981995 {@ */
                } else {
                    return null;
                /*}@*/
                }
            case "location":
                return new LocationTile(mHost);
            case "cast":
                return new CastTile(mHost);
            case "hotspot":
                return new HotspotTile(mHost);
            case "user":
                return new UserTile(mHost);
            case "battery":
                /*SPRD bug 885650:Super power feature*/
                if(StatusBar.SUPPORT_SUPER_POWER_SAVE){
                    Log.w(TAG, "SuperBatteryTile " + tileSpec);
                    return new SuperBatteryTile(mHost);
                }else{
                    return new BatterySaverTile(mHost);
                }
                /* @} */
            case "saver":
                return new DataSaverTile(mHost);
            case "night":
                return new NightDisplayTile(mHost);
            case "nfc":
                return new NfcTile(mHost);
            /*UNISOC bug 886412 692442:New feature for audioprofile {@*/
            case "audioprofile":
                if (SystemUIAudioProfileUtils.getInstance().isSupportAudioProfileTile()  && (!SystemProperties.getBoolean("ro.config.google.sound", false))) {
                    return SystemUIAudioProfileUtils.getInstance().createAudioProfileTile(mHost);
                }
            /* @} */
            /*UNISOC add for bug 985941*/
            case "volte1":
                if (isShowVolte() && ImsManagerEx.isDualLteModem()) {
                    return new VolteTile(mHost,0);
                }
            case "volte2":
                if (isShowVolte() && ImsManagerEx.isDualLteModem()) {
                    return new VolteTile(mHost,1);
                }
            /* @} */
        }

        // Intent tiles.
        if (tileSpec.startsWith(IntentTile.PREFIX)) return IntentTile.create(mHost, tileSpec);
        if (tileSpec.startsWith(CustomTile.PREFIX)) return CustomTile.create(mHost, tileSpec);

        // Debug tiles.
        /* UNISOC bug 908966 control MemoryTile by ro.systemui.debug_memory @{ */
        boolean debugMemory = SystemProperties.getBoolean("systemui.debug_memory",false);
        if (Build.IS_DEBUGGABLE && debugMemory) {
        /* @} */
            if (tileSpec.equals(GarbageMonitor.MemoryTile.TILE_SPEC)) {
                return new GarbageMonitor.MemoryTile(mHost);
            }
        }

        // Broken tiles.
        Log.w(TAG, "Bad tile spec: " + tileSpec);
        return null;
    }

    public QSTileImpl createExtraTile(QSTileHost host, String tileSpec) {
        if ("vowifi".equals(tileSpec)
                && ImsManager.isWfcEnabledByPlatform(host.getContext())){
            return new VoWifiTile(host);
        } else if ("lte".equals(tileSpec)
                && host.getContext().getResources().getBoolean(R.bool.config_show_lte_tile)
                && isDeviceSupportLte()) {
            return new LteServiceTile(host);
        } else if ("lte2".equals(tileSpec)
                && host.getContext().getResources().getBoolean(R.bool.config_show_lte_tile)
                && ImsManagerEx.isDualLteModem()) {
            return new LteServiceTile(host,1);
        }
        return null;
    }

    /* UNISOC: Bug792842 not support lte for pike2 @{ */
    public static boolean isDeviceSupportLte() {
        String prop = SystemProperties.get(RADIO_MODEM_CAPABILITY, "");
        Log.d(TAG, "isDeviceSupportLte prop = " + prop);
        if (!prop.isEmpty() && prop.toUpperCase().contains(LTE_INDICATOR)) {
            Log.d(TAG, "isDeviceSupportLte TRUE");
            return true;
        }
        return false;
    }
    /* @} */

    @Override
    public QSTileView createTileView(QSTile tile, boolean collapsedView) {
        Context context = new ContextThemeWrapper(mHost.getContext(), R.style.qs_theme);
        QSIconView icon = tile.createTileView(context);
        if (collapsedView) {
            return new QSTileBaseView(context, icon, collapsedView);
        } else {
            return new com.android.systemui.qs.tileimpl.QSTileView(context, icon);
        }
    }
    /* SPRD: add for bug:985941@{ */
    private boolean isShowVolte() {
        boolean isVersionShowVolte = false;
        if (mHost.getContext() != null) {
            isVersionShowVolte = mHost.getContext().getResources().getBoolean(R.bool.config_qs_volte_tile);
        }
        Log.w(TAG, "isVersionShowVoltec: " + isVersionShowVolte);
        return isVersionShowVolte;
    }
    /* @} */
}
