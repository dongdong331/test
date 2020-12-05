/**
 * Copyright (C) 2017 Spreadtrum Communications Inc.
 */

#define LOG_TAG "performance"

#include <IPerformanceManager.h>
#include <binder/Parcel.h>
#include <utils/String8.h>
#include <utils/String16.h>
#include <cutils/jstring.h>

namespace android {
#define PACKAGE_NAME    "com.android.server.performance"

enum {
    TRANSACTION_PROCESS_RECLAIM = IBinder::FIRST_CALL_TRANSACTION + 0,
    TRANSACTION_ENABLE_BOOST_KILL = IBinder::FIRST_CALL_TRANSACTION + 1,
    TRANSACTION_PROCESS_REACLAIM_ENABLED = IBinder::FIRST_CALL_TRANSACTION + 2,
    TRANSACTION_READ_PROC_FILE = IBinder::FIRST_CALL_TRANSACTION + 3,
    TRANSACTION_WRITE_PROC_FILE = IBinder::FIRST_CALL_TRANSACTION + 4,
    TRANSACTION_EXTRA_FETCH = IBinder::FIRST_CALL_TRANSACTION + 5,
    TRANSACTION_FREE_FETCH_DATA = IBinder::FIRST_CALL_TRANSACTION + 6,
    TRANSACTION_GET_BLOCKDEV_NAME = IBinder::FIRST_CALL_TRANSACTION + 7,
};

static char *strdupReadString(Parcel &p) {
    size_t stringlen;
    const char16_t *s16;

    s16 = p.readString16Inplace(&stringlen);

    return strndup16to8(s16, stringlen);
}

static size_t * readIntArray(const Parcel &data, int* count) {
    int length = data.readInt32();
    if (length != 0) {
        *count = length; 
        size_t * array = (size_t *)malloc(sizeof(size_t)*length);
        if (array) {
            for (int i = 0;i <length;i++) {
                array[i] = (size_t)data.readInt32();
            }
            return array;
        }
    }
    return NULL;
}

static long * readLongArray(const Parcel &data, int* count) {
    int length = data.readInt32();
    if (length != 0) {
        *count = length;
        long * array = (long *)malloc(sizeof(long)*length);
        if (array) {
            for (int i = 0;i <length;i++) {
                array[i] = data.readInt64();
            }
            return array;
        }
    }
    return NULL;
}

class BpPerformanceManager: public BpInterface<IPerformanceManager> {
 public:
    explicit BpPerformanceManager(const sp<IBinder>& impl)
        : BpInterface<IPerformanceManager>(impl) {
    }

    virtual int reclaimProcess(int pid, const char *type, char* reclaimResult) {
        Parcel data, reply;
        data.writeInterfaceToken(IPerformanceManager::getInterfaceDescriptor());
        data.writeInt32(pid);
        data.writeString16(String16(type));
        if (remote()->transact(TRANSACTION_PROCESS_RECLAIM, data, &reply) !=
                NO_ERROR) {
            ALOGE("reclaimProcess could not contact remote");
            return -1;
        }
        int32_t err = reply.readExceptionCode();
        if (err < 0) {
            ALOGE("reclaimProcess caught exception %d", err);
            return -1;
        }
        char* reclaimed = strdupReadString(reply);
        if (reclaimed != NULL) {
            memcpy(reclaimResult, reclaimed, strlen(reclaimed) + 1);
        }
        free((void *)reclaimed);
        return 0;
    }
    virtual void enableBoostKill(int enable){
        ALOGE("enableBoostKill %d", enable);
    }
    virtual int processReclaimEnabled(){
        return 0;
    }
    virtual void readProcFile(const char * path, char* result){
        ALOGE("readProcFile %s, resultis %s", path, result);
    }
    virtual void writeProcFile(const char * path, const char* value) {
        ALOGE("writeProcFile:%s, value is %s", path, value);
    }
    virtual long fetchIfCacheMiss(const char* filepath, off_t* offset, size_t * length,
                                          int totalCount, bool fetch, bool lock) {
        ALOGE("fetchIfCacheMiss %s, %p,%p,%d,%d,%d",filepath, offset, length, totalCount, fetch, lock);
        return 0;
    }
    virtual void freeFetchData(void * addr, size_t length){
        ALOGE("freeFetchData %p, %d", addr, (int)length);
    }
    virtual void getBlockDevName(const char* dirName, char* bdevName){
        ALOGE("getBlockDevName ,%s, %s", dirName, bdevName);
    }
};

IMPLEMENT_META_INTERFACE(PerformanceManager, PACKAGE_NAME);



status_t BnPerformanceManager::onTransact(uint32_t code, const Parcel& data,
            Parcel* reply, uint32_t flags)
{
    //ALOGE("BnPerformanceManager:: onTransact: %d", code);
    switch (code) {
        case TRANSACTION_PROCESS_RECLAIM: {
            CHECK_INTERFACE(IPerformanceManager, data, reply);
            int pid = data.readInt32();
            char result [1024];
            String8 type = String8(data.readString16());
            reclaimProcess(pid , type.string(), result);
            reply->writeString16(String16(result));
            return NO_ERROR;
         }
         case TRANSACTION_ENABLE_BOOST_KILL: {
             CHECK_INTERFACE(IPerformanceManager, data, reply);
             int enable = data.readInt32();
             enableBoostKill(enable);
             return NO_ERROR;
         }
         case TRANSACTION_PROCESS_REACLAIM_ENABLED: {
             CHECK_INTERFACE(IPerformanceManager, data, reply);
             int enable = processReclaimEnabled();
             reply->writeInt32(enable);
             return NO_ERROR;
         }
         case TRANSACTION_READ_PROC_FILE: {
             CHECK_INTERFACE(IPerformanceManager, data, reply);
             char result [1024];
             String8 path = String8(data.readString16());
             readProcFile(path.string(), result);
             reply->writeString16(String16(result));
             return NO_ERROR;
         }
         case TRANSACTION_WRITE_PROC_FILE: {
             CHECK_INTERFACE(IPerformanceManager, data, reply);
             String8 path = String8(data.readString16());
             String8 value = String8(data.readString16());
             writeProcFile(path.string(), value.string());
             return NO_ERROR;
         }
         case TRANSACTION_EXTRA_FETCH: {
             CHECK_INTERFACE(IPerformanceManager, data, reply);
             String8 path = String8(data.readString16());
             int count = 0;
             long * offset = readLongArray(data, &count);
             size_t * length = readIntArray(data, &count);
             bool fetch = data.readInt32() == 1 ? true:false;
             bool lock = data.readInt32() == 1 ? true:false;
             long addr = 0;
             if (offset && length && count) {
                 addr = fetchIfCacheMiss(path, offset, length, count, fetch, lock);
                 free(offset);
                 free(length);
             }
             reply->writeInt64(addr);
             return NO_ERROR;
         }
         case TRANSACTION_FREE_FETCH_DATA: {
             CHECK_INTERFACE(IPerformanceManager, data, reply);
             long addr = data.readInt64();
             unsigned int length = data.readInt32();
             freeFetchData((void*)addr, length);
             return NO_ERROR;
         }
         case TRANSACTION_GET_BLOCKDEV_NAME: {
             CHECK_INTERFACE(IPerformanceManager, data, reply);
             char result [128];
             String8 dirName = String8(data.readString16());
             getBlockDevName(dirName.string(), result);
             reply->writeString16(String16(result));
             return NO_ERROR;
         }
         default: {
             return BBinder::onTransact(code, data, reply, flags);
         }
    }
}
};  // namespace android
