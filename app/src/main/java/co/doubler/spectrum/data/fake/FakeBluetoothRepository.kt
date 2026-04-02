package co.doubler.spectrum.data.fake

import co.doubler.spectrum.domain.model.BluetoothDeviceType
import co.doubler.spectrum.domain.model.BluetoothNode
import co.doubler.spectrum.domain.repository.BluetoothRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class FakeBluetoothRepository @Inject constructor() : BluetoothRepository {

    private data class FakeDevice(
        val address: String,
        val name: String,
        val type: BluetoothDeviceType,
        val baseRssi: Int,
        val txPower: Int,
    )

    private val baseDevices = listOf(
        FakeDevice("F0:18:98:AA:01:11", "AirPods Pro",        BluetoothDeviceType.HEADPHONES, -62, 4),
        FakeDevice("C4:29:4A:BB:02:22", "Galaxy Watch 5",     BluetoothDeviceType.WATCH,      -71, 2),
        FakeDevice("00:0C:8A:CC:03:33", "JBL Flip 6",         BluetoothDeviceType.SPEAKER,    -55, 8),
        FakeDevice("A8:20:66:DD:04:44", "Pixel 7 Pro",        BluetoothDeviceType.PHONE,      -78, 4),
        FakeDevice("34:AB:37:EE:05:55", "Sony WH-1000XM5",    BluetoothDeviceType.HEADPHONES, -68, 4),
    )

    private fun driftRssi(base: Int, tick: Long, index: Int): Int {
        val shift = ((tick + index) % 5 - 2).toInt()
        return (base + shift).coerceIn(-90, -40)
    }

    private fun buildNodes(tick: Long): List<BluetoothNode> =
        baseDevices.mapIndexed { index, device ->
            val rssi = driftRssi(device.baseRssi, tick, index)
            val distance = if (rssi > -90)
                Math.pow(10.0, (device.txPower - rssi) / 20.0)
            else null
            BluetoothNode(
                address = device.address,
                name = device.name,
                type = device.type,
                rssi = rssi,
                isConnected = index == 0,
                txPower = device.txPower,
                estimatedDistance = distance,
                lastSeen = System.currentTimeMillis(),
            )
        }

    override fun scanDevices(): Flow<List<BluetoothNode>> = flow {
        var tick = 0L
        while (true) {
            emit(buildNodes(tick))
            delay(1_500)
            tick++
        }
    }

    override fun observeNearby(): Flow<List<BluetoothNode>> = flow {
        var tick = 0L
        while (true) {
            emit(buildNodes(tick))
            delay(1_500)
            tick++
        }
    }
}
