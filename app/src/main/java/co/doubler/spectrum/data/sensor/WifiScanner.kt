package co.doubler.spectrum.data.sensor

import co.doubler.spectrum.domain.model.WifiSignal
import kotlinx.coroutines.flow.Flow

interface WifiScanner {
    /** Periodic scan results, updated every WIFI_SCAN_INTERVAL_MS. */
    val scanResults: Flow<List<WifiSignal>>

    /** Currently connected WiFi network, null if not connected. */
    val connectedNetwork: Flow<WifiSignal?>
}
