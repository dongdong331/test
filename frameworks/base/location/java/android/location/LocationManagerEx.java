
package android.location;

import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.util.Log;

public class LocationManagerEx {

    private static final String TAG = LocationManagerEx.class.getSimpleName();

    private Context mContext;
    private String mOperatorName;

    public LocationManagerEx(Context context) {
        mContext = context;
        try {
            mOperatorName = mContext.getResources().getString(
                    com.android.internal.R.string.config_location_support_operator);
        } catch (NotFoundException e) {
            Log.d(TAG, "config_location_support_operator cannot be found.");
        }
        Log.d(TAG, "config_location_support_operator value is " + mOperatorName);
    }

    /** @hide */
    public boolean isSupportCmcc() {
        return "cmcc".equals(mOperatorName);
    }
}
