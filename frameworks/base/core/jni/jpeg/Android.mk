LOCAL_PATH:= $(call my-dir)

ifeq ($(strip $(TARGET_BOARD_SPRD_JPEG_CODEC_SUPPORT)),true)
include $(CLEAR_VARS)
LOCAL_SRC_FILES :=     \
        YuvJpegConverter.cpp              \
        ../../../../../vendor/sprd/modules/libmemion/MemIon.cpp \

LOCAL_C_INCLUDES += \
        $(TOP)/vendor/sprd/modules/libmemion   \
        $(TOP)/vendor/sprd/external/kernel-headers  \
        $(TOP)/frameworks/base/core/jni       \
        $(TOP)/frameworks/base/core/jni/android/graphics  \
        external/skia/include/core   \
        external/skia/include/config

LOCAL_SHARED_LIBRARIES :=               \
        libandroid_runtime  \
        liblog \
        libutils  \

LOCAL_MODULE:= libyuv_jpeg_converter_jni
LOCAL_CFLAGS += -Werror -Wall
LOCAL_CFLAGS += -Wno-unused-parameter -Wno-unused-variable
include $(BUILD_SHARED_LIBRARY)
endif