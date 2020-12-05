/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sprd.systemui.battery;

import android.animation.ArgbEvaluator;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Op;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.phone.StatusBar;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.content.BroadcastReceiver;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.util.TypedValue;

import com.android.settingslib.Utils;
import com.android.settingslib.R;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.os.sprdpower.PowerManagerEx;

public class BatteryMeterDrawable extends Drawable implements
        BatteryController.BatteryStateChangeCallback {

    private static final float ASPECT_RATIO = 9.5f / 14.5f;
    public static final String TAG = BatteryMeterDrawable.class.getSimpleName();
    public static final String SHOW_PERCENT_SETTING = "status_bar_show_battery_percent";

    // SPRD : Bug 474751 add charge animation of batteryView
    public static final String ACTION_LEVEL_TEST = "com.android.systemui.BATTERY_LEVEL_TEST";
    private static final boolean SINGLE_DIGIT_PERCENT = false;

    private static final int FULL = 96;

    private static final float BOLT_LEVEL_THRESHOLD = 0.3f;  // opaque bolt below this fraction

    private final int[] mColors;
    private final int mIntrinsicWidth;
    private final int mIntrinsicHeight;

    private boolean mShowPercent;
    private float mButtonHeightFraction;
    private float mSubpixelSmoothingLeft;
    private float mSubpixelSmoothingRight;
    private final Paint mFramePaint, mBatteryPaint, mWarningTextPaint, mTextPaint, mBoltPaint,
            mPlusPaint;
    private float mTextHeight, mWarningTextHeight;
    private int mIconTint = Color.WHITE;
    private float mOldDarkIntensity = 0f;

    private int mHeight;
    private int mWidth;
    private String mWarningString;
    private final int mCriticalLevel;
    private int mChargeColor;
    private final float[] mBoltPoints;
    private final Path mBoltPath = new Path();
    private final float[] mPlusPoints;
    private final Path mPlusPath = new Path();

    private final RectF mFrame = new RectF();
    private final RectF mButtonFrame = new RectF();
    private final RectF mBoltFrame = new RectF();
    private final RectF mPlusFrame = new RectF();

    private final Path mShapePath = new Path();
    private final Path mClipPath = new Path();
    private final Path mTextPath = new Path();

    private BatteryController mBatteryController;
    private boolean mPowerSaveEnabled;

    private int mDarkModeBackgroundColor;
    private int mDarkModeFillColor;

    private int mLightModeBackgroundColor;
    private int mLightModeFillColor;

    private final SettingObserver mSettingObserver = new SettingObserver();

    private final Context mContext;
    private final Handler mHandler;

    private int mLevel = -1;
    private boolean mPluggedIn;
    // SPRD: Bug 601597 support battery animation for status bar
    private boolean mCharging;

    private boolean mListening;

    /* SPRD: Bug 474751 add charge animation of batteryView @{ */
    private Runnable mChargingAnimate;
    private static final int LEVEL_UPDATE = 1;
    private static final int ANIMATION_DURATION = 1000;
    private BatteryTracker mTracker = new BatteryTracker();
    // SPRD : Bug 587470 set flag to decide how to draw
    private final boolean mIsBatteryTile;
    /* @} */
    private boolean animate = true;
    private boolean mScreenOn = true;

    /* UNISOC: Bug 897304 add red powersave outline @{ */
    protected boolean mPowerSaveAsColorError = true;
    private final Path mOutlinePath = new Path();
    protected final Paint mPowersavePaint;
    /* @} */

    // SPRD: Bug 587470 set flag to decide how to draw
    public BatteryMeterDrawable(Context context, Handler handler, int frameColor,boolean isBatteryTilte) {
        mContext = context;
        mHandler = handler;
        final Resources res = context.getResources();
        TypedArray levels = res.obtainTypedArray(R.array.batterymeter_color_levels);
        TypedArray colors = res.obtainTypedArray(R.array.batterymeter_color_values);

        final int N = levels.length();
        mColors = new int[2*N];
        for (int i=0; i<N; i++) {
            mColors[2*i] = levels.getInt(i, 0);
            //mColors[2*i+1] = colors.getColor(i, 0);
            if (colors.getType(i) == TypedValue.TYPE_ATTRIBUTE) {
                mColors[2 * i + 1] = Utils.getColorAttr(context, colors.getThemeAttributeId(i, 0));
            } else {
                mColors[2 * i + 1] = colors.getColor(i, 0);
            }
        }
        levels.recycle();
        colors.recycle();
        updateShowPercent();
        mWarningString = context.getString(R.string.battery_meter_very_low_overlay_symbol);
        mCriticalLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel);
        mButtonHeightFraction = context.getResources().getFraction(
                R.fraction.battery_button_height_fraction, 1, 1);
        mSubpixelSmoothingLeft = context.getResources().getFraction(
                R.fraction.battery_subpixel_smoothing_left, 1, 1);
        mSubpixelSmoothingRight = context.getResources().getFraction(
                R.fraction.battery_subpixel_smoothing_right, 1, 1);

        mFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFramePaint.setColor(frameColor);
        mFramePaint.setDither(true);
        mFramePaint.setStrokeWidth(0);
        mFramePaint.setStyle(Paint.Style.FILL_AND_STROKE);

        mBatteryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBatteryPaint.setDither(true);
        mBatteryPaint.setStrokeWidth(0);
        mBatteryPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Typeface font = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        mTextPaint.setTypeface(font);
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        mWarningTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        font = Typeface.create("sans-serif", Typeface.BOLD);
        mWarningTextPaint.setTypeface(font);
        mWarningTextPaint.setTextAlign(Paint.Align.CENTER);
        if (mColors.length > 1) {
            mWarningTextPaint.setColor(mColors[1]);
        }

        mChargeColor = Utils.getDefaultColor(mContext, R.color.meter_consumed_color);

        mBoltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBoltPaint.setColor(Utils.getDefaultColor(mContext, R.color.batterymeter_bolt_color));
        mBoltPoints = loadPoints(res, R.array.batterymeter_bolt_points);

        mPlusPaint = new Paint(mBoltPaint);
        // UNISOC: Bug 897304 add red powersave outline,set pluspaint color to red
        mPlusPaint.setColor(Utils.getDefaultColor(mContext, R.color.batterymeter_plus_color));
        mPlusPoints = loadPoints(res, R.array.batterymeter_plus_points);

        mDarkModeBackgroundColor =
                context.getColor(R.color.dark_mode_icon_color_dual_tone_background);
        mDarkModeFillColor = context.getColor(R.color.dark_mode_icon_color_dual_tone_fill);
        mLightModeBackgroundColor =
                context.getColor(R.color.light_mode_icon_color_dual_tone_background);
        mLightModeFillColor = context.getColor(R.color.light_mode_icon_color_dual_tone_fill);

        /* UNISOC: Bug 897304 add red powersave outline @{ */
        mPowersavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPowersavePaint.setColor(mPlusPaint.getColor());
        mPowersavePaint.setStyle(Style.STROKE);
        mPowersavePaint.setStrokeWidth(context.getResources()
                .getDimensionPixelSize(R.dimen.battery_powersave_outline_thickness));
        /* @} */

        mIntrinsicWidth = context.getResources().getDimensionPixelSize(R.dimen.battery_width);
        mIntrinsicHeight = context.getResources().getDimensionPixelSize(R.dimen.battery_height);

        // SPRD: Bug 587470 set flag to decide how to draw
        mIsBatteryTile = isBatteryTilte;
    }

    @Override
    public int getIntrinsicHeight() {
        return mIntrinsicHeight;
    }

    @Override
    public int getIntrinsicWidth() {
        return mIntrinsicWidth;
    }

    public void setShouldAnimate(boolean animate) {

        if (this.animate == animate) return;
        this.animate = animate;
        if (animate) invalidateSelf();
    }


    public void startListening() {
        mListening = true;
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(SHOW_PERCENT_SETTING), false, mSettingObserver,UserHandle.USER_ALL);
        updateShowPercent();

        /* SPRD: Bug 474751 add charge animation of batteryView @{ */
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(ACTION_LEVEL_TEST);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        final Intent sticky = mContext.registerReceiver(mTracker, filter);
        if (sticky != null) {
            // preload the battery level
            mTracker.onReceive(mContext, sticky);
        }
        /* @} */
        mBatteryController.addCallback(this);
    }

    public void stopListening() {
        mListening = false;
        // SPRD : Bug 474751 add charge animation of batteryView
        mContext.unregisterReceiver(mTracker);
        mContext.getContentResolver().unregisterContentObserver(mSettingObserver);
        mBatteryController.removeCallback(this);
    }

    public void disableShowPercent() {
        mShowPercent = false;
        postInvalidate();
    }

    @Override
    public void invalidateSelf() {
        super.invalidateSelf();
    }

    private void postInvalidate() {

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                invalidateSelf();
            }
        });
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        /* SPRD: Bug 883647 battery icon did't have '+' after enable battery saver @{ */
        if (StatusBar.SUPPORT_SUPER_POWER_SAVE ) {
            mPowerSaveEnabled = (mBatteryController.getPowerSaveModeInternal() == PowerManagerEx.MODE_LOWPOWER);
        } else {
            mPowerSaveEnabled = mBatteryController.isPowerSave();
        }
        /* @} */
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        Log.d(TAG, "onBatteryLevelChanged level="+level+",pluggedIn="+pluggedIn+",charging="+charging);
        mLevel = level;
        mPluggedIn = pluggedIn;
        // SPRD: Bug 601597 support battery animation for status bar
        mCharging = charging;
        postInvalidate();
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        /* SPRD: Bug 883647 battery icon did't have '+' after enable battery saver @{ */
        Log.d(TAG,"onPowerSaveChanged isPowerSave="+isPowerSave);
        if (StatusBar.SUPPORT_SUPER_POWER_SAVE ) {
            mPowerSaveEnabled = (mBatteryController.getPowerSaveModeInternal() == PowerManagerEx.MODE_LOWPOWER);
        } else {
            mPowerSaveEnabled = mBatteryController.isPowerSave();
        }
        /* @} */
        invalidateSelf();
    }

    private static float[] loadBoltPoints(Resources res) {
        final int[] pts = res.getIntArray(R.array.batterymeter_bolt_points);
        int maxX = 0, maxY = 0;
        for (int i = 0; i < pts.length; i += 2) {
            maxX = Math.max(maxX, pts[i]);
            maxY = Math.max(maxY, pts[i + 1]);
        }
        final float[] ptsF = new float[pts.length];
        for (int i = 0; i < pts.length; i += 2) {
            ptsF[i] = (float)pts[i] / maxX;
            ptsF[i + 1] = (float)pts[i + 1] / maxY;
        }
        return ptsF;
    }

    private static float[] loadPoints(Resources res, int pointArrayRes) {
        final int[] pts = res.getIntArray(pointArrayRes);
        int maxX = 0, maxY = 0;
        for (int i = 0; i < pts.length; i += 2) {
            maxX = Math.max(maxX, pts[i]);
            maxY = Math.max(maxY, pts[i + 1]);
        }
        final float[] ptsF = new float[pts.length];
        for (int i = 0; i < pts.length; i += 2) {
            ptsF[i] = (float)pts[i] / maxX;
            ptsF[i + 1] = (float)pts[i + 1] / maxY;
        }
        return ptsF;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        mHeight = bottom - top;
        mWidth = right - left;
        mWarningTextPaint.setTextSize(mHeight * 0.75f);
        mWarningTextHeight = -mWarningTextPaint.getFontMetrics().ascent;
    }

    private void updateShowPercent() {
        mShowPercent = 0 != Settings.System.getIntForUser(mContext.getContentResolver(),
                SHOW_PERCENT_SETTING, 0, ActivityManager.getCurrentUser());
        Log.d(TAG, "mShowPercent="+mShowPercent);
    }

    private int getColorForLevel(int percent) {

        // If we are in power save mode, always use the normal color.
        if (mPowerSaveEnabled) {
            return mIconTint;
        }
        int thresh, color = 0;
        for (int i=0; i<mColors.length; i+=2) {
            thresh = mColors[i];
            color = mColors[i+1];
            if (percent <= thresh) {

                // Respect tinting for "normal" level
                if (i == mColors.length-2) {
                    return mIconTint;
                } else {
                    return color;
                }
            }
        }
        return color;
    }

    public void setColors(int fillColor, int backgroundColor) {
        mIconTint = fillColor;
        mFramePaint.setColor(backgroundColor);
        mBoltPaint.setColor(fillColor);
        mChargeColor = fillColor;
        invalidateSelf();
    }

    public void setDarkIntensity(float darkIntensity) {
        if (darkIntensity == mOldDarkIntensity) {
            return;
        }
        int backgroundColor = getBackgroundColor(darkIntensity);
        int fillColor = getFillColor(darkIntensity);
        mIconTint = fillColor;
        mFramePaint.setColor(backgroundColor);
        mBoltPaint.setColor(fillColor);
        mChargeColor = fillColor;
        invalidateSelf();
        mOldDarkIntensity = darkIntensity;
    }

    private int getBackgroundColor(float darkIntensity) {
        return getColorForDarkIntensity(
                darkIntensity, mLightModeBackgroundColor, mDarkModeBackgroundColor);
    }

    private int getFillColor(float darkIntensity) {
        return getColorForDarkIntensity(
                darkIntensity, mLightModeFillColor, mDarkModeFillColor);
    }

    private int getColorForDarkIntensity(float darkIntensity, int lightColor, int darkColor) {
        return (int) ArgbEvaluator.getInstance().evaluate(darkIntensity, lightColor, darkColor);
    }

    @Override
    public void draw(Canvas c) {
        /* SPRD: Bug 587470 draw according the flag @{ */
        /* SPRD: Bug 474751 add charge animation of batteryView @{ */
        //final int level = mLevel;
        final int level = mIsBatteryTile ?  mLevel : mTracker.level;
        final boolean plugged = mIsBatteryTile ?  mPluggedIn : mTracker.plugged;
        /* @} */
        /* @} */

        if (level == -1) return;

        float drawFrac = (float) level / 100f;
        final int height = mHeight;
        final int width = (int) (ASPECT_RATIO * mHeight);
        int px = (mWidth - width) / 2;

        final int buttonHeight = (int) (height * mButtonHeightFraction);

        mFrame.set(0, 0, width, height);
        mFrame.offset(px, 0);

        // button-frame: area above the battery body
        mButtonFrame.set(
                mFrame.left + Math.round(width * 0.25f),
                mFrame.top,
                mFrame.right - Math.round(width * 0.25f),
                mFrame.top + buttonHeight);

        mButtonFrame.top += mSubpixelSmoothingLeft;
        mButtonFrame.left += mSubpixelSmoothingLeft;
        mButtonFrame.right -= mSubpixelSmoothingRight;

        // frame: battery body area
        mFrame.top += buttonHeight;
        mFrame.left += mSubpixelSmoothingLeft;
        mFrame.top += mSubpixelSmoothingLeft;
        mFrame.right -= mSubpixelSmoothingRight;
        mFrame.bottom -= mSubpixelSmoothingRight;

        // SPRD : Bug 474751 add charge animation of batteryView
        // set the battery charging color
        mBatteryPaint.setColor(plugged ? mChargeColor : getColorForLevel(level));

        if (level >= FULL) {
            drawFrac = 1f;
        } else if (level <= mCriticalLevel) {
            drawFrac = 0f;
        }

        final float levelTop = drawFrac == 1f ? mButtonFrame.top
                : (mFrame.top + (mFrame.height() * (1f - drawFrac)));

        // define the battery shape
        mShapePath.reset();
        mShapePath.moveTo(mButtonFrame.left, mButtonFrame.top);
        mShapePath.lineTo(mButtonFrame.right, mButtonFrame.top);
        mShapePath.lineTo(mButtonFrame.right, mFrame.top);
        mShapePath.lineTo(mFrame.right, mFrame.top);
        mShapePath.lineTo(mFrame.right, mFrame.bottom);
        mShapePath.lineTo(mFrame.left, mFrame.bottom);
        mShapePath.lineTo(mFrame.left, mFrame.top);
        mShapePath.lineTo(mButtonFrame.left, mFrame.top);
        mShapePath.lineTo(mButtonFrame.left, mButtonFrame.top);

        /* UNISOC: Bug 897304 add red powersave outline  */
        if (mPowerSaveEnabled  && !plugged) {
            mOutlinePath.reset();
            mOutlinePath.moveTo(mButtonFrame.left, mButtonFrame.top);
            mOutlinePath.lineTo(mButtonFrame.right, mButtonFrame.top);
            mOutlinePath.lineTo(mButtonFrame.right, mFrame.top);
            mOutlinePath.lineTo(mFrame.right, mFrame.top);
            mOutlinePath.lineTo(mFrame.right, mFrame.bottom);
            mOutlinePath.lineTo(mFrame.left, mFrame.bottom);
            mOutlinePath.lineTo(mFrame.left, mFrame.top);
            mOutlinePath.lineTo(mButtonFrame.left, mFrame.top);
            mOutlinePath.lineTo(mButtonFrame.left, mButtonFrame.top);
        }
        /* @}  */

        /* SPRD: Bug 601597 support battery animation for status bar @{ */
        // SPRD : Bug 474751 add charge animation of batteryView
        if (plugged && level != 100
                && (mTracker.status == BatteryManager.BATTERY_STATUS_CHARGING || mCharging)) {
            // define the bolt shape
            /* @} */
            final float bl = mFrame.left + mFrame.width() / 4f;
            final float bt = mFrame.top + mFrame.height() / 6f;
            final float br = mFrame.right - mFrame.width() / 4f;
            final float bb = mFrame.bottom - mFrame.height() / 10f;
            if (mBoltFrame.left != bl || mBoltFrame.top != bt
                    || mBoltFrame.right != br || mBoltFrame.bottom != bb) {
                mBoltFrame.set(bl, bt, br, bb);
                mBoltPath.reset();
                mBoltPath.moveTo(
                        mBoltFrame.left + mBoltPoints[0] * mBoltFrame.width(),
                        mBoltFrame.top + mBoltPoints[1] * mBoltFrame.height());
                for (int i = 2; i < mBoltPoints.length; i += 2) {
                    mBoltPath.lineTo(
                            mBoltFrame.left + mBoltPoints[i] * mBoltFrame.width(),
                            mBoltFrame.top + mBoltPoints[i + 1] * mBoltFrame.height());
                }
                mBoltPath.lineTo(
                        mBoltFrame.left + mBoltPoints[0] * mBoltFrame.width(),
                        mBoltFrame.top + mBoltPoints[1] * mBoltFrame.height());
            }

            float boltPct = (mBoltFrame.bottom - levelTop) / (mBoltFrame.bottom - mBoltFrame.top);
            boltPct = Math.min(Math.max(boltPct, 0), 1);
            if (boltPct <= BOLT_LEVEL_THRESHOLD) {
                // draw the bolt if opaque
                c.drawPath(mBoltPath, mBoltPaint);
            } else {
                // otherwise cut the bolt out of the overall shape
                mShapePath.op(mBoltPath, Path.Op.DIFFERENCE);
            }
        /* SPRD: Bug 883647 battery icon did't have '+' after enable battery saver @{ */
        } else if (mPowerSaveEnabled  && !plugged) {
        /* @} */
            // define the plus shape
            final float pw = mFrame.width() * 2 / 3;
            final float pl = mFrame.left + (mFrame.width() - pw) / 2;
            final float pt = mFrame.top + (mFrame.height() - pw) / 2;
            final float pr = mFrame.right - (mFrame.width() - pw) / 2;
            final float pb = mFrame.bottom - (mFrame.height() - pw) / 2;
            if (mPlusFrame.left != pl || mPlusFrame.top != pt
                    || mPlusFrame.right != pr || mPlusFrame.bottom != pb) {
                mPlusFrame.set(pl, pt, pr, pb);
                mPlusPath.reset();
                mPlusPath.moveTo(
                        mPlusFrame.left + mPlusPoints[0] * mPlusFrame.width(),
                        mPlusFrame.top + mPlusPoints[1] * mPlusFrame.height());
                for (int i = 2; i < mPlusPoints.length; i += 2) {
                    mPlusPath.lineTo(
                            mPlusFrame.left + mPlusPoints[i] * mPlusFrame.width(),
                            mPlusFrame.top + mPlusPoints[i + 1] * mPlusFrame.height());
                }
                mPlusPath.lineTo(
                        mPlusFrame.left + mPlusPoints[0] * mPlusFrame.width(),
                        mPlusFrame.top + mPlusPoints[1] * mPlusFrame.height());
            }

            /* UNISOC: Bug 897304 add red powersave outline @{ */
            // Always cut out of the whole shape, and sometimes filled colorError
            mShapePath.op(mPlusPath, Path.Op.DIFFERENCE);
            if (mPowerSaveAsColorError) {
                c.drawPath(mPlusPath, mPlusPaint);
            }
            /* @} */
        }

        // compute percentage text
        boolean pctOpaque = false;
        float pctX = 0, pctY = 0;
        String pctText = null;
        /* SPRD: Bug 474751 add charge animation of batteryView @{ */
        /*if (!plugged && !mPowerSaveEnabled && level > mCriticalLevel && mShowPercent) {
            mTextPaint.setColor(getColorForLevel(level));
            mTextPaint.setTextSize(height *
                    (SINGLE_DIGIT_PERCENT ? 0.75f
                            : (mTracker.level == 100 ? 0.38f : 0.5f)));
            mTextHeight = -mTextPaint.getFontMetrics().ascent;
            pctText = String.valueOf(SINGLE_DIGIT_PERCENT ? (level/10) : level);
            pctX = mWidth * 0.5f;
            pctY = (mHeight + mTextHeight) * 0.47f;
            pctOpaque = levelTop > pctY;
            if (!pctOpaque) {
                mTextPath.reset();
                mTextPaint.getTextPath(pctText, 0, pctText.length(), pctX, pctY, mTextPath);
                // cut the percentage text out of the overall shape
                mShapePath.op(mTextPath, Path.Op.DIFFERENCE);
            }
        }*/

        // draw the battery shape background
        c.drawPath(mShapePath, mFramePaint);

        // draw the battery shape, clipped to charging level
        mFrame.top = levelTop;
        mClipPath.reset();
        mClipPath.addRect(mFrame,  Path.Direction.CCW);
        mShapePath.op(mClipPath, Path.Op.INTERSECT);
        c.drawPath(mShapePath, mBatteryPaint);

        // SPRD 474751: change the date when charging, the charge animation of batteryView was stop
        if (!plugged && !mPowerSaveEnabled) {
            if (level <= mCriticalLevel) {
                // draw the warning text
                final float x = mWidth * 0.5f;
                final float y = (mHeight + mWarningTextHeight) * 0.48f;
                c.drawText(mWarningString, x, y, mWarningTextPaint);
            } else if (pctOpaque) {
                // draw the percentage text
                c.drawText(pctText, pctX, pctY, mTextPaint);
            }
        }

        /* UNISOC: Bug 897304 add red powersave outline @{ */
        // Draw the powersave outline last
        if (mPowerSaveEnabled && !plugged && mPowerSaveAsColorError) {
            c.drawPath(mOutlinePath, mPowersavePaint);
        }
        /* @} */
    }

    // Some stuff required by Drawable.
    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mFramePaint.setColorFilter(colorFilter);
        mBatteryPaint.setColorFilter(colorFilter);
        mWarningTextPaint.setColorFilter(colorFilter);
        mBoltPaint.setColorFilter(colorFilter);
        mPlusPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return 0;
    }

    /* SPRD: Bug 474751 add charge animation of batteryView @{ */
    private final class BatteryTracker extends BroadcastReceiver {
        public static final int UNKNOWN_LEVEL = -1;

        // current battery status
        int level = UNKNOWN_LEVEL;
        String percentStr;
        int plugType;
        boolean plugged;
        int health;
        int status;
        String technology;
        int voltage;
        int temperature;
        boolean testmode = false;
        int tempLevel = -1;
        int ChargeLevel = -1;

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "onReceive action="+action);
            if (action.equals(Intent.ACTION_USER_SWITCHED)) {
                updateShowPercent();
                postInvalidate();
            }
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                if (testmode && ! intent.getBooleanExtra("testmode", false)) return;
                level = (int)(100f
                        * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                        / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));

                plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                plugged = plugType != 0;
                health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH,
                        BatteryManager.BATTERY_HEALTH_UNKNOWN);
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN);
                technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
                voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);

                Log.d(TAG, "onReceive level="+level+",plugType="+plugType+",status="+status);
	         if(mScreenOn == false){
                        Log.d(TAG, "Screen is off, stop charging anim");
                        return;
                }

                /* @} SPRD bug 814301&819675 :add control of whether refresh statusbar icons*/
                BatteryMeterViewAnimationShow(intent);
                /* @} */
            } else if (action.equals(ACTION_LEVEL_TEST)) {
                testmode = true;
                mHandler.post(new Runnable() {
                    int curLevel = 0;
                    int incr = 1;
                    int saveLevel = level;
                    int savePlugged = plugType;
                    Intent dummy = new Intent(Intent.ACTION_BATTERY_CHANGED);
                    @Override
                    public void run() {
                        if (curLevel < 0) {
                            testmode = false;
                            dummy.putExtra("level", saveLevel);
                            dummy.putExtra("plugged", savePlugged);
                            dummy.putExtra("testmode", false);
                        } else {
                            dummy.putExtra("level", curLevel);
                            dummy.putExtra("plugged", incr > 0 ? BatteryManager.BATTERY_PLUGGED_AC
                                    : 0);
                            dummy.putExtra("testmode", true);
                        }
                        mContext.sendBroadcast(dummy);

                        if (!testmode) return;

                        curLevel += incr;
                        if (curLevel == 100) {
                            incr *= -1;
                        }
                        mHandler.postDelayed(this, 200);
                    }
                });
            }

            if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mScreenOn = false;
                cleanTimerTask();
            }

            if (action.equals(Intent.ACTION_SCREEN_ON)) {
                mScreenOn = true;
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    if (mChargingAnimate == null && level < 100) {
                        mChargingAnimate = new Runnable() {
                            @Override
                            public void run() {
                                ChargeLevel += 20;
                                level = ChargeLevel;
                                postInvalidate();
                                if (ChargeLevel > 90) {
                                    if (tempLevel > 20) {
                                        ChargeLevel = tempLevel - 20;
                                    } else {
                                        ChargeLevel = tempLevel;
                                    }
                                }
                                mHandler.postDelayed(this, ANIMATION_DURATION);
                            }
                        };
                        mHandler.postDelayed(mChargingAnimate, ANIMATION_DURATION);
                    }
                }
            }
        }

        private void BatteryMeterViewAnimationShow(Intent intent) {
            Log.d(TAG, "BatteryMeterViewAnimationShow level should be =" + level);
            if (plugged && level < 100 && status == BatteryManager.BATTERY_STATUS_CHARGING) {
                if (0 < level && level < 20) {
                    tempLevel = -1;
                } else if (20 < level && level < 40) {
                    tempLevel = 19;
                } else if (40 < level && level < 60) {
                    tempLevel = 39;
                } else if (60 < level && level < 80) {
                    tempLevel = 59;
                } else if (80 < level && level < 100) {
                    tempLevel = 79;
                }
                ChargeLevel = tempLevel;

                if (mChargingAnimate == null) {
                    mChargingAnimate = new Runnable() {
                        @Override
                        public void run() {
                            ChargeLevel += 20;
                            level = ChargeLevel;
                            postInvalidate();
                            if (ChargeLevel > 90) {
                                if (tempLevel > 20) {
                                    ChargeLevel = tempLevel - 20;
                                } else {
                                    ChargeLevel = tempLevel;
                                }
                            }
                            mHandler.postDelayed(this, ANIMATION_DURATION);
                        }
                    };
                    mHandler.postDelayed(mChargingAnimate, ANIMATION_DURATION);
                }
            } else {
                level = (int) (100f * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) / intent
                        .getIntExtra(BatteryManager.EXTRA_SCALE, 100));

                postInvalidate();
                cleanTimerTask();
            }
        }
    }
    /* @} */

    private final class SettingObserver extends ContentObserver {
        public SettingObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            updateShowPercent();
            /* SPRD : Bug 474751 add charge animation of batteryView @{ */
            BatteryTracker tracker =  mTracker;
            if (!tracker.plugged) {
                postInvalidate();
                cleanTimerTask();
            }
            /* @} */
        }
    }

    /* SPRD : Bug 474751 add charge animation of batteryView @{ */
    private void cleanTimerTask() {
        if (mChargingAnimate != null){
            mHandler.removeCallbacks(mChargingAnimate);
            mChargingAnimate = null;
        }
    }
    /* @} */

    /* UNISOC: Bug 897304 add red powersave outline @{ */
    protected void setPowerSaveAsColorError(boolean asError) {
        mPowerSaveAsColorError = asError;
    }
    /* @} */
}
