# Copyright (C) 2016 The Android Open Source Project
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

LOCAL_PATH := device/sprd/roc1/common
ROOTCOMM := $(LOCAL_PATH)/rootdir
include $(LOCAL_PATH)/ModemCommon.mk
include $(LOCAL_PATH)/TelephonyCommon.mk
include $(wildcard $(LOCAL_PATH)/common_packages.mk)
include $(LOCAL_PATH)/emmc/emmc_device.mk

#BOARD_VNDK_VERSION := current
OMA_DRM := true

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

#PRODUCT_PACKAGES += \
    SGPS

PRODUCT_PACKAGES += \
    libnfctest \
    libgpio \
    engpc \

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

# Sensor HAL
PRODUCT_PACKAGES += \
    android.hardware.sensors@1.0-impl

# vndk
PRODUCT_PACKAGES += \
    vndk_package

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/vndk/system.public.libraries-sprd.txt:system/etc/public.libraries-sprd.txt \
    $(LOCAL_PATH)/vndk/vendor.public.libraries.txt:vendor/etc/public.libraries.txt

# Power hidl HAL
PRODUCT_PACKAGES += \
    vendor.sprd.hardware.power@3.0-impl \
    vendor.sprd.hardware.power@3.0-service

#aosp power hidl service
#PRODUCT_PACKAGES += \
    android.hardware.power@1.0-impl \
    android.hardware.power@1.0-service


PRODUCT_PROPERTY_OVERRIDES += \
    ro.vendor.gnsschip=ge2

# Health HAL
PRODUCT_PACKAGES += \
    android.hardware.health@1.0-impl

# Light HAL
PRODUCT_PACKAGES += \
    android.hardware.light@2.0-impl

# Keymaster HAL
PRODUCT_PACKAGES += \
    android.hardware.keymaster@3.0-impl \
    android.hardware.keymaster@3.0-service

# Bluetooth HAL
#PRODUCT_PACKAGES += \
#    libbt-vendor \
#    android.hardware.bluetooth@1.0-impl

# RenderScript HAL
PRODUCT_PACKAGES += \
     android.hardware.renderscript@1.0-impl

#add USB vts service
PRODUCT_PACKAGES += \
    android.hardware.usb@1.1-service

# Dumpstate service
PRODUCT_PACKAGES += \
    android.hardware.dumpstate@1.0-service

# add  treble enable
PRODUCT_PROPERTY_OVERRIDES += \
ro.treble.enabled = true

# use PRODUCT_SHIPPING_API_LEVEL indicates the first api level,and contorl treble macro
#PRODUCT_SHIPPING_API_LEVEL := 28

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

#poweroff charge
PRODUCT_PACKAGES += charge \
                    phasecheckserver
#PowerHint HAL
# sprdemand, interactive
BOARD_POWERHINT_HAL := interactive

# Power hint config file
PRODUCT_PACKAGES += \
    power_scene_id_define.txt \
    power_scene_config.xml \
    power_resource_file_info.xml \
    libpowerhal_cli

# ION HAL module
PRODUCT_PACKAGES += \
    libmemion
# ION module
PRODUCT_PACKAGES += \
    libion
#MAP USER
PRODUCT_PACKAGES += \
    libmapuser

# Camera configuration
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
camera.disable_zsl_mode=1

# media component modules
PRODUCT_PACKAGES += \
    libsprd_omx_core                     \
    libstagefrighthw                     \
    libstagefright_sprd_h264dec          \
    libstagefright_sprd_h264enc          \
    libstagefright_sprd_mpeg4dec         \
    libstagefright_sprd_mp3dec           \
    libstagefright_soft_imaadpcmdec      \
    libstagefright_soft_mjpgdec          \
    libstagefright_sprd_mp3enc

# factorytest modules
#PRODUCT_PACKAGES += \
	factorytest

# sensor
PRODUCT_PACKAGES += \
    android.hardware.sensors@1.0-service \
    android.hardware.sensors@1.0-impl

# Vibrator HAL
PRODUCT_PACKAGES += \
    android.hardware.vibrator@1.0-impl

PRODUCT_PACKAGES += \
    vibrator.$(TARGET_BOARD_PLATFORM)

# Memtack HAL
PRODUCT_PACKAGES += \
    android.hardware.memtrack@1.0-impl

