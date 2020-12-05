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
KERNEL_PATH := kernel4.14
export KERNEL_PATH
BOARD_PATH=$(KERNEL_PATH)/sprd-board-config/roc1/ud710_haps/ud710_haps_native
include $(BOARD_PATH)
$(call inherit-product, device/sprd/roc1/ud710_haps/ud710_haps_Base.mk)
PLATDIR := device/sprd/roc1
TARGET_BOARD := ud710_haps
BOARDDIR := $(PLATDIR)/$(TARGET_BOARD)
PLATCOMM := $(PLATDIR)/common
ROOTDIR := $(BOARDDIR)/rootdir
TARGET_BOARD_PLATFORM := ud710
TARGET_GPU_PLATFORM := soft

TARGET_BOOTLOADER_BOARD_NAME := ud710_haps
CHIPRAM_DEFCONFIG := ud710_haps
UBOOT_DEFCONFIG := ud710_haps
UBOOT_TARGET_DTB := ud710_haps

PRODUCT_NAME := ud710_haps_native
PRODUCT_DEVICE := ud710_haps
PRODUCT_BRAND := SPRD
PRODUCT_MODEL := ud710_haps_native
PRODUCT_WIFI_DEVICE := sprd
PRODUCT_MANUFACTURER := sprd

DEVICE_PACKAGE_OVERLAYS := $(BOARDDIR)/overlay $(PLATDIR)/overlay $(PLATCOMM)/overlay

#Runtime Overlay Packages
PRODUCT_ENFORCE_RRO_TARGETS := \
    framework-res

TARGET_ARCH := arm64
TARGET_ARCH_VARIANT := armv8-a
TARGET_CPU_ABI := arm64-v8a
TARGET_CPU_VARIANT := generic

TARGET_2ND_ARCH := arm
TARGET_2ND_ARCH_VARIANT := armv7-a-neon
TARGET_2ND_CPU_VARIANT := cortex-a15
TARGET_2ND_CPU_ABI := armeabi-v7a
TARGET_2ND_CPU_ABI2 := armeabi

TARGET_USES_64_BIT_BINDER := true

#secure boot
#BOARD_SECBOOT_CONFIG := true

$(call inherit-product, $(PLATCOMM)/security_feature.mk)

#soter(weixin pay)
CONFIG_CHIP_UID := false
BOARD_SOTER_TRUSTY := false

#enable 3dnr & bokeh
PRODUCT_PROPERTY_OVERRIDES += \
	persist.vendor.cam.3dnr.version=1 \
	persist.vendor.cam.ba.blur.version=6 \
	persist.vendor.cam.api.version=0

PRODUCT_COPY_FILES += \
    $(BOARDDIR)/ud710_haps.xml:$(PRODUCT_OUT)/ud710_haps.xml

PRODUCT_AAPT_CONFIG := normal large xlarge mdpi 420dpi xxhdpi
PRODUCT_AAPT_PREF_CONFIG := 400dpi
PRODUCT_AAPT_PREBUILT_DPI := 400dpi 320hdpi xhdpi

#Preset TouchPal InputMethod
PRODUCT_REVISION := oversea multi-lang

# add for ifaa
BOARD_IFAA_TRUSTY := false

# Add for MDT feature
MDT_ENABLE := false

ifeq ($(MDT_ENABLE),true)
PRODUCT_PROPERTY_OVERRIDES += ro.vendor.radio.mdt_enable = 1
PRODUCT_PACKAGES += \
   MinDriveTest \
   libDivRIL
endif

# disable camera service for haps bringup
PRODUCT_PROPERTY_OVERRIDES += \
    config.disable_cameraservice=true

# disable smart sense for haps bringup
PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.ss.enable=false
