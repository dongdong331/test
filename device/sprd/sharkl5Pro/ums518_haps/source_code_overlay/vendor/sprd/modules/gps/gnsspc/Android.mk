#
#LOCAL_PATH := $(call my-dir)
#
#include $(CLEAR_VARS)
#
#LOCAL_32_BIT_ONLY := true
#LOCAL_SRC_FILES := gps_pc_mode.c fft.c gnss_pc_cmd.c
#
#LOCAL_MODULE := libgpspc
#LOCAL_MODULE_TAGS := optional
#LOCAL_MODULE_RELATIVE_PATH := npidevice
#LOCAL_PROPRIETARY_MODULE := true
#
#LOCAL_C_INCLUDES := \
#	$(LOCAL_PATH)\
#	hardware/libhardware_legacy \
#	hardware/libhardware_legacy/include/hardware_legacy \
#	hardware/libhardware/include/hardware \
#	$(TOP)/vendor/sprd/proprietories-source/engmode
#
#LOCAL_SHARED_LIBRARIES := \
#	libc \
#	libutils \
#	libhardware\
#	libcutils \
#	liblog \
#	libdl
#
#ifeq ($(strip $(PLATFORM_VERSION)),7.0)
#LOCAL_CFLAGS += -DGNSS_ANDROIDN
#endif
#LOCAL_CFLAGS += -DGNSS_ANDROIDN #it the test
#
#ifeq ($(strip $(SPRD_MODULES_GNSS3)),true)
#LOCAL_CFLAGS += -DGNSS_MARLIN3
#endif
#
#include $(BUILD_SHARED_LIBRARY)
#
#
