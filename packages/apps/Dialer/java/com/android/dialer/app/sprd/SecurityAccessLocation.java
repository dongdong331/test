package com.android.dialer.app.sprd;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import com.android.dialer.app.DialtactsActivity;
import com.android.dialer.R;

public class SecurityAccessLocation extends Activity {
    private AlertDialog.Builder mLocationDialog;
    private Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;

        mLocationDialog = new AlertDialog.Builder(SecurityAccessLocation.this);
        mLocationDialog.setCancelable(false);
        mLocationDialog.setTitle(null)
                .setMessage(mContext.getResources().getText(R.string.location_permission_message))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int which) {
                        Intent intent = new Intent(SecurityAccessLocation.this,
                                DialtactsActivity.class);
                        Bundle bundle = new Bundle();
                        bundle.putString("result", "1");
                        intent.putExtras(bundle);
                        SecurityAccessLocation.this.startActivity(intent);
                        SecurityAccessLocation.this.finish();
                    }
                })
                .setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                SecurityAccessLocation.this.finish();
                            }
                        });
        AlertDialog dialog = mLocationDialog.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }
}
