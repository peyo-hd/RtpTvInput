LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := rtp_sink.cpp  \
        sink/LinearRegression.cpp \
        sink/TunnelRenderer.cpp \
        sink/RTPSink.cpp

LOCAL_C_INCLUDES:= \
        frameworks/av/media/libstagefright \
        frameworks/av/media/libstagefright/mpeg2ts

LOCAL_SHARED_LIBRARIES:= \
        libbinder                       \
        libcutils                       \
        liblog                          \
        libgui                          \
        libmedia                        \
        libstagefright                  \
        libstagefright_foundation       \
        libui                           \
        libutils                        \
        libandroid_runtime

LOCAL_MODULE:= librtp_sink
LOCAL_MODULE_TAGS:= optional
LOCAL_32_BIT_ONLY := true

include $(BUILD_SHARED_LIBRARY)
