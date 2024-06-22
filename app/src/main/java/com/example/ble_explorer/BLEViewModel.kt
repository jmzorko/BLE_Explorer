package com.example.ble_explorer

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.ktx.suspend
import no.nordicsemi.android.ble.observer.ConnectionObserver

class BLEViewModel(private val ctx: Context) : ViewModel() {
    var devicesState = mutableStateListOf<DeviceScanResult>()
    var connectState = mutableStateOf<Int>(0)
    var connectedAddress = mutableStateOf<String>("")
    var connectedAddresses = mutableStateListOf<String>()
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
                connectedAddresses.filter { it != device.address }
                connectedAddresses.add(device.address)
            }

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                Log.d(TAG, "connection observer reports ${bleManager.connectionState}")
                connectState.value = bleManager.connectionState
                connectedAddress.value = ""
                connectedAddresses.filter { it != device.address }
            }

            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                Log.d(TAG, "connection observer reports ${bleManager.connectionState}")
                connectState.value = bleManager.connectionState
                connectedAddress.value = ""
                connectedAddresses.filter { it != device.address }
            }

            override fun onDeviceConnecting(device: BluetoothDevice) {
                Log.d(TAG, "connection observer reports ${bleManager.connectionState}")
                connectState.value = bleManager.connectionState
                connectedAddress.value = ""
                connectedAddresses.filter { it != device.address }
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

    fun saveDevice(device: BluetoothDevice) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putString("save_device", device.address)
            apply()
        }
    }

    fun forgetSavedDevice() {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            remove("save_device")
            apply()
        }
    }

    fun savedDevice() : String? {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("save_device", null)
    }

    fun update(result: ScanResult) {
        var dev = DeviceScanResult(result.rssi, result.device)
        val found = devicesState.find {
            it.device.address.equals(dev.device.address)
        }
        if (found == null) {
            devicesState.add(dev)
        } else {
            found.rssi = result.rssi    // FIXME results jump around in the list due to changing RSSI. Try only using the 10s digit of RSSI to sort.
        }

        sort()

        /*if (connectedAddress.value == null) {
            savedDevice()?.let {
                if (result.device.address.equals(it)) {
                    connect(result.device)
                }
            }
        }*/ // FIXME not the correct place for this code as update() can get called often before a device connects
    }

    fun connect(device: BluetoothDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bleManager?.let {
                    bleManager.connect(device)
                        .retry(3, 100)
                        .timeout(15_000)
                        .useAutoConnect(true)
                        .suspend()

                    batteryLevel.value = bleManager.getBatteryLevel()!!
                }
            } catch (e: Exception) {
                //connectState = bleManager?.connectionState ?: BluetoothGatt.STATE_DISCONNECTED
            }
        }
    }

    fun sort() {
        devicesState.sortWith(rssiComparator)
    }

    companion object {
        private const val TAG = "BLEViewModel"
        private const val PREFS = "BLE_Explorer"
    }
}

private val rssiComparator = Comparator<DeviceScanResult> { left, right ->
    right.rssi.compareTo(left.rssi)
}