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
 * limitations under the License.
 */

package com.android.incallui;

import android.app.ActivityManager;
import android.app.LowmemoryUtils;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build.VERSION_CODES;
import android.support.annotation.RequiresApi;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.util.CallUtil;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.TelecomAdapter;

/**
 * Accepts broadcast Intents which will be prepared by {@link StatusBarNotifier} and thus sent from
 * the notification manager. This should be visible from outside, but shouldn't be exported.
 */
public class NotificationBroadcastReceiver extends BroadcastReceiver {

  /**
   * Intent Action used for hanging up the current call from Notification bar. This will choose
   * first ringing call, first active call, or first background call (typically in STATE_HOLDING
   * state).
   */
  public static final String ACTION_DECLINE_INCOMING_CALL =
      "com.android.incallui.ACTION_DECLINE_INCOMING_CALL";

  public static final String ACTION_HANG_UP_ONGOING_CALL =
      "com.android.incallui.ACTION_HANG_UP_ONGOING_CALL";
  public static final String ACTION_ANSWER_VIDEO_INCOMING_CALL =
      "com.android.incallui.ACTION_ANSWER_VIDEO_INCOMING_CALL";
  public static final String ACTION_ANSWER_VOICE_INCOMING_CALL =
      "com.android.incallui.ACTION_ANSWER_VOICE_INCOMING_CALL";
  public static final String ACTION_ACCEPT_VIDEO_UPGRADE_REQUEST =
      "com.android.incallui.ACTION_ACCEPT_VIDEO_UPGRADE_REQUEST";
  public static final String ACTION_DECLINE_VIDEO_UPGRADE_REQUEST =
      "com.android.incallui.ACTION_DECLINE_VIDEO_UPGRADE_REQUEST";
  public static final String ACTION_TURN_ON_SPEAKER = "com.android.incallui.ACTION_TURN_ON_SPEAKER";
  public static final String ACTION_TURN_OFF_SPEAKER =
      "com.android.incallui.ACTION_TURN_OFF_SPEAKER";
  /* SPRD: Add Mute action for feature FL1000060393 @{ */
  public static final String ACTION_TURN_ON_MUTE = "com.android.incallui.ACTION_TURN_ON_MUTE";
  public static final String ACTION_TURN_OFF_MUTE =
          "com.android.incallui.ACTION_TURN_OFF_MUTE";
  /* }@ */
  /* SPRD: add rejectmessage action in the notification. @{ */
  public static final String ACTION_REJECT_MESSAGE_INCOMING_CALL =
          "com.android.incallui.ACTION_REJECT_MESSAGE_INCOMING_CALL";
  /* @} */

  @RequiresApi(VERSION_CODES.N_MR1)
  public static final String ACTION_PULL_EXTERNAL_CALL =
      "com.android.incallui.ACTION_PULL_EXTERNAL_CALL";

  public static final String EXTRA_NOTIFICATION_ID =
      "com.android.incallui.extra.EXTRA_NOTIFICATION_ID";

  private final String DIALOG_TYPE_KEY = "dialog_type_key";

