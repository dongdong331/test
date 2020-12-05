package android.os.sprdpower;


/**
 * @hide
 */
interface IAppStatsService {
    oneway void reportData(int type, int data1, int data2, int data3, int data4);
    oneway void reportAppStateChanged(String packageName, int userId, int state);
    oneway void reportAppProcStateChanged(String appName, int uid, int procState);
}
