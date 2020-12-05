#LOCAL_PATH := $(call my-dir)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := vendor.sprd.hardware.aprd@1.0-impl
#LOCAL_PROPRIETARY_MODULE := true
#LOCAL_MODULE_RELATIVE_PATH := hw
#LOCAL_SRC_FILES := \
#    CPxInfo.cpp \
#    ModemExpMonitor.cpp \
#    AprdInfoSync.cpp \
#    VendorPropMonitor.cpp
#
#LOCAL_SHARED_LIBRARIES := \
#	libhidlbase \
#	libhidltransport \
#	liblog \
#	libsysutils \
#	libdl \
#	libutils \
#	libcutils \
#	libhardware \
#	libatci \
#	libc++ \
#	libxml2 \
#	vendor.sprd.hardware.aprd@1.0
#	
#LOCAL_C_INCLUDES := vendor/sprd/modules/libatci
#LOCAL_C_INCLUDES += external/libcxx/include
#
#LOCAL_CFLAGS := -DLOG_TAG=\"vendor.sprd.hardware.IAprd@1.0-service\"
#
#include $(BUILD_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE_RELATIVE_PATH := hw
#LOCAL_PROPRIETARY_MODULE := true
#LOCAL_MODULE := vendor.sprd.hardware.aprd@1.0-service
#LOCAL_INIT_RC := vendor.sprd.hardware.aprd@1.0-service.rc
#LOCAL_SRC_FILES := \
#    service.cpp \
#
#LOCAL_SHARED_LIBRARIES := \
#	libhidlbase \
#	libhidltransport \
#	liblog \
#	libsysutils \
#	libdl \
#	libutils \
#	libcutils \
#	libhardware \
#	libatci \
#	libc++ \
#	libxml2 \
#	vendor.sprd.hardware.aprd@1.0
#	
#LOCAL_C_INCLUDES := vendor/sprd/modules/libatci
#LOCAL_C_INCLUDES += external/libcxx/include
#
#LOCAL_CFLAGS := -DLOG_TAG=\"vendor.sprd.hardware.IAprd@1.0-service\"
#
#include $(BUILD_EXECUTABLE)
