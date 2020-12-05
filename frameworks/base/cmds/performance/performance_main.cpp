/*
 * Copyright © 2017 Spreadtrum Communications Inc.
 */

#define LOG_TAG "performance"

#include <binder/IServiceManager.h>
#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <sys/resource.h>
#include <utils/Log.h>
#include <utils/threads.h>

#include "PerformanceManager.h"

using namespace android;

int main(int, char**) {
    signal(SIGPIPE, SIG_IGN);
    ProcessState::self()->setThreadPoolMaxThreadCount(6);

    // start the thread pool
    sp<ProcessState> ps(ProcessState::self());
    ps->startThreadPool();

    // instantiate performacemanager
    sp<PerformanceManager> performace = new PerformanceManager();
    // initialize before clients can connect
    performace->init();

    // publish performance
    sp<IServiceManager> sm(defaultServiceManager());
    sm->addService(String16(PerformanceManager::getServiceName()), performace, false);

    performace->run();
    while (1) {
        sleep(100000);
    }
    return 0;
}