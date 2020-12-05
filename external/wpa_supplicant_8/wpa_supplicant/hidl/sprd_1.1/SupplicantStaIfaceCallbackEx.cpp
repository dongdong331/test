#include "SupplicantStaIfaceCallbackEx.h"

namespace vendor {
namespace sprd {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_1 {
namespace implementation {

// Methods from ::vendor::sprd::hardware::wifi::supplicant::V1_1::ISupplicantStaIfaceCallbackEx follow.
Return<void> SupplicantStaIfaceCallbackEx::onEventEx(const hidl_string& event) {
    // TODO implement
    return Void();
}


// Methods from ::android::hidl::base::V1_0::IBase follow.

//ISupplicantStaIfaceCallbackEx* HIDL_FETCH_ISupplicantStaIfaceCallbackEx(const char* /* name */) {
//    return new SupplicantStaIfaceCallbackEx();
//}

}  // namespace implementation
}  // namespace V1_1
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace sprd
}  // namespace vendor
