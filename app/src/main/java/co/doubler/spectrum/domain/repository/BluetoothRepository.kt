package co.doubler.spectrum.domain.repository

import co.doubler.spectrum.domain.model.BluetoothNode
import kotlinx.coroutines.flow.Flow

interface BluetoothRepository {
    fun scanDevices(): Flow<List<BluetoothNode>>
    fun observeNearby(): Flow<List<BluetoothNode>>
}
