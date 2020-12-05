/*
 ** Copyright 2018 The Spreadtrum.com
 */

package android.os;

import android.annotation.NonNull;
import android.util.ArrayMap;
import android.os.Bundle;
import java.util.ArrayList;
import android.os.sprdpower.Util;
import android.app.ActivityManager;

/**
 * Used by when report event to SceneRecognizeService
 * @hide
 */
public class BundleData {

    static final String TAG = "BundleData";

    public static final int DATA_TYPE_PROCESS_STATE = 0;
    public static final int DATA_TYPE_APP_STATE_EVENT = 1;
    public static final int DATA_TYPE_APP_TRANSITION = 2;
    public static final int DATA_TYPE_BATTERY_EVENT = 3;
    public static final int DATA_TYPE_DEV_STATUS = 4;
    public static final int DATA_TYPE_INPUT = 5;
    public static final int DATA_TYPE_APP_VIDEO = 6;
    public static final int DATA_TYPE_SYSTEM_EVENT = 7;

    // DATA_TYPE_PROCESS_STATE
    public static final int PROCESS_STATE_VENDOR_FIRST = ActivityManager.PROCESS_STATE_NONEXISTENT + 1;
    public static final int PROCESS_STATE_VENDOR_START = PROCESS_STATE_VENDOR_FIRST;
    public static final int PROCESS_STATE_VENDOR_FINISH = PROCESS_STATE_VENDOR_FIRST + 1;

    // belows used in TYPE_BATTERY_EVENT
    public static final int DATA_SUBTYPE_DEFAULT = 0;
    public static final int DATA_SUBTYPE_AUDIO = 1;
    public static final int DATA_SUBTYPE_VIDEO = 2;
    public static final int DATA_SUBTYPE_SENSOR = 3;
    public static final int DATA_SUBTYPE_GPS = 4;

    // belows used in TYPE_DEV_STATUS
    public static final int DATA_SUBTYPE_SCREEN = 5;
    public static final int DATA_SUBTYPE_BATTERY = 6;
    public static final int DATA_SUBTYPE_NETWORK = 7;

    public static final int STATE_STOP = 0;
    public static final int STATE_START = 1;

    public static final int TOUCH_EVENT_DOWN = 0;
    public static final int TOUCH_EVENT_UP = 1;
    public static final int TOUCH_EVENT_SINGLE_TAP = 2;
    public static final int TOUCH_EVENT_DOUBLE_TAP = 3;
    public static final int TOUCH_EVENT_LONG_PRESS = 4;
    public static final int TOUCH_EVENT_SCROLL = 5;
    public static final int TOUCH_EVENT_FLING = 6;

    public static final int SYSTEM_EVENT_WINDOW_CHANGED = 1;
    public static final int SYSTEM_EVENT_FPS_CHANGED = 2;
    public static final int SYSTEM_EVENT_TOUCHRATE_CHANGED = 3;

    private int mType;
    private Bundle mExtras;

    /**
     * A int extra used with {@link #DATA_TYPE_BATTERY_EVENT or #DATA_TYPE_DEV_STATUS} which
     * represents the subtype of current data
     * @hide
     * @removed
     */
    public static final String DATA_EXTRA_SUBTYPE = "data.extra.SUBTYPE";

    /**
     * A String extra which represents the package name of a app in current data
     * @hide
     * @removed
     */
    public static final String DATA_EXTRA_PACKAGENAME = "data.extra.PACKAGENAME";

    /**
     * A int extra which represents the uid of a app in current data
     * @hide
     * @removed
     */
    public static final String DATA_EXTRA_UID = "data.extra.UID";

    /**
     * A int extra used with {@link #DATA_TYPE_PROCESS_STATE} which represents
     * the process state of a app in current data
     * @hide
     * @removed
     */
    public static final String DATA_EXTRA_PROCESS_STATE = "data.extra.PROCESS_STATE";

    /**
     * A int extra used with {@link #DATA_TYPE_APP_STATE_EVENT} which represents
     * the app state event of a app in current data
     * @hide
     * @removed
     */
    public static final String DATA_EXTRA_APP_STATE_EVENT = "data.extra.APP_STATE_EVENT";

