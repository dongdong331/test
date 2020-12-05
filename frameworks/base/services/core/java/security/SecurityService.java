/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.security;

import android.os.Binder;
import android.os.FileUtils;
import android.os.Handler;
import android.os.ServiceManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.UriMatcher;
import android.database.ContentObserver;
import android.database.Cursor;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.DropBoxManager;
import android.os.RemoteException;
import android.os.ISecurityService;
import android.telecom.TelecomManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.util.EventLog;
import android.util.Log;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.lang.InterruptedException;
import com.android.server.wm.WindowManagerService;
import android.widget.Toast;
import java.util.Arrays;
import android.os.HandlerThread;
import android.os.Handler;
import android.os.Message;
import android.widget.Button;
import android.os.SystemProperties;
import java.util.Date;
import java.text.SimpleDateFormat;
import android.net.wifi.WifiManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

public class SecurityService extends ISecurityService.Stub  {
    private static final String TAG = "SecurityService";
    private static final String ID = "_id";
    private static final String SIGN = "sign";
    private static final String UID = "uid";
    private static final String SERVICENAME = "service_name";
    private static final String PACKAGENAME = "package_name";
    private static final String PERMISSIONID = "permission_id";
    private static final String PERMISSION = "permission";
    private static final String ASSIST = "assist";
    private static final String COUNT = "count(*) count ";

    private static final int JDG_POLICY_ALLOW  = 1;
    private static final int JDG_USER_ALLOW  = 2;
    private static final int JDG_TIME_ALLOW  = 3;
    private static final int JDG_POLICY_REFUSE  = -10;
    private static final int JDG_USER_REFUSE  = -9;
    private static final int JDG_UNKNOWN  = 0;

    private static final int PERMISSION_ALLOW  = 0;
    private static final int PERMISSION_REFUSE  = 1;
    private static final int PERMISSION_ALERT  = 2;

    private static final int OPR_NORMAL  = 0;
    private static final int OPR_PROVIDER  = 1;
    private static final int OPR_STARTACTIVITY  = 2;
    private  static final Object permissionlock = new Object();
    private  static final Object seqlock =  new Object();
    private  static final Object sysUidlock =  new Object();
    private boolean vdebug = false;
    private int sysuid[] = null;
    private Context resourceContext;
    private int[][] mPolicy2ResID = null;

    private class Opr2Policy {
        int mOprID;
        int mOprType;
        int mPolicyID;

        public Opr2Policy(int oprID, int oprType, int policyID) {
            mOprID = oprID;
            mOprType = oprType;
            mPolicyID = policyID;
        }
    }

    private class PermissionKey {
        int mUid;
        int mPolicyID;

        public PermissionKey(int uid, int policyID) {
            mUid = uid;
            mPolicyID = policyID;
        }

        public int hashCode() {
            int ret = new Integer(mUid).hashCode() ^ new Integer(mPolicyID).hashCode();
            return ret;
        }

        public boolean equals(Object obj) {
            if (null == obj) {
                return false;
            }
            if (!(obj instanceof PermissionKey)) {
                return false;
            }
            PermissionKey tmpObj = (PermissionKey) obj;
            return tmpObj.mUid == mUid && tmpObj.mPolicyID == mPolicyID;
        }
    }

    private class uidKey {
        int mUid;
        public uidKey(int uid) {
            mUid = uid;
        }

        public int hashCode() {
            int ret = new Integer(mUid).hashCode();
            return ret;
        }

        public boolean equals(Object obj) {
            if (null == obj) {
                return false;
            }
            if (!(obj instanceof uidKey)) {
                return false;
            }
            uidKey tmpObj = (uidKey) obj;
            return tmpObj.mUid == mUid;
        }
    }

    private class KeywordsKey {
        int mOprType;
        String mKeywords;

        public KeywordsKey(int oprType, String keywords) {
            mOprType = oprType;
            mKeywords = keywords;
        }

