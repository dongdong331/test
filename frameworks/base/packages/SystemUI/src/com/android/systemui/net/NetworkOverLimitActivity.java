/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui.net;

import static android.net.NetworkPolicyManager.EXTRA_NETWORK_TEMPLATE;
import static android.net.NetworkTemplate.MATCH_MOBILE;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.INetworkPolicyManager;
import android.net.NetworkPolicy;
import android.net.NetworkTemplate;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.WindowManager;

import com.android.systemui.R;

/**
 * Notify user that a {@link NetworkTemplate} is over its
 * {@link NetworkPolicy#limitBytes}, giving them the choice of acknowledging or
 * "snoozing" the limit.
 */
public class NetworkOverLimitActivity extends Activity {
    private static final String TAG = "NetworkOverLimitActivity";
    // UNISOC: Add for bug 711969.
    private TelephonyManager mTelephonyManager;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // UNISOC: Add for bug 711969.
        mTelephonyManager = TelephonyManager.from(this);
        final NetworkTemplate template = getIntent().getParcelableExtra(EXTRA_NETWORK_TEMPLATE);
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getLimitedDialogTitleForTemplate(template));
        builder.setMessage(R.string.data_usage_disabled_dialog);

        /* UNISOC: Add for bug 711969. @{ */
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (mTelephonyManager != null) {
                    mTelephonyManager.setDataEnabled(false);
                }
            }
        });
        /* @} */
        builder.setNegativeButton(
                R.string.data_usage_disabled_dialog_enable, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        snoozePolicy(template);
                        /* UNISOC: Add for bug 725642 @{ */
                        if (mTelephonyManager != null) {
                            mTelephonyManager.setDataEnabled(true);
                        }
                        /* @} */
                    }
                });

        final Dialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        });

        dialog.show();
    }

    private void snoozePolicy(NetworkTemplate template) {
        final INetworkPolicyManager policyService = INetworkPolicyManager.Stub.asInterface(
                ServiceManager.getService(Context.NETWORK_POLICY_SERVICE));
        try {
            /* UNISOC: modify for bug920537 @{ */
            if (template != null) {
                policyService.snoozeLimit(template);
            }
            /* @} */
        } catch (RemoteException e) {
            Log.w(TAG, "problem snoozing network policy", e);
        }
    }

    private static int getLimitedDialogTitleForTemplate(NetworkTemplate template) {
        /* UNISOC: modify for bug920537 @{ */
        if (template == null) {
            return R.string.data_usage_disabled_dialog_title;
        }
        /* @} */
        switch (template.getMatchRule()) {
            case MATCH_MOBILE:
                return R.string.data_usage_disabled_dialog_mobile_title;
            default:
                return R.string.data_usage_disabled_dialog_title;
        }
    }
}
