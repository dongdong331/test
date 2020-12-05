
#ifndef ANDROID_PERFORMANCE_H
#define ANDROID_PERFORMANCE_H
#include <utils/RefBase.h>
#include <sys/types.h>
#include "IPerformanceManager.h"

#define MAX_PATH_LEN 128

namespace android {

class PerformanceManager : public BnPerformanceManager 
{
public:
    static char const* getServiceName() {
        return "performancemanager";
    }
    PerformanceManager();
    virtual ~PerformanceManager();
    void init();
    void run();
    virtual int reclaimProcess(int pid, const char *type, char* reclaimResult);
    virtual void enableBoostKill(int enable);
    virtual int processReclaimEnabled();
    virtual void readProcFile(const char* path, char* result);
    virtual void writeProcFile(const char* path, const char* value);
    virtual long fetchIfCacheMiss(const char* filepath, off_t* offset, size_t * length,
                                          int totalCount, bool fetch, bool lock);
    virtual void freeFetchData(void * addr, size_t length);
    virtual void getBlockDevName(const char* dirName, char* bdevName);
    int inited;
    virtual status_t onTransact(uint32_t code, const Parcel& data,
        Parcel* reply, uint32_t flags);
};
}
#endif
