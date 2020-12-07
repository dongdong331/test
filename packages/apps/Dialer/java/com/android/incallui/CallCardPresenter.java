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
 * limitations under the License
 */

package com.android.incallui;

import static com.android.contacts.common.compat.CallCompat.Details.PROPERTY_ENTERPRISE_CALL;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Trace;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.telecom.Call.Details;
import android.telecom.PhoneAccount;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.view.Display;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.app.sprd.telcel.DialerTelcelHelper;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.ActivityCompat;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.multimedia.MultimediaData;
import com.android.dialer.oem.MotorolaUtils;
import com.android.ims.internal.ImsManagerEx;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.postcall.PostCall;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallEventListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCall.State;
import com.android.incallui.call.DialerCallListener;
import com.android.incallui.calllocation.CallLocation;
import com.android.incallui.calllocation.CallLocationComponent;
import com.android.incallui.incall.protocol.ContactPhotoType;
import com.android.incallui.incall.protocol.InCallScreen;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryCallState.ButtonState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.incall.protocol.SecondaryInfo;
import com.android.incallui.sprd.InCallUiUtils;
import com.android.incallui.sprd.plugin.displayfdn.DisplayFdnHelper;
import com.android.incallui.videotech.utils.SessionModificationState;
import java.lang.ref.WeakReference;
import com.android.ims.internal.ImsManagerEx;//UNISOC:add for bug1014616

/**
 * Controller for the Call Card Fragment. This class listens for changes to InCallState and passes
 * it along to the fragment.
 */
