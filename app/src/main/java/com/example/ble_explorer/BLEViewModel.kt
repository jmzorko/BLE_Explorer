package com.example.ble_explorer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
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

    var scanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner

    var scanHandler = Handler(Looper.getMainLooper())
    var isScanning = false
    var scanCount = 0

    val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let { update(result) }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            scanCount --
        }
    }

    @SuppressLint("MissingPermission")
    fun startDeviceScan() {
        if (!isScanning) {
            Log.d("JMZ", "starting BLE scan")
            /*scanHandler.postDelayed({
                if (scanCount > 0) {
                    scanner.stopScan(scanCallback)
                    isScanning = false
                }
            }, 10000)*/

            isScanning = true
            val filters: MutableList<ScanFilter> = ArrayList()
            val scanFilterName = ScanFilter.Builder().setDeviceName(null).build()
            filters.add(scanFilterName)

            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
            scanner.startScan(filters, settings, scanCallback)
            //scanner.startScan(scanCallback)
            scanCount ++
        }
    }

    fun stopDeviceScan() {
        if (scanCount > 0) {
            scanner.stopScan(scanCallback)
            isScanning = false
        }
    }

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
            found.rssi = (Math.round((result.rssi / 10).toDouble()) * 10).toInt()    // so results don't jump around so much
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