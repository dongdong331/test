/*
 ** Copyright 2016 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;


import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IDeviceIdleController;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.Xml;

import com.android.internal.util.XmlUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;
import com.android.internal.util.FastXmlSerializer;
import org.xmlpull.v1.XmlPullParserException;


public class PowerConfig {

    static final String TAG = "PowerController.PowerConfig";

    static PowerConfig sInstance;

    private Constants mConstants;



    public static PowerConfig getInstance() {
        synchronized (PowerConfig.class) {
            if (sInstance == null) {
                sInstance = new PowerConfig();
            }
            return sInstance;
        }
    }

    public PowerConfig() {
        mConstants = new Constants();
        mConstants.loadConfig();
        mConstants.dump();
    }


    public Constants getConstants() {
        return mConstants;
    }

    public boolean inLaunchWhiteList(String pkgName) {
        int index = mConstants.mLaunchWhitelist.indexOf(pkgName);
        if (index >= 0) {
            return true;
        }
        return false;
    }

    public boolean inLaunchBlackList(String pkgName) {
        int index = mConstants.mLaunchBlacklist.indexOf(pkgName);
        if (index >= 0) {
            return true;
        }
        return false;
    }

    //
    // --------------- Constant config value for PowerController ----------
    //
    /**
     * All times are in milliseconds. These constants are load from "/system/etc/pwctl_config.xml"
     * If there is not "/system/etc/pwctl_config.xml", then all the Helpers
     * will use their own default value.
     */
    public static class Constants {
        private static final String TAG = "PowerController.Const";

        private final boolean TEST = PowerController.TEST;


        private static final String configFile = "/system/etc/pwctl_config.xml";

        private static final String XML_ITEM_TAG = "item";
        private static final String XML_NAME_TAG = "name";

        private static final String XML_TIMEOUT_TAG = "to";
        private static final String XML_TIMEOUT_LOWPOWER_TAG = "lto";
        private static final String XML_PAROLE_TIMEOUT_TAG = "pto";
        private static final String XML_PAROLE_TIMEOUT_LOWPOWER_TAG = "lpto";
        private static final String XML_IDLE_TIMEOUT_TAG = "ito";
        private static final String XML_IDLE_TIMEOUT_LOWPOWER_TAG = "lito";

        private static final String XML_DURATION_TAG = "duration";
        private static final String XML_DURATION_LOWPOWER_TAG = "lduration";

        private static final String XML_THREASHOLD1_TAG = "threshold1";
        private static final String XML_THREASHOLD2_TAG = "threshold2";
        private static final String XML_THREASHOLD3_TAG = "threshold3";

        private static final String XML_RESERVE_APP_COUNT_TAG = "rappc";
        private static final String XML_RESERVE_APP_COUNT_LOWPOWER_TAG = "lrappc";

        private static final String XML_PACKAGE_NAME_TAG = "packageName";

        private static final String CONFIG_GURU = "guru";
        private static final String CONFIG_IDLE = "idle";
        private static final String CONFIG_WAKELOCK = "wake";
        private static final String CONFIG_BACKGROUND = "bg";
        private static final String CONFIG_GPS = "gps";

        private static final String CONFIG_LAUNCH_WHITE_LIST = "LaunchWhiteList";
        private static final String CONFIG_LAUNCH_BLACK_LIST = "LaunchBlackList";


        /**
         * This is the time, after becoming inactive (standby), at which we start apply
         * power guru constraint
         */
        long POWERGURU_INACTIVE_TIMEOUT =  (TEST ? 5 * 60 * 1000L : 20 * 60 * 1000L);
        long POWERGURU_INACTIVE_TIMEOUT_LOWPOWER = (5 * 60 * 1000L);


        /**
         * This is the time, after becoming inactive (standby), at which we start apply
         * wake lock constraint
         */
        long WAKELOCK_INACTIVE_TIMEOUT = (TEST ? 5 * 60 * 1000L : 20 * 60 * 1000L);
        long WAKELOCK_INACTIVE_TIMEOUT_LOWPOWER = (5 * 60 * 1000L); // 5min

        /**
         * This is the time, after app is constrait for wakelock, at which app can hold
         * a partial wake lock
         */
        long WAKELOCK_CONSTRAINT_DURATION = (30 * 1000L); // 30s
        long WAKELOCK_CONSTRAINT_DURATION_LOWPOWER = (5*1000L); // 5s

        /**
         * This is the time, after becoming inactive (standby), at which we start apply
         * app idle constraint
         */
        long APPIDLE_INACTIVE_TIMEOUT = (TEST ? 5 * 60 * 1000L : 20 * 60 * 1000L);
        long APPIDLE_INACTIVE_TIMEOUT_LOWPOWER = (5 * 60 * 1000L);

        /**
         * This is the time, after app exit app idle (standby) state, at which we start apply
         * app idle constraint again. That is the time app will stay in non-idle state
         */
        long APPIDLE_PAROLE_TIMEOUT = (4 * 60 * 1000L); // 4min < CHECK period
        long APPIDLE_PAROLE_TIMEOUT_LOWPOWER = (3 * 60 * 1000L);

        /**
         * This is the time, after app enter app idle (standby) state, at which we start
         * make app exit idle state. That is the time app will stay in idle state
         */
        long APPIDLE_IDLE_TIMEOUT = (TEST ? 60 * 60 * 1000L : 60 * 60 * 1000L);
        long APPIDLE_IDLE_TIMEOUT_LOWPOWER = (120 * 60 * 1000L);


        /**
         * This is the time, after becoming inactive (standby), at which we start
         * kill a app which has notification and is NOT started by user
         */
        long BG_APPIDLE_THRESHOLD1 = (TEST ? 10 * 60 * 1000L : 60 * 60 * 1000L);
        /**
         * This is the time, after becoming inactive (standby), at which we start
         * kill a app which has NOT notification and is started by user
         */
        long BG_APPIDLE_THRESHOLD2 = (TEST ? 20 * 60 * 1000L : 120 * 60 * 1000L);
        /**
         * This is the time, after becoming inactive (standby), at which we start
         * kill a app which has notification and is started by user
         */
        long BG_APPIDLE_THRESHOLD3 = (TEST ? 30 * 60 * 1000L : 240 * 60 * 1000L);

        /**
         * This is MAX app will be keep, when doing background app clean
         */
        int BG_MAX_LAUNCHED_APP_KEEP = 3;
        int BG_MAX_LAUNCHED_APP_KEEP_LOWPOWER = 2;

        /**
         * This is the time, after becoming inactive (standby), at which we start apply
         * GPS constraint
         */
        long GPS_INACTIVE_TIMEOUT = (TEST ? 5 * 60 * 1000L : 30 * 60 * 1000L);
        long GPS_INACTIVE_TIMEOUT_LOWPOWER =  (5 * 60 * 1000L);


        ArrayList<String> mLaunchWhitelist = new ArrayList<>();
        ArrayList<String> mLaunchBlacklist = new ArrayList<>();


        public Constants() {
        }

        public boolean loadConfig(){
            AtomicFile aFile = new AtomicFile(new File(configFile));
            InputStream stream = null;

            try {
                stream = aFile.openRead();
            }catch (FileNotFoundException exp){
                Slog.e(TAG, ">>>file not found,"+exp);
                return false;
            }

            try {
                String configName = null;
                XmlPullParser pullParser = Xml.newPullParser();
                pullParser.setInput(stream, "UTF-8");

                int type;
                while ((type=pullParser.next()) != pullParser.START_TAG
                           && type != pullParser.END_DOCUMENT) {
                    ;
                }

                if (type != pullParser.START_TAG) {
                     Slog.e(TAG, ">>>No start tag found");
                     return false;
                }

                if (!pullParser.getName().equals("config")) {
                    Slog.e(TAG, "Unexpected start tag found :" + pullParser.getName() + ", expected 'config'");
                    return false;
                }


                while (true) {
                    XmlUtils.nextElement(pullParser);
                    if (pullParser.getEventType() == XmlPullParser.END_DOCUMENT) {
                        break;
                    }

                     if (XML_ITEM_TAG.equals(pullParser.getName())) {
                        configName = pullParser.getAttributeValue(null, XML_NAME_TAG);

                        if (CONFIG_GURU.equals(configName)) {
                            POWERGURU_INACTIVE_TIMEOUT = getLong(pullParser.getAttributeValue(null, XML_TIMEOUT_TAG), (20 * 60 * 1000L));
                            POWERGURU_INACTIVE_TIMEOUT_LOWPOWER = getLong(pullParser.getAttributeValue(null, XML_TIMEOUT_LOWPOWER_TAG), (5 * 60 * 1000L));

                            XmlUtils.skipCurrentTag(pullParser);
                            continue;
                        } else if (CONFIG_IDLE.equals(configName)) {
                            APPIDLE_INACTIVE_TIMEOUT = getLong(pullParser.getAttributeValue(null, XML_TIMEOUT_TAG), (20 * 60 * 1000L));
                            APPIDLE_INACTIVE_TIMEOUT_LOWPOWER = getLong(pullParser.getAttributeValue(null, XML_TIMEOUT_LOWPOWER_TAG), (5 * 60 * 1000L));

                            APPIDLE_PAROLE_TIMEOUT = getLong(pullParser.getAttributeValue(null, XML_PAROLE_TIMEOUT_TAG), (4 * 60 * 1000L));
                            APPIDLE_PAROLE_TIMEOUT_LOWPOWER = getLong(pullParser.getAttributeValue(null, XML_PAROLE_TIMEOUT_LOWPOWER_TAG), (3 * 60 * 1000L));

                            APPIDLE_IDLE_TIMEOUT = getLong(pullParser.getAttributeValue(null, XML_IDLE_TIMEOUT_TAG), (60 * 60 * 1000L));
                            APPIDLE_IDLE_TIMEOUT_LOWPOWER = getLong(pullParser.getAttributeValue(null, XML_IDLE_TIMEOUT_LOWPOWER_TAG), (120 * 60 * 1000L));

                            XmlUtils.skipCurrentTag(pullParser);
                            continue;

                        } else if (CONFIG_WAKELOCK.equals(configName)) {
                            WAKELOCK_INACTIVE_TIMEOUT = getLong(pullParser.getAttributeValue(null, XML_TIMEOUT_TAG), (20 * 60 * 1000L));
                            WAKELOCK_INACTIVE_TIMEOUT_LOWPOWER = getLong(pullParser.getAttributeValue(null, XML_TIMEOUT_LOWPOWER_TAG), (5 * 60 * 1000L));

                            WAKELOCK_CONSTRAINT_DURATION = getLong(pullParser.getAttributeValue(null, XML_DURATION_TAG), (30 * 1000L));
                            WAKELOCK_CONSTRAINT_DURATION_LOWPOWER = getLong(pullParser.getAttributeValue(null, XML_DURATION_LOWPOWER_TAG), (5*1000L));

                            XmlUtils.skipCurrentTag(pullParser);
                            continue;

                        } else if (CONFIG_BACKGROUND.equals(configName)) {
                            BG_APPIDLE_THRESHOLD1 = getLong(pullParser.getAttributeValue(null, XML_THREASHOLD1_TAG), (60 * 60 * 1000L));
                            BG_APPIDLE_THRESHOLD2 = getLong(pullParser.getAttributeValue(null, XML_THREASHOLD2_TAG), (120 * 60 * 1000L));

                            BG_APPIDLE_THRESHOLD3 = getLong(pullParser.getAttributeValue(null, XML_THREASHOLD3_TAG), (240 * 60 * 1000L));

                            BG_MAX_LAUNCHED_APP_KEEP = getInteger(pullParser.getAttributeValue(null, XML_RESERVE_APP_COUNT_TAG), 3);
                            BG_MAX_LAUNCHED_APP_KEEP_LOWPOWER = getInteger(pullParser.getAttributeValue(null, XML_RESERVE_APP_COUNT_LOWPOWER_TAG), 2);

                            XmlUtils.skipCurrentTag(pullParser);
                            continue;
                        } else if (CONFIG_GPS.equals(configName)) {
                            GPS_INACTIVE_TIMEOUT = getLong(pullParser.getAttributeValue(null, XML_TIMEOUT_TAG), (30 * 60 * 1000L));
                            GPS_INACTIVE_TIMEOUT_LOWPOWER = getLong(pullParser.getAttributeValue(null, XML_TIMEOUT_LOWPOWER_TAG), (5 * 60 * 1000L));

                            XmlUtils.skipCurrentTag(pullParser);
                            continue;

                        } else if (CONFIG_LAUNCH_WHITE_LIST.equals(configName)) {
                            readAppList(pullParser, mLaunchWhitelist);
                        } else if (CONFIG_LAUNCH_BLACK_LIST.equals(configName)) {
                            readAppList(pullParser, mLaunchBlacklist );
                        } else {
                            XmlUtils.skipCurrentTag(pullParser);
                            continue;
                        }
                    } else {
                        XmlUtils.skipCurrentTag(pullParser);
                        continue;
                    }

                }
            } catch (IllegalStateException e) {
                Slog.e(TAG, "Failed parsing " + e);
                return false;
            } catch (NullPointerException e) {
                Slog.e(TAG, "Failed parsing " + e);
                return false;
            } catch (NumberFormatException e) {
                Slog.e(TAG, "Failed parsing " + e);
                return false;
            } catch (XmlPullParserException e) {
                Slog.e(TAG, "Failed parsing " + e);
                return false;
            } catch (IOException e) {
                Slog.e(TAG, "Failed parsing " + e);
                return false;
            } catch (IndexOutOfBoundsException e) {
                Slog.e(TAG, "Failed parsing " + e);
                return false;
            } finally {
                try {
                    stream.close();
                } catch (IOException e) {
                    Slog.e(TAG, "Fail to close stream " + e);
                    return false;
                } catch (Exception e) {
                    Slog.e(TAG, "exception at last,e: " + e);
                    return false;
                }
            }

            return true;
        }

        public void dump() {
            Slog.d(TAG, "POWERGURU_INACTIVE_TIMEOUT: " + POWERGURU_INACTIVE_TIMEOUT
                + " POWERGURU_INACTIVE_TIMEOUT_LOWPOWER: " + POWERGURU_INACTIVE_TIMEOUT_LOWPOWER
                + " WAKELOCK_INACTIVE_TIMEOUT: " + WAKELOCK_INACTIVE_TIMEOUT
                + " WAKELOCK_INACTIVE_TIMEOUT_LOWPOWER: " + WAKELOCK_INACTIVE_TIMEOUT_LOWPOWER
                + " WAKELOCK_CONSTRAINT_DURATION: " + WAKELOCK_CONSTRAINT_DURATION
                + " WAKELOCK_CONSTRAINT_DURATION_LOWPOWER: " + WAKELOCK_CONSTRAINT_DURATION_LOWPOWER
                + " APPIDLE_INACTIVE_TIMEOUT: " + APPIDLE_INACTIVE_TIMEOUT
                + " APPIDLE_INACTIVE_TIMEOUT_LOWPOWER: " + APPIDLE_INACTIVE_TIMEOUT_LOWPOWER
                + " APPIDLE_PAROLE_TIMEOUT: " + APPIDLE_PAROLE_TIMEOUT
                + " APPIDLE_PAROLE_TIMEOUT_LOWPOWER: " + APPIDLE_PAROLE_TIMEOUT_LOWPOWER
                + " APPIDLE_IDLE_TIMEOUT: " + APPIDLE_IDLE_TIMEOUT
                + " APPIDLE_IDLE_TIMEOUT_LOWPOWER: " + APPIDLE_IDLE_TIMEOUT_LOWPOWER
                + " BG_APPIDLE_THRESHOLD1: " + BG_APPIDLE_THRESHOLD1
                + " BG_APPIDLE_THRESHOLD2: " + BG_APPIDLE_THRESHOLD2
                + " BG_APPIDLE_THRESHOLD3: " + BG_APPIDLE_THRESHOLD3
                + " BG_MAX_LAUNCHED_APP_KEEP: " + BG_MAX_LAUNCHED_APP_KEEP
                + " BG_MAX_LAUNCHED_APP_KEEP_LOWPOWER: " + BG_MAX_LAUNCHED_APP_KEEP_LOWPOWER
                + " GPS_INACTIVE_TIMEOUT: " + GPS_INACTIVE_TIMEOUT
                + " GPS_INACTIVE_TIMEOUT_LOWPOWER: " + GPS_INACTIVE_TIMEOUT_LOWPOWER);


            Slog.d(TAG, "mLaunchWhitelist: " + mLaunchWhitelist.size());
            for (int i=0;i<mLaunchWhitelist.size();i++) {
                Slog.d(TAG, "App:" + mLaunchWhitelist.get(i));
            }

            Slog.d(TAG, "mLaunchBlacklist: " + mLaunchBlacklist.size());
            for (int i=0;i<mLaunchBlacklist.size();i++) {
                Slog.d(TAG, "App:" + mLaunchBlacklist.get(i));
            }

        }

        private long getLong(String value, long def) {
            if (value != null) {
                try {
                    return Long.parseLong(value);
                } catch (Exception e) {
                    // fallthrough
                }
            }
            return def;
        }

        private int getInteger(String value, int def) {
            if (value != null) {
                try {
                    return Integer.parseInt(value);
                } catch (Exception e) {
                    // fallthrough
                }
            }
            return def;
        }

        private void readAppList(XmlPullParser parser, ArrayList<String> outAppList)
            throws IOException, XmlPullParserException {
            if (parser == null || outAppList == null) {
                Slog.e(TAG, "readAppList Error: null");
                return;
            }

            int outerDepth = parser.getDepth();
            //Slog.w(TAG, "readAppList depth: " + outerDepth);

            int type;
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                   && (type != XmlPullParser.END_TAG
                           || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG
                        || type == XmlPullParser.TEXT) {
                    continue;
                }

                String tagName = parser.getName();
                //Slog.w(TAG, "tagName: " + tagName);

                if (XML_PACKAGE_NAME_TAG.equals(tagName)) {
                    String packageName = parser.nextText();
                    //Slog.w(TAG, "<XML_PACKAGE_NAME_TAG>: " + packageName);
                    if (packageName != null) {
                        int index = outAppList.indexOf(packageName);
                        if (index < 0) {
                            outAppList.add(packageName);
                        }
                    } else {
                        Slog.w(TAG, "<XML_PACKAGE_NAME_TAG> without text at "
                                + parser.getPositionDescription());
                    }
                }
            }
        }

    }

}
