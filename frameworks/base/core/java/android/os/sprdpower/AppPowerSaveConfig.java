/*
 * Copyright (C) 2016 The Spreadtrum.com
 */

package android.os.sprdpower;

import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.Xml;

import com.android.internal.util.FastXmlSerializer;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import org.xmlpull.v1.XmlSerializer;

import java.util.Map;

import java.io.File;
//import java.io.FileDescriptor;
//import java.io.PrintWriter;
//import java.io.ByteArrayOutputStream;
//import java.io.OutputStream;
//import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * @hide
 */
public class AppPowerSaveConfig implements Parcelable{
    public int optimize = 1;
    public int alarm = 0;
    public int wakelock = 0;
    public int network = 0;
    public int autoLaunch = 0;
    public int secondaryLaunch = 0;
    public int lockscreenCleanup = 0;
    public int powerConsumerType = 0;

    static final String TAG = "PowerController.Config";

    public enum ConfigType {
        TYPE_NULL(-1),
        TYPE_OPTIMIZE(0),
        TYPE_ALARM(1),
        TYPE_WAKELOCK(2),
        TYPE_NETWORK(3),
        TYPE_AUTOLAUNCH(4),
        TYPE_SECONDARYLAUNCH(5),
        TYPE_LOCKSCREENCLEANUP(6),
        TYPE_POWERCONSUMERTYPE(7),
        TYPE_MAX(8);

        public final int value;
        private ConfigType(int value) {
            this.value = value;
        }
    }

    public static final int POWER_CONSUMER_TYPE_NONE = 0x00;
    public static final int POWER_CONSUMER_TYPE_ALARM = 0x01;
    public static final int POWER_CONSUMER_TYPE_WAKELOCK = 0x02;
    public static final int POWER_CONSUMER_TYPE_GPS = 0x04;


    public static String ConfigType2Str(int configType) {
        final String[] typeStr = {"TYPE_OPTIMIZE",
            "TYPE_ALARM",
            "TYPE_WAKELOCK",
            "TYPE_NETWORK",
            "TYPE_AUTOLAUNCH", //5
            "TYPE_SECONDARYLAUNCH",
            "TYPE_LOCKSCREENCLEANUP",
            "TYPE_POWERCONSUMERTYPE"};

        if ((configType >= 0) && (configType < typeStr.length)) {
            return typeStr[configType];
        } else {
            return "Unknown type: " + configType;
        }
    }

    public static final int MASK_NULL = 0x00;
    public static final int MASK_OPTIMIZE = 0x01;
    public static final int MASK_ALARM = 0x02;
    public static final int MASK_WAKELOCK = 0x04;
    public static final int MASK_NETWORK = 0x08;
    public static final int MASK_AUTOLAUNCH = 0x10;
    public static final int MASK_SECONDARYLAUNCH = 0x20;
    public static final int MASK_LOCKSCRRENCLEANUP = 0x40;
    public static final int MASK_POWERCONSUMERTYPE = 0x80;

    public static int[] mMaskArray = {MASK_OPTIMIZE,
        MASK_ALARM,
        MASK_WAKELOCK,
        MASK_NETWORK,
        MASK_AUTOLAUNCH,
        MASK_SECONDARYLAUNCH,
        MASK_LOCKSCRRENCLEANUP,
        MASK_POWERCONSUMERTYPE
    };

    public static final int VALUE_INVALID = -1;
    public static final int VALUE_AUTO = 0;
    public static final int VALUE_OPTIMIZE = 1;
    public static final int VALUE_NO_OPTIMIZE = 2;

    public static String ConfigValue2Str(int configValue) {
        final String[] typeStr = {"VALUE_AUTO",
            "VALUE_OPTIMIZE",
            "VALUE_NO_OPTIMIZE"};

        if ((configValue >= 0) && (configValue < typeStr.length)) {
            return typeStr[configValue];
        } else {
            return "Unknown value: " + configValue;
        }
    }

