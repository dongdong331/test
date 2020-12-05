/*
 * ScenarioAction.java
 *
 * Copyright (C) 2016 Spreadtrum Communications Inc.
 */

package com.android.server.boardScore;
import android.util.Log;
import android.os.Handler;

public class ScenarioAction{
    private static final String TAG = "ScenarioAction";
    private String mName;
    private String mArg;
    private String mFile;
    private Handler mHandler;
    /*
     * we should always call the setXXX function for enough member info
     */
    public ScenarioAction(){
    }

    public ScenarioAction(String name, String arg, String file){
        mName = name;
        mArg = arg;
        mFile = file;
    }

    protected void setName( String name ){
        mName = name;
    }

    protected String getName(){
        return mName;
    }

    protected void setArg( String arg ){
        mArg = arg;
    }

    protected String getArg(){
        return mArg;
    }

    protected void setFile( String file ){
        mFile = file;
    }

    protected String getFile(){
        return mFile;
    }

    protected void setHandler( Handler handler ){
        mHandler = handler;
    }

    protected Handler getHandler(){
        return mHandler;
    }

    protected void doAction(){
        Log.d(TAG,"doAction,===>"+mName
                    +",mActionArg:"+mArg
                            +",mFileNode:"+mFile);
    }

}
