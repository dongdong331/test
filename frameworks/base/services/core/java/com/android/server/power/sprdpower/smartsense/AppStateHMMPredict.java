/*
 ** Copyright 2016 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlarmManager;
import android.app.AppGlobals;
import android.app.INotificationManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.sprdpower.IPowerGuru;
import android.app.IActivityManager;
import android.app.IUidObserver;
import android.app.usage.IUsageStatsManager;
import android.app.usage.UsageEvents.Event;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.app.usage.UsageStatsManagerInternal;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.sprdpower.AppPowerSaveConfig;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IDeviceIdleController;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.sprdpower.PowerControllerInternal;
import android.os.PowerManager;
import android.os.sprdpower.PowerManagerEx;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.service.wallpaper.WallpaperService;
import android.speech.RecognizerIntent;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.SparseArray;
import android.view.inputmethod.InputMethodInfo;

import com.android.internal.view.IInputMethodManager;
import com.android.internal.util.ArrayUtils;
import com.android.server.LocalServices;
    
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.Calendar;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.os.sprdpower.Util;

public class AppStateHMMPredict {

    private static final String TAG = "SSense.AppStateHMMPredict";

    private final boolean DEBUG_LOG = Util.getDebugLog();
    private final boolean DEBUG = Util.isDebug() || DEBUG_LOG;
    private final boolean DEBUG_MORE = false;
    private final boolean TEST = PowerController.TEST;

    private static final boolean ENABLE_RARE = true;

    // use for debug, force to call bucket api
    private final boolean FORCE_PREDICT = SystemProperties.getBoolean("persist.sys.ss.predict", false);

    private static final long DELAYED_INIT_TIME = (10*1000);

    private static final int THRESHOLD_ACTIVE = (2); // 2hours
    private static final int THRESHOLD_WORKING_SET = (4); // 4hours
    private static final int THRESHOLD_FREQUENT = (6); // hours

    private static final int PREDICT_SET_INTERVAL = (1*60*60*1000); //ms, 1hour

    public static final int APP_PREDICT_STATE_ACTIVE = AppStateTracker.APP_PREDICT_STATE_ACTIVE;
    public static final int APP_PREDICT_STATE_WORKING_SET = AppStateTracker.APP_PREDICT_STATE_WORKING_SET;
    public static final int APP_PREDICT_STATE_FREQUENT = AppStateTracker.APP_PREDICT_STATE_FREQUENT;
    public static final int APP_PREDICT_STATE_RARE = AppStateTracker.APP_PREDICT_STATE_RARE;

    static final String ALARM_TAG = "AppStateHMMPredict";

    static AppStateHMMPredict sInstance;


    private final Context mContext;

    private Handler mHandler;
    private  UsageStatsManagerInternal mUsageStatsInternal;

    private AppUsageStatsCollection mAppUsageStatsCollection;

    private ArrayList<AppPredictState> mAppPredictList = new ArrayList<>();

    private AppStateTracker mAppStateTracker;
    private SmartSenseService mSmartSenseService;

    // for -1, is invalid value, for 0 is non-gms version, for 1 is gms version
    private int mIsGmsVersion = -1;

    private long mLastPredictTime = 0;

    // if call the mUsageStatsInternal.setAppStandbyBucket
    private boolean mEnableApplyStandbyBucket = false;

    // if call mUsageStatsInternal.forceAppIdleEnabled(true);
    private boolean mForceAppIdleEnabled = false;


    private PowerController.LocalService mPowerControllerInternal;

    private static class AppPredictState {
        public String mPackageName;
        public int mUid;
        public int mState;
        public boolean mStateChanged;

        public AppPredictState(String packageName, int uid, int state) {
            mPackageName = packageName;
            mUid = uid;
            mState = state;
            mStateChanged = true;
        }
    }


    public static AppStateHMMPredict getInstance(Context context) {
        synchronized (AppStateHMMPredict.class) {
            if (sInstance == null) {
                sInstance = new AppStateHMMPredict(context);
            }
            return sInstance;
        }
    }

    public AppStateHMMPredict(Context context) {
        mContext = context;
        mHandler = new H(SmartSenseService.BackgroundThread.get().getLooper());
    }


    void systemReady() {
        mAppStateTracker = LocalServices.getService(AppStateTracker.class);
        mUsageStatsInternal = LocalServices.getService(UsageStatsManagerInternal.class);

        mAppUsageStatsCollection = AppUsageStatsCollection.getInstance();
    }

    void setSenseService(SmartSenseService ss) {
        mSmartSenseService = ss;
    }

    void startPeriodCheck() {
        mAppUsageStatsCollection.registerAppUsageObserver(new AppUsageObserver(0));
    }

    void startHMM() {
        mHandler.removeMessages(MSG_INIT_HMM);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_INIT_HMM), DELAYED_INIT_TIME);
    }

    void enableApplyStandbyBucket(boolean enabled) {
        if (DEBUG) Slog.d(TAG, "enableApplyStandbyBucket:" + enabled);
        mEnableApplyStandbyBucket = enabled;
    }

    void forceAppIdleEnabled(boolean enabled) {
        if (DEBUG) Slog.d(TAG, "forceAppIdleEnabled:" + enabled);
        mForceAppIdleEnabled = enabled;
    }

    //Message define
    static final int MSG_INIT_HMM = 0;
    static final int MSG_CHECK_APPSTATE = 1;

    private class H extends Handler {
        public H(Looper looper) {
            super(looper);
        }

        String msg2Str(int msg) {
            final String msgStr[] = {"MSG_INIT",
                "MSG_CHECK_APPSTATE",
            };

            if ((0 <= msg) && (msg < msgStr.length))
                return msgStr[msg];
            else
                return "Unknown message: " + msg;
        }

        @Override
        public void handleMessage(Message msg) {
            if (DEBUG) Slog.d(TAG, "handleMessage(" + msg2Str(msg.what) + ")");
            switch (msg.what) {
                case MSG_CHECK_APPSTATE:
                    handleCheckAppState();
                    break;
                case MSG_INIT_HMM:
                    HMMInit();
                    break;
                default:
                    break;
            }
        }
    }


    private final AlarmManager.OnAlarmListener mNextCheckListener = new AlarmManager.OnAlarmListener() {
        @Override
        public void onAlarm() {
            if (DEBUG) Slog.d(TAG, "on alarm check!");
            handleCheckAppState();
        }
    };

    private AlarmManager mAlarmManager;

    private void getAlarmManager(){
        if (mAlarmManager == null) {
            mAlarmManager = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        }
    }
    private void scheduleAlarm(long delay) {
        getAlarmManager();
        if (mAlarmManager != null) {
            mAlarmManager.set(AlarmManager.ELAPSED_REALTIME,
                (SystemClock.elapsedRealtime()) + delay, ALARM_TAG, mNextCheckListener, mHandler);
        }
    }

    private void cancelAlarm() {
        getAlarmManager();
        if (mAlarmManager != null) {
            mAlarmManager.cancel(mNextCheckListener);
        }
    }

    private void handleCheckAppState() {
        mHandler.removeMessages(MSG_CHECK_APPSTATE);
        scheduleAlarm(30*60*1000);
        checkAppState();
    }

    private class AppUsageObserver extends AppUsageStatsCollection.AppUsageObserver {
        public AppUsageObserver(long duration) {
            timeDuration = duration;
            notifyFlag = FLAG_NOTIFY_ONLY_USEDAPP_UPDATED;
        }

        @Override
        public void onUsedAppUpdated() {
            if (DEBUG) Slog.d(TAG, "onUsedAppUpdated");
            cancelAlarm();
            mHandler.removeMessages(MSG_CHECK_APPSTATE);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_CHECK_APPSTATE));
        }
    }

    public void checkAppState() {
        try {
            ArrayList<AppUsageStatsHistory> currentAppUsageStatsList = mAppUsageStatsCollection.getCurrentAppUsageList();

            int[] nextUsedAppMaps = HMMUpdate();

            if (currentAppUsageStatsList != null) {
                for (int i = 0; i < currentAppUsageStatsList.size(); i++) {
                    if (DEBUG_MORE) Slog.d(TAG, "currentAppUsageStats: " + i + ":" + currentAppUsageStatsList.get(i));
                }
            }

            long nowElapsed = SystemClock.elapsedRealtime();

            if (currentAppUsageStatsList != null && nextUsedAppMaps != null) {
                int currentTimeSlot = getCurrentTimeSlot();

                if (DEBUG_LOG) {
                    for (int i = 0; i<nextUsedAppMaps.length; i++) {
                        Slog.d(TAG, "app at " + i + " is " + nextUsedAppMaps[i] + ":" + mapApp(nextUsedAppMaps[i]));
                    }
                }

                for (int i = 0; i < currentAppUsageStatsList.size(); i++) {
                    AppUsageStatsHistory currentUsage = currentAppUsageStatsList.get(i);
                    int uid = 0;
                    if (currentUsage.mPackageName != null)
                        uid = getPackageUid(currentUsage.mPackageName);

                    if (currentUsage.mPackageName == null
                        || /*getPackageUid(currentUsage.mPackageName)*/ uid <= Process.FIRST_APPLICATION_UID
                        /*|| !mSmartSenseService.isInstalledApp(currentUsage.mPackageName, 0)*/) continue;


                        int nextInterval = getNextUsedTimeInterval(currentUsage.mPackageName, nextUsedAppMaps, currentTimeSlot);
                        if (DEBUG) Slog.d(TAG, "nextInterval for " + currentUsage.mPackageName + " is " + nextInterval);
                        int newState = APP_PREDICT_STATE_ACTIVE;
                        if (enabledRareBucket() && nextInterval > THRESHOLD_FREQUENT) {
                            newState = APP_PREDICT_STATE_RARE;
                        } else if (nextInterval > THRESHOLD_WORKING_SET) {
                            newState = APP_PREDICT_STATE_FREQUENT;
                        } else if (nextInterval > THRESHOLD_ACTIVE) {
                            newState = APP_PREDICT_STATE_WORKING_SET;
                        }

                        // correct state according app type and white list
                        newState = correctState(newState, currentUsage.mPackageName, uid);

                        boolean found = false;
                        for (int index=0;index<mAppPredictList.size();index++) {
                            AppPredictState predict =  mAppPredictList.get(index);
                            if (currentUsage.mPackageName.equals(predict.mPackageName)) {
                                found = true;
                                if (predict.mState != newState) {
                                    if (DEBUG) Slog.d(TAG, "State for " + currentUsage.mPackageName + " change from " + getStateString(predict.mState)
                                       +  " to " + getStateString(newState));

                                    mAppStateTracker.setAppPredictState(currentUsage.mPackageName, 0, newState);

                                    if (mUsageStatsInternal != null && (FORCE_PREDICT || needApplyStandbyBucket()))
                                        mUsageStatsInternal.setAppStandbyBucket(currentUsage.mPackageName, getBucket(newState), 0, "ssense");

                                    predict.mState = newState;
                                    predict.mStateChanged = true;
                                }  else {
                                    if (nowElapsed - mLastPredictTime >= PREDICT_SET_INTERVAL && mLastPredictTime > 0) {
                                        if (DEBUG) Slog.d(TAG, "State for " + currentUsage.mPackageName + " is " + getStateString(newState));
                                        if (mUsageStatsInternal != null && (FORCE_PREDICT || needApplyStandbyBucket())) {
                                            mUsageStatsInternal.setAppStandbyBucket(currentUsage.mPackageName, getBucket(newState), 0, "ssense");
                                            if (DEBUG) Slog.d(TAG, "Call UsageStatsInternal.setAppStandbyBucket() !!! ");
                                        }
                                    }
                                }
                            }
                        }

                        if (!found && newState != APP_PREDICT_STATE_ACTIVE) {
                            if (DEBUG) Slog.d(TAG, "New State for " + currentUsage.mPackageName + " is " + getStateString(newState));
                            mAppStateTracker.setAppPredictState(currentUsage.mPackageName, 0, newState);

                            if (mUsageStatsInternal != null && (FORCE_PREDICT || needApplyStandbyBucket())) {
                                mUsageStatsInternal.setAppStandbyBucket(currentUsage.mPackageName, getBucket(newState), 0, "ssense");
                                if (DEBUG) Slog.d(TAG, "Call UsageStatsInternal.setAppStandbyBucket() !!! ");
                            }

                            mAppPredictList.add(new AppPredictState(currentUsage.mPackageName, 0, newState));
                        }
                }
            }

            if (mLastPredictTime == 0 || (nowElapsed - mLastPredictTime) >= PREDICT_SET_INTERVAL) {
                mLastPredictTime = SystemClock.elapsedRealtime();
            }
        } catch (Exception e) {
             Slog.w(TAG, "checkAppState Exception: " + e);
        }
        // notify 
        //for (int i=0;i<mAppPredictList.size();i++) {
        //    AppPredictState predict =  mAppPredictList.get(i);
        //    if (predict.mStateChanged) {
        //        Slog.d(TAG, "Notify new state for " + predict.mPackageName + " is " + getStateString(predict.mState));
        //        predict.mStateChanged = false;
        //    }
        //}
    }

    private int getPackageUid(String packageName) {
        if (packageName == null) return -1;
        int uid = -1;
        try {
            uid = AppGlobals.getPackageManager().getPackageUid(packageName, 0, 0);
        } catch (Exception e) {
            Slog.w(TAG, "getPackageUid for " + packageName + " Exception:" + e);
        }
        return uid;
    }

    static int[] states = new int[]{0, 1, 2};
    static int[] observations = new int[]{0, 1, 2, 3, 4, 5};
    static double[] start_probability = new double[]{0.16, 0.5, 0.34};
    static double[][] transititon_probability = new double[][]{
            {0.005, 0.99, 0.005},
            {0.01, 0.33, 0.66},
            {0.495, 0.495, 0.01}
    };
    static double[][] emission_probability = new double[][]{
            {0.99, 0.002, 0.002, 0.002, 0.002, 0.002},
            {0.0001, 0.3332, 0.0001, 0.3332, 0.3333, 0.0001},
            {0.0005, 0.0005, 0.499, 0.0005, 0.0005, 0.499}
    };


    static double[] start_probability1 = new double[]{0.1, 0.8, 0.1};
    static double[][] transititon_probability1 = new double[][]{
            {0.2, 0.6, 0.2},
            {0.3, 0.4, 0.3},
            {0.1, 0.6, 0.3}
    };
    static double[][] emission_probability1 = new double[][]{
            {0.6, 0.1, 0.1, 0.1, 0.05, 0.05},
            {0.08, 0.02, 0.6, 0.1, 0.1, 0.1},
            {0.0005, 0.0005, 0.499, 0.0005, 0.0005, 0.499}
    };

    public static void viterbiTest() {
        int result[] = Viterbi.decode(observations, states, start_probability, transititon_probability, emission_probability);
        for (int r:result) {
            Slog.d(TAG, "viterbiTest: " + r);
        }

        int result1[] = Viterbi.decode(observations, states, start_probability1, transititon_probability1, emission_probability1);
        for (int r:result1) {
            Slog.d(TAG, "viterbiTest1: " + r);
        }

    }
    private static class Viterbi {
        /**
         * @param obs : observer sequence
         * @param states : all the hide states
         * @param start_p : start probability of each hide state
         * @param trans_p : transition probability (A) between the hide state
         * @param emit_p : transition probability (B) from hide state to observer state
         * @return the hide state sequence
         */
        public static int[] decode(int[] obs, int[] states, double[] start_p, double[][] trans_p, double[][] emit_p) {
            if (obs == null || states == null || obs.length == 0 || states.length == 0) return new int[0];

            /**
             * V[t][s] save the max probability at time 't' when hide state is 's'
             * path[t][s] save the state index of 'si' that reach the current state 's'.
             * For example:
             *      when there are 3 hide state s1, s2, s3, and assume that
             *      V[t-1][s1] = p1
             *      V[t-1][s2] = p2
             *      V[t-1][s3] = p3
             *      then for time 't' to reach state 's1', the probability from s1(t-1) -> s1(t) ; s2(t-2) --> s1(t)
             *      s3(t-1) --> s1(t) are:
             *      V[t-1][s1] * A[s1][s1]
             *      V[t-1][s2] * A[s2][s1]
             *      V[t-1][s3] * A[s3][s1]
             *      if V[t-1][s3] * A[s3][s1] is the max, then path[t][s1] will set to be the index of 's3'
             *      and the V[t][s1] will update to be V[t-1][s3] * A[s3][s1]
             *      At last we choose the max probability from V[t][s1], V[t][s2], V[t][s3]. And if V[t][s1] is the max
             *      then we will track back the state index from Path[t][s1], that is s1, s3 .... (in revert order)
             */
            double[][] V = new double[obs.length][states.length];
            int[][] path = new int[obs.length][states.length];

            // init the V[0][s]
            for (int y : states) {
                V[0][y] = start_p[y] * emit_p[y][obs[0]];
                path[0][y] = 0;
            }

            for (int t = 1; t < obs.length; ++t) {
                for (int y : states) {
                    double prob = -1;
                    int state = 0;
                    for (int y0 : states) {
                        double nprob = V[t - 1][y0] * trans_p[y0][y];
                        // record the max probability and corresponding state
                        if (nprob > prob) {
                            prob = nprob;
                            state = y0;
                        }
                    }

                    // update the V[t][s] and path[t][s]
                    V[t][y] = prob * emit_p[y][obs[t]];
                    path[t][y] = state;
                }
            }

            double prob = -1;
            int state = 0;
            // find the max probability from v[T][s]
            for (int y : states) {
                if (V[obs.length - 1][y] > prob) {
                    prob = V[obs.length - 1][y];
                    state = y;
                }
            }

            int[] retpath = new int[obs.length];
            int T = obs.length - 1;

            retpath[T] = state;
            // track the hide state sequence from path[][]
            for (int t = obs.length -2; t >= 0; t--) {
                retpath[t] = path[t+1][retpath[t+1]];
            }

            return retpath;
        }
    }


    private int getCurrentTimeSlot() {
        long currentTimeMillis = System.currentTimeMillis(); //wall time

        return TimeUtil.getTimeSlot(currentTimeMillis);
    }

    private String getStateString(int state) {
        switch (state) {
            case APP_PREDICT_STATE_ACTIVE:
                return "ACTIVE";
            case APP_PREDICT_STATE_WORKING_SET:
                return "WORKING_SET";
            case APP_PREDICT_STATE_FREQUENT:
                return "FREQUENT";
            case APP_PREDICT_STATE_RARE:
                return "RARE";
            default:
                return "UN-KNOWN";
        }
    }


    ArrayList<String> mUsedAppList;

    int[] mDataSet;
    int[] mStateMaps;
    int[] mStates;
    int[] mObserverStates;
    int mStateSize = 0;
    int mObserverSize = 0;
    int mDataSetSize = 0;
    double[][] A1;
    double[][] B1;
    double[][] A2;
    double[][] B2;
    double[] InitProb;

    private static final int STATE_MAPS_VALUE_INVALID = (-2);
    private static final int STATE_MAPS_VALUE_SYSTEMAPP = (-1);


    private void HMMInit() {
        if (mUsageStatsInternal != null && (FORCE_PREDICT || mForceAppIdleEnabled))
            mUsageStatsInternal.forceAppIdleEnabled(true);

        mObserverSize = TimeUtil.getTimeSlotCount();
        mObserverStates = new int[mObserverSize];
        for (int i=0; i<mObserverSize; i++) {
            mObserverStates[i] = i;
        }

        //Slog.d(TAG, "HMMInit: mObserverSize: " + mObserverSize);

        generateDataSet();
        generateA();
        generateB();
        generateInitProb();

        //int result[] = Viterbi.decode(mObserverStates, mStates, InitProb, A1, B1);
        //if (DEBUG_MORE) {
        //    for (int r:result) {
        //        Slog.d(TAG, "HMMInit: " + r + " App:" + mapApp(r));
        //    }
        //}

    }

    private int[] HMMUpdate() {
        generateDataSet();
        generateA();
        generateB();
        generateInitProb();

        int result[] = Viterbi.decode(mObserverStates, mStates, InitProb, A1, B1);

        //Slog.d(TAG, "HMMUpdate: result.length: " + result.length + " result:" + result);
        //for (int i=0; i< result.length; i++) {
        //    Slog.d(TAG, "HMMUpdate: " + result[i] + " App:" + mapApp(result[i]));
        //}

        if (DEBUG) checkPredictResult(result);

        return result;
    }

    private void generateDataSet() {

        mUsedAppList = mAppUsageStatsCollection.getUsedAppList();
        final int[] usedAppIndexMaps = mAppUsageStatsCollection.getUsedAppIndexMaps();

        if (DEBUG_MORE) Slog.d(TAG, "generateDataSet() usedAppIndexMaps.length:" + usedAppIndexMaps.length
            + " mUsedAppList.size():" + mUsedAppList.size());

        mDataSetSize = 0;
        mDataSet = new int[usedAppIndexMaps.length];

        mStateSize = 0;
        mStateMaps = new int[mUsedAppList.size()+1];
        for (int i=0; i<mStateMaps.length; i++) {
            mStateMaps[i] = STATE_MAPS_VALUE_INVALID;
        }

        for (int i=0; i<usedAppIndexMaps.length; i++) {
            int index = usedAppIndexMaps[i];

            //String app = mUsedAppList.get(usedAppIndexMaps[i]);
            // see as a system app
            //if (app == null) {
            //    index = STATE_MAPS_VALUE_SYSTEMAPP;
            //} else {
            //    index = mUsedAppList.indexOf(app);
            //    if (index < 0)
            //        index = STATE_MAPS_VALUE_SYSTEMAPP;
            //}

            int foundIndex = -1;
            for(int j=0; j<mStateMaps.length; j++) {
                if (mStateMaps[j] == index) {
                    foundIndex = j;
                }
            }
            // save the state to mDataSet
            if (foundIndex >= 0 && foundIndex<mStateMaps.length) {
                mDataSet[i] = foundIndex;
            }

            // not found, new state
            if (foundIndex == -1) {
                mDataSet[i] = mStateSize;
                mStateMaps[mStateSize++] = index;
            }

            mDataSetSize++;
        }

        if (DEBUG_MORE) {
            Slog.d(TAG, "generateDataSet() mStateSize:" + mStateSize + " mDataSetSize:" + mDataSetSize);
            for (int i = 0; i<mStateMaps.length; i++) {
                Slog.d(TAG, "generateDataSet() mStateMaps[" + i + "]:"  + mStateMaps[i]);
            }
        }

        mStates = new int[mStateSize];
        for (int i = 0; i<mStateSize; i++) {
            mStates[i] = i;
            if (DEBUG_LOG) Slog.d(TAG, "generateDataSet() mStates[" + i + "]:"  + mStates[i] + " name:" + mapApp(mStates[i]));
        }

        if (DEBUG_MORE) {
            for (int i=0; i<mDataSetSize; i++) {
                Slog.d(TAG, "generateDataSet() mDataSet[" + i + "]:"  + mDataSet[i] + " name:" + mapApp(mDataSet[i]));
            }
        }

    }


    private void generateA() {
        A1 = new double[mStateSize][mStateSize];

        for (int i=0; i<mStateSize; i++) {
            double countI = 0;
            for (int s:mDataSet) {
                if (s == i) countI++;
            }
            // special handle for 'SYSTEM'
            if (UsedAppStats.DEFAULT_APP.equals(mapApp(i))) {
                countI += mStateSize;
            } else {
                countI += (0.1 * mStateSize);
            }

            for (int j=0; j<mStateSize; j++) {
                double countIJ = 0;
                for (int t=0; t<mDataSet.length-1; t++) {
                    if (mDataSet[t] == i && mDataSet[t+1] == j) countIJ++;
                }

                if (mDataSet[mDataSet.length-1] == i && mDataSet[0] == j)
                    countIJ++;

                // special handle for 'SYSTEM'
                if (UsedAppStats.DEFAULT_APP.equals(mapApp(i))) {
                    countIJ ++;
                } else {
                    countIJ += 0.1;
                }

                if (DEBUG_MORE) Slog.d(TAG, "generateA(): " + i + " countI:" + countI + " countIJ:" + countIJ);
                A1[i][j] = (double)countIJ/countI;
            }
        }

        if (DEBUG_MORE) {
            for (int i=0; i<mStateSize; i++) {
                for (int j=0; j<mStateSize; j++) {
                    Slog.d(TAG, "generateA() A[" + i + "][" + j + "]" + A1[i][j]);
                }
            }
        }

    }


    // should use the foreground time to calculate
    private void generateB() {
        B1 = new double[mStateSize][mObserverSize];

        if (mStateSize == 0) return;

        ArrayList<AppUsageStatsHistory> historyUsageList = mAppUsageStatsCollection.getHistoryAppUsageList();
        if (historyUsageList == null) return;

        for (int i=0; i<mStateSize; i++) {
            //int countI = 0;
            //for (int s:mDataSet) {
            //    if (s == i) countI++;
            //}
            String appName = mapApp(i);
            long totalTimeI =  getTotalUsedTimeForApp(appName, historyUsageList);
            if (DEBUG_MORE) Slog.d(TAG, "generateB() totalTime for " + appName + " is " + totalTimeI);

/********************************************************************
            if (UsedAppStats.DEFAULT_APP.equals(appName)) {
                int countI = 0;
                for (int s:mDataSet) {
                    if (s == i) countI++;
                }
                for (int j=0; j<mObserverSize; j++) {
                    int countIJ = 0;
                    int tempJ = j;
                    for (int k=0; tempJ < mDataSet.length ; k++) {
                        tempJ = mObserverSize * k + j;
                        if ( tempJ < mDataSet.length && mDataSet[tempJ] == i) countIJ++;
                    }
                    B1[i][j] = (double)countIJ/countI;
                }
                continue;
            }
**********************************************************************/
            for (int j=0; j<mObserverSize; j++) {
                //int countIJ = 0;
                //if ( j < mDataSet.length && mDataSet[j] == i) countIJ++;

                //B1[i][j] = (double)countIJ/countI;

                long usedTimeIJ = getUsedTimeForAppAtTimeSlot(appName, historyUsageList, j);
                if (DEBUG_MORE) Slog.d(TAG, "generateB() used time for " + appName + " at " + j + " is " + usedTimeIJ);

                // special handle for 'SYSTEM'
                if (UsedAppStats.DEFAULT_APP.equals(appName)) {
                    double used = (double)usedTimeIJ + 0.25;
                    double total = (double)totalTimeI + (double)mObserverSize/4;
                    B1[i][j] = used/total; //((double)usedTimeIJ + 1/4)/((double)totalTimeI + mObserverSize/4);
                    if (DEBUG_MORE) Slog.d(TAG, "generateB() used time for " + appName + " at " + j + " is " + used + " total:" + total);
                    continue;
                    //usedTimeIJ += 1;
                }

                if (totalTimeI == 0 || usedTimeIJ == 0)
                    B1[i][j] = 0;
                else
                    B1[i][j] = (double)usedTimeIJ/totalTimeI;
            }
        }

        if (DEBUG_MORE) {
            for (int i=0; i<mStateSize; i++) {
                for (int j=0; j<mObserverSize; j++) {
                    Slog.d(TAG, "generateB() B[" + i + "][" + j + "]" + B1[i][j]);
                }
            }
        }
    }

    private void generateInitProb() {

        InitProb = new double[mStateSize];

        for (int i=0; i<mStateSize; i++) {
            int countI = 0;
            for (int s:mDataSet) {
                if (s == i) countI++;
            }

            InitProb[i] = (double)countI/mDataSet.length;
        }

        if (DEBUG_MORE) {
            for (int i=0; i<mStateSize; i++) {
                Slog.d(TAG, "generateInitProb() InitProb[" + i + "]" + InitProb[i]);
            }
        }
    }

    private String mapApp(int state) {
        int appIndex = mStateMaps[state];
        if (appIndex == STATE_MAPS_VALUE_SYSTEMAPP)
            return UsedAppStats.DEFAULT_APP;

        if (appIndex < 0)
            return null;

        return mUsedAppList.get(appIndex);
    }


    private long getTotalUsedTimeForApp(String app, ArrayList<AppUsageStatsHistory> historyUsageList) {
        if (app == null) {
            Slog.w(TAG, "getTotalUsedTimeForApp: null app name!!");
            return 0;
        }

        // special handle for "SYSTEM"
        if (UsedAppStats.DEFAULT_APP.equals(app)) {
            long totalCount = 0;
            if (historyUsageList != null) {
                for (int k=0; k<TimeUtil.getTimeSlotCount(); k++) {
                    long totalTimeAppUsed = 0;
                    for (int i = 0; i < historyUsageList.size(); i++) {
                        if (k >= historyUsageList.get(i).mBucketCount) {
                            Slog.w(TAG, "getTotalUsedTimeForApp: invalid timeSlot:" + k);
                            continue;
                        }
                        totalTimeAppUsed += historyUsageList.get(i).mTimeInForeground[k];
                    }
                    if (DEBUG_MORE) Slog.d(TAG, "totalTimeAppUsed at " + k + " is " + totalTimeAppUsed);
                    if (totalTimeAppUsed == 0)
                        totalCount++;
                }
            }
            return totalCount;
        }

        long totalTime = 0;
        if (historyUsageList != null) {
            for (int i = 0; i < historyUsageList.size(); i++) {
                if (app.equals(historyUsageList.get(i).mPackageName)) {
                    for (int j=0; j<historyUsageList.get(i).mBucketCount; j++)
                        totalTime += historyUsageList.get(i).mTimeInForeground[j];
                    if (DEBUG_MORE) Slog.d(TAG, "total time for " + app + " is " + totalTime);
                }
            }
        }

        return totalTime;
    }

    private long getUsedTimeForAppAtTimeSlot(String app, ArrayList<AppUsageStatsHistory> historyUsageList,
        int timeSlot) {

        if (app == null) {
            Slog.w(TAG, "getUsedTimeForAppAtTimeSlot: null app name!!");
            return 0;
        }

        // special handle for "SYSTEM"
        if (UsedAppStats.DEFAULT_APP.equals(app)) {
            long totalCount = 0;
            if (historyUsageList != null) {
                long totalTimeAppUsed = 0;
                for (int i = 0; i < historyUsageList.size(); i++) {
                    if (timeSlot < 0 || timeSlot >= historyUsageList.get(i).mBucketCount) {
                        Slog.w(TAG, "getUsedTimeForAppAtTimeSlot: invalid timeSlot:" + timeSlot);
                        continue;
                    }
                    totalTimeAppUsed += historyUsageList.get(i).mTimeInForeground[timeSlot];
                }
                if (DEBUG_MORE) Slog.d(TAG, "totalTimeAppUsed at " + timeSlot + " is " + totalTimeAppUsed);
                if (totalTimeAppUsed == 0)
                    totalCount++;
            }
            return totalCount;
        }

        long usedTime = 0;
        if (historyUsageList != null) {
            for (int i = 0; i < historyUsageList.size(); i++) {
                if (app.equals(historyUsageList.get(i).mPackageName)) {
                    if (timeSlot < 0 || timeSlot >= historyUsageList.get(i).mBucketCount) {
                        Slog.w(TAG, "getUsedTimeForAppAtTimeSlot: invalid timeSlot:" + timeSlot);
                        return 0;
                    }
                    usedTime = historyUsageList.get(i).mTimeInForeground[timeSlot];
                    if (DEBUG_MORE) Slog.d(TAG, "used time for " + app + " at timeslot:" + timeSlot + " is " + usedTime);
                }
            }
        }

        return usedTime;
    }

    private int getNextUsedTimeInterval(String packageName, int[] nextAppMaps, int currentTimeSlot) {
        for (int i = currentTimeSlot; i<nextAppMaps.length; i++) {

            if (DEBUG_MORE) Slog.d(TAG, "app at " + i + " is " + nextAppMaps[i] + ":" + mapApp(nextAppMaps[i]));
            if (packageName.equals(mapApp(nextAppMaps[i]))) {

                return ((i - currentTimeSlot)/TimeUtil.getTimeSlotCountPerHour());
            }
        }

        for (int i = 0; i<currentTimeSlot; i++) {

            if (DEBUG_MORE) Slog.d(TAG, "app at " + i + " is " + nextAppMaps[i] + ":" + mapApp(nextAppMaps[i]));
            if (packageName.equals(mapApp(nextAppMaps[i]))) {

                return (24 + ((i - currentTimeSlot)/TimeUtil.getTimeSlotCountPerHour()));
            }
        }


        return 24; // for not found
    }


    private String getAppAtTimeSlotWithMaxForegroundTime(int timeSlot, ArrayList<AppUsageStatsHistory> cUsageList) {
        if (timeSlot < 0 || timeSlot >= TimeUtil.getTimeSlotCount()) return null;

        int maxIndex = 0;
        long maxForegroundTime = 0;
        for (int i = 0; i < cUsageList.size(); i++) {
            if (DEBUG_MORE) Slog.d(TAG, "HistoryAppUsageStats: " + i + ":" + cUsageList.get(i));
            long foregroundTime = 0;
            if (cUsageList.get(i).mDayCount <=1)
                foregroundTime = cUsageList.get(i).mTimeInForeground[timeSlot];
            else
                foregroundTime = cUsageList.get(i).mTimeInForeground[timeSlot]/cUsageList.get(i).mDayCount;

            if (foregroundTime > maxForegroundTime) {
                maxForegroundTime = foregroundTime;
                maxIndex = i;
            }
        }

        if (maxForegroundTime == 0)
            return null;

        return cUsageList.get(maxIndex).mPackageName;
    }

    private String getAppAtTimeSlotWithMaxLaunchCount(int timeSlot, ArrayList<AppUsageStatsHistory> cUsageList) {
        if (timeSlot < 0 || timeSlot >= TimeUtil.getTimeSlotCount()) return null;

        int maxIndex = 0;
        long maxLaunchCount = 0;
        for (int i = 0; i < cUsageList.size(); i++) {
            if (DEBUG_MORE) Slog.d(TAG, "HistoryAppUsageStats: " + i + ":" + cUsageList.get(i));
            long launchCount = 0;
            if (cUsageList.get(i).mDayCount <=1)
                launchCount = cUsageList.get(i).mLaunchCount[timeSlot];
            else
                launchCount = cUsageList.get(i).mLaunchCount[timeSlot]/cUsageList.get(i).mDayCount;

            if (launchCount > maxLaunchCount) {
                maxLaunchCount = launchCount;
                maxIndex = i;
            }
        }

        if (maxLaunchCount == 0)
            return null;

        return cUsageList.get(maxIndex).mPackageName;
    }

    private int getBucket(int state) {
        switch (state) {
            case APP_PREDICT_STATE_ACTIVE:
                return UsageStatsManager.STANDBY_BUCKET_ACTIVE;
            case APP_PREDICT_STATE_WORKING_SET:
                return UsageStatsManager.STANDBY_BUCKET_WORKING_SET;
            case APP_PREDICT_STATE_FREQUENT:
                return UsageStatsManager.STANDBY_BUCKET_FREQUENT;
            case APP_PREDICT_STATE_RARE:
                return UsageStatsManager.STANDBY_BUCKET_RARE;
            default:
                return UsageStatsManager.STANDBY_BUCKET_ACTIVE;
        }
    }

    private boolean needApplyStandbyBucket() {
        return mEnableApplyStandbyBucket;
    }

    private boolean enabledRareBucket() {
        return (ENABLE_RARE && isGmsVersion());
    }

    private boolean isGmsVersion() {
        if (mIsGmsVersion != -1)
            return (mIsGmsVersion == 1);

        int gms = SystemProperties.getInt("persist.sys.gms", -1);
        String buildExtraInfo = SystemProperties.get("ro.sprd.extrainfo");

        if (gms == 1 || (buildExtraInfo != null && buildExtraInfo.contains("gms"))) {
            mIsGmsVersion = 1;
        } else if (gms != -1) {
            mIsGmsVersion = 0;
        }
        return (mIsGmsVersion == 1);
    }

    private int correctState(int inState, String pkgName, int uid) {
        int outState = inState;
        if (mPowerControllerInternal == null) {
            mPowerControllerInternal = LocalServices.getService(PowerController.LocalService.class);
        }

        if (mPowerControllerInternal != null) {
             if (mPowerControllerInternal.isWhitelistApp(pkgName)) {
                outState = APP_PREDICT_STATE_ACTIVE;
                if (DEBUG) Slog.d(TAG, "correctState: " + inState + " to " + outState + " for doze white list app!");
            } else if (mPowerControllerInternal.isWhitelistApp(pkgName, uid)) {
                if (APP_PREDICT_STATE_FREQUENT == inState
                    || APP_PREDICT_STATE_RARE == inState) {
                    outState = APP_PREDICT_STATE_WORKING_SET;
                    if (DEBUG) Slog.d(TAG, "correctState: " + inState + " to " + outState + " for app has the same uid as doze white list app!");
                }
            } else if (PowerControllerInternal.APP_CATEGORY_TYPE_MESSAGE ==
                mPowerControllerInternal.getAppCategoryType(pkgName, uid)) {
                if (APP_PREDICT_STATE_FREQUENT == inState
                    || APP_PREDICT_STATE_RARE == inState) {
                    outState = APP_PREDICT_STATE_WORKING_SET;
                    if (DEBUG) Slog.d(TAG, "correctState: " + inState + " to " + outState + " for message app!");
                }
            }
        }

        return outState;
    }


    private void checkPredictResult(int result[]) {

        ArrayList<AppUsageStatsHistory> cUsageList = mAppUsageStatsCollection.getCurrentAppUsageList();
        ArrayList<AppUsageStatsHistory> historyUsageList = mAppUsageStatsCollection.getHistoryAppUsageList();

        long now = System.currentTimeMillis(); //wall time

        int currentTimeSlot = (int) (TimeUtil.getMillis(now) /TimeUtil.getTimeSlotDuration());

        int currentError = 0;
        int historyError = 0;

        if (DEBUG) Slog.w(TAG, " checkPredictResult() currentTimeSlot:" + currentTimeSlot);

        for (int i=0; i<TimeUtil.getTimeSlotCount(); i++) {
            String app1 = getAppAtTimeSlotWithMaxForegroundTime(i, cUsageList);
            int index = 0;
            String app2 = getAppAtTimeSlotWithMaxForegroundTime(i, historyUsageList);


            // see as a system app
            if (i < currentTimeSlot) {
                if (app1 == null) {
                    if (!UsedAppStats.DEFAULT_APP.equals(mapApp(result[i]))) {
                        currentError++;
                        if (DEBUG) Slog.w(TAG, " checkPredictResult() un-match at " + i
                            + " current:" +  UsedAppStats.DEFAULT_APP
                            + " predict:" + mapApp(result[i])
                            + " errorCount:" + currentError);
                    }
                } else {
                    if (!app1.equals(mapApp(result[i]))) {
                        currentError++;
                        if (DEBUG) Slog.w(TAG, " checkPredictResult() un-match at " + i
                            + " current:" +  app1
                            + " predict:" + mapApp(result[i])
                            + " errorCount:" + currentError);
                    }
                }
            }

            if (app2 == null) {
                if (!UsedAppStats.DEFAULT_APP.equals(mapApp(result[i]))) {
                    historyError++;
                    if (DEBUG) Slog.w(TAG, " checkPredictResult() un-match at " + i
                        + " history:" +  UsedAppStats.DEFAULT_APP
                        + " predict:" + mapApp(result[i])
                        + " errorCount:" + historyError);
                }
            } else {
                if (!app2.equals(mapApp(result[i]))) {
                    historyError++;
                    if (DEBUG) Slog.w(TAG, " checkPredictResult() un-match at " + i
                        + " history:" +  app2
                        + " predict:" + mapApp(result[i])
                        + " errorCount:" + historyError);
                }
            }

        }

        double currentErrorRatio = (double)currentError/(currentTimeSlot+1);
        double historyErrorRatio = (double)historyError/TimeUtil.getTimeSlotCount();

        if (DEBUG) Slog.w(TAG, " checkPredictResult(): errorRatio"
            + " history:" +  historyErrorRatio
            + " current:" + currentErrorRatio);

    }
}
