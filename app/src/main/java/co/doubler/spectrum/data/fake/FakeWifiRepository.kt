package co.doubler.spectrum.data.fake

import co.doubler.spectrum.domain.model.WifiSignal
import co.doubler.spectrum.domain.repository.WifiRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class FakeWifiRepository @Inject constructor() : WifiRepository {

    private val baseNetworks = listOf(
        Triple("HomeNetwork_2G",    1,  2412),
        Triple("CoffeeShop_WiFi",   6,  2437),
        Triple("OfficeNet",         11, 2462),
        Triple("Neighbor_5G",       36, 5180),
        Triple("SmartTV_5GHz",      40, 5200),
        Triple("GuestNetwork",      3,  2422),
        Triple("IoT_Hub_5G",        149, 5745),
    )

    override fun scanNetworks(): Flow<List<WifiSignal>> = flow {
        var tick = 0L
        while (true) {
            val networks = baseNetworks.mapIndexed { index, (ssid, channel, freq) ->
                val drift = ((tick + index) % 7 - 3).toInt()
                val baseRssi = -45 - (index * 6)
                WifiSignal(
                    bssid = "AA:BB:CC:DD:EE:%02X".format(index),
                    ssid = ssid,
                    rssi = (baseRssi + drift).coerceIn(-85, -45),
                    frequency = freq,
                    channel = channel,
                    capabilities = if (index % 3 == 0) "[WPA-PSK-CCMP][ESS]" else "[WPA2-PSK-CCMP][ESS]",
                    isUserNetwork = index == 0,
                    timestamp = System.currentTimeMillis(),
                )
            }
            emit(networks)
            delay(2_000)
            tick++
        }
    }

    override fun observeConnected(): Flow<WifiSignal?> = flow {
        while (true) {
            emit(
                WifiSignal(
                    bssid = "AA:BB:CC:DD:EE:00",
                    ssid = "HomeNetwork_2G",
                    rssi = -52,
                    frequency = 2412,
                    channel = 1,
                    capabilities = "[WPA-PSK-CCMP][ESS]",
                    isUserNetwork = true,
                    timestamp = System.currentTimeMillis(),
                )
            )
            delay(2_000)
        }
    }
}
