package com.android.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IPackageMoveObserver;
import android.content.pm.PackageManager;
import android.os.IVoldTaskListener;
import android.os.PersistableBundle;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.EnvironmentEx;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.storage.IStorageManager;
import android.os.storage.IStorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.storage.VolumeInfo;
import android.util.ArrayMap;
import android.util.Slog;
import com.android.server.StorageManagerService.Callbacks;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.os.SomeArgs;

public abstract class StorageManagerServiceEx extends IStorageManager.Stub {
    private static final String TAG = "StorageManagerServiceEx";
    protected volatile boolean mDebug = true;
    protected static final String ATTR_PRIMARY_EMULATED_UUID = "primaryEmulatedUuid";
    @GuardedBy("mLock")
    protected StorageManagerService mStorageManagerService;
    @GuardedBy("mLock")
    protected String mPrimaryEmulatedUuid;
    @GuardedBy("mLock")
    protected boolean mSetEmulated;

    /* @SPRD: add for UMS */
    protected static final int H_VOLUME_UNSHARED_BROADCAST = 20;

    //protected static final int H_VOLUME_MOUNT = 5;

    //protected static final int H_RESET = 10;

    /* Disable App2Sd. SPRD: support double sdcard add for sdcard hotplug @{
    class VolumesPresentState {
        boolean mVolumeBadRemoved = false;
    }

    protected VolumesPresentState mVolumesPresentState = new VolumesPresentState();
    */

    protected void sendPriEmulatedVolumeMounted(VolumeInfo vol, VolumeInfo privateVol){
        if (Objects.equals(StorageManager.UUID_PRIVATE_INTERNAL, mPrimaryEmulatedUuid)
                && VolumeInfo.ID_PRIVATE_INTERNAL.equals(privateVol.id)) {
            Slog.v(TAG, "Found primary emulated storage at " + vol);
            vol.mountFlags |= VolumeInfo.MOUNT_FLAG_PRI_EMU;
            vol.mountFlags |= VolumeInfo.MOUNT_FLAG_VISIBLE;
            mStorageManagerService.mHandler.obtainMessage(mStorageManagerService.H_VOLUME_MOUNT, vol).sendToTarget();
       } else if (Objects.equals(privateVol.fsUuid, mPrimaryEmulatedUuid)) {
            Slog.v(TAG, "Found primary emulated storage at " + vol);
            vol.mountFlags |= VolumeInfo.MOUNT_FLAG_PRI_EMU;
            vol.mountFlags |= VolumeInfo.MOUNT_FLAG_VISIBLE;
            mStorageManagerService.mHandler.obtainMessage(mStorageManagerService.H_VOLUME_MOUNT, vol).sendToTarget();
       }
    }

    protected void sendUnsharedBroadcastAndUpdatePms(int oldState, int newState, VolumeInfo vol,
            int[] startedUsers) {
        if (oldState == VolumeInfo.STATE_SHARED && newState != oldState) {
            for (int userId : startedUsers) {
                if (vol.isVisibleForRead(userId)) {
                    final StorageVolume userVol = vol.buildStorageVolume(mStorageManagerService.mContext, userId, false);
                    mStorageManagerService.mHandler.obtainMessage(H_VOLUME_UNSHARED_BROADCAST, userVol).sendToTarget();
                }
            }
        }
        /* Disable App2Sd. SPRD: support double sdcard add for sdcard hotplug
        if (vol.type == VolumeInfo.TYPE_PUBLIC && (Objects.equals(vol.linkName,"sdcard0")
                || Objects.equals(vol.linkName,"sdcard1")) && newState == VolumeInfo.STATE_MOUNTED) {
            synchronized (mVolumesPresentState) {
                if(mVolumesPresentState.mVolumeBadRemoved){
                    synchronized (mStorageManagerService.mAsecMountSet) {
                        mStorageManagerService.mAsecMountSet.clear();
                    }
                    mVolumesPresentState.mVolumeBadRemoved = false;
                }
                }
        } else if(vol.type == VolumeInfo.TYPE_PUBLIC && (Objects.equals(vol.linkName,"sdcard0")
                || Objects.equals(vol.linkName,"sdcard1")) && newState == VolumeInfo.STATE_EJECTING){
            synchronized (mVolumesPresentState) {
                mVolumesPresentState.mVolumeBadRemoved = true;
            }
        }
        */
    }

    protected void setPrimaryExternalProperties() {
        if (SystemProperties.getBoolean(StorageManager.PROP_PRIMARY_PHYSICAL, false)) {
            SystemProperties.set(StorageManager.PROP_PRIMARY_TYPE,
                    String.valueOf(EnvironmentEx.STORAGE_PRIMARY_EXTERNAL));
        }
    }

    protected VolumeInfo findSourceVolume(VolumeInfo to){
        VolumeInfo from;
        if ((to != null && to.getType() == VolumeInfo.TYPE_EMULATED)
                && !Objects.equals(mStorageManagerService.mMoveTargetUuid, mPrimaryEmulatedUuid)) {
            from = mStorageManagerService.findStorageForUuid(mPrimaryEmulatedUuid);
        } else {
            from = mStorageManagerService.findStorageForUuid(mStorageManagerService.mPrimaryStorageUuid);
        }
        return from;
    }
    protected boolean skipMove(VolumeInfo from ,VolumeInfo to){
        return (to != null && (to.getType() == VolumeInfo.TYPE_PUBLIC
                || (from == null && Objects.equals(StorageManager.UUID_PRIMARY_PHYSICAL, mStorageManagerService.mPrimaryStorageUuid))
                || (from != null && from.getType() == VolumeInfo.TYPE_PUBLIC)));
    }
    protected void sprdJustResetOrNot(String fsUuid){
        boolean needReset = false;
        if (Objects.equals(mStorageManagerService.mPrimaryStorageUuid, fsUuid)) {
            mStorageManagerService.mPrimaryStorageUuid = mStorageManagerService.getDefaultPrimaryStorageUuid();
            needReset = true;
            if (SystemProperties.getBoolean(StorageManager.PROP_PRIMARY_PHYSICAL, false)) {
                SystemProperties.set(StorageManager.PROP_PRIMARY_TYPE,
                        String.valueOf(EnvironmentEx.STORAGE_PRIMARY_EXTERNAL));
            }
        }
        if (Objects.equals(mPrimaryEmulatedUuid, fsUuid)) {
            mPrimaryEmulatedUuid = StorageManager.UUID_PRIVATE_INTERNAL;
            needReset = true;
        }
        if (needReset) {
        	mStorageManagerService.mHandler.obtainMessage(mStorageManagerService.H_RESET).sendToTarget();
        }
    }

