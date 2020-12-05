ifeq ($(USE_PROJECT_SEC),true)
LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
            $(call all-subdir-java-files)
LOCAL_JAVA_LIBRARIES := bouncycastle core-libart ext services.core
LOCAL_MODULE_TAGS := optional
LOCAL_JACK_ENABLED := disabled
LOCAL_MODULE:= security
#LOCAL_NO_EMMA_INSTRUMENT := true
#LOCAL_NO_EMMA_COMPILE := true

include $(BUILD_JAVA_LIBRARY)
endif
