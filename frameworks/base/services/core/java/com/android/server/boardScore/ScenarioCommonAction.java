/*
 * ScenarioCommonAction.java
 *
 * Copyright (C) 2016 Spreadtrum Communications Inc.
 */

package com.android.server.boardScore;
import android.util.Slog;
import android.util.Log;
import java.io.FileWriter;
import java.io.IOException;

 public class ScenarioCommonAction extends ScenarioAction{

     private static final String TAG = "ScenarioCommonAction";

     protected void doAction(){
         super.doAction();
	 writeFs(this.getFile(),this.getArg());
     }

     protected static void writeFs(String file, String value){
        if (file == null || value == null) return;
        char[] buffer = value.toCharArray();
        FileWriter fr = null;
        try {
            fr = new FileWriter(file, false);
            if (fr != null) {
                fr.write(buffer);
            }
        }catch (IOException e){
            Log.d(TAG, "write file failed,exception:"+e.toString());
        } finally {
            try{
                if(fr != null)
                    fr.close();
            } catch(IOException e) {
                Log.w(TAG, "close FileWriter failed,exption:"+e.toString());
            }
        }
     }



 }
