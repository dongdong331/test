LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := TfLiteCameraDemo

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_STATIC_JAVA_LIBRARIES := \
    tensorflowlite_java \
    android-support-v13 \
    android-support-v4 \
    android-support-annotations

# tensorflowlite_java depend on libtensorflowlite_jni, here do NOT need specifying.
#LOCAL_JNI_SHARED_LIBRARIES := libtensorflowlite_jni

LOCAL_SDK_VERSION := 27

LOCAL_DEX_PREOPT := false

LOCAL_ASSET_DIR := $(LOCAL_PATH)/assets
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_AAPT_FLAGS := -0 tflite
#LOCAL_USE_AAPT2 := true

include $(BUILD_PACKAGE)

#include $(call all-makefiles-under,$(LOCAL_PATH))

