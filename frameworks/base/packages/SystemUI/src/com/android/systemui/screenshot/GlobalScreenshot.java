/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *c
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.screenshot;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static com.android.systemui.screenshot.GlobalScreenshot.SHARING_INTENT;
import static com.android.systemui.statusbar.phone.StatusBar.SYSTEM_DIALOG_REASON_SCREENSHOT;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.Notification.BigPictureStyle;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.provider.Settings;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.PowerManager;
import android.os.EnvironmentEx;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.Settings.Global;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.text.TextUtils;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.systemui.R;
import com.android.systemui.SystemUI;
import com.android.systemui.util.NotificationChannels;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.pm.ActivityInfo;
import android.hardware.input.InputManager;
import android.os.Message;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.ViewConfiguration;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import android.app.KeyguardManager;
import android.os.RemoteException;
import android.view.WindowManagerGlobal;
import com.android.internal.app.ChooserActivity;

import android.support.v4.content.FileProvider;

/**
 * POD used in the AsyncTask which saves an image in the background.
 */
class SaveImageInBackgroundData {
    Context context;
    Bitmap image;
    Uri imageUri;
    Runnable finisher;
    int iconSize;
    int previewWidth;
    int previewheight;
    int errorMsgResId;

    void clearImage() {
        image = null;
        imageUri = null;
        iconSize = 0;
    }
    void clearContext() {
        context = null;
    }
}

/**
 * An AsyncTask that saves an image to the media store in the background.
 */
class SaveImageInBackgroundTask extends AsyncTask<Void, Void, Void> {
    private static final String TAG = "SaveImageInBackgroundTask";

    private static final String SCREENSHOTS_DIR_NAME = "Screenshots";
    private static final String SCREENSHOT_FILE_NAME_TEMPLATE = "Screenshot_%s.png";
    private static final String SCREENSHOT_SHARE_SUBJECT_TEMPLATE = "Screenshot (%s)";

    private final SaveImageInBackgroundData mParams;
    private final NotificationManager mNotificationManager;
    private final Notification.Builder mNotificationBuilder, mPublicNotificationBuilder;
    private final File mScreenshotDir;
    private final String mImageFileName;
    private final String mImageFilePath;
    private final long mImageTime;
    private final BigPictureStyle mNotificationStyle;
    private final int mImageWidth;
    private final int mImageHeight;
    /*SPRD :fix bug 708401,modify save path for a screenshot@{*/
    private static final boolean SAVE_SPRD_EXTERNAL_STORAGE = false;
    private ContentResolver mContentResolver = null;

    SaveImageInBackgroundTask(Context context, SaveImageInBackgroundData data,
            NotificationManager nManager) {
        Resources r = context.getResources();
        mContentResolver = context.getContentResolver();

        // Prepare all the output metadata
        mParams = data;
        mImageTime = System.currentTimeMillis();
        String imageDate = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(mImageTime));
        mImageFileName = String.format(SCREENSHOT_FILE_NAME_TEMPLATE, imageDate);

