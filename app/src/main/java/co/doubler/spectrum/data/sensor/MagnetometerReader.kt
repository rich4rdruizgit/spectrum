package co.doubler.spectrum.data.sensor

import co.doubler.spectrum.domain.model.MagneticReading
import kotlinx.coroutines.flow.Flow

interface MagnetometerReader {
    /** Latest magnetic field reading, ~50Hz emission rate. */
    val readings: Flow<MagneticReading>

    /** Rolling history of the last [bufferSize] readings. */
    fun readingHistory(bufferSize: Int): Flow<List<MagneticReading>>
}
