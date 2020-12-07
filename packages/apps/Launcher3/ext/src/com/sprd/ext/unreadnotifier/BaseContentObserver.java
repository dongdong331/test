package com.sprd.ext.unreadnotifier;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;

import com.sprd.ext.LogUtils;

/**
 * Created by SPRD on 2016/11/23.
 */

public class BaseContentObserver extends ContentObserver {
    private static final String TAG = "BaseContentObserver";
    private Uri mUri;
    private Context mContext;
    private UnreadBaseItem mItem;
    private Handler mHandler = new Handler();
    private static final int OBSERVER_HANDLER_DELAY = 1000;
    private boolean isRegister = false;

    public BaseContentObserver(Handler handler,Context context,Uri uri, UnreadBaseItem item) {
        super(handler);
        mContext = context;
        mUri = uri;
        mItem = item;
    }

    void registerContentObserver() {
        if (!isRegister) {
            mContext.getContentResolver().registerContentObserver(mUri, true, this);
            isRegister = true;
        }
    }

    void unregisterContentObserver() {
        if (isRegister) {
            isRegister = false;
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    private Runnable changeRunnable = new Runnable() {
        @Override
        public void run() {
            if (mItem != null) {
                mItem.updateUIFromDatabase();
            }
        }
    };

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);
        if(LogUtils.DEBUG_UNREAD) {
            LogUtils.d(TAG, String.format("onChange: uri=%s selfChange=%b", uri.toString(), selfChange));
        }
        mHandler.removeCallbacks(changeRunnable);
        mHandler.postDelayed(changeRunnable, OBSERVER_HANDLER_DELAY);
    }

}
