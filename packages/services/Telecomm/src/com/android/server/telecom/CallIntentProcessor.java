package com.android.server.telecom;

import com.android.server.telecom.components.ErrorDialogActivity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Trace;
import android.os.UserHandle;
import android.provider.Settings;
import android.os.UserManager;
import android.telecom.DefaultDialerManager;
import android.telecom.Log;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.DisconnectCause;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneNumberUtilsEx;
import android.widget.Toast;
import android.telephony.TelephonyManagerEx;
import android.os.Handler;
import android.os.Looper;
import android.os.AsyncResult;
import android.os.Message;

/**
 * Single point of entry for all outgoing and incoming calls.
 * {@link com.android.server.telecom.components.UserCallIntentProcessor} serves as a trampoline that
 * captures call intents for individual users and forwards it to the {@link CallIntentProcessor}
 * which interacts with the rest of Telecom, both of which run only as the primary user.
 */
public class CallIntentProcessor {
    /* SPRD: Add for bug895875 @{ */
    private static TelecomHandler mHandler;
    private static final int EVENT_ADD_CALL_NOT_ALLOWED = 1;

    private static TelephonyManagerEx mTelephonyMgrEx;

    public interface Adapter {
        void processOutgoingCallIntent(Context context, CallsManager callsManager,
                Intent intent);
        void processIncomingCallIntent(CallsManager callsManager, Intent intent);
        void processUnknownCallIntent(CallsManager callsManager, Intent intent);
    }

    public static class AdapterImpl implements Adapter {
        @Override
        public void processOutgoingCallIntent(Context context, CallsManager callsManager,
                Intent intent) {
            CallIntentProcessor.processOutgoingCallIntent(context, callsManager, intent);
        }

        @Override
        public void processIncomingCallIntent(CallsManager callsManager, Intent intent) {
            CallIntentProcessor.processIncomingCallIntent(callsManager, intent);
        }

        @Override
        public void processUnknownCallIntent(CallsManager callsManager, Intent intent) {
            CallIntentProcessor.processUnknownCallIntent(callsManager, intent);
        }
    }

    public static final String KEY_IS_UNKNOWN_CALL = "is_unknown_call";
    public static final String KEY_IS_INCOMING_CALL = "is_incoming_call";
    /*
     *  Whether or not the dialer initiating this outgoing call is the default dialer, or system
     *  dialer and thus allowed to make emergency calls.
     */
    public static final String KEY_IS_PRIVILEGED_DIALER = "is_privileged_dialer";

    /**
     * The user initiating the outgoing call.
     */
    public static final String KEY_INITIATING_USER = "initiating_user";


    private final Context mContext;
    private final CallsManager mCallsManager;

    public CallIntentProcessor(Context context, CallsManager callsManager) {
        this.mContext = context;
        this.mCallsManager = callsManager;
        mHandler = new TelecomHandler(mContext.getMainLooper());/* SPRD: Add for bug895875 @{ */
    }
    /* SPRD: Add for bug895875 @{ */
    private class TelecomHandler extends Handler {

        //private Context mContext;

        public TelecomHandler(Looper looper) {
            super(looper);
            //mContext = context;
        }
        @Override
        public void handleMessage(Message msg) {
            final boolean isAirplaneModeOn =
                   Settings.System.getInt(mContext.getContentResolver(),
                           Settings.System.AIRPLANE_MODE_ON, 0) != 0;
                switch (msg.what) {
                    case EVENT_ADD_CALL_NOT_ALLOWED:
                        String uriString = (String) msg.obj;
                        //SPRD: Add for bug978689
                        if(mTelephonyMgrEx == null) {
                          mTelephonyMgrEx = TelephonyManagerEx.from(mContext);
                        }
                        if (isAirplaneModeOn && !PhoneNumberUtilsEx.isEmergencyNumber(uriString) &&
                                !mTelephonyMgrEx.isImsRegistered()){
                        Log.i(CallIntentProcessor.class, "Toast.makeText.");
                        Toast.makeText(mContext, R.string.dialog_make_call_airplane_mode_message,
                                Toast.LENGTH_LONG).show();
                        break;
                        }
                }
            }
    }

    public void processIntent(Intent intent) {
        final boolean isUnknownCall = intent.getBooleanExtra(KEY_IS_UNKNOWN_CALL, false);
        Log.i(this, "onReceive - isUnknownCall: %s", isUnknownCall);

        Trace.beginSection("processNewCallCallIntent");
        if (isUnknownCall) {
            processUnknownCallIntent(mCallsManager, intent);
        } else {
            processOutgoingCallIntent(mContext, mCallsManager, intent);
        }
        Trace.endSection();
    }


