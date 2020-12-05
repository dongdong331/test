package android.os.sprdpower;

/**
 * @hide
 */
public abstract class AbsPowerManager {

    public void shutdownForAlarm(){

    }

    public void rebootAnimation(){

    }

    public void scheduleButtonLightTimeout(long now){

    }

    /**
     * Bug 707103
     * This method must only be called by the CatServiceSprd
     * flag:control whether open the funtion that sending user activity broadcast after press physical button
     */
    public void setEventUserActivityNeeded(boolean bEventNeeded){

    }

}
