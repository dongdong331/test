package com.android.server.performance.collector;

import android.app.ProcState;
import android.app.ProcessInfo;
import android.content.pm.ApplicationInfo;
import com.android.server.am.ActivityManagerServiceEx;
import android.os.UserHandle;
import android.os.Debug;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Xml;


import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;

import static com.android.server.performance.PerformanceManagerDebugConfig.*;
/**
 * Created by SPREADTRUM\joe.yu on 5/8/17.
 */

public class ProcessStateCollector {
    private final String TAG_PROC = "process";
    private final String TAG_USER = "user";
    private final String ATTR_USER_ID = "user_id";
    private final String PROCSTATE_FILE = "/data/system/usrhabit/procs.xml";
    private final long POLLING_DELAY = 1 * 60 * 60 *1000;
    HashMap<Integer, HashMap<String, ProcState>> mProcStates = new HashMap<>();
    ActivityManagerServiceEx mService;
    Handler mHandler;

    public ProcessStateCollector(ActivityManagerServiceEx service, Handler handler) {
        mService = service;
        mHandler = handler;
        loadProcStatesFromFile();
        if (DEBUG_PROCSTAT) dumpProcStates();
    }

    public void updateProcStates() {
        try {
            updateProcStatesInnder();
        } catch (Exception e) {}
    }

    private void scheduleSyncWithFile() {
        mHandler.postDelayed(new Runnable() {
            public void run() {
                saveProcStatesToFile();
                scheduleSyncWithFile();
            }
        }, POLLING_DELAY);
    }

    private void updateProcStatesInnder() {
        //for top apps:

        ArrayList<ProcessInfo> runningList = mService.getRunningProcessesInfo();
        ProcessInfo top = null;
        for (int i = 0; i < runningList.size(); i++) {
            ProcessInfo p = runningList.get(i);
            if(p != null && p.curAdj == 0) {
                top = p;
                break;
            }
        }
        if (top == null) {
            return;
        }
        ProcState procState = getOrCreatProcState(top.processName, UserHandle.myUserId());
        long now = System.currentTimeMillis();
        synchronized (mProcStates) {
            long pss = Debug.getPss(top.pid, null, null);
            long avg = (procState.avgTopPss * procState.topPssSampleCount + pss) / (procState.topPssSampleCount + 1);
            procState.avgTopPss = avg;
            procState.topPssSampleCount ++;
        }
        if (DEBUG_PROCSTAT) dumpProcStates();
    }

    HashMap<String, ProcState> getProcStateHashMap(int userID) {
        synchronized (mProcStates) {
            HashMap<String, ProcState> map = mProcStates.get(userID);
            if (map == null) {
                map = new HashMap<>();
                mProcStates.put(userID, map);
            }
            return (HashMap<String, ProcState>)map;
        }
    }

    public void onShutDown(){
        synchronized(mProcStates) {
            saveProcStatesToFile();
        }
    }

    ProcState getOrCreatProcState(String processName, Integer userId) {
        //Integer userId = 0;
        ProcState procState;
        synchronized (mProcStates) {
            HashMap<String, ProcState> map = getProcStateHashMap(userId);
            if ((procState = map.get(processName)) == null) {
                procState = new ProcState(processName, userId);
                map.put(processName, procState);
            }
        }
        return procState;
    }

    public ArrayList<ProcState> getProcStates(Integer userId) {
        ArrayList<ProcState> list = new ArrayList<>();
        synchronized (mProcStates) {
            HashMap<String, ProcState> map = getProcStateHashMap(userId);
            for (String key : map.keySet()) {
                ProcState procState = map.get(key);
                if (procState != null) {
                    list.add(procState);
                }
            }
        }
        return list;
    }

    public ProcState getProcStates(Integer userId, String processName) {
        return getOrCreatProcState(processName, userId);
    }

