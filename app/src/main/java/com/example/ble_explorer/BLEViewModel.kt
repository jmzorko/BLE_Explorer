package com.example.ble_explorer

import android.bluetooth.le.ScanResult
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel

class BLEViewModel : ViewModel() {
    var devicesState = mutableStateListOf<DeviceScanResult>()

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