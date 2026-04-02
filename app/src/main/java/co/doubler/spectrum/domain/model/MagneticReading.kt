package co.doubler.spectrum.domain.model

data class MagneticReading(
    val x: Float,              // microtesla
    val y: Float,              // microtesla
    val z: Float,              // microtesla
    val magnitude: Float,      // total field strength in microtesla — stored, not computed on access
    val timestamp: Long,       // epoch millis
    val position: Triple<Float, Float, Float> // AR world coordinates (x, y, z)
)
