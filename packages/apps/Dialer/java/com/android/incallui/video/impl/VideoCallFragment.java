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

package com.android.incallui.video.impl;

import android.Manifest.permission;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Point;
import android.graphics.drawable.Animatable;
import android.os.Bundle;
import android.os.SystemClock;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.telecom.CallAudioState;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.ActivityCompat;
import com.android.dialer.util.PermissionsUtil;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment.AudioRouteSelectorPresenter;
import com.android.incallui.contactgrid.ContactGridManager;
import com.android.incallui.hold.OnHoldFragment;
import com.android.incallui.incall.protocol.InCallButtonIds;
import com.android.incallui.incall.protocol.InCallButtonIdsExtension;
import com.android.incallui.incall.protocol.InCallButtonUi;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.incall.protocol.InCallButtonUiDelegateFactory;
import com.android.incallui.incall.protocol.InCallScreen;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.InCallScreenDelegateFactory;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.incall.protocol.SecondaryInfo;
import com.android.incallui.sprd.InCallUiUtils;
import com.android.incallui.video.impl.CheckableImageButton.OnCheckedChangeListener;
import com.android.incallui.video.protocol.VideoCallScreen;
import com.android.incallui.video.protocol.VideoCallScreenDelegate;
import com.android.incallui.videosurface.impl.VideoSurfaceTextureImpl;  //UNISOC:add for bug905887
import com.android.incallui.video.protocol.VideoCallScreenDelegateFactory;
import com.android.incallui.videosurface.bindings.VideoSurfaceBindings;
import com.android.incallui.videosurface.protocol.VideoSurfaceTexture;
import com.android.incallui.videotech.utils.VideoUtils;
import com.android.internal.view.menu.MenuBuilder;
import com.android.internal.view.menu.MenuPopupHelper;

/** Contains UI elements for a video call. */

