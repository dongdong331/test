/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.incallui.sprd.plugin.SpeakerToHeadset;

import android.content.Context;

import com.android.dialer.R;
import com.android.incallui.Log;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
/*
* Add for hands-free switch to headset
* */
public class  SpeakerToHeadsetHelper {

private static final String TAG = "SpeakerToHeadsetHelper";
static SpeakerToHeadsetHelper sInstance;
public SpeakerToHeadsetHelper() {
   }
   public static SpeakerToHeadsetHelper getInstance(Context context) {
      if (sInstance == null) {
           if(context.getResources().getBoolean(R.bool.config_is_speaker_to_headset_feature)){
              sInstance = new SpeakerToHeadsetPlugin();
           } else {
              sInstance = new SpeakerToHeadsetHelper();
           }
       }
        Log.d(TAG, "getInstance [" + sInstance + "]");
      return sInstance;
   }

   public void init(Context context, InCallButtonUiDelegate inCallButtonUiDelegate) {
   }

    public void unRegisterSpeakerTriggerListener() {
   }
}
