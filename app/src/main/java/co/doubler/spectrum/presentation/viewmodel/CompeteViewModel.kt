package co.doubler.spectrum.presentation.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.doubler.spectrum.ar.ArSessionManager
import co.doubler.spectrum.domain.model.WifiSignal
import co.doubler.spectrum.domain.repository.WifiRepository
import co.doubler.spectrum.presentation.model.CompeteAp
import co.doubler.spectrum.presentation.model.CompeteScoreEntry
import co.doubler.spectrum.presentation.model.CompeteUiState
import co.doubler.spectrum.util.Constants.COMPETE_AP_RSSI_MAX
import co.doubler.spectrum.util.Constants.COMPETE_AP_RSSI_MIN
import co.doubler.spectrum.util.Constants.COMPETE_COVERAGE_HISTORY
import co.doubler.spectrum.util.Constants.COMPETE_MAX_APS
import co.doubler.spectrum.util.Constants.COMPETE_TOP_APS
import co.doubler.spectrum.util.Constants.GHOST_DISTANCE_MAX_M
import co.doubler.spectrum.util.Constants.GHOST_DISTANCE_MIN_M
import co.doubler.spectrum.util.SignalUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

/**
 * ViewModel for Compete mode — WiFi coverage territory competition.
 *
 * Takes the top [COMPETE_TOP_APS] networks by RSSI as "user's APs" and models
 * them as competing coverage territories. Tracks a rolling coverage history to
 * compute per-AP coverage percentages for the scoreboard.
 *
 * Thread model:
 * - [_uiState] is written on Main, read by CompeteScreen on Main.
 * - [accessPointsRef] is written on Main, read by CompeteOverlayRenderer on GL thread.
 */
