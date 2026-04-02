package co.doubler.spectrum.data.repository

import co.doubler.spectrum.domain.model.MagneticReading
import co.doubler.spectrum.domain.repository.MagneticFieldRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class MagneticFieldRepositoryImpl @Inject constructor() : MagneticFieldRepository {

    override fun observeField(): Flow<MagneticReading> = emptyFlow()

    override fun observeFieldHistory(limit: Int): Flow<List<MagneticReading>> = flowOf(emptyList())
}
