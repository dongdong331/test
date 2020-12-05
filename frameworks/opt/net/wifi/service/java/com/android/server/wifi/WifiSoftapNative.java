/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.server.wifi;

import android.annotation.Nullable;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.Immutable;
import com.android.internal.util.HexDump;
import android.net.KeepalivePacketData;
import com.android.server.wifi.util.FrameParser;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import android.text.TextUtils;


/**
 * Native calls for bring up/shut down of the supplicant daemon and for sending requests to the
 * supplicant daemon {@hide}
 */
public class WifiSoftapNative {
    private final String mTAG;
    private final String mInterfaceName;
    private final HostapdHal mHostapdHal;

    public WifiSoftapNative(String interfaceName, HostapdHal HostapdHal) {
        mTAG = "WifiNative-" + interfaceName;
        mInterfaceName = interfaceName;
        mHostapdHal = HostapdHal;
    }

    public String getInterfaceName() {
        return mInterfaceName;
    }

    /**
     * Enable verbose logging for all sub modules.
     */
    public void enableVerboseLogging(int verbose) {
        mHostapdHal.enableVerboseLogging(verbose > 0);
    }

    /********************************************************
     * Supplicant operations
     ********************************************************/

    /**
     * This method is called repeatedly until the connection to wpa_supplicant is established.
     *
     * @return true if connection is established, false otherwise. TODO: Add unit tests for these
     *         once we remove the legacy code.
     */
    public boolean connectToHostapd() {
        Log.e(mTAG,"connectToHostapd");
        // Start initialization if not already started.
        if (!mHostapdHal.isInitializationStarted()
                && !mHostapdHal.initialize()) {
            return false;
        }
        // Check if the initialization is complete.
        return mHostapdHal.isInitializationComplete();
    }
    public boolean isHostapdConncted () {
         Log.e(mTAG,"isHostapdConncted");
         return mHostapdHal.isInitializationComplete();
     }

    public boolean doHostapdBooleanCommand(String ifaceName, String cmd) {
        return mHostapdHal.doHostapdBooleanCommand(ifaceName, cmd);
    }
    public int doHostapdIntCommand(String ifaceName, String cmd) {
        return mHostapdHal.doHostapdIntCommand(ifaceName, cmd);
    }
    public String doHostapdStringCommand(String ifaceName, String cmd) {
        return mHostapdHal.doHostapdStringCommand(ifaceName, cmd);
    }

    //NOTE: Add for softap support wps connect mode and hidden ssid Feature BEG-->
    public boolean softApStartWpsPbc(String ifaceName) {
        return doHostapdBooleanCommand(ifaceName, "WPS_PBC");
    }

    public boolean softApStartWpsPinKeypad(String pin) {
        if (TextUtils.isEmpty(pin)) return false;
        return doHostapdBooleanCommand(mInterfaceName, "WPS_PIN any " + pin);
    }

    public boolean softApCancelWps() {
      return doHostapdBooleanCommand(mInterfaceName, "WPS_CANCEL");
    }

    public boolean softApCheckWpsPin(String pin) {
       if (TextUtils.isEmpty(pin)) return false;
     String res = doHostapdStringCommand(mInterfaceName, "WPS_CHECK_PIN " + pin);
     return (res != null && !res.equals("FAIL-CHECKSUM"));
    }
    //<-- Add for softap support wps connect mode and hidden ssid Feature END

    //NOTE: Add For SoftAp advance Feature BEG-->

    public boolean softApSetBlockStation(String infaceName, String mac, boolean enabled) {
        if (enabled) {
            return doHostapdBooleanCommand(infaceName, "DRIVER BLOCK " + mac);
        } else {
            return doHostapdBooleanCommand(infaceName, "DRIVER UNBLOCK " + mac);
        }
    }

    private static final String UNKOWN_COMMAND = "UNKNOWN COMMAND";
    public String softApGetBlockStationList(String infaceName) {
        String blockList = doHostapdStringCommand(infaceName, "DRIVER BLOCK_LIST");
        return UNKOWN_COMMAND.equalsIgnoreCase(blockList) ? "" : blockList;
    }


    /**
     *  To add a station to the white list or remove from the white list
     * enabled:
     *     ture: add to the white list
     *     false: remove from the white list
     */
    public boolean softApSetStationToWhiteList(String infaceName, String mac, boolean enabled) {
        if (mac == null) return false;
        if (enabled) {
            return doHostapdBooleanCommand(infaceName, "DRIVER WHITE_ADD " + mac);
        } else {
            return doHostapdBooleanCommand(infaceName, "DRIVER WHITE_DEL " + mac);
        }
    }

    /**
     *  To enable the white list mode or not
     * enabled:
     *     ture: enable white list
     *     false: disable white list
     */
    public boolean softApSetWhiteListEnabled(String infaceName, boolean enabled) {
        if (enabled) {
            return doHostapdBooleanCommand(infaceName, "DRIVER WHITE_EN");//set macaddr_acl 1
        } else {
            return doHostapdBooleanCommand(infaceName, "DRIVER WHITE_DIS");//set macaddr_acl 0
        }
    }
    //<-- Add for SoftAp Advance Feature END
    public void softApGetStations() {
        doHostapdStringCommand(mInterfaceName, "GETSTATIONS");
    }
    public boolean softApSetCountry(String country) {
        if (country == null) {
            Log.e(mTAG, "softApSetCountry country == null ");
            return false;
        }
        doHostapdStringCommand(mInterfaceName, "SET_HOSTAPD_COUNTRY_CODE " + country);
        return true;
    }

    public String softApGetSupportChannel() {
        String channels = doHostapdStringCommand(mInterfaceName, "GET_HOSTAPD_SUPPORT_CHANNEL ");
        Log.d(mTAG, "softApGetSupportChannel channels = " + channels);
        return channels;
    }
}

