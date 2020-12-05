/*
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

//#define LOG_NDEBUG 0
#define LOG_TAG "YuvJpegConverter-JNI"
#include "utils/Log.h"
#include "core_jni_helpers.h"
#include <jni.h>
#include "sprd_ion.h"
#include "SkStream.h"
#include "CreateJavaOutputStreamAdaptor.h"
#include <utils/Log.h>
#include "YuvJpegConverter.h"
#include <dlfcn.h>

static jclass   gYuvImageEx_class;
static jmethodID gYuvImageEx_constructorMethodID;

using namespace android;

static bool haveException(JNIEnv *env) {
    if (env->ExceptionCheck() != 0) {
        ALOGE("*** Exception returned from Java call!\n");
        env->ExceptionDescribe();
        env->ExceptionClear();
        return true;
    }
    return false;
}

static void jpegWriteOutputStream(
            JNIEnv* env, jobject outputStream, int size, uint8_t *buffer) {
    jbyteArray byteArray = NULL;
    int byteArraySize = size;
    SkWStream* strm = NULL;
    if (size == 0 || buffer == NULL) {
        ALOGE("jpegWriteOutputStream invalid parameter, size: %d", size);
        return;
    }
    if (size > WORKING_STORAGE_CACHE_SIZE) {
        byteArraySize = WORKING_STORAGE_CACHE_SIZE;
    }
    byteArray = env->NewByteArray(byteArraySize);
    if (haveException(env) || byteArray == NULL) {
        ALOGE("jpegWriteOutputStream Couldn't allocate byte array for jpeg data");
        if (byteArray != NULL) {
            env->DeleteLocalRef(byteArray);
        }
        return;
    }

    strm = CreateJavaOutputStreamAdaptor(env, outputStream, byteArray);
    if (strm != NULL) {
        bool writeSuceed = strm->write(buffer, size);
        if (!writeSuceed) {
            ALOGE("write to output stream fail!");
        }
        strm->flush();
        delete strm;
    } else {
        ALOGE("create output stream adaptor fail!");
    }
    env->DeleteLocalRef(byteArray);
}

static bool jpegReadInputStream(JNIEnv* env, jobject inputStream, int size, uint8_t *buffer) {
    jbyteArray byteArray = NULL;
    int byteArraySize = size;
    SkStream* strm = NULL;
    if (size > WORKING_STORAGE_CACHE_SIZE) {
        byteArraySize = WORKING_STORAGE_CACHE_SIZE;
    }
    byteArray = env->NewByteArray(byteArraySize);
    if (byteArray == NULL) {
        ALOGE("jpegReadInputStream Couldn't allocate byte array for yuv data");
        return false;
    }

    strm = CreateJavaInputStreamAdaptor(env, inputStream, byteArray);
    if (strm != NULL) {
        strm->read(buffer, size);
        delete strm;
    } else {
        ALOGE("jpegReadInputStream input stream adaptor is NULL");
        env->DeleteLocalRef(byteArray);
        return false;
    }
    env->DeleteLocalRef(byteArray);
    return true;
}

static jobject jpegCreateYuvImage(JNIEnv* env, jbyteArray yuvData,
       int format, int width, int height, jintArray strides) {
    jobject obj = env->NewObject(gYuvImageEx_class, gYuvImageEx_constructorMethodID,
            yuvData, format, width, height, strides);

    if (haveException(env)) {
        ALOGE("jpegCreateYuvImage fail, width: %d, height: %d", width, height);
        if (obj != NULL) {
            env->DeleteLocalRef(obj);
        }
        return NULL;
    }
    return obj;
}

///////////////////////////////////////////////////////////////////////////////
SprdJpegLibManager::SprdJpegLibManager()
    : mLibHandle(NULL),
    mJPEGInit(NULL),
    mJPEGDeinit(NULL),
    mJPEGEncode(NULL),
    mJPEGDecode(NULL),
    mJPEGGetIommuStatus(NULL),
    mJPEGDecGetResolution(NULL) {
}

SprdJpegLibManager::~SprdJpegLibManager() {
    closeJpegLib();
}

bool SprdJpegLibManager::openJpegLib(const char* libName) {
    if(mLibHandle) {
        dlclose(mLibHandle);
    }

    ALOGV("openJpegLib, lib: %s", libName);

    mLibHandle = dlopen(libName, RTLD_NOW);
    if(mLibHandle == NULL) {
        ALOGE("openJpegLib, can't open lib: %s",libName);
        return false;
    }

    mJPEGInit = (JPEGInit)dlsym(mLibHandle, "sprd_jpeg_init");
    if(mJPEGInit == NULL) {
        ALOGE("Can't find sprd_jpeg_init in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mJPEGDeinit = (JPEGDeinit)dlsym(mLibHandle, "sprd_jpeg_deinit");
    if(mJPEGDeinit == NULL) {
        ALOGE("Can't find sprd_jpeg_deinit in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mJPEGEncode = (JPEGEncode)dlsym(mLibHandle, "sprd_jpg_encode");
    if(mJPEGEncode == NULL) {
        ALOGE("Can't find sprd_jpg_encode in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mJPEGDecode = (JPEGDecode)dlsym(mLibHandle, "sprd_jpg_decode");
    if(mJPEGDecode == NULL) {
        ALOGE("Can't find sprd_jpg_decode in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mJPEGGetIommuStatus = (JPEGGetIommuStatus)dlsym(mLibHandle, "sprd_jpg_get_Iommu_status");
    if(mJPEGGetIommuStatus == NULL) {
        ALOGE("Can't find sprd_jpg_get_Iommu_status in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mJPEGDecGetResolution = (JPEGDecGetResolution)dlsym(mLibHandle, "sprd_jpg_dec_get_resolution");
    if(mJPEGDecGetResolution == NULL) {
        ALOGE("Can't find sprd_jpg_dec_get_resolution in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }
    return true;
}

void SprdJpegLibManager::closeJpegLib() {
    if (mLibHandle) {
        dlclose(mLibHandle);
        mLibHandle = NULL;
    }
}

sp<MemIon> SprdJpegLibManager::allocateBuffer(int bufferSize, bool ioMmuEnabled, uint32_t flags,
            uint8_t *&v_addr, uint32_t *buffer_fd) {
    sp<MemIon> pMemHeap = NULL;
    unsigned int memoryType = (1<<31) | ION_HEAP_ID_MASK_SYSTEM;
    if (flags == MemIon::NO_CACHING) {
        memoryType = ION_HEAP_ID_MASK_SYSTEM;
    }
    if(ioMmuEnabled) {
        pMemHeap = new MemIon(SPRD_ION_DEV, bufferSize, flags, memoryType);
    } else {
        pMemHeap = new MemIon(SPRD_ION_DEV, bufferSize, flags, memoryType);
    }

    if (pMemHeap == NULL) {
        ALOGE("Failed to create MemIon!");
        return NULL;
    }

    int fd = pMemHeap->getHeapID();
    if (fd < 0) {
        ALOGE("Failed to alloc buffer, size: %d, fd: %d", bufferSize, fd);
        pMemHeap.clear();
        return NULL;
    }
    *buffer_fd = (uint32_t)fd;

    v_addr = (uint8_t *)(pMemHeap->getBase());
    if (v_addr == NULL) {
        ALOGE("allocateBuffer fail, virtual address is NULL");
        pMemHeap.clear();
        return NULL;
    }

    return pMemHeap;
}

///////////////////////////////////////////////////////////////////////////////
SprdJpegEncoder::SprdJpegEncoder(
        jbyte* yuv,
        jint yuvSize,
        jint format,
        jint width,
        jint height,
        jint jpegQuality,
        jint rotation)
        : mInitCheck(false),
        mYuvData(yuv),
        mYuvSize(yuvSize),
        mYuvFormat(format),
        mWidth(width),
        mHeight(height),
        mQualityLevel(jpegQuality),
        mRotation(rotation),
        mIoMmuEnabled(false) {
    mOem_handle = (struct jpeg_codec_caller_handle*)malloc(sizeof(struct jpeg_codec_caller_handle));
    if (mOem_handle != NULL) {
        memset(mOem_handle, 0, sizeof(struct jpeg_codec_caller_handle));
    }
    memset(&mInput_y_buffer, 0, sizeof(struct ion_buffer));
    memset(&mOutput_jpg_buffer, 0, sizeof(struct ion_buffer));
    init();
}

SprdJpegEncoder::~SprdJpegEncoder() {
    if (mOem_handle != NULL) {
        if (mJPEGDeinit != NULL) {
            (*mJPEGDeinit)(mOem_handle);
        }
        free(mOem_handle);
    }
    if (mInput_y_buffer.pmem_heap != NULL) {
        mInput_y_buffer.pmem_heap.clear();
        mInput_y_buffer.pmem_heap = NULL;
    }
    if (mOutput_jpg_buffer.pmem_heap != NULL) {
        mOutput_jpg_buffer.pmem_heap.clear();
        mOutput_jpg_buffer.pmem_heap = NULL;
    }
    closeJpegLib();
}

void SprdJpegEncoder::init() {
    bool ret = openJpegLib("libjpeg_hw_sprd.so");
    if (!ret) {
        mInitCheck = false;
        ALOGE("SprdJpegEncoder::init() open jpeg lib fail!");
        return;
    }

    if (JPEG_CODEC_SUCCESS != (*mJPEGInit)(mOem_handle, NULL)) {
        mInitCheck = false;
        ALOGE("SprdJpegEncoder::init() sprd_jpeg_init fail!");
        return;
    }
    mIoMmuEnabled = (*mJPEGGetIommuStatus)(mOem_handle) == 0 ? true : false;

    //allocate input yuv buffer and output jpeg buffer
    if (!allocEncBuffers()) {
        mInitCheck = false;
        return;
    } else {
        //copy yuv data to input buffer
        memcpy(mInput_y_buffer.vir_addr, mYuvData, mYuvSize);
    }
    mInitCheck = true;
}

bool SprdJpegEncoder::allocEncBuffers() {
    int jpgBufferSize = (mWidth * mHeight * 3) / 2;
    int yuvBufferSize = ((mWidth + 15) & (~15)) * ((mHeight + 15) & (~15)) * 3 / 2;
    if (mYuvSize == 0 || jpgBufferSize == 0 || mYuvSize > yuvBufferSize) {
        ALOGE("allocEncBuffers() size exception, mYuvSize: %d, jpgBufferSize: %d, yuvBufferSize: %d", mYuvSize, jpgBufferSize, yuvBufferSize);
        return false;
    }

    //allocate yuv ion buffer
    uint32_t flags = 0;
    if ((mWidth % 16 != 0) || (mHeight % 16 != 0)) {
        flags = MemIon::NO_CACHING;
    }
    sp<MemIon> pYuvMem = allocateBuffer(yuvBufferSize, mIoMmuEnabled, flags, mInput_y_buffer.vir_addr, &(mInput_y_buffer.buffer_fd));
    mInput_y_buffer.pmem_heap = pYuvMem;
    if (pYuvMem != NULL) {
        mInput_y_buffer.pmem_size = yuvBufferSize;
        if (!mIoMmuEnabled) {
            pYuvMem->get_phy_addr_from_ion(&(mInput_y_buffer.phy_addr), &(mInput_y_buffer.pmem_size));
        }
    } else {
        ALOGE("allocEncBuffers() allocate yuv buffer fail, yuvBufferSize: %d", yuvBufferSize);
        return false;
    }

    //allocate jpeg ion buffer
    sp<MemIon> pJpgMem = allocateBuffer(jpgBufferSize, mIoMmuEnabled, 0, mOutput_jpg_buffer.vir_addr, &(mOutput_jpg_buffer.buffer_fd));
    mOutput_jpg_buffer.pmem_heap = pJpgMem;
    if (pJpgMem != NULL) {
        mOutput_jpg_buffer.pmem_size = jpgBufferSize;
        if (!mIoMmuEnabled) {
            pJpgMem->get_phy_addr_from_ion(&(mOutput_jpg_buffer.phy_addr), &(mOutput_jpg_buffer.pmem_size));
        }
    } else {
        ALOGE("allocEncBuffers() allocate jpg buffer fail, size: %d", jpgBufferSize);
        return false;
    }
    return true;
}

bool SprdJpegEncoder::encode(JNIEnv* env, jobject jstream) {
    struct yuvbuf_frm *dst =(struct yuvbuf_frm*)malloc(sizeof(struct yuvbuf_frm));
    if (dst == NULL) {
        return false;
    } else {
        memset(dst, 0, sizeof(struct yuvbuf_frm));
    }

    struct jpeg_enc_cb_param *out_jpeg_enc_param = (struct jpeg_enc_cb_param*)malloc(sizeof(struct jpeg_enc_cb_param));
    if (out_jpeg_enc_param == NULL) {
        free(dst);
        return false;
    } else {
        memset(out_jpeg_enc_param, 0, sizeof(struct jpeg_enc_cb_param));
    }

    //begin to encode
    if (!jpegEncStart(dst, out_jpeg_enc_param)) {
        free(dst);
        free(out_jpeg_enc_param);
        return false;
    }

    //write the jpeg data to outputstream
    jpegWriteOutputStream(env, jstream, out_jpeg_enc_param->stream_size, (uint8_t *)dst->addr_vir.addr_y);
    free(dst);
    free(out_jpeg_enc_param);
    return true;
}

bool SprdJpegEncoder::jpegEncStart(yuvbuf_frm* dst, jpeg_enc_cb_param *out_enc_param) {
    struct yuvbuf_frm *src = NULL;
    struct jpg_op_mean *mean = NULL;
    bool result = true;
    int ret = -1;
    if (dst == NULL) {
        return false;
    }

    mean = (struct jpg_op_mean*)malloc(sizeof(struct jpg_op_mean));
    if (mean == NULL) {
        return false;
    }
    memset(mean, 0, sizeof(struct jpg_op_mean));
    mean->slice_mode = 1;
    mean->quality_level = mQualityLevel;
    mean->is_sync = 1;
    if (mRotation == 90) {
        mean->rotation = 1;
    } else  if (mRotation == 180) {
        mean->mirror = 1;
        mean->flip = 1;
    }  else  if (mRotation == 270) {
        mean->rotation = 1;
        mean->mirror = 1;
        mean->flip = 1;
    }

    src = (struct yuvbuf_frm*)malloc(sizeof(struct yuvbuf_frm));
    if (src == NULL) {
        free(mean);
        return false;
    }
    memset(src, 0, sizeof(struct yuvbuf_frm));
    src->size.width = mWidth;
    src->size.height = mHeight;
    src->fd = mInput_y_buffer.buffer_fd;
    src->addr_vir.addr_y=(jpg_uint)(mInput_y_buffer.vir_addr);
    //compatible with the usage of camera hal
    if (mIoMmuEnabled) {
        src->addr_phy.addr_y = 0;  //treat addr_y and addr_u as offset when ioMmuEnabled is true
        src->addr_phy.addr_u = src->size.width * src->size.height;
    } else {
        src->addr_phy.addr_y = mInput_y_buffer.phy_addr;
    }
    src->buf_size = mInput_y_buffer.pmem_size;
    src->data_end.y_endian = 1;
    if (mYuvFormat == YUV_FORMAT_NV21) {
        src->data_end.uv_endian= 2;  //yuv NV21
    } else {
        src->data_end.uv_endian= 1;  //yuv NV12
    }
    MemIon::Invalid_ion_buffer(src->fd);

    if (mean->rotation == 1) {
        dst->size.width = mHeight;
        dst->size.height = mWidth;
    } else {
        dst->size.width = mWidth;
        dst->size.height = mHeight;
    }
    dst->fd = mOutput_jpg_buffer.buffer_fd;
    dst->addr_vir.addr_y =(jpg_uint)(mOutput_jpg_buffer.vir_addr);
    dst->addr_phy.addr_y =  mOutput_jpg_buffer.phy_addr;
    dst->buf_size = mOutput_jpg_buffer.pmem_size;
    MemIon::Invalid_ion_buffer(dst->fd);

    ret = (*mJPEGEncode)(mOem_handle, src, dst, mean, out_enc_param);
    if (ret != JPEG_CODEC_SUCCESS) {
        result = false;
        ALOGE("sprd_jpg_encode fail, ret: %d", ret);
    }
    free(src);
    free(mean);
    return result;
}

///////////////////////////////////////////////////////////////////////////////
SprdJpegDecoder::SprdJpegDecoder(JNIEnv* env, jbyte* jpegData, jobject jpegInputStream, jint jpegSize, jint format)
    :mInitCheck(false),
    mJpegData(jpegData),
    mJpegInputStream(jpegInputStream),
    mJpegSize(jpegSize),
    mWidth(0),
    mHeight(0),
    mIoMmuEnabled(false),
    mYuvSize(0),
    mYuvFormat(format) {
    mOem_handle = (struct jpeg_codec_caller_handle*)malloc(sizeof(struct jpeg_codec_caller_handle));
    if (mOem_handle != NULL) {
        memset(mOem_handle, 0, sizeof(struct jpeg_codec_caller_handle));
    }
    memset(&mInput_jpg_buffer, 0, sizeof(struct ion_buffer));
    memset(&mOutput_y_buffer, 0, sizeof(struct ion_buffer));
    init(env);
}

SprdJpegDecoder::~SprdJpegDecoder() {
    if (mOem_handle != NULL) {
        if (mJPEGDeinit != NULL) {
            (*mJPEGDeinit)(mOem_handle);
        }
        free(mOem_handle);
    }
    if (mInput_jpg_buffer.pmem_heap != NULL) {
        mInput_jpg_buffer.pmem_heap.clear();
        mInput_jpg_buffer.pmem_heap = NULL;
    }
    if (mOutput_y_buffer.pmem_heap != NULL) {
        mOutput_y_buffer.pmem_heap.clear();
        mOutput_y_buffer.pmem_heap = NULL;
    }
    closeJpegLib();
}

bool SprdJpegDecoder::allocOutputBuffer() {
    if (mYuvSize == 0) {
        return false;
    }

    sp<MemIon> pYuvMem = allocateBuffer(mYuvSize, mIoMmuEnabled, 0, mOutput_y_buffer.vir_addr, &(mOutput_y_buffer.buffer_fd));
    mOutput_y_buffer.pmem_heap = pYuvMem;
    if (pYuvMem != NULL) {
        mOutput_y_buffer.pmem_size = mYuvSize;
        if (!mIoMmuEnabled) {
            pYuvMem->get_phy_addr_from_ion(&(mOutput_y_buffer.phy_addr), &(mOutput_y_buffer.pmem_size));
        }
    } else {
        ALOGE("allocOutputBuffer allocate yuv buffer fail, size: %d", mYuvSize);
        return false;
    }
    return true;
}

bool SprdJpegDecoder::allocInputBuffer() {
    if (mJpegSize == 0) {
        return false;
    }

    //allocate jpeg buffer
    sp<MemIon> pJpegMem = allocateBuffer(mJpegSize, mIoMmuEnabled, 0, mInput_jpg_buffer.vir_addr, &(mInput_jpg_buffer.buffer_fd));
    mInput_jpg_buffer.pmem_heap = pJpegMem;
    if (pJpegMem != NULL) {
        mInput_jpg_buffer.pmem_size = mJpegSize;
        if (!mIoMmuEnabled) {
            pJpegMem->get_phy_addr_from_ion(&(mInput_jpg_buffer.phy_addr), &(mInput_jpg_buffer.pmem_size));
        }
    } else {
        ALOGE("allocate jpeg buffer fail, size: %d", mJpegSize);
        return false;
    }
    return true;
}

void SprdJpegDecoder::init(JNIEnv* env) {
    bool ret = openJpegLib("libjpeg_hw_sprd.so");
    if (!ret) {
        ALOGE("SprdJpegDecoder::init openJpegLib fail!");
        mInitCheck = false;
        return;
    }

    if (JPEG_CODEC_SUCCESS != (*mJPEGInit)(mOem_handle, NULL)) {
        ALOGE("SprdJpegDecoder::init() sprd_jpeg_init fail!");
        mInitCheck = false;
        return;
    }
    mIoMmuEnabled = (*mJPEGGetIommuStatus)(mOem_handle) == 0 ? true : false;

    //allocate jpeg buffer
    if (!allocInputBuffer()) {
        ALOGE("SprdJpegDecoder::init allocate jpeg buffer fail, size: %d", mJpegSize);
        mInitCheck = false;
        return;
    } else {
        //read jpeg data
        if (mJpegData != NULL) {
            memcpy(mInput_jpg_buffer.vir_addr, mJpegData, mJpegSize);
        } else if (mJpegInputStream != NULL) {
            if (!jpegReadInputStream(env, mJpegInputStream, mJpegSize, mInput_jpg_buffer.vir_addr)) {
                mInitCheck = false;
                return;
            }
        } else {
            ALOGE("SprdJpegDecoder::init jpeg data is NULL! mJpegSize: %d", mJpegSize);
            mInitCheck = false;
            return;
        }

        //allocate yuv buffer
        (*mJPEGDecGetResolution)(mInput_jpg_buffer.vir_addr, mInput_jpg_buffer.pmem_size, &mWidth, &mHeight, &mYuvSize);
        if (!allocOutputBuffer()) {
            ALOGE("allocate yuv buffer fail, yuvBufferSize: %d", mYuvSize);
            mInitCheck = false;
            return;
        }
        mInitCheck = true;
    }
}

bool SprdJpegDecoder::jpegDecStart(yuvbuf_frm* dst) {
    struct yuvbuf_frm *src = NULL;
    struct jpg_op_mean *mean = NULL;
    bool result = true;
    int decRet = -1;
    if (dst == NULL) {
        return false;
    }

    mean = (struct jpg_op_mean*)malloc(sizeof(struct jpg_op_mean));
    if (mean == NULL) {
        return false;
    }
    memset(mean, 0, sizeof(struct jpg_op_mean));
    mean->slice_mode = 1;
    mean->is_sync = 1;

    src = (struct yuvbuf_frm*)malloc(sizeof(struct yuvbuf_frm));
    if (src == NULL) {
        free(mean);
        return false;
    }
    memset(src, 0, sizeof(struct yuvbuf_frm));
    src->fd = mInput_jpg_buffer.buffer_fd;
    src->addr_phy.addr_y = mInput_jpg_buffer.phy_addr;
    src->addr_vir.addr_y=(jpg_uint)(mInput_jpg_buffer.vir_addr);
    src->buf_size = mInput_jpg_buffer.pmem_size;
    MemIon::Invalid_ion_buffer(src->fd);

    dst->fd = mOutput_y_buffer.buffer_fd;
    if (mIoMmuEnabled) {
        dst->addr_phy.addr_y =  0;  //treat addr_y and addr_u as offset when ioMmuEnabled is true
        dst->addr_phy.addr_u =  mWidth * mHeight;
    } else {
        dst->addr_phy.addr_y =  mOutput_y_buffer.phy_addr;
    }
    dst->addr_vir.addr_y =(jpg_uint)(mOutput_y_buffer.vir_addr);
    dst->buf_size = mOutput_y_buffer.pmem_size;
    dst->size.width = mWidth;
    dst->size.height = mHeight;
    dst->data_end.y_endian = 1;
    if (mYuvFormat == YUV_FORMAT_NV21) {
        dst->data_end.uv_endian = 2;  //need yuv NV21
    } else {
        dst->data_end.uv_endian = 1;  //need yuv NV12
    }
    MemIon::Invalid_ion_buffer(dst->fd);

    decRet = (*mJPEGDecode)(mOem_handle, src, dst, mean);
    if (decRet != JPEG_CODEC_SUCCESS) {
        result = false;
        ALOGE("sprd_jpg_decode fail, decRet: %d", decRet);
    }
    free(src);
    free(mean);
    return result;
}

jobject SprdJpegDecoder::decode(JNIEnv* env) {
    jbyteArray yuvData = NULL;
    struct yuvbuf_frm *dst = NULL;
    dst = (struct yuvbuf_frm*)malloc(sizeof(struct yuvbuf_frm));
    if (dst == NULL) {
        return NULL;
    }
    memset(dst, 0, sizeof(struct yuvbuf_frm));

    //begin to decode the jpeg data
    if (!jpegDecStart(dst)) {
        free(dst);
        return NULL;
    }

    //copy the decoded yuv data
    if (dst->buf_size == 0 || dst->buf_size > mYuvSize) {
        free(dst);
        return NULL;
    } else {
        yuvData = env->NewByteArray(mYuvSize);
        if (haveException(env)) {
            free(dst);
            return NULL;
        }
        if (yuvData != NULL) {
            env->SetByteArrayRegion(yuvData, 0, mYuvSize, (jbyte *)mOutput_y_buffer.vir_addr);
        }
    }
    free(dst);

    //create yuv image object
    if (mWidth <= 0 || mHeight <= 0 || yuvData == NULL) {
        ALOGE("SprdJpegDecoder::decode create yuv image fail, mWidth: %d, mHeight: %d", mWidth, mHeight);
        return NULL;
    }
    return jpegCreateYuvImage(env, yuvData, mYuvFormat, mWidth, mHeight, NULL);
}
///////////////////////////////////////////////////////////////////////////////

/**
 * Encode yuv to jpeg, sprd hardware encoder support rotate, width and height needs
 * to be 16 bytes alignment when they are passed to encoder
 *
 * @param inYuv: input yuv byte array
 * @param format: the format of yuv image, sync with YuvImageEx
 * @param width: the width of yuv image
 * @param height: the height of yuv image
 * @param jpegQuality: compress percentage(the value is between 0~100)
 * @param rotation: The angle of rotation, can be 0, 90, 180 or 270
 * @param jstream: output stream object is used to store jpeg data
 *
 * @return true as success, false as fail
 */