    private static final String AUTOLAUNCH_DEF_PROP = "persist.sys.pwctl.auto";
    private static final String SECONDARYLAUNCH_DEF_PROP = "persist.sys.pwctl.secondary";

    private static final int AUTOLAUNCH_DEF =
            SystemProperties.getInt(AUTOLAUNCH_DEF_PROP, VALUE_OPTIMIZE);
    private static final int SECONDARYLAUNCH_DEF =
            SystemProperties.getInt(SECONDARYLAUNCH_DEF_PROP, VALUE_OPTIMIZE);

    private static int[] mDefConfig = {VALUE_OPTIMIZE, // for optimize
        VALUE_AUTO, // for alarm
        VALUE_AUTO, // for wakelock
        VALUE_AUTO, // for network
        VALUE_OPTIMIZE, // for autolaunch
        VALUE_OPTIMIZE, // for 2ndlaunch
        VALUE_AUTO, // for lockscrenn cleanup
        POWER_CONSUMER_TYPE_NONE}; // for powerConsumerType

    /**
     * Check whether equal default config value. The default values are defined in mDefConfig[].
     *
     * @param type The config type need to check.
     * @param value The config value need to check.
     * @return Returns the compare result.
     */
    public static boolean isDefault(int type, int value) {
        return (mDefConfig[type] == value);
    }

    /**
     * Get the default value of specific config type.
     * The default values are defined in mDefConfig[].
     *
     * @param type The config type.
     * @return Returns the default value.
     */
    public static int getDefaultValue(int type) {
        return mDefConfig[type];
    }

    static {
        mDefConfig[ConfigType.TYPE_AUTOLAUNCH.value] = AUTOLAUNCH_DEF;
        mDefConfig[ConfigType.TYPE_SECONDARYLAUNCH.value] = SECONDARYLAUNCH_DEF;
    }

    public AppPowerSaveConfig() {
        for (int i = 0; i < ConfigType.TYPE_MAX.value; i++) {
            setConfigWithType(this , i, mDefConfig[i]);
        }
    }

    AppPowerSaveConfig(Parcel in) {
        optimize = in.readInt();
        alarm = in.readInt();
        wakelock = in.readInt();
        network = in.readInt();
        autoLaunch = in.readInt();
        secondaryLaunch = in.readInt();
        lockscreenCleanup = in.readInt();
        powerConsumerType = in.readInt();
    }

    /**
     * static api used to return the config value of the 'configType' in the config.
     * @param config The AppPowerSaveConfig.
     * @param configType The specified config type.
     * @return Returns the value of the config type.
     */
    public static int getConfigValue(AppPowerSaveConfig config, int configType) {
        if (configType == ConfigType.TYPE_OPTIMIZE.value) {
            return config.optimize;
        } else if (configType == ConfigType.TYPE_ALARM.value) {
            return config.alarm;
        } else if (configType == ConfigType.TYPE_WAKELOCK.value) {
            return config.wakelock;
        } else if (configType == ConfigType.TYPE_NETWORK.value) {
            return config.network;
        } else if (configType == ConfigType.TYPE_AUTOLAUNCH.value) {
            return config.autoLaunch;
        } else if (configType == ConfigType.TYPE_SECONDARYLAUNCH.value) {
            return config.secondaryLaunch;
        } else if (configType == ConfigType.TYPE_LOCKSCREENCLEANUP.value) {
            return config.lockscreenCleanup;
        } else if (configType == ConfigType.TYPE_POWERCONSUMERTYPE.value) {
            return config.powerConsumerType;
        }

        return VALUE_INVALID;
    }

