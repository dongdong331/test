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

package com.android.server.telecom;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.telephony.SmsMessage;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Ultra-simple subclass of EditTextPreference that allows the "title" to wrap
 * onto multiple lines.
 *
 * (By default, the title of an EditTextPreference is singleLine="true"; see
 * preference_holo.xml under frameworks/base.  But in the "Respond via SMS"
 * settings UI we want titles to be multi-line, since the customized messages
 * might be fairly long, and should be able to wrap.)
 *
 * TODO: This is pretty cumbersome; it would be nicer for the framework to
 * either allow modifying the title's attributes in XML, or at least provide
 * some way from Java (given an EditTextPreference) to reach inside and get a
 * handle to the "title" TextView.
 *
 * TODO: Also, it would reduce clutter if this could be an inner class in
 * RespondViaSmsManager.java, but then there would be no way to reference the
 * class from XML.  That's because
 *    <com.android.server.telecom.MultiLineTitleEditTextPreference ... />
 * isn't valid XML syntax due to the "$" character.  And Preference
 * elements don't have a "class" attribute, so you can't do something like
 * <view class="com.android.server.telecom.Foo$Bar"> as you can with regular views.
 */
public class MultiLineTitleEditTextPreference extends EditTextPreference {
    /* UNISOC: modify by bug895851 @{ */
    private Button mPositiveButton;
    private EditText mEditText;
    /* @} */

    public MultiLineTitleEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public MultiLineTitleEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MultiLineTitleEditTextPreference(Context context) {
        super(context);
    }

    // The "title" TextView inside an EditTextPreference defaults to
    // singleLine="true" (see preference_holo.xml under frameworks/base.)
    // We override onBindView() purely to look up that TextView and call
    // setSingleLine(false) on it.
    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        TextView textView = (TextView) view.findViewById(com.android.internal.R.id.title);
        if (textView != null) {
            textView.setSingleLine(false);
        }
        /* UNISOC: modify by bug895851 @{ */
        mEditText = getEditText();
        mEditText.addTextChangedListener(mTextWatcher);
        /* @} */
        // UNISOC: modify for bug 900883 910932
        mEditText.setFilters(new InputFilter[] { new SmsLengthFilter() });
    }

    /* UNISOC: modify by bug895851 @{ */
    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        final AlertDialog editDialog = (AlertDialog) getDialog();
        mPositiveButton = editDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        /* UNISOC: modify by bug903652 @{ */
        if (mEditText != null) {
            mEditText.setFocusable(true);
            mEditText.setFocusableInTouchMode(true);
            mEditText.requestFocus();
        }
        /* @} */
        // UNISOC: modify for bug964791
        mEditText.setSelection(mEditText.getText().length());
    }

    private TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void onTextChanged(CharSequence s, int start, int before,
                int count) {
            if (mPositiveButton != null) {
                mPositiveButton.setEnabled(s.toString() != null
                        && !TextUtils.isEmpty(s.toString().trim()));
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count,
                int after) {

        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };
    /* @} */
    // UNISOC: add for bug910932
    private static class SmsLengthFilter implements InputFilter{

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                                   int dstart, int dend) {
            String sourceSubStr = source.subSequence(start, end).toString();
            String orignalStart = dest.subSequence(0, dstart).toString() + dest.subSequence(dend, dest.length()).toString();

            String s = orignalStart + sourceSubStr;
            int[] params = SmsMessage.calculateLength(s, false);
            int endIndex = start + 1;
            if (params[0] == 1) {
                return null;
            } else if (!containsEmoji(source.subSequence(start, end).toString())){
                for (; endIndex <= end; ++endIndex) {
                    int[] ps = SmsMessage.calculateLength(orignalStart + source.subSequence(start, endIndex).toString(), false);
                    if (ps[0] > 1) {
                        endIndex--;
                        break;
                    }
                }
                if (endIndex == start) {
                    return "";
                } else {
                    return source.subSequence(start, endIndex);
                }
            } else {
                return "";
            }
        }

        private boolean containsEmoji(String source) {
            int len = source.length();
            for (int i = 0; i < len; i++) {
                char codePoint = source.charAt(i);
                if (!isEmojiCharacter(codePoint)) {
                    return true;
                }
            }
            return false;
        }

        private  boolean isEmojiCharacter(char codePoint) {
            return (codePoint == 0x0) || (codePoint == 0x9) || (codePoint == 0xA) ||
                    (codePoint == 0xD) || ((codePoint >= 0x20) && (codePoint <= 0xD7FF)) ||
                    ((codePoint >= 0xE000) && (codePoint <= 0xFFFD)) || ((codePoint >= 0x10000)
                    && (codePoint <= 0x10FFFF));
        }
    }
}
