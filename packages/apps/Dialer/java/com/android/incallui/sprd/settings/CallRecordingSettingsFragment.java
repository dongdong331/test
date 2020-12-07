package com.android.incallui.sprd.settings;

import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import com.android.incallui.sprd.settings.callrecording.CallRecordingContactsHelper;
import com.android.incallui.sprd.settings.callrecording.CallRecordingContacts;
import com.android.incallui.sprd.settings.callrecording.CallRecordingContactsHelper.CallRecordSettingEntity;
import com.android.incallui.sprd.settings.callrecording.ListedNumberActivity;
import com.android.incallui.sprd.settings.callrecording.RecordListFrom;
import com.android.dialer.app.R;

public class CallRecordingSettingsFragment extends PreferenceFragment implements
        Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener{

    private final String KEY_AUTOMATIC_RECORDING = "automatic_recording_key";
    private final String KEY_RECORDING_NOTIFICATION = "recording_notification_key";
    private final String KEY_LISTED_NUMBERS = "listed_numbers_key";
    private final String KEY_RECORD_CALL_FROM = "record_calls_from_key";
    private SwitchPreference mButtonAutomaticRecording;
    private SwitchPreference mButtonRecordingNotification;
    private RecordListFrom mListRecordsCallFrom;
    private PreferenceScreen mButtonListedNumber;
    private PreferenceScreen mPrefScreen;

    private boolean isFirst;
    private final int KEY_LISTED_NUMBERS_CODE = 1001;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.call_recording_settings_ex);
        mPrefScreen = getPreferenceScreen();
        mButtonAutomaticRecording = (SwitchPreference) mPrefScreen.findPreference(KEY_AUTOMATIC_RECORDING);
        mButtonAutomaticRecording.setOnPreferenceChangeListener(this);
        mButtonRecordingNotification = (SwitchPreference) mPrefScreen.findPreference(KEY_RECORDING_NOTIFICATION);
        mButtonRecordingNotification.setOnPreferenceChangeListener(this);
        mListRecordsCallFrom = (RecordListFrom) mPrefScreen.findPreference(KEY_RECORD_CALL_FROM);
        mListRecordsCallFrom.setOnPreferenceChangeListener(this);
        mButtonListedNumber = (PreferenceScreen) mPrefScreen.findPreference(KEY_LISTED_NUMBERS);
        mButtonListedNumber.setOnPreferenceClickListener(this);
        setSettings();
    }
    //this mehtod is used to set initial values
    private void setSettings() {
        CallRecordSettingEntity recordSettings = CallRecordingContactsHelper
                .getInstance(getActivity()).getCallRecordingSettings();
        mButtonAutomaticRecording.setChecked(recordSettings.getAutoCallRecording());
        mButtonRecordingNotification.setChecked(recordSettings.getRecordingNotification());

        int selected = recordSettings.getRecordFrom();
        mListRecordsCallFrom.setSelected(selected);
        mListRecordsCallFrom.setValue(String.valueOf(selected));
        setListedNumberPref(selected);
        changeState(recordSettings.getAutoCallRecording());
        setNumberCount();
        isFirst =  recordSettings.getIsFirstSetting();
    }
    // set number of contacts added in record list
    private void setNumberCount() {
        int count = CallRecordingContactsHelper.getInstance(getActivity()).getCallRecordingNumberCount();
        // Add for bug758720
        String quantifier = count == 1 ? getActivity().getString(R.string.number_has_been_added) :
                getActivity().getString(R.string.numbers_has_been_added);
        String summary =  count + " " + quantifier;

        if (count == 0) {
            summary = getActivity().getString(R.string.none);
        }
        mButtonListedNumber.setSummary(summary);
    }
    //to update ui if selection changed
    private void setListedNumberPref(int selected) {
        if (mPrefScreen != null) {
            if (selected == 0) {
                mPrefScreen.removePreference(mButtonListedNumber);
            } else {
                mPrefScreen.addPreference(mButtonListedNumber);
            }
        }
    }
    //update ui if any change occur
    private void changeState(boolean state) {
        mListRecordsCallFrom.setEnabled(state);
        mButtonRecordingNotification.setEnabled(state);
        mButtonListedNumber.setEnabled(state);
    }
    // method is used to update auto call recordSettings
    private void updateCallRecordingSettings(String key,int value) {
        CallRecordingContactsHelper.getInstance(getActivity()).updateCallRecordingSettings(key,value);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mButtonAutomaticRecording) {
            changeState((boolean)newValue);
            updateCallRecordingSettings(CallRecordingContacts.COLUMN_AUTOMATIC_CALL_RECORDING
                    ,(boolean)newValue ? 1 : 0);
            if((boolean)newValue && isFirst){
                mButtonRecordingNotification.setChecked(isFirst);
                updateCallRecordingSettings(CallRecordingContacts.COLUMN_IS_FIRST,0);
                updateCallRecordingSettings(CallRecordingContacts.COLUMN_CALL_RECORDING_NOTIFICATION,1);
                // SPRD : Modify for bug 757875, reset value of isFirst
                isFirst = false;
            }
        }else if(preference == mButtonRecordingNotification){
            updateCallRecordingSettings(CallRecordingContacts.COLUMN_CALL_RECORDING_NOTIFICATION
                    ,(boolean)newValue ? 1 : 0);
        }else if(preference == mListRecordsCallFrom){
            int selected = Integer.parseInt((String)newValue);
            setListedNumberPref(selected);
            mListRecordsCallFrom.setSelected(selected);
            updateCallRecordingSettings(CallRecordingContacts.COLUMN_RECORDS_FROM, selected);
        }
        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey().equals(KEY_LISTED_NUMBERS)) {
            Intent intent = new Intent(getActivity(), ListedNumberActivity.class);
            startActivityForResult(intent, KEY_LISTED_NUMBERS_CODE);
            return true;
        }
        return false;
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // To maintain contact numbers count
        if (requestCode == KEY_LISTED_NUMBERS_CODE) {
            setNumberCount();
        }
    }
}

