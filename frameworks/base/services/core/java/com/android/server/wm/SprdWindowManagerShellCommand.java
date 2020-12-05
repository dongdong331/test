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

package com.android.server.wm;

import android.graphics.Bitmap;
import android.graphics.GraphicBuffer;
import android.graphics.Rect;
import android.os.Build;
import android.os.RemoteException;
import android.os.ShellCommand;
import android.view.SurfaceControl;
import com.android.server.wm.WindowManagerShellCommand;
import android.util.Slog;

import java.lang.reflect.Field;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.OutputStream;
import java.util.ArrayList;

import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;
import static android.os.Build.IS_USER;

class SprdWindowManagerShellCommand extends WindowManagerShellCommand {

    final WindowManagerService mService;

    SprdWindowManagerShellCommand(WindowManagerService service) {
        super(service);
        mService = service;
    }

    static String fullClassName[] = {"com.android.server.wm.WindowManagerDebugConfig",
                "com.android.server.policy.PhoneWindowManager"};
    static String shortClassName[] = {"s", "p"};

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(cmd);
        }
        PrintWriter pw = getOutPrintWriter();
        // should only do this in debug mode.
        if (IS_USER) {
            return super.onCommand(cmd);
        }
        try {
            switch (cmd) {
                case "p":
                    return setTag(pw, 1);
                case "s":
                    return setTag(pw, 0);
                case "t":
                    return setThreadPriorities(pw);
                case "c":
                    return captureWindow(pw);
                case "size":
                case "density":
                case "overscan":
                case "scaling":
                case "dismiss-keyguard":
                case "tracing":
                    return super.onCommand(cmd);
                default:
                    return handleDefaultCommands(cmd);
            }
        } catch (Exception e) {
            pw.println("Exception: " + e);
        }
        return -1;
    }

    public int setThreadPriorities(PrintWriter pw) {
        String dispatchValueStr = getNextArgRequired();
        String deliveryValueStr = getNextArgRequired();
        int dispatchValue = Integer.parseInt(dispatchValueStr);
        int deliveryValue = Integer.parseInt(deliveryValueStr);
        mService.setThreadPriorities(dispatchValue, deliveryValue);
        return 0;
    }

    public int setTag(PrintWriter pw, int cl) {
        //must contain 2 args
        String cmd = getNextArgRequired();
        String valueStr = getNextArgRequired();
        boolean value = false;
        Slog.e(TAG_WM, shortClassName[cl] + ", cmd = " + cmd + " value = " + valueStr);

        if ("0".equals(valueStr) || "false".equalsIgnoreCase(valueStr)) {
            value = false;
        } else if ("1".equals(valueStr) || "true".equalsIgnoreCase(valueStr)) {
            value = true;
        } else {
            pw.println("wrong usage of " + shortClassName[cl]
                    + " debug flag, try using 1/0/true/false");
            return -1;
        }

        Class cls = null;
        Field field = null;
        try {
            cls = Class.forName(fullClassName[cl]);
            field = cls.getDeclaredField(cmd);
            boolean access = field.isAccessible();
            field.setAccessible(true);
            field.setBoolean(null, value);
            field.setAccessible(access);
        } catch (Exception e) {
            pw.println("Exception when get " + shortClassName[cl] + ", or accessing wrong param");
            return -1;
        }
        return 0;
    }

    int captureWindow(PrintWriter pw) {
        String windowName = getNextArg();
        if (windowName == null) {
            pw.println("Wrong usage of captureWindow");
            return -1;
        }
        String saveFileName = getNextArg();
        if (saveFileName == null) {
            pw.println("use the default file path name /data/misc/wmtrace/a.png");
            saveFileName = "/data/misc/wmtrace/a.png";
        }
        pw.println("WindowName = " + windowName);
        final ArrayList<WindowState> windows = new ArrayList();
        GraphicBuffer buffer;
        synchronized(mService.mWindowMap) {
            mService.mRoot.getWindowsByName(windows, windowName);
            if (windows.size() <= 0) {
                pw.println("can not find the window " + windowName);
                return -1;
            }
            if (windows.size() > 1) {
                pw.println("Warning the window is more than one,"
                    + " so the capture layer may not you want window size = " + windows.size());
            }
            WindowState ws = windows.get(0);
            final Rect bounds = ws.getBounds();
            bounds.offsetTo(0, 0);
            pw.println("window bounds=" + bounds);

            buffer = SurfaceControl.captureLayers(
                    ws.getSurfaceControl().getHandle(), bounds, 1 /* frameScale */);
        }

        if (buffer == null) {
            pw.println("Fail to capture the " + windowName + "'s layer when call  captureLayers");
            return -1;
        }
        Bitmap bitmap = Bitmap.createHardwareBitmap(buffer);
        try {
            OutputStream out = new FileOutputStream(saveFileName);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            if (bitmap != null) {
                bitmap.recycle();
            }
        } catch (Exception e) {
            pw.println("something wrong when save screencap to " + saveFileName);
            return -1;
        }

        return 0;
    }

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();
        dumpHelp(pw);
    }

    static void dumpHelp(PrintWriter pw) {
        pw.println("Window manager (window) commands:");
        pw.println("  help");
        pw.println("    Print this help text.");
        if(!IS_USER) {
            pw.println("  s XXX 1 //or 0, true, false");
            pw.println("    Set WindowManagerDebugConfig debug param to true/false.");
            pw.println("  p XXX 1 //or 0, true, false");
            pw.println("    Set PhoneWindowManager debug param to true/false.");
            pw.println("  t XXX XXX //dispatcMs  deliveryMs");
            pw.println("    set Slow DispatchThres hold XXX Ms and deliveryThres XXX Ms.");
            pw.println("  c windowName /data/misc/wmtrace/a.png");
            pw.println("    capture the specific window's layer to bimmap");
        }
        pw.println("  size [reset|WxH|WdpxHdp]");
        pw.println("    Return or override display size.");
        pw.println("    width and height in pixels unless suffixed with 'dp'.");
        pw.println("  density [reset|DENSITY]");
        pw.println("    Return or override display density.");
        pw.println("  overscan [reset|LEFT,TOP,RIGHT,BOTTOM]");
        pw.println("    Set overscan area for display.");
        pw.println("  scaling [off|auto]");
        pw.println("    Set display scaling mode.");
        pw.println("  dismiss-keyguard");
        pw.println("    Dismiss the keyguard, prompting user for auth if necessary.");
        if (!IS_USER) {
            pw.println("  tracing (start | stop)");
            pw.println("    Start or stop window tracing.");
        }
    }
}

