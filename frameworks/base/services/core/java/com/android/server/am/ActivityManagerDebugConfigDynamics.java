/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.am;


import android.content.ContentResolver;
import android.provider.Settings;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.ArrayMap;
import android.util.Slog;

import android.os.Binder;

import java.util.ArrayList;
import java.util.List;

import java.io.PrintWriter;
import java.lang.reflect.Field;

/**
 * Common class for the various debug {@link android.util.Log} output configuration in the activity
 * manager package.
 */
class ActivityManagerDebugConfigDynamics {

    private static final String TAG = "ActivityManagerDebugConfigDynamics";

    static ArrayMap<String,Field> sDebugSwitchStateMap;

    static void printIfNeeded(@Nullable PrintWriter pw, String message) {
        if (pw != null)
            pw.println(message);
        else
            Slog.w(TAG, message);
    }

    static void ensureMapIfNeeded(@Nullable PrintWriter pw) {
        if (sDebugSwitchStateMap == null) {
            ArrayMap<String,Field> tempDebugSwitchMap = new ArrayMap<>();
            Field[] fields = ActivityManagerDebugConfig.class.getDeclaredFields();
            for (Field f : fields) {
                if (f.getType().getName().equals("boolean") && f.getName().startsWith("DEBUG"))
                    tempDebugSwitchMap.put(f.getName(), f);
            }
            sDebugSwitchStateMap = tempDebugSwitchMap;
        }
    }

    static void setBooleanFromFieldSilently(@Nullable PrintWriter pw, Field field, boolean enabled) {
        try {
            field.setBoolean(null, enabled);
        } catch (IllegalAccessException e) {
            printIfNeeded(pw,"throw the IllegalAccessException when setSwitchEnabled "
                    + e.toString() + "\n" + e.getStackTrace());
        }
    }

    static boolean getBooleanFromFieldSilently(@NonNull PrintWriter pw, Field field) {
        boolean enabled = false;
        try {
           enabled  = field.getBoolean(null);
        } catch (IllegalAccessException e) {
            printIfNeeded(pw,"throw the IllegalAccessException when setSwitchEnabled "
                    + e.toString() + "\n" + e.getStackTrace());
        }
        return enabled;
    }

    static void setSwitchEnabled(@NonNull String secondInput, boolean enabled, @Nullable PrintWriter pw) {
        ensureMapIfNeeded(pw);
        Field field = sDebugSwitchStateMap.get(secondInput);
        if (field != null) {
            setBooleanFromFieldSilently(pw, field, enabled);
            printIfNeeded(pw, "\n" + secondInput + " SWITCH was set " + enabled + "\n");
        } else {
            printIfNeeded(pw,"\nSorry,your parameter was unsupported: " + secondInput + "\n");
        }
    }

    static void listSwitchState(@Nullable String secondInput, @NonNull PrintWriter pw) {
        ensureMapIfNeeded(pw);
        if (secondInput != null){
            //only list the needed Debug state
            Field field = sDebugSwitchStateMap.get(secondInput);
            if (field != null)
                pw.println("\n" + field.getName() + " = "
                        + getBooleanFromFieldSilently(pw, field) + "\n");
            else
                pw.println("\nHi,please check your parameter: " + secondInput + "\n");
        }else {
            //list all the Debug can be operated
            for (int i=0;i < sDebugSwitchStateMap.size();i++) {
                pw.println("\t" + sDebugSwitchStateMap.keyAt(i) + " = "
                        + getBooleanFromFieldSilently(pw, sDebugSwitchStateMap.valueAt(i)));
            }
        }
    }

    static void resetAllSwitchState(@NonNull PrintWriter pw) {
        ensureMapIfNeeded(pw);
        for (int i=0;i < sDebugSwitchStateMap.size();i++) {
            setBooleanFromFieldSilently(pw, sDebugSwitchStateMap.valueAt(i),false);
        }
        pw.println("\nAll the Log_switch have been reset false\n");
    }

    static String buildEnabledSwitchesString(@NonNull PrintWriter pw) {
        String enabledSwitchesString = "";
            for (String key : sDebugSwitchStateMap.keySet()) {
                if (getBooleanFromFieldSilently(pw, sDebugSwitchStateMap.get(key))) {
                    enabledSwitchesString += key + ",";
                }
            }
        return enabledSwitchesString;
    }

    static void saveDebugSwtichStates(@NonNull ContentResolver contentResolver, @NonNull PrintWriter pw) {
        ensureMapIfNeeded(pw);
        long ident = Binder.clearCallingIdentity();
        Settings.Global.putString(contentResolver, "ams_enabled_debug_switches", buildEnabledSwitchesString(pw));
        Binder.restoreCallingIdentity(ident);
        pw.println("\nAll the Log_switch state have been saved\n");
    }

    static void showLogSwitchHelp(@NonNull PrintWriter pw) {
        pw.println("usage:");
        pw.println("\tenable <DEBUG_NAME> : only open the switch of DEBUG_NAME.");
        pw.println("\tdisable <DEBUG_NAME> : only close the switch of DEBUG_NAME.");
        pw.println("\tlist [DEBUG_NAME] : list all the debug state,optionally passing DEBUG_NAME only to list the needed.");
        pw.println("\treset : set all the debug state to false.");
        pw.println("\tsave_state : save the debug state to storage zone and read from AMS again when the phone restart. \n");
    }

    static List<String> parseEnabledSwitchKeys(String switchKeys) {
        List<String> enabledDebugSwitchesList = new ArrayList<>();
        if (switchKeys != null  && switchKeys.length() > 0) {
            for (String key : switchKeys.split(",")) {
                enabledDebugSwitchesList.add(key);
            }
        }
        return enabledDebugSwitchesList;
    }

    static void restoreDebugSwitchStates(@NonNull ContentResolver contentResolver) {
        String enabledDebugSwitches = Settings.Global.getString(contentResolver, "ams_enabled_debug_switches");
        List<String> enabledDebugSwitchesList = parseEnabledSwitchKeys(enabledDebugSwitches);
            if (enabledDebugSwitchesList.size() > 0) {
                for (String key : enabledDebugSwitchesList) {
                    setSwitchEnabled(key,true,/*PrintWriter*/null);
                }
            }
    }

}
