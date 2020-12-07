/**
 * Copyright (C) 2015 Spreadtrum Communications Inc.
 * <p>
 * SelectPhoneAccountDialogFragment.java
 * Created at 1:48:02 PM, Sep 1, 2015
 */
package com.android.dialer.app.fastdial;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;


import com.android.dialer.app.fastdial.CallSettingsActivityContainer;
import com.android.dialer.app.fastdial.SelectPhoneAccountDialogFragment.SelectPhoneAccountListener;
import com.android.dialer.app.R;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FastDialSettingActivity extends Activity implements View.OnClickListener,
        DialogInterface.OnClickListener, DialogInterface.OnShowListener {

    private static String TAG = FastDialManager.TAG;
    private static final int REQUESET_CODE_SELECT_CONTACTS = 4;
    private static final String PHONE_PACKAGE = "com.android.phone";
    private static final String CALL_SETTINGS_CLASS_NAME =
            "com.android.phone.settings.VoicemailSettingsActivity";
    private GridView gridView;
    private int fastDialIndex;
    private Dialog mInputDialog;
    private SubscriptionManager mSubscriptionManager;
    private TelephonyManager mTelephonyManager;
    private static final String KEY_IS_FAST_INDEX = "is_fast_index";
    private CallSettingsActivityContainer mActivityContainer;
    private static final int NO_DECIDE_BY_PHONEID = -1;
    private AlertDialog mDeleteDialog = null;
    private boolean mIsSelected;
    private AlertDialog mDialog = null;
    private AlertDialog mOperationDialog;
    // UNISOC: add for FEATURE bug903732
    private String mOldPhoneNumber;
    // Extra on intent containing the id of a subscription.
    private static final String SUB_ID_EXTRA =
            "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionId";
    // Extra on intent containing the label of a subscription.
    private static final String SUB_LABEL_EXTRA =
            "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionLabel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /* SPRD: for bug494223.  @{ */
        if (FastDialManager.getInstance() == null) {
            FastDialManager.init(this);
        }
        /* }@ */
        mSubscriptionManager = SubscriptionManager.from(this);
        mTelephonyManager = (TelephonyManager) TelephonyManager.from(this);
        if (this.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.fast_dial_setting_land_ex);
        } else if (this.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT) {
            setContentView(R.layout.fast_dial_setting_ex);
        }

        ActionBar actionBar = getActionBar();
        Log.d(TAG, "actionBar " + actionBar);
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.quantum_ic_arrow_back_white_24);
        }
        gridView = (GridView) findViewById(R.id.fast_dial_grid);
        gridView.setOnItemClickListener(new OnItemClickListenerImpl(this));
        FastDialManager.getInstance().setGridView(gridView);
        /* SPRD: add for bug604693 @{ */
        if (savedInstanceState != null) {
            fastDialIndex = savedInstanceState.getInt(KEY_IS_FAST_INDEX);
        }
        /* @} */
        /* SPRD: add to receive sim state to fix bug 506773 @{ */
        final IntentFilter intentFilter = new IntentFilter(
                TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        registerReceiver(mReceiver, intentFilter);
        /* @} */

        /* SPRD: add for bug645817 @{ */
        mActivityContainer = CallSettingsActivityContainer.getInstance();
        mActivityContainer.setApplication(getApplication());
        mActivityContainer.addActivity(this, NO_DECIDE_BY_PHONEID);
        /* @} */
    }

    private class OnItemClickListenerImpl implements OnItemClickListener {

        private Context mContext = null;

        public OnItemClickListenerImpl(Context context) {
            // TODO Auto-generated constructor stub
            mContext = context;
        }

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (position == 0) {
                // voice mail setting
                int[] subInfos = mSubscriptionManager.getActiveSubscriptionIdList();
                int subCount = (subInfos == null) ? 0 : subInfos.length;
                int[] standbySims = new int[subCount];
                int standbyCount = 0;

                for (int i = 0; i < subCount; i++) {
                    int phoneId = SubscriptionManager.getPhoneId(subInfos[i]);
                    boolean isStandby = true;
                    /* SPRD: bug#494066, delete useless code @{ */
                    // Judge the state of SIMCard, if it's not ready, making the card not standby.
                    int simState = mTelephonyManager.getSimState(phoneId);
                    if (simState != TelephonyManager.SIM_STATE_READY
                        // TODO: the interface isSimStandby is not exists
                            /*|| !mTelephonyManager.isSimStandby(phoneId)*/) {
                        isStandby = false;
                    }
                    /* @} */
                    if (isStandby) {
                        standbySims[i] = 1;
                        standbyCount++;
                    }
                }

                if (0 == standbyCount) {
                    Toast.makeText(mContext, R.string.no_sim_text, Toast.LENGTH_SHORT).show();
                } else if (1 == standbyCount) {
                    for (int i = 0; i < subCount; i++) {
                        if (1 == standbySims[i]) {
                            int subId = subInfos[i];
                            final Intent intent = new Intent(Intent.ACTION_MAIN);
                            intent.setClassName(PHONE_PACKAGE, CALL_SETTINGS_CLASS_NAME);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            SubscriptionInfo subscription =
                                    mSubscriptionManager.getActiveSubscriptionInfo(subId);
                            // SPRD: fix bug  426079
                            //intent.putExtra(SubscriptionInfoHelper.SUB_ID_EXTRA, subId);
                            addExtrasToIntent(intent, subscription);
                            startActivity(intent);
                        }
                    }
                } else {
                    // BUG424690 add voicemail funciton on dual card mode.
                    final TelecomManager mTelecomManager
                            = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
                    List<PhoneAccountHandle> subscriptionAccountHandles
                            = new ArrayList<PhoneAccountHandle>();
                    List<PhoneAccountHandle> accountHandles
                            = mTelecomManager.getCallCapablePhoneAccounts();
                    for (PhoneAccountHandle accountHandle : accountHandles) {
                        PhoneAccount account = mTelecomManager.getPhoneAccount(accountHandle);
                        if (account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
                            subscriptionAccountHandles.add(accountHandle);
                            Log.d(TAG, "accountHandle id = " + accountHandle.getId());
                        }
                    }
                    /* SPRD: add for bug660826 @{ */
                    mDialog = createSelectPhoneAccountDialog(subscriptionAccountHandles);
                    mDialog.show();
                    /* @} */
                }
            } else {
                fastDialIndex = position + 1;
                Log.i(TAG, "fastDialIndex= " + fastDialIndex);
                HashMap<String, Object> map
                        = (HashMap<String, Object>) parent.getAdapter().getItem(position);
                String mPhoneNameString = (String) map.get("contacts_cell_name");
                // UNISOC: add for FEATURE bug903732
                mOldPhoneNumber = FastDialManager.getInstance().getCallNumber(fastDialIndex);

                if (getString(R.string.menu_add).equals(map.get("contacts_cell_name"))) {
                    // add new number
                    selectFastDial();
                } else {
                    // popup delete or update dialog
                    showDeleteDialog(mPhoneNameString);
                }
            }
        }

        public void selectFastDial() {
            if (mInputDialog != null) {
                mInputDialog.dismiss();
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(FastDialSettingActivity.this);
            builder.setIcon(R.mipmap.ic_launcher_phone);
            builder.setTitle(R.string.fast_dial_settings);
            builder.setPositiveButton(android.R.string.ok, FastDialSettingActivity.this);
            builder.setNegativeButton(android.R.string.cancel, FastDialSettingActivity.this);
            builder.setView(View.inflate(FastDialSettingActivity.this,
                    R.layout.fast_dial_input_dialog_ex, null));
            mInputDialog = builder.create();
            mInputDialog.setOnShowListener(FastDialSettingActivity.this);

            mInputDialog.show();
            /* UNISOC: add for FEATURE bug903732 @{ */
            EditText editText = (EditText) mInputDialog.findViewById(R.id.number);
            if (editText != null) {
                editText.setText(mOldPhoneNumber);
                // UNISOC: add for bug bug903181
                editText.setSelection(mOldPhoneNumber.length());

            }
            /* @} */

        }

        public void showDeleteDialog(final String phoneName) {
            /* SPRD: modify for bug701697 @{ */
            AlertDialog.Builder builder = new AlertDialog.Builder(FastDialSettingActivity.this);
            builder.setTitle(phoneName);
            builder.setItems(R.array.items_fastdial_dialog,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) {// update
                                selectFastDial();
                            } else {// delete
                                showDeleteConfirmDialog(phoneName);
                                dialog.dismiss();
                            }
                        }
                    });
            builder.setNegativeButton(getString(R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            mOperationDialog = builder.create();
            mOperationDialog.show();
            /* @} */
        }

        public void showDeleteConfirmDialog(String phoneName) {
            /* SPRD: add for bug651804 @{ */
            AlertDialog.Builder builder = new AlertDialog.Builder(FastDialSettingActivity.this);
            builder.setTitle(R.string.delete_fastdial);
            builder.setMessage(phoneName);
            builder.setPositiveButton(getString(R.string.alert_dialog_yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            FastDialManager.getInstance().deleteFastDial(fastDialIndex);
                        }
                    });
            builder.setNegativeButton(getString(R.string.alert_dialog_no),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });

            mDeleteDialog = builder.create();
            mDeleteDialog.show();
            /* @} */
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // SPRD: modify for bug601333
        FastDialManager.getInstance().flushFdMem();
    }

    @Override
    protected void onPause() {
        super.onPause();
        /* SPRD: add for bug660826 @{ */
        if (!mIsSelected) {
            dismissDialog();
        }
        /* @} */
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mSubscriptionManager.getActiveSubscriptionIdList().length
                < TelephonyManager.getDefault().getPhoneCount()) {
            // SPRD: modify for bug660826
            dismissDialog();
        }
        if (this.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.fast_dial_setting_land_ex);
            Log.d(TAG, "onConfigurationChanged: landscape");
            gridView = (GridView) findViewById(R.id.fast_dial_grid);
            gridView.setOnItemClickListener(new OnItemClickListenerImpl(this));
            FastDialManager.getInstance().setGridView(gridView);

        } else if (this.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT) {
            Log.d(TAG, "onConfigurationChanged: portrait");
            setContentView(R.layout.fast_dial_setting_ex);
            gridView = (GridView) findViewById(R.id.fast_dial_grid);
            gridView.setOnItemClickListener(new OnItemClickListenerImpl(this));
            FastDialManager.getInstance().setGridView(gridView);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                finish();
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // SPRD: add to fix bug 506773
        unregisterReceiver(mReceiver);
        /* SPRD: add for bug645817 @{ */
        if (mActivityContainer != null) {
            mActivityContainer.removeActivity(this);
        }
        /* @} */
        /* SPRD: add for bug651804 @{ */
        if ((mDeleteDialog != null) && (mDeleteDialog.isShowing())) {
            mDeleteDialog.dismiss();
        }
        /* @} */
        /* SPRD: add for bug701697 @{ */
        if ((mOperationDialog != null) && (mOperationDialog.isShowing())) {
            mOperationDialog.dismiss();
        }
        /* @} */
        /* SPRD: add for bug933351 the dialog disapear when lock screen @{ */
        if (mInputDialog != null && mInputDialog.isShowing()) {
            mInputDialog.dismiss();
            mInputDialog = null;
        }
        /* @} */
        Log.d(TAG, "FastDialSettingActivity onDestroy");
    }

    /* SPRD: add for bug604693 @{ */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_IS_FAST_INDEX, fastDialIndex);
    }
    /* @} */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (resultCode != RESULT_OK) {
            Log.d(TAG, "fail due to resultCode=" + resultCode);
            return;
        }
        int myRequestCode = requestCode;
        switch (myRequestCode) {
            case REQUESET_CODE_SELECT_CONTACTS:
                /* SPRD: add for bug933351 the dialog disapear when lock screen @{ */
                if (mInputDialog != null) {
                    mInputDialog.dismiss();
                    mInputDialog = null;
                }
                /* @} */
                FastDialManager.getInstance().addFastDial(data, fastDialIndex);
                break;
            default:
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            EditText editText = (EditText) ((AlertDialog) dialog).findViewById(R.id.number);
            final String number = editText.getText().toString();
            /* UNISOC: modify for bug914472  @{ */
            if (TextUtils.isEmpty(number) || !is12Key(number)) {
                Toast.makeText(FastDialSettingActivity.this, R.string.fast_dial_number_invalid,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            /* @} */
            FastDialManager.getInstance().addFastDial(null, fastDialIndex, number);
        }
    }

    @Override
    public void onShow(DialogInterface dialog) {
        ImageView imageView = (ImageView) ((AlertDialog) dialog).findViewById(R.id.contacts);
        imageView.setOnClickListener(this);
        /* UNISOC: modify for bug924685  @{ */
        EditText editText = (EditText) ((AlertDialog) dialog).findViewById(R.id.number);
        editText.post(new Runnable() {
            @Override
            public void run() {
                InputMethodManager inputMethodManager =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.showSoftInput(editText, 0);
            }
        });
        /* @} */
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.contacts) {
            Intent mContactListIntent = new Intent(Intent.ACTION_PICK);
            mContactListIntent.setType(Phone.CONTENT_TYPE);
            try {
                FastDialSettingActivity.this
                        .startActivityForResult(mContactListIntent, REQUESET_CODE_SELECT_CONTACTS);
            } catch (ActivityNotFoundException e) {
                String toast = this.getResources()
                        .getString(com.android.internal.R.string.noApplications);
                Toast.makeText(this, toast, Toast.LENGTH_LONG).show();
                Log.e(TAG, "No Activity found to handle Intent: " + mContactListIntent);
            }
        }
    }

    /* SPRD: add to receive sim state to fix bug 506773 @{ */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                if (stateExtra != null
                        && IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                    // SPRD: modify for bug660826
                    dismissDialog();
                }
            }
        }
    };
    /* @} */

    /* SPRD: add for bug660826 @{ */
    private AlertDialog createSelectPhoneAccountDialog(List<PhoneAccountHandle> accountHandles) {

        mIsSelected = false;
        final DialogInterface.OnClickListener selectionListener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mIsSelected = true;
                        PhoneAccountHandle selectedAccountHandle = accountHandles.get(which);
                        final List<SubscriptionInfo> sil =
                                mSubscriptionManager.getActiveSubscriptionInfoList();
                        SubscriptionInfo subscription = null;
                        for (int i = 0; i < sil.size(); i++) {
                            if (selectedAccountHandle.getId()
                                    .equals(String.valueOf(sil.get(i).getIccId()))) {
                                subscription = sil.get(i);
                            }
                        }
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.setClassName(PHONE_PACKAGE, CALL_SETTINGS_CLASS_NAME);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        addExtrasToIntent(intent, subscription);
                        Log.d(TAG, "selectedAccountHandle id = " + selectedAccountHandle.getId());
                        startActivity(intent);
                    }
                };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        ListAdapter selectAccountListAdapter = new SelectAccountListAdapter(
                builder.getContext(),
                R.layout.select_account_list_item_ex,
                accountHandles);
        AlertDialog dialog = builder.setTitle(R.string.sim_list_title)
                .setAdapter(selectAccountListAdapter, selectionListener)
                .create();

        return dialog;
    }

    private class SelectAccountListAdapter extends ArrayAdapter<PhoneAccountHandle> {
        private int mResId;

        public SelectAccountListAdapter(
                Context context, int resource, List<PhoneAccountHandle> accountHandles) {
            super(context, resource, accountHandles);
            mResId = resource;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater =
                    (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View rowView;
            final ViewHolder holder;

            if (convertView == null) {
                // Cache views for faster scrolling
                rowView = inflater.inflate(mResId, null);
                holder = new ViewHolder();
                holder.labelTextView = (TextView) rowView.findViewById(R.id.label);
                holder.numberTextView = (TextView) rowView.findViewById(R.id.number);
                holder.imageView = (ImageView) rowView.findViewById(R.id.icon);
                rowView.setTag(holder);
            } else {
                rowView = convertView;
                holder = (ViewHolder) rowView.getTag();
            }

            PhoneAccountHandle accountHandle = getItem(position);
            TelecomManager telecomManager =
                    (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            PhoneAccount account = telecomManager.getPhoneAccount(accountHandle);
            holder.labelTextView.setText(account.getLabel());
            if (account.getAddress() == null ||
                    TextUtils.isEmpty(account.getAddress().getSchemeSpecificPart())) {
                holder.numberTextView.setVisibility(View.GONE);
            } else {
                holder.numberTextView.setVisibility(View.VISIBLE);
                holder.numberTextView.setText(
                        PhoneNumberUtils
                                .createTtsSpannable(account.getAddress().getSchemeSpecificPart()));
            }

            holder.imageView.setImageDrawable(account.getIcon() != null
                    ? account.getIcon().loadDrawable(getContext()) : null);
            holder.labelTextView.setEllipsize(TextUtils.TruncateAt.END);
            holder.labelTextView.setSingleLine(true);
            return rowView;
        }

        private class ViewHolder {
            TextView labelTextView;
            TextView numberTextView;
            ImageView imageView;
        }
    }

    private void dismissDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }
    /* @} */

    private static void addExtrasToIntent(Intent intent, SubscriptionInfo subscription) {
        if (subscription == null) {
            return;
        }

        intent.putExtra(SUB_ID_EXTRA, subscription.getSubscriptionId());
        intent.putExtra(SUB_LABEL_EXTRA, subscription.getDisplayName().toString());
    }

    /* UNISOC: modify for bug914472,969500  @{ */
    private boolean is12Key(String phoneNumber){
        for (int i = 0, count = phoneNumber.length(); i < count; i++) {
            if (!PhoneNumberUtils.isDialable(phoneNumber.charAt(i))) {
                return false;
            }
        }
        return true;
    }
    /* @} */
}

