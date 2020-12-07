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

package com.android.settings.users;

import android.app.AddonManager;
import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;
import android.util.Log;
import com.android.settings.R;

public class SetLimitOwnerInfoLength {
    static SetLimitOwnerInfoLength mInstance;
    public static Context context;
    public static String TAG = "SetLimitOwnerInfoLength";
    public static boolean DEBUG = true;

    public static SetLimitOwnerInfoLength getInstance() {
        if (mInstance == null) {
            mInstance = (SetLimitOwnerInfoLength) AddonManager.getDefault().getAddon(
                    R.string.feature_limit_ownerinfo_length_addon, SetLimitOwnerInfoLength.class);
        }
        return mInstance;
    }

    public boolean isSupport() {
        if (DEBUG) {
            Log.i(TAG,"isSupport");
        }
        return true;
    }

    public void checkText(EditText mOwnerInfo, Context context) {
        if (DEBUG) {
            Log.i(TAG,"checkText");
        }
    }
}
