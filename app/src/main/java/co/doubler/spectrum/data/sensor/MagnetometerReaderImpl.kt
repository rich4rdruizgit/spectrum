package co.doubler.spectrum.data.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import co.doubler.spectrum.domain.model.MagneticReading
import co.doubler.spectrum.util.Constants.MAG_HISTORY_DEFAULT_SIZE
import co.doubler.spectrum.util.Constants.MAG_SENSOR_DELAY
import co.doubler.spectrum.util.SignalUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MagnetometerReaderImpl @Inject constructor(
    private val sensorManager: SensorManager,
) : MagnetometerReader {

    companion object {
        private const val TAG = "MagnetometerReaderImpl"
    }

    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    override val readings: Flow<MagneticReading> by lazy {
        if (magnetometer == null) {
            Log.w(TAG, "Magnetometer sensor not available on this device")
            return@lazy emptyFlow()
        }

        callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    val reading = MagneticReading(
                        x = x,
                        y = y,
                        z = z,
                        magnitude = SignalUtils.magnitude(x, y, z),
                        timestamp = System.currentTimeMillis(),
                        position = Triple(0f, 0f, 0f),
                    )
                    trySend(reading)
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    Log.d(TAG, "Magnetometer accuracy changed: $accuracy")
                }
            }

            sensorManager.registerListener(listener, magnetometer, MAG_SENSOR_DELAY)

            awaitClose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    override fun readingHistory(bufferSize: Int): Flow<List<MagneticReading>> {
        val effectiveSize = if (bufferSize > 0) bufferSize else MAG_HISTORY_DEFAULT_SIZE

        return readings.scan(ArrayDeque<MagneticReading>(effectiveSize)) { buffer, reading ->
            buffer.apply {
                if (size >= effectiveSize) removeFirst()
                addLast(reading)
            }
        }.map { it.toList() }
    }
}
