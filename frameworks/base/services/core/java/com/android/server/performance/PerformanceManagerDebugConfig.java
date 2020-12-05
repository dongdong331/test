package com.android.server.performance;

public class PerformanceManagerDebugConfig {
    public static String TAG = "performance";
    public static boolean DEBUG_ALL = false;
    public static boolean DEBUG_SERVICE = DEBUG_ALL || false;
    public static boolean DEBUG_USRHABIT = DEBUG_ALL || false;
    public static boolean DEBUG_PROCSTAT = DEBUG_ALL || false;
    public static boolean DEBUG_TASKSNAPSHOT = DEBUG_ALL || false;
    public static boolean DEBUG_PRIOADJ = DEBUG_ALL || false;
    public static boolean DEBUG_RAMPOLICY = DEBUG_ALL || false;
    public static boolean DEBUG_CPUSTAT = DEBUG_ALL || false;
    public static boolean DEBUG_EXTRAFETCH = DEBUG_ALL || false;

    public static void enableDebug(boolean enable) {
        DEBUG_ALL = enable;
        DEBUG_SERVICE = DEBUG_ALL || false;
        DEBUG_USRHABIT = DEBUG_ALL || false;
        DEBUG_PROCSTAT = DEBUG_ALL || false;
        DEBUG_TASKSNAPSHOT = DEBUG_ALL || false;
        DEBUG_PRIOADJ = DEBUG_ALL || false;
        DEBUG_RAMPOLICY = DEBUG_ALL || false;
        DEBUG_CPUSTAT = DEBUG_ALL || false;
        DEBUG_EXTRAFETCH = DEBUG_ALL || false;
    }

    public static void handleDebugSwitch(String[] args, boolean debug) {
        int opti = 0;
        boolean debugAll = false;
        boolean debugUserHabit = false;
        boolean debugService = false;
        boolean debugProcStat = false;
        boolean debugTaskSnapShot = false;
        boolean debugPrioAdj = false;
        boolean debugRamPolicy = false;
        boolean debugCpuStat = false;
        boolean debugExtraFetch = false;
        while (opti < args.length) {
            String opt = args[opti];
            if (opt == null || opt.length() <= 0) {
                break;
            }
            opti++;
            if ("usrhabit".equals(opt)) {
                debugUserHabit = debug;
            } else if ("service".equals(opt)) {
                debugService = debug;
            } else if ("procstat".equals(opt)) {
                debugProcStat = debug;
            } else if ("prioadj".equals(opt)) {
                debugPrioAdj = debug;
            } else if ("rampolicy".equals(opt)) {
                debugRamPolicy = debug;
            } else if ("cpustat".equals(opt)) {
                debugCpuStat = debug;
            } else if ("extrafetch".equals(opt)) {
                debugExtraFetch = debug;
            } else if ("all".equals(opt)) {
                debugAll = debug;
            }
        }

        DEBUG_ALL = debugAll;
        DEBUG_SERVICE = debugAll || debugService;
        DEBUG_USRHABIT = debugAll || debugUserHabit;
        DEBUG_PROCSTAT = debugAll || debugProcStat;
        DEBUG_TASKSNAPSHOT = debugAll || debugTaskSnapShot;
        DEBUG_PRIOADJ = debugAll || debugPrioAdj;
        DEBUG_RAMPOLICY = debugAll || debugRamPolicy;
        DEBUG_CPUSTAT = debugAll || debugCpuStat;
        DEBUG_EXTRAFETCH = debugAll || debugExtraFetch;
    }
}