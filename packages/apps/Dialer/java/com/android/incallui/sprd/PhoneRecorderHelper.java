package com.android.incallui.sprd;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.EnvironmentEx;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.telecom.Call;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;
import android.util.Log;

import com.android.dialer.app.R;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.binary.common.DialerApplication;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.sprd.settings.callrecording.CallRecordingContactsHelper;

public class PhoneRecorderHelper {
    private static final String TAG = "PhoneRecorderHelper";
    private static final boolean DBG = true;
    private static final boolean TEST_DBG = false;

    private static final int SIGNAL_STATE = 7;
    private static final int SIGNAL_ERROR = 8;
    private static final int SIGNAL_TIME = 9;
    private static final int SIGNAL_MSG = 10;

    private static final int MSG_CHECK_DISK = 10;
    private static final int MSG_START = 11;
    private static final int MSG_STOP = 12;

    private static final String DEFAULT_STORE_SUBDIR = "/voicecall";
    private static final String DEFAULT_FROMAT = "yyyy-MM-dd HH:mm:ss";
    private static final int DEFAULT_AUDIO_SAMPLING_RATE = 44100;
    private static final String DEFAULT_DECOLLATOR = "-";
    private static final String DEFAULT_SIM_DESCRIPTOR = "sim";
    private static final String COMPOSER_NAME = "Call Record";

    public static final int NO_ERROR = 0;
    public static final int TYPE_ERROR_SD_ACCESS = 1; // can not access sdcard
    public static final int TYPE_ERROR_SD_NOT_EXIST = 2; // sdcard no exist
    public static final int TYPE_ERROR_SD_FULL = 3;
    public static final int TYPE_ERROR_FILE_INIT = 4;
    public static final int TYPE_ERROR_IN_RECORD = 5; // may be other application is recording
    public static final int TYPE_ERROR_INTERNAL = 6; // internal error
    public static final int TYPE_MSG_PATH = 7;
    public static final int TYPE_SAVE_FAIL = 8;
    public static final int TYPE_NO_AVAILABLE_STORAGE = 9; // no available storage

    private static final long MIN_LENGTH = 512; // 512
    private static final long MINIMUM_FREE_SIZE = 6 * 1024 * 1024;
    private static final long FORCE_FREE_SIZE = MINIMUM_FREE_SIZE;
    private static final long INIT_TIME = 0;
    private static final int INVALID_SIM_SLOT_INDEX = -1;

    private static final int IO_THROW_SD_FULL_ERROR = 28;

    // support mp3
    private String mRecorderSuffix = ".mp3";
    private String mMIMEType = "audio/mp3";
    private int mOutputformatType = 10;
    private int mAudioEncoder = 7;
    private boolean mIsSupportMp3;
    //SPRD: add for bug711372
    public String mBaseRoot;
    public BroadcastReceiver mSDCardMountEventReceiver;

    /**
     * a call back when state changed or an error occur
     **/
    public interface OnStateChangedListener {
        public void onStateChanged(State state);

        public void onTimeChanged(long time);

        public void onShowMessage(int type, String msg);
    }

    public enum State {
        IDLE, // a normal state
        ACTIVE, // when is recording
        WAITING; // a short moment when reset audio

        public boolean isActive() {
            return this == ACTIVE;
        }

        public boolean isIdle() {
            return this == IDLE;
        }

        public boolean isBlock() {
            return this == WAITING;
        }
    }

    private Context mContext;
    private static PhoneRecorderHelper mInstance;

    private MediaRecorder mRecorder;
    private OnStateChangedListener mOnStateChangedListener;

    private State mState = State.IDLE; // to record recorder state
    private long mTime;
    private File mSampleFile = null;
    /**
     * flag a moment when the record start
     **/
    private long mStartTime = INIT_TIME;

    private AsyncThread mAsyncThread;
    private UiHandler mUiHandler;

    // SPRD: Get default storage location from settings.
    private static final int STORAGE_INTERNAL = 0;
    private static final int STORAGE_SDCARD = 1;
    private static final int currentLocation = -1;

    // SPRD Feature Porting: Add for shaking phone to start recording.
    private final DialerApplication dialerApplication;

