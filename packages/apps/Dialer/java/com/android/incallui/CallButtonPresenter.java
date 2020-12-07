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

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.Trace;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.PhoneLookup;
import android.support.v4.app.Fragment;
import android.support.v4.os.UserManagerCompat;
import android.telecom.CallAudioState;
import android.telecom.PhoneAccountHandle;
import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.contacts.common.compat.CallCompat;
import com.android.dialer.binary.common.DialerApplication;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.DialerImpression.Type;
import com.android.dialer.logging.Logger;
import com.android.dialer.util.CallUtil;
import com.android.dialer.telecom.TelecomUtil;
import com.android.ims.ImsManager;
import com.android.ims.internal.ImsManagerEx;
import com.android.ims.internal.IImsServiceEx;
import com.android.ims.internal.IImsRegisterListener;
import com.android.incallui.InCallCameraManager.Listener;
import com.android.incallui.InCallPresenter.CanAddCallListener;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.audiomode.AudioModeProvider.AudioModeListener;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCall.CameraDirection;
import com.android.incallui.call.TelecomAdapter;
import com.android.incallui.incall.protocol.InCallButtonIds;
import com.android.incallui.incall.protocol.InCallButtonUi;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.multisim.SwapSimWorker;
import com.android.incallui.sprd.InCallUiUtils;
import com.android.incallui.sprd.plugin.ExplicitCallTransfer.ExplicitCallTransferHelper;
import com.android.incallui.sprd.plugin.SendSms.SendSmsHelper;
import com.android.incallui.sprd.settings.callrecording.CallRecordingContactsHelper;
import com.android.incallui.sprd.settings.callrecording.CallRecordingContactsHelper.CallRecordSettingEntity;
import com.android.incallui.sprd.settings.callrecording.RecordListFrom;
import com.android.incallui.video.impl.VideoCallFragment;
import com.android.incallui.videotech.utils.VideoUtils;
import com.android.incallui.videotech.utils.SessionModificationState;
import com.android.incallui.sprd.plugin.ConferenceNumLimit.ConferenceNumLimitHelper;
import java.util.ArrayList;
import java.util.List;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManagerEx;

import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE;

