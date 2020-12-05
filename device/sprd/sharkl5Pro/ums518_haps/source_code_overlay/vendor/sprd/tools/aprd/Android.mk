#LOCAL_PATH:= $(call my-dir)
#
##apr
#include $(CLEAR_VARS)
#
#LOCAL_MODULE := collect_apr
#LOCAL_INIT_RC := aprd.rc
#LOCAL_MODULE_TAGS := optional
##LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR_EXECUTABLES)
#
#LOCAL_STATIC_LIBRARIES := libxml2 libcutils
#LOCAL_SHARED_LIBRARIES := libcutils libutils liblog libhardware  libz
#LOCAL_SHARED_LIBRARIES += libicuuc
#LOCAL_SHARED_LIBRARIES += libc
#LOCAL_SHARED_LIBRARIES += libext2_uuid
#LOCAL_SHARED_LIBRARIES += liblog
#LOCAL_SHARED_LIBRARIES += libhidlbase libsysutils libhidltransport vendor.sprd.hardware.aprd@1.0
##LOCAL_SHARED_LIBRARIES += liblogcat
#
#LOCAL_SHARED_LIBRARIES += libc++ #libatci
#LOCAL_SRC_FILES := main.cpp
#LOCAL_SRC_FILES += VendorSync.cpp
#LOCAL_SRC_FILES += VendorXml.cpp
##LOCAL_SRC_FILES += CPxInfoThread.cpp
#LOCAL_SRC_FILES += Observable.cpp
#LOCAL_SRC_FILES += Observer.cpp
#LOCAL_SRC_FILES += AprData.cpp
#LOCAL_SRC_FILES += XmlStorage.cpp
#LOCAL_SRC_FILES += Thread.cpp
#LOCAL_SRC_FILES += InotifyThread.cpp
#LOCAL_SRC_FILES += NativeCrash.cpp
#LOCAL_SRC_FILES += AnrThread.cpp
#LOCAL_SRC_FILES += JavaCrashThread.cpp
##LOCAL_SRC_FILES += ModemThread.cpp
#LOCAL_SRC_FILES += RamUsedThread.cpp
#LOCAL_SRC_FILES += SSRListener.cpp
#LOCAL_SRC_FILES += common.c
#LOCAL_SRC_FILES += confile.c
#
#LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog
#
#LOCAL_CFLAGS := -D_STLP_USE_NO_IOSTREAMS
#LOCAL_CFLAGS += -D_STLP_USE_MALLOC
#LOCAL_CFLAGS += -DLOG_TAG=\"APR\"
##LOCAL_CFLAGS += -DAPR_CMCC_ONLY
#
#LOCAL_C_INCLUDES := $(LOCAL_PATH)/inc
#
#LOCAL_C_INCLUDES += external/libxml2/include
#LOCAL_C_INCLUDES += external/icu/icu4c/source/common
##LOCAL_C_INCLUDES += vendor/sprd/modules/libatci
#LOCAL_C_INCLUDES += external/libcxx/include
##libxml2 libatci libc++
#
#include $(BUILD_EXECUTABLE)
#
##aprctl
#include $(CLEAR_VARS)
#LOCAL_SRC_FILES := aprctl.c
#LOCAL_SRC_FILES += common.c
#LOCAL_MODULE := aprctl
#LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR_EXECUTABLES)
#LOCAL_STATIC_LIBRARIES := libcutils
##LOCAL_SHARED_LIBRARIES += libatci libc++
#LOCAL_MODULE_TAGS := optional
##LOCAL_LDLIBS += -lpthread
#LOCAL_SHARED_LIBRARIES += liblog libz
#LOCAL_C_INCLUDES := $(LOCAL_PATH)/inc
##LOCAL_C_INCLUDES += vendor/sprd/modules/libatci
#LOCAL_C_INCLUDES += external/libcxx/include
#include $(BUILD_EXECUTABLE)
#
##apr.conf
##include $(CLEAR_VARS)
##LOCAL_MODULE := apr.conf
##LOCAL_MODULE_TAGS := optional
##LOCAL_MODULE_CLASS := DATA
##LOCAL_MODULE_PATH :=  $(TARGET_OUT_DATA)/vendor/sprdinfo
##LOCAL_SRC_FILES := apr.conf
##include $(BUILD_PREBUILT)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := apr.conf.etc
#LOCAL_MODULE_TAGS := optional
#LOCAL_MODULE_CLASS := DATA
#LOCAL_MODULE_PATH :=  $(TARGET_OUT_ETC)
#LOCAL_SRC_FILES := apr.conf
#include $(BUILD_PREBUILT)
#
##apr.conf
#include $(CLEAR_VARS)
#LOCAL_MODULE := apr.cmcc.conf
#LOCAL_MODULE_TAGS := optional
#LOCAL_MODULE_CLASS := ETC
#LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)
#LOCAL_SRC_FILES := $(LOCAL_MODULE)
#include $(BUILD_PREBUILT)
#
##apr.conf.user
#include $(CLEAR_VARS)
#LOCAL_MODULE := apr.cmcc.conf.user
#LOCAL_MODULE_TAGS := optional
#LOCAL_MODULE_CLASS := ETC
#LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)
#LOCAL_SRC_FILES := $(LOCAL_MODULE)
#include $(BUILD_PREBUILT)
#
#CUSTOM_MODULES += collect_apr
#CUSTOM_MODULES += aprctl
##CUSTOM_MODULES += apr.conf
#CUSTOM_MODULES += apr.conf.etc
#CUSTOM_MODULES += apr.cmcc.conf
#CUSTOM_MODULES += apr.cmcc.conf.user
