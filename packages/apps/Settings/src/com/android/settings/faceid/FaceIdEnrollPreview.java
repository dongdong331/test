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
 * limitations under the License
 */

package com.android.settings.faceid;

import java.io.File;
import java.util.Arrays;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.media.MediaPlayer;
import android.hardware.face.FaceManager;

import com.android.settings.R;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.sprd.settings.sensor.LightSensorManager;

/**
 * activity for faceid enrollment.
 */
public class FaceIdEnrollPreview extends Activity {

    private static final String TAG = "FaceIdEnrollPreview";
    private static final String TAG_SIDECAR = "sidecar";
    private static final int CHOOSE_LOCK_GENERIC_REQUEST = 1;
    private static final int ENROLLMENT_PROGRESS_COMPLETE = 100;
    private static final int FINISH_DELAY = 250;

    private static final int FACE_ENROLL_INFO_OK = 0;
    private static final int FACE_ENROLL_INFO_NOFACE = 1;
    private static final int FACE_ENROLL_INFO_MULTI_FACE = 2;
    private static final int FACE_ENROLL_INFO_LIVENESS = 3;
    private static final int FACE_ENROLL_INFO_EYECLOSE = 4;
    private static final int FACE_ENROLL_INFO_OCCLUSION = 5;
    private static final int FACE_ENROLL_INFO_BLUR = 6;
    private static final int FACE_ENROLL_INFO_OUTOFIMAGE = 7;
    private static final int FACE_ENROLL_INFO_MOVINGFAST = 8;
    private static final int FACE_ENROLL_INFO_AE_NOT_CONVERGED = 9;
    private static final int FACE_ENROLL_INFO_OFFSETPOS = 10;
    private static final int FACE_ENROLL_INFO_FAR = 11;
    private static final int FACE_ENROLL_INFO_NEAR = 12;
    private static final int FACE_HELP_NEED_UPANGLE = 13;
    private static final int FACE_HELP_NEED_DOWNANGLE = 14;
    private static final int FACE_ENROLL_INFO_NEEDRIGHTANGLE = 15;
    private static final int FACE_ENROLL_INFO_NEEDLEFTANGLE = 16;
    private static final int FACE_ENROLL_INFO_NEEDDIFFPOS = 17;
    private static final int FACE_ENROLL_INFO_ABNORMAL = 18;
    private static final int FACE_ENROLL_INFO_SAVE_ERROR = 19;
    private static final int FACEID_ENROLL_TIMEOUT = 1000;
    protected static final int MSG_HANDLE_CHECK_LIGHT = 10;
    protected static final int LIGHT_BRIGHTNESS_LIMIT = 5;

    private int mEnrollmentProgess = 0;
    private boolean mProgessChanged = false;
    private byte[] mToken;
    private TextView mShowEnrollingText;
    private FrameLayout mTextureFrameLayout;
    private FaceIdEnrollSidecar mSidecar;

    private static final int WIDTH = 960;
    private static final int HEIGHT = 720;

    // private IrisDetectThread detectRun = new IrisDetectThread();
    // private Thread detectThread = new Thread(detectRun);
    private PowerManager.WakeLock mWakeLock;
    private Handler mHandler;
    private static final int MSG_NOTIFY_ENROLL_PROGRESS_CHANGED = 100;
    // used for help code
    private long playTime = System.currentTimeMillis();
    private int frameCount = 0;
    private int failCount = 0;
    private int distRange = 0;
    private MediaPlayer mEnrollSuccess = null;
    private MediaPlayer mSoundCloser = null;
    private MediaPlayer mSoundFarther = null;
    private MediaPlayer mSoundLeft = null;
    private MediaPlayer mSoundRight = null;
    private FaceManager mFaceManager;
    private CancellationSignal mEnrollmentCancel;
    private CameraSurfaceView mCameraSurfaceView;
    private static Toast mToast;
    private long oneTime = 0;
    private long twoTime = 0;
    private int oldMsgId = 0;
    private TextView mEnrollTips;
    private int mLastProgress = 0;
    private int mDefaultBrightness;

