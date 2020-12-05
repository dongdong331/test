/*
 * ScenarioPowerHintAction.java
 *
 * Copyright (C) 2017 Spreadtrum Communications Inc.
 */

package com.android.server.boardScore;
import android.util.Log;
import android.content.Context;
import android.os.Handler;
import android.os.PowerHALManager;

public class ScenarioPowerHintAction extends ScenarioAction{

    private static final String TAG = "ScenarioPowerHintAction";
    private static PowerHALManager mPowerHALManager;
    private static PowerHALManager.PowerHintScene mSceneScenario;

    private Context mContext;

    public ScenarioPowerHintAction(Context context){
        mContext = context;
    }

    protected int requestPowerHint(){
        int id;

        if (mPowerHALManager != null && mSceneScenario != null){
            return 0;
        }

        try {
            id = Integer.parseInt(this.getFile());
        } catch (Throwable e) {
            Log.e(TAG, "parament " +this.getFile()+ " isn't int type.");
            return -1;
        }
        try {
            mPowerHALManager = new PowerHALManager(mContext,  getHandler());
            if (mPowerHALManager == null){
                Log.e(TAG, "PowerHALManager error!");
                return -1;
            }

            mSceneScenario = mPowerHALManager.createPowerHintScene(
                                    TAG + " performance", id, null);
            if (mSceneScenario == null){
                Log.e(TAG, "createPowerHintScene error!");
                return -1;
            }
        } catch (java.lang.NoClassDefFoundError e) {
            Log.d(TAG, "Exception:" + e.getMessage());
            return -1;
        }
        Log.d(TAG, "request powerhint ID: " + id + " success!");
        return 0;
    }

    protected void doAction(){
        if (requestPowerHint() < 0){
            Log.e(TAG, "mSceneScenario is null!");
            return;
        }
        super.doAction();

        if (this.getArg().equals("request")){
            mSceneScenario.acquire();
        }else{
            mSceneScenario.release();
        }
        Log.d(TAG, "doAction: "+this.getName()+" "+this.getArg());

    }
}

