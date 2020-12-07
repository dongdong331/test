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
 * limitations under the License
 */

package com.android.settings.faceid;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;

import com.android.settings.R;

/**
 * Activity which concludes faceid enrollment.
 */
public class FaceIdEnrollFinish extends FaceIdEnrollBase {

    private static final int FACEID_MAX_TEMPLATES_PER_USER = 1;
    private static final String FACEID_FUNCTION = "faceid_function";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.faceid_enroll_finish);
        setHeaderText(R.string.security_settings_faceid_enroll_finish_title);
        Button addButton = (Button) findViewById(R.id.add_another_button);

        int enrolled = 1;
        int max = FACEID_MAX_TEMPLATES_PER_USER;
        if (enrolled >= max) {
            /* Don't show "Add" button if too many faceid already added */
            addButton.setVisibility(View.INVISIBLE);
        } else {
            addButton.setOnClickListener(this);
        }
        Settings.System.putInt(getContentResolver(),
                FACEID_FUNCTION, 1);
    }

    @Override
    protected void onNextButtonClick() {
        setResult(RESULT_FINISHED);
        finish();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.add_another_button) {
            final Intent intent = getEnrollingIntent();
            intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            startActivity(intent);
            finish();
        }
        super.onClick(v);
    }
}
