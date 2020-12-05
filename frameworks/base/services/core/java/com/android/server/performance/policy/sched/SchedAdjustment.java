package com.android.server.performance.policy.sched;

import android.os.Process;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.PrintWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;

import com.android.server.performance.PolicyConfig;

public class SchedAdjustment {
    public static final String TAG = "SchedAdjustment";
    public static final String TAG_PACKAGE = "package";
    public static final String TAG_PROCESS = "process";
    public static final String TAG_THREADS = "threads";
    public static final String TAG_PRIO = "prio";
    public static final String TAG_SCHEDCLASS = "schedClass";

    /*
                <item package="com.kiloo.subwaysurf">
                <process>com.kiloo.subwaysurf</process>
                <string-array name="threads">
                   <string>UnityMain</string>
                   <string>UnityGfxDeviceW</string>
                </string-array>
            </item>
    */
    public HashMap<String, SchedProcessInfo> mMap = new HashMap<>();

    public static class SchedThreadInfo {
        String thread;
        int schedClass;
        int prio;

        public String toString() {
            return "PID NAME:" + thread + "|schedClass:" + schedClass + "|prio" + prio;
        }
    }

    public static class SchedProcessInfo {
        String packageName;
        String processName;
        ArrayList<String> boostThreads = new ArrayList<>();
        ArrayList<String> boostLikeThreads = new ArrayList<>();
        HashMap<String, SchedThreadInfo> schedThreadInfos = new HashMap<>();

        public String toString() {
            String tids = "Threads:";
             for(String key : schedThreadInfos.keySet()){
                 SchedThreadInfo schedThreadInfo = schedThreadInfos.get(key);
                 tids = tids + schedThreadInfo.thread +"|schedClass:" + schedThreadInfo.schedClass + "|prio:" + schedThreadInfo.prio;
             }
            return "pkg:"+packageName+"|NAME:"+processName+"|"+tids;
        }
    }

    public static SchedAdjustment loadFromConfig(PolicyConfig config) {
        SchedAdjustment schedAdjustment = new SchedAdjustment();
        HashMap<String, PolicyConfig.ConfigItem> data = config.getAllConfigItems();
        if (data != null) {
            for (String key: data.keySet()) {
                PolicyConfig.ConfigItem item = data.get(key);
                SchedProcessInfo info  = new SchedProcessInfo();
                ArrayList<String> schedThreads = new ArrayList<>();
                info.packageName = key;
                info.processName = item.getString(TAG_PROCESS);
                schedThreads = item.getStringArray(TAG_THREADS);
                for (String schedThread : schedThreads) {
                    SchedThreadInfo threadInfo  = new SchedThreadInfo();
                    if (schedThread.contains(",")) {
                        String[] tmp = schedThread.split(",");
                        threadInfo.thread = tmp[0];
                        threadInfo.schedClass = transferSchedPolicy(tmp[1].trim());
                        threadInfo.prio = Integer.parseInt(tmp[2].trim());
                        info.schedThreadInfos.put(tmp[0], threadInfo);
                        if (tmp[0].endsWith("*")) {
                            info.boostLikeThreads.add(tmp[0].replace("*", "").trim());
                        } else {
                            info.boostThreads.add(tmp[0]);
                        }
                    }
                }
                if (info.processName != null && info.processName != null){
                    schedAdjustment.mMap.put(key, info);
                }
            }
            return schedAdjustment;
        }
        return null;
    }

    private static int transferSchedPolicy(String policy){
        int newPolicy = Process.SCHED_OTHER;
        if (policy.equals("other")) {
            newPolicy = Process.SCHED_OTHER;
        } else if (policy.equals("rr")) {
            newPolicy = Process.SCHED_RR;
        } else if (policy.equals("fifo")) {
            newPolicy = Process.SCHED_FIFO;
        }
        return newPolicy;
    }

    public boolean isPackageSupported(String pkg) {
        return mMap.get(pkg) != null;
    }

    public boolean isProcessSupported(String pkg, String proc) {
        SchedProcessInfo info = mMap.get(pkg);
        if (info != null) {
            return info.processName.equals(proc);
        }
        return false;
    }

    public boolean isThreadBoostNeeded(String pkg, String proc, String thread) {
        SchedProcessInfo info = mMap.get(pkg);
        if (info != null && info.processName.equals(proc) &&
            info.boostThreads.contains(thread)) {
            return true;
        }
        //handle '*' cases,GLThread-xxx -->  GLThread*
        if (info != null && info.processName.equals(proc)) {
            for (String t : info.boostLikeThreads) {
                if (thread.startsWith(t)) {
                    return true;
                }
            }
        }
        return false;
    }


    public void dump() {
        if (mMap != null) {
            for (String key : mMap.keySet()) {
                SchedProcessInfo info = mMap.get(key);
                if (info != null) {
                    Log.e(TAG, ""+info);
                }
            }
        }
    }

    public void dump(PrintWriter pw) {
        pw.println("Dump ScheduleAdjust:");
        pw.println("----------------------------");
        if (mMap != null) {
            for (String key : mMap.keySet()) {
                SchedProcessInfo info = mMap.get(key);
                if (info != null) {
                    pw.println(""+info);
                }
            }
        }
        pw.println("----------------------------");
    }
}
