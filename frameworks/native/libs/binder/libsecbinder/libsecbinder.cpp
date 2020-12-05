#ifndef NO_USE_PROJECT_SEC /*vendor define NO_USE_PROJECT_SEC all the time for vndk rules*/
#ifdef USE_PROJECT_SEC /*system define USE_PROJECT_SEC when open ccsa*/
/*
 * Copyright (C) 2005 The Android Open Source Project
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

#define LOG_TAG "libs"

#include <utils/Errors.h>
#include <binder/Parcel.h>
#include <binder/ProcessState.h>
#include <utils/Vector.h>
#ifdef HAVE_WIN32_PROC
typedef  int  uid_t;
#endif

#include <binder/Binder.h>
#include <binder/BpBinder.h>
#include <binder/IServiceManager.h>
//#include <media/IMediaPlayerService.h>
#include <cutils/sched_policy.h>
#include <utils/Debug.h>
#include <utils/Log.h>
#include <binder/TextOutput.h>
#include <utils/threads.h>
#include <utils/List.h>
#include <cutils/properties.h>
#include <sqlite3.h>
//#include "sqlite3ext.h"
#include <private/binder/binder_module.h>
#include <private/binder/Static.h>
#include <sys/ioctl.h>
#include <signal.h>
#include <errno.h>
#include <stdio.h>
#include <unistd.h>

#ifdef HAVE_PTHREADS
#include <pthread.h>
#include <sched.h>
#include <sys/resource.h>
#endif
#ifdef HAVE_WIN32_THREADS
#include <windows.h>
#endif

static int debugenable = 0;

namespace android {
/*-------------------------- Security Data & Interface  BEGIN---------------------------------*/
typedef enum OprType {
    OPR_NORMAL = 0,
    OPR_PROVIDER,
    OPR_STARTACTIVITY,
    OPR_MAX
} OprType;

class OprDetail  : public RefBase {
public:
    OprDetail(unsigned int oprID, OprType type) : oprID(oprID), oprType(type) {};

    unsigned int oprID;
    OprType oprType;
};

class KeyService  : public RefBase {
public:
    KeyService(const String16& name, const sp<IBinder>& svc) : name(name), svc(svc) {};

    String16 name;
    sp<IBinder> svc;
    List<sp<OprDetail> > oprList;
};

static List<KeyService*> gKeyService;
static List<KeyService*> gUnknownService;
static List<int> gSysUIDs;
static sp<IBinder> gSecurityService = NULL;
static bool gNeedJudge = false;
static bool gHasUnknownService = false;

void initKeyService(const String16& name, const sp<IBinder>& svc);
static __inline void readConnTransCode();
bool doJudge(int uid, const sp<IBinder>& svc, unsigned int oprID, Parcel& data,  Parcel &reply) ;
static __inline bool checkUnknownService();
static __inline void trimPacel(Parcel& data);
static __inline String16 parseUri(Parcel& data);
static String16 parseIntent(Parcel& data);

static String16 readIntentFromParcel(Parcel& data);
static void readUriPart(Parcel& data);
static void readUriPathPart(Parcel& data);
static String16 Uri_createFromParcel(Parcel& data);

class DeathHandler : public android::IBinder::DeathRecipient {
        virtual void binderDied(const wp<IBinder>& /*who*/) {
            gSecurityService = NULL;
        }
};

//the following structure and interfaces are the same ones as security service
typedef enum JdgRet {
    JDG_POLICY_REFUSE = -10,
    JDG_USER_REFUSE,
    JDG_UNKNOWN = 0,
    JDG_POLICY_ALLOW = 1,
    JDG_USER_ALLOW,
    JDG_TIME_ALLOW
} JdgRet;


#define SECURITY_NAME "security"
#define SECURITY_DESCRIPTION "android.os.ISecurityService"

static const int TRANSACTION_isKeyService = 1;
static const int TRANSACTION_getKeyInterfaces = 2;
static const int TRANSACTION_judge = 3;
static const int TRANSACTION_getSysUids = 4;

static __inline sp<IBinder> getSecurityService();
static bool isKeyService(const String16& name, const sp<IBinder>& svc);
static void getKeyInterfaces(const String16& name, List<sp<OprDetail> >& oprList);
static void getSysUids(List<int>& uids);
JdgRet judge(int uid, const String16& name, int oprID, int oprType, const String16& param);

