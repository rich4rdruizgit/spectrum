package co.doubler.spectrum.data.repository

import co.doubler.spectrum.domain.model.BluetoothNode
import co.doubler.spectrum.domain.repository.BluetoothRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class BluetoothRepositoryImpl @Inject constructor() : BluetoothRepository {

    override fun scanDevices(): Flow<List<BluetoothNode>> = flowOf(emptyList())

    override fun observeNearby(): Flow<List<BluetoothNode>> = flowOf(emptyList())
}
