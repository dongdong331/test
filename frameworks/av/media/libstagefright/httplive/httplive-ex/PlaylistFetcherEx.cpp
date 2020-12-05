#include "PlaylistFetcher.h"
#include <cutils/properties.h>
namespace android {

void PlaylistFetcher::init() {
    mDumpTsEnabled = false;
    mFileDump = NULL;
    char value[PROPERTY_VALUE_MAX];
    property_get("hls.ts.dump", value, "0");
    mDumpTsEnabled = !strcmp(value, "1") || !strcasecmp(value, "true");
    if (mDumpTsEnabled) {
        time_t now = time(NULL);
        char formattedTime[64] = {'\0'};
        strftime(formattedTime, sizeof(formattedTime), "%m-%d_%H-%M-%S", localtime(&now));
        char filePath[128] = {'\0'};
        sprintf(filePath, "/data/misc/media/hls_stream_%lld_%s.ts", (long long)ALooper::GetNowUs(), formattedTime);
        mFileDump = fopen(filePath, "ab");
        ALOGI("PlaylistFetcher(%p) open dump file: %s, and mFileDump %s NULL",
                    this, filePath, (mFileDump != NULL)? "is not": "is");
    }
}

void PlaylistFetcher::deinit() {
    if (mFileDump != NULL) {
        fclose(mFileDump);
        mFileDump = NULL;
        ALOGI("~PlaylistFetcher(%p) close dump file", this);
    }
}
} //namespace android