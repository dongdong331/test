/*
 *Created by spreadst
 */

package com.android.settings.datetime;

import android.content.Context;

public interface SprdGpsUpdateTimeCallback {
    // Minimum time is Nov 5, 2007, 0:00.
    long MIN_DATE = 1194220800000L;

    void updatePreference(Context context);
}
