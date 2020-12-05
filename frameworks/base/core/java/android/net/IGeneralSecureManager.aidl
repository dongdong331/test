
package android.net;

import android.content.Intent;

/**
 *@hide
 */
interface IGeneralSecureManager {
   void setPackageBlockState(int packageUid, int networkType, boolean allowed);
   void deleteBlockPackage (int packageUid);
}