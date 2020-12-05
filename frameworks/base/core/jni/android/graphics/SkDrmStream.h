/*
 * Copyright 2006 The Android Open Source Project
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef SkDrmStream_DEFINED
#define SkDrmStream_DEFINED

#include "SkData.h"
#include "SkRefCnt.h"
#include "SkScalar.h"
#include "SkStream.h"

#include <memory.h>

namespace android {
    class DrmManagerClientImpl;
    class DecryptHandleWrapper;
};

using android::DrmManagerClientImpl;
using android::DecryptHandleWrapper;

////////////////////////////////////////////////////////////////////////////////////////

#include <stdio.h>

class SkDrmStream : public SkStreamRewindable {
    int mUniqueId;
    off64_t mOffset;
    DrmManagerClientImpl* mClient;
    DecryptHandleWrapper* mHandleWrapper;
    bool mEof;
public:
    SkDrmStream(int uniqueId, DrmManagerClientImpl*, DecryptHandleWrapper*);
    virtual ~SkDrmStream();
    virtual bool rewind() override ;
    virtual size_t read(void* buffer, size_t size) override ;
    // virtual const char* getFileName() override { return NULL; }
    virtual bool isAtEnd() const override { return mEof; }
private:
    SkStreamRewindable* onDuplicate() const override { return NULL; }
};


#endif
