package com.android.server.performance.policy.ram;

import android.util.Log;

import java.io.PrintWriter;
import java.util.HashMap;

import com.android.server.performance.PolicyConfig;

/**
 * Created by SPREADTRUM\joe.yu on 8/1/17.
 */

public class MemoryScene {
    public static final String  TAG = "MemoryScene";


    private static final String TAG_SCENE = "scene";
    private static final String ATTR_NAME = "name";
    private static final String TAG_ACTION = "action";
    public static final String SCENE_IDLE = "idle";
    public static final String SCENE_DEF = "default";
    public static final String SCENE_BIG_MEM = "BigMem";
    public static final String SCENE_EMERGENCY = "Emergency";
    public static final String POLICY_KILL = "kill";
    public static final String POLICY_RECLAIM = "reclaim";
    public static final String POLICY_QUICK_KILL = "quickkill";
    public static final String POLILCY_RECLAIM_PERSIST = "reclaimPersist";

    HashMap<String,String> mSceneMap = new HashMap<String, String>();

    /*
            <config name="MemoryScene">
            <item scene="idle">
                <action name="reclaim"></action>
            </item>
            <item scene="default">
                <action name="kill"></action>
            </item>
            <item scene="BigMem">
                <action name="quickkill"></action>
            </item>
            </config>
    *
    * */
    public String getPolicyForScene(String scene) {
        return mSceneMap.get(scene);
    }
    public static MemoryScene loadFromConfig(PolicyConfig config) {
        MemoryScene memoryScene = new MemoryScene();
        PolicyConfig.ConfigItem idleConfig = config.getConfigItem(SCENE_IDLE);
        PolicyConfig.ConfigItem defConfig = config.getConfigItem(SCENE_DEF);
        PolicyConfig.ConfigItem bigMemConfig = config.getConfigItem(SCENE_BIG_MEM);
        PolicyConfig.ConfigItem emergencyConfig = config.getConfigItem(SCENE_EMERGENCY);
        if (idleConfig != null && defConfig != null && bigMemConfig != null && emergencyConfig != null) {
            //for idle:
            String actionIdle = config.getConfigItem(SCENE_IDLE).getString(TAG_ACTION);
            //for default:
            String actionDefault = config.getConfigItem(SCENE_DEF).getString(TAG_ACTION);
            //for BigMem
            String actionBigMem = config.getConfigItem(SCENE_BIG_MEM).getString(TAG_ACTION);
            //for Emergency
            String actionEmergency = config.getConfigItem(SCENE_EMERGENCY).getString(TAG_ACTION);
            if (actionIdle != null && actionDefault != null && actionBigMem != null && actionEmergency != null) {
                memoryScene.mSceneMap.put(SCENE_IDLE, actionIdle);
                memoryScene.mSceneMap.put(SCENE_DEF, actionDefault);
                memoryScene.mSceneMap.put(SCENE_BIG_MEM, actionBigMem);
                memoryScene.mSceneMap.put(SCENE_EMERGENCY, actionEmergency);
                return memoryScene;
            }
        }
        return null;
    }

    public void dump() {
        if (mSceneMap != null) { 
            for (String key : mSceneMap.keySet()) {
                String action = mSceneMap.get(key);
                if (action != null) {
                    Log.e(TAG, "scene"+key + " --> policy:"+action);
                }
            }
        }
    }
    public void dump(PrintWriter pw) {
        pw.println("Dump MemoryScene:");
        pw.println("----------------------------");
        if (mSceneMap != null) { 
            for (String key : mSceneMap.keySet()) {
                String action = mSceneMap.get(key);
                if (action != null) {
                    pw.println("scene"+key + " --> policy:"+action);
                }
            }
        }
        pw.println("----------------------------");
    }
}