    /**
     * Set the value of specific config type in given config struct.
     *
     * @param config The config struct need to update.
     * @param configType The config type.
     * @param value The config value.
     * @return Returns whether operatte successfully.
     */
    public static boolean setConfigWithType(AppPowerSaveConfig config, int configType, int value) {
        if (configType == ConfigType.TYPE_POWERCONSUMERTYPE.value) {
            config.powerConsumerType = value;
            return true;
        }

        if ((value < VALUE_AUTO) || (value > VALUE_NO_OPTIMIZE)) {
            return false;
        }

        if (configType == ConfigType.TYPE_OPTIMIZE.value) {
            config.optimize = value;
        } else if (configType == ConfigType.TYPE_ALARM.value) {
            config.alarm = value;
        } else if (configType == ConfigType.TYPE_WAKELOCK.value) {
            config.wakelock = value;
        } else if (configType == ConfigType.TYPE_NETWORK.value) {
            config.network = value;
        } else if (configType == ConfigType.TYPE_AUTOLAUNCH.value) {
            config.autoLaunch = value;
        } else if (configType == ConfigType.TYPE_SECONDARYLAUNCH.value) {
            config.secondaryLaunch = value;
        } else if (configType == ConfigType.TYPE_LOCKSCREENCLEANUP.value) {
            config.lockscreenCleanup = value;
        }

        return true;
    }

    /**
     * check if this AppPowerSaveConfig is valid.
     * @return Returns ture for valid. False for invalid.
     */
    public boolean isValid() {
        if ((alarm < VALUE_AUTO) || (alarm > VALUE_NO_OPTIMIZE)) {
            return false;
        }
        if ((wakelock < VALUE_AUTO) || (wakelock > VALUE_NO_OPTIMIZE)) {
            return false;
        }
        if ((network < VALUE_AUTO) || (network > VALUE_NO_OPTIMIZE)) {
            return false;
        }
        if ((autoLaunch < VALUE_AUTO) || (autoLaunch > VALUE_NO_OPTIMIZE)) {
            return false;
        }
        if ((secondaryLaunch < VALUE_AUTO) || (secondaryLaunch > VALUE_NO_OPTIMIZE)) {
            return false;
        }
        if ((lockscreenCleanup < VALUE_AUTO) || (lockscreenCleanup > VALUE_NO_OPTIMIZE)) {
            return false;
        }
        return true;
    }

    //whether all the config value have been reset
    public boolean isReset() {
        /*if ((1 == optimize)
            && (mDefConfig[ConfigType.TYPE_ALARM.value] == alarm)
            && (mDefConfig[ConfigType.TYPE_WAKELOCK.value] == wakelock)
            && (mDefConfig[ConfigType.TYPE_NETWORK.value] == network)
            && (mDefConfig[ConfigType.TYPE_AUTOLAUNCH.value] == autoLaunch)
            && (mDefConfig[ConfigType.TYPE_SECONDARYLAUNCH.value] == secondaryLaunch)
            && (mDefConfig[ConfigType.TYPE_LOCKSCREENCLEANUP.value] == lockscreenCleanup)) {
            return true;
        }*/
        return false;
    }
    private static final String APPCONFIG_FILENAME = "appPowerSaveConfig.xml";

    private static final String XML_TAG_FILE = "app_powersave_config";
    private static final String XML_TAG_PKG = "package";
    private static final String XML_ATTRIBUTE_PKG_NAME = "name";
    private static final String XML_ATTRIBUTE_PKG_OPTIMIZE  = "optimize";
    private static final String XML_ATTRIBUTE_PKG_ALARM  = "alarm";
    private static final String XML_ATTRIBUTE_PKG_WAKELOCK = "wakelock";
    private static final String XML_ATTRIBUTE_PKG_NETWORK  = "network";
    private static final String XML_ATTRIBUTE_PKG_AUTOLAUNCH  = "autolaunch";
    private static final String XML_ATTRIBUTE_PKG_SECONDARYLAUNCH  = "secondarylaunch";
    private static final String XML_ATTRIBUTE_PKG_LOCKSCREENCLEANUP  = "lockscreencleanup";
    private static final String XML_ATTRIBUTE_PKG_POWERCONSUMERTYPE = "consumertype";

    private static void writeItem(XmlSerializer serializer,
            String tag, String value) throws IOException {
        serializer.startTag(null, tag);
        if (value == null) {
            serializer.text("null");
        } else {
            serializer.text(value);
        }
        serializer.endTag(null, tag);
    }

