package com.android.systemui.qs.tiles;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.provider.SettingsEx;
import android.service.quicksettings.Tile;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManagerEx;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.Switch;
import com.android.ims.internal.ImsManagerEx;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.qs.DetailAdapter;
import com.android.systemui.plugins.qs.QSIconView;
import com.android.systemui.plugins.qs.QSTile.SignalState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.SignalTileView;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;

/** Quick settings tile: Lte **/
public class LteServiceTile extends QSTileImpl<SignalState> {
    private int NT_MODE_LTE_GSM_WCDMA = RILConstants.NETWORK_MODE_LTE_GSM_WCDMA;
    private int NT_MODE_WCDMA_PREF = RILConstants.NETWORK_MODE_WCDMA_PREF;
//    private int NT_MODE_LTE_GSM = RILConstants.NETWORK_MODE_LTE_GSM;
    private int NT_MODE_GSM_PREF = RILConstants.NETWORK_MODE_GSM_ONLY;
    private TelephonyManager mTelephonyManager;
    private TelephonyManagerEx mTelephonyManagerEx;
    private boolean mListening;
    private boolean mLteEnabled;
    private boolean mLteAvailable;
    public static final int QS_LTESERVICE = 948;
    private boolean mIsNotSupport3G;
    private int mServiceForPhone = 0;
    /* UNISOC: Add for LTE+LTE in bug 627298. @{ */
    public static final int FOURG_PHONEID_ONE = 0;
    public static final int FOURG_PHONEID_TWO = 1;
    /* @} */

    public LteServiceTile(QSHost host) {
        super(host);
        mTelephonyManager = TelephonyManager.from(mContext);
        mTelephonyManagerEx = TelephonyManagerEx.from(mContext);
        mIsNotSupport3G = false;//mContext.getResources().getBoolean(R.bool.config_dut_not_support_3G);
    }

    public LteServiceTile(QSHost host, int phoneId) {
        this(host);
        mServiceForPhone = phoneId;
    }