static jboolean YuvJpegConverter_encodeYuvToJpeg(
        JNIEnv* env, jobject,
        jbyteArray inYuv,
        jint format,
        jint width,
        jint height,
        jint jpegQuality,
        jint rotation,
        jobject jstream) {
    jboolean result = JNI_FALSE;
    jbyte* yuv = env->GetByteArrayElements(inYuv, NULL);
    int yuvSize = env->GetArrayLength(inYuv);
    if (yuv == NULL) {
        return JNI_FALSE;
    }

    SprdJpegEncoder* jpegEncoder = new SprdJpegEncoder(yuv, yuvSize, format, width, height, jpegQuality, rotation);
    if (jpegEncoder != NULL) {
        if (jpegEncoder->initCheck()) {
            result = jpegEncoder->encode(env, jstream);
        }
        delete jpegEncoder;
    } else {
        ALOGE("YuvJpegConverter_encodeYuvToJpeg create SprdJpegEncoder fail!");
    }

    env->ReleaseByteArrayElements(inYuv, yuv, 0);
    return result;
}

/**
 * Decode jpeg to yuv, in order to use the YuvImage, we specify the yuv as NV21 format
 *
 * @param jstream: jpeg input stream, we could read data from it
 * @param size: jpeg data size
 *
 * @return YuvImage: the caller can get yuv data, width and height value from the object
 */
