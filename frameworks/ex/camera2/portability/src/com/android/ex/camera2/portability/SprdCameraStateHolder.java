
package com.android.ex.camera2.portability;

import android.os.SystemClock;

import com.android.ex.camera2.portability.debug.Log;

public abstract class SprdCameraStateHolder {
    private static final Log.Tag TAG = new Log.Tag("SprdCameraStateHolder");

    public abstract int getState();

    public static final int CAMERA_WITH_THUMB = 1 << 6;
    public static final int CAMERA_RECODERING = 1 << 7;

    private static interface ConditionChecker {
        /**
         * @return Whether the condition holds.
         */
        boolean success();
    }

    public boolean waitForStatesWithTimeout(final int states, final long timeoutMs) {
        Log.i(TAG, "waitForStatesWithTimeout - states = " + Integer.toBinaryString(states)
                + " getState() = " + getState());
        return waitForConditionWithTimeout(new ConditionChecker() {
            @Override
            public boolean success() {
                return (states | getState()) == states;
            }
        }, timeoutMs);
    }

    private boolean waitForConditionWithTimeout(ConditionChecker stateChecker, long timeoutMs) {
        long timeBound = SystemClock.uptimeMillis() + timeoutMs;
        synchronized (this) {
            while (!stateChecker.success()) {
                try {
                    this.wait(timeoutMs);
                    if (SystemClock.uptimeMillis() >= timeBound) {
                        return false;
                    }
                } catch (InterruptedException ex) {
                    if (SystemClock.uptimeMillis() > timeBound) {
                        // Timeout.
                        Log.w(TAG, "Timeout waiting " + timeoutMs);
                    }
                    return false;
                }
            }
        }
        return true;
    }
}
