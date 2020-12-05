package com.android.server.performance.policy.powerhint;

import android.os.SystemProperties;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.PowerHintVendorSprd;
import android.util.Log;
import android.util.Slog;

import java.io.PrintWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;

import com.android.server.performance.policy.ram.RamPolicyExecutor;
import com.android.server.performance.PolicyConfig;

/**
 * Created by SPREADTRUM\joe.yu on 8/1/17.
 */

public class PowerHintConstants {

    public  static final String TAG = "powerHintConstants";
    private static String TAG_PKGNAME = "pkgName";
    private static String TAG_PROC_NAME = "processName";
    private static String TAG_HINT_TYPE = "hintType";
    private static String TAG_HINT_DURATION = "duration";
    public static String SCEN_TYPE_INSTRMENTATIONCTS = "InStrumentationCTS";
    public static String SCEN_TYPE_INSTRMENTATIONGTS = "InStrumentationGTS";
    public static String SCEN_TYPE_BENCHMARK = "BenchMark";
    public static String SCEN_TYPE_START_PROC = "StartProc";
    public static final String POWER_HINT_VENDOR_PERFORMANCE_CTS = "POWER_HINT_VENDOR_PERFORMANCE_CTS";
    public static final String POWER_HINT_VENDOR_PERFORMANCE_GTS = "POWER_HINT_VENDOR_PERFORMANCE_GTS";
    public static final String POWER_HINT_VENDOR_DDR = "POWER_HINT_VENDOR_DDR";
    private PolicyConfig.ConfigItem mConfig;
    HashMap<String, ScenesList> mScenesLists = new HashMap<String, ScenesList>();;



    private static class ScenesList {
        HashMap<String, Scenes> mMap;
        int hintType;
        String sceneType;

        public boolean isPackageSupported(String pkgName) {
            if (mMap != null) {
                for (String key: mMap.keySet()) {
                    if (pkgName.equals(key) || pkgName.contains(key)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public Scenes getPackageScenes(String pkgName) {
            return mMap.get(pkgName);
        }

        public void dump(PrintWriter pw) {
            pw.println("for :"+sceneType);
            for(String key : mMap.keySet()) {
                pw.println(mMap.get(key));
            }
        }
    }

    public static class Scenes {
        public String pkgName;
        public long duration;
        public int hintType;
        public String sceneType;

        public String toString() {
            return "pkg:"+pkgName+" type:"+hintType+"duration"+duration+"scentype"+sceneType;
        }
    }

    private void addScenesList(ScenesList list) {
        mScenesLists.put(list.sceneType, list);
    }

    public boolean isPackageSupportScene(String scene, String pkgName) {
        ScenesList list = mScenesLists.get(scene);
        if (list != null) {
            return list.isPackageSupported(pkgName);
        }
        return false;
    }

    public int getPackageScenePowerHintType(String scene, String pkgName) {
        ScenesList list = mScenesLists.get(scene);
        if (list != null && list.isPackageSupported(pkgName)) {
            return list.hintType;
        }
        return 0;
    }

    public int getScenePowerHintType(String scene) {
        ScenesList list = mScenesLists.get(scene);
        if (list != null) {
            return list.hintType;
        }
        return 0;
    }

    public Scenes getPowerHintScenes(String scene, String pkgName) {
        ScenesList list = mScenesLists.get(scene);
        if (list != null) {
            return list.getPackageScenes(pkgName);
        }
        return null;
    }

    public static ScenesList initScenes(PolicyConfig.ConfigItem item) {
        ScenesList list = new ScenesList();
        HashMap<String, Scenes> map = new HashMap<String, Scenes>();
        ArrayList<String> strings = item.getStringArray(TAG_PKGNAME);
        for (String str : strings) {
            Scenes scenes = new Scenes();
            if (str.contains(",")) {
                String[] tmp = str.split(",");
                scenes.pkgName = tmp[0];
                scenes.duration = Long.valueOf(tmp[1].trim());
            } else {
                scenes.pkgName = str;
                scenes.duration = 0;
            }
            scenes.hintType = StringToPowerHintType(item.getString(TAG_HINT_TYPE));
            scenes.sceneType = item.getItemValue();
            map.put(scenes.pkgName, scenes);
        }
        list.mMap = map;
        list.hintType = StringToPowerHintType(item.getString(TAG_HINT_TYPE));
        list.sceneType = item.getItemValue();
        return list;
    }

    static int StringToPowerHintType(String type) {
        switch (type) {
            case POWER_HINT_VENDOR_PERFORMANCE_CTS:
                return PowerHintVendorSprd.POWER_HINT_VENDOR_PERFORMANCE_CTS;
            case POWER_HINT_VENDOR_PERFORMANCE_GTS:
                return PowerHintVendorSprd.POWER_HINT_VENDOR_PERFORMANCE_GTS;
            case POWER_HINT_VENDOR_DDR:
                return PowerHintVendorSprd.POWER_HINT_VENDOR_DDR;
            default:
                return 0;
        }
    }

    public static PowerHintConstants loadFromConfig(PolicyConfig config) {

        PowerHintConstants powerHintConstants = new PowerHintConstants();
        try {
            HashMap<String , PolicyConfig.ConfigItem> map = config.getAllConfigItems();
            for (String key : map.keySet()) {
                ScenesList list = initScenes(map.get(key));
                powerHintConstants.addScenesList(list);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return powerHintConstants;
    }

    public void dump(PrintWriter pw) {
        pw.println("Dump PowerHintConstants:");
        pw.println("---------------------------");
        for (String key : mScenesLists.keySet()) {
            mScenesLists.get(key).dump(pw);
        }
        pw.println("---------------------------");
    }
}
