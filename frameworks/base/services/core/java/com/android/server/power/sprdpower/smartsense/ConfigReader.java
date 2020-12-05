/*
 ** Copyright 2018 The Spreadtrum.com
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


public class ConfigReader {

    static final String TAG = "SSense.ConfigReader";

    static ConfigReader sInstance;

    private Constants mConstants;



    public static ConfigReader getInstance() {
        synchronized (ConfigReader.class) {
            if (sInstance == null) {
                sInstance = new ConfigReader();
            }
            return sInstance;
        }
    }

    public ConfigReader() {
        mConstants = new Constants();
        mConstants.loadConfig();
        mConstants.dump();
    }


    public Constants getConstants() {
        return mConstants;
    }

    public boolean inInternalVideoAppList(String pkgName) {
        if (pkgName == null) return false;
        for (int i=0;i<mConstants.mVideoAppList.size();i++) {
            if (pkgName.contains(mConstants.mVideoAppList.get(i)))
                return true;
        }

        return false;
    }

    public boolean inInternalGameAppList(String pkgName) {
        if (pkgName == null) return false;
        for (int i=0;i<mConstants.mGameAppList.size();i++) {
            if (pkgName.contains(mConstants.mGameAppList.get(i)))
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

        private static final String configFile = "/system/etc/sprdssense_config.xml";

        private static final String XML_ITEM_TAG = "item";
        private static final String XML_NAME_TAG = "name";
        private static final String XML_PACKAGE_NAME_TAG = "packageName";


        private static final String CONFIG_VIDEO_LIST = "Video";
        private static final String CONFIG_GAME_LIST = "Game";


        ArrayList<String> mVideoAppList = new ArrayList<>();
        ArrayList<String> mGameAppList = new ArrayList<>();


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

                        if (CONFIG_VIDEO_LIST.equals(configName)) {
                            readAppList(pullParser, mVideoAppList);
                        } else if (CONFIG_GAME_LIST.equals(configName)) {
                            readAppList(pullParser, mGameAppList );
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
            Slog.d(TAG, "mVideoAppList: " + mVideoAppList.size());
            for (int i=0;i<mVideoAppList.size();i++) {
                Slog.d(TAG, "App:" + mVideoAppList.get(i));
            }

            Slog.d(TAG, "mGameAppList: " + mGameAppList.size());
            for (int i=0;i<mGameAppList.size();i++) {
                Slog.d(TAG, "App:" + mGameAppList.get(i));
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
