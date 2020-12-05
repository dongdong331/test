package com.android.server.power.sprdpower;

public abstract class AbsDeviceIdleController {

    public void readPresetConfigListFromFile(){

    }

    public boolean isInPresetWhiteAppList(int uid) {
        return false;
    }

    public boolean isInPresetWhiteAppList(String packageName) {
        return false;
    }

    /**
     * register a observer for IMS Network state
     * @param observer link {@ImsNetworkObserver}
     */
    public void registerImsNetworkObserver(ImsNetworkObserver observer) {
    }

    /**
     * unregister a observer for IMS Network state
     * @param observer link {@ImsNetworkObserver}
     */
    public void unregisterImsNetworkObserver(ImsNetworkObserver observer) {
    }

    /**
     * Observer for IMS Network state, will be call when state changed.
     */
    public static abstract class ImsNetworkObserver {
        /**
         * onVoWifiCalling will be call when the calling state of VoWifi is changed.
         * @param on
         */
        public abstract void onVoWifiCalling(boolean calling);
    }


    /**
     * Enable Ims network observer functions. When disabled, the state of IMS Network will
     * not notify to Observers
     * @param enabled
     */
    public void setImsNetworkObserverEnabled(boolean enabled) {
    }

}