    /**
     * A Boolean extra used with {@link #DATA_TYPE_DEV_STATUS with #SUBTYPE_SCREEN} which represents
     * the screen state
     * @hide
     * @removed
     */
    public static final String DATA_EXTRA_SCREEN_ON = "data.extra.SCREEN_ON";

    /**
     * A Boolean extra used with {@link #DATA_TYPE_DEV_STATUS with #SUBTYPE_BATTERY} which represents
     * the battery plugged state
     * @hide
     * @removed
     */
    public static final String DATA_EXTRA_BATTERY_PLUGGED = "data.extra.BATTERY_PLUGGED";

    /**
     * A int extra used with {@link #DATA_TYPE_BATTERY_EVENT} which represents
     * the using state, such as start/stop
     * @hide
     * @removed
     */
    public static final String DATA_EXTRA_STATE = "data.extra.STATE";

    /**
     * A int extra used with {@link #DATA_TYPE_INPUT} which represents
     * the touch event, such as down/up
     * @hide
     * @removed
     */
    public static final String DATA_EXTRA_TOUCH_EVENT = "data.extra.TOUCH_EVENT";

    /**
     * A ArrayList<String> extra used with {@link #DATA_TYPE_APP_TRANSITION} which represents
     * the current visible apps
     * @hide
     * @removed
     */
    public static final String DATA_EXTRA_VISIBLE_APPS = "data.extra.VISIBLE_APPS";

    /**
     * A int extra which represents the sensor type
     * @hide
     * @removed
     */
    public static final String DATA_EXTRA_SENSOR = "data.extra.SENSOR";

    /**
     * A Bundle extra is used to transmit customize raw data
     */
    public static final String DATA_EXTRA_RAW_DATA = "data.extra.RAW_DATA";

    /**
     * A int extra used with {@link #DATA_TYPE_VIDEO_WIDTH} which represents
     * the width of the video
     * @hide
     * @removed
     */
    public static final String DATA_EXTRA_VIDEO_WIDTH = "data.extra.VIDEO_WIDTH";

    /**
     * A int extra used with {@link #DATA_TYPE_VIDEO_HEIGHT} which represents
     * the height of the video
     * @hide
     * @removed
     */
    public static final String DATA_EXTRA_VIDEO_HEIGHT = "data.extra.VIDEO_HEIGHT";

    /**
     * A float extra used with {@link #SYSTEM_EVENT_FPS_CHANGED} and {@link #SYSTEM_EVENT_TOUCHRATE_CHANGED}
     * which represents the rate
     * @hide
     * @removed
     */
    public static final String DATA_EXTRA_RATE = "data.extra.RATE";

    public BundleData() {
        this(-1);
    }

    public BundleData(int type) {
        mType = type;
    }

    public int getType() {
        return mType;
    }

    public @NonNull BundleData setType(int type) {
        mType = type;
        return this;
    }

    public void clear() {
        mType = -1;
        if (mExtras != null)
            mExtras.clear();
    }

    /**
     * Retrieve extended data from the intent.
     *
     * @param name The name of the desired item.
     * @param defaultValue the value to be returned if no value of the desired
     * type is stored with the given name.
     *
     * @return the value of an item that previously added with putExtra()
     * or the default value if none was found.
     *
     * @see #putExtra(String, boolean)
     */
    public boolean getBooleanExtra(String name, boolean defaultValue) {
        return mExtras == null ? defaultValue :
            mExtras.getBoolean(name, defaultValue);
    }

    /**
     * Retrieve extended data from the intent.
     *
     * @param name The name of the desired item.
     * @param defaultValue the value to be returned if no value of the desired
     * type is stored with the given name.
     *
     * @return the value of an item that previously added with putExtra()
     * or the default value if none was found.
     *
     * @see #putExtra(String, byte)
     */
    public byte getByteExtra(String name, byte defaultValue) {
        return mExtras == null ? defaultValue :
            mExtras.getByte(name, defaultValue);
    }

    /**
     * Retrieve extended data from the intent.
     *
     * @param name The name of the desired item.
     * @param defaultValue the value to be returned if no value of the desired
     * type is stored with the given name.
     *
     * @return the value of an item that previously added with putExtra()
     * or the default value if none was found.
     *
     * @see #putExtra(String, short)
     */
    public short getShortExtra(String name, short defaultValue) {
        return mExtras == null ? defaultValue :
            mExtras.getShort(name, defaultValue);
    }

