package com.android.phone;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.GsmCdmaPhoneEx;

import static com.android.phone.TimeConsumingPreferenceActivity.RESPONSE_ERROR;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

public class CallWaitingSwitchPreference extends SwitchPreference {
    private static final String LOG_TAG = "CallWaitingSwitchPreference";
    private final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private final MyHandler mHandler = new MyHandler();
    private Phone mPhone;
    private TimeConsumingPreferenceListener mTcpListener;
    // UNISOC: add for bug821579
    private static final String CW_PROP = "gsm.ss.call_waiting";
    // UNISOC: add for bug952191
    public int mCWArray[];
    public CallWaitingSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public CallWaitingSwitchPreference(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.switchPreferenceStyle);
    }

    public CallWaitingSwitchPreference(Context context) {
        this(context, null);
    }

    /* package */ void init(
            TimeConsumingPreferenceListener listener, boolean skipReading, Phone phone) {
        mPhone = phone;
        mTcpListener = listener;

        if (!skipReading) {
            mPhone.getCallWaiting(mHandler.obtainMessage(MyHandler.MESSAGE_GET_CALL_WAITING,
                    MyHandler.MESSAGE_GET_CALL_WAITING, MyHandler.MESSAGE_GET_CALL_WAITING));
            if (mTcpListener != null) {
                mTcpListener.onStarted(this, true);
            }
        }
    }

    @Override
    protected void onClick() {
        super.onClick();

        if (mTcpListener != null) {
            mTcpListener.setSuppServiceFlag(GsmCdmaPhoneEx.SS_FLAG_DES_START);
        }
        mPhone.setCallWaiting(isChecked(),
                mHandler.obtainMessage(MyHandler.MESSAGE_SET_CALL_WAITING));
        if (mTcpListener != null) {
            mTcpListener.onStarted(this, false);
        }
    }

    private class MyHandler extends Handler {
        static final int MESSAGE_GET_CALL_WAITING = 0;
        static final int MESSAGE_SET_CALL_WAITING = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_CALL_WAITING:
                    handleGetCallWaitingResponse(msg);
                    break;
                case MESSAGE_SET_CALL_WAITING:
                    handleSetCallWaitingResponse(msg);
                    break;
            }
        }

        private void handleGetCallWaitingResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (mTcpListener != null) {
                if (msg.arg2 == MESSAGE_SET_CALL_WAITING) {
                    mTcpListener.onFinished(CallWaitingSwitchPreference.this, false);
                } else {
                    mTcpListener.onFinished(CallWaitingSwitchPreference.this, true);
                }
            }
            // UNISOC: add for bug952191
            mCWArray = null;
            if (ar.exception instanceof CommandException) {
                if (DBG) {
                    Log.d(LOG_TAG, "handleGetCallWaitingResponse: CommandException=" +
                            ar.exception);
                }
                if (mTcpListener != null) {
                    mTcpListener.onException(CallWaitingSwitchPreference.this,
                            (CommandException)ar.exception);
                }
            } else if (ar.userObj instanceof Throwable || ar.exception != null) {
                // Still an error case but just not a CommandException.
                if (DBG) {
                    Log.d(LOG_TAG, "handleGetCallWaitingResponse: Exception" + ar.exception);
                }
                if (mTcpListener != null) {
                    mTcpListener.onError(CallWaitingSwitchPreference.this, RESPONSE_ERROR);
                }
            } else {
                if (DBG) {
                    Log.d(LOG_TAG, "handleGetCallWaitingResponse: CW state successfully queried.");
                }
                int[] cwArray = (int[])ar.result;
                // UNISOC: modify for bug952191
                handleGetCWResult(cwArray);
            }
        }

        private void handleSetCallWaitingResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (ar.exception != null) {
                if (DBG) {
                    Log.d(LOG_TAG, "handleSetCallWaitingResponse: ar.exception=" + ar.exception);
                }
                //setEnabled(false);
            }
            if (DBG) Log.d(LOG_TAG, "handleSetCallWaitingResponse: re get");
            if (mTcpListener != null) {
                mTcpListener.setSuppServiceFlag(GsmCdmaPhoneEx.SS_FLAG_DES_END);
            }
            mPhone.getCallWaiting(obtainMessage(MESSAGE_GET_CALL_WAITING,
                    MESSAGE_SET_CALL_WAITING, MESSAGE_SET_CALL_WAITING, ar.exception));
        }
    }

    /* UNISOC: add for bug952191 @{ */
    public void handleGetCWResult(int tmpCwArray[]) {
        mCWArray = tmpCwArray;
        // If cwArray[0] is = 1, then cwArray[1] must follow,
        // with the TS 27.007 service class bit vector of services
        // for which call waiting is enabled.
        try {
            setChecked(((tmpCwArray[0] == 1) && ((tmpCwArray[1] & 0x01) == 0x01)));
            //isInitFinish = true;
            if (hasService()) {
                setEnabled(true);
            } else {
                setEnabled(false);
            }
            /* SPRD: add for bug821579 @{ */
            Log.d(LOG_TAG, "set CW_PROP " + isChecked());
            SystemProperties.set(CW_PROP, String.valueOf(isChecked()));
            /* @} */
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.e(LOG_TAG, "handleGetCallWaitingResponse: improper result: err ="
                    + e.getMessage());
        }
    }

    private boolean hasService() {
        Context context = (Context)mTcpListener;
        TelephonyManager mTeleMgr = (TelephonyManager)context.getSystemService(Context
                .TELEPHONY_SERVICE);
        ServiceState ss = mTeleMgr.getServiceStateForSubscriber(mPhone
                .getSubId());
        if (ss != null) {
            switch (ss.getVoiceRegState()) {
                case ServiceState.STATE_POWER_OFF:
                    return false;
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    return ss.getDataRegState() == ServiceState.STATE_IN_SERVICE;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }
    /* @} */
}
