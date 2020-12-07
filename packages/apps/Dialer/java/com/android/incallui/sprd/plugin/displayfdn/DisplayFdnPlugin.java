package com.android.incallui.sprd.plugin.displayfdn;
import java.io.IOException;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;

import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManagerEx;

import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import com.android.incallui.incall.impl.InCallFragment;
import com.android.incallui.incall.protocol.PrimaryInfo;


public class DisplayFdnPlugin extends DisplayFdnHelper {

    private static final String TAG = "DisplayFdnPlugin";
    private Context context;

    /* SPRD Feature Porting: Show fdn list name in incallui feature. @{ */
    private static final int INVALID_SUBSCRIPTION_ID = -1;
    private static final String FDN_CONTENT_URI = "content://icc/fdn/subId/";
    private static final String[] FDN_SELECT_PROJECTION = new String[]{
            "name", "number"
    };
    private static final int FDN_NAME_COLUMN = 0;
    private static final int FDN_NUMBER_COLUMN = 1;
    private int subId = -1;
    private String fdnNumber;
    private PrimaryInfo mPrimaryInfo; // UNISOC: add for bug976698
    /* @} */

    public  DisplayFdnPlugin() {

    }

    public DisplayFdnPlugin(Context context) {
        this.context = context;
    }

    /* SPRD Feature Porting: Show fdn list name in incallui feature. @{ */
    @Override
    public boolean isSupportFdnListName(int subId) {
        TelephonyManagerEx telephonyManager = TelephonyManagerEx.from(context);
        return telephonyManager.getIccFdnEnabled(subId);
    }

    @Override
    public void setFDNListName(PrimaryInfo primaryInfo, InCallFragment incallFragment) {
        // UNISOC: add for bug976698
        mPrimaryInfo = primaryInfo;
        if (primaryInfo.nameIsNumber()) {
            fdnNumber = primaryInfo.name();
        } else {
            fdnNumber = primaryInfo.number();
        }
        if (!TextUtils.isEmpty(fdnNumber) && primaryInfo.subId() > INVALID_SUBSCRIPTION_ID) {
            GetFDNListNameAsyncTask getFdnListNameTask = new GetFDNListNameAsyncTask(primaryInfo,
                    incallFragment);
            getFdnListNameTask.execute();
        } else if (incallFragment != null) {
            incallFragment.setFdnName(primaryInfo);
        }
    }

    /* UNISOC: add for bug965735 @{ */
    @Override
    public String getFDNListName(String number, int subId) {
        String fdnListName = null;
        String compareNumber;
        // UNISOC: modify for bug969214
        String formatNumber = (number == null) ? " " : number.replace(" ", "");

        Cursor cursor = context.getContentResolver().query(Uri.parse(FDN_CONTENT_URI + subId),
                FDN_SELECT_PROJECTION, null, null, null);
        try {
            while (cursor != null && cursor.moveToNext()) {
                compareNumber = cursor.getString(FDN_NUMBER_COLUMN);

                if (compareNumber != null && compareNumber.equals(formatNumber)) {
                    fdnListName = cursor.getString(FDN_NAME_COLUMN);
                    break;
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return fdnListName;
    }
    /* @} */

    public String getFDNListName(String number) {
        String fdnListName = null;
        String compareNumber;
        // UNISOC: modify for bug969214
        String formatNumber = (number == null) ? " " : number.replace(" ", "");

        Cursor cursor = context.getContentResolver().query(Uri.parse(FDN_CONTENT_URI + subId),
                FDN_SELECT_PROJECTION, null, null, null);
        try {
            while (cursor != null && cursor.moveToNext()) {
                compareNumber = cursor.getString(FDN_NUMBER_COLUMN);

                if (compareNumber != null && compareNumber.equals(formatNumber)) {
                    fdnListName = cursor.getString(FDN_NAME_COLUMN);
                    break;
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return fdnListName;
    }

    private class GetFDNListNameAsyncTask extends AsyncTask<String, Void, String> {
        private PrimaryInfo primaryInfo;
        private InCallFragment incallFragment;

        public GetFDNListNameAsyncTask(PrimaryInfo primaryInfo, InCallFragment incallFragment) {
            this.primaryInfo = primaryInfo;
            this.incallFragment = incallFragment;
            subId = primaryInfo.subId();
        }

        protected String doInBackground(String[] params) {
            return getFDNListName(fdnNumber);
        };

        protected void onPostExecute(String result) {
            Log.d(TAG, "GetFDNListNameAsyncTask.onPostExecute." + result);
            // UNISOC: add for bug976698
            if (incallFragment != null && mPrimaryInfo == primaryInfo) {
                if (!TextUtils.isEmpty(result)) {
                    // UNISOC. add for bug962171
                    PrimaryInfo newPrimaryInfo = PrimaryInfo.builder()
                            .setNumber(primaryInfo.number())
                            .setName(result)
                            .setNameIsNumber(false)
                            .setLabel(primaryInfo.label())
                            .setLocation(primaryInfo.location())
                            .setPhoto(primaryInfo.photo())
                            .setPhotoType(primaryInfo.photoType())
                            .setIsSipCall(primaryInfo.isSipCall())
                            .setIsContactPhotoShown(primaryInfo.isContactPhotoShown())
                            .setIsWorkCall(primaryInfo.isWorkCall())
                            .setIsSpam(primaryInfo.isSpam())
                            .setIsVoiceMailNumber(primaryInfo.isVoiceMailNumber())
                            .setIsConference(primaryInfo.isConference())
                            .setIsLocalContact(primaryInfo.isLocalContact())
                            .setAnsweringDisconnectsOngoingCall(primaryInfo.answeringDisconnectsOngoingCall())
                            .setShouldShowLocation(primaryInfo.shouldShowLocation())
                            .setContactInfoLookupKey(primaryInfo.contactInfoLookupKey())
                            .setMultimediaData(primaryInfo.multimediaData())
                            .setShowInCallButtonGrid(true)
                            .setNumberPresentation(primaryInfo.numberPresentation())
                            .setSubId(primaryInfo.subId())
                            .build();
                    incallFragment.setFdnName(newPrimaryInfo);
                } else {
                    incallFragment.setFdnName(primaryInfo);
                }
            } else {
                Log.d(TAG, "incallFragment is null when onPostExecute or primaryInfo has been updated. ");
            }
        };
    }
    /* @} */
}

