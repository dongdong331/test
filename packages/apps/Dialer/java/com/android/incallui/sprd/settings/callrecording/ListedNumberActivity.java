/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.incallui.sprd.settings.callrecording;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneNumberUtils;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.view.MenuItem;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.dialer.app.R;
import com.android.incallui.sprd.InCallUiUtils;
import com.android.incallui.sprd.settings.callrecording.CallRecordingContactsHelper.CallRecordSettingEntity;
import java.util.ArrayList;

/**
 * Displays list of number that added for auto call recording
 */
public class ListedNumberActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, View.OnClickListener,
        DialogInterface.OnClickListener, DialogInterface.OnShowListener {

    private AddedContactsAdapter addedContactsAdapter;
    private ArrayList<String> addedContactsList;
    private final String TAG = "ListedNumberActivity";
    private Dialog mInputDialog;
    private static final int REQUESET_CODE_SELECT_CONTACTS = 4;
    private EditText mEditText;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.activity_listed_number);

        addedContactsList = new ArrayList<String>();
        addedContactsList = CallRecordingContactsHelper.getInstance(this).getCallRecordingNumber();
        ListView listView = (ListView) findViewById(R.id.list_added_number);
        listView.setVisibility(View.VISIBLE);
        addedContactsAdapter = new AddedContactsAdapter(this, addedContactsList);
        listView.setAdapter(addedContactsAdapter);
        listView.setOnItemClickListener(this);
        CheckBox unknownNumber = (CheckBox) findViewById(R.id.unknown_number);
        setSettings(unknownNumber);
        unknownNumber.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                CallRecordingContactsHelper.getInstance(ListedNumberActivity.this)
                        .updateCallRecordingSettings(CallRecordingContacts.COLUMN_UNKNOWN_NUMBERS, isChecked ? 1 : 0);
            }
        });

        TextView textView = (TextView) findViewById(R.id.add_number);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectContact();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String number = addedContactsAdapter.getItem(position);
        CallRecordingContactsHelper.getInstance(ListedNumberActivity.this).deleteContact(number);
        addedContactsList.remove(position);
        addedContactsAdapter.onDataSetChanged(addedContactsList);
    }

    private void setSettings(CheckBox unknownNumber) {
        CallRecordSettingEntity recordSettings = CallRecordingContactsHelper.getInstance(this)
                .getCallRecordingSettings();
        unknownNumber.setChecked(recordSettings.getUnknownNumberSetting());
    }

    /*
     * SPRD : Modify for bug 770260 @{
     *
     */
    public void selectContact() {
        if (mInputDialog != null) {
            mInputDialog.dismiss();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(ListedNumberActivity.this);
        builder.setIcon(R.mipmap.ic_launcher_phone);
        builder.setTitle(R.string.add_number);
        builder.setPositiveButton(android.R.string.ok, ListedNumberActivity.this);
        builder.setNegativeButton(android.R.string.cancel, ListedNumberActivity.this);
        builder.setView(View.inflate(ListedNumberActivity.this,
                R.layout.dialog_contact_picker, null));
        mInputDialog = builder.create();

        mInputDialog.setOnShowListener(ListedNumberActivity.this);
        mInputDialog.show();
    }

    //This method to adjust dialog width on orientation changed.
    private void adjustDialog(){
        if (mInputDialog == null) {
            return;
        }

        Window window = mInputDialog.getWindow();
        window.setGravity(Gravity.CENTER);

        WindowManager.LayoutParams layoutParams = window.getAttributes();
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int max_width = (int) (displayMetrics.widthPixels * 0.90);
        int max_height = displayMetrics.heightPixels;

        if (max_height > max_width) {
            layoutParams.width = max_width;
        } else {
            layoutParams.width = max_height;
        }
        window.setAttributes(layoutParams);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "requestCode=" + requestCode + ", resultCode=" + resultCode);
        if (resultCode != RESULT_OK) {
            Log.d(TAG, "fail due to resultCode=" + resultCode);
            return;
        }
        switch (requestCode) {
            case REQUESET_CODE_SELECT_CONTACTS:
                contactPicked(data);
                if (mInputDialog != null) { //add for bug940902
                    mInputDialog.dismiss();
                    mInputDialog = null;
                }
                break;
            default:
        }
    }

    /**
     * Query the Uri and read contact details. Handle the picked contact data.
     *
     * @param data
     */
    private void contactPicked(Intent data) {
        Cursor cursor = null;
        try {
            String phoneNo = null;
            String name = null;
            Uri uri = data.getData();
            cursor = getContentResolver().query(uri, null, null, null, null);

            if (cursor != null) {
                cursor.moveToFirst();
                int phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                phoneNo = cursor.getString(phoneIndex);
                //name = cursor.getString(nameIndex); //TODO: later name may be use
                addNumber(phoneNo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void addNumber(String number) {
        String phoneNumber;
        if (!TextUtils.isEmpty(number)) {
            //modify for bug930591
        //    phoneNumber = PhoneNumberUtils.normalizeNumber(number);
            phoneNumber = InCallUiUtils.removeNonNumericForNumber(number);
            Log.d(TAG, "addNumber  phone number after remove non-numeric: " + phoneNumber);
            /*
             * SPRD : Modify for bug 761451 @{
             * Check phoneNumber is empty or not, after normalize the phone number.
             */
            if (TextUtils.isEmpty(phoneNumber)) {
                Toast.makeText(ListedNumberActivity.this, getString(R.string.phone_number_not_saved), Toast.LENGTH_SHORT).show();
                return;
            }
            /* @} */
            /*
             * SPRD : Modify for bug 761490 @{
             * Check phone number contains in list or not.
             */
            if (addedContactsList.contains(phoneNumber)) {
                Toast.makeText(ListedNumberActivity.this, getString(R.string.phone_number_exists), Toast.LENGTH_SHORT).show();
                return;
            }
            /* @} */
            CallRecordingContactsHelper.getInstance(ListedNumberActivity.this)
                    .addCallRecordingNumber(phoneNumber,"");
            addedContactsList.add(phoneNumber);
            addedContactsAdapter.onDataSetChanged(addedContactsList);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            final String number = mEditText.getText().toString();
            addNumber(number);
        }
    }

    @Override
    public void onShow(DialogInterface dialog) {
        adjustDialog();
        ImageView imageView = (ImageView) ((AlertDialog) dialog).findViewById(R.id.pick_contact);
        imageView.setOnClickListener(this);
        mEditText = (EditText) ((AlertDialog) dialog).findViewById(R.id.number);
        mEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(15)});
        mEditText.post(new Runnable(){  //add for bug939377
            @Override
            public void run(){
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(mEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        }); 
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.pick_contact) {
            Intent mContactListIntent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            try {
                ListedNumberActivity.this
                        .startActivityForResult(mContactListIntent, REQUESET_CODE_SELECT_CONTACTS);
            } catch (ActivityNotFoundException e) {
                String toast = this.getResources()
                        .getString(Resources.getSystem().getIdentifier("noApplications", "string", "android"));
                Toast.makeText(this, toast, Toast.LENGTH_LONG).show();
                Log.e(TAG, "No Activity found to handle Intent: " + mContactListIntent);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
//        if (mInputDialog != null) {
//            mInputDialog.dismiss();
//            mInputDialog = null;
//        }  //modify for bug933886
    }

    @Override
    //add for bug933886
    protected void onDestroy(){
        super.onDestroy();
        if (mInputDialog != null) {
            mInputDialog.dismiss();
            mInputDialog = null;
        }
    }
    /* @} */
}
