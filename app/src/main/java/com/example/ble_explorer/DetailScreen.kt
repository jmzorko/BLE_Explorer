package com.example.ble_explorer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(navController: NavController, deviceAddress: String, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()

    var mainActivity = navController.context.findActivity() as MainActivity
    var device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress)

    mainActivity.viewModel?.let { vm ->
        var bleManager = vm.bleManagerMap[deviceAddress]
        var connectedStates = remember { vm.connectedStates }
        var batteryLevels = remember { vm.batteryLevels }
        var stateString = connectedStates[deviceAddress]?.let {
            getConnectedStateString(it)
        }

        LaunchedEffect(Unit) {
            vm.connect(device)
        }

        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
            title = {
                Log.d("DetailScreen", "connection state: ${stateString}")

                Row {
                    IconButton(onClick = {
                        if (!navController.popBackStack()) {
                            mainActivity.finish()
                            bleManager?.let {
                                //bleManager.disconnect().enqueue()
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = ""
                        )
                    }
                    Text("${device.name ?: device.address} ... $stateString")
                }
            }
        )

        if (connectedStates[deviceAddress] == BluetoothGatt.STATE_CONNECTED) {
            Column(modifier = modifier.padding(top = 128.dp)) {
                Row {
                    batteryLevels[deviceAddress]?.let { batt ->
                        if (batt >= 0) {
                            Text("Battery: ${batt}")
                        } else {
                            Text("Battery info unavailable")
                        }
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
                            bleManager?.let {
                                // FIXME: doesn't disconnect after recreating view model bc connection was not closed before earlier view model was destroyed
                                bleManager.disconnect().enqueue()
                            }
                        }
                    ) {
                        Text("Disconnect")
                    }
                }

                Row {
                    bleManager?.let { ble ->
                        LazyColumn(modifier = modifier.padding(top = 10.dp)) {
                            items(ble.services.toList()) { service ->
                                Row {
                                    TextButton(onClick = {
                                        navController.navigate(Screen.CharacteristicsScreen.route + "/${deviceAddress}/${service.first}")
                                    }) {
                                        Text(service.first.toString(), fontSize = 23.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getConnectedStateString(state: Int) : String {
    var ret: String = ""

    when (state) {
        BluetoothGatt.STATE_CONNECTING -> ret = "Connecting"
        BluetoothGatt.STATE_CONNECTED -> ret = "Connected"
        //2 -> ret = "Could not connect"
        BluetoothGatt.STATE_DISCONNECTED -> ret = "Disconnected"
        //4 -> ret = "Failed to connect"
        BluetoothGatt.STATE_DISCONNECTING -> ret = "Disconnecting"
    }

    return ret
}
