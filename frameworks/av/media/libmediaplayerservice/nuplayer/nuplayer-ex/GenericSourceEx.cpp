#include "GenericSource.h"

namespace android {

void NuPlayer::GenericSource::notifySeekDone(status_t err) {
    sp<AMessage> msg = dupNotify();
    msg->setInt32("what", kWhatSeekDone);
    msg->setInt32("err", err);
    msg->post();
}

bool NuPlayer::GenericSource::isSeeking() {
    Mutex::Autolock _l(mSeekingLock);
    if (mSeekingCount > 0) {
        ALOGV("seeking now, return wouldblock");
        return true;
    }
    return false;
}
} //namespace android