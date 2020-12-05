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

include device/sprd/sharkl5/common/BoardCommon.mk

#chipram tool for arm64
TOOLCHAIN_64 := true

TARGET_CPU_SMP := true
ARCH_ARM_HAVE_TLS_REGISTER := true

sprdiskexist := $(shell if [ -f $(TOPDIR)sprdisk/Makefile -a "$(TARGET_BUILD_VARIANT)" = "userdebug" ]; then echo "exist"; else echo "notexist"; fi;)
ifneq ($(sprdiskexist), exist)
TARGET_NO_SPRDISK := true
else
TARGET_NO_SPRDISK := false
endif
SPRDISK_BUILD_PATH := sprdisk/


# ext4 partition layout
BOARD_VENDORIMAGE_PARTITION_SIZE := 419430400
BOARD_VENDORIMAGE_FILE_SYSTEM_TYPE := ext4
TARGET_COPY_OUT_VENDOR=vendor
TARGET_USERIMAGES_USE_EXT4 := true
BOARD_BOOTIMAGE_PARTITION_SIZE := 36700160
BOARD_RECOVERYIMAGE_PARTITION_SIZE := 41943040
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 2621440000
BOARD_CACHEIMAGE_PARTITION_SIZE := 150000000
BOARD_PRODNVIMAGE_PARTITION_SIZE := 5242880
BOARD_USERDATAIMAGE_PARTITION_SIZE := 10737418240
BOARD_DTBIMG_PARTITION_SIZE := 8388608
BOARD_DTBOIMG_PARTITION_SIZE := 8388608
BOARD_FLASH_BLOCK_SIZE := 4096
BOARD_CACHEIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_PRODNVIMAGE_FILE_SYSTEM_TYPE := ext4

TARGET_SYSTEMIMAGES_SPARSE_EXT_DISABLED := true
TARGET_USERIMAGES_SPARSE_EXT_DISABLED := false
BOARD_PERSISTIMAGE_PARTITION_SIZE := 2097152
TARGET_PRODNVIMAGES_SPARSE_EXT_DISABLED := true
TARGET_CACHEIMAGES_SPARSE_EXT_DISABLED := false
USE_SPRD_SENSOR_HUB := true
BOARD_PRODUCTIMAGE_PARTITION_SIZE :=104857600
BOARD_PRODUCTIMAGE_FILE_SYSTEM_TYPE := ext4
TARGET_COPY_OUT_PRODUCT=product


#camera configuration start
#------section 1: software structure------
#cameraframework extension by sprd
TARGET_BOARD_SPRD_EXFRAMEWORKS_SUPPORT := true
#hal1.0 or hal3.2
TARGET_BOARD_CAMERA_HAL_VERSION := HAL3.2
#isp software version
TARGET_BOARD_CAMERA_ISP_VERSION := 2.6
#for big system validation
TARGET_BOARD_IS_SC_FPGA := false
#camera offline structure
TARGET_BOARD_CAMERA_OFFLINE := true

#------section 2: sensor & flash config------
#camera auto detect sensor
TARGET_BOARD_CAMERA_AUTO_DETECT_SENSOR := true
#select camera 2M,3M,5M,8M,13M,16M,21M
CAMERA_SUPPORT_SIZE := 16M
FRONT_CAMERA_SUPPORT_SIZE := 8M
BACK_EXT_CAMERA_SUPPORT_SIZE := 8M
#camera sensor support list
CAMERA_SENSOR_TYPE_BACK := "ov12a10"
CAMERA_SENSOR_TYPE_FRONT := "ov8856_shine"
CAMERA_SENSOR_TYPE_BACK_EXT := "ov5675_dual"
CAMERA_SENSOR_TYPE_FRONT_EXT :=
#camera dual sensor
TARGET_BOARD_CAMERA_DUAL_SENSOR_MODULE := true
#dual camera 3A sync
#TARGET_BOARD_CONFIG_CAMERA_DUAL_SYNC := true
#sensor multi-instance
TARGET_BOARD_CAMERA_SENSOR_MULTI_INSTANCE_SUPPORT := false
#flash led feature
TARGET_BOARD_CAMERA_FLASH_LED_0 := true
TARGET_BOARD_CAMERA_FLASH_LED_1 := true
TARGET_BOARD_CAMERA_FLASH_CTRL := false
#flash ic
TARGET_BOARD_CAMERA_FLASH_TYPE := ocp8137
#range of value 0~31
CAMERA_TORCH_LIGHT_LEVEL := 16
#pdaf feature
TARGET_BOARD_CAMERA_PDAF := false
TARGET_BOARD_CAMERA_PDAF_TYPE := 0
TARGET_BOARD_CAMERA_DCAM_PDAF := true

