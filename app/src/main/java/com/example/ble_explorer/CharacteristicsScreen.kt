package com.example.ble_explorer

import android.Manifest
import android.bluetooth.BluetoothAdapter
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacteristicsScreen(navController: NavController, deviceAddress: String, service: UUID, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current

    var mainActivity = navController.context.findActivity() as MainActivity
    var device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress)

    mainActivity.viewModel?.let { vm ->
        var bleManager = vm.bleManagerMap[deviceAddress]

        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
            title = {
                Row(modifier = Modifier.padding(0.dp)) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = ""
                        )
                    }

                    Column(modifier = modifier.weight(1f)) {
                        Text(service.toString())
                    }
                }
            }
        )

        Column(modifier = modifier.padding(top = 128.dp)) {
            Row {
                bleManager?.let { ble ->
                    LazyColumn(modifier = modifier.padding(top = 10.dp)) {
                        items(ble.services[service] ?: emptyList<UUID>()) { char ->
                            Row {
                                TextButton(onClick = {
                                }) {
                                    Text(char.toString(), fontSize = 23.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
