package co.doubler.spectrum.data.fake

import co.doubler.spectrum.data.prefs.DemoPreferences
import co.doubler.spectrum.di.DemoImpl
import co.doubler.spectrum.di.RealImpl
import co.doubler.spectrum.domain.model.WifiSignal
import co.doubler.spectrum.domain.repository.WifiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoAwareWifiRepository @Inject constructor(
    @RealImpl private val real: WifiRepository,
    @DemoImpl private val fake: WifiRepository,
    private val demoPreferences: DemoPreferences,
) : WifiRepository {

    override fun scanNetworks(): Flow<List<WifiSignal>> =
        demoPreferences.isDemoEnabled
            .flatMapLatest { if (it) fake.scanNetworks() else real.scanNetworks() }

    override fun observeConnected(): Flow<WifiSignal?> =
        demoPreferences.isDemoEnabled
            .flatMapLatest { if (it) fake.observeConnected() else real.observeConnected() }
}
