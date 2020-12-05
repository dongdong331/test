# Copyright (C) 2015 The Android Open Source Project
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

LOCAL_PATH := device/sprd/pike2/common
ROOTCOMM := $(LOCAL_PATH)/rootdir
include $(LOCAL_PATH)/TelephonyCommon.mk
include $(LOCAL_PATH)/ModemCommon.mk
include $(wildcard $(LOCAL_PATH)/common_packages.mk)
include $(LOCAL_PATH)/emmc/emmc_device.mk

BOARD_VNDK_VERSION := current
OMA_DRM := true

#add Omacp package
PRODUCT_PACKAGES += \
    Omacp

# add oma drm in pac
PRODUCT_PACKAGES += \
    libdrmomaplugin

ifeq ($(OMA_DRM),true)
PRODUCT_PROPERTY_OVERRIDES += \
    drm.service.enabled=true
else
PRODUCT_PROPERTY_OVERRIDES += \
    drm.service.enabled=false
endif

PRODUCT_PACKAGES += \
    SGPS

PRODUCT_PACKAGES += \
	connmgr  \
    engpc  \
    libgpio


#default audio
PRODUCT_PACKAGES += \
    audio.a2dp.default \
    audio.usb.default \
    audio.r_submix.default

#audio hidl hal impl
PRODUCT_PACKAGES += \
    android.hardware.audio@4.0-impl \
    android.hardware.audio.effect@4.0-impl \
    android.hardware.broadcastradio@1.0-impl \
    android.hardware.soundtrigger@2.0-impl \
    android.hardware.audio@2.0-service

# vndk
PRODUCT_PACKAGES += \
    vndk_package
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/vndk/system.public.libraries-sprd.txt:system/etc/public.libraries-sprd.txt \
    $(LOCAL_PATH)/vndk/vendor.public.libraries.txt:vendor/etc/public.libraries.txt

#aosp power hidl service
#PRODUCT_PACKAGES += \
    android.hardware.power@1.0-impl \
    android.hardware.power@1.0-service

# Power hidl HAL
PRODUCT_PACKAGES += \
    vendor.sprd.hardware.power@3.0-impl \
    vendor.sprd.hardware.power@3.0-service

# powerhint HAL config
# sprdemand, interactive, schedutil_8
BOARD_POWERHINT_HAL := interactive
POWERHINT_PRODUCT_CONFIG := pike2

# Power hint config file
PRODUCT_PACKAGES += \
    power_scene_id_define.txt \
    power_scene_config.xml \
    power_resource_file_info.xml \
    libpowerhal_cli

# GNSS
ifeq ($(strip $(SUPPORT_GNSS_HARDWARE)), true)
PRODUCT_PROPERTY_OVERRIDES += \
    ro.vendor.gnsschip=ge2
endif

# Health HAL
PRODUCT_PACKAGES += \
    android.hardware.health@2.0-service \
    android.hardware.health@2.0-impl

# Light HAL
PRODUCT_PACKAGES += \
    android.hardware.light@2.0-impl \

include vendor/sprd/modules/devdrv/input/leds/leddrv.mk

# Keymaster HAL
PRODUCT_PACKAGES += \
    android.hardware.keymaster@3.0-impl \
    android.hardware.keymaster@3.0-service

# Bluetooth HAL
#PRODUCT_PACKAGES += \
#    libbt-vendor \
    android.hardware.bluetooth@1.0-impl

# RenderScript HAL
PRODUCT_PACKAGES += \
    android.hardware.renderscript@1.0-impl

# Dumpstate service
PRODUCT_PACKAGES += \
    android.hardware.dumpstate@1.0-service

# add  treble enable
PRODUCT_PROPERTY_OVERRIDES += \
ro.treble.enabled = true

# use PRODUCT_SHIPPING_API_LEVEL indicates the first api level,and contorl treble macro
PRODUCT_SHIPPING_API_LEVEL := 28
# add vndk version
PRODUCT_PROPERTY_OVERRIDES += \
ro.vendor.vndk.version = 1

