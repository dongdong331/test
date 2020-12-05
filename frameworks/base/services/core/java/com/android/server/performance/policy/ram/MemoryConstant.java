package com.android.server.performance.policy.ram;

import android.os.SystemProperties;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.HashMap;

import com.android.server.performance.PolicyConfig;
import com.android.server.performance.status.RamStatus;

import android.util.Slog;

/**
 * Created by SPREADTRUM\joe.yu on 8/1/17.
 */

public class MemoryConstant {

    public static final String TAG = "MemoryConstant";

    private static String TAG_AVG_APP_USS = "averageAppUss";
    private static String TAG_LOW_CPU_LOAD = "lowCpuLoad";
    private static String TAG_HIGH_CPU_LOAD = "highCpuLoad";
    private static String TAG_EMER_MEM = "emergencyMemory";
    private static String TAG_BIG_CRITIAL_MEM = "bigMemCriticalMemory";
    private static String TAG_DEF_CRITIAL_MEM = "defaultCriticalMemory";
    private static String TAG_NORMAL_MEM = "normalMemory";
    private static String TAG_FS_MEM = "fsMemory";
    private static String TAG_FS_IDLE = "fsIdleTime";
    private static String TAG_SWAP_USAGE = "swapUsage";
    private static String TAG_BG_PKG_LIMIT = "bgPkgLimit";
    private static String TAG_PROTECT_LRU_LIMIT = "protect_lru_limit";
    private PolicyConfig.ConfigItem mConfig;

    private static class ForceStopParams {
        public long fsMemory;
        public long fsIdleTime;
        public int swapUsage;
    }
    // default for 1g
    long averageAppUss = 20; // in MB
    long lowCpuLoad = 30;
    long highCpuLoad = 60;
    long emergencyMemory = 350; // in MB
    long bigMemCriticalMemory = 400;
    long defaultCriticalMemory = 600;
    long normalMemory = 900;
    ArrayList<ForceStopParams> mFsParams = new ArrayList<>();
    int backgroundRunningLimit = 5;

    String[] proctectLruLimit;

    public long getCurrentForceStopIdleTime(long currentMem) {
        if (mFsParams.size() == 0) {
            return -1;
        }
        //quick:
        if (mFsParams.get(mFsParams.size() -1).fsMemory < currentMem) {
            return -1;
        }
        for (ForceStopParams param : mFsParams) {
            if (param.fsMemory > currentMem) {
                return param.fsIdleTime;
            }
        }
        return -1;
    }

    public long getCurrentForceStopIdleTime (int swapUsage) {
        if (mFsParams.size() == 0) {
            return -1;
        }
        //quick:
        if (mFsParams.get(mFsParams.size() -1).swapUsage == -1 ||
            mFsParams.get(mFsParams.size() -1).swapUsage > swapUsage) {
            return -1;
        }
        for (ForceStopParams param : mFsParams) {
            if (param.swapUsage <= swapUsage) {
                return param.fsIdleTime;
            }
        }
        return -1;
    }

    public boolean isVmpressureListenerEnabled() {
        if (mFsParams.size() == 0) {
            return false;
        }

        return mFsParams.get(mFsParams.size() -1).swapUsage != -1;

    }
    public int getBackgroundPkgRunningLimit() {
        return backgroundRunningLimit;
    }
    public long getAverageAppUss() {
        return averageAppUss;
    }

    public long getLowCpuLoad() {
        return lowCpuLoad;
    }

    public long getHighCpuLoad() {
        return highCpuLoad;
    }

    public long getEmergencyMemory() {
        return emergencyMemory;
    }

    public long getBigMemCriticalMemory() {
        return bigMemCriticalMemory;
    }

    public long getDefaultCriticalMemory() {
        return defaultCriticalMemory;
    }

    public long getNormalMemory() {
        return normalMemory;
    }

    public String[] getProctectLruLimit() {
        return proctectLruLimit;
    }

    public void dump() {
        if (mConfig != null) {
            HashMap<String, String> map = mConfig.getAllString();
            if (map != null) {
                for (String key : map.keySet()) {
                    Log.d(TAG, "--->" + key + "  : " + map.get(key));
                }
            }
        }
    }

    public void dump(PrintWriter pw) {
        pw.println("Dump MemoryConstant:");
        pw.println("----------------------------");
        if (mConfig != null) {
            HashMap<String, String> map = mConfig.getAllString();
            if (map != null) {
                for (String key : map.keySet()) {
                    pw.println("--->" + key + "  : " + map.get(key));
                }
            }
        }
        pw.println("----------------------------");
    }

    public static MemoryConstant loadFromConfig(PolicyConfig config, int ramsize) {
        MemoryConstant memoryConstant = new MemoryConstant();
        PolicyConfig.ConfigItem item = config.getConfigItem(String.valueOf(ramsize));
        if (item == null) {
            item = config.getDefaultItem();
        }
        if (item != null) {
            try {
                memoryConstant.averageAppUss = Long.valueOf(item
                        .getString(TAG_AVG_APP_USS));
                memoryConstant.lowCpuLoad = Long.valueOf(item
                        .getString(TAG_LOW_CPU_LOAD));
                memoryConstant.highCpuLoad = Long.valueOf(item
                        .getString(TAG_HIGH_CPU_LOAD));
                memoryConstant.emergencyMemory = Long.valueOf(item
                        .getString(TAG_EMER_MEM));
                memoryConstant.bigMemCriticalMemory = Long.valueOf(item
                        .getString(TAG_BIG_CRITIAL_MEM));
                memoryConstant.defaultCriticalMemory = Long.valueOf(item
                        .getString(TAG_DEF_CRITIAL_MEM));
                memoryConstant.normalMemory = Long.valueOf(item
                        .getString(TAG_NORMAL_MEM));
                String [] temp1 = item.getString(
                        TAG_FS_MEM).split(" ");
                String [] temp2 = item.getString(
                        TAG_FS_IDLE).split(" ");
                String temp3 = item.getString(
                        TAG_SWAP_USAGE);
                String [] swapUsage = new String[10];
                if (temp3 != null) {
                    swapUsage = temp3.split(" ");
                }
                if (temp1 != null && temp2 != null
                    && temp1.length == temp2.length) {

                    for (int i = 0; i < temp1.length; i++) {
                        ForceStopParams param = new ForceStopParams();
                        param.fsMemory = Long.valueOf(temp1[i]);
                        param.fsIdleTime = 1000 * 60 * Long.valueOf(temp2[i]);
                        param.swapUsage = -1;
                        if (swapUsage != null && (swapUsage.length == temp1.length)) {
                            param.swapUsage = Integer.valueOf(swapUsage[i]);
                        }
                        memoryConstant.mFsParams.add(param);
                    }
                    Collections.sort(memoryConstant.mFsParams, new Comparator<ForceStopParams>() {
                        @Override
                        public int compare(ForceStopParams lhs, ForceStopParams rhs) {
                            if (lhs.fsMemory == rhs.fsMemory) {
                                return 0;
                            }
                            return lhs.fsMemory <= rhs.fsMemory ? -1 : 1;
                        }
                    });
                }
                memoryConstant.proctectLruLimit = item.getString(
                        TAG_PROTECT_LRU_LIMIT).split("");
                memoryConstant.backgroundRunningLimit = Integer.valueOf(item
                        .getString(TAG_BG_PKG_LIMIT));
                memoryConstant.mConfig = item;
                return memoryConstant;
            } catch (Exception e) {
                return null;
            }
        }
        return memoryConstant;
    }
}
