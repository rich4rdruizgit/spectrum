package co.doubler.spectrum.data.fake

import co.doubler.spectrum.data.prefs.DemoPreferences
import co.doubler.spectrum.di.DemoImpl
import co.doubler.spectrum.di.RealImpl
import co.doubler.spectrum.domain.model.MagneticReading
import co.doubler.spectrum.domain.repository.MagneticFieldRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoAwareMagneticRepository @Inject constructor(
    @RealImpl private val real: MagneticFieldRepository,
    @DemoImpl private val fake: MagneticFieldRepository,
    private val demoPreferences: DemoPreferences,
) : MagneticFieldRepository {

    override fun observeField(): Flow<MagneticReading> =
        demoPreferences.isDemoEnabled
            .flatMapLatest { if (it) fake.observeField() else real.observeField() }

    override fun observeFieldHistory(limit: Int): Flow<List<MagneticReading>> =
        demoPreferences.isDemoEnabled
            .flatMapLatest { if (it) fake.observeFieldHistory(limit) else real.observeFieldHistory(limit) }
}
