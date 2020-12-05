#include "RTSPSource.h"

namespace android {

void NuPlayer::RTSPSource::finishSeek(status_t err) {
    sp<AMessage> notify = dupNotify();
    notify->setInt32("what", kWhatSeekDone);
    notify->setInt32("result", err);
    notify->post();
    mIsSeeking = false;
}
} //namespace android