ifeq ($(TARGET_BUILD_VARIANT),user)
PRODUCT_PROPERTY_OVERRIDES += \
    persist.vendor.sys.wcnreset=1 \
    persist.vendor.aprservice.enabled=0 \
    persist.sys.apr.enabled=0 \
    persist.sys.apr.timechanged=180 \
    persist.sys.apr.rlchanged=800 \
    persist.sys.apr.lifetime=0 \
    persist.sys.apr.reload=0  \
    persist.sys.apr.reportlevel=2  \
    persist.sys.apr.exceptionnode=0
else
PRODUCT_PROPERTY_OVERRIDES += \
    persist.vendor.sys.wcnreset=0 \
    persist.vendor.aprservice.enabled=1 \
    persist.sys.apr.enabled=1 \
    persist.sys.apr.timechanged=180 \
    persist.sys.apr.rlchanged=800 \
    persist.sys.apr.lifetime=0 \
    persist.sys.apr.reload=0  \
    persist.sys.apr.reportlevel=0  \
    persist.sys.apr.exceptionnode=0  \
    persist.vendor.sys.core.enabled = 1
endif # TARGET_BUILD_VARIANT == user


# Set default USB interface
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    persist.sys.usb.config=ptp

PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    sys.usb.controller=musb-hdrc.0.auto

PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.heartbeat.enable=1\
    persist.sys.power.touch=1

#poweroff charge
PRODUCT_PACKAGES += charge \
                    phasecheckserver


# ION HAL module
PRODUCT_PACKAGES += \
    libmemion
# ION module
PRODUCT_PACKAGES += \
    libion
#MAP USER
PRODUCT_PACKAGES += \
    libmapuser

# graphics modules
PRODUCT_PACKAGES += gralloc.$(TARGET_BOARD_PLATFORM).so

ifeq ($(strip $(GRAPHIC_RENDER_TYPE)), CPU)
PRODUCT_PACKAGES +=  \
        libGLES_android \
        libEGL       \
        libGLESv1_CM \
        libGLESv2
ifeq ($(strip $(USE_SPRD_HWCOMPOSER)),true)
$(error, USE_SPRD_HWCOMPOSER should not be true)
endif

else # $(GRAPHIC_RENDER_TYPE)), CPU)
#PRODUCT_PACKAGES += \
    #libGLES_mali.so \
    #libRSDriverArm_64.so \
    #libRSDriverArm.so \
    #libbccArm_64.so \
    #libbccArm.so \
    #libmalicore_64.bc \
    #libmalicore.bc \
    #libmalicore.bci \
    #libhwc2on1adapter.so \
    #libboost.so \
    #bcc.vendor  \
    #libclcore.bc.vendor \
    #libclcore_neon.bc.vendor

PRODUCT_PACKAGES += \
    libGLES_mali.so \
    libhwc2on1adapter.so \
    libboost.so

PRODUCT_PACKAGES += \
    android.hardware.graphics.mapper@2.0-impl \
    android.hardware.graphics.allocator@2.0-impl \
    android.hardware.graphics.allocator@2.0-service \
    android.hardware.graphics.composer@2.1-impl \
    android.hardware.graphics.composer@2.1-service
PRODUCT_PACKAGES += \
    android.hardware.camera.provider@2.4-impl-sprd \
    android.hardware.camera.provider@2.4-service

ifeq ($(strip $(USE_SPRD_HWCOMPOSER)),true)
$(warning, if sprd hwcomposer is not ready, USE_SPRD_HWCOMPOSER should not be true)
PRODUCT_PACKAGES +=  hwcomposer.$(TARGET_BOARD_PLATFORM)
endif
OVERRIDE_RS_DRIVER := libRSDriverArm.so

PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
   ro.vendor.gpu.boost=1 \
   ro.vendor.gpu.boost.path=/vendor/lib/libboost.so \
   ro.vendor.gpu.boost.sf=/system/bin/surfaceflinger \
   ro.vendor.gpu.boost.sf.level=9

ifeq ($(strip $(TARGET_GPU_PLATFORM)),midgard)
PRODUCT_COPY_FILES += \
    vendor/sprd/external/drivers/gpu/midgard/mali/platform/pike2/libboost.so:$(TARGET_COPY_OUT_VENDOR)/lib/libboost.so
