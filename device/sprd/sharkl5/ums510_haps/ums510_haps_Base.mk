#
# Copyright 2015 The Android Open-Source Project
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
#

PLATDIR := device/sprd/sharkl5
PLATCOMM := $(PLATDIR)/common
TARGET_BOARD := ums510_haps
BOARDDIR := $(PLATDIR)/$(TARGET_BOARD)
ROOTDIR := $(BOARDDIR)/rootdir
TARGET_BOARD_PLATFORM := ums510

# source code overlay for haps
SOURCE_CODE_OVERLAY := $(shell $(PLATDIR)/$(TARGET_BOARD)/source_code_overlay.sh)

TARGET_GPU_PLATFORM := rogue
TARGET_NO_BOOTLOADER := false

USE_AUDIO_WHALE_HAL :=true
USE_XML_AUDIO_POLICY_CONF := 1
SPRD_AUDIO_HIDL_CLIENT_SUPPORT := true
# use configurable audio policy
USE_CONFIGURABLE_AUDIO_POLICY :=1
USE_CUSTOM_PARAMETER_FRAMEWORK :=1
AUDIO_PFW_PATH := $(ROOTDIR)/system/etc/parameter-framework
pfw_rebuild_settings :=true
SPRD_PFW_CRITERIA_FILE := $(AUDIO_PFW_PATH)/policy_criteria.txt
SPRD_PFW_EDD_FILES := \
        $(AUDIO_PFW_PATH)/Settings/device_for_strategy_media.pfw \
        $(AUDIO_PFW_PATH)/Settings/device_for_strategy_phone.pfw \
        $(AUDIO_PFW_PATH)/Settings/device_for_strategy_sonification.pfw \
        $(AUDIO_PFW_PATH)/Settings/device_for_strategy_sonification_respectful.pfw \
        $(AUDIO_PFW_PATH)/Settings/device_for_strategy_dtmf.pfw \
        $(AUDIO_PFW_PATH)/Settings/device_for_strategy_enforced_audible.pfw \
        $(AUDIO_PFW_PATH)/Settings/device_for_strategy_transmitted_through_speaker.pfw \
        $(AUDIO_PFW_PATH)/Settings/device_for_strategy_accessibility.pfw \
        $(AUDIO_PFW_PATH)/Settings/device_for_strategy_rerouting.pfw \
        $(AUDIO_PFW_PATH)/Settings/strategy_for_stream.pfw \
        $(AUDIO_PFW_PATH)/Settings/strategy_for_usage.pfw \
        $(AUDIO_PFW_PATH)/Settings/device_for_input_source.pfw \
        $(AUDIO_PFW_PATH)/Settings/volumes.pfw \
        $(AUDIO_PFW_PATH)/Settings/device_for_strategy_fm.pfw

# pre opt for haps
WITH_DEXPREOPT_PIC := true
WITH_DEXPREOPT := true

# graphics
USE_SPRD_HWCOMPOSER  := true

# support gnss hidl
SUPPORT_GNSS_HARDWARE := false

$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base.mk)
$(call inherit-product, $(PLATCOMM)/DeviceCommon.mk)
$(call inherit-product, $(PLATCOMM)/proprietories.mk)
$(call inherit-product-if-exists, vendor/sprd/modules/libcamera/libcam_device.mk)

BOARD_HAVE_SPRD_WCN_COMBO := marlin3
$(call inherit-product-if-exists, vendor/sprd/modules/wcn/connconfig/device-sprd-wcn.mk)
$(call inherit-product-if-exists, vendor/sprd/modules/wlan/wlanconfig/device-sprd-wlan.mk)

#enable F2FS for userdata.img
BOARD_USERDATAIMAGE_FILE_SYSTEM_TYPE := f2fs

#fstab
ifeq (f2fs,$(strip $(BOARD_USERDATAIMAGE_FILE_SYSTEM_TYPE)))
  NORMAL_FSTAB_SUFFIX1 := .f2fs
endif
ifeq (true,$(strip $(BOARD_SECURE_BOOT_ENABLE)))
  NORMAL_FSTAB_SUFFIX2 :=
endif
NORMAL_FSTAB_SUFFIX := $(NORMAL_FSTAB_SUFFIX1)$(NORMAL_FSTAB_SUFFIX2)
# $(warning NORMAL_FSTAB=$(LOCAL_PATH)/rootdir/root/fstab$(NORMAL_FSTAB_SUFFIX).$(TARGET_BOARD))
PRODUCT_COPY_FILES += $(BOARDDIR)/rootdir/root/fstab.$(TARGET_BOARD)$(NORMAL_FSTAB_SUFFIX):vendor/etc/fstab.$(TARGET_BOARD)


