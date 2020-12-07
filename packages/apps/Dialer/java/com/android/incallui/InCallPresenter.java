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

package com.android.incallui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.net.wifi.WifiManager;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerHALManager;
import android.os.PowerHintVendorSprd;
import android.os.PowerManager;
import android.os.RemoteException;//UNISOC:add for bug937619
import android.os.Trace;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.UserManagerCompat;
import android.telecom.Call.Details;
import android.telecom.CallAudioState;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.contacts.common.compat.CallCompat;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler.OnCheckBlockedListener;
import com.android.dialer.blocking.FilteredNumberCompat;
import com.android.dialer.blocking.FilteredNumbersUtil;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.logging.InteractionEvent;
import com.android.dialer.logging.Logger;
import com.android.dialer.postcall.PostCall;
import com.android.dialer.telecom.TelecomCallUtil;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.CallUtil;//UNISOC:add for bug937619
import com.android.dialer.util.TouchPointManager;
import com.android.ims.ImsManager;
import com.android.ims.internal.ImsManagerEx;
import com.android.incallui.InCallOrientationEventListener.ScreenOrientation;
import com.android.incallui.answerproximitysensor.PseudoScreenState;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.ExternalCallList;
import com.android.incallui.call.TelecomAdapter;
import com.android.incallui.disconnectdialog.DisconnectMessage;
import com.android.incallui.incalluilock.InCallUiLock;
import com.android.incallui.latencyreport.LatencyReport;
import com.android.incallui.legacyblocking.BlockedNumberContentObserver;
import com.android.incallui.spam.SpamCallListListener;
import com.android.incallui.sprd.PhoneRecorderHelper;
import com.android.incallui.sprd.plugin.voiceclearcode.VoiceClearCodeHelper;
import com.android.incallui.sprd.plugin.hdaudio.InCallUIHdAudioHelper;
import com.android.incallui.sprd.WifiCallDialog;
import com.android.incallui.videosurface.bindings.VideoSurfaceBindings;
import com.android.incallui.videosurface.protocol.VideoSurfaceTexture;
import com.android.incallui.videotech.utils.VideoUtils;
import com.android.incallui.VideoCallPresenter.PreviewSurfaceState;
import com.android.incallui.sprd.InCallUiUtils;
import com.android.incallui.sprd.plugin.shakePhoneToStartRecording.ShakePhoneToStartRecordingHelper;
import com.android.incallui.sprd.NeededForReflection;

//UNISOC:add for bug937619
import com.android.ims.ImsManager;
import com.android.ims.internal.IImsRegisterListener;
import com.android.ims.internal.IImsServiceEx;
import com.android.sprd.telephony.RadioInteractor;
import com.android.sprd.telephony.RadioInteractorCallbackListener;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import static android.Manifest.permission.READ_PHONE_STATE;
/**
 * Takes updates from the CallList and notifies the InCallActivity (UI) of the changes. Responsible
 * for starting the activity for a new call and finishing the activity when all calls are
 * disconnected. Creates and manages the in-call state and provides a listener pattern for the
 * presenters that want to listen in on the in-call state changes. TODO: This class has become more
 * of a state machine at this point. Consider renaming.
 */
public class InCallPresenter implements CallList.Listener, AudioModeProvider.AudioModeListener {
  private static final String PIXEL2017_SYSTEM_FEATURE =
      "com.google.android.feature.PIXEL_2017_EXPERIENCE";

  private static final long BLOCK_QUERY_TIMEOUT_MS = 1000;

  private static final Bundle EMPTY_EXTRAS = new Bundle();

  private static InCallPresenter inCallPresenter;

  /**
   * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is load factor before
   * resizing, 1 means we only expect a single thread to access the map so make only a single shard
   */
  private final Set<InCallStateListener> listeners =
      Collections.newSetFromMap(new ConcurrentHashMap<InCallStateListener, Boolean>(8, 0.9f, 1));