  @Override
  public void onReceive(Context context, Intent intent) {
    final String action = intent.getAction();
    LogUtil.i("NotificationBroadcastReceiver.onReceive", "Broadcast from Notification: " + action);

    // TODO: Commands of this nature should exist in the CallList.
    if (action.equals(ACTION_ANSWER_VIDEO_INCOMING_CALL)) {
      answerIncomingCall(context, VideoProfile.STATE_BIDIRECTIONAL);
    } else if (action.equals(ACTION_ANSWER_VOICE_INCOMING_CALL)) {
      answerIncomingCall(context, VideoProfile.STATE_AUDIO_ONLY);
    } else if (action.equals(ACTION_DECLINE_INCOMING_CALL)) {
      Logger.get(context)
          .logImpression(DialerImpression.Type.REJECT_INCOMING_CALL_FROM_NOTIFICATION);
      declineIncomingCall();
    } else if (action.equals(ACTION_HANG_UP_ONGOING_CALL)) {
      hangUpOngoingCall();
    } else if (action.equals(ACTION_ACCEPT_VIDEO_UPGRADE_REQUEST)) {
      acceptUpgradeRequest(context);
    } else if (action.equals(ACTION_DECLINE_VIDEO_UPGRADE_REQUEST)) {
      declineUpgradeRequest();
    } else if (action.equals(ACTION_PULL_EXTERNAL_CALL)) {
      context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
      int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
      InCallPresenter.getInstance().getExternalCallNotifier().pullExternalCall(notificationId);
      /* SPRD: Added for bug656290 @{ */
    } else if (action.equals(WifiManager.RSSI_CHANGED_ACTION)) {
        int rssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, -200);
        int level = WifiManager.calculateSignalLevel(rssi, 5);
        Log.i(this, "onReceive RSSI_CHANGED_ACTION"+level);
        InCallPresenter.getInstance().setSignalLevel(level, rssi);
        /* @} */
    } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
        final android.net.NetworkInfo networkInfo = (NetworkInfo)
        intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        boolean isWifiConnected = (networkInfo != null && networkInfo.isConnected());
        Log.i(this, "onReceive NETWORK_STATE_CHANGED_ACTION: mIsWifiConnected=" + isWifiConnected);
        InCallPresenter.getInstance().setWifiNetworkState(isWifiConnected);
        /* @} */
    } else if (action.equals(ACTION_TURN_ON_SPEAKER)) {
      TelecomAdapter.getInstance().setAudioRoute(CallAudioState.ROUTE_SPEAKER);
    } else if (action.equals(ACTION_TURN_OFF_SPEAKER)) {
      TelecomAdapter.getInstance().setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE);
    /* SPRD: Add Mute action for feature FL1000060393 @{ */
    } else if (action.equals(ACTION_TURN_ON_MUTE)) {
      // UNISOC: add for bug905796
      InCallPresenter.getInstance().setPreviousMuteState(true);
      TelecomAdapter.getInstance().mute(true);
    } else if (action.equals(ACTION_TURN_OFF_MUTE)) {
      // UNISOC: add for bug905796
      InCallPresenter.getInstance().setPreviousMuteState(false);
      TelecomAdapter.getInstance().mute(false);
    /* @} */
    /* SPRD: add rejectmessage action in the notification. @{ */
    } else if (action.equals(ACTION_REJECT_MESSAGE_INCOMING_CALL)) {
      InCallPresenter.getInstance().rejectCallWithStartSms(true);
    }
    /* @} */
  }

  private void acceptUpgradeRequest(Context context) {
    CallList callList = InCallPresenter.getInstance().getCallList();
    if (callList == null) {
      StatusBarNotifier.clearAllCallNotifications();
      LogUtil.e("NotificationBroadcastReceiver.acceptUpgradeRequest", "call list is empty");
    } else {
      DialerCall call = callList.getVideoUpgradeRequestCall();
      if (call != null) {
        call.getVideoTech().acceptVideoRequest(context);
      }
    }
  }

  private void declineUpgradeRequest() {
    CallList callList = InCallPresenter.getInstance().getCallList();
    if (callList == null) {
      StatusBarNotifier.clearAllCallNotifications();
      LogUtil.e("NotificationBroadcastReceiver.declineUpgradeRequest", "call list is empty");
    } else {
      DialerCall call = callList.getVideoUpgradeRequestCall();
      if (call != null) {
        call.getVideoTech().declineVideoRequest();
      }
    }
  }

  private void hangUpOngoingCall() {
    CallList callList = InCallPresenter.getInstance().getCallList();
    if (callList == null) {
      StatusBarNotifier.clearAllCallNotifications();
      LogUtil.e("NotificationBroadcastReceiver.hangUpOngoingCall", "call list is empty");
    } else {
      DialerCall call = callList.getOutgoingCall();
      if (call == null) {
        call = callList.getActiveOrBackgroundCall();
      }
      LogUtil.i(
          "NotificationBroadcastReceiver.hangUpOngoingCall", "disconnecting call, call: " + call);
      if (call != null) {
        call.disconnect();
      }
    }
  }

  private void answerIncomingCall(Context context, int videoState) {
    CallList callList = InCallPresenter.getInstance().getCallList();
    if (callList == null) {
      StatusBarNotifier.clearAllCallNotifications();
      LogUtil.e("NotificationBroadcastReceiver.answerIncomingCall", "call list is empty");
    } else {
      DialerCall call = callList.getIncomingCall();
      if (call != null) {
        /* SPRD: Kill font app when lowMemory. @{ */
        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        if (memoryInfo != null && memoryInfo.lowMemory) {
          LowmemoryUtils.killStopFrontApp(LowmemoryUtils.KILL_STOP_FRONT_APP);
          LogUtil.i("NotificationBroadcastReceiver.answerIncomingCall", "killStopFrontApp : KILL_STOP_FRONT_APP");
        }
        /* @} */
 //       call.answer(videoState);
        /* SPRD FL0108020020: Add feature of low battery for Reliance @{ */
        if (CallUtil.isBatteryLow(context) && VideoProfile.isBidirectional(videoState)) {
          CallUtil.showLowBatteryInCallDialog(context, call.getTelecomCall());
        } else {
          call.answer(videoState);
        }
        /* @} */
        InCallPresenter.getInstance()
            .showInCall(false /* showDialpad */, false /* newOutgoingCall */);
      }
    }
  }

  private void declineIncomingCall() {
    CallList callList = InCallPresenter.getInstance().getCallList();
    if (callList == null) {
      StatusBarNotifier.clearAllCallNotifications();
      LogUtil.e("NotificationBroadcastReceiver.declineIncomingCall", "call list is empty");
    } else {
      DialerCall call = callList.getIncomingCall();
      if (call != null) {
        call.reject(false /* rejectWithMessage */, null);
      }
    }
  }
}
