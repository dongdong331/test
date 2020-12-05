package android.os.sprdpower;

import android.content.Context;

//import android.os.BatteryStatsi;
import android.os.BatteryStats.LevelStepTracker;
import android.os.Parcel;
//import android.os.PowerManagerEx;
import android.os.SystemClock;
import android.os.SystemProperties;
//import android.os.UserHandle;
//import android.util.ArrayMap;
//import android.util.SparseArray;
import android.util.Slog;
//import java.util.ArrayList;

/**
 * @hide
 */
public class PowerControllerBatteryStatsHelper {
    static final String TAG = "PowerController.BattStats";

    private boolean DEBUG = true;
    private boolean DEBUG_MORE = false;

    private static PowerControllerBatteryStatsHelper sInstance;


    private final boolean mPowerControllerEnabled =
            (1 == SystemProperties.getInt(PowerManagerEx.POWER_CONTROLLER_ENABLE, 1));

    static final int MAX_LEVEL_STEPS = 200;

    /**
     * The defaut discharge rate is :
     * lowPowerMode = (1000/1100) * normalPowerMode
     * ultraPowerMode = (1000/1200) * normalPowerMode
     */
    private final long[] mFactorForModes = new long[] {1000/**/,
        1000/*MODE_SMART*/,
        1000/**/,
        1100/*MODE_LOWPOWER*/,
        1200/*MODE_ULTRASAVING*/,
        1000,
        1000};


    final LevelStepTracker mLowModeDischargeStepTracker = new LevelStepTracker(MAX_LEVEL_STEPS);
    final LevelStepTracker mNormalModeDischargeStepTracker = new LevelStepTracker(MAX_LEVEL_STEPS);
    final LevelStepTracker mUltraModeDischargeStepTracker = new LevelStepTracker(MAX_LEVEL_STEPS);

    private long mLastLevelStepTime;
    private long mLastLowPowerModeTime;
    private long mLastNormalPowerModeTime;
    private long mLastUltraSavingPowerModeTime;

    private long[] mDurationSpentInModes = new long[6];


    private int mCurrentMode = PowerManagerEx.MODE_SMART;
    private int mPreMode = PowerManagerEx.MODE_SMART;

    // used internally for synchronization
    private final Object mLock = new Object();

    public PowerControllerBatteryStatsHelper() {
        mLastLowPowerModeTime = 0;
        mLastNormalPowerModeTime = 0;
        mLastUltraSavingPowerModeTime = 0;
    }

    public static PowerControllerBatteryStatsHelper getInstance() {
        synchronized (PowerControllerBatteryStatsHelper.class) {
            if (sInstance == null) {
                sInstance = new PowerControllerBatteryStatsHelper();
            }
            return sInstance;
        }
    }

    public void init() {
        mLastLevelStepTime = -1;
        mLowModeDischargeStepTracker.init();
        mNormalModeDischargeStepTracker.init();
        mUltraModeDischargeStepTracker.init();
    }

    public void clearTime() {
        mLastLevelStepTime = -1;
        mLowModeDischargeStepTracker.clearTime();
        mNormalModeDischargeStepTracker.clearTime();
        mUltraModeDischargeStepTracker.clearTime();
    }

