package com.android.incallui.sprd.settings.callrecording;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.util.ArrayList;

/**
 *This class used for communication between callrecording db and ui class
 */

public class CallRecordingContactsHelper {

    private static CallRecordingContactsHelper callRecordingContactsHelper;
    private CallRecordingContacts callRecordingContacts;
    public static final int DEFAULT_TRUE = 1; //default true
    public static final int DEFAULT_FALSE = 0; //default false

    private CallRecordingContactsHelper(Context context) {
        callRecordingContacts = new CallRecordingContacts(context);
    }

    public static CallRecordingContactsHelper getInstance(Context context) {
        if (callRecordingContactsHelper == null){
            callRecordingContactsHelper = new CallRecordingContactsHelper(context);
        }
        return callRecordingContactsHelper;
    }

    //method to add contacts for auto call record
    public long addCallRecordingNumber(String number,String name) {
        ContentValues values = new ContentValues();
        values.put(CallRecordingContacts.COLUMN_NUMBER, number);
        values.put(CallRecordingContacts.COLUMN_NAME, name);
        long id = callRecordingContacts.getWritableDatabase()
                .insert(CallRecordingContacts.TABLE_CALL_RECORDING_CONTACTS, null, values);
        return id;
    }

    //method to get contacts that added for auto call record
    public ArrayList<String> getCallRecordingNumber() {
        ArrayList<String> contactList = new ArrayList<String>();
        Cursor cursor = callRecordingContacts.getWritableDatabase()
                .query(CallRecordingContacts.TABLE_CALL_RECORDING_CONTACTS, null, null, null, null,
                        null, null);

        if (cursor != null) {
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                contactList.add(cursor.getString(cursor.getColumnIndex(CallRecordingContacts.COLUMN_NUMBER)));
                cursor.moveToNext();
            }
            cursor.close();
        }
        return contactList;
    }

    //method to get contacts count that added for auto call record
    public int getCallRecordingNumberCount() {
        int count = 0;
        Cursor cursor = callRecordingContacts.getWritableDatabase()
                .query(CallRecordingContacts.TABLE_CALL_RECORDING_CONTACTS, null, null, null, null,
                        null, null);
        if (cursor != null) {
            count = cursor.getCount();
            cursor.close();
        }
        return count;
    }

    //method to update auto call recording settings
    public long updateCallRecordingSettings(String key,int value) {
        ContentValues values = new ContentValues();
        values.put(key, value);
        long id = callRecordingContacts.getWritableDatabase()
                .update(CallRecordingContacts.TABLE_CALL_RECORDING_SETTINGS, values, null, null);
        return  id;
    }

    //method to get auto call recording settings
    public CallRecordSettingEntity getCallRecordingSettings() {
        CallRecordSettingEntity recordSettings = new CallRecordSettingEntity();
        Cursor cursor = callRecordingContacts.getWritableDatabase()
                .query(CallRecordingContacts.TABLE_CALL_RECORDING_SETTINGS, null, null, null, null,
                        null, null);

        if (cursor != null && cursor.moveToFirst() ) {
            recordSettings.setAutoCallRecording(cursor.getInt(cursor
                    .getColumnIndex(CallRecordingContacts.COLUMN_AUTOMATIC_CALL_RECORDING)));
            recordSettings.setRecordingNotification(cursor.getInt(cursor
                    .getColumnIndex(CallRecordingContacts.COLUMN_CALL_RECORDING_NOTIFICATION)));
            recordSettings.setRecordFrom(cursor.getInt(cursor
                    .getColumnIndex(CallRecordingContacts.COLUMN_RECORDS_FROM)));
            recordSettings.setUnknownNumberSetting(cursor.getInt(cursor
                    .getColumnIndex(CallRecordingContacts.COLUMN_UNKNOWN_NUMBERS)));
            recordSettings.setIsFirstSetting(cursor.getInt(cursor
                    .getColumnIndex(CallRecordingContacts.COLUMN_IS_FIRST)));
        }else{
            ContentValues values = new ContentValues();
            values.put(CallRecordingContacts.COLUMN_AUTOMATIC_CALL_RECORDING, DEFAULT_FALSE);
            values.put(CallRecordingContacts.COLUMN_CALL_RECORDING_NOTIFICATION, DEFAULT_FALSE);
            values.put(CallRecordingContacts.COLUMN_RECORDS_FROM, DEFAULT_FALSE);
            values.put(CallRecordingContacts.COLUMN_UNKNOWN_NUMBERS, DEFAULT_FALSE);
            values.put(CallRecordingContacts.COLUMN_IS_FIRST, DEFAULT_TRUE);
            callRecordingContacts.getWritableDatabase()
                    .insert(CallRecordingContacts.TABLE_CALL_RECORDING_SETTINGS, null, values);
        }

        if(cursor != null){
            cursor.close();
        }
        return recordSettings;
    }

    //method to delete contacts that added for auto call record
    public void deleteContact(String number) {
        String selection = CallRecordingContacts.COLUMN_NUMBER + " LIKE ?";
        String[] selectionArgs = { number };
        int count = callRecordingContacts.getWritableDatabase()
                .delete(CallRecordingContacts.TABLE_CALL_RECORDING_CONTACTS, selection, selectionArgs);
    }

    //Data model to get/set values for auto call record settings
    public static class CallRecordSettingEntity{

        private boolean automatic_call_recording;
        private boolean recording_notification;
        private int records_from;
        private boolean unknown_numbers;
        private boolean is_first = true;

        public void setAutoCallRecording(int automatic_call_recording){
            this.automatic_call_recording = automatic_call_recording == DEFAULT_TRUE ? true :false;
        }

        public boolean getAutoCallRecording(){
            return automatic_call_recording;
        }

        public void setRecordingNotification(int recording_notification){
            this.recording_notification = recording_notification == DEFAULT_TRUE ? true :false;
        }

        public boolean getRecordingNotification(){
            return recording_notification;
        }

        public void setRecordFrom(int records_from){
            this.records_from = records_from;
        }

        public int getRecordFrom(){
            return records_from;
        }

        public void setUnknownNumberSetting(int unknown_numbers){
            this.unknown_numbers = unknown_numbers == DEFAULT_TRUE ? true :false;
        }

        public boolean getUnknownNumberSetting(){
            return unknown_numbers;
        }

        public void setIsFirstSetting(int is_first){
            this.is_first = is_first == DEFAULT_TRUE ? true :false;
        }

        public boolean getIsFirstSetting(){
            return is_first;
        }
    }
}
