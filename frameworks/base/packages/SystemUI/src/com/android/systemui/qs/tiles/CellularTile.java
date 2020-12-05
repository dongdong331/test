/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import static android.telephony.TelephonyManager.SIM_STATE_READY;
import static com.android.systemui.Prefs.Key.QS_HAS_TURNED_OFF_MOBILE_DATA;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.service.quicksettings.Tile;
import android.telephony.SubscriptionManager;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.widget.Switch;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settingslib.net.DataUsageController;
import com.android.systemui.Dependency;
import com.android.systemui.Prefs;
import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.qs.DetailAdapter;
import com.android.systemui.plugins.qs.QSIconView;
import com.android.systemui.plugins.qs.QSTile.SignalState;
import com.android.systemui.qs.CellTileView;
import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;

import android.os.sprdpower.PowerManagerEx;
import android.os.sprdpower.IPowerManagerEx;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.systemui.statusbar.phone.StatusBar;


/** Quick settings tile: Cellular **/
public class CellularTile extends QSTileImpl<SignalState> {
    private static final String ENABLE_SETTINGS_DATA_PLAN = "enable.settings.data.plan";
    // UNISOC: bug 916022
    private static final String TAG = "CellularTile";

    private final NetworkController mController;
    private final DataUsageController mDataController;
    private final CellularDetailAdapter mDetailAdapter;
    /* UNISOC: bug 916022 @{ */
    private GlobalSetting mDataSetting;
    private int mLastDefaultDataSubId;
    private boolean mHasEvent = false;
    /* @} */

    private final CellSignalCallback mSignalCallback = new CellSignalCallback();
    private final ActivityStarter mActivityStarter;
    private final KeyguardMonitor mKeyguardMonitor;
    private IPowerManagerEx mPowerManagerEx;
    // UNISOC: bug 900578
    private AlertDialog mAlertDialog;

    public CellularTile(QSHost host) {
        super(host);
        mController = Dependency.get(NetworkController.class);
        mActivityStarter = Dependency.get(ActivityStarter.class);
        mKeyguardMonitor = Dependency.get(KeyguardMonitor.class);
        mDataController = mController.getMobileDataController();
        mDetailAdapter = new CellularDetailAdapter();
    }

    @Override
    public SignalState newTileState() {
        return new SignalState();
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (listening) {
            // UNISOC: bug 916022
            mHasEvent = false;
            mController.addCallback(mSignalCallback);
        } else {
            mController.removeCallback(mSignalCallback);
        }
        /* UNISOC: bug 916022 @{ */
        final int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        String nameString = Global.MOBILE_DATA;
        if (TelephonyManager.getDefault().getSimCount() != 1) {
            nameString = Global.MOBILE_DATA + subId;
        }
        if (mLastDefaultDataSubId != subId) {
            mLastDefaultDataSubId = subId;
            mDataSetting = null;
            Log.d(TAG, "default data subid has been change");
        }
        Log.d(TAG, "setListening subid : " + subId + "; mDataSetting : " + mDataSetting
                + "; listening :" + listening + " nameString = " + nameString);
        if (mDataSetting == null && SubscriptionManager.isValidSubscriptionId(subId)) {
            mDataSetting = new GlobalSetting(mContext, mHandler, nameString) {
                @Override
                protected void handleValueChanged(int value) {
                    Log.d(TAG, "mDataSetting handleValueChanged value : " + value
                            + "; subid : " + subId + " mHasEvent : " + mHasEvent);
                    mState.value = mDataController.isMobileDataEnabled();
                    handleRefreshState(null);
                    mDetailAdapter.setMobileDataEnabled(mState.value);
                    mHasEvent = false;
                }
            };
        }
        if (mDataSetting != null) {
            mDataSetting.setListening(listening);
        }
        /* @} */
    }

    @Override
    public QSIconView createTileView(Context context) {
        return new CellTileView(context);
    }

    @Override
    public Intent getLongClickIntent() {
        if (mContext.getResources().getBoolean(R.bool.config_show_data_usage_summary)) {
            return getCellularSettingIntent();
        } else {
            return new Intent();
        }
    }

    @Override
    protected void handleLongClick() {
        if (StatusBar.SUPPORT_SUPER_POWER_SAVE) {
            mPowerManagerEx = IPowerManagerEx.Stub.asInterface(ServiceManager.getService("power_ex"));
            int powerMode = getPowerSaveModeInternal();
            if (powerMode ==PowerManagerEx.MODE_ULTRASAVING) {
                return;
            }
        }
        super.handleLongClick();
    }

