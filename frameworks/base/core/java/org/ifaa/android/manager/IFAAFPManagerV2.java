package org.ifaa.android.manager;

import java.util.Locale;

import android.content.Context;
import android.content.Intent;
import android.service.ifaa.IIFAAService;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;

public class IFAAFPManagerV2 extends IFAAManagerV2
{
    public static final int COMMAND_OK = 0;
    public static final int COMMAND_FAIL = -1;
    private IIFAAService mService;

    public IFAAFPManagerV2() {
        mService = IIFAAService.Stub.asInterface(ServiceManager.getService("ifaa"));
    }

    public static void logd(String format, Object... args) {
        String log = "";
        log = String.format(Locale.getDefault(), format, args);
        Log.d("IFAAFPManagerV2", log);
    }

    public static void loge(String format, Object... args) {
        String log = "";
        log = String.format(Locale.getDefault(), format, args);
        Log.e("IFAAFPManagerV2", log);
    }

    @Override
    public String getDeviceModel() {
        String deviceMode = "";
        final String mode = SystemProperties.get("ro.product.model", null);
        if (mode != null && mode.startsWith("sp9850kh")) {
            deviceMode = "sprd-9850kh";
        } else if (mode != null && mode.startsWith("sp9850ka")) {
            deviceMode = "sprd-9850ka";
        } else if (mode != null && mode.startsWith("sp9832e")) {
            deviceMode = "sprd-9832e";
        } else if (mode != null && mode.startsWith("sp9850e")) {
            deviceMode = "sprd-9850e";
        } else if (mode != null && mode.startsWith("s9863a")) {
            deviceMode = "sprd-9863a";
        }
        logd("getDeviceModel: " + deviceMode);
        return deviceMode;
    }

    @Override
    public int getSupportBIOTypes(Context context) {
        logd("getSupportBIOTypes");
        return 1;
    }

    @Override
    public int getVersion()
    {
        logd("getVersion");
        return 2;
    }

    @Override
    public byte[] processCmd(Context context, byte[] data) {
        logd("processCmd --- not support!");
        return null;
    }

    @Override
    public byte[] processCmdV2(Context context, byte[] param) {
        // TODO Auto-generated method stub
        if (mService != null) {
            try {
                logd("BEGIN processCmdV2.");
                return mService.processCmdV2(param);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            loge("mService is null!");
        }
        return null;
    }

    @Override
    public int startBIOManager(Context context, int authType) {
        logd("startBIOManager");

        try
        {
           String broadcastIntent = "com.sprd.fingerprint.startBIOManager";
           Intent intent = new Intent(broadcastIntent);
           intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
           context.sendBroadcast(intent);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        return COMMAND_OK;
    }
}
