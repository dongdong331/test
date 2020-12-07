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

package com.android.settings.fingerprint;


import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.hardware.fingerprint.Fingerprint;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.annotation.VisibleForTesting;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.PreferenceViewHolder;
import android.text.Annotation;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.SubSettings;
import com.android.settings.Utils;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.password.ChooseLockGeneric;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.android.settings.utils.AnnotationSpan;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.TwoTargetPreference;
import com.android.settingslib.widget.FooterPreference;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.HashMap;
import java.util.List;

/**
 * Settings screen for fingerprints
 */
public class FingerprintSettings extends SubSettings {

    private static final String TAG = "FingerprintSettings";

    /**
     * Used by the choose fingerprint wizard to indicate the wizard is
     * finished, and each activity in the wizard should finish.
     * <p>
     * Previously, each activity in the wizard would finish itself after
     * starting the next activity. However, this leads to broken 'Back'
     * behavior. So, now an activity does not finish itself until it gets this
     * result.
     */
    protected static final int RESULT_FINISHED = RESULT_FIRST_USER;

    /**
     * Used by the enrolling screen during setup wizard to skip over setting up fingerprint, which
     * will be useful if the user accidentally entered this flow.
     */
    protected static final int RESULT_SKIP = RESULT_FIRST_USER + 1;

    /**
     * Like {@link #RESULT_FINISHED} except this one indicates enrollment failed because the
     * device was left idle. This is used to clear the credential token to require the user to
     * re-enter their pin/pattern/password before continuing.
     */
    protected static final int RESULT_TIMEOUT = RESULT_FIRST_USER + 2;

    private static final long LOCKOUT_DURATION = 30000; // time we have to wait for fp to reset, ms

    public static final String ANNOTATION_URL = "url";
    public static final String ANNOTATION_ADMIN_DETAILS = "admin_details";
	
    private static final String KEY_FINGERPRINT_SETTINGS_APPLOCK = "fingerprint_settings_applock";