static __inline String16 parseUri(Parcel& data)
{
    data.setDataPosition(0);
    trimPacel(data);

    int type;
    data.readInt32(&type);

    if (type == 0) {
        return String16();
    }

    return data.readString16();
}

/**
 *解析 Uri.Part
 */
static void readUriPart(Parcel& data){
        int representation;
        data.readInt32(&representation);
        if(representation == 0){
            // Representation.BOTH
            data.readString16();
            data.readString16();
        }else if(representation == 1){
            // Representation.ENCODED
            data.readString16();
        }else if(representation == 2){
            // Representation.DECODED
            data.readString16();
        }else{
            // do nothing
        }
}

/**
 *解析 Uri.PathPart
 */
static void readUriPathPart(Parcel& data){
        int representation;
        data.readInt32(&representation);
        if(representation == 0){
            // Representation.BOTH
            data.readString16();
            data.readString16();
        }else if(representation == 1){
            // Representation.ENCODED
            data.readString16();
        }else if(representation == 2){
            // Representation.DECODED
            data.readString16();
        }else{
            // do nothing
        }
}

/**
 *解析 Intent中的 Uri mData,可以在这里根据scheme对intent action为sendto的方式进行判断拦截
 */
static String16 Uri_createFromParcel(Parcel& data){
	int uriType;
	data.readInt32(&uriType);
	String16 scheme;
	if(uriType == 0){
	    // do nothing
	}else if(uriType == 1){// StringUri
        scheme = data.readString16();
	}else if(uriType == 2){// OpaqueUri
        /* scheme */
        scheme = data.readString16();
        /* part ssp */
        readUriPart(data);
        /* part fragment */
        readUriPart(data);
	}else if(uriType == 3){//HierarchicalUri
        /* scheme */
        scheme = data.readString16();
        /* part authority */
        readUriPart(data);
        /* PathPart path */
        readUriPathPart(data);
        /* Part query */
        readUriPart(data);
        /* Part fragment */
        readUriPart(data);
	}
	return scheme;
}

static String16 readIntentFromParcel(Parcel& data){
    data.readInt32();// for androidO
    data.dataPosition();//size_t pos = data.dataPosition();
//    ALOGD_IF(debugenable," WL PARSEintent pos is %d ",pos);
    String16 action = data.readString16();// action
    if(strcmp(String8(action).string(), "android.intent.action.SEND") == 0
        || strcmp(String8(action).string(), "android.intent.action.SENDTO") == 0){
        /* Uri mData */
        String16 scheme = Uri_createFromParcel(data);
        if(scheme && scheme.size()>0){
            if(strstr(String8(scheme).string(),"mailto")){// 邮件发送操作
	            String16 mail = String16(".mailto");
	            action.append(mail);
            }else if (strstr(String8(scheme).string(),"smsto") || strstr(String8(scheme).string(),"sms")){// 短信发送操作
	            String16 sms = String16(".sms");
	            action.append(sms);
            }else if (strstr(String8(scheme).string(),"mmsto") || strstr(String8(scheme).string(),"mms")){// 彩信发送操作
	            String16 mms = String16(".mms");
	            action.append(mms);
            }
        }
        if(strcmp(String8(action).string(), "android.intent.action.SENDTO") == 0){
            ALOGD_IF(debugenable," WL PARSEintent 1 action is %s ",String8(action).string());
            return action;
        }
    }else{
        ALOGD_IF(debugenable," WL PARSEintent 2 action is %s ",String8(action).string());
        return action;
    }
    ALOGD_IF(debugenable," WL PARSEintent 3 action is %s ",String8(action).string());
    return action;
}

static String16 parseIntent(Parcel& data)
{
//test code start
    {
        int size1 = data.dataSize();
        int i = 0;
        ALOGD_IF(debugenable,"CCSA_TEST WL PARSEintent data size = %d",size1);
        while (i <= size1 ) {
        data.setDataPosition(i);
        String16 str = data.readString16();
        ALOGD_IF(debugenable,"CCSA_TEST WL PARSEintent i= %d,str=%s",i,String8(str).string());
        i+=4;
        }
    }
//test code end
    data.setDataPosition(0);
    trimPacel(data);
    data.readStrongBinder();
    String16 data1 = data.readString16();// callingPackage
    ALOGD_IF(debugenable," WL PARSEintent callingPackage is %s ",String8(data1).string());
    String16 data2 = readIntentFromParcel(data);
    ALOGD_IF(debugenable," WL PARSEintent data2 is %s ",String8(data2).string());
    return data2;
}

