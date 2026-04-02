package co.doubler.spectrum.presentation.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.doubler.spectrum.ar.ArSessionManager
import co.doubler.spectrum.domain.model.WifiSignal
import co.doubler.spectrum.domain.repository.WifiRepository
import co.doubler.spectrum.presentation.model.GhostNetwork
import co.doubler.spectrum.presentation.model.GhostUiState
import co.doubler.spectrum.presentation.model.InterferenceGroup
import co.doubler.spectrum.util.Constants.GHOST_DISTANCE_MAX_M
import co.doubler.spectrum.util.Constants.GHOST_DISTANCE_MIN_M
import co.doubler.spectrum.util.Constants.GHOST_MAX_WAVES
import co.doubler.spectrum.util.Constants.GHOST_RSSI_MAX
import co.doubler.spectrum.util.Constants.GHOST_RSSI_MIN
import co.doubler.spectrum.util.SignalUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@HiltViewModel
class GhostViewModel @Inject constructor(
    private val wifiRepository: WifiRepository,
    val sessionManager: ArSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GhostUiState(isScanning = true))

    val uiState: StateFlow<GhostUiState> = _uiState.asStateFlow()

    /** Written by ViewModel on main thread, read by GhostOverlayRenderer on GL thread. */
    val ghostNetworksRef: AtomicReference<List<GhostNetwork>> = AtomicReference(emptyList())

    /** Written by GhostOverlayRenderer on GL thread, read by ViewModel on main thread. */
    val screenPositionsRef: AtomicReference<Map<String, Offset>> = AtomicReference(emptyMap())

    private var hasReceivedFirstEmission = false

    init {
        observeWifiScans()
    }

    private fun observeWifiScans() {
        combine(
            wifiRepository.scanNetworks(),
            wifiRepository.observeConnected()
        ) { scans, connected ->
            buildGhostState(scans, connected)
        }
            .onEach { state ->
                _uiState.value = state
                ghostNetworksRef.set(state.networks)
            }
            .launchIn(viewModelScope)
    }

    private fun buildGhostState(
        scans: List<WifiSignal>,
        connected: WifiSignal?
    ): GhostUiState {
        hasReceivedFirstEmission = true

        if (scans.isEmpty()) {
            return GhostUiState(
                networks = emptyList(),
                connectedNetwork = null,
                interferenceGroups = emptyList(),
                isScanning = false,
                totalNetworkCount = 0,
                screenPositions = screenPositionsRef.get()
            )
        }

        val ghostNetworks = scans
            .map { signal -> mapToGhostNetwork(signal, connected) }
            .sortedByDescending { it.rssi }

        val capped = ghostNetworks.take(GHOST_MAX_WAVES)
        val connectedGhost = capped.find { it.isUserNetwork }
        val interferenceGroups = detectInterference(capped)

        return GhostUiState(
            networks = capped,
            connectedNetwork = connectedGhost,
            interferenceGroups = interferenceGroups,
            isScanning = false,
            totalNetworkCount = scans.size,
            screenPositions = screenPositionsRef.get()
        )
    }

    private fun mapToGhostNetwork(signal: WifiSignal, connected: WifiSignal?): GhostNetwork {
        val isUser = connected != null && signal.bssid == connected.bssid
        val azimuth = estimateAzimuth(signal.bssid)
        val distance = estimateDistance(signal.rssi)
        val strength = normalizeSignalStrength(signal.rssi)
        val color = assignColor(isUser, strength)

        return GhostNetwork(
            bssid = signal.bssid,
            ssid = signal.ssid,
            rssi = signal.rssi,
            channel = signal.channel,
            frequency = signal.frequency,
            isUserNetwork = isUser,
            estimatedDistance = distance,
            signalStrength = strength,
            color = color,
            azimuthDeg = azimuth
        )
    }

    // ── Position computation ──────────────────────────────────────

    /**
     * Deterministic azimuth from BSSID hash.
     * Same BSSID always produces the same angle (0-360).
     */
    fun estimateAzimuth(bssid: String): Float {
        return (bssid.hashCode() and 0x7FFFFFFF) % 360f
    }

    /**
     * Map RSSI to estimated distance in meters using log-distance path-loss model.
     * Clamped to [GHOST_DISTANCE_MIN_M, GHOST_DISTANCE_MAX_M].
     */
    fun estimateDistance(rssi: Int): Float {
        return SignalUtils.estimateDistance(rssi)
            .toFloat()
            .coerceIn(GHOST_DISTANCE_MIN_M, GHOST_DISTANCE_MAX_M)
    }

    /**
     * Convert polar coordinates (azimuth + distance) to a 3D world point.
     * Y=0 (ground plane), Z is negative forward (camera convention).
     */
    fun azimuthToWorldPoint(azimuthDeg: Float, distance: Float): FloatArray {
        val rad = Math.toRadians(azimuthDeg.toDouble())
        return floatArrayOf(
            (distance * sin(rad)).toFloat(),
            0f,
            -(distance * cos(rad)).toFloat()
        )
    }

    // ── Signal processing ─────────────────────────────────────────

    /**
     * Normalize RSSI to 0.0-1.0 range.
     * -90 dBm -> 0.0 (weakest), -30 dBm -> 1.0 (strongest).
     */
    fun normalizeSignalStrength(rssi: Int): Float {
        return ((rssi - GHOST_RSSI_MIN).toFloat() / (GHOST_RSSI_MAX - GHOST_RSSI_MIN))
            .coerceIn(0f, 1f)
    }

    /**
     * Assign wave color based on network ownership and signal strength.
     * User network: green (GhostWaveGreen).
     * Neighbors: gradient from orange (weak) to red (strong).
     */
    fun assignColor(isUserNetwork: Boolean, signalStrength: Float): Long {
        if (isUserNetwork) return 0xFF76FF03 // GhostWaveGreen

        // Lerp from orange (0xFFFFAB00) at weak to red (0xFFFF1744) at strong
        val r = 0xFF
        val g = (0xAB * (1f - signalStrength)).toInt().coerceIn(0, 0xFF)
        val b = (0x44 * (1f - signalStrength)).toInt().coerceIn(0, 0xFF)
        return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    // ── Interference detection ────────────────────────────────────

    /**
     * Detect channel interference between networks.
     * Groups networks sharing the same channel within the same band.
     * Also detects adjacent-channel interference:
     *   - 2.4 GHz: +/- 1 channel overlap
     *   - 5/6 GHz: +/- 2 channel overlap
     * Single networks on a channel produce NO interference group.
     */
    fun detectInterference(networks: List<GhostNetwork>): List<InterferenceGroup> {
        if (networks.size < 2) return emptyList()

        val groups = mutableListOf<InterferenceGroup>()

        // Group by band to prevent cross-band false positives
        val byBand = networks.groupBy { bandOf(it.frequency) }

        for ((band, bandNetworks) in byBand) {
            if (band == Band.UNKNOWN) continue

            val adjacencyRange = when (band) {
                Band.TWO_GHZ -> 1
                Band.FIVE_GHZ, Band.SIX_GHZ -> 2
                Band.UNKNOWN -> continue
            }

            // Build adjacency groups: networks that share or are adjacent on channel
            val processed = mutableSetOf<String>()

            for (network in bandNetworks) {
                if (network.bssid in processed) continue

                val interfering = bandNetworks.filter { other ->
                    other.bssid != network.bssid &&
                        kotlin.math.abs(other.channel - network.channel) <= adjacencyRange
                }

                if (interfering.isNotEmpty()) {
                    val group = listOf(network) + interfering
                    val allBssids = group.map { it.bssid }

                    // Only create group if not all members already processed
                    if (!allBssids.all { it in processed }) {
                        val severity = min(1.0f, group.size / 4.0f)
                        // Adjacent (non-same) channel groups get reduced severity
                        val hasSameChannel = group.any { it.channel == network.channel && it.bssid != network.bssid }
                        val adjustedSeverity = if (hasSameChannel) severity else severity * 0.5f

                        groups.add(
                            InterferenceGroup(
                                channel = network.channel,
                                networks = group,
                                severity = adjustedSeverity.coerceIn(0f, 1f)
                            )
                        )
                        processed.addAll(allBssids)
                    }
                }
            }
        }

        return groups
    }

    private enum class Band { TWO_GHZ, FIVE_GHZ, SIX_GHZ, UNKNOWN }

    private fun bandOf(frequencyMhz: Int): Band = when (frequencyMhz) {
        in 2412..2484 -> Band.TWO_GHZ
        in 5170..5885 -> Band.FIVE_GHZ
        in 5955..7115 -> Band.SIX_GHZ
        else -> Band.UNKNOWN
    }
}
