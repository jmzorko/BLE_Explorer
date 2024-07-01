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
import kotlin.math.abs


class BLEViewModel(private val ctx: Context) : ViewModel() {
    var devicesState = mutableStateListOf<DeviceScanResult>()
    var connectState = mutableStateOf<Int>(0)
    var connectedStates = mutableStateMapOf<String, Int>()
    var connectedAddress = mutableStateOf<String>("")
    var connectedAddresses = mutableStateListOf<String>()
    var batteryLevel = mutableStateOf<Int>(0)

    var scanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
    var bleManagerMap = mutableMapOf<String, MyBleManager>()

    var scanHandler = Handler(Looper.getMainLooper())
    var isScanning = false
    var scanStartedCount = 0
    var lastScanTimeScaled: Long = 0
    var scanStartedTimeSeconds = System.currentTimeMillis() / 1000

    val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val currentTimeScaled = System.currentTimeMillis() / TIME_SCALE
            if (currentTimeScaled > lastScanTimeScaled || currentTimeScaled < scanStartedTimeSeconds + FAST_SCAN_THRESHOLD_SECONDS) {
                lastScanTimeScaled = currentTimeScaled
                Log.d(TAG, "updating list")
                result?.let { update(result) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            scanStartedCount --
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
            scanStartedCount ++
        }
    }

    fun stopDeviceScan() {
        if (scanStartedCount > 0) {
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

    fun clearDevices() {
        var alreadyConnectedDevices = mutableListOf<DeviceScanResult>()

        devicesState.forEach { dev ->
            connectedAddresses.find { addr ->
                addr.equals(dev.device.address)
            }?.let {
                alreadyConnectedDevices.add(dev.clone())
            }
        }

        devicesState.clear()

        alreadyConnectedDevices.forEach { dev ->
            devicesState.add(dev)
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

        var shouldSort = false

        if (found == null) {
            devicesState.add(dev)
            shouldSort = true
        } else {
            if (abs(found.rssi - result.rssi) > RSSI_SCALE) {
                found.rssi = result.rssi
                shouldSort = true
            }
            /*val newRssiScaled = scaleRSSI(result.rssi)    // so results don't jump around so much
            val oldRssiScaled = scaleRSSI(found.rssi)
            if (newRssiScaled != oldRssiScaled) {
                found.rssi = newRssiScaled
                shouldSort = true
                Log.d(TAG, "orig scaled RSSI: ${oldRssiScaled} new scaled RSSI: ${newRssiScaled}, re-sorting")
            }*/
        }

        if (shouldSort) {
            Log.d(TAG, "JMZ sorting")
            sort()
        }

        /*if (connectedAddress.value == null) {
            savedDevice()?.let {
                if (result.device.address.equals(it)) {
                    connect(result.device)
                }
            }
        }*/ // FIXME not the correct place for this code as update() can get called often before a device connects
    }

    fun scaleRSSI(rssi: Int) : Int {
        val scaled = (Math.round((rssi / 10).toDouble()) * 10).toInt()
        return scaled
    }

    fun createNewConnectionMgr(deviceAddress: String) : MyBleManager {
        var bleManager = MyBleManager(batteryStateCallback, context = ctx)

        bleManager.connectionObserver = object : ConnectionObserver {
            override fun onDeviceConnected(device: BluetoothDevice) {
                Log.d(TAG, "JMZ connection observer reports ${bleManager.connectionState} for device ${device.address}")
                connectState.value = bleManager.connectionState
                connectedStates[device.address] = bleManager.connectionState
                connectedAddress.value = device.address
                //connectedAddresses.filter { it != device.address }
                connectedAddresses.add(device.address)
            }

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                Log.d(TAG, "JMZ connection observer reports ${bleManager.connectionState} for device ${device.address}")
                connectState.value = bleManager.connectionState
                connectedStates[device.address] = bleManager.connectionState
                connectedAddress.value = ""
                connectedAddresses.remove(device.address)
            }

            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                Log.d(TAG, "JMZ connection observer reports ${bleManager.connectionState} for device ${device.address}")
                connectState.value = bleManager.connectionState
                connectedStates[device.address] = bleManager.connectionState
                connectedAddress.value = ""
                connectedAddresses.filter { it != device.address }
            }

            override fun onDeviceConnecting(device: BluetoothDevice) {
                Log.d(TAG, "JMZ connection observer reports ${bleManager.connectionState} for device ${device.address}")
                connectState.value = bleManager.connectionState
                connectedStates[device.address] = bleManager.connectionState
                connectedAddress.value = ""
                //connectedAddresses.filter { it != device.address }
            }

            override fun onDeviceDisconnecting(device: BluetoothDevice) {
                Log.d(TAG, "JMZ connection observer reports ${bleManager.connectionState} for device ${device.address}")
            }

            override fun onDeviceReady(device: BluetoothDevice) {
                Log.d(TAG, "JMZ connection observer reports ${bleManager.connectionState} for device ${device.address}")
            }
        }

        bleManagerMap[deviceAddress] = bleManager
        return bleManager
    }

    fun connect(device: BluetoothDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            var bleManager = this@BLEViewModel.bleManagerMap[device.address] ?: createNewConnectionMgr(device.address)

            try {
                Log.d(TAG, "JMZ connecting to ${device.address}")
                bleManager.connect(device)
                    .retry(3, 100)
                    .timeout(15_000)
                    .useAutoConnect(true)
                    .suspend()

                batteryLevel.value = bleManager.getBatteryLevel()!!
            } catch (e: Exception) {
                Log.d(TAG, "JMZ exception ${e} while trying to connect to ${device.address}")
                e.printStackTrace()
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
        private const val FAST_SCAN_THRESHOLD_SECONDS = 5
        private const val TIME_SCALE = 100
        private const val RSSI_SCALE = 1
    }
}

private val rssiComparator = Comparator<DeviceScanResult> { left, right ->
    right.rssi.compareTo(left.rssi)
}