PRODUCT_COPY_FILES += \
    $(BOARDDIR)/rootdir/root/init.$(TARGET_BOARD).rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.$(TARGET_BOARD).rc \
    $(ROOTDIR)/prodnv/PCBA.conf:$(TARGET_COPY_OUT_VENDOR)/etc/PCBA.conf \
    $(ROOTDIR)/prodnv/BBAT.conf:$(TARGET_COPY_OUT_VENDOR)/etc/BBAT.conf \
    $(ROOTDIR)/system/etc/audio_params/audio_config.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_config.xml \
    $(ROOTDIR)/system/etc/audio_params/audio_pcm.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_pcm.xml \
    $(ROOTDIR)/system/etc/audio_params/audio_route.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_route.xml \
    $(ROOTDIR)/system/etc/audio_params/sprd/audio_param_infor:$(TARGET_COPY_OUT_VENDOR)/etc/audio_params/sprd/audio_param_infor \
    $(ROOTDIR)/system/etc/audio_params/sprd/audioparam_config.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_params/sprd/audioparam_config.xml \
    $(ROOTDIR)/system/etc/audio_params/sprd/audio_pga.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_params/sprd/audio_pga.xml \
    $(ROOTDIR)/system/etc/audio_params/sprd/audio_process.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_params/sprd/audio_process.xml \
    $(ROOTDIR)/system/etc/audio_params/sprd/audio_structure.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_params/sprd/audio_structure.xml \
    $(ROOTDIR)/system/etc/audio_params/sprd/codec.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_params/sprd/codec.xml \
    $(ROOTDIR)/system/etc/audio_params/sprd/cvs.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_params/sprd/cvs.xml \
    $(ROOTDIR)/system/etc/audio_params/sprd/dsp_smartamp.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_params/sprd/dsp_smartamp_backup.xml \
    $(ROOTDIR)/system/etc/audio_params/sprd/dsp_vbc.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_params/sprd/dsp_vbc.xml \
    $(ROOTDIR)/system/etc/audio_params/sprd/audio_structure:$(TARGET_COPY_OUT_VENDOR)/etc/audio_params/sprd/audio_structure \
    $(ROOTDIR)/system/etc/audio_params/sprd/codec:$(TARGET_COPY_OUT_VENDOR)/etc/audio_params/sprd/codec \
    $(ROOTDIR)/system/etc/audio_params/sprd/cvs:$(TARGET_COPY_OUT_VENDOR)/etc/audio_params/sprd/cvs \
    $(ROOTDIR)/system/etc/audio_params/sprd/dsp_smartamp:$(TARGET_COPY_OUT_VENDOR)/etc/audio_params/sprd/dsp_smartamp_backup \
    $(ROOTDIR)/system/etc/audio_params/sprd/dsp_vbc:$(TARGET_COPY_OUT_VENDOR)/etc/audio_params/sprd/dsp_vbc \
    $(PLATCOMM)/rootdir/root/ueventd.common.rc:$(TARGET_COPY_OUT_VENDOR)/ueventd.rc \
    $(PLATCOMM)/rootdir/root/init.common.usb.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.$(TARGET_BOARD).usb.rc \
    frameworks/native/data/etc/android.hardware.camera.flash-autofocus.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.flash-autofocus.xml \
    frameworks/native/data/etc/android.hardware.camera.front.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.front.xml \
    frameworks/native/data/etc/android.hardware.opengles.aep.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.opengles.aep.xml \
    $(BOARDDIR)/log_conf/slog_modem_$(TARGET_BUILD_VARIANT).conf:vendor/etc/slog_modem.conf \
    $(BOARDDIR)/log_conf/slog_modem_cali.conf:vendor/etc/slog_modem_cali.conf \
    $(BOARDDIR)/log_conf/slog_modem_factory.conf:vendor/etc/slog_modem_factory.conf \
    $(BOARDDIR)/log_conf/slog_modem_autotest.conf:vendor/etc/slog_modem_autotest.conf \
    $(BOARDDIR)/log_conf/mlogservice_$(TARGET_BUILD_VARIANT).conf:vendor/etc/mlogservice.conf

#copy audio policy config
ifeq ($(USE_XML_AUDIO_POLICY_CONF), 1)
PRODUCT_COPY_FILES += \
    $(ROOTDIR)/system/etc/audio_policy_config/audio_policy_configuration_generic.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_configuration.xml \
    $(ROOTDIR)/system/etc/audio_policy_config/primary_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/primary_audio_policy_configuration.xml \
    $(ROOTDIR)/system/etc/audio_policy_config/a2dp_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/a2dp_audio_policy_configuration.xml \
    $(ROOTDIR)/system/etc/audio_policy_config/usb_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/usb_audio_policy_configuration.xml \
    $(ROOTDIR)/system/etc/audio_policy_config/audio_policy_volumes.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_volumes.xml \
    $(ROOTDIR)/system/etc/audio_policy_config/default_volume_tables.xml:$(TARGET_COPY_OUT_VENDOR)/etc/default_volume_tables.xml
else
PRODUCT_COPY_FILES += \
    $(ROOTDIR)/system/etc/audio_params/audio_policy.conf:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy.conf
endif

# config sepolicy
#duplicate definition
#BOARD_SEPOLICY_DIRS += device/sprd/sharkl5/common/sepolicy \
#    build/target/board/generic/sepolicy