    public long computeTimePerLevel(long timePerLevel) {
        if (!mPowerControllerEnabled) return timePerLevel;

        int mode = mCurrentMode;

        long timePerLevelForLowMode = getTimePerLevel(PowerManagerEx.MODE_LOWPOWER);
        long timePerLevelForNormalMode = getTimePerLevel(PowerManagerEx.MODE_SMART);
        long timePerLevelForUltraMode = getTimePerLevel(PowerManagerEx.MODE_ULTRASAVING);
        long timePerLevelForCurrent = getTimePerLevel(mode);

        Slog.d(TAG, "timePerLevelForUltraMode:" + timePerLevelForUltraMode
                + ", timePerLevelForLowMode:" + timePerLevelForLowMode
                + ", timePerLevelForNormalMode:" + timePerLevelForNormalMode
                + ", timePerLevelForCurrent:" + timePerLevelForCurrent
                + ", timePerLevel:" + timePerLevel);

        if (timePerLevelForCurrent > 0) {
            Slog.d(TAG, "new timePerLevel for mode:" + mode
                    + " is:" + timePerLevelForCurrent
                    + ", timePerLevel:" + timePerLevel);
            return timePerLevelForCurrent;
        }

        if (timePerLevelForLowMode < 0 && timePerLevelForNormalMode < 0
                && timePerLevelForUltraMode < 0) {
            Slog.d(TAG, "new timePerLevel for mode:" + mode
                    + " is:" + timePerLevel
                    + ", timePerLevel:" + timePerLevel);
            return timePerLevel;
        }

        long newTimePerLevel = timePerLevel;
/**
        if (PowerManagerEx.MODE_LOWPOWER == mode) {
            if (timePerLevelForUltraMode < 0) {
                newTimePerLevel = (timePerLevelForNormalMode * 1100)/1000;
            } else if (timePerLevelForUltraMode > 0 && timePerLevelForNormalMode < 0) {
                newTimePerLevel = (timePerLevelForUltraMode * 916)/1000;
            } else {
                newTimePerLevel = (timePerLevelForNormalMode + timePerLevelForUltraMode) / 2;
            }
        } else if (PowerManagerEx.MODE_ULTRASAVING == mode) {
            if (timePerLevelForLowMode < 0) {
                newTimePerLevel = (timePerLevelForNormalMode * 1200)/1000;
            } else if (timePerLevelForLowMode > 0 && timePerLevelForNormalMode < 0) {
                newTimePerLevel = (timePerLevelForLowMode * 1090)/1000;
            } else {
                newTimePerLevel = (timePerLevelForLowMode + timePerLevelForNormalMode) *1140 / 2000;
            }
        } else if (PowerManagerEx.MODE_SMART == mode) {
            if (timePerLevelForLowMode < 0) {
                newTimePerLevel = (timePerLevelForUltraMode * 830)/1000;
            } else if (timePerLevelForLowMode > 0 && timePerLevelForUltraMode < 0) {
                newTimePerLevel = (timePerLevelForLowMode * 909)/1000;
            } else {
                newTimePerLevel = (timePerLevelForLowMode + timePerLevelForUltraMode) *870 / 2000;
            }
        }
*/
        Slog.d(TAG, "new timePerLevel for mode:" + mode
                + " is:" + newTimePerLevel
                + ", timePerLevel:" + timePerLevel);

        return newTimePerLevel;
    }

