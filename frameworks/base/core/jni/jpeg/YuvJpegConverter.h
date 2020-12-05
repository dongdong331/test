/*
 * Copyright (C) 2012 The Android Open Source Project
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
#ifndef _YUV_JPEG_CONVERTER_H_
#define _YUV_JPEG_CONVERTER_H_
#include "jpeg_api.h"
#include "MemIon.h"

#define SPRD_ION_DEV "/dev/ion"
#define WORKING_STORAGE_CACHE_SIZE   (5<<20)

namespace android {

struct ion_buffer {
    sp<MemIon> pmem_heap;
    size_t pmem_size;
    unsigned long phy_addr ;
    unsigned char* vir_addr;
    uint32_t buffer_fd;
};

//sync with jpg_jpeg_ret in jpeg_codec.c
enum jpg_error_type {
    JPEG_CODEC_SUCCESS = 0,
    JPEG_CODEC_PARAM_ERR,
    JPEG_CODEC_INVALID_HANDLE,
    JPEG_CODEC_NO_MEM
};

//format sync with YuvImageEx.java
enum yuv_format {
    YUV_FORMAT_NV21 = 0x11,
    YUV_FORMAT_NV12 = 0x12,   //private defined in YuvImageEx
    YUV_FORMAT_YUY2 = 0x14    //currently jpeg codec library not support
};

typedef jpg_int (*JPEGInit)(JPEG_CODEC_CALLER_T*  oem_handle,jpg_evt_cb_ptr jpg_evt_cb);
typedef jpg_int (*JPEGDeinit)(JPEG_CODEC_CALLER_T*  oem_handle);
typedef jpg_int (*JPEGEncode)(JPEG_CODEC_CALLER_T* oem_handle, struct yuvbuf_frm *src, struct yuvbuf_frm *dst, struct jpg_op_mean *mean, jpeg_enc_cb_param *out_enc_param);
typedef jpg_int (*JPEGDecode)(JPEG_CODEC_CALLER_T*  oem_handle,struct yuvbuf_frm *src, struct yuvbuf_frm *dst, struct jpg_op_mean *mean);
typedef jpg_int (*JPEGGetIommuStatus)(JPEG_CODEC_CALLER_T*  oem_handle);
typedef jpg_int (*JPEGDecGetResolution)(unsigned char* jpg_src, int jpg_size,  unsigned int *wdith, unsigned int *height, unsigned int *yuv_buffer_size);

class SprdJpegLibManager {
public:
    SprdJpegLibManager();
    virtual ~SprdJpegLibManager();

protected:
    void *mLibHandle;
    JPEGInit mJPEGInit;
    JPEGDeinit mJPEGDeinit;
    JPEGEncode mJPEGEncode;
    JPEGDecode mJPEGDecode;
    JPEGGetIommuStatus mJPEGGetIommuStatus;
    JPEGDecGetResolution mJPEGDecGetResolution;

    bool openJpegLib(const char* libName);
    void closeJpegLib();

    //allocate ion buffer for hw jpeg lib
    sp<MemIon> allocateBuffer(int bufferSize, bool ioMmuEnabled, uint32_t flags, uint8_t *&v_addr, uint32_t *buffer_fd);
};

class SprdJpegDecoder : public SprdJpegLibManager {
public:
    SprdJpegDecoder(JNIEnv* env, jbyte* jpegData, jobject jpegInputStream, jint jpegSize, jint format);
    virtual ~SprdJpegDecoder();
    jobject decode(JNIEnv* env);
    bool initCheck() const { return mInitCheck; }

private:
    bool mInitCheck;
    jbyte* mJpegData;
    jobject mJpegInputStream;
    int mJpegSize;
    uint32_t mWidth;
    uint32_t mHeight;
    struct ion_buffer mInput_jpg_buffer;
    struct ion_buffer mOutput_y_buffer;
    JPEG_CODEC_CALLER_T* mOem_handle;
    bool mIoMmuEnabled;
    uint32_t mYuvSize;
    int mYuvFormat;

    void init(JNIEnv* env);
    bool jpegDecStart(yuvbuf_frm* dst);
    bool allocInputBuffer();
    bool allocOutputBuffer();
};

class SprdJpegEncoder : public SprdJpegLibManager {
public:
    SprdJpegEncoder(
            jbyte* yuv,
            jint yuvSize,
            jint format,
            jint width,
            jint height,
            jint jpegQuality,
            jint rotation);
    virtual ~SprdJpegEncoder();
    bool encode(JNIEnv* env, jobject jstream);
    bool initCheck() const { return mInitCheck; }

private:
    bool mInitCheck;
    jbyte* mYuvData;
    int mYuvSize;
    uint32_t mYuvFormat;
    uint32_t mWidth;
    uint32_t mHeight;
    uint32_t mQualityLevel;
    uint32_t mRotation;
    JPEG_CODEC_CALLER_T* mOem_handle;
    bool mIoMmuEnabled;
    struct ion_buffer mInput_y_buffer;
    struct ion_buffer mOutput_jpg_buffer;

    void init();
    bool allocEncBuffers();
    bool jpegEncStart(yuvbuf_frm* dst, jpeg_enc_cb_param *out_enc_param);
};

}

#endif