        public int hashCode() {
            int ret = new Integer(mOprType).hashCode() ^ mKeywords.hashCode();
            return ret;
        }

        public boolean equals(Object obj) {
            if (null == obj) {
                return false;
            }
            if (!(obj instanceof KeywordsKey)) {
                return false;
            }
            KeywordsKey tmpObj = (KeywordsKey) obj;
            return (tmpObj.mOprType == mOprType) && tmpObj.mKeywords.equals(mKeywords);
        }
    }

    private static final int[]  CacheOPrID = {202,203} ;

    private static final String SECURITY_DB = "/system/etc/telephonesec.db";
    private static final String POLICYID_TABLE = "opr2policy";
    private static final String KEYWORDS_TABLE = "opr2keywords";

    private  Map<String, List<Opr2Policy> > gPolicyIDCache = new HashMap<String, List<Opr2Policy> >();
    private Map<PermissionKey, Integer> gPermissionCache = new HashMap<PermissionKey, Integer>();
    private Map<KeywordsKey, Integer > gKeywordsCache = new HashMap<KeywordsKey, Integer >();
    private Map<uidKey,String>  gApplicationCache = new HashMap<uidKey,String>() ;
    private Map<PermissionKey, Integer> gOPrID2ResIDCache = new HashMap<PermissionKey, Integer>();

    // items for policy cache
    private static final String SERVICE_NAME = "service_name";
    private static final String OPR_ID = "operation_id";
    private static final String OPR_TYPE = "operation_type";
    private static final String POLICY_ID = "policy_id";
    private static final String KEYWORDS = "keywords";

    private static final String AUTHORITY = "com.spreadst.security.permission.provider";
    private static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/permission");
    private static final Uri CONTENT_URI_UID = Uri.parse("content://" + AUTHORITY + "/permissionUID");
    private static final Uri CONTENT_URI_PERMISSIONID = Uri.parse("content://" + AUTHORITY + "/permissionID");
    private static final Uri CONTENT_URI_PERMISSIONCOUNT = Uri.parse("content://" + AUTHORITY + "/permissionCount");
    private static final Uri CONTENT_URI_APPCOUNT = Uri.parse("content://" + AUTHORITY + "/appCount");

    private static final Uri CONTENT_URI_SELFSTART = Uri.parse("content://" + AUTHORITY + "/self_start");
    private static final Uri CONTENT_URI_SELFSTART_UID = Uri.parse("content://" + AUTHORITY + "/self_startUID");
    private static final Uri CONTENT_URI_CONTROL = Uri.parse("content://" + AUTHORITY + "/control");
    private static final Uri CONTENT_URI_LOG= Uri.parse("content://" + AUTHORITY + "/log");
    private static final String LOG_ID = "_id";
    private static final String LOG_PKGNAME = "pkgName";
    private static final String LOG_PERMID = "permName";
    private static final String LOG_STRATEGY = "permStrategy";
    private static final String LOG_TIME = "time";

    private Context mContext;
    private PackageManager mPackageManager;
    private WindowManagerService mWindowManagerService;
    private Handler mHandler;
    private Object mAlertLock = new Object();
    private volatile boolean  mAlertRet = false;
    private String mAlertMsg = null;
    private volatile int  controlflag = 1;

    private void  initResIDCache ()
    {
        gOPrID2ResIDCache.put(new PermissionKey(203,9) , getStringId(resourceContext,"security_alert_recordcall"));
        gOPrID2ResIDCache.put(new PermissionKey(203,10) ,getStringId(resourceContext,"security_alert_record"));
        gOPrID2ResIDCache.put(new PermissionKey(202,9) , getStringId(resourceContext,"security_alert_recordcall"));
        gOPrID2ResIDCache.put(new PermissionKey(202,10) ,getStringId(resourceContext,"security_alert_record"));
    }

    public SecurityService() {
        log("Constructor start");
        initKeywordsCache();
        initPolicyIDCache();
        log("Constructor end");
    }

    private class PermissionObserver extends ContentObserver {
        PermissionObserver(Handler handler) {
            super(handler);
        }

