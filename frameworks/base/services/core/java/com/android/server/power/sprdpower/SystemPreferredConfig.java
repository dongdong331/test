/*
 ** Copyright 2016 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AppGlobals;
import android.app.PendingIntent;
import android.app.IActivityManager;
import android.app.IUidObserver;
import android.app.usage.IUsageStatsManager;
import android.app.usage.UsageEvents.Event;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.app.usage.UsageStatsManagerInternal;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
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
import android.os.sprdpower.PowerManagerEx;
import android.print.PrintManager;
import android.printservice.PrintServiceInfo;
import android.provider.Settings;
import android.telecom.DefaultDialerManager;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;

import android.service.quicksettings.TileService;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.Xml;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.WindowManager;

import com.android.internal.view.IInputMethodManager;
import com.android.internal.util.XmlUtils;
import com.android.internal.R;
import com.android.internal.telephony.SmsApplication;
import com.android.server.DeviceIdleController;
import com.android.server.LocalServices;
import com.android.server.net.NetworkPolicyManagerInternal;
import com.android.server.wm.WindowManagerService;
import com.android.server.policy.WindowManagerPolicy.WindowManagerFuncs;

import libcore.io.IoUtils;

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
import java.nio.charset.StandardCharsets;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;
import com.android.internal.util.FastXmlSerializer;
import org.xmlpull.v1.XmlPullParserException;


public class SystemPreferredConfig {

    static final String TAG = "PowerController.SysPrefConfig";

    static SystemPreferredConfig sInstance;

    private final boolean DEBUG = isDebug();

    static final String WALLPAPER_INFO = "wallpaper_info.xml";

    private static final char ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR = ':';

    final static TextUtils.SimpleStringSplitter sStringColonSplitter =
            new TextUtils.SimpleStringSplitter(ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR);

    private SettingsObserver mSettingsObserver;

    private final Context mContext;

    private ComponentName mDefaultWallpaperComp;
    private ComponentName mDefaultSmsComp;
    private String mDefaultPhoneApp;

    private List<ComponentName> mEnabledAccessibilityServiceList = new ArrayList<>();
    private List<ComponentName> mInstalledAccessibilityServiceList = new ArrayList<>();
    private List<ComponentName> mTTSServiceList = new ArrayList<>();
    private List<ComponentName> mPrintServiceList = new ArrayList<>();

    // add for bug#966540
    private List<ComponentName> mInstalledTileServiceList = new ArrayList<>();

    private final String[] mInternalTTSActionList = new String[] {
        "com.iflytek.vflynote.synthesize",
    };

    private final String[] mInternalServiceActionList = new String[] {
        "android.accessibilityservice.AccessibilityService", //AccessibilityService.SERVICE_INTERFACE,
        "android.media.midi.MidiDeviceService", //MidiDeviceService.SERVICE_INTERFACE,
        "android.net.VpnService", //VpnService.SERVICE_INTERFACE,
        //HostApduService.SERVICE_INTERFACE,
        //HostNfcFService.SERVICE_INTERFACE,
        "android.printservice.PrintService", //PrintService.SERVICE_INTERFACE,
        "android.service.carrier.CarrierMessagingService", //CarrierMessagingService.SERVICE_INTERFACE,
        "android.service.chooser.ChooserTargetService", //ChooserTargetService.SERVICE_INTERFACE,
        "android.service.dreams.DreamService", //DreamService.SERVICE_INTERFACE,
        "android.media.browse.MediaBrowserService", //MediaBrowserService.SERVICE_INTERFACE,
        "android.service.textservice.SpellCheckerService", //SpellCheckerService.SERVICE_INTERFACE,
        "android.service.voice.VoiceInteractionService", //VoiceInteractionService.SERVICE_INTERFACE,
        "android.service.vr.VrListenerService", //VrListenerService.SERVICE_INTERFACE,
        "android.service.wallpaper.WallpaperService", //WallpaperService.SERVICE_INTERFACE,
        "android.speech.RecognitionService", //RecognitionService.SERVICE_INTERFACE,
        "android.telecom.CallScreeningService", //CallScreeningService.SERVICE_INTERFACE,
        "android.telecom.ConnectionService", //ConnectionService.SERVICE_INTERFACE,
        "android.telecom.InCallService",  //InCallService.SERVICE_INTERFACE,
        "android.view.InputMethod", //InputMethod.SERVICE_INTERFACE,
    };

    private boolean mLoadUsageStats = false;
    private ArrayMap<String, UsageStats> mUsageStatsMap = new ArrayMap<>();
    private boolean mBootCompleted = false;


    public static SystemPreferredConfig getInstance(Context context, Handler handler) {
        synchronized (SystemPreferredConfig.class) {
            if (sInstance == null) {
                sInstance = new SystemPreferredConfig(context, handler);
            }
            return sInstance;
        }
    }

    public SystemPreferredConfig(Context context, Handler handler) {
        mContext = context;
        getEnabledServiceList();
        mSettingsObserver = new SettingsObserver(handler, mContext.getContentResolver());
        // loadWallpaperSettings(UserHandle.USER_SYSTEM);

        try {
            loadPreferredConfigs();
        } catch (Exception e) {}
    }

    public ComponentName getDefaultWallpaperComponent() {
        return mDefaultWallpaperComp;
    }

    public boolean isDefaultWallpaperService(String pkgName) {
        return (pkgName != null
            && mDefaultWallpaperComp != null
            && pkgName.equals(mDefaultWallpaperComp.getPackageName()));
    }

    public boolean isInstalledAccessibilityService(ComponentName comp) {
        return mInstalledAccessibilityServiceList.contains(comp);
    }

    public boolean isEnabledAccessibilityService(ComponentName comp) {
        return mEnabledAccessibilityServiceList.contains(comp);
    }

    public boolean isInstalledTTSService(String pkgName) {
        if(pkgName == null) return false;
        for (int i = 0, count = mTTSServiceList.size(); i < count; ++i) {
             if(pkgName.equals(mTTSServiceList.get(i).getPackageName())) return true;
        }
        return false;
    }

    public boolean isTTSAction(String action) {
        for(String s : mInternalTTSActionList) {
            if(s.equals(action)) {
                return true;
            }
        }

        if (TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE.equals(action))
            return true;

        return false;
    }

    public boolean isDefaultSmsApp(ComponentName comp) {
        return (comp != null
            && mDefaultSmsComp != null
            && comp.equals(mDefaultSmsComp));
    }

    public boolean isDefaultSmsApp(String pkgName) {
        return (pkgName != null
            && mDefaultSmsComp != null
            && pkgName.equals(mDefaultSmsComp.getPackageName()));
    }

    public boolean isDefaultPhoneApp(String pkgName) {
        return (pkgName != null
            && mDefaultPhoneApp != null
            && pkgName.equals(mDefaultPhoneApp));
    }

    public boolean isInstalledPrintService(ComponentName comp) {
        return mPrintServiceList.contains(comp);
    }

    public boolean isInstalledTileService(ComponentName comp) {
        return mInstalledTileServiceList.contains(comp);
    }

    // for bug#841035 --START
    public boolean isFirstLaunched(String pkgName) {
        if (!mBootCompleted && mLoadUsageStats) loadUsageStats();
        UsageStats existingStats =
                mUsageStatsMap.get(pkgName);

        if (existingStats != null)
            return false;
        return true;
    }

    public void onAppRemoved(String pkgName) {
        mUsageStatsMap.remove(pkgName);
    }
    // for bug#841035 --END


    public void setLoadUsageStats(boolean load) {
        mLoadUsageStats = load;
    }

    public void initDataForBootCompleted() {
        boolean changed = false;

        loadInstalledAccessibilityServiceList();
        //loadTTSServiceList();
        loadPrintServices();
        changed = loadTileServiceList();


        //
        mDefaultSmsComp = SmsApplication.getDefaultSmsApplication(mContext, true);
        mDefaultPhoneApp = DefaultDialerManager.getDefaultDialerApplication(mContext);
        if (DEBUG) Slog.d(TAG, "mDefaultSmsComp:" + mDefaultSmsComp + " mDefaultPhoneApp:" + mDefaultPhoneApp);

        // for bug#841035
        loadUsageStats();
        mBootCompleted = true;


        if (changed) {
            try {
                writePreferredConfigs();
            } catch (Exception e) {}
        }
    }

    public void onAppInstalled() {
        boolean changed = false;
        loadInstalledAccessibilityServiceList();
        loadPrintServices();
        changed = loadTileServiceList();

        if (changed) {
            try {
                writePreferredConfigs();
            } catch (Exception e) {}
        }
    }

    private static File getWallpaperDir(int userId) {
        return Environment.getUserSystemDirectory(userId);
    }

    private void loadWallpaperSettings(int userId) {
        if (DEBUG) Slog.d(TAG, "loadSettingsLocked");

        FileInputStream stream = null;
        File file = new File(getWallpaperDir(userId), WALLPAPER_INFO);
;
        if (!file.exists()) {
            // This should only happen one time, when upgrading from a legacy system
            return;
        }

        boolean success = false;
        try {
            stream = new FileInputStream(file);
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(stream, StandardCharsets.UTF_8.name());

            int type;
            do {
                type = parser.next();
                if (type == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("wp".equals(tag)) {
                        // Common to system + lock wallpapers

                        // A system wallpaper might also be a live wallpaper
                        String comp = parser.getAttributeValue(null, "component");
                        mDefaultWallpaperComp = ComponentName.unflattenFromString(comp);

                    } else if ("kwp".equals(tag)) {
                    }
                }
            } while (type != XmlPullParser.END_DOCUMENT);
            success = true;
        } catch (FileNotFoundException e) {
            Slog.w(TAG, "no current wallpaper -- first boot?");
        } catch (NullPointerException e) {
            Slog.w(TAG, "failed parsing " + file + " " + e);
        } catch (NumberFormatException e) {
            Slog.w(TAG, "failed parsing " + file + " " + e);
        } catch (XmlPullParserException e) {
            Slog.w(TAG, "failed parsing " + file + " " + e);
        } catch (IOException e) {
            Slog.w(TAG, "failed parsing " + file + " " + e);
        } catch (IndexOutOfBoundsException e) {
            Slog.w(TAG, "failed parsing " + file + " " + e);
        }
        IoUtils.closeQuietly(stream);

        if (DEBUG) Slog.d(TAG, "mDefaultWallpaperComp:" + mDefaultWallpaperComp);

    }


    private void loadInstalledAccessibilityServiceList () {

        AccessibilityManager accessibilityManager = AccessibilityManager.getInstance(mContext);

        try {
            List<AccessibilityServiceInfo> installedServices =
                    accessibilityManager.getInstalledAccessibilityServiceList();

            mInstalledAccessibilityServiceList.clear();
            for (int i = 0, count = installedServices.size(); i < count; ++i) {
                AccessibilityServiceInfo info = installedServices.get(i);

                ServiceInfo serviceInfo = info.getResolveInfo().serviceInfo;
                ComponentName componentName = new ComponentName(serviceInfo.packageName,
                        serviceInfo.name);

                mInstalledAccessibilityServiceList.add(componentName);
            }
        } catch (Exception e) {}

        if (DEBUG) {
            for (int i = 0, count = mInstalledAccessibilityServiceList.size(); i < count; ++i) {
                Slog.d(TAG, "Installed Accessibility Service: " + mInstalledAccessibilityServiceList.get(i));
            }
        }
    }


    private void loadTTSServiceList() {
        if (DEBUG) Slog.d(TAG, "loadTTSServiceList");

        try {
            final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            final List<ResolveInfo> rList = mContext.getPackageManager().queryIntentActivities(intent,
                    /*PackageManager.MATCH_DEFAULT_ONLY*/0);

            mTTSServiceList.clear();
            for (ResolveInfo info : rList) {

                mTTSServiceList.add(new ComponentName(
                        info.activityInfo.packageName, info.activityInfo.name));
            }
        } catch (Exception e) {}

        if (DEBUG) {
            for (int i = 0, count = mTTSServiceList.size(); i < count; ++i) {
                Slog.d(TAG, "Installed TTSService : " + mTTSServiceList.get(i));
            }
        }

    }

    private void loadPrintServices() {

        try {
            PrintManager printManager = (PrintManager) mContext.getSystemService(
                    Context.PRINT_SERVICE);

            List<PrintServiceInfo> services =
                    printManager.getPrintServices(PrintManager.ALL_SERVICES);

            if (services != null) {
                mPrintServiceList.clear();
                final int serviceCount = services.size();
                for (int i = 0; i < serviceCount; i++) {
                    PrintServiceInfo service = services.get(i);

                    mPrintServiceList.add(new ComponentName(
                            service.getResolveInfo().serviceInfo.packageName,
                            service.getResolveInfo().serviceInfo.name));
                }
            }
        } catch (Exception e){}

        if (DEBUG) {
            for (int i = 0, count = mPrintServiceList.size(); i < count; ++i) {
                Slog.d(TAG, "Installed PrintService : " + mPrintServiceList.get(i));
            }
        }
    }


    /**
     * @get the set of enabled accessibility services.
     */
    private void getEnabledServiceList() {
        try {
            final String enabledServicesSetting = Settings.Secure.getStringForUser(
                    mContext.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    UserHandle.myUserId());

            Slog.d(TAG, "enabledServicesSetting:" + enabledServicesSetting);
            if (enabledServicesSetting == null) {
                return;
            }
            mEnabledAccessibilityServiceList.clear();

            final TextUtils.SimpleStringSplitter colonSplitter = sStringColonSplitter;
            colonSplitter.setString(enabledServicesSetting);

            while (colonSplitter.hasNext()) {
                final String componentNameString = colonSplitter.next();
                final ComponentName enabledService = ComponentName.unflattenFromString(
                        componentNameString);
                if (enabledService != null) {
                    mEnabledAccessibilityServiceList.add(enabledService);
                }
            }
        } catch (Exception e){}

        if (DEBUG) {
            for (int i = 0, count = mEnabledAccessibilityServiceList.size(); i < count; ++i) {
                Slog.d(TAG, "Enabled AccessibilityService : " + mEnabledAccessibilityServiceList.get(i));
            }
        }
    }

    // add for bug#966540
    private boolean loadTileServiceList () {

        boolean changed = false;
        try {
            PackageManager pm = mContext.getPackageManager();
            List<ResolveInfo> services = pm.queryIntentServicesAsUser(
                    new Intent(TileService.ACTION_QS_TILE), 0, ActivityManager.getCurrentUser());

            for (ResolveInfo info : services) {
                String packageName = info.serviceInfo.packageName;
                ComponentName componentName = new ComponentName(packageName, info.serviceInfo.name);

                if (!mInstalledTileServiceList.contains(componentName)) {
                    mInstalledTileServiceList.add(componentName);
                    changed = true;
                }
            }

        } catch (Exception e) {}

        if (DEBUG) {
            for (int i = 0, count = mInstalledTileServiceList.size(); i < count; ++i) {
                Slog.d(TAG, "Installed Tile Service: " + mInstalledTileServiceList.get(i));
            }
        }

        return changed;
    }


    class SettingsObserver extends ContentObserver {
        private final ContentResolver mResolver;
        final Uri defaultSMSUri = Settings.Secure.getUriFor(
                Settings.Secure.SMS_DEFAULT_APPLICATION);
        final Uri defaultPhoneUri = Settings.Secure.getUriFor(
                Settings.Secure.DIALER_DEFAULT_APPLICATION);

        SettingsObserver(Handler handler, ContentResolver resolver) {
            super(handler);
            mResolver = resolver;
            // mResolver.registerContentObserver(Settings.Secure.getUriFor(
            //        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, this);
            if (defaultSMSUri != null)
                mResolver.registerContentObserver(defaultSMSUri, false, this, UserHandle.USER_ALL);
            if (defaultPhoneUri != null)
                mResolver.registerContentObserver(defaultPhoneUri, false, this, UserHandle.USER_ALL);
        }

        @Override public void onChange(boolean selfChange, Uri uri) {
            // getEnabledServiceList();
            try {
                if (defaultSMSUri !=null && defaultSMSUri.equals(uri)) {
                    mDefaultSmsComp = SmsApplication.getDefaultSmsApplication(mContext, true);
                    if (DEBUG) Slog.d(TAG, "mDefaultSmsComp:" + mDefaultSmsComp + " mDefaultPhoneApp:" + mDefaultPhoneApp);
                } else if (defaultPhoneUri != null && defaultPhoneUri.equals(uri)) {
                    mDefaultPhoneApp = DefaultDialerManager.getDefaultDialerApplication(mContext);
                    if (DEBUG) Slog.d(TAG, "mDefaultSmsComp:" + mDefaultSmsComp + " mDefaultPhoneApp:" + mDefaultPhoneApp);
                }
            } catch (Exception e) {}

        }
    }

    // for bug#841035
    private void loadUsageStats() {
        if (!mLoadUsageStats) {
            if (DEBUG) Slog.d(TAG, "loadUsageStats : " + mLoadUsageStats);
            return;
        }
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -30);
        final UsageStatsManager usageStatsManager =
            (UsageStatsManager) mContext.getSystemService(Context.USAGE_STATS_SERVICE);

        final List<UsageStats> stats =
                usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                        cal.getTimeInMillis(), System.currentTimeMillis());
        if (stats == null) {
            return;
        }

        final int statCount = stats.size();
        if (DEBUG) Slog.d(TAG, "loadUsageStats count: " + statCount);
        for (int i = 0; i < statCount; i++) {
            final UsageStats pkgStats = stats.get(i);

            //if (DEBUG) Slog.d(TAG, "add UsageStats for " + pkgStats.getPackageName() + " mlaunchcount:" + pkgStats.mLaunchCount
            //    + " mlasttime:" + pkgStats.getLastTimeUsed());

            UsageStats existingStats =
                    mUsageStatsMap.get(pkgStats.getPackageName());
            if (existingStats == null) {
                try {
                    ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(pkgStats.getPackageName(), 0);
                    if (appInfo != null)
                        mUsageStatsMap.put(pkgStats.getPackageName(), pkgStats);
                } catch (Exception e) {}
            } else {
                existingStats.add(pkgStats);
            }
        }
    }


    private static final String CONFIG_FILENAME = "SystemPreferredConfig.xml";

    private static final String XML_CONFIG_TILE_SERVICE_TAG = "TileServiceList";
    private static final String XML_CONFIG_COMPONET_NAME_TAG = "compname";
    private static final String XML_CONFIG_FILE_TAG  = "SystemPreferredConfig";

    // add for bug#1000896
    private boolean loadPreferredConfigs() {
        AtomicFile aFile = new AtomicFile(new File(new File(Environment.getDataDirectory(),
                "system"), CONFIG_FILENAME));

        InputStream stream = null;

        try {
            stream = aFile.openRead();
        } catch (FileNotFoundException exp) {
            Slog.e(TAG, ">>>file not found," + exp);
        }

        if (null == stream) {
            return false;
        }

        if (DEBUG) Slog.d(TAG, "loadPreferredConfigs() ");

        try {
            String appName = null;
            AppPowerSaveConfig appConfig = null;
            XmlPullParser pullParser = Xml.newPullParser();
            pullParser.setInput(stream, "UTF-8");
            int event = pullParser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                switch (event) {
                    case XmlPullParser.START_DOCUMENT:
                        //retList = new ArrayList<PowerGuruAlarmInfo>();
                        break;

                    case XmlPullParser.START_TAG:
                        if (XML_CONFIG_TILE_SERVICE_TAG.equals(pullParser.getName())) {
                            mInstalledTileServiceList.clear();
                        } else if (XML_CONFIG_COMPONET_NAME_TAG.equals(pullParser.getName())) {
                            String compName = pullParser.nextText();
                            ComponentName componentName = ComponentName.unflattenFromString(compName);
                            if (!mInstalledTileServiceList.contains(componentName)) {
                                mInstalledTileServiceList.add(componentName);
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        break;
                }
                event = pullParser.next();
            }
        } catch (IllegalStateException e) {
            Slog.e(TAG, "Failed parsing " + e);
            return false;
        } catch (NullPointerException e) {
            Slog.e(TAG, "Failed parsing " + e);
            return false;
        } catch (NumberFormatException e) {
            Slog.e(TAG, "Failed parsing " + e);
            return false;
        } catch (XmlPullParserException e) {
            Slog.e(TAG, "Failed parsing " + e);
            return false;
        } catch (IOException e) {
            Slog.e(TAG, "Failed parsing " + e);
            return false;
        } catch (IndexOutOfBoundsException e) {
            Slog.e(TAG, "Failed parsing " + e);
            return false;
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                Slog.e(TAG, "Fail to close stream " + e);
                return false;
            } catch (Exception e) {
                Slog.e(TAG, "exception at last,e: " + e);
                return false;
            }
        }

        if (DEBUG) {
            for (int i = 0, count = mInstalledTileServiceList.size(); i < count; ++i) {
                Slog.d(TAG, " Load Installed Tile Service: " + mInstalledTileServiceList.get(i));
            }
        }

        return true;
    }


    private static void writeItem(XmlSerializer serializer, String tag, String value) throws IOException {
        serializer.startTag(null, tag);
        if (value == null ) {
            serializer.text("null");
        } else {
            serializer.text(value);
        }
        serializer.endTag(null, tag);
    }

    private boolean writePreferredConfigs() {
        AtomicFile aFile = new AtomicFile(new File(new File(Environment.getDataDirectory(), "system"), CONFIG_FILENAME));
        FileOutputStream stream;
        try {
            stream = aFile.startWrite();
        } catch (IOException e) {
            Slog.e(TAG, "Failed to write state: " + e);
            return false;
        }

        if (DEBUG) Slog.d(TAG, "writePreferredConfigs() ");

        try {
            XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(stream, "utf-8");
            serializer.startDocument(null, true);
            serializer.startTag(null, XML_CONFIG_FILE_TAG);

            if (mInstalledTileServiceList.size() > 0) {
                serializer.startTag(null, XML_CONFIG_TILE_SERVICE_TAG);
                for (int i=0; i< mInstalledTileServiceList.size(); i++) {
                    ComponentName componentName = mInstalledTileServiceList.get(i);
                    if (componentName != null) {
                        String compName = componentName.flattenToString();
                        writeItem(serializer, XML_CONFIG_COMPONET_NAME_TAG, compName);
                    }
                }
                serializer.endTag(null, XML_CONFIG_TILE_SERVICE_TAG);
            }

            serializer.endTag(null, XML_CONFIG_FILE_TAG);
            serializer.endDocument();
            aFile.finishWrite(stream);
        } catch (IOException e) {
            Slog.e(TAG, "Failed to write state, restoring backup."+"exp:"+"\n"+e);
            aFile.failWrite(stream);
            return false;
        }
        return true;
    }

    private boolean isDebug(){
        return Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug");
    }
}
