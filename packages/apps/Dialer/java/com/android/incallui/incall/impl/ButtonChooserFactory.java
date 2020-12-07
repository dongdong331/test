/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.incallui.incall.impl;

import android.content.Context;
import android.support.v4.util.ArrayMap;
import android.telephony.TelephonyManager;
import com.android.incallui.incall.impl.MappedButtonConfig.MappingInfo;
import com.android.incallui.incall.protocol.InCallButtonIds;
import com.android.incallui.sprd.InCallUiUtils;
import java.util.Map;

/**
 * Creates {@link ButtonChooser} objects, based on the current network and phone type.
 */
class ButtonChooserFactory {

  /**
   * Creates the appropriate {@link ButtonChooser} based on the given information.
   *
   * @param voiceNetworkType the result of a call to {@link TelephonyManager#getVoiceNetworkType()}.
   * @param isWiFi {@code true} if the call is made over WiFi, {@code false} otherwise.
   * @param phoneType the result of a call to {@link TelephonyManager#getPhoneType()}.
   * @return the ButtonChooser.
   */
  public static ButtonChooser newButtonChooser(
      int voiceNetworkType, boolean isWiFi, int phoneType, Context context) {
    if (voiceNetworkType == TelephonyManager.NETWORK_TYPE_LTE || isWiFi) {
      return newImsAndWiFiButtonChooser(context);
    }

    if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
      return newCdmaButtonChooser(context);
    }

