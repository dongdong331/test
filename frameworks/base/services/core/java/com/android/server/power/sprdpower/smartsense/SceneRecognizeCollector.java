package com.android.server.power.sprdpower;

import android.app.AppGlobals;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.content.Context;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import android.graphics.Rect;
import android.graphics.Point;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.sprdpower.ISceneStatsNotifier;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.sprdpower.Scene;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;

import android.view.Display;
import android.view.IWindowManager;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import android.util.ArrayMap;
import android.util.Log;
import android.util.MathUtils;
import android.util.Slog;
import android.util.SparseArray;

import com.android.server.LocalServices;
import com.android.server.wm.WindowManagerInternal;

import java.util.ArrayList;
import java.util.List;

import android.os.BundleData;
import android.os.sprdpower.Util;

/**
 * SceneRecognizeCollector class is used to manage all sub scene recognizers
 * Created by SPREADTRUM\jonas.zhang on 18-1-30.
 */

public class SceneRecognizeCollector {

    private static final String TAG = "SSense.SRecogCollector";

    private static final boolean DEBUG_LOG = Util.getDebugLog();
    private static final boolean DEBUG = Util.isDebug() || DEBUG_LOG;
    private static final boolean DEBUG_MORE = false;

    private static final int DELAY_CHECK_INTERVAL = (2*1000); // 2s

    private static final int TOUCH_CHECK_DURATION = (60*1000); // 60s

    private Context mContext;

    private Handler mHandler;

    private final SparseArray<SceneRecognizer> mRecognizers = new SparseArray<>();


    private final IWindowManager mIWindowManager;
    private final WindowManagerInternal mWindowManagerInternal;
    private ActivityManagerInternal mActivityManagerInternal;

    private int mCurrentUserId = 0;

    public float mCurrentFPS = 0;
    public float mCurrentTouchRate = 0;

    // Apps that are visible
    private final ArrayList<String> mVisibleAppList = new ArrayList<>();

    private final ArrayList<AppBehaviorInfo> mWaitForCheckAppList = new ArrayList<>();

    private final ArrayMap<String, AppBehaviorInfo> mAppBehaviorInfoList = new ArrayMap<>();

    public long mLastFpsChangedStamp = 0;
    public  long mLastAppTranstionStamp = 0;
    public long mLastTouchChangedStamp = 0;
    public long mLastAppWindowChangedStamp = 0;

    private boolean mWindowChanged = true;


    private final String[] mInternelSystem = new String[] {
        "android",
        "com.android.systemui",
    };


    SceneRecognizeCollector(Context context) {
        mContext = context;
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mHandler = new CollectorMsgHandler(handlerThread.getLooper());
        initRecognizers();

        mIWindowManager = IWindowManager.Stub.asInterface(
                ServiceManager.getService(Context.WINDOW_SERVICE));
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
        mActivityManagerInternal = LocalServices.getService(ActivityManagerInternal.class);
    }

