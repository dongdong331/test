package com.android.incallui.sprd.settings.callrecording;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Auto call recording settings database
 * Created  on 6/20/2017.
 */

public class CallRecordingContacts extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "CallRecordingContacts.db";

    public static  final String TABLE_CALL_RECORDING_CONTACTS = "call_recording_contacts";
    public static  final String COLUMN_ID = "_id";
    public static  final String COLUMN_NUMBER = "phone_number";
    public static  final String COLUMN_NAME = "name";

    public static  final String TABLE_CALL_RECORDING_SETTINGS = "call_recording_settings";
    public static  final String COLUMN_AUTOMATIC_CALL_RECORDING = "automatic_call_recording";
    public static  final String COLUMN_CALL_RECORDING_NOTIFICATION = "recording_notification";
    public static  final String COLUMN_RECORDS_FROM = "records_from";
    public static  final String COLUMN_UNKNOWN_NUMBERS = "unknown_numbers";
    public static  final String COLUMN_IS_FIRST = "is_first";

    // Database creation sql statement
    private static final String DATABASE_CREATE_RECORDING_SETTINGS = "create table "
            + TABLE_CALL_RECORDING_SETTINGS + "( "
            + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_AUTOMATIC_CALL_RECORDING + " integer DEFAULT 0, "
            + COLUMN_CALL_RECORDING_NOTIFICATION + " integer DEFAULT 0, "
            + COLUMN_RECORDS_FROM + " integer DEFAULT 0, "
            + COLUMN_UNKNOWN_NUMBERS + " integer DEFAULT 0, "
            + COLUMN_IS_FIRST + " integer DEFAULT 1);";

    private static final String DATABASE_CREATE_CONTACTS = "create table "
            + TABLE_CALL_RECORDING_CONTACTS + "( "
            + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_NUMBER + " text not null, "
            + COLUMN_NAME + " text not null);";

    public CallRecordingContacts(Context context){
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DATABASE_CREATE_CONTACTS);
        db.execSQL(DATABASE_CREATE_RECORDING_SETTINGS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(newVersion > 2){
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CALL_RECORDING_CONTACTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CALL_RECORDING_SETTINGS);
            onCreate(db);
        }
    }
}
