/*
 * hidl interface for wpa_supplicant daemon
 * Copyright (c) 2004-2016, Jouni Malinen <j@w1.fi>
 * Copyright (c) 2004-2016, Roshan Pius <rpius@google.com>
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#ifndef WPA_SUPPLICANT_HIDL_HIDL_MANAGER_H
#define WPA_SUPPLICANT_HIDL_HIDL_MANAGER_H

#include <map>
#include <string>


#include <vendor/sprd/hardware/wifi/hostapd/1.0/IHostapdCallback.h>

#include "hostapd.h"

extern "C" {
#include "utils/common.h"
#include "utils/includes.h"
#include "hostapd.h"
}

namespace vendor {
namespace sprd {
namespace hardware {
namespace wifi {
namespace hostapd {
namespace V1_0 {
namespace implementation {


using ::vendor::sprd::hardware::wifi::hostapd::V1_0::IHostapdCallback;
using ::vendor::sprd::hardware::wifi::hostapd::V1_0::implementation::Hostapd;


/**
 * HidlManager is responsible for managing the lifetime of all
 * hidl objects created by wpa_supplicant. This is a singleton
 * class which is created by the supplicant core and can be used
 * to get references to the hidl objects.
 */
class HidlManager
{
public:
	static HidlManager *getInstance();
	static void destroyInstance();
    struct hostapd_data *hapd_;
    sp<IHostapdCallback> callback;
	int registerInterface(struct hostapd_data *hapd);
	int unregisterInterface(struct hostapd_data *hapd);
    void HostApEvents(const char* data);
private:
	HidlManager() = default;
	~HidlManager() = default;
	HidlManager(const HidlManager &) = default;
	HidlManager &operator=(const HidlManager &) = default;


	// Singleton instance of this class.
	static HidlManager *instance_;
	// The main hidl service object.
	android::sp<Hostapd> hostapd_object_;


};

}  // namespace implementation
}  // namespace V1_0
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace sprd
}  // namespace vendor
#endif  // WPA_SUPPLICANT_HIDL_HIDL_MANAGER_H
