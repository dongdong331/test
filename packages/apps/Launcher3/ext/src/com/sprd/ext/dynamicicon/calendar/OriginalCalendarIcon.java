package com.sprd.ext.dynamicicon.calendar;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.android.launcher3.R;
import com.sprd.ext.UtilitiesExt;
import com.sprd.ext.dynamicicon.BaseDynamicIcon;
import com.sprd.ext.dynamicicon.DynamicIconSettings;
import com.sprd.ext.dynamicicon.DynamicIconUtils;

/**
 * Created on 5/30/18.
 */
public class OriginalCalendarIcon extends BaseDynamicIcon {

    public OriginalCalendarIcon(Context context, String pkg) {
        super(context, pkg);
        mIcon = new DreamCalendar(context);

        mPreDynamic = DynamicIconUtils.getAppliedValue(context, mPkg,
                context.getResources().getBoolean(R.bool.dynamic_calendar_default_state));
        mCurDynamic = DynamicIconUtils.getAppliedValue(context,
                DynamicIconSettings.PREF_KEY_ORIGINAL_CALENDAR,
                context.getResources().getBoolean(R.bool.dynamic_calendar_default_state));

        mFilter.addAction(Intent.ACTION_DATE_CHANGED);
        mFilter.addAction(Intent.ACTION_TIME_CHANGED);
        mFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver();
    }

    @Override
    public String getDrawableContentNeedShown() {
        return String.valueOf(DynamicIconUtils.dayOfMonth());
    }

    private class DreamCalendar extends DynamicDrawable {
        // The proportion of the date font occupied in the dynamic calendar icon.
        private static final float WEEK_SIZE_FACTOR = 0.135f;

        // The proportion of the week font occupied in the dynamic calendar icon.
        private static final float WEEK_RECT_TOP = 26.00f;
        private static final float WEEK_RECT_BOTTOM = 38.00f;
        private static final float DATE_RECT_BOTTOM = 86.00f;
        private static final float BASE_ICON_HEIGHT = 108.00f;


        private Paint mDatePaint;
        private Paint mWeekPaint;

        private DreamCalendar(Context context) {
            super(context);

            Typeface dateFont = Typeface.create("sans-serif-light", Typeface.NORMAL);
            Typeface weekFont = Typeface.create("sans-serif-regular", Typeface.NORMAL);

            mDatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mDatePaint.setTypeface(dateFont);
            mDatePaint.setTextAlign(Paint.Align.CENTER);
            mDatePaint.setColor(mRes.getColor(R.color.dynamic_calendar_date, null));
            mDatePaint.setFilterBitmap(true);

            mWeekPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mWeekPaint.setTextAlign(Paint.Align.CENTER);
            mWeekPaint.setTypeface(weekFont);
            mWeekPaint.setColor(mRes.getColor(R.color.dynamic_calendar_week, null));
            mWeekPaint.setFilterBitmap(true);
        }

        @Override
        public Drawable create(Drawable icon) {
            final int width = icon.getIntrinsicWidth();
            final int height = icon.getIntrinsicHeight();
            mDatePaint.setTextSize(height * WEEK_SIZE_FACTOR * 2.8f);
            mWeekPaint.setTextSize(height * WEEK_SIZE_FACTOR);

            final Canvas canvas = new Canvas();
            final Bitmap bitmap = Bitmap.createBitmap(width, height,
                    Bitmap.Config.ARGB_8888);
            canvas.setBitmap(bitmap);
            canvas.save();

            // draw the week
            Rect iconRect = new Rect(0, 0, width, height);
            Rect drawRect = new Rect(iconRect);
            drawRect.top += (int) (iconRect.height() * (WEEK_RECT_TOP / BASE_ICON_HEIGHT));
            drawRect.bottom = (int) (iconRect.top + iconRect.height() * (WEEK_RECT_BOTTOM / BASE_ICON_HEIGHT));

            Paint.FontMetrics fm = mWeekPaint.getFontMetrics();
            Point drawPoint = UtilitiesExt.getTextDrawPoint(drawRect, fm);
            canvas.drawText(DynamicIconUtils.dayOfWeek(), drawPoint.x, drawPoint.y, mWeekPaint);

            // draw the date
            drawRect.top = drawRect.bottom;
            drawRect.bottom = (int) (iconRect.top + iconRect.height() * (DATE_RECT_BOTTOM / BASE_ICON_HEIGHT));

            fm = mDatePaint.getFontMetrics();
            drawPoint = UtilitiesExt.getTextDrawPoint(drawRect, fm);
            mContent = getDrawableContentNeedShown();
            canvas.drawText(mContent, drawPoint.x, drawPoint.y, mDatePaint);

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
