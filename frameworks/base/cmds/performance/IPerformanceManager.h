/**
 * Copyright (C) 2017 Spreadtrum Communications Inc.
 */

#ifndef IPERFORMANCE_H
#define IPERFORMANCE_H

#include <binder/IInterface.h>
#include <binder/Parcel.h>

namespace android {

class IPerformanceManager: public IInterface {
 public:
    DECLARE_META_INTERFACE(PerformanceManager);

    virtual int reclaimProcess(int pid, const char *type, char* reclaimResult) = 0;
    virtual void enableBoostKill(int enable) = 0;
    virtual int processReclaimEnabled() = 0;
    virtual void readProcFile(const char * path, char* result) = 0;
    virtual void writeProcFile(const char * path, const char* value) = 0;
    virtual long fetchIfCacheMiss(const char* filepath, off_t* offset, size_t * length,
                                          int totalCount, bool fetch, bool lock) = 0;
    virtual void freeFetchData(void * addr, size_t length) = 0;
    virtual void getBlockDevName(const char* dirName, char* bdevName) = 0;
};

class BnPerformanceManager: public BnInterface<IPerformanceManager> {
 public:
    virtual status_t onTransact(uint32_t code, const Parcel& data,
            Parcel* reply, uint32_t flags = 0);
};

};  // namespace android

#endif
