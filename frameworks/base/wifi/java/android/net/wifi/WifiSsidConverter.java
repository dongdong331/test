/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.wifi;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;

import java.math.BigInteger;


/**
 * Stores SSID octets and handles conversion.
 *
 * For Ascii encoded string, any octet < 32 or > 127 is encoded as
 * a "\x" followed by the hex representation of the octet.
 * Exception chars are ", \, \e, \n, \r, \t which are escaped by a \
 * See src/utils/common.c for the implementation in the supplicant.
 *
 * @hide
 */
public class WifiSsidConverter {
    private static final String TAG = "WifiSsidConverter";

    public static final String NONE = "<unknown ssid>";

    private static int GB2312_FLAG = 0x1;

    private static String GB2312_FLAG_STRING = "\u0001";

    private static int INVALID_CHARTSET_FLAG = 0x2;

    private static String INVALID_CHARSET_FLAG_STRING = "\u0002";

    private WifiSsidConverter() {
    }

    private static String encodeSsid(byte[] ssidBytes) {
        int i = 0;
        int val = 0;
        String ssid = new String("");
        for (i = 0; i < ssidBytes.length; i++) {
            switch (ssidBytes[i]) {
                case 34: // '\"':
                    ssid += "\\\"";
                    break;
                case 92: // '\\':
                    ssid += "\\\\";
                    break;
                case 27: // '\e':
                    ssid += "\\e";
                    break;
                case 10: // '\n':
                    ssid += "\\n";
                    break;
                case 13: // '\r':
                    ssid += "\\r";
                    break;
                case 9: // '\t':
                    ssid += "\\t";
                    break;
                default:
                    if (ssidBytes[i] >= 32 && ssidBytes[i] <= 127) {
                        ssid += String.format(Locale.US, "%c", ssidBytes[i]);
                    } else {
                        ssid += String.format(Locale.US, "\\x%02x", ssidBytes[i]);
                    }
                    break;
            }
        }

        return ssid;
    }

