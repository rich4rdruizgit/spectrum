package co.doubler.spectrum.domain.model

data class BluetoothNode(
    val address: String,
    val name: String?,          // Nullable — unnamed BLE devices exist
    val type: BluetoothDeviceType,
    val rssi: Int,              // dBm
    val isConnected: Boolean,
    val txPower: Int?,          // Nullable — not always available
    val estimatedDistance: Double?, // Nullable — derived from rssi + txPower when available
    val lastSeen: Long          // epoch millis
)

enum class BluetoothDeviceType {
    HEADPHONES,
    WATCH,
    SPEAKER,
    PHONE,
    UNKNOWN
}
