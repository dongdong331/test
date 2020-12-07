package com.android.wallpaperpicker.common;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class WallpaperManagerCompatVN extends WallpaperManagerCompatV16 {

    private static final String TAG = "WallpaperManagerCompatV";

    public WallpaperManagerCompatVN(Context context) {
        super(context);
    }

    @Override
    public void setStream(final InputStream data, Rect visibleCropHint, boolean allowBackup,
            int whichWallpaper) throws IOException {
        long now = SystemClock.uptimeMillis();
        try {
            // TODO: use mWallpaperManager.setStream(data, visibleCropHint, allowBackup, which)
            // without needing reflection.
            Method setStream = WallpaperManager.class.getMethod("setStream", InputStream.class,
                    Rect.class, boolean.class, int.class);
            if (Utilities.DEBUG) {
                Log.d(TAG, "Getting setStream method took " + (SystemClock.uptimeMillis() - now) + "ms");
            }
            setStream.invoke(mWallpaperManager, data, visibleCropHint, allowBackup, whichWallpaper);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            // Fall back to previous implementation (set both)
            super.setStream(data, visibleCropHint, allowBackup, whichWallpaper);
        }
        if (Utilities.DEBUG) {
            Log.d(TAG, "setStream took " + (SystemClock.uptimeMillis() - now) + "ms");
        }
    }

    @Override
    public void clear(int whichWallpaper) throws IOException {
        long now = SystemClock.uptimeMillis();
        try {
            // TODO: use mWallpaperManager.clear(whichWallpaper) without needing reflection.
            Method clear = WallpaperManager.class.getMethod("clear", int.class);
            if (Utilities.DEBUG) {
                Log.d(TAG, "Getting clear method took " + (SystemClock.uptimeMillis() - now) + "ms");
            }
            clear.invoke(mWallpaperManager, whichWallpaper);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Fall back to previous implementation (set both)
            super.clear(whichWallpaper);
        }
        if (Utilities.DEBUG) {
            Log.d(TAG, "clear took " + (SystemClock.uptimeMillis() - now) + "ms");
        }
    }
}
