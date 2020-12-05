/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <hidl/HidlTransportSupport.h>
#include "Usb.h"
#include "../../1.0/default/Usb.h"

using android::sp;

// libhwbinder:
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;

using android::status_t;
using android::OK;

int main() {

    android::sp<android::hardware::usb::V1_1::IUsb> service1_1 = new android::hardware::usb::V1_1::implementation::Usb();

    configureRpcThreadpool(1, true /*callerWillJoin*/);
    status_t status1 = service1_1->registerAsService();

    if (status1 == OK) {
        ALOGI("USB HAL Ready.");
        joinRpcThreadpool();
    }

    ALOGE("Cannot register USB HAL service");
    return 1;
}