        @Override public void onChange(boolean selfChange) {
            log("PermissionObserver onChange start");
            initPermissionCache();
            getSysUids();
            log("PermissionObserver onChange end");
        }
    }
    private class ControlObserver extends ContentObserver {
        ControlObserver(Handler handler) {
            super(handler);
        }

        @Override public void onChange(boolean selfChange) {
            log("ControlObserver onChange start initControlFlag");
            initControlFlag();
            log("ControlObserver onChange end initControlFlag");
        }
    }
    /**
    *readdbto cache
    *readprovider to cache
    *register contentProvider
    *read systemuid
    */
    public void systemReady(Context context) {

        mContext = context;

        IntentFilter filter = new IntentFilter(Intent.ACTION_BOOT_COMPLETED);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
                    log("systemReady start");
                    initPermissionCache();
                    initControlFlag();
                    mHandler = new Handler();
                    mPackageManager = mContext.getPackageManager();
                    mWindowManagerService = (WindowManagerService) ServiceManager.getService(
                                                Context.WINDOW_SERVICE);
                    mContext.getContentResolver().registerContentObserver(CONTENT_URI, false, new PermissionObserver(mHandler));
                    mContext.getContentResolver().registerContentObserver(CONTENT_URI_CONTROL, false, new ControlObserver(mHandler));
                    try {
                        resourceContext = mContext.createPackageContext("com.android.resource.security",Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
                        log("success to get resourceContext");
                    } catch (NameNotFoundException e) {
                        e.printStackTrace();
                        log("fail to get resourceContext");
                    }
                    initResIDCache();
                    mPolicy2ResID = initPolicy2ResID();
                    log("systemReady end");
                    
                }
            }
        }, filter);
    }

    public boolean isSystemUId(int uid) {
        for(int i = 0; i<sysuid.length; i++) {
            synchronized(sysUidlock){
            if(uid == sysuid[i]) {
                log("isSystemUId true");
                return true;
            }
            }
        }
        log("isSystemUId false");
        return false;
    }
    private void initPolicyIDCache() {
        Cursor cr = null;
        SQLiteDatabase db;
        db = SQLiteDatabase.openDatabase(SECURITY_DB, null, SQLiteDatabase.OPEN_READONLY);
        if (db == null) {
            log("initPolicyIDCache(), open db fail");
            return;
        }
        cr = db.query(POLICYID_TABLE, new String[] {SERVICE_NAME}, null, null, null, null, null);
        if((cr == null) || !cr.moveToFirst()) {
            log("initPolicyIDCache(), cur is null");
            return;
        }
        log("initPolicyIDCache(), name count: " + cr.getCount());
        gPolicyIDCache.clear();
        do {
            String name = cr.getString(0);
            log("initPolicyIDCache(), name: " + name);
            List<Opr2Policy> list = new ArrayList<Opr2Policy>();
            Cursor cur = db.query(POLICYID_TABLE, new String[] {OPR_ID, OPR_TYPE, POLICY_ID}, SERVICE_NAME + "=?", new String[] {name}, null, null, null);
            if ((cur != null) && cur.moveToFirst()) {
                log("initPolicyIDCache(), policy count: " + cur.getCount());
                do {
                    log("initPolicyIDCache(), opr_id: " + cur.getInt(0) + ", opr_type: " + cur.getInt(1) + ", policy_id: " + cur.getInt(2));
                    list.add(new Opr2Policy(cur.getInt(0), cur.getInt(1), cur.getInt(2)));
                } while(cur.moveToNext());
                gPolicyIDCache.put(name, list);
                cur.close();
            }
        } while (cr.moveToNext());
        cr.close();
        db.close();
    }
    private void initControlFlag() {
        Cursor cur = null;
        long identityToken = clearCallingIdentity();
        try {
            cur = mContext.getContentResolver().query(CONTENT_URI_CONTROL, new String[] {SERVICENAME,PERMISSION}, SERVICENAME + "=? ", new String[] {String.valueOf("security")}, null);
            if ((cur == null) || !cur.moveToFirst()) {
                log("initControlFlag(), cur is null");
                return;
            }
            controlflag = cur.getInt(1) ;
            SystemProperties.set("service.project.sec",String.valueOf(controlflag)) ;
            log("initControlFlag(),controlflag : " + controlflag);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (cur != null) {
                cur.close();
            }
            restoreCallingIdentity(identityToken);
        }
    }
    private void initPermissionCache() {
        Cursor cur = null;
        long identityToken = clearCallingIdentity();
        try {
            cur = mContext.getContentResolver().query(CONTENT_URI, new String[] {UID, PERMISSIONID, PERMISSION}, null, null, null);
            if ((cur == null) || !cur.moveToFirst()) {
                log("initPermissionCache(), cur is null");
                return;
            }
            log("initPermissionCache(), permission count: " + cur.getCount());
            synchronized(permissionlock)
            {
                gPermissionCache.clear();
                do {
                    log("initPermissionCache(), uid: " + cur.getInt(0) + ", policyID: " + cur.getInt(1) + ", permission: " + cur.getInt(2));
                    PermissionKey key = new PermissionKey(cur.getInt(0), cur.getInt(1));
                    gPermissionCache.put(key, new Integer(cur.getInt(2)));
                } while (cur.moveToNext());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (cur != null) {
                cur.close();
            }
            restoreCallingIdentity(identityToken);
        }
    }

    private void initKeywordsCache() {
        Cursor cr = null;
        SQLiteDatabase db;
        db = SQLiteDatabase.openDatabase(SECURITY_DB, null, SQLiteDatabase.OPEN_READONLY);
        if (db == null) {
            log("initKeywordsCache(), open db fail");
            return;
        }
        cr = db.query(KEYWORDS_TABLE, new String[] {KEYWORDS, POLICY_ID}, OPR_TYPE + "=?", new String[] {String.valueOf(OPR_STARTACTIVITY)}, null, null, null);
        if((cr == null) || !cr.moveToFirst()) {
            log("initKeywordsCache(), cr is null");
            return;
        }
        do {
            log("initKeywordsCache(), policyID: " + cr.getInt(1) + ", keywords: " + cr.getString(0));
            KeywordsKey key = new KeywordsKey(OPR_STARTACTIVITY, cr.getString(0));
            gKeywordsCache.put(key, new Integer(cr.getInt(1)));
        } while (cr.moveToNext());
        cr.close();
        db.close();
    }

    private int getPermission(int uid,int policyId ) {
        log("getPermission uid:" + uid + ", policyId: " + policyId);
        int pem = PERMISSION_REFUSE;
        Cursor cur = null;
        long identityToken = clearCallingIdentity();
        try {
            cur = mContext.getContentResolver().query(CONTENT_URI, new String[] {PERMISSION}, UID + "=? AND " +  PERMISSIONID + "=?", new String[] {String.valueOf(uid), String.valueOf(policyId)}, null);
            if ((cur == null) || !cur.moveToFirst()) {
                pem = cur.getInt(0);
                log("getPermission find pem: " + pem);
            } else {
                log("getPermission cur is null");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (cur != null) {
                cur.close();
            }
            restoreCallingIdentity(identityToken);
        }
        log("getPermission perm: " + pem);
        return pem;
    }
    private int updatePermission(int uid,int policyId,int perm ) {
        log("updatePermission uid:" + uid + ", policyId: " + policyId + ",perm:" + perm);
        int  rows  = 0;
        ContentValues permValue = null ;
        long identityToken = clearCallingIdentity();
        try {
            permValue = new ContentValues();
            permValue.put(PERMISSION,perm) ;
            rows  = mContext.getContentResolver().update(CONTENT_URI, permValue, UID + "=? AND " +  PERMISSIONID + "=?", new String[] {String.valueOf(uid), String.valueOf(policyId)});
            if (rows == 0 ) {
                log("updatePermission rows: " + rows);
            } else {
                log("updatePermission rows: " + rows);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            restoreCallingIdentity(identityToken);
        }
        return rows;
    }
    private int updateOperationLog(String  packagename,int policyId,int perm ) {
        log("updateOperationLog:" + packagename + ", policyId: " + policyId + ",perm:" + perm);
        Uri  rows ;
        ContentValues logValue = null ;
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss") ;
        long identityToken = clearCallingIdentity();
        try {
            logValue = new ContentValues();
            logValue.put(LOG_PKGNAME,packagename) ;
            logValue.put(LOG_PERMID,String.valueOf(policyId)) ;
            logValue.put(LOG_STRATEGY,String.valueOf(perm)) ;
            logValue.put(LOG_TIME,df.format(new Date())) ;
            rows  = mContext.getContentResolver().insert(CONTENT_URI_LOG,logValue );
            log("updateOperationLog: " + rows);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            restoreCallingIdentity(identityToken);
        }
        return 0;
    }


    private int getPermissionFromCache(int uid,int policyId ) {
        log("getPermissionFromCache uid:" + uid + ", policyId: " + policyId);
        synchronized(permissionlock) {
            Integer perm = gPermissionCache.get(new PermissionKey(uid, policyId));
            if (perm != null) {
                log("getPermissionFromCache permission:" + perm);
                return perm.intValue();
            } else {
                log("getPermissionFromCache cann't find permission");
                return PERMISSION_REFUSE;
            }
        }
    }

    private int getPolicyIDFromCache(String name, int oprID, int oprType, String keyword) {
        log("getPolicyIDFromCache name:" + name + ", oprID: " + oprID + ", oprType: " + oprType + ", keyword: " + keyword);
        List<Opr2Policy> list = gPolicyIDCache.get(name);
        if((list != null) && (!list.isEmpty())) {
            int size = list.size();
            for (int i = 0; i < size; i++) {
                Opr2Policy o2p = list.get(i);
                log("getPolicyIDFromCache(), i: " + i + ", mOprID: " + o2p.mOprID + ", mOprType: " + o2p.mOprType);
                if (o2p.mOprID == oprID) {
                    if ((o2p.mOprType == OPR_NORMAL) || (o2p.mOprType == OPR_PROVIDER)) {
                        log("getPolicyIDFromCache(), find 1, policyID: " + o2p.mPolicyID);
                        return o2p.mPolicyID;
                    } else {
                        Integer id = gKeywordsCache.get(new KeywordsKey(oprType, keyword));
                        if (id != null) {
                            log("getPolicyIDFromCache(), find 2, policyID: " + id);
                            return id.intValue();

                        }
                    }
                }
            }
        }
        log("getPolicyIDFromCache(), find nothing");
        return -1;
    }

    private int isInCall(int oprID){
	TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        final long ident = Binder.clearCallingIdentity();
	if (telecomManager.isInCall()){
		oprID = 9; 
	}
        Binder.restoreCallingIdentity(ident);
	return oprID;
    }
    private String trimServiceName(String name) {
        char endChar = name.charAt(name.length()-1);
        if (('0' <= endChar) && (endChar <= '9')) {
            name = name.substring(0, name.length() -1);
        }
        return name;
    }
    public boolean isKeyService(String name) {
        log("isKeyService(), name: " + name);
        List<Opr2Policy> list = gPolicyIDCache.get(trimServiceName(name));
        if(list != null) {
            if (!list.isEmpty()) {
                log("isKeyService(), true");
                return true;
            }
        }
        log("isKeyService(), false");
        return false;
    }

    public boolean getKeyInterfaces(String name, int[] oprID, int[] oprType) {
        log("getKeyInterfaces(), name: " + name);
        List<Opr2Policy> list = gPolicyIDCache.get(trimServiceName(name));
        if(list != null) {
            if (!list.isEmpty()) {
                int size = list.size();
                if (oprID == null) {
                    oprID = new int[size];
                }
                if (oprType == null) {
                    oprType = new int[size];
                }
                for (int i = 0; i < size; i++) {
                    Opr2Policy o2p = list.get(i);
                    oprID[i] = o2p.mOprID;
                    oprType[i] = o2p.mOprType;
                    log("getKeyInterfaces(), i: " + i + ", oprID: " + o2p.mOprID + ", oprType: " + o2p.mOprType+";name: "+name);
                }
		log("return true keyInterfaces - name ="+name);
                return true;
            }
        }
        log("getKeyInterfaces(), find nothing");
        return false;
    }

    public int[] getSysUids() {
        int uidCounts = 0;
        int tmp[] = null;
        int myCount[] = new int[300];
        
        mPackageManager = mContext.getPackageManager();
        if (mPackageManager == null) {
            log("getSysUids(), pm is null");
            return tmp;
        }
        synchronized(sysUidlock){
        log("add getSysUids sysUidlock");
        List<PackageInfo> packageinfo = mPackageManager.getInstalledPackages(0);
        int count = packageinfo.size();
        gApplicationCache.clear();
        for(int i=0; i<count; i++) {
            PackageInfo pinfo = packageinfo.get(i);
            ApplicationInfo appInfo = pinfo.applicationInfo;
            if((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) > 0)
            {
                myCount[uidCounts] = appInfo.uid;
                uidCounts++;
                log("getSysUids i="+i+" uidCount= "+uidCounts+" name="+ pinfo.packageName);
            }
            else
            {
                uidKey  uido =  new uidKey(appInfo.uid);
                String name = appInfo.loadLabel(mContext.getPackageManager()).toString();
                log("gApplicationCache.put(), name"+name);
                gApplicationCache.put(uido,name);
                log("gApplicationCache.put(), uido"+uido);
            }
        }
        log("getSysUids(), count"+uidCounts);
        tmp = new int[uidCounts];
        for(int j = 0; j<uidCounts; j++) {
            tmp[j] = myCount[j];
            log("getSysUids(),  j: " + j + ", uid: " +tmp[j]);
        }
        if (sysuid == null)
        {
            sysuid = new int[uidCounts];
            for(int j = 0; j<uidCounts; j++) {
                sysuid[j] = myCount[j];
                log("init sysUids(),  j: " + j + ", uid: " +tmp[j]);
            }
        }
        return tmp;
        }
    }

    private int Policy2AlertMsg(int policyID, int oprID) {
        for(int k = 0 ; k < CacheOPrID.length; k++ ){
            if (policyID == CacheOPrID[k]) {
                return gOPrID2ResIDCache.get(new PermissionKey(policyID,oprID));
            }
        }
        for (int i = 0; i<mPolicy2ResID.length; i++) {
            if (policyID == mPolicy2ResID[i][0]) {
                return mPolicy2ResID[i][1];
            }
        }
        return -1;
    }
    public int judge(int uid, String name, int oprID, int oprType, String param) {
        String s = SystemProperties.get("persist.sys.secure.debuglog");
        vdebug = s.equals("!@#$%^&*");
        log("judge() uid: " + uid + ", name:" + name + ", oprID: " + oprID + ", oprType: " + oprType + ", param: " + param);
        int iRet = JDG_POLICY_ALLOW;
        synchronized(seqlock) {
            if (controlflag == 0 || isSystemUId (uid)){
                return JDG_POLICY_ALLOW;
            }
            String packagename[] = mPackageManager.getPackagesForUid(uid);
            int policyID = getPolicyIDFromCache(trimServiceName(name), oprID, oprType, param);
            if (policyID == 202) {
		oprID = isInCall(oprID);
	    }
            log("judge(), policyID: " + policyID + "; oprID == "+ oprID);
            if (policyID > 0) {
                int perm = getPermissionFromCache(uid, policyID);
                if (perm == PERMISSION_ALERT) {
                    String msg;
                    int resID = Policy2AlertMsg(policyID,oprID);
                    if (resID > 0) {
                        msg = resourceContext.getResources().getText(resID).toString();
                        if(policyID==106) {
                            WifiManager mWifiManager = (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
                            if(mWifiManager.isWifiEnabled()){
                                msg = resourceContext.getResources().getText(getStringId(resourceContext,"security_alert_closewifi")).toString();
                            }
                        }
                        if(policyID==105) {
                            if(param.equals("0")) {
                                msg = resourceContext.getResources().getText(getStringId(resourceContext,"security_alert_closedataconnect")).toString();
                            }
                        }
                        log("judge() policyID:" + policyID +",msg:" + msg);
                    } else {
                        msg = "" + policyID;
                    }
                    uidKey uidkeyo = new uidKey(uid);
                    String appname = gApplicationCache.get(uidkeyo);
                    if (appname == null){
                        try {
                            int index = packagename.length ;
                            ApplicationInfo appInfo = mPackageManager.getApplicationInfo(packagename[index-1],0);
                            appname = appInfo.loadLabel(mContext.getPackageManager()).toString();
                            uidKey uido = new uidKey(uid);
                            gApplicationCache.put(uido,appname);
                            for(int x=0; x<index; x++) {
                                log("judge() packagename="+packagename[x].toString());
                            }
                        } catch (PackageManager.NameNotFoundException e) {
                            log("PackageManager.NameNotFoundException");
                        }
                    }
                    msg = appname + resourceContext.getResources().getText(getStringId(resourceContext,"security_alert_tryto")) + " " + msg;
                    if (showAlert(msg,uid,policyID)) {
                        iRet = JDG_USER_ALLOW;
                    } else {
                        iRet = JDG_USER_REFUSE;
                    }
                } else if (perm == PERMISSION_ALLOW) {
                    iRet = JDG_POLICY_ALLOW;
                } else if (perm == PERMISSION_REFUSE) {
                    iRet = JDG_POLICY_REFUSE;
                }
                if (iRet > 0) {
                    updateOperationLog(packagename[0],policyID,1) ;
                } else {
                    updateOperationLog(packagename[0],policyID,0) ;
                }
            }
        }
        log("judge(), ret: " + iRet);
        return iRet;
    }

    private String getTimeText(String text, int count) {
        if(text != null && text.length() > 0 && count > 0) {
            int index = text.indexOf("(");
            if(index > 0) {
                text = text.substring(0, index);
                return (text + "("+count+"s)");
            } else {
                return (text + "("+count+"s)");
            }
        }
        return text;
    }
    private class selectDialog {
        private  Handler mHcountdwn = null ;
        private HandlerThread  mTcountdwn = new HandlerThread("countdwn") ;
        private static final int TYPE_NEGATIVE = 1;
        private Button n = null;
        private int mNegativeCount = 15;
        private AlertDialog d= null;
        private volatile boolean  remind = false ;
        private volatile int  suid = 0 ;
        private volatile int  spolicyId = 0 ;
        private final Runnable mUpdatatime = new Runnable() {
            public void run() {
                if(n != null) {
                    if(mNegativeCount > 0) {
                        String text = (String) n.getText();
                        n.setText(getTimeText(text, mNegativeCount));
                    }
                    else
                    {
                        n.performClick();
                    }
                }
            }
        };
        public selectDialog(int uid,int policyId)
        {
            suid = uid;
            spolicyId = policyId ;
            mTcountdwn.start() ;
            mHcountdwn = new Handler(mTcountdwn.getLooper()) {
                public void handleMessage(Message msg) {
                    switch(msg.what) {
                    case TYPE_NEGATIVE:
                        log("mNegativeCount = " + mNegativeCount + n);
                        if(mNegativeCount > 0) {
                            mNegativeCount--;
                            mHandler.post(mUpdatatime);
                            mHcountdwn.sendEmptyMessageDelayed(TYPE_NEGATIVE, 1000);
                        } else {
                            log("mNegativeCount < 0" + n);
                            mHandler.post(mUpdatatime);
                        }
                        break;
                    default:
                        break;
                    }
                }
            };
        }
        private final Runnable mPowerLongPress = new Runnable() {
            CharSequence msg = resourceContext.getResources().getText(getStringId(resourceContext,"security_alert_remind"));
            final CharSequence[] items = { msg };
            boolean  [] state =new boolean[] {false};
            public void run() {
                AlertDialog.Builder b = new AlertDialog.Builder(mContext).setTitle("Security Alert")
                .setTitle(mAlertMsg)
                .setMultiChoiceItems(items,state, new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        if(isChecked)
                            remind = true ;
                        else
                            remind = false ;
                    }
                })
                .setPositiveButton((resourceContext.getResources().getText(getStringId(resourceContext,"security_alert_choice_allow"))), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        log("allow");
                        mAlertRet = true ;
                        if (remind)
                            updatePermission(suid,spolicyId,PERMISSION_ALLOW);
                    }
                })
                .setNegativeButton((resourceContext.getResources().getText(getStringId(resourceContext,"security_alert_choice_refuse")) + "(" +mNegativeCount + "s)" ), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        log("refuse");
                        mAlertRet = false ;
                        if (remind)
                            updatePermission(suid,spolicyId,PERMISSION_REFUSE);
                    }
                });
                d = b.create();
                d.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        mHcountdwn.removeMessages(TYPE_NEGATIVE);
                        mHcountdwn.getLooper().quit();
                        synchronized(mAlertLock) {
                            log("dismiss");
                            mAlertLock.notify();
                        }
                    }
                });
                d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
                d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                d.show();
                n = d.getButton(AlertDialog.BUTTON_NEGATIVE);
                mHcountdwn.sendEmptyMessageDelayed(TYPE_NEGATIVE, 1000);
            }
        };
        private boolean  show(String msg) {
            log("showAlert, msg: " + msg);
            synchronized(mAlertLock) {
                mAlertMsg = msg;
		mAlertRet = false ;
                mHandler.post(mPowerLongPress);
                try {
                    mAlertLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return mAlertRet;
        }

    }

    private boolean  showAlert(String msg,int uid,int policyId) {
        log("showAlert, msg: " + msg);
        return new selectDialog(uid,policyId).show(msg) ;
    }

    private int[][] initPolicy2ResID() {
        log("start initPolicy2resid");
        int[][] mPolicy = {
            {101, getStringId(resourceContext,"security_alert_placecall")},
            {102, getStringId(resourceContext,"security_alert_sendmessage")},
            {103, getStringId(resourceContext,"security_alert_sendmms")},
            {104, getStringId(resourceContext,"security_alert_sendemail")},
            {105, getStringId(resourceContext,"security_alert_enalbedataconnect")},
            {106, getStringId(resourceContext,"security_alert_enablewifi")},
            {201, getStringId(resourceContext,"security_alert_enablelocation")},
            {204, getStringId(resourceContext,"security_alert_opencamera")},
            {205, getStringId(resourceContext,"security_alert_writecontacts")},
            {206, getStringId(resourceContext,"security_alert_writecalllog")},
            {207, getStringId(resourceContext,"security_alert_writemessage")},
            {208, getStringId(resourceContext,"security_alert_writemms")},
            {209, getStringId(resourceContext,"security_alert_readcontacts")},
            {210, getStringId(resourceContext,"security_alert_readcalllog")},
            {211, getStringId(resourceContext,"security_alert_readmessage")},
            {212, getStringId(resourceContext,"security_alert_readmms")},
            {213, getStringId(resourceContext,"security_alert_readprotectedfiles")},
            {301, getStringId(resourceContext,"security_alert_bt")},
            {302, getStringId(resourceContext,"security_alert_nfc")},
        };
        return mPolicy;

    }

    public int getStringId(Context paramContext, String paramString) {
        return paramContext.getResources().getIdentifier(paramString, "string",
                paramContext.getPackageName());
    }

    private void log(String msg) {
        if (vdebug)
            Log.i(TAG, msg);
    }
}