char *code, *code_phoneid;
static __inline void readConnTransCode() {
    sqlite3 *db;
    const char *dbadrr="/system/etc/telephonesec.db";
    int ret;
    int nRow,nColumn;
    char **dbResult, **dbResultid;
    char *errmsg;
    const char *sql = "select operation_id from interception_list where service_name='connectivity' and method = 'setMobileDataEnabled';";
    const char *sql_phoneid = "select operation_id from interception_list where service_name='connectivity' and method = 'setMobileDataEnabledByPhoneId';";
    
    ret = sqlite3_open(dbadrr, &db);
    if(SQLITE_OK == ret) {
        ALOGD_IF(debugenable,"DB SUCESS");
        ret = sqlite3_get_table( db, sql_phoneid , &dbResultid, &nRow, &nColumn, &errmsg);
        if(SQLITE_OK == ret ){
            code_phoneid = dbResultid[1];
            ALOGD_IF(debugenable,"code_phoneid is %s:%s:%s!%d,%d!%s  /n",code_phoneid,dbResultid[0],dbResultid[1],nRow,nColumn,code);
        } else {
            sqlite3_free_table(dbResultid);
            return;
        }
        ret = sqlite3_get_table( db, sql , &dbResult, &nRow, &nColumn, &errmsg );
        if(SQLITE_OK == ret ) {
            code = dbResult[1];
            ALOGD_IF(debugenable,"code is %s:%s:%s!%d,%d! /n",code,dbResult[0],dbResult[1],nRow,nColumn);
        } else {
            sqlite3_free_table(dbResult);
            return;
        }
        sqlite3_free_table(dbResult);
        sqlite3_free_table(dbResultid);
    } else {
        ALOGD_IF(debugenable,"DB FAIL");
        return;
    }
    sqlite3_close(db);
}

static pthread_mutex_t k_Mutex = PTHREAD_MUTEX_INITIALIZER;

void initKeyService(const String16& name, const sp<IBinder>& svc)
{
    ALOGI_IF(debugenable , "securityservice initKeyService enter name:%s" , String8(name).string());
    if (!isKeyService(name, svc))
    {    
        ALOGI_IF(debugenable , "securityservice initKeyService %s is not key service" , String8(name).string());
        return;
    }

    pthread_mutex_lock(&k_Mutex);
    if (gSysUIDs.size() <= 0) {
       getSysUids(gSysUIDs);
    }
    pthread_mutex_unlock(&k_Mutex);

    bool bFind = false;
    List<KeyService*>::iterator it = gKeyService.begin();
    while (it != gKeyService.end()) {
        if ((*it)->name == name) {
            (*it)->svc = svc;
            ALOGI_IF(debugenable , "securityservice initKeyService %s has already been find reget KeyInterfaces" , String8(name).string());
            getKeyInterfaces(name,  (*it)->oprList);
            bFind = true;
           break;
        }
        it++;
    }

    if(bFind) return;

    KeyService* s = new KeyService(name, svc);
    getKeyInterfaces(name,  s->oprList);
//    ALOGI_IF(debugenable , "securityservice initKeyService push back gKeyService s->svc:%x , name:%s" , s->svc.get()  , String8(name).string());
    ALOGI_IF(debugenable , "securityservice initKeyService push back gKeyService , name:%s" , String8(name).string());
    gKeyService.push_back(s);

    gNeedJudge = true;
}

static __inline void trimPacel(Parcel& data)
{
    data.readInt32();
    data.readString16();

}

static pthread_mutex_t s_Mutex = PTHREAD_MUTEX_INITIALIZER;

static __inline bool checkUnknownService() {
    if (getSecurityService() == NULL) {
        return false;
    }
    ALOGI_IF(debugenable , "checkUnknownService()");
//    bool bFind = false;
    pthread_mutex_lock(&s_Mutex);
    List<KeyService*>::iterator itService = gUnknownService.begin();
    while (itService != gUnknownService.end()) {
        initKeyService((*itService)->name, (*itService)->svc);
        ALOGI_IF(debugenable , "checkUnknownService(), re-init service:%s" , String8((*itService)->name).string());
        itService++;
    }
    gUnknownService.clear();
    pthread_mutex_unlock(&s_Mutex);
    gHasUnknownService = false;
    return true;
}

