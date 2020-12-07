/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.dialer.app.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.view.MenuItem;
import android.widget.Toast;
import com.android.dialer.about.AboutPhoneFragment;
import com.android.dialer.app.R;
import com.android.dialer.assisteddialing.ConcreteCreator;
import com.android.dialer.blocking.FilteredNumberCompat;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.proguard.UsedByReflection;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.voicemail.settings.VoicemailSettingsFragment;
import com.android.voicemail.VoicemailClient;
import java.util.List;
import com.android.dialer.util.PermissionsUtil;
import com.android.incallui.sprd.InCallUiUtils;
import com.android.incallui.sprd.settings.CallRecordingSettingsFragment;
import com.android.incallui.sprd.settings.CallConnectionSettingsFragment;
import static android.Manifest.permission.READ_PHONE_STATE;
import com.android.ims.ImsManager;

import android.telephony.SubscriptionManager;

//import static android.Manifest.permission.READ_PHONE_STATE;

/** Activity for dialer settings. */
@SuppressWarnings("FragmentInjection") // Activity not exported
@UsedByReflection(value = "AndroidManifest-app.xml")
public class DialerSettingsActivity extends AppCompatPreferenceActivity {

  protected SharedPreferences preferences;
  private boolean migrationStatusOnBuildHeaders;
  private List<Header> headers;
  /* UNISOC: FAST DIAL FEATURE @{ */
  private static final String FASTDIAL_ACTION = "android.callsettings.action.FASTDIAL";
  private static final String FASTDIAL_PACKAGE_NAME = "com.android.dialer";
  private static final String FASTDIAL_CLASS_NAME =
          "com.android.dialer.app.fastdial.FastDialSettingActivity";
  /* @} */

  //SPRD: add for bug893265
  private SubscriptionManager mSubscriptionManager;