@HiltViewModel
class CompeteViewModel @Inject constructor(
    private val wifiRepository: WifiRepository,
    val sessionManager: ArSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompeteUiState(isScanning = true))

    val uiState: StateFlow<CompeteUiState> = _uiState.asStateFlow()

    /**
     * Written by ViewModel on main thread, read by CompeteOverlayRenderer on GL thread.
     * Contains the current set of APs with their NDC positions + weights.
     */
    val accessPointsRef: AtomicReference<List<CompeteAp>> = AtomicReference(emptyList())

    /** Written by CompeteOverlayRenderer on GL thread, read by ViewModel on main thread. */
    val screenPositionsRef: AtomicReference<Map<String, Offset>> = AtomicReference(emptyMap())

    // ── Coverage history tracking ─────────────────────────────────────────────

    /**
     * Rolling window: for each scan frame, which BSSID "won" (had top RSSI).
     * Used to compute per-AP coverage percentage across recent frames.
     */
    private val winHistory = ArrayDeque<String>(COMPETE_COVERAGE_HISTORY)

    /** Previous scoreboard order — used to compute rank deltas. */
    private var previousRankOrder: List<String> = emptyList()

    init {
        observeWifiScans()
    }

    private fun observeWifiScans() {
        wifiRepository.scanNetworks()
            .onEach { scans -> handleScanResult(scans) }
            .launchIn(viewModelScope)
    }

    private fun handleScanResult(scans: List<WifiSignal>) {
        if (scans.isEmpty()) {
            _uiState.value = CompeteUiState(
                accessPoints = emptyList(),
                scoreboard = emptyList(),
                isScanning = false,
                totalNetworkCount = 0,
                screenPositions = screenPositionsRef.get()
            )
            accessPointsRef.set(emptyList())
            return
        }

        // Take top N by RSSI — these are "user's APs"
        val topNetworks = scans
            .filter { it.rssi >= COMPETE_AP_RSSI_MIN }
            .sortedByDescending { it.rssi }
            .take(minOf(COMPETE_TOP_APS, COMPETE_MAX_APS))

        if (topNetworks.isEmpty()) {
            _uiState.value = CompeteUiState(
                accessPoints = emptyList(),
                scoreboard = emptyList(),
                isScanning = false,
                totalNetworkCount = scans.size,
                screenPositions = screenPositionsRef.get()
            )
            accessPointsRef.set(emptyList())
            return
        }

        // Record winner for this frame (strongest AP by RSSI)
        val winner = topNetworks.first()
        recordWin(winner.bssid)

        // Build CompeteAp models
        val aps = topNetworks.mapIndexed { index, signal ->
            mapToCompeteAp(signal, index)
        }

        // Compute coverage percentages from history
        val coverage = computeCoveragePercents(aps.map { it.bssid })
        val apsWithCoverage = aps.map { ap ->
            ap.copy(coveragePercent = coverage[ap.bssid] ?: 0f)
        }

        // Build scoreboard
        val scoreboard = buildScoreboard(apsWithCoverage)

        val state = CompeteUiState(
            accessPoints = apsWithCoverage,
            scoreboard = scoreboard,
            isScanning = false,
            totalNetworkCount = scans.size,
            screenPositions = screenPositionsRef.get()
        )

        _uiState.value = state
        accessPointsRef.set(apsWithCoverage)

        // Update rank order for next frame's delta computation
        previousRankOrder = scoreboard.map { it.bssid }
    }

    private fun mapToCompeteAp(signal: WifiSignal, paletteIndex: Int): CompeteAp {
        val azimuth = estimateAzimuth(signal.bssid)
        val distance = estimateDistance(signal.rssi)
        val weight = normalizeSignalStrength(signal.rssi)
        val color = COMPETE_PALETTE[paletteIndex % COMPETE_PALETTE.size]

        return CompeteAp(
            bssid = signal.bssid,
            ssid = signal.ssid,
            rssi = signal.rssi,
            channel = signal.channel,
            frequency = signal.frequency,
            estimatedDistance = distance,
            signalWeight = weight,
            color = color,
            azimuthDeg = azimuth
        )
    }

    private fun buildScoreboard(aps: List<CompeteAp>): List<CompeteScoreEntry> {
        val ranked = aps.sortedByDescending { it.coveragePercent }
        return ranked.mapIndexed { index, ap ->
            val rank = index + 1
            val prevRank = previousRankOrder.indexOf(ap.bssid).let {
                if (it < 0) rank else it + 1
            }
            CompeteScoreEntry(
                bssid = ap.bssid,
                ssid = ap.ssid,
                rssi = ap.rssi,
                color = ap.color,
                rank = rank,
                coveragePercent = ap.coveragePercent,
                rankDelta = prevRank - rank   // positive = moved up in ranking
            )
        }
    }

    // ── Coverage history ──────────────────────────────────────────────────────

    private fun recordWin(bssid: String) {
        if (winHistory.size >= COMPETE_COVERAGE_HISTORY) {
            winHistory.removeFirst()
        }
        winHistory.addLast(bssid)
    }

    private fun computeCoveragePercents(bssids: List<String>): Map<String, Float> {
        if (winHistory.isEmpty()) return bssids.associateWith { 0f }
        val counts = bssids.associateWith { bssid ->
            winHistory.count { it == bssid }.toFloat()
        }
        val total = winHistory.size.toFloat()
        return counts.mapValues { (_, count) -> count / total }
    }

    // ── Position / signal helpers ─────────────────────────────────────────────

    /**
     * Deterministic azimuth from BSSID hash.
     * Same BSSID always produces the same angle (0–360°).
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

    /**
     * Normalize RSSI to 0.0–1.0 range used as Voronoi weight.
     * [COMPETE_AP_RSSI_MIN] dBm → ~0.1 (weakest), [COMPETE_AP_RSSI_MAX] dBm → 1.0 (strongest).
     * Clamped so even weak APs have minimum weight of 0.1 to remain visible.
     */
    fun normalizeSignalStrength(rssi: Int): Float {
        val norm = ((rssi - COMPETE_AP_RSSI_MIN).toFloat() / (COMPETE_AP_RSSI_MAX - COMPETE_AP_RSSI_MIN))
            .coerceIn(0f, 1f)
        // Map 0–1 → 0.1–1.0 so every AP has some territory even at weak signal
        return 0.1f + norm * 0.9f
    }

    companion object {
        /**
         * 8-slot territory color palette (ARGB packed as Long).
         * Matches CompeteAp1..CompeteAp8 in Color.kt.
         */
        val COMPETE_PALETTE = longArrayOf(
            0xFF00FF88L, // CompeteAp1 — Green
            0xFF00E5FFL, // CompeteAp2 — Cyan
            0xFFFFD600L, // CompeteAp3 — Amber
            0xFFFF6D00L, // CompeteAp4 — Orange
            0xFFE040FBL, // CompeteAp5 — Purple
            0xFFFF1744L, // CompeteAp6 — Red
            0xFF64DD17L, // CompeteAp7 — Lime
            0xFF00B0FFL  // CompeteAp8 — Sky
        )
    }
}
