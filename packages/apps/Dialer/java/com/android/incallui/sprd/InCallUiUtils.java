package com.android.incallui.sprd;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SprdSensor;
import android.telecom.Call.Details;//UNISOC:add for bug1088195
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManagerEx;
import android.telephony.PhoneNumberUtilsEx;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManagerEx;
import com.android.dialer.common.LogUtil;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.android.contacts.common.util.ContactDisplayUtils;//UNISOC:add for bug940943
import com.android.contacts.common.preference.ContactsPreferences;//UNISOC:add for bug940943
import com.android.dialer.app.R;

/* SPRD Feature Porting: Display caller address for phone number feature. @{ */
import android.widget.TextView;

import com.android.dialer.location.GeoUtil;
import com.android.dialer.util.DialerUtils;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.ims.internal.ImsManagerEx;
import com.android.incallui.ContactInfoCache;//UNISOC:add for bug940943
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.sprd.plugin.CallerAddress.CallerAddressHelper;

import static android.Manifest.permission.READ_PHONE_STATE;
/* @} */

/**
 * General purpose utility methods for the InCallUI.
 */
public class InCallUiUtils {
    /* SPRD Feature Porting: Vibrate when call connected or disconnected feature. @{ */
    public static final String VIBRATION_FEEDBACK_FOR_DISCONNECT_PREFERENCES_NAME =
            "call_disconnection_prompt_key";
    public static final String VIBRATION_FEEDBACK_FOR_CONNECT_PREFERENCES_NAME =
            "call_connection_prompt_key";
    public static final int VIBRATE_DURATION = 100; // vibrate for 100ms.
    /* @} */

    private static HashMap<Integer, SubscriptionInfo> sSubInfoMap =
            new HashMap<Integer, SubscriptionInfo>();
    private static List<SubscriptionInfo> sSubInfos = new ArrayList<SubscriptionInfo>();
    private static boolean sPermissionFlag = false;

    //add for bug930591
    private static final char PREFIX_PLUS = '+';
    private static final String PREFIX_DOUBLE_ZERO = "00";

    /**
     * SPRD: Flip to silence from incoming calls.
     */
    private static final String ACTION_SILENT_CALL_BY_FILPING =
            "android.telecom.action.SILENT_CALL_BY_FILPING";
    private static final String TELECOM_PACKAGE = "com.android.server.telecom";
    public static final int FLAG_SILENT_FLIPING = 0;
    /**
    *SPRD Feature Porting: Fade in ringer volume when incoming calls
    */
    private static final String ACTION_FADE_IN_RINGER = "android.telecom.action.FADE_IN_RINGER";
    public static final int FLAG_FADE_IN = 1;

    public static boolean isSupportMp3ForCallRecord(Context context) {
        if (context.getResources().getBoolean(R.bool.config_support_mp3_for_call_recorder)) {
            return true;
        }
        return false;
    }


