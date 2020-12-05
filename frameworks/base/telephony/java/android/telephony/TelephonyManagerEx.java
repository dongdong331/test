
package android.telephony;

import com.android.internal.telephony.ITelephonyEx;

import android.annotation.RequiresPermission;
import android.app.ActivityThread;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ComponentName;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.CellLocation;

import android.util.Log;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteFullException;
import android.net.Uri;
import android.os.ServiceManager;
import android.provider.BaseColumns;
import android.provider.BlockedNumberContract;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.ITelephonyEx;
import java.util.Date;

/**
 * Extended Telephony API for Unisoc system app
 * @hide
 */

public class TelephonyManagerEx {

    private static final String TAG = "TelephonyManagerEx";

    private final Context mContext;
    /* UNISOC: Bring for CallFireWall. @{ */
    private final int mSubId;
    private static final String BLACKLIST_PACKAGE_NAME = "com.sprd.firewall";
    private static final String BLACKLIST_ACTION_NAME =
            "com.sprd.firewall.ui.CallFireWallActivity";
    private static final String BLACKLIST_NUMBER_URI =
            "content://com.android.blockednumber/blocked";
    private static final String BLACKLIST_CALL_RECORD_URI =
            "content://com.sprd.providers.block/block_recorded";
    private static final String BLACKLIST_SMS_RECORD_URI =
            "content://com.sprd.providers.block/sms_block_recorded";
    /** @hide */
    public static final int SMS_SHIFT = 0;
    /** @hide */
    public static final int SMS_SELECT = 1 << SMS_SHIFT;
    private static final int CALL_SHIFT = 1;
    private static final int CALL_SELECT = 1 << CALL_SHIFT;

    private TelephonyScanManager mTelephonyScanManager;

    private static final class CallBlockRecorder implements BaseColumns {
        public static final Uri CONTENT_URI = Uri
                .parse(BLACKLIST_CALL_RECORD_URI);

        public static final String NUMBER_VALUE = "number_value";
        public static final String CALL_TYPE = "call_type";
        public static final String BLOCK_DATE = "block_date";
        public static final String NAME = "name";
    }

    private static final class SmsBlockRecorder implements BaseColumns {
        public static final Uri CONTENT_URI = Uri
                .parse(BLACKLIST_SMS_RECORD_URI);

        public static final String NUMBER_VALUE = "number_value";
        public static final String BLOCK_SMS_CONTENT = "sms_content";
        public static final String BLOCK_DATE = "block_date";
        public static final String NAME = "name";
    }
    /* @} */

    /** @hide */
    public TelephonyManagerEx(Context context) {
      this(context, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
    }

    /** @hide */
    public TelephonyManagerEx(Context context, int subId) {

        mSubId = subId;
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
            mContext = appContext;
        } else {
            mContext = context;
        }
    }

    /** {@hide} */
    public static TelephonyManagerEx from(Context context) {
        return (TelephonyManagerEx) context.getSystemService("phone_ex");
    }

    private ITelephonyEx getITelephonyEx() {
        return ITelephonyEx.Stub.asInterface(ServiceManager.getService("phone_ex"));
    }

