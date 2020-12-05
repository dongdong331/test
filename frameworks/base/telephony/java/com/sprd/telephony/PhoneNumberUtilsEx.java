package android.telephony;

import android.content.Context;
import android.location.CountryDetector;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.i18n.phonenumbers.ShortNumberInfo;

/**
 * Various utilities for dealing with phone number strings.
 */
public class PhoneNumberUtilsEx
{
    /*
     * Special characters
     *
     * (See "What is a phone number?" doc)
     * 'p' --- GSM pause character, same as comma
     * 'w' --- GSM wait character
     */
    public static final char PAUSE = ',';
    public static final char WAIT = ';';
    public static final char WILD = 'N';
    static final int MIN_MATCH = 7;

    static final String LOG_TAG = "PhoneNumberUtilsEx";
    /**
     * SPRD: Porting send P/W to CP
     * Strips separators from a phone number string.Except p w
     * @param phoneNumber phone number to strip.
     * @return phone string stripped of separators.
     * @hide
     */
     public static String stripSeparatorsExceptPW(String phoneNumber) {
         if (phoneNumber == null) {
             return null;
         }
         int len = phoneNumber.length();
         StringBuilder ret = new StringBuilder(len);
         for (int i = 0; i < len; i++) {
             char c = phoneNumber.charAt(i);
             // Character.digit() supports ASCII and Unicode digits (fullwidth,Arabic-Indic, etc.)
             int digit = Character.digit(c, 10);
             if (digit != -1) {
                 ret.append(digit);
             }else if (PhoneNumberUtils.isNonSeparator(c)) {
                 ret.append(c);
             }else if (c == 'P' || c == 'p' || c == 'W' || c == 'w') {
                 ret.append(c);
             }
         }
         return ret.toString();
     }

     /**
      * SPRD: exchange P & W to , & ;
      * @hide
      */
     public final static String pAndwToCommaAndSemicolon(String str) {
         if (null != str) {
             StringBuilder strBlder = new StringBuilder();
             int len = str.length();
             for (int i = 0; i < len; i++) {
                 switch (str.charAt(i)) {
                 case 'p':
                 case 'P':
                     strBlder.append(PAUSE);
                     break;
                 case 'w':
                 case 'W':
                     strBlder.append(WAIT);
                     break;
                 default:
                     strBlder.append(str.charAt(i));
                 }
             }
             return strBlder.toString();
         } else {
             return null;
         }
     }

     /**
      * SPRD:exchange , & ; to P & W
      * @hide
      */
     public final static String CommaAndSemicolonTopAndw(String str) {
         if (null != str) {
             StringBuilder strBlder = new StringBuilder();
             int len = str.length();
             for (int i = 0; i < len; i++) {
                 switch (str.charAt(i)) {
                 case PAUSE:
                     strBlder.append('P');
                     break;
                 case WAIT:
                     strBlder.append('W');
                     break;
                 default:
                     strBlder.append(str.charAt(i));
                 }
             }
             return strBlder.toString();
         } else {
             return null;
         }
     }

