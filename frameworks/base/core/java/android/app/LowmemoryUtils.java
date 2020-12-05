/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.app;

import android.os.RemoteException;

/**
 * Provide some brutal API for app to do something make itself
 * work fine in the extremely lowmemory case.
 * DO NOT misuse these interface.
 *
 */
public class LowmemoryUtils {

    //add for kill-stop front app process when phone call is incoming
    /**
     * Cancel kill stop front appllication action if it has not execute yet.
     */
    public static final int KILL_CONT_STOPPED_APP = 0;

    /**
     * Kill stop front application, that the kill action will not immediately
     * execute. Gernerally, it will be executed while the
     * ActivityManagerServiceEx$KILL_STOP_TIMEOUT_DELAY time is expired.
     */
    public static final int KILL_STOP_FRONT_APP = 1;

    /**
     * Cancel kill stop front appllication action if it has not execute yet.
     * Reserving for feature adding.
     */
    public static final int CANCEL_KILL_STOP_TIMEOUT = 2;

    /**
     * Kill stop front application or cancel the kill stop front application action.
     * Which action will be executed determined by the arg - func
     *
     * @param func What action you want to do.
     */
    public static void killStopFrontApp(int func) {
        try {
            ActivityManager.getService().killStopFrontApp(func);
        } catch (RemoteException e) {
            // System dead, we will be dead too soon!
        }
    }
}
