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
import co.doubler.spectrum.presentation.components.CoverageScoreboard
import co.doubler.spectrum.presentation.viewmodel.CompeteViewModel
import co.doubler.spectrum.rendering.compete.CompeteOverlayRenderer
import co.doubler.spectrum.ui.theme.CompeteAccent
import co.doubler.spectrum.ui.theme.DataFontFamily
import co.doubler.spectrum.ui.theme.NearBlack
import co.doubler.spectrum.ui.theme.TextSecondary
import co.doubler.spectrum.util.PermissionGroups
import co.doubler.spectrum.util.rememberPermissionState

/**
 * Compete mode screen — WiFi coverage territory competition AR visualization.
 *
 * Renders the top N detected WiFi networks as competing coverage territories
 * via a screen-space Voronoi shader overlaid on the AR camera feed. Pulsing
 * white borders appear at handover zones. A top panel scoreboard ranks APs
 * by coverage dominance in real time.
 *
 * Architecture:
 * - [CompeteViewModel] provides [CompeteUiState] + [AtomicReference] bridge for GL
 * - [CompeteOverlayRenderer] draws territory shader on the GL thread
 * - [ArSceneView] hosts the GL surface with overlay registration
 * - [CoverageScoreboard] displays the AP ranking at the top of the screen
 *
 * Permission flow:
 * - Requires Camera + WiFi permissions (same as Ghost mode)
 * - Shows permission request UI when permissions are missing
 */
@Composable
fun CompeteScreen(
    viewModel: CompeteViewModel = hiltViewModel()
) {
    val permissionState = rememberPermissionState(
        PermissionGroups.forMode(ScanMode.COMPETE)
    )

    if (!permissionState.allGranted) {
        CompetePermissionContent(
            deniedPermissions = permissionState.deniedPermissions,
            onRequestPermissions = permissionState.requestPermissions
        )
    } else {
        CompeteArContent(viewModel = viewModel)
    }
}

// ── AR Content (permissions granted) ────────────────────────────────────────

@Composable
private fun CompeteArContent(
    viewModel: CompeteViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Create renderer once, tied to ViewModel identity.
    // Renderer reads APs from AtomicReference on GL thread.
    val competeRenderer = remember(viewModel) {
        CompeteOverlayRenderer(
            context = context,
            accessPointsRef = viewModel.accessPointsRef
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ArSceneView(
            sessionManager = viewModel.sessionManager,
            scanMode = ScanMode.COMPETE,
            isScanning = uiState.isScanning,
            overlayRenderer = competeRenderer,
            isAnomalyActive = uiState.isAnomalyActive,
            hudContent = {
                // ── Coverage scoreboard at the TOP of the screen ──────────
                CoverageScoreboard(
                    entries = uiState.scoreboard,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        )

        // ── Loading indicator while initial scan in progress ──────────────
        if (uiState.isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = CompeteAccent,
                strokeWidth = 2.dp
            )
        }

        // ── Empty state — no APs detected above RSSI threshold ────────────
        if (!uiState.isScanning && uiState.accessPoints.isEmpty()) {
            CompeteEmptyState(
                totalNetworkCount = uiState.totalNetworkCount,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

// ── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun CompeteEmptyState(
    totalNetworkCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "NO APs IN RANGE",
            fontFamily = DataFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = CompeteAccent
        )
        Text(
            text = if (totalNetworkCount > 0) {
                "$totalNetworkCount network${if (totalNetworkCount > 1) "s" else ""} detected\nbut all are below threshold"
            } else {
                "No WiFi networks detected.\nMove closer to your access points."
            },
            fontFamily = DataFontFamily,
            fontSize = 11.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// ── Permission Request UI ────────────────────────────────────────────────────

@Composable
private fun CompetePermissionContent(
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
                text = "COMPETE MODE",
                fontFamily = DataFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CompeteAccent
            )
            Text(
                text = "Camera and WiFi permissions are required to visualize access point coverage territories in AR.",
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
                    containerColor = CompeteAccent,
                    contentColor = NearBlack
                )
            ) {
                Text(
                    text = "Grant Permissions",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
