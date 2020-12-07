package com.sprd.ext.customizeappsort;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Process;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.SparseArray;
import android.util.Xml;

import com.android.launcher3.AppInfo;
import com.android.launcher3.R;
import com.sprd.ext.FeatureOption;
import com.sprd.ext.LogUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by SPRD on 2017/2/20.
 */
public class CustomizeAppSort {
    private static final String TAG = "CustomizeAppSort";
    private static final String XML_ITEM_TAG = "App";
    private static final String POSITION_SEPARATOR = "#";
    private static final String CLASS_PACKAGE_SEPARATOR = "/";

    private SparseArray<Pair<String, String>> mCustomizePositions = new SparseArray<>();
    private boolean mHasCustomizeAppData = false;

    private static CustomizeAppSort INSTANCE;

    /**
     * private constructor here, It is a singleton class.
     */
    private CustomizeAppSort(final Context appContext) {
        if (FeatureOption.SPRD_ALLAPP_CUSTOMIZE_SUPPORT) {
            mCustomizePositions = loadCustomizeAppPositions(appContext);
            if (mCustomizePositions.size() <= 0) {
                mCustomizePositions = loadCustomizeAppPosFromRes(appContext);
            }
            mHasCustomizeAppData = mCustomizePositions.size() > 0;
            LogUtils.d(TAG, "load config done:" + mCustomizePositions.toString());
        }
    }

    private SparseArray<Pair<String,String>> loadCustomizeAppPosFromRes(Context appContext) {
        SparseArray<Pair<String, String>> customizePositions = new SparseArray<>();

        String[] array = appContext.getResources().getStringArray(R.array.customize_app_position);
        for (int i = 0; i < array.length; i++) {
            String pkgName, clsName;
            int position;
            // separate class&package name, position
            String[] separteByPos = array[i].split(POSITION_SEPARATOR, 2);
            if (separteByPos.length < 2) {
                LogUtils.w(TAG,"customize app info must contains '#', string is : " + array[i]);
                continue;
            }
            String pkgClsName = separteByPos[0];
            try{
                position = Integer.parseInt(separteByPos[1]);
            } catch (NumberFormatException e) {
                LogUtils.w(TAG, "position must be a number : " + array[i]);
                continue;
            }

            // separate class name, package name
            String[] separteByClsPkg = pkgClsName.split(CLASS_PACKAGE_SEPARATOR, 2);
            if (separteByClsPkg.length < 2) {
                pkgName = pkgClsName;
                clsName = null;
            } else {
                pkgName = separteByClsPkg[0];
                clsName = separteByClsPkg[1].isEmpty() ? null : separteByClsPkg[1];
            }
            if (pkgName != null && pkgName.length() != 0) {
                customizePositions.put(position, new Pair<>(pkgName, clsName));
            }
        }
        return customizePositions;
    }

    public static CustomizeAppSort getInstance(final Context context) {
        if (INSTANCE == null) {
            INSTANCE = new CustomizeAppSort(context.getApplicationContext());
        }
        return INSTANCE;
    }

    public final void sortApps(final List<AppInfo> apps) {
        if (!FeatureOption.SPRD_ALLAPP_CUSTOMIZE_SUPPORT || !mHasCustomizeAppData) {
            return;
        }
        ArrayList<AppInfo> sortApps = new ArrayList<>();
        sortApps.addAll(apps);
        onSortApps(sortApps);

        // refresh mApps
        apps.clear();
        apps.addAll(sortApps);
    }

