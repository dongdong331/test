/*
 ** Copyright 2016 The Spreadtrum.com
 */

package com.android.server.power.sprdpower;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AppGlobals;
import android.app.PendingIntent;
import android.app.sprdpower.IPowerGuru;
import android.app.IActivityManager;
import android.app.INotificationManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.IUidObserver;
import android.app.usage.IUsageStatsManager;
import android.app.usage.UsageEvents.Event;
import android.app.usage.UsageStatsManagerInternal;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.ParceledListSlice;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IDeviceIdleController;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.service.notification.StatusBarNotification;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseIntArray;

import com.android.internal.R;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

import android.os.sprdpower.Util;

public class AppState {

    static final String TAG = "PowerController.AppState";

    private final boolean DEBUG = Util.isDebug() || Util.getDebugLog();
    private final boolean DEBUG_MORE = false;


    static final int TCP_TYPE_IPV4 = 0;
    static final int TCP_TYPE_IPV6 = 1;

    static final String NET_TCP_IPV4_PATH = "/proc/net/tcp";
    static final String NET_TCP_IPV6_PATH = "/proc/net/tcp6";

    // Audio type: 'IN' for recoder; 'OUT' for play music
    public static int AUDIO_TYPE_NONE = 0;
    public static int AUDIO_TYPE_IN = 1<<1;
    public static int AUDIO_TYPE_OUT = 1<<2;

    // App status, BG/FG/USER ACTIVE/...
    int mState;
    int mLastState;
    // the launch count during current running, will clear when app is force stopped by BgClean
    int mLaunchCount;
    // the total launch count from system boot up
    int mTotalLaunchCount;
    // the last using time during current running, will clear when app is force stopped by BgClean
    long mLastTimeUsed; // elapsed time instead
    // the last launch time during current running, will clear when app is force stopped by BgClean
    long mLastLaunchTime;  // elapsed time instead

    // if true, then track the count of Launch (MOVE_TO_FOREGROUND) during standby
    boolean mTrackingLaunchCountWhenStandby;
    int mLastLaunchCountWhenStandby;
    int mLaunchCountWhenStandby;
    boolean mStateChangedToFGDuringStandby;
    boolean mStateChangedStudyDone;

    // Process state: PROCESS_STATE_PERSISTENT / PROCESS_STATE_PERSISTENT_UI /...
    int mProcState;

    // timestamp when app enter a state that can be evaluated to be put to PowerGuru blacklist
    long mBeginPowerGuruEvaluatedTimeStamp;

    // timestamp when app enter a state that can be evaluated to enter standby
    long mBeginEvaluatedTimeStamp;

    String mPackageName;
    int mUserId;

    // The kernel user-ID that has been assigned to this application
    int mUid;

    // flags from ApplicationInfo
    int mFlags;

    // if this app is already set in PowerGuru constrained list
    boolean mInPowerGuruBlackList;

    // if this app is already set in App Idle state
    boolean mInAppStandby;
    // timestamp at entering AppStandby
    long mBeginStanbyTimeStamp;
    long mMinForcedStanbyDuration;

    // Received bytes when starting Evaluated
    // only for the app that its procState is < PROCESS_STATE_BOUND_FOREGROUND_SERVICE
    long mRxBytesWhenStartEvaluated;
    // timestamp at recoding rxBytes
    long mTimeStampForRxBytes;

    // the socket stream for this app
    ArrayList<String> mSocketStreams;
     // if doing download
    boolean mDoingDownload;
     // download is checked using a time slice (default 30s)
     // if the count of time slice using to complete detecting download
     // is >= 1, then we can sure that user use this app doing download
     // before standby
    int mUsedTimeSliceCount;

    // if set to true, this app is constrained for hold partial wake lock
    boolean mWakeLockContrained;

    // if set to true, then will not try to kill this app
    boolean mAvoidKilling;


    // if set to true, then this app is constraint for GPS access
    boolean mGpsConstrained;

    // if set to true, then this app has active notification when enter standby
    boolean mHasNotification;
    boolean mHasNoClearNotification;

    // if set to true, then this app is playing music for a loop
    boolean mPlayingMusic;

    // if this a input method
    boolean mIsEnabledInputMethod;
    boolean mIsDefaultInputMethod;

    // current audio type
    int mAudioFlag;
    // last time a playing music behavior is observed
    long mLastTimePlayingMusicSeen;

    // wakeup alarm info
    long mStartRunningTime; // the start time of this running
    long mRunningDuration; // the running duration of the app
    int mTotalWakeupAlarmCount;