    /**
     * static API used to write the configs in 'appConfigMap' to /data/system/appPowerSaveConfig.xml
     * @param appConfigMap The configs that will save to config file.
     * @return Returns true for sucess.Return false for fail
     */
    public static boolean writeConfig(Map<String, AppPowerSaveConfig> appConfigMap) {
        AtomicFile aFile = new AtomicFile(new File(new File(Environment.getDataDirectory(),
                "system"), APPCONFIG_FILENAME));

        FileOutputStream stream;
        try {
            stream = aFile.startWrite();
        } catch (IOException e) {
            Slog.e(TAG, "Failed to write state: " + e);
            return false;
        }

        try {
            XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(stream, "utf-8");
            serializer.startDocument(null, true);
            serializer.startTag(null, XML_TAG_FILE);

            if (appConfigMap != null) {
                for (Map.Entry<String, AppPowerSaveConfig> cur : appConfigMap.entrySet()) {
                    final String appName = cur.getKey();
                    final AppPowerSaveConfig config = cur.getValue();
                    if (config.isReset()) continue;

                    serializer.startTag(null, XML_TAG_PKG);
                    serializer.attribute(null, XML_ATTRIBUTE_PKG_NAME, appName);
                    serializer.attribute(null, XML_ATTRIBUTE_PKG_OPTIMIZE,
                            String.valueOf(config.optimize));
                    serializer.attribute(null, XML_ATTRIBUTE_PKG_ALARM,
                            String.valueOf(config.alarm));
                    serializer.attribute(null, XML_ATTRIBUTE_PKG_WAKELOCK,
                            String.valueOf(config.wakelock));
                    serializer.attribute(null, XML_ATTRIBUTE_PKG_NETWORK,
                            String.valueOf(config.network));
                    serializer.attribute(null, XML_ATTRIBUTE_PKG_AUTOLAUNCH,
                            String.valueOf(config.autoLaunch));
                    serializer.attribute(null, XML_ATTRIBUTE_PKG_SECONDARYLAUNCH,
                            String.valueOf(config.secondaryLaunch));
                    serializer.attribute(null, XML_ATTRIBUTE_PKG_LOCKSCREENCLEANUP,
                            String.valueOf(config.lockscreenCleanup));
                    serializer.attribute(null, XML_ATTRIBUTE_PKG_POWERCONSUMERTYPE,
                            String.valueOf(config.powerConsumerType));
                    serializer.endTag(null, XML_TAG_PKG);
                }
            }
            serializer.endTag(null, XML_TAG_FILE);
            serializer.endDocument();
            aFile.finishWrite(stream);
        } catch (IOException e) {
            Slog.e(TAG, "Failed to write state, restoring backup." + "exp:" + "\n" + e);
            aFile.failWrite(stream);
            return false;
        }
        return true;
    }


