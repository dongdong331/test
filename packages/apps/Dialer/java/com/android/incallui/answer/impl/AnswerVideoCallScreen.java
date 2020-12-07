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

package com.android.incallui.answer.impl;

import com.android.incallui.videosurface.impl.VideoSurfaceTextureImpl;//UNISOC:add for bug905887
import android.content.res.Configuration;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.incallui.video.protocol.VideoCallScreen;
import com.android.incallui.video.protocol.VideoCallScreenDelegate;
import com.android.incallui.video.protocol.VideoCallScreenDelegateFactory;
import com.android.incallui.videosurface.bindings.VideoSurfaceBindings;

/** Shows a video preview for an incoming call. */
public class AnswerVideoCallScreen implements VideoCallScreen {
  @NonNull private final String callId;
  @NonNull private final Fragment fragment;
  @NonNull private final TextureView textureView;
  @NonNull private final VideoCallScreenDelegate delegate;

  public AnswerVideoCallScreen(
      @NonNull String callId, @NonNull Fragment fragment, @NonNull View view) {
    this.callId = Assert.isNotNull(callId);
    this.fragment = Assert.isNotNull(fragment);

    textureView =
        Assert.isNotNull((TextureView) view.findViewById(R.id.incoming_preview_texture_view));
    View overlayView =
        Assert.isNotNull(view.findViewById(R.id.incoming_preview_texture_view_overlay));
    view.setBackgroundColor(0xff000000);
    delegate =
        FragmentUtils.getParentUnsafe(fragment, VideoCallScreenDelegateFactory.class)
            .newVideoCallScreenDelegate(this);
    delegate.initVideoCallScreenDelegate(fragment.getContext(), this);

    textureView.setVisibility(View.VISIBLE);
    overlayView.setVisibility(View.VISIBLE);

    /* UNISOC: bug900278@{ */
    ViewTreeObserver observer = textureView.getViewTreeObserver();
    observer.addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
              @Override
              public void onGlobalLayout() {
                LogUtil.i("AnswerVideoCallScreen.onGlobalLayout", null);
                updatePreviewVideoScaling();
                // Remove the listener so we don't continually re-layout.
                ViewTreeObserver observer = textureView.getViewTreeObserver();
                if (observer.isAlive()) {
                  observer.removeOnGlobalLayoutListener(this);
                }
              }
            });
    /*@}*/
  }

  @Override
  public void onVideoScreenStart() {
    LogUtil.i("AnswerVideoCallScreen.onStart", null);
    delegate.onVideoCallScreenUiReady();
    delegate.getLocalVideoSurfaceTexture().attachToTextureView(textureView);
  }

  @Override
  public void onVideoScreenStop() {
    LogUtil.i("AnswerVideoCallScreen.onStop", null);
    delegate.onVideoCallScreenUiUnready();
  }

  @Override
  public void showVideoViews(
      boolean shouldShowPreview, boolean shouldShowRemote, boolean isRemotelyHeld) {
    LogUtil.i(
        "AnswerVideoCallScreen.showVideoViews",
        "showPreview: %b, shouldShowRemote: %b",
        shouldShowPreview,
        shouldShowRemote);
  }

  @Override
  public void onLocalVideoDimensionsChanged() {
    LogUtil.i("AnswerVideoCallScreen.onLocalVideoDimensionsChanged", null);
    updatePreviewVideoScaling();
  }

  @Override
  public void onRemoteVideoDimensionsChanged() {}

  @Override
  public void onLocalVideoOrientationChanged() {
    LogUtil.i("AnswerVideoCallScreen.onLocalVideoOrientationChanged", null);
    updatePreviewVideoScaling();
  }

  @Override
  public void updateFullscreenAndGreenScreenMode(
      boolean shouldShowFullscreen, boolean shouldShowGreenScreen) {}

  @Override
  public Fragment getVideoCallScreenFragment() {
    return fragment;
  }

  @NonNull
  @Override
  public String getCallId() {
    return callId;
  }

  private void updatePreviewVideoScaling() {
    if (textureView.getWidth() == 0 || textureView.getHeight() == 0) {
      LogUtil.i(
          "AnswerVideoCallScreen.updatePreviewVideoScaling", "view layout hasn't finished yet");
      return;
    }
    Point cameraDimensions = delegate.getLocalVideoSurfaceTexture().getSurfaceDimensions();
    if (cameraDimensions == null) {
      LogUtil.i("AnswerVideoCallScreen.updatePreviewVideoScaling", "camera dimensions not set");
      return;
    }
    /* UNISOC: bug900278@{ */
    if (isLandscape()) {
      VideoSurfaceBindings.scaleVideoMaintainingAspectRatioWithRot(
          textureView, cameraDimensions.y, cameraDimensions.x, delegate.getDeviceOrientation());
    } else {
      // Landscape, so dimensions are swapped
      //noinspection SuspiciousNameCombination
      VideoSurfaceBindings.scaleVideoMaintainingAspectRatioWithRot(
          textureView, cameraDimensions.x, cameraDimensions.y, delegate.getDeviceOrientation());
    }
  }

  private boolean isLandscape() {
    return fragment.getResources().getConfiguration().orientation
        == Configuration.ORIENTATION_LANDSCAPE;
  }
  /* SPRD: Added for video call conference @{ */
  @Override
  public void showPreviewVideoViews(boolean showPreview) {}
  /* @} */
  /* SPRD: modify for bug888126 @{ */
  @Override
  public int getAnswerAndDialpadContainerResourceId() {
    return 0;
  }
  /* @} */
  /* UNISOC: add for bug905887 @{ */
  public void localSurfaceClickForChange() {}

  public void remoteSurfaceClickForChange() {}

  public void changeSmallSizeAndPosition(VideoSurfaceTextureImpl videoCallSurface) {}

  public void changeBigSizeAndPosition(VideoSurfaceTextureImpl videoCallSurface) {}

  /*add for bug904816@{ */
  @Override
  public void onVideoCallIsFront() {
    LogUtil.i("AnswerVideoCallScreen.onVideoCallIsFront", null);
    if (delegate != null) {
      delegate.onVideoCallIsFront();
    }
  }
  @Override
  public void onVideoCallIsBack() {
    LogUtil.i("AnswerVideoCallScreen.onVideoCallIsBack", null);
    if (delegate != null) {
      delegate.onVideoCallIsBack();
    }
  }
  /* @} */
}
