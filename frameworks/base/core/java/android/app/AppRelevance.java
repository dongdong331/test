/*
 * Copyright 2018 Spreadtrum Communications Inc.
 */
package android.app;

import android.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;


import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Calendar;

public class AppRelevance {
    private static final String TAG = "AppRelevance";
    public static final int TYPE_MORNING = 0;// 5:01 ~ 11:00
    public static final int TYPE_NOON =1;// 11:01 ~ 15:00
    public static final int TYPE_AFTERNOON = 2;// 15:01 ~ 17:00
    public static final int TYPE_SUNSET = 3;// 17:01 ~ 19:00
    public static final int TYPE_NIGHT = 4;// 19:01 ~ 5:00
    public static final int TYPE_COUNT = 5 ;
    public static final int TYPE_NOT_CARE = -1;
    public static final long DURATION_1MIN = 60 * 1000;
    public static final long DURATION_2MIN = 2 * DURATION_1MIN;
    public static final long DURATION_5MIN = 5 * DURATION_1MIN;
    public static final long DURATION_15MIN = 15 * DURATION_1MIN;
    public static final long DURATION_30MIN = 30 * DURATION_1MIN;
    public static final String CONF_TAG_APPRELEVANCE = "apprelevance";
    public static final String CONF_TAG_APPRELEVANCE_DATA = "apredata";
    public static final String ATTR_AR_TYPE = "type";
    public static final String CONF_TAG_PKG = "arpkg";
    public static final String ATTR_NAME = "name";
    public static final String ATTR_LAUNCHCOUNT = "launchcount";

    private String packageName;
    // statics other app launch in a specific duration
    // such as: wechat -->5min--->webo--->1min--->camera--->1min--->wechat
    // or static  other app launch in a specific time
    // such as :  
    // am 7:40   clock ---> wechat
    // am 8:30   wechat --->didi/metro
    // am 8:40   neteasyNews ---> wechat
    // am 11:30  wechat --->alipay
    // pm 17:30  metro/didi --->wechat
    // pm 18:30  wechat--->youku
    // pm 21:30   xxx---->xxx
    // the purpose we statics this is to keep Target process the user want to launch next in memory
    // to decrease the cold start time
    public static class RelevanceDate {
        public long launchCount;
        public long launchCountIndex;

        @Override
        public String toString() {
            return " count :"+launchCount + "index" + launchCountIndex;
        }

        public static RelevanceDate createFromXml(XmlPullParser in) {
            RelevanceDate tmp = new RelevanceDate();
            tmp.launchCount = Long.valueOf(getAttrValue(in, ATTR_LAUNCHCOUNT));
            return tmp;
        }

        public void writeToFile(XmlSerializer serializer) throws IOException, XmlPullParserException{
            serializer.attribute(null, ATTR_LAUNCHCOUNT, String.valueOf(launchCount));
        }
    }

    // for pkgname, RelevanceDate
    HashMap<Integer, HashMap<String, RelevanceDate>> mData = new HashMap<>();

    public AppRelevance (String pkgName) {
        packageName = pkgName;
        for (int i = 0; i < TYPE_COUNT; i++) {
            mData.put(Integer.valueOf(i), new HashMap<String, RelevanceDate>());
        }
    }

    public RelevanceDate getRelevanceDate(String pkgName, int type) {
        synchronized (mData) {
            if (type >=0 && type < TYPE_COUNT) {
                return mData.get(type).get(pkgName);
            }
            return null;
        }
    }

    public void update(String pkgName, int type) {
        synchronized (mData) {
            if (type >= 0 && type < TYPE_COUNT) {
                RelevanceDate data = getRelevanceDate(pkgName, type);
                if (data == null) {
                    data  = new RelevanceDate();
                    mData.get(type).put(pkgName, data);
                }
                data.launchCount++;
                updateLaunchCountIndexLmd(type);
            }
        }
    }

    void updateLaunchCountIndexLmd(int type) {
        ArrayList<RelevanceDate> relevanceDates = new ArrayList<>();
        HashMap<String, RelevanceDate> map = mData.get(type);
        if (map != null) {
            for (String key : map.keySet()) {
                relevanceDates.add(map.get(key));
            }
            Collections.sort(relevanceDates, new Comparator<RelevanceDate>() {
                @Override
                public int compare(RelevanceDate lhs, RelevanceDate rhs) {
                    return lhs.launchCount >= rhs.launchCount ? -1 : 1;
                }
            });
            for (RelevanceDate data : relevanceDates) {
                data.launchCountIndex = relevanceDates.indexOf(data);
            }
        }
    }

    public static int getType() {
        Calendar c = Calendar.getInstance();
        int minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        if (minutes > 5 * 60 && minutes <= 11 * 60) {
            return TYPE_MORNING;
        } else if (minutes > 11 * 60 && minutes <= 15 * 60) {
            return TYPE_NOON;
        } else if (minutes > 15 * 60 && minutes <= 17 * 60) {
            return TYPE_AFTERNOON;
        } else if (minutes > 17 * 60 && minutes <= 19 * 60) {
            return TYPE_SUNSET;
        } else {
            return TYPE_NIGHT;
        }
    }

