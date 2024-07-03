sealed class Screen(val route: String) {
    object DevicesScreen : Screen("DeviceList")
    object DeviceDetailScreen : Screen("DeviceDetail")
    object ServicesScreen : Screen("DeviceServices")
    object CharacteristicsScreen: Screen("DeviceCharacteristics")
}