     /**
      * Checks a given number against the list of
      * emergency numbers provided by the RIL and SIM card.
      * @hide
      * @param number the number to look up.
      * @return
      */
     public static boolean isEmergencyNumber(String number) {
         boolean isEmergencyNumber = false;
         for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
             isEmergencyNumber |= isEmergencyNumber(i, number);
         }
         return isEmergencyNumber;
     }

     /**
      * Checks a given number against the list of
      * emergency numbers provided by the RIL and SIM card.
      * @hide
      * @param slotId the slot id of the sim.
      * @param number the number to look up.
      * @return
      */
     public static boolean isEmergencyNumber(int slotId, String number) {
         return isEmergecyNumberInternal(slotId, number, null, true);
     }

     /**
      * Checks a given number against the list of
      * emergency numbers provided by the RIL and SIM card.
      * @param slotId the slot id of the sim.
      * @param number the number to look up.
      * @return
      */
     private static boolean isEmergecyNumberInternal(int slotId, String number,
             String defaultCountryIso, boolean useExactMatch) {
         // If the number passed in is null, just return false:
         if (number == null) return false;

         // If the number passed in is a SIP address, return false, since the
         // concept of "emergency numbers" is only meaningful for calls placed
         // over the cell network.
         // (Be sure to do this check *before* calling extractNetworkPortionAlt(),
         // since the whole point of extractNetworkPortionAlt() is to filter out
         // any non-dialable characters (which would turn 'abc911def@example.com'
         // into '911', for example.))
         if (PhoneNumberUtils.isUriNumber(number)) {
             return false;
         }

         // Strip the separators from the number before comparing it
         // to the list.
         number = PhoneNumberUtils.extractNetworkPortionAlt(number);

         String emergencyNumbers = "";

         // retrieve the list of emergency numbers
         // check read-write ecclist property first
         String ecclist = (slotId <= 0) ? "ril.ecclist" : ("ril.ecclist" + slotId);

         emergencyNumbers = SystemProperties.get(ecclist, "");

         Rlog.d(LOG_TAG, "slotId:" + slotId + " country:"
                 + defaultCountryIso + " emergencyNumbers: " +  emergencyNumbers);

         if (TextUtils.isEmpty(emergencyNumbers)) {
             // then read-only ecclist property since old RIL only uses this
             emergencyNumbers = SystemProperties.get("ro.ril.ecclist");
         }

         if (!TextUtils.isEmpty(emergencyNumbers)) {
             // searches through the comma-separated list for a match,
             // return true if one is found.
             for (String emergencyNum : emergencyNumbers.split(",")) {
                 // It is not possible to append additional digits to an emergency number to dial
                 // the number in Brazil - it won't connect.
                 if (useExactMatch || "BR".equalsIgnoreCase(defaultCountryIso)) {
                     if (number.equals(emergencyNum)) {
                         return true;
                     }
                 } else {
                     if (number.startsWith(emergencyNum)) {
                         return true;
                     }
                 }
             }
             // no matches found against the list!
             return false;
         }

         Rlog.d(LOG_TAG, "System property doesn't provide any emergency numbers."
                 + " Use embedded logic for determining ones.");

         // If slot id is invalid, means that there is no sim card.
         // According spec 3GPP TS22.101, the following numbers should be
         // ECC numbers when SIM/USIM is not present.
         emergencyNumbers = !TelephonyManager.getDefault().hasIccCard(slotId) ? "112,911,000,08,110,118,119,999" : "112,911";

         for (String emergencyNum : emergencyNumbers.split(",")) {
             if (useExactMatch) {
                 if (number.equals(emergencyNum)) {
                     return true;
                 }
             } else {
                 if (number.startsWith(emergencyNum)) {
                     return true;
                 }
             }
         }

         // No ecclist system property, so use our own list.
         if (defaultCountryIso != null) {
             ShortNumberInfo info = ShortNumberInfo.getInstance();
             if (useExactMatch) {
                 return info.isEmergencyNumber(number, defaultCountryIso);
             } else {
                 return info.connectsToEmergencyNumber(number, defaultCountryIso);
             }
         }

         return false;
     }

     /**
      * Checks if a given number is an emergency number for the country that the user is in.
      *
      * @param number the number to look up.
      * @param countryIso the specific country which the number should be checked against
      * @param context the specific context which the number should be checked against
      * @return true if the specified number is an emergency number for the country the user
      * is currently in.
      * @hide
      */
     public static boolean isLocalEmergencyNumberForCountryIso(Context context, String countryIso, String number) {
         if (number == null) return false;
         if (PhoneNumberUtils.isUriNumber(number)
                 //UNISOC: only check ShortNumberInfo.isEmergencyNumber for CN/TW.
                 ||(!"TW".equalsIgnoreCase(countryIso)
                 && !"CN".equalsIgnoreCase(countryIso))) {
             return false;
         }

         number = PhoneNumberUtils.extractNetworkPortionAlt(number);
         if (countryIso != null) {
             ShortNumberInfo info = ShortNumberInfo.getInstance();
             Rlog.d(LOG_TAG, "isLocalEmergencyNumber number:" + number + " country:" + countryIso
                     + " isEmergencyNumber " + info.isEmergencyNumber(number, countryIso));
             return info.isEmergencyNumber(number, countryIso);
         }
         return false;
     }
}
