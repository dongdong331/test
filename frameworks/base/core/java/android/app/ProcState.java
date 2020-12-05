/*
 * Copyright © 2017 Spreadtrum Communications Inc.
 */
package android.app;

import android.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

/**
 * Created by SPREADTRUM\joe.yu on 8/17/17.
 */

public class ProcState {
    private static final String TAG = "ProcState";
    public String packageName;
    public String processName;
    public long avgTopPss;//avg pss in 'top-activity'state
    public long topPssSampleCount;//sample count of top pss
    public Integer mUserId;

    private static final String ATTR_PKG = "pkgName";
    private static final String ATTR_USERID = "userid";
    private static final String ATTR_PROC = "procName";
    private static final String ATTR_TOP_PSS = "topPss";
    private static final String ATTR_TOP_PSS_SAMPLE_COUNT = "topPssCount";

    public ProcState(String processName, Integer userId){
        this.packageName = processName;
        this.processName = processName;
        this.avgTopPss = 0;
        this.topPssSampleCount = 0;
        this.mUserId = userId;
    }
    public ProcState(String packageName, String processName, long avgTopPss,
                     long topPssSampleCount, int mUserId){
        this.packageName = packageName;
        this.processName = processName;
        this.avgTopPss = avgTopPss;
        this.topPssSampleCount = topPssSampleCount;
        this.mUserId = mUserId;
    }
    public ProcState(){
    }

    @Override
    public String toString() {
        return "UID:"+mUserId+"|pkg:"+packageName+"|proc:"+processName+"|pss:"+avgTopPss+"|cou t"+topPssSampleCount;
    }

    public void writeToFile(XmlSerializer serializer) throws IOException, XmlPullParserException{
        serializer.attribute(null, ATTR_USERID,String.valueOf(mUserId));
        serializer.attribute(null, ATTR_PKG, packageName);
        serializer.attribute(null, ATTR_PROC, processName);
        serializer.attribute(null, ATTR_TOP_PSS,String.valueOf(avgTopPss));
        serializer.attribute(null, ATTR_TOP_PSS_SAMPLE_COUNT, String.valueOf(topPssSampleCount));
    }
    public static ProcState restoreFromFile(XmlPullParser in ) throws IOException, XmlPullParserException {
        ProcState procState = new ProcState();
        for (int attrNdx = in.getAttributeCount() - 1; attrNdx >= 0; --attrNdx) {
            final String attrName = in.getAttributeName(attrNdx);
            final String attrValue = in.getAttributeValue(attrNdx);
            if (ATTR_USERID.equals(attrName)) {
                procState.mUserId = Integer.valueOf(attrValue);
            } else if(ATTR_PKG.equals(attrName)) {
                procState.packageName = attrValue;
            } else if(ATTR_PROC.equals(attrName)) {
                procState.processName = attrValue;
            } else if(ATTR_TOP_PSS.equals(attrName)) {
                procState.avgTopPss = Long.valueOf(attrValue);
            } else if(ATTR_TOP_PSS_SAMPLE_COUNT.equals(attrName)) {
                procState.topPssSampleCount = Long.valueOf(attrValue);
            } else {
                Log.e(TAG, "error attr name....:"+attrName);
            }
        }
        return procState;
    }
}
