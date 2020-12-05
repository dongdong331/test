package com.android.keyguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.sprd.telephony.RadioInteractor;
import android.util.Log;
import com.android.sprd.telephony.uicc.IccCardStatusEx;
import android.content.res.Resources;
import android.widget.Toast;
import android.view.WindowManager;
import android.os.SystemProperties;
/**
 * KeyguardSubsidyLockController: receive the Broadcast from slc and controller the SubsidyLock UI in Keyguard
 */

public class KeyguardSubsidyLockController  {

    private static final String TAG = "KeyguardSubsidyController";
    private static final String ACTION_SUBSIDYLOCK_STATE = "com.slc.action.ACTION_SUBSIDYLOCK_STATE";
    static final String ACTION_USER_REQUEST = "com.slc.action.ACTION_USER_UNLOCK";
    private static final String ACTION_ENTERCODE = "com.sprd.intent.action.ACTION_ENTERCODE";

    //the intent from slc
    private static final String INTENT_KEY_LOCK_SCREEN = "INTENT_KEY_LOCK_SCREEN";
    private static final String INTENT_KEY_SWITCH_SIM_SCREEN = "INTENT_KEY_SWITCH_SIM_SCREEN";
    private static final String INTENT_KEY_UNLOCK_SCREEN = "INTENT_KEY_UNLOCK_SCREEN";
    private static final String INTENT_KEY_UNLOCK_PERMANENTLY = "INTENT_KEY_UNLOCK_PERMANENTLY";
    private static final String INTENT_KEY_ENTER_CODE_SCREEN = "INTENT_KEY_ENTER_CODE_SCREEN";
    //the intent to slc
    static final String INTENT_KEY_UNLOCK = "INTENT_KEY_UNLOCK";
    static final String INTENT_KEY_PIN_VERIFIED = "INTENT_KEY_PIN_VERIFIED";

    //SubsidyLock mode
    static final int SUBSIDY_LOCK_SCREEN_MODE_NOT_SUPPORTT = -1;
    static final int SUBSIDY_LOCK_SCREEN_MODE_INIT = 0;
    public static final int SUBSIDY_LOCK_SCREEN_MODE_LOCK = 1;
    public static final int SUBSIDY_LOCK_SCREEN_MODE_SWITCH_SIM = 2;
    public static final int SUBSIDY_LOCK_SCREEN_MODE_UNLOCK = 3;
    public static final int SUBSIDY_LOCK_SCREEN_MODE_UNLOCK_PERMANENTLY = 4;
    //these 2 state are temp state when click unlock on lock view
    public static final int SUBSIDY_LOCK_SCREEN_MODE_NODATA = 5;
    public static final int SUBSIDY_LOCK_SCREEN_MODE_UNLOCKING = 6;

    private static final int MSG_SUBSIDY_LOCK_SCREEN = 1;
    private static final int MSG_SUBSIDY_SWITCH_SIM_SCREEN = 2;
    private static final int MSG_SUBSIDY_UNLOCK_SCREEN = 3;
    private static final int MSG_SUBSIDY_UNLOCK_PERMANENTLY_SCREEN = 4;
    private static final int MSG_SUBSIDY_ENTER_CODE_SCREEN = 5;
    private static KeyguardSubsidyLockController sInstance;
    private ArrayList<WeakReference<KeyguardUpdateMonitorCallback>> mCallbacks;
    private boolean mSupportSubsidyLock;
    private int mSubsidyLockMode = SUBSIDY_LOCK_SCREEN_MODE_NOT_SUPPORTT;
    private boolean mSubsidyLockEnterCode = false;
    private boolean mSubsidyLockUnlocking = false;
    private boolean mSubsidyLockNodata = false;
    private Context mContext;

