/**
 * Copyright (C) 2018 UNISOC Communications Inc.
 */

#include <inttypes.h>
#include <pthread.h>
#include <sys/mman.h>  // For madvise
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <memory>

#include "base/logging.h"
#include "android-base/stringprintf.h"
#include "android-base/strings.h"

#include <sys/time.h>
#include <pthread.h>
#include <sys/param.h>
#include <sys/wait.h>
#include <sys/socket.h>
#include <poll.h>
#include <signal.h>
#include <errno.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <paths.h>
#include <backtrace/BacktraceMap.h>
#include <android/log.h>
#include "native_stack_dump.h"
#include "android-base/file.h"
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#ifdef ART_TARGET_ANDROID
#include <cutils/properties.h>
#endif

#if defined(__APPLE__)
#include "AvailabilityMacros.h"  // For MAC_OS_X_VERSION_MAX_ALLOWED
#include <sys/syscall.h>
#include <crt_externs.h>
#endif

#if defined(__linux__)
#include <linux/unistd.h>
#endif

namespace art {

using android::base::StringAppendF;
using android::base::StringPrintf;

#define AID_SYSTEM 1000 // From: system/core/include/private/android_filesystem_config.h
#define UNISOC_BASE "/data/resource-cache/"

static int TimeFormatTime(char *timeBuf, struct timeval *tv) {
    struct tm tm;
    struct tm* ptm;
    time_t t;
    int len;

    t = tv->tv_sec; //time(NULL);
    //#if defined(HAVE_LOCALTIME_R)
    ptm = localtime_r(&t, &tm);
    //#else
    //    ptm = localtime(&t);
    //#endif
    /* strftime(timeBuf, sizeof(timeBuf), "%Y-%m-%d %H:%M:%S", ptm); */
    len = strftime(timeBuf, 32, "[%m-%d %H:%M:%S", ptm);
    /* 01-01 22:14:36.000   826   826 D SignalClusterView_dual: */
    len += snprintf(timeBuf + len, 32 - len,
            ".%03ld] ", tv->tv_usec / 1000);
    return len;
}

static int TimeGetFormatTime(char *buf) {
    char *timeBuf = buf;
    struct timeval tv;
    //char timeBuf[32];/* good margin, 23+nul for msec, 26+nul for usec */
    /* From system/core/liblog/logprint.c */
    /*
     * Get the current date/time in pretty form
     *
     * It's often useful when examining a log with "less" to jump to
     * a specific point in the file by searching for the date/time stamp.
     * For this reason it's very annoying to have regexp meta characters
     * in the time stamp.  Don't use forward slashes, parenthesis,
     * brackets, asterisks, or other special chars here.
     */
    gettimeofday(&tv, NULL);
    return TimeFormatTime(timeBuf, &tv);
}

#define CLOSE(fd) ({\
    int ll_ret = -1; \
    if (fd >= 0) { \
        ll_ret = close(fd); \
        if (ll_ret < 0) \
            PLOG(ERROR) << "close"; \
    } \
    ll_ret; \
})

static int open_with_maxsize(const char *filename, long max_size, int uid, int gid, int flags, int mode) {
    int fd;
    /* while (fd == -1) */ {
        int ret = 0;
        fd = open(filename, flags, 0644);
        if (fd != -1) {
            struct stat sbuf;
            ret |= fstat(fd, &sbuf);
            if (ret == 0) {
                LOG(INFO) << android::base::StringPrintf("%s size is %d", filename, (int)sbuf.st_size);
                if (sbuf.st_size >= max_size)
                    ret |= ftruncate(fd, 0);
            } else
                PLOG(WARNING) << android::base::StringPrintf("fstat failed on %s", filename);
            if (sbuf.st_size == 0) {
                ret |= fchown(fd, uid, gid);
                ret |= fchmod(fd, mode);
                if (ret != 0) {
                    PLOG(WARNING) << android::base::StringPrintf("fchown or fchown failed on %s", filename);
                    // usleep(2*1000*1000);
                }
            }
        } else {
            /**
             * /system/bin/webview_zygote32: open failed on /data/resource-cache/zygote.log: Permission denied
             */
            PLOG(WARNING) << android::base::StringPrintf("open failed on %s", filename);
        }
    }
    return fd;
}

