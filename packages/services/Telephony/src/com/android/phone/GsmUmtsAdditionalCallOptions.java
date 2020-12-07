package com.android.phone;

import android.app.ActionBar;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.MenuItem;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.GsmCdmaPhoneEx;

import java.util.ArrayList;

public class GsmUmtsAdditionalCallOptions extends TimeConsumingPreferenceActivity {
    private static final String LOG_TAG = "GsmUmtsAdditionalCallOptions";
    private final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private static final String BUTTON_CLIR_KEY  = "button_clir_key";
    private static final String BUTTON_CW_KEY    = "button_cw_key";

    private CLIRListPreference mCLIRButton;
    private CallWaitingSwitchPreference mCWButton;

    private final ArrayList<Preference> mPreferences = new ArrayList<Preference>();
    private int mInitIndex = 0;
    private Phone mPhone;
    private SubscriptionInfoHelper mSubscriptionInfoHelper;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.gsm_umts_additional_options);

        mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mSubscriptionInfoHelper.setActionBarTitle(
                getActionBar(), getResources(), R.string.additional_gsm_call_settings_with_label);
        mPhone = mSubscriptionInfoHelper.getPhone();

        PreferenceScreen prefSet = getPreferenceScreen();
        mCLIRButton = (CLIRListPreference) prefSet.findPreference(BUTTON_CLIR_KEY);
        mCWButton = (CallWaitingSwitchPreference) prefSet.findPreference(BUTTON_CW_KEY);

        mPreferences.add(mCLIRButton);
        mPreferences.add(mCWButton);

        if (icicle == null) {
            if (DBG) Log.d(LOG_TAG, "start to init ");
            if (mPreferences.size() > 1) {
                setSuppServiceFlag(GsmCdmaPhoneEx.SS_FLAG_DES_START);
            }
            mCLIRButton.init(this, false, mPhone);
        } else {
            if (DBG) Log.d(LOG_TAG, "restore stored states");
            mInitIndex = mPreferences.size();
            mCLIRButton.init(this, true, mPhone);
            mCWButton.init(this, true, mPhone);
            int[] clirArray = icicle.getIntArray(mCLIRButton.getKey());
            int[] cwArray = icicle.getIntArray(mCWButton.getKey());
            if (clirArray == null && cwArray == null) {
                mInitIndex = 0;
                mCLIRButton.init(this, false, mPhone);
            } else {
                mInitIndex = mPreferences.size();
                if (clirArray == null) {
                    mCLIRButton.init(this, false, mPhone);
                } else {
                    if (DBG) Log.d(LOG_TAG, "onCreate:  clirArray[0]="
                            + clirArray[0] + ", clirArray[1]=" + clirArray[1]);
                    mCLIRButton.handleGetCLIRResult(clirArray);
                }
                if (cwArray == null) {
                    mCWButton.init(this, false, mPhone);
                } else {
                    Log.d(LOG_TAG, "onCreate:  cwArray[0]="
                            + cwArray[0] + ", cwArray[1]=" + cwArray[1]);
                    mCWButton.handleGetCWResult(cwArray);
                }
            }
        }

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mCLIRButton.clirArray != null) {
            outState.putIntArray(mCLIRButton.getKey(), mCLIRButton.clirArray);
        }
        /* UNISOC: add for bug952191 @{ */
        if (mCWButton.mCWArray != null) {
            outState.putIntArray(mCWButton.getKey(), mCWButton.mCWArray);
        }
        /* @} */
    }

    @Override
    public void onFinished(Preference preference, boolean reading) {
        if (mInitIndex < mPreferences.size()-1 && !isFinishing()) {
            mInitIndex++;
            if (mInitIndex == mPreferences.size() - 1) {
                //set ss flag end before query last cf option
                setSuppServiceFlag(GsmCdmaPhoneEx.SS_FLAG_DES_END);
            }
            Preference pref = mPreferences.get(mInitIndex);
            if (pref instanceof CallWaitingSwitchPreference) {
                ((CallWaitingSwitchPreference) pref).init(this, false, mPhone);
            }
        }
        super.onFinished(preference, reading);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            CallFeaturesSetting.goUpToTopLevelSetting(this, mSubscriptionInfoHelper);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /* UNISSOC: add for bug1237955@ */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mInitIndex < mPreferences.size()-1){
            setSuppServiceFlag(GsmCdmaPhoneEx.SS_FLAG_DES_RETURN);
        }
    }

    @Override
    public void setSuppServiceFlag(int startFlag) {

        Log.d(LOG_TAG, "setSuppServiceFlag: startFlag = " + startFlag);
        if (mPhone != null) {
            ((GsmCdmaPhoneEx) mPhone).setSuppServiceFlag(startFlag);
        }
    }/* @} */
}
