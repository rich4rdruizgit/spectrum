package co.doubler.spectrum.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import co.doubler.spectrum.presentation.screen.BluetoothScreen
import co.doubler.spectrum.presentation.screen.CompeteScreen
import co.doubler.spectrum.presentation.screen.GhostScreen
import co.doubler.spectrum.presentation.screen.MagFieldScreen
import co.doubler.spectrum.presentation.screen.SettingsScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Ghost,
        modifier = modifier
    ) {
        composable<Screen.Ghost> { GhostScreen() }
        composable<Screen.Compete> { CompeteScreen() }
        composable<Screen.Bluetooth> { BluetoothScreen() }
        composable<Screen.MagField> { MagFieldScreen() }
        composable<Screen.Settings> { SettingsScreen() }
    }
}
