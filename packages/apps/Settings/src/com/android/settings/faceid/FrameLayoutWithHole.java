
package com.android.settings.faceid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class FrameLayoutWithHole extends FrameLayout {
    private Bitmap mEraserBitmap;
    private Canvas mEraserCanvas;
    private Paint mEraser;
    private float mDensity;
    private Context mContext;

    private float mRadius;
    private int mBackgroundColor;
    private float mRx;
    private float mRy;

    public FrameLayoutWithHole(Context context) {
        super(context);
        mContext = context;
    }

    public FrameLayoutWithHole(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        mRadius = 0;
        mRx = 0;
        mRy = 0;
        init(null, 0);
    }

    public FrameLayoutWithHole(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public FrameLayoutWithHole(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr);
    }

    public FrameLayoutWithHole(Context context, int backgroundColor, int radius, int rx, int ry) {
        this(context);

        this.mRadius = radius;
        this.mRx = rx;
        this.mRy = ry;
        init(null, 0);
    }

    private void init(AttributeSet attrs, int defStyle) {
        setWillNotDraw(false);
        mDensity = mContext.getResources().getDisplayMetrics().density;

        Point size = new Point();
        size.x = mContext.getResources().getDisplayMetrics().widthPixels;
        size.y = mContext.getResources().getDisplayMetrics().heightPixels;

        mRx = mRx * mDensity;
        mRy = mRy * mDensity;

        mRx = mRx != 0 ? mRx : size.x / 2;
        mRy = mRy != 0 ? mRy : size.y / 2 - 80;

        mRadius = mRadius != 0 ? mRadius : 145;

        mRadius = mRadius * mDensity;

        mBackgroundColor = Color.parseColor("#88000000");

        mEraserBitmap = Bitmap.createBitmap(size.x, size.y, Bitmap.Config.ARGB_8888);
        mEraserCanvas = new Canvas(mEraserBitmap);

        mEraser = new Paint();
        mEraser.setColor(0xFFFFFFFF);
        mEraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mEraser.setFlags(Paint.ANTI_ALIAS_FLAG);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mEraserBitmap.eraseColor(Color.TRANSPARENT);
        mEraserCanvas.drawColor(mBackgroundColor);

        mEraserCanvas.drawCircle(mRx, mRy, mRadius, mEraser);

        canvas.drawBitmap(mEraserBitmap, 0, 0, null);
    }
}