    @Override
    protected void handleClick() {
        /* UNISOC: bug 916022 @{ */
        Log.d(TAG, "handleClick mHasEvent : " + mHasEvent);
        if (mHasEvent) {
            return;
        }
        if (getState().state == Tile.STATE_UNAVAILABLE) {
            return;
        }
        mHasEvent = true;
        /* @} */
        int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        int phoneId = SubscriptionManager.getPhoneId(subId);
        TelephonyManager teleManager = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (SubscriptionManager.isValidPhoneId(phoneId) && (teleManager.getSimState(phoneId) == SIM_STATE_READY) && mDataController.isMobileDataEnabled()) {
            if (mKeyguardMonitor.isSecure() && !mKeyguardMonitor.canSkipBouncer()) {
                mActivityStarter.postQSRunnableDismissingKeyguard(this::maybeShowDisableDialog);
            } else {
                mUiHandler.post(this::maybeShowDisableDialog);
            }
        } else {
            mDataController.setMobileDataEnabled(true);
        }
    }

    public int getPowerSaveModeInternal(){
        try {
            int mode = mPowerManagerEx.getPowerSaveMode();
            return mode;
        } catch (RemoteException e) {
            // TODO: handle exception
            e.printStackTrace();
        } catch (NullPointerException e) {
        }
        return -1;
    }

