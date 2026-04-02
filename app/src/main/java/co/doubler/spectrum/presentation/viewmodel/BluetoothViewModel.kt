package co.doubler.spectrum.presentation.viewmodel

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.doubler.spectrum.ar.ArSessionManager
import co.doubler.spectrum.domain.model.BluetoothDeviceType
import co.doubler.spectrum.domain.model.BluetoothNode
import co.doubler.spectrum.domain.model.EcholocationState
import co.doubler.spectrum.domain.model.HeadingRssiTracker
import co.doubler.spectrum.domain.repository.BluetoothRepository
import co.doubler.spectrum.presentation.model.BluetoothDeviceNode
import co.doubler.spectrum.presentation.model.BluetoothUiState
import co.doubler.spectrum.util.Constants.BT_DISTANCE_MAX_M
import co.doubler.spectrum.util.Constants.BT_DISTANCE_MIN_M
import co.doubler.spectrum.util.Constants.BT_MAX_DEVICES
import co.doubler.spectrum.util.Constants.BT_RSSI_MAX
import co.doubler.spectrum.util.Constants.BT_RSSI_MIN
import co.doubler.spectrum.util.Constants.DEFAULT_TX_POWER
import co.doubler.spectrum.util.SignalUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    val sessionManager: ArSessionManager,
    private val sensorManager: SensorManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BluetoothUiState(isScanning = true))

    val uiState: StateFlow<BluetoothUiState> = _uiState.asStateFlow()

    /** Written by ViewModel on main thread, read by BluetoothOverlayRenderer on GL thread. */
    val bluetoothDevicesRef: AtomicReference<List<BluetoothDeviceNode>> =
        AtomicReference(emptyList())

    /** Written by BluetoothOverlayRenderer on GL thread, read by ViewModel on main thread. */
    val screenPositionsRef: AtomicReference<Map<String, Offset>> =
        AtomicReference(emptyMap())

    private var rotationListener: SensorEventListener? = null
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    init {
        observeBluetoothScans()
    }

    fun activateEcholocation(address: String) {
        val tracker = HeadingRssiTracker()
        _uiState.update { it.copy(echolocationState = EcholocationState.Active(address, tracker)) }
        registerRotationListener(tracker, address)
    }

    fun deactivateEcholocation() {
        unregisterRotationListener()
        _uiState.update { it.copy(echolocationState = EcholocationState.Idle) }
    }

    private fun registerRotationListener(tracker: HeadingRssiTracker, address: String) {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
        rotationListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val headingDeg = ((Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + 360f) % 360f)
                val rssi = _uiState.value.devices.find { it.address == address }?.rssi ?: return
                tracker.record(headingDeg, rssi)
                val confidence = tracker.getConfidence()
                val bestHeading = tracker.getBestHeading()
                if (confidence >= 0.4f && bestHeading != null) {
                    unregisterRotationListener()
                    _uiState.update {
                        it.copy(echolocationState = EcholocationState.Result(address, bestHeading, confidence))
                    }
                } else {
                    _uiState.update { it.copy() }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sensorManager.registerListener(rotationListener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun unregisterRotationListener() {
        rotationListener?.let { sensorManager.unregisterListener(it) }
        rotationListener = null
    }

    override fun onCleared() {
        super.onCleared()
        unregisterRotationListener()
    }

    private fun observeBluetoothScans() {
        bluetoothRepository.observeNearby()
            .onEach { nodes -> handleScanResult(nodes) }
            .launchIn(viewModelScope)
    }

    private fun handleScanResult(nodes: List<BluetoothNode>) {
        if (nodes.isEmpty()) {
            _uiState.update {
                it.copy(
                    devices = emptyList(),
                    connectedDevices = emptyList(),
                    isScanning = false,
                    totalDeviceCount = 0,
                    screenPositions = screenPositionsRef.get()
                )
            }
            bluetoothDevicesRef.set(emptyList())
            return
        }

        val deviceNodes = nodes
            .map { node -> mapToDeviceNode(node) }
            .sortedByDescending { it.rssi }
            .take(BT_MAX_DEVICES)

        val connected = deviceNodes.filter { it.isConnected }
        val isAnomalyActive = deviceNodes.any { it.estimatedDistance > 0f && it.estimatedDistance < 0.5f }

        _uiState.update {
            it.copy(
                devices = deviceNodes,
                connectedDevices = connected,
                isScanning = false,
                totalDeviceCount = nodes.size,
                screenPositions = screenPositionsRef.get(),
                isAnomalyActive = isAnomalyActive
            )
        }
        bluetoothDevicesRef.set(deviceNodes)
    }

    private fun mapToDeviceNode(node: BluetoothNode): BluetoothDeviceNode {
        val azimuth = estimateAzimuth(node.address)
        val distance = estimateDistance(node.rssi, node.txPower)
        val strength = normalizeSignalStrength(node.rssi)
        val color = assignColor(node.isConnected, node.type, strength)

        return BluetoothDeviceNode(
            address = node.address,
            name = node.name ?: "Unknown",
            type = node.type,
            rssi = node.rssi,
            isConnected = node.isConnected,
            estimatedDistance = distance,
            signalStrength = strength,
            color = color,
            azimuthDeg = azimuth
        )
    }

    // ── Position computation ──────────────────────────────────────

    /**
     * Deterministic azimuth from BLE/BT MAC address hash.
     * Same address always produces the same angle (0–360°).
     */
    fun estimateAzimuth(address: String): Float {
        return (address.hashCode() and 0x7FFFFFFF) % 360f
    }

    /**
     * Map RSSI to estimated distance using log-distance path-loss model.
     * Uses device's advertised TX power when available; falls back to [DEFAULT_TX_POWER].
     * Clamped to [BT_DISTANCE_MIN_M, BT_DISTANCE_MAX_M].
     */
    fun estimateDistance(rssi: Int, txPower: Int?): Float {
        val calibratedTxPower = txPower ?: DEFAULT_TX_POWER
        return SignalUtils.estimateDistance(rssi, calibratedTxPower)
            .toFloat()
            .coerceIn(BT_DISTANCE_MIN_M, BT_DISTANCE_MAX_M)
    }

    /**
     * Convert polar coordinates (azimuth + distance) to a 3D world point.
     * Y=0 (ground plane), Z is negative forward (camera convention).
     */
    fun azimuthToWorldPoint(azimuthDeg: Float, distance: Float): FloatArray {
        val rad = Math.toRadians(azimuthDeg.toDouble())
        return floatArrayOf(
            (distance * sin(rad)).toFloat(),
            0f,
            -(distance * cos(rad)).toFloat()
        )
    }

    // ── Signal processing ─────────────────────────────────────────

    /**
     * Normalize RSSI to 0.0–1.0 range.
     * [BT_RSSI_MIN] dBm → 0.0 (weakest), [BT_RSSI_MAX] dBm → 1.0 (strongest).
     */
    fun normalizeSignalStrength(rssi: Int): Float {
        return ((rssi - BT_RSSI_MIN).toFloat() / (BT_RSSI_MAX - BT_RSSI_MIN))
            .coerceIn(0f, 1f)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

    private fun lerpColor(from: Long, to: Long, t: Float): Long {
        val r = lerp(((from shr 16) and 0xFF).toFloat(), ((to shr 16) and 0xFF).toFloat(), t).toInt()
        val g = lerp(((from shr 8) and 0xFF).toFloat(), ((to shr 8) and 0xFF).toFloat(), t).toInt()
        val b = lerp((from and 0xFF).toFloat(), (to and 0xFF).toFloat(), t).toInt()
        return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    fun assignColor(isConnected: Boolean, type: BluetoothDeviceType, signalStrength: Float): Long {
        if (type == BluetoothDeviceType.UNKNOWN) return 0xFFFF1744L
        return if (isConnected) {
            lerpColor(0xFF1A3AFFL, 0xFF00CFFFL, signalStrength)
        } else {
            lerpColor(0xFF4A4A6AL, 0xFF8888BBL, signalStrength)
        }
    }
}
