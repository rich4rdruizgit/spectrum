package co.doubler.spectrum.domain.model

sealed class EcholocationState {
    object Idle : EcholocationState()
    data class Active(
        val address: String,
        val tracker: HeadingRssiTracker,
        val rotationTooFast: Boolean = false
    ) : EcholocationState()
    data class Result(val address: String, val bestHeading: Float, val confidence: Float) : EcholocationState()
}