ifeq ($(strip $(SUPPORT_GNSS_HARDWARE)), true)
PRODUCT_PACKAGES += \
    vendor.sprd.hardware.gnss@2.0-service \
    vendor.sprd.hardware.gnss@2.0-impl
endif

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rootdir/root/init.common.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.common.rc \
    $(BOARDDIR)/recovery/init.recovery.$(TARGET_BOARD).rc:root/init.recovery.$(TARGET_BOARD).rc \
    $(LOCAL_PATH)/rootdir/system/usr/keylayout/gpio-keys.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/gpio-keys.kl \
    $(LOCAL_PATH)/rootdir/system/usr/keylayout/adaptive_ts.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/adaptive_ts.kl \
    $(LOCAL_PATH)/rootdir/system/usr/keylayout/adaptive_ts.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/synaptics_dsx.kl \
    $(LOCAL_PATH)/rootdir/system/usr/keylayout/focaltech_ts.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/focaltech_ts.kl \
    $(LOCAL_PATH)/rootdir/system/usr/keylayout/msg2138_ts.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/msg2138_ts.kl \
    frameworks/native/data/etc/android.hardware.touchscreen.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.touchscreen.xml \
    frameworks/native/data/etc/android.hardware.touchscreen.multitouch.distinct.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.touchscreen.multitouch.distinct.xml \
    frameworks/native/data/etc/android.hardware.usb.accessory.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.accessory.xml \
    frameworks/native/data/etc/android.software.midi.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.midi.xml \
    frameworks/native/data/etc/android.hardware.usb.host.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.host.xml \
    frameworks/native/data/etc/android.hardware.telephony.gsm.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.telephony.gsm.xml \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth_le.xml \
    frameworks/native/data/etc/android.hardware.opengles.aep.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.opengles.aep.xml \
    $(LOCAL_PATH)/rootdir/system/usr/idc/adaptive_ts.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/adaptive_ts.idc \
    $(LOCAL_PATH)/rootdir/system/usr/idc/adaptive_ts.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/synaptics_dsx.idc \
    $(LOCAL_PATH)/rootdir/system/usr/idc/focaltech_ts.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/focaltech_ts.idc \
    $(LOCAL_PATH)/rootdir/system/usr/idc/msg2138_ts.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/msg2138_ts.idc \
    $(LOCAL_PATH)/handheld_core_hardware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/handheld_core_hardware.xml
#    $(LOCAL_PATH)/rootdir/system/vendor/firmware/D5523E6G_5x46__V08_D01_20160226_app.bin:system/vendor/firmware/focaltech-FT5x46.bin \

PRODUCT_COPY_FILES += $(LOCAL_PATH)/temp_img/vbmeta-sign.img:$(PRODUCT_OUT)/vbmeta-gsi.img

# graphics modules
$(warning, TARGET_GPU_PLATFORM=$(TARGET_GPU_PLATFORM))

ifeq ($(strip $(TARGET_GPU_PLATFORM)), soft)
PRODUCT_PACKAGES +=  \
        libEGL       \
        libGLESv1_CM \
        libGLESv2 \
        libGLES_android \
		gralloc.$(TARGET_BOARD_PLATFORM).so \
	    android.hardware.graphics.mapper@2.0-impl \
	    android.hardware.graphics.allocator@2.0-impl \
	    android.hardware.graphics.allocator@2.0-service \
	    android.hardware.graphics.composer@2.1-impl \
	    android.hardware.graphics.composer@2.1-service

#ifeq ($(strip $(USE_SPRD_HWCOMPOSER)),true)
#$(error, USE_SPRD_HWCOMPOSER should not be true)
#endif

USE_SPRD_HWCOMPOSER:= true
PRODUCT_PACKAGES +=  hwcomposer.$(TARGET_BOARD_PLATFORM)

else # $(TARGET_GPU_PLATFORM)), soft)
ifeq ($(strip $(TARGET_GPU_PLATFORM)), midgard)
PRODUCT_PACKAGES += mali.ko gralloc.$(TARGET_BOARD_PLATFORM).so
PRODUCT_PACKAGES += \
	libGLES_android \
    libGLES_mali_64.so \
    libGLES_mali.so \
    libRSDriverArm_64.so \
    libRSDriverArm.so \
    libbccArm_64.so \
    libbccArm.so \
    libmalicore_64.bc \
    libmalicore.bc \
    libhwc2on1adapter.so

OVERRIDE_RS_DRIVER := libRSDriverArm.so
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.gpu.boost=0
endif # $(TARGET_GPU_PLATFORM)), midgard)

