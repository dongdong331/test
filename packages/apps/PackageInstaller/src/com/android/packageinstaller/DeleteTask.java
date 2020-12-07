package com.android.packageinstaller;

import java.io.File;
import java.util.ArrayList;
import android.content.ContentProviderOperation;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import android.content.Context;

public class DeleteTask extends AsyncTask<File, Integer, Boolean> {
    private ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
    private File mFile;
    private Context mContext;
    private Boolean isFileDeleted;
    private final String TAG = "DeleteTask";
    public DeleteTask(Context context ,File file) {
        mContext = context;
        mFile = file;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected Boolean doInBackground(File... params) {
        try {
            isFileDeleted = mFile.delete();
        } catch (Exception e) {
        }
        if (isFileDeleted) {
            ops.add(ContentProviderOperation.newDelete(MediaStore.Files.getContentUri("external"))
                    .withSelection(MediaStore.Files.FileColumns.DATA + "=?", new String[] { mFile.getAbsolutePath() })
                    .build());
            try {
                mContext.getContentResolver().applyBatch("media", ops);
            } catch (Exception e) {
                Log.w(TAG,"media db applyBatch delete failed");
                mContext.getContentResolver().delete(MediaStore.Files.getContentUri("external"),
                MediaStore.Files.FileColumns.DATA + "=?", new String[] { mFile.getAbsolutePath() });
            }
            return true;
        } else {
            return false;
        }
    }
}
