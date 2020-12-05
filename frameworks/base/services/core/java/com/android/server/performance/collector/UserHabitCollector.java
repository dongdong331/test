/*
 * Copyright © 2017 Spreadtrum Communications Inc.
 */

package com.android.server.performance.collector;

import android.app.UserHabit;
import android.app.AppRelevance;
import android.content.pm.ApplicationInfo;
import com.android.server.am.ActivityManagerServiceEx;
import android.os.UserHandle;
import android.os.SystemClock;
import android.os.Handler;
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

public class UserHabitCollector {
    private final int EVENT_MOVE_TO_FG = 0;
    private final int EVENT_MOVE_TO_BG = 1;
    private final String TAG_USER_HABIT = "userhabit";
    private final String TAG_USER = "user";
    private final String ATTR_USER_ID = "user_id";
    private final String USER_HABIT_FILE = "/data/system/usrhabit/habit.xml";
    private final long POLLING_DELAY = 1 * 60 * 60 *1000;
    HashMap<Integer, HashMap<String, UserHabit>> mUserHabits = new HashMap<>();
    ActivityManagerServiceEx mService;
    Handler mBgHandler;

    public UserHabitCollector(ActivityManagerServiceEx service, Handler handler) {
        mService = service;
        mBgHandler = handler;
        loadUserHabitsFromFile();
        if (DEBUG_USRHABIT) dumpHabits();
        scheduleSyncWithFile();
    }

    public void updateHabitStatus(String packageName, boolean resumed, boolean appSwitch) {
        if (packageName == null) {
            return;
        }
        int event = resumed ? EVENT_MOVE_TO_FG : EVENT_MOVE_TO_BG;
        try {
            updateHabitStatusInnder(event, UserHandle.myUserId(), packageName, appSwitch);
        } catch (Exception e) {}
    }

    public void updateHabitsLaunchTime(String pkgName, long launchTime) {
        try {
            updateHabitsLaunchTimeInner(UserHandle.myUserId(), pkgName, launchTime);
        } catch (Exception e) {}
    }

    private void scheduleSyncWithFile() {
        mBgHandler.postDelayed(new Runnable() {
            public void run() {
                saveUserHabitsToFile();
                scheduleSyncWithFile();
            }
        }, POLLING_DELAY);
    }

    private void updateHabitsLaunchTimeInner(Integer userId, String pkgName, long launchTime) {
        UserHabit habit = getOrCreatUserHabit(pkgName, userId);
        if (DEBUG_USRHABIT) Log.d(TAG, "updateHabitsLaunchTimeInner:"+habit+" time:"+launchTime);
        synchronized (mUserHabits) {
            habit.updateLaunchTime(String.valueOf(launchTime));
        }
    }

    private void updateAppRelevanceLocked(Integer userId, String pkgName, long now) {
        //check specific duration:
        HashMap<String, UserHabit> map = mUserHabits.get(userId);
        for(String key: map.keySet()) {
            UserHabit habit = map.get(key);
            long idle = now - habit.lastUseTime;
            if (!habit.getPackageName().equals(pkgName) &&
                 habit.lastUseTime != 0 && idle >=0 &&
                AppRelevance.getType(idle) != AppRelevance.TYPE_NOT_CARE) {
                // update relevance:
                AppRelevance ar = habit.getAppRelevance();
                if (DEBUG_USRHABIT) Log.d(TAG, "updateAppRelevanceLocked cur:"+habit.getPackageName() +
                         "targ:"+pkgName+"idle = "+idle+" type = "+AppRelevance.getType(idle));
                if (ar != null) {
                    ar.update(pkgName, AppRelevance.getType(idle));
                }
            }
        }
    }

    private void updateHabitStatusInnder(int event, Integer userId, String pkgName, boolean appSwitch) {

        UserHabit habit = getOrCreatUserHabit(pkgName, userId);
        if (DEBUG_USRHABIT) Log.d(TAG, "updateHabitStatusInnder:"+habit+" event:"+event);
        long now = System.currentTimeMillis();
        synchronized (mUserHabits) {
            switch (event) {
                case EVENT_MOVE_TO_FG:
                    habit.mLastEvent = EVENT_MOVE_TO_FG;
                    habit.launchCount++;
                    habit.lastForegroundTime = now;
                    if (appSwitch) {
                        updateAppRelevanceLocked(userId, pkgName, now);
                    }
                    break;
                case EVENT_MOVE_TO_BG:
                    if (habit.mLastEvent == EVENT_MOVE_TO_FG) {
                        habit.totalForegroundTime += now - habit.lastForegroundTime;
                    }
                    habit.mLastEvent = EVENT_MOVE_TO_BG;
                    habit.lastUseTime = now;
                    break;
                default:
                    break;
            }
        }
        if (DEBUG_USRHABIT) dumpHabits();
    }

