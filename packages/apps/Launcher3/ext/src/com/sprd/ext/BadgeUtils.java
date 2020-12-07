package com.sprd.ext;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.folder.FolderIcon;

/**
 * Created by sprd on 2017/7/31.
 */
public class BadgeUtils {
    private static final float ROUNDRECT_ARC_X = 30.0f;
    private static final float ROUNDRECT_ARC_Y = 30.0f;

    private static final String BLANK_STRING = " ";

    private static Paint sBgPaint;
    private static Paint sTextPaint;

    public enum BadgeMode {LT_BADGE, RT_BADGE, LB_BADGE, RB_BADGE}

    public static void drawBadge(Canvas canvas, View v, String text) {
        drawBadge(canvas, v, text, BadgeMode.RT_BADGE);
    }

    public static void drawBadge(Canvas canvas, View v, String text, BadgeMode mode) {
        if (canvas == null || v == null) {
            return;
        }

        //init text
        if (TextUtils.isEmpty(text)) {
            text = BLANK_STRING;
        }

        //init view rect
        Rect vRect = new Rect();
        v.getDrawingRect(vRect);

        //init icon rect
        Rect iconRect = new Rect(vRect);
        if (v instanceof BubbleTextView) {
            iconRect = ((BubbleTextView)v).getIconRect();
        } else if (v instanceof FolderIcon) {
            iconRect = ((FolderIcon)v).getIconRect();
        }

        //init offset for different container
        Resources res = v.getContext().getResources();
        Point offsetPoint = new Point(0,0);
        if(v.getTag() instanceof ItemInfo) {
            ItemInfo info = (ItemInfo) v.getTag();
            if(info instanceof AppInfo) {
                offsetPoint.x = (int) res.getDimension(R.dimen.app_badge_margin_x);
                offsetPoint.y = (int) res.getDimension(R.dimen.app_badge_margin_y);
            } else {
                if(info.container == (long) LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                    offsetPoint.x = (int) res.getDimension(R.dimen.hotseat_badge_margin_x);
                    offsetPoint.y = (int) res.getDimension(R.dimen.hotseat_badge_margin_y);
                } else if(info.container == (long) LauncherSettings.Favorites.CONTAINER_DESKTOP){
                    offsetPoint.x = (int) res.getDimension(R.dimen.workspace_badge_margin_x);
                    offsetPoint.y = (int) res.getDimension(R.dimen.workspace_badge_margin_y);
                } else {
                    offsetPoint.x = (int) res.getDimension(R.dimen.folder_badge_margin_x);
                    offsetPoint.y = (int) res.getDimension(R.dimen.folder_badge_margin_y);
                }
            }
        }

        //init paint
        Paint textPaint = sTextPaint == null ? createTextPaint(iconRect, v.getContext()) : sTextPaint;
        Paint bgPaint = sBgPaint == null ? createBgPaint(v.getContext()) : sBgPaint;

        canvas.save();

        //init text bounds
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        Rect textBounds = new Rect();
        textBounds.left = 0;
        textBounds.top = 0;
        textBounds.right = Math.round(textPaint.measureText(text));
        textBounds.bottom = Math.round(fm.descent - fm.ascent);
        long minHeight = Math.round(Math.sqrt((textBounds.height() * textBounds.height()) << 1));
        if (textBounds.width() <= textBounds.height()) {
            textBounds.right = textBounds.bottom = (int) minHeight;
        } else {
            textBounds.right += Math.round(textPaint.measureText(BLANK_STRING));
            textBounds.bottom = (int) minHeight;
        }

        //draw badge bg
        Rect badgeRect = getBadgeRect(vRect, iconRect, textBounds, mode, offsetPoint);
        if (badgeRect.width() == badgeRect.height()) {
            canvas.drawCircle(badgeRect.centerX(), badgeRect.centerY(), (badgeRect.width() >> 1), bgPaint);
        } else {
            canvas.drawRoundRect(new RectF(badgeRect), ROUNDRECT_ARC_X, ROUNDRECT_ARC_Y, bgPaint);
        }

        //draw badge text
        Point drawPoint = UtilitiesExt.getTextDrawPoint(badgeRect, fm);
        canvas.drawText(text, drawPoint.x, drawPoint.y, textPaint);
        UtilitiesExt.drawDebugRect(canvas, badgeRect, Color.YELLOW);

        canvas.restore();
    }

    private static Paint createBgPaint(Context context) {
        Resources res = context.getResources();
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(res.getColor(R.color.badge_bg_color));
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setAntiAlias(true);
        return bgPaint;
    }

    private static Paint createTextPaint(Rect iconRect, Context context) {
        Resources res = context.getResources();
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(res.getColor(R.color.badge_text_color));
        textPaint.setTextSize((iconRect.height() >> 2) - 1);
        Typeface font = Typeface.create("sans-serif", Typeface.BOLD);
        textPaint.setTypeface(font);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        return textPaint;
    }

    private static Rect getBadgeRect(Rect viewRect, Rect iconRect, Rect textBounds, BadgeMode mode,
                                     Point offset) {
        Rect badgeRect = new Rect(textBounds);
        int halfWidth = badgeRect.width() >> 1;
        int halfHeight = badgeRect.height() >> 1;

        switch (mode) {
            case LT_BADGE:
                badgeRect.offsetTo(iconRect.left - halfWidth, iconRect.top - halfHeight);
                badgeRect.offset(offset.x, offset.y);
                break;
            case RT_BADGE:
                badgeRect.offsetTo(iconRect.right - halfWidth, iconRect.top - halfHeight);
                badgeRect.offset(-offset.x, offset.y);
                break;
            case LB_BADGE:
                badgeRect.offsetTo(iconRect.left - halfWidth, iconRect.bottom - halfHeight);
                badgeRect.offset(offset.x, -offset.y);
                break;
            case RB_BADGE:
                badgeRect.offsetTo(iconRect.right - halfWidth, iconRect.bottom - halfHeight);
                badgeRect.offset(-offset.x, -offset.y);
                break;
        }

        Point autoOffset = calcOffset(badgeRect, viewRect, mode);
        badgeRect.offset(autoOffset.x, autoOffset.y);

        return badgeRect;
    }

    private static Point calcOffset(Rect badgeRect, Rect viewRect, BadgeMode mode) {
        Point p = new Point();
        switch (mode) {
            case LT_BADGE:
                p.x = badgeRect.left < viewRect.left ? viewRect.left - badgeRect.left + 1 : 0;
                p.y = badgeRect.top < viewRect.top ? viewRect.top - badgeRect.top + 1 : 0;
                break;
            case RT_BADGE:
                p.x = badgeRect.right > viewRect.right ? viewRect.right - badgeRect.right - 1 : 0;
                p.y = badgeRect.top < viewRect.top ? viewRect.top - badgeRect.top + 1 : 0;
                break;
            case LB_BADGE:
                p.x = badgeRect.left < viewRect.left ? viewRect.left - badgeRect.left + 1 : 0;
                p.y = badgeRect.bottom > viewRect.bottom ? viewRect.bottom - badgeRect.bottom - 1 : 0;
                break;
            case RB_BADGE:
                p.x = badgeRect.right > viewRect.right ? viewRect.right - badgeRect.right - 1 : 0;
                p.y = badgeRect.bottom > viewRect.bottom ? viewRect.bottom - badgeRect.bottom - 1 : 0;
                break;
        }

        return p;
    }

    public static void setBgPaint(final Paint paint) {
        sBgPaint = paint;
    }

    public static void setTextPaint(final Paint paint) {
        sTextPaint = paint;
    }
}