        /*SPRD :fix bug 708401,modify save path for a screenshot@{*/
        if(!SAVE_SPRD_EXTERNAL_STORAGE){
            mScreenshotDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), SCREENSHOTS_DIR_NAME);
        }else{
            mScreenshotDir = new File(getStorePath(), SCREENSHOTS_DIR_NAME);
        }
        /*SPRD :fix bug 708401,modify save path for a screenshot*/
        mImageFilePath = new File(mScreenshotDir, mImageFileName).getAbsolutePath();

        // Create the large notification icon
        mImageWidth = data.image.getWidth();
        mImageHeight = data.image.getHeight();
        int iconSize = data.iconSize;
        int previewWidth = data.previewWidth;
        int previewHeight = data.previewheight;

        Paint paint = new Paint();
        ColorMatrix desat = new ColorMatrix();
        desat.setSaturation(0.25f);
        paint.setColorFilter(new ColorMatrixColorFilter(desat));
        Matrix matrix = new Matrix();
        int overlayColor = 0x40FFFFFF;

        matrix.setTranslate((previewWidth - mImageWidth) / 2, (previewHeight - mImageHeight) / 2);
        Bitmap picture = generateAdjustedHwBitmap(data.image, previewWidth, previewHeight, matrix,
                paint, overlayColor);

        // Note, we can't use the preview for the small icon, since it is non-square
        float scale = (float) iconSize / Math.min(mImageWidth, mImageHeight);
        matrix.setScale(scale, scale);
        matrix.postTranslate((iconSize - (scale * mImageWidth)) / 2,
                (iconSize - (scale * mImageHeight)) / 2);
        Bitmap icon = generateAdjustedHwBitmap(data.image, iconSize, iconSize, matrix, paint,
                overlayColor);

        mNotificationManager = nManager;
        final long now = System.currentTimeMillis();

        // Setup the notification
        mNotificationStyle = new Notification.BigPictureStyle()
                .bigPicture(picture.createAshmemBitmap());

        // The public notification will show similar info but with the actual screenshot omitted
        mPublicNotificationBuilder =
                new Notification.Builder(context, NotificationChannels.SCREENSHOTS_HEADSUP)
                        .setContentTitle(r.getString(R.string.screenshot_saving_title))
                        .setSmallIcon(R.drawable.stat_notify_image)
                        .setCategory(Notification.CATEGORY_PROGRESS)
                        .setWhen(now)
                        .setShowWhen(true)
                        .setColor(r.getColor(
                                com.android.internal.R.color.system_notification_accent_color));
        SystemUI.overrideNotificationAppName(context, mPublicNotificationBuilder, true);

        mNotificationBuilder = new Notification.Builder(context,
                NotificationChannels.SCREENSHOTS_HEADSUP)
            .setContentTitle(r.getString(R.string.screenshot_saving_title))
            .setSmallIcon(R.drawable.stat_notify_image)
            .setWhen(now)
            .setShowWhen(true)
            .setColor(r.getColor(com.android.internal.R.color.system_notification_accent_color))
            .setStyle(mNotificationStyle)
            .setPublicVersion(mPublicNotificationBuilder.build());
        mNotificationBuilder.setFlag(Notification.FLAG_NO_CLEAR, true);
        SystemUI.overrideNotificationAppName(context, mNotificationBuilder, true);

        if(mNotificationManager != null) mNotificationManager.notify(SystemMessage.NOTE_GLOBAL_SCREENSHOT, mNotificationBuilder.build());

        /**
         * NOTE: The following code prepares the notification builder for updating the notification
         * after the screenshot has been written to disk.
         */

        // On the tablet, the large icon makes the notification appear as if it is clickable (and
        // on small devices, the large icon is not shown) so defer showing the large icon until
        // we compose the final post-save notification below.
        mNotificationBuilder.setLargeIcon(icon.createAshmemBitmap());
        // But we still don't set it for the expanded view, allowing the smallIcon to show here.
        mNotificationStyle.bigLargeIcon((Bitmap) null);
    }

    /**
     * Generates a new hardware bitmap with specified values, copying the content from the passed
     * in bitmap.
     */
    private Bitmap generateAdjustedHwBitmap(Bitmap bitmap, int width, int height, Matrix matrix,
            Paint paint, int color) {
        Picture picture = new Picture();
        Canvas canvas = picture.beginRecording(width, height);
        canvas.drawColor(color);
        canvas.drawBitmap(bitmap, matrix, paint);
        picture.endRecording();
        return Bitmap.createBitmap(picture);
    }

    @Override
    protected Void doInBackground(Void... params) {
        if (isCancelled()) {
            return null;
        }

        // By default, AsyncTask sets the worker thread to have background thread priority, so bump
        // it back up so that we save a little quicker.
        Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);

        Context context = mParams.context;
        Bitmap image = mParams.image;
        Resources r = context.getResources();

        try {
            // Create screenshot directory if it doesn't exist
            mScreenshotDir.mkdirs();

            // media provider uses seconds for DATE_MODIFIED and DATE_ADDED, but milliseconds
            // for DATE_TAKEN
            long dateSeconds = mImageTime / 1000;

            // Save
            OutputStream out = new FileOutputStream(mImageFilePath);
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();

            // Save the screenshot to the MediaStore
            ContentValues values = new ContentValues();
            ContentResolver resolver = context.getContentResolver();
            values.put(MediaStore.Images.ImageColumns.DATA, mImageFilePath);
            values.put(MediaStore.Images.ImageColumns.TITLE, mImageFileName);
            values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, mImageFileName);
            values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, mImageTime);
            values.put(MediaStore.Images.ImageColumns.DATE_ADDED, dateSeconds);
            values.put(MediaStore.Images.ImageColumns.DATE_MODIFIED, dateSeconds);
            values.put(MediaStore.Images.ImageColumns.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.ImageColumns.WIDTH, mImageWidth);
            values.put(MediaStore.Images.ImageColumns.HEIGHT, mImageHeight);
            values.put(MediaStore.Images.ImageColumns.SIZE, new File(mImageFilePath).length());
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            // Create a share intent
            String subjectDate = DateFormat.getDateTimeInstance().format(new Date(mImageTime));
            String subject = String.format(SCREENSHOT_SHARE_SUBJECT_TEMPLATE, subjectDate);
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("image/png");
            sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            sharingIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            if(mNotificationManager == null) {
                ActivityOptions opts = ActivityOptions.makeBasic();
                opts.setDisallowEnterPictureInPictureWhileLaunching(true);
                context.startActivityAsUser(Intent.createChooser(sharingIntent, r.getString(com.android.internal.R.string.share)), opts.toBundle(), UserHandle.CURRENT);
                return null;
            }

            // Create a share action for the notification. Note, we proxy the call to
            // ScreenshotActionReceiver because RemoteViews currently forces an activity options
            // on the PendingIntent being launched, and since we don't want to trigger the share
            // sheet in this case, we start the chooser activity directly in
            // ScreenshotActionReceiver.
            PendingIntent shareAction = PendingIntent.getBroadcast(context, 0,
                    new Intent(context, GlobalScreenshot.ScreenshotActionReceiver.class)
                            .putExtra(SHARING_INTENT, sharingIntent),
                    PendingIntent.FLAG_CANCEL_CURRENT);
            Notification.Action.Builder shareActionBuilder = new Notification.Action.Builder(
                    R.drawable.ic_screenshot_share,
                    r.getString(com.android.internal.R.string.share), shareAction);
            mNotificationBuilder.addAction(shareActionBuilder.build());

            Intent editIntent = new Intent(Intent.ACTION_EDIT);
            editIntent.setType("image/png");
            editIntent.setData(uri);
            editIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            editIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            // Create a edit action for the notification the same way.
            PendingIntent editAction = PendingIntent.getBroadcast(context, 1,
                    new Intent(context, GlobalScreenshot.ScreenshotActionReceiver.class)
                            .putExtra(SHARING_INTENT, editIntent),
                    PendingIntent.FLAG_CANCEL_CURRENT);
            Notification.Action.Builder editActionBuilder = new Notification.Action.Builder(
                    R.drawable.ic_screenshot_edit,
                    r.getString(com.android.internal.R.string.screenshot_edit), editAction);
            mNotificationBuilder.addAction(editActionBuilder.build());


            // Create a delete action for the notification
            PendingIntent deleteAction = PendingIntent.getBroadcast(context, 0,
                    new Intent(context, GlobalScreenshot.DeleteScreenshotReceiver.class)
                            .putExtra(GlobalScreenshot.SCREENSHOT_URI_ID, uri.toString()),
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
            Notification.Action.Builder deleteActionBuilder = new Notification.Action.Builder(
                    R.drawable.ic_screenshot_delete,
                    r.getString(com.android.internal.R.string.delete), deleteAction);
            mNotificationBuilder.addAction(deleteActionBuilder.build());

            mParams.imageUri = uri;
            mParams.image = null;
            mParams.errorMsgResId = 0;
        } catch (Exception e) {
            // IOException/UnsupportedOperationException may be thrown if external storage is not
            // mounted
            Slog.e(TAG, "unable to save screenshot", e);
            mParams.clearImage();
            mParams.errorMsgResId = R.string.screenshot_failed_to_save_text;
        }

        // Recycle the bitmap data
        if (image != null) {
            image.recycle();
        }

        return null;
    }

    @Override
    protected void onPostExecute(Void params) {
        if(mNotificationManager == null) {
            mParams.finisher.run();
            mParams.clearContext();
            return;
        }
        if (mParams.errorMsgResId != 0) {
            // Show a message that we've failed to save the image to disk
            GlobalScreenshot.notifyScreenshotError(mParams.context, mNotificationManager,
                    mParams.errorMsgResId);
        } else {
            // Show the final notification to indicate screenshot saved
            Context context = mParams.context;
            Resources r = context.getResources();

            // Create the intent to show the screenshot in gallery
            Intent launchIntent = new Intent(Intent.ACTION_VIEW);
            launchIntent.setDataAndType(mParams.imageUri, "image/png");
            launchIntent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            final long now = System.currentTimeMillis();

            // Update the text and the icon for the existing notification
            mPublicNotificationBuilder
                    .setContentTitle(r.getString(R.string.screenshot_saved_title))
                    .setContentText(r.getString(R.string.screenshot_saved_text))
                    .setContentIntent(PendingIntent.getActivity(mParams.context, 0, launchIntent, 0))
                    .setWhen(now)
                    .setAutoCancel(true)
                    .setColor(context.getColor(
                            com.android.internal.R.color.system_notification_accent_color));
            mNotificationBuilder
                .setContentTitle(r.getString(R.string.screenshot_saved_title))
                .setContentText(r.getString(R.string.screenshot_saved_text))
                .setContentIntent(PendingIntent.getActivity(mParams.context, 0, launchIntent, 0))
                .setWhen(now)
                .setAutoCancel(true)
                .setColor(context.getColor(
                        com.android.internal.R.color.system_notification_accent_color))
                .setPublicVersion(mPublicNotificationBuilder.build())
                .setFlag(Notification.FLAG_NO_CLEAR, false);

            mNotificationManager.notify(SystemMessage.NOTE_GLOBAL_SCREENSHOT,
                    mNotificationBuilder.build());
        }
        Settings.System.putIntForUser(mContentResolver, "longshot_switch", 0, UserHandle.USER_OWNER);
        mParams.finisher.run();
        mParams.clearContext();
    }

    @Override
    protected void onCancelled(Void params) {
        // If we are cancelled while the task is running in the background, we may get null params.
        // The finisher is expected to always be called back, so just use the baked-in params from
        // the ctor in any case.
        mParams.finisher.run();
        mParams.clearImage();
        mParams.clearContext();

        // Cancel the posted notification
        if(mNotificationManager != null) mNotificationManager.cancel(SystemMessage.NOTE_GLOBAL_SCREENSHOT);
    }

    /* SPRD :fix bug 708401,modify save path for a screenshot@{ */
    public static File getStorePath() {
        File pathDir = null;
        if (Environment.MEDIA_MOUNTED.equals(EnvironmentEx.getExternalStoragePathState())) {
            pathDir = EnvironmentEx.getExternalStoragePath();
        } else {
            pathDir = EnvironmentEx.getInternalStoragePath();
        }
        return new File(pathDir, Environment.DIRECTORY_PICTURES);
    }
    /* SPRD :fix bug 708401,modify save path for a screenshot*/

    public static void getScreenshotPicInfo(ScreenshotPicInfo info) {
        info.mImageTime = System.currentTimeMillis();
        info.dateSeconds = info.mImageTime / 1000;
        String imageDate = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(info.mImageTime));
        info.mImageFileName = String.format(SCREENSHOT_FILE_NAME_TEMPLATE, imageDate);
        File mScreenshotDir = null;
        if(!SAVE_SPRD_EXTERNAL_STORAGE){
            mScreenshotDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), SCREENSHOTS_DIR_NAME);
        }else{
            mScreenshotDir = new File(getStorePath(), SCREENSHOTS_DIR_NAME);
        }
        if (!mScreenshotDir.exists()) mScreenshotDir.mkdir();
        /*SPRD :fix bug 708401,modify save path for a screenshot*/
        info.longscreenshotFilePath = new File(mScreenshotDir, info.mImageFileName).getAbsolutePath();
    }
}

/**
 * An AsyncTask that deletes an image from the media store in the background.
 */
class DeleteImageInBackgroundTask extends AsyncTask<Uri, Void, Void> {
    private Context mContext;

    DeleteImageInBackgroundTask(Context context) {
        mContext = context;
    }

    @Override
    protected Void doInBackground(Uri... params) {
        if (params.length != 1) return null;

        Uri screenshotUri = params[0];
        ContentResolver resolver = mContext.getContentResolver();
        resolver.delete(screenshotUri, null, null);
        return null;
    }
}

class ScreenshotPicInfo {
    Uri uri;
    String longscreenshotFilePath;
    String mImageFileName;
    long mImageTime;
    long dateSeconds;
    int mImageWidth;
    int mImageHeight;
}

class GlobalScreenshot {
    static final String SCREENSHOT_URI_ID = "android:screenshot_uri_id";
    static final String SHARING_INTENT = "android:screenshot_sharing_intent";
    private static final String TAG = "GlobalScreenshot";