typedef int (*pcmd_write_handler)(char *buf, int count, void *priv);
struct pcmd_event {
    int fd;
    int times;
    int sleep_ms;
    int max_size;
    char buf[4096];
    int buf_count;
    const char *pcmds_list[128];
};

static struct pid {
    struct pid *next;
    FILE *fp;
    pid_t pid;
} *pidlist;

static pthread_mutex_t popen2_pidlist_mutex = PTHREAD_MUTEX_INITIALIZER;

FILE *popen2(const char *command, const char *type) {
    struct pid *cur;
    FILE *iop;
    int pdes[2], pid;
    int volatile twoway;
    struct pid *p;
    const char * volatile xtype = type;

    if (strchr(xtype, '+')) {
        twoway = 1;
        xtype = "r+";
        if (socketpair(AF_UNIX, SOCK_STREAM, 0, pdes) < 0)
            return (NULL);
    } else {
        twoway = 0;
        if ((xtype[0] != 'r' && xtype[0] != 'w') || xtype[1])
            return (NULL);
    }
    if (pipe(pdes) < 0)
        return (NULL);

    if ((cur = (struct pid *)malloc(sizeof(struct pid))) == NULL) {
        (void)CLOSE(pdes[0]);
        (void)CLOSE(pdes[1]);
        errno = ENOMEM;
        return (NULL);
    }

    switch (pid = vfork()) {
        case -1:            /* Error. */
            (void)CLOSE(pdes[0]);
            (void)CLOSE(pdes[1]);
            free(cur);
            return (NULL);
            /* NOTREACHED */
        case 0:                /* Child. */
            if (xtype[0] == 'r') {
                /*
                 * The _dup2() to STDIN_FILENO is repeated to avoid
                 * writing to pdes[1], which might corrupt the
                 * parent's copy.  This isn't good enough in
                 * general, since the _exit() is no return, so
                 * the compiler is free to corrupt all the local
                 * variables.
                 */
                (void)CLOSE(pdes[0]);
                if (pdes[1] != STDOUT_FILENO) {
                    (void)dup2(pdes[1], STDOUT_FILENO);
                    (void)CLOSE(pdes[1]);
                    if (twoway)
                        (void)dup2(STDOUT_FILENO, STDIN_FILENO);
                } else if (twoway && (pdes[1] != STDIN_FILENO)) {
                    (void)dup2(pdes[1], STDIN_FILENO);
                }
            } else {
                if (pdes[0] != STDIN_FILENO) {
                    (void)dup2(pdes[0], STDIN_FILENO);
                    (void)CLOSE(pdes[0]);
                }
                (void)CLOSE(pdes[1]);
            }
            for (p = pidlist; p; p = p->next) {
                (void)CLOSE(fileno(p->fp));
            }
            //execl(_PATH_BSHELL, "sh", "-c", command, NULL);
            /**
             * if we use sh -c, the pclose2 can't kill all
             * the child created by sh -c,
             * will became zombie process attached to init by luther
             */
            // execl(command, "111111111111111", NULL);
#if 0
            {
                char **env = environ;
                while (*env){
                    printf("environ %s\n", *env);
                    env++;
                }
            }
#endif
            // execle(command, basename(command), NULL, environ);
            {
                char tmp[256];
                char *cmd;
                char *last;
                char *argv[100];
                int i;
                memset(argv, 0, sizeof argv);
                strcpy(tmp, command);
                cmd=strtok_r(tmp, " ", &last);
                argv[0] = cmd;
                for (i = 1; i < 100; i++) {
                    argv[i] = strtok_r(NULL, " ", &last);
                    if (argv[i] == NULL)
                        break;
                }
                execvp(cmd, argv); // In bionic/libc/bionic/exec.cpp
#if 0
execvp ==> execvpe ==> execve
kernel/fs/exec.c
SYSCALL_DEFINE3(execve,
		const char __user *, filename,
		const char __user *const __user *, argv,
		const char __user *const __user *, envp)
{
	return do_execve(getname(filename), argv, envp);
}
#endif
                PLOG(ERROR) << android::base::StringPrintf("zygote pcmd popen2 %s", command);
            }
            _exit(127);
            /* NOTREACHED */
    }

    /* Parent; assume fdopen can't fail. */
    if (xtype[0] == 'r') {
        iop = fdopen(pdes[0], xtype);
        (void)CLOSE(pdes[1]);
    } else {
        iop = fdopen(pdes[1], xtype);
        (void)CLOSE(pdes[0]);
    }

    /* Link into list of file descriptors. */
    cur->fp = iop;
    cur->pid =  pid;
    pthread_mutex_lock(&popen2_pidlist_mutex);
    cur->next = pidlist;
    pidlist = cur;
    pthread_mutex_unlock(&popen2_pidlist_mutex);

    return (iop);
}