    private class MyHandler extends Handler {

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case MSG_NOTIFY_ENROLL_PROGRESS_CHANGED:
                    mShowEnrollingText.setText(String.format(
                            FaceIdEnrollPreview.this.getString(R.string.default_enroll_value),
                            mEnrollmentProgess));
                    break;
                case MSG_HANDLE_CHECK_LIGHT:
                    checkLight();
                    mHandler.sendEmptyMessageDelayed(MSG_HANDLE_CHECK_LIGHT, 250);
                    break;
            }
        }
    }

    private FaceManager.EnrollmentCallback mEnrollmentCallback = new FaceManager.EnrollmentCallback() {
        @Override
        public void onEnrollmentError(int errMsgId, CharSequence errString) {
            Log.d(TAG, "onEnrollmentError:" + errMsgId + " errString:" + errString);
            if(errMsgId == FACEID_ENROLL_TIMEOUT) {
                showToast(R.string.faceid_enroll_timeout);
                if(mEnrollmentCancel != null) {
                    mEnrollmentCancel.cancel();
                }
                setResult(RESULT_CANCELED);
                finish();
            }
        }

        @Override
        public void onEnrollmentHelp(int helpMsgId, CharSequence helpString) {
            Log.d(TAG, "onEnrollmentHelp:" + helpMsgId + " helpString:" + helpString);
            switch (helpMsgId) {
                case FACE_ENROLL_INFO_OK:
                    setTips(R.string.face_enroll_info_ok);
                    break;
                case FACE_ENROLL_INFO_NOFACE:
                    setTips(R.string.face_enroll_info_noface);
                    break;
                case FACE_ENROLL_INFO_MULTI_FACE:
                    setTips(R.string.face_enroll_info_multi_face);
                    break;
                case FACE_ENROLL_INFO_LIVENESS:
                    setTips(R.string.face_enroll_info_liveness);
                    break;
                case  FACE_ENROLL_INFO_EYECLOSE:
                    setTips(R.string.face_enroll_info_eyeclose);
                    break;
                case FACE_ENROLL_INFO_OCCLUSION:
                    setTips(R.string.face_enroll_info_occlusion);
                    break;
                case FACE_ENROLL_INFO_BLUR:
                    setTips(R.string.face_enroll_info_blur);
                    break;
                case FACE_ENROLL_INFO_OUTOFIMAGE:
                    setTips(R.string.face_enroll_info_outofimage);
                    break;
                case FACE_ENROLL_INFO_MOVINGFAST:
                    setTips(R.string.face_enroll_info_movingfast);
                    break;
                case FACE_ENROLL_INFO_AE_NOT_CONVERGED:
                    setTips(R.string.face_enroll_info_ae_not_converged);
                    break;
                case FACE_ENROLL_INFO_OFFSETPOS:
                    setTips(R.string.face_enroll_info_offsetpos);
                    break;
                case FACE_ENROLL_INFO_FAR:
                    setTips(R.string.face_enroll_info_far);
                    break;
                case FACE_ENROLL_INFO_NEAR:
                    setTips(R.string.face_enroll_info_near);
                    break;
                case FACE_HELP_NEED_UPANGLE:
                    setTips(R.string.face_enroll_info_need_upangle);
                    break;
                case FACE_HELP_NEED_DOWNANGLE:
                    setTips(R.string.face_enroll_info_need_downangle);
                    break;
                case FACE_ENROLL_INFO_NEEDRIGHTANGLE:
                    setTips(R.string.face_enroll_info_needrightangle);
                    break;
                case FACE_ENROLL_INFO_NEEDLEFTANGLE:
                    setTips(R.string.face_enroll_info_needleftangle);
                    break;
                case FACE_ENROLL_INFO_NEEDDIFFPOS:
                    setTips(R.string.face_enroll_info_needdiffpos);
                    break;
                case FACE_ENROLL_INFO_ABNORMAL:
                    setTips(R.string.face_enroll_info_abnormal);
                    break;
                case FACE_ENROLL_INFO_SAVE_ERROR:
                    setTips(R.string.face_enroll_info_save_error);
                    break;
                default:
                    setTips(R.string.faceid_enroll_unknown);
                    break;
            }
        }

        @Override
        public void onEnrollmentProgress(int progress) {
            if(mLastProgress != progress) {
                clearTips();
                mLastProgress = progress;
            }
            mShowEnrollingText.setText(FaceIdEnrollPreview.this.getString(
                    R.string.default_enroll_value, progress));
            if(progress >= 100) {
                mHandler.postDelayed(mDelayedFinishRunnable, 200);
            }
        }
    };

    private void setTips(int msgId) {
        mEnrollTips.setText(msgId);
    }

    private void clearTips() {
        mEnrollTips.setText("");
    }

    private void showToast(int msgId) {
        if(mToast == null) {
            mToast = Toast.makeText(FaceIdEnrollPreview.this, msgId, Toast.LENGTH_SHORT);
            mToast.show();
            oneTime = System.currentTimeMillis();
        } else {
            twoTime = System.currentTimeMillis();
            if(oldMsgId == msgId) {
                if(twoTime - oneTime > 2000) {
                    mToast = Toast.makeText(FaceIdEnrollPreview.this, msgId, Toast.LENGTH_SHORT);
                    mToast.show();
                    oneTime = System.currentTimeMillis();
                }
            } else {
                if(twoTime - oneTime < 2000) {
                    oldMsgId = msgId;
                    mToast.setText(msgId);
                    mToast.show();
                } else {
                    mToast = Toast.makeText(FaceIdEnrollPreview.this, msgId, Toast.LENGTH_SHORT);
                    mToast.show();
                    oneTime = System.currentTimeMillis();
                }
            }
        }
    }

    private void cancelToast() {
        if(mToast != null) {
            mToast.cancel();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.faceid_enroll_enrolling_preview);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (isInMultiWindowMode()) {
            Toast.makeText(this, R.string.faceid_dock_forced_resizable, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mTextureFrameLayout = (FrameLayout) this.findViewById(R.id.texture_layout);
        mShowEnrollingText = (TextView) findViewById(R.id.faceid_status_hint);
        mToken = getIntent().getByteArrayExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
        mCameraSurfaceView = (CameraSurfaceView) findViewById(R.id.surfaceView);
        mWakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "faceid wakelock");
        mHandler = new MyHandler();
        mEnrollSuccess = MediaPlayer.create(this, R.raw.enrsucc);
        mSoundCloser = MediaPlayer.create(this, R.raw.closer);
        mSoundFarther = MediaPlayer.create(this, R.raw.farther);
        mSoundLeft = MediaPlayer.create(this, R.raw.moveleft);
        mSoundRight = MediaPlayer.create(this, R.raw.moveright);

        mEnrollTips = (TextView) findViewById(R.id.enroll_tips);

        mShowEnrollingText.setText(String.format(getString(R.string.default_enroll_value),
                mEnrollmentProgess));
        mFaceManager = (FaceManager) getSystemService(Context.FACE_SERVICE);

        mCameraSurfaceView.setSurfaceTextureListener(surfaceTextureListener);
        Button btnCancel = (Button) findViewById(R.id.btn_cancel);
        btnCancel.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View arg0) {
                if(mEnrollmentCancel != null) {
                    mEnrollmentCancel.cancel();
                }
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mCameraSurfaceView.getLayoutParams();
        params.width = getWidth();
        params.height = 960 * getWidth() / 720;
        mCameraSurfaceView.setLayoutParams(params);
        Log.d(TAG, "height:" + params.height + " width:" + params.width);
    }

    private int getWidth() {
        WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        return wm.getDefaultDisplay().getWidth();
    }

    /**
     * {@link android.view.TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener surfaceTextureListener =
        new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(
                final SurfaceTexture texture, final int width, final int height) {
                Log.d(TAG, "onSurfaceTextureAvailable width:" + width + " height:" + height);
                startEnrollment();
            }

            @Override
            public void onSurfaceTextureSizeChanged(
                final SurfaceTexture texture, final int width, final int height) {
                Log.d(TAG, "onSurfaceTextureSizeChanged width:" + width + " height:" + height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                Log.d(TAG, "onSurfaceTextureDestroyed");
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
                Log.d(TAG, "onSurfaceTextureUpdated");
            }
        };

    private void startEnrollment() {
        Log.d(TAG, "startEnrollment begin");
        mEnrollmentCancel = new CancellationSignal();
        try {
            final SurfaceTexture texture = mCameraSurfaceView.getSurfaceTexture();

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(WIDTH, HEIGHT);

            // This is the output Surface we need to start preview.
            final Surface surface = new Surface(texture);
            //mCameraSurfaceView.getHolder().setFixedSize(WIDTH, HEIGHT);
            mFaceManager.enroll(mToken, mEnrollmentCancel, 0, mEnrollmentCallback,
                    surface, WIDTH, HEIGHT, mHandler);
        } catch (Exception e) {
            Log.d(TAG, "startEnrollment Exception", e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mSidecar = (FaceIdEnrollSidecar) getFragmentManager().findFragmentByTag(TAG_SIDECAR);
        if (mSidecar == null) {
            mSidecar = new FaceIdEnrollSidecar();
            getFragmentManager().beginTransaction().add(mSidecar, TAG_SIDECAR).commit();
        }
    }

    private void handleShowLightScreen() {
        Log.d("faceid_screen_light", "handleShowLightScreen");
        getDefaultBrightness();
        LightSensorManager.getInstance().start(this);
        handleCheckShowLightScreen();
    }

    private void handleCheckShowLightScreen() {
        Log.d("faceid_screen_light", "handleCheckShowLightScreen");
        mHandler.sendEmptyMessageDelayed(MSG_HANDLE_CHECK_LIGHT, 500);
    }

    private void checkLight() {
        float lux = LightSensorManager.getInstance().getLux();
        float proxy = LightSensorManager.getInstance().getProxy();
        Log.d("faceid_screen_light", "checkLight lux:" + lux + " proxy:" + proxy);
        //lux light sensor brightness values 5，maybe modify it
        //if proxy sensor if closed, should not be set brightness
        if(lux < LIGHT_BRIGHTNESS_LIMIT && proxy > 0) {
            setMaxScreenBrightness();
        } else if (lux > 10) {
            resetDefaultScreenBrightness();
        }
    }

    private void getDefaultBrightness() {
        mDefaultBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1);
        Log.d("faceid_screen_light", "mDefaultBrightness:" + mDefaultBrightness);
    }

    private void resetDefaultScreenBrightness() {
        if(isFaceBrightnessEnabled()) {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, mDefaultBrightness);
        }
    }

    private void setMaxScreenBrightness() {
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 255);
    }

    private boolean isFaceBrightnessEnabled() {
        return FaceIdSettings.FACEID_BRIGHTNESS_FEATURE_ENABLED
                && Settings.System.getInt(getContentResolver(), FaceIdSettings.FACEID_BRIGHTNESS, 0) == 1;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWakeLock.acquire();
        if(isFaceBrightnessEnabled()) {
            handleShowLightScreen();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mEnrollmentProgess = 0;
        mWakeLock.release();
        if(isFaceBrightnessEnabled()) {
            if(mHandler != null) {
                mHandler.removeMessages(MSG_HANDLE_CHECK_LIGHT);
            }
            resetDefaultScreenBrightness();
            LightSensorManager.getInstance().stop();
        }
        if(mLastProgress < 100) {
            Log.d(TAG, "enroll breaked by user, progress:" + mLastProgress);
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSidecar.setListener(null);
        if (mEnrollmentCancel != null) {
            mEnrollmentCancel.cancel();
            mEnrollmentCancel = null;
        }
    }

    // Give the user a chance to see progress completed before jumping to the
    // next stage.
    private final Runnable mDelayedFinishRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "mDelayedFinishRunnable launchFinish()");
            launchFinish(mToken);
        }
    };

    private void launchFinish(byte[] token) {
        Intent intent = getFinishIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, token);
        startActivity(intent);
        finish();
    }

    protected Intent getFinishIntent() {
        return new Intent(this, FaceIdEnrollFinish.class);
    }
}
