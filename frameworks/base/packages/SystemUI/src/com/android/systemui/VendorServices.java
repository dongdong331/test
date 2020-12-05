/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.systemui;

import android.content.Intent;
import android.content.res.Configuration;
import android.util.Log;

import com.sprd.systemui.floatkey.SystemUIFloatKey;
/**
 * Placeholder for any vendor-specific services.
 */
public class VendorServices extends SystemUI {
    private static final String TAG = "VendorServices";
    private int mDensity;
    private SystemUIFloatKey mSystemUIFloatKey = null;

    @Override
    public void start() {
        // no-op
        /*SPRD: bug 692486 Assistant touch @{*/
        try {
            final Configuration currentConfig = mContext.getResources().getConfiguration();
            mDensity = currentConfig.densityDpi;
            /*SPRD: bug 692486 Assistant touch @{*/
            /* UNISOC: Modify for bug990337 {@ */
            mSystemUIFloatKey = SystemUIFloatKey.getInstance(mContext);
            /*@}*/
            mSystemUIFloatKey.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
        /*@}*/
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        final int density = newConfig.densityDpi;
        if (density != mDensity) {
            onDensityChanged();
            mDensity = density;
            Log.d(TAG, "onDensityChanged:" +mDensity + "->"+density);
        }

    }

    private void onDensityChanged() {
        mSystemUIFloatKey.restart();
    }

}
