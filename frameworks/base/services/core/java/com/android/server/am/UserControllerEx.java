package com.android.server.am;

import android.content.ComponentName;
import android.content.Intent;
import android.util.Slog;

final class UserControllerEx extends UserController {
    private static final String TAG = "UserControllerEx";
    private final ActivityManagerService mAm;

    UserControllerEx(ActivityManagerService service) {
        super(service);
        mAm = service;
    }

    private final void start3rdPartyPersistentService(final int userId) {
        String[] thirdPartyPersistSvcCompnts =
                ((ActivityManagerServiceEx)mAm).m3rdPartyPersistentSvcCompnts;
        for (int i = 0; i < thirdPartyPersistSvcCompnts.length; i++) {
            ComponentName compName = ComponentName.unflattenFromString(
                    thirdPartyPersistSvcCompnts[i]);
            if (compName != null) {
                int pkgUid = mAm.getPackageManagerInternalLocked().getPackageUid(
                        compName.getPackageName(), 0, userId);
                if (pkgUid != -1) {
                    mAm.backgroundWhitelistUid(pkgUid);
                    Intent intent = new Intent();
                    intent.setComponent(compName);
                    Slog.i(TAG, "Starting 3rd party persisitent service: " + intent);
                    mAm.mContext.startService(intent);
                } else {
                    Slog.i(TAG, "start3rdPartyPersistentService, can not find the package uid for "
                            + compName);
                }
            }
        }
    }

    @Override
    /* package */ void finishUserUnlockedCompleted(UserState uss) {
        super.finishUserUnlockedCompleted(uss);
        start3rdPartyPersistentService(uss.mHandle.getIdentifier());
    }
}