/**
 *    pclose2 returns -1 if stream is not associated with a `popened' command,
 *    if already `pclosed', or waitpid returns an error.
 */
int pclose2(FILE *iop) {
    struct pid *cur, *last;
    int pstat;
    pid_t pid;
    int fd;

    pthread_mutex_lock(&popen2_pidlist_mutex);
    /* Find the appropriate file pointer. */
    for (last = NULL, cur = pidlist; cur; last = cur, cur = cur->next)
        if (cur->fp == iop)
            break;
    if (cur == NULL) {
        pthread_mutex_unlock(&popen2_pidlist_mutex);
        return (-1);
    }
    /* Remove the entry from the linked list. */
    if (last == NULL)
        pidlist = cur->next;
    else
        last->next = cur->next;
    pthread_mutex_unlock(&popen2_pidlist_mutex);

    fd = cur->pid;

    kill(fd, SIGKILL);
    //kill(fd, SIGINT);
    //kill(fd, SIGTERM);

    (void)fclose(iop);

    do {
        pid = waitpid(cur->pid, &pstat, 0);
    } while (pid == -1 && errno == EINTR);

    free(cur);

    return (pid == -1 ? -1 : pstat);
}

static int fcntl_read_nonblock(int fd, const char *desc ATTRIBUTE_UNUSED) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) {
        PLOG(ERROR) << "xxxxxxxxxxxxxxxxxxxxxx fcntl_nonblock";
        return 1;
    }
    flags |= O_NONBLOCK;
    if (fcntl(fd, F_SETFL, flags) < 0) {
        PLOG(ERROR) << "xxxxxxxxxxxxxxxxxxxxxx fcntl_nonblock";
        return 1;
    }
    return 0;
}

static int fcntl_read_block(int fd, const char *desc ATTRIBUTE_UNUSED) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) {
        PLOG(ERROR) << "xxxxxxxxxxxxxxxxxxxxxx fcntl_nonblock";
        return 1;
    }
    flags &= ~O_NONBLOCK;
    if (fcntl(fd, F_SETFL, flags) < 0) {
        PLOG(ERROR) << "xxxxxxxxxxxxxxxxxxxxxx fcntl_nonblock";
        return 1;
    }
    return 0;
}

