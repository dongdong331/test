package com.android.keyguard;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.UserHandle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.util.SimLockUtil;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.sprd.telephony.RadioInteractor;
import com.android.sprd.telephony.uicc.IccCardStatusEx;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class KeyguardSimLockMonitor {
    private final static String TAG = "KeyguardSimLockMonitor";

    private final Context mContext;
    private static KeyguardSimLockMonitor sInstance;

    public static final String SECRECT_CODE_UNLOCK_BY_SIM = "0808";
    public static final String SECRECT_CODE_UNLOCK_BY_NV = "2413";

    public boolean mIsAutoShow;
    public boolean mIsBynv;

    private RadioInteractor mRadioInteractor = null;
    private SimLockUtil mSimLockUtil = null;
    private static State mNvState = State.READY;
    private static boolean mSimLockBySim;
    private ArrayList<WeakReference<KeyguardUpdateMonitorCallback>> mCallbacks;

    private static boolean mSimLockCanceld;

    public static KeyguardSimLockMonitor getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new KeyguardSimLockMonitor(context);
        }
        return sInstance;
    }

    protected KeyguardSimLockMonitor(Context context) {
        mContext = context;

        addRadioInteractorListener();

        mSimLockUtil = SimLockUtil.getInstance(mContext);

        mIsAutoShow = SimLockUtil.isAutoShow();
        mIsBynv = SimLockUtil.isByNv();
        Log.d(TAG, "mIsAutoShow= " + mIsAutoShow + ", mIsBynv= " + mIsBynv);

        final IntentFilter secCodeFilter = new IntentFilter();
        secCodeFilter.addAction("android.provider.Telephony.SECRET_CODE");
        secCodeFilter.addDataScheme("android_secret_code");
        context.registerReceiverAsUser(mSecrectCodeReceiver, UserHandle.ALL, secCodeFilter,
                null, null);
    }

    private void addRadioInteractorListener(){
        mContext.bindService(new Intent("com.android.sprd.telephony.server.RADIOINTERACTOR_SERVICE")
        .setPackage("com.android.sprd.telephony.server"), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "on radioInteractor service connected");
                if (mRadioInteractor == null) {
                    mRadioInteractor = new RadioInteractor(mContext);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        }, Context.BIND_AUTO_CREATE);
    }

    final BroadcastReceiver mSecrectCodeReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "received broadcast " + action);
            String host = null;
            Uri uri = intent.getData();
            if (uri != null) {
                host = uri.getHost();
            } else {
                Log.d(TAG, "uri is null");
                return;
            }
            Log.d(TAG, "host: " + host);
            if (SECRECT_CODE_UNLOCK_BY_SIM.equals(host)) {
                showUnlockBySim();
            } else if (SECRECT_CODE_UNLOCK_BY_NV.equals(host)) {
                showUnlockByNv();
            } else {
                Log.d(TAG, "Unhandle host [" + host + "]");
            }
        }
    };

    /*Unlock by NV @{*/
    private void showUnlockByNv (){
        Log.d(TAG, "showUnlockByNv");
        if (!mIsBynv) {
            Log.d(TAG, "Simlock unlock by nv turned off!");
            return;
        } else {
            setSimLockCanceled(false);
            int isNetworkLock = mRadioInteractor.getSimLockStatus(IccCardStatusEx.UNLOCK_NETWORK, 0);
            int isNetworkSubsetLock = mRadioInteractor.getSimLockStatus(IccCardStatusEx.UNLOCK_NETWORK_SUBSET, 0);
            int isServiceProviderLock = mRadioInteractor.getSimLockStatus(IccCardStatusEx.UNLOCK_SERVICE_PORIVDER, 0);
            int isCorporateLock = mRadioInteractor.getSimLockStatus(IccCardStatusEx.UNLOCK_CORPORATE, 0);
            int isSimLock = mRadioInteractor.getSimLockStatus(IccCardStatusEx.UNLOCK_SIM, 0);

            Log.d(TAG, "isNetworkLock: " + isNetworkLock + ", isNetworkSubsetLock=" + isNetworkSubsetLock +
                    ", isServiceProviderLock= " + isServiceProviderLock + ", isCorporateLock=" + isCorporateLock + ", isSimLock=" + isSimLock);

            //Need to show simlock unlock view, when any kind of simlock is locked state.
            if (isNetworkLock > 0 || isNetworkSubsetLock > 0 || isServiceProviderLock > 0
                    || isCorporateLock > 0 || isSimLock > 0) {
                //Sim locked permanently
                if (TelephonyManager.from(mContext).getSimState(0) == IccCardStatusEx.SIM_STATE_SIM_LOCKED_FOREVER) {
                    Log.d(TAG, "SIM locked permanently! No need to show unlock view!");
                    return;
                }

                //Get sim state
                mNvState = mSimLockUtil.getStateByLockStatus(isNetworkLock,isNetworkSubsetLock,isServiceProviderLock,isCorporateLock,isSimLock);
                mCallbacks = KeyguardUpdateMonitor.getInstance(mContext).getCallbacks();
                for (int i = 0; i < mCallbacks.size(); i++) {
                    KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
                    if (cb != null) {
                        cb.onShowSimLockByNv();
                    }
                }
            } else if (isNetworkLock == 0 && isNetworkSubsetLock == 0 && isServiceProviderLock == 0
                    && isCorporateLock == 0 && isSimLock == 0 ) {
                Toast.makeText(mContext,
                        mContext.getString(R.string.simlock_unlocked),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(mContext,
                        mContext.getString(R.string.sim_lock_try_later),
                        Toast.LENGTH_LONG).show();
            }
        }
    }
    /* @} */


    /* Unlock By Sim *@{*/
    private void showUnlockBySim(){
        if (SimLockUtil.isAutoShow()) {
            Log.d(TAG, "Return for autoshow is turned on.");
            return;
        } else {
            setSimLockCanceled(false);
            KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(mContext);
            if (SubscriptionManager.isValidSubscriptionId(monitor.getNextSubIdForState(IccCardConstants.State.NETWORK_LOCKED))
                    || SubscriptionManager.isValidSubscriptionId(monitor.getNextSubIdForState(IccCardConstants.State.NETWORK_SUBSET_LOCKED))
                    || SubscriptionManager.isValidSubscriptionId(monitor.getNextSubIdForState(IccCardConstants.State.SERVICE_PROVIDER_LOCKED))
                    || SubscriptionManager.isValidSubscriptionId(monitor.getNextSubIdForState(IccCardConstants.State.CORPORATE_LOCKED))
                    || SubscriptionManager.isValidSubscriptionId(monitor.getNextSubIdForState(IccCardConstants.State.SIM_LOCKED))
                    || SubscriptionManager.isValidSubscriptionId(monitor.getNextSubIdForState(IccCardConstants.State.NETWORK_LOCKED_PUK))
                    || SubscriptionManager.isValidSubscriptionId(monitor.getNextSubIdForState(IccCardConstants.State.NETWORK_SUBSET_LOCKED_PUK))
                    || SubscriptionManager.isValidSubscriptionId(monitor.getNextSubIdForState(IccCardConstants.State.SERVICE_PROVIDER_LOCKED_PUK))
                    || SubscriptionManager.isValidSubscriptionId(monitor.getNextSubIdForState(IccCardConstants.State.CORPORATE_LOCKED_PUK))
                    || SubscriptionManager.isValidSubscriptionId(monitor.getNextSubIdForState(IccCardConstants.State.SIM_LOCKED_PUK))) {
                mSimLockBySim = true;
                mCallbacks = KeyguardUpdateMonitor.getInstance(mContext).getCallbacks();
                for (int i = 0; i < mCallbacks.size(); i++) {
                    KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
                    if (cb != null) {
                        cb.onShowSimLockBySim();
                    }
                }
            } else {
                Toast.makeText(mContext,
                        mContext.getString(R.string.sim_lock_none),
                        Toast.LENGTH_LONG).show();
            }
        }
    }
    /* @} */

    public State getNvState() {
        return mNvState;
    }

    //by nv or by sim
    public static boolean isSimLockStateByUser() {
        if (mSimLockBySim) {
            return true;
        } else {
            return mNvState.isSimlockLocked();
        }
    }

    //report simlock unlocked by nv
    public void reportUnlockedByNV() {
        Log.d(TAG, "reportUnlockedByNV");
        mNvState = State.READY;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onShowSimLockByNv();
            }
        }
    }

    //report simlock unlocked by sim
    public void reportUnlockedBySIM() {
        Log.d(TAG, "reportUnlockedBySIM");
        mSimLockBySim = false;
    }

    /*For dismiss simlock @{ */
    public void setSimLockCanceled(boolean canceled){
        Log.d(TAG, "setSimLockCanceled");
        mSimLockCanceld = canceled;
    }

    public static boolean getSimLockCanceled(){
        return mSimLockCanceld;
    }

    public void reportSimLockCanceled(int subId){
        Log.d(TAG, "reportSimLockByNvCanceled");
        if (SimLockUtil.isByNv()) {
            reportUnlockedByNV();
        } else {
            int slotId = SubscriptionManager.getSlotIndex(subId);
            KeyguardUpdateMonitor.getInstance(mContext).handleSimStateChange(subId, slotId, State.READY);
        }
    }
    /* @} */

    public static boolean isSimLockShowing(State state) {
        // True if simlock secure view is showing
        boolean isOnekeyLock = SimLockUtil.isOnekeyLock();
        boolean isAutoShow = SimLockUtil.isAutoShow();
        boolean canceled = getSimLockCanceled();
        return !isOnekeyLock && !canceled
                && ((isAutoShow && SimLockUtil.isSimlockState(state)
                        && state != IccCardConstants.State.SIM_LOCKED_PERMANENTLY)
                        || isSimLockStateByUser());
    }
}