    void reportData(BundleData data) {
        if (DEBUG_MORE) {
            Slog.d(TAG, "reportData: BundleData:" + data);
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_REPORT_DATA, data));
    }

    public void reportEvent(int event) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_REPORT_EVENT, event, 0));
    }

    private void handleReportData(BundleData data) {
        if (DEBUG_MORE) {
            Slog.d(TAG, "handleReportData: BundleData:" + data);
        }

        // notify recognizers
        for (int i = 0; i < mRecognizers.size(); i++) {
            SceneRecognizer recognizer = mRecognizers.valueAt(i);
            if (recognizer != null) {
                recognizer.reportData(data);
            }
        }

        // handle internal
        handleDataInternal(data);
    }

    private void handleReportEvent(int event) {

        for (int i = 0; i < mRecognizers.size(); i++) {
            SceneRecognizer recognizer = mRecognizers.valueAt(i);
            if (recognizer != null) {
                recognizer.reportEvent(event);
            }
        }

        // handle internal
        handleSystemEvent2(event);
    }

    void registerSceneStatusNotifier(ISceneStatsNotifier callback, int type) {
        for (int i = 0; i < mRecognizers.size(); i++) {
            SceneRecognizer recognizer = mRecognizers.valueAt(i);
            if (recognizer != null && recognizer.match(type)) {
                recognizer.registerSceneStatusNotifier(callback);
            }
        }
    }

    void unregisterSceneStatusNotifier(ISceneStatsNotifier callback, int type) {
        for (int i = 0; i < mRecognizers.size(); i++) {
            SceneRecognizer recognizer = mRecognizers.valueAt(i);
            if (recognizer != null && recognizer.match(type)) {
                recognizer.unregisterSceneStatusNotifier(callback);
            }
        }
    }

    ArrayList<Scene> getCurrentSceneAll() {
        ArrayList<Scene> scenes = new ArrayList<>();

        for (int i = 0; i < mRecognizers.size(); i++) {
            SceneRecognizer recognizer = mRecognizers.valueAt(i);
            if (recognizer != null) {
                scenes.add(recognizer.getScene());
            }
        }

        return  scenes;
    }

    Scene getSceneByType(int type) {
        SceneRecognizer recognizer = mRecognizers.get(type);
        if (recognizer != null) {
            return recognizer.getScene();
        } else {
            if (DEBUG) {
                Slog.d(TAG, "getSceneByType: " + type + " doesn't exist.");
            }
            return null;
        }
    }

    private void initRecognizers() {
        addRecognizer(Scene.SCENE_TYPE_AUDIO, new AudioSceneRecognizer(mContext, this, mHandler));
        addRecognizer(Scene.SCENE_TYPE_VIDEO, new VideoSceneRecognizer(mContext, this, mHandler));
        addRecognizer(Scene.SCENE_TYPE_GAME, new GameSceneRecognizer(mContext, this, mHandler));
        addRecognizer(Scene.SCENE_TYPE_APP, new AppSceneRecognizer(mContext, this, mHandler));
        addRecognizer(Scene.SCENE_TYPE_EVENT, new EventSceneRecognizer(mContext, this, mHandler));
    }

    private void addRecognizer(int type, SceneRecognizer recognizer) {
        if (DEBUG) {
            Slog.d(TAG, "addRecognizer: Type " + type);
        }

        if (mRecognizers.get(type) == null) {
            mRecognizers.put(type, recognizer);
        } else {
            if (DEBUG) {
                Slog.d(TAG, "addRecognizer: Type " + type + " already exists.");
            }
        }
    }

    private void removeRecognizer(int type) {
        mRecognizers.remove(type);
    }


    // CollectorMsgHandler message define
    private static final int MSG_INIT = 0;
    private static final int MSG_REPORT_DATA = 1;
    private static final int MSG_REPORT_EVENT = 2;
    private static final int MSG_CHECK_WINDOW = 3;
    private static final int MSG_CHECK_APP_START_WINDOW = 4;
    private static final int MSG_APP_TRANSITION = 5;
    private static final int MSG_UPDATE_SCENE = 6;
    private static final int MSG_CHECK_VISIBLE_APP = 7;

    private class CollectorMsgHandler extends Handler {
        private CollectorMsgHandler(Looper looper) {
            super(looper);
        }

        String msg2Str(int msg) {
            final String msgStr[] = {"MSG_INIT",
                "MSG_REPORT_DATA",
                "MSG_REPORT_EVENT",
                "MSG_CHECK_WINDOW",
                "MSG_CHECK_APP_START_WINDOW",
                "MSG_APP_TRANSITION",
                "MSG_UPDATE_SCENE",
                "MSG_CHECK_VISIBLE_APP",
            };

            if ((0 <= msg) && (msg < msgStr.length))
                return msgStr[msg];
            else
                return "Unknown message: " + msg;
        }

        @Override
        public void handleMessage(Message msg) {
            if (DEBUG_MORE) Slog.d(TAG, "handleMessage(" + msg2Str(msg.what) + ")");

            try {
                switch (msg.what) {
                    case MSG_REPORT_DATA:
                        handleReportData((BundleData) msg.obj);
                        break;
                    case MSG_REPORT_EVENT:
                        handleReportEvent(msg.arg1);
                        break;

                    case MSG_CHECK_APP_START_WINDOW:
                        String appName = (String)msg.obj;
                        int uid = msg.arg1;
                        checkAppStartWindow(appName, uid);
                        break;
                    case MSG_CHECK_WINDOW:
                        break;
                    case MSG_APP_TRANSITION:
                        handleVisibleAppChanged();
                        break;
                    case MSG_UPDATE_SCENE:
                        updateAppScene();
                        break;
                    case MSG_CHECK_VISIBLE_APP:
                        checkSceneOfVisibleApp();
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////
    private void handleDataInternal(BundleData data) {
        int type = data.getType();
        switch (type) {
            case BundleData.DATA_TYPE_BATTERY_EVENT:
                handleBatteryEvent(data);
                break;
            case BundleData.DATA_TYPE_APP_VIDEO:
                handleAppVideoEvent(data);
                break;
            case BundleData.DATA_TYPE_SYSTEM_EVENT:
                handleSystemEvent(data);
                break;
            case BundleData.DATA_TYPE_APP_TRANSITION:
                handleAppTransition(data);
                break;
            default:
                break;
        }
    }

    private void handleBatteryEvent(BundleData data) {
        int extraState;             // DATA_EXTRA_SUBTYPE in new event
        int uid;                    // DATA_EXTRA_UID in new event
        String packageName;         // DATA_EXTRA_PACKAGENAME in new event
        Bundle rawBundle;           // DATA_EXTRA_RAW_DATA in new event
        Bundle extraData;           // used as mExtras in Scene

        if (DEBUG_MORE) {
            Slog.d(TAG, "handleBatteryEvent: enter");
        }
        int subtype = data.getIntExtra(BundleData.DATA_EXTRA_SUBTYPE, BundleData.DATA_SUBTYPE_DEFAULT);

        // Format of DATA_TYPE_BATTERY_EVENT in video scene:
        // BundleData.DATA_EXTRA_SUBTYPE:   int, e.g. BundleData.DATA_SUBTYPE_VIDEO
        // BundleData.DATA_EXTRA_UID:   uid
        // BundleData.DATA_EXTRA_PACKAGENAME:   package name associated with uid
        // BundleData.DATA_EXTRA_STATE: BundleData.STATE_START/STATE_STOP
        if (subtype == BundleData.DATA_SUBTYPE_VIDEO) {
            extraState = data.getIntExtra(BundleData.DATA_EXTRA_STATE, BundleData.STATE_STOP);

            if (DEBUG_MORE) {
                Slog.d(TAG, "DATA_EXTRA_STATE:" + extraState);
            }

            int newVideoState = 0;
            if (extraState == BundleData.STATE_START) {
                newVideoState = AppBehaviorInfo.REQUEST_VIDEO_TYPE_START_NORMAL;
            }

            // Check uid & packages name
            uid = data.getIntExtra(BundleData.DATA_EXTRA_UID, -1);
            packageName = data.getStringExtra(BundleData.DATA_EXTRA_PACKAGENAME);


            AppBehaviorInfo appInfo = getOrCreateAppBehaviorInfo(packageName, uid);

            if (appInfo == null) return;


            if ((appInfo.mRequestVideoType & AppBehaviorInfo.REQUEST_VIDEO_TYPE_START_NORMAL)  != newVideoState) {
                if (newVideoState == 0) {
                    appInfo.mRequestVideoType = (appInfo.mRequestVideoType & ~AppBehaviorInfo.REQUEST_VIDEO_TYPE_START_NORMAL);
                } else {
                    appInfo.mRequestVideoType = (appInfo.mRequestVideoType |AppBehaviorInfo.REQUEST_VIDEO_TYPE_START_NORMAL);
                }

                appInfo.mRequestVideoType = newVideoState;
                if (!alreadyInCheckList(appInfo)) {
                    mWaitForCheckAppList.add(appInfo);
                }
                mHandler.removeMessages(MSG_UPDATE_SCENE);
                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_UPDATE_SCENE), DELAY_CHECK_INTERVAL);
            }

            if (DEBUG) {
                Slog.d(TAG, "Video type for uid:" + uid + " pkg name:" + packageName +
                " is:" + newVideoState);
            }
        }else if (subtype == BundleData.DATA_SUBTYPE_AUDIO) {
            extraState = data.getIntExtra(BundleData.DATA_EXTRA_STATE, BundleData.STATE_STOP);

            int newAudioState = AppBehaviorInfo.REQUEST_AUDIO_TYPE_NONE;
            if (extraState == BundleData.STATE_START) {
                newAudioState = AppBehaviorInfo.REQUEST_AUDIO_TYPE_START;
            }

            // Check uid & packages name
            uid = data.getIntExtra(BundleData.DATA_EXTRA_UID, -1);
            packageName = data.getStringExtra(BundleData.DATA_EXTRA_PACKAGENAME);


            AppBehaviorInfo appInfo = getOrCreateAppBehaviorInfo(packageName, uid);

            if (appInfo == null) return;


            if (appInfo.mRequestAudioType != newAudioState) {
                appInfo.mRequestAudioType = newAudioState;
                if (newAudioState == AppBehaviorInfo.REQUEST_AUDIO_TYPE_START) {
                    long nowElapsed = SystemClock.elapsedRealtime();
                    appInfo.mLastAudioStartStamp = nowElapsed;
                    if (nowElapsed > appInfo.mLastScreenTypeChangedStamp)
                        appInfo.mAudioStartCountSinceScreenChanged++;
                }

                if (appInfo.mVisible) {
                    if (!alreadyInCheckList(appInfo)) {
                        mWaitForCheckAppList.add(appInfo);
                    }
                    mHandler.removeMessages(MSG_UPDATE_SCENE);
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_UPDATE_SCENE), DELAY_CHECK_INTERVAL);
                }
            }

            if (DEBUG) {
                Slog.d(TAG, "Audio type for uid:" + uid + " pkg name:" + packageName +
                " is:" + newAudioState);
            }
        } else if (subtype == BundleData.DATA_SUBTYPE_SENSOR) {
            extraState = data.getIntExtra(BundleData.DATA_EXTRA_STATE, BundleData.STATE_STOP);

            int newSensorState = 0;
            if (extraState == BundleData.STATE_START) {
                newSensorState = data.getIntExtra(BundleData.DATA_EXTRA_SENSOR, 0);
            }

            // Check uid & packages name
            uid = data.getIntExtra(BundleData.DATA_EXTRA_UID, -1);
            packageName = data.getStringExtra(BundleData.DATA_EXTRA_PACKAGENAME);


            AppBehaviorInfo appInfo = getOrCreateAppBehaviorInfo(packageName, uid);

            if (appInfo == null) return;

            appInfo.mRequestSensorType = newSensorState;

            if (DEBUG) {
                Slog.d(TAG, "sensor type for uid:" + uid + " pkg name:" + packageName +
                " is:" + newSensorState);
            }

        }

        if (DEBUG_MORE) {
            Slog.d(TAG, "handleBatteryEvent: leave");
        }
    }

    private void handleAppVideoEvent(BundleData data) {
        int extraState;             // DATA_EXTRA_SUBTYPE in new event
        int uid;                    // DATA_EXTRA_UID in new event
        String packageName;         // DATA_EXTRA_PACKAGENAME in new event
        Bundle rawBundle;           // DATA_EXTRA_RAW_DATA in new event
        Bundle extraData;           // used as mExtras in Scene

        if (DEBUG_MORE) {
            Slog.d(TAG, "handleAppVideoEvent: enter");
        }

        // Format of DATA_TYPE_APP_VIDEO in video scene:
        // BundleData.DATA_EXTRA_UID:   uid
        // BundleData.DATA_EXTRA_PACKAGENAME:   package name associated with uid
        // BundleData.DATA_EXTRA_STATE: BundleData.STATE_START/STATE_STOP

        extraState = data.getIntExtra(BundleData.DATA_EXTRA_STATE, BundleData.STATE_STOP);

        if (DEBUG_MORE) {
           Slog.d(TAG, "DATA_EXTRA_STATE:" + extraState);
        }

        int newVideoState = 0;
        if (extraState == BundleData.STATE_START) {
            newVideoState = AppBehaviorInfo.REQUEST_VIDEO_TYPE_START_CODEC;
        }

        // Check uid & packages name
        uid = data.getIntExtra(BundleData.DATA_EXTRA_UID, -1);
        packageName = data.getStringExtra(BundleData.DATA_EXTRA_PACKAGENAME);


        AppBehaviorInfo appInfo = getOrCreateAppBehaviorInfo(packageName, uid);

        if (appInfo == null) return;

        appInfo.mVideoWidth = data.getIntExtra(BundleData.DATA_EXTRA_VIDEO_WIDTH, 0);
        appInfo.mVideoHeight = data.getIntExtra(BundleData.DATA_EXTRA_VIDEO_HEIGHT, 0);

        if ((appInfo.mRequestVideoType & AppBehaviorInfo.REQUEST_VIDEO_TYPE_START_CODEC) != newVideoState) {
            if (newVideoState == 0) {
                appInfo.mRequestVideoType = (appInfo.mRequestVideoType & ~AppBehaviorInfo.REQUEST_VIDEO_TYPE_START_CODEC);
            } else {
                appInfo.mRequestVideoType = (appInfo.mRequestVideoType |AppBehaviorInfo.REQUEST_VIDEO_TYPE_START_CODEC);;
            }
            if (!alreadyInCheckList(appInfo)) {
                mWaitForCheckAppList.add(appInfo);
            }
            mHandler.removeMessages(MSG_UPDATE_SCENE);
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_UPDATE_SCENE), DELAY_CHECK_INTERVAL);
        }

        if (DEBUG) {
            Slog.d(TAG, "Video type for uid:" + uid + " pkg name:" + packageName +
            " is:" + newVideoState);
        }

        if (DEBUG_MORE) {
            Slog.d(TAG, "handleBatteryEvent: leave");
        }
    }

    private void handleSystemEvent(BundleData data) {

        int subEvent = data.getIntExtra(BundleData.DATA_EXTRA_SUBTYPE, -1);

        if (DEBUG_MORE) {
            Slog.d(TAG, "handleSystemEvent:" + subEvent);
        }

        if (BundleData.SYSTEM_EVENT_FPS_CHANGED == subEvent) {
            mCurrentFPS = data.getFloatExtra(BundleData.DATA_EXTRA_RATE, 0);
            mLastFpsChangedStamp = SystemClock.elapsedRealtime();
            mHandler.removeMessages(MSG_CHECK_VISIBLE_APP);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_CHECK_VISIBLE_APP));
        } else if (BundleData.SYSTEM_EVENT_TOUCHRATE_CHANGED == subEvent) {
            float touchRate = data.getFloatExtra(BundleData.DATA_EXTRA_RATE, 0);
            if (DEBUG) Slog.d(TAG, "current touchRate:" + touchRate + " lastTouch Rate:" + mCurrentTouchRate);
            //float newRate = (float)((touchRate + mCurrentTouchRate)*0.5);
            //if (touchRateChanged(newRate)) {
            if (touchRate != mCurrentTouchRate) {
                mCurrentTouchRate = touchRate;

                mHandler.removeMessages(MSG_CHECK_VISIBLE_APP);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CHECK_VISIBLE_APP));
            }
        }
    }

    private void handleSystemEvent2(int event) {

        if (BundleData.SYSTEM_EVENT_WINDOW_CHANGED != event) {
            return;
        }

        if (DEBUG_MORE) Slog.d(TAG, "window changed!");

        // record the timestamp
        mLastAppWindowChangedStamp = SystemClock.elapsedRealtime();

        mWindowChanged = true;

        mHandler.removeMessages(MSG_APP_TRANSITION);
        mHandler.removeMessages(MSG_CHECK_VISIBLE_APP);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_APP_TRANSITION), DELAY_CHECK_INTERVAL/2);
    }

    private void checkWindowsState(String packageName, int uid, boolean forceCheck) {

        if (!mWindowChanged) {
            if (DEBUG) {
                Slog.d(TAG, "checkWindowsState: window not changed, not need to check!!");
            }
            return;
        }

        AppBehaviorInfo appInfo = getOrCreateAppBehaviorInfo(packageName, uid);
        if (appInfo == null) return;

        if (!forceCheck && !appInfo.mVisible) {
            appInfo.mRequestScreenType = AppBehaviorInfo.REQUEST_SCREEN_TYPE_NONE;
            if (DEBUG) {
                Slog.d(TAG, "checkWindowsState: " + appInfo);
            }
            return;
        }

        // check the window state
        try {
            Rect[] rects = new Rect[3];
            rects[0] = new Rect();
            rects[1] = new Rect();
            rects[2] = new Rect();
            int findCount = mWindowManagerInternal.getWindowVisibleBounds(packageName, rects, 3);
            if (findCount > 0) {
                for (int i=0; i<findCount; i++) {
                    if (DEBUG) Slog.d(TAG, "Rect " + i + " for uid:" + uid + " pkg name:" + packageName + " is:" + rects[i]);
                }
                Rect rect = getAvailableRect(rects);
                if (rect.isEmpty()) return;

                Point baseSize = new Point();
                try {
                    mIWindowManager.getBaseDisplaySize(Display.DEFAULT_DISPLAY, baseSize);
                } catch (RemoteException e) {
                }

                appInfo = getOrCreateAppBehaviorInfo(packageName, uid);
                if (appInfo == null) return;

                int newType = AppBehaviorInfo.REQUEST_SCREEN_TYPE_NONE;
                if (isVFullScreen(rect, baseSize)) {
                    newType= AppBehaviorInfo.REQUEST_SCREEN_TYPE_VFULL;
                } else if (isHFullScreenExact(rect, baseSize)) {
                    newType = AppBehaviorInfo.REQUEST_SCREEN_TYPE_HFULL_EXACT;
                } else if (isHFullScreen(rect, baseSize)) {
                    newType = AppBehaviorInfo.REQUEST_SCREEN_TYPE_HFULL;
                } else if (isNormalScreen(rect, baseSize)) {
                    newType = AppBehaviorInfo.REQUEST_SCREEN_TYPE_NORMAL;
                } else if (isSmallScreen(rect, baseSize)) {
                    newType = AppBehaviorInfo.REQUEST_SCREEN_TYPE_SMALL;
                }else {
                    newType = AppBehaviorInfo.REQUEST_SCREEN_TYPE_NONE;
                }

                if (newType != appInfo.mRequestScreenType) {
                    if (DEBUG) Slog.d(TAG, "Screen type changed for uid:" + uid + " pkg name:" + packageName);
                    appInfo.mRequestScreenType = newType;
                    appInfo.mLastScreenTypeChangedStamp = SystemClock.elapsedRealtime();
                    appInfo.mAudioStartCountSinceScreenChanged = 0;
                }

                appInfo.mVisibleWindowCount = findCount;
                appInfo.mRequestScreenTypeForMaxWindow = appInfo.mRequestScreenType;
                if (findCount > 1) {
                    Rect maxRect = getMaxRect(rects);
                    if (maxRect.isEmpty()) return;
                    appInfo.mRequestScreenTypeForMaxWindow = getScreenType(maxRect, baseSize);
                }

            } else {

                appInfo = getOrCreateAppBehaviorInfo(packageName, uid);
                if (appInfo == null) return;

                appInfo.mRequestScreenType = AppBehaviorInfo.REQUEST_SCREEN_TYPE_NONE;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void handleAppTransition(BundleData data) {

        if (DEBUG_MORE) Slog.d(TAG, "handleAppTransition!");

        try {
            ArrayList<String> appList = data.getStringArrayListExtra(BundleData.DATA_EXTRA_VISIBLE_APPS);

            if (appList != null) {
                for (int i=0;i<appList.size();i++) {
                    updateVisibleState(appList.get(i), true);
                }
            }

            mCurrentTouchRate = 0;
            mCurrentFPS = 0;
            mLastTouchChangedStamp = SystemClock.elapsedRealtime();
            mLastAppTranstionStamp = SystemClock.elapsedRealtime();
            mWindowChanged = true;
            mHandler.removeMessages(MSG_APP_TRANSITION);
            mHandler.removeMessages(MSG_CHECK_VISIBLE_APP);
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_APP_TRANSITION), DELAY_CHECK_INTERVAL);
        } catch (Exception e) {}
    }

    private void handleVisibleAppChanged() {

        final ArrayList<String> visibleList = new ArrayList<>();

        try {
            clearVisibleState();
            for (int i=0;i<mVisibleAppList.size();i++) {
                String componentName = mVisibleAppList.get(i);
                // update window state
                String appName = ComponentName.unflattenFromString(componentName).getPackageName();
                if (isInternalSystem(appName)) continue;

                ApplicationInfo app = null;
                int uid = 0;
                try {
                    app = AppGlobals.getPackageManager().
                        getApplicationInfo(appName, 0, mCurrentUserId);
                } catch (RemoteException e) {
                    // can't happen; package manager is process-local
                }

                if (app != null) {
                    uid = app.uid;
                }

                // not care system app
                if (uid <= 10000) continue;

                checkWindowsState(appName, uid, true);

                AppBehaviorInfo appInfo = getOrCreateAppBehaviorInfo(appName, uid);
                if (appInfo == null) continue;
                appInfo.mRequestTouchRateType = AppBehaviorInfo.REQUEST_TOUCH_TYPE_NONE;
                appInfo.mRequestFpsType = AppBehaviorInfo.REQUEST_FPS_TYPE_NONE;

                if (appInfo.mRequestScreenType != AppBehaviorInfo.REQUEST_SCREEN_TYPE_NONE) {
                    appInfo.mVisible = true;
                    visibleList.add(componentName);
                }

                if (!alreadyInCheckList(appInfo)) {
                    mWaitForCheckAppList.add(appInfo);
                }
            }

        } catch (Exception e) {}

        mVisibleAppList.clear();
        try {

            List<IBinder> activityTokens = null;

            // Let's get top activities from all visible stacks
            activityTokens = mActivityManagerInternal.getTopVisibleActivities();
            final int count = activityTokens.size();

            for (int i = 0; i < count; i++) {
                IBinder topActivity =  activityTokens.get(i);
                try {
                    ComponentName comp =  ActivityManager.getService().getActivityClassForToken(topActivity);
                    if (DEBUG) Slog.d(TAG, "top comp:" + comp);
                    if (comp != null) {
                        String appName = comp.getPackageName();
                        if (!isInternalSystem(appName)) {
                            mVisibleAppList.add(comp.flattenToShortString());
                        }
                    }
                } catch (RemoteException e) {
                }
            }

            for (int i=0;i<visibleList.size();i++) {
                String name = visibleList.get(i);
                String packName = ComponentName.unflattenFromString(name).getPackageName();
                int size = mVisibleAppList.size();
                int k = 0;
                for (k=0;k<size;k++) {
                    if (mVisibleAppList.get(k).contains(packName))
                        break;
                }
                if (k == size) {
                    if (DEBUG) Slog.d(TAG, "re add " + name + " to visible app list!");
                    mVisibleAppList.add(name);
                }
            }

            for (int i=0;i<mVisibleAppList.size();i++) {
                String componentName = mVisibleAppList.get(i);
                String appName = ComponentName.unflattenFromString(componentName).getPackageName();

                ApplicationInfo app = null;
                int uid = 0;
                try {
                    app = AppGlobals.getPackageManager().
                        getApplicationInfo(appName, 0, mCurrentUserId);
                } catch (RemoteException e) {
                    // can't happen; package manager is process-local
                }

                if (app != null) {
                    uid = app.uid;
                }

                // not care system app
                if (uid <= 10000) continue;

                // update window state
                checkWindowsState(appName, uid, true);

                // update visible state
                updateVisibleState(appName, true);

                // re- get
                AppBehaviorInfo appInfo = getOrCreateAppBehaviorInfo(appName, uid);
                if (appInfo == null) continue;

                // save visible component
                appInfo.mVisibleComponent = componentName;

                // init the mScreenTypeWhenStarted
                evaluateAppStartWindow(appInfo);

                // check fps ?

                // check touch rate ?

                if (DEBUG) {
                    Slog.d(TAG, "handleVisibleAppChanged: " + appInfo);
                }

                if (!alreadyInCheckList(appInfo)) {
                    mWaitForCheckAppList.add(appInfo);
                }
            }

            // notify check scenes
            for (int i=0; i<mWaitForCheckAppList.size(); i++) {
                notifyCheckScene(mWaitForCheckAppList.get(i));
            }

            mWaitForCheckAppList.clear();

            // clear window change flag
            mWindowChanged = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void checkSceneOfVisibleApp() {

        try {
            for (int i=0;i<mVisibleAppList.size();i++) {
                String componentName = mVisibleAppList.get(i);
                String appName = ComponentName.unflattenFromString(componentName).getPackageName();

                ApplicationInfo app = null;
                int uid = 0;
                try {
                    app = AppGlobals.getPackageManager().
                        getApplicationInfo(appName, 0, mCurrentUserId);
                } catch (RemoteException e) {
                    // can't happen; package manager is process-local
                }

                if (app != null) {
                    uid = app.uid;
                }

                // not care system app
                if (uid <= 10000) continue;

                // update window state
                checkWindowsState(appName, uid, true);

                // update visible state
                updateVisibleState(appName, true);

                // re- get
                AppBehaviorInfo appInfo = getOrCreateAppBehaviorInfo(appName, uid);
                if (appInfo == null) continue;

                // save visible component
                appInfo.mVisibleComponent = componentName;

                // init the mScreenTypeWhenStarted
                evaluateAppStartWindow(appInfo);

                // check fps ?

                // check touch rate ?

                if (DEBUG) {
                    Slog.d(TAG, "checkSceneOfVisibleApp: " + appInfo);
                }

                if (!alreadyInCheckList(appInfo)) {
                    mWaitForCheckAppList.add(appInfo);
                }
            }

            // notify check scenes
            for (int i=0; i<mWaitForCheckAppList.size(); i++) {
                notifyCheckScene(mWaitForCheckAppList.get(i));
            }

            mWaitForCheckAppList.clear();

            // clear window change flag
            mWindowChanged = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void updateAppScene() {

        for (int i=0; i<mWaitForCheckAppList.size(); i++) {
            checkWindowsState(mWaitForCheckAppList.get(i).mPackageName, mWaitForCheckAppList.get(i).mUid, false);
            notifyCheckScene(mWaitForCheckAppList.get(i));
        }

        mWaitForCheckAppList.clear();
    }

    private void checkAppStartWindow(String packageName, int uid) {

        AppBehaviorInfo appInfo = getOrCreateAppBehaviorInfo(packageName, mCurrentUserId);
        if (appInfo == null) return;

        // init the mScreenTypeWhenStarted
        if (!isNormalScreenTypeForStartUp(appInfo.mScreenTypeWhenStarted) && appInfo.mAppFirstStartStamp > 0) {

            // update window state
            checkWindowsState(packageName, uid, true);

            evaluateAppStartWindow(appInfo);
        }
    }

    // import function to decide the 'mScreenTypeWhenStarted' this is usefull for video/game judgement
    private void evaluateAppStartWindow(AppBehaviorInfo appInfo) {
        if (!isNormalScreenTypeForStartUp(appInfo.mScreenTypeWhenStarted) && appInfo.mAppFirstStartStamp == 0) {
            appInfo.mAppFirstStartStamp = SystemClock.elapsedRealtime();
            appInfo.mScreenTypeWhenStarted = appInfo.mRequestScreenType;
            appInfo.mStartUpComponent = appInfo.mVisibleComponent;

            mHandler.sendMessageDelayed(
                mHandler.obtainMessage(MSG_CHECK_APP_START_WINDOW, appInfo.mUid, 0, appInfo.mPackageName),
                (10*1000));

            return;
        }

        long now = SystemClock.elapsedRealtime();
        if ((now -appInfo.mAppFirstStartStamp) > (10*1000)
            && appInfo.mAppFirstStartStamp > 0) {

            if (!isNormalScreenTypeForStartUp(appInfo.mScreenTypeWhenStarted)) {
                appInfo.mScreenTypeWhenStarted = appInfo.mRequestScreenType;
                appInfo.mStartUpComponent = appInfo.mVisibleComponent;
                if (DEBUG) Slog.d(TAG, " mScreenTypeWhenStarted: " + appInfo.mScreenTypeWhenStarted
                    +  "  for pkg name:" + appInfo.mPackageName);
            } else if (appInfo.mScreenTypeWhenStarted == AppBehaviorInfo.REQUEST_SCREEN_TYPE_VFULL
                && appInfo.mRequestScreenType == AppBehaviorInfo.REQUEST_SCREEN_TYPE_NORMAL) {
                if (DEBUG) Slog.d(TAG, " mScreenTypeWhenStarted changed from " + appInfo.mScreenTypeWhenStarted
                    + " to " + appInfo.mRequestScreenType
                    + " for pkg name:" + appInfo.mPackageName
                    + " mLastScreenTypeChangedStamp:" + appInfo.mLastScreenTypeChangedStamp
                    + " now:" + now);

                appInfo.mScreenTypeWhenStarted = appInfo.mRequestScreenType;
                appInfo.mStartUpComponent = appInfo.mVisibleComponent;
            } else if (isHFullScreen(appInfo.mScreenTypeWhenStarted)) {

                if (appInfo.mRequestScreenType != appInfo.mScreenTypeWhenStarted
                    && isNormalScreenTypeForStartUp(appInfo.mRequestScreenType)
                    && appInfo.mVisibleComponent != null
                    && appInfo.mVisibleComponent.equals(appInfo.mStartUpComponent)) {
                    appInfo.mScreenTypeWhenStarted  = appInfo.mRequestScreenType;

                    if (DEBUG) Slog.d(TAG, " mScreenTypeWhenStarted changed from " + appInfo.mScreenTypeWhenStarted
                        + " to " + appInfo.mRequestScreenType
                        + " for pkg name:" + appInfo.mPackageName
                        + " mLastScreenTypeChangedStamp:" + appInfo.mLastScreenTypeChangedStamp
                        + " now:" + now);
                }
            }

        }

    }

    private boolean alreadyInCheckList(AppBehaviorInfo appInfo) {

        for (int i=0; i<mWaitForCheckAppList.size(); i++) {
            if (mWaitForCheckAppList.get(i).mPackageName.equals(appInfo.mPackageName))
                return true;
        }
        return false;
    }

    private void notifyCheckScene(AppBehaviorInfo appInfo) {

        for (int i = 0; i < mRecognizers.size(); i++) {
            SceneRecognizer recognizer = mRecognizers.valueAt(i);
            if (recognizer != null) {
                recognizer.checkScene(appInfo);
            }
        }
    }

    private boolean isFullScreen(Rect rect, Point baseSize) {
        if (rect.isEmpty()) return false;

        if (DEBUG_LOG) {
            Slog.d(TAG, "isFullScreen rect:" + rect + " baseSize:" + baseSize);
        }

        if ((rect.right - rect.left) == baseSize.x
            && (rect.bottom - rect.top) == baseSize.y) {
            if (DEBUG) Slog.d(TAG, "V FullScreen");
            return true;
        }

        if ((rect.right - rect.left) == baseSize.y
            && (rect.bottom - rect.top) == baseSize.x) {
            if (DEBUG) Slog.d(TAG, "H FullScreen");
            return true;
        }

        return false;
    }

    private boolean isVFullScreen(Rect rect, Point baseSize) {
        if (rect.isEmpty()) return false;

        if (DEBUG_LOG) {
            Slog.d(TAG, "isVFullScreen rect:" + rect + " baseSize:" + baseSize);
        }

        if ((rect.right - rect.left) == baseSize.x
            && (rect.bottom - rect.top) == baseSize.y) {
            if (DEBUG) Slog.d(TAG, "V FullScreen");
            return true;
        }

        return false;
    }

    private boolean isHFullScreen(Rect rect, Point baseSize) {
        if (rect.isEmpty()) return false;

        if (DEBUG_LOG) {
            Slog.d(TAG, "isHFullScreen rect:" + rect + " baseSize:" + baseSize);
        }

        if ((rect.right - rect.left) == baseSize.y
            && (rect.bottom - rect.top) >= (baseSize.x - 100)) {// 100 is consider for the height of status bar 
            if (DEBUG) Slog.d(TAG, "H FullScreen");
            return true;
        }
        return false;
    }

    private boolean isHFullScreenExact(Rect rect, Point baseSize) {
        if (rect.isEmpty()) return false;

        if (DEBUG_LOG) {
            Slog.d(TAG, "isHFullScreen rect:" + rect + " baseSize:" + baseSize);
        }

        if ((rect.right - rect.left) == baseSize.y
            && (rect.bottom - rect.top) == baseSize.x ) {
            if (DEBUG) Slog.d(TAG, "Exact H FullScreen");
            return true;
        }
        return false;
    }

    private boolean isNormalScreen(Rect rect, Point baseSize) {
        if (rect.isEmpty()) return false;

        if (DEBUG_LOG) {
            Slog.d(TAG, "isNormalScreen rect:" + rect + " baseSize:" + baseSize);
        }

        if ((rect.right - rect.left) == baseSize.x
            && (rect.bottom - rect.top) >= (baseSize.y - 100)) {// 100 is consider for the height of status bar 
            if (DEBUG) Slog.d(TAG, "Normal Screen");
            return true;
        }
        return false;
    }

    private boolean isSmallScreen(Rect rect, Point baseSize) {
        if (rect.isEmpty()) return false;

        if (DEBUG_LOG) {
            Slog.d(TAG, "isSmallScreen rect:" + rect + " baseSize:" + baseSize);
        }

        if ((rect.right - rect.left) >= 400
            && (rect.bottom - rect.top) >= 300) {
            if (DEBUG) Slog.d(TAG, "Small Screen");
            return true;
        }
        return false;
    }

    private boolean isMatchFullScreen(int width, int height) {

        if (width == 0 || height == 0) return false;


        // check the window state
        try {
            Point baseSize = new Point();
            try {
                mIWindowManager.getBaseDisplaySize(Display.DEFAULT_DISPLAY, baseSize);
            } catch (RemoteException e) {
            }

            if (baseSize.x == 0 || baseSize.y == 0) return false;

            if (DEBUG) {
                Slog.d(TAG, "isMatchFullScreen width:" + width + " height:" + height + " baseSize:" + baseSize);
            }

            float ratio1 = (float) baseSize.x/baseSize.y;
            float ratio2 = (float) height / width;

            if (MathUtils.abs(ratio1-ratio2) < 0.2) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private boolean isHFullScreen(int screenState) {
        return ((AppBehaviorInfo.REQUEST_SCREEN_TYPE_HFULL == screenState
            || AppBehaviorInfo.REQUEST_SCREEN_TYPE_HFULL_EXACT == screenState));
    }

    private boolean isNormalScreenTypeForStartUp(int screenState) {
        return !((AppBehaviorInfo.REQUEST_SCREEN_TYPE_NONE == screenState
            || AppBehaviorInfo.REQUEST_SCREEN_TYPE_SMALL == screenState));
    }

    private boolean highFps(AppBehaviorInfo appInfo) {

        if (DEBUG) Slog.d(TAG, "highFps mCurrentFPS:" + mCurrentFPS);

        return (mCurrentFPS > 20.0f);
    }

    private boolean highTouchRate() {

        if (mCurrentTouchRate > 2.0f) {
            return true;
        }

        long now = SystemClock.elapsedRealtime();
        if (mLastTouchChangedStamp > 0) {
            if (now - mLastTouchChangedStamp > TOUCH_CHECK_DURATION && mLastTouchChangedStamp >= mLastAppTranstionStamp)
                return false;
        }

        if (DEBUG) Slog.d(TAG, "highTouchRate mLastTouchChangedStamp:" + mLastTouchChangedStamp + " mLastAppTranstionStamp:" + mLastAppTranstionStamp
            + " now:" + now
            + " mCurrentTouchRate:" + mCurrentTouchRate);

        return true;
    }

    private boolean touchRateChanged(float newRate) {

        boolean changed = false;
        if ((newRate > 1.0f && mCurrentTouchRate < 1.0f)
            || (newRate < 1.0f && mCurrentTouchRate > 1.0f)) {
            changed = true;
        }
        mCurrentTouchRate = newRate;
        if (changed || mLastTouchChangedStamp == 0 ) {
            mLastTouchChangedStamp = SystemClock.elapsedRealtime();
        }

        long now = SystemClock.elapsedRealtime();
        // not changed, check if expired for TOUCH_CHECK_DURATION
        if (!changed) {
            if (mLastTouchChangedStamp > 0) {
                if (now - mLastTouchChangedStamp <= (TOUCH_CHECK_DURATION + TOUCH_CHECK_DURATION/2))
                    changed = true;
            }
        }

        if (DEBUG) Slog.d(TAG, "touchRateChanged mLastTouchChangedStamp:" + mLastTouchChangedStamp + " mLastAppTranstionStamp:" + mLastAppTranstionStamp
            + " now:" + now
            + " mCurrentTouchRate:" + mCurrentTouchRate
            + " changed:" + changed);

        return changed;
    }


    private Rect getAvailableRect(Rect[] rects) {
        int count = rects.length;
        for(int i =0; i<count; i++) {
            if (rects[i].isEmpty()) continue;
            if (isNormalBounds(rects[i])) return rects[i];
        }
        return rects[0];
    }

    private Rect getMaxRect(Rect[] rects) {
        int count = rects.length;
        int maxArea = 0;
        int maxIndex = 0;
        for(int i =0; i<count; i++) {
            if (rects[i].isEmpty()) continue;
            int area = (rects[i].right - rects[i].left) * (rects[i].bottom - rects[i].top);
            if (area > maxArea) {
                maxArea = area;
                maxIndex = i;
            }
        }
        return rects[maxIndex];
    }

    private boolean isNormalBounds(Rect rect) {
        if (rect.isEmpty()) return false;

        if (DEBUG) {
            Slog.d(TAG, "isNormalBounds rect:" + rect);
        }

        if ((rect.right - rect.left) >= 200
            && (rect.bottom - rect.top) >= 200) {
            if (DEBUG) Slog.d(TAG, "Normal Bounds");
            return true;
        }
        return false;
    }

    private int getScreenType(Rect rect, Point baseSize) {
        int newType = AppBehaviorInfo.REQUEST_SCREEN_TYPE_NONE;
        if (isVFullScreen(rect, baseSize)) {
            newType= AppBehaviorInfo.REQUEST_SCREEN_TYPE_VFULL;
        } else if (isHFullScreenExact(rect, baseSize)) {
            newType = AppBehaviorInfo.REQUEST_SCREEN_TYPE_HFULL_EXACT;
        } else if (isHFullScreen(rect, baseSize)) {
            newType = AppBehaviorInfo.REQUEST_SCREEN_TYPE_HFULL;
        } else if (isNormalScreen(rect, baseSize)) {
            newType = AppBehaviorInfo.REQUEST_SCREEN_TYPE_NORMAL;
        } else if (isSmallScreen(rect, baseSize)) {
            newType = AppBehaviorInfo.REQUEST_SCREEN_TYPE_SMALL;
        }else {
            newType = AppBehaviorInfo.REQUEST_SCREEN_TYPE_NONE;
        }
        return newType;
    }

    private long abs(long v) {
        return v > 0 ? v : -v;
    }

   private boolean isInternalSystem(String appName) {
        if (appName == null) return false;
        for(String s : mInternelSystem) {
            if(appName.equals(s)) {
                return true;
            }
        }

        return false;
    }

    private void updateVisibleState(String packageName, boolean visible) {
        AppBehaviorInfo appInfo = mAppBehaviorInfoList.get(packageName);
        if (appInfo != null) {
            appInfo.mVisible = visible;
        }
    }

    private void clearVisibleState() {
        for (int i=0;i<mAppBehaviorInfoList.size();i++) {
            AppBehaviorInfo appInfo =  mAppBehaviorInfoList.valueAt(i);
            if (appInfo != null) {
                appInfo.mVisible = false;
            }
        }
    }

    private AppBehaviorInfo getOrCreateAppBehaviorInfo(String packageName, int uid) {
        if (packageName == null) return null;
        AppBehaviorInfo appInfo = mAppBehaviorInfoList.get(packageName);
        if (appInfo == null) {
            appInfo = new AppBehaviorInfo(packageName, uid);
            mAppBehaviorInfoList.put(packageName, appInfo);
            if (DEBUG) Slog.d(TAG, "new AppBehaviorInfo for " + packageName);
        }
        return appInfo;
    }

}