    if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
      return newGsmButtonChooser(context);
    }

    return newImsAndWiFiButtonChooser(context);
  }

  private static ButtonChooser newImsAndWiFiButtonChooser(Context context) {
    Map<Integer, MappingInfo> mapping = createCommonMapping(context);
    /* SPRD Feature Porting: Add for call recorder feature. @{ */
    mapping.put(
        InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE,
        MappingInfo.builder(5).setSlotOrder(0).build());
    mapping.put(
        InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO, MappingInfo.builder(5).setSlotOrder(10).build());
    mapping.put(
        InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY, MappingInfo.builder(6).setSlotOrder(0).build());
    mapping.put(InCallButtonIds.BUTTON_HOLD, MappingInfo.builder(6).setSlotOrder(10).build());
    // SPRD Feature Porting: Enable send sms in incallui feature.
    mapping.put(InCallButtonIds.BUTTON_SEND_MESSAGE, MappingInfo.builder(7).build());
    // SPRD Feature Porting: FL0108160005 Hangup all calls for orange case.
    mapping.put(InCallButtonIds.BUTTON_HANGUP_ALL, MappingInfo.builder(8).build());
    // SPRD Feature Porting: Explicit Call Transfer.
    mapping.put(InCallButtonIds.BUTTON_ECT, MappingInfo.builder(9).build());
    /* SPRD Feature Porting: Add for call invite feature. @{ */
    mapping.put(InCallButtonIds.BUTTON_INVITE, MappingInfo.builder(10).build());
    /* @} */
    return new ButtonChooser(new MappedButtonConfig(mapping));
  }

  private static ButtonChooser newCdmaButtonChooser(Context context) {
    Map<Integer, MappingInfo> mapping = createCommonMapping(context);
     /* SPRD Feature Porting: Add for call recorder feature. @{ */
    mapping.put(
        InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE,
        MappingInfo.builder(5).setSlotOrder(0).build());
    mapping.put(
        InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO, MappingInfo.builder(5).setSlotOrder(10).build());
    mapping.put(InCallButtonIds.BUTTON_SWAP, MappingInfo.builder(6).setSlotOrder(0).build());
    // For multi-sim devices, the first sim's phoneType is used so hold button might be not
    // available for CDMA + GSM devices calling with GSM sim. Adding hold button as low priority
    // here to let telecom control whether it should be shown.
    mapping.put(InCallButtonIds.BUTTON_HOLD, MappingInfo.builder(6).setSlotOrder(5).build());
    mapping.put(
        InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY,
        MappingInfo.builder(6)
            .setSlotOrder(Integer.MAX_VALUE)
            .setMutuallyExclusiveButton(InCallButtonIds.BUTTON_SWAP)
            .build());
    // SPRD Feature Porting: Enable send sms in incallui feature.
    mapping.put(InCallButtonIds.BUTTON_SEND_MESSAGE, MappingInfo.builder(7).build());

    // SPRD Feature Porting: FL0108160005 Hangup all calls for orange case.
    mapping.put(InCallButtonIds.BUTTON_HANGUP_ALL, MappingInfo.builder(8).build());

    // SPRD Feature Porting: Explicit Call Transfer
    mapping.put(InCallButtonIds.BUTTON_ECT, MappingInfo.builder(9).build());

    return new ButtonChooser(new MappedButtonConfig(mapping));
  }

  private static ButtonChooser newGsmButtonChooser(Context context) {
    Map<Integer, MappingInfo> mapping = createCommonMapping(context);
     /* SPRD Feature Porting: Add for call recorder feature. @{ */
    mapping.put(
        InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY, MappingInfo.builder(5).setSlotOrder(0).build());
    mapping.put(
        InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO, MappingInfo.builder(5).setSlotOrder(10).build());

    /*
     * Unlike the other configurations, MANAGE_VOICE_CONFERENCE shares a spot with HOLD for GSM.
     * On GSM, pressing hold while there's a background call just swaps to the background call. It
     * doesn't make sense to show both SWITCH_TO_SECONDARY and HOLD when they do the same thing, so
     * we show MANAGE_VOICE_CONFERENCE instead. Previously MANAGE_VOICE_CONFERENCE would not show.
     */
    mapping.put(
        InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE,
        MappingInfo.builder(6).setSlotOrder(0).build());
    mapping.put(InCallButtonIds.BUTTON_HOLD, MappingInfo.builder(6).setSlotOrder(5).build());
    // SPRD Feature Porting: Enable send sms in incallui feature.
    mapping.put(InCallButtonIds.BUTTON_SEND_MESSAGE, MappingInfo.builder(7).build());
    // SPRD Feature Porting: FL0108160005 Hangup all calls for orange case.
    mapping.put(InCallButtonIds.BUTTON_HANGUP_ALL, MappingInfo.builder(8).build());
    // SPRD Feature Porting: Explicit Call Transfer.
    mapping.put(InCallButtonIds.BUTTON_ECT, MappingInfo.builder(9).build());
    return new ButtonChooser(new MappedButtonConfig(mapping));
  }

  private static Map<Integer, MappingInfo> createCommonMapping(Context context) {
    Map<Integer, MappingInfo> mapping = new ArrayMap<>();
     /* SPRD Feature Porting: Add for call recorder feature. @{ */
    mapping.put(InCallButtonIds.BUTTON_RECORD, MappingInfo.builder(0).build());
    // SPRD: InCallUI Layout Refactor
    // SPRD Feature Porting: Hide recorder feature for telstra case.
    if (InCallUiUtils.isRecorderEnabled(context)) {
      mapping.put(InCallButtonIds.BUTTON_DIALPAD, MappingInfo.builder(1).build());
      mapping.put(InCallButtonIds.BUTTON_MUTE, MappingInfo.builder(2).build());
    } else {
      mapping.put(InCallButtonIds.BUTTON_MUTE, MappingInfo.builder(1).build());
      mapping.put(InCallButtonIds.BUTTON_DIALPAD, MappingInfo.builder(2).build());
    }   
    mapping.put(InCallButtonIds.BUTTON_AUDIO, MappingInfo.builder(3).build());
    mapping.put(InCallButtonIds.BUTTON_MERGE, MappingInfo.builder(4).setSlotOrder(0).build());
    mapping.put(InCallButtonIds.BUTTON_ADD_CALL, MappingInfo.builder(4).build());
    mapping.put(InCallButtonIds.BUTTON_SWAP_SIM, MappingInfo.builder(5).build());
    return mapping;
  }
}
