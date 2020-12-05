package com.android.server.boardScore;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;
import android.os.SystemProperties;
import android.os.Handler;
import android.os.HandlerThread;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import android.os.RemoteException;
import android.util.Log;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.content.pm.PackageManager;
import android.app.IProcessObserver;

public class BoardScoreService {
    private static final String TAG = "BoardScore";
    private Context mContext;
    private Handler mHandler;
    private HandlerThread mThread;
    private static final byte[] lock = new byte[0];
    private String[] mForegroundPkgs = null;
    private int mForegroundUid = -1;
    private Scenario mCurScenario = null;
    private int mCurUid = -1;
    private String mCurrentPkg = null;
    private List<Scenario> mScenarioList;
    PackageManager mPackageManager;

    public BoardScoreService(Context context) {
        mContext = context;
        mPackageManager = mContext.getPackageManager();
        mThread = new HandlerThread(TAG);
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mHandler.post(mXmlParserRunnable);
    }

    private Runnable mXmlParserRunnable = new Runnable() {
        @Override
        public void run() {
            ScenarioXmlParser thmScenarioXml = new ScenarioXmlParser(mContext, mHandler);
            mScenarioList = thmScenarioXml.initScenariosFromXml();
            if (mScenarioList == null || mScenarioList.size() <= 0){
                Log.d(TAG,"mScenarioList is null , parser Xml error");
                return;
            }

            try {
                Log.d(TAG,"Start monitoring the foreground process");
                ActivityManagerNative.getDefault().registerProcessObserver(mProcessObserver);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException... e:" + e);
            }
        }
    };

    private Runnable mStartTripRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (lock) {
                if (mForegroundPkgs == null) {
                    Log.e(TAG,  "Error: mForegroundPkgs == null");
                    return;
                }
                if (mScenarioList == null) {
                    Log.e(TAG,  "Error: mScenarioList == null");
                    return;
                }
                for (String psname : mForegroundPkgs) {
                    for (int n = 0; n < mScenarioList.size(); n++) {
                        Scenario scenario = mScenarioList.get(n);
                        //Log.d(TAG, "scenario:  " + n );
                        for (int k = 0 ; k < scenario.packageList.size(); k++){
                            String pkname = scenario.packageList.get(k);
                            //Log.d(TAG, "pkname[" + k + "]=" + pkname);
                            if (psname.contains(pkname)) {
                                if (mCurScenario != null) {
                                    Log.d(TAG,  "will start new trip, end the old trip UID="+ mCurUid + " pkgName=" + mCurrentPkg);
                                    mCurScenario.tripAction(mCurScenario.endTrip);
                                    mCurScenario = null;
                                }
                                mCurScenario = scenario;
                                mCurUid = mForegroundUid;
                                mCurrentPkg = psname;
                                Log.d(TAG, "get " + psname + " UID:" + mCurUid + "  belong the scenario of " + scenario.getType());
                                mCurScenario.tripAction(mCurScenario.startTrip);
                                return;
                            }
                        }
                    }
                }
            }
        }
    };

    private Runnable mEndTripRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (lock) {
                if (mCurScenario != null) {
                    Log.d(TAG,  "tripAction endTrip UID=" + mCurUid + " pkgName=" + mCurrentPkg);
                    mCurScenario.tripAction(mCurScenario.endTrip);
                    mCurScenario = null;
                    mCurUid = -1;
                }
            }
        }
    };

    final IProcessObserver mProcessObserver = new IProcessObserver.Stub() {
        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            if (ActivityManager.isUserAMonkey()) {
                return;
            }
            synchronized (lock) {
                if (foregroundActivities) {
                    String pkgName = mPackageManager.getNameForUid(uid);
                    if (pkgName == null) {
                        Log.e(TAG,  "ERR: pkgName == null pid=" + pid + " uid=" + uid );
                        return;
                    }
                    if (pkgName.contains("android.uid.system")) {
                        return;
                    }
                    mForegroundPkgs = mPackageManager.getPackagesForUid(uid);
                    if (mForegroundPkgs == null) {
                        Log.e(TAG,  "ERR: mForegroundPkgs == null pid=" + pid + " uid=" + uid );
                        return;
                    }
                    //Log.d(TAG,  "Foregroud pid = " + pid + " --uid: " + uid + " pkgName:" + pkgName);
                    mForegroundUid = uid;
                    mHandler.removeCallbacks(mStartTripRunnable);
                    mHandler.post(mStartTripRunnable);
                } else if (mCurUid == uid) {
                    mHandler.removeCallbacks(mEndTripRunnable);
                    mHandler.post(mEndTripRunnable);
                }
            }
        }

        @Override
        public void onProcessDied(int pid, int uid) {
            //Log.d(TAG, " onProcessDied pid = " + pid + " --uid: " + uid);
            synchronized (lock) {
                if (mCurUid == uid) {
                    mHandler.removeCallbacks(mEndTripRunnable);
                    mHandler.post(mEndTripRunnable);
                }
           }
        }
    };
}
