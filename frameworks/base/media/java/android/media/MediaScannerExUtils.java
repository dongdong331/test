package android.media;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.EnvironmentEx;
import android.os.RemoteException;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.Settings;
import android.provider.SettingsEx;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;

import com.android.internal.telephony.TelephonyProperties;

/**
 * @hide
 */
public class MediaScannerExUtils {
    private static final String TAG = "MediaScannerExUtils";

    private MediaScanner mMediaScanner;

    /* SPRD: add for OTA upgrade */
    private static final String EMULATE_EXTERNAL_PATH = "/storage/sdcard0";

    public MediaScannerExUtils(MediaScanner mediaScanner) {
        mMediaScanner = mediaScanner;
    }

    /**
     * SPRD : add for multicard support. @{
     */
    protected static String getSetting(String defaultSetting, int phoneId) {
        if (isMultiSimEnabledEx()) {
            return defaultSetting + phoneId;
        } else {
            return defaultSetting;
        }
    }

    protected static boolean isMultiSimEnabledEx() {
        String multiSimConfig =
                SystemProperties.get(TelephonyProperties.PROPERTY_MULTI_SIM_CONFIG);
        return (multiSimConfig.equals("dsds") || multiSimConfig.equals("dsda") || multiSimConfig
                .equals("tsts"));
    }

    protected boolean isNeedScanRingtone(MediaScanner.FileEntry entry) {
        for (int i = 0; i < mMediaScanner.mPhoneCount; i++) {
            if (!mMediaScanner.mDefaultRingtoneSet[i] &&
                    mMediaScanner.mClient.doesPathHaveFilename(entry.mPath, mMediaScanner.mDefaultRingtoneFilenames[i])) {
                return true;
            }
        }
        return false;
    }

    protected void setDefaultRingtoneFileNamesEx() {
        mMediaScanner.mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
        mMediaScanner.mDefaultRingtoneSet = new boolean[mMediaScanner.mPhoneCount];
        mMediaScanner.mDefaultRingtoneFilenames = new String[mMediaScanner.mPhoneCount];
        mMediaScanner.mDefaultMessageSet = new boolean[mMediaScanner.mPhoneCount];
        mMediaScanner.mDefaultMessageFilename = new String[mMediaScanner.mPhoneCount];
        for (int i = 0; i < mMediaScanner.mPhoneCount; i++) {
            mMediaScanner.mDefaultRingtoneSet[i] = false;
            mMediaScanner.mDefaultRingtoneFilenames[i] = "";
            mMediaScanner.mDefaultMessageSet[i] = false;
            mMediaScanner.mDefaultMessageFilename[i] = "";
        }

        for (int i = 0; i < mMediaScanner.mPhoneCount; i++) {
            mMediaScanner.mDefaultRingtoneFilenames[i] = SystemProperties.get(MediaScanner.DEFAULT_RINGTONE_PROPERTY_PREFIX
                    + getSetting(Settings.System.RINGTONE, i));
            if ("".equals(mMediaScanner.mDefaultRingtoneFilenames[i])) {
                mMediaScanner.mDefaultRingtoneFilenames[i] = SystemProperties.get(MediaScanner.DEFAULT_RINGTONE_PROPERTY_PREFIX
                        + Settings.System.RINGTONE);
            }
            mMediaScanner.mDefaultMessageFilename[i] = SystemProperties.get(MediaScanner.DEFAULT_RINGTONE_PROPERTY_PREFIX
                    + getSetting(SettingsEx.SystemEx.DEFAULT_MESSAGE, i));
            if ("".equals(mMediaScanner.mDefaultMessageFilename[i])) {
                mMediaScanner.mDefaultMessageFilename[i] = SystemProperties.get(MediaScanner.DEFAULT_RINGTONE_PROPERTY_PREFIX
                        + SettingsEx.SystemEx.DEFAULT_MESSAGE);
            }
        }
    }

    protected void setRingtoneIfNotSetForMultiSim(Context context, String settingName, Uri uri, long rowId, int phoneId) {
        final String ringtoneName = getSetting(settingName, phoneId);
        if (mMediaScanner.wasRingtoneAlreadySet(ringtoneName)) {
            return;
        }

        ContentResolver cr = context.getContentResolver();
        String existingSettingValue = Settings.System.getString(cr, ringtoneName);
        if (TextUtils.isEmpty(existingSettingValue)) {
            final Uri settingUri = Settings.System.getUriFor(settingName);
            final Uri ringtoneUri = ContentUris.withAppendedId(uri, rowId);
            RingtoneManagerEx.setActualDefaultRingtoneUri(context,
                    RingtoneManager.getDefaultType(settingUri), ringtoneUri, phoneId);
            Log.d(TAG, "ringtoneName = " + ringtoneName + ", uri = " + uri.toString() + ",rowId = " + rowId);
        }
        Settings.System.putInt(cr, mMediaScanner.settingSetIndicatorName(ringtoneName), 1);
    }

    protected static void saveUriToSettingsDB(ContentResolver contentResolver, String settingName, Uri tableUri, long rowId) {
        Settings.System.putString(contentResolver, settingName,
                ContentUris.withAppendedId(tableUri, rowId).toString());
    }
    /* @} */

    /**
     * SPRD : add for OTA upgrade. @{
     */
    protected static void updateNewPathValues(Context context, ContentValues values, String pathInDatabase) {
        if (pathInDatabase != null && pathInDatabase.startsWith(getOldPath())) {
            String externalPath = EnvironmentEx.getExternalStoragePath().getPath();
            String newPath = pathInDatabase.replace(getOldPath(), externalPath);
            values.put(MediaStore.MediaColumns.DATA, newPath);
            computeBucketValues(newPath, values);
            /*
            int storageId = getStorageId(context, newPath);
            values.put(FileColumns.STORAGE_ID, storageId);
            */
        }
    }

    /*
    private static int getStorageId(Context context, String path) {
        final StorageManager storage = context.getSystemService(StorageManager.class);
        final StorageVolume vol = storage.getStorageVolume(new File(path));
        if (vol != null) {
            return vol.getStorageId();
        } else {
            Log.w(TAG, "Missing volume for " + path + "; assuming invalid");
            return StorageVolume.STORAGE_ID_INVALID;
        }
    }
    */

    private static void computeBucketValues(String data, ContentValues values) {
        File parentFile = new File(data).getParentFile();
        if (parentFile == null) {
            parentFile = new File("/");
        }

        // Lowercase the path for hashing. This avoids duplicate buckets if the
        // filepath case is changed externally.
        // Keep the original case for display.
        String path = parentFile.toString().toLowerCase();
        String name = parentFile.getName();

        // Note: the BUCKET_ID and BUCKET_DISPLAY_NAME attributes are spelled the
        // same for both images and video. However, for backwards-compatibility reasons
        // there is no common base class. We use the ImageColumns version here
        values.put(ImageColumns.BUCKET_ID, path.hashCode());
        values.put(ImageColumns.BUCKET_DISPLAY_NAME, name);
    }

    protected static String getOldPath() {
        String path = EMULATE_EXTERNAL_PATH;
        return path;
    }
    /* @} */
}
