import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ble_explorer.MyBleManager
import com.example.ble_explorer.R
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.ktx.suspend
import no.nordicsemi.android.ble.observer.ConnectionObserver

/*@Composable
fun Navigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.DevicesScreen.route) {
        composable(route = Screen.DevicesScreen.route) {
            DevicesScreen(navController = navController)
        }
        composable(
            route = Screen.DeviceDetailScreen.route + "/{address}",
            arguments = listOf(
                navArgument(name = "address") {
                    type = NavType.StringType
                }
            )
        ) { device ->
            device.arguments?.getString("address")?.let { DetailScreen(deviceAddress = it) }
        }
    }
}

@Composable
fun DevicesScreen(navController: NavController, modifier: Modifier = Modifier) {
    val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let {
                viewModel.update(result)
            }
        }
    }

    var scanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner

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
            scanner.startScan(scanCallback)
        }
    }

    val ctx = LocalContext.current

    when (PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) -> {
            Log.d("JMZ", "needs BLUETOOTH_SCAN")
            scanner.startScan(scanCallback)
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
            Text(stringResource(R.string.app_name))
        }
    )

    LazyColumn(modifier = modifier.padding(top = 128.dp)) {
        items(viewModel.devicesState) {
            Device(it.device.name ?: "(no name)", it.device.address, it.rssi, it.device.bondState)
        }
    }
}

@Composable
fun DetailScreen(deviceAddress: String, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    var batteryLevel = remember { mutableStateOf<Int>(0) }
    var connectState = remember { mutableStateOf<Int>(0) } // FIXME: should use BLEManager connect state enum

    val ctx = LocalContext.current
    var bleManager = MyBleManager(ctx)  // FIXME move BLEManager out of this activity, maybe make it a service
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
}*/
