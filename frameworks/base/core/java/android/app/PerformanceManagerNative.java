/**
 * Copyright (C) 2017 Spreadtrum Communications Inc.
 */
package android.app;


import android.os.Binder;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.os.IProgressListener;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StrictMode;
import android.text.TextUtils;
import android.util.Log;
import android.util.Singleton;

import java.util.ArrayList;
import java.util.List;



/** {@hide} */
public class PerformanceManagerNative extends Binder implements IPerformanceManager
{
    /**
     * Cast a Binder object into an native performance manager interface, generating
     * a proxy if needed.
     */
    static public IPerformanceManager asInterface(IBinder obj) {
        if (obj == null) {
            return null;
        }
        IPerformanceManager in =
            (IPerformanceManager)obj.queryLocalInterface(descriptor);
        if (in != null) {
            return in;
        }

        return new PerformanceManagerProxy(obj);
    }

    /**
     * Retrieve the system's default/global performance manager.
     */
    static public IPerformanceManager getDefault() {
        return gDefault.get();
    }
    public PerformanceManagerNative() {
        attachInterface(this, descriptor);
    }

    public IBinder asBinder() {
        return this;
    }

    private static final Singleton<IPerformanceManager> gDefault = new Singleton<IPerformanceManager>() {
        protected IPerformanceManager create() {
            IBinder b = ServiceManager.getService("performancemanager");
            IPerformanceManager pm = asInterface(b);
            return pm;
        }
    };

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        switch (code) {
            case RECLAIM_PROCESS:
            {
                return true;
            }
            case ENALBE_BOOSTKILL:{
                return true;
            }
            case PROCESS_RECLAIM_ENABLED:{
                return true;
            }
            case READ_PROC_FILE: {
                return true;
            }
            case WRITE_PROC_FILE: {
                return true;
            }
            case EXTRA_FETCH: {
                return true;
            }
            case EXTRA_FETCH_FREE_DATA: {
                return true;
            }
            case GET_BLOCKDEV_NAME: {
                return true;
            }
            default:
            return super.onTransact(code, data, reply, flags);
        }
    }
    public String reclaimProcess(int pid, String type) throws RemoteException{
        Log.d("PerformanceManager", "dummy");
        return "";
    }

    public void enableBoostKill(int enable) throws RemoteException{
        Log.d("PerformanceManager", "dummy");
    }

    public boolean reclaimProcessEnabled() throws RemoteException{
        Log.d("PerformanceManager", "dummy");
        return false;
    }

    public String readProcFile(String path) throws RemoteException{
        Log.d("PerformanceManager", "dummy");
        return "";
    }

    public void writeProcFile(String path, String value) throws RemoteException{
        Log.d("PerformanceManager", "dummy");
        return ;
    }
    public long fetchIfCacheMiss(String path, long[] offset, 
                                int[]length, boolean fetch,
                                boolean lock) {
        return 0;
    }

    public void freeFetchData(long addr, int length){}

    public String getBlockDevName(String dirName){
        return "";
    }
}
class PerformanceManagerProxy implements IPerformanceManager
{
    private IBinder mRemote;
    public PerformanceManagerProxy(IBinder remote)
    {
        mRemote = remote;
    }

    public IBinder asBinder()
    {
        return mRemote;
    }
    @Override
    public String reclaimProcess(int pid, String type) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IPerformanceManager.descriptor);
        data.writeInt(pid);
        data.writeString(type);
        mRemote.transact(RECLAIM_PROCESS, data, reply, 0);
        String result = reply.readString();
        data.recycle();
        reply.recycle();
        return result;
    }
    @Override
    public void enableBoostKill(int enable) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IPerformanceManager.descriptor);
        data.writeInt(enable);
        mRemote.transact(ENALBE_BOOSTKILL, data, reply, 0);
        data.recycle();
        reply.recycle();
    }

    @Override
    public boolean reclaimProcessEnabled() throws RemoteException
    {
        boolean enable = false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IPerformanceManager.descriptor);
        mRemote.transact(PROCESS_RECLAIM_ENABLED, data, reply, 0);
        enable = reply.readInt() == 1 ? true : false;
        data.recycle();
        reply.recycle();
        return enable;
    }

    @Override
    public String readProcFile(String file) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IPerformanceManager.descriptor);
        data.writeString(file);
        mRemote.transact(READ_PROC_FILE, data, reply, 0);
        String result = reply.readString();
        data.recycle();
        reply.recycle();
        return result;
    }

    @Override
    public void writeProcFile(String file, String value) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IPerformanceManager.descriptor);
        data.writeString(file);
        data.writeString(value);
        mRemote.transact(WRITE_PROC_FILE, data, reply, 0);
        data.recycle();
        reply.recycle();
    }

    @Override
    public long fetchIfCacheMiss(String path, long[] offset, 
                                int[]length, boolean fetch,
                                boolean lock) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IPerformanceManager.descriptor);
        data.writeString(path);
        data.writeLongArray(offset);
        data.writeIntArray(length);
        data.writeInt(fetch ? 1:0);
        data.writeInt(lock? 1:0);
        mRemote.transact(EXTRA_FETCH, data, reply, 0);
        long addr = reply.readLong();
        data.recycle();
        reply.recycle();
        return addr;
    }

    @Override
    public void freeFetchData(long addr, int length) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IPerformanceManager.descriptor);
        data.writeLong(addr);
        data.writeInt(length);
        mRemote.transact(EXTRA_FETCH_FREE_DATA, data, reply, 0);
        data.recycle();
        reply.recycle();
    }
    @Override
    public String getBlockDevName(String dirName) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IPerformanceManager.descriptor);
        data.writeString(dirName);
        mRemote.transact(GET_BLOCKDEV_NAME, data, reply, 0);
        String result = reply.readString();
        data.recycle();
        reply.recycle();
        return result;
    }
}

