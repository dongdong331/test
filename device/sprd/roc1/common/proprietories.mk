PROPRIETARY_BOARD := roc1

#ifneq ($(shell ls -d vendor/sprd/proprietories-source 2>/dev/null),)
# for spreadtrum internal proprietories modules: only support package module names

#FIXME: C99[-Werror,-Wimplicit-function-declaration]
PRODUCT_PACKAGES :=              \
    libomx_m4vh263dec_sw_sprd    \
    libomx_m4vh263enc_sw_sprd    \
    libomx_avcdec_sw_sprd        \
    libomx_avcdec_hw_sprd        \
    libomx_avcenc_hw_sprd        \
    libomx_mp3dec_sprd           \
    libomx_mp3enc_sprd           \
    libjpeg_hw_sprd              \
    thermald

# add for volte
ifneq ($(filter $(VOLTE_SERVICE_ENABLE), volte_vowifi_shared volte_only), )
PRODUCT_PACKAGES += \
    libvideo_call_engine_jni \
    libvolte_video_service_jni
endif

ifeq ($(strip $(VOLTE_SERVICE_ENABLE)), volte_only)
PRODUCT_PACKAGES += \
    modemDriver_vpad_main
endif

ifeq ($(strip $(VOLTE_SERVICE_ENABLE)), volte_vowifi_shared)
PRODUCT_PACKAGES += \
    ims_bridged \
    libimsbrd \
    libavatar \
    libzmf \
    libCamdrv24 \
    libmme_jrtc
endif

ifeq ($(strip $(VOWIFI_SERVICE_ENABLE)), true)
# Add for vowifi
# add for vowifi source code module
PRODUCT_PACKAGES += \
    SprdVoWifiService \
    ImsCM \
    operator_info.xml \
    operator_config.xml

#ims bridge daemon
PRODUCT_PACKAGES += \
    ims_bridged \
    libimsbrd

#ip monitor
PRODUCT_PACKAGES += ip_monitor.sh

# add for vowifi bin module
PRODUCT_PACKAGES += vowifi_sdk \
    libavatar \
    libzmf \
    libCamdrv24 \
    liblemon \
    libmme_jrtc \
    ju_ipsec_server
endif

# for spreadtrum customer proprietories modules: only support direct copy
#FIXME: C99[-Werror,-Wimplicit-function-declaration]
PROPMODS :=                                     \
    system/lib/libomx_m4vh263dec_sw_sprd.so     \
    system/lib/libomx_m4vh263enc_sw_sprd.so     \
    system/lib/libomx_avcdec_sw_sprd.so         \
    system/lib/libomx_avcdec_hw_sprd.so         \
    system/lib/libomx_avcenc_hw_sprd.so         \
    system/lib/libomx_mp3dec_sprd.so            \
    system/lib/libomx_mp3enc_sprd.so            \
    system/lib/libjpeg_hw_sprd.so               \
    vendor/bin/thermald                         \
    system/bin/ylog                             \
    system/bin/ylog_cli                         \
    system/bin/ylog_benchmark                   \
    system/bin/ylog_benchmark_socket_server     \
    system/bin/ylogd                            \
    system/bin/exfatfsck                        \
    system/bin/mkfsexfat

#config video engine for volte video call
ifneq ($(filter $(VOLTE_SERVICE_ENABLE), volte_vowifi_shared volte_only), )
PROPMODS += \
    system/app/VceDaemon \
    system/lib/libvideo_call_engine_jni.so \
    system/lib/libvolte_video_service_jni_.so \
    system/lib64/libvideo_call_engine_jni.so \
    system/lib64/libvolte_video_service_jni_.so
endif

ifeq ($(strip $(VOLTE_SERVICE_ENABLE)), volte_only)
PROPMODS += \
    system/bin/modemDriver_vpad_main
endif

ifeq ($(strip $(VOLTE_SERVICE_ENABLE)), volte_vowifi_shared)
PROPMODS += \
    system/bin/ims_bridged \
    system/lib/libimsbrd.so \
    system/lib/libavatar.so \
    system/lib/libzmf.so \
    system/lib/libCamdrv24.so \
    system/lib/libmme_jrtc.so \
    system/lib64/libimsbrd.so \
    system/lib64/libavatar.so \
    system/lib64/libzmf.so \
    system/lib64/libCamdrv24.so \
    system/lib64/libmme_jrtc.so
endif