ifeq ($(strip $(TARGET_GPU_PLATFORM)), rogue)
PRODUCT_PACKAGES += \
    pvrdebug \
    pvrhwperf \
    pvrlogdump \
    pvrlogsplit \
    pvrtld \
    pvrhtb2txt \
    pvrhtbd \
    libdrm \
    rscompiler \
    libadf \
    libadfhwc \
    rgx.fw.signed.22.86.104.218 \
    libEGL_POWERVR_ROGUE.so \
    libGLESv1_CM_POWERVR_ROGUE.so \
    libGLESv2_POWERVR_ROGUE.so \
    libcreatesurface.so \
    libglslcompiler.so \
    libIMGegl.so \
    libpvrANDROID_WSEGL.so \
    libPVRScopeServices.so \
    libsrv_um.so \
    libusc.so \
    memtrack.generic.so \
    libPVRRS.so \
    libPVRRS.sha1.so \
    gralloc.$(TARGET_BOARD_PLATFORM).so \
    libhwc2on1adapter.so \
    libsutu_display.so

PRODUCT_PACKAGES += pvrsrvkm.ko
PRODUCT_COPY_FILES += \
    vendor/sprd/external/drivers/gpu/rogue/driver/build/linux/sprd_android/pvrsrvkm.ko:root/lib/modules/pvrsrvkm.ko

OVERRIDE_RS_DRIVER := libPVRRS.so
endif # $(TARGET_GPU_PLATFORM)), rogue)

PRODUCT_PACKAGES += \
    android.hardware.graphics.mapper@2.0-impl \
    android.hardware.graphics.allocator@2.0-impl \
    android.hardware.graphics.allocator@2.0-service \
    android.hardware.graphics.composer@2.1-impl \
    android.hardware.graphics.composer@2.1-service

#PRODUCT_PACKAGES += \
    android.hardware.camera.provider@2.4-impl-sprd \
    android.hardware.camera.provider@2.4-service

ifeq ($(strip $(USE_SPRD_HWCOMPOSER)),true)
$(warning, if sprd hwcomposer is not ready, USE_SPRD_HWCOMPOSER should not be true)
PRODUCT_PACKAGES +=  hwcomposer.$(TARGET_BOARD_PLATFORM)
endif

endif # $(TARGET_GPU_PLATFORM)), soft)

# for soft gpu
PRODUCT_PACKAGES += libdrm

#End Graphic Module

#bbat test
PRODUCT_PACKAGES += autotestgpio \
                    autotestsensorinfo \
                    autotestgps \
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

# multimedia configs
PRODUCT_COPY_FILES += \
    frameworks/av/media/libstagefright/data/media_codecs_google_telephony.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_telephony.xml \
    $(LOCAL_PATH)/media_codecs_google_video.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_video.xml \
    $(LOCAL_PATH)/media_codecs_google_audio.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_audio.xml \
    $(LOCAL_PATH)/media_codecs.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs.xml \
    $(LOCAL_PATH)/media_profiles.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_V1_0.xml \
    $(LOCAL_PATH)/media_profiles_turnkey.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_turnkey.xml \
    $(LOCAL_PATH)/media_codecs_performance.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance.xml \
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

#copy cdrom resource file
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

#Add PowerSaveModeLauncher
TARGET_PWCTL_ULTRASAVING ?= true
ifeq (true,$(strip $(TARGET_PWCTL_ULTRASAVING)))
PRODUCT_PACKAGES += \
    PowerSaveModeLauncher
endif

#copy cgroup blkio resource file
ifeq ($(TARGET_BUILD_VARIANT),userdebug)
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rootdir/system/etc/init/cgroup_blkio.rc:system/etc/init/cgroup_blkio.rc
endif

#copy coredump resource file
ifneq ($(TARGET_BUILD_VARIANT),user)
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rootdir/system/etc/init/coredump.rc:system/etc/init/coredump.rc
endif

# SPRD WCN modules
PRODUCT_PACKAGES += connmgr
# EngineerMode modules
#PRODUCT_PACKAGES += \
    EngineerMode \
    EngineerInternal

# MultiMedia Apps
#PRODUCT_PACKAGES += \
    SprdMediaProvider \
    DreamSoundRecorder \
    NewMusic

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
    vendor.sprd.hardware.thermal@1.0-impl \
    vendor.sprd.hardware.thermal@1.0-service

