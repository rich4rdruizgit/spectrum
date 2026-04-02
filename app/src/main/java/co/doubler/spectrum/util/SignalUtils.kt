package co.doubler.spectrum.util

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure stateless signal processing functions.
 * No dependencies, no DI, no state — thread-safe by definition.
 */
object SignalUtils {

    /**
     * Exponential Moving Average for smoothing RSSI fluctuations.
     * @param previous Last smoothed value
     * @param current New raw measurement
     * @param alpha Smoothing factor (0..1). Lower = smoother, higher = more responsive.
     * @return Smoothed value: alpha * current + (1 - alpha) * previous
     */
    fun exponentialMovingAverage(previous: Double, current: Double, alpha: Double): Double {
        val clampedAlpha = alpha.coerceIn(0.0, 1.0)
        return clampedAlpha * current + (1.0 - clampedAlpha) * previous
    }

    /**
     * Log-distance path-loss model for distance estimation from RSSI.
     * @param rssi Received signal strength in dBm
     * @param txPower Calibrated TX power at 1 meter in dBm (default: -59)
     * @param pathLossExponent Environment factor: 2.0 (free space), 2.5-3.5 (indoor)
     * @return Estimated distance in meters, or -1.0 if pathLossExponent is invalid
     */
    fun estimateDistance(rssi: Int, txPower: Int = -59, pathLossExponent: Double = 2.0): Double {
        if (pathLossExponent <= 0.0) return -1.0
        return 10.0.pow((txPower - rssi).toDouble() / (10.0 * pathLossExponent))
    }

    /**
     * Convert WiFi frequency (MHz) to channel number.
     * Supports 2.4 GHz (channels 1-14), 5 GHz (channels 34-177), and 6 GHz (channels 1-233).
     * @return Channel number, or -1 if frequency is unrecognized.
     */
    fun channelFromFrequency(frequencyMhz: Int): Int = when {
        // 2.4 GHz band: 2412 MHz (ch 1) to 2484 MHz (ch 14)
        frequencyMhz in 2412..2484 -> {
            if (frequencyMhz == 2484) 14
            else (frequencyMhz - 2412) / 5 + 1
        }
        // 5 GHz band: 5170 MHz (ch 34) to 5885 MHz (ch 177)
        frequencyMhz in 5170..5885 -> (frequencyMhz - 5000) / 5
        // 6 GHz band: 5955 MHz (ch 1) to 7115 MHz (ch 233)
        frequencyMhz in 5955..7115 -> (frequencyMhz - 5950) / 5
        else -> -1
    }

    /**
     * Euclidean magnitude of a 3D vector.
     * Used for magnetic field total intensity from x/y/z components.
     */
    fun magnitude(x: Float, y: Float, z: Float): Float =
        sqrt(x * x + y * y + z * z)
}
