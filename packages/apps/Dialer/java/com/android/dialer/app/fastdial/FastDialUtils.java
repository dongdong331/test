/*
 * SRPD: create fastdial
 */
package com.android.dialer.app.fastdial;

import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManagerEx;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import com.android.dialer.app.R;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.util.CallUtil;

public class FastDialUtils extends DialogFragment {
    private static final String TAG = "FastDialUtils";
    private static final boolean DBG = true;
    private static final String CONTENT_HEAD = "content";
    private static final String FAST_DIAL_SP = "fast_dial_";
    private static final String SHARED_PREFERENCES_NAME = "fast_dial_numbers";

    /* SPRD:BUG 896033 PreCall for start fastdial @{*/
    public static String getFastDialPhoneNumber(Fragment fragment, Editable edit, int id) {
        /* SPRD: add for bug587904 @{ */
        if ((fragment != null) && (fragment.getActivity() != null)
                && (fragment.getActivity().getApplicationContext() != null)) {
            Context appContext = fragment.getActivity().getApplicationContext();
            UserManager userManager = (UserManager)
                    appContext.getSystemService(Context.USER_SERVICE);
            /* SPRD: add for bug620099 @{ */
            if (userManager != null && !userManager.isSystemUser() && edit.length() == 1) {
                Toast.makeText(appContext, appContext.getText(R.string.fast_dial_in_guest_mode),
                        Toast.LENGTH_SHORT).show();
                return null;
            }
        }
        /* @} */
        int key = 0;
        switch (id) {
            case R.id.two:
                key = 2;
                break;
            case R.id.three:
                key = 3;
                break;
            case R.id.four:
                key = 4;
                break;
            case R.id.five:
                key = 5;
                break;
            case R.id.six:
                key = 6;
                break;
            case R.id.seven:
                key = 7;
                break;
            case R.id.eight:
                key = 8;
                break;
            case R.id.nine:
                key = 9;
                break;
            default:
                Log.e(TAG, "Not support key id = " + id);
                return null;
        }
        int length = edit.length();
        if (length > 1) {
            return null;
        } else if (length == 1) {
            int code = edit.toString().charAt(0) - '0';
            if (code != key) {
                return null;
            }
        }
        if (DBG) {
            Log.d(TAG, "onCallFastDial : key = " + key);
        }
        /* SPRD: add for bug753638 @{ */
        Context appContext = fragment.getActivity().getApplicationContext();
        if (FastDialManager.getInstance() == null) {
            FastDialManager.init(appContext);
        }
        String fastCall = FastDialManager.getInstance().getCallNumber(key);
        /* SPRD: add for bug790939 @{ */
        final boolean isAirplaneModeOn = Settings.System.getInt(appContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        TelephonyManager telephonyManager = (TelephonyManager)
                appContext.getSystemService(Context.TELEPHONY_SERVICE);
        boolean isImsRegistered = telephonyManager.isImsRegistered();
        if (isAirplaneModeOn
                && !PhoneNumberUtils.isEmergencyNumber(fastCall)
                && !isImsRegistered) {
            Toast.makeText(appContext, R.string.dialog_make_call_airplane_mode_message,
                    Toast.LENGTH_LONG).show();
            return null;
        }
        /* @} */
        if (TextUtils.isEmpty(fastCall)) {
            Toast.makeText(appContext, appContext.getText(R.string.no_fast_dial_number),
                    Toast.LENGTH_SHORT).show();
            return null;
        }
        if (DBG) {
            Log.d(TAG, "onCallFastDial : fastCall = " + fastCall);
        }
        return fastCall;
        /* @} */
    }
    /* @} */

    private static Intent getCallIntent(String number) {
        return getCallIntent(CallUtil.getCallUri(number));
    }

    private static Intent getCallIntent(Uri uri) {
        return new Intent(Intent.ACTION_CALL, uri);
    }
}
