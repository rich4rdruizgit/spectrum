package co.doubler.spectrum.presentation.model

data class MagneticUiState(
    val currentMagnitude: Float = 0f,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val isAnomaly: Boolean = false,
    val anomalyIntensity: Float = 0f,
    val aboveIcnirpLimit: Boolean = false,
    val colorBand: MagColorBand = MagColorBand.LOW,
    val isScanning: Boolean = true,

    /** Rolling average magnitude for the stats bar. */
    val averageMagnitude: Float = 0f,

    /** True when averageMagnitude is below ICNIRP safe limit. */
    val isSafe: Boolean = true,

    /** Recent anomaly detection events shown as AR nodes. Max 5. */
    val anomalyEvents: List<AnomalyEvent> = emptyList()
)

enum class MagColorBand { LOW, MEDIUM, HIGH }

/**
 * A single anomaly detection event captured when the field spiked.
 * Holds the magnitude at spike time, the classifier signature, and a
 * normalized screen position where the node will be rendered.
 */
data class AnomalyEvent(
    val id: Int,
    val magnitude: Float,       // µT at time of detection
    val signature: String,      // e.g. "Motor eléctrico"
    val screenX: Float,         // 0–1 fraction of screen width
    val screenY: Float          // 0–1 fraction of screen height
)

/**
 * Packed render data passed via AtomicReference from ViewModel (main thread)
 * to MagneticOverlayRenderer (GL thread).
 */
data class MagneticRenderData(
    val fieldX: Float = 0f,
    val fieldY: Float = 0f,
    val magnitude: Float = 0f,
    val colorR: Float = 0.08f,
    val colorG: Float = 0.25f,
    val colorB: Float = 0.95f,
    val isAnomaly: Boolean = false,
    val anomalyIntensity: Float = 0f
)
