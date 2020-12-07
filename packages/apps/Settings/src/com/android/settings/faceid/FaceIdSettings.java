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
 * limitations under the License.
 */

package com.android.settings.faceid;


import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceScreen;
import android.support.v14.preference.SwitchPreference;
import android.text.Annotation;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.hardware.face.Face;
import android.hardware.face.FaceManager;
import android.hardware.face.FaceManager.RemovalCallback;

import com.android.internal.logging.MetricsLogger;
import com.android.settings.password.ChooseLockGeneric;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.SubSettings;
import android.os.SystemProperties;

import android.provider.Settings;

import java.util.List;

/**
 * Settings screen for faceid
 */
public class FaceIdSettings extends SubSettings {
    /**
     * Used by the faceid settings wizard to indicate the wizard is
     * finished, and each activity in the wizard should finish.
     * <p>
     * Previously, each activity in the wizard would finish itself after
     * starting the next activity. However, this leads to broken 'Back'
     * behavior. So, now an activity does not finish itself until it gets this
     * result.
     */
    static final int RESULT_FINISHED = RESULT_FIRST_USER;
    private static final long LOCKOUT_DURATION = 30000; // time we have to wait for fp to reset, ms
    private static final int FACEID_MAX_TEMPLATES_PER_USER = 5;
    private static final int MetricsLogger_FACEID = 49;
    private static final int KEYGUARD_DISABLE_FACEID = 1 << 5;

