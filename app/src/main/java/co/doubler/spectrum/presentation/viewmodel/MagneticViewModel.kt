package co.doubler.spectrum.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.doubler.spectrum.ar.ArSessionManager
import co.doubler.spectrum.domain.repository.MagneticFieldRepository
import co.doubler.spectrum.presentation.model.MagColorBand
import co.doubler.spectrum.presentation.model.MagneticRenderData
import co.doubler.spectrum.presentation.model.MagneticUiState
import co.doubler.spectrum.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

@HiltViewModel
class MagneticViewModel @Inject constructor(
    private val magneticFieldRepository: MagneticFieldRepository,
    val sessionManager: ArSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MagneticUiState(isScanning = true))
    val uiState: StateFlow<MagneticUiState> = _uiState.asStateFlow()

    /** Written by ViewModel on main thread, read by MagneticOverlayRenderer on GL thread. */
    val magneticDataRef: AtomicReference<MagneticRenderData> =
        AtomicReference(MagneticRenderData())

    // Anomaly detection state
    private val magnitudeHistory = ArrayDeque<Float>(Constants.MAG_ANOMALY_WINDOW)
    private var lastAnomalyTriggerMs = 0L
    private var readingCount = 0

    init {
        observeMagneticField()
    }

    private fun observeMagneticField() {
        magneticFieldRepository.observeField()
            .onEach { reading ->
                readingCount++

                // Update rolling history for anomaly baseline
                if (magnitudeHistory.size >= Constants.MAG_ANOMALY_WINDOW) {
                    magnitudeHistory.removeFirst()
                }
                magnitudeHistory.addLast(reading.magnitude)

                // Anomaly detection — disabled during warmup
                val isAnomaly = if (readingCount >= Constants.MAG_ANOMALY_WINDOW) {
                    detectAnomaly(reading.magnitude)
                } else false

                val mean = if (magnitudeHistory.isNotEmpty()) magnitudeHistory.average().toFloat() else 0f
                val delta = abs(reading.magnitude - mean)
                val anomalyIntensity = if (isAnomaly) {
                    (delta / (Constants.MAG_ANOMALY_THRESHOLD_UT * 3f)).coerceIn(0f, 1f)
                } else 0f

                val colorBand = magnitudeToColorBand(reading.magnitude)
                val (r, g, b) = magnitudeToColor(reading.magnitude)

                // Normalized XY field direction for shader
                val xyLen = sqrt(reading.x * reading.x + reading.y * reading.y)
                    .coerceAtLeast(0.001f)
                val nx = reading.x / xyLen
                val ny = reading.y / xyLen

                // Update render data — lock-free, read by GL thread
                magneticDataRef.set(
                    MagneticRenderData(
                        fieldX = nx,
                        fieldY = ny,
                        magnitude = reading.magnitude,
                        colorR = r,
                        colorG = g,
                        colorB = b,
                        isAnomaly = isAnomaly,
                        anomalyIntensity = anomalyIntensity
                    )
                )

                _uiState.value = MagneticUiState(
                    currentMagnitude = reading.magnitude,
                    x = reading.x,
                    y = reading.y,
                    z = reading.z,
                    isAnomaly = isAnomaly,
                    anomalyIntensity = anomalyIntensity,
                    aboveIcnirpLimit = reading.magnitude > Constants.MAG_SAFE_LIMIT_UT,
                    colorBand = colorBand,
                    isScanning = false
                )
            }
            .launchIn(viewModelScope)
    }

    /**
     * Detects anomaly using fixed-delta threshold against rolling average.
     * Auto-clears after [Constants.MAG_ANOMALY_DECAY_MS] without a trigger.
     */
    private fun detectAnomaly(magnitude: Float): Boolean {
        val mean = magnitudeHistory.average().toFloat()
        val delta = abs(magnitude - mean)
        val now = System.currentTimeMillis()

        return if (delta > Constants.MAG_ANOMALY_THRESHOLD_UT) {
            lastAnomalyTriggerMs = now
            true
        } else {
            // Keep anomaly active during decay window
            (now - lastAnomalyTriggerMs) < Constants.MAG_ANOMALY_DECAY_MS
        }
    }

    private fun magnitudeToColorBand(magnitude: Float): MagColorBand = when {
        magnitude < Constants.MAG_LOW_THRESHOLD_UT  -> MagColorBand.LOW
        magnitude < Constants.MAG_HIGH_THRESHOLD_UT -> MagColorBand.MEDIUM
        else                                         -> MagColorBand.HIGH
    }

    /**
     * Maps magnitude to RGB triple using the three-band gradient:
     * LOW  (< 50 µT)   → blue  (0.08, 0.25, 0.95)
     * MED  (50-100 µT) → purple (0.65, 0.10, 0.90)
     * HIGH (> 100 µT)  → red   (0.95, 0.08, 0.10)
     */
    private fun magnitudeToColor(magnitude: Float): Triple<Float, Float, Float> {
        val t = (magnitude / Constants.MAG_HIGH_THRESHOLD_UT.toFloat()).coerceIn(0f, 1f)
        return if (t < 0.5f) {
            val s = t * 2f
            Triple(lerp(0.08f, 0.65f, s), lerp(0.25f, 0.10f, s), lerp(0.95f, 0.90f, s))
        } else {
            val s = (t - 0.5f) * 2f
            Triple(lerp(0.65f, 0.95f, s), lerp(0.10f, 0.08f, s), lerp(0.90f, 0.10f, s))
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
