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

package android.util;

import android.os.SystemClock;

/**
 * util for check time.
 * @hide
 */
public class CheckTime {
    private static final long ACTION_TIMEOUT = 200;

    private static final String TAG = "CheckTime";

    public static long getTime() {
        return SystemClock.elapsedRealtime();
    }

    /**
     * Check the action running duration, follow the ACTION_TIMEOUT default duration.
     * @return true if check failed.
     */
    public static boolean checkTime(long start, String action) {
        return checkTime(start, action, ACTION_TIMEOUT);
    }

    /**
     * Check the action running duration.
     * @return true if duration longer than timeout.
     */
    public static boolean checkTime(long start, String action, long timeout) {
        return checkTime(start, action, timeout, true);
    }

    /**
     * If the action executes timeout, print the log to inform developer the action
     * executing too slowly.
     * @param start Action starting time
     * @param action The action name
     * @param timeout The total time that the action should be done
     * @param app Whether the action execute in app process
     * @return If the action execute timeout return true, otherwise return false.
     */
    public static boolean checkTime(long start, String action, long timeout, boolean app) {
        long now = getTime();
        long cost = now - start;
        boolean failed = cost > timeout;
        if (failed) {
            // If we are taking a long time, log about it.
            if (app) {
                Log.w(TAG, "App running slow: Executing " + action + " took " + cost + "ms");
            } else {
                Slog.w(TAG, "System running slow: Executing " + action + " took " + cost + "ms");
            }
        }

        return failed;
    }

    /**
     * @hide
     */
    public static class System {
        public System() {
            throw new RuntimeException("You should never call new CheckTime.System().");
        }

        /**
         * See {@link android.util.CheckTime#checkTime}
         */
        public static boolean checkTime(long start, String action) {
            return CheckTime.checkTime(start, action, CheckTime.ACTION_TIMEOUT, false);
        }

        /**
         * See {@link android.util.CheckTime#checkTime}
         */
        public static boolean checkTime(long start, String action, long timeout) {
            return CheckTime.checkTime(start, action, timeout, false);
        }

    }
}