  private final List<IncomingCallListener> incomingCallListeners = new CopyOnWriteArrayList<>();
  private final Set<InCallDetailsListener> detailsListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<InCallDetailsListener, Boolean>(8, 0.9f, 1));
  private final Set<CanAddCallListener> canAddCallListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<CanAddCallListener, Boolean>(8, 0.9f, 1));
  private final Set<InCallUiListener> inCallUiListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<InCallUiListener, Boolean>(8, 0.9f, 1));
  private final Set<InCallOrientationListener> orientationListeners =
      Collections.newSetFromMap(
          new ConcurrentHashMap<InCallOrientationListener, Boolean>(8, 0.9f, 1));
  private final Set<InCallEventListener> inCallEventListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<InCallEventListener, Boolean>(8, 0.9f, 1));

  private StatusBarNotifier statusBarNotifier;
  private ExternalCallNotifier externalCallNotifier;
  private ContactInfoCache contactInfoCache;
  private Context context;
  private final OnCheckBlockedListener onCheckBlockedListener =
      new OnCheckBlockedListener() {
        @Override
        public void onCheckComplete(final Integer id) {
          if (id != null && id != FilteredNumberAsyncQueryHandler.INVALID_ID) {
            // Silence the ringer now to prevent ringing and vibration before the call is
            // terminated when Telecom attempts to add it.
            TelecomUtil.silenceRinger(context);
          }
        }
      };
  private CallList callList;
  private ExternalCallList externalCallList;
  private InCallActivity inCallActivity;
  private ManageConferenceActivity manageConferenceActivity;
  private final android.telecom.Call.Callback callCallback =
      new android.telecom.Call.Callback() {
        @Override
        public void onPostDialWait(
            android.telecom.Call telecomCall, String remainingPostDialSequence) {
          // UNISOC: add for bug921423
          if (callList == null) {
            return;
          }
          final DialerCall call = callList.getDialerCallFromTelecomCall(telecomCall);
          if (call == null) {
            LogUtil.w(
                "InCallPresenter.onPostDialWait",
                "DialerCall not found in call list: " + telecomCall);
            return;
          }
          onPostDialCharWait(call.getId(), remainingPostDialSequence);
        }

        @Override
        public void onDetailsChanged(
            android.telecom.Call telecomCall, android.telecom.Call.Details details) {
          // UNISOC: add for bug921423
          if (callList == null) {
            return;
          }
          final DialerCall call = callList.getDialerCallFromTelecomCall(telecomCall);
          if (call == null) {
            LogUtil.w(
                "InCallPresenter.onDetailsChanged",
                "DialerCall not found in call list: " + telecomCall);
            return;
          }

          if (details.hasProperty(Details.PROPERTY_IS_EXTERNAL_CALL)
              && !externalCallList.isCallTracked(telecomCall)) {

            // A regular call became an external call so swap call lists.
            LogUtil.i("InCallPresenter.onDetailsChanged", "Call became external: " + telecomCall);
            callList.onInternalCallMadeExternal(context, telecomCall);
            externalCallList.onCallAdded(telecomCall);
            return;
          }

          for (InCallDetailsListener listener : detailsListeners) {
            listener.onDetailsChanged(call, details);
          }
        }

        @Override
        public void onConferenceableCallsChanged(
            android.telecom.Call telecomCall, List<android.telecom.Call> conferenceableCalls) {
          LogUtil.i(
              "InCallPresenter.onConferenceableCallsChanged",
              "onConferenceableCallsChanged: " + telecomCall);
          onDetailsChanged(telecomCall, telecomCall.getDetails());
        }
      };
  private InCallState inCallState = InCallState.NO_CALLS;
  private ProximitySensor proximitySensor;
  private final PseudoScreenState pseudoScreenState = new PseudoScreenState();
  private boolean serviceConnected;
  private InCallCameraManager inCallCameraManager;
  private FilteredNumberAsyncQueryHandler filteredQueryHandler;
  private CallList.Listener spamCallListListener;
  /** Whether or not we are currently bound and waiting for Telecom to send us a new call. */
  private boolean boundAndWaitingForOutgoingCall;
  /** Determines if the InCall UI is in fullscreen mode or not. */
  private boolean isFullScreen = false;

  private boolean screenTimeoutEnabled = true;

  private TelephonyManager mTelephonyManager;
  private int mPhoneCount;


  // SPRD Feature Porting: Add for call recorder feature.
  private PhoneRecorderHelper mRecorderHelper;

  private PhoneStateListener phoneStateListener =
      new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
          if (state == TelephonyManager.CALL_STATE_RINGING) {
            if (FilteredNumbersUtil.hasRecentEmergencyCall(context)) {
              return;
            }
            // Check if the number is blocked, to silence the ringer.
            String countryIso = GeoUtil.getCurrentCountryIso(context);
            filteredQueryHandler.isBlockedNumber(
                onCheckBlockedListener, incomingNumber, countryIso);
          }
        }
      };

  /** Whether or not InCallService is bound to Telecom. */
  private boolean serviceBound = false;
    /** Whether or not  bound to RadioInteracto. */
  private boolean mRadioInteractorConnection = false;

  /**
   * When configuration changes Android kills the current activity and starts a new one. The flag is
   * used to check if full clean up is necessary (activity is stopped and new activity won't be
   * started), or if a new activity will be started right after the current one is destroyed, and
   * therefore no need in release all resources.
   */
  private boolean isChangingConfigurations = false;

  private boolean awaitingCallListUpdate = false;

  private ExternalCallList.ExternalCallListener externalCallListener =
      new ExternalCallList.ExternalCallListener() {

        @Override
        public void onExternalCallPulled(android.telecom.Call call) {
          // Note: keep this code in sync with InCallPresenter#onCallAdded
          LatencyReport latencyReport = new LatencyReport(call);
          latencyReport.onCallBlockingDone();
          // Note: External calls do not require spam checking.
          callList.onCallAdded(context, call, latencyReport);
          call.registerCallback(callCallback);
        }

        @Override
        public void onExternalCallAdded(android.telecom.Call call) {
          // No-op
        }

        @Override
        public void onExternalCallRemoved(android.telecom.Call call) {
          // No-op
        }

        @Override
        public void onExternalCallUpdated(android.telecom.Call call) {
          // No-op
        }
      };

  private ThemeColorManager themeColorManager;
  private VideoSurfaceTexture localVideoSurfaceTexture;
  private VideoSurfaceTexture remoteVideoSurfaceTexture;

  private MotorolaInCallUiNotifier motorolaInCallUiNotifier;

  /* SPRD: Added for bug656290 @{ */
  private boolean mVowifiRssiReceiver = false;
  private NotificationBroadcastReceiver mNotificationReceiver = new NotificationBroadcastReceiver();
  private IntentFilter iFilter =  new IntentFilter(WifiManager.RSSI_CHANGED_ACTION);
  /* @} */
  /* SPRD Feature: Support wifiCall notification. @{ */
  private final String DIALOG_TYPE_KEY = "dialog_type_key";
  private int mRssi = -200;
  private boolean mIsWifiConnected = false;
  // UNISOC: add for bug905849
  private boolean automaticallyMuted = false;
  //add for bug906119
  private int mPreviewSurfaceState = PreviewSurfaceState.NONE;
  //UNISOC:add for bug916926
  private boolean mIsUiShowing = false;
  /* @} */
  // UNISOC: add for bug905796
  private boolean previousMuteState = false;

  /** Inaccessible constructor. Must use getRunningInstance() to get this singleton. */
  @VisibleForTesting
  InCallPresenter() {}

  public static synchronized InCallPresenter getInstance() {
    if (inCallPresenter == null) {
      Trace.beginSection("InCallPresenter.Constructor");
      inCallPresenter = new InCallPresenter();
      Trace.endSection();
    }
    return inCallPresenter;
  }

  @VisibleForTesting
  public static synchronized void setInstanceForTesting(InCallPresenter inCallPresenter) {
    InCallPresenter.inCallPresenter = inCallPresenter;
  }

  /**
   * Determines whether or not a call has no valid phone accounts that can be used to make the call
   * with. Emergency calls do not require a phone account.
   *
   * @param call to check accounts for.
   * @return {@code true} if the call has no call capable phone accounts set, {@code false} if the
   *     call contains a phone account that could be used to initiate it with, or is an emergency
   *     call.
   */
  public static boolean isCallWithNoValidAccounts(DialerCall call) {
    if (call != null && !call.isEmergencyCall()) {
      Bundle extras = call.getIntentExtras();

      if (extras == null) {
        extras = EMPTY_EXTRAS;
      }

      final List<PhoneAccountHandle> phoneAccountHandles =
          extras.getParcelableArrayList(android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS);

      if ((call.getAccountHandle() == null
          && (phoneAccountHandles == null || phoneAccountHandles.isEmpty()))) {
        LogUtil.i(
            "InCallPresenter.isCallWithNoValidAccounts", "No valid accounts for call " + call);
        return true;
      }
    }
    return false;
  }

  public InCallState getInCallState() {
    return inCallState;
  }

  public CallList getCallList() {
    return callList;
  }

  public void setUp(
      @NonNull Context context,
      CallList callList,
      ExternalCallList externalCallList,
      StatusBarNotifier statusBarNotifier,
      ExternalCallNotifier externalCallNotifier,
      ContactInfoCache contactInfoCache,
      ProximitySensor proximitySensor,
      FilteredNumberAsyncQueryHandler filteredNumberQueryHandler) {
    Trace.beginSection("InCallPresenter.setUp");
    if (serviceConnected) {
      LogUtil.i("InCallPresenter.setUp", "New service connection replacing existing one.");
      if (context != this.context || callList != this.callList) {
        throw new IllegalStateException();
      }
      Trace.endSection();
      return;
    }

    Objects.requireNonNull(context);
    this.context = context;

    this.contactInfoCache = contactInfoCache;

    this.statusBarNotifier = statusBarNotifier;
    this.externalCallNotifier = externalCallNotifier;
    addListener(this.statusBarNotifier);
    EnrichedCallComponent.get(this.context)
        .getEnrichedCallManager()
        .registerStateChangedListener(this.statusBarNotifier);

    this.proximitySensor = proximitySensor;
    addListener(this.proximitySensor);

    if (themeColorManager == null) {
      themeColorManager =
          new ThemeColorManager(new InCallUIMaterialColorMapUtils(this.context.getResources()));
    }

    this.callList = callList;
    this.externalCallList = externalCallList;
    externalCallList.addExternalCallListener(this.externalCallNotifier);
    externalCallList.addExternalCallListener(externalCallListener);

    // This only gets called by the service so this is okay.
    serviceConnected = true;

    // The final thing we do in this set up is add ourselves as a listener to CallList.  This
    // will kick off an update and the whole process can start.
    this.callList.addListener(this);

    // Create spam call list listener and add it to the list of listeners
    spamCallListListener =
        new SpamCallListListener(
            context, DialerExecutorComponent.get(context).dialerExecutorFactory());
    this.callList.addListener(spamCallListListener);

    VideoPauseController.getInstance().setUp(this);

    filteredQueryHandler = filteredNumberQueryHandler;
    this.context
        .getSystemService(TelephonyManager.class)
        .listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

    AudioModeProvider.getInstance().addListener(this);


    if (motorolaInCallUiNotifier == null) {
      // Add listener to notify Telephony process when the incoming call screen is started or
      // finished. This is for hiding USSD dialog because the incoming call screen should have
      // higher precedence over this dialog.
      motorolaInCallUiNotifier = new MotorolaInCallUiNotifier(context);
      addInCallUiListener(motorolaInCallUiNotifier);
      addListener(motorolaInCallUiNotifier);
    }

      //UNISOC：add for bug937619
      tryRegisterImsListener(context);
      mIsVideoEnable = CallUtil.isVideoEnabled(context);
      LogUtil.i("InCallPresenter.setUp","mIsVideoEnable = "+mIsVideoEnable);
    LogUtil.d("InCallPresenter.setUp", "Finished InCallPresenter.setUp");
    Trace.endSection();
  }

  /**
   * Called when the telephony service has disconnected from us. This will happen when there are no
   * more active calls. However, we may still want to continue showing the UI for certain cases like
   * showing "Call Ended". What we really want is to wait for the activity and the service to both
   * disconnect before we tear things down. This method sets a serviceConnected boolean and calls a
   * secondary method that performs the aforementioned logic.
   */
  public void tearDown() {
    LogUtil.d("InCallPresenter.tearDown", "tearDown");
    callList.clearOnDisconnect();

    serviceConnected = false;

    context
        .getSystemService(TelephonyManager.class)
        .listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
    unRegisterImsListener(context);//UNISOC:add for bug937619
    attemptCleanup();
    VideoPauseController.getInstance().tearDown();
    AudioModeProvider.getInstance().removeListener(this);
    mPreviewSurfaceState = PreviewSurfaceState.NONE;//add for bug906119
  }

  private void attemptFinishActivity() {
    screenTimeoutEnabled = true;
    final boolean doFinish = (inCallActivity != null && isActivityStarted());
    LogUtil.i("InCallPresenter.attemptFinishActivity", "Hide in call UI: " + doFinish);
    if (doFinish) {
      inCallActivity.setExcludeFromRecents(true);
      inCallActivity.finish();
    }
  }

  /**
   * Called when the UI ends. Attempts to tear down everything if necessary. See {@link #tearDown()}
   * for more insight on the tear-down process.
   */
  public void unsetActivity(InCallActivity inCallActivity) {
    if (inCallActivity == null) {
      throw new IllegalArgumentException("unregisterActivity cannot be called with null");
    }
    if (this.inCallActivity == null) {
      LogUtil.i(
          "InCallPresenter.unsetActivity", "No InCallActivity currently set, no need to unset.");
      return;
    }
    if (this.inCallActivity != inCallActivity) {
      LogUtil.w(
          "InCallPresenter.unsetActivity",
          "Second instance of InCallActivity is trying to unregister when another"
              + " instance is active. Ignoring.");
      return;
    }
    updateActivity(null);
  }

  /**
   * Updates the current instance of {@link InCallActivity} with the provided one. If a {@code null}
   * activity is provided, it means that the activity was finished and we should attempt to cleanup.
   */
  private void updateActivity(InCallActivity inCallActivity) {
    Trace.beginSection("InCallPresenter.updateActivity");
    boolean updateListeners = false;
    boolean doAttemptCleanup = false;

    if (inCallActivity != null) {
      if (this.inCallActivity == null) {
        context = inCallActivity.getApplicationContext();
        updateListeners = true;
        LogUtil.i("InCallPresenter.updateActivity", "UI Initialized");
      } else {
        // since setActivity is called onStart(), it can be called multiple times.
        // This is fine and ignorable, but we do not want to update the world every time
        // this happens (like going to/from background) so we do not set updateListeners.
      }

      this.inCallActivity = inCallActivity;
      this.inCallActivity.setExcludeFromRecents(false);

      // By the time the UI finally comes up, the call may already be disconnected.
      // If that's the case, we may need to show an error dialog.
      if (callList != null && callList.getDisconnectedCall() != null) {
        showDialogOrToastForDisconnectedCall(callList.getDisconnectedCall());
      }

      // When the UI comes up, we need to first check the in-call state.
      // If we are showing NO_CALLS, that means that a call probably connected and
      // then immediately disconnected before the UI was able to come up.
      // If we dont have any calls, start tearing down the UI instead.
      // NOTE: This code relies on {@link #mInCallActivity} being set so we run it after
      // it has been set.
      if (inCallState == InCallState.NO_CALLS) {
        LogUtil.i("InCallPresenter.updateActivity", "UI Initialized, but no calls left. Shut down");
        attemptFinishActivity();
        Trace.endSection();
        return;
      }
    } else {
      LogUtil.i("InCallPresenter.updateActivity", "UI Destroyed");
      updateListeners = true;
      this.inCallActivity = null;

      // We attempt cleanup for the destroy case but only after we recalculate the state
      // to see if we need to come back up or stay shut down. This is why we do the
      // cleanup after the call to onCallListChange() instead of directly here.
      doAttemptCleanup = true;
    }

    // Messages can come from the telephony layer while the activity is coming up
    // and while the activity is going down.  So in both cases we need to recalculate what
    // state we should be in after they complete.
    // Examples: (1) A new incoming call could come in and then get disconnected before
    //               the activity is created.
    //           (2) All calls could disconnect and then get a new incoming call before the
    //               activity is destroyed.
    //
    // a bug - We previously had a check for mServiceConnected here as well, but there are
    // cases where we need to recalculate the current state even if the service in not
    // connected.  In particular the case where startOrFinish() is called while the app is
    // already finish()ing. In that case, we skip updating the state with the knowledge that
    // we will check again once the activity has finished. That means we have to recalculate the
    // state here even if the service is disconnected since we may not have finished a state
    // transition while finish()ing.
    if (updateListeners) {
      onCallListChange(callList);
    }

    if (doAttemptCleanup) {
      attemptCleanup();
    }
    Trace.endSection();
  }

  public void setManageConferenceActivity(
      @Nullable ManageConferenceActivity manageConferenceActivity) {
    this.manageConferenceActivity = manageConferenceActivity;
  }

  public void onBringToForeground(boolean showDialpad) {
    LogUtil.i("InCallPresenter.onBringToForeground", "Bringing UI to foreground.");
    bringToForeground(showDialpad);
  }

  public void onCallAdded(final android.telecom.Call call) {
    Trace.beginSection("InCallPresenter.onCallAdded");
    LatencyReport latencyReport = new LatencyReport(call);
    if (shouldAttemptBlocking(call)) {
      maybeBlockCall(call, latencyReport);
    } else {
      if (call.getDetails().hasProperty(CallCompat.Details.PROPERTY_IS_EXTERNAL_CALL)) {
        externalCallList.onCallAdded(call);
      } else {
        latencyReport.onCallBlockingDone();
        callList.onCallAdded(context, call, latencyReport);
      }
    }

    // Since a call has been added we are no longer waiting for Telecom to send us a call.
    setBoundAndWaitingForOutgoingCall(false, null);
    call.registerCallback(callCallback);
    // TODO(maxwelb): Return the future in recordPhoneLookupInfo and propagate.
    PhoneLookupHistoryRecorder.recordPhoneLookupInfo(context.getApplicationContext(), call);
    Trace.endSection();
  }

  private boolean shouldAttemptBlocking(android.telecom.Call call) {
    if (call.getState() != android.telecom.Call.STATE_RINGING) {
      return false;
    }
    if (!UserManagerCompat.isUserUnlocked(context)) {
      LogUtil.i(
          "InCallPresenter.shouldAttemptBlocking",
          "not attempting to block incoming call because user is locked");
      return false;
    }
    if (TelecomCallUtil.isEmergencyCall(call)) {
      LogUtil.i(
          "InCallPresenter.shouldAttemptBlocking",
          "Not attempting to block incoming emergency call");
      return false;
    }
    if (FilteredNumbersUtil.hasRecentEmergencyCall(context)) {
      LogUtil.i(
          "InCallPresenter.shouldAttemptBlocking",
          "Not attempting to block incoming call due to recent emergency call");
      return false;
    }
    if (call.getDetails().hasProperty(CallCompat.Details.PROPERTY_IS_EXTERNAL_CALL)) {
      return false;
    }
    if (FilteredNumberCompat.useNewFiltering(context)) {
      LogUtil.i(
          "InCallPresenter.shouldAttemptBlocking",
          "not attempting to block incoming call because framework blocking is in use");
      return false;
    }
    return true;
  }

  /**
   * Checks whether a call should be blocked, and blocks it if so. Otherwise, it adds the call to
   * the CallList so it can proceed as normal. There is a timeout, so if the function for checking
   * whether a function is blocked does not return in a reasonable time, we proceed with adding the
   * call anyways.
   */
  private void maybeBlockCall(final android.telecom.Call call, final LatencyReport latencyReport) {
    final String countryIso = GeoUtil.getCurrentCountryIso(context);
    final String number = TelecomCallUtil.getNumber(call);
    final long timeAdded = System.currentTimeMillis();

    // Though AtomicBoolean's can be scary, don't fear, as in this case it is only used on the
    // main UI thread. It is needed so we can change its value within different scopes, since
    // that cannot be done with a final boolean.
    final AtomicBoolean hasTimedOut = new AtomicBoolean(false);

    final Handler handler = new Handler();

    // Proceed if the query is slow; the call may still be blocked after the query returns.
    final Runnable runnable =
        new Runnable() {
          @Override
          public void run() {
            hasTimedOut.set(true);
            latencyReport.onCallBlockingDone();
            callList.onCallAdded(context, call, latencyReport);
          }
        };
    handler.postDelayed(runnable, BLOCK_QUERY_TIMEOUT_MS);

    OnCheckBlockedListener onCheckBlockedListener =
        new OnCheckBlockedListener() {
          @Override
          public void onCheckComplete(final Integer id) {
            if (isReadyForTearDown()) {
              LogUtil.i("InCallPresenter.onCheckComplete", "torn down, not adding call");
              return;
            }
            if (!hasTimedOut.get()) {
              handler.removeCallbacks(runnable);
            }
            if (id == null) {
              if (!hasTimedOut.get()) {
                latencyReport.onCallBlockingDone();
                callList.onCallAdded(context, call, latencyReport);
              }
            } else if (id == FilteredNumberAsyncQueryHandler.INVALID_ID) {
              LogUtil.d(
                  "InCallPresenter.onCheckComplete", "invalid number, skipping block checking");
              if (!hasTimedOut.get()) {
                handler.removeCallbacks(runnable);

                latencyReport.onCallBlockingDone();
                callList.onCallAdded(context, call, latencyReport);
              }
            } else {
              LogUtil.i(
                  "InCallPresenter.onCheckComplete", "Rejecting incoming call from blocked number");
              call.reject(false, null);
              Logger.get(context).logInteraction(InteractionEvent.Type.CALL_BLOCKED);

              /*
               * If mContext is null, then the InCallPresenter was torn down before the
               * block check had a chance to complete. The context is no longer valid, so
               * don't attempt to remove the call log entry.
               */
              if (context == null) {
                return;
              }
              // Register observer to update the call log.
              // BlockedNumberContentObserver will unregister after successful log or timeout.
              BlockedNumberContentObserver contentObserver =
                  new BlockedNumberContentObserver(context, new Handler(), number, timeAdded);
              contentObserver.register();
            }
          }
        };

    filteredQueryHandler.isBlockedNumber(onCheckBlockedListener, number, countryIso);
  }

  public void onCallRemoved(android.telecom.Call call) {
    if (call.getDetails().hasProperty(CallCompat.Details.PROPERTY_IS_EXTERNAL_CALL)) {
      externalCallList.onCallRemoved(call);
    } else {
      callList.onCallRemoved(context, call);
      call.unregisterCallback(callCallback);
    }
  }

  public void onCanAddCallChanged(boolean canAddCall) {
    for (CanAddCallListener listener : canAddCallListeners) {
      listener.onCanAddCallChanged(canAddCall);
    }
  }

  @Override
  public void onWiFiToLteHandover(DialerCall call) {
    if (inCallActivity != null) {
      inCallActivity.showToastForWiFiToLteHandover(call);
    }
  }

  @Override
  public void onHandoverToWifiFailed(DialerCall call) {
    if (inCallActivity != null) {
      inCallActivity.showDialogOrToastForWifiHandoverFailure(call);
    }
  }

  @Override
  public void onInternationalCallOnWifi(@NonNull DialerCall call) {
    LogUtil.enterBlock("InCallPresenter.onInternationalCallOnWifi");
    if (inCallActivity != null) {
      inCallActivity.showDialogForInternationalCallOnWifi(call);
    }
  }

  /**
   * Called when there is a change to the call list. Sets the In-Call state for the entire in-call
   * app based on the information it gets from CallList. Dispatches the in-call state to all
   * listeners. Can trigger the creation or destruction of the UI based on the states that is
   * calculates.
   */
  @Override
  public void onCallListChange(CallList callList) {
    Trace.beginSection("InCallPresenter.onCallListChange");
    if (inCallActivity != null && inCallActivity.isInCallScreenAnimating()) {
      awaitingCallListUpdate = true;
      Trace.endSection();
      return;
    }
    if (callList == null) {
      Trace.endSection();
      return;
    }

    awaitingCallListUpdate = false;

    InCallState newState = getPotentialStateFromCallList(callList);
    InCallState oldState = inCallState;
    LogUtil.d(
        "InCallPresenter.onCallListChange",
        "onCallListChange oldState= " + oldState + " newState=" + newState);

    // If the user placed a call and was asked to choose the account, but then pressed "Home", the
    // incall activity for that call will still exist (even if it's not visible). In the case of
    // an incoming call in that situation, just disconnect that "waiting for account" call and
    // dismiss the dialog. The same activity will be reused to handle the new incoming call. See
    // a bug for more details.
    DialerCall waitingForAccountCall;
    if (newState == InCallState.INCOMING
        && (waitingForAccountCall = callList.getWaitingForAccountCall()) != null) {
      waitingForAccountCall.disconnect();
      // The InCallActivity might be destroyed or not started yet at this point.
      if (isActivityStarted()) {
        inCallActivity.dismissPendingDialogs();
      }
    }

    newState = startOrFinishUi(newState);
    LogUtil.d(
        "InCallPresenter.onCallListChange", "onCallListChange newState changed to " + newState);

    // Set the new state before announcing it to the world
    LogUtil.i(
        "InCallPresenter.onCallListChange",
        "Phone switching state: " + oldState + " -> " + newState);
    inCallState = newState;

    // notify listeners of new state
    for (InCallStateListener listener : listeners) {
      LogUtil.d(
          "InCallPresenter.onCallListChange",
          "Notify " + listener + " of state " + inCallState.toString());
      listener.onStateChange(oldState, inCallState, callList);
    }

    if (isActivityStarted()) {
      final boolean hasCall =
          callList.getActiveOrBackgroundCall() != null || callList.getOutgoingCall() != null;
      inCallActivity.dismissKeyguard(hasCall);
    }

    //SPRD: Add incallui Hd Voice
    if (newState == InCallState.NO_CALLS) {
      InCallUIHdAudioHelper.getInstance(context).removeHdVoiceIcon(context);
      setAutomaticallyMuted(false);   //add for bug967410
      setPreviousMuteState(false);    //add for bug969429
    }
    Trace.endSection();
  }

  /** Called when there is a new incoming call. */
  @Override
  public void onIncomingCall(DialerCall call) {
    Trace.beginSection("InCallPresenter.onIncomingCall");
    InCallState newState = startOrFinishUi(InCallState.INCOMING);
    InCallState oldState = inCallState;

    LogUtil.i(
        "InCallPresenter.onIncomingCall", "Phone switching state: " + oldState + " -> " + newState);
    inCallState = newState;

    Trace.beginSection("listener.onIncomingCall");
    for (IncomingCallListener listener : incomingCallListeners) {
      listener.onIncomingCall(oldState, inCallState, call);
    }
    Trace.endSection();

    if(previousMuteState == true && AudioModeProvider.getInstance().getAudioState().isMuted() == true){ //add for bug969429
      automaticallyMuted = true;
      LogUtil.i("InCallPresenter.onIncomingCall-test","set auto is true");
    }

    Trace.beginSection("onPrimaryCallStateChanged");
    if (inCallActivity != null) {
      // Re-evaluate which fragment is being shown.
      inCallActivity.onPrimaryCallStateChanged();
    }
    Trace.endSection();
    Trace.endSection();
  }

  @Override
  public void onUpgradeToVideo(DialerCall call) {
    if (VideoUtils.hasReceivedVideoUpgradeRequest(call.getVideoTech().getSessionModificationState())
        && inCallState == InCallPresenter.InCallState.INCOMING) {
      LogUtil.i(
          "InCallPresenter.onUpgradeToVideo",
          "rejecting upgrade request due to existing incoming call");
      call.getVideoTech().declineVideoRequest();
    }

    if (inCallActivity != null) {
      // Re-evaluate which fragment is being shown.
      inCallActivity.onPrimaryCallStateChanged();
    }
  }

  @Override
  public void onSessionModificationStateChange(DialerCall call) {
    int newState = call.getVideoTech().getSessionModificationState();
    LogUtil.i("InCallPresenter.onSessionModificationStateChange", "state: %d", newState);
    if (proximitySensor == null) {
      LogUtil.i("InCallPresenter.onSessionModificationStateChange", "proximitySensor is null");
      return;
    }
    proximitySensor.setIsAttemptingVideoCall(
        call.hasSentVideoUpgradeRequest() || call.hasReceivedVideoUpgradeRequest());
    if (inCallActivity != null) {
      // Re-evaluate which fragment is being shown.
      inCallActivity.onPrimaryCallStateChanged();
    }
  }

  /**
   * Called when a call becomes disconnected. Called everytime an existing call changes from being
   * connected (incoming/outgoing/active) to disconnected.
   */
  @Override
  public void onDisconnect(DialerCall call) {
    // UNISOC: modify for bug926625
    if (isActivityStarted() && inCallActivity.IsAnswer()) {
      inCallActivity.dismissPendingDialogs(); //UNISOC Bug 917118 919448
    }
    showDialogOrToastForDisconnectedCall(call);

    // We need to do the run the same code as onCallListChange.
    onCallListChange(callList);

    if (isActivityStarted()) {
      inCallActivity.dismissKeyguard(false);
    }

    if (call.isEmergencyCall()) {
      FilteredNumbersUtil.recordLastEmergencyCallTime(context);
    }

    if (!callList.hasLiveCall()
        && !call.getLogState().isIncoming
        && !isSecretCode(call.getNumber())
        && !call.isVoiceMailNumber()) {
      /*UNISOC: add for bug916945 @{ */
      PhoneAccountHandle phoneAccountHandle = call.getAccountHandle();
      int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
      if (phoneAccountHandle != null) {
          subId = InCallUiUtils.getPhoneSubIdForPhoneAccountHandle(context, phoneAccountHandle);
      }
      PostCall.onCallDisconnected(context, call.getNumber(), call.getConnectTimeMillis(), subId);
      /* @} */
    }

    // SPRD Feature Porting: Add for call recorder feature.
    stopRecorderForDisconnect();

    // SPRD Feature Porting: Vibrate when call connected or disconnected feature.
    InCallUiUtils.vibrateForCallStateChange(context.getApplicationContext(),
            call, InCallUiUtils.VIBRATION_FEEDBACK_FOR_DISCONNECT_PREFERENCES_NAME);

    /* SPRD Feature: Support wifiCall notification. @{ */
    if(!callList.hasLiveCall()
            && call.hasProperty(Details.PROPERTY_WIFI)
            && !mIsWifiConnected
            && context.checkSelfPermission(READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            && !ImsManager.isVolteEnabledByPlatform(context)
            && ImsManager.isWfcEnabledByPlatform(context)
            && InCallUiUtils.isWifiCallNotificationEnabled(context,call)){
        popupWifiCallDialog(1);
    }
        /* @} */
  }

  private boolean isSecretCode(@Nullable String number) {
    return number != null
        && (number.length() <= 8 || number.startsWith("*#*#") || number.endsWith("#*#*"));
  }

  /** Given the call list, return the state in which the in-call screen should be. */
  public InCallState getPotentialStateFromCallList(CallList callList) {

    InCallState newState = InCallState.NO_CALLS;

    if (callList == null) {
      return newState;
    }


    /* SPRD: DSDA @{ */
    if(ImsManagerEx.isDualVoLTERegistered()){
      DialerCall primaryCall = callList.getUserPrimaryCall();
      if (primaryCall != null) {
        if (primaryCall.getState() == DialerCall.State.ONHOLD
                || primaryCall.getState() == DialerCall.State.ACTIVE) {
          newState = InCallState.INCALL;
          return newState;
        } else if (primaryCall.getState() == DialerCall.State.INCOMING) {
          newState = InCallState.INCOMING;
          return newState;
        } else if (primaryCall.getState() == DialerCall.State.DIALING) {
          newState = InCallState.OUTGOING;
          return newState;
        }
      }
    }
    /* @} */
    if (callList.getIncomingCall() != null) {
      newState = InCallState.INCOMING;
    } else if (callList.getWaitingForAccountCall() != null) {
      newState = InCallState.WAITING_FOR_ACCOUNT;
    } else if (callList.getPendingOutgoingCall() != null) {
      newState = InCallState.PENDING_OUTGOING;
    } else if (callList.getOutgoingCall() != null) {
      newState = InCallState.OUTGOING;
    } else if (callList.getActiveCall() != null
        || callList.getBackgroundCall() != null
        || callList.getDisconnectedCall() != null
        || callList.getDisconnectingCall() != null) {
      newState = InCallState.INCALL;
    }

    if (newState == InCallState.NO_CALLS) {
      if (boundAndWaitingForOutgoingCall) {
        return InCallState.PENDING_OUTGOING;
      }
    }

    return newState;
  }

  public boolean isBoundAndWaitingForOutgoingCall() {
    return boundAndWaitingForOutgoingCall;
  }

  public void setBoundAndWaitingForOutgoingCall(boolean isBound, PhoneAccountHandle handle) {
    LogUtil.i(
        "InCallPresenter.setBoundAndWaitingForOutgoingCall",
        "setBoundAndWaitingForOutgoingCall: " + isBound);
    boundAndWaitingForOutgoingCall = isBound;
    themeColorManager.setPendingPhoneAccountHandle(handle);
    if (isBound && inCallState == InCallState.NO_CALLS) {
      inCallState = InCallState.PENDING_OUTGOING;
    }
  }

  public void onShrinkAnimationComplete() {
    if (awaitingCallListUpdate) {
      onCallListChange(callList);
    }
  }

  public void addIncomingCallListener(IncomingCallListener listener) {
    Objects.requireNonNull(listener);
    incomingCallListeners.add(listener);
  }

  public void removeIncomingCallListener(IncomingCallListener listener) {
    if (listener != null) {
      incomingCallListeners.remove(listener);
    }
  }

  public void addListener(InCallStateListener listener) {
    Objects.requireNonNull(listener);
    listeners.add(listener);
  }

  public void removeListener(InCallStateListener listener) {
    if (listener != null) {
      listeners.remove(listener);
    }
  }

  public void addDetailsListener(InCallDetailsListener listener) {
    Objects.requireNonNull(listener);
    detailsListeners.add(listener);
  }

  public void removeDetailsListener(InCallDetailsListener listener) {
    if (listener != null) {
      detailsListeners.remove(listener);
    }
  }

  public void addCanAddCallListener(CanAddCallListener listener) {
    Objects.requireNonNull(listener);
    canAddCallListeners.add(listener);
  }

  public void removeCanAddCallListener(CanAddCallListener listener) {
    if (listener != null) {
      canAddCallListeners.remove(listener);
    }
  }

  public void addOrientationListener(InCallOrientationListener listener) {
    Objects.requireNonNull(listener);
    orientationListeners.add(listener);
  }

  public void removeOrientationListener(InCallOrientationListener listener) {
    if (listener != null) {
      orientationListeners.remove(listener);
    }
  }

  public void addInCallEventListener(InCallEventListener listener) {
    Objects.requireNonNull(listener);
    inCallEventListeners.add(listener);
  }

  public void removeInCallEventListener(InCallEventListener listener) {
    if (listener != null) {
      inCallEventListeners.remove(listener);
    }
  }

  public ProximitySensor getProximitySensor() {
    return proximitySensor;
  }

  public PseudoScreenState getPseudoScreenState() {
    return pseudoScreenState;
  }

  /** Returns true if the incall app is the foreground application. */
  public boolean isShowingInCallUi() {
    if (!isActivityStarted()) {
      return false;
    }
    if (manageConferenceActivity != null && manageConferenceActivity.isVisible()) {
      return true;
    }
    return inCallActivity.isVisible();
  }

  /**
   * Returns true if the activity has been created and is running. Returns true as long as activity
   * is not destroyed or finishing. This ensures that we return true even if the activity is paused
   * (not in foreground).
   */
  public boolean isActivityStarted() {
    return (inCallActivity != null
        && !inCallActivity.isDestroyed()
        && !inCallActivity.isFinishing());
  }

  /**
   * Determines if the In-Call app is currently changing configuration.
   *
   * @return {@code true} if the In-Call app is changing configuration.
   */
  public boolean isChangingConfigurations() {
    return isChangingConfigurations;
  }

  /**
   * Tracks whether the In-Call app is currently in the process of changing configuration (i.e.
   * screen orientation).
   */
  /*package*/
  void updateIsChangingConfigurations() {
    isChangingConfigurations = false;
    if (inCallActivity != null) {
      isChangingConfigurations = inCallActivity.isChangingConfigurations();
    }
    LogUtil.v(
        "InCallPresenter.updateIsChangingConfigurations",
        "updateIsChangingConfigurations = " + isChangingConfigurations);
  }

  void updateNotification() {
    // We need to update the notification bar when we leave the UI because that
    // could trigger it to show again.
    if (statusBarNotifier != null) {
      statusBarNotifier.updateNotification();
    }
  }

  /** Called when the activity goes in/out of the foreground. */
  public void onUiShowing(boolean showing) {
    //UNISOC:add for bug916926
    mIsUiShowing = showing;
    if (proximitySensor != null) {
      proximitySensor.onInCallShowing(showing);
    }

    if (!showing) {
      updateIsChangingConfigurations();
    }

    if (statusBarNotifier != null) {
      /* SPRD: Added for bug656290 @{ */
      Log.d(this, "onUiShowing registerForVowifiRssiChange");
      registerForVowifiRssiChange();
      /* @} */
    }

    for (InCallUiListener listener : inCallUiListeners) {
      listener.onUiShowing(showing);
    }

    if (inCallActivity != null) {
      // Re-evaluate which fragment is being shown.
      inCallActivity.onPrimaryCallStateChanged();
    }
  }

  public void refreshUi() {
    if (inCallActivity != null) {
      // Re-evaluate which fragment is being shown.
      inCallActivity.onPrimaryCallStateChanged();
    }
  }

  public void addInCallUiListener(InCallUiListener listener) {
    inCallUiListeners.add(listener);
  }

  public boolean removeInCallUiListener(InCallUiListener listener) {
    return inCallUiListeners.remove(listener);
  }

  /*package*/
  void onActivityStarted() {
    LogUtil.d("InCallPresenter.onActivityStarted", "onActivityStarted");
    notifyVideoPauseController(true);
    applyScreenTimeout();
    if (statusBarNotifier != null) {
      /* SPRD: Added for bug656290 @{ */
      Log.d(this, "onActivityStarted registerForVowifiRssiChange");
      registerForVowifiRssiChange();
      /* @} */
    }
  }

  /*package*/
  void onActivityStopped() {
    LogUtil.d("InCallPresenter.onActivityStopped", "onActivityStopped");
    notifyVideoPauseController(false);
  }

  private void notifyVideoPauseController(boolean showing) {
    LogUtil.d(
        "InCallPresenter.notifyVideoPauseController",
        "mIsChangingConfigurations=" + isChangingConfigurations);
    if (!isChangingConfigurations) {
      VideoPauseController.getInstance().onUiShowing(showing);
    }
  }

  /** Brings the app into the foreground if possible. */
  public void bringToForeground(boolean showDialpad) {
    // Before we bring the incall UI to the foreground, we check to see if:
    // 1. It is not currently in the foreground
    // 2. We are in a state where we want to show the incall ui (i.e. there are calls to
    // be displayed)
    // If the activity hadn't actually been started previously, yet there are still calls
    // present (e.g. a call was accepted by a bluetooth or wired headset), we want to
    // bring it up the UI regardless.
    if (!isShowingInCallUi() && inCallState != InCallState.NO_CALLS) {
      showInCall(showDialpad, false /* newOutgoingCall */);
    }
  }

  public void onPostDialCharWait(String callId, String chars) {
    if (isActivityStarted()) {
      inCallActivity.showDialogForPostCharWait(callId, chars);
    }
  }

  /**
   * Handles the green CALL key while in-call.
   *
   * @return true if we consumed the event.
   */
  public boolean handleCallKey() {
    LogUtil.v("InCallPresenter.handleCallKey", null);

    // The green CALL button means either "Answer", "Unhold", or
    // "Swap calls", or can be a no-op, depending on the current state
    // of the Phone.

    /** INCOMING CALL */
    final CallList calls = callList;
    final DialerCall incomingCall = calls.getIncomingCall();
    LogUtil.v("InCallPresenter.handleCallKey", "incomingCall: " + incomingCall);

    // (1) Attempt to answer a call
    if (incomingCall != null) {
      incomingCall.answer(VideoProfile.STATE_AUDIO_ONLY);
      return true;
    }

    /** STATE_ACTIVE CALL */
    final DialerCall activeCall = calls.getActiveCall();
    if (activeCall != null) {
      // TODO: This logic is repeated from CallButtonPresenter.java. We should
      // consolidate this logic.
      final boolean canMerge =
          activeCall.can(android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE);
      final boolean canSwap =
          activeCall.can(android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE);

      LogUtil.v(
          "InCallPresenter.handleCallKey",
          "activeCall: " + activeCall + ", canMerge: " + canMerge + ", canSwap: " + canSwap);

      // (2) Attempt actions on conference calls
      if (canMerge) {
        TelecomAdapter.getInstance().merge(activeCall.getId());
        return true;
      } else if (canSwap) {
        TelecomAdapter.getInstance().swap(activeCall.getId());
        return true;
      }
    }

    /** BACKGROUND CALL */
    final DialerCall heldCall = calls.getBackgroundCall();
    if (heldCall != null) {
      // We have a hold call so presumeable it will always support HOLD...but
      // there is no harm in double checking.
      final boolean canHold = heldCall.can(android.telecom.Call.Details.CAPABILITY_HOLD);

      LogUtil.v("InCallPresenter.handleCallKey", "heldCall: " + heldCall + ", canHold: " + canHold);

      // (4) unhold call
      if (heldCall.getState() == DialerCall.State.ONHOLD && canHold) {
        heldCall.unhold();
        return true;
      }
    }

    // Always consume hard keys
    return true;
  }

  /** Clears the previous fullscreen state. */
  public void clearFullscreen() {
    isFullScreen = false;
  }

  /**
   * Changes the fullscreen mode of the in-call UI.
   *
   * @param isFullScreen {@code true} if in-call should be in fullscreen mode, {@code false}
   *     otherwise.
   */
  public void setFullScreen(boolean isFullScreen) {
    setFullScreen(isFullScreen, false /* force */);
  }

  /**
   * Changes the fullscreen mode of the in-call UI.
   *
   * @param isFullScreen {@code true} if in-call should be in fullscreen mode, {@code false}
   *     otherwise.
   * @param force {@code true} if fullscreen mode should be set regardless of its current state.
   */
  public void setFullScreen(boolean isFullScreen, boolean force) {
    LogUtil.i("InCallPresenter.setFullScreen", "setFullScreen = " + isFullScreen);

    // As a safeguard, ensure we cannot enter fullscreen if the dialpad is shown.
    if (isDialpadVisible()) {
      isFullScreen = false;
      LogUtil.v(
          "InCallPresenter.setFullScreen",
          "setFullScreen overridden as dialpad is shown = " + isFullScreen);
    }

    if (this.isFullScreen == isFullScreen && !force) {
      LogUtil.v("InCallPresenter.setFullScreen", "setFullScreen ignored as already in that state.");
      return;
    }
    this.isFullScreen = isFullScreen;
    notifyFullscreenModeChange(this.isFullScreen);
  }

  /**
   * @return {@code true} if the in-call ui is currently in fullscreen mode, {@code false}
   *     otherwise.
   */
  public boolean isFullscreen() {
    return isFullScreen;
  }

  /**
   * Called by the {@link VideoCallPresenter} to inform of a change in full screen video status.
   *
   * @param isFullscreenMode {@code True} if entering full screen mode.
   */
  public void notifyFullscreenModeChange(boolean isFullscreenMode) {
    for (InCallEventListener listener : inCallEventListeners) {
      listener.onFullscreenModeChanged(isFullscreenMode);
    }
  }

  /** Instruct the in-call activity to show an error dialog or toast for a disconnected call. */
  private void showDialogOrToastForDisconnectedCall(DialerCall call) {
    if (!isActivityStarted() || call.getState() != DialerCall.State.DISCONNECTED) {
      return;
    }

    // For newly disconnected calls, we may want to show a dialog on specific error conditions
    if (call.getAccountHandle() == null && !call.isConferenceCall()) {
      setDisconnectCauseForMissingAccounts(call);
    }

    /* SPRD Feature Porting: Voice Clear Code Feature. @{ */
    VoiceClearCodeHelper callFailCauseHelper = VoiceClearCodeHelper.getInstance(context);
    callFailCauseHelper.showToastMessage(context, call.getDisconnectCause().getReason());
    LogUtil.i("InCallPresenter.showDialogOrToastForDisconnectedCall", "call.getDisconnectCause().getReason()=" + call.getDisconnectCause().getReason());
    if (callFailCauseHelper.isSpecialVoiceClearCode(call.getNumber())) {
      return;
    }
    /* @} */
    inCallActivity.showDialogOrToastForDisconnectedCall(
        new DisconnectMessage(inCallActivity, call));
  }

  /**
   * When the state of in-call changes, this is the first method to get called. It determines if the
   * UI needs to be started or finished depending on the new state and does it.
   */
  private InCallState startOrFinishUi(InCallState newState) {
    Trace.beginSection("InCallPresenter.startOrFinishUi");
    LogUtil.d(
        "InCallPresenter.startOrFinishUi", "startOrFinishUi: " + inCallState + " -> " + newState);

    // TODO: Consider a proper state machine implementation

    // If the state isn't changing we have already done any starting/stopping of activities in
    // a previous pass...so lets cut out early
    if (newState == inCallState) {
      Trace.endSection();
      return newState;
    }

    // A new Incoming call means that the user needs to be notified of the the call (since
    // it wasn't them who initiated it).  We do this through full screen notifications and
    // happens indirectly through {@link StatusBarNotifier}.
    //
    // The process for incoming calls is as follows:
    //
    // 1) CallList          - Announces existence of new INCOMING call
    // 2) InCallPresenter   - Gets announcement and calculates that the new InCallState
    //                      - should be set to INCOMING.
    // 3) InCallPresenter   - This method is called to see if we need to start or finish
    //                        the app given the new state.
    // 4) StatusBarNotifier - Listens to InCallState changes. InCallPresenter calls
    //                        StatusBarNotifier explicitly to issue a FullScreen Notification
    //                        that will either start the InCallActivity or show the user a
    //                        top-level notification dialog if the user is in an immersive app.
    //                        That notification can also start the InCallActivity.
    // 5) InCallActivity    - Main activity starts up and at the end of its onCreate will
    //                        call InCallPresenter::setActivity() to let the presenter
    //                        know that start-up is complete.
    //
    //          [ AND NOW YOU'RE IN THE CALL. voila! ]
    //
    // Our app is started using a fullScreen notification.  We need to do this whenever
    // we get an incoming call. Depending on the current context of the device, either a
    // incoming call HUN or the actual InCallActivity will be shown.
    final boolean startIncomingCallSequence = (InCallState.INCOMING == newState);

    // A dialog to show on top of the InCallUI to select a PhoneAccount
    final boolean showAccountPicker = (InCallState.WAITING_FOR_ACCOUNT == newState);

    // A new outgoing call indicates that the user just now dialed a number and when that
    // happens we need to display the screen immediately or show an account picker dialog if
    // no default is set. However, if the main InCallUI is already visible, we do not want to
    // re-initiate the start-up animation, so we do not need to do anything here.
    //
    // It is also possible to go into an intermediate state where the call has been initiated
    // but Telecom has not yet returned with the details of the call (handle, gateway, etc.).
    // This pending outgoing state can also launch the call screen.
    //
    // This is different from the incoming call sequence because we do not need to shock the
    // user with a top-level notification.  Just show the call UI normally.
    boolean callCardFragmentVisible =
        inCallActivity != null && inCallActivity.getCallCardFragmentVisible();
    final boolean mainUiNotVisible = !isShowingInCallUi() || !callCardFragmentVisible;
    boolean showCallUi = InCallState.OUTGOING == newState && mainUiNotVisible;

    // Direct transition from PENDING_OUTGOING -> INCALL means that there was an error in the
    // outgoing call process, so the UI should be brought up to show an error dialog.
    showCallUi |=
        (InCallState.PENDING_OUTGOING == inCallState
            && InCallState.INCALL == newState
            && !isShowingInCallUi());

    // Another exception - InCallActivity is in charge of disconnecting a call with no
    // valid accounts set. Bring the UI up if this is true for the current pending outgoing
    // call so that:
    // 1) The call can be disconnected correctly
    // 2) The UI comes up and correctly displays the error dialog.
    // TODO: Remove these special case conditions by making InCallPresenter a true state
    // machine. Telecom should also be the component responsible for disconnecting a call
    // with no valid accounts.
    showCallUi |=
        InCallState.PENDING_OUTGOING == newState
            && mainUiNotVisible
            && isCallWithNoValidAccounts(callList.getPendingOutgoingCall());
    /* SPRD: add for DSDA @{ */
    showCallUi |= InCallState.WAITING_FOR_ACCOUNT == inCallState
            && InCallState.INCALL == newState && mainUiNotVisible
            && ImsManagerEx.isReadyForDualActiveCall(); // UNISOC: modify for bug921953
    /* @} */


    // The only time that we have an instance of mInCallActivity and it isn't started is
    // when it is being destroyed.  In that case, lets avoid bringing up another instance of
    // the activity.  When it is finally destroyed, we double check if we should bring it back
    // up so we aren't going to lose anything by avoiding a second startup here.
    boolean activityIsFinishing = inCallActivity != null && !isActivityStarted();
    if (activityIsFinishing) {
      LogUtil.i(
          "InCallPresenter.startOrFinishUi",
          "Undo the state change: " + newState + " -> " + inCallState);
      Trace.endSection();
      return inCallState;
    }

    // We're about the bring up the in-call UI for outgoing and incoming call. If we still have
    // dialogs up, we need to clear them out before showing in-call screen. This is necessary
    // to fix the bug that dialog will show up when data reaches limit even after makeing new
    // outgoing call after user ignore it by pressing home button.
    if ((newState == InCallState.INCOMING || newState == InCallState.PENDING_OUTGOING)
        && !showCallUi
        && isActivityStarted()) {
      inCallActivity.dismissPendingDialogs();
    }

    if (showCallUi || showAccountPicker) {
      LogUtil.i("InCallPresenter.startOrFinishUi", "Start in call UI");
      showInCall(false /* showDialpad */, !showAccountPicker /* newOutgoingCall */);
    } else if (startIncomingCallSequence) {
      LogUtil.i("InCallPresenter.startOrFinishUi", "Start Full Screen in call UI");

      try {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerHALManager powerHALManager = new PowerHALManager(context, new Handler());
        PowerHALManager.PowerHintScene sceneIncall = powerHALManager.createPowerHintScene(
                "InCall: InCallPresenter", PowerHintVendorSprd.POWER_HINT_VENDOR_RADIO_CALL, null);
        if (sceneIncall != null && !pm.isScreenOn()) {
          Log.i(this, "power Hint POWER_HINT_VENDOR_RADIO_CALL");
          sceneIncall.acquire(1000);
        }
      } catch (java.lang.NoClassDefFoundError e) {
        LogUtil.e("InCallPresenter.startOrFinishUi", "Exception:" + e.getMessage());
      // UNISOC: add for bug933579
      } catch (java.util.NoSuchElementException e) {
        LogUtil.e("InCallPresenter.startOrFinishUi", "Exception:" + e.getMessage());
      }

      statusBarNotifier.updateNotification();
      /* SPRD: Added for bug656290 @{ */
      registerForVowifiRssiChange();
      /* @} */
    } else if (newState == InCallState.NO_CALLS) {
      // The new state is the no calls state.  Tear everything down.
      attemptFinishActivity();
      attemptCleanup();
    }
    /** SPRD: add for bug693110. Porting Auto Answer Feature. @{
     * If it is INCALL and InCallActivity is not started yet, we should
     * show the InCallActivity. Specially when in auto-answer mode,
     * call state is changed from NO_CALL to INCALL very quickly, and InCallActivity
     * can only be started when user click answer button or receive the answer broadcaster.
     * It may be not the best way to fix the issue.
     */
    else if (newState == InCallState.INCALL &&
            (CallList.getInstance().getActiveCall() != null
                    || CallList.getInstance().getBackgroundCall() != null) && !isShowingInCallUi()) {
      showInCall(false, false);
    }

    Trace.endSection();
    return newState;
  }

  /**
   * Sets the DisconnectCause for a call that was disconnected because it was missing a PhoneAccount
   * or PhoneAccounts to select from.
   */
  private void setDisconnectCauseForMissingAccounts(DialerCall call) {

    Bundle extras = call.getIntentExtras();
    // Initialize the extras bundle to avoid NPE
    if (extras == null) {
      extras = new Bundle();
    }

    final List<PhoneAccountHandle> phoneAccountHandles =
        extras.getParcelableArrayList(android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS);

    if (phoneAccountHandles == null || phoneAccountHandles.isEmpty()) {
      String scheme = call.getHandle().getScheme();
      final String errorMsg =
          PhoneAccount.SCHEME_TEL.equals(scheme)
              ? context.getString(R.string.callFailed_simError)
              : context.getString(R.string.incall_error_supp_service_unknown);
      DisconnectCause disconnectCause =
          new DisconnectCause(DisconnectCause.ERROR, null, errorMsg, errorMsg);
      call.setDisconnectCause(disconnectCause);
    }
  }

  /**
   * @return {@code true} if the InCallPresenter is ready to be torn down, {@code false} otherwise.
   *     Calling classes should use this as an indication whether to interact with the
   *     InCallPresenter or not.
   */
  public boolean isReadyForTearDown() {
    return inCallActivity == null && !serviceConnected && inCallState == InCallState.NO_CALLS;
  }

  /**
   * Checks to see if both the UI is gone and the service is disconnected. If so, tear it all down.
   */
  private void attemptCleanup() {
    if (isReadyForTearDown()) {
      LogUtil.i("InCallPresenter.attemptCleanup", "Cleaning up");

      cleanupSurfaces();

      isChangingConfigurations = false;

      // blow away stale contact info so that we get fresh data on
      // the next set of calls
      if (contactInfoCache != null) {
        contactInfoCache.clearCache();
      }
      contactInfoCache = null;

      if (proximitySensor != null) {
        removeListener(proximitySensor);
        proximitySensor.tearDown();
      }
      proximitySensor = null;

      if (statusBarNotifier != null) {
        removeListener(statusBarNotifier);
        EnrichedCallComponent.get(context)
            .getEnrichedCallManager()
            .unregisterStateChangedListener(statusBarNotifier);
         /* SPRD: Added for bug656290 @{ */
        Log.d(this, "attemptCleanup unregisterForVowifiRssiChange");
        unregisterForVowifiRssiChange();
        /* @} */
      }

      if (externalCallNotifier != null && externalCallList != null) {
        externalCallList.removeExternalCallListener(externalCallNotifier);
      }
      statusBarNotifier = null;

      if (callList != null) {
        callList.removeListener(this);
        callList.removeListener(spamCallListListener);
      }
      callList = null;

      context = null;
      inCallActivity = null;
      manageConferenceActivity = null;

      listeners.clear();
      incomingCallListeners.clear();
      detailsListeners.clear();
      canAddCallListeners.clear();
      orientationListeners.clear();
      inCallEventListeners.clear();
      inCallUiListeners.clear();
      if (!inCallUiLocks.isEmpty()) {
        LogUtil.e("InCallPresenter.attemptCleanup", "held in call locks: " + inCallUiLocks);
        inCallUiLocks.clear();
      }
      LogUtil.d("InCallPresenter.attemptCleanup", "finished");
    }
  }

  public void showInCall(boolean showDialpad, boolean newOutgoingCall) {
    LogUtil.i("InCallPresenter.showInCall", "Showing InCallActivity");
    context.startActivity(
        InCallActivity.getIntent(context, showDialpad, newOutgoingCall, false /* forFullScreen */));
  }

  public void onServiceBind() {
    serviceBound = true;
    // SPRD Feature Porting: Add for shaking phone to start recording.
    ShakePhoneToStartRecordingHelper.getInstance(context).init(context);//SPRD:fix for bug 868304,bug876231
    // UNISOC:Telcel volte csfb clear code
    if(context.getResources().getBoolean(com.android.dialer.app.R.bool.config_is_support_csfb_volteclearcode_feature)) {
        LogUtil.i("InCallPresenter.onServiceBind", "bindRadioInteracter");
        bindRadioInteracter();
    }
  }

  // UNISOC:Telcel volte csfb clear code
  private void bindRadioInteracter() {
      if (context == null) {
          LogUtil.e("InCallPresenter:bindRadioInteracter", "context is null");
          return;
      }

      if (!mRadioInteractorConnection){
          context.bindService(new Intent(
                  "com.android.sprd.telephony.server.RADIOINTERACTOR_SERVICE")
                  .setPackage("com.android.sprd.telephony.server"), new ServiceConnection() {
              private RadioInteractor mRadioInteractor;
              private RadioInteractorCallbackListener[] mRadioInteractorCallbackListener;
              public void onServiceConnected(ComponentName name, IBinder service) {
                  LogUtil.i("InCallPresenter:bindRadioInteracter", "on radioInteractor service connected");
                  if (mRadioInteractor == null) {
                      mRadioInteractor = new RadioInteractor(context);
                  }
                  mRadioInteractorConnection = true;
                  mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                  if (mTelephonyManager != null) {
                      mPhoneCount = mTelephonyManager.getPhoneCount();
                  }
                  if (mRadioInteractorCallbackListener == null) {
                      mRadioInteractorCallbackListener = new RadioInteractorCallbackListener[mPhoneCount];
                  }

                  for (int i = 0; i < mPhoneCount; i++) {
                      mRadioInteractorCallbackListener[i] = getRadioInteractorCallbackListener(i);
                      LogUtil.i("InCallPresenter:bindRadioInteracter", "RadioInteractorCallbackListener.LISTEN_IMS_CSFB_VENDOR_CAUSE_EVENT");
                      mRadioInteractor.listen(mRadioInteractorCallbackListener[i],
                              RadioInteractorCallbackListener.LISTEN_IMS_CSFB_VENDOR_CAUSE_EVENT, false);
                  }
              }
              public void onServiceDisconnected(ComponentName name) {
                  LogUtil.i("InCallPresenter:bindRadioInteracter", "on radioInteractor service disconnect");
                  for (int i = 0; i < mPhoneCount; i++) {
                      mRadioInteractorCallbackListener[i] = getRadioInteractorCallbackListener(i);
                      mRadioInteractor.listen(mRadioInteractorCallbackListener[i],
                              RadioInteractorCallbackListener.LISTEN_NONE, false);
                  }
              }
          }, 0);

      }
  }

  private RadioInteractorCallbackListener getRadioInteractorCallbackListener(int phoneId){
      return new RadioInteractorCallbackListener(phoneId) {
          public void onImsCsfbVendorCauseEvent(Object object) {
            if (object != null) {
                String causCode = (String) ((AsyncResult) object).result;
                LogUtil.i("InCallPresenter:onImsCsfbVendorCauseEvent","cuseCode="+causCode);
                VoiceClearCodeHelper csfbcallFailCauseHelper = VoiceClearCodeHelper.getInstance(context);
                csfbcallFailCauseHelper.showToastMessage(context, causCode);
              }
          }
      };
  }

  public void onServiceUnbind() {
    InCallPresenter.getInstance().setBoundAndWaitingForOutgoingCall(false, null);
    serviceBound = false;
    mRadioInteractorConnection = false;
    // SPRD Feature Porting: Add for shaking phone to start recording.
    ShakePhoneToStartRecordingHelper.getInstance(context).unRegisterTriggerRecorderListener();//SPRD:fix for bug 868304,bug876231
  }

  public boolean isServiceBound() {
    return serviceBound;
  }


  public void maybeStartRevealAnimation(Intent intent) {
    if (intent == null || inCallActivity != null) {
      return;
    }
    final Bundle extras = intent.getBundleExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS);
    if (extras == null) {
      // Incoming call, just show the in-call UI directly.
      return;
    }

    if (extras.containsKey(android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS)) {
      // Account selection dialog will show up so don't show the animation.
      return;
    }

    final PhoneAccountHandle accountHandle =
        intent.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);
    final Point touchPoint = extras.getParcelable(TouchPointManager.TOUCH_POINT);

    InCallPresenter.getInstance().setBoundAndWaitingForOutgoingCall(true, accountHandle);

    final Intent activityIntent =
        InCallActivity.getIntent(context, false, true, false /* forFullScreen */);
    activityIntent.putExtra(TouchPointManager.TOUCH_POINT, touchPoint);
    context.startActivity(activityIntent);
  }

  /**
   * Retrieves the current in-call camera manager instance, creating if necessary.
   *
   * @return The {@link InCallCameraManager}.
   */
  public InCallCameraManager getInCallCameraManager() {
    synchronized (this) {
      if (inCallCameraManager == null) {
        inCallCameraManager = new InCallCameraManager(context);
      }

      return inCallCameraManager;
    }
  }

  /**
   * Notifies listeners of changes in orientation and notify calls of rotation angle change.
   *
   * @param orientation The screen orientation of the device (one of: {@link
   *     InCallOrientationEventListener#SCREEN_ORIENTATION_0}, {@link
   *     InCallOrientationEventListener#SCREEN_ORIENTATION_90}, {@link
   *     InCallOrientationEventListener#SCREEN_ORIENTATION_180}, {@link
   *     InCallOrientationEventListener#SCREEN_ORIENTATION_270}).
   */
  public void onDeviceOrientationChange(@ScreenOrientation int orientation) {
    LogUtil.d(
        "InCallPresenter.onDeviceOrientationChange",
        "onDeviceOrientationChange: orientation= " + orientation);

    if (callList != null) {
      callList.notifyCallsOfDeviceRotation(orientation);
    } else {
      LogUtil.w("InCallPresenter.onDeviceOrientationChange", "CallList is null.");
    }

    // Notify listeners of device orientation changed.
    for (InCallOrientationListener listener : orientationListeners) {
      listener.onDeviceOrientationChanged(orientation);
    }
  }

  /**
   * Configures the in-call UI activity so it can change orientations or not. Enables the
   * orientation event listener if allowOrientationChange is true, disables it if false.
   *
   * @param allowOrientationChange {@code true} if the in-call UI can change between portrait and
   *     landscape. {@code false} if the in-call UI should be locked in portrait.
   */
  public void setInCallAllowsOrientationChange(boolean allowOrientationChange) {
    if (inCallActivity == null) {
      LogUtil.e(
          "InCallPresenter.setInCallAllowsOrientationChange",
          "InCallActivity is null. Can't set requested orientation.");
      return;
    }
    inCallActivity.setAllowOrientationChange(allowOrientationChange);
  }

  public void enableScreenTimeout(boolean enable) {
    LogUtil.i("InCallPresenter.enableScreenTimeout", "enableScreenTimeout: value=" + enable);
    screenTimeoutEnabled = enable;
    applyScreenTimeout();
  }

  private void applyScreenTimeout() {
    if (inCallActivity == null) {
      LogUtil.e("InCallPresenter.applyScreenTimeout", "InCallActivity is null.");
      return;
    }

    final Window window = inCallActivity.getWindow();
    if (screenTimeoutEnabled) {
      window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    } else {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
  }

  /**
   * Hides or shows the conference manager fragment.
   *
   * @param show {@code true} if the conference manager should be shown, {@code false} if it should
   *     be hidden.
   */
  public void showConferenceCallManager(boolean show) {
    if (inCallActivity != null) {
      inCallActivity.showConferenceFragment(show);
    }
    if (!show && manageConferenceActivity != null) {
      manageConferenceActivity.finish();
    }
  }

  /**
   * Determines if the dialpad is visible.
   *
   * @return {@code true} if the dialpad is visible, {@code false} otherwise.
   */
  public boolean isDialpadVisible() {
    if (inCallActivity == null) {
      return false;
    }
    return inCallActivity.isDialpadVisible();
  }

  public ThemeColorManager getThemeColorManager() {
    return themeColorManager;
  }

  @VisibleForTesting
  public void setThemeColorManager(ThemeColorManager themeColorManager) {
    this.themeColorManager = themeColorManager;
  }

  /** Called when the foreground call changes. */
  public void onForegroundCallChanged(DialerCall newForegroundCall) {
    themeColorManager.onForegroundCallChanged(context, newForegroundCall);
    if (inCallActivity != null) {
      inCallActivity.onForegroundCallChanged(newForegroundCall);
    }
  }

  public InCallActivity getActivity() {
    return inCallActivity;
  }

  /** Called when the UI begins, and starts the callstate callbacks if necessary. */
  public void setActivity(InCallActivity inCallActivity) {
    if (inCallActivity == null) {
      throw new IllegalArgumentException("registerActivity cannot be called with null");
    }
    if (this.inCallActivity != null && this.inCallActivity != inCallActivity) {
      LogUtil.w(
          "InCallPresenter.setActivity", "Setting a second activity before destroying the first.");
    }
    updateActivity(inCallActivity);

    // SPRD Feature Porting: Add for call recorder feature.
    mRecorderHelper = PhoneRecorderHelper.getInstance(inCallActivity);  //mInCallActivity
  }

  ExternalCallNotifier getExternalCallNotifier() {
    return externalCallNotifier;
  }

  VideoSurfaceTexture getLocalVideoSurfaceTexture() {
    if (localVideoSurfaceTexture == null) {
      boolean isPixel2017 = false;
      if (context != null) {
        isPixel2017 = context.getPackageManager().hasSystemFeature(PIXEL2017_SYSTEM_FEATURE);
      }
      localVideoSurfaceTexture = VideoSurfaceBindings.createLocalVideoSurfaceTexture(isPixel2017);
    }
    return localVideoSurfaceTexture;
  }

  VideoSurfaceTexture getRemoteVideoSurfaceTexture() {
    if (remoteVideoSurfaceTexture == null) {
      boolean isPixel2017 = false;
      if (context != null) {
        isPixel2017 = context.getPackageManager().hasSystemFeature(PIXEL2017_SYSTEM_FEATURE);
      }
      remoteVideoSurfaceTexture = VideoSurfaceBindings.createRemoteVideoSurfaceTexture(isPixel2017);
    }
    return remoteVideoSurfaceTexture;
  }

  void cleanupSurfaces() {
    if (remoteVideoSurfaceTexture != null) {
      remoteVideoSurfaceTexture.setDoneWithSurface();
      remoteVideoSurfaceTexture = null;
    }
    if (localVideoSurfaceTexture != null) {
      localVideoSurfaceTexture.setDoneWithSurface();
      localVideoSurfaceTexture = null;
    }
  }

  @Override
  public void onAudioStateChanged(CallAudioState audioState) {
    if (statusBarNotifier != null) {
      statusBarNotifier.updateNotification();
    }
  }

  /** All the main states of InCallActivity. */
  public enum InCallState {
    // InCall Screen is off and there are no calls
    NO_CALLS,

    // Incoming-call screen is up
    INCOMING,

    // In-call experience is showing
    INCALL,

    // Waiting for user input before placing outgoing call
    WAITING_FOR_ACCOUNT,

    // UI is starting up but no call has been initiated yet.
    // The UI is waiting for Telecom to respond.
    PENDING_OUTGOING,

    // User is dialing out
    OUTGOING;

    public boolean isIncoming() {
      return (this == INCOMING);
    }

    public boolean isConnectingOrConnected() {
      return (this == INCOMING || this == OUTGOING || this == INCALL);
    }
  }

  /** Interface implemented by classes that need to know about the InCall State. */
  public interface InCallStateListener {

    // TODO: Enhance state to contain the call objects instead of passing CallList
    void onStateChange(InCallState oldState, InCallState newState, CallList callList);
  }

  public interface IncomingCallListener {

    void onIncomingCall(InCallState oldState, InCallState newState, DialerCall call);
  }

  public interface CanAddCallListener {

    void onCanAddCallChanged(boolean canAddCall);
  }

  public interface InCallDetailsListener {

    void onDetailsChanged(DialerCall call, android.telecom.Call.Details details);
  }

  public interface InCallOrientationListener {

    void onDeviceOrientationChanged(@ScreenOrientation int orientation);
  }

  /**
   * Interface implemented by classes that need to know about events which occur within the In-Call
   * UI. Used as a means of communicating between fragments that make up the UI.
   */
  public interface InCallEventListener {

    void onFullscreenModeChanged(boolean isFullscreenMode);
  }

  public interface InCallUiListener {

    void onUiShowing(boolean showing);
  }

  private class InCallUiLockImpl implements InCallUiLock {
    private final String tag;

    private InCallUiLockImpl(String tag) {
      this.tag = tag;
    }

    @MainThread
    @Override
    public void release() {
      Assert.isMainThread();
      releaseInCallUiLock(InCallUiLockImpl.this);
    }

    @Override
    public String toString() {
      return "InCallUiLock[" + tag + "]";
    }
  }

  @MainThread
  public InCallUiLock acquireInCallUiLock(String tag) {
    Assert.isMainThread();
    InCallUiLock lock = new InCallUiLockImpl(tag);
    inCallUiLocks.add(lock);
    return lock;
  }

  @MainThread
  private void releaseInCallUiLock(InCallUiLock lock) {
    Assert.isMainThread();
    LogUtil.i("InCallPresenter.releaseInCallUiLock", "releasing %s", lock);
    inCallUiLocks.remove(lock);
    if (inCallUiLocks.isEmpty()) {
      LogUtil.i("InCallPresenter.releaseInCallUiLock", "all locks released");
      if (inCallState == InCallState.NO_CALLS) {
        LogUtil.i("InCallPresenter.releaseInCallUiLock", "no more calls, finishing UI");
        attemptFinishActivity();
        attemptCleanup();
      }
    }
  }

  @MainThread
  public boolean isInCallUiLocked() {
    Assert.isMainThread();
    if (inCallUiLocks.isEmpty()) {
      return false;
    }
    for (InCallUiLock lock : inCallUiLocks) {
      LogUtil.i("InCallPresenter.isInCallUiLocked", "still locked by %s", lock);
    }
    return true;
  }

  private final Set<InCallUiLock> inCallUiLocks = new ArraySet<>();

  /* SPRD Feature Porting: Add for call record feature @{ */
  public void stopRecorderForDisconnect() {
    if (mRecorderHelper != null) {
      // Stop recorder only when no active call or background call exists.
      DialerCall call = CallList.getInstance().getActiveOrBackgroundCall();
      if (call == null) {
        mRecorderHelper.stop();
      }
    }
  }

  public void toggleRecorder() {
    if (mRecorderHelper == null) {
      mRecorderHelper = PhoneRecorderHelper.getInstance(context); //mContext
    }
    mRecorderHelper.toggleRecorder();
  }

  public boolean isRecording() {
      if (mRecorderHelper != null) {
        PhoneRecorderHelper.State state = mRecorderHelper.getState();
        if (state != null && state.isActive()) {
          return true;
        }
      }
      return false;
    }

  public long getRecordTime() {
    if (mRecorderHelper != null) {
      return mRecorderHelper.getRecordTime();
    }
    return 0;
  }
  /* @} */

  /* SPRD: add rejectmessage action in the notification. @{ */
  public void rejectCallWithStartSms(boolean showRejectMessage) {
    context.startActivity(
            InCallActivity.getShowRejectSMStIntent(context, showRejectMessage));
  }
   /* @} */
      /**
     * SPRD: Modify for bug 876231 @{
     */
    @NeededForReflection
    public void recordClick() {
        LogUtil.d("InCallPresenter.recordClick", "inCallActivity = "+inCallActivity);
        if (inCallActivity != null) {
            inCallActivity.recordClick();
        }
    }
  /* SPRD: Added for bug656290 @{ */
  public void registerForVowifiRssiChange() {
      if (context != null && !mVowifiRssiReceiver) {
          mVowifiRssiReceiver = true;
          Log.d(this, "registerForVowifiRssiChange mVowifRssiReceive:"+mVowifiRssiReceiver);
          iFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
          context.registerReceiver(mNotificationReceiver, iFilter);
      }
  }
  public void unregisterForVowifiRssiChange() {
      if (context != null && mVowifiRssiReceiver) {
          mVowifiRssiReceiver = false;
          Log.i(this, "unregisterForVowifiRssiChange mVowifRssiReceive:"+mVowifiRssiReceiver);
          context.unregisterReceiver(mNotificationReceiver);
      }
  }
  public void setSignalLevel(int level, int rssi){
      if (statusBarNotifier != null) {
          statusBarNotifier.setSignalLevel(level);
      }
      if(mRssi != rssi){
            mRssi = rssi;
            if(mRssi <= context.getResources().getInteger(R.integer.wifi_weak_notification)
                    && context.checkSelfPermission(READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                    && !ImsManager.isVolteEnabledByPlatform(context)
                    && ImsManager.isWfcEnabledByPlatform(context)
                    && getPotentialCallFromCallList() != null
                    && getPotentialCallFromCallList().hasProperty(Details.PROPERTY_WIFI)
                    && InCallUiUtils.isWifiCallNotificationEnabled(context,getPotentialCallFromCallList())){
                popupWifiCallDialog(0);
            }
        }
  }
  /* @} */
 /*SPRD add for bug937619 @{*/
  private IImsServiceEx mIImsServiceEx;
  private boolean mIsImsListenerRegistered = false;
  private boolean mIsVideoEnable = false;

  public void tryRegisterImsListener(Context context) {
    if (!ImsManager.isVolteEnabledByPlatform(context) && !ImsManager.isWfcEnabledByPlatform(context)) {
      return;
    }
    mIImsServiceEx = ImsManagerEx.getIImsServiceEx();
    if (mIImsServiceEx != null) {
      try {
        if (!mIsImsListenerRegistered) {
          mIImsServiceEx.registerforImsRegisterStateChanged(mImsUtListenerExBinder);
          mIsImsListenerRegistered = true;
        }
      } catch (RemoteException e) {
        LogUtil.e("InCallPresenter.tryRegisterImsListener", "tryRegisterImsListener error: " + e);
      }
    }
  }

  public void unRegisterImsListener(Context context) {
    if (ImsManager.isVolteEnabledByPlatform(context) || ImsManager.isWfcEnabledByPlatform(context)) {
      try {
        if (mIsImsListenerRegistered) {
          mIImsServiceEx.unregisterforImsRegisterStateChanged(mImsUtListenerExBinder);
          mIsImsListenerRegistered = false;
        }
      } catch (RemoteException e) {
        LogUtil.e("InCallPresenter.unRegisterImsListener", "unRegisterImsListener: " + e);
      }
    }
  }

  private IImsRegisterListener.Stub
          mImsUtListenerExBinder = new IImsRegisterListener.Stub() {
    @Override
    public void imsRegisterStateChange(boolean isRegistered) {
      LogUtil.d("InCallPresenter.imsRegisterStateChange", "isRegistered = " + isRegistered);

      mIsVideoEnable = CallUtil.isVideoEnabled(context);
      LogUtil.d("InCallPresenter.imsRegisterStateChange", "mIsVideoEnable = " + mIsVideoEnable);
    }
  };

  public boolean isVideoEnabled() {
    return mIsVideoEnable;
  }
  /*@}*/


  public void setWifiNetworkState(boolean isWifiConnected) {
        mIsWifiConnected = isWifiConnected;
  }
  /**
   * UNISOC : add for bug 905849
   * Update the state of loudspeaker and mute on notification.
   */
  public void muteNotificationUpdate(boolean isChecked) {
    if (callList == null)
      return;
    //Modify for bug 905849,  update the state of mute on notification.
    if (statusBarNotifier != null) {
      statusBarNotifier.setIsCallStateMute(isChecked);
      statusBarNotifier.updateNotification();
    }
  }
  /* SPRD Feature: Support wifiCall notification. @{ */
  public DialerCall getPotentialCallFromCallList() {
      DialerCall call = null;

      if (callList == null) {
          return null;
      }

      if (callList.getIncomingCall() != null) {
          call = callList.getIncomingCall();
      } else if (callList.getWaitingForAccountCall() != null) {
          call = callList.getWaitingForAccountCall();
      } else if (callList.getPendingOutgoingCall() != null) {
          call = callList.getPendingOutgoingCall();
      } else if (callList.getOutgoingCall() != null) {
          call = callList.getOutgoingCall();
      } else if (callList.getActiveCall() != null){
          call = callList.getActiveCall();
      }else if (callList.getBackgroundCall() != null) {
          call = callList.getBackgroundCall();
      }
      return call;
  }
  private void popupWifiCallDialog(int dialogType) {
      Intent intent = new Intent(context, WifiCallDialog.class);
      intent.putExtra(DIALOG_TYPE_KEY, dialogType);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
      context.startActivity(intent);
  }
  /* @} */

  /*UNISOC: add for bug905849{@*/
  public void setAutomaticallyMuted(boolean isAutomaticallyMuted) {
    automaticallyMuted = isAutomaticallyMuted;
  }
  public boolean isAutomaticallyMuted() {
    return automaticallyMuted;
  }
  /*UNISOC: add for bug905796{@*/
  public void setPreviousMuteState(boolean previousMuteState) {
    this.previousMuteState = previousMuteState;
  }

  public boolean isPreviousMuteState() {
      return previousMuteState;
  }

  /*add for bug906119 { @*/
  public void setPreviewSurfaceState(int previewSurfaceState) {
    mPreviewSurfaceState = previewSurfaceState;
  }

  public int getPreviewSurfaceState() {
    return mPreviewSurfaceState;
  }

  /*UNISOC:add for bug916926 { @*/
  public boolean isUiShowing() {
    return mIsUiShowing;
  }
  /* @} */
}
