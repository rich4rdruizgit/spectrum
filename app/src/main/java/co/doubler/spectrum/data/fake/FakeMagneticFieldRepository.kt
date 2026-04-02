package co.doubler.spectrum.data.fake

import co.doubler.spectrum.domain.model.MagneticReading
import co.doubler.spectrum.domain.repository.MagneticFieldRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sin
import kotlin.math.sqrt
import javax.inject.Inject

class FakeMagneticFieldRepository @Inject constructor() : MagneticFieldRepository {

    private fun buildReading(tick: Long): MagneticReading {
        val t = tick * 0.1
        // Earth's field ~45 µT with slow sinusoidal variation ±15 µT
        val oscillation = sin(t * 0.05) * 15f
        val base = 45f + oscillation.toFloat()

        val x = base * sin(t * 0.03).toFloat()
        val y = base * sin(t * 0.03 + Math.PI / 3).toFloat()
        val z = base * sin(t * 0.03 + 2 * Math.PI / 3).toFloat()
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        return MagneticReading(
            x = x,
            y = y,
            z = z,
            magnitude = magnitude,
            timestamp = System.currentTimeMillis(),
            position = Triple(0f, 0f, 0f),
        )
    }

    override fun observeField(): Flow<MagneticReading> = flow {
        var tick = 0L
        while (true) {
            emit(buildReading(tick))
            delay(100) // 10 Hz
            tick++
        }
    }

    override fun observeFieldHistory(limit: Int): Flow<List<MagneticReading>> = flow {
        var tick = 0L
        val history = ArrayDeque<MagneticReading>(limit)
        while (true) {
            val reading = buildReading(tick)
            if (history.size >= limit) history.removeFirst()
            history.addLast(reading)
            emit(history.toList())
            delay(100)
            tick++
        }
    }
}
