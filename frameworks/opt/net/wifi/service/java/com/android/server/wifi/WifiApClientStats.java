/*
 * Copyright (C) 2015 Spreadtrum.com
 *
 */

package com.android.server.wifi;

import static android.os.Process.WIFI_UID;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.wifi.WifiManager;
import android.os.Debug;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;


import java.nio.charset.StandardCharsets;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Pattern;



/**
 * Provides API to the WifiStateMachine for getting information of softap client
 */
class WifiApClientStats {

    private static final String TAG = "WifiApClientStats";
    private static boolean DBG = true; //Debug.isDebug();

    private static final String NDSMASQ_SOCKNAME = "dnsmasq";

    private final int BUFFER_SIZE = 1024;

    private static final String AP_BLOCKLIST_FILE = Environment.getDataDirectory() +
        "/misc/wifi/softapblocklist.conf";

    private static final String AP_WHITELIST_FILE = Environment.getDataDirectory() +
        "/misc/wifi/hostapd.accept";

    private static final String EVENT_NEW_CLIENT_PREFIX_STR = "<NEW> ";
    private static final int EVENT_NEW_CLIENT_PREFIX_STR_LEN = EVENT_NEW_CLIENT_PREFIX_STR.length();

    private static final String CLIENT_BLOCKMODE_PREFIX_STR = "#!macaddr_acl=";
    private static final int CLIENT_BLOCKMODE_PREFIX_STR_LEN = CLIENT_BLOCKMODE_PREFIX_STR.length();

    private static final String CLIENT_BLOCKMODE_BLACKLIST = "0";
    private static final String CLIENT_BLOCKMODE_WHITELIST = "1";
    private static final String patternStr = "^[A-Fa-f0-9]{2}(:[A-Fa-f0-9]{2}){5}$";


    private Object mLocker = new Object();
    private Object mWaiter = new Object();

    private boolean mStart = false;
    private HashMap<String, String> mClientInfoCache = new HashMap<String, String>();
    private HashMap<String, String> mBlockedClientInfoCache = new HashMap<String, String>();
    private HashMap<String, String> mWhiteClientInfoCache = new HashMap<String, String>();

    private boolean mBlockedClientChanged = false;
    private boolean mWhiteClientChanged = false;

    private Context mContext;
    private WifiApClientStatsThread mWifiApClientStatsThread;

    private Object mClientBlockedModeLocker = new Object();
    private String mClientBlockedMode = CLIENT_BLOCKMODE_BLACKLIST;

    WifiApClientStats(Context context) {
        mContext = context;
        mWifiApClientStatsThread = new WifiApClientStatsThread();
        mWifiApClientStatsThread.start();
    }

