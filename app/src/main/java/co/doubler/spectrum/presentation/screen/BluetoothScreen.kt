package co.doubler.spectrum.presentation.screen

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.doubler.spectrum.domain.model.BluetoothDeviceType
import co.doubler.spectrum.domain.model.EcholocationState
import co.doubler.spectrum.domain.model.ScanMode
import co.doubler.spectrum.presentation.components.ArSceneView
import co.doubler.spectrum.presentation.components.EcholocationOverlay
import co.doubler.spectrum.presentation.model.BluetoothDeviceNode
import co.doubler.spectrum.presentation.viewmodel.BluetoothViewModel
import co.doubler.spectrum.rendering.bluetooth.BluetoothOverlayRenderer
import co.doubler.spectrum.ui.theme.BluetoothAccent
import co.doubler.spectrum.ui.theme.BluetoothNodeConnected
import co.doubler.spectrum.ui.theme.BluetoothNodeDetected
import co.doubler.spectrum.ui.theme.BluetoothNodeUnknown
import co.doubler.spectrum.ui.theme.DataFontFamily
import co.doubler.spectrum.ui.theme.NearBlack
import co.doubler.spectrum.ui.theme.TextSecondary
import co.doubler.spectrum.util.PermissionGroups
import co.doubler.spectrum.util.rememberPermissionState

/**
 * Bluetooth Vision mode screen — BLE/BT device AR visualization.
 *
 * The GL renderer ([BluetoothOverlayRenderer]) draws glowing blobs per device
 * on the camera feed. On top of that, a Compose constellation overlay adds:
 * - Dashed lines from the "TU" anchor to each device node
 * - Circular nodes with device-type icons and connection status
 * - A stats bar at the bottom summarizing detected/connected/available counts
 */
@Composable
fun BluetoothScreen(
    viewModel: BluetoothViewModel = hiltViewModel()
) {
    val permissionState = rememberPermissionState(
        PermissionGroups.forMode(ScanMode.BLUETOOTH)
    )

    if (!permissionState.allGranted) {
        BluetoothPermissionRequestContent(
            deniedPermissions = permissionState.deniedPermissions,
            onRequestPermissions = permissionState.requestPermissions
        )
    } else {
        BluetoothArContent(viewModel = viewModel)
    }
}

// ── AR Content ───────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun BluetoothArContent(viewModel: BluetoothViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val bluetoothRenderer = remember(viewModel) {
        BluetoothOverlayRenderer(
            context = context,
            bluetoothDevicesRef = viewModel.bluetoothDevicesRef,
            screenPositionsRef = viewModel.screenPositionsRef
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ArSceneView(
            sessionManager = viewModel.sessionManager,
            scanMode = ScanMode.BLUETOOTH,
            isScanning = uiState.isScanning,
            overlayRenderer = bluetoothRenderer,
            isAnomalyActive = uiState.isAnomalyActive,
            subtitle = "BLUETOOTH VISION",
            iconEmoji = "🛸",
            hudContent = {
                // ── Constellation: lines + TU node + device nodes ──
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val pxW = constraints.maxWidth.toFloat()
                    val pxH = constraints.maxHeight.toFloat()
                    val tuPosition = Offset(pxW / 2f, pxH * 0.80f)

                    // Full-screen canvas for dashed lines + TU circle (below nodes)
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

                        uiState.screenPositions.forEach { (address, devicePos) ->
                            val device = uiState.devices.find { it.address == address }
                                ?: return@forEach
                            val lineColor = nodeColor(device).copy(alpha = 0.35f)
                            drawLine(
                                color = lineColor,
                                start = tuPosition,
                                end = devicePos,
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = dashEffect
                            )
                        }

                        // TU outer ring
                        drawCircle(
                            color = BluetoothAccent,
                            radius = 14.dp.toPx(),
                            center = tuPosition,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // TU inner fill
                        drawCircle(
                            color = BluetoothAccent.copy(alpha = 0.25f),
                            radius = 10.dp.toPx(),
                            center = tuPosition
                        )
                    }

                    // "TU" label below the anchor circle
                    Text(
                        text = "TU",
                        fontFamily = DataFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = BluetoothAccent,
                        modifier = Modifier.offset {
                            IntOffset(
                                (tuPosition.x - 10).toInt(),
                                (tuPosition.y.toInt() + 18.dp.roundToPx())
                            )
                        }
                    )

                    // Device constellation nodes
                    uiState.screenPositions.forEach { (address, position) ->
                        val device = uiState.devices.find { it.address == address }
                            ?: return@forEach
                        val screenX = position.x.toInt()
                        val screenY = position.y.toInt()
                        if (screenX in 0..4000 && screenY in 0..4000) {
                            ConstellationNode(
                                device = device,
                                modifier = Modifier.offset {
                                    // Center the node on the screen position
                                    IntOffset(screenX - 20.dp.roundToPx(), screenY - 20.dp.roundToPx())
                                }
                            )
                        }
                    }
                }

                // ── Device list + stats bar at bottom ──
                Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                    BtDevicePanel(devices = uiState.devices, onLocate = viewModel::activateEcholocation)
                    BtStatsBar(
                        total = uiState.totalDeviceCount,
                        connected = uiState.connectedDevices.size
                    )
                }
            }
        )

        if (uiState.isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = BluetoothAccent,
                strokeWidth = 2.dp
            )
        }

        if (uiState.echolocationState !is EcholocationState.Idle) {
            EcholocationOverlay(
                state = uiState.echolocationState,
                onDismiss = viewModel::deactivateEcholocation
            )
        }
    }
}

