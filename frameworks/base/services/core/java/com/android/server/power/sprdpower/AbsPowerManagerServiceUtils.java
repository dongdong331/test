package com.android.server.power.sprdpower;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.WorkSource;

public class AbsPowerManagerServiceUtils {

    public void registerButtonLightOffTimeOut(ContentResolver resolver, ContentObserver settingsObserver){

    }

    public void updateButtonLightOffTimeOut(ContentResolver resolver){

    }

    public int getButtonOffTimeoutSetting(){
        return 0;
    }

    public void setBootCompleted(boolean bootCompleted){

    }

    //Bug 707103 sending user activity broadcast after press physical button
    public void notifyStkUserActivity(){

    }

    //Bug 624590 send wakelock info to AMS
    public void updateWakeLockStatusLocked(int opt, int lockUid, int flags, WorkSource workSource) {

    }

    //Bug 624590 send wakelock info to AMS
    public void updateWakeLockStatusLockedChanging(int flags, WorkSource from, WorkSource to) {

    }

}
