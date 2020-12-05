/*
 * Scenario.java
 *
 * Copyright (C) 2016 Spreadtrum Communications Inc.
 */
package com.android.server.boardScore;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import android.util.Log;
import android.util.Slog;


public class Scenario {
    private static final String TAG = "Scenario";
    private String type;
    public  int pollingDelay;

    List<String> packageList;
    ScenarioTrip startTrip;
    ScenarioTrip endTrip;

    public Scenario(){
        packageList = new ArrayList<String>();
    }

    protected void setType( String scenarioType ){
        type = scenarioType ;
    }

    protected String getType(){
        return type;
    }

    protected void setPackageList( List<String> mPackageList ){
        packageList = mPackageList;
    }

    protected List<String> getPackageList(){
        return packageList;
    }

    protected void setStartTrip( ScenarioTrip mStartTrip ){
        startTrip = mStartTrip;
    }

    protected ScenarioTrip getStartTripList(){
        return startTrip;
    }

    protected void setEndTrip( ScenarioTrip mEndTrip ){
        endTrip = mEndTrip;
    }

    protected ScenarioTrip getEndTrip(){
        return endTrip;
    }

    protected void tripAction( ScenarioTrip nextTrip ){
        for(ScenarioAction mAction : nextTrip.actionList){
            mAction.doAction();
        }
    }

    protected void setPollingDelay( int mPollingDelay ){
        pollingDelay = mPollingDelay;
    }

    protected int getPollingDelay(){
        return pollingDelay;
    }


}
