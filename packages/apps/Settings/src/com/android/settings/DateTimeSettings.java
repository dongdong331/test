/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.SearchIndexableResource;
import android.util.Log;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.dashboard.SummaryLoader;
import com.android.settings.datetime.AutoTimeFormatPreferenceController;
import com.android.settings.datetime.AutoTimePreferenceController;
import com.android.settings.datetime.AutoTimeZonePreferenceController;
import com.android.settings.datetime.DatePreferenceController;
import com.android.settings.datetime.TimeChangeListenerMixin;
import com.android.settings.datetime.TimeFormatPreferenceController;
import com.android.settings.datetime.TimePreferenceController;
import com.android.settings.datetime.TimeZonePreferenceController;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.datetime.ZoneGetter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.android.settings.location.GpsHelper;
import com.android.settings.datetime.SprdAutoTimePreferenceController;

public class DateTimeSettings extends DashboardFragment implements
        TimePreferenceController.TimePreferenceHost, DatePreferenceController.DatePreferenceHost, SprdAutoTimePreferenceController.SprdAutoTimePreferenceHost {

    private static final String TAG = "DateTimeSettings";

    // have we been launched from the setup wizard?
    protected static final String EXTRA_IS_FROM_SUW = "firstRun";
    private static boolean SUPPORT_CMCC = false;

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.DATE_TIME;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        //SPRD: Bug#692739 support GPS automatic update time BEG -->
        int resId = R.xml.date_time_prefs;
        if (Utils.SUPPORT_GNSS) {
            if (SUPPORT_CMCC) {
                resId = R.xml.date_time_prefs_cmcc;
            }
        } else {
            if (SUPPORT_CMCC) {
                resId = R.xml.date_time_prefs_cmcc_no_gps;
            }
        }
        return resId;
        //<-- support GPS automatic update time END
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        SUPPORT_CMCC = GpsHelper.getInstance(context).isSupportCmcc();
        getLifecycle().addObserver(new TimeChangeListenerMixin(context, this));
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        final Activity activity = getActivity();
        final Intent intent = activity.getIntent();
        final boolean isFromSUW = intent.getBooleanExtra(EXTRA_IS_FROM_SUW, false);

        final AutoTimeZonePreferenceController autoTimeZonePreferenceController =
                new AutoTimeZonePreferenceController(
                        activity, this /* UpdateTimeAndDateCallback */, isFromSUW);
        final AutoTimeFormatPreferenceController autoTimeFormatPreferenceController =
                new AutoTimeFormatPreferenceController(
                        activity, this /* UpdateTimeAndDateCallback */);
        controllers.add(autoTimeZonePreferenceController);
        //SPRD: Bug#692739 support GPS automatic update time BEG -->
        boolean isSupportCmcc = GpsHelper.getInstance(context).isSupportCmcc();
        SprdAutoTimePreferenceController sprdAutoTimePreferenceController = null;
        AutoTimePreferenceController autoTimePreferenceController = null;
        if (isSupportCmcc){
            sprdAutoTimePreferenceController = new SprdAutoTimePreferenceController(
                            activity, this /* SprdAutoTimePreferenceHost */,this /* SprdGpsUpdateTimeCallback */);
            controllers.add(sprdAutoTimePreferenceController);
        } else {
            autoTimePreferenceController = new AutoTimePreferenceController(
                            activity, this /* UpdateTimeAndDateCallback */);
            controllers.add(autoTimePreferenceController);
        }
        //<-- support GPS automatic update time END
        controllers.add(autoTimeFormatPreferenceController);

        controllers.add(new TimeFormatPreferenceController(
                activity, this /* UpdateTimeAndDateCallback */, isFromSUW));
        controllers.add(new TimeZonePreferenceController(
                activity, autoTimeZonePreferenceController));
        //SPRD: Bug#692739 support GPS automatic update time BEG -->
        if (isSupportCmcc){
            controllers.add(new TimePreferenceController(
                    activity, this /* UpdateTimeAndDateCallback */, sprdAutoTimePreferenceController));
            controllers.add(new DatePreferenceController(
                    activity, this /* UpdateTimeAndDateCallback */, sprdAutoTimePreferenceController));
        } else {
            controllers.add(new TimePreferenceController(
                    activity, this /* UpdateTimeAndDateCallback */, autoTimePreferenceController));
            controllers.add(new DatePreferenceController(
                    activity, this /* UpdateTimeAndDateCallback */, autoTimePreferenceController));
        }
        //<-- support GPS automatic update time END
        return controllers;
    }

    @Override
    public void updateTimeAndDateDisplay(Context context) {
        updatePreferenceStates();
    }

    //SPRD: Bug#692739 support GPS automatic update time BEG -->
    @Override
    public void updatePreference(Context context) {
        Log.d(TAG, "updatePreferenceBySprdUpdateTime");
        updatePreferenceStates();
    }
    //<-- support GPS automatic update time END

    @Override
    public Dialog onCreateDialog(int id) {
        switch (id) {
            case DatePreferenceController.DIALOG_DATEPICKER:
                return use(DatePreferenceController.class)
                        .buildDatePicker(getActivity());
            case TimePreferenceController.DIALOG_TIMEPICKER:
                return use(TimePreferenceController.class)
                        .buildTimePicker(getActivity());
            //SPRD: Bug#692739 support GPS automatic update time BEG -->
            case SprdAutoTimePreferenceController.DIALOG_AUTO_TIME_GPS_CONFIRM:
                return use(SprdAutoTimePreferenceController.class)
                        .buildGpsConfirm(getActivity());
            //<-- support GPS automatic update time END
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        switch (dialogId) {
            case DatePreferenceController.DIALOG_DATEPICKER:
                return MetricsEvent.DIALOG_DATE_PICKER;
            case TimePreferenceController.DIALOG_TIMEPICKER:
                return MetricsEvent.DIALOG_TIME_PICKER;
            //SPRD: Bug#692739 support GPS automatic update time BEG -->
            case SprdAutoTimePreferenceController.DIALOG_AUTO_TIME_GPS_CONFIRM:
                int value = MetricsEvent.DIALOG_AUTO_TIME_GPS_CONFIRM;
                return MetricsEvent.DIALOG_AUTO_TIME_GPS_CONFIRM;
            //<-- support GPS automatic update time END
            default:
                return 0;
        }
    }

    @Override
    public void showTimePicker() {
        removeDialog(TimePreferenceController.DIALOG_TIMEPICKER);
        showDialog(TimePreferenceController.DIALOG_TIMEPICKER);
    }

    @Override
    public void showDatePicker() {
        showDialog(DatePreferenceController.DIALOG_DATEPICKER);
    }

    //SPRD: Bug#692739 support GPS automatic update time BEG -->
    @Override
    public void showGpsConfirm() {
        removeDialog(SprdAutoTimePreferenceController.DIALOG_AUTO_TIME_GPS_CONFIRM);
        showDialog(SprdAutoTimePreferenceController.DIALOG_AUTO_TIME_GPS_CONFIRM);
    }
    //<-- support GPS automatic update time END

    private static class SummaryProvider implements SummaryLoader.SummaryProvider {

        private final Context mContext;
        private final SummaryLoader mSummaryLoader;

        public SummaryProvider(Context context, SummaryLoader summaryLoader) {
            mContext = context;
            mSummaryLoader = summaryLoader;
        }

        @Override
        public void setListening(boolean listening) {
            if (listening) {
                final Calendar now = Calendar.getInstance();
                mSummaryLoader.setSummary(this, ZoneGetter.getTimeZoneOffsetAndName(mContext,
                        now.getTimeZone(), now.getTime()));
            }
        }
    }

    public static final SummaryLoader.SummaryProviderFactory SUMMARY_PROVIDER_FACTORY
            = new SummaryLoader.SummaryProviderFactory() {
        @Override
        public SummaryLoader.SummaryProvider createSummaryProvider(Activity activity,
                SummaryLoader summaryLoader) {
            return new SummaryProvider(activity, summaryLoader);
        }
    };


    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new DateTimeSearchIndexProvider();

    private static class DateTimeSearchIndexProvider extends BaseSearchIndexProvider {

        @Override
        public List<SearchIndexableResource> getXmlResourcesToIndex(
                Context context, boolean enabled) {
            List<SearchIndexableResource> result = new ArrayList<>();
            // Remove data/time settings from search in demo mode
            if (UserManager.isDeviceInDemoMode(context)) {
                return result;
            }

            SearchIndexableResource sir = new SearchIndexableResource(context);
            sir.xmlResId = R.xml.date_time_prefs;
            result.add(sir);

            return result;
        }
    }
}
