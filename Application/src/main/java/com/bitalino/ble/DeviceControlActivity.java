/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.bitalino.ble;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 1;

    private BluetoothAdapter mBluetoothAdapter;

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BITalinoBLEService mBITalinoBLEService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBITalinoBLEService = ((BITalinoBLEService.LocalBinder) service).getService();
            if (!mBITalinoBLEService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBITalinoBLEService = null;
            mConnected = false;
            updateConnectionState(com.bitalino.ble.R.string.disconnected);
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BITalinoBLEService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(com.bitalino.ble.R.string.connected);
                invalidateOptionsMenu();
            } else if (BITalinoBLEService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(com.bitalino.ble.R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBITalinoBLEService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBITalinoBLEService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBITalinoBLEService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
            };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(com.bitalino.ble.R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.bitalino.ble.R.layout.gatt_services_characteristics);

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, com.bitalino.ble.R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, com.bitalino.ble.R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mDeviceName = "BITalino";
        mDeviceAddress = "a0:e6:f8:d4:be:a8".toUpperCase();

        // Sets up UI references.
        ((TextView) findViewById(com.bitalino.ble.R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(com.bitalino.ble.R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(com.bitalino.ble.R.id.connection_state);
        mDataField = (TextView) findViewById(com.bitalino.ble.R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BITalinoBLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mConnected = false;
        invalidateOptionsMenu();

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                final Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);

        Log.d(TAG, "pausing");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBITalinoBLEService = null;

        Log.d(TAG, "destroying");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(com.bitalino.ble.R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(com.bitalino.ble.R.id.menu_connect).setVisible(false);
            menu.findItem(com.bitalino.ble.R.id.menu_disconnect).setVisible(true);
            menu.findItem(com.bitalino.ble.R.id.menu_start_acquisition).setVisible(!mBITalinoBLEService.isAcquiring());
            menu.findItem(com.bitalino.ble.R.id.menu_stop_acquisition).setVisible(mBITalinoBLEService.isAcquiring());
        } else {
            menu.findItem(com.bitalino.ble.R.id.menu_connect).setVisible(true);
            menu.findItem(com.bitalino.ble.R.id.menu_start_acquisition).setVisible(false);
            menu.findItem(com.bitalino.ble.R.id.menu_stop_acquisition).setVisible(false);
            menu.findItem(com.bitalino.ble.R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case com.bitalino.ble.R.id.menu_connect:
                mBITalinoBLEService.connect(mDeviceAddress);
                return true;
            case com.bitalino.ble.R.id.menu_disconnect:
                if (mBITalinoBLEService.isAcquiring()) {
                    mBITalinoBLEService.stopAcquisition();
                }
                mBITalinoBLEService.disconnect();
                return true;
            case com.bitalino.ble.R.id.menu_start_acquisition:
                if (!mBITalinoBLEService.isAcquiring()) {
                    mBITalinoBLEService.startAcquisition();
                    invalidateOptionsMenu();
                }
                return true;
            case com.bitalino.ble.R.id.menu_stop_acquisition:
                if (mBITalinoBLEService.isAcquiring()) {
                    mBITalinoBLEService.stopAcquisition();
                    invalidateOptionsMenu();
                }
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BITalinoBLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BITalinoBLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BITalinoBLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BITalinoBLEService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