  //UNISOC:add for the bug1009598
  public static final String SUB_ID_EXTRA =
          "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionId";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    LogUtil.enterBlock("DialerSettingsActivity.onCreate");
    super.onCreate(savedInstanceState);
    preferences = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());

    Intent intent = getIntent();
    Uri data = intent.getData();
    if (data != null) {
      String headerToOpen = data.getSchemeSpecificPart();
      if (headerToOpen != null && headers != null) {
        for (Header header : headers) {
          if (headerToOpen.equals(header.fragment)) {
            LogUtil.i("DialerSettingsActivity.onCreate", "switching to header: " + headerToOpen);
            switchToHeader(header);
            break;
          }
        }
      }
    }

    //SPRD: add for bug893265
    mSubscriptionManager = SubscriptionManager.from(this);
  }

  @Override
  protected void onResume() {
    super.onResume();
    /*
     * The blockedCallsHeader need to be recreated if the migration status changed because
     * the intent needs to be updated.
     */
    if (migrationStatusOnBuildHeaders != FilteredNumberCompat.hasMigratedToNewBlocking(this)) {
      invalidateHeaders();
    }
    // SPRD: add for bug893265
    mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
  }

  @Override
  public void onBuildHeaders(List<Header> target) {
    // Keep a reference to the list of headers (since PreferenceActivity.getHeaders() is @Hide)
    headers = target;

    if (showDisplayOptions()) {
      Header displayOptionsHeader = new Header();
      displayOptionsHeader.titleRes = R.string.display_options_title;
      displayOptionsHeader.fragment = DisplayOptionsSettingsFragment.class.getName();
      target.add(displayOptionsHeader);
    }

    /* SPRD: modify for bug877407 @{
    Header soundSettingsHeader = new Header();
    soundSettingsHeader.titleRes = R.string.sounds_and_vibration_title;
    soundSettingsHeader.id = R.id.settings_header_sounds_and_vibration;
    target.add(soundSettingsHeader);
     @} */

    // UNISOC: modify for bug710657
    if (isPrimaryUser()) {
        Header quickResponseSettingsHeader = new Header();
        Intent quickResponseSettingsIntent =
            new Intent(TelecomManager.ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS);
        quickResponseSettingsHeader.titleRes = R.string.respond_via_sms_setting_title;
        quickResponseSettingsHeader.intent = quickResponseSettingsIntent;
        target.add(quickResponseSettingsHeader);
    }

    TelephonyManager telephonyManager =
        (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

    // "Call Settings" (full settings) is shown if the current user is primary user and there
    // is only one SIM. Before N, "Calling accounts" setting is shown if the current user is
    // primary user and there are multiple SIMs. In N+, "Calling accounts" is shown whenever
    // "Call Settings" is not shown.
    boolean isPrimaryUser = isPrimaryUser();
    if (isPrimaryUser && TelephonyManagerCompat.getPhoneCount(telephonyManager) <= 1) {
      Header callSettingsHeader = new Header();
      Intent callSettingsIntent = new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS);
      callSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      /**UNISOC:modify the bug for 1009598 @{*/
      int[] subId = SubscriptionManager.getSubId(0);
      if (subId != null && subId.length > 0 && subId[0] >= 0) {
          callSettingsIntent.putExtra(SUB_ID_EXTRA, subId[0]);
      }
      /** @}*/
      callSettingsHeader.titleRes = R.string.call_settings_label;
      callSettingsHeader.intent = callSettingsIntent;
      target.add(callSettingsHeader);
    } else if ((VERSION.SDK_INT >= VERSION_CODES.N) || isPrimaryUser) {
      Header phoneAccountSettingsHeader = new Header();
      Intent phoneAccountSettingsIntent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
      phoneAccountSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

      phoneAccountSettingsHeader.titleRes = R.string.phone_account_settings_label;
      phoneAccountSettingsHeader.intent = phoneAccountSettingsIntent;
      target.add(phoneAccountSettingsHeader);
    }
    /* SPRD: Add for Bug703760,841808. @{
    if (FilteredNumberCompat.canCurrentUserOpenBlockSettings(this)) {
      Header blockedCallsHeader = new Header();
      blockedCallsHeader.titleRes = R.string.manage_blocked_numbers_label;
      blockedCallsHeader.intent = FilteredNumberCompat.createManageBlockedNumbersIntent(this);
      target.add(blockedCallsHeader);
      migrationStatusOnBuildHeaders = FilteredNumberCompat.hasMigratedToNewBlocking(this);
    } */

    addVoicemailSettings(target, isPrimaryUser);
    /** SPRD: Feature bug877583 @{*/
    boolean isSupportVideoMeeting = getResources().getBoolean(
            R.bool.config_show_video_meeting_photo);
    if (PermissionsUtil.hasPermission(this, READ_PHONE_STATE)
        && (ImsManager.isVolteEnabledByPlatform(this) || ImsManager.isWfcEnabledByPlatform(this)) && isSupportVideoMeeting) {
      Header videoPhotoSettingsHeader = new Header();
      videoPhotoSettingsHeader.titleRes = R.string.video_photo_select_title;
      videoPhotoSettingsHeader.fragment
              = VideoCallMeetingPhotoSettingsFragment.class.getName();
      videoPhotoSettingsHeader.id = R.id.settings_video_photo_select;
      target.add(videoPhotoSettingsHeader);
    }
    /** @} */
    if (isPrimaryUser
        && (TelephonyManagerCompat.isTtyModeSupported(telephonyManager)
            || TelephonyManagerCompat.isHearingAidCompatibilitySupported(telephonyManager))) {
      Header accessibilitySettingsHeader = new Header();
      Intent accessibilitySettingsIntent =
          new Intent(TelecomManager.ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS);
      accessibilitySettingsHeader.titleRes = R.string.accessibility_settings_title;
      accessibilitySettingsHeader.intent = accessibilitySettingsIntent;
      target.add(accessibilitySettingsHeader);
    }

    boolean isAssistedDialingEnabled =
        ConcreteCreator.isAssistedDialingEnabled(
            ConfigProviderBindings.get(getApplicationContext()));
    LogUtil.i(
        "DialerSettingsActivity.onBuildHeaders",
        "showing assisted dialing header: " + isAssistedDialingEnabled);
    if (isAssistedDialingEnabled) {

      Header assistedDialingSettingsHeader = new Header();
      assistedDialingSettingsHeader.titleRes =
          com.android.dialer.assisteddialing.ui.R.string.assisted_dialing_setting_title;
      assistedDialingSettingsHeader.intent =
          new Intent("com.android.dialer.app.settings.SHOW_ASSISTED_DIALING_SETTINGS");
      target.add(assistedDialingSettingsHeader);
    }

    if (showAbout()) {
      Header aboutPhoneHeader = new Header();
      aboutPhoneHeader.titleRes = R.string.about_phone_label;
      aboutPhoneHeader.fragment = AboutPhoneFragment.class.getName();
      target.add(aboutPhoneHeader);
    }
    /* SPRD Feature Porting: Automatic record. && Add switch for automatic record feature.
     * && SPRD Feature Porting: Hide recorder feature for telstra case @{
     **/
    if (isPrimaryUser && PermissionsUtil.hasPermission(this, READ_PHONE_STATE)
            && InCallUiUtils.isSupportAutomaticCallRecord(this)
            && InCallUiUtils.isRecorderEnabled(this)) {
      Header recordSettingsHeader = new Header();
      recordSettingsHeader.titleRes = R.string.call_recording_setting_title;
      recordSettingsHeader.fragment = CallRecordingSettingsFragment.class.getName();
      target.add(recordSettingsHeader);
      }

    /* SPRD Feature Porting: Flip to silence from incoming calls. @{ */
    if(PermissionsUtil.hasPermission(this, READ_PHONE_STATE) &&
            !InCallUiUtils.isSupportFlipToMute(this) && isPrimaryUser
            && InCallUiUtils.isFlipToSilentCallEnabled(this)) {
      Header incomingCallsFlippingSilenceSettingsHeader = new Header();
      incomingCallsFlippingSilenceSettingsHeader.titleRes =
              R.string.incomingcall_flipping_silence_title;
      incomingCallsFlippingSilenceSettingsHeader.intent = InCallUiUtils
              .getIntentForStartingActivity(InCallUiUtils.FLAG_SILENT_FLIPING);
      target.add(incomingCallsFlippingSilenceSettingsHeader);
    }
    /* @} */

    /* UNISOC: FAST DIAL FEATURE @{ */
    if (isPrimaryUser) {
      Header fastDialSettingsHeader = new Header();
      Intent fastDialSettingsIntent = new Intent(FASTDIAL_ACTION);
      fastDialSettingsIntent.setClassName(FASTDIAL_PACKAGE_NAME, FASTDIAL_CLASS_NAME);
      fastDialSettingsHeader.titleRes = R.string.fast_dial_title;
      fastDialSettingsHeader.intent = fastDialSettingsIntent;
      target.add(fastDialSettingsHeader);
    }
    /* @} */

    /* SPRD Feature Porting: Vibrate when call connected or disconnected feature. @{ */
    if (PermissionsUtil.hasPermission(this, READ_PHONE_STATE) && isPrimaryUser
            && InCallUiUtils.isSupportVibrateForCallConnectionFeature(this) ) {
      Header callConnectionSettingsHeader = new Header();
      callConnectionSettingsHeader.titleRes =
              R.string.vibration_feedback_for_call_connection_setting_title;
      callConnectionSettingsHeader.fragment =
              CallConnectionSettingsFragment.class.getName();
      target.add(callConnectionSettingsHeader);
    }

    /* SPRD Feature Porting: Fade in ringer volume when incoming calls. @{ */
    if (!InCallUiUtils.isSupportSensorHub(getApplicationContext())
            && PermissionsUtil.hasPermission(this, READ_PHONE_STATE)
            && isPrimaryUser && InCallUiUtils.isFadeInRingerEnabled(this)) {
      Header fadeInSettingsHeader = new Header();
      fadeInSettingsHeader.titleRes = R.string.fade_in_title;
      fadeInSettingsHeader.intent = InCallUiUtils
              .getIntentForStartingActivity(InCallUiUtils.FLAG_FADE_IN);
      target.add(fadeInSettingsHeader);
    }
    /* @} */
  }

  private void addVoicemailSettings(List<Header> target, boolean isPrimaryUser) {
    if (!isPrimaryUser) {
      LogUtil.i("DialerSettingsActivity.addVoicemailSettings", "user not primary user");
      return;
    }
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      LogUtil.i(
          "DialerSettingsActivity.addVoicemailSettings",
          "Dialer voicemail settings not supported by system");
      return;
    }

    if (!PermissionsUtil.hasReadPhoneStatePermissions(this)) {
      LogUtil.i("DialerSettingsActivity.addVoicemailSettings", "Missing READ_PHONE_STATE");
      return;
    }

    /** SPRD: add for bug939232 When there is no sim card, the call settings should not display the voicemail menu.@{ */
    List<SubscriptionInfo> subscriptionInfos
                = SubscriptionManager.from(this).getActiveSubscriptionInfoList();
    if (subscriptionInfos == null) {
      LogUtil.i("DialerSettingsActivity.addVoicemailSettings", "no active subcriptioninfo");
      return;
    }
    /** @} */
    LogUtil.i("DialerSettingsActivity.addVoicemailSettings", "adding voicemail settings");
    Header voicemailSettings = new Header();
    voicemailSettings.titleRes = R.string.voicemail_settings_label;
    PhoneAccountHandle soleAccount = getSoleSimAccount();
    if (soleAccount == null) {
      LogUtil.i(
          "DialerSettingsActivity.addVoicemailSettings", "showing multi-SIM voicemail settings");
      voicemailSettings.fragment = PhoneAccountSelectionFragment.class.getName();
      Bundle bundle = new Bundle();
      bundle.putString(
          PhoneAccountSelectionFragment.PARAM_TARGET_FRAGMENT,
          VoicemailSettingsFragment.class.getName());
      bundle.putString(
          PhoneAccountSelectionFragment.PARAM_PHONE_ACCOUNT_HANDLE_KEY,
          VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE);
      bundle.putBundle(PhoneAccountSelectionFragment.PARAM_ARGUMENTS, new Bundle());
      bundle.putInt(
          PhoneAccountSelectionFragment.PARAM_TARGET_TITLE_RES, R.string.voicemail_settings_label);
      voicemailSettings.fragmentArguments = bundle;
      target.add(voicemailSettings);
    } else {
      LogUtil.i(
          "DialerSettingsActivity.addVoicemailSettings", "showing single-SIM voicemail settings");
      voicemailSettings.fragment = VoicemailSettingsFragment.class.getName();
      Bundle bundle = new Bundle();
      bundle.putParcelable(VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE, soleAccount);
      voicemailSettings.fragmentArguments = bundle;
      target.add(voicemailSettings);
    }
  }

  /**
   * @return the only SIM phone account, or {@code null} if there are none or more than one. Note:
   *     having a empty SIM slot still count as a PhoneAccountHandle that is "invalid", and
   *     voicemail settings should still be available for it.
   */
  @Nullable
  private PhoneAccountHandle getSoleSimAccount() {
    TelecomManager telecomManager = getSystemService(TelecomManager.class);
    PhoneAccountHandle result = null;
    for (PhoneAccountHandle phoneAccountHandle : telecomManager.getCallCapablePhoneAccounts()) {
      PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
      // UNISOC: modify for bug719145
      if (phoneAccount != null &&
              phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
        LogUtil.i(
            "DialerSettingsActivity.getSoleSimAccount", phoneAccountHandle + " is a SIM account");
        if (result != null) {
          return null;
        }
        result = phoneAccountHandle;
      }
    }
    return result;
  }

  /** Whether "about" should be shown in settings. Override to hide about. */
  public boolean showAbout() {
    return false;
  }

  /**
   * Returns {@code true} or {@code false} based on whether the display options setting should be
   * shown. For languages such as Chinese, Japanese, or Korean, display options aren't useful since
   * contacts are sorted and displayed family name first by default.
   *
   * @return {@code true} if the display options should be shown, {@code false} otherwise.
   */
  private boolean showDisplayOptions() {
    return getResources().getBoolean(R.bool.config_display_order_user_changeable)
        && getResources().getBoolean(R.bool.config_sort_order_user_changeable);
  }

  /**
   * For the "sounds and vibration" setting, we go directly to the system sound settings fragment.
   * This helps since:
   * <li>We don't need a separate Dialer sounds and vibrations fragment, as everything we need is
   *     present in the system sounds fragment.
   * <li>OEM's e.g Moto that support dual sim ring-tones no longer need to update the dialer sound
   *     and settings fragment.
   *
   *     <p>For all other settings, we launch our our preferences fragment.
   */
  @Override
  public void onHeaderClick(Header header, int position) {
    if (header.id == R.id.settings_header_sounds_and_vibration) {

      if (!Settings.System.canWrite(this)) {
        Toast.makeText(
                this,
                getResources().getString(R.string.toast_cannot_write_system_settings),
                Toast.LENGTH_SHORT)
            .show();
      }

      startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
      return;
    }

    super.onHeaderClick(header, position);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return false;
  }

  @Override
  public void onBackPressed() {
    if (!isSafeToCommitTransactions()) {
      return;
    }
    super.onBackPressed();
  }

  @Override
  protected boolean isValidFragment(String fragmentName) {
    return true;
  }

  /** @return Whether the current user is the primary user. */
  private boolean isPrimaryUser() {
    return getSystemService(UserManager.class).isSystemUser();
  }

  /* SPRD: add for bug893265 @{ */
  private final SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangeListener
          = new SubscriptionManager.OnSubscriptionsChangedListener() {
    @Override
    public void onSubscriptionsChanged() {
      invalidateHeaders();
    }
  };

  @Override
  protected void onPause() {
    super.onPause();
    mSubscriptionManager.removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
  }
  /* @} */
}