    public void addLevelSteps(int numStepLevels, long modeBits, long elapsedRealtime) {

        long now = SystemClock.elapsedRealtime();
        Slog.d(TAG, "now:" + now + ", elapsedRealtime:"
                + elapsedRealtime + ", numStepLevels:" + numStepLevels
                + ", mLastLevelStepTime:" + mLastLevelStepTime);

        if (mLastLevelStepTime < 0) {
            mLastLevelStepTime = elapsedRealtime;
            LevelStepTracker stepTracker = getLevelStepTracker(mCurrentMode);
            if (stepTracker == null) return;

            if (stepTracker.mLastStepTime < 0) {
                stepTracker.addLevelSteps(numStepLevels, modeBits, elapsedRealtime);
                Slog.d(TAG, "addLevelSteps: first level update: for mode:" + mCurrentMode
                        + ", mNumStepDurations:" + stepTracker.mNumStepDurations
                        + ", timePerLevel:" + stepTracker.computeTimePerLevel());

                updateLevelStepsForOtherMode(numStepLevels, modeBits,
                        elapsedRealtime, mLastLevelStepTime);
            }

            return;
        }
        // should before mLastLevelStepTime is updated
        updateDurationSpentInMode(mCurrentMode, elapsedRealtime);

        long lastLevelStepTime = mLastLevelStepTime;
        mLastLevelStepTime = elapsedRealtime;


        long timeForLowMode = getTimeSpentInMode(PowerManagerEx.MODE_LOWPOWER,
                lastLevelStepTime, elapsedRealtime);
        long timeForNormalMode = getTimeSpentInMode(PowerManagerEx.MODE_SMART,
                lastLevelStepTime, elapsedRealtime);
        long timeForUltraMode = getTimeSpentInMode(PowerManagerEx.MODE_ULTRASAVING,
                lastLevelStepTime, elapsedRealtime);
        long timeForCurrentMode = getTimeSpentInMode(mCurrentMode,
                lastLevelStepTime, elapsedRealtime);

        Slog.d(TAG, "timeForLowMode:" + timeForLowMode
                + ", timeForNormalMode:" + timeForNormalMode
                + ", timeForUltraMode:" + timeForUltraMode
                + ", timeForCurrentMode:" + timeForCurrentMode
                + ", ((elapsedRealtime - lastLevelStepTime) *900/1000):"
                + ((elapsedRealtime - lastLevelStepTime) * 900 / 1000));


        clearDurationSpentInAllMode();

        LevelStepTracker dischargeStepTracker = getLevelStepTracker(mCurrentMode);
        if (dischargeStepTracker == null) return;

        if (timeForCurrentMode >= ((elapsedRealtime - lastLevelStepTime) * 900 / 1000)
                || (timeForCurrentMode > 0 && dischargeStepTracker.mLastStepTime < 0)) {
            dischargeStepTracker.addLevelSteps(numStepLevels, modeBits, elapsedRealtime);
            Slog.d(TAG, "addLevelSteps: for mode:" + mCurrentMode
                    + ", mNumStepDurations:" + dischargeStepTracker.mNumStepDurations
                    + ", timePerLevel:" + dischargeStepTracker.computeTimePerLevel());

            updateLevelStepsForOtherMode(numStepLevels, modeBits,
                    elapsedRealtime, lastLevelStepTime);
        } else {
            updateLevelStepsForAllMode(numStepLevels, modeBits, elapsedRealtime);
        }
    }

    public void updatePowerMode(int newMode) {
        //if (mCurrentMode == newMode) return;

        long now = SystemClock.elapsedRealtime();

        Slog.d(TAG, "updatePowerMode: new mode:" + newMode
                + ", now:" + now);

        mPreMode = mCurrentMode;
        mCurrentMode = newMode;

        if (PowerManagerEx.MODE_LOWPOWER == mCurrentMode) {
            mLastLowPowerModeTime = now;
        } else if (PowerManagerEx.MODE_ULTRASAVING == mCurrentMode) {
            mLastUltraSavingPowerModeTime = now;
        } else if (PowerManagerEx.MODE_SMART == mCurrentMode) {
            mLastNormalPowerModeTime = now;
        }

        updateDurationSpentInMode(mPreMode, now);
    }

    public void readFromParcel(Parcel in) {
        mPreMode = in.readInt();
        mCurrentMode = in.readInt();
        mLowModeDischargeStepTracker.readFromParcel(in);
        mNormalModeDischargeStepTracker.readFromParcel(in);
        mUltraModeDischargeStepTracker.readFromParcel(in);


        if (DEBUG_MORE) {
            Slog.d(TAG, "readFromParcel:"
                    + " mLowModeDischargeStepTracker.mNumStepDurations:"
                    + mLowModeDischargeStepTracker.mNumStepDurations
                    + ", mNormalModeDischargeStepTracker.mNumStepDurations:"
                    + mNormalModeDischargeStepTracker.mNumStepDurations
                    + ", mUltraModeDischargeStepTracker.mNumStepDurations:"
                    + mUltraModeDischargeStepTracker.mNumStepDurations);
        }
    }

    public void writeToParcel(Parcel out) {
        out.writeInt(mPreMode);
        out.writeInt(mCurrentMode);
        mLowModeDischargeStepTracker.writeToParcel(out);
        mNormalModeDischargeStepTracker.writeToParcel(out);
        mUltraModeDischargeStepTracker.writeToParcel(out);

        if (DEBUG_MORE) {
            Slog.d(TAG, "writeToParcel:"
                    + "mLowModeDischargeStepTracker.mNumStepDurations:"
                    + mLowModeDischargeStepTracker.mNumStepDurations
                    + ", mNormalModeDischargeStepTracker.mNumStepDurations:"
                    + mNormalModeDischargeStepTracker.mNumStepDurations
                    + ", mUltraModeDischargeStepTracker.mNumStepDurations:"
                    + mUltraModeDischargeStepTracker.mNumStepDurations);
        }
    }


