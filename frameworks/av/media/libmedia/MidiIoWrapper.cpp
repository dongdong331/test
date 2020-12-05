/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "MidiIoWrapper"
#include <utils/Log.h>
#include <utils/RefBase.h>

#include <sys/stat.h>
#include <fcntl.h>

#include <media/MidiIoWrapper.h>

static int readAt(void *handle, void *buffer, int pos, int size) {
    return ((android::MidiIoWrapper*)handle)->readAt(buffer, pos, size);
}
static int size(void *handle) {
    return ((android::MidiIoWrapper*)handle)->size();
}

namespace android {
int MidiIoWrapper::mCachUseSize = 0;
Mutex MidiIoWrapper::mCacheLock;

MidiIoWrapper::MidiIoWrapper(const char *path) {
    ALOGV("MidiIoWrapper(%s)", path);
    mFd = open(path, O_RDONLY | O_LARGEFILE);
    mBase = 0;
    mLength = lseek(mFd, 0, SEEK_END);
    mDataSource = nullptr;
    mCacheBuffer = NULL;
    mBufferSize = 0;
}

MidiIoWrapper::MidiIoWrapper(int fd, off64_t offset, int64_t size) {
    ALOGV("MidiIoWrapper(fd=%d)", fd);
    mFd = fd < 0 ? -1 : dup(fd);
    mBase = offset;
    mLength = size;
    mDataSource = nullptr;
    mCacheBuffer = NULL;
    mBufferSize = 0;
}

MidiIoWrapper::MidiIoWrapper(DataSourceBase *source) {
    ALOGV("MidiIoWrapper(DataSource)");
    mFd = -1;
    mDataSource = source;
    off64_t l;
    if (mDataSource->getSize(&l) == OK) {
        mLength = l;
    } else {
        mLength = 0;
    }
    mCacheBuffer = NULL;
    mBufferSize = 0;
}

MidiIoWrapper::~MidiIoWrapper() {
    ALOGV("~MidiIoWrapper");
    if (mFd >= 0) {
        close(mFd);
    }

    if (NULL != mCacheBuffer) {
        delete [] mCacheBuffer;
        mCacheBuffer = NULL;
        {
            Mutex::Autolock _l(mCacheLock);
            mCachUseSize -= mLength;
        }
    }
}

int MidiIoWrapper::readAt(void *buffer, int offset, int size) {
    ALOGV("readAt(%p, %d, %d)", buffer, offset, size);
    int CACHE_MAX_SIZE = 1024 * 1024 + 512 * 1024;
    int sizes = 0;
    int64_t read_sizes = mLength;
    int64_t available_read = 0;
    int midiSize = mLength;

    if (mCacheBuffer == NULL) {
        Mutex::Autolock _l(mCacheLock);
        if (mCachUseSize + midiSize <= CACHE_MAX_SIZE) {
            mCacheBuffer = new unsigned char[midiSize];
            if (NULL != mCacheBuffer) {
                mCachUseSize += midiSize;
                ALOGV("mCachUseSize : %d", mCachUseSize);
            }
        } else {
            ALOGV("Cache is not enough");
        }
    }

    if (mCacheBuffer != NULL && mBufferSize > 0 && mBufferSize >= (offset + size)) {
        /* Use buffered data */
        memcpy(buffer, (void*)(mCacheBuffer + offset), size);
        return size;
    } else if (mCacheBuffer != NULL && mBufferSize <= midiSize) {
        /* Buffer new data */
        available_read = midiSize - mBufferSize;
        read_sizes = (read_sizes < available_read) ? read_sizes : available_read;
        sizes = readAt_l(mCacheBuffer + mBufferSize, mBufferSize, read_sizes);
        if(sizes > 0) {
            mBufferSize += sizes;
            if(offset < mBufferSize) {
                if (mBufferSize > offset + size) {
                    memcpy(buffer, (void*)(mCacheBuffer + offset), size);
                    return size;
                } else {
                    memcpy(buffer, (void*)(mCacheBuffer + offset), mBufferSize-offset);
                    return mBufferSize-offset;
                }
            } else {
                return 0;
            }
        } else {
            return sizes;
        }
    } else {
        return readAt_l(buffer, offset, size);
    }
}

int MidiIoWrapper::readAt_l(void *buffer, int offset, int size) {
    ALOGV("readAt(%p, %d, %d)", buffer, offset, size);
    if (mDataSource != NULL) {
        return mDataSource->readAt(offset, buffer, size);
    }
    if (mFd < 0) {
        errno = EBADF;
        return -1; // as per failed read.
    }
    lseek(mFd, mBase + offset, SEEK_SET);
    if (offset + size > mLength) {
        size = mLength - offset;
    }
    return read(mFd, buffer, size);
}

int MidiIoWrapper::size() {
    ALOGV("size() = %d", int(mLength));
    return mLength;
}

EAS_FILE_LOCATOR MidiIoWrapper::getLocator() {
    mEasFile.handle = this;
    mEasFile.readAt = ::readAt;
    mEasFile.size = ::size;
    return &mEasFile;
}

}  // namespace android
