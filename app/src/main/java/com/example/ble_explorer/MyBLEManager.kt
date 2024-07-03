package com.example.ble_explorer

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.ktx.suspendForResponse
import no.nordicsemi.android.ble.response.ReadResponse
import java.util.UUID

class MyBleManager(batteryChangedCallback: DataReceivedCallback, context: Context) : BleManager(context) {
    val services = mutableMapOf<UUID, List<UUID>>()

    var batteryCallback = batteryChangedCallback

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

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        // Here obtain instances of your characteristics.
        // Return false if a required service has not been discovered.

        gatt.services.forEach { service ->
            Log.d(TAG, "... found service ${service.uuid}")

            var chars = mutableListOf<UUID>()

            service.characteristics.forEach { char ->
                chars.add(char.uuid)
                Log.d(TAG, "... ... found characteristic ${char.uuid}")
            }

            services[service.uuid] = chars
        }

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

    companion object {
        private const val TAG = "MyBleManager"
        private const val BATTERY_SERVICE = "0000180F-0000-1000-8000-00805f9b34fb"
        private const val BATTERY_CHARACTERISTIC = "00002a19-0000-1000-8000-00805f9b34fb"
    }
}