const char * proviedname [] = {"sms","mms","mms-sms","call_log","contacts;com.android.contacts",NULL} ;
const char * returnerr[] = {"camera.camera","media.recorder","media.audio_flinger",NULL};

static void initFakeReply(const String16& sername, unsigned int oprID, Parcel &reply)
{
    bool bprovied = false ;
    bool breturnerr = false ;
    ALOGI_IF(debugenable , "initFakeReply sername: %s ", String8(sername).string());
    for (int i = 0 ; proviedname[i]  != NULL ; i++)
    {
        if (!strcmp(proviedname[i],String8(sername).string()))
        {
            bprovied = true ;
            ALOGI_IF(debugenable , "initFakeReply proviedname find %s ",proviedname[i]);
            break ;
        }
    }
    if (bprovied && oprID == 1 ) /*for query*/
    {
       reply.writeNoException();
       reply.writeInt32(1);
       reply.writeStrongBinder(NULL);
       reply.writeString8(String8(""));
       reply.writeInt32(0);
       reply.writeInt32(0);
       reply.writeInt32(0);
    }
    else
    {
        for (int i = 0 ; returnerr[i]  != NULL ; i++)
        {
            if (!strcmp(returnerr[i],String8(sername).string()))
            {
               breturnerr = true ;
               ALOGI_IF(debugenable , "initFakeReply returnerr find %s ",returnerr[i]);
               break ;
            }
        }
        if (breturnerr)
        {
            reply.writeInt32(1);
        }
        else
        {
            reply.writeNoException();
            reply.writeInt32(0);
        }
    }
 }

