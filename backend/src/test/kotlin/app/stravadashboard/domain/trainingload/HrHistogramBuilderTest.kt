package app.stravadashboard.domain.trainingload

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.HeartRateStream
import app.stravadashboard.domain.model.Seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HrHistogramBuilderTest {
    private fun stream(
        times: List<Int>,
        bpm: List<Int>,
    ) = HeartRateStream(times.map(::Seconds), bpm.map(::Bpm))

    @Test
    fun `R4 - seconds per bpm from stream`() {
        val histogram = HrHistogramBuilder.build(stream(listOf(0, 1, 2, 3), listOf(140, 140, 160, 180)))
        assertEquals(mapOf(140 to 2, 160 to 1), histogram.secondsByBpm)
    }

    @Test
    fun `R4 - gaps over 30 s skipped`() {
        val histogram = HrHistogramBuilder.build(stream(listOf(0, 1, 200, 201, 231), listOf(140, 150, 160, 170, 170)))
        assertEquals(mapOf(140 to 1, 160 to 1, 170 to 30), histogram.secondsByBpm)
    }

    @Test
    fun `R4 - non increasing time skipped`() {
        val histogram = HrHistogramBuilder.build(stream(listOf(0, 0, 5), listOf(140, 150, 160)))
        assertEquals(mapOf(150 to 5), histogram.secondsByBpm)
    }

    @Test
    fun `R4 - zero bpm skipped`() {
        val histogram = HrHistogramBuilder.build(stream(listOf(0, 1, 2), listOf(0, 150, 150)))
        assertEquals(mapOf(150 to 1), histogram.secondsByBpm)
    }

    @Test
    fun `R4 - all skipped gives empty histogram`() {
        val histogram = HrHistogramBuilder.build(stream(listOf(0, 100), listOf(150, 150)))
        assertTrue(histogram.secondsByBpm.isEmpty())
    }
}
