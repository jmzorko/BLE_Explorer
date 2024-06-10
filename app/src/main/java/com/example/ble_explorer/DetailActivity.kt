package com.example.ble_explorer

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ble_explorer.R
import com.example.ble_explorer.ui.theme.BLE_ExplorerTheme
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.ktx.suspend
import no.nordicsemi.android.ble.observer.ConnectionObserver

class DetailActivity : ComponentActivity() {
    var deviceAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BLE_ExplorerTheme {
                val ctx = LocalContext.current
                val activity = ctx.findActivity()
                var address: String? = null

                activity?.intent?.let {
                    address = it.getStringExtra("DEVICE_ADDRESS")
                    address?.let {
                        detailScreen(address!!, modifier = Modifier.fillMaxSize())
                    }
                }
                /*Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }*/
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun detailScreen(deviceAddress: String, modifier: Modifier = Modifier) {
        val coroutineScope = rememberCoroutineScope()
        var batteryLevel = remember { mutableStateOf<Int>(0) }
        var connectState = remember { mutableStateOf<Int>(0) } // FIXME: should use BLEManager connect state enum

        val ctx = LocalContext.current
        var bleManager = MyBleManager(ctx)
        var device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress)

        bleManager.connectionObserver = object : ConnectionObserver {
            override fun onDeviceConnected(device: BluetoothDevice) {
                connectState.value = 1
            }

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                connectState.value = 3
            }

            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                connectState.value = 2
            }

            override fun onDeviceConnecting(device: BluetoothDevice) {
                connectState.value = 0
            }

            override fun onDeviceDisconnecting(device: BluetoothDevice) {
            }

            override fun onDeviceReady(device: BluetoothDevice) {
            }
        }

        LaunchedEffect(Unit) {
            coroutineScope.launch {
                try {
                    connectState.value = 0

                    bleManager.connect(device)
                        .retry(3, 100)
                        .timeout(15_000)
                        .useAutoConnect(true)
                        .suspend()

                    batteryLevel.value = bleManager.getBatteryLevel()!!
                    connectState.value = if (bleManager.connectionState == 2) 1 else 4
                } catch (e: Exception) {
                    connectState.value = 4
                }
            }
        }

        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
            title = {
                var stateString = getConnectedStateString(connectState.value)

                Row {
                    IconButton(onClick = { finish() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = ""
                        )
                    }
                    Text("${device.name ?: device.address} ... $stateString")
                }
            }
        )

        if (connectState.value == 1) {
            Column(modifier = modifier.padding(top = 128.dp)) {
                Row {
                    if (batteryLevel.value >= 0) {
                        Text("Battery: ${batteryLevel.value}")
                    } else {
                        Text("Battery info unavailable")
                    }
                }

                if (!device.name.isNullOrBlank()) {
                    Row {
                        Text("${device.name}")
                    }
                }

                Row {
                    Text("${device.address}")
                }

                Row {
                    Button(modifier = Modifier.size(width = 160.dp, height = 40.dp),
                        onClick = {
                            bleManager.disconnect().enqueue()   // FIXME doesn't seem to disconnect
                        }
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }
    }

    private fun getConnectedStateString(state: Int) : String {
        var ret: String = ""

        when (state) {
            0 -> ret = "Connecting"
            1 -> ret = "Connected"
            2 -> ret = "Could not connect"
            3 -> ret = "Disconnected"
            4 -> ret = "Failed to connect"
        }

        return ret
    }

    fun Context.findActivity(): Activity? = when(this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}