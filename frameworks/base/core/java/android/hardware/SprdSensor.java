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

package android.hardware;

/**
 * SPRD: add some new sensor type
 *
 * @hide
 */

public final class SprdSensor {

    private static final int SPRD_SENSOR_TYPE_BASE = 0x10000;

    /**
     * A constant describing a shake sensor.
     *
     * A sensor of this type triggers when the device is shaken.
     * This sensor deactivates itself immediately after it triggers.
     *
     * @hide This sensor is expected to only be used by the System Application.
     */
    public static final int TYPE_SPRDHUB_SHAKE = SPRD_SENSOR_TYPE_BASE + 1;

    /**
     * A constant string describing a shake sensor.
     *
     * @hide
     */
    public static final String STRING_TYPE_SPRDHUB_SHAKE = "com.spreadtrum.shake";

    /**
     * A constant describing a pocket-mode sensor.
     *
     * A sensor of this type triggers when the device is put into a pocket.
     * The only allowed return value is 1.0. This sensor deactivates
     * itself immediately after it triggers.
     *
     * @hide This sensor is expected to only be used by the System Application.
     */
    public static final int TYPE_SPRDHUB_POCKET_MODE = SPRD_SENSOR_TYPE_BASE + 2;

    /**
     * A constant string describing a pocket-mode sensor.
     *
     * @hide
     */
    public static final String STRING_TYPE_SPRDHUB_POCKET_MODE = "com.spreadtrum.pocket_mode";

    /**
     * A constant describing a tap sensor.
     *
     * A sensor of this type triggers when the device is tapped single or twice.
     * This sensor deactivates itself immediately after it triggers.
     *
     * @hide Expected to be used by system core service.
     */
    public static final int TYPE_SPRDHUB_TAP = SPRD_SENSOR_TYPE_BASE + 3;

    /**
     * A constant string describing a tap sensor.
     *
     * @hide
     */
    public static final String STRING_TYPE_SPRDHUB_TAP = "com.spreadtrum.tap";

    /**
     * A constant describing a face-up/face-down sensor.
     *
     * A sensor of this type triggers when the device is faced up or faced down.
     * This sensor deactivates itself immediately after it triggers.
     *
     * @hide Expected to be used by System Application.
     */
    public static final int TYPE_SPRDHUB_FACE_UP_DOWN = SPRD_SENSOR_TYPE_BASE + 4;

    /**
     * A constant string describing a face-up/face-down sensor.
     *
     * @hide
     */
    public static final String STRING_TYPE_SPRDHUB_FACE_UP_DOWN = "com.spreadtrum.face_up_down";

    /**
     * A constant describing a flip sensor.
     *
     * A sensor of this type triggers when the device is fliped.
     * reporting-mode: on_change.
     *
     * @hide Expected to be used by System Application.
     */
    public static final int TYPE_SPRDHUB_FLIP = SPRD_SENSOR_TYPE_BASE + 10;

    /**
     * A constant string describing a flip sensor.
     *
     * @hide
     */
    public static final String STRING_TYPE_SPRDHUB_FLIP =  "com.spreadtrum.flip";

    /**
     * A constant describing a hand-up sensor.
     *
     * A sensor of this type triggers when the device is handed up
     * reporting mode: on_change
     *
     * @hide Expected to be used by System Application.
     */
    public static final int TYPE_SPRDHUB_HAND_UP = SPRD_SENSOR_TYPE_BASE + 12;

    /**
     * A constant string describing a hand-up sensor.
     *
     * @hide
     */
    public static final String STRING_TYPE_SPRDHUB_HAND_UP =   "com.spreadtrum.hand_up";

    /**
     * A constant describing a hand-down sensor.
     *
     * A sensor of this type triggers when the device is handed down
     * reporting mode: on_change
     *
     * @hide Expected to be used by System Application.
     */
    public static final int TYPE_SPRDHUB_HAND_DOWN = SPRD_SENSOR_TYPE_BASE + 13;

    /**
     * A constant string describing a hand-down sensor.
     *
     * @hide
     */
    public static final String STRING_TYPE_SPRDHUB_HAND_DOWN = "com.spreadtrum.hand_down";

}

