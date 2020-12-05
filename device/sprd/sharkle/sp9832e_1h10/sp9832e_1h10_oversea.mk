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

include device/sprd/sharkle/sp9832e_1h10/sp9832e_1h10_native.mk

PRODUCT_REVISION := oversea multi-lang
include $(APPLY_PRODUCT_REVISION)

# Override
PRODUCT_NAME := sp9832e_1h10_oversea

#enable VoWiFi
VOWIFI_SERVICE_ENABLE := true
