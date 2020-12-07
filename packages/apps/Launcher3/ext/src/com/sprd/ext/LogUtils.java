package com.sprd.ext;

import android.os.Build;
import android.util.Log;

import com.android.launcher3.logging.FileLog;
import com.android.launcher3.util.TraceHelper;

/**
 * Created by SPRD on 2016/9/18.
 */
public final class LogUtils {

    public static final String MODULE_NAME = "Launcher3";

    private static LogUtils INSTANCE;

    public static boolean DEBUG_ALL = false;
    public static boolean DEBUG = false;
    public static boolean DEBUG_LOADER = false;
    public static boolean DEBUG_WIDGET = false;
    public static boolean DEBUG_RECEIVER = false;
    public static boolean DEBUG_DUMP_LOG = false;
    public static boolean DEBUG_DYNAMIC_ICON = false;
    public static boolean DEBUG_DYNAMIC_ICON_ALL = false;
    public static boolean DEBUG_UNREAD = false;
    public static boolean DEBUG_PERFORMANCE = false;

    /** use android properties to control debug on/off. */
    // define system properties
    private static final String PROP_DEBUG_ALL = "persist.sys.launcher.all";
    private static final String PROP_DEBUG = "persist.sys.launcher.debug";
    private static final String PROP_DEBUG_LOADER = "persist.sys.launcher.loader";
    private static final String PROP_DEBUG_WIDGET = "persist.sys.launcher.widget";
    private static final String PROP_DEBUG_RECEIVER = "persist.sys.launcher.receiver";
    private static final String PROP_DEBUG_DUMP_LOG = "persist.sys.launcher.dumplog";
    private static final String PROP_DEBUG_DYNAMIC_ICON = "persist.sys.launcher.dyicon";
    private static final String PROP_DEBUG_DYNAMIC_ICON_ALL = "persist.sys.launcher.dy.all";
    private static final String PROP_DEBUG_UNREAD = "persist.sys.launcher.unread";
    private static final String PROP_DEBUG_PERFORMANCE = "persist.sys.launcher.perform";
    /** end */

    static {
        DEBUG_ALL = SystemPropertiesUtils.getBoolean(PROP_DEBUG_ALL, false);

        if (DEBUG_ALL) {
            DEBUG = true;
            DEBUG_LOADER = true;
            DEBUG_WIDGET = true;
            DEBUG_RECEIVER = true;
            DEBUG_DUMP_LOG = true;
            DEBUG_DYNAMIC_ICON = true;
            DEBUG_DYNAMIC_ICON_ALL= true;
            DEBUG_UNREAD = true;
            DEBUG_PERFORMANCE = true;
        } else {
            DEBUG = SystemPropertiesUtils.getBoolean(PROP_DEBUG, !Build.TYPE.equals("user"));
            DEBUG_LOADER = SystemPropertiesUtils.getBoolean(PROP_DEBUG_LOADER, DEBUG);
            DEBUG_WIDGET = SystemPropertiesUtils.getBoolean(PROP_DEBUG_WIDGET, false);
            DEBUG_RECEIVER = SystemPropertiesUtils.getBoolean(PROP_DEBUG_RECEIVER, true);
            DEBUG_DUMP_LOG = SystemPropertiesUtils.getBoolean(PROP_DEBUG_DUMP_LOG, false);
            DEBUG_DYNAMIC_ICON = SystemPropertiesUtils.getBoolean(PROP_DEBUG_DYNAMIC_ICON, false);
            DEBUG_DYNAMIC_ICON_ALL = DEBUG_DYNAMIC_ICON &&
                    SystemPropertiesUtils.getBoolean(PROP_DEBUG_DYNAMIC_ICON_ALL, false);
            DEBUG_UNREAD = SystemPropertiesUtils.getBoolean(PROP_DEBUG_UNREAD, false);
            DEBUG_PERFORMANCE = SystemPropertiesUtils.getBoolean(PROP_DEBUG_PERFORMANCE, false);
        }

        if (DEBUG_PERFORMANCE) {
            TraceHelper.setTraceHelperEnable(true);
        }
        if (DEBUG_WIDGET) {
            FileLog.setFileLogEnable(true);
        }
    }
    /**
     * private constructor here, It is a singleton class.
     */
    private LogUtils() {
    }


    public static LogUtils getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LogUtils();
        }
        return INSTANCE;
    }

    /**
     * The method prints the log, level error.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void e(String tag, String msg) {
        Log.e(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level error.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t an exception to log.
     */
    public static void e(String tag, String msg, Throwable t) {
        Log.e(MODULE_NAME, tag + ", " + msg, t);
    }

    /**
     * The method prints the log, level warning.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void w(String tag, String msg) {
        Log.w(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level warning.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t an exception to log.
     */
    public static void w(String tag, String msg, Throwable t) {
        Log.w(MODULE_NAME, tag + ", " + msg, t);
    }

    /**
     * The method prints the log, level debug.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void i(String tag, String msg) {
        Log.i(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level debug.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t an exception to log.
     */
    public static void i(String tag, String msg, Throwable t) {
        Log.i(MODULE_NAME, tag + ", " + msg, t);
    }

    /**
     * The method prints the log, level debug.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void d(String tag, String msg) {
        Log.d(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level debug.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t An exception to log.
     */
    public static void d(String tag, String msg, Throwable t) {
        Log.d(MODULE_NAME, tag + ", " + msg, t);
    }

    /**
     * The method prints the log, level debug.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void v(String tag, String msg) {
        Log.v(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level debug.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t An exception to log.
     */
    public static void v(String tag, String msg, Throwable t) {
        Log.v(MODULE_NAME, tag + ", " + msg, t);
    }

}
