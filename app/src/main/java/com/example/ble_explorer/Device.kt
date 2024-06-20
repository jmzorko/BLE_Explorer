package com.example.ble_explorer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun Device(navController: NavController, name: String, address: String, rssi: Int, connected: Boolean, bondState: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var expanded = rememberSaveable { mutableStateOf(false) }
    val extraPadding = 0.dp/*by animateDpAsState(
        if (expanded.value) 48.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )*/

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
                    navController.navigate(Screen.DeviceDetailScreen.route + "/${address}")
                }
            ) {
                Text(if (!connected) "Connect" else "Disconnect")
            }
        }
    }
}