    public void dumpTimePerLevel() {
        if (DEBUG) {
            Slog.d(TAG, "dumpTimePerLevel:"
                    + "mLowModeDischargeStepTracker.mNumStepDurations:"
                    + mLowModeDischargeStepTracker.mNumStepDurations
                    + " timePerLevel for mLowModeDischargeStepTracker: "
                    + mLowModeDischargeStepTracker.computeTimePerLevel()
                    + ", mNormalModeDischargeStepTracker.mNumStepDurations:"
                    + mNormalModeDischargeStepTracker.mNumStepDurations
                    + " timePerLevel for mNormalModeDischargeStepTracker: "
                    + mNormalModeDischargeStepTracker.computeTimePerLevel()
                    + ", mUltraModeDischargeStepTracker.mNumStepDurations:"
                    + mUltraModeDischargeStepTracker.mNumStepDurations
                    + " timePerLevel for mUltraModeDischargeStepTracker: "
                    + mUltraModeDischargeStepTracker.computeTimePerLevel());
        }
    }

    private long getTimePerLevel(int mode) {
        LevelStepTracker dischargeStepTracker = getLevelStepTracker(mode);

        if (DEBUG_MORE) {
            Slog.d(TAG, "getTimePerLevel: for mode:" + mode
                    + ", dischargeStepTracker:" + dischargeStepTracker);
        }

        if (dischargeStepTracker == null) {
            return -1;
        }

        if (DEBUG_MORE) {
            Slog.d(TAG, "getTimePerLevel: for mode:" + mode
                    + ", mNumStepDurations:" + dischargeStepTracker.mNumStepDurations);
        }

        if (dischargeStepTracker.mNumStepDurations < 1) {
            return -1;
        }

        long msPerLevel = dischargeStepTracker.computeTimePerLevel();
        if (msPerLevel <= 0) {
            return -1;
        }
        return msPerLevel;
    }


    private long getLastModeTime(int mode) {
        long lastModeTime = 0;

        if (PowerManagerEx.MODE_SMART == mode) {
            lastModeTime = mLastNormalPowerModeTime;
        } else if (PowerManagerEx.MODE_LOWPOWER == mode) {
            lastModeTime = mLastLowPowerModeTime;
        } else if (PowerManagerEx.MODE_ULTRASAVING == mode) {
            lastModeTime = mLastUltraSavingPowerModeTime;
        }
        return lastModeTime;
    }

    private LevelStepTracker getLevelStepTracker(int mode) {
        LevelStepTracker dischargeStepTracker = null;

        if (PowerManagerEx.MODE_SMART == mode) {
            dischargeStepTracker = mNormalModeDischargeStepTracker;
        } else if (PowerManagerEx.MODE_LOWPOWER == mode) {
            dischargeStepTracker = mLowModeDischargeStepTracker;
        } else if (PowerManagerEx.MODE_ULTRASAVING == mode) {
            dischargeStepTracker = mUltraModeDischargeStepTracker;
        }

        return dischargeStepTracker;
    }

    private long getTimeSpentInMode(int mode, long lastStepLevelTime, long now) {

        if (mode < 6 && mode >= 0) {
            return mDurationSpentInModes[mode];
        }

        long modeTime = getLastModeTime(mode);

        if (modeTime <= 0) {
            return 0;
        }

        if (mode == mCurrentMode) {
            if (lastStepLevelTime > modeTime) {
                return (now - lastStepLevelTime);
            } else {
                return (now - modeTime);
            }
        } else if (mode == mPreMode) {
            long currentModeTime = getLastModeTime(mCurrentMode);
            if (lastStepLevelTime > modeTime) {
                return (lastStepLevelTime > currentModeTime ? 0
                    : (currentModeTime - lastStepLevelTime));
            } else {
                return (currentModeTime - modeTime);
            }
        } else {
            long preModeTime = getLastModeTime(mPreMode);
            if (lastStepLevelTime > modeTime) {
                return (lastStepLevelTime > preModeTime ? 0
                    : (preModeTime - lastStepLevelTime));
            } else {
                return (preModeTime - modeTime);
            }
        }
    }

