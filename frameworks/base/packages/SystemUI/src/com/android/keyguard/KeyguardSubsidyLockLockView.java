
package com.android.keyguard;

import com.android.keyguard.EmergencyButton;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.keyguard.R;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.UserHandle;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.os.SystemProperties;
import android.os.Handler;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardSecurityCallback;
import com.android.keyguard.KeyguardSecurityView;
/**
 * Displays a lock screen for subsidylock
 */
public class KeyguardSubsidyLockLockView extends LinearLayout implements KeyguardSecurityView {
    private static final boolean DEBUG = true;
    public static final String LOG_TAG = "KeyguardSubsidylockView";


    private TextView mButtonUnlock;
    private TextView mEnableData;
    private TextView mSetupWifi;
    private TextView mNodataMessage;
    private TextView mLockTitle;
    private TextView mLockeDescription1;
    private TextView mLockeDescription2;
    private RelativeLayout mRecommendArea;
        private int mSubsidyMode = KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_NOT_SUPPORTT;
    private SubscriptionManager mSubscriptionManager = null;
    private TelephonyManager mTelephonyManager = null;
    private IntentFilter mFilter = new IntentFilter();
    private ConnectivityManager mConnectivityManager = (ConnectivityManager) mContext
            .getSystemService(Context.CONNECTIVITY_SERVICE);
    private WifiManager mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    private NetworkInfo mMobileNetworkInfo;
    private NetworkInfo mWifiNetworkInfo;
    protected KeyguardSecurityCallback mCallback;
    KeyguardUpdateMonitorCallback mUpdateMonitorCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onSubsidyDeviceLock(int mode) {
            Log.d(LOG_TAG, "onSubsidyDeviceLock");
            mSubsidyMode = mode;
            if(mode == KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_UNLOCK || mode == KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_UNLOCK_PERMANENTLY ){
                dismissView();
                return;
            }
            resetState();
        };
    };

    public KeyguardSubsidyLockLockView(Context context) {
        this(context, null);
    }

    public KeyguardSubsidyLockLockView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void resetState() {
        mSubscriptionManager = SubscriptionManager.from(getContext());
        mTelephonyManager = TelephonyManager.from(getContext());
        mButtonUnlock = (TextView) findViewById(R.id.unlock_button);
        mEnableData = (TextView) findViewById(R.id.enable_data);
        mNodataMessage = (TextView) findViewById(R.id.nodata_message);
        mSetupWifi = (TextView) findViewById(R.id.setup_wifi);
        mLockTitle = (TextView) findViewById(R.id.lock_title);
        mLockeDescription1 = (TextView) findViewById(R.id.locked_description1);
        mLockeDescription2 = (TextView) findViewById(R.id.locked_description2);
        mRecommendArea = (RelativeLayout) findViewById(R.id.recommend_area);
        mButtonUnlock.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                if(mSubsidyMode == KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_LOCK){
                    mMobileNetworkInfo = mConnectivityManager
                            .getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                    mWifiNetworkInfo = mConnectivityManager
                            .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                    if ((mMobileNetworkInfo != null
                            && mMobileNetworkInfo.isConnected()) || (mWifiNetworkInfo != null
                                    && mWifiNetworkInfo.isConnected())) {
                        // unlock the device to SLC
                        Log.d(LOG_TAG, "unlock");
                        setUnlockingScreen();
                    } else {
                        Log.d(LOG_TAG, "no data");
                        setNoDataScreen();
                    }
                } else if(mSubsidyMode == KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_SWITCH_SIM){
                    handleSwitchSim();
                }
            }
        });
        setEnableDataBtn();
        mEnableData.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                   handleEnableData();
            }
        });
        mSetupWifi.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                handleSetupWifi();
            }
        });
        if(mSubsidyMode == KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_LOCK){
            setLockScreen();
        } else if(mSubsidyMode == KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_SWITCH_SIM){
            setSwitchSimScreen();
        }
    }

    private void handleSwitchSim() {
        Log.d(LOG_TAG, "handleSwitchSim");
        mButtonUnlock.setClickable(false);
        int oldPriSim = mSubscriptionManager.getDefaultDataPhoneId();
        int newPriSim = oldPriSim == 1 ? 0 : 1;
        int[] subIds = SubscriptionManager.getSubId(newPriSim);
        if (subIds == null || subIds.length < 1) return;
        mSubscriptionManager.setDefaultDataSubId(subIds[0]);
    }

    private boolean isAirplaneModeOn() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void handleEnableData() {
        Log.d(LOG_TAG, "setMobileDataEnabled");

        if (isAirplaneModeOn()) {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0);
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", false);
            getContext().sendBroadcastAsUser(intent, UserHandle.ALL);
        }
        mTelephonyManager.setDataEnabled(true);
    }

    private static final String IS_SUBSIDYLOCK = ":settings:subsidylock";
    private void handleSetupWifi() {
        Log.d(LOG_TAG, "handleSetupWifi");
        ComponentName componetName = new ComponentName(
              "com.android.settings",
              "com.android.settings.wifi.WifiPickerActivity");
        Intent wifiSetupIntent = new Intent();
        wifiSetupIntent.setComponent(componetName);
        wifiSetupIntent.putExtra(IS_SUBSIDYLOCK, true);
        wifiSetupIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        mWifiManager.setWifiEnabled(true);
        mContext.startActivity(wifiSetupIntent);
    }

    private void setUnlockingScreen(){
        Log.d(LOG_TAG, "setUnlockingScreen");
        mSubsidyMode = KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_UNLOCKING;
        Intent intent = new Intent(KeyguardSubsidyLockController.ACTION_USER_REQUEST);
        intent.putExtra(KeyguardSubsidyLockController.INTENT_KEY_UNLOCK, true);
        mContext.sendBroadcast(intent);
        KeyguardSubsidyLockController.getInstance(mContext).setSubSidyUnlocking();
        mLockTitle.setText(R.string.unlocking_device_str);
        mLockeDescription1.setText(R.string.unlocking_description_str_1);
        mLockeDescription2.setText(R.string.unlocking_description_str_2);
        mButtonUnlock.setVisibility(View.GONE);
        mEnableData.setVisibility(View.GONE);
    }

    private void setNoDataScreen(){
        Log.d(LOG_TAG, "setNoDataDevice");
        mSubsidyMode = KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_NODATA;
        KeyguardSubsidyLockController.getInstance(mContext).setSubSidyNodata();
        mRecommendArea.setVisibility(View.INVISIBLE);
        mNodataMessage.setVisibility(View.VISIBLE);
    }

    private void setLockScreen(){
        Log.d(LOG_TAG, "setLockScreen");
        mSubsidyMode = KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_LOCK;
        mLockTitle.setText(R.string.locked_str);
        mLockeDescription1.setText(R.string.description_str_1);
        mLockeDescription2.setVisibility(View.VISIBLE);
        mLockeDescription2.setText(R.string.description_str_2);
        mButtonUnlock.setVisibility(View.VISIBLE);
        mButtonUnlock.setText(R.string.unlock_str);
        mNodataMessage.setVisibility(View.INVISIBLE);
        mRecommendArea.setVisibility(View.VISIBLE);
    }

    private void setSwitchSimScreen(){
        Log.d(LOG_TAG, "setSwitchSimScreen");
        mSubsidyMode = KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_SWITCH_SIM;
        mLockTitle.setText(R.string.locked_str);
        mLockeDescription1.setText(R.string.switch_sim_description_str_1);
        mLockeDescription2.setVisibility(View.VISIBLE);
        mLockeDescription2.setText(R.string.switch_sim_description_str_2);
        mButtonUnlock.setText(R.string.switch_sim_str);
    }

    private boolean isAllSimAbsent() {
        boolean isAllSimAbsent = true;
        for (int phoneId = 0; phoneId < mTelephonyManager.getPhoneCount(); phoneId++) {
            if (mTelephonyManager.hasIccCard(phoneId)) {
                isAllSimAbsent = false;
                break;
            }
        }
        Log.d(LOG_TAG, "isAllSimAbsent = " + isAllSimAbsent);
        return isAllSimAbsent;
    }

    private void setEnableDataBtn(){
        Log.d(LOG_TAG, "setEnableDataBtn visable");
        if(mSubsidyMode == KeyguardSubsidyLockController.SUBSIDY_LOCK_SCREEN_MODE_UNLOCKING){
            Log.d(LOG_TAG, "SUBSIDY_LOCK_SCREEN_MODE_UNLOCKING no need show the data");
            return;
        }
        if(isAllSimAbsent()== false && (isAirplaneModeOn() || mTelephonyManager.getDataEnabled() == false) ){
            mEnableData.setVisibility(View.VISIBLE);
        } else {
            mEnableData.setVisibility(View.GONE);
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                Log.d(LOG_TAG, "ACTION_AIRPLANE_MODE_CHANGED");
                setEnableDataBtn();
            }
        }
    };

    private void dismissView() {
        Log.d(LOG_TAG, "dismissView");
        if (mCallback == null) {
            Log.d(LOG_TAG, "mCallback is null");
            return;
        }
        mCallback.dismiss(true, KeyguardUpdateMonitor.getCurrentUser());
    }

    private ContentObserver mMobileDataObserver = new ContentObserver(
            new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Log.d(LOG_TAG, "MobileDataObserver onChange");
            setEnableDataBtn();
        }
    };

    @Override
    protected void onAttachedToWindow() {
        Log.d(LOG_TAG, "onAttachedToWindow");
        super.onAttachedToWindow();
        mFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        getContext().registerReceiver(mBroadcastReceiver, mFilter);
        getContext().getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.MOBILE_DATA +SubscriptionManager.MAX_SUBSCRIPTION_ID_VALUE), true,
                mMobileDataObserver,UserHandle.USER_OWNER);
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mUpdateMonitorCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        Log.d(LOG_TAG, "onDetachedFromWindow");
        super.onDetachedFromWindow();
        getContext().unregisterReceiver(mBroadcastReceiver);
        getContext().getContentResolver().unregisterContentObserver(mMobileDataObserver);
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mUpdateMonitorCallback);
    }

    @Override
    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        mCallback = callback;
    }

    @Override
    public KeyguardSecurityCallback getCallback() {
        return mCallback;
    }

    @Override
    public void showUsabilityHint() {
    }

    @Override
    public void onPause() {
    }

    @Override
    public boolean needsInput() {
        return false;
    }

    @Override
    public void showMessage(CharSequence message, int color) {
    }

    @Override
    public void setLockPatternUtils(LockPatternUtils utils) {
    }

    @Override
    public void startAppearAnimation() {
        // noop.
    }

    @Override
    public boolean startDisappearAnimation(Runnable finishRunnable) {
        return false;
    }

    @Override
    public void onResume(int reason) {
    }

    @Override
    public void showPromptReason(int reason) {
    }

    @Override
    public void reset() {
    }

    @Override
    public CharSequence getTitle() {
        return getContext().getString(
                com.android.internal.R.string.keyguard_accessibility_sim_pin_unlock);
   }
}
