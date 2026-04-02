package co.doubler.spectrum.domain.repository

import co.doubler.spectrum.domain.model.WifiSignal
import kotlinx.coroutines.flow.Flow

interface WifiRepository {
    fun scanNetworks(): Flow<List<WifiSignal>>
    fun observeConnected(): Flow<WifiSignal?>
}
