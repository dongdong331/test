package com.sprd.ext.dynamicicon.clock;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;

import com.android.launcher3.R;
import com.sprd.ext.LogUtils;
import com.sprd.ext.SystemPropertiesUtils;
import com.sprd.ext.dynamicicon.BaseDynamicIcon;
import com.sprd.ext.dynamicicon.DynamicIconSettings;
import com.sprd.ext.dynamicicon.DynamicIconUtils;

import java.util.Calendar;

/**
 * Created on 5/30/18.
 */
public class OriginalClockIcon extends BaseDynamicIcon {
    private static final String TAG = "OriginalClockIcon";

    // It will reduce the system performance when showing the second hand in dynamic clock icon.
    // So make IS_SHOW_SECOND to be false in general.
    private static final boolean IS_SHOW_SECOND = SystemPropertiesUtils.getBoolean(
            "ro.launcher.dyclock.second", false);
    private static final boolean DBG = IS_SHOW_SECOND ?
            LogUtils.DEBUG_DYNAMIC_ICON_ALL : LogUtils.DEBUG_DYNAMIC_ICON;

    private Runnable mSecondTick;

    public OriginalClockIcon(Context context, String pkg) {
        super(context, pkg);
        mIcon = new DreamClock(context);

        mPreDynamic = DynamicIconUtils.getAppliedValue(context, mPkg,
                context.getResources().getBoolean(R.bool.dynamic_clock_default_state));
        mCurDynamic = DynamicIconUtils.getAppliedValue(context,
                DynamicIconSettings.PREF_KEY_ORIGINAL_CLOCK,
                context.getResources().getBoolean(R.bool.dynamic_clock_default_state));

        if (IS_SHOW_SECOND) {
            mSecondTick = new Runnable() {
                @Override
                public void run() {
                    if (mRegistered) {
                        if (DBG) {
                            LogUtils.d(TAG, "start update icon in second handler");

                        }
                        updateUI();
                        mHandler.postAtTime(this, SystemClock.uptimeMillis() / 1000 * 1000 + 1000);
                    }
                }
            };
        }
        mFilter.addAction(Intent.ACTION_TIME_TICK);
        mFilter.addAction(Intent.ACTION_TIME_CHANGED);
        mFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver();
    }

    @Override
    protected void onReceiverRegistered() {
        if (IS_SHOW_SECOND) {
            mHandler.removeCallbacks(mSecondTick);
            mHandler.postAtTime(mSecondTick,
                    SystemClock.uptimeMillis() / 1000 * 1000 + 1000);
        }

        super.onReceiverRegistered();
    }

    @Override
    protected void onReceiverUnregistered() {
        if (IS_SHOW_SECOND) {
            mHandler.removeCallbacks(mSecondTick);
        }

        super.onReceiverUnregistered();
    }

    @Override
    public String getDrawableContentNeedShown() {
        StringBuilder str = new StringBuilder();
        str.append(DynamicIconUtils.timeOfField(Calendar.HOUR))
                .append(DynamicIconUtils.timeOfField(Calendar.MINUTE));
        return str.toString();
    }

    private class DreamClock extends DynamicDrawable {
        private static final float ICON_SCALE = 0.9f;

        private Bitmap mBgBitmap;
        private Bitmap mCircleBitmap;
        private Bitmap mHourBitmap;
        private Bitmap mMinuteBitmap;
        private Bitmap mSecondBitmap;

        private int mBgWidth;
        private int mBgHeight;
        private int mCircleWidth;
        private int mCircleHeight;
        private int mCenterX;
        private int mCenterY;
        private int mStartX;
        private int mStartY;

        private Paint mPaint;

        private DreamClock(Context context) {
            super(context);
            mBgBitmap = BitmapFactory.decodeResource(mRes, R.drawable.ic_dial_plate);
            mCircleBitmap = BitmapFactory.decodeResource(mRes, R.drawable.ic_dial_circle);
            mHourBitmap = BitmapFactory.decodeResource(mRes, R.drawable.ic_dial_hour_hand);
            mMinuteBitmap = BitmapFactory.decodeResource(mRes, R.drawable.ic_dial_minute_hand);
            mSecondBitmap = BitmapFactory.decodeResource(mRes, R.drawable.ic_dial_minute_hand);

            mBgWidth = mBgBitmap.getWidth();
            mBgHeight = mBgBitmap.getHeight();
            mCircleWidth = mCircleBitmap.getWidth();
            mCircleHeight = mCircleBitmap.getHeight();
            mCenterX = mBgWidth / 2;
            mCenterY = mBgHeight / 2;
            mStartX = mCenterX - mCircleWidth / 2;
            mStartY = mCenterY - mCircleHeight / 2;

            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaint.setFilterBitmap(true);
        }

        @Override
        public Drawable create(Drawable icon) {
            final Canvas canvas = new Canvas();
            final Bitmap bitmap = Bitmap.createBitmap(mBgWidth, mBgHeight,
                    Bitmap.Config.ARGB_8888);
            canvas.setBitmap(bitmap);

            int hour = DynamicIconUtils.timeOfField(Calendar.HOUR);
            int minute = DynamicIconUtils.timeOfField(Calendar.MINUTE);
            int second = DynamicIconUtils.timeOfField(Calendar.SECOND);
            StringBuilder str = new StringBuilder();
            str.append(hour).append(minute);
            mContent = str.toString();

            float minutef = minute + second / 60.0f;
            float hourf = hour + minutef / 60.0f;

            float angleSecond = second / 60.0f * 360.0f - 90;
            float angleMinute = minutef / 60.0f * 360.0f - 90;
            float angleHour = hourf / 12.0f * 360.0f - 90;

            canvas.save();
            canvas.scale(ICON_SCALE, ICON_SCALE, mCenterX, mCenterY);
            canvas.drawBitmap(mBgBitmap, 0, 0, mPaint);
            canvas.restore();

            canvas.save();
            canvas.rotate(angleHour, mCenterX, mCenterY);
            canvas.scale(ICON_SCALE, ICON_SCALE, mCenterX, mCenterY);
            canvas.drawBitmap(mHourBitmap, mStartX, mCenterY - mHourBitmap.getHeight() / 2, mPaint);
            canvas.restore();

            canvas.save();
            canvas.rotate(angleMinute, mCenterX, mCenterY);
            canvas.scale(ICON_SCALE, ICON_SCALE, mCenterX, mCenterY);
            canvas.drawBitmap(mMinuteBitmap, mStartX, mCenterY - mMinuteBitmap.getHeight() / 2, mPaint);
            canvas.restore();

            if (IS_SHOW_SECOND) {
                canvas.save();
                canvas.rotate(angleSecond, mCenterX, mCenterY);
                canvas.scale(0.9f, 0.9f, mCenterX, mCenterY);
                canvas.drawBitmap(mSecondBitmap, mStartX, mCenterY - mSecondBitmap.getHeight() / 2, mPaint);
                canvas.restore();
            }

            canvas.save();
            canvas.scale(ICON_SCALE, ICON_SCALE, mCenterX, mCenterY);
            canvas.drawBitmap(mCircleBitmap, mStartX, mStartY, mPaint);
            canvas.restore();

            canvas.setBitmap(null);

            BitmapDrawable foreground = new BitmapDrawable(mRes, bitmap);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (mBackground == null) {
                    mBackground = getBackgroundDrawable(mPkg, mContext);
                }
                return new AdaptiveIconDrawable(mBackground, foreground);
            } else {
                return foreground;
            }
        }
    }
}
