FEATURES.PRODUCT_PACKAGES += \
    ShakePhoneToStartRecording \
    SpeakerToHeadset \
    FlipToMute \
    FadeDownRingtoneToVibrate \
    PickUpToAnswerIncomingCall \
    MaxRingingVolumeAndVibrate

#VT config
ADDITIONAL_BUILD_PROPERTIES += \
    persist.sys.support.vt=true \
    persist.sys.csvt=false
#add for Telephony
ADDITIONAL_BUILD_PROPERTIES += \
    ro.telephony.default_network = 9\
    keyguard.no_require_sim=true \
    ro.com.android.dataroaming=false \
    ro.simlock.unlock.autoshow=1 \
    ro.simlock.unlock.bynv=0 \
    ro.simlock.onekey.lock=0
# Additional settings used in all AOSP builds
ADDITIONAL_BUILD_PROPERTIES += \
    ro.config.ringtone=Ring_Synth_04.ogg \
    ro.config.ringtone0=Ring_Synth_04.ogg \
    ro.config.ringtone1=Ring_Synth_02.ogg \
    ro.config.default_message=Argon.ogg \
    ro.config.default_message0=Argon.ogg \
    ro.config.default_message1=Highwire.ogg \
    ro.config.notification_sound=pixiedust.ogg \
    ro.config.alarm_alert=Oxygen.ogg
## Telephony config end ##

#ADD For WMs
#0:1080x1920 1:720x1080 2: 720x1440 3:540x960
ADDITIONAL_BUILD_PROPERTIES += \
    ro.sprd.displayenhance=true \
    ro.sprd.nightdisplay.enhance = true
ifeq ($(strip $(SPRD_SR)),true)
ADDITIONAL_BUILD_PROPERTIES += \
    ro.sprd.superresolution=1 \
    ro.sr.displaysize.defaultresolution=0 \
    ro.sr.displaysize.lowresolution=1
else
ADDITIONAL_BUILD_PROPERTIES += \
    ro.sprd.superresolution=0 \
    ro.sr.displaysize.defaultresolution=1 \
    ro.sr.displaysize.lowresolution=1
endif

##powercontroller-ultrasaving start##
TARGET_PWCTL_ULTRASAVING ?= true
ifeq (true,$(strip $(TARGET_PWCTL_ULTRASAVING)))
# ultrasaving mode
ADDITIONAL_BUILD_PROPERTIES += \
    ro.sys.pwctl.ultrasaving=1
else
ADDITIONAL_BUILD_PROPERTIES += \
    ro.sys.pwctl.ultrasaving=0
endif
##powercontroller-ultrasaving end##

# Location configs
$(warning "SUPPORT_LOCATION = $(SUPPORT_LOCATION), SUPPORT_LOCATION_GNSS = $(SUPPORT_LOCATION_GNSS)")
ifneq ($(strip $(SUPPORT_LOCATION)), disabled)
ifneq ($(strip $(SUPPORT_LOCATION_GNSS)), disabled)
    PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.location.gps.xml:vendor/etc/permissions/android.hardware.location.gps.xml
    else
    PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.location.xml:vendor/etc/permissions/android.hardware.location.xml
endif
endif

ifeq ($(strip $(SUPPORT_LOCATION)), disabled)
ADDITIONAL_BUILD_PROPERTIES += \
    ro.location=disabled \
    ro.location.gnss=disabled
FEATURES.PRODUCT_PACKAGE_OVERLAYS += vendor/sprd/feature_configs/location/overlay
endif

ifeq ($(strip $(SUPPORT_LOCATION_GNSS)), disabled)
ADDITIONAL_BUILD_PROPERTIES += \
    ro.location.gnss=disabled
FEATURES.PRODUCT_PACKAGE_OVERLAYS += vendor/sprd/feature_configs/location/overlay
endif

#ifaa
ifeq ($(strip $(BOARD_IFAA_TRUSTY)), true)
ADDITIONAL_BUILD_PROPERTIES += \
    ro.ifaa.support=true
endif
#soter
ifeq ($(strip $(BOARD_SOTER_TRUSTY)), true)
ADDITIONAL_BUILD_PROPERTIES += \
    ro.soter.support=true
endif

$(warning "BOARD_HAVE_SPRD_WCN_COMBO = $(BOARD_HAVE_SPRD_WCN_COMBO)")
include $(wildcard vendor/sprd/modules/wlan/wlanconfig/device-sprd-wlan-property.mk)
