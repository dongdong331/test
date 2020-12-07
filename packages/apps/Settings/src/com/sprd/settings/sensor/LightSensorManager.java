
package com.sprd.settings.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class LightSensorManager {

    private static final boolean DEBUG = true;
    private static final String TAG = "LightSensor";

    private static LightSensorManager instance;
    private SensorManager mSensorManager;
    private LightSensorListener mLightSensorListener;
    private ProxySensorListener mProxySensorListener;
    private boolean mHasStarted = false;

    private LightSensorManager() {
    }

    public static LightSensorManager getInstance() {
        if (instance == null) {
            instance = new LightSensorManager();
        }
        return instance;
    }

    public void start(Context context) {
        if (mHasStarted) {
            return;
        }
        mHasStarted = true;
        mSensorManager = (SensorManager) context.getApplicationContext().getSystemService(
                Context.SENSOR_SERVICE);
        Sensor lightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (lightSensor != null) {
            mLightSensorListener = new LightSensorListener();
            mSensorManager.registerListener(mLightSensorListener, lightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        Sensor proxySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proxySensor != null) {
            mProxySensorListener = new ProxySensorListener();
            mSensorManager.registerListener(mProxySensorListener, proxySensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public void stop() {
        if (!mHasStarted || mSensorManager == null) {
            return;
        }
        mHasStarted = false;
        mSensorManager.unregisterListener(mLightSensorListener);
        mSensorManager.unregisterListener(mProxySensorListener);
    }

    public float getLux() {
        if (mLightSensorListener != null) {
            return mLightSensorListener.lux;
        }
        return -1.0f;
    }

    public float getProxy() {
        if (mProxySensorListener != null) {
            return mProxySensorListener.proxy;
        }
        return -1.0f;
    }

    private class LightSensorListener implements SensorEventListener {

        private float lux;

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                lux = event.values[0];
                if (DEBUG) {
                    Log.d(TAG, "lux : " + lux);
                }
            }
        }
    }

    private class ProxySensorListener implements SensorEventListener {

        private float proxy;

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                proxy = event.values[0];
                if (DEBUG) {
                    Log.d(TAG, "proxy : " + proxy);
                }
            }
        }
    }
}
