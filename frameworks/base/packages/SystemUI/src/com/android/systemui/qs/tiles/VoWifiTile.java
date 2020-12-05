package com.android.systemui.qs.tiles;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.provider.SettingsEx;
import android.service.quicksettings.Tile;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManagerGlobal;
import android.widget.CheckBox;
import android.widget.Switch;
import android.widget.Toast;

import com.android.ims.ImsManager;
import com.android.ims.internal.ImsManagerEx;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.qs.DetailAdapter;
import com.android.systemui.plugins.qs.QSIconView;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.qs.QSTile.SignalState;
import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.SignalTileView;
import com.android.systemui.qs.SubscriptionSetting;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;

/** Quick settings tile: Vowifi **/
public class VoWifiTile extends QSTileImpl<BooleanState> {
    static final String SETTINGS_PACKAGE_NAME = "com.android.settings";
    static final String WIFI_CALLING_SETTING_ACTIVITY_NAME =
            "com.android.settings.Settings$WifiCallingSettingsActivity";
    static final Intent WIFICALLING_SETTINGS = new Intent().setComponent(new ComponentName(
            SETTINGS_PACKAGE_NAME, WIFI_CALLING_SETTING_ACTIVITY_NAME));
    static final boolean DBG = true;
    static final String TAG = "QSVoWifiTile";
    private final SubscriptionSetting mRadioSetting;
    private TelephonyManager mTelephonyManager;
    private boolean mListening;
    private boolean mVoWifiEnabled;
    private boolean mVoWifiAvailable;
    public static final int QS_VOWIFI_SERVICE = 951;
    /*UNISOC: add for bug691804 carrier special new feature*/
    private AlertDialog mVowifiEnableDialog = null;
    private static final String ENHANCED_VOWIFI_TOAST_SHOW_ENABLED =
            SettingsEx.GlobalEx.ENHANCED_VOWIFI_TOAST_SHOW_ENABLED;
    /* @} */

