package com.sprd.ext.unreadnotifier;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.launcher3.R;
import com.sprd.ext.LogUtils;
import com.sprd.ext.unreadnotifier.calendar.CalendarPreference;
import com.sprd.ext.unreadnotifier.call.DefaultPhonePreference;
import com.sprd.ext.unreadnotifier.email.EmailPreference;
import com.sprd.ext.unreadnotifier.sms.DefaultSmsPreference;

import java.util.Arrays;

public class UnreadActivity extends Activity {
    private static final String TAG = "UnreadSettingsFragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new UnreadSettingsFragment())
                .commit();
    }

    public static class UnreadSettingsFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener,
            AppListPreference.OnPreferenceCheckBoxClickListener{

        private Context mContext;
        private PackageManager mPm;

        public static final String PREF_KEY_MISS_CALL = "pref_missed_call_count";
        public static final String PREF_KEY_UNREAD_SMS = "pref_unread_sms_count";
        public static final String PREF_KEY_UNREAD_EMAIL = "pref_unread_email_count";
        public static final String PREF_KEY_UNREAD_CALENDAR = "pref_unread_calendar_count";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mContext = getActivity();
            mPm = mContext.getPackageManager();

            UnreadInfoManager.getInstance(mContext).createItemIfNeeded();
            addPreferencesFromResource(R.xml.unread_settings_preferences);

            init();
        }

        private void updateUnreadItemInfos(UnreadBaseItem item) {
            if(!item.checkPermission()) {
                return;
            }
            boolean isChecked = item.isPersistChecked();
            ComponentName cn = item.getCurrentComponentName();
            String value = null;
            if(cn != null) {
                value = cn.flattenToShortString();
            }
            if(isChecked) {
                item.mContentObserver.registerContentObserver();
                item.updateUIFromDatabase();
            } else {
                item.mContentObserver.unregisterContentObserver();
                item.setUnreadCount(0);
                UnreadInfoManager.updateUI(mContext, value);
            }
        }

        private void updateComponentUnreadInfos(UnreadBaseItem item) {
            if(!item.checkPermission()) {
                return;
            }
            String oldValue = item.mOldCn;
            String currentValue = item.mCurrentCn;

            if(item.isPersistChecked()) {
                //clear the unread info on the old icon, and update the current icon
                if(!TextUtils.isEmpty(oldValue)) {
                    UnreadInfoManager.updateUI(mContext, oldValue);
                }

                UnreadInfoManager.updateUI(mContext, currentValue);
            }

            //update the old value
            item.mOldCn = currentValue;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference instanceof AppListPreference) {
                UnreadBaseItem item = ((AppListPreference) preference).item;
                item.mCurrentCn = (String)newValue;
                ((AppListPreference) preference).setValue((String)newValue);
                preference.setSummary(((AppListPreference) preference).getEntry());
                updateComponentUnreadInfos(item);
                return false;
            }
            return true;
        }

        @Override
        public void onPreferenceCheckboxClick(Preference preference) {
            String key = preference.getKey();
            if(LogUtils.DEBUG_UNREAD) {
                LogUtils.d(TAG, "onPreferenceCheckboxClick, key is: "+ key);
            }

            UnreadBaseItem item = UnreadInfoManager.getInstance(mContext).getItemByKey(key);

            if (item != null) {
                if (item.isPersistChecked() && !item.checkPermission()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(new String[] {item.mPermission}, item.mType);
                    }
                }
                updateUnreadItemInfos(item);
            }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if(LogUtils.DEBUG_UNREAD) {
                LogUtils.d(TAG, "onRequestPermissionsResult, requestCode: "+requestCode+ ", permissions: "
                        + Arrays.toString(permissions) + ", grantResults:" + Arrays.toString(grantResults));
            }

            UnreadBaseItem item = UnreadInfoManager.getInstance(mContext).getItemByType(requestCode);
            if(item != null) {
                if (grantResults.length == 1) {
                    if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                        Toast.makeText(mContext, item.getUnreadHintString(), Toast.LENGTH_LONG).show();
                    } else {
                        item.mContentObserver.registerContentObserver();
                        item.updateUIFromDatabase();
                    }
                } else {
                    LogUtils.e(TAG, "grantResult length error.");
                }
            }
        }

        private void init() {
            DefaultPhonePreference defaultPhonePref = (DefaultPhonePreference) findPreference(PREF_KEY_MISS_CALL);
            initPref(defaultPhonePref, R.string.pref_missed_call_count_summary);

            DefaultSmsPreference defaultSmsPref = (DefaultSmsPreference) findPreference(PREF_KEY_UNREAD_SMS);
            initPref(defaultSmsPref, R.string.pref_unread_sms_count_summary);

            EmailPreference emailPref = (EmailPreference) findPreference(PREF_KEY_UNREAD_EMAIL);
            initPref(emailPref, R.string.pref_unread_email_count_summary);

            CalendarPreference calendarPref = (CalendarPreference) findPreference(PREF_KEY_UNREAD_CALENDAR);
            initPref(calendarPref, R.string.pref_unread_calendar_count_summary);
        }

        private boolean hasValidSelectItem(AppListPreference pref) {
            return pref != null && pref.item != null && pref.item.mInstalledList != null
                    && !pref.item.mInstalledList.isEmpty();
        }

        private void initPref(Preference pref, int defaultSummaryID) {
            if (pref != null) {
                if (pref instanceof AppListPreference) {
                    AppListPreference appListPref = (AppListPreference)pref;
                    if (hasValidSelectItem(appListPref)) {
                        appListPref.setOnPreferenceCheckBoxClickListener(this);
                        appListPref.setPreferenceChecked(appListPref.item.isPersistChecked());
                        loadPrefsSetting(appListPref, defaultSummaryID);
                    } else {
                        removePref(appListPref);
                        return;
                    }
                }
                pref.setOnPreferenceChangeListener(this);
            }
        }

        private void loadPrefsSetting(AppListPreference preference, int defaultSummaryID) {
            if (preference == null) {
                return;
            }

            boolean ret = false;
            ApplicationInfo info = null;
            try {
                UnreadBaseItem item = preference.item;
                String pkgName = ComponentName.unflattenFromString(item.mCurrentCn).getPackageName();
                item.mOldCn = item.mCurrentCn;
                info = mPm.getApplicationInfo(pkgName, 0);
                ret = info != null;
            } catch (Exception e) {
                LogUtils.e(TAG, "loadPrefsSetting failed, e:" + e);
            }

            preference.setSummary(ret ? info.loadLabel(mPm) : getString(defaultSummaryID));
        }

        private void removePref(Preference pref) {
            if (pref != null) {
                getPreferenceScreen().removePreference(pref);
                LogUtils.e(TAG, "preference: " +pref.getTitle()+ " is null, remove it.");
            }
        }
    }
}