    HashMap<String, UserHabit> getUserHabitHashMap(int userID) {
        synchronized (mUserHabits) {
            HashMap<String, UserHabit> map = mUserHabits.get(userID);
            if (map == null) {
                map = new HashMap<>();
                mUserHabits.put(userID, map);
            }
            return map;
        }
    }

    UserHabit getOrCreatUserHabit(String pkgName, Integer userId) {
        //Integer userId = 0;
        UserHabit habit;
        synchronized (mUserHabits) {
            HashMap<String, UserHabit> map = getUserHabitHashMap(userId);
            if ((habit = map.get(pkgName)) == null) {
                habit = new UserHabit(pkgName, userId);
                map.put(pkgName, habit);
            }
        }
        return habit;
    }

    public ArrayList<UserHabit> getUserHabits(Integer userId) {
        ArrayList<UserHabit> list = new ArrayList<>();
        synchronized (mUserHabits) {
            HashMap<String, UserHabit> map = getUserHabitHashMap(userId);
            for (String key : map.keySet()) {
                UserHabit habit = map.get(key);
                if (habit != null) {
                    list.add(habit);
                }
            }
        }
        return list;
    }

    public UserHabit getPackageUserHabits(Integer userId, String packageName) {
        return getOrCreatUserHabit(packageName, userId).clone();
    }

    public void onShutDown(){
        synchronized(mUserHabits) {
            saveUserHabitsToFile();
        }
    }
    public void loadUserHabitsFromFile() {
        File configFile = new File(USER_HABIT_FILE);
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
            HashMap<String, UserHabit> map = new HashMap<>();
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
                    } else if (TAG_USER_HABIT.equals(name)) {
                        UserHabit habit = UserHabit.restoreFromFile(in);
                        habit.restoreAppRelevanceFromFile(in);
                        map.put(habit.packageName, habit);
                        if (DEBUG_USRHABIT) Log.d(TAG, "habit-->" + habit);
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    if (TAG_USER.equals(name)) {
                        synchronized (mUserHabits) {
                            mUserHabits.put(uid, map);
                        }
                    }
                }
            }
        } catch (Exception e) {
            configFile.delete();
            synchronized (mUserHabits) {
                mUserHabits.clear();
            }
            Log.e(TAG, "unable get userhabit date.. deleting");
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


    private void dumpHabits() {
        HashMap<Integer, HashMap<String, UserHabit>> maps = new HashMap<>();
        synchronized (mUserHabits) {
            for (Integer i : mUserHabits.keySet()) {
                HashMap<String, UserHabit> map = mUserHabits.get(i);
                for(String key: map.keySet()) {
                    UserHabit habit = map.get(key);
                    Log.d(TAG, "habit->"+habit);
                }
            }
        }
    }


    private void saveUserHabitsToFile() {
        FileOutputStream fo = null;
        AtomicFile file = null;
        StringWriter writer = new StringWriter();
        HashMap<Integer, HashMap<String, UserHabit>> temp = null;
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlSerializer xmlSerializer = factory.newSerializer();
            xmlSerializer.setOutput(writer);
            xmlSerializer.startDocument(null, true);
            synchronized (mUserHabits) {
                temp = (HashMap<Integer, HashMap<String, UserHabit>>)mUserHabits.clone();
            }
            for (Integer id : temp.keySet()) {
                HashMap<String, UserHabit> map = temp.get(id);
                if (map != null) {
                    xmlSerializer.startTag(null, TAG_USER);
                    xmlSerializer.attribute(null, ATTR_USER_ID, String.valueOf(id));
                    for (String key : map.keySet()) {
                        UserHabit habit = map.get(key);
                        if (habit != null) {
                            xmlSerializer.startTag(null, TAG_USER_HABIT);
                            habit.writeToFile(xmlSerializer);
                            xmlSerializer.endTag(null, TAG_USER_HABIT);
                        }
                    }
                    xmlSerializer.endTag(null, TAG_USER);
                }
            }
            xmlSerializer.endDocument();
            xmlSerializer.flush();
            file = new AtomicFile(new File(USER_HABIT_FILE));
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
        pw.println("USAGE STATICS:");
        pw.println("----------------------------");
        HashMap<Integer, HashMap<String, UserHabit>> maps = new HashMap<>();
        synchronized (mUserHabits) {
            for (Integer i : mUserHabits.keySet()) {
                HashMap<String, UserHabit> map = mUserHabits.get(i);
                for(String key: map.keySet()) {
                    UserHabit habit = map.get(key);
                    pw.println("habit->"+habit);
                    AppRelevance ar = habit.getAppRelevance();
                    if (ar != null) {
                        habit.getAppRelevance().dump(fd, pw, args);
                    }
                }
            }
        }
    }
}