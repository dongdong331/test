/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.sprd.quickstepext.memory;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;
import android.widget.LinearLayout;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;

public class MeminfoContainerView extends LinearLayout {

    private TextView mAvailText;
    private TextView mTotalText;
    private Context mContext;
    private float mContentAlpha = 0;

    public MeminfoContainerView(Context context) {
        this(context, null);
    }

    public MeminfoContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MeminfoContainerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAvailText = findViewById(R.id.recents_memory_avail);
        mTotalText = findViewById(R.id.recents_memory_total);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void setText(String availStr, String totalStr) {
        mAvailText.setText(mContext.getString(R.string.recents_memory_avail, availStr));
        mTotalText.setText(totalStr);
    }

    public void setContentAlpha(float alpha) {
        alpha = Utilities.boundToRange(alpha, 0, 1);
        mContentAlpha = alpha;
        setAlpha(alpha);
    }

    public float getContentAlpha() {
        return mContentAlpha;
    }
}