// ── Constellation Node ───────────────────────────────────────────────

/**
 * Circular node rendered at a device's projected screen position.
 * Shows device-type icon inside a colored ring, a green dot if connected,
 * and name + dBm below.
 */
@Composable
private fun ConstellationNode(
    device: BluetoothDeviceNode,
    modifier: Modifier = Modifier
) {
    val color = nodeColor(device)
    val nodeSize = 40.dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Outer ring
            Box(
                modifier = Modifier
                    .size(nodeSize)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f))
            )
            // Device type icon
            Icon(
                imageVector = deviceIcon(device.type),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            // Connected green dot — top-right
            if (device.isConnected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(BluetoothNodeConnected)
                )
            }
        }

        // Name
        Text(
            text = device.name.take(12),
            fontFamily = DataFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        // dBm
        Text(
            text = "${device.rssi} dBm",
            fontFamily = DataFontFamily,
            fontSize = 8.sp,
            color = TextSecondary
        )
    }
}

// ── Device Panel (collapsible) ───────────────────────────────────────

@Composable
private fun BtDevicePanel(
    devices: List<BluetoothDeviceNode>,
    onLocate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NearBlack.copy(alpha = 0.88f))
    ) {
        // Handle row — always visible
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🛸", fontSize = 12.sp)
                Text(
                    text = "DISPOSITIVOS CERCANOS",
                    fontFamily = DataFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BluetoothAccent,
                    letterSpacing = 0.5.sp
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${devices.size} disp.",
                    fontFamily = DataFontFamily,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown
                                  else          Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Expandable list
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                items(devices, key = { it.address }) { device ->
                    BtDeviceRow(device = device, onLocate = onLocate)
                    HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun BtDeviceRow(device: BluetoothDeviceNode, onLocate: (String) -> Unit) {
    val color = nodeColor(device)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = deviceIcon(device.type),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(15.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                fontFamily = DataFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = 1
            )
            Text(
                text = device.type.name.lowercase()
                    .replaceFirstChar { it.uppercase() },
                fontFamily = DataFontFamily,
                fontSize = 9.sp,
                color = TextSecondary
            )
        }
        if (device.isConnected) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(BluetoothNodeConnected)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${device.rssi} dBm",
                fontFamily = DataFontFamily,
                fontSize = 10.sp,
                color = color
            )
            Text(
                text = "${"%.1f".format(device.estimatedDistance)} m",
                fontFamily = DataFontFamily,
                fontSize = 9.sp,
                color = TextSecondary
            )
        }
        Button(
            onClick = { onLocate(device.address) },
            modifier = Modifier.height(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BluetoothAccent,
                contentColor = NearBlack
            ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                text = "LOCALIZAR",
                fontFamily = DataFontFamily,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Stats Bar ────────────────────────────────────────────────────────

@Composable
private fun BtStatsBar(
    total: Int,
    connected: Int
) {
    val available = (total - connected).coerceAtLeast(0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NearBlack.copy(alpha = 0.85f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatChip(value = total, label = "DETECTADOS", color = BluetoothAccent)
        StatDot()
        StatChip(value = connected, label = "CONECTADOS", color = BluetoothNodeConnected)
        StatDot()
        StatChip(value = available, label = "DISPONIBLES", color = TextSecondary)
    }
}

@Composable
private fun StatChip(value: Int, label: String, color: Color) {
    Text(
        text = "$value $label",
        fontFamily = DataFontFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun StatDot() {
    Text(
        text = "  •  ",
        fontFamily = DataFontFamily,
        fontSize = 10.sp,
        color = TextSecondary
    )
}

// ── Permission Request UI ────────────────────────────────────────────

@Composable
private fun BluetoothPermissionRequestContent(
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
                text = "BLUETOOTH VISION",
                fontFamily = DataFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = BluetoothAccent
            )
            Text(
                text = "Se necesitan permisos de cámara y Bluetooth para visualizar los dispositivos cercanos en AR.",
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
                    containerColor = BluetoothAccent,
                    contentColor = NearBlack
                )
            ) {
                Text(text = "Conceder permisos", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

private fun nodeColor(device: BluetoothDeviceNode): Color = when {
    device.isConnected                         -> BluetoothNodeConnected
    device.type == BluetoothDeviceType.UNKNOWN -> BluetoothNodeUnknown
    else                                       -> BluetoothNodeDetected
}

private fun deviceIcon(type: BluetoothDeviceType): ImageVector = when (type) {
    BluetoothDeviceType.HEADPHONES -> Icons.Default.Headphones
    BluetoothDeviceType.WATCH      -> Icons.Default.Watch
    BluetoothDeviceType.SPEAKER    -> Icons.Default.Speaker
    BluetoothDeviceType.PHONE      -> Icons.Default.PhoneAndroid
    BluetoothDeviceType.UNKNOWN    -> Icons.Default.Bluetooth
}
