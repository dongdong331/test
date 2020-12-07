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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemProperties;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.DualVolteController;
import com.android.settings.R;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SimDialogActivity extends Activity {
    private static String TAG = "SimDialogActivity";

    public static String PREFERRED_SIM = "preferred_sim";
    public static String DIALOG_TYPE_KEY = "dialog_type";
    public static final int INVALID_PICK = -1;
    public static final int DATA_PICK = 0;
    public static final int CALLS_PICK = 1;
    public static final int SMS_PICK = 2;
    public static final int PREFERRED_PICK = 3;
    private SubscriptionManager mSubscriptionManager;
    private Dialog mSimChooseDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        processIntent();
    }

    private void processIntent() {
        final Bundle extras = getIntent().getExtras();
        if(extras == null){
            return;
        }
        final int dialogType = getIntent().getIntExtra(DIALOG_TYPE_KEY, INVALID_PICK);

        switch (dialogType) {
            case DATA_PICK:
            case CALLS_PICK:
            case SMS_PICK:
                mSimChooseDialog = createDialog(this, dialogType);
                mSimChooseDialog.show();
                break;
            case PREFERRED_PICK:
                displayPreferredDialog(getIntent().getIntExtra(PREFERRED_SIM, 0));
                break;
            default:
                throw new IllegalArgumentException("Invalid dialog type " + dialogType + " sent.");
        }
    }

    /* UNISOC: MODIFY FOR BUG 589530:Hot swap, Settings crash @{ */
    @Override
    protected void onResume() {
        super.onResume();
        mSubscriptionManager = SubscriptionManager.from(this);
        mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        IntentFilter intentFilter = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        registerReceiver(mReceiver,intentFilter);
    }

    private final SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangeListener = new SubscriptionManager.OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            List<SubscriptionInfo> availableSubInfoList = SubscriptionManager
                    .from(getApplicationContext()).getActiveSubscriptionInfoList();
            if (availableSubInfoList == null || availableSubInfoList.size() < 2) {
                finish();
            }
        }
    };

    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mSimChooseDialog != null && mSimChooseDialog.isShowing()) {
                finish();
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        mSubscriptionManager.removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        unregisterReceiver(mReceiver);
    }
    /* @} */

    private void displayPreferredDialog(final int slotId) {
        final Resources res = getResources();
        final Context context = getApplicationContext();
        final SubscriptionInfo sir = SubscriptionManager.from(context)
                .getActiveSubscriptionInfoForSimSlotIndex(slotId);

        if (sir != null) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder.setTitle(R.string.sim_preferred_title);
            alertDialogBuilder.setMessage(res.getString(
                        R.string.sim_preferred_message, sir.getDisplayName()));

            alertDialogBuilder.setPositiveButton(R.string.yes, new
                    DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    final int subId = sir.getSubscriptionId();
                    PhoneAccountHandle phoneAccountHandle =
                            subscriptionIdToPhoneAccountHandle(subId);
                    setDefaultDataSubId(context, subId);
                    setDefaultSmsSubId(context, subId);
                    setUserSelectedOutgoingPhoneAccount(phoneAccountHandle);
                    finish();
                }
            });
            alertDialogBuilder.setNegativeButton(R.string.no, new
                    DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog,int id) {
                    finish();
                }
            });

            alertDialogBuilder.create().show();
        } else {
            finish();
        }
    }

    private static void setDefaultDataSubId(final Context context, final int subId) {
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
        subscriptionManager.setDefaultDataSubId(subId);
        Toast.makeText(context, R.string.data_switch_started, Toast.LENGTH_LONG).show();
    }

    private static void setDefaultSmsSubId(final Context context, final int subId) {
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
        subscriptionManager.setDefaultSmsSubId(subId);
    }

    private void setUserSelectedOutgoingPhoneAccount(PhoneAccountHandle phoneAccount) {
        final TelecomManager telecomManager = TelecomManager.from(this);
        telecomManager.setUserSelectedOutgoingPhoneAccount(phoneAccount);
    }

    private PhoneAccountHandle subscriptionIdToPhoneAccountHandle(final int subId) {
        final TelecomManager telecomManager = TelecomManager.from(this);
        final TelephonyManager telephonyManager = TelephonyManager.from(this);
        final Iterator<PhoneAccountHandle> phoneAccounts =
                telecomManager.getCallCapablePhoneAccounts().listIterator();

        while (phoneAccounts.hasNext()) {
            final PhoneAccountHandle phoneAccountHandle = phoneAccounts.next();
            final PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
            if (subId == telephonyManager.getSubIdForPhoneAccount(phoneAccount)) {
                return phoneAccountHandle;
            }
        }

        return null;
    }

    public Dialog createDialog(final Context context, final int id) {
        dismissSimChooseDialog();
        final ArrayList<String> list = new ArrayList<String>();
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
        final List<SubscriptionInfo> subInfoList =
            subscriptionManager.getActiveSubscriptionInfoList();
        final int selectableSubInfoLength = subInfoList == null ? 0 : subInfoList.size();

        final DialogInterface.OnClickListener selectionListener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int value) {
                        setDefaltSubIdByDialogId(id,subInfoList,value,context);
                    }
                };

        Dialog.OnKeyListener keyListener = new Dialog.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface arg0, int keyCode,
                    KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        finish();
                    }
                    return true;
                }
            };

        ArrayList<SubscriptionInfo> callsSubInfoList = new ArrayList<SubscriptionInfo>();
        if (id == CALLS_PICK) {
            final TelecomManager telecomManager = TelecomManager.from(context);
            final TelephonyManager telephonyManager = TelephonyManager.from(context);
            final Iterator<PhoneAccountHandle> phoneAccounts =
                    telecomManager.getCallCapablePhoneAccounts().listIterator();
            //fixed Bug 754830
            if(DualVolteController.isDualVoLTEModeActive()){
                list.add(getResources().getString(R.string.sim_calls_not_set_up));
            }else {
                list.add(getResources().getString(R.string.sim_calls_ask_first_prefs_title));
            }
            callsSubInfoList.add(null);
            while (phoneAccounts.hasNext()) {
                final PhoneAccount phoneAccount =
                        telecomManager.getPhoneAccount(phoneAccounts.next());
                list.add((String)phoneAccount.getLabel());
                int subId = telephonyManager.getSubIdForPhoneAccount(phoneAccount);
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    final SubscriptionInfo sir = SubscriptionManager.from(context)
                            .getActiveSubscriptionInfo(subId);
                    callsSubInfoList.add(sir);
                } else {
                    callsSubInfoList.add(null);
                }
            }
        } else {
            for (int i = 0; i < selectableSubInfoLength; ++i) {
                final SubscriptionInfo sir = subInfoList.get(i);
                CharSequence displayName = sir.getDisplayName();
                if (displayName == null) {
                    displayName = "";
                }
                list.add(displayName.toString());
            }
        }

        String[] arr = list.toArray(new String[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        ListAdapter adapter = new SelectAccountListAdapter(
                id == CALLS_PICK ? callsSubInfoList : subInfoList,
                builder.getContext(),
                R.layout.select_account_list_item,
                arr, id);

        switch (id) {
            case DATA_PICK:
                builder.setTitle(R.string.select_sim_for_data);
                break;
            case CALLS_PICK:
                builder.setTitle(R.string.select_sim_for_calls);
                break;
            case SMS_PICK:
                builder.setTitle(R.string.sim_card_select_title);
                break;
            default:
                throw new IllegalArgumentException("Invalid dialog type "
                        + id + " in SIM dialog.");
        }

        Dialog dialog = builder.setAdapter(adapter, selectionListener).create();
        dialog.setOnKeyListener(keyListener);

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                finish();
            }
        });

        return dialog;

    }

    private void dismissSimChooseDialog() {
        if (mSimChooseDialog != null && mSimChooseDialog.isShowing()) {
            mSimChooseDialog.dismiss();
        }
    }
    /*  UNISOC: Add for data switch prompt if necessary. @{ */
    private void handleDataSwitch(Context context, int subId) {
        boolean switchDataShowPrompt = context.getResources().getBoolean(R.bool.show_prompt_switch_data);
        int phoneId = SubscriptionManager.getPhoneId(subId);
        Log.d(TAG, "handleDataSwitch: subId=" + subId + " phoneId=" + phoneId);
        // Give prompt if switch data card from JIO SIM to non-JIO SIM.
        if (switchDataShowPrompt && isJioCard(SubscriptionManager.getDefaultDataSubscriptionId())
                && !isJioCard(subId)) {
            Resources res = context.getResources();
            String title = res.getString(R.string.confirm_data_dialog_title, phoneId + 1);
            new AlertDialog.Builder(context).setTitle(title)
                    .setMessage(res.getString(R.string.confirm_data_dialog_message))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.d(TAG, "Do switch data card.");
                            setDefaultDataSubId(context, subId);
                            finish();
                        }
                    }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }}).create().show();
        } else {
            setDefaultDataSubId(context, subId);
            finish();
        }
    }

    private boolean isJioCard(int subId) {
        Context context = getApplicationContext();
        String SPN = TelephonyManager.from(context).getSimOperatorName(subId);
        Log.d(TAG, "SPN[" + subId + "]: " + SPN);
        if (!TextUtils.isEmpty(SPN) && SPN.matches("J(IO|io).*")) {
            return true;
        }
        return false;
    }
    /* @} */
    private  void setDefaltSubIdByDialogId (int dialogId,List<SubscriptionInfo> subInfoList,int value,final Context context){
        final SubscriptionInfo sir;

        switch (dialogId) {
            case DATA_PICK:
                sir = subInfoList.get(value);
                /* : Add for data switch prompt if necessary. @{ */
                //setDefaultDataSubId(context, sir.getSubscriptionId());
                handleDataSwitch(context,sir.getSubscriptionId());
                return;
            case CALLS_PICK:
                final TelecomManager telecomManager =
                        TelecomManager.from(context);
                final List<PhoneAccountHandle> phoneAccountsList =
                        telecomManager.getCallCapablePhoneAccounts();
                setUserSelectedOutgoingPhoneAccount(
                        value < 1 ? null : phoneAccountsList.get(value - 1));
                break;
            case SMS_PICK:
                sir = subInfoList.get(value);
                setDefaultSmsSubId(context, sir.getSubscriptionId());
                break;
            default:
                throw new IllegalArgumentException("Invalid dialog type "
                        + dialogId + " in SIM dialog.");
        }
        finish();
    }

    private class SelectAccountListAdapter extends ArrayAdapter<String> {
        private Context mContext;
        private int mResId;
        private int mDialogId;
        private final float OPACITY = 0.54f;
        private List<SubscriptionInfo> mSubInfoList;

        public SelectAccountListAdapter(List<SubscriptionInfo> subInfoList,
                Context context, int resource, String[] arr, int dialogId) {
            super(context, resource, arr);
            mContext = context;
            mResId = resource;
            mDialogId = dialogId;
            mSubInfoList = subInfoList;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater)
                    mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView;
            final ViewHolder holder;

            if (convertView == null) {
                // Cache views for faster scrolling
                rowView = inflater.inflate(mResId, null);
                holder = new ViewHolder();
                holder.title = (TextView) rowView.findViewById(R.id.title);
                holder.summary = (TextView) rowView.findViewById(R.id.summary);
                holder.icon = (ImageView) rowView.findViewById(R.id.icon);
                holder.defaultSubscription = (RadioButton) rowView
                        .findViewById(R.id.default_subscription_off);
                rowView.setTag(holder);
            } else {
                rowView = convertView;
                holder = (ViewHolder) rowView.getTag();
            }

            final SubscriptionInfo sir = mSubInfoList.get(position);
            if (sir == null) {
                holder.title.setText(getItem(position));
                holder.summary.setText("");
                holder.icon.setImageDrawable(getResources()
                        .getDrawable(R.drawable.ic_live_help));
                holder.icon.setAlpha(OPACITY);
            } else {
                holder.title.setText(sir.getDisplayName());
                holder.summary.setTextDirection(View.TEXT_DIRECTION_LTR);
                holder.summary.setText(sir.getNumber());
                holder.icon.setImageBitmap(sir.createIconBitmap(mContext));
            }
            updateSubscriptionRadioButton(holder,sir,position);
            final boolean isSubIdChecked = holder.defaultSubscription.isChecked();
            rowView.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    Log.d(TAG, "onCheckedChanged isSubIdChecked = " + isSubIdChecked);
                    if (!isSubIdChecked) {
                        setDefaltSubIdByDialogId(mDialogId,mSubInfoList,position,mContext);
                    } else {
                        finish();
                    }
                }
            });
            holder.defaultSubscription.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d(TAG, "onCheckedChanged isSubIdChecked = " + isSubIdChecked);
                    if (!isSubIdChecked) {
                        setDefaltSubIdByDialogId(mDialogId,mSubInfoList,position,mContext);
                    } else {
                        finish();
                    }
                }
            });
           /* @} */
            return rowView;
        }

        private void updateSubscriptionRadioButton(ViewHolder holder, SubscriptionInfo sir, int position) {
            switch (mDialogId) {
                case DATA_PICK:
                    holder.defaultSubscription.setChecked(
                            sir != null ? mSubscriptionManager.getDefaultDataSubscriptionId() == sir.getSubscriptionId() : false);
                    break;
                case CALLS_PICK:
                    TelecomManager telecomManager = TelecomManager.from(mContext);
                    PhoneAccountHandle phoneAccount = telecomManager.getUserSelectedOutgoingPhoneAccount();
                    if (sir == null) {
                        holder.defaultSubscription.setChecked(0 == position
                                && phoneAccount == null);
                    } else {
                        holder.defaultSubscription.setChecked(phoneAccount != null
                                && mSubscriptionManager.getDefaultVoiceSubscriptionId() == sir.getSubscriptionId());
                    }
                    break;
                case SMS_PICK:
                    holder.defaultSubscription.setChecked(
                            sir != null ? mSubscriptionManager.getDefaultSmsSubscriptionId() == sir.getSubscriptionId() : false);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid dialog type " + mDialogId + " in SIM dialog.");
            }
        }

        private class ViewHolder {
            TextView title;
            TextView summary;
            ImageView icon;
            // UNISOC: modify by add radioButton on set defult sub id
            RadioButton defaultSubscription;
        }
    }
}
