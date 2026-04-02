package co.doubler.spectrum.util

import android.hardware.SensorManager

object Constants {
    // ── WiFi ─────────────────────────────────────────────────────
    const val WIFI_SCAN_INTERVAL_MS = 30_000L
    const val RSSI_SMOOTHING_FACTOR = 0.3
    const val PATH_LOSS_EXPONENT_FREE_SPACE = 2.0
    const val PATH_LOSS_EXPONENT_INDOOR = 3.0

    // ── Bluetooth ────────────────────────────────────────────────
    const val BLE_SCAN_PERIOD_MS = 10_000L
    const val BLE_SCAN_PAUSE_MS = 5_000L
    const val BLE_NEARBY_TIMEOUT_MS = 30_000L
    const val DEFAULT_TX_POWER = -59 // dBm at 1 m

    // ── WiFi Connected ──────────────────────────────────────────
    const val WIFI_CONNECTED_CHECK_INTERVAL_MS = 5_000L

    // ── Magnetometer ─────────────────────────────────────────────
    val MAG_SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME
    const val MAG_HISTORY_DEFAULT_SIZE = 100
    const val MAG_SAFE_LIMIT_UT = 200.0
    const val MAG_LOW_THRESHOLD_UT = 50.0
    const val MAG_HIGH_THRESHOLD_UT = 100.0

    // ── Magnetic Mode ────────────────────────────────────────────
    const val MAG_PARTICLE_SPEED_MIN = 0.03f
    const val MAG_PARTICLE_SPEED_MAX = 0.40f
    const val MAG_ANOMALY_WINDOW = 30
    const val MAG_ANOMALY_THRESHOLD_UT = 25.0f
    const val MAG_ANOMALY_DECAY_MS = 2000L
    const val MAG_COLOR_LERP_WINDOW_UT = 5.0f

    // ── AR ───────────────────────────────────────────────────────
    const val AR_MIN_DISTANCE_M = 0.1
    const val AR_MAX_DISTANCE_M = 20.0

    // ── OpenGL ──────────────────────────────────────────────────
    const val CAMERA_TEXTURE_UNIT = 0
    const val FLOAT_SIZE_BYTES = 4
    const val COORDS_PER_VERTEX = 2 // x, y (NDC quad, no z needed)
    const val TEX_COORDS_PER_VERTEX = 2

    // ── Ghost Mode ─────────────────────────────────────────────
    const val GHOST_MAX_WAVES = 20
    const val GHOST_WAVE_FREQUENCY = 15.0f
    const val GHOST_WAVE_SPEED = 2.0f
    const val GHOST_WAVE_THICKNESS = 0.3f
    const val GHOST_MAX_RADIUS = 0.8f
    const val GHOST_BASE_ALPHA = 0.4f
    const val GHOST_FALLOFF_RATE = 3.0f
    const val GHOST_RSSI_MIN = -90
    const val GHOST_RSSI_MAX = -30
    const val GHOST_DISTANCE_MIN_M = 0.5f
    const val GHOST_DISTANCE_MAX_M = 5.0f

    // ── Bluetooth Mode ──────────────────────────────────────────────
    const val BT_MAX_DEVICES = 15
    const val BT_NODE_RADIUS = 0.03f           // circle radius in aspect-corrected NDC
    const val BT_RING_WIDTH = 0.012f           // ring band half-width in NDC
    const val BT_MAX_RING_RADIUS = 0.25f       // max expansion radius in aspect-corrected NDC
    const val BT_LINE_WIDTH = 0.005f           // connection line half-width in NDC
    const val BT_LINE_INTENSITY = 0.4f         // connection line max alpha contribution
    const val BT_RSSI_MIN = -90               // weakest signal to display
    const val BT_RSSI_MAX = -30               // strongest expected signal
    const val BT_DISTANCE_MIN_M = 0.3f         // closest device placement
    const val BT_DISTANCE_MAX_M = 8.0f         // farthest device placement

    // ── Compete Mode ────────────────────────────────────────────────
    const val COMPETE_MAX_APS = 8
    const val COMPETE_TOP_APS = 4                 // default APs shown in compete mode
    const val COMPETE_BORDER_THRESHOLD = 0.08f    // NDC distance delta for border zone
    const val COMPETE_TERRITORY_ALPHA = 0.25f     // territory fill transparency
    const val COMPETE_BORDER_PULSE_SPEED = 3.0f   // sin frequency for border animation
    const val COMPETE_AP_RSSI_MIN = -85           // weakest AP to include
    const val COMPETE_AP_RSSI_MAX = -40           // strongest expected AP signal
    const val COMPETE_COVERAGE_HISTORY = 30       // frames tracked for coverage % computation

    /** Fullscreen quad vertex coords (triangle strip): BL, BR, TL, TR */
    val FULLSCREEN_QUAD_COORDS = floatArrayOf(
        -1.0f, -1.0f, // bottom-left
         1.0f, -1.0f, // bottom-right
        -1.0f,  1.0f, // top-left
         1.0f,  1.0f  // top-right
    )

    /**
     * Default texture coords for fullscreen quad.
     * These are overridden per-frame by frame.transformDisplayUvCoords()
     * to handle device rotation/orientation.
     */
    val FULLSCREEN_QUAD_TEX_COORDS = floatArrayOf(
        0.0f, 1.0f, // bottom-left
        1.0f, 1.0f, // bottom-right
        0.0f, 0.0f, // top-left
        1.0f, 0.0f  // top-right
    )
}
