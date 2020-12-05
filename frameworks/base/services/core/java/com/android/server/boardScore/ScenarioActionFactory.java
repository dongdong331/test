/*
 * ScenarioActionFactory.java
 *
 * Copyright (C) 2016 Spreadtrum Communications Inc.
 */

package com.android.server.boardScore;
import android.content.Context;
import android.os.Handler;

public class ScenarioActionFactory{
    private static final String ACT_THERMAL = "thermal";
    private static final String ACT_POWERHINT = "powerhint";

    static ScenarioAction getAction( String mName, Context context, Handler handler){
        ScenarioAction action;
        if (mName.equals(ACT_THERMAL)){
            action = (ScenarioAction) (new ScenarioThermalAction());
        }else if (mName.equals(ACT_POWERHINT)){
            action = (ScenarioAction) (new ScenarioPowerHintAction(context));
            action.setHandler(handler);
        } else {
            action = (ScenarioAction) (new ScenarioCommonAction());
        }
        action.setName(mName);
        return action;
    }


}
