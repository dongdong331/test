package com.android.server.performance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import android.util.Log;

public class PolicyExecutorConfigLoader {
    private static void error(String msg) {
         Log.e("performance", msg);
    }

    public static List<PolicyItem> load() {
        String configPath = "/system/etc/sprd_performance_config.xml";
        File configFile = new File(configPath);
        if (!configFile.exists()) {
            error("config file not exist");
            return Collections.emptyList();
        }
        BufferedReader reader = null;
        List<PolicyItem> configs = new ArrayList<PolicyItem>();
        try {
            reader = new BufferedReader(new FileReader(configFile));
            final XmlPullParser in = XmlPullParserFactory.newInstance().newPullParser();
            in.setInput(reader);
            int event;
            while (((event = in.next()) != XmlPullParser.END_DOCUMENT)) {
                if (event == XmlPullParser.START_TAG) {
                    final String name = in.getName();
                    if ("policy".equals(name)) {
                        PolicyItem config = loadConfigFromXml(in);
                        if (config != null) {
                            configs.add(config);
                        }
                    }
                }
            }
            return configs;
        } catch (Exception e) {
            error("Failed to load executors");
            return Collections.emptyList();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private static PolicyItem loadConfigFromXml(XmlPullParser in) {
        String policyName = null;
        for (int attrNdx = in.getAttributeCount() - 1; attrNdx >= 0; --attrNdx) {
            final String attrName = in.getAttributeName(attrNdx);
            final String attrValue = in.getAttributeValue(attrNdx);
            if ("name".equals(attrName)) {
                policyName = attrValue;
                break;
            }
        }
        if (policyName == null) {
            return null;
        }
        int event;
        PolicyItem policy = new PolicyItem(policyName);
        try {
            while (((event = in.next()) != XmlPullParser.END_DOCUMENT)) {
                final String name = in.getName();
                String confName = "";
                if (event == XmlPullParser.START_TAG) {
                    if ("config".equals(name)) {
                        for (int attrNdx = in.getAttributeCount() - 1; attrNdx >= 0; --attrNdx) {
                            final String attrName = in
                                    .getAttributeName(attrNdx);
                            final String attrValue = in
                                    .getAttributeValue(attrNdx);
                            if ("name".equals(attrName)) {
                                confName = attrValue;
                                break;
                            }
                        }
                        PolicyConfig config = PolicyConfig.loadFromXml(
                                confName, in);
                        if (config != null) {
                            policy.addConfig(confName, config);
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    if ("policy".equals(name)) {
                        return policy;
                    }
                }
            }
        } catch (Exception e) {
            error("Failed to load executors");
        }
        return null;
    }
}
