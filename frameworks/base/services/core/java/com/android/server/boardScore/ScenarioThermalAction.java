/*
 * ScenarioThermalAction.java
 *
 * Copyright (C) 2016 Spreadtrum Communications Inc.
 */

package com.android.server.boardScore;
import android.util.Log;
import vendor.sprd.hardware.thermal.V1_0.IExtThermal;

public class ScenarioThermalAction extends ScenarioAction{

    private static final String TAG = "ScenarioThermalAction";

    protected void doAction(){
        super.doAction();
        try
        {
            int cmd = Integer.parseInt(this.getArg());
            IExtThermal server = IExtThermal.getService();
            server.setExtThermal(cmd);
            Log.d(TAG, "doAction: "+this.getName()+" "+this.getArg());
        } catch (Throwable e) {
            Log.e(TAG, "IExtThermal.setExtThermal error!");
            e.printStackTrace();
        }
    }
}

