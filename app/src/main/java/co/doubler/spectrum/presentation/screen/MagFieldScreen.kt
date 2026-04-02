package co.doubler.spectrum.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.doubler.spectrum.domain.model.ScanMode
import co.doubler.spectrum.presentation.components.ArSceneView
import co.doubler.spectrum.presentation.components.MagneticInfoPanel
import co.doubler.spectrum.presentation.viewmodel.MagneticViewModel
import co.doubler.spectrum.rendering.magnetic.MagneticOverlayRenderer
import co.doubler.spectrum.ui.theme.DataFontFamily
import co.doubler.spectrum.ui.theme.MagneticAccent
import co.doubler.spectrum.ui.theme.NearBlack
import co.doubler.spectrum.ui.theme.TextSecondary
import co.doubler.spectrum.util.PermissionGroups
import co.doubler.spectrum.util.rememberPermissionState

/**
 * Magnetic Field mode screen — magnetometer AR visualization.
 *
 * Renders the Earth's magnetic field (and local distortions) as animated flow lines
 * and particles overlaid on the AR camera feed. A HUD panel shows live magnitude,
 * XYZ components, ICNIRP reference bar, and anomaly warnings.
 *
 * Architecture:
 * - [MagneticViewModel] collects [MagneticFieldRepository] flow, performs anomaly
 *   detection, and updates [MagneticRenderData] via [AtomicReference] for GL access
 * - [MagneticOverlayRenderer] draws the fullscreen shader overlay on the GL thread
 * - [ArSceneView] hosts the GL surface with overlay registration
 * - [MagneticInfoPanel] shows HUD data in the bottom-start corner
 */
@Composable
fun MagFieldScreen(
    viewModel: MagneticViewModel = hiltViewModel()
) {
    val permissionState = rememberPermissionState(
        PermissionGroups.forMode(ScanMode.MAGNETIC)
    )

    if (!permissionState.allGranted) {
        MagneticPermissionRequestContent(
            deniedPermissions = permissionState.deniedPermissions,
            onRequestPermissions = permissionState.requestPermissions
        )
    } else {
        MagneticArContent(viewModel = viewModel)
    }
}

// ── AR Content (permissions granted) ────────────────────────────────

@Composable
private fun MagneticArContent(
    viewModel: MagneticViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Renderer is created once, tied to ViewModel identity.
    // Reads MagneticRenderData from AtomicReference on the GL thread.
    val magneticRenderer = remember(viewModel) {
        MagneticOverlayRenderer(
            context = context,
            magneticDataRef = viewModel.magneticDataRef
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ArSceneView(
            sessionManager = viewModel.sessionManager,
            scanMode = ScanMode.MAGNETIC,
            isScanning = uiState.isScanning,
            overlayRenderer = magneticRenderer,
            isAnomalyActive = uiState.isAnomaly,
            hudContent = {
                MagneticInfoPanel(
                    state = uiState,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 32.dp)
                )
            }
        )

        if (uiState.isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MagneticAccent,
                strokeWidth = 2.dp
            )
        }
    }
}

// ── Permission Request UI ─────────────────────────────────────────────

@Composable
private fun MagneticPermissionRequestContent(
    deniedPermissions: List<String>,
    onRequestPermissions: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "MAGNETIC FIELD",
                fontFamily = DataFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MagneticAccent
            )
            Text(
                text = "Camera permission is required to visualize the magnetic field in AR.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Missing: ${deniedPermissions.size} permission(s)",
                fontFamily = DataFontFamily,
                fontSize = 11.sp,
                color = TextSecondary.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MagneticAccent,
                    contentColor = NearBlack
                )
            ) {
                Text(
                    text = "Grant Permission",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
