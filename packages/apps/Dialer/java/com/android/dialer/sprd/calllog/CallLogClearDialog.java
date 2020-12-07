package com.android.dialer.sprd.calllog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.widget.Toast;

import com.android.dialer.R;

/**
 * Dialog that clears the call log after confirming with the user
 * add by sprd
 */
public class CallLogClearDialog {
    /**
     * Preferred way to show this dialog
     */
    public Dialog show(Context cox, Runnable run, boolean is_all) {
        ClickListener listener = new ClickListener(cox, run);
        AlertDialog.Builder builder = new AlertDialog.Builder(cox);
        builder.setTitle(R.string.delete_calls);
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        if (is_all) {
            builder.setMessage(R.string.delete_all_calls_confirm);
        } else {
            builder.setMessage(R.string.delete_selected_calls);
        }
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, listener);
        builder.setCancelable(true);
        builder.create();
        return builder.show();
    }

    class ClickListener implements DialogInterface.OnClickListener {
        private Context cContext;
        private Runnable cRunnable;

        public ClickListener(Context context, Runnable run) {
            cContext = context;
            cRunnable = run;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            final ProgressDialog progressDialog = ProgressDialog.show(cContext,
                    cContext.getString(R.string.clearCallLogProgress_title),
                    "", true, false);
            progressDialog.setOwnerActivity((Activity) cContext);
            final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    cRunnable.run();
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    Toast.makeText(cContext, R.string.delete_success, Toast.LENGTH_SHORT).show();
                    /* SPRD: add for bug 730145 @{ */
                    if (progressDialog != null && progressDialog.isShowing()) {
                        Activity activity = progressDialog.getOwnerActivity();
                        if (activity != null && !activity.isDestroyed()) {
                            progressDialog.dismiss();
                        }
                    }
                    /* @} */
                }
            };
            progressDialog.show();
            task.execute();
        }
    }

    ;
}
