/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.calldetails;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.v7.widget.RecyclerView;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.calllogutils.PhoneAccountUtils;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.precall.PreCall;
import com.android.dialer.sprd.util.IpDialingUtils;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.CallUtil;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.PermissionsUtil;

import java.util.List;

import android.support.annotation.Nullable;

import static android.Manifest.permission.READ_PHONE_STATE;

import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallInitiationType.Type;
import com.android.dialer.callintent.CallSpecificAppData;

/** ViewHolder for Header/Contact in {@link CallDetailsActivity}. */
public class CallOperationsViewHolder extends RecyclerView.ViewHolder
        implements OnClickListener {


  private final Context mContext;
  ImageView mIpCallIcon = null;
  ImageView mVideoCallIcon = null;
  ImageView mSmsIcon = null;
  ImageView mAddContactIcon = null;
  private DialerContact mContact;
  private static final String TAG = "CallOperationsViewHolder";
  public static final String SUB_ID_EXTRA =
          "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionId";

        /* SPRD: modify for bug901993 @{ */
    private static final String PHONE_PACKAGE_NAME = "com.android.dialer";
    private static final String IP_NUMBER_LIST_ACTIVITY =
            "com.android.dialer.app.ipdial.IpNumberListActivity";

  // SPRD: modify for bug709708、900405
  private boolean mIsVoiceMial = false;

  CallOperationsViewHolder(View container) {
    super(container);
    mContext = container.getContext();
    // SPRD: modify for bug709708、900405
    mIsVoiceMial = ((CallDetailsActivity) mContext).isVoiceMailForSelectedItem();

    mIpCallIcon = (ImageView) container.findViewById(R.id.ip_call_icon);
    mVideoCallIcon = (ImageView) container.findViewById(R.id.video_call_icon);
    mSmsIcon = (ImageView) container.findViewById(R.id.message_icon);
    mAddContactIcon = (ImageView) container.findViewById(R.id.add_contact_icon);

    //video call
    mIpCallIcon.setVisibility(View.GONE);
    mVideoCallIcon.setVisibility(View.GONE);
    mSmsIcon.setVisibility(View.GONE);
    mAddContactIcon.setVisibility(View.GONE);
    mVideoCallIcon.setOnClickListener(this);
    mIpCallIcon.setOnClickListener(this);
    mSmsIcon.setOnClickListener(this);
    mAddContactIcon.setOnClickListener(this);


  }


  /**
   * Populates the contact info fields based on the current contact information.
   * * SPRD: modify for bug894017 @{
   */
  void updateContactInfo(DialerContact contact) {
    boolean isUnknownCall = TextUtils.isEmpty(contact.getNumber());
    mContact = contact;
    //video call
    mVideoCallIcon.setTag(CallUtil.getVideoCallIntent(contact.getNumber(), ""));
    // SPRD: 895153 it still show VideoCallIcon when close the volte 4g
    if (CallUtil.isVideoEnabled(mContext) && !isUnknownCall) {
        mVideoCallIcon.setVisibility(View.VISIBLE);
    } else {
        mVideoCallIcon.setVisibility(View.GONE);
    }
    //send sms
    // SPRD: modify for bug709708、900405
    if (!isUnknownCall && !mIsVoiceMial) {
        mSmsIcon.setTag(new Intent(Intent.ACTION_SENDTO,
                Uri.fromParts("sms", contact.getNumber(), null)));
        mSmsIcon.setVisibility(View.VISIBLE);
    }
    // add contact
    // SPRD: modify for bug709708、900405
    if (!isUnknownCall && !mIsVoiceMial && TextUtils.isEmpty(contact.getNumberLabel())) {
      Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
      intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
      intent.putExtra(ContactsContract.Intents.Insert.PHONE, contact.getNumber());
      mAddContactIcon.setTag(intent);
      mAddContactIcon.setVisibility(View.VISIBLE);
    } else {
      mAddContactIcon.setVisibility(View.GONE);
    }
    /* SPRD: add for bug724835 @{ */
    /**UNISOC:970074,983956 cmcc,cucc show IP feature @{*/
    boolean shouldShowIP = mContext.getResources().getBoolean(com.android.internal.R.bool.ip_dial_enabled_bool);
    Log.d(TAG,"shouldShowIP:"+shouldShowIP);
    if (PermissionsUtil.hasPermission(mContext, READ_PHONE_STATE) && !isUnknownCall && shouldShowIP) {
      mIpCallIcon.setVisibility(View.VISIBLE);
    } else {
      mIpCallIcon.setVisibility(View.GONE);
    }
    /** @}*/
    /* @} */
  }
  /* @} */

  @Override
  public void onClick(View view) {
    Intent intent = (Intent) view.getTag();
    if (view.getId() == R.id.ip_call_icon) {
      /** SPRD: add for bug 892901 @{ */
        TelephonyManager telephonyManager = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);
        boolean isImsRegistered = telephonyManager.isImsRegistered();
        if (isAirplaneModeOn()
                && !PhoneNumberUtils.isEmergencyNumber(mContact.getNumber())
                && !isImsRegistered) {
            Toast.makeText(mContext,
                    com.android.dialer.app.R.string.dialog_make_call_airplane_mode_message,
                    Toast.LENGTH_LONG).show();
            return;
        }
      /** @} */
      /**SPRD: bug877517&708782 FEATURE_CALL_DETAIL_ACTIONS @{ */
        List<PhoneAccountHandle> subscriptionAccountHandles =
                PhoneAccountUtils.getSubscriptionPhoneAccounts(mContext);
        PhoneAccountHandle defaultPhoneAccountHandle = TelecomUtil
                .getDefaultOutgoingPhoneAccount(mContext, PhoneAccount.SCHEME_TEL);
        boolean hasUserSelectedDefault = subscriptionAccountHandles
                .contains(defaultPhoneAccountHandle);
        int activeSimCount = SubscriptionManager.from(mContext)
                .getActiveSubscriptionInfoCount();
        if (activeSimCount <= 0
                && !PhoneNumberUtils.isEmergencyNumber(mContact.getNumber())
                && !isImsRegistered) {
            Toast.makeText(mContext, R.string.no_available_sim, Toast.LENGTH_SHORT).show();
            return;
        }
        UserManager userManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        if (userManager != null && !userManager.isSystemUser()) {
            Toast.makeText(mContext, R.string.ip_dial_in_guest_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        if (DialerUtils.isPhoneInUse(mContext)) {
            intent = handleIpDial(mContext, DialerUtils.getCallingPhoneAccountHandle(mContext),
                    mContact.getNumber());
        } else if (subscriptionAccountHandles.size() <= 1 || hasUserSelectedDefault) {
            intent = handleIpDial(mContext, defaultPhoneAccountHandle, mContact.getNumber());
        } else {
            SelectPhoneAccountDialogFragment.SelectPhoneAccountListener ipDialCallback =
                    new HandleDialAccountSelectedCallback(mContact.getNumber(), false);
            DialerUtils.showSelectPhoneAccountDialog(mContext, subscriptionAccountHandles,
                    ipDialCallback);
        }
      /** @} */
    }

    /** SPRD: bug900273 ,905062 Card one Unicom card two mobile, call record details to make a video call,
      * pop-up card interface.@{ */
    if (view.getId() == R.id.video_call_icon) {

        CallSpecificAppData callSpecificAppData =
                CallSpecificAppData.newBuilder()
                        .setAllowAssistedDialing(true)
                        .setCallInitiationType(CallInitiationType.Type.CALL_LOG_DETAILS_VIDEO)
                        .build();

        if (DialerUtils.getRegisteredImsSlotForDualLteModem() == DialerUtils.SLOT_ID_ONE_TWO) {
            Log.d(TAG,"onClick DualLteModem");
            PreCall.start(mContext,
                    new CallIntentBuilder(mContact.getNumber(), callSpecificAppData)
                            .setIsVideoCall(true)
                            .setAllowAssistedDial(callSpecificAppData.getAllowAssistedDialing()));
        } else {
            Log.d(TAG,"onClick SoleModem");
            Intent mIntent = new CallIntentBuilder(mContact.getNumber(), callSpecificAppData)
                    .setIsVideoCall(true)
                    .setPhoneAccountHandle(DialerUtils.getPhoneAccountHandleBySlotId(mContext,
                            DialerUtils.getRegisteredImsSlotForDualLteModem()))
                    .build();
            DialerUtils.startActivityWithErrorToast(mContext, mIntent);
        }

        return;
    }
    /** @} */

    if (intent != null) {
      DialerUtils.startActivityWithErrorToast(mContext, intent);
    } else {
      android.util.Log.e(TAG, "intent is null");
    }
  }
  /** SPRD: add for bug 892901 @{ */
    private boolean isAirplaneModeOn() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }
  /** @} */

    /** SPRD: add for bug708782 @{ */
    class HandleDialAccountSelectedCallback extends SelectPhoneAccountDialogFragment.SelectPhoneAccountListener {
        final private String mNumber;
        final private boolean misVoicemail;

        public HandleDialAccountSelectedCallback(String number, boolean isVoiceMail) {
            mNumber = number;
            misVoicemail = isVoiceMail;
        }

        @Override
        public void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle,
                                           boolean setDefault, @Nullable String callId) {
            Intent intent = null;
            if (misVoicemail) {
                DialerUtils.callVoicemail(mContext, selectedAccountHandle);
            } else {
                intent = handleIpDial(mContext, selectedAccountHandle, mNumber);
            }
            if (intent != null) {
                DialerUtils.startActivityWithErrorToast(mContext, intent);
            }
        }
    }
  /** @} */

  public Intent handleIpDial(Context context, PhoneAccountHandle phoneAccountHandle,
                             String number) {
    if (phoneAccountHandle == null) {
      return null;
    }
    IpDialingUtils ipUtils = new IpDialingUtils(context);
    for (String prefix : IpDialingUtils.EXCLUDE_PREFIX) {
      if (number.startsWith(prefix)) {
        number = number.substring(prefix.length());
        break;
      }
    }
    TelecomManager telecomManager = (TelecomManager) context
            .getSystemService(Context.TELECOM_SERVICE);
    TelephonyManager tm = (TelephonyManager) context
            .getSystemService(Context.TELEPHONY_SERVICE);
    PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
    int subId = tm.getSubIdForPhoneAccount(phoneAccount);
    String ipPrefixNum = ipUtils.getIpDialNumber(subId);
    if (!TextUtils.isEmpty(ipPrefixNum)) {
      /**
       * UNISOC: Bug996182 Append ip prefix-num when the number starts with ip prefix-num
       * {@
       * */
      //if (!number.startsWith(ipPrefixNum)) {
        number = ipPrefixNum + number;
      //}
        /**
         * ｝@
         * */
      // append ip prefix-num, also as we will pass it to
      // Telecomm, so dial number can be handled in Telecom.
      final Intent intent = CallUtil.getCallIntent(number);
      intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
      intent.putExtra(IpDialingUtils.EXTRA_IS_IP_DIAL, true);
      intent.putExtra(IpDialingUtils.EXTRA_IP_PRFIX_NUM, ipPrefixNum);
      return intent;
    } else {
      final Intent ipListIntent = new Intent();
      ipListIntent.putExtra(SUB_ID_EXTRA, subId);
      ipListIntent.setAction(Intent.ACTION_MAIN);
      ipListIntent.addCategory(Intent.CATEGORY_DEVELOPMENT_PREFERENCE);
      ipListIntent.setComponent(new ComponentName(PHONE_PACKAGE_NAME, IP_NUMBER_LIST_ACTIVITY));
      return ipListIntent;
    }
  }

}
