/*
 ** Copyright 2018 The Spreadtrum.com
 */

#ifndef ANDROID_ISSENSE_H
#define ANDROID_ISSENSE_H

#include <utils/Errors.h>
#include <binder/IInterface.h>

namespace android {

// ----------------------------------------------------------------------------

class ISSense : public IInterface
{
public:
    // These transaction IDs must be kept in sync with the method order from
    // ISSense.aidl.
    enum {
        REPORT_DATA            = IBinder::FIRST_CALL_TRANSACTION,
    };

    DECLARE_META_INTERFACE(SSense)

    // The parcels created by these methods must be kept in sync with the
    // corresponding methods from ISSense.aidl.
    // FIXME remove the bool isOneWay parameters as they are not oneway in the .aidl

    virtual void reportData(int type, int data1, int data2, int data3, int data4) = 0;
};

// ----------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_ISSENSE_H