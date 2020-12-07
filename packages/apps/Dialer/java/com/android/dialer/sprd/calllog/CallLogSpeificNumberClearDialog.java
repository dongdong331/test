package com.android.dialer.sprd.calllog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.provider.CallLog.Calls;
import android.widget.Toast;

import com.android.dialer.R;
import com.android.dialer.common.LogUtil;

/**
 * Dialog that clears the call log of specific number after confirming with the user
 */
public class CallLogSpeificNumberClearDialog {

  private final String TAG = "CallLogSpeificNumberClearDialog";
  /** Preferred way to show this dialog */
  public Dialog show(Context cox, String number) {
    ClickListener listener = new ClickListener(cox, number);
    AlertDialog.Builder builder = new AlertDialog.Builder(cox);
    builder.setTitle(R.string.delete_calls);
    builder.setIconAttribute(android.R.attr.alertDialogIcon);
    builder.setMessage(R.string.delete_all_calls_specific_number_confirm);
    builder.setNegativeButton(android.R.string.cancel, null);
    builder.setPositiveButton(android.R.string.ok, listener);
    builder.setCancelable(true);
    builder.create();
    return builder.show();
  }

  class ClickListener implements DialogInterface.OnClickListener {
    private Context cContext;
    private String cNumber;

    public ClickListener(Context context, String number) {
      cContext = context;
      cNumber = number;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
      final ProgressDialog progressDialog = ProgressDialog.show(cContext,
            cContext.getString(R.string.clearCallLogProgress_title),
            "", true, false);
      final AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
        @Override
        protected Boolean doInBackground(Void... params) {
          ContentResolver cr = cContext.getContentResolver();
          try {
            int numberRowDelete = cr.delete(Calls.CONTENT_URI, Calls.NUMBER + " =?", new String[] {cNumber});
            if (numberRowDelete == 0) {
              return Boolean.FALSE;
            } else {
              return Boolean.TRUE;
            }
          } catch (SQLiteException ex) {
            LogUtil.e(TAG, "delete error : " + ex.getMessage());
            return Boolean.FALSE;
          }
        }

        @Override
        protected void onPostExecute(Boolean result) {
          progressDialog.dismiss();
          if (result == Boolean.TRUE) {
            Toast.makeText(cContext, R.string.delete_success, Toast.LENGTH_SHORT).show();
          } else {
            Toast.makeText(cContext, R.string.delete_unsuccess, Toast.LENGTH_SHORT).show();
          }
        }
      };
      progressDialog.show();
      task.execute();
    }
  };
}