    private static final int TEXT_MAX_LENGTH = 50;

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, FingerprintSettingsFragment.class.getName());
        return modIntent;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (FingerprintSettingsFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CharSequence msg = getText(R.string.security_settings_fingerprint_preference_title);
        setTitle(msg);
    }

    public static class FingerprintSettingsFragment extends SettingsPreferenceFragment
            implements OnPreferenceChangeListener, FingerprintPreference.OnDeleteClickListener {
        private static final int RESET_HIGHLIGHT_DELAY_MS = 500;

        private static final String TAG = "FingerprintSettings";
        private static final String KEY_FINGERPRINT_ITEM_PREFIX = "key_fingerprint_item";
        private static final String KEY_FINGERPRINT_ADD = "key_fingerprint_add";
        private static final String KEY_FINGERPRINT_ENABLE_KEYGUARD_TOGGLE =
                "fingerprint_enable_keyguard_toggle";
        private static final String KEY_LAUNCHED_CONFIRM = "launched_confirm";

        private static final int MSG_REFRESH_FINGERPRINT_TEMPLATES = 1000;
        private static final int MSG_FINGER_AUTH_SUCCESS = 1001;
        private static final int MSG_FINGER_AUTH_FAIL = 1002;
        private static final int MSG_FINGER_AUTH_ERROR = 1003;
        private static final int MSG_FINGER_AUTH_HELP = 1004;

        private static final int CONFIRM_REQUEST = 101;
        private static final int CHOOSE_LOCK_GENERIC_REQUEST = 102;

        private static final int ADD_FINGERPRINT_REQUEST = 10;

        protected static final boolean DEBUG = true;

        private FingerprintManager mFingerprintManager;
        private boolean mInFingerprintLockout;
        private byte[] mToken;
        private boolean mLaunchedConfirm;
        private Drawable mHighlightDrawable;
        private int mUserId;

        private static final String TAG_AUTHENTICATE_SIDECAR = "authenticate_sidecar";
        private static final String TAG_REMOVAL_SIDECAR = "removal_sidecar";
        private FingerprintAuthenticateSidecar mAuthenticateSidecar;
        private FingerprintRemoveSidecar mRemovalSidecar;
        private HashMap<Integer, String> mFingerprintsRenaming;

        FingerprintAuthenticateSidecar.Listener mAuthenticateListener =
            new FingerprintAuthenticateSidecar.Listener() {
                @Override
                public void onAuthenticationSucceeded(
                        FingerprintManager.AuthenticationResult result) {
                    int fingerId = result.getFingerprint().getFingerId();
                    mHandler.obtainMessage(MSG_FINGER_AUTH_SUCCESS, fingerId, 0).sendToTarget();
                }

                @Override
                public void onAuthenticationFailed() {
                    mHandler.obtainMessage(MSG_FINGER_AUTH_FAIL).sendToTarget();
                }

                @Override
                public void onAuthenticationError(int errMsgId, CharSequence errString) {
                    mHandler.obtainMessage(MSG_FINGER_AUTH_ERROR, errMsgId, 0, errString)
                            .sendToTarget();
                }

                @Override
                public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
                    mHandler.obtainMessage(MSG_FINGER_AUTH_HELP, helpMsgId, 0, helpString)
                            .sendToTarget();
                }
            };

        FingerprintRemoveSidecar.Listener mRemovalListener =
                new FingerprintRemoveSidecar.Listener() {
            public void onRemovalSucceeded(Fingerprint fingerprint) {
                mHandler.obtainMessage(MSG_REFRESH_FINGERPRINT_TEMPLATES,
                        fingerprint.getFingerId(), 0).sendToTarget();
                updateDialog();
            }

            public void onRemovalError(Fingerprint fp, int errMsgId, CharSequence errString) {
                final Activity activity = getActivity();
                if (activity != null) {
                    Toast.makeText(activity, errString, Toast.LENGTH_SHORT);
                }
                updateDialog();
            }

            private void updateDialog() {
                RenameDialog renameDialog = (RenameDialog) getFragmentManager().
                        findFragmentByTag(RenameDialog.class.getName());
                if (renameDialog != null) {
                    renameDialog.enableDelete();
                }
            }
        };

        private final Handler mHandler = new Handler() {
            @Override
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case MSG_REFRESH_FINGERPRINT_TEMPLATES:
                        removeFingerprintPreference(msg.arg1);
                        updateAddPreference();
                        /* SPRD: add fingerprint application lock. @{ */
                        if (SystemProperties.getBoolean("persist.vendor.sprd.fp.lockapp", false)) {
                            updateFingerprintSettingPreference();
                        }
                        /* @} */
                        retryFingerprint();
                    break;
                    case MSG_FINGER_AUTH_SUCCESS:
                        highlightFingerprintItem(msg.arg1);
                        retryFingerprint();
                    break;
                    case MSG_FINGER_AUTH_FAIL:
                        // No action required... fingerprint will allow up to 5 of these
                    break;
                    case MSG_FINGER_AUTH_ERROR:
                        handleError(msg.arg1 /* errMsgId */, (CharSequence) msg.obj /* errStr */ );
                    break;
                    case MSG_FINGER_AUTH_HELP: {
                        // Not used
                    }
                    break;
                }
            }
        };

        /**
         * @param errMsgId
         */
        protected void handleError(int errMsgId, CharSequence msg) {
            switch (errMsgId) {
                case FingerprintManager.FINGERPRINT_ERROR_CANCELED:
                    return; // Only happens if we get preempted by another activity. Ignored.
                case FingerprintManager.FINGERPRINT_ERROR_LOCKOUT:
                    mInFingerprintLockout = true;
                    // We've been locked out.  Reset after 30s.
                    if (!mHandler.hasCallbacks(mFingerprintLockoutReset)) {
                        mHandler.postDelayed(mFingerprintLockoutReset,
                                LOCKOUT_DURATION);
                    }
                    break;
                case FingerprintManager.FINGERPRINT_ERROR_LOCKOUT_PERMANENT:
                    mInFingerprintLockout = true;
                    break;
            }

            if (mInFingerprintLockout) {
                // Activity can be null on a screen rotation.
                final Activity activity = getActivity();
                if (activity != null) {
                    Toast.makeText(activity, msg , Toast.LENGTH_SHORT).show();
                }
            }
            retryFingerprint(); // start again
        }

        private void retryFingerprint() {
            if (mRemovalSidecar.inProgress()
                    || 0 == mFingerprintManager.getEnrolledFingerprints(mUserId).size()) {
                return;
            }
            // Don't start authentication if ChooseLockGeneric is showing, otherwise if the user
            // is in FP lockout, a toast will show on top
            if (mLaunchedConfirm) {
                return;
            }
            if (!mInFingerprintLockout) {
                mAuthenticateSidecar.startAuthentication(mUserId);
                mAuthenticateSidecar.setListener(mAuthenticateListener);
            }
        }

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.FINGERPRINT;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Activity activity = getActivity();
            mFingerprintManager = Utils.getFingerprintManagerOrNull(activity);

            mAuthenticateSidecar = (FingerprintAuthenticateSidecar)
                    getFragmentManager().findFragmentByTag(TAG_AUTHENTICATE_SIDECAR);
            if (mAuthenticateSidecar == null) {
                mAuthenticateSidecar = new FingerprintAuthenticateSidecar();
                getFragmentManager().beginTransaction()
                        .add(mAuthenticateSidecar, TAG_AUTHENTICATE_SIDECAR).commit();
            }
            mAuthenticateSidecar.setFingerprintManager(mFingerprintManager);

            mRemovalSidecar = (FingerprintRemoveSidecar)
                    getFragmentManager().findFragmentByTag(TAG_REMOVAL_SIDECAR);
            if (mRemovalSidecar == null) {
                mRemovalSidecar = new FingerprintRemoveSidecar();
                getFragmentManager().beginTransaction()
                        .add(mRemovalSidecar, TAG_REMOVAL_SIDECAR).commit();
            }
            mRemovalSidecar.setFingerprintManager(mFingerprintManager);
            mRemovalSidecar.setListener(mRemovalListener);

            RenameDialog renameDialog = (RenameDialog) getFragmentManager().
                    findFragmentByTag(RenameDialog.class.getName());
            if (renameDialog != null) {
                renameDialog.setDeleteInProgress(mRemovalSidecar.inProgress());
            }

            mFingerprintsRenaming = new HashMap<Integer, String>();

            if (savedInstanceState != null) {
                mFingerprintsRenaming = (HashMap<Integer, String>)
                        savedInstanceState.getSerializable("mFingerprintsRenaming");
                mToken = savedInstanceState.getByteArray(
                        ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
                mLaunchedConfirm = savedInstanceState.getBoolean(
                        KEY_LAUNCHED_CONFIRM, false);
            }
            mUserId = getActivity().getIntent().getIntExtra(
                    Intent.EXTRA_USER_ID, UserHandle.myUserId());

            // Need to authenticate a session token if none
            if (mToken == null && mLaunchedConfirm == false) {
                mLaunchedConfirm = true;
                launchChooseOrConfirmLock();
            }

            final FooterPreference pref = mFooterPreferenceMixin.createFooterPreference();
            final EnforcedAdmin admin = RestrictedLockUtils.checkIfKeyguardFeaturesDisabled(
                    activity, DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT, mUserId);
            final AnnotationSpan.LinkInfo adminLinkInfo = new AnnotationSpan.LinkInfo(
                    ANNOTATION_ADMIN_DETAILS, (view) -> {
                RestrictedLockUtils.sendShowAdminSupportDetailsIntent(activity, admin);
            });
            final Intent helpIntent = HelpUtils.getHelpIntent(
                    activity, getString(getHelpResource()), activity.getClass().getName());
            CharSequence rawText = getText(R.string.security_settings_fingerprint_enroll_disclaimer);
            CharSequence adminText = getText(R.string.security_settings_fingerprint_enroll_disclaimer_lockscreen_disabled);
            SpannableString msg = new SpannableString(rawText);
            Annotation[] spans = msg.getSpans(0, msg.length(), Annotation.class);
            int start = msg.getSpanStart(spans[0]);
            if(helpIntent == null){
                rawText = rawText.toString().substring(0, start);
            }
            final AnnotationSpan.LinkInfo linkInfo = new AnnotationSpan.LinkInfo(
                    activity, ANNOTATION_URL, helpIntent);
            pref.setTitle(AnnotationSpan.linkify(admin != null
                            ? adminText
                            : rawText,
                    linkInfo, adminLinkInfo));
        }

        protected void removeFingerprintPreference(int fingerprintId) {
            String name = genKey(fingerprintId);
            Preference prefToRemove = findPreference(name);
            if (prefToRemove != null) {
                if (!getPreferenceScreen().removePreference(prefToRemove)) {
                    Log.w(TAG, "Failed to remove preference with key " + name);
                }
            } else {
                Log.w(TAG, "Can't find preference to remove: " + name);
            }
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
            addPreferencesFromResource(R.xml.security_settings_fingerprint);
            root = getPreferenceScreen();
            addFingerprintItemPreferences(root);
            setPreferenceScreen(root);
            return root;
        }

        private void addFingerprintItemPreferences(PreferenceGroup root) {
            root.removeAll();
            final List<Fingerprint> items = mFingerprintManager.getEnrolledFingerprints(mUserId);
            final int fingerprintCount = items.size();
            for (int i = 0; i < fingerprintCount; i++) {
                final Fingerprint item = items.get(i);
                FingerprintPreference pref = new FingerprintPreference(root.getContext(),
                        this /* onDeleteClickListener */);
                pref.setKey(genKey(item.getFingerId()));
                pref.setTitle(item.getName());
                pref.setFingerprint(item);
                pref.setPersistent(false);
                pref.setIcon(R.drawable.ic_fingerprint_24dp);
                if (mRemovalSidecar.isRemovingFingerprint(item.getFingerId())) {
                    pref.setEnabled(false);
                }
                if (mFingerprintsRenaming.containsKey(item.getFingerId())) {
                    pref.setTitle(mFingerprintsRenaming.get(item.getFingerId()));
                }
                root.addPreference(pref);
                pref.setOnPreferenceChangeListener(this);
            }
            Preference addPreference = new Preference(root.getContext());
            addPreference.setKey(KEY_FINGERPRINT_ADD);
            addPreference.setTitle(R.string.fingerprint_add_title);
            addPreference.setIcon(R.drawable.ic_menu_add);
            root.addPreference(addPreference);
            addPreference.setOnPreferenceChangeListener(this);
            updateAddPreference();
            /* SPRD: add fingerprint application lock. @{ */
            if (SystemProperties.getBoolean("persist.vendor.sprd.fp.lockapp", false)
                    && ActivityManager.getCurrentUser() == UserHandle.USER_OWNER) {
                addFingerprintSettingPreference(root);
                updateFingerprintSettingPreference();
            }
            /* @} */
        }

        private void updateAddPreference() {
            if (getActivity() == null) return; // Activity went away

            /* Disable preference if too many fingerprints added */
            final int max = getContext().getResources().getInteger(
                    com.android.internal.R.integer.config_fingerprintMaxTemplatesPerUser);
            boolean tooMany = mFingerprintManager.getEnrolledFingerprints(mUserId).size() >= max;
            // retryFingerprint() will be called when remove finishes
            // need to disable enroll or have a way to determine if enroll is in progress
            final boolean removalInProgress = mRemovalSidecar.inProgress();
            CharSequence maxSummary = tooMany ?
                    getContext().getString(R.string.fingerprint_add_max, max) : "";
            Preference addPreference = findPreference(KEY_FINGERPRINT_ADD);
            addPreference.setSummary(maxSummary);
            addPreference.setEnabled(!tooMany && !removalInProgress);
        }
        /* SPRD: add fingerprint application lock. @{ */
        private void updateFingerprintSettingPreference() {
            boolean hasFinger = mFingerprintManager.getEnrolledFingerprints().size() > 0;
            Preference fingerprintSettingsPref = findPreference(KEY_FINGERPRINT_SETTINGS_APPLOCK);
            if (fingerprintSettingsPref != null) {
                if (!hasFinger) {
                    if (!getPreferenceScreen().removePreference(fingerprintSettingsPref)) {
                        Log.w(TAG, "Failed to remove fingerprint setting preference");
                    }
                }
            } else {
                Log.w(TAG, "Can't find fingerprint setting preference to remove");
            }
        }

        private void addFingerprintSettingPreference(PreferenceGroup root) {
            Preference settingPref = new Preference(root.getContext());
            settingPref.setKey(KEY_FINGERPRINT_SETTINGS_APPLOCK);
            settingPref.setTitle(R.string.fingerprint_settings_applock);
            settingPref.setIcon(R.drawable.ic_settings_security);
            root.addPreference(settingPref);
            settingPref.setOnPreferenceChangeListener(this);
        }
        /* @} */

        private static String genKey(int id) {
            return KEY_FINGERPRINT_ITEM_PREFIX + "_" + id;
        }

        @Override
        public void onResume() {
            super.onResume();
            mInFingerprintLockout = false;
            // Make sure we reload the preference hierarchy since fingerprints may be added,
            // deleted or renamed.
            updatePreferences();
            if (mRemovalSidecar != null) {
                mRemovalSidecar.setListener(mRemovalListener);
            }
        }

        private void updatePreferences() {
            createPreferenceHierarchy();
            retryFingerprint();
        }

        @Override
        public void onPause() {
            super.onPause();
            if (mRemovalSidecar != null) {
                mRemovalSidecar.setListener(null);
            }
            if (mAuthenticateSidecar != null) {
                mAuthenticateSidecar.setListener(null);
                mAuthenticateSidecar.stopAuthentication();
            }
        }

        @Override
        public void onSaveInstanceState(final Bundle outState) {
            outState.putByteArray(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN,
                    mToken);
            outState.putBoolean(KEY_LAUNCHED_CONFIRM, mLaunchedConfirm);
            outState.putSerializable("mFingerprintsRenaming", mFingerprintsRenaming);
        }

        @Override
        public boolean onPreferenceTreeClick(Preference pref) {
            final String key = pref.getKey();
            if (KEY_FINGERPRINT_ADD.equals(key)) {
                Intent intent = new Intent();
                intent.setClassName("com.android.settings",
                        FingerprintEnrollEnrolling.class.getName());
                intent.putExtra(Intent.EXTRA_USER_ID, mUserId);
                intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, mToken);
                startActivityForResult(intent, ADD_FINGERPRINT_REQUEST);
            } else if (pref instanceof FingerprintPreference) {
                FingerprintPreference fpref = (FingerprintPreference) pref;
                final Fingerprint fp = fpref.getFingerprint();
                showRenameDialog(fp);
            }
            /* SPRD: add fingerprint application lock. @{ */
            else if (KEY_FINGERPRINT_SETTINGS_APPLOCK.equals(key)) {
                Log.d(TAG, "onPreferenceTreeClick LockApplication...");
                Intent intent = new Intent();
                intent.setClassName("com.sprd.applock", "com.sprd.applock.AppLockListActivity");
                startActivity(intent);
            }
            /* @} */
            return super.onPreferenceTreeClick(pref);
        }

        @Override
        public void onDeleteClick(FingerprintPreference p) {
            final boolean hasMultipleFingerprint =
                    mFingerprintManager.getEnrolledFingerprints(mUserId).size() > 1;
            final Fingerprint fp = p.getFingerprint();

            if (hasMultipleFingerprint) {
                if (mRemovalSidecar.inProgress()) {
                    Log.d(TAG, "Fingerprint delete in progress, skipping");
                    return;
                }
                DeleteFingerprintDialog.newInstance(fp, this /* target */)
                        .show(getFragmentManager(), DeleteFingerprintDialog.class.getName());
            } else {
                ConfirmLastDeleteDialog lastDeleteDialog = new ConfirmLastDeleteDialog();
                final boolean isProfileChallengeUser =
                        UserManager.get(getContext()).isManagedProfile(mUserId);
                final Bundle args = new Bundle();
                args.putParcelable("fingerprint", fp);
                args.putBoolean("isProfileChallengeUser", isProfileChallengeUser);
                lastDeleteDialog.setArguments(args);
                lastDeleteDialog.setTargetFragment(this, 0);
                lastDeleteDialog.show(getFragmentManager(),
                        ConfirmLastDeleteDialog.class.getName());
            }
        }

        private void showRenameDialog(final Fingerprint fp) {
            RenameDialog renameDialog = new RenameDialog();
            Bundle args = new Bundle();
            if (mFingerprintsRenaming.containsKey(fp.getFingerId())) {
                final Fingerprint f = new Fingerprint(mFingerprintsRenaming.get(fp.getFingerId()),
                        fp.getGroupId(), fp.getFingerId(), fp.getDeviceId());
                args.putParcelable("fingerprint", f);
            } else {
                args.putParcelable("fingerprint", fp);
            }
            renameDialog.setDeleteInProgress(mRemovalSidecar.inProgress());
            renameDialog.setArguments(args);
            renameDialog.setTargetFragment(this, 0);
            renameDialog.show(getFragmentManager(), RenameDialog.class.getName());
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            boolean result = true;
            final String key = preference.getKey();
            if (KEY_FINGERPRINT_ENABLE_KEYGUARD_TOGGLE.equals(key)) {
                // TODO
            } else {
                Log.v(TAG, "Unknown key:" + key);
            }
            return result;
        }

        @Override
        public int getHelpResource() {
            return R.string.help_url_fingerprint;
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == CHOOSE_LOCK_GENERIC_REQUEST
                    || requestCode == CONFIRM_REQUEST) {
                mLaunchedConfirm = false;
                if (resultCode == RESULT_FINISHED || resultCode == RESULT_OK) {
                    // The lock pin/pattern/password was set. Start enrolling!
                    if (data != null) {
                        mToken = data.getByteArrayExtra(
                                ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
                    }
                }
            } else if (requestCode == ADD_FINGERPRINT_REQUEST) {
                if (resultCode == RESULT_TIMEOUT) {
                    Activity activity = getActivity();
                    activity.setResult(RESULT_TIMEOUT);
                    activity.finish();
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
                int result = mFingerprintManager.postEnroll();
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

        private void highlightFingerprintItem(int fpId) {
            String prefName = genKey(fpId);
            FingerprintPreference fpref = (FingerprintPreference) findPreference(prefName);
            final Drawable highlight = getHighlightDrawable();
            if (highlight != null && fpref != null && fpref.getView() != null) {
                final View view = fpref.getView();
                final int centerX = view.getWidth() / 2;
                final int centerY = view.getHeight() / 2;
                highlight.setHotspot(centerX, centerY);
                view.setBackground(highlight);
                view.setPressed(true);
                view.setPressed(false);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        view.setBackground(null);
                    }
                }, RESET_HIGHLIGHT_DELAY_MS);
            }
        }

        private void launchChooseOrConfirmLock() {
            Intent intent = new Intent();
            long challenge = mFingerprintManager.preEnroll();
            ChooseLockSettingsHelper helper = new ChooseLockSettingsHelper(getActivity(), this);
            if (!helper.launchConfirmationActivity(CONFIRM_REQUEST,
                    getString(R.string.security_settings_fingerprint_preference_title),
                    null, null, challenge, mUserId)) {
                intent.setClassName("com.android.settings", ChooseLockGeneric.class.getName());
                intent.putExtra(ChooseLockGeneric.ChooseLockGenericFragment.MINIMUM_QUALITY_KEY,
                        DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
                intent.putExtra(ChooseLockGeneric.ChooseLockGenericFragment.HIDE_DISABLED_PREFS,
                        true);
                intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, true);
                intent.putExtra(Intent.EXTRA_USER_ID, mUserId);
                intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, challenge);
                intent.putExtra(Intent.EXTRA_USER_ID, mUserId);
                startActivityForResult(intent, CHOOSE_LOCK_GENERIC_REQUEST);
            }
        }

        @VisibleForTesting
        void deleteFingerPrint(Fingerprint fingerPrint) {
            mRemovalSidecar.startRemove(fingerPrint, mUserId);
            String name = genKey(fingerPrint.getFingerId());
            Preference prefToRemove = findPreference(name);
            prefToRemove.setEnabled(false);
            updateAddPreference();
        }

        private void renameFingerPrint(int fingerId, String newName) {
            mFingerprintManager.rename(fingerId, mUserId, newName);
            if (!TextUtils.isEmpty(newName)) {
                mFingerprintsRenaming.put(fingerId, newName);
            }
            updatePreferences();
        }

        private final Runnable mFingerprintLockoutReset = new Runnable() {
            @Override
            public void run() {
                mInFingerprintLockout = false;
                retryFingerprint();
            }
        };

        public static class DeleteFingerprintDialog extends InstrumentedDialogFragment
                implements DialogInterface.OnClickListener {

            private static final String KEY_FINGERPRINT = "fingerprint";
            private Fingerprint mFp;
            private AlertDialog mAlertDialog;

            public static DeleteFingerprintDialog newInstance(Fingerprint fp,
                    FingerprintSettingsFragment target) {
                final DeleteFingerprintDialog dialog = new DeleteFingerprintDialog();
                final Bundle bundle = new Bundle();
                bundle.putParcelable(KEY_FINGERPRINT, fp);
                dialog.setArguments(bundle);
                dialog.setTargetFragment(target, 0 /* requestCode */);
                return dialog;
            }

            @Override
            public int getMetricsCategory() {
                return MetricsEvent.DIALOG_FINGERPINT_EDIT;
            }

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                mFp = getArguments().getParcelable(KEY_FINGERPRINT);
                final String title = getString(R.string.fingerprint_delete_title, mFp.getName());

                mAlertDialog = new AlertDialog.Builder(getActivity())
                        .setTitle(title)
                        .setMessage(R.string.fingerprint_delete_message)
                        .setPositiveButton(
                                R.string.security_settings_fingerprint_enroll_dialog_delete,
                                this /* onClickListener */)
                        .setNegativeButton(R.string.cancel, null /* onClickListener */)
                        .create();
                return mAlertDialog;
            }

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    final int fingerprintId = mFp.getFingerId();
                    Log.v(TAG, "Removing fpId=" + fingerprintId);
                    mMetricsFeatureProvider.action(getContext(),
                            MetricsEvent.ACTION_FINGERPRINT_DELETE,
                            fingerprintId);
                    FingerprintSettingsFragment parent
                            = (FingerprintSettingsFragment) getTargetFragment();
                    parent.deleteFingerPrint(mFp);
                }
            }
        }

        public static class RenameDialog extends InstrumentedDialogFragment {

            private Fingerprint mFp;
            private EditText mDialogTextField;
            private String mFingerName;
            private Boolean mTextHadFocus;
            private int mTextSelectionStart;
            private int mTextSelectionEnd;
            private AlertDialog mAlertDialog;
            private boolean mDeleteInProgress;
            private Button positiveButton;
            private static final int MSG_FINGER_RENAME_FILTER_UPDATE = 2000;

            private Handler renameHandler = new Handler() {
                @Override
                public void handleMessage(android.os.Message msg) {
                    switch (msg.what) {
                        case MSG_FINGER_RENAME_FILTER_UPDATE:
                            String name = (String) msg.obj;
                            int index = msg.arg1;
                            mDialogTextField.setText(name);
                            mDialogTextField.setSelection(index);
                        break;
                    }
                };
            };

            public static String stringFilter(String str) throws PatternSyntaxException {
                String regEx = "[\uDBEC-\uDFF4]";
                Pattern p = Pattern.compile(regEx);
                Matcher m = p.matcher(str);
                return m.replaceAll("");
            }

            public void setDeleteInProgress(boolean deleteInProgress) {
                mDeleteInProgress = deleteInProgress;
            }

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                mFp = getArguments().getParcelable("fingerprint");
                if (savedInstanceState != null) {
                    mFingerName = savedInstanceState.getString("fingerName");
                    mTextHadFocus = savedInstanceState.getBoolean("textHadFocus");
                    mTextSelectionStart = savedInstanceState.getInt("startSelection");
                    mTextSelectionEnd = savedInstanceState.getInt("endSelection");
                }
                mAlertDialog = new AlertDialog.Builder(getActivity())
                        .setView(R.layout.fingerprint_rename_dialog)
                        .setPositiveButton(R.string.security_settings_fingerprint_enroll_dialog_ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        final String newName =
                                                mDialogTextField.getText().toString();
                                        final CharSequence name = mFp.getName();
                                        if (!TextUtils.equals(newName, name)) {
                                            Log.d(TAG, "rename " + name + " to " + newName);
                                            mMetricsFeatureProvider.action(getContext(),
                                                    MetricsEvent.ACTION_FINGERPRINT_RENAME,
                                                    mFp.getFingerId());
                                            FingerprintSettingsFragment parent
                                                    = (FingerprintSettingsFragment)
                                                    getTargetFragment();
                                            parent.renameFingerPrint(mFp.getFingerId(),
                                                    newName);
                                        }
                                        dialog.dismiss();
                                    }
                                })
                        // Added for fingerprint launch app begin
                        .setNeutralButton(
                                R.string.accessibility_menu_item_settings,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent intent = new Intent();
                                        if(SystemProperties.getBoolean("persist.vendor.sprd.fp.launchapp", false)){
                                            intent.setClassName("com.sprd.applock",
                                                "com.sprd.launchapp.LaunchAppActivity");
                                        }
                                        Bundle args = new Bundle();
                                        args.putParcelable("fingerprint", mFp);
                                        intent.putExtras(args);
                                        startActivity(intent);
                                    }
                                })
                        // Added for fingerprint launch app end
                        .create();
                mAlertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        mDialogTextField = (EditText) mAlertDialog.findViewById(
                                R.id.fingerprint_rename_field);
                        mDialogTextField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TEXT_MAX_LENGTH)});
                        CharSequence name = mFingerName == null ? mFp.getName() : mFingerName;
                        mDialogTextField.setText(name);
                        // Added for fingerprint screen switch , button state changed begin for bug734107
                        if(positiveButton == null) {
                           positiveButton = mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                        }
                        if(TextUtils.isEmpty(name.toString())) {
                           positiveButton.setEnabled(false);
                        } else {
                           positiveButton.setEnabled(true);
                        }
                        // Added for fingerprint screen switch , button state changed end for bug734107
                        // Added for fingerprint launch app begin
                        Button neutralButton = mAlertDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                        if (neutralButton != null && (!SystemProperties.getBoolean("persist.vendor.sprd.fp.launchapp", false))
                                || !(ActivityManager.getCurrentUser() == UserHandle.USER_OWNER)) {
                            neutralButton.setVisibility(View.GONE);
                        }
                        // Added for fingerprint launch app end
                        //add by sprd for bug667200 begin
                        mDialogTextField.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void onTextChanged(CharSequence s, int start, int before,
                                    int count) {
                                String name = mDialogTextField.getText().toString();
                                String filterName = stringFilter(name);
                                if (filterName != null && !name.equals(filterName)) {
                                    char[] nameArray = name.toCharArray();
                                    char[] filterNameArray = filterName.toCharArray();
                                    int index;
                                    for (index = 0; index < filterNameArray.length; index++) {
                                        if (filterNameArray[index] != nameArray[index]) {
                                            break;
                                        }
                                    }
                                    renameHandler.obtainMessage(MSG_FINGER_RENAME_FILTER_UPDATE,
                                            index, 0, filterName).sendToTarget();
                                }
                            }

                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count,
                                    int after) {
                            }

                            @Override
                            public void afterTextChanged(Editable s) {
                                if(positiveButton == null) {
                                    positiveButton = mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                                }
                                String content = s.toString();
                                if(TextUtils.isEmpty(content)) {
                                    positiveButton.setEnabled(false);
                                } else {
                                    positiveButton.setEnabled(true);
                                }
                            }
                        });
                         //add by sprd for bug667200 end
                        if (mTextHadFocus == null) {
                            mDialogTextField.selectAll();
                        } else {
                            mDialogTextField.setSelection(mTextSelectionStart, mTextSelectionEnd);
                        }
                        if (mDeleteInProgress) {
                            mAlertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                        }
                        mDialogTextField.requestFocus();
                    }
                });
                if (mTextHadFocus == null || mTextHadFocus) {
                    // Request the IME
                    mAlertDialog.getWindow().setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
                return mAlertDialog;
            }

            public void enableDelete() {
                mDeleteInProgress = false;
                if (mAlertDialog != null) {
                    mAlertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                }
            }

            @Override
            public void onSaveInstanceState(Bundle outState) {
                super.onSaveInstanceState(outState);
                if (mDialogTextField != null) {
                    outState.putString("fingerName", mDialogTextField.getText().toString());
                    outState.putBoolean("textHadFocus", mDialogTextField.hasFocus());
                    outState.putInt("startSelection", mDialogTextField.getSelectionStart());
                    outState.putInt("endSelection", mDialogTextField.getSelectionEnd());
                }
            }

            @Override
            public int getMetricsCategory() {
                return MetricsEvent.DIALOG_FINGERPINT_EDIT;
            }
        }

        public static class ConfirmLastDeleteDialog extends InstrumentedDialogFragment {

            private Fingerprint mFp;

            @Override
            public int getMetricsCategory() {
                return MetricsEvent.DIALOG_FINGERPINT_DELETE_LAST;
            }

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                mFp = getArguments().getParcelable("fingerprint");
                final boolean isProfileChallengeUser =
                        getArguments().getBoolean("isProfileChallengeUser");
                final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.fingerprint_last_delete_title)
                        .setMessage((isProfileChallengeUser)
                                ? R.string.fingerprint_last_delete_message_profile_challenge
                                : R.string.fingerprint_last_delete_message)
                        .setPositiveButton(R.string.fingerprint_last_delete_confirm,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        FingerprintSettingsFragment parent
                                                = (FingerprintSettingsFragment) getTargetFragment();
                                        parent.deleteFingerPrint(mFp);
                                        /* SPRD: add fingerprint application lock. @{ */
                                        if (SystemProperties.getBoolean(
                                                "persist.vendor.sprd.fp.lockapp", false)) {
                                            Intent intent = new Intent();
                                            intent.setAction("com.sprd.fp.Settings.DISABLE_FINGERPRINT");
                                            intent.setPackage("com.sprd.applock");
                                            parent.getActivity().sendBroadcastAsUser(intent, UserHandle.CURRENT);
                                        }
                                        /* @} */
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

    public static class FingerprintPreference extends TwoTargetPreference {

        private final OnDeleteClickListener mOnDeleteClickListener;

        private Fingerprint mFingerprint;
        private View mView;
        private View mDeleteView;

        public interface OnDeleteClickListener {
            void onDeleteClick(FingerprintPreference p);
        }

        public FingerprintPreference(Context context, OnDeleteClickListener onDeleteClickListener) {
            super(context);
            mOnDeleteClickListener = onDeleteClickListener;
        }

        public View getView() {
            return mView;
        }

        public void setFingerprint(Fingerprint item) {
            mFingerprint = item;
        }

        public Fingerprint getFingerprint() {
            return mFingerprint;
        }

        @Override
        protected int getSecondTargetResId() {
            return R.layout.preference_widget_delete;
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder view) {
            super.onBindViewHolder(view);
            mView = view.itemView;
            mDeleteView = view.itemView.findViewById(R.id.delete_button);
            mDeleteView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mOnDeleteClickListener != null) {
                        mOnDeleteClickListener.onDeleteClick(FingerprintPreference.this);
                    }
                }
            });
        }
    }
}
