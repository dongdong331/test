/*
 * Copyright (C) 2016 Spreadtrum.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.wifi;

import android.net.wifi.WifiInfo;
import android.net.LinkProperties;

/**
 * Callback class for receiving events from an WifiService
 *
 * @hide
 */
interface IWifiNetworkEventObserver {
    /**
     * A wifi network is connected.
     *
     * @param wifiInfo The WifiInfo of the current connected Wifi Network.
     * @param trustNetwork True if the connected Wifi Network is a trusted network.
     */
    void onWifiNetworkConnected(in WifiInfo wifiInfo, boolean trustNetwork);

    /**
     * A wifi network is diconnected.
     *
     * @param wifiInfo The WifiInfo of the current diconnected Wifi Network.
     * @param trustNetwork True if the diconnected Wifi Network is a trusted network.
     * @param disable True if this disconnected is cause by disable wifi.
     */
    void onWifiNetworkDisconnected(in WifiInfo wifiInfo, boolean trustNetwork, boolean disable);

    /**
     * A wifi network is connecting.
     *
     * @param wifiInfo The WifiInfo of the current connecting Wifi Network, default is null.
     * @param trustNetwork True if the connectingi Network is a trusted network, defaut is false.
     */
    void onWifiNetworkConnecting(in WifiInfo wifiInfo, boolean trustNetwork);

    /**
     * The rssi of the connected Wifi is changed
     *
     * @param rssi The new rssi.
     */
    void onRssiChanged(int rssi);

    /**
     * The linkProperties of the connected Wifi is changed
     *
     * @param linkProperties The new linkProperties.
     */
    void notifyLinkPropertiesChanged(in LinkProperties linkProperties);

    /**
     * Notify wifi will be close.
     *
     */
    void onWifiPreClose();
}
