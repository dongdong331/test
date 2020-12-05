package com.android.server.power.sprdpower;

import android.content.Context;
import android.content.ContentValues;
import android.content.res.AssetManager;


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.SQLException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;

import android.util.ArrayMap;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;

import java.io.IOException;
import java.io.InputStream;

/**
 * To load Power Preset Data Base
 */

public class PowerDataBaseControl {

    private static String TAG="PowerDataBaseControl";

    private static final String[] APP_INFOS_DB_COLUMNS;
    private static final String APP_INFOS_DB_TABLE_NAME = "general";
    private static String DB_FILE_NAME = "power_info.db";
    private static String assetsdbName = "/system/etc/power_info.db";

    public static final int UNKNOWN = -1;
    public static final int SYSTEM = 0;
    public static final int GAME = 1;
    public static final int MUSIC = 2;
    public static final int MESSAGE = 3;
    public static final int SOCIAL = 4;
    public static final int NEWS = 5;
    public static final int MEDIA = 6;
    public static final int READER = 7;
    public static final int NET = 8;
    public static final int MAP = 9;
    public static final int MONEY = 10;
    public static final int STUDY = 11;
    public static final int SHOPPING = 12;
    public static final int SPORTS = 13; // such as com.codoon.gps
    public static final int LIFESERVICE = 14; // such as baiduwaimai
    public static final int TRAVEL = 15; // such as xiecheng
    public static final int TRAFFIC = 16; // such as didi / mobike
    public static final int OTHER = 17;
    public static final int SPORT = 18; // such as keep


    private SQLiteDatabase mSqlinfoDb;
    private Context mContext;


    public static String TAG_SOCIAL;
    public static String TAG_NEWS;
    public static String TAG_DALYTOOLS;
    public static String TAG_ERROR;
    public static String TAG_GAME;
    public static String TAG_MUSIC;
    public static String TAG_MEDIA;
    public static String TAG_MESSAGE;
    public static String TAG_OTHER;
    public static String TAG_READER;
    public static String TAG_SYSTEM;
    public static String TAG_SHOPPING;
    public static String TAG_SPORTS;
    public static String TAG_LIFESERVICE;
    public static String TAG_TRAVEL;
    public static String TAG_TRAFFIC;


    static {
        APP_INFOS_DB_COLUMNS = new String[] { "pkgname", "category" };
        TAG_GAME = "GAMES";
        TAG_SYSTEM = "SYSTEM";
        TAG_SHOPPING = "SHOPPING";
        TAG_MESSAGE = "MESSAGE";
        TAG_DALYTOOLS = "DALYTOOLS";
        TAG_READER = "READER";
        TAG_MEDIA = "MEDIA";
        TAG_MUSIC = "MUSIC";
        TAG_OTHER = "OTHER";
        TAG_ERROR = "ERROR";
        TAG_NEWS = "NEWS";
        TAG_SOCIAL = "SOCIAL";
        TAG_SPORTS = "SPORTS";
        TAG_LIFESERVICE = "LIFESERVICE";
        TAG_TRAVEL = "TRAVEL";
        TAG_TRAFFIC = "TRAFFIC";
    }


    public PowerDataBaseControl(Context context) {
        mContext = context;
    }

    private String getTypeNameById(int id) {
        switch (id) {
            default:
                return TAG_OTHER;
            case GAME:
                return TAG_GAME;
            case SYSTEM:
                return TAG_SYSTEM;
            case SHOPPING:
                return TAG_SHOPPING;
            case MESSAGE:
                return TAG_MESSAGE;
            case SOCIAL:
                return TAG_SOCIAL;
            case MUSIC:
                return TAG_MUSIC;
            case MEDIA:
                return TAG_MEDIA;
            case NEWS:
                return TAG_NEWS;
            case READER:
                return TAG_READER;
            case SPORTS:
                return TAG_SPORTS;
            case LIFESERVICE:
                return TAG_LIFESERVICE;
            case TRAVEL:
                return TAG_TRAVEL;
            case TRAFFIC:
                return TAG_TRAFFIC;
            case STUDY:
            case NET:
            case MAP:
            case MONEY:
        }
        return TAG_DALYTOOLS;
    }

    private Cursor queryDatabase(String tableName, String[] selectColumns,
        String selection, String[] selectionArgs, String groupBy) {
        if (mSqlinfoDb == null) {
            Log.e(TAG, "database didn't open! ");
            return null;
        }
        while (true) {
            try {
                Cursor localCursor = mSqlinfoDb.query(tableName, selectColumns, selection, selectionArgs, null, null, null, null);
                return localCursor;
            } catch (Exception localException) {
                Log.e(TAG, "queryDatabase ERROR = " + localException.getMessage());
                return null;
            }
        }
    }

