#ifndef VENDOR_SPRD_HARDWARE_WIFI_SUPPLICANT_V1_1_SUPPLICANTSTANETWORKCALLBACKEX_H
#define VENDOR_SPRD_HARDWARE_WIFI_SUPPLICANT_V1_1_SUPPLICANTSTANETWORKCALLBACKEX_H

#include <vendor/sprd/hardware/wifi/supplicant/1.1/ISupplicantStaNetworkCallbackEx.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

namespace vendor {
namespace sprd {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_1 {
namespace implementation {

using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

struct SupplicantStaNetworkCallbackEx : public ISupplicantStaNetworkCallbackEx {
    // Methods from ::vendor::sprd::hardware::wifi::supplicant::V1_1::ISupplicantStaNetworkCallbackEx follow.
    Return<void> onNetworkEventEx(const hidl_string& event) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.

};

// FIXME: most likely delete, this is only for passthrough implementations
// extern "C" ISupplicantStaNetworkCallbackEx* HIDL_FETCH_ISupplicantStaNetworkCallbackEx(const char* name);

}  // namespace implementation
}  // namespace V1_1
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace sprd
}  // namespace vendor

#endif  // VENDOR_SPRD_HARDWARE_WIFI_SUPPLICANT_V1_1_SUPPLICANTSTANETWORKCALLBACKEX_H
