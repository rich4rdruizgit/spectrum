package co.doubler.spectrum.domain.model

data class WifiSignal(
    val bssid: String,
    val ssid: String,
    val rssi: Int,          // dBm (typically -30 to -90)
    val frequency: Int,     // MHz (2400 or 5000 range)
    val channel: Int,
    val capabilities: String, // e.g. "[WPA2-PSK-CCMP][ESS]"
    val isUserNetwork: Boolean,
    val timestamp: Long     // epoch millis
)