else
PRODUCT_COPY_FILES += \
    vendor/sprd/external/drivers/gpu/utgard/mali/mali.ko:vendor/lib/modules/mali.ko
endif

endif # $(GRAPHIC_RENDER_TYPE)), CPU)

#End Graphic Module
PRODUCT_PACKAGES += mali.ko  gralloc.$(TARGET_BOARD_PLATFORM).so

#bbat test
PRODUCT_PACKAGES += autotestgps \
                    autotestsim \
                    autotesttcard

#add for engpc
PRODUCT_PACKAGES += libkeypadnpi \
                    liblcdnpi \
                    liblkvnpi \
                    libsensornpi \
                    libeng_tok \
                    libnpi_rtc \
                    libcharge \
                    libdloader \
                    libtsxrawdata

#add for engpc factory client
PRODUCT_PACKAGES += factory_cli \

# Camera configuration
#PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
camera.disable_zsl_mode=1

# open camera feature new panoram
PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.cam.wide.frame.gap=2

PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.cam.sfv.alter=true \
    persist.sys.cam.slow_motion=false

# media component modules
PRODUCT_PACKAGES += \
    libsprd_omx_core                     \
    libstagefrighthw                     \
    libstagefright_sprd_mp3dec           \
    libstagefright_sprd_mpeg4dec         \
    libstagefright_sprd_mpeg4enc         \
    libstagefright_sprd_h264dec          \
    libstagefright_sprd_h264enc          \
    libstagefright_sprd_vpxdec           \
    libstagefright_sprd_deintl           \
    libstagefright_soft_imaadpcmdec      \
    libstagefright_soft_mjpgdec          \
    libstagefright_sprd_mp3enc

# factorytest modules
PRODUCT_PACKAGES += \
    factorytest
# sensor
PRODUCT_PACKAGES += \
    android.hardware.sensors@1.0-service \
    android.hardware.sensors@1.0-impl

# Vibrator HAL
PRODUCT_PACKAGES += \
    android.hardware.vibrator@1.0-impl

include vendor/sprd/modules/devdrv/input/vibrator/vibdrv.mk

# GNSS
ifeq ($(strip $(SUPPORT_GNSS_HARDWARE)), true)
PRODUCT_PACKAGES += \
    vendor.sprd.hardware.gnss@2.0-service \
    vendor.sprd.hardware.gnss@2.0-impl
endif

# memtrack
PRODUCT_PACKAGES += \
    android.hardware.memtrack@1.0-impl

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rootdir/root/init.common.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.common.rc \
    $(LOCAL_PATH)/rootdir/root/init.ram.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.ram.rc \
    $(BOARDDIR)/recovery/init.recovery.$(TARGET_BOARD).rc:root/init.recovery.$(TARGET_BOARD).rc \
    $(LOCAL_PATH)/rootdir/system/usr/idc/adaptive_ts.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/adaptive_ts.idc \
    $(LOCAL_PATH)/rootdir/system/usr/keylayout/gpio-keys.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/gpio-keys.kl \
    $(LOCAL_PATH)/rootdir/system/usr/keylayout/adaptive_ts.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/adaptive_ts.kl \
    frameworks/native/data/etc/android.hardware.telephony.gsm.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.telephony.gsm.xml \
    frameworks/native/data/etc/android.hardware.touchscreen.multitouch.distinct.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.touchscreen.multitouch.distinct.xml \
#    frameworks/native/data/etc/android.hardware.vulkan.level-1.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.level-1.xml  \
#    frameworks/native/data/etc/android.hardware.vulkan.version-1_0_3.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.version.xml \
#    $(LOCAL_PATH)/rootdir/system/vendor/firmware/D5523E6G_5x46__V08_D01_20160226_app.bin:system/vendor/firmware/focaltech-FT5x46.bin \
#    frameworks/native/data/etc/android.hardware.touchscreen.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.touchscreen.xml \
#    frameworks/native/data/etc/android.hardware.usb.accessory.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.accessory.xml \
#    frameworks/native/data/etc/android.hardware.usb.host.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.host.xml \
#    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth_le.xml \
#    $(LOCAL_PATH)/rootdir/system/media/engtest_sample.pcm:system/media/engtest_sample.pcm \

