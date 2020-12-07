/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings.users;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.security.OwnerInfoPreferenceController.OwnerInfoCallback;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.util.Log;

public class OwnerInfoSettings extends InstrumentedDialogFragment implements OnClickListener {

    private static final String TAG_OWNER_INFO = "ownerInfo";

    private View mView;
    private int mUserId;
    private LockPatternUtils mLockPatternUtils;
    private EditText mOwnerInfo;
    // SPRD: 886446  modify for limit OwnerInfo length
    private Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUserId = UserHandle.myUserId();
        mLockPatternUtils = new LockPatternUtils(getActivity());

        // SPRD: 886446  modify for limit OwnerInfo length
        mContext = getActivity();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mView = LayoutInflater.from(getActivity()).inflate(R.layout.ownerinfo, null);
        initView();
        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.owner_info_settings_title)
                .setView(mView)
                .setPositiveButton(R.string.save, this)
                .setNegativeButton(R.string.cancel, this)
                .create();
           
       dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {             
                 if (mOwnerInfo != null && mOwnerInfo.requestFocus()) {
                      InputMethodManager imm = (InputMethodManager) getContext().getSystemService(
                            Context.INPUT_METHOD_SERVICE);
                      if (imm != null) {
                            imm.showSoftInput(mOwnerInfo, InputMethodManager.SHOW_IMPLICIT);
                      }
                 } 
            }
        });

        dialog.show();
        return dialog;


    }

    private void initView() {
        String info = mLockPatternUtils.getOwnerInfo(mUserId);

        mOwnerInfo = (EditText) mView.findViewById(R.id.owner_info_edit_text);
        if (!TextUtils.isEmpty(info)) {
            mOwnerInfo.setText(info);
            mOwnerInfo.setSelection(info.length());
        }

        // SPRD: 886446  modify for limit OwnerInfo length
        if (SetLimitOwnerInfoLength.getInstance().isSupport()) {
            SetLimitOwnerInfoLength.getInstance().checkText(mOwnerInfo, mContext);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            String info = mOwnerInfo.getText().toString();
            mLockPatternUtils.setOwnerInfoEnabled(!TextUtils.isEmpty(info), mUserId);
            mLockPatternUtils.setOwnerInfo(info, mUserId);

            if (getTargetFragment() instanceof OwnerInfoCallback) {
                ((OwnerInfoCallback) getTargetFragment()).onOwnerInfoUpdated();
            }
        }
    }

    public static void show(Fragment parent) {
        if (!parent.isAdded()) return;

        final OwnerInfoSettings dialog = new OwnerInfoSettings();
        dialog.setTargetFragment(parent, 0);
        dialog.show(parent.getFragmentManager(), TAG_OWNER_INFO);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.DIALOG_OWNER_INFO_SETTINGS;
    }
}
