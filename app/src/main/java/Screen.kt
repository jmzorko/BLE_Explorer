sealed class Screen(val route: String) {
    object DevicesScreen : Screen("DeviceList")
    object DeviceDetailScreen : Screen("DeviceDetail")
}