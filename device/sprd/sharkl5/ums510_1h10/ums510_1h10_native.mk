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
BOARD_PATH=$(KERNEL_PATH)/sprd-board-config/sharkl5/ums510_1h10/ums510_1h10_native
include $(BOARD_PATH)
$(call inherit-product, device/sprd/sharkl5/ums510_1h10/ums510_1h10_base.mk)
PLATDIR := device/sprd/sharkl5
TARGET_BOARD := ums510_1h10
BOARDDIR := $(PLATDIR)/$(TARGET_BOARD)
PLATCOMM := $(PLATDIR)/common
ROOTDIR := $(BOARDDIR)/rootdir
TARGET_BOARD_PLATFORM := ums510
TARGET_GPU_PLATFORM := rogue

TARGET_BOOTLOADER_BOARD_NAME := ums510_1h10
CHIPRAM_DEFCONFIG := ums510_1h10
UBOOT_DEFCONFIG := ums510_1h10
UBOOT_TARGET_DTB := ums510_1h10

PRODUCT_NAME := ums510_1h10_native
PRODUCT_DEVICE := ums510_1h10
PRODUCT_BRAND := SPRD
PRODUCT_MODEL := ums510_1h10_native
PRODUCT_WIFI_DEVICE := sprd
PRODUCT_MANUFACTURER := sprd

DEVICE_PACKAGE_OVERLAYS := $(BOARDDIR)/overlay $(PLATDIR)/overlay $(PLATCOMM)/overlay

#Runtime Overlay Packages
PRODUCT_ENFORCE_RRO_TARGETS := \
    framework-res

TARGET_ARCH := arm64
TARGET_ARCH_VARIANT := armv8-2a
TARGET_CPU_ABI := arm64-v8a
TARGET_CPU_VARIANT := cortex-a55

TARGET_2ND_ARCH := arm
TARGET_2ND_ARCH_VARIANT := armv8-a
TARGET_2ND_CPU_VARIANT := cortex-a55
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
    $(BOARDDIR)/ums510_1h10.xml:$(PRODUCT_OUT)/ums510_1h10.xml \
    $(BOARDDIR)/temp_img/vbmeta-sign.img:$(PRODUCT_OUT)/vbmeta-gsi.img

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
