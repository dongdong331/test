/*
 ** Copyright 2018 The Spreadtrum.com
 */

#define LOG_TAG "ISSense"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <stdint.h>
#include <sys/types.h>

#include <binder/Parcel.h>

#include <sprdssense/ISSense.h>

namespace android {

class BpSSense : public BpInterface<ISSense>
{
public:
    explicit BpSSense(const sp<IBinder>& impl)
        : BpInterface<ISSense>(impl)
    {
    }


    virtual void reportData(int type, int data1, int data2, int data3, int data4)
    {
        Parcel data, reply;
        data.writeInterfaceToken(ISSense::getInterfaceDescriptor());
        data.writeInt32(type);
        data.writeInt32(data1);
        data.writeInt32(data2);
        data.writeInt32(data3);
        data.writeInt32(data4);
        // This FLAG_ONEWAY is in the .aidl, so there is no way to disable it
        remote()->transact(REPORT_DATA, data, &reply, IBinder::FLAG_ONEWAY);
    }

};

IMPLEMENT_META_INTERFACE(SSense, "android.os.sprdpower.ISSense");

// ----------------------------------------------------------------------------

}; // namespace android