static jobject YuvJpegConverter_decodeJpegToYuvEx(
        JNIEnv* env, jobject clazz, jobject jstream, jint size, jint format) {
    jobject yuvImage = NULL;

    SprdJpegDecoder* jpegDecoder = new SprdJpegDecoder(env, NULL, jstream, size, format);
    if (jpegDecoder == NULL) {
        return NULL;
    } else {
        if (jpegDecoder->initCheck()) {
            yuvImage = jpegDecoder->decode(env);
        } else {
            ALOGE("YuvJpegConverter_decodeJpegToYuvEx SprdJpegDecoder init fail!");
        }
        delete jpegDecoder;
    }
    return yuvImage;
}

/**
 * Decode jpeg data to yuv, in order to use the YuvImage, we specify the yuv as NV21 format
 *
 * @param inJpegData: jpeg byte array
 * @param size: jpeg data size
 *
 * @return YuvImage: the caller can get yuv data, width and height value from the object
 */
static jobject YuvJpegConverter_decodeJpegToYuv(
        JNIEnv* env, jobject clazz, jbyteArray inJpegData, jint size, jint format) {
    jobject yuvImage = NULL;
    jbyte* jpegData = env->GetByteArrayElements(inJpegData, NULL);
    if (jpegData == NULL) {
        ALOGE("YuvJpegConverter_decodeJpegToYuv jpeg byte array is NULL!");
        return NULL;
    }

    SprdJpegDecoder* jpegDecoder = new SprdJpegDecoder(env, jpegData, NULL, size, format);
    if (jpegDecoder == NULL) {
        env->ReleaseByteArrayElements(inJpegData, jpegData, 0);
        return NULL;
    } else {
        if (jpegDecoder->initCheck()) {
            yuvImage = jpegDecoder->decode(env);
        } else {
            ALOGE("YuvJpegConverter_decodeJpegToYuv SprdJpegDecoder init fail!");
        }
        delete jpegDecoder;
    }

    env->ReleaseByteArrayElements(inJpegData, jpegData, 0);
    return yuvImage;
}

