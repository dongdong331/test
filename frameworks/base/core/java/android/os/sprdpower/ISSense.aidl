package android.os.sprdpower;


/**
 * @hide
 */
interface ISSense{
    oneway void reportData(int type, int data1, int data2, int data3, int data4);
}