    // gps request info
    long mStartRequestGpsTime;
    long mRequestGpsDuration;
    int mRequestGpsCount; // the current request gps count

    // if this app is visible
    boolean mVisible;
    long mLastVisibleTime;
    long mLastStopTime;
    long mLastInvisibleTime;

   boolean mFCMorGCMForAppIdle;
   boolean mFCMorGCMForBgClean;

    // if this app need using gps,
    // such as doing a navigation or location tracker (sports)
    boolean mNeedUsingGps;

    public AppState (String packageName, int userId, int uid, int state, int procState, int flags) {
        mPackageName = packageName;
        mUserId = userId;
        mUid = uid;
        mState = state;
        mProcState = procState;
        mFlags = flags;
        mInAppStandby = false;
        mInPowerGuruBlackList = false;
        mBeginStanbyTimeStamp = 0;
        mMinForcedStanbyDuration = 0;
        mBeginEvaluatedTimeStamp = 0;
        mBeginPowerGuruEvaluatedTimeStamp = 0;
        mRxBytesWhenStartEvaluated = 0;
        mTimeStampForRxBytes = 0;
        mSocketStreams = new ArrayList<String>();
        mDoingDownload = false;
        mUsedTimeSliceCount = 0;
        mWakeLockContrained = false;
        mGpsConstrained = false;
        mHasNotification = false;
        mHasNoClearNotification = false;
        mPlayingMusic = false;
        mIsEnabledInputMethod = false;
        mIsDefaultInputMethod = false;

        mStateChangedToFGDuringStandby = false;
        mTrackingLaunchCountWhenStandby = false;
        mLastLaunchCountWhenStandby = 0;
        mLaunchCountWhenStandby = 0;
        mStateChangedStudyDone = false;

        mLastState = mState;
        if (Event.MOVE_TO_FOREGROUND == state) {
            mLaunchCount = 1;
            mTotalLaunchCount = 1;
            mLastLaunchTime = SystemClock.elapsedRealtime();
        } else {
            mLaunchCount = 0;
            mTotalLaunchCount = 0;
            mLastLaunchTime = 0;
        }

        if (Event.SYSTEM_INTERACTION != state)
            mLastTimeUsed = SystemClock.elapsedRealtime();
        else
            mLastTimeUsed = 0;

        mAvoidKilling = false;

        mAudioFlag = AUDIO_TYPE_NONE;

        mStartRunningTime = SystemClock.elapsedRealtime();
        mRunningDuration = 0;
        mTotalWakeupAlarmCount = 0;

        mStartRequestGpsTime = 0;
        mRequestGpsDuration = 0;
        mRequestGpsCount = 0;

        mVisible = false;
        mLastVisibleTime = 0;
        mLastStopTime = 0;
        mLastInvisibleTime = 0;
        mLastTimePlayingMusicSeen = 0;

        mFCMorGCMForAppIdle = false;
        mFCMorGCMForBgClean = false;

        mNeedUsingGps = false;
    }

    // return the old state
    public int updateAppState(int state) {
        int oldState = mState;

        mLastState = mState;
        mState = state;
        if (Event.MOVE_TO_FOREGROUND == mState) {
            mLaunchCount++;
            mTotalLaunchCount++;
            mLastLaunchTime = SystemClock.elapsedRealtime();
        }

        if (Event.SYSTEM_INTERACTION != mState
            && Event.NONE != mState) {
            mLastTimeUsed = SystemClock.elapsedRealtime();
            if (mTrackingLaunchCountWhenStandby && Event.MOVE_TO_FOREGROUND == mState) {
                mLaunchCountWhenStandby++;
                if (DEBUG) Slog.d(TAG, "uid:" + mUid + " packageName:" + mPackageName
                    + " mLaunchCountWhenStandby:" + mLaunchCountWhenStandby);
            }
        }


        // if target app exit
        if (mProcState == ActivityManager.PROCESS_STATE_CACHED_EMPTY
                && Event.NONE == mState) {
            if (mStartRunningTime > 0)
                mRunningDuration += (SystemClock.elapsedRealtime() - mStartRunningTime);

            // clear mStartRunningTime
            mStartRunningTime = 0;
        } else if (mStartRunningTime == 0) {
            mStartRunningTime = SystemClock.elapsedRealtime();
        }

        return oldState;
    }

    // update the mLastTimeUsed, only for app that has been launched
    public void forceUpdateLastUsedTime() {
        if (mLaunchCount > 0) {
            mLastTimeUsed = SystemClock.elapsedRealtime();
        }
    }


    public boolean updateAppPowerGuruState(boolean inBlacklist) {
        mInPowerGuruBlackList = inBlacklist;
        return true;
    }