/** Logic for call buttons. */
public class CallButtonPresenter
    implements InCallStateListener,
        AudioModeListener,
        IncomingCallListener,
        InCallDetailsListener,
        CanAddCallListener,
        Listener,
        InCallButtonUiDelegate {

  private static final String KEY_AUTOMATICALLY_MUTED = "incall_key_automatically_muted";
  private static final String KEY_PREVIOUS_MUTE_STATE = "incall_key_previous_mute_state";

  private final Context context;
  private InCallButtonUi inCallButtonUi;
  private DialerCall call;
  private boolean automaticallyMuted = false;
  private boolean previousMuteState = false;
  private boolean isInCallButtonUiReady;
  private PhoneAccountHandle otherAccount;

    /* SPRD Feature Porting: Automatic record. @{ */
  private static final String AUTOMATIC_RECORDING_PREFERENCES_NAME = "automatic_recording_key";
  private boolean mAutomaticRecording;
  private boolean mIsAutomaticRecordingStart;
  /* @} */

  /* @} */  /* SPRD: Add for Volte @{ */
  private boolean mIsImsListenerRegistered;
  private IImsServiceEx mIImsServiceEx;
  private static final String MULTI_PICK_CONTACTS_ACTION = "com.android.contacts.action.MULTI_TAB_PICK";
  private static final String ADD_MULTI_CALL_AGAIN = "addMultiCallAgain";
  private static final int MAX_GROUP_CALL_NUMBER = 5;
  private static final int MIN_CONTACTS_NUMBER = 1;
  private boolean mStayVolte;
  /* @}*/
  //SPRD: add for Bug 905754
  boolean mIsSupportTxRxVideo = false;

  public CallButtonPresenter(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void onInCallButtonUiReady(InCallButtonUi ui) {
    /* SPRD : InCallUI Layout Refactor
     * @orig
    Assert.checkState(!isInCallButtonUiReady);
    */
    inCallButtonUi = ui;
    AudioModeProvider.getInstance().addListener(this);

    // register for call state changes last
    final InCallPresenter inCallPresenter = InCallPresenter.getInstance();
    inCallPresenter.addListener(this);
    inCallPresenter.addIncomingCallListener(this);
    inCallPresenter.addDetailsListener(this);
    inCallPresenter.addCanAddCallListener(this);
    inCallPresenter.getInCallCameraManager().addCameraSelectionListener(this);

    // Update the buttons state immediately for the current call
    onStateChange(InCallState.NO_CALLS, inCallPresenter.getInCallState(), CallList.getInstance());

    /*SPRD: add for VoLTE{@*/
    tryRegisterImsListener();
    /* @} */

    isInCallButtonUiReady = true;
    /*UNISOC: add for bug905849{@*/
    if (InCallPresenter.getInstance().isAutomaticallyMuted()
            && AudioModeProvider.getInstance().getAudioState().isMuted() != InCallPresenter.getInstance().isPreviousMuteState()) {
      if (inCallButtonUi == null) {
        return;
      }
      InCallPresenter.getInstance().muteNotificationUpdate(InCallPresenter.getInstance().isPreviousMuteState());
      muteClicked(InCallPresenter.getInstance().isPreviousMuteState(), false /* clickedByUser */);
    }
    InCallPresenter.getInstance().setAutomaticallyMuted(false);
    /* @} */
  }

  @Override
  public void onInCallButtonUiUnready() {
    /* SPRD : InCallUI Layout Refactor
     * @orig
    Assert.checkState(isInCallButtonUiReady);
    */
    inCallButtonUi = null;
    InCallPresenter.getInstance().removeListener(this);
    AudioModeProvider.getInstance().removeListener(this);
    InCallPresenter.getInstance().removeIncomingCallListener(this);
    InCallPresenter.getInstance().removeDetailsListener(this);
    InCallPresenter.getInstance().getInCallCameraManager().removeCameraSelectionListener(this);
    InCallPresenter.getInstance().removeCanAddCallListener(this);

    /*SPRD: add for VoLTE{@*/
    unTryRegisterImsListener();
    /* @} */

    isInCallButtonUiReady = false;
  }

  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    Trace.beginSection("CallButtonPresenter.onStateChange");

    /* SPRD Feature Porting: Automatic record. @{ */
    DialerApplication dialerApplication = (DialerApplication) context;  //mContext
    mIsAutomaticRecordingStart = dialerApplication.getIsAutomaticRecordingStart();
    // UNISOC: modify for bug905669
    try {
      final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);  //mContext
      mAutomaticRecording = sp.getBoolean(
              AUTOMATIC_RECORDING_PREFERENCES_NAME, false);
    } catch (Exception e) {
      LogUtil.e("CallButtonPresenter", "Exception:" + e.getMessage());
    }

    /* @} */

    if (newState == InCallState.OUTGOING) {
      call = callList.getOutgoingCall();
    } else if (newState == InCallState.INCALL) {
      call = callList.getActiveOrBackgroundCall();

      // When connected to voice mail, automatically shows the dialpad.
      // (On previous releases we showed it when in-call shows up, before waiting for
      // OUTGOING.  We may want to do that once we start showing "Voice mail" label on
      // the dialpad too.)
      if (oldState == InCallState.OUTGOING && call != null) {
        if (call.isVoiceMailNumber() && getActivity() != null) {
          getActivity().showDialpadFragment(true /* show */, true /* animate */);
        }
      }
    } else if (newState == InCallState.INCOMING) {
      if (getActivity() != null) {
        getActivity().showDialpadFragment(false /* show */, true /* animate */);
      }
      call = callList.getIncomingCall();
    } else {
      call = null;
    }
    // SPRD: DSDA
    if (callList.getUserPrimaryCall() != null) {
     call = callList.getUserPrimaryCall();
    }
    updateUi(newState, call);

    /* SPRD Feature Porting: Automatic record. @{
     * Using function toggleRecorder for triggering the automatic recording only once on the following conditions:
     * 1) mAutomaticRecording is true :Automatic recording switch to open In the general setting before dialing.
     * 2) Call State is ACTIVE.
     * mIsAutomaticRecordingStart is used for identifying automatic recording started or not
     * TODO: When we cancel recording in first call and add a new call ,the new call will not trigger automatic recording currently.
     * */
    if (mAutomaticRecording && !mIsAutomaticRecordingStart
            && call != null && call.getState() == DialerCall.State.ACTIVE) {  //mCall,mCall
      LogUtil.i("CallButtonPresenter", "Automatic record");
      /* SPRD: modified for bug 957870(812244) @{*/
      boolean autoCallRecording = false;
      if (call.isConferenceCall()) {
        List<String> childIds = call.getChildCallIds();
        for (String ids : childIds) {
          DialerCall call = callList.getCallById(ids);
          if (call != null) {
            autoCallRecording = checkForAutoCallSettings(getActivity(), call.getUserDialedString());
          }
          if (autoCallRecording) {
            break;
          }
        }
      } else {
        autoCallRecording = checkForAutoCallSettings(getActivity(), call.getUserDialedString());
      }
      if(autoCallRecording){
        recordClick(true);
        dialerApplication.setIsAutomaticRecordingStart(true);
      }
      /* @} */
    }
    if (newState == InCallState.NO_CALLS) {
      dialerApplication.setIsAutomaticRecordingStart(false);
    }
    /* @} */
    Trace.endSection();
  }

  /**
   * Updates the user interface in response to a change in the details of a call. Currently handles
   * changes to the call buttons in response to a change in the details for a call. This is
   * important to ensure changes to the active call are reflected in the available buttons.
   *
   * @param call The active call.
   * @param details The call details.
   */
  @Override
  public void onDetailsChanged(DialerCall call, android.telecom.Call.Details details) {
    // Only update if the changes are for the currently active call
    if (inCallButtonUi != null && call != null && call.equals(this.call)) {
      updateButtonsState(call);
    }
  }

  @Override
  public void onIncomingCall(InCallState oldState, InCallState newState, DialerCall call) {
    onStateChange(oldState, newState, CallList.getInstance());
  }

  @Override
  public void onCanAddCallChanged(boolean canAddCall) {
    if (inCallButtonUi != null && call != null) {
      updateButtonsState(call);
    }
  }

  @Override
  public void onAudioStateChanged(CallAudioState audioState) {
    if (inCallButtonUi != null) {
      inCallButtonUi.setAudioState(audioState);
    }
  }

  @Override
  public CallAudioState getCurrentAudioState() {
    return AudioModeProvider.getInstance().getAudioState();
  }

  @Override
  public void setAudioRoute(int route) {
    LogUtil.i(
        "CallButtonPresenter.setAudioRoute",
        "sending new audio route: " + CallAudioState.audioRouteToString(route));
    TelecomAdapter.getInstance().setAudioRoute(route);
  }

  /** Function assumes that bluetooth is not supported. */
  @Override
  public void toggleSpeakerphone() {
    // This function should not be called if bluetooth is available.
    CallAudioState audioState = getCurrentAudioState();
    if (0 != (CallAudioState.ROUTE_BLUETOOTH & audioState.getSupportedRouteMask())) {
      // It's clear the UI is wrong, so update the supported mode once again.
      LogUtil.e(
          "CallButtonPresenter", "toggling speakerphone not allowed when bluetooth supported.");
      inCallButtonUi.setAudioState(audioState);
      return;
    }

    if(call == null){//SPRD:add for bug894964
      LogUtil.e(
              "CallButtonPresenter", "toggleSpeakerphone mCall is null return");
      return;
    }

    int newRoute;
    if (audioState.getRoute() == CallAudioState.ROUTE_SPEAKER) {
      newRoute = CallAudioState.ROUTE_WIRED_OR_EARPIECE;
      Logger.get(context)
          .logCallImpression(
              DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_WIRED_OR_EARPIECE,
              call.getUniqueCallId(),
              call.getTimeAddedMs());
    } else {
      newRoute = CallAudioState.ROUTE_SPEAKER;
      Logger.get(context)
          .logCallImpression(
              DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_SPEAKERPHONE,
              call.getUniqueCallId(),
              call.getTimeAddedMs());
    }

    setAudioRoute(newRoute);
  }

  @Override
  public void muteClicked(boolean checked, boolean clickedByUser) {
    LogUtil.i(
        "CallButtonPresenter", "turning on mute: %s, clicked by user: %s", checked, clickedByUser);
    if (clickedByUser) {
      InCallPresenter.getInstance().setPreviousMuteState(checked);//add for bug969429
      Logger.get(context)
          .logCallImpression(
              checked
                  ? DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_MUTE
                  : DialerImpression.Type.IN_CALL_SCREEN_TURN_OFF_MUTE,
              call.getUniqueCallId(),
              call.getTimeAddedMs());
    }
    TelecomAdapter.getInstance().mute(checked);
  }

  @Override
  public void holdClicked(boolean checked) {
    if (call == null) {
      return;
    }
    if (checked) {
      LogUtil.i("CallButtonPresenter", "putting the call on hold: " + call);
      call.hold();
    } else {
      LogUtil.i("CallButtonPresenter", "removing the call from hold: " + call);
      call.unhold();
    }
  }

  @Override
  public void swapClicked() {
    if (call == null) {
      return;
    }

    LogUtil.i("CallButtonPresenter", "swapping the call: " + call);
    TelecomAdapter.getInstance().swap(call.getId());
  }

  @Override
  public void mergeClicked() {
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.IN_CALL_MERGE_BUTTON_PRESSED,
            call.getUniqueCallId(),
            call.getTimeAddedMs());
    TelecomAdapter.getInstance().merge(call.getId());
  }

  @Override
  public void addCallClicked() {
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.IN_CALL_ADD_CALL_BUTTON_PRESSED,
            call.getUniqueCallId(),
            call.getTimeAddedMs());
    // Automatically mute the current call
    automaticallyMuted = true;
    previousMuteState = AudioModeProvider.getInstance().getAudioState().isMuted();
    // UNISOC: add for bug905849
    InCallPresenter.getInstance().setAutomaticallyMuted(automaticallyMuted);
    // UNISOC: add for bug905796
    InCallPresenter.getInstance().setPreviousMuteState(previousMuteState);
    // Simulate a click on the mute button
    muteClicked(true /* checked */, false /* clickedByUser */);
    TelecomAdapter.getInstance().addCall();
  }

  @Override
  public void showDialpadClicked(boolean checked) {
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.IN_CALL_SHOW_DIALPAD_BUTTON_PRESSED,
            call.getUniqueCallId(),
            call.getTimeAddedMs());
    LogUtil.v("CallButtonPresenter", "show dialpad " + String.valueOf(checked));
    if(getActivity() != null){ //add for bug942862
      getActivity().showDialpadFragment(checked /* show */, true /* animate */);
    }else{
      LogUtil.i("CallButtonPresenter", " showDialpadClicked activity is null");
    }
  }

  @Override
  public void changeToVideoClicked() {
    LogUtil.enterBlock("CallButtonPresenter.changeToVideoClicked");
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.VIDEO_CALL_UPGRADE_REQUESTED,
            call.getUniqueCallId(),
            call.getTimeAddedMs());

    if (isSupportTxRxVideo(call)) {
      displayModifyCallOptions(call, getActivity()); //mCall
      return;
    }

