package co.doubler.spectrum.presentation.model

import androidx.compose.ui.geometry.Offset
import co.doubler.spectrum.domain.model.BluetoothDeviceType
import co.doubler.spectrum.domain.model.EcholocationState

data class BluetoothUiState(
    val devices: List<BluetoothDeviceNode> = emptyList(),
    val connectedDevices: List<BluetoothDeviceNode> = emptyList(),
    val isScanning: Boolean = false,
    val totalDeviceCount: Int = 0,
    val screenPositions: Map<String, Offset> = emptyMap(),
    val isAnomalyActive: Boolean = false,
    val echolocationState: EcholocationState = EcholocationState.Idle
)

/**
 * Presentation-layer model for a Bluetooth device in AR space.
 *
 * Derived from [co.doubler.spectrum.domain.model.BluetoothNode] with additional
 * rendering-specific fields: world-space position, NDC color, azimuth.
 */
data class BluetoothDeviceNode(
    val address: String,                   // Unique key (MAC address)
    val name: String,                      // Display name, "Unknown" if null
    val type: BluetoothDeviceType,
    val rssi: Int,                         // dBm
    val isConnected: Boolean,
    val estimatedDistance: Float,          // meters, from path-loss model
    val signalStrength: Float,             // 0.0–1.0 normalized
    val color: Long,                       // ARGB packed — blue/gray/red by status
    val azimuthDeg: Float                  // 0–360, from address hash
)
