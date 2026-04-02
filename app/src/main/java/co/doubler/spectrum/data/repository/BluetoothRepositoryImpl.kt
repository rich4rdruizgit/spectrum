package co.doubler.spectrum.data.repository

import co.doubler.spectrum.data.sensor.BluetoothScanner
import co.doubler.spectrum.di.SensorScope
import co.doubler.spectrum.domain.model.BluetoothNode
import co.doubler.spectrum.domain.repository.BluetoothRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject

class BluetoothRepositoryImpl @Inject constructor(
    bluetoothScanner: BluetoothScanner,
    @SensorScope scope: CoroutineScope,
) : BluetoothRepository {

    override fun scanDevices(): Flow<List<BluetoothNode>> = scanDevicesFlow

    override fun observeNearby(): Flow<List<BluetoothNode>> = nearbyFlow

    private val scanDevicesFlow: Flow<List<BluetoothNode>> =
        bluetoothScanner.scanResults.shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            replay = 1,
        )

    private val nearbyFlow: Flow<List<BluetoothNode>> =
        bluetoothScanner.nearbyDevices.shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            replay = 1,
        )
}
