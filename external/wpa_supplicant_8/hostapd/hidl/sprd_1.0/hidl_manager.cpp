/*
 * hidl interface for wpa_supplicant daemon
 * Copyright (c) 2004-2016, Jouni Malinen <j@w1.fi>
 * Copyright (c) 2004-2016, Roshan Pius <rpius@google.com>
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#include <algorithm>
#include <regex>

#include "hidl_manager.h"
extern "C" {
#include "hostapd.h"
}

namespace vendor {
namespace sprd {
namespace hardware {
namespace wifi {
namespace hostapd {
namespace V1_0 {
namespace implementation {

HidlManager *HidlManager::instance_ = NULL;

HidlManager *HidlManager::getInstance()
{
	if (!instance_)
		instance_ = new HidlManager();
	return instance_;
}

void HidlManager::destroyInstance()
{
	if (instance_)
		delete instance_;
	instance_ = NULL;
}

/**
 * Register an interface to hidl manager.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface.
 *
 * @return 0 on success, 1 on failure.
 */
int HidlManager::registerInterface(struct hostapd_data *hapd)
{
	if (!hapd)
		return 1;
	    wpa_printf(MSG_DEBUG,"registerInterface");
        hapd_ = hapd;
        callback = NULL;
	return 0;
}

/**
 * Unregister an interface from hidl manager.
 *
 * @param wpa_s |wpa_supplicant| struct corresponding to the interface.
 *
 * @return 0 on success, 1 on failure.
 */
int HidlManager::unregisterInterface(struct hostapd_data *hapd)
{
	if (!hapd)
		return 1;
        hapd_ = NULL;
        callback = NULL;
		return 0;
}

void HidlManager::HostApEvents(const char* data) {

    std::bind(&IHostapdCallback::HostApEvents, std::placeholders::_1, data)(callback);
}
}  // namespace implementation
}  // namespace V1_0
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace sprd
}  // namespace vendor
