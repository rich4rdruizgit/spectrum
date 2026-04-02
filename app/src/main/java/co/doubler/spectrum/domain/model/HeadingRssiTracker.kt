package co.doubler.spectrum.domain.model

class HeadingRssiTracker {
    private val bucketEma = FloatArray(36) { Float.NaN }
    private val bucketFilled = BooleanArray(36)
    private val alpha = 0.3f

    fun record(headingDeg: Float, rssi: Int) {
        val normalized = ((headingDeg % 360f) + 360f) % 360f
        val index = (normalized / 10f).toInt().coerceIn(0, 35)
        bucketEma[index] = if (bucketEma[index].isNaN()) rssi.toFloat()
                           else bucketEma[index] * (1f - alpha) + rssi * alpha
        bucketFilled[index] = true
    }

    fun getBestHeading(): Float? {
        if (bucketFilled.count { it } < 5) return null
        val best = bucketEma.indices.filter { bucketFilled[it] }.maxByOrNull { bucketEma[it] } ?: return null
        return best * 10f + 5f
    }

    fun getConfidence(): Float = bucketFilled.count { it }.toFloat() / 36f

    fun getBucketStates(): BooleanArray = bucketFilled.copyOf()

    fun reset() {
        bucketEma.fill(Float.NaN)
        bucketFilled.fill(false)
    }
}
