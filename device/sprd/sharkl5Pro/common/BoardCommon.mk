LOCAL_PATH := device/sprd/sharkl5Pro/common
TARGET_CPU_SMP := true
TARGET_NO_KERNEL := false
ROOTCOMM := $(LOCAL_PATH)/rootdir
PLATCOMM := $(LOCAL_PATH)

BOARD_SUPPORT_UMS_K44 := true
TARGET_RECOVERY_PIXEL_FORMAT := "RGBX_8888"
BOARD_KERNEL_SEPARATED_DT := true
USES_UNCOMPRESSED_KERNEL := true
BOARD_KERNEL_BASE :=  0x00000000
BOARD_KERNEL_CMDLINE := console=ttyS1,115200n8
BOARD_BOOTIMG_HEADER_VERSION := 1
BOARD_INCLUDE_RECOVERY_DTBO := true
BOARD_MKBOOTIMG_ARGS := --kernel_offset 0x00008000 --ramdisk_offset 0x05400000 --header_version $(BOARD_BOOTIMG_HEADER_VERSION)
BOARD_KERNEL_PAGESIZE := 2048

#TARGET_GLOBAL_CFLAGS   += -mfpu=neon -mfloat-abi=softfp
#TARGET_GLOBAL_CPPFLAGS += -mfpu=neon -mfloat-abi=softfp

# graphics
USE_SPRD_HWCOMPOSER  := true
USE_OPENGL_RENDERER := true
USE_OVERLAY_COMPOSER_GPU := true
NUM_FRAMEBUFFER_SURFACE_BUFFERS := 3
SPRD_VIRTUAL_DISPLAY:= 1
TARGET_USES_HWC2 := true
SPRD_TARGET_USES_HWC2 := true
TARGET_SUPPORT_ADF_DISPLAY := false

# audio configs
BOARD_USES_GENERIC_AUDIO := true
BOARD_USES_TINYALSA_AUDIO := true
BOARD_USES_ALSA_AUDIO := false
BUILD_WITH_ALSA_UTILS := false
USE_LEGACY_AUDIO_POLICY := 0
USE_CUSTOM_AUDIO_POLICY := 1
SPRD_VBC_NOT_USE_AD23 := true
#SPRD: vbc support deepbufer mixer channel
SPRD_VBC_DEEPBUFFER_MIXER :=true
SPRD_AUDIO_VOIP_VERSION  :=v2


# telephony
BOARD_SPRD_RIL := true
USE_BOOT_AT_DIAG :=true

# ota releasetools extensions
TARGET_RECOVERY_UPDATER_LIBS := libsprd_updater

TARGET_OTA_EXTENSIONS_DIR := vendor/sprd/tools/ota
TARGET_RELEASETOOLS_EXTENSIONS := $(TARGET_OTA_EXTENSIONS_DIR)
#TARGET_RECOVERY_UI_LIB := librecovery_ui_whale2
# recovery configs
RECOVERYCOMM := $(PLATCOMM)/recovery
#TARGET_RECOVERY_FSTAB := $(RECOVERYCOMM)/recovery.fstab
TARGET_RECOVERY_INITRC := $(RECOVERYCOMM)/init.rc
ifeq (f2fs,$(strip $(BOARD_USERDATAIMAGE_FILE_SYSTEM_TYPE)))
  RECOVERY_FSTAB_SUFFIX1 := .f2fs
endif
RECOVERY_FSTAB_SUFFIX := $(RECOVERY_FSTAB_SUFFIX1)
TARGET_RECOVERY_FSTAB := $(RECOVERYCOMM)/recovery$(RECOVERY_FSTAB_SUFFIX).fstab
# $(warning RECOVERY_FSTAB=$(TARGET_RECOVERY_FSTAB))
# SPRD: add nvmerge config
TARGET_RECOVERY_NVMERGE_CONFIG := $(PLATCOMM)/nvmerge.cfg
#SPRD:modem update config
MODEM_UPDATE_CONFIG := true
MODEM_UPDATE_CONFIG_FILE := $(PLATCOMM)/modem.cfg

# default value is 512M, using resize to adapter real size
BOARD_USERDATAIMAGE_PARTITION_SIZE := 536870912

BOARD_RESERVED_SPACE_ON := true

#SPRD:SUPPORT_64BIT_MEDIA
TARGET_SUPPORT_64BIT_MEDIA := true

#SPRD：AVIExtractorEx
USE_AVIExtractorEx :=true

#SPRD：WAVExtractorEx
USE_WAVExtractorEx :=true

#SPRD: Use High Quality Dyn SRC
USE_HIGH_QUALITY_DYN_SRC :=true

#SPRD：SUPPORT IMAADPCM
SUPPORT_IMAADPCM :=true

#SPRD：SUPPORT FLVExtractor
SUPPORT_FLVExtractor :=true
#SPRD:SUPPORT PSXSTRExtractor
SUPPORT_PSXSTRExtractor :=true

#SPRD: support wcnd eng mode
USE_SPRD_ENG :=true

# CP log directory
CP_LOG_DIR_IN_AP := ylog

# WCN log
SPRD_CP_LOG_WCN := MARLIN2

# ETB log
SPRD_ETB_ARCH := SHARKL5PRO

#BBAT GPIO ADDRESS
SHARKL5PRO_BBAT_GPIO := true

#guangsheng fota start
FOTA_UPDATE_SUPPORT := false
FOTA_UPDATE_WITH_ICON := false

#redstone fota
REDSTONE_FOTA_SUPPORT := false
REDSTONE_FOTA_APK_ICON := no
REDSTONE_FOTA_APK_KEY := none
REDSTONE_FOTA_APK_CHANNELID := none
#end

## GPU
TARGET_GPU_PLATFORM := soft
MALI_PLATFORM_NAME := sharkl5Pro
GPU_GRALLOC_INCLUDES := $(TOP)/vendor/sprd/external/drivers/gpu
#MAX_EGL_CACHE_SIZE := 1048576
#$(warning MALI_PLATFORM_NAME:$(MALI_PLATFORM_NAME))

#vsp config
TARGET_JPG_PLATFORM := r8p0
TARGET_VSP_PLATFORM := sharkl5Pro
SUPPORT_RGB_ENC := true

#SUPPORT LOWPOWER WITH LCD 30 FPS
POWER_HINT_VIDEO_LOWPOWER_DISPLAY :=true

#SPRD: streaming extention, AOSP should be false.
USE_SPRD_STREAMING_EX := true

#SPRD: set property overrides split
BOARD_PROPERTY_OVERRIDES_SPLIT_ENABLED := false


#Supprot camera filter mode. 0:Sprd 1:Arcsoft
TARGET_BOARD_CAMERA_FILTER_VERSION := 0

TARGET_BOARD_CAMERA_ZOOM_FACTOR_SUPPORT := 4

#sprd cnr feature
TARGET_BOARD_CAMERA_CNR_CAPTURE = true

#Camera Power and Performence optimization
CONFIG_CAMERA_DFS_FIXED_MAXLEVEL := 3

TARGET_BOARD_CAMERA_FUNCTION_DUMMY := true

#vendor apps are restricted to use these versions
BOARD_SYSTEMSDK_VERSIONS := $(PLATFORM_SYSTEMSDK_VERSIONS)
