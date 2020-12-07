/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.dialer.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.text.TextUtils;

import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.CompatUtils;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import java.util.List;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.telecom.VideoProfile;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.os.Build;
import android.provider.Settings;
import android.view.WindowManager;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.CallList;
import com.android.incallui.InCallPresenter;


/** Utilities related to calls that can be used by non system apps. */
public class CallUtil {
  public static final String TAG = "CallUtil";
  /** Indicates that the video calling is not available. */
  public static final int VIDEO_CALLING_DISABLED = 0;

  /** Indicates that video calling is enabled, regardless of presence status. */
  public static final int VIDEO_CALLING_ENABLED = 1;

  /**
   * Indicates that video calling is enabled, but the availability of video call affordances is
   * determined by the presence status associated with contacts.
   */
  public static final int VIDEO_CALLING_PRESENCE = 2;

  private static boolean hasInitializedIsVideoEnabledState;
  private static boolean cachedIsVideoEnabledState;

  //this has been defined in PhoneConstants.
  public static final String EXTRA_CALL_ORIGIN = "com.android.phone.CALL_ORIGIN";

  /** Return Uri with an appropriate scheme, accepting both SIP and usual phone call numbers. */
  public static Uri getCallUri(String number) {
    if (PhoneNumberHelper.isUriNumber(number)) {
      return Uri.fromParts(PhoneAccount.SCHEME_SIP, number, null);
    }
    return Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
  }

  /**
   * Determines if video calling is available, and if so whether presence checking is available as
   * well.
   *
   * <p>Returns a bitmask with {@link #VIDEO_CALLING_ENABLED} to indicate that video calling is
   * available, and {@link #VIDEO_CALLING_PRESENCE} if presence indication is also available.
   *
   * @param context The context
   * @return A bit-mask describing the current video capabilities.
   */
  public static int getVideoCallingAvailability(Context context) {
    if (!PermissionsUtil.hasPermission(context, android.Manifest.permission.READ_PHONE_STATE)
        || !CompatUtils.isVideoCompatible()) {
      return VIDEO_CALLING_DISABLED;
    }
    TelecomManager telecommMgr = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
    if (telecommMgr == null) {
      return VIDEO_CALLING_DISABLED;
    }

    List<PhoneAccountHandle> accountHandles = telecommMgr.getCallCapablePhoneAccounts();
    for (PhoneAccountHandle accountHandle : accountHandles) {
      PhoneAccount account = telecommMgr.getPhoneAccount(accountHandle);
      if (account != null) {
        if (account.hasCapabilities(PhoneAccount.CAPABILITY_VIDEO_CALLING)) {
          // Builds prior to N do not have presence support.
          if (!CompatUtils.isVideoPresenceCompatible()) {
            return VIDEO_CALLING_ENABLED;
          }

          int videoCapabilities = VIDEO_CALLING_ENABLED;
          if (account.hasCapabilities(PhoneAccount.CAPABILITY_VIDEO_CALLING_RELIES_ON_PRESENCE)) {
            videoCapabilities |= VIDEO_CALLING_PRESENCE;
          }
          return videoCapabilities;
        }
      }
    }
    return VIDEO_CALLING_DISABLED;
  }

  /**
   * Determines if one of the call capable phone accounts defined supports video calling.
   *
   * @param context The context.
   * @return {@code true} if one of the call capable phone accounts supports video calling, {@code
   *     false} otherwise.
   */
  public static boolean isVideoEnabled(Context context) {
    boolean isVideoEnabled = (getVideoCallingAvailability(context) & VIDEO_CALLING_ENABLED) != 0;

    // Log everytime the video enabled state changes.
    if (!hasInitializedIsVideoEnabledState) {
      LogUtil.i("CallUtil.isVideoEnabled", "isVideoEnabled: " + isVideoEnabled);
      hasInitializedIsVideoEnabledState = true;
      cachedIsVideoEnabledState = isVideoEnabled;
    } else if (cachedIsVideoEnabledState != isVideoEnabled) {
      LogUtil.i(
          "CallUtil.isVideoEnabled",
          "isVideoEnabled changed from %b to %b",
          cachedIsVideoEnabledState,
          isVideoEnabled);
      cachedIsVideoEnabledState = isVideoEnabled;
    }

    return isVideoEnabled;
  }

  /**
   * Determines if one of the call capable phone accounts defined supports calling with a subject
   * specified.
   *
   * @param context The context.
   * @return {@code true} if one of the call capable phone accounts supports calling with a subject
   *     specified, {@code false} otherwise.
   */
  public static boolean isCallWithSubjectSupported(Context context) {
    if (!PermissionsUtil.hasPermission(context, android.Manifest.permission.READ_PHONE_STATE)
        || !CompatUtils.isCallSubjectCompatible()) {
      return false;
    }
    TelecomManager telecommMgr = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
    if (telecommMgr == null) {
      return false;
    }

    List<PhoneAccountHandle> accountHandles = telecommMgr.getCallCapablePhoneAccounts();
    for (PhoneAccountHandle accountHandle : accountHandles) {
      PhoneAccount account = telecommMgr.getPhoneAccount(accountHandle);
      if (account != null && account.hasCapabilities(PhoneAccount.CAPABILITY_CALL_SUBJECT)) {
        /** UNISOC:add for bug954652 @{*/
        Log.d(TAG,"PhoneAccount_Capability: "+account.getCapabilities());
        /**@}*/
        return true;
      }
    }
    return false;
  }

  /** SPRD:bug877517 FEATURE_CALL_DETAIL_ACTIONS @{*/
  /**
   * Return an Intent for making a phone call. Scheme (e.g. tel, sip) will be determined
   * automatically.
   */
  public static Intent getCallIntent(String number) {
    return getCallIntent(getCallUri(number));
  }

