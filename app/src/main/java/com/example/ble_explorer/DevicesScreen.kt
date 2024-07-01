package com.example.ble_explorer

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(navController: NavController, modifier: Modifier = Modifier) {
    var mainActivity = navController.context.findActivity() as MainActivity

    val bluetoothPermissionResultLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) {
        var good = false
        it.forEach { s, b ->
            if (b) {
                good = true
                Log.d("JMZ", "$s granted")
            } else {
                good = false
                Log.d("JMZ", "$s denied")
                return@forEach
            }
        }

        if (good) { // FIXME if user declines permissions twice, consider sending them to the app Settings page to grant them
            mainActivity.viewModel?.startDeviceScan()
        }
    }

    val ctx = LocalContext.current

    when (PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) -> {
            Log.d("JMZ", "needs BLUETOOTH_SCAN")
            mainActivity.viewModel?.startDeviceScan()
        }
        ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) -> {
            Log.d("JMZ", "needs BLUETOOTH_CONNECT")
        }
        else -> {
            LaunchedEffect(Unit) {
                bluetoothPermissionResultLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
            }
        }
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = {
            Row(modifier = Modifier.padding(0.dp)) {
                Column(modifier = modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name))
                }

                IconButton(modifier = Modifier.padding(end = 5.dp), onClick = {
                    mainActivity.viewModel?.let {
                        it.scanStartedTimeSeconds = System.currentTimeMillis() / 1000
                        it.clearDevices()
                    }

                    mainActivity.viewModel?.stopDeviceScan()
                    mainActivity.viewModel?.startDeviceScan()
                }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = ""
                    )
                }
            }
        }
    )

    mainActivity.viewModel?.let { vm ->
        vm.devicesState?.let { devices ->
            LazyColumn(modifier = modifier.padding(top = 128.dp)) {
                items(devices) { dev ->
                    var connected = vm.connectedAddresses.contains(dev.device.address)
                    Device(navController, dev.device.name ?: "(no name)", dev.device.address, dev.rssi, connected, dev.device.bondState)
                }
            }
        }
    }
}

