package com.example.ble_explorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ble_explorer.ui.theme.BLE_ExplorerTheme

class MainActivity : ComponentActivity() {
    public var viewModel: BLEViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BLE_ExplorerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = Screen.DevicesScreen.route) {
                        viewModel = BLEViewModel(this@MainActivity)
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
                            device.arguments?.let {
                                it.getString("address")?.let {
                                    DetailScreen(navController = navController, deviceAddress = it)
                                }
                            }
                        }
                        composable(
                            route = Screen.CharacteristicsScreen.route + "/{service}",
                            arguments = listOf(
                                navArgument(name = "service") {
                                    type = NavType.StringType
                                }
                            )
                        ) { service ->
                            service.arguments?.let {
                                it.getString("service")?.let {
                                    CharacteristicsScreen(navController = navController, service = it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
