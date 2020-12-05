#include "SupplicantStaNetworkCallbackEx.h"

namespace vendor {
namespace sprd {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_1 {
namespace implementation {

// Methods from ::vendor::sprd::hardware::wifi::supplicant::V1_1::ISupplicantStaNetworkCallbackEx follow.
Return<void> SupplicantStaNetworkCallbackEx::onNetworkEventEx(const hidl_string& event) {
    // TODO implement
    return Void();
}


// Methods from ::android::hidl::base::V1_0::IBase follow.

//ISupplicantStaNetworkCallbackEx* HIDL_FETCH_ISupplicantStaNetworkCallbackEx(const char* /* name */) {
//    return new SupplicantStaNetworkCallbackEx();
//}

}  // namespace implementation
}  // namespace V1_1
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace sprd
}  // namespace vendor
