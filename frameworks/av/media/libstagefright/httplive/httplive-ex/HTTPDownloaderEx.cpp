#include "HTTPDownloader.h"
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ALooper.h>
#include <cutils/properties.h>

namespace android {

void HTTPDownloader::init() {
    mDumpPlaylist = false;
    char value[PROPERTY_VALUE_MAX];
    property_get("hls.playlist.dump", value, "0");
    mDumpPlaylist = !strcmp(value, "1") || !strcasecmp(value, "true");
}

void HTTPDownloader::dumpPlaylist(sp<ABuffer> &buffer) {
    time_t now = time(NULL);
    char formattedTime[64] = {'\0'};
    strftime(formattedTime, sizeof(formattedTime), "%m-%d_%H-%M-%S", localtime(&now));

    char filePath[128] = {'\0'};
    sprintf(filePath, "/data/misc/media/hls_playlist_%lld_%s.m3u8", (long long)ALooper::GetNowUs(), formattedTime);
    FILE* fp = fopen(filePath, "ab");
    ALOGI("dumpPlaylist(%p) filePath: %s, fp %s NULL", this, filePath, (fp != NULL)? "is not": "is");
    if (fp != NULL) {
        if (buffer != NULL && buffer->size() > 0) {
            fwrite(buffer->data(), 1, buffer->size(), fp);
        }
        fclose(fp);
    }
}
} //namespace android