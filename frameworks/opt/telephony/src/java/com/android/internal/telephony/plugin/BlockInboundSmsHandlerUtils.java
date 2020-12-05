package com.android.internal.telephony.plugin;

import java.util.Date;

import android.app.Activity;

import com.android.internal.telephony.InboundSmsTracker;
import com.android.internal.telephony.InboundSmsHandler;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import com.android.internal.R;
import android.util.Log;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.provider.Telephony.Sms.Intents;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManagerEx;
import android.text.TextUtils;

public class BlockInboundSmsHandlerUtils {

    private static final String LOGTAG = "BlockInboundSmsHandlerUtils";
    static BlockInboundSmsHandlerUtils sInstance;
    private Context mContext;
    private TelephonyManagerEx mTelephonyMgrEx;

    public static BlockInboundSmsHandlerUtils getInstance(Context context) {
        if (sInstance != null) return sInstance;
        Log.d(LOGTAG, "BlockInboundSmsHandlerUtils getInstance");
        sInstance = new BlockInboundSmsHandlerUtils(context);

        return sInstance;

    }

    public BlockInboundSmsHandlerUtils(Context context) {
        mContext = context;
        mTelephonyMgrEx = TelephonyManagerEx.from(context);
    }

    public void blockSms(String address,Intent intent) {
        if (mTelephonyMgrEx != null
                && mTelephonyMgrEx.isCallFireWallInstalled()
                && mTelephonyMgrEx.checkIsBlockSMSNumber(mContext, address)) {
            SmsMessage[] messages = Intents.getMessagesFromIntent(intent);
            SmsMessage sms = messages[0];
            StringBuffer bodyBuffer = new StringBuffer();
            int count = messages.length;
            for (int i = 0; i < count; i++) {
                sms = messages[i];
                if (sms.mWrappedSmsMessage != null) {
                    bodyBuffer.append(sms.getDisplayMessageBody());
                }
            }
            String body = bodyBuffer.toString();
            mTelephonyMgrEx.putToSmsBlackList(mContext, address, body, (new Date()).getTime());
        }
    }

    public void blockMms(String address, String contentField) {
        if (mTelephonyMgrEx != null
                && mTelephonyMgrEx.isCallFireWallInstalled()
                && mTelephonyMgrEx.checkIsBlockSMSNumber(mContext, address)
                && !mTelephonyMgrEx.isRedundantWapPushMessage(mContext, address, contentField)) {
            mTelephonyMgrEx.putToSmsBlackList(mContext,
                    address,
                    contentField,
                    (new Date()).getTime());
        }
    }
}
