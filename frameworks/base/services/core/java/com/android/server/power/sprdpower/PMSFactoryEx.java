package com.android.server.power.sprdpower;

import android.content.Context;
import android.hardware.display.DisplayManagerInternelEx;

import com.android.server.DeviceIdleControllerEx;
import com.android.server.display.AbsDisplayPowerController;
import com.android.server.display.AbsDisplayPowerState;
import com.android.server.power.sprdpower.AbsDeviceIdleController;
import com.android.server.power.sprdpower.AbsPowerManagerServiceUtils;

public class PMSFactoryEx extends PMSFactory{

    public void initExtraPowerManagerService(Context context, Context uiContext) {
        PowerManagerServiceEx.init(context, uiContext);
    }

    public AbsPowerManagerServiceUtils createPowerManagerServiceUtils(Context context) {
        return PowerManagerServiceUtils.getInstance(context);
    }
/*
    public AbsDisplayPowerController createExtraDisplayPowerController(Context context){
        return DisplayPowerControllerEx.getInstance(context);
    }

    public AbsDisplayPowerState createExtraDisplayPowerState(Context context){
        return DisplayPowerStateEx.getInstance(context);
    }

    public DisplayManagerInternelEx createExtraDisplayManagerService(Context context){
        return DisplayManagerServiceEx.getInstance(context);
    }*/

    public AbsDeviceIdleController createExtraDeviceIdleController(Context context){
        return DeviceIdleControllerEx.getInstance(context);
    }
}