    public static final String FACEID_BRIGHTNESS = "faceid_brightness";
    public static final boolean FACEID_BRIGHTNESS_FEATURE_ENABLED = false;//UNISOC: Bug 979458 disable faceid brightness

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, FaceIdSettingsFragment.class.getName());
        return modIntent;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (FaceIdSettingsFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CharSequence msg = getText(R.string.security_settings_faceid_preference_title);
        setTitle(msg);
    }

    public static class FaceIdSettingsFragment extends SettingsPreferenceFragment
        implements OnPreferenceChangeListener {
        private static final int MAX_RETRY_ATTEMPTS = 20;
        private static final int RESET_HIGHLIGHT_DELAY_MS = 500;

        private static final String TAG = "FaceIdSettingsFragment";
        private static final String KEY_FACEID_ITEM_PREFIX = "key_faceid_item";
        private static final String KEY_FACEID_ADD = "key_faceid_add";
        private static final String KEY_FACEID_ENABLE_KEYGUARD_TOGGLE =
                "faceid_enable_keyguard_toggle";
        private static final String KEY_LAUNCHED_CONFIRM = "launched_confirm";
        private static final String KEY_FACEID_SETTINGS_SWITCH = "faceid_settings_switch";
        private static final String KEY_FACEID_LIVENESS_MODE_SWITCH = "faceid_livenessmode_switch";
        private static final String KEY_FACEID_EDIT = "faceid_edit";
        private static final String KEY_FACEID_DELETE = "faceid_delete";
        private static final String FACEID_FUNCTION = "faceid_function";
        private static final String FACEID_LIVENESS_MODE_PROP = "persist.vendor.faceid.livenessmode";
        private static final String KEY_FACEID_BRIGHTNESS_SWITCH = "faceid_brightness_switch";

        private static final int MSG_REFRESH_FACEID_TEMPLATES = 1000;

        private static final int CONFIRM_REQUEST = 101;
        private static final int CHOOSE_LOCK_GENERIC_REQUEST = 102;

        private static final int ADD_FACEID_REQUEST = 10;

        protected static final boolean DEBUG = true;

        private FaceManager mFaceManager;
        private CancellationSignal mFaceIdCancel;
        private boolean mInFaceIdLockout;
        private byte[] mToken;
        private boolean mLaunchedConfirm;
        private Drawable mHighlightDrawable;
        private SwitchPreference mFaceIdPreferenceSwitch, mFaceIdLivenessModeSwitch;
        private Preference mFaceIdEdit, mFaceIdDelete;
        private SwitchPreference mFaceIdBrightnessSwitch;

        private RemovalCallback mRemoveCallback = new RemovalCallback() {

            @Override
            public void onRemovalSucceeded(Face face) {
                mHandler.obtainMessage(MSG_REFRESH_FACEID_TEMPLATES,
                        face.getFaceId(), 0).sendToTarget();
            }

            @Override
            public void onRemovalError(Face face, int errMsgId, CharSequence errString) {
                final Activity activity = getActivity();
                if (activity != null) {
                    Toast.makeText(activity, errString, Toast.LENGTH_SHORT).show();
                }
            }
        };
        private final Handler mHandler = new Handler() {
            @Override
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case MSG_REFRESH_FACEID_TEMPLATES:
                        //removeFaceIdPreference(msg.arg1);
                        //updateAddPreference();
                        updatePrefrenceState();
                    break;
                }
            };
        };

        @Override
        public int getMetricsCategory() {
            return MetricsLogger_FACEID;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (savedInstanceState != null) {
                mToken = savedInstanceState.getByteArray(
                        ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
                mLaunchedConfirm = savedInstanceState.getBoolean(
                        KEY_LAUNCHED_CONFIRM, false);
            }

            Activity activity = getActivity();
            mFaceManager = (FaceManager) activity.getSystemService(Context.FACE_SERVICE);

            // Need to authenticate a session token if none
            if (mToken == null && mLaunchedConfirm == false) {
                mLaunchedConfirm = true;
                launchChooseOrConfirmLock();
            }
        }

        @Override
        public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
        }

        private boolean isFaceIdDisabled() {
            final DevicePolicyManager dpm =
                    (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            return dpm != null && (dpm.getKeyguardDisabledFeatures(null)
                    & KEYGUARD_DISABLE_FACEID) != 0;
        }

        protected void removeFaceIdPreference(int faceidId) {
            /*String name = genKey(faceidId);
            Preference prefToRemove = findPreference(name);
            if (prefToRemove != null) {
                if (!getPreferenceScreen().removePreference(prefToRemove)) {
                    Log.w(TAG, "Failed to remove preference with key " + name);
                }
            } else {
                Log.w(TAG, "Can't find preference to remove: " + name);
            }*/
        }

        /**
         * Important!
         *
         * Don't forget to update the SecuritySearchIndexProvider if you are doing any change in the
         * logic or adding/removing preferences here.
         */
        private PreferenceScreen createPreferenceHierarchy() {
            PreferenceScreen root = getPreferenceScreen();
            if (root != null) {
                root.removeAll();
            }
            addPreferencesFromResource(R.xml.security_settings_faceid);
            root = getPreferenceScreen();

            mFaceIdPreferenceSwitch = new SwitchPreference(getActivity());
            mFaceIdPreferenceSwitch.setKey(KEY_FACEID_SETTINGS_SWITCH);
            mFaceIdPreferenceSwitch.setTitle(R.string.security_settings_faceid_preference_title);
            mFaceIdPreferenceSwitch.setSummary(R.string.security_settings_faceid_switch_summary);
            mFaceIdPreferenceSwitch.setChecked(Settings.System.getInt(getContentResolver(),
                    FACEID_FUNCTION, 0) == 1);
            mFaceIdPreferenceSwitch.setOnPreferenceChangeListener(this);
            root.addPreference(mFaceIdPreferenceSwitch);
            //mFaceIdPreferenceSwitch = (SwitchPreference) root.findPreference(KEY_FACEID_SETTINGS_SWITCH);
            //mFaceIdLivenessModeSwitch = (SwitchPreference) root.findPreference(KEY_FACEID_LIVENESS_MODE_SWITCH);

            mFaceIdLivenessModeSwitch = new SwitchPreference(getActivity());
            mFaceIdLivenessModeSwitch.setKey(KEY_FACEID_LIVENESS_MODE_SWITCH);
            mFaceIdLivenessModeSwitch.setTitle(R.string.faceid_livenessmodepreference_title);
            mFaceIdLivenessModeSwitch.setSummary(R.string.faceid_livenessmode_summary);
            mFaceIdLivenessModeSwitch.setChecked(SystemProperties.getBoolean(FACEID_LIVENESS_MODE_PROP + ActivityManager.getCurrentUser(), false));
            mFaceIdLivenessModeSwitch.setOnPreferenceChangeListener(this);
            root.addPreference(mFaceIdLivenessModeSwitch);

            //faceid brightness feature.
            if(FACEID_BRIGHTNESS_FEATURE_ENABLED) {
                mFaceIdBrightnessSwitch = new SwitchPreference(getActivity());
                mFaceIdBrightnessSwitch.setKey(KEY_FACEID_BRIGHTNESS_SWITCH);
                mFaceIdBrightnessSwitch.setTitle(R.string.faceid_brightness_preference_title);
                mFaceIdBrightnessSwitch.setSummary(R.string.faceid_brightness_summary);
                mFaceIdBrightnessSwitch.setChecked(Settings.System.getInt(getContentResolver(),
                        FACEID_BRIGHTNESS, 0) == 1);
                mFaceIdBrightnessSwitch.setOnPreferenceChangeListener(this);
                root.addPreference(mFaceIdBrightnessSwitch);
            }

            mFaceIdEdit = new Preference(getActivity());
            mFaceIdEdit.setKey(KEY_FACEID_EDIT);
            mFaceIdEdit.setTitle(R.string.faceid_Edit_title);
            mFaceIdEdit.setOnPreferenceChangeListener(this);
            root.addPreference(mFaceIdEdit);

            mFaceIdDelete = new Preference(getActivity());
            mFaceIdDelete.setKey(KEY_FACEID_DELETE);
            mFaceIdDelete.setTitle(R.string.faceid_delete_title);
            mFaceIdDelete.setOnPreferenceChangeListener(this);
            root.addPreference(mFaceIdDelete);
            //addFaceIdItemPreferences(root);
            updatePrefrenceState();
            return root;
        }

        private void updatePrefrenceState() {
            final List<Face> items = mFaceManager.getEnrolledFaces();
            final int facesCount = items.size();
            if(facesCount > 0) {
                mFaceIdDelete.setEnabled(true);
            } else {
                mFaceIdEdit.setTitle(R.string.security_settings_faceid_enroll_add_faceid_title);
                mFaceIdDelete.setEnabled(false);
            }
        }

        private void addFaceIdItemPreferences(PreferenceGroup root) {
            root.removeAll();
            final List<Face> items = mFaceManager.getEnrolledFaces();
            final int facesCount = items.size();
            for (int i = 0; i < facesCount; i++) {
                final Face item = items.get(i);
                FaceIdPreference pref = new FaceIdPreference(root.getContext());
                pref.setKey(genKey(item.getFaceId()));
                pref.setTitle(item.getName());
                pref.setFace(item);
                pref.setPersistent(false);
                root.addPreference(pref);
                pref.setOnPreferenceChangeListener(this);
            }
            /*Preference addPreference = new Preference(root.getContext());
            addPreference.setKey(KEY_FACEID_ADD);
            addPreference.setTitle(R.string.faceid_add_title);
            addPreference.setIcon(R.drawable.ic_add_24dp);
            root.addPreference(addPreference);
            addPreference.setOnPreferenceChangeListener(this);
            updateAddPreference();*/
        }

        private void updateAddPreference() {
            /* Disable preference if too many faceid added */
            final int max = FACEID_MAX_TEMPLATES_PER_USER;
            boolean tooMany = false;//mFaceManager.getEnrolledFaces().size() >= max;
            CharSequence maxSummary = tooMany ?
                    getContext().getString(R.string.faceid_add_max, max) : "";
            Preference addPreference = findPreference(KEY_FACEID_ADD);
            addPreference.setSummary(maxSummary);
            addPreference.setEnabled(!tooMany);
        }

        private static String genKey(int id) {
            return KEY_FACEID_ITEM_PREFIX + "_" + id;
        }

        @Override
        public void onResume() {
            super.onResume();
            // Make sure we reload the preference hierarchy since faceides may be added,
            // deleted or renamed.
            updatePreferences();
        }

        private void updatePreferences() {
            createPreferenceHierarchy();
        }

        @Override
        public void onPause() {
            super.onPause();
        }

        @Override
        public void onSaveInstanceState(final Bundle outState) {
            outState.putByteArray(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN,
                    mToken);
            outState.putBoolean(KEY_LAUNCHED_CONFIRM, mLaunchedConfirm);
        }

        @Override
        public boolean onPreferenceTreeClick(Preference pref) {
            final String key = pref.getKey();
            if (KEY_FACEID_ADD.equals(key)) {
                Intent intent = new Intent();
                intent.setClassName("com.android.settings",
                        FaceIdEnrollPreview.class.getName());
                intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, mToken);
                startActivityForResult(intent, ADD_FACEID_REQUEST);
            } else if (pref instanceof FaceIdPreference) {
                FaceIdPreference faceidref = (FaceIdPreference) pref;
                final Face face = faceidref.getFace();
                showRenameDeleteDialog(face);
                return super.onPreferenceTreeClick(pref);
            } else if (KEY_FACEID_EDIT.equals(key)) {
                Intent intent = new Intent();
                intent.setClassName("com.android.settings",
                        FaceIdEnrollPreview.class.getName());
                intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, mToken);
                startActivityForResult(intent, ADD_FACEID_REQUEST);
            } else if (KEY_FACEID_DELETE.equals(key)) {
                ConfirmLastDeleteDialog lastDeleteDialog = new ConfirmLastDeleteDialog();
                Bundle args = new Bundle();
                lastDeleteDialog.setArguments(args);
                lastDeleteDialog.setTargetFragment(this, 0);
                lastDeleteDialog.show(getFragmentManager(),
                        ConfirmLastDeleteDialog.class.getName());
            }
            return true;
        }

        private void showRenameDeleteDialog(final Face face) {
            RenameDeleteDialog renameDeleteDialog = new RenameDeleteDialog();
            Bundle args = new Bundle();
            args.putParcelable("face", face);
            renameDeleteDialog.setArguments(args);
            renameDeleteDialog.setTargetFragment(this, 0);
            renameDeleteDialog.show(getFragmentManager(), RenameDeleteDialog.class.getName());
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            boolean result = true;
            final String key = preference.getKey();
            if (KEY_FACEID_ENABLE_KEYGUARD_TOGGLE.equals(key)) {
                // TODO
            } else if (KEY_FACEID_SETTINGS_SWITCH.equals(key)) {
                Log.d(TAG, "sprd_facelock onpreferenceChange()  KEY_FACEID_SETTINGS value = " + (Boolean) value);
                if ((Boolean) value) {
                    Settings.System.putInt(getContentResolver(),
                            FACEID_FUNCTION, 1);
                } else {
                    Settings.System.putInt(getContentResolver(),
                            FACEID_FUNCTION, 0);
                }
            } else if (KEY_FACEID_BRIGHTNESS_SWITCH.equals(key)) {
                Log.d(TAG, "sprd_facelock onpreferenceChange()  KEY_FACEID_BRIGHTNESS_SWITCH value = " + (Boolean) value);
                if ((Boolean) value) {
                    Settings.System.putInt(getContentResolver(),
                            FACEID_BRIGHTNESS, 1);
                } else {
                    Settings.System.putInt(getContentResolver(),
                            FACEID_BRIGHTNESS, 0);
                }
            } else if (KEY_FACEID_LIVENESS_MODE_SWITCH.equals(key)) {
                Log.d(TAG, "sprd_facelock onpreferenceChange()  KEY_FACEID_SETTINGS value = " + (Boolean) value);
                if ((Boolean) value) {
                    SystemProperties.set(FACEID_LIVENESS_MODE_PROP + ActivityManager.getCurrentUser(), "1");
                } else {
                    SystemProperties.set(FACEID_LIVENESS_MODE_PROP + ActivityManager.getCurrentUser(), "0");
                }
            } else {
                Log.v(TAG, "Unknown key:" + key);
            }
            return result;
        }

        /*@Override
        protected int getHelpResource() {
            return R.string.help_url_faceid;
        }*/

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == CHOOSE_LOCK_GENERIC_REQUEST
                    || requestCode == CONFIRM_REQUEST) {
                if (resultCode == RESULT_FINISHED || resultCode == RESULT_OK) {
                    // The lock pin/pattern/password was set. Start enrolling!
                    if (data != null) {
                        mToken = data.getByteArrayExtra(
                                ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
                    }
                }
            }

            if (mToken == null) {
                // Didn't get an authentication, finishing
                getActivity().finish();
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (getActivity().isFinishing()) {
                int result = mFaceManager.postEnroll();
                if (result < 0) {
                    Log.w(TAG, "postEnroll failed: result = " + result);
                }
            }
        }

        private Drawable getHighlightDrawable() {
            if (mHighlightDrawable == null) {
                final Activity activity = getActivity();
                if (activity != null) {
                    mHighlightDrawable = activity.getDrawable(R.drawable.preference_highlight);
                }
            }
            return mHighlightDrawable;
        }

        private void launchChooseOrConfirmLock() {
            Intent intent = new Intent();
            long challenge = mFaceManager.preEnroll();
            ChooseLockSettingsHelper helper = new ChooseLockSettingsHelper(getActivity(), this);
            if (!helper.launchConfirmationActivity(CONFIRM_REQUEST,
                    getString(R.string.security_settings_faceid_preference_title),
                    null, null, challenge)) {
                intent.setClassName("com.android.settings", ChooseLockGeneric.class.getName());
                intent.putExtra(ChooseLockGeneric.ChooseLockGenericFragment.MINIMUM_QUALITY_KEY,
                        DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
                intent.putExtra(ChooseLockGeneric.ChooseLockGenericFragment.HIDE_DISABLED_PREFS,
                        true);
                intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, true);
                intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, challenge);
                startActivityForResult(intent, CHOOSE_LOCK_GENERIC_REQUEST);
            }
        }

        private void deleteFace() {
            Log.v(TAG, "deleteFace");
            final List<Face> items = mFaceManager.getEnrolledFaces();
            final int facesCount = items.size();
            Face face = null;
            if(facesCount > 0) {
                face = items.get(0);
                mFaceManager.remove(face, mRemoveCallback);
            }
        }

        private void renameFace(int faceId, String newName) {
            mFaceManager.rename(faceId, newName);
            updatePreferences();
        }

        public static class RenameDeleteDialog extends DialogFragment {

            private Face mFace;
            private EditText mDialogTextField;
            private String mFaceName;
            private Boolean mTextHadFocus;
            private int mTextSelectionStart;
            private int mTextSelectionEnd;

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                mFace = getArguments().getParcelable("face");
                if (savedInstanceState != null) {
                    mFaceName = savedInstanceState.getString("faceName");
                    mTextHadFocus = savedInstanceState.getBoolean("textHadFocus");
                    mTextSelectionStart = savedInstanceState.getInt("startSelection");
                    mTextSelectionEnd = savedInstanceState.getInt("endSelection");
                }
                final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                        .setView(R.layout.faceid_rename_dialog)
                        .setPositiveButton(R.string.security_settings_faceid_enroll_dialog_ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        final String newName =
                                                mDialogTextField.getText().toString();
                                        final CharSequence name = mFace.getName();
                                        if (!newName.equals(name)) {
                                            if (DEBUG) {
                                                Log.v(TAG, "rename " + name + " to " + newName);
                                            }
                                            FaceIdSettingsFragment parent = (FaceIdSettingsFragment)
                                                    getTargetFragment();
                                            parent.renameFace(mFace.getFaceId(),newName);
                                        }
                                        dialog.dismiss();
                                    }
                                })
                        .setNegativeButton(
                                R.string.security_settings_faceid_enroll_dialog_delete,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        //onDeleteClick(dialog);
                                    }
                                })
                        .create();
                alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        mDialogTextField = (EditText) alertDialog.findViewById(
                                R.id.faceid_rename_field);
                        CharSequence name = mFaceName == null ? mFace.getName() : mFaceName;
                        mDialogTextField.setText(name);
                        if (mTextHadFocus == null) {
                            mDialogTextField.selectAll();
                        } else {
                            mDialogTextField.setSelection(mTextSelectionStart, mTextSelectionEnd);
                        }
                    }
                });
                if (mTextHadFocus == null || mTextHadFocus) {
                    // Request the IME
                    alertDialog.getWindow().setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
                return alertDialog;
            }

            private void onDeleteClick(DialogInterface dialog) {
                if (DEBUG) Log.v(TAG, "Removing faceId=" + mFace.getFaceId());
                FaceIdSettingsFragment parent = (FaceIdSettingsFragment) getTargetFragment();
                if (parent.mFaceManager.getEnrolledFaces().size() > 1) {
                    parent.deleteFace();
                } else {
                    /*ConfirmLastDeleteDialog lastDeleteDialog = new ConfirmLastDeleteDialog();
                    Bundle args = new Bundle();
                    args.putParcelable("face", mFace);
                    lastDeleteDialog.setArguments(args);
                    lastDeleteDialog.setTargetFragment(getTargetFragment(), 0);
                    lastDeleteDialog.show(getFragmentManager(),
                            ConfirmLastDeleteDialog.class.getName());*/
                }
                dialog.dismiss();
            }

            @Override
            public void onSaveInstanceState(Bundle outState) {
                super.onSaveInstanceState(outState);
                if (mDialogTextField != null) {
                    outState.putString("faceName", mDialogTextField.getText().toString());
                    outState.putBoolean("textHadFocus", mDialogTextField.hasFocus());
                    outState.putInt("startSelection", mDialogTextField.getSelectionStart());
                    outState.putInt("endSelection", mDialogTextField.getSelectionEnd());
                }
            }
        }

        public static class ConfirmLastDeleteDialog extends DialogFragment {

            private Face mFace;

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                mFace = getArguments().getParcelable("face");
                final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.faceid_last_delete_title)
                        .setMessage(R.string.faceid_last_delete_message)
                        .setPositiveButton(R.string.faceid_last_delete_confirm,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        FaceIdSettingsFragment parent = (FaceIdSettingsFragment) getTargetFragment();
                                        if (parent.mFaceManager.getEnrolledFaces().size() >= 1) {
                                            parent.deleteFace();
                                        }
                                        dialog.dismiss();
                                    }
                                })
                        .setNegativeButton(
                                R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                })
                        .create();
                return alertDialog;
            }
        }
    }

    public static class FaceIdPreference extends Preference {
        private Face mFace;
        private View mView;

        public FaceIdPreference(Context context, AttributeSet attrs, int defStyleAttr,
                int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
        }
        public FaceIdPreference(Context context, AttributeSet attrs, int defStyleAttr) {
            this(context, attrs, defStyleAttr, 0);
        }

        public FaceIdPreference(Context context, AttributeSet attrs) {
            this(context, attrs, com.android.internal.R.attr.preferenceStyle);
        }

        public FaceIdPreference(Context context) {
            this(context, null);
        }

        public View getView() { return mView; }

        public void setFace(Face item) {
            mFace = item;
        }

        public Face getFace() {
            return mFace;
        }

        protected void onBindView(View view) {
            mView = view;
        }
    };
}
