package com.android.server.ifaa;

import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IHwBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserManager;
import android.util.Slog;
import com.android.server.SystemService;
import android.service.ifaa.IIFAAService;
import vendor.sprd.hardware.ifaa.V1_0.IIfaa;
import java.util.ArrayList;
import java.util.List;

public class IFAAService extends SystemService implements IHwBinder.DeathRecipient {

    static final String TAG = "IFAAService";
    private IIfaa mDaemon;
    private final Object mLock = new Object();
    private Context mContext;

    private Handler mHandler = new Handler(Looper.myLooper());

    public IFAAService(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void serviceDied(long cookie) {
        Slog.v(TAG, "ifaa HAL died");
        mDaemon = null;
    }

    public IIfaa getIFAADaemon() {
        if (mDaemon == null) {
            try {
                mDaemon = IIfaa.getService();

            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to open iffad!", e);
                mDaemon = null;
            }
            if (mDaemon != null) {
                mDaemon.asBinder().linkToDeath(this, 0);
                Slog.d(TAG, "getIFAADaemon success!");
            } else {
                Slog.w(TAG, "ifaa service not available");
            }
        }
        return mDaemon;
    }

    public void onStart() {
        // TODO Auto-generated method stub
        Slog.d(TAG, "onStart begin.");
        publishBinderService("ifaa", new IFAAServiceWrapper());
    }

    private final class IFAAServiceWrapper extends IIFAAService.Stub {
        @Override
        public byte[] processCmdV2(byte[] param) {
            byte[] response = null;
            IIfaa daemon = getIFAADaemon();
            if (daemon != null) {
                try {
                    response = toByteArray(daemon.processCmdV2(toByteArrayList(param)));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            return response;
        }
    }

    private ArrayList toByteArrayList(byte[] data) {
        if (data == null) {
            return null;
        }
        ArrayList<Byte> result = new ArrayList<Byte>(data.length);
        for (final byte b : data) {
            result.add(b);
        }
        return result;
    }

    private byte[] toByteArray(List<Byte> in) {
        final int n = in.size();
        byte ret[] = new byte[n];
        for (int i = 0; i < n; i++) {
            ret[i] = in.get(i);
        }
        return ret;
    }
}