# For the new devices shipped we would use go_handheld_core_hardware.xml and
# previously launched devices should continue using handheld_core_hardware.xml
ifeq ($(strip $(PRODUCT_GO_DEVICE)),true)
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/go_handheld_core_hardware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/handheld_core_hardware.xml
else
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/handheld_core_hardware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/handheld_core_hardware.xml
endif

#Add for factorytest/bbat/engpc
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rootdir/root/ueventd.common.rc:recovery/root/ueventd.$(TARGET_BOARD).rc \
    $(LOCAL_PATH)/recovery/recovery.tmpfsdata.fstab:recovery/root/etc/recovery.tmpfsdata.fstab

#init autotest.rc start
PRODUCT_COPY_FILES += vendor/sprd/proprietories-source/autotest/autotest.rc:vendor/etc/init/autotest.rc
#init autotest.rc end

# usb
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.usb.accessory.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.accessory.xml \
    frameworks/native/data/etc/android.software.midi.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.midi.xml

# otg host config
ifeq ($(strip $(TARGET_BOARD_SUPPORT_OTG)),true)
    PRODUCT_COPY_FILES += \
        frameworks/native/data/etc/android.hardware.usb.host.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.host.xml
endif

#engpc support cali mode when product have no modem
PRODUCT_PROPERTY_OVERRIDES += \
  ro.vendor.modem.support=1

# Disable uncompressing priv apps so that there is enough space to build the system partition.
DONT_UNCOMPRESS_PRIV_APPS_DEXS := true

# multimedia configs
    PRODUCT_COPY_FILES += \
    frameworks/av/media/libstagefright/data/media_codecs_google_telephony.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_telephony.xml \
    $(LOCAL_PATH)/media_codecs_google_video.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_video.xml \
    $(LOCAL_PATH)/media_codecs_google_audio.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_audio.xml \
    $(LOCAL_PATH)/media_codecs.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs.xml \
    $(LOCAL_PATH)/media_profiles.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_V1_0.xml \
    $(LOCAL_PATH)/media_profiles_turnkey.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_turnkey.xml \
    $(LOCAL_PATH)/media_codecs_performance.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance.xml\
    $(LOCAL_PATH)/seccomp_policy/mediacodec.policy:$(TARGET_COPY_OUT_VENDOR)/etc/seccomp_policy/mediacodec.policy \
    $(LOCAL_PATH)/seccomp_policy/mediaextractor.policy:$(TARGET_COPY_OUT_VENDOR)/etc/seccomp_policy/mediaextractor.policy

#audio effect configs;
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rootdir/system/etc/audio_effect/audio_effects.conf:$(TARGET_COPY_OUT_VENDOR)/etc/audio_effects.conf \
    $(LOCAL_PATH)/rootdir/system/etc/audio_effect/audio_effects.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_effects.xml

#ZRAM
ifeq ($(strip $(PRODUCT_GO_DEVICE)),true)
PRODUCT_COPY_FILES += \
	$(LOCAL_PATH)/fstab.enableswap_go:$(TARGET_COPY_OUT_VENDOR)/etc/fstab.enableswap
else
PRODUCT_COPY_FILES += \
	$(LOCAL_PATH)/fstab.enableswap:$(TARGET_COPY_OUT_VENDOR)/etc/fstab.enableswap
endif

#add for cts feature
    PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.cts.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.cts.xml

# copy cdrom resource file
PRODUCT_COPY_FILES += \
    vendor/sprd/resource/cdrom/adb.iso:vendor/etc/adb.iso

# Power Framework
PRODUCT_COPY_FILES += \
    vendor/sprd/modules/power/fw-power-config/power_info.db:system/etc/power_info.db \
    vendor/sprd/modules/power/fw-power-config/appPowerSaveConfig.xml:system/etc/appPowerSaveConfig.xml \
    vendor/sprd/modules/power/fw-power-config/blackAppList.xml:system/etc/blackAppList.xml \
    vendor/sprd/modules/power/fw-power-config/pwctl_config.xml:system/etc/pwctl_config.xml \
    vendor/sprd/modules/power/fw-power-config/powercontroller.xml:system/etc/powercontroller.xml \
    vendor/sprd/modules/power/fw-power-config/deviceidle.xml:system/etc/deviceidle.xml