//    call.getVideoTech().upgradeToVideo(context);
    /* SPRD FL0108020020: Add feature of low battery for Reliance@{ */
    if (CallUtil.isBatteryLow(context)) {
      CallUtil.showLowBatteryChangeToVideoDialog(context, call.getTelecomCall());
    }else{
      call.getVideoTech().upgradeToVideo(context);
    }/*@}*/
  }

  /* SPRD: Add video call option menu@{ */
  @Override
  public void changeToVoiceClicked() {
    LogUtil.i("CallButtonPresenter.changeToVoiceClicked","");
    call.getVideoTech().degradeToVoice();  //mCall
  }
  /*@}*/

  @Override
  public void onEndCallClicked() {
    LogUtil.i("CallButtonPresenter.onEndCallClicked", "call: " + call);
    if (call != null) {
      call.disconnect();
    }
  }

  @Override
  public void showAudioRouteSelector() {
    inCallButtonUi.showAudioRouteSelector();
  }

  @Override
  public void swapSimClicked() {
    LogUtil.enterBlock("CallButtonPresenter.swapSimClicked");
    Logger.get(getContext()).logImpression(Type.DUAL_SIM_CHANGE_SIM_PRESSED);
    SwapSimWorker worker =
        new SwapSimWorker(
            getContext(),
            call,
            InCallPresenter.getInstance().getCallList(),
            otherAccount,
            InCallPresenter.getInstance().acquireInCallUiLock("swapSim"));
    DialerExecutorComponent.get(getContext())
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(worker)
        .build()
        .executeParallel(null);
  }

  /**
   * Switches the camera between the front-facing and back-facing camera.
   *
   * @param useFrontFacingCamera True if we should switch to using the front-facing camera, or false
   *     if we should switch to using the back-facing camera.
   */
  @Override
  public void switchCameraClicked(boolean useFrontFacingCamera) {
    updateCamera(useFrontFacingCamera);
  }

  @Override
  public void toggleCameraClicked() {
    LogUtil.i("CallButtonPresenter.toggleCameraClicked", "");
    if (call == null) {
      return;
    }
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.IN_CALL_SCREEN_SWAP_CAMERA,
            call.getUniqueCallId(),
            call.getTimeAddedMs());
    switchCameraClicked(
        !InCallPresenter.getInstance().getInCallCameraManager().isUsingFrontFacingCamera());
  }

  /**
   * Stop or start client's video transmission.
   *
   * @param pause True if pausing the local user's video, or false if starting the local user's
   *     video.
   */
  @Override
  public void pauseVideoClicked(boolean pause) {
    LogUtil.i("CallButtonPresenter.pauseVideoClicked", "%s", pause ? "pause" : "unpause");

    Logger.get(context)
        .logCallImpression(
            pause
                ? DialerImpression.Type.IN_CALL_SCREEN_TURN_OFF_VIDEO
                : DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_VIDEO,
            call.getUniqueCallId(),
            call.getTimeAddedMs());

    if (pause) {
      call.getVideoTech().setCamera(null);
      call.getVideoTech().stopTransmission();
    } else {
      updateCamera(
          InCallPresenter.getInstance().getInCallCameraManager().isUsingFrontFacingCamera());
      call.getVideoTech().resumeTransmission(context);
    }

    inCallButtonUi.setVideoPaused(pause);
    inCallButtonUi.enableButton(InCallButtonIds.BUTTON_PAUSE_VIDEO, false);
  }

  private void updateCamera(boolean useFrontFacingCamera) {
    InCallCameraManager cameraManager = InCallPresenter.getInstance().getInCallCameraManager();
    cameraManager.setUseFrontFacingCamera(useFrontFacingCamera);

    String cameraId = cameraManager.getActiveCameraId();
    if (cameraId != null) {
      final int cameraDir =
          cameraManager.isUsingFrontFacingCamera()
              ? CameraDirection.CAMERA_DIRECTION_FRONT_FACING
              : CameraDirection.CAMERA_DIRECTION_BACK_FACING;
      call.setCameraDir(cameraDir);
      call.getVideoTech().setCamera(cameraId);
    }
  }

  private void updateUi(InCallState state, DialerCall call) {
    LogUtil.v("CallButtonPresenter", "updating call UI for call: %s", call);

    if (inCallButtonUi == null) {
      return;
    }

    if (call != null) {
      inCallButtonUi.updateInCallButtonUiColors(
          InCallPresenter.getInstance().getThemeColorManager().getSecondaryColor());
    }

    final boolean isEnabled =
        state.isConnectingOrConnected() && !state.isIncoming() && call != null;
    inCallButtonUi.setEnabled(isEnabled);
    LogUtil.i("CallButtonPresenter", "updateUi isEnabled:"+isEnabled);

    if (call == null) {
      return;
    }

    updateButtonsState(call);
  }

  /**
   * Updates the buttons applicable for the UI.
   *
   * @param call The active call.
   */
  @SuppressWarnings("MissingPermission")
  private void updateButtonsState(DialerCall call) {
    LogUtil.v("CallButtonPresenter.updateButtonsState", "start update");
    final boolean isVideo = call.isVideoCall();

    // Common functionality (audio, hold, etc).
    // Show either HOLD or SWAP, but not both. If neither HOLD or SWAP is available:
    //     (1) If the device normally can hold, show HOLD in a disabled state.
    //     (2) If the device doesn't have the concept of hold/swap, remove the button.
    final boolean showSwap = call.can(android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE);
    final boolean showHold =
         //UNISOC modified for bug 982291 start
        !showSwap && call.getState() != DialerCall.State.DIALING
         //end
            && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD)
            && call.can(android.telecom.Call.Details.CAPABILITY_HOLD)
            && InCallUiUtils.showHoldOnButton(context,isVideo,call)
                //UNISOC:add for bug900063
            && (call.getVideoTech().getSessionModificationState() != SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE);
    final boolean isCallOnHold = call.getState() == DialerCall.State.ONHOLD;

    final boolean showAddCall =
        TelecomAdapter.getInstance().canAddCall() && UserManagerCompat.isUserUnlocked(context)
         /* Add for bug904886 @{ */
        && (call.getVideoTech().getSessionModificationState() != SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE)
        // UNISOC: add for bug921567
        && !(inCallButtonUi != null && VideoCallFragment.class.isInstance(inCallButtonUi)
        && call.getVideoTech().getSessionModificationState() == SessionModificationState.UPGRADE_TO_VIDEO_REQUEST_FAILED);
    /* SPRD Feature Porting: Toast information when the number of conference call is over limit
       for cmcc case @{
     * @orig
     final boolean showMerge = call.can(
                android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE); */
    final boolean showMerge = ConferenceNumLimitHelper.getInstance(context)
            .showMergeButton(call)
            && CallList.getInstance().callOnSamePhone(call, context)/** SPRD: DSDA */
            && (call.getVideoTech().getSessionModificationState() != SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE) //add for bug916624
            /* UNISOC: Add for bug 915636 @{ */
            && !(CallList.getInstance().isConferenceParticipant());

        /* @} */
        //add for bug896056
    final boolean showUpgradeToVideo = !isVideo && (hasVideoCallCapabilities(call)) && !isCallOnHold
                  && InCallUiUtils.isSupportUpAndDownGrade(context,call)//SPRD:modify by bug895541
                  &&!call.isRemotelyHeld();// UNISOC: add for bug943905

    //UNISOC:add for bug958670
    final boolean isSupportTxRxVideo = isSupportTxRxVideo(call);

    final boolean showDowngradeToAudio =
            //UNISOC:add for bug958670
            !isSupportTxRxVideo// Add for change video type feature
            && isVideo && isDowngradeToAudioSupported(call) && call.getState() == DialerCall.State.ACTIVE
             /* SPRD: Added for video call conference @{ */
            && !call.isConferenceCall()
            /* @} */
            && !call.isRemotelyHeld(); // UNISOC: add for bug943905

    /* SPRD: Add for change video type feature @{ */
    final boolean showChangeVideoType =
            //UNISOC:add for bug958670
            isSupportTxRxVideo
            && isVideo && isDowngradeToAudioSupported(call)
            && hasVideoCallCapabilities(call)
            && !call.isConferenceCall()
            && call.getState() == DialerCall.State.ACTIVE;
    /* @} */

    final boolean showMute = call.can(android.telecom.Call.Details.CAPABILITY_MUTE);

    // UNISOC: add for bug960861
    final boolean onlyCameraPermission = VideoUtils.hasCameraPermissionAndShownPrivacyToast(context);
    final boolean hasCameraPermission =
        isVideo && onlyCameraPermission;
    // Disabling local video doesn't seem to work when dialing. See a bug.
    final boolean showPauseVideo =
            //UNISOC:add for bug958670
            !isSupportTxRxVideo// Add for change video type feature
            && isVideo
            && call.getState() != DialerCall.State.DIALING
            && call.getState() != DialerCall.State.CONNECTING;

    // SPRD Feature Porting: Enable send sms in incallui feature.
    final boolean isCallActive = call.getState() == DialerCall.State.ACTIVE;

    otherAccount = TelecomUtil.getOtherAccount(getContext(), call.getAccountHandle());
    boolean showSwapSim =
        otherAccount != null
            && !call.isVoiceMailNumber()
            // UNISOC: add for bug950842
            && !call.isEmergencyCall()
            && DialerCall.State.isDialing(call.getState())
            // Most devices cannot make calls on 2 SIMs at the same time.
            && InCallPresenter.getInstance().getCallList().getAllCalls().size() == 1;

    /*SPRD: add for VoLTE{@*/
    int conferenceSize = 0;
    if (call.isConferenceCall() && call.getChildCallIds() != null) {
        conferenceSize = call.getChildCallIds().size();
    }
    // UNISOC: add for bug920540
    boolean isVolteCall = false;
    if (ImsManagerEx.isImsRegisteredForPhone(InCallUiUtils.getPhoneIdByAccountHandle(context, call.getAccountHandle()))) {
      isVolteCall = true;
    }
    final boolean canInvite = call.isConferenceCall()
            // UNISOC: add for bug 895929
            && call.getChildCallIds().size() >= 1
            && (conferenceSize < 5)
            && mStayVolte && (call.getState() == DialerCall.State.ACTIVE)
            // UNISOC: add for bug920540
            && !isVideo && isVolteCall
            // add for bug966900
            && !call.hasProperty(android.telecom.Call.Details.PROPERTY_WIFI);
    /* @} */
    LogUtil.i("CallButtonPresenter.updateButtonsState", " iscon=" + call.isConferenceCall()
            + " conferenceSize=" + conferenceSize + " mStayVolte=" + mStayVolte + " callstate="
            + call.getState() + " isCallOnHold=" + isCallOnHold + ", isVolteCall=" + isVolteCall);

    inCallButtonUi.showButton(InCallButtonIds.BUTTON_AUDIO, true);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_SWAP, showSwap);
    /* add for bug915633: Show switch to secondary button when activeCall and backgroundCall is not null. @{ */
    DialerCall activeCall = CallList.getInstance().getActiveCall();
    DialerCall backgroundCall = CallList.getInstance().getBackgroundCall();
    boolean showSwitchToSec = false;
    if (activeCall != null && backgroundCall != null) {
      showSwitchToSec = call.getVideoTech().getSessionModificationState() != SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE;
      inCallButtonUi.showButton(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY, showSwitchToSec);
    } else {
      inCallButtonUi.showButton(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY, false);
      // UNISOC: add for bug917019
      inCallButtonUi.enableButton(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY, false);
    }
    LogUtil.i("CallButtonPresenter.updateButtonsState", " showSwap:"+showSwap+" showSwitchToSec:"+showSwitchToSec);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_HOLD, showHold && !showSwitchToSec);
    inCallButtonUi.setHold(isCallOnHold);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_MUTE, showMute);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_SWAP_SIM, showSwapSim);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_ADD_CALL, true);
    inCallButtonUi.enableButton(InCallButtonIds.BUTTON_ADD_CALL, showAddCall);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO, showUpgradeToVideo);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_DOWNGRADE_TO_AUDIO, showDowngradeToAudio);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_CHANGE_VIDEO_TYPE, showChangeVideoType);
    inCallButtonUi.showButton(
        InCallButtonIds.BUTTON_SWITCH_CAMERA,
            (hasCameraPermission && call.getVideoTech().isTransmitting())
                    // UNISOC: add for bug960861
                    || (onlyCameraPermission
                    && call.getVideoTech().getSessionModificationState() == SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE));
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_PAUSE_VIDEO, showPauseVideo);
    if (isVideo) {
      inCallButtonUi.setVideoPaused(!call.getVideoTech().isTransmitting() || !hasCameraPermission);
      //change for UNISOC:Bug906924
      inCallButtonUi.enableButton(InCallButtonIds.BUTTON_SWITCH_CAMERA, VideoProfile.isTransmissionEnabled(call.getVideoState()) );
      inCallButtonUi.showButton(InCallButtonIds.BUTTON_SWITCH_CAMERA, VideoProfile.isTransmissionEnabled(call.getVideoState()) && !call.isRingToneOnAudioCall());
    }
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_DIALPAD, true);
    //UNISOC add for bug 983812 start
    inCallButtonUi.enableButton(InCallButtonIds.BUTTON_DIALPAD,!isCallOnHold);
    //end
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_MERGE, showMerge);

    /* SPRD Feature Porting: Add for call recorder feature. @{ 
     && SPRD Feature Porting: Hide recorder feature for telstra case. */
    if (InCallUiUtils.isRecorderEnabled(context)) {  //mContext
      if (isVideo) {
        if (call.getState() == DialerCall.State.ACTIVE) {
          inCallButtonUi.showButton(InCallButtonIds.BUTTON_RECORD, true);
        } else {
          inCallButtonUi.showButton(InCallButtonIds.BUTTON_RECORD, false);
        }
      } else {
        inCallButtonUi.showButton(InCallButtonIds.BUTTON_RECORD, true);
      }
      inCallButtonUi.enableButton(InCallButtonIds.BUTTON_RECORD, isEnableRecorder(call));
    } else {
      inCallButtonUi.showButton(InCallButtonIds.BUTTON_RECORD, false);
    }
    /* @} */

     /* SPRD Feature Porting: Enable send sms in incallui feature. @{ */
    if ((isCallActive || isCallOnHold) && SendSmsHelper.getInstance(context).isSupportSendSms()) {  //add for bug977753
      inCallButtonUi.showButton(InCallButtonIds.BUTTON_SEND_MESSAGE, true);  //mInCallButtonUi
    } else {
      inCallButtonUi.showButton(InCallButtonIds.BUTTON_SEND_MESSAGE, false);  //mInCallButtonUi
    }
    /* @} */

    // SPRD Feature Porting: FL0108160005 Hangup all calls for orange case.
    if(InCallUiUtils.isSupportHangupAll(context)) {
      inCallButtonUi.showButton(InCallButtonIds.BUTTON_HANGUP_ALL, true);
      if(isShowHangupAll()) {
        inCallButtonUi.enableButton(InCallButtonIds.BUTTON_HANGUP_ALL, true);
      } else {
        inCallButtonUi.enableButton(InCallButtonIds.BUTTON_HANGUP_ALL, false);
      }
    }

    // SPRD Feature Porting: Explicit Call Transfer.
    boolean isSupportEctFeature = ExplicitCallTransferHelper.getInstance(context)
            .isSupportEctFeature(context);

    if(isSupportEctFeature) {
      inCallButtonUi.showButton(InCallButtonIds.BUTTON_ECT, true);
      boolean isShowECT = enableTransferButton() && !isVideo;
      if(isShowECT) {
        inCallButtonUi.enableButton(InCallButtonIds.BUTTON_ECT, true);
      }else {
        inCallButtonUi.enableButton(InCallButtonIds.BUTTON_ECT, false);
      }
    }

    

    /*SPRD: add for VoLTE{@*/
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_INVITE, canInvite);
    /* @} */

    inCallButtonUi.updateButtonStates();
    LogUtil.v("CallButtonPresenter.updateButtonsState", "end update");
  }

  private boolean hasVideoCallCapabilities(DialerCall call) {
    return call.getVideoTech().isAvailable(context, call.getAccountHandle());
  }

  /**
   * Determines if downgrading from a video call to an audio-only call is supported. In order to
   * support downgrade to audio, the SDK version must be >= N and the call should NOT have the
   * {@link android.telecom.Call.Details#CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO}.
   *
   * @param call The call.
   * @return {@code true} if downgrading to an audio-only call from a video call is supported.
   */
  private boolean isDowngradeToAudioSupported(DialerCall call) {
    // TODO(a bug): If there is an RCS video share session, return true here
    return !call.can(CallCompat.Details.CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO);
  }

  @Override
  public void refreshMuteState() {
    //UNISOC: add for bug905796
    previousMuteState = InCallPresenter.getInstance().isPreviousMuteState();
    // Restore the previous mute state
    if (automaticallyMuted
        && AudioModeProvider.getInstance().getAudioState().isMuted() != previousMuteState) {
      if (inCallButtonUi == null) {
        return;
      }
      muteClicked(previousMuteState, false /* clickedByUser */);
    }
    automaticallyMuted = false;
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    outState.putBoolean(KEY_AUTOMATICALLY_MUTED, automaticallyMuted);
    outState.putBoolean(KEY_PREVIOUS_MUTE_STATE, previousMuteState);
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    automaticallyMuted = savedInstanceState.getBoolean(KEY_AUTOMATICALLY_MUTED, automaticallyMuted);
    previousMuteState = savedInstanceState.getBoolean(KEY_PREVIOUS_MUTE_STATE, previousMuteState);
  }

  @Override
  public void onCameraPermissionGranted() {
    if (call != null) {
      updateButtonsState(call);
    }
  }

  @Override
  public void onActiveCameraSelectionChanged(boolean isUsingFrontFacingCamera) {
    if (inCallButtonUi == null) {
      return;
    }
    inCallButtonUi.setCameraSwitched(!isUsingFrontFacingCamera);
  }

  @Override
  public Context getContext() {
    return context;
  }

  private InCallActivity getActivity() {
    if (inCallButtonUi != null) {
      Fragment fragment = inCallButtonUi.getInCallButtonUiFragment();
      if (fragment != null) {
        return (InCallActivity) fragment.getActivity();
      }
    }
    return null;
  }

  /* SPRD Feature Porting: Add for call recorder feature. @{ */
  public boolean isEnableRecorder(DialerCall call) {
    UserManager userManager = (UserManager) context
            .getSystemService(Context.USER_SERVICE);
    int state = call.getState();
    return (state == DialerCall.State.ACTIVE || state == DialerCall.State.ONHOLD
            || state == DialerCall.State.CONFERENCED) && userManager.isUserUnlocked();
  }

  @Override
  public void recordClick(boolean isChecked) {
    LogUtil.i("CallButtonPresenter.recordClick"," isChecked = " + isChecked);
    if (getActivity() != null) {
      getActivity().recordClick();
    }
    // UNISOC: add for bug921569
    if (inCallButtonUi != null) {
      inCallButtonUi.setRecord(isChecked);  //mInCallButtonUi
    }
  }
  /* @} */

  /* SPRD Feature Porting: Enable send sms in incallui feature. @{ */
  public void sendSMSClicked() {
    LogUtil.d("CallButtonPresenter.sendSMSClicked", "");
    if (call != null) {  //mCall
      CallList callList = InCallPresenter.getInstance().getCallList();
      SendSmsHelper.getInstance(context).sendSms(  //mContext
              context, call, callList);
    } else {
      LogUtil.d("CallButtonPresenter.sendSMSClicked", "The call is null, can't send message.");
    }
  }
  /* @} */

  /* SPRD Feature Porting: FL0108160005 Hangup all calls for orange case. */
  @Override
  public void hangupAllClicked() {
    LogUtil.i("CallButtonPresenter.hangupAllClicked", "");
    DialerCall fgCall = CallList.getInstance().getActiveCall();
    DialerCall bgCall = CallList.getInstance().getBackgroundCall();

    if (fgCall != null) {
      fgCall.disconnect();
    }
    if (bgCall != null) {
      bgCall.disconnect();
    }
  }

  private boolean isShowHangupAll() {
    boolean enable = InCallUiUtils.isSupportHangupAll(getContext());
    CallList calllist = CallList.getInstance();

    return enable && calllist != null && calllist.getActiveCall() != null
            && calllist.getBackgroundCall() != null;
  }
  /* @} */


  /**
   * SPRD: To check  auto call recording settings
   */
  private boolean checkForAutoCallSettings(Context context, String phoneNumber) {
    /* SPRD: modified for bug957870( 812244) @{*/
    LogUtil.d("CallButtonPresenter", "checkForAutoCallSettings phoneNumber: " + phoneNumber);
    if(phoneNumber == null){
      return false;
    }
    CallRecordSettingEntity recordSettings = CallRecordingContactsHelper
            .getInstance(context).getCallRecordingSettings();
    LogUtil.i("CallButtonPresenter", "checkForAutoCallSettings getRecordFrom: " + recordSettings.getRecordFrom());
    // UNISOC: add for bug934749
    if (!recordSettings.getAutoCallRecording()) {
      return false;
    }
    if (recordSettings.getRecordFrom() == RecordListFrom.RECORD_ALL_NUMBERS) {
      return true;
    } else if (recordSettings.getRecordFrom() == RecordListFrom.RECORD_LISTED_NUMBERS) {
      if (recordSettings.getUnknownNumberSetting() && isNumberUnknown(context, phoneNumber)) {
        return true;
      } else {
        String number = InCallUiUtils.removeNonNumericForNumber(phoneNumber);
        LogUtil.d("CallButtonPresenter", "checkForAutoCallSettings phoneNumber " +
                "after remove non-numric: " + number);
        /* SPRD: modified for bug 812381 & 812244 @{*/
        phoneNumber = InCallUiUtils.getPhoneNumberWithoutCountryCode(number, context);
        ArrayList<String> recordList = CallRecordingContactsHelper.getInstance(context).getCallRecordingNumber();
        for(String saveNumber : recordList) {
          saveNumber = InCallUiUtils.getPhoneNumberWithoutCountryCode(saveNumber, context);
          if(saveNumber.equals(phoneNumber)) {
            return true;
          }
        }
        return false;
      }
    } else {
      return false;
    }
  }

  /**
   * SPRD: To check  weather given number is exist in contacts or not
   */
  private boolean isNumberUnknown(Context context, String phoneNumber) {
    ContentResolver cr = context.getContentResolver();
    if (cr == null || phoneNumber == null || TextUtils.isEmpty(phoneNumber)) {
      return true;
    }
    Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
    Cursor cursor = cr.query(uri, new String[]{PhoneLookup.DISPLAY_NAME}, null, null, null);
    try {
      if (cursor != null && cursor.moveToFirst()) {
        int index = cursor.getColumnIndex(PhoneLookup.DISPLAY_NAME);
        if (index != -1 && (cursor.getString(index) != null)) {
          return false;
        }
      }
    } catch (Exception e) {
      LogUtil.e("CallButtonPresenter isNumberUnknown", " e = " + e);
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
    return true;
  }

  /**
   * Explicit Call Transfer
   */
  public boolean enableTransferButton() {
    return ExplicitCallTransferHelper.getInstance(context)
            .shouldEnableTransferButton();
  }

  /**
   * Explicit Call Transfer
   */
  public void transferCall() {
    if (call != null && context != null) {
      ExplicitCallTransferHelper.getInstance(context)
              .explicitCallTransfer(context);
    }
  }

  /*SPRD: add for VoLTE{@*/
  @Override
  public void inviteClicked() {
    LogUtil.i("CallButtonPresenter.inviteClick","");
    // UNISOC: modify for bug922248
    String [] numberArray = CallList.getInstance().getActiveConferenceCallNumberArray();
    Intent intentPick = new Intent(MULTI_PICK_CONTACTS_ACTION).
            putExtra("checked_limit_count",MAX_GROUP_CALL_NUMBER - CallList.getInstance().getConferenceCallSize()).
            putExtra("checked_min_limit_count", MIN_CONTACTS_NUMBER).
            putExtra("cascading",new Intent(MULTI_PICK_CONTACTS_ACTION).setType(Phone.CONTENT_ITEM_TYPE)).
            putExtra("multi",ADD_MULTI_CALL_AGAIN).
            putExtra("number",numberArray);
    try { //add for bug968873
      context.startActivity(intentPick);
    } catch (android.content.ActivityNotFoundException e){
      LogUtil.e("CallButtonPresenter.inviteClicked", "Exception:" + e.getMessage());
    }
  }
  private synchronized void tryRegisterImsListener(){
      if(context != null && context.checkSelfPermission(READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
              // UNISOC: add for bug907013
              && context.checkSelfPermission(READ_PRIVILEGED_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
              && (ImsManager.isVolteEnabledByPlatform(context) || ImsManager.isWfcEnabledByPlatform(context))){
          mIImsServiceEx = ImsManagerEx.getIImsServiceEx();
          if(mIImsServiceEx != null){
              try{
                  if(!mIsImsListenerRegistered){
                      mIsImsListenerRegistered = true;
                      mIImsServiceEx.registerforImsRegisterStateChanged(mImsUtListenerExBinder);
                  }
              }catch(RemoteException e){
                  LogUtil.e("CallButtonPresenter tryRegisterImsListener", " e = " + e);
              }
          }
      }
  }

  private final IImsRegisterListener.Stub mImsUtListenerExBinder = new IImsRegisterListener.Stub(){
      @Override
      public void imsRegisterStateChange(boolean isRegistered){
        LogUtil.i("CallButtonPresenter imsRegisterStateChange", " isRegistered: " + isRegistered);
          if(mStayVolte != isRegistered){
              mStayVolte = isRegistered;
          }
      }
  };

  private synchronized void unTryRegisterImsListener(){
      if(context != null && context.checkSelfPermission(READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
              // UNISOC: add for bug907013
              && context.checkSelfPermission(READ_PRIVILEGED_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
              && (ImsManager.isVolteEnabledByPlatform(context) || ImsManager.isWfcEnabledByPlatform(context))){
          try{
              if(mIsImsListenerRegistered){
                  mIsImsListenerRegistered = false;
                  mIImsServiceEx.unregisterforImsRegisterStateChanged(mImsUtListenerExBinder);
              }
          }catch(RemoteException e){
              LogUtil.e("CallButtonPresenter unTryRegisterImsListener", " e = " + e);
          }
      }
  }
  /* @} */

/* SPRD Feature Porting: Add for change video type feature. */
  @Override
  public void changeVideoTypeClicked() {
    displayModifyCallOptions(call, getActivity());
  }

  //UNISOC:add for bug958670
  private int mPrePhoneId = -1;

  public boolean isSupportTxRxVideo(DialerCall call) {
    /* SPRD: add for Bug 905754 & 958670 @{ */
    CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
            Context.CARRIER_CONFIG_SERVICE);
    PhoneAccountHandle accountHandle = call.getAccountHandle();
    int currentPhoneId = InCallUiUtils.getPhoneIdByAccountHandle(context,accountHandle);
    if(-1 == currentPhoneId || mPrePhoneId == currentPhoneId) {
      LogUtil.i("CallButtonPresenter",
              "has already got the value or invalid phone id. currentPhoneId =" + currentPhoneId);
      return mIsSupportTxRxVideo && mStayVolte;
    }

    if (configManager.getConfigForPhoneId(currentPhoneId) != null) {
      mIsSupportTxRxVideo = configManager.getConfigForPhoneId(currentPhoneId).getBoolean(
              CarrierConfigManagerEx.KEY_SUPPORT_TXRX_VT_CALL_BOOL);
      LogUtil.i("CallButtonPresenter", "isSupportTxRxVideo:"
              + configManager.getConfigForPhoneId(currentPhoneId)
              .getBoolean(CarrierConfigManagerEx.KEY_SUPPORT_TXRX_VT_CALL_BOOL));
      mPrePhoneId = currentPhoneId;
    } else {
      LogUtil.i("CallButtonPresenter", "isSupportTxRxVideo getConfigForDefaultPhone = null");
    }
    /* @}*/
    //SPRD: add for Bug 846738
    return mIsSupportTxRxVideo && mStayVolte;
  }

  /**
   * The function is called when Modify Call button gets pressed. The function creates and
   * displays modify call options.
   */
  public void displayModifyCallOptions(final DialerCall call, final Context context) {
    if (call == null) {
      Log.d(this, "Can't display modify call options. Call is null");
      return;
    }

    /*if (context.getResources().getBoolean(
            R.bool.config_enable_enhance_video_call_ui)) {
        // selCallType is set to -1 default, if the value is not updated, it is unexpected.
        if (selectType != -1) {
            VideoProfile videoProfile = new VideoProfile(selectType);
            Log.v(this, "Videocall: Enhance videocall: upgrade/downgrade to "
                    + callTypeToString(selectType));
            changeToVideoClicked(call, videoProfile);
        }
        return;
    }*/

    final ArrayList<CharSequence> items = new ArrayList<CharSequence>();
    final ArrayList<Integer> itemToCallType = new ArrayList<Integer>();
    final Resources res = context.getResources();

    // Prepare the string array and mapping.
    if (isDowngradeToAudioSupported(call)) {
      items.add(res.getText(R.string.call_type_voice));
      itemToCallType.add(VideoProfile.STATE_AUDIO_ONLY);
    }

    if (hasVideoCallCapabilities(call)) {
      if(!VideoUtils.isRxOnlyVideoCall(call)){
        items.add(res.getText(R.string.onscreenVideoCallTxText));
        itemToCallType.add(VideoProfile.STATE_TX_ENABLED);
      }
      if(!VideoUtils.isTxOnlyVideoCall(call)){
        items.add(res.getText(R.string.onscreenVideoCallRxText));
        itemToCallType.add(VideoProfile.STATE_RX_ENABLED);
      }

      items.add(res.getText(R.string.incall_label_videocall));
      itemToCallType.add(VideoProfile.STATE_BIDIRECTIONAL);
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(R.string.call_type_title);
    final AlertDialog alert;

    DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int item) {
        final int selCallType = itemToCallType.get(item);
        //add for bug900423
        if(selCallType != call.getVideoState()){
          Toast.makeText(context, items.get(item), Toast.LENGTH_SHORT).show();
          changeToCallTypeClicked(call, selCallType);
        }
        dialog.dismiss();
        if(getActivity() != null){
           getActivity().setAlert(null);
        }
      }
    };
    final int currUnpausedVideoState = VideoUtils.getUnPausedVideoState(call.getVideoState());
    final int index = itemToCallType.indexOf(currUnpausedVideoState);
    LogUtil.i("CallButtonPresenter.displayModifyCallOptions", " currUnpausedVideoState:" + currUnpausedVideoState +" index:"+ index);
    builder.setSingleChoiceItems(items.toArray(new CharSequence[0]), index, listener);
    alert = builder.create();
    alert.show();
    if(getActivity() != null){
      getActivity().setAlert(alert);
    }
  }

  /**
   * Sends a session modify request to the telephony framework
   */
  private void changeToCallTypeClicked(DialerCall call, int videoState) {
    if(call == null){
      Log.d(this,"changeToCallTypeClicked: call = null");
      return;
    }

    if(videoState == VideoProfile.STATE_AUDIO_ONLY){
      call.getVideoTech().degradeToVoice();
    }else{
      if(CallList.getInstance() != null && CallList.getInstance().getBackgroundCall() != null
              && ImsManagerEx.isDualVoLTEActive()) {
        Toast.makeText(context, R.string.fail_change_video_due_to_bgcall,
                Toast.LENGTH_SHORT).show();//mContext
        return;
      }

      if (CallUtil.isBatteryLow(context)) {
        CallUtil.showLowBatteryChangeToVideoDialog(context, call.getTelecomCall()); //mCall
      } else {
        switch (videoState){
          case VideoProfile.STATE_BIDIRECTIONAL:
              call.getVideoTech().upgradeToVideo(context);
            break;
          case VideoProfile.STATE_RX_ENABLED:
            call.getVideoTech().changeToRxVideo();
            break;
          case VideoProfile.STATE_TX_ENABLED:
            call.getVideoTech().changeToTxVideo();
            break;
        }
      }
    }
  }
  /* @} */
}
