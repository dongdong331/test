/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.content.DialogInterface.OnClickListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.quicksettings.Tile;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import com.android.internal.telephony.TelephonyIntents;
import android.widget.Switch;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.Prefs;
import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.statusbar.policy.DataSaverController;
import com.android.systemui.statusbar.policy.NetworkController;
import java.util.List;

public class DataSaverTile extends QSTileImpl<BooleanState> implements
        DataSaverController.Listener{

    private final DataSaverController mDataSaverController;

    public DataSaverTile(QSHost host) {
        super(host);
        mDataSaverController = Dependency.get(NetworkController.class).getDataSaverController();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (listening) {
            mDataSaverController.addCallback(this);
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SIM_STATE_CHANGED);
            mContext.registerReceiver(mReceiver, filter);
        } else {
            mDataSaverController.removeCallback(this);
             mContext.unregisterReceiver(mReceiver);
        }
    }

    private boolean hasSimCard() {
        SubscriptionManager subscriptionManager = SubscriptionManager.from(mContext);
        List<SubscriptionInfo> sil = subscriptionManager.getActiveSubscriptionInfoList();
        android.util.Log.d("xxrrsave","sil="+sil);
        return (sil != null);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                refreshState();
            }
        }
    };
    @Override
    public Intent getLongClickIntent() {
        if (mContext.getResources().getBoolean(R.bool.config_show_data_usage_summary)) {
            return CellularTile.getCellularSettingIntent();
        } else {
            return new Intent();
        }
    }
    @Override
    protected void handleClick() {
        if (!hasSimCard()) {
            return;
        }
        if (mState.value
                || Prefs.getBoolean(mContext, Prefs.Key.QS_DATA_SAVER_DIALOG_SHOWN, false)) {
            // Do it right away.
            toggleDataSaver();
            return;
        }
        // Shows dialog first
        SystemUIDialog dialog = new SystemUIDialog(mContext);
        dialog.setTitle(com.android.internal.R.string.data_saver_enable_title);
        dialog.setMessage(com.android.internal.R.string.data_saver_description);
        dialog.setPositiveButton(com.android.internal.R.string.data_saver_enable_button,
                (OnClickListener) (dialogInterface, which) -> toggleDataSaver());
        dialog.setNegativeButton(com.android.internal.R.string.cancel, null);
        dialog.setShowForAllUsers(true);
        dialog.show();
        Prefs.putBoolean(mContext, Prefs.Key.QS_DATA_SAVER_DIALOG_SHOWN, true);
    }

    @Override
    protected void handleLongClick() {
        if (!hasSimCard()) {
            return;
        }
        super.handleLongClick();
    }
    private void toggleDataSaver() {
        mState.value = !mDataSaverController.isDataSaverEnabled();
        mDataSaverController.setDataSaverEnabled(mState.value);
        refreshState(mState.value);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.data_saver);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = arg instanceof Boolean ? (Boolean) arg
                : mDataSaverController.isDataSaverEnabled();
        state.state = !hasSimCard() ? Tile.STATE_UNAVAILABLE
                   : state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        state.label = mContext.getString(R.string.data_saver);
        state.contentDescription = state.label;
        state.icon = ResourceIcon.get(state.value ? R.drawable.ic_data_saver
                : R.drawable.ic_data_saver_off);
        state.expandedAccessibilityClassName = Switch.class.getName();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_DATA_SAVER;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_data_saver_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_data_saver_changed_off);
        }
    }

    @Override
    public void onDataSaverChanged(boolean isDataSaving) {
        refreshState(isDataSaving);
    }
}
