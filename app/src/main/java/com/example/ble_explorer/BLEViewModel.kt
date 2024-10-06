package com.example.ble_explorer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.ktx.suspend
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.UUID
import kotlin.math.abs


class BLEViewModel(private val ctx: Context) : ViewModel() {
    var devicesState = mutableStateListOf<DeviceScanResult>()
    var connectedStates = mutableStateMapOf<String, Int>()
    var batteryLevels = mutableStateMapOf<String, Int>()
    var connectedAddresses = mutableStateListOf<String>()

    var scanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
    var bleManagerMap = mutableMapOf<String, MyBleManager>()

    var scanHandler = Handler(Looper.getMainLooper())
    var isScanning = false
    var foundAlreadyConnectedDevices = false
    var scanStartedCount = 0
    var lastScanTimeScaled: Long = 0
    var scanStartedTimeSeconds = System.currentTimeMillis() / 1000

    val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            //val currentTimeScaled = System.currentTimeMillis() / TIME_SCALE
            //if (currentTimeScaled > lastScanTimeScaled || currentTimeScaled < scanStartedTimeSeconds + FAST_SCAN_THRESHOLD_SECONDS) {
            //    lastScanTimeScaled = currentTimeScaled
            result?.let { r ->
                update(result)
            }
            //}
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            scanStartedCount --
        }
    }

    @SuppressLint("MissingPermission")
    fun startDeviceScan() {
        if (!isScanning) {
            Log.d(TAG, "starting BLE scan")
            /*scanHandler.postDelayed({
                if (scanCount > 0) {
                    scanner.stopScan(scanCallback)
                    isScanning = false
                }
            }, 10000)*/

            isScanning = true
            val filters: MutableList<ScanFilter> = ArrayList()
            // M3   "E5030001-4E19-428E-A331-F90D5ABBA18C"
            // M2   "01E53401-E7D4-FBBD-7D41-12A404783F7F"
            val scanFilterNameM2  = ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString("E5030001-4E19-428E-A331-F90D5ABBA18C"))).build()
            val scanFilterNameM3 = ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString("01E53401-E7D4-FBBD-7D41-12A404783F7F"))).build()
            filters.add(scanFilterNameM2)
            filters.add(scanFilterNameM3)

            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(filters, settings, scanCallback)
            scanStartedCount ++
        }

        if (!foundAlreadyConnectedDevices) {
            findAlreadyConnectedDevices()
            foundAlreadyConnectedDevices = true
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
            batteryLevels[device.address] = data.getIntValue(Data.FORMAT_UINT8, 0)!!
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

        //Log.d(TAG, "updating list with ${result.device?.name ?: "?"} address ${result.device?.address} already found ${found != null}")

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
            //Log.d(TAG, "JMZ sorting")
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

    fun cleanup() {
        bleManagerMap.forEach { it.value.close() }
    }

    @SuppressLint("MissingPermission")
    fun findAlreadyConnectedBondedDevices() {
        BluetoothAdapter.getDefaultAdapter().bondedDevices.forEach { device ->
            try {
                var isConnectedMethod = device.javaClass.getMethod("isConnected")
                var isConnected = isConnectedMethod.invoke(device)
                if (isConnected.toString().equals("true")) {
                    connectAndSort(device)
                }
            } catch (e: Exception) {
                Log.d(TAG, "method not found")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun findAlreadyConnectedDevices() {
        var bluetoothManager = ContextCompat.getSystemService(ctx, BluetoothManager::class.java)
        bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT)?.forEach { device ->
            connectAndSort(device)
        }
    }

    fun createNewConnectionMgr(deviceAddress: String) : MyBleManager {
        var bleManager = MyBleManager(batteryStateCallback, context = ctx)

        bleManager.connectionObserver = object : ConnectionObserver {
            override fun onDeviceConnected(device: BluetoothDevice) {
                Log.d(TAG, "JMZ onDeviceConnected on mgr ${bleManager} reports ${bleManager.connectionState} for device ${device.address}")
                connectedStates[device.address] = bleManager.connectionState

                if (!connectedAddresses.contains(device.address)) {
                    connectedAddresses.add(device.address)
                }

                var found = false
                for (d in devicesState) {
                    if (d.device.address.equals(device.address)) {
                        found = true
                        break
                    }
                }

                // if we didn't find the connected device in the scanned devices list, add it
                if (!found) {
                    val scanResult = DeviceScanResult(10, device)
                    devicesState.add(scanResult)
                }
            }

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                if (reason == BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED) {
                    Log.d(TAG, "JMZ onDeviceDisconnected on mgr ${bleManager} because device ${device.address} not bonded")
                } else {
                    Log.d(
                        TAG,
                        "JMZ  .................. onDeviceDisconnected on mgr ${bleManager} reports ${bleManager.connectionState} for device ${device.address} reason ${reason}"
                    )
                }

                connectedStates[device.address] = bleManager.connectionState

                if (connectedAddresses.contains(device.address)) {
                    connectedAddresses.remove(device.address)
                }
            }

            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                Log.d(TAG, "JMZ onDeviceFailedToConnect on mgr ${bleManager} reports ${bleManager.connectionState} for device ${device.address}")
                connectedStates[device.address] = bleManager.connectionState
            }

            override fun onDeviceConnecting(device: BluetoothDevice) {
                Log.d(TAG, "JMZ onDeviceConnecting on mgr ${bleManager} reports ${bleManager.connectionState} for device ${device.address}")
                connectedStates[device.address] = bleManager.connectionState
            }

            override fun onDeviceDisconnecting(device: BluetoothDevice) {
                if (bleManager.connectionState == BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED) {
                    Log.d(TAG, "JMZ onDeviceDisconnecting on mgr ${bleManager} because device ${device.address} not bonded")
                } else {
                    Log.d(
                        TAG,
                        "JMZ onDeviceDisconnecting on mgr ${bleManager} reports ${bleManager.connectionState} for device ${device.address}"
                    )
                }
            }

            override fun onDeviceReady(device: BluetoothDevice) {
                Log.d(TAG, "JMZ onDeviceReady on mgr ${bleManager} reports ${bleManager.connectionState} for device ${device.address}")
            }
        }

        bleManagerMap[deviceAddress] = bleManager

        return bleManager
    }

    fun connectAndSort(device: BluetoothDevice) {
        Log.d(TAG, "device ${device} already connected")
        var dev = DeviceScanResult(10, device)
        connect(device) // ... the device is already connected - we do this to setup the connection observer
        sort()
    }

    fun connect(device: BluetoothDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            var bleManager = this@BLEViewModel.bleManagerMap[device.address] ?: createNewConnectionMgr(device.address)
            Log.d(TAG, "device ${device.address} mgr ${bleManager}")

            try {
                Log.d(TAG, "JMZ connecting to ${device.address}")
                bleManager.connect(device)
                    .retry(3, 100)
                    .timeout(15_000)
                    .useAutoConnect(true)
                    .suspend()

                batteryLevels[device.address] = bleManager.getBatteryLevel()!!
                val disName = bleManager.getDISName()
                Log.d(TAG, "JMZ name: ${disName}")
                val disSerial = bleManager.getDISSerial()
                Log.d(TAG, "JMZ serial #: ${disSerial}")
                var deviceState = bleManager.getDeviceState()
                Log.d(TAG, "JMZ device state: ${deviceState}")
            } catch (e: Exception) {
                bleManager.disconnect()
                Log.d(TAG, "JMZ exception ${e} while trying to connect to ${device.address}")
                e.printStackTrace()
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
        private const val RSSI_SCALE = 5
    }
}

private val rssiComparator = Comparator<DeviceScanResult> { left, right ->
    right.rssi.compareTo(left.rssi)
}