PRODUCT_PROPERTY_OVERRIDES += \
   persist.vendor.bsservice.enable=1

# APR auto upload
PRODUCT_PROPERTY_OVERRIDES += \
	persist.sys.apr.intervaltime=1 \
	persist.sys.apr.testgroup=CSSLAB \
	persist.sys.apr.autoupload=1


PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.heartbeat.enable=1

# PDK prebuild apps
#include $(wildcard vendor/sprd/pdk_prebuilts/prebuilts.mk)

# add widevine build for androidp
PRODUCT_PACKAGES += \
    android.hardware.drm@1.0-impl    \
    android.hardware.drm@1.0-service \
    android.hardware.drm@1.1-service.widevine \
    android.hardware.drm@1.1-service.clearkey

#add sprd Contacts
#PRODUCT_PACKAGES += \
    SprdContacts \
    SprdContactsProvider \
    ContactsBlackListAddon \
    ContactsDefaultContactAddon \
    ContactsEFDisplayAddon \
    EFDisplaySupportAddon \
    FastScrollBarSupportAddon

# add sprd browser
#PRODUCT_PACKAGES += \
    SprdBrowser \
    SprdBrowserCustomAddon \
    SprdBrowserStorageAddon

# add drm for sprd browser
#PRODUCT_PACKAGES += \
    BrowserXposed

# add oma drm for download&documentui
#PRODUCT_PACKAGES += \
    DownloadProviderDrmAddon \
    DocumentsUIXposed

#dataLogDaemon
#PRODUCT_PACKAGES += \
        dataLogDaemon

# add sprd email
#PRODUCT_PACKAGES += \
    Email2 \
    Exchange2

# add sprd calendar
#PRODUCT_PACKAGES += \
    SprdCalendar

# add sprd CalendarProvider
#PRODUCT_PACKAGES += \
    SprdCalendarProvider

# add sprd quicksearchbox
#PRODUCT_PACKAGES += \
    SprdQuickSearchBox

# add sprd deskclock
#PRODUCT_PACKAGES += \
    SprdDeskClock

# misc
PRODUCT_PACKAGES += tune2fs

PRODUCT_PACKAGES += libapdeepsleep

ifneq ($(strip $(wildcard vendor/sprd/modules/radiointeractor/Android.mk)),)
    PRODUCT_PACKAGES += radio_interactor_service
    PRODUCT_BOOT_JARS += radio_interactor_common
    PRODUCT_PACKAGES += radio_interactor_common
endif

# Set default USB interface
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    persist.sys.usb.config=ptp

PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    sys.usb.controller=musb-hdrc.0.auto

#use lmkd instead of lmfs since AndroidO MR1
#PRODUCT_PACKAGES += lmfs

ifneq ($(strip $(PRODUCT_GO_DEVICE)),true)
PRODUCT_REVISION += multiuser
endif

# add for sprd super resultion and picture quality feature
PRODUCT_PACKAGES += ColorTemperatureAdjusting

# add for sprd super resultion and picture quality feature
PRODUCT_PACKAGES += LowResolutionPowerSaving

#add for EIS enable
PRODUCT_PROPERTY_OVERRIDES += \
    ro.media.recoderEIS.enabled=true

# For gator by Ken.Kuang
PRODUCT_PACKAGES += gatord \
		    gator.ko
PRODUCT_COPY_FILES += vendor/sprd/tools/gator/gatordstart:$(TARGET_COPY_OUT_VENDOR)/bin/gatordstart

DEVICE_MANIFEST_FILE += $(PLATCOMM)/manifest_main.xml
ifeq ($(SIM_COUNT),1)
DEVICE_MANIFEST_FILE += $(PLATCOMM)/manifest_singlesim.xml
else
DEVICE_MANIFEST_FILE += $(PLATCOMM)/manifest_dualsim.xml
endif
DEVICE_MATRIX_FILE := $(PLATCOMM)/compatibility_matrix.xml
ifeq ($(strip $(SUPPORT_GNSS_HARDWARE)), true)
DEVICE_MANIFEST_FILE += $(PLATCOMM)/manifest_gnss.xml
endif

#add for performance
PRODUCT_PACKAGES += performancemanager
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/sprd_performance_config.xml:/system/etc/sprd_performance_config.xml

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rootdir/root/ueventd.common.rc:recovery/root/ueventd.$(TARGET_BOARD).rc \
    $(LOCAL_PATH)/recovery/recovery.tmpfsdata.fstab:recovery/root/etc/recovery.tmpfsdata.fstab