    public static synchronized SubscriptionInfo getActiveSubscriptionInfo(
            Context context, int slotId, boolean forceReload) {

        LogUtil.d("InCallUiUtils.getActiveSubscriptionInfo", "forceReload = "
                + forceReload + " sPermissionFlag = " + sPermissionFlag);

        if ((forceReload || !sPermissionFlag)
                && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {

            sPermissionFlag = true;
            InCallUiUtils.sSubInfoMap.clear();
            final SubscriptionManager subScriptionManager = SubscriptionManager.from(context);
            List<SubscriptionInfo> subInfos = subScriptionManager.getActiveSubscriptionInfoList();
            sSubInfos = subInfos;
            if (subInfos != null) {
                for (SubscriptionInfo subInfo : subInfos) {
                    int phoneId = subInfo.getSimSlotIndex();
                    InCallUiUtils.sSubInfoMap.put(phoneId, subInfo);
                }
            }
        }

        return InCallUiUtils.sSubInfoMap.get(slotId);
    }

     /**
     * Add method to get phone id by PhoneAccountHandle.
     */
    public static int getPhoneIdByAccountHandle(Context context,
        PhoneAccountHandle phoneAcountHandle) {
        if (phoneAcountHandle != null) {
            String iccId = phoneAcountHandle.getId();
            List<SubscriptionInfo> result = DialerUtils.getActiveSubscriptionInfoList(context);

            if (result != null) {
                for (SubscriptionInfo subInfo : result) {
                    if (iccId != null && subInfo != null && iccId.equals(subInfo.getIccId())) {
                        return subInfo.getSimSlotIndex();
                    }
                }
            // UNISOC: add for bug931964
            } else {
                LogUtil.i("InCallUiUtils.getPhoneIdByAccountHandle", "active subscription info list is null");
            }
        }
        return -1;
    }

    public static List<SubscriptionInfo> getActiveSubscriptionInfoList(Context context) {
        if (!sPermissionFlag) {
            getActiveSubscriptionInfo(context, -1, true);
        }
        return sSubInfos;
    }

    /**
     * SPRD: Add switch for automatic record feature.
     */
    public static boolean isSupportAutomaticCallRecord(Context context) {
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        if (configManager.getConfigForDefaultPhone() != null) {
            return configManager.getConfigForDefaultPhone().getBoolean(
                    CarrierConfigManagerEx.KEY_FEATURE_AUTOMATIC_CALL_RECORD_ENABLED_BOOL);
        }
        return false;
    }
    /* @} */

    /* SPRD Feature Porting: Flip to silence from incoming calls. @{ */
    public static boolean isFlipToSilentCallEnabled(Context context) {
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        if (configManager.getConfigForDefaultPhone() != null) {
            return configManager.getConfigForDefaultPhone().getBoolean(
                    CarrierConfigManagerEx.KEY_FEATURE_FLIP_SILENT_INCOMING_CALL_ENABLED_BOOL);
        }
        return false;
    }


    public static boolean isSupportFlipToMute(Context context) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(
                Context.SENSOR_SERVICE);
        Sensor sensor = sensorManager.getDefaultSensor(SprdSensor.TYPE_SPRDHUB_FLIP);
        if (sensor == null) {
            return false;
        }
        return true;
    }

    public static Intent getIntentForStartingActivity(int flag) {
        Intent intent = null;
        if (flag == FLAG_SILENT_FLIPING) {
            intent = new Intent(ACTION_SILENT_CALL_BY_FILPING);
            intent.addFlags(FLAG_SILENT_FLIPING);
        }else{
            intent = new Intent(ACTION_FADE_IN_RINGER);
            intent.addFlags(FLAG_FADE_IN);
        }

        intent.setPackage(TELECOM_PACKAGE);
        return intent;
    }
    /* @} */

    /* SPRD Feature Porting: Show call elapsed time feature. @{ */
    public static boolean isShowCallElapsedTime(Context context) {
        return context.getResources().getBoolean(R.bool.config_is_show_call_elapsed_time_feature);
    }
    /* @} */

    /* SPRD Feature Porting: Hide recorder feature . @{ */
    public static boolean isRecorderEnabled(Context context) {
        return context.getResources().getBoolean(R.bool.config_is_recorder_enabled_feature);
    }
    /* @} */

