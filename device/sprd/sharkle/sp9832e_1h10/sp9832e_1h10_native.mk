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
KERNEL_PATH := kernel4.4
export KERNEL_PATH
BOARD_PATH=$(KERNEL_PATH)/sprd-board-config/sharkle/sp9832e_1h10/sp9832e_1h10_native
include $(BOARD_PATH)

$(call inherit-product, device/sprd/sharkle/sp9832e_1h10/sp9832e_1h10_base.mk)
PLATDIR := device/sprd/sharkle
TARGET_BOARD := sp9832e_1h10
BOARDDIR := $(PLATDIR)/$(TARGET_BOARD)
PLATCOMM := $(PLATDIR)/common
ROOTDIR := $(BOARDDIR)/rootdir
TARGET_BOARD_PLATFORM := sp9832e

TARGET_GPU_PLATFORM := midgard

TARGET_BOOTLOADER_BOARD_NAME := sp9832e_1h10
CHIPRAM_DEFCONFIG := sp9832e_1h10
UBOOT_DEFCONFIG := sp9832e_1h10
UBOOT_TARGET_DTB := sp9832e_1h10

PRODUCT_NAME := sp9832e_1h10_native
PRODUCT_DEVICE := sp9832e_1h10
PRODUCT_BRAND := SPRD
PRODUCT_MODEL := sp9832e_1h10_native
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

TARGET_KERNEL_ARCH = arm64
TARGET_USES_64_BIT_BINDER := true

#secure boot
BOARD_SECBOOT_CONFIG := true

#add for sunwave fingerprint
BOARD_FINGERPRINT_CONFIG := sunwave

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
    $(BOARDDIR)/sp9832e_1h10.xml:$(PRODUCT_OUT)/sp9832e_1h10.xml

PRODUCT_AAPT_CONFIG := normal large xlarge mdpi 420dpi xxhdpi
PRODUCT_AAPT_PREF_CONFIG := xhdpi
PRODUCT_AAPT_PREBUILT_DPI := 320hdpi xhdpi

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

