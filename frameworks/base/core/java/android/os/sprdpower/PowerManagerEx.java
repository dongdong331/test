package android.os.sprdpower;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;

/**
 * SPRD added for PowerOff Alarm
 * @hide
 */
public class PowerManagerEx extends AbsPowerManager{

    private static final String TAG = "PowerManagerEx";
    private Context mContext;

    //PowerManagerEx user activity event is based on 1000 growth
    public static final int USER_ACTIVITY_EVENT_WAKEUP = 1000;

// NOTE: Bug #627645 low power Feature BEG-->
    public static final int MODE_INVALID = -1;
    public static final int MODE_PERFORMANCE = 0;
    public static final int MODE_SMART = 1;
    public static final int MODE_POWERSAVING = 2;
    public static final int MODE_LOWPOWER = 3;
    public static final int MODE_ULTRASAVING = 4;
    public static final int MODE_NONE = 5; //to close the power save mode
    public static final int MODE_MAX = MODE_NONE;

    /** @hide */
    public static final String ACTION_POWEREX_SAVE_MODE_CHANGED
            = "android.os.action.POWEREX_SAVE_MODE_CHANGED";

    /** @hide */
    public static final String EXTRA_POWEREX_SAVE_MODE = "mode";
    public static final String EXTRA_POWEREX_SAVE_PREMODE = "pre-mode";


    public static final String POWER_CONTROLLER_ENABLE = "persist.sys.pwctl.enable";

    private static final boolean mPowerControllerEnabled = (1 == SystemProperties.getInt(POWER_CONTROLLER_ENABLE, 1));

    private final IPowerManagerEx mPowerMan;

// <-- NOTE: Bug #627645 low power Feature END


    public static boolean isPowerControllerEnabled() {
        return mPowerControllerEnabled;
    }

    public PowerManagerEx(Context context){
        mContext = context;
        mPowerMan = IPowerManagerEx.Stub.asInterface(ServiceManager.getService("power_ex"));
    }

    private IPowerManagerEx getPowerManagerEx(){
        return IPowerManagerEx.Stub.asInterface(ServiceManager.getService("power_ex"));
    }

    @Override
    public void shutdownForAlarm() {
        try {
            getPowerManagerEx().shutdownForAlarm(false, true);
        } catch (RemoteException e) {
            Log.i(TAG,"shutdownForAlarm could not get remote service");
        }
    }

    @Override
    public void rebootAnimation() {
        try {
            getPowerManagerEx().rebootAnimation();
        } catch (RemoteException e) {
            Log.i(TAG,"rebootAnimation could not get remote service");
        }
    }

    @Override
    public void scheduleButtonLightTimeout(long now) {
        try {
            getPowerManagerEx().scheduleButtonLightTimeout(now);
        } catch (RemoteException e) {
            Log.i(TAG,"scheduleButtonLightTimeout could not get remote service");
        }
    }

    /**
     * Bug 707103
     * This method must only be called by the CatServiceSprd
     * flag:control whether open the funtion that sending user activity broadcast after press physical button
     */
    @Override
    public void setEventUserActivityNeeded(boolean bEventNeeded) {
        try {
            getPowerManagerEx().setEventUserActivityNeeded(bEventNeeded);
        } catch (RemoteException e) {
            Log.i(TAG,"setEventUserActivityNeeded could not get remote service");
        }
    }

    public boolean forcePowerSaveMode(boolean mode) {
        try {
            return mPowerMan.forcePowerSaveMode(mode);
        } catch (Exception e) {
        }
        return false;
    }
}
