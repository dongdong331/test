#include "ARTSPConnection.h"

namespace android {

void ARTSPConnection::setTearDownFlag(bool isTearDown) {
    mIsTearDown = isTearDown;
}
} //namespace android