    /**
     * Retrieve extended data from the intent.
     *
     * @param name The name of the desired item.
     * @param defaultValue the value to be returned if no value of the desired
     * type is stored with the given name.
     *
     * @return the value of an item that previously added with putExtra()
     * or the default value if none was found.
     *
     * @see #putExtra(String, char)
     */
    public char getCharExtra(String name, char defaultValue) {
        return mExtras == null ? defaultValue :
            mExtras.getChar(name, defaultValue);
    }

    /**
     * Retrieve extended data from the intent.
     *
     * @param name The name of the desired item.
     * @param defaultValue the value to be returned if no value of the desired
     * type is stored with the given name.
     *
     * @return the value of an item that previously added with putExtra()
     * or the default value if none was found.
     *
     * @see #putExtra(String, int)
     */
    public int getIntExtra(String name, int defaultValue) {
        return mExtras == null ? defaultValue :
            mExtras.getInt(name, defaultValue);
    }

    /**
     * Retrieve extended data from the intent.
     *
     * @param name The name of the desired item.
     * @param defaultValue the value to be returned if no value of the desired
     * type is stored with the given name.
     *
     * @return the value of an item that previously added with putExtra()
     * or the default value if none was found.
     *
     * @see #putExtra(String, long)
     */
    public long getLongExtra(String name, long defaultValue) {
        return mExtras == null ? defaultValue :
            mExtras.getLong(name, defaultValue);
    }

    /**
     * Retrieve extended data from the intent.
     *
     * @param name The name of the desired item.
     * @param defaultValue the value to be returned if no value of the desired
     * type is stored with the given name.
     *
     * @return the value of an item that previously added with putExtra(),
     * or the default value if no such item is present
     *
     * @see #putExtra(String, float)
     */
    public float getFloatExtra(String name, float defaultValue) {
        return mExtras == null ? defaultValue :
            mExtras.getFloat(name, defaultValue);
    }

    /**
     * Retrieve extended data from the intent.
     *
     * @param name The name of the desired item.
     * @param defaultValue the value to be returned if no value of the desired
     * type is stored with the given name.
     *
     * @return the value of an item that previously added with putExtra()
     * or the default value if none was found.
     *
     * @see #putExtra(String, double)
     */
    public double getDoubleExtra(String name, double defaultValue) {
        return mExtras == null ? defaultValue :
            mExtras.getDouble(name, defaultValue);
    }

    /**
     * Retrieve extended data from the intent.
     *
     * @param name The name of the desired item.
     *
     * @return the value of an item that previously added with putExtra()
     * or null if no String value was found.
     *
     * @see #putExtra(String, String)
     */
    public String getStringExtra(String name) {
        return mExtras == null ? null : mExtras.getString(name);
    }

    /**
     * Retrieve extended data from the intent.
     *
     * @param name The name of the desired item.
     *
     * @return the value of an item that previously added with putExtra()
     * or null if no ArrayList<String> value was found.
     *
     * @see #putStringArrayListExtra(String, ArrayList)
     */
    public ArrayList<String> getStringArrayListExtra(String name) {
        return mExtras == null ? null : mExtras.getStringArrayList(name);
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated the key.
     *
     * @param name The name of the desired item.
     *
     * @return the value of on item that previously added with putExtra()
     * or null if no Bundle value was found.
     *
     * @see #putExtra(String, Bundle)
     */
    public Bundle getBundleExtra(String name) {
        return mExtras == null ? null : mExtras.getBundle(name);
    }

    /**
     * Add a Bundle value into the mapping of this BundleData, replacing
     * any existing value for the given key.
     *
     * @param name The name of the extra data, with package prefix.
     * @param value The Bundle value
     *
     * @return Returns the same BundleData object.
     *
     * @see #getBundleExtra(String);
     */
    public @NonNull BundleData putExtra(String name, Bundle value) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putBundle(name, value);
        return this;
    }

