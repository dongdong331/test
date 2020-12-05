/*
 * Copyright © 2017 Spreadtrum Communications Inc.
 */
package android.app;

import android.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by SPREADTRUM\joe.yu on 5/8/17.
 */

public class UserHabit {
    private static final String TAG = "UserHabit";
    private static final int RECENT_LAUNCH_TIME_COUNT = 10;
    public String packageName;
    public long launchCount;
    public long totalForegroundTime;
    public int mLastEvent;
    public long lastForegroundTime;
    public long lastUseTime;
    public Integer mUserId;
    private ArrayList<String> mLaunchTime = new ArrayList<>();
    //for enhanced app relationship
    private AppRelevance mAppRelevance;

    private static final String ATTR_PKG = "pkgName";
    private static final String ATTR_USERID = "userid";
    private static final String ATTR_LAUNCH_COUNT = "launchCount";
    private static final String ATTR_TOTAL_FG_TIME = "totalForegroundTime";
    private static final String ATTR_LAST_FG_TIME = "lastFgTime";
    private static final String ATTR_LAST_USE_Time = "lastUseTime";
    private static final String ATTR_RECENT_LAUNCH_TIME = "recentLaunchTime";

    public UserHabit(String packageName, Integer userId){
        this.packageName = packageName;
        this.launchCount = 0;
        this.totalForegroundTime = 0;
        this.mLastEvent = -1;
        this.lastForegroundTime = 0;
        this.lastUseTime = 0;
        this.mUserId = userId;
        mAppRelevance = new AppRelevance(packageName);
    }
    public UserHabit(String packageName, int launchCount, long totalForegroundTime, long lastForegroundTime, long  lastUseTime, int mUserId){
        this.packageName = packageName;
        this.launchCount = launchCount;
        this.totalForegroundTime = totalForegroundTime;
        this.mLastEvent = -1;
        this.lastForegroundTime = lastForegroundTime;
        this.lastUseTime = lastUseTime;
        this.mUserId = mUserId;
        mAppRelevance = new AppRelevance(packageName);
    }
    public UserHabit(){
        mLastEvent = -1;
    }
    @Override
    public String toString() {
        String tmp = "";
        synchronized(mLaunchTime) {
            for (String s : mLaunchTime) {
                tmp += s+";";
            }
        }
        return "UID:"+mUserId+"|pkg:"+packageName+"|count:"+launchCount+"|LastUsed:"+lastUseTime+"|totalForegroundTime"+totalForegroundTime+"|launchTime:"+tmp;
    }

    public void updateLaunchTime(String newVal) {
        synchronized(mLaunchTime) {
            mLaunchTime.add(newVal);
            if(mLaunchTime.size() >= RECENT_LAUNCH_TIME_COUNT) {
                mLaunchTime.remove(0);
            }
        }
    }

    public void writeToFile(XmlSerializer serializer) throws IOException, XmlPullParserException{
        serializer.attribute(null, ATTR_USERID,String.valueOf(mUserId));
        serializer.attribute(null, ATTR_PKG, packageName);
        serializer.attribute(null, ATTR_LAUNCH_COUNT, String.valueOf(launchCount));
        serializer.attribute(null, ATTR_TOTAL_FG_TIME,String.valueOf(totalForegroundTime));
        serializer.attribute(null, ATTR_LAST_FG_TIME, String.valueOf(lastForegroundTime));
        serializer.attribute(null, ATTR_LAST_USE_Time,String.valueOf(lastUseTime));
        String tmp = "";
        synchronized(mLaunchTime) {
            for (String s : mLaunchTime) {
                tmp += s+";";
            }
        }
        serializer.attribute(null, ATTR_RECENT_LAUNCH_TIME,tmp);
        writeAppRelevanceToFile(serializer);
    }

    private void writeAppRelevanceToFile(XmlSerializer serializer) throws IOException, XmlPullParserException{
        mAppRelevance.writeToFile(serializer);
    }

    public void restoreAppRelevanceFromFile(XmlPullParser in) throws IOException, XmlPullParserException {
        mAppRelevance = AppRelevance.restoreFromFile(packageName, in);
    }
    public static UserHabit restoreFromFile(XmlPullParser in) throws IOException, XmlPullParserException {
        UserHabit habit = new UserHabit();
        for (int attrNdx = in.getAttributeCount() - 1; attrNdx >= 0; --attrNdx) {
            final String attrName = in.getAttributeName(attrNdx);
            final String attrValue = in.getAttributeValue(attrNdx);
            if (ATTR_USERID.equals(attrName)) {
                habit.mUserId = Integer.valueOf(attrValue);
            } else if(ATTR_PKG.equals(attrName)) {
                habit.packageName = attrValue;
            } else if(ATTR_LAUNCH_COUNT.equals(attrName)) {
                habit.launchCount = Long.valueOf(attrValue);
            } else if(ATTR_TOTAL_FG_TIME.equals(attrName)) {
                habit.totalForegroundTime = Long.valueOf(attrValue);
            } else if(ATTR_LAST_FG_TIME.equals(attrName)) {
                habit.lastForegroundTime = Long.valueOf(attrValue);
            } else if(ATTR_LAST_USE_Time.equals(attrName)) {
                habit.lastUseTime = Long.valueOf(attrValue);
            } else if (ATTR_RECENT_LAUNCH_TIME.equals(attrName)){
                String[] tmp = attrValue.split(";");
                for (String s : tmp) {
                    habit.mLaunchTime.add(s);
                }
            } else {
                Log.e(TAG, "error attr name....:"+attrName);
            }
        }
        return habit;
    }

    public  UserHabit clone() {
        UserHabit out  = new UserHabit();
        out.packageName = this.packageName;
        out.launchCount = this.launchCount;
        out.totalForegroundTime = this.totalForegroundTime;
        out.mLastEvent = this.mLastEvent;
        out.lastForegroundTime = this.lastForegroundTime;
        out.lastUseTime = this.lastUseTime;
        out.mUserId = this.mUserId;
        out.mLaunchTime = (ArrayList<String>)this.mLaunchTime.clone();
        out.mAppRelevance = this.mAppRelevance;
        return out;
    }

    public long getTotalTimeInForeground(){
        return totalForegroundTime;
    }

    public String getPackageName() {
        return packageName;
    }

    public long getLastTimeUsed() {
        return lastUseTime;
    }

    public long getLastForegroundTime() {
        return lastForegroundTime;
    }

    public ArrayList<String> getRecentLaunchTime() {
        ArrayList<String> list = null;
        synchronized(mLaunchTime) {
            list = (ArrayList<String>) mLaunchTime.clone();
        }
        return list;
    }

    public AppRelevance getAppRelevance() {
        return mAppRelevance;
    }
}
