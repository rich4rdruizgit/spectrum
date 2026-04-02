package co.doubler.spectrum.presentation.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import co.doubler.spectrum.presentation.screen.BluetoothScreen
import co.doubler.spectrum.presentation.screen.CompeteScreen
import co.doubler.spectrum.presentation.screen.GhostScreen
import co.doubler.spectrum.presentation.screen.MagFieldScreen
import co.doubler.spectrum.presentation.screen.SettingsScreen

private val slideSpec = tween<IntOffset>(durationMillis = 300, easing = FastOutSlowInEasing)
private val fadeSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

private fun NavBackStackEntry.screenTabIndex(): Int? = when {
    destination.hasRoute(Screen.Ghost::class)     -> 0
    destination.hasRoute(Screen.Compete::class)   -> 1
    destination.hasRoute(Screen.Bluetooth::class) -> 2
    destination.hasRoute(Screen.MagField::class)  -> 3
    else                                          -> null
}

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
        composable<Screen.Ghost>(
            enterTransition = {
                val from = initialState.screenTabIndex()
                val to = 0
                when {
                    from == null -> fadeIn(fadeSpec)
                    from > to    -> slideInHorizontally(slideSpec) { -it } + fadeIn(fadeSpec)
                    else         -> slideInHorizontally(slideSpec) { it } + fadeIn(fadeSpec)
                }
            },
            exitTransition = {
                val from = 0
                val to = targetState.screenTabIndex()
                when {
                    to == null -> fadeOut(fadeSpec)
                    to > from  -> slideOutHorizontally(slideSpec) { -it } + fadeOut(fadeSpec)
                    else       -> slideOutHorizontally(slideSpec) { it } + fadeOut(fadeSpec)
                }
            },
            popEnterTransition = {
                val from = initialState.screenTabIndex()
                val to = 0
                when {
                    from == null -> fadeIn(fadeSpec)
                    from > to    -> slideInHorizontally(slideSpec) { -it } + fadeIn(fadeSpec)
                    else         -> slideInHorizontally(slideSpec) { it } + fadeIn(fadeSpec)
                }
            },
            popExitTransition = {
                val from = 0
                val to = targetState.screenTabIndex()
                when {
                    to == null -> fadeOut(fadeSpec)
                    to > from  -> slideOutHorizontally(slideSpec) { -it } + fadeOut(fadeSpec)
                    else       -> slideOutHorizontally(slideSpec) { it } + fadeOut(fadeSpec)
                }
            }
        ) { GhostScreen() }

        composable<Screen.Compete>(
            enterTransition = {
                val from = initialState.screenTabIndex()
                val to = 1
                when {
                    from == null -> fadeIn(fadeSpec)
                    from > to    -> slideInHorizontally(slideSpec) { -it } + fadeIn(fadeSpec)
                    else         -> slideInHorizontally(slideSpec) { it } + fadeIn(fadeSpec)
                }
            },
            exitTransition = {
                val from = 1
                val to = targetState.screenTabIndex()
                when {
                    to == null -> fadeOut(fadeSpec)
                    to > from  -> slideOutHorizontally(slideSpec) { -it } + fadeOut(fadeSpec)
                    else       -> slideOutHorizontally(slideSpec) { it } + fadeOut(fadeSpec)
                }
            },
            popEnterTransition = {
                val from = initialState.screenTabIndex()
                val to = 1
                when {
                    from == null -> fadeIn(fadeSpec)
                    from > to    -> slideInHorizontally(slideSpec) { -it } + fadeIn(fadeSpec)
                    else         -> slideInHorizontally(slideSpec) { it } + fadeIn(fadeSpec)
                }
            },
            popExitTransition = {
                val from = 1
                val to = targetState.screenTabIndex()
                when {
                    to == null -> fadeOut(fadeSpec)
                    to > from  -> slideOutHorizontally(slideSpec) { -it } + fadeOut(fadeSpec)
                    else       -> slideOutHorizontally(slideSpec) { it } + fadeOut(fadeSpec)
                }
            }
        ) { CompeteScreen() }

        composable<Screen.Bluetooth>(
            enterTransition = {
                val from = initialState.screenTabIndex()
                val to = 2
                when {
                    from == null -> fadeIn(fadeSpec)
                    from > to    -> slideInHorizontally(slideSpec) { -it } + fadeIn(fadeSpec)
                    else         -> slideInHorizontally(slideSpec) { it } + fadeIn(fadeSpec)
                }
            },
            exitTransition = {
                val from = 2
                val to = targetState.screenTabIndex()
                when {
                    to == null -> fadeOut(fadeSpec)
                    to > from  -> slideOutHorizontally(slideSpec) { -it } + fadeOut(fadeSpec)
                    else       -> slideOutHorizontally(slideSpec) { it } + fadeOut(fadeSpec)
                }
            },
            popEnterTransition = {
                val from = initialState.screenTabIndex()
                val to = 2
                when {
                    from == null -> fadeIn(fadeSpec)
                    from > to    -> slideInHorizontally(slideSpec) { -it } + fadeIn(fadeSpec)
                    else         -> slideInHorizontally(slideSpec) { it } + fadeIn(fadeSpec)
                }
            },
            popExitTransition = {
                val from = 2
                val to = targetState.screenTabIndex()
                when {
                    to == null -> fadeOut(fadeSpec)
                    to > from  -> slideOutHorizontally(slideSpec) { -it } + fadeOut(fadeSpec)
                    else       -> slideOutHorizontally(slideSpec) { it } + fadeOut(fadeSpec)
                }
            }
        ) { BluetoothScreen() }

        composable<Screen.MagField>(
            enterTransition = {
                val from = initialState.screenTabIndex()
                val to = 3
                when {
                    from == null -> fadeIn(fadeSpec)
                    from > to    -> slideInHorizontally(slideSpec) { -it } + fadeIn(fadeSpec)
                    else         -> slideInHorizontally(slideSpec) { it } + fadeIn(fadeSpec)
                }
            },
            exitTransition = {
                val from = 3
                val to = targetState.screenTabIndex()
                when {
                    to == null -> fadeOut(fadeSpec)
                    to > from  -> slideOutHorizontally(slideSpec) { -it } + fadeOut(fadeSpec)
                    else       -> slideOutHorizontally(slideSpec) { it } + fadeOut(fadeSpec)
                }
            },
            popEnterTransition = {
                val from = initialState.screenTabIndex()
                val to = 3
                when {
                    from == null -> fadeIn(fadeSpec)
                    from > to    -> slideInHorizontally(slideSpec) { -it } + fadeIn(fadeSpec)
                    else         -> slideInHorizontally(slideSpec) { it } + fadeIn(fadeSpec)
                }
            },
            popExitTransition = {
                val from = 3
                val to = targetState.screenTabIndex()
                when {
                    to == null -> fadeOut(fadeSpec)
                    to > from  -> slideOutHorizontally(slideSpec) { -it } + fadeOut(fadeSpec)
                    else       -> slideOutHorizontally(slideSpec) { it } + fadeOut(fadeSpec)
                }
            }
        ) { MagFieldScreen() }

        composable<Screen.Settings>(
            enterTransition = {
                scaleIn(initialScale = 0.95f, animationSpec = fadeSpec) + fadeIn(fadeSpec)
            },
            exitTransition = {
                scaleOut(targetScale = 0.95f, animationSpec = fadeSpec) + fadeOut(fadeSpec)
            },
            popEnterTransition = {
                scaleIn(initialScale = 0.95f, animationSpec = fadeSpec) + fadeIn(fadeSpec)
            },
            popExitTransition = {
                scaleOut(targetScale = 0.95f, animationSpec = fadeSpec) + fadeOut(fadeSpec)
            }
        ) { SettingsScreen() }
    }
}