    private void updateDurationSpentInMode(int mode, long now) {
        if (mode >= 6 || mode < 0) return;

        long modeTime = getLastModeTime(mode);

        if (modeTime <= mLastLevelStepTime) {
            mDurationSpentInModes[mode] += (now - mLastLevelStepTime);
        } else {
            mDurationSpentInModes[mode] += (now - modeTime);
        }
    }

    private void clearDurationSpentInAllMode() {
        for (int i = mDurationSpentInModes.length - 1; i >= 0; i--) {
            mDurationSpentInModes[i] = 0;
        }
    }

    private void updateLevelStepsForOtherMode(int numStepLevels, long modeBits,
            long elapsedRealtime, long lastLevelStepTime) {

        long newElapsedRealtime = elapsedRealtime;

        if (mCurrentMode == PowerManagerEx.MODE_LOWPOWER) {

            newElapsedRealtime =
                (elapsedRealtime * mFactorForModes[PowerManagerEx.MODE_SMART])
                / mFactorForModes[PowerManagerEx.MODE_LOWPOWER]
                + (lastLevelStepTime * (mFactorForModes[PowerManagerEx.MODE_LOWPOWER]
                - mFactorForModes[PowerManagerEx.MODE_SMART]))
                / mFactorForModes[PowerManagerEx.MODE_LOWPOWER];

            mNormalModeDischargeStepTracker.addLevelSteps(numStepLevels,
                    modeBits, newElapsedRealtime);
            // correct mLastStepTime
            mNormalModeDischargeStepTracker.mLastStepTime = elapsedRealtime;

            newElapsedRealtime =
                (elapsedRealtime * mFactorForModes[PowerManagerEx.MODE_ULTRASAVING])
                / mFactorForModes[PowerManagerEx.MODE_LOWPOWER]
                + (lastLevelStepTime * (mFactorForModes[PowerManagerEx.MODE_LOWPOWER]
                - mFactorForModes[PowerManagerEx.MODE_ULTRASAVING]))
                / mFactorForModes[PowerManagerEx.MODE_LOWPOWER];

            mUltraModeDischargeStepTracker.addLevelSteps(numStepLevels,
                    modeBits, newElapsedRealtime);
            // correct mLastStepTime
            mUltraModeDischargeStepTracker.mLastStepTime = elapsedRealtime;

        } else if (mCurrentMode == PowerManagerEx.MODE_SMART) {

            newElapsedRealtime =
                (elapsedRealtime * mFactorForModes[PowerManagerEx.MODE_LOWPOWER])
                / mFactorForModes[PowerManagerEx.MODE_SMART]
                + (lastLevelStepTime * (mFactorForModes[PowerManagerEx.MODE_SMART]
                - mFactorForModes[PowerManagerEx.MODE_LOWPOWER]))
                / mFactorForModes[PowerManagerEx.MODE_SMART];

            mLowModeDischargeStepTracker.addLevelSteps(numStepLevels,
                    modeBits, newElapsedRealtime);
            // correct mLastStepTime
            mLowModeDischargeStepTracker.mLastStepTime = elapsedRealtime;

            newElapsedRealtime =
                (elapsedRealtime * mFactorForModes[PowerManagerEx.MODE_ULTRASAVING])
                / mFactorForModes[PowerManagerEx.MODE_SMART]
                + (lastLevelStepTime * (mFactorForModes[PowerManagerEx.MODE_SMART]
                - mFactorForModes[PowerManagerEx.MODE_ULTRASAVING]))
                / mFactorForModes[PowerManagerEx.MODE_SMART];

            mUltraModeDischargeStepTracker.addLevelSteps(numStepLevels,
                    modeBits, newElapsedRealtime);
            // correct mLastStepTime
            mUltraModeDischargeStepTracker.mLastStepTime = elapsedRealtime;

        } else if (mCurrentMode == PowerManagerEx.MODE_ULTRASAVING) {

            newElapsedRealtime =
                (elapsedRealtime * mFactorForModes[PowerManagerEx.MODE_SMART])
                / mFactorForModes[PowerManagerEx.MODE_ULTRASAVING]
                + (lastLevelStepTime * (mFactorForModes[PowerManagerEx.MODE_ULTRASAVING]
                - mFactorForModes[PowerManagerEx.MODE_SMART]))
                / mFactorForModes[PowerManagerEx.MODE_ULTRASAVING];

            mNormalModeDischargeStepTracker.addLevelSteps(numStepLevels,
                    modeBits, newElapsedRealtime);
            // correct mLastStepTime
            mNormalModeDischargeStepTracker.mLastStepTime = elapsedRealtime;

            newElapsedRealtime =
                (elapsedRealtime * mFactorForModes[PowerManagerEx.MODE_LOWPOWER])
                / mFactorForModes[PowerManagerEx.MODE_ULTRASAVING]
                + (lastLevelStepTime * (mFactorForModes[PowerManagerEx.MODE_ULTRASAVING]
                - mFactorForModes[PowerManagerEx.MODE_LOWPOWER]))
                / mFactorForModes[PowerManagerEx.MODE_ULTRASAVING];

            mLowModeDischargeStepTracker.addLevelSteps(numStepLevels,
                    modeBits, newElapsedRealtime);
            // correct mLastStepTime
            mLowModeDischargeStepTracker.mLastStepTime = elapsedRealtime;
        }


        if (DEBUG) {
            Slog.d(TAG, "updateLevelStepsForOtherMode:"
                    + "mLowModeDischargeStepTracker.mNumStepDurations:"
                    + mLowModeDischargeStepTracker.mNumStepDurations
                    + " timePerLevel for mLowModeDischargeStepTracker: "
                    + mLowModeDischargeStepTracker.computeTimePerLevel()
                    + ", mNormalModeDischargeStepTracker.mNumStepDurations:"
                    + mNormalModeDischargeStepTracker.mNumStepDurations
                    + " timePerLevel for mNormalModeDischargeStepTracker: "
                    + mNormalModeDischargeStepTracker.computeTimePerLevel()
                    + ", mUltraModeDischargeStepTracker.mNumStepDurations:"
                    + mUltraModeDischargeStepTracker.mNumStepDurations
                    + " timePerLevel for mUltraModeDischargeStepTracker: "
                    + mUltraModeDischargeStepTracker.computeTimePerLevel());
        }
    }


