#define LOG_TAG "performance"
#include <stdint.h>
#include <sys/types.h>
#include <errno.h>
#include <math.h>
#include <dlfcn.h>
#include <inttypes.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <cutils/log.h>
#include <cutils/properties.h>
#include <unistd.h>
#include <sys/mman.h>
#include <mntent.h>
#include <ctype.h>
#include <sys/stat.h>
#include <sys/swap.h>

#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/MemoryHeapBase.h>
#include <binder/PermissionCache.h>


#include <utils/String8.h>
#include <utils/String16.h>

#include "PerformanceManager.h"

#define RECLAIM_TYPE_ANON 0
#define RECLAIM_TYPE_FILE 1
#define RECLAIM_TYPE_ALL 2
#define RECLAIM_TYPE_HIBER 3
#define VALUE_LEN 100
#define PAGE_SIZE 4096

namespace android {


PerformanceManager::PerformanceManager() : BnPerformanceManager(),inited(0)
{
    ALOGE("PerformanceManager --- oncreate..");
}

PerformanceManager::~PerformanceManager()
{
    ALOGE("PerformanceManager --- Destory..");
}

static ssize_t read_all(int fd, char *buf, size_t max_len)
{
    ssize_t ret = 0;

    while (max_len > 0) {
        ssize_t r = read(fd, buf, max_len);
        if (r == 0) {
            break;
        }
        if (r == -1) {
            return -1;
        }
        ret += r;
        buf += r;
        max_len -= r;
    }

    return ret;
}

static void writefilestring(const char *path, char *s) {
    int fd = open(path, O_WRONLY | O_CLOEXEC);
    int len = strlen(s);
    int ret;

    if (fd < 0) {
        //ALOGE("Error opening %s; errno=%d", path, errno);
        return;
    }

    ret = write(fd, s, len);
    if (ret < 0) {
        //ALOGE("Error writing %s; errno=%d", path, errno);
    } else if (ret < len) {
        //ALOGE("Short write on %s; length=%d", path, ret);
    }

    close(fd);
}

static void readfilestring(char * path, char *s) {
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    int len = VALUE_LEN;
    int ret;

    if (fd < 0) {
        //ALOGE("Error opening %s; errno=%d", path, errno);
        return;
    }

    ret = read_all(fd, s, len);
    if (ret < 0) {
        //ALOGE("Error read %s; errno=%d", path, errno);
    }

    close(fd);
}

static void handle_process_reclaim(int pid, const char * reclaim_type, char * result) {
    char path[80];
    char val[VALUE_LEN];

    snprintf(path, sizeof(path), "/proc/%d/reclaim", pid);
    snprintf(val, sizeof(val), "%s", reclaim_type);
    writefilestring(path, val);

    memset(path, 0, sizeof(path));
    snprintf(path, sizeof(path), "/proc/%d/reclaim_result", pid);
    readfilestring(path,result);
}

int PerformanceManager::reclaimProcess(int pid, const char *type, char* reclaimResult){
    //ALOGE("PerformanceManager::reclaimProcess for %d, type :%s", pid, type);
    handle_process_reclaim(pid, type, reclaimResult);
    return 0;
}
void PerformanceManager::enableBoostKill(int enable) {
    char val[VALUE_LEN];
    const char *p = "/proc/sys/kernel/boost_killing";
    snprintf(val, sizeof(val), "%d", enable);
    writefilestring(p, val);
}

int PerformanceManager::processReclaimEnabled() {
    int fd = open("/proc/self/reclaim", O_WRONLY | O_CLOEXEC);
    if (fd >= 0) {
        close(fd);
        fd = open("/proc/self/reclaim_result", O_RDONLY | O_CLOEXEC);
        if (fd >=0) {
            close(fd);
            return 1;
        }
    }
    return 0;
}

void PerformanceManager::readProcFile(const char * path, char* result) {
    char proc[80];
    snprintf(proc, sizeof(proc), "%s", path);
    readfilestring(proc, result);
}

void PerformanceManager::writeProcFile(const char* path, const char* value) {
    char proc[80];
    char val[80];

    if (!path || !value)
        return;

    snprintf(proc, sizeof(proc), "%s", path);
    snprintf(val, sizeof(val), "%s", value);
    //ALOGI("writeProcFile: path:%s, value:%s", proc, val);
    writefilestring(proc, val);
}

void PerformanceManager::getBlockDevName(const char* dirName, char* bdevName) {
    struct mntent *me;
    FILE *fp;
    const char *p = "/proc/mounts";

    if (!(fp = setmntent(p, "r"))) {
        ALOGE("error !!");
    } else {
        while ((me = getmntent(fp))) {
            if (strcmp(me->mnt_dir, dirName)==0) {
                //ALOGE("MOUNT:%s, %s, %s, %s",me->mnt_type, me->mnt_dir,me->mnt_fsname, me->mnt_opts);
                if (strcmp("/data", dirName) == 0) {
                    strcpy(bdevName, me->mnt_fsname);
                } else {
                    readlink(me->mnt_fsname, bdevName,128);
                }
                //ALOGE("abs : %s", bdevName);
            }
        }
        endmntent(fp);
    }
}

static void fetchIntoPageCache(int fd, off_t offset, size_t size) {
    char * buf = (char *)malloc(size);
    pread(fd, buf, size, offset);
    free(buf);
    //ALOGE("fetchIntoPageCache :%d bytes read in to cache for fd %d", bytes, fd);
}
/*
static int size2pageCount(long size ) {
    return (int) (size + (long) PAGE_SIZE - 1L) / PAGE_SIZE;
}
*/
void PerformanceManager::freeFetchData(void * addr, size_t length) {
    if (addr) {
        munlock(addr, length);
        if (munmap(addr, length) != 0) {
            //ALOGE("freeFetchData failed for --> %p", addr);
        }
        //ALOGE("freeFetchData unmap for --> %p", addr);
    }
}

long PerformanceManager::fetchIfCacheMiss(const char* filepath, off_t* offset, size_t * length,
                                          int totalCount, bool fetch, bool lock) {
    void *addr;
    int fd = -1;
    struct stat sb;

    if (!fetch) {
        return 0;
    }
    fd = open(filepath, O_RDONLY);
    if (fd == -1) {
        //ALOGE("fetchIfCacheMiss open %s failed:%s\n", filepath, strerror(errno));
        return 0;
    }

    if (fstat(fd, &sb) == -1) {
        //ALOGE("fetchIfCacheMiss fstat %s failed:%s\n", filepath, strerror(errno));
        close(fd);
        return 0;
    }
    addr = mmap(NULL, sb.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
    if (addr == MAP_FAILED) {
        //ALOGE("fetchIfCacheMiss mmap %s failed:%s\n",filepath,strerror(errno));
        close(fd);
        return 0;
    }
    for (int i = 0;i < totalCount; i++) {

        fetchIntoPageCache(fd, offset[i], length[i]);
        /*
        int pageCount = size2pageCount((long)length[i]);
        vec = (unsigned char *)malloc(pageCount);
        void * addrn = addr;
        addrn = (void *)((long)addrn + offset[i]);
        ALOGE("fetchIfCacheMiss %s  : address: %p offset %ld length:%d\n",filepath, addrn, offset[i], length[i]);
        if (fetch && vec != NULL && mincore(addrn, length[i], vec) == 0) {
            for (int j = 0;j < pageCount;j++) {
                if (vec[j] == 0) {
                    fetchIntoPageCache(fd, offset[i]+j*PAGE_SIZE, PAGE_SIZE);
                }
            }
            free(vec);
        }
        */
    }
    if (lock) {
        if (mlock(addr, sb.st_size) == -1) {
            //ALOGE("fetchIfCacheMiss mlock file %s failed:%s\n",filepath,strerror(errno));
        }
    }
    if (fd >= 0)
        close(fd);
    ALOGE("fetchIfCacheMiss %p for file :%s",addr, filepath);
    return (long)addr;
}


void PerformanceManager::init() {}
void PerformanceManager::run(){}
status_t PerformanceManager::onTransact(uint32_t code, const Parcel& data,
    Parcel* reply, uint32_t flags){
    status_t res = BnPerformanceManager::onTransact(code, data, reply, flags);
    return res;
}
}
