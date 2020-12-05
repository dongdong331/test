package com.android.server.performance.status;

import android.os.StrictMode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Set;

/**
 * Created by SPREADTRUM\joe.yu on 7/31/17.
 */

public class RamStatus {

    private String TAG_MEM_AVAILABLE = "MemAvailable"; // after K44
    private String TAG_MEM_FREE = "MemFree";
    private String TAG_MEM_TOTAL = "MemTotal";
    private String TAG_MEM_CACHED = "Cached";


    private HashMap<String,Long>mMeminfoMap = new HashMap<>();




    public RamStatus() {
        resetMemMap();
    }

    public void updateMemInfo() {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        update();
        StrictMode.setThreadPolicy(oldPolicy);
    }

    private void resetMemMap () {
        synchronized (mMeminfoMap) {
            mMeminfoMap.clear();
            mMeminfoMap.put(TAG_MEM_AVAILABLE, 0l);
            mMeminfoMap.put(TAG_MEM_FREE, 0l);
            mMeminfoMap.put(TAG_MEM_TOTAL, 0l);
            mMeminfoMap.put(TAG_MEM_CACHED, 0l);
        }
    }

    public int getRamConfig() {
        int ramSize = 1024;
        int totalMem = (int) (getTotalMemKb() / 1024);
        if (totalMem > 0 && totalMem <= 512) {
            ramSize = 512;
        } else if (totalMem > 512 && totalMem <= 768) {
            ramSize = 768;
        } else if (totalMem > 768 && totalMem <= 1024) {
            ramSize = 1024;
        } else if (totalMem > 1024 && totalMem <= 2048) {
            ramSize = 2048;
        } else if (totalMem > 2048 && totalMem <= 3072) {
            ramSize = 3072;
        } else if (totalMem > 3072) {
            ramSize = 4096;
        }
        return ramSize;
    }

    private void update() {
        // for /proc/meminfo
        resetMemMap();
        BufferedReader reader = null;
        synchronized (mMeminfoMap) {
            try {
                reader = new BufferedReader(new FileReader(new File("/proc/meminfo")));
                String info;
                while ((info = reader.readLine()) != null) {
                    for (String key : mMeminfoMap.keySet()) {
                        if (info.startsWith(key)) {
                            String tmp = info.replace(key + ":", "").replace("kB", "").trim();
                            Long mem = Long.parseLong(tmp);
                            mMeminfoMap.put(key, mem);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public long getAvailableMemKb() {
        synchronized (mMeminfoMap) {
            Long avail = mMeminfoMap.get(TAG_MEM_AVAILABLE);
            return avail;
        }
    }
    public long getFreeMemKb() {
        synchronized (mMeminfoMap) {
            Long free = mMeminfoMap.get(TAG_MEM_FREE);
            return free;
        }
    }
    public long getTotalMemKb() {
        synchronized (mMeminfoMap) {
            Long total = mMeminfoMap.get(TAG_MEM_TOTAL);
            return total;
        }
    }
    public long getCachedMemKb() {
        synchronized (mMeminfoMap) {
            Long cached = mMeminfoMap.get(TAG_MEM_CACHED);
            return cached;
        }
    }
}