# Add PowerSaveModeLauncher
TARGET_PWCTL_ULTRASAVING ?= true
ifeq (true,$(strip $(TARGET_PWCTL_ULTRASAVING)))
PRODUCT_PACKAGES += \
    PowerSaveModeLauncher
endif


#copy cgroup blkio resource file
ifeq ($(TARGET_BUILD_VARIANT),userdebug)
#PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rootdir/system/etc/init/cgroup_blkio.rc:system/etc/init/cgroup_blkio.rc
endif

#copy coredump resource file
ifneq ($(TARGET_BUILD_VARIANT),user)
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rootdir/system/etc/init/coredump.rc:system/etc/init/coredump.rc
endif

# EngineerMode modules
PRODUCT_PACKAGES += \
    EngineerMode \
	EngineerInternal \
    LogManager \
	ValidationTools \
        SprdAutoSlt \
	SystemUpdate \
	UASetting 

# SPRD WCN modules
#PRODUCT_PACKAGES += connmgr

PRODUCT_PACKAGES += \
    DreamFMRadio

# MultiMedia Apps
PRODUCT_PACKAGES +=  \
    NewGallery2 
#   SprdFMRadio

# Dream Camera
PRODUCT_PACKAGES += \
    DreamCamera2 \
    QuickCamera

# Network related modules
PRODUCT_PACKAGES += \
    dhcp6c \
    dhcp6s \
    radvd \
    tcpdump \
    ext_data \
    ip_monitor.sh \
    tiny_firewall.sh \
    data_rps.sh \
    netbox.sh

#copy thermal config file
PRODUCT_COPY_FILES += \
	$(LOCAL_PATH)/thermal.conf:$(TARGET_COPY_OUT_VENDOR)/etc/thermal.conf \
    $(LOCAL_PATH)/scenario.conf:system/etc/scenario.conf

PRODUCT_PACKAGES += \
    thermal.default \
    vendor.sprd.hardware.thermal@1.0 \
    vendor.sprd.hardware.thermal@1.0-impl

PRODUCT_PROPERTY_OVERRIDES += \
   persist.vendor.bsservice.enable=1

#HWUI
PRODUCT_PROPERTY_OVERRIDES += \
    ro.vendor.hwui.platform=1

#enable powerhint
PRODUCT_PROPERTY_OVERRIDES += \
	ro.vendor.powerhint.enable=1


# APR auto upload
PRODUCT_PROPERTY_OVERRIDES += \
	persist.sys.apr.intervaltime=1 \
	persist.sys.apr.testgroup=CSSLAB \
	persist.sys.apr.autoupload=1

# PDK prebuild apps
#include $(wildcard vendor/sprd/pdk_prebuilts/prebuilts.mk)

# add widevine build for androido
PRODUCT_PACKAGES += \
    android.hardware.drm@1.0-impl    \
    android.hardware.drm@1.0-service \
    android.hardware.drm@1.1-service.widevine \
    android.hardware.drm@1.1-service.clearkey

# add sprd browser
PRODUCT_PACKAGES += \
    SprdBrowser \
    SprdBrowserCustomAddon \
    SprdBrowserStorageAddon

# add drm for sprd browser
PRODUCT_PACKAGES += \
    BrowserXposed

# add oma drm for download&documentui
PRODUCT_PACKAGES += \
    DownloadProviderDrmAddon \
    DocumentsUIXposed

#dataLogDaemon
PRODUCT_PACKAGES += \
        dataLogDaemon

# add sprd email
PRODUCT_PACKAGES += \
    Email2 \
    Exchange2

# add sprd deskclock
PRODUCT_PACKAGES += \
    SprdDeskClock

# add sprd calendar
PRODUCT_PACKAGES += \
    SprdCalendar

# add sprd CalendarProvider
PRODUCT_PACKAGES += \
    SprdCalendarProvider

