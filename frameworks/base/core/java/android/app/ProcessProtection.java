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
 * limitations under the License.
 */

package android.app;

import android.os.Process;
import android.os.RemoteException;

/**
 * ProtectArea feature related interface and constant.
 *
 */
public class ProcessProtection {
    /**
     * If protected process status set to this state, it means the process become
     * normal background process.
     */
    public static final int  PROCESS_STATUS_IDLE = 0;
    /**
     * If protected process status set to this state, its adj will be force adjust to
     * com.android.server.am.ProcessList#FOREGROUND_APP_ADJ
     */
    public static final int  PROCESS_STATUS_RUNNING = 1;
    /**
     * If protected process status set to this state, its adj will be force adjusted to
     * com.android.server.am.ProcessList#PERCEPTIBLE_APP_ADJ
     */
    public static final int  PROCESS_STATUS_MAINTAIN = 2;
    /**
     * If protected process status set to this state, the process will be persistent,
     * its adj will be force adjusted to com.android.server.am.ProcessList#PERSISTENT_PROC_ADJ
     * if protected status is this, do not reset status to PROCESS_STATUS_IDLE
     */
    public static final int  PROCESS_STATUS_PERSISTENT = 3;
    /**
     * If process protect level set to this level, its adj will be force adjusted to
     * com.android.server.am.ProcessList#FOREGROUND_APP_ADJ when the process adj
     * get into the range that you set in the ProtectArea.
     */
    public static final int  PROCESS_PROTECT_CRITICAL = 11;
    /**
     * If process protect level set to this level, its adj will be force adjusted to
     * com.android.server.am.ProcessList#PERCEPTIBLE_APP_ADJ when the process adj
     * get into the range that you set in the ProtectArea.
     */
    public static final int  PROCESS_PROTECT_IMPORTANCE = 12;
    /**
     * If process protect level set to this leve, its adj will be force adjusted to
     * com.android.server.am.ProcessList#HEAVY_WEIGHT_APP_AD when the process adj
     * get into the range that you set in the ProtectArea.
     */
    public static final int  PROCESS_PROTECT_NORMAL = 13;
    /**
     * Set myslef process protect status
     * @see #PROCESS_STATUS_IDLE
     * @see #PROCESS_STATUS_RUNNING
     * @see #PROCESS_STATUS_MAINTAIN
     * @see #PROCESS_STATUS_PERSISTENT
     */
    public void setSelfProtectStatus(int status) {
        try {
            ActivityManager.getService().setProcessProtectStatusByPid(Process.myPid(), status);
        } catch (RemoteException e) {
            // if this happen the system will die soon.
        }
    }
}