    private void onSortApps(final ArrayList<AppInfo> originalApps) {
        TreeMap<Integer, AppInfo> sortedMaps = new TreeMap<>();
        ArrayList<AppInfo> cloneAppList = new ArrayList<>();

        // find the customize component in componentNames
        Pair<String, String> pair;
        for (AppInfo app : originalApps) {
            for (int i = 0; i < mCustomizePositions.size(); i++) {
                ComponentName cn = app.componentName;
                pair = mCustomizePositions.valueAt(i);
                if (pair.first.equals(cn.getPackageName() )) {
                    if (pair.second == null || pair.second.equals(cn.getClassName())) {
                        if (app.user.equals(Process.myUserHandle())) {
                            sortedMaps.put(mCustomizePositions.keyAt(i), app);
                        } else {
                            cloneAppList.add(app);
                        }
                    }
                }

            }
        }

        // insert clone app
        if (!cloneAppList.isEmpty()) {
            LogUtils.d(TAG, "onSortApps, cloneAppList.size():" + cloneAppList.size());
            for (AppInfo app : cloneAppList) {
                insertCloneApp(app, sortedMaps);
            }
        }

        LogUtils.d(TAG, "onSortApps, sortedMaps keys:" + sortedMaps.keySet().toString());
        if (LogUtils.DEBUG_ALL) {
            LogUtils.d(TAG, "onSortApps, sortedMaps:" + maps2String(sortedMaps));
            LogUtils.d(TAG, "onSortApps, need sort apps:" + apps2String(originalApps));
        }

        // remove the found component
        for (Map.Entry<Integer, AppInfo> integerAppInfoEntry : sortedMaps.entrySet()) {
            originalApps.remove(integerAppInfoEntry.getValue());
        }

        // insert at the customize position
        for (Map.Entry<Integer, AppInfo> integerAppInfoEntry : sortedMaps.entrySet()) {
            if (integerAppInfoEntry.getKey() > originalApps.size()) {
                // append to last position
                originalApps.add(integerAppInfoEntry.getValue());
            } else {
                // insert at specific position
                originalApps.add(integerAppInfoEntry.getKey(), integerAppInfoEntry.getValue());
            }
        }
        if (LogUtils.DEBUG_ALL) {
            LogUtils.d(TAG, "onSortApps, sorted apps:" + apps2String(originalApps));
        }
    }

    private static String maps2String(TreeMap<Integer, AppInfo> maps) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, AppInfo> infoEntry : maps.entrySet()) {
            AppInfo app = infoEntry.getValue();
            sb.append("\n[ ")
                    .append(infoEntry.getKey())
                    .append(" -> ").append(app.toComponentKey().toString())
                    .append(" ]");
        }
        return sb.toString();
    }

    private static String apps2String(final List<AppInfo> apps) {
        TreeMap<Integer, AppInfo> maps = new TreeMap<>();
        for (int i = 0; i < apps.size(); i++) {
            maps.put(i, apps.get(i));
        }
        return maps2String(maps);
    }

    private static void insertCloneApp(AppInfo cloneApp, TreeMap<Integer, AppInfo> sortedAppMaps) {
        if (sortedAppMaps.containsValue(cloneApp)) {
            return;
        }

        int findKey = -1;
        for (Map.Entry<Integer, AppInfo> integerAppInfoEntry : sortedAppMaps.entrySet()) {
            AppInfo me = integerAppInfoEntry.getValue();
            int key = integerAppInfoEntry.getKey();
            if (cloneApp.componentName.equals(me.componentName)) {
                findKey = key;
                LogUtils.d(TAG, "insertCloneApp, find owner app:[" + findKey + "] " + me.componentName);
                break;
            }
        }

        if (findKey != -1) {
            int lastKey = sortedAppMaps.lastKey();
            for (int i = findKey + 1; i <= lastKey + 1; i++) {
                if (sortedAppMaps.get(i) == null) {
                    LogUtils.d(TAG, "insertCloneApp, find empty grid:[" + i + "] ");
                    sortedAppMaps.put(i, cloneApp);
                    return;
                }
            }
        }
    }

    /**
     * Get customize app's position. The result is a map, the key indicate the
     * customize position, and the value is a pair of package name and class
     * name.
     * @param context cTx
     * @return customize app map
     */
    private static SparseArray<Pair<String, String>> loadCustomizeAppPositions(final Context context) {
        SparseArray<Pair<String, String>> customizePositions = new SparseArray<>();
        try {
            XmlResourceParser parser = context.getResources().getXml(R.xml.customize_app_positions);
            AttributeSet attrs = Xml.asAttributeSet(parser);

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if (XML_ITEM_TAG.equals(tagName)) {
                        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SprdAppInfo);
                        String pkgName = a.getString(R.styleable.SprdAppInfo_pkgName);
                        String clsName = a.getString(R.styleable.SprdAppInfo_clsName);
                        int position = a.getInteger(R.styleable.SprdAppInfo_position, 0);

                        // package name must not be null or empty
                        if (pkgName != null && pkgName.length() != 0) {
                            customizePositions.put(position, new Pair<>(pkgName, clsName));
                        }
                        a.recycle();
                    }
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException | IOException | RuntimeException e) {
            LogUtils.w(TAG, "parse xml failed", e);
        }
        return customizePositions;
    }

}