public class VideoCallFragment extends Fragment
    implements InCallScreen,
        InCallButtonUi,
        VideoCallScreen,
        OnClickListener,
        OnCheckedChangeListener,
        AudioRouteSelectorPresenter,
        OnSystemUiVisibilityChangeListener {

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String ARG_CALL_ID = "call_id";

  @VisibleForTesting static final float BLUR_PREVIEW_RADIUS = 16.0f;
  @VisibleForTesting static final float BLUR_PREVIEW_SCALE_FACTOR = 1.0f;
  private static final float BLUR_REMOTE_RADIUS = 25.0f;
  private static final float BLUR_REMOTE_SCALE_FACTOR = 0.25f;
  private static final float ASPECT_RATIO_MATCH_THRESHOLD = 0.2f;

  private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
  private static final long CAMERA_PERMISSION_DIALOG_DELAY_IN_MILLIS = 2000L;
  private static final long VIDEO_OFF_VIEW_FADE_OUT_DELAY_IN_MILLIS = 2000L;

  private final ViewOutlineProvider circleOutlineProvider =
      new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
          int x = view.getWidth() / 2;
          int y = view.getHeight() / 2;
          int radius = Math.min(x, y);
          outline.setOval(x - radius, y - radius, x + radius, y + radius);
        }
      };

  private InCallScreenDelegate inCallScreenDelegate;
  private VideoCallScreenDelegate videoCallScreenDelegate;
  private InCallButtonUiDelegate inCallButtonUiDelegate;
  private View endCallButton;
  private CheckableImageButton speakerButton;
  private SpeakerButtonController speakerButtonController;
  private CheckableImageButton muteButton;
  private CheckableImageButton cameraOffButton;
  private ImageButton swapCameraButton;
  private ImageButton mOverflowButton;
  private ImageButton mChangeVideoTypeButton;
  private View switchOnHoldButton;
  private View onHoldContainer;
  private SwitchOnHoldCallController switchOnHoldCallController;
  private TextView remoteVideoOff;
  private ImageView remoteOffBlurredImageView;
  private View mutePreviewOverlay;
  private View previewOffOverlay;
  private ImageView previewOffBlurredImageView;
  private View controls;
  private View controlsContainer;
  private TextureView previewTextureView;
  private TextureView remoteTextureView;
  private View greenScreenBackgroundView;
  private View fullscreenBackgroundView;
  private boolean shouldShowRemote;
  private boolean shouldShowPreview;
  private boolean isInFullscreenMode;
  private boolean isInGreenScreenMode;
  private boolean hasInitializedScreenModes;
  private boolean isRemotelyHeld;
  /* UNISOC: add for bug905887 @{ */
  private int smallTextureViewIndex = 0;
  private int smallBlurredImageIndex = 0;
  private int bigTextureViewIndex = 0;
  private int bigBlurredImageIndex = 0;
  private ContactGridManager contactGridManager;
  private SecondaryInfo savedSecondaryInfo;
  /* SPRD: Add video call option menu@{ */
  private PopupMenu mOverflowPopup;
  // UNISOC: Add for bug895442
  private MenuPopupHelper mMenuHelper;
  //The button is currently visible in the UI
  private static final int BUTTON_VISIBLE = 1;
  // The button is hidden in the UI
  private static final int BUTTON_HIDDEN = 2;
  private SparseIntArray mButtonVisibilityMap = new SparseIntArray(InCallButtonIds.BUTTON_COUNT);
  /*@}*/
  private boolean isRecording = false;//SPRD:add for bug711670 whether call recording started
  private View switchControls;//UNISOC:add for bug900290
  private final Runnable cameraPermissionDialogRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (videoCallScreenDelegate.shouldShowCameraPermissionToast()) {
            LogUtil.i("VideoCallFragment.cameraPermissionDialogRunnable", "showing dialog");
            checkCameraPermission();
          }
        }
      };

  public static VideoCallFragment newInstance(String callId) {
    Bundle bundle = new Bundle();
    bundle.putString(ARG_CALL_ID, Assert.isNotNull(callId));

    VideoCallFragment instance = new VideoCallFragment();
    instance.setArguments(bundle);
    return instance;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    LogUtil.i("VideoCallFragment.onCreate", null);

    inCallButtonUiDelegate =
        FragmentUtils.getParent(this, InCallButtonUiDelegateFactory.class)
            .newInCallButtonUiDelegate();
    if (savedInstanceState != null) {
      inCallButtonUiDelegate.onRestoreInstanceState(savedInstanceState);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        LogUtil.i("VideoCallFragment.onRequestPermissionsResult", "Camera permission granted.");
        videoCallScreenDelegate.onCameraPermissionGranted();
      } else {
        LogUtil.i("VideoCallFragment.onRequestPermissionsResult", "Camera permission denied.");
      }
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
    LogUtil.i("VideoCallFragment.onCreateView", null);

    View view =
        layoutInflater.inflate(
            isLandscape() ? R.layout.frag_videocall_land : R.layout.frag_videocall,
            viewGroup,
            false);
    contactGridManager =
        new ContactGridManager(view, null /* no avatar */, 0, false /* showAnonymousAvatar */);

    controls = view.findViewById(R.id.videocall_video_controls);
    controls.setVisibility(
        ActivityCompat.isInMultiWindowMode(getActivity()) ? View.GONE : View.VISIBLE);
    controlsContainer = view.findViewById(R.id.videocall_video_controls_container);
    speakerButton = (CheckableImageButton) view.findViewById(R.id.videocall_speaker_button);
    muteButton = (CheckableImageButton) view.findViewById(R.id.videocall_mute_button);
    muteButton.setOnCheckedChangeListener(this);
    mutePreviewOverlay = view.findViewById(R.id.videocall_video_preview_mute_overlay);
    cameraOffButton = (CheckableImageButton) view.findViewById(R.id.videocall_mute_video);
    cameraOffButton.setOnCheckedChangeListener(this);
    previewOffOverlay = view.findViewById(R.id.videocall_video_preview_off_overlay);
    previewOffBlurredImageView =
        (ImageView) view.findViewById(R.id.videocall_preview_off_blurred_image_view);
    swapCameraButton = (ImageButton) view.findViewById(R.id.videocall_switch_video);
    swapCameraButton.setOnClickListener(this);
    //UNISOC:add for bug900290
    switchControls = view.findViewById(R.id.videocall_switch_controls);
    switchControls.setVisibility(ActivityCompat.isInMultiWindowMode(getActivity()) ? View.GONE : View.VISIBLE);
    /* SPRD: Add video call option menu@{ */
    mOverflowButton = (ImageButton) view.findViewById(R.id.videocall_overflow_button);
    mOverflowButton.setOnClickListener(this);
    /*@}*/
    /* SPRD: Add for change video type feature@{ */
    mChangeVideoTypeButton = (ImageButton) view.findViewById(R.id.videocall_change_type);
    mChangeVideoTypeButton.setOnClickListener(
      new OnClickListener() {
        @Override
        public void onClick(View v) {
          inCallButtonUiDelegate.changeVideoTypeClicked();
        }
      });
    /*@}*/
    switchOnHoldButton = view.findViewById(R.id.videocall_switch_on_hold);
    onHoldContainer = view.findViewById(R.id.videocall_on_hold_banner);
    remoteVideoOff = (TextView) view.findViewById(R.id.videocall_remote_video_off);
    remoteVideoOff.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    remoteOffBlurredImageView =
        (ImageView) view.findViewById(R.id.videocall_remote_off_blurred_image_view);
    endCallButton = view.findViewById(R.id.videocall_end_call);
    endCallButton.setOnClickListener(this);
    previewTextureView = (TextureView) view.findViewById(R.id.videocall_video_preview);
    previewTextureView.setClipToOutline(true);
    previewOffOverlay.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            checkCameraPermission();
          }
        });
    remoteTextureView = (TextureView) view.findViewById(R.id.videocall_video_remote);
    greenScreenBackgroundView = view.findViewById(R.id.videocall_green_screen_background);
    fullscreenBackgroundView = view.findViewById(R.id.videocall_fullscreen_background);
    /* UNISOC: add for bug905887 @{ */
    smallTextureViewIndex = ((ViewGroup)previewTextureView.getParent()).indexOfChild(previewTextureView);
    smallBlurredImageIndex = ((ViewGroup)previewOffBlurredImageView.getParent()).indexOfChild(previewOffBlurredImageView);
    bigTextureViewIndex = ((ViewGroup)remoteTextureView.getParent()).indexOfChild(remoteTextureView);
    bigBlurredImageIndex = ((ViewGroup)remoteOffBlurredImageView.getParent()).indexOfChild(remoteOffBlurredImageView);

    remoteTextureView.addOnLayoutChangeListener(
        new OnLayoutChangeListener() {
          @Override
          public void onLayoutChange(
              View v,
              int left,
              int top,
              int right,
              int bottom,
              int oldLeft,
              int oldTop,
              int oldRight,
              int oldBottom) {
            LogUtil.i("VideoCallFragment.onLayoutChange", "remoteTextureView layout changed");
            updateRemoteVideoScaling();
            updateRemoteOffView();
          }
        });

    previewTextureView.addOnLayoutChangeListener(
        new OnLayoutChangeListener() {
          @Override
          public void onLayoutChange(
              View v,
              int left,
              int top,
              int right,
              int bottom,
              int oldLeft,
              int oldTop,
              int oldRight,
              int oldBottom) {
            LogUtil.i("VideoCallFragment.onLayoutChange", "previewTextureView layout changed");
            updatePreviewVideoScaling();
            updatePreviewOffView();
          }
        });
    return view;
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle bundle) {
    super.onViewCreated(view, bundle);
    LogUtil.i("VideoCallFragment.onViewCreated", null);

    inCallScreenDelegate =
        FragmentUtils.getParentUnsafe(this, InCallScreenDelegateFactory.class)
            .newInCallScreenDelegate();
    videoCallScreenDelegate =
        FragmentUtils.getParentUnsafe(this, VideoCallScreenDelegateFactory.class)
            .newVideoCallScreenDelegate(this);

    speakerButtonController =
        new SpeakerButtonController(speakerButton, inCallButtonUiDelegate, videoCallScreenDelegate);
    switchOnHoldCallController =
        new SwitchOnHoldCallController(
            switchOnHoldButton, onHoldContainer, inCallScreenDelegate, videoCallScreenDelegate);

    videoCallScreenDelegate.initVideoCallScreenDelegate(getContext(), this);
    /* UNISOC: add for bug905887 @{ */
    videoCallScreenDelegate.getLocalVideoSurfaceTexture().attachToTextureView(previewTextureView); //modify for bug959418
    videoCallScreenDelegate.getLocalVideoSurfaceTexture().attachToImageView(previewOffBlurredImageView);
    videoCallScreenDelegate.getRemoteVideoSurfaceTexture().attachToTextureView(remoteTextureView); //modify for bug959418
    videoCallScreenDelegate.getRemoteVideoSurfaceTexture().attachToImageView(remoteOffBlurredImageView);

    inCallScreenDelegate.onInCallScreenDelegateInit(this);
    inCallScreenDelegate.onInCallScreenReady();
    inCallButtonUiDelegate.onInCallButtonUiReady(this);

    view.setOnSystemUiVisibilityChangeListener(this);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    inCallButtonUiDelegate.onSaveInstanceState(outState);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    LogUtil.i("VideoCallFragment.onDestroyView", null);
    inCallButtonUiDelegate.onInCallButtonUiUnready();
    inCallScreenDelegate.onInCallScreenUnready();
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if (savedSecondaryInfo != null) {
      setSecondary(savedSecondaryInfo);
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    LogUtil.i("VideoCallFragment.onStart", null);
    onVideoScreenStart();
    onVideoCallIsFront(); // UNISOC:add for bug 903996,904816
  }

  @Override
  public void onVideoScreenStart() {
    inCallButtonUiDelegate.refreshMuteState();
    videoCallScreenDelegate.onVideoCallScreenUiReady();
    getView().postDelayed(cameraPermissionDialogRunnable, CAMERA_PERMISSION_DIALOG_DELAY_IN_MILLIS);
  }

  @Override
  public void onResume() {
    super.onResume();
    LogUtil.i("VideoCallFragment.onResume", null);
    inCallScreenDelegate.onInCallScreenResumed();
    /* UNISOC: add for bug905887 @{ */
    if(!videoCallScreenDelegate.getLocalVideoSurfaceTexture().getSmallSurface()) {
      LogUtil.i("VideoCallFragment.onResume", "PreviewTexture is big");
      localSurfaceClickForChange();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    LogUtil.i("VideoCallFragment.onPause", null);
    inCallScreenDelegate.onInCallScreenPaused();
  }

  @Override
  public void onStop() {
    super.onStop();
    LogUtil.i("VideoCallFragment.onStop", null);
    onVideoScreenStop();
    onVideoCallIsBack();// UNISOC:add for bug 903996,904816
  }

  @Override
  public void onVideoScreenStop() {
    getView().removeCallbacks(cameraPermissionDialogRunnable);
    videoCallScreenDelegate.onVideoCallScreenUiUnready();
    //UNISOC:add for bug895978, 895442
    if (mMenuHelper != null && mOverflowPopup != null && mOverflowPopup.getMenu() != null
            && mOverflowPopup.getMenu().hasVisibleItems()) {
      mMenuHelper.dismiss();
    }
  }

  private void exitFullscreenMode() {
    LogUtil.i("VideoCallFragment.exitFullscreenMode", null);

    if (!getView().isAttachedToWindow()) {
      LogUtil.i("VideoCallFragment.exitFullscreenMode", "not attached");
      return;
    }

    showSystemUI();

    LinearOutSlowInInterpolator linearOutSlowInInterpolator = new LinearOutSlowInInterpolator();

    // Animate the controls to the shown state.
    controls
        .animate()
        .translationX(0)
        .translationY(0)
        .setInterpolator(linearOutSlowInInterpolator)
        .alpha(1)
        .withStartAction(
          new Runnable() {//SPRD:add for bug895397 
            @Override
            public void run() {
              controls.setVisibility(ActivityCompat.isInMultiWindowMode(getActivity()) ? View.GONE : View.VISIBLE);
            }
          })
        .start();

    // Animate onHold to the shown state.
    switchOnHoldButton
        .animate()
        .translationX(0)
        .translationY(0)
        .setInterpolator(linearOutSlowInInterpolator)
        .alpha(1)
        .withStartAction(
            new Runnable() {
              @Override
              public void run() {
           //     switchOnHoldCallController.setOnScreen();  //modify for bug933610
              }
            });

    View contactGridView = contactGridManager.getContainerView();
    // Animate contact grid to the shown state.
    contactGridView
        .animate()
        .translationX(0)
        .translationY(0)
        .setInterpolator(linearOutSlowInInterpolator)
        .alpha(1)
        .withStartAction(
            new Runnable() {
              @Override
              public void run() {
                contactGridManager.show();
              }
            });

    endCallButton
        .animate()
        .translationX(0)
        .translationY(0)
        .setInterpolator(linearOutSlowInInterpolator)
        .alpha(1)
        .withStartAction(
            new Runnable() {
              @Override
              public void run() {
                endCallButton.setVisibility(View.VISIBLE);
              }
            })
        .start();

    // Animate all the preview controls up to make room for the navigation bar.
    // In green screen mode we don't need this because the preview takes up the whole screen and has
    // a fixed position.
    if (!isInGreenScreenMode) {
      Point previewOffsetStartShown = getPreviewOffsetStartShown();
      for (View view : getAllPreviewRelatedViews()) {
        // Animate up with the preview offset above the navigation bar.
        view.animate()
            .translationX(previewOffsetStartShown.x)
            .translationY(previewOffsetStartShown.y)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
      }
    }

    updateOverlayBackground();
  }

  private void showSystemUI() {
    View view = getView();
    if (view != null) {
      // Code is more expressive with all flags present, even though some may be combined
      // noinspection PointlessBitwiseExpression
      view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
  }

  /** Set view flags to hide the system UI. System UI will return on any touch event */
  private void hideSystemUI() {
    View view = getView();
    if (view != null) {
      view.setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
  }

  private Point getControlsOffsetEndHidden(View controls) {
    if (isLandscape()) {
      return new Point(0, getOffsetBottom(controls));
    } else {
      return new Point(getOffsetStart(controls), 0);
    }
  }

  private Point getSwitchOnHoldOffsetEndHidden(View swapCallButton) {
    if (isLandscape()) {
      return new Point(0, getOffsetTop(swapCallButton));
    } else {
      return new Point(getOffsetEnd(swapCallButton), 0);
    }
  }

  private Point getContactGridOffsetEndHidden(View view) {
    return new Point(0, getOffsetTop(view));
  }

  private Point getEndCallOffsetEndHidden(View endCallButton) {
    if (isLandscape()) {
      return new Point(getOffsetEnd(endCallButton), 0);
    } else {
      return new Point(0, ((MarginLayoutParams) endCallButton.getLayoutParams()).bottomMargin);
    }
  }

  private Point getPreviewOffsetStartShown() {
    // No insets in multiwindow mode, and rootWindowInsets will get the display's insets.
    if (ActivityCompat.isInMultiWindowMode(getActivity())) {
      return new Point();
    }
    if (isLandscape()) {
      int stableInsetEnd =
          getView().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
              ? getView().getRootWindowInsets().getStableInsetLeft()
              : -getView().getRootWindowInsets().getStableInsetRight();
      return new Point(stableInsetEnd, 0);
    } else {
      return new Point(0, -getView().getRootWindowInsets().getStableInsetBottom());
    }
  }

  private View[] getAllPreviewRelatedViews() {
    return new View[] {
      previewTextureView, previewOffOverlay, previewOffBlurredImageView, mutePreviewOverlay,
    };
  }

  private int getOffsetTop(View view) {
    return -(view.getHeight() + ((MarginLayoutParams) view.getLayoutParams()).topMargin);
  }

  private int getOffsetBottom(View view) {
    return view.getHeight() + ((MarginLayoutParams) view.getLayoutParams()).bottomMargin;
  }

  private int getOffsetStart(View view) {
    int offset = view.getWidth() + ((MarginLayoutParams) view.getLayoutParams()).getMarginStart();
    if (view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
      offset = -offset;
    }
    return -offset;
  }

  private int getOffsetEnd(View view) {
    int offset = view.getWidth() + ((MarginLayoutParams) view.getLayoutParams()).getMarginEnd();
    if (view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
      offset = -offset;
    }
    return offset;
  }

  private void enterFullscreenMode() {
    LogUtil.i("VideoCallFragment.enterFullscreenMode", null);

    hideSystemUI();

    Interpolator fastOutLinearInInterpolator = new FastOutLinearInInterpolator();

    // Animate controls to the hidden state.
    Point offset = getControlsOffsetEndHidden(controls);
    controls
        .animate()
        .translationX(offset.x)
        .translationY(offset.y)
        .setInterpolator(fastOutLinearInInterpolator)
        .alpha(0)
        .start();

    // Animate onHold to the hidden state.
    offset = getSwitchOnHoldOffsetEndHidden(switchOnHoldButton);
    switchOnHoldButton
        .animate()
        .translationX(offset.x)
        .translationY(offset.y)
        .setInterpolator(fastOutLinearInInterpolator)
        .alpha(0);

    View contactGridView = contactGridManager.getContainerView();
    // Animate contact grid to the hidden state.
    offset = getContactGridOffsetEndHidden(contactGridView);
    contactGridView
        .animate()
        .translationX(offset.x)
        .translationY(offset.y)
        .setInterpolator(fastOutLinearInInterpolator)
        .alpha(0);

    offset = getEndCallOffsetEndHidden(endCallButton);
    // Use a fast out interpolator to quickly fade out the button. This is important because the
    // button can't draw under the navigation bar which means that it'll look weird if it just
    // abruptly disappears when it reaches the edge of the naivgation bar.
    endCallButton
        .animate()
        .translationX(offset.x)
        .translationY(offset.y)
        .setInterpolator(fastOutLinearInInterpolator)
        .alpha(0)
        .withEndAction(
            new Runnable() {
              @Override
              public void run() {
                endCallButton.setVisibility(View.INVISIBLE);
              }
            })
        .setInterpolator(new FastOutLinearInInterpolator())
        .start();

    // Animate all the preview controls down now that the navigation bar is hidden.
    // In green screen mode we don't need this because the preview takes up the whole screen and has
    // a fixed position.
    if (!isInGreenScreenMode) {
      for (View view : getAllPreviewRelatedViews()) {
        // Animate down with the navigation bar hidden.
        view.animate()
            .translationX(0)
            .translationY(0)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
      }
    }
    updateOverlayBackground();

    //UNISOC: Add for bug895442
    if (mMenuHelper != null && mOverflowPopup != null && mOverflowPopup.getMenu() != null
            && mOverflowPopup.getMenu().hasVisibleItems()) {
      mMenuHelper.dismiss();
    }
  }

  @Override
  public void onClick(View v) {
    if (v == endCallButton) {
      LogUtil.i("VideoCallFragment.onClick", "end call button clicked");
      inCallButtonUiDelegate.onEndCallClicked();
      videoCallScreenDelegate.resetAutoFullscreenTimer();
    } else if (v == swapCameraButton) {
      if (swapCameraButton.getDrawable() instanceof Animatable) {
        ((Animatable) swapCameraButton.getDrawable()).start();
      }
      inCallButtonUiDelegate.toggleCameraClicked();
      videoCallScreenDelegate.resetAutoFullscreenTimer();
    } else if (v == mOverflowButton){  /* SPRD: Add video call option menu, bug895442@ */
      if (v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
        if (mOverflowPopup != null && mMenuHelper != null) {
          mMenuHelper.show(-5 * mOverflowButton.getWidth() / 2, -mOverflowButton.getHeight() * 2 / 3);
        }
      } else {
        if (mOverflowPopup != null && mMenuHelper != null) {
          mMenuHelper.show(3 * mOverflowButton.getWidth() / 2, -mOverflowButton.getHeight() * 2 / 3);
        }
      }
    }/*@}*/
  }

  @Override
  public void onCheckedChanged(CheckableImageButton button, boolean isChecked) {
    if (button == cameraOffButton) {
      if (!isChecked && !VideoUtils.hasCameraPermissionAndShownPrivacyToast(getContext())) {
        LogUtil.i("VideoCallFragment.onCheckedChanged", "show camera permission dialog");
        checkCameraPermission();
      } else {
        inCallButtonUiDelegate.pauseVideoClicked(isChecked);
        videoCallScreenDelegate.resetAutoFullscreenTimer();
      }
    } else if (button == muteButton) {
      inCallButtonUiDelegate.muteClicked(isChecked, true /* clickedByUser */);
      videoCallScreenDelegate.resetAutoFullscreenTimer();
    }
  }

  @Override
  public void showVideoViews(
      boolean shouldShowPreview, boolean shouldShowRemote, boolean isRemotelyHeld) {
    LogUtil.i(
        "VideoCallFragment.showVideoViews",
        "showPreview: %b, shouldShowRemote: %b",
        shouldShowPreview,
        shouldShowRemote);

    // UNISOC: modify for bug916269
    if (this.shouldShowRemote != shouldShowRemote || this.isRemotelyHeld != isRemotelyHeld) {
      this.isRemotelyHeld = isRemotelyHeld;
      this.shouldShowRemote = shouldShowRemote;
      updateRemoteOffView();
    }
    if (this.shouldShowPreview != shouldShowPreview) {
      this.shouldShowPreview = shouldShowPreview;
      updatePreviewOffView();
    }
  }

  @Override
  public void onLocalVideoDimensionsChanged() {
    LogUtil.i("VideoCallFragment.onLocalVideoDimensionsChanged", null);
    updatePreviewVideoScaling();
  }

  @Override
  public void onLocalVideoOrientationChanged() {
    LogUtil.i("VideoCallFragment.onLocalVideoOrientationChanged", null);
    updatePreviewVideoScaling();
  }

  /** Called when the remote video's dimensions change. */
  @Override
  public void onRemoteVideoDimensionsChanged() {
    LogUtil.i("VideoCallFragment.onRemoteVideoDimensionsChanged", null);
    updateRemoteVideoScaling();
  }

  @Override
  public void updateFullscreenAndGreenScreenMode(
      boolean shouldShowFullscreen, boolean shouldShowGreenScreen) {
    LogUtil.i(
        "VideoCallFragment.updateFullscreenAndGreenScreenMode",
        "shouldShowFullscreen: %b, shouldShowGreenScreen: %b",
        shouldShowFullscreen,
        shouldShowGreenScreen);

    if (getActivity() == null) {
      LogUtil.i("VideoCallFragment.updateFullscreenAndGreenScreenMode", "not attached to activity");
      return;
    }

    // Check if anything is actually going to change. The first time this function is called we
    // force a change by checking the hasInitializedScreenModes flag. We also force both fullscreen
    // and green screen modes to update even if only one has changed. That's because they both
    // depend on each other.
    if (hasInitializedScreenModes
        && shouldShowGreenScreen == isInGreenScreenMode
        && shouldShowFullscreen == isInFullscreenMode) {
      LogUtil.i(
          "VideoCallFragment.updateFullscreenAndGreenScreenMode", "no change to screen modes");
      return;
    }
    hasInitializedScreenModes = true;
    isInGreenScreenMode = shouldShowGreenScreen;
    isInFullscreenMode = shouldShowFullscreen;

    if (getView().isAttachedToWindow() && !ActivityCompat.isInMultiWindowMode(getActivity())) {
      controlsContainer.onApplyWindowInsets(getView().getRootWindowInsets());
    }
    //UNISOC:add for bug960855
    if (shouldShowGreenScreen && !InCallUiUtils.isSupportRingTone(getContext()) && videoCallScreenDelegate.getLocalVideoSurfaceTexture().getSmallSurface() ) {
      enterGreenScreenMode();
    } else if(videoCallScreenDelegate.getLocalVideoSurfaceTexture().getSmallSurface()){
      exitGreenScreenMode();//add for bug905887
    // UNISOC: add for bug960861 965827
    } else if (!shouldShowGreenScreen) {
      if (remoteTextureView != null) {
        remoteTextureView.setVisibility(View.VISIBLE);
      }
    }
    if (shouldShowFullscreen) {
      enterFullscreenMode();
    } else {
      exitFullscreenMode();
    }

    OnHoldFragment onHoldFragment =
        ((OnHoldFragment)
            getChildFragmentManager().findFragmentById(R.id.videocall_on_hold_banner));
    if (onHoldFragment != null) {
      onHoldFragment.setPadTopInset(!isInFullscreenMode);
    }
  }

  @Override
  public Fragment getVideoCallScreenFragment() {
    return this;
  }

  @Override
  @NonNull
  public String getCallId() {
    return Assert.isNotNull(getArguments().getString(ARG_CALL_ID));
  }

  @Override
  public void showButton(@InCallButtonIds int buttonId, boolean show) {
    LogUtil.i(
        "VideoCallFragment.showButton",
        " buttonId:"+InCallButtonIdsExtension.toString(buttonId) + " show:"+show);
    if (buttonId == InCallButtonIds.BUTTON_AUDIO) {
      speakerButtonController.setEnabled(show);
    } else if (buttonId == InCallButtonIds.BUTTON_MUTE) {
      muteButton.setEnabled(show);
    } else if (buttonId == InCallButtonIds.BUTTON_PAUSE_VIDEO) {
      cameraOffButton.setVisibility(show ? View.VISIBLE : View.GONE);
    } else if (buttonId == InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY) {
      switchOnHoldCallController.setVisible(show);
    } else if (buttonId == InCallButtonIds.BUTTON_SWITCH_CAMERA) {
      swapCameraButton.setEnabled(show);
       /* SPRD: Add video call option menu@{ */
    } else if (buttonId == InCallButtonIds.BUTTON_ADD_CALL
            || buttonId == InCallButtonIds.BUTTON_MERGE
            || buttonId == InCallButtonIds.BUTTON_HOLD
            || buttonId == InCallButtonIds.BUTTON_SWAP
            || buttonId == InCallButtonIds.BUTTON_DOWNGRADE_TO_AUDIO
            || buttonId == InCallButtonIds.BUTTON_RECORD
            || buttonId == InCallButtonIds.BUTTON_DIALPAD){
      mButtonVisibilityMap.put(buttonId, show ? BUTTON_VISIBLE : BUTTON_HIDDEN);
    }
    /*@}*/
    else if (buttonId == InCallButtonIds.BUTTON_CHANGE_VIDEO_TYPE) { //UNISOC:add for bug895690
           mChangeVideoTypeButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }
  }

  @Override
  public void enableButton(@InCallButtonIds int buttonId, boolean enable) {
    LogUtil.i(
        "VideoCallFragment.setEnabled",
        " buttonId:" + InCallButtonIdsExtension.toString(buttonId) + " enable:" + enable);
    if (buttonId == InCallButtonIds.BUTTON_AUDIO) {
      speakerButtonController.setEnabled(enable);
    } else if (buttonId == InCallButtonIds.BUTTON_MUTE) {
      muteButton.setEnabled(enable);
    } else if (buttonId == InCallButtonIds.BUTTON_PAUSE_VIDEO) {
      cameraOffButton.setEnabled(enable);
    } else if (buttonId == InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY) {
      switchOnHoldCallController.setEnabled(enable);
      /* SPRD: Add video call option menu@{ */
    } else if (buttonId == InCallButtonIds.BUTTON_ADD_CALL
            || buttonId == InCallButtonIds.BUTTON_MERGE
            || buttonId == InCallButtonIds.BUTTON_HOLD
            || buttonId == InCallButtonIds.BUTTON_SWAP
            || buttonId == InCallButtonIds.BUTTON_DOWNGRADE_TO_AUDIO
            || buttonId == InCallButtonIds.BUTTON_RECORD){  //add for bug930344
      mButtonVisibilityMap.put(buttonId, enable ? BUTTON_VISIBLE : BUTTON_HIDDEN);
    }
    /*@}*/
  }

  @Override
  public void setEnabled(boolean enabled) {
    LogUtil.i("VideoCallFragment.setEnabled", "enabled: " + enabled);
    speakerButtonController.setEnabled(enabled);
    speakerButton.setEnabled(enabled);//add for bug907768
    muteButton.setEnabled(enabled);
    cameraOffButton.setEnabled(enabled);
    switchOnHoldCallController.setEnabled(enabled);
    //add for bug907768
    swapCameraButton.setEnabled(enabled);
    mOverflowButton.setEnabled(enabled);

    /* SPRD: Add for change video type feature@{ */
    mChangeVideoTypeButton.setEnabled(enabled);
    /*@}*/
  }

  @Override
  public void setHold(boolean value) {
    LogUtil.i("VideoCallFragment.setHold", "value: " + value);
     /* SPRD: Add video call option menu@{ */
    if(mOverflowPopup == null){
        LogUtil.i("VideoCallFragment.setHold", "mOverflowPopup is null ");
        return;
    }
    MenuItem menuItem = mOverflowPopup.getMenu().findItem(R.id.hold_call_menu);
    if ( menuItem != null && menuItem.isChecked()!= value) {
        menuItem.setChecked(value);
        menuItem.setTitle(getContext().getString(
                value ? R.string.incall_content_description_hold
                        : R.string.incall_content_description_unhold));
    }
    /*@}*/
  }

  @Override
  public void setCameraSwitched(boolean isBackFacingCamera) {
    LogUtil.i("VideoCallFragment.setCameraSwitched", "isBackFacingCamera: " + isBackFacingCamera);
  }

  @Override
  public void setVideoPaused(boolean isPaused) {
    LogUtil.i("VideoCallFragment.setVideoPaused", "isPaused: " + isPaused);
    cameraOffButton.setChecked(isPaused);
  }

  @Override
  public void setAudioState(CallAudioState audioState) {
    LogUtil.i("VideoCallFragment.setAudioState", "audioState: " + audioState);
    speakerButtonController.setAudioState(audioState);
    muteButton.setChecked(audioState.isMuted());
    updateMutePreviewOverlayVisibility();
  }

  @Override
  public void updateButtonStates() {
    LogUtil.i("VideoCallFragment.updateButtonState", null);
    speakerButtonController.updateButtonState();
    switchOnHoldCallController.updateButtonState();

    /* SPRD: Add video call option menu@{ */
    /* SPRD:modify for bug608545,895442 @ { */
    if (mMenuHelper != null && mOverflowPopup != null && mOverflowPopup.getMenu() != null
            && mOverflowPopup.getMenu().hasVisibleItems()) {
      mMenuHelper.dismiss();
    }
    /* @} */
    mOverflowPopup = new PopupMenu(getActivity(), mOverflowButton);
    mOverflowPopup.getMenuInflater().inflate(R.menu.videocall_option_menu, mOverflowPopup.getMenu());
    //UNISOC:Add for bug895442
    mMenuHelper = new MenuPopupHelper(getActivity(), (MenuBuilder) mOverflowPopup.getMenu(), mOverflowButton);
    Menu menu = mOverflowPopup.getMenu();
    int count = menu.size();
    for (int i = 0; i < count; i++) {
        MenuItem item = menu.getItem(i);
        boolean visible = false;
        switch (item.getItemId()) {
            case R.id.add_call_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_ADD_CALL) == BUTTON_VISIBLE ? true : false;
                break;
            case R.id.merge_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_MERGE) == BUTTON_VISIBLE ? true : false;
                break;
            case R.id.hold_call_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_HOLD) == BUTTON_VISIBLE ? true : false;
                break;
            case R.id.swap_call_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_SWAP) == BUTTON_VISIBLE ? true : false;
                break;
            case R.id.changeto_audio_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_DOWNGRADE_TO_AUDIO) == BUTTON_VISIBLE ? true : false;
                break;
            case R.id.call_record_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_RECORD) == BUTTON_VISIBLE ? true : false;
                break;
            case R.id.dialpad_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_DIALPAD) == BUTTON_VISIBLE ? true : false;
                break;
                //add for bug915634
            case R.id.manager_conference_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_MANAGE_VIDEO_CONFERENCE) == BUTTON_VISIBLE ? true : false;
                break;
        }
        item.setVisible(visible);
    }
    mOverflowPopup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            selectMenuItem(item);
            return true;
        }
    });
    mOverflowButton.setVisibility((mOverflowPopup != null && mOverflowPopup.getMenu() != null && mOverflowPopup.getMenu().hasVisibleItems()) ? View.VISIBLE : View.GONE);
    /*@}*/
  }

  @Override
  public void updateInCallButtonUiColors(@ColorInt int color) {}

  @Override
  public Fragment getInCallButtonUiFragment() {
    return this;
  }

  @Override
  public void showAudioRouteSelector() {
    LogUtil.i("VideoCallFragment.showAudioRouteSelector", null);
    AudioRouteSelectorDialogFragment.newInstance(inCallButtonUiDelegate.getCurrentAudioState())
        .show(getChildFragmentManager(), null);
  }

  @Override
  public void onAudioRouteSelected(int audioRoute) {
    LogUtil.i("VideoCallFragment.onAudioRouteSelected", "audioRoute: " + audioRoute);
    inCallButtonUiDelegate.setAudioRoute(audioRoute);
  }

  @Override
  public void onAudioRouteSelectorDismiss() {}

  @Override
  public void setPrimary(@NonNull PrimaryInfo primaryInfo) {
    LogUtil.i("VideoCallFragment.setPrimary", primaryInfo.toString());
    contactGridManager.setPrimary(primaryInfo);
  }

  @Override
  public void setSecondary(@NonNull SecondaryInfo secondaryInfo) {
    LogUtil.i("VideoCallFragment.setSecondary", secondaryInfo.toString());
    if (!isAdded()) {
      savedSecondaryInfo = secondaryInfo;
      return;
    }
    savedSecondaryInfo = null;
    switchOnHoldCallController.setSecondaryInfo(secondaryInfo);
    updateButtonStates();
    FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
    Fragment oldBanner = getChildFragmentManager().findFragmentById(R.id.videocall_on_hold_banner);
    if (secondaryInfo.shouldShow()) {
      OnHoldFragment onHoldFragment = OnHoldFragment.newInstance(secondaryInfo);
      onHoldFragment.setPadTopInset(!isInFullscreenMode);
      transaction.replace(R.id.videocall_on_hold_banner, onHoldFragment);
    } else {
      if (oldBanner != null) {
        transaction.remove(oldBanner);
      }
    }
    transaction.setCustomAnimations(R.anim.abc_slide_in_top, R.anim.abc_slide_out_top);
    transaction.commitAllowingStateLoss();
  }

  @Override
  public void setCallState(@NonNull PrimaryCallState primaryCallState) {
    LogUtil.i("VideoCallFragment.setCallState", primaryCallState.toString());
    contactGridManager.setCallState(primaryCallState);
  }

  @Override
  public void setEndCallButtonEnabled(boolean enabled, boolean animate) {
    // add for bug907768
    if (endCallButton != null) {
      endCallButton.setEnabled(enabled);
    }
    LogUtil.i("VideoCallFragment.setEndCallButtonEnabled", "enabled: " + enabled);
  }

  @Override
  public void showManageConferenceCallButton(boolean visible) {
    LogUtil.i("VideoCallFragment.showManageConferenceCallButton", "visible: " + visible);
    mButtonVisibilityMap.put(InCallButtonIds.BUTTON_MANAGE_VIDEO_CONFERENCE, visible ? BUTTON_VISIBLE : BUTTON_HIDDEN);//add for bug915634
    if (visible) {
      LogUtil.i("VideoCallFragment.showManageConferenceCallButton", "if manage visible, just show it!");
      updateButtonStates();
    }
  }

  @Override
  public boolean isManageConferenceVisible() {
    LogUtil.i("VideoCallFragment.isManageConferenceVisible", null);
    return mButtonVisibilityMap.get(InCallButtonIds.BUTTON_MANAGE_VIDEO_CONFERENCE) == BUTTON_VISIBLE ? true :false;//modify for bug915634
  }

  @Override
  public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
    contactGridManager.dispatchPopulateAccessibilityEvent(event);
  }

  @Override
  public void showNoteSentToast() {
    LogUtil.i("VideoCallFragment.showNoteSentToast", null);
  }

  @Override
  public void updateInCallScreenColors() {
    LogUtil.i("VideoCallFragment.updateColors", null);
  }

  @Override
  public void onInCallScreenDialpadVisibilityChange(boolean isShowing) {
    LogUtil.i("VideoCallFragment.onInCallScreenDialpadVisibilityChange", null);
  }

  @Override
  public int getAnswerAndDialpadContainerResourceId() {
    return R.id.videocall_dialpad_container;
  }

  @Override
  public Fragment getInCallScreenFragment() {
    return this;
  }

  @Override
  public boolean isShowingLocationUi() {
    return false;
  }

  @Override
  public void showLocationUi(Fragment locationUi) {
    LogUtil.e("VideoCallFragment.showLocationUi", "Emergency video calling not supported");
    // Do nothing
  }

  private void updatePreviewVideoScaling() {
    if (previewTextureView.getWidth() == 0 || previewTextureView.getHeight() == 0) {
      LogUtil.i("VideoCallFragment.updatePreviewVideoScaling", "view layout hasn't finished yet");
      return;
    }
    VideoSurfaceTexture localVideoSurfaceTexture =
        videoCallScreenDelegate.getLocalVideoSurfaceTexture();
    Point cameraDimensions = localVideoSurfaceTexture.getSurfaceDimensions();
    if (cameraDimensions == null) {
      LogUtil.i(
          "VideoCallFragment.updatePreviewVideoScaling", "camera dimensions haven't been set");
      return;
    }
    /* UNISOC: bug900278,bug905887@{ */
    if((isInGreenScreenMode || !videoCallScreenDelegate.getLocalVideoSurfaceTexture().getSmallSurface())
            && !InCallUiUtils.isSupportRingTone(getContext())){
      if (isLandscape()) {
        VideoSurfaceBindings.scaleVideoMaintainingAspectRatioWithRot(
                previewTextureView,
                cameraDimensions.y,
                cameraDimensions.x,
                videoCallScreenDelegate.getDeviceOrientation());
      }else{
        VideoSurfaceBindings.scaleVideoMaintainingAspectRatioWithRot(
                previewTextureView,
                cameraDimensions.x,
                cameraDimensions.y,
                videoCallScreenDelegate.getDeviceOrientation());
      }
      /*@}*/
    } else {
      if (isLandscape()) {
        VideoSurfaceBindings.scaleVideoAndFillView(
                previewTextureView,
                cameraDimensions.y,
                cameraDimensions.x,
                videoCallScreenDelegate.getDeviceOrientation());
      } else {
        VideoSurfaceBindings.scaleVideoAndFillView(
                previewTextureView,
                cameraDimensions.x,
                cameraDimensions.y,
                videoCallScreenDelegate.getDeviceOrientation());
      }
    }
  }

  private void updateRemoteVideoScaling() {
    VideoSurfaceTexture remoteVideoSurfaceTexture =
        videoCallScreenDelegate.getRemoteVideoSurfaceTexture();
    Point videoSize = remoteVideoSurfaceTexture.getSourceVideoDimensions();
    if (videoSize == null) {
      LogUtil.i("VideoCallFragment.updateRemoteVideoScaling", "video size is null");
      return;
    }
    if (remoteTextureView.getWidth() == 0 || remoteTextureView.getHeight() == 0) {
      LogUtil.i("VideoCallFragment.updateRemoteVideoScaling", "view layout hasn't finished yet");
      return;
    }

    // If the video and display aspect ratio's are close then scale video to fill display
    float videoAspectRatio = ((float) videoSize.x) / videoSize.y;
    float displayAspectRatio =
        ((float) remoteTextureView.getWidth()) / remoteTextureView.getHeight();
    float delta = Math.abs(videoAspectRatio - displayAspectRatio);
    float sum = videoAspectRatio + displayAspectRatio;
    if (delta / sum < ASPECT_RATIO_MATCH_THRESHOLD) {
      if(remoteVideoSurfaceTexture.getSmallSurface()){ //add for bug975883(874448)
        VideoSurfaceBindings.scaleVideoAndFillView(remoteTextureView, videoSize.x, videoSize.y, 0);
      }else{
        LogUtil.i("VideoCallFragment.updateRemoteVideoScaling", "is not samllSurface");
        VideoSurfaceBindings.scaleVideoMaintainingAspectRatioWithRot(remoteTextureView, videoSize.x, videoSize.y, 0);
      }
    } else {
      VideoSurfaceBindings.scaleVideoMaintainingAspectRatio(
          remoteTextureView, videoSize.x, videoSize.y);
    }
  }

  private boolean isLandscape() {
    // Choose orientation based on display orientation, not window orientation
    int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
    return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
  }

  private void enterGreenScreenMode() {
    LogUtil.i("VideoCallFragment.enterGreenScreenMode", null);
    RelativeLayout.LayoutParams params =
        new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
    params.addRule(RelativeLayout.ALIGN_PARENT_START);
    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
    previewTextureView.setLayoutParams(params);
    previewTextureView.setOutlineProvider(null);
    updateOverlayBackground();
    contactGridManager.setIsMiddleRowVisible(true);
    updateMutePreviewOverlayVisibility();

    previewOffBlurredImageView.setLayoutParams(params);
    previewOffBlurredImageView.setOutlineProvider(null);
    previewOffBlurredImageView.setClipToOutline(false);
    //UNISOC:add for bug905737
    if (remoteTextureView != null) {
      remoteTextureView.setVisibility(View.GONE);
    }
  }

  private void exitGreenScreenMode() {
    LogUtil.i("VideoCallFragment.exitGreenScreenMode", null);
    Resources resources = getResources();
    RelativeLayout.LayoutParams params =
        new RelativeLayout.LayoutParams(
            (int) resources.getDimension(R.dimen.videocall_preview_width),
            (int) resources.getDimension(R.dimen.videocall_preview_height));
    params.setMargins(
        0, 0, 0, (int) resources.getDimension(R.dimen.videocall_preview_margin_bottom));
    if (isLandscape()) {
      params.addRule(RelativeLayout.ALIGN_PARENT_END);
      params.setMarginEnd((int) resources.getDimension(R.dimen.videocall_preview_margin_end));
    } else {
      params.addRule(RelativeLayout.ALIGN_PARENT_START);
      params.setMarginStart((int) resources.getDimension(R.dimen.videocall_preview_margin_start));
    }
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
    previewTextureView.setLayoutParams(params);
    previewTextureView.setOutlineProvider(circleOutlineProvider);
    updateOverlayBackground();
    contactGridManager.setIsMiddleRowVisible(isInGreenScreenMode ? true : false);//modify for bug907094
    updateMutePreviewOverlayVisibility();

    previewOffBlurredImageView.setLayoutParams(params);
    previewOffBlurredImageView.setOutlineProvider(circleOutlineProvider);
    previewOffBlurredImageView.setClipToOutline(true);
    //UNISOC:add for bug905737
    if (remoteTextureView != null) {
            remoteTextureView.setVisibility(View.VISIBLE);
    }
  }

  private void updatePreviewOffView() {
    LogUtil.enterBlock("VideoCallFragment.updatePreviewOffView");

    // Always hide the preview off and remote off views in green screen mode.
    boolean previewEnabled = isInGreenScreenMode || shouldShowPreview;
    previewOffOverlay.setVisibility(previewEnabled ? View.GONE : View.VISIBLE);
    if(videoCallScreenDelegate.isRingToneOnAudioCall()) { //UNISOC:add for bug 960205
      previewTextureView.setVisibility(shouldShowPreview ? View.VISIBLE : View.INVISIBLE);//add for bug907094
    }
    updateBlurredImageView(
        previewTextureView,
        previewOffBlurredImageView,
        shouldShowPreview,
        BLUR_PREVIEW_RADIUS,
        BLUR_PREVIEW_SCALE_FACTOR);
  }

  private void updateRemoteOffView() {
    LogUtil.enterBlock("VideoCallFragment.updateRemoteOffView");
    boolean remoteEnabled = isInGreenScreenMode || shouldShowRemote;
    boolean isResumed = remoteEnabled && !isRemotelyHeld;
    if (isResumed) {
      boolean wasRemoteVideoOff =
          TextUtils.equals(
              remoteVideoOff.getText(),
              remoteVideoOff.getResources().getString(R.string.videocall_remote_video_off));
      // The text needs to be updated and hidden after enough delay in order to be announced by
      // talkback.
      remoteVideoOff.setText(
          wasRemoteVideoOff
              ? R.string.videocall_remote_video_on
              : R.string.videocall_remotely_resumed);
      remoteVideoOff.postDelayed(
          new Runnable() {
            @Override
            public void run() {
              remoteVideoOff.setVisibility(View.GONE);
            }
          },
          VIDEO_OFF_VIEW_FADE_OUT_DELAY_IN_MILLIS);
    } else {
      remoteVideoOff.setText(
          isRemotelyHeld ? R.string.videocall_remotely_held : R.string.videocall_remote_video_off);
      /* UNISOC: add for bug905887 @{ */
      if(videoCallScreenDelegate.getLocalVideoSurfaceTexture().getSmallSurface()) {
        remoteVideoOff.setVisibility(View.VISIBLE);
      } else {
        remoteVideoOff.setVisibility(View.GONE);
      }
    }
    updateBlurredImageView(
        remoteTextureView,
        remoteOffBlurredImageView,
        shouldShowRemote,
        BLUR_REMOTE_RADIUS,
        BLUR_REMOTE_SCALE_FACTOR);
  }

  @VisibleForTesting
  void updateBlurredImageView(
      TextureView textureView,
      ImageView blurredImageView,
      boolean isVideoEnabled,
      float blurRadius,
      float scaleFactor) {
    Context context = getContext();

    if (isVideoEnabled || context == null) {
      blurredImageView.setImageBitmap(null);
      blurredImageView.setVisibility(View.GONE);
      return;
    }

    long startTimeMillis = SystemClock.elapsedRealtime();
    int width = Math.round(textureView.getWidth() * scaleFactor);
    int height = Math.round(textureView.getHeight() * scaleFactor);

    LogUtil.i("VideoCallFragment.updateBlurredImageView", "width: %d, height: %d", width, height);

    // This call takes less than 10 milliseconds.
    Bitmap bitmap = textureView.getBitmap(width, height);

    /* UNISOC:add for bug 960205 965780 @{ */
    if (textureView == previewTextureView && bitmap != null) {
      VideoSurfaceTexture localVideoSurfaceTexture =
              videoCallScreenDelegate.getLocalVideoSurfaceTexture();
      Point cameraDimensions = localVideoSurfaceTexture.getSurfaceDimensions();
      if (cameraDimensions != null && (!localVideoSurfaceTexture.getSmallSurface()) && (!isLandscape())) {
        if (height * cameraDimensions.x > width * cameraDimensions.y) {
          height = (int) (width * cameraDimensions.y / cameraDimensions.x);
        } else if (height * cameraDimensions.x < width * cameraDimensions.y) {
          width = (int) (height * cameraDimensions.x / cameraDimensions.y);
        }
        bitmap = textureView.getBitmap(width, height);
        if (!InCallUiUtils.isSupportRingTone(getContext())) {
          blurredImageView.setScaleType(ImageView.ScaleType.CENTER);
        } else {
          blurredImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
      }
    }
    /*@}*/

    if (bitmap == null) {
      // UNISOC:modify for bug 960205
      // blurredImageView.setImageBitmap(null);
      blurredImageView.setVisibility(View.VISIBLE);
      return;
    }

    // TODO(mdooley): When the view is first displayed after a rotation the bitmap is empty
    // and thus this blur has no effect.
    // This call can take 100 milliseconds.
    blur(getContext(), bitmap, blurRadius);

    // TODO(mdooley): Figure out why only have to apply the transform in landscape mode
    if (width > height) {
      bitmap =
          Bitmap.createBitmap(
              bitmap,
              0,
              0,
              bitmap.getWidth(),
              bitmap.getHeight(),
              textureView.getTransform(null),
              true);
    }

    blurredImageView.setImageBitmap(bitmap);
    blurredImageView.setVisibility(View.VISIBLE);

    LogUtil.i(
        "VideoCallFragment.updateBlurredImageView",
        "took %d millis",
        (SystemClock.elapsedRealtime() - startTimeMillis));
  }

  private void updateOverlayBackground() {
    if (isInGreenScreenMode) {
      // We want to darken the preview view to make text and buttons readable. The fullscreen
      // background is below the preview view so use the green screen background instead.
      animateSetVisibility(greenScreenBackgroundView, View.VISIBLE);
      animateSetVisibility(fullscreenBackgroundView, View.GONE);
    } else if (!isInFullscreenMode) {
      // We want to darken the remote view to make text and buttons readable. The green screen
      // background is above the preview view so it would darken the preview too. Use the fullscreen
      // background instead.
      animateSetVisibility(greenScreenBackgroundView, View.GONE);
      animateSetVisibility(fullscreenBackgroundView, View.VISIBLE);
    } else {
      animateSetVisibility(greenScreenBackgroundView, View.GONE);
      animateSetVisibility(fullscreenBackgroundView, View.GONE);
    }
  }

  private void updateMutePreviewOverlayVisibility() {
    // Normally the mute overlay shows on the bottom right of the preview bubble. In green screen
    // mode the preview is fullscreen so there's no where to anchor it.
    mutePreviewOverlay.setVisibility(
        muteButton.isChecked() && !isInGreenScreenMode ? View.VISIBLE : View.GONE);
  }

  private static void animateSetVisibility(final View view, final int visibility) {
    if (view.getVisibility() == visibility) {
      return;
    }

    int startAlpha;
    int endAlpha;
    if (visibility == View.GONE) {
      startAlpha = 1;
      endAlpha = 0;
    } else if (visibility == View.VISIBLE) {
      startAlpha = 0;
      endAlpha = 1;
    } else {
      Assert.fail();
      return;
    }

    view.setAlpha(startAlpha);
    view.setVisibility(View.VISIBLE);
    view.animate()
        .alpha(endAlpha)
        .withEndAction(
            new Runnable() {
              @Override
              public void run() {
                view.setVisibility(visibility);
              }
            })
        .start();
  }

  private static void blur(Context context, Bitmap image, float blurRadius) {
    RenderScript renderScript = RenderScript.create(context);
    ScriptIntrinsicBlur blurScript =
        ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
    Allocation allocationIn = Allocation.createFromBitmap(renderScript, image);
    Allocation allocationOut = Allocation.createFromBitmap(renderScript, image);
    blurScript.setRadius(blurRadius);
    blurScript.setInput(allocationIn);
    blurScript.forEach(allocationOut);
    allocationOut.copyTo(image);
    blurScript.destroy();
    allocationIn.destroy();
    allocationOut.destroy();
  }

  @Override
  public void onSystemUiVisibilityChange(int visibility) {
    boolean navBarVisible = (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
    videoCallScreenDelegate.onSystemUiVisibilityChange(navBarVisible);
  }

  private void checkCameraPermission() {
    // Checks if user has consent of camera permission and the permission is granted.
    // If camera permission is revoked, shows system permission dialog.
    // If camera permission is granted but user doesn't have consent of camera permission
    // (which means it's first time making video call), shows custom dialog instead. This
    // will only be shown to user once.
    if (!VideoUtils.hasCameraPermissionAndShownPrivacyToast(getContext())) {
      videoCallScreenDelegate.onCameraPermissionDialogShown();
      if (!VideoUtils.hasCameraPermission(getContext())) {
        requestPermissions(new String[] {permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
      } else {
        PermissionsUtil.showCameraPermissionToast(getContext());
        videoCallScreenDelegate.onCameraPermissionGranted();
      }
    }
  }

  /* SPRD Feature Porting: Add for call recorder feature.bug711670 @{  */
  @Override
  public void setRecord(boolean value) {
    LogUtil.i("VideoCallFragment.setRecord", "value: " + value);
    isRecording = value;
    if(mOverflowPopup == null){//SPRD:add for bug894964
      LogUtil.e(
              "VideoCallFragment", "setRecord mOverflowPopup is null return");
      return;
    }
    MenuItem menuItem =  mOverflowPopup.getMenu().findItem(R.id.call_record_menu);
    if(!value){
      menuItem.setTitle(R.string.record_menu_title);
    }else{
      menuItem.setTitle(R.string.record_menu_title_recording);
    }
  }

  @Override
  public void setRecordTime(String recordTime) {
    LogUtil.i("VideoCallFragment.setRecordTime", "recordTime: " + recordTime);
    if(mOverflowPopup == null){//SPRD:add for bug894964
      LogUtil.e(
              "VideoCallFragment", "setRecordTime mOverflowPopup is null return");
      return;
    }
    MenuItem menuItem =  mOverflowPopup.getMenu().findItem(R.id.call_record_menu);
    if(menuItem!=null && isRecording){
      String timeStr = mOverflowButton.getResources().getString(R.string.record_menu_title_recording)+" "+recordTime;
      menuItem.setTitle(timeStr);
    }
  }
  /* @} */

  /* SPRD: Add video call option menu@{ */
  protected void selectMenuItem(MenuItem item) {
      switch (item.getItemId()) {
          case R.id.add_call_menu:
              inCallButtonUiDelegate.addCallClicked();
              break;
          case R.id.merge_menu:
              inCallButtonUiDelegate.mergeClicked();
              break;
          case R.id.hold_call_menu:
              inCallButtonUiDelegate.holdClicked(!item.isChecked());
              break;
          case R.id.swap_call_menu:
              inCallButtonUiDelegate.swapClicked();
              break;
          case R.id.changeto_audio_menu:
              inCallButtonUiDelegate.changeToVoiceClicked();
              break;
          case R.id.call_record_menu:
              inCallButtonUiDelegate.recordClick(!isRecording);
              break;
          case R.id.dialpad_menu:
              inCallButtonUiDelegate.showDialpadClicked(!item.isChecked());
              break;
          case R.id.manager_conference_menu:
              inCallScreenDelegate.onManageConferenceClicked();
              break;
      }
  }
  /*@}*/

  /* SPRD: Added for video call conference @{ */
  @Override
  public void showPreviewVideoViews(boolean showPreview) {
    if (previewTextureView != null) {
      previewTextureView.setVisibility(showPreview ? View.VISIBLE : View.INVISIBLE);
    }else{
      LogUtil.i("VideoCallFragment.showPreviewVideoViews", "previewView == null ");
    }
  }
  /* @} */

  /*UNISOC: add for bug900290  @{ */
  @Override
  public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
    LogUtil.i("VideoCallFragment.onMultiWindowModeChanged", "isInMultiWindowMode:"+isInMultiWindowMode);
    super.onMultiWindowModeChanged(isInMultiWindowMode);
    controls.setVisibility(isInMultiWindowMode ? View.GONE : View.VISIBLE);
    switchControls.setVisibility(isInMultiWindowMode ? View.GONE : View.VISIBLE);
  }
  /* @} */

  /**
   * SPRD: add for DSDA, bug900867
   */
  @Override
  public void updateSecondaryCallState(int callState) {
  }
  /* UNISOC: add for bug905887 @{ */
  public void localSurfaceClickForChange() {
    videoCallScreenDelegate.getLocalVideoSurfaceTexture().changeToBigSurface();
    videoCallScreenDelegate.getRemoteVideoSurfaceTexture().changeToSmallSurface();
    updateMuteLocation();
    updateMutePreviewOverlayVisibility();
  }

  public void remoteSurfaceClickForChange() {
    videoCallScreenDelegate.getRemoteVideoSurfaceTexture().changeToBigSurface();
    videoCallScreenDelegate.getLocalVideoSurfaceTexture().changeToSmallSurface();
    updateMuteLocation();
    updateMutePreviewOverlayVisibility();
  }

  public void changeSmallSizeAndPosition(VideoSurfaceTextureImpl videoCallSurface){
    Resources resources = getResources();
    RelativeLayout.LayoutParams params =
            new RelativeLayout.LayoutParams(
                    (int) resources.getDimension(R.dimen.videocall_preview_width),
                    (int) resources.getDimension(R.dimen.videocall_preview_height));
    params.setMargins(
            0, 0, 0, (int) resources.getDimension(R.dimen.videocall_preview_margin_bottom));
    if (isLandscape()) {
      params.addRule(RelativeLayout.ALIGN_PARENT_END);
      params.setMarginEnd((int) resources.getDimension(R.dimen.videocall_preview_margin_end));
    } else {
      params.addRule(RelativeLayout.ALIGN_PARENT_START);
      params.setMarginStart((int) resources.getDimension(R.dimen.videocall_preview_margin_start));
    }
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
    videoCallSurface.getTextureView().setLayoutParams(params);
    videoCallSurface.getTextureView().setOutlineProvider(circleOutlineProvider);
    videoCallSurface.getTextureView().setClipToOutline(true);
    bringToIndex(videoCallSurface.getTextureView(),smallTextureViewIndex);
    updateOverlayBackground();
    contactGridManager.setIsMiddleRowVisible(false);

    videoCallSurface.getImageView().setLayoutParams(params);
    videoCallSurface.getImageView().setOutlineProvider(circleOutlineProvider);
    videoCallSurface.getImageView().setClipToOutline(true);
    bringToIndex(videoCallSurface.getImageView(),smallBlurredImageIndex);

    videoCallSurface.setSmallSurface(true);
  }

  public void changeBigSizeAndPosition(VideoSurfaceTextureImpl videoCallSurface) {
    RelativeLayout.LayoutParams params =
            new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
    params.addRule(RelativeLayout.ALIGN_PARENT_START);
    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
    videoCallSurface.getTextureView().setLayoutParams(params);
    videoCallSurface.getTextureView().setOutlineProvider(null);
    updateOverlayBackground();
    contactGridManager.setIsMiddleRowVisible(true);
    bringToIndex(videoCallSurface.getTextureView(),bigTextureViewIndex);

    videoCallSurface.getImageView().setLayoutParams(params);
    videoCallSurface.getImageView().setOutlineProvider(null);
    videoCallSurface.getImageView().setClipToOutline(false);
    bringToIndex(videoCallSurface.getImageView(),bigBlurredImageIndex);

    videoCallSurface.setSmallSurface(false);
  }

  private void bringToIndex(View child, int index) {
    ViewGroup mParent = (ViewGroup) child.getParent();
    if (index >= 0) {
      mParent.removeView(child);
      mParent.addView(child, index);
      mParent.requestLayout();
      mParent.invalidate();
    }
  }

  private void updateMuteLocation() {
    Resources resources = getResources();
    RelativeLayout.LayoutParams params =
            new RelativeLayout.LayoutParams(
                    (int) resources.getDimension(R.dimen.videocall__mute_image_size),
                    (int) resources.getDimension(R.dimen.videocall__mute_image_size));

    params.addRule(RelativeLayout.ALIGN_BOTTOM,getSmallTextureView().getId());
    params.addRule(RelativeLayout.ALIGN_RIGHT,getSmallTextureView().getId());
    mutePreviewOverlay.setLayoutParams(params);
  }

  private TextureView getSmallTextureView() {
    if(videoCallScreenDelegate.getLocalVideoSurfaceTexture().getSmallSurface()) {
      return previewTextureView != null ? previewTextureView: null;
    } else {
      return remoteTextureView != null ? remoteTextureView: null;
    }
  }

  /* add for bug904816 807288@{ */
  @Override
  public void onVideoCallIsFront() {
    LogUtil.i("VideoCallFragment.onVideoCallIsFront", null);
    if (videoCallScreenDelegate != null) {
      videoCallScreenDelegate.onVideoCallIsFront();
    }
  }
  @Override
  public void onVideoCallIsBack() {
    LogUtil.i("VideoCallFragment.onVideoCallIsBack", null);
    if (videoCallScreenDelegate != null) {
      videoCallScreenDelegate.onVideoCallIsBack();
    }
  }
  /* @} */
}

