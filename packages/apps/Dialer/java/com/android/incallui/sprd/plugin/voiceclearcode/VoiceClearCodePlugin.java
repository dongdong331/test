package com.android.incallui.sprd.plugin.voiceclearcode;

import android.content.Context;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.util.XmlUtils;
import com.android.dialer.app.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;


public class VoiceClearCodePlugin extends VoiceClearCodeHelper {

    private static final String TAG = "VoiceClearCodePlugin";
    private Context context;

    private static final String FIRST_ELEMELT_NAME = "resources";
    private static final String CAUSE_NUMBER = "number";
    private static final String TEXT_REQUIRED = "text_required";
    private final String SPECIAL_VOICE_CLEAR_CODE = "*00015,*00008";
    private HashMap<Integer, String> mEsTextRequiredHashmap = new HashMap<Integer, String>();
    private HashMap<Integer, String> mEnTextRequiredHashmap = new HashMap<Integer, String>();

    public VoiceClearCodePlugin(Context context) {
        this.context = context;
    }

    public VoiceClearCodePlugin() {

    }

    @Override
    public void showToastMessage(Context context, String reason) {
        Log.i(TAG, "showToastMessage: reason (" + reason + ")");
        if (!TextUtils.isEmpty(reason) && reason.contains(",")) {
            reason = reason.substring(0, reason.indexOf(","));
        }else if (!TextUtils.isEmpty(reason) &&!reason.contains(",")){
            reason =reason;//UNISOC:Telcel volte csfb clear code
        }

        parserAttributeValue();
        try {
            String callFailCause = getAttributeValue(Integer.parseInt(reason));
            Log.i(TAG, "callFailCause="+callFailCause);
            if (!TextUtils.isEmpty(callFailCause)) {
                Toast.makeText(context, callFailCause, Toast.LENGTH_LONG).show();
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "NumberFormatException when parse vendorCause.");
        }
    }

    /**
     * Support English and Spanish for CC code.
     */
    public void parserAttributeValue() {
        Log.i(TAG, "start  parserAttributeValue");

        Resources r = context.getResources();
        //UNISOC:add for bug987797
        boolean clarovoiceclearcode=context.getResources().getBoolean(com.android.incallui.R.bool.config_is_support_voiceclearcode_feature_claro);
        Log.i(TAG, "parserAttributeValue  clarovoiceclearcode: " + clarovoiceclearcode);
        if (clarovoiceclearcode){
            XmlPullParser parser = r.getXml(R.xml.networkvalue_config_claro);
            parseXml(parser, mEsTextRequiredHashmap);
        }else{
            XmlPullParser parser = r.getXml(R.xml.networkvalue_config);
            parseXml(parser, mEsTextRequiredHashmap);
        }

        XmlPullParser enParser = r.getXml(R.xml.networkvalue_config_en);
        parseXml(enParser, mEnTextRequiredHashmap);
    }

    public String getAttributeValue(Integer causeNumber) {
        Log.i(TAG, "getAttributeValue  causeNumber: " + causeNumber);
        String strRequired = null;
        String language = SystemProperties.get("persist.sys.locale");
        Log.i(TAG, "getAttributeValue language: " + language);
        if (language.indexOf("es") != -1) {
            if (mEsTextRequiredHashmap.containsKey(causeNumber)) {
                strRequired = mEsTextRequiredHashmap.get(causeNumber);
            }
        } else {
            if (mEnTextRequiredHashmap.containsKey(causeNumber)) {
                strRequired = mEnTextRequiredHashmap.get(causeNumber);
            }
        }
        return strRequired;
    }

    private void parseXml(XmlPullParser parser,
                          HashMap<Integer, String> hashmap) {
        if (parser != null && hashmap.isEmpty()) {
            try {
                XmlUtils.beginDocument(parser, FIRST_ELEMELT_NAME);
                XmlUtils.nextElement(parser);
                while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                    int causeNumber = Integer.parseInt(parser.getAttributeValue(null, CAUSE_NUMBER));
                    String textRequired = parser.getAttributeValue(null, TEXT_REQUIRED);
                    hashmap.put(causeNumber, textRequired);
                    XmlUtils.nextElement(parser);
                }
            } catch (XmlPullParserException e) {
                Log.e(TAG, "XmlPullParserException : " + e);
            } catch (IOException e) {
                Log.e(TAG, "IOException: " + e);
            }
        }
    }

    @Override
    public boolean isVoiceClearCodeLabel(String callStateLabel) {
        return true;
    }

    @Override
    public boolean isSpecialVoiceClearCode(String number) {
        if (!TextUtils.isEmpty(SPECIAL_VOICE_CLEAR_CODE)) {
            for (String code : SPECIAL_VOICE_CLEAR_CODE.split(",")) {
                if (code != null && code.equals(number)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }
    /* @} */
}