    private void updateLevelStepsForAllMode(int numStepLevels,
            long modeBits, long elapsedRealtime) {

        mNormalModeDischargeStepTracker.addLevelSteps(numStepLevels, modeBits, elapsedRealtime);
        mUltraModeDischargeStepTracker.addLevelSteps(numStepLevels, modeBits, elapsedRealtime);
        mLowModeDischargeStepTracker.addLevelSteps(numStepLevels, modeBits, elapsedRealtime);

        if (DEBUG) {
            Slog.d(TAG, "updateLevelStepsForAllMode:"
                    + "mLowModeDischargeStepTracker.mNumStepDurations:"
                    + mLowModeDischargeStepTracker.mNumStepDurations
                    + " timePerLevel for mLowModeDischargeStepTracker: "
                    + mLowModeDischargeStepTracker.computeTimePerLevel()
                    + ", mNormalModeDischargeStepTracker.mNumStepDurations:"
                    + mNormalModeDischargeStepTracker.mNumStepDurations
                    + " timePerLevel for mNormalModeDischargeStepTracker: "
                    + mNormalModeDischargeStepTracker.computeTimePerLevel()
                    + ", mUltraModeDischargeStepTracker.mNumStepDurations:"
                    + mUltraModeDischargeStepTracker.mNumStepDurations
                    + " timePerLevel for mUltraModeDischargeStepTracker: "
                    + mUltraModeDischargeStepTracker.computeTimePerLevel());
        }
    }

}
