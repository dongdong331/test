package com.android.phone;

import com.android.ims.ImsManager;
import com.android.ims.internal.IImsRegisterListener;
import com.android.ims.internal.IImsServiceEx;
import com.android.ims.internal.ImsManagerEx;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.GsmCdmaPhoneEx;
import com.unisoc.phone.callsettings.callforward.CallForwardTimeEditPreFragement;
import com.unisoc.phone.callsettings.callforward.GsmUmtsAllCallForwardOptions;

import android.app.ActionBar;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManagerEx;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.telephony.RadioAccessFamily;
import android.util.Log;
import android.view.MenuItem;
import com.unisoc.phone.callsettings.ActivityContainer;
import java.util.ArrayList;

public class GsmUmtsCallForwardOptions extends TimeConsumingPreferenceActivity
                                   implements CallForwardTimeEditPreFragement.Listener {
    private static final String LOG_TAG = "GsmUmtsCallForwardOptions";

    private static final String NUM_PROJECTION[] = {
        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
    };

    private static final String BUTTON_CFU_KEY   = "button_cfu_key";
    private static final String BUTTON_CFB_KEY   = "button_cfb_key";
    private static final String BUTTON_CFNRY_KEY = "button_cfnry_key";
    private static final String BUTTON_CFNRC_KEY = "button_cfnrc_key";

    private static final String KEY_TOGGLE = "toggle";
    private static final String KEY_STATUS = "status";
    private static final String KEY_NUMBER = "number";

    private CallForwardEditPreference mButtonCFU;
    private CallForwardEditPreference mButtonCFB;
    private CallForwardEditPreference mButtonCFNRy;
    private CallForwardEditPreference mButtonCFNRc;

    private final ArrayList<CallForwardEditPreference> mPreferences =
            new ArrayList<CallForwardEditPreference> ();
    private int mInitIndex= 0;

    private boolean mFirstResume;
    private Bundle mIcicle;
    private Phone mPhone;
    private SubscriptionInfoHelper mSubscriptionInfoHelper;
    private boolean mReplaceInvalidCFNumbers;
    /* UNISOC: add for FEATURE_VIDEO_CALL_FOR @{ */
    private static final String KEY_REASON = "reason";
    private static final String KEY_SERVICECLASS = "serviceclass";
    private static final String KEY_HASINIT = "hasinit";
    private static final String PROGRESS_DIALOG_SHOWING = "progress_dialog_showing";
    private static final int ALL_CALL_FORWARD_INDEX = 0;
    private static final int[] SUB_CF_ITEM_ORDER = {CommandsInterface.CF_REASON_UNCONDITIONAL,
            CommandsInterface.CF_REASON_BUSY,
            CommandsInterface.CF_REASON_NO_REPLY,
            CommandsInterface.CF_REASON_NOT_REACHABLE};
    private boolean mHasInit = false;
    private boolean mIsVolteEnable;
    private boolean mIsImsListenerRegistered;
    private IImsServiceEx mIImsServiceEx;
    private Context mContext;
    private ImsManager mImsManager;
    /* @} */
    /* UNISOC: add for feature 888845 @{ */
    private int mServiceClass;
    private static final int NOT_SPECAIL_SERVICECLASS = -1;
    /* @} */
    /* UNISOC: add for feature 884921 durationforward @{ */
    private static final String PREF_PREFIX_TIME = "phonecalltimeforward_";
    private static final String BUTTON_CFT_KEY = "button_cft_key";
    private Preference mButtonCFT;
    SharedPreferences mTimePrefs;
    private boolean mSupportDurationForward;
    TimeConsumingPreferenceListener tcpListener;
    private static final int CFU_PREF_REASON = 0;
    private static final String CFT_STATUS_ACTIVE = "1";
    private boolean mIsFDNOn;
    /* @} */
    // UNISOC: modify by BUG 916869
    private ActivityContainer mActivityContainer;
    //SPRD: add for bug 937030
    private static boolean mEnterMultiWindow = false;
    //UNISOC: add for bug1013340
    private Dialog mProgressDialog;
    private static final int BUSY_READING_DIALOG = 100;
    private static final int BUSY_SAVING_DIALOG = 200;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.callforward_options);

        mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mSubscriptionInfoHelper.setActionBarTitle(
                getActionBar(), getResources(), R.string.call_forwarding_settings_with_label);
        mPhone = mSubscriptionInfoHelper.getPhone();
        /* UNISOC: add for bug 943401 @{ */
        if(mPhone == null){
            Log.d(LOG_TAG, "call finish()!");
            finish();
            return;
        }
        /* @} */
        // UNISOC: add for feature 888845
        mServiceClass = mSubscriptionInfoHelper.getCarrierIntValueByKey(
                CarrierConfigManagerEx.KEY_CONFIG_IMS_CALLFORWARD_SERVICECLASS);
        // UNISOC: add for FEATURE_ALL_QUERY_CALLFOR
        mContext = this.getApplicationContext();
        mImsManager = new ImsManager(mContext, mPhone.getPhoneId());
        // UNISOC: add for feature 884921 durationforward
        mSupportDurationForward = mImsManager.isVolteEnabledByPlatform()
                && mContext.getResources().getBoolean(R.bool.config_show_durationforward);
        CarrierConfigManager carrierConfig = (CarrierConfigManager)
                getSystemService(CARRIER_CONFIG_SERVICE);
        if (carrierConfig != null) {
            mReplaceInvalidCFNumbers = carrierConfig.getConfig().getBoolean(
                    CarrierConfigManager.KEY_CALL_FORWARDING_MAP_NON_NUMBER_TO_VOICEMAIL_BOOL);
        }

        PreferenceScreen prefSet = getPreferenceScreen();
        mButtonCFU = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFU_KEY);
        mButtonCFB = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFB_KEY);
        mButtonCFNRy = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFNRY_KEY);
        mButtonCFNRc = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFNRC_KEY);
        // UNISOC: add for FEATURE_ALL_QUERY_CALLFOR
        tryRegisterImsListener();
        mButtonCFU.setParentActivity(this, mButtonCFU.reason);
        mButtonCFB.setParentActivity(this, mButtonCFB.reason);
        mButtonCFNRy.setParentActivity(this, mButtonCFNRy.reason);
        mButtonCFNRc.setParentActivity(this, mButtonCFNRc.reason);

        mPreferences.add(mButtonCFU);
        mPreferences.add(mButtonCFB);
        mPreferences.add(mButtonCFNRy);
        mPreferences.add(mButtonCFNRc);
        /* UNISOC: add for feature 884921 durationforward @{ */
        mButtonCFT = prefSet.findPreference(BUTTON_CFT_KEY);
        mTimePrefs = mContext.getSharedPreferences(PREF_PREFIX_TIME + mPhone.getSubId(), Context.MODE_PRIVATE);
        if (mSupportDurationForward) {
            CallForwardTimeEditPreFragement.addListener(this);
            tcpListener = this;
        } else {
            if (mButtonCFT != null && prefSet != null) {
                prefSet.removePreference(mButtonCFT);
            }
        }
        /* @} */

        // we wait to do the initialization until onResume so that the
        // TimeConsumingPreferenceActivity dialog can display as it
        // relies on onResume / onPause to maintain its foreground state.

        mFirstResume = true;
        mIcicle = icicle;

        /* UNISOC: add for FEATURE_VIDEO_CALL_FOR @{ */
        if (mIcicle != null) {
            mHasInit = mIcicle.getBoolean(KEY_HASINIT);
        }
        /* @} */

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        /* UNISOC: modify by BUG 916869 @{ */
        mActivityContainer = ActivityContainer.getInstance();
        mActivityContainer.setApplication(getApplication());
        mActivityContainer.addActivity(this, mSubscriptionInfoHelper.getPhone().getPhoneId());
        /* @} */
    }

    @Override
    public void onResume() {
        super.onResume();

        /* SPRD: add for bug 937030 @{ */
        if (mEnterMultiWindow){
            Log.d(LOG_TAG, "now is in multi-mode");
            // UNISOC: add for Bug 977835
            dismissAllDialog();
            mInitIndex = 0;
            mPreferences.get(mInitIndex).init(this, false, mPhone, mReplaceInvalidCFNumbers);
            mEnterMultiWindow = false;
        } else if (mFirstResume) {/* @} */
            /* UNISOC: add for FEATURE_ALL_QUERY_CALLFOR @{ */
            if (mIcicle == null || !mHasInit) {
                // UNISOC: add for feature 888845
                Log.d(LOG_TAG, "start to init, is special operator " + mServiceClass
                        + ";mIsVolteEnable:" + mIsVolteEnable);
                if ((mServiceClass == NOT_SPECAIL_SERVICECLASS) && mIsVolteEnable) {
                    Log.d(LOG_TAG, "start to getCallForwardingOption all : " + mIsVolteEnable);
                    GsmUmtsCallForwardOptions.this.onStarted(
                            mPreferences.get(ALL_CALL_FORWARD_INDEX), true);
                    mPhone.getCallForwardingOption(CommandsInterface.CF_REASON_ALL,
                            queryHandler.obtainMessage());
                } else {
                    mInitIndex = 0;
                    if (mPreferences.size() > 1) {
                        setSuppServiceFlag(GsmCdmaPhoneEx.SS_FLAG_DES_START);
                    }
                    mPreferences.get(mInitIndex).init(this, false, mPhone, mReplaceInvalidCFNumbers);
                }
            } else {
                mInitIndex = mPreferences.size();

                //UNISOC: add for bug1013340
                boolean needQuery = false;
                if(mIcicle.getBoolean(PROGRESS_DIALOG_SHOWING)){
                    removeDialog(BUSY_SAVING_DIALOG);
                    needQuery = true;
                }
                //UNISOC: add for bug1237955
                int preIndex = 0;
                for (CallForwardEditPreference pref : mPreferences) {
                    Bundle bundle = mIcicle.getParcelable(pref.getKey());
                    pref.setToggled(bundle.getBoolean(KEY_TOGGLE));
                    CallForwardInfo cf = new CallForwardInfo();
                    cf.number = bundle.getString(KEY_NUMBER);
                    cf.status = bundle.getInt(KEY_STATUS);
                    cf.reason = bundle.getInt(KEY_REASON);
                    cf.serviceClass = bundle.getInt(KEY_SERVICECLASS);
                    Log.d(LOG_TAG,
                            "restore instance pref : " + pref.reason + "CallForwardInfo : " + cf
                                    + " needQuery = " + needQuery + " preIndex = " + preIndex);
                    // UNISOC: add for bug1013340 bug1237955
                    if (needQuery) {
                        if (mPreferences.size() > 1 && preIndex == 0) {
                            setSuppServiceFlag(GsmCdmaPhoneEx.SS_FLAG_DES_START);
                        } else if (preIndex == mPreferences.size() - 1) {
                            setSuppServiceFlag(GsmCdmaPhoneEx.SS_FLAG_DES_END);
                        }
                        preIndex++;
                    }
                    pref.init(this, !needQuery, mPhone, mReplaceInvalidCFNumbers);
                    if (!needQuery) {
                        pref.handleCallForwardResult(cf);
                        // UNISOC: add for feature 888845
                        pref.updateSummaryText();
                    }
                }
            }
            /* @} */
        }
        mFirstResume = false;
        mIcicle = null;
        // UNISOC: add for feature 884921 durationforward
        updateCFTSummaryText();
    }

    /* UNISOC: add for FEATURE_ALL_QUERY_CALLFOR @{ */
    protected void onDestroy() {
        super.onDestroy();

        Log.d(LOG_TAG,"onDestroy mInitIndex: "+ mInitIndex + " mPreferences.size():"+mPreferences.size());
        if(mInitIndex < mPreferences.size()-1){
            setSuppServiceFlag(GsmCdmaPhoneEx.SS_FLAG_DES_RETURN);
        }

        /* UNISOC: add for feature 884921 durationforward @{ */
        if (mSupportDurationForward) {
            CallForwardTimeEditPreFragement.removeListener(this);
        }
        /* @} */
        unRegisterImsListener();
        /* UNISOC: modify by BUG 916869 @{ */
        if (mActivityContainer != null) {
            mActivityContainer.removeActivity(this);
        }
        /* @} */
    }
    /* @} */

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        for (CallForwardEditPreference pref : mPreferences) {
            Bundle bundle = new Bundle();
            bundle.putBoolean(KEY_TOGGLE, pref.isToggled());
            if (pref.callForwardInfo != null) {
                bundle.putString(KEY_NUMBER, pref.callForwardInfo.number);
                bundle.putInt(KEY_STATUS, pref.callForwardInfo.status);
                /* UNISOC: add for FEATURE_ALL_QUERY_CALLFOR @{ */
                bundle.putInt(KEY_REASON, pref.callForwardInfo.reason);
                bundle.putInt(KEY_SERVICECLASS, pref.callForwardInfo.serviceClass);
                /* @} */
            /* UNISOC: add for feature 888845 @{ */
            } else if (pref.mImsCallForwardInfoEx != null) {
                bundle.putString(KEY_NUMBER, pref.mImsCallForwardInfoEx.mNumber);
                bundle.putInt(KEY_STATUS, pref.mImsCallForwardInfoEx.mStatus);
                bundle.putInt(KEY_REASON, pref.mImsCallForwardInfoEx.mCondition);
                bundle.putInt(KEY_SERVICECLASS, pref.mImsCallForwardInfoEx.mServiceClass);
            }
            /* @} */
            outState.putParcelable(pref.getKey(), bundle);
        }
        // UNISOC: add for FEATURE_ALL_QUERY_CALLFOR
        outState.putBoolean(KEY_HASINIT, mHasInit);
        //UNISOC: add for bug1013340
        outState.putBoolean(PROGRESS_DIALOG_SHOWING,
                mProgressDialog != null && mProgressDialog.isShowing());
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        // Left deliberately empty. There should be no side effects if a direct
        // subclass of Activity does not call super.
        Log.d(LOG_TAG, "onMultiWindowModeChanged isInMultiWindowMode=" + isInMultiWindowMode);
        dismissAllDialog();

        /* SPRD: add for bug 937030 @{ */
        if (isInMultiWindowMode) {
            mEnterMultiWindow = true;
        } else {
            Log.d(LOG_TAG, "now is quit the multi-mode");
            mInitIndex = 0;
            mPreferences.get(mInitIndex).init(this, false, mPhone, mReplaceInvalidCFNumbers);
            mEnterMultiWindow = false;
        }/* @} */
    }
    /* @} */


    @Override
    public void onFinished(Preference preference, boolean reading) {
        if (mInitIndex < mPreferences.size()-1 && !isFinishing()) {
            mInitIndex++;
            if (mInitIndex == mPreferences.size() - 1) {
                //set ss flag end before query last cf option
                setSuppServiceFlag(GsmCdmaPhoneEx.SS_FLAG_DES_END);
            }
            mPreferences.get(mInitIndex).init(this, false, mPhone, mReplaceInvalidCFNumbers);
            /* UNISOC: add for feature 884921 durationforward @{ */
        }  else if (mInitIndex == mPreferences.size() - 1 && !isFinishing()) {
            if (mSupportDurationForward) {
                mInitIndex++;
                mPreferences.get(0).initCallTimeForward();
            } else {
                mHasInit = true;
            }
            /* @} */
        } else {
            mHasInit = true;
        }

        super.onFinished(preference, reading);
    }

    //UNISOC: add for bug1013340
    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        super.onPrepareDialog(id, dialog, args);
        if (id == BUSY_READING_DIALOG || id == BUSY_SAVING_DIALOG) {
            // For onSaveInstanceState, treat the SAVING dialog as the same as the READING. As
            // the result, if the activity is recreated while waiting for SAVING, it starts reading
            // all the newest data.
            mProgressDialog = dialog;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(LOG_TAG, "onActivityResult: done");
        if (resultCode != RESULT_OK) {
            Log.d(LOG_TAG, "onActivityResult: contact picker result not OK.");
            return;
        }
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(data.getData(),
                NUM_PROJECTION, null, null, null);
            if ((cursor == null) || (!cursor.moveToFirst())) {
                Log.d(LOG_TAG, "onActivityResult: bad contact data, no results found.");
                return;
            }

            switch (requestCode) {
                case CommandsInterface.CF_REASON_UNCONDITIONAL:
                    mButtonCFU.onPickActivityResult(cursor.getString(0));
                    break;
                case CommandsInterface.CF_REASON_BUSY:
                    mButtonCFB.onPickActivityResult(cursor.getString(0));
                    break;
                case CommandsInterface.CF_REASON_NO_REPLY:
                    mButtonCFNRy.onPickActivityResult(cursor.getString(0));
                    break;
                case CommandsInterface.CF_REASON_NOT_REACHABLE:
                    mButtonCFNRc.onPickActivityResult(cursor.getString(0));
                    break;
                default:
                    // TODO: may need exception here.
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            GsmUmtsAllCallForwardOptions.goUpToTopLevelSetting(this, mSubscriptionInfoHelper);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /* UNISOC: add for FEATURE_ALL_QUERY_CALLFOR @{ */
    @Override
    public void onBackPressed() {
        GsmUmtsAllCallForwardOptions.goUpToTopLevelSetting(this, mSubscriptionInfoHelper);
    }

    @Override
    public void onError(Preference preference, int error) {
        Log.d(LOG_TAG, "onError, preference=" + preference.getKey() + ", error=" + error);
        CallForwardEditPreference pref = null;
        if (preference instanceof CallForwardEditPreference) {
            pref = (CallForwardEditPreference)preference;
            if (pref != null && !pref.isInitializing()) {
                pref.setEnabled(false);
            }
        }
        Log.d(LOG_TAG, "mInitIndex =" + mInitIndex);
        if (pref != null && !pref.isInitializing()) {
            super.onError(preference,error);
        }
        /* UNISOC: add for feature 884921 durationforward @{ */
        if (error == FDN_CHECK_FAILURE) {
            mIsFDNOn = true;
        } else {
            mIsFDNOn = false;
        }
        if (mInitIndex == mPreferences.size() - 1) {
            refreshCFTButton();
        }
        /* @} */
    }

    /* UNISOC: add for feature 884921 durationforward @{ */
    @Deprecated
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (preference == mButtonCFT) {
            final Intent intent = new Intent();
            intent.setClassName("com.android.phone",
                    "com.unisoc.phone.callsettings.callforward.CallForwardTimeEditPreference");
            intent.putExtra("phone_id", String.valueOf(mPhone.getPhoneId()));
            startActivity(intent);
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
    /* @} */

    private synchronized void tryRegisterImsListener() {
        if (mImsManager != null && (mImsManager.isVolteEnabledByPlatform() || mImsManager.isWfcEnabledByPlatform())) {
            mIImsServiceEx = ImsManagerEx.getIImsServiceEx();
            if (mIImsServiceEx != null) {
                try {
                    if (!mIsImsListenerRegistered) {
                        mIsImsListenerRegistered = true;
                        mIImsServiceEx.registerforImsRegisterStateChanged(mImsUtListenerExBinder);
                    }
                } catch (RemoteException e) {
                    Log.e(LOG_TAG, "regiseterforImsException", e);
                }
            }
        }
    }

    private void unRegisterImsListener() {
        // SPRD: add for bug686917 and bug813467 and bug 943401
        if (mImsManager != null && (mImsManager.isVolteEnabledByPlatform() || mImsManager.isWfcEnabledByPlatform())) {
            try {
                if (mIsImsListenerRegistered) {
                    mIsImsListenerRegistered = false;
                    mIImsServiceEx.unregisterforImsRegisterStateChanged(mImsUtListenerExBinder);
                }
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "unRegisterImsListener", e);
            }
        }
    }

    private final IImsRegisterListener.Stub mImsUtListenerExBinder
            = new IImsRegisterListener.Stub() {
        @Override
        public void imsRegisterStateChange(boolean isRegistered) {
            boolean isRegisteredById = ImsManagerEx.isImsRegisteredForPhone(mPhone.getPhoneId());
            Log.d(LOG_TAG, "isRegistered : " + isRegistered
                    + " isRegisteredById : " + isRegisteredById);
            if (mIsVolteEnable != isRegisteredById) {
                mIsVolteEnable = isRegistered;
            }
        }
    };

    private final Handler queryHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.d(LOG_TAG, "after query all callforward, query cfu.");
            boolean querySuccess = true;
            mInitIndex = mPreferences.size() - 1;
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar != null && (ar.exception != null || ar.userObj instanceof Throwable)) {
                querySuccess = false;
            }
            CallForwardInfo cfInfoArray[] = (CallForwardInfo[]) ar.result;
            if (cfInfoArray != null && cfInfoArray.length == 0) {
                querySuccess = false;
            }
            if (querySuccess) {
                addReasonForCFInfoList(cfInfoArray);
                int length = cfInfoArray.length;
                for (int i = 0; i < length; i++) {
                    Log.d(LOG_TAG,
                                "handleGetCFAllResponse, cfInfoArray[" + i + "]=" + cfInfoArray[i]);
                    handleCallForwardAllResult(cfInfoArray[i]);
                }
                GsmUmtsCallForwardOptions.this.onFinished(
                        mPreferences.get(ALL_CALL_FORWARD_INDEX), true);
            } else {
                mInitIndex = 0;
                //set ss flag start when query all callforward failed
                if (mPreferences.size() > 1) {
                    setSuppServiceFlag(GsmCdmaPhoneEx.SS_FLAG_DES_START);
                }
                mPreferences.get(mInitIndex).init(
                        GsmUmtsCallForwardOptions.this, false, mPhone, mReplaceInvalidCFNumbers);
                GsmUmtsCallForwardOptions.super.onFinished(
                        mPreferences.get(ALL_CALL_FORWARD_INDEX), true);
            }
        }

    };

    /*
     * CF_ALL response has not reason info,
     * we should parse and add it to response data for menu displaying
     * response of CF_ALL query protocol(order of sub cf items info):
     * CFU - voice call
     * CFU - data
     * CFB - voice call
     * CFB - data
     * CFNRy - voice all
     * CFNRy - data
     * CFNRc - voice all
     * CFNRc - data
     **/
    private void addReasonForCFInfoList(CallForwardInfo[] cfInfoArray) {
        if (cfInfoArray != null && cfInfoArray.length > 0) {
            int subCnt = SUB_CF_ITEM_ORDER.length;
            int j = 0;
            int m = 0;
            Log.d(LOG_TAG, "add reason size= " + subCnt);
            for (int i = 0; i < cfInfoArray.length; i++) {
                if ((cfInfoArray[i].serviceClass & CommandsInterface.SERVICE_CLASS_VOICE) == 0) {
                    if (j < subCnt) {
                        cfInfoArray[i].reason = SUB_CF_ITEM_ORDER[j];
                        j++;
                    }
                } else {
                    if (m < subCnt) {
                        cfInfoArray[i].reason = SUB_CF_ITEM_ORDER[m];
                        m++;
                    }
                }
                Log.d(LOG_TAG, "cls : " + cfInfoArray[i].serviceClass
                        + " reason : " + cfInfoArray[i].reason);
            }
        }
    }

    private void handleCallForwardAllResult(CallForwardInfo callForwardInfo) {
        if (callForwardInfo != null) {
            Log.d(LOG_TAG, "handleCallForwardAllResult done, callForwardInfo= " + callForwardInfo);
            for (CallForwardEditPreference pref : mPreferences) {
                Log.d(LOG_TAG, "pref srv =" + pref.getServiceClass() + " reason" + pref.getReason());
                if ((callForwardInfo.serviceClass & pref.getServiceClass()) != 0
                        && callForwardInfo.reason == pref.getReason()) {
                    if (pref == mButtonCFU) {
                        mPhone.setVoiceCallForwardingFlag(
                                1, (callForwardInfo.status == 1), callForwardInfo.number);
                    }
                    pref.init(this, true, mPhone, mReplaceInvalidCFNumbers);
                    pref.handleCallForwardResult(callForwardInfo);
                    pref.updateSummaryText();
                }
            }
        }
    }

    private boolean getLteCapability(Phone phone) {
        if (phone != null) {
            int rafMax = phone.getRadioAccessFamily();
            return (rafMax & RadioAccessFamily.RAF_LTE) == RadioAccessFamily.RAF_LTE;
        }
        return false;
    }
    /* @} */

    /* UNISOC: add for feature 884921 durationforward @{ */
    public void refreshCFTButton() {
        if (mButtonCFT != null && mSupportDurationForward) {
            if ((mButtonCFU.isToggled()
                    && !CFT_STATUS_ACTIVE.equals(
                    mTimePrefs.getString(PREF_PREFIX_TIME + "status_" + mPhone.getSubId(), "")))
                        || mIsFDNOn) {
                mButtonCFT.setEnabled(false);
            } else {
                mButtonCFT.setEnabled(true);
            }
        }
    }

    private void updateCFTSummaryText() {
        if (mSupportDurationForward) {
            CharSequence summary;
            if (CFT_STATUS_ACTIVE.equals(mTimePrefs.getString(PREF_PREFIX_TIME
                    + "status_" + mPhone.getSubId(), ""))) {
                summary = mTimePrefs
                        .getString(PREF_PREFIX_TIME + "num_" + mPhone.getSubId(), "");
            } else {
                summary = mContext.getText(R.string.sum_cft_disabled);
            }
            mButtonCFT.setSummary(summary);
        }
    }

    public void savePrefData(String key, String value) {
        Log.w(LOG_TAG, "savePrefData(" + key + ", " + value + ")");
        if (mTimePrefs != null) {
            SharedPreferences.Editor editor = mTimePrefs.edit();
            editor.putString(key, value);
            editor.apply();
        }
    }

    @Override
    public void onEnableStatus(Preference preference, int status) {
        if (status == 1) {
            if (mButtonCFT != null) {
                mButtonCFT.setEnabled(false);
            }
        } else if (mSupportDurationForward) {
            refreshCFTButton();
            if (CFT_STATUS_ACTIVE.equals(
                    mTimePrefs.getString(PREF_PREFIX_TIME + "status_" + mPhone.getSubId(), ""))) {
                mButtonCFU.setEnabled(false);
            }
            updateCFTSummaryText();
        }
        if (preference == mButtonCFU) {
            Log.i(LOG_TAG, "onEnableStatus...status = " + status);
            updatePrefCategoryEnabled(mButtonCFU);
        }
    }

    @Override
    public void onCallForawrdTimeStateChanged(String number) {
        mInitIndex = 0;
        mPreferences.get(mInitIndex).init(this, false, mPhone);
        updateCFTSummaryText();
        if (number != null) {
            SharedPreferences prefs = mContext.getSharedPreferences(
                    CallForwardEditPreference.PREF_PREFIX + mPhone.getSubId(), Context.MODE_PRIVATE);
            mButtonCFU.saveStringPrefs(
                    CallForwardEditPreference.PREF_PREFIX + mPhone.getSubId() + "_" + CFU_PREF_REASON,
                    number, prefs);
        }
    }

    private void updatePrefCategoryEnabled(Preference preference) {
        if (preference == mButtonCFU) {
            if (mButtonCFU.isToggled()) {
                mButtonCFB.setEnabled(false);
                mButtonCFNRc.setEnabled(false);
                mButtonCFNRy.setEnabled(false);
            } else {
                mButtonCFB.setEnabled(true);
                mButtonCFNRc.setEnabled(true);
                mButtonCFNRy.setEnabled(true);
            }
        }
    }
    /* @} */

    /* UNISSOC: add for bug1237955@ */
    @Override
    public void setSuppServiceFlag(int startFlag) {
        Log.d(LOG_TAG, "setSuppServiceFlag: startFlag = " + startFlag);
        if (mPhone != null) {
            ((GsmCdmaPhoneEx) mPhone).setSuppServiceFlag(startFlag);
        }
    }/* @} */

    /* UNISOC: add for Bug 972349@{ */
    @Override
    public void dismissAllDialog() {
        for (CallForwardEditPreference pref : mPreferences) {
            if ((pref.getDialog() != null) && (pref.getDialog().isShowing())) {
                pref.getDialog().dismiss();
            }
        }
        super.dismissAllDialog();
    }/* @} */
}
