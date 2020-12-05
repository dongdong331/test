package com.android.server;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import com.android.server.power.sprdpower.AbsDeviceIdleController;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Log;
import android.util.Xml;

import com.android.ims.internal.IImsDozeManager;
import com.android.ims.internal.IImsDozeObserver;
import com.android.ims.internal.ImsManagerEx;
import com.android.internal.os.AtomicFile;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DeviceIdleControllerEx extends  AbsDeviceIdleController {

    private static final String TAG = "DeviceIdleControllerEx";
    private Context mContext;
    private AtomicFile mSourceConfigFile;
    private List<String> mPresetConfigWhiteList = new ArrayList<String>();
    private boolean mPresetWhiteListLoaded = false;

    private final ArrayList<ImsNetworkObserver> mImsNetworkListeners
            = new ArrayList<ImsNetworkObserver>();
    private final Object mLock = new Object();
    private boolean mListenImsNetwork = false;
    private IImsDozeManager mImsDozeMgr;

    private static DeviceIdleControllerEx sInstance;

    private DeviceIdleControllerEx(Context context){
        mContext = context;
        mSourceConfigFile = new AtomicFile(new File("/system/etc/deviceidle.xml"));
    }

    public static DeviceIdleControllerEx getInstance(Context context){
        synchronized(DeviceIdleControllerEx.class){
            if (sInstance == null ){
                sInstance = new DeviceIdleControllerEx(context);
            }
        }

        return sInstance;
    }

    @Override
    public void readPresetConfigListFromFile(){
        Log.d(TAG, "Reading String list config from " + mSourceConfigFile.getBaseFile()
            + " mPresetWhiteListLoaded:" + mPresetWhiteListLoaded);

        if (mPresetWhiteListLoaded) return;

        mPresetConfigWhiteList.clear();
        FileInputStream presetStream;
        try {
            presetStream = mSourceConfigFile.openRead();
        } catch (FileNotFoundException e) {
            return;
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(presetStream, StandardCharsets.UTF_8.name());
            readStringListFileLocked(parser);
        } catch (XmlPullParserException e) {
        } finally {
            try {
                presetStream.close();
            } catch (IOException e) {
            }
        }

        mPresetWhiteListLoaded = true;

    }

    /**
     * perform a read operation
     */
    private void readStringListFileLocked(XmlPullParser parser) {
        final PackageManager pm = mContext.getPackageManager();

        try {
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                ;
            }

            if (type != XmlPullParser.START_TAG) {
                throw new IllegalStateException("no start tag found when reading String list config");
            }

            int outerDepth = parser.getDepth();
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }

                String tagName = parser.getName();
                if (tagName.equals("wl")) {
                    String name = parser.getAttributeValue(null, "n");
                    if (name != null) {
                        Log.d(TAG, "add "+name+" to mPresetConfigWhiteList !!!");
                        mPresetConfigWhiteList.add(name);
                    }
                } else {
                    Log.w(TAG, "Unknown element under <config>: "
                            + parser.getName());
                    XmlUtils.skipCurrentTag(parser);
                }
            }

        } catch (IllegalStateException e) {
            Log.w(TAG, "Failed parsing String list config " + e);
        } catch (NullPointerException e) {
            Log.w(TAG, "Failed parsing String list config " + e);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed parsing String list config " + e);
        } catch (XmlPullParserException e) {
            Log.w(TAG, "Failed parsing String list config " + e);
        } catch (IOException e) {
            Log.w(TAG, "Failed parsing String list config " + e);
        } catch (IndexOutOfBoundsException e) {
            Log.w(TAG, "Failed parsing String list config " + e);
        }
    }

    @Override
    public boolean isInPresetWhiteAppList(int uid) {
        //Log.d(TAG, "judge "+uid+" isInPresetWhiteAppList");
        final String[] packages = mContext.getPackageManager().getPackagesForUid(uid);
        final int userId = UserHandle.getUserId(uid);

        if (!ArrayUtils.isEmpty(packages)) {
            for (String packageName : packages) {
                if (mPresetConfigWhiteList.contains(packageName)){
                   Log.d(TAG, "packageName: "+packageName+" uid: "+uid+" is in preset white app list");
                   return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isInPresetWhiteAppList(String packageName) {
        if (mPresetConfigWhiteList.contains(packageName)){
           Log.d(TAG, "packageName: "+packageName+" is in preset white app list");
           return true;
        }
        return false;
    }

    /**
     * register a observer for IMS Network state
     * @param observer link {@ImsNetworkObserver}
     */
    @Override
    public void registerImsNetworkObserver(ImsNetworkObserver observer) {
        synchronized (mLock) {
            mImsNetworkListeners.add(observer);
        }
    }

    /**
     * unregister a observer for IMS Network state
     * @param observer link {@ImsNetworkObserver}
     */
    @Override
    public void unregisterImsNetworkObserver(ImsNetworkObserver observer) {
        synchronized (mLock) {
            mImsNetworkListeners.remove(observer);
        }
    }

    /**
     * Enable Ims network observer functions. When disabled, the state of IMS Network will
     * not notify to Observers
     * @param enabled
     */
    @Override
    public void setImsNetworkObserverEnabled(boolean enabled) {
         Log.d(TAG, "setImsNetworkObserverEnabled: enabled = "
                  + enabled + ", mListenImsNetwork = " + mListenImsNetwork);

        if (mListenImsNetwork == enabled) return;

        mListenImsNetwork = enabled;
        try {
            if (mListenImsNetwork) {
                getImsDozeManager();
                if (mImsDozeMgr != null) {
                    mImsDozeMgr.registerImsDozeObserver(mImsDozeObserver);
                }
            } else {
                getImsDozeManager();
                if (mImsDozeMgr != null) {
                    mImsDozeMgr.unregisterImsDozeObserver(mImsDozeObserver);
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error registering observer :" + e);
        }
    }

    /**
     *  Observer to monitor the doze state requested from Ims network
     */
    private IImsDozeObserver mImsDozeObserver = new BaseImsDozeObserver() {

        /** notify if Doze mode can be enabled
         *  if switchedOn is true, Doze mode can be enabled;
         *  if switchedOn is false, Doze mode should be disabled.
         */
        @Override
        public void onDozeModeOnOff(boolean switchedOn) {
            Log.d(TAG, "IImsDozeObserver::onDozeModeOnOff : " + switchedOn);

            ArrayList<ImsNetworkObserver> listeners;
            synchronized (mLock) {
                listeners = new ArrayList<ImsNetworkObserver>(
                        mImsNetworkListeners);
            }
            for (int i=0; i<listeners.size(); i++) {
                listeners.get(i).onVoWifiCalling(!switchedOn);
            }
        }
    };

    private IImsDozeManager getImsDozeManager() {
        synchronized (this) {
            if (mImsDozeMgr != null) {
                return mImsDozeMgr;
            }

            IBinder b = ServiceManager.getService(ImsManagerEx.IMS_DOZE_MANAGER);
            mImsDozeMgr = IImsDozeManager.Stub.asInterface(b);
            if (mImsDozeMgr == null) {
                Log.w(TAG, "getImsDozeManager: mImsDozeMgr is null");
            }
            return mImsDozeMgr;
        }
    }

    private class BaseImsDozeObserver extends IImsDozeObserver.Stub {
        @Override
        public void onDozeModeOnOff(boolean switchedOn) {
            // default no-op
        }
    }
}
