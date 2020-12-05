#ifndef VENDOR_SPRD_HARDWARE_WIFI_HOSTAPD_V1_0_HOSTAPDCALLBACK_H
#define VENDOR_SPRD_HARDWARE_WIFI_HOSTAPD_V1_0_HOSTAPDCALLBACK_H

#include <vendor/sprd/hardware/wifi/hostapd/1.0/IHostapdCallback.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

namespace vendor {
namespace sprd {
namespace hardware {
namespace wifi {
namespace hostapd {
namespace V1_0 {
namespace implementation {

using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

struct HostapdCallback : public IHostapdCallback {
    // Methods from ::vendor::sprd::hardware::wifi::hostapd::V1_0::IHostapdCallback follow.
    Return<void> HostApEvents(const hidl_string& dataString) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.

};

// FIXME: most likely delete, this is only for passthrough implementations
// extern "C" IHostapdCallback* HIDL_FETCH_IHostapdCallback(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace sprd
}  // namespace vendor

#endif  // VENDOR_SPRD_HARDWARE_WIFI_HOSTAPD_V1_0_HOSTAPDCALLBACK_H