    private PhoneRecorderHelper(Context context) {
        mContext = context;
        HandlerThread thread = new HandlerThread("Recorder");
        thread.start();
        mAsyncThread = new AsyncThread(thread.getLooper());
        mUiHandler = new UiHandler();

        // Initialize
        mIsSupportMp3 = InCallUiUtils.isSupportMp3ForCallRecord(context);
        if (!mIsSupportMp3) {
            mRecorderSuffix = ".amr";
            mMIMEType = "audio/amr";
            mOutputformatType = MediaRecorder.OutputFormat.AMR_NB;
            mAudioEncoder = MediaRecorder.AudioEncoder.AMR_NB;
        }

        /* SPRD Feature Porting: Add for shaking phone to start recording. @{ */
        Context applicationContext = mContext.getApplicationContext();
        dialerApplication = (DialerApplication) applicationContext;
        /* @} */
    }

    private void setContext(Context context) {
        mContext = context;
    }

    /**
     * this should be single instance so that it can not be make a new one or
     * the state of the record may be wrong
     **/
    public static PhoneRecorderHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new PhoneRecorderHelper(context);
        }
        // it can not handle the old one still or the activity will be not release
        mInstance.setContext(context);
        return mInstance;
    }

    private class UiHandler extends Handler {
        private boolean updateUi;

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SIGNAL_ERROR:
                case SIGNAL_MSG:
                    signalShowMessage(msg.arg1, msg.obj != null ? String.valueOf(msg.obj) : null);
                    break;
                case SIGNAL_STATE:
                    State state = (State) msg.obj;
                    signalStateChanged(state);
                    break;
                case SIGNAL_TIME:
                    signalTimeChanged(mStartTime == INIT_TIME ? 0 : SystemClock.elapsedRealtime()
                            - mStartTime);
                    update(true);
                    break;
            }
        }

        public void update(boolean force) {
            if (updateUi) {
                if (TEST_DBG)
                    Log.d(TAG, "Update ui ");
                sendMessageDelayed(obtainMessage(SIGNAL_TIME), force ? 1000 : 0);
            }
        }

        public void setUpdate(boolean update) {
            updateUi = update;
        }

        private void signalStateChanged(State state) {
            if (mOnStateChangedListener != null) {
                mOnStateChangedListener.onStateChanged(state);
            }
        }

        private void signalTimeChanged(long time) {
            mTime = time;
            if (mOnStateChangedListener != null) {
                mOnStateChangedListener.onTimeChanged(time);
            }
        }

        private void signalShowMessage(int type, String msg) {
            if (mOnStateChangedListener != null) {
                mOnStateChangedListener.onShowMessage(type, msg);
            }
        }
    }

    private class AsyncThread extends Handler {
        private boolean checkAble;

        public AsyncThread(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            switch (what) {
                case MSG_CHECK_DISK:
                    try {// check disk now
                        Log.d(TAG, "MSG_CHECK_DISK mBaseRoot：" + mBaseRoot);
                        if (TextUtils.isEmpty(mBaseRoot)) {
                            return;
                        }
                        StatFs stat = new StatFs(mBaseRoot);
                        boolean hasAvailableSize = checkAvailableSize(FORCE_FREE_SIZE, stat);
                        if (!hasAvailableSize) {
                            Log.d(TAG, "check disk : hasAvailableSize = " + hasAvailableSize);
                            stop();
                            mUiHandler.removeMessages(SIGNAL_ERROR);
                            mUiHandler.obtainMessage(SIGNAL_ERROR, TYPE_ERROR_SD_FULL, 0)
                                    .sendToTarget();
                            break; // break the loop
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        if (e.getCause() != null && e.getCause() instanceof ErrnoException) {
                            if (((ErrnoException) e.getCause()).errno == IO_THROW_SD_FULL_ERROR) {
                                mUiHandler.removeMessages(SIGNAL_ERROR);
                                mUiHandler.obtainMessage(SIGNAL_ERROR, TYPE_ERROR_SD_FULL, 0, null)
                                        .sendToTarget();
                                stop();
                                break;
                            }
                        } else {
                            mUiHandler.removeMessages(SIGNAL_ERROR);
                            mUiHandler.obtainMessage(SIGNAL_ERROR, TYPE_ERROR_SD_ACCESS, 0, null)
                                    .sendToTarget();
                            stop();
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        mUiHandler.removeMessages(SIGNAL_ERROR);
                        mUiHandler.obtainMessage(SIGNAL_ERROR, TYPE_ERROR_SD_ACCESS, 0, null)
                                .sendToTarget();
                        stop();
                        break;
                    }
                    sleep(true);
                    break;
                case MSG_START:
                    if (mState.isIdle()) {
                        int error = startRecording(mOutputformatType,
                                mRecorderSuffix, mContext);
                        // here not to send full space msg, it will be done when handle msg
                        // with MSG_CHECK_DISK
                        if (error != NO_ERROR && error != TYPE_ERROR_SD_FULL) {
                            mUiHandler.removeMessages(SIGNAL_ERROR);
                            mUiHandler.obtainMessage(SIGNAL_ERROR, error, 0, null).sendToTarget();
                        }
                        break;
                    }
                    Log.e(TAG, "Start with error State : " + mState);
                    break;
                case MSG_STOP:
                    if (!mState.isIdle()) {
                        stopRecording();
                        if (mSampleFile == null || !mSampleFile.exists()) {
                            Log.e(TAG, "error, file not exist !");
                        }
                        long fileLength = mSampleFile.length();
                        if (fileLength > MIN_LENGTH) {
                            try {
                                // UNISOC: add for bug905957
                                if (addToMediaDB(mSampleFile) == null) {
                                    break;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "addToMediaDB happen exception : " + e);
                            }
                            String canonicalPath;
                            try {
                                canonicalPath = mSampleFile.getCanonicalPath();
                            } catch (IOException e) {
                                Log.e(TAG, "getCanonicalPath happen IOException : " + e);
                                canonicalPath = mSampleFile.getPath();
                            }
                            mUiHandler.removeMessages(SIGNAL_MSG);
                            mUiHandler.obtainMessage(SIGNAL_MSG, TYPE_MSG_PATH, 0,
                                    mContext.getString(R.string.prompt_record_finish) + canonicalPath).sendToTarget();

                            //SPRD : to display call record notification after call ended
                            CallRecordingContactsHelper.CallRecordSettingEntity recordSettings = CallRecordingContactsHelper
                                    .getInstance(mContext).getCallRecordingSettings();
                            Log.i(TAG, " PhoneRecorderHelper : " + recordSettings.getRecordingNotification());
                            //add for bug902149
                            Log.i(TAG, " PhoneRecorderHelper :getAutoCallRecording  " + recordSettings.getAutoCallRecording());
                            if (recordSettings.getAutoCallRecording() && recordSettings.getRecordingNotification()) {
                                PhoneRecordNotifier.getInstance(mContext).showNotification(mContext
                                        .getString(R.string.prompt_record_finish),canonicalPath);
                            }
                        } else {
                            mUiHandler.removeMessages(SIGNAL_MSG);
                            mUiHandler.obtainMessage(SIGNAL_MSG, TYPE_SAVE_FAIL, 0,
                                    mContext.getString(R.string.recorderr)).sendToTarget();
                            boolean isDeleteSucceed = mSampleFile.delete();
                            Log.d(TAG, "It is so short, delete file : " + mSampleFile.getName()
                                    + ", isDeleteSucceed = " + isDeleteSucceed);
                        }
                        break;
                    }
                    Log.e(TAG, "Stop with error State : " + mState);
                    break;
            }
        }

        private void sleep(boolean force) {
            if (checkAble) {
                Message m = obtainMessage(MSG_CHECK_DISK);
                sendMessageDelayed(m, force ? 2000 : 0);
                if (TEST_DBG)
                    Log.d(TAG, "send check msg ");
            }
        }

        public void setCheckAble(boolean able) {
            checkAble = able;
        }
    }

    private boolean checkAvailableSize(long size, StatFs stat) {
        long available_size = stat.getBlockSize() * ((long) stat.getAvailableBlocks() - 4);
        if (available_size < size) {
            Log.e(TAG, "Recording File aborted - not enough free space");
            return false;
        }
        return true;
    }

    private int initRecordFile() {
        File base = null;

        UserManager userManager = (UserManager) mContext
                .getSystemService(Context.USER_SERVICE);
        if (EnvironmentEx.getExternalStoragePathState().equals(
                Environment.MEDIA_MOUNTED)
                && userManager.isSystemUser()) {
            // SPRD: add for bug711372 908609
            mBaseRoot = EnvironmentEx.getExternalStoragePath().getPath();
            // UNISOC: add for bug910771
            File[] paths = mContext.getExternalMediaDirs();
            for (int i = 0; i < paths.length; ++i) {
                if (paths[i].getAbsolutePath().startsWith(mBaseRoot)) {
                    mBaseRoot = paths[i].getAbsolutePath();
                    break;
                }
            }
        } else if (EnvironmentEx.getInternalStoragePathState().equals(Environment.MEDIA_MOUNTED)) {
            // SPRD: add for bug711372
            mBaseRoot = EnvironmentEx.getInternalStoragePath().getPath();
        } else {
            return TYPE_NO_AVAILABLE_STORAGE;
        }

        // SPRD: add for bug711372
        Log.d(TAG, "initRecordFile mBaseRoot =" + mBaseRoot);
        base = new File(mBaseRoot + DEFAULT_STORE_SUBDIR);

        if (!base.isDirectory()) {
            try {
                android.system.Os.mkdir(base.getPath(), OsConstants.S_IRWXU);
            } catch (ErrnoException e) {
                Log.e(TAG, "Recording File aborted - can't create base directory : " + base.getPath());
                e.printStackTrace();
                if (e.errno == IO_THROW_SD_FULL_ERROR) {
                    return TYPE_ERROR_SD_FULL;
                } else {
                    return TYPE_ERROR_SD_ACCESS;
                }
            }
        }
        /* Naming record file with contacts name & number and sim card feature. @{ */
        // UNISOC: modify for bug905957
        SimpleDateFormat sdf = new SimpleDateFormat("'voicecall'-yyyyMMddHHmmssSSS");
        String fileName = sdf.format(new Date());
        String number = getNumber();
        if (number != null) {
            fileName = number + DEFAULT_DECOLLATOR + fileName;
        }
        int slotId = getSimSlotIndex();
        if (slotId >= 0) {
            fileName = DEFAULT_SIM_DESCRIPTOR + String.valueOf(slotId + 1)
                    + DEFAULT_DECOLLATOR + fileName;
        }
        /* @} */
        fileName = base.getPath() + File.separator + fileName + mRecorderSuffix;

        StatFs stat = null;
        try {
            stat = new StatFs(base.getPath());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "StatFs create fail!!");
            if (e.getCause() != null && e.getCause() instanceof ErrnoException) {
                if (((ErrnoException) e.getCause()).errno == IO_THROW_SD_FULL_ERROR) {
                    return TYPE_ERROR_SD_FULL;
                }
            } else {
                return TYPE_ERROR_SD_ACCESS;
            }
        }

        boolean hasAvailableSize = checkAvailableSize(MINIMUM_FREE_SIZE, stat);
        if (!hasAvailableSize) {
            // here not to send full space msg, it will be done when handle msg
            // with MSG_CHECK_DISK
            Log.e(TAG, "Recording can not start, no enough free size!");
            return TYPE_ERROR_SD_FULL;
        }

        File outFile = new File(fileName);
        try {
            if (outFile.exists()) {
                outFile.delete();
            }
            boolean bRet = outFile.createNewFile();
            if (!bRet) {
                Log.e(TAG, "getRecordFile, fn: " + fileName + ", failed");
                return TYPE_ERROR_FILE_INIT;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "getRecordFile, fn: " + fileName + ", " + e);
            return TYPE_ERROR_FILE_INIT;
        } catch (IOException e) {
            Log.e(TAG, "getRecordFile, fn: " + fileName + ", " + e);
            return TYPE_ERROR_FILE_INIT;
        }
        mSampleFile = outFile;
        return NO_ERROR;
    }

    private synchronized int startRecording(int outputfileformat, String extension, Context context) {
        //UNISOC modified for bug 980726 start
        setState(State.WAITING);
        //end
        int error = initRecordFile();
        if (error != NO_ERROR) {
            Log.e(TAG, "error, can not create a new file to record !");
            //UNISOC added for bug 980726 start
            setState(State.IDLE);
            //end
            return error;
        }
        mRecorder = new MediaRecorder();
        Log.i(TAG, "setAudioSource as MIC.");
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(outputfileformat);
        mRecorder.setAudioEncoder(mAudioEncoder);
        if (mIsSupportMp3) {
            mRecorder.setAudioSamplingRate(DEFAULT_AUDIO_SAMPLING_RATE);
        }
        mRecorder.setOutputFile(mSampleFile.getAbsolutePath());

        // Handle IOException
        try {
            mRecorder.prepare();
        } catch (IOException exception) {
            Log.e(TAG, "IOException when recorder prepare !");
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
            setState(State.IDLE);
            return TYPE_ERROR_INTERNAL;
        }
        // Handle RuntimeException if the recording couldn't start
        try {
            // SPRD: Plays the tone before recording to notify the callee.
            playDtmfTone();
            mRecorder.start();
        } catch (RuntimeException exception) {
            AudioManager audioMngr = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            boolean isInCall = audioMngr.getMode() == AudioManager.MODE_IN_CALL;
            if (isInCall) {
                Log.e(TAG, "RuntimeException when recorder start, cause : incall!");
            } else {
                Log.e(TAG, "RuntimeException when recorder start !");
            }
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
            setState(State.IDLE);
            return isInCall ? TYPE_ERROR_IN_RECORD : TYPE_ERROR_INTERNAL;
        }
        mStartTime = SystemClock.elapsedRealtime(); // start to record time
        setState(State.ACTIVE);

        // to update ui
        mUiHandler.setUpdate(true);
        mUiHandler.update(false);
        return NO_ERROR;
    }

    private synchronized void stopRecording() {
        if (mRecorder == null) {
            mStartTime = INIT_TIME;
            Log.e(TAG, "error, mRecorder is null !");
            return;
        }
        setState(State.WAITING);
        try {
            // SPRD: Plays the tone before recording to notify the callee.
            playDtmfTone();
            mRecorder.setOnErrorListener(null);
            mRecorder.setOnInfoListener(null);
            mRecorder.setPreviewDisplay(null);
            mRecorder.stop();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "RuntimeException when recorder stop!");
        } finally {
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
            mStartTime = INIT_TIME;
            // SPRD: add for bug711372
            mBaseRoot = null;
        }
        // should we reset the state in finally ?
        setState(State.IDLE);

        // to update ui
        mUiHandler.setUpdate(false);
    }

    /**
     * signal current state, when state changed
     **/
    private void setState(State state) {
        if (DBG)
            Log.d(TAG, "pre State = " + mState + ", new State = " + state);
        if (state != mState) {
            mState = state;
            Message m = mUiHandler.obtainMessage(SIGNAL_STATE, mState);
            m.sendToTarget();
        }
    }

    public State getState() {
        return mState;
    }

    public long getRecordTime() {
        return mTime;
    }

    public void setOnStateChangedListener(OnStateChangedListener listener) {
        mOnStateChangedListener = listener;
    }

    /**
     * to start recording not in looper thread
     **/
    public void start() {
        Log.d(TAG, "Recorder Start : state = " + mState);
            // SPRD Feature Porting: Add for shaking phone to start recording.
            dialerApplication.setIsRecordingStart(true);
            mAsyncThread.removeMessages(MSG_START);
            mAsyncThread.sendEmptyMessage(MSG_START);// send start msg
            mAsyncThread.setCheckAble(true);// set disk check flag
            mAsyncThread.sleep(false);// start check disk

    }

    /**
     * to stop recording not in looper thread
     **/
    public void stop() {
        Log.d(TAG, "Recorder Stop : state = " + mState);
        if (!mState.isIdle()) {
            // SPRD Feature Porting: Add for shaking phone to start recording.
            dialerApplication.setIsRecordingStart(false);
            mAsyncThread.removeMessages(MSG_STOP);
            mAsyncThread.sendEmptyMessage(MSG_STOP);// send stop msg
            mAsyncThread.setCheckAble(false);// stop check disk
        }
    }

    /**
     * start or stop recording
     **/
    public void toggleRecorder() {
        Log.d(TAG, "Recorder toggleRecorder : state = " + mState);
        if (mState.isBlock()) {
            return;
        }
        if (mState.isActive()) {
            stop();
        } else {
            start();
        }
    }

    public void notifyCurrentState() {
        Message m = mUiHandler.obtainMessage(SIGNAL_STATE, mState);
        m.sendToTarget();
    }

    /**
     * save file to db
     **/

    private Uri addToMediaDB(File file) {
        Resources res = mContext.getResources();
        ContentValues cv = new ContentValues();
        long current = System.currentTimeMillis();
        long modDate = file.lastModified();

        long duration = 0;
        MediaPlayer p = new MediaPlayer();// a media player to get a duration
        try {
            p.setDataSource(file.getAbsolutePath());
            p.prepare();
            duration = p.getDuration();
            Log.d(TAG, "Recorder duration : " + duration);
            // UNISOC: add for bug925581
            if (duration < 1000) {
                file.delete();
                // UNISOC: add for bug905957 931455
                mUiHandler.removeMessages(SIGNAL_MSG);
                mUiHandler.obtainMessage(SIGNAL_MSG, TYPE_SAVE_FAIL, 0,
                        mContext.getString(R.string.recorderr)).sendToTarget();
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (p != null) {
                p.release();
                p = null;
            }
        }

        Date date = new Date(current);
        SimpleDateFormat formatter = new SimpleDateFormat(DEFAULT_FROMAT);
        String title = formatter.format(date);

        // Lets label the recorded audio file as NON-MUSIC so that the file
        // won't be displayed automatically, except for in the playlist.
        cv.put(MediaStore.Audio.Media.IS_MUSIC, "1");
        cv.put(MediaStore.Audio.Media.DURATION, duration);
        cv.put(MediaStore.Audio.Media.TITLE, title);
        cv.put(MediaStore.Audio.Media.DATA, file.getAbsolutePath());
        cv.put(MediaStore.Audio.Media.DATE_ADDED, (int) (current / 1000));
        cv.put(MediaStore.Audio.Media.DATE_MODIFIED, (int) (modDate / 1000));
        cv.put(MediaStore.Audio.Media.MIME_TYPE, mMIMEType);
        cv.put(MediaStore.Audio.Media.ARTIST,
                res.getString(R.string.audio_db_artist_name));
        cv.put(MediaStore.Audio.Media.ALBUM,
                res.getString(R.string.audio_db_album_name));
        cv.put(MediaStore.Audio.Media.COMPOSER, COMPOSER_NAME);
        if (DBG)
            Log.d(TAG, "Inserting audio record: " + cv.toString());
        ContentResolver resolver = mContext.getContentResolver();
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        if (DBG)
            Log.d(TAG, "ContentURI: " + base);
        Uri result = resolver.insert(base, cv);
        if (result == null) {
            new AlertDialog.Builder(mContext).setTitle(R.string.app_name)
                    .setMessage(R.string.error_mediadb_new_record)
                    .setPositiveButton(R.string.ok, null).setCancelable(false)
                    .show();
            Log.e(TAG, "addToMediaDB:result == null");
            return null;
        }
        if (getPlaylistId(res) == -1) {
            createPlaylist(res, resolver);
        }
        int audioId = Integer.valueOf(result.getLastPathSegment());
        addToPlaylist(resolver, audioId, getPlaylistId(res));
        if (DBG)
            Log.d(TAG, "addToMediaDB: success , send scanner intent!");

        // Notify those applications such as Music listening to the
        // scanner events that a recorded audio file just created.
        mContext.sendBroadcast(new Intent(
                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, result));
        return result;
    }

    private int getPlaylistId(Resources res) {
        Uri uri = MediaStore.Audio.Playlists.getContentUri("external");
        final String[] ids = new String[]{
                MediaStore.Audio.Playlists._ID
        };
        final String where = MediaStore.Audio.Playlists.NAME + "=?";
        final String[] args = new String[]{"My voice call"};
        Cursor cursor = query(uri, ids, where, args, null);
        if (cursor == null) {
            Log.e(TAG, "error, query returns null");
        }
        int id = -1;
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                id = cursor.getInt(0);
            }
            cursor.close();
        }
        return id;
    }

    /**
     * Add the given audioId to the play list with the given playlistId; and
     * maintain the play_order in the play list.
     */
    private void addToPlaylist(ContentResolver resolver, int audioId, long playlistId) {
        String[] cols = new String[]{
                "count(*)"
        };
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        Cursor cur = resolver.query(uri, cols, null, null, null);
        cur.moveToFirst();
        final int base = cur.getInt(0);
        cur.close();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER,
                Integer.valueOf(base + audioId));
        values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, audioId);
        uri = resolver.insert(uri, values);
        if (DBG)
            Log.d(TAG, "addToPlaylist:uri=" + uri);
    }

    /**
     * Create a playlist with the given default playlist name, if no such
     * playlist exists.
     */
    private Uri createPlaylist(Resources res, ContentResolver resolver) {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Audio.Playlists.NAME, "My voice call");
        Uri uri = resolver.insert(MediaStore.Audio.Playlists.getContentUri("external"), cv);
        if (uri == null) {
            new AlertDialog.Builder(mContext).setTitle(R.string.app_name)
                    .setMessage(R.string.error_mediadb_new_record)
                    .setPositiveButton(R.string.ok, null).setCancelable(false)
                    .show();
            Log.e(TAG, "createPlaylist: uri == null");
        }
        return uri;
    }

    /**
     * A simple utility to do a query into the databases.
     */
    private Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                         String sortOrder) {
        try {
            ContentResolver resolver = mContext.getContentResolver();
            if (resolver == null) {
                return null;
            }
            return resolver.query(uri, projection, selection, selectionArgs,
                    sortOrder);
        } catch (UnsupportedOperationException ex) {
            return null;
        }
    }

    /* SPRD: Naming record file with number and sim card feature. @{ */
    private int getSimSlotIndex() {
        Call call = (CallList.getInstance().getActiveCall() == null) ? null :
                CallList.getInstance().getActiveCall().getTelecomCall();

        if (call != null && call.getDetails() != null
                && call.getDetails().getAccountHandle() != null) {
            String iccId = call.getDetails().getAccountHandle().getId();
             List<SubscriptionInfo> result = DialerUtils
                     .getActiveSubscriptionInfoList(mContext);

             if (result != null) {
                 for (SubscriptionInfo subInfo : result) {
                     if (iccId != null && subInfo != null && iccId.equals(subInfo.getIccId())) {
                         return subInfo.getSimSlotIndex();
                     }
                 }
             }
         }
         return INVALID_SIM_SLOT_INDEX;
    }

    private String getNumber() {
        DialerCall call = CallList.getInstance().getActiveCall();
        if (call != null && call.getNumber() != null) {
            String name = getContactNameByNumber(call.getNumber());
            if (name != null) {
                return name + DEFAULT_DECOLLATOR + call.getNumber();
            } else {
                return call.getNumber();
            }
        }
        return null;
    }
    /* @} */

    public String getContactNameByNumber(String number) {
        String name = null;

        Uri uri = Uri.parse(ContactsContract.AUTHORITY_URI + "/data/phones/filter/" + number);
        ContentResolver resolver = mContext.getContentResolver();
        try {
            final Cursor cursor = resolver.query(uri, new String[]{"display_name"}, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0);
                    }
                } finally {
                    cursor.close();
                }
            }
            return name;
        } catch (SQLiteException e) {
            Log.e(TAG, "SQLiteException when query contacts.");
            return name;
        }
    }

    /*  Voice Memo inform feature. Plays the DTMF tone @{ */
    private void playDtmfTone() {
        Log.i(TAG, "setParameters play_sprd_record_tone = 1.");
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setParameters("play_sprd_record_tone=1");

    }
    /* @} */

    /*  SPRD: Add for bug711372 @{ */
    public void registerExternalStorageStateListener(Context context) {
        if (mSDCardMountEventReceiver == null) {
            mSDCardMountEventReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null) {
                        return;
                    }
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        String path = null;
                        if (intent.getData() != null) {
                            path = intent.getData().getPath();
                        }
                        Log.d(TAG, "Media eject path =" + path);
                        if (!TextUtils.isEmpty(mBaseRoot) && !TextUtils.isEmpty(path)
                                // UNISOC: add for bug915133
                                && mBaseRoot.equals(EnvironmentEx.getExternalStoragePath().getPath() + "/Android/media/com.android.dialer")
                                && mBaseRoot.startsWith(path)) {
                            stop();
                            return;
                        }
                    }
                    boolean hasSdcard = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
                    if (!hasSdcard) {
                        stop();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addDataScheme("file");
            if (context != null) {
                context.registerReceiver(mSDCardMountEventReceiver, iFilter);
            }
        }
    }

    public void unRegisterExternalStorageStateListener(Context context) {
        if (mSDCardMountEventReceiver != null && context != null) {
            context.unregisterReceiver(mSDCardMountEventReceiver);
            mSDCardMountEventReceiver = null;
        }
    }
    /* @} */
}

