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

package com.android.settings.sim;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.provider.SettingsEx;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceViewHolder;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManagerEx;
import android.text.TextUtils;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;
import android.view.View;

import com.android.ims.internal.ImsManagerEx;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.DualVolteController;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TeleUtils;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.settings.R;
import com.android.settings.RestrictedSettingsFragment;
import com.android.settings.Utils;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.MasterSwitchPreference;
import com.android.settings.search.Indexable;
import com.android.settingslib.WirelessUtils;
import com.android.sprd.telephony.RadioInteractor;
import com.android.sprd.telephony.RadioInteractorCallbackListener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SimSettings extends RestrictedSettingsFragment implements Indexable {
    private static final String TAG = "SimSettings";
    private static final boolean DBG = false;

    private static final String DISALLOW_CONFIG_SIM = "no_config_sim";
    private static final String SIM_CARD_CATEGORY = "sim_cards";
    private static final String KEY_CELLULAR_DATA = "sim_cellular_data";
    private static final String KEY_CALLS = "sim_calls";
    private static final String KEY_SMS = "sim_sms";
    private static final String KEY_ACTIVITIES = "sim_activities";
    public static final String EXTRA_SLOT_ID = "slot_id";
    private static final String KEY_DATA_DURING_CALL = "data_enabled_during_calls";

    /**
     * By UX design we use only one Subscription Information(SubInfo) record per SIM slot.
     * mAvalableSubInfos is the list of SubInfos we present to the user.
     * mSubInfoList is the list of all SubInfos.
     * mSelectableSubInfos is the list of SubInfos that a user can select for data, calls, and SMS.
     */
    private List<SubscriptionInfo> mAvailableSubInfos = null;
    private List<SubscriptionInfo> mSubInfoList = null;
    private List<SubscriptionInfo> mSelectableSubInfos = null;
    private PreferenceScreen mSimCards = null;
    private static Toast mToast = null ;
    private SubscriptionManager mSubscriptionManager;
    private int mNumSlots;
    private Context mContext;
    /* UNISOC: add new feature for data switch on/off @{ */
    private DataPreference mDataPreference;
    private PreferenceCategory mSimPreferenceCatergory;
    /* @} */
    /* UNISOC: add for new featrue:Smart Dual SIM @{ */
    private static final String KEY_SMART_DUAL_SIM = "smart_dual_sim";
    private Preference mSmartDualSim = null;
    /* @} */

    private int mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
    private int[] mCallState = new int[mPhoneCount];
    private PhoneStateListener[] mPhoneStateListener = new PhoneStateListener[mPhoneCount];
    private RadioInteractor mRadioInteractor;
    private RadioInteractorCallbackListener[] mRadioInteractorCallbackListener;
    private DialogFragment mAlertDialogFragment;
    private DialogFragment mDataAlerDialogFragment;
    private SwitchPreference mSwitchPreference;
    private TelephonyManagerEx mTmEx;
    private boolean[] mIsChecked = new boolean[mPhoneCount];
    private boolean[] mSwitchHasChanged = new boolean[mPhoneCount];
    public SimSettings() {
        super(DISALLOW_CONFIG_SIM);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.SIM;
    }

    @Override
    public void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        mContext = getActivity();

        //UNISOC:add for Bug978602
        if (getActivity() != null && getActivity().isInMultiWindowMode()) {
            stopSimSettings();
        }

        mSubscriptionManager = SubscriptionManager.from(getActivity());
        final TelephonyManager tm =
                (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        addPreferencesFromResource(R.xml.sim_settings);

        mNumSlots = tm.getSimCount();
        mSimCards = (PreferenceScreen)findPreference(SIM_CARD_CATEGORY);
        mAvailableSubInfos = new ArrayList<SubscriptionInfo>(mNumSlots);
        mSelectableSubInfos = new ArrayList<SubscriptionInfo>();
        SimSelectNotification.cancelNotification(getActivity());
        /* UNISOC: add new feature for data switch on/off @{ */
        mSimPreferenceCatergory = (PreferenceCategory) findPreference(KEY_ACTIVITIES);
        mDataPreference = (DataPreference) findPreference(KEY_CELLULAR_DATA);
         /* @} */
        mTmEx = TelephonyManagerEx.from(mContext);
        mRadioInteractor = new RadioInteractor(mContext);
        mRadioInteractorCallbackListener = new RadioInteractorCallbackListener[mPhoneCount];
        /* UNISOC: add for new featrue:Smart Dual SIM @{ */
        mSmartDualSim = findPreference(KEY_SMART_DUAL_SIM);
        mSimPreferenceCatergory.removePreference(mSmartDualSim);
        refreshSmartDualSimStatus();
        /* @} */
        /* SPRD: Add Data enable during volte call for dual volte @{ */
        mSwitchPreference = (SwitchPreference) findPreference(KEY_DATA_DURING_CALL);
        if (!ImsManagerEx.isDualLteModem()) {
            mSimPreferenceCatergory.removePreference(mSwitchPreference);
        }
        /* @} */
    }

    private final SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangeListener
            = new SubscriptionManager.OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            if (DBG) log("onSubscriptionsChanged:");
            updateSubscriptions();
            updateAllOptions();
        }
    };

    private void updateSubscriptions() {
        mSubInfoList = mSubscriptionManager.getActiveSubscriptionInfoList();
        for (int i = 0; i < mNumSlots; ++i) {
            Preference pref = mSimCards.findPreference("sim" + i);
            if (pref instanceof SimPreference) {
                mSimCards.removePreference(pref);
            }
        }
        mAvailableSubInfos.clear();
        mSelectableSubInfos.clear();

        for (int i = 0; i < mNumSlots; ++i) {
            final SubscriptionInfo sir = mSubscriptionManager
                    .getActiveSubscriptionInfoForSimSlotIndex(i);
            SimPreference simPreference = new SimPreference(getPrefContext(), sir, i);
            simPreference.setOrder(i-mNumSlots);
            mSimCards.addPreference(simPreference);
            mAvailableSubInfos.add(sir);
            if (sir != null) {
                mSelectableSubInfos.add(sir);
            }
        }
        updateAllOptions();
    }

    private void updateAllOptions() {
        if (getActivity() == null) {
            return;
        }

        boolean isAirplaneModeOn = WirelessUtils.isAirplaneModeOn(mContext);
        getPreferenceScreen().setEnabled(!isAirplaneModeOn && !TeleUtils.isRadioBusy(mContext));
        if (isAirplaneModeOn || !isCallStateIdle()) {
            if (mAlertDialogFragment != null) {
                mAlertDialogFragment.dismissAllowingStateLoss();
            }
            if (mDataAlerDialogFragment != null) {
                mDataAlerDialogFragment.dismissAllowingStateLoss();
            }
        }
        updateSimSlotValues();
        updateActivitesCategory();
    }

    void updateSimSlotValues() {
        final int prefSize = mSimCards.getPreferenceCount();
        for (int i = 0; i < prefSize; ++i) {
            Preference pref = mSimCards.getPreference(i);
            if (pref instanceof SimPreference) {
                ((SimPreference)pref).update();
            }
        }
    }

    void updateActivitesCategory() {
        updateCellularDataValues();
        updateCallValues();
        updateSmsValues();
        updateDataPreferActiveDuringCall();
    }

    private void updateSmsValues() {
        final Preference simPref = findPreference(KEY_SMS);
        final SubscriptionInfo sir = mSubscriptionManager.getDefaultSmsSubscriptionInfo();
        simPref.setTitle(R.string.sms_messages_title);
        if (DBG) log("[updateSmsValues] mSubInfoList=" + mSubInfoList);

        if (sir != null) {
            simPref.setSummary(sir.getDisplayName());
            simPref.setEnabled(mSelectableSubInfos.size() > 1);
        } else if (sir == null) {
            simPref.setSummary(R.string.sim_selection_required_pref);
            simPref.setEnabled(mSelectableSubInfos.size() >= 1);
        }
    }

    private void updateCellularDataValues() {
        /* UNISOC: add new feature for data switch on/off @{ */
        //final Preference simPref = findPreference(KEY_CELLULAR_DATA);
        /* @} */
        if (isShowDataPrefer()) {
            mSimPreferenceCatergory.addPreference(mDataPreference);
        } else {
            mSimPreferenceCatergory.removePreference(mDataPreference);
        }
        /* @} */

        final SubscriptionInfo sir = mSubscriptionManager.getDefaultDataSubscriptionInfo();
        mDataPreference.setTitle(R.string.cellular_data_title);
        if (DBG) log("[updateCellularDataValues] mSubInfoList=" + mSubInfoList);

        boolean callStateIdle = isCallStateIdle();
        final boolean ecbMode = SystemProperties.getBoolean(
                TelephonyProperties.PROPERTY_INECM_MODE, false);
        if (sir != null) {
            //UNISOC:Add for Bug947653
            boolean isCustomizeDisplayName = mContext.getResources().
                    getBoolean(com.android.internal.R.bool.mobile_network_capability_downgrade);
            String displayNamePrefix = "";
            if (isCustomizeDisplayName) {
                displayNamePrefix = "SIM"+ (sir.getSimSlotIndex()+1) + ":";
            }
            mDataPreference.setSummary(displayNamePrefix + sir.getDisplayName());
            // Enable data preference in msim mode and call state idle
            mDataPreference.setEnabled((mSelectableSubInfos.size() > 1) && callStateIdle && !ecbMode);
            mDataPreference.updateDataSwitch();
        } else if (sir == null) {
            mDataPreference.setSummary(R.string.sim_selection_required_pref);
            // Enable data preference in msim mode and call state idle
            mDataPreference.setEnabled((mSelectableSubInfos.size() >= 1) && callStateIdle && !ecbMode);
        }

        // UNISOC: modify for bug841324
        mDataPreference.setShouldDisableView(
                mSelectableSubInfos.size() == 0 || WirelessUtils.isAirplaneModeOn(mContext));
    }

    public boolean isShowDataPrefer() {
        boolean isShowDataPreferConfig = mContext.getResources().getBoolean(R.bool.hide_data_preference);
        if (!isShowDataPreferConfig) {
            if (mSelectableSubInfos == null || mSelectableSubInfos.size() < 2 || customOperatorPrefer() || !mTmEx.isPrimaryCardSwitchAllowed()) {
                return false;
            }
        }
        return true;
    }

    private boolean customOperatorPrefer() {
        int customOperatorSimCount = 0;
        for (SubscriptionInfo subInfo : mSelectableSubInfos) {
            String iccid = subInfo.getIccId();
            if (iccid.matches("^89860[0247].*")) {
                customOperatorSimCount++;
            }
        }
        if (mSelectableSubInfos != null && mSelectableSubInfos.size() == mPhoneCount
                && customOperatorSimCount == 1) {
            return true;
        }
        return false;
    }

    private void updateCallValues() {
        final Preference simPref = findPreference(KEY_CALLS);
        final TelecomManager telecomManager = TelecomManager.from(mContext);
        final PhoneAccountHandle phoneAccount =
            telecomManager.getUserSelectedOutgoingPhoneAccount();
        final List<PhoneAccountHandle> allPhoneAccounts =
            telecomManager.getCallCapablePhoneAccounts();

        //SPRD: modify for bug588658
        PhoneAccount pa = telecomManager.getPhoneAccount(phoneAccount);
        final boolean isPhoneAccountAvialable = (phoneAccount != null) && (pa != null);
        simPref.setTitle(R.string.calls_title);
        int  str = R.string.sim_calls_ask_first_prefs_title;
        //fixed Bug 754830
        if (DualVolteController.isDualVoLTEModeActive()) {
            str = R.string.sim_calls_not_set_up;
        }
        //SPRD: modify for bug819138
        if (isPhoneAccountAvialable) {
            SubscriptionInfo subInfo = mSubscriptionManager.getActiveSubscriptionInfoForIccIndex(phoneAccount.getId());
            simPref.setSummary(subInfo != null ? subInfo.getDisplayName() : pa.getLabel());
        } else {
            simPref.setSummary(mContext.getResources().getString(str));
        }
//        simPref.setSummary(phoneAccount == null
//                ? mContext.getResources().getString(R.string.sim_calls_ask_first_prefs_title)
//                : (String)telecomManager.getPhoneAccount(phoneAccount).getLabel());
        simPref.setEnabled(allPhoneAccounts.size() > 1);
        // UNISOC: add for new featrue:Smart Dual SIM
        refreshSmartDualSimStatus();
    }

    @Override
    public void onStart() {
        super.onStart();
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.MOBILE_DATA + SubscriptionManager.MAX_SUBSCRIPTION_ID_VALUE), true,
                mMobileDataObserver, UserHandle.USER_OWNER);
        // UNISOC:add for Bug968889,set the data card out of sync in split screen mode.
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION), true,
                mDataSubscriptionObserver, UserHandle.USER_OWNER);
        mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        /** UNISOC: add new feature for data switch on/off @{ */
        getContentResolver().registerContentObserver(Settings.Global.getUriFor(SettingsEx.GlobalEx.RADIO_BUSY), true,
                mRadioBusyObserver, UserHandle.USER_OWNER);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSubscriptions();
        // UNISOC: add for new featrue:Smart Dual SIM
        refreshSmartDualSimStatus();
        final TelephonyManager tm =
                (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        //if (mSelectableSubInfos.size() > 1) {
        if (mSelectableSubInfos.size() > 0) {
            Log.d(TAG, "Register for call state change");
            //for (int i = 0; i < mPhoneCount; i++) {
            for (int i = 0; i < mSelectableSubInfos.size(); i++) {
                int subId = mSelectableSubInfos.get(i).getSubscriptionId();
                tm.listen(getPhoneStateListener(mSelectableSubInfos.get(i).getSimSlotIndex(), subId),
                        PhoneStateListener.LISTEN_CALL_STATE);
            }
        }

        for (int i = 0; i < mPhoneCount; i++) {
            final int phoneId = i;
            mRadioInteractorCallbackListener[i] = new RadioInteractorCallbackListener(phoneId) {
                @Override
                public void onRealSimStateChangedEvent() {
                    Log.d(TAG, "onRealSimStateChangedEvent");
                    updateSubscriptions();
                }
                @Override
                public void onSubsidyLockEvent(Object object) {
                    Log.d(TAG, "onSubsidyLockEvent " + object + "phoneId = " + phoneId);
                    if (object != null || phoneId == 0) {
                        updateSubscriptions();
                    }
                }
            };
            mRadioInteractor.listen(mRadioInteractorCallbackListener[i],
                    RadioInteractorCallbackListener.LISTEN_SIMMGR_SIM_STATUS_CHANGED_EVENT, false);
            mRadioInteractor.listen(mRadioInteractorCallbackListener[i],
                    RadioInteractorCallbackListener.LISTEN_SUBSIDYLOCK_EVENT, false);
        }

        IntentFilter intentFilter = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(mReceiver,intentFilter);
    }

    private void stopSimSettings() {
        Log.d(TAG, "inMultiWindowMode ,stop sim setting " );
        Toast.makeText(mContext, R.string.simsettings_forced_resizable, Toast.LENGTH_SHORT).show();
        finish();
    }

    private ContentObserver mMobileDataObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateCellularDataValues();
        }
    };

    private ContentObserver mDataSubscriptionObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateCellularDataValues();
        }
    };
    /* @} */
    private ContentObserver mRadioBusyObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateAllOptions();
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                    if (mDataAlerDialogFragment != null) {
                        mDataAlerDialogFragment.dismissAllowingStateLoss();
                    }
                }
            } else {
                updateAllOptions();
            }
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        final TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        for (int i = 0; i < mPhoneCount; i++) {
            if (mPhoneStateListener[i] != null) {
                tm.listen(mPhoneStateListener[i], PhoneStateListener.LISTEN_NONE);
                mPhoneStateListener[i] = null;
            }

            if (mRadioInteractorCallbackListener[i] != null) {;
                mRadioInteractor.listen(mRadioInteractorCallbackListener[i],
                        RadioInteractorCallbackListener.LISTEN_NONE);
                mRadioInteractorCallbackListener[i] = null;
            }
        }
        mContext.unregisterReceiver(mReceiver);
    }

    //Fix Bug931078,when SimSettings in split screen situation，the data switch don't update.
    @Override
    public void onStop() {
        super.onStop();
        //fix bug 722200 the data state can not Synchronize
        getContentResolver().unregisterContentObserver(mMobileDataObserver);
        getContentResolver().unregisterContentObserver(mDataSubscriptionObserver);
        getContentResolver().unregisterContentObserver(mRadioBusyObserver);
        mSubscriptionManager.removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
    }

    private PhoneStateListener getPhoneStateListener(int phoneId, int subId) {
        // Disable Sim selection for Data when voice call is going on as changing the default data
        // sim causes a modem reset currently and call gets disconnected
        // ToDo : Add subtext on disabled preference to let user know that default data sim cannot
        // be changed while call is going on
        final int i = phoneId;
        mPhoneStateListener[phoneId]  = new PhoneStateListener(subId) {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (DBG) log("PhoneStateListener.onCallStateChanged: state=" + state);
                mCallState[i] = state;
                updateCellularDataValues();
                updateSimSlotValues();
                updateDataPreferActiveDuringCall();
            }
        };
        return mPhoneStateListener[phoneId];
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        final Context context = mContext;
        Intent intent = new Intent(context, SimDialogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        if (preference instanceof SimPreference) {
//            Intent newIntent = new Intent(context, SimPreferenceDialog.class);
//            newIntent.putExtra(EXTRA_SLOT_ID, ((SimPreference)preference).getSlotId());
//            startActivity(newIntent);
            // UNISOC: modify for Re edit the SIM card interface
            if (getActivity() != null && getActivity().isResumed()) {
                SimFragmentDialog.show(SimSettings.this, ((SimPreference) preference).getSlotId());
            }
        } else if (findPreference(KEY_CELLULAR_DATA) == preference) {
            /** UNISOC:Add for bug950908,change SimDialogActivity to SimChooseDialogFragment @{ */
//            intent.putExtra(SimDialogActivity.DIALOG_TYPE_KEY, SimDialogActivity.DATA_PICK);
//            context.startActivity(intent);
            if (getActivity() != null && getActivity().isResumed()) {
                SimChooseDialogFragment.show(SimSettings.this, SimChooseDialogFragment.DATA_PICK);
            }
        } else if (findPreference(KEY_CALLS) == preference) {
//            intent.putExtra(SimDialogActivity.DIALOG_TYPE_KEY, SimDialogActivity.CALLS_PICK);
//            context.startActivity(intent);
            if (getActivity() != null && getActivity().isResumed()) {
                SimChooseDialogFragment.show(SimSettings.this, SimChooseDialogFragment.CALLS_PICK);
            }
        } else if (findPreference(KEY_SMS) == preference) {
//            intent.putExtra(SimDialogActivity.DIALOG_TYPE_KEY, SimDialogActivity.SMS_PICK);
//            context.startActivity(intent);
            if (getActivity() != null && getActivity().isResumed()) {
                SimChooseDialogFragment.show(SimSettings.this, SimChooseDialogFragment.SMS_PICK);
            }
            /** @} */
          // UNISOC: add for new featrue:Smart Dual SIM
        } else if (findPreference(KEY_SMART_DUAL_SIM) == preference) {
            return false;
        } /*UNISOC: Add Data enable during volte call for dual volte @{ */
        else if (mSwitchPreference == preference) {
            if (mSwitchPreference.isChecked()) {
                showDataAlertDialog(getNonDataPhoneId(), mSwitchPreference.isChecked());
            } else {
                TeleUtils.setDataEnabledDuringVolteCall(false,mContext);
            }
        }
        return true;
    }

    protected void updateDataPreferActiveDuringCall() {
        mSwitchPreference.setChecked(TeleUtils.getDataEnabledDuringVolteCall(mContext));
        if (mSelectableSubInfos != null && mSelectableSubInfos.size() > 1) {
            mSwitchPreference.setEnabled(isCallStateIdle() && !TeleUtils.isRadioBusy(mContext) && isShowDataPrefer());
        } else {
            mSwitchPreference.setEnabled(false);
        }
    }

    protected int getNonDataPhoneId() {
        int dataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        for (SubscriptionInfo sub : mSelectableSubInfos) {
            if (sub.getSubscriptionId() != dataSubId) {
                return sub.getSimSlotIndex() + 1;
            }
        }
        return SubscriptionManager.DEFAULT_SIM_SLOT_INDEX;
    }

    private void showDataAlertDialog(final int phoneId, final boolean onOff) {
        if (getActivity() != null && getActivity().isResumed()
                && mDataAlerDialogFragment == null) {
            DataAlertFragmentDialog.show(SimSettings.this, onOff, phoneId);
        }
    }

    protected void resetDataDialogFragment(DialogFragment dialogFragment) {
        mDataAlerDialogFragment = dialogFragment;
    }
    /* @} */

    /*UNISOC: Add feature: Enable or Disable SIM card. @{ */
    private class SimPreference extends Preference {
        private SubscriptionInfo mSubInfoRecord;
        private int mSlotId;
        Context mContext;
        private Switch mSwitch;
        boolean mInitDone;
        boolean mIsSimExist;

        public SimPreference(Context context, SubscriptionInfo subInfoRecord, int slotId) {
            super(context);
            setLayoutResource(R.layout.sim_preference);

            mContext = context;
            mSubInfoRecord = subInfoRecord;
            mSlotId = slotId;
            setKey("sim" + mSlotId);
            mIsSimExist = mRadioInteractor.getRealSimSatus(mSlotId) != 0;
            Log.d(TAG, "SimPreference[" + slotId + "]: " + mIsSimExist);
            update();
            mInitDone = true;
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);
            final View divider = holder.findViewById(R.id.two_target_divider);
            final View widgetFrame = holder.findViewById(android.R.id.widget_frame);
            if (divider != null) {
                divider.setVisibility(!mIsSimExist ? View.GONE : View.VISIBLE);
            }
            if (widgetFrame != null) {
                widgetFrame.setVisibility(!mIsSimExist ? View.GONE : View.VISIBLE);
            }
            mSwitch = (Switch) holder.findViewById(R.id.switchWidget);
            if (mSwitch != null) {
                mSwitch.setOnClickListener(null);
                mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        Log.d(TAG, "onCheckedChanged[" + mSlotId + "]: " + isChecked);
                        if(mAlertDialogFragment != null) return;
                        if (isChecked != mTmEx.isSimEnabled(mSlotId)) {
                            int simActiveCount = 0;
                            for (int i = 0; i < mPhoneCount; i++) {
                                boolean isSimExit = mRadioInteractor.getRealSimSatus(i) != 0;
                                if (mTmEx.isSimEnabled(i) && isSimExit) {
                                    simActiveCount++;
                                }
                            }
                            /*UNISOC:modify for single card can not be disabled @{ */
                            if (!isChecked && simActiveCount < 2) {
                                mSwitch.setChecked(mTmEx.isSimEnabled(mSlotId));
                                if (mToast != null) {
                                    mToast.cancel();
                                }
                                mToast = Toast.makeText(mContext,
                                        R.string.cannot_disable_two_sim_card,
                                        Toast.LENGTH_SHORT);
                                mToast.show();
                                return;
                            }
                            /* @} */
                            mIsChecked[mSlotId] = isChecked;
                            mSwitchHasChanged[mSlotId] = true;
                            showAlertDialog(isChecked);
                        }
                    }
                });
            }
            updateSwitchState();
        }

        public void update() {
            if (!isAdded()) {
                return;
            }

            final Resources res = mContext.getResources();

            setTitle(String.format(mContext.getResources()
                    .getString(R.string.sim_editor_title), (mSlotId + 1)));
            boolean isRadioBusy = TeleUtils.isRadioBusy(mContext);

            if (mSubInfoRecord != null) {
                String primaryTag = getTagDisplayName(mSubInfoRecord.getSubscriptionId());
                if (TextUtils.isEmpty(getPhoneNumber(mSubInfoRecord))) {
                    setSummary(primaryTag + mSubInfoRecord.getDisplayName());
                } else {
                    setSummary(primaryTag + mSubInfoRecord.getDisplayName() + " - " +
                            PhoneNumberUtils.createTtsSpannable(getPhoneNumber(mSubInfoRecord)));
                }
                setEnabled(!isRadioBusy);
                setIcon(new BitmapDrawable(res, (mSubInfoRecord.createIconBitmap(mContext))));
            } else if (mIsSimExist && !mTmEx.isSimEnabled(mSlotId)) {
                setSummary(R.string.sim_disabled);
                setFragment(null);
                setEnabled(false);
            } else {
                setSummary(R.string.sim_slot_empty);
                setFragment(null);
                setEnabled(false);
            }

            updateSwitchState();
        }

        private void updateSwitchState() {
            if (mSwitch != null) {
                if(!mTmEx.isDisableSimAllowed(mSlotId)){
                    Log.d(TAG, "not allow disable sim");
                    mSwitch.setChecked(true);
                    mSwitch.setEnabled(false);
                    return;
                }
                boolean isRadioBusy = TeleUtils.isRadioBusy(mContext);
                boolean isAirplaneModeOn = WirelessUtils.isAirplaneModeOn(mContext);
                Log.d(TAG, "updateSwitchState[" + mSlotId + "]: radioBusy: " + isRadioBusy
                        + " APM: " + isAirplaneModeOn +",isSimEnabled:"+mTmEx.isSimEnabled(mSlotId));
                /* SPRD:MODIFY FOR BUG601836 @{*/
                if (mSwitchHasChanged[mSlotId]) {
                    mSwitch.setChecked(mIsChecked[mSlotId]);
                    mSwitchHasChanged[mSlotId] = false;
                } else {
                    mSwitch.setChecked(mTmEx.isSimEnabled(mSlotId));
                }
                /* @}*/
                mSwitch.setEnabled(
                        mIsSimExist && !isRadioBusy && !isAirplaneModeOn && isCallStateIdle());
            }
        }

        private int getSlotId() {
            return mSlotId;
        }

        private void showAlertDialog(boolean onOff) {
            if (getActivity() != null /*&& getActivity().isResumed()*/
                    && mAlertDialogFragment == null) {
                SimEnabledAlertDialogFragment.show(SimSettings.this, mSlotId, onOff);
            }
        }
    }

    void clearSwitchChanged(int phoneId) {
        mSwitchHasChanged[phoneId] = false;
    }

    void resetAlertDialogFragment(DialogFragment dialogFragment) {
        mAlertDialogFragment = dialogFragment;
    }
    /* @} */

    // Returns the line1Number. Line1number should always be read from TelephonyManager since it can
    // be overridden for display purposes.
    private String getPhoneNumber(SubscriptionInfo info) {
        final TelephonyManager tm =
            (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        return tm.getLine1Number(info.getSubscriptionId());
    }

    private void log(String s) {
        Log.d(TAG, s);
    }

    /**
     * For search
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();

                    if (Utils.showSimCardTile(context)) {
                        SearchIndexableResource sir = new SearchIndexableResource(context);
                        sir.xmlResId = R.xml.sim_settings;
                        result.add(sir);
                    }

                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    final UserManager userManager = (UserManager) context.getSystemService(
                            Context.USER_SERVICE);

                    if (!userManager.isAdminUser()) {
                        keys.add(KEY_ACTIVITIES);
                        keys.add(KEY_CALLS);
                        keys.add(KEY_CELLULAR_DATA);
                        keys.add(KEY_SMS);
                        keys.add(KEY_SMART_DUAL_SIM);
                        keys.add(SIM_CARD_CATEGORY);
                    }

                    if (!context.getResources().getBoolean(R.bool.config_show_smartdualsim)) {
                        keys.add(KEY_SMART_DUAL_SIM);
                    }
                    return keys;
                }
            };

    private boolean isCallStateIdle() {
        boolean callStateIdle = true;
        for (int i = 0; i < mCallState.length; i++) {
            if (TelephonyManager.CALL_STATE_IDLE != mCallState[i]) {
                callStateIdle = false;
            }
        }
        Log.d(TAG, "isCallStateIdle " + callStateIdle);
        return callStateIdle;
    }

    // UNISOC: In the sub-items of the SIM card management interface, the main and sub-cards are distinguished
    public String getTagDisplayName(int subId) {
        boolean isShowTag = mContext.getResources().getBoolean(R.bool.show_primary_card_tag);
        if (isShowTag) {
            return SubscriptionManager.getDefaultDataSubscriptionId() == subId
                    ? mContext.getResources().getString(R.string.main_card_slot) : mContext.getResources().getString(R.string.secondary_card_slot);
        }
        return "";
    }

    /* UNISOC: add for new featrue:Smart Dual SIM @{ */
    private void refreshSmartDualSimStatus() {
        List<SubscriptionInfo> availableSubInfoList = getActiveSubInfoList();
        boolean showSmartDualSimsOption
                = mContext.getResources().getBoolean(R.bool.config_show_smartdualsim);
        if ((availableSubInfoList != null)
                && (availableSubInfoList.size() > 1)
                && showSmartDualSimsOption) {
            log("add KEY_SMART_DUAL_SIM");
            mSimPreferenceCatergory.addPreference(mSmartDualSim);
        } else {
            log("removePreference KEY_SMART_DUAL_SIM");
            mSimPreferenceCatergory.removePreference(mSmartDualSim);
        }
    }

    private List<SubscriptionInfo> getActiveSubInfoList() {
        if (mSubscriptionManager == null || getActivity() == null) {
            return new ArrayList<SubscriptionInfo>();
        }
        List<SubscriptionInfo> availableSubInfoList = mSubscriptionManager
                .getActiveSubscriptionInfoList();
        if (availableSubInfoList == null) {
            return new ArrayList<SubscriptionInfo>();
        }
        Iterator<SubscriptionInfo> iterator = availableSubInfoList.iterator();
        TelephonyManager telephonyManager =
                (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        while (iterator.hasNext()) {
            SubscriptionInfo subInfo = iterator.next();
            int phoneId = subInfo.getSimSlotIndex();
            boolean isSimReady = telephonyManager
                    .getSimState(phoneId) == TelephonyManager.SIM_STATE_READY;
            if (!isSimReady) {
                iterator.remove();
            }
        }
        return availableSubInfoList;
    }
    /* @} */
}