    private static final int SCREENSHOT_FLASH_TO_PEAK_DURATION = 130;
    private static final int SCREENSHOT_DROP_IN_DURATION = 430;
    private static final int SCREENSHOT_DROP_OUT_DELAY = 500;
    private static final int SCREENSHOT_DROP_OUT_DURATION = 430;
    private static final int SCREENSHOT_DROP_OUT_SCALE_DURATION = 370;
    private static final int SCREENSHOT_FAST_DROP_OUT_DURATION = 320;
    private static final float BACKGROUND_ALPHA = 1.0f;
    private static final float SCREENSHOT_SCALE = 1f;
    private static final float SCREENSHOT_DROP_IN_MIN_SCALE = SCREENSHOT_SCALE * 0.725f;
    private static final float SCREENSHOT_DROP_OUT_MIN_SCALE = SCREENSHOT_SCALE * 0.45f;
    private static final float SCREENSHOT_FAST_DROP_OUT_MIN_SCALE = SCREENSHOT_SCALE * 0.6f;
    private static final float SCREENSHOT_DROP_OUT_MIN_SCALE_OFFSET = 0f;
    private final int mPreviewWidth;
    private final int mPreviewHeight;

    private Context mContext;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mWindowLayoutParams;
    private NotificationManager mNotificationManager;
    private Display mDisplay;
    private DisplayMetrics mDisplayMetrics;
    private Matrix mDisplayMatrix;

    private Bitmap mScreenBitmap;
    private View mScreenshotLayout;
    private ScreenshotSelectorView mScreenshotSelectorView;
    private ImageView mBackgroundView;
    private ScreenShotImageView mScreenshotView;
    private ScrollView scrollView;
    private FrameLayout bottomFrame;
    private ScreenShotImageView mSecondScreenshotView;
    private ImageView mScreenshotFlash;
    private View mMoreActionLayout;

    private AnimatorSet mScreenshotAnimation;

    private int mNotificationIconSize;
    private float mBgPadding;
    private float mBgPaddingScale;

    private AsyncTask<Void, Void, Void> mSaveInBgTask;

    private MediaActionSound mCameraSound;

    // SPRD for powerful screenshot
    private ContentResolver mResolver;
    private Runnable mFinisher;
    private Bitmap mFirstBitmap;
    private Bitmap mLastBitmap;
    private ArrayList<Bitmap> mBitmapResultList;
    private boolean toEnd;
    private boolean manSave;
    private int shotCount;
    private boolean mMoveable;
    // use 4 breakpoint to split screen into 5 parts
    private int mBPone;
    private int mBPtwo;
    private int mBPthree;
    private int mBPfour;

    /* SPRD:Bug 900116 @{*/
    protected static final String SCREENSHOTS_GLOBAL_ZEN_MODE = "zen_mode";
    private AudioManager mAudioManager;
    /* @} */


