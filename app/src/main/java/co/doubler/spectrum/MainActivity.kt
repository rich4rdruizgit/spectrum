package co.doubler.spectrum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import co.doubler.spectrum.ar.ArSessionManager
import co.doubler.spectrum.domain.model.ScanMode
import co.doubler.spectrum.presentation.components.ModeSelector
import co.doubler.spectrum.presentation.navigation.AppNavigation
import co.doubler.spectrum.presentation.navigation.toScreen
import co.doubler.spectrum.ui.theme.SpectrumTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: ArSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kick off ARCore availability check as early as possible.
        // checkAvailability() is non-blocking: it posts a delayed re-check
        // if the result is transient, and transitions sessionState to Ready
        // (or NotSupported / Error) once resolved.
        sessionManager.checkAvailability(this)
        enableEdgeToEdge()
        setContent {
            SpectrumTheme {
                val navController = rememberNavController()
                var currentMode by remember { mutableStateOf(ScanMode.GHOST) }

                Scaffold(
                    bottomBar = {
                        ModeSelector(
                            currentMode = currentMode,
                            onModeSelected = { mode ->
                                currentMode = mode
                                navController.navigate(mode.toScreen()) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                ) { padding ->
                    AppNavigation(
                        navController = navController,
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
}
