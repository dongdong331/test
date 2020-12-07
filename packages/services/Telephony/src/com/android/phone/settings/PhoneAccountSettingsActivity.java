/**
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.phone.settings;

import android.app.ActionBar;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

import com.android.phone.R;
import com.unisoc.phone.callsettings.ActivityContainer;

public class PhoneAccountSettingsActivity extends PreferenceActivity {
    /* SPRD: add for bug903733 @{ */
    private ActivityContainer mActivityContainer;
    private static final int NO_DECIDE_BY_PHONEID = -1;
    /* @} */

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.phone_accounts);
        }
        getFragmentManager().beginTransaction().replace(
                android.R.id.content, new PhoneAccountSettingsFragment()).commit();
        /* SPRD: add for bug903733 @{ */
        mActivityContainer = ActivityContainer.getInstance();
        mActivityContainer.setApplication(getApplication());
        mActivityContainer.addActivity(this, NO_DECIDE_BY_PHONEID);
        /* @} */
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /* SPRD: add for bug903733 @{ */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mActivityContainer != null) {
            mActivityContainer.removeActivity(this);
        }
    }
    /* @} */
}
