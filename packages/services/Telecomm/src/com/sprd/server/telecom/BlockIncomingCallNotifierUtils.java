
package com.sprd.server.telecom;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteFullException;

import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.TelephonyManagerEx;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.logging.AndroidConfig;
import com.android.server.telecom.Call;

import java.util.Date;

public class BlockIncomingCallNotifierUtils {

    private static final String LOGTAG = "BlockIncomingCallNotifierUtils";
    static BlockIncomingCallNotifierUtils sInstance;
    private static final int CALL_SHIFT = 1;
    private static final int CALL_SELECT = 1 << CALL_SHIFT;
    private Context mContext;
    public static final boolean DEBUG = "userdebug".equals(Build.TYPE) || "eng".equals(Build.TYPE);
    /* SPRD: Add block number notification for bug657761. @{ */
    private static final String EXTRA_BLOCK_NUMBER = "block_phone_number";
    private static final String ACTION_INCOMING_BLOCK_NUMBER = "action_incoming_block_number";
    /* @} */
    private TelephonyManagerEx mTelephonyMgrEx;

    public static BlockIncomingCallNotifierUtils getInstance(Context context) {
        if (sInstance != null) return sInstance;
        sInstance = new BlockIncomingCallNotifierUtils(context);
        return sInstance;
    }

    public BlockIncomingCallNotifierUtils(Context context) {
        mContext = context;
        mTelephonyMgrEx = TelephonyManagerEx.from(context);
    }

    public void blockCall(Call call) {
        //final String inComingNumber = connection.getAddress();
        final String inComingNumber = call.getHandle().getSchemeSpecificPart();
        // SPRD: add for new feature for import call log to blacklist
        if (mTelephonyMgrEx != null
                && mTelephonyMgrEx.isCallFireWallInstalled()
                && !TextUtils.isEmpty(inComingNumber)) {
            // SPRD: add for bug493481
            if (DEBUG) {
                Log.d(LOGTAG, "inComingNumber = " + inComingNumber);
            }
            mTelephonyMgrEx.putToBlockListCall(mContext, inComingNumber);
            /* SPRD: Add block number notification for bug657761. @{ */
            Intent intent = new Intent();
            intent.setAction(ACTION_INCOMING_BLOCK_NUMBER);
            intent.putExtra(EXTRA_BLOCK_NUMBER, inComingNumber);
            mContext.sendBroadcast(intent);
            /* @} */
        }
    }
}