static int pcmd(const char *cmd, pcmd_write_handler w,
        char *prefix, int millisecond, int max_size, void *priv) {
    struct pcmd_event *pe = (struct pcmd_event *)priv;
    char *buf = pe->buf;
    int count = pe->buf_count;
    char *p = (char*)buf;
    char *pmax = p + count;
    int ret, rcnt = 0, timeout = 0;
    FILE *wfp = popen2(cmd, (const char*)"r");
    char timeBuf[32];
    if (wfp) {
        int fd = fileno(wfp);
        struct pollfd pfd[1];
        if (w) {
            TimeGetFormatTime(timeBuf);
            if (prefix == NULL)
                prefix = (char*)"pcmd";
            rcnt += w(p, snprintf(p, pmax - p, "\n%s [ %s ]\n", prefix, cmd), priv);
        }
        pfd[0].fd = fd;
        pfd[0].events = POLLIN;
        if (millisecond < 0)
            millisecond = 500;
        do {
            ret = poll(pfd, 1, millisecond);
            if (ret <= 0) {
                PLOG(ERROR) << "poll on " << android::base::StringPrintf("%s fd=%d done", cmd, fd);
                timeout = 1;
                break;
            }
            if (fcntl_read_nonblock(fd, cmd) == 0) {
                ret = read(fd, p, pmax - p);
                if (ret > 0)
                    fcntl_read_block(fd, cmd);
            } else
                ret = 0;
            if (ret > 0) {
                if (w)
                    w(p, ret, priv);
                rcnt += ret;
                if (max_size > 0) {
                    if (rcnt >= max_size) { /* reach to the max size we want to receive */
                        LOG(ERROR) << "pcmd reaches the max size";
                        break;
                    }
                }
            }
        } while (ret > 0);
        if (w) {
            if (timeout)
                ret = snprintf(buf, count, "xxxxxxxxxxxxxxxxxxxxxx cmd %s is failed, %s\n",
                        cmd, ret == 0 ? "timeout":strerror(errno));
            else
                ret = snprintf(buf, count, "\n");
            rcnt += w(buf, ret, priv);
        }
        pclose2(wfp);
    } else {
        PLOG(ERROR) << "popen2 failed";
    }
    return rcnt;
}

static int pe_write_handler__pcmd_print2file(char *buf, int count, void *priv) {
    struct pcmd_event *pe = (struct pcmd_event *)priv;
    if (pe->fd >= 0)
        return android::base::WriteFully(pe->fd, buf, count);
    return count;
}

static void *pcmd_handler_default(void *arg) {
    int rcnt = 0;
    const char **cmd;
    struct pcmd_event *pe = (struct pcmd_event *)arg;
    int times = pe->times;
    int sleep_ms = pe->sleep_ms;
    int fd = pe->fd;

    if (times < 0)
        times = INT_MAX;

    std::string file_path;
    if (fd >= 0) {
        const std::string fd_path = android::base::StringPrintf("/proc/self/fd/%d", fd);
        if (!android::base::Readlink(fd_path, &file_path)) {
            PLOG(ERROR) << "Readlink of " << fd_path;
        }
    }

    for (; times; times--) {
        for (cmd = pe->pcmds_list; *cmd; cmd++) {
            LOG(WARNING) << android::base::StringPrintf("pcmd %s to [%d] = ", *cmd, fd) << file_path;
            rcnt += pcmd(*cmd, pe_write_handler__pcmd_print2file,
                    NULL, 800, pe->max_size, pe);
        }
        if (times > 1)
            usleep(sleep_ms * 1000);
    }

    for (cmd = pe->pcmds_list; *cmd; cmd++)
        free((void*)*cmd);
    /**
     * We should close it ASAP, otherwise frameworks/base/core/jni/fd_utils.cpp will call : *error_msg = std::string("Not whitelisted : ").append(file_path);
     */
    if (fd >= 0)
        close(fd);
    free(pe);
    return NULL;
}

static int getprocname(pid_t pid, char *buf, size_t len) {
    char filename[32];
    FILE *f;

    snprintf(filename, sizeof(filename), "/proc/%d/cmdline", pid);
    f = fopen(filename, "r");
    if (!f) {
        *buf = '\0';
        return 1;
    }
    if (!fgets(buf, len, f)) {
        *buf = '\0';
        fclose(f);
        return 2;
    }
    fclose(f);
    return 0;
}