static const JNINativeMethod gYuvJpegMethods[] = {
    {   "nativeEncodeYuvToJpeg",  "([BIIIIILjava/io/OutputStream;)Z", (void*)YuvJpegConverter_encodeYuvToJpeg },
    {   "nativeDecodeJpegToYuv",  "([BII)Landroid/graphics/YuvImageEx;", (void*)YuvJpegConverter_decodeJpegToYuv},
    {   "nativeDecodeJpegToYuvEx",  "(Ljava/io/InputStream;II)Landroid/graphics/YuvImageEx;", (void*)YuvJpegConverter_decodeJpegToYuvEx},
};

jint JNI_OnLoad(JavaVM* vm, void* reserved __unused)
{
    JNIEnv* env = NULL;
    jint result = -1;

    ALOGI("JNI_OnLoad YuvJpegConverter");
    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("YuvJpegConverter GetEnv failed\n");
        goto exit;
    }
    assert(env != NULL);

    gYuvImageEx_class = (jclass) env->NewGlobalRef(env->FindClass("android/graphics/YuvImageEx"));
    if (gYuvImageEx_class == NULL) {
        ALOGE("gYuvImageEx_class is NULL!");
        goto exit;
    }
    gYuvImageEx_constructorMethodID = env->GetMethodID(gYuvImageEx_class,  "<init>", "([BIII[I)V");
    if (gYuvImageEx_constructorMethodID == NULL) {
        ALOGE("gYuvImageEx_constructorMethodID is NULL!");
        goto exit;
    }

    if (env->RegisterNatives(gYuvImageEx_class, gYuvJpegMethods, NELEM(gYuvJpegMethods)) != JNI_OK) {
        ALOGE("RegisterNatives failed");
        goto exit;
    }

    result = JNI_VERSION_1_4;

exit:
    return result;
}

