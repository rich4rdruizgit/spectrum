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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import co.doubler.spectrum.presentation.components.DeviceListPanel
import co.doubler.spectrum.presentation.model.BluetoothDeviceNode
import co.doubler.spectrum.presentation.viewmodel.BluetoothViewModel
import co.doubler.spectrum.rendering.bluetooth.BluetoothOverlayRenderer
import co.doubler.spectrum.ui.theme.BluetoothAccent
import co.doubler.spectrum.ui.theme.BluetoothLabelBackground
import co.doubler.spectrum.ui.theme.BluetoothNodeConnected
import co.doubler.spectrum.ui.theme.BluetoothNodeDetected
import co.doubler.spectrum.ui.theme.BluetoothNodeUnknown
import co.doubler.spectrum.ui.theme.DataFontFamily
import co.doubler.spectrum.ui.theme.NearBlack
import co.doubler.spectrum.ui.theme.TextSecondary
import co.doubler.spectrum.util.PermissionGroups
import co.doubler.spectrum.util.rememberPermissionState
import co.doubler.spectrum.domain.model.BluetoothDeviceType

/**
 * Bluetooth Vision mode screen — BLE/BT device AR visualization.
 *
 * Renders detected Bluetooth devices as glowing floating nodes in AR space,
 * with expanding proximity rings and animated connection lines to paired devices.
 * A bottom panel lists all detected devices with type, RSSI, and connection status.
 *
 * Architecture:
 * - [BluetoothViewModel] provides [BluetoothUiState] via StateFlow + AtomicReferences for GL bridge
 * - [BluetoothOverlayRenderer] draws nodes/rings/lines on the GL thread
 * - [ArSceneView] hosts the GL surface with overlay registration
 * - [DeviceListPanel] displays the device list at the bottom
 * - [BluetoothLabel] renders floating AR labels at projected screen positions
 *
 * Permission flow:
 * - Checks Camera + Bluetooth permissions before showing AR view
 * - Shows permission request UI when permissions are missing
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

// ── AR Content (permissions granted) ────────────────────────────────

@Composable
private fun BluetoothArContent(
    viewModel: BluetoothViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Create renderer once, tied to ViewModel identity.
    // Renderer reads device nodes from AtomicReference on GL thread
    // and writes screen positions back for Compose label positioning.
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
            hudContent = {
                // ── AR Labels — positioned by GL→Compose screen projections ──
                uiState.screenPositions.forEach { (address, position) ->
                    val device = uiState.devices.find { it.address == address }
                    if (device != null) {
                        val screenX = position.x.toInt()
                        val screenY = position.y.toInt()
                        // Only render labels within a sane viewport range
                        if (screenX in 0..4000 && screenY in 0..4000) {
                            BluetoothLabel(
                                device = device,
                                modifier = Modifier.offset {
                                    IntOffset(screenX, screenY)
                                }
                            )
                        }
                    }
                }

                // ── Device list panel at bottom ──
                DeviceListPanel(
                    devices = uiState.devices,
                    connectedDevices = uiState.connectedDevices,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        )

        // ── Loading indicator while initial scan in progress ──
        if (uiState.isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = BluetoothAccent,
                strokeWidth = 2.dp
            )
        }
    }
}

// ── Bluetooth Label (floating AR label) ──────────────────────────────

/**
 * Compact floating label rendered at the projected screen position of a
 * Bluetooth device node. Shows device name, type, RSSI, and connection status
 * with a color-coded accent matching the node's visual color.
 */
@Composable
private fun BluetoothLabel(
    device: BluetoothDeviceNode,
    modifier: Modifier = Modifier
) {
    val labelColor = when {
        device.isConnected                          -> BluetoothNodeConnected
        device.type == BluetoothDeviceType.UNKNOWN  -> BluetoothNodeUnknown
        else                                        -> BluetoothNodeDetected
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(BluetoothLabelBackground)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = device.name,
            fontFamily = DataFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor,
            maxLines = 1
        )
        Text(
            text = "${device.rssi}dBm  ${deviceTypeShortLabel(device.type)}",
            fontFamily = DataFontFamily,
            fontSize = 8.sp,
            color = TextSecondary
        )
        if (device.isConnected) {
            Text(
                text = "CONNECTED",
                fontFamily = DataFontFamily,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = BluetoothNodeConnected,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ── Permission Request UI ─────────────────────────────────────────────

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
                text = "Camera and Bluetooth permissions are required to visualize nearby devices in AR.",
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
                    containerColor = BluetoothAccent,
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

// ── Helpers ───────────────────────────────────────────────────────────

private fun deviceTypeShortLabel(type: BluetoothDeviceType): String = when (type) {
    BluetoothDeviceType.HEADPHONES -> "Headphones"
    BluetoothDeviceType.WATCH      -> "Watch"
    BluetoothDeviceType.SPEAKER    -> "Speaker"
    BluetoothDeviceType.PHONE      -> "Phone"
    BluetoothDeviceType.UNKNOWN    -> "Unknown"
}
