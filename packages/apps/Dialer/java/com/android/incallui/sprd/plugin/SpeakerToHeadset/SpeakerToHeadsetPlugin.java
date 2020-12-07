package com.android.incallui.sprd.plugin.SpeakerToHeadset;

import android.content.Context;
import android.provider.Settings;
import android.telecom.CallAudioState;
import android.util.Log;

import com.android.incallui.sprd.plugin.SpeakerToHeadset.SpeakerToHeadsetHelper;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;

import android.hardware.Sensor;
import android.hardware.SprdSensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

public class SpeakerToHeadsetPlugin extends SpeakerToHeadsetHelper
        implements SensorEventListener {

    private static final String TAG = "SpeakerToHeadsetPlugin";
    private Context mContext;
    private InCallButtonUiDelegate mInCallButtonUiDelegate;
    private SensorManager mSensorManager;
    private Sensor mSensor;

    public SpeakerToHeadsetPlugin() {

    }

    public void init(Context context, InCallButtonUiDelegate inCallButtonUiDelegate) {
        mContext = context;
        mInCallButtonUiDelegate = inCallButtonUiDelegate;

        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(SprdSensor.TYPE_SPRDHUB_HAND_UP);

        registerSpeakerTriggerListener();
    }

    private boolean isHandsfreeSwitchToHeadsetOn() {
        //UNISOC:modify for bug937150
        boolean isHandsfreeSwitchToHeadsetOn = Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.HANDSFREE_SWITCH, 0) != 0;
        return isHandsfreeSwitchToHeadsetOn;
    }

    void registerSpeakerTriggerListener() {

        if (isHandsfreeSwitchToHeadsetOn() && mSensor != null) {
            Log.d(TAG, "registerListener.");
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public void unRegisterSpeakerTriggerListener() {
        if (mSensor != null) {
            Log.d(TAG, "unregisterListener.");
            mSensorManager.unregisterListener(this, mSensor);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d(TAG, "onSensorChanged");
        if (mInCallButtonUiDelegate != null
                && mInCallButtonUiDelegate.getCurrentAudioState().getRoute() ==
                CallAudioState.ROUTE_SPEAKER) {
            mInCallButtonUiDelegate.toggleSpeakerphone();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

}

