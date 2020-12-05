/*
 * Copyright © 2017 Spreadtrum Communications Inc.
 */
package com.android.server.performance;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Created by SPREADTRUM\joe.yu on 9/23/17.
 */

public class PolicyConfig {
    private LinkedHashMap<String, ConfigItem> mItems = new LinkedHashMap<String, ConfigItem>();
    private String mName;

    public PolicyConfig(String name) {
        mName = name;
    }

    private void addConfigItem(ConfigItem item) {
        mItems.put(item.getItemValue(), item);
    }

    public ConfigItem getConfigItem(String key) {
        return mItems.get(key);
    }

    public ConfigItem getDefaultItem() {
        return mItems.entrySet().iterator().next().getValue();
    }

    public HashMap<String, ConfigItem> getAllConfigItems() {
        return mItems;
    }

    public static class ConfigItem {
        private String itemName;
        private String itemValue;
        private HashMap<String, String> mMap = new HashMap<String, String>();
        private HashMap<String, ArrayList<String>> mStringArrayMap = new HashMap<String, ArrayList<String>>();

        public String getItemName() {
            return itemName;
        }

        public String getItemValue() {
            return itemValue;
        }

        public ConfigItem(String itemName, String itemValue) {
            this.itemName = itemName;
            this.itemValue = itemValue;
        }

        public void add(String key, String value) {
            mMap.put(key, value);
        }

        public void addStringArray(String key, ArrayList<String> list) {
            mStringArrayMap.put(key, list);
        }

        public String getString(String key) {
            return mMap.get(key);
        }

        public ArrayList<String> getStringArray(String key) {
            return mStringArrayMap.get(key);
        }

        public HashMap<String, String> getAllString() {
            return mMap;
        }
    }
    /*
     * <string-array name="dummy"> <string>dumpy1</string>
     * <string>dumpy2</string> <string>dumpy3</string> </string-array>
     */

    public static PolicyConfig loadFromXml(String configName, XmlPullParser in) {
        int event;
        PolicyConfig config = new PolicyConfig(configName);
        ConfigItem item = null;
        ArrayList<String> list = null;
        try {
            while (((event = in.next()) != XmlPullParser.END_DOCUMENT)) {
                final String name = in.getName();
                if (event == XmlPullParser.START_TAG) {
                    if ("item".equals(name)) {
                        for (int attrNdx = in.getAttributeCount() - 1; attrNdx >= 0; --attrNdx) {
                            final String attrName = in.getAttributeName(attrNdx);
                            final String attrValue = in.getAttributeValue(attrNdx);
                            item = new ConfigItem(attrName, attrValue);
                            break;
                        }

                    } else if ("string-array".equals(name)) {
                        for (int attrNdx = in.getAttributeCount() - 1; attrNdx >= 0; --attrNdx) {
                            final String attrName = in.getAttributeName(attrNdx);
                            final String attrValue = in.getAttributeValue(attrNdx);
                            if ("name".equals(attrName)) {
                                if (item != null) {
                                    list = new ArrayList<String>();
                                    item.addStringArray(attrValue, list);
                                    break;
                                }
                            }

                        }

                    } else if ("string".equals(name)) {
                        if (list != null) {
                            list.add(in.nextText());
                        }
                    } else {
                        if (item != null) {
                            item.add(name, in.nextText());
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    if ("item".equals(name)) {
                        if (item != null) {
                            config.addConfigItem(item);
                        }
                    } else if ("config".equals(name)) {
                        return config;
                    }
                }
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