    public static KeyguardSubsidyLockController getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new KeyguardSubsidyLockController(context);
        }
        return sInstance;
    }

    private KeyguardSubsidyLockController(Context context) {
        mSupportSubsidyLock = Resources.getSystem().getBoolean(com.android.internal.R.bool.config_subsidyLock);
        mContext = context;
        if (mSupportSubsidyLock) {
            mCallbacks = KeyguardUpdateMonitor.getInstance(context).getCallbacks();
            mSubsidyLockMode = SystemProperties.getInt("gsm.subsidylock.currentstate", SUBSIDY_LOCK_SCREEN_MODE_INIT);
            final IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_SUBSIDYLOCK_STATE);
            filter.addAction("com.sprd.intent.action.ACTION_ENTERCODE");
            context.registerReceiver(mBroadcastReceiver, filter);
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "received broadcast " + action);

            if (ACTION_SUBSIDYLOCK_STATE.equals(action)) {
                Log.d(TAG, "action " + action );
                if (intent.getBooleanExtra(INTENT_KEY_LOCK_SCREEN, false)){
                    Log.d(TAG, "---- Receive broadcast: INTENT_KEY_LOCK_SCREEN");
                    mHandler.obtainMessage(MSG_SUBSIDY_LOCK_SCREEN).sendToTarget();
                } else if(intent.getBooleanExtra(INTENT_KEY_SWITCH_SIM_SCREEN, false)){
                    Log.d(TAG, "---- Receive broadcast: INTENT_KEY_SWITCH_SIM_SCREEN");
                    mHandler.obtainMessage(MSG_SUBSIDY_SWITCH_SIM_SCREEN).sendToTarget();
                } else if(intent.getBooleanExtra(INTENT_KEY_UNLOCK_SCREEN, false)){
                    Log.d(TAG, "---- Receive broadcast: INTENT_KEY_UNLOCK_SCREEN");
                    mHandler.obtainMessage(MSG_SUBSIDY_UNLOCK_SCREEN).sendToTarget();
                } else if (intent.getBooleanExtra(INTENT_KEY_UNLOCK_PERMANENTLY, false)) {
                    Log.d(TAG, "---- Receive broadcast: INTENT_KEY_UNLOCK_PERMANENTLY");
                    mHandler.obtainMessage(MSG_SUBSIDY_UNLOCK_PERMANENTLY_SCREEN).sendToTarget();
                } else {
                    Log.d(TAG, "No Valid Extra For SubsidyLock");
                }
            } else if ("com.sprd.intent.action.ACTION_ENTERCODE".equals(action)){
                Log.d(TAG, "---- Receive broadcast: INTENT_KEY_ENTER_CODE_SCREEN");
                RadioInteractor radioInteractor = new RadioInteractor(context);
                int isNetworkLock = radioInteractor.getSimLockStatus(IccCardStatusEx.UNLOCK_NETWORK, 0);
                Log.d(TAG, "isNetworkLock = " + isNetworkLock);
                if (isNetworkLock == 1 && (mSubsidyLockMode == SUBSIDY_LOCK_SCREEN_MODE_LOCK || mSubsidyLockMode == SUBSIDY_LOCK_SCREEN_MODE_SWITCH_SIM)){
                    mHandler.obtainMessage(MSG_SUBSIDY_ENTER_CODE_SCREEN).sendToTarget();
                } else {
                    Log.d(TAG, "network not set, unable to unlock");
                }
            }
        }};

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SUBSIDY_LOCK_SCREEN:
                    handleSubsidyDeviceLock(SUBSIDY_LOCK_SCREEN_MODE_LOCK);
                    break;
                case MSG_SUBSIDY_SWITCH_SIM_SCREEN:
                    handleSubsidyDeviceLock(SUBSIDY_LOCK_SCREEN_MODE_SWITCH_SIM);
                    break;
                case MSG_SUBSIDY_UNLOCK_SCREEN:
                    handleSubsidyDeviceLock(SUBSIDY_LOCK_SCREEN_MODE_UNLOCK);
                    break;
                case MSG_SUBSIDY_ENTER_CODE_SCREEN:
                    handleSubsidyEnterCode();
                    break;
                case MSG_SUBSIDY_UNLOCK_PERMANENTLY_SCREEN:
                    handleSubsidyDeviceLock(SUBSIDY_LOCK_SCREEN_MODE_UNLOCK_PERMANENTLY);
                    break;
             }
        }
    };

    public void handleSubsidyDeviceLock(int mode) {
        Log.d(TAG, "handleSubsidyDeviceLock mSubsidyLockUnlocking = " + mSubsidyLockUnlocking);
        if (mode == mSubsidyLockMode && !mSubsidyLockUnlocking && !mSubsidyLockNodata) {
            Log.d(TAG, "SubsidyLock state not change, return");
            return;
        } else if (mode == SUBSIDY_LOCK_SCREEN_MODE_UNLOCK_PERMANENTLY) {
            Log.d(TAG, "show unlock toast for SUBSIDY_LOCK_SCREEN_MODE_UNLOCK_PERMANENTLY");
            Resources rez = mContext.getResources();
            final String msg = rez.getString(R.string.device_unlocked);
            Toast toast = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
            toast.getWindowParams().type = WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL;
            toast.getWindowParams().privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
            toast.getWindowParams().flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
            toast.show();
        }
        mSubsidyLockMode = mode;
        mSubsidyLockEnterCode = false;
        mSubsidyLockUnlocking = false;
        mSubsidyLockNodata = false;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onSubsidyDeviceLock(mode);
            }
        }
    }

    public boolean isSubsidyLock(){
        if (!mSupportSubsidyLock) {
            Log.d(TAG, "not Support SubsidyLock");
            return false;
        }
        return mSubsidyLockMode == SUBSIDY_LOCK_SCREEN_MODE_SWITCH_SIM
            || mSubsidyLockMode == SUBSIDY_LOCK_SCREEN_MODE_LOCK
            || mSubsidyLockMode == SUBSIDY_LOCK_SCREEN_MODE_INIT
            || mSubsidyLockEnterCode;
    }

    public int getSubsidyLockMode(){
        if (mSupportSubsidyLock) {
            return mSubsidyLockMode;
        } else {
            return SUBSIDY_LOCK_SCREEN_MODE_NOT_SUPPORTT;
        }
    }

    public boolean isSubsidyEnterCode(){
        if (!mSupportSubsidyLock) {
            return false;
        }
        return mSubsidyLockEnterCode;
    }

    public void resetSubSidyEnterCode(){
        mSubsidyLockEnterCode = false;
    }

    public void setSubSidyUnlocking() {
        mSubsidyLockUnlocking = true;
    }

    public void setSubSidyNodata() {
        mSubsidyLockNodata = true;
    }

    private void handleSubsidyEnterCode() {
        Log.d(TAG, "handleSubsidyEnterCode");
        mSubsidyLockEnterCode = true;
        mSubsidyLockUnlocking = false;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onSubsidyEnterCode();
            }
        }
    }
}
