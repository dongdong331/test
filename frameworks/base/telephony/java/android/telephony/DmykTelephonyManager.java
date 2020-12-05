
package com.dmyk.android.telephony;

import android.app.ActivityThread;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.ims.internal.ImsManagerEx;
import com.android.internal.telephony.IPhoneSubInfo;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.ITelephonyEx;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.dmyk.commlog.data.SprdNetworkStats;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

public class DmykTelephonyManager extends com.dmyk.android.telephony.DmykAbsTelephonyManager {

    static final String TAG = "DmykTelephonyManager";
    private SubscriptionManager mSubscriptionManager;
    private ServiceState mServiceState = null;
    /** Watches for changes to the APN db. */
    private ApnChangeObserver mApnObserver;
    private Handler mHandler;
    private static final String APN_URI = "content://telephony/carriers/";
    /** Watches for changes of VoLTE State. */
    private final BroadcastReceiver mVoLTEConfigReceiver = new VoLTEConfigReceiver();
    /** Watches for changes of Enhanced 4GLTE Switch State. */
    private ContentObserver mEnhancedLTEObserver;
    private ContentObserver mVoLTESettingObserver;
    private ContentObserver mVoLTESettingObserver1;
    private boolean mVolteEnable;
    private boolean mIsSettingEnhancedLTEByByUser = false;
    private boolean mIsSettingEnhancedLTEBySDK = false;
    private boolean mIsSettingEnhancedLTEByByUser1 = false;
    private boolean mIsSettingEnhancedLTEBySDK1 = false;
    public static final int PHONE_ID_ZERO = 0;
    public static final int PHONE_ID_ONE = 1;
    private boolean[] mEnhanced4gSettingState = null;
    private String mCameraId;
    private int mFlashlightState = SWITCH_STATE_UNKNOWN;
    private int mWifiState = SWITCH_STATE_UNKNOWN;
    private Handler mTorchHandler;
    private LocationMsg mLocationMsg;

