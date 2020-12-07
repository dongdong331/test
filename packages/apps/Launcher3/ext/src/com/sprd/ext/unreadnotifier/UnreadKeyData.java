package com.sprd.ext.unreadnotifier;

import android.content.ComponentName;
import android.os.UserHandle;

import com.android.launcher3.util.ComponentKey;

/**
 * Created by SPRD on 6/23/17.
 */

public class UnreadKeyData {
    private ComponentKey componentKey;
    private int unreadNum;

    UnreadKeyData(ComponentKey key, int num) {
        componentKey = key;
        unreadNum = num;
    }

    public ComponentKey getComponentKey() {
        return componentKey;
    }

    public ComponentName getUnreadComponentName() {
        return componentKey.componentName;
    }

    public UserHandle getUserHandle() {
        return  componentKey.user;
    }

    public int getUnreadNum() {
        return unreadNum;
    }
}
