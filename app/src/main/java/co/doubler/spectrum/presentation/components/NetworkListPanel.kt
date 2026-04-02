package co.doubler.spectrum.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.doubler.spectrum.domain.model.WifiSecurityLevel
import co.doubler.spectrum.presentation.model.GhostNetwork
import co.doubler.spectrum.presentation.model.InterferenceGroup
import co.doubler.spectrum.ui.theme.DataFontFamily
import co.doubler.spectrum.ui.theme.GhostLabelBackground
import co.doubler.spectrum.ui.theme.GhostWaveGreen
import co.doubler.spectrum.ui.theme.NearBlack
import co.doubler.spectrum.ui.theme.TextPrimary
import co.doubler.spectrum.ui.theme.TextSecondary
import co.doubler.spectrum.ui.theme.WarningAmber
import co.doubler.spectrum.ui.theme.toColor

/**
 * Semi-transparent bottom panel that displays detected WiFi networks.
 *
 * Shows user's connected network at the top (green accent), followed by
 * neighbor networks sorted by RSSI descending. Each row displays SSID,
 * channel badge, frequency band, signal strength bar, and RSSI value.
 * Networks involved in channel interference show a warning indicator.
 *
 * @param networks All detected ghost networks, pre-sorted by RSSI desc from ViewModel.
 * @param connectedNetwork The user's currently connected network (highlighted at top).
 * @param interferenceGroups Groups of networks sharing or adjacent on channels.
 * @param modifier Modifier applied to the outer container.
 */
@Composable
fun NetworkListPanel(
    networks: List<GhostNetwork>,
    connectedNetwork: GhostNetwork?,
    interferenceGroups: List<InterferenceGroup>,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * 0.4f).dp
    val panelVisible = remember { MutableTransitionState(false).apply { targetState = true } }

    // Pre-compute which BSSIDs have interference for O(1) lookup
    val interferingBssids = interferenceGroups
        .flatMap { group -> group.networks.map { it.bssid } }
        .toSet()

    AnimatedVisibility(
        visibleState = panelVisible,
        enter = slideInVertically(tween(350)) { it } + fadeIn(tween(350))
    ) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(NearBlack.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NETWORKS",
                fontFamily = DataFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "${networks.size} detected",
                fontFamily = DataFontFamily,
                fontSize = 10.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (networks.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No networks detected. Is WiFi enabled?",
                    fontFamily = DataFontFamily,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Connected network first (if present and in the list)
                if (connectedNetwork != null) {
                    item(key = "connected_${connectedNetwork.bssid}") {
                        NetworkRow(
                            network = connectedNetwork,
                            hasInterference = connectedNetwork.bssid in interferingBssids,
                            isConnected = true
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.5.dp,
                            color = TextSecondary.copy(alpha = 0.2f)
                        )
                    }
                }

                // Remaining networks (exclude connected to avoid duplicate)
                val neighbors = if (connectedNetwork != null) {
                    networks.filter { it.bssid != connectedNetwork.bssid }
                } else {
                    networks
                }

                items(
                    items = neighbors,
                    key = { it.bssid }
                ) { network ->
                    NetworkRow(
                        network = network,
                        hasInterference = network.bssid in interferingBssids,
                        isConnected = false,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
    } // AnimatedVisibility
}

// ── Network Row ─────────────────────────────────────────────────────

@Composable
private fun NetworkRow(
    network: GhostNetwork,
    hasInterference: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = if (isConnected) GhostWaveGreen else TextPrimary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isConnected) GhostWaveGreen.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // WiFi icon + interference warning
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (hasInterference) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Channel interference",
                    modifier = Modifier.size(16.dp),
                    tint = WarningAmber
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = accentColor.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // SSID + connected badge
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = network.ssid.ifEmpty { "Hidden Network" },
                    fontFamily = DataFontFamily,
                    fontSize = 12.sp,
                    fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Normal,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isConnected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Badge(text = "CONNECTED", color = GhostWaveGreen)
                }
            }

            // Channel + Frequency row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(
                    text = "Ch ${network.channel}",
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Badge(
                    text = frequencyBandLabel(network.frequency),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Badge(
                    text = network.securityLevel.name,
                    color = network.securityLevel.toColor()
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Signal strength bar + dBm value
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(72.dp)
        ) {
            Text(
                text = "${network.rssi} dBm",
                fontFamily = DataFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = signalColor(network.signalStrength)
            )
            Spacer(modifier = Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { network.signalStrength },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = signalColor(network.signalStrength),
                trackColor = TextSecondary.copy(alpha = 0.15f)
            )
        }
    }
}

// ── Badge ───────────────────────────────────────────────────────────

@Composable
private fun Badge(
    text: String,
    color: Color
) {
    Text(
        text = text,
        fontFamily = DataFontFamily,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        color = color.copy(alpha = 0.8f),
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

// ── Helpers ─────────────────────────────────────────────────────────

private fun frequencyBandLabel(frequencyMhz: Int): String = when (frequencyMhz) {
    in 2412..2484 -> "2.4 GHz"
    in 5170..5885 -> "5 GHz"
    in 5955..7115 -> "6 GHz"
    else -> "${frequencyMhz}MHz"
}

/**
 * Signal strength color: green (strong) → amber (medium) → red (weak).
 */
private fun signalColor(strength: Float): Color = when {
    strength >= 0.7f -> Color(0xFF76FF03) // Strong — green
    strength >= 0.4f -> Color(0xFFFFAB00) // Medium — amber
    else -> Color(0xFFFF5252)             // Weak — red
}
