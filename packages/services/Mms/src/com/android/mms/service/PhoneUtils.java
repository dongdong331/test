/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.mms.service;

import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.i18n.phonenumbers.NumberParseException;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.Phonenumber;
import com.android.mms.service.R;
import java.util.Locale;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.content.Context;
import android.content.res.XmlResourceParser;
import com.android.internal.util.XmlUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;
/**
 * Utility to handle phone numbers.
 */
public class PhoneUtils {

    /**
     * Get a canonical national format phone number. If parsing fails, just return the
     * original number.
     *
     * @param telephonyManager
     * @param subId The SIM ID associated with this number
     * @param phoneText The input phone number text
     * @return The formatted number or the original phone number if failed to parse
     */

    public static int mSubId;
    public static Context mContext;
    public static  SubscriptionManager mSubscriptionManager;
    public static String getNationalNumber(TelephonyManager telephonyManager, int subId,
            String phoneText) {
        final String country = getSimOrDefaultLocaleCountry(telephonyManager, subId);
        final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        final Phonenumber.PhoneNumber parsed = getParsedNumber(phoneNumberUtil, phoneText, country);
        if (parsed == null) {
            return phoneText;
        }
        return phoneNumberUtil
                .format(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
                .replaceAll("\\D", "");
    }

    // Parse the input number into internal format
    private static Phonenumber.PhoneNumber getParsedNumber(PhoneNumberUtil phoneNumberUtil,
            String phoneText, String country) {
        try {
            final Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(phoneText, country);
            if (phoneNumberUtil.isValidNumber(phoneNumber)) {
                return phoneNumber;
            } else {
                LogUtil.e("getParsedNumber: not a valid phone number"
                        + " for country " + country);
                return null;
            }
        } catch (final NumberParseException e) {
            LogUtil.e("getParsedNumber: Not able to parse phone number", e);
            return null;
        }
    }

    // Get the country/region either from the SIM ID or from locale
    private static String getSimOrDefaultLocaleCountry(TelephonyManager telephonyManager,
            int subId) {
        String country = getSimCountry(telephonyManager, subId);
        if (TextUtils.isEmpty(country)) {
            country = Locale.getDefault().getCountry();
        }

        return country;
    }

    // Get country/region from SIM ID
    private static String getSimCountry(TelephonyManager telephonyManager, int subId) {
        final String country = telephonyManager.getSimCountryIso(subId);
        if (TextUtils.isEmpty(country)) {
            return null;
        }
        return country.toUpperCase();
    }
    public static boolean isOperatorSupport(Context context,int subId){
        LogUtil.e("andy"," isOperatorSupport = ");
        mSubId=subId;
        mContext=context;
        String mccMncString=getMccMncString(getMccMnc());
        LogUtil.e("andy"," mccMncString = " + mccMncString);
        return isSupportOperator(context,mccMncString);
    }

    private static boolean isSupportOperator(Context context,String mccMncString){
        boolean support=false;
       // if("234030".equals(mccMncString) || "234033".equals(mccMncString) ||"505002".equals(mccMncString)||"260002".equals(mccMncString))
     if(getSupportMccMncFromResource(context,mccMncString)){
            support =true;
    }
        LogUtil.e("andy"," isSupportOperator = " + support);
        return support;
    }
    private static boolean getSupportMccMncFromResource(Context context,String mccmnc) {

        int id = R.xml.vowifi_mccmnc;
        XmlResourceParser parser = null;
        try {
            parser = context.getResources().getXml(id);
            return getSupportMccMncFromXmlParser(parser, mccmnc);
        } finally {
            if (parser != null) parser.close();
        }
    }

    private static boolean getSupportMccMncFromXmlParser(XmlPullParser parser,String mccmnc) {
        try {
            XmlUtils.beginDocument(parser, "xml");

            while (true) {
                XmlUtils.nextElement(parser);
                String element = parser.getName();
                if (element == null) {
                    Log.e("mms", "Parsing pattern data found null");
                    break;
                }

                if (element.equals("vowifi")) {
                    String xml_mccmnc = parser.getAttributeValue(null, "mccmnc");
                 LogUtil.e("mms"," get xml_mccmnc = "+xml_mccmnc);
                    if (mccmnc.equals(xml_mccmnc)) {

                        return true;
                    }
                } else {
                    Log.e("mms", "Error: skipping unknown XML tag ");
                }
            }
        } catch (XmlPullParserException e) {
            Log.e("mms", "XML parser exception reading mccmnc xml", e);
        } catch (IOException e) {
            Log.e("mms", "I/O exception reading mccmnc xml", e);
        }

        return false;
    }
    private static String getMccMncString(int[] mccmnc) {
        if (mccmnc == null || mccmnc.length != 2) {
            return "000000";
        }
        return String.format("%03d%03d", mccmnc[0], mccmnc[1]);
    }
    private static int[] getMccMnc() {
        int mcc = 0;
        int mnc = 0;
        final SubscriptionInfo subInfo = getActiveSubscriptionInfo();
        if (subInfo != null) {
            mcc = subInfo.getMcc();
            mnc = subInfo.getMnc();
        }
        return new int[]{mcc, mnc};
    }
    private static SubscriptionInfo getActiveSubscriptionInfo() {
        try {
            mSubscriptionManager = SubscriptionManager.from(mContext);
            final SubscriptionInfo subInfo =
                    mSubscriptionManager.getActiveSubscriptionInfo(mSubId);
            return subInfo;
        } catch (Exception e) {
        }
        return null;
    }
}