    private void maybeShowDisableDialog() {
        if (Prefs.getBoolean(mContext, QS_HAS_TURNED_OFF_MOBILE_DATA, false)) {
            // Directly turn off mobile data if the user has seen the dialog before.
            mDataController.setMobileDataEnabled(false);
            return;
        }
        /* UNISOC: bug 900578 @{ */
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            return;
        }
        String carrierName = mController.getMobileDataNetworkName();
        /* UNISOC: bug 930353 @{ */
        int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        TelephonyManager teleManager = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);
        ServiceState ss = teleManager.getServiceStateForSubscriber(subId);
        if (TextUtils.isEmpty(carrierName) || !hasService(ss)) {
            carrierName = mContext.getString(R.string.mobile_data_disable_message_default_carrier);
        }
        mAlertDialog = new Builder(mContext)
                .setTitle(R.string.mobile_data_disable_title)
                .setMessage(mContext.getString(R.string.mobile_data_disable_message, carrierName))
                .setNegativeButton(
                        android.R.string.cancel,
                        //UNISOC: bug 930173
                        (d, w) -> {
                            mHasEvent = false;
                        })
                .setPositiveButton(
                        com.android.internal.R.string.alert_windows_notification_turn_off_action,
                        (d, w) -> {
                            mDataController.setMobileDataEnabled(false);
                            Prefs.putBoolean(mContext, QS_HAS_TURNED_OFF_MOBILE_DATA, true);
                        })
                .create();
        mAlertDialog.getWindow().setType(LayoutParams.TYPE_KEYGUARD_DIALOG);
        SystemUIDialog.setShowForAllUsers(mAlertDialog, true);
        SystemUIDialog.registerDismissListener(mAlertDialog);
        SystemUIDialog.setWindowOnTop(mAlertDialog);
        mAlertDialog.show();
        mAlertDialog.setCanceledOnTouchOutside(false);
        /* @} */
    }

    /* UNISOC: bug 930353 @{ */
    private boolean hasService(ServiceState ss) {
        if (ss != null) {
            switch (ss.getVoiceRegState()) {
                case ServiceState.STATE_POWER_OFF:
                    return false;
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    return ss.getDataRegState() == ServiceState.STATE_IN_SERVICE;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }
    /* @} */

    @Override
    protected void handleSecondaryClick() {
        if (mDataController.isMobileDataSupported()) {
            showDetail(true);
        } else {
            mActivityStarter
                    .postStartActivityDismissingKeyguard(getCellularSettingIntent(),0 /* delay */);
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_cellular_detail_title);
    }

    @Override
    protected void handleUpdateState(SignalState state, Object arg) {
        CallbackInfo cb = (CallbackInfo) arg;
        if (cb == null) {
            cb = mSignalCallback.mInfo;
        }

        final Resources r = mContext.getResources();
        state.activityIn = cb.enabled && cb.activityIn;
        state.activityOut = cb.enabled && cb.activityOut;
        state.label = r.getString(R.string.mobile_data);
        boolean mobileDataEnabled = mDataController.isMobileDataSupported()
                && mDataController.isMobileDataEnabled();
        // UNISOC: bug 916022
        Log.d(TAG, "handleUpdateState() mobileDataEnabled = " + mobileDataEnabled);
        state.value = mobileDataEnabled;
        state.expandedAccessibilityClassName = Switch.class.getName();
        if (cb.noSim) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_no_sim);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_swap_vert);
        }

        if (cb.noSim) {
            state.state = Tile.STATE_UNAVAILABLE;
            state.secondaryLabel = r.getString(R.string.keyguard_missing_sim_message_short);
        } else if (cb.airplaneModeEnabled) {
            state.state = Tile.STATE_UNAVAILABLE;
            state.secondaryLabel = r.getString(R.string.status_bar_airplane);
        } else if (mobileDataEnabled) {
            state.state = Tile.STATE_ACTIVE;
            state.secondaryLabel = getMobileDataDescription(cb);
        } else {
            state.state = Tile.STATE_INACTIVE;
            state.secondaryLabel = r.getString(R.string.cell_data_off);
        }


        // TODO(b/77881974): Instead of switching out the description via a string check for
        // we need to have two strings provided by the MobileIconGroup.
        final CharSequence contentDescriptionSuffix;
        if (state.state == Tile.STATE_INACTIVE) {
            contentDescriptionSuffix = r.getString(R.string.cell_data_off_content_description);
        } else {
            contentDescriptionSuffix = state.secondaryLabel;
        }

        state.contentDescription = state.label + ", " + contentDescriptionSuffix;
    }

    private CharSequence getMobileDataDescription(CallbackInfo cb) {
        if (cb.roaming && !TextUtils.isEmpty(cb.dataContentDescription)) {
            String roaming = mContext.getString(R.string.data_connection_roaming);
            String dataDescription = cb.dataContentDescription;
            return mContext.getString(R.string.mobile_data_text_format, roaming, dataDescription);
        }
        if (cb.roaming) {
            return mContext.getString(R.string.data_connection_roaming);
        }
        return cb.dataContentDescription;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_CELLULAR;
    }

    @Override
    public boolean isAvailable() {
        return mController.hasMobileDataFeature();
    }

    private static final class CallbackInfo {
        boolean enabled;
        boolean airplaneModeEnabled;
        String dataContentDescription;
        boolean activityIn;
        boolean activityOut;
        boolean noSim;
        boolean roaming;
    }

    private final class CellSignalCallback implements SignalCallback {
        private final CallbackInfo mInfo = new CallbackInfo();

        @Override
        public void setMobileDataIndicators(IconState statusIcon, IconState qsIcon, int statusType,
                int qsType, boolean activityIn, boolean activityOut, String typeContentDescription,
                String description, boolean isWide, int subId, boolean roaming) {
            if (qsIcon == null) {
                // Not data sim, don't display.
                return;
            }
            mInfo.enabled = qsIcon.visible;
            mInfo.dataContentDescription = typeContentDescription;
            mInfo.activityIn = activityIn;
            mInfo.activityOut = activityOut;
            mInfo.roaming = roaming;
            refreshState(mInfo);
        }

        @Override
        public void setNoSims(boolean show, boolean simDetected) {
            mInfo.noSim = show;
            refreshState(mInfo);
        }

        @Override
        public void setIsAirplaneMode(IconState icon) {
            mInfo.airplaneModeEnabled = icon.visible;
            refreshState(mInfo);
        }

        @Override
        public void setMobileDataEnabled(boolean enabled) {
            mDetailAdapter.setMobileDataEnabled(enabled);
        }
    }

    static Intent getCellularSettingIntent() {
            return new Intent(Settings.ACTION_DATA_USAGE_SETTINGS);
    }

    private final class CellularDetailAdapter implements DetailAdapter {

        @Override
        public CharSequence getTitle() {
            return mContext.getString(R.string.quick_settings_cellular_detail_title);
        }

        @Override
        public Boolean getToggleState() {
            return mDataController.isMobileDataSupported()
                    ? mDataController.isMobileDataEnabled()
                    : null;
        }

        @Override
        public Intent getSettingsIntent() {
            return getCellularSettingIntent();
        }

        @Override
        public void setToggleState(boolean state) {
            MetricsLogger.action(mContext, MetricsEvent.QS_CELLULAR_TOGGLE, state);
            mDataController.setMobileDataEnabled(state);
        }

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.QS_DATAUSAGEDETAIL;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            final DataUsageDetailView v = (DataUsageDetailView) (convertView != null
                    ? convertView
                    : LayoutInflater.from(mContext).inflate(R.layout.data_usage, parent, false));
            final DataUsageController.DataUsageInfo info = mDataController.getDataUsageInfo();
            if (info == null) return v;
            v.bind(info);
            v.findViewById(R.id.roaming_text).setVisibility(mSignalCallback.mInfo.roaming
                    ? View.VISIBLE : View.INVISIBLE);
            return v;
        }

        public void setMobileDataEnabled(boolean enabled) {
            fireToggleStateChanged(enabled);
        }
    }
}
