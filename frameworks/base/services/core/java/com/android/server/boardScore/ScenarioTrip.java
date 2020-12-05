/*
 * ScenarioTrip.java
 *
 * Copyright (C) 2016 Spreadtrum Communications Inc.
 */
package com.android.server.boardScore;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

public class ScenarioTrip{

    List<ScenarioAction> actionList = null;
    String mType;

    public ScenarioTrip(){
    }

    protected void setActionList(  List<ScenarioAction> mActionList ){
       actionList = mActionList;
    }

    protected List<ScenarioAction> getActionList(){
        return actionList;
    }

    protected void setType( String type ){
        mType = type;
    }

    protected String getType(){
        return mType;
    }

}
