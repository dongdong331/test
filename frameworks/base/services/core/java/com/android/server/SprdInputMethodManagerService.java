/*
 * Copyright (C) 2017 Sprdtrum.com
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

package com.android.server;

import com.android.internal.view.IInputContext;
import com.android.internal.view.IInputMethod;
import com.android.internal.view.IInputMethodClient;
import com.android.internal.view.InputMethodClient;
import android.content.Context;
import android.os.RemoteException;
import android.util.Printer;
import android.util.Slog;
import com.android.server.InputMethodManagerService;
import java.io.FileDescriptor;
import java.io.PrintWriter;


/** {@hide} */
public class SprdInputMethodManagerService extends InputMethodManagerService {
    public String[] mArgs;
    public int mNextArg;

    public SprdInputMethodManagerService(Context context) {
        super(context);
        Slog.i("InputMethodMangerService","init SprdInputMethodManagerServices");
    }
    /* SPRD: Changed for switch log on/off in the runtime. @ { */
    public void handleDebugCmd(FileDescriptor fd, PrintWriter pw, String option, String[] args) {
        mNextArg = 1;
        mArgs = args;
        if ("-d".equals(option)) {
            String action = nextArg();
            if ("enable".equals(action)) {
                runDebug(fd, pw, true);
            } else if ("disable".equals(action)) {
                runDebug(fd, pw, false);
            } else {
                printUsage(pw);
            }
        } else if ("-h".equals(option)) {
            printUsage(pw);
        } else {
            pw.println("Unknown argument: " + option + "; use -h for help");
        }
    }
    private void runDebug(FileDescriptor fd, PrintWriter pw, boolean enable) {
        String[] args = new String[1];

        for (String type; (type = nextArg()) != null; ) {
            if ("0".equals(type)) {
                DEBUG = enable;
            } else if ("1".equals(type)) {
                args[0] = enable ? "enable" : "disable";
                runInputMethodServiceDebug(fd, pw, args);
            } else if ("2".equals(type)) {
                args[0] = enable ? "enable" : "disable";
                runInputMethodManagerDebug(fd, pw, args);
            } else {
                printUsage(pw);
                return;
            }
        }
    }
    private void printUsage(PrintWriter pw) {
        pw.println("Input method manager service dump options:");
        pw.println("  [-d] [-h] [cmd] [option] ...");
        pw.println("  -d enable <zone>          enable the debug zone");
        pw.println("  -d disable <zone>         disable the debug zone");
        pw.println("       zone list:");
        pw.println("         0 : InputMethodManagerService");
        pw.println("         1 : InputMethodService");
        pw.println("         2 : InputMethodManager");
        pw.println("  -h                        print the dump usage");
    }
    private void runInputMethodServiceDebug(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mCurMethod != null) {
            try {
                mCurMethod.asBinder().dump(fd, args);
            } catch (RemoteException e) {
                pw.println("Input method client dead: " + e);
            }
        }
    }
    private void runInputMethodManagerDebug(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mCurClient != null) {
            try {
               mCurClient.client.asBinder().dump(fd, args);
            } catch (RemoteException e) {
                pw.println("Input method client dead: " + e);
            }
        }
    }
    private String nextArg() {
        if (mNextArg >= mArgs.length) {
            return null;
        }

        return mArgs[mNextArg++];
    }
    /** @} **/
}

