package com.example.ble_explorer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import no.nordicsemi.android.ble.observer.ConnectionObserver

class BLEViewModel(private val ctx: Context) : ViewModel() {
    var devicesState = mutableStateListOf<DeviceScanResult>()
    var connectState = mutableStateOf<Int>(0)

    var bleManager = MyBleManager(context = ctx)

    init {
        bleManager.connectionObserver = object : ConnectionObserver {
            override fun onDeviceConnected(device: BluetoothDevice) {
                Log.d("JMZ", "connection observer reports ${bleManager.connectionState}")
                connectState.value = bleManager.connectionState
            }

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                Log.d("JMZ", "connection observer reports ${bleManager.connectionState}")
                connectState.value = bleManager.connectionState
            }

            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                Log.d("JMZ", "connection observer reports ${bleManager.connectionState}")
                connectState.value = bleManager.connectionState
            }

            override fun onDeviceConnecting(device: BluetoothDevice) {
                Log.d("JMZ", "connection observer reports ${bleManager.connectionState}")
                connectState.value = bleManager.connectionState
            }

            override fun onDeviceDisconnecting(device: BluetoothDevice) {
                Log.d("JMZ", "connection observer reports ${bleManager.connectionState}")
            }

            override fun onDeviceReady(device: BluetoothDevice) {
                Log.d("JMZ", "connection observer reports ${bleManager.connectionState}")
            }
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
}

private val rssiComparator = Comparator<DeviceScanResult> { left, right ->
    right.rssi.compareTo(left.rssi)
}