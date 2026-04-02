package co.doubler.spectrum.data.fake

import co.doubler.spectrum.data.prefs.DemoPreferences
import co.doubler.spectrum.di.DemoImpl
import co.doubler.spectrum.di.RealImpl
import co.doubler.spectrum.domain.model.BluetoothNode
import co.doubler.spectrum.domain.repository.BluetoothRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoAwareBleRepository @Inject constructor(
    @RealImpl private val real: BluetoothRepository,
    @DemoImpl private val fake: BluetoothRepository,
    private val demoPreferences: DemoPreferences,
) : BluetoothRepository {

    override fun scanDevices(): Flow<List<BluetoothNode>> =
        demoPreferences.isDemoEnabled
            .flatMapLatest { if (it) fake.scanDevices() else real.scanDevices() }

    override fun observeNearby(): Flow<List<BluetoothNode>> =
        demoPreferences.isDemoEnabled
            .flatMapLatest { if (it) fake.observeNearby() else real.observeNearby() }
}
