# Copyright (C) 2015 The Android Open Source Project
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

PRODUCT_PROPERTY_OVERRIDES += \
    ro.vendor.radio.modemtype=l \
    persist.vendor.radio.phone_count=2 \
    persist.radio.multisim.config=dsds\

$(call inherit-product, build/target/product/telephony.mk)
# telephony modules
PRODUCT_PACKAGES += \
    MmsFolderView \
    messaging \
    SprdStk  \
    SprdDialer \
    CallFireWall

PRODUCT_PACKAGES += \
    UplmnSettings \
    sprdrild \
    librilsprd \
    libsprd-ril \
    librilutils \
    modemd \
    libatci \
    vendor.sprd.hardware.radio@1.0 \
    libril_threads \
    ModemNotifier \
    libFactoryRadioTest \
    librilsprd-single \
    libsprd-ril-single

# SPRD: FEATURE_VOLTE_CALLFORWARD_OPTIONS
PRODUCT_PACKAGES += \
    CallSettings

# add sprd Contacts
PRODUCT_PACKAGES += \
    SprdContacts \
    SprdContactsProvider

# add mobile tracker
PRODUCT_PACKAGES += \
    SprdMobileTracker

# telephony resource
include $(wildcard vendor/sprd/telephony-res/apply_telephony_res.mk)

# sprd cbcustomsetting
$(call inherit-product, vendor/sprd/platform/packages/apps/CbCustomSetting/etc/device-sprd-cb.mk)

DISABLE_RILD_OEM_HOOK := true
