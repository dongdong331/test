/*
 * Copyright © 2017 Spreadtrum Communications Inc.
 */
package android.app;

//for processRecord
public class ProcessInfo {

    // for activity state change:
    public static final int ACTIVITY_STATE_START = 0;
    public static final int ACTIVITY_STATE_STOP = 1;
    public static final int ACTIVITY_STATE_PAUSE = 2;
    public static final int ACTIVITY_STATE_RESUME = 3;
    public static final int ACTIVITY_STATE_FINISH = 4;
    public static final int ACTIVITY_STATE_LAUNCHDONE = 5;
    public static final int ACTIVITY_STATE_PROC_START = 6;

    public static final int PROCESS_LMK_ADJ_TUNNING_INCREASE = 0;
    public static final int PROCESS_LMK_ADJ_TUNNING_DECREASE = 1;
    public static final int PROCESS_LMK_ADJ_TUNNING_NONEED = 2;
    //keep sync with ProcessList:
    public static final int CACHED_APP_MAX_ADJ = 906;
    public static final int CACHED_APP_MIN_ADJ = 900;

    // The B list of SERVICE_ADJ -- these are the old and decrepit
    // services that aren't as shiny and interesting as the ones in the A list.
    public static final int SERVICE_B_ADJ = 800;

    // This is the process of the previous application that the user was in.
    // This process is kept above other things, because it is very common to
    // switch back to the previous app.  This is important both for recent
    // task switch (toggling between the two top recent apps) as well as normal
    // UI flow such as clicking on a URI in the e-mail app to view in the browser,
    // and then pressing back to return to e-mail.
    public static final int PREVIOUS_APP_ADJ = 700;

    // This is a process holding the home application -- we want to try
    // avoiding killing it, even if it would normally be in the background,
    // because the user interacts with it so much.
    public static final int HOME_APP_ADJ = 600;

    // This is a process holding an application service -- killing it will not
    // have much of an impact as far as the user is concerned.
    public static final int SERVICE_ADJ = 500;

    // This is a process with a heavy-weight application.  It is in the
    // background, but we want to try to avoid killing it.  Value set in
    // system/rootdir/init.rc on startup.
    public static final int HEAVY_WEIGHT_APP_ADJ = 400;

    // This is a process currently hosting a backup operation.  Killing it
    // is not entirely fatal but is generally a bad idea.
    public static final int BACKUP_APP_ADJ = 300;

    // This is a process only hosting components that are perceptible to the
    // user, and we really want to avoid killing them, but they are not
    // immediately visible. An example is background music playback.
    public static final int PERCEPTIBLE_APP_ADJ = 200;

    // This is a process only hosting activities that are visible to the
    // user, so we'd prefer they don't disappear.
    public static final int VISIBLE_APP_ADJ = 100;
    public static final int VISIBLE_APP_LAYER_MAX = PERCEPTIBLE_APP_ADJ - VISIBLE_APP_ADJ - 1;

    // This is the process running the current foreground app.  We'd really
    // rather not kill it!
    public static final int FOREGROUND_APP_ADJ = 0;

    // This is a process that the system or a persistent process has bound to,
    // and indicated it is important.
    public static final int PERSISTENT_SERVICE_ADJ = -700;

    // This is a system persistent process, such as telephony.  Definitely
    // don't want to kill it, but doing so is not completely fatal.
    public static final int PERSISTENT_PROC_ADJ = -800;

    // The system process runs at the default adjustment.
    public static final int SYSTEM_ADJ = -900;

    // Activity manager's version of Process.THREAD_GROUP_BG_NONINTERACTIVE
    public static final int SCHED_GROUP_BACKGROUND = 0;
    // Activity manager's version of Process.THREAD_GROUP_DEFAULT
    public static final int SCHED_GROUP_DEFAULT = 1;
    // Activity manager's version of Process.THREAD_GROUP_TOP_APP
    public static final int SCHED_GROUP_TOP_APP = 2;
    // Activity manager's version of Process.THREAD_GROUP_TOP_APP
    // Disambiguate between actual top app and processes bound to the top app
    public static final int SCHED_GROUP_TOP_APP_BOUND = 3;

    public final static String KEY_PKGNAME = "pkgname";
    public final static String KEY_CLSNAME = "clsname";
    public final static String KEY_ACTIVITY_STATE = "activity_state";
    public final static String KEY_LAUNCH_TIME = "launchTime";
    public final static String KEY_FULLSCREEN = "fullscreen";
    public final static String KEY_START_PROC_HOST_TYPE = "hostingtype";
    public final static String KEY_START_PROC_PID = "pid";

    //Boost app scene
    public final static int TYPE_NONE = 0;
    public final static int TYPE_CONFIG_CHANGE = 1;

    public String packageName;
    public String processName;
    public int pid;
    public int uid;
    public int curAdj;
    public String curAdjType;
    public long lastPss;
    public int flags;
    public int curProcState;
    public int curSchedGroup;
    //store the tunning result
    public boolean adjTunned;
    public int tunnedAdj;
    public String tunnedAdjType;
    public int tunnedProcState;
    public int tunnedSchedGroup;


    public ProcessInfo() {
    }

    public void resetTunningParams() {
        adjTunned = false;
        tunnedAdj = curAdj;
        tunnedAdjType = curAdjType;
        tunnedProcState = curProcState;
        tunnedSchedGroup = curSchedGroup;
    }

    @Override
    public String toString() {
        return "Process:" + processName + " pkg:" + packageName+" pid:"+ pid +"adj" +curAdj;
    }
}