SPRD_GNSS_ARCH := arm64
# GNSS
ifeq ($(strip $(SUPPORT_GNSS_HARDWARE)), true)
SPRD_GNSS_SHARKLE_PIKL2 := true
$(call inherit-product, vendor/sprd/modules/gps/gnsshal/device-sprd-gps.mk)
endif

#WCN config
PRODUCT_PROPERTY_OVERRIDES += \
    ro.vendor.modem.wcn.enable=1 \
    ro.vendor.modem.wcn.diag=/dev/slog_wcn0 \
    ro.vendor.modem.wcn.id=1 \
    ro.vendor.modem.wcn.count=1 \
    ro.vendor.modem.gnss.diag=/dev/slog_gnss \
    ro.vendor.wcn.gpschip=marlin3

#Display/Graphic config
PRODUCT_PROPERTY_OVERRIDES += \
      ro.sf.lcd_density=400 \
      ro.vendor.sf.lcd_width=54 \
      ro.vendor.sf.lcd_height=96 \
      ro.opengles.version=196610

# Dual-sim config
PRODUCT_PACKAGES += \
        Stk1 \
        MsmsStk

# Volte config
PRODUCT_PACKAGES += \
        ims

# Screen Capture
PRODUCT_PACKAGES += \
        ScreenCapture

# enable ims/modem_vpad
PRODUCT_PROPERTY_OVERRIDES += persist.vendor.sys.volte.enable=true

# config video engine for VOLTE video call
VOLTE_SERVICE_ENABLE := volte_vowifi_shared
ifeq ($(strip $(VOLTE_SERVICE_ENABLE)), volte_vowifi_shared)
PRODUCT_PROPERTY_OVERRIDES += persist.sys.vilte.socket=ap
endif

# sprd hw module
PRODUCT_PACKAGES += \
	lights.$(TARGET_BOARD_PLATFORM) \
	sensors.$(TARGET_BOARD_PLATFORM) \
	tinymix \
	audio_hardware_test \
	camera.$(TARGET_BOARD_PLATFORM) \
	power.$(TARGET_BOARD_PLATFORM) \
	memtrack.$(TARGET_BOARD_PLATFORM)


ifeq ($(strip $(SUPPORT_GNSS_HARDWARE)), true)
#PRODUCT_PACKAGES += gnss_download
endif

#audio vbc_eq
PRODUCT_PACKAGES += audio_vbc_eq \
                    libaudionpi \
                    libaudioparamteser \
                    libpolicy-subsystem

#SPRD_NTFS_SUPPORT := true
ifeq ($(strip $(SPRD_NTFS_SUPPORT)), true)
PRODUCT_PACKAGES += ntfs3g \
                    ntfsfix

endif

#SANSA|SPRD|NONE
#PRODUCT_SECURE_BOOT := NONE
#PRODUCT_PACKAGES += imgheaderinsert \
                    packimage.sh \
                    FMRadio \
                    #libGLES_android \
                    gralloc.$(TARGET_BOARD_PLATFORM)

#faceid feature
FACEID_FEATURE_SUPPORT := false

#PRODUCT_PACKAGES += iwnpi \
		    libiwnpi \
		    libwifieut_m3 \
		    sprdwl_ng.ko

#camera filter and beauty
PRODUCT_PROPERTY_OVERRIDES += persist.vendor.cam.facebeauty.corp=2

#enable blur mode
PRODUCT_PROPERTY_OVERRIDES += \
    persist.vendor.cam.fr.blur.version=1 \
    persist.vendor.cam.blur.cov.id=3

#enable faceID
TARGET_BOARD_FACE_UNLOCK_SUPPORT := false

#faceid version    0--disable  1--single_camera  2--dual_camera
PRODUCT_PROPERTY_OVERRIDES += \
    persist.vendor.cam.faceid.version=0

# config sensor features supported by this board
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.sensor.proximity.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.proximity.xml \
    frameworks/native/data/etc/android.hardware.sensor.light.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.light.xml \
    frameworks/native/data/etc/android.hardware.sensor.accelerometer.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.accelerometer.xml \
    frameworks/native/data/etc/android.hardware.sensor.compass.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.compass.xml

PRODUCT_DEXPREOPT_SPEED_APPS += \
    SystemUI \



# remove packages for haps
PACKAGES_TO_REMOVE := SGPS \
    factorytest \
    android.hardware.camera.provider@2.4-impl-sprd \
    android.hardware.camera.provider@2.4-service \
    EngineerMode \
    EngineerInternal \
    SprdMediaProvider \
    DreamSoundRecorder \
    NewMusic \
    SprdBrowser \
    SprdBrowserCustomAddon \
    SprdBrowserStorageAddon \
    BrowserXposed \
    DownloadProviderDrmAddon \
    DocumentsUIXposed \
    dataLogDaemon \
    Email2 \
    Exchange2 \
    SprdCalendar \
    SprdCalendarProvider \
    SprdDeskClock \
    TouchPalGlobal \
    VceDaemon

$(warning  "target haps PACKAGES_TO_REMOVE:  $(PACKAGES_TO_REMOVE)")