public class CallCardPresenter
    implements InCallStateListener,
        IncomingCallListener,
        InCallDetailsListener,
        InCallEventListener,
        InCallScreenDelegate,
        DialerCallListener {

  /**
   * Amount of time to wait before sending an announcement via the accessibility manager. When the
   * call state changes to an outgoing or incoming state for the first time, the UI can often be
   * changing due to call updates or contact lookup. This allows the UI to settle to a stable state
   * to ensure that the correct information is announced.
   */
  private static final long ACCESSIBILITY_ANNOUNCEMENT_DELAY_MILLIS = 500;

  /** Flag to allow the user's current location to be shown during emergency calls. */
  private static final String CONFIG_ENABLE_EMERGENCY_LOCATION = "config_enable_emergency_location";

  private static final boolean CONFIG_ENABLE_EMERGENCY_LOCATION_DEFAULT = true;

  /**
   * Make it possible to not get location during an emergency call if the battery is too low, since
   * doing so could trigger gps and thus potentially cause the phone to die in the middle of the
   * call.
   */
  private static final String CONFIG_MIN_BATTERY_PERCENT_FOR_EMERGENCY_LOCATION =
      "min_battery_percent_for_emergency_location";

  private static final long CONFIG_MIN_BATTERY_PERCENT_FOR_EMERGENCY_LOCATION_DEFAULT = 10;

  private final Context context;
  private final Handler handler = new Handler();

  private DialerCall primary;
  private String primaryNumber;
  private DialerCall secondary;
  private String secondaryNumber;
  private ContactCacheEntry primaryContactInfo;
  private ContactCacheEntry secondaryContactInfo;
  @Nullable private ContactsPreferences contactsPreferences;
  private boolean isFullscreen = false;
  private InCallScreen inCallScreen;
  private boolean isInCallScreenReady;
  private boolean shouldSendAccessibilityEvent;
  //UNISOC: add for bug900276
  private boolean isConference;
  private boolean isEmergency;

  @NonNull private final CallLocation callLocation;
  private final Runnable sendAccessibilityEventRunnable =
      new Runnable() {
        @Override
        public void run() {
          shouldSendAccessibilityEvent = !sendAccessibilityEvent(context, getUi());
          LogUtil.i(
              "CallCardPresenter.sendAccessibilityEventRunnable",
              "still should send: %b",
              shouldSendAccessibilityEvent);
          if (!shouldSendAccessibilityEvent) {
            handler.removeCallbacks(this);
          }
        }
      };

  public CallCardPresenter(Context context) {
    LogUtil.i("CallCardPresenter.constructor", null);
    this.context = Assert.isNotNull(context).getApplicationContext();
    callLocation = CallLocationComponent.get(this.context).getCallLocation();
  }

  private static boolean hasCallSubject(DialerCall call) {
    return !TextUtils.isEmpty(call.getCallSubject());
  }

  @Override
  public void onInCallScreenDelegateInit(InCallScreen inCallScreen) {
    Assert.isNotNull(inCallScreen);
    this.inCallScreen = inCallScreen;
    contactsPreferences = ContactsPreferencesFactory.newContactsPreferences(context);

    // Call may be null if disconnect happened already.
    DialerCall call = CallList.getInstance().getFirstCall();
    //UNISOC: add for bug900276
    if (primary != null) {
      isConference = primary.isConferenceCall();
    }
    if (call != null) {
      primary = call;
      if (shouldShowNoteSentToast(primary)) {
        this.inCallScreen.showNoteSentToast();
      }
      call.addListener(this);
      // start processing lookups right away.
      if (!call.isConferenceCall()
         // SPRD: for bug887066
         ||(call.isConferenceCall() && call.getChildCallIds().size() < 1)) {
        startContactInfoSearch(call, true, call.getState() == DialerCall.State.INCOMING);
      } else {
        updateContactEntry(null, true);
      }
    }

    onStateChange(null, InCallPresenter.getInstance().getInCallState(), CallList.getInstance());
  }

  @Override
  public void onInCallScreenReady() {
    LogUtil.i("CallCardPresenter.onInCallScreenReady", null);
    Assert.checkState(!isInCallScreenReady);
    if (contactsPreferences != null) {
      contactsPreferences.refreshValue(ContactsPreferences.DISPLAY_ORDER_KEY);
    }

    // Contact search may have completed before ui is ready.
    if (primaryContactInfo != null) {
      updatePrimaryDisplayInfo();
    }

    // Register for call state changes last
    InCallPresenter.getInstance().addListener(this);
    InCallPresenter.getInstance().addIncomingCallListener(this);
    InCallPresenter.getInstance().addDetailsListener(this);
    InCallPresenter.getInstance().addInCallEventListener(this);
    isInCallScreenReady = true;

    // Log location impressions
    if (isOutgoingEmergencyCall(primary)) {
      Logger.get(context).logImpression(DialerImpression.Type.EMERGENCY_NEW_EMERGENCY_CALL);
    } else if (isIncomingEmergencyCall(primary) || isIncomingEmergencyCall(secondary)) {
      Logger.get(context).logImpression(DialerImpression.Type.EMERGENCY_CALLBACK);
    }

    // Showing the location may have been skipped if the UI wasn't ready during previous layout.
    if (shouldShowLocation()) {
      inCallScreen.showLocationUi(getLocationFragment());

      // Log location impressions
      if (!hasLocationPermission()) {
        Logger.get(context).logImpression(DialerImpression.Type.EMERGENCY_NO_LOCATION_PERMISSION);
      } else if (isBatteryTooLowForEmergencyLocation()) {
        Logger.get(context)
            .logImpression(DialerImpression.Type.EMERGENCY_BATTERY_TOO_LOW_TO_GET_LOCATION);
      } else if (!callLocation.canGetLocation(context)) {
        Logger.get(context).logImpression(DialerImpression.Type.EMERGENCY_CANT_GET_LOCATION);
      }
    }
  }

  @Override
  public void onInCallScreenUnready() {
    LogUtil.i("CallCardPresenter.onInCallScreenUnready", null);
    Assert.checkState(isInCallScreenReady);

    // stop getting call state changes
    InCallPresenter.getInstance().removeListener(this);
    InCallPresenter.getInstance().removeIncomingCallListener(this);
    InCallPresenter.getInstance().removeDetailsListener(this);
    InCallPresenter.getInstance().removeInCallEventListener(this);
    if (primary != null) {
      primary.removeListener(this);
    }

    if(primary != null && !primary.isVideoCall()){// add for bug966920
      InCallPresenter.getInstance().enableScreenTimeout(true);
    }

    callLocation.close();

    primary = null;
    primaryContactInfo = null;
    secondaryContactInfo = null;
    isInCallScreenReady = false;
  }

  @Override
  public void onIncomingCall(InCallState oldState, InCallState newState, DialerCall call) {
    // same logic should happen as with onStateChange()
    onStateChange(oldState, newState, CallList.getInstance());
  }

  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    Trace.beginSection("CallCardPresenter.onStateChange");
    LogUtil.v("CallCardPresenter.onStateChange", "oldState: %s, newState: %s", oldState, newState);
    if (inCallScreen == null) {
      Trace.endSection();
      return;
    }

    DialerCall primary = null;
    DialerCall secondary = null;

    if (newState == InCallState.INCOMING) {
      /**
       * SPRD: DSDA
       * @orig primary = callList.getIncomingCall();
       */
      if (callList.getUserPrimaryCall() != null
              && callList.getUserPrimaryCall().getState() == DialerCall.State.INCOMING
              && ImsManagerEx.isDualVoLTERegistered()) {
        primary = callList.getUserPrimaryCall();
      } else {
      primary = callList.getIncomingCall();
      }
      if (ImsManagerEx.isDualVoLTERegistered()) {
        secondary = getCallToDisplayForDADA(callList, primary);
      }
    } else if (newState == InCallState.PENDING_OUTGOING || newState == InCallState.OUTGOING) {
      primary = callList.getOutgoingCall();
      if (primary == null) {
        primary = callList.getPendingOutgoingCall();
      }

      // getCallToDisplay doesn't go through outgoing or incoming calls. It will return the
      // highest priority call to display as the secondary call.
      /**
       * SPRD: DSDA
       * @orig secondary = getCallToDisplay(callList, null, true);
       */
      if(ImsManagerEx.isDualVoLTERegistered()){
        secondary = getCallToDisplayForDADA(callList, primary);
      } else {
        secondary = getCallToDisplay(callList, null, true);
      }
    } else if (newState == InCallState.INCALL) {
      /**
       * SPRD: DUAL VOLTE
       * @orig primary = getCallToDisplay(callList, null, false);
      secondary = getCallToDisplay(callList, primary, true);
       */
      if(ImsManagerEx.isDualVoLTERegistered()){
        if (callList.getUserPrimaryCall()!= null &&
                (callList.getUserPrimaryCall().getState() == DialerCall.State.ACTIVE
                        || callList.getUserPrimaryCall().getState() == DialerCall.State.ONHOLD)) {
          primary = callList.getUserPrimaryCall();
        } else {
          primary = getCallToDisplay(callList, null, false);
        }
        secondary = getCallToDisplayForDADA(callList, primary);
      } else {
        primary = getCallToDisplay(callList, null, false);
        secondary = getCallToDisplay(callList, primary, true);
      }
    }

    LogUtil.i("CallCardPresenter.onStateChange", "primary call: " + primary);
    LogUtil.i("CallCardPresenter.onStateChange", "secondary call: " + secondary);

    String primaryNumber = null;
    String secondaryNumber = null;
    //UNISOC: add for bug900276
    boolean isConference = false;
    if (primary != null) {
      primaryNumber = primary.getNumber();
      isConference = primary.isConferenceCall()
              // UNISOC: add for bug941546
              && !(InCallUiUtils.shouldUpdateConferenceUIWithOneParticipant(context) &&
              primary.getChildCallIds() != null && primary.getChildCallIds().size() == 1);
    }
    if (secondary != null) {
      secondaryNumber = secondary.getNumber();
    }

    // UNISOC: add for bug Bug 887066
    if (this.primary != null && this.primaryNumber == null) {
      this.primaryNumber = this.primary.getNumber();
    }
    final boolean primaryChanged =
        !(DialerCall.areSame(this.primary, primary)
            && TextUtils.equals(this.primaryNumber, primaryNumber));
    final boolean secondaryChanged =
        !(DialerCall.areSame(this.secondary, secondary)
            && TextUtils.equals(this.secondaryNumber, secondaryNumber));
    DialerCall previousSecondary = secondary;

    this.secondary = secondary;
    this.secondaryNumber = secondaryNumber;
    DialerCall previousPrimary = this.primary;
    this.primary = primary;
    this.primaryNumber = primaryNumber;

    if (this.primary != null) {
      InCallPresenter.getInstance().onForegroundCallChanged(this.primary);
      inCallScreen.updateInCallScreenColors();
    }

    if (primaryChanged && shouldShowNoteSentToast(primary)) {
      inCallScreen.showNoteSentToast();
    }

    /* SPRD: add for feature FL0108020006, use cpu time instead of system time @{ */
    if (isPrimaryCallActive()) {
        this.primary.updateConnectRealTimeMillis();
    }
    /* @} */

    boolean isEmergencyChanged = false;
    if (primary != null && this.isEmergency != primary.isEmergencyCall()) {
        this.isEmergency = primary.isEmergencyCall();
        isEmergencyChanged = true;
      }
    LogUtil.i("CallCardPresenter.onStateChange", "isEmergencyChanged " + isEmergencyChanged);

    // Refresh primary call information if either:
    // 1. Primary call changed.
    // 2. The call's ability to manage conference has changed.
    if (shouldRefreshPrimaryInfo(primaryChanged)
            || isEmergencyChanged) {
      // primary call has changed
      if (previousPrimary != null) {
        previousPrimary.removeListener(this);
      }
      this.primary.addListener(this);

      primaryContactInfo =
          ContactInfoCache.buildCacheEntryFromCall(
              context, this.primary, this.primary.getState() == DialerCall.State.INCOMING);
      // UNISOC: add for bug954922
      this.isConference = isConference;
      updatePrimaryDisplayInfo();
      maybeStartSearch(this.primary, true);
      //UNISOC: add for bug900276 903500
    } else if (this.isConference != isConference) {
      //UNISOC: add for bug900276
      this.isConference = isConference;
      updatePrimaryDisplayInfo();
    }

    if (previousPrimary != null && this.primary == null) {
      previousPrimary.removeListener(this);
    }

    if (secondaryChanged) {
      if (this.secondary == null) {
        // Secondary call may have ended.  Update the ui.
        secondaryContactInfo = null;
        updateSecondaryDisplayInfo();
      } else {
        // secondary call has changed
        secondaryContactInfo =
            ContactInfoCache.buildCacheEntryFromCall(
                context, this.secondary, this.secondary.getState() == DialerCall.State.INCOMING);
        updateSecondaryDisplayInfo();
        maybeStartSearch(this.secondary, false);
      }
    }

    // Set the call state
    int callState = DialerCall.State.IDLE;
    if (this.primary != null) {
      callState = this.primary.getState();
      updatePrimaryCallState();
    } else {
      getUi().setCallState(PrimaryCallState.empty());
    }

    /* SPRD: DSDA. @{*/
    if (secondary != null && ImsManagerEx.isDualVoLTERegistered()) {
      // UNISOC: modify for bug900867
      updateSecondaryCallState();
    }
    /* @} */
    maybeShowManageConferenceCallButton();

    // Hide the end call button instantly if we're receiving an incoming call.
    getUi()
        .setEndCallButtonEnabled(
            shouldShowEndCallButton(this.primary, callState),
            callState != DialerCall.State.INCOMING /* animate */);

    maybeSendAccessibilityEvent(oldState, newState, primaryChanged);

    /* SPRD Feature Porting: Vibrate when call connected or disconnected feature. @{ */
    if (oldState == InCallState.OUTGOING
            && newState == InCallState.INCALL) {
      InCallUiUtils.vibrateForCallStateChange(context.getApplicationContext(),
              primary,
              InCallUiUtils.VIBRATION_FEEDBACK_FOR_CONNECT_PREFERENCES_NAME);
    }
    /* @} */
    Trace.endSection();
  }

  @Override
  public void onDetailsChanged(DialerCall call, Details details) {
    updatePrimaryCallState();

    if (call.can(Details.CAPABILITY_MANAGE_CONFERENCE)
        != details.can(Details.CAPABILITY_MANAGE_CONFERENCE)) {
      maybeShowManageConferenceCallButton();
    }
  }

  @Override
  public void onDialerCallDisconnect() {}

  @Override
  public void onDialerCallUpdate() {
    // No-op; specific call updates handled elsewhere.
  }

  @Override
  public void onWiFiToLteHandover() {}

  @Override
  public void onHandoverToWifiFailure() {}

  @Override
  public void onInternationalCallOnWifi() {}

  @Override
  public void onEnrichedCallSessionUpdate() {
    LogUtil.enterBlock("CallCardPresenter.onEnrichedCallSessionUpdate");
    updatePrimaryDisplayInfo();
  }

  /** Handles a change to the child number by refreshing the primary call info. */
  @Override
  public void onDialerCallChildNumberChange() {
    LogUtil.v("CallCardPresenter.onDialerCallChildNumberChange", "");

    if (primary == null) {
      return;
    }
    updatePrimaryDisplayInfo();
  }

  /** Handles a change to the last forwarding number by refreshing the primary call info. */
  @Override
  public void onDialerCallLastForwardedNumberChange() {
    LogUtil.v("CallCardPresenter.onDialerCallLastForwardedNumberChange", "");

    if (primary == null) {
      return;
    }
    updatePrimaryDisplayInfo();
    updatePrimaryCallState();
  }

  @Override
  public void onDialerCallUpgradeToVideo() {}

  /** Handles a change to the session modification state for a call. */
  @Override
  public void onDialerCallSessionModificationStateChange() {
    LogUtil.enterBlock("CallCardPresenter.onDialerCallSessionModificationStateChange");

    if (primary == null) {
      return;
    }
    getUi()
        .setEndCallButtonEnabled(
            primary.getVideoTech().getSessionModificationState()
                != SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST,
            true /* shouldAnimate */);
    updatePrimaryCallState();
  }

  private boolean shouldRefreshPrimaryInfo(boolean primaryChanged) {
    if (primary == null) {
      return false;
    }
    return primaryChanged
        || inCallScreen.isManageConferenceVisible() != shouldShowManageConference();
  }

  private void updatePrimaryCallState() {
    if (getUi() != null && primary != null) {
      boolean isWorkCall =
          primary.hasProperty(PROPERTY_ENTERPRISE_CALL)
              || (primaryContactInfo != null
                  && primaryContactInfo.userType == ContactsUtils.USER_TYPE_WORK);
      boolean isHdAudioCall =
          isPrimaryCallActive() && primary.hasProperty(Details.PROPERTY_HIGH_DEF_AUDIO);
      boolean isAttemptingHdAudioCall =
          !isHdAudioCall
              && !primary.hasProperty(DialerCall.PROPERTY_CODEC_KNOWN)
              && MotorolaUtils.shouldBlinkHdIconWhenConnectingCall(context);

      boolean isBusiness = primaryContactInfo != null && primaryContactInfo.isBusiness;

      // Check for video state change and update the visibility of the contact photo.  The contact
      // photo is hidden when the incoming video surface is shown.
      // The contact photo visibility can also change in setPrimary().
      boolean shouldShowContactPhoto =
          !VideoCallPresenter.showIncomingVideo(primary.getVideoState(), primary.getState());
      getUi()
          .setCallState(
              PrimaryCallState.builder()
                  .setState(primary.getState())
                  .setIsVideoCall(primary.isVideoCall())
                  .setSessionModificationState(primary.getVideoTech().getSessionModificationState())
                  .setDisconnectCause(primary.getDisconnectCause())
                  .setConnectionLabel(getConnectionLabel())
                  .setConnectionIcon(getCallStateIcon())
                  .setGatewayNumber(getGatewayNumber())
                  .setCallSubject(shouldShowCallSubject(primary) ? primary.getCallSubject() : null)
                  .setCallbackNumber(
                      PhoneNumberHelper.formatNumber(
                          context, primary.getCallbackNumber(), primary.getSimCountryIso()))
                  .setIsWifi(primary.hasProperty(Details.PROPERTY_WIFI))
                  .setIsConference(
                      primary.isConferenceCall()
                          && !primary.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE))
                  .setIsWorkCall(isWorkCall)
                  .setIsHdAttempting(isAttemptingHdAudioCall)
                  .setIsHdAudioCall(isHdAudioCall)
                  .setIsForwardedNumber(
                      !TextUtils.isEmpty(primary.getLastForwardedNumber())
                          || primary.isCallForwarded())
                  .setShouldShowContactPhoto(shouldShowContactPhoto)
                  .setConnectTimeMillis(primary.getConnectRealTimeMillis())  //getConnectTimeMillis(); modify for feature FL0108020006, use cpu time instead of system time
                  .setIsVoiceMailNumber(primary.isVoiceMailNumber())
                  .setIsRemotelyHeld(primary.isRemotelyHeld())
                  .setIsBusinessNumber(isBusiness)
                  .setSupportsCallOnHold(supports2ndCallOnHold())
                  .setSwapToSecondaryButtonState(getSwapToSecondaryButtonState())
                  .setIsAssistedDialed(primary.isAssistedDialed())
                  .setCustomLabel(null)
                  .setAssistedDialingExtras(primary.getAssistedDialingExtras())
                  .build());

      InCallActivity activity =
          (InCallActivity) (inCallScreen.getInCallScreenFragment().getActivity());
      if (activity != null) {
        activity.onPrimaryCallStateChanged();
      }
    }
  }

  private @ButtonState int getSwapToSecondaryButtonState() {
    if (secondary == null) {
      return ButtonState.NOT_SUPPORT;
    }
    if (primary.getState() == State.ACTIVE) {
      return ButtonState.ENABLED;
    }
    return ButtonState.DISABLED;
  }

  /** Only show the conference call button if we can manage the conference. */
  private void maybeShowManageConferenceCallButton() {
    getUi().showManageConferenceCallButton(shouldShowManageConference());
  }

  /**
   * Determines if the manage conference button should be visible, based on the current primary
   * call.
   *
   * @return {@code True} if the manage conference button should be visible.
   */
  private boolean shouldShowManageConference() {
    if (primary == null) {
      return false;
    }

    /*SPRD: add for feature FL1000060357 {@*/
    if (!InCallUiUtils.shouldShowConferenceWithOneParticipant(context)
            &&(primary.can(android.telecom.Call.Details.CAPABILITY_MANAGE_CONFERENCE)
            && primary.getChildCallIds() != null
            && primary.getChildCallIds().size() ==1
            || primary.getState() == DialerCall.State.DISCONNECTING
            || primary.getState() == DialerCall.State.DISCONNECTED)) {
      return false;
    }
    /*@}*/

    return primary.can(android.telecom.Call.Details.CAPABILITY_MANAGE_CONFERENCE) && !isFullscreen;
  }

  private boolean supports2ndCallOnHold() {
    DialerCall firstCall = CallList.getInstance().getActiveOrBackgroundCall();
    DialerCall incomingCall = CallList.getInstance().getIncomingCall();
    if (firstCall != null && incomingCall != null && firstCall != incomingCall) {
      return incomingCall.can(Details.CAPABILITY_HOLD);
    }
    return true;
  }

  @Override
  public void onCallStateButtonClicked() {
    Intent broadcastIntent = Bindings.get(context).getCallStateButtonBroadcastIntent(context);
    if (broadcastIntent != null) {
      LogUtil.v(
          "CallCardPresenter.onCallStateButtonClicked",
          "sending call state button broadcast: " + broadcastIntent);
      context.sendBroadcast(broadcastIntent, Manifest.permission.READ_PHONE_STATE);
    }
  }

  @Override
  public void onManageConferenceClicked() {
    InCallActivity activity =
        (InCallActivity) (inCallScreen.getInCallScreenFragment().getActivity());
    activity.showConferenceFragment(true);
  }

  @Override
  public void onShrinkAnimationComplete() {
    InCallPresenter.getInstance().onShrinkAnimationComplete();
  }

  private void maybeStartSearch(DialerCall call, boolean isPrimary) {
    // no need to start search for conference calls which show generic info.
    if (call != null && (!call.isConferenceCall()
            //SPRD: for bug887066 905912
            || (call.isConferenceCall() && call.getChildCallIds() != null && call.getChildCallIds().size() < 1))) {
      startContactInfoSearch(call, isPrimary, call.getState() == DialerCall.State.INCOMING);
    }
  }

  /** Starts a query for more contact data for the save primary and secondary calls. */
  private void startContactInfoSearch(
      final DialerCall call, final boolean isPrimary, boolean isIncoming) {
    final ContactInfoCache cache = ContactInfoCache.getInstance(context);

    cache.findInfo(call, isIncoming, new ContactLookupCallback(this, isPrimary));
  }

  private void onContactInfoComplete(String callId, ContactCacheEntry entry, boolean isPrimary) {
    final boolean entryMatchesExistingCall =
        (isPrimary && primary != null && TextUtils.equals(callId, primary.getId()))
            || (!isPrimary && secondary != null && TextUtils.equals(callId, secondary.getId()));
    if (entryMatchesExistingCall) {
      updateContactEntry(entry, isPrimary);
    // UNISOC: add for bug941546
    } else if (InCallUiUtils.shouldUpdateConferenceUIWithOneParticipant(context) &&
            isPrimary && primary != null && primary.isConferenceCall() && primary.getChildCallIds() != null
            && primary.getChildCallIds().size() == 1 && TextUtils.equals(callId,primary.getChildCallIds().get(0))) {
      updatePrimaryDisplayInfo();
    }  else {
      LogUtil.e(
          "CallCardPresenter.onContactInfoComplete",
          "dropping stale contact lookup info for " + callId);
    }

    final DialerCall call = CallList.getInstance().getCallById(callId);
    if (call != null) {
      call.getLogState().contactLookupResult = entry.contactLookupResult;
    }
    if (entry.lookupUri != null) {
      CallerInfoUtils.sendViewNotification(context, entry.lookupUri);
    }
  }

  private void onImageLoadComplete(String callId, ContactCacheEntry entry) {
    if (getUi() == null) {
      return;
    }

    if (entry.photo != null) {
      if (primary != null && callId.equals(primary.getId())) {
        updateContactEntry(entry, true /* isPrimary */);
      } else if (secondary != null && callId.equals(secondary.getId())) {
        updateContactEntry(entry, false /* isPrimary */);
      }
    }
  }

  private void updateContactEntry(ContactCacheEntry entry, boolean isPrimary) {
    if (isPrimary) {
      primaryContactInfo = entry;
      updatePrimaryDisplayInfo();
    } else {
      secondaryContactInfo = entry;
      updateSecondaryDisplayInfo();
    }
  }

  /**
   * Get the highest priority call to display. Goes through the calls and chooses which to return
   * based on priority of which type of call to display to the user. Callers can use the "ignore"
   * feature to get the second best call by passing a previously found primary call as ignore.
   *
   * @param ignore A call to ignore if found.
   */
  private DialerCall getCallToDisplay(
      CallList callList, DialerCall ignore, boolean skipDisconnected) {
    // Active calls come second.  An active call always gets precedent.
    DialerCall retval = callList.getActiveCall();
    if (retval != null && retval != ignore) {
      return retval;
    }

    // Sometimes there is intemediate state that two calls are in active even one is about
    // to be on hold.
    retval = callList.getSecondActiveCall();
    if (retval != null && retval != ignore) {
      return retval;
    }

    // Disconnected calls get primary position if there are no active calls
    // to let user know quickly what call has disconnected. Disconnected
    // calls are very short lived.
    if (!skipDisconnected) {
      retval = callList.getDisconnectingCall();
      if (retval != null && retval != ignore) {
        return retval;
      }
      retval = callList.getDisconnectedCall();
      if (retval != null && retval != ignore) {
        return retval;
      }
    }

    // Then we go to background call (calls on hold)
    retval = callList.getBackgroundCall();
    if (retval != null && retval != ignore) {
      return retval;
    }

    // Lastly, we go to a second background call.
    retval = callList.getSecondBackgroundCall();

    return retval;
  }

  private void updatePrimaryDisplayInfo() {
    if (inCallScreen == null) {
      // TODO: May also occur if search result comes back after ui is destroyed. Look into
      // removing that case completely.
      LogUtil.v(
          "CallCardPresenter.updatePrimaryDisplayInfo",
          "updatePrimaryDisplayInfo called but ui is null!");
      return;
    }

    if (primary == null) {
      // Clear the primary display info.
      inCallScreen.setPrimary(PrimaryInfo.empty());
      return;
    }

    // Hide the contact photo if we are in a video call and the incoming video surface is
    // showing.
    boolean showContactPhoto =
        !VideoCallPresenter.showIncomingVideo(primary.getVideoState(), primary.getState());

    // DialerCall placed through a work phone account.
    boolean hasWorkCallProperty = primary.hasProperty(PROPERTY_ENTERPRISE_CALL);

    //UNISOC:add for bug1014616
    boolean primaryisVolteCall = false;
    if (ImsManagerEx.isImsRegisteredForPhone(InCallUiUtils.getPhoneIdByAccountHandle(context, primary.getAccountHandle()))) {
      primaryisVolteCall = true;
    }

    MultimediaData multimediaData = null;
    if (primary.getEnrichedCallSession() != null) {
      multimediaData = primary.getEnrichedCallSession().getMultimediaData();
    }

    /* SPRD Feature Porting: Show fdn list name in incallui feature. @{ */
    int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    if (primary.getAccountHandle() != null) {
      subId = InCallUiUtils.getSubIdForPhoneAccountHandle(context, primary.getAccountHandle());
    }
    /* @} */

    /*SPRD: add for feature FL1000062299 {@*/
    //UNISOC:add for bug949977 & 968871
    if (InCallUiUtils.shouldUpdateConferenceUIWithOneParticipant(context)
            && primary.isConferenceCall() && primary.getState() != DialerCall.State.INCOMING) {
      LogUtil.v(
              "CallCardPresenter.updatePrimaryDisplayInfo",
              "update primary display info for conference call when there is only one child.");
      // UNISOC: add for bug941546
      ContactInfoCache.ContactCacheEntry cinfo = getCallerInfo(CallList.getInstance());
      if (primary.getChildCallIds() != null && primary.getChildCallIds().size() == 1
              && cinfo != null) {
        primaryContactInfo = cinfo;
        // UNISOC: modify for bug965735
        String name = getNameForCall(primaryContactInfo, subId);
        String number = getNumberForCall(primaryContactInfo);

        boolean isChildNumberShown = !TextUtils.isEmpty(primary.getChildNumber());
        boolean isForwardedNumberShown = !TextUtils.isEmpty(primary.getLastForwardedNumber());
        boolean isCallSubjectShown = shouldShowCallSubject(primary);

        boolean nameIsNumber = (name != null && name.equals(primaryContactInfo.number));
        // UNISOC: modify for bug954922
        String[] callerIds = (String[]) CallList.getInstance().getAllConferenceCall().getChildCallIds().toArray(new String[0]);
        DialerCall participantCall = CallList.getInstance().getCallById(callerIds[0]);
        if (nameIsNumber) {
            name = primaryContactInfo.number;
        }
        // DialerCall with caller that is a work contact.
        boolean isWorkContact = (primaryContactInfo.userType == ContactsUtils.USER_TYPE_WORK);
        inCallScreen.setPrimary(
                PrimaryInfo.builder()
                        .setNumber(number)
                        .setName(name)
                        .setNameIsNumber(nameIsNumber)
                        .setLocation(shouldShowLocationAsLabel(nameIsNumber, primaryContactInfo.shouldShowLocation)
                                        ? primaryContactInfo.location
                                        : null)
                        .setLabel(isChildNumberShown || isCallSubjectShown ? null : primaryContactInfo.label)
                        .setPhoto(primaryContactInfo.photo)
                        .setPhotoType(primaryContactInfo.photoType)
                        .setIsSipCall(primaryContactInfo.isSipCall)
                        .setIsContactPhotoShown(showContactPhoto)
                        .setIsWorkCall(hasWorkCallProperty || isWorkContact)
                        .setIsSpam(primary.isSpam())
                        .setIsLocalContact(false)
                        //UNISOC: add for bug912215
                        .setIsVoiceMailNumber(participantCall != null ? participantCall.isVoiceMailNumber() : primary.isVoiceMailNumber())
                        //UNISOC: add for bug954922 949977
                        .setIsConference(false)
                        .setAnsweringDisconnectsOngoingCall(primary.answeringDisconnectsForegroundVideoCall())
                        .setShouldShowLocation(shouldShowLocation())
                        .setShowInCallButtonGrid(true)
                        .setContactInfoLookupKey(primaryContactInfo.lookupKey)
                        .setMultimediaData(multimediaData)
                        .setNumberPresentation(primary.getNumberPresentation())
                        .setSubId(subId)
                        .build());
        // UNISOC: modify for bug954922
      } else if (primary.getChildCallIds() != null && primary.getChildCallIds().size() == 1 && cinfo == null) {
        return;
      } else {
        if (getConferenceString(primary).equals(context.getString(R.string.unknown))
                || primary.getState() == DialerCall.State.DISCONNECTING
                || primary.getState() == DialerCall.State.DISCONNECTED) {
          return;
        }
        // UNISOC: add for bug940948
        String number = null;
        String contactName = "";
        boolean nameIsNumber = false;
        String location = null;
        if (primaryContactInfo != null
                && primary.getChildCallIds().size() < 1) {
          number = primary.getNumber();
          // UNISOC: modify for bug965735
          contactName = getNameForCall(primaryContactInfo, subId);
          nameIsNumber = contactName != null
                  && contactName.equals(primaryContactInfo.number);
          location = shouldShowLocationAsLabel(nameIsNumber, primaryContactInfo.shouldShowLocation)
                  ? primaryContactInfo.location: null;
        }
        if (!(!context.getResources().getBoolean(R.bool.config_is_show_conference_participant_label) && primary.isConferenceCall() && primary.getChildCallIds().size() < 1 && primaryisVolteCall)) {
          inCallScreen.setPrimary(
                  PrimaryInfo.builder()
                          .setNumber(number) /* number */
                          .setName(getConferenceString(primary))
                          .setContactName(contactName)
                          .setNameIsNumber(false) /* nameIsNumber */
                          .setLocation(location) /* location */
                          .setLabel(null) /* label */
                          .setPhoto(null) /* photo */
                          .setPhotoType(ContactPhotoType.DEFAULT_PLACEHOLDER)
                          .setIsSipCall(false) /* isSipCall */
                          .setIsContactPhotoShown(showContactPhoto)
                          .setIsWorkCall(hasWorkCallProperty)
                          .setIsSpam(false) /* isSpam */
                          .setIsLocalContact(false)
                          //UNISOC: add for bug912215
                          .setIsVoiceMailNumber(false)
                          //UNISOC: add for bug954922 949977
                          .setIsConference(true)
                          .setAnsweringDisconnectsOngoingCall(false) /* answeringDisconnectsOngoingCall */
                          .setShouldShowLocation(shouldShowLocation())
                          .setShowInCallButtonGrid(true)
                          .setContactInfoLookupKey(null) /* contactInfoLookupKey */
                          .setMultimediaData(null) /* enrichedCallMultimediaData */
                          .setNumberPresentation(primary.getNumberPresentation())
                          .setSubId(subId)
                          .build());
        }
      }
    }
    /* @} */
    else if (primary.isConferenceCall()) {
      LogUtil.v(
          "CallCardPresenter.updatePrimaryDisplayInfo",
          "update primary display info for conference call.");

      String number = null;
      String contactName = "";
      boolean nameIsNumber = false;
      String location = null;
      //SPRD: add for bug867870, show contactName or number for conference participant
      if (primaryContactInfo != null
                   && primary.getChildCallIds().size() < 1) {
        number = primary.getNumber();
        // UNISOC: modify for bug965735
        contactName = getNameForCall(primaryContactInfo, subId);
        nameIsNumber = contactName != null
                && contactName.equals(primaryContactInfo.number);
        location = shouldShowLocationAsLabel(nameIsNumber, primaryContactInfo.shouldShowLocation)
                ? primaryContactInfo.location: null;
      }
      if (!(!context.getResources().getBoolean(R.bool.config_is_show_conference_participant_label) && primary.isConferenceCall() && primary.getChildCallIds().size() < 1 && primaryisVolteCall)) {
        inCallScreen.setPrimary(
                PrimaryInfo.builder()
                        .setNumber(number)
                        .setName(
                                CallerInfoUtils.getConferenceString(
                                        context, primary.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE)))
                        .setContactName(contactName)
                        .setNameIsNumber(false)
                        .setLocation(location)
                        .setPhotoType(ContactPhotoType.DEFAULT_PLACEHOLDER)
                        .setIsSipCall(false)
                        .setIsContactPhotoShown(showContactPhoto)
                        .setIsWorkCall(hasWorkCallProperty)
                        .setIsSpam(false)
                        .setIsLocalContact(false)
                        //UNISOC: add for bug912215
                        .setIsVoiceMailNumber(false)
                        //UNISOC: add for bug954922 949977
                        .setIsConference(true)
                        .setAnsweringDisconnectsOngoingCall(false)
                        .setShouldShowLocation(shouldShowLocation())
                        .setShowInCallButtonGrid(true)
                        .setNumberPresentation(primary.getNumberPresentation())
                        .setSubId(subId) // SPRD Feature Porting: Show fdn list name in incallui feature.
                        .build());
      }
    } else if (primaryContactInfo != null) {
      LogUtil.v(
          "CallCardPresenter.updatePrimaryDisplayInfo",
          "update primary display info for " + primaryContactInfo);

      // UNISOC: modify for bug965735
      String name = getNameForCall(primaryContactInfo, subId);
      String number;

      boolean isChildNumberShown = !TextUtils.isEmpty(primary.getChildNumber());
      boolean isForwardedNumberShown = !TextUtils.isEmpty(primary.getLastForwardedNumber());
      boolean isCallSubjectShown = shouldShowCallSubject(primary);

      if (isCallSubjectShown) {
        number = null;
      } else if (isChildNumberShown) {
        number = context.getString(R.string.child_number, primary.getChildNumber());
      } else if (isForwardedNumberShown) {
        // Use last forwarded number instead of second line, if present.
        number = primary.getLastForwardedNumber();
      } else {
        number = primaryContactInfo.number;
      }

      boolean nameIsNumber = name != null && name.equals(primaryContactInfo.number);

      // DialerCall with caller that is a work contact.
      boolean isWorkContact = (primaryContactInfo.userType == ContactsUtils.USER_TYPE_WORK);
      inCallScreen.setPrimary(
          PrimaryInfo.builder()
              .setNumber(number)
              .setName(primary.updateNameIfRestricted(name))
              .setNameIsNumber(nameIsNumber)
              // UNISOC: modify for bug923423
              .setLocation(
                  shouldShowLocationAsLabel(nameIsNumber, primaryContactInfo.shouldShowLocation)
                      ? primaryContactInfo.location
                      : null)
              .setLabel(
                  isChildNumberShown || isCallSubjectShown ? null : primaryContactInfo.label)
              .setPhoto(primaryContactInfo.photo)
              .setPhotoType(primaryContactInfo.photoType)
              .setIsSipCall(primaryContactInfo.isSipCall)
              .setIsContactPhotoShown(showContactPhoto)
              //UNISOC: add for bug912215
              .setIsVoiceMailNumber(primary.isVoiceMailNumber())
              //UNISOC: add for bug954922 949977
              .setIsConference(false)
              .setIsWorkCall(hasWorkCallProperty || isWorkContact)
              .setIsSpam(primary.isSpam())
              .setIsLocalContact(primaryContactInfo.isLocalContact())
              .setAnsweringDisconnectsOngoingCall(primary.answeringDisconnectsForegroundVideoCall())
              .setShouldShowLocation(shouldShowLocation())
              .setContactInfoLookupKey(primaryContactInfo.lookupKey)
              .setMultimediaData(multimediaData)
              .setShowInCallButtonGrid(true)
              .setNumberPresentation(primary.getNumberPresentation())
                  .setSubId(subId) // SPRD Feature Porting: Show fdn list name in incallui feature.
              .build());
    } else {
      // Clear the primary display info.
      inCallScreen.setPrimary(PrimaryInfo.empty());
    }

    if (isInCallScreenReady) {
      inCallScreen.showLocationUi(getLocationFragment());
    } else {
      LogUtil.i("CallCardPresenter.updatePrimaryDisplayInfo", "UI not ready, not showing location");
    }
  }

  private static boolean shouldShowLocationAsLabel(
      boolean nameIsNumber, boolean shouldShowLocation) {
    if (nameIsNumber) {
      return true;
    }
    if (shouldShowLocation) {
      return true;
    }
    return false;
  }

  private Fragment getLocationFragment() {
    if (!shouldShowLocation()) {
      return null;
    }
    LogUtil.i("CallCardPresenter.getLocationFragment", "returning location fragment");
    return callLocation.getLocationFragment(context);
  }

  private boolean shouldShowLocation() {
    if (!ConfigProviderBindings.get(context)
        .getBoolean(CONFIG_ENABLE_EMERGENCY_LOCATION, CONFIG_ENABLE_EMERGENCY_LOCATION_DEFAULT)) {
      LogUtil.i("CallCardPresenter.getLocationFragment", "disabled by config.");
      return false;
    }
    if (!isPotentialEmergencyCall()) {
      LogUtil.i("CallCardPresenter.getLocationFragment", "shouldn't show location");
      return false;
    }
    if (!hasLocationPermission()) {
      LogUtil.i("CallCardPresenter.getLocationFragment", "no location permission.");
      return false;
    }
    if (isBatteryTooLowForEmergencyLocation()) {
      LogUtil.i("CallCardPresenter.getLocationFragment", "low battery.");
      return false;
    }
    if (ActivityCompat.isInMultiWindowMode(inCallScreen.getInCallScreenFragment().getActivity())) {
      LogUtil.i("CallCardPresenter.getLocationFragment", "in multi-window mode");
      return false;
    }
    if (primary.isVideoCall()) {
      LogUtil.i("CallCardPresenter.getLocationFragment", "emergency video calls not supported");
      return false;
    }
    if (!callLocation.canGetLocation(context)) {
      LogUtil.i("CallCardPresenter.getLocationFragment", "can't get current location");
      return false;
    }
    return true;
  }

  private boolean isPotentialEmergencyCall() {
    if (isOutgoingEmergencyCall(primary)) {
      LogUtil.i("CallCardPresenter.shouldShowLocation", "new emergency call");
      return true;
    } else if (isIncomingEmergencyCall(primary)) {
      LogUtil.i("CallCardPresenter.shouldShowLocation", "potential emergency callback");
      return true;
    } else if (isIncomingEmergencyCall(secondary)) {
      LogUtil.i("CallCardPresenter.shouldShowLocation", "has potential emergency callback");
      return true;
    }
    return false;
  }

  private static boolean isOutgoingEmergencyCall(@Nullable DialerCall call) {
    return call != null && !call.isIncoming() && call.isEmergencyCall();
  }

  private static boolean isIncomingEmergencyCall(@Nullable DialerCall call) {
    return call != null && call.isIncoming() && call.isPotentialEmergencyCallback();
  }

  private boolean hasLocationPermission() {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        == PackageManager.PERMISSION_GRANTED;
  }

  private boolean isBatteryTooLowForEmergencyLocation() {
    Intent batteryStatus =
        context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    if (status == BatteryManager.BATTERY_STATUS_CHARGING
        || status == BatteryManager.BATTERY_STATUS_FULL) {
      // Plugged in or full battery
      return false;
    }
    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    float batteryPercent = (100f * level) / scale;
    long threshold =
        ConfigProviderBindings.get(context)
            .getLong(
                CONFIG_MIN_BATTERY_PERCENT_FOR_EMERGENCY_LOCATION,
                CONFIG_MIN_BATTERY_PERCENT_FOR_EMERGENCY_LOCATION_DEFAULT);
    LogUtil.i(
        "CallCardPresenter.isBatteryTooLowForEmergencyLocation",
        "percent charged: " + batteryPercent + ", min required charge: " + threshold);
    return batteryPercent < threshold;
  }

  private void updateSecondaryDisplayInfo() {
    if (inCallScreen == null) {
      return;
    }

    if (secondary == null) {
      // Clear the secondary display info.
      inCallScreen.setSecondary(SecondaryInfo.builder().setIsFullscreen(isFullscreen).build());
      return;
    }

    if (secondary.isMergeInProcess()) {
      LogUtil.i(
          "CallCardPresenter.updateSecondaryDisplayInfo",
          "secondary call is merge in process, clearing info");
      inCallScreen.setSecondary(SecondaryInfo.builder().setIsFullscreen(isFullscreen).build());
      return;
    }
    /* SPRD DSDA. @{ */
    int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    if (secondary.getAccountHandle() != null) {
      subId = InCallUiUtils.getSubIdForPhoneAccountHandle(context, secondary.getAccountHandle());
    }
    /* @} */
    //UNISOC:add for bug1014616
    boolean secondaryisVolteCall = false;
    if (ImsManagerEx.isImsRegisteredForPhone(InCallUiUtils.getPhoneIdByAccountHandle(context, secondary.getAccountHandle()))) {
      secondaryisVolteCall = true;
    }

    /*SPRD: add for feature FL1000062299 {@*/
    if (InCallUiUtils.shouldUpdateConferenceUIWithOneParticipant(context)
            && secondary.isConferenceCall() && secondaryContactInfo != null) {
      String name = "";
      if (secondary.getChildCallIds() != null && secondary.getChildCallIds().size() == 1
              && getCallerInfo(CallList.getInstance()) != null) {
        secondaryContactInfo = getCallerInfo(CallList.getInstance());
        // UNISOC: modify for bug965735
        name = getNameForCall(secondaryContactInfo, subId);
      } else { //modify for bug940938
        if (!context.getResources().getBoolean(R.bool.config_is_show_conference_participant_label) && secondary.isConferenceCall() && secondary.getChildCallIds().size() < 1 && secondaryisVolteCall) {
          name = getNameForCall(secondaryContactInfo, subId);
        } else {
          name = getConferenceString(secondary);
        }
      }
      LogUtil.i("CallCardPresenter.updateSecondaryDisplayInfo 1 ", " name:" + name);
      boolean nameIsNumber = name != null && name.equals(secondaryContactInfo.number);
      SecondaryInfo secondaryInfo =
              SecondaryInfo.builder()
                      .setShouldShow(true)
                      .setName(name)
                      .setNameIsNumber(nameIsNumber)
                      .setLabel(secondaryContactInfo.label)
                      .setProviderLabel(secondary.getCallProviderLabel())
                      .setIsConference(false)
                      .setIsVideoCall(secondary.isVideoCall())
                      .setIsFullscreen(isFullscreen)
                      .build();
        secondaryInfo.setSecondaryCallState(secondary.getState());
        secondaryInfo.setSecondaryColor(getSecondaryColor());
        secondaryInfo.setSubId(subId);
        secondaryInfo.setPrimaryCallState(primary != null ? primary.getState() : DialerCall.State.IDLE);
      inCallScreen.setSecondary(secondaryInfo);
    }
    /* @} */
    else if (secondary.isConferenceCall()) {
      String name = "";
      if (!context.getResources().getBoolean(R.bool.config_is_show_conference_participant_label) && secondary.isConferenceCall() && secondary.getChildCallIds().size() < 1 && secondaryisVolteCall) {
        name = getNameForCall(secondaryContactInfo, subId);
      } else{
        name =CallerInfoUtils.getConferenceString(context, secondary.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE));
      }
      /* SPRD DSDA. @{ */
      SecondaryInfo secondaryInfo =
          SecondaryInfo.builder()
              .setShouldShow(true)
              .setName(name)
               //UNISOC:modify for bug985582    + " " + (secondary.getNumber() != null ? secondary.getNumber() : "")) /* SPRD: Fix bug692158 */
              .setNameIsNumber(false)
              .setLabel(null) /* label */
              .setProviderLabel(secondary.getCallProviderLabel())
              .setIsConference(true)
              .setIsVideoCall(secondary.isVideoCall())
              .setIsFullscreen(isFullscreen)
              .build();
        secondaryInfo.setSecondaryCallState(secondary.getState());
        secondaryInfo.setSecondaryColor(getSecondaryColor());
        secondaryInfo.setSubId(subId);
        secondaryInfo.setPrimaryCallState(primary != null ? primary.getState() : DialerCall.State.IDLE);
      inCallScreen.setSecondary(secondaryInfo);
    } else if (secondaryContactInfo != null) {
      LogUtil.v("CallCardPresenter.updateSecondaryDisplayInfo", "" + secondaryContactInfo);
      // UNISOC: modify for bug965735
      String name = getNameForCall(secondaryContactInfo, subId);
      boolean nameIsNumber = name != null && name.equals(secondaryContactInfo.number);
      /* SPRD DSDA. @{ */
      SecondaryInfo secondaryInfo =
          SecondaryInfo.builder()
              .setShouldShow(true)
              .setName(secondary.updateNameIfRestricted(name))
              .setNameIsNumber(nameIsNumber)
              .setLabel(secondaryContactInfo.label)
              .setProviderLabel(secondary.getCallProviderLabel())
              .setIsConference(false)
              .setIsVideoCall(secondary.isVideoCall())
              .setIsFullscreen(isFullscreen)
              .build();
        secondaryInfo.setSecondaryCallState(secondary.getState());
        secondaryInfo.setSecondaryColor(getSecondaryColor());
        secondaryInfo.setSubId(subId);
        secondaryInfo.setPrimaryCallState(primary != null ? primary.getState() : DialerCall.State.IDLE);
      inCallScreen.setSecondary(secondaryInfo);
    } else {
      // Clear the secondary display info.
      inCallScreen.setSecondary(SecondaryInfo.builder().setIsFullscreen(isFullscreen).build());
    }
  }

  /** Returns the gateway number for any existing outgoing call. */
  private String getGatewayNumber() {
    if (hasOutgoingGatewayCall()) {
      return DialerCall.getNumberFromHandle(primary.getGatewayInfo().getGatewayAddress());
    }
    return null;
  }

  /**
   * Returns the label (line of text above the number/name) for any given call. For example,
   * "calling via [Account/Google Voice]" for outgoing calls.
   */
  private String getConnectionLabel() {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
        != PackageManager.PERMISSION_GRANTED) {
      return null;
    }
    StatusHints statusHints = primary.getStatusHints();
    if (statusHints != null && !TextUtils.isEmpty(statusHints.getLabel())) {
      return statusHints.getLabel().toString();
    }

    if (hasOutgoingGatewayCall() && getUi() != null) {
      // Return the label for the gateway app on outgoing calls.
      final PackageManager pm = context.getPackageManager();
      try {
        ApplicationInfo info =
            pm.getApplicationInfo(primary.getGatewayInfo().getGatewayProviderPackageName(), 0);
        return pm.getApplicationLabel(info).toString();
      } catch (PackageManager.NameNotFoundException e) {
        LogUtil.e("CallCardPresenter.getConnectionLabel", "gateway Application Not Found.", e);
        return null;
      }
    }
    return primary.getCallProviderLabel();
  }

  private Drawable getCallStateIcon() {
    // Return connection icon if one exists.
    StatusHints statusHints = primary.getStatusHints();
    if (statusHints != null && statusHints.getIcon() != null) {
      Drawable icon = statusHints.getIcon().loadDrawable(context);
      if (icon != null) {
        return icon;
      }
    }

    return null;
  }

  private boolean hasOutgoingGatewayCall() {
    // We only display the gateway information while STATE_DIALING so return false for any other
    // call state.
    // TODO: mPrimary can be null because this is called from updatePrimaryDisplayInfo which
    // is also called after a contact search completes (call is not present yet).  Split the
    // UI update so it can receive independent updates.
    if (primary == null) {
      return false;
    }
    return DialerCall.State.isDialing(primary.getState())
        && primary.getGatewayInfo() != null
        && !primary.getGatewayInfo().isEmpty();
  }

  /* UNISOC: add for bug965735 @{ */
  /** Gets the name to display for the call. */
  private String getNameForCall(ContactCacheEntry contactInfo, int subId) {
    String preferredName =
        ContactDisplayUtils.getPreferredDisplayName(
            contactInfo.namePrimary, contactInfo.nameAlternative, contactsPreferences);
    boolean isSupportFdnListName = DisplayFdnHelper.getInstance(context).isSupportFdnListName(subId);
    if (isSupportFdnListName) {
      String preferredFdnName =  DisplayFdnHelper.getInstance(context).getFDNListName(contactInfo.number, subId);
      if(!TextUtils.isEmpty(preferredFdnName)){  //add for bug973281
        preferredName = preferredFdnName;
      }
    }
    if (TextUtils.isEmpty(preferredName)) {
      return contactInfo.number;
    }
    return preferredName;
  }
  /* @} */

  @Override
  public void onSecondaryInfoClicked() {
    if (secondary == null) {
      LogUtil.e(
          "CallCardPresenter.onSecondaryInfoClicked",
          "secondary info clicked but no secondary call.");
      return;
    }

    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.IN_CALL_SWAP_SECONDARY_BUTTON_PRESSED,
            primary.getUniqueCallId(),
            primary.getTimeAddedMs());
    LogUtil.i(
        "CallCardPresenter.onSecondaryInfoClicked", "swapping call to foreground: " + secondary);
    secondary.unhold();
  }

  @Override
  public void onEndCallClicked() {
    LogUtil.i("CallCardPresenter.onEndCallClicked", "disconnecting call: " + primary);
    if (primary != null) {
      primary.disconnect();
    }
    PostCall.onDisconnectPressed(context);
  }

  /**
   * Handles a change to the fullscreen mode of the in-call UI.
   *
   * @param isFullscreenMode {@code True} if the in-call UI is entering full screen mode.
   */
  @Override
  public void onFullscreenModeChanged(boolean isFullscreenMode) {
    isFullscreen = isFullscreenMode;
    if (inCallScreen == null) {
      return;
    }
    maybeShowManageConferenceCallButton();
  }

  private boolean isPrimaryCallActive() {
    return primary != null && primary.getState() == DialerCall.State.ACTIVE;
  }

  private boolean shouldShowEndCallButton(DialerCall primary, int callState) {
    if (primary == null) {
      return false;
    }
    if ((!DialerCall.State.isConnectingOrConnected(callState)
            && callState != DialerCall.State.DISCONNECTING
            && callState != DialerCall.State.DISCONNECTED)
        || callState == DialerCall.State.INCOMING) {
      return false;
    }
    if (this.primary.getVideoTech().getSessionModificationState()
        == SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
      return false;
    }
    return true;
  }

  @Override
  public void onInCallScreenResumed() {
    updatePrimaryDisplayInfo();

    if (shouldSendAccessibilityEvent) {
      handler.postDelayed(sendAccessibilityEventRunnable, ACCESSIBILITY_ANNOUNCEMENT_DELAY_MILLIS);
    }
  }

  @Override
  public void onInCallScreenPaused() {}

  static boolean sendAccessibilityEvent(Context context, InCallScreen inCallScreen) {
    AccessibilityManager am =
        (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    if (!am.isEnabled()) {
      LogUtil.w("CallCardPresenter.sendAccessibilityEvent", "accessibility is off");
      return false;
    }
    if (inCallScreen == null) {
      LogUtil.w("CallCardPresenter.sendAccessibilityEvent", "incallscreen is null");
      return false;
    }
    Fragment fragment = inCallScreen.getInCallScreenFragment();
    if (fragment == null || fragment.getView() == null || fragment.getView().getParent() == null) {
      LogUtil.w("CallCardPresenter.sendAccessibilityEvent", "fragment/view/parent is null");
      return false;
    }

    DisplayManager displayManager =
        (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
    boolean screenIsOn = display.getState() == Display.STATE_ON;
    LogUtil.d("CallCardPresenter.sendAccessibilityEvent", "screen is on: %b", screenIsOn);
    if (!screenIsOn) {
      return false;
    }

    AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT);
    inCallScreen.dispatchPopulateAccessibilityEvent(event);
    View view = inCallScreen.getInCallScreenFragment().getView();
    view.getParent().requestSendAccessibilityEvent(view, event);
    return true;
  }

  private void maybeSendAccessibilityEvent(
      InCallState oldState, final InCallState newState, boolean primaryChanged) {
    shouldSendAccessibilityEvent = false;
    if (context == null) {
      return;
    }
    final AccessibilityManager am =
        (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    if (!am.isEnabled()) {
      return;
    }
    // Announce the current call if it's new incoming/outgoing call or primary call is changed
    // due to switching calls between two ongoing calls (one is on hold).
    if ((oldState != InCallState.OUTGOING && newState == InCallState.OUTGOING)
        || (oldState != InCallState.INCOMING && newState == InCallState.INCOMING)
        || primaryChanged) {
      LogUtil.i(
          "CallCardPresenter.maybeSendAccessibilityEvent", "schedule accessibility announcement");
      shouldSendAccessibilityEvent = true;
      handler.postDelayed(sendAccessibilityEventRunnable, ACCESSIBILITY_ANNOUNCEMENT_DELAY_MILLIS);
    }
  }

  /**
   * Determines whether the call subject should be visible on the UI. For the call subject to be
   * visible, the call has to be in an incoming or waiting state, and the subject must not be empty.
   *
   * @param call The call.
   * @return {@code true} if the subject should be shown, {@code false} otherwise.
   */
  private boolean shouldShowCallSubject(DialerCall call) {
    if (call == null) {
      return false;
    }

    boolean isIncomingOrWaiting =
        primary.getState() == DialerCall.State.INCOMING
            || primary.getState() == DialerCall.State.CALL_WAITING;
    return isIncomingOrWaiting
        && !TextUtils.isEmpty(call.getCallSubject())
        && call.getNumberPresentation() == TelecomManager.PRESENTATION_ALLOWED
        && call.isCallSubjectSupported();
  }

  /**
   * Determines whether the "note sent" toast should be shown. It should be shown for a new outgoing
   * call with a subject.
   *
   * @param call The call
   * @return {@code true} if the toast should be shown, {@code false} otherwise.
   */
  private boolean shouldShowNoteSentToast(DialerCall call) {
    return call != null
        && hasCallSubject(call)
        && (call.getState() == DialerCall.State.DIALING
            || call.getState() == DialerCall.State.CONNECTING);
  }

  private InCallScreen getUi() {
    return inCallScreen;
  }
  /**
   * SPRD: add for DSDA
   */
  private int getSecondaryColor () {
    int highlightColor = PhoneAccount.NO_HIGHLIGHT_COLOR;
    if (secondary != null && secondary.getAccountHandle() != null) {
      final TelecomManager tm = (TelecomManager)
              context.getSystemService(Context.TELECOM_SERVICE);
      if (tm != null) {
        final PhoneAccount account = tm.getPhoneAccount(secondary.getAccountHandle());
        if (account != null) {
          highlightColor = account.getHighlightColor();
        }
      }
    }
    return highlightColor;
  }

  /**
   * SPRD: add for DSDA
   * @return CallToDisplayForDADA.
   */
  private DialerCall getCallToDisplayForDADA (CallList callList, DialerCall ignore) {

    if (ignore == null) {
      return null;
    }
    if (ignore.getState() == DialerCall.State.INCOMING) {
      if (ImsManagerEx.isDualVoLTERegistered()
              && callList.getOtherRingingCall(ignore) != null) {
        return callList.getOtherRingingCall(ignore);
      } else if (ImsManagerEx.isDualVoLTERegistered()
              && callList.getFirstCallWithState(DialerCall.State.DIALING) != null) {
        return callList.getFirstCallWithState(DialerCall.State.DIALING);
      } else {
        return null;
      }
    } else if (ignore.getState() == DialerCall.State.DIALING) {
      if (ImsManagerEx.isDualVoLTERegistered()
              && callList.getIncomingCall()!= null) {
        return callList.getIncomingCall();
      } else {
        return getCallToDisplay(callList, ignore, true);
      }
    } else if (ignore.getState() == DialerCall.State.ACTIVE
            || ignore.getState() == DialerCall.State.ONHOLD) {
      DialerCall retval = callList.getActiveCall();
      if (retval != null && retval != ignore) {
        return retval;
      }
      retval = callList.getBackgroundCall();
      if (retval != null && retval != ignore) {
        return retval;
      }
      retval = callList.getFirstCallWithState(DialerCall.State.DIALING);
      if (retval != null && retval != ignore) {
        return retval;
      }
    }
    return getCallToDisplay(callList, ignore, true);
  }

  /**
   * SPRD: add for DSDA
   */
  @Override
  public void swapSecondaryToForeground() {
    if (secondary == null) {
      LogUtil.e(
              "CallCardPresenter.swapSecondaryToForeground",
              "secondary info clicked but no secondary call.");
      return;
    }
    /* SPRD: Invalid secondary info clicked when secondary call state is onhold. */
    if (secondary.getState() == DialerCall.State.ONHOLD) {
      LogUtil.d(
              "CallCardPresenter.swapSecondaryToForeground",
              "invalid secondary info clicked when secondary call state is onhold.");
      return;
    }
    /* @} */
    LogUtil.i(
            "CallCardPresenter.swapSecondaryToForeground", "swapping call to foreground: " + secondary);
    CallList.getInstance().markAsUserPrimaryCall(secondary);
  }
  /**
   * SPRD: add for DSDA, bug900867
   */
  private void updateSecondaryCallState () {
    if (getUi() != null && secondary != null) {
      getUi().updateSecondaryCallState(secondary.getState());
    }
  }

  /** Callback for contact lookup. */
  public static class ContactLookupCallback implements ContactInfoCacheCallback {

    private final WeakReference<CallCardPresenter> callCardPresenter;
    private final boolean isPrimary;

    public ContactLookupCallback(CallCardPresenter callCardPresenter, boolean isPrimary) {
      this.callCardPresenter = new WeakReference<CallCardPresenter>(callCardPresenter);
      this.isPrimary = isPrimary;
    }

    @Override
    public void onContactInfoComplete(String callId, ContactCacheEntry entry) {
      CallCardPresenter presenter = callCardPresenter.get();
      if (presenter != null) {
        presenter.onContactInfoComplete(callId, entry, isPrimary);
      }
    }

    @Override
    public void onImageLoadComplete(String callId, ContactCacheEntry entry) {
      CallCardPresenter presenter = callCardPresenter.get();
      if (presenter != null) {
        presenter.onImageLoadComplete(callId, entry);
      }
    }
  }

  /*SPRD: add for feature FL1000062299 {@*/
  private ContactInfoCache.ContactCacheEntry getCallerInfo(CallList callList) {
    if (getUi() == null || callList == null || callList.getAllConferenceCall() == null
            || callList.getAllConferenceCall().getChildCallIds() == null) {
      Log.w(this, "There is an error when update!");
      return null;
    }
    String[] callerIds = null;
    callerIds = (String[]) callList.getAllConferenceCall().getChildCallIds().toArray(new String[0]);
    // UNISOC: modify for bug954922 949977
    DialerCall participantCall = callList.getCallById(callerIds[0]);
    DialerCall childCall = callList.getCallWithState(DialerCall.State.CONFERENCED, 1);
    
    if (childCall != null || participantCall == null) {
      this.isConference = true;
      return null;
    }

    final ContactCacheEntry contactCache = ContactInfoCache.getInstance(context).
            getInfo(callerIds[0]);
    if (contactCache != null) {
      return contactCache;
    // UNISOC: add for bug941546
    } else {
      maybeStartSearch(participantCall, true);
    }
    return null;
  }

  /**
   * Gets the number to display for a call.
   */
  String getNumberForCall(ContactCacheEntry contactInfo) {
    // If the name is empty, we use the number for the name...so don't show a second
    // number in the number field
    String preferredName =
            ContactDisplayUtils.getPreferredDisplayName(
                    contactInfo.namePrimary, contactInfo.nameAlternative, contactsPreferences);
    if (TextUtils.isEmpty(preferredName)) {
      return contactInfo.location;
    }
    return contactInfo.number;
  }

  private String getConferenceString(DialerCall call) {
    boolean isGenericConference = call.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE);
    LogUtil.v("CallCardPresenter.getConferenceString", "" + isGenericConference);

    final int resId =
            isGenericConference ? R.string.generic_conference_call_name : R.string.conference_call_name;
    return context.getResources().getString(resId);
  }
  /* @} */

}
