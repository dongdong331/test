package com.android.phone;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.ims.ImsException;
import com.android.ims.ImsManager;

import android.telephony.ims.ImsReasonInfo;
import com.android.ims.internal.ImsCallForwardInfoEx;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.GsmCdmaPhoneEx;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.uicc.IccRecords;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManagerEx;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.SpannableString;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputFilter;

import static com.android.phone.TimeConsumingPreferenceActivity.RESPONSE_ERROR;
import static com.android.phone.TimeConsumingPreferenceActivity.EXCEPTION_ERROR;
import static com.android.phone.TimeConsumingPreferenceActivity.RADIO_OFF_ERROR;
import static com.android.phone.TimeConsumingPreferenceActivity.FDN_CHECK_FAILURE;
import com.unisoc.phone.callsettings.callforward.CallForwardTimeUtils;

public class CallForwardEditPreference extends EditPhoneNumberPreference {
    private static final String LOG_TAG = "CallForwardEditPreference";

    private static final String SRC_TAGS[]       = {"{0}"};
    private CharSequence mSummaryOnTemplate;
    /**
     * Remembers which button was clicked by a user. If no button is clicked yet, this should have
     * {@link DialogInterface#BUTTON_NEGATIVE}, meaning "cancel".
     *
     * TODO: consider removing this variable and having getButtonClicked() in
     * EditPhoneNumberPreference instead.
     */
    private int mButtonClicked;
    private int mServiceClass;
    private MyHandler mHandler = new MyHandler();
    int reason;
    private Phone mPhone;
    CallForwardInfo callForwardInfo;
    private TimeConsumingPreferenceListener mTcpListener;
    // Should we replace CF queries containing an invalid number with "Voicemail"
    private boolean mReplaceInvalidCFNumber = false;
    /* UNISOC: add for FEATURE_VIDEO_CALL_FOR @{ */
    public int mReason;
    private static final boolean DBG = true;
    private GsmCdmaPhoneEx mGsmCdmaPhoneEx;
    public ImsCallForwardInfoEx mImsCallForwardInfoEx;
    private static final String REFRESH_VIDEO_CF_NOTIFICATION_ACTION =
            "android.intent.action.REFRESH_VIDEO_CF_NOTIFICATION";
    private static final String VIDEO_CF_SUB_ID = "video_cf_flag_with_subid";
    private static final String VIDEO_CF_STATUS = "video_cf_status";
    private int mStatus;
    private Context mContext;
    private boolean mIsInitializing = false;
    /* @} */
    /* UNISOC: function caching edited CF number support. @{ */
    private SharedPreferences mPrefs;
    private int mPhoneId = 0;
    private int mSubId = 0;
    static final String PREF_PREFIX = "phonecallforward_";
    private EditPhoneNumberPreference.GetDefaultNumberListener mCallForwardListener;
    /* @} */
    /* UNISOC: add for feature 888845 @{ */
    private boolean mUseConfigServiceClass = false;
    private int mConfigServiceClass;
    private static final int DEF_STATUS = 0;
    private static final int TOOGLE_STATUS = 1;
    /* @} */
    /* UNISOC: add for feature 884921 durationforward @{ */
    private SharedPreferences mTimePrefs;
    private static final String PREF_PREFIX_TIME = "phonecalltimeforward_";
    /* @} */
    /* UNISOC: add for bug905164 @{ */
    private Button mUpdateButton;
    private Button mConfirmButton;
    /* @} */
    public CallForwardEditPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mSummaryOnTemplate = this.getSummaryOn();

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.CallForwardEditPreference, 0, R.style.EditPhoneNumberPreference);
        mServiceClass = a.getInt(R.styleable.CallForwardEditPreference_serviceClass,
                CommandsInterface.SERVICE_CLASS_VOICE);
        reason = a.getInt(R.styleable.CallForwardEditPreference_reason,
                CommandsInterface.CF_REASON_UNCONDITIONAL);
        /* UNISOC: add for FEATURE_VIDEO_CALL_FOR @{ */
        mReason = reason;
        mContext = context;
        /* @} */
        a.recycle();

        Log.d(LOG_TAG, "mServiceClass=" + mServiceClass + ", reason=" + reason);
    }

    /*  UNISOC: add for FEATURE_VIDEO_CALL_FOR  @{ */
    public CallForwardEditPreference(Context context) {
        this(context, null);
    }
    /* @} */

    public void init(TimeConsumingPreferenceListener listener, boolean skipReading, Phone phone) {
        init(listener, skipReading, phone, false);
    }

    void init(TimeConsumingPreferenceListener listener, boolean skipReading, Phone phone,
            boolean replaceInvalidCFNumber) {
        mPhone = phone;
        // UNISOC: add for FEATURE_VIDEO_CALL_FOR
        mGsmCdmaPhoneEx = (GsmCdmaPhoneEx) phone;
        mTcpListener = listener;
        mReplaceInvalidCFNumber = replaceInvalidCFNumber;
        /* UNISOC: function caching edited CF number support. @{ */
        mPhoneId = mPhone.getPhoneId();
        mSubId = mPhone.getSubId();
        // UNISOC: add for feature 884921 durationforward
        mTimePrefs = mContext.getSharedPreferences(PREF_PREFIX_TIME + mSubId, mContext.MODE_PRIVATE);
        mPrefs = mPhone.getContext().getSharedPreferences(PREF_PREFIX + mSubId, Context.MODE_PRIVATE);
        mCallForwardListener = new EditPhoneNumberPreference.GetDefaultNumberListener() {
            public String onGetDefaultNumber(EditPhoneNumberPreference preference) {
                String number;
                if ((mServiceClass & CommandsInterface.SERVICE_CLASS_VOICE) == 0) {
                    number = mPrefs.getString(PREF_PREFIX + "Video_" + mSubId + "_" + reason, "");
                } else {
                    number = mPrefs.getString(PREF_PREFIX + mSubId + "_" + reason, "");
                }
                return number;
            }
        };
        /* @} */
        /* UNISOC: add for feature 888845 @{ */
        mConfigServiceClass = getCarrierIntValueByKey(
                CarrierConfigManagerEx.KEY_CONFIG_IMS_CALLFORWARD_SERVICECLASS);
        mUseConfigServiceClass = (mServiceClass == 1) && (mConfigServiceClass != -1);
        mConfigServiceClass = mUseConfigServiceClass ? mConfigServiceClass : mServiceClass;
        Log.d(LOG_TAG, "mConfigServiceClass " + mConfigServiceClass);
        /* @} */
        if (!skipReading) {
            mIsInitializing = true;
            /*  UNISOC: add for FEATURE_VIDEO_CALL_FOR & feature 888845 @{ */
            if (mConfigServiceClass != CommandsInterface.SERVICE_CLASS_VOICE) {
                mGsmCdmaPhoneEx.getCallForwardingOption(
                        reason,
                        mConfigServiceClass,
                        null,
                        mHandler.obtainMessage(MyHandler.MESSAGE_GET_CFV,
                                // unused in this case
                                CommandsInterface.CF_ACTION_DISABLE,
                                MyHandler.MESSAGE_GET_CFV, null));
            } else {
                mPhone.getCallForwardingOption(reason,
                        mHandler.obtainMessage(MyHandler.MESSAGE_GET_CF,
                                // unused in this case
                                CommandsInterface.CF_ACTION_DISABLE,
                                MyHandler.MESSAGE_GET_CF, null));
            }
            /* @} */
            if (mTcpListener != null) {
                mTcpListener.onStarted(this, true);
            }
        }
    }

    @Override
    protected void onBindDialogView(View view) {
        // default the button clicked to be the cancel button.
        mButtonClicked = DialogInterface.BUTTON_NEGATIVE;
        super.onBindDialogView(view);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        super.onClick(dialog, which);
        mButtonClicked = which;
        //  UNISOC: add for FEATURE_VIDEO_CALL_FOR
        mIsInitializing = false;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        Log.d(LOG_TAG, "mButtonClicked=" + mButtonClicked + ", positiveResult=" + positiveResult);
        // Ignore this event if the user clicked the cancel button, or if the dialog is dismissed
        // without any button being pressed (back button press or click event outside the dialog).
        if (this.mButtonClicked != DialogInterface.BUTTON_NEGATIVE) {
            int action = (isToggled() || (mButtonClicked == DialogInterface.BUTTON_POSITIVE)) ?
                    CommandsInterface.CF_ACTION_REGISTRATION :
                    CommandsInterface.CF_ACTION_DISABLE;
            int time = (reason != CommandsInterface.CF_REASON_NO_REPLY) ? 0 : 20;
            String number = getPhoneNumber();

            Log.d(LOG_TAG, "callForwardInfo=" + callForwardInfo);

            if (action == CommandsInterface.CF_ACTION_REGISTRATION
                    && callForwardInfo != null
                    && callForwardInfo.status == 1
                    && number.equals(callForwardInfo.number)) {
                // no change, do nothing
                Log.d(LOG_TAG, "no change, do nothing");
            } else {
                // set to network
                Log.d(LOG_TAG, "reason=" + reason + ", action=" + action
                        + ", number=" + number);

                /* UNISOC: add for bug917389 & 925949 @{ */
                if (action == CommandsInterface.CF_ACTION_DISABLE) {
                    String cfnumber;
                    if (checkVideoCallServiceClass(mServiceClass)) {
                        cfnumber = mPrefs.getString(PREF_PREFIX + "Video_"
                                + mSubId + "_" + reason, "");
                    } else {
                        cfnumber = mPrefs.getString(PREF_PREFIX + mSubId + "_" + reason, "");
                    }
                    if (!number.equals(cfnumber)) {
                        Log.d(LOG_TAG, "disable cf but enter a different number, update the " +
                                "number to the default number" + cfnumber);
                        number = cfnumber;
                    }
                }
                /* @} */
                // Display no forwarding number while we're waiting for
                // confirmation
                setSummaryOn("");
                if (mTcpListener != null) {
                    mTcpListener.setSuppServiceFlag(GsmCdmaPhoneEx.SS_FLAG_DES_START);
                }
                /*  UNISOC: add for FEATURE_VIDEO_CALL_FOR & feature 888845, 948130 @{ */
                if (mConfigServiceClass != CommandsInterface.SERVICE_CLASS_VOICE && mGsmCdmaPhoneEx != null) {
                    mGsmCdmaPhoneEx.setCallForwardingOption(action,
                            reason,
                            mConfigServiceClass,
                            number,
                            time,
                            null,
                            mHandler.obtainMessage(MyHandler.MESSAGE_SET_CF,
                            action,
                            MyHandler.MESSAGE_SET_CF));
                } else if (mPhone != null) {
                    // the interface of Phone.setCallForwardingOption has error:
                    // should be action, reason...
                    mPhone.setCallForwardingOption(action,
                            reason,
                            number,
                            time,
                            mHandler.obtainMessage(MyHandler.MESSAGE_SET_CF,
                                    action,
                                    MyHandler.MESSAGE_SET_CF));
                }
                /* @} */

                if (mTcpListener != null) {
                    mTcpListener.onStarted(this, false);
                }
            }
        }
    }

    void handleCallForwardResult(CallForwardInfo cf) {
        callForwardInfo = cf;
        Log.d(LOG_TAG, "handleGetCFResponse done, callForwardInfo=" + callForwardInfo);
        // In some cases, the network can send call forwarding URIs for voicemail that violate the
        // 3gpp spec. This can cause us to receive "numbers" that are sequences of letters. In this
        // case, we must detect these series of characters and replace them with "Voicemail".
        // PhoneNumberUtils#formatNumber returns null if the number is not valid.
        if (mReplaceInvalidCFNumber && (PhoneNumberUtils.formatNumber(callForwardInfo.number,
                getCurrentCountryIso()) == null)) {
            callForwardInfo.number = getContext().getString(R.string.voicemail);
            Log.i(LOG_TAG, "handleGetCFResponse: Overridding CF number");
        }

        setToggled(callForwardInfo.status == 1);
        boolean displayVoicemailNumber = false;
        if (TextUtils.isEmpty(callForwardInfo.number)) {
            PersistableBundle carrierConfig =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            if (carrierConfig != null) {
                displayVoicemailNumber = carrierConfig.getBoolean(CarrierConfigManager
                        .KEY_DISPLAY_VOICEMAIL_NUMBER_AS_DEFAULT_CALL_FORWARDING_NUMBER_BOOL);
                Log.d(LOG_TAG, "display voicemail number as default " + displayVoicemailNumber);
            }
        }
        String voicemailNumber = mPhone.getVoiceMailNumber();
        setPhoneNumber(displayVoicemailNumber ? voicemailNumber : callForwardInfo.number);
        //  UNISOC: add for FEATURE_VIDEO_CALL_FOR
        mTcpListener.onEnableStatus(CallForwardEditPreference.this, 0);
        /* UNISOC: function caching edited CF number support. @{ */
        if (!TextUtils.isEmpty(getPhoneNumber())) {
            saveStringPrefs(PREF_PREFIX + mSubId + "_" + reason, getPhoneNumber(), mPrefs);
        }
        /* @} */
    }

    /* UNISOC: function caching edited CF number support @{ */
    void saveStringPrefs(String key, String value, SharedPreferences prefs) {
        Log.w(LOG_TAG, "saveStringPrefs(" + key + ", " + value + ")");
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(key, value);
            editor.apply();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception happen.");
        }
    }
    /* @} */

    /*  UNISOC: add for FEATURE_VIDEO_CALL_FOR  @{ */
    public int getServiceClass() {
        return mServiceClass;
    }

    public int getReason() {
        return reason;
    }

    public int getStatus() {
        return mStatus;
    }

    public boolean isInitializing() {
        return mIsInitializing;
    }

    public void handleCallForwardVResult(ImsCallForwardInfoEx cf) {
        mImsCallForwardInfoEx = cf;
        mStatus = mImsCallForwardInfoEx.mStatus;
        /* UNISOC: add for feature 913471 @{ */
        Log.d(LOG_TAG, "handleCallForwardVResult done, callForwardInfo=" + cf
                + "; mStatus=" + mStatus + "; mUseConfigServiceClass:" + mUseConfigServiceClass);
        /* @} */
        /* UNISOC: add for feature 888845 @{ */
        if (mUseConfigServiceClass) {
            if (DBG) {
                Log.d(LOG_TAG, "special operator handleCallForwardVResult done");
            }
            if (mReplaceInvalidCFNumber && (PhoneNumberUtils.formatNumber(mImsCallForwardInfoEx.mNumber,
                    getCurrentCountryIso()) == null)) {
                mImsCallForwardInfoEx.mNumber = getContext().getString(R.string.voicemail);
                Log.i(LOG_TAG, "handleGetCFResponse: Overridding CF number");
            }
            setToggled(mImsCallForwardInfoEx.mStatus == TOOGLE_STATUS);
            boolean displayVoicemailNumber = false;
            if (TextUtils.isEmpty(mImsCallForwardInfoEx.mNumber)) {
                PersistableBundle carrierConfig =
                        PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
                if (carrierConfig != null) {
                    displayVoicemailNumber = carrierConfig.getBoolean(CarrierConfigManager
                            .KEY_DISPLAY_VOICEMAIL_NUMBER_AS_DEFAULT_CALL_FORWARDING_NUMBER_BOOL);
                    Log.d(LOG_TAG, "display voicemail number as default");
                }
            }
            String voicemailNumber = mPhone.getVoiceMailNumber();
            setPhoneNumber(displayVoicemailNumber ? voicemailNumber : mImsCallForwardInfoEx.mNumber);
            mTcpListener.onEnableStatus(CallForwardEditPreference.this, DEF_STATUS);

            if (!TextUtils.isEmpty(getPhoneNumber())) {
                saveStringPrefs(PREF_PREFIX + mSubId + "_" + reason, getPhoneNumber(), mPrefs);
            }
            return;
        }
        /* @} */
        /* UNISOC: modify for feature 884921 durationforward @{ */
        String iccId = getIccId(mSubId);
        mStatus = mImsCallForwardInfoEx.mStatus;
        if (TextUtils.isEmpty(mImsCallForwardInfoEx.mRuleset)) {
            if ((mImsCallForwardInfoEx.mServiceClass
                    & CommandsInterface.SERVICE_CLASS_VOICE) == 0) {
                setToggled(mImsCallForwardInfoEx.mStatus == 1);
                setPhoneNumber(mImsCallForwardInfoEx.mNumber);
                mTcpListener.onUpdateTwinsPref((mImsCallForwardInfoEx.mStatus == 1),
                        mImsCallForwardInfoEx.mServiceClass,
                        reason, mImsCallForwardInfoEx.mNumber, null);
            }
        } else {
            if ((mImsCallForwardInfoEx.mServiceClass
                    & CommandsInterface.SERVICE_CLASS_VOICE) != 0) {
                String number = mImsCallForwardInfoEx.mNumber;
                String numberToSave = null;
                if (number != null && PhoneNumberUtils.isUriNumber(number)) {
                    numberToSave = number;
                    saveStringPrefs(PREF_PREFIX_TIME + "num_" + mSubId, numberToSave, mTimePrefs);
                } else {
                    numberToSave = PhoneNumberUtils.stripSeparators(number);
                    saveStringPrefs(PREF_PREFIX_TIME + "num_" + mSubId, numberToSave, mTimePrefs);
                }
                saveStringPrefs(PREF_PREFIX_TIME + "ruleset_" + mSubId,
                        mImsCallForwardInfoEx.mRuleset, mTimePrefs);
                saveStringPrefs(PREF_PREFIX_TIME + "status_" + mSubId,
                        String.valueOf(mImsCallForwardInfoEx.mStatus), mTimePrefs);
                CallForwardTimeUtils.getInstance(mContext).writeStatus(
                        iccId, mImsCallForwardInfoEx.mStatus);
                /* @} */
                setToggled(true);
                setPhoneNumber(mImsCallForwardInfoEx.mNumber);
                mTcpListener.onEnableStatus(CallForwardEditPreference.this, 0);
            }
        }
        /* @} */

        /* UNISOC: function caching edited CF number support @{ */
        if (!TextUtils.isEmpty(mImsCallForwardInfoEx.mNumber)) {
            if ((mImsCallForwardInfoEx.mServiceClass
                    & CommandsInterface.SERVICE_CLASS_VOICE) == 0) {
                if (PhoneNumberUtils.isUriNumber(mImsCallForwardInfoEx.mNumber)) {
                    saveStringPrefs(PREF_PREFIX + "Video_" + mSubId + "_" + reason,
                           mImsCallForwardInfoEx.mNumber, mPrefs);
                } else {
                    saveStringPrefs(PREF_PREFIX + "Video_" + mSubId + "_" + reason,
                            PhoneNumberUtils.stripSeparators(mImsCallForwardInfoEx.mNumber),
                            mPrefs);
                }
            }
        }
        /* @} */

        if (mImsCallForwardInfoEx.mCondition == CommandsInterface.CF_REASON_UNCONDITIONAL
                &&  (mImsCallForwardInfoEx.mServiceClass
                        & CommandsInterface.SERVICE_CLASS_VOICE) == 0) {
            boolean videoCfStatus = (mImsCallForwardInfoEx.mStatus == 1);
            Intent intent = new Intent(REFRESH_VIDEO_CF_NOTIFICATION_ACTION);
            intent.putExtra(VIDEO_CF_STATUS, videoCfStatus);
            intent.putExtra(VIDEO_CF_SUB_ID, mPhone.getSubId());
            mContext.sendBroadcast(intent);
            if (DBG) {
                Log.d(LOG_TAG, "refresh notification for video cf subid : "
                        + mPhone.getSubId() + "; enable : " + videoCfStatus);
            }
        }
    }

    /* UNISOC: add for feature 913471 @{ */
    public boolean checkServiceClassSupport(int sc) {
        return (sc & CommandsInterface.SERVICE_CLASS_DATA) != 0
                || (sc & CommandsInterface.SERVICE_CLASS_DATA_SYNC) != 0
                || (sc & CommandsInterface.SERVICE_CLASS_DATA_ASYNC) != 0
                || (sc & CommandsInterface.SERVICE_CLASS_PACKET) != 0
                || (sc & CommandsInterface.SERVICE_CLASS_PAD) != 0
                || (sc & CommandsInterface.SERVICE_CLASS_VOICE) != 0;
    }
    /* @} */


    /* UNISOC: add for feature 925949 @{ */
    public boolean checkVideoCallServiceClass(int sc) {
        return (sc & CommandsInterface.SERVICE_CLASS_DATA) != 0
                || (sc & CommandsInterface.SERVICE_CLASS_DATA_SYNC) != 0
                || (sc & CommandsInterface.SERVICE_CLASS_DATA_ASYNC) != 0
                || (sc & CommandsInterface.SERVICE_CLASS_PACKET) != 0
                || (sc & CommandsInterface.SERVICE_CLASS_PAD) != 0;
    }
    /* @} */

    public void updateSummaryText() {
    /* @} */
        if (isToggled()) {
            final String number = getRawPhoneNumber();
            if (number != null && number.length() > 0) {
                // Wrap the number to preserve presentation in RTL languages.
                String wrappedNumber = BidiFormatter.getInstance().unicodeWrap(
                        number, TextDirectionHeuristics.LTR);
                String values[] = { wrappedNumber };
                String summaryOn = String.valueOf(
                        TextUtils.replace(mSummaryOnTemplate, SRC_TAGS, values));
                int start = summaryOn.indexOf(wrappedNumber);

                SpannableString spannableSummaryOn = new SpannableString(summaryOn);
                PhoneNumberUtils.addTtsSpan(spannableSummaryOn,
                        start, start + wrappedNumber.length());
                setSummaryOn(spannableSummaryOn);
            } else {
                setSummaryOn(getContext().getString(R.string.sum_cfu_enabled_no_number));
            }
        }

    }

    /**
     * @return The ISO 3166-1 two letters country code of the country the user is in based on the
     *      network location.
     */
    private String getCurrentCountryIso() {
        final TelephonyManager telephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            return "";
        }
        return telephonyManager.getNetworkCountryIso().toUpperCase();
    }

    // Message protocol:
    // what: get vs. set
    // arg1: action -- register vs. disable
    // arg2: get vs. set for the preceding request
    /* UNISOC: modify for FEATURE_VIDEO_CALL_FOR @{ */
    private class MyHandler extends Handler {
        static final int MESSAGE_GET_CF = 0;
        static final int MESSAGE_SET_CF = 1;
        static final int MESSAGE_GET_CFV = 2;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_CF:
                    handleGetCFResponse(msg);
                    break;
                case MESSAGE_SET_CF:
                    handleSetCFResponse(msg);
                    break;
                case MESSAGE_GET_CFV:
                    handleGetCFVResponse(msg);
                    break;
                default:
                    break;
            }
        }

        private void handleGetCFResponse(Message msg) {
            Log.d(LOG_TAG, "handleGetCFResponse: done");

            mTcpListener.onFinished(CallForwardEditPreference.this, msg.arg2 != MESSAGE_SET_CF);

            AsyncResult ar = (AsyncResult) msg.obj;

            callForwardInfo = null;
            if (ar.exception != null) {
                Log.d(LOG_TAG, "handleGetCFResponse: ar.exception=" + ar.exception);
                if (ar.exception instanceof CommandException) {

                    CommandException e = (CommandException) ar.exception;
                    if (e.getMessage() != null && e.getMessage().contains("code")) {
                        int length = e.getMessage().length();
                        try {
                            /* SPRD: add for bug854975 @{ */
                            String regEx = "[^0-9]";
                            Pattern pattern = Pattern.compile(regEx);
                            Log.d(LOG_TAG, "e.getMessage = " + e.getMessage());
                            Matcher matcher = pattern.matcher(e.getMessage());
                            int errorCode = Integer.parseInt(matcher.replaceAll("").trim());
                            /* @} */
                            if (DBG) {
                                Log.d(LOG_TAG, "handleGetCFResponse: errorCode = " + errorCode);
                            }
                            if (errorCode == ImsReasonInfo.CODE_FDN_BLOCKED) {
                                mIsInitializing = false;
                                mTcpListener.onError(CallForwardEditPreference.this,
                                        FDN_CHECK_FAILURE);
                            } else if (errorCode == ImsReasonInfo.CODE_RADIO_OFF
                                    // UNISOC: modify for bug881031
                                    || errorCode == ImsReasonInfo.CODE_UT_SERVICE_UNAVAILABLE) {
                                mIsInitializing = false;
                                mTcpListener.onError(CallForwardEditPreference.this,
                                        RADIO_OFF_ERROR);
                            } else {
                                // UNISOC: modify for Bug# 1057641, if Chinese language,do not run here!
                                mIsInitializing = false;
                                mTcpListener.onError(CallForwardEditPreference.this,
                                        EXCEPTION_ERROR);
                            }
                        } catch (Exception error) {
                            error.printStackTrace();
                        }
                        return;
                    }
                    if (e.getCommandError() == CommandException.Error.FDN_CHECK_FAILURE
                            || e.getCommandError() == CommandException.Error.RADIO_NOT_AVAILABLE
                            // UNISOC: modify for Bug# 1057641, for Chinese language
                            || e.getCommandError() == CommandException.Error.GENERIC_FAILURE) {
                        mIsInitializing = false;
                    }
                    mTcpListener.onException(CallForwardEditPreference.this,
                            (CommandException) ar.exception);
                } else {
                    // Most likely an ImsException and we can't handle it the same way as
                    // a CommandException. The best we can do is to handle the exception
                    // the same way as mTcpListener.onException() does when it is not of type
                    // FDN_CHECK_FAILURE.
                    mTcpListener.onError(CallForwardEditPreference.this, EXCEPTION_ERROR);
                }
            } else {
                if (ar.userObj instanceof Throwable) {
                    mTcpListener.onError(CallForwardEditPreference.this, RESPONSE_ERROR);
                }
                CallForwardInfo cfInfoArray[] = (CallForwardInfo[]) ar.result;
                if (cfInfoArray.length == 0) {
                    Log.d(LOG_TAG, "handleGetCFResponse: cfInfoArray.length==0");
                    setEnabled(false);
                    mTcpListener.onError(CallForwardEditPreference.this, RESPONSE_ERROR);
                } else {
                    for (int i = 0, length = cfInfoArray.length; i < length; i++) {
                        Log.d(LOG_TAG, "handleGetCFResponse, cfInfoArray[" + i + "]="
                                + cfInfoArray[i]);
                        if ((mServiceClass & cfInfoArray[i].serviceClass) != 0) {
                            // corresponding class
                            CallForwardInfo info = cfInfoArray[i];
                            handleCallForwardResult(info);

                            // Show an alert if we got a success response but
                            // with unexpected values.
                            // Currently only handle the fail-to-disable case
                            // since we haven't observed fail-to-enable.
                            if (msg.arg2 == MESSAGE_SET_CF &&
                                    msg.arg1 == CommandsInterface.CF_ACTION_DISABLE &&
                                    info.status == 1) {
                                // Skip showing error dialog since some operators return
                                // active status even if disable call forward succeeded.
                                // And they don't like the error dialog.
                                if (isSkipCFFailToDisableDialog()) {
                                    Log.d(LOG_TAG, "Skipped Callforwarding fail-to-disable dialog");
                                    continue;
                                }
                                CharSequence s;
                                switch (reason) {
                                    case CommandsInterface.CF_REASON_BUSY:
                                        s = getContext().getText(R.string.disable_cfb_forbidden);
                                        break;
                                    case CommandsInterface.CF_REASON_NO_REPLY:
                                        s = getContext().getText(R.string.disable_cfnry_forbidden);
                                        break;
                                    default: // not reachable
                                        s = getContext().getText(R.string.disable_cfnrc_forbidden);
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                builder.setNeutralButton(R.string.close_dialog, null);
                                builder.setTitle(getContext().getText(R.string.error_updating_title));
                                builder.setMessage(s);
                                builder.setCancelable(true);
                                // SPRD: add for bug929600
                                Context context = getContext();
                                if (context != null
                                        && (context instanceof Activity)
                                        && (!((Activity) context).isFinishing())
                                        && (!((Activity) context).isDestroyed())) {
                                    builder.create().show();
                                }
                            }
                        }
                    }
                }
            }

            // Now whether or not we got a new number, reset our enabled
            // summary text since it may have been replaced by an empty
            // placeholder.
            updateSummaryText();
        }

        private void handleGetCFVResponse(Message msg) {
            if (DBG) {
                Log.d(LOG_TAG, "handleGetCFVResponse: done");
            }

            mTcpListener.onFinished(CallForwardEditPreference.this, msg.arg2 != MESSAGE_SET_CF);

            AsyncResult ar = (AsyncResult) msg.obj;

            mImsCallForwardInfoEx = null;
            if (ar.exception != null) {
                if (DBG) {
                    Log.d(LOG_TAG, "handleGetCFVResponse: ar.exception=" + ar.exception);
                }
                setToggled(false);
                if (ar.exception instanceof CommandException) {
                    mTcpListener.onException(CallForwardEditPreference.this,
                            (CommandException) ar.exception);
                } else if (ar.exception instanceof ImsException) {
                    ImsException e = (ImsException) ar.exception;
                    if (e.getCode() == ImsReasonInfo.CODE_UT_SERVICE_UNAVAILABLE) {
                        mIsInitializing = false;
                        mTcpListener.onError(CallForwardEditPreference.this, RADIO_OFF_ERROR);
                    } else if (e.getCode() == ImsReasonInfo.CODE_FDN_BLOCKED) {
                        mIsInitializing = false;
                        mTcpListener.onError(CallForwardEditPreference.this, FDN_CHECK_FAILURE);
                    } else {
                        mTcpListener.onError(CallForwardEditPreference.this, EXCEPTION_ERROR);
                    }
                } else {
                    // Most likely an ImsException and we can't handle it the same way as
                    // a CommandException. The best we can do is to handle the exception
                    // the same way as mTcpListener.onException() does when it is not of type
                    // FDN_CHECK_FAILURE.
                    mTcpListener.onError(CallForwardEditPreference.this, EXCEPTION_ERROR);
                }
            } else {
                if (ar.userObj instanceof Throwable) {
                    /* SPRD: modify by BUG 851633 @{ */
                    if (DBG) {
                        Log.d(LOG_TAG, "handleGetCFVResponse: RESPONSE_ERROR");
                    }
                    /* @} */
                    mTcpListener.onError(CallForwardEditPreference.this, RESPONSE_ERROR);
                }
                ImsCallForwardInfoEx cfInfoArray[] = (ImsCallForwardInfoEx[]) ar.result;
                if (cfInfoArray.length == 0) {
                    if (DBG) {
                        Log.d(LOG_TAG, "handleGetCFVResponse: cfInfoArray.length==0");
                    }
                    setEnabled(false);
                    mTcpListener.onError(CallForwardEditPreference.this, RESPONSE_ERROR);
                } else {
                    for (int i = 0, length = cfInfoArray.length; i < length; i++) {
                        if (DBG) {
                            Log.d(LOG_TAG, "handleGetCFVResponse, cfInfoArray[" + i + "]="
                                    + cfInfoArray[i] + "; checkServiceClassSupport : "
                                    + checkServiceClassSupport(cfInfoArray[i].mServiceClass));
                        }
                        // UNISOC : Modify for bug 913471
                        if (checkServiceClassSupport(cfInfoArray[i].mServiceClass)) {
                            // corresponding class
                            ImsCallForwardInfoEx info = cfInfoArray[i];
                            handleCallForwardVResult(info);

                            // Show an alert if we got a success response but
                            // with unexpected values.
                            // Currently only handle the fail-to-disable case
                            // since we haven't observed fail-to-enable.
                            if (msg.arg2 == MESSAGE_SET_CF
                                    && msg.arg1 == CommandsInterface.CF_ACTION_DISABLE
                                    && info.mStatus == 1) {
                                CharSequence s;
                                switch (reason) {
                                    case CommandsInterface.CF_REASON_BUSY:
                                        s = getContext().getText(R.string.disable_cfb_forbidden);
                                        break;
                                    case CommandsInterface.CF_REASON_NO_REPLY:
                                        s = getContext().getText(R.string.disable_cfnry_forbidden);
                                        break;
                                    default:
                                        s = getContext().getText(R.string.disable_cfnrc_forbidden);
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                builder.setNeutralButton(R.string.close_dialog, null);
                                builder.setTitle(getContext().getText(
                                        R.string.error_updating_title));
                                builder.setMessage(s);
                                builder.setCancelable(true);
                                builder.create().show();
                            }
                        }
                    }
                }
            }

            // Now whether or not we got a new number, reset our enabled
            // summary text since it may have been replaced by an empty
            // placeholder.
            updateSummaryText();
        }

        private void handleSetCFResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (ar.exception != null) {
                Log.d(LOG_TAG, "handleSetCFResponse: ar.exception=" + ar.exception);
                // setEnabled(false);
            }
            Log.d(LOG_TAG, "handleSetCFResponse: re get, mConfigServiceClass : " + mConfigServiceClass);
            if (mTcpListener != null) {
                mTcpListener.setSuppServiceFlag(GsmCdmaPhoneEx.SS_FLAG_DES_END);
            }
            // UNISOC: add for feature 888845
            if (mConfigServiceClass != CommandsInterface.SERVICE_CLASS_VOICE) {
                mGsmCdmaPhoneEx.getCallForwardingOption(reason, mConfigServiceClass, null,
                        obtainMessage(MESSAGE_GET_CFV, msg.arg1, MESSAGE_SET_CF, ar.exception));
            } else {
                mPhone.getCallForwardingOption(reason,
                        obtainMessage(MESSAGE_GET_CF, msg.arg1, MESSAGE_SET_CF, ar.exception));
            }
        }
    }
    /* @} */

    /*
     * Get the config of whether skip showing CF fail-to-disable dialog
     * from carrier config manager.
     *
     * @return boolean value of the config
     */
    private boolean isSkipCFFailToDisableDialog() {
        PersistableBundle carrierConfig =
                PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
        if (carrierConfig != null) {
            return carrierConfig.getBoolean(
                    CarrierConfigManager.KEY_SKIP_CF_FAIL_TO_DISABLE_DIALOG_BOOL);
        } else {
            // by default we should not skip
            return false;
        }
    }

    /* UNISOC: add for feature 888845 @{ */
    public int getCarrierIntValueByKey(String key) {
        int value = -1;
        PersistableBundle carrierConfig =
                PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
        if (carrierConfig != null) {
            value = carrierConfig.getInt(key);
        }
        Log.d(LOG_TAG, "getCarrierIntValueByKey key = " + key
                + "Value = " + value + " phone slot = " + mPhone.getPhoneId());
        return value;
    }
    /* @} */

    /* UNISOC: add for feature 884921 durationforward @{ */
    void initCallTimeForward() {
        if (mGsmCdmaPhoneEx != null) {
            mGsmCdmaPhoneEx.getCallForwardingOption(
                    CommandsInterface.CF_REASON_UNCONDITIONAL,
                    CommandsInterface.SERVICE_CLASS_VOICE,
                    null,
                    mHandler.obtainMessage(MyHandler.MESSAGE_GET_CFV,
                            // unused in this case
                            CommandsInterface.CF_ACTION_DISABLE,
                            MyHandler.MESSAGE_GET_CFV, null));
        }
        if (mTcpListener != null) {
            mTcpListener.onStarted(this, true);
        }
    }

    private String getIccId(int subId) {
        String iccId = "";
        if (mPhone != null) {
            IccCard iccCard = mPhone.getIccCard();
            if (iccCard != null) {
                IccRecords iccRecords = iccCard.getIccRecords();
                if (iccRecords != null) {
                    iccId = iccRecords.getIccId();
                }
            }
        }
        return iccId;
    }
    /* @} */

    /* UNISOC: add for bug905164 @{ */
    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        mUpdateButton = ((AlertDialog) getDialog()).getButton(DialogInterface.BUTTON1);
        mConfirmButton = ((AlertDialog) getDialog()).getButton(DialogInterface.BUTTON3);
        EditText editText = getEditText();
        if (editText != null) {
            if (TextUtils.isEmpty(editText.getText().toString())) {
                updateButtonState(false);
            }
            // UNISOC: add for bug967617
            editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(
                        CharSequence charSequence, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(
                        CharSequence charSequence, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    String number = editable.toString().trim();
                    if (TextUtils.isEmpty(number)) {
                        updateButtonState(false);
                    } else {
                        updateButtonState(true);
                    }
                }
            });
        }
    }

    private void updateButtonState(boolean enabled) {
        if (mUpdateButton != null && mUpdateButton.isShown()) {
            mUpdateButton.setEnabled(enabled);
        } else if (mConfirmButton != null && mConfirmButton.isShown()) {
            mConfirmButton.setEnabled(enabled);
        }
    }
    /* @} */
}
