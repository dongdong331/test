
package com.android.systemui.qs;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings.Global;
import android.telephony.SubscriptionManager;

import com.android.ims.ImsManager;
import com.android.systemui.statusbar.policy.Listenable;

public abstract class SubscriptionSetting extends ContentObserver implements Listenable {
    private final Context mContext;
    private final String mSettingName;

    protected abstract void handleValueChanged(boolean value);

    public SubscriptionSetting(Context context, Handler handler, String settingName) {
        super(handler);
        mContext = context;
        mSettingName = settingName;
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mContext.getContentResolver().registerContentObserver(
                    SubscriptionManager.CONTENT_URI, false, this);
        } else {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    @Override
    public void onChange(boolean selfChange) {
        handleValueChanged(ImsManager.isWfcEnabledByUser(mContext));
    }
}