    public boolean checkInfoDB() {
        FileOutputStream localFileOutputStream;
        FileInputStream localInputStream;

        File file = mContext.getDatabasePath(DB_FILE_NAME);

        if (file.exists()) {
            return true;
        }

        String path = file.getPath();
        Log.d(TAG, "Output data base:" + path);

        try {
            localInputStream = new FileInputStream(assetsdbName);
            localFileOutputStream = new FileOutputStream(file);
        } catch (Exception e) {
            Log.e(TAG, "Exception while copyDataBase");
            e.printStackTrace();
            return false;
        }

        // copy DataBase
        while (true) {
            byte[] arrayOfByte;
            int count;
            try
            {
                arrayOfByte = new byte[8192];
                count = localInputStream.read(arrayOfByte);
                if (count <= 0) {
                  localInputStream.close();
                  localFileOutputStream.close();
                  Log.v(TAG, "copyDataBase = true");
                  return true;
                }
            } catch (IOException localIOException){
                Log.e(TAG, "IOException while copyDataBase");
                localIOException.printStackTrace();
                return false;
            }

            try {
              localFileOutputStream.write(arrayOfByte, 0, count);
              localFileOutputStream.flush();
            } catch (IOException localIOException) {
                Log.e(TAG, "IOException while copyDataBase");
                localIOException.printStackTrace();
                return false;
            }
        }

    }

    public void closeDB() {
        if (mSqlinfoDb != null)
          mSqlinfoDb.close();
        mSqlinfoDb = null;
    }

    public boolean openDB() {
        try
        {
            File file = mContext.getDatabasePath(DB_FILE_NAME);
            String path = file.getPath();

            mSqlinfoDb = SQLiteDatabase.openDatabase(path, null, 16);
            if (mSqlinfoDb != null)
                return true;
        }  catch (Exception localException) {
            Log.e(TAG, "openDatabase ERROR = " + localException.getMessage());
        }
        return false;
    }

    public Cursor queryDB(String pkgName) {
        if (pkgName == null)
            return queryDatabase("general", APP_INFOS_DB_COLUMNS, null, null, null);
        else
            return queryDatabase("general", APP_INFOS_DB_COLUMNS, "pkgname ='" + pkgName + "'", null, null);
    }

    public Cursor queryDB(int category) {
        return queryDatabase("general", APP_INFOS_DB_COLUMNS, "category ='" + category + "'", null, null);
    }

    public ArrayMap<String, Integer> queryAll() {
        ArrayMap<String, Integer> retValues = new ArrayMap<String, Integer>();
        Cursor localCursor = queryDB(null);
        if (localCursor == null) {
            Log.e(TAG, "db_cursor = null!");
            return retValues;
        }

        try {
            if (localCursor.moveToFirst()) {
                int type = localCursor.getInt(localCursor.getColumnIndex(APP_INFOS_DB_COLUMNS[1]));
                String name = localCursor.getString(localCursor.getColumnIndex(APP_INFOS_DB_COLUMNS[0]));
                Log.d(TAG, "apk=" + name + " type=" + type);
                retValues.put(name, type);
                while (localCursor.moveToNext()) {
                    type = localCursor.getInt(localCursor.getColumnIndex(APP_INFOS_DB_COLUMNS[1]));
                    name = localCursor.getString(localCursor.getColumnIndex(APP_INFOS_DB_COLUMNS[0]));
                    Log.d(TAG, "apk=" + name + " type=" + type);
                    retValues.put(name, type);
                }
            }
        } finally {
            localCursor.close();
        }
        return retValues;
    }


    public String queryInfo(String pkgName) {
        Cursor localCursor = queryDB(pkgName);
        if (localCursor == null) {
            Log.d(TAG, "db_cursor = null!");
            return TAG_ERROR;
        }
        if (localCursor.moveToFirst()) {
            String type = getTypeNameById(localCursor.getInt(localCursor.getColumnIndex(APP_INFOS_DB_COLUMNS[1])));

            localCursor.close();
            return type;
        }

        int i = pkgName.lastIndexOf('.');
        String str = null;
        if (i > 0) str = pkgName.substring(0, i);
        if (str != null) {
            if (pkgName.contains("com.android"))
                return TAG_SYSTEM;
        }

        return TAG_OTHER;
    }

}
