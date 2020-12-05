/*
* opyright (C) 2015 The Android Open Source Project
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
#ifndef ANDROID_HARDWARE_CAMERA2_OUTPUTCONFIGURATIONEX_H
#define ANDROID_HARDWARE_CAMERA2_OUTPUTCONFIGURATIONEX_H
#include <utils/Log.h>
#include <utils/CallStack.h>
#include <gui/Surface.h>
#include <binder/Parcel.h>
namespace android {
class OutputConfigurationEx {
    public:
        OutputConfigurationEx(){
            mWidth = 0;
            mHeight = 0;
        }
        int getWidth() const{return mWidth;}
        int getHeight() const{return mHeight;}
        int readInfo(const Parcel* parcel, int* rotation){
            int err = OK;
            int width = 0;
            int height = 0;
            int mRotation = 0;
            if ((err = parcel->readInt32(&width)) != OK) {
                ALOGE("%s: Failed to read width from parcel", __FUNCTION__);
            }

            if ((err = parcel->readInt32(&height)) != OK) {
                ALOGE("%s: Failed to read height from parcel", __FUNCTION__);
            }
            mWidth = width;
            mHeight = height;
            ALOGD("w:%d ,h:%d ",mWidth,mHeight);
            if ((err = parcel->readInt32(&mRotation)) != OK) {
                ALOGE("%s: Failed to read rotation from parcel", __FUNCTION__);
                return err;
            }
            *rotation = mRotation;
            return err;
        }
    private:
        int mWidth;
        int mHeight;
};
}
#endif
