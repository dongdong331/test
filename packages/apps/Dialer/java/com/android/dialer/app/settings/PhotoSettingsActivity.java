package com.android.dialer.app.settings;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.EnvironmentEx;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Toast;

import com.android.dialer.app.R;
import com.android.dialer.util.PermissionsUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * photo setting activity
 */
public class PhotoSettingsActivity extends PreferenceActivity {

    private final static String TAG = "PhotoSettingsActivity";
    private final static String KEY_DEFAULT_PHOTO = "default_photo";
    private final static String KEY_SELECT_PHOTO = "photo_select";
    private final static String KEY_SELECT_GALLERY = "select_gallery";
    private final static String HORIZONTAL_PHOTO = "horizontal_photo";
    private final static String VERTICAL_PHOTO = "vertical_photo";
    private final static int INVALID_PHOTO = -1;
    private final static int SELECT_DEFAULT_PHOTO = 0;
    private final static int SELECT_PHOTO_FROM_GALLERY = 1;
    private final static int CROP_VERTICAL_PHOTO = 1;
    private final static int CROP_HORIZONTAL_PHOTO = 2;
    private final static String PHOTO_SAVE_DIR = getInternalStoragePath();
    // = EnvironmentEx.getInternalStoragePath().getPath() + "/.video";
    private final static String HORIZONTAL_PHOTO_FILENAME = "horizontal.raw";
    private final static String VERTICAL_PHOTO_FILENAME = "vertical.raw";
    private final static String DEFAULT_VERTICAL_PHOTO_FILENAME = "default_vertical.raw";
    private final static int SAVE_PHOTO_SUCCESS = 0;
    private final static int SAVE_PHOTO_FAILED = 1;
    private final static String PREFERENCE_PACKAGE = "com.android.dialer";
    private final static String VIDEO_CALL_MEETING = "video_call_meeting";
    private final static int REQUEST_PERMISSION_CAMERA_CODE =1001;
    private static final int BYTE_LENGTH = 1024;
    private static final int ORDER_FIRST = 0;
    private static final int ORDER_SEC = 1;
    private static final int VALUE_LONG = 320;
    private static final int VALUE_SHORT = 240;
    private static final int ERROR = -1;
    private static final int ASPECT_SHORT = 3;
    private static final int ASPECT_LONG = 4;

    private String mPhotoPreSavedDir;
    protected SharedPreferences mPreferences;
    private PhotoSelectPreference mDefaultPreference;
    private PhotoSelectPreference mSelectPhotoPreference;
    private boolean mSelectHorizontalPhoto = false;
    private Bitmap mHorizontalBitmap;
    private Bitmap mVerticalBitmap;
    private boolean mSelectPhotoForTrunOnSwitch = false;
    private boolean mIsPhotoSavedFail = false;

    private Thread mSavePhotoThread;
    private Thread mSaveDefaultPhotoThread;

    private Uri uriTempFile;