    /**
     * @param context everything needs a context :(
     */
    public GlobalScreenshot(Context context) {
        Resources r = context.getResources();
        mContext = context;
        mResolver = mContext.getContentResolver();
        LayoutInflater layoutInflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Inflate the screenshot layout
        mDisplayMatrix = new Matrix();
        mScreenshotLayout = layoutInflater.inflate(R.layout.global_screenshot, null);
        mMoreActionLayout = layoutInflater.inflate(R.layout.global_screenshot_more_action, null);
        btnScrollMode = (Button) mMoreActionLayout.findViewById(R.id.btn_scroll_mode);
        btnScrollMode.setVisibility(View.INVISIBLE);
        //btnScrollMode.setText(mContext.getString(R.string.start_long_screen_shot));
        mBackgroundView = (ImageView) mScreenshotLayout.findViewById(R.id.global_screenshot_background);
        mScreenshotView = (ScreenShotImageView) mScreenshotLayout.findViewById(R.id.global_screenshot);
        //mScreenshotView.setLimitedHeight(r.getDisplayMetrics().heightPixels * 2);
        scrollView = (ScrollView) mScreenshotLayout.findViewById(R.id.scrollview);
        bottomFrame = (FrameLayout) mScreenshotLayout.findViewById(R.id.screenshotview_bottom);
        mSecondScreenshotView = (ScreenShotImageView) mScreenshotLayout.findViewById(R.id.global_screenshot_bottom);
        mSecondScreenshotView.setVisibility(View.GONE);
        mScreenshotFlash = (ImageView) mScreenshotLayout.findViewById(R.id.global_screenshot_flash);
        mScreenshotSelectorView = (ScreenshotSelectorView) mMoreActionLayout.findViewById(R.id.screen_shot_select_view);
        mScreenshotLayout.setFocusable(true);
        mScreenshotSelectorView.setFocusable(true);
        mScreenshotSelectorView.setFocusableInTouchMode(true);
        mScreenshotLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Intercept and ignore all touch events
                return true;
            }
        });
        mMoreActionLayout.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewDetachedFromWindow(View v) {
                setIsInLongshot(0);
            }

            @Override
            public void onViewAttachedToWindow(View v) {
                float degrees = getDegreesForRotation(mDisplay.getRotation());
                if (degrees > 0) {
                    stopScreenshot();
                    setIsInLongshot(0);
                    if(mFinisher != null) {
                        mFinisher.run();
                    }
                    Toast.makeText(mContext, R.string.screen_shot_landscape_tips, Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Inflate the partial screenshot layout
        mResolver = mContext.getContentResolver();
        mBitmapResultList = new ArrayList<Bitmap>();

        // Setup the window that we are going to use
        mWindowLayoutParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 0, 0,
                WindowManager.LayoutParams.TYPE_SCREENSHOT,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        mWindowLayoutParams.setTitle("ScreenshotAnimation");
        mWindowLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mNotificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mDisplay = mWindowManager.getDefaultDisplay();
        mDisplayMetrics = new DisplayMetrics();
        mDisplay.getRealMetrics(mDisplayMetrics);

        // Get the various target sizes
        mNotificationIconSize =
            r.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);

        // Scale has to account for both sides of the bg
        mBgPadding = (float) r.getDimensionPixelSize(R.dimen.global_screenshot_bg_padding);
        mBgPaddingScale = mBgPadding /  mDisplayMetrics.widthPixels;

        // determine the optimal preview size
        int panelWidth = 0;
        try {
            panelWidth = r.getDimensionPixelSize(R.dimen.notification_panel_width);
        } catch (Resources.NotFoundException e) {
        }
        if (panelWidth <= 0) {
            // includes notification_panel_width==match_parent (-1)
            panelWidth = mDisplayMetrics.widthPixels;
        }
        mPreviewWidth = panelWidth;
        mPreviewHeight = r.getDimensionPixelSize(R.dimen.notification_max_height);

        // Setup the Camera shutter sound
        mCameraSound = new MediaActionSound();
        mCameraSound.load(MediaActionSound.SHUTTER_CLICK);
        /* SPRD:Bug 900116 @{*/
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        /* @} */
    }

    /**
     * Creates a new worker thread and saves the screenshot to the media store.
     */
    private void saveScreenshotInWorkerThread(Runnable finisher) {
        /*UNISOC: modify for bug966927 {@*/
        if (null == mScreenBitmap) {
            Log.e(TAG,"saveScreenshotInWorkerThread mScreenBitmap is null!");
            return;
        }
        /* @} */
        SaveImageInBackgroundData data = new SaveImageInBackgroundData();
        data.context = mContext;
        data.image = mScreenBitmap;
        data.iconSize = mNotificationIconSize;
        data.finisher = finisher;
        data.previewWidth = mPreviewWidth;
        data.previewheight = mPreviewHeight;
        if (mSaveInBgTask != null) {
            mSaveInBgTask.cancel(false);
        }
        mSaveInBgTask = new SaveImageInBackgroundTask(mContext, data, mNotificationManager)
                .execute();
    }

    /**
     * Creates a new worker thread and saves the screenshot to the media store.
     */
    private void saveSharedScreenshotInWorkerThread(Runnable finisher) {
        SaveImageInBackgroundData data = new SaveImageInBackgroundData();
        data.context = mContext;
        data.image = mScreenBitmap;
        data.iconSize = mNotificationIconSize;
        data.finisher = finisher;
        data.previewWidth = mPreviewWidth;
        data.previewheight = mPreviewHeight;
        if (mSaveInBgTask != null) {
            mSaveInBgTask.cancel(false);
        }
        mSaveInBgTask = new SaveImageInBackgroundTask(mContext, data, null)
                .execute();
    }

    /**
     * @return the current display rotation in degrees
     */
    private float getDegreesForRotation(int value) {
        switch (value) {
        case Surface.ROTATION_90:
            return 360f - 90f;
        case Surface.ROTATION_180:
            return 360f - 180f;
        case Surface.ROTATION_270:
            return 360f - 270f;
        }
        return 0f;
    }

    /**
     * Takes a screenshot of the current display and shows an animation.
     */
    private void takeScreenshot(Runnable finisher, boolean statusBarVisible, boolean navBarVisible,
            Rect crop) {
        if (isInLongshot()) {
            Toast info = Toast.makeText(mContext, R.string.long_screen_shot_tips, Toast.LENGTH_SHORT);
            info.show();
            finisher.run();
            return;
        }
        int rot = mDisplay.getRotation();
        int width = crop.width();
        int height = crop.height();

        // Take the screenshot
        mScreenBitmap = SurfaceControl.screenshot(crop, width, height, rot);
        if (mScreenBitmap == null) {
            notifyScreenshotError(mContext, mNotificationManager,
                    R.string.screenshot_failed_to_capture_text);
            finisher.run();
            return;
        }

        // Optimizations
        mScreenBitmap.setHasAlpha(false);
        mScreenBitmap.prepareToDraw();

        // Start the post-screenshot animation
        startAnimation(finisher, mDisplayMetrics.widthPixels, mDisplayMetrics.heightPixels,
                statusBarVisible, navBarVisible);
    }

    void takeScreenshot(Runnable finisher, boolean statusBarVisible, boolean navBarVisible) {
        mDisplay.getRealMetrics(mDisplayMetrics);
        takeScreenshot(finisher, statusBarVisible, navBarVisible,
                new Rect(0, 0, mDisplayMetrics.widthPixels, mDisplayMetrics.heightPixels));
    }

    /**
     * Takes a screenshot of the current display and shows an animation.
     */
    void takeScreenshotNoAnimation(Runnable finisher, int x, int y, int width, int height) {
        // We need to orient the screenshot correctly (and the Surface api seems to take screenshots
        // only in the natural orientation of the device :!)
        mDisplay.getRealMetrics(mDisplayMetrics);
        float[] dims = {mDisplayMetrics.widthPixels, mDisplayMetrics.heightPixels};
        float degrees = getDegreesForRotation(mDisplay.getRotation());
        boolean requiresRotation = (degrees > 0);
        if (requiresRotation) {
            // Get the dimensions of the device in its native orientation
            mDisplayMatrix.reset();
            mDisplayMatrix.preRotate(-degrees);
            mDisplayMatrix.mapPoints(dims);
            dims[0] = Math.abs(dims[0]);
            dims[1] = Math.abs(dims[1]);
        }

        int rot = mDisplay.getRotation();
        Rect sourceCrop = new Rect(0, 0, (int) dims[0], (int) dims[1]);
        // Take the screenshot
        mScreenBitmap = SurfaceControl.screenshot(sourceCrop, (int) dims[0], (int) dims[1], rot);
        Log.d(TAG, "takeScreenshotNoAnimation");
        if (mScreenBitmap == null) {
            notifyScreenshotError(mContext, mNotificationManager,
                    R.string.screenshot_failed_to_capture_text);
            finisher.run();
            setIsInLongshot(0);
            Log.d(TAG, "takeScreenshotNoAnimation return");
            return;
        }

        if (requiresRotation) {
            // Rotate the screenshot to the current orientation
            Bitmap ss = Bitmap.createBitmap(mDisplayMetrics.widthPixels,
                    mDisplayMetrics.heightPixels, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(ss);
            c.translate(ss.getWidth() / 2, ss.getHeight() / 2);
            c.rotate(degrees);
            c.translate(-dims[0] / 2, -dims[1] / 2);
            c.drawBitmap(mScreenBitmap, 0, 0, null);
            c.setBitmap(null);
            // Recycle the previous bitmap
            mScreenBitmap.recycle();
            mScreenBitmap = ss;
        }

        if (width != mDisplayMetrics.widthPixels || height != mDisplayMetrics.heightPixels) {
            /*SPRD bug 670330:Catch IllegalArgumentException*/
            try {
                // Crop the screenshot to selected region
                Bitmap cropped = Bitmap.createBitmap(mScreenBitmap, x, y, width, height);
                mScreenBitmap.recycle();
                mScreenBitmap = cropped;
            } catch (IllegalArgumentException e) {
                // TODO: handle exception
                e.printStackTrace();
                notifyScreenshotError(mContext, mNotificationManager,
                        R.string.screenshot_failed_to_save_unknown_text);
                finisher.run();
                setIsInLongshot(0);
                mScreenBitmap.recycle();
                mScreenBitmap = null;
                return;
            }
            /*@}*/
        }

        // Optimizations
        mScreenBitmap.setHasAlpha(false);
        mScreenBitmap.prepareToDraw();

        mScreenshotView.setImageBitmap(mScreenBitmap);
        mScreenshotLayout.requestFocus();

        // Setup the animation with the screenshot just taken
        if (mScreenshotAnimation != null) {
            if (mScreenshotAnimation.isStarted()) {
                mScreenshotAnimation.end();
            }
            mScreenshotAnimation.removeAllListeners();
        }

        if(mScreenshotLayout.getParent() != null) {
            mWindowManager.removeView(mScreenshotLayout);
        }
        mWindowManager.addView(mScreenshotLayout, mWindowLayoutParams);
        ValueAnimator screenshotDropInAnim = createScreenshotDropInAnimation();
        mScreenshotAnimation = new AnimatorSet();
        mScreenshotAnimation.play(screenshotDropInAnim);
        mScreenshotAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                float degrees = getDegreesForRotation(mDisplay.getRotation());
                if (degrees > 0) {
                    if(mScreenshotLayout.getParent() != null) {
                        mWindowManager.removeView(mScreenshotLayout);
                    }
                    setIsInLongshot(0);
                    if(mFinisher != null) {
                        mFinisher.run();
                    }
                    Toast.makeText(mContext, R.string.screen_shot_landscape_tips, Toast.LENGTH_SHORT).show();
                    return;
                }
                int[] location = new int[2];
                mScreenshotView.getLocationOnScreen(location);
                if(mMoreActionLayout.getParent() != null) {
                    mWindowManager.removeView(mMoreActionLayout);
                }
                mWindowManager.addView(mMoreActionLayout, mWindowLayoutParams);
                mScreenshotSelectorView.startRegionSelection((int) dims[0], (int) dims[1], location);
                initButtonActions(finisher);

                resetDate();
                sendLongscreenshotBroadcast();
                mScreenshotLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (btnScrollMode.getVisibility() != View.VISIBLE) {
                            setSupportLongScreenshot(supportLongScreenshot);
                        }
                    }
                }, 500);
            }
        });
        mScreenshotLayout.post(new Runnable() {
            @Override
            public void run() {
                mScreenshotView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                mScreenshotView.buildLayer();
                mScreenshotAnimation.start();
            }
        });
    }

    private Button btnScrollMode;
    private void initButtonActions(final Runnable finisher) {
        Button btnCancel = (Button) mMoreActionLayout.findViewById(R.id.btn_cancel);
        Button btnShare = (Button) mMoreActionLayout.findViewById(R.id.btn_share);
        Button btnSave = (Button) mMoreActionLayout.findViewById(R.id.btn_save);

        mMoreActionLayout.findViewById(R.id.more_action_layout).setVisibility(View.VISIBLE);
        btnCancel.setVisibility(View.VISIBLE);
        btnCancel.setText(mContext.getString(com.android.internal.R.string.cancel));
        btnSave.setVisibility(View.VISIBLE);
        btnSave.setText(mContext.getString(R.string.save));
        btnShare.setText(mContext.getString(com.android.internal.R.string.share));
        KeyguardManager keyguardManager =
                (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager.isKeyguardLocked()) {
            btnShare.setVisibility(View.INVISIBLE);
        } else {
            btnShare.setVisibility(View.VISIBLE);
        }
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopLongscreenshot();
                stopScreenshot();
                finisher.run();
            }
        });
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareScreenshot(finisher);
                setIsInLongshot(0);
            }
         });
         btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveScreenshot(finisher);
            }
        });
        btnScrollMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isInLongscreenshot) {
                    v.setEnabled(false);
                    startLongscreenshot();
                    ((Button)v).setText(mContext.getString(R.string.stop_long_screen_shot));
                    mMoreActionLayout.findViewById(R.id.more_action_layout).setVisibility(View.GONE);
                    mScreenshotSelectorView.setVisibility(View.GONE);
                    mSecondScreenshotView.setVisibility(View.GONE);
                    float s = scrollView.getScaleX();
                    bottomFrame.setScaleX(s);
                    bottomFrame.setScaleY(s);
                } else {
                    btnScrollMode.setEnabled(false);
                    btnScrollMode.setText("stopping...");
                    stopLongBitmapAnimation();
                    mMoreActionLayout.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (isInLongscreenshot) {
                                saveAndShowLongScreenShot();
                                stopLongscreenshot();
                                stopScreenshot();
                            }
                        }
                    }, STOP_LONGSCREENSHOT_TIMEOUT);
                }
            }
        });
    }

    private static final long STOP_LONGSCREENSHOT_TIMEOUT = 3000;

    private TakeScreenshotService screenshotService = null;
    public void setScreenshotService(TakeScreenshotService service) {
        screenshotService = service;
    }

    BroadcastReceiver mStopLongScreenshotReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopLongBitmapAnimation();
            stopLongscreenshot();
            stopScreenshot();
            if(mFinisher != null) {
                mFinisher.run();
                mFinisher = null;
            }
        }
    };

    private boolean isInLongscreenshot = false;
    protected boolean supportLongScreenshot = false;
    protected void startLongscreenshot() {
        isInLongscreenshot = true;
        if (supportLongScreenshot) screenshotService.sendLongscreenshotMessage(TakeScreenshotService.START_LONG_SCREENSHOT);
        btnScrollMode.setEnabled(true);

    }

    private void registerStopLongScreenshotReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("action.stop.longscreenshot");
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction("android.intent.action.PHONE_STATE");
        mContext.registerReceiver(mStopLongScreenshotReceiver, filter);
    }

    public void setSupportLongScreenshot(boolean supportLongScreenshot) {
        this.supportLongScreenshot = supportLongScreenshot;
        if (supportLongScreenshot) {
            btnScrollMode.setText(mContext.getString(R.string.start_long_screen_shot));
            btnScrollMode.setEnabled(true);
            btnScrollMode.setVisibility(View.VISIBLE);
        } else {
            btnScrollMode.setText(mContext.getString(R.string.not_support_long_screen_shot));
            btnScrollMode.setEnabled(false);
            btnScrollMode.setVisibility(View.VISIBLE);
        }
        registerStopLongScreenshotReceiver();
    }

    public void notifyScreenshotError() {
        Log.d(TAG, "notifyScreenshotError");
        stopLongBitmapAnimation();
        stopLongscreenshot();
        stopScreenshot();
        Toast.makeText(mContext, R.string.not_support_long_screen_shot, Toast.LENGTH_SHORT).show();
    }

    private void resetDate() {
        isInLongscreenshot = false;
        scrollComplete = false;
        bitmapAnimating = false;
        currentAnimator = null;
        first = null;
        second = null;
        longBitmap = null;
        capturedBitmap = null;
        statusBarBitmap = null;
        cropOverlayBitmap = null;
        supportLongScreenshot = false;
        scrollView.setScrollY(0);
        animators.clear();
        bitmaps.clear();
    }

    private void releaseBitmap(Bitmap b) {
        if ( b != null && !b.isRecycled() ) {
           b.recycle();  //cause Called getHeight() on a recycle()'d bitmap! This is undefined behavior!
           b = null;
        }
    }

    private boolean scrollComplete = false;
    public void completeLongscreenshot() {
        scrollComplete = true;
        if (bitmapAnimating) {
            return;
        }
        stopLongscreenshot();
        stopScreenshot();
        saveAndShowLongScreenShot();
    }

    private BitmapAnimator currentAnimator = null;
    public void stopLongscreenshot() {
        if (!isInLongscreenshot) {
            screenshotService.sendLongscreenshotMessage(TakeScreenshotService.CANCEL_LONG_SCREENSHOT);
            setIsInLongshot(0);
            return;
        }
        screenshotService.sendLongscreenshotMessage(TakeScreenshotService.STOP_LONG_SCREENSHOT);
        try {
            mContext.unregisterReceiver(mStopLongScreenshotReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (longBitmap == null) longBitmap = mScreenBitmap;
        setIsInLongshot(0);
        isInLongscreenshot = false;
    }

    private void saveAndShowLongScreenShot() {
        Log.d(TAG, "saveAndShowLongScreenShot");
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                savaBitmapToFile(getCapturedBitmap());
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                Log.d(TAG, "savaBitmapToFile onPostExecute");
                super.onPostExecute(aVoid);
                showPicwithGallery();
                if(mFinisher != null) {
                    mFinisher.run();
                    mFinisher = null;
                }
            }
        }.execute();
    }

    protected void showCapturedBitmap() {
        mScreenshotView.setScaleY(1.0f);
    }

    protected Bitmap getCapturedBitmap() {
        if(longBitmap == null) {
            return mScreenBitmap;
        }
        if (longBitmap.getHeight() < mScreenBitmap.getHeight()){
            return mScreenBitmap;
        }
        List<Bitmap> list = new ArrayList<Bitmap>();
        int height = scrollView.getHeight() + scrollView.getScrollY() - (second!= null ? second.getHeight() : 0);
        if(longBitmap.getHeight() < height) {
            Log.d(TAG, "longbitmap height:" + longBitmap.getHeight() + " must bigger than height:" + height);
            return longBitmap;
        }
        Bitmap b = Bitmap.createBitmap(longBitmap, 0, 0, longBitmap.getWidth(), height);
        list.add(b);
        if (second != null) {
            list.add(second);
        }
        return mergeBitmaps(list);
    }

    private void savaBitmapToFile(Bitmap bitmap) {
        Log.d(TAG, "savaBitmapToFile..");
        try {
            File file = new File(mScreenshotPicInfo.longscreenshotFilePath);
            if (file.exists() && file.delete()) {
                file.createNewFile();
            }
            OutputStream out = new FileOutputStream(mScreenshotPicInfo.longscreenshotFilePath);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();

            // Save the screenshot to the MediaStore
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.ImageColumns.DATA, mScreenshotPicInfo.longscreenshotFilePath);
            values.put(MediaStore.Images.ImageColumns.TITLE, mScreenshotPicInfo.mImageFileName);
            values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, mScreenshotPicInfo.mImageFileName);
            values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, mScreenshotPicInfo.mImageTime);
            values.put(MediaStore.Images.ImageColumns.DATE_ADDED, mScreenshotPicInfo.dateSeconds);
            values.put(MediaStore.Images.ImageColumns.DATE_MODIFIED, mScreenshotPicInfo.dateSeconds);
            values.put(MediaStore.Images.ImageColumns.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.ImageColumns.WIDTH, bitmap.getWidth());
            values.put(MediaStore.Images.ImageColumns.HEIGHT, bitmap.getHeight());
            values.put(MediaStore.Images.ImageColumns.SIZE, new File(mScreenshotPicInfo.longscreenshotFilePath).length());
            mScreenshotPicInfo.uri = mResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            //bitmap.recycle();//may cause "java.lang.IllegalStateException: Can't compress a recycled bitmap"
        } catch (Exception e) {
            e.printStackTrace();
            stopScreenshot();
        }
    }

    public void notSupportLongscreenshot() {
        stopLongscreenshot();
        isInLongscreenshot = false;
    }

    public boolean isInLongscreenshot() {
        return isInLongscreenshot;
    }

    private void showPicwithGallery() {
        Log.d(TAG, "showPicwithGallery");
        Intent intent = getGalleryIntent(mContext);
        if (intent == null) {
            Log.d(TAG, "no matched gallery, return");
            return;
        }
        mContext.startActivity(intent);
    }

    private static final String GALLERY_PACKAGE_NAME = "com.android.gallery3d";
    private static final String GALLERY_ACTIVITY_CLASS =
            "com.android.gallery3d.app.GalleryActivity";
    public Intent getGalleryIntent(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        //Uri uri = FileProvider.getUriForFile(context, GALLERY_PACKAGE_NAME + ".fileprovider", new File(longscreenshotFilePath));
        intent.setDataAndType(mScreenshotPicInfo.uri, "image/*");
        intent.putExtra("read-only", false);
        //intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    //private String longscreenshotFilePath = null;
    private ScreenshotPicInfo mScreenshotPicInfo = new ScreenshotPicInfo();
    void sendLongscreenshotBroadcast() {
        ActivityManager mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = mActivityManager.getRunningTasks(1);
        ComponentName cmp  = tasks.get(0).topActivity;
        SaveImageInBackgroundTask.getScreenshotPicInfo(mScreenshotPicInfo);

        String packageName = cmp.getPackageName();
        String activityName = cmp.getClassName();
        Intent intent = new Intent("action.longscreenshot");
        intent.putExtra("package", packageName);
        intent.putExtra("activity", activityName);
        intent.putExtra("filepath", mScreenshotPicInfo.longscreenshotFilePath);
        mContext.sendBroadcast(intent);
        Log.d(TAG, "sendLongscreenshotBroadcast: packageName=" + packageName + " activityName=" + activityName);
    }

    private void shareScreenshot(Runnable finisher) {
        try {
            Log.d(TAG, "shareScreenshot");
            mScreenBitmap = getRealBitmapRegion();
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "saveScreenShot error", e);
            // TODO: handle exception
            e.printStackTrace();
            notifyScreenshotError(mContext, mNotificationManager,
                    R.string.screenshot_failed_to_save_unknown_text);
            finisher.run();
            mScreenBitmap.recycle();
            mScreenBitmap = null;
            return;
        }
        saveSharedScreenshotInWorkerThread(finisher);
        stopScreenshot();
    }

    private void saveScreenshot(Runnable finisher) {
        Log.d(TAG, "saveScreenShot...");
        try {
            Log.d(TAG, "mScreenshotSelectorView.getRealSelectionLocationX():" + mScreenshotSelectorView.getRealSelectionLocationX()
             + " mScreenshotSelectorView.getRealSelectionLocationY():" + mScreenshotSelectorView.getRealSelectionLocationY()
             + " mScreenshotSelectorView.getRealSelectionRegionWidth():" + mScreenshotSelectorView.getRealSelectionRegionWidth()
             + " mScreenshotSelectorView.getRealSelectionRegionHeight():" + mScreenshotSelectorView.getRealSelectionRegionHeight());
            mScreenshotSelectorView.printLogs();

            Log.d(TAG, "saveScreenshot mScreenBitmap.getWidth:" + mScreenBitmap.getWidth() + " mScreenBitmap.getHeight:" + mScreenBitmap.getHeight());
            mScreenBitmap = getRealBitmapRegion();
            Log.d(TAG, "saveScreenshot after mScreenBitmap.getWidth:" + mScreenBitmap.getWidth() + " mScreenBitmap.getHeight:" + mScreenBitmap.getHeight());
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "saveScreenShot error", e);
            // TODO: handle exception
            e.printStackTrace();
            notifyScreenshotError(mContext, mNotificationManager,
                    R.string.screenshot_failed_to_save_unknown_text);
            finisher.run();
            mScreenBitmap.recycle();
            mScreenBitmap = null;
            return;
        }
        saveScreenshotInWorkerThread(finisher);
        stopScreenshot();
    }

    private Bitmap getRealBitmapRegion() {
        Log.d(TAG, "getRealBitmapRegion start");
            // Crop the screenshot to selected region
            Bitmap cropped = Bitmap.createBitmap(mScreenBitmap,
                    mScreenshotSelectorView.getRealSelectionLocationX(), mScreenshotSelectorView.getRealSelectionLocationY(),
                    mScreenshotSelectorView.getRealSelectionRegionWidth(), mScreenshotSelectorView.getRealSelectionRegionHeight());
            if(cropped.getWidth() != mScreenBitmap.getWidth() || cropped.getHeight() != mScreenBitmap.getHeight()) {
                mScreenBitmap.recycle();
            }
            Log.d(TAG, "getRealBitmapRegion end");
        return cropped;
    }

    void takeRegionScreenshot(Runnable finisher) {
        if(isInLongscreenshot()) {
            Log.d(TAG, "current aready in mode!" + isInLongscreenshot());
            return;
        }
        setIsInLongshot(1);
        scrollView.setVisibility(View.VISIBLE);
        mFinisher = finisher;
        Log.d(TAG, "takeRegionScreenshot");
        float degrees = getDegreesForRotation(mDisplay.getRotation());
        String checkRes = null;
        if (degrees > 0) checkRes = mContext.getString(R.string.screen_shot_landscape_tips);
        if (!TextUtils.isEmpty(checkRes)) {
            Toast info = Toast.makeText(mContext, checkRes, Toast.LENGTH_SHORT);
            info.show();
            finisher.run();
            setIsInLongshot(0);
            return;
        }
        mDisplay.getRealMetrics(mDisplayMetrics);
        takeScreenshotNoAnimation(finisher, 0, 0, mDisplayMetrics.widthPixels,
                mDisplayMetrics.heightPixels);

        Log.d(TAG, "(int) mDisplay.getWidth():" + (int) mDisplay.getWidth() + " (int) mDisplay.getHeight():" + (int) mDisplay.getHeight());
        mScreenshotSelectorView = (ScreenshotSelectorView) mMoreActionLayout.findViewById(R.id.screen_shot_select_view);
        mScreenshotSelectorView.setCurrentForLongScreenShot(true);
        mScreenshotSelectorView.setFocusable(true);
        mScreenshotSelectorView.setFocusableInTouchMode(true);
        mScreenshotSelectorView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                ScreenshotSelectorView view = (ScreenshotSelectorView) v;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.updateCurrentSelectionCircle((int) event.getX(), (int) event.getY());
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        view.updateRegionSelection((int) event.getX(), (int) event.getY());
                        return true;
                    case MotionEvent.ACTION_UP:
                        return true;
                }

                return false;
            }
        });
        mScreenshotLayout.post(new Runnable() {
            @Override
            public void run() {
                mScreenshotSelectorView.setVisibility(View.VISIBLE);
                mScreenshotSelectorView.requestFocus();
                mSecondScreenshotView.setVisibility(View.GONE);
            }
        });
    }

    /**
     * Displays a screenshot selector
     */
    void takeScreenshotPartial(final Runnable finisher, final boolean statusBarVisible,
            final boolean navBarVisible) {
        if (isInLongshot()) {
            return;
        }
        if(mScreenshotLayout.getParent() != null) {
            mWindowManager.removeView(mScreenshotLayout);
        }
        scrollView.setVisibility(View.GONE);
        mWindowManager.addView(mScreenshotLayout, mWindowLayoutParams);
        mScreenshotSelectorView = (ScreenshotSelectorView) mScreenshotLayout.findViewById(
                R.id.global_screenshot_selector);
        mScreenshotSelectorView.setCurrentForLongScreenShot(false);
        mScreenshotSelectorView.setFocusable(true);
        mScreenshotSelectorView.setFocusableInTouchMode(true);
        mScreenshotSelectorView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                ScreenshotSelectorView view = (ScreenshotSelectorView) v;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.startSelection((int) event.getX(), (int) event.getY());
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        view.updateSelection((int) event.getX(), (int) event.getY());
                        return true;
                    case MotionEvent.ACTION_UP:
                        view.setVisibility(View.GONE);
                        mWindowManager.removeView(mScreenshotLayout);
                        final Rect rect = view.getSelectionRect();
                        if (rect != null) {
                            if (rect.width() != 0 && rect.height() != 0) {
                                // Need mScreenshotLayout to handle it after the view disappears
                                mScreenshotLayout.post(new Runnable() {
                                    public void run() {
                                        takeScreenshot(finisher, statusBarVisible, navBarVisible,
                                                rect);
                                    }
                                });
                            }
                        }

                        view.stopSelection();
                        return true;
                }

                return false;
            }
        });
        mScreenshotLayout.post(new Runnable() {
            @Override
            public void run() {
                mScreenshotSelectorView.setVisibility(View.VISIBLE);
                mScreenshotSelectorView.requestFocus();
            }
        });
    }

    /**
     * Cancels screenshot request
     */
    void stopScreenshot() {
        // If the selector layer still presents on screen, we remove it and resets its state.
        if (mScreenshotSelectorView.getSelectionRect() != null) {
            //UNISOC: fix for bug 961104
            if(mScreenshotLayout.getParent() != null) {
                mWindowManager.removeView(mScreenshotLayout);
            }
            if(mMoreActionLayout.getParent() != null) {
                mWindowManager.removeView(mMoreActionLayout);
            }
            mScreenshotSelectorView.stopSelection();
        }
    }

    private Bitmap longBitmap = null;
    private Bitmap first = null;
    private Bitmap second = null;

    private Bitmap statusBarBitmap = null;
    private Bitmap cropOverlayBitmap = null;
    private Object updateLock = new Object();
    private List<Bitmap> bitmaps = new ArrayList<>();
    private int totalHeight = 0;

    void updateLongScreenshotView(final Bitmap b, final int overlayViewsTop, final int secondBitmapHeight) {

        Log.d(TAG, "updateLongScreenshotView overlayViewsTop:" + overlayViewsTop + " secondBitmapHeight:" + secondBitmapHeight);
        int statusBarHeight = getStatusBarHeight();
        if (secondBitmapHeight > 0 && second == null) {
            first = Bitmap.createBitmap(mScreenBitmap, 0, 0, mScreenBitmap.getWidth(), mScreenBitmap.getHeight() - secondBitmapHeight);
            second = Bitmap.createBitmap(mScreenBitmap, 0, mScreenBitmap.getHeight() - secondBitmapHeight,
                    mScreenBitmap.getWidth(), secondBitmapHeight);
            mSecondScreenshotView.setHwBitmapsInSwModeEnabled(true);
            mSecondScreenshotView.setImageBitmap(second);
            mSecondScreenshotView.setVisibility(View.VISIBLE);
        } else {
            first = mScreenBitmap;
            mSecondScreenshotView.setVisibility(View.GONE);
        }
        //bitmaps.add(first);

        statusBarBitmap = Bitmap.createBitmap(mScreenBitmap, 0, 0, mScreenBitmap.getWidth(), statusBarHeight);
        bitmaps.add(statusBarBitmap);

        int overlayHeight = 0;
        if (overlayViewsTop > 0) {
            overlayHeight = mScreenBitmap.getHeight() - secondBitmapHeight - overlayViewsTop;
            int startY = b.getHeight() - overlayHeight;
            cropOverlayBitmap = Bitmap.createBitmap(b, 0, startY, b.getWidth(), overlayHeight);
        }
        Bitmap cropStatusBarBitmap = Bitmap.createBitmap(b, 0, statusBarHeight, b.getWidth(), b.getHeight() - statusBarHeight - overlayHeight);
        bitmaps.add(cropStatusBarBitmap);
        /*if (overlayViewsTop > 0 && cropOverlayBitmap != null) {
            bitmaps.add(cropOverlayBitmap);
        }*/
        //if (secondBitmapHeight > 0 && second != null) bitmaps.add(second);
        longBitmap = mergeBitmaps(bitmaps);
        currentAnimator = createAnimator(mScreenBitmap, longBitmap);
        if(!mScreenshotView.isHardwareAccelerated()) {
            longBitmap = longBitmap.copy(Bitmap.Config.ARGB_8888, true);
        }
        mScreenshotView.setHwBitmapsInSwModeEnabled(true);
        mScreenshotView.setImageBitmap(longBitmap);
        Rect rect = new Rect();
        mScreenshotView.getGlobalVisibleRect(rect);
        if (!bitmapAnimating) {
            currentAnimator.start();
        }
    }

    private int getStatusBarHeight() {
        int statusBarHeight = -1;
        int resourceId = mContext.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = mContext.getResources().getDimensionPixelSize(resourceId);
        }
        return statusBarHeight;
    }

    public boolean isbitmapAnimating() {
        return bitmapAnimating;
    }

    private List<ValueAnimator> animators = new ArrayList<>();
    private boolean bitmapAnimating = false;
    private Bitmap capturedBitmap = null;
    private BitmapAnimator createAnimator(final Bitmap current, final Bitmap target) {
        final BitmapAnimator animator = new BitmapAnimator(current, target);
        animator.setInterpolator(new LinearInterpolator());
        return animator;
    }

    private class BitmapAnimator extends ValueAnimator {
        private Bitmap current;
        private Bitmap target;
        private int currentValue = 0;
        public BitmapAnimator(Bitmap current, Bitmap target) {
            this.current = current;
            this.target = target;
            this.setIntValues(target.getHeight() - current.getHeight());
            //this.setIntValues(5000);
            setAnimationDuration();
            addUpdateListener(updateListener);
            addListener(listenerAdapter);
        }

        public void setTargetBitmap(Bitmap target) {
            this.target = target;
            this.setIntValues(target.getHeight() - current.getHeight());
            setAnimationDuration();
        }

        public void setAnimationDuration() {
            int increasedHeight = target.getHeight() - current.getHeight();
            int duration = (int)(increasedHeight * 1000.0f / 200);
            Log.d(TAG, "animator duration = " + duration);
            if(duration < 0) {
                duration = 0;
            }
            setDuration(duration);
        }

        private AnimatorUpdateListener updateListener = new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                int updateValue = (int)a.getAnimatedValue();
                if (updateValue == currentValue) updateValue += 3;
                scrollView.scrollBy(0,updateValue - currentValue);
                scrollView.invalidate();
                currentValue = updateValue;
            }
        };

        private AnimatorListenerAdapter listenerAdapter = new AnimatorListenerAdapter() {

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                bitmapAnimating = false;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                Log.d(TAG, "onAnimationEnd");
                super.onAnimationEnd(animation);
                stopLongscreenshot();
                stopScreenshot();
                saveAndShowLongScreenShot();
            }

            @Override
            public void onAnimationStart(Animator animation) {
                Log.d(TAG, "onAnimationStart");
                super.onAnimationStart(animation);
                bitmapAnimating = true;
            }
        };
    }

    private void stopLongBitmapAnimation() {
        if (currentAnimator != null && currentAnimator.isRunning()) {
            animators.clear();
            currentAnimator.cancel();
        }
    }

    private static Bitmap mergeBitmaps(List<Bitmap> bitmaps) {
        if (bitmaps.size() == 0) return null;
        if (bitmaps.size() == 1) return bitmaps.get(0);
        int height = 0;
        for (int i=0; i<bitmaps.size(); i++) {
            Bitmap b = bitmaps.get(i);
            height += b.getHeight();
        }

        Bitmap result = Bitmap.createBitmap(bitmaps.get(0).getWidth(), height, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(result);
        int currentHight = 0;
        for (int i=0; i<bitmaps.size(); i++) {
            Bitmap b = bitmaps.get(i);
            if(!canvas.isHardwareAccelerated()) {
                b = bitmaps.get(i).copy(Bitmap.Config.ARGB_8888, true);
            }
            canvas.drawBitmap(b, 0, currentHight, null);
            canvas.save(Canvas.ALL_SAVE_FLAG);
            canvas.restore();
            currentHight += b.getHeight();
        }

        return result;
    }

    private boolean isInLongshot() {
        int result = Settings.System.getIntForUser(mResolver, "longshot_switch", 0, UserHandle.USER_OWNER);
        Log.d(TAG, "get longshot_switch result:" + result);
        return result == 1;
    }

    private void setIsInLongshot(int value) {
        Log.d(TAG, "setIsInLongshot:" + value);
        Settings.System.putIntForUser(mResolver, "longshot_switch", value, UserHandle.USER_OWNER);
    }

    /**
     * Starts the animation after taking the screenshot
     */
    private void startAnimation(final Runnable finisher, int w, int h, boolean statusBarVisible,
            boolean navBarVisible) {
        // If power save is on, show a toast so there is some visual indication that a screenshot
        // has been taken.
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (powerManager.isPowerSaveMode()) {
            Toast.makeText(mContext, R.string.screenshot_saved_title, Toast.LENGTH_SHORT).show();
        }

        // Add the view for the animation
        mScreenshotView.setImageBitmap(mScreenBitmap);
        mScreenshotLayout.requestFocus();

        // Setup the animation with the screenshot just taken
        if (mScreenshotAnimation != null) {
            if (mScreenshotAnimation.isStarted()) {
                mScreenshotAnimation.end();
            }
            mScreenshotAnimation.removeAllListeners();
        }

        if(mScreenshotLayout.getParent() != null) {
            mWindowManager.removeView(mScreenshotLayout);
        }
        mWindowManager.addView(mScreenshotLayout, mWindowLayoutParams);
        ValueAnimator screenshotDropInAnim = createScreenshotDropInAnimation();
        ValueAnimator screenshotFadeOutAnim = createScreenshotDropOutAnimation(w, h,
                statusBarVisible, navBarVisible);
        mScreenshotAnimation = new AnimatorSet();
        mScreenshotAnimation.playSequentially(screenshotDropInAnim, screenshotFadeOutAnim);
        mScreenshotAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Save the screenshot once we have a bit of time now
                saveScreenshotInWorkerThread(finisher);
                mWindowManager.removeView(mScreenshotLayout);

                // Clear any references to the bitmap
                mScreenBitmap = null;
                mScreenshotView.setImageBitmap(null);
            }
        });
        mScreenshotLayout.post(new Runnable() {
            @Override
            public void run() {
                /* SPRD:Bug 900116 @{*/
                if ((Global.getInt(mContext.getContentResolver(),
                        SCREENSHOTS_GLOBAL_ZEN_MODE, 0) == Global.ZEN_MODE_OFF)
                        && !(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE)
                         && !(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT)){
                /* @} */
                    // Play the shutter sound to notify that we've taken a screenshot
                    mCameraSound.play(MediaActionSound.SHUTTER_CLICK);
                }

                mScreenshotView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                mScreenshotView.buildLayer();
                mScreenshotAnimation.start();
            }
        });
    }
    private ValueAnimator createScreenshotDropInAnimation() {
        final float flashPeakDurationPct = ((float) (SCREENSHOT_FLASH_TO_PEAK_DURATION)
                / SCREENSHOT_DROP_IN_DURATION);
        final float flashDurationPct = 2f * flashPeakDurationPct;
        final Interpolator flashAlphaInterpolator = new Interpolator() {
            @Override
            public float getInterpolation(float x) {
                // Flash the flash view in and out quickly
                if (x <= flashDurationPct) {
                    return (float) Math.sin(Math.PI * (x / flashDurationPct));
                }
                return 0;
            }
        };
        final Interpolator scaleInterpolator = new Interpolator() {
            @Override
            public float getInterpolation(float x) {
                // We start scaling when the flash is at it's peak
                if (x < flashPeakDurationPct) {
                    return 0;
                }
                return (x - flashDurationPct) / (1f - flashDurationPct);
            }
        };
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(SCREENSHOT_DROP_IN_DURATION);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mBackgroundView.setAlpha(0f);
                mBackgroundView.setVisibility(View.VISIBLE);
                scrollView.setAlpha(0f);
                scrollView.setTranslationX(0f);
                scrollView.setTranslationY(0f);
                scrollView.setScaleX(SCREENSHOT_SCALE + mBgPaddingScale);
                scrollView.setScaleY(SCREENSHOT_SCALE + mBgPaddingScale);
                mScreenshotView.setVisibility(View.VISIBLE);
                mScreenshotFlash.setAlpha(0f);
                mScreenshotFlash.setVisibility(View.VISIBLE);
            }
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                //mScreenshotFlash.setVisibility(View.GONE);
            }
        });
        anim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float t = (Float) animation.getAnimatedValue();
                float scaleT = (SCREENSHOT_SCALE + mBgPaddingScale)
                    - scaleInterpolator.getInterpolation(t)
                        * (SCREENSHOT_SCALE - SCREENSHOT_DROP_IN_MIN_SCALE);
                mBackgroundView.setAlpha(scaleInterpolator.getInterpolation(t) * BACKGROUND_ALPHA);
                scrollView.setAlpha(t);
                scrollView.setScaleX(scaleT);
                scrollView.setScaleY(scaleT);
                mScreenshotFlash.setAlpha(flashAlphaInterpolator.getInterpolation(t));
            }
        });
        return anim;
    }
    private ValueAnimator createScreenshotDropOutAnimation(int w, int h, boolean statusBarVisible,
            boolean navBarVisible) {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setStartDelay(SCREENSHOT_DROP_OUT_DELAY);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mBackgroundView.setVisibility(View.GONE);
                mScreenshotView.setVisibility(View.GONE);
                mScreenshotView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });

        if (!statusBarVisible || !navBarVisible) {
            // There is no status bar/nav bar, so just fade the screenshot away in place
            anim.setDuration(SCREENSHOT_FAST_DROP_OUT_DURATION);
            anim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float t = (Float) animation.getAnimatedValue();
                    float scaleT = (SCREENSHOT_DROP_IN_MIN_SCALE + mBgPaddingScale)
                            - t * (SCREENSHOT_DROP_IN_MIN_SCALE - SCREENSHOT_FAST_DROP_OUT_MIN_SCALE);
                    mBackgroundView.setAlpha((1f - t) * BACKGROUND_ALPHA);
                    scrollView.setAlpha(1f - t);
                    scrollView.setScaleX(scaleT);
                    scrollView.setScaleY(scaleT);
                }
            });
        } else {
            // In the case where there is a status bar, animate to the origin of the bar (top-left)
            final float scaleDurationPct = (float) SCREENSHOT_DROP_OUT_SCALE_DURATION
                    / SCREENSHOT_DROP_OUT_DURATION;
            final Interpolator scaleInterpolator = new Interpolator() {
                @Override
                public float getInterpolation(float x) {
                    if (x < scaleDurationPct) {
                        // Decelerate, and scale the input accordingly
                        return (float) (1f - Math.pow(1f - (x / scaleDurationPct), 2f));
                    }
                    return 1f;
                }
            };

            // Determine the bounds of how to scale
            float halfScreenWidth = (w - 2f * mBgPadding) / 2f;
            float halfScreenHeight = (h - 2f * mBgPadding) / 2f;
            final float offsetPct = SCREENSHOT_DROP_OUT_MIN_SCALE_OFFSET;
            final PointF finalPos = new PointF(
                -halfScreenWidth + (SCREENSHOT_DROP_OUT_MIN_SCALE + offsetPct) * halfScreenWidth,
                -halfScreenHeight + (SCREENSHOT_DROP_OUT_MIN_SCALE + offsetPct) * halfScreenHeight);

            // Animate the screenshot to the status bar
            anim.setDuration(SCREENSHOT_DROP_OUT_DURATION);
            anim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float t = (Float) animation.getAnimatedValue();
                    float scaleT = (SCREENSHOT_DROP_IN_MIN_SCALE + mBgPaddingScale)
                        - scaleInterpolator.getInterpolation(t)
                            * (SCREENSHOT_DROP_IN_MIN_SCALE - SCREENSHOT_DROP_OUT_MIN_SCALE);
                    mBackgroundView.setAlpha((1f - t) * BACKGROUND_ALPHA);
                    scrollView.setAlpha(1f - scaleInterpolator.getInterpolation(t));
                    scrollView.setScaleX(scaleT);
                    scrollView.setScaleY(scaleT);
                    scrollView.setTranslationX(t * finalPos.x);
                    scrollView.setTranslationY(t * finalPos.y);
                }
            });
        }
        return anim;
    }

    static void notifyScreenshotError(Context context, NotificationManager nManager, int msgResId) {
        Resources r = context.getResources();
        String errorMsg = r.getString(msgResId);

        // Repurpose the existing notification to notify the user of the error
        Notification.Builder b = new Notification.Builder(context, NotificationChannels.ALERTS)
            .setTicker(r.getString(R.string.screenshot_failed_title))
            .setContentTitle(r.getString(R.string.screenshot_failed_title))
            .setContentText(errorMsg)
            .setSmallIcon(R.drawable.stat_notify_image_error)
            .setWhen(System.currentTimeMillis())
            .setVisibility(Notification.VISIBILITY_PUBLIC) // ok to show outside lockscreen
            .setCategory(Notification.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setColor(context.getColor(
                        com.android.internal.R.color.system_notification_accent_color));
        final DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        final Intent intent = dpm.createAdminSupportIntent(
                DevicePolicyManager.POLICY_DISABLE_SCREEN_CAPTURE);
        if (intent != null) {
            final PendingIntent pendingIntent = PendingIntent.getActivityAsUser(
                    context, 0, intent, 0, null, UserHandle.CURRENT);
            b.setContentIntent(pendingIntent);
        }

        SystemUI.overrideNotificationAppName(context, b, true);

        Notification n = new Notification.BigTextStyle(b)
                .bigText(errorMsg)
                .build();
        nManager.notify(SystemMessage.NOTE_GLOBAL_SCREENSHOT, n);
    }

    /**
     * Receiver to proxy the share or edit intent.
     */
    public static class ScreenshotActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                ActivityManager.getService().closeSystemDialogs(SYSTEM_DIALOG_REASON_SCREENSHOT);
            } catch (RemoteException e) {
            }

            Intent actionIntent = intent.getParcelableExtra(SHARING_INTENT);

            // If this is an edit & default editor exists, route straight there.
            String editorPackage = context.getResources().getString(R.string.config_screenshotEditor);
            if (actionIntent.getAction() == Intent.ACTION_EDIT &&
                    editorPackage != null && editorPackage.length() > 0) {
                actionIntent.setComponent(ComponentName.unflattenFromString(editorPackage));
                final NotificationManager nm =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                nm.cancel(SystemMessage.NOTE_GLOBAL_SCREENSHOT);
            } else {
                PendingIntent chooseAction = PendingIntent.getBroadcast(context, 0,
                        new Intent(context, GlobalScreenshot.TargetChosenReceiver.class),
                        PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
                actionIntent = Intent.createChooser(actionIntent, null,
                        chooseAction.getIntentSender())
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            ActivityOptions opts = ActivityOptions.makeBasic();
            opts.setDisallowEnterPictureInPictureWhileLaunching(true);

            /* UNISOC Bug 899996 can not share screenshot captured in notifications @{ */
            actionIntent.putExtra(ChooserActivity.EXTRA_PRIVATE_RETAIN_IN_ON_STOP, true);
            KeyguardManager keyguardManager =
                    (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager.isKeyguardLocked()) {
                try {
                    WindowManagerGlobal.getWindowManagerService().dismissKeyguard(null,null);
                } catch (RemoteException e) {
                }
            }
            /* @} */

            context.startActivityAsUser(actionIntent, opts.toBundle(), UserHandle.CURRENT);
        }
    }

    /**
     * Removes the notification for a screenshot after a share or edit target is chosen.
     */
    public static class TargetChosenReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Clear the notification
            final NotificationManager nm =
                      (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(SystemMessage.NOTE_GLOBAL_SCREENSHOT);
        }
    }

    /**
     * Removes the last screenshot.
     */
    public static class DeleteScreenshotReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.hasExtra(SCREENSHOT_URI_ID)) {
                return;
            }

            // Clear the notification
            final NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            final Uri uri = Uri.parse(intent.getStringExtra(SCREENSHOT_URI_ID));
            nm.cancel(SystemMessage.NOTE_GLOBAL_SCREENSHOT);

            // And delete the image from the media store
            new DeleteImageInBackgroundTask(context).execute(uri);
        }
    }
}