    /**
     * static API used to read the config from /data/system/appPowerSaveConfig.xml
     * and save them in appConfigMap
     * @param appConfigMap The configs read from config file will save to it
     * @return Returns true for sucess.Return false for fail
     */
    public static boolean readConfig(Map<String, AppPowerSaveConfig> appConfigMap) {
        AtomicFile aFile = new AtomicFile(new File(new File(Environment.getDataDirectory(),
                "system"), APPCONFIG_FILENAME));

        InputStream stream = null;

        try {
            stream = aFile.openRead();
        } catch (FileNotFoundException exp) {
            Slog.e(TAG, ">>>file not found," + exp);
        }

        if (null == stream) {
            aFile = new AtomicFile(new File(new File(Environment.getRootDirectory(), "etc"),
                APPCONFIG_FILENAME));

            try {
                stream = aFile.openRead();
            } catch (FileNotFoundException exp) {
                Slog.e(TAG, ">>>default file not found," + exp);
                return false;
            }
        }

        try {
            String appName = null;
            AppPowerSaveConfig appConfig = null;
            XmlPullParser pullParser = Xml.newPullParser();
            pullParser.setInput(stream, "UTF-8");
            int event = pullParser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                switch (event) {
                    case XmlPullParser.START_DOCUMENT:
                        //retList = new ArrayList<PowerGuruAlarmInfo>();
                        break;

                    case XmlPullParser.START_TAG:
                        if (XML_TAG_PKG.equals(pullParser.getName())) {
                            appConfig = new AppPowerSaveConfig();
                            appName = pullParser.getAttributeValue(null, XML_ATTRIBUTE_PKG_NAME);
                            appConfig.optimize = Integer.parseInt(pullParser.getAttributeValue(null,
                                XML_ATTRIBUTE_PKG_OPTIMIZE));
                            appConfig.alarm = Integer.parseInt(pullParser.getAttributeValue(null,
                                XML_ATTRIBUTE_PKG_ALARM));
                            appConfig.wakelock = Integer.parseInt(pullParser.getAttributeValue(null,
                                XML_ATTRIBUTE_PKG_WAKELOCK));
                            appConfig.network = Integer.parseInt(pullParser.getAttributeValue(null,
                                XML_ATTRIBUTE_PKG_NETWORK));
                            appConfig.autoLaunch = Integer.parseInt(pullParser.getAttributeValue(
                                null, XML_ATTRIBUTE_PKG_AUTOLAUNCH));
                            appConfig.secondaryLaunch =
                                Integer.parseInt(pullParser.getAttributeValue(
                                    null, XML_ATTRIBUTE_PKG_SECONDARYLAUNCH));
                            appConfig.lockscreenCleanup =
                                Integer.parseInt(pullParser.getAttributeValue(
                                    null, XML_ATTRIBUTE_PKG_LOCKSCREENCLEANUP));
                            appConfig.powerConsumerType =
                                Integer.parseInt(pullParser.getAttributeValue(
                                    null, XML_ATTRIBUTE_PKG_POWERCONSUMERTYPE));
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if (XML_TAG_PKG.equals(pullParser.getName())) {
                            appConfigMap.put(appName, appConfig);
                            appConfig = null;
                        }
                        break;
                }
                event = pullParser.next();
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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(optimize);
        dest.writeInt(alarm);
        dest.writeInt(wakelock);
        dest.writeInt(network);
        dest.writeInt(autoLaunch);
        dest.writeInt(secondaryLaunch);
        dest.writeInt(lockscreenCleanup);
        dest.writeInt(powerConsumerType);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(TAG);
        result.append("{ optimize: " + optimize
                + ", alarm: " + AppPowerSaveConfig.ConfigValue2Str(alarm) + "(" + alarm
                + "), wakelock: "  + AppPowerSaveConfig.ConfigValue2Str(wakelock) + "(" + wakelock
                + "), network: " + AppPowerSaveConfig.ConfigValue2Str(network) + "(" + network
                + "), autoLaunch: " + AppPowerSaveConfig.ConfigValue2Str(autoLaunch) + "("
                + autoLaunch + "), secondaryLaunch: "
                + AppPowerSaveConfig.ConfigValue2Str(secondaryLaunch) + "("
                + secondaryLaunch + "), lockscreenCleanup: "
                + AppPowerSaveConfig.ConfigValue2Str(lockscreenCleanup)
                + "(" + lockscreenCleanup + ") }");
        return result.toString();
    }

    /**
     * static api used to create AppPowerSaveConfig
     */
    public static final Parcelable.Creator<AppPowerSaveConfig> CREATOR =
                        new Parcelable.Creator<AppPowerSaveConfig>() {

        /**
         * Create a AppPowerSaveConfig object from a  {@link Parcel}
         * @param in
         * @return Return a AppPowerSaveConfig object
         */
        public AppPowerSaveConfig createFromParcel(Parcel in) {
            return new AppPowerSaveConfig(in);
        }

        /**
         * Create an array of AppPowerSaveConfig objects with a size of 'size'
         * @param size The size of the array
         * @return Return an array of AppPowerSaveConfig objects
         */
        public AppPowerSaveConfig[] newArray(int size) {
            return new AppPowerSaveConfig[size];
        }
    };

}