    /**
     * temp porting code for Android8.1: Replace EnvironmentEx.getInternalStoragePath().getPath()
     *TODO
     */
    public static String getInternalStoragePath() {
        String path = "/storage/emulated/0";
        return path;
    }


    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SAVE_PHOTO_SUCCESS:
                    if (mSelectPhotoForTrunOnSwitch) {
                        mSelectPhotoPreference.saveRaioButtonState();
                        mSelectPhotoForTrunOnSwitch = false;
                    }
                    Toast.makeText(getApplicationContext(),
                            getResources().getString(R.string.save_photo_successful),
                            Toast.LENGTH_SHORT).show();
                    break;
                case SAVE_PHOTO_FAILED:
                    if (mSelectPhotoForTrunOnSwitch) {
                        mDefaultPreference.saveRaioButtonState();
                        mSelectPhotoForTrunOnSwitch = false;
                    }
                    Toast.makeText(getApplicationContext(),
                            getResources().getString(R.string.save_photo_failed),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!PermissionsUtil.hasPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)){
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_PERMISSION_CAMERA_CODE);
        }

        mPreferences = getPreferences();
        Intent intent = getIntent();
        mSelectHorizontalPhoto = intent.getBooleanExtra(HORIZONTAL_PHOTO, false);
        createPreferences();
        mPhotoPreSavedDir = this.getFilesDir().getPath();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CAMERA_CODE && grantResults[0] == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, R.string.permission_set_meeting_photo, Toast.LENGTH_SHORT).show();
            finish();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSaveDefaultPhotoThread != null) {
            // Stop the thread; we are finished with it.
            mSaveDefaultPhotoThread.interrupt();
            mSaveDefaultPhotoThread = null;
        }
        File newFilePath = new File(PHOTO_SAVE_DIR);
        if (!newFilePath.exists()) {
            newFilePath.mkdirs();
        }

        final File deafultPhotoFile = new File(PHOTO_SAVE_DIR, DEFAULT_VERTICAL_PHOTO_FILENAME);
        if (!deafultPhotoFile.exists()) {
            mSaveDefaultPhotoThread = new Thread() {
                @Override
                public void run() {
                    InputStream is = null;
                    FileOutputStream outStram = null;
                    try {
                        deafultPhotoFile.createNewFile();
                        int byteRead = 0;
                        is = getResources().openRawResource(R.raw.vertical);
                        outStram = new FileOutputStream(deafultPhotoFile);
                        byte[] buffer = new byte[BYTE_LENGTH];
                        while ((byteRead = is.read(buffer)) != ERROR) {
                            outStram.write(buffer, 0, byteRead);
                        }
                    } catch (FileNotFoundException ex){
                        ex.printStackTrace();
                        Log.e(TAG, "open file failed." + ex.getMessage());
                    } catch (IOException e){
                        e.printStackTrace();
                        Log.e(TAG, "write pic data failed." + e.getMessage());
                    } finally {
                        try {
                            if (is != null) {
                                is.close();
                            }
                            if (outStram != null) {
                                outStram.close();
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "close FileOutputStream failed." + e.getMessage());
                        }
                    }

                }
            };
            mSaveDefaultPhotoThread.start();
        }

    }

    private void createPreferences() {
        addPreferencesFromResource(R.layout.video_call_meeting_photo_select_layout_ex);

        PreferenceScreen photoPreferenceScreen = (PreferenceScreen) findPreference(KEY_SELECT_PHOTO);
        photoPreferenceScreen.setTitle(mSelectHorizontalPhoto
                ? R.string.select_horizontal_photo : R.string.select_vertical_photo);
        setTitle(mSelectHorizontalPhoto
                ? R.string.select_horizontal_photo : R.string.select_vertical_photo);
        mDefaultPreference = (PhotoSelectPreference) new PhotoSelectPreference(this, true);
        mDefaultPreference.setOrder(ORDER_FIRST);
        mDefaultPreference.setKey(KEY_DEFAULT_PHOTO);
        mDefaultPreference.setTitle(R.string.default_photo);
        photoPreferenceScreen.addPreference(mDefaultPreference);

        mSelectPhotoPreference = (PhotoSelectPreference) new PhotoSelectPreference(this, false);
        mSelectPhotoPreference.setOrder(ORDER_SEC);
        mSelectPhotoPreference.setKey(KEY_SELECT_PHOTO);
        mSelectPhotoPreference.setTitle(R.string.select_photo_from_gallery);
        photoPreferenceScreen.addPreference(mSelectPhotoPreference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "requestCode=" + requestCode + ", resultCode=" + resultCode);
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            Log.d(TAG, "fail due to resultCode = " + resultCode);
            if (mSelectPhotoForTrunOnSwitch) {
                mDefaultPreference.saveRaioButtonState();
                mSelectPhotoForTrunOnSwitch = false;
            }
            return;
        }
        try {
            Bitmap temp = BitmapFactory.decodeStream(getContentResolver().openInputStream(uriTempFile));
            if (requestCode == CROP_HORIZONTAL_PHOTO) {
//            mHorizontalBitmap = data.getParcelableExtra("data");
                mHorizontalBitmap = temp;
                setSubstitutePic(this, HORIZONTAL_PHOTO_FILENAME);
            } else if (requestCode == CROP_VERTICAL_PHOTO) {
//            mVerticalBitmap = data.getParcelableExtra("data");
                mVerticalBitmap = temp;
                setSubstitutePic(this, VERTICAL_PHOTO_FILENAME);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onHeaderClick(Header header, int position) {
        super.onHeaderClick(header, position);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    private void setSubstitutePic(final Context context, final String fileName){
        if (mSavePhotoThread != null) {
            mSavePhotoThread.interrupt();
            mSavePhotoThread = null;
        }
        mSavePhotoThread = new Thread() {
            @Override
            public void run() {
                Log.d(TAG, "save bitmap start");
                Bitmap bmap = (mSelectHorizontalPhoto ? mHorizontalBitmap : mVerticalBitmap);
                FileOutputStream fout = null;
                int dstPicWidth = (mSelectHorizontalPhoto ? VALUE_LONG : VALUE_SHORT);
                int dstPicHeight = (mSelectHorizontalPhoto ? VALUE_SHORT : VALUE_LONG);
                int pixel;
                final int height;
                final int width;
                try {
                    bmap = Bitmap.createScaledBitmap(bmap, dstPicWidth, dstPicHeight, true);
                    // SPRD: modify for bug756315
                    fout = context.openFileOutput(fileName, Context.MODE_PRIVATE);
                    ByteBuffer buffer = ByteBuffer.allocate(4 * dstPicWidth * dstPicHeight);
                    height = (dstPicHeight <= bmap.getHeight()) ? dstPicHeight : bmap.getHeight();
                    width = (dstPicWidth <= bmap.getWidth()) ? dstPicWidth : bmap.getWidth();
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            pixel = bmap.getPixel(x, y);
                            buffer.putInt(pixel);
                        }
                    }
                    fout.write(buffer.array());
                } catch (FileNotFoundException ex) {
                    mIsPhotoSavedFail = true;
                    Log.e(TAG, "open file failed." + ex.getMessage());
                } catch (IOException e) {
                    mIsPhotoSavedFail = true;
                    Log.e(TAG, "write pic data failed." + e.getMessage());
                } finally {
                    try {
                        if (fout != null) {
                            fout.close();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "close FileOutputStream failed." + e.getMessage());
                    }
                }
                bmap.recycle();
                bmap = null;
                copyPhoto();
                if (mIsPhotoSavedFail) {
                    mIsPhotoSavedFail = false;
                    mHandler.sendEmptyMessage(SAVE_PHOTO_FAILED);
                }

                Log.d(TAG, "save bitmap end");
            }
        };
        mSavePhotoThread.start();
    }

    private void copyPhoto() {
        String oldPath = mPhotoPreSavedDir + "/" + (mSelectHorizontalPhoto
                ? HORIZONTAL_PHOTO_FILENAME : VERTICAL_PHOTO_FILENAME);
        if (!Environment.MEDIA_MOUNTED.equals(EnvironmentEx.getInternalStoragePathState())) {
            mIsPhotoSavedFail = true;
            return;
        }
        Log.d(TAG, "copy photo to:" + PHOTO_SAVE_DIR);
        FileInputStream inStream = null;
        FileOutputStream outStram = null;
        try {
            File newFilePath = new File(PHOTO_SAVE_DIR);
            if (!newFilePath.exists()) {
                newFilePath.mkdirs();
            }
            inStream = new FileInputStream(oldPath);
            File oldFile = new File(mPhotoPreSavedDir, mSelectHorizontalPhoto
                    ? HORIZONTAL_PHOTO_FILENAME : VERTICAL_PHOTO_FILENAME);
            Log.d(TAG, "oldFile.exists() ? " + oldFile.exists());
            if (oldFile.exists()) {
                int byteRead = 0;
                File newFile = new File(PHOTO_SAVE_DIR, mSelectHorizontalPhoto
                        ? HORIZONTAL_PHOTO_FILENAME : VERTICAL_PHOTO_FILENAME);
                if (newFile.exists()) {
                    newFile.delete();
                    newFile.createNewFile();
                } else {
                    newFile.createNewFile();
                }
                outStram = new FileOutputStream(newFile);
                byte[] buffer = new byte[BYTE_LENGTH];
                while ((byteRead = inStream.read(buffer)) != ERROR) {
                    outStram.write(buffer, SELECT_DEFAULT_PHOTO, byteRead);
                }
                oldFile.delete();
                mHandler.sendEmptyMessage(SAVE_PHOTO_SUCCESS);
            }
        } catch (FileNotFoundException ex){
            ex.printStackTrace();
            mIsPhotoSavedFail = true;
            Log.e(TAG, "open file failed." + ex.getMessage());
        } catch (IOException e){
            mIsPhotoSavedFail = true;
            e.printStackTrace();
            Log.e(TAG, "write pic data failed." + e.getMessage());
        } finally {
            try {
                if (inStream != null) {
                    inStream.close();
                }
                if (outStram != null) {
                    outStram.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "close FileOutputStream failed." + e.getMessage());
            }
        }
    }

    private Intent getImageClipIntent(boolean isHorizontal) {
        Intent imageClipIntent = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imageClipIntent.setType("image/*");
        imageClipIntent.putExtra("crop", "true");
        if (isHorizontal) {
            imageClipIntent.putExtra("aspectX", ASPECT_LONG);
            imageClipIntent.putExtra("aspectY", ASPECT_SHORT);
            imageClipIntent.putExtra("outputX", VALUE_LONG);
            imageClipIntent.putExtra("outputY", VALUE_SHORT);
        } else {
            imageClipIntent.putExtra("aspectX", ASPECT_SHORT);
            imageClipIntent.putExtra("aspectY", ASPECT_LONG);
            imageClipIntent.putExtra("outputX", VALUE_SHORT);
            imageClipIntent.putExtra("outputY", VALUE_LONG);
        }
        imageClipIntent.putExtra("return-data", false);
        uriTempFile = Uri.parse("file://" + "/" + Environment.getExternalStorageDirectory().getPath() + "/" + "temp_image.jpg");
        imageClipIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriTempFile);
        imageClipIntent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
        return imageClipIntent;
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed");
        super.onBackPressed();
    }

    private SharedPreferences getPreferences() {
        Context packageContext = null;
        try {
            packageContext = this.createPackageContext(PREFERENCE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        if (packageContext != null) {
            return packageContext.getSharedPreferences(
                    VIDEO_CALL_MEETING, Context.MODE_MULTI_PROCESS);
        }
        return null;
    }

    public class PhotoSelectPreference extends Preference {

        Activity mActivity;
        boolean mIsDefaultPhoto;
        ImageView mImageView;
        RadioButton mRadioButton;

        public PhotoSelectPreference(PhotoSettingsActivity activity, boolean isDefaultPhoto) {
            super(activity);
            mActivity = activity;
            mIsDefaultPhoto = isDefaultPhoto;
            setLayoutResource(R.layout.photo_select_preference_ex);
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);
            mImageView = (ImageView) view.findViewById(R.id.photo_select_icon);
            if (!mIsDefaultPhoto) {
                mImageView.setImageResource(R.drawable.ic_bt_config);
                mImageView.setVisibility(View.VISIBLE);
            } else {
                mImageView.setVisibility(View.GONE);
            }
            mImageView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    mActivity.startActivityForResult(
                            getImageClipIntent(mSelectHorizontalPhoto),
                            mSelectHorizontalPhoto ? CROP_HORIZONTAL_PHOTO : CROP_VERTICAL_PHOTO);
                }
            });
            mRadioButton = (RadioButton) view.findViewById(R.id.radio_button);
            mRadioButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    Log.d(TAG, "onCheckedChanged isChecked = " + isChecked);
                    if (isChecked) {
                        if (mIsDefaultPhoto) {
                            saveRaioButtonState();
                        } else {
                            boolean isFileSelected = false;
                            File file = new File(PHOTO_SAVE_DIR,
                                    mSelectHorizontalPhoto ? HORIZONTAL_PHOTO_FILENAME
                                            : VERTICAL_PHOTO_FILENAME);
                            if (file.exists()) {
                                isFileSelected = true;
                            }
                            if (isFileSelected) {
                                saveRaioButtonState();
                            } else {
                                Intent intent = getImageClipIntent(mSelectHorizontalPhoto);
                                mSelectPhotoForTrunOnSwitch = true;
                                mActivity.startActivityForResult(intent,
                                        mSelectHorizontalPhoto ? CROP_HORIZONTAL_PHOTO
                                                : CROP_VERTICAL_PHOTO);
                            }
                        }
                    }
                }
            });
            updateRaioButtonState();
        }

        public void saveRaioButtonState() {
            if (mPreferences != null) {
                Editor editor = mPreferences.edit();
                editor.putInt(mSelectHorizontalPhoto ? HORIZONTAL_PHOTO : VERTICAL_PHOTO,
                        mIsDefaultPhoto ? SELECT_DEFAULT_PHOTO : SELECT_PHOTO_FROM_GALLERY);
                editor.commit();
                mDefaultPreference.updateRaioButtonState();
                mSelectPhotoPreference.updateRaioButtonState();
            }
        }

        public void updateRaioButtonState() {
            if (mRadioButton == null) return;
            if (mPreferences != null) {
                // SPRD: modify for bug756315
                int photoId = mPreferences.getInt(
                        mSelectHorizontalPhoto ? HORIZONTAL_PHOTO : VERTICAL_PHOTO,
                        SELECT_DEFAULT_PHOTO);
                int defaultPhotoId = mIsDefaultPhoto ?
                        SELECT_DEFAULT_PHOTO : SELECT_PHOTO_FROM_GALLERY;
                mRadioButton.setChecked(photoId == defaultPhotoId);
            }
        }
    }
}
