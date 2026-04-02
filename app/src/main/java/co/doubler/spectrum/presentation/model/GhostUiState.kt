package co.doubler.spectrum.presentation.model

import androidx.compose.ui.geometry.Offset

data class GhostUiState(
    val networks: List<GhostNetwork> = emptyList(),
    val connectedNetwork: GhostNetwork? = null,
    val interferenceGroups: List<InterferenceGroup> = emptyList(),
    val isScanning: Boolean = false,
    val totalNetworkCount: Int = 0,
    val screenPositions: Map<String, Offset> = emptyMap(),
    val isAnomalyActive: Boolean = false
)

data class GhostNetwork(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val channel: Int,
    val frequency: Int,
    val isUserNetwork: Boolean,
    val estimatedDistance: Float,   // meters (path-loss model)
    val signalStrength: Float,     // 0.0-1.0 normalized
    val color: Long,               // ARGB for wave rendering
    val azimuthDeg: Float          // 0-360, from BSSID hash
)

data class InterferenceGroup(
    val channel: Int,
    val networks: List<GhostNetwork>,
    val severity: Float            // 0.0-1.0, from combined RSSI
)
