/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.screenshot;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.MotionEvent;

/**
 * Draws a selection rectangle while taking screenshot
 */
public class ScreenshotSelectorView extends View {
    private static final String TAG = "ScreenshotSelectorView";
    private Point mStartPoint;
    private Rect mSelectionRect;
    private final Paint mPaintSelection, mPaintBackground, mPaintCircle;
    private Point[] mCircles = new Point[4];
    private final int CIRCLE_RADIUS = 20;
    private final int MIN_REGION = 10;
    private int mCurrentSelectedCircle;
    private int[] mLocation;
    private int mScreenWidth, mScreenHeight;
    private boolean mCurrentForLongScreenshot;

    public ScreenshotSelectorView(Context context) {
        this(context, null);
    }

    public ScreenshotSelectorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mPaintBackground = new Paint(Color.BLACK);
        mPaintBackground.setAlpha(166);
        mPaintSelection = new Paint(Color.TRANSPARENT);
        mPaintSelection.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_ATOP));
        mPaintCircle = new Paint(Color.WHITE);
    }

    public void startSelection(int x, int y) {
        mStartPoint = new Point(x, y);
        mSelectionRect = new Rect(x, y, x, y);
    }

    public void updateSelection(int x, int y) {
        if (mSelectionRect != null) {
            mSelectionRect.left = Math.min(mStartPoint.x, x);
            mSelectionRect.right = Math.max(mStartPoint.x, x);
            mSelectionRect.top = Math.min(mStartPoint.y, y);
            mSelectionRect.bottom = Math.max(mStartPoint.y, y);
            invalidate();
        }
    }

    public void setCurrentForLongScreenShot(boolean longshot) {
        mCurrentForLongScreenshot = longshot;
    }

    public void startRegionSelection(int x, int y, int[] location) {
        mLocation = location;
        mStartPoint = new Point(mLocation[0], mLocation[1]);
        mSelectionRect = new Rect(mLocation[0], mLocation[1], x - mLocation[0], y - mLocation[1]);
        mCircles[0] = new Point(mLocation[0], mLocation[1]);
        mCircles[1] = new Point(x - mLocation[0], mLocation[1]);
        mCircles[2] = new Point(mLocation[0], y - mLocation[1]);
        mCircles[3] = new Point(x - mLocation[0], y - mLocation[1]);
        mScreenWidth = x;
        mScreenHeight = y;
    }

    public void updateCurrentSelectionCircle(int x, int y) {
        mCurrentSelectedCircle = -1;
        for (int i = 0; i < 4; i++) {
            if (x < mCircles[i].x + CIRCLE_RADIUS && x > mCircles[i].x - CIRCLE_RADIUS
                    && y < mCircles[i].y + CIRCLE_RADIUS && y > mCircles[i].y - CIRCLE_RADIUS) {
                mCurrentSelectedCircle = i;
                break;
            }
        }
    }

    public int checkCurrentX(int x) {
        if (x < mLocation[0]) {
            x = mLocation[0];
        }
        if (x > (mScreenWidth - mLocation[0])) {
            x = (mScreenWidth - mLocation[0]);
        }
        return x;
    }

    public int checkCurrentY(int y) {
        if (y < mLocation[1]) {
            y = mLocation[1];
        }
        if (y > (mScreenHeight - mLocation[1])) {
            y = (mScreenHeight - mLocation[1]);
        }
        return y;
    }

    public void updateRegionSelection(int x, int y) {
        x = checkCurrentX(x);
        y = checkCurrentY(y);
        if (mSelectionRect != null) {
            if (mCurrentSelectedCircle == 0) {
                if (x > (mSelectionRect.right - MIN_REGION) || y > (mSelectionRect.bottom - MIN_REGION)) {
                    return;
                }
                mSelectionRect.left = x;
                mSelectionRect.top = y;
                mCircles[0].x = x;
                mCircles[0].y = y;
                mCircles[1].y = y;
                mCircles[2].x = x;
            } else if (mCurrentSelectedCircle == 1) {
                if (x < (mSelectionRect.left + MIN_REGION) || y > (mSelectionRect.bottom - MIN_REGION)) {
                    return;
                }
                mSelectionRect.right = x;
                mSelectionRect.top = y;
                mCircles[1].x = x;
                mCircles[1].y = y;
                mCircles[0].y = y;
                mCircles[3].x = x;
            } else if (mCurrentSelectedCircle == 2) {
                if (x > (mSelectionRect.right - MIN_REGION) || y < (mSelectionRect.top + MIN_REGION)) {
                    return;
                }
                mSelectionRect.left = x;
                mSelectionRect.bottom = y;
                mCircles[2].x = x;
                mCircles[2].y = y;
                mCircles[0].x = x;
                mCircles[3].y = y;
            } else if (mCurrentSelectedCircle == 3) {
                if (x < (mSelectionRect.left + MIN_REGION) || y < (mSelectionRect.top + MIN_REGION)) {
                    return;
                }
                mSelectionRect.right = x;
                mSelectionRect.bottom = y;
                mCircles[3].x = x;
                mCircles[3].y = y;
                mCircles[1].x = x;
                mCircles[2].y = y;
            }
            invalidate();
        }
    }

    public int getRealSelectionLocationX() {
        if (mCircles[0].x == mLocation[0]) {
            return 0;
        }
        return (mCircles[0].x - mLocation[0]) * getRealSelectionRegionWidth()
                / (mCircles[1].x - mCircles[0].x);
    }

    public int getRealSelectionLocationY() {
        if (mCircles[1].y == mLocation[1]) {
            return 0;
        }
        return (mCircles[1].y - mLocation[1]) * getRealSelectionRegionHeight()
                / (mCircles[3].y - mCircles[1].y);
    }

    public int getRealSelectionRegionWidth() {
        return (mCircles[1].x - mCircles[0].x) * mScreenWidth / (mScreenWidth - mLocation[0] * 2);
    }

    public int getRealSelectionRegionHeight() {
        return (mCircles[3].y - mCircles[1].y) * mScreenHeight / (mScreenHeight - mLocation[1] * 2);
    }

    public void printLogs() {
        Log.d(TAG, "mCircles[0]x:" + mCircles[0].x + " mCircles[0]x:" + mCircles[0].y);
        Log.d(TAG, "mCircles[1]x:" + mCircles[1].x + " mCircles[1]y:" + mCircles[1].y);
        Log.d(TAG, "mCircles[2]x:" + mCircles[2].x + " mCircles[2]y:" + mCircles[2].y);
        Log.d(TAG, "mCircles[3]x:" + mCircles[3].x + " mCircles[3]y:" + mCircles[3].y);
        Log.d(TAG, "mLocation[0]:" + mLocation[0] + " mLocation[1]:" + mLocation[1]);
        Log.d(TAG, "mSelectionRect top:" + mSelectionRect.top + " mLocation left:" + mSelectionRect.left);
        Log.d(TAG, "mSelectionRect bottom:" + mSelectionRect.bottom + " mLocation right:" + mSelectionRect.right);
    }

    public Rect getSelectionRect() {
        return mSelectionRect;
    }

    public int[] getLocation() {
        return mLocation;
    }

    public void stopSelection() {
        mStartPoint = null;
        mSelectionRect = null;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void draw(Canvas canvas) {
        if (mCurrentForLongScreenshot) {
            mPaintBackground.setAlpha(255);
        }
        canvas.drawRect(mLeft, mTop, mRight, mBottom, mPaintBackground);
        mPaintSelection.setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.DST_OUT));

        if (mSelectionRect != null) {
            canvas.drawRect(mSelectionRect, mPaintSelection);
        }
        mPaintCircle.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_OVER));
        mPaintCircle.setAntiAlias(true);
        for (int i = 0; i < 4 && mCircles[i] != null; i++) {
            canvas.drawCircle(mCircles[i].x, mCircles[i].y, CIRCLE_RADIUS, mPaintCircle);
        }
    }
}
