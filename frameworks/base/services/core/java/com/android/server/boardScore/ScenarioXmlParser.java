/*
 * ScenarioXmlParser.java
 *
 * Copyright (C) 2016 Spreadtrum Communications Inc.
 */

package com.android.server.boardScore;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import android.content.Context;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.File;

import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import java.security.Key;
import java.security.SecureRandom;
import android.os.Handler;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;

public class ScenarioXmlParser{
    private static final String TAG = "ScenarioXmlParser";
    private static final String CONFIG_FILE_PATCH = "/system/etc/scenario.conf";
    private static final String TAGPATH = "/data/scenario_performance_power.xml";
    private static final String PLLING_DELAY = "polling_delay";
    private static final String SCENARIO = "scenario";
    private static final String TYPE = "type";
    private static final String PACKAGE = "package";
    private static final String NAME = "name";
    private static final String TRIP = "trip";
    private static final String END_TRIP = "end_trip";
    private static final String START_TRIP = "start_trip";
    private static final String ACTION = "action";
    private static final String ARG = "arg";
    private static final String FILE = "file";
    private static final String AESCODE = "0000000000000000";
    private static final String CRYPTOMODE = "AES";
    private File mConfigFile = null;
    private Context mContext;
    public ScenarioAction mScenarioAction = null;
    public Scenario mScenario = null;
    List<Scenario> ScenarioList;
    List<ScenarioAction> actionList;
    private Handler mHandler;

    public ScenarioXmlParser(Context context){
        mContext = context;
    }

    public ScenarioXmlParser(Context context, Handler handler){
        mContext = context;
        mHandler = handler;
    }

    public List<Scenario> parserXml(String filePath) {
        List<Scenario> mScenarioList = new ArrayList<Scenario>();
        try {
            Log.d(TAG, "staring parser XML");
            InputStream is = ScenarioXmlCrypto.deCrypto(filePath, AESCODE);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document thmXml = builder.parse(is);
            NodeList delayList = thmXml.getElementsByTagName(PLLING_DELAY);
            Node delayNode = delayList.item(0);
            String mPollingDelay = delayNode.getFirstChild().getNodeValue();;
            NodeList scenarioList = thmXml.getElementsByTagName(SCENARIO);
            Log.d(TAG, "Total have "+ scenarioList.getLength() + " scenario node");
            for (int i = 0; i < scenarioList.getLength(); i++) {
                mScenario = new Scenario();
                Node scenario = scenarioList.item(i);
                Element scenarioElem = (Element) scenario;
                String scenarioType = scenarioElem.getAttribute(TYPE);
                mScenario.setType(scenarioType);
                Log.d(TAG, "get scenario: "+mScenario.getType());
                mScenario.setPollingDelay(Integer.parseInt(mPollingDelay));
                Log.d(TAG,scenarioType  + " set polling_delay: "+mScenario.getPollingDelay());
                NodeList childNoneList = scenarioElem.getChildNodes();
                for(int index = 0;index < childNoneList.getLength();index++){
                    Node childNode = childNoneList.item(index);
                    if (childNode.getNodeName().equals(PACKAGE)){
                        Element childNodeElm = (Element) childNode;
                        String packageName = childNodeElm.getAttribute(NAME);
                        mScenario.packageList.add(packageName);
                        Log.d(TAG,scenarioType + " add package : " + packageName );
                    }
                    if (childNode.getNodeName().contains(TRIP)) {
                        parserTripNode(mScenario,childNode);
                    }
                }
                mScenarioList.add(mScenario);
                Log.d(TAG, "add scenario "+mScenario.getType());
            }
        }catch(Exception e){
            Log.d(TAG,e.getMessage());
        }finally{
            return mScenarioList;
        }
    }

    protected void parserTripNode (Scenario mScenario, Node TripNode){
        ScenarioTrip mScenarioTrip = new ScenarioTrip();
        if (TripNode.getNodeName().equals(START_TRIP)){
            mScenarioTrip.setType(TripNode.getNodeName());
            Log.d(TAG, mScenario.getType() + " add trip :" + TripNode.getNodeName() );
            storeActionInfo(TripNode, mScenarioTrip);
            mScenario.startTrip = mScenarioTrip;
        }else if(TripNode.getNodeName().equals(END_TRIP)){
            mScenarioTrip.setType(TripNode.getNodeName());
            Log.d(TAG, mScenario.getType() + " add type : " + TripNode.getNodeName() );
            storeActionInfo(TripNode, mScenarioTrip);
            mScenario.endTrip = mScenarioTrip;
        }
    }

    protected void storeActionInfo(Node TripNode, ScenarioTrip mScenarioTrip){
        Element tripElem = (Element) TripNode;
        NodeList actionNodeList = tripElem.getChildNodes();
        actionList = new ArrayList<ScenarioAction>();
        for(int index = 0;index < actionNodeList.getLength();index++){
            if (actionNodeList.item(index).getNodeName().equals(ACTION)){
                Element ActionElem = (Element) actionNodeList.item(index);
                String mName = ActionElem.getAttribute(NAME);
                mScenarioAction = ScenarioActionFactory.getAction(mName, mContext, mHandler);
                mScenarioAction.setName(mName);
                String mArg = ActionElem.getAttribute(ARG);
                mScenarioAction.setArg(mArg);
                String mFile = ActionElem.getAttribute(FILE);
                mScenarioAction.setFile(mFile);
                Log.d (TAG," action name=" + mScenarioAction.getName() +
                            " arg=" + mScenarioAction.getArg() + " mFile=" + mScenarioAction.getFile());
                            actionList.add(mScenarioAction);
            }
        }
        mScenarioTrip.setActionList(actionList);
    }

    public List<Scenario> initScenariosFromXml (){
        Log.d (TAG,"initialing Scenario xml...");
        mConfigFile = new File(CONFIG_FILE_PATCH);
        if (!mConfigFile.exists()){
            Log.d (TAG,"Scenario xml don't exit");
            return null;
        }
        try{
            ScenarioList = parserXml(CONFIG_FILE_PATCH);
        }catch(Exception e){
            Log.d(TAG,e.getMessage());
        }
        return ScenarioList;
    }
}