    public void sendDetailInfoAvailableBroadcast() {
        Intent intent = new Intent(WifiManager.WIFI_AP_CLIENT_DETAILINFO_AVAILABLE_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }


    /**
     *  load block list:
     *  MAC IP DEV_NAME
     *  such as: 00:08:22:0e:2d:fc 192.168.43.37 android-9dfb76a944bd077a
     */
    private void loadBlockList() {
        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(new FileInputStream(
                            AP_BLOCKLIST_FILE)));

            while (true) {

                String line = in.readUTF();

                if (line == null) continue;

                if (DBG) Log.d(TAG, "block: " + line);
                String[] tokens = line.split(" ");
                String mac = null;

                if (tokens.length == 0) continue;
                mac = tokens[0];

                mBlockedClientInfoCache.put(mac, line);

            }
        } catch (EOFException ignore) {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e1) {
                    Log.e(TAG, "loadBlockList: Error reading file" + e1);
                }
            }
        } catch (FileNotFoundException e2) {
            if (DBG) Log.e(TAG, "Could not open " + AP_BLOCKLIST_FILE + ", " + e2);
        }catch (IOException e3) {
            if (DBG) Log.e(TAG, "Could not read " + AP_BLOCKLIST_FILE + ", " + e3);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {}
            }
        }
    }

    /**
     *  save block list:
     *  MAC IP DEV_NAME
     *  such as: 00:08:22:0e:2d:fc 192.168.43.37 android-9dfb76a944bd077a
     */
    private void writeBlockList() {
        if (!mBlockedClientChanged) {
            if (DBG) Log.d(TAG, "block list not changed, do not need to save again!");
            return;
        }

        File file = new File(AP_BLOCKLIST_FILE);
        if (!file.exists()) {
            Log.d(TAG, "path : " + AP_BLOCKLIST_FILE + ", not exist");
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        DataOutputStream out = null;
        try {
            out = new DataOutputStream(new BufferedOutputStream(
                        new FileOutputStream(AP_BLOCKLIST_FILE)));

            for (String s : mBlockedClientInfoCache.values()) {
                if (s != null )
                    out.writeUTF(s + "\n");
            }

            mBlockedClientChanged = false;
        } catch (IOException e) {
            Log.e(TAG, "Error writing block list" + e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {}
            }
        }
    }



    /**
     *  load white list:
     *  format is the same as hostapd.accept in hostapd.
     *  for block mode, its format is as below:
     *  #!macaddr_acl=1
     *
     *  for mac white list
     *  MAC #DEV_NAME
     *  such as: 00:08:22:0e:2d:fc #android-9dfb76a944bd077a
     */
    private void loadWhiteList() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(AP_WHITELIST_FILE));

            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                //check #!macaddr_acl=1
                if (line.startsWith(CLIENT_BLOCKMODE_PREFIX_STR)) {
                    synchronized(mClientBlockedModeLocker) {
                        mClientBlockedMode = line.substring(CLIENT_BLOCKMODE_PREFIX_STR_LEN);
                        if (!CLIENT_BLOCKMODE_BLACKLIST.equals(mClientBlockedMode) && !CLIENT_BLOCKMODE_WHITELIST.equals(mClientBlockedMode)) {
                            if (DBG) Log.d(TAG, "Invalid client block mode: " + line);
                            mClientBlockedMode = CLIENT_BLOCKMODE_BLACKLIST;
                        }
                    }
                }

                if (line.startsWith("#")) continue;

                if (DBG) Log.d(TAG, "white: " + line);
                String[] tokens = line.split(" ");
                String mac = null;

                if (tokens.length == 0) continue;
                mac = tokens[0];

                String info = mac;

                //#DEV_NAME may not exist
                if (tokens.length > 1) {
                    info = mac + " " + tokens[1].substring(1);
                }

                synchronized(mWhiteClientInfoCache) {
                    mWhiteClientInfoCache.put(mac, info);
                }
            }

        } catch (FileNotFoundException e) {
            if (DBG) Log.e(TAG, "Could not open " + AP_WHITELIST_FILE + ", " + e);
        } catch (IOException e) {
            if (DBG) Log.e(TAG, "Could not read " + AP_WHITELIST_FILE + ", " + e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                // Just ignore the fact that we couldn't close
            }
        }
    }

    /**
     *  load white list:
     *  format is the same as hostapd.accept in hostapd.
     *  for block mode, its format is as below:
     *  #!macaddr_acl=1
     *
     *  for mac white list
     *  MAC #DEV_NAME
     *  such as: 00:08:22:0e:2d:fc #android-9dfb76a944bd077a
     */
    private void writeWhiteList() {

        if (!mWhiteClientChanged) {
            if (DBG) Log.d(TAG, "white list not changed, do not need to save again!");
            return;
        }

        File file = new File(AP_WHITELIST_FILE);
        if (!file.exists()) {
            Log.d(TAG, "path : " + AP_WHITELIST_FILE + ", not exist");
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        BufferedWriter out = null;
        try {

            out = new BufferedWriter(new FileWriter(AP_WHITELIST_FILE));

            //write client blockmode first
            out.write(CLIENT_BLOCKMODE_PREFIX_STR + mClientBlockedMode + "\n");

            synchronized(mWhiteClientInfoCache) {
                for (String s : mWhiteClientInfoCache.values()) {
                    if (s != null ) {
                        String[] tokens = s.split(" ");
                        if (tokens.length > 1)
                            out.write(tokens[0] + " #" + tokens[1] + "\n");
                        else
                            out.write(s + "\n");
                    }
                }
            }
            mWhiteClientChanged = false;
        } catch (IOException e) {
            Log.e(TAG, "Error writing hotspot configuration" + e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {}
            }
        }

        try {
            FileUtils.setPermissions(AP_WHITELIST_FILE, 0660, -1, WIFI_UID);
        } catch (Exception e1) {
            Log.e(TAG, "Error change permissions of hotspot configuration" + e1);
        }

    }


    /**
     * DHCP client info EVENT from dnsmasq
     * TYPE MAC IP DEV_NAME
     * <NEW> 00:08:22:0e:2d:fc 192.168.43.37 android-9dfb76a944bd077a
     * <EXPIRE> 00:08:22:0e:2d:fc 192.168.43.37 android-9dfb76a944bd077a
     */
    private void parseEvent(String event) {

        if (event==null  || !event.startsWith(EVENT_NEW_CLIENT_PREFIX_STR)) return;


        String eventStr = event.substring(EVENT_NEW_CLIENT_PREFIX_STR_LEN);

        if (eventStr == null) return;

        String[] tokens = eventStr.split(" ");
        String mac = null;

        if (tokens.length == 0) return;

        mac = tokens[0];

        String old = mClientInfoCache.get(mac);

        synchronized( mLocker){
            mClientInfoCache.put(mac, eventStr);
        }

        if (old == null) {
            if (DBG) Log.d(TAG, "New client info!!");
            //send broadcast
            sendDetailInfoAvailableBroadcast();
        }

        if (DBG) {
            Log.d(TAG, "All client Info:");
            for (String s : mClientInfoCache.values()) {
                Log.d(TAG, s);
            }
        }
    }

    private void listenToSocket(String socketName) throws IOException {
        LocalSocket socket = null;

        try {
            socket = new LocalSocket();
            LocalSocketAddress address = new LocalSocketAddress(socketName,
                    LocalSocketAddress.Namespace.ABSTRACT);

            socket.connect(address);

            InputStream inputStream = socket.getInputStream();


            byte[] buffer = new byte[BUFFER_SIZE];
            int start = 0;

            while (mStart) {
                int count = inputStream.read(buffer, start, BUFFER_SIZE - start);
                if (count < 0) {
                    Log.e(TAG, "got " + count + " reading with start = " + start);
                    break;
                }

                // Add our starting point to the count and reset the start.
                count += start;
                start = 0;

                //Note: message send from wcnd must end with null
                for (int i = 0; i < count; i++) {
                    if (buffer[i] == 0) {
                        final String rawEvent = new String(
                                buffer, start, i - start, StandardCharsets.UTF_8);
                        if (DBG) Log.d(TAG, "RCV <- {" + rawEvent + "}");

                        try {
                            parseEvent(rawEvent);
                        } catch (Exception e) {
                            Log.e(TAG, "Problem parsing message: " + rawEvent + " - " + e);
                        }

                        start = i + 1;
                    }
                }
                if (start == 0) {
                    final String rawEvent = new String(buffer, start, count, StandardCharsets.UTF_8);
                    if (DBG) Log.d(TAG, "RCV incomplete <- {" + rawEvent + "}");
                }

                // We should end at the amount we read. If not, compact then
                // buffer and read again.
                if (start != count) {
                    final int remaining = BUFFER_SIZE - start;
                    System.arraycopy(buffer, start, buffer, 0, remaining);
                    start = remaining;
                } else {
                    start = 0;
                }
            }
        } catch (IOException ex) {
            Log.e(TAG, "Communications error: " + ex);
            throw ex;
        } finally {

            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ex) {
                Log.e(TAG, "Failed closing socket: " + ex);
            }
        }
    }


    private class WifiApClientStatsThread extends Thread {

        public WifiApClientStatsThread() {
            super("WifiApClientStatsThread");
        }

        public void run() {
            loadBlockList();//load block list first
            loadWhiteList();//load white list first

            //noinspection InfiniteLoopStatement
            for (;;) {
                try {
                    if (mStart)
                        listenToSocket(NDSMASQ_SOCKNAME);
                    else {
                        if (DBG) Log.d(TAG, "WAIT");

                        synchronized( mWaiter) {
                            if (!mStart)
                                mWaiter.wait();
                        }
                        SystemClock.sleep(1000); //this time, dnsmasq may be not started completely
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception in connectting to dnsmasq " + e);
                    if (!mStart) {
                        synchronized( mWaiter) {
                            if (!mStart) {
                                try {
                                    mWaiter.wait();
                                } catch (Exception ex) {}
                            }
                        }
                    } else {
                        SystemClock.sleep(5000);
                    }
                }
            }
        }
    }


    public void start() {

        synchronized( mWaiter) {
            mStart = true;

            mWaiter.notifyAll();
        }
        if (DBG) Log.d(TAG, "START NOTIFY");

    }

    public void stop() {
        mStart = false;

        synchronized( mLocker){
            mClientInfoCache.clear();
        }

        writeBlockList();
        writeWhiteList();
        if (DBG) Log.d(TAG, "STOP");

    }


    /**
     * Get the detail info of the connected client
     * in: macList
     *      contain the mac that want to get the detail info. Format: xx:xx:xx:xx:xx:xx xx:xx:xx:xx:xx:xx
     * return:
     *      return the detail info list.
     *      Format of each string info:
     *      MAC IP DEV_NAME
     * such as:
     *      00:08:22:0e:2d:fc 192.168.43.37 android-9dfb76a944bd077a
     */
    public List<String> getClientInfoList(String macList) {
        List<String> infoList = new ArrayList<String>();
        String[] macs = null;

        if (macList != null) {
            if (macList.equals(""))
                return infoList;
            macs = macList.split(" ");
        }

        try {
            synchronized( mLocker){
                if (macs == null) {
                    for (String s : mClientInfoCache.values()) {
                        if (s != null) infoList.add(s);
                    }
                } else {
                    for (String mac : macs){
                        String info = mClientInfoCache.get(mac);
                        if (info != null) {
                            infoList.add(info);
                        } else {
                            if (DBG) Log.d(TAG, "the detail info for " + mac + " is not got yet");
                            infoList.add(mac);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed get client info list: " + e);
        }

        if (DBG) {
            Log.d(TAG, "Client info List:");
            for (String info : infoList){
                Log.d(TAG, info);
            }
        }

        return infoList;
    }


    /**
     * Get the detail info of the blocked client
     * in: macList
     *      contain the mac that want to get the detail info. Format: xx:xx:xx:xx:xx:xx xx:xx:xx:xx:xx:xx
     * return:
     *      return the detail info list.
     *      Format of each string info:
     *      MAC IP DEV_NAME
     * such as:
     *      00:08:22:0e:2d:fc 192.168.43.37 android-9dfb76a944bd077a
     */
    public List<String> getBlockedClientInfoList(String macList) {
        List<String> infoList = new ArrayList<String>();
        String[] macs = null;
        String[] macstring = null;

        if (macList != null) {
            if (macList.equals(""))
                return infoList;
            macs = macList.split(" ");
        }

        try {
            if (macs == null) {
                for (String s : mBlockedClientInfoCache.values()) {
                    if (s != null) infoList.add(s);
                }
            } else {
                for (String mac : macs){
                    macstring =mac.split(" ");
                    Log.d(TAG, "get client : "+ macstring[0] + " chek mac :" +  checkMac(macstring[0].trim()));
                    if (checkMac(macstring[0].trim())) {
                        String info = mBlockedClientInfoCache.get(mac);
                        if (info != null) {
                            infoList.add(info);
                        } else {
                            if (DBG) Log.d(TAG, "the detail info for " + mac + " is not got yet");
                            infoList.add(mac);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed get client info list: " + e);
        }

        if (DBG) {
            Log.d(TAG, "Blocked Client info List:");
            for (String info : infoList){
                Log.d(TAG, info);
            }
        }

        return infoList;
    }


    /**
     * unblock the client
     * in: mac
     *      contain the mac that want Unblocked. Format: xx:xx:xx:xx:xx:xx
     * return:
     *      return true for success.
     */
    public boolean unBlockClient(String mac) {

        try {
            Log.e(TAG, "unBlockClient " + mac);
            mBlockedClientInfoCache.remove(mac);
        } catch (Exception e) {
            Log.e(TAG, "Failed to unblock client: " + e);
        }

        mBlockedClientChanged = true;
        sendDetailInfoAvailableBroadcast();

        return true;
    }

    /**
     * block the client
     * in: mac
     *      contain the mac that want Unblocked. Format: xx:xx:xx:xx:xx:xx
     * return:
     *      return true for success.
     */
    public boolean blockClient(String mac) {

        try {
            String info = mClientInfoCache.get(mac);

            if (info != null)
                mBlockedClientInfoCache.put(mac, info);
        } catch (Exception e) {
            Log.e(TAG, "Failed to block client: " + e);
        }

        mBlockedClientChanged = true;
        sendDetailInfoAvailableBroadcast();

        return true;
    }


    /**
     * add the client to white list
     * in: mac
     *      contain the mac that want to add to white list. Format: xx:xx:xx:xx:xx:xx
     * in: name
     *      the name of the client, may be null
     * in softapStarted
     *      tell if the softap has started or not
     * return:
     *      return true for success.
     */
    public boolean addClientToWhiteList(String mac, String name, boolean softapStarted) {

        if (mac == null) return false;
        if (!checkMac(mac)) {
            Log.e(TAG, "checkMac false, mac: " + mac);
            return false;
        }

        try {
            String info = mac;
            if (name != null) info = mac + " " + name;

            synchronized(mWhiteClientInfoCache) {
                mWhiteClientInfoCache.put(mac, info);
            }
            mWhiteClientChanged = true;
            if (DBG) Log.d(TAG, "Save White list");
            writeWhiteList();
        } catch (Exception e) {
            Log.e(TAG, "Failed to unblock client: " + e);
        }
        sendDetailInfoAvailableBroadcast();

        return true;
    }

    /**
     * remove the client from white list
     * in: mac
     *      contain the mac that want to remove from white list. Format: xx:xx:xx:xx:xx:xx
     * in: name
     *      the name of the client, may be null
     * in softapStarted
     *      tell if the softap has started or not
     * return:
     *      return true for success.
     */
    public boolean delClientFromWhiteList(String mac, String name, boolean softapStarted) {

        try {
            synchronized(mWhiteClientInfoCache) {
                mWhiteClientInfoCache.remove(mac);
            }
            mWhiteClientChanged = true;

            if (DBG) Log.d(TAG, "Save White list");
            writeWhiteList();
        } catch (Exception e) {
            Log.e(TAG, "Failed to unblock client: " + e);
        }
        sendDetailInfoAvailableBroadcast();
        return true;
    }

    /**
     * To enable the white list or not
     * in enabled
     *      true: enable white list
     *      false: disable white list
     */
    public boolean setClientWhiteListEnabled(boolean enabled, boolean softapStarted) {

        try {
            synchronized(mClientBlockedModeLocker) {
                if (enabled)
                    mClientBlockedMode = CLIENT_BLOCKMODE_WHITELIST;
                else
                    mClientBlockedMode = CLIENT_BLOCKMODE_BLACKLIST;
            }
            mWhiteClientChanged = true;
            writeWhiteList();
        } catch (Exception e) {
            Log.e(TAG, "Failed to unblock client: " + e);
        }
        return true;
    }
    private boolean checkMac(String str) {
        if (str == null) {
            Log.e(TAG, "checkMac error: str == null");
            return false;
        }
        return Pattern.matches(patternStr, str);
    }
    /**
     * Get the detail info of the white client list
     * return:
     *      return the detail info list.
     *      Format of each string info:
     *      MAC DEV_NAME
     * such as:
     *      00:08:22:0e:2d:fc android-9dfb76a944bd077a
     */
    public List<String> getClientWhiteList() {
        List<String> infoList = new ArrayList<String>();
        String macs[] = null;

        try {
            synchronized(mWhiteClientInfoCache) {
                for (String s : mWhiteClientInfoCache.values()) {
                    if(s != null) {
                        macs = s.split(" ");
                        Log.d(TAG, "get client : "+ macs[0] + " chek mac :" +  checkMac(macs[0].trim()));
                        if (checkMac(macs[0].trim())) {
                            infoList.add(s);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed get white client info list: " + e);
        }

        if (DBG) {
            Log.d(TAG, "White Client info List:");
            for (String info : infoList){
                Log.d(TAG, info);
            }
        }

        return infoList;
    }

    /**
     * Get the MAC info of the white client list
     * return:
     *      return the MAC info list.
     *      Format of each string info:
     *      MAC
     * such as:
     *      00:08:22:0e:2d:fc
     */
    public List<String> getClientMacWhiteList() {
        List<String> infoList = new ArrayList<String>();
        String macs[] = null;
        Log.d(TAG, "getClientMacWhiteList");

        try {
            synchronized(mWhiteClientInfoCache) {
                for (String s : mWhiteClientInfoCache.values()) {
                    if (s != null) {
                        macs = s.split(" ");
                        if (checkMac(macs[0].trim())) {
                            infoList.add(macs[0].trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed get white client mac list: " + e);
        }

        return infoList;
    }

    public boolean isWhiteListEnabled() {
        synchronized(mClientBlockedModeLocker) {
            if (CLIENT_BLOCKMODE_WHITELIST.equals(mClientBlockedMode))
                return true;
            else
                return false;
        }
    }

}