    @Override
    public SignalState newTileState() {
        return new SignalState();
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) {
            return;
        }
        mListening = listening;
        if (listening) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            mContext.registerReceiver(mReceiver, filter);
        } else {
            mContext.unregisterReceiver(mReceiver);
        }
    }

    @Override
    public QSIconView createTileView(Context context) {
        return new SignalTileView(context);
    }

    /* UNISOC: modify by BUG 607871 & 727530 @{ */
    @Override
    protected void handleLongClick() {
        // handleClick();
    }
    /* @} */

    @Override
    public Intent getLongClickIntent() {
        return new Intent();
    }

    @Override
    protected void handleClick() {
        MetricsLogger.action(mContext, getMetricsCategory(), !mState.value);
        /* UNISOC: Add for Bug 947650 @{ */
        if (!isNetworkOptionAllowed() && mServiceForPhone == 1) {
            Log.d(TAG, "handleClick---is not Allowed Network Option");
            return;
        }
        /* @} */
        if (isAirplaneModeOn()) {
            mHost.collapsePanels();
            Toast.makeText(mContext, R.string.lte_service_error_airplane, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        /* UNISOC: During the call,user can't switch 4G service for bug686919. @{ */
        if (isInCall()) {
            mHost.collapsePanels();
            Toast.makeText(mContext, R.string.lte_service_error_incall, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        /* @} */
        if (!mLteAvailable) {
            return;
        } else {
            setLteEnabled();
        }
    }

    private void setLteEnabled() {
        Log.d(TAG, "setLteEnabled: " + !mState.value);
        mLteEnabled = !mState.value;
        /* UNISOC: Add for LTE+LTE in bug 627298. @{ */
        int defaultDataSubId;
        if (isSupportDualLTE()) {
            defaultDataSubId = SubscriptionManager.getSubId(mServiceForPhone)[0];
        } else {
            defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        }
        /* @} */
        boolean result = false;
        /* UNISOC: reset to the previous state if set up network fail,modify for bug 599770 @{ */
        int previousType = android.provider.Settings.Global.getInt(mContext.getContentResolver(),
                android.provider.Settings.Global.PREFERRED_NETWORK_MODE + defaultDataSubId,
                RILConstants.PREFERRED_NETWORK_MODE);
        if (mIsNotSupport3G) {
//            android.provider.Settings.Global.putInt(mContext.getContentResolver(),
//                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE + defaultDataSubId,
//                    mLteEnabled ? NT_MODE_LTE_GSM : NT_MODE_GSM_PREF);
//            result = mTelephonyManager.setPreferredNetworkType(defaultDataSubId,
//                    mLteEnabled ? NT_MODE_LTE_GSM : NT_MODE_GSM_PREF);
//            if (!result) {
//                android.provider.Settings.Global.putInt(mContext.getContentResolver(),
//                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE + defaultDataSubId,
//                        previousType);
//            }
        } else {
            android.provider.Settings.Global.putInt(mContext.getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE + defaultDataSubId,
                    mLteEnabled ? NT_MODE_LTE_GSM_WCDMA : NT_MODE_WCDMA_PREF);
            result = mTelephonyManager.setPreferredNetworkType(defaultDataSubId,
                    mLteEnabled ? NT_MODE_LTE_GSM_WCDMA : NT_MODE_WCDMA_PREF);
            if (!result) {
                android.provider.Settings.Global.putInt(mContext.getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE + defaultDataSubId,
                        previousType);
            }
        }
        /* @} */
        handleRefreshState(!mState.value);
    }

    private boolean getLteEnabled() {
        /* UNISOC: Add for LTE+LTE in bug 627298. @{ */
        int defaultDataSubId;
        if (isSupportDualLTE()) {
            defaultDataSubId = SubscriptionManager.getSubId(mServiceForPhone)[0];
        } else {
            defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        }
        /* @} */
        int settingsNetworkMode = android.provider.Settings.Global.getInt(
                mContext.getContentResolver(),
                android.provider.Settings.Global.PREFERRED_NETWORK_MODE + defaultDataSubId,
                RILConstants.PREFERRED_NETWORK_MODE);
        boolean lteEnable = (settingsNetworkMode == RILConstants.NETWORK_MODE_LTE_GSM_WCDMA
                || settingsNetworkMode == RILConstants.NETWORK_MODE_LTE_ONLY
                /*|| settingsNetworkMode == RILConstants.NETWORK_MODE_LTE_GSM*/);
        return lteEnable;
    }

    @Override
    public CharSequence getTileLabel() {
        /* UNISOC: Add for LTE+LTE in bug 627298. @{ */
        if (isSupportDualLTE()) {
            if (mServiceForPhone == 0) {
              return mContext.getString(R.string.quick_settings_lte1_service_label);
            } else if (mServiceForPhone == 1) {
                return mContext.getString(R.string.quick_settings_lte2_service_label);
            }
        }
        return mContext.getString(R.string.quick_settings_lte_service_label);
        /* @} */
    }

    public Icon getDualLTETileIcon(boolean on) {
            if (on) {
                if (mServiceForPhone == 0) {
                    return ResourceIcon.get(R.drawable.ic_qs_4g1_ex);
                } else if (mServiceForPhone == 1) {
                    return ResourceIcon.get(R.drawable.ic_qs_4g2_ex);
                }
            } else {
                if (mServiceForPhone == 0) {
                    return ResourceIcon.get(R.drawable.ic_qs_4g1_ex);
                } else if (mServiceForPhone == 1) {
                    return ResourceIcon.get(R.drawable.ic_qs_4g2_ex);
                }
            }
            return null;
    }

    /* UNISOC: Add for Bug 947650 @{ */
    private boolean isNetworkOptionAllowed() {
        return Settings.Global.getInt(mContext.getContentResolver(), SettingsEx.GlobalEx.RESTRICT_NETWORK_TYPE + 1, 0) == 0;
    }
    /* @} */

    @Override
    protected void handleUpdateState(SignalState state, Object arg) {
        if (!isAirplaneModeOn()) {
            updateLteEnabledState();
        }
        state.value = mLteEnabled;
        // state.visible = true;
        /* UNISOC: Add for LTE+LTE in bug 627298. @{ */
        int defaultDataSubId;
        int phoneId;
        int slotId;
        boolean isPrimaryCardReady;
        if (isSupportDualLTE()) {
            state.label = getTileLabel();
            defaultDataSubId = SubscriptionManager.getSubId(mServiceForPhone)[0];
            isPrimaryCardReady = mTelephonyManager.getSimState(mServiceForPhone) ==
                    TelephonyManager.SIM_STATE_READY;
        } else {
            state.label = mContext.getString(R.string.quick_settings_lte_service_label);
            defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
            phoneId = SubscriptionManager.getPhoneId(defaultDataSubId);
            slotId = SubscriptionManager.getSlotIndex(defaultDataSubId);
            isPrimaryCardReady = mTelephonyManager.getSimState(slotId) ==
                    TelephonyManager.SIM_STATE_READY;
        }
        /* @} */
        // UNISOC: Modify for bug 904740
        if (isAirplaneModeOn() || isInCall()
                || SubscriptionManager.from(mContext).getActiveSubscriptionInfoCount() < 1) {
            if (isSupportDualLTE()) {
              state.icon = getDualLTETileIcon(false);
          } else {
              state.icon = ResourceIcon.get(R.drawable.ic_qs_4g_ex);
          }
            state.state = Tile.STATE_UNAVAILABLE;
            return;
        }
        mLteAvailable = isPrimaryCardReady;

        Log.d(TAG, "handleUpdateState: mLteEnabled = " + mLteEnabled +
                " isDefaultDataCardReady = " + isPrimaryCardReady);

        if (mLteEnabled && mLteAvailable
                && !isAirplaneModeOn()) {
            // UNISOC: Replace the LTE switch icon.
            /* UNISOC: Add for LTE+LTE in bug 627298. @{ */
            if (isSupportDualLTE()) {
                state.icon = getDualLTETileIcon(true);
            } else {
                state.icon = ResourceIcon.get(R.drawable.ic_qs_4g_ex);
            }
            /* @} */
            if (!isNetworkOptionAllowed() && mServiceForPhone == 1) {
                state.state = Tile.STATE_INACTIVE;
            } else {
                state.state = Tile.STATE_ACTIVE;
            }
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_lte_on);
        } else {
            // UNISOC: Replace the LTE switch icon.
            /* UNISOC: Add for LTE+LTE in bug 627298. @{ */
            if (isSupportDualLTE()) {
                state.icon = getDualLTETileIcon(false);
            } else {
                state.icon = ResourceIcon.get(R.drawable.ic_qs_4g_ex);
            }
            /* @} */
            state.state = Tile.STATE_INACTIVE;
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_lte_off);
        }
        state.expandedAccessibilityClassName = Switch.class.getName();
    }

    public void updateLteEnabledState() {
        /* UNISOC: Add for LTE+LTE in bug 627298. @{ */
        int defaultDataSubId;
        if (isSupportDualLTE()) {
            defaultDataSubId = SubscriptionManager.getSubId(mServiceForPhone)[0];
        } else {
            defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        }
        /* @} */
        boolean lteEnabled = false;

        if (SubscriptionManager.isValidSubscriptionId(defaultDataSubId)) {
            /* UNISOC: query LTE status from AP instead of from BP with AT commands,
             * as the latter method may cause ANR for multiple blocked thread.*/;
            lteEnabled = getLteEnabled();

            if (mLteEnabled != lteEnabled) {
                mState.value = mLteEnabled = lteEnabled;
                refreshState();
            }
            /* @} */
        }
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_lte_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_lte_changed_off);
        }
    }

    @Override
    public int getMetricsCategory() {
        return QS_LTESERVICE;
    }

    @Override
    public boolean isAvailable() {
        final boolean showLTETile = mContext.getResources().getBoolean(
                R.bool.config_show_lte_tile);
        return showLTETile;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                refreshState();
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())) {
                refreshState();
            }
        }
    };

    public boolean isAirplaneModeOn() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    /* UNISOC: During the call,user can't switch 4G service for bug686919. @{ */
    private boolean isInCall() {
        return getTelecommManager().isInCall();
    }

    private TelecomManager getTelecommManager() {
        return (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }
    /* @} */

    /* UNISOC: Add for LTE+LTE in bug 627298. @{ */
    private boolean isSupportDualLTE() {
        return ImsManagerEx.isDualLteModem();
    }
    /* @} */
}