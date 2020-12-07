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
 * limitations under the License.
 */

package com.android.incallui.hold;
import android.content.Context;
import android.graphics.Rect;//UNISOC:add for bug907818
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.Window;//UNISOC:add for bug907818
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.ims.internal.ImsManagerEx;
import com.android.incallui.call.DialerCall;
import com.android.incallui.contactgrid.R;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.SecondaryInfo;
import com.android.incallui.sprd.InCallUiUtils;

/** Shows banner UI for background call */
public class OnHoldFragment extends Fragment {

  private static final String ARG_INFO = "info";
  private boolean padTopInset = true;
  private int topInset;
  private InCallScreenDelegate mInCallScreenDelegate; //SPRD: DSDA
  private TextView stateView; //SPRD: DSDA, bug900867
  // UNISOC: add for bug907818
  private View holdContainer;

  public static OnHoldFragment newInstance(@NonNull SecondaryInfo info) {
    OnHoldFragment fragment = new OnHoldFragment();
    Bundle args = new Bundle();
    args.putParcelable(ARG_INFO, info);
    fragment.setArguments(args);
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
    final View view = layoutInflater.inflate(R.layout.incall_on_hold_banner, viewGroup, false);

    SecondaryInfo secondaryInfo = getArguments().getParcelable(ARG_INFO);
    LogUtil.i("OnHoldFragment.onCreateView", secondaryInfo.toString());

    secondaryInfo = Assert.isNotNull(secondaryInfo);

    ((TextView) view.findViewById(R.id.hold_contact_name))
        .setText(
            secondaryInfo.nameIsNumber()
                ? PhoneNumberUtils.createTtsSpannable(
                    BidiFormatter.getInstance()
                        .unicodeWrap(secondaryInfo.name(), TextDirectionHeuristics.LTR))
                : secondaryInfo.name());

    /* SPRD: DSDA. @{*/
    TextView labelView = (TextView) view.findViewById(R.id.provider_label);
    labelView.setText(InCallUiUtils.getSlotInfoBySubId(view.getContext(), secondaryInfo.subId) + secondaryInfo.providerLabel());
    labelView.setTextColor(secondaryInfo.secondaryColor);

    TextView stateView = (TextView) view.findViewById(R.id.call_state);
    stateView.setText(getSecondaryCallState(view.getContext(), secondaryInfo.callState));
    this.stateView = stateView; //UNISOC: bug900867
    /* @} */
    ((ImageView) view.findViewById(R.id.hold_phone_icon))
        .setImageResource(
            secondaryInfo.isVideoCall()
                ? R.drawable.quantum_ic_videocam_white_18
                : R.drawable.quantum_ic_phone_paused_vd_theme_24);
    view.addOnAttachStateChangeListener(
        new OnAttachStateChangeListener() {
          @Override
          public void onViewAttachedToWindow(View v) {
            topInset = v.getRootWindowInsets().getSystemWindowInsetTop();
            applyInset();
          }

          @Override
          public void onViewDetachedFromWindow(View v) {}
        });

    /* SPRD: DSDA. @{*/
    if (ImsManagerEx.isDualVoLTERegistered()) {
      view.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (mInCallScreenDelegate != null) {
            mInCallScreenDelegate.swapSecondaryToForeground();
          }
        }
      });
     labelView.setVisibility(View.VISIBLE);
      stateView.setText(getSecondaryCallState(view.getContext(), secondaryInfo.callState));
    } else {
     labelView.setVisibility(View.GONE);
     stateView.setText(view.getContext().getString(R.string.incall_on_hold));

    }
    /* @} */
    // UNISOC: add for bug907818
    holdContainer = view.findViewById(R.id.hold_container);
    return view;
  }

  public void setPadTopInset(boolean padTopInset) {
    this.padTopInset = padTopInset;
    applyInset();
  }

  private void applyInset() {
    if (getView() == null) {
      return;
    }

    int newPadding = padTopInset ? topInset : 0;
    if (newPadding != getView().getPaddingTop()) {
      TransitionManager.beginDelayedTransition(((ViewGroup) getView().getParent()));
      getView().setPadding(0, newPadding, 0, 0);
    }
  }

  /**
   * SPRD: add for DSDA
   */
  public void setInCallScreen (InCallScreenDelegate inCallScreenDelegate) {
    mInCallScreenDelegate = inCallScreenDelegate;
  }
  /**
   * SPRD: add for DSDA, bug900867
   */
  public void updateCallState(int callState) {
    if (stateView != null) {
      stateView.setText(getSecondaryCallState(stateView.getContext(), callState));
    }
  }
  /**
   * SPRD: add for DSDA
   */
  public String getSecondaryCallState(Context context, int state) {
    String callStateLabel = context.getString(R.string.incall_on_hold);
    switch  (state) {
      case DialerCall.State.ACTIVE:
        callStateLabel = context.getString(R.string.incall_in_call);
        break;
      case DialerCall.State.ONHOLD:
        callStateLabel = context.getString(R.string.incall_on_hold);
        break;
      case DialerCall.State.CONNECTING:
      case DialerCall.State.DIALING:
        callStateLabel = context.getString(R.string.incall_dialing);
        break;
      case DialerCall.State.INCOMING:
      case DialerCall.State.CALL_WAITING:
        callStateLabel = context.getString(R.string.incall_incoming_call);
        break;
      case DialerCall.State.DISCONNECTING:
        callStateLabel = context.getString(R.string.incall_hanging_up);
        break;
      case DialerCall.State.CONFERENCED:
        callStateLabel = context.getString(R.string.incall_conf_call);
        break;
      default:
    }
    return callStateLabel;
  }

  // UNISOC: add for bug907818
  public void adjustLayout() {
    int height = 0;
    final Rect rect = new Rect();
    try {
      Window window = getActivity().getWindow();
      window.getDecorView().getWindowVisibleDisplayFrame(rect);
      height = rect.top;

      int heightV = getView().getHeight();
      int heightC = holdContainer.getHeight();

      if (heightC == heightV && heightC != 0) {
        ViewGroup.LayoutParams para;
        para = getView().getLayoutParams();
        para.height = heightC + height;
        holdContainer.setPadding(holdContainer.getPaddingLeft(), holdContainer.getPaddingTop() + height, holdContainer.getPaddingRight(), holdContainer.getPaddingBottom());
        getView().setLayoutParams(para);
        getView().requestLayout();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