    /**
     * Processes CALL, CALL_PRIVILEGED, and CALL_EMERGENCY intents.
     *
     * @param intent Call intent containing data about the handle to call.
     */
    static void processOutgoingCallIntent(
            Context context,
            CallsManager callsManager,
            Intent intent) {

        /* SPRD porting: Add for CMCC requirement bug693518 @{ */
        int intentVideoState = intent.getIntExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                VideoProfile.STATE_AUDIO_ONLY);
        if (TelecomCmccHelper.getInstance(context).shouldPreventAddVideoCall(callsManager,
                intentVideoState, callsManager.hasVideoCall())) {
            return;
        }/* @} */

        /* SPRD: Add for VoLTE @{ */
        Bundle b = intent.getExtras();
        boolean isConferenceDial = false;
        String[] callees = null;
        if(b != null){
            isConferenceDial = b.getBoolean("android.intent.extra.IMS_CONFERENCE_REQUEST",false);
            callees = b.getStringArray("android.intent.extra.IMS_CONFERENCE_PARTICIPANTS");
            Log.d(CallIntentProcessor.class, "processOutgoingCallIntent->isConferenceDial:"+isConferenceDial);
        }
        /* @} */

        Uri handle = intent.getData();
        String scheme = null;
        String uriString = null;
        //unisoc bug919609
        if(handle != null){
            scheme = handle.getScheme();
            uriString = handle.getSchemeSpecificPart();
        }
        /* SPRD: Add for bug895875 @{ */
        final boolean isAirplaneModeOn =
                Settings.System.getInt(context.getContentResolver(),
                        Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        //SPRD: Add for bug978689
        if(mTelephonyMgrEx == null) {
          mTelephonyMgrEx = TelephonyManagerEx.from(context);
        }

        Log.i(CallIntentProcessor.class, "isImsRegistered =%d."+mTelephonyMgrEx.isImsRegistered());

        if (isAirplaneModeOn && !PhoneNumberUtilsEx.isEmergencyNumber(uriString) &&
                !mTelephonyMgrEx.isImsRegistered()) {
            Message msg = mHandler.obtainMessage(EVENT_ADD_CALL_NOT_ALLOWED, uriString);
            mHandler.sendMessage(msg);
            return;
        }
        /* @} */

        // Ensure sip URIs dialed using TEL scheme get converted to SIP scheme.
        if (PhoneAccount.SCHEME_TEL.equals(scheme) && PhoneNumberUtils.isUriNumber(uriString)) {
            handle = Uri.fromParts(PhoneAccount.SCHEME_SIP, uriString, null);
        }

        PhoneAccountHandle phoneAccountHandle = intent.getParcelableExtra(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);

        Bundle clientExtras = null;
        if (intent.hasExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)) {
            clientExtras = intent.getBundleExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS);
        }
        if (clientExtras == null) {
            clientExtras = new Bundle();
        }

        /* SPRD: Add for VoLTE @{ */
        clientExtras.putBoolean("android.intent.extra.IMS_CONFERENCE_REQUEST",isConferenceDial);
        clientExtras.putStringArray("android.intent.extra.IMS_CONFERENCE_PARTICIPANTS",callees);
        /* @} */

        // Ensure call subject is passed on to the connection service.
        if (intent.hasExtra(TelecomManager.EXTRA_CALL_SUBJECT)) {
            String callsubject = intent.getStringExtra(TelecomManager.EXTRA_CALL_SUBJECT);
            clientExtras.putString(TelecomManager.EXTRA_CALL_SUBJECT, callsubject);
        }

        final int videoState = intent.getIntExtra( TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                VideoProfile.STATE_AUDIO_ONLY);
        clientExtras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, videoState);

        if (!callsManager.isSelfManaged(phoneAccountHandle,
                (UserHandle) intent.getParcelableExtra(KEY_INITIATING_USER))) {
            boolean fixedInitiatingUser = fixInitiatingUserIfNecessary(context, intent);
            // Show the toast to warn user that it is a personal call though initiated in work
            // profile.
            if (fixedInitiatingUser) {
                Toast.makeText(context, R.string.toast_personal_call_msg, Toast.LENGTH_LONG).show();
            }
        } else {
            Log.i(CallIntentProcessor.class,
                    "processOutgoingCallIntent: skip initiating user check");
        }

        UserHandle initiatingUser = intent.getParcelableExtra(KEY_INITIATING_USER);

        // Send to CallsManager to ensure the InCallUI gets kicked off before the broadcast returns
        Call call = callsManager
                .startOutgoingCall(handle, phoneAccountHandle, clientExtras, initiatingUser,
                        intent);

        if (call != null) {
            sendNewOutgoingCallIntent(context, call, callsManager, intent);
            /* SPRD: Add for bug895875 @{ */
            Message msg = mHandler.obtainMessage(EVENT_ADD_CALL_NOT_ALLOWED,uriString);
            mHandler.sendMessage(msg);
        }
    }

    static void sendNewOutgoingCallIntent(Context context, Call call, CallsManager callsManager,
            Intent intent) {
        // Asynchronous calls should not usually be made inside a BroadcastReceiver because once
        // onReceive is complete, the BroadcastReceiver's process runs the risk of getting
        // killed if memory is scarce. However, this is OK here because the entire Telecom
        // process will be running throughout the duration of the phone call and should never
        // be killed.
        final boolean isPrivilegedDialer = intent.getBooleanExtra(KEY_IS_PRIVILEGED_DIALER, false);

        NewOutgoingCallIntentBroadcaster broadcaster = new NewOutgoingCallIntentBroadcaster(
                context, callsManager, call, intent, callsManager.getPhoneNumberUtilsAdapter(),
                isPrivilegedDialer);
        final int result = broadcaster.processIntent();
        final boolean success = result == DisconnectCause.NOT_DISCONNECTED;

        if (!success && call != null) {
            disconnectCallAndShowErrorDialog(context, call, result);
        }
    }

    /**
     * If the call is initiated from managed profile but there is no work dialer installed, treat
     * the call is initiated from its parent user.
     *
     * @return whether the initiating user is fixed.
     */
    static boolean fixInitiatingUserIfNecessary(Context context, Intent intent) {
        final UserHandle initiatingUser = intent.getParcelableExtra(KEY_INITIATING_USER);
        // UNISOC: add for bug925453
        if (initiatingUser != null && UserUtil.isManagedProfile(context, initiatingUser)) {
            boolean noDialerInstalled = DefaultDialerManager.getInstalledDialerApplications(context,
                    initiatingUser.getIdentifier()).size() == 0;
            if (noDialerInstalled) {
                final UserManager userManager = UserManager.get(context);
                UserHandle parentUserHandle =
                        userManager.getProfileParent(
                                initiatingUser.getIdentifier()).getUserHandle();
                intent.putExtra(KEY_INITIATING_USER, parentUserHandle);

                Log.i(CallIntentProcessor.class, "fixInitiatingUserIfNecessary: no dialer installed"
                        + " for current user; setting initiator to parent %s" + parentUserHandle);
                return true;
            }
        }
        return false;
    }

    static void processIncomingCallIntent(CallsManager callsManager, Intent intent) {
        PhoneAccountHandle phoneAccountHandle = intent.getParcelableExtra(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);

        if (phoneAccountHandle == null) {
            Log.w(CallIntentProcessor.class,
                    "Rejecting incoming call due to null phone account");
            return;
        }
        if (phoneAccountHandle.getComponentName() == null) {
            Log.w(CallIntentProcessor.class,
                    "Rejecting incoming call due to null component name");
            return;
        }

        Bundle clientExtras = null;
        if (intent.hasExtra(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)) {
            clientExtras = intent.getBundleExtra(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS);
        }
        if (clientExtras == null) {
            clientExtras = new Bundle();
        }

        Log.d(CallIntentProcessor.class,
                "Processing incoming call from connection service [%s]",
                phoneAccountHandle.getComponentName());
        callsManager.processIncomingCallIntent(phoneAccountHandle, clientExtras);
    }

    static void processUnknownCallIntent(CallsManager callsManager, Intent intent) {
        PhoneAccountHandle phoneAccountHandle = intent.getParcelableExtra(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);

        if (phoneAccountHandle == null) {
            Log.w(CallIntentProcessor.class, "Rejecting unknown call due to null phone account");
            return;
        }
        if (phoneAccountHandle.getComponentName() == null) {
            Log.w(CallIntentProcessor.class, "Rejecting unknown call due to null component name");
            return;
        }

        callsManager.addNewUnknownCall(phoneAccountHandle, intent.getExtras());
    }

    private static void disconnectCallAndShowErrorDialog(
            Context context, Call call, int errorCode) {
        call.disconnect();
        final Intent errorIntent = new Intent(context, ErrorDialogActivity.class);
        int errorMessageId = -1;
        switch (errorCode) {
            case DisconnectCause.INVALID_NUMBER:
            case DisconnectCause.NO_PHONE_NUMBER_SUPPLIED:
                errorMessageId = R.string.outgoing_call_error_no_phone_number_supplied;
                break;
        }
        if (errorMessageId != -1) {
            errorIntent.putExtra(ErrorDialogActivity.ERROR_MESSAGE_ID_EXTRA, errorMessageId);
            errorIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivityAsUser(errorIntent, UserHandle.CURRENT);
        }
    }
}
