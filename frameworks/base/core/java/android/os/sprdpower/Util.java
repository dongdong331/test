/*
 ** Copyright 2016 The Spreadtrum.com
 */

package android.os.sprdpower;

import android.os.Build;
import android.os.SystemProperties;
import android.util.Slog;
import android.util.SparseIntArray;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/**
 * @hide
 */
public class Util {

    static final String TAG = "PowerController";

    // Keep same with UsageEvents.java
    private static String[] STATE_NAMES = new String[] {"NONE",
        "MOVE_TO_FOREGROUND",
        "MOVE_TO_BACKGROUND",
        "END_OF_DAY",
        "CONTINUE_PREVIOUS_DAY",
        "CONFIGURATION_CHANGE",//5
        "SYSTEM_INTERACTION",
        "USER_INTERACTION",
        "SHORTCUT_INVOCATION",
        "CHOOSER_ACTION",
        "NOTIFICATION_SEEN",
        "STANDBY_BUCKET_CHANGED",
        "NOTIFICATION_INTERRUPTION",
        "SLICE_PINNED_PRIV",
        "SLICE_PINNED",
        "SCREEN_INTERACTIVE",
        "SCREEN_NON_INTERACTIVE",
        "KEYGUARD_SHOWN",
        "KEYGUARD_HIDDEN",
    };

    // Keep same with ActivityManager.java and BundleData.java ROCESS_STATE_XXX
    private static String[] PROC_STATE_NAMES = new String[] {
        "PROCESS_STATE_PERSISTENT",
        "PROCESS_STATE_PERSISTENT_UI",
        "PROCESS_STATE_TOP",
        "PROCESS_STATE_FOREGROUND_SERVICE",
        "PROCESS_STATE_BOUND_FOREGROUND_SERVICE",
        "PROCESS_STATE_IMPORTANT_FOREGROUND",//5
        "PROCESS_STATE_IMPORTANT_BACKGROUND",
        "PROCESS_STATE_TRANSIENT_BACKGROUND",
        "PROCESS_STATE_BACKUP",
        "PROCESS_STATE_SERVICE",
        "PROCESS_STATE_RECEIVER",//10
        "PROCESS_STATE_TOP_SLEEPING",
        "PROCESS_STATE_HEAVY_WEIGHT",
        "PROCESS_STATE_HOME",
        "PROCESS_STATE_LAST_ACTIVITY",
        "PROCESS_STATE_CACHED_ACTIVITY", //15
        "PROCESS_STATE_CACHED_ACTIVITY_CLIENT",
        "PROCESS_STATE_CACHED_RECENT",
        "PROCESS_STATE_CACHED_EMPTY",
        "PROCESS_STATE_NONEXISTENT",
        "PROCESS_STATE_VENDOR_START",
        "PROCESS_STATE_VENDOR_FINISH",
    };

    // This white list is used for CTS & GTS
    private final static String[] mCtsWhiteAppList = new String[] {
        "android.app.cts",
        "com.android.cts",
        "android.icu.dev.test.util",
        "android.largeapk.app",
        "android.abioverride.app",
        "com.google.android.ar.svc",
        "com.android.tradefed.utils.wifi",
        "com.drawelements.deqp",
        "android.theme.app",
        "android.libcore.runner",
        "android.taskswitching.appa",
        "android.cpptools.app",
        "android.externalservice.service",
        "android.os.app",
        "android.assist.service",
        "android.assist.testapp",
        "android.leanbackjank.app",
        "android.voiceinteraction.service",
        "android.voiceinteraction.testapp",
        "android.backup.app",
        "android.trustedvoice.app",
        "com.android.gputest",
        "android.test.app",
        "android.voicesettings.service",
        "android.app.usage.app",
        "android.admin.app",
        "android.app.stubs",
        "android.server.app",
        "android.displaysize.app",
        "com.replica.replicaisland",
        "android.taskswitching.appb",
        "android.cpptools.app",
        "android.harmfulappwarning.testapp",
        "android.harmfulappwarning.sampleapp",
        "android.alarmclock.service"
    };

    private final static  String[] mGmsCoreAppList = new String[] {
        "com.google.android.gms",
        "com.android.vending",
    };


    private static final boolean DEBUG_LOG_ENABLED = getDebugLogEnabled();

    public static String AppState2Str(int state) {
        if ((state >= 0) && (state < STATE_NAMES.length))
            return STATE_NAMES[state];
        else
            return "Unknown state: " + state;
    }


    public static String ProcState2Str(int state) {


        if ((state >= 0) && (state < PROC_STATE_NAMES.length))
            return PROC_STATE_NAMES[state];
        else
            return "Unknown state: " + state;
    }


    public static boolean isCts(String pkgName) {
        if (pkgName == null) return false;


        /*check if in internal white app list, like CTS app*/
        for(String s : mCtsWhiteAppList) {
            if(pkgName.contains(s)) {
                return true;
            }
        }

        // is cts app
        if ((pkgName.startsWith("android.") && pkgName.contains(".cts."))
            || (pkgName.startsWith("android.") && pkgName.endsWith(".cts"))
            || (pkgName.startsWith("com.android.") && pkgName.contains(".cts."))
            || (pkgName.startsWith("com.android.") && pkgName.endsWith(".cts"))) {
            return true;
        }

        // is gts app
        if ((pkgName.startsWith("com.google.") && pkgName.contains(".gts."))
            || (pkgName.startsWith("com.google.") && pkgName.endsWith(".gts"))
            || (pkgName.startsWith("com.android.") && pkgName.contains(".gts."))
            || (pkgName.startsWith("com.android.") && pkgName.endsWith(".gts"))
            || (pkgName.startsWith("com.android.compatibility.") )
            ) {
            return true;
        }

        return false;
    }

    public static boolean isGmsCoreApp(String pkgName) {
        if (pkgName == null) return false;

        for(String s : mGmsCoreAppList) {
            if(pkgName.contains(s)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isDebug(){
        return Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug");
    }

    private static boolean getDebugLogEnabled() {
        String mValue = SystemProperties.get("persist.sys.power.fw.debug");
        StringBuilder mStringBuilder = new StringBuilder(mValue);
        String mStringBuilderNew = mStringBuilder.toString();
        if (mStringBuilderNew.contains("controller")) {
            return true;
        }
        return false;
    }

    public static boolean getDebugLog() {
        return DEBUG_LOG_ENABLED;
    }
}
