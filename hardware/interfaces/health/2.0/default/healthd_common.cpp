/*
 * Copyright (C) 2013 The Android Open Source Project
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

#define LOG_TAG "android.hardware.health@2.0-impl"
#define KLOG_LEVEL 6

#include <healthd/BatteryMonitor.h>
#include <healthd/healthd.h>

#include <batteryservice/BatteryService.h>
#include <cutils/klog.h>
#include <cutils/uevent.h>
#include <errno.h>
#include <libgen.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/epoll.h>
#include <sys/timerfd.h>
#include <unistd.h>
#include <utils/Errors.h>

#include <health2/Health.h>
#include <time.h>

using namespace android;

// Periodic chores fast interval in seconds
#define DEFAULT_PERIODIC_CHORES_INTERVAL_FAST (60 * 1)
// Periodic chores fast interval in seconds
// -----chnaged to 15 min for low power Feature BEG ------
#define DEFAULT_PERIODIC_CHORES_INTERVAL_SLOW (60 * 15)

static struct healthd_config healthd_config = {
    .periodic_chores_interval_fast = DEFAULT_PERIODIC_CHORES_INTERVAL_FAST,
    .periodic_chores_interval_slow = DEFAULT_PERIODIC_CHORES_INTERVAL_SLOW,
    .batteryStatusPath = String8(String8::kEmptyString),
    .batteryHealthPath = String8(String8::kEmptyString),
    .batteryPresentPath = String8(String8::kEmptyString),
    .batteryCapacityPath = String8(String8::kEmptyString),
    .batteryVoltagePath = String8(String8::kEmptyString),
    .batteryTemperaturePath = String8(String8::kEmptyString),
    .batteryTechnologyPath = String8(String8::kEmptyString),
    .batteryCurrentNowPath = String8(String8::kEmptyString),
    .batteryCurrentAvgPath = String8(String8::kEmptyString),
    .batteryChargeCounterPath = String8(String8::kEmptyString),
    .batteryFullChargePath = String8(String8::kEmptyString),
    .batteryCycleCountPath = String8(String8::kEmptyString),
    .energyCounter = NULL,
    .boot_min_cap = 0,
    .screen_on = NULL,
};

static int eventct;
static int epollfd;

#define POWER_SUPPLY_SUBSYSTEM "power_supply"

// epoll_create() parameter is actually unused
#define MAX_EPOLL_EVENTS 40
static int uevent_fd;
static int wakealarm_fd;

// -1 for no epoll timeout
static int awake_poll_interval = -1;

static int wakealarm_wake_interval = DEFAULT_PERIODIC_CHORES_INTERVAL_FAST;

using ::android::hardware::health::V2_0::implementation::Health;

struct healthd_mode_ops* healthd_mode_ops = nullptr;

int healthd_register_event(int fd, void (*handler)(uint32_t), EventWakeup wakeup) {
    struct epoll_event ev;

    ev.events = EPOLLIN;

    if (wakeup == EVENT_WAKEUP_FD) ev.events |= EPOLLWAKEUP;

    ev.data.ptr = (void*)handler;
    if (epoll_ctl(epollfd, EPOLL_CTL_ADD, fd, &ev) == -1) {
        KLOG_ERROR(LOG_TAG, "epoll_ctl failed; errno=%d\n", errno);
        return -1;
    }

    eventct++;
    return 0;
}

// NOTE: Bug #693427 low power Feature BEG-->
// to align the next start alarm time to (DEFAULT_PERIODIC_CHORES_INTERVAL_SLOW/60)mins(defalult 10min),
// and return the seconds to next start alarm time
// return -1 for fail
static int next_align_start_time() {
	time_t now;
	struct tm ptm;

	// DEFAULT_PERIODIC_CHORES_INTERVAL_SLOW should multi-times of 5 mins
	if (DEFAULT_PERIODIC_CHORES_INTERVAL_SLOW % (5 *60) !=0)
		return -1;
	time(&now);
	localtime_r(&now, &ptm);

	KLOG_INFO(LOG_TAG, "now:%ld, sec:%d, min:%d, hour:%d, day:%d\n",
		now, ptm.tm_sec, ptm.tm_min, ptm.tm_hour, ptm.tm_mday);

	int align_min = DEFAULT_PERIODIC_CHORES_INTERVAL_SLOW/60;
	if (align_min <= 0) align_min = 10;
	int count_per_hour = 60/align_min;
	if (count_per_hour <=0) count_per_hour = 1;

	int residue = ptm.tm_min /align_min;
	if (residue < (count_per_hour-1)) {
		ptm.tm_min = (residue+1)*align_min;
		ptm.tm_sec = 0;
	} else {
		ptm.tm_sec = 0;
		ptm.tm_min = 0;
		ptm.tm_hour += 1;
	}

	time_t next =  mktime(&ptm);
	KLOG_INFO(LOG_TAG, "next:%ld, sec:%d, min:%d, hour:%d, day:%d\n",
		next, ptm.tm_sec, ptm.tm_min, ptm.tm_hour, ptm.tm_mday);

	return (next - now);
}
// <-- NOTE: Bug #693427 low power Feature END


static void wakealarm_set_interval(int interval) {
    struct itimerspec itval;

    if (wakealarm_fd == -1) return;

    wakealarm_wake_interval = interval;

    if (interval == -1) interval = 0;

// NOTE: Bug #693427 low power Feature BEG-->
    int next = interval;
    if (DEFAULT_PERIODIC_CHORES_INTERVAL_SLOW == wakealarm_wake_interval) {
       next = next_align_start_time();
       if (next < 0) next = interval;
    }
// <-- NOTE: Bug #693427 low power Feature END

    itval.it_interval.tv_sec = interval;
    itval.it_interval.tv_nsec = 0;
    itval.it_value.tv_sec = next; //interval;
    itval.it_value.tv_nsec = 0;

    KLOG_INFO(LOG_TAG, "it_interval.tv_sec: %ld, it_value.tv_sec:%ld\n", itval.it_interval.tv_sec, itval.it_value.tv_sec);


    if (timerfd_settime(wakealarm_fd, 0, &itval, NULL) == -1)
        KLOG_ERROR(LOG_TAG, "wakealarm_set_interval: timerfd_settime failed\n");
}

void healthd_battery_update_internal(bool charger_online) {
    // Fast wake interval when on charger (watch for overheat);
    // slow wake interval when on battery (watch for drained battery).

    int new_wake_interval = charger_online ? healthd_config.periodic_chores_interval_fast
                                           : healthd_config.periodic_chores_interval_slow;

    if (new_wake_interval != wakealarm_wake_interval) wakealarm_set_interval(new_wake_interval);

    // During awake periods poll at fast rate.  If wake alarm is set at fast
    // rate then just use the alarm; if wake alarm is set at slow rate then
    // poll at fast rate while awake and let alarm wake up at slow rate when
    // asleep.

    if (healthd_config.periodic_chores_interval_fast == -1)
        awake_poll_interval = -1;
    else
        awake_poll_interval = new_wake_interval == healthd_config.periodic_chores_interval_fast
                                  ? -1
                                  : healthd_config.periodic_chores_interval_fast * 1000;
}

static void healthd_battery_update(void) {
    Health::getImplementation()->update();
}

static void periodic_chores() {
    healthd_battery_update();
}

#define UEVENT_MSG_LEN 2048
static void uevent_event(uint32_t /*epevents*/) {
    char msg[UEVENT_MSG_LEN + 2];
    char* cp;
    int n;

    n = uevent_kernel_multicast_recv(uevent_fd, msg, UEVENT_MSG_LEN);
    if (n <= 0) return;
    if (n >= UEVENT_MSG_LEN) /* overflow -- discard */
        return;

    msg[n] = '\0';
    msg[n + 1] = '\0';
    cp = msg;

    while (*cp) {
        if (!strcmp(cp, "SUBSYSTEM=" POWER_SUPPLY_SUBSYSTEM)) {
            healthd_battery_update();
            break;
        }

        /* advance to after the next \0 */
        while (*cp++)
            ;
    }
}

