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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import co.doubler.spectrum.domain.model.WifiSecurityLevel
import co.doubler.spectrum.presentation.components.ArSceneView
import co.doubler.spectrum.presentation.components.NetworkListPanel
import co.doubler.spectrum.presentation.model.GhostNetwork
import co.doubler.spectrum.presentation.model.InterferenceGroup
import co.doubler.spectrum.presentation.viewmodel.GhostViewModel
import co.doubler.spectrum.rendering.ghost.GhostOverlayRenderer
import co.doubler.spectrum.ui.theme.DataFontFamily
import co.doubler.spectrum.ui.theme.GhostAccent
import co.doubler.spectrum.ui.theme.GhostLabelBackground
import co.doubler.spectrum.ui.theme.GhostWaveGreen
import co.doubler.spectrum.ui.theme.NearBlack
import co.doubler.spectrum.ui.theme.TextPrimary
import co.doubler.spectrum.ui.theme.TextSecondary
import co.doubler.spectrum.ui.theme.WarningAmber
import co.doubler.spectrum.ui.theme.toColor
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

    var selectedNetwork by remember { mutableStateOf<GhostNetwork?>(null) }

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
                                onClick = { selectedNetwork = network },
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

    // ── Network detail bottom sheet ──
    selectedNetwork?.let { network ->
        NetworkDetailSheet(
            network = network,
            interferenceGroups = uiState.interferenceGroups,
            onDismiss = { selectedNetwork = null }
        )
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val waveColor = androidx.compose.ui.graphics.Color(network.color)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(GhostLabelBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Router,
            contentDescription = null,
            tint = waveColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Icon(
            imageVector = lockIconFor(network.securityLevel),
            contentDescription = null,
            tint = network.securityLevel.toColor(),
            modifier = Modifier.size(10.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
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

// ── Network Detail Bottom Sheet ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkDetailSheet(
    network: GhostNetwork,
    interferenceGroups: List<InterferenceGroup>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val waveColor = androidx.compose.ui.graphics.Color(network.color)
    val interferenceGroup = interferenceGroups.find { g -> g.networks.any { it.bssid == network.bssid } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NearBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = null,
                    tint = waveColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = network.ssid.ifEmpty { "Hidden Network" },
                        fontFamily = DataFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = waveColor
                    )
                    Text(
                        text = network.bssid.uppercase(),
                        fontFamily = DataFontFamily,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                if (network.isUserNetwork) {
                    Text(
                        text = "CONNECTED",
                        fontFamily = DataFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = GhostWaveGreen,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(GhostWaveGreen.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))

            // ── Signal ──
            SheetRow(label = "SEÑAL") {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${network.rssi} dBm",
                            fontFamily = DataFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = signalColor(network.signalStrength)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = { network.signalStrength },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = signalColor(network.signalStrength),
                            trackColor = TextSecondary.copy(alpha = 0.15f)
                        )
                    }
                }
            }

            // ── Channel / Band / Distance ──
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SheetStat(label = "CANAL", value = network.channel.toString())
                SheetStat(label = "BANDA", value = frequencyBandLabel(network.frequency))
                SheetStat(label = "DISTANCIA", value = "~${"%.1f".format(network.estimatedDistance)} m")
            }

            HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))

            // ── Security ──
            SheetRow(label = "SEGURIDAD") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = lockIconFor(network.securityLevel),
                        contentDescription = null,
                        tint = network.securityLevel.toColor(),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = securityLabel(network.securityLevel),
                        fontFamily = DataFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = network.securityLevel.toColor()
                    )
                }
            }

            // ── Interference warning ──
            if (interferenceGroup != null) {
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(WarningAmber.copy(alpha = 0.08f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = "INTERFERENCIA — Canal ${interferenceGroup.channel}",
                            fontFamily = DataFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber
                        )
                        Text(
                            text = "${interferenceGroup.networks.size} redes comparten este canal",
                            fontFamily = DataFontFamily,
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontFamily = DataFontFamily,
            fontSize = 10.sp,
            color = TextSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.width(80.dp)
        )
        content()
    }
}

@Composable
private fun SheetStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontFamily = DataFontFamily,
            fontSize = 9.sp,
            color = TextSecondary,
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            fontFamily = DataFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

private fun signalColor(strength: Float) = when {
    strength >= 0.7f -> androidx.compose.ui.graphics.Color(0xFF76FF03)
    strength >= 0.4f -> androidx.compose.ui.graphics.Color(0xFFFFAB00)
    else             -> androidx.compose.ui.graphics.Color(0xFFFF5252)
}

private fun frequencyBandLabel(frequencyMhz: Int): String = when (frequencyMhz) {
    in 2412..2484 -> "2.4 GHz"
    in 5170..5885 -> "5 GHz"
    in 5955..7115 -> "6 GHz"
    else           -> "${frequencyMhz} MHz"
}

private fun lockIconFor(level: WifiSecurityLevel) = when (level) {
    WifiSecurityLevel.OPEN, WifiSecurityLevel.WEP -> Icons.Default.LockOpen
    else -> Icons.Default.Lock
}

private fun securityLabel(level: WifiSecurityLevel) = when (level) {
    WifiSecurityLevel.OPEN   -> "ABIERTA"
    WifiSecurityLevel.WEP    -> "WEP (insegura)"
    WifiSecurityLevel.WEAK   -> "WPA/TKIP (débil)"
    WifiSecurityLevel.GOOD   -> "WPA2-CCMP"
    WifiSecurityLevel.STRONG -> "WPA3"
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
