#include "hostapdCallback.h"

namespace vendor {
namespace sprd {
namespace hardware {
namespace wifi {
namespace hostapd {
namespace V1_0 {
namespace implementation {

// Methods from ::vendor::sprd::hardware::wifi::hostapd::V1_0::IHostapdCallback follow.
Return<void> HostapdCallback::HostApEvents(const hidl_string& dataString) {
    // TODO implement
    return Void();
}


// Methods from ::android::hidl::base::V1_0::IBase follow.

//IHostapdCallback* HIDL_FETCH_IHostapdCallback(const char* /* name */) {
//    return new HostapdCallback();
//}

}  // namespace implementation
}  // namespace V1_0
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace sprd
}  // namespace vendor