    private void loadProcStatesFromFile() {
        File configFile = new File(PROCSTATE_FILE);
        if(!configFile.exists()) {
            Log.d(TAG, "maybe first boot, config file not exist");
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(configFile));
            final XmlPullParser in = Xml.newPullParser();
            in.setInput(reader);
            int event;
            HashMap<String, ProcState> map = new HashMap<>();
            Integer uid = 0;
            while (((event = in.next()) != XmlPullParser.END_DOCUMENT)) {
                final String name = in.getName();

                if (event == XmlPullParser.START_TAG) {
                    if (TAG_USER.equals(name)) {
                        map = new HashMap<>();
                        for (int attrNdx = in.getAttributeCount() - 1; attrNdx >= 0; --attrNdx) {
                            final String attrName = in.getAttributeName(attrNdx);
                            final String attrValue = in.getAttributeValue(attrNdx);
                            if (ATTR_USER_ID.equals(attrName)) {
                                uid = Integer.valueOf(attrValue);
                            }
                        }
                    } else if (TAG_PROC.equals(name)) {
                        ProcState procState = ProcState.restoreFromFile(in);
                        map.put(procState.processName, procState);
                        if (DEBUG_PROCSTAT) Log.d(TAG, "procState-->" + procState);
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    if (TAG_USER.equals(name)) {
                        synchronized (mProcStates) {
                            mProcStates.put(uid, map);
                        }
                    }
                }
            }
        } catch (Exception e) {
            configFile.delete();
            synchronized (mProcStates) {
                mProcStates.clear();
            }
            Log.e(TAG, "unable get userprocState date.. deleting");
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void dumpProcStates() {
        HashMap<Integer, HashMap<String, ProcState>> maps = new HashMap<>();
        synchronized (mProcStates) {
            for (Integer i : mProcStates.keySet()) {
                HashMap<String, ProcState> map = mProcStates.get(i);
                for(String key: map.keySet()) {
                    ProcState procState = map.get(key);
                    Log.d(TAG, "procState->"+procState);
                }
            }
        }
    }


    private void saveProcStatesToFile() {
        FileOutputStream fo = null;
        AtomicFile file = null;
        StringWriter writer = new StringWriter();
        HashMap<Integer, HashMap<String, ProcState>> temp = null;
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlSerializer xmlSerializer = factory.newSerializer();
            xmlSerializer.setOutput(writer);
            xmlSerializer.startDocument(null, true);
            synchronized (mProcStates) {
                temp = (HashMap<Integer, HashMap<String, ProcState>>) mProcStates.clone();
            }
            for (Integer id : temp.keySet()) {
                HashMap<String, ProcState> map = temp.get(id);
                if (map != null) {
                    xmlSerializer.startTag(null, TAG_USER);
                    xmlSerializer.attribute(null, ATTR_USER_ID, String.valueOf(id));
                    for (String key : map.keySet()) {
                        ProcState procState = map.get(key);
                        if (procState != null) {
                            xmlSerializer.startTag(null, TAG_PROC);
                            procState.writeToFile(xmlSerializer);
                            xmlSerializer.endTag(null, TAG_PROC);
                        }
                    }
                    xmlSerializer.endTag(null, TAG_USER);
                }
            }
            xmlSerializer.endDocument();
            xmlSerializer.flush();
            file = new AtomicFile(new File(PROCSTATE_FILE));
            fo = file.startWrite();
            fo.write(writer.toString().getBytes());
            fo.write('\n');
            file.finishWrite(fo);
        } catch (Exception e) {
            e.printStackTrace();
            if (fo != null) {
                file.failWrite(fo);
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("PROCESS STATUS:");
        pw.println("----------------------------");
        HashMap<Integer, HashMap<String, ProcState>> maps = new HashMap<>();
        synchronized (mProcStates) {
            for (Integer i : mProcStates.keySet()) {
                HashMap<String, ProcState> map = mProcStates.get(i);
                for(String key: map.keySet()) {
                    ProcState procState = map.get(key);
                    pw.println("procState->"+procState);
                }
            }
        }
        pw.println("----------------------------");
    }
}