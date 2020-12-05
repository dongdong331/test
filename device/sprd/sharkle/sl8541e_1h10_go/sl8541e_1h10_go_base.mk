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

PLATDIR := device/sprd/sharkle
PLATCOMM := $(PLATDIR)/common
TARGET_BOARD := sl8541e_1h10_go
BOARDDIR := $(PLATDIR)/$(TARGET_BOARD)
ROOTDIR := $(BOARDDIR)/rootdir
TARGET_BOARD_PLATFORM := sp9832e

TARGET_GPU_PLATFORM := midgard
TARGET_NO_BOOTLOADER := false

USE_XML_AUDIO_POLICY_CONF := 1
SPRD_AUDIO_HIDL_CLIENT_SUPPORT := true
# support gnss hidl
SUPPORT_GNSS_HARDWARE := true

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

#Config Android Render With CPU; Default Android render with GPU
#GRAPHIC_RENDER_TYPE    := CPU

# graphics
USE_SPRD_HWCOMPOSER  := true

#$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
ifeq ($(strip $(PRODUCT_GO_DEVICE)),true)
PRODUCT_PROPERTY_OVERRIDES := \
    ro.config.ringtone=Ring_Synth_04.ogg \
    ro.config.ringtone0=Ring_Synth_04.ogg \
    ro.config.ringtone1=Ring_Synth_02.ogg \
    ro.config.default_message=Argon.ogg \
    ro.config.default_message0=Argon.ogg \
    ro.config.default_message1=Highwire.ogg \
    ro.config.notification_sound=pixiedust.ogg \
    ro.config.alarm_alert=Oxygen.ogg
# Get some sounds
$(call inherit-product-if-exists, frameworks/base/data/sounds/AudioPackageGo.mk)
# Get the TTS language packs
$(call inherit-product-if-exists, external/svox/pico/lang/all_pico_languages.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic.mk)
else
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base.mk)
endif
$(call inherit-product, $(PLATCOMM)/DeviceCommon.mk)
$(call inherit-product, $(PLATCOMM)/proprietories.mk)
$(call inherit-product-if-exists, vendor/sprd/modules/libcamera/libcam_device.mk)


BOARD_HAVE_SPRD_WCN_COMBO := sharkle
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
    $(ROOTDIR)/prodnv/BBAT.conf:$(TARGET_COPY_OUT_VENDOR)/etc/BBAT.conf \
    $(ROOTDIR)/system/etc/audio_params/tiny_hw.xml:$(TARGET_COPY_OUT_VENDOR)/etc/tiny_hw.xml \
    $(ROOTDIR)/system/etc/audio_params/codec_pga.xml:$(TARGET_COPY_OUT_VENDOR)/etc/codec_pga.xml \
    $(ROOTDIR)/system/etc/audio_params/audio_hw.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_hw.xml \
    $(ROOTDIR)/system/etc/audio_params/audio_para:$(TARGET_COPY_OUT_VENDOR)/etc/audio_para \
    $(ROOTDIR)/system/etc/audio_params/smart_amp_init.bin:$(TARGET_COPY_OUT_VENDOR)/etc/smart_amp_init.bin \
    $(ROOTDIR)/system/etc/audio_params/record_tone_1.pcm:$(TARGET_COPY_OUT_VENDOR)/etc/record_tone_1.pcm \
    $(ROOTDIR)/system/etc/audio_params/record_tone_2.pcm:$(TARGET_COPY_OUT_VENDOR)/etc/record_tone_2.pcm \
    $(ROOTDIR)/system/etc/audio_params/rx_data.pcm:$(TARGET_COPY_OUT_VENDOR)/etc/rx_data.pcm \
    $(PLATCOMM)/rootdir/root/ueventd.common.rc:$(TARGET_COPY_OUT_VENDOR)/ueventd.rc \
    $(PLATCOMM)/rootdir/root/init.common.usb.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.$(TARGET_BOARD).usb.rc \
    frameworks/native/data/etc/android.hardware.camera.flash-autofocus.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.flash-autofocus.xml \
    frameworks/native/data/etc/android.hardware.camera.front.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.front.xml \
    frameworks/native/data/etc/android.hardware.opengles.aep.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.opengles.aep.xml \
    $(BOARDDIR)/log_conf/slog_modem_$(TARGET_BUILD_VARIANT).conf:vendor/etc/slog_modem.conf \
    $(BOARDDIR)/log_conf/slog_modem_cali.conf:vendor/etc/slog_modem_cali.conf \
    $(BOARDDIR)/log_conf/slog_modem_factory.conf:vendor/etc/slog_modem_factory.conf \
    $(BOARDDIR)/log_conf/slog_modem_autotest.conf:vendor/etc/slog_modem_autotest.conf \
    $(BOARDDIR)/log_conf/mlogservice_$(TARGET_BUILD_VARIANT).conf:vendor/etc/mlogservice.conf \
    $(BOARDDIR)/features/otpdata/otpgoldendata.txt:system/etc/otpdata/otpgoldendata.txt \
    $(BOARDDIR)/features/otpdata/input_parameters_values.txt:system/etc/otpdata/input_parameters_values.txt \
    $(BOARDDIR)/features/otpdata/obj_disc.txt:system/etc/otpdata/obj_disc.txt
