package co.doubler.spectrum.domain.model

sealed class EcholocationState {
    object Idle : EcholocationState()
    data class Active(val deviceAddress: String, val tracker: HeadingRssiTracker) : EcholocationState()
    data class Result(val deviceAddress: String, val bestHeading: Float, val confidence: Float) : EcholocationState()
}
