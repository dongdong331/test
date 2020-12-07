/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.sprd.quickstepext.locktask;

import android.content.Context;
import android.content.SharedPreferences;
import com.android.launcher3.Utilities;
import com.android.systemui.shared.recents.model.Task;

public class TaskLockStatus {

    public static boolean isSavedUnlockedTask(Context context, Task task) {
        String stringKey = makeTaskStringKey(task);

        if (null == stringKey) {
            return false;
        }
        SharedPreferences sharedPref = Utilities.getPrefs(context.getApplicationContext());
        return null != sharedPref && sharedPref.contains(stringKey);
    }

    public static void removeLockState(Context context, Task task) {
        SharedPreferences sharedPref = Utilities.getPrefs(context.getApplicationContext());
        String stringKey = makeTaskStringKey(task);
        if (null != stringKey && null != sharedPref
                && sharedPref.contains(stringKey)) {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.remove(stringKey);
            editor.apply();
        }
    }

    public static void setLockState(Context context, Task task, boolean isLocked) {
        String stringKey = makeTaskStringKey(task);
        if (null == stringKey) {
            return;
        }
        SharedPreferences sharedPref = Utilities.getPrefs(context.getApplicationContext());
        SharedPreferences.Editor editor = sharedPref.edit();

        if (isLocked) {
            editor.putInt(stringKey, 0);
            editor.apply();
        } else {
            editor.remove(stringKey);
            editor.apply();
        }
    }

    private static String makeTaskStringKey(Task task) {
        if (null == task || null == task.key || task.key.baseIntent.getComponent() == null) {
            return null;
        }
        return "Task.Key: " + task.key.id + ", " + "u: " + task.key.userId + ", "
                + task.key.baseIntent.getComponent().getPackageName();
    }
}