    @Override
    public String getPrimaryEmulatedStorageUuid() {
        synchronized (mStorageManagerService.mLock) {
            return mPrimaryEmulatedUuid;
        }
    }
    protected StorageManagerService getStorageManagerService(){
        StorageManagerService ms = StorageManagerService.sSelf;
            return ms;
    }
    /* SPRD: add for emulated storage @{ */
    @Override
    public void setPrimaryEmulatedStorageUuid(String volumeUuid, IPackageMoveObserver callback) {
         mStorageManagerService.enforcePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        final VolumeInfo target = mStorageManagerService.findStorageForUuid(volumeUuid);

        if (target == null || target.getType() != VolumeInfo.TYPE_EMULATED) {
            throw new IllegalArgumentException("Target volume[" + volumeUuid + "] is not emulated");
        }
        final VolumeInfo from;
        final VolumeInfo to;
        synchronized (mStorageManagerService.mLock) {
            if (Objects.equals(mPrimaryEmulatedUuid, volumeUuid)) {
                throw new IllegalArgumentException("Primary emulated storage already at " + volumeUuid);
            }

            if (mStorageManagerService.mMoveCallback != null) {
                throw new IllegalStateException("Move already in progress");
            }
            mStorageManagerService.mMoveCallback = callback;
            mStorageManagerService.mMoveTargetUuid = volumeUuid;
            mSetEmulated = true;

            // Here just move from emulated storage to emulated storage
            {
                from = mStorageManagerService.findStorageForUuid(mPrimaryEmulatedUuid);
                to = mStorageManagerService.findStorageForUuid(volumeUuid);

                if (from == null) {
                    Slog.w(TAG, "Failing move due to missing from volume " + mPrimaryEmulatedUuid);
                    mStorageManagerService.onMoveStatusLocked(PackageManager.MOVE_FAILED_INTERNAL_ERROR);
                    return;
                } else if (to == null) {
                    Slog.w(TAG, "Failing move due to missing to volume " + volumeUuid);
                    mStorageManagerService.onMoveStatusLocked(PackageManager.MOVE_FAILED_INTERNAL_ERROR);
                    return;
                }
            }
        }

        try {
            slogi(TAG, "Cmd moveStorage from " + from.id + " to " + to.id);
            mStorageManagerService.mVold.moveStorage(from.id, to.id, new IVoldTaskListener.Stub() {
                @Override
                public void onStatus(int status, PersistableBundle extras) {
                    synchronized (mStorageManagerService.mLock) {
                        mStorageManagerService.onMoveStatusLocked(status);
                    }
                }

                @Override
                public void onFinished(int status, PersistableBundle extras) {
                    // Not currently used
                }
            });
        } catch (Exception e) {
            Slog.wtf(TAG, e);
        }
    }
    public String onMovestatusLockedEx(){
        if (mSetEmulated) {
            /* when we migrate data, if primary is emulated, we should
             * keep primary storage as same as mounted emulated storage
             */
            if (Objects.equals(mStorageManagerService.mPrimaryStorageUuid, mPrimaryEmulatedUuid)) {
                mStorageManagerService.mPrimaryStorageUuid = mStorageManagerService.mMoveTargetUuid;
            }
            mPrimaryEmulatedUuid = mStorageManagerService.mMoveTargetUuid;
        } else {
            final VolumeInfo target = mStorageManagerService.findStorageForUuid(mStorageManagerService.mMoveTargetUuid);
            /* when we set primary, if target is phsical, we should
             * set mPrimaryStorageUuid as UUID_PRIMARY_PHYSICAL and
             */
            if (target != null && target.getType() == VolumeInfo.TYPE_PUBLIC) {
                mStorageManagerService.mPrimaryStorageUuid = StorageManager.UUID_PRIMARY_PHYSICAL;
                /* set priamry type as external primary */
                SystemProperties.set(StorageManager.PROP_PRIMARY_TYPE,
                                         String.valueOf(EnvironmentEx.STORAGE_PRIMARY_EXTERNAL));
            } else {
                /* when we set primary, if target is emulated, we should
                 * keep mounted emulated storage as same as primary storage
                 */
                if (target == null || target.getType() == VolumeInfo.TYPE_EMULATED) {
                    mPrimaryEmulatedUuid = mStorageManagerService.mMoveTargetUuid;
                    /* set priamry type as internal primary */
                    SystemProperties.set(StorageManager.PROP_PRIMARY_TYPE,
                            String.valueOf(EnvironmentEx.STORAGE_PRIMARY_INTERNAL));
                }
                mStorageManagerService.mPrimaryStorageUuid = mStorageManagerService.mMoveTargetUuid;
            }
        }
        return mStorageManagerService.mPrimaryStorageUuid;
    }
    /* @} */

    protected void slogi(String tag, String logstring) {
        if (mDebug) Slog.i(tag, logstring);
    }
}
