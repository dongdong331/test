/*
 ** Copyright 2018 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.AppGlobals;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.sprdpower.IPowerGuru;
import android.app.IActivityManager;
import android.app.IUidObserver;
import android.app.INotificationManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.usage.IUsageStatsManager;
import android.app.usage.UsageEvents.Event;
import android.app.usage.UsageStatsManagerInternal;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.sprdpower.AppPowerSaveConfig;
import android.os.BatteryManager;
import android.os.BatteryManagerInternal;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IDeviceIdleController;
import android.os.sprdpower.ISceneRecognizeManagerEx;
import android.os.sprdpower.ISceneStatsNotifier;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.sprdpower.Scene;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.sprdpower.PowerManagerEx;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.Xml;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodInfo;
import android.view.GestureDetector;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;

import com.android.internal.view.IInputMethodManager;
import com.android.internal.util.XmlUtils;
import com.android.internal.R;
import com.android.server.DeviceIdleController;
import com.android.server.LocalServices;
import com.android.server.net.NetworkPolicyManagerInternal;
import com.android.server.policy.PhoneWindowManager;
import com.android.server.policy.WindowManagerPolicy.WindowManagerFuncs;
import com.android.server.wm.WindowManagerInternal;
import com.android.server.wm.WindowManagerInternal.AppTransitionListener;
import com.android.server.wm.WindowManagerService;
import android.view.WindowManagerPolicyConstants.PointerEventListener;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;
import com.android.internal.util.FastXmlSerializer;
import org.xmlpull.v1.XmlPullParserException;

import android.os.BundleData;
import android.os.sprdpower.Util;

/**
 * Receive event and convent to Scene, dispatch Scene to all registered ISceneStatsNotifier callbacks.
 */
public class SceneRecognizeService extends ISceneRecognizeManagerEx.Stub {

    private static final String TAG = "SceneRecognizeService";

    private static final boolean DEBUG = false;

    private final boolean mEnabled = SystemProperties.getBoolean(SCENE_RECOGNIZE_ENABLE, true);

    private static final String SCENE_RECOGNIZE_ENABLE = "persist.sys.ss.sr.enable";

    @SuppressLint("StaticFieldLeak")
    private static SceneRecognizeService sInstance;

    private Context mContext;

    private SceneRecognizeCollector mSceneRecognizeCollector;

    public static SceneRecognizeService getInstance(Context context) {
        synchronized (SceneRecognizeService.class) {
            if (sInstance == null) {
                sInstance = new SceneRecognizeService(context);
            }
        }
        return sInstance;
    }

    private SceneRecognizeService(Context context) {
        mContext = context;
        mSceneRecognizeCollector = new SceneRecognizeCollector(context);

        ServiceManager.addService("SceneRecognize", this);
    }

    /**
     * Report system status information to SceneCollector.
     *
     * @param data system status information
     */
    public void reportData(BundleData data) {
        if (DEBUG) {
            Slog.d(TAG, "reportData:"+ " BundleData:" + data);
        }

        if (mSceneRecognizeCollector != null) {
            mSceneRecognizeCollector.reportData(data);
        }
    }

    public void reportEvent(int event) {
        if (mSceneRecognizeCollector != null) {
            mSceneRecognizeCollector.reportEvent(event);
        }
    }


    /**
     * This function is exposed by AIDL to get current {@link Scene}s collection.
     *
     * @return a collection list of current {@link Scene}s
     * @throws RemoteException
     */
    @Override
    public List<Scene> getCurrentScene() throws RemoteException {
        if (mSceneRecognizeCollector == null) {
            if (DEBUG) {
                Slog.w(TAG, "getCurrentScene: mSceneRecognizePlugin is null.");
            }
            return null;
        }
        return mSceneRecognizeCollector.getCurrentSceneAll();
    }

    /**
     * Other module uses this function to register scene which it's interested in.
     *
     * @param callback if the status of interested scene is changed, this param is used to notify
     *                 the registered module.
     * @param type the type of scene which the registered module is interested in.
     * @throws RemoteException
     */
    @Override
    public void registerSceneStatsNotifier(ISceneStatsNotifier callback, int type)
            throws RemoteException {
        if (mSceneRecognizeCollector == null) {
            if (DEBUG) {
                Slog.e(TAG, "registerSceneStatsNotifier: mSceneRecognizePlugin is null.");
            }
            return;
        }
        mSceneRecognizeCollector.registerSceneStatusNotifier(callback, type);
    }
}
