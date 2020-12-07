/*
 * create by spreadst
 */

package com.sprd.settings;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;
import android.util.Log;
import com.android.settings.TetherSettings;

import com.android.settings.R;

public class SprdChooseOS extends Activity implements AdapterView.OnItemSelectedListener, OnClickListener{

    private UsbManager mUsbManager;

    private Button mCancelButton;
    private Button mNextButton;
    private Spinner mSpinner;
    private View mOSView;
    private View mNextView;
    private ArrayAdapter<String> mAdapter ;
    private String [] mOSArray;
    private String [] mIPArray;
    private String resultIP;
    private boolean isGuidance = false;
    private BroadcastReceiver mPowerDisconnectReceiver = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chooseos);
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        mCancelButton = (Button) findViewById(R.id.cancel_button);
        mNextButton = (Button) findViewById(R.id.next_button);
        mSpinner = (Spinner) findViewById(R.id.os_spinner);
        mOSView = (View) findViewById(R.id.os);
        mNextView = (View) findViewById(R.id.next);
        mOSArray = getResources().getStringArray(R.array.os_string_array);
        mIPArray = getResources().getStringArray(R.array.os_ip_values);
        mAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,mOSArray);

        mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner.setAdapter(mAdapter);
        mSpinner.setOnItemSelectedListener(this);
        mCancelButton.setOnClickListener(this);
        mNextButton.setOnClickListener(this);
        mNextButton.setEnabled(false);

        mPowerDisconnectReceiver = new PowerDisconnectReceiver();
        registerReceiver(mPowerDisconnectReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mPowerDisconnectReceiver);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        resultIP = mIPArray[position];
        if (position == 0) {
            mNextButton.setEnabled(false);
        } else {
            mNextButton.setEnabled(true);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        //
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(this, TetherSettings.class);
        switch (v.getId()) {
            case R.id.next_button:
                if (isGuidance) {
                    intent.putExtra("usb_rndis_ip_address", resultIP);
                    setResult(Activity.RESULT_OK, intent);
                    finish();
                } else {
                    isGuidance = true;
                    updateUI(true);
                }
                break;

            case R.id.cancel_button:
                if (isGuidance) {
                    isGuidance = false;
                    updateUI(false);
                } else {
                    setResult(Activity.RESULT_CANCELED, intent);
                    finish();
                }
                break;
        }
    }

    private void updateUI(boolean toNext) {
        if (toNext) {
            mOSView.setVisibility(View.GONE);
            mNextView.setVisibility(View.VISIBLE);
            mCancelButton.setText(R.string.usb_pc_back);
            mNextButton.setText(R.string.usb_pc_ok);
        } else {
            mOSView.setVisibility(View.VISIBLE);
            mNextView.setVisibility(View.GONE);
            mCancelButton.setText(R.string.usb_pc_cancel);
            mNextButton.setText(R.string.usb_pc_next);
        }
    }

    private class PowerDisconnectReceiver extends BroadcastReceiver {
        public void onReceive(Context content, Intent intent) {
            int plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            if (plugType == 0) {
                SprdChooseOS.this.setResult(Activity.RESULT_CANCELED, intent);
                SprdChooseOS.this.finish();
            }
        }
    }
}
