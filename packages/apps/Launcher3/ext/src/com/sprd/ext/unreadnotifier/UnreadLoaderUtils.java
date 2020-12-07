package com.sprd.ext.unreadnotifier;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.UserHandle;
import android.view.View;
import com.android.launcher3.AppInfo;
import com.android.launcher3.FolderInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Workspace;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AlphabeticalAppsList;
import com.android.launcher3.util.ComponentKey;
import com.sprd.ext.BadgeUtils;
import com.sprd.ext.LauncherAppMonitor;
import com.sprd.ext.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.android.launcher3.LauncherSettings;
//import com.android.launcher3.util.RunnableWithId;

/**
 * Created by SPRD on 2016/9/27.
 */
class UnreadSupportShortcut {
    UnreadSupportShortcut(String keyString, UserHandle userHandle, int type) {
        mComponent = ComponentName.unflattenFromString(keyString);
        mKey = keyString;
        mShortcutType = type;
        mUnreadNum = 0;
        mUserHandle = userHandle;
    }

    ComponentName mComponent;
    String mKey;
    int mShortcutType;
    int mUnreadNum;
    UserHandle mUserHandle;

    @Override
    public String toString() {
        return "{UnreadSupportShortcut[" + mComponent + "], key = " + mKey + ",type = "
                + mShortcutType + ",unreadNum = " + mUnreadNum + "}";
    }
}

/**
 * This class is a util class, implemented to do the following two things,:
 *
 * 1.
 *
 * 2. Receive unread broadcast sent by application, update shortcuts and folders in
 * workspace, hot seat and update application icons in app customize paged view.
 */
public class UnreadLoaderUtils extends BroadcastReceiver {
    private static final String TAG = "UnreadLoaderUtils";

    private static final String PREFS_FILE_NAME = TAG + "_Pref";
    private static final int UNREAD_TYPE_INTERNAL = 0;
    private static final int UNREAD_TYPE_EXTERNAL = 1;

    private static final int MAX_UNREAD_COUNT = 99;
    static final int INVALID_NUM = -1;

    public static final String ACTION_UNREAD_CHANGED = "com.sprd.action.UNREAD_CHANGED";
    private static final String EXTRA_UNREAD_COMPONENT = "com.sprd.intent.extra.UNREAD_COMPONENT";
    private static final String EXTRA_UNREAD_NUMBER = "com.sprd.intent.extra.UNREAD_NUMBER";

    private static final int MSG_UNREADINFO_UPDATED = 1;
    private static final int MSG_UNREADINFO_WORKSPACE_REFRESH = 2;
    private static final int MSG_UNREADINFO_APPS_REFRESH = 3;
    private boolean isRegister = false;

    private final Handler mWorkerHandler;
    private final Handler mUiHandler;
    private Map<ComponentKey, Integer> mUpdatedUnreadData = new HashMap<>();

    private static final ArrayList<UnreadSupportShortcut> UNREAD_SUPPORT_SHORTCUTS =
            new ArrayList<>();
    private static int sUnreadSupportShortcutsNum = 0;

    private static final Object LOG_LOCK = new Object();

    private Context mContext;
    private SharedPreferences mSharePrefs;

    private static UnreadLoaderUtils INSTANCE;

    private static UnreadChangedListener sUnreadChangedListener;