  /**
   * Return an Intent for making a phone call. A given Uri will be used as is (without any
   * sanity check).
   */
  public static Intent getCallIntent(Uri uri) {
    return new Intent(Intent.ACTION_CALL, uri);
  }

  /**
   * A variant of {@link #getCallIntent} for starting a video call.
   */
  public static Intent getVideoCallIntent(String number, String callOrigin) {
    final Intent intent = new Intent(Intent.ACTION_CALL, getCallUri(number));
    intent.putExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
            VideoProfile.STATE_BIDIRECTIONAL);
    if (!TextUtils.isEmpty(callOrigin)) {
      intent.putExtra(EXTRA_CALL_ORIGIN, callOrigin);
    }
    return intent;
  }
  /**@}*/

  /** @return Uri that directly dials a user's voicemail inbox. */
  public static Uri getVoicemailUri() {
    return Uri.fromParts(PhoneAccount.SCHEME_VOICEMAIL, "", null);
  }

  /* SPRD: Add feature of low battery for Reliance @{ */
  public static boolean isBatteryLow(Context context) {
      Log.d(TAG, "in isBatteryLow ");
      boolean isShowLowBattery = context.getResources().getBoolean(com.android.internal.R.bool.config_show_low_battery_dialog);
      if (isShowLowBattery) {
          Intent batteryInfoIntent = context.registerReceiver(null, new IntentFilter(
                  Intent.ACTION_BATTERY_CHANGED));
          int current = batteryInfoIntent.getIntExtra("level", 0);
          int total = batteryInfoIntent.getIntExtra("scale", 0);
          if (current * 1.0 / total <= 0.15) {
              Log.d(TAG, "in isBattery Low 15%");
              return true;
          } else {
              Log.d(TAG, "in isBatteryLow high 15%");
              return false;
          }
      } else {
          return false;
      }
  }

  public static void showLowBatteryDialDialog(Context context, final Intent intent, final boolean isDialingByDialer) {
      if (Build.VERSION.SDK_INT >= 23) {
          if (! Settings.canDrawOverlays(context)) {
              Intent intent1 = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
              context.startActivity(intent1);
          }
      }
      AlertDialog.Builder builder = new AlertDialog.Builder(context);
      builder.setTitle(R.string.low_battery_warning_title);
      builder.setMessage(R.string.low_battery_warning_message);
      builder.setPositiveButton(R.string.low_battery_continue_video_call,
              new android.content.DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                      if (isDialingByDialer) {
                          DialerUtils.startActivityWithErrorToast(context, intent,
                                  R.string.activity_not_available);
                      } else {
                          context.startActivity(intent);
                      }
                      if (dialog != null) {
                          dialog.dismiss();
                      }
                  }
              });
      builder.setNegativeButton(R.string.low_battery_convert_to_voice_call,
              new android.content.DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                      intent.putExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                              VideoProfile.STATE_AUDIO_ONLY);
                      if (isDialingByDialer) {
                          DialerUtils.startActivityWithErrorToast(context, intent,
                                  R.string.activity_not_available);
                      } else {
                          context.startActivity(intent);
                      }
                      if (dialog != null) {
                          dialog.dismiss();
                      }
                  }
              });
      builder.setCancelable(false);
      AlertDialog dialog = builder.create();
      dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
      dialog.show();
  }

  public static void showLowBatteryInCallDialog(Context context , final android.telecom.Call telecomCall) {
      AlertDialog.Builder builder = new AlertDialog.Builder(context);
      builder.setTitle(R.string.low_battery_warning_title);
      builder.setMessage(R.string.low_battery_warning_message);
      builder.setPositiveButton(R.string.low_battery_continue_video_call,
              new android.content.DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                      if (telecomCall != null) {
                          telecomCall.answer(VideoProfile.STATE_BIDIRECTIONAL);
                      }
                      if (dialog != null) {
                          dialog.dismiss();
                      }
                  }
              });
      builder.setNegativeButton(R.string.low_battery_convert_to_voice_call,
              new android.content.DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                      if (telecomCall != null) {
                          telecomCall.answer(VideoProfile.STATE_AUDIO_ONLY);
                      }
                      if (dialog != null) {
                          dialog.dismiss();
                      }
                  }
              });
      builder.setCancelable(false);
      AlertDialog dialog = builder.create();
      dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
      dialog.show();
  }

  public static void showLowBatteryChangeToVideoDialog(Context context, final android.telecom.Call telecomCall) {
      AlertDialog.Builder builder = new AlertDialog.Builder(context);
      builder.setTitle(R.string.low_battery_warning_title);
      builder.setMessage(R.string.low_battery_warning_message);
      builder.setPositiveButton(R.string.low_battery_continue_video_call,
              new android.content.DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                      if (telecomCall != null) {
                          CallList callList = InCallPresenter.getInstance().getCallList();
                          DialerCall dialerCall = callList.getDialerCallFromTelecomCall(telecomCall);

                          if (dialerCall != null) {
                              dialerCall.getVideoTech().upgradeToVideo(context);
                          }
                      }
                      if (dialog != null) {
                          dialog.dismiss();
                      }
                  }
              });
      builder.setNegativeButton(R.string.low_battery_convert_to_voice_call,
              new android.content.DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                      if (dialog != null) {
                          dialog.dismiss();
                      }
                  }
              });
      builder.setCancelable(false);
      AlertDialog dialog = builder.create();
      dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
      dialog.show();
  }
  /*@}*/
}
