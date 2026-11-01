package app.stravadashboard.domain.longrundrift

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.DriftStream
import app.stravadashboard.domain.model.HrDriftResult
import app.stravadashboard.domain.model.HrDriftStatus
import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.roundOneDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

// All streams here are invented.
class HrDriftCalculatorTest {
    /** [count] one-second samples at one speed and heart rate, optionally not moving. */
    private data class Segment(
        val count: Int,
        val speed: Double,
        val bpm: Int,
        val moving: Boolean = true,
    )

    private fun stream(
        vararg segments: Segment,
        withMoving: Boolean = false,
        gapsAfter: Set<Int> = emptySet(),
    ): DriftStream {
        val samples = segments.flatMap { s -> List(s.count) { s } }
        var t = 0
        val times =
            samples.indices.map { i ->
                Seconds(t).also { t += if (i in gapsAfter) GAP_SECONDS else 1 }
            }
        return DriftStream(
            times = times,
            speeds = samples.map { MetersPerSecond(it.speed) },
            bpm = samples.map { Bpm(it.bpm) },
            moving = if (withMoving) samples.map { it.moving } else null,
        )
    }

    private fun drift(stream: DriftStream): Double? = HrDriftCalculator.compute(stream).driftPct?.let(::roundOneDecimal)

    @Test
    fun `R1 - drift is efficiency loss from first to second half`() {
        val result = HrDriftCalculator.compute(stream(Segment(3600, 3.0, 140), Segment(3600, 2.9, 145)))
        assertEquals(HrDriftStatus.COMPUTED, result.status)
        assertEquals(6.7, result.driftPct?.let(::roundOneDecimal))
    }

    @Test
    fun `R1 - rising HR at constant speed gives positive drift`() {
        assertEquals(4.8, drift(stream(Segment(3600, 3.0, 140), Segment(3600, 3.0, 147))))
    }

    @Test
    fun `R1 - negative drift is kept as is`() {
        assertEquals(-5.0, drift(stream(Segment(3600, 3.0, 147), Segment(3600, 3.0, 140))))
    }

    @Test
    fun `R1 - needs 60 minutes usable moving time`() {
        // n one-second samples give n - 1 intervals.
        assertEquals(HrDriftStatus.COMPUTED, HrDriftCalculator.compute(stream(Segment(3601, 3.0, 150))).status)
        assertEquals(
            HrDriftResult(HrDriftStatus.TOO_SHORT, null),
            HrDriftCalculator.compute(stream(Segment(3600, 3.0, 150))),
        )
    }

    @Test
    fun `R1 - gaps over 30 seconds add no time`() {
        // The sample before the gap has HR 200; if its interval counted, drift wouldn't be zero.
        val withGap =
            stream(Segment(1800, 3.0, 150), Segment(1, 3.0, 200), Segment(1801, 3.0, 150), gapsAfter = setOf(1800))
        assertEquals(0.0, drift(withGap))
        val twoGaps =
            stream(
                Segment(1800, 3.0, 150),
                Segment(1, 3.0, 200),
                Segment(1801, 3.0, 150),
                gapsAfter = setOf(1799, 1800),
            )
        assertEquals(HrDriftStatus.TOO_SHORT, HrDriftCalculator.compute(twoGaps).status)
    }

    @Test
    fun `R1 - non moving intervals are excluded and halves split usable time`() {
        val stopped =
            stream(
                Segment(3600, 3.0, 140),
                Segment(600, 0.0, 120, moving = false),
                Segment(3600, 2.9, 145),
                withMoving = true,
            )
        assertEquals(6.7, drift(stopped))
    }

    @Test
    fun `R1 - missing moving stream counts every short interval as moving`() {
        val allStopped = stream(Segment(7200, 3.0, 150, moving = false), withMoving = true)
        assertEquals(HrDriftStatus.NO_DATA, HrDriftCalculator.compute(allStopped).status)
        val noFlags = stream(Segment(7200, 3.0, 150, moving = false))
        assertEquals(0.0, drift(noFlags))
    }

    @Test
    fun `R1 - zero heart rate samples are excluded`() {
        val dropout = stream(Segment(3600, 3.0, 140), Segment(600, 5.0, 0), Segment(3600, 2.9, 145))
        assertEquals(6.7, drift(dropout))
    }

    @Test
    fun `R1 - no heart rate or no speed gives no data`() {
        val noData = HrDriftResult(HrDriftStatus.NO_DATA, null)
        assertEquals(noData, HrDriftCalculator.compute(stream(Segment(7200, 3.0, 0))))
        assertEquals(noData, HrDriftCalculator.compute(stream(Segment(7200, 0.0, 150))))
        assertEquals(noData, HrDriftCalculator.compute(stream(Segment(3600, 0.0, 150), Segment(3600, 3.0, 150))))
        // Checked before the 60-minute rule: a short stream without HR is still no data.
        assertEquals(noData, HrDriftCalculator.compute(stream(Segment(100, 3.0, 0))))
    }

    private companion object {
        const val GAP_SECONDS = 120
    }
}
