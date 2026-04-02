package co.doubler.spectrum.data.repository

import co.doubler.spectrum.data.sensor.WifiScanner
import co.doubler.spectrum.di.SensorScope
import co.doubler.spectrum.domain.model.WifiSignal
import co.doubler.spectrum.domain.repository.WifiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject

class WifiRepositoryImpl @Inject constructor(
    wifiScanner: WifiScanner,
    @SensorScope scope: CoroutineScope,
) : WifiRepository {

    override fun scanNetworks(): Flow<List<WifiSignal>> = scanNetworksFlow

    override fun observeConnected(): Flow<WifiSignal?> = connectedFlow

    private val scanNetworksFlow: Flow<List<WifiSignal>> =
        wifiScanner.scanResults.shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            replay = 1,
        )

    private val connectedFlow: Flow<WifiSignal?> =
        wifiScanner.connectedNetwork.shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            replay = 1,
        )
}
