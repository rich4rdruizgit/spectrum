package co.doubler.spectrum.presentation.model

import androidx.compose.ui.geometry.Offset

/**
 * Presentation-layer UI state for Compete mode.
 *
 * Compete mode visualizes WiFi coverage competition between multiple
 * access points owned by the user. Each AP is assigned a territory
 * color and dominates the screen area where its signal is strongest.
 */
data class CompeteUiState(
    /** Active APs participating in the coverage competition (up to COMPETE_MAX_APS). */
    val accessPoints: List<CompeteAp> = emptyList(),

    /** Ranked scoreboard entries — sorted by coverage %, descending. */
    val scoreboard: List<CompeteScoreEntry> = emptyList(),

    /** True while waiting for the first WiFi scan result. */
    val isScanning: Boolean = false,

    /** Total networks detected in this scan cycle (before top-N filtering). */
    val totalNetworkCount: Int = 0,

    /** True when any AP has RSSI > -50 (unusually strong signal nearby). */
    val isAnomalyActive: Boolean = false,

    /** Screen-space positions written by GL renderer, keyed by BSSID. */
    val screenPositions: Map<String, Offset> = emptyMap()
)

/**
 * A single access point participating in the coverage competition.
 *
 * Derived from [co.doubler.spectrum.domain.model.WifiSignal] with additional
 * rendering-specific fields: territory color, NDC center position, signal weight.
 */
data class CompeteAp(
    /** Unique key — MAC address of the AP. */
    val bssid: String,

    /** Human-readable network name. Empty string if hidden. */
    val ssid: String,

    /** Raw RSSI in dBm. */
    val rssi: Int,

    /** WiFi channel number. */
    val channel: Int,

    /** Frequency in MHz. */
    val frequency: Int,

    /** Estimated distance in meters from path-loss model. */
    val estimatedDistance: Float,

    /** Normalized signal strength 0.0–1.0 (used as Voronoi weight). */
    val signalWeight: Float,

    /**
     * ARGB territory color packed as Long.
     * Assigned from CompetePalette by slot index (round-robin).
     */
    val color: Long,

    /** Azimuth in degrees 0–360, deterministic from BSSID hash. */
    val azimuthDeg: Float,

    /**
     * Coverage percentage 0.0–1.0: fraction of recent scan frames where
     * this AP held the dominant signal.
     */
    val coveragePercent: Float = 0f
)

/**
 * One row in the coverage scoreboard panel.
 */
data class CompeteScoreEntry(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val color: Long,
    val rank: Int,              // 1-based
    val coveragePercent: Float, // 0.0–1.0
    val rankDelta: Int          // positive = moved up, negative = moved down, 0 = no change
)
