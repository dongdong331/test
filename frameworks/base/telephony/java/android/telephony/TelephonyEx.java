/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.provider;

import android.net.Uri;
import android.provider.Telephony.BaseMmsColumns;

public final class TelephonyEx {

	public interface SmsEx {
		/** Message type: alarm. 613227 */
        public static final int MESSAGE_TYPE_ALARM = 8;
		
		/** 613227. */
        public static final String ALARM = "alarm";
		
	}
	
	public interface MmsEx{
		/** Message box: alarm. 613227 */
        public static final int MESSAGE_BOX_ALARM = 8;
		
		/** 613227. */
        public static final String ALARM = "alarm";
		
	    /**
         *613227 Contains all MMS Alarm messages in the MMS app alarmbox.
         */
		public static final class Alarmbox implements BaseMmsColumns {
            /**
             * Not instantiable.
             * @hide
             */
            private Alarmbox() {
            }

            /**
             * The {@code content://} style URL for this table.
             */
            public static final Uri
                    CONTENT_URI = Uri.parse("content://mms/alarmbox");

            /**
             * The default sort order for this table.
             */
            public static final String DEFAULT_SORT_ORDER = "date DESC";
        }
	}
}