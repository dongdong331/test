#ifeq ($(strip $(SUPPORT_GNSS_HARDWARE)), true)
#LOCAL_PATH := $(call my-dir)
#
##$(warning shell echo "it should $(TARGET_BUILD_VARIANT)")
#
#ifneq ($(strip $(SPRD_MODULES_GNSS3)), true)
##get the lte src path
#ifeq ($(TARGET_ARCH), $(filter $(TARGET_ARCH), x86 x86_64))
#    GNSS_LTE_SO_PATH := lte/x86
#else
#   GNSS_LTE_SO_PATH := lte/arm
#endif
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := liblte
#LOCAL_MODULE_CLASS := SHARED_LIBRARIES
#
#LOCAL_MULTILIB := both
#LOCAL_MODULE_STEM_32 := liblte.so
#LOCAL_SRC_FILES_32 :=  $(GNSS_LTE_SO_PATH)/32bit/liblte.so
#
#ifeq ($(TARGET_ARCH), $(filter $(TARGET_ARCH), arm64 x86_64))
#LOCAL_MODULE_STEM_64 := liblte.so
#LOCAL_SRC_FILES_64 :=  $(GNSS_LTE_SO_PATH)/64bit/liblte.so
#endif
#LOCAL_MODULE_TAGS := optional
#LOCAL_PROPRIETARY_MODULE := true
#
#
#include $(BUILD_PREBUILT)
#endif
#
#GNSS_LCS_FLAG := TRUE
#
#include $(CLEAR_VARS)
#LOCAL_SRC_FILES := \
#	common/src/agps.c \
#	common/src/agps_server.c \
#	common/src/gps.c \
#	common/src/gps_comm.c \
#	common/src/gps_hardware.c \
#	common/src/gps_daemon.c \
#	common/src/gnss_libgps_api.c \
#	common/src/SpreadOrbit.c \
#	common/src/eph.c \
#	common/src/nmeaenc.c\
#	common/src/gnssdaemon_client.c\
#	common/src/navc.c \
#  	common/src/gnss_log.c \
#	agps/src/common.c \
#	agps/src/gps_api.c \
#	agps/src/agps_interface.c \
#  	agps/src/lcs_agps.c
#
#
#LOCAL_C_INCLUDES := \
#	$(LOCAL_PATH)/agps/inc \
#	$(LOCAL_PATH)/common/inc \
#	hardware/libhardware_legacy \
#	hardware/libhardware_legacy/include/hardware_legacy \
#	hardware/libhardware/include/hardware \
#	external/openssl/include
#
#LOCAL_SHARED_LIBRARIES := \
#	libc \
#	libutils \
#	libcutils \
#	libhardware \
#	liblog \
#	libpower \
#	libm   \
#	libdl
#
#ifneq ($(strip $(SPRD_MODULES_GNSS3)),true)
#LOCAL_SHARED_LIBRARIES += liblte
#endif
#
#ifeq ($(TARGET_BUILD_VARIANT),user)
#LOCAL_CFLAGS += -DGNSS_NODEBUG
#endif
#
#LOCAL_CFLAGS +=  -Wall -Wno-missing-field-initializers  -Wunreachable-code -Wpointer-arith -Wshadow 
#LOCAL_PRELINK_MODULE := false
#LOCAL_MODULE_RELATIVE_PATH := hw
#LOCAL_MODULE := gps.default
#LOCAL_MODULE_TAGS := optional
#LOCAL_PROPRIETARY_MODULE := true
##$(warning shell echo "it build end libgps")
#include $(BUILD_SHARED_LIBRARY)
#
##gpsd 
##$(warning shell echo "gpsd build begin")
#include $(CLEAR_VARS)
#
#LOCAL_SRC_FILES := gnssdeamon/gnssdaemon.c
#
#LOCAL_STATIC_LIBRARIES := libcutils
#
#
#LOCAL_MODULE := gpsd
#LOCAL_MODULE_TAGS := optional
#
#LOCAL_SHARED_LIBRARIES := liblog
#LOCAL_PROPRIETARY_MODULE := true
#
#include $(BUILD_EXECUTABLE)
#endif
