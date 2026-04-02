package co.doubler.spectrum.domain.model

import kotlin.math.*

class HeadingRssiTracker {

    // --- Histogram state ---
    private val buckets  = FloatArray(BUCKETS) { Float.NaN }
    private val alpha    = 0.3f

    // --- Shadow edge state ---
    private var prevRssi: Int?   = null
    private var shadowEntry: Float? = null
    private var shadowExit:  Float? = null

    // --- Speed guard output (read immediately after record()) ---
    var isFastRotation: Boolean = false
        private set

    fun record(headingDeg: Float, rssi: Int, speedDegPerSec: Float) {
        isFastRotation = speedDegPerSec > 90f
        val weight = if (isFastRotation) alpha * 0.5f else alpha
        val idx = headingToIndex(headingDeg)
        buckets[idx] = if (buckets[idx].isNaN()) rssi.toFloat()
                       else weight * rssi + (1f - weight) * buckets[idx]

        prevRssi?.let { prev ->
            val delta = rssi - prev
            if (delta < -8)  shadowEntry = headingDeg
            if (delta > +8)  shadowExit  = headingDeg
        }
        prevRssi = rssi
    }

    fun getHybridHeading(): Float? {
        val gauss  = gaussHeading()
        val shadow = shadowHeading()
        val gw     = gaussConf()
        val sw     = shadowWeight()

        return when {
            sw == 0f && gw == 0f -> null
            sw == 0f             -> gauss
            gw == 0f             -> shadow
            else                 -> {
                shadow ?: return gauss
                gauss  ?: return shadow
                circularWeightedMean(listOf(shadow to sw, gauss to gw))
            }
        }
    }

    fun getHybridConfidence(): Float {
        val sc = shadowWeight() * 0.7f
        val gc = gaussConf()
        return maxOf(sc, gc)
    }

    fun getBucketStates(): BooleanArray = BooleanArray(BUCKETS) { !buckets[it].isNaN() }

    fun filledCount(): Int = buckets.count { !it.isNaN() }

    fun reset() {
        buckets.fill(Float.NaN)
        prevRssi    = null
        shadowEntry = null
        shadowExit  = null
        isFastRotation = false
    }

    // --- Private helpers ---

    private fun headingToIndex(deg: Float): Int =
        ((deg % 360 + 360) % 360 / 10).toInt().coerceIn(0, BUCKETS - 1)

    private fun filledBuckets(): List<Pair<Float, Float>> =
        buckets.indices
            .filter { !buckets[it].isNaN() }
            .map { idx -> (idx * 10f + 5f) to buckets[idx] }

    private fun gaussHeading(): Float? {
        val pts = filledBuckets()
        if (pts.size < 5) return null
        return circularWeightedMean(pts.map { (h, r) -> h to shiftedWeight(r, pts) })
    }

    private fun gaussConf(): Float {
        val pts = filledBuckets()
        if (pts.size < 5) return 0f
        val mu = gaussHeading() ?: return 0f
        val weights = pts.map { (_, r) -> shiftedWeight(r, pts) }
        val totalW = weights.sum()
        if (totalW == 0f) return 0f
        val variance = pts.indices.sumOf { i ->
            val diff = angularDiff(pts[i].first, mu)
            (weights[i] * diff * diff).toDouble()
        }.toFloat() / totalW
        val sigma = sqrt(variance)
        return 1f / (1f + sigma / 45f)
    }

    private fun shiftedWeight(rssi: Float, pts: List<Pair<Float, Float>>): Float {
        val minR = pts.minOf { it.second }
        return (rssi - minR).coerceAtLeast(0f)
    }

    private fun shadowHeading(): Float? {
        val entry = shadowEntry ?: return shadowExit?.let { (it + 180f) % 360f }
        val exit  = shadowExit  ?: return (entry + 180f) % 360f
        val mid   = circularWeightedMean(listOf(entry to 1f, exit to 1f))
        return (mid + 180f) % 360f
    }

    private fun shadowWeight(): Float = when {
        shadowEntry != null && shadowExit != null -> 1.0f
        shadowEntry != null || shadowExit != null -> 0.6f
        else                                      -> 0.0f
    }

    private fun circularWeightedMean(pairs: List<Pair<Float, Float>>): Float {
        var sinSum = 0.0
        var cosSum = 0.0
        pairs.forEach { (deg, w) ->
            val rad = Math.toRadians(deg.toDouble())
            sinSum += sin(rad) * w
            cosSum += cos(rad) * w
        }
        val result = Math.toDegrees(atan2(sinSum, cosSum)).toFloat()
        return (result + 360f) % 360f
    }

    private fun angularDiff(a: Float, b: Float): Float {
        val diff = abs(a - b) % 360f
        return if (diff > 180f) 360f - diff else diff
    }

    companion object {
        private const val BUCKETS = 36
    }
}
