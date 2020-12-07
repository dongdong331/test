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

package com.android.incallui.incall.impl;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.ArraySet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.incallui.incall.protocol.InCallButtonIds;
import java.util.List;
import java.util.Set;

/** Fragment for the in call buttons (mute, speaker, ect.). */
public class InCallButtonGridFragment extends Fragment {

  private static final String PAGE = "page";

  private static final int BUTTON_COUNT = 6;
  public static final int BUTTONS_PER_ROW = 3;

  private CheckableLabeledButton[] buttons = new CheckableLabeledButton[BUTTON_COUNT];
  private OnButtonGridCreatedListener buttonGridListener;

  /* SPRD : InCallUI Layout Refactor */
  private int page;

  public static Fragment newInstance(int page) {
    InCallButtonGridFragment f = new InCallButtonGridFragment();
    Bundle bundle = new Bundle();
    bundle.putInt(PAGE, page);
    f.setArguments(bundle);
    return f;
  }

  /* @} */
  @Override
  public void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    if (getArguments() != null) {
      page = getArguments().getInt(PAGE);
    }

    buttonGridListener = FragmentUtils.getParent(this, OnButtonGridCreatedListener.class);
    Assert.isNotNull(buttonGridListener);
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle bundle) {
    View view = inflater.inflate(R.layout.incall_button_grid, parent, false);

    buttons[0] = ((CheckableLabeledButton) view.findViewById(R.id.incall_first_button));
    buttons[1] = ((CheckableLabeledButton) view.findViewById(R.id.incall_second_button));
    buttons[2] = ((CheckableLabeledButton) view.findViewById(R.id.incall_third_button));
    buttons[3] = ((CheckableLabeledButton) view.findViewById(R.id.incall_fourth_button));
    buttons[4] = ((CheckableLabeledButton) view.findViewById(R.id.incall_fifth_button));
    buttons[5] = ((CheckableLabeledButton) view.findViewById(R.id.incall_sixth_button));

    return view;
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle bundle) {
    super.onViewCreated(view, bundle);
    buttonGridListener.onButtonGridCreated(this, page);
  }

  //UNISOC: add for bug900503
  @Override
  public void onResume() {
    super.onResume();
    buttonGridListener.onButtonStatusUpdated();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    buttonGridListener.onButtonGridDestroyed(page);
  }

  public void onInCallScreenDialpadVisibilityChange(boolean isShowing) {
    for (CheckableLabeledButton button : buttons) {
      button.setImportantForAccessibility(
          isShowing
              ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
              : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }
  }

  public int updateButtonStates(
      List<ButtonController> buttonControllers,
      @Nullable ButtonChooser buttonChooser,
      int voiceNetworkType,
      int phoneType,
      Context context) {
    Set<Integer> allowedButtons = new ArraySet<>();
    Set<Integer> disabledButtons = new ArraySet<>();
    for (ButtonController controller : buttonControllers) {
      if (controller.isAllowed()) {
        allowedButtons.add(controller.getInCallButtonId());
        if (!controller.isEnabled()) {
          disabledButtons.add(controller.getInCallButtonId());
        }
      }
    }

    /*for (ButtonController controller : buttonControllers) {
      controller.setButton(null);
    }*/

    if (buttonChooser == null) {
      buttonChooser =
          ButtonChooserFactory.newButtonChooser(voiceNetworkType, false /* isWiFi */, phoneType, context);
    }

    /* SRPD: InCallUI Layout Refactor @{ */
    int numVisibleButtons = getResources().getInteger(R.integer.incall_num_rows) * BUTTONS_PER_ROW * 2;
    List<Integer> buttonsToPlace =
        buttonChooser.getButtonPlacement(numVisibleButtons, allowedButtons, disabledButtons);

    // Used for notifer incallfragment update page.
    buttonGridListener.onPageChanged(buttonsToPlace.size());

    if (page == InCallPagerAdapter.PAGE_TWO && buttonsToPlace.size() > BUTTON_COUNT) {
      int i = 0;
      for (; i < (buttonsToPlace.size() - BUTTON_COUNT); ++i) {
        @InCallButtonIds int button = buttonsToPlace.get(i + BUTTON_COUNT);
        buttonGridListener.getButtonController(button).setButton(buttons[i]);
      }
      for (int j = i; j < BUTTON_COUNT; ++j) {
        buttons[j].setVisibility(View.INVISIBLE);
      }

      return numVisibleButtons;
    } else if (page == InCallPagerAdapter.PAGE_TWO) {
      return 0;
    }
    /* @} */
    for (ButtonController controller : buttonControllers) {
      controller.setButton(null);
    }

    for (int i = 0; i < BUTTON_COUNT; ++i) {
      if (i >= buttonsToPlace.size()) {
        buttons[i].setVisibility(View.INVISIBLE);
        continue;
      }
      @InCallButtonIds int button = buttonsToPlace.get(i);
      buttonGridListener.getButtonController(button).setButton(buttons[i]);
    }

    return numVisibleButtons;
  }

  public void updateButtonColor(@ColorInt int color) {
    for (CheckableLabeledButton button : buttons) {
      button.setCheckedColor(color);
    }
  }

  /** Interface to let the listener know the status of the button grid. */
  public interface OnButtonGridCreatedListener {
    /* SRPD: InCallUI Layout Refactor @{ */
    void onButtonGridCreated(InCallButtonGridFragment inCallButtonGridFragment, int page);
    void onButtonGridDestroyed(int page);
    void onPageChanged(int buttonsToPlaceSize);
    /* @} */
    //UNISOC: add for bug900503
    void onButtonStatusUpdated();

    ButtonController getButtonController(@InCallButtonIds int id);
  }
}
