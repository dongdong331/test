package com.android.incallui.sprd.plugin.shakePhoneToStartRecording;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.android.dialer.binary.common.DialerApplication;
import com.android.incallui.InCallPresenter;
import com.android.incallui.sprd.plugin.shakePhoneToStartRecording.ShakePhoneToStartRecordingHelper;
import com.android.incallui.InCallActivity;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;

import android.hardware.Sensor;
import android.hardware.SprdSensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

public class ShakePhoneToStartRecordingPlugin extends ShakePhoneToStartRecordingHelper
        implements SensorEventListener {

    private static final String TAG = "ShakePhoneToStartRecordingPlugin";
    private Context mContext;
    private SensorManager mSensorManager;
    private Sensor mSensor;

    public ShakePhoneToStartRecordingPlugin() {

    }

    public void init(Context context) {
        Log.d(TAG,"init");
        mContext = context;

        registerTriggerRecorderListener();
    }

    private boolean isShakePhoneToStartRecordingOn() {//modify for bug935980
        boolean isShakePhoneToStartRecordingOn = Settings.Global.getInt(mContext
                .getContentResolver(), Settings.Global.SMART_CALL_RECORDER, 0) != 0;
        return isShakePhoneToStartRecordingOn;
    }

    void registerTriggerRecorderListener() {
        if (isShakePhoneToStartRecordingOn()) {
            mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            mSensor = mSensorManager.getDefaultSensor(SprdSensor.TYPE_SPRDHUB_SHAKE, true);
            if (mSensor != null) {
                Log.d(TAG, "registerTriggerListener");
                mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    }

    public void unRegisterTriggerRecorderListener() {
        Log.d(TAG, "unRegisterTriggerListener");
        if (mSensor != null) {
            mSensorManager.unregisterListener(this, mSensor);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d(TAG, "onSensorChanged");
        DialerCall mCall = CallList.getInstance().getActiveOrBackgroundCall();
        Context context = mContext.getApplicationContext();
        DialerApplication dialerApplication = (DialerApplication) context;
        boolean isRecordingStart = dialerApplication.getIsRecordingStart();
        InCallPresenter inCallPresenter = InCallPresenter.getInstance();

        if (!isRecordingStart && (mCall != null
                && CallList.getInstance().getOutgoingCall() == null)
                && inCallPresenter != null && inCallPresenter.isShowingInCallUi() ){
            inCallPresenter.recordClick();
            unRegisterTriggerRecorderListener();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

}