    public VoWifiTile(QSHost host) {
        super(host);
        mTelephonyManager = TelephonyManager.from(mContext);
        Log.d(TAG, "create VoWifiTile");
        mRadioSetting = new SubscriptionSetting(mContext, mHandler, Global.WFC_IMS_ENABLED) {
            @Override
            protected void handleValueChanged(boolean value) {
                if (DBG) {
                    Log.d(TAG, "handleValueChanged: value = " + value);
                }
                handleRefreshState(value);
            }
        };

    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) {
            return;
        }
        mListening = listening;
        //UNISOC: add for bug691804 carrier special new feature
        mRadioSetting.setListening(listening);
        if (listening) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            intentFilter.addAction(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
            mContext.registerReceiver(mBroadcastReceiver, intentFilter);
        } else {
            mContext.unregisterReceiver(mBroadcastReceiver);
        }
    }

    @Override
    public Intent getLongClickIntent() {
        return WIFICALLING_SETTINGS;
    }

    @Override
    protected void handleClick() {
        MetricsLogger.action(mContext, getMetricsCategory(), !mState.value);

        /* UNISOC: During the call,user can't switch vowifi service for bug743325. @{ */
        if (isInCall()) {
            mHost.collapsePanels();
            Toast.makeText(mContext, R.string.vowifi_service_error_incall, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        /* @} */
        if (!mVoWifiAvailable) {
            return;
        } else {
            /*UNISOC: add for bug691804 carrier special new feature @{ */
            boolean showVowifiEnableDiaglog = mContext.getResources()
                    .getBoolean(R.bool.config_show_vowifi_enable_dialog);
            if (showVowifiEnableDiaglog) {
                setVoWifiEnabledForCarrier();
            /* @} */
            } else {
                setVolteEnableWithNotice();
                setVoWifiEnabled();
            }
        }
    }

    /*UNISOC: add for bug691804 carrier special new feature*/
    public AlertDialog showVowifiEnableDialog(final Context context) {

        int enabled = Settings.Global.getInt(context.getContentResolver(),
                ENHANCED_VOWIFI_TOAST_SHOW_ENABLED, 0);
        boolean nomore = (enabled == 1);
        if (nomore) {
            Log.d(TAG, "showVoWifiNotification nomore ");
            return null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.xml.vowifi_register_dialog, null);

        builder.setView(view);
        builder.setTitle(context.getString(R.string.vowifi_connected_title));
        builder.setMessage(context.getString(R.string.vowifi_connected_message));
        final CheckBox cb = (CheckBox) view.findViewById(R.id.nomore);

        builder.setPositiveButton(context.getString(R.string.vowifi_connected_continue),
                new android.content.DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (cb.isChecked()) {
                    Settings.Global.putInt(context.getContentResolver(),
                            ENHANCED_VOWIFI_TOAST_SHOW_ENABLED, 1);
                }
                Log.d(TAG, "Vowifi service Continue, cb.isChecked = " + cb.isChecked());
                setVolteEnableWithNotice();
                ImsManager.setWfcSetting(context, true);
                if (dialog != null) {
                    dialog.dismiss();
                    dialog = null;
                }
            }
        });
        builder.setNegativeButton(context.getString(R.string.vowifi_connected_disable),
                new android.content.DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (cb.isChecked()) {
                    Settings.Global.putInt(context.getContentResolver(),
                            ENHANCED_VOWIFI_TOAST_SHOW_ENABLED, 1);
                }
                Log.d(TAG, "Vowifi service disable, cb.isChecked = " + cb.isChecked());
                ImsManager.setWfcSetting(context, false);
                if (dialog != null) {
                    dialog.dismiss();
                    dialog = null;
                }
            }
        });
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        dialog.show();
        return  dialog;
    }
    /* @} */

    private void setVoWifiEnabled() {
        if (DBG) {
            Log.d(TAG, "setVoWifiEnabled: " + !mState.value);
        }
        mVoWifiEnabled = !mState.value;
        ImsManager.setWfcSetting(mContext, mVoWifiEnabled);
    }

    /*UNISOC: add for bug691804 carrier special new feature*/
    private void setVoWifiEnabledForCarrier() {
        if (DBG) {
          Log.d(TAG, "setVoWifiEnabledForCarrier: " + !mState.value);
        }
        mVoWifiEnabled = !mState.value;
        if (mVoWifiEnabled) {
            if (mVowifiEnableDialog != null) {
                mHost.collapsePanels();
                dissmissDefaultKeyguard();
                return;
            }
            mVowifiEnableDialog = showVowifiEnableDialog(mContext);
            if (mVowifiEnableDialog != null) {
                mVowifiEnableDialog.setOnDismissListener(new OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        // TODO Auto-generated method stub
                        mVowifiEnableDialog = null;
                    }
                });
            }

            if (mVowifiEnableDialog == null) {
                setVolteEnableWithNotice();
                setVoWifiEnabled();
            } else {
                StatusBarManager statusBarManager = (StatusBarManager) mContext
                        .getSystemService(android.app.Service.STATUS_BAR_SERVICE);
                statusBarManager.collapsePanels();
                dissmissDefaultKeyguard();
            }
        } else {
            setVoWifiEnabled();
        }
    }

    private void dissmissDefaultKeyguard() {
        KeyguardManager keyguardManager =
                (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        Log.d(TAG, "keyguardManager.isKeyguardLocked() = " + keyguardManager.isKeyguardLocked());
        if (keyguardManager.isKeyguardLocked()) {
            try {
                WindowManagerGlobal.getWindowManagerService().dismissKeyguard(null ,null);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    /* @} */
    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_vowifi_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        updateVoWifiEnabledState();

        state.value = mVoWifiEnabled;

        state.label = removeDoubleQuotes(getSSID());

        mVoWifiAvailable = ImsManager.isWfcEnabledByPlatform(mContext);

        if (DBG) {
            Log.d(TAG, "handleUpdateState: mVoWifiEnabled = "
                    + mVoWifiEnabled + " mVoWifiAvailable = " + mVoWifiAvailable);
        }
        int phoneId = SubscriptionManager.getPhoneId(SubscriptionManager.getDefaultDataSubscriptionId());
        if (SubscriptionManager.isValidPhoneId(phoneId) && mVoWifiAvailable) {
            if (mVoWifiEnabled) {
                state.state = Tile.STATE_ACTIVE;
                state.icon = ResourceIcon.get(R.drawable.ic_qs_vowifi_on_ex);
                state.label = removeDoubleQuotes(getSSID());
                state.contentDescription =  mContext.getString(
                        R.string.accessibility_quick_settings_vowifi_on);
            } else {
                state.state = Tile.STATE_INACTIVE;
                state.icon = ResourceIcon.get(R.drawable.ic_qs_vowifi_on_ex);
                state.label = mContext.getString(R.string.quick_settings_vowifi_label);
                state.contentDescription =  mContext.getString(
                        R.string.accessibility_quick_settings_vowifi_off);
            }
        } else {
            Log.d(TAG, "handleUpdateState: Tile.STATE_UNAVAILABLE");
            state.state = Tile.STATE_UNAVAILABLE;
            state.icon = ResourceIcon.get(R.drawable.ic_qs_vowifi_on_ex);
            state.label = mContext.getString(R.string.quick_settings_vowifi_label);
            return;
        }
        state.expandedAccessibilityClassName = Switch.class.getName();
    }

    public void updateVoWifiEnabledState() {
        boolean vowifiEnabled = false;

        vowifiEnabled = ImsManager.isWfcEnabledByUser(mContext);
        if (DBG) {
            Log.d(TAG, "updateVoWifiEnabledState: vowifiEnabled = "
                    + vowifiEnabled + ", mVoWifiEnabled = " + mVoWifiEnabled);
        }

        if (mVoWifiEnabled != vowifiEnabled) {
            mState.value = mVoWifiEnabled = vowifiEnabled;
            handleRefreshState(mState.value);
        }
    }

    private String getSSID() {
        WifiManager wifi = (WifiManager) mContext.getSystemService(
                Context.WIFI_SERVICE);
        if (wifi != null) {
            WifiInfo info = wifi.getConnectionInfo();
            if (info != null) {
                return (info.getSSID() != WifiSsid.NONE) ?
                        info.getSSID() : mContext.getString(R.string.quick_settings_vowifi_label);
            }
        }
        return mContext.getString(R.string.quick_settings_vowifi_label);
    }

    private static String removeDoubleQuotes(String string) {
        if (string == null) {
            return null;
        }
        final int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"') && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    @Override
    public int getMetricsCategory() {
        return QS_VOWIFI_SERVICE;
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                refreshState();
            } else if (action.equals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)){
                refreshState();
            }
        }
    };

    /* UNISOC: During the call,user can't switch vowifi for bug743325. @{ */
    private boolean isInCall() {
        return getTelecommManager().isInCall();
    }

    private TelecomManager getTelecommManager() {
        return (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }
    /* @} */

    private static Toast getToast(Context context, int text, int duration) {
      Toast mToast = Toast.makeText(context, text, duration);
      mToast.getWindowParams().type = WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL;
      mToast.getWindowParams().flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
      return mToast;
    }

    private void setVolteEnableWithNotice() {
        /* UNISOC: when user open vowifi,then volte will be open synchronously for bug770787. @{ */
        /* UNISOC: bug 811336 && 977043@{ */
        if(!ImsManagerEx.separateSettingForWFCandVoLTE(mContext)) {
            return;
        }
        int defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        int phoneId = SubscriptionManager.getPhoneId(defaultDataSubId);
        ImsManager imsManager = ImsManager.getInstance(mContext, phoneId);
        boolean isVolteEnabled = imsManager.isEnhanced4gLteModeSettingEnabledByUser();
        Log.d(TAG, "setVolteEnableWithNotice() phoneId = " + phoneId + " isVolteEnabled = "
                + isVolteEnabled);
        if (ImsManager.isVolteEnabledByPlatform(mContext) && !mState.value
                && !isVolteEnabled) {
            imsManager.setEnhanced4gLteModeSetting(true);
            /* @} */
            getToast(mContext, R.string.vowifi_service_volte_open_synchronously, Toast.LENGTH_LONG)
                    .show();
        }
        /* @} */
    }
}
