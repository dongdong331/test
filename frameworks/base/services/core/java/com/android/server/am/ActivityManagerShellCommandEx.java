/*
 * The Spreadtrum Communication Inc. 2016
 */

package com.android.server.am;

import static com.android.server.am.ActivityManagerDebugConfigDynamics.setSwitchEnabled;
import static com.android.server.am.ActivityManagerDebugConfigDynamics.listSwitchState;
import static com.android.server.am.ActivityManagerDebugConfigDynamics.resetAllSwitchState;
import static com.android.server.am.ActivityManagerDebugConfigDynamics.saveDebugSwtichStates;
import static com.android.server.am.ActivityManagerDebugConfigDynamics.showLogSwitchHelp;

import android.content.ContentResolver;
import android.os.RemoteException;
import android.util.Slog;

import java.io.PrintWriter;

class ActivityManagerShellCommandEx extends ActivityManagerShellCommand {
    private static final String TAG = "ActivityManager.Shell";

    ContentResolver mContentResolver;

    ActivityManagerShellCommandEx(ActivityManagerService service, boolean dumping) {
        super(service, dumping);
        mContentResolver = service.mContext.getContentResolver();
    }

    int runLmkForceStop(PrintWriter pw) {
        if (mInternal != null
                && mInternal instanceof ActivityManagerServiceEx) {
            ActivityManagerServiceEx service = (ActivityManagerServiceEx) mInternal;
            if (service.mLmkTracker != null) {
                String opt = null;
                if ((opt = getNextArgRequired()) != null) {
                    int pid = 0;
                    try {
                        pid = Integer.parseInt(opt);
                    } catch (Exception e) {
                        return -1;
                    }
                    if (pid > 0) {
                        service.mLmkTracker.doLmkForceStop(pid);
                        return 0;
                    }
                }
            } else {
                Slog.e(TAG, "lmk-force-stop failed, lmk tracker is null");
            }
        } else {
            Slog.e(TAG, "lmk-force-stop failed, AmsEx not inherited");
        }
        return -1;
    }

    int runLogSwitch(PrintWriter pw) {
        String firstInput = getNextArgRequired();
        String secondInput;
        if (firstInput != null){
            switch (firstInput) {
                case "enable":
                    secondInput = getNextArgRequired();
                    setSwitchEnabled(secondInput, true, pw);
                    break;
                case "disable":
                    secondInput = getNextArgRequired();
                    setSwitchEnabled(secondInput, false, pw);
                    break;
                case "list":
                    secondInput = getNextArg();
                    listSwitchState(secondInput, pw);
                    break;
                case "reset":
                    resetAllSwitchState(pw);
                    break;
                case "save_state":
                    saveDebugSwtichStates(mContentResolver, pw);
                    break;
                case "--help":
                    showLogSwitchHelp(pw);
                    break;
                default:
                    pw.println("\nplease input the effective command!And use '--help' to acquire the help information\n");
                    return -1;
            }
        }

        return 0;
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(cmd);
        }
        PrintWriter pw = getOutPrintWriter();
        try {
            switch (cmd) {
                case "lmk-force-stop":
                    return runLmkForceStop(pw);
                case "log_switch":
                    return runLogSwitch(pw);
                default:
                    return super.onCommand(cmd);
            }
        } catch (Exception e) {
            pw.println("Exception: " + e);
        }
        return -1;
    }

}