#------section 3: feature config------
#support 4k record
TARGET_BOARD_CAMERA_SUPPORT_4K_RECORD := false
#face detect
TARGET_BOARD_CAMERA_FACE_DETECT := true
#face beauty
TARGET_BOARD_CAMERA_FACE_BEAUTY := true
#hdr capture
TARGET_BOARD_CAMERA_HDR_CAPTURE := true
TARGET_BOARD_CAMERA_HDR_SPRD_LIB := true
#bokeh feature
TARGET_BOARD_BOKEH_MODE_SUPPORT := false
TARGET_BOARD_ARCSOFT_BOKEH_MODE_SUPPORT := false
#covered camera enble
TARGET_BOARD_COVERED_SENSOR_SUPPORT := false
#blur mode enble
TARGET_BOARD_BLUR_MODE_SUPPORT := false
#3dnr capture
TARGET_BOARD_CAMERA_3DNR_CAPTURE := false
#eis
TARGET_BOARD_CAMERA_EIS := true
CAMERA_EIS_BOARD_PARAM := "sp9863a-1"
#gyro
TARGET_BOARD_CAMERA_GYRO := true
#uv denoise
TARGET_BOARD_CAMERA_UV_DENOISE := false
#support camera filter mode. 0:sprd 1:arcsoft
TARGET_BOARD_CAMERA_FILTER_VERSION := 0
#zoom x
TARGET_BOARD_CAMERA_ZOOM_FACTOR_SUPPORT := 4
#sprd cnr feature
TARGET_BOARD_CAMERA_CNR_CAPTURE = true

#------section 4: optimize config------
#image angle in different project
TARGET_BOARD_CAMERA_ADAPTER_IMAGE := 180
#set camera recording frame rate dynamic
TARGET_BOARD_CONFIG_CAMRECORDER_DYNAMIC_FPS := false
#power optimization
TARGET_BOARD_CAMERA_POWER_OPTIMIZATION := false
#Slowmotion optimize
TARGET_BOARD_SPRD_SLOWMOTION_OPTIMIZE := true
#camera power and performence optimization
CONFIG_CAMERA_DFS_FIXED_MAXLEVEL := 3

#------section 5: other misc config------
#open dummy when camera hal not ready in bringup
TARGET_BOARD_CAMERA_FUNCTION_DUMMY := false

#------section 6: kernel module config------
#use module for kernel driver or not
TARGET_BOARD_CAMERA_MODULAR := true

#modulars & version config
TARGET_BOARD_CAMERA_ISP_MODULAR_KERNEL := isp2.6
TARGET_BOARD_CAMERA_FD_MODULAR_KERNEL := fd1.0
TARGET_BOARD_CAMERA_CPP_MODULAR_KERNEL := lite_r5p0
TARGET_BOARD_CAMERA_CPP_USER_DRIVER := true
TARGET_BOARD_CAMERA_SENSOR_MODULAR_KERNEL := yes
TARGET_BOARD_CAMERA_CSI_VERSION := r2p1
TARGET_BOARD_CAMERA_HAPS_TEST := true

#camera configuration end
# ===============end of camera configuration ===============



#GNSS GPS
BOARD_USE_SPRD_GNSS := ge2

#SPRD: SUPPORT EXTERNAL WCN
SPRD_CP_LOG_WCN := MARLIN2

# select FMRadio
BOARD_USE_SPRD_FMAPP := false
BOARD_HAVE_FM_BCM := false
BOARD_HAVE_BLUETOOTH := true

# select sdcard
TARGET_USE_SDCARDFS := false
USE_VENDOR_LIB := true

#Audio NR enable
AUDIO_RECORD_NR := true

# WIFI configs
BOARD_WPA_SUPPLICANT_DRIVER := NL80211
WPA_SUPPLICANT_VERSION      := VER_2_1_DEVEL
BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_sprdwl
BOARD_HOSTAPD_DRIVER        := NL80211
BOARD_HOSTAPD_PRIVATE_LIB   := lib_driver_cmd_sprdwl
BOARD_WLAN_DEVICE           := sc2355
WIFI_DRIVER_FW_PATH_PARAM   := "/data/vendor/wifi/fwpath"
WIFI_DRIVER_FW_PATH_STA     := "sta_mode"
WIFI_DRIVER_FW_PATH_P2P     := "p2p_mode"
WIFI_DRIVER_FW_PATH_AP      := "ap_mode"
WIFI_DRIVER_MODULE_PATH     := "/vendor/lib/modules/sprdwl_ng.ko"
WIFI_DRIVER_MODULE_NAME     := "sprdwl_ng"
BOARD_SEPOLICY_DIRS += device/sprd/sharkl5/common/sepolicy \
    build/target/board/generic/sepolicy
BOARD_PLAT_PRIVATE_SEPOLICY_DIR += device/sprd/sharkl5/common/plat_sepolicy/private
BOARD_PLAT_PUBLIC_SEPOLICY_DIR += device/sprd/sharkl5/common/plat_sepolicy/public

#SPRD: acquire powerhint during playing video
POWER_HINT_VIDEO_CONTROL_CORE := true

# select sensor
SENSOR_HUB_ACCELEROMETER := lsm6ds3
SENSOR_HUB_GYROSCOPE := null
SENSOR_HUB_LIGHT := null
SENSOR_HUB_MAGNETIC := null
SENSOR_HUB_PROXIMITY := null
SENSOR_HUB_PRESSURE := null
SENSOR_HUB_CALIBRATION := ums510_haps
SENSOR_HUB_FEATURE := hub

# WFD max support 720P
TARGET_BOARD_WFD_MAX_SUPPORT_720P := true

#SUPPORT LOWPOWER WITH LCD 30 FPS
LOWPOWER_DISPLAY_30FPS :=true

# fps adjust to expected range
TARGET_BOARD_ADJUST_FPS_IN_RANGE := true

# Use mke2fs to create ext4 images
TARGET_USES_MKE2FS := true

BOARD_IOSNOOP_DISABLE := true

BOARD_BUILD_SYSTEM_ROOT_IMAGE := true