    /**
     * Add extended data to the intent.  The name must include a package
     * prefix, for example the app com.android.contacts would use names
     * like "com.android.contacts.ShowAll".
     *
     * @param name The name of the extra data, with package prefix.
     * @param value The boolean data value.
     *
     * @return Returns the same Intent object, for chaining multiple calls
     * into a single statement.
     *
     * @see #putExtras
     * @see #removeExtra
     * @see #getBooleanExtra(String, boolean)
     */
    public @NonNull BundleData putExtra(String name, boolean value) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putBoolean(name, value);
        return this;
    }

    /**
     * Add extended data to the intent.  The name must include a package
     * prefix, for example the app com.android.contacts would use names
     * like "com.android.contacts.ShowAll".
     *
     * @param name The name of the extra data, with package prefix.
     * @param value The byte data value.
     *
     * @return Returns the same Intent object, for chaining multiple calls
     * into a single statement.
     *
     * @see #putExtras
     * @see #removeExtra
     * @see #getByteExtra(String, byte)
     */
    public @NonNull BundleData putExtra(String name, byte value) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putByte(name, value);
        return this;
    }

    /**
     * Add extended data to the intent.  The name must include a package
     * prefix, for example the app com.android.contacts would use names
     * like "com.android.contacts.ShowAll".
     *
     * @param name The name of the extra data, with package prefix.
     * @param value The char data value.
     *
     * @return Returns the same Intent object, for chaining multiple calls
     * into a single statement.
     *
     * @see #putExtras
     * @see #removeExtra
     * @see #getCharExtra(String, char)
     */
    public @NonNull BundleData putExtra(String name, char value) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putChar(name, value);
        return this;
    }

    /**
     * Add extended data to the intent.  The name must include a package
     * prefix, for example the app com.android.contacts would use names
     * like "com.android.contacts.ShowAll".
     *
     * @param name The name of the extra data, with package prefix.
     * @param value The short data value.
     *
     * @return Returns the same Intent object, for chaining multiple calls
     * into a single statement.
     *
     * @see #putExtras
     * @see #removeExtra
     * @see #getShortExtra(String, short)
     */
    public @NonNull BundleData putExtra(String name, short value) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putShort(name, value);
        return this;
    }

    /**
     * Add extended data to the intent.  The name must include a package
     * prefix, for example the app com.android.contacts would use names
     * like "com.android.contacts.ShowAll".
     *
     * @param name The name of the extra data, with package prefix.
     * @param value The integer data value.
     *
     * @return Returns the same Intent object, for chaining multiple calls
     * into a single statement.
     *
     * @see #putExtras
     * @see #removeExtra
     * @see #getIntExtra(String, int)
     */
    public @NonNull BundleData putExtra(String name, int value) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putInt(name, value);
        return this;
    }

    /**
     * Add extended data to the intent.  The name must include a package
     * prefix, for example the app com.android.contacts would use names
     * like "com.android.contacts.ShowAll".
     *
     * @param name The name of the extra data, with package prefix.
     * @param value The long data value.
     *
     * @return Returns the same Intent object, for chaining multiple calls
     * into a single statement.
     *
     * @see #putExtras
     * @see #removeExtra
     * @see #getLongExtra(String, long)
     */
    public @NonNull BundleData putExtra(String name, long value) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putLong(name, value);
        return this;
    }

    /**
     * Add extended data to the intent.  The name must include a package
     * prefix, for example the app com.android.contacts would use names
     * like "com.android.contacts.ShowAll".
     *
     * @param name The name of the extra data, with package prefix.
     * @param value The float data value.
     *
     * @return Returns the same Intent object, for chaining multiple calls
     * into a single statement.
     *
     * @see #putExtras
     * @see #removeExtra
     * @see #getFloatExtra(String, float)
     */
    public @NonNull BundleData putExtra(String name, float value) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putFloat(name, value);
        return this;
    }

    /**
     * Add extended data to the intent.  The name must include a package
     * prefix, for example the app com.android.contacts would use names
     * like "com.android.contacts.ShowAll".
     *
     * @param name The name of the extra data, with package prefix.
     * @param value The double data value.
     *
     * @return Returns the same Intent object, for chaining multiple calls
     * into a single statement.
     *
     * @see #putExtras
     * @see #removeExtra
     * @see #getDoubleExtra(String, double)
     */
    public @NonNull BundleData putExtra(String name, double value) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putDouble(name, value);
        return this;
    }

    /**
     * Add extended data to the intent.  The name must include a package
     * prefix, for example the app com.android.contacts would use names
     * like "com.android.contacts.ShowAll".
     *
     * @param name The name of the extra data, with package prefix.
     * @param value The String data value.
     *
     * @return Returns the same Intent object, for chaining multiple calls
     * into a single statement.
     *
     * @see #putExtras
     * @see #removeExtra
     * @see #getStringExtra(String)
     */
    public @NonNull BundleData putExtra(String name, String value) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putString(name, value);
        return this;
    }

    public void putExtra(String name, ArrayMap map) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }

        Bundle bundle = new Bundle();
        bundle.putAll(map);
        mExtras.putBundle(name, bundle);
    }

    /**
     * Add extended data to the intent.  The name must include a package
     * prefix, for example the app com.android.contacts would use names
     * like "com.android.contacts.ShowAll".
     *
     * @param name The name of the extra data, with package prefix.
     * @param value The ArrayList<String> data value.
     *
     * @return Returns the same Intent object, for chaining multiple calls
     * into a single statement.
     *
     * @see #putExtras
     * @see #removeExtra
     * @see #getStringArrayListExtra(String)
     */
    public @NonNull BundleData putStringArrayListExtra(String name, ArrayList<String> value) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putStringArrayList(name, value);
        return this;
    }

    private String typeToString(int type) {
        final String str[] = {
            "PROCESS_STATE",
            "APP_STATE_EVENT",
            "APP_TRANSITION",
            "BATTERY_EVENT",
            "DEV_STATUS",
            "INPUT",
            "DATA_TYPE_APP_VIDEO",
            "DATA_TYPE_SYSTEM_EVENT",
        };
        if ((0 <= type) && (type < str.length))
            return str[type];
        else
            return "Unknown Type: " + type;
    }

    private String subTypeToString(int subType) {
        final String str[] = {
            "NONE",
            "AUDIO",
            "VIDEO",
            "SENSOR",
            "GPS",
            "SCREEN",
            "BATTERY",
            "NETWORK"
        };
        if ((0 <= subType) && (subType < str.length))
            return str[subType];
        else
            return "Unknown subType: " + subType;
    }

    private String stateToString(int state) {
        final String str[] = {
            "STOP",
            "START"
        };
        if ((0 <= state) && (state < str.length))
            return str[state];
        else
            return "Unknown state: " + state;
    }

    private String touchEventToString(int touchEvent) {
        final String str[] = {
            "DOWN",
            "UP",
            "SINGLE_TAP",
            "DOUBLE_TAP",
            "LONG_PRESS",
            "SCROLL",
            "FLING"
        };
        if ((0 <= touchEvent) && (touchEvent < str.length))
            return str[touchEvent];
        else
            return "Unknown touchEvent: " + touchEvent;
    }

    private String sensorTypeToString(int sensor) {
        final String str[] = {
            "NONE",
            "TYPE_ACCELEROMETER",
            "TYPE_MAGNETIC_FIELD",
            "TYPE_ORIENTATION",
            "TYPE_GYROSCOPE",
            "TYPE_LIGHT",
            "TYPE_PRESSURE",
            "TYPE_TEMPERATURE",
            "TYPE_PROXIMITY",
            "TYPE_GRAVITY",
            "TYPE_LINEAR_ACCELERATION",
            "TYPE_ROTATION_VECTOR",
            "TYPE_RELATIVE_HUMIDITY",
            "TYPE_AMBIENT_TEMPERATURE",
            "TYPE_MAGNETIC_FIELD_UNCALIBRATED",
            "TYPE_GAME_ROTATION_VECTOR",
            "TYPE_GYROSCOPE_UNCALIBRATED",
            "TYPE_SIGNIFICANT_MOTION",
            "TYPE_STEP_DETECTOR",
            "TYPE_STEP_COUNTER",
            "TYPE_GEOMAGNETIC_ROTATION_VECTOR",
            "TYPE_HEART_RATE",
            "TYPE_TILT_DETECTOR",
            "TYPE_WAKE_GESTURE",
            "TYPE_GLANCE_GESTURE",
            "TYPE_PICK_UP_GESTURE",
            "TYPE_WRIST_TILT_GESTURE",
            "TYPE_DEVICE_ORIENTATION",
            "TYPE_POSE_6DOF",
            "TYPE_STATIONARY_DETECT",
            "TYPE_MOTION_DETECT",
            "TYPE_HEART_BEAT",
            "TYPE_DYNAMIC_SENSOR_META",
            "TYPE_ADDITIONAL_INFO",
            "TYPE_LOW_LATENCY_OFFBODY_DETECT",
            "TYPE_ACCELEROMETER_UNCALIBRATED"
        };
        if ((0 <= sensor) && (sensor < str.length))
            return str[sensor];
        else
            return "Unknown sensor: " + sensor;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(TAG);

        switch (mType) {
            case DATA_TYPE_PROCESS_STATE:
                int procState = getIntExtra(DATA_EXTRA_PROCESS_STATE, -1);
                int uid = getIntExtra(DATA_EXTRA_UID, -1);
                String packName = getStringExtra(DATA_EXTRA_PACKAGENAME);

                result.append("{ type: " + typeToString(mType)
                    + ", packageName: " + packName
                    + ", uid: "  + uid
                    + ", processState: " + Util.ProcState2Str(procState) + " }");

                break;
            case DATA_TYPE_APP_STATE_EVENT:
                int stateEvent = getIntExtra(DATA_EXTRA_APP_STATE_EVENT, 0);
                uid = getIntExtra(DATA_EXTRA_UID, -1);
                packName = getStringExtra(DATA_EXTRA_PACKAGENAME);

                result.append("{ type: " + typeToString(mType)
                    + ", packageName: " + packName
                    + ", uid: "  + uid
                    + ", stateEvent: " + Util.AppState2Str(stateEvent) + " }");

                break;
            case DATA_TYPE_DEV_STATUS:
                int subType = getIntExtra(DATA_EXTRA_SUBTYPE, -1);

                if (subType == DATA_SUBTYPE_SCREEN) {
                    boolean screenOn = getBooleanExtra(DATA_EXTRA_SCREEN_ON, false);
                    result.append("{ type: " + typeToString(mType)
                        + ", subType: " + subTypeToString(subType)
                        + ", screenOn: "  + screenOn + " }");
                } else if (subType == DATA_SUBTYPE_BATTERY) {
                    boolean plugged = getBooleanExtra(DATA_EXTRA_BATTERY_PLUGGED, false);

                    result.append("{ type: " + typeToString(mType)
                        + ", subType: " + subTypeToString(subType)
                        + ", plugged: "  + plugged + " }");
                 } else {
                    result.append("{ type: " + typeToString(mType)
                        + ", subType: " + subTypeToString(subType) + " }");
                }
                break;
            case DATA_TYPE_APP_TRANSITION:
                ArrayList<String> appList = getStringArrayListExtra(DATA_EXTRA_VISIBLE_APPS);
                result.append("{ type: " + typeToString(mType)
                    + ", visiable apps:" );

                if (appList != null) {
                    for (int i=0;i<appList.size();i++) {
                        result.append(" " + appList.get(i));
                    }
                }
                result.append(" }");

                break;
            case DATA_TYPE_BATTERY_EVENT:
                int state = getIntExtra(DATA_EXTRA_STATE, -1);
                subType = getIntExtra(DATA_EXTRA_SUBTYPE, -1);
                result.append("{ type: " + typeToString(mType)
                    + ", subType: " + subTypeToString(subType)
                    + ", state: " + stateToString(state));

                uid = getIntExtra(DATA_EXTRA_UID, -1);
                if (uid != -1) {
                    packName = getStringExtra(DATA_EXTRA_PACKAGENAME);
                    result.append(", packageName: " + packName
                        + ", uid: "  + uid);
                }

                if (subType == DATA_SUBTYPE_SENSOR) {
                    int sensorType = getIntExtra(DATA_EXTRA_SENSOR, -1);
                    result.append(", sensorType: " + sensorTypeToString(sensorType));
                }

                result.append(" }");

                break;

            case DATA_TYPE_INPUT:
                int event = getIntExtra(DATA_EXTRA_TOUCH_EVENT, -1);
                result.append("{ type: " + typeToString(mType)
                    + ", touchEvent: " + touchEventToString(event) + " }");
                break;

            default:
                result.append("{ type: " + typeToString(mType) + " }");
                break;
            }

        return result.toString();
    }

}
