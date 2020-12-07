package com.sprd.ext.dynamicicon.clock;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import com.android.launcher3.R;
import com.sprd.ext.LogUtils;
import com.sprd.ext.SystemPropertiesUtils;
import com.sprd.ext.dynamicicon.BaseDynamicIcon;
import com.sprd.ext.dynamicicon.DynamicIconSettings;
import com.sprd.ext.dynamicicon.DynamicIconUtils;

import java.util.Calendar;

/**
 * Created on 6/7/18.
 */
public class GoogleClockIcon extends BaseDynamicIcon {
    private static final String TAG = "GoogleClockIcon";

    // It will reduce the system performance when showing the second hand in dynamic clock icon.
    // So make IS_SHOW_SECOND to be false in general.
    private static final boolean IS_SHOW_SECOND = SystemPropertiesUtils.getBoolean(
            "ro.launcher.dyclock.second", false);
    private static final boolean DBG = IS_SHOW_SECOND ?
            LogUtils.DEBUG_DYNAMIC_ICON_ALL : LogUtils.DEBUG_DYNAMIC_ICON;

    private Runnable mSecondTick;

    public GoogleClockIcon(Context context, String pkg) {
        super(context, pkg);
        mIcon = new GoogleClock(context);

        mPreDynamic = DynamicIconUtils.getAppliedValue(context, mPkg,
                context.getResources().getBoolean(R.bool.dynamic_clock_default_state));
        mCurDynamic = DynamicIconUtils.getAppliedValue(context,
                DynamicIconSettings.PREF_KEY_GOOGLE_CLOCK,
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

    private class GoogleClock extends DynamicDrawable {

        // Fraction of the length of second hand.
        private static final float SECOND_LENGTH_FACTOR = 0.45f;
        // Fraction of the length of minute hand.
        private static final float MINUTE_LENGTH_FACTOR = 0.40f;
        // Fraction of the length of hour hand.
        private static final float HOUR_LENGTH_FACTOR = 0.30f;

        private Drawable mBg;
        private int mWidth;
        private int mHeight;

        private Paint mSecondPaint;
        private Paint mMinutePaint;
        private Paint mHourPaint;

        private float mCenterRadius;
        private float mSecondLength;
        private float mMinuteLength;
        private float mHourLength;

        private GoogleClock(Context context) {
            super(context);

            mBg = context.getDrawable(R.drawable.ic_dial_plate_original);
            mWidth = mBg.getIntrinsicWidth();
            mHeight = mBg.getIntrinsicHeight();

            mSecondPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mSecondPaint.setColor(Color.WHITE);
            mSecondPaint.setStrokeWidth(mHeight / 40);
            mSecondLength = mHeight * SECOND_LENGTH_FACTOR;

            mMinutePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mMinutePaint.setColor(Color.WHITE);
            mMinutePaint.setStrokeWidth(mHeight / 25);
            mMinuteLength = mHeight * MINUTE_LENGTH_FACTOR;

            mHourPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mHourPaint.setColor(Color.WHITE);
            mHourPaint.setStrokeWidth(mHeight / 25);
            mHourLength = mHeight * HOUR_LENGTH_FACTOR;

            mCenterRadius = mHeight / 25;
        }

        @Override
        public Drawable create(Drawable icon) {
            final Rect sOldBounds = new Rect();
            sOldBounds.set(mBg.getBounds());
            final Canvas canvas = new Canvas();
            final Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight,
                    Bitmap.Config.ARGB_8888);
            canvas.setBitmap(bitmap);
            // draw the background drawable
            mBg.setBounds(0, 0, mWidth, mHeight);
            mBg.draw(canvas);
            mBg.setBounds(sOldBounds);

            int hour = DynamicIconUtils.timeOfField(Calendar.HOUR);
            int minute = DynamicIconUtils.timeOfField(Calendar.MINUTE);
            int second = DynamicIconUtils.timeOfField(Calendar.SECOND);
            StringBuilder str = new StringBuilder();
            str.append(hour).append(minute);
            mContent = str.toString();

            float Minutes = minute + second / 60.0f;
            float Hour = hour + Minutes / 60.0f;

            double radianSecond = ((float) second / 60.0f * 360f)/180f * Math.PI;
            double radianMinute = (Minutes / 60.0f * 360f)/180f * Math.PI;
            double radianHour = (Hour / 12.0f * 360f)/180f * Math.PI;

            float secondX = 0f;
            float secondY = 0f;
            if (IS_SHOW_SECOND) {
                secondX = (float) (mSecondLength * Math.sin(radianSecond));
                secondY = (float) (mSecondLength * Math.cos(radianSecond));
            }

            float minuteX = (float) (mMinuteLength * Math.sin(radianMinute));
            float minuteY = (float) (mMinuteLength * Math.cos(radianMinute));

            float hourX = (float) (mHourLength * Math.sin(radianHour));
            float hourY = (float) (mHourLength * Math.cos(radianHour));

            // draw the hour hand, minute hand and second hand.
            canvas.save();
            int centerX = canvas.getClipBounds().centerX();
            int centerY = canvas.getClipBounds().centerY();

            canvas.drawLine(centerX, centerY, centerX + hourX, centerY - hourY, mHourPaint);
            canvas.drawLine(centerX, centerY, centerX + minuteX, centerY - minuteY, mMinutePaint);
            if (IS_SHOW_SECOND) {
                canvas.drawLine(centerX, centerY, centerX + secondX, centerY - secondY, mSecondPaint);
            }
            canvas.drawCircle(centerX, centerY, mCenterRadius, mSecondPaint);
            canvas.restore();
            canvas.setBitmap(null);

            return new BitmapDrawable(mRes, bitmap);
        }
    }
}
