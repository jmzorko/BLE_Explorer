package com.example.ble_explorer

import android.bluetooth.BluetoothDevice
import android.os.Parcelable

data class DeviceScanResult(
    var rssi: Int,
    var device: BluetoothDevice
)
