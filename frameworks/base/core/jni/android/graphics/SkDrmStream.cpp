#include "SkDrmStream.h"
#include <unistd.h>
#include <DrmManagerClientImpl.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

using android::sp;
using android::DecryptHandle;

SkDrmStream::SkDrmStream(int uniqueId, DrmManagerClientImpl* client, DecryptHandleWrapper* handleWrapper)
    :mOffset(0),mEof(false)
{
    mClient = client;
    mUniqueId = uniqueId;
    mHandleWrapper = handleWrapper;
}

SkDrmStream::~SkDrmStream() {
}

bool SkDrmStream::rewind() {
    mOffset = 0;
    mEof = false;
    return true;
}

size_t SkDrmStream::read(void* buffer, size_t size) {
    if (size == 0) {
        return 0;
    }
    // allocate a tmpBuffer, because DrmManagerClientImpl will refuse
    // to decode if buffer is NULL, while skia will use a nulled
    // buffer if decoding is just used to get the bitmap rect.
    int tmpBuffer = 0;
    if (buffer == NULL) {
        tmpBuffer = 1;
        buffer = (void *)malloc(size);
    }
    size_t ret = mClient->pread(mUniqueId, mHandleWrapper->handle, buffer, size, mOffset);
    if (tmpBuffer == 1) {
        free(buffer);
    }
    SkDebugf("skdrmstream read: size: %d offset: %d ret: %d\n", (int)size, (int)mOffset, (int)ret);
    mOffset += ret;
        if (ret <= 0) {
            mEof = true;
        }
    return ret;
}
