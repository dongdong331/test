
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := tensorflowlite_java

LOCAL_SDK_VERSION := 27
LOCAL_SRC_FILES := $(call all-java-files-under, java)

LOCAL_STATIC_JAVA_LIBRARIES := android-support-annotations

LOCAL_REQUIRED_MODULES := libtensorflowlite_jni

include $(BUILD_STATIC_JAVA_LIBRARY)

