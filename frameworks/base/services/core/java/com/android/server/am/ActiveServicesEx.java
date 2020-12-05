package com.android.server.am;

import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.os.Binder;
import android.os.Process;
import android.util.Slog;

public final class ActiveServicesEx extends ActiveServices {
    private static final String TAG = "ActiveServicesEx";

    ActiveServicesEx(ActivityManagerService service) {
        super(service);
    }

    @Override
    List<ActivityManager.RunningServiceInfo> getRunningServiceInfoLocked(int maxNum, int flags,
        int callingUid, boolean allowed, boolean canInteractAcrossUsers) {
        List<RunningServiceInfo> res = super.getRunningServiceInfoLocked(maxNum, flags, callingUid,
                allowed, canInteractAcrossUsers);
        String[] thirdPartyPersistSvcProcs =
                ((ActivityManagerServiceEx)mAm).m3rdPartyPersistentSvcProcs;
        List<RunningServiceInfo> resCopy = new ArrayList(res);

        for (RunningServiceInfo rsi: resCopy) {
            if (rsi != null && rsi.process != null
                    && callingUid > Process.FIRST_APPLICATION_UID) {
                for (int i = 0; i < thirdPartyPersistSvcProcs.length; i++) {
                    if (rsi.process.equals(thirdPartyPersistSvcProcs[i])) {
                        Slog.i(TAG, "getRunningServiceInfoLocked: Removing rsi:" + rsi.process
                                + " from result" + " for caller[uid]:" + callingUid);
                        res.remove(rsi);
                    }
                }
            }
        }
        return res;
    }
}