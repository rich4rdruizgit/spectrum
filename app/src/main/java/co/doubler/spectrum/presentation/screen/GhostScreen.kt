package co.doubler.spectrum.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import co.doubler.spectrum.presentation.components.NetworkListPanel
import co.doubler.spectrum.presentation.model.GhostNetwork
import co.doubler.spectrum.presentation.model.InterferenceGroup
import co.doubler.spectrum.presentation.viewmodel.GhostViewModel
import co.doubler.spectrum.rendering.ghost.GhostOverlayRenderer
import co.doubler.spectrum.ui.theme.DataFontFamily
import co.doubler.spectrum.ui.theme.GhostAccent
import co.doubler.spectrum.ui.theme.GhostLabelBackground
import co.doubler.spectrum.ui.theme.NearBlack
import co.doubler.spectrum.ui.theme.TextSecondary
import co.doubler.spectrum.ui.theme.WarningAmber
import co.doubler.spectrum.util.PermissionGroups
import co.doubler.spectrum.util.rememberPermissionState

/**
 * Ghost mode screen — WiFi AR visualization.
 *
 * Renders detected WiFi networks as animated concentric wave entities
 * overlaid on the AR camera feed, with floating labels and a bottom
 * panel listing all networks with interference indicators.
 *
 * Architecture:
 * - [GhostViewModel] provides [GhostUiState] via StateFlow + AtomicReferences for GL bridge
 * - [GhostOverlayRenderer] draws wave shader on the GL thread
 * - [ArSceneView] hosts the GL surface with overlay registration
 * - [GhostLabel] renders floating AR labels at projected screen positions
 *
 * Permission flow:
 * - Checks Camera + WiFi permissions before showing AR view
 * - Shows permission request UI when permissions are missing
 */
@Composable
fun GhostScreen(
    viewModel: GhostViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionState = rememberPermissionState(
        PermissionGroups.forMode(ScanMode.GHOST)
    )

    if (!permissionState.allGranted) {
        PermissionRequestContent(
            deniedPermissions = permissionState.deniedPermissions,
            onRequestPermissions = permissionState.requestPermissions
        )
    } else {
        GhostArContent(viewModel = viewModel)
    }
}

// ── AR Content (permissions granted) ────────────────────────────────

@Composable
private fun GhostArContent(
    viewModel: GhostViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Create renderer once, tied to ViewModel identity.
    // Renderer reads ghost networks from AtomicReference on GL thread
    // and writes screen positions back for Compose label positioning.
    val ghostRenderer = remember(viewModel) {
        GhostOverlayRenderer(
            context = context,
            ghostNetworksRef = viewModel.ghostNetworksRef,
            screenPositionsRef = viewModel.screenPositionsRef
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ArSceneView(
            sessionManager = viewModel.sessionManager,
            scanMode = ScanMode.GHOST,
            isScanning = uiState.isScanning,
            overlayRenderer = ghostRenderer,
            isAnomalyActive = uiState.isAnomalyActive,
            subtitle = "REDES FANTASMA",
            iconEmoji = "👻",
            hudContent = {
                // ── AR Labels — positioned by GL→Compose screen projections ──
                uiState.screenPositions.forEach { (bssid, position) ->
                    val network = uiState.networks.find { it.bssid == bssid }
                    if (network != null) {
                        // Only render labels within viewport bounds (avoid off-screen draws)
                        val screenX = position.x.toInt()
                        val screenY = position.y.toInt()
                        if (screenX in 0..4000 && screenY in 0..4000) {
                            GhostLabel(
                                network = network,
                                modifier = Modifier.offset {
                                    IntOffset(screenX, screenY)
                                }
                            )
                        }
                    }
                }

                // ── Collapsible network panel at bottom ──
                CollapsibleNetworkPanel(
                    networks = uiState.networks,
                    connectedNetwork = uiState.connectedNetwork,
                    interferenceGroups = uiState.interferenceGroups,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        )

        // ── Loading indicator while initial scan in progress ──
        if (uiState.isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = GhostAccent,
                strokeWidth = 2.dp
            )
        }
    }
}

// ── Ghost Label (floating AR label) ─────────────────────────────────

/**
 * Compact floating label rendered at the projected screen position of a
 * ghost network. Shows SSID, channel, and signal strength with a colored
 * border matching the network's wave color.
 */
@Composable
private fun GhostLabel(
    network: GhostNetwork,
    modifier: Modifier = Modifier
) {
    val waveColor = androidx.compose.ui.graphics.Color(network.color)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(GhostLabelBackground)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Router,
            contentDescription = null,
            tint = waveColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = network.ssid.ifEmpty { "Hidden" },
            fontFamily = DataFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = waveColor,
            maxLines = 1
        )
    }
}

// ── Collapsible Network Panel ────────────────────────────────────────

/**
 * Bottom panel that collapses to a single handle bar by default.
 * Tapping the handle toggles the full [NetworkListPanel].
 */
@Composable
private fun CollapsibleNetworkPanel(
    networks: List<GhostNetwork>,
    connectedNetwork: GhostNetwork?,
    interferenceGroups: List<InterferenceGroup>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val hasInterference = interferenceGroups.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Expanded list ──
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            NetworkListPanel(
                networks = networks,
                connectedNetwork = connectedNetwork,
                interferenceGroups = interferenceGroups
            )
        }

        // ── Handle bar — always visible, tappable ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NearBlack.copy(alpha = 0.85f))
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasInterference) {
                Text(
                    text = "INTERFERENCIA",
                    fontFamily = DataFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmber,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "${networks.size} redes",
                fontFamily = DataFontFamily,
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = if (expanded) "Colapsar" else "Expandir",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ── Permission Request UI ───────────────────────────────────────────

@Composable
private fun PermissionRequestContent(
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
                text = "GHOST MODE",
                fontFamily = DataFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = GhostAccent
            )
            Text(
                text = "Camera and WiFi permissions are required to visualize nearby networks in AR.",
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
                    containerColor = GhostAccent,
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
