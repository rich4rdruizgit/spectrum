package co.doubler.spectrum.domain.repository

import co.doubler.spectrum.domain.model.MagneticReading
import kotlinx.coroutines.flow.Flow

interface MagneticFieldRepository {
    fun observeField(): Flow<MagneticReading>
    fun observeFieldHistory(limit: Int): Flow<List<MagneticReading>>
}
