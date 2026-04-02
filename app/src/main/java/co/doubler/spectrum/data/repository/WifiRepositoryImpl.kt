package co.doubler.spectrum.data.repository

import co.doubler.spectrum.domain.model.WifiSignal
import co.doubler.spectrum.domain.repository.WifiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class WifiRepositoryImpl @Inject constructor() : WifiRepository {

    override fun scanNetworks(): Flow<List<WifiSignal>> = flowOf(emptyList())

    override fun observeConnected(): Flow<WifiSignal?> = flowOf(null)
}