    public static int getType(long idle) {
        if (idle > DURATION_2MIN) {
            return TYPE_NOT_CARE;
        } else {
            Calendar c = Calendar.getInstance();
            int minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            if (minutes > 5 * 60 && minutes <= 11 * 60) {
                return TYPE_MORNING;
            } else if (minutes > 11 * 60 && minutes <= 15 * 60) {
                return TYPE_NOON;
            } else if (minutes > 15 * 60 && minutes <= 17 * 60) {
                return TYPE_AFTERNOON;
            } else if (minutes > 17 * 60 && minutes <= 19 * 60) {
                return TYPE_SUNSET;
            } else {
                return TYPE_NIGHT;
            }
        }
    }

    public static String getAttrValue(XmlPullParser in, String name) {
        String value = null;
        for (int attrNdx = in.getAttributeCount() - 1; attrNdx >= 0; --attrNdx) {
            final String attrName = in.getAttributeName(attrNdx);
            final String attrValue = in.getAttributeValue(attrNdx);
            if (name.equals(attrName)) {
                value = attrValue;
                break;
            }
        }
        return value;
    }

    public void writeToFile(XmlSerializer serializer) throws IOException, XmlPullParserException{
        HashMap<Integer, HashMap<String, RelevanceDate>> temp = null;
        synchronized (mData) {
            temp = (HashMap<Integer, HashMap<String, RelevanceDate>>)mData.clone();
        }
        serializer.startTag(null, CONF_TAG_APPRELEVANCE);
        for (Integer i : temp.keySet()) {
            HashMap<String, RelevanceDate> map = temp.get(i);
            serializer.startTag(null, CONF_TAG_APPRELEVANCE_DATA);
            serializer.attribute(null, ATTR_AR_TYPE,String.valueOf(i));
            for(String key: map.keySet()) {
                serializer.startTag(null, CONF_TAG_PKG);
                serializer.attribute(null, ATTR_NAME, key);
                RelevanceDate data = map.get(key);
                data.writeToFile(serializer);
                serializer.endTag(null, CONF_TAG_PKG);
            }
            serializer.endTag(null, CONF_TAG_APPRELEVANCE_DATA);
        }
        serializer.endTag(null, CONF_TAG_APPRELEVANCE);
    }

    public static AppRelevance restoreFromFile(String packageName, XmlPullParser in) throws IOException, XmlPullParserException {
        AppRelevance ar = new AppRelevance(packageName);
        int event;
        HashMap<String, RelevanceDate> temp = null;
        RelevanceDate data = null;
        String pkgName = "";
        Integer type = 0;
        try {
            while (((event = in.next()) != XmlPullParser.END_DOCUMENT)) {
                final String name = in.getName();
                if (event == XmlPullParser.START_TAG) {
                    if (CONF_TAG_APPRELEVANCE_DATA.equals(name)) {
                        type = Integer.valueOf(getAttrValue(in, ATTR_AR_TYPE));
                        temp = ar.mData.get(type);
                    } else if (CONF_TAG_PKG.equals(name)) {
                        pkgName = getAttrValue(in, ATTR_NAME);
                        data = RelevanceDate.createFromXml(in);
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    if (CONF_TAG_APPRELEVANCE.equals(name)) {
                        for (Integer tp : ar.mData.keySet()) {
                            ar.updateLaunchCountIndexLmd(tp);
                        }
                        return  ar;
                    } else if (CONF_TAG_APPRELEVANCE_DATA.equals(name)) {
                        if (temp != null) {
                            ar.mData.put(type, temp);
                        }
                    } else if (CONF_TAG_PKG.equals(name)) {
                        if (temp != null && data != null) {
                            temp.put(pkgName, data);
                        }
                    }
                }
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ar;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("dump "+packageName+" Relevance:");
        HashMap<Integer, HashMap<String, RelevanceDate>> temp = null;
        synchronized (mData) {
            temp = (HashMap<Integer, HashMap<String, RelevanceDate>>)mData.clone();
        }
        for (Integer i : temp.keySet()) {
            HashMap<String, RelevanceDate> map = temp.get(i);
            pw.println("---type "+i+"---");
            for(String key: map.keySet()) {
                RelevanceDate data = map.get(key);
                pw.println(key + " " + data);
            }
        }
    }

    public void dump() {
        Log.d(TAG, "dump "+packageName+" Relevance:");
        HashMap<Integer, HashMap<String, RelevanceDate>> temp = null;
        synchronized (mData) {
            temp = (HashMap<Integer, HashMap<String, RelevanceDate>>)mData.clone();
        }
        for (Integer i : temp.keySet()) {
            HashMap<String, RelevanceDate> map = temp.get(i);
            Log.d(TAG, "---type "+i+"---");
            for(String key: map.keySet()) {
                RelevanceDate data = map.get(key);
                Log.d(TAG, key + " " + data);
            }
        }
    }
}