    public boolean updateAppStandbyState(boolean standby) {
        if (mInAppStandby != standby) {
            mInAppStandby = standby;
            if (mInAppStandby) {
                mBeginStanbyTimeStamp = SystemClock.elapsedRealtime();
            } else {
                mBeginStanbyTimeStamp = 0L;
            }
        }
        return true;
    }

    /**
     * update the RxBytes && socket stats
     * only for app with procState <= ActivityManager.PROCESS_STATE_SERVICE
     */
    public boolean updateAppTrafficStats(boolean clear) {

        if (clear) {
            mRxBytesWhenStartEvaluated = 0;
            mTimeStampForRxBytes = 0;
            mSocketStreams.clear();
            mUsedTimeSliceCount = 0;
            mDoingDownload = false;
        } else if (mRxBytesWhenStartEvaluated == 0
            && mProcState <= ActivityManager.PROCESS_STATE_SERVICE
            && mUid > Process.FIRST_APPLICATION_UID) {

            mRxBytesWhenStartEvaluated = TrafficStats.getUidRxBytes(mUid);
            mTimeStampForRxBytes = SystemClock.elapsedRealtime();
            if (DEBUG) Slog.d(TAG, "uid:" + mUid + " packageName:" + mPackageName
                + " RxBytes:" + mRxBytesWhenStartEvaluated);

            // get socket stream info
            // getAppSocketStreams(state); // --> in fact this is not needed
        }
        return true;
    }

    public boolean updateAppWakeLockConstrainedState(boolean constrained) {
        mWakeLockContrained = constrained;
        return true;
    }

    // To clear the information about a launched app
    public void clearLaunchInfo() {
        mProcState = ActivityManager.PROCESS_STATE_CACHED_EMPTY;
        mState = Event.NONE;
        mLastState = mState;
        mLaunchCount = 0;
        mLastLaunchTime = 0;
        mLastTimeUsed = 0;
        mAvoidKilling = false;

        mLastStopTime = SystemClock.elapsedRealtime();
    }


    public boolean updateAppGpsConstrainedState(boolean constrained) {
        mGpsConstrained = constrained;
        return true;
    }


    // check if the mState will change to Event.MOVE_TO_FOREGROUND during standby 
    // if the changing rate > 1/(30s), we see it yes
    // it means, this app will do some special action when it is in some state.
    // for example: for com.codoon.gps, when it is tracking sports, it will change to Event.MOVE_TO_FOREGROUND frequently
    //      and when it stop tracking, it will not change the mState during standby
    public boolean stateChangedToFGDuringStandby(long standbyStartTime) {
        if (standbyStartTime <= 0) return false;
        if (mStateChangedStudyDone) return  mStateChangedToFGDuringStandby;

        long standbyDuration = (SystemClock.elapsedRealtime() - standbyStartTime)/(1000); // seconds
        if (standbyDuration <= 120) return false;
        // < 1/(30s)
        if ((mLaunchCountWhenStandby - mLastLaunchCountWhenStandby) < (standbyDuration /30)) {
            if (DEBUG) Slog.d(TAG, "app: " + mPackageName + " mLaunchCountWhenStandby:" + mLaunchCountWhenStandby
                + " mLastLaunchCountWhenStandby:" + mLastLaunchCountWhenStandby
                + " duration:" + standbyDuration);
            mStateChangedToFGDuringStandby = false;
        } else {
            mStateChangedToFGDuringStandby = true;
            mStateChangedStudyDone = true;
        }

        return mStateChangedToFGDuringStandby;
    }


    public void setPlayingMusicState(boolean playing) {
        mPlayingMusic = playing;
    }

    public void noteDeviceStateChanged(boolean bStandby, final long nowELAPSED) {
        if (bStandby) {
            mTrackingLaunchCountWhenStandby = true;
            mLastLaunchCountWhenStandby = mLaunchCountWhenStandby;
        } else {
            mTrackingLaunchCountWhenStandby = false;
            //mHasNoClearNotification = false;
            //mHasNotification = false;
            mPlayingMusic  = false;
            mDoingDownload = false;
        }
    }