    /**
     * SPRD: Add method to get SubId from PhoneAccountHandle.
     */
    public static int getSubIdForPhoneAccountHandle(Context context, PhoneAccountHandle handle) {
        List<SubscriptionInfo> result = DialerUtils.getActiveSubscriptionInfoList(context);  //modify for bug977784

        if (result != null) {
            String iccId = handle.getId();
            for (SubscriptionInfo subInfo : result) {
                if (iccId != null && subInfo != null && iccId.equals(subInfo.getIccId())) {
                    return subInfo.getSubscriptionId();
                }
            }
        }

        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    /**
     * UNISOC:add for bug1005308.Add method to get SlotId from PhoneAccountHandle.
     */
    public static int getSlotIdForPhoneAccountHandle(Context context, PhoneAccountHandle handle) {
        if (handle != null) {
            String iccId = handle.getId();

            SubscriptionInfo subInfo = SubscriptionManager.from(
                    context).getActiveSubscriptionInfoForIccIndex(iccId);
            if (subInfo != null) {
                return subInfo.getSimSlotIndex();
            }
        }
        return SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    }

    /* SPRD: add for feature FL1000060357 */
    public static boolean shouldShowConferenceWithOneParticipant(Context context) {
        return context.getResources().getBoolean(R.bool.config_is_show_conference_manager);
    }

    /*SPRD: add for feature FL1000062299 {@*/
    public static boolean shouldUpdateConferenceUIWithOneParticipant( Context context) {
        return context.getResources().getBoolean(R.bool.update_incallui_to_usual_call);
    }
    /* @} */

    /* SPRD Feature Porting: FL0108160005 Hangup all calls for orange case. */
    public static boolean isSupportHangupAll(Context context) {
        return context.getResources().getBoolean(R.bool.config_is_support_hangup_all_feature);
    }
    /* @} */

    /*SPRD Featrure Porting: FL0108130015 not show hold buttong when videocall cmcc @{*/
    public static boolean showHoldOnButton(Context context, boolean isVideoCall, DialerCall call) {  //modeify for bug886964
        if (call != null && isVideoCall) {
            CarrierConfigManager cm = (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            PhoneAccountHandle accountHandle = call.getAccountHandle();
            int currentPhoneId = getPhoneIdByAccountHandle(context,accountHandle);
            // UNISOC: add for bug931964
            LogUtil.i("InCallUtils","currentPhoneId =" + currentPhoneId + ",accountHandle.ID =" + (accountHandle == null ? "null" : accountHandle.getId()));
            if(-1 == currentPhoneId) {
                LogUtil.i("InCallUtils","getCurrentPhoneId failed");
                return  false;
            }
            if(cm.getConfigForPhoneId(currentPhoneId) != null){
                LogUtil.i("InCallUtils","configValue =" + cm.getConfigForPhoneId(currentPhoneId).getBoolean(CarrierConfigManagerEx.KEY_CARRIER_SHOW_HOLD_BUTTON));
                return cm.getConfigForPhoneId(currentPhoneId).getBoolean(CarrierConfigManagerEx.KEY_CARRIER_SHOW_HOLD_BUTTON);
            }else{
                LogUtil.e("InCallUtils","showHoldOnButton getConfigForDefaultPhone = null");
            }
        }
        return true;
    }/*@}*/

    /* SPRD Feature Porting: Display caller address for phone number feature. @{ */
    public static void setCallerAddress(Context context, PrimaryInfo primaryInfo,
                                        TextView geocodeView) {
        if (geocodeView != null) {
            String newDescription = "";
            String oldDescription = geocodeView.getText().toString();
            if (primaryInfo.nameIsNumber()) {
                newDescription = CallerAddressHelper.getsInstance(context)
                        .getCallerAddress(context, primaryInfo.name());
            } else {
                newDescription = CallerAddressHelper.getsInstance(context)
                        .getCallerAddress(context, primaryInfo.number());
            }
            if (newDescription != null && !oldDescription.equals(newDescription)) {
                LogUtil.d("InCallFragment.setCallerAddress", "newDescription:" + newDescription);
                geocodeView.setText(newDescription);
            }
        }
    }
    /* @} */

    /*SPRD: add for feature support reject with message on incoming call notification {@*/
    public static boolean shouldAddRejectMessageButton(Context context) {
        return context.getResources().getBoolean(R.bool.incallui_notification_add_reject_message_button);
    }
    /* @} */

    /*SPRD Featrure Porting: add for feature FL1000060387 @{*/
    public static boolean isSupportRingTone(Context context) {

        //modify for bug1000884
        DialerCall call = CallList.getInstance().getFirstCall();

        if(call == null || context == null){
            LogUtil.i("InCallUtils", "isSupportRingTone call is null");
            return false;
        }

        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        PhoneAccountHandle accountHandle = call.getAccountHandle();
        int currentPhoneId = getPhoneIdByAccountHandle(context, accountHandle);

        if(-1 == currentPhoneId){
            LogUtil.i("InCallUtils", "isSupportRingTone getCurrentPhoneId failed");
            return false;
        }

        if(context.checkSelfPermission(READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                && configManager.getConfigForPhoneId(currentPhoneId) != null){
            LogUtil.i("InCallUtils", "isSupportRingTone phone id = " + currentPhoneId + " isSupportRingTone = "
                    + configManager.getConfigForPhoneId(currentPhoneId).getBoolean(CarrierConfigManagerEx.KEY_CARRIER_SUPPORTS_VIDEO_RING_TONE));
            return configManager.getConfigForPhoneId(currentPhoneId).getBoolean(
                    CarrierConfigManagerEx.KEY_CARRIER_SUPPORTS_VIDEO_RING_TONE);
        } else {
            LogUtil.i("InCallUtils", "isSupportRingTone getConfigForDefaultPhone = null");
        }

        return false;

    }/*@}*/

    /**
     * Add method to get phone id with current calls.
     */
    public static int getCurrentPhoneId(Context context) {
        DialerCall call = CallList.getInstance().getFirstCall();

        if (call != null && call.getAccountHandle() != null) {
            String iccId = call.getAccountHandle().getId();
            List<SubscriptionInfo> result = DialerUtils
                    .getActiveSubscriptionInfoList(context);

            if (result != null) {
                for (SubscriptionInfo subInfo : result) {
                    if (iccId != null && subInfo != null && iccId.equals(subInfo.getIccId())) {
                        return subInfo.getSimSlotIndex();
                    }
                }
            }
        }
        return -1;
    }

   /* SPRD Feature Porting: Vibrate when call connected or disconnected feature. @{ */
    /**
     * @return Whether support call connected or disconnected feature.
     */
    public static boolean isSupportVibrateForCallConnectionFeature(Context context) {
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        if (configManager.getConfigForDefaultPhone() != null) {
            return configManager.getConfigForDefaultPhone().getBoolean(
                    CarrierConfigManagerEx.KEY_FEATURE_VIBRATE_FOR_CALL_CONNECTION_BOOL);
        }
        return false;
    }

    /*UNISOC: Add for bug916945, get the call.subId from PhoneAccount @{ */
    public static int getPhoneSubIdForPhoneAccountHandle(Context context, PhoneAccountHandle handle) {
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        TelecomManager telecomManager = (TelecomManager) context
               .getSystemService(Context.TELECOM_SERVICE);
        TelephonyManager telephonyManager = (TelephonyManager) context
               .getSystemService(Context.TELEPHONY_SERVICE);
        try {
            PhoneAccount phoneAccount = telecomManager.getPhoneAccount(handle);
            subId = telephonyManager.getSubIdForPhoneAccount(phoneAccount);
        } catch (NullPointerException e)   {
            android.util.Log.d("exception","Exception raised during paring int.");
        }
        return subId;
    }
    /*@}*/

    /**
     * Add method for Vibration feedback
     */
    public static void vibrateForCallStateChange(Context context, DialerCall call, String preferenceName) {
        // UNISOC: modify for bug912781
        Boolean vibrate = false;
        try {
            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            vibrate = sp.getBoolean(preferenceName, false);
        } catch (Exception e) {
            LogUtil.e("InCallUiUtils.vibrateForCallStateChange", "Exception:" + e.getMessage());
        }
        if (call == null || !vibrate) {
            return;
        }
        boolean shouldVibrate = false;
        if (TextUtils.equals(VIBRATION_FEEDBACK_FOR_DISCONNECT_PREFERENCES_NAME, preferenceName)) {
            if (call.getState() == DialerCall.State.DISCONNECTED && !call.isConferenceCall()) {
                LogUtil.d("InCallUiUtils.vibrateForCallStateChange",
                        "vibrate for call state changed to disconnected. call: " + call);
                shouldVibrate = true;
            }
        }else{
            if (call.getState() == DialerCall.State.ACTIVE && !call.isConferenceCall()) {
                LogUtil.d("InCallUiUtils.vibrateForCallStateChange",
                        "vibrate for call state changed to active. call : " + call);
                shouldVibrate = true;
            }
        }
        if (shouldVibrate) {
            Vibrator vibrator = (Vibrator) context
                    .getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }
    /*@}*/

    /* SPRD Feature Porting: Fade in ringer volume when incoming calls. @{ */
    public static boolean isFadeInRingerEnabled(Context context) {
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        if (configManager.getConfigForDefaultPhone() != null) {
            return configManager.getConfigForDefaultPhone().getBoolean(
                    CarrierConfigManagerEx.KEY_FEATURE_FADE_IN_ENABLED_BOOL);
        }
        return false;
    }

    public static final int[] SENSORHUB_LIST = new int[]{
            SprdSensor.TYPE_SPRDHUB_HAND_UP,
            SprdSensor.TYPE_SPRDHUB_SHAKE,
            Sensor.TYPE_PICK_UP_GESTURE,
            SprdSensor.TYPE_SPRDHUB_FLIP,
            SprdSensor.TYPE_SPRDHUB_TAP,
            Sensor.TYPE_WAKE_GESTURE,
            SprdSensor.TYPE_SPRDHUB_POCKET_MODE
    };

    public static boolean isSupportSensorHub(Context context) {
        if (context == null) {
            return false;
        }
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Boolean isSupportSmartControl = false;
        if (sensorManager != null) {
            for (int i = 0; i < SENSORHUB_LIST.length; i++) {
                isSupportSmartControl |= sensorManager.getDefaultSensor(SENSORHUB_LIST[i]) != null;
            }
            return isSupportSmartControl;
        }
        return false;
    }
    /* @} */

    /**
     * Add method to get slot info by PhoneAccountHandle.
     */
    public static String getSlotInfoByPhoneAccountHandle(
            Context context, PhoneAccountHandle accountHandle) {
        if (accountHandle == null) {
            return "";
        }
        return InCallUiUtils.getSlotInfoBySubId(context,
                getSubIdForPhoneAccountHandle(context, accountHandle));
    }

    /**
     * Add method to get slot info by call.
     */
    public static String getSlotInfoByCall(Context context, DialerCall call) {
        if (call == null) {
            return "";
        }
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (call != null && call.getAccountHandle() != null) {
            subId = InCallUiUtils.getSubIdForPhoneAccountHandle(context, call.getAccountHandle());
        }
        return getSlotInfoBySubId(context, subId);
    }
    /**
     * Add method to get slot info by subId.
     */
    public static String getSlotInfoBySubId(Context context, int subId) {

        //add for bug913838
        int phoneId = SubscriptionManager.from(context).getPhoneId(subId);
        if(ImsManagerEx.isDualLteModem()){
            LogUtil.i("InCallUtils","getSlotInfoBySubId isDualLteModem phoneId:"+phoneId+"subId:"+subId);
            //UNISOC:add for bug919313,bug940953
            if (phoneId==0) {
                return context.getResources().getString(R.string.xliff_string1)+ " ";
            }
            else{
            return context.getResources().getString(R.string.xliff_string2)+ " ";
            }
        }
        //android P: Adjust judgment main card scheme
        //Boolean isPrimaryCard = TelephonyManagerEx.from(context).getLTECapabilityForSubId(subId);
        int defaultDataSubId = SubscriptionManager.from(context).getDefaultDataSubscriptionId();
        Boolean isPrimaryCard = (defaultDataSubId == subId);
        LogUtil.i("InCallUtils"," defaultDataSubId:"+defaultDataSubId+" subId:"+subId+" isPrimaryCard:"+isPrimaryCard);
        String card_slot;
        if (isPrimaryCard) {
            card_slot = context.getResources().getString(R.string.main_card_slot);
        } else {
            card_slot = context.getResources().getString(R.string.vice_card_slot);
        }
        return card_slot;
    }

    /**
     * Add method to get phone account label by call.
     */
    public static String getPhoneAccountLabel(DialerCall call, Context context) {
        String label = "";
        if (call == null) {
            return label;
        }
        PhoneAccountHandle accountHandle = call.getAccountHandle();
        if (accountHandle == null) {
            return null;
        }
        PhoneAccount account = context.getSystemService(TelecomManager.class)
                .getPhoneAccount(accountHandle);

        if (account != null && !TextUtils.isEmpty(account.getLabel())) {
            label = account.getLabel().toString();
        }
        return label;
    }
    /* SPRD Feature: Support wifiCall notification. @{ */
    public static boolean isWifiCallNotificationEnabled(Context context, DialerCall call) {
        if(null == call){
            LogUtil.i("InCallUtils","isWifiCallNotificationEnabled call is null");
            return false;
        }
        CarrierConfigManager carrierConfigManager = (CarrierConfigManager)context.getSystemService(Context.CARRIER_CONFIG_SERVICE);

        PhoneAccountHandle accountHandle = call.getAccountHandle();
        int currentPhoneId = getPhoneIdByAccountHandle(context,accountHandle);
        if(-1 == currentPhoneId) {
            LogUtil.i("InCallUtils","isWifiCallNotificationEnabled getCurrentPhoneId failed");
            return false;
        }

        if (context.checkSelfPermission(READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                && carrierConfigManager.getConfigForPhoneId(currentPhoneId) != null) {
            return carrierConfigManager.getConfigForPhoneId(currentPhoneId).getBoolean(
                    CarrierConfigManagerEx.KEY_WIFI_CALL_NOTIFICATION_ENABLE);
        }else{
            LogUtil.i("InCallUtils","isWifiCallNotificationEnabled getConfigForPhoneId = null");
        }
        return false;
    }
    /* @} */

    /*SPRD: add for bug895541 @{*/
    public static boolean isSupportUpAndDownGrade(Context context, DialerCall call) {
        if (context != null && call != null && call.isConferenceCall()) {
            CarrierConfigManager configManager = (CarrierConfigManager)context.getSystemService(Context.CARRIER_CONFIG_SERVICE);

            PhoneAccountHandle accountHandle = call.getAccountHandle();
            int currentPhoneId = getPhoneIdByAccountHandle(context, accountHandle);
            if (-1 == currentPhoneId) {
                LogUtil.i("InCallUtils", "isSupportUpAndDownGrade getCurrentPhoneId failed");
                return false;
            }

            if (context.checkSelfPermission(READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                    && configManager.getConfigForPhoneId(currentPhoneId) != null) {
                LogUtil.i("InCallUtils", "isSupportUpAndDownGrade:"+configManager.getConfigForPhoneId(currentPhoneId).getBoolean(CarrierConfigManagerEx.KEY_SUPPORT_UP_DOWN_GRADE_VT_CONFERENCE));
                return configManager.getConfigForPhoneId(currentPhoneId).getBoolean(
                        CarrierConfigManagerEx.KEY_SUPPORT_UP_DOWN_GRADE_VT_CONFERENCE);
            } else {
                LogUtil.i("InCallUtils", "isSupportUpAndDownGrade getConfigForDefaultPhone = null");
            }
        }
        return true;
    }/*@}*/

    /*SPRD: add for bug812381 & 812244 @{*/
    public static String getPhoneNumberWithoutCountryCode(String phoneNumber, Context context){
        if (phoneNumber == null) {
            return null;
        }
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        String countryIso = GeoUtil.getCurrentCountryIso(context);
        int countryCode = phoneUtil.getCountryCodeForRegion(countryIso);
        LogUtil.i("InCallUtils.getPhoneNumberWithoutCountryCode",
                "countryCode: " + countryCode);
        if (countryCode > 0) {
            String code = "";
            if (phoneNumber.startsWith(String.valueOf(PREFIX_PLUS))) {
                code = String.valueOf(PREFIX_PLUS) + countryCode;
            } else if(phoneNumber.startsWith(PREFIX_DOUBLE_ZERO)) {
                code = PREFIX_DOUBLE_ZERO + countryCode;
            }
            try {
                phoneNumber = phoneNumber.substring(code.length());
            } catch (StringIndexOutOfBoundsException exception) {
                LogUtil.e("InCallUtils.getPhoneNumberWithoutCountryCode", "Exception: " + exception);
            }
            LogUtil.d("InCallUtils.getPhoneNumberWithoutCountryCode", "code: " + code +
                    " Phone Number: " + phoneNumber);
        }
        return phoneNumber;
    }
    /*@}*/

    // add for bug930591
    public static String removeNonNumericForNumber(String number) {
        if (number == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int len = number.length();
        int i = 0;
        if (number.charAt(i) == PREFIX_PLUS) {
            sb.append(PREFIX_PLUS);
            i++;
        }
        for (; i < len; i++) {
            char c = number.charAt(i);
            if ((c >= '0' && c <= '9') || c == PhoneNumberUtilsEx.PAUSE || c == PhoneNumberUtilsEx.WAIT) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    ///UNISOC:add for bug940943
    /**
    * Get CallerInfo for Conference Child Call.
    * */
    public static ContactInfoCache.ContactCacheEntry getCallerInfo(Context context) {
        CallList callList = CallList.getInstance();
        if (context == null || callList == null || callList.getAllConferenceCall() == null
                || callList.getAllConferenceCall().getChildCallIds() == null) {
            return null;
        }
        String[] callerIds = null;
        callerIds = (String[]) callList.getAllConferenceCall().getChildCallIds().toArray(new String[0]);
        final ContactInfoCache.ContactCacheEntry contactCache = ContactInfoCache.getInstance(context).
                getInfo(callerIds[0]);
        if (contactCache != null) {
            return contactCache;
        }
        return null;
    }

    /**
     * Get the name to display for a call.
     */
    public static String getNameForCall(ContactInfoCache.ContactCacheEntry contactInfo,
                                        ContactsPreferences contactsPreferences) {
        String preferredName =
                ContactDisplayUtils.getPreferredDisplayName(
                        contactInfo.namePrimary, contactInfo.nameAlternative, contactsPreferences);
        if (TextUtils.isEmpty(preferredName)) {
            return contactInfo.number;
        }
        return preferredName;
    }

    /**
     * Add method to get sub id with current calls.
     */
    public static int getCurrentSubId(Context context) {  //add for bug990129
        DialerCall call = CallList.getInstance().getFirstCall();

        if (call != null && call.getAccountHandle() != null) {
        return getSubIdForPhoneAccountHandle(context, call.getAccountHandle());
        }
        LogUtil.i("InCallUtils.getCurrentSubId", "get subId is -1");
        return -1;
    }

    /**
     * Add method to get slot info by PhoneAccountHandle.
     */

    /* UNISOC:add for bug1088195 */
    public static boolean isShouldshowVowifiIcon(Context context, DialerCall call) {
        Boolean isSupportshowVowifiIcon = context.getResources().getBoolean(R.bool.config_is_support_vowifi_icon_feature);
        Boolean isinVowifiState = call != null ? call.hasProperty(Details.PROPERTY_WIFI) :false;
        LogUtil.i("InCallUtils.isShouldshowVowifiIcon", "isSupportshowVowifiIcon："+isSupportshowVowifiIcon);
        LogUtil.i("InCallUtils.isShouldshowVowifiIcon", "isinVowifiState："+isinVowifiState);
        return isSupportshowVowifiIcon && isinVowifiState;
    }
    /* @} */

     //UNISOC:add for bug1043239
    public static boolean HideHDVoiceIcon(Context context,DialerCall call) {
        if (call !=null) {
            CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            PhoneAccountHandle accountHandle = call.getAccountHandle();
            int currentPhoneId = getPhoneIdByAccountHandle(context,accountHandle);
            LogUtil.i("InCallUtils.HideHDVoiceIcon","currentPhoneId =" + currentPhoneId + ",accountHandle.ID =" + (accountHandle == null ? "null" : accountHandle.getId()));

            if(currentPhoneId != -1 && configManager.getConfigForPhoneId(currentPhoneId) != null){
                LogUtil.i("InCallUtils.HideHDVoiceIcon","configValue =" + configManager.getConfigForPhoneId(currentPhoneId).getBoolean(CarrierConfigManagerEx.KEY_HD_VOICE_ICON_SHOULD_BE_REMOVED));
                return configManager.getConfigForPhoneId(currentPhoneId).getBoolean(CarrierConfigManagerEx.KEY_HD_VOICE_ICON_SHOULD_BE_REMOVED);
            }else {
                LogUtil.i("InCallUtils.HideHDVoiceIcon","getConfigForDefaultPhone = null");
                return  false;
            }
        }
        return false;
    }
}

