# Copyright 2010 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH:= $(call my-dir)

#
# libcameraservice
#

include $(CLEAR_VARS)

# Camera service source

LOCAL_SRC_FILES :=  \
    CameraService.cpp \
    CameraFlashlight.cpp \
    common/Camera2ClientBase.cpp \
    common/CameraDeviceBase.cpp \
    common/CameraProviderManager.cpp \
    common/FrameProcessorBase.cpp \
    api1/CameraClient.cpp \
    api1/Camera2Client.cpp \
    api1/client2/Parameters.cpp \
    api1/client2/FrameProcessor.cpp \
    api1/client2/StreamingProcessor.cpp \
    api1/client2/JpegProcessor.cpp \
    api1/client2/CallbackProcessor.cpp \
    api1/client2/JpegCompressor.cpp \
    api1/client2/CaptureSequencer.cpp \
    api1/client2/ZslProcessor.cpp \
    api2/CameraDeviceClient.cpp \
    device1/CameraHardwareInterface.cpp \
    device3/Camera3Device.cpp \
    device3/Camera3Stream.cpp \
    device3/Camera3IOStreamBase.cpp \
    device3/Camera3InputStream.cpp \
    device3/Camera3OutputStream.cpp \
    device3/Camera3DummyStream.cpp \
    device3/Camera3SharedOutputStream.cpp \
    device3/StatusTracker.cpp \
    device3/Camera3BufferManager.cpp \
    device3/Camera3StreamSplitter.cpp \
    device3/DistortionMapper.cpp \
    gui/RingBufferConsumer.cpp \
    utils/CameraTraces.cpp \
    utils/AutoConditionLock.cpp \
    utils/TagMonitor.cpp \
    utils/LatencyHistogram.cpp

# SPRD:Add for Sprd Ex
LOCAL_CFLAGS += -DSPRD_FRAMEWORKS_CAMERA_EX
LOCAL_SRC_FILES += \
    ../../../../../vendor/sprd/platform/frameworks/av/service/camera/libcameraservice/api1/client2/ParametersExFun.cpp \
    ../../../../../vendor/sprd/platform/frameworks/av/service/camera/libcameraservice/api1/Camera2ClientEx.cpp \
    ../../../../../vendor/sprd/platform/frameworks/av/service/camera/libcameraservice/device3/Camera3DeviceEx.cpp \
    ../../../../../vendor/sprd/platform/frameworks/av/service/camera/libcameraservice/api2/CameraDeviceClientEx.cpp

LOCAL_SHARED_LIBRARIES:= \
    libui \
    liblog \
    libutilscallstack \
    libutils \
    libbinder \
    libcutils \
    libmedia \
    libmediautils \
    libcamera_client \
    libcamera_metadata \
    libfmq \
    libgui \
    libhardware \
    libhidlbase \
    libhidltransport \
    libjpeg \
    libpowermanager \
    libmemunreachable \
    android.hardware.camera.common@1.0 \
    android.hardware.camera.provider@2.4 \
    android.hardware.camera.device@1.0 \
    android.hardware.camera.device@3.2 \
    android.hardware.camera.device@3.3 \
    android.hardware.camera.device@3.4

LOCAL_C_INCLUDES += \
    vendor/sprd/modules/libcamera/include \
    frameworks/av/include/camera/cameraEx \
    vendor/sprd/platform/frameworks/av/service/camera/libcameraservice

LOCAL_EXPORT_SHARED_LIBRARY_HEADERS := libbinder libcamera_client libfmq

LOCAL_C_INCLUDES += \
    system/media/private/camera/include \
    frameworks/native/include/media/openmax

LOCAL_EXPORT_C_INCLUDE_DIRS := \
    frameworks/av/services/camera/libcameraservice

LOCAL_CFLAGS += -Wall -Wextra -Werror

# SPRD: add for feature:LCD flash
ifneq (,$(filter lcd led flash, $(shell echo $(TARGET_BOARD_FRONT_CAMERA_FLASH_TYPE) | tr A-Z a-z)))
LOCAL_CFLAGS += -DFRONT_CAMERA_FLASH_TYPE=\"$(shell echo $(TARGET_BOARD_FRONT_CAMERA_FLASH_TYPE) | tr A-Z a-z)\"
else
LOCAL_CFLAGS += -DFRONT_CAMERA_FLASH_TYPE=\"none\"
endif

# SPRD: add for feature:eis
ifeq ($(strip $(TARGET_BOARD_CAMERA_EIS)),true)
LOCAL_CFLAGS += -DCONFIG_CAMERA_SPRD_EIS
endif

# SPRD:Add for Feature:slowmotion
ifeq ($(strip $(TARGET_BOARD_SPRD_SLOWMOTION_OPTIMIZE)),true)
LOCAL_CFLAGS += -DSPRD_SLOWMOTION_OPTIMIZE
endif

# SPRD:Add for Feature:selfshot and pageturn
ifeq ($(strip $(TARGET_BOARD_COVERED_SENSOR_SUPPORT)),true)
LOCAL_CFLAGS += -DSPRD_FEATURE_COVERED_SENSOR
endif

# SPRD:Add for Feature:ZSL
LOCAL_CFLAGS += -DSPRD_FEATURE_ZSL
# SPRD:Add for Feature:Brightness
LOCAL_CFLAGS += -DSPRD_FEATURE_BRIGHTNESS
# SPRD:Add for Feature:ISO
LOCAL_CFLAGS += -DSPRD_FEATURE_ISO
# SPRD:Add for Feature:Contrast
LOCAL_CFLAGS += -DSPRD_FEATURE_CONTRAST
# SPRD:Add for Feature:Saturation
LOCAL_CFLAGS += -DSPRD_FEATURE_SATURATION
# SPRD:Add for Feature:Metering Mode
LOCAL_CFLAGS += -DSPRD_FEATURE_METERING_MODE
# SPRD:Add for Feature:Slow Motion
LOCAL_CFLAGS += -DSPRD_FEATURE_SLOW_MOTION
# SPRD:Add for Feature:3GVT
LOCAL_CFLAGS += -DSPRD_FEATURE_3GVT
# SPRD:Add for Feature:Beauty
LOCAL_CFLAGS += -DSPRD_FEATURE_BEAUTY
# SPRD:Add for Feature:EIS
LOCAL_CFLAGS += -DSPRD_FEATURE_EIS
# SPRD:Add for Feature:3DNR
LOCAL_CFLAGS += -DSPRD_FEATURE_3DNR

LOCAL_MODULE:= libcameraservice

include $(BUILD_SHARED_LIBRARY)

# Build tests too

include $(LOCAL_PATH)/tests/Android.mk