# add sprd quicksearchbox
PRODUCT_PACKAGES += \
    SprdQuickSearchBox

ifneq ($(strip $(wildcard vendor/sprd/modules/radiointeractor/Android.mk)),)
    PRODUCT_PACKAGES += radio_interactor_service
    PRODUCT_BOOT_JARS += radio_interactor_common
    PRODUCT_PACKAGES += radio_interactor_common
endif

# misc
PRODUCT_PACKAGES += tune2fs

#add ap deepsleep
PRODUCT_PACKAGES += libapdeepsleep

ifeq (true,$(strip $(TARGET_DM_VERITY)))
#$(call inherit-product, build/target/product/verity.mk)
#PRODUCT_SYSTEM_VERITY_PARTITION := /dev/block/platform/soc/soc:ap-ahb/20600000.sdio/by-name/system
endif

#use lmkd instead of lmfs since AndroidO MR1
#PRODUCT_PACKAGES += lmfs


# Vendor Interface Manifest
DEVICE_MANIFEST_FILE += $(PLATCOMM)/manifest_main.xml
ifeq ($(SIM_COUNT),1)
DEVICE_MANIFEST_FILE += $(PLATCOMM)/manifest_singlesim.xml
else
DEVICE_MANIFEST_FILE += $(PLATCOMM)/manifest_dualsim.xml
endif
DEVICE_MATRIX_FILE := $(PLATCOMM)/compatibility_matrix.xml

# GNSS
ifeq ($(strip $(SUPPORT_GNSS_HARDWARE)), true)
DEVICE_MANIFEST_FILE += $(PLATCOMM)/manifest_gnss.xml
endif

#add for csvt
PRODUCT_PACKAGES += libsprdstreamrecoder \
        libvtmanager

# For gator by Ken.Kuang
PRODUCT_PACKAGES += gatord \
		    gator.ko
PRODUCT_COPY_FILES += vendor/sprd/tools/gator/gatordstart:$(TARGET_COPY_OUT_VENDOR)/bin/gatordstart
#add for log
PRODUCT_PACKAGES +=  \
    vendor.sprd.hardware.log@1.0-impl \
    vendor.sprd.hardware.log@1.0 \
    vendor.sprd.hardware.log@1.0-service \
    srtd \
    ylog_common \
    log_service \
	vendor.sprd.hardware.aprd@1.0-impl \
	vendor.sprd.hardware.aprd@1.0-service
#add for connmgr hidl

PRODUCT_PACKAGES +=  \
    vendor.sprd.hardware.connmgr@1.0-impl \
    vendor.sprd.hardware.connmgr@1.0-service

#add for performance
PRODUCT_PACKAGES += performancemanager
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/sprd_performance_config.xml:/system/etc/sprd_performance_config.xml
#brightness
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/brightness/readme:/vendor/bin/power/backlight/readme

#para
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/para/readme:/vendor/bin/power/paras/readme
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/para/para.sh:/vendor/bin/power/paras/tool/para.sh

#interrupts
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/interrupts/readme:/vendor/bin/power/interrupt/readme

#fps
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/fps/readme:/vendor/bin/power/frame/readme
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/fps/fps_sf:/vendor/bin/power/frame/fps_sf
#top
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/top/readme:/vendor/bin/power/tops/readme

#cpu_gpu_ddr_loading
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/loading/loading.sh:/vendor/bin/power/loadings/tool/loading.sh
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/loading/readme:/vendor/bin/power/loadings/readme

#CPU
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/cpu/fix_freq/readme:/vendor/bin/power/cpu/fix_freq/readme
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/cpu/lit_freq/readme:/vendor/bin/power/cpu/lit_freq/readme
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/cpu/hotplug/readme:/vendor/bin/power/cpu/hotplug/readme
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/cpu/trans_table/readme:/vendor/bin/power/cpu/trans_table/readme
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/cpu/loading/readme:/vendor/bin/power/cpu/loading/readme
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/cpu/dvfs/readme:/vendor/bin/power/cpu/dvfs/readme

