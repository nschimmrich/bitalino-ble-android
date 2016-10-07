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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.movisens.smartgattlib.Characteristic;
import com.movisens.smartgattlib.Descriptor;
import com.movisens.smartgattlib.characteristics.HeartRateMeasurement;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a BITalino BLE (r)evolution
 * (a GATT server).
 */
public class BITalinoBLEService extends Service {
    private final static String TAG = BITalinoBLEService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    // default state is disconnected
    private int mConnectionState = STATE_DISCONNECTED;

    private boolean mAcquiring = false;

    public final static String ACTION_GATT_CONNECTED =
            "com.bitalino.ble.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.bitalino.ble.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.bitalino.ble.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.bitalino.ble.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.bitalino.ble.EXTRA_DATA";

    // BITalino BLE UUIDs
    private static final UUID UUID_EXCHANGE_DATA_SERVICE = UUID.fromString("c566488a-0882-4e1b-a6d0-0b717e652234");
    private static final UUID UUID_CHARACTERISTIC_COMMANDS = UUID.fromString("4051eb11-bf0a-4c74-8730-a48f4193fcea");
    private static final UUID UUID_CHARACTERISTIC_FRAMES = UUID.fromString("40fdba6b-672e-47c4-808a-e529adff3633");


    // Implements callback methods for GATT events that the app cares about. For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    // TODO write to digital port
    public enum DigitalPort {
        ONE,
        TWO;
    }

    public boolean writeToPort(final DigitalPort port, final boolean value) {
        // TODO replace this with exceptions
        boolean result = false;
        if (mConnectionState == STATE_CONNECTED) {
            BluetoothGattService s = mBluetoothGatt.getService(UUID_EXCHANGE_DATA_SERVICE);
            if (s == null) {
                Log.e(TAG, "BITalino BLE data exchange service not found.");
            } else {
                final BluetoothGattCharacteristic c = s.getCharacteristic(UUID_CHARACTERISTIC_COMMANDS);
                c.setValue(0x2, BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                result = mBluetoothGatt.writeCharacteristic(c);
            }
        } else {
            Log.e(TAG, "Device is disconnected.");
        }

        return result;
    }

    public boolean startAcquisition(){
        // TODO replace this with exceptions
        boolean result = false;
        if (mConnectionState == STATE_CONNECTED) {
            BluetoothGattService s = mBluetoothGatt.getService(UUID_EXCHANGE_DATA_SERVICE);
            if (s == null) {
                Log.e(TAG, "BITalino BLE data exchange service not found.");
            } else {
                final BluetoothGattCharacteristic c = s.getCharacteristic(UUID_CHARACTERISTIC_COMMANDS);
                c.setValue(0x2, BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                result = mBluetoothGatt.writeCharacteristic(c);
                if (result) {
                    mAcquiring = true;
                }
            }
        } else {
            Log.e(TAG, "Device is disconnected.");
        }

        return result;
    }

    public boolean stopAcquisition(){
        // TODO replace this with exceptions
        boolean result = false;
        if (mConnectionState == STATE_CONNECTED) {
            BluetoothGattService s = mBluetoothGatt.getService(UUID_EXCHANGE_DATA_SERVICE);
            if (s == null) {
                Log.e(TAG, "BITalino BLE data exchange service not found.");
            } else {
                final BluetoothGattCharacteristic c = s.getCharacteristic(UUID_CHARACTERISTIC_COMMANDS);
                c.setValue(0x0, BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                result = mBluetoothGatt.writeCharacteristic(c);
                if (result) {
                    mAcquiring = false;
                }
            }
        } else {
            Log.e(TAG, "Device is disconnected.");
        }

        return result;
    }

    public boolean isConnected(){
        return mConnectionState == STATE_CONNECTED;
    }

    public boolean isAcquiring(){
        return mAcquiring;
    }

    // TODO read from analog ports

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // Only take care of BITalino BLE characteristics.
        final byte[] data = characteristic.getValue();
        if (UUID_CHARACTERISTIC_FRAMES.equals(characteristic.getUuid())) {
            // TODO
            HeartRateMeasurement hrm = new HeartRateMeasurement(data); // Interpret
            // Characteristic
            intent.putExtra(EXTRA_DATA, "HR: " + hrm.getHr() + "bpm" + ", EE: "
                    + hrm.getEe() + "kJ" + ", Status: " + hrm.getSensorWorn());
        } else {
            // For all other profiles, writes the data formatted in HEX.
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BITalinoBLEService getService() {
            return BITalinoBLEService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

//        // Previously connected device.  Try to reconnect.
//        if (mDeviceAddress != null && address.equals(mDeviceAddress)
//                && mBluetoothGatt != null) {
//            Log.d(TAG, "Connecting to " + mDeviceAddress + "...");
//            if (mBluetoothGatt.connect()) {
//                mConnectionState = STATE_CONNECTING;
//                return true;
//            } else {
//                return false;
//            }
//        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mDeviceAddress = address;
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (Characteristic.HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(Descriptor.CLIENT_CHARACTERISTIC_CONFIGURATION);
            descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
}
