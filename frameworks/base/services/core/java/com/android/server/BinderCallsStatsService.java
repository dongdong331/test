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
 * limitations under the License
 */

package com.android.server;

import android.content.Context;
import android.os.Binder;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Slog;

import com.android.internal.os.BinderCallsStats;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public class BinderCallsStatsService extends SystemService {

    private static final String TAG = "BinderCallsStatsService";

    private final Context mContext;
    private static BinderService mBinderService;

    public BinderCallsStatsService(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onStart() {
        mBinderService = new BinderService();
        mBinderService.start();
    }

    public static void reset() {
        Slog.i(TAG, "Resetting stats");
        if (mBinderService != null) {
            mBinderService.reset();
        }
    }

    private final class BinderService extends Binder {

        private static final String TAG = "BinderCallsStatsServiceInner";

        private static final String PERSIST_SYS_BINDER_CALLS_DETAILED_TRACKING
            = "persist.sys.binder_calls_detailed_tracking";

        public void start() {
            ServiceManager.addService("binder_calls_stats", this);
            boolean detailedTrackingEnabled = SystemProperties.getBoolean(
                    PERSIST_SYS_BINDER_CALLS_DETAILED_TRACKING, false);

            if (detailedTrackingEnabled) {
                Slog.i(TAG, "Enabled CPU usage tracking for binder calls. Controlled by "
                        + PERSIST_SYS_BINDER_CALLS_DETAILED_TRACKING
                        + " or via dumpsys binder_calls_stats --enable-detailed-tracking");
                BinderCallsStats.getInstance().setDetailedTracking(true);
            }
        }

        public void reset() {
            Slog.i(TAG, "Resetting stats");
            BinderCallsStats.getInstance().reset();
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (!DumpUtils.checkDumpPermission(mContext, TAG, pw)) return;
            if (args != null) {
                for (final String arg : args) {
                    if ("-a".equals(arg)) {
                        // We currently dump all information by default
                        continue;
                    } else if ("--reset".equals(arg)) {
                        reset();
                        pw.println("binder_calls_stats reset.");
                        return;
                    } else if ("--enable-detailed-tracking".equals(arg)) {
                        SystemProperties.set(PERSIST_SYS_BINDER_CALLS_DETAILED_TRACKING, "1");
                        BinderCallsStats.getInstance().setDetailedTracking(true);
                        pw.println("Detailed tracking enabled");
                        return;
                    } else if ("--disable-detailed-tracking".equals(arg)) {
                        SystemProperties.set(PERSIST_SYS_BINDER_CALLS_DETAILED_TRACKING, "");
                        BinderCallsStats.getInstance().setDetailedTracking(false);
                        pw.println("Detailed tracking disabled");
                        return;
                    } else if ("-h".equals(arg)) {
                        pw.println("binder_calls_stats commands:");
                        pw.println("  --reset: Reset stats");
                        pw.println("  --enable-detailed-tracking: Enables detailed tracking");
                        pw.println("  --disable-detailed-tracking: Disables detailed tracking");
                        return;
                    } else {
                        pw.println("Unknown option: " + arg);
                    }
                }
            }
            BinderCallsStats.getInstance().dump(pw);
        }
    }
}