    /**
     * Returns the current location of the device according to the giving slotId.
     *<p>
     * If there is only one radio in the device and that radio has an LTE connection,
     * this method will return null. The implementation must not to try add LTE
     * identifiers into the existing cdma/gsm classes.
     *<p>
     * In the future this call will be deprecated.
     *<p>
     * @return Current location of the device or null if not available.
     *
     * <p>Requires Permission:
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_COARSE_LOCATION} or
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION}.
     *  @hide
     */
    public CellLocation getCellLocationForPhone(int phoneId) {
        try {
            Bundle bundle = getITelephonyEx().getCellLocationForPhone(phoneId,mContext.getOpPackageName());
            if (bundle.isEmpty())
                return null;
            CellLocation cl = CellLocation.newFromBundle(bundle);
            if (cl.isEmpty())
                return null;
            return cl;
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /* UNISOC: Add for bug693265, AndroidP porting for USIM/SIM phonebook @{ */
    public boolean getIccFdnEnabled(int subId) {
        try {
            return getITelephonyEx().getIccFdnEnabledForSubscriber(subId);
        } catch (RemoteException ex) {
            // Assume no ICC card if remote exception which shouldn't happen
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
        }
        return false;
    }
    /* @} */
    /**
     * UNISOC: [Bug602746]
     * Returns the PNN home name.
     * @hide
     */
    public String getPnnHomeName(int subId) {
        try {
            return getITelephonyEx().getPnnHomeName(subId);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException calling getPnnHomeName", ex);
        } catch (NullPointerException ex) {
            Log.e(TAG, "NullPointerException calling getPnnHomeName", ex);
        }
        return null;
    }

    /**
     * UNISOC Add for bug849436.
     * @param phoneId for which network type is returned
     * @return the NETWORK_TYPE_xxxx for current data connection.
     *
     * @hide
     */
    public int getNetworkTypeForPhone(int phoneId) {
        try {
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony != null) {
                return telephony.getNetworkTypeForPhone(phoneId, mContext.getOpPackageName());
            } else {
                // This can happen when the ITelephonyEx interface is not up yet.
                return TelephonyManager.NETWORK_TYPE_UNKNOWN;
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
    }

    /**
     * Set the line 1 phone number string and its alphatag for the current ICCID
     * for display purpose only, for example, displayed in Phone Status. It won't
     * change the actual MSISDN/MDN. To unset alphatag or number, pass in a null
     * value.
     *
     * @param subId the subscriber that the alphatag and dialing number belongs to.
     * @param alphaTag alpha-tagging of the dailing nubmer
     * @param number The dialing number
     * @return true if the operation was executed correctly.
     * @hide
     */
    public boolean setLine1NumberForDisplayForSubscriberEx(int subId, String alphaTag, String number) {
        try {
            return getITelephonyEx().setLine1NumberForDisplayForSubscriberEx(subId, alphaTag, number);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException calling ITelephony#setLine1NumberForDisplayForSubscriberEx", ex);
        } catch (NullPointerException ex) {
            Log.e(TAG, "NullPointerException calling ITelephony#setLine1NumberForDisplayForSubscriberEx", ex);
        }
        return false;
    }

    /* UNISOC: Bringup for CallFireWall. @{ */
    /**
     * Create a new TelephonyManagerEx object pinned to the given subscription ID.
     *
     * @return a TelephonyManagerEx that uses the given subId for all calls.
     */
    /** @hide */
    public TelephonyManagerEx createForSubscriptionId(int subId) {
        // Don't reuse any TelephonyManager objects.
        return new TelephonyManagerEx(mContext, subId);
    }

    /**
     * Return an appropriate subscription ID for any situation.
     *
     * If this object has been created with {@link #createForSubscriptionId}, then the provided
     * subId is returned. Otherwise, the default subId will be returned.
     */
    private int getSubId() {
        if (mSubId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
            return getDefaultSubscription();
        }
        return mSubId;
    }

    /**
     * Returns Default subscription.
     */
    private static int getDefaultSubscription() {
        return SubscriptionManager.getDefaultSubscriptionId();
    }

    private String getOpPackageName() {
        // For legacy reasons the TelephonyManager has API for getting
        // a static instance with no context set preventing us from
        // getting the op package name. As a workaround we do a best
        // effort and get the context from the current activity thread.
        if (mContext != null) {
            return mContext.getOpPackageName();
        }
        return ActivityThread.currentOpPackageName();
    }

    /** @hide */
    public Intent createManageBlockedNumbersIntent() {
        Intent intent = new Intent();
        ComponentName com = new ComponentName(BLACKLIST_PACKAGE_NAME,BLACKLIST_ACTION_NAME);
        intent.setComponent(com);
        return intent;
    }

    /** @hide */
    public static final Uri BlOCK_NUMBER_CONTENT_URI = Uri
            .parse(BLACKLIST_NUMBER_URI);

    /** @hide */
    public boolean isCallFireWallInstalled() {
        boolean installed = false;
        try {
            if (mContext != null) {
                mContext.getPackageManager().getPackageInfo(
                        BLACKLIST_PACKAGE_NAME, PackageManager.GET_ACTIVITIES);
                installed = true;
            }
        } catch (PackageManager.NameNotFoundException e) {
        }
        return installed;
    }

    /** @hide */
    public boolean checkIsBlackNumber(Context context, String str) {
        Log.d(TAG, "checkIsBlackCallNumber");
        ContentResolver cr = context.getContentResolver();
        String originalNumber;
        int block_type;
        String[] columns = new String[] {
                BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                BlockedNumberContract.BlockedNumbers.BLOCK_TYPE
        };

        Cursor cursor = cr.query(BlOCK_NUMBER_CONTENT_URI, columns, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    originalNumber = cursor.getString(cursor.getColumnIndex(
                            BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER));
                    block_type = cursor.getInt(cursor.getColumnIndex(
                            BlockedNumberContract.BlockedNumbers.BLOCK_TYPE));
                    if (PhoneNumberUtils.compare(str.trim(), originalNumber.trim())) {
                        return true;
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            // process exception
        } finally {
            if (cursor != null)
                cursor.close();
            else
                Log.i(TAG, "black number cursor == null");
        }
        return false;
    }

    /** @hide */
    public boolean checkIsBlockCallNumber(Context context, String number) {
        Log.d(TAG, "checkIsBlackCallNumber");
        ContentResolver cr = context.getContentResolver();
        if (cr == null) {
            return false;
        }
        int block_type;
        String number_value;
        String[] columns = new String[] {
                BlockedNumberContract.BlockedNumbers.BLOCK_TYPE,
                BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER
        };
        Cursor cursor = cr.query(BlockedNumberContract.BlockedNumbers.CONTENT_URI, columns,
                null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    block_type = cursor.getInt(cursor
                            .getColumnIndex(BlockedNumberContract.BlockedNumbers.BLOCK_TYPE));
                    number_value = cursor.getString(cursor.
                            getColumnIndex(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER));
                    if (PhoneNumberUtils.compare(number, number_value)) {
                        if ((CALL_SELECT & block_type) == CALL_SELECT) {
                            return true;
                        }
                    }
                } while (cursor.moveToNext());
            } else {
                Log.e(TAG, "Query black list cursor is null.");
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }

    /** @hide */
    public boolean checkIsBlockSMSNumber(Context context, String number) {
        Log.d(TAG, "checkIsBlockSMSNumber");
        ContentResolver cr = context.getContentResolver();
        if (cr == null) {
            return false;
        }
        int block_type;
        String number_value;
        String[] columns = new String[] {BlockedNumberContract.BlockedNumbers.BLOCK_TYPE,
                BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER};
        Cursor cursor = cr.query(BlockedNumberContract.BlockedNumbers.CONTENT_URI, columns,
                null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    block_type = cursor.getInt(cursor.
                            getColumnIndex(BlockedNumberContract.BlockedNumbers.BLOCK_TYPE));
                    number_value = cursor.getString(cursor.
                            getColumnIndex(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER));
                    if (PhoneNumberUtils.compare(number, number_value)) {
                        if ((SMS_SELECT & block_type) == SMS_SELECT) {
                            return true;
                        }
                    }
                } while (cursor.moveToNext());
            } else {
                Log.e(TAG, "Query black list cursor is null.");
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }

    /** @hide */
    public boolean putToBlockList(Context context, String phoneNumber, int Blocktype,
                                  String name) {
        ContentResolver cr = context.getContentResolver();
        String normalizedNumber = PhoneNumberUtils.normalizeNumber(phoneNumber);
        ContentValues values = new ContentValues();
        if (values != null) {
            try {
                values.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, phoneNumber);
                values.put(BlockedNumberContract.BlockedNumbers.MIN_MATCH,
                        PhoneNumberUtils.toCallerIDMinMatch(normalizedNumber));
                values.put(BlockedNumberContract.BlockedNumbers.BLOCK_TYPE, Blocktype);
                values.put(BlockedNumberContract.BlockedNumbers.NAME, name);
            } catch (Exception e) {
                Log.e(TAG, "black number putToBlockList:exception");
            }
        }
        Uri result = null;
        try {
            result = cr.insert(BlOCK_NUMBER_CONTENT_URI, values);
        } catch (Exception e) {
            Log.e(TAG, "black number putToBlockList: provider == null");
        }
        return result != null ? true : false;
    }

    /** @hide */
    public void putToBlockListCall(Context context, String number) {
        if (!isCallFireWallInstalled()) {
            Log.e(TAG, "sprd callfirewall is not installed, no call block list");
            return;
        }
        ContentResolver cr = context.getContentResolver();
        if (cr == null) {
            return;
        }

        long date = (new Date()).getTime();
        String name = "";
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number));
        Cursor cursor = cr.query(uri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                if (index != -1) {
                    name = cursor.getString(index);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        try {
            ContentValues values = new ContentValues();
            if (values != null) {
                values.put(CallBlockRecorder.NUMBER_VALUE, number);
                values.put(CallBlockRecorder.BLOCK_DATE, Long.valueOf(date));
                values.put(CallBlockRecorder.NAME, name);
                cr.insert(CallBlockRecorder.CONTENT_URI, values);
            }
        } catch (SQLiteFullException e) {
            e.printStackTrace();
        } catch (SQLiteDiskIOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** @hide */
    public Uri putToSmsBlackList(Context context, String phoneNumber, String message,
                                 long date) {
        if (!isCallFireWallInstalled()) {
            Log.e(TAG, "sprd callfirewall is not installed, no sms block list");
            return null;
        }
        ContentResolver cr = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(SmsBlockRecorder.NUMBER_VALUE, phoneNumber);
        values.put(SmsBlockRecorder.BLOCK_SMS_CONTENT, message);
        values.put(SmsBlockRecorder.BLOCK_DATE, date);

        return cr.insert(SmsBlockRecorder.CONTENT_URI, values);
    }

    /** @hide */
    public boolean isRedundantWapPushMessage(
            Context context, String address, String contentField) {
        boolean retValue = false;
        ContentResolver cr = context.getContentResolver();
        if (cr != null) {
            //TODO: sms records should be distinguished from type sms and wap push.
            String[] columns = new String[] {
                    SmsBlockRecorder.NUMBER_VALUE,
                    SmsBlockRecorder.BLOCK_SMS_CONTENT
            };

            String number, field;
            Cursor cursor = cr.query(SmsBlockRecorder.CONTENT_URI, columns, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        number = cursor.getString(
                                cursor.getColumnIndex(SmsBlockRecorder.NUMBER_VALUE));
                        if (PhoneNumberUtils.compare(address, number)) {
                            field = cursor.getString(
                                    cursor.getColumnIndex(SmsBlockRecorder.BLOCK_SMS_CONTENT));
                            if (TextUtils.equals(field, contentField)) {
                                Log.d(TAG, "found redundant wap push record.");
                                retValue = true;
                                break;
                            }
                        }
                    } while (cursor.moveToNext());
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        return retValue;
    }
    /* @} */

    private static final String SIM_ENABLED_PROPERTY = "persist.vendor.radio.sim_enabled";
    /**
     * Enable or disable SIM card just like hot-plugging. If SIM is disabled, all informations
     * related to this SIM card will be removed. SIM card will be reloaded if enable it again.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#MODIFY_PHONE_STATE MODIFY_PHONE_STATE}
     *
     * @hide
     * @param phoneId
     * @param turnOn whether disable or enable SIM
     */

    public void setSimEnabled(int phoneId, boolean turnOn) {
        try {
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony != null) {
                telephony.setSimEnabled(phoneId, turnOn);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephonyEx#setSimEnabled", e);
        }
    }

    /**
     * Returns whether SIM is enabled which can be changed by user through SIM settings.
     * @hide
     * @param phoneId
     */
    public boolean isSimEnabled(int phoneId) {
        return !"0".equals(
                TelephonyManager.getTelephonyProperty(phoneId, SIM_ENABLED_PROPERTY, "1"));
    }

    /**
     *Add cache of original SPN value.
     * @hide
     */
    public String getOriginalServiceProviderName(int phoneId) {
        try {
            return getITelephonyEx().getOriginalServiceProviderName(phoneId);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException getOriginalServiceProviderName", ex);
        } catch (NullPointerException ex) {
            Log.e(TAG, "NullPointerException getOriginalServiceProviderName", ex);
        }
        return null;
    }

    /**
     * UNISOC add to extend subId for requestNetworkScan
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public NetworkScan requestNetworkScan(
        NetworkScanRequest request, TelephonyScanManager.NetworkScanCallback callback, int subId) {
        synchronized (this) {
            if (mTelephonyScanManager == null) {
                mTelephonyScanManager = new TelephonyScanManager();
            }
        }
        return mTelephonyScanManager.requestNetworkScan(subId, request,
                AsyncTask.SERIAL_EXECUTOR, callback);
    }

    // UNISOC: add for bug978689
    public boolean isImsRegistered(){
          try {
              ITelephonyEx telephony = getITelephonyEx();
              if (telephony != null) {
                  return telephony.isImsRegistered();
              }
          } catch (RemoteException e) {
              Log.e(TAG, "Error calling ITelephonyEx#isImsRegistered", e);
          }
          return false;
    }
    /**
     * Return whether primary card can be hot-switch manually.
     */
    public boolean isPrimaryCardSwitchAllowed() {
        try {
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony != null) {
                return getITelephonyEx().isPrimaryCardSwitchAllowed();
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException calling isPrimaryCardSwitchAllowed", ex);
        } catch (NullPointerException ex) {
            Log.e(TAG, "NullPointerException calling isPrimaryCardSwitchAllowed", ex);
        }
        return true;
    }

    /**
     * Return whether primary card can be hot-switch manually.
     */
    public boolean isDisableSimAllowed(int phoneId) {
        try {
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony != null) {
                return getITelephonyEx().isDisableSimAllowed(phoneId);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException calling isDisableSimAllowed", ex);
        } catch (NullPointerException ex) {
            Log.e(TAG, "NullPointerException calling isDisableSimAllowed", ex);
        }
        return true;
    }
}
