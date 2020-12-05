/*
 ** Copyright 2018 The Spreadtrum.com
 */

#ifndef ANDROID_SSENSE_H
#define ANDROID_SSENSE_H

namespace android {

// must be kept in sync with definitions in SmartSenseService.java

// DATA TYPE for binder call reportData
enum {
    DATA_TYPE_APP_AUDIO = 0,
    DATA_TYPE_APP_VIDEO = 1,
};

// DATA SUBTYPE for VIDEO for binder call reportData
enum {
    DATA_SUBTYPE_APP_VIDEO_STOP = 0,
    DATA_SUBTYPE_APP_VIDEO_START = 1,
};

}; // namespace android

#endif // ANDROID_SSENSE_H