bool doJudge(int uid, const sp<IBinder>& svc, unsigned int oprID, Parcel& data,  Parcel &reply)
{
    char debugs[PROPERTY_VALUE_MAX] = "";  //PROPERTY_VALUE_MAX 92
    property_get("persist.sys.secure.debuglog", debugs, "");
    if(0 == strcmp(debugs, "!@#$%^&*")){
    	debugenable = 1;
    }else{
    	debugenable = 0;
    }
    JdgRet jResult;

    if ( uid < 10000) { return true; }
    char value[PROPERTY_VALUE_MAX] = "";
    property_get("service.project.sec", value, "0");
	
    if(0 != strcmp(value, "1"))
        return true ;
    ALOGI_IF(debugenable , "securityservice gHasUnknownService=%d",gHasUnknownService);
    if (gHasUnknownService && checkUnknownService()) {
        ALOGI_IF(debugenable , "securityservice reCheck gUnknownService");
    } 
    pthread_mutex_lock(&k_Mutex);
    if (gSysUIDs.size() <= 0) {		
        getSysUids(gSysUIDs);
    }
    pthread_mutex_unlock(&k_Mutex);
    List<int>::iterator itUID = gSysUIDs.begin();
    while (itUID != gSysUIDs.end()) {
        if ((*itUID) == uid) {
                ALOGI_IF(debugenable , "securityservice libsecbinder:dojudge uid=%d is SysUIDs. return!",uid);
        	return true;
        }
        itUID++;
    }

    bool bFind = false ;
    List<KeyService*>::iterator itService = gKeyService.begin();
    while (itService != gKeyService.end()) {
        if ((*itService)->svc == svc) {
            bFind = true;
            ALOGI_IF(debugenable , "securityservice doJudge find true service name:%s, oprID: %d", String8((*itService)->name).string(), oprID);
            break;
        }
        itService++;
    }

    if(!bFind)
    {
        ALOGI_IF(debugenable , "securityservice libsecbinder:dojudge, uid=%d is not keyservice, return!", uid);
        return true;
    }
    List<sp<OprDetail> >::iterator it = (*itService)->oprList.begin();
    while (it != (*itService)->oprList.end()) {
        if ((*it)->oprID == oprID) {
            String16 param;
            if ((*it)->oprType == OPR_NORMAL) {
                if(0 == strcmp(String8((*itService)->name).string(), "connectivity")) {//no connectivity service now, will delete?  2016.7.7
		    if(((unsigned int)atoi(code_phoneid)) == oprID ) {
		        data.setDataPosition(0);
			trimPacel(data);
			data.readStrongBinder();
			char s[10];
			data.readInt32();//int phoneid = data.readInt32();
			int enable = data.readInt32();
			sprintf(s,"%d",enable);
			param = String16(s);
//			ALOGD_IF(debugenable,"securityservice phoneid -%d,data2- %d, param %s, %d,  %d.%s",phoneid,enable,String8(param).string(),data.dataSize(), data.dataPosition(),String8((*itService)->name).string());
		    }else {
			data.setDataPosition(0);
			trimPacel(data);
			data.readStrongBinder();
                        char s[10];
                        int enable = data.readInt32();
                        sprintf(s,"%d",enable);
                        param = String16(s);
//			ALOGD_IF(debugenable,"securityservice data1- %d, param %s, %d,  %d.%s",enable,String8(param).string(),data.dataSize(), data.dataPosition(),String8((*itService)->name).string());
		    }
		    if(param == String16("0")) {
			ALOGD_IF(debugenable,"securityservice return true");
			return true; 
		    }
                }
            } else if ((*it)->oprType == OPR_PROVIDER) { //not find anyone, will delete? 2016.7.7
                param = parseUri(data);
            } else if ((*it)->oprType == OPR_STARTACTIVITY) {
                param = parseIntent(data);
                ALOGD_IF(debugenable," WL PARSEintent param is %s ",String8(param).string());
                if(strstr(String8(param).string(),"android.intent.action.SENDTO")){
                   ALOGD_IF(debugenable,"securityservice WL PARSEintent action is SENDTO, we do not intercept!");
                   return true;
                }
                //if(0 == strcmp(String8((*itService)->name).string(), "activity" ) && 0 == strcmp(String8(param).string(), "android.intent.action.SEND" ) ) return true;
            } else {
                ALOGI_IF(debugenable , "securityservice doJudge ok needn't check oprType not match");
                return true;
            }
            data.setDataPosition(0);
            jResult = judge(uid, (*itService)->name, oprID,(int) ((*it)->oprType), param);
            if(jResult > 0) {
                ALOGI_IF(debugenable , "securityservice Judge ok , service:%s , judge oprInfo oprID:%d , it->oprType:%d , param:%s" , 
                    String8((*itService)->name).string(), oprID , (*it)->oprType , String8(param).string());
                return true;
            } else {
                ALOGI_IF(debugenable , "securityservice Judge failed result:%d , service:%s , judge oprInfo oprID:%d , it->oprType:%d , param:%s" , 
                    jResult , String8((*itService)->name).string(), oprID , (*it)->oprType , String8(param).string());
		initFakeReply((*itService)->name,oprID,reply);
		return false;
            }
        }
        it++;
    }
    ALOGI_IF(debugenable , "securityservice doJudge ok not find matched opration_id, so needn't check");
    return true;
}

static __inline sp<IBinder> getSecurityService() 
{
    if (gSecurityService != NULL)
        return gSecurityService;
    gSecurityService = defaultServiceManager()->getService(String16(SECURITY_NAME));
    return gSecurityService;
}

static bool isKeyService(const String16& name, const sp<IBinder>& svc)
{
    ALOGI_IF(debugenable , "securityservice isKeyService service:%s." , String8(name).string());
    if (getSecurityService() == NULL)
    {
        ALOGW_IF(debugenable , "isKeyService service:%s getSecurityService is NULL" , String8(name).string());

        bool bFind = false;
        List<KeyService*>::iterator it = gUnknownService.begin();
        while (it != gUnknownService.end()) {
            if ((*it)->name == name) {
                (*it)->svc = svc;
                ALOGI_IF(debugenable , "isKeyService %s has already been find in gUnknownService " , String8(name).string());
                bFind = true;
               break;
            }
            it++;
        }
    
        if(bFind) return false;
    
        KeyService* s = new KeyService(name, svc);
//        ALOGD_IF(debugenable , "isKeyService push back gUnknownService s->svc:%x , name:%s" , s->svc.get()  , String8(name).string());
        ALOGD_IF(debugenable , "isKeyService push back gUnknownService name:%s" , String8(name).string());
        gUnknownService.push_back(s);
        gHasUnknownService = true;
        return false;
    }
    ALOGI_IF(debugenable , "securityservice Func:%s service:%s E" , __FUNCTION__, String8(name).string());
    Parcel data, reply;
    data.writeInterfaceToken(String16(SECURITY_DESCRIPTION));
    data.writeString16(name);
    status_t err = gSecurityService->transact(TRANSACTION_isKeyService, data, &reply, 0);
    err = reply.readExceptionCode();
    int bRet = reply.readInt32();
    ALOGI_IF(debugenable , "securityservice Func:%s service:%s , return value:%d" , __FUNCTION__ , String8(name).string(), bRet );
    return (bRet != 0);
}