static void uevent_init(void) {
    uevent_fd = uevent_open_socket(64 * 1024, true);

    if (uevent_fd < 0) {
        KLOG_ERROR(LOG_TAG, "uevent_init: uevent_open_socket failed\n");
        return;
    }

    fcntl(uevent_fd, F_SETFL, O_NONBLOCK);
    if (healthd_register_event(uevent_fd, uevent_event, EVENT_WAKEUP_FD))
        KLOG_ERROR(LOG_TAG, "register for uevent events failed\n");
}

static void wakealarm_event(uint32_t /*epevents*/) {
    unsigned long long wakeups;

    if (read(wakealarm_fd, &wakeups, sizeof(wakeups)) == -1) {
        KLOG_ERROR(LOG_TAG, "wakealarm_event: read wakealarm fd failed\n");
        return;
    }

    periodic_chores();
}

static void wakealarm_init(void) {
    wakealarm_fd = timerfd_create(CLOCK_BOOTTIME_ALARM, TFD_NONBLOCK);
    if (wakealarm_fd == -1) {
        KLOG_ERROR(LOG_TAG, "wakealarm_init: timerfd_create failed\n");
        return;
    }

    if (healthd_register_event(wakealarm_fd, wakealarm_event, EVENT_WAKEUP_FD))
        KLOG_ERROR(LOG_TAG, "Registration of wakealarm event failed\n");

    wakealarm_set_interval(healthd_config.periodic_chores_interval_fast);
}

static void healthd_mainloop(void) {
    int nevents = 0;
    while (1) {
        struct epoll_event events[eventct];
        int timeout = awake_poll_interval;
        int mode_timeout;

        /* Don't wait for first timer timeout to run periodic chores */
        if (!nevents) periodic_chores();

        healthd_mode_ops->heartbeat();

        mode_timeout = healthd_mode_ops->preparetowait();
        if (timeout < 0 || (mode_timeout > 0 && mode_timeout < timeout)) timeout = mode_timeout;
        nevents = epoll_wait(epollfd, events, eventct, timeout);
        if (nevents == -1) {
            if (errno == EINTR) continue;
            KLOG_ERROR(LOG_TAG, "healthd_mainloop: epoll_wait failed\n");
            break;
        }

        for (int n = 0; n < nevents; ++n) {
            if (events[n].data.ptr) (*(void (*)(int))events[n].data.ptr)(events[n].events);
        }
    }

    return;
}

static int healthd_init() {
    epollfd = epoll_create(MAX_EPOLL_EVENTS);
    if (epollfd == -1) {
        KLOG_ERROR(LOG_TAG, "epoll_create failed; errno=%d\n", errno);
        return -1;
    }

    healthd_mode_ops->init(&healthd_config);
    wakealarm_init();
    uevent_init();

    return 0;
}

int healthd_main() {
    int ret;

    klog_set_level(KLOG_LEVEL);

    if (!healthd_mode_ops) {
        KLOG_ERROR("healthd ops not set, exiting\n");
        exit(1);
    }

    ret = healthd_init();
    if (ret) {
        KLOG_ERROR("Initialization failed, exiting\n");
        exit(2);
    }

    healthd_mainloop();
    KLOG_ERROR("Main loop terminated, exiting\n");
    return 3;
}