    /** @hide */
    public DmykTelephonyManager(Context context) {
        super(context);
        Log.d(TAG, "context = " + context + " this = " + this);
        mSubscriptionManager = SubscriptionManager.from(mContext);
        mHandler = new DmykHandler(context.getMainLooper());
        mApnObserver = new ApnChangeObserver();
        context.getContentResolver().registerContentObserver(
                Uri.parse(APN_URI + "preferapn"), true, mApnObserver);
        int phoneCount = getPhoneCount();
        mEnhanced4gSettingState = new boolean[phoneCount];
        for (int i = 0; i < phoneCount; i++) {
            mEnhanced4gSettingState[i] = true;
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_VOLTE_STATE_SETTING);
        intentFilter.addAction(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        context.registerReceiver(mVoLTEConfigReceiver, intentFilter);
        Log.d(TAG, "register receiver");
        mEnhancedLTEObserver = new ContentObserver(null) {
            public void onChange(boolean selfChange) {
                Log.d(TAG, "onVoLTESettingChangeByUser");
                onVoLTESettingChangeByUser();
            }
        };
        mVoLTESettingObserver = new ContentObserver(null) {
            public void onChange(boolean selfChange, Uri uri) {
                Log.d(TAG, "mVoLTESettingObserver uri = " + uri.toString());
                onVoLTESettingChange();
            }
        };
        mVoLTESettingObserver1 = new ContentObserver(null) {
            public void onChange(boolean selfChange, Uri uri) {
                Log.d(TAG, "mVoLTESettingObserver1 uri = " + uri.toString());
                onVoLTESettingChange1();
            }
        };
        context.getContentResolver().registerContentObserver(Uri.withAppendedPath(SubscriptionManager.
                CONTENT_URI, SubscriptionManager.ENHANCED_4G_MODE_ENABLED),
                true, mEnhancedLTEObserver);
        context.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                VOLTE_DMYK_STATE_0),
                true, mVoLTESettingObserver);
        context.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                VOLTE_DMYK_STATE_1),
                true, mVoLTESettingObserver1);
        SprdNetworkStats.getInstance().init(context);
        mLocationMsg = new LocationMsg();
    }

    /** Network type is unknown */
    public static final int NETWORK_TYPE_UNKNOWN = 0;
    /** Current network is GPRS */
    public static final int NETWORK_TYPE_GPRS = 1;
    /** Current network is EDGE */
    public static final int NETWORK_TYPE_EDGE = 2;
    /** Current network is UMTS */
    public static final int NETWORK_TYPE_UMTS = 3;
    /** Current network is CDMA: Either IS95A or IS95B */
    public static final int NETWORK_TYPE_CDMA = 4;
    /** Current network is EVDO revision 0 */
    public static final int NETWORK_TYPE_EVDO_0 = 5;
    /** Current network is EVDO revision A */
    public static final int NETWORK_TYPE_EVDO_A = 6;
    /** Current network is 1xRTT */
    public static final int NETWORK_TYPE_1xRTT = 7;
    /** Current network is HSDPA */
    public static final int NETWORK_TYPE_HSDPA = 8;
    /** Current network is HSUPA */
    public static final int NETWORK_TYPE_HSUPA = 9;
    /** Current network is HSPA */
    public static final int NETWORK_TYPE_HSPA = 10;
    /** Current network is iDen */
    public static final int NETWORK_TYPE_IDEN = 11;
    /** Current network is EVDO revision B */
    public static final int NETWORK_TYPE_EVDO_B = 12;
    /** Current network is LTE */
    public static final int NETWORK_TYPE_LTE = 13;
    /** Current network is eHRPD */
    public static final int NETWORK_TYPE_EHRPD = 14;
    /** Current network is HSPA+ */
    public static final int NETWORK_TYPE_HSPAP = 15;
    /** Current network is GSM */
    public static final int NETWORK_TYPE_GSM = 16;
    /** Current network is TD_SCDMA */
    public static final int NETWORK_TYPE_TD_SCDMA = 17;
    /** Current network is IWLAN */
    public static final int NETWORK_TYPE_IWLAN = 18;
    /**
     * Add For LTE_CA. Current network is LTE_CA
     */
    public static final int NETWORK_TYPE_LTE_CA = 19;

    private static String PROP_CTA_MODEL = "ro.cta.hardware.model";
    private static String PROP_CTA_ROM_SIZE = "ro.cta.hardware.rom.size";
    private static String PROP_CTA_RAM_SIZE = "ro.cta.hardware.ram.size";

    /**
     * Returns the number of phones available.
     * Returns 0 if none of voice, sms, data is not supported
     * Returns 1 for Single standby mode (Single SIM functionality)
     * Returns 2 for Dual standby mode.(Dual SIM functionality)
     */
    @Override
    public int getPhoneCount() {
        int phoneCount = 1;
        switch (getMultiSimConfiguration()) {
            case UNKNOWN:
                phoneCount = 1;
                break;
            case DSDS:
            case DSDA:
                phoneCount = PhoneConstants.MAX_PHONE_COUNT_DUAL_SIM;
                break;
            case TSTS:
                phoneCount = PhoneConstants.MAX_PHONE_COUNT_TRI_SIM;
                break;
        }
        Log.d(TAG, "getPhoneCount phoneCount = " + phoneCount);
        return phoneCount;
    }

    /**
     * Returns the unique device ID, for example, the IMEI for GSM phones. Return null if device ID
     * is not available.
     * <p>
     * Requires Permission: {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @param phoneId of which deviceID is returned
     */
    @Override
    public String getGsmDeviceId(int phoneId) {
        Log.d(TAG, "getGsmDeviceId phoneId = " + phoneId);
        if (phoneId > getPhoneCount() - 1) {
            throw new IllegalArgumentException("phoneId is invalid" + phoneId);
        }

        try {
            IPhoneSubInfo info = getSubscriberInfo();
            if (info == null)
                return null;
            return info.getDeviceIdForPhone(phoneId, mContext.getOpPackageName());
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Returns the unique device ID, the MEID or ESN for CDMA phones. Return null if device ID is
     * not available.
     */
    @Override
    public String getCdmaDeviceId() {
        return null;
    }

    /**
     * Returns the unique subscriber ID, for example, the IMSI for a GSM phone.
     * Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @param phoneId whose subscriber id is returned
     */
    @Override
    public String getSubscriberId(int phoneId) {
        if (phoneId > getPhoneCount() - 1) {
            throw new IllegalArgumentException("phoneId is invalid" + phoneId);
        }
        int subId = getSubId(phoneId);
        Log.d(TAG, "getSubscriberId subId = " + subId);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return null;
        }
        try {
            IPhoneSubInfo info = getSubscriberInfo();
            if (info == null)
                return null;
            return info.getSubscriberIdForSubscriber(subId, mContext.getOpPackageName());
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Retrieves the serial number of the ICC, if applicable.
     *
     * @param phoneId whose ICCID is returned.
     */
    @Override
    public String getIccId(int phoneId) {
        Log.d(TAG, "getIccId phoneId = " + phoneId);
        if (phoneId > getPhoneCount() - 1) {
            throw new IllegalArgumentException("phoneId is invalid" + phoneId);
        }

        int subId = getSubId(phoneId);
        Log.d(TAG, "getIccId subId = " + subId);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return null;
        }
        SubscriptionInfo sir = mSubscriptionManager.getActiveSubscriptionInfo(subId);
        if (sir != null) {
            Log.d(TAG, "getIccId IccId = " + sir.getIccId());
            return sir.getIccId();
        }
        return null;
    }

    /**
     * Returns a constant indicating the current data connection state
     * (cellular).
     *
     * @see #DATA_DISCONNECTED
     * @see #DATA_CONNECTING
     * @see #DATA_CONNECTED
     * @see #DATA_SUSPENDED
     *
     * @param phoneId of which data state is returned
     */
    @Override
    public int getDataState(int phoneId) {
        Log.d(TAG, "getDataState phoneId = " + phoneId);
        if (phoneId > getPhoneCount() - 1) {
            throw new IllegalArgumentException("phoneId is invalid" + phoneId);
        }
        int subId = getSubId(phoneId);
        Log.d(TAG, "getDataState subId = " + subId);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return DATA_DISCONNECTED;
        }
        try {
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony == null)
                return DATA_DISCONNECTED;
            return telephony.getDataState(subId);
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return DATA_DISCONNECTED;
        } catch (NullPointerException ex) {
            return DATA_DISCONNECTED;
        }
    }

    /**
     * Returns a constant indicating the state of the device SIM card in a slot.
     *
     * @param phoneId
     *
     * @see #SIM_STATE_UNKNOWN
     * @see #SIM_STATE_ABSENT
     * @see #SIM_STATE_PIN_REQUIRED
     * @see #SIM_STATE_PUK_REQUIRED
     * @see #SIM_STATE_NETWORK_LOCKED
     * @see #SIM_STATE_READY
     * @see #SIM_STATE_NOT_READY
     * @see #SIM_STATE_PERM_DISABLED
     * @see #SIM_STATE_CARD_IO_ERROR
     */
    @Override
    public int getSimState(int phoneId) {
        Log.d(TAG, "getSimState phoneId = " + phoneId);
        if (phoneId > getPhoneCount() - 1) {
            throw new IllegalArgumentException("phoneId is invalid" + phoneId);
        }
        int simState = SubscriptionManager.getSimStateForSlotIndex(phoneId);
        if (simState == SIM_STATE_LOADED) {
            simState = SIM_STATE_READY;
        }
        Log.d(TAG, "getSimState simState = " + simState);
        return simState;
    }

    @Override
    public int getNetworkType(int phoneId) {
        Log.d(TAG, "getNetworkType phoneId = " + phoneId);
        if (phoneId > getPhoneCount() - 1) {
            throw new IllegalArgumentException("phoneId is invalid" + phoneId);
        }
        int subId = getSubId(phoneId);
        Log.d(TAG, "getNetworkType subId = " + subId);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return NETWORK_TYPE_UNKNOWN;
        }

        try {
            if (mSubscriptionManager == null) {
                Log.d(TAG, "getNetworkType mSubscriptionManager == null ");
                return NETWORK_TYPE_UNKNOWN;
            }
            int dataPhoneId = mSubscriptionManager.getDefaultDataPhoneId();
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                if (phoneId == dataPhoneId) {
                    return telephony.getDataNetworkTypeForSubscriber(subId, getOpPackageName());
                } else {
                    mServiceState = telephony.getServiceStateForSubscriber(subId, getOpPackageName());
                    if (mServiceState.getState() == ServiceState.STATE_IN_SERVICE) {
                        return telephony.getVoiceNetworkTypeForSubscriber(subId, getOpPackageName());
                    } else {
                        return NETWORK_TYPE_UNKNOWN;
                    }
                }
            } else {
                // This can happen when the ITelephony interface is not up yet.
                return NETWORK_TYPE_UNKNOWN;
            }
        } catch (RemoteException ex) {
            // This shouldn't happen in the normal case
            return NETWORK_TYPE_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return NETWORK_TYPE_UNKNOWN;
        }
    }

    /**
     * Returns the software version number for the device, for example,
     * the IMEI/SV for GSM phones. Return null if the software version is
     * not available.
     */
    @Override
    public String getDeviceSoftwareVersion() {
        return SystemProperties.get("ro.build.display.id", "");
    }

    /**
     * Returns device type.
     *
     * @see #DEVICE_TYPE_UNKNOWN
     * @see #DEVICE_TYPE_CELLPHONE
     * @see #DEVICE_TYPE_PAD
     * @see #DEVICE_TYPE_STB
     * @see #DEVICE_TYPE_WATCH
     * @see #DEVICE_TYPE_BRACELET
     */
    @Override
    public int getDeviceType() {
        return DEVICE_TYPE_CELLPHONE;
    }

    @Override
    public int getMasterPhoneId() {
        Log.d(TAG, "getMasterPhoneId");
        return SubscriptionManager.getPhoneId(SubscriptionManager.getDefaultDataSubscriptionId());
    }

    /**
     * Returns true if the device is considered international roaming on the current network for a
     * subscription.
     * <p>
     * Availability: Only when user registered to a network.
     *
     * @param phoneId
     */
    @Override
    public boolean isInternationalNetworkRoaming(int phoneId) {
        Log.d(TAG, "isInternationalNetworkRoaming phoneId = " + phoneId);

        int subId = getSubId(phoneId);
        Log.d(TAG, "isInternationalNetworkRoaming subId = " + subId);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return false;
        }
        try{
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony != null) {
                return telephony.isInternationalNetworkRoaming(subId);
            } else {
                // This can happen when the ITelephony interface is not up yet.
                return false;
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
            return false;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return false;
        }
    }

    /**
     * Returns a platform configuration for VoLTE which may override the user setting.
     */
    public boolean isVolteEnabledByPlatform() {
        try{
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony != null) {
                return telephony.isVolteEnabledByPlatform();
            } else {
                // This can happen when the ITelephony interface is not up yet.
                return false;
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
            return false;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return false;
        }
    }

    /**
     * Returns the user configuration of Enhanced 4G LTE Mode setting.
     */
    public boolean isEnhanced4gLteModeSettingEnabledByUser(int phoneId) {
        Log.d(TAG, "isEnhanced4gLteModeSettingEnabledByUser phoneId = " + phoneId);
        try{
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony != null) {
                return telephony.isEnhanced4gLteModeSettingEnabledByUser(phoneId);
            } else {
                // This can happen when the ITelephony interface is not up yet.
                return false;
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
            return false;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return false;
        }
    }

    /**
     * Change persistent Enhanced 4G LTE Mode setting.
     */
    public void setEnhanced4gLteModeSetting(boolean enabled, int phoneId) {
        Log.d(TAG, "setEnhanced4gLteModeSetting phoneId = " + phoneId + " enabled = " + enabled);
        try{
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony != null) {
                telephony.setEnhanced4gLteModeSetting(enabled, phoneId);
            } else {
                // This can happen when the ITelephony interface is not up yet.
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
        }
    }

    /**
     * Returns a constant indicating the VoLTE state of the device SIM card in a slot.
     *
     * @see #VOLTE_STATE_ON
     * @see #VOLTE_STATE_OFF
     * @see #VOLTE_STATE_UNKNOWN
     * @param phoneId of which VoLTE state is returned
     */
    @Override
    public int getVoLTEState(int phoneId) {
        Log.d(TAG, "getVoLTEState phoneId = " + phoneId);
        if (phoneId > getPhoneCount() - 1) {
            Log.d(TAG, "phoneId is invalid  return state unknow");
            return VOLTE_STATE_UNKNOWN;
        }
        mVolteEnable = isVolteEnabledByPlatform();
        int masterPhoneId = getMasterPhoneId();
        Log.d(TAG, "getVoLTEState() - mVolteEnable = " + mVolteEnable
                + " phoneId = " + phoneId
                + " masterPhoneId = " + masterPhoneId
                + " isSingleVolte = " + isSingleVolte());
        if (mVolteEnable && masterPhoneId != SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
            if (isSingleVolte()) {
                if (phoneId == 0) {
                    return android.provider.Settings.System.getInt(
                            mContext.getContentResolver(), getMasterPhoneId() == 1 ?
                            VOLTE_DMYK_STATE_1 : VOLTE_DMYK_STATE_0,
                            VOLTE_STATE_OFF);
                }
            } else {
                if (phoneId == 0) {
                    return android.provider.Settings.System.getInt(
                           mContext.getContentResolver(),
                           VOLTE_DMYK_STATE_0,
                           VOLTE_STATE_OFF);
                } else if (phoneId == 1) {
                   return android.provider.Settings.System.getInt(
                           mContext.getContentResolver(),
                           VOLTE_DMYK_STATE_1,
                           VOLTE_STATE_OFF);
                }
            }
        }
        return VOLTE_STATE_UNKNOWN;
    }

    /**
     * Returns APN content URI of the giving phoneId.
     */
    @Override
    public Uri getAPNContentUri(int phoneId) {
        Log.d(TAG, "getAPNContentUri phoneId = " + phoneId);
        if (phoneId > getPhoneCount() - 1) {
            throw new IllegalArgumentException("phoneId is invalid" + phoneId);
        }
        int preferedId = -1;
        String mccmnc = getTelephonyProperty(phoneId,
                TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, "");
        Log.d(TAG, "mccmnc = " + mccmnc);
        if (mccmnc.isEmpty()) {
            return null;
        }
        int subId = getSubId(phoneId);
        Uri uri = Uri.parse(APN_URI);
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            final String orderBy = "_id";
            final String where = "numeric=\""
                + mccmnc
                + "\" AND NOT (type='ia' AND (apn=\"\" OR apn IS NULL))";

            Cursor cursor = mContext.getContentResolver().query(Uri.parse(APN_URI + "preferapn/subId/" + subId), new String[] {
                    "_id"}, where, null,orderBy);
            if (cursor != null) {
                if (cursor.getCount() > 0 && cursor.moveToFirst()) {
                    preferedId = cursor.getInt(0);
                    Log.d(TAG, "preferedId = " + preferedId + ", sub id = " + subId);
                    if (preferedId != -1) {
                        uri =  Uri.parse(APN_URI + preferedId);
                    }
                }
                cursor.close();
            }
        }

        return uri;
    }


    /**
     * Returns slotId as a positive integer associated with the phoneId or throw Exception if an
     * invalid phoneId index.
     */
    @Override
    public int getSlotId(int phoneId) {
        if (phoneId > getPhoneCount() - 1) {
            throw new IllegalArgumentException("phoneId is invalid" + phoneId);
        }
        return phoneId;
    }

    /**
     * Returns subscriber ID associated with the phoneId.
     */
    @Override
    public int getSubId(int phoneId) {
        SubscriptionManager subManager = SubscriptionManager.from(mContext);
        List<SubscriptionInfo> availableSubInfoList = subManager.getActiveSubscriptionInfoList();
        if (availableSubInfoList == null) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        for(int i = 0 ;i < availableSubInfoList.size();i++){
            if(availableSubInfoList.get(i).getSimSlotIndex() == phoneId){
                return availableSubInfoList.get(i).getSubscriptionId();
            }
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    /**
     * Returns cell id of the current location of the device.
     *
     * @param phoneId whose cell id is returned
     * @return cell id, -1 if unknown, 0xffff max legal value
     *
     * <p>Requires Permission:
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_COARSE_LOCATION} or
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION}.
     */
    @Override
    public int getCellId(int phoneId) {
        Log.d(TAG, "getCellId phoneId = " + phoneId);
        if (phoneId > getPhoneCount() - 1) {
            throw new IllegalArgumentException("phoneId is invalid" + phoneId);
        }
        int cellId = -1;
        try {
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony != null) {
                int simState = getSimState(phoneId);
                if (simState != SIM_STATE_ABSENT && simState != SIM_STATE_UNKNOWN) {
                    Bundle bundle = telephony.getCellLocationForPhone(phoneId, mContext.getOpPackageName());
                    if (bundle != null) {
                        cellId = bundle.getInt("cid", -1);
                        Log.d(TAG, "getCellId cellId = " + cellId);
                        return cellId;
                    }
                }
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
        }
        return cellId;
    }

    /**
     * Returns lac of the current location of the device.
     *
     * @param phoneId whose lac is returned
     * @return location area code, -1 if unknown, 0xffff max legal value
     *
     * <p>Requires Permission:
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_COARSE_LOCATION} or
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION}.
     */
    @Override
    public int getLac(int phoneId) {
        Log.d(TAG, "getLac phoneId = " + phoneId);
        if (phoneId > getPhoneCount() - 1) {
            throw new IllegalArgumentException("phoneId is invalid" + phoneId);
        }
        int lac = -1;
        try {
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony != null) {
                int simState = getSimState(phoneId);
                if (simState != SIM_STATE_ABSENT && simState != SIM_STATE_UNKNOWN) {
                    Bundle bundle = telephony.getCellLocationForPhone(phoneId, mContext.getOpPackageName());
                    if (bundle != null) {
                        lac = bundle.getInt("lac", -1);
                        Log.d(TAG, "getLac lac = " + lac);
                        return lac;
                    }
                }
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
        }
        return lac;
    }

    @Override
    public int getPriorNetworkType() {
        int type = Resources.getSystem().getInteger(com.android.internal.
                R.integer.config_getpriornetworktype);
        return type;
    }

    @Override
    public void setVolteState(int phoneId, int volte) {
        Log.d(TAG, "setVolteState phoneId = " + phoneId + " volte = " + volte);
        if (SubscriptionManager.isValidPhoneId(phoneId)) {
            mIsSettingEnhancedLTEByByUser = true;
            mIsSettingEnhancedLTEByByUser1 = true;
            setEnhanced4gLteModeSetting((volte == VOLTE_STATE_ON), phoneId);
        }
    }

    @Override
    public boolean isDMSet() {
        Log.d(TAG, "isDMSet mIsSettingEnhancedLTEBySDK = " + mIsSettingEnhancedLTEBySDK
              +" mIsSettingEnhancedLTEBySDK1 = " + mIsSettingEnhancedLTEBySDK1);
        return mIsSettingEnhancedLTEBySDK
                || mIsSettingEnhancedLTEBySDK1;
    }

    @Override
    public int queryLteCtccSimType (int phoneId) {
        try {
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony != null) {
                return telephony.queryLteCtccSimType(phoneId);
            } else {
                // This can happen when the ITelephony interface is not up yet.
            }
        } catch (RemoteException ex) {
            // This shouldn't happen in the normal case
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
        }
        return -1;
    }

    @Override
    public String getCTAModel()
    {
        String model = SystemProperties.get(PROP_CTA_MODEL);
        if (model.isEmpty()) {
            return "ro.cta.hardware.model null";
        }

        return model;
    }

    @Override
    public String getRomStorageSize()
    {
        String romSize = SystemProperties.get(PROP_CTA_ROM_SIZE);
        if (romSize.isEmpty()) {
            return "ro.cta.hardware.rom.size null";
        }

        return romSize;
    }

    @Override
    public String getRamStorageSize()
    {
        String ramSize = SystemProperties.get(PROP_CTA_RAM_SIZE);
        if (ramSize.isEmpty()) {
            return "ro.cta.hardware.ram.size null";
        }

        return ramSize;
    }

    @Override
    public String getMacAddress()
    {
        WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        if (null == wifiManager) {
            return null;
        }

        int wifiState = wifiManager.getWifiState();
        Log.d(TAG, "getMacAddress wifiState = " + wifiState);
        if (wifiManager.WIFI_STATE_ENABLED != wifiState) {
            return null;
        }

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        return !TextUtils.isEmpty(macAddress) ? macAddress : null;
    }

    @Override
    public String getCPUModel()
    {
        String str = null;
        try
        {
            str = getCpuHardwareByFile();
            if (TextUtils.isEmpty(str)) {
                str = Build.HARDWARE;
            }
        }
        catch (Exception localException)
        {
            str = Build.HARDWARE;
        }
        return str;
    }

    @Override
    public String getOSVersion()
    {
        return android.os.Build.VERSION.RELEASE;
    }

    @Override
    public long getWiFiTotalBytes(long startTime, long endTime)
    {
        long wifiTotalBytes = 0;

        Log.d(TAG, "getWiFiTotalBytes startTime: " + startTime + ", endTime: " + endTime);

        wifiTotalBytes = SprdNetworkStats.getInstance().getWiFiTotalBytes(startTime, endTime);

        Log.d(TAG, "getWiFiTotalBytes wifiTotalBytes: " + wifiTotalBytes);

        return wifiTotalBytes;
    }

    @Override
    public long getMobileTotalBytes(int phoneId, long startTime, long endTime)
    {
         long mobileTotalBytes = 0;

         Log.d(TAG, "getMobileTotalBytes startTime: " + startTime + ", endTime: " + endTime + ", phoneId: " + phoneId);

         mobileTotalBytes = SprdNetworkStats.getInstance().getMobileTotalBytes(phoneId, startTime, endTime);

         Log.d(TAG, "getMobileTotalBytes mobileTotalBytes: " + mobileTotalBytes);
         return mobileTotalBytes;
    }

    @Override
    public String getPhoneNumber(int phoneId) {
        Log.d(TAG, "getPhoneNumber phoneId = " + phoneId);
        String number = null;
        if (phoneId > getPhoneCount() - 1) {
            throw new IllegalArgumentException("phoneId is invalid" + phoneId);
        }
        int subId = getSubId(phoneId);
        Log.d(TAG, "getPhoneNumber subId = " + subId);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return null;
        }
        try {
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                number = telephony.getLine1NumberForDisplay(subId, mContext.getOpPackageName());
            }
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
        if (number != null) {
            return number;
        }
        try {
            IPhoneSubInfo info = getSubscriberInfo();
            if (info == null)
                return null;
            return info.getLine1NumberForSubscriber(subId, mContext.getOpPackageName());
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    @Override
    public int getSwitchState(int switchId) {
        int switchState = SWITCH_STATE_UNKNOWN;
        switch (switchId) {
            case SWITCH_WIFI:
                switchState = getWifiState();
                break;
            case SWITCH_GPRS:
                switchState = getGprsState();
                break;
            case SWITCH_BLUETOOTH:
                switchState = getBlueToothState();
                break;
            case SWITCH_GPS:
                switchState = getGpsState();
                break;
            case SWITCH_SHOCK:
                switchState = getShockState();
                break;
            case SWITCH_SILENT:
                switchState = getSilentState();
                break;
            case SWITCH_HOT_SPOT:
                switchState = getHotspotState();
                break;
            case SWITCH_FLYING:
                switchState = getFlyingState();
                break;
            case SWITCH_FLASH_LIGHT:
                switchState = getFlashlightState();
                break;
            case SWITCH_SCREEN:
                switchState = getScreenState();
                break;
            case SWITCH_SCREEN_ROTATE:
                switchState = getScreenRotateState();
                break;
            case SWITCH_LTE:
                switchState = getLTEState();
                break;
            case SWITCH_AUTO_BRIGHT:
                switchState = getAutoBrightState();
                break;
        }
        Log.d(TAG, "getSwitchState switchId: " + switchId + ", switchState: " + switchState);
        return switchState;
    }

    @Override
    public LocationMsg getLocationMsg() {
        Log.d(TAG, "getLocationMsg Enter");

        mLocationMsg.latitude = 0x1.fffffffffffffP+1023;//init with invalid value
        mLocationMsg.longitude = 0x1.fffffffffffffP+1023; //init with invalid value
        mLocationMsg.addrFrom = 0;

        LocationManager locationManager = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
        if (null == locationManager) {
            Log.d(TAG, "getLocationMsg locationManager null");
            return mLocationMsg;//return invalid value
        }

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {//GPS off, just return
            Log.d(TAG, "getLocationMsg GPS off, just return default value");
            return mLocationMsg; //return invalid value
        }

        HandlerThread handlerThread = new HandlerThread("getLocation");
        handlerThread.start();

        try {
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, handlerThread.getLooper());
        } catch (Exception e){
            e.printStackTrace();
        }

        Log.d(TAG, "getLocationMsg sleep 10s");
        SystemClock.sleep(10000);
        locationManager.removeUpdates(locationListener);
        handlerThread.quit();
        Log.d(TAG, "getLocationMsg latitude: " + mLocationMsg.latitude + ", longitude: " + mLocationMsg.longitude + ", addrFrom: " + mLocationMsg.addrFrom);
        return mLocationMsg;
    }

    LocationListener locationListener = new LocationListener() {
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG, "locationListener onLocationChanged");
            mLocationMsg.latitude = location.getLatitude();
            mLocationMsg.longitude = location.getLongitude();
        }
    };

    private int getWifiState() {
        mWifiState = SWITCH_STATE_UNKNOWN;
        WifiManager wifiManager = (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
        if (null == wifiManager) {
            return mWifiState;  //SWITCH_STATE_UNKNOWN by default
        }

        int wifiState = wifiManager.getWifiState();

        if (wifiManager.WIFI_STATE_ENABLED == wifiState) {
            mWifiState = SWITCH_STATE_ON;
        } else if (wifiManager.WIFI_STATE_UNKNOWN != wifiState) {
            mWifiState = SWITCH_STATE_OFF;
        }
        Log.d(TAG, "getWifiState mWifiState = " + mWifiState);
        return mWifiState;
    }

    private int getGprsState() {
        int subId;
        if (mSubscriptionManager == null) {
            Log.d(TAG, "getGprsState mSubscriptionManager == null");
            return SWITCH_STATE_UNKNOWN;
        }
        subId = mSubscriptionManager.getDefaultDataSubscriptionId();
        Log.d(TAG, "getGprsState subId = " + subId);
        try {
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                boolean retVal = telephony.getDataEnabled(subId);
                return retVal? SWITCH_STATE_ON : SWITCH_STATE_OFF;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#getDataEnabled", e);
        } catch (NullPointerException e) {
        }
        return SWITCH_STATE_UNKNOWN;
    }

    private int getBlueToothState() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (null == bluetoothAdapter) {
            return SWITCH_STATE_UNKNOWN;
        }

        return bluetoothAdapter.isEnabled() ? SWITCH_STATE_ON : SWITCH_STATE_OFF;
    }

    private int getGpsState() {
        LocationManager locationManager = (LocationManager) mContext.getSystemService(mContext.LOCATION_SERVICE);
        if (null == locationManager) {
            return SWITCH_STATE_UNKNOWN;
        }

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ? SWITCH_STATE_ON : SWITCH_STATE_OFF;
    }

    private int getShockState() {
        AudioManager audioManager = (AudioManager)mContext.getSystemService(mContext.AUDIO_SERVICE);
        if (null == audioManager) {
            return SWITCH_STATE_UNKNOWN;
        }

        return (audioManager.RINGER_MODE_VIBRATE == audioManager.getRingerMode()) ? SWITCH_STATE_ON : SWITCH_STATE_OFF;
    }

    private int getSilentState() {
        AudioManager audioManager = (AudioManager)mContext.getSystemService(mContext.AUDIO_SERVICE);
        if (null == audioManager) {
            return SWITCH_STATE_UNKNOWN;
        }

        return (audioManager.RINGER_MODE_SILENT == audioManager.getRingerMode()) ? SWITCH_STATE_ON : SWITCH_STATE_OFF;
    }

    private int getHotspotState() {
        WifiManager wifiManager = (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
        if (null == wifiManager) {
            return SWITCH_STATE_UNKNOWN;
        }

        return wifiManager.isWifiApEnabled() ? SWITCH_STATE_ON : SWITCH_STATE_OFF;
    }

    private int getFlyingState() {
        return (Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0) ? SWITCH_STATE_ON : SWITCH_STATE_OFF;
    }

    private int getFlashlightState() {
        mFlashlightState = SWITCH_STATE_UNKNOWN;
        Log.d(TAG, "getFlashlightState Enter");
        CameraManager cameraManager = (CameraManager)mContext.getSystemService(Context.CAMERA_SERVICE);
        if (null == cameraManager) {
            Log.d(TAG, "getFlashlightState null == cameraManager");
            return SWITCH_STATE_UNKNOWN;
        }

        tryInitCamera(cameraManager);
        Log.d(TAG, "getFlashlightState Before sleep 50ms");
        SystemClock.sleep(50);

        Log.d(TAG, "getFlashlightState mFlashlightState: " + mFlashlightState);
        cameraManager.unregisterTorchCallback(mTorchCallback);

        return mFlashlightState;
    }

    private int getScreenState() {
        PowerManager powerManager = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
        if (null == powerManager) {
            return SWITCH_STATE_UNKNOWN;
        }

        return powerManager.isScreenOn() ? SWITCH_STATE_ON : SWITCH_STATE_OFF;
    }

    private int getScreenRotateState() {
        return (Settings.System.getInt(mContext.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1) ? SWITCH_STATE_ON : SWITCH_STATE_OFF;
    }

    private int getLTEState() {
        int subId;
        subId = mSubscriptionManager.getDefaultDataSubscriptionId();
        int settingsNetworkMode = android.provider.Settings.Global.getInt(
                mContext.getContentResolver(),
                android.provider.Settings.Global.PREFERRED_NETWORK_MODE + subId,
                RILConstants.PREFERRED_NETWORK_MODE);
        boolean lteEnable = (settingsNetworkMode == RILConstants.NETWORK_MODE_LTE_GSM_WCDMA
                || settingsNetworkMode == RILConstants.NETWORK_MODE_LTE_ONLY
                || settingsNetworkMode == RILConstants.NETWORK_MODE_LTE_GSM);
        return lteEnable? SWITCH_STATE_ON : SWITCH_STATE_OFF;
    }

    private int getAutoBrightState() {
        return (Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) ? SWITCH_STATE_ON : SWITCH_STATE_OFF;
    }

    /** Enum indicating multisim variants
     *  DSDS - Dual SIM Dual Standby
     *  DSDA - Dual SIM Dual Active
     *  TSTS - Triple SIM Triple Standby
     **/
    private enum MultiSimVariants {
        DSDS, DSDA, TSTS, UNKNOWN
    };

    /**
     * Returns the multi SIM variant Returns DSDS for Dual SIM Dual Standby Returns DSDA for Dual
     * SIM Dual Active Returns TSTS for Triple SIM Triple Standby Returns UNKNOWN for others
     */
    private MultiSimVariants getMultiSimConfiguration() {
        String mSimConfig = SystemProperties.get(TelephonyProperties.PROPERTY_MULTI_SIM_CONFIG);
        if (mSimConfig.equals("dsds")) {
            return MultiSimVariants.DSDS;
        } else if (mSimConfig.equals("dsda")) {
            return MultiSimVariants.DSDA;
        } else if (mSimConfig.equals("tsts")) {
            return MultiSimVariants.TSTS;
        } else {
            return MultiSimVariants.UNKNOWN;
        }
    }

    private IPhoneSubInfo getSubscriberInfo() {
        // get it each time because that process crashes a lot
        return IPhoneSubInfo.Stub.asInterface(ServiceManager.getService("iphonesubinfo"));
    }

    private ITelephony getITelephony() {
        return ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
    }

    private ITelephonyEx getITelephonyEx() {
        return ITelephonyEx.Stub.asInterface(ServiceManager.getService("phone_ex"));
    }

    /**
     * Gets the telephony property.
     */
    private static String getTelephonyProperty(int phoneId, String property, String defaultVal) {
        String propVal = null;
        String prop = SystemProperties.get(property);
        if ((prop != null) && (prop.length() > 0)) {
            String values[] = prop.split(",");
            if ((phoneId >= 0) && (phoneId < values.length) && (values[phoneId] != null)) {
                propVal = values[phoneId];
            }
        }
        return propVal == null ? defaultVal : propVal;
    }

    private String getOpPackageName() {
        // For legacy reasons the TelephonyManager has API for getting
        // a static instance with no context set preventing us from
        // getting the op package name. As a workaround we do a best
        // effort and get the context from the current activity thread.
        if (mContext != null) {
            return mContext.getOpPackageName();
        }
        return ActivityThread.currentOpPackageName();
    }

    private class DmykHandler extends Handler{
        public DmykHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage = " + msg);
        }
    }
    private class ApnChangeObserver extends ContentObserver {
        public ApnChangeObserver () {
            super(mHandler);
        }

        public void onChange(boolean selfChange) {
            Uri uri =  Uri.parse(APN_URI);
            int phoneId = mSubscriptionManager.getDefaultDataPhoneId();
            if (SubscriptionManager.isValidPhoneId(phoneId)) {
                uri = getAPNContentUri(phoneId);
                Log.d(TAG, "uri " + uri );
            }
            Intent intent = new Intent(ACTION_APN_STATE_CHANGE);
            intent.setData(uri);
            mContext.sendBroadcast(intent);
        }
    }

    /**
     * Put value in Settings when VoLTE state changed .
     */
    private void putVoLTEState() {
        boolean isEnhanced4gLteMode = isEnhanced4gLteModeSettingEnabledByUser(PHONE_ID_ZERO);
        Log.d(TAG, "putVoLTEState: isEnhanced4gLteMode = " + isEnhanced4gLteMode +
                ", phoneId = " + PHONE_ID_ZERO);
        android.provider.Settings.System.putInt(
                mContext.getContentResolver(),
                VOLTE_DMYK_STATE_0,
                isEnhanced4gLteMode ? VOLTE_STATE_ON
                        : VOLTE_STATE_OFF);
    }

    private void putVoLTEState1() {
        boolean isEnhanced4gLteMode = isEnhanced4gLteModeSettingEnabledByUser(PHONE_ID_ONE);
        Log.d(TAG, "putVoLTEState1: isEnhanced4gLteMode = " + isEnhanced4gLteMode +
                ", phoneId = " + PHONE_ID_ONE);
        android.provider.Settings.System.putInt(
                mContext.getContentResolver(),
                VOLTE_DMYK_STATE_1,
                isEnhanced4gLteMode ? VOLTE_STATE_ON
                        : VOLTE_STATE_OFF);
    }

    private void onVoLTESettingChangeByUser() {
        Log.d(TAG, "onVoLTESettingChangeByUser mIsSettingEnhancedLTEBySDK = " +
                mIsSettingEnhancedLTEBySDK + " mIsSettingEnhancedLTEByByUser = " +
                mIsSettingEnhancedLTEByByUser);
        if (mIsSettingEnhancedLTEBySDK == true) {
            mIsSettingEnhancedLTEBySDK = false;
        } else {
            mIsSettingEnhancedLTEByByUser = true;
        }

        int phoneCount = getPhoneCount();
        boolean[] isEnhanced4gLteMode = null;
        isEnhanced4gLteMode = new boolean[phoneCount];
        for (int i = 0; i < phoneCount; i++) {
            isEnhanced4gLteMode[i] = true;
        }
        for (int phoneId = 0; phoneId < phoneCount; phoneId++) {
            isEnhanced4gLteMode[phoneId] = isEnhanced4gLteModeSettingEnabledByUser(phoneId);
            Log.d(TAG, "putVoLTEState: isEnhanced4gLteMode[" + phoneId + "] = " + isEnhanced4gLteMode[phoneId]);
            if (isEnhanced4gLteMode[phoneId] != mEnhanced4gSettingState[phoneId]) {
                mEnhanced4gSettingState[phoneId] = isEnhanced4gLteMode[phoneId];
                if (phoneId == PHONE_ID_ZERO) {
                    Log.d(TAG, "onVoLTESettingChangeByUser: sim0 volte setting changed");
                    putVoLTEState();
                } else if (phoneId == PHONE_ID_ONE) {
                    Log.d(TAG, "onVoLTESettingChangeByUser: sim1 volte setting changed");
                    putVoLTEState1();
                }
            }
        }
    }

    private void onVoLTESettingChange() {
        Log.d(TAG, "onVoLTESettingChange mIsSettingEnhancedLTEBySDK = " +
                mIsSettingEnhancedLTEBySDK + "mIsSettingEnhancedLTEByByUser = " +
                mIsSettingEnhancedLTEByByUser);
        /* Single Volte situation: only master phoneId send broadcast to SDK
           Dual Volte situation: sim0 send broadcast to SDK. */
        if ((isSingleVolte() && getMasterPhoneId() == PHONE_ID_ZERO)
                || !isSingleVolte()) {
            if (mIsSettingEnhancedLTEByByUser == false) {
                boolean enabled = android.provider.Settings.System.getInt(
                        mContext.getContentResolver(),
                        VOLTE_DMYK_STATE_0,
                        VOLTE_STATE_OFF) == 1;
                mIsSettingEnhancedLTEBySDK = true;
                setEnhanced4gLteModeSetting(enabled, PHONE_ID_ZERO);
            }
            Intent intent = new Intent(ACTION_VOLTE_STATE_CHANGE);
            intent.putExtra(EXTRA_SIM_PHONEID, PHONE_ID_ZERO);
            intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            mContext.sendBroadcast(intent);
            mIsSettingEnhancedLTEByByUser = false;
            Log.d(TAG, "PHONE_ID_ZERO sendBroadcast VOLTE_STATE_CHANGE");
        }
    }

    private void onVoLTESettingChange1() {
        Log.d(TAG, "onVoLTESettingChange1 mIsSettingEnhancedLTEBySDK = " +
                mIsSettingEnhancedLTEBySDK1 + "mIsSettingEnhancedLTEByByUser = " +
                mIsSettingEnhancedLTEByByUser1);
        /* Single Volte situation: only master phoneId send broadcast to SDK
           Dual Volte situation: sim1 send broadcast to SDK. */
        if ((isSingleVolte() && getMasterPhoneId() == PHONE_ID_ONE)
                || !isSingleVolte()) {
            if (mIsSettingEnhancedLTEByByUser1 == false) {
                boolean enabled = android.provider.Settings.System.getInt(
                        mContext.getContentResolver(),
                        VOLTE_DMYK_STATE_1,
                        VOLTE_STATE_UNKNOWN) == 1;
                mIsSettingEnhancedLTEBySDK1 = true;
                setEnhanced4gLteModeSetting(enabled, PHONE_ID_ONE);
            }
            Intent intent = new Intent(ACTION_VOLTE_STATE_CHANGE);
            intent.putExtra(EXTRA_SIM_PHONEID, PHONE_ID_ONE);
            intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            mContext.sendBroadcast(intent);
            mIsSettingEnhancedLTEByByUser1 = false;
            Log.d(TAG, "PHONE_ID_ONE sendBroadcast VOLTE_STATE_CHANGE");
        }
    }

    /**
     * Put value in Settings when VoLTE state changed .
     */
    private class VoLTEConfigReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "receive action = " + action);
            if (ACTION_VOLTE_STATE_SETTING.equals(action)) {
                intent.setClassName("com.android.phone",
                        "com.android.phone.MobileNetworkSettings");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            } else if (TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED.equals(action)) {
                int state = isEnhanced4gLteModeSettingEnabledByUser(PHONE_ID_ZERO) ? 1 : 0;
                int state1 = isEnhanced4gLteModeSettingEnabledByUser(PHONE_ID_ONE) ? 1 : 0;
                int masterPhoneId = getMasterPhoneId();
                int masterState = VOLTE_STATE_UNKNOWN;
                if (masterPhoneId == PHONE_ID_ZERO) { // sim0
                    masterState = state;
                } else if (masterPhoneId == PHONE_ID_ONE) { // sim1
                    masterState = state1;
                }
                Log.d(TAG, "default phoneId = " + masterPhoneId + "state = " + state
                        + " state1 = " + state1
                        + " masterState = " + masterState);
                // Single Volte version only focus on main sim card volte state change.
                if (isSingleVolte()) {
                    if (getVoLTEState(masterPhoneId) != masterState) {
                        mIsSettingEnhancedLTEByByUser = true;
                        mIsSettingEnhancedLTEByByUser1 = true;
                        putVoLTEState();
                        putVoLTEState1();
                    }
                // Dual Volte version should focus on both sim0 and sim1 volte states change.
                } else if (getVoLTEState(PHONE_ID_ZERO) != state
                        || getVoLTEState(PHONE_ID_ONE) != state1) {
                    mIsSettingEnhancedLTEByByUser = true;
                    mIsSettingEnhancedLTEByByUser1 = true;
                    putVoLTEState();
                    putVoLTEState1();
                }
            } else if(ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                Intent conn = new Intent(ACTION_CONNECTIVITY_CHANGE);
                mContext.sendBroadcast(conn);
            } else if (TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED.equals(action)) {
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY, -1);
                int simStatus = intent.getIntExtra(TelephonyManager.EXTRA_SIM_STATE,
                        TelephonyManager.SIM_STATE_UNKNOWN);
                Log.d(TAG, "phoneId = " + phoneId + " simStatus = " + simStatus);
                if (TelephonyManager.SIM_STATE_LOADED == simStatus) {
                    mEnhanced4gSettingState[phoneId] = isEnhanced4gLteModeSettingEnabledByUser(phoneId);
                    Log.d(TAG, "mEnhanced4gSettingState [" + phoneId + "] = " + mEnhanced4gSettingState[phoneId]);
                }
            }
        }
    }

    // If L+L or DSDA version, return false.
    private boolean isSingleVolte() {
        return !ImsManagerEx.isDualVoLTEActive() && !ImsManagerEx.isDualLteModem();
    }
    private static String getCpuHardwareByFile()
    {
        HashMap localHashMap = new HashMap();
        Scanner localScanner = null;
        try
        {
            localScanner = new Scanner(new File("/proc/cpuinfo"));
            while (localScanner.hasNextLine())
            {
                String[] arrayOfString = localScanner.nextLine().split(": ");
                if (arrayOfString.length > 1) {
                    localHashMap.put(arrayOfString[0].trim(), arrayOfString[1].trim());
                }
            }
        }
        catch (Exception localException)
        {
            localException.printStackTrace();
        }
        finally
        {
            if (localScanner != null) {
                localScanner.close();
            }
        }
        return (String)localHashMap.get("Hardware");
    }

    private String getCameraId(CameraManager cameraManager) throws CameraAccessException {
        String[] ids = cameraManager.getCameraIdList();
        for (String id : ids) {
            CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
            Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
            if (flashAvailable != null && flashAvailable
                    && lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        return null;
    }

    private synchronized void ensureHandler() {
        Log.d(TAG, "ensureHandler Enter");
        if (null == mTorchHandler) {
            Log.d(TAG, "ensureHandler mTorchHandler null");
            HandlerThread thread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
            mTorchHandler = new Handler(thread.getLooper());
        }
    }

    private void tryInitCamera(CameraManager cameraManager) {
        try {
            mCameraId = getCameraId(cameraManager);
            Log.d(TAG, "tryInitCamera mCameraId: " + mCameraId);
        } catch (Throwable e) {
            Log.e(TAG, "Couldn't initialize.", e);
            return;
        }

        if (mCameraId != null) {
            ensureHandler();
            Log.d(TAG, "registerTorchCallback");
            cameraManager.registerTorchCallback(mTorchCallback, mTorchHandler);
            Log.d(TAG, "registerTorchCallback after");
        }
    }

    private final CameraManager.TorchCallback mTorchCallback =
        new CameraManager.TorchCallback() {
            @Override
            public void onTorchModeUnavailable(String cameraId) {
            }

            @Override
            public void onTorchModeChanged(String cameraId, boolean enabled) {
                Log.d(TAG, "cameraId: " + cameraId);
                if (TextUtils.equals(cameraId, mCameraId)) {
                    mFlashlightState = enabled ? SWITCH_STATE_ON : SWITCH_STATE_OFF;
                    Log.d(TAG, "mFlashlightState: " + mFlashlightState);
                }
            }
    };
}
