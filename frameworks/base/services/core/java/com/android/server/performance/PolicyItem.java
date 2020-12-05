/*
 * Copyright © 2017 Spreadtrum Communications Inc.
 */
package com.android.server.performance;

import java.util.HashMap;

/**
 * Created by SPREADTRUM\joe.yu on 9/23/17.
 */

public class PolicyItem {
    private HashMap<String, PolicyConfig> mConfigs = new HashMap<String, PolicyConfig>();
    private String mName;

    public PolicyItem(String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public void addConfig(String name, PolicyConfig config) {
        mConfigs.put(name, config);
    }

    public PolicyConfig getConfig(String name) {
        return mConfigs.get(name);
    }

//    public static PolicyExecutorConfig loadConfigFromXml(String policyName, XmlPullParser in) {
//        int event;
//        PolicyExecutorConfig policy = new PolicyExecutorConfig(policyName);
//        try {
//            while (((event = in.next()) != XmlPullParser.END_DOCUMENT)) {
//                final String name = in.getName();
//                String confName = "";
//                if (event == XmlPullParser.START_TAG) {
//                    if ("config".equals(name)) {
//                        for (int attrNdx = in.getAttributeCount() - 1; attrNdx >= 0; --attrNdx) {
//                            final String attrName = in.getAttributeName(attrNdx);
//                            final String attrValue = in.getAttributeValue(attrNdx);
//                            if ("name".equals(attrName)) {
//                                confName = attrValue;
//                                break;
//                            }
//                        }
//                        PolicyConfig config = PolicyConfig.loadFromXml(confName, in);
//                        if (config != null) {
//                            policy.addConfig(confName, config);
//                        }
//                    }
//                } else if (event == XmlPullParser.END_TAG) {
//                    if ("policy".equals(name)) {
//                        return policy;
//                    }
//                }
//            }
//        } catch (XmlPullParserException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return policy;
//    }
}