    private static String removeDoubleQuotes(String string) {
        int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"')
                && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    /**
    * Check if the string contains only GB2312 string
    * str: input string, it must be a completed string.
    * str_len : input string byte length
    * return 1 for yes
    * return 0 for no
    * GB2312 range: A1A0~FFFF
    * GBK range:
    * 1 byte: 0X ~ 7F
    * 2 byte:
    * first byte: 81 ~ FE
    * second byte: 40 ~ FE
    */
    private static boolean isGb2312(byte[] ssidBytes) {
        int i = 0;
        int len = ssidBytes.length;

        boolean isGb = true;
        boolean isGbTruncated = false;


        while (i < len) {
            if (0 == ((ssidBytes[i]&0xff) & 0x80)) {
                i++;
            } else if ((ssidBytes[i]&0xff) >= 0x81 && (ssidBytes[i]&0xff) <=0xfe && ((i+1) < len)
                && (ssidBytes[i+1]&0xff) >= 0x40 && (ssidBytes[i+1]&0xff) <= 0xfe) {
                i += 2;
            } else if ((ssidBytes[i]&0xff) >= 0x81 && (ssidBytes[i]&0xff) <=0xfe && ((i+1) >= len)) {
                isGbTruncated = true;
                i++;
            } else {
                isGb = false;
                break;
            }
        }

        if (isGbTruncated)
            Log.e( TAG, "is GB2312 Tuncated!!!!!");


        if (isGb)
            Log.e( TAG, "is GB2312!!!!!");

        return isGb;
    }


    /**
    * Check if the string only contains a GB2312 character
    * str: input string, must be a completed string
    * str_len : input string byte length
    * return 1 for yes
    * return 0 for no
    * UTF-8 range:
    * 1 byte: 0X ~7X
    * 2 byte: CX 8X ~ DX BX
    * 3 btye: EX 8X 8X ~EX BX BX
    *
    * GB2312 range: A1A0~FFFF
    * GBK range:
    * 1 byte: 0X ~ 7F
    * 2 byte:
    * first byte: 81 ~ FE
    * second byte: 40 ~ FE
    * Note: Because CX8X ~ DX BX is also in GBK code range.
    */
    private static boolean containGb2312Byte(byte[] ssidBytes) {
        int i = 0;
        int len = ssidBytes.length;

        boolean containGb312 = false;
        int asianCount = 0;
        int twoBytesCount = 0;
        int val;

        //check utf-8 first
        while (i < ssidBytes.length) {
            val = ssidBytes[i] & 0xFF;

            if (0 == (val & 0x80)) {//ascii
                i++;
            } else if ((0xc0 == (val & 0xe0)) && ((i+1) < len)
                && (0x80 == ((ssidBytes[i+1]&0xff) & 0xc0))) {//UTF-8 2 bytes: CX 8X ~ DX BX
                i += 2;
                asianCount = 0;
                twoBytesCount++;
            } else if ((0xe0 == (val & 0xf0) && ((i+2) < len)
                && (0x80 == ((ssidBytes[i+1]&0xff) & 0xc0))) && (0x80==((ssidBytes[i+2]&0xff) & 0xc0))) {
                //UTF-8 3 bytes: EX 8X 8X ~EX BX BX
                i += 3;
                if ((++ asianCount) > 1) {
                    containGb312 = false;
                    break;
                }
            } else if (((0xc0 == (val & 0xe0)) && ((i+1) >= len)) ||
                (0xe0 == (val & 0xf0) && ((i+2) >= len))) {//truncated utf-8
                break;
            } else {
                containGb312 = true;
                break;
            }
        }

        //if contains 2 bytes coded character without 3 bytes coded character, check if this is a gbk string.
        if ((!containGb312) && (twoBytesCount > 0) && (asianCount == 0))
            return isGb2312(ssidBytes);

        if (containGb312)
            Log.e( TAG, "Contain GB2312!!!!!");

        return containGb312;
    }

    /**
    * To check if a GB2312 string truncated.
    */
    private static boolean isGb2312Truncated(byte[] ssidBytes) {
        int i = 0;
        int len = ssidBytes.length;
        boolean isGbTruncated = false;
        int val;

        while (i < len) {
            val = ssidBytes[i] & 0xff;

            if (0 == (val & 0x80)) {//ascii
                i++;
            } else if (val >= 0x81 && val <=0xfe && ((i+1) < len)
                && (ssidBytes[i+1]&0xff) >= 0x40 && (ssidBytes[i+1]&0xff) <= 0xfe) {
                i += 2;
            } else if (val >= 0x81 && val <= 0xfe && ((i+1) >= len)) {//truncated GBK
                isGbTruncated = true;
                break;
            } else {
                break;
            }
        }

        if (isGbTruncated)
            Log.e( TAG, "is GB2312 Tuncated!!!!!");

        return isGbTruncated;
    }

    /**
    * To check if a utf8 string truncated.
    */
    private static boolean isUTF8Truncated(byte[] ssidBytes) {
        int i = 0;
        int len = ssidBytes.length;
        boolean isUtf8Truncated = false;

        while (i < len) {
            if (0 == ((ssidBytes[i]&0xff) & 0x80)) {//ascii
                i++;
            } else if ((0xc0 == ((ssidBytes[i]&0xff) & 0xe0)) && ((i+1) < len)
                && (0x80 == ((ssidBytes[i+1]&0xff) & 0xc0))) {//UTF-8 2 bytes: CX 8X ~ DX BX
                i += 2;
            } else if ((0xe0 == ((ssidBytes[i]&0xff) & 0xf0) && ((i+2) < len)
                && (0x80 == ((ssidBytes[i+1]&0xff) & 0xc0))) && (0x80==((ssidBytes[i+2]&0xff) & 0xc0))) {
                //UTF-8 3 bytes: EX 8X 8X ~EX BX BX
                i += 3;
            } else if (((0xc0 == ((ssidBytes[i]&0xff) & 0xe0)) && ((i+1) >= len)) ||
                (0xe0 == ((ssidBytes[i]&0xff) & 0xf0) && ((i+2) >= len))) {//truncated UTF-8
                isUtf8Truncated =true;
                break;
            } else
                break;
        }

        if (isUtf8Truncated)
            Log.e( TAG, "is UTF-8 Tuncated!!!!!");

        return isUtf8Truncated;
    }

    /**
    * Check if the ssid string has a truncated char.
    */
    private static boolean isSsidTruncated(byte[] ssidBytes) {
        boolean containGb = containGb2312Byte(ssidBytes);

        if (containGb) {
            return isGb2312Truncated(ssidBytes);
        } else {
            return isUTF8Truncated(ssidBytes);
        }
    }

    public static String toString(byte[] ssidBytes) {
        Charset charset = Charset.forName("UTF-8");
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer out = CharBuffer.allocate(32);

        CoderResult result = decoder.decode(ByteBuffer.wrap(ssidBytes), out, true);
        out.flip();

        //Log.e(TAG, "ssid = " + out.toString() + ", result: " + result);

        boolean tryGB2312Failed = false;

        if (result.isError()) { // TO check if it is a GB2312 string
            Log.e(TAG, "Try GB2312");
            try {
                if (isSsidTruncated(ssidBytes)) {
                        return encodeSsid(ssidBytes) + INVALID_CHARSET_FLAG_STRING;
                } else {
                    charset = Charset.forName("GB2312");
                    decoder = charset.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT);

                    CharBuffer gb2312Out = CharBuffer.allocate(32);

                    //int i= 0;
                    //Log.e(TAG, "start:");
                    //for(i=0; i<ssidBytes.length; i++) {
                    //     Log.e(TAG, " " +  String.format(Locale.US, "0x%x", ssidBytes[i]));
                    //}
                    //Log.e(TAG, "end");

                    result = decoder.decode(ByteBuffer.wrap(ssidBytes, 0, ssidBytes.length), gb2312Out, true);
                    gb2312Out.flip();

                    if (result.isError()) {
                        Log.e(TAG, "ssid contain invalid charset ");
                        return encodeSsid(ssidBytes) + INVALID_CHARSET_FLAG_STRING;
                    }

                    Log.e(TAG, "ssid = " + gb2312Out.toString() + ", result: " + result);

                   return gb2312Out.toString() + GB2312_FLAG_STRING;
                }
            }catch (Exception cause) {
                Log.e(TAG, "exception = " + cause);
                tryGB2312Failed = true;
            }
        }

        if (tryGB2312Failed) {
            return NONE;
        }

        return out.toString();
    }


    public static String makeSupplicantSSID(String str){
        String tmp = removeDoubleQuotes(str);
        int i = tmp.length();
        char c = tmp.charAt(i-1);
        if ( c == GB2312_FLAG) {
            String tmp2 = tmp.substring(0, i-1);
            Log.e(TAG, "GB2312 String:" + tmp2);
            return String.format("%x", new BigInteger(1, tmp2.getBytes(Charset.forName("GB2312"))));

        } else if (c == INVALID_CHARTSET_FLAG) {
            String tmp3 = tmp.substring(0, i-1);
            Log.e(TAG, "encode String:" + tmp3);
            return "P\"" + tmp3 + "\"";
        }
        return String.format("%x", new BigInteger(1, tmp.getBytes(Charset.forName("UTF-8"))));
    }


}
