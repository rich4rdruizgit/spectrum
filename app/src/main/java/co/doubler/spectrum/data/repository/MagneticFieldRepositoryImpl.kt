package co.doubler.spectrum.data.repository

import co.doubler.spectrum.data.sensor.MagnetometerReader
import co.doubler.spectrum.di.SensorScope
import co.doubler.spectrum.domain.model.MagneticReading
import co.doubler.spectrum.domain.repository.MagneticFieldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject

class MagneticFieldRepositoryImpl @Inject constructor(
    private val magnetometerReader: MagnetometerReader,
    @SensorScope private val scope: CoroutineScope,
) : MagneticFieldRepository {

    override fun observeField(): Flow<MagneticReading> = fieldFlow

    override fun observeFieldHistory(limit: Int): Flow<List<MagneticReading>> =
        magnetometerReader.readingHistory(limit).shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            replay = 1,
        )

    private val fieldFlow: Flow<MagneticReading> =
        magnetometerReader.readings.shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            replay = 1,
        )
}