#    frameworks/native/data/etc/android.hardware.vulkan.level-1.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.level-1.xml  \
#    frameworks/native/data/etc/android.hardware.vulkan.version-1_0_3.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.version.xml \

#audio share memory size (1024 + 512) config for android go version
PRODUCT_PROPERTY_OVERRIDES += \
    ro.af.client_heap_size_kbyte = 3072

#media codec memory config for android 512M version
PRODUCT_PROPERTY_OVERRIDES += \
    ro.media.maxmem = 187904819

#copy audio policy config
ifeq ($(USE_XML_AUDIO_POLICY_CONF), 1)
PRODUCT_COPY_FILES += \
    $(ROOTDIR)/system/etc/audio_policy_config/audio_policy_configuration_generic.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_configuration.xml \
    $(ROOTDIR)/system/etc/audio_policy_config/primary_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/primary_audio_policy_configuration.xml \
    $(ROOTDIR)/system/etc/audio_policy_config/a2dp_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/a2dp_audio_policy_configuration.xml \
    $(ROOTDIR)/system/etc/audio_policy_config/usb_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/usb_audio_policy_configuration.xml \
    $(ROOTDIR)/system/etc/audio_policy_config/r_submix_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/r_submix_audio_policy_configuration.xml \
    $(ROOTDIR)/system/etc/audio_policy_config/audio_policy_volumes.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_volumes.xml \
    $(ROOTDIR)/system/etc/audio_policy_config/default_volume_tables.xml:$(TARGET_COPY_OUT_VENDOR)/etc/default_volume_tables.xml
else
PRODUCT_COPY_FILES += \
    $(ROOTDIR)/system/etc/audio_params/audio_policy.conf:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy.conf
endif

# config sepolicy
#duplicate definition
#BOARD_SEPOLICY_DIRS += device/sprd/sharkle/common/sepolicy \
#    build/target/board/generic/sepolicy

SPRD_GNSS_ARCH := arm64

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
    ro.vendor.wcn.gpschip=ge2


#Display/Graphic config
#PRODUCT_PROPERTY_OVERRIDES += \
      ro.sf.lcd_density=320 \
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

# enable ims/modem_vpad
PRODUCT_PROPERTY_OVERRIDES += persist.vendor.sys.volte.enable=true

# enable dual camera calibration
PRODUCT_PROPERTY_OVERRIDES += ro.vendor.camera.dualcamera_cali_enable=1
PRODUCT_PROPERTY_OVERRIDES += ro.vendor.camera.dualcamera_cali_time=3

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
	audio.primary.$(TARGET_BOARD_PLATFORM) \
	audio_hardware_test \
	dpu.$(TARGET_BOARD_PLATFORM) \
	gsp.$(TARGET_BOARD_PLATFORM) \
	camera.$(TARGET_BOARD_PLATFORM) \
	power.$(TARGET_BOARD_PLATFORM) \
	memtrack.$(TARGET_BOARD_PLATFORM)

#audio vbc_eq
PRODUCT_PACKAGES += audio_vbc_eq \
                    libaudionpi \
                    libaudioparamteser \
                    libpolicy-subsystem

#SANSA|SPRD|NONE
#PRODUCT_SECURE_BOOT := NONE
#PRODUCT_PACKAGES += imgheaderinsert \
                    packimage.sh \
                    FMRadio \
                    #libGLES_android \
                    gralloc.$(TARGET_BOARD_PLATFORM)

PRODUCT_PACKAGES += iwnpi \
		    libiwnpi \
		    sprdwl_ng.ko
#for lmkd
PRODUCT_PROPERTY_OVERRIDES += \
     ro.lmk.medium=700 \
     ro.lmk.downgrade_pressure=60 \
     ro.lmk.critical=210 \
     ro.lmk.watermarkenhanced=true

# for jemalloc
MALLOC_SVELTE := true

# sensors
PRODUCT_PACKAGES += bstclass.ko \
            bma2x2.ko \
            akm09911.ko \
            ltr_558als.ko

# config sensor features supported by this board
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.sensor.proximity.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.proximity.xml \
    frameworks/native/data/etc/android.hardware.sensor.light.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.light.xml \
    frameworks/native/data/etc/android.hardware.sensor.accelerometer.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.accelerometer.xml \
    frameworks/native/data/etc/android.hardware.sensor.compass.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.compass.xml


#disable dmverity for system
#remove dmverity cfg for google's required
#PRODUCT_DMVERITY_DISABLE := true