    public static UnreadLoaderUtils getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new UnreadLoaderUtils(context.getApplicationContext());
        }
        return INSTANCE;
    }

    void registerUnreadReceiver() {
        if(!isRegister) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_UNREAD_CHANGED);
            LogUtils.d(TAG, "registerUnreadReceiver: this = " + this);
            mContext.registerReceiver(this, filter);
            isRegister = true;
        }
    }

    void unRegisterUnreadReceiver() {
        if(isRegister) {
            isRegister = false;
            LogUtils.d(TAG, "unRegisterUnreadReceiver: this = " + this);
            mContext.unregisterReceiver(this);
        }
    }

    private UnreadLoaderUtils(Context context) {
        mContext = context;
        mSharePrefs = mContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE);
        mWorkerHandler = new Handler(LauncherModel.getWorkerLooper(), mWorkerCallback);
        mUiHandler = new Handler(Looper.getMainLooper(), mUiCallback);
    }

    private Handler.Callback mWorkerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case MSG_UNREADINFO_UPDATED:
                    mUiHandler.obtainMessage(message.what, message.obj).sendToTarget();
                    break;
                case MSG_UNREADINFO_WORKSPACE_REFRESH:
                    mUiHandler.obtainMessage(message.what).sendToTarget();
                    break;
                case MSG_UNREADINFO_APPS_REFRESH:
                    mUiHandler.obtainMessage(message.what, message.obj).sendToTarget();
                    break;
            }
            return true;
        }
    };

    private Handler.Callback mUiCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case MSG_UNREADINFO_UPDATED:
                    if (sUnreadChangedListener != null) {
                        UnreadKeyData info = (UnreadKeyData) message.obj;
                        sUnreadChangedListener.onUnreadInfoChanged(info);
                    }
                    break;
                case MSG_UNREADINFO_WORKSPACE_REFRESH:
                    if (sUnreadChangedListener != null) {
                        sUnreadChangedListener.onWorkspaceUnreadInfoRefresh();
                    }
                    break;
                case MSG_UNREADINFO_APPS_REFRESH:
                    if (sUnreadChangedListener != null) {
                        ArrayList<AppInfo> apps = (ArrayList<AppInfo>) message.obj;
                        sUnreadChangedListener.onAppsUnreadInfoRefresh(apps);
                    }
                    break;
            }
            return true;
        }
    };

    public void initializeUnreadInfo(UnreadChangedListener listener) {
        sUnreadChangedListener = listener;

        UnreadInfoManager.getInstance(mContext).createItemIfNeeded();
        UnreadInfoManager.getInstance(mContext).initAppsAndPermissionList();

        loadInitialUnreadInfo();
        UnreadInfoManager.getInstance(mContext).initUnreadInfo();
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        if (ACTION_UNREAD_CHANGED.equals(action)) {
            final ComponentName componentName = ComponentName.unflattenFromString(
                     intent.getStringExtra(EXTRA_UNREAD_COMPONENT));
            final int unreadNum = intent.getIntExtra(EXTRA_UNREAD_NUMBER, INVALID_NUM);

            updateComponentUnreadInfo(unreadNum , componentName, Process.myUserHandle());
        }
    }

    public void updateComponentUnreadInfo(int unreadNum, ComponentName componentName, UserHandle userHandle) {
        if (LogUtils.DEBUG_UNREAD) {
            LogUtils.d(TAG, "[[[ updateComponentUnreadInfo ]]]: componentName = " + componentName
                    + ", unreadNum = " + unreadNum);
        }

        if (componentName != null && unreadNum != INVALID_NUM && userHandle != null) {
            saveAndUpdateUI(componentName, unreadNum, userHandle);
        }
    }

    public void refreshWorkspaceUnreadInfoIfNeeded() {
        mWorkerHandler.obtainMessage(MSG_UNREADINFO_WORKSPACE_REFRESH).sendToTarget();
    }

    public void refreshAppsUnreadInfoIfNeeded(List<AppInfo> apps) {
        mWorkerHandler.obtainMessage(MSG_UNREADINFO_APPS_REFRESH, apps).sendToTarget();
    }

    private void saveAndUpdateUI(final ComponentName component, final int unReadNum, UserHandle userHandle) {
        ComponentKey componentKey = new ComponentKey(component, userHandle);
        final String sharedPrefKey = componentKey.flattenToString(mContext);
        final int index = supportUnreadFeature(component, userHandle);
        boolean needUpdate = false;
        if (index != INVALID_NUM) {
            if (UNREAD_SUPPORT_SHORTCUTS.get(index).mUnreadNum != unReadNum) {
                saveUnreadNum(sharedPrefKey, unReadNum);
                needUpdate = true;
            }
        } else {
            //add new info
            if (unReadNum > 0) {
                saveUnreadNum(sharedPrefKey, unReadNum);
                needUpdate = true;
            }
        }

        if (needUpdate) {
            if (index != INVALID_NUM) {
                UNREAD_SUPPORT_SHORTCUTS.get(index).mUnreadNum = unReadNum;
                if (LogUtils.DEBUG_UNREAD) {
                    LogUtils.d(TAG, "saveAndUpdateUI,update SupportList, key:" + sharedPrefKey + " success.");
                }
            } else {
                UnreadSupportShortcut usShortcut = new UnreadSupportShortcut(
                        component.flattenToShortString(), userHandle, UNREAD_TYPE_EXTERNAL);
                usShortcut.mUnreadNum = unReadNum;
                UNREAD_SUPPORT_SHORTCUTS.add(usShortcut);
                sUnreadSupportShortcutsNum = UNREAD_SUPPORT_SHORTCUTS.size();
                if (LogUtils.DEBUG_UNREAD) {
                    LogUtils.d(TAG, "saveAndUpdateUI, add To SupportList, key:" + sharedPrefKey + " success."
                            + getUnreadSupportShortcutInfo());
                }
            }

            UnreadKeyData unreadKeyData = new UnreadKeyData(new ComponentKey(component,userHandle), unReadNum);
            mWorkerHandler.obtainMessage(MSG_UNREADINFO_UPDATED, unreadKeyData)
                    .sendToTarget();
        }
    }

    private int readUnreadNum(final String key) {
        return mSharePrefs.getInt(key, INVALID_NUM);
    }

    private boolean saveUnreadNum(final String key, final int unReadNum) {
        SharedPreferences.Editor editor = mSharePrefs.edit();
        editor.putInt(key, unReadNum).apply();
        return true;
    }

    private boolean deleteUnreadNum(final String key) {
        SharedPreferences.Editor editor = mSharePrefs.edit();
        editor.remove(key).apply();
        return true;
    }

    /**
     * Get unread support shortcut information, since the information are stored
     * in an array list, we may query it and modify it at the same time, a lock
     * is needed.
     *
     * @return SupportShortString
     */
    private static String getUnreadSupportShortcutInfo() {
        String info = " Unread support shortcuts are ";
        ArrayList<UnreadSupportShortcut> logList = new ArrayList<>(UNREAD_SUPPORT_SHORTCUTS);
        synchronized (LOG_LOCK) {
            info += logList.toString();
        }
        return info;
    }

    /**
     * Whether the given component support unread feature.
     *
     * @param component component
     * @return array index, find fail return INVALID_NUM
     */
    static int supportUnreadFeature(ComponentName component, UserHandle userHandle) {
        if (component == null) {
            return INVALID_NUM;
        }

        final int size = UNREAD_SUPPORT_SHORTCUTS.size();
        for (int i = 0; i < size; i++) {
            UnreadSupportShortcut supportShortcut = UNREAD_SUPPORT_SHORTCUTS.get(i);
            if (supportShortcut.mComponent.equals(component) && supportShortcut.mUserHandle.equals(userHandle)) {
                return i;
            }
        }

        return INVALID_NUM;
    }

    private void loadInitialUnreadInfo() {
        long start = System.currentTimeMillis();
        if (LogUtils.DEBUG_UNREAD) {
            LogUtils.d(TAG, "loadUnreadSupportShortcuts begin: start = " + start);
        }

        UNREAD_SUPPORT_SHORTCUTS.clear();
        for (String sharedPrefKey : mSharePrefs.getAll().keySet()) {
            boolean needCreatePackageUserKey = false;
            ComponentKey componentKey = new ComponentKey(mContext, sharedPrefKey);
            ComponentName componentName = componentKey.componentName;
            UserHandle userHandle = componentKey.user;
            String key = componentName.flattenToShortString();
            int loadNum = 0;
            if (!UnreadInfoManager.getInstance(mContext).isDeniedPermissionItem(key)) {
                loadNum = readUnreadNum(sharedPrefKey);
                needCreatePackageUserKey = loadNum > 0;
            }

            if (needCreatePackageUserKey) {
                UnreadSupportShortcut usShortcut = new UnreadSupportShortcut(
                        key, userHandle, UNREAD_TYPE_INTERNAL );
                usShortcut.mUnreadNum = loadNum;
                if (!UNREAD_SUPPORT_SHORTCUTS.contains( usShortcut )) {
                    UNREAD_SUPPORT_SHORTCUTS.add(usShortcut);
                }
            } else {
                deleteUnreadNum(sharedPrefKey);
            }
        }
        sUnreadSupportShortcutsNum = UNREAD_SUPPORT_SHORTCUTS.size();

        if (LogUtils.DEBUG_UNREAD) {
            LogUtils.d(TAG, "loadUnreadSupportShortcuts end: time used = "
                    + (System.currentTimeMillis() - start) + ",sUnreadSupportShortcutsNum = "
                    + sUnreadSupportShortcutsNum + getUnreadSupportShortcutInfo());
        }
    }

    //init
    public void onWorkspaceUnreadInfoRefresh() {
        //Full refresh shortcut and folder unreadInfo
        refreshShortcutsAndFoldersUnread();
    }

    public void onAppsUnreadInfoRefresh(ArrayList<AppInfo> apps) {
        //Full refresh all apps unreadInfo
        refreshAppsUnreadInfo(apps);
    }

    Runnable mUnreadChangeRunnable = new Runnable()/*RunnableWithId(RunnableWithId.RUNNABLE_ID_UNREAD_UPDATEDATA)*/ {
        @Override
        public void run() {
            HashMap<ComponentKey, Integer> updatedUnreadData = new HashMap<>(mUpdatedUnreadData);
            if(LogUtils.DEBUG_UNREAD) {
                LogUtils.d(TAG, "onUnreadInfoChanged, execute runnable, unreadData.size: "+updatedUnreadData.size());
            }
            mUpdatedUnreadData.clear();
            for(ComponentKey key:updatedUnreadData.keySet()) {
                int unreadNum = updatedUnreadData.get(key);
                updateWorkspaceUnreadInfo(key, unreadNum);
                updateAppsUnreadInfo(key, unreadNum);
            }
        }
    };

    //update
    public void onUnreadInfoChanged(final UnreadKeyData info) {
        Launcher launcher = LauncherAppMonitor.getInstanceNoCreate().getLauncher();
        if(launcher == null || info == null) {
            return;
        }

        mUpdatedUnreadData.put(info.getComponentKey(), info.getUnreadNum());
        //if(!launcher.waitUntilResume(mUnreadChangeRunnable,true)) {
        //if(!launcher.waitUntilResume(mUnreadChangeRunnable)) {
            mUnreadChangeRunnable.run();
        //}
    }

    private void updateWorkspaceUnreadInfo(ComponentKey key, int unreadNum) {
        Launcher launcher = LauncherAppMonitor.getInstanceNoCreate().getLauncher();
        if(launcher != null) {
            Workspace workspace = launcher.getWorkspace();

            final ArrayList<ShortcutAndWidgetContainer> containers =
                    workspace.getAllShortcutAndWidgetContainers();
            ComponentName componentName = key.componentName;
            UserHandle userHandle = key.user;
            for(ShortcutAndWidgetContainer container:containers) {
                final int itemCount = container.getChildCount();
                for (int itemIdx = 0; itemIdx < itemCount; itemIdx++) {
                    View item = container.getChildAt(itemIdx);
                    ItemInfo info = (ItemInfo) item.getTag();
                    if (info instanceof FolderInfo) {
                        if(updateFolderUnreadNum((FolderInfo)info, componentName, unreadNum, userHandle)){
                            item.invalidate();
                        }
                    } else if(info instanceof ShortcutInfo){
                        final ShortcutInfo shortcutInfo = (ShortcutInfo) info;
                        final Intent intent = shortcutInfo.getIntent();
                        final ComponentName component = intent.getComponent();
                        if (component != null && component.equals(componentName) && info.user.equals(userHandle)) {
                            if (shortcutInfo.unreadNum != unreadNum) {
                                shortcutInfo.unreadNum = unreadNum;
                                item.invalidate();
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateAppsUnreadInfo(ComponentKey key, int unreadNum) {
        Launcher launcher = LauncherAppMonitor.getInstanceNoCreate().getLauncher();
        if(launcher!= null) {
            AllAppsContainerView allAppsContainerView = launcher.getAppsView();
            if(allAppsContainerView != null) {
                AlphabeticalAppsList apps = allAppsContainerView.getApps();
                ComponentName componentName = key.componentName;
                UserHandle userHandle = key.user;
                List<AppInfo> updateApps =  new ArrayList<>();
                for (AppInfo appInfo: apps.getApps()) {
                    ComponentName name = appInfo.intent.getComponent();
                    if (name != null && name.equals(componentName) && appInfo.user.equals(userHandle)) {
                        if(appInfo.unreadNum != unreadNum) {
                            appInfo.unreadNum = unreadNum;
                            updateApps.add(appInfo);
                        }
                    }
                }
                allAppsContainerView.getAppsStore().addOrUpdateApps(updateApps);
            }
        }
    }

    public static boolean updateFolderUnreadNum(FolderInfo folderInfo, ComponentName componentName, int unreadNum, UserHandle userHandle) {
        if (folderInfo == null) {
            return false;
        }

        int unreadNumTotal = 0;
        ComponentName name;
        final ArrayList<ComponentName> components = new ArrayList<>();
        for (ShortcutInfo si : folderInfo.contents) {
            name = si.getTargetComponent();
            if (name != null && name.equals(componentName) && si.user.equals(userHandle)) {
                si.unreadNum = unreadNum;
            }
            if (si.unreadNum > 0 && isUnreadItemType(si.itemType)) {
                int j;
                for (j = 0; j < components.size(); j++) {
                    if (name != null && name.equals(components.get(j))) {
                        break;
                    }
                }

                if (j >= components.size()) {
                    components.add(name);
                    unreadNumTotal += si.unreadNum;
                }
            }
        }

        if (LogUtils.DEBUG_UNREAD) {
            LogUtils.d(TAG, "updateFolderUnreadNum, end: unreadNumTotal = " + unreadNumTotal);
        }

        return setFolderUnreadNum(folderInfo, unreadNumTotal);
    }

    public static boolean setFolderUnreadNum(FolderInfo folderInfo, int unreadNum) {
        if (folderInfo == null) {
            return false;
        }
        if (LogUtils.DEBUG_UNREAD) {
            LogUtils.d(TAG, "setFolderUnreadNum: unreadNum = " + unreadNum + ", info = " + folderInfo);
        }

        if (unreadNum <= 0) {
            unreadNum = 0;
        }

        if (unreadNum != folderInfo.unreadNum) {
            folderInfo.unreadNum = unreadNum;
            return true;
        }
        return false;
    }


    static synchronized int getUnreadNumberAt(int index) {
        if (index < 0 || index >= sUnreadSupportShortcutsNum) {
            return 0;
        }
        return UNREAD_SUPPORT_SHORTCUTS.get(index).mUnreadNum;
    }

    public static int getUnreadNumberOfComponent(ComponentName component, UserHandle userHandle) {
        final int index = supportUnreadFeature(component, userHandle);
        return getUnreadNumberAt(index);
    }

    //Full refresh shortcut and folder unreadInfo
    private void refreshShortcutsAndFoldersUnread() {
        Launcher launcher = LauncherAppMonitor.getInstanceNoCreate().getLauncher();
        if(launcher!= null) {
            Workspace workspace = launcher.getWorkspace();
            final ArrayList<ShortcutAndWidgetContainer> containers =
                    workspace.getAllShortcutAndWidgetContainers();
            for (ShortcutAndWidgetContainer container : containers) {
                final int itemCount = container.getChildCount();
                for (int itemIdx = 0; itemIdx < itemCount; itemIdx++) {
                    View item = container.getChildAt(itemIdx);
                    ItemInfo info = (ItemInfo) item.getTag();
                    if (info instanceof FolderInfo) {
                        if (updateFolderUnreadNum((FolderInfo) info)) {
                            item.invalidate();
                        }
                    } else if (info instanceof ShortcutInfo) {
                        final ShortcutInfo shortcutInfo = (ShortcutInfo) info;
                        final Intent intent = shortcutInfo.getIntent();
                        final ComponentName component = intent.getComponent();
                        final int num = getUnreadNumberOfComponent(component, shortcutInfo.user);
                        if (info.unreadNum != num) {
                            info.unreadNum = num;
                            item.invalidate();
                        }
                    }
                }
            }
        }
    }

    public boolean updateFolderUnreadNum(FolderInfo folderInfo) {
        if (folderInfo == null) {
            return false;
        }

        int unreadNumTotal = 0;
        final ArrayList<ComponentName> components = new ArrayList<>();
        ComponentName componentName;
        int unreadNum;
        for (ShortcutInfo si : folderInfo.contents) {
            componentName = si.getIntent().getComponent();
            unreadNum = getUnreadNumberOfComponent(componentName, si.user);
            if (unreadNum > 0 && isUnreadItemType(si.itemType)) {
                si.unreadNum = unreadNum;
                int j;
                for (j = 0; j < components.size(); j++) {
                    if (componentName != null && componentName.equals(components.get(j))) {
                        break;
                    }
                }

                if (j >= components.size()) {
                    components.add(componentName);
                    unreadNumTotal += unreadNum;
                }
            }
        }

        if (LogUtils.DEBUG_UNREAD) {
            LogUtils.d(TAG, "updateFolderUnreadNum end: unreadNumTotal = " + unreadNumTotal);
        }
        return setFolderUnreadNum(folderInfo, unreadNumTotal);
    }

    //Full refresh all apps unreadInfo
    private void refreshAppsUnreadInfo(ArrayList<AppInfo> apps) {
        Launcher launcher = LauncherAppMonitor.getInstanceNoCreate().getLauncher();
        if(launcher != null) {
            AllAppsContainerView allAppsContainerView = launcher.getAppsView();
            if(allAppsContainerView!=null) {
                if(LogUtils.DEBUG_UNREAD) {
                    LogUtils.d(TAG, "refreshAppsUnreadInfo: apps.size: "+apps.size());
                }
                List<AppInfo> updateApps =  new ArrayList<>();
                for (AppInfo appInfo: apps) {
                    ComponentName name = appInfo.getTargetComponent();
                    int num = getUnreadNumberOfComponent(name, appInfo.user);
                    if(appInfo.unreadNum != num) {
                        appInfo.unreadNum = num;
                        updateApps.add(appInfo);
                    }
                }
                allAppsContainerView.getAppsStore().addOrUpdateApps(updateApps);
            }
        }
    }

    /**
     * Draw unread number for the given icon.
     *
     * @param canvas
     * @param icon
     * @param unreadCount
     * @return
     */
    public static void drawUnreadEventIfNeed(Canvas canvas, View icon, int unreadCount) {
        if (icon.getTag() instanceof ItemInfo) {
            ItemInfo info = (ItemInfo) icon.getTag();
            if (unreadCount > 0 && isUnreadItemType(info.itemType)) {
                String unreadText;
                if (unreadCount > MAX_UNREAD_COUNT) {
                    unreadText = Integer.toString(MAX_UNREAD_COUNT) + "+";
                } else {
                    unreadText = Integer.toString(unreadCount);
                }
                BadgeUtils.drawBadge(canvas, icon, unreadText);
            }
        }
    }

    public interface UnreadChangedListener {
        void onUnreadInfoChanged(UnreadKeyData info);
        void onWorkspaceUnreadInfoRefresh();
        void onAppsUnreadInfoRefresh(ArrayList<AppInfo> apps);
    }

    public static boolean isUnreadItemType(int itemType) {
        boolean ret = false;

        switch (itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                ret = true;
                break;
            default:
                break;
        }
        return ret;
    }
    public void unregisterItemContentObservers(){
        UnreadInfoManager.getInstance(mContext).unregisterItemContentObservers();
    }

}
