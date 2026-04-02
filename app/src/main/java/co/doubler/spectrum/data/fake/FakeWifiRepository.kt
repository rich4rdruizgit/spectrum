package co.doubler.spectrum.data.fake

import co.doubler.spectrum.domain.model.WifiSignal
import co.doubler.spectrum.domain.repository.WifiRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class FakeWifiRepository @Inject constructor() : WifiRepository {

    private val baseNetworks = listOf(
        Triple("HomeNetwork_WPA3",   1,  2412),
        Triple("CoffeeShop_Secure",  6,  2437),
        Triple("OfficeNet_Legacy",   11, 2462),
        Triple("Neighbor_TKIP",      36, 5180),
        Triple("OldRouter_WEP",      40, 5200),
        Triple("OpenHotspot",        3,  2422),
        Triple("IoT_Hub_5G",         149, 5745),
    )

    private val capabilitiesList = listOf(
        "[WPA3-SAE][ESS]",           // 0 → STRONG
        "[WPA2-PSK-CCMP][ESS]",      // 1 → GOOD
        "[WPA2-PSK-TKIP+CCMP][ESS]", // 2 → WEAK
        "[WPA-PSK-TKIP][ESS]",       // 3 → WEAK
        "[WEP][ESS]",                // 4 → WEP
        "[ESS]",                     // 5 → OPEN
        "[WPA2-PSK-CCMP][ESS]",      // 6 → GOOD
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
                    capabilities = capabilitiesList[index],
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
                    ssid = "HomeNetwork_WPA3",
                    rssi = -52,
                    frequency = 2412,
                    channel = 1,
                    capabilities = "[WPA3-SAE][ESS]",
                    isUserNetwork = true,
                    timestamp = System.currentTimeMillis(),
                )
            )
            delay(2_000)
        }
    }
}
