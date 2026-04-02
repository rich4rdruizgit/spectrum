package co.doubler.spectrum.data.sensor

import co.doubler.spectrum.domain.model.BluetoothNode
import kotlinx.coroutines.flow.Flow

interface BluetoothScanner {
    /** All BLE devices discovered during active scan windows. Accumulates over duty cycles. */
    val scanResults: Flow<List<BluetoothNode>>

    /** Devices seen within the last 30 seconds (filtered from scanResults). */
    val nearbyDevices: Flow<List<BluetoothNode>>
}
