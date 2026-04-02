package co.doubler.spectrum.presentation.model

data class MagneticUiState(
    val currentMagnitude: Float = 0f,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val isAnomaly: Boolean = false,
    val anomalyIntensity: Float = 0f,       // 0-1, normalized anomaly severity
    val aboveIcnirpLimit: Boolean = false,
    val colorBand: MagColorBand = MagColorBand.LOW,
    val isScanning: Boolean = true
)

enum class MagColorBand { LOW, MEDIUM, HIGH }

/**
 * Packed render data passed via AtomicReference from ViewModel (main thread)
 * to MagneticOverlayRenderer (GL thread). Uses primitives to avoid per-frame
 * allocation on the GL thread.
 */
data class MagneticRenderData(
    val fieldX: Float = 0f,           // normalized field direction X (XY plane)
    val fieldY: Float = 0f,           // normalized field direction Y (XY plane)
    val magnitude: Float = 0f,        // raw µT value
    val colorR: Float = 0.08f,        // pre-computed RGB from magnitude band
    val colorG: Float = 0.25f,
    val colorB: Float = 0.95f,
    val isAnomaly: Boolean = false,
    val anomalyIntensity: Float = 0f  // 0-1
)