$(warning  "common/DeviceCommon.mk: PRODUCT_PACKAGES:  $(PRODUCT_PACKAGES)")


#fs doesn't have HEH filename encryption
PRODUCT_PROPERTY_OVERRIDES += \
    ro.crypto.volume.filenames_mode=aes-256-cts

#add for log
PRODUCT_PACKAGES +=  \
    vendor.sprd.hardware.log@1.0-impl \
    vendor.sprd.hardware.log@1.0 \
    vendor.sprd.hardware.log@1.0-service \
    srtd \
    log_service \
    vendor.sprd.hardware.aprd@1.0-impl \
    vendor.sprd.hardware.aprd@1.0-service

ifeq ($(strip $(FACEID_FEATURE_SUPPORT)),true)
PRODUCT_PACKAGES += \
    vendor.sprd.hardware.face@1.0-service

DEVICE_MANIFEST_FILE += $(PLATCOMM)/manifest_face.xml

PRODUCT_COPY_FILES += \
    vendor/sprd/interfaces/face/vendor.sprd.hardware.faceid.xml:vendor/etc/permissions/vendor.sprd.hardware.faceid.xml
endif

#add for force speed compile
PRODUCT_DEXPREOPT_SPEED_APPS += \
    NewGallery2 \
    SprdCalendar 

#add for connmgr hidl
PRODUCT_PACKAGES +=  \
    vendor.sprd.hardware.connmgr@1.0-impl \
    vendor.sprd.hardware.connmgr@1.0-service

PRODUCT_PACKAGES += \
    libyuv_jpeg_converter_jni

#for gms
versiontype :=native
ifeq (vendor/google, $(wildcard vendor/google))
versiontype :=gms
else
    ifeq (vendor/partner_gms, $(wildcard vendor/partner_gms))
        versiontype :=gms
    endif
endif
$(warning  "common/DeviceCommon.mk: version type: $(versiontype)")
ifeq ($(versiontype),gms)
PRODUCT_COPY_FILES += $(LOCAL_PATH)/rootdir/root/init.ram.gms.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.ram.rc
else
PRODUCT_COPY_FILES += $(LOCAL_PATH)/rootdir/root/init.ram.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.ram.rc
endif

#add for combined hal
ifeq ($(strip $(PRODUCT_GO_DEVICE)),true)
PRODUCT_PACKAGES +=  \
    vendor.sprd.hardware.combined@1.0-service
else
PRODUCT_PACKAGES +=  \
    android.hardware.health@2.0-service \
    android.hardware.light@2.0-service \
    android.hardware.vibrator@1.0-service \
    android.hardware.memtrack@1.0-service
endif
include vendor/sprd/modules/devdrv/input/leds/leddrv.mk
include vendor/sprd/modules/devdrv/input/vibrator/vibdrv.mk

#init autotest.rc start
PRODUCT_COPY_FILES += vendor/sprd/proprietories-source/autotest/autotest.rc:vendor/etc/init/autotest.rc
#init autotest.rc end

#engpc support cali mode when product have no modem
PRODUCT_PROPERTY_OVERRIDES += \
  ro.vendor.modem.support=1

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

# config gallery discover module on non-go device
ifneq ($(strip $(PRODUCT_GO_DEVICE)),true)
#0 means not support
#1 means tfl_inception_v3_quant
#2 means tfl_mnasnet_1.3_224 float
TARGET_BOARD_GALLERY_DISCOVER_SUPPORT := 2
PRODUCT_PROPERTY_OVERRIDES += persist.sys.gallery.discover.module=$(TARGET_BOARD_GALLERY_DISCOVER_SUPPORT)
ifneq ($(strip $(TARGET_BOARD_GALLERY_DISCOVER_SUPPORT)),0)
#USCAIEngine
PRODUCT_PACKAGES += \
        USCAIEngine
$(call inherit-product, vendor/sprd/platform/frameworks/base/data/etc/models_config.mk)
endif # $(TARGET_BOARD_GALLERY_DISCOVER_SUPPORT)
endif # $(PRODUCT_GO_DEVICE)

#init Launcher build
ifneq ($(wildcard vendor/sprd/generic/misc/launchercfg),)
    $(call inherit-product, vendor/sprd/generic/misc/launchercfg/LauncherPackages.mk)
endif
