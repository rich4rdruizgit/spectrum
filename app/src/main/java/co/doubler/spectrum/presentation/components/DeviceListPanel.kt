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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.doubler.spectrum.domain.model.BluetoothDeviceType
import co.doubler.spectrum.presentation.model.BluetoothDeviceNode
import co.doubler.spectrum.ui.theme.BluetoothAccent
import co.doubler.spectrum.ui.theme.BluetoothLabelBackground
import co.doubler.spectrum.ui.theme.BluetoothNodeConnected
import co.doubler.spectrum.ui.theme.BluetoothNodeDetected
import co.doubler.spectrum.ui.theme.BluetoothNodeUnknown
import co.doubler.spectrum.ui.theme.DataFontFamily
import co.doubler.spectrum.ui.theme.NearBlack
import co.doubler.spectrum.ui.theme.TextPrimary
import co.doubler.spectrum.ui.theme.TextSecondary
import co.doubler.spectrum.ui.theme.WarningAmber

/**
 * Semi-transparent bottom panel displaying detected Bluetooth devices.
 *
 * Connected devices are listed at the top with a blue accent. Detected (unconnected)
 * devices follow, sorted by RSSI descending. Unknown-type devices are flagged in red.
 *
 * @param devices All detected devices, pre-sorted by RSSI desc from ViewModel.
 * @param connectedDevices Subset of [devices] that are actively connected.
 * @param modifier Modifier applied to the outer container.
 */
@Composable
fun DeviceListPanel(
    devices: List<BluetoothDeviceNode>,
    connectedDevices: List<BluetoothDeviceNode>,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * 0.4f).dp
    val panelVisible = remember { MutableTransitionState(false).apply { targetState = true } }

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
                text = "BLUETOOTH",
                fontFamily = DataFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "${devices.size} detected",
                fontFamily = DataFontFamily,
                fontSize = 10.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No devices detected. Is Bluetooth enabled?",
                    fontFamily = DataFontFamily,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Connected devices first
                val connectedAddresses = connectedDevices.map { it.address }.toSet()
                connectedDevices.forEach { device ->
                    item(key = "connected_${device.address}") {
                        DeviceRow(device = device, isConnected = true)
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.5.dp,
                            color = TextSecondary.copy(alpha = 0.2f)
                        )
                    }
                }

                // Remaining detected devices
                val others = devices.filter { it.address !in connectedAddresses }
                items(items = others, key = { it.address }) { device ->
                    DeviceRow(device = device, isConnected = false, modifier = Modifier.animateItem())
                }
            }
        }
    }
    } // AnimatedVisibility
}

// ── Device Row ───────────────────────────────────────────────────────

@Composable
private fun DeviceRow(
    device: BluetoothDeviceNode,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = when {
        isConnected                                -> BluetoothNodeConnected
        device.type == BluetoothDeviceType.UNKNOWN -> BluetoothNodeUnknown
        else                                       -> TextPrimary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isConnected) BluetoothNodeConnected.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Device type icon
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = deviceTypeIcon(device.type, isConnected),
                contentDescription = device.type.name,
                modifier = Modifier.size(16.dp),
                tint = accentColor.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Device name + type + status badges
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.name,
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
                    DeviceBadge(text = "CONNECTED", color = BluetoothNodeConnected)
                }
                if (device.type == BluetoothDeviceType.UNKNOWN && !isConnected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    DeviceBadge(text = "UNKNOWN", color = BluetoothNodeUnknown)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceBadge(
                    text = deviceTypeLabel(device.type),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                DeviceBadge(
                    text = device.address.takeLast(8),
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Signal strength + RSSI
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(72.dp)
        ) {
            Text(
                text = "${device.rssi} dBm",
                fontFamily = DataFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = btSignalColor(device.signalStrength)
            )
            Spacer(modifier = Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { device.signalStrength },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = btSignalColor(device.signalStrength),
                trackColor = TextSecondary.copy(alpha = 0.15f)
            )
        }
    }
}

// ── Badge ────────────────────────────────────────────────────────────

@Composable
private fun DeviceBadge(
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

// ── Helpers ──────────────────────────────────────────────────────────

private fun deviceTypeIcon(type: BluetoothDeviceType, isConnected: Boolean): ImageVector =
    when {
        isConnected -> Icons.Default.BluetoothConnected
        else -> when (type) {
            BluetoothDeviceType.HEADPHONES -> Icons.Default.Headphones
            BluetoothDeviceType.WATCH      -> Icons.Default.Watch
            BluetoothDeviceType.SPEAKER    -> Icons.Default.Speaker
            BluetoothDeviceType.PHONE      -> Icons.Default.PhoneAndroid
            BluetoothDeviceType.UNKNOWN    -> Icons.Default.DeviceUnknown
        }
    }

private fun deviceTypeLabel(type: BluetoothDeviceType): String = when (type) {
    BluetoothDeviceType.HEADPHONES -> "Headphones"
    BluetoothDeviceType.WATCH      -> "Watch"
    BluetoothDeviceType.SPEAKER    -> "Speaker"
    BluetoothDeviceType.PHONE      -> "Phone"
    BluetoothDeviceType.UNKNOWN    -> "Unknown"
}

/**
 * Signal strength color for Bluetooth: blue (strong) → amber (medium) → gray (weak).
 */
private fun btSignalColor(strength: Float): Color = when {
    strength >= 0.7f -> BluetoothNodeConnected   // Strong — blue
    strength >= 0.4f -> WarningAmber             // Medium — amber
    else             -> BluetoothNodeDetected     // Weak — gray
}
