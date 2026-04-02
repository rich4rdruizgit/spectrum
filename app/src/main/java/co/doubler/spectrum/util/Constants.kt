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
    const val DEFAULT_TX_POWER = -59 // dBm at 1 m

    // ── Magnetometer ─────────────────────────────────────────────
    val MAG_SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME
    const val MAG_SAFE_LIMIT_UT = 200.0
    const val MAG_LOW_THRESHOLD_UT = 50.0
    const val MAG_HIGH_THRESHOLD_UT = 100.0

    // ── AR ───────────────────────────────────────────────────────
    const val AR_MIN_DISTANCE_M = 0.1
    const val AR_MAX_DISTANCE_M = 20.0

    // ── OpenGL ──────────────────────────────────────────────────
    const val CAMERA_TEXTURE_UNIT = 0
    const val FLOAT_SIZE_BYTES = 4
    const val COORDS_PER_VERTEX = 2 // x, y (NDC quad, no z needed)
    const val TEX_COORDS_PER_VERTEX = 2

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