#GPU
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/gpu/fix_freq/readme:/vendor/bin/power/gpu/fix_freq/readme
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/gpu/loading/readme:/vendor/bin/power/gpu/loading/readme
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/gpu/trans_table/readme:/vendor/bin/power/gpu/trans_table/readme

#DDR
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/ddr/bm/readme:/vendor/bin/power/ddr/bm/readme
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/ddr/bm/cygwin1.dll:/vendor/bin/power/ddr/bm/cygwin1.dll
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/ddr/bm/pike2_perf_log.exe:/vendor/bin/power/ddr/bm/pike2_perf_log.exe
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/ddr/loading/readme:/vendor/bin/power/ddr/loading/readme
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/ddr/trans_table/readme:/vendor/bin/power/ddr/trans_table/readme
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/ddr/fix_freq/readme:/vendor/bin/power/ddr/fix_freq/readme

#total
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/total/readme:/vendor/bin/power/total/readme
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/power/total/tool/total.sh:/vendor/bin/power/total/tool/total.sh
$(warning  "common/DeviceCommon.mk: PRODUCT_PACKAGES:  $(PRODUCT_PACKAGES)")

PRODUCT_COPY_FILES += $(LOCAL_PATH)/temp_img/vbmeta-sign.img:$(PRODUCT_OUT)/vbmeta-gsi.img

#add for corefile enable
PRODUCT_PROPERTY_OVERRIDES += \
    ro.media.nocorefile.enabled=true

PRODUCT_PACKAGES += \
    libyuv_jpeg_converter_jni

#copy the tf model files to system/etc
TF_MODEL_PATH := vendor/sprd/platform/frameworks/base/data/etc/tf_models
model_files := $(shell ls $(TF_MODEL_PATH))
PRODUCT_COPY_FILES += $(foreach file, $(model_files), \
        $(TF_MODEL_PATH)/$(file):system/etc/tf_models/$(file))

#add for combined hal
ifeq ($(strip $(PRODUCT_GO_DEVICE)),true)
PRODUCT_PACKAGES +=  \
    vendor.sprd.hardware.combined@1.0-service
else
PRODUCT_PACKAGES +=  \
    android.hardware.light@2.0-service \
    android.hardware.vibrator@1.0-service \
    android.hardware.memtrack@1.0-service \
    vendor.sprd.hardware.thermal@1.0-service
endif

#copy hprofs rc file
ifneq ($(TARGET_BUILD_VARIANT),user)
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rootdir/system/etc/init/hprofs.rc:system/etc/init/hprofs.rc
endif
# engmod sensor AT test module
PRODUCT_PACKAGES +=  autotestsensorinfo

ifeq ($(strip $(FACEID_FEATURE_SUPPORT)),true)
PRODUCT_PACKAGES += \
    vendor.sprd.hardware.face@1.0-service \
    libfaceid_ca \
    faceid.elf

DEVICE_MANIFEST_FILE += $(PLATCOMM)/manifest_face.xml

PRODUCT_COPY_FILES += \
    vendor/sprd/interfaces/face/vendor.sprd.hardware.faceid.xml:vendor/etc/permissions/vendor.sprd.hardware.faceid.xml
endif

#init gms build
ifneq ($(wildcard vendor/partner_gms),)
  ifeq ($(strip $(TARGET_BUILD_VERSION)),gms)
    ifeq ($(strip $(PRODUCT_GO_DEVICE)),true)
      $(call inherit-product, vendor/partner_gms/products/gms_go.mk)
      PRODUCT_PROPERTY_OVERRIDES += \
              ro.com.google.clientidbase=android-google
    else
      $(call inherit-product, vendor/partner_gms/products/gms.mk)
      PRODUCT_PROPERTY_OVERRIDES += \
              ro.com.google.clientidbase=android-google
    endif
  endif
endif

#init Launcher build
ifneq ($(wildcard vendor/sprd/generic/misc/launchercfg),)
    $(call inherit-product, vendor/sprd/generic/misc/launchercfg/LauncherPackages.mk)
endif

#add for F2FS
ifeq (f2fs,$(strip $(BOARD_USERDATAIMAGE_FILE_SYSTEM_TYPE)))
TARGET_USERIMAGES_USE_F2FS := true
endif
