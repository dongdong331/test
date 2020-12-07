package com.android.bluetooth.opp;

import android.app.Activity;

import java.io.File;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.EnvironmentEx;
import android.os.storage.StorageVolume;
import android.os.storage.StorageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import java.util.List;
import android.util.Log;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.Toast;
import com.android.bluetooth.R;


public class GrantActivity extends Activity {

    private final String TAG = "BluetoothOppGrantActvity";

    private final int REQUEST_CODE = 1;
    private File mBtFile;
    private BluetoothOppService mService;

    private static final int GRANT_TIMEMOUT = 2;
    private static final int GRANT_TIMEMOUT_DELAY = 10000;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case GRANT_TIMEMOUT:
                    BluetoothOppService service = BluetoothOppService.getBluetoothOppService();
                    if (service != null) {
                        service.continueTrans(null);
                    }
                    finish();
                    finishActivity(REQUEST_CODE);
                    break;
                default:
                    break;
            }
        };
    };
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setVisible(false);
        setTitle(null);
        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        params.alpha = 0.0f;
        window.setAttributes(params);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(GRANT_TIMEMOUT), GRANT_TIMEMOUT_DELAY);
        requestAccessPer();
    }

    private void requestAccessPer() {
        for (StorageVolume volume:getVolumes(this)) {
            File volumePath = volume.getPathFile();
            if (!volume.isPrimary() && (volumePath != null) &&
                    EnvironmentEx.getExternalStoragePathState().equals(Environment.MEDIA_MOUNTED)
                    && EnvironmentEx.getExternalStoragePath().equals(volumePath)){
                mBtFile = new File(volumePath.toString()+"/bluetooth");
                Intent intent = volume.createAccessIntent(null);
                if (intent != null) {
                    startActivityForResult(intent,REQUEST_CODE);
                    break;
                }
            }
        }
    }

    private Uri receiveFile(File file,Uri uri) {
        Uri doc;
        if (file.exists()) {
            doc = DocumentsContract. buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)+"/bluetooth");
        } else {
            doc = createDir(getContentResolver(),uri,"bluetooth");
        }
        return doc;
    }

    public Uri createDir(ContentResolver cr, Uri uri, String dirName) {
        Uri dir;
        try {
            Uri doc = DocumentsContract. buildDocumentUriUsingTree(uri,DocumentsContract.getTreeDocumentId(uri));
            dir = DocumentsContract.createDocument(cr, doc,DocumentsContract.Document.MIME_TYPE_DIR,dirName);
        }catch (Exception e) {
            Log.e(TAG,"create bluetooth dir fail",e);
            dir = null;
        }
        return dir;
    }

    private List<StorageVolume> getVolumes(Context context) {
        final StorageManager sm = (StorageManager)context.getSystemService(Context.STORAGE_SERVICE);
        final List<StorageVolume> volumes = sm.getStorageVolumes();
        return volumes;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mHandler.removeMessages(GRANT_TIMEMOUT);
        BluetoothOppService service = BluetoothOppService.getBluetoothOppService();
        if (service == null) {
            finish();
            return;
        }
        Log.d(TAG, "onActivityResult");
        if (resultCode == Activity.RESULT_OK) {
            if(requestCode == REQUEST_CODE){
                Uri uri = null;
                if (data != null && data.getData() != null) {
                    uri = data.getData();
                    final ContentResolver resolver = getContentResolver();
                    final int modeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    resolver.takePersistableUriPermission(uri, modeFlags);
                    uri = receiveFile(mBtFile, uri);
                    service.continueTrans(uri);
                }
            }
        } else {
            service.continueTrans(null);
        }
        finish();
    }
}

