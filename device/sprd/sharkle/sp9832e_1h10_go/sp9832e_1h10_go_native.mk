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
#

KERNEL_PATH := kernel4.4
export KERNEL_PATH
BOARD_PATH := $(KERNEL_PATH)/sprd-board-config/sharkle/sp9832e_1h10_go/sp9832e_1h10_go_native
include $(BOARD_PATH)
PRODUCT_GO_DEVICE := true

PLATDIR := device/sprd/sharkle
TARGET_BOARD := sp9832e_1h10_go
BOARDDIR := $(PLATDIR)/$(TARGET_BOARD)
PLATCOMM := $(PLATDIR)/common
ROOTDIR := $(BOARDDIR)/rootdir
WPDIR := vendor/sprd/resource/wallpapers/FWVGA
TARGET_BOARD_PLATFORM := sp9832e

# copy media_profiles.xml before calling device.mk,
# because we want to use our file, not the common one
PRODUCT_COPY_FILES += $(BOARDDIR)/media_profiles.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_V1_0.xml

$(call inherit-product, device/sprd/sharkle/sp9832e_1h10_go/sp9832e_1h10_go_base.mk)
TARGET_GPU_PLATFORM := midgard

TARGET_BOOTLOADER_BOARD_NAME := sp9832e_1h10_32b
CHIPRAM_DEFCONFIG := sp9832e_1h10_32b
UBOOT_DEFCONFIG := sp9832e_1h10_32b
UBOOT_TARGET_DTB := sp9832e_1h10_32b

PRODUCT_NAME := sp9832e_1h10_go_native
PRODUCT_DEVICE := sp9832e_1h10_go
PRODUCT_BRAND := SPRD
PRODUCT_MODEL := sp9832e_1h10_go_native
PRODUCT_WIFI_DEVICE := sprd
PRODUCT_MANUFACTURER := sprd

DEVICE_PACKAGE_OVERLAYS := $(PLATCOMM)/wlan/overlay $(BOARDDIR)/overlay $(PLATDIR)/overlay $(PLATCOMM)/overlay $(WPDIR)/overlay

#Runtime Overlay Packages
PRODUCT_ENFORCE_RRO_TARGETS := \
    framework-res

#TARGET_ARCH := arm64
#TARGET_ARCH_VARIANT := armv8-a
#TARGET_CPU_ABI := arm64-v8a
#TARGET_CPU_VARIANT := generic

#TARGET_2ND_ARCH := arm
#TARGET_2ND_ARCH_VARIANT := armv7-a-neon
#TARGET_2ND_CPU_VARIANT := cortex-a15
#TARGET_2ND_CPU_ABI := armeabi-v7a
#TARGET_2ND_CPU_ABI2 := armeabi

TARGET_ARCH := arm
TARGET_ARCH_VARIANT := armv7-a-neon
TARGET_CPU_ABI := armeabi-v7a
TARGET_CPU_ABI2 := armeabi
TARGET_CPU_VARIANT := generic
#CONFIG_64KERNEL_32FRAMEWORK := true

TARGET_KERNEL_ARCH = arm
TARGET_USES_64_BIT_BINDER := true

#secure boot
BOARD_SECBOOT_CONFIG := true
BOARD_TEE_LOW_MEM := true

CFG_TRUSTY_DEFAULT_PROJECT := sharkle-lowmemory
$(call inherit-product, $(PLATCOMM)/security_feature.mk)

PRODUCT_COPY_FILES += \
    $(BOARDDIR)/sp9832e_1h10_go.xml:$(PRODUCT_OUT)/sp9832e_1h10.xml

PRODUCT_COPY_FILES += \
    $(BOARDDIR)/sp9832e_1h10_go.xml:$(PRODUCT_OUT)/sp9832e_1h10.xml \
    $(BOARDDIR)/temp_img/prodnv.img:$(PRODUCT_OUT)/prodnv.img

PRODUCT_AAPT_CONFIG := normal large xlarge mdpi 420dpi xxhdpi
PRODUCT_AAPT_PREF_CONFIG := hdpi
PRODUCT_AAPT_PREBUILT_DPI := hdpi xhdpi

$(call inherit-product, $(SRC_TARGET_DIR)/product/languages_full.mk)
include build/make/target/product/go_defaults_common_speed_compile.mk
PRODUCT_PACKAGES += SprdDialerGo

CHIPRAM_DDR_CUSTOMIZE_LIMITED := true
CHIPRAM_DDR_CUSTOMIZE_SIZE := 0x20000000

#camera compile config
PRODUCT_USE_CAM_QUICKCAM := false
PRODUCT_USE_CAM_FILTER := false
PRODUCT_USE_CAM_PANORAMA := false
PRODUCT_PROPERTY_OVERRIDES += persist.vendor.cam.facebeauty.corp=0

#Camera feature cut down
SPRD_CAMERA_MINI_FEATURE := true

#Display/Graphic config
PRODUCT_PROPERTY_OVERRIDES += \
      ro.sf.lcd_density=240 \
      ro.vendor.sf.lcd_width=54 \
      ro.vendor.sf.lcd_height=96 \
      ro.opengles.version=196610

#media codec memory config for android 512M version
PRODUCT_PROPERTY_OVERRIDES += \
    ro.media.maxmem= 263066746

PRODUCT_REVISION +=  gocamera

#VT feature config
PRODUCT_PROPERTY_OVERRIDES := \
    persist.sys.support.vt=false \
$(PRODUCT_PROPERTY_OVERRIDES)

#NativeMMI Config
PRODUCT_COPY_FILES += \
    $(ROOTDIR)/prodnv/PCBA_NOFINGER.conf:$(TARGET_COPY_OUT_VENDOR)/etc/PCBA.conf