void pcmds_start(int fd, const char *filename, const char *pcmds_list[], long max_size, int sync = 1, int times = 1, int sleep_ms = 0) {
    struct pcmd_event *pe;
    pthread_t ptid;

    pe = (struct pcmd_event *)calloc(1, sizeof(*pe));
    if (pe == NULL) {
        PLOG(ERROR) << "calloc";
        return;
    }

    const char **pcm = pe->pcmds_list;
    for (const char **cmd = pcmds_list; *cmd; cmd++) {
        *pcm++ = strdup(*cmd); // max 128
    }
    *pcm = NULL;

    pe->fd = fd >= 0 ? dup(fd):-1;
    pe->times = times;
    pe->sleep_ms = sleep_ms;
    pe->max_size = max_size;
    pe->buf_count = sizeof(pe->buf);

    if (pe->fd < 0) {
        if (filename == NULL) {
            char process_name[256] = {0};
            pid_t pid = getpid();
            memcpy(process_name, UNISOC_BASE, sizeof(UNISOC_BASE));
            getprocname(pid, process_name + sizeof(UNISOC_BASE) - 1, sizeof(process_name) - sizeof(UNISOC_BASE));
            if (process_name[sizeof(UNISOC_BASE) - 1]) {
                pe->fd = open_with_maxsize(process_name, max_size,
                        AID_SYSTEM, AID_SYSTEM, O_APPEND | O_CREAT | O_WRONLY, 0644);
            }
        }
    }

    if (pthread_create(&ptid, NULL, pcmd_handler_default, pe)) {
        PLOG(ERROR) << "Failed to pthread_create";
        for (const char **cmd = pe->pcmds_list; *cmd; cmd++)
            free((void*)*cmd);
        free(pe);
        return;
    }

    if (sync)
        pthread_join(ptid, NULL);
}

void PrintLogToFile(const std::string& content) {
    int fd = -1;
    char timeBuf[64];
    fd = open_with_maxsize(UNISOC_BASE"zygote.log",
            50*1024*1024, AID_SYSTEM, AID_SYSTEM, O_APPEND | O_CREAT | O_WRONLY, 0644);
    if (fd < 0)
        return;

    android::base::WriteFully(fd, timeBuf, TimeGetFormatTime(timeBuf));
    android::base::WriteStringToFd(content+"\n", fd);
    /**
     * We should close it, otherwise frameworks/base/core/jni/fd_utils.cpp will call : *error_msg = std::string("Not whitelisted : ").append(file_path);
     */
    close(fd);
    LOG(WARNING) << content;
}

#ifdef ART_TARGET_ANDROID
void print_backtrace2logd(void) {
    std::ostringstream oss;
    char process_name[256] = {0};
    pid_t pid = getpid();
    std::string content;
    getprocname(pid, process_name, sizeof process_name);
    DumpNativeStack(oss, gettid(), NULL, "\t");
    content = "UNISOC Backtrace: " + android::base::StringPrintf("%s\n", process_name) + oss.str();
    // __android_log_write(ANDROID_LOG_ERROR, "UNISOC-Backtrace", oss.str().c_str());
    // LOG(WARNING) << content; // PrintLogToFile will output the log to logd also
    PrintLogToFile(content);
#if 0
    // SELinux Permissive Mode is needed
    // # setenforce 0
    const char *cmds[] = {
        "start ylog",
        "mount",
        "free -m",
        "date",
        "uptime",
        // "cat /dev/kmsg",
        "dmesg",
        // "logcat -d",
        "logcat", // never return
        "/system/bin/cat /proc/meminfo",
        NULL,
    };
    // property_set("ctl.start", "ylog");
    pcmds_start(-1, NULL, cmds, 50*1024*1024, 0);
    // system("/system/bin/dmesg >> /data/resource-cache/kmsg.log");
    // system("/system/bin/logcat -d >> /data/resource-cache/logcat.log");
#endif
}
#else
void print_backtrace2logd(void) { }
#endif

} // namespace art
