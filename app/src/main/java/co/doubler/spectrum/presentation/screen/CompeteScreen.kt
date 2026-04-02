package co.doubler.spectrum.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.doubler.spectrum.domain.model.ScanMode
import co.doubler.spectrum.presentation.components.ArSceneView
import co.doubler.spectrum.presentation.components.CoverageScoreboard
import co.doubler.spectrum.presentation.model.CompeteAp
import co.doubler.spectrum.presentation.viewmodel.CompeteViewModel
import co.doubler.spectrum.rendering.compete.CompeteOverlayRenderer
import co.doubler.spectrum.ui.theme.CompeteAccent
import co.doubler.spectrum.ui.theme.DataFontFamily
import co.doubler.spectrum.ui.theme.NearBlack
import co.doubler.spectrum.ui.theme.TextSecondary
import co.doubler.spectrum.util.PermissionGroups
import co.doubler.spectrum.util.rememberPermissionState
import kotlin.math.roundToInt

/**
 * Compete mode screen — WiFi coverage territory competition AR visualization.
 *
 * Renders the top N detected WiFi networks as competing coverage territories
 * via a screen-space Voronoi shader overlaid on the AR camera feed. Pulsing
 * white borders appear at handover zones. A bottom collapsible scoreboard ranks
 * APs by coverage dominance in real time. Floating AR labels show each AP's
 * coverage % and name at its projected screen position.
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

    val competeRenderer = remember(viewModel) {
        CompeteOverlayRenderer(
            context = context,
            accessPointsRef = viewModel.accessPointsRef,
            screenPositionsRef = viewModel.screenPositionsRef
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ArSceneView(
            sessionManager = viewModel.sessionManager,
            scanMode = ScanMode.COMPETE,
            isScanning = uiState.isScanning,
            overlayRenderer = competeRenderer,
            isAnomalyActive = uiState.isAnomalyActive,
            subtitle = "MODO COMPETENCIA",
            iconEmoji = "✂",
            hudContent = {
                // ── Floating AP labels at projected AR positions ──
                uiState.screenPositions.forEach { (bssid, position) ->
                    val ap = uiState.accessPoints.find { it.bssid == bssid }
                    if (ap != null) {
                        val screenX = position.x.toInt()
                        val screenY = position.y.toInt()
                        if (screenX in 0..4000 && screenY in 0..4000) {
                            CompeteApLabel(
                                ap = ap,
                                modifier = Modifier.offset { IntOffset(screenX, screenY) }
                            )
                        }
                    }
                }

                // ── Collapsible scoreboard at bottom ──
                CoverageScoreboard(
                    entries = uiState.scoreboard,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        )

        // ── Loading indicator ──
        if (uiState.isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = CompeteAccent,
                strokeWidth = 2.dp
            )
        }

        // ── Empty state ──
        if (!uiState.isScanning && uiState.accessPoints.isEmpty()) {
            CompeteEmptyState(
                totalNetworkCount = uiState.totalNetworkCount,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

// ── Floating AR label per AP ─────────────────────────────────────────────────

/**
 * Floating label rendered at the projected screen position of an AP.
 * Shows coverage % large above a colored dot, and SSID + dBm below.
 */
@Composable
private fun CompeteApLabel(
    ap: CompeteAp,
    modifier: Modifier = Modifier
) {
    val apColor = Color(ap.color)
    val pct = (ap.coveragePercent * 100).roundToInt()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Coverage %
        Text(
            text = "$pct%",
            fontFamily = DataFontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = apColor
        )
        // Colored dot
        Box(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .background(apColor, shape = androidx.compose.foundation.shape.CircleShape)
                .padding(5.dp)
        )
        // SSID
        Text(
            text = ap.ssid.ifEmpty { "Hidden" }.take(12),
            fontFamily = DataFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = apColor
        )
        // dBm
        Text(
            text = "${ap.rssi} dBm",
            fontFamily = DataFontFamily,
            fontSize = 9.sp,
            color = apColor.copy(alpha = 0.65f)
        )
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
            text = "SIN APs EN RANGO",
            fontFamily = DataFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = CompeteAccent
        )
        Text(
            text = if (totalNetworkCount > 0) {
                "$totalNetworkCount redes detectadas\npero todas están bajo el umbral"
            } else {
                "No se detectaron redes WiFi.\nAcercate a tus access points."
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
                text = "MODO COMPETENCIA",
                fontFamily = DataFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CompeteAccent
            )
            Text(
                text = "Se necesitan permisos de cámara y WiFi para visualizar los territorios de cobertura en AR.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Faltan: ${deniedPermissions.size} permiso(s)",
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
                    text = "Conceder permisos",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
