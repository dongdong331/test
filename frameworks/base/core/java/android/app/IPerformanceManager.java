package android.app;


import android.os.IBinder;
import android.os.IInterface;
import android.content.Intent;
import android.os.RemoteException;

public interface IPerformanceManager extends IInterface {
    String descriptor = "com.android.server.performance";

    int RECLAIM_PROCESS  = IBinder.FIRST_CALL_TRANSACTION;
    int ENALBE_BOOSTKILL = IBinder.FIRST_CALL_TRANSACTION + 1;
    int PROCESS_RECLAIM_ENABLED = IBinder.FIRST_CALL_TRANSACTION + 2;
    int READ_PROC_FILE = IBinder.FIRST_CALL_TRANSACTION + 3;
    int WRITE_PROC_FILE = IBinder.FIRST_CALL_TRANSACTION + 4;
    int EXTRA_FETCH = IBinder.FIRST_CALL_TRANSACTION + 5;
    int EXTRA_FETCH_FREE_DATA = IBinder.FIRST_CALL_TRANSACTION + 6;
    int GET_BLOCKDEV_NAME = IBinder.FIRST_CALL_TRANSACTION + 7;

    public String reclaimProcess(int pid, String type) throws RemoteException;
    public void enableBoostKill(int enable) throws RemoteException;
    public boolean reclaimProcessEnabled() throws RemoteException;
    public String readProcFile(String path) throws RemoteException;
    public void writeProcFile(String path, String value) throws RemoteException;
    public long fetchIfCacheMiss(String path, long[] offset, 
                                int[]length, boolean fetch,
                                boolean lock) throws RemoteException;
    public void freeFetchData(long addr, int length) throws RemoteException;
    public String getBlockDevName(String dirName) throws RemoteException;
}
