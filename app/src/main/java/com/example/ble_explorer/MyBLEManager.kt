package com.example.ble_explorer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.content.Context
import android.util.Log
import com.example.ble_explorer.BLEViewModel.Companion
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.Request
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.ktx.suspendForResponse
import no.nordicsemi.android.ble.observer.BondingObserver
import no.nordicsemi.android.ble.response.ReadResponse
import java.util.UUID

class MyBleManager(batteryChangedCallback: DataReceivedCallback, context: Context) : BleManager(context) {
    val services = mutableMapOf<UUID, List<UUID>>()

    var batteryCallback = batteryChangedCallback

    init {
        bondingObserver = object : BondingObserver {
            override fun onBondingRequired(device: BluetoothDevice) {
                Log.d(TAG, "needs bonding")
            }

            override fun onBonded(device: BluetoothDevice) {
                Log.d(TAG, "has bonded")
            }

            override fun onBondingFailed(device: BluetoothDevice) {
                Log.d(TAG, "bonding failed")
            }
        }
    }

    // ==== Logging =====
    override fun getMinLogPriority(): Int {
        // Use to return minimal desired logging priority.
        return Log.VERBOSE
    }

    override fun log(priority: Int, message: String) {
        // Log from here.
        Log.println(priority, TAG, message)
    }

    // ==== Required implementation ====
    // This is a reference to a characteristic that the manager will use internally.
    var batteryChar: BluetoothGattCharacteristic? = null
    var disNameChar: BluetoothGattCharacteristic? = null
    var disSerialChar: BluetoothGattCharacteristic? = null
    var deviceStateChar: BluetoothGattCharacteristic? = null

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        // Here obtain instances of your characteristics.
        // Return false if a required service has not been discovered.

        var ret = false

        val dis = gatt.getService(UUID.fromString(DEVICE_INFO_SERVICE))
        if (dis != null) {
            disNameChar = dis.getCharacteristic(UUID.fromString(DEVICE_INFO_CHAR_NAME))
            disSerialChar = dis.getCharacteristic(UUID.fromString(DEVICE_INFO_CHAR_SERIAL_NUMBER))
            ret = true
        }

        val primary = gatt.getService(UUID.fromString(M3_PRIMARY_SERVICE))
        if (primary != null) {
            deviceStateChar = primary.getCharacteristic(UUID.fromString(M3_DEVICE_STATE))
            ret = true
        }

        gatt.services.forEach { service ->
            Log.d(TAG, "... found service ${service.uuid}")

            var chars = mutableListOf<UUID>()

            service.characteristics.forEach { char ->
                chars.add(char.uuid)
                Log.d(TAG, "... ... found characteristic ${char.uuid}")
            }

            services[service.uuid] = chars
        }

        return ret
    }

    override fun isOptionalServiceSupported(gatt: BluetoothGatt): Boolean {
        val batteryService = gatt.getService(UUID.fromString(BATTERY_SERVICE))
        if (batteryService != null) {
            batteryChar = batteryService.getCharacteristic(UUID.fromString(BATTERY_CHARACTERISTIC))
        }

        return batteryChar != null
    }

    override fun initialize() {
        // Initialize your device.
        // This means e.g. enabling notifications, setting notification callbacks, or writing
        // something to a Control Point characteristic.
        // Kotlin projects should not use suspend methods here, as this method does not suspend.
        requestMtu(517).enqueue()

        readCharacteristic(disNameChar).enqueue()

        enableNotifications(batteryChar).enqueue()
        setNotificationCallback(batteryChar).with(batteryCallback)
    }

    override fun onServicesInvalidated() {
        // This method is called when the services get invalidated, i.e. when the device
        // disconnects.
        // References to characteristics should be nullified here.
        batteryChar = null
    }

    override fun onServerReady(server: BluetoothGattServer) {
        super.onServerReady(server)
        server.services.forEach { service ->
            Log.d(TAG, "... found service ${service.uuid}")
        }
    }

    // ==== Public API ====
    // Here you may add some high level methods for your device:
    /*fun enableFluxCapacitor() {
        // Do the magic.
        writeCharacteristic(
            controlPoint,
            Flux.enable(),
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        )
            .enqueue()
    }*/

    suspend fun getBatteryLevel() : Int? {
        try {
            val response: ReadResponse = readCharacteristic(batteryChar)
                .suspendForResponse()   // FIXME needed JVM 17 to run ... might need to update to newer lib
            return response.rawData?.getIntValue(Data.FORMAT_UINT8, 0)
        } catch (e: Exception) {
            return -1;
        }
    }

    suspend fun getDISName() : String? {
        try {
            Log.d(TAG, "JMZ reading device name ...")
            val response: ReadResponse = readCharacteristic(disNameChar)
                .suspendForResponse()   // FIXME needed JVM 17 to run ... might need to update to newer lib
            return response.rawData?.getStringValue(0)
        } catch (e: Exception) {
            return "Error getting DIS name " + e.message
        }
    }

    suspend fun getDISSerial() : String? {
        try {
            Log.d(TAG, "JMZ reading device serial # ...")
            val response: ReadResponse = readCharacteristic(disSerialChar)
                .suspendForResponse()   // FIXME needed JVM 17 to run ... might need to update to newer lib
            return response.rawData?.getStringValue(0)
        } catch (e: Exception) {
            return "Error getting DIS serial number " + e.message
        }
    }

    suspend fun getDeviceState() : String? {
        if (deviceStateChar != null) {
            try {
                Log.d(TAG, "JMZ reading device state ...")
                val response: ReadResponse = readCharacteristic(deviceStateChar)
                    .suspendForResponse()   // FIXME needed JVM 17 to run ... might need to update to newer lib
                return response.rawData?.getStringValue(0)
            } catch (e: Exception) {
                return "Error getting M3 device state " + e.message
            }
        } else {
            return "No device state char found - not M3?"
        }
    }

    companion object {
        private const val TAG = "MyBleManager"
        private const val DEVICE_INFO_SERVICE = "0000180A-0000-1000-8000-00805F9B34FB"
        private const val DEVICE_INFO_CHAR_NAME = "00002A24-0000-1000-8000-00805F9B34FB"
        private const val DEVICE_INFO_CHAR_SERIAL_NUMBER = "00002A25-0000-1000-8000-00805F9B34FB"

        private const val M3_PRIMARY_SERVICE = "E5030001-4E19-428E-A331-F90D5ABBA18C"
        private const val M3_DEVICE_STATE = "E5030006-4E19-428E-A331-F90D5ABBA18C"
        private const val BATTERY_SERVICE = "0000180F-0000-1000-8000-00805f9b34fb"
        private const val BATTERY_CHARACTERISTIC = "00002a19-0000-1000-8000-00805f9b34fb"
    }
}