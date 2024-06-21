package com.example.ble_explorer

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.observer.ConnectionObserver

class BLEViewModel(private val ctx: Context) : ViewModel() {
    var devicesState = mutableStateListOf<DeviceScanResult>()
    var connectState = mutableStateOf<Int>(0)
    var connectedAddress = mutableStateOf<String>("")
    var batteryLevel = mutableStateOf<Int>(0)

    var batteryStateCallback = object: DataReceivedCallback {
        override fun onDataReceived(device: BluetoothDevice, data: Data) {
            batteryLevel.value = data.getIntValue(Data.FORMAT_UINT8, 0)!!
            Log.d(TAG, "Battery level: ${batteryLevel.value}")
        }
    }

    var bleManager = MyBleManager(batteryStateCallback, context = ctx)

    init {
        bleManager.connectionObserver = object : ConnectionObserver {
            override fun onDeviceConnected(device: BluetoothDevice) {
                Log.d(TAG, "connection observer reports ${bleManager.connectionState}")
                connectState.value = bleManager.connectionState
                connectedAddress.value = device.address
            }

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                Log.d(TAG, "connection observer reports ${bleManager.connectionState}")
                connectState.value = bleManager.connectionState
                connectedAddress.value = ""
            }

            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                Log.d(TAG, "connection observer reports ${bleManager.connectionState}")
                connectState.value = bleManager.connectionState
                connectedAddress.value = ""
            }

            override fun onDeviceConnecting(device: BluetoothDevice) {
                Log.d(TAG, "connection observer reports ${bleManager.connectionState}")
                connectState.value = bleManager.connectionState
                connectedAddress.value = ""
            }

            override fun onDeviceDisconnecting(device: BluetoothDevice) {
                Log.d(TAG, "connection observer reports ${bleManager.connectionState}")
            }

            override fun onDeviceReady(device: BluetoothDevice) {
                Log.d(TAG, "connection observer reports ${bleManager.connectionState}")
            }
        }
    }

    fun clearDevices() {
        val found = devicesState.find {
            it.device.address.equals(connectedAddress.value)
        }?.clone()

        devicesState.clear()

        found?.let {
            devicesState.add(found)
        }
    }

    fun update(result: ScanResult) {
        var dev = DeviceScanResult(result.rssi, result.device)
        val found = devicesState.find {
            it.device.address.equals(dev.device.address)
        }
        if (found == null) {
            devicesState.add(dev)
        } else {
            found.rssi = result.rssi
        }

        sort()
    }

    fun sort() {
        devicesState.sortWith(rssiComparator)
    }

    companion object {
        private const val TAG = "BLEViewModel"
    }
}

private val rssiComparator = Comparator<DeviceScanResult> { left, right ->
    right.rssi.compareTo(left.rssi)
}