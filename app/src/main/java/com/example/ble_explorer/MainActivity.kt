package com.example.ble_explorer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.ble_explorer.DetailActivity
import com.example.ble_explorer.ui.theme.BLE_ExplorerTheme

class MainActivity : ComponentActivity() {
    private val viewModel = BLEViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BLE_ExplorerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DevicesScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DevicesScreen(modifier: Modifier = Modifier) {
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
    fun Device(name: String, address: String, rssi: Int, bondState: Int, modifier: Modifier = Modifier) {
        val ctx = LocalContext.current
        var expanded = rememberSaveable { mutableStateOf(false) }
        val extraPadding by animateDpAsState(
            if (expanded.value) 48.dp else 0.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )

        Surface(
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.padding(vertical = 4.dp, horizontal = 8.dp)
        ) {
            Row(modifier = Modifier.padding(0.dp)) {
                Column(modifier = modifier.weight(1f).padding(bottom = extraPadding.coerceAtLeast(0.dp))) {
                    TextButton(onClick = { /*expanded.value = !expanded.value*/ },
                        colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.primaryContainer)) {

                        Column {
                            Text(text = name)
                            Text(address)
                            Text(text = "$rssi")
                            Text(text = "Bond state: $bondState")
                        }
                    }
                }

                ElevatedButton(modifier = modifier.padding(end = 5.dp),
                    onClick = {
                        val intent = Intent(ctx, DetailActivity::class.java)
                        intent.putExtra("DEVICE_ADDRESS", address)
                        ctx.startActivity(intent)
                    }
                ) {
                    Text("Connect")
                }
            }
        }
    }
}