static void getKeyInterfaces(const String16& name, List<sp<OprDetail> >& oprList)
{
    ALOGI_IF(debugenable , "securityservice Func:%s service:%s E" , __FUNCTION__, String8(name).string());
    if (getSecurityService() == NULL)
    {
        ALOGW_IF(debugenable , "securityservice getKeyInterfaces service:%s getSecurityService is NULL" , String8(name).string());
        return;
    }
    if(0 == strcmp(String8(name).string(), "connectivity")){
        readConnTransCode();
    }
    Parcel data, reply;
    data.writeInterfaceToken(String16(SECURITY_DESCRIPTION));
    data.writeString16(name);
    data.writeInt32(15);
    data.writeInt32(15);
    status_t err = gSecurityService->transact(TRANSACTION_getKeyInterfaces, data, &reply, 0);
    err = reply.readExceptionCode();
    int result = reply.readInt32();
    int  size = reply.readInt32();
    oprList.clear();
    unsigned int oprID[15];
    OprType oprType[15];
    ALOGI_IF(debugenable , "securityservice getKeyInterfaces result -%d",result);
    for(int i=0; i<size; i++){
        oprID[i] = reply.readInt32();
    }
    reply.readInt32();
    for(int i=0; i<size; i++){
        oprType[i] = (OprType)reply.readInt32();
    }
    for(int i=0; i<size; i++){
        sp<OprDetail> oprDetail = new OprDetail(oprID[i], oprType[i]);
        oprList.push_back(oprDetail);
        ALOGD_IF(debugenable , "securityservice Func:%s service:%s , push back oprInfo index:%d , oprID:%d , oprType:%d" , __FUNCTION__ , String8(name).string() , i ,oprID[i] , oprType[i]);
    }
}

static void getSysUids(List<int>& uids)
{
    if (getSecurityService() == NULL)
    {
        ALOGW_IF(debugenable , "securityservice getSysUids getSecurityService is NULL");
        return;
    }
    Parcel data, reply;
    data.writeInterfaceToken(String16(SECURITY_DESCRIPTION));
    ALOGI_IF(debugenable , "securityservice Func:%s for enter" , __FUNCTION__ );
    status_t err = gSecurityService->transact(TRANSACTION_getSysUids, data, &reply, 0);
    err = reply.readExceptionCode();
    int size = reply.readInt32();
    uids.clear();
    for(int i=0; i<size; i++){
        int uid = reply.readInt32();
        uids.push_back(uid);
        ALOGI_IF(debugenable , "securityservice Func:%s push back sysID index:%d , sysId:%d" , __FUNCTION__ , i , uid);
    }
    ALOGI_IF(debugenable , "securityservice Func:%s for exit" , __FUNCTION__ );
}

JdgRet judge(int uid, const String16& name, int oprID, int oprType, const String16& param)
{
    if (getSecurityService() == NULL)
    {
        ALOGW_IF(debugenable , "securityservice judge service:%s getSecurityService is NULL" , String8(name).string());
        return JDG_UNKNOWN;
    }

    ALOGI_IF(debugenable , "securityservice libsecbinder:invoke SecurityService");	
    Parcel data, reply;
    data.writeInterfaceToken(String16(SECURITY_DESCRIPTION));
    data.writeInt32(uid);
    data.writeString16(name);
    data.writeInt32(oprID);
    data.writeInt32(oprType);
    data.writeString16(param);
    status_t err = gSecurityService->transact(TRANSACTION_judge, data, &reply, 0);
    err = reply.readExceptionCode();
    return (JdgRet)reply.readInt32();
}
}
/*-------------------------- Security Data & Interface  END---------------------------------*/
#endif
#endif