    public void updateActiveNotificationState(Context context) {
        boolean hasNotification = false;

        if (mPackageName != null && mPackageName.equals("android")) return;

        // has other method to judge ??
        try {
            INotificationManager inm = NotificationManager.getService();
            final ParceledListSlice<StatusBarNotification> parceledList
                    = inm.getAppActiveNotifications(mPackageName, UserHandle.myUserId());
            final List<StatusBarNotification> list = parceledList.getList();
            //StatusBarNotification[] active = list.toArray(new StatusBarNotification[list.size()]);

            if (list != null && list.size() > 0) {
                hasNotification = true;
            }

            int N = list.size();
            for (int i = 0; i < N; i++) {
                StatusBarNotification sbn = list.get(i);
                Notification notification = sbn.getNotification();
                if (DEBUG) Slog.d(TAG,  "packageName:" + mPackageName
                    + " has Notification:(" + notification
                    /*+ " title=" + notification.extras.getCharSequence(Notification.EXTRA_TITLE)*/
                    /*+ " text=" + notification.extras.getCharSequence(Notification.EXTRA_TEXT)*/ +")");

                if (notification.extras.getCharSequence(Notification.EXTRA_TEXT) != null) {

                    String sysText = context.getString(com.android.internal.R.string
                                                                            .app_running_notification_text);
                    String text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString();
                    int color = context.getColor(com.android.internal
                                                .R.color.system_notification_accent_color);
                    if (sysText.equals(text) && color== notification.color) {
                        continue;
                    }
                }

                if (notification.priority >= Notification.PRIORITY_DEFAULT
                    && ((notification.flags & Notification.FLAG_NO_CLEAR) != 0)
                    && ((notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0)) {
                    mHasNoClearNotification = true;
                }
            }

        } catch (Exception e) {}

        // notification is clear, update the 
        if (mHasNotification != hasNotification && mHasNotification) {
            mLastTimeUsed = SystemClock.elapsedRealtime();
        }
        mHasNotification = hasNotification;
   }

    public void updateAudioState(int audioState) {
        if (mAudioFlag != audioState
            && (audioState & AUDIO_TYPE_OUT) != 0) {
            mLastTimePlayingMusicSeen = SystemClock.elapsedRealtime();
        }

        mAudioFlag = audioState;
    }

    public boolean isPlayingMusic() {
        return (mAudioFlag & AUDIO_TYPE_OUT) != 0;
    }

    public boolean isRecording() {
        return (mAudioFlag & AUDIO_TYPE_IN) != 0;
    }

    public void incWakeupAlarm() {
        mTotalWakeupAlarmCount++;
    }

    public int getWakeupAlarmAvgCount() {
        long currentRunningDuration = 0;
        if (mStartRunningTime > 0) {
            currentRunningDuration = SystemClock.elapsedRealtime() - mStartRunningTime;
        }
        int hours = (int)((mRunningDuration+currentRunningDuration) / (3600*1000));

        if (hours <= 0) hours = 1;

        return mTotalWakeupAlarmCount / hours;
    }

    // @param state: 1 is request, 0 stop request
    public void updateGpsRequestState(int state) {
        if (state == 1) {
            mRequestGpsCount++;
            if (mRequestGpsCount > 0 && mStartRequestGpsTime == 0)
                mStartRequestGpsTime = SystemClock.elapsedRealtime();
        } else {
            mRequestGpsCount--;
            if (mRequestGpsCount == 0) {
                if (mStartRequestGpsTime > 0)
                    mRequestGpsDuration += (SystemClock.elapsedRealtime() - mStartRequestGpsTime);

                mStartRequestGpsTime = 0;
            }
        }
    }

    public long getGpsRequestDuration() {
        return mRequestGpsDuration;
    }

    public void updateVisibleState(boolean visible) {
        if (DEBUG_MORE) Slog.d(TAG, "updateVisibleState: uid:" + mUid + " packageName:" + mPackageName
            + " visible:" + visible);

        if (mVisible != visible) {
            mVisible = visible;
            if (visible) mLastVisibleTime = SystemClock.elapsedRealtime();
            else mLastInvisibleTime = SystemClock.elapsedRealtime();
        }
    }

    public void updateGpsRequirementState(boolean need) {
        mNeedUsingGps = need;
    }

    /////////////////For setting AppState END////////////////////////////////////////////////////////


    /////////////////Below for App Traffic information////////////////////////////////////////////////////////

    /**
     * sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
     * 0: 00000000000000000000000000000000:3CC3 00000000000000000000000000000000:0000 0A 00000000:00000000 00:00000000 00000000 10090        0 1596774 1 00000000 100 0 0 10 -1
     * sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
     * 0: 0100007F:13AD 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 515724 1 00000000 100 0 0 10 0
     */

    private String parseLine(String line, int uid, int type) {
        String sl = null;
        String local_address = null;
        String rem_address = null;
        String st = null;
        String tx_queue_rx_queue = null;
        String tr_tm_when = null;
        String retrnsmt = null;
        int local_uid = -1;
        String tcpStream = null;

        if (line == null) return null;


        if (DEBUG) Slog.d(TAG,"line:" + line);
        String trimline = line.trim();

        if (trimline == null) return null;

        if (DEBUG) Slog.d(TAG,"trimline:" + trimline);

        String localAddr = null;
        if (type == TCP_TYPE_IPV4) {
            localAddr = "00000000";
        } else {
            localAddr = "00000000000000000000000000000000";
        }


        String[] tokens = trimline.split(" ");

        if (tokens.length < 8) return null;

        if (DEBUG) Slog.d(TAG,"length:" +tokens.length);

        sl = tokens[0];
        local_address = tokens[1];
        rem_address = tokens[2];
        st = tokens[3];
        tx_queue_rx_queue = tokens[4];
        tr_tm_when = tokens[5];
        retrnsmt = tokens[6];
        for(int i=7; i<tokens.length; i++) {
            if (!tokens[i].equals("")) {
                local_uid = Integer.parseInt(tokens[i]);
                if (DEBUG) Slog.d(TAG,"uid:" + local_uid);
                break;
            }
        }
        if (DEBUG) Slog.d(TAG,"sl:" + sl + " " + local_address + " " + rem_address
            + " " + st + " "  + tx_queue_rx_queue);

        if (local_uid == uid && !rem_address.contains(localAddr)) {
            tcpStream = sl + " " + local_address + " " + rem_address;
            if (DEBUG) Slog.d(TAG,"TCP Stream:" + tcpStream);
        }

        return tcpStream;
    }


    private List<String> getUidTcpStreams(int uid, int type) {
        ArrayList<String> tcpStreams = new ArrayList<String>();

        String filePath = null;
        if (type == TCP_TYPE_IPV4) {
            filePath = NET_TCP_IPV4_PATH;
        } else {
            filePath =NET_TCP_IPV6_PATH;
        }

        FileInputStream reader = null;
        try {
            reader = new FileInputStream(filePath);

            byte[] buffer = new byte[4096];
            String contents = null;

            int count = reader.read(buffer);
            if (count > 0) {
                contents = new String(buffer, 0, count);
                String[] tokens = contents.split("\n");
                for(int i=1; i<tokens.length; i++) {
                    String stream = parseLine(tokens[i], uid, type);
                    if (stream != null) tcpStreams.add(stream);
                }
            }

        } catch (Exception e) {
            Slog.d(TAG, "Exception:" + e);
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e1) {
                    Slog.e(TAG, " Error reading file" + e1);
                }
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    Slog.e(TAG, " Error reading file" + e);
                }
            }
        }

        return tcpStreams;
    }

    private boolean getAppSocketStreams(AppState state) {
        List<String> tcpV4Streams = getUidTcpStreams(state.mUid, TCP_TYPE_IPV4);
        List<String> tcpV6Streams = getUidTcpStreams(state.mUid, TCP_TYPE_IPV6);

        for(String s: tcpV4Streams) {
            if (s != null)
                state.mSocketStreams.add(s);
        }

        for(String s: tcpV6Streams) {
            if (s != null)
                state.mSocketStreams.add(s);
        }

        if (DEBUG) {
            Slog.d(TAG,"TCP Streams For:" + state.mPackageName + " size:" + state.mSocketStreams.size());
            for (String s: state.mSocketStreams) {
                if (DEBUG) Slog.d(TAG, s);
            }
        }
        return true;
    }

    private List<String> getUidSocketStreams(int uid) {
        List<String> tcpV4Streams = getUidTcpStreams(uid, TCP_TYPE_IPV4);
        List<String> tcpV6Streams = getUidTcpStreams(uid, TCP_TYPE_IPV6);
        List<String> socketStreams = new ArrayList<String>();

        for(String s: tcpV4Streams) {
            if (s != null)
                socketStreams.add(s);
        }

        for(String s: tcpV6Streams) {
            if (s != null)
                socketStreams.add(s);
        }

        if (DEBUG) {
            Slog.d(TAG,"TCP Streams For:" + uid + " size:" + socketStreams.size());
            for (String s: socketStreams) {
                if (DEBUG) Slog.d(TAG, s);
            }
        }
        return socketStreams;
    }

    // return true for equal
    private boolean compareSocketStreams(List<String> stream1, List<String> stream2) {
        if(stream1.size() != stream2.size()) return false;
        for (String s: stream1) {
            int index = stream2.indexOf(s);
            if (index < 0) {
                return false;
            }
        }

        return true;
    }

    /////////////////For App Traffic information END////////////////////////////////////////////////////////


}
