package co.doubler.spectrum.domain.model

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Rule-based classifier that identifies the likely source of a magnetic anomaly
 * from a window of magnitude readings and the current XYZ field components.
 *
 * No ML model or training data required — uses signal features:
 * - Variance over the history window  → oscillating vs. static source
 * - Mean magnitude                    → field strength band
 * - Z-axis ratio                      → vertical vs. horizontal source orientation
 *
 * Accuracy is best at < 50 cm from the source. At greater distances the
 * signal attenuates rapidly (1/r³) and classifications become less reliable.
 */
class MagneticSignatureClassifier {

    /**
     * Classifies the current anomaly.
     *
     * @param history Recent magnitude readings (at least 10 recommended).
     * @param x Current field X component in µT.
     * @param y Current field Y component in µT.
     * @param z Current field Z component in µT.
     * @return Human-readable signature label in Spanish.
     */
    fun classify(
        history: List<Float>,
        x: Float,
        y: Float,
        z: Float
    ): String {
        if (history.isEmpty()) return "Señal desconocida"

        val mean      = history.average().toFloat()
        val variance  = computeVariance(history, mean)
        val zRatio    = computeZRatio(x, y, z)
        val isOscillating = variance > OSCILLATION_THRESHOLD

        return when {
            // Very strong + oscillating → large motor or industrial source
            mean > 150f && isOscillating          -> "Motor de alta potencia"
            // Strong + oscillating + mostly vertical → microwave transformer
            mean > 80f && isOscillating && zRatio > 0.6f -> "Microondas / horno"
            // Strong + oscillating → generic transformer / power supply
            mean > 80f && isOscillating            -> "Transformador / fuente"
            // Strong + static → permanent magnet (speaker, hard drive)
            mean > 60f && !isOscillating           -> "Imán permanente"
            // Medium + oscillating + horizontal → fridge or AC motor
            mean > 30f && isOscillating && zRatio < 0.4f -> "Motor de heladera"
            // Medium + oscillating → generic electric motor
            mean > 30f && isOscillating            -> "Motor eléctrico"
            // Medium + static → magnetized metal object
            mean > 20f && !isOscillating           -> "Objeto metálico"
            // Weak + oscillating → current-carrying cable nearby
            isOscillating                          -> "Cable con carga"
            // Fallback
            else                                   -> "Campo ambiental"
        }
    }

    // ── Feature extraction ────────────────────────────────────────────────────

    private fun computeVariance(history: List<Float>, mean: Float): Float {
        if (history.size < 2) return 0f
        return history.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / history.size
    }

    /**
     * Fraction of total field magnitude carried by the Z axis.
     * High Z ratio → source is above or below (vertical orientation).
     * Low Z ratio  → source is in the horizontal plane (wall cable, motor on ground).
     */
    private fun computeZRatio(x: Float, y: Float, z: Float): Float {
        val total = sqrt(x * x + y * y + z * z).coerceAtLeast(0.001f)
        return abs(z) / total
    }

    companion object {
        /** Variance threshold (µT²) above which a signal is considered oscillating. */
        private const val OSCILLATION_THRESHOLD = 4.0f
    }
}
