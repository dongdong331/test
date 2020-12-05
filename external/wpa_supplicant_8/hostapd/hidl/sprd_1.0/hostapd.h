/*
 * hidl interface for wpa_hostapd daemon
 * Copyright (c) 2004-2018, Jouni Malinen <j@w1.fi>
 * Copyright (c) 2004-2018, Roshan Pius <rpius@google.com>
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#ifndef HOSTAPD_HIDL_SUPPLICANT_H
#define HOSTAPD_HIDL_SUPPLICANT_H
#define VENDOR_SPRD_HARDWARE_WIFI_HOSTAPD_V1_0_HOSTAPD_H

#include <string>

#include <android-base/macros.h>

//#include <android/hardware/wifi/hostapd/1.0/IHostapd.h>

#include <vendor/sprd/hardware/wifi/hostapd/1.0/IHostapd.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

extern "C"
{
#include "utils/common.h"
#include "utils/includes.h"
#include "utils/wpa_debug.h"
#include "ap/hostapd.h"
#include "ctrl_iface.h"
#include "ap/wps_hostapd.h"
}
namespace vendor {
namespace sprd {
namespace hardware {
namespace wifi {
namespace hostapd {
namespace V1_0 {
namespace implementation {

using ::android::hardware::wifi::hostapd::V1_0::HostapdStatus;
using ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode;
using vendor::sprd::hardware::wifi::hostapd::V1_0::IHostapd;
using vendor::sprd::hardware::wifi::hostapd::V1_0::IHostapdCallback;

using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

/**
 * Implementation of the hostapd hidl object. This hidl
 * object is used core for global control operations on
 * hostapd.
 */
class Hostapd : public V1_0::IHostapd
{
public:
	Hostapd(hapd_interfaces* interfaces);
	~Hostapd() override = default;

	// Hidl methods exposed.
	Return<void> addAccessPoint(
	    const IfaceParams& iface_params, const NetworkParams& nw_params,
	    addAccessPoint_cb _hidl_cb) override;
	Return<void> addAccessPointEx(
	    const IfaceParams& iface_params, const NetworkParamsEx& nw_params,
	    addAccessPoint_cb _hidl_cb) override;
	Return<void> removeAccessPoint(
	    const hidl_string& iface_name,
	    removeAccessPoint_cb _hidl_cb) override;
	Return<void> terminate() override;

       // Methods from ::vendor::sprd::hardware::wifi::hostapd::V1_0::IHostapd follow.
	Return<bool> registerCallback(const IfaceParams& iface_params,
	    const sp<IHostapdCallback>& callback) override;
	Return<bool> doHostapdBooleanCommand(const IfaceParams& iface_params,
	    const hidl_string& type) override;
	Return<uint32_t> doHostapdIntCommand(const IfaceParams& iface_params,
	    const hidl_string& type) override;
	Return<void> doHostapdStringCommand(const IfaceParams& iface_params,
	    const hidl_string& type, doHostapdStringCommand_cb _hidl_cb) override;
private:
	// Corresponding worker functions for the HIDL methods.
	HostapdStatus addAccessPointInternal(
	    const IfaceParams& iface_params,const NetworkParams& nw_params);
	HostapdStatus addAccessPointInternalEx(
	    const IfaceParams& iface_params,const NetworkParamsEx& nw_params);

	HostapdStatus removeAccessPointInternal(const std::string& iface_name);
	
	std::string doHostapdStringCommandInternal(const IfaceParams& iface_params,
				const hidl_string& type);

	// Raw pointer to the global structure maintained by the core.
	struct hapd_interfaces* interfaces_;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    DISALLOW_COPY_AND_ASSIGN(Hostapd);
};

// FIXME: most likely delete, this is only for passthrough implementations
// extern "C" IHostapd* HIDL_FETCH_IHostapd(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace sprd
}  // namespace vendor

#endif  // VENDOR_SPRD_HARDWARE_WIFI_HOSTAPD_V1_0_HOSTAPD_H
