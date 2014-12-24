LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_JNI_SHARED_LIBRARIES := librtp_sink
LOCAL_32_BIT_ONLY := true

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := RtpTvInput
LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)

include $(call first-makefiles-under,$(LOCAL_PATH))


