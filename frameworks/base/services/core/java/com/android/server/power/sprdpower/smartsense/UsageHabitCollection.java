/*
 ** Copyright 2018 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

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
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
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

public class UsageHabitCollection {

    static final String TAG = "SSense.UHabitCollection";

    private final boolean DEBUG = Util.isDebug();
    private final boolean DEBUG_MORE = false;

    private final boolean mEnabled = SystemProperties.getBoolean(USAGE_HABIT_COLLECTION_ENABLE, true);

    static final String USAGE_HABIT_COLLECTION_ENABLE = "persist.sys.ss.uhc.enable";



    private final Context mContext;

    private final AppUsageStatsCollection mAppUsageStatsCollection;

    public UsageHabitCollection(Context context) {
        mContext = context;
        mAppUsageStatsCollection = AppUsageStatsCollection.getInstance();
    }

    public void reportData(BundleData data) {

        if (DEBUG_MORE) Slog.d(TAG, "reportData:"+ " BundleData:" + data);

        switch (data.getType()) {
            case BundleData.DATA_TYPE_APP_STATE_EVENT:
                int stateEvent = data.getIntExtra(BundleData.DATA_EXTRA_APP_STATE_EVENT, 0);
                int uid = data.getIntExtra(BundleData.DATA_EXTRA_UID, -1);
                String packName = data.getStringExtra(BundleData.DATA_EXTRA_PACKAGENAME);

                mAppUsageStatsCollection.update(packName, stateEvent);
                break;
            case BundleData.DATA_TYPE_APP_TRANSITION:
                mAppUsageStatsCollection.reportAppTransition(data);
                break;
            case BundleData.DATA_TYPE_DEV_STATUS:
                int subType = data.getIntExtra(BundleData.DATA_EXTRA_SUBTYPE, -1);

                if (subType == BundleData.DATA_SUBTYPE_SCREEN) {
                    boolean screenOn = data.getBooleanExtra(BundleData.DATA_EXTRA_SCREEN_ON, false);
                    mAppUsageStatsCollection.reportScreenOn(screenOn);
                }
                break;
        }

/*
        List<String> favoriteApps = mAppUsageStatsCollection.getFavoriteAppList(3);

        if (DEBUG_MORE) {
            for (int i = 0; i < favoriteApps.size(); i++) {
                String app = favoriteApps.get(i);
                int nextInterval = mAppUsageStatsCollection.getNextFavoriteTimeInterval(app);
                Slog.d(TAG, "reportData:Favorite App:" + app + " next Favorite time interval:" + nextInterval);
            }
        }
*/
    }

    public void reportEvent(int